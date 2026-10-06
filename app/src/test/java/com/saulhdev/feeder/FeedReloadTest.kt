package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The feed reloads after a burst of table changes, not during one.
 *
 * A Room Flow query re-runs on every write to any table it names, and the feed
 * names Article and Feeds — both of which a sync writes to continuously.
 * Measured on a device: thirty a second while sources were being added, each
 * rebuilding five hundred whole article rows including full article text,
 * against a 256MB heap.
 *
 * A 300ms debounce was already downstream and was not helping, because a
 * debounce drops a value it has already been handed: the query had run and
 * built its rows before anything could decide they were unwanted. 151
 * emissions in one five-second window, 25 of them used.
 *
 * Two things have to stay true for the replacement to be honest, and neither
 * is visible by reading one file:
 *
 *  - the debounce is on the *signal*, never back on the result;
 *  - each one-shot query returns exactly what its Flow original did, because
 *    a "tidied" copy is a second, quietly different feed.
 */
class FeedReloadTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    private val dao = read("data/db/dao/FeedArticleDao.kt")
    private val repo = read("data/repository/ArticleRepository.kt")
    private val viewModel = read("viewmodels/ArticleListViewModel.kt")

    /** The SQL of a named query, normalised for whitespace. */
    private fun sqlOf(function: String): String {
        val at = dao.indexOf("fun $function(")
        assertTrue("$function is missing", at > 0)
        val block = dao.substring(0, at)
        val open = block.lastIndexOf("\"\"\"")
        val body = block.lastIndexOf("\"\"\"", open - 1)
        assertTrue("$function has no query attached", body > 0)
        return dao.substring(body + 3, open).replace(Regex("\\s+"), " ").trim()
    }

    @Test
    fun `each one-shot query matches the flow it stands in for`() {
        listOf(
            "getAllEnabledFeedItems" to "loadAllEnabledFeedItems",
            "getFeedItemsByFeedIdsFlow" to "loadFeedItemsByFeedIds",
            "getAllBookmarkedFeedItems" to "loadAllBookmarkedFeedItems",
        ).forEach { (flow, oneShot) ->
            assertEquals(
                "$oneShot no longer returns what $flow returns",
                sqlOf(flow),
                sqlOf(oneShot),
            )
        }
    }

    @Test
    fun `the feed is loaded through the debounced signal, not a flow query`() {
        // The Flow variants still exist — the one-shot queries are compared
        // against them above — but nothing may feed the list from one, or the
        // debounce is bypassed and the measurement above comes straight back.
        listOf(
            "getAllEnabledFeedItems",
            "getFeedItemsByFeedIdsFlow",
            "getAllBookmarkedFeedItems",
        ).forEach { flow ->
            assertTrue(
                "the repository is serving the feed from $flow again",
                !repo.contains("articlesDao.$flow("),
            )
        }
        assertTrue(
            "the repository no longer debounces the invalidation signal",
            repo.contains("invalidationTracker.createFlow(\"Article\", \"Feeds\""),
        )
    }

    @Test
    fun `the debounce has not crept back onto the built list`() {
        // Debouncing here is what looked like a fix for two years and was not.
        // It costs the whole query and saves only the processing.
        // Scoped to categoryArticles' own definition. The search field's
        // debounce lives just below it and is a different thing entirely —
        // that one delays reacting to typing, which is right, and an earlier
        // version of this test condemned it.
        val start = viewModel.indexOf("private val categoryArticles")
        assertTrue("categoryArticles is gone", start > 0)
        val definition = viewModel.substring(start, viewModel.indexOf(".conflate()", start))
        assertTrue(
            "the article list is debounced again after being built",
            !definition.contains(".debounce"),
        )
        assertTrue(
            "the bookmarks flow debounces its built list again",
            !viewModel.contains("getBookmarkedFeedItems().debounce"),
        )
    }

    @Test
    fun `the first load is not delayed`() {
        // Tapping a category and waiting a third of a second for the list
        // would trade one visible fault for another.
        val at = repo.indexOf("private fun <T> whenChanged")
        assertTrue("whenChanged is gone", at > 0)
        val body = repo.substring(at, minOf(at + 2200, repo.length))
        assertTrue("the first emission is no longer immediate", body.contains("0L"))
        // Per collection, not per flow. Shared, the second collector inherits
        // a flag the first one cleared and waits out the debounce for an
        // opening list — on a screen that has only just been opened.
        val declaration = body.indexOf("var first = true")
        val builder = body.indexOf("flow {")
        assertTrue("the first-load flag is gone", declaration > 0)
        assertTrue("whenChanged no longer builds its own flow", builder > 0)
        assertTrue(
            "the first-load flag is shared by every collector of this flow",
            builder < declaration,
        )
        assertTrue(
            "a load still running is no longer abandoned when another change arrives",
            body.contains("mapLatest"),
        )
    }
}
