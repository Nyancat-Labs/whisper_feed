package com.saulhdev.feeder

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.saulhdev.feeder.manager.backup.SettingsReset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Reset all settings (sheet 68), against a real preference store. */
class ResetSettingsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun done() = scope.cancel()

    @Test
    fun `settings go back to their defaults, the reader's own things stay`() = runBlocking {
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { folder.root.resolve("p.preferences_pb") })
        val layout = stringPreferencesKey("pref_feed_layout")
        val blocked = stringSetPreferencesKey("pref_blocked_words")
        val alerts = booleanPreferencesKey("pref_breaking_alerts")
        val pinned = stringSetPreferencesKey("pref_favourite_sources")
        val place = stringPreferencesKey("pref_glance_place_name")
        val cloud = booleanPreferencesKey("pref_platform_backup_cloud")
        val folderKey = stringPreferencesKey("pref_backup_folder")
        val toured = booleanPreferencesKey("pref_tour_seen")
        store.edit {
            it[layout] = "cards"; it[blocked] = setOf("sport"); it[alerts] = true
            it[pinned] = setOf("12"); it[place] = "Skibbereen"; it[cloud] = false
            it[folderKey] = "content://x"; it[toured] = true
        }

        assertEquals(3, SettingsReset.reset(store))

        val after = store.data.first()
        assertNull(after[layout]); assertNull(after[blocked]); assertNull(after[alerts])
        assertEquals(setOf("12"), after[pinned])
        assertEquals("Skibbereen", after[place])
        assertEquals("a backup choice is never switched on by a reset", false, after[cloud])
        assertEquals("content://x", after[folderKey])
        assertEquals(true, after[toured])
    }

    @Test
    fun `the row is in Help and asks first`() {
        val src = File("src/main/java/com/saulhdev/feeder/").let {
            it.resolve("ui/pages/SettingsSection.kt").readText() to it.resolve("data/content/FeedPreferences.kt").readText()
        }
        assertTrue(src.first.substringAfter("SettingsSection.HELP -> listOf(").substringBefore(")").contains("prefs.resetSettings"))
        assertTrue(src.second.contains("confirmId = R.string.pref_reset_settings_confirm"))
    }

    @Test
    fun `restore starts with everything ticked`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt").readText()
        assertTrue(page.contains("mutableStateOf(BackupStore.Part.entries.toSet())"))
    }

    @Test
    fun `the welcome screen says saved articles come back too`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("your sources, saved articles and settings come back"))
    }

    @Test
    fun `a loading indicator on its own line is centred`() {
        for (f in listOf("AccountPage.kt", "GlanceLocationPage.kt")) {
            val page = File("src/main/java/com/saulhdev/feeder/ui/pages/$f").readText()
            assertTrue(f, page.substringAfter("WhisperLoading(").take(300).contains("wrapContentWidth(Alignment.CenterHorizontally)"))
        }
    }
}
