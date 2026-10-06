package com.saulhdev.feeder

import com.saulhdev.feeder.viewmodels.ArticleViewModel.FullText
import com.saulhdev.feeder.viewmodels.ArticleViewModel.FullTextState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A full-text state belongs to an article, and says which.
 *
 * ArticleViewModel is scoped to the activity — koinNeoViewModel does that on
 * purpose, so screens share one instance across navigation — which means every
 * article the reader opens uses the same one. During a navigation transition
 * the outgoing and incoming pages are composed against it simultaneously.
 *
 * A bare state cannot survive that, in either direction:
 *
 *  - the outgoing page's disposal calls resetFullText, and it can land after
 *    the incoming page reported Ready, so the new article renders the
 *    paragraph the feed sent instead of the page just fetched for it;
 *  - a Ready left by the previous article, read by the next before its own
 *    file exists, shows "not found" for an article that is perfectly fine.
 *
 * Neither happens every time. They follow how quickly the database answered
 * and how long the transition ran, which is what made the reported symptom
 * "it doesn't do it every time" rather than a reproducible fault.
 *
 * With the id attached, a state belonging to another article is ignored and
 * no ordering between the two pages can be wrong.
 */
class FullTextOwnershipTest {

    private val viewModel = File(
        "src/main/java/com/saulhdev/feeder/viewmodels/ArticleViewModel.kt"
    ).readText()
    private val page = File(
        "src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt"
    ).readText()

    @Test
    fun `the state carries the article it describes`() {
        assertEquals("", FullTextState().articleId)
        assertEquals(FullText.Absent, FullTextState().state)
        assertEquals("a", FullTextState("a", FullText.Ready).articleId)
    }

    @Test
    fun `a reset only applies to the article it was asked for`() {
        val at = viewModel.indexOf("fun resetFullText(")
        assertTrue("resetFullText no longer takes the article it is resetting", at > 0)
        val body = viewModel.substring(at, viewModel.indexOf("\n    }", at))
        assertTrue(
            "a disposal can clear the state of the article now being read",
            body.contains("if (_fullText.value.articleId != id) return"),
        )
    }

    @Test
    fun `a finished fetch does not report onto another article`() {
        // A fetch takes seconds, which is long enough for the reader to have
        // moved on twice.
        val at = viewModel.indexOf("fun loadFullText(")
        val body = viewModel.substring(at, viewModel.indexOf("\n    }", at))
        val guard = body.indexOf("if (_fullText.value.articleId != id) return@launch")
        val report = body.indexOf("FullText.Ready else FullText.Failed")
        assertTrue("a slow fetch reports onto whichever article is open now", guard > 0)
        assertTrue("the guard comes after the report", guard < report)
    }

    @Test
    fun `every state the page reads is matched to the article on screen`() {
        // Both the full-article switch and the progress bar. A spinner left by
        // the previous article is a spinner that never finishes.
        val reads = Regex("""fullText\.state ==""").findAll(page).count()
        val matched = Regex("""fullText\.articleId == articleId""").findAll(page).count()
        assertTrue("the page stopped reading the full-text state", reads > 0)
        assertEquals(
            "a full-text state is read without checking which article it is about",
            reads,
            matched,
        )
    }

    @Test
    fun `the disposal names the article it is disposing`() {
        assertTrue(
            "the page resets the full-text state without saying which article",
            page.contains("viewModel.resetFullText(articleId)"),
        )
    }
}
