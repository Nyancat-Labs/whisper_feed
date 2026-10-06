package com.saulhdev.feeder

import com.saulhdev.feeder.manager.bookmarks.upgradedUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The repair for a regression the audit introduced.
 *
 * Refusing plaintext is right, and it broke a class of feed nobody had thought
 * about: one stored with an http address whose server has redirected to https
 * for years. Refuse the connection and that redirect never arrives, so those
 * feeds simply stop and look broken — through no fault of the publisher's, and
 * for the sake of a scheme written down years ago.
 */
class UpgradeToHttpsTest {

    private fun upgraded(url: String) = upgradedUrl(url.toHttpUrl()).toString()

    @Test
    fun `an http feed is asked for over https`() {
        assertEquals("https://9to5google.com/feed/", upgraded("http://9to5google.com/feed/"))
    }

    @Test
    fun `https is left exactly as it is`() {
        val url = "https://feeds.arstechnica.com/arstechnica/index"
        assertEquals(url, upgraded(url))
    }

    @Test
    fun `the query string survives`() {
        // From a real subscription on the test device. Losing a query turns a
        // working fetch into a 404, which is worse than the problem being
        // fixed.
        assertEquals(
            "https://english.aljazeera.net/rss.xml?Postingl=1&a=b",
            upgraded("http://english.aljazeera.net/rss.xml?Postingl=1&a=b"),
        )
    }

    @Test
    fun `a non-default port is kept`() {
        // Whoever serves a feed on 8080 chose that. The scheme is the only
        // thing this is entitled to change.
        assertEquals("https://example.com:8080/feed", upgraded("http://example.com:8080/feed"))
    }

    @Test
    fun `the path is kept, including a trailing slash`() {
        assertEquals("https://www.aftvnews.com/feed/", upgraded("http://www.aftvnews.com/feed/"))
    }
}
