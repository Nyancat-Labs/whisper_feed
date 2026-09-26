package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderIds
import com.saulhdev.feeder.manager.sync.greader.Outbox
import com.saulhdev.feeder.manager.sync.greader.savesToKeep
import com.saulhdev.feeder.manager.sync.greader.starsToLookUp
import com.saulhdev.feeder.manager.sync.greader.starsUnplacedAfter
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress

/**
 * Saves reach the other devices even for articles matching never reached.
 *
 * A device matched only what the server delivered from a little before its
 * last sync, so a save of an older article had no server id and was let go
 * unsent; a report from a third device showed two saves and no sync that had
 * ever sent one.
 */
class SaveSyncTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `a save the server has not matched, from a feed it carries, waits for the next sync`() {
        val outbox = Outbox(star = setOf("a", "b", "c"), unstar = setOf("d"))
        val keep = savesToKeep(outbox, mapped = setOf("a"), feedOnServer = setOf("a", "b", "d"))
        // a was matched and goes; c's feed is not on the server, so it has
        // nowhere to go; b and d wait.
        assertEquals(setOf("b", "d"), keep)
    }

    @Test
    fun `reads are never held, only saves`() {
        val outbox = Outbox(read = setOf("r"), star = setOf("s"))
        assertEquals(setOf("s"), savesToKeep(outbox, mapped = emptySet(), feedOnServer = setOf("r", "s")))
    }

    @Test
    fun `the push lets go of everything except the saves still waiting`() {
        val push = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(push.contains("val done = outbox.copy(star = outbox.star - keepSaves, unstar = outbox.unstar - keepSaves)"))
        assertTrue(push.contains("GoogleReaderState.updateOutbox(context) { it.without(done) }"))
    }

    @Test
    fun `waiting saves are looked for in their own feed on the server`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(service.contains("serverStreams = remoteAs.mapValues { it.value.id }.filterValues { it.isNotBlank() }"))
        assertTrue(service.contains("api.contentsPage(auth, stream, MAP_PAGE_SIZE, null, continuation)"))
    }

    @Test
    fun `the saved list is read as ids, and only saves not placed here are asked for`() {
        // It was read with contents, eight pages of 250 on every sync. The
        // ids are kilobytes; the contents are needed only for an address.
        val service = source("manager/sync/service/GoogleReaderService.kt")
        val stars = service.substring(service.indexOf("private suspend fun pullStars"))
            .substringBefore("override suspend fun setRead")
        assertTrue(stars.contains("api.allItemIds(auth, stream = GoogleReaderIds.STREAM_STARRED"))
        assertTrue(stars.contains("val lookUp = starsToLookUp(starred, known, unplacedBefore)"))
        assertTrue(stars.contains("api.itemsContents(auth, chunk)"))
        assertTrue(stars.contains("articles.attachRemoteId(link, remoteId)"))
        assertTrue(!stars.contains("contentsPage("))
    }

    @Test
    fun `a save is asked about once, and forgotten when it is unsaved there`() {
        val starred = setOf("known", "new", "asked-before")
        // Matched here already, or asked about and not found: neither again.
        assertEquals(listOf("new"), starsToLookUp(starred, known = setOf("known"), unplaced = setOf("asked-before")))
        // "new" was asked about and is still not here; "gone" was unsaved on
        // the server, so there is nothing left to remember.
        assertEquals(
            setOf("new", "asked-before"),
            starsUnplacedAfter(starred, before = setOf("asked-before", "gone"), lookedUp = listOf("new"), known = setOf("known")),
        )
        // Found this time: not remembered as missing.
        assertEquals(emptySet<String>(), starsUnplacedAfter(setOf("new"), emptySet(), listOf("new"), known = setOf("new")))
    }

    @Test
    fun `saved items are asked for by id, in the body, one i each`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var path = ""
        var form = ""
        server.createContext("/") { ex ->
            path = ex.requestURI.rawPath + "?" + ex.requestURI.rawQuery
            form = ex.requestBody.readBytes().decodeToString()
            val body = """{"items":[{"id":"tag:google.com,2005:reader/item/000000000000000b",
                "alternate":[{"href":"https://example.com/older-story"}]}]}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val api = GoogleReaderApi("http://127.0.0.1:${server.address.port}/api/greader.php")
            val items = runBlocking { api.itemsContents("auth", listOf("11", "12")) }
            assertEquals("https://example.com/older-story" to "11", items.single().mapping())
            assertTrue(path, path.endsWith("/reader/api/0/stream/items/contents?output=json"))
            assertEquals("i=11&i=12", form)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `saves lost before the fix are sent once`() {
        val service = source("manager/sync/service/GoogleReaderService.kt")
        assertTrue(service.contains("if (GoogleReaderState.savesRequeued(context)) return"))
        assertTrue(service.contains("saved.filter { it !in outbox.unstar }.fold(outbox) { acc, id -> acc.withStar(id, true) }"))
    }

    @Test
    fun `the saved stream and a feed stream come back with their addresses`() {
        // Against a server on this machine answering as FreshRSS does.
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val asked = mutableListOf<String>()
        server.createContext("/") { ex ->
            asked += ex.requestURI.rawPath
            val body = """{"items":[{"id":"tag:google.com,2005:reader/item/000000000000000a",
                "alternate":[{"href":"https://example.com/story"}]}]}"""
            val bytes = body.toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val api = GoogleReaderApi("http://127.0.0.1:${server.address.port}/api/greader.php")
            val (starred, _) = runBlocking { api.contentsPage("auth", GoogleReaderIds.STREAM_STARRED, 250, null, null) }
            assertEquals("https://example.com/story" to "10", starred.single().mapping())
            val (feed, _) = runBlocking { api.contentsPage("auth", "feed/12", 250, null, null) }
            assertEquals(1, feed.size)
            assertTrue(asked.toString(), asked.any { it.endsWith("/stream/contents/user/-/state/com.google/starred") })
            assertTrue(asked.toString(), asked.any { it.endsWith("/stream/contents/feed/12") })
        } finally {
            server.stop(0)
        }
    }
}
