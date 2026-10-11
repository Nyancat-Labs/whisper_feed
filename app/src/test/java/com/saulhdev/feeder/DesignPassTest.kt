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
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.ui.components.siteOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URL

/**
 * The design pass of 11 October 2026, after uxpeak's principles: a button
 * names what it does, an action is undone rather than asked about, and a
 * card leads with the name people know, not the address.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DesignPassTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() {
        db.close()
        stopKoin()
    }

    private fun sql(s: String) = db.openHelper.writableDatabase.execSQL(s)

    private fun feed(id: Int, tag: String) = sql(
        "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, tag, currentlySyncing, isEnabled) " +
            "VALUES ($id, 'f$id', '', 'https://example.org/$id', '', 0, 0, 0, '$tag', 0, 1)"
    )

    private fun tagsOf(id: Int): Set<String> =
        db.openHelper.readableDatabase.query("SELECT tag FROM Feeds WHERE id = $id").use {
            it.moveToFirst()
            it.getString(0).split(",").filter(String::isNotEmpty).toSet()
        }

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `undoing a deleted category puts it back on the same sources and keeps later edits`() {
        feed(1, "Tech,News")
        feed(2, "Tech")
        feed(3, "News")
        // The repository asks for WorkManager when it is built; nothing here
        // schedules work.
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        startKoin { modules(module { single { WorkManager.getInstance(context) } }) }
        val repo = SourcesRepository(db)
        val ids = runBlocking { repo.deleteTag("Tech") }
        assertEquals(setOf(1L, 2L), ids.toSet())
        assertEquals(setOf("News"), tagsOf(1))
        assertEquals(emptySet<String>(), tagsOf(2))
        // Edited between the delete and the Undo.
        sql("UPDATE Feeds SET tag = 'News,Misc' WHERE id = 1")
        runBlocking { repo.restoreTag("Tech", ids) }
        assertEquals(setOf("News", "Misc", "Tech"), tagsOf(1))
        assertEquals(setOf("Tech"), tagsOf(2))
        assertEquals("never had it", setOf("News"), tagsOf(3))
    }

    @Test
    fun `a category is deleted straight away with Undo, not behind a question`() {
        val page = source("ui/pages/CategoryListPage.kt")
        assertTrue(page.contains("IconButton(onClick = { viewModel.deleteTag(tag) })"))
        assertFalse("the confirm dialog is back", page.contains("category_delete_desc"))
        assertTrue(page.contains("R.plurals.category_deleted"))
        assertTrue(page.contains("viewModel.undoDeleteTag(deleted)"))
        assertTrue("the offer ends with the screen", page.contains("finally {\n            // Also on leaving the screen"))
    }

    @Test
    fun `no dialog that acts says only OK`() {
        // DialogButton's default, the "why this size" panel (it only
        // explains, and OK closes it) and a list-preference picker, where OK
        // after choosing is Android's own convention.
        val allowed = setOf("DialogButton.kt", "ArticleMenu.kt")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .filter { it.readText().contains("android.R.string.ok") }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), offenders)
        assertTrue(source("ui/pages/SourceEditPage.kt").contains("primaryText = stringResource(id = R.string.action_remove)"))
        assertTrue(source("ui/pages/SourceListPage.kt").contains("{ Text(stringResource(action.labelId)) }"))
    }

    @Test
    fun `a source card leads with its name, and shows the address only among duplicates`() {
        val item = source("ui/components/SourceItem.kt")
        assertTrue(item.contains("overlineContent = if (showAddress) {"))
        assertTrue(item.contains("listOfNotNull(siteOf(source).takeIf { !showAddress }) + source.tags"))
        val list = source("ui/pages/SourceListPage.kt")
        assertEquals("only the duplicates view shows addresses", 1, Regex("showAddress = true").findAll(list).count())
        val dup = list.indexOf("duplicateGroups.forEach")
        assertTrue(list.indexOf("showAddress = true") in dup..list.indexOf("return@LazyColumn", dup))

        assertEquals("science.org", siteOf(Feed(url = URL("https://www.science.org/rss/news_current.xml"))))
        assertEquals("news.google.com", siteOf(Feed(url = URL("https://news.google.com/?output=rss"))))
        assertNull(siteOf(Feed(url = URL("file:/feed.xml"))))
    }

    // From uxpeak's UI/UX Playbook, the same day.

    @Test
    fun `the reader keeps to about 75 characters a line on a wide screen`() {
        val page = source("ui/pages/ArticlePage.kt")
        assertTrue(page.contains("private const val READER_MEASURE_EM = 36f"))
        assertTrue(page.contains("(MaterialTheme.typography.bodyLarge.fontSize * READER_MEASURE_EM).toDp()"))
        assertTrue(page.contains("val side = max(CARD_MARGIN, (maxWidth - measure) / 2)"))
        assertTrue("inside the list, so the margins scroll", page.contains("contentPadding = PaddingValues(horizontal = side)"))
        assertFalse("a fixed margin around the list again", page.contains("start = CARD_MARGIN,"))
    }

    @Test
    fun `an empty feed offers the way out as a button`() {
        val state = source("ui/components/FeedEmptyState.kt")
        assertTrue(state.contains("FeedEmptyReason.NoSources -> onAddSource?.let { R.string.empty_add_source to it }"))
        assertTrue(state.contains("FeedEmptyReason.NothingFetched -> onRetry?.let { R.string.empty_try_again to it }"))
        val showAll = state.substring(state.indexOf("FeedEmptyReason.FilteredOut -> R.string.sources_show_all"))
        listOf("categoryFilter", "tagsFilter", "sourcesFilter").forEach {
            assertTrue("Show all leaves $it on", showAll.substringBefore("FeedEmptyReason.Syncing").contains("prefs.$it.setValue(emptySet())"))
        }
        assertTrue(state.contains("FeedEmptyReason.Syncing -> null"))

        val app = source("ui/pages/ArticleListPage.kt")
        assertTrue(app.contains("navController.navigate(NavRoute.SourceAdd)"))
        assertTrue(app.contains("onRetry = { scope.launch { syncClient.syncAllFeeds() } }"))
        assertTrue(source("ui/overlay/FeedScaffold.kt").contains("onRetry = onRefresh,"))

        // The hints no longer give directions to what the button now does.
        val strings = File("src/main/res/values/strings.xml").readText()
        listOf("Settings, then Sources", "Pull down to try again", "Tap All").forEach {
            assertFalse("still says \"$it\"", strings.contains(it))
        }
    }

    @Test
    fun `the README points GitHub installs at Obtainium`() {
        val readme = File("../README.md").readText()
        val download = readme.substring(readme.indexOf("## Download"), readme.indexOf("## What it does"))
        assertTrue(download.contains("https://github.com/Nyancat-Labs/whisper_feed/releases"))
        assertTrue(download.contains("[Obtainium](https://github.com/ImranR98/Obtainium)"))
        assertTrue(download.contains("three different keys"))
        assertFalse(download.contains("No public release yet"))
    }
}
