package com.saulhdev.feeder

import com.saulhdev.feeder.utils.readerBody
import com.saulhdev.feeder.utils.restoreLazyImages
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class LazyImageTest {

    private fun restored(html: String) =
        Jsoup.parse(html, "https://example.com/story").body().also(::restoreLazyImages).selectFirst("img")!!

    @Test
    fun `IEEE Spectrum's stand-in gives way to the real address`() {
        val img = restored(
            "<p><img alt=\"Danfoss drives\" data-runner-src=\"https://spectrum.ieee.org/media-library/" +
                "danfoss.jpg?id=68442949&amp;width=980\" lazy-loadable=\"true\" " +
                "src=\"data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%3E%3C/svg%3E\"></p>"
        )
        assertEquals("https://spectrum.ieee.org/media-library/danfoss.jpg?id=68442949&width=980", img.attr("src"))
    }

    @Test
    fun `SoraNews24's picture and its sizes come back`() {
        val img = restored(
            "<img src=\"data:image/svg+xml;base64,PHN2Zy8+\" " +
                "data-sco-src=\"https://soranews24.com/uploads/kachan3.jpg?w=640\" " +
                "data-sco-srcset=\"https://soranews24.com/uploads/kachan3.jpg 1300w, " +
                "https://soranews24.com/uploads/kachan3.jpg?resize=150,113 150w\">"
        )
        assertEquals("https://soranews24.com/uploads/kachan3.jpg?w=640", img.attr("src"))
        assertEquals(true, img.attr("srcset").startsWith("https://soranews24.com/uploads/kachan3.jpg 1300w"))
    }

    @Test
    fun `a picture with no src at all takes data-src`() {
        assertEquals("/a.jpg", restored("<img data-src=\"/a.jpg\">").attr("src"))
    }

    @Test
    fun `a real src is left alone`() {
        val img = restored("<img src=\"https://example.com/a.jpg\" data-src=\"https://example.com/b.jpg\">")
        assertEquals("https://example.com/a.jpg", img.attr("src"))
    }

    @Test
    fun `a data attribute that is not an address is ignored`() {
        val img = restored("<img src=\"data:image/gif;base64,R0lGOD\" data-src=\"placeholder\">")
        assertEquals("data:image/gif;base64,R0lGOD", img.attr("src"))
    }

    @Test
    fun `the reader shows the restored picture`() {
        val reader = readerBody(
            // Two full paragraphs first, so the picture is inside the article
            // rather than its opening one, which goes to the top.
            ("<p>${"Opening paragraph of the story. ".repeat(6)}</p><p>${"More of it. ".repeat(12)}</p>" +
                "<p><img src=\"data:image/svg+xml;base64,PHN2Zy8+\" data-lazy-src=\"https://example.com/inside.jpg\"></p>")
                .byteInputStream(),
            "https://example.com/story",
            leadImageUrl = "https://example.com/card.jpg",
        )
        assertEquals("https://example.com/card.jpg", reader.leadImage)
        assertEquals("https://example.com/inside.jpg", reader.body.selectFirst("img")?.attr("src"))
    }
}
