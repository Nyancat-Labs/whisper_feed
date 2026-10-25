package com.saulhdev.feeder

import com.saulhdev.feeder.ui.pages.MINIMUM_MS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The owner's review of the 21 October preview, 25 October. */
class ReviewFixesTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `an empty feed brings the top bar back, in the app and the panel`() {
        listOf("ui/pages/ArticleListPage.kt", "ui/overlay/FeedScaffold.kt").forEach { path ->
            val page = read(path)
            val empty = page.substringAfter("frame.articles.isEmpty()) {").take(400)
            assertTrue(path, empty.contains("scrollBehavior.state.heightOffset = 0f"))
        }
    }

    @Test
    fun `the app's Undo is not in the Scaffold slot above the button`() {
        val page = read("ui/pages/ArticleListPage.kt")
        assertFalse(page.contains("snackbarHost = { FeedSnackbarHost"))
        assertTrue(page.contains(".align(Alignment.BottomCenter)"))
    }

    @Test
    fun `Settings icons say what each page is`() {
        val section = read("ui/pages/SettingsSection.kt")
        listOf("Phosphor.Rss)", "Phosphor.Newspaper)", "Phosphor.ArrowsClockwise)", "Phosphor.Archive)", "Phosphor.Lifebuoy)")
            .forEach { assertTrue(it, section.contains(it)) }
        // "Your data" is kept on the phone; a cloud said otherwise.
        assertFalse(section.contains("Phosphor.CloudArrowUp)"))
        assertFalse(read("ui/pages/BackupPage.kt").contains("PaintRoller"))
    }

    @Test
    fun `the backup page restores from one button, and other apps have their own heading`() {
        val page = read("ui/pages/BackupPage.kt")
        assertTrue(page.contains("store.restoreFolder(uri, parts)"))
        assertTrue(page.contains("R.string.backup_heading_other_apps"))
        assertFalse(page.contains("R.string.backup_restore_settings"))
        val store = read("manager/backup/BackupStore.kt")
        assertTrue("settings are only restored when ticked", store.contains("if (Part.SETTINGS !in parts) null"))
    }

    @Test
    fun `the splash is short`() {
        assertTrue(MINIMUM_MS <= 300L)
    }

    @Test
    fun `the filter sheet never asks for a position it does not have`() {
        // Peek height 0: Material 3 1.5 builds only Hidden and Expanded, and
        // a back gesture crashed asking for PartiallyExpanded.
        val page = read("ui/pages/ArticleListPage.kt")
        assertTrue(page.contains("sheetPeekHeight = 0.dp"))
        assertTrue(page.contains("initialValue = SheetValue.Hidden"))
        assertFalse(page.contains("partialExpand()"))
    }
}
