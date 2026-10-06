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

import com.saulhdev.feeder.manager.discovery.LibraryNeighbours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suggesting the publication beside the one you already read.
 *
 * Reported from the device, and the reasoning was sound: link harvesting
 * answers "what do the things you read point at", which is a good question
 * about blogs and a useless one about news, because a BBC article links to
 * the BBC. The obvious suggestion — that ITV and Sky News exist and are not
 * being followed — was sitting in the bundled library the whole time.
 *
 * The packs here mirror the shape of the real ones: the BBC and the Guardian are in a
 * great many, because a large publication has a feed for everything, and the
 * whole difficulty is that this must not make them look like cricket readers.
 */
class LibraryNeighboursTest {

    private val library = mapOf(
        "bbci.co.uk" to setOf("united_kingdom", "news", "sports", "cricket", "tennis", "science"),
        "theguardian.com" to setOf("united_kingdom", "news", "sports", "cricket", "tennis", "food", "books"),
        "natpaper.example" to setOf("country_a"),
        "daily.example" to setOf("country_a"),
        "herald.example" to setOf("country_a"),
        "arstechnica.com" to setOf("tech"),
        "theverge.com" to setOf("tech"),
        "quantamagazine.org" to setOf("science"),
    )

    /**
     * The failure the weighting exists to prevent. Following the BBC and the
     * Guardian puts the reader in six packs at two sources each — counting
     * heads, cricket and tennis rank exactly as high as United Kingdom.
     */
    @Test
    fun `a large publication does not make somebody a cricket reader`() {
        val mine = setOf("bbci.co.uk", "theguardian.com", "natpaper.example", "daily.example")
        val packs = LibraryNeighbours.topPacks(mine, library).map { it.first }
        assertEquals("country_a", packs.first())
        assertTrue("cricket outranked a real interest", packs.indexOf("cricket") != 0)
    }

    /** A source in one pack says something definite; one in seven says little. */
    @Test
    fun `a focused source outvotes a promiscuous one`() {
        val mine = setOf("bbci.co.uk", "theguardian.com", "natpaper.example", "daily.example")
        val scores = LibraryNeighbours.packScores(mine, library)
        assertTrue(scores["country_a"]!! > scores["united_kingdom"]!!)
        assertTrue(scores["country_a"]!! > scores["cricket"]!!)
    }

    /** One shared source is a coincidence, not a pattern. */
    @Test
    fun `a pack needs more than one of the reader's sources`() {
        val packs = LibraryNeighbours.topPacks(setOf("quantamagazine.org"), library)
        assertTrue("one source should suggest nothing", packs.isEmpty())
    }

    @Test
    fun `two sources in a pack is enough`() {
        val packs = LibraryNeighbours.topPacks(setOf("arstechnica.com", "theverge.com"), library)
        assertEquals(listOf("tech" to 2), packs)
    }

    /**
     * The reported case: a reader of large news sites should be offered the
     * other large news sites, which is what the pack is.
     */
    @Test
    fun `reading two national broadcasters suggests their pack`() {
        val packs = LibraryNeighbours.topPacks(setOf("bbci.co.uk", "theguardian.com"), library)
            .map { it.first }
        assertTrue("united_kingdom or news should be offered",
            packs.contains("united_kingdom") || packs.contains("news"))
    }

    @Test
    fun `the shared count is the evidence, and it is honest`() {
        val mine = setOf("natpaper.example", "daily.example", "herald.example")
        assertEquals(3, LibraryNeighbours.sharedCounts(mine, library)["country_a"])
    }

    @Test
    fun `a source the library has never heard of contributes nothing`() {
        val packs = LibraryNeighbours.topPacks(setOf("some-blog.example", "another.example"), library)
        assertTrue(packs.isEmpty())
    }

    @Test
    fun `no subscriptions suggests nothing rather than everything`() {
        assertTrue(LibraryNeighbours.topPacks(emptySet(), library).isEmpty())
        assertTrue(LibraryNeighbours.packScores(emptySet(), library).isEmpty())
    }

    /** A handful of packs, so the screen stays a suggestion and not a catalogue. */
    @Test
    fun `only a few packs are drawn from`() {
        val everything = library.keys
        assertTrue(
            LibraryNeighbours.topPacks(everything, library).size <= LibraryNeighbours.MAX_PACKS
        )
    }

    /** Ordering must not depend on which order the sources happen to arrive in. */
    @Test
    fun `the answer does not depend on set iteration order`() {
        val a = setOf("natpaper.example", "daily.example", "bbci.co.uk", "theguardian.com")
        val b = setOf("theguardian.com", "bbci.co.uk", "daily.example", "natpaper.example")
        assertEquals(LibraryNeighbours.topPacks(a, library), LibraryNeighbours.topPacks(b, library))
    }
}
