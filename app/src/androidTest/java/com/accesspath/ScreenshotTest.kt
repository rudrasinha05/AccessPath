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

/** Saves screenshots of each tab for review. CI uploads them with the device test reports. */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun s(id: Int) = compose.activity.getString(id)

    /** Captures the window that contains [marker] (the main screen or an open dialog). */
    private fun save(name: String, marker: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot()).filter(hasAnyDescendant(hasText(marker))).onFirst().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitForText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun captureEveryTab() {
        waitForText(s(R.string.where_to))
        save("1-navigate", s(R.string.where_to))
        compose.onNodeWithTag("navigate").performScrollToNode(hasText(s(R.string.map_hint)))
        save("2-floor-plan", s(R.string.map_hint))
        compose.onNodeWithTag("navigate").performScrollToNode(hasText(s(R.string.steps_title)))
        save("3-directions", s(R.string.steps_title))
        compose.onNodeWithText(s(R.string.tab_reports)).performClick()
        save("4-reports", s(R.string.reports_title))
        compose.onNodeWithText(s(R.string.report_button)).performClick()
        save("5-report-form", s(R.string.description_label))
        compose.onNodeWithText(s(R.string.cancel)).performClick()
        compose.onNodeWithText(s(R.string.tab_manage)).performClick()
        save("6-manage", s(R.string.manage_title))
        compose.onNodeWithText(s(R.string.tab_settings)).performClick()
        save("7-settings", s(R.string.settings_title))
    }

    @Test
    fun captureHindi() {
        waitForText(s(R.string.where_to))
        try {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi")) }
            waitForText("कहाँ जाना है?")
            save("8-navigate-hindi", "कहाँ जाना है?")
        } finally {
            compose.runOnUiThread { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) }
        }
    }
}
