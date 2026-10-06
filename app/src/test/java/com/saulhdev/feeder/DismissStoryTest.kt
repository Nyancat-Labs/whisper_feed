package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.clusterStories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import kotlin.time.Instant

private const val NOW = 1_800_000_000_000L
private var seq = 0

private fun story(
    title: String,
    source: String,
    minutesAgo: Long = 30,
    dismissedAt: Long = 0L,
) = FeedItem(
    article = Article(
        uuid = "a${seq++}",
        title = title,
        primarySortTime = Instant.fromEpochMilliseconds(NOW - minutesAgo * 60_000),
        dismissedAt = dismissedAt,
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        tag = "News",
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

/**
 * Dismissing a breaking story takes back its promotion, and only that.
 *
 * Breaking news is the one thing the app promotes entirely on its own: a story
 * several sources carry at once is inferred to matter, held to the top and
 * weighted heavily. Nobody asked for it, and the only way out was the scroll —
 * which is not a way out of something stuck to the top, because scrolling back
 * up brings it back exactly as it was.
 *
 * The retraction is one line in the clustering rather than three exemptions,
 * because the hold, the weight bonus and the "Covered by N sources" label are
 * all read from the map it builds. These tests are about that map.
 */
class DismissStoryTest {

    /** The same story, filed by four papers, newest last. */
    private fun fourSources(dismissLead: Boolean = false) = listOf(
        story("Chancellor makes televised address", "Journal", minutesAgo = 90),
        story("Chancellor addresses nation on TV", "Independent", minutesAgo = 70),
        story("Chancellor in televised address tonight", "Guardian", minutesAgo = 50),
        story(
            "Chancellor makes televised address to nation",
            "RTE",
            minutesAgo = 10,
            dismissedAt = if (dismissLead) NOW - 60_000 else 0L,
        ),
    )

    @Test
    fun `four sources on one story make a cluster`() {
        // The control: without this the test below would pass for the wrong
        // reason, by there being no cluster to dismiss in the first place.
        val clusters = clusterStories(fourSources(), NOW)
        assertTrue("expected a cluster", clusters.isNotEmpty())
        val lead = clusters.values.first().leadId
        assertNotNull(clusters[lead])
        assertEquals(4, clusters.values.first().sources)
    }

    @Test
    fun `the newest report leads`() {
        val items = fourSources()
        val clusters = clusterStories(items, NOW)
        assertEquals(items.last().id, clusters.values.first().leadId)
    }

    @Test
    fun `dismissing the lead retracts the whole story`() {
        // Not just the lead's own promotion: every member carries the same
        // cluster, so leaving any of them in it would keep the story claiming
        // to be covered by four sources.
        val clusters = clusterStories(fourSources(dismissLead = true), NOW)
        assertEquals(emptyMap<String, Any>(), clusters)
    }

    @Test
    fun `a dismissed article that leads nothing changes nothing`() {
        // Dismissal is only meaningful on a promoted card, and the menu only
        // offers it there — but the clustering must not be upset by a
        // dismissal that arrived some other way.
        val items = fourSources().toMutableList()
        items[0] = items[0].copy(article = items[0].article.copy(dismissedAt = NOW - 60_000))
        val clusters = clusterStories(items, NOW)
        assertTrue("the story should survive", clusters.isNotEmpty())
        assertEquals(items.last().id, clusters.values.first().leadId)
    }

    @Test
    fun `dismissing does not remove the article from the feed`() {
        // The point of the feature: it loses the promotion, not its place.
        // Clustering reads the list; it never shortens it.
        val items = fourSources(dismissLead = true)
        assertEquals(4, items.size)
        assertNull(clusterStories(items, NOW)[items.last().id])
    }
}
