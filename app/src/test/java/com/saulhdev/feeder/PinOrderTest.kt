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
import com.saulhdev.feeder.viewmodels.spreadSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import kotlin.time.Instant

private var seq = 0

private fun item(source: String, pinned: Boolean = false, read: Boolean = false) = FeedItem(
    article = Article(
        uuid = "p${seq++}",
        title = "Headline",
        description = "A summary.",
        pinned = pinned,
        readAt = if (read) 1L else 0L,
        primarySortTime = Instant.fromEpochMilliseconds(1_800_000_000_000L),
    ),
    feed = Feed(
        id = source.hashCode().toLong(),
        title = source,
        url = URL("https://${source.lowercase()}.example/feed"),
    ),
)

/** What the feed does before it is drawn: pins first, then spread out. */
private fun order(items: List<FeedItem>) =
    spreadSources(items.sortedByDescending { it.pinned })

/**
 * Where a pinned article ends up.
 *
 * A pin means "keep this in front of me", and the reader's own description of
 * what that should do is precise: first in the list, under the glance row and
 * the chips, scrolling away like anything else, and still first after a
 * refresh. It is not stuck to the viewport — that was a misreading, and a card
 * that follows you down the feed covers what you are trying to read.
 *
 * Everything here is about the list it produces, because that is where the
 * behaviour now lives.
 */
class PinOrderTest {

    @Test
    fun `a pinned article comes first`() {
        val pin = item("Quiet", pinned = true)
        val rest = (1..6).map { item("Busy") }
        assertEquals(pin.id, order(rest + pin).first().id)
    }

    /**
     * The bug this was reported as. The spreading rule stops one source
     * filling the screen, and it was applying that to pins too: pin four
     * things from one source and the fourth was deferred to the bottom of the
     * feed, which looks exactly like pinning not working.
     */
    @Test
    fun `several pins from one source all stay at the top`() {
        val pins = (1..4).map { item("Same", pinned = true) }
        val rest = (1..8).map { item("Other") }
        val out = order(rest + pins)
        assertEquals(pins.map { it.id }.toSet(), out.take(4).map { it.id }.toSet())
    }

    /** Being read changes nothing about where a pin sits. */
    @Test
    fun `a read article can be pinned to the top`() {
        val pin = item("Quiet", pinned = true, read = true)
        val rest = (1..6).map { item("Busy") }
        assertEquals(pin.id, order(rest + pin).first().id)
    }

    /** And nothing is lost to the rule, pinned or not. */
    @Test
    fun `spreading never drops an article`() {
        val items = (1..5).map { item("A") } + (1..3).map { item("B") } +
            item("A", pinned = true)
        val out = order(items)
        assertEquals(items.size, out.size)
        assertEquals(items.map { it.id }.toSet(), out.map { it.id }.toSet())
    }

    /** The rule still does its job where no pin is involved. */
    @Test
    fun `an unpinned run from one source is still broken up`() {
        val items = (1..6).map { item("Flood") } + (1..3).map { item("Other") }
        val out = spreadSources(items)
        var run = 0
        var worst = 0
        var last: String? = null
        out.forEach {
            run = if (it.sourceId == last) run + 1 else 1
            last = it.sourceId
            if (run > worst) worst = run
        }
        assertTrue("a run of $worst got through", worst <= 3)
    }

    @Test
    fun `a feed of pins is left alone rather than reshuffled`() {
        val pins = (1..5).map { item("Same", pinned = true) }
        assertEquals(pins.map { it.id }, order(pins).map { it.id })
    }
}
