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
package com.saulhdev.feeder.manager.glance

import java.time.ZoneId
import java.time.Instant
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** What the glance row shows. Any field may be absent. */
data class GlanceState(
    val enabled: Boolean = false,
    val placeName: String = "",
    val weather: GlanceWeather? = null,
    val readToday: Int = 0,
)

/**
 * Backs the strip of chips above the feed.
 *
 * The two counts come from the database and update themselves. Weather does
 * not: it is a network call, so it is fetched at most once every [REFRESH_MS]
 * and held in memory, and a failure leaves the previous value on screen rather
 * than blanking the chip. Nothing is fetched at all unless the row is enabled
 * and a place has been chosen, so the default install makes no network request
 * for this feature.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GlanceStateHolder(
    private val prefs: FeedPreferences,
    private val articleRepo: ArticleRepository,
    private val weatherRepo: WeatherRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val weather = MutableStateFlow<GlanceWeather?>(null)

    /**
     * Midnight, as a value that can change.
     *
     * Held in a flow rather than captured once, because the process outlives a
     * day: a phone left overnight would otherwise keep counting from the
     * previous midnight and report yesterday's reading as today's. Re-evaluated
     * whenever the feed is opened, which is the only time the count is looked
     * at anyway.
     */
    private val dayStart = MutableStateFlow(startOfToday())

    val state: StateFlow<GlanceState> = combine(
        prefs.glanceEnabled.get(),
        prefs.glancePlaceName.get(),
        // Read on this phone, not everything marked read since midnight: a
        // FreshRSS sync or one bulk Mark as read added hundreds (review 14).
        dayStart.flatMapLatest {
            articleRepo.readOnPhone(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())
        }.distinctUntilChanged(),
        weather,
    ) { enabled, placeName, readToday, currentWeather ->
        GlanceState(
            enabled = enabled,
            placeName = placeName,
            weather = currentWeather,
            readToday = readToday,
        )
    }.stateIn(scope, SharingStarted.Eagerly, GlanceState())

    /**
     * Fetches weather if it is enabled, a place is set, and what we have is
     * stale. Safe to call on every feed open — it is a no-op most of the time.
     */
    fun refreshIfStale() {
        dayStart.value = startOfToday()
        scope.launch {
            if (!prefs.glanceEnabled.getValue()) return@launch
            val coords = prefs.glancePlaceCoords.getValue()
            val name = prefs.glancePlaceName.getValue()
            if (coords.isBlank() || name.isBlank()) return@launch

            val existing = weather.value
            if (existing != null && System.currentTimeMillis() - existing.fetchedAt < REFRESH_MS) {
                return@launch
            }

            val (lat, lon) = coords.split(",").let {
                if (it.size != 2) return@launch
                (it[0].toDoubleOrNull() ?: return@launch) to (it[1].toDoubleOrNull() ?: return@launch)
            }
            // A failed fetch leaves the last good reading in place; a stale
            // temperature is more useful than a chip that vanishes.
            weatherRepo.fetch(GlancePlace(name, lat, lon))?.let { weather.value = it }
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        /** Weather changes slowly and the chip is glanceable, not a forecast app. */
        private const val REFRESH_MS = 30 * 60 * 1000L
    }
}
