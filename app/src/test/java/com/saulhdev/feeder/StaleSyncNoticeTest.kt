/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.saulhdev.feeder.manager.sync.SyncNoticeCheck
import com.saulhdev.feeder.manager.sync.clearStaleSyncNotice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A "Syncing" notice left up after its sync ended, as on 13 October 2026:
 * the 08:58 sync finished at 09:01 and the notice was still there at 09:45.
 * And on 17 October, from 10:54 to past 12:06, with only the panel opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StaleSyncNoticeTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** FeedSyncer's syncNotificationId. */
    private val syncNoticeId = 42624

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        manager.createNotificationChannel(NotificationChannel("t", "t", NotificationManager.IMPORTANCE_LOW))
    }

    private fun post(id: Int) = manager.notify(
        id,
        Notification.Builder(context, "t").setContentTitle("Syncing").setSmallIcon(android.R.drawable.stat_notify_sync).build(),
    )

    @Test
    fun `a notice with no sync behind it is taken down, and nothing else is`() {
        post(syncNoticeId)
        post(42625) // the watchdog's own "feeds have stopped updating"
        assertTrue(runBlocking { clearStaleSyncNotice(context) })
        assertEquals(listOf(42625), manager.activeNotifications.map { it.id })
    }

    @Test
    fun `with no notice up there is nothing to do`() {
        assertFalse(runBlocking { clearStaleSyncNotice(context) })
    }

    @Test
    fun `a running sync keeps its notice, and both checks are wired in`() {
        val code = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        val body = code.substring(code.indexOf("suspend fun clearStaleSyncNotice("))
        assertTrue(body.substring(0, body.indexOf("\n}\n")).contains("if (running) return false"))
        assertTrue(File("src/main/java/com/saulhdev/feeder/MainActivity.kt").readText()
            .contains("clearStaleSyncNotice(applicationContext)"))
        assertTrue(File("src/main/java/com/saulhdev/feeder/manager/sync/SyncWatchdog.kt").readText()
            .contains("runCatching { clearStaleSyncNotice(applicationContext) }"))
    }

    @Test
    fun `the check a minute after a sync takes a left-over notice down`() {
        post(syncNoticeId)
        val worker = TestListenableWorkerBuilder<SyncNoticeCheck>(context).build()
        assertEquals(ListenableWorker.Result.success(), runBlocking { worker.doWork() })
        assertTrue(manager.activeNotifications.none { it.id == syncNoticeId })
    }

    @Test
    fun `a run of syncs leaves one check, waiting its minute`() {
        SyncNoticeCheck.schedule(context)
        SyncNoticeCheck.schedule(context)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("sync_notice_check").get()
        assertEquals(1, infos.count { it.state == WorkInfo.State.ENQUEUED })
        assertEquals(60L, SyncNoticeCheck.DELAY_SECONDS)
    }

    @Test
    fun `every sync in the foreground queues the check, and the panel looks too`() {
        val syncer = File("src/main/java/com/saulhdev/feeder/manager/sync/FeedSyncer.kt").readText()
        val doWork = syncer.substring(syncer.indexOf("override suspend fun doWork()"))
        assertTrue(doWork.substring(0, doWork.indexOf("private suspend fun sync()"))
            .contains("finally {\n        if (inForeground) SyncNoticeCheck.schedule(applicationContext)"))
        assertTrue(File("src/main/java/com/saulhdev/feeder/manager/service/OverlayView.kt").readText()
            .contains("runCatching { clearStaleSyncNotice(context.applicationContext) }"))
    }

}
