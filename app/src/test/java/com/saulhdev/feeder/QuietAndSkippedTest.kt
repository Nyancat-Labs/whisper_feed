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

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.db.models.SourceEngagement
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import com.saulhdev.feeder.ui.overlay.FeedEmphasis
import com.saulhdev.feeder.ui.overlay.FeedEmphasisMemory
import com.saulhdev.feeder.ui.overlay.feedEmphasisFor
import com.saulhdev.feeder.ui.overlay.isSkippedSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URL
import kotlin.time.Instant

private const val NOW = 1_800_000_000_000L
private var n = 0

private fun art(source: String, hoursAgo: Long, image: Boolean = true) = FeedItem(
    article = Article(
        uuid = "q${n++}",
        title = "An ordinary headline of about the right length",
        description = "A summary.",
        imageUrl = if (image) "https://e/x.jpg" else null,
        primarySortTime = Instant.fromEpochMilliseconds(NOW - hoursAgo * 3_600_000),
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

private fun feedId(source: String) = source.hashCode().toLong()

/**
 * The two rules that act on something other than the article in front of them.
 *
 * The rescue acts on a source's silence; the fade acts on a reader's habit of
 * scrolling past. Both override or alter what the ordinary scoring produced,
 * so both are where a mistake is most visible and least explicable — hence the
 * caution encoded here, and the tests for the cases where they must *not* fire.
 */
class QuietAndSkippedTest {

    @Before fun clean() = FeedEmphasisMemory.forget()
    @After fun tidy() = FeedEmphasisMemory.forget()

    private fun sizes(items: List<FeedItem>, habit: Map<Long, Float>) =
        feedEmphasisFor(items, emptyMap(), NOW, habit)

    /* ------------------------------------------------------------- rescue -- */

    /** The case it exists for: a source you read, back after a fortnight. */
    @Test
    fun `a well-read source returning after a fortnight gets a large slot`() {
        val items = listOf(art("Daily", 1)) +
            (1..8).map { art("Daily", it.toLong()) } +
            art("Quiet", 336)
        val s = sizes(items, mapOf(feedId("Quiet") to 1f))
        assertEquals(FeedEmphasis.Large, s.last())
    }

    /** A source going quiet proves nothing on its own. */
    @Test
    fun `a quiet source nobody reads is not rescued`() {
        val items = (1..8).map { art("Daily", it.toLong()) } + art("Quiet", 336)
        val s = sizes(items, mapOf(feedId("Quiet") to 0.1f))
        assertTrue(s.last() != FeedEmphasis.Large)
    }

    /**
     * Nor does being well read, if the source never went away.
     *
     * Four days old: too old to earn a large slot on its own merits, and not
     * silent long enough to be rescued. A fresher article would have been
     * promoted by the ordinary scoring and proved nothing about this rule —
     * which is what the first version of this test did.
     */
    @Test
    fun `a well-read source that never went quiet is not rescued`() {
        val items = (1..8).map { art("Daily", it.toLong()) } + art("Recent", 100)
        val s = sizes(items, mapOf(feedId("Recent") to 1f))
        assertTrue(s.last() != FeedEmphasis.Large)
    }

    /** The large shapes need a picture, so a rescue without one helps nobody. */
    @Test
    fun `a returning article with no picture is not rescued`() {
        val items = (1..8).map { art("Daily", it.toLong()) } +
            art("Quiet", 336, image = false)
        val s = sizes(items, mapOf(feedId("Quiet") to 1f))
        assertTrue(s.last() != FeedEmphasis.Large)
    }

    /** A source back with three articles is back, not entitled to the screen. */
    @Test
    fun `only the newest article from a returning source is rescued`() {
        val items = (1..8).map { art("Daily", it.toLong()) } +
            listOf(art("Quiet", 336), art("Quiet", 340), art("Quiet", 350))
        val s = sizes(items, mapOf(feedId("Quiet") to 1f))
        assertEquals(1, s.takeLast(3).count { it == FeedEmphasis.Large })
    }

    /** However many sources went quiet, the feed is not made of rescues. */
    @Test
    fun `no more than the cap are rescued at once`() {
        val quiet = listOf("A", "B", "C", "D").map { art(it, 336) }
        val items = (1..12).map { art("Daily", it.toLong()) } + quiet
        val habit = listOf("A", "B", "C", "D").associate { feedId(it) to 1f }
        val s = sizes(items, habit)
        assertTrue(s.count { it == FeedEmphasis.Large } <= ArticleWeight.RESCUE_MAX + 1)
    }

    /** With nothing learned about anybody, the rule cannot fire at all. */
    @Test
    fun `no reading history means no rescues`() {
        val items = (1..8).map { art("Daily", it.toLong()) } + art("Quiet", 336)
        assertTrue(sizes(items, emptyMap()).last() != FeedEmphasis.Large)
    }

    /* --------------------------------------------------------------- fade -- */

    @Test
    fun `a source whose articles all go past is skipped`() {
        assertTrue(isSkippedSource(SourceEngagement(feedId = 1, score = 4, opened = 0, seen = 80)))
    }

    /**
     * The floor on evidence. A source that arrived last week must not be
     * condemned on a handful of articles nobody happened to be in the mood for.
     */
    @Test
    fun `a new source is not judged yet`() {
        assertFalse(isSkippedSource(SourceEngagement(feedId = 1, score = 0, opened = 0, seen = 5)))
        assertFalse(
            isSkippedSource(
                SourceEngagement(feedId = 1, score = 0, opened = 0, seen = ArticleWeight.SKIPPED_MIN_SEEN - 1)
            )
        )
    }

    /** Stopping at the headlines is enough to stay bright, without opening any. */
    @Test
    fun `a source that is looked at but rarely opened is not skipped`() {
        // Eighty seen, most of them glanced at: well above half a point each.
        assertTrue(
            !isSkippedSource(SourceEngagement(feedId = 1, score = 60, opened = 0, seen = 80))
        )
    }

    /** And a source with a lot of articles is not condemned for volume. */
    @Test
    fun `a busy source that is read is not skipped`() {
        assertFalse(isSkippedSource(SourceEngagement(feedId = 1, score = 900, opened = 40, seen = 300)))
    }
}
