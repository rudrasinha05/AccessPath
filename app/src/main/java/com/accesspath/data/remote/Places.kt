package com.accesspath.data.remote

import com.accesspath.BuildConfig
import com.accesspath.domain.AppError
import com.accesspath.domain.ErrorCode
import com.accesspath.domain.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Place categories shown as chips on the map. Each maps to OpenStreetMap tags (real places, no sample data). */
enum class PoiCategory(val filter: String, val keywords: List<String> = emptyList()) {
    RESTAURANTS("""["amenity"~"^(restaurant|fast_food|food_court)$"]""", listOf("restaurant", "fast food")),
    COFFEE("""["amenity"="cafe"]""", listOf("cafe")),
    PETROL("""["amenity"="fuel"]""", listOf("fuel", "petrol pump")),
    HOTELS("""["tourism"~"^(hotel|guest_house|hostel|motel)$"]""", listOf("hotel", "guest house")),
    ATMS("""["amenity"~"^(atm|bank)$"]""", listOf("atm", "bank")),
    HOSPITALS("""["amenity"~"^(hospital|clinic|doctors|pharmacy)$"]""", listOf("hospital", "clinic", "pharmacy")),
    SHOPPING("""["shop"~"^(supermarket|mall|department_store|convenience|clothes|general)$"]""", listOf("supermarket", "mall")),
    TOILETS("""["amenity"="toilets"]""", listOf("toilets")),
    ACCESSIBLE("""["wheelchair"~"^(yes|designated)$"]["name"]""", listOf("toilets", "hospital", "restaurant", "bank"))
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
    private const val UA = "AccessPath/${BuildConfig.VERSION_NAME} (+https://github.com/rudrasinha05/AccessPath)"
    private val json = Json { ignoreUnknownKeys = true }
    private val overpass = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter", "https://overpass.private.coffee/api/interpreter")

    /**
     * Real places of a category near [center], nearest first. Uses OpenStreetMap's Nominatim search inside a box
     * around the point (fast and reliable); one request per keyword, spaced as its usage policy asks.
     * "Accessible places" keeps only places whose wheelchair access is recorded as yes, designated or limited.
     */
    suspend fun nearby(category: PoiCategory, center: LatLng, radiusM: Int = 1500, limit: Int = 60): List<Poi> {
        val dLat = radiusM / 111_320.0
        val dLng = radiusM / (111_320.0 * Math.cos(Math.toRadians(center.lat)))
        val box = "${center.lng - dLng},${center.lat + dLat},${center.lng + dLng},${center.lat - dLat}"
        val all = mutableListOf<Poi>()
        var failure: Exception? = null
        category.keywords.forEachIndexed { i, kw ->
            if (i > 0) kotlinx.coroutines.delay(1_100)
            try {
                val body = http("https://nominatim.openstreetmap.org/search?q=${URLEncoder.encode(kw, "UTF-8")}&format=jsonv2&limit=40" +
                    "&viewbox=$box&bounded=1&extratags=1&addressdetails=1&accept-language=en")
                all += parseNominatim(body, category)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { failure = e }
        }
        if (all.isEmpty() && failure != null) throw (failure as? AppError) ?: AppError(ErrorCode.NETWORK, failure?.message ?: "")
        val list = all.distinctBy { it.id }.let { l ->
            if (category == PoiCategory.ACCESSIBLE) l.filter { it.wheelchair in setOf("yes", "designated", "limited") } else l
        }
        return list.sortedBy { distance(center, it.at) }.take(limit)
    }

    private fun parseNominatim(body: String, category: PoiCategory): List<Poi> =
        json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
            val o = el.jsonObject
            val extra = o["extratags"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val addr = o["address"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: o["display_name"]?.jsonPrimitive?.contentOrNull?.substringBefore(",") ?: return@mapNotNull null
            val tags = extra + listOfNotNull(
                addr["house_number"]?.let { "addr:housenumber" to it }, addr["road"]?.let { "addr:street" to it },
                (addr["suburb"] ?: addr["neighbourhood"])?.let { "addr:suburb" to it }, (addr["city"] ?: addr["town"] ?: addr["village"])?.let { "addr:city" to it }
            ) + listOfNotNull(o["type"]?.jsonPrimitive?.contentOrNull?.let { "type" to it })
            Poi("${o["osm_type"]?.jsonPrimitive?.content}/${o["osm_id"]?.jsonPrimitive?.content}", name,
                o["lat"]!!.jsonPrimitive.content.toDouble(), o["lon"]!!.jsonPrimitive.content.toDouble(), category.name, tags)
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
        val body = http("https://api.maptiler.com/geocoding/${at.lng},${at.lat}.json?key=${BuildConfig.MAPTILER_KEY}")
        // Prefer the most local named area; fall back to whatever comes first.
        val features = json.parseToJsonElement(body).jsonObject["features"]?.jsonArray.orEmpty().map { it.jsonObject }
        val preferred = listOf("neighbourhood", "locality", "place", "municipality", "county")
        fun types(o: JsonObject) = o["place_type"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val pick = preferred.firstNotNullOfOrNull { t -> features.firstOrNull { t in types(it) } } ?: features.firstOrNull()
        return pick?.get("text")?.jsonPrimitive?.contentOrNull
    }

    fun distance(a: LatLng, b: LatLng) = a.metresTo(b)

    private suspend fun http(url: String, post: String? = null, contentType: String? = null): String = withContext(Dispatchers.IO) {
        val c = try { URL(url).openConnection() as HttpURLConnection } catch (e: IOException) { throw AppError(ErrorCode.NETWORK, e.message ?: "") }
        try {
            c.connectTimeout = 8_000; c.readTimeout = 15_000
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
