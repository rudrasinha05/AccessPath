package com.accesspath

import com.accesspath.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class FenceTest {
    private val campus: CampusMap = MapJson.decodeFromString(CampusMap.serializer(), File("src/test/resources/demo-campus.json").readText())

    @Test
    fun emptyCampusWatchesNothing() {
        val empty = campus.copy(buildings = emptyList(), floors = emptyList(), nodes = emptyList(), edges = emptyList(), facilities = emptyList())
        assertTrue(FencePlanner.plan(AppSnapshot(empty)).isEmpty())
    }

    @Test
    fun everyMappedBuildingGetsAnAreaThatCoversItsPlaces() {
        val fences = FencePlanner.plan(AppSnapshot(campus))
        val areas = fences.filter { it.kind == FenceKind.AREA }
        assertEquals(campus.buildings.size, areas.size)
        areas.forEach { f ->
            assertTrue(f.radius >= FencePlanner.MIN_RADIUS)
            val buildingId = f.id.removePrefix("area:")
            val floors = campus.floors.filter { it.buildingId == buildingId }.map { it.id }.toSet()
            campus.nodes.filter { it.floorId in floors }.mapNotNull { Geo.position(campus, it) }.forEach {
                assertTrue(Coverage.distanceMetres(LatLng(f.lat, f.lng), it) <= f.radius)
            }
        }
        // Nothing is closed or broken in the fixture.
        assertTrue(fences.none { it.kind == FenceKind.PATH_CLOSED || it.kind == FenceKind.LIFT_DOWN })
    }

    @Test
    fun brokenLiftsAlertOnlyPeopleWhoNeedLifts() {
        val broken = campus.copy(facilities = campus.facilities.map { if (it.id == "liftA") it.copy(availability = Availability.UNAVAILABLE) else it })
        assertTrue(FencePlanner.plan(AppSnapshot(broken)).none { it.kind == FenceKind.LIFT_DOWN })
        val wheelchair = FencePlanner.plan(AppSnapshot(broken, profile = Profile(Mobility.WHEELCHAIR)))
        assertEquals(listOf("lift:liftA"), wheelchair.filter { it.kind == FenceKind.LIFT_DOWN }.map { it.id })
    }

    @Test
    fun closedPathsGetOneAlertPerSegment() {
        val edge = campus.edges.first { it.id == "hall2" }
        val reverse = campus.edges.first { it.from == edge.to && it.to == edge.from }
        val s = AppSnapshot(campus, blockedEdges = setOf(edge.id, reverse.id))
        val closed = FencePlanner.plan(s).filter { it.kind == FenceKind.PATH_CLOSED }
        assertEquals(1, closed.size)
        assertTrue(closed.single().name.isNotBlank())
    }

    @Test
    fun neverMoreThanAndroidAllows() {
        val many = campus.copy(buildings = (1..150).map { i -> campus.buildings.first().copy(id = "b$i", name = "B$i") },
            floors = (1..150).map { i -> campus.floors.first().copy(id = "f$i", buildingId = "b$i") },
            nodes = (1..150).map { i -> campus.nodes.first().copy(id = "n$i", floorId = "f$i") }, edges = emptyList(), facilities = emptyList())
        assertEquals(FencePlanner.MAX_FENCES, FencePlanner.plan(AppSnapshot(many)).size)
    }
}
