package com.accesspath.data.remote

import com.accesspath.data.CampusRepository
import com.accesspath.domain.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder

/** Keeps this phone and the campus server in step. Shared state comes from the server; the phone keeps working offline. */
class RemoteSync(private val api: SupabaseClient, private val repo: CampusRepository) {
    private val json = Json { ignoreUnknownKeys = true }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    val configured get() = api.configured
    val signedInEmail get() = api.session?.user?.email
    val signedIn get() = api.session != null

    suspend fun profile(): RemoteProfile? {
        val uid = api.session?.user?.id ?: return null
        return json.decodeFromString(ListSerializer(RemoteProfile.serializer()), api.get("profiles?id=eq.$uid&select=*")).firstOrNull()
    }

    /** Sends unsent reports, then downloads reports, lift status, closures, the activity log and a newer map. */
    suspend fun syncAll(role: Role) {
        pushPendingReports()
        val s = repo.snapshot()
        val id = enc(s.map.id)
        pullNewerMap(s)
        // Surveys are optional extra data: a missing table or a bad row must not stop reports and lift status syncing.
        runCatching { syncSurveys() }.onFailure { android.util.Log.w("RemoteSync", "Survey sync failed", it) }
        val reports = json.decodeFromString(ListSerializer(RemoteReport.serializer()), api.get("reports?map_id=eq.$id&select=*&order=created_at.desc&limit=2000"))
        val facilities = json.decodeFromString(ListSerializer(RemoteFacility.serializer()), api.get("facility_status?map_id=eq.$id&select=*"))
        val closures = json.decodeFromString(ListSerializer(RemoteClosure.serializer()), api.get("closures?map_id=eq.$id&select=*"))
        val audit = if (role.canReview) json.decodeFromString(ListSerializer(RemoteAudit.serializer()),
            api.get("audit_events?map_id=eq.$id&select=*&order=created_at.desc&limit=200")) else null
        repo.applyRemote { RemoteMerge.apply(it, reports, facilities, closures, audit) }
    }

    private suspend fun pullNewerMap(s: AppSnapshot) {
        val head = json.decodeFromString(ListSerializer(RemoteMap.serializer()), api.get("campus_maps?id=eq.${enc(s.map.id)}&select=id,version")).firstOrNull()
            ?: return
        if (head.version == s.map.version) return
        val rows = json.parseToJsonElement(api.get("campus_maps?id=eq.${enc(s.map.id)}&select=payload")) as kotlinx.serialization.json.JsonArray
        val payload = (rows.firstOrNull() as? kotlinx.serialization.json.JsonObject)?.get("payload") ?: return
        runCatching { repo.importMap(payload.toString()) }
    }

