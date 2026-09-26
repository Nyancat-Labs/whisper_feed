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

import com.saulhdev.feeder.manager.models.answersFeedChallenge
import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.sameServer
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress

/**
 * A password goes only to the server it was given for: a feed's, written into
 * its address, and the sync account's, typed into the sign-in.
 */
class CredentialScopeTest {

    @Test
    fun `a feed's password answers only its own server, over https`() {
        assertTrue(answersFeedChallenge("https://feeds.example.org/private.xml".toHttpUrl(), "feeds.example.org"))
        assertTrue(answersFeedChallenge("https://FEEDS.example.org/x".toHttpUrl(), "feeds.example.org"))
        // Where the feed redirected to, and the same server over plain http.
        assertFalse(answersFeedChallenge("https://elsewhere.example/x".toHttpUrl(), "feeds.example.org"))
        assertFalse(answersFeedChallenge("http://feeds.example.org/x".toHttpUrl(), "feeds.example.org"))
    }

    @Test
    fun `no feed password is offered to a proxy`() {
        val parser = File("src/main/java/com/saulhdev/feeder/manager/models/FeedParser.kt").readText()
        assertFalse(parser.contains("proxyAuthenticator"))
    }

    @Test
    fun `a redirect is followed with the password only on the same server`() {
        assertTrue(sameServer("https://rss.example.org/api/greader.php".toHttpUrl(), "https://rss.example.org/api/greader.php/".toHttpUrl()))
        assertFalse(sameServer("https://rss.example.org/a".toHttpUrl(), "https://other.example/a".toHttpUrl()))
        assertFalse(sameServer("https://rss.example.org/a".toHttpUrl(), "https://rss.example.org:8443/a".toHttpUrl()))
        assertFalse(sameServer("https://rss.example.org/a".toHttpUrl(), "http://rss.example.org/a".toHttpUrl()))
    }

    @Test
    fun `a sign-in sent on to another server is stopped, and names it`() {
        // Two names for this machine: to the client they are two servers.
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val bodies = mutableListOf<String>()
        server.createContext("/") { ex ->
            val body = ex.requestBody.readBytes().decodeToString()
            val path = ex.requestURI.path
            val host = ex.requestHeaders.getFirst("Host").orEmpty()
            if (host.startsWith("localhost")) bodies += "other:$body" else bodies += "own:$path"
            when {
                path.endsWith("/moved/accounts/ClientLogin") -> {
                    ex.responseHeaders.add("Location", "http://localhost:${server.address.port}/steal")
                    ex.sendResponseHeaders(307, -1)
                }
                path.endsWith("/slash/accounts/ClientLogin") -> {
                    ex.responseHeaders.add("Location", "/slash/accounts/ClientLogin/")
                    ex.sendResponseHeaders(308, -1)
                }
                path.endsWith("/slash/accounts/ClientLogin/") -> {
                    val reply = "SID=x\nAuth=the-token\n".toByteArray()
                    ex.sendResponseHeaders(200, reply.size.toLong())
                    ex.responseBody.use { it.write(reply) }
                }
                else -> ex.sendResponseHeaders(404, -1)
            }
            ex.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val moved = runBlocking { GoogleReaderApi("$base/moved").signIn("reader@example.org", "hunter2") }
            assertEquals(GoogleReaderApi.AuthResult.Failed(AccountProblem.MOVED, "localhost"), moved)
            assertTrue("the password reached another server: $bodies", bodies.none { it.startsWith("other:") })

            // The same server moving its own path is followed, password and all.
            val slash = runBlocking { GoogleReaderApi("$base/slash").signIn("reader@example.org", "hunter2") }
            assertEquals(GoogleReaderApi.AuthResult.Success("the-token"), slash)
        } finally {
            server.stop(0)
        }
    }
}
