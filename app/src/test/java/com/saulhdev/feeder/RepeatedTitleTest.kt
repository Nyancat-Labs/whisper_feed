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
    fun `a kicker and a date above the headline go with it`() {
        // TechCrunch, as Readability leaves it: loose text, then a date, then the title.
        val out = strip(
            "<div> In Brief <p>Posted:</p><p>2:31 PM PDT · October 17, 2026</p>" +
                "<h1>Meta removes language that raised scraping concerns</h1>" +
                "<p id=\"speakable-summary\">Meta recently updated its terms.</p></div>",
            "Meta removes language that raised scraping concerns",
        )
        assertTrue("the headline is gone", !out.contains("<h1>"))
        assertTrue("so is everything above it", !out.contains("In Brief") && !out.contains("Posted"))
        assertTrue("the article is not", out.contains("Meta recently updated"))
    }

    @Test
    fun `an affiliate notice above the headline goes with it`() {
        // TechHive.
        val out = strip(
            "<p>When you purchase through links in our articles, we may earn a small commission. " +
                "This doesn't affect our editorial independence.</p>" +
                "<h1>Dell’s business laptop drops from \$1,099 to \$265</h1><p>Sponsored DealPost</p><p>Body.</p>",
            "Dell's business laptop drops from \$1,099 to \$265",
        )
        assertTrue(!out.contains("<h1>") && !out.contains("commission"))
        assertTrue(out.contains("Body."))
    }

    @Test
    fun `a picture above the headline stays`() {
        val out = strip(
            "<p>France</p><figure><img src=\"https://example.com/a.jpg\"></figure>" +
                "<h1>Macron vows full transparency</h1><p>Shops were looted.</p>",
            "Macron vows full transparency",
        )
        assertTrue(!out.contains("<h1>") && !out.contains("France"))
        assertTrue("the picture is not text to take away", out.contains("a.jpg"))
    }

    @Test
    fun `a headline below the article's opening paragraph stays`() {
        val intro = "Close to one billion meetings take place each day around the world. ".repeat(5)
        val out = strip("<p>$intro</p><h1>Science says talk less</h1><p>More.</p>", "Science says talk less")
        assertTrue("more than a kicker above it: the article had started", out.contains("<h1>"))
        assertTrue(out.contains("Close to one billion"))
    }

    @Test
    fun `an opening paragraph that is only the headline goes`() {
        val title = "Clarence Seedorf calls out genocide in first Corinthians press conference"
        val out = strip("<div><p>$title</p></div><p>In his first press conference as coach…</p>", title)
        assertTrue(!out.contains("calls out genocide in first"))
        assertTrue(out.contains("In his first press conference"))
        // A paragraph that only starts like the headline is the article.
        assertTrue(strip("<p>$title, he said.</p>", title).contains("he said"))
    }

    @Test
    fun `normalisation folds quotes dashes spaces and trailing punctuation`() {
        assertEquals(
            normalizedHeading("A Title — Here."),
            normalizedHeading("a title - here"),
        )
    }
}
