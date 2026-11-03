package com.accesspath.domain

/** One instruction in a route. Consecutive lift or stair segments are merged into a single step. */
data class RouteStep(
    val type: Segment,
    val from: Node,
    val to: Node,
    val distance: Double,
    /** Facility name for lifts, e.g. "Lift A". */
    val facilityName: String? = null,
    /** Floor label of [to] when the step changes floor. */
    val toFloorLabel: String? = null,
    /** Index into the route's node list of the step's end point. */
    val endNodeIndex: Int
)

object RouteSteps {
    fun build(route: RouteResult.Found, map: CampusMap): List<RouteStep> {
        val floors = map.floors.associateBy { it.id }
        val facilities = map.facilities.associateBy { it.id }
        val steps = mutableListOf<RouteStep>()
        route.edges.forEachIndexed { i, edge ->
            val from = route.nodes[i]
            val to = route.nodes[i + 1]
            val last = steps.lastOrNull()
            val mergeable = edge.type == Segment.ELEVATOR || edge.type == Segment.STAIRS
            val floorLabel = if (from.floorId != to.floorId) floors[to.floorId]?.label else null
            if (last != null && mergeable && last.type == edge.type && last.to.id == from.id &&
                (edge.type != Segment.ELEVATOR || last.facilityName == facilities[edge.facilityId]?.name)
            ) {
                steps[steps.lastIndex] = last.copy(
                    to = to, distance = last.distance + edge.length,
                    toFloorLabel = floorLabel ?: last.toFloorLabel, endNodeIndex = i + 1
                )
            } else {
                steps += RouteStep(
                    type = edge.type, from = from, to = to, distance = edge.length,
                    facilityName = edge.facilityId?.let { facilities[it]?.name },
                    toFloorLabel = floorLabel, endNodeIndex = i + 1
                )
            }
        }
        return steps
    }
}
