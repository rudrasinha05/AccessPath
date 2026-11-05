package com.accesspath.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Is a real-world position inside an area the map already covers? */
object Coverage {
    /** Places further than this from every mapped place count as unmapped. */
    const val MAPPED_RADIUS_M = 35.0

    fun distanceMetres(a: LatLng, b: LatLng): Double {
        val dLat = (b.lat - a.lat) * 111_320.0
        val dLng = (b.lng - a.lng) * 111_320.0 * cos((a.lat + b.lat) / 2 * PI / 180.0)
        return hypot(dLat, dLng)
    }

    fun isMapped(map: CampusMap, here: LatLng, radius: Double = MAPPED_RADIUS_M): Boolean {
        val pos = map.nodes.mapNotNull { n -> Geo.position(map, n)?.let { n.id to it } }.toMap()
        if (pos.values.any { distanceMetres(it, here) <= radius }) return true
        // Long straight paths have few points: also count the lines between them.
        return map.edges.any { e -> val a = pos[e.from]; val b = pos[e.to]; a != null && b != null && distanceToLine(here, a, b) <= radius }
    }

    private fun distanceToLine(p: LatLng, a: LatLng, b: LatLng): Double {
        val k = 111_320.0 * cos(p.lat * PI / 180.0)
        val ax = (a.lng - p.lng) * k; val ay = (a.lat - p.lat) * 111_320.0
        val bx = (b.lng - p.lng) * k; val by = (b.lat - p.lat) * 111_320.0
        val dx = bx - ax; val dy = by - ay; val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
        return hypot(ax + t * dx, ay + t * dy)
    }
}

/** A point of the walked trail in the AR session's frame: x to the right, z backwards (metres). */
data class TrailPoint(val x: Double, val z: Double)

/** A named place the surveyor added at a trail point. */
data class SurveyPlace(val trailIndex: Int, val name: String, val type: String)

@kotlinx.serialization.Serializable
data class Survey(val building: Building, val floor: Floor, val nodes: List<Node>, val edges: List<Edge>) {
    /** The same area once an admin has verified it: shown on the map like any other area. */
    fun approved() = copy(building = building.copy(draft = false))

    companion object {
        /** Rebuilds the survey of a draft building from the map, so it can be shared. */
        fun of(map: CampusMap, buildingId: String, draftOnly: Boolean = true): Survey? {
            val b = map.buildings.firstOrNull { it.id == buildingId && (it.draft || !draftOnly) } ?: return null
            val floor = map.floors.firstOrNull { it.buildingId == b.id } ?: return null
            val nodes = map.nodes.filter { it.floorId == floor.id }
            val ids = nodes.map { it.id }.toSet()
            return Survey(b, floor, nodes, map.edges.filter { it.from in ids && it.to in ids })
        }
    }
}

/**
 * Turns a camera-tracked walk into draft map data. Path width, slope and step-free status cannot be
 * measured from the walk, so they stay unknown and wheelchair routes skip these paths until a reviewer
 * fills them in.
 */
object SurveyBuilder {
    const val MIN_SPACING_M = 3.0
    private const val ORIGIN_SHIFT_M = 500.0

    /**
     * @param start real-world position where the session started
     * @param headingDeg compass direction the camera faced at the start, clockwise from north
     */
    fun build(id: String, name: String, trail: List<TrailPoint>, places: List<SurveyPlace>, start: LatLng, headingDeg: Double): Survey? {
        if (trail.size < 2) return null
        val h = headingDeg * PI / 180.0
        // Floor-local metres: x east, y south, shifted so every coordinate is positive.
        fun local(p: TrailPoint): Pair<Double, Double> {
            val forward = -p.z; val right = p.x
            val north = forward * cos(h) - right * sin(h)
            val east = forward * sin(h) + right * cos(h)
            return (east + ORIGIN_SHIFT_M) to (-north + ORIGIN_SHIFT_M)
        }
        val placeAt = places.associateBy { it.trailIndex.coerceIn(0, trail.lastIndex) }
        // A clean line: only the bends of the walk and the named places are kept, not every GPS wobble.
        val kept = PathSimplifier.keep(trail, placeAt.keys, PathSimplifier.TOLERANCE_M)
        if (kept.size < 2) return null
        val anchor = Geo.toLatLng(GeoAnchor(start.lat, start.lng), -ORIGIN_SHIFT_M, -ORIGIN_SHIFT_M)
        val building = Building(id, name, GeoAnchor(anchor.lat, anchor.lng), draft = true)
        val floor = Floor("${id}_f0", id, "Surveyed level", 0)
        var pointNo = 0
        val nodes = kept.mapIndexed { k, i ->
            val (x, y) = local(trail[i])
            val place = placeAt[i]
            // Plain path points are type PATH: part of the line, never shown as places.
            Node(
                id = "${id}_n$k", floorId = floor.id,
                name = place?.name?.trim()?.takeIf { it.isNotEmpty() } ?: "Path ${++pointNo}",
                x = x, y = y, type = place?.type ?: "PATH"
            )
        }
        val edges = nodes.zipWithNext().flatMapIndexed { k, (a, b) ->
            val length = hypot(a.x - b.x, a.y - b.y)
            val forward = Edge("${id}_e$k", a.id, b.id, Segment.CORRIDOR, length)
            listOf(forward, forward.copy(id = "${id}_e${k}_r", from = b.id, to = a.id))
        }
        return Survey(building, floor, nodes, edges)
    }
}

