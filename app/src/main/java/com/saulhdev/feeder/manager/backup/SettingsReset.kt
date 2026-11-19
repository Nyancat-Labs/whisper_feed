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
package com.saulhdev.feeder.manager.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit

/**
 * Every setting back to how Whisper comes (sheet 68, after Harmonic 3.1).
 *
 * For someone who has changed several things and can't find their way back,
 * and as an easy first step in support. A setting goes back to its default by
 * being removed: every preference reads its default when nothing is stored.
 *
 * What stays is what isn't a setting: sources and how they're arranged,
 * pinned and hidden; what Whisper has learned; the place for the weather
 * row; the backup folder and this phone's own records; the choices about
 * cloud and device backup, which a reset must not switch on unasked; and the
 * welcome and tour, which have been seen. The sync account lives elsewhere
 * and isn't touched.
 */
object SettingsReset {

    val KEEP: Set<String> = SettingsBackup.NOT_PORTABLE + setOf(
        "pref_onboarding_seen",
        "pref_tour_seen",
        "pref_full_text_purged",
        "pref_learned_reset_at",
        "pref_source_affinity",
        "pref_favourite_sources",
        "pref_hidden_sources",
        "pref_category_order",
        "pref_glance_place_name",
        "pref_glance_place_coords",
        "pref_platform_backup_cloud",
        "pref_platform_backup_device",
        "pref_account",
    )

    /** Of the stored [names], the ones a reset removes. */
    fun toReset(names: Collection<String>): Set<String> = names.filterTo(mutableSetOf()) { it !in KEEP }

    /** Resets, and returns how many settings were changed from the default. */
    suspend fun reset(dataStore: DataStore<Preferences>): Int {
        var removed = 0
        dataStore.edit { prefs ->
            val gone = prefs.asMap().keys.filter { it.name !in KEEP }
            gone.forEach { prefs.remove(it) }
            removed = gone.size
        }
        return removed
    }
}
