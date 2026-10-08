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

import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderIds
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress

/**
 * The match's size is counted from the server's responses, not read from
 * Android's count for the whole app, which twice reported "<1 KB" for
 * hundreds of items because it had not caught up.
 */
class MatchBytesTest {

    @Test
    fun `every byte of a page is counted the moment it has been read`() {
        val item = """{"id":"tag:google.com,2005:reader/item/000000000000000a","crawlTimeMsec":"1759300000123",""" +
            """"timestampUsec":"1759300000123456","alternate":[{"href":"https://example.com/story"}]}"""
        val body = """{"items":[${List(200) { item }.joinToString(",")}],"continuation":"next"}""".toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/greader.php/reader/api/0/stream/contents") { ex ->
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val api = GoogleReaderApi("http://127.0.0.1:${server.address.port}/api/greader.php")
            assertEquals(0L, api.receivedBytes)
            val (items, _) = runBlocking {
                api.contentsPage("auth", GoogleReaderIds.STREAM_READING_LIST, 250, null, null, oldestFirst = true)
            }
            assertEquals(200, items.size)
            assertEquals("all of the first page", body.size.toLong(), api.receivedBytes)
            runBlocking { api.contentsPage("auth", GoogleReaderIds.STREAM_READING_LIST, 250, null, "next", oldestFirst = true) }
            assertEquals("and the second added to it", 2L * body.size, api.receivedBytes)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `the match reads its size from the server's counter`() {
        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        val map = service.substring(service.indexOf("private suspend fun mapRemoteIds("), service.indexOf("private suspend fun queueNewlyMatched"))
        assertTrue(map.contains("val receivedBefore = api.receivedBytes"))
        assertTrue(map.contains("bytes = api.receivedBytes - receivedBefore"))
        assertFalse("Android's whole-app count again", map.contains("receivedBytes()"))
    }
}
