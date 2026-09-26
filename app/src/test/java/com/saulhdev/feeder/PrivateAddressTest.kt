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

import com.saulhdev.feeder.manager.bookmarks.reachesPrivateNetwork
import com.saulhdev.feeder.manager.bookmarks.refusingPrivateNetworks
import com.sun.net.httpserver.HttpServer
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * The private-network guard judges the address a connection actually reached,
 * not a second lookup of the name.
 */
class PrivateAddressTest {

    private val router = InetAddress.getByName("192.168.1.1")
    private val public = InetAddress.getByName("93.184.216.34")

    /** A name check that would say yes: what a rebinding domain tells the second lookup. */
    private val sayPublic: (String) -> Boolean = { true }
    private val mustNotAsk: (String) -> Boolean = { fail("looked the name up again"); true }

    @Test
    fun `a connection to the reader's network is refused whatever the name says`() {
        assertTrue(reachesPrivateNetwork(Proxy.Type.DIRECT, router, "news.example", sayPublic))
    }

    @Test
    fun `a connection to a public address is let through without a second lookup`() {
        assertFalse(reachesPrivateNetwork(Proxy.Type.DIRECT, public, "news.example", mustNotAsk))
    }

    @Test
    fun `through a proxy the name is what is judged`() {
        // The socket is the proxy's, which may well be on the reader's own
        // network, so it says nothing about where the request goes.
        assertFalse(reachesPrivateNetwork(Proxy.Type.HTTP, router, "news.example") { true })
        assertTrue(reachesPrivateNetwork(Proxy.Type.HTTP, public, "router.local") { false })
    }

    @Test
    fun `with nothing known about the connection the name is judged`() {
        assertTrue(reachesPrivateNetwork(null, null, "router.local") { false })
    }

    // --- end to end, through OkHttp ------------------------------------------

    private lateinit var server: HttpServer

    @Before
    fun serve() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.sendResponseHeaders(200, 2)
                exchange.responseBody.use { it.write("ok".toByteArray()) }
            }
            start()
        }
    }

    @After
    fun stop() = server.stop(0)

    @Test
    fun `a name that resolves to the reader's own machine is refused before any request`() {
        // The rebinding case, end to end: OkHttp's lookup answers loopback for
        // a name that looks like anybody's website.
        val rebound = Dns { listOf(InetAddress.getByName("127.0.0.1")) }
        var requests = 0
        server.removeContext("/")
        server.createContext("/") { exchange ->
            requests++
            exchange.sendResponseHeaders(200, 0)
            exchange.close()
        }
        val client = OkHttpClient.Builder().dns(rebound).refusingPrivateNetworks().build()
        val url = "http://news.example:${server.address.port}/feed.xml"
        try {
            client.newCall(Request.Builder().url(url).build()).execute().close()
            fail("reached a private address")
        } catch (e: IOException) {
            assertEquals("Refusing to fetch a private address", e.message)
        }
        assertEquals("no request may reach the server", 0, requests)
    }
}
