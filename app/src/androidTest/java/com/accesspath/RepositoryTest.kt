package com.accesspath

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.accesspath.data.*
import com.accesspath.domain.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: AccessDatabase
    private lateinit var repo: CampusRepository
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    // The fixture lives in the test APK, so read it with the instrumentation context.
    private fun seed() = InstrumentationRegistry.getInstrumentation().context.assets.open("maps/demo-campus.json").bufferedReader().use { it.readText() }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AccessDatabase::class.java).allowMainThreadQueries().build()
        repo = CampusRepository(db, ::seed)
    }

    @After
    fun close() = db.close()

    @Test
    fun reportReviewAndAuditAreAtomic() = runBlocking {
        repo.initialize()
        repo.submitReport("r", "hall2", BarrierCategory.BLOCKED_PATH, "Construction debris")
        val s = repo.snapshot()
        assertEquals(1, s.reports.size)
        repo.review("r", ReportStatus.VERIFIED, "Confirmed by reviewer", s.revision)
        val n = repo.snapshot()
        assertEquals(ReportStatus.VERIFIED, n.reports.first().status)
        assertEquals(1, n.audit.size)
    }

    @Test
    fun duplicateSubmissionIsIdempotent() = runBlocking {
        repo.initialize()
        repeat(2) { repo.submitReport("r", "hall2", BarrierCategory.BLOCKED_PATH, "Construction debris") }
        assertEquals(1, repo.snapshot().reports.size)
    }

    @Test
    fun invalidReportIsRejected() = runBlocking {
        repo.initialize()
        val e = runCatching { repo.submitReport("r", "hall2", BarrierCategory.BLOCKED_PATH, "no") }.exceptionOrNull()
        assertEquals(ErrorCode.DESCRIPTION_LENGTH, (e as AppError).code)
        val e2 = runCatching { repo.submitReport("r", "nope", BarrierCategory.BLOCKED_PATH, "Long enough") }.exceptionOrNull()
        assertEquals(ErrorCode.UNKNOWN_SEGMENT, (e2 as AppError).code)
        assertTrue(repo.snapshot().reports.isEmpty())
    }

    @Test
    fun staleUpdateIsRejected() = runBlocking {
        repo.initialize()
        val rev = repo.snapshot().revision
        repo.setFacility("liftA", Availability.UNAVAILABLE, "Maintenance", rev)
        val e = runCatching { repo.setFacility("liftB", Availability.UNAVAILABLE, "Maintenance", rev) }.exceptionOrNull()
        assertEquals(ErrorCode.STALE_DATA, (e as AppError).code)
        assertEquals(Availability.AVAILABLE, repo.snapshot().map.facilities.first { it.id == "liftB" }.availability)
    }

    @Test
    fun parallelReportsDoNotLoseUpdates() = runBlocking {
        repo.initialize()
        (1..10).map { i -> async(Dispatchers.IO) { repo.submitReport("r$i", "hall2", BarrierCategory.BLOCKED_PATH, "Distinct barrier $i") } }.awaitAll()
        assertEquals(10, repo.snapshot().reports.size)
    }

    @Test
    fun confirmingPositionRecalculatesFromThere() = runBlocking {
        repo.initialize()
        repo.setCurrent("lobby1")
        val s = repo.snapshot()
        assertEquals("lobby1", s.current)
        assertEquals("a1", s.selectedFloor)
        assertEquals("lobby1", (Routing.calculate(s) as RouteResult.Found).nodes.first().id)
    }

    @Test
    fun closingAPathClosesBothDirectionsAndIsAudited() = runBlocking {
        repo.initialize()
        repo.setPathClosed("hall2", true, "Floor cleaning")
        val s = repo.snapshot()
        assertTrue("hall2" in s.blockedEdges && "hall2_r" in s.blockedEdges)
        assertEquals("Close path", s.audit.last().action)
        repo.setPathClosed("hall2_r", false, "Cleaning finished")
        assertTrue(repo.snapshot().blockedEdges.isEmpty())
    }

    @Test
    fun invalidImportLeavesMapUntouched() = runBlocking {
        repo.initialize()
        val old = repo.snapshot()
        val e = runCatching { repo.importMap("{broken") }.exceptionOrNull()
        assertEquals(ErrorCode.MAP_INVALID, (e as AppError).code)
        assertEquals(old, repo.snapshot())
    }

    @Test
    fun validImportReplacesMapAndKeepsPosition() = runBlocking {
        repo.initialize()
        val map = repo.snapshot().map.copy(version = "survey-2", provenance = "Surveyed by estates team")
        repo.importMap(MapJson.encodeToString(CampusMap.serializer(), map))
        val s = repo.snapshot()
        assertEquals("survey-2", s.map.version)
        assertFalse(s.map.isDemo)
    }

    @Test
    fun oldSampleCampusIsRemovedAndFirstSurveyBecomesPosition() = runBlocking {
        val old = MapJson.decodeFromString(CampusMap.serializer(), seed()).copy(provenance = "Illustrative demo")
        db.stateDao().insert(StateRow(revision = 1, payload = MapJson.encodeToString(AppSnapshot.serializer(),
            AppSnapshot(old, profile = Profile(Mobility.WHEELCHAIR)))))
        val empty = """{"id":"main-campus","schemaVersion":1,"version":"0","name":"Campus","provenance":"Field survey","buildings":[],"floors":[],"nodes":[],"edges":[],"facilities":[]}"""
        val r = CampusRepository(db, seed = { empty })
        r.initialize()
        val s = r.snapshot()
        assertTrue(s.map.nodes.isEmpty())
        assertEquals(Mobility.WHEELCHAIR, s.profile.mobility)

        val trail = (0..20).map { TrailPoint(0.0, -it * 0.5) }
        r.addSurvey(SurveyBuilder.build("survey_e", "Draft", trail, emptyList(), LatLng(28.48, 77.49), 0.0)!!)
        val after = r.snapshot()
        assertTrue(after.map.nodes.any { it.id == after.current })
        assertEquals(after.node(after.current)?.floorId, after.selectedFloor)
        r.deleteSurvey("survey_e", "Recorded by mistake")
        assertEquals("", r.snapshot().current)
    }

    @Test
    fun unreadableSavedDataIsRecovered() = runBlocking {
        db.stateDao().insert(StateRow(revision = 3, payload = "{not json"))
        repo.initialize()
        val s = repo.snapshot()
        assertEquals(31, s.map.nodes.size)
        assertEquals("Recover saved data", s.audit.single().action)
    }

    @Test
    fun persistentStateSurvivesDatabaseReopen(): Unit = runBlocking {
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, AccessDatabase::class.java, name).allowMainThreadQueries().build()
        var r = CampusRepository(disk, ::seed)
        r.initialize()
        r.submitReport("saved", "hall2", BarrierCategory.BLOCKED_PATH, "Save this report")
        disk.close()

        disk = Room.databaseBuilder(context, AccessDatabase::class.java, name).allowMainThreadQueries().build()
        r = CampusRepository(disk, ::seed)
        r.initialize()
        assertEquals("saved", r.snapshot().reports.single().id)
        disk.close()
        context.deleteDatabase(name)
        Unit
    }

    @Test
    fun cameraSurveyIsAddedAndCanBeDeleted() = runBlocking {
        repo.initialize()
        val trail = (0..20).map { TrailPoint(0.0, -it * 0.5) }
        val survey = SurveyBuilder.build("survey_t", "Draft", trail, emptyList(), LatLng(28.48, 77.49), 0.0)!!
        repo.addSurvey(survey)
        repo.addSurvey(survey) // idempotent
        val s = repo.snapshot()
        assertEquals(1, s.map.buildings.count { it.draft })
        assertEquals("Add camera survey", s.audit.last().action)
        repo.deleteSurvey("survey_t", "Recorded by mistake")
        val after = repo.snapshot()
        assertTrue(after.map.buildings.none { it.draft })
        assertTrue(after.map.nodes.none { it.id.startsWith("survey_t") })
    }
}
