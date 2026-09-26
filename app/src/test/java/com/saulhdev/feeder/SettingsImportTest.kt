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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.backup.SettingsBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Modifier

/**
 * A settings file puts back only what this version of the app declares, and
 * only as the type it declares it.
 *
 * Against a real preference store, because the failure being prevented only
 * happens there: a value stored under the right name with the wrong type
 * makes the next read of that setting throw, at start, on every start.
 */
class SettingsImportTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var stores = 0

    @After
    fun done() = scope.cancel()

    private fun store(): DataStore<Preferences> {
        val file = folder.root.resolve("settings${stores++}.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun entry(key: String, type: String, value: Any): JsonObject = buildJsonObject {
        put("key", key)
        put("type", type)
        when (value) {
            is Boolean -> put("value", value)
            is Number -> put("value", value)
            is List<*> -> put("value", JsonArray(value.map { JsonPrimitive(it.toString()) }))
            else -> put("value", value.toString())
        }
    }

    private fun document(vararg entries: JsonObject): String = buildJsonObject {
        put("version", 1)
        put("app", "whisper")
        put("settings", buildJsonArray { entries.forEach { add(it) } })
    }.toString()

    @Test
    fun `a setting in its own type is put back`() = runBlocking {
        val store = store()
        val applied = SettingsBackup.import(store, document(entry("pref_sync_frequency", "string", "2")))
        assertEquals(1, applied)
        assertEquals("2", store.data.first()[stringPreferencesKey("pref_sync_frequency")])
    }

    @Test
    fun `a setting in the wrong type is skipped, and reading it does not throw`() = runBlocking {
        val store = store()
        // The sync frequency is text. Written as a number, the next read of
        // it as text threw a ClassCastException, and that read is at start.
        val applied = SettingsBackup.import(store, document(entry("pref_sync_frequency", "int", 2)))
        assertEquals(0, applied)
        assertNull(store.data.first()[stringPreferencesKey("pref_sync_frequency")])
    }

    @Test
    fun `a setting this version never declared is skipped`() = runBlocking {
        val store = store()
        val applied = SettingsBackup.import(
            store,
            document(
                entry("pref_from_a_later_version", "boolean", true),
                entry("com.saulhdev.neofeed.some_old_setting", "string", "x"),
            ),
        )
        assertEquals(0, applied)
        assertTrue(store.data.first().asMap().isEmpty())
    }

    @Test
    fun `a mixed file puts back the good and skips the rest`() = runBlocking {
        val store = store()
        val applied = SettingsBackup.import(
            store,
            document(
                entry("pref_pure_black", "boolean", true),
                entry("pref_pure_black", "string", "yes"),
                entry("pref_overlay_opacity", "float", 0.5),
                entry("pref_hidden_sources", "stringSet", listOf("3", "7")),
                entry("pref_learned_reset_at", "long", 1_800_000_000_000L),
                entry("pref_nobody_declared", "int", 4),
            ),
        )
        assertEquals(4, applied)
        val prefs = store.data.first()
        assertEquals(true, prefs[booleanPreferencesKey("pref_pure_black")])
        assertEquals(0.5f, prefs[floatPreferencesKey("pref_overlay_opacity")])
        assertEquals(setOf("3", "7"), prefs[stringSetPreferencesKey("pref_hidden_sources")])
        assertEquals(1_800_000_000_000L, prefs[longPreferencesKey("pref_learned_reset_at")])
        assertNull(prefs[intPreferencesKey("pref_nobody_declared")])
    }

    @Test
    fun `what belongs to one phone stays on it`() = runBlocking {
        // The folder is a permission granted to one install; the last run and
        // the stopped warning describe that folder; and a phone that has never
        // asked for notification permission must still ask.
        val mine = listOf(
            "pref_backup_folder",
            "pref_backup_last_run",
            "pref_backup_stopped_at",
            "pref_notification_permission_asked",
        )
        val source = store()
        source.edit {
            it[stringPreferencesKey("pref_backup_folder")] = "content://tree/primary%3ABackups"
            it[stringPreferencesKey("pref_backup_last_run")] = "1800000000000"
            it[longPreferencesKey("pref_backup_stopped_at")] = 1_800_000_000_000L
            it[booleanPreferencesKey("pref_notification_permission_asked")] = true
            it[booleanPreferencesKey("pref_pure_black")] = true
        }
        val file = SettingsBackup.export(source)
        mine.forEach { assertFalse("$it was written to the file", file.contains("\"$it\"")) }

        // And refused on the way in, from a file written by something else.
        val target = store()
        SettingsBackup.import(
            target,
            document(
                entry("pref_backup_folder", "string", "content://tree/x"),
                entry("pref_backup_stopped_at", "long", 5L),
                entry("pref_notification_permission_asked", "boolean", true),
            ),
        )
        assertTrue(target.data.first().asMap().isEmpty())
    }

    @Test
    fun `a round trip puts back everything that travels`() = runBlocking {
        val source = store()
        source.edit {
            it[booleanPreferencesKey("pref_pure_black")] = true
            it[stringPreferencesKey("pref_feed_layout")] = "mosaic"
            it[floatPreferencesKey("pref_mark_read_dwell_seconds")] = 1.5f
            it[stringSetPreferencesKey("pref_blocked_words")] = setOf("crypto", "rumour")
            it[longPreferencesKey("pref_learned_reset_at")] = 42L
        }
        val target = store()
        val applied = SettingsBackup.import(target, SettingsBackup.export(source))
        assertEquals(5, applied)
        assertEquals(source.data.first().asMap(), target.data.first().asMap())
    }

    @Test
    fun `every key the app declares can be put back`() {
        // Read from the declarations themselves, so a setting added later is
        // covered without anybody remembering a second list.
        val declared = FeedPreferences::class.java.declaredFields
            .filter { Modifier.isStatic(it.modifiers) && Preferences.Key::class.java.isAssignableFrom(it.type) }
            .map { field ->
                field.isAccessible = true
                (field.get(null) as Preferences.Key<*>).name
            }
            .toSet()
        assertTrue("found only ${declared.size} keys", declared.size > 60)
        assertEquals(declared, FeedPreferences.DECLARED_KEYS.keys)
    }
}
