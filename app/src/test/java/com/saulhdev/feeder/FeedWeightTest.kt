package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import com.saulhdev.feeder.ui.overlay.FeedEmphasis
import com.saulhdev.feeder.ui.overlay.articleWeight
import com.saulhdev.feeder.ui.overlay.habitWindowStart
import com.saulhdev.feeder.ui.overlay.feedEmphasisFor
import com.saulhdev.feeder.ui.overlay.parseAffinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.saulhdev.feeder.ui.overlay.FeedEmphasisMemory
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.net.URL
import kotlin.time.Instant

private const val NOW = 1_800_000_000_000L
private var seq = 0

private fun item(
    source: String = "BBC",
    hoursAgo: Long = 1,
    title: String = "A perfectly ordinary headline about something",
    description: String = "A summary.",
    read: Boolean = false,
    saved: Boolean = false,
    pinned: Boolean = false,
    image: String? = "https://example.com/a.jpg",
) = FeedItem(
    article = Article(
        uuid = "w${seq++}",
        title = title,
        description = description,
        imageUrl = image,
        readAt = if (read) NOW - 1000 else 0L,
        bookmarked = saved,
        pinned = pinned,
        primarySortTime = Instant.fromEpochMilliseconds(NOW - hoursAgo * 3_600_000),
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

/**
 * The weighting decides how much of the screen an article gets, so getting it
 * wrong is visible on every scroll. These pin the properties that were argued
 * for rather than the exact numbers, which are still being tuned — a test that
 * asserted 2.5 would fail on every adjustment without ever catching a bug.
 */
class FeedWeightTest {

    private fun weight(item: FeedItem, affinity: Map<String, Int> = emptyMap()) =
        articleWeight(item, affinity, NOW)

    @Test
    fun `an article with no picture can never reach the large band`() {
        val perfect = item(image = null, hoursAgo = 0, saved = true, pinned = true)
        assertTrue(weight(perfect) < ArticleWeight.LARGE_AT)
    }

    @Test
    fun `fresher outranks older, all else equal`() {
        assertTrue(weight(item(hoursAgo = 1)) > weight(item(hoursAgo = 12)))
        assertTrue(weight(item(hoursAgo = 12)) > weight(item(hoursAgo = 100)))
    }

    @Test
    fun `reading an article takes it out of the running`() {
        val unread = item()
        val read = item(read = true)
        assertTrue(weight(read) < weight(unread))
        assertTrue(weight(read) < ArticleWeight.MEDIUM_AT)
    }

    @Test
    fun `a habit cannot outrank a fresh story from elsewhere`() {
        // The property the cap exists for: favouring what someone reads must
        // not become "only ever show that one site".
        val favourite = item(source = "Favourite", hoursAgo = 30)
        val strangerButFresh = item(source = "Stranger", hoursAgo = 1)
        val habit = mapOf(favourite.feed.id to 1f)
        assertTrue(
            articleWeight(favourite, emptyMap(), NOW, habit) <
                    articleWeight(strangerButFresh, emptyMap(), NOW, habit)
        )
    }

    @Test
    fun `affinity is clamped, so a dozen taps cannot own the feed forever`() {
        val ten = mapOf(item().sourceId to 10)
        val three = mapOf(item().sourceId to 3)
        val subject = item()
        assertEquals(
            articleWeight(subject, mapOf(subject.sourceId to 3), NOW),
            articleWeight(subject, mapOf(subject.sourceId to 10), NOW),
            0.001f,
        )
        assertTrue(ten.isNotEmpty() && three.isNotEmpty())
    }

    @Test
    fun `large tiles are spaced out even when everything scores highly`() {
        val items = (0 until 40).map { item(source = "S$it", hoursAgo = 0) }
        val sizes = feedEmphasisFor(items, emptyMap(), NOW)
        val larges = sizes.withIndex().filter { it.value == FeedEmphasis.Large }.map { it.index }
        larges.zipWithNext().forEach { (a, b) ->
            assertTrue("large tiles at $a and $b are too close", b - a > ArticleWeight.LARGE_GAP)
        }
    }

    @Test
    fun `one source cannot take every large slot`() {
        // A morning sync from a favourite source: forty fresh articles, all
        // from one publisher, all scoring identically.
        val items = (0 until 40).map { item(source = "OnlySource", hoursAgo = 0) }
        val sizes = feedEmphasisFor(items, emptyMap(), NOW)
        val larges = sizes.withIndex().filter { it.value == FeedEmphasis.Large }.map { it.index }
        larges.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "same source took large slots at $a and $b",
                b - a > ArticleWeight.SAME_SOURCE_LARGE_GAP,
            )
        }
    }

    @Test
    fun `a flat feed of old articles still opens on something`() {
        // Nothing here clears the large threshold on its own; opening on a
        // uniform wall of small cards reads as a bug rather than a choice.
        val items = (0 until 10).map { item(source = "S$it", hoursAgo = 200) }
        val sizes = feedEmphasisFor(items, emptyMap(), NOW)
        assertTrue(
            "no anchor near the top",
            sizes.take(ArticleWeight.ANCHOR_WITHIN).any { it == FeedEmphasis.Large },
        )
    }

    @Test
    fun `nothing is ever removed, only resized`() {
        val items = (0 until 25).map { item(source = "S$it", read = it % 2 == 0) }
        assertEquals(items.size, feedEmphasisFor(items, emptyMap(), NOW).size)
    }

    @Test
    fun `saving an article is not the same as pinning it`() {
        // These used to be one switch: bookmarking set both, so a saved
        // article collected both bonuses and pinning meant nothing on its own.
        val saved = item(saved = true)
        val pinned = item(pinned = true)
        val both = item(saved = true, pinned = true)
        assertTrue(weight(saved) < weight(pinned))
        assertTrue(weight(pinned) < weight(both))
    }

    @Test
    fun `reading an article is what makes it shrink, which is why size is settled`() {
        // The bug this pins: with read-on-scroll enabled, marking an article
        // read subtracts enough weight to drop it a size, so the card shrank
        // under the reader's finger and everything below it jumped. The weight
        // change is correct and stays; rememberFeedEmphasis settles the size
        // per article for as long as the feed is open so it cannot be seen.
        val items = (0 until 8).map { item(source = "S$it", hoursAgo = 0) }
        val before = feedEmphasisFor(items, emptyMap(), NOW)

        val read = items.mapIndexed { i, it ->
            if (i == 0) it.copy(article = it.article.copy(readAt = NOW - 1000)) else it
        }
        val after = feedEmphasisFor(read, emptyMap(), NOW)

        assertTrue(
            "reading an article should lower its weight",
            weight(read[0]) < weight(items[0]),
        )
        assertEquals(FeedEmphasis.Large, before[0])
        assertTrue(
            "and that is exactly what would resize it mid-scroll",
            before[0] != after[0],
        )
    }

    @Test
    fun `a read article loses the large slot but keeps its card`() {
        // A second device receives most of its reads from the account before
        // it draws the articles, so they are sized read from the start. When
        // read meant Small, a tablet showed a wall of thin rows where the
        // phone showed cards for the same stories.
        val items = (0 until 8).map { item(source = "S$it", hoursAgo = 3, read = true) }
        val sizes = feedEmphasisFor(items, emptyMap(), NOW)
        assertTrue("a read article took a thin row: $sizes", sizes.none { it == FeedEmphasis.Small })
        // The anchor rule still gives the top of the feed one large card.
        assertEquals(1, sizes.take(ArticleWeight.ANCHOR_WITHIN).count { it == FeedEmphasis.Large })
    }

    @Test
    fun `reading does not lift an article that was small unread`() {
        // Past its time and badly shaped: small unread, and small read.
        val weak = item(hoursAgo = 200, title = "Hi", description = "")
        assertTrue(weight(weak) < ArticleWeight.MEDIUM_AT)
        val items = listOf(item(source = "A", hoursAgo = 0), weak.copy(article = weak.article.copy(readAt = NOW - 1000)))
        assertEquals(FeedEmphasis.Small, feedEmphasisFor(items, emptyMap(), NOW)[1])
    }

    @Test
    fun `affinity parses back the way it was written`() {
        assertEquals(mapOf("12" to -2, "7" to 4), parseAffinity(setOf("12:-2", "7:4")))
        // Junk in the set must not take the rest of it down with it.
        assertEquals(mapOf("3" to 1), parseAffinity(setOf("3:1", "nonsense", "", ":9")))
    }
}

