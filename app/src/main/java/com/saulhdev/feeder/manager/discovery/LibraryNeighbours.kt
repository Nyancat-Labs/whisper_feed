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
package com.saulhdev.feeder.manager.discovery

/**
 * Publications that sit beside the ones somebody already follows.
 *
 * [LinkHarvest] answers "what do the things you read point at", which is a
 * good question about blogs and a useless one about news: a BBC article links
 * to the BBC. Somebody who reads the BBC, the Guardian and RTÉ generates
 * almost no outbound links, and got an empty screen — while the obvious
 * suggestion, that ITV or Sky News exist and they are not following them, was
 * sitting in the app the whole time.
 *
 * The bundled library is a set of curated peer groups. Being in "United
 * Kingdom" beside the BBC is exactly the statement "similar publication", made
 * by whoever assembled the pack rather than inferred from behaviour. So the
 * mechanism is: find the packs the reader's own sources cluster in, and offer
 * what else is in them.
 *
 * **Still nothing leaves the device and still no reader is compared to
 * another.** The library ships in the APK. This is the app reading a file it
 * already has against a list the reader already made, which is the same
 * standard everything else here is held to.
 */
object LibraryNeighbours {

    /**
     * How many of the reader's sources must share a pack before it counts.
     *
     * Two. One is a coincidence — everybody's list touches some pack
     * somewhere — and requiring three would rule out the case this exists
     * for, which is somebody with a short, focused subscription list.
     */
    const val MIN_SHARED = 2

    /** How many packs to draw from, so the screen is a suggestion and not a catalogue. */
    const val MAX_PACKS = 3

    /**
     * How much each pack looks like the reader's own list.
     *
     * A plain count of shared sources does not work, and the reason is worth
     * stating because it is the whole design. The BBC is in eight of these
     * packs and the Guardian is in fourteen — cricket, tennis, football, food,
     * travel — because a large publication has a feed for everything. Counting
     * heads, somebody who follows those two "belongs to" the cricket pack
     * exactly as much as the United Kingdom one, and would be offered cricket
     * feeds for reading the news.
     *
     * So a source's vote is divided by the number of packs it appears in. A
     * source that sits in one pack says something definite about that pack; a
     * source that sits in fourteen says almost nothing about any of them. A
     * national paper in one country pack votes 1.0 for it; the Guardian votes
     * 0.07 for each of its fourteen.
     *
     * Returned unsorted; [topPacks] is what orders them.
     */
    fun packScores(
        subscribed: Set<String>,
        domainToPacks: Map<String, Set<String>>,
    ): Map<String, Float> {
        val scores = mutableMapOf<String, Float>()
        subscribed.forEach { domain ->
            val packs = domainToPacks[domain] ?: return@forEach
            if (packs.isEmpty()) return@forEach
            val vote = 1f / packs.size
            packs.forEach { pack -> scores[pack] = (scores[pack] ?: 0f) + vote }
        }
        return scores
    }

    /** How many of the reader's sources are in each pack, for the explanation. */
    fun sharedCounts(
        subscribed: Set<String>,
        domainToPacks: Map<String, Set<String>>,
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        subscribed.forEach { domain ->
            domainToPacks[domain]?.forEach { pack -> counts[pack] = (counts[pack] ?: 0) + 1 }
        }
        return counts
    }

    /**
     * The packs worth drawing suggestions from, best first.
     *
     * Both tests have to pass: enough of the reader's sources in the pack to
     * be a pattern rather than an accident, and a weighted score that survives
     * the large-publication problem above. The count is the structural floor;
     * the score is the ordering.
     */
    fun topPacks(
        subscribed: Set<String>,
        domainToPacks: Map<String, Set<String>>,
        limit: Int = MAX_PACKS,
    ): List<Pair<String, Int>> {
        val counts = sharedCounts(subscribed, domainToPacks)
        val scores = packScores(subscribed, domainToPacks)
        return counts.asSequence()
            .filter { (_, n) -> n >= MIN_SHARED }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { scores[it.key] ?: 0f }
                    .thenBy { it.key }
            )
            .take(limit)
            .map { it.key to it.value }
            .toList()
    }
}
