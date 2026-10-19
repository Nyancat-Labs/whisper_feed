package com.saulhdev.feeder

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.ui.overlay.SwipeWeight
import com.saulhdev.feeder.utils.ReadingTime
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

/**
 * "Not interested" and reading time: what leaves the feed, what comes back,
 * how hard the swipe is, and when a time is worth showing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotInterestedTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    private fun sql(s: String) = db.openHelper.writableDatabase.execSQL(s)

    private fun seed() {
        sql(
            "INSERT INTO Feeds (id, title, description, url, feedImage, lastSync, alternateId, fullTextByDefault, tag, currentlySyncing, isEnabled) " +
                "VALUES (1, 'Example', '', 'https://example.org/feed', '', 0, 0, 0, '', 0, 1)"
        )
        listOf("a", "b", "c").forEachIndexed { i, id ->
            sql(
                "INSERT INTO Article (uuid, guid, title, plainTitle, plainSnippet, description, feedId, firstSyncedTime, primarySortTime, categories, pinned, bookmarked, readAt, pubDateV2) " +
                    "VALUES ('$id', '$id', '', '', '', '', 1, $i, $i, '[]', 0, 0, 0, $i)"
            )
        }
    }

    @Test
    fun `a dismissed article leaves every feed query and shows in Dismissed today`() = runBlocking {
        seed()
        val dao = db.feedArticleDao()
        dao.setHidden("b", 1_000L)
        val feed = dao.loadAllEnabledFeedItems(100, everything = true).map { it.id }
        assertEquals(listOf("c", "a"), feed)
        assertFalse("b" in dao.loadFeedItemsByFeedIds(listOf(1L), 100).map { it.id })
        assertEquals(listOf("b"), dao.hiddenSince(500L).first().map { it.id })
        assertEquals("yesterday's is not today's", emptyList<String>(), dao.hiddenSince(2_000L).first().map { it.id })
        assertEquals(1, dao.countHiddenSince(1L, 0L))
    }

    @Test
    fun `brought back, it is in the feed again`() = runBlocking {
        seed()
        val dao = db.feedArticleDao()
        dao.setHidden("b", 1_000L)
        dao.setHidden("b", 0L)
        assertTrue("b" in dao.loadAllEnabledFeedItems(100, everything = true).map { it.id })
        assertEquals(0, dao.countHiddenSince(1L, 0L))
    }

    @Test
    fun `the swipe only claims a drag that is rightward and clearly sideways`() {
        assertTrue(SwipeWeight.claims(30f, 5f))
        assertFalse("leftward is the launcher's", SwipeWeight.claims(-30f, 0f))
        assertFalse("diagonal is the list's", SwipeWeight.claims(30f, 20f))
        assertFalse(SwipeWeight.claims(0f, 30f))
    }

    @Test
    fun `the swipe is heavy`() {
        val width = 1000f
        val flick = 4000f
        assertFalse("a third, slowly, springs back", SwipeWeight.dismisses(340f, width, 500f, flick))
        assertTrue("a third with a hard flick goes", SwipeWeight.dismisses(340f, width, 5000f, flick))
        assertFalse("a quarter, however hard, springs back", SwipeWeight.dismisses(250f, width, 9000f, flick))
        assertTrue("half, at any speed, goes", SwipeWeight.dismisses(500f, width, 0f, flick))
        // And the finger has to travel twice that, at half resistance.
        assertEquals(0.5f, SwipeWeight.RESISTANCE)
    }

    @Test
    fun `reading time from a page counts its words`() {
        val words = "word ".repeat(920)
        assertEquals(4, ReadingTime.fromPage("<p>$words</p>"))
        assertEquals("never zero for a page with words", 1, ReadingTime.fromPage("<p>Short.</p>"))
        assertEquals(0, ReadingTime.fromPage("<img src=x>"))
    }

    @Test
    fun `a feed's teaser gives no time at all`() {
        assertEquals(0, ReadingTime.fromFeed("<p>${"word ".repeat(40)}</p>"))
        val whole = "word ".repeat(700)
        assertEquals(3, ReadingTime.fromFeed("<p>$whole</p>"))
        assertEquals(
            "a long text that ends by sending the reader away is still a teaser",
            0, ReadingTime.fromFeed("<p>$whole Continue reading…</p>"),
        )
        assertEquals(0, ReadingTime.fromFeed("<p>$whole […]</p>"))
    }
}