/**
 * The memory that stops an article changing size once it has been given one.
 *
 * The bug it exists for is easy to mistake for a feature: read an article,
 * come back, and it is smaller than the ones around it. Reading subtracts from
 * an article's weight, so the moment the sizes are worked out afresh the one
 * you just finished drops a band — and the app's feed is the list pane of a
 * ListDetailPaneScaffold, which on a phone is thrown away and rebuilt every
 * time you open an article.
 */
class FeedEmphasisMemoryTest {

    @Before
    fun clean() = FeedEmphasisMemory.forget()

    @After
    fun tidy() = FeedEmphasisMemory.forget()

    @Test
    fun `the first answer is the one that sticks`() {
        val a = item()
        assertEquals(
            listOf(FeedEmphasis.Large),
            FeedEmphasisMemory.settle(listOf(a), listOf(FeedEmphasis.Large)),
        )
        // Same article, and the weighting has since decided it is small —
        // because it has been read. It keeps the size it was drawn at.
        assertEquals(
            listOf(FeedEmphasis.Large),
            FeedEmphasisMemory.settle(listOf(a), listOf(FeedEmphasis.Small)),
        )
    }

    @Test
    fun `a size survives the feed being composed from scratch`() {
        // What actually happens on a phone: the feed is disposed when an
        // article opens and rebuilt when it closes. The memory is not part of
        // the composition, so it is still there on the way back.
        val a = item()
        FeedEmphasisMemory.settle(listOf(a), listOf(FeedEmphasis.Large))
        val afterReturning = FeedEmphasisMemory.settle(listOf(a), listOf(FeedEmphasis.Small))
        assertEquals(listOf(FeedEmphasis.Large), afterReturning)
    }

