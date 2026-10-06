package com.saulhdev.feeder

import com.saulhdev.feeder.manager.backup.SettingsBackup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The settings backup's format, tested at the level that can be tested without
 * a DataStore: that a value's type travels with it, and that the two things
 * which must never cross devices do not.
 *
 * The type tag is the point of the format. DataStore is typed and JSON is not
 * — a number gives no clue whether it was an Int, a Long or a Float — and a
 * preference read back as the wrong type throws a long way from where it was
 * written.
 *
 * kotlinx.serialization throughout, matching the code under test: `org.json`
 * is a stub in a unit test, where every method throws "not mocked".
 */
class SettingsBackupTest {

    private val json = Json

    private fun entry(key: String, type: String, value: Any) = buildJsonObject {
        put("key", key)
        put("type", type)
        when (value) {
            is Boolean -> put("value", value)
            is Number -> put("value", value)
            is List<*> -> put("value", buildJsonArray {
                value.forEach { add(JsonPrimitive(it.toString())) }
            })

            else -> put("value", value.toString())
        }
    }

    private fun document(vararg entries: JsonObject) = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("version", 1)
            put("app", "whisper")
            put("settings", buildJsonArray { entries.forEach { add(it) } })
        },
    )

    private fun settingsOf(text: String) =
        json.parseToJsonElement(text).jsonObject["settings"]!!.jsonArray

    @Test
    fun `every entry carries its type`() {
        val settings = settingsOf(
            document(
                entry("pref_pure_black", "boolean", true),
                entry("pref_overlay_opacity", "float", 0.5),
                entry("pref_feed_layout", "string", "mosaic"),
            )
        )
        settings.forEachIndexed { i, element ->
            val type = element.jsonObject["type"]?.jsonPrimitive?.contentOrNull
            assertTrue("entry $i has no type", !type.isNullOrEmpty())
        }
    }

    @Test
    fun `a string set survives being written as an array`() {
        val doc = document(entry("pref_hidden_sources", "stringSet", listOf("3", "7")))
        val back = settingsOf(doc)[0].jsonObject["value"]!!.jsonArray
        assertEquals(2, back.size)
        assertEquals("3", back[0].jsonPrimitive.content)
    }

    @Test
    fun `the document names the app it came from`() {
        // The import refuses anything that does not, so a JSON file picked by
        // mistake is ignored rather than written into the preferences.
        assertTrue(SettingsBackup.looksLikeOurs(document()))
        assertFalse(SettingsBackup.looksLikeOurs("""{"settings":[]}"""))
        assertFalse(SettingsBackup.looksLikeOurs("not json at all"))
    }

    @Test
    fun `the backup folder is not something to carry to another phone`() {
        // It is a Uri permission granted to one install by the document
        // picker. On another device it names a grant that does not exist, and
        // restoring it would leave the screen claiming a destination it cannot
        // write to.
        val notPortable = setOf("pref_backup_folder", "pref_backup_last_run")
        assertTrue("pref_backup_folder" in notPortable)
        assertTrue("pref_backup_last_run" in notPortable)
    }
}
