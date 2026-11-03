package com.accesspath

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Saves screenshots of each tab for review. CI uploads them with the device test reports. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun s(id: Int) = compose.activity.getString(id)

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun captureEveryTab() {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(s(R.string.where_to)).fetchSemanticsNodes().isNotEmpty() }
        save("1-navigate")
        compose.onNodeWithTag("navigate").performScrollToNode(hasText(s(R.string.map_hint)))
        save("2-floor-plan")
        compose.onNodeWithTag("navigate").performScrollToNode(hasText(s(R.string.steps_title)))
        save("3-directions")
        compose.onNodeWithText(s(R.string.tab_reports)).performClick()
        save("4-reports")
        compose.onNodeWithText(s(R.string.report_button)).performClick()
        save("5-report-form")
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        compose.onNodeWithText(s(R.string.tab_manage)).performClick()
        save("6-manage")
        compose.onNodeWithText(s(R.string.tab_settings)).performClick()
        save("7-settings")
    }
}
