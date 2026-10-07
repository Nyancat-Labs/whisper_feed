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

import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.manager.models.OPMLParser
import com.saulhdev.feeder.manager.models.ParserToDatabase
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

/**
 * The published XML attacks, sent to the two parsers that read XML somebody
 * else wrote: a feed from any server a subscription names, and an OPML file
 * a reader was sent. XmlHardeningTest covers the third, the bundled packs.
 *
 * XXE reads a local file into the document; billion laughs expands a few
 * hundred bytes of entities into gigabytes. Either one in a feed would run
 * on every sync of that feed.
 */
class XmlAttackTest {

    private val secret = "whisper-xxe-canary-" + System.nanoTime()
    private val file = File.createTempFile("xxe", ".txt").apply { writeText(secret); deleteOnExit() }

    private fun rss(doctype: String, title: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        $doctype
        <rss version="2.0"><channel><title>$title</title><link>https://example.org/</link>
        <item><title>$title</title><link>https://example.org/1</link><description>$title</description></item>
        </channel></rss>
    """.trimIndent()

    private fun feedText(xml: String): String? = runBlocking {
        runCatching {
            val body = xml.toResponseBody("application/rss+xml".toMediaType())
            val feed = FeedParser().parseRssAtom(URL("https://example.org/feed"), body, findIcon = false)
            listOfNotNull(feed.title, feed.description) +
                feed.items.orEmpty().flatMap { listOfNotNull(it.title, it.content_html, it.content_text, it.summary) }
        }.getOrNull()?.joinToString(" ")
    }

    @Test
    fun `a feed cannot read a file off the phone`() {
        val xml = rss("""<!DOCTYPE rss [<!ENTITY xxe SYSTEM "${file.toURI()}">]>""", "&xxe;")
        val text = feedText(xml)
        // Refused outright is the expected answer; parsed without the file is
        // the only other acceptable one.
        assertFalse("the file reached the feed: $text", text.orEmpty().contains(secret))
    }

    @Test
    fun `the payload is a real attack`() {
        // Sent to a parser with no protection, the same feed does carry the
        // file away, so the tests above pass because the app refuses it and
        // not because the attack was written wrong.
        val xml = rss("""<!DOCTYPE rss [<!ENTITY xxe SYSTEM "${file.toURI()}">]>""", "&xxe;")
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.byteInputStream())
        assertTrue(doc.documentElement.textContent.contains(secret))
    }

    @Test
    fun `a feed cannot read a file through a parameter entity`() {
        val xml = rss(
            """<!DOCTYPE rss [<!ENTITY % ext SYSTEM "${file.toURI()}"> %ext;]>""",
            "plain",
        )
        assertFalse(feedText(xml).orEmpty().contains(secret))
    }

    @Test
    fun `a feed cannot expand into gigabytes`() {
        val laughs = buildString {
            append("<!DOCTYPE rss [<!ENTITY lol \"lol\">")
            for (i in 1..10) {
                val prev = if (i == 1) "lol" else "lol${i - 1}"
                append("<!ENTITY lol$i \"${"&$prev;".repeat(10)}\">")
            }
            append("]>")
        }
        val started = System.currentTimeMillis()
        val text = feedText(rss(laughs, "&lol10;"))
        assertTrue("took ${System.currentTimeMillis() - started}ms", System.currentTimeMillis() - started < 5_000)
        assertTrue("expanded to ${text?.length} characters", (text?.length ?: 0) < 100_000)
    }

    @Test
    fun `an ordinary feed still parses`() {
        val text = feedText(rss("", "Plain title"))
        assertTrue(text, text.orEmpty().contains("Plain title"))
    }

    private fun opmlFeeds(doctype: String, entity: String): List<Feed> {
        val saved = mutableListOf<Feed>()
        val opml = """
            <?xml version="1.0" encoding="UTF-8"?>
            $doctype
            <opml version="1.0"><head><title>t</title></head><body>
            <outline text="$entity" title="$entity" type="rss" xmlUrl="https://example.org/$entity" />
            </body></opml>
        """.trimIndent()
        val parser = OPMLParser(object : ParserToDatabase<Feed> {
            override suspend fun getItem(id: String): Feed? = null
            override suspend fun saveItem(item: Feed) { saved += item }
        })
        runBlocking { runCatching { parser.parseInputStream(opml.byteInputStream()) } }
        return saved
    }

    @Test
    fun `an imported OPML file cannot read a file off the phone`() {
        val feeds = opmlFeeds("""<!DOCTYPE opml [<!ENTITY xxe SYSTEM "${file.toURI()}">]>""", "&xxe;")
        feeds.forEach { assertFalse(it.toString(), it.title.contains(secret) || it.url.toString().contains(secret)) }
    }

    @Test
    fun `an imported OPML file cannot expand into gigabytes`() {
        val laughs = buildString {
            append("<!DOCTYPE opml [<!ENTITY lol \"lol\">")
            for (i in 1..10) {
                val prev = if (i == 1) "lol" else "lol${i - 1}"
                append("<!ENTITY lol$i \"${"&$prev;".repeat(10)}\">")
            }
            append("]>")
        }
        val started = System.currentTimeMillis()
        val feeds = opmlFeeds(laughs, "&lol10;")
        assertTrue(System.currentTimeMillis() - started < 5_000)
        feeds.forEach { assertTrue(it.title.length < 100_000) }
    }

    @Test
    fun `a feed being added cannot send for ever`() {
        // The discovery pass checks addresses that websites link to, so the
        // address can be anybody's. Sync stopped at 10 MB; this path did not.
        // A well-formed feed whose channel never closes. It gives up at 50 MB
        // only so that a broken build fails this test instead of hanging it.
        var sent = 0L
        val endless = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (sent > 50L * 1024 * 1024) return -1
                val chunk = (if (sent == 0L) "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>t</title>" else "") +
                    "<item><title>x</title><description>endless</description></item>".repeat(256)
                sink.writeUtf8(chunk)
                sent += chunk.length
                return chunk.length.toLong()
            }
            override fun timeout() = Timeout.NONE
            override fun close() = Unit
        }
        val body = object : ResponseBody() {
            override fun contentType(): MediaType = "application/rss+xml".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = endless.buffer()
        }
        val response = Response.Builder()
            .request(Request.Builder().url("https://example.org/feed").build())
            .protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body).build()
        val result = runBlocking { runCatching { FeedParser().parseFeedResponse(response) } }
        assertTrue("an endless feed was accepted", result.isFailure)
        assertTrue("read $sent bytes", sent < 11L * 1024 * 1024)
    }
}
