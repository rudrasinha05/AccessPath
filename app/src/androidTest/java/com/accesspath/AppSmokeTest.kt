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

    private fun waitForMap() = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(s(R.string.where_to)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun opensWithoutPermissionPromptsAndShowsDirections() {
        waitForMap()
        compose.onNodeWithText(s(R.string.where_to)).assertIsDisplayed()
        compose.onNodeWithTag("navigate").performScrollToNode(hasText(s(R.string.map_title)))
        compose.onNodeWithText(s(R.string.map_title)).assertExists()
        compose.onNodeWithTag("navigate").performScrollToNode(hasText(s(R.string.steps_title)))
        compose.onNodeWithText(s(R.string.steps_title)).assertExists()
    }

    @Test
    fun mobilitySettingsDialogOpensAndCancels() {
        waitForMap()
        compose.onAllNodesWithText(s(R.string.change))[2].performClick()
        compose.onNodeWithText(s(R.string.profile_title)).assertIsDisplayed()
        compose.onNodeWithText(s(R.string.mobility_avoid_stairs)).assertExists()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        compose.onNodeWithText(s(R.string.profile_title)).assertDoesNotExist()
    }

    @Test
    fun reportFormIsReachable() {
        waitForMap()
        compose.onNodeWithText(s(R.string.tab_reports)).performClick()
        compose.onNodeWithText(s(R.string.report_button)).performClick()
        compose.onNodeWithText(s(R.string.description_label)).assertExists()
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        compose.onNodeWithText(s(R.string.description_label)).assertDoesNotExist()
    }

    @Test
    fun manageAndSettingsTabsShowRealData() {
        waitForMap()
        compose.onNodeWithText(s(R.string.tab_manage)).performClick()
        compose.onNodeWithText(s(R.string.reviewer_mode)).assertIsDisplayed()
        compose.onNodeWithText("Lift A").assertExists()
        compose.onNodeWithText(s(R.string.tab_settings)).performClick()
        compose.onNodeWithText(s(R.string.map_data_title)).assertExists()
        compose.onNodeWithText(s(R.string.demo_warning)).assertExists()
    }
}
