package com.accesspath.domain

import kotlinx.serialization.Serializable

@Serializable
data class ReviewerAccount(
    val reviewerId: String,
    val displayName: String,
    val role: String = "CERTIFIED_SURVEYOR",
    val authToken: String = "Bearer accesspath_demo_token"
)

@Serializable
data class SyncPayload(
    val mapVersion: String,
    val facilities: List<Facility>,
    val reports: List<BarrierReport>,
    val exportedAt: Long,
    val publisher: String
)

@Serializable
data class ConflictRecord(
    val entityId: String,
    val localValue: String,
    val remoteValue: String,
    val resolvedValue: String,
    val reason: String
)

object ConflictResolver {
    /**
     * Resolves facility statuses between local and remote datasets using a Safety-First policy.
     * Rule: If either side marks a facility as UNAVAILABLE, the resolved state MUST be UNAVAILABLE.
     */
    fun resolveFacilities(local: List<Facility>, remote: List<Facility>): Pair<List<Facility>, List<ConflictRecord>> {
        val remoteMap = remote.associateBy { it.id }
        val conflicts = mutableListOf<ConflictRecord>()
        val resolved = local.map { loc ->
            val rem = remoteMap[loc.id]
            if (rem == null || rem.availability == loc.availability) {
                loc
            } else {
                val decidedAvailability = when {
                    loc.availability == Availability.UNAVAILABLE || rem.availability == Availability.UNAVAILABLE -> Availability.UNAVAILABLE
                    loc.availability == Availability.UNKNOWN || rem.availability == Availability.UNKNOWN -> Availability.UNKNOWN
                    else -> Availability.AVAILABLE
                }
                conflicts.add(
                    ConflictRecord(
                        entityId = loc.id,
                        localValue = loc.availability.name,
                        remoteValue = rem.availability.name,
                        resolvedValue = decidedAvailability.name,
                        reason = "Safety-first conflict rule: conservative closure adopted"
                    )
                )
                loc.copy(
                    availability = decidedAvailability,
                    reason = "Synced: ${loc.reason} / Remote: ${rem.reason}",
                    updatedAt = maxOf(loc.updatedAt, rem.updatedAt),
                    source = "Safety-First Sync"
                )
            }
        }
        return Pair(resolved, conflicts)
    }

    /**
     * Merges barrier reports from local and remote.
     */
    fun resolveReports(local: List<BarrierReport>, remote: List<BarrierReport>): Pair<List<BarrierReport>, List<ConflictRecord>> {
        val conflicts = mutableListOf<ConflictRecord>()
        val localMap = local.associateBy { it.id }.toMutableMap()

        remote.forEach { rem ->
            val loc = localMap[rem.id]
            if (loc == null) {
                localMap[rem.id] = rem
            } else if (loc.status != rem.status) {
                // If either is VERIFIED or RESOLVED, prefer the more advanced state
                val decidedStatus = when {
                    loc.status == ReportStatus.RESOLVED || rem.status == ReportStatus.RESOLVED -> ReportStatus.RESOLVED
                    loc.status == ReportStatus.VERIFIED || rem.status == ReportStatus.VERIFIED -> ReportStatus.VERIFIED
                    loc.status == ReportStatus.REJECTED || rem.status == ReportStatus.REJECTED -> ReportStatus.REJECTED
                    else -> ReportStatus.PENDING
                }
                conflicts.add(
                    ConflictRecord(
                        entityId = rem.id,
                        localValue = loc.status.name,
                        remoteValue = rem.status.name,
                        resolvedValue = decidedStatus.name,
                        reason = "Report transition priority merge"
                    )
                )
                localMap[rem.id] = loc.copy(
                    status = decidedStatus,
                    reason = if (loc.reason.isNotBlank()) loc.reason else rem.reason,
                    reviewedAt = maxOf(loc.reviewedAt ?: 0, rem.reviewedAt ?: 0)
                )
            }
        }
        return Pair(localMap.values.toList(), conflicts)
    }
}
