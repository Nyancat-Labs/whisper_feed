package com.saulhdev.feeder

import com.saulhdev.feeder.utils.candidateFeedUrls
import com.saulhdev.feeder.utils.isSameFeedUrl
import com.saulhdev.feeder.utils.normalizeFeedUrl
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

class FeedUrlsTest {

    @Test
    fun `the same feed typed four ways is one feed`() {
        val forms = listOf(
            "https://www.example.com/feed",
            "http://example.com/feed/",
            "HTTPS://Example.COM/feed",
            "https://example.com:443/feed",
        )
        val keys = forms.map(::normalizeFeedUrl).distinct()
        assertEquals("expected one key, got $keys", 1, keys.size)
        assertEquals("example.com/feed", keys.single())
    }

    @Test
    fun `a non-default port is part of the identity`() {
        assertFalse(
            isSameFeedUrl(
                URL("https://example.com/feed"),
                URL("https://example.com:8443/feed"),
            )
        )
    }

    @Test
    fun `the query is kept, because it selects the feed`() {
        // Two different WordPress feeds on one site.
        assertFalse(
            isSameFeedUrl(
                URL("https://example.com/?feed=rss2"),
                URL("https://example.com/?feed=atom"),
            )
        )
        assertEquals("example.com?feed=rss2", normalizeFeedUrl("https://example.com/?feed=rss2"))
    }

    @Test
    fun `path case is preserved, host case is not`() {
        assertFalse(isSameFeedUrl(URL("https://example.com/Feed"), URL("https://example.com/feed")))
        assertTrue(isSameFeedUrl(URL("https://EXAMPLE.com/feed"), URL("https://example.com/feed")))
    }

    @Test
    fun `the fragment never reaches the server, so it is dropped`() {
        assertTrue(
            isSameFeedUrl(
                URL("https://example.com/feed#top"),
                URL("https://example.com/feed"),
            )
        )
    }

    @Test
    fun `a bare host normalises without a path`() {
        assertEquals("howtogeek.com", normalizeFeedUrl("https://www.howtogeek.com/"))
    }

    @Test
    fun `candidates try the site root and cover the common paths`() {
        val candidates = candidateFeedUrls(URL("https://www.howtogeek.com/")).map(URL::toString)
        assertTrue(candidates.contains("https://www.howtogeek.com/feed"))
        assertTrue(candidates.contains("https://www.howtogeek.com/rss.xml"))
        assertTrue(candidates.contains("https://www.howtogeek.com/atom.xml"))
        // The WordPress query form must not gain a double slash.
        assertTrue(candidates.contains("https://www.howtogeek.com/?feed=rss2"))
    }

    @Test
    fun `a section of a site is tried before the site root`() {
        val candidates = candidateFeedUrls(URL("https://example.com/blog")).map(URL::toString)
        val section = candidates.indexOf("https://example.com/blog/feed")
        val root = candidates.indexOf("https://example.com/feed")
        assertTrue("section feed missing", section >= 0)
        assertTrue("root feed missing", root >= 0)
        assertTrue("section should be tried first", section < root)
    }

    @Test
    fun `candidates are free of duplicates`() {
        val candidates = candidateFeedUrls(URL("https://example.com/")).map(URL::toString)
        assertEquals(candidates.size, candidates.distinct().size)
    }
}

/**
 * Asking whether an address is already taken, without the asker matching
 * itself.
 *
 * `normalizeFeedUrl` ignores the scheme on purpose — an http and an https
 * subscription to one feed are one subscription — and that is exactly what
 * made "is the https form of my address free?" answer "no, you have it".
 * Every upgrade reported a collision, and the screen could move nothing.
 */
class SelfMatchTest {

    private val http = sloppyLinkToStrictURL("http://example.com/feed")
    private val https = sloppyLinkToStrictURL("https://example.com/feed")

    @Test
    fun `the two schemes of one address are the same subscription`() {
        assertTrue(isSameFeedUrl(http, https))
    }

    /**
     * Which is why a caller deciding whether it may move a feed has to
     * exclude that feed: the question is whether somebody *else* holds the
     * address, and this is the comparison that answers it.
     */
    @Test
    fun `a feed does not collide with itself across schemes`() {
        val feeds = listOf(1L to http, 2L to sloppyLinkToStrictURL("https://other.com/feed"))
        val clash = feeds.firstOrNull { (id, url) -> id != 1L && isSameFeedUrl(url, https) }
        assertNull(clash)
    }

    @Test
    fun `a different feed on the same address does collide`() {
        val feeds = listOf(1L to http, 2L to https)
        val clash = feeds.firstOrNull { (id, url) -> id != 1L && isSameFeedUrl(url, https) }
        assertEquals(2L, clash?.first)
    }
}
