package com.accesspath.data.remote

import com.accesspath.BuildConfig
import com.accesspath.domain.AppError
import com.accesspath.domain.ErrorCode
import com.accesspath.domain.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** How the person wants to travel outdoors, between places on the world map. */
enum class TravelMode { WALK, WHEELCHAIR, BIKE, CAR, BUS }

/** One instruction of an outdoor route. [instruction] is ready-made text when the service gives one. */
data class OutdoorStep(
    val type: String,
    val modifier: String?,
    val road: String,
    val distance: Double,
    val location: LatLng,
    val instruction: String? = null
)

data class OutdoorRoute(
    val mode: TravelMode,
    val distance: Double,
    val durationSec: Double,
    val line: List<LatLng>,
    val steps: List<OutdoorStep>
)

data class GeoResult(val name: String, val detail: String, val at: LatLng)

/**
 * Real-world search and outdoor directions.
 * - Search: MapTiler Geocoding (needs MAPTILER_KEY).
 * - Walk, bike, car: the public OSRM servers run by FOSSGIS on OpenStreetMap data (no key; fair use only).
 * - Wheelchair: OpenRouteService's wheelchair profile (needs ORS_KEY). Without a key there is no wheelchair
 *   route, because a walking route is not checked for steps or kerbs.
 * - Bus: no free route data is available; the app offers to open Google Maps transit directions instead.
 */
object Outdoor {
    private const val UA = "AccessPath/${BuildConfig.VERSION_NAME} (com.accesspath)"
    private val json = Json { ignoreUnknownKeys = true }

    val canSearch get() = BuildConfig.MAPTILER_KEY.isNotBlank()
    fun supports(mode: TravelMode) = when (mode) {
        TravelMode.BUS -> false
        TravelMode.WHEELCHAIR -> BuildConfig.ORS_KEY.isNotBlank()
        else -> true
    }

    /** Turns parse problems into [AppError] so callers handle one error type; cancellation passes through. */
    private suspend fun <T> guarded(block: suspend () -> T): T = try { block() }
        catch (e: AppError) { throw e }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { throw AppError(ErrorCode.SERVER, e.message ?: e.javaClass.simpleName) }

    suspend fun search(query: String, near: LatLng?): List<GeoResult> = guarded { searchRaw(query, near) }

