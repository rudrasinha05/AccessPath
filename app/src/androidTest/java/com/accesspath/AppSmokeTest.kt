package com.accesspath

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun s(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
    private fun waitForText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** The first launch asks whether to check for unmapped areas; tests decline it. */
    private fun waitForMap(empty: Boolean = false) {
        waitForText(s(R.string.search_any))
        TestCampus.load(empty)
        compose.waitForIdle()
        if (compose.onAllNodesWithText(s(R.string.not_now)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(s(R.string.area_check_title)).assertExists()
            compose.onNodeWithText(s(R.string.not_now)).performClick()
        }
        waitForText(s(if (empty) R.string.no_campus_title else R.string.explore_title))
    }

    @Test
    fun opensOnTheMapWithSearchAndExplorePanel() {
        waitForMap()
        compose.onNodeWithTag("worldMap").assertExists()
        compose.onNodeWithText(s(R.string.explore_title)).assertExists()
    }

    @Test
    fun emptyCampusInvitesMappingAndShowsMapControls() {
        waitForMap(empty = true)
        compose.onNodeWithText(s(R.string.no_campus_title)).assertExists()
        compose.onNodeWithText(s(R.string.map_this_area)).assertExists()
        compose.onNodeWithTag("compass").assertExists()
        compose.onNodeWithContentDescription(s(R.string.zoom_in)).assertExists()
        compose.onNodeWithContentDescription(s(R.string.zoom_out)).performClick()
    }

    @Test
    fun longPressDropsPinWithOptionsAndTravelModes() {
        waitForMap()
        compose.onNodeWithTag("worldMap").performTouchInput { longClick(percentOffset(0.5f, 0.35f)) }
        waitForText(s(R.string.pin_title))
        compose.onNodeWithText(s(R.string.view_campus)).assertExists()
        compose.onNodeWithText(s(R.string.map_this_area)).assertExists()
        compose.onNodeWithText(s(R.string.directions)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("mode_bus").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("mode_bus").performClick()
        waitForText(s(R.string.open_google_maps))
        compose.onNodeWithTag("mode_car").assertExists()
        compose.onNodeWithTag("mode_wheelchair").assertExists()
    }

    @Test
    fun nearestWashroomShowsDirectionsAndNavigationStarts() {
        waitForMap()
        compose.onAllNodesWithText(s(R.string.nearby_washroom))[0].performClick()
        waitForText(s(R.string.start))
        compose.onNodeWithText(s(R.string.start)).performClick()
        waitForText(s(R.string.exit_navigation))
        compose.onNodeWithText(s(R.string.exit_navigation)).performClick()
        waitForText(s(R.string.search_any))
    }

    @Test
    fun searchFindsAPlaceAndOpensItsCard() {
        waitForMap()
        compose.onNodeWithText(s(R.string.search_any)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Libr")
        compose.onAllNodes(hasText("Library") and hasAnyAncestor(isDialog()))[0].performClick()
        waitForText(s(R.string.directions))
        compose.onNodeWithText(s(R.string.directions)).assertExists()
    }

    @Test
    fun reportFormIsReachable() {
        waitForMap()
        compose.onNodeWithText(s(R.string.tab_contribute)).performClick()
        compose.onNodeWithText(s(R.string.report_button)).performClick()
        compose.onNodeWithText(s(R.string.description_label)).assertExists()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        compose.onNodeWithText(s(R.string.description_label)).assertDoesNotExist()
    }

    @Test
    fun youTabOpensCampusManagement() {
        waitForMap()
        compose.onNodeWithText(s(R.string.tab_you)).performClick()
        compose.onNodeWithText(s(R.string.campus_management)).performScrollTo().performClick()
        compose.onNodeWithText(s(R.string.reviewer_mode)).assertIsDisplayed()
        compose.onNodeWithText("Lift A").assertExists()
    }
}
