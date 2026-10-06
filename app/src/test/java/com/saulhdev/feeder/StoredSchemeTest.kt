package com.saulhdev.feeder

import com.saulhdev.feeder.utils.normalizeFeedUrl
import com.saulhdev.feeder.utils.preferringHttps
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

/**
 * Every feed address is recorded over https, whatever route it arrived by.
 *
 * Each client that fetches a feed, an article or an icon is built with
 * `onlyPublicHttps()`, so the scheme is rewritten before a socket opens. An
 * address stored as http was therefore never the address used — it was a
 * working subscription described wrongly, then listed on the "Feeds still on
 * http" screen as though its publisher had never moved.
 *
 * The obvious place to enforce this is the Room type converter, since only
 * Feed has URL-typed columns and every write passes through it. That is the
 * wrong place, and the reason is worth keeping: the converter runs *after* the
 * duplicate checks, so an address that changed there could collide with a row
 * those checks had already cleared — and `Feeds.url` is uniquely indexed with
 * an insert strategy of REPLACE, so the collision deletes the other feed and
 * cascades to its articles. A guard against a cosmetic wrong would have
 * introduced silent data loss.
 *
 * So it is applied at the two places a source enters storage, before their
 * checks run.
 */
class StoredSchemeTest {

    private fun upgraded(url: String) = URL(url).preferringHttps().toString()

    @Test
    fun `http becomes https`() {
        assertEquals("https://collider.com/feed", upgraded("http://collider.com/feed"))
        assertEquals("https://example.com", upgraded("http://example.com"))
    }

    @Test
    fun `https is left alone`() {
        assertEquals("https://example.com/feed", upgraded("https://example.com/feed"))
    }

    @Test
    fun `the path, query, port and case survive`() {
        // A lost query turns a working feed into a 404, and a private feed is
        // reachable only because of a token in one.
        assertEquals(
            "https://example.com/feed?format=atom&token=abc",
            upgraded("http://example.com/feed?format=atom&token=abc"),
        )
        assertEquals("https://example.com:8080/feed", upgraded("http://example.com:8080/feed"))
        assertEquals("https://Euronews.eu/rss", upgraded("http://Euronews.eu/rss"))
    }

    @Test
    fun `a scheme this app does not fetch over is not rewritten`() {
        // Turning something unfetchable into "https://" plus rubbish would be
        // worse than leaving it exactly as found.
        assertEquals("file:/tmp/feed.xml", upgraded("file:/tmp/feed.xml"))
        assertEquals("ftp://example.com/feed", upgraded("ftp://example.com/feed"))
    }

    @Test
    fun `an address typed without a scheme gets https`() {
        assertEquals("https://collider.com/feed", sloppyLinkToStrictURL("collider.com/feed").toString())
    }

    @Test
    fun `the upgrade cannot turn one subscription into a different one`() {
        // Guards the whole change against the worry that made the converter
        // the wrong place: a feed already stored must still be recognised as
        // the same feed after its scheme moves, or it is added a second time.
        listOf(
            "http://example.com/feed",
            "http://example.com/feed?x=1",
            "http://example.com:8080/feed",
        ).forEach { before ->
            assertEquals(
                "$before stopped matching itself once upgraded",
                normalizeFeedUrl(before),
                normalizeFeedUrl(URL(before).preferringHttps()),
            )
        }
    }

    @Test
    fun `both ways into storage convert before they check for duplicates`() {
        // The ordering is the safety property, not the conversion. Converting
        // after the check lets an import insert a row that collides with one
        // it has just decided was different — and REPLACE makes that a
        // deletion, not an error.
        mapOf(
            "data/repository/SourcesRepository.kt" to "findRemovedByUrl",
            "manager/models/SourceToRoom.kt" to "findExisting(source.url)",
        ).forEach { (path, check) ->
            val text = File("src/main/java/com/saulhdev/feeder/$path").readText()
            val convert = text.indexOf("preferringHttps()")
            val lookup = text.indexOf(check)
            assertTrue("$path no longer converts the scheme", convert > 0)
            assertTrue("$path no longer checks for a duplicate", lookup > 0)
            assertTrue("$path checks for a duplicate before converting", convert < lookup)
        }
    }

    @Test
    fun `the type converter does not quietly do it too`() {
        // Belt and braces here would be braces alone: a second conversion
        // after the checks reintroduces exactly the collision the ordering
        // above exists to prevent.
        val converters = File(
            "src/main/java/com/saulhdev/feeder/data/db/Converters.kt"
        ).readText()
        assertTrue(
            "the converter upgrades the scheme after the duplicate checks have run",
            !converters.contains("preferringHttps"),
        )
    }
}
