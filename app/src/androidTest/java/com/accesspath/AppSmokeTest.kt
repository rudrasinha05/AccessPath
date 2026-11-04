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
    private fun waitForMap() {
        waitForMap()
        compose.waitForIdle()
        if (compose.onAllNodesWithText(s(R.string.not_now)).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText(s(R.string.area_check_title)).assertExists()
            compose.onNodeWithText(s(R.string.not_now)).performClick()
        }
    }

    @Test
    fun opensOnTheMapWithSearchAndExplorePanel() {
        waitForMap()
        compose.onNodeWithTag("worldMap").assertExists()
        compose.onNodeWithText(s(R.string.explore_title)).assertExists()
    }

    @Test
    fun nearestWashroomShowsDirectionsAndNavigationStarts() {
        waitForMap()
        compose.onAllNodesWithText(s(R.string.nearby_washroom))[0].performClick()
        waitForText(s(R.string.start))
        compose.onNodeWithText(s(R.string.start)).performClick()
        waitForText(s(R.string.exit_navigation))
        compose.onNodeWithText(s(R.string.exit_navigation)).performClick()
        waitForText(s(R.string.search_campus))
    }

    @Test
    fun searchFindsAPlaceAndOpensItsCard() {
        waitForMap()
        compose.onNodeWithText(s(R.string.search_campus)).performClick()
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
        compose.onNodeWithText(s(R.string.campus_management)).performClick()
        compose.onNodeWithText(s(R.string.reviewer_mode)).assertIsDisplayed()
        compose.onNodeWithText("Lift A").assertExists()
    }
}
