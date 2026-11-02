package com.accesspath.domain

import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Serializable
enum class MappingStatus { IDLE, RECORDING, PAUSED, STOPPED }

@Serializable
enum class OccupancyCell { UNKNOWN, FREE, OBSTACLE, LANDMARK }

@Serializable
data class TrailPoint(
    val x: Double,
    val y: Double,
    val floorId: String,
    val confidence: Float,
    val timestamp: Long
)

@Serializable
data class MappingConsent(
    val agreed: Boolean,
    val timestamp: Long,
    val deviceHardware: String
)

@Serializable
data class OccupancyGrid(
    val gridSize: Int = 30,
    val resolutionMeters: Double = 1.0,
    val cells: List<OccupancyCell> = List(30 * 30) { OccupancyCell.UNKNOWN }
) {
    fun getCell(xIdx: Int, yIdx: Int): OccupancyCell {
        if (xIdx !in 0 until gridSize || yIdx !in 0 until gridSize) return OccupancyCell.UNKNOWN
        return cells[yIdx * gridSize + xIdx]
    }

    fun update(xIdx: Int, yIdx: Int, cell: OccupancyCell): OccupancyGrid {
        if (xIdx !in 0 until gridSize || yIdx !in 0 until gridSize) return this
        val index = yIdx * gridSize + xIdx
        val newCells = cells.toMutableList()
        newCells[index] = cell
        return copy(cells = newCells)
    }

    val freeCount: Int get() = cells.count { it == OccupancyCell.FREE }
    val obstacleCount: Int get() = cells.count { it == OccupancyCell.OBSTACLE }
    val unknownCount: Int get() = cells.count { it == OccupancyCell.UNKNOWN }
}

