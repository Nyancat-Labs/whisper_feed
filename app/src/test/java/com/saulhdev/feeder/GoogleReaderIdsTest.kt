package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.GoogleReaderIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The protocol's ids are the part of a Google Reader client most likely to be
 * quietly wrong: two forms of the same number, one of which overflows a signed
 * Long for half its range. A client that gets this wrong works for months and
 * then fails on one article.
 */
class GoogleReaderIdsTest {

    @Test
    fun `the long form converts to the decimal form`() {
        assertEquals(
            "3405691582",
            GoogleReaderIds.itemId("tag:google.com,2005:reader/item/00000000cafebabe"),
        )
    }

    @Test
    fun `a FreshRSS id, sixteen digits, stays decimal`() {
        // Microseconds since 1970: sixteen digits since 2001. Read as hex it
        // became 1682259387568239702, and nothing matched it.
        assertEquals("1758960000123456", GoogleReaderIds.itemId("1758960000123456"))
    }

    @Test
    fun `the unread list and the matched articles agree on a real id`() {
        // Matched from the contents stream, in the long form; listed as
        // unread by the ids stream, in decimal. Both must name one article.
        val decimal = "1758960000123456"
        val long = GoogleReaderIds.longItemId(decimal)!!
        assertEquals(GoogleReaderIds.itemId(decimal), GoogleReaderIds.itemId(long))
    }

    @Test
    fun `sixteen hex characters without the prefix are still read as hex`() {
        assertEquals("3405691582", GoogleReaderIds.itemId("00000000cafebabe"))
    }

    @Test
    fun `a decimal id is already what we want`() {
        assertEquals("3405691582", GoogleReaderIds.itemId("3405691582"))
    }

    @Test
    fun `an id with the top bit set does not throw`() {
        // ffffffffffffffff is 18446744073709551615 unsigned, which is -1
        // signed. toLong() on the hex would throw here; the bit pattern is
        // what the protocol means.
        assertEquals("-1", GoogleReaderIds.itemId("tag:google.com,2005:reader/item/ffffffffffffffff"))
    }

    @Test
    fun `the conversion round-trips both ways`() {
        val long = "tag:google.com,2005:reader/item/00000000cafebabe"
        val decimal = GoogleReaderIds.itemId(long)!!
        assertEquals(long, GoogleReaderIds.longItemId(decimal))
    }

    @Test
    fun `a negative decimal round-trips too`() {
        val decimal = "-1"
        val long = GoogleReaderIds.longItemId(decimal)!!
        assertEquals("tag:google.com,2005:reader/item/ffffffffffffffff", long)
        assertEquals(decimal, GoogleReaderIds.itemId(long))
    }

    @Test
    fun `rubbish costs one article rather than the whole sync`() {
        assertNull(GoogleReaderIds.itemId("not-an-id"))
        assertNull(GoogleReaderIds.itemId(""))
        assertNull(GoogleReaderIds.longItemId("not-a-number"))
    }

    @Test
    fun `the feed prefix is added exactly once`() {
        val url = "https://example.com/feed.xml"
        assertEquals("feed/$url", GoogleReaderIds.feedStream(url))
        assertEquals("feed/$url", GoogleReaderIds.feedStream("feed/$url"))
        assertEquals(url, GoogleReaderIds.feedUrl(GoogleReaderIds.feedStream(url)))
    }

    @Test
    fun `a label survives being wrapped and unwrapped`() {
        assertEquals("user/-/label/News", GoogleReaderIds.labelStream("News"))
        assertEquals("user/-/label/News", GoogleReaderIds.labelStream("user/-/label/News"))
        assertEquals("News", GoogleReaderIds.labelName("user/-/label/News"))
    }
}
