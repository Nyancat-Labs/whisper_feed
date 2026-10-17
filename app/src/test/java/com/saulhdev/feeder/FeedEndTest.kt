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

import com.saulhdev.feeder.ui.overlay.FEED_END_KEY
import com.saulhdev.feeder.ui.overlay.feedLookup
import com.saulhdev.feeder.ui.overlay.passedIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hide read articles, with mark read on scroll on: "the last two remain
 * because it's no longer possible to scroll further" (17 October 2026).
 */
class FeedEndTest {

    private val position = mapOf("a" to 0, "b" to 1, "c" to 2)

    @Test
    fun `without anything below them, the last articles can never be passed`() {
        // Scrolled as far as it goes, "b" and "c" are still on screen, so the
        // topmost thing showing is "b": only "a" counts as passed.
        assertEquals(listOf("a"), passedIds(setOf("a", "b", "c"), position, topmostVisible = 1))
    }

    @Test
    fun `with the space after them on screen alone, every one of them is`() {
        // What the tracker works out when the space is all that is showing.
        val lookup = feedLookup(emptyList())
        val topmost = listOf(FEED_END_KEY).mapNotNull { lookup.position[it] }.minOrNull()
        assertEquals(0, topmost)
        // And with articles: the space sits just below the last one, so it
        // being the topmost thing showing passes all of them.
        val end = position.size
        assertEquals(
            setOf("a", "b", "c"),
            passedIds(setOf("a", "b", "c"), position + (FEED_END_KEY to end), topmostVisible = end).toSet(),
        )
    }

    @Test
    fun `every feed ends with the space`() {
        fun count(path: String, call: String) = File(path).readText().split(call).size - 1
        val app = "src/main/java/com/saulhdev/feeder/ui/pages/ArticleListPage.kt"
        val panel = "src/main/java/com/saulhdev/feeder/ui/overlay/FeedScaffold.kt"
        assertEquals(1, count(app, "feedEnd(listEnd)"))
        assertEquals(1, count(app, "feedEnd(gridEnd)"))
        assertEquals(1, count(panel, "feedEnd(listEnd)"))
        assertEquals(1, count(panel, "feedEnd(gridEnd)"))
        val items = File("src/main/java/com/saulhdev/feeder/ui/overlay/FeedItems.kt").readText()
        assertTrue("only while mark read on scroll is on", items.contains("if (setting < 0.25f) return null"))
    }
}
