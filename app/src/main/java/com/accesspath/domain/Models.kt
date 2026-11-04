package com.accesspath.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json

/** JSON used for both bundled/imported maps and the persisted app state. */
val MapJson = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable enum class Segment { CORRIDOR, OUTDOOR, DOOR, RAMP, STAIRS, ELEVATOR }
@Serializable enum class Mobility { STANDARD, AVOID_STAIRS, WHEELCHAIR }
@Serializable enum class Availability { AVAILABLE, UNAVAILABLE, UNKNOWN }
@Serializable enum class ReportStatus { PENDING, VERIFIED, REJECTED, RESOLVED }

/** Barrier categories a person can report. Stored by name, so do not rename entries. */
@Serializable(with = BarrierCategorySerializer::class)
enum class BarrierCategory { BLOCKED_PATH, LIFT_NOT_WORKING, DAMAGED_RAMP, TOO_NARROW, STEP_OR_THRESHOLD, OTHER }

/** Reads both current names and the English labels saved by earlier app versions. */
object BarrierCategorySerializer : KSerializer<BarrierCategory> {
    private val legacy = mapOf(
        "Blocked path" to BarrierCategory.BLOCKED_PATH,
        "Unavailable elevator" to BarrierCategory.LIFT_NOT_WORKING,
        "Damaged ramp" to BarrierCategory.DAMAGED_RAMP,
        "Unsuitable width" to BarrierCategory.TOO_NARROW,
        "Steps or threshold" to BarrierCategory.STEP_OR_THRESHOLD
    )
    override val descriptor = PrimitiveSerialDescriptor("BarrierCategory", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BarrierCategory) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): BarrierCategory {
        val raw = decoder.decodeString()
        return BarrierCategory.entries.firstOrNull { it.name == raw } ?: legacy[raw] ?: BarrierCategory.OTHER
    }
}

/**
 * Places a floor-local coordinate frame on the real world: (0,0) in metres sits at [lat]/[lng],
 * x grows east and y grows south, both turned clockwise by [rotationDeg].
 */
@Serializable data class GeoAnchor(val lat: Double, val lng: Double, val rotationDeg: Double = 0.0)

/** [draft] marks an area recorded with the camera that a reviewer has not checked yet. */
@Serializable data class Building(val id: String, val name: String, val geo: GeoAnchor? = null, val draft: Boolean = false)
@Serializable data class Floor(val id: String, val buildingId: String, val label: String, val ordinal: Int)
@Serializable data class Node(
    val id: String, val floorId: String, val name: String, val x: Double, val y: Double,
    val type: String = "LANDMARK"
)
@Serializable data class Edge(
    val id: String, val from: String, val to: String, val type: Segment, val length: Double,
    val width: Double? = null, val slope: Double? = null, val stepFree: Boolean? = null,
    val wheelchairSuitable: Boolean? = null, val facilityId: String? = null,
    val restricted: Boolean = false, val multiplier: Double? = null, val penalty: Double? = null
)
@Serializable data class Facility(
    val id: String, val name: String, val availability: Availability = Availability.AVAILABLE,
    val reason: String = "", val updatedAt: Long = 0, val source: String = "Map data"
)
@Serializable data class CampusMap(
    /** Stable campus identifier shared with the server; reports and statuses are stored per campus. */
    val id: String = "main-campus",
    val schemaVersion: Int = 1, val version: String, val name: String,
    val provenance: String, val verifiedAt: Long? = null, val geo: GeoAnchor? = null, val buildings: List<Building>,
    val floors: List<Floor>, val nodes: List<Node>, val edges: List<Edge>, val facilities: List<Facility>
) {
    /** Older app versions shipped an illustrative sample campus; it is removed when found. */
    val isDemo: Boolean get() = provenance == DEMO_PROVENANCE

    companion object { const val DEMO_PROVENANCE = "Illustrative demo" }
}

@Serializable data class Profile(
    val mobility: Mobility = Mobility.WHEELCHAIR,
    val minWidth: Double = 0.85,
    val maxSlope: Double = 8.0,
    val maxWalkingDistance: Double? = null
) {
    val isValid: Boolean get() =
        minWidth.isFinite() && minWidth > 0 && minWidth <= 5 &&
            maxSlope.isFinite() && maxSlope >= 0 && maxSlope <= 100 &&
            (maxWalkingDistance == null || (maxWalkingDistance.isFinite() && maxWalkingDistance > 0 && maxWalkingDistance <= 20000))

    fun validate() { if (!isValid) throw AppError(ErrorCode.INVALID_PROFILE) }
}

@Serializable data class BarrierReport(
    val id: String, val edgeId: String, val category: BarrierCategory, val description: String,
    val createdAt: Long, val status: ReportStatus = ReportStatus.PENDING, val reason: String = "",
    val reviewedAt: Long? = null,
    /** False while a report made on this phone has not reached the campus server yet. */
    val synced: Boolean = true
) {
    val isActive: Boolean get() = status == ReportStatus.PENDING || status == ReportStatus.VERIFIED
}

@Serializable data class AuditEvent(
    val id: String, val action: String, val target: String, val before: String,
    val after: String, val reason: String, val time: Long, val actor: String = "Local reviewer"
)

@Serializable data class AppSnapshot(
    val map: CampusMap, val revision: Long = 0, val reports: List<BarrierReport> = emptyList(),
    val audit: List<AuditEvent> = emptyList(), val blockedEdges: Set<String> = emptySet(),
    val profile: Profile = Profile(), val start: String = "gate", val destination: String = "classroom",
    val current: String = "gate", val selectedFloor: String = "a0"
) {
    fun node(id: String): Node? = map.nodes.firstOrNull { it.id == id }
    fun floor(id: String): Floor? = map.floors.firstOrNull { it.id == id }
    fun edge(id: String): Edge? = map.edges.firstOrNull { it.id == id }
}

