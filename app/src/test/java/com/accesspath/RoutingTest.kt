package com.accesspath

import com.accesspath.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RoutingTest {
    private val demo: CampusMap = MapJson.decodeFromString(File("src/main/assets/maps/demo-campus.json").readText())
    private fun state() = AppSnapshot(demo)
    private fun found(s: AppSnapshot) = Routing.calculate(s) as RouteResult.Found
    private fun cause(s: AppSnapshot) = (Routing.calculate(s) as RouteResult.Unavailable).cause
    private fun withLifts(s: AppSnapshot, a: Availability) = s.copy(map = s.map.copy(facilities = s.map.facilities.map { it.copy(availability = a) }))

    // ---- Map data -----------------------------------------------------------------------------

    @Test fun demoMapIsValidWithDisconnectedWarning() {
        assertEquals(31, demo.nodes.size); assertEquals(72, demo.edges.size)
        assertTrue(demo.isDemo)
        assertTrue(MapValidator.validate(demo).isNotEmpty())
    }

    @Test fun invalidMapsAreRejected() {
        listOf(
            demo.copy(schemaVersion = 2),
            demo.copy(nodes = demo.nodes + demo.nodes.first()),
            demo.copy(edges = demo.edges + demo.edges.first().copy(id = "bad", from = "missing")),
            demo.copy(edges = listOf(demo.edges.first().copy(length = -1.0))),
            demo.copy(edges = listOf(demo.edges.first().copy(length = Double.NaN))),
            demo.copy(edges = listOf(demo.edges.first().copy(type = Segment.ELEVATOR, facilityId = null)))
        ).forEach { bad ->
            val e = assertThrows(AppError::class.java) { MapValidator.validate(bad) }
            assertEquals(ErrorCode.MAP_INVALID, e.code)
        }
    }

    // ---- Profiles -----------------------------------------------------------------------------

    @Test fun wheelchairUsesLiftAndNoStairs() {
        val r = found(state())
        assertTrue(r.edges.any { it.facilityId == "liftA" }); assertFalse(r.usesStairs); assertTrue(r.usesLift)
    }

    @Test fun walkingUsesShorterStairRoute() {
        val walking = found(state().copy(profile = Profile(Mobility.STANDARD)))
        assertTrue(walking.usesStairs)
        assertTrue(walking.distance < found(state()).distance)
        assertNull(walking.extraDistanceVsStandard)
    }

    @Test fun wheelchairRouteReportsExtraDistanceVsStairs() {
        val r = found(state())
        val walking = found(state().copy(profile = Profile(Mobility.STANDARD)))
        assertEquals(r.distance - walking.distance, r.extraDistanceVsStandard!!, 0.001)
    }

    @Test fun avoidStairsExcludesStairs() = assertFalse(found(state().copy(profile = Profile(Mobility.AVOID_STAIRS))).usesStairs)

    @Test fun unknownWidthAndStepFreeAreNotAssumedAccessible() =
        assertEquals(NoRouteCause.PROFILE_TOO_STRICT, cause(state().copy(destination = "unknown")))

    @Test fun overlyStrictWidthHasNoRoute() = assertEquals(NoRouteCause.PROFILE_TOO_STRICT, cause(state().copy(profile = Profile(minWidth = 2.0))))
    @Test fun steepRampHasNoRoute() = assertEquals(NoRouteCause.PROFILE_TOO_STRICT, cause(state().copy(profile = Profile(maxSlope = 1.0))))
    @Test fun invalidProfileDoesNotCrash() = assertEquals(NoRouteCause.INVALID_PROFILE, cause(state().copy(profile = Profile(minWidth = -1.0))))

    @Test fun maxWalkingDistanceIsFlaggedNotHidden() {
        val r = found(state().copy(profile = Profile(maxWalkingDistance = 10.0)))
        assertTrue(r.exceedsMaxWalkingDistance)
        assertFalse(found(state()).exceedsMaxWalkingDistance)
    }

    // ---- Closures and reports -----------------------------------------------------------------

    @Test fun failedFirstLiftUsesAlternative() {
        val s = state()
        val next = s.copy(map = s.map.copy(facilities = s.map.facilities.map { if (it.id == "liftA") it.copy(availability = Availability.UNAVAILABLE) else it }))
        val r = found(next)
        assertTrue(r.edges.any { it.facilityId == "liftB" }); assertFalse(r.edges.any { it.facilityId == "liftA" })
    }

    @Test fun allLiftsDownExplainsClosure() {
        val r = Routing.calculate(withLifts(state(), Availability.UNAVAILABLE)) as RouteResult.Unavailable
        assertEquals(NoRouteCause.CLOSED_NOW, r.cause)
        assertTrue(ExclusionReason.FACILITY_UNAVAILABLE in r.closures)
    }

    @Test fun unknownFacilityStatusIsExcluded() =
        assertEquals(NoRouteCause.CLOSED_NOW, cause(withLifts(state(), Availability.UNKNOWN)))

    @Test fun blockedHallUsesSouthCorridor() {
        val r = found(state().copy(current = "lobby2", blockedEdges = setOf("hall2", "hall2_r")))
        assertFalse(r.edges.any { it.id == "hall2" }); assertTrue(r.edges.any { it.id == "south2" })
    }

    @Test fun reportedLiftExcludesEveryFloorOfThatLift() {
        val s = state().copy(reports = listOf(BarrierReport("r", "liftA_0", BarrierCategory.LIFT_NOT_WORKING, "Lift is broken", 1)))
        assertFalse(found(s).edges.any { it.facilityId == "liftA" })
        assertEquals(ExclusionReason.PENDING_REPORT, Routing.exclusions(s)["liftA_1_r"])
    }

    @Test fun reportClosesBothDirections() {
        val s = state().copy(reports = listOf(BarrierReport("r", "hall2", BarrierCategory.BLOCKED_PATH, "Construction debris", 1)))
        assertTrue("hall2" in Routing.exclusions(s)); assertTrue("hall2_r" in Routing.exclusions(s))
    }

    @Test fun resolvingOneReportKeepsOtherRestriction() {
        val r = BarrierReport("1", "hall2", BarrierCategory.BLOCKED_PATH, "Construction debris", 1, status = ReportStatus.RESOLVED)
        val s = state().copy(reports = listOf(r, r.copy(id = "2", status = ReportStatus.VERIFIED)))
        assertEquals(ExclusionReason.VERIFIED_REPORT, Routing.exclusions(s)["hall2"])
    }

    @Test fun rejectedReportDoesNotRestrictPath() {
        val s = state().copy(reports = listOf(BarrierReport("1", "hall2", BarrierCategory.BLOCKED_PATH, "False report", 1, status = ReportStatus.REJECTED)))
        assertFalse("hall2" in Routing.exclusions(s))
    }

    @Test fun reviewTransitionsFollowTheRules() {
        val r = BarrierReport("1", "hall2", BarrierCategory.BLOCKED_PATH, "Construction", 1)
        assertEquals(ErrorCode.INVALID_TRANSITION, assertThrows(AppError::class.java) { ReportRules.review(r, ReportStatus.RESOLVED, "Not valid", 2) }.code)
        assertEquals(ErrorCode.REASON_LENGTH, assertThrows(AppError::class.java) { ReportRules.review(r, ReportStatus.VERIFIED, "ok", 2) }.code)
        val confirmed = ReportRules.review(r, ReportStatus.VERIFIED, "Confirmed on site", 2)
        assertEquals(ReportStatus.RESOLVED, ReportRules.review(confirmed, ReportStatus.RESOLVED, "Path is now clear", 3).status)
        assertThrows(AppError::class.java) { ReportRules.review(confirmed.copy(status = ReportStatus.RESOLVED), ReportStatus.VERIFIED, "Reopen it", 4) }
    }

    // ---- Graph behaviour ----------------------------------------------------------------------

    @Test fun disconnectedDestinationIsExplained() = assertEquals(NoRouteCause.NOT_CONNECTED, cause(state().copy(destination = "isolated")))
    @Test fun invalidLocationDoesNotCrash() = assertEquals(NoRouteCause.INVALID_ENDPOINTS, cause(state().copy(current = "invalid")))

    @Test fun sameLocationIsZeroLengthRoute() {
        val r = found(state().copy(destination = "gate"))
        assertEquals(0.0, r.distance, 0.0); assertTrue(r.edges.isEmpty())
    }

    @Test fun totalsAndContiguityMatchEdges() {
        val r = found(state())
        assertEquals(r.edges.sumOf { Routing.cost(it) }, r.score, 0.0001)
        assertEquals(r.edges.sumOf { it.length }, r.distance, 0.0001)
        r.edges.forEachIndexed { i, e -> assertEquals(r.nodes[i].id, e.from); assertEquals(r.nodes[i + 1].id, e.to) }
    }

    @Test fun recalculationStartsFromConfirmedLocation() = assertEquals("lobby1", found(state().copy(current = "lobby1")).nodes.first().id)

    @Test fun repeatedRunsHaveStablePaths() {
        val s = state(); val expected = found(s).edges
        repeat(20) { assertEquals(expected, found(s).edges) }
    }

    @Test fun oneWayEdgesDoNotImplyReverse() {
        val s = state()
        val next = s.copy(map = s.map.copy(edges = s.map.edges.filter { !it.id.endsWith("_r") }), current = "classroom", destination = "gate")
        assertTrue(Routing.calculate(next) is RouteResult.Unavailable)
    }

    @Test fun costModelUsesDefinedPenalties() {
        val e = demo.edges.first()
        assertEquals(15.0, Routing.cost(e.copy(type = Segment.RAMP, length = 10.0)), 0.0)
        assertEquals(20.0, Routing.cost(e.copy(type = Segment.STAIRS, length = 10.0)), 0.0)
        assertEquals(30.0, Routing.cost(e.copy(type = Segment.ELEVATOR, length = 10.0)), 0.0)
    }

    @Test fun largeGraphWarmPerformance() {
        val nodes = (0 until 1000).map { Node("n$it", "a0", "Node $it", it.toDouble(), 0.0) }
        val edges = (0 until 1000).flatMap { a -> (1..5).map { d -> Edge("e${a}_$d", "n$a", "n${(a + d) % 1000}", Segment.CORRIDOR, d.toDouble(), 2.0, 0.0, true) } }
        val s = AppSnapshot(demo.copy(nodes = nodes, edges = edges), current = "n0", destination = "n999")
        repeat(5) { found(s) }
        val ms = (1..30).map { val t = System.nanoTime(); assertEquals(999.0, found(s).distance, 0.001); (System.nanoTime() - t) / 1_000_000.0 }.sorted()
        assertTrue("Routing exceeded 500 ms warm p95: ${ms[28]}", ms[28] < 500)
    }

    // ---- Nearest place and directions ---------------------------------------------------------

    @Test fun nearestFindsReachablePlaces() {
        val s = state()
        assertEquals("washroom", Routing.nearest(s, NearbyKind.WASHROOM)?.id)
        assertEquals("liftA0", Routing.nearest(s, NearbyKind.LIFT)?.id)
        assertEquals("gate", Routing.nearest(s.copy(current = "classroom"), NearbyKind.EXIT)?.id)
    }

    @Test fun nearestLiftSkipsLiftsOutOfService() {
        val s = state().copy(current = "lobby2")
        assertEquals("liftA2", Routing.nearest(s, NearbyKind.LIFT)?.id)
        val liftADown = s.copy(map = demo.copy(facilities = demo.facilities.map { if (it.id == "liftA") it.copy(availability = Availability.UNAVAILABLE) else it }))
        assertEquals("liftB2", Routing.nearest(liftADown, NearbyKind.LIFT)?.id)
        assertNull(Routing.nearest(withLifts(s, Availability.UNAVAILABLE), NearbyKind.LIFT))
    }

    @Test fun stepsMergeConsecutiveLiftSegments() {
        val r = found(state())
        val steps = RouteSteps.build(r, demo)
        val lifts = steps.filter { it.type == Segment.ELEVATOR }
        assertEquals(1, lifts.size)
        assertEquals("Lift A", lifts.single().facilityName)
        assertEquals("Second floor", lifts.single().toFloorLabel)
        assertEquals(r.distance, steps.sumOf { it.distance }, 0.001)
        assertEquals(r.nodes.last().id, steps.last().to.id)
        assertEquals(r.nodes.lastIndex, steps.last().endNodeIndex)
    }

    // ---- Saved data compatibility -------------------------------------------------------------

    @Test fun legacyReportCategoriesStillLoad() {
        val json = """{"id":"r","edgeId":"hall2","category":"Unavailable elevator","description":"Broken","createdAt":1,"photoUri":null,"expiryHours":24}"""
        assertEquals(BarrierCategory.LIFT_NOT_WORKING, MapJson.decodeFromString<BarrierReport>(json).category)
        val other = """{"id":"r","edgeId":"hall2","category":"Something new","description":"Broken","createdAt":1}"""
        assertEquals(BarrierCategory.OTHER, MapJson.decodeFromString<BarrierReport>(other).category)
    }

    @Test fun legacySnapshotFieldsAreIgnored() {
        val s = state().copy(reports = listOf(BarrierReport("r", "hall2", BarrierCategory.BLOCKED_PATH, "Debris here", 1)))
        val legacy = MapJson.encodeToString(AppSnapshot.serializer(), s).dropLast(1) +
            ""","mapStyle":"SATELLITE","activeCampusId":"iitd","geofences":[],"assistanceRequests":[]}"""
        assertEquals(s, MapJson.decodeFromString<AppSnapshot>(legacy))
    }

    // ---- World map position -------------------------------------------------------------------

    @Test fun sampleCampusHasWorldPositionAndBuildingsDoNotOverlap() {
        assertTrue(Geo.hasGeo(demo))
        val gate = Geo.position(demo, demo.nodes.first { it.id == "gate" })!!
        val garden = Geo.position(demo, demo.nodes.first { it.id == "garden" })!!
        assertTrue(garden.lng > gate.lng)
    }

    @Test fun geoConversionMovesEastAndSouthInMetres() {
        val a = GeoAnchor(28.0, 77.0)
        val east = Geo.toLatLng(a, 100.0, 0.0)
        val south = Geo.toLatLng(a, 0.0, 100.0)
        assertEquals(28.0, east.lat, 1e-9); assertTrue(east.lng > 77.0)
        assertEquals(77.0, south.lng, 1e-9); assertEquals(28.0 - 100.0 / 111_320.0, south.lat, 1e-9)
        val turned = Geo.toLatLng(a.copy(rotationDeg = 90.0), 100.0, 0.0)
        assertTrue(turned.lat < 28.0)
    }

    @Test fun invalidGeoIsRejected() {
        assertThrows(AppError::class.java) { MapValidator.validate(demo.copy(geo = GeoAnchor(120.0, 77.0))) }
    }

    // ---- Coverage and camera surveys -----------------------------------------------------------

    @Test fun coverageKnowsMappedFromUnmapped() {
        val gate = Geo.position(demo, demo.nodes.first { it.id == "gate" })!!
        assertTrue(Coverage.isMapped(demo, gate))
        assertTrue(Coverage.isMapped(demo, LatLng(gate.lat + 20 / 111_320.0, gate.lng)))
        assertFalse(Coverage.isMapped(demo, LatLng(gate.lat + 500 / 111_320.0, gate.lng)))
    }

    @Test fun surveyBuildsDraftPathsWithUnknownAccessibility() {
        // Walk 12 m forward (−z) then 6 m to the right, facing north.
        val trail = (0..24).map { TrailPoint(0.0, -it * 0.5) } + (1..12).map { TrailPoint(it * 0.5, -12.0) }
        val start = LatLng(28.48, 77.49)
        val s = SurveyBuilder.build("survey_1", "Draft", trail, listOf(SurveyPlace(trail.lastIndex, "Lab 3", "ROOM")), start, 0.0)!!
        assertTrue(s.building.draft)
        assertTrue(s.nodes.size >= 5)
        assertEquals("Lab 3", s.nodes.last().name)
        assertTrue(s.edges.all { it.width == null && it.stepFree == null && it.slope == null })
        assertEquals(2 * (s.nodes.size - 1), s.edges.size)
        val merged = demo.copy(buildings = demo.buildings + s.building, floors = demo.floors + s.floor, nodes = demo.nodes + s.nodes, edges = demo.edges + s.edges)
        MapValidator.validate(merged)
        // First point sits at the start position; the walk went north then east.
        val first = Geo.position(merged, s.nodes.first())!!
        assertEquals(0.0, Coverage.distanceMetres(first, start), 0.5)
        val last = Geo.position(merged, s.nodes.last())!!
        assertTrue(last.lat > start.lat && last.lng > start.lng)
        // A mapped walk makes its own area count as mapped.
        assertTrue(Coverage.isMapped(merged, last))
        // Wheelchair routes skip unverified paths; walking routes may use them.
        val st = AppSnapshot(merged, current = s.nodes.first().id, destination = s.nodes.last().id)
        assertTrue(Routing.calculate(st) is RouteResult.Unavailable)
        assertTrue(Routing.calculate(st.copy(profile = Profile(Mobility.STANDARD))) is RouteResult.Found)
    }

    @Test fun surveyNeedsAWalk() {
        assertNull(SurveyBuilder.build("s", "D", listOf(TrailPoint(0.0, 0.0)), emptyList(), LatLng(0.0, 0.0), 0.0))
    }

    @Test fun qrLinksOpenTheRightPlace() {
        assertEquals("accesspath://node/lobby0", LocationLinks.forNode("lobby0"))
        val placards = LocationLinks.placesForPlacards(demo)
        assertTrue(placards.any { it.id == "washroom" })
        assertTrue(placards.none { it.type == "LANDMARK" })
    }
}
