package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.GoogleReaderIds
import com.saulhdev.feeder.manager.sync.greader.StreamItem
import com.saulhdev.feeder.manager.sync.greader.StreamLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turning what a server says into something this app can match.
 *
 * The app fetches its articles from the feeds themselves, so every article has
 * a local uuid the server has never seen while the server has an id this app
 * has never seen. The link is the only thing both sides know, and these are
 * the two steps of getting from one naming to the other.
 */
class GoogleReaderMappingTest {

    @Test
    fun `an item yields its address and the server's id for it`() {
        val item = StreamItem(
            id = "tag:google.com,2005:reader/item/00000000cafebabe",
            alternate = listOf(StreamLink(href = "https://example.org/story", type = "text/html")),
        )
        assertEquals("https://example.org/story" to "3405691582", item.mapping())
    }

    @Test
    fun `an item with no link maps to nothing rather than to a guess`() {
        // Without an address there is nothing to match against, and matching
        // on anything else — a title, a position — is how the wrong article
        // gets marked read.
        val item = StreamItem(id = "tag:google.com,2005:reader/item/00000000cafebabe")
        assertNull(item.mapping())
    }

    @Test
    fun `an item with no usable id maps to nothing`() {
        val item = StreamItem(
            id = "not-an-id",
            alternate = listOf(StreamLink(href = "https://example.org/story")),
        )
        assertNull(item.mapping())
    }

    @Test
    fun `the first usable link wins, and blank ones are skipped`() {
        val item = StreamItem(
            id = "tag:google.com,2005:reader/item/0000000000000001",
            alternate = listOf(
                StreamLink(href = ""),
                StreamLink(href = "https://example.org/real"),
            ),
        )
        assertEquals("https://example.org/real" to "1", item.mapping())
    }

    @Test
    fun `an id with the top bit set does not throw`() {
        // Sixteen hex digits is unsigned, and half that range is larger than
        // Long.MAX_VALUE. Parsing it with toLong() throws on exactly those
        // ids, which is how a client works for months and then falls over on
        // one article.
        val big = GoogleReaderIds.itemId("tag:google.com,2005:reader/item/ffffffffffffffff")
        assertEquals("-1", big)
        assertEquals(
            "tag:google.com,2005:reader/item/ffffffffffffffff",
            GoogleReaderIds.longItemId("-1"),
        )
    }

    @Test
    fun `the short and long forms are the same item`() {
        val long = "tag:google.com,2005:reader/item/00000000cafebabe"
        assertEquals(GoogleReaderIds.itemId(long), GoogleReaderIds.itemId("3405691582"))
    }
}