@Serializable
data class MappingSession(
    val id: String,
    val floorId: String = "a0",
    val status: MappingStatus = MappingStatus.IDLE,
    val consent: MappingConsent? = null,
    val trail: List<TrailPoint> = emptyList(),
    val grid: OccupancyGrid = OccupancyGrid(),
    val currentConfidence: Float = 1.0f,
    val trackingLost: Boolean = false,
    val distanceTraversed: Double = 0.0
) {
    fun addStep(stepLength: Double, headingRad: Double, confidence: Float, now: Long): MappingSession {
        if (status != MappingStatus.RECORDING) return this
        val lastPoint = trail.lastOrNull() ?: TrailPoint(15.0, 15.0, floorId, 1.0f, now)
        val newX = (lastPoint.x + stepLength * cos(headingRad)).coerceIn(1.0, 28.0)
        val newY = (lastPoint.y + stepLength * sin(headingRad)).coerceIn(1.0, 28.0)
        val isLost = confidence < 0.45f

        val xIdx = newX.roundToInt().coerceIn(0, grid.gridSize - 1)
        val yIdx = newY.roundToInt().coerceIn(0, grid.gridSize - 1)
        val newGrid = grid.update(xIdx, yIdx, OccupancyCell.FREE)

        val newPoint = TrailPoint(newX, newY, floorId, confidence, now)
        return copy(
            trail = trail + newPoint,
            grid = newGrid,
            currentConfidence = confidence,
            trackingLost = isLost,
            distanceTraversed = distanceTraversed + stepLength
        )
    }


    /**
     * Continuous wheelchair displacement dead-reckoning engine.
     * Computes incremental displacement from accelerometer/wheel speed rather than footstep detection.
     */
    fun addWheelchairMovement(displacementMeters: Double, headingRad: Double, confidence: Float, now: Long): MappingSession {
        if (status != MappingStatus.RECORDING) return this
        val lastPoint = trail.lastOrNull() ?: TrailPoint(15.0, 15.0, floorId, 1.0f, now)
        val newX = (lastPoint.x + displacementMeters * kotlin.math.cos(headingRad)).coerceIn(1.0, 28.0)
        val newY = (lastPoint.y + displacementMeters * kotlin.math.sin(headingRad)).coerceIn(1.0, 28.0)
        val isLost = confidence < 0.45f

        val xIdx = newX.roundToInt().coerceIn(0, grid.gridSize - 1)
        val yIdx = newY.roundToInt().coerceIn(0, grid.gridSize - 1)
        val newGrid = grid.update(xIdx, yIdx, OccupancyCell.FREE)

        val newPoint = TrailPoint(newX, newY, floorId, confidence, now)
        return copy(
            trail = trail + newPoint,
            grid = newGrid,
            currentConfidence = confidence,
            trackingLost = isLost,
            distanceTraversed = distanceTraversed + displacementMeters
        )
    }

    fun recordObstacle(distanceAhead: Double, headingRad: Double): MappingSession {
        if (status != MappingStatus.RECORDING) return this
        val lastPoint = trail.lastOrNull() ?: return this
        val obsX = (lastPoint.x + distanceAhead * cos(headingRad)).roundToInt().coerceIn(0, grid.gridSize - 1)
        val obsY = (lastPoint.y + distanceAhead * sin(headingRad)).roundToInt().coerceIn(0, grid.gridSize - 1)
        return copy(grid = grid.update(obsX, obsY, OccupancyCell.OBSTACLE))
    }

    /**
     * Converts the recorded survey trail into verified landmarks and corridors.
     */
    fun convertToNavigationElements(prefix: String = "survey_${System.currentTimeMillis() % 100000}"): Pair<List<Node>, List<Edge>> {
        if (trail.size < 2) return Pair(emptyList(), emptyList())
        val sampled = trail.filterIndexed { i, _ -> i == 0 || i == trail.size - 1 || i % 3 == 0 }
        val nodes = sampled.mapIndexed { idx, pt ->
            Node(
                id = "${prefix}_n$idx",
                floorId = pt.floorId,
                name = "Survey Point ${idx + 1}",
                x = pt.x * 5.0,
                y = pt.y * 5.0,
                type = "SURVEY_LANDMARK"
            )
        }
        val edges = mutableListOf<Edge>()
        for (i in 0 until nodes.size - 1) {
            val from = nodes[i]
            val to = nodes[i + 1]
            val dx = to.x - from.x
            val dy = to.y - from.y
            val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
            val forward = Edge(
                id = "${prefix}_e$i",
                from = from.id,
                to = to.id,
                type = Segment.CORRIDOR,
                length = length,
                width = 1.5,
                slope = 0.0,
                stepFree = true,
                wheelchairSuitable = true
            )
            val reverse = forward.copy(id = "${forward.id}_r", from = to.id, to = from.id)
            edges.add(forward)
            edges.add(reverse)
        }
        return Pair(nodes, edges)
    }

    /**
     * Connects newly surveyed path to nearest existing campus nodes so it integrates into navigation.
     */
    fun stitchToCampus(existingNodes: List<Node>, surveyNodes: List<Node>): List<Edge> {
        if (existingNodes.isEmpty() || surveyNodes.isEmpty()) return emptyList()
        val startSurvey = surveyNodes.first()
        val sameFloor = existingNodes.filter { it.floorId == startSurvey.floorId }
        val closest = (if (sameFloor.isNotEmpty()) sameFloor else existingNodes).minByOrNull { n ->
            val dx = n.x - startSurvey.x
            val dy = n.y - startSurvey.y
            dx * dx + dy * dy
        } ?: return emptyList()

        val dx = startSurvey.x - closest.x
        val dy = startSurvey.y - closest.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1.0)
        val forward = Edge(
            id = "stitch_${closest.id}_${startSurvey.id}",
            from = closest.id,
            to = startSurvey.id,
            type = Segment.CORRIDOR,
            length = length,
            width = 1.8,
            slope = 0.0,
            stepFree = true,
            wheelchairSuitable = true
        )
        val reverse = forward.copy(
            id = "stitch_${startSurvey.id}_${closest.id}",
            from = startSurvey.id,
            to = closest.id
        )
        return listOf(forward, reverse)
    }

}
