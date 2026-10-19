package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.utils.newSinceSeen
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL

class NewArticlesTest {

    private val seen = 1_790_000_000_000L

    private fun item(id: String, arrivedMs: Long, readAt: Long = 0L) = FeedItem(
        article = Article(uuid = id, title = id, firstSyncedTime = Instant.fromEpochMilliseconds(arrivedMs), readAt = readAt),
        feed = Feed(id = 1L, title = "Wired", tag = "Tech", url = URL("https://wired.example/feed")),
    )

    @Test
    fun `articles that arrived since the reader last looked are new`() {
        val list = listOf(item("a", seen + 60_000), item("b", seen + 1), item("c", seen - 60_000), item("d", seen))
        assertEquals(2, newSinceSeen(list, seen))
    }

    @Test
    fun `a read article is not new, whenever it came`() {
        assertEquals(0, newSinceSeen(listOf(item("a", seen + 60_000, readAt = seen + 120_000)), seen))
    }

    @Test
    fun `nothing counts before the first look`() {
        assertEquals(0, newSinceSeen(listOf(item("a", seen + 60_000)), 0L))
    }

    @Test
    fun `leaving the app and closing the panel both mark the feed as seen`() {
        val activity = File("src/main/java/com/saulhdev/feeder/MainActivity.kt").readText()
        assertTrue(activity.contains("override fun onStop()") && activity.contains("feedSeenAt.set(System.currentTimeMillis())"))
        val panel = File("src/main/java/com/saulhdev/feeder/manager/service/OverlayView.kt").readText()
        assertTrue(panel.contains("newState == PanelState.CLOSED") && panel.contains("feedSeenAt.set(System.currentTimeMillis())"))
    }

    @Test
    fun `when the feed was seen stays on this phone`() {
        val backup = File("src/main/java/com/saulhdev/feeder/manager/backup/SettingsBackup.kt").readText()
        assertTrue(backup.substringAfter("NOT_PORTABLE").substringBefore(")").contains("\"pref_feed_seen_at\""))
    }
}
