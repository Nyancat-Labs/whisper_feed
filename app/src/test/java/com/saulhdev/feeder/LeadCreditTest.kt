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

import com.saulhdev.feeder.data.db.models.authorName
import com.saulhdev.feeder.utils.readerBody
import com.saulhdev.feeder.utils.stripPageChrome
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Two things the reader showed on 18 October 2026, on an Engadget article. */
class LeadCreditTest {

    private fun body(html: String, card: String? = null) =
        readerBody(html.byteInputStream(), "https://www.engadget.com/a/", leadImageUrl = card)

    /** Engadget's lead picture, as the page has it: no figure, the credit beside it. */
    private val engadget = """
        <p class="subtitle">Roku's remote is straightforward, except for its multi-purpose star button.</p>
        <div class="image-holder credit-image-wrap lead-image-holder">
          <picture><img src="https://www.engadget.com/img/intro-1791990292.jpg" width="780" height="438"></picture>
          <span class="gallery-image-credit">Joshua Westbrook/Getty Images</span>
        </div>
        <p>A Roku system's remote control is one of the most straightforward remote designs you'll find.</p>
    """

    @Test
    fun `the picture's credit goes with the picture`() {
        val lifted = body(engadget)
        assertEquals("https://www.engadget.com/img/intro-1791990292.jpg", lifted.leadImage)
        val text = lifted.body.text()
        assertFalse(text, text.contains("Getty Images"))
        assertTrue(text.contains("A Roku system's remote"))
        assertTrue(text.contains("multi-purpose star button"))
    }

    @Test
    fun `and when the card's picture is on top instead`() {
        val text = body(engadget, card = "https://www.engadget.com/img/l-intro-1791990292.jpg").body.text()
        assertFalse(text, text.contains("Getty Images"))
    }

    @Test
    fun `a line of the article's own beside a picture stays`() {
        val text = body("""
            <div><img src="https://example.org/a.jpg" width="800" height="450"> The vote was close.</div>
            <p>More text that makes this an article rather than a caption, at some length.</p>
        """).body.text()
        assertTrue(text, text.contains("The vote was close."))
    }

    @Test
    fun `an RSS author's address gives way to the name`() {
        assertEquals("Deven McClure", authorName("staff@engadget.com (Deven McClure)"))
        assertEquals("Zhiye Liu", authorName("Zhiye Liu "))
        assertEquals("staff@example.org", authorName("staff@example.org"))
        assertEquals(null, authorName(" "))
    }

    /** The page's picture and credit, told apart before extraction can drop their classes. */
    @Test
    fun `cleaning the page makes a picture and its credit a figure`() {
        val page = Jsoup.parse("""<html><body>
            <div class="image-holder credit-image-wrap lead-image-holder">
              <picture><img src="https://www.engadget.com/img/intro-1791990292.jpg"></picture>
              <span class="gallery-image-credit">Joshua Westbrook/Getty Images</span>
            </div><p>Text.</p></body></html>""", "https://www.engadget.com/a/").also(::stripPageChrome)
        assertEquals("Joshua Westbrook/Getty Images", page.select("figure > figcaption").text())
        assertEquals(1, page.select("figure img").size)
    }

    /**
     * What extraction made of the real Engadget page on 18 October, cut down:
     * the figure and figcaption came through, and the reader takes both.
     */
    @Test
    fun `the stored figure leaves the reader whole`() {
        val stored = """<div><figure>
              <picture><img src="https://www.engadget.com/img/gallery/roku/intro-1791990292.jpg" width="780" height="438"></picture>
              <figcaption>Joshua Westbrook/Getty Images</figcaption>
            </figure>
            <div><p dir="ltr">A Roku system's remote control is one of the most straightforward remote designs you'll find.</p></div></div>"""
        val text = body(stored, card = "https://www.engadget.com/img/gallery/roku/l-intro-1791990292.jpg").body.text()
        assertFalse(text, text.contains("Westbrook"))
        assertTrue(text.contains("A Roku system's remote"))
    }

    /** A wrapper with article text in it is not a figure, whatever it holds. */
    @Test
    fun `a wrapper of article text is left as it is`() {
        val page = Jsoup.parse("""<html><body><div class="content">
            <img src="https://example.org/a.jpg"><span class="credit">Someone/Agency</span>
            <p>The article's own paragraph.</p></div></body></html>""", "https://example.org/").also(::stripPageChrome)
        assertTrue(page.select("figure").isEmpty())
    }

    /** A picture further down keeps its credit, now a caption under it. */
    @Test
    fun `a later picture keeps its credit`() {
        val url = "https://www.engadget.com/a/"
        val page = Jsoup.parse("""<html><body>
            <p>Opening paragraph of the article, long enough to count as text before any picture.</p>
            <p>A second paragraph, so the picture below is not the article's opening one.</p>
            <div class="image-holder"><picture><img src="https://www.engadget.com/img/slide-2.jpg" width="780" height="438"></picture>
              <span class="gallery-image-credit">Delbert Harper/Getty Images</span></div>
            <p>More of the article.</p></body></html>""", url).also(::stripPageChrome)
        val text = readerBody(page.outerHtml().byteInputStream(), url).body.text()
        assertTrue(text, text.contains("Delbert Harper/Getty Images"))
    }

}
