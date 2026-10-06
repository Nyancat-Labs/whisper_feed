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

import com.saulhdev.feeder.manager.models.escapingBareAmpersands
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.xml.parsers.SAXParserFactory

private fun fix(s: String) = escapingBareAmpersands(s.byteInputStream()).bufferedReader().readText()

/** Whether a strict parser will actually accept it, which is the real question. */
private fun parses(xml: String): Boolean = runCatching {
    SAXParserFactory.newInstance().newSAXParser()
        .parse(escapingBareAmpersands(xml.byteInputStream()), org.xml.sax.helpers.DefaultHandler())
}.isSuccess

/**
 * Real OPML is full of bare ampersands, and SAX rejects the whole document
 * for one of them. Thirteen of twenty-four country files in one public feed
 * directory fail this way — a subscription list refused over a showbiz
 * headline containing "&".
 */
class OpmlAmpersandTest {

    @Test
    fun `a bare ampersand is escaped`() {
        assertEquals("""<a t="showbiz &amp; celebrity"/>""", fix("""<a t="showbiz & celebrity"/>"""))
    }

    /** Escaping an existing entity would turn `&amp;` into a visible "&amp;". */
    @Test
    fun `entities that are already correct are left alone`() {
        assertEquals("""<a t="rock &amp; roll"/>""", fix("""<a t="rock &amp; roll"/>"""))
        assertEquals("""<a t="&lt;tag&gt;"/>""", fix("""<a t="&lt;tag&gt;"/>"""))
        assertEquals("""<a t="&#169; &#x41;"/>""", fix("""<a t="&#169; &#x41;"/>"""))
    }

    @Test
    fun `a document with a bare ampersand parses afterwards`() {
        val opml = """<?xml version="1.0"?><opml><body>""" +
            """<outline title="World & Nation" xmlUrl="https://e.com/f"/>""" +
            """</body></opml>"""
        assertEquals(true, parses(opml))
    }

    @Test
    fun `several on one line are all escaped`() {
        assertEquals(
            """<a t="a &amp; b &amp; c" u="x?p=1&amp;q=2"/>""",
            fix("""<a t="a & b & c" u="x?p=1&q=2"/>"""),
        )
    }

    /**
     * An ampersand in a query string is the commonest of all, and losing it
     * would point a subscription at a different feed rather than a broken one.
     */
    @Test
    fun `a query string survives the round trip intact`() {
        val fixed = fix("""<a xmlUrl="https://e.com/f?id=1&alt=rss"/>""")
        assertEquals("""<a xmlUrl="https://e.com/f?id=1&amp;alt=rss"/>""", fixed)
    }

    @Test
    fun `a file with nothing to fix is unchanged`() {
        val clean = """<?xml version="1.0"?><opml><body><outline title="BBC"/></body></opml>"""
        assertEquals(clean, fix(clean))
    }
}
