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
package com.saulhdev.feeder.ui.overlay

import com.saulhdev.feeder.data.db.models.FeedItem

/**
 * A story several sources are covering at once.
 *
 * @param sources how many *distinct* sources are in it. The count that
 *   matters: one prolific feed posting six times about its own topic is not a
 *   story, and counting articles rather than sources would say it was.
 * @param leadId the article that represents the cluster. Only it is promoted;
 *   the others keep whatever size they had earned on their own, because
 *   promoting all six would fill the screen with the same headline.
 */
data class StoryCluster(
    val sources: Int,
    val leadId: String,
)

/**
 * Finding the story behind a burst of headlines.
 *
 * The signal is right — when several sources publish about the same thing
 * inside a few hours, that is a story, and a single-source post is not. The
 * hard part is "the same thing", and this takes the cheapest option that
 * works: comparing the words in the titles. No model, no network, no account,
 * a few milliseconds over a few hundred articles.
 *
 * Two refinements make plain word overlap usable. Common words are ignored, or
 * every headline with "after" in it would be one story. And words are weighted
 * by how *rare* they are in the current feed: a token appearing in two or
 * three titles out of four hundred is a proper noun doing the work of
 * identifying an event, while one appearing in fifty is a topic. Two articles
 * sharing two rare tokens are almost always the same story; two sharing four
 * common ones almost never are.
 *
 * What this deliberately does not do is hide anything. Every member of a
 * cluster stays in the feed at its own size; only the lead is promoted.
 * Collapsing five reports into one would mean choosing which four the reader
 * does not see, and no automatic rule here is good enough to be trusted with
 * that.
 */
object Clustering {

    /** How far apart two reports can be and still be one story. */
    const val WINDOW_MS = 12L * 60 * 60 * 1000

    /** Distinct sources needed before a burst counts as a story at all. */
    const val MIN_SOURCES = 3

    /**
     * The most titles a word can appear in and still count as distinctive.
     *
     * Above this it is describing a topic rather than an event — "review",
     * "update", "market" — and matching on it would merge every article about
     * that topic into one enormous cluster.
     */
    const val MAX_DOC_FREQUENCY = 8

    /** How many distinctive words two titles must share to be the same story. */
    const val MIN_SHARED = 2

    /** The shortest word worth comparing. */
    private const val MIN_TOKEN_LENGTH = 4

    /**
     * Words carried by headlines regardless of what they are about.
     *
     * Short ones are already excluded by length; these are the longer ones
     * that are just as empty. Kept deliberately small — an aggressive list
     * starts removing the words that identify the story.
     */
    private val STOPWORDS = setOf(
        "after", "about", "with", "from", "that", "this", "have", "has", "been",
        "will", "would", "could", "should", "says", "said", "more", "than",
        "over", "into", "amid", "what", "when", "where", "which", "while",
        "their", "there", "they", "them", "here", "your", "you", "just",
        "first", "last", "next", "best", "worst", "new", "news", "report",
        "reports", "update", "updates", "review", "reviews", "how", "why",
    )

    /**
     * The meaningful words in a headline.
     *
     * The source suffix goes first — plenty of feeds append " - The Guardian"
     * or " | Reuters" to every title, and left in it would match every article
     * from that source to every other.
     */
    fun tokens(title: String): Set<String> {
        val withoutSuffix = title
            .substringBefore(" - ")
            .substringBefore(" | ")
            .substringBefore(" — ")
            .substringBefore(" · ")
        return withoutSuffix
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOPWORDS }
            .toSet()
    }
}

/**
 * Groups the articles that are covering the same story.
 *
 * @param newsOnly restrict to sources the reader has filed under a news
 *   category. The same burst across five review sites is a product launch and
 *   across five football sites is a transfer window; neither wants the
 *   treatment breaking news gets. The reader's own categories are used because
 *   they are a judgement already made, rather than one guessed at here.
 * @return the cluster for every article that is in one, by article id. An
 *   article in no cluster is absent rather than present with a count of one.
 */
fun clusterStories(
    items: List<FeedItem>,
    nowMs: Long,
    newsOnly: Boolean = true,
): Map<String, StoryCluster> {
    val candidates = items.withIndex().filter { (_, item) ->
        (!newsOnly || item.feedTags.any { it.equals("news", ignoreCase = true) }) &&
                nowMs - item.timeMillis <= Clustering.WINDOW_MS
    }
    if (candidates.size < Clustering.MIN_SOURCES) return emptyMap()

    val tokens = candidates.map { (_, item) -> Clustering.tokens(item.contentTitle) }

    // How many titles each word appears in, so the rare ones can be told from
    // the ones every headline carries.
    val docFrequency = HashMap<String, Int>()
    tokens.forEach { set -> set.forEach { docFrequency[it] = (docFrequency[it] ?: 0) + 1 } }

    // An inverted index over the distinctive words only. Comparing every pair
    // of titles would be quadratic in the size of the feed; this touches only
    // the pairs that share a rare word, which is a handful.
    val postings = HashMap<String, MutableList<Int>>()
    tokens.forEachIndexed { i, set ->
        set.forEach { token ->
            val df = docFrequency[token] ?: 0
            if (df in 2..Clustering.MAX_DOC_FREQUENCY) {
                postings.getOrPut(token) { mutableListOf() }.add(i)
            }
        }
    }

    val union = UnionFind(candidates.size)
    val shared = HashMap<Long, Int>()
    postings.values.forEach { list ->
        for (a in list.indices) for (b in a + 1 until list.size) {
            val i = list[a]
            val j = list[b]
            val key = i.toLong() * candidates.size + j
            val count = (shared[key] ?: 0) + 1
            shared[key] = count
            if (count >= Clustering.MIN_SHARED) union.join(i, j)
        }
    }

    // Group, then keep only the groups that several *sources* contributed to.
    val groups = candidates.indices.groupBy { union.find(it) }
    val result = HashMap<String, StoryCluster>()
    groups.values.forEach { members ->
        val sources = members.map { candidates[it].value.sourceId }.distinct()
        if (sources.size < Clustering.MIN_SOURCES) return@forEach
        // The newest report leads. Not the longest or the most illustrated:
        // for a developing story the latest one is the one worth reading.
        val lead = members.maxByOrNull { candidates[it].value.timeMillis } ?: return@forEach
        // A dismissed lead takes its whole story with it. Everything the
        // promotion does — the hold at the top, the weight bonus, the "Covered
        // by N sources" line — is read from the map this builds, so leaving
        // the group out is the entire retraction, in one place, rather than
        // three separate exemptions that could drift apart.
        //
        // The lead is what the reader dismissed, because the lead is the card
        // they were looking at when they did it.
        if (candidates[lead].value.article.dismissedAt != 0L) return@forEach
        val cluster = StoryCluster(
            sources = sources.size,
            leadId = candidates[lead].value.id,
        )
        members.forEach { result[candidates[it].value.id] = cluster }
    }
    return result
}

/**
 * Disjoint sets, for merging headlines into stories.
 *
 * Written out rather than pulled in: it is fifteen lines, and the alternative
 * is repeatedly walking a list of groups looking for the one holding an index,
 * which is quadratic in exactly the case this exists to avoid.
 */
private class UnionFind(size: Int) {
    private val parent = IntArray(size) { it }

    fun find(x: Int): Int {
        var root = x
        while (parent[root] != root) root = parent[root]
        var walk = x
        while (parent[walk] != root) {
            val next = parent[walk]
            parent[walk] = root
            walk = next
        }
        return root
    }

    fun join(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[rb] = ra
    }
}
