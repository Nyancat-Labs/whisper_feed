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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.saulhdev.feeder.manager.models.enqueueUnlessWaiting
import com.saulhdev.feeder.manager.models.worthAnotherRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * However many saves or syncs ask for the full-text download, there is never
 * more than one run waiting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WorkQueueTest {

    class Nothing(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork() = Result.success()
    }

    private lateinit var workManager: WorkManager

    @Before
    fun start() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    /** A run that waits for Wi-Fi, as the downloads do with full text off on mobile data. */
    private fun waitingForWifi(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<Nothing>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
        .build()

    private fun runs(name: String) = workManager.getWorkInfosForUniqueWork(name).get()

    @Test
    fun `ten saves on mobile data leave one run waiting, not ten`() {
        repeat(10) { enqueueUnlessWaiting(workManager, "saved", waitingForWifi()) }
        val runs = runs("saved")
        assertEquals(runs.map { it.state }.toString(), 1, runs.size)
        assertEquals(WorkInfo.State.ENQUEUED, runs.single().state)
    }

    @Test
    fun `a finished run is followed by a new one`() {
        enqueueUnlessWaiting(workManager, "saved", OneTimeWorkRequestBuilder<Nothing>().build())
        val first = runs("saved").single()
        WorkManagerTestInitHelper.getTestDriver(ApplicationProvider.getApplicationContext())!!
            .setAllConstraintsMet(first.id)
        assertEquals(WorkInfo.State.SUCCEEDED, workManager.getWorkInfoById(first.id).get()!!.state)

        enqueueUnlessWaiting(workManager, "saved", waitingForWifi())
        assertTrue(runs("saved").any { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test
    fun `only a run already waiting stops another being added`() {
        assertFalse(worthAnotherRun(listOf(WorkInfo.State.ENQUEUED)))
        assertFalse(worthAnotherRun(listOf(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)))
        // One running is appended behind, so neither it nor the new save is lost.
        assertTrue(worthAnotherRun(listOf(WorkInfo.State.RUNNING)))
        assertTrue(worthAnotherRun(listOf(WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED)))
        assertTrue(worthAnotherRun(emptyList()))
    }
}
