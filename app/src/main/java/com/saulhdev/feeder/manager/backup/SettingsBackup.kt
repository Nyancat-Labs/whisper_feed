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
package com.saulhdev.feeder.manager.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.PrefType
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The settings, as a file that can be written beside the OPML.
 *
 * **Not inside the OPML.** That is a defined interchange format, and the whole
 * value of writing one is that Feedly, FreshRSS or Thunderbird can read it.
 * Smuggling this app's preferences into its head would either be ignored, or
 * worse, not be — and would make a file that exists to be portable into one
 * that carries a particular app's furniture around. Two files in one folder.
 *
 * Every preference is written with its type, because DataStore is typed and a
 * JSON number does not say whether it was an Int, a Long or a Float. Reading a
 * value back as the wrong one throws at the point of use, a long way from
 * here, so the type travels with the value.
 */
object SettingsBackup {

    const val FILE_NAME = "whisper-settings.json"

    private const val VERSION = 1

    /**
     * Preferences that must not travel to another device.
     *
     * The backup folder is a Uri granted to *this* install by the system's
     * document picker; on another phone it names a permission that does not
     * exist, and restoring it would leave the backup screen claiming a
     * destination it cannot write to. The reader picks a folder on the new
     * device, which takes one tap and is honest about what happened.
     */
    private val NOT_PORTABLE = setOf(
        "pref_backup_folder",
        "pref_backup_last_run",
        // Also this phone's: when its folder stopped working, and whether it
        // has asked for notification permission. Restored on a new phone,
        // the one warned about a folder it never had and the other never
        // asked at all.
        "pref_backup_stopped_at",
        "pref_notification_permission_asked",
    )

    /**
     * kotlinx.serialization rather than `org.json`.
     *
     * The platform's JSON classes are stubs in a unit test — every method
     * throws "not mocked" — so a format written with them can only be checked
     * on a device. This is already a dependency, works on the JVM, and means
     * the shape of the file is covered by the same suite as everything else.
     */
    private val json = Json { prettyPrint = true }

    /** Everything currently set, as JSON. */
    suspend fun export(dataStore: DataStore<Preferences>): String {
        val prefs = dataStore.data.first()

        val entries = buildJsonArray {
            prefs.asMap().forEach { (key, value) ->
                if (key.name in NOT_PORTABLE) return@forEach
                val entry = buildJsonObject {
                    put("key", key.name)
                    when (value) {
                        is Boolean -> { put("type", "boolean"); put("value", value) }
                        is Int -> { put("type", "int"); put("value", value) }
                        is Long -> { put("type", "long"); put("value", value) }
                        is Float -> { put("type", "float"); put("value", value) }
                        is String -> { put("type", "string"); put("value", value) }
                        is Set<*> -> {
                            put("type", "stringSet")
                            put(
                                "value",
                                buildJsonArray { value.forEach { add(JsonPrimitive(it.toString())) } },
                            )
                        }
                        // A type DataStore gained since this was written.
                        // Skipped rather than guessed at: a preference missing
                        // from a backup falls back to its default, which is
                        // recoverable, and one restored as the wrong type is not.
                        else -> return@forEach
                    }
                }
                add(entry)
            }
        }

        return json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("version", VERSION)
                put("app", "whisper")
                put("settings", entries)
            },
        )
    }

    /**
     * Puts them back, and reports how many.
     *
     * Only a key this version declares, and only as the type it declares it.
     * An unknown key is skipped: a backup from a later version names settings
     * this one has never heard of, and storing them would leave junk in the
     * preferences for ever. A known key in another type is skipped too, and
     * that one matters more. It was written as given, and the next read of the
     * setting as its own type threw, at start, on every start, until the app's
     * data was cleared. A setting that changed type between versions would
     * have done it with no hand-edited file at all.
     *
     * @param declared what may be put back; the app's own keys unless a test
     *   says otherwise.
     */
    suspend fun import(
        dataStore: DataStore<Preferences>,
        text: String,
        declared: Map<String, PrefType> = FeedPreferences.DECLARED_KEYS,
    ): Int {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: return 0
        if (root["app"]?.jsonPrimitive?.contentOrNull != "whisper") return 0
        val entries = runCatching { root["settings"]?.jsonArray }.getOrNull() ?: return 0

        var applied = 0
        dataStore.edit { prefs ->
            entries.forEach { element ->
                val entry = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
                val name = entry["key"]?.jsonPrimitive?.contentOrNull.orEmpty()
                if (name.isEmpty() || name in NOT_PORTABLE) return@forEach
                val type = declared[name] ?: return@forEach
                if (entry["type"]?.jsonPrimitive?.contentOrNull != type.tag) return@forEach
                val value = entry["value"] ?: return@forEach

                runCatching {
                    when (type) {
                        PrefType.BOOLEAN -> prefs[booleanPreferencesKey(name)] =
                            value.jsonPrimitive.boolean

                        PrefType.INT -> prefs[intPreferencesKey(name)] = value.jsonPrimitive.int
                        PrefType.LONG -> prefs[longPreferencesKey(name)] = value.jsonPrimitive.long
                        PrefType.FLOAT -> prefs[floatPreferencesKey(name)] = value.jsonPrimitive.float
                        // content alone would take a number or true as text;
                        // only a JSON string is one.
                        PrefType.STRING -> prefs[stringPreferencesKey(name)] =
                            value.jsonPrimitive.takeIf { it.isString }?.content ?: return@forEach

                        PrefType.STRING_SET -> prefs[stringSetPreferencesKey(name)] =
                            value.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    }
                    applied++
                }
            }
        }
        return applied
    }

    /** Whether a file looks like one of ours, before anything is written. */
    fun looksLikeOurs(text: String): Boolean =
        runCatching {
            json.parseToJsonElement(text).jsonObject["app"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() == "whisper"
}