/** Errors a person can understand and fix. The UI maps each code to a translated message. */
enum class ErrorCode {
    NOT_LOADED, STALE_DATA, CONCURRENT_UPDATE, INVALID_PROFILE, UNKNOWN_LOCATION, UNKNOWN_SEGMENT,
    DESCRIPTION_LENGTH, REASON_LENGTH, REPORT_NOT_FOUND, INVALID_TRANSITION, FACILITY_NOT_FOUND,
    RESET_ONLY_DEMO, MAP_TOO_LARGE, MAP_INVALID, IMPORT_ORPHANS_REPORTS,
    NEEDS_SIGN_IN, NEEDS_REVIEWER, NEEDS_ADMIN, NETWORK, SERVER
}

class AppError(val code: ErrorCode, val detail: String? = null) :
    IllegalArgumentException(detail?.let { "$code: $it" } ?: code.name)

object MapValidator {
    const val MAX_BYTES = 10 * 1024 * 1024

    /** Throws [AppError] with [ErrorCode.MAP_INVALID] for an unusable map; returns non-fatal warnings. */
    fun validate(map: CampusMap): List<String> {
        fun check(ok: Boolean, detail: () -> String) { if (!ok) throw AppError(ErrorCode.MAP_INVALID, detail()) }
        check(map.schemaVersion == 1) { "Unsupported map schema ${map.schemaVersion}. Expected 1." }
        check(map.name.isNotBlank() && map.version.isNotBlank() && map.provenance.isNotBlank()) { "Map name, version and provenance are required." }
        check(map.nodes.size <= 10000 && map.edges.size <= 50000) { "Map must have at most 10,000 places and 50,000 segments." }
        fun unique(ids: List<String>, label: String) = check(ids.all { it.isNotBlank() } && ids.toSet().size == ids.size) { "Duplicate or empty $label IDs." }
        unique(map.buildings.map { it.id }, "building"); unique(map.floors.map { it.id }, "floor")
        unique(map.nodes.map { it.id }, "place"); unique(map.edges.map { it.id }, "segment"); unique(map.facilities.map { it.id }, "facility")
        val buildings = map.buildings.map { it.id }.toSet()
        val floors = map.floors.associateBy { it.id }
        val nodes = map.nodes.associateBy { it.id }
        val facilities = map.facilities.map { it.id }.toSet()
        (listOfNotNull(map.geo) + map.buildings.mapNotNull { it.geo }).forEach { g ->
            check(g.lat.isFinite() && g.lng.isFinite() && g.lat in -85.0..85.0 && g.lng in -180.0..180.0 && g.rotationDeg.isFinite()) { "Invalid geo position." }
        }
        map.floors.forEach { check(it.buildingId in buildings && it.label.isNotBlank()) { "Invalid floor ${it.id}." } }
        map.nodes.forEach {
            check(it.floorId in floors && it.name.isNotBlank() && it.x.isFinite() && it.y.isFinite() &&
                it.x in 0.0..10000.0 && it.y in 0.0..10000.0) { "Invalid place ${it.id}." }
        }
        map.edges.forEach { e ->
            check(e.from in nodes && e.to in nodes && e.from != e.to) { "Invalid endpoints for ${e.id}." }
            check(e.length.isFinite() && e.length >= 0) { "Invalid length for ${e.id}." }
            listOfNotNull(e.width, e.slope, e.multiplier, e.penalty).forEach { check(it.isFinite() && it >= 0) { "Invalid metadata for ${e.id}." } }
            check(e.facilityId == null || e.facilityId in facilities) { "Unknown facility for ${e.id}." }
            check(e.type != Segment.ELEVATOR || e.facilityId != null) { "Lift segment ${e.id} needs a facility." }
            val a = floors.getValue(nodes.getValue(e.from).floorId)
            val b = floors.getValue(nodes.getValue(e.to).floorId)
            if (a.id != b.id) check(
                (a.buildingId == b.buildingId && e.type in setOf(Segment.STAIRS, Segment.ELEVATOR, Segment.RAMP)) ||
                    (a.buildingId != b.buildingId && a.ordinal == b.ordinal && e.type == Segment.OUTDOOR)
            ) { "Invalid floor transition for ${e.id}." }
        }
        if (map.nodes.isEmpty()) return emptyList()
        val reached = mutableSetOf(map.nodes.first().id)
        val adjacency = map.edges.flatMap { listOf(it.from to it.to, it.to to it.from) }.groupBy({ it.first }, { it.second })
        val queue = ArrayDeque(reached)
        while (queue.isNotEmpty()) adjacency[queue.removeFirst()].orEmpty().forEach { if (reached.add(it)) queue.add(it) }
        return if (reached.size < map.nodes.size) listOf("Some places are not connected to the rest of the map.") else emptyList()
    }
}

object ReportRules {
    const val MIN_TEXT = 5
    const val MAX_TEXT = 1000

    fun review(report: BarrierReport, status: ReportStatus, reason: String, now: Long): BarrierReport {
        if (reason.trim().length !in MIN_TEXT..MAX_TEXT) throw AppError(ErrorCode.REASON_LENGTH)
        val allowed = when (report.status) {
            ReportStatus.PENDING -> setOf(ReportStatus.VERIFIED, ReportStatus.REJECTED)
            ReportStatus.VERIFIED -> setOf(ReportStatus.RESOLVED)
            else -> emptySet()
        }
        if (status !in allowed) throw AppError(ErrorCode.INVALID_TRANSITION)
        return report.copy(status = status, reason = reason.trim(), reviewedAt = now)
    }
}
