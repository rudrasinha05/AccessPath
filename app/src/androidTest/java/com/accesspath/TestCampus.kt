package com.accesspath

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking

/** The app ships with an empty campus; UI tests load a small test campus (or an empty one) into the app's storage. */
object TestCampus {
    private const val EMPTY = """{"id":"main-campus","schemaVersion":1,"version":"0","name":"Campus","provenance":"Field survey","buildings":[],"floors":[],"nodes":[],"edges":[],"facilities":[]}"""

    private fun fixture() = InstrumentationRegistry.getInstrumentation().context.assets
        .open("maps/demo-campus.json").bufferedReader().use { it.readText() }

    fun load(empty: Boolean = false) = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AccessPathApplication
        app.repository.initialize()
        app.repository.importMap(if (empty) EMPTY else fixture())
    }
}

/**
 * A real long press in the middle of [tag]'s node. Compose's simulated touches run on test time, but the map's
 * long-press detector uses the real clock, so this sends actual touch events with a real pause.
 */
fun androidx.compose.ui.test.junit4.ComposeTestRule.realLongPress(tag: String) {
    val b = onNode(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNode().boundsInWindow
    val x = (b.left + b.right) / 2f; val y = b.top + (b.bottom - b.top) * 0.35f
    val inst = InstrumentationRegistry.getInstrumentation()
    val down = android.os.SystemClock.uptimeMillis()
    inst.sendPointerSync(android.view.MotionEvent.obtain(down, down, android.view.MotionEvent.ACTION_DOWN, x, y, 0))
    Thread.sleep(1_200)
    val up = android.os.SystemClock.uptimeMillis()
    inst.sendPointerSync(android.view.MotionEvent.obtain(down, up, android.view.MotionEvent.ACTION_UP, x, y, 0))
    waitForIdle()
}
