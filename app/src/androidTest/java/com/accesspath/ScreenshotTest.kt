package com.accesspath

import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Saves screenshots of the main screens for review. CI prints them to the log and uploads them. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun s(id: Int) = compose.activity.getString(id)
    private fun waitForText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun save(name: String, settleMs: Long = 0) {
        if (settleMs > 0) Thread.sleep(settleMs) // let map tiles and camera animations finish
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun captureMainScreens() {
        waitForText(s(R.string.search_campus))
        save("1-explore", 6000)
        compose.onNodeWithText(s(R.string.search_campus)).performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Libr")
        compose.onAllNodes(hasText("Library") and hasAnyAncestor(isDialog()))[0].performClick()
        waitForText(s(R.string.directions))
        save("2-place", 4000)
        compose.onNodeWithText(s(R.string.directions)).performClick()
        waitForText(s(R.string.start))
        save("3-directions", 4000)
        compose.onNodeWithText(s(R.string.start)).performClick()
        waitForText(s(R.string.exit_navigation))
        save("4-navigate", 4000)
        compose.onNodeWithText(s(R.string.exit_navigation)).performClick()
        compose.onNodeWithText(s(R.string.tab_contribute)).performClick()
        save("5-contribute")
        compose.onNodeWithText(s(R.string.tab_you)).performClick()
        save("6-you")
        compose.onNodeWithText(s(R.string.campus_management)).performClick()
        save("7-manage")
    }

    @Test
    fun captureHindi() {
        waitForText(s(R.string.search_campus))
        try {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi")) }
            waitForText("कैंपस में जगह खोजें")
            save("8-explore-hindi", 4000)
        } finally {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        }
    }
}
