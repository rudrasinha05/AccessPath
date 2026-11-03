package com.accesspath.domain

import java.util.PriorityQueue

/** Why a segment cannot be used. Closures are temporary; the rest come from the person's profile or the map. */
enum class ExclusionReason(val isClosure: Boolean) {
    BLOCKED_BY_SIMULATION(true),
    PENDING_REPORT(true),
    VERIFIED_REPORT(true),
    FACILITY_UNAVAILABLE(true),
    FACILITY_STATUS_UNKNOWN(true),
    RESTRICTED(false),
    STAIRS_NOT_ALLOWED(false),
    STEP_FREE_UNKNOWN(false),
    WIDTH_UNSUITABLE(false),
    SLOPE_UNSUITABLE(false),
    LIFT_UNSUITABLE(false)
}

enum class NoRouteCause {
    /** Start or destination is not on the current map. */
    INVALID_ENDPOINTS,
    /** Saved mobility settings are out of range. */
    INVALID_PROFILE,
    /** The two places are not connected at all in the map data. */
    NOT_CONNECTED,
    /** A suitable route exists, but closures (reports, lifts out of service, blocks) cut it off right now. */
    CLOSED_NOW,
    /** No route meets the mobility settings, even with everything open. */
    PROFILE_TOO_STRICT
}

sealed interface RouteResult {
    data class Found(
        val nodes: List<Node>,
        val edges: List<Edge>,
        val distance: Double,
        val score: Double,
        val revision: Long,
        val mapVersion: String,
        /** Extra metres compared with the shortest route for someone who can use stairs; null if not computed. */
        val extraDistanceVsStandard: Double? = null,
        val exceedsMaxWalkingDistance: Boolean = false
    ) : RouteResult {
        val usesLift: Boolean get() = edges.any { it.type == Segment.ELEVATOR }
        val usesRamp: Boolean get() = edges.any { it.type == Segment.RAMP }
        val usesStairs: Boolean get() = edges.any { it.type == Segment.STAIRS }
    }

    data class Unavailable(val cause: NoRouteCause, val closures: List<ExclusionReason> = emptyList()) : RouteResult
}

enum class NearbyKind { WASHROOM, LIFT, EXIT }

object Routing {
    /** Every segment that cannot be used right now, with the first reason that applies. */
    fun exclusions(state: AppSnapshot, profile: Profile = state.profile): Map<String, ExclusionReason> {
        val edgeIndex = state.map.edges.associateBy { it.id }
        val reportStatus = mutableMapOf<String, ReportStatus>()
        state.reports.filter { it.isActive }.forEach { report ->
            val target = edgeIndex[report.edgeId] ?: return@forEach
            state.map.edges.filter { e ->
                (e.from == target.from && e.to == target.to) || (e.from == target.to && e.to == target.from) ||
                    (target.facilityId != null && e.facilityId == target.facilityId)
            }.forEach { e ->
                // A verified report outranks a pending one on the same segment.
                if (reportStatus[e.id] != ReportStatus.VERIFIED) reportStatus[e.id] = report.status
            }
        }
        val facilities = state.map.facilities.associateBy { it.id }
        val wheelchair = profile.mobility == Mobility.WHEELCHAIR
        val result = LinkedHashMap<String, ExclusionReason>()
        for (e in state.map.edges) {
            val facility = e.facilityId?.let { facilities[it] }
            val reason = when {
                e.id in state.blockedEdges -> ExclusionReason.BLOCKED_BY_SIMULATION
                reportStatus[e.id] == ReportStatus.VERIFIED -> ExclusionReason.VERIFIED_REPORT
                reportStatus[e.id] == ReportStatus.PENDING -> ExclusionReason.PENDING_REPORT
                e.facilityId != null && facility?.availability == Availability.UNAVAILABLE -> ExclusionReason.FACILITY_UNAVAILABLE
                e.facilityId != null && facility?.availability != Availability.AVAILABLE -> ExclusionReason.FACILITY_STATUS_UNKNOWN
                e.restricted -> ExclusionReason.RESTRICTED
                profile.mobility != Mobility.STANDARD && e.type == Segment.STAIRS -> ExclusionReason.STAIRS_NOT_ALLOWED
                wheelchair && e.stepFree != true -> ExclusionReason.STEP_FREE_UNKNOWN
                wheelchair && (e.width == null || e.width < profile.minWidth) -> ExclusionReason.WIDTH_UNSUITABLE
                wheelchair && e.type == Segment.RAMP && (e.slope == null || e.slope > profile.maxSlope) -> ExclusionReason.SLOPE_UNSUITABLE
                wheelchair && e.type == Segment.ELEVATOR && e.wheelchairSuitable != true -> ExclusionReason.LIFT_UNSUITABLE
                else -> null
            }
            if (reason != null) result[e.id] = reason
        }
        return result
    }

    fun cost(e: Edge): Double =
        e.length * (e.multiplier ?: when (e.type) { Segment.RAMP -> 1.5; Segment.STAIRS -> 2.0; else -> 1.0 }) +
            (e.penalty ?: if (e.type == Segment.ELEVATOR) 20.0 else 0.0)

