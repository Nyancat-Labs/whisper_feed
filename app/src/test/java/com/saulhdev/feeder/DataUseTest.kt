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

import com.saulhdev.feeder.manager.sync.servedUnchanged
import com.saulhdev.feeder.utils.FULL_TEXT_RETRY_AFTER_MS
import com.saulhdev.feeder.utils.FullTextAttempts
import com.saulhdev.feeder.utils.MAX_FULL_TEXT_ATTEMPTS
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.formatBytes
import com.saulhdev.feeder.utils.fullTextOutcome
import com.saulhdev.feeder.utils.isPermanentHttpFailure
import com.saulhdev.feeder.utils.orphanArticleFiles
import com.saulhdev.feeder.utils.shouldPrefetchFullText
import com.saulhdev.feeder.utils.syncOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val HOUR = 3_600_000L
private const val NOW = 1_800_000_000_000L

/**
 * Where a month's 777 MB went: every feed downloaded in full on every sync,
 * and every refused full-article page downloaded again after every sync.
 */
class DataUseTest {

    @Test
    fun `only the copy already parsed counts as unchanged`() {
        assertTrue("the server said 304", servedUnchanged(networkCode = 304, hadCachedCopy = true))
        assertTrue("the cache answered alone", servedUnchanged(networkCode = null, hadCachedCopy = true))
        assertFalse("a new body", servedUnchanged(networkCode = 200, hadCachedCopy = true))
        assertFalse("nothing cached", servedUnchanged(networkCode = 200, hadCachedCopy = false))
        assertFalse(servedUnchanged(networkCode = null, hadCachedCopy = false))
    }

    @Test
    fun `sizes read the way Android's data screen writes them`() {
        assertEquals("<1 KB", formatBytes(500))
        assertEquals("1 KB", formatBytes(1_500))
        assertEquals("999 KB", formatBytes(999_999))
        assertEquals("2.3 MB", formatBytes(2_345_678))
    }

    @Test
    fun `a sync line says what was unchanged and what it cost`() {
        assertEquals(
            "ok (120 feeds, 95 unchanged, 2 failed, 1.4 MB, queued)",
            syncOutcome(SyncResult(due = 120, failed = 2, unchanged = 95, bytes = 1_400_000), listOf("queued")),
        )
        assertEquals("nothing due (12 KB)", syncOutcome(SyncResult(due = 0, bytes = 12_000)))
    }

    @Test
    fun `a full text line says what it fetched and what it cost`() {
        assertEquals("ok (34 fetched, 5 failed, 12.3 MB)", fullTextOutcome(34, 5, 12_300_000))
        assertEquals("ok (3 fetched)", fullTextOutcome(3, 0, null))
    }

    @Test
    fun `the failure record survives a round trip, and junk reads as never tried`() {
        val a = FullTextAttempts(2, NOW, permanent = false)
        assertEquals(a, FullTextAttempts.decode(a.encode()))
        assertNull(FullTextAttempts.decode(""))
        assertNull(FullTextAttempts.decode("two 5 0"))
        assertNull(FullTextAttempts.decode("1 5 maybe"))
    }

    @Test
    fun `a permanent refusal stays permanent`() {
        val refused = FullTextAttempts.none.next(NOW, permanent = true)
        assertTrue(refused.next(NOW + HOUR, permanent = false).permanent)
        assertEquals(2, refused.next(NOW + HOUR, permanent = false).count)
    }

    @Test
    fun `a refused page is never asked for again`() {
        val refused = FullTextAttempts(1, NOW - 100 * HOUR, permanent = true)
        assertFalse(shouldPrefetchFullText(NOW, refused))
    }

    @Test
    fun `a failed page is retried later, and not for ever`() {
        assertTrue("never tried", shouldPrefetchFullText(NOW, null))
        val once = FullTextAttempts(1, NOW - HOUR, permanent = false)
        assertFalse("too soon", shouldPrefetchFullText(NOW, once))
        assertTrue("later", shouldPrefetchFullText(NOW + FULL_TEXT_RETRY_AFTER_MS, once))
        val spent = FullTextAttempts(MAX_FULL_TEXT_ATTEMPTS, NOW - 100 * HOUR, permanent = false)
        assertFalse("given up", shouldPrefetchFullText(NOW, spent))
    }

    @Test
    fun `paywalls and missing pages are refusals, busy servers are not`() {
        listOf(401, 402, 403, 404, 410).forEach { assertTrue("$it", isPermanentHttpFailure(it)) }
        listOf(200, 408, 429, 500, 502, 503).forEach { assertFalse("$it", isPermanentHttpFailure(it)) }
    }

