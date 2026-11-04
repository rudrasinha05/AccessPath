package com.accesspath.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class LatLng(val lat: Double, val lng: Double)

/** Converts floor-local metres to real-world coordinates using the building's or map's [GeoAnchor]. */
object Geo {
    private const val METRES_PER_DEGREE_LAT = 111_320.0

    /** True when every place can be shown on a world map. */
    fun hasGeo(map: CampusMap): Boolean = map.geo != null || (map.buildings.isNotEmpty() && map.buildings.all { it.geo != null })

    fun anchorFor(map: CampusMap, node: Node): GeoAnchor? {
        val buildingId = map.floors.firstOrNull { it.id == node.floorId }?.buildingId
        return map.buildings.firstOrNull { it.id == buildingId }?.geo ?: map.geo
    }

    fun toLatLng(anchor: GeoAnchor, x: Double, y: Double): LatLng {
        val r = anchor.rotationDeg * PI / 180.0
        val east = x * cos(r) - y * sin(r)
        val south = x * sin(r) + y * cos(r)
        val lat = anchor.lat - south / METRES_PER_DEGREE_LAT
        val lng = anchor.lng + east / (METRES_PER_DEGREE_LAT * cos(anchor.lat * PI / 180.0))
        return LatLng(lat, lng)
    }

    fun position(map: CampusMap, node: Node): LatLng? = anchorFor(map, node)?.let { toLatLng(it, node.x, node.y) }
}