    private class Tree(val score: Map<String, Double>, val previous: Map<String, Edge>) {
        fun path(start: String, destination: String): List<Edge>? {
            if (destination !in score) return null
            val edges = mutableListOf<Edge>()
            var cursor = destination
            while (cursor != start) {
                val edge = previous[cursor] ?: return null
                edges.add(edge); cursor = edge.from
            }
            return edges.asReversed()
        }
    }

    /** Dijkstra over allowed edges. Stops early when [destination] is settled. Ties break by node ID for stable results. */
    private fun search(edges: List<Edge>, start: String, destination: String?): Tree {
        val adjacency = edges.groupBy { it.from }.mapValues { (_, list) -> list.sortedBy(Edge::id) }
        val score = HashMap<String, Double>().apply { put(start, 0.0) }
        val previous = HashMap<String, Edge>()
        data class Entry(val id: String, val score: Double)
        val queue = PriorityQueue(compareBy<Entry> { it.score }.thenBy { it.id })
        queue.add(Entry(start, 0.0))
        while (queue.isNotEmpty()) {
            val cur = queue.remove()
            if (cur.score != score[cur.id]) continue
            if (cur.id == destination) break
            adjacency[cur.id].orEmpty().forEach { edge ->
                val next = cur.score + cost(edge)
                if (next < (score[edge.to] ?: Double.POSITIVE_INFINITY)) {
                    score[edge.to] = next; previous[edge.to] = edge; queue.add(Entry(edge.to, next))
                }
            }
        }
        return Tree(score, previous)
    }

    private fun allowed(state: AppSnapshot, excluded: Map<String, ExclusionReason>, keep: (ExclusionReason) -> Boolean = { true }) =
        state.map.edges.filter { e -> excluded[e.id]?.let { !keep(it) } ?: true }

    fun calculate(state: AppSnapshot, start: String = state.current, destination: String = state.destination): RouteResult {
        if (!state.profile.isValid) return RouteResult.Unavailable(NoRouteCause.INVALID_PROFILE)
        val nodes = state.map.nodes.associateBy { it.id }
        if (start !in nodes || destination !in nodes) return RouteResult.Unavailable(NoRouteCause.INVALID_ENDPOINTS)

        val excluded = exclusions(state)
        val edges = search(allowed(state, excluded), start, destination).path(start, destination)
            ?: return RouteResult.Unavailable(explainNoRoute(state, excluded, start, destination), excluded.values.filter { it.isClosure }.distinct())

        val distance = edges.sumOf { it.length }
        val extra = if (state.profile.mobility != Mobility.STANDARD) {
            val standard = exclusions(state, Profile(Mobility.STANDARD))
            search(allowed(state, standard), start, destination).path(start, destination)
                ?.let { (distance - it.sumOf(Edge::length)).coerceAtLeast(0.0) }
        } else null
        val max = state.profile.maxWalkingDistance
        return RouteResult.Found(
            nodes = listOf(nodes.getValue(start)) + edges.map { nodes.getValue(it.to) },
            edges = edges,
            distance = distance,
            score = edges.sumOf(::cost),
            revision = state.revision,
            mapVersion = state.map.version,
            extraDistanceVsStandard = extra,
            exceedsMaxWalkingDistance = max != null && distance > max
        )
    }

    private fun explainNoRoute(state: AppSnapshot, excluded: Map<String, ExclusionReason>, start: String, destination: String): NoRouteCause {
        // Ignore temporary closures: does the profile allow any route at all?
        val profileOnly = allowed(state, excluded) { !it.isClosure }
        if (search(profileOnly, start, destination).path(start, destination) != null) return NoRouteCause.CLOSED_NOW
        val anything = search(state.map.edges, start, destination).path(start, destination)
        return if (anything == null) NoRouteCause.NOT_CONNECTED else NoRouteCause.PROFILE_TOO_STRICT
    }

    fun matches(state: AppSnapshot, node: Node, kind: NearbyKind): Boolean = when (kind) {
        NearbyKind.WASHROOM -> node.type.equals("WASHROOM", true) || node.name.contains("washroom", true) || node.name.contains("toilet", true)
        NearbyKind.LIFT -> node.type.equals("ELEVATOR", true) || state.map.edges.any { it.type == Segment.ELEVATOR && (it.from == node.id || it.to == node.id) }
        NearbyKind.EXIT -> node.type.equals("ENTRANCE", true) || node.type.equals("EXIT", true) || node.name.contains("exit", true) || node.name.contains("gate", true)
    }

    /** Closest place of [kind] that can be reached from the current position with the current profile and closures. */
    fun nearest(state: AppSnapshot, kind: NearbyKind): Node? {
        if (!state.profile.isValid || state.node(state.current) == null) return null
        val excluded = exclusions(state)
        // A lift only counts if at least one of its lift segments is usable right now.
        val workingLiftNodes = state.map.edges.filter { it.type == Segment.ELEVATOR && it.id !in excluded }.flatMap { listOf(it.from, it.to) }.toSet()
        val candidates = state.map.nodes
            .filter { matches(state, it, kind) && (kind != NearbyKind.LIFT || it.id in workingLiftNodes) }
            .associateBy { it.id }
        if (candidates.isEmpty()) return null
        val tree = search(allowed(state, excluded), state.current, null)
        return candidates.keys.filter { it in tree.score }
            .minWithOrNull(compareBy<String> { tree.score.getValue(it) }.thenBy { it })
            ?.let(candidates::getValue)
    }
}
