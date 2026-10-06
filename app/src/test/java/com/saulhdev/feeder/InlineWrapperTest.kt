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

import com.saulhdev.feeder.utils.stripPageChrome
import net.dankito.readability4j.extended.Readability4JExtended
import org.jsoup.Jsoup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Words inside a sentence survive extraction, whatever their wrapper is called.
 *
 * Investing.com wraps every ticker in `span.aqPopupWrapper`, for its hover
 * card. Readability drops any element whose class says "popup" as clutter, so
 * "Goosehead Insurance Inc. (NASDAQ:GSHD) to Neutral" reached the reader as
 * "Goosehead Insurance Inc. () to Neutral".
 */
class InlineWrapperTest {

    private val url = "https://www.investing.com/news/stock-market-news/example"

    private fun extract(paragraph: String): String {
        val filler = (1..6).joinToString("") {
            "<p>The firm said the main question for investors heading into the quarter is " +
                "whether guidance for revenue growth holds, and analysts expect a steady year.</p>"
        }
        val html = """
            <html><body>
              <div class="site-menu"><a href="/">Home</a> <a href="/news">News</a></div>
              <article><h1>Piper Sandler cuts rating</h1>$paragraph$filler</article>
              <div class="footer">Copyright</div>
            </body></html>
        """.trimIndent()
        val doc = Jsoup.parse(html, url).also(::stripPageChrome)
        return Readability4JExtended(url, doc).parse().textContent.orEmpty()
    }

    @Test
    fun `a ticker in a popup wrapper keeps its symbol`() {
        val text = extract(
            "<p>Piper Sandler downgraded Goosehead Insurance Inc. (</span>" +
                "<span class=\"aqPopupWrapper js-hover-me-wrapper\"><a href=\"https://www.investing.com/equities/goosehead\" " +
                "class=\"aqlink js-hover-me\" hoverme=\"aql\" data-pairid=\"1\">NASDAQ:GSHD</a></span><span>) " +
                "to Neutral from Overweight on Tuesday.</p>"
        )
        assertTrue(text, text.contains("(NASDAQ:GSHD)"))
        assertFalse(text, text.contains("()"))
    }

    @Test
    fun `other clutter-sounding words inside a sentence stay too`() {
        val text = extract(
            "<p>Shares rose after the <span class=\"share-link\">sharing economy</span> report and " +
                "a <span class=\"sidebar-note\">sidebar</span> remark from the chief executive.</p>"
        )
        assertTrue(text, text.contains("sharing economy"))
        assertTrue(text, text.contains("a sidebar remark"))
    }

    @Test
    fun `page furniture outside the article still goes`() {
        val text = extract("<p>Plain opening paragraph about the downgrade and the new price target.</p>")
        assertFalse(text, text.contains("Copyright"))
    }
}
