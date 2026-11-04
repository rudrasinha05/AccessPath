package com.accesspath

import com.accesspath.data.BarrierKind
import com.accesspath.data.OutdoorBarrier
import com.accesspath.data.onLine
import com.accesspath.data.remote.OutdoorRoute
import com.accesspath.data.remote.TravelMode
import com.accesspath.domain.LatLng
import com.accesspath.ui.chooseRoute
import org.junit.Assert.assertEquals
import org.junit.Test

/** Outdoor smart routing: reported barriers decide which of the route choices is used. */
class BarrierRouteTest {
    // Two ways from A to B: straight east (fast), or north-east-south around a block (slower).
    private val a = LatLng(28.4740, 77.4800)
    private val straight = OutdoorRoute(TravelMode.WALK, 1000.0, 720.0, listOf(a, LatLng(28.4740, 77.4900)), emptyList())
    private val around = OutdoorRoute(TravelMode.WALK, 1400.0, 1000.0,
        listOf(a, LatLng(28.4780, 77.4800), LatLng(28.4780, 77.4900), LatLng(28.4740, 77.4900)), emptyList())
    private fun barrier(at: LatLng) = OutdoorBarrier("b", at.lat, at.lng, BarrierKind.CONSTRUCTION)

    @Test
    fun withoutReportsTheFastestRouteIsUsed() {
        val r = chooseRoute(listOf(straight, around), emptyList())
        assertEquals(0, r.index)
    }

    @Test
    fun aReportedBarrierOnTheFastestRouteSwitchesToTheOtherWay() {
        val onStraight = barrier(LatLng(28.4740, 77.4850))
        assertEquals(1, listOf(onStraight).onLine(straight.line).size)
        assertEquals(0, listOf(onStraight).onLine(around.line).size)
        val r = chooseRoute(listOf(straight, around), listOf(onStraight))
        assertEquals(1, r.index)
        assertEquals(0, r.barriersOnRoute.size)
    }

    @Test
    fun whenEveryRouteIsBlockedTheOneWithFewestBarriersIsUsedAndFlagged() {
        val b1 = barrier(LatLng(28.4740, 77.4850))
        val b2 = barrier(LatLng(28.4740, 77.4830)).copy(id = "b2")
        val b3 = barrier(LatLng(28.4780, 77.4850)).copy(id = "b3")
        val r = chooseRoute(listOf(straight, around), listOf(b1, b2, b3))
        assertEquals(1, r.index)
        assertEquals(1, r.barriersOnRoute.size)
    }

    @Test
    fun aBarrierFarFromTheLineDoesNotCount() {
        val far = barrier(LatLng(28.4760, 77.4850)) // about 220 m north of the straight road
        assertEquals(0, chooseRoute(listOf(straight, around), listOf(far)).index)
    }
}
