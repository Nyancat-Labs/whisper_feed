package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The backup confirmation names both files.
 *
 * A backup is two files — the subscriptions as OPML, the settings as JSON
 * beside it — and the screen's own explanation says so in as many words. The
 * confirmation afterwards named only the OPML, which left the reader with a
 * paragraph promising two files and a message reporting one. The reasonable
 * conclusion is that the settings were not included, which is the opposite of
 * what happened.
 *
 * Pinned here rather than left to the next reading of the screen, because the
 * two halves live in different files: the sentence is in strings.xml and the
 * thing it describes is in BackupStore, and they drifted apart once already.
 */
class BackupMessageTest {

    private val strings = File("src/main/res/values/strings.xml").readText()
    private val store = File(
        "src/main/java/com/saulhdev/feeder/manager/backup/BackupStore.kt"
    ).readText()
    private val page = File(
        "src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt"
    ).readText()

    private fun string(name: String): String {
        val match = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)
        assertTrue("$name is missing from strings.xml", match != null)
        return match!!.groupValues[1]
    }

    @Test
    fun `the confirmation has a place for both file names`() {
        val written = string("backup_written")
        assertTrue("the OPML name is missing", written.contains("%1\$s"))
        assertTrue("the settings name is missing", written.contains("%2\$s"))
    }

    @Test
    fun `the half-written case has its own sentence, with one name`() {
        // Claiming both files when only one was written would be the same
        // untruth in the other direction.
        val partial = string("backup_written_sources_only")
        assertTrue("the OPML name is missing", partial.contains("%1\$s"))
        assertTrue("it cannot name a file that was not written", !partial.contains("%2\$s"))
    }

    @Test
    fun `the result carries the settings file name`() {
        assertTrue(
            "Written no longer reports the settings file",
            store.contains("settingsName: String?"),
        )
        assertTrue(
            "the settings name is no longer conditional on the write succeeding",
            store.contains("SettingsBackup.FILE_NAME.takeIf { settingsWritten }"),
        )
    }

    @Test
    fun `a failed settings write does not sink the whole backup`() {
        // The OPML is the part nobody could reconstruct. If it reached the
        // folder, the reader has a backup, and reporting Failed would have
        // them believe otherwise.
        val settings = store.indexOf("val settingsWritten")
        assertTrue("the settings write moved", settings > 0)
        val opml = store.indexOf("val file = tree.createFile(MIME_TYPE, FILE_NAME)")
        assertTrue("the OPML write moved", opml > 0)
        assertTrue("the settings write comes after the OPML's", settings > opml)
        assertTrue(
            "the settings write is no longer caught on its own",
            store.substring(settings, settings + 600).contains("runCatching"),
        )
    }

    @Test
    fun `the screen chooses between the two sentences`() {
        assertEquals(1, Regex("""R\.string\.backup_written\b""").findAll(page).count())
        assertEquals(1, Regex("""R\.string\.backup_written_sources_only""").findAll(page).count())
        assertTrue(
            "the screen no longer checks whether the settings were written",
            page.contains("result.settingsName != null"),
        )
    }
}