    /**
     * Mapped areas. Only areas an admin APPROVED are downloaded and added to the map; pending and rejected
     * ones never reach other phones (the server's row level security also hides them).
     */
    suspend fun syncSurveys() {
        val s = repo.snapshot()
        val known = s.map.buildings.map { it.id }.toSet()
        val rows = json.parseToJsonElement(api.get("surveys?map_id=eq.${enc(s.map.id)}&status=eq.APPROVED&select=id,payload&order=created_at.asc&limit=500")) as kotlinx.serialization.json.JsonArray
        rows.forEach { row ->
            val o = row as? kotlinx.serialization.json.JsonObject ?: return@forEach
            val id = (o["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@forEach
            if (id in known) return@forEach
            val survey = runCatching { MapJson.decodeFromString(Survey.serializer(), o["payload"].toString()) }.getOrNull() ?: return@forEach
            runCatching { repo.addSurvey(survey.approved()) }
        }
    }

    /** Sends an area for review (safe to repeat). */
    suspend fun submitSurvey(survey: Survey) {
        val body = buildJsonObject {
            put("id", survey.building.id); put("map_id", repo.snapshot().map.id); put("name", survey.building.name)
            put("payload", json.parseToJsonElement(MapJson.encodeToString(Survey.serializer(), survey)))
        }
        api.insertIgnoringDuplicates("surveys", body.toString())
    }

    @kotlinx.serialization.Serializable
    data class SurveyStatus(val id: String, val status: String = "PENDING", val reason: String = "")

    /** Review status of areas this phone sent. */
    suspend fun surveyStatuses(ids: List<String>): List<SurveyStatus> {
        if (ids.isEmpty()) return emptyList()
        val list = ids.joinToString(",") { "\"" + it.replace("\"", "") + "\"" }
        return json.decodeFromString(ListSerializer(SurveyStatus.serializer()), api.get("surveys?id=in.(${enc(list)})&select=id,status,reason"))
    }

    /** Areas waiting for review (reviewers only; the server returns nothing to others). */
    suspend fun surveysToReview(): List<Pair<Survey, Long>> {
        val rows = json.parseToJsonElement(api.get("surveys?map_id=eq.${enc(repo.snapshot().map.id)}&status=eq.PENDING&select=id,payload,created_at&order=created_at.asc&limit=200")) as kotlinx.serialization.json.JsonArray
        return rows.mapNotNull { row ->
            val o = row as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val survey = runCatching { MapJson.decodeFromString(Survey.serializer(), o["payload"].toString()) }.getOrNull() ?: return@mapNotNull null
            val at = (o["created_at"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            survey to (at ?: 0L)
        }
    }

    /** An admin's decision; the server checks the role and records who decided and when. */
    suspend fun reviewSurvey(id: String, approve: Boolean, reason: String) {
        api.rpc("review_survey", buildJsonObject {
            put("survey_id", id); put("decision", if (approve) "APPROVED" else "REJECTED"); put("why", reason)
        }.toString())
    }

    /** Removes an area from the server too, so other phones drop it. Only reviewers may. */
    suspend fun deleteSurvey(id: String) {
        // An empty result is fine: the area may never have been uploaded. The caller has checked the role.
        api.delete("surveys", "id=eq.${enc(id)}")
    }

    suspend fun pushPendingReports() {
        if (!api.signedIn()) return
        val pending = repo.snapshot().let { s -> s.reports.filter { !it.synced }.map { s.map.id to it } }
        pending.forEach { (mapId, r) ->
            if (runCatching { java.util.UUID.fromString(r.id) }.isFailure) return@forEach
            val body = buildJsonObject {
                put("id", r.id); put("map_id", mapId); put("edge_id", r.edgeId)
                put("category", r.category.name); put("description", r.description)
            }
            api.insertIgnoringDuplicates("reports", body.toString())
            repo.markReportSynced(r.id)
        }
    }

    suspend fun review(reportId: String, status: ReportStatus, reason: String) {
        val body = buildJsonObject { put("report_id", reportId); put("new_status", status.name); put("decision_reason", reason) }
        val changed = api.rpc("review_report", body.toString()).trim().toIntOrNull() ?: 0
        if (changed != 1) throw AppError(ErrorCode.NEEDS_REVIEWER)
    }

    suspend fun setFacility(mapId: String, facilityId: String, status: Availability, reason: String) {
        val body = buildJsonObject {
            put("map_id", mapId); put("facility_id", facilityId); put("availability", status.name); put("reason", reason)
            put("updated_at", java.time.Instant.now().toString())
        }
        api.upsert("facility_status", body.toString(), "map_id,facility_id")
    }

    suspend fun setClosure(mapId: String, edgeId: String, closed: Boolean, reason: String) {
        val body = buildJsonObject {
            put("map_id", mapId); put("edge_id", edgeId); put("closed", closed); put("reason", reason)
            put("updated_at", java.time.Instant.now().toString())
        }
        api.upsert("closures", body.toString(), "map_id,edge_id")
    }

    suspend fun publishMap(map: CampusMap) {
        val body = buildJsonObject {
            put("id", map.id); put("version", map.version)
            put("payload", Json.parseToJsonElement(MapJson.encodeToString(CampusMap.serializer(), map)))
            put("updated_at", java.time.Instant.now().toString())
        }
        api.upsert("campus_maps", body.toString(), "id")
    }

    private fun SupabaseClient.signedIn() = session != null
}
