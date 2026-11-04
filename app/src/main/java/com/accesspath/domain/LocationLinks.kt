package com.accesspath.domain

/** Links printed as QR codes at places. Opening one confirms the person's position at that place. */
object LocationLinks {
    const val SCHEME = "accesspath"
    const val HOST = "node"

    fun forNode(id: String): String = "$SCHEME://$HOST/" + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")

    /** Places worth a printed QR code: everything except plain corridor points. */
    fun placesForPlacards(map: CampusMap): List<Node> {
        val floors = map.floors.associateBy { it.id }
        return map.nodes.filter { !it.type.equals("LANDMARK", true) }
            .sortedWith(compareBy({ floors[it.floorId]?.buildingId }, { floors[it.floorId]?.ordinal }, { it.name }))
    }
}
