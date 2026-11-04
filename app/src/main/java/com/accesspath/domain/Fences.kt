package com.accesspath.domain

import kotlinx.serialization.Serializable

/** What a geofence is watching. */
@Serializable enum class FenceKind { AREA, DRAFT_AREA, LIFT_DOWN, PATH_CLOSED }

/** One circular area the phone watches; [name] is shown in the alert. */
@Serializable data class Fence(val id: String, val kind: FenceKind, val name: String, val lat: Double, val lng: Double, val radius: Float)

/**
 * Decides which places the phone should watch:
 * - every mapped campus area (arrival alert, with a warning for unverified draft areas),
 * - lifts that are out of service (only for people who avoid stairs or use a wheelchair),
 * - paths that are closed right now.
 * Android allows 100 geofences per app; areas come first, then lifts, then closures.
 */
object FencePlanner {
    const val MAX_FENCES = 100
    /** Android recommends at least 100 m; smaller circles fire late or not at all. */
    const val MIN_RADIUS = 100f
    private const val MAX_RADIUS = 1_000f

    fun plan(s: AppSnapshot): List<Fence> {
        val map = s.map
        val pos = map.nodes.mapNotNull { n -> Geo.position(map, n)?.let { n.id to it } }.toMap()
        if (pos.isEmpty()) return emptyList()
        val floorBuilding = map.floors.associate { it.id to it.buildingId }
        val names = map.nodes.associate { it.id to it.name }

        val areas = map.buildings.mapNotNull { b ->
            val pts = map.nodes.filter { floorBuilding[it.floorId] == b.id }.mapNotNull { pos[it.id] }
            if (pts.isEmpty()) return@mapNotNull null
            val c = centre(pts)
            val r = (pts.maxOf { Coverage.distanceMetres(c, it) } + 25.0).toFloat().coerceIn(MIN_RADIUS, MAX_RADIUS)
            Fence("area:${b.id}", if (b.draft) FenceKind.DRAFT_AREA else FenceKind.AREA, b.name, c.lat, c.lng, r)
        }

        val lifts = if (s.profile.mobility == Mobility.STANDARD) emptyList() else
            map.facilities.filter { it.availability == Availability.UNAVAILABLE }.mapNotNull { f ->
                val pts = map.edges.filter { it.facilityId == f.id }.flatMap { listOf(it.from, it.to) }.distinct().mapNotNull { pos[it] }
                if (pts.isEmpty()) null else centre(pts).let { Fence("lift:${f.id}", FenceKind.LIFT_DOWN, f.name, it.lat, it.lng, MIN_RADIUS) }
            }

        val closedIds = s.blockedEdges + s.reports.filter { it.isActive }.map { it.edgeId }
        val closures = map.edges.filter { it.id in closedIds && it.type != Segment.ELEVATOR }
            .distinctBy { listOf(it.from, it.to).sorted() }
            .mapNotNull { e ->
                val a = pos[e.from] ?: return@mapNotNull null
                val b = pos[e.to] ?: return@mapNotNull null
                val m = centre(listOf(a, b))
                Fence("closed:${e.id}", FenceKind.PATH_CLOSED, "${names[e.from]} – ${names[e.to]}", m.lat, m.lng, MIN_RADIUS)
            }

        return (areas + lifts + closures).take(MAX_FENCES)
    }

    private fun centre(pts: List<LatLng>): LatLng {
        val lat = pts.sumOf { it.lat } / pts.size
        // Average longitudes are fine at campus scale (no date-line crossing).
        val lng = pts.sumOf { it.lng } / pts.size
        return LatLng(lat, lng)
    }
}
