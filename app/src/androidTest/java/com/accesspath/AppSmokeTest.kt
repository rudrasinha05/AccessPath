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

    @Test
    fun navigationAndProfileDialogWork() {
        compose.waitUntil(20000) { compose.onAllNodesWithText("Plan a route").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Plan a route").performScrollTo().performClick()
        compose.onNodeWithText("Select mobility persona:").assertIsDisplayed()
        compose.onNodeWithText("Standard").performClick()
        compose.onNodeWithText("Find Accessible Route").performClick()
        compose.onNodeWithText("Plan a route").assertExists()
    }

    @Test
    fun reportFormAndHelpAreReachable() {
        compose.waitUntil(20000) { compose.onAllNodesWithText("Plan a route").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Reports").performClick()
        compose.onNodeWithText("Report a barrier").performClick()
        compose.onNodeWithText("Describe the barrier").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Help").performClick()
        compose.onNodeWithText("Simple by design.").assertExists()
    }
}
