package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.saulhdev.feeder.utils.SyncLog
import java.io.File

/**
 * One download per feed per need, and a sync the reader started finishes.
 *
 * All three from one afternoon's report: plugging in released two syncs that
 * together fetched all 120 feeds twice; a scheduled sync that had just started
 * was cancelled by the app re-applying an unchanged schedule; and every pull
 * to refresh lost its network as soon as Whisper left the screen.
 */
class SyncEfficiencyTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()
    private val syncer = source("manager/sync/FeedSyncer.kt")
    private val sync = source("manager/sync/RssLocalSync.kt")
    private val activity = source("MainActivity.kt")

    @Test
    fun `a request that does not say force is not forced`() {
        // The scheduled sync's request carries no force_network key, and the
        // default of true made every scheduled sync refetch every feed.
        assertTrue(syncer.contains("inputData.getBoolean(\"force_network\", false)"))
        assertFalse(syncer.contains("inputData.getBoolean(\"force_network\", true)"))
    }

    @Test
    fun `a forced sync that queued only fetches what is older than its request`() {
        val outer = sync.substringAfter("suspend fun syncFeeds(").substringBefore("private const val QUEUED_AFTER_MS")
        val asked = outer.indexOf("val requestedAt")
        val locked = outer.indexOf("syncMutex.withLock")
        assertTrue("the time of asking is taken before the wait", asked in 0 until locked)
        assertTrue(outer.contains("freshSince = if (forceNetwork && waited) requestedAt else null"))

        assertTrue("freshSince decides staleness first", sync.contains("freshSince != null -> freshSince"))
        assertTrue(
            "and selection then goes by staleness, not by force",
            sync.contains("forceNetwork = forceNetwork && freshSince == null"),
        )
    }

    @Test
    fun `an unchanged schedule is left alone`() {
        val configure = activity.substringAfter("private fun configurePeriodicSync()")
            .substringBefore("private fun scheduleMatches")
        val check = configure.indexOf("if (scheduleMatches(")
        assertTrue("checked", check > 0)
        assertTrue(
            "before anything is cancelled",
            check < configure.indexOf("workManager.cancelUniqueWork(AUTOMATIC_SYNC_WORK)\n\n        val syncWork"),
        )
        assertTrue("or enqueued", check < configure.indexOf("enqueueUniquePeriodicWork("))
    }

    @Test
    fun `a pull runs in the foreground, silently`() {
        val worker = syncer.substringAfter("override suspend fun doWork()")
        assertTrue(
            "the reader's own syncs, wherever they start; the rest only when begun on screen",
            worker.contains("val foreground = runInForeground(origin, dataBlocked, onScreen)") &&
                SyncLog.ASKED_ORIGINS.all { com.saulhdev.feeder.manager.sync.runInForeground(it, dataBlocked = false, onScreen = false) } &&
                SyncLog.AUTOMATIC_ORIGINS.none { com.saulhdev.feeder.manager.sync.runInForeground(it, dataBlocked = false, onScreen = false) },
        )
        assertTrue(worker.contains("setForeground(getForegroundInfo())"))
        assertTrue("before the sync, while the app is still on screen", worker.indexOf("setForeground(") < worker.indexOf("syncFeeds("))
        assertTrue("no sound, no pop-up", syncer.contains(".setSilent(true)"))

        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue("Android 14 refuses the task without it", manifest.contains("android.permission.FOREGROUND_SERVICE_DATA_SYNC"))
        assertTrue("Android 13 hides the line without it", manifest.contains("android.permission.POST_NOTIFICATIONS"))
    }

    @Test
    fun `the record says when a run queued or ran in the foreground`() {
        assertTrue(syncer.contains("val queued = syncMutex.isLocked"))
        assertTrue(syncer.contains("if (queued) \"queued\" else null"))
        assertTrue(syncer.contains("if (inForeground) \"foreground\" else null"))
    }
}
