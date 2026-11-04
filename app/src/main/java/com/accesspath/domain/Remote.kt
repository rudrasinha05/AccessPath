package com.accesspath.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

// Rows as the campus server (Supabase) stores them. Field names match the SQL columns.

@Serializable data class RemoteProfile(
    val id: String, val email: String? = null,
    @SerialName("display_name") val displayName: String? = null, val role: String = "student"
)

@Serializable data class RemoteReport(
    val id: String, @SerialName("map_id") val mapId: String, @SerialName("edge_id") val edgeId: String,
    val category: String, val description: String, val status: String = "PENDING", val reason: String = "",
    @SerialName("created_at") val createdAt: String? = null, @SerialName("reviewed_at") val reviewedAt: String? = null
)

@Serializable data class RemoteFacility(
    @SerialName("map_id") val mapId: String, @SerialName("facility_id") val facilityId: String,
    val availability: String, val reason: String, @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable data class RemoteClosure(
    @SerialName("map_id") val mapId: String, @SerialName("edge_id") val edgeId: String,
    val closed: Boolean, val reason: String, @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable data class RemoteAudit(
    val id: String, val action: String, val target: String,
    @SerialName("before_value") val before: String = "", @SerialName("after_value") val after: String = "",
    val reason: String = "", @SerialName("created_at") val createdAt: String? = null
)

@Serializable data class RemoteMap(val id: String, val version: String)

enum class Role { STUDENT, REVIEWER, ADMIN;
    val canReview get() = this == REVIEWER || this == ADMIN
    companion object { fun of(s: String?) = entries.firstOrNull { it.name.equals(s, true) } ?: STUDENT }
}

/** Server data wins for shared state; reports made on this phone that have not been sent yet are kept. */
object RemoteMerge {
    fun millis(iso: String?): Long = iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() } ?: 0L

    fun apply(
        s: AppSnapshot,
        reports: List<RemoteReport>,
        facilities: List<RemoteFacility>,
        closures: List<RemoteClosure>,
        audit: List<RemoteAudit>?
    ): AppSnapshot {
        val mapId = s.map.id
        val remoteReports = reports.filter { it.mapId == mapId }.mapNotNull { r ->
            val status = ReportStatus.entries.firstOrNull { it.name == r.status } ?: return@mapNotNull null
            BarrierReport(
                id = r.id, edgeId = r.edgeId,
                category = BarrierCategory.entries.firstOrNull { it.name == r.category } ?: BarrierCategory.OTHER,
                description = r.description, createdAt = millis(r.createdAt), status = status,
                reason = r.reason, reviewedAt = r.reviewedAt?.let(::millis), synced = true
            )
        }
        val remoteIds = remoteReports.map { it.id }.toSet()
        val unsent = s.reports.filter { !it.synced && it.id !in remoteIds }
        val status = facilities.filter { it.mapId == mapId }.associateBy { it.facilityId }
        val newFacilities = s.map.facilities.map { f ->
            status[f.id]?.let { r ->
                f.copy(
                    availability = Availability.entries.firstOrNull { it.name == r.availability } ?: Availability.UNKNOWN,
                    reason = r.reason, updatedAt = millis(r.updatedAt), source = "Campus server"
                )
            } ?: f
        }
        // A closure covers both directions of the physical segment.
        val closedIds = closures.filter { it.mapId == mapId && it.closed }.map { it.edgeId }.toSet()
        val blocked = s.map.edges.filter { e ->
            closedIds.any { id ->
                val c = s.map.edges.firstOrNull { it.id == id } ?: return@any false
                (e.from == c.from && e.to == c.to) || (e.from == c.to && e.to == c.from)
            }
        }.map { it.id }.toSet()
        return s.copy(
            reports = (remoteReports + unsent).filter { r -> s.map.edges.any { it.id == r.edgeId } },
            map = s.map.copy(facilities = newFacilities),
            blockedEdges = blocked,
            audit = audit?.map {
                AuditEvent(it.id, it.action, it.target, it.before, it.after, it.reason, millis(it.createdAt), actor = "Campus server")
            }?.sortedBy { it.time } ?: s.audit
        )
    }
}
