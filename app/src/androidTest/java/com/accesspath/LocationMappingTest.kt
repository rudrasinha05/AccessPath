package com.accesspath

import android.Manifest
import android.graphics.Bitmap
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.services.storage.TestStorage
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping by walking with location (the blue dot), driven by a fake GPS: walks 30 m north, names a room, walks 20 m
 * east and finishes. The draft area must be saved where the walk happened. The same live location feed is what
 * outdoor navigation follows, so this also checks that it delivers positions.
 */
@RunWith(AndroidJUnit4::class)
class LocationMappingTest {
    @get:Rule(order = 0)
    val location: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val lm get() = inst.targetContext.getSystemService(LocationManager::class.java)
    private val start = Pair(28.5000, 77.5000) // far from the test campus, so this area is unmapped
    private fun s(id: Int) = compose.activity.getString(id)
    private fun waitForText(text: String, ms: Long = 20_000) = compose.waitUntil(ms) {
        runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    @Before
    fun fakeGps() {
        // Wait for the shell command to finish; closing its output early can cancel it.
        android.os.ParcelFileDescriptor.AutoCloseInputStream(
            inst.uiAutomation.executeShellCommand("appops set ${inst.targetContext.packageName} android:mock_location allow")
        ).use { it.readBytes() }
        providers.forEach { p ->
            runCatching { lm.removeTestProvider(p) }
            lm.addTestProvider(p, false, false, false, false, true, true, true, 1, 1)
            lm.setTestProviderEnabled(p, true)
        }
        walkTo(0.0, 0.0)
    }

    @After
    fun stopFakeGps() { providers.forEach { runCatching { lm.removeTestProvider(it) } } }

    // Both providers are faked so no real (emulator default) position mixes into the walk.
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    /** Puts the fake GPS [north] m north and [east] m east of the start. */
    private fun walkTo(north: Double, east: Double) = providers.forEach { provider ->
        val loc = Location(provider).apply {
            latitude = start.first + north / 111_320.0
            longitude = start.second + east / (111_320.0 * Math.cos(Math.toRadians(start.first)))
            accuracy = 5f; altitude = 200.0
            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        lm.setTestProviderLocation(provider, loc)
    }

    private fun save(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onAllNodes(isRoot()).onFirst().captureToImage().asAndroidBitmap()
        TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun walkingWithLocationMapsANewArea() {
        waitForText(s(R.string.search_any))
        TestCampus.load()
        compose.waitForIdle()
        if (compose.onAllNodesWithText(s(R.string.not_now)).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText(s(R.string.not_now)).performClick()
        val draftsBefore = campusSnapshot().map.buildings.count { it.draft }

        compose.onNodeWithText(s(R.string.tab_you)).performClick()
        compose.onNodeWithText(s(R.string.map_new_area)).performScrollTo().performClick()
        waitForText(s(R.string.mapping_method_gps))
        save("uc6-mapping-choice")
        compose.onNodeWithText(s(R.string.mapping_method_gps)).performClick()

        // Walk 30 m north, one fix a second (2 m apart).
        for (i in 1..15) { walkTo(i * 2.0, 0.0); Thread.sleep(1_100) }
        compose.waitUntil(15_000) { runCatching { compose.onNodeWithText(s(R.string.add_place)).assertIsEnabled(); true }.getOrDefault(false) }
        compose.onNodeWithText(s(R.string.add_place)).performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Room 101")
        compose.onNodeWithText(s(R.string.save)).performClick()
        // Then 20 m east.
        for (i in 1..10) { walkTo(30.0, i * 2.0); Thread.sleep(1_100) }
        save("uc7-walk-recorded")
        compose.onNodeWithText(s(R.string.finish)).performClick()

        compose.waitUntil(15_000) { campusSnapshot().map.buildings.count { it.draft } > draftsBefore }
        val snap = campusSnapshot()
        val room = snap.map.nodes.firstOrNull { it.name == "Room 101" }
        assertNotNull("named place saved", room)
        val pos = com.accesspath.domain.Geo.position(snap.map, room!!)!!
        val expected = com.accesspath.domain.LatLng(start.first + 30.0 / 111_320.0, start.second)
        assertTrue("room saved where it was named", com.accesspath.domain.Coverage.distanceMetres(pos, expected) < 5.0)
        Thread.sleep(2_000)
        save("uc8-new-area-on-map")
    }
}
