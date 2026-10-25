package com.saulhdev.feeder

import com.saulhdev.feeder.ui.pages.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings as a short top list (owner, 24 October 2026),
 * each row a page. Nothing was to be lost on the way.
 */
class SettingsSplitTest {

    private val pages = File("src/main/java/com/saulhdev/feeder/ui/pages/SettingsSection.kt").readText()
    private val top = File("src/main/java/com/saulhdev/feeder/ui/pages/PreferencesPage.kt").readText()

    @Test
    fun `the top list is the seven pages in the agreed order, then About`() {
        assertEquals(
            listOf("APPEARANCE", "SOURCES", "FEED", "READING", "FETCHING", "DATA", "HELP"),
            SettingsSection.topLevel.map { it.name },
        )
        assertTrue(top.contains("ActionPreference(pref = prefs.about"))
    }

    @Test
    fun `every setting the old page listed is on exactly one page`() {
        val settings = listOf(
            "syncFrequency", "syncRange", "itemsPerFeed", "syncOnlyOnWifi", "syncOnlyWhenCharging",
            "fullTextForAllFeeds", "fullTextOnMobile", "breakingNews", "dimSkipped", "removeDuplicates",
            "blockedWords", "glanceEnabled", "glancePlaceName", "learned", "statistics", "articleOpenMode",
            "readerTextSize", "readVisibility", "markReadOnScroll", "volumeKeyScroll", "feedLayout",
            "showSummaries", "timeFormat", "overlayTheme", "pureBlack", "appFont", "dynamicColor",
            "overlayTransparency", "sources", "feedLibrary", "importBookmarks", "account", "backupFolder",
            "showTour", "launcherSetup", "reportProblem", "exportDiagnostics", "debugging", "testSyncNotice",
        )
        settings.forEach { name ->
            val count = Regex("""prefs\.$name\b(?!Page)""").findAll(pages.substringAfter("fun settingsRows(").substringBefore("/** One page of Settings. */")).count()
            assertEquals("prefs.$name", 1, count)
        }
    }

    @Test
    fun `the diagnostics are one row in Help, not three`() {
        val help = pages.substringAfter("SettingsSection.HELP -> listOf(").substringBefore(")")
        assertTrue(help.contains("prefs.reportProblemPage"))
        assertFalse(help.contains("prefs.exportDiagnostics"))
        assertFalse(help.contains("prefs.debugging"))
    }
}
