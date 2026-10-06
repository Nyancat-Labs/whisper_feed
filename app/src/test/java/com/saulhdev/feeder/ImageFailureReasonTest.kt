package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ImageTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A named image failure, with no address in the name.
 *
 * "IllegalArgumentException" was the most common image failure across two
 * device reports and said nothing: the class is thrown by a zero decode size,
 * by a malformed address, and by several things inside Coil. Naming which one
 * is the difference between fixing it and guessing at it — but the reason goes
 * into a file the reader sends to a stranger, and a failure message is the one
 * place a URL turns up uninvited.
 */
class ImageFailureReasonTest {

    @Test
    fun `the message is kept beside the class`() {
        assertEquals(
            "IllegalArgumentException: px must be > 0.",
            ImageTrace.reasonOf(IllegalArgumentException("px must be > 0.")),
        )
    }

    @Test
    fun `an http status still wins`() {
        assertEquals("http 403", ImageTrace.reasonOf(RuntimeException("HTTP 403")))
    }

    @Test
    fun `addresses are dropped, not trimmed`() {
        val reason = ImageTrace.reasonOf(
            IllegalStateException(
                "Unable to fetch https://www.theverge.com/img/a.jpg for user@example.com"
            )
        )
        assertFalse(reason.contains("theverge"))
        assertFalse(reason.contains("example.com"))
        assertFalse(reason.contains("/"))
        assertFalse(reason.contains("@"))
        assertTrue("the class survives", reason.startsWith("IllegalStateException"))
    }

    @Test
    fun `a message that was nothing but an address leaves the class alone`() {
        assertEquals(
            "IllegalArgumentException",
            ImageTrace.reasonOf(IllegalArgumentException("https://example.com/a.png")),
        )
    }

    @Test
    fun `a long message is capped`() {
        val reason = ImageTrace.reasonOf(IllegalStateException("word ".repeat(100)))
        assertTrue("capped", reason.length < 100)
        assertTrue("and said to be", reason.endsWith("…"))
    }

    @Test
    fun `no message is the class alone`() {
        assertEquals("IllegalStateException", ImageTrace.reasonOf(IllegalStateException()))
        assertEquals("unknown", ImageTrace.reasonOf(null))
    }
}
