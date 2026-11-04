package com.accesspath.data

import android.util.Log
import androidx.room.withTransaction
import com.accesspath.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.UUID

/**
 * Single source of truth. All state lives in one versioned row so a report decision and its audit
 * entry can never be half-saved. Every change runs in a transaction with a compare-and-set revision.
 */
class CampusRepository(
    private val db: AccessDatabase,
    private val seed: () -> String,
    private val clock: () -> Long = System::currentTimeMillis
) {
    val snapshots: Flow<AppSnapshot?> = db.stateDao().observe()
        .map { row -> row?.let { MapJson.decodeFromString<AppSnapshot>(it.payload) } }
        .flowOn(Dispatchers.Default)

    private fun fresh(): AppSnapshot {
        val map = MapJson.decodeFromString<CampusMap>(seed())
        MapValidator.validate(map)
        val first = map.nodes.first()
        fun pick(id: String, fallback: String) = if (map.nodes.any { it.id == id }) id else fallback
        val start = pick("gate", first.id)
        return AppSnapshot(
            map = map, start = start, current = start,
            destination = pick("classroom", map.nodes.last().id),
            selectedFloor = map.nodes.first { it.id == start }.floorId
        )
    }

    /** Creates the first state, or replaces saved data that can no longer be read (logged, never silent in the audit). */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        db.withTransaction {
            val row = db.stateDao().get()
            if (row == null) {
                db.stateDao().insert(StateRow(revision = 0, payload = MapJson.encodeToString(fresh())))
            } else {
                val readable = runCatching { MapJson.decodeFromString<AppSnapshot>(row.payload) }
                readable.getOrNull()?.let { saved -> withSampleGeo(saved)?.let { upgraded ->
                    db.stateDao().compareAndSet(MapJson.encodeToString(upgraded), saved.revision + 1, saved.revision)
                } }
                if (readable.isFailure) {
                    Log.w(TAG, "Saved state unreadable; restoring bundled map", readable.exceptionOrNull())
                    val recovered = fresh().let {
                        it.copy(revision = row.revision + 1, audit = listOf(event("Recover saved data", "state", "Unreadable", "Bundled map", "Saved data could not be read after an update")))
                    }
                    db.stateDao().compareAndSet(MapJson.encodeToString(recovered), recovered.revision, row.revision)
                }
            }
        }
    }

    /** Saved sample maps from before world-map support get the sample's real-world position. Returns null if nothing changes. */
    private fun withSampleGeo(saved: AppSnapshot): AppSnapshot? {
        if (!saved.map.isDemo || Geo.hasGeo(saved.map)) return null
        val seedMap = runCatching { MapJson.decodeFromString<CampusMap>(seed()) }.getOrNull() ?: return null
        if (seedMap.geo == null) return null
        val buildingGeo = seedMap.buildings.associate { it.id to it.geo }
        return saved.copy(
            revision = saved.revision + 1,
            map = saved.map.copy(geo = seedMap.geo, buildings = saved.map.buildings.map { it.copy(geo = buildingGeo[it.id] ?: it.geo) })
        )
    }

    suspend fun snapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        MapJson.decodeFromString(db.stateDao().get()?.payload ?: throw AppError(ErrorCode.NOT_LOADED))
    }

    private suspend fun mutate(expected: Long? = null, block: (AppSnapshot) -> AppSnapshot) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val row = db.stateDao().get() ?: throw AppError(ErrorCode.NOT_LOADED)
            if (expected != null && expected != row.revision) throw AppError(ErrorCode.STALE_DATA)
            val state = MapJson.decodeFromString<AppSnapshot>(row.payload)
            val changed = block(state)
            if (changed == state) return@withTransaction
            val next = changed.copy(revision = state.revision + 1, audit = changed.audit.takeLast(MAX_AUDIT))
            if (db.stateDao().compareAndSet(MapJson.encodeToString(next), next.revision, state.revision) != 1) {
                throw AppError(ErrorCode.CONCURRENT_UPDATE)
            }
        }
    }

    private fun event(action: String, target: String, before: String, after: String, reason: String) =
        AuditEvent(UUID.randomUUID().toString(), action, target, before, after, reason, clock())

    private fun AppSnapshot.requireNode(id: String): Node = node(id) ?: throw AppError(ErrorCode.UNKNOWN_LOCATION)

    // ---- Journey -------------------------------------------------------------------------------

    suspend fun setProfile(profile: Profile) = mutate { s -> profile.validate(); s.copy(profile = profile) }

    suspend fun setDestination(nodeId: String) = mutate { s -> s.requireNode(nodeId); s.copy(destination = nodeId) }

    /** Confirms where the person is now. Routes always recalculate from this confirmed place, never from a guess. */
    suspend fun setCurrent(nodeId: String) = mutate { s ->
        val node = s.requireNode(nodeId)
        s.copy(current = nodeId, start = nodeId, selectedFloor = node.floorId)
    }

    suspend fun swapEnds() = mutate { s ->
        val destination = s.requireNode(s.destination)
        s.copy(current = s.destination, start = s.destination, destination = s.current, selectedFloor = destination.floorId)
    }

    suspend fun selectFloor(floorId: String) = mutate { s ->
        if (s.floor(floorId) == null) throw AppError(ErrorCode.UNKNOWN_LOCATION)
        s.copy(selectedFloor = floorId)
    }

    // ---- Reports ------------------------------------------------------------------------------

    /** Idempotent: submitting the same [id] twice (e.g. a double tap) stores one report. */
    suspend fun submitReport(id: String, edgeId: String, category: BarrierCategory, description: String) = mutate { s ->
        if (description.trim().length !in ReportRules.MIN_TEXT..ReportRules.MAX_TEXT) throw AppError(ErrorCode.DESCRIPTION_LENGTH)
        if (s.edge(edgeId) == null) throw AppError(ErrorCode.UNKNOWN_SEGMENT)
        if (s.reports.any { it.id == id }) s
        else s.copy(reports = s.reports + BarrierReport(id, edgeId, category, description.trim(), clock()))
    }

    suspend fun review(id: String, target: ReportStatus, reason: String, expected: Long) = mutate(expected) { s ->
        val old = s.reports.find { it.id == id } ?: throw AppError(ErrorCode.REPORT_NOT_FOUND)
        val next = ReportRules.review(old, target, reason, clock())
        s.copy(
            reports = s.reports.map { if (it.id == id) next else it },
            audit = s.audit + event("Review report", id, old.status.name, target.name, next.reason)
        )
    }

    // ---- Facilities and closures --------------------------------------------------------------

    suspend fun setFacility(id: String, status: Availability, reason: String, expected: Long) = mutate(expected) { s ->
        if (reason.trim().length !in ReportRules.MIN_TEXT..ReportRules.MAX_TEXT) throw AppError(ErrorCode.REASON_LENGTH)
        val old = s.map.facilities.find { it.id == id } ?: throw AppError(ErrorCode.FACILITY_NOT_FOUND)
        s.copy(
            map = s.map.copy(facilities = s.map.facilities.map {
                if (it.id == id) it.copy(availability = status, reason = reason.trim(), updatedAt = clock(), source = "Local reviewer") else it
            }),
            audit = s.audit + event("Update facility", id, old.availability.name, status.name, reason.trim())
        )
    }

    /** Temporarily closes or reopens both directions of a physical segment. */
    suspend fun setPathClosed(edgeId: String, closed: Boolean, reason: String) = mutate { s ->
        if (reason.trim().length !in ReportRules.MIN_TEXT..ReportRules.MAX_TEXT) throw AppError(ErrorCode.REASON_LENGTH)
        val e = s.edge(edgeId) ?: throw AppError(ErrorCode.UNKNOWN_SEGMENT)
        val ids = s.map.edges.filter { (it.from == e.from && it.to == e.to) || (it.from == e.to && it.to == e.from) }.map { it.id }.toSet()
        s.copy(
            blockedEdges = if (closed) s.blockedEdges + ids else s.blockedEdges - ids,
            audit = s.audit + event(if (closed) "Close path" else "Reopen path", edgeId,
                if (edgeId in s.blockedEdges) "Closed" else "Open", if (closed) "Closed" else "Open", reason.trim())
        )
    }

    /** Demo only: sets every lift facility at once to show the "no accessible route" case. */
    suspend fun simulateAllLifts(available: Boolean) = mutate { s ->
        if (!s.map.isDemo) throw AppError(ErrorCode.RESET_ONLY_DEMO)
        val liftIds = s.map.edges.filter { it.type == Segment.ELEVATOR }.mapNotNull { it.facilityId }.toSet()
        s.copy(
            map = s.map.copy(facilities = s.map.facilities.map {
                if (it.id in liftIds) it.copy(
                    availability = if (available) Availability.AVAILABLE else Availability.UNAVAILABLE,
                    reason = "Demo simulation", source = "Demo simulation", updatedAt = clock()
                ) else it
            }),
            audit = s.audit + event("Simulate all lifts", "all", "", if (available) "AVAILABLE" else "UNAVAILABLE", "Demo simulation")
        )
    }

    suspend fun resetDemo() = mutate { s ->
        if (!s.map.isDemo) throw AppError(ErrorCode.RESET_ONLY_DEMO)
        fresh().copy(profile = s.profile)
    }

    // ---- Map data -----------------------------------------------------------------------------

    /** Validates fully before saving; an invalid file never changes the stored map. Returns non-fatal warnings. */
    suspend fun importMap(json: String): List<String> = withContext(Dispatchers.Default) {
        if (json.toByteArray().size > MapValidator.MAX_BYTES) throw AppError(ErrorCode.MAP_TOO_LARGE)
        val map = try { MapJson.decodeFromString<CampusMap>(json) } catch (e: AppError) { throw e } catch (e: Exception) {
            throw AppError(ErrorCode.MAP_INVALID, e.message?.take(300))
        }
        val warnings = MapValidator.validate(map)
        mutate { s ->
            if (!s.reports.filter { it.isActive }.all { r -> map.edges.any { it.id == r.edgeId } }) throw AppError(ErrorCode.IMPORT_ORPHANS_REPORTS)
            val ids = map.nodes.map { it.id }.toSet()
            val first = map.nodes.first()
            val current = if (s.current in ids) s.current else first.id
            s.copy(
                map = map,
                reports = s.reports.filter { r -> map.edges.any { it.id == r.edgeId } },
                blockedEdges = s.blockedEdges.intersect(map.edges.map { it.id }.toSet()),
                start = current, current = current,
                destination = if (s.destination in ids) s.destination else first.id,
                selectedFloor = map.nodes.first { it.id == current }.floorId,
                audit = s.audit + event("Import map", map.version, s.map.version, map.version, map.provenance)
            )
        }
        warnings
    }

    suspend fun exportMapJson(): String = MapJson.encodeToString(snapshot().map)

    companion object {
        private const val TAG = "CampusRepository"
        const val MAX_AUDIT = 500
    }
}