    @Test
    fun `only old files of articles that are gone are swept`() {
        val old = NOW - 2 * HOUR
        val files = listOf(
            "kept.txt.gz" to old,
            "gone.txt.gz" to old,
            "gone.full.html.gz" to old,
            "gone.full.failed" to old,
            "new.full.html.gz" to NOW - 60_000,
            "datastore" to old,
            "notes.txt" to old,
        )
        assertEquals(
            listOf("gone.txt.gz", "gone.full.html.gz", "gone.full.failed"),
            orphanArticleFiles(files, knownIds = setOf("kept"), nowMs = NOW, minAgeMs = HOUR),
        )
    }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the feed client has a cache to revalidate against`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        assertTrue(sync.contains(".cache(Cache(File(context.cacheDir, \"feeds\"), FEED_CACHE_BYTES))"))
        assertTrue(sync.contains("syncHttpClient(context).getResponse("))
    }

    @Test
    fun `an unchanged feed is not parsed, but still ages out`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        val body = sync.substring(sync.indexOf("private suspend fun syncFeed("))
        val skip = body.substring(body.indexOf("servedUnchanged("), body.indexOf("return FeedFetchResult.Unchanged"))
        assertTrue("only when the source has articles", skip.contains("articleRepo.countInFeed(feedSql.id) > 0"))
        assertTrue("old articles still go", skip.contains("cleanUpFeed(articleRepo, feedSql, filesDir)"))
        assertTrue("before any parsing", body.indexOf("return FeedFetchResult.Unchanged") < body.indexOf("FeedParser()"))
        assertTrue(sync.contains("FeedFetchResult.Unchanged -> {\n                                            unchangedFeeds.incrementAndGet()"))
    }

    @Test
    fun `cleanup removes every file an article has`() {
        val sync = source("manager/sync/RssLocalSync.kt")
        val cleanup = sync.substring(sync.indexOf("private suspend fun cleanUpFeed("))
        assertTrue(cleanup.contains("deleteArticleFiles(itemId = id, filesDir = filesDir)"))
        val blob = source("utils/Blob.kt")
        val delete = blob.substring(blob.indexOf("fun deleteArticleFiles("))
        listOf("blobFile(", "blobFullFile(", "blobFullFailedFile(").forEach {
            assertTrue(it, delete.substringBefore("\n}").contains(it))
        }
        assertTrue(source("NeoApp.kt").contains("sweepOrphanArticleFiles()\n"))
    }

    @Test
    fun `the prefetch skips what it should and remembers what failed`() {
        val full = source("manager/models/FullTextParser.kt")
        assertTrue(full.contains("shouldPrefetchFullText(now, readAttempts(item.uuid, filesDir))"))
        val prefetch = full.substring(full.indexOf("private suspend fun prefetchFullArticle("))
            .substringBefore("\n}\n")
        val rethrow = prefetch.indexOf("if (error is CancellationException) throw error")
        assertTrue("stopping is not failing", rethrow >= 0 && rethrow < prefetch.indexOf("writeText("))
        assertTrue(prefetch.contains("isPermanentHttpFailure(code)"))
        assertTrue("a success clears the record", full.contains("blobFullFailedFile(feedItem.uuid, filesDir).delete()"))
        val worker = full.substring(full.indexOf("override suspend fun doWork()"))
        assertTrue(
            "no history line for a run with nothing to do",
            worker.indexOf("if (toFetch.isEmpty()) return Result.success()") <
                worker.indexOf("SyncLog.started(applicationContext, SyncLog.ORIGIN_FULL_TEXT)"),
        )
    }

    @Test
    fun `a refused response is closed`() {
        val parser = source("manager/models/FeedParser.kt")
        val curl = parser.substring(parser.indexOf("suspend fun OkHttpClient.curlAndOnResponse("))
            .substringBefore("\n}\n")
        val use = curl.indexOf(".use { response ->")
        assertTrue(use >= 0 && use < curl.indexOf("throw HttpStatusException(response.code)"))
    }

    @Test
    fun `every sync line carries what it cost`() {
        val worker = source("manager/sync/FeedSyncer.kt")
        assertTrue(worker.contains("result = result.copy(bytes = bytesSince(receivedBefore))"))
        assertTrue(worker.indexOf("val receivedBefore = receivedBytes()") < worker.indexOf("syncFeeds("))
    }
}