    private suspend fun searchRaw(query: String, near: LatLng?): List<GeoResult> {
        if (!canSearch || query.isBlank()) return emptyList()
        val q = URLEncoder.encode(query.trim(), "UTF-8").replace("+", "%20")
        val proximity = near?.let { "&proximity=${it.lng},${it.lat}" }.orEmpty()
        val body = http("https://api.maptiler.com/geocoding/$q.json?key=${BuildConfig.MAPTILER_KEY}&limit=6$proximity")
        return json.parseToJsonElement(body).jsonObject["features"]?.jsonArray.orEmpty().mapNotNull { f ->
            val o = f.jsonObject
            val c = o["center"]?.jsonArray ?: return@mapNotNull null
            val name = o["text"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val full = o["place_name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            GeoResult(name, full.removePrefix(name).removePrefix(", "), LatLng(c[1].jsonPrimitive.double, c[0].jsonPrimitive.double))
        }
    }

    suspend fun route(mode: TravelMode, from: LatLng, to: LatLng): OutdoorRoute = routes(mode, from, to).first()

    /** The best route first, then up to two alternatives when the routing service has them. */
    suspend fun routes(mode: TravelMode, from: LatLng, to: LatLng): List<OutdoorRoute> = guarded {
        when (mode) {
            TravelMode.BUS -> throw AppError(ErrorCode.SERVER, "No public transport data")
            TravelMode.WHEELCHAIR -> listOf(ors(from, to))
            else -> osrm(mode, from, to)
        }
    }

    private suspend fun osrm(mode: TravelMode, from: LatLng, to: LatLng): List<OutdoorRoute> {
        val profile = when (mode) { TravelMode.CAR -> "routed-car"; TravelMode.BIKE -> "routed-bike"; else -> "routed-foot" }
        val body = http("https://routing.openstreetmap.de/$profile/route/v1/driving/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson&steps=true&alternatives=3")
        val root = json.parseToJsonElement(body).jsonObject
        val all = root["routes"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        if (all.isEmpty()) throw AppError(ErrorCode.SERVER, "No route")
        return all.take(3).map { r -> osrmRoute(mode, r) }
    }

    private fun osrmRoute(mode: TravelMode, r: JsonObject): OutdoorRoute {
        val line = r["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map { it.toLatLng() }
        val steps = r["legs"]!!.jsonArray.flatMap { leg ->
            leg.jsonObject["steps"]!!.jsonArray.map { s ->
                val o = s.jsonObject; val m = o["maneuver"]!!.jsonObject
                OutdoorStep(
                    type = m["type"]?.jsonPrimitive?.contentOrNull ?: "continue",
                    modifier = m["modifier"]?.jsonPrimitive?.contentOrNull,
                    road = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    distance = o["distance"]?.jsonPrimitive?.double ?: 0.0,
                    location = m["location"]!!.toLatLng()
                )
            }
        }
        return OutdoorRoute(mode, r["distance"]!!.jsonPrimitive.double, r["duration"]!!.jsonPrimitive.double, line, steps)
    }

    private suspend fun ors(from: LatLng, to: LatLng): OutdoorRoute {
        if (BuildConfig.ORS_KEY.isBlank()) throw AppError(ErrorCode.SERVER, "Wheelchair routing is not set up")
        val payload = """{"coordinates":[[${from.lng},${from.lat}],[${to.lng},${to.lat}]],"instructions":true}"""
        val body = http("https://api.openrouteservice.org/v2/directions/wheelchair/geojson", payload, mapOf("Authorization" to BuildConfig.ORS_KEY))
        val f = json.parseToJsonElement(body).jsonObject["features"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw AppError(ErrorCode.SERVER, "No route")
        val line = f["geometry"]!!.jsonObject["coordinates"]!!.jsonArray.map { it.toLatLng() }
        val props = f["properties"]!!.jsonObject
        val summary = props["summary"]?.jsonObject
        val steps = props["segments"]?.jsonArray.orEmpty().flatMap { seg ->
            seg.jsonObject["steps"]?.jsonArray.orEmpty().map { s ->
                val o = s.jsonObject
                val wp = o["way_points"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.int ?: 0
                OutdoorStep(
                    type = "ors", modifier = null,
                    road = o["name"]?.jsonPrimitive?.contentOrNull.orEmpty().takeIf { it != "-" }.orEmpty(),
                    distance = o["distance"]?.jsonPrimitive?.double ?: 0.0,
                    location = line.getOrElse(wp) { line.last() },
                    instruction = o["instruction"]?.jsonPrimitive?.contentOrNull
                )
            }
        }
        return OutdoorRoute(TravelMode.WHEELCHAIR, summary?.get("distance")?.jsonPrimitive?.double ?: 0.0,
            summary?.get("duration")?.jsonPrimitive?.double ?: 0.0, line, steps)
    }

    private fun JsonElement.toLatLng(): LatLng = jsonArray.let { LatLng(it[1].jsonPrimitive.double, it[0].jsonPrimitive.double) }

    private suspend fun http(url: String, post: String? = null, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val c = try { URL(url).openConnection() as HttpURLConnection } catch (e: IOException) { throw AppError(ErrorCode.NETWORK, e.message ?: "") }
        try {
            c.connectTimeout = 10_000; c.readTimeout = 20_000
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (post != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(post.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AppError(ErrorCode.SERVER, "HTTP $code")
            text
        } catch (e: IOException) {
            throw AppError(ErrorCode.NETWORK, e.message ?: "")
        } finally { c.disconnect() }
    }

    /** Opens Google Maps public-transport directions; used for bus because no free transit data exists. */
    fun transitIntent(to: LatLng) = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${to.lat},${to.lng}&travelmode=transit")
    )
}

/** Distance in metres between two points (haversine). */
fun LatLng.metresTo(o: LatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(o.lat - lat); val dLng = Math.toRadians(o.lng - lng)
    val a = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(o.lat)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(a))
}

/** Shortest distance in metres from this point to a polyline. Good enough for "off route" checks. */
fun LatLng.metresToLine(line: List<LatLng>): Double {
    if (line.size < 2) return line.firstOrNull()?.let(::metresTo) ?: Double.MAX_VALUE
    val kx = 111_320.0 * Math.cos(Math.toRadians(lat)); val ky = 111_320.0
    var best = Double.MAX_VALUE
    for (i in 0 until line.lastIndex) {
        val ax = (line[i].lng - lng) * kx; val ay = (line[i].lat - lat) * ky
        val bx = (line[i + 1].lng - lng) * kx; val by = (line[i + 1].lat - lat) * ky
        val dx = bx - ax; val dy = by - ay
        val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else (-(ax * dx + ay * dy) / len).coerceIn(0.0, 1.0)
        val px = ax + t * dx; val py = ay + t * dy
        best = minOf(best, Math.sqrt(px * px + py * py))
    }
    return best
}
