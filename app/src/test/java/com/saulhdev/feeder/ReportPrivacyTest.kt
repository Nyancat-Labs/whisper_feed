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

import com.saulhdev.feeder.utils.LogScrub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The diagnostics report carries no addresses: none in what the app writes to
 * its log, and none in the report built from it.
 *
 * The lines below are the shapes the log really has. The first is from a
 * report sent from the Sony on 26 September, word for word.
 */
class ReportPrivacyTest {

    private val own = mapOf(
        "rss.home.example" to "<server>",
        "reader" to "<account>",
        "Skibbereen, Cork, Ireland" to LogScrub.PLACE,
        "Skibbereen" to LogScrub.PLACE,
        "51.54774" to LogScrub.PLACE,
        "-9.26605" to LogScrub.PLACE,
    )

    private fun scrub(line: String) = LogScrub.scrub(line, own)

    @Test
    fun `a failed feed keeps its title and loses its address`() {
        val line = "09-26 21:49:11.694 E/RssLocalSync(16177): Failed to sync Company | " +
            "The JetBrains Blog: https://blog.jetbrains.com/blog/feed"
        val out = scrub(line)
        assertFalse(out, out.contains("://"))
        assertFalse(out, out.contains("jetbrains.com"))
        assertTrue(out, out.contains("Failed to sync Company | The JetBrains Blog"))
        assertTrue(out, out.startsWith("09-26 21:49:11.694 E/RssLocalSync(16177)"))
    }

    @Test
    fun `a feed address carrying a token goes whole`() {
        val out = scrub("403 when fetching Private: https://example.org/feed.xml?token=Zx81kQ&user=me")
        assertFalse(out, out.contains("Zx81kQ"))
        assertEquals("403 when fetching Private: ${LogScrub.ADDRESS}", out)
    }

    @Test
    fun `the weather query loses the location and the place typed`() {
        val forecast = scrub(
            "W/WeatherRepository: HTTP 500 for https://api.open-meteo.com/v1/forecast" +
                "?latitude=51.54774&longitude=-9.26605&current=temperature_2m"
        )
        val search = scrub(
            "W/WeatherRepository: HTTP 429 for https://geocoding-api.open-meteo.com/v1/search?name=Skibb&count=8"
        )
        listOf(forecast, search).forEach { out ->
            assertFalse(out, out.contains("://"))
            assertFalse(out, out.contains("51.5"))
            assertFalse(out, out.contains("Skibb"))
        }
    }

    @Test
    fun `coordinates inside a quoted body go`() {
        val out = scrub(
            """JsonDecodingException: Unexpected token at offset 40: {"latitude":51.54,"longitude":-9.26,"elevation":52.0}"""
        )
        assertFalse(out, out.contains("51.54"))
        assertFalse(out, out.contains("-9.26"))
        assertTrue(out, out.contains("\"latitude\":${LogScrub.PLACE}"))
        // A number that is not a coordinate stays: it could be the clue.
        assertTrue(out, out.contains("\"elevation\":52.0"))
    }

    @Test
    fun `a coordinate pair written out goes`() {
        assertEquals("Moved to ${LogScrub.PLACE}", scrub("Moved to 51.5521, -9.2633"))
    }

    @Test
    fun `the sync server goes, bare or inside an exception`() {
        val out = scrub(
            "java.net.UnknownHostException: Unable to resolve host \"rss.home.example\": " +
                "No address associated with hostname"
        )
        assertEquals(
            "java.net.UnknownHostException: Unable to resolve host \"<server>\": " +
                "No address associated with hostname",
            out,
        )
        val tls = scrub("javax.net.ssl.SSLPeerUnverifiedException: Hostname RSS.home.example not verified")
        assertFalse(tls, tls.contains("home.example", ignoreCase = true))
    }

    @Test
    fun `IP addresses go, both kinds`() {
        val out = scrub(
            "java.net.SocketTimeoutException: failed to connect to rss.home.example/203.0.113.9 " +
                "(port 443) from /192.168.1.23 (port 41234) after 10000ms"
        )
        assertFalse(out, out.contains("203.0.113.9"))
        assertFalse(out, out.contains("192.168.1.23"))
        assertTrue(out, out.contains("(port 443)"))
        val six = scrub(
            "failed to connect to example.org/2606:4700::6810:84e5 (port 443) " +
                "from /2a02:8084:4c22:1d80:1c5b:9f33:7e1b:62a0 (port 5123)"
        )
        assertFalse(six, six.contains("2606:4700"))
        assertFalse(six, six.contains("2a02:8084"))
    }

    @Test
    fun `a chosen file's address goes`() {
        val out = scrub(
            "E/SAFFile: Failed to read content://com.android.externalstorage.documents/document/primary%3ADownload%2Fwhisper.opml"
        )
        assertFalse(out, out.contains("whisper.opml"))
    }

