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

import com.saulhdev.feeder.manager.models.Page
import com.saulhdev.feeder.manager.models.pageDocument
import com.saulhdev.feeder.utils.DownloadRefused
import com.saulhdev.feeder.utils.bytesAtMost
import com.saulhdev.feeder.utils.errorKind
import com.saulhdev.feeder.utils.isMedia
import com.saulhdev.feeder.utils.refuseMedia
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A feed or a page is read only up to a limit, and not at all when it is a
 * picture, a sound or a film; and a page is decoded in the encoding it names.
 */
class DownloadLimitsTest {

    /**
     * A body that never ends, counting what is taken from it: a podcast
     * episode at a feed's address, as far as the reader is concerned.
     */
    private class Endless(private val declared: Long = -1L) : ResponseBody() {
        var read = 0L
        override fun contentType(): MediaType? = "application/rss+xml".toMediaType()
        override fun contentLength(): Long = declared
        override fun source(): BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                sink.write(ByteArray(byteCount.toInt()))
                read += byteCount
                return byteCount
            }
            override fun timeout() = Timeout.NONE
            override fun close() {}
        }.buffer()
    }

    @Test
    fun `a body within the limit is read whole`() {
        val bytes = ByteArray(1000) { it.toByte() }
        assertArrayEquals(bytes, bytes.toResponseBody().bytesAtMost(1000))
    }

    @Test
    fun `a body past the limit is refused having read no more than a step past it`() {
        val body = Endless()
        try {
            body.bytesAtMost(1_000_000)
            fail("an endless body was read")
        } catch (e: DownloadRefused) {
            assertEquals("too large", e.kind)
        }
        assertTrue("read ${body.read} bytes", body.read < 1_000_000 + 200_000)
    }

    @Test
    fun `a body declared too large is refused before any of it is read`() {
        val body = Endless(declared = 300L * 1024 * 1024)
        try {
            body.bytesAtMost(10L * 1024 * 1024)
            fail("a 300 MB body was read")
        } catch (e: DownloadRefused) {
            assertEquals("too large", e.kind)
        }
        assertEquals(0L, body.read)
    }

    @Test
    fun `pictures, sounds and films are not feeds, and odd text types still are`() {
        listOf("audio/mpeg", "video/mp4", "image/jpeg").forEach {
            assertTrue(it, it.toMediaType().isMedia())
        }
        // Servers send feeds with every type there is. None of these is
        // refused: the size limit is the guard for them.
        listOf("application/rss+xml", "text/xml", "text/html", "application/octet-stream", "text/plain").forEach {
            assertFalse(it, it.toMediaType().isMedia())
        }
        assertFalse((null as MediaType?).isMedia())
    }

    @Test
    fun `a podcast episode at a feed's address is refused in words`() {
        val episode = ByteArray(10).toResponseBody("audio/mpeg".toMediaType())
        try {
            episode.refuseMedia("a feed")
            fail("an episode was taken for a feed")
        } catch (e: DownloadRefused) {
            // What the feed's history shows, readable in a release build.
            assertEquals("not a feed", errorKind(e))
        }
    }

    // --- encodings ---------------------------------------------------------

    private val url = "https://example.org/story"

    @Test
    fun `a page that names its encoding only in a meta tag is read in it`() {
        val html = """<html><head><meta charset="windows-1252"><title>t</title></head>""" +
            "<body><p>Café crème, “quoted”</p></body></html>"
        val page = Page(html.toByteArray(charset("windows-1252")), charset = null)
        assertEquals("Café crème, “quoted”", pageDocument(page, url).select("p").text())
        // What reading it as UTF-8 first did.
        assertTrue(String(page.bytes, Charsets.UTF_8).contains('�'))
    }

    @Test
    fun `the server's charset is taken when it names one`() {
        val html = "<html><body><p>日本語</p></body></html>"
        val page = Page(html.toByteArray(charset("Shift_JIS")), charset = charset("Shift_JIS"))
        assertEquals("日本語", pageDocument(page, url).select("p").text())
    }

    @Test
    fun `a page that names nothing is read as UTF-8`() {
        val html = "<html><body><p>naïve — résumé</p></body></html>"
        val page = Page(html.toByteArray(Charsets.UTF_8), charset = null)
        assertEquals("naïve — résumé", pageDocument(page, url).select("p").text())
    }
}
