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

import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.StarterSources
import com.saulhdev.feeder.ui.overlay.Clustering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The nine feeds a brand-new reader is offered before they have chosen
 * anything.
 *
 * These had no test at all, on the strength of a comment saying every address
 * was checked before it shipped — which was true when it was written and is
 * not a thing a comment can keep being true. They matter more per feed than
 * anything in the bundled library: a dead entry there is one row among 847,
 * and a dead entry here is part of the first screen somebody ever sees.
 *
 * Liveness still cannot be asserted from a unit test, and should not be — a
 * suite that fails because a newspaper is having an outage is a suite people
 * learn to ignore. What is checked here is everything that can go wrong
 * without anybody noticing: the structure, and the couplings that live in
 * prose elsewhere.
 */
class StarterSourcesTest {

    private val all = StarterSources.ALL

    @Test
    fun `there is a starter list at all`() {
        assertTrue("the first-run screen would be empty", all.size >= 5)
    }

    /**
     * Cleartext is refused at the platform level, so an http address here is
     * not a feed that travels in the clear — it is a subscription that fails
     * on the first sync, handed to somebody on their first run.
     */
    @Test
    fun `every starter address is https`() {
        all.forEach { assertTrue("${it.title} is not https: ${it.url}", it.url.startsWith("https://")) }
    }

    @Test
    fun `no address appears twice`() {
        val urls = all.map { it.url.trimEnd('/').lowercase() }
        assertEquals("a starter feed is listed twice", urls.size, urls.toSet().size)
    }

    @Test
    fun `no publication appears twice`() {
        val titles = all.map { it.title.lowercase() }
        assertEquals("a starter publication is listed twice", titles.size, titles.toSet().size)
    }

    /**
     * The coupling this exists for.
     *
     * Breaking-news clustering only groups a story once [Clustering.MIN_SOURCES]
     * separate sources carry it, so a starter list shorter than that leaves
     * the feature switched on and never firing — for exactly the readers who
     * have subscribed to nothing else. The four world feeds are there for that
     * reason, which until now was a comment that any future tidy-up of the
     * list could have quietly removed.
     */
    @Test
    fun `enough world sources for breaking news to ever fire`() {
        val world = all.count { it.categoryId == R.string.starter_category_world }
        assertTrue(
            "$world world sources cannot cluster; clustering needs ${Clustering.MIN_SOURCES}",
            world >= Clustering.MIN_SOURCES,
        )
    }

    /**
     * A regional feed starts unticked, and the rest start ticked.
     *
     * A reader in Berlin should not be handed an Australian broadcaster by
     * default; it stays on the list, one tap away, because hiding it would be
     * worse than offering it.
     */
    @Test
    fun `the default selection is everything without a region`() {
        val expected = all.filter { it.region == null }.map { it.url }.toSet()
        assertEquals(expected, StarterSources.defaultSelection().toSet())
        assertTrue("nothing would be ticked on first run", expected.isNotEmpty())
    }

    @Test
    fun `every starter feed is named and filed`() {
        all.forEach {
            assertTrue("a starter feed has no title", it.title.isNotBlank())
            assertTrue("${it.title} has no category", it.categoryId != 0)
        }
    }
}