    @Test
    fun `what the report is for survives`() {
        // Times, process ids, stack frames with R8's map id, class names that
        // look like domains, a browser version with four parts, sizes, and a
        // feed title. Every one of these is in real reports.
        val lines = listOf(
            "09-26 21:49:04.471 D/FeedTrace(16177): changes 58 (11.6/s) -> loads 4 (0.8/s, 500 rows each), " +
                "processed 4 avg 1.0ms, frames 135, none slow, cache 46/129MB (35%, 21 images)",
            "09-26 21:49:11.694 E/RssLocalSync(16177): \tat dk1.g(r8-map-id-f1d70dba21a1fe4ff1be260ee1d2169a644a4efa576d7758aa8b58fbf874f9dd:172)",
            "Caused by: com.rometools.rome.io.ParsingFeedException: Invalid XML: Error on line 1: At line 1, column 0",
            "Chrome/130.0.6723.58 Mobile",
            "  Android Central              20:25 new 0 142 KB · 20:15 new 1 142 KB",
            "Version:     1.0.0-84ae84a (1)",
        )
        lines.forEach { assertEquals(it, scrub(it)) }
    }

    @Test
    fun `an article's id goes`() {
        // From a real report, 8 October 2026.
        assertEquals(
            "10-08 19:46:28.870 E/FeederFullText( 7039): Failed to get fulltext for <id>: HTTP 403",
            scrub("10-08 19:46:28.870 E/FeederFullText( 7039): Failed to get fulltext for a62e9ae6-1d15-4c7a-aa8e-9801154d8208: HTTP 403"),
        )
    }

    @Test
    fun `no log line names an article by its id`() {
        val id = Regex("""\$\{?[\w.?]*?(uuid|remoteId|articleId)\b""")
        // The whole call, across lines: the one that did it put its message on
        // the line after `Log.e(`.
        val call = Regex("""(?:Log\.[iwe]|Log\.println)\((?:[^()]|\([^()]*\))*\)""")
        val offenders = kotlinFiles().flatMap { file ->
            val source = file.readText()
            call.findAll(source)
                .filter { id.containsMatchIn(it.value) }
                .map { "${file.path}:${source.substring(0, it.range.first).count { c -> c == '\n' } + 1}" }
                .toList()
        }
        assertEquals("these log an article's id: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `the reader's own values go only as whole words`() {
        // The account name "reader" must not eat "Whisper RSS reader-mode"
        // or "readers", only the word itself.
        assertEquals("readers and reader-mode", scrub("readers and reader-mode"))
        assertEquals("signed in as <account>", scrub("signed in as reader"))
        // Too short to be told from ordinary words, so left alone.
        assertEquals("ab ab", LogScrub.scrub("ab ab", mapOf("ab" to "<account>")))
    }

    @Test
    fun `the whole place goes before its first word can split it`() {
        assertEquals("Weather for ${LogScrub.PLACE}.", scrub("Weather for Skibbereen, Cork, Ireland."))
    }

    // --- the source ------------------------------------------------------

    private val mainSources = File("src/main/java")

    private fun kotlinFiles(): List<File> =
        mainSources.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `the sources are actually being read`() {
        assertTrue("no Kotlin sources found under $mainSources", kotlinFiles().size > 50)
    }

    /**
     * Warnings and errors survive a release build (§19a), so each is a line in
     * somebody's report. None may put an address into its message. The scrub
     * would catch most, but not a bare host or a typed search, so the rule is
     * held at the source too.
     */
    @Test
    fun `no warning or error writes an address into its message`() {
        val call = Regex("""Log\.[we]\(\s*[^,]+,\s*"((?:[^"\\]|\\.)*)"""")
        val address = Regex(
            """\$\{?[\w.?]*?(url|Url|URL|link|Link|uri|Uri|host|Host|site|query|Query|server|Server|address|Address)\b"""
        )
        val offenders = kotlinFiles().flatMap { file ->
            val source = file.readText()
            call.findAll(source)
                .filter { address.containsMatchIn(it.groupValues[1]) }
                .map { "${file.path}:${source.substring(0, it.range.first).count { c -> c == '\n' } + 1}" }
                .toList()
        }
        assertEquals("these log an address: $offenders", emptyList<String>(), offenders)
    }

    /**
     * The read-on-scroll trace logged each article's headline as it was marked
     * read, so a shared report said what the reader had been reading. Feed
     * titles may be logged; article titles may not.
     */
    @Test
    fun `no log line carries an article's headline`() {
        val headline = Regex("""\$\{?[\w.?]*?(contentTitle|articleTitle)\b""")
        val call = Regex("""(?:Log\.\w+|gateLog|Log\.println)\([^\n]*""")
        val offenders = kotlinFiles().flatMap { file ->
            val source = file.readText()
            call.findAll(source)
                .filter { headline.containsMatchIn(it.value) }
                .map { "${file.path}:${source.substring(0, it.range.first).count { c -> c == '\n' } + 1}" }
                .toList()
        }
        assertEquals("these log a headline: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `the report is scrubbed as one piece`() {
        val diagnostics = File(mainSources, "com/saulhdev/feeder/utils/Diagnostics.kt").readText()
        assertTrue(
            diagnostics.contains(
                "suspend fun collect(context: Context): String = LogScrub.scrub(draft(context), ownValues())"
            )
        )
    }
}
