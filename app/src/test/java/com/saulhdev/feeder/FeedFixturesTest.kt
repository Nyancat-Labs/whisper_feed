package com.saulhdev.feeder

import com.saulhdev.feeder.data.entity.JsonFeed
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.utils.HttpStatusException
import com.saulhdev.feeder.utils.errorKind
import com.saulhdev.feeder.viewmodels.refusesApps
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

/**
 * Feeds that once broke Whisper, kept as samples (src/test/resources/feeds,
 * see its README). Each goes through the same parser a sync uses, so a change
 * that breaks one of them again fails here, not on a phone.
 */
class FeedFixturesTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("feeds/$name")!!.readText()

    private fun parse(name: String, url: String): JsonFeed = runBlocking {
        FeedParser().parseRssAtom(
            URL(url),
            fixture(name).toResponseBody("application/rss+xml; charset=utf-8".toMediaType()),
            findIcon = false,
        )
    }

    @Test
    fun `Darknet - a script after the end of the feed doesn't sink it (117)`() {
        val feed = parse("darknet-trailing-script.xml", "https://www.darknet.org.uk/feed/")
        assertEquals("Darknet – Hacking Tools, Hacker News & Cyber Security", feed.title)
        assertEquals(2, feed.items?.size)
        assertEquals("httpx – ProjectDiscovery’s HTTP Toolkit for Probing Live Hosts", feed.items!!.first().title)
    }

    @Test
    fun `Quartz - a feed with nothing in it parses, with no items, so the sync counts it as empty (111, 128)`() {
        val feed = parse("quartz-empty.xml", "https://qz.com/feed/")
        assertEquals("Quartz", feed.title)
        assertTrue(feed.items.isNullOrEmpty())
    }

    @Test
    fun `Cyberspace - Markdown gives a picture, a real headline, and code with its spacing (45)`() {
        val items = parse("cyberspace-markdown.xml", "https://cyberspace.online/feed.xml").items!!
        val photo = items[0]
        assertEquals("https://bunker.cyberspace.online/uploads/post/a/headlights.webp", photo.image)
        assertFalse("a file name is not a headline", photo.title!!.contains(".jpg"))
        assertFalse(photo.content_text.orEmpty().contains("!["))
        val art = items[1].content_html.orEmpty()
        assertTrue(art, art.contains("<pre><code>___ ___\n|\\ \\ |\\ \\</code></pre>"))
        assertTrue(art.contains("<li><code>/coin</code> for a coin-flip</li>"))
    }

    @Test
    fun `IT Security Guru - a 403 to apps is named as a site blocking apps (117)`() {
        val refused = FeedParser.FeedParsingError(URL("https://itsecurityguru.org/feed/"), HttpStatusException(403))
        assertTrue(refusesApps(refused))
        assertNotNull(errorKind(refused))
    }
}
