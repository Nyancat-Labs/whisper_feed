package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.normalisedServerUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sync server address, corrected where the reader can see it happen.
 *
 * Every other client in the app rewrites `http` to `https` on its way to the
 * socket. This one does not, and the reason is not that upgrading a
 * credentialed address is unsafe — it is the same host either way, checked
 * against the system trust store — but that a password should not be quietly
 * routed somewhere its owner did not agree to. The correction belongs in the
 * field, before the password is sent, where it can be read.
 */
class ServerAddressTest {

    @Test
    fun `http becomes https, keeping the path`() {
        // The path matters: a Google Reader endpoint is not the site root, and
        // losing `/api/greader.php` turns a working sign-in into a 404.
        assertEquals(
            "https://freshrss.example.com/api/greader.php",
            normalisedServerUrl("http://freshrss.example.com/api/greader.php"),
        )
    }

    @Test
    fun `an address with no scheme gets one`() {
        // What people paste out of their own browser's address bar.
        assertEquals(
            "https://freshrss.example.com/api/greader.php",
            normalisedServerUrl("freshrss.example.com/api/greader.php"),
        )
    }

    @Test
    fun `https is left exactly as it is`() {
        val url = "https://rss.example.com/api/greader.php"
        assertEquals(url, normalisedServerUrl(url))
    }

    @Test
    fun `a port survives, because self-hosted servers use one`() {
        assertEquals(
            "https://192.168.1.50:8443/api/greader.php",
            normalisedServerUrl("http://192.168.1.50:8443/api/greader.php"),
        )
    }

    @Test
    fun `a private address is normalised like any other`() {
        // This client is the one allowed onto the reader's own network, so a
        // LAN address is an ordinary address here and not a special case.
        assertEquals("https://nas.local/greader.php", normalisedServerUrl("nas.local/greader.php"))
    }

    @Test
    fun `the scheme is matched whatever case it was typed in`() {
        assertEquals("https://example.com/x", normalisedServerUrl("HTTP://example.com/x"))
        assertEquals("https://example.com/x", normalisedServerUrl("HtTpS://example.com/x"))
    }

    @Test
    fun `surrounding whitespace is dropped`() {
        // Pasting from a browser or a password manager brings it along.
        assertEquals(
            "https://example.com/greader.php",
            normalisedServerUrl("  https://example.com/greader.php\n"),
        )
    }

    @Test
    fun `an empty field stays empty`() {
        assertEquals("", normalisedServerUrl(""))
        assertEquals("", normalisedServerUrl("   "))
    }

    @Test
    fun `another scheme is left alone rather than rewritten into a lie`() {
        // Turning ftp:// into https://ftp:// would take a clear mistake and
        // make it a confusing one.
        assertEquals("ftp://example.com/x", normalisedServerUrl("ftp://example.com/x"))
    }
}
