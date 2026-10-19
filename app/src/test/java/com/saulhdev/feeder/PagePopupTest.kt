package com.saulhdev.feeder

import com.saulhdev.feeder.utils.stripPageChrome
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagePopupTest {

    private fun cleaned(html: String) = Jsoup.parse(html, "https://example.com/").also(::stripPageChrome).body()

    private val story = (1..4).joinToString("") { "<p>Paragraph $it of the story, long enough to be the article's own prose and nothing else.</p>" }

    @Test
    fun `pop-ups and notices go, by the names the page gave them`() {
        // The Independent's, as served.
        val text = cleaned(
            "<div id=\"success-hint-header\"><h3>Thank you for registering</h3></div>" +
                "<div class=\"modal-overlay\" id=\"mos-already-subscribed-modal\"><h1>You’re all set!</h1></div>" +
                "<div role=\"alertdialog\"><h2 id=\"popover-heading-:R1:\">Bookmark popover</h2>" +
                "<p id=\"popover-content-:R1:\">Removed from bookmarks</p></div>" +
                "<a href=\"#main-content\">Skip to content</a><h1>Homeowners told to do one thing</h1>$story"
        ).text()
        listOf("Thank you for registering", "You’re all set", "Bookmark popover", "Removed from bookmarks", "Skip to content")
            .forEach { assertFalse(it, text.contains(it)) }
        assertTrue(text.contains("Paragraph 4 of the story"))
    }

    @Test
    fun `a tip box styled as a hint stays, and so does an ordinary link`() {
        val body = cleaned(
            "<h1>How to clean a keyboard</h1><div class=\"hint\"><p>Unplug it first.</p></div>" +
                "<p><a href=\"#step-2\">Skip to step two</a></p>$story"
        )
        assertTrue(body.text().contains("Unplug it first."))
        assertTrue("only skips to the page's content go", body.text().contains("Skip to step two"))
    }

    @Test
    fun `one article holding the headline and a story is all that is kept`() {
        val body = cleaned(
            "<header><p>Sign in to your account</p></header>" +
                "<article><h1>The headline</h1>$story</article><footer><p>Newsletter sign-up</p></footer>"
        )
        assertEquals(1, body.children().size)
        assertFalse(body.text().contains("Sign in") || body.text().contains("Newsletter"))
    }

    @Test
    fun `a short post or a card is not narrowed to`() {
        // Cyberspace: three words under the headline. Readability keeps them
        // when the page is left whole, and lost them when narrowed.
        val post = cleaned("<nav><p>Home</p></nav><article><h1>tricky trees</h1><p>Go Not'tem Forest.</p></article><aside>x</aside>")
        assertTrue(post.text().contains("Home"))
        // The Independent: its one <article> is a related story's card, without the headline.
        val card = cleaned("<h1>The headline</h1>$story<article><h3>A related story</h3>$story</article>")
        assertTrue(card.text().contains("The headline"))
    }
}
