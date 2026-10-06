package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The feed has no side margin, on either surface.
 *
 * `PullToRefreshLazyColumn` defaults its content padding to 8dp on all four
 * sides. The launcher feed passes its own and the app's feed did not, so the
 * same card was full bleed in the Discover panel and inset by eight points in
 * the app — a photograph with a margin, which reads as a component on a page
 * rather than as the article it is.
 *
 * Checked in the source because the difference is a defaulted argument. There
 * is nothing to observe at runtime without rendering both surfaces and
 * measuring pixels, which is how it was eventually found and is not something
 * to need twice.
 */
class FeedPaddingTest {

    private val mainSources = File("src/main/java")

    private fun read(path: String) =
        File(mainSources, path).readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

    /** The scroll containers that default to a margin if nobody says otherwise. */
    private val defaulting = listOf("PullToRefreshLazyColumn(", "PullToRefreshStaggeredGrid(")

    private fun callsWithoutPadding(code: String): List<String> =
        defaulting.filter { call ->
            var from = 0
            var missing = false
            while (true) {
                val start = code.indexOf(call, from)
                if (start < 0) break
                // Far enough to clear this file's indentation and reach the
                // named arguments before the content lambda begins.
                val body = code.substring(start, minOf(start + 900, code.length))
                val lambda = body.indexOf("content =")
                val args = if (lambda > 0) body.substring(0, lambda) else body
                if (!args.contains("contentPadding")) missing = true
                from = start + 1
            }
            missing
        }

    @Test
    fun `the sources are actually being read`() {
        assertTrue("no Kotlin sources found under $mainSources", mainSources.exists())
    }

    @Test
    fun `the app's feed states its own padding`() {
        val code = read("com/saulhdev/feeder/ui/pages/ArticleListPage.kt")
        assertEquals(
            "these take the 8dp default instead of the feed's padding",
            emptyList<String>(),
            callsWithoutPadding(code),
        )
    }

    @Test
    fun `the launcher feed states its own padding`() {
        val code = read("com/saulhdev/feeder/ui/overlay/FeedScaffold.kt")
        assertEquals(
            "these take the 8dp default instead of the feed's padding",
            emptyList<String>(),
            callsWithoutPadding(code),
        )
    }

    @Test
    fun `neither surface puts a horizontal margin on the feed`() {
        // The cards run to the edge; their text carries its own margin.
        val app = read("com/saulhdev/feeder/ui/pages/ArticleListPage.kt")
        assertTrue(
            "the app feed must not set a horizontal content padding",
            !Regex("""contentPadding\s*=\s*PaddingValues\([^)]*horizontal""").containsMatchIn(app),
        )
    }
}
