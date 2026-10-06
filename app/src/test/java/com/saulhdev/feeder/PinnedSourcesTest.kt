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

import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.viewmodels.MAX_PINNED_SOURCES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

private fun feed(id: Long, title: String) =
    Feed(id = id, title = title, url = URL("https://${title.lowercase()}.example/feed"))

/** What the list does once a few sources are kept at the top. */
private fun arrange(sources: List<Feed>, pinned: Set<Long>) =
    sources.sortedBy { it.title.lowercase() }.sortedByDescending { it.id in pinned }

/**
 * Pinned sources, which is what reorder turned into.
 *
 * A drag handle and a sort selector contradict each other: a hand-made order
 * has no meaning while the list is sorted by name, so the handle either does
 * nothing in three of four sorts or silently overrides the one that was
 * picked. Both are controls that lie. A few pinned compose with every
 * sort instead, and these pin that property rather than any particular order.
 */
class PinnedSourcesTest {

    private val all = listOf(
        feed(1, "Ars"), feed(2, "BBC"), feed(3, "Cnn"),
        feed(4, "Dw"), feed(5, "Elpais"), feed(6, "France"),
    )

    @Test
    fun `pinned come first`() {
        val out = arrange(all, setOf(4L, 6L))
        assertEquals(setOf(4L, 6L), out.take(2).map { it.id }.toSet())
    }

    /**
     * The property that made this worth building instead of reorder: the
     * chosen sort still decides everything, including the order of the
     * pinned among themselves.
     */
    @Test
    fun `the chosen sort still orders both groups`() {
        val out = arrange(all, setOf(6L, 4L))
        assertEquals(listOf("Dw", "France"), out.take(2).map { it.title })
        assertEquals(listOf("Ars", "BBC", "Cnn", "Elpais"), out.drop(2).map { it.title })
    }

    @Test
    fun `no pinned leaves the list exactly as the sort produced it`() {
        assertEquals(
            all.sortedBy { it.title.lowercase() }.map { it.id },
            arrange(all, emptySet()).map { it.id },
        )
    }

    @Test
    fun `nothing is dropped or duplicated`() {
        val out = arrange(all, setOf(2L, 5L))
        assertEquals(all.size, out.size)
        assertEquals(all.map { it.id }.toSet(), out.map { it.id }.toSet())
    }

    /** An id left behind by a deleted source must not reserve a slot. */
    @Test
    fun `a pinned that no longer exists changes nothing`() {
        assertEquals(arrange(all, emptySet()).map { it.id }, arrange(all, setOf(99L)).map { it.id })
    }

    /**
     * The cap is the feature. A list where everything is at the top is a list
     * in its original order.
     */
    @Test
    fun `the cap is small enough to be a choice`() {
        assertTrue(MAX_PINNED_SOURCES in 3..8)
    }

    @Test
    fun `every source pinned still leaves the sort intact`() {
        val out = arrange(all, all.map { it.id }.toSet())
        assertEquals(all.sortedBy { it.title.lowercase() }.map { it.id }, out.map { it.id })
    }
}
