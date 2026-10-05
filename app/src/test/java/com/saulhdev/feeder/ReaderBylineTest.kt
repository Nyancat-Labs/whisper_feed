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

import com.saulhdev.feeder.utils.isBylineNoise
import com.saulhdev.feeder.utils.readerBody
import com.saulhdev.feeder.utils.stripBylineNoise
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 4 October: an Investing.com article opened, under the reader's own byline,
 * with "By", "Published 10/04/2026, 08:15 AM" and "Updated …" - the page's
 * byline with its author's name gone, and its dates said a second time.
 */
class ReaderBylineTest {

    private fun cleaned(html: String, author: String? = null): String {
        val body = Jsoup.parse(html).body()
        stripBylineNoise(body, author)
        return body.text()
    }

    private val story = "<p>Spain and Morocco reopened the border crossing at Ceuta on Saturday.</p>"

    @Test
    fun `the Investing com opening goes, the story stays`() {
        val html = "<div><span>By</span></div>" +
            "<div><span>Published 10/04/2026, 08:15 AM</span> <span>Updated 10/04/2026, 09:10 AM</span></div>" +
            story
        assertEquals("Spain and Morocco reopened the border crossing at Ceuta on Saturday.", cleaned(html))
    }

    @Test
    fun `the same through readerBody`() {
        val html = "<div>By</div><div>Published 10/04/2026, 08:15 AM Updated 10/04/2026, 09:10 AM</div>$story"
        val body = readerBody(html.byteInputStream(), "https://www.investing.com/news/x", articleTitle = "Ceuta")
        assertTrue(body.body.text().startsWith("Spain and Morocco"))
    }

    @Test
    fun `the author's own name goes only when it is the article's author`() {
        assertEquals(cleaned(story), cleaned("<p>By Reuters</p>$story", author = "Reuters"))
        assertTrue(cleaned("<p>By Reuters</p>$story").startsWith("By Reuters"))
    }

    @Test
    fun `date stamps in the forms publishers use`() {
        listOf(
            "Published 10/04/2026, 08:15 AM",
            "Updated: Oct 4, 2026 9:10AM ET",
            "Last updated on 4 October 2026 at 09:10",
            "Posted 2 hours ago",
            "By | Published 10/04/2026 | Updated 10/04/2026",
        ).forEach { assertTrue(it, isBylineNoise(it)) }
    }

    @Test
    fun `lines that only begin like one are left alone`() {
        listOf(
            "Published in 2019 by Penguin, the book sold a million copies.",
            "Updated figures released on Friday show inflation at 2.1%.",
            "By the end of 2026 the port will have doubled in size.",
            "Posted",
        ).forEach { assertFalse(it, isBylineNoise(it)) }
    }

    @Test
    fun `only the opening lines are looked at`() {
        val html = "$story<p>Updated 10/04/2026, 09:10 AM</p>"
        assertTrue(cleaned(html).endsWith("Updated 10/04/2026, 09:10 AM"))
    }

    @Test
    fun `a line with a picture in it ends the search`() {
        val html = "<div><img src=\"https://example.com/a.jpg\"> By</div><p>Published 10/04/2026</p>$story"
        assertTrue(cleaned(html).contains("Published 10/04/2026"))
    }
}
