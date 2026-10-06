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
import com.saulhdev.feeder.data.entity.SORT_CHRONOLOGICAL
import com.saulhdev.feeder.data.entity.SORT_SOURCE
import com.saulhdev.feeder.data.entity.SORT_TITLE
import com.saulhdev.feeder.ui.overlay.isNamedSort
import com.saulhdev.feeder.ui.overlay.mutedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL

private fun feed(id: Long, title: String) =
    Feed(id = id, title = title, url = URL("https://${id}.example/feed"))

/**
 * The bar that says what the feed is being narrowed by.
 *
 * The filter used to be a solid funnel in the header, which answers *whether*
 * and not *what*: a reader who muted a category, forgot, and came back a day
 * later saw a shorter feed and an icon, and had to open a sheet to find out
 * why. That is the moment somebody concludes the app has lost their articles.
 */
class ActiveFiltersTest {

    private val sources = listOf(feed(1, "BBC"), feed(2, "Reuters"), feed(3, "Hackaday"))

    @Test
    fun `a muted source is named rather than numbered`() {
        assertEquals(listOf(2L to "Reuters"), mutedSources(setOf("2"), sources))
    }

    @Test
    fun `nothing muted is no chips`() {
        assertTrue(mutedSources(emptySet(), sources).isEmpty())
    }

    /**
     * The ghost case, which the pinned sources had too: an id left behind by
     * a deleted source must not draw a blank chip with a cross on it.
     */
    @Test
    fun `an id that no longer names a source is dropped`() {
        assertEquals(listOf(1L to "BBC"), mutedSources(setOf("1", "99"), sources))
        assertTrue(mutedSources(setOf("99"), sources).isEmpty())
    }

    /** Rubbish in the set is not a crash and not a chip. */
    @Test
    fun `an unparseable id is ignored`() {
        assertTrue(mutedSources(setOf("", "not-a-number"), sources).isEmpty())
    }

    /** Chips follow the sources list, not a set's iteration order. */
    @Test
    fun `the order follows the source list`() {
        assertEquals(
            listOf(1L to "BBC", 3L to "Hackaday"),
            mutedSources(setOf("3", "1"), sources),
        )
    }

    @Test
    fun `only a chosen sort is worth naming`() {
        assertTrue(isNamedSort(SORT_TITLE))
        assertTrue(isNamedSort(SORT_SOURCE))
        assertFalse("the default ordering is not a filter", isNamedSort(SORT_CHRONOLOGICAL))
        assertFalse(isNamedSort(""))
    }
}
