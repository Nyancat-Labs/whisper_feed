package com.saulhdev.feeder

import com.saulhdev.feeder.utils.stripPageChrome
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoscriptImageTest {

    private val story = "<p>${"Humpbacks and other baleen whales feed on small crustaceans and fish. ".repeat(8)}</p>"

    /** Inverse's in-article picture: a script-drawn stand-in, and the real one only in noscript. */
    private val inverse = """
        <html><body><article><h1>Can you be swallowed by a whale?</h1>$story
        <figure><div><style>.s-1{padding-top:63%}</style>
        <noscript><picture><source srcset="https://imgix.bustle.com/whale.jpg?w=825">
        <img alt="A breaching humpback" src="https://imgix.bustle.com/whale.jpg?w=374"></picture></noscript>
        <img src="data:image/gif;base64,R0lGODlhAQABAAAAACw=" alt=""></div>
        <figcaption><p>Try not to be jealous.</p><cite>John Wells/Solent News/Shutterstock</cite></figcaption>
        </figure>$story</article></body></html>
    """.trimIndent()

    @Test
    fun `a picture only in noscript survives extraction`() {
        val document = Jsoup.parse(inverse, "https://www.inverse.com/science/whale")
        stripPageChrome(document)
        val content = Readability4JExtended("https://www.inverse.com/science/whale", document).parse().content.orEmpty()
        val images = Jsoup.parse(content).select("img")
        assertEquals("the real picture, and not the blank stand-in", 1, images.size)
        assertTrue(images.first()!!.attr("src").startsWith("https://imgix.bustle.com/whale.jpg"))
    }

    @Test
    fun `a noscript copy beside a working picture changes nothing`() {
        val document = Jsoup.parse(
            "<html><body><figure><img src=\"https://example.com/a.jpg\">" +
                "<noscript><img src=\"https://example.com/a.jpg\"></noscript></figure></body></html>",
            "https://example.com/",
        )
        stripPageChrome(document)
        assertEquals(1, document.select("img").count { it.parents().none { p -> p.tagName() == "noscript" } })
    }

    @Test
    fun `a noscript without a picture is left to Readability`() {
        val document = Jsoup.parse(
            "<html><body><p>Text.</p><noscript><p>Please turn on JavaScript.</p></noscript></body></html>",
            "https://example.com/",
        )
        stripPageChrome(document)
        assertEquals(1, document.select("noscript").size)
    }
}
