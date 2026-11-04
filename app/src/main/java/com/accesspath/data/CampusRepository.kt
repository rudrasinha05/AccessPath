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

    /** The map that ships with the app: an empty campus until surveys or a published map fill it. */
    private fun fresh(profile: Profile = Profile()): AppSnapshot {
        val map = MapJson.decodeFromString<CampusMap>(seed())
        MapValidator.validate(map)
        val first = map.nodes.firstOrNull()
        return AppSnapshot(
            map = map, profile = profile, start = first?.id.orEmpty(), current = first?.id.orEmpty(),
            destination = first?.id.orEmpty(), selectedFloor = first?.floorId ?: map.floors.firstOrNull()?.id.orEmpty()
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
                // Remove the illustrative sample campus that older versions shipped; keep the person's settings.
                readable.getOrNull()?.takeIf { it.map.isDemo }?.let { saved ->
                    // Compare against the stored row's revision: it is the one compare-and-set checks.
                    val clean = fresh(saved.profile).copy(revision = row.revision + 1)
                    db.stateDao().compareAndSet(MapJson.encodeToString(clean), clean.revision, row.revision)
                }
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
        else s.copy(reports = s.reports + BarrierReport(id, edgeId, category, description.trim(), clock(), synced = false))
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

    // ---- Campus server ---------------------------------------------------------------------------

    /** Replaces shared state with what the server has. Not audited locally: the server keeps the log. */
    suspend fun applyRemote(transform: (AppSnapshot) -> AppSnapshot) = mutate { s -> transform(s) }

    suspend fun markReportSynced(id: String) = mutate { s ->
        s.copy(reports = s.reports.map { if (it.id == id) it.copy(synced = true) else it })
    }

    // ---- Camera surveys --------------------------------------------------------------------------

    /** Adds a camera-recorded draft area. Its paths have unknown accessibility until reviewed. Adding the same survey twice changes nothing. */
    suspend fun addSurvey(survey: Survey) = mutate { s ->
        if (s.map.buildings.any { it.id == survey.building.id }) s
        else {
            val next = s.map.copy(
                buildings = s.map.buildings + survey.building, floors = s.map.floors + survey.floor,
                nodes = s.map.nodes + survey.nodes, edges = s.map.edges + survey.edges
            )
            MapValidator.validate(next)
            // On an empty campus the first survey also becomes where the person is.
            val ids = next.nodes.map { it.id }.toSet()
            val first = survey.nodes.firstOrNull()?.id.orEmpty()
            val current = if (s.current in ids) s.current else first
            s.copy(
                map = next, start = if (s.start in ids) s.start else current, current = current,
                destination = if (s.destination in ids) s.destination else current,
                selectedFloor = if (next.floors.any { it.id == s.selectedFloor }) s.selectedFloor else survey.floor.id,
                audit = s.audit + event("Add camera survey", survey.building.id, "", survey.building.name,
                    "${survey.nodes.size} places, ${survey.edges.size / 2} paths recorded with the camera")
            )
        }
    }

    /** Removes a draft area and everything on it. Refused while active reports point at its paths. */
    suspend fun deleteSurvey(buildingId: String, reason: String) = mutate { s ->
        if (reason.trim().length !in ReportRules.MIN_TEXT..ReportRules.MAX_TEXT) throw AppError(ErrorCode.REASON_LENGTH)
        val building = s.map.buildings.firstOrNull { it.id == buildingId && it.draft } ?: throw AppError(ErrorCode.UNKNOWN_LOCATION)
        val floorIds = s.map.floors.filter { it.buildingId == buildingId }.map { it.id }.toSet()
        val nodeIds = s.map.nodes.filter { it.floorId in floorIds }.map { it.id }.toSet()
        val edgeIds = s.map.edges.filter { it.from in nodeIds || it.to in nodeIds }.map { it.id }.toSet()
        if (s.reports.any { it.isActive && it.edgeId in edgeIds }) throw AppError(ErrorCode.IMPORT_ORPHANS_REPORTS)
        val next = s.map.copy(
            buildings = s.map.buildings.filter { it.id != buildingId }, floors = s.map.floors.filter { it.id !in floorIds },
            nodes = s.map.nodes.filter { it.id !in nodeIds }, edges = s.map.edges.filter { it.id !in edgeIds }
        )
        val fallback = next.nodes.firstOrNull()
        fun keep(id: String) = if (id in nodeIds) fallback?.id.orEmpty() else id
        val current = keep(s.current)
        s.copy(
            map = next, reports = s.reports.filter { it.edgeId !in edgeIds }, blockedEdges = s.blockedEdges - edgeIds,
            start = keep(s.start), current = current, destination = keep(s.destination),
            selectedFloor = if (s.selectedFloor in floorIds) next.nodes.firstOrNull { it.id == current }?.floorId ?: next.floors.firstOrNull()?.id.orEmpty() else s.selectedFloor,
            audit = s.audit + event("Delete camera survey", buildingId, building.name, "Deleted", reason.trim())
        )
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
            val first = map.nodes.firstOrNull()
            val current = if (s.current in ids) s.current else first?.id.orEmpty()
            s.copy(
                map = map,
                reports = s.reports.filter { r -> map.edges.any { it.id == r.edgeId } },
                blockedEdges = s.blockedEdges.intersect(map.edges.map { it.id }.toSet()),
                start = current, current = current,
                destination = if (s.destination in ids) s.destination else first?.id.orEmpty(),
                selectedFloor = map.nodes.firstOrNull { it.id == current }?.floorId ?: map.floors.firstOrNull()?.id.orEmpty(),
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
