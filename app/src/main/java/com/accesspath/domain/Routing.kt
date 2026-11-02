package com.accesspath.domain

import java.util.PriorityQueue

sealed interface RouteResult {
    data class Found(
        val nodes: List<Node>,
        val edges: List<Edge>,
        val distance: Double,
        val score: Double,
        val revision: Long,
        val mapVersion: String,
        val explanation: String = "",
        val exceedsMaxWalkingDistance: Boolean = false
    ) : RouteResult
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

    private fun dijkstra(state: AppSnapshot, start: String, destination: String, profile: Profile): Pair<List<Edge>, Double>? {
        val excluded = exclusions(state, profile)
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
        val score = distances[destination] ?: return null
        val edges = mutableListOf<Edge>(); var cursor = destination
        while(cursor != start) { val edge = previous.getValue(cursor); edges.add(edge); cursor = edge.from }
        edges.reverse()
        return Pair(edges, score)
    }

    fun calculate(state: AppSnapshot, start: String = state.current, destination: String = state.destination): RouteResult {
        state.profile.validate()
        val nodes = state.map.nodes.associateBy { it.id }
        if(start !in nodes || destination !in nodes) return RouteResult.Unavailable(listOf("Choose a valid start and destination."))
        val excluded = exclusions(state)

        val routeData = dijkstra(state, start, destination, state.profile)
            ?: return RouteResult.Unavailable(
                listOf("No connected route satisfies the current profile and restrictions.") + excluded.values.distinct()
            )

        val edges = routeData.first
        val score = routeData.second
        val totalDistance = edges.sumOf { it.length }

        // Route Explanation & Comparison
        val explanationParts = mutableListOf<String>()
        val usesLift = edges.any { it.type == Segment.ELEVATOR }
        val usesRamp = edges.any { it.type == Segment.RAMP }

        if (state.profile.mobility == Mobility.WHEELCHAIR) {
            explanationParts.add("Step-free wheelchair route.")
            if (usesLift) explanationParts.add("Uses accessible elevator.")
            if (usesRamp) explanationParts.add("Uses verified accessible ramp.")
            
            // Compare with standard route distance
            val standardResult = dijkstra(state, start, destination, Profile(Mobility.STANDARD))
            if (standardResult != null) {
                val stdDist = standardResult.first.sumOf { it.length }
                val delta = totalDistance - stdDist
                if (delta > 0.5) {
                    explanationParts.add("${String.format(java.util.Locale.US, "%.1f", delta)} m longer than stair path to ensure step-free access.")
                }
            }
        } else if (state.profile.mobility == Mobility.AVOID_STAIRS) {
            explanationParts.add("Stairs excluded from route. Uses step-free corridors and elevators.")
        } else {
            explanationParts.add("Standard path across shortest campus corridors.")
        }

        val exceedsMax = state.profile.maxWalkingDistance != null && totalDistance > state.profile.maxWalkingDistance
        if (exceedsMax) {
            explanationParts.add("⚠️ Notice: Route distance exceeds preferred limit of ${String.format(java.util.Locale.US, "%.1f", state.profile.maxWalkingDistance)} m.")
        }

        val explanation = explanationParts.joinToString(" ")

        return RouteResult.Found(
            nodes = listOf(nodes.getValue(start)) + edges.map { nodes.getValue(it.to) },
            edges = edges,
            distance = totalDistance,
            score = score,
            revision = state.revision,
            mapVersion = state.map.version,
            explanation = explanation,
            exceedsMaxWalkingDistance = exceedsMax
        )
    }

    /**
     * Finds the nearest accessible facility (e.g. WASHROOM, ELEVATOR, EXIT) from the current node.
     */
    fun findNearestFacility(state: AppSnapshot, facilityCategory: String): Pair<Node, RouteResult.Found>? {
        val candidateNodes = state.map.nodes.filter { n ->
            when (facilityCategory.uppercase()) {
                "WASHROOM" -> n.id.contains("wash", true) || n.name.contains("washroom", true) || n.name.contains("toilet", true)
                "ELEVATOR" -> state.map.edges.any { it.type == Segment.ELEVATOR && (it.from == n.id || it.to == n.id) }
                "EXIT" -> n.id.equals("gate", true) || n.name.contains("gate", true) || n.name.contains("exit", true) || n.type == "EXIT"
                else -> false
            }
        }
        if (candidateNodes.isEmpty()) return null

        var bestNode: Node? = null
        var bestRoute: RouteResult.Found? = null

        for (candidate in candidateNodes) {
            val result = calculate(state, start = state.current, destination = candidate.id)
            if (result is RouteResult.Found) {
                if (bestRoute == null || result.distance < bestRoute.distance) {
                    bestNode = candidate
                    bestRoute = result
                }
            }
        }

        return if (bestNode != null && bestRoute != null) Pair(bestNode, bestRoute) else null
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
