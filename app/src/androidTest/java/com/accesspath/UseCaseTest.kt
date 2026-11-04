package com.accesspath

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import com.accesspath.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end checks of the main use cases, with a screenshot of each step. */
@RunWith(AndroidJUnit4::class)
class UseCaseTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun s(id: Int) = compose.activity.getString(id)
    private fun waitForText(text: String, ms: Long = 20_000) = compose.waitUntil(ms) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun save(name: String, settleMs: Long = 1500) {
        Thread.sleep(settleMs); compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun openCampus() {
        waitForText(s(R.string.search_here))
        TestCampus.load()
        compose.waitForIdle()
        if (compose.onAllNodesWithText(s(R.string.not_now)).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText(s(R.string.not_now)).performClick()
        waitForText(s(R.string.explore_title))
    }
    private fun directionsToLibrary() {
        compose.onNodeWithText(s(R.string.search_here)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Libr")
        compose.onAllNodes(hasText("Library") and hasAnyAncestor(isDialog()))[0].performClick()
        waitForText(s(R.string.directions))
        compose.onNodeWithText(s(R.string.directions)).performClick()
        waitForText(s(R.string.start))
    }

    /** The main use case: a blockage reported on the route closes that path and the route changes around it. */
    @Test
    fun reportedBarrierClosesThePathAndTheRouteAvoidsIt() {
        openCampus()
        directionsToLibrary()
        val before = campusSnapshot().let { Routing.calculate(it, it.current, it.destination) } as RouteResult.Found
        save("uc1-route-before")
        compose.onNodeWithText(s(R.string.start)).performClick()
        waitForText(s(R.string.exit_navigation))
        compose.onNodeWithContentDescription(s(R.string.report_title)).performClick()
        waitForText(s(R.string.description_label))
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Corridor blocked by construction work")
        save("uc2-report-form", 500)
        compose.onNodeWithText(s(R.string.submit)).performClick()
        compose.waitUntil(10_000) { campusSnapshot().reports.any { it.isActive } }

        val after = campusSnapshot()
        val reported = after.reports.single { it.isActive }.edgeId
        assertTrue("the reported path was on the old route", before.edges.any { it.id == reported })
        when (val r = Routing.calculate(after, after.current, after.destination)) {
            is RouteResult.Found -> assertTrue("new route avoids the barrier", r.edges.none { it.id == reported } && r.edges != before.edges)
            is RouteResult.Unavailable -> assertEquals(NoRouteCause.CLOSED_NOW, r.cause)
        }
        save("uc3-route-after-barrier", 2500)
    }

    /** Switching mobility mode re-plans the route for that person. */
    @Test
    fun mobilityModesReplanTheRoute() {
        openCampus()
        directionsToLibrary()
        listOf(Mobility.STANDARD, Mobility.AVOID_STAIRS, Mobility.WHEELCHAIR).forEach { m ->
            compose.onAllNodesWithText(s(m.labelRes()))[0].performClick()
            compose.waitUntil(10_000) { campusSnapshot().profile.mobility == m }
            val snap = campusSnapshot()
            val r = Routing.calculate(snap, snap.current, snap.destination)
            if (r is RouteResult.Found && m != Mobility.STANDARD) assertFalse("$m route uses no stairs", r.usesStairs)
            save("uc4-mode-${m.name.lowercase()}", 1500)
        }
    }

    /** Nearby alerts (geofencing) is opt-in and explains itself before asking for anything. */
    @Test
    fun nearbyAlertsShowADisclosureFirst() {
        openCampus()
        compose.onNodeWithText(s(R.string.tab_you)).performClick()
        compose.onNodeWithTag("nearbyAlerts").performScrollTo().performClick()
        waitForText(s(R.string.geo_turn_on))
        save("uc5-geofence-disclosure", 500)
        compose.onNodeWithText(s(R.string.not_now)).performClick()
        compose.onNodeWithText(s(R.string.geo_turn_on)).assertDoesNotExist()
    }

    private fun Mobility.labelRes() = when (this) {
        Mobility.STANDARD -> R.string.mobility_standard
        Mobility.AVOID_STAIRS -> R.string.mobility_avoid_stairs
        Mobility.WHEELCHAIR -> R.string.mobility_wheelchair
    }
}
