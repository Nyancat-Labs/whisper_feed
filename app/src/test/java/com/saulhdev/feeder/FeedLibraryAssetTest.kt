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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

private val LIBRARY = File("src/main/assets/library")

private class Collect : DefaultHandler() {
    val feeds = mutableListOf<Pair<String, String>>()
    override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) {
        if (l != "outline" && q != "outline") return
        val url = a?.getValue("xmlUrl") ?: return
        val title = a.getValue("title") ?: a.getValue("text") ?: return
        feeds += title to url
    }
}

private fun feedsIn(file: File): List<Pair<String, String>> {
    val h = Collect()
    file.inputStream().use { SAXParserFactory.newInstance().newSAXParser().parse(InputSource(it), h) }
    return h.feeds
}

/**
 * The bundled library, checked as the app will read it.
 *
 * These run against the shipped asset files with the same strict SAX parser
 * the app uses, because the failure being guarded against is not subtle and is
 * entirely silent: `FeedLibrary.feeds` catches everything and returns an empty
 * list, so a malformed pack is an empty screen with no error anywhere. A
 * build-time script said these were fine; a test says so on every build.
 */
class FeedLibraryAssetTest {

    /**
     * The manifest, read without `org.json`.
     *
     * Android stubs that class in unit tests — every method throws — so the
     * parser the app uses cannot be the parser the test uses. The file is
     * generated, flat and of a known shape, so a regex is honest here; the
     * emptiness check below is what stops a change in that shape passing
     * silently as "nothing to check".
     */
    private fun index(): List<Triple<String, String, Int>> {
        val text = File(LIBRARY, "index.json").readText()
        val entry = Regex(
            """\{[^{}]*"slug"\s*:\s*"([^"]+)"[^{}]*"kind"\s*:\s*"([^"]+)"[^{}]*"count"\s*:\s*(\d+)[^{}]*}"""
        )
        val rows = entry.findAll(text)
            .map { m -> Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt()) }
            .toList()
        assertTrue("the manifest parsed to nothing — has its shape changed?", rows.size > 50)
        return rows
    }

    @Test
    fun `every pack in the manifest exists and parses`() {
        index().forEach { (slug, _, _) ->
            val file = File(LIBRARY, "$slug.opml")
            assertTrue("missing pack: $slug", file.isFile)
            assertTrue("no feeds in $slug", feedsIn(file).isNotEmpty())
        }
    }

    /**
     * The count is printed on the row before anybody opens it, so a wrong one
     * is a promise the next screen breaks.
     */
    @Test
    fun `the manifest counts match the files`() {
        index().forEach { (slug, _, count) ->
            assertEquals("count wrong for $slug", count, feedsIn(File(LIBRARY, "$slug.opml")).size)
        }
    }

    /** Every pack file is listed; an unlisted one is dead weight in the APK. */
    @Test
    fun `no pack file is left out of the manifest`() {
        val listed = index().map { "${it.first}.opml" }.toSet()
        val onDisk = LIBRARY.listFiles { f -> f.extension == "opml" }!!.map { it.name }.toSet()
        assertEquals(emptySet<String>(), onDisk - listed)
    }

    /**
     * Five is the floor, for a country as much as for a topic.
     *
     * A pack of two is worse than no pack. Somebody opening "Denmark" and
     * finding a pair of feeds learns that the app does not really cover
     * Denmark, which is a more damaging thing to learn than that it does not
     * list Denmark at all — and the reader in a country the directory happened
     * to skimp on is exactly the reader who most needs the library to work.
     *
     * This is a promise about verification as much as coverage: reaching five
     * means five addresses that were actually fetched, not five names.
     */
    @Test
    fun `every pack has at least five feeds`() {
        index().forEach { (slug, _, count) ->
            assertTrue("$slug has only $count", count >= 5)
        }
    }

    @Test
    fun `every address is https and absolute`() {
        index().forEach { (slug, _, _) ->
            feedsIn(File(LIBRARY, "$slug.opml")).forEach { (title, url) ->
                assertTrue("$slug / $title is not https: $url", url.startsWith("https://"))
            }
        }
    }

    /** Two rows offering the same address in one pack is a mistake, not a choice. */
    @Test
    fun `no pack lists the same address twice`() {
        index().forEach { (slug, _, _) ->
            val urls = feedsIn(File(LIBRARY, "$slug.opml")).map { it.second }
            assertEquals("duplicate address in $slug", urls.size, urls.toSet().size)
        }
    }

    /**
     * Nor the same address with a trailing slash added.
     *
     * How Republika got in twice: `/rss` and `/rss/`, which are one feed to
     * every server that has ever existed and two distinct strings to the check
     * above. Cheap to catch, and it costs a pack one of its five when missed.
     *
     * Deliberately not a test for "the same publication twice". That needs a
     * judgement — two language editions are two feeds, and PNA and PIA are two
     * agencies sharing most of a name — and a matcher confident enough to
     * decide it silently deleted three feeds it should not have. The scan for
     * that lives in docs/FEED_LIBRARY.md, where a person reads its output.
     */
    @Test
    fun `no pack lists one address under two spellings`() {
        index().forEach { (slug, _, _) ->
            val normalised = feedsIn(File(LIBRARY, "$slug.opml"))
                .map { it.second.trimEnd('/').lowercase() }
            assertEquals("same address twice in $slug", normalised.size, normalised.toSet().size)
        }
    }

    @Test
    fun `the library still covers both kinds`() {
        val kinds = index().map { it.second }.toSet()
        assertEquals(setOf("topic", "country"), kinds)
    }
}
