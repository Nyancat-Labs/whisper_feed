package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.Clustering
import com.saulhdev.feeder.ui.overlay.clusterStories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import kotlin.time.Instant

private const val NOW = 1_800_000_000_000L

private var nextId = 0

private fun item(
    title: String,
    source: String,
    minutesAgo: Long = 30,
    tags: String = "News",
) = FeedItem(
    article = Article(
        uuid = "a${nextId++}",
        title = title,
        primarySortTime = Instant.fromEpochMilliseconds(NOW - minutesAgo * 60_000),
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        tag = tags,
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

/**
 * The clustering has to be right about two opposite things: it must find the
 * story when several papers run it, and it must not invent one out of
 * headlines that merely share a topic. Both failures look identical from the
 * outside — an article in the biggest slot — so both are tested.
 */
class StoryClusterTest {

    @Test
    fun `three sources on one story is a story`() {
        val items = listOf(
            item("Hurricane Delphine makes landfall in Florida", "BBC"),
            item("Delphine strikes Florida coast as thousands evacuate", "Reuters"),
            item("Florida braces as Hurricane Delphine arrives", "Guardian"),
        )
        val clusters = clusterStories(items, NOW)
        assertEquals(3, clusters.size)
        assertEquals(3, clusters.values.first().sources)
    }

    @Test
    fun `only the lead is promoted, and it is the newest report`() {
        val items = listOf(
            item("Hurricane Delphine makes landfall in Florida", "BBC", minutesAgo = 300),
            item("Delphine strikes Florida coast as thousands evacuate", "Reuters", minutesAgo = 20),
            item("Florida braces as Hurricane Delphine arrives", "Guardian", minutesAgo = 180),
        )
        val clusters = clusterStories(items, NOW)
        val lead = clusters.values.first().leadId
        assertEquals(items[1].id, lead)
        // Every member knows the cluster; only one of them is the lead.
        assertEquals(1, clusters.count { (id, c) -> c.leadId == id })
    }

    @Test
    fun `one prolific source posting six times is not breaking news`() {
        val items = (1..6).map {
            item("Hurricane Delphine update number $it from the coast", "WireService")
        }
        assertTrue(clusterStories(items, NOW).isEmpty())
    }

    @Test
    fun `two sources are not enough`() {
        val items = listOf(
            item("Hurricane Delphine makes landfall in Florida", "BBC"),
            item("Delphine strikes Florida coast as thousands evacuate", "Reuters"),
        )
        assertTrue(clusterStories(items, NOW).isEmpty())
    }

    @Test
    fun `unrelated headlines sharing a common word are not one story`() {
        val items = listOf(
            item("Market rally continues into second week", "BBC"),
            item("Market stalls open again after refurbishment", "Guardian"),
            item("Farmers market returns to the square", "Reuters"),
        )
        assertTrue(clusterStories(items, NOW).isEmpty())
    }

    @Test
    fun `a source suffix on every title does not merge that source's articles`() {
        // Feeds that append their own name to every headline were the obvious
        // way for this to go wrong: the suffix is shared by everything.
        val items = listOf(
            item("Council approves the bypass - The Chronicle", "Chronicle"),
            item("Swimming pool reopens - The Chronicle", "Chronicle"),
            item("Library hours extended - The Chronicle", "Chronicle"),
            item("Bakery wins award - The Chronicle", "Chronicle"),
        )
        assertTrue(clusterStories(items, NOW).isEmpty())
    }

    @Test
    fun `old articles fall out of the window`() {
        val stale = Clustering.WINDOW_MS / 60_000 + 60
        val items = listOf(
            item("Hurricane Delphine makes landfall in Florida", "BBC", minutesAgo = stale),
            item("Delphine strikes Florida coast as thousands evacuate", "Reuters", minutesAgo = stale),
            item("Florida braces as Hurricane Delphine arrives", "Guardian", minutesAgo = stale),
        )
        assertTrue(clusterStories(items, NOW).isEmpty())
    }

    @Test
    fun `sources not filed under News are left alone`() {
        val items = listOf(
            item("Pixel 12 Pro review: the camera finally delivers", "XDA", tags = "Tech"),
            item("Pixel 12 Pro review roundup, camera tested", "Verge", tags = "Tech"),
            item("Reviewing the Pixel 12 Pro camera", "AndroidPolice", tags = "Tech"),
        )
        assertTrue(clusterStories(items, NOW).isEmpty())
        // The same burst does cluster once the reader files them as news.
        val asNews = items.map { it.copy(feed = it.feed.copy(tag = "News")) }
        assertTrue(clusterStories(asNews, NOW).isNotEmpty())
    }

    @Test
    fun `a headline that shares nothing joins no cluster`() {
        val items = listOf(
            item("Hurricane Delphine makes landfall in Florida", "BBC"),
            item("Delphine strikes Florida coast as thousands evacuate", "Reuters"),
            item("Florida braces as Hurricane Delphine arrives", "Guardian"),
            item("Chancellor announces autumn budget date", "Times"),
        )
        val clusters = clusterStories(items, NOW)
        assertEquals(3, clusters.size)
        assertNull(clusters[items[3].id])
    }

    @Test
    fun `the source's own name is not what ties a story together`() {
        val items = listOf(
            item("Delphine landfall in Florida", "BBC"),
            item("Delphine reaches Florida", "Reuters"),
            item("Delphine over Florida now", "Guardian"),
        )
        // Two distinctive words shared, from three sources: a story.
        assertEquals(3, clusterStories(items, NOW).size)
    }
}
