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
package com.saulhdev.feeder.ui.overlay

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.AgeUnit
import com.saulhdev.feeder.utils.SyncFreshness
import com.saulhdev.feeder.utils.format
import com.saulhdev.feeder.utils.msUntilNextMinute
import com.saulhdev.feeder.utils.syncFreshness
import com.saulhdev.feeder.utils.syncOverdueAfterMs
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

/**
 * "Updated 12m ago", under the chips in both feeds.
 *
 * A background sync is silent on purpose, so this is where somebody finds out
 * whether one happened. It says "Updating…" while any sync runs, and turns to
 * the error colour with a pull-down hint once the gap is longer than the
 * schedule can explain — see [syncOverdueAfterMs].
 *
 * Reads the repository itself, as [ActiveFilterBar] does, so the app and the
 * launcher panel draw the same line from the same source without either
 * screen having to carry it through.
 */
@Composable
fun SyncFreshnessLine(modifier: Modifier = Modifier) = SyncFreshnessText(
    modifier = modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp),
)

/**
 * The same words without a line of their own, for drawing beside something
 * else: the app puts them at the right of the first day heading.
 */
@Composable
fun SyncFreshnessText(modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    val sources: SourcesRepository = koinInject()
    val prefs: FeedPreferences = koinInject()
    val context = LocalContext.current

    val newestSync by sources.newestSync.collectAsState(initial = null)
    val syncing by sources.isSyncing.collectAsState()
    val frequency by prefs.syncFrequency.get().collectAsState(initial = "1")

    // Ticks on the minute, and restarts whenever a sync starts or lands so
    // the line never shows an age measured from a stale clock.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(newestSync, syncing) {
        while (true) {
            now = System.currentTimeMillis()
            delay(msUntilNextMinute(now))
        }
    }

    val freshness = syncFreshness(
        nowMs = now,
        lastSyncMs = newestSync,
        syncing = syncing,
        overdueAfterMs = syncOverdueAfterMs(frequency),
    ) ?: return

    val text = when (freshness) {
        SyncFreshness.Updating -> context.getString(R.string.freshness_updating)
        SyncFreshness.NeverUpdated -> context.getString(R.string.freshness_never)
        is SyncFreshness.Updated -> when {
            freshness.overdue ->
                context.getString(R.string.freshness_overdue, freshness.age.format(context))
            freshness.age.unit == AgeUnit.NOW ->
                context.getString(R.string.freshness_just_now)
            else ->
                context.getString(R.string.freshness_updated, freshness.age.format(context))
        }
    }
    val late = freshness == SyncFreshness.NeverUpdated ||
        (freshness is SyncFreshness.Updated && freshness.overdue)

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (late) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}
