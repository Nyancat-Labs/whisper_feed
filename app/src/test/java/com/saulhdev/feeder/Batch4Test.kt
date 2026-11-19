package com.saulhdev.feeder

import com.saulhdev.feeder.ui.pages.siteHome
import com.saulhdev.feeder.viewmodels.SourceSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

/** Batch 4, 19 November: Sources, Import, About, Help (sheet 20-61, 107, 110). */
class Batch4Test {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `Open website goes to the site's home page, not the feed`() {
        assertEquals("https://example.com/", siteHome(URL("https://example.com/feed.xml")))
        assertEquals("http://example.com:8080/", siteHome(URL("http://example.com:8080/rss?x=1")))
        assertEquals("https://news.example.org/", siteHome(URL("https://news.example.org/a/b/c/")))
    }

    @Test
    fun `no asterisk left for Pin`() {
        for (f in listOf("ui/components/SourceItem.kt", "ui/overlay/ArticleCard.kt", "ui/overlay/ArticleMenu.kt")) {
            val text = source(f)
            assertFalse(f, text.contains("Phosphor.Asterisk"))
            assertTrue(f, text.contains("Phosphor.PushPin"))
        }
    }

    @Test
    fun `no clouds on import and export`() {
        for (f in listOf("ui/pages/BookmarkImportPage.kt", "ui/pages/SourceListPage.kt")) {
            assertFalse(f, source(f).contains("CloudArrow"))
        }
    }

    @Test
    fun `every sort choice has its own icon`() {
        assertEquals(SourceSort.entries.size, SourceSort.entries.map { it.icon.name }.toSet().size)
    }

    @Test
    fun `Your reading is under Your data, Learned stays under Your feed`() {
        val sections = source("ui/pages/SettingsSection.kt")
        val feed = sections.substringAfter("SettingsSection.FEED -> listOf(").substringBefore(")")
        val data = sections.substringAfter("SettingsSection.DATA -> listOf(").substringBefore(")")
        assertTrue(feed.contains("prefs.learned"))
        assertFalse(feed.contains("prefs.statistics"))
        assertTrue(data.contains("prefs.statistics"))
        assertTrue(data.contains("prefs.forgetLearned"))
    }

    @Test
    fun `About wording`() {
        assertTrue(strings.contains("<string name=\"about_version_line\">Version %1\$s (build %2\$d)</string>"))
        assertTrue(strings.contains("<string name=\"about_team\">Built on</string>"))
        assertTrue(strings.contains("<string name=\"about_support_button\">Chip in on Ko-fi</string>"))
        assertFalse("the Website row is gone", strings.contains("about_contact_website"))
        assertTrue(strings.contains("<string name=\"pref_transparency\">Launcher page background</string>"))
    }
}
