package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.Subscription
import com.saulhdev.feeder.manager.sync.greader.SubscriptionCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The parsing half of the client, which is what varies between the four
 * services that speak this protocol. The HTTP half needs a server and is
 * covered by using it against a real one.
 */
class GoogleReaderApiTest {

    @Test
    fun `a FreshRSS subscription list parses`() {
        val json = """
            {"subscriptions":[
              {"id":"feed/https://example.com/rss.xml",
               "title":"Example",
               "categories":[{"id":"user/-/label/News","label":"News"}],
               "url":"https://example.com/rss.xml",
               "htmlUrl":"https://example.com",
               "iconUrl":"https://example.com/favicon.ico"}
            ]}
        """.trimIndent()

        val subs = GoogleReaderApi.subscriptionsAdapter.fromJson(json)!!.subscriptions
        assertEquals(1, subs.size)
        assertEquals("Example", subs[0].title)
        assertEquals("https://example.com/rss.xml", subs[0].feedUrl)
        assertEquals(listOf("News"), subs[0].folders)
    }

    @Test
    fun `a subscription with no url falls back to its stream id`() {
        // Not every server fills in `url`; the id always carries the address.
        val sub = Subscription(id = "feed/https://example.com/atom", title = "T")
        assertEquals("https://example.com/atom", sub.feedUrl)
    }

    @Test
    fun `a feed in no folder is not a feed in a folder called nothing`() {
        val sub = Subscription(id = "feed/https://example.com/rss", title = "T")
        assertTrue(sub.folders.isEmpty())
    }

    @Test
    fun `folders come back as the category names Whisper uses`() {
        val sub = Subscription(
            id = "feed/https://example.com/rss",
            categories = listOf(
                SubscriptionCategory(id = "user/-/label/News"),
                SubscriptionCategory(id = "user/-/label/Tech"),
            ),
        )
        assertEquals(listOf("News", "Tech"), sub.folders)
    }

    @Test
    fun `an item ref list parses and converts its ids`() {
        val json = """
            {"itemRefs":[{"id":"3405691582"},{"id":"1"}]}
        """.trimIndent()
        val refs = GoogleReaderApi.itemRefsAdapter.fromJson(json)!!.itemRefs
        assertEquals(listOf("3405691582", "1"), refs.map { it.id })
    }

    @Test
    fun `a missing field does not take the whole list down`() {
        // Servers differ about which fields they bother to send.
        val json = """{"subscriptions":[{"id":"feed/https://a.example/rss"}]}"""
        val subs = GoogleReaderApi.subscriptionsAdapter.fromJson(json)!!.subscriptions
        assertEquals("", subs[0].title)
        assertEquals("https://a.example/rss", subs[0].feedUrl)
    }
}
