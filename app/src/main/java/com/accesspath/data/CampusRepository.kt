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
                // Safety-First: Do NOT automatically assume expired barrier is safe!
                // Path remains closed for safety until verified by authorized staff.
                r.copy(reason = "Expired after ${r.expiryHours}h - awaiting physical re-verification. Path remains restricted.")
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

        // Load actual distinct campus map topologies for each selected location
        val distinctMap: CampusMap = when (campusId) {
            "iitd" -> createIITDelhiMap()
            "aiims" -> createAIIMSMap()
            "del_airport" -> createDelhiAirportMap()
            "stanford" -> createStanfordMap()
            else -> fresh().map
        }

        s.copy(
            activeCampusId = campusId,
            map = distinctMap,
            start = distinctMap.nodes.first().id,
            current = distinctMap.nodes.first().id,
            destination = distinctMap.nodes.last().id,
            selectedFloor = distinctMap.floors.first().id,
            blockedEdges = emptySet(),
            reports = emptyList(),
            audit = s.audit + event("Switch Campus", campusId, s.activeCampusId, campusId, "Switched active campus to $campusName")
        )
    }

    suspend fun publishCampusToWorld(): String = withContext(Dispatchers.IO) {
        val s = snapshot()
        "[Demo Sandbox Mode] Successfully serialized and staged '${s.map.name}' (Revision ${s.revision}) to the AccessPath local cloud registry."
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
                isVerified = false, // Illustrative demo dataset pending field verification
                description = "Primary engineering & research complex (Illustrative demo layout with accessible ramps and elevators)."
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
                isVerified = false, // Demo crowdsourced layout
                description = "Bharti School & Lecture Hall Complex (Community surveyed layout with motorized wheelchair corridors)."
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
                isVerified = false, // Simulated hospital layout
                description = "Outpatient & Trauma Centre (Hospital demo layout with wide gurney ramps and medical lifts)."
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
                isVerified = false, // Simulated airport layout
                description = "Terminal 3 PRM Zone (International terminal demo layout with step-free walkways and assistance gates)."
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
                isVerified = false, // Simulated campus layout
                description = "Science & Engineering Quad (Demo layout with curb ramps and power-assisted doors)."
            )
        )

        private fun createIITDelhiMap(): CampusMap {
            val b = listOf(Building("iitd_b1", "Bharti School"), Building("iitd_b2", "Lecture Hall Complex"))
            val f = listOf(Floor("iitd_f0", "iitd_b1", "Ground Floor", 0), Floor("iitd_f1", "iitd_b1", "First Floor", 1))
            val n = listOf(
                Node("iitd_gate", "iitd_f0", "IITD Main Entry", 10.0, 80.0, "ENTRANCE"),
                Node("iitd_ramp", "iitd_f0", "North Accessible Ramp", 10.0, 50.0, "RAMP"),
                Node("iitd_foyer", "iitd_f0", "Central Foyer", 35.0, 50.0, "LANDMARK"),
                Node("iitd_lift0", "iitd_f0", "Central Lift 0", 35.0, 20.0, "ELEVATOR"),
                Node("iitd_lift1", "iitd_f1", "Central Lift 1", 35.0, 20.0, "ELEVATOR"),
                Node("iitd_lhc", "iitd_f1", "LHC Auditorium 1", 70.0, 20.0, "ROOM"),
                Node("iitd_lab", "iitd_f1", "Telecom Research Lab", 70.0, 50.0, "ROOM")
            )
            val fac = listOf(Facility("iitd_lift_fac", "Central Lift", Availability.AVAILABLE, "Operational", System.currentTimeMillis(), "Seeded"))
            val e = listOf(
                Edge("e1", "iitd_gate", "iitd_ramp", Segment.OUTDOOR, 30.0, 2.0, 0.0, true, true),
                Edge("e1_r", "iitd_ramp", "iitd_gate", Segment.OUTDOOR, 30.0, 2.0, 0.0, true, true),
                Edge("e2", "iitd_ramp", "iitd_foyer", Segment.RAMP, 25.0, 1.8, 3.5, true, true),
                Edge("e2_r", "iitd_foyer", "iitd_ramp", Segment.RAMP, 25.0, 1.8, 3.5, true, true),
                Edge("e3", "iitd_foyer", "iitd_lift0", Segment.CORRIDOR, 30.0, 2.2, 0.0, true, true),
                Edge("e3_r", "iitd_lift0", "iitd_foyer", Segment.CORRIDOR, 30.0, 2.2, 0.0, true, true),
                Edge("e4", "iitd_lift0", "iitd_lift1", Segment.ELEVATOR, 5.0, 2.0, 0.0, true, true, facilityId = "iitd_lift_fac"),
                Edge("e4_r", "iitd_lift1", "iitd_lift0", Segment.ELEVATOR, 5.0, 2.0, 0.0, true, true, facilityId = "iitd_lift_fac"),
                Edge("e5", "iitd_lift1", "iitd_lhc", Segment.CORRIDOR, 35.0, 2.0, 0.0, true, true),
                Edge("e5_r", "iitd_lhc", "iitd_lift1", Segment.CORRIDOR, 35.0, 2.0, 0.0, true, true),
                Edge("e6", "iitd_lhc", "iitd_lab", Segment.CORRIDOR, 30.0, 2.0, 0.0, true, true),
                Edge("e6_r", "iitd_lab", "iitd_lhc", Segment.CORRIDOR, 30.0, 2.0, 0.0, true, true)
            )
            return CampusMap(schemaVersion = 1, version = "1.0", name = "IIT Delhi Hauz Khas Campus", provenance = "Community Crowdsourced (Demo Layout)", verifiedAt = null, buildings = b, floors = f, nodes = n, edges = e, facilities = fac)
        }

        private fun createAIIMSMap(): CampusMap {
            val b = listOf(Building("aiims_b1", "Trauma Center"), Building("aiims_b2", "OPD Block"))
            val f = listOf(Floor("aiims_f0", "aiims_b1", "Emergency Floor", 0), Floor("aiims_f1", "aiims_b1", "Specialty Clinics", 1))
            val n = listOf(
                Node("aiims_entry", "aiims_f0", "Ambulance / Patient Bay", 15.0, 85.0, "ENTRANCE"),
                Node("aiims_triage", "aiims_f0", "Emergency Triage", 15.0, 50.0, "LANDMARK"),
                Node("aiims_lift0", "aiims_f0", "Medical Lift G", 40.0, 50.0, "ELEVATOR"),
                Node("aiims_lift1", "aiims_f1", "Medical Lift 1", 40.0, 50.0, "ELEVATOR"),
                Node("aiims_opd", "aiims_f1", "Outpatient Consultation", 80.0, 50.0, "ROOM"),
                Node("aiims_pharmacy", "aiims_f1", "24x7 Accessible Pharmacy", 80.0, 20.0, "ROOM")
            )
            val fac = listOf(Facility("aiims_lift_fac", "Medical Gurney Lift", Availability.AVAILABLE, "Operational", System.currentTimeMillis(), "Seeded"))
            val e = listOf(
                Edge("ae1", "aiims_entry", "aiims_triage", Segment.RAMP, 35.0, 2.5, 2.8, true, true),
                Edge("ae1_r", "aiims_triage", "aiims_entry", Segment.RAMP, 35.0, 2.5, 2.8, true, true),
                Edge("ae2", "aiims_triage", "aiims_lift0", Segment.CORRIDOR, 25.0, 2.4, 0.0, true, true),
                Edge("ae2_r", "aiims_lift0", "aiims_triage", Segment.CORRIDOR, 25.0, 2.4, 0.0, true, true),
                Edge("ae3", "aiims_lift0", "aiims_lift1", Segment.ELEVATOR, 6.0, 2.2, 0.0, true, true, facilityId = "aiims_lift_fac"),
                Edge("ae3_r", "aiims_lift1", "aiims_lift0", Segment.ELEVATOR, 6.0, 2.2, 0.0, true, true, facilityId = "aiims_lift_fac"),
                Edge("ae4", "aiims_lift1", "aiims_opd", Segment.CORRIDOR, 40.0, 2.2, 0.0, true, true),
                Edge("ae4_r", "aiims_opd", "aiims_lift1", Segment.CORRIDOR, 40.0, 2.2, 0.0, true, true),
                Edge("ae5", "aiims_opd", "aiims_pharmacy", Segment.CORRIDOR, 30.0, 2.0, 0.0, true, true),
                Edge("ae5_r", "aiims_pharmacy", "aiims_opd", Segment.CORRIDOR, 30.0, 2.0, 0.0, true, true)
            )
            return CampusMap(schemaVersion = 1, version = "1.0", name = "AIIMS New Delhi Medical Hub", provenance = "Hospital Transport Layout (Demo Sandbox)", verifiedAt = null, buildings = b, floors = f, nodes = n, edges = e, facilities = fac)
        }

        private fun createDelhiAirportMap(): CampusMap {
            val b = listOf(Building("igi_b1", "Terminal 3 Main Pier"))
            val f = listOf(Floor("igi_f0", "igi_b1", "Arrivals & Baggage", 0), Floor("igi_f1", "igi_b1", "Departures Pier", 1))
            val n = listOf(
                Node("igi_curb", "igi_f1", "Departures Drop-Off", 10.0, 90.0, "ENTRANCE"),
                Node("igi_sec", "igi_f1", "PRM Security Lane", 35.0, 90.0, "LANDMARK"),
                Node("igi_lift1", "igi_f1", "PRM Lift Departures", 35.0, 45.0, "ELEVATOR"),
                Node("igi_lift0", "igi_f0", "PRM Lift Arrivals", 35.0, 45.0, "ELEVATOR"),
                Node("igi_lounge", "igi_f0", "Special Assistance Lounge", 70.0, 45.0, "ROOM"),
                Node("igi_gate", "igi_f0", "Boarding Gate 24", 70.0, 85.0, "ROOM")
            )
            val fac = listOf(Facility("igi_lift_fac", "Terminal PRM Elevator", Availability.AVAILABLE, "Operational", System.currentTimeMillis(), "Seeded"))
            val e = listOf(
                Edge("ie1", "igi_curb", "igi_sec", Segment.OUTDOOR, 25.0, 3.0, 0.0, true, true),
                Edge("ie1_r", "igi_sec", "igi_curb", Segment.OUTDOOR, 25.0, 3.0, 0.0, true, true),
                Edge("ie2", "igi_sec", "igi_lift1", Segment.CORRIDOR, 45.0, 2.8, 0.0, true, true),
                Edge("ie2_r", "igi_lift1", "igi_sec", Segment.CORRIDOR, 45.0, 2.8, 0.0, true, true),
                Edge("ie3", "igi_lift1", "igi_lift0", Segment.ELEVATOR, 6.0, 2.5, 0.0, true, true, facilityId = "igi_lift_fac"),
                Edge("ie3_r", "igi_lift0", "igi_lift1", Segment.ELEVATOR, 6.0, 2.5, 0.0, true, true, facilityId = "igi_lift_fac"),
                Edge("ie4", "igi_lift0", "igi_lounge", Segment.CORRIDOR, 35.0, 2.5, 0.0, true, true),
                Edge("ie4_r", "igi_lounge", "igi_lift0", Segment.CORRIDOR, 35.0, 2.5, 0.0, true, true),
                Edge("ie5", "igi_lounge", "igi_gate", Segment.CORRIDOR, 40.0, 2.5, 0.0, true, true),
                Edge("ie5_r", "igi_gate", "igi_lounge", Segment.CORRIDOR, 40.0, 2.5, 0.0, true, true)
            )
            return CampusMap(schemaVersion = 1, version = "1.0", name = "Indira Gandhi International Airport (T3)", provenance = "Terminal 3 PRM Zone (Demo Sandbox)", verifiedAt = null, buildings = b, floors = f, nodes = n, edges = e, facilities = fac)
        }

        private fun createStanfordMap(): CampusMap {
            val b = listOf(Building("su_b1", "Packard Electrical Eng"), Building("su_b2", "Huang Engineering"))
            val f = listOf(Floor("su_f0", "su_b1", "Arcade Ground", 0), Floor("su_f1", "su_b1", "Mezzanine Level", 1))
            val n = listOf(
                Node("su_oval", "su_f0", "Stanford Oval Entry", 10.0, 80.0, "ENTRANCE"),
                Node("su_arcade", "su_f0", "Sandstone Arcade", 30.0, 80.0, "RAMP"),
                Node("su_lift0", "su_f0", "Packard Ada Lift 0", 30.0, 40.0, "ELEVATOR"),
                Node("su_lift1", "su_f1", "Packard Ada Lift 1", 30.0, 40.0, "ELEVATOR"),
                Node("su_aud", "su_f1", "Huang Center Auditorium", 65.0, 40.0, "ROOM"),
                Node("su_lab", "su_f1", "Robotics & AI Center", 65.0, 80.0, "ROOM")
            )
            val fac = listOf(Facility("su_lift_fac", "Packard ADA Elevator", Availability.AVAILABLE, "Operational", System.currentTimeMillis(), "Seeded"))
            val e = listOf(
                Edge("se1", "su_oval", "su_arcade", Segment.RAMP, 20.0, 2.2, 3.2, true, true),
                Edge("se1_r", "su_arcade", "su_oval", Segment.RAMP, 20.0, 2.2, 3.2, true, true),
                Edge("se2", "su_arcade", "su_lift0", Segment.CORRIDOR, 40.0, 2.0, 0.0, true, true),
                Edge("se2_r", "su_lift0", "su_arcade", Segment.CORRIDOR, 40.0, 2.0, 0.0, true, true),
                Edge("se3", "su_lift0", "su_lift1", Segment.ELEVATOR, 5.0, 2.0, 0.0, true, true, facilityId = "su_lift_fac"),
                Edge("se3_r", "su_lift1", "su_lift0", Segment.ELEVATOR, 5.0, 2.0, 0.0, true, true, facilityId = "su_lift_fac"),
                Edge("se4", "su_lift1", "su_aud", Segment.CORRIDOR, 35.0, 2.0, 0.0, true, true),
                Edge("se4_r", "su_aud", "su_lift1", Segment.CORRIDOR, 35.0, 2.0, 0.0, true, true),
                Edge("se5", "su_aud", "su_lab", Segment.CORRIDOR, 40.0, 2.0, 0.0, true, true),
                Edge("se5_r", "su_lab", "su_aud", Segment.CORRIDOR, 40.0, 2.0, 0.0, true, true)
            )
            return CampusMap(schemaVersion = 1, version = "1.0", name = "Stanford University Main Quad", provenance = "Engineering Quad Layout (Demo Sandbox)", verifiedAt = null, buildings = b, floors = f, nodes = n, edges = e, facilities = fac)
        }
    }
}
