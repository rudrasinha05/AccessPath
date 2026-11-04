package com.accesspath

import com.accesspath.domain.*
import org.junit.Assert.*
import org.junit.Test

class GpsTrailTest {
    private val start = LatLng(28.4720, 77.4880)
    /** A point [north] m north and [east] m east of the start. */
    private fun at(north: Double, east: Double) =
        LatLng(start.lat + north / 111_320.0, start.lng + east / (111_320.0 * Math.cos(Math.toRadians(start.lat))))

    @Test
    fun weakAndTinyStepsAreSkipped() {
        val r = GpsTrailRecorder()
        assertTrue(r.add(at(0.0, 0.0), 5.0))
        assertFalse("too close", r.add(at(0.5, 0.0), 5.0))
        assertFalse("too weak", r.add(at(10.0, 0.0), 60.0))
        assertTrue(r.add(at(10.0, 0.0), 8.0))
        r.paused = true
        assertFalse("paused", r.add(at(20.0, 0.0), 5.0))
        assertEquals(2, r.size)
        assertEquals(10.0, r.distance, 0.1)
    }

    @Test
    fun walkedPathBecomesADraftAreaInTheRightPlace() {
        val r = GpsTrailRecorder()
        // 30 m north, then 20 m east.
        (0..15).forEach { r.add(at(it * 2.0, 0.0), 6.0) }
        (1..10).forEach { r.add(at(30.0, it * 2.0), 6.0) }
        r.addPlace("Room 101", "ROOM")
        val survey = SurveyBuilder.build("gps_t", "Walk", r.trail(), r.places, r.start!!, 0.0)!!
        val map = CampusMap(name = "C", provenance = "Field survey", version = "0", buildings = listOf(survey.building),
            floors = listOf(survey.floor), nodes = survey.nodes, edges = survey.edges, facilities = emptyList())
        val room = survey.nodes.single { it.name == "Room 101" }
        // The named place lands where it was added: 30 m north, 20 m east of the start.
        assertTrue(Coverage.distanceMetres(Geo.position(map, room)!!, at(30.0, 20.0)) < 1.0)
        val first = Geo.position(map, survey.nodes.first())!!
        assertTrue(Coverage.distanceMetres(first, start) < 1.0)
        assertTrue(MapValidator.validate(map).none { it.contains("error", ignoreCase = true) })
    }
}
