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
}
