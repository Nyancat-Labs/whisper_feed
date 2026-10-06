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
package com.saulhdev.feeder.utils

import java.net.URI

/**
 * How much of the smaller source has to arrive through the other as well.
 *
 * Four in five. Two subscriptions to one feed share every article, give or
 * take the few the older one has kept longer; two sections of one paper share
 * a story now and then. A Guardian world feed and its Europe feed have plenty
 * in common and are still two feeds, and this is the line between them.
 */
const val SAME_ARTICLES_SHARE = 0.8f

/** Below this many articles there is too little to compare. */
const val SAME_ARTICLES_MIN = 5

/**
 * An article's address, reduced to what says which article it is.
 *
 * The same story reaches two subscriptions under addresses that differ only
 * in the parts that do not matter: http or https, with or without `www.`, a
 * trailing slash, and the `utm_` tags a publisher adds to say which feed the
 * click came from. That last one is exactly what differs between two feeds of
 * the same site. Null for anything that is not a web address.
 */
fun articleKey(link: String?): String? {
    val uri = runCatching { URI(link?.trim().orEmpty()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
    val path = uri.rawPath.orEmpty().trimEnd('/')
    val query = uri.rawQuery
        ?.split('&')
        ?.filterNot { param -> TRACKING.any { param.lowercase().startsWith(it) } }
        ?.filter { it.isNotEmpty() }
        ?.sorted()
        ?.joinToString("&")
        ?.takeIf { it.isNotEmpty() }
    return if (query == null) "$host$path" else "$host$path?$query"
}

private val TRACKING = listOf("utm_", "fbclid=", "gclid=", "mc_cid=", "mc_eid=", "ref=", "cmp=", "src=rss")

/**
 * Sources that deliver the same articles, grouped.
 *
 * The addresses cannot say this. `pocket-lint.com/feed` and
 * `www.pocket-lint.com/rss.phtml` are one feed; so are The Verge's `index.xml`
 * and `full.xml`, and the Guardian's world news under `feeds.` and under
 * `www.`. No rule about URLs gets all of those without also calling two
 * sections of one site the same thing. What they deliver can: the articles are
 * already in the database, and one subscription twice over has the same
 * articles twice over.
 *
 * Two sources are joined when at least [minShare] of the smaller one's
 * articles also came through the other, and it has at least [minArticles] to
 * judge by. Joined sources are grouped, so a feed subscribed three times is
 * one group of three.
 *
 * @param links every stored article as (source id, link).
 */
fun sameArticleGroups(
    links: List<Pair<Long, String?>>,
    minShare: Float = SAME_ARTICLES_SHARE,
    minArticles: Int = SAME_ARTICLES_MIN,
): List<Set<Long>> = joinPairs(articleOverlap(links, minArticles).filterValues { it >= minShare }.keys)

/**
 * For every two sources that delivered any of the same articles: the share of
 * the smaller one's articles the other delivered too, from 0 to 1. Only where
 * the smaller has at least [minArticles] to judge by. Pairs come smaller id
 * first.
 *
 * What [sameArticleGroups] decides by, kept as numbers, so the report can say
 * how alike two sources are rather than only that they are.
 *
 * @param links every stored article as (source id, link).
 */
fun articleOverlap(
    links: List<Pair<Long, String?>>,
    minArticles: Int = SAME_ARTICLES_MIN,
): Map<Pair<Long, Long>, Float> {
    val bySource = HashMap<Long, HashSet<String>>()
    links.forEach { (source, link) ->
        val key = articleKey(link) ?: return@forEach
        bySource.getOrPut(source) { HashSet() } += key
    }
    // Who carried each article, then how many articles each pair shares.
    val carriers = HashMap<String, MutableList<Long>>()
    bySource.forEach { (source, keys) -> keys.forEach { carriers.getOrPut(it) { mutableListOf() } += source } }
    val shared = HashMap<Pair<Long, Long>, Int>()
    carriers.values.forEach { sources ->
        if (sources.size < 2) return@forEach
        val sorted = sources.sorted()
        for (a in sorted.indices) for (b in a + 1 until sorted.size) {
            val pair = sorted[a] to sorted[b]
            shared[pair] = (shared[pair] ?: 0) + 1
        }
    }
    return shared.mapNotNull { (pair, count) ->
        val smaller = minOf(bySource.getValue(pair.first).size, bySource.getValue(pair.second).size)
        if (smaller >= minArticles) pair to count.toFloat() / smaller else null
    }.toMap()
}

/**
 * Why a group of sources counts as duplicates, as the report says it: one
 * subscription twice over ("same address"), or two feeds that deliver most of
 * the same articles - which can be two sections of one publication, each
 * worth keeping, and is for the reader to judge.
 *
 * [articlesShared] is the largest share between two of the group, or null
 * when only their addresses joined them.
 */
fun duplicateReason(sameAddress: Boolean, articlesShared: Float?): String = listOfNotNull(
    if (sameAddress) "same address" else null,
    articlesShared?.let { "mostly the same articles, ${(it * 100).toInt()}%" },
).joinToString("; ").ifEmpty { "same address" }

/** Pairs into groups: A with B and B with C is one group of three. */
fun joinPairs(pairs: Collection<Pair<Long, Long>>): List<Set<Long>> {
    val parent = HashMap<Long, Long>()
    fun find(x: Long): Long {
        var root = x
        while (parent[root] != null && parent[root] != root) root = parent.getValue(root)
        parent[x] = root
        return root
    }
    pairs.forEach { (a, b) ->
        parent.putIfAbsent(a, a)
        parent.putIfAbsent(b, b)
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[rb] = ra
    }
    return parent.keys.groupBy(::find).values.map { it.toSet() }.filter { it.size > 1 }
}
