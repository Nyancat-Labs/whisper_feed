package com.saulhdev.feeder

import com.saulhdev.feeder.utils.isBrowsable
import com.saulhdev.feeder.utils.isViewable
import com.saulhdev.feeder.utils.schemeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two checks standing between a feed's contents and an intent leaving the
 * app.
 *
 * Every address they see was written by somebody else — an `<a href>` in an
 * article body, a link on a page in the in-app browser, a URL out of an
 * imported OPML — so these are the tests worth having.
 */
class UrlSchemesTest {

    @Test
    fun `an ordinary address has its scheme, lowercased`() {
        assertEquals("https", schemeOf("https://example.com/feed"))
        assertEquals("http", schemeOf("HTTP://example.com"))
        assertEquals("mailto", schemeOf("mailto:someone@example.com"))
    }

    @Test
    fun `a scheme may carry digits, plus, dot and dash`() {
        assertEquals("android-app", schemeOf("android-app://com.example"))
        assertEquals("coap+ws", schemeOf("coap+ws://x"))
        assertEquals("z39.50r", schemeOf("z39.50r://x"))
    }

    @Test
    fun `no colon, no scheme`() {
        assertNull(schemeOf("example.com/feed"))
        assertNull(schemeOf(""))
    }

    @Test
    fun `a scheme must start with a letter and hold nothing else`() {
        // ":evil" and "3http:" are not addresses; neither is a Windows path.
        assertNull(schemeOf(":https://example.com"))
        assertNull(schemeOf("3http://example.com"))
        assertNull(schemeOf("we ird://example.com"))
    }

    @Test
    fun `a colon inside a path is not a scheme`() {
        assertNull(schemeOf("/some/path:with/a/colon"))
    }

    @Test
    fun `the browser opens the web and nothing else`() {
        assertTrue(isBrowsable("https://example.com"))
        assertTrue(isBrowsable("http://example.com"))
        // These are the ones that matter: the in-app WebView runs JavaScript,
        // and any app on the device can point it somewhere by explicit intent.
        assertFalse("app's own storage", isBrowsable("file:///data/data/com.nyancatlabs.whisper/x"))
        assertFalse("a provider", isBrowsable("content://sms/inbox"))
        assertFalse("script in the address bar", isBrowsable("javascript:alert(1)"))
        assertFalse("intent scheme", isBrowsable("intent://scan/#Intent;end"))
        assertFalse(isBrowsable("data:text/html,<script>alert(1)</script>"))
        assertFalse(isBrowsable("about:blank"))
    }

    @Test
    fun `handing a link to another app allows the ones a link really uses`() {
        listOf(
            "https://example.com",
            "http://example.com",
            "mailto:a@b.com",
            "tel:+441234567890",
            "sms:+441234567890",
            "geo:51.5,-0.1",
            "market://details?id=com.nyancatlabs.whisper",
        ).forEach { assertTrue(it, isViewable(it)) }
    }

    @Test
    fun `and refuses the ones that reach past the web`() {
        listOf(
            "file:///sdcard/x",
            "content://media/external/images/1",
            "javascript:alert(1)",
            "intent://x#Intent;end",
            "android-app://com.example",
            "jar:file:///x!/y",
            "no-scheme-at-all",
        ).forEach { assertFalse(it, isViewable(it)) }
    }

    @Test
    fun `case is not a way past either check`() {
        assertFalse(isBrowsable("JavaScript:alert(1)"))
        assertFalse(isViewable("FILE:///sdcard/x"))
        assertTrue(isViewable("HTTPS://example.com"))
    }
}
