package com.accesspath.data.remote

import com.accesspath.BuildConfig
import com.accesspath.domain.AppError
import com.accesspath.domain.ErrorCode
import com.accesspath.domain.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Place categories shown as chips on the map. Each maps to OpenStreetMap tags (real places, no sample data). */
enum class PoiCategory(val filter: String) {
    RESTAURANTS("""["amenity"~"^(restaurant|fast_food|food_court)$"]"""),
    COFFEE("""["amenity"="cafe"]"""),
    PETROL("""["amenity"="fuel"]"""),
    HOTELS("""["tourism"~"^(hotel|guest_house|hostel|motel)$"]"""),
    ATMS("""["amenity"~"^(atm|bank)$"]"""),
    HOSPITALS("""["amenity"~"^(hospital|clinic|doctors|pharmacy)$"]"""),
    SHOPPING("""["shop"~"^(supermarket|mall|department_store|convenience|clothes|general)$"]"""),
    TOILETS("""["amenity"="toilets"]"""),
    ACCESSIBLE("""["wheelchair"~"^(yes|designated)$"]["name"]""")
}

/** A real place from OpenStreetMap. [tags] keeps the raw OSM tags for details (hours, phone, wheelchair access). */
@Serializable
data class Poi(val id: String, val name: String, val lat: Double, val lng: Double, val kind: String, val tags: Map<String, String> = emptyMap()) {
    val at get() = LatLng(lat, lng)
    val address: String get() = listOfNotNull(
        listOfNotNull(tags["addr:housenumber"], tags["addr:street"]).joinToString(" ").takeIf { it.isNotBlank() },
        tags["addr:suburb"] ?: tags["addr:neighbourhood"], tags["addr:city"]
    ).joinToString(", ")
    val hours: String? get() = tags["opening_hours"]
    val phone: String? get() = tags["phone"] ?: tags["contact:phone"]
    val website: String? get() = tags["website"] ?: tags["contact:website"]
    /** yes / limited / no / designated, or null when nobody has recorded it. */
    val wheelchair: String? get() = tags["wheelchair"]
    val hasAccessibleToilet: Boolean get() = tags["toilets:wheelchair"] == "yes"
}

data class Weather(val temperatureC: Double, val code: Int, val isDay: Boolean, val usAqi: Int?)

/**
 * Real-world data for the home screen:
 * - nearby places by category: OpenStreetMap through the Overpass API (no key, fair use),
 * - weather and air quality: Open-Meteo (no key),
 * - the area's name: MapTiler reverse geocoding.
 */
object Places {
    private const val UA = "AccessPath/${BuildConfig.VERSION_NAME} (com.accesspath)"
    private val json = Json { ignoreUnknownKeys = true }
    private val overpass = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter")

    suspend fun nearby(category: PoiCategory, center: LatLng, radiusM: Int = 1500, limit: Int = 60): List<Poi> {
        val q = "[out:json][timeout:25];nwr(around:$radiusM,${center.lat},${center.lng})${category.filter};out center tags $limit;"
        var last: Exception? = null
        for (endpoint in overpass) {
            try {
                val body = http(endpoint, "data=" + URLEncoder.encode(q, "UTF-8"), "application/x-www-form-urlencoded")
                return parse(body, category).sortedBy { distance(center, it.at) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { last = e }
        }
        throw (last as? AppError) ?: AppError(ErrorCode.NETWORK, last?.message ?: "")
    }

    private fun parse(body: String, category: PoiCategory): List<Poi> =
        json.parseToJsonElement(body).jsonObject["elements"]?.jsonArray.orEmpty().mapNotNull { el ->
            val o = el.jsonObject
            val tags = o["tags"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val name = tags["name"] ?: tags["name:en"] ?: tags["brand"] ?: return@mapNotNull null
            val lat = o["lat"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            val lng = o["lon"]?.jsonPrimitive?.doubleOrNull ?: o["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            Poi("${o["type"]?.jsonPrimitive?.content}/${o["id"]?.jsonPrimitive?.content}", name, lat, lng, category.name, tags)
        }.distinctBy { it.id }

    suspend fun weather(at: LatLng): Weather {
        val w = json.parseToJsonElement(http("https://api.open-meteo.com/v1/forecast?latitude=${at.lat}&longitude=${at.lng}&current=temperature_2m,weather_code,is_day"))
            .jsonObject["current"]!!.jsonObject
        val aqi = runCatching {
            json.parseToJsonElement(http("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=${at.lat}&longitude=${at.lng}&current=us_aqi"))
                .jsonObject["current"]!!.jsonObject["us_aqi"]!!.jsonPrimitive.doubleOrNull?.toInt()
        }.getOrNull()
        return Weather(w["temperature_2m"]!!.jsonPrimitive.double, w["weather_code"]?.jsonPrimitive?.intOrNull ?: 0,
            (w["is_day"]?.jsonPrimitive?.intOrNull ?: 1) == 1, aqi)
    }

    /** The neighbourhood or locality name at a point, for the sheet header. */
    suspend fun areaName(at: LatLng): String? {
        if (BuildConfig.MAPTILER_KEY.isBlank()) return null
        val body = http("https://api.maptiler.com/geocoding/${at.lng},${at.lat}.json?key=${BuildConfig.MAPTILER_KEY}&limit=1&types=neighbourhood,locality,place,municipality")
        return json.parseToJsonElement(body).jsonObject["features"]?.jsonArray?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
    }

    fun distance(a: LatLng, b: LatLng) = a.metresTo(b)

    private suspend fun http(url: String, post: String? = null, contentType: String? = null): String = withContext(Dispatchers.IO) {
        val c = try { URL(url).openConnection() as HttpURLConnection } catch (e: IOException) { throw AppError(ErrorCode.NETWORK, e.message ?: "") }
        try {
            c.connectTimeout = 10_000; c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", UA)
            if (post != null) {
                c.requestMethod = "POST"; c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/json")
                c.outputStream.use { it.write(post.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw AppError(ErrorCode.SERVER, "HTTP $code")
            text
        } catch (e: IOException) { throw AppError(ErrorCode.NETWORK, e.message ?: "") } finally { c.disconnect() }
    }
}
