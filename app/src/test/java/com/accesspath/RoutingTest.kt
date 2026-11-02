package com.accesspath

import com.accesspath.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RoutingTest {
    private fun state() = AppSnapshot(MapJson.decodeFromString<CampusMap>(File("src/main/assets/maps/demo-campus.json").readText()))
    private fun found(s:AppSnapshot) = Routing.calculate(s) as RouteResult.Found
    @Test fun fixtureIsValidWithExplicitDisconnectedWarning() {
        val s=state(); assertEquals(31,s.map.nodes.size); assertEquals(72,s.map.edges.size)
        assertTrue(MapValidator.validate(s.map).isNotEmpty())
    }
    @Test fun wheelchairUsesLiftAndNoStairs() {
        val route=found(state());assertTrue(route.edges.any{it.facilityId=="liftA"});assertFalse(route.edges.any{it.type==Segment.STAIRS})
    }
    @Test fun standardUsesShorterStairRoute() {
        val route=found(state().copy(profile=Profile(Mobility.STANDARD)))
        assertTrue(route.edges.any{it.type==Segment.STAIRS});assertTrue(route.distance<found(state()).distance)
    }
    @Test fun avoidStairsExcludesStairs() { assertFalse(found(state().copy(profile=Profile(Mobility.AVOID_STAIRS))).edges.any{it.type==Segment.STAIRS}) }
    @Test fun failedFirstLiftUsesAlternative() {
        val s=state();val next=s.copy(map=s.map.copy(facilities=s.map.facilities.map{if(it.id=="liftA")it.copy(availability=Availability.UNAVAILABLE)else it}))
        assertTrue(found(next).edges.any{it.facilityId=="liftB"});assertFalse(found(next).edges.any{it.facilityId=="liftA"})
    }
    @Test fun allFailedLiftsReturnNoRoute() {
        val s=state();assertTrue(Routing.calculate(s.copy(map=s.map.copy(facilities=s.map.facilities.map{it.copy(availability=Availability.UNAVAILABLE)}))) is RouteResult.Unavailable)
    }
    @Test fun blockedHallUsesSouthCorridor() {
        val s=state().copy(current="lobby2",blockedEdges=setOf("hall2","hall2_r"))
        val r=found(s);assertFalse(r.edges.any{it.id=="hall2"});assertTrue(r.edges.any{it.id=="south2"})
    }
    @Test fun reportedElevatorExcludesEntireFacility() {
        val s=state().copy(reports=listOf(BarrierReport("r","liftA_0","Unavailable elevator","Lift is broken",1)))
        assertFalse(found(s).edges.any{it.facilityId=="liftA"});assertTrue("liftA_1_r" in Routing.exclusions(s))
    }
    @Test fun reportClosesBothDirections() {
        val s=state().copy(reports=listOf(BarrierReport("r","hall2","Blocked path","Construction debris",1)))
        assertTrue("hall2" in Routing.exclusions(s));assertTrue("hall2_r" in Routing.exclusions(s))
    }
    @Test fun resolvingOneReportKeepsOtherRestriction() {
        val r=BarrierReport("1","hall2","Blocked path","Construction debris",1,status=ReportStatus.RESOLVED)
        val s=state().copy(reports=listOf(r,r.copy(id="2",status=ReportStatus.VERIFIED)))
        assertTrue("hall2" in Routing.exclusions(s))
    }
    @Test fun rejectedReportDoesNotRestrictPath() {
        val s=state().copy(reports=listOf(BarrierReport("1","hall2","Blocked path","False report",1,status=ReportStatus.REJECTED)))
        assertFalse("hall2" in Routing.exclusions(s))
    }
    @Test fun unknownWidthAndStepFreeAreNotAssumedAccessible() {assertTrue(Routing.calculate(state().copy(destination="unknown")) is RouteResult.Unavailable)}
    @Test fun overlyStrictWidthHasNoRoute() {assertTrue(Routing.calculate(state().copy(profile=Profile(minWidth=2.0))) is RouteResult.Unavailable)}
    @Test fun steepRampHasNoRoute() {assertTrue(Routing.calculate(state().copy(profile=Profile(maxSlope=1.0))) is RouteResult.Unavailable)}
    @Test fun disconnectedDestinationHasNoRoute() {assertTrue(Routing.calculate(state().copy(destination="isolated")) is RouteResult.Unavailable)}
    @Test fun sameLocationIsZeroLengthRoute() {val r=found(state().copy(destination="gate"));assertEquals(0.0,r.distance,0.0);assertTrue(r.edges.isEmpty())}
    @Test fun invalidLocationDoesNotCrash() {assertTrue(Routing.calculate(state().copy(current="invalid")) is RouteResult.Unavailable)}
    @Test fun totalsAndContiguityMatchEdges() {
        val r=found(state());assertEquals(r.edges.sumOf{Routing.cost(it)},r.score,0.0001);assertEquals(r.edges.sumOf{it.length},r.distance,0.0001)
        r.edges.forEachIndexed{i,e->assertEquals(r.nodes[i].id,e.from);assertEquals(r.nodes[i+1].id,e.to)}
    }
    @Test fun recalculationStartsFromConfirmedLocation() {val r=found(state().copy(current="lobby1"));assertEquals("lobby1",r.nodes.first().id)}
    @Test fun unknownFacilityStatusIsExcluded() {
        val s=state();val next=s.copy(map=s.map.copy(facilities=s.map.facilities.map{it.copy(availability=Availability.UNKNOWN)}))
        assertTrue(Routing.calculate(next) is RouteResult.Unavailable)
    }
    @Test fun repeatedRunsHaveStablePaths() {val s=state();val expected=found(s).edges;repeat(20){assertEquals(expected,found(s).edges)}}
    @Test fun oneWayEdgesDoNotImplyReverse() {
        val s=state();val next=s.copy(map=s.map.copy(edges=s.map.edges.filter{!it.id.endsWith("_r")} ), current="classroom",destination="gate")
        assertTrue(Routing.calculate(next) is RouteResult.Unavailable)
    }
    @Test fun invalidImportsAreRejected() {
        val m=state().map
        listOf(m.copy(schemaVersion=2),m.copy(nodes=m.nodes+m.nodes.first()),m.copy(edges=m.edges+ m.edges.first().copy(id="bad",from="missing")),
            m.copy(edges=listOf(m.edges.first().copy(length=-1.0))),m.copy(edges=listOf(m.edges.first().copy(length=Double.NaN)))).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java){MapValidator.validate(bad)}
        }
    }
    @Test fun invalidReviewTransitionsAreRejected() {
        val r=BarrierReport("1","hall2","Blocked path","Construction",1)
        assertThrows(IllegalArgumentException::class.java){ReportRules.review(r,ReportStatus.RESOLVED,"Not valid",2)}
        val approved=ReportRules.review(r,ReportStatus.VERIFIED,"Confirmed on site",2)
        assertEquals(ReportStatus.RESOLVED,ReportRules.review(approved,ReportStatus.RESOLVED,"Path is now clear",3).status)
    }
    @Test fun costModelUsesNonnegativeDefinedPenalties() {
        val e=state().map.edges.first()
        assertEquals(15.0,Routing.cost(e.copy(type=Segment.RAMP,length=10.0)),0.0)
        assertEquals(20.0,Routing.cost(e.copy(type=Segment.STAIRS,length=10.0)),0.0)
        assertEquals(30.0,Routing.cost(e.copy(type=Segment.ELEVATOR,length=10.0)),0.0)
    }
    @Test fun largeGraphWarmPerformance() {
        val base=state().map
        val nodes=(0 until 1000).map{Node("n$it","a0","Node $it",it.toDouble(),0.0)}
        val edges=(0 until 1000).flatMap{a->(1..5).map{d->Edge("e${a}_$d","n$a","n${(a+d)%1000}",Segment.CORRIDOR,d.toDouble(),2.0,0.0,true)}}
        val s=AppSnapshot(base.copy(nodes=nodes,edges=edges),current="n0",destination="n999")
        repeat(5){found(s)}
        val ms=(1..30).map{val t=System.nanoTime();val r=found(s);assertEquals(999.0,r.distance,0.001);(System.nanoTime()-t)/1_000_000.0}.sorted()
        println("1000 nodes / 5000 directed edges: warm p95=${ms[28]} ms")
        assertTrue("Routing exceeded 500 ms warm p95",ms[28]<500)
    }
    @Test fun dynamicLandmarkAndConnectorRouting() {
        val s = state()
        val newNode = Node("new_lab", "a0", "Robotics Lab", 150.0, 150.0)
        val forwardEdge = Edge("to_lab", "lobby0", "new_lab", Segment.CORRIDOR, 12.0, 1.5, 0.0, true, true)
        val reverseEdge = forwardEdge.copy(id="to_lab_r", from="new_lab", to="lobby0")
        val updatedMap = s.map.copy(nodes = s.map.nodes + newNode, edges = s.map.edges + listOf(forwardEdge, reverseEdge))
        MapValidator.validate(updatedMap)
        val route = Routing.calculate(s.copy(map = updatedMap, destination = "new_lab"))
        assertTrue(route is RouteResult.Found)
        assertEquals("new_lab", (route as RouteResult.Found).nodes.last().id)
    }
}