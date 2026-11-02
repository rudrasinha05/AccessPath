package com.accesspath.domain

import java.util.PriorityQueue

sealed interface RouteResult {
    data class Found(val nodes: List<Node>, val edges: List<Edge>, val distance: Double, val score: Double, val revision: Long, val mapVersion: String) : RouteResult
    data class Unavailable(val reasons: List<String>) : RouteResult
}

object Routing {
    fun exclusions(state: AppSnapshot, profile: Profile = state.profile): Map<String, String> {
        val activeReports = state.reports.filter { it.status in setOf(ReportStatus.PENDING, ReportStatus.VERIFIED) }
        val edgeIndex = state.map.edges.associateBy { it.id }
        val active = mutableMapOf<String, BarrierReport>()
        activeReports.forEach { report -> edgeIndex[report.edgeId]?.let { target ->
            state.map.edges.filter { edge ->
                (edge.from == target.from && edge.to == target.to) || (edge.from == target.to && edge.to == target.from) ||
                    (target.facilityId != null && edge.facilityId == target.facilityId)
            }.forEach { active[it.id] = report }
        } }
        val facilities = state.map.facilities.associateBy { it.id }
        return state.map.edges.mapNotNull { e ->
            val reason = when {
                e.id in state.blockedEdges -> "Path blocked by demo control"
                e.id in active -> if(active[e.id]?.status == ReportStatus.PENDING) "Unverified barrier report: precautionary closure" else "Verified accessibility barrier"
                e.restricted -> "Restricted access"
                e.facilityId != null && facilities[e.facilityId]?.availability != Availability.AVAILABLE -> "Facility unavailable or status unknown"
                profile.mobility != Mobility.STANDARD && e.type == Segment.STAIRS -> "Stairs excluded by profile"
                profile.mobility == Mobility.WHEELCHAIR && e.stepFree != true -> "Step-free status unsuitable or unknown"
                profile.mobility == Mobility.WHEELCHAIR && (e.width == null || e.width < profile.minWidth) -> "Passage width unsuitable or unknown"
                profile.mobility == Mobility.WHEELCHAIR && e.type == Segment.RAMP && (e.slope == null || e.slope > profile.maxSlope) -> "Ramp slope unsuitable or unknown"
                profile.mobility == Mobility.WHEELCHAIR && e.type == Segment.ELEVATOR && e.wheelchairSuitable != true -> "Lift suitability unknown or unsuitable"
                else -> null
            }
            reason?.let { e.id to it }
        }.toMap()
    }
    fun cost(e: Edge): Double = e.length * (e.multiplier ?: when(e.type) { Segment.RAMP -> 1.5; Segment.STAIRS -> 2.0; else -> 1.0 }) +
        (e.penalty ?: if(e.type == Segment.ELEVATOR) 20.0 else 0.0)

    fun calculate(state: AppSnapshot, start: String = state.current, destination: String = state.destination): RouteResult {
        state.profile.validate()
        val nodes = state.map.nodes.associateBy { it.id }
        if(start !in nodes || destination !in nodes) return RouteResult.Unavailable(listOf("Choose a valid start and destination."))
        val excluded = exclusions(state)
        val adjacency = state.map.edges.filter { it.id !in excluded }.groupBy { it.from }.mapValues { it.value.sortedBy(Edge::id) }
        val distances = mutableMapOf(start to 0.0); val previous = mutableMapOf<String, Edge>()
        data class Entry(val id: String, val score: Double)
        val queue = PriorityQueue(compareBy<Entry> { it.score }.thenBy { it.id }); queue.add(Entry(start, 0.0))
        while(queue.isNotEmpty()) {
            val cur = queue.remove()
            if(cur.score != distances[cur.id]) continue
            if(cur.id == destination) break
            adjacency[cur.id].orEmpty().forEach { edge ->
                val next = cur.score + cost(edge)
                if(next < (distances[edge.to] ?: Double.POSITIVE_INFINITY)) {
                    distances[edge.to] = next; previous[edge.to] = edge; queue.add(Entry(edge.to, next))
                }
            }
        }
        val score = distances[destination] ?: return RouteResult.Unavailable(
            listOf("No connected route satisfies the current profile and restrictions.") + excluded.values.distinct())
        val edges = mutableListOf<Edge>(); var cursor = destination
        while(cursor != start) { val edge = previous.getValue(cursor); edges.add(edge); cursor = edge.from }
        edges.reverse()
        return RouteResult.Found(listOf(nodes.getValue(start)) + edges.map { nodes.getValue(it.to) }, edges, edges.sumOf { it.length }, score, state.revision, state.map.version)
    }
}

object ReportRules {
    fun review(report: BarrierReport, status: ReportStatus, reason: String, now: Long): BarrierReport {
        require(reason.trim().length in 5..1000) { "Decision reason must contain 5–1000 characters." }
        val allowed = when(report.status) {
            ReportStatus.PENDING -> setOf(ReportStatus.VERIFIED, ReportStatus.REJECTED)
            ReportStatus.VERIFIED -> setOf(ReportStatus.RESOLVED)
            else -> emptySet()
        }
        require(status in allowed) { "Invalid report transition. Reload the latest report." }
        return report.copy(status = status, reason = reason.trim(), reviewedAt = now)
    }
}
