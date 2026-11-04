package com.accesspath.data

import android.content.Context
import com.accesspath.data.remote.SupabaseClient
import com.accesspath.data.remote.metresTo
import com.accesspath.data.remote.metresToLine
import com.accesspath.domain.AppError
import com.accesspath.domain.ErrorCode
import com.accesspath.domain.LatLng
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

/** What blocks the way outdoors. Reported by people on the spot, like a traffic report in a map app. */
enum class BarrierKind { CONSTRUCTION, ROAD_BLOCKED, FLOODING, NO_RAMP, BROKEN_FOOTPATH, OTHER }

/** A blockage reported at a real position outside a building. */
@Serializable
data class OutdoorBarrier(
    val id: String,
    val lat: Double,
    val lng: Double,
    val kind: BarrierKind,
    val note: String = "",
    @SerialName("created_at") val createdAt: String = Instant.now().toString(),
    /** True while only this phone knows about it (not signed in, or the upload has not gone through yet). */
    val localOnly: Boolean = false
) {
    val at get() = LatLng(lat, lng)
    val time: Long get() = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L)
}

/** Reported barriers within [withinM] metres of a route line. */
fun List<OutdoorBarrier>.onLine(line: List<LatLng>, withinM: Double = 25.0): List<OutdoorBarrier> =
    if (line.size < 2) emptyList() else filter { it.at.metresToLine(line) <= withinM }

/**
 * Outdoor barrier reports: kept on the phone, and shared through the server (table `outdoor_barriers`)
 * when the person is signed in. Reports older than [MAX_AGE_DAYS] days are hidden, because most blockages
 * outdoors (digging, water, a parked truck) are temporary.
 */
class OutdoorBarriers(context: Context, private val supabase: SupabaseClient, private val contributions: Contributions? = null) {
    private val prefs = context.getSharedPreferences("accesspath_barriers", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val list = ListSerializer(OutdoorBarrier.serializer())

    private fun fresh(b: OutdoorBarrier) = System.currentTimeMillis() - b.time < MAX_AGE_DAYS * 86_400_000L

    fun local(): List<OutdoorBarrier> = prefs.getString(KEY, null)
        ?.let { runCatching { json.decodeFromString(list, it) }.getOrNull() }.orEmpty().filter(::fresh)
    private fun saveLocal(v: List<OutdoorBarrier>) = prefs.edit().putString(KEY, json.encodeToString(list, v)).apply()

    /** Saves a report on the phone and shares it when possible. Returns true when it reached the server. */
    suspend fun report(at: LatLng, kind: BarrierKind, note: String): Boolean {
        val b = OutdoorBarrier(UUID.randomUUID().toString(), at.lat, at.lng, kind, note.trim().take(300), localOnly = true)
        saveLocal(listOf(b) + local())
        contributions?.add(Contributions.Kind.OUTDOOR_BARRIER)
        return upload(b)
    }

    private suspend fun upload(b: OutdoorBarrier): Boolean {
        if (!supabase.configured || supabase.session == null) return false
        return try {
            supabase.insertIgnoringDuplicates(TABLE,
                """{"id":"${b.id}","lat":${b.lat},"lng":${b.lng},"kind":"${b.kind.name}","note":${json.encodeToString(String.serializer(), b.note)}}""")
            saveLocal(local().map { if (it.id == b.id) it.copy(localOnly = false) else it })
            true
        } catch (e: AppError) { false }
    }

    /** Sends reports made while signed out or offline, once the person is signed in. */
    suspend fun uploadPending() { local().filter { it.localOnly }.forEach { upload(it) } }

    /** Removes a report this phone made (the blockage is gone). */
    suspend fun clear(b: OutdoorBarrier) {
        saveLocal(local().filterNot { it.id == b.id })
        if (supabase.session != null) runCatching { supabase.delete(TABLE, "id=eq.${b.id}") }
    }

    /** Barriers around [center]: shared ones from the server plus this phone's own. */
    suspend fun near(center: LatLng, radiusM: Int = 5_000): List<OutdoorBarrier> {
        val mine = local()
        val shared = if (!supabase.configured) emptyList() else try {
            val dLat = radiusM / 111_320.0
            val dLng = radiusM / (111_320.0 * Math.cos(Math.toRadians(center.lat)))
            val since = Instant.now().minusSeconds(MAX_AGE_DAYS * 86_400L).toString()
            val body = supabase.get("$TABLE?select=id,lat,lng,kind,note,created_at" +
                "&lat=gte.${center.lat - dLat}&lat=lte.${center.lat + dLat}&lng=gte.${center.lng - dLng}&lng=lte.${center.lng + dLng}" +
                "&created_at=gte.$since&order=created_at.desc&limit=200")
            json.decodeFromString(list, body)
        } catch (e: AppError) { if (mine.isEmpty() && e.code == ErrorCode.NETWORK) throw e else emptyList() }
        val ids = shared.map { it.id }.toSet()
        return (shared + mine.filter { it.id !in ids }).filter { it.at.metresTo(center) <= radiusM }
    }

    companion object {
        private const val KEY = "barriers"
        const val TABLE = "outdoor_barriers"
        const val MAX_AGE_DAYS = 14
    }
}
