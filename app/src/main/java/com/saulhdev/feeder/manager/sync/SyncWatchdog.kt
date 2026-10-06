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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saulhdev.feeder.MainActivity
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.data.repository.FAILURES_BEFORE_BROKEN
import com.saulhdev.feeder.ui.navigation.Routes
import com.saulhdev.feeder.utils.StuckReason
import com.saulhdev.feeder.utils.SyncProblem
import com.saulhdev.feeder.utils.articleAge
import com.saulhdev.feeder.utils.format
import com.saulhdev.feeder.utils.isPowerSaveMode
import com.saulhdev.feeder.utils.isUnmetered
import com.saulhdev.feeder.utils.mayRenotifyStuck
import com.saulhdev.feeder.utils.powerState
import com.saulhdev.feeder.utils.sourcesAreNews
import com.saulhdev.feeder.utils.stuckReasons
import com.saulhdev.feeder.utils.syncOverdueAfterMs
import com.saulhdev.feeder.utils.syncProblem
import org.koin.java.KoinJavaComponent.inject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Looks in every few hours and speaks up only when syncing has stopped working.
 *
 * Its own work, with no constraints, because the thing it watches for is the
 * scheduled sync never getting its constraints: a check that also waited for
 * a charger would be stuck in exactly the case it exists to report.
 */
