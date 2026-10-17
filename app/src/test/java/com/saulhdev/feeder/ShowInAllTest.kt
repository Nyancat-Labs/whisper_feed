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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A source kept out of All, asked for on 17 October 2026: a feed added to be
 * read under its own category, not in the main stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShowInAllTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    private fun sql(s: String) = db.openHelper.writableDatabase.execSQL(s)

    private fun feed(id: Long, showInAll: Boolean, enabled: Boolean = true) = sql(
        "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, tag, currentlySyncing, isEnabled, showInAll) " +
            "VALUES ($id, 'f$id', '', 'https://example.org/$id', '', 0, 0, 0, 'Corner', 0, ${if (enabled) 1 else 0}, ${if (showInAll) 1 else 0})"
    )

    private fun article(uuid: String, feedId: Long, time: Long) = sql(
        "INSERT INTO Article (uuid, guid, title, plainTitle, plainSnippet, description, feedId, firstSyncedTime, primarySortTime, categories, pinned, bookmarked, readAt, pubDateV2) " +
            "VALUES ('$uuid', '$uuid', '', '', '', '', $feedId, $time, $time, '[]', 0, 0, 0, $time)"
    )

    private fun ids(everything: Boolean) = runBlocking {
        db.feedArticleDao().loadAllEnabledFeedItems(100, everything).map { it.article.uuid }
    }

    @Test
    fun `All leaves the source out, search and focus still find it`() {
        feed(1, showInAll = true)
        feed(2, showInAll = false)
        feed(3, showInAll = false, enabled = false)
        article("in", 1, 3)
        article("corner", 2, 2)
        article("off", 3, 1)
        assertEquals(listOf("in"), ids(everything = false))
        // A source that is switched off stays out of everything, as before.
        assertEquals(listOf("in", "corner"), ids(everything = true))
    }

    /**
     * Left out by the query, not afterwards: the feed shows a window of the
     * newest articles, and a busy source filtered out of it later would leave
     * All with fewer than the window holds.
     */
    @Test
    fun `the window fills from the sources that are in All`() {
        feed(1, showInAll = true)
        feed(2, showInAll = false)
        (1..5).forEach { article("corner$it", 2, 100L + it) }
        (1..3).forEach { article("in$it", 1, it.toLong()) }
        val window = runBlocking { db.feedArticleDao().loadAllEnabledFeedItems(3, false) }
        assertEquals(listOf("in3", "in2", "in1"), window.map { it.article.uuid })
    }

    @Test
    fun `every source added before this stays in All`() {
        sql(
            "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, tag, currentlySyncing, isEnabled) " +
                "VALUES (9, 'old', '', 'https://example.org/9', '', 0, 0, 0, '', 0, 1)"
        )
        article("old", 9, 1)
        assertEquals(listOf("old"), ids(everything = false))
    }

    /** Only All itself: a chip, a search or a focused source take everything. */
    @Test
    fun `only All itself leaves the source out`() {
        val vm = File("src/main/java/com/saulhdev/feeder/viewmodels/ArticleListViewModel.kt").readText()
        assertTrue(vm.contains("val all = categories.isEmpty() && !searching && focused == null"))
        assertTrue(vm.contains("articleRepo.getEnabledFeedItems(limit, everything = !all)"))
    }

    /** Said in the sources list too, or the reason is forgotten. */
    @Test
    fun `the sources list says a source is not in All`() {
        val row = File("src/main/java/com/saulhdev/feeder/ui/components/SourceItem.kt").readText()
        assertTrue(row.contains(".takeIf { !source.showInAll && source.isEnabled }"))
        assertTrue(row.contains("listOfNotNull(notInAll)"))
    }

}
