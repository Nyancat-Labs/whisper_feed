package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ensureLeadImage
import com.saulhdev.feeder.utils.namesItselfByline
import com.saulhdev.feeder.utils.opensWithImage
import com.saulhdev.feeder.utils.stripPageChrome
import com.saulhdev.feeder.utils.stripRepeatedTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup
import java.io.File

/**
 * The reader shows each thing once.
 *
 * Measured, not guessed: six real articles - How-To Geek, BGR, Engadget,
 * SlashGear, Android Authority, The Verge - were run through the reader's own
 * extraction and every block that survived was listed. The duplicates came
 * from four places, one of them the reader itself. The markup below is
 * written for these tests in the shape those pages use; none of it is copied
 * from them.
 */
class ReaderDuplicationTest {

    private fun page(body: String) = Jsoup.parse("<html><body>$body</body></html>", "https://example.com/a")

    // --- 1. the lead image the reader was adding twice ---------------------

    @Test
    fun `a body that opens with its own picture is not given the feed's`() {
        // BGR's shape: the feed offers l-intro-123, the body opens with intro-123.
        val body = page(
            "<h1>Title</h1><img src=\"https://cdn.example/intro-123.jpg\" width=\"780\" height=\"438\">" +
                "<p>${"The world has been getting hotter for decades. ".repeat(3)}</p>",
        ).body()
        ensureLeadImage(body, "https://cdn.example/l-intro-123.jpg")
        assertEquals(listOf("https://cdn.example/intro-123.jpg"), body.select("img").map { it.attr("src") })
    }

    @Test
    fun `a dek above the lead picture does not hide it`() {
        // Engadget's shape: headline, one line of dek, then the photograph.
        val body = page(
            "<p>It will offer several privacy-focused options for users who are not confirmed.</p>" +
                "<img src=\"https://cdn.example/intro-9.jpg\" width=\"780\" height=\"438\">" +
                "<p>${"Many platforms have been applying new rules. ".repeat(3)}</p>",
        ).body()
        assertTrue(opensWithImage(body))
    }

    @Test
    fun `an avatar at the top is not a lead picture`() {
        val body = page(
            "<img src=\"https://cdn.example/Photo.jpg\" width=\"90\" height=\"90\">" +
                "<p>${"The article begins here and goes on. ".repeat(3)}</p>",
        ).body()
        assertFalse(opensWithImage(body))
    }

    @Test
    fun `a body with no picture before the story still gets one`() {
        // The Verge's shape, which is why this function exists.
        val body = page("<p>${"Microsoft is laying off around 260 employees. ".repeat(3)}</p><p>More.</p>").body()
        ensureLeadImage(body, "https://cdn.example/hero.png")
        assertEquals("https://cdn.example/hero.png", body.selectFirst("img")!!.attr("src"))
    }

    // --- 2. the headline, with the page's site name on it -------------------

    @Test
    fun `a headline is recognised with or without the site's name`() {
        val body = page("<h1>The MacBook Neo Is Repairable</h1><p>Body text.</p>").body()
        stripRepeatedTitle(body, "The MacBook Neo Is Repairable - SlashGear")
        assertTrue(body.select("h1").isEmpty())

        val other = page("<h1>The MacBook Neo Is Repairable - SlashGear</h1><p>Body.</p>").body()
        stripRepeatedTitle(other, "The MacBook Neo Is Repairable")
        assertTrue(other.select("h1").isEmpty())
    }

    @Test
    fun `a hyphen inside the headline is not a site name`() {
        val body = page("<h1>Anti-motion sickness arrives</h1><p>x</p>").body()
        stripRepeatedTitle(body, "Anti-motion sickness")
        assertEquals("different headline, kept", 1, body.select("h1").size)
    }

    // --- 3. the page's own furniture, taken out before extraction ------------

    @Test
    fun `byline blocks go, by what they call themselves`() {
        val doc = page(
            "<div class=\"article-header-author-img\"><img src=\"a.jpg\"></div>" +
                "<div class=\"w-author\"><a>Goran</a></div>" +
                "<div class=\"duet--article--article-byline _19wv\">Tom Warren</div>" +
                "<p>${"The article itself. ".repeat(40)}</p>",
        )
        stripPageChrome(doc)
        assertEquals(1, doc.select("p").size)
        assertTrue(doc.select("img").isEmpty())
        assertFalse(doc.text().contains("Tom Warren"))
    }

    @Test
    fun `a byline word inside another word is not a byline`() {
        assertFalse(namesItselfByline(Jsoup.parse("<div class=\"authority-brand\"></div>").selectFirst("div")!!))
        assertFalse(namesItselfByline(Jsoup.parse("<div class=\"coauthored\"></div>").selectFirst("div")!!))
        assertTrue(namesItselfByline(Jsoup.parse("<div id=\"x-author_byline_lead\"></div>").selectFirst("div")!!))
    }

    @Test
    fun `text the publisher marks not-the-article goes`() {
        // How-To Geek's biography: no author label, but marked data-nosnippet.
        val doc = page(
            "<div class=\"with-excerpt\" data-nosnippet><p>Ever since he got his first phone...</p></div>" +
                "<p>${"The article itself. ".repeat(40)}</p>",
        )
        stripPageChrome(doc)
        assertFalse(doc.text().contains("Ever since"))
    }

    @Test
    fun `asides go - follow widgets and pull quotes repeat what is already there`() {
        val doc = page(
            "<p>${"The article itself. ".repeat(40)}</p>" +
                "<aside><p>Posts from this topic will be added to your daily email digest.</p></aside>",
        )
        stripPageChrome(doc)
        assertFalse(doc.text().contains("Posts from this topic"))
    }

    @Test
    fun `a label on the whole article removes the label, not the article`() {
        val doc = page("<div class=\"post author-jane\"><p>${"Every word of the story. ".repeat(40)}</p></div>")
        stripPageChrome(doc)
        assertTrue("the article survives its theme's class names", doc.text().contains("Every word"))
    }

    @Test
    fun `extraction runs on the cleaned page`() {
        val parser = File("src/main/java/com/saulhdev/feeder/manager/models/FullTextParser.kt").readText()
        // Parsed from bytes in the page's own encoding since B6; still cleaned
        // before anything is extracted.
        assertTrue(parser.contains("val page = pageDocument(fetched, url).also(::stripPageChrome)"))
        assertTrue(parser.contains("Readability4JExtended(url, page)"))
    }

    // --- 4. captions ---------------------------------------------------------

    /**
     * Alt text describes the picture for someone who cannot see it. Drawn
     * under the picture it said the photograph again in words, and then the
     * page's real caption followed - two captions on most images.
     */
    @Test
    fun `alt text is read aloud, not drawn`() {
        val html = File("src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt").readText()
        val image = html.substringAfter("private fun TextComposer.handleImage(")
            .substringBefore("private fun TextComposer.handleTable(")
        assertTrue("still the picture's description", image.contains("contentDescription = alt"))
        assertFalse("but no longer a caption", Regex("""Text\(\s*alt""").containsMatchIn(image))
        assertTrue(
            "the page's own caption is set as one",
            html.contains("\"figcaption\"             -> {"),
        )
    }
}
