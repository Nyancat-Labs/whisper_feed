package com.saulhdev.feeder

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.manager.models.OPMLParser
import com.saulhdev.feeder.manager.models.ParserToDatabase
import com.saulhdev.feeder.manager.models.writeOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * Szept 463: with "Fetch full articles for every feed" on, a source's own
 * switch was greyed out, so one source could not be left out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FullTextOptOutTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    private val plain = Feed(id = 1, title = "076", url = URL("https://076.example/feed.xml"))

    @Test
    fun `a source can be switched off while every feed is on, and on while it is off`() {
        // Global on: off leaves this source out, on brings it back.
        val left = plain.withFullText(on = false, allFeeds = true)
        assertFalse(left.fetchesFullText(allFeeds = true))
        assertTrue(left.withFullText(on = true, allFeeds = true).fetchesFullText(allFeeds = true))
        // Global off: on works as before, and off records no opt-out.
        val chosen = plain.withFullText(on = true, allFeeds = false)
        assertTrue(chosen.fetchesFullText(allFeeds = false))
        val cleared = chosen.withFullText(on = false, allFeeds = false)
        assertEquals(plain, cleared)
        // Untouched sources follow Settings.
        assertTrue(plain.fetchesFullText(allFeeds = true))
        assertFalse(plain.fetchesFullText(allFeeds = false))
    }

    @Test
    fun `the prefetch leaves an opted-out source alone, but still fetches its saved articles`() = runBlocking {
        val sql = db.openHelper.writableDatabase
        for ((id, off) in listOf(1L to 0, 2L to 1)) {
            sql.execSQL(
                "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, fullTextOff, tag, currentlySyncing, isEnabled) " +
                    "VALUES ($id, 'f$id', '', 'https://example.org/$id', '', 0, 0, 0, $off, '', 0, 1)"
            )
        }
        for ((uuid, feedId, saved) in listOf(Triple("in", 1L, 0), Triple("out", 2L, 0), Triple("kept", 2L, 1))) {
            sql.execSQL(
                "INSERT INTO Article (uuid, guid, title, plainTitle, plainSnippet, description, feedId, firstSyncedTime, primarySortTime, categories, pinned, bookmarked, readAt, link) " +
                    "VALUES ('$uuid', '$uuid', '', '', '', '', $feedId, 0, 1000, '[]', 0, $saved, 0, 'https://example.org/a/$uuid')"
            )
        }
        val waiting = db.feedArticleDao().getArticleIdLinks(allFeeds = true, since = 0).first().map { it.uuid }.toSet()
        assertEquals(setOf("in", "kept"), waiting)
    }

    @Test
    fun `a backup keeps a source's opt-out`() = runBlocking {
        val out = ByteArrayOutputStream()
        val off = plain.copy(id = 0).withFullText(on = false, allFeeds = true)
        writeOutputStream(out, mapOf("" to listOf(off)))
        val restored = mutableListOf<Feed>()
        OPMLParser(object : ParserToDatabase<Feed> {
            override suspend fun getItem(id: String): Feed? = null
            override suspend fun saveItem(item: Feed) { restored += item }
        }).parseInputStream(ByteArrayInputStream(out.toByteArray()))
        assertTrue(restored.single().fullTextOff)
        assertFalse(restored.single().fullTextByDefault)
    }
}
