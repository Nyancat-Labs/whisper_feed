package com.saulhdev.feeder

import com.saulhdev.feeder.viewmodels.MarkReadRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MarkAllReadTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `the four choices, in the order they are offered`() {
        assertEquals(
            listOf(
                MarkReadRange.Everything, MarkReadRange.OlderThanHour,
                MarkReadRange.OlderThanDay, MarkReadRange.OlderThanTwoDays,
            ),
            MarkReadRange.entries,
        )
    }

    @Test
    fun `each choice cuts off where it says`() {
        // Everything has no cut-off, so an article a site dated tomorrow is
        // still marked.
        assertEquals(Long.MAX_VALUE, MarkReadRange.Everything.before(now))
        assertEquals(now - 3_600_000L, MarkReadRange.OlderThanHour.before(now))
        assertEquals(now - 86_400_000L, MarkReadRange.OlderThanDay.before(now))
        assertEquals(now - 172_800_000L, MarkReadRange.OlderThanTwoDays.before(now))
    }

    @Test
    fun `older reaches less far than everything`() {
        assertTrue(MarkReadRange.OlderThanTwoDays.before(now) < MarkReadRange.OlderThanDay.before(now))
        assertTrue(MarkReadRange.OlderThanDay.before(now) < MarkReadRange.OlderThanHour.before(now))
        assertTrue(MarkReadRange.OlderThanHour.before(now) < MarkReadRange.Everything.before(now))
    }

    @Test
    fun `the count and the marking ask the same question`() {
        val dao = File("src/main/java/com/saulhdev/feeder/data/db/dao/FeedArticleDao.kt").readText()
        val cut = "AND Article.primarySortTime < :before"
        assertEquals("both queries filter by the article's own date", 2, Regex(Regex.escape(cut)).findAll(dao).count())
        assertTrue(dao.contains("suspend fun unreadIdsBefore(before: Long): List<String>"))
        assertTrue(dao.contains("suspend fun unreadCountBefore(before: Long): Int"))
    }

    @Test
    fun `it lives in the feed's sheet, and is offered back at once`() {
        val sheet = File("src/main/java/com/saulhdev/feeder/ui/pages/SortFilterSheet.kt").readText()
        assertTrue(sheet.contains("MarkReadBlock("))
        // The count and the marking use the moment the sheet opened.
        assertTrue(sheet.contains("value = counts(now)"))
        assertTrue(sheet.contains("onClick = { onMark(range, now) }"))
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt").readText()
        assertTrue(page.contains("viewModel.markAllRead(range, now) { count ->"))
        assertTrue(page.contains("if (count > 0) scope.launch { offerReadsBack(count) }"))
    }

    @Test
    fun `settings no longer has its own copy`() {
        val settings = File("src/main/java/com/saulhdev/feeder/ui/pages/PreferencesPage.kt").readText()
        assertFalse(settings.contains("markAllRead"))
        val prefs = File("src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt").readText()
        assertFalse(prefs.contains("markEverythingRead"))
    }
}
