package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Narrowing the feed to one source lands on the article the tap came from.
 *
 * A filtered feed is a different list of a different length, and a scroll
 * position is an index into whichever list is on screen. Kept across the
 * change it points at a different story — which is what the reader saw:
 * tapping SlashGear on one article opened the filtered feed somewhere else
 * entirely, halfway down.
 *
 * The fix has two halves, and each is the kind that survives a compile after
 * being quietly undone, so both are read here rather than trusted.
 */
class FocusAnchorTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    private val anchor = source("ui/overlay/FocusAnchor.kt")
    private val page = source("ui/pages/ArticleListPage.kt")
    private val scaffold = source("ui/overlay/FeedScaffold.kt")
    private val overlay = source("manager/service/OverlayView.kt")
    private val viewModel = source("viewmodels/ArticleListViewModel.kt")
    private val card = source("ui/overlay/ArticleCard.kt")

    /**
     * The first half: the scroll waits for the list the narrowing produced.
     *
     * Keyed on the request instead, it would run while the list being left is
     * still on screen, scroll to a position in that one, and be replaced a
     * moment later — the original bug wearing a fix.
     */
    @Test
    fun `the scroll is keyed on the applied focus, not the request`() {
        val key = Regex("""LaunchedEffect\(([^)]*)\)""").find(anchor)?.groupValues?.get(1)
        assertTrue("keyed on something", key != null)
        assertTrue("on the applied focus: $key", key!!.contains("appliedFocus"))
        assertTrue("and on the anchor: $key", key.contains("anchorId"))
    }

    @Test
    fun `the article's position is offset past the header`() {
        assertTrue(
            "an index into the articles is not an index into the list",
            anchor.contains("feedListIndex(position, breaks)") &&
                source("ui/overlay/FeedDays.kt").contains("position + FEED_ARTICLES_START + breaks.count { it.index <= position }"),
        )
        assertEquals(
            "one header item, in every feed list",
            1,
            Regex("""FEED_ARTICLES_START\s*=\s*(\d+)""")
                .find(source("ui/overlay/FeedRhythm.kt"))!!.groupValues[1].toInt(),
        )
    }

    /**
     * The second half: the focus the list was built under travels *with* the
     * list. Combined in beside it, the screen can be handed a fresh focus
     * with the previous list still attached, and the first half is defeated.
     */
    @Test
    fun `the state carries the focus its articles were built under`() {
        assertTrue(
            "built from the processed feed, not a separate flow",
            viewModel.contains("focusedSource = feed.focusedSource"),
        )
        assertTrue(
            "and processing puts it there",
            viewModel.contains("ProcessedFeed(processed, filter.focusedSource)"),
        )
    }

    @Test
    fun `both surfaces anchor, from the state rather than the request`() {
        assertTrue(
            "the app feed's frame is built under the state's focus",
            page.contains("rememberFeedFrame(state.articles, state.focusedSource)"),
        )
        listOf(page to "the app feed", scaffold to "the panel").forEach { (text, what) ->
            assertTrue(
                "$what anchors on the focus its drawn articles were built under",
                text.contains("appliedFocus = frame.focus,\n        anchorId = focusAnchor,\n        articles = frame.articles,"),
            )
        }
        assertTrue("the app feed anchors at all", page.contains("AnchorFeedOnFocusChange("))
        assertTrue("the panel anchors too", scaffold.contains("AnchorFeedOnFocusChange("))
        assertTrue(
            "and takes the applied focus as a parameter rather than reusing focusedSource",
            scaffold.contains("appliedFocus: String? = null"),
        )
        assertTrue(
            "the panel's applied focus is set from the same emission as its articles",
            Regex(
                """articleListState\.collect \{[^}]*articlesState\.value[^}]*appliedFocus\.value""",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(overlay),
        )
    }

    /**
     * Every card shape names the article its byline belongs to.
     *
     * The anchor is the article id, and the only place it can come from is the
     * row that was tapped. A shape that forgets to pass it still compiles —
     * the parameter is optional, because four of the five call sites would
     * otherwise have to be edited to add a card — and silently goes back to
     * the arbitrary landing.
     */
    @Test
    fun `every byline carries its article`() {
        // Not the declaration, which ends in the same two characters.
        val calls = Regex("""(?<!fun )ArticleMeta\(\n""").findAll(card).count()
        val carried = Regex("""\n\s*articleId = item\.id,""").findAll(card).count()
        assertEquals("one per ArticleMeta call site", calls, carried)
        assertTrue("and there are some", calls >= 4)
    }
}
