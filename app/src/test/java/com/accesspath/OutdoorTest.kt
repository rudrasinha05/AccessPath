package com.accesspath

import com.accesspath.data.remote.metresTo
import com.accesspath.data.remote.metresToLine
import com.accesspath.domain.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorTest {
    @Test
    fun distancesMatchKnownValues() {
        val a = LatLng(28.0, 77.0)
        // One thousandth of a degree of latitude is about 111 m.
        assertEquals(111.2, a.metresTo(LatLng(28.001, 77.0)), 0.5)
        val line = listOf(LatLng(28.0, 77.0), LatLng(28.0, 77.01))
        assertEquals(0.0, LatLng(28.0, 77.005).metresToLine(line), 0.5)
        assertEquals(111.2, LatLng(28.001, 77.005).metresToLine(line), 1.0)
        // Beyond the end of the line the distance is to the end point.
        assertEquals(LatLng(28.0, 77.02).metresTo(LatLng(28.0, 77.01)), LatLng(28.0, 77.02).metresToLine(line), 0.5)
    }

    @Test
    fun surveyRoundTripsAsJsonAndCanBeRebuiltFromTheMap() {
        val trail = (0..20).map { TrailPoint(0.0, -it * 0.5) }
        val survey = SurveyBuilder.build("survey_x", "Draft", trail, listOf(SurveyPlace(10, "Room 1", "ROOM")), LatLng(28.48, 77.49), 0.0)!!
        val text = MapJson.encodeToString(Survey.serializer(), survey)
        assertEquals(survey, MapJson.decodeFromString(Survey.serializer(), text))
        val map = CampusMap(name = "Campus", provenance = "Field survey", version = "0",
            buildings = listOf(survey.building), floors = listOf(survey.floor), nodes = survey.nodes, edges = survey.edges, facilities = emptyList())
        assertEquals(survey, Survey.of(map, "survey_x"))
        assertNull(Survey.of(map, "missing"))
    }
}
