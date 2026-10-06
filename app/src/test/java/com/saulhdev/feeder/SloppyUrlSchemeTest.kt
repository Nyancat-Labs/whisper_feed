package com.saulhdev.feeder

import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What scheme an address gets when it was typed without one.
 *
 * The add-a-source search box runs everything typed into it through this, and
 * `collider.com/feed` is how a person writes an address. It became
 * `http://collider.com/feed`, was stored that way, and then appeared on the
 * "Feeds still on http" screen as though the publisher had never moved — four
 * of them on one device in an afternoon, every one added by hand that day.
 *
 * It stayed invisible because the fetch worked. Plaintext is refused, so an
 * http address ought to fail loudly; instead the upgrade interceptor rewrites
 * the scheme before the socket opens, so the feed syncs perfectly while the
 * address written beside it is wrong.
 *
 * Which is why this is pinned rather than left to the next reading: nothing
 * about the app's behaviour reveals the difference. Only a screen that exists
 * to list http subscriptions ever mentions it.
 */
class SloppyUrlSchemeTest {

    @Test
    fun `an address typed without a scheme gets https`() {
        assertEquals("https://collider.com/feed", sloppyLinkToStrictURL("collider.com/feed").toString())
        assertEquals("https://example.com", sloppyLinkToStrictURL("example.com").toString())
    }

    @Test
    fun `a scheme that was given is left exactly as it was`() {
        // Someone who typed http meant http, and the screen that offers to
        // upgrade it is where that conversation belongs — not here, silently.
        assertEquals("http://example.com/feed", sloppyLinkToStrictURL("http://example.com/feed").toString())
        assertEquals("https://example.com/feed", sloppyLinkToStrictURL("https://example.com/feed").toString())
    }

    @Test
    fun `the path, query and case survive the scheme being added`() {
        // A lost query turns a working feed into a 404, and plenty of servers
        // distinguish /Feed from /feed.
        assertEquals(
            "https://Euronews.eu/rss",
            sloppyLinkToStrictURL("Euronews.eu/rss").toString(),
        )
        assertEquals(
            "https://example.com/feed?format=atom&token=abc",
            sloppyLinkToStrictURL("example.com/feed?format=atom&token=abc").toString(),
        )
        assertEquals(
            "https://example.com:8443/Feed",
            sloppyLinkToStrictURL("example.com:8443/Feed").toString(),
        )
    }

    @Test
    fun `the scheme is not part of what makes two subscriptions the same`() {
        // Guards the change above against the obvious worry: that flipping the
        // default would make a feed already stored as http look like a
        // different subscription and be added a second time.
        assertEquals(
            com.saulhdev.feeder.utils.normalizeFeedUrl("http://example.com/feed"),
            com.saulhdev.feeder.utils.normalizeFeedUrl("https://example.com/feed"),
        )
        assertEquals(
            com.saulhdev.feeder.utils.normalizeFeedUrl("example.com/feed"),
            com.saulhdev.feeder.utils.normalizeFeedUrl("http://example.com/feed"),
        )
    }
}
