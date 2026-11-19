/*
 * This file is part of Whisper.
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
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.saulhdev.feeder.MainActivity
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.navigation.Routes
import com.saulhdev.feeder.ui.overlay.StoryCluster
import com.saulhdev.feeder.ui.overlay.clusterStories

/**
 * A notification when a background sync finds a new breaking story (asked by a
 * user, 1 November; decided 5 November, sheet 80).
 *
 * Off unless the reader turns it on. The same rule as the breaking-news card:
 * several News sources on one story within a few hours. One notification per
 * story, never again for any article already told about, and at most
 * [DAILY_CAP] a day, so it cannot nag. It comes with the background sync, not
 * the instant a story breaks, and the setting says so.
 */
object BreakingAlert {

    const val DAILY_CAP = 3
    private const val CHANNEL_ID = "breakingNews"
    private const val STATE = "breaking_alert"
    private const val KEY_TOLD = "told"
    private const val KEY_DAY = "day"
    private const val KEY_COUNT = "count"

    /** A story to tell about: its lead article and how many sources cover it. */
    data class Story(val lead: FeedItem, val sources: Int)

    /**
     * The stories not told about yet, newest lead first, within what is left
     * of today's allowance. A story counts as told if any of its articles was
     * in one already, so a story that gains a source is not news twice.
     */
    fun newStories(
        items: List<FeedItem>,
        clusters: Map<String, StoryCluster>,
        told: Set<String>,
        allowance: Int,
    ): List<Story> {
        if (allowance <= 0) return emptyList()
        val byId = items.associateBy { it.id }
        return clusters.entries
            .groupBy({ it.value.leadId }, { it.key })
            .filter { (_, members) -> members.none { it in told } }
            .mapNotNull { (leadId, _) ->
                val lead = byId[leadId] ?: return@mapNotNull null
                Story(lead, clusters.getValue(leadId).sources)
            }
            .sortedByDescending { it.lead.timeMillis }
            .take(allowance)
    }

    /** Called after a successful sync; does nothing unless switched on and allowed. */
    fun check(context: Context, enabled: Boolean, items: List<FeedItem>, nowMs: Long = System.currentTimeMillis()) {
        if (!enabled) {
            reset(context)
            return
        }
        if (!canNotify(context)) return
        val clusters = clusterStories(items, nowMs)
        val state = context.getSharedPreferences(STATE, Context.MODE_PRIVATE)
        // The first run after switching on only notes what is already there:
        // turning it on at noon should not announce the morning's news.
        if (!state.contains(KEY_TOLD)) {
            state.edit { putStringSet(KEY_TOLD, clusters.keys) }
            return
        }
        if (clusters.isEmpty()) return
        val today = nowMs / DAY_MS
        val count = if (state.getLong(KEY_DAY, -1) == today) state.getInt(KEY_COUNT, 0) else 0
        val told = state.getStringSet(KEY_TOLD, emptySet()).orEmpty()
        val stories = newStories(items, clusters, told, DAILY_CAP - count)
        stories.forEach { post(context, it) }
        // Every article now in a story counts as told, whether or not today's
        // allowance reached it: tomorrow, yesterday's story is not news. Only
        // what is still clustered is kept, so the set does not grow for ever.
        state.edit {
            putStringSet(KEY_TOLD, clusters.keys)
            putLong(KEY_DAY, today)
            putInt(KEY_COUNT, count + stories.size)
        }
    }

    /** Switching off forgets, so switching on again starts quietly. */
    fun reset(context: Context) {
        context.getSharedPreferences(STATE, Context.MODE_PRIVATE).edit { clear() }
    }

    private fun post(context: Context, story: Story) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.breaking_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.breaking_channel_description) }
        )
        val id = story.lead.id.hashCode()
        val tap = MainActivity.navigateIntent(context, "${Routes.ARTICLE_VIEW}/${story.lead.id}")
        val pending = PendingIntent.getActivity(
            context, id, tap.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(R.string.breaking_notification_text, story.sources, story.lead.feedTitle)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(story.lead.displayTitle)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission withdrawn between the check and the call.
        }
    }

    private fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
