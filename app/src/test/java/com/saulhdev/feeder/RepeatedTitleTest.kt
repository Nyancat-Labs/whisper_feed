package com.saulhdev.feeder

import com.saulhdev.feeder.utils.normalizedHeading
import com.saulhdev.feeder.utils.stripRepeatedTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup

/**
 * The reader draws an article's headline once.
 *
 * It draws the title itself, above the source and the byline, from the feed.
 * Plenty of publishers also open the article body with the same words in an
 * `h1`, and Readability keeps it — so BGR, SlashGear and Engadget all arrived
 * with their headline twice, set two different ways, one after the other.
 */
class RepeatedTitleTest {

    private fun strip(html: String, title: String?): String {
        val body = Jsoup.parse(html).body()
        stripRepeatedTitle(body, title)
        return body.html()
    }

    @Test
    fun `an opening heading that repeats the title is removed`() {
        val out = strip(
            "<h1>5 Earbuds Cheaper Than The AirPods</h1><p>Apple recently…</p>",
            "5 Earbuds Cheaper Than The AirPods",
        )
        assertTrue("the duplicate heading is gone", !out.contains("<h1>"))
        assertTrue("the article itself is not", out.contains("Apple recently"))
    }

    @Test
    fun `typographic differences do not save the duplicate`() {
        // The feed's title and the page's own rarely match byte for byte.
        val out = strip(
            "<h1>Xbox’s future — barely Xbox anymore.</h1><p>Body.</p>",
            "Xbox's future - barely Xbox anymore",
        )
        assertTrue("normalised comparison catches it", !out.contains("<h1>"))
    }

    @Test
    fun `a heading further down is a real section and stays`() {
        val out = strip(
            "<p>Intro.</p><h2>The Best Projectors</h2><p>More.</p>",
            "The Best Projectors",
        )
        assertTrue("only the opening block is considered", out.contains("<h2>"))
    }

    @Test
    fun `a different heading stays`() {
        val out = strip(
            "<h1>What you need to know</h1><p>Body.</p>",
            "Google's anti-motion sickness feature is rolling out",
        )
        assertTrue(out.contains("<h1>"))
    }

    @Test
    fun `no title means nothing is removed`() {
        val out = strip("<h1>Anything</h1><p>Body.</p>", null)
        assertTrue(out.contains("<h1>"))
        assertTrue(strip("<h1>Anything</h1>", "   ").contains("<h1>"))
    }

    @Test
    fun `a heading nested in the body's first wrapper is still the opening one`() {
        val out = strip(
            "<div class=\"page\"><header><h1>Titled</h1></header><p>Body.</p></div>",
            "Titled",
        )
        assertTrue(!out.contains("<h1>"))
    }

    @Test
    fun `normalisation folds quotes dashes spaces and trailing punctuation`() {
        assertEquals(
            normalizedHeading("A Title — Here."),
            normalizedHeading("a title - here"),
        )
    }
}
