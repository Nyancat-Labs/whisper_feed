package com.saulhdev.feeder

import com.saulhdev.feeder.ui.pages.MINIMUM_MS
import org.junit.Assert.assertEquals
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

    @Test
    fun `buttons come in three tones, and none is the theme's pink`() {
        val buttons = read("ui/components/Button.kt")
        assertFalse(buttons.contains("colorScheme.tertiary"))
        assertTrue(buttons.contains("destructive -> MaterialTheme.colorScheme.error"))
        // Remove, forget and sign out say so in red.
        listOf("SourceEditPage", "LearnedPage", "AccountPage", "BrokenFeedsPage").forEach {
            assertTrue(it, read("ui/pages/$it.kt").contains("destructive = true"))
        }
        assertFalse("Reset is not destructive", read("ui/pages/SortFilterSheet.kt").contains("destructive = true"))
    }

    @Test
    fun `no two settings on a page share a vague icon`() {
        val prefs = read("data/content/FeedPreferences.kt")
        fun iconOf(name: String) = Regex("va[lr] $name *=.*?icon = Phosphor\\.(\\w+)", RegexOption.DOT_MATCHES_ALL)
            .find(prefs)!!.groupValues[1]
        assertTrue(listOf("glanceEnabled", "glancePlaceName", "markReadOnScroll").map(::iconOf).none { it == "Clock" })
        assertTrue(iconOf("account") != iconOf("backupFolder"))
        assertTrue(iconOf("overlayTransparency") != iconOf("showSummaries"))
        assertTrue(iconOf("blockedWords") != "Hash" && iconOf("itemsPerFeed") != "Hash")
    }

    @Test
    fun `List with pictures is a layout of its own, and plain List downloads nothing`() {
        val card = read("ui/overlay/ArticleCard.kt")
        val row = card.substringAfter("fun ArticleTextRow(").substringBefore("fun ArticleMosaicTile(")
        assertTrue(row.contains("picture: Boolean = false,"))
        assertTrue(row.contains("if (picture && !image.isNullOrBlank())"))
        assertTrue(row.contains(".size(LIST_PICTURE_SIZE)"))
        assertTrue(card.contains("picture = layout == LAYOUT_LIST_PICTURES,"))
        assertFalse("no separate switch", read("data/content/FeedPreferences.kt").contains("listPictures"))
        assertEquals(
            com.saulhdev.feeder.ui.overlay.FeedCardShape.Text,
            com.saulhdev.feeder.ui.overlay.feedCardShape(0, true, com.saulhdev.feeder.utils.LAYOUT_LIST_PICTURES),
        )
    }
}