class SyncWatchdog(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val sources: SourcesRepository by inject(SourcesRepository::class.java)
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    override suspend fun doWork(): Result {
        check(applicationContext, sources.getAllSources(), prefs)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "sync_watchdog"
        private const val CHECK_EVERY_HOURS = 6L

        private const val CHANNEL_ID = "syncProblems"
        private const val STUCK_NOTIFICATION_ID = 42625
        private const val SOURCES_NOTIFICATION_ID = 42626

        private const val STATE = "sync_problems"
        private const val KEY_STUCK_AT = "stuck_notified_at"
        private const val KEY_SOURCES_COUNT = "sources_notified_count"

        /**
         * KEEP, so opening the app does not push the next check back by
         * another six hours each time.
         */
        fun schedule(workManager: WorkManager) {
            val work = PeriodicWorkRequestBuilder<SyncWatchdog>(CHECK_EVERY_HOURS, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        }

        /**
         * Takes a stuck-sync notice back once a sync has landed.
         *
         * Called by every sync that finishes well, so the notice does not
         * sit in the shade for hours after the problem ended — but only
         * cleared if the feed really is current again, since a sync can
         * finish "ok" having reached none of its sources.
         */
        suspend fun clearIfCurrent(context: Context, sources: SourcesRepository, prefs: FeedPreferences) {
            val newest = newestSync(sources.getAllSources()) ?: return
            val overdue = syncOverdueAfterMs(prefs.syncFrequency.getValue())
            if (System.currentTimeMillis() - newest <= overdue) clearStuck(context)
        }

        /**
         * One line for the report: whether a notice could be shown, when the
         * check next looks, and what it last said.
         */
        suspend fun describe(context: Context): String {
            val clock = SimpleDateFormat("MM-dd HH:mm", Locale.US)
            val work = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(WORK_NAME).first().firstOrNull()
            val next = when {
                work == null -> "not scheduled"
                work.nextScheduleTimeMillis == Long.MAX_VALUE -> "${work.state}"
                else -> "next check ${clock.format(Date(work.nextScheduleTimeMillis))}"
            }
            val state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            val stuckAt = state.getLong(KEY_STUCK_AT, 0L)
            val stuck = if (stuckAt > 0L) "stuck notice ${clock.format(Date(stuckAt))}" else "no stuck notice"
            val reported = state.getInt(KEY_SOURCES_COUNT, 0)
            return "notifications ${if (canNotify(context)) "allowed" else "off"}, $next, $stuck, " +
                "stopped sources reported $reported"
        }

        private fun newestSync(feeds: List<Feed>): Long? =
            feeds.maxOfOrNull { it.lastSync.toEpochMilliseconds() }

        private fun clearStuck(context: Context) {
            val state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
            if (state.getLong(KEY_STUCK_AT, 0L) == 0L) return
            state.edit { putLong(KEY_STUCK_AT, 0L) }
            NotificationManagerCompat.from(context).cancel(STUCK_NOTIFICATION_ID)
        }

        private fun check(context: Context, feeds: List<Feed>, prefs: FeedPreferences) {
            val now = System.currentTimeMillis()
            // Counted exactly as "Feeds that stopped working" lists them, which
            // is where the notice opens: a count that included feeds the page
            // leaves out would send somebody looking for sources not there.
            val stopped = feeds.count {
                it.isEnabled && it.consecutiveFailures >= FAILURES_BEFORE_BROKEN
            }
            val newest = newestSync(feeds)
            val problem = syncProblem(
                nowMs = now,
                newestSyncMs = newest,
                overdueAfterMs = syncOverdueAfterMs(prefs.syncFrequency.getValue()),
                stoppedSources = stopped,
            )
            val state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)

            if (problem != SyncProblem.SyncStuck) clearStuck(context)
            // Fewer stopped than last reported lowers the mark, so a source
            // that stops after others were fixed is news again.
            val reportedSources = state.getInt(KEY_SOURCES_COUNT, 0)
            if (stopped < reportedSources) state.edit { putInt(KEY_SOURCES_COUNT, stopped) }

            if (!canNotify(context)) return
            when (problem) {
                SyncProblem.None -> Unit
                SyncProblem.SyncStuck -> {
                    if (!mayRenotifyStuck(now, state.getLong(KEY_STUCK_AT, 0L))) return
                    val age = articleAge(now, newest ?: now).format(context)
                    notify(
                        context,
                        STUCK_NOTIFICATION_ID,
                        title = context.getString(R.string.sync_stuck_title, age),
                        text = stuckText(context, prefs),
                        tap = Intent(context, MainActivity::class.java),
                    )
                    state.edit { putLong(KEY_STUCK_AT, now) }
                }
                SyncProblem.SourcesStuck -> {
                    if (!sourcesAreNews(stopped, state.getInt(KEY_SOURCES_COUNT, 0))) return
                    notify(
                        context,
                        SOURCES_NOTIFICATION_ID,
                        title = context.resources.getQuantityString(
                            R.plurals.sources_stuck_title, stopped, stopped,
                        ),
                        text = context.getString(R.string.sources_stuck_text),
                        tap = MainActivity.navigateIntent(context, Routes.BROKEN_FEEDS),
                    )
                    state.edit { putInt(KEY_SOURCES_COUNT, stopped) }
                }
            }
        }

        /**
         * The stuck-sync notice, now, so it can be seen without waiting three
         * hours for a real one.
         *
         * The same builder, the same channel, the same reasons read from the
         * phone as it is, and the same tap: what this shows is what the real
         * one would. Only the title is marked as a test, and the age is a
         * stand-in, since the feed has probably just updated.
         *
         * Nothing is recorded. A test must not count as having told the
         * reader, or the real notice would be held back for a day afterwards.
         *
         * False when notifications are off, so the button can say why nothing
         * appeared instead of appearing to do nothing.
         */
        fun sendTest(context: Context, prefs: FeedPreferences): Boolean {
            if (!canNotify(context)) return false
            val now = System.currentTimeMillis()
            val age = articleAge(now, now - TEST_AGE_MS).format(context)
            notify(
                context,
                STUCK_NOTIFICATION_ID,
                title = context.getString(
                    R.string.sync_stuck_test_title,
                    context.getString(R.string.sync_stuck_title, age),
                ),
                text = stuckText(context, prefs),
                tap = Intent(context, MainActivity::class.java),
            )
            return true
        }

        /** Three hours: the age at which an hourly schedule first counts as stuck. */
        private const val TEST_AGE_MS = 3 * 60 * 60 * 1000L

        /** Why it is stuck, as far as the phone can tell right now, and what to do. */
        private fun stuckText(context: Context, prefs: FeedPreferences): String {
            val power = powerState(context)
            val reasons = stuckReasons(
                requiresCharging = prefs.syncOnlyWhenCharging.getValue(),
                pluggedIn = power.pluggedIn,
                wifiOnly = prefs.syncOnlyOnWifi.getValue(),
                unmetered = isUnmetered(context),
                batterySaver = isPowerSaveMode(context),
                batteryLow = power.low,
            )
            return (reasons.map { context.getString(it.text) } +
                context.getString(R.string.sync_stuck_hint)).joinToString(" ")
        }

        private val StuckReason.text: Int
            get() = when (this) {
                StuckReason.Charger -> R.string.sync_stuck_charger
                StuckReason.WiFi -> R.string.sync_stuck_wifi
                StuckReason.BatterySaver -> R.string.sync_stuck_battery_saver
                StuckReason.MoreBattery -> R.string.sync_stuck_more_battery
                StuckReason.Unknown -> R.string.sync_stuck_unknown
            }

        /** Switched off, or never allowed: say nothing, and ask nothing. */
        private fun canNotify(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }

        private fun notify(context: Context, id: Int, title: String, text: String, tap: Intent) {
            val manager = NotificationManagerCompat.from(context)
            // Its own channel, apart from the silent "Syncing" one, so it can
            // be heard - and switched off by itself without losing the other.
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.sync_problems_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.sync_problems_channel_description) }
            )
            val pending = PendingIntent.getActivity(
                context,
                id,
                tap.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            if (canNotify(context)) {
                try {
                    manager.notify(id, notification)
                } catch (_: SecurityException) {
                    // Permission withdrawn between the check and the call.
                }
            }
        }
    }
}
