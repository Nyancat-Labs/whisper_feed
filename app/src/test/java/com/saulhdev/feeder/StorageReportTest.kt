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
import com.saulhdev.feeder.data.db.dao.SourceArticleCount
import com.saulhdev.feeder.data.db.dao.StorageCounts
import com.saulhdev.feeder.utils.DiskUse
import com.saulhdev.feeder.utils.StorageReport
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.syncOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private const val NOW = 1_800_000_000_000L
private const val DAY = 24 * 60 * 60_000L

/**
 * 12,884 articles on 29 September and 19,398 three days later, with the sync
 * range at a week, and nothing in the report to say whether the week was still
 * filling after a large import or the clean-up had stopped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StorageReportTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    private fun sql(s: String) = db.openHelper.writableDatabase.execSQL(s)

    private fun feed(id: Long, title: String) = sql(
        "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, tag, currentlySyncing, isEnabled) " +
            "VALUES ($id, '$title', '', 'https://example.org/$id', '', 0, 0, 0, '', 0, 1)"
    )

    private var n = 0
    private fun article(feedId: Long, pubAgeDays: Double?, seenAgeDays: Double = 0.0, read: Boolean = false, saved: Boolean = false) {
        val uuid = "a${n++}"
        val pub = pubAgeDays?.let { NOW - (it * DAY).toLong() } ?: 0L
        sql(
            "INSERT INTO Article (uuid, guid, title, plainTitle, plainSnippet, description, feedId, firstSyncedTime, primarySortTime, categories, pinned, bookmarked, readAt, pubDateV2) " +
                "VALUES ('$uuid', '$uuid', '', '', '', '', $feedId, ${NOW - (seenAgeDays * DAY).toLong()}, $pub, '[]', 0, ${if (saved) 1 else 0}, ${if (read) NOW else 0}, $pub)"
        )
    }

    private fun counts(rangeDays: Int = 7): StorageCounts = runBlocking {
        val (day, three, cutoff, ahead) = StorageReport.cutoffs(NOW, rangeDays)
        db.feedArticleDao().storageCounts(day, three, cutoff, ahead)
    }

    @Test
    fun `articles are counted by age, by the date the clean-up goes by`() {
        feed(1, "Irish Independent")
        feed(2, "Aeon")
        article(1, 0.5)
        article(1, 0.2, read = true)
        article(1, 2.0, seenAgeDays = 2.0)
        article(1, 5.0, seenAgeDays = 5.0)
        article(1, 9.0, seenAgeDays = 9.0)
        article(2, 40.0, seenAgeDays = 40.0, saved = true)
        article(2, -3.0)
        article(2, null)

        val c = counts()
        assertEquals(8, c.total)
        assertEquals(7, c.unread)
        assertEquals(1, c.saved)
        assertEquals("under a day, the one dated ahead included", 3, c.underDay)
        assertEquals(1, c.dayToThree)
        assertEquals(1, c.threeToRange)
        assertEquals("the saved one is not counted as overdue", 2, c.pastRange)
        assertEquals(1, c.datedAhead)
        assertEquals(1, c.undated)
        assertEquals(4, c.addedDay)
        assertEquals("the oldest that is not saved", NOW - 9 * DAY, c.oldest)

        val largest = runBlocking { db.feedArticleDao().largestSources(5) }
        assertEquals(listOf(SourceArticleCount("Irish Independent", 5), SourceArticleCount("Aeon", 3)), largest)
    }

    @Test
    fun `an empty database answers with zeros, not a failure`() {
        val c = counts()
        assertEquals(0, c.total)
        assertEquals(0, c.pastRange)
        assertNull(c.oldest)
    }

    @Test
    fun `the section reads as one line per question`() {
        val c = StorageCounts(19398, 6, 0, 14315, 2650, 5300, 9100, 340, 12, 0, 2130, NOW - (8.4 * DAY).toLong())
        val lines = StorageReport.lines(
            c, NOW, 7, "25", fullTextForAll = false, fullTextOnMobile = false,
            largest = listOf(SourceArticleCount("Irish Independent", 820)),
            disk = DiskUse(120_000_000, 19000, 80_000_000, 2100, 60_000_000, 10_000_000),
        )
        assertEquals("Settings:          keep 1 week, 25 items per feed per fetch, full text for all feeds no, on mobile data no", lines[0])
        assertEquals("Articles:          19398 (unread 14315, saved 6, pinned 0)", lines[1])
        assertEquals("By date:           under 1 day 2650 · 1-3 days 5300 · 3 days to 1 week 9100 · older than 1 week, not saved 340", lines[2])
        assertEquals("Oldest kept:       8.4 days old (not saved or pinned)", lines[3])
        assertEquals("Added last 24 h:   2130", lines[4])
        assertTrue(lines.any { it == "Most stored:       Irish Independent 820" })
        assertTrue(lines.last(), lines.last().startsWith("On disk:           database 120.0 MB, feed text 19000 files 80.0 MB"))
        assertEquals("3 days", StorageReport.rangeName(3))
        assertEquals("1 month", StorageReport.rangeName(30))
    }

    @Test
    fun `disk use is measured by the names article files are given`() {
        val dir = temp.newFolder("files")
        File(dir, "a.txt.gz").writeBytes(ByteArray(100))
        File(dir, "b.txt.gz").writeBytes(ByteArray(50))
        File(dir, "a.full.html.gz").writeBytes(ByteArray(300))
        File(dir, "saved").mkdirs()
        File(dir, "saved/pic.jpg").writeBytes(ByteArray(7))
        val dbFile = temp.newFile("NeoFeed").apply { writeBytes(ByteArray(1000)) }
        val use = DiskUse.measure(listOf(dbFile, File(dbFile.path + "-wal")), dir)
        assertEquals(DiskUse(1000, 2, 150, 1, 300, 7), use)
        val late = DiskUse.measure(listOf(dbFile), dir, stopAt = 0L)
        assertTrue("past its deadline it says so", late.partial)
        assertTrue(late.describe().endsWith("(counting stopped early)"))
    }

    @Test
    fun `each sync says how many articles aged out`() {
        assertEquals("ok (120 feeds, 340 articles aged out)", syncOutcome(SyncResult(due = 120, agedOut = 340)))
        assertEquals("ok (120 feeds)", syncOutcome(SyncResult(due = 120)))
        val sync = File("src/main/java/com/saulhdev/feeder/manager/sync/RssLocalSync.kt").readText()
        assertEquals("every clean-up is counted", 3, Regex("agedOut\\?\\.addAndGet\\(cleanUpFeed\\(").findAll(sync).count())
        assertEquals("and no clean-up goes uncounted", 3, Regex("cleanUpFeed\\(articleRepo, ").findAll(sync).count())
        assertTrue(sync.contains("agedOut = agedOutArticles.get(),"))
    }

    @Test
    fun `the report carries the section`() {
        val report = File("src/main/java/com/saulhdev/feeder/utils/Diagnostics.kt").readText()
        assertTrue(report.contains("appendLine(\"== Storage ==\")"))
        assertTrue(report.contains("context.getDatabasePath(NeoFeedDb.NAME)"))
    }
}
