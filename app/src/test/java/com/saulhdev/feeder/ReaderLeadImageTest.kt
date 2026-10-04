/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import com.saulhdev.feeder.utils.liftLeadImage
import com.saulhdev.feeder.utils.readerBody
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The reader carries the cards' style: the article's picture first and edge
 * to edge, then the headline, then the card's own byline. So the opening
 * picture comes out of the body to go on top, and is never drawn twice.
 */
class ReaderLeadImageTest {

    private val base = "https://example.org/story"
    private fun body(html: String) = Jsoup.parse(html, base).body()
    private fun read(html: String, card: String? = null) =
        readerBody(html.byteInputStream(), base, articleTitle = "A headline", leadImageUrl = card)

    @Test
    fun `the opening picture goes on top, with its figure and caption`() {
        val b = body("""<figure><img src="https://cdn.example.org/lead.jpg"><figcaption>Photo: someone</figcaption></figure><p>Text.</p>""")
        assertEquals("https://cdn.example.org/lead.jpg", liftLeadImage(b))
        assertTrue(b.select("img, figure, figcaption").isEmpty())
        assertEquals("Text.", b.text())
    }

    @Test
    fun `a picture after the opening paragraphs stays in the article`() {
        val long = "This paragraph is long enough to count as the article's text and not a dek."
        val b = body("<p>$long</p><p>$long</p><img src=\"https://cdn.example.org/later.jpg\">")
        assertNull(liftLeadImage(b))
        assertEquals(1, b.select("img").size)
    }

    @Test
    fun `an address the app would not load is left where it is`() {
        val b = body("""<img src="data:image/png;base64,AAAA"><p>Text.</p>""")
        assertNull(liftLeadImage(b))
        assertEquals(1, b.select("img").size)
    }

    @Test
    fun `with no picture of its own, the card's goes on top`() {
        val article = read("<p>Only text.</p>", card = "https://cdn.example.org/card.jpg")
        assertEquals("https://cdn.example.org/card.jpg", article.leadImage)
    }

    @Test
    fun `the card's picture at another size is one picture, drawn once`() {
        val article = read(
            """<img src="https://cdn.example.org/lead.jpg?w=1200"><p>Text.</p><p><img src="https://cdn.example.org/lead.jpg?w=400"></p>""",
            card = "https://cdn.example.org/lead.jpg?w=560",
        )
        assertTrue(article.leadImage!!.startsWith("https://cdn.example.org/lead.jpg"))
        assertTrue("not again in the article", article.body.select("img").isEmpty())
        assertEquals("Text.", article.body.text())
    }

    @Test
    fun `no picture anywhere, none on top`() {
        assertNull(read("<p>Only text.</p>").leadImage)
    }

    @Test
    fun `the reader draws the picture first, then the headline and the card's byline`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt").readText()
        val picture = page.indexOf("article?.leadImage?.let { picture ->")
        val headline = page.indexOf("text = title,")
        val byline = page.indexOf("ArticleMeta(")
        assertTrue(picture in 1 until headline && headline < byline)
        assertTrue(page.contains(".bleed(CARD_MARGIN)"))
        assertTrue(page.contains("fontWeight = FontWeight.Medium,"))
        assertTrue("the date line is quiet", page.contains("style = MaterialTheme.typography.bodySmall,\n                                color = MaterialTheme.colorScheme.onSurfaceVariant,"))
        assertTrue("the body is read once, for both", page.contains("readerBodyText(\n                        article = article,"))
        assertFalse(page.contains("htmlFormattedText("))
    }
}
