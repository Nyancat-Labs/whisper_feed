package com.saulhdev.feeder

import com.saulhdev.feeder.utils.stripPageChrome
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitArticleTest {

    private fun paragraphs(prefix: String, count: Int) =
        (1..count).joinToString("") { "<p>$prefix paragraph $it, long enough to read as the article's own prose and not a caption.</p>" }

    /** Ars Technica: the article in two blocks, each in its own wrapper, an advert between. */
    private val ars = """
        <html><body><header><h1>Anthropic gives outside AI watchdog access</h1></header>
        <div class="grid"><div class="relative"><div class="post-content post-content-double">
        ${paragraphs("Opening", 8)}</div></div></div>
        <div class="ad-wrapper"><div class="ad ad--mid-content"></div></div>
        <div class="grid"><div class="relative"><div class="post-content post-content-double">
        ${paragraphs("Later", 14)}</div></div></div>
        <footer><p>Comments and more stories.</p></footer></body></html>
    """.trimIndent()

    @Test
    fun `an article split around its adverts is read whole`() {
        val document = Jsoup.parse(ars, "https://arstechnica.com/ai/2026/10/watchdog/")
        stripPageChrome(document)
        val text = Readability4JExtended("https://arstechnica.com/ai/2026/10/watchdog/", document).parse().textContent.orEmpty()
        assertTrue("the opening survives", text.contains("Opening paragraph 1,"))
        assertTrue("and the rest", text.contains("Later paragraph 14,"))
        assertTrue("in page order", text.indexOf("Opening paragraph 8,") < text.indexOf("Later paragraph 1,"))
    }

    @Test
    fun `only blocks named as the article's body are joined`() {
        val document = Jsoup.parse(
            "<html><body><div class=\"card\"><p>One.</p></div><div class=\"card\"><p>Two.</p></div></body></html>",
        )
        stripPageChrome(document)
        assertEquals(2, document.select("div.card").size)
    }

    @Test
    fun `a body block inside another is not moved`() {
        val document = Jsoup.parse(
            "<html><body><div class=\"entry-content\"><p>Outer.</p><div class=\"entry-content\"><p>Inner.</p></div></div></body></html>",
        )
        stripPageChrome(document)
        assertEquals("Outer. Inner.", document.select("div.entry-content").first()!!.text())
        assertEquals(2, document.select("div.entry-content").size)
    }
}
