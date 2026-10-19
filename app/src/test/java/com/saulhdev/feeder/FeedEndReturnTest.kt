package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.FEED_END_KEY
import com.saulhdev.feeder.ui.overlay.onlyTheEndShows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FeedEndReturnTest {

    private val ids = setOf("a", "b", "c")

    @Test
    fun `the empty end alone on screen counts`() {
        assertTrue(onlyTheEndShows(listOf(FEED_END_KEY), ids))
        // A day heading above it is not an article.
        assertTrue(onlyTheEndShows(listOf("day-2026-10-19", FEED_END_KEY), ids))
    }

    @Test
    fun `an article still on screen keeps the place`() {
        assertFalse(onlyTheEndShows(listOf("c", FEED_END_KEY), ids))
        assertFalse("not at the end at all", onlyTheEndShows(listOf("a", "b"), ids))
    }

    @Test
    fun `both feeds go back, on returning or on something new, never on a read leaving`() {
        val helper = File("src/main/java/com/saulhdev/feeder/ui/overlay/FeedItems.kt").readText()
        assertTrue(helper.contains("if (visible && resumed && onlyTheEndShows(visibleKeys(), ids)) scrollToTop()"))
        assertTrue("only arrivals, not departures", helper.contains("ids.any { it !in before }"))
        listOf("ui/pages/ArticleListPage.kt", "ui/overlay/FeedScaffold.kt").forEach {
            assertTrue(it, File("src/main/java/com/saulhdev/feeder/$it").readText().contains("FeedEndReturn("))
        }
    }
}
