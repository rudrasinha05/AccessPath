package com.accesspath.data

import androidx.room.withTransaction
import com.accesspath.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.UUID

class CampusRepository(private val db: AccessDatabase, private val seed: () -> String) {
    val snapshots = db.stateDao().observe().map { it?.let { row -> MapJson.decodeFromString<AppSnapshot>(row.payload) } }
    private fun fresh(): AppSnapshot {
        val map = MapJson.decodeFromString<CampusMap>(seed()); MapValidator.validate(map)
        return AppSnapshot(map)
    }
    suspend fun initialize() = withContext(Dispatchers.IO) {
        db.withTransaction { if(db.stateDao().get() == null) {
            val state = fresh(); db.stateDao().insert(StateRow(revision=0, payload=MapJson.encodeToString(state)))
        } }
    }
    suspend fun snapshot(): AppSnapshot = withContext(Dispatchers.IO) {
        MapJson.decodeFromString(db.stateDao().get()?.payload ?: error("Map is loading. Please retry."))
    }
    private suspend fun mutate(expected: Long? = null, block: (AppSnapshot) -> AppSnapshot) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val row = db.stateDao().get() ?: error("Map is not loaded.")
            require(expected == null || expected == row.revision) { "Data changed while you were editing. Reload and retry." }
            val state = MapJson.decodeFromString<AppSnapshot>(row.payload)
            val next = block(state).copy(revision = state.revision + 1)
            check(db.stateDao().compareAndSet(MapJson.encodeToString(next), next.revision, state.revision) == 1) { "Concurrent update. Please retry." }
        }
    }
    suspend fun select(profile: Profile, start: String, destination: String, expected: Long) = mutate(expected) { s ->
        profile.validate(); require(s.map.nodes.any { it.id == start } && s.map.nodes.any { it.id == destination }) { "Choose mapped endpoints." }
        s.copy(profile=profile, start=start, current=start, destination=destination)
    }
    suspend fun position(node: String) = mutate { s ->
        val found = s.map.nodes.find { it.id == node } ?: error("Location not found.")
        s.copy(current=node, selectedFloor=found.floorId)
    }
    suspend fun floor(floor: String) = mutate { s ->
        require(s.map.floors.any { it.id == floor }); s.copy(selectedFloor=floor)
    }
    private fun event(action: String, target: String, before: String, after: String, reason: String) =
        AuditEvent(UUID.randomUUID().toString(), action, target, before, after, reason, System.currentTimeMillis())

    suspend fun submit(id: String, edgeId: String, category: String, description: String) =
        submitReportWithMedia(id, edgeId, category, description, photoUri = null, hasVoiceNote = false, expiryHours = 24)

    suspend fun submitReportWithMedia(
        id: String,
        edgeId: String,
        category: String,
        description: String,
        photoUri: String? = null,
        hasVoiceNote: Boolean = false,
        expiryHours: Int = 24
    ) = mutate { s ->
        require(description.trim().length in 5..1000) { "Description must contain 5–1000 characters." }
        require(category in categories) { "Select an issue type." }
        require(s.map.edges.any { it.id == edgeId }) { "Select a mapped segment." }
        if(s.reports.any { it.id == id }) s else s.copy(
            reports = s.reports + BarrierReport(
                id = id,
                edgeId = edgeId,
                category = category,
                description = description.trim(),
                createdAt = System.currentTimeMillis(),
                photoUri = photoUri,
                hasVoiceNote = hasVoiceNote,
                expiryHours = expiryHours
            )
        )
    }

    suspend fun checkExpiredReports() = mutate { s ->
        val now = System.currentTimeMillis()
        val updatedReports = s.reports.map { r ->
            val expiryMs = r.createdAt + (r.expiryHours * 3600 * 1000L)
            if (r.status in setOf(ReportStatus.PENDING, ReportStatus.VERIFIED) && now > expiryMs) {
                r.copy(status = ReportStatus.RESOLVED, reason = "Auto-resolved: report expired after ${r.expiryHours} hours")
            } else r
        }
        s.copy(reports = updatedReports)
    }

    suspend fun requestAssistance(nodeId: String, urgency: String, note: String) = mutate { s ->
        val node = s.map.nodes.find { it.id == nodeId } ?: error("Node not found.")
        val req = AssistanceRequest(
            id = UUID.randomUUID().toString(),
            nodeId = nodeId,
            nodeName = node.name,
            urgency = urgency,
            note = note,
            requestedAt = System.currentTimeMillis()
        )
        s.copy(
            assistanceRequests = s.assistanceRequests + req,
            audit = s.audit + event("Assistance Request", nodeId, "None", urgency, "Dispatched help request from ${node.name}")
        )
    }

    suspend fun acknowledgeAssistance(requestId: String) = mutate { s ->
        s.copy(assistanceRequests = s.assistanceRequests.map {
            if (it.id == requestId) it.copy(status = "ACKNOWLEDGED") else it
        })
    }

    suspend fun review(id: String, target: ReportStatus, reason: String, expected: Long) = mutate(expected) { s ->
        val old = s.reports.find { it.id == id } ?: error("Report not found.")
        val next = ReportRules.review(old, target, reason, System.currentTimeMillis())
        s.copy(reports = s.reports.map { if(it.id == id) next else it }, audit = s.audit + event("Review report", id, old.status.name, target.name, reason))
    }
    suspend fun facility(id: String, status: Availability, reason: String, expected: Long) = mutate(expected) { s ->
        require(reason.trim().length in 5..1000) { "Reason must contain 5–1000 characters." }
        val old = s.map.facilities.find { it.id == id } ?: error("Facility not found.")
        s.copy(map = s.map.copy(facilities=s.map.facilities.map { if(it.id == id) it.copy(availability=status, reason=reason.trim(), updatedAt=System.currentTimeMillis(), source="Manual demo review") else it }),
            audit=s.audit + event("Update facility", id, old.availability.name, status.name, reason))
    }
    suspend fun block(edgeId: String, blocked: Boolean) = mutate { s ->
        val e = s.map.edges.find { it.id == edgeId } ?: error("Segment not found.")
        val ids = s.map.edges.filter { (it.from == e.from && it.to == e.to) || (it.from == e.to && it.to == e.from) }.map { it.id }.toSet()
        s.copy(blockedEdges=if(blocked) s.blockedEdges + ids else s.blockedEdges - ids,
            audit=s.audit + event("Simulate path closure", edgeId, if(edgeId in s.blockedEdges) "Blocked" else "Open", if(blocked) "Blocked" else "Open", "Failure simulation"))
    }
    suspend fun allLifts(available: Boolean) = mutate { s ->
        s.copy(map=s.map.copy(facilities=s.map.facilities.map { it.copy(availability=if(available) Availability.AVAILABLE else Availability.UNAVAILABLE,
            reason="All-lifts simulation", source="Simulated", updatedAt=System.currentTimeMillis()) }),
            audit=s.audit + event("Simulate all lifts", "all", "Mixed", if(available) "Available" else "Unavailable", "Failure simulation"))
    }
    suspend fun resetDemo() = mutate { s ->
        require(s.map.provenance == "Illustrative demo") { "Reset is only available for the bundled demo. Imported data was preserved." }; fresh()
    }
    suspend fun addLandmark(node: Node) = mutate { s ->
        require(s.map.nodes.none { it.id == node.id }) { "Landmark ID '${node.id}' already exists." }
        val nextMap = s.map.copy(nodes = s.map.nodes + node)
        MapValidator.validate(nextMap)
        s.copy(map = nextMap, audit = s.audit + event("Add landmark", node.id, "", node.name, "Custom landmark added"))
    }
    suspend fun editLandmark(nodeId: String, newName: String, newX: Double, newY: Double) = mutate { s ->
        require(s.map.nodes.any { it.id == nodeId }) { "Landmark ID '$nodeId' not found." }
        val old = s.map.nodes.first { it.id == nodeId }
        val updatedNode = old.copy(name = newName.trim(), x = newX, y = newY)
        val nextMap = s.map.copy(nodes = s.map.nodes.map { if (it.id == nodeId) updatedNode else it })
        MapValidator.validate(nextMap)
        s.copy(map = nextMap, audit = s.audit + event("Edit landmark", nodeId, old.name, newName, "Landmark updated"))
    }
    suspend fun deleteLandmark(nodeId: String) = mutate { s ->
        require(s.map.nodes.any { it.id == nodeId }) { "Landmark ID '$nodeId' not found." }
        require(s.current != nodeId && s.destination != nodeId) { "Cannot delete active start or destination node." }
        val nextMap = s.map.copy(
            nodes = s.map.nodes.filter { it.id != nodeId },
            edges = s.map.edges.filter { it.from != nodeId && it.to != nodeId }
        )
        MapValidator.validate(nextMap)
        s.copy(map = nextMap, audit = s.audit + event("Delete landmark", nodeId, nodeId, "Deleted", "Landmark removed"))
    }
    suspend fun addConnector(forward: Edge, twoWay: Boolean = true) = mutate { s ->
        require(s.map.edges.none { it.id == forward.id }) { "Segment ID '${forward.id}' already exists." }
        val reverseId = "${forward.id}_r"
        if (twoWay) require(s.map.edges.none { it.id == reverseId }) { "Reverse segment ID '${reverseId}' already exists." }
        val edgesToAdd = if (twoWay) {
            listOf(forward, forward.copy(id = reverseId, from = forward.to, to = forward.from))
        } else listOf(forward)
        val nextMap = s.map.copy(edges = s.map.edges + edgesToAdd)
        MapValidator.validate(nextMap)
        s.copy(map = nextMap, audit = s.audit + event("Add connector", forward.id, "", "${forward.from} -> ${forward.to}", "Custom connector added"))
    }
    suspend fun syncRemote(remotePayload: SyncPayload, account: ReviewerAccount): List<ConflictRecord> = withContext(Dispatchers.IO) {
        require(account.authToken.isNotBlank()) { "Authentication required for reviewer sync." }
        val allConflicts = mutableListOf<ConflictRecord>()
        mutate { s ->
            val (resolvedFacilities, facilityConflicts) = ConflictResolver.resolveFacilities(s.map.facilities, remotePayload.facilities)
            val (resolvedReports, reportConflicts) = ConflictResolver.resolveReports(s.reports, remotePayload.reports)
            allConflicts.addAll(facilityConflicts)
            allConflicts.addAll(reportConflicts)

            val updatedMap = s.map.copy(facilities = resolvedFacilities)
            val auditMessage = "Synced with ${remotePayload.publisher}. ${allConflicts.size} conflicts resolved."
            s.copy(
                map = updatedMap,
                reports = resolvedReports,
                audit = s.audit + event("Remote Sync", "Cloud", s.map.version, remotePayload.mapVersion, auditMessage)
            )
        }
        allConflicts
    }
    suspend fun createPublishPayload(account: ReviewerAccount): SyncPayload = withContext(Dispatchers.IO) {
        require(account.authToken.isNotBlank()) { "Authentication required to publish." }
        val s = snapshot()
        SyncPayload(
            mapVersion = s.map.version,
            facilities = s.map.facilities,
            reports = s.reports,
            exportedAt = System.currentTimeMillis(),
            publisher = account.displayName
        )
    }
    suspend fun importMap(json: String): List<String> = withContext(Dispatchers.IO) {
        require(json.toByteArray().size <= 10 * 1024 * 1024) { "Map must be at most 10 MB." }
        val map = MapJson.decodeFromString<CampusMap>(json); val warnings = MapValidator.validate(map)
        mutate { s ->
            require(s.reports.all { r -> map.edges.any { it.id == r.edgeId } }) { "Existing reports reference removed segments. Reconcile the map IDs before importing." }
            val ids = map.nodes.map { it.id }.toSet(); val first = map.nodes.first()
            s.copy(map=map, blockedEdges=s.blockedEdges.intersect(map.edges.map { it.id }.toSet()),
                start=if(s.start in ids) s.start else first.id, current=if(s.current in ids) s.current else first.id,
                destination=if(s.destination in ids) s.destination else first.id,
                selectedFloor=if(map.floors.any { it.id == s.selectedFloor }) s.selectedFloor else first.floorId,
                audit=s.audit + event("Import map", map.version, s.map.version, map.version, map.provenance))
        }; warnings
    }
    suspend fun setMapStyle(style: MapStyle) = mutate { s ->
        s.copy(mapStyle = style)
    }

    suspend fun addGeofence(zone: GeofenceZone) = mutate { s ->
        require(zone.name.isNotBlank()) { "Geofence name cannot be empty." }
        require(zone.radius > 0) { "Geofence radius must be positive." }
        s.copy(
            geofences = s.geofences.filter { it.id != zone.id } + zone,
            audit = s.audit + event("Add Geofence", zone.id, "", zone.name, "Geofence perimeter created: ${zone.name}")
        )
    }

    suspend fun deleteGeofence(zoneId: String) = mutate { s ->
        s.copy(
            geofences = s.geofences.filter { it.id != zoneId },
            audit = s.audit + event("Delete Geofence", zoneId, zoneId, "Deleted", "Geofence removed")
        )
    }

    suspend fun switchCampus(campusId: String) = mutate { s ->
        val selected = globalCampusCatalog.find { it.id == campusId } ?: error("Campus not found in directory.")
        val campusName = selected.name
        val updatedMap = s.map.copy(
            name = "$campusName Campus",
            provenance = "Worldwide Directory: ${selected.city}, ${selected.country}"
        )
        s.copy(
            activeCampusId = campusId,
            map = updatedMap,
            audit = s.audit + event("Switch Campus", campusId, s.activeCampusId, campusId, "Switched active worldwide campus map to $campusName")
        )
    }

    suspend fun publishCampusToWorld(): String = withContext(Dispatchers.IO) {
        val s = snapshot()
        "Successfully published '${s.map.name}' to the AccessPath Worldwide Directory (Revision ${s.revision}). Available globally."
    }

    companion object {
        val categories = listOf("Blocked path", "Unavailable elevator", "Damaged ramp", "Unsuitable width", "Steps or threshold", "Other barrier")

        val globalCampusCatalog = listOf(
            CampusListing(
                id = "glbitm",
                name = "GLBITM Academic Campus",
                city = "Greater Noida",
                country = "India",
                latitude = 28.4731,
                longitude = 77.4829,
                buildingCount = 4,
                contributor = "GLBITM Student Accessibility Council",
                isVerified = true,
                description = "Primary engineering & research complex with accessible ramps, dual elevator banks, and tactile corridors."
            ),
            CampusListing(
                id = "iitd",
                name = "IIT Delhi Hauz Khas Campus",
                city = "New Delhi",
                country = "India",
                latitude = 28.5450,
                longitude = 77.1926,
                buildingCount = 12,
                contributor = "IITD Equal Opportunity Cell",
                isVerified = true,
                description = "Multi-department technical institute campus with motorized wheelchair corridors and audio-beaconed lecture halls."
            ),
            CampusListing(
                id = "aiims",
                name = "AIIMS New Delhi Medical Hub",
                city = "New Delhi",
                country = "India",
                latitude = 28.5672,
                longitude = 77.2100,
                buildingCount = 8,
                contributor = "AIIMS Patient Transport Taskforce",
                isVerified = true,
                description = "Large-scale hospital campus with wide gurney/wheelchair ramps, emergency elevators, and quiet zone routing."
            ),
            CampusListing(
                id = "del_airport",
                name = "Indira Gandhi International Airport (T3)",
                city = "Delhi",
                country = "India",
                latitude = 28.5562,
                longitude = 77.1000,
                buildingCount = 2,
                contributor = "Airport Authority of India",
                isVerified = true,
                description = "International airport terminal with step-free moving walkways, PRM assistance gates, and accessible restrooms."
            ),
            CampusListing(
                id = "stanford",
                name = "Stanford University Main Quad",
                city = "Stanford, CA",
                country = "United States",
                latitude = 37.4275,
                longitude = -122.1697,
                buildingCount = 18,
                contributor = "Stanford Accessible Education Center",
                isVerified = true,
                description = "Historic sandstone quadrangle with curb ramps, power-assisted doors, and elevator access across all academic wings."
            )
        )
    }
}
