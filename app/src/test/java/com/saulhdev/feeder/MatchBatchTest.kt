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

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.repository.ArticleRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A match files a page of server ids in one commit.
 *
 * The diagnostics of 7 October: server 2.5s, phone 60s, nearly all of it
 * "linking", which was one database write per item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MatchBatchTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    private fun sql(s: String) = db.openHelper.writableDatabase.execSQL(s)

    private fun remoteIdOf(uuid: String): String? =
        db.openHelper.readableDatabase.query("SELECT remoteId FROM Article WHERE uuid = '$uuid'").use {
            it.moveToFirst()
            if (it.isNull(0)) null else it.getString(0)
        }

    private fun article(uuid: String, remoteId: String? = null) = sql(
        "INSERT INTO Article (uuid, guid, title, plainTitle, plainSnippet, description, feedId, firstSyncedTime, primarySortTime, categories, pinned, bookmarked, readAt, link, remoteId) " +
            "VALUES ('$uuid', '$uuid', '', '', '', '', 1, 0, 0, '[]', 0, 0, 0, 'https://example.org/$uuid', ${remoteId?.let { "'$it'" } ?: "NULL"})"
    )

    @Test
    fun `a page is filed as one batch, with the same answers one at a time gave`() {
        sql(
            "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, tag, currentlySyncing, isEnabled) " +
                "VALUES (1, 'f', '', 'https://example.org/feed', '', 0, 0, 0, '', 0, 1)"
        )
        article("a")
        article("b")
        article("claimed", remoteId = "old")
        val attached = runBlocking {
            ArticleRepository(db).attachRemoteIds(
                listOf(
                    "https://example.org/a" to "1",
                    "https://example.org/b" to "2",
                    // Already the server's under another id: left as it was.
                    "https://example.org/claimed" to "3",
                    // A link this phone never fetched.
                    "https://example.org/elsewhere" to "4",
                    // The same link twice on one page: the first id wins.
                    "https://example.org/a" to "5",
                )
            )
        }
        assertEquals(2, attached)
        assertEquals("1", remoteIdOf("a"))
        assertEquals("2", remoteIdOf("b"))
        assertEquals("old", remoteIdOf("claimed"))
        assertEquals(0, runBlocking { ArticleRepository(db).attachRemoteIds(emptyList()) })
    }

    @Test
    fun `the match loop files each page once, in a transaction`() {
        val repo = File("src/main/java/com/saulhdev/feeder/data/repository/ArticleRepository.kt").readText()
        val batch = repo.substring(repo.indexOf("suspend fun attachRemoteIds("))
        assertTrue(batch.substring(0, batch.indexOf("\n    }\n")).contains("db.withTransaction {"))

        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        val loop = service.substring(service.indexOf("private suspend fun mapRemoteIds"), service.indexOf("private suspend fun queueNewlyMatched"))
        assertTrue(loop.contains("articles.attachRemoteIds(items.mapNotNull { it.mapping() })"))
        assertFalse("one write per item again", loop.contains("articles.attachRemoteId("))
    }

    @Test
    fun `to send counts what went into the outbox, not what was matched`() {
        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        val queue = service.substring(service.indexOf("private suspend fun queueNewlyMatched"))
        val body = queue.substring(0, queue.indexOf("\n    }\n"))
        assertTrue(body.contains("queued = (read + starred.map { it.uuid }).toSet().size"))
        assertTrue(body.trimEnd().endsWith("return queued"))
    }
}
