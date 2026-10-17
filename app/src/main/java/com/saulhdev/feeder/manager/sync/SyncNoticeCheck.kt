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
package com.saulhdev.feeder.manager.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * A look for a left-over "Syncing" notice, a minute after a sync in the
 * foreground ends. See FeedSyncer.doWork and clearStaleSyncNotice.
 *
 * No constraints: it needs no network, and the case it is for is a phone
 * that has just lost one. Asleep, Android may hold it to the next
 * maintenance window, which is still minutes rather than the watchdog's
 * hours. Not tagged as a FeedSyncer, so it never counts as a sync running.
 */
class SyncNoticeCheck(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runCatching { clearStaleSyncNotice(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "sync_notice_check"
        const val DELAY_SECONDS = 60L

        /**
         * REPLACE, so a run of syncs leaves one check, a minute after the last.
         * A sync that starts in that minute keeps its notice, and queues its
         * own check when it ends.
         */
        fun schedule(context: Context) {
            val work = OneTimeWorkRequestBuilder<SyncNoticeCheck>()
                .setInitialDelay(DELAY_SECONDS, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, work)
        }
    }
}