/**
 * Records a walked path from location fixes (the blue dot), for phones without ARCore or when the camera can't be used.
 * Converts positions to the same trail format camera mapping uses (x east, z south, metres from the first point), so
 * [SurveyBuilder.build] with heading 0 places it correctly. Weak fixes are skipped instead of drawing a wrong path.
 */
class GpsTrailRecorder(private val maxAccuracyM: Double = MAX_ACCURACY_M, private val minStepM: Double = MIN_STEP_M) {
    private val pts = mutableListOf<LatLng>()
    private val placeList = mutableListOf<SurveyPlace>()
    var paused = false
    var distance = 0.0; private set
    val start: LatLng? get() = pts.firstOrNull()
    val size get() = pts.size
    val points: List<LatLng> get() = pts.toList()
    val places: List<SurveyPlace> get() = placeList.toList()

    /** Returns true when the fix was kept. */
    fun add(at: LatLng, accuracyM: Double): Boolean {
        if (paused || accuracyM > maxAccuracyM) return false
        val last = pts.lastOrNull()
        if (last != null) {
            val step = Coverage.distanceMetres(last, at)
            if (step < minStepM) return false
            distance += step
        }
        pts += at
        return true
    }

    /** Names the place at the latest kept point. */
    fun addPlace(name: String, type: String) {
        if (pts.isEmpty()) return
        placeList.removeAll { it.trailIndex == pts.lastIndex }
        placeList += SurveyPlace(pts.lastIndex, name, type)
    }

    fun trail(): List<TrailPoint> {
        val o = pts.firstOrNull() ?: return emptyList()
        return pts.map { p ->
            val north = (p.lat - o.lat) * 111_320.0
            val east = (p.lng - o.lng) * 111_320.0 * cos(o.lat * PI / 180.0)
            TrailPoint(x = east, z = -north)
        }
    }

    companion object {
        /** Indoors, fixes worse than this jump around too much to draw a path. */
        const val MAX_ACCURACY_M = 25.0
        const val MIN_STEP_M = 1.5
    }
}

/**
 * Straightens a walked trail (Douglas–Peucker): keeps the first and last points, every point in [keep]
 * (named places), and only the points where the walk really turns more than [toleranceM] metres off a straight line.
 */
object PathSimplifier {
    const val TOLERANCE_M = 2.5

    fun keep(trail: List<TrailPoint>, keep: Set<Int> = emptySet(), toleranceM: Double = TOLERANCE_M): List<Int> {
        if (trail.size <= 2) return trail.indices.toList()
        val anchors = (setOf(0, trail.lastIndex) + keep.filter { it in trail.indices }).sorted()
        val out = sortedSetOf<Int>()
        anchors.zipWithNext().forEach { (a, b) -> out += a; out += b; simplify(trail, a, b, toleranceM, out) }
        return out.toList()
    }

    private fun simplify(t: List<TrailPoint>, a: Int, b: Int, tol: Double, out: MutableSet<Int>) {
        if (b - a < 2) return
        var far = -1; var best = tol
        for (i in a + 1 until b) {
            val d = distToSegment(t[i], t[a], t[b])
            if (d > best) { best = d; far = i }
        }
        if (far < 0) return
        out += far
        simplify(t, a, far, tol, out); simplify(t, far, b, tol, out)
    }

    private fun distToSegment(p: TrailPoint, a: TrailPoint, b: TrailPoint): Double {
        val dx = b.x - a.x; val dz = b.z - a.z
        val len = dx * dx + dz * dz
        val u = if (len == 0.0) 0.0 else (((p.x - a.x) * dx + (p.z - a.z) * dz) / len).coerceIn(0.0, 1.0)
        return hypot(p.x - (a.x + u * dx), p.z - (a.z + u * dz))
    }
}
