package com.accesspath.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val MapJson = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable enum class Segment { CORRIDOR, OUTDOOR, DOOR, RAMP, STAIRS, ELEVATOR }
@Serializable enum class Mobility { STANDARD, AVOID_STAIRS, WHEELCHAIR }
@Serializable enum class Availability { AVAILABLE, UNAVAILABLE, UNKNOWN }
@Serializable enum class ReportStatus { PENDING, VERIFIED, REJECTED, RESOLVED }
@Serializable enum class DoorType { NONE, AUTOMATIC, MANUAL_LIGHT, HEAVY, ASSISTANCE_REQUIRED }
@Serializable enum class SurfaceCondition { SMOOTH_TILES, PAVED, UNEVEN, DAMAGED }

@Serializable data class Building(val id: String, val name: String)
@Serializable data class Floor(val id: String, val buildingId: String, val label: String, val ordinal: Int)
@Serializable data class Node(val id: String, val floorId: String, val name: String, val x: Double, val y: Double, val type: String = "LANDMARK")
@Serializable data class Edge(
    val id: String, val from: String, val to: String, val type: Segment, val length: Double,
    val width: Double? = null, val slope: Double? = null, val stepFree: Boolean? = null,
    val wheelchairSuitable: Boolean? = null, val facilityId: String? = null,
    val restricted: Boolean = false, val multiplier: Double? = null, val penalty: Double? = null,
    val doorType: DoorType = DoorType.NONE,
    val surface: SurfaceCondition = SurfaceCondition.SMOOTH_TILES
)
@Serializable data class Facility(val id: String, val name: String, val availability: Availability = Availability.AVAILABLE,
    val reason: String = "Seeded demo status", val updatedAt: Long = 0, val source: String = "Seeded")
@Serializable data class CampusMap(
    val schemaVersion: Int = 1, val version: String, val name: String,
    val provenance: String, val verifiedAt: Long? = null, val buildings: List<Building>,
    val floors: List<Floor>, val nodes: List<Node>, val edges: List<Edge>, val facilities: List<Facility>
)
@Serializable data class Profile(
    val mobility: Mobility = Mobility.WHEELCHAIR,
    val minWidth: Double = 0.85,
    val maxSlope: Double = 8.0,
    val maxWalkingDistance: Double? = null
) {
    fun validate() {
        require(minWidth.isFinite() && minWidth > 0 && minWidth <= 5) { "Width must be above 0 and at most 5 metres." }
        require(maxSlope.isFinite() && maxSlope >= 0 && maxSlope <= 100) { "Slope must be between 0 and 100 percent." }
        if (maxWalkingDistance != null) {
            require(maxWalkingDistance.isFinite() && maxWalkingDistance > 0 && maxWalkingDistance <= 20000) {
                "Max walking distance must be positive and realistic (up to 20 km)."
            }
        }
    }
}
@Serializable data class BarrierReport(
    val id: String, val edgeId: String, val category: String, val description: String,
    val createdAt: Long, val status: ReportStatus = ReportStatus.PENDING, val reason: String = "",
    val reviewedAt: Long? = null, val photoUri: String? = null, val hasVoiceNote: Boolean = false,
    val expiryHours: Int = 24
)
@Serializable data class AssistanceRequest(
    val id: String, val nodeId: String, val nodeName: String,
    val urgency: String = "STANDARD", val note: String = "",
    val requestedAt: Long, val status: String = "OPEN"
)
@Serializable data class AuditEvent(val id: String, val action: String, val target: String, val before: String,
    val after: String, val reason: String, val time: Long, val actor: String = "Local demo reviewer")
@Serializable data class AppSnapshot(
    val map: CampusMap, val revision: Long = 0, val reports: List<BarrierReport> = emptyList(),
    val audit: List<AuditEvent> = emptyList(), val blockedEdges: Set<String> = emptySet(),
    val profile: Profile = Profile(), val start: String = "gate", val destination: String = "classroom",
    val current: String = "gate", val selectedFloor: String = "a0",
    val assistanceRequests: List<AssistanceRequest> = emptyList()
)

object MapValidator {
    fun validate(map: CampusMap): List<String> {
        require(map.schemaVersion == 1) { "Unsupported map schema ${map.schemaVersion}. Expected 1." }
        require(map.name.isNotBlank() && map.version.isNotBlank() && map.provenance.isNotBlank()) { "Map identity and provenance are required." }
        require(map.nodes.size in 1..10000 && map.edges.size <= 50000) { "Map exceeds 10,000 nodes or 50,000 edges." }
        fun unique(ids: List<String>, label: String) { require(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size) { "Duplicate or empty $label IDs." } }
        unique(map.buildings.map { it.id }, "building"); unique(map.floors.map { it.id }, "floor")
        unique(map.nodes.map { it.id }, "node"); unique(map.edges.map { it.id }, "edge"); unique(map.facilities.map { it.id }, "facility")
        val buildings = map.buildings.map { it.id }.toSet(); val floors = map.floors.associateBy { it.id }; val nodes = map.nodes.associateBy { it.id }
        val facilities = map.facilities.map { it.id }.toSet()
        map.floors.forEach { require(it.buildingId in buildings && it.label.isNotBlank()) { "Invalid floor ${it.id}." } }
        map.nodes.forEach { require(it.floorId in floors && it.name.isNotBlank() && it.x.isFinite() && it.y.isFinite() && it.x in 0.0..10000.0 && it.y in 0.0..10000.0) { "Invalid node ${it.id}." } }
        map.edges.forEach { e ->
            require(e.from in nodes && e.to in nodes && e.from != e.to) { "Invalid endpoints for ${e.id}." }
            require(e.length.isFinite() && e.length >= 0) { "Invalid length for ${e.id}." }
            listOf(e.width, e.slope, e.multiplier, e.penalty).filterNotNull().forEach { require(it.isFinite() && it >= 0) { "Invalid metadata for ${e.id}." } }
            require(e.facilityId == null || e.facilityId in facilities) { "Unknown facility for ${e.id}." }
            require(e.type != Segment.ELEVATOR || e.facilityId != null) { "Elevator ${e.id} needs a facility." }
            val a = floors.getValue(nodes.getValue(e.from).floorId); val b = floors.getValue(nodes.getValue(e.to).floorId)
            if (a.id != b.id) require((a.buildingId == b.buildingId && e.type in setOf(Segment.STAIRS, Segment.ELEVATOR, Segment.RAMP)) ||
                (a.buildingId != b.buildingId && a.ordinal == b.ordinal && e.type == Segment.OUTDOOR)) { "Invalid floor transition for ${e.id}." }
        }
        val reached = mutableSetOf(map.nodes.first().id); val adjacency = map.edges.flatMap { listOf(it.from to it.to, it.to to it.from) }.groupBy({ it.first }, { it.second })
        val queue = ArrayDeque(reached); while(queue.isNotEmpty()) adjacency[queue.removeFirst()].orEmpty().forEach { if (reached.add(it)) queue.add(it) }
        return if(reached.size < map.nodes.size) listOf("Map contains disconnected areas; some destinations may be unreachable.") else emptyList()
    }
}
