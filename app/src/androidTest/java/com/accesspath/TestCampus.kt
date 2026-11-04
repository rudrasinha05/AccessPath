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
        // Start each test clean: no reports or closures left over from an earlier test.
        app.repository.applyRemote { it.copy(reports = emptyList(), blockedEdges = emptySet()) }
        app.repository.importMap(if (empty) EMPTY else fixture())
    }
}


fun campusSnapshot() = runBlocking {
    (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AccessPathApplication).repository.snapshot()
}
