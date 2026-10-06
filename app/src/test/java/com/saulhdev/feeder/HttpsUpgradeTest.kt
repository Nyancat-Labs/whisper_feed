/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder

import com.saulhdev.feeder.viewmodels.httpsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URL

/**
 * Rewriting a stored feed address from http to https.
 *
 * Only the scheme moves. Everything that identifies *which* feed — host,
 * path, query — has to survive untouched, because this runs unattended over
 * a whole subscription list and a rewrite that quietly drops a query string
 * points a subscription at the wrong feed rather than a secure one.
 */
class HttpsUpgradeTest {

    @Test
    fun `plain address changes only its scheme`() {
        assertEquals(
            URL("https://example.com/feed"),
            httpsOf(URL("http://example.com/feed")),
        )
    }

    @Test
    fun `an address already on https is left alone`() {
        assertNull(httpsOf(URL("https://example.com/feed")))
    }

    @Test
    fun `the query string survives`() {
        assertEquals(
            URL("https://example.com/rss?format=xml&id=42"),
            httpsOf(URL("http://example.com/rss?format=xml&id=42")),
        )
    }

    @Test
    fun `a fragment survives`() {
        assertEquals(
            URL("https://example.com/feed#latest"),
            httpsOf(URL("http://example.com/feed#latest")),
        )
    }

    @Test
    fun `an empty path stays empty`() {
        assertEquals(URL("https://example.com"), httpsOf(URL("http://example.com")))
    }

    /**
     * Port 80 is http's default. Carrying it across would ask for the
     * plaintext port over TLS, which fails on every server there is — so the
     * upgrade would appear to work and then break every feed it touched.
     */
    @Test
    fun `an explicit port 80 is dropped rather than carried over`() {
        assertEquals(
            URL("https://example.com/feed"),
            httpsOf(URL("http://example.com:80/feed")),
        )
    }

    /** A deliberate non-default port is the server's choice, not ours to discard. */
    @Test
    fun `a non-default port is kept`() {
        assertEquals(
            URL("https://example.com:8080/feed"),
            httpsOf(URL("http://example.com:8080/feed")),
        )
    }

    @Test
    fun `a subdomain and a deep path are untouched`() {
        assertEquals(
            URL("https://blog.example.co.uk/a/b/c/atom.xml"),
            httpsOf(URL("http://blog.example.co.uk/a/b/c/atom.xml")),
        )
    }
}