    @Test
    fun `an article never seen before takes the size it is offered`() {
        val old = item()
        val new = item()
        FeedEmphasisMemory.settle(listOf(old), listOf(FeedEmphasis.Small))
        assertEquals(
            listOf(FeedEmphasis.Small, FeedEmphasis.Large),
            FeedEmphasisMemory.settle(listOf(old, new), listOf(FeedEmphasis.Large, FeedEmphasis.Large)),
        )
    }

    @Test
    fun `the answer follows the order of the articles asked about`() {
        // The caller maps the result back onto its list positionally, so a
        // reordering sync must not hand an article somebody else's size.
        val a = item()
        val b = item()
        FeedEmphasisMemory.settle(listOf(a, b), listOf(FeedEmphasis.Large, FeedEmphasis.Small))
        assertEquals(
            listOf(FeedEmphasis.Small, FeedEmphasis.Large),
            FeedEmphasisMemory.settle(listOf(b, a), listOf(FeedEmphasis.Medium, FeedEmphasis.Medium)),
        )
    }

    @Test
    fun `a short fresh list does not throw`() {
        // Defensive, because the two lists are built by different code paths
        // and an index mismatch here would crash the feed rather than mis-size
        // one card.
        val a = item()
        assertEquals(
            listOf(FeedEmphasis.Medium),
            FeedEmphasisMemory.settle(listOf(a), emptyList()),
        )
    }
}

/**
 * The habit window, which "Forget everything" moves.
 *
 * The button used to clear only the More/Less scores, leaving every source its
 * read count and most of its weight — so the screen still showed non-zero
 * weights immediately after being told to forget everything.
 */
class HabitWindowTest {

    private val day = 24 * 60 * 60 * 1000L
    private val thirtyDays = ArticleWeight.HABIT_WINDOW_DAYS * day

    @Test
    fun `never reset means the plain thirty day window`() {
        assertEquals(NOW - thirtyDays, habitWindowStart(resetAt = 0L, now = NOW))
    }

    @Test
    fun `a recent reset starts the count there`() {
        val yesterday = NOW - day
        assertEquals(yesterday, habitWindowStart(resetAt = yesterday, now = NOW))
    }

    @Test
    fun `a reset just now counts nothing before it`() {
        assertEquals(NOW, habitWindowStart(resetAt = NOW, now = NOW))
    }

    @Test
    fun `a reset older than the window does not widen it`() {
        val longAgo = NOW - 400 * day
        assertEquals(NOW - thirtyDays, habitWindowStart(resetAt = longAgo, now = NOW))
    }
}
