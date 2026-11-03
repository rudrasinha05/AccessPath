package com.accesspath

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private fun seed() = context.assets.open("maps/demo-campus.json").bufferedReader().use { it.readText() }

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AccessDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CampusRepository(db, ::seed)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun reportReviewAndAuditAreAtomic() = runBlocking {
        repo.initialize()
        repo.submit("r", "hall2", "Blocked path", "Construction debris")
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
        repeat(2) { repo.submit("r", "hall2", "Blocked path", "Construction debris") }
        assertEquals(1, repo.snapshot().reports.size)
    }

    @Test
    fun staleUpdateIsRejected() = runBlocking {
        repo.initialize()
        val rev = repo.snapshot().revision
        repo.facility("liftA", Availability.UNAVAILABLE, "Maintenance", rev)
        try {
            repo.facility("liftB", Availability.UNAVAILABLE, "Maintenance", rev)
            fail("Stale revision accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(Availability.AVAILABLE, repo.snapshot().map.facilities.first { it.id == "liftB" }.availability)
    }

    @Test
    fun parallelReportsDoNotLoseUpdates() = runBlocking {
        repo.initialize()
        (1..10).map { i -> async(Dispatchers.IO) { repo.submit("r$i", "hall2", "Blocked path", "Distinct barrier $i") } }.awaitAll()
        assertEquals(10, repo.snapshot().reports.size)
    }

    @Test
    fun invalidImportLeavesMapUntouched() = runBlocking {
        repo.initialize()
        val old = repo.snapshot()
        try {
            repo.importMap("{broken")
            fail("Invalid map accepted")
        } catch (_: Exception) {}
        assertEquals(old, repo.snapshot())
    }

    @Test
    fun resetRestoresDemo() = runBlocking {
        repo.initialize()
        repo.allLifts(false)
        repo.submit("r", "hall2", "Blocked path", "Construction debris")
        repo.resetDemo()
        val s = repo.snapshot()
        assertTrue(s.reports.isEmpty())
        assertTrue(Routing.calculate(s) is RouteResult.Found)
    }

    @Test
    fun persistentStateSurvivesDatabaseReopen(): Unit = runBlocking {
        val name = "persistence-test.db"
        context.deleteDatabase(name)
        var disk = Room.databaseBuilder(context, AccessDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        var r = CampusRepository(disk, ::seed)
        r.initialize()
        r.submit("saved", "hall2", "Blocked path", "Save this report")
        disk.close()

        disk = Room.databaseBuilder(context, AccessDatabase::class.java, name)
            .allowMainThreadQueries()
            .build()
        r = CampusRepository(disk, ::seed)
        r.initialize()
        assertEquals("saved", r.snapshot().reports.single().id)
        disk.close()
        context.deleteDatabase(name)
    }
}
