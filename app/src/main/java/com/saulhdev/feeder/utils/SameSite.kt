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

import java.net.URL

/**
 * Multi-part public suffixes common enough to matter here.
 *
 * Taking the last two labels of a host is right for `example.com` and wrong
 * for `theregister.co.uk`, which would reduce to `co.uk` and then group every
 * British site in the list together. The real answer is the Public Suffix
 * List, which is thousands of entries and a dependency; this is the short tail
 * that actually turns up in feed addresses.
 *
 * Being incomplete makes this *under*-group — an unlisted suffix leaves two
 * feeds in separate groups rather than merging unrelated ones. That is the
 * safe direction for a screen whose whole job is to suggest candidates.
 */
private val MULTI_PART_SUFFIXES = setOf(
    "co.uk", "org.uk", "me.uk", "ac.uk", "gov.uk", "net.uk", "sch.uk",
    "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
    "com.au", "net.au", "org.au", "edu.au", "gov.au",
    "co.nz", "net.nz", "org.nz", "govt.nz",
    "com.br", "com.cn", "com.mx", "com.tr", "com.sg", "com.hk", "com.tw",
    "co.za", "co.in", "co.kr", "co.il", "com.ar", "com.pl", "com.ua",
)

/**
 * The registrable part of a host: `bbc.co.uk` from `feeds.bbci.co.uk`'s
 * neighbours, `guardian.co.uk` from `feeds.guardian.co.uk`.
 *
 * Case-folded, and `www.` is not special-cased because dropping labels from
 * the left already handles it.
 */
fun registrableDomain(host: String): String {
    val labels = host.lowercase().trim('.').split('.')
    if (labels.size <= 2) return labels.joinToString(".")
    val lastTwo = labels.takeLast(2).joinToString(".")
    val take = if (lastTwo in MULTI_PART_SUFFIXES) 3 else 2
    return labels.takeLast(take).joinToString(".")
}

/**
 * Domains that host other people's feeds.
 *
 * FeedBurner is the reason this exists. It is a feed *service*, not a
 * publication: sixteen of the feeds in one ordinary subscription list sit
 * behind `feeds.feedburner.com`, and they are TorrentFreak, PetaPixel,
 * Hackaday and thirteen other unrelated things. Grouping by registrable
 * domain would offer all sixteen as one another's duplicates, which is not a
 * flawed suggestion so much as a flood that buries the real ones.
 *
 * YouTube is the same shape — three channel feeds share `youtube.com` and
 * have nothing else in common — and so are the rest of these.
 *
 * A domain here contributes no grouping key at all, rather than a cleverer
 * one. FeedBurner's identity lives in the path and YouTube's in a query
 * parameter, so there is no single rule that extracts it; and leaving these
 * feeds to be joined by title alone under-groups, which is the direction this
 * screen is already built to fail in.
 */
private val FEED_HOSTS = setOf(
    "feedburner.com", "feedburner.google.com", "feedproxy.google.com",
    "youtube.com", "youtu.be",
    "feedpress.me", "feedpress.com", "rss.app", "fetchrss.com",
    "rsshub.app", "politepol.com", "feed43.com", "feedity.com",
    "medium.com", "substack.com", "blogspot.com", "tumblr.com",
    "wordpress.com", "livejournal.com", "typepad.com",
    "reddit.com", "stackexchange.com", "github.com",
)

/**
 * Whether a host merely carries somebody else's feed.
 *
 * Shared so that everything asking "which site is this feed from" agrees, and
 * so that adding a service to the list fixes every one of them at once.
 */
fun isFeedHost(host: String): Boolean = registrableDomain(host) in FEED_HOSTS

/**
 * Words too generic to mean two feeds are related.
 *
 * A title segment like "News" or "Blog" appears across unrelated
 * publications, and joining on one would put half the list in a single group.
 */
private val GENERIC_TITLE_WORDS = setOf(
    "news", "blog", "home", "feed", "feeds", "rss", "atom", "articles",
    "latest", "topstories", "frontpage", "headlines", "world", "worldnews",
    "posts", "updates", "stories", "thelatest", "allcontent", "fullarticles",
)

/**
 * The parts of a title that could identify a publication.
 *
 * Feeds name themselves inconsistently, and the name is not reliably first:
 * "Hack a Day — Fresh hacks every day" leads with it, "Blog – Hackaday" ends
 * with it. Taking the first segment gets one of those right and the other
 * badly wrong, so every segment is offered and two feeds are related if any
 * segment matches.
 *
 * Segments shorter than five characters and the generic words above are
 * dropped, because those are what would join unrelated publications. An empty
 * result means no opinion, not a match.
 */
fun titleKeys(title: String): Set<String> =
    title.split('|', '\u2014', '\u2013', '-', ':', '\u00b7')
        .map { part -> part.lowercase().filter(Char::isLetterOrDigit) }
        .filter { it.length >= 5 && it !in GENERIC_TITLE_WORDS }
        .toSet()

/**
 * Subscriptions that appear to come from the same publication.
 *
 * Two feeds are related when they share a registrable domain **or** a title
 * key, and relatedness is transitive: a FeedBurner address and a site's own
 * address have nothing in common in their hosts, and are joined by their
 * titles instead. That is the case that matters most, because a FeedBurner
 * alias is the commonest way to end up subscribed to one publication twice.
 *
 * **These are candidates, not duplicates.** The Guardian's World, Europe and
 * Culture feeds share a domain and are three different things; so do NPR's
 * topic feeds. Anything built on this has to show the groups and let the
 * reader decide, never act on them — which is exactly why this is separate
 * from [normalizeFeedUrl], where a match means the same address and can be
 * trusted.
 *
 * Groups of one are dropped, and each group is returned in the order the
 * feeds were given.
 */
fun <T> sameSiteGroups(items: List<T>, url: (T) -> URL, title: (T) -> String): List<List<T>> {
    if (items.size < 2) return emptyList()

    // Union-find over the two keys. A plain groupBy cannot express "related to
    // anything related to this", which is what joins a FeedBurner address to a
    // site's own through a shared title.
    val parent = IntArray(items.size) { it }
    fun find(a: Int): Int {
        var x = a
        while (parent[x] != x) {
            parent[x] = parent[parent[x]]
            x = parent[x]
        }
        return x
    }
    fun union(a: Int, b: Int) {
        val ra = find(a)
        val rb = find(b)
        if (ra != rb) parent[rb] = ra
    }

    val firstSeen = HashMap<String, Int>()
    items.forEachIndexed { i, item ->
        // Nothing from a feed service: see FEED_HOSTS. Sixteen publications
        // behind one FeedBurner domain are sixteen publications.
        val domain = runCatching { url(item).host }
            .getOrNull()
            ?.takeUnless(::isFeedHost)
            ?.let(::registrableDomain)
            .orEmpty()
        // Prefixed so a domain can never collide with a title that happens
        // to read the same once punctuation is stripped.
        val keys = buildList {
            if (domain.isNotEmpty()) add("d:$domain")
            titleKeys(title(item)).forEach { add("t:$it") }
        }
        keys.forEach { key ->
            val prior = firstSeen.putIfAbsent(key, i)
            if (prior != null) union(prior, i)
        }
    }

    val groups = LinkedHashMap<Int, MutableList<T>>()
    items.forEachIndexed { i, item -> groups.getOrPut(find(i)) { mutableListOf() }.add(item) }
    return groups.values.filter { it.size > 1 }
}
