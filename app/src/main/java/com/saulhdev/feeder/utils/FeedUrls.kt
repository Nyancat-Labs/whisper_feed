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
 * A comparison key for a feed address.
 *
 * Two subscriptions are the same subscription when they reach the same
 * document, and the string a user types is a poor guide to that:
 * `example.com/feed`, `https://www.example.com/feed/` and
 * `HTTP://Example.COM:80/feed` are one feed typed four ways. Adding the second
 * of them should say "already subscribed", not create a duplicate — and with
 * `Feeds.url` carrying a unique index and an insert strategy of REPLACE, the
 * duplicate would not even sit alongside the original: it would delete it, and
 * take that feed's articles with it.
 *
 * What is deliberately *kept*:
 *  - the query string, because `?feed=rss2` and `?feed=atom` are different
 *    feeds on the same WordPress site, and a token in a query is what makes a
 *    private feed reachable at all;
 *  - the path's case, since only the scheme and host are case-insensitive by
 *    specification, and plenty of servers do distinguish `/Feed` from `/feed`.
 *
 * What is dropped: the scheme (an http and an https subscription to one feed
 * are one subscription), a leading `www.`, the default port for the scheme, a
 * trailing slash, and the fragment, which never reaches the server.
 */
fun normalizeFeedUrl(url: String): String {
    val parsed = try {
        sloppyLinkToStrictURL(url.trim())
    } catch (_: Throwable) {
        return url.trim().lowercase()
    }
    return normalizeFeedUrl(parsed)
}

fun normalizeFeedUrl(url: URL): String {
    val host = url.host.lowercase().removePrefix("www.")
    val defaultPort = if (url.protocol.equals("https", true)) 443 else 80
    val port = if (url.port == -1 || url.port == defaultPort) "" else ":${url.port}"
    val path = url.path.trimEnd('/')
    val query = url.query?.let { "?$it" }.orEmpty()
    return "$host$port$path$query"
}

/**
 * The same address, over https.
 *
 * Every client that fetches a feed, an article page or an icon is built with
 * `onlyPublicHttps()`, which rewrites the scheme before a socket is opened. So
 * an address stored as http is *already* fetched over https: the scheme
 * written down is cosmetic, and when it says http it is simply wrong about
 * what the app does.
 *
 * Wrong in a way that is not harmless. It puts working subscriptions on the
 * "Feeds still on http" screen, which reads as an accusation against the
 * publisher; it makes an export carry addresses the exporting app would not
 * itself use; and it leaves anyone reading the list believing their feeds are
 * being fetched in the clear when none of them are.
 *
 * Only `http` is touched. A scheme this app does not fetch over is not this
 * function's business to rewrite, and turning something unparseable into
 * `https://` + rubbish would be worse than leaving it.
 */
fun URL.preferringHttps(): URL =
    if (protocol.equals("http", ignoreCase = true)) {
        runCatching { URL("https", host, port, file) }.getOrDefault(this)
    } else {
        this
    }

/** Whether two addresses name the same subscription. */
fun isSameFeedUrl(a: URL, b: URL): Boolean = normalizeFeedUrl(a) == normalizeFeedUrl(b)

/**
 * The conventional feed paths, in the order they are worth trying.
 *
 * This is the *fallback* half of autodiscovery and the ordering says why: a
 * site's own `<link rel="alternate">` is an answer, and this is a guess. Guess
 * first and a site that advertises its Atom feed can still be subscribed to by
 * whatever happens to sit at `/feed`, which on some hosts is a redirect to the
 * homepage that parses as nothing at all.
 *
 * Both the given path and the site root are tried, because a section of a site
 * usually has its own feed — `example.com/blog` wants `example.com/blog/feed`
 * before `example.com/feed` — and because a URL pasted from the address bar
 * often has a path that has nothing to do with feeds.
 */
fun candidateFeedUrls(url: URL): List<URL> {
    val paths = listOf(
        "feed",
        "rss",
        "feed.xml",
        "rss.xml",
        "atom.xml",
        "index.xml",
        "feeds/posts/default", // Blogger
        "?feed=rss2",          // WordPress without pretty permalinks
    )
    val origin = "${url.protocol}://${url.authority}"
    val base = url.path.trim('/')

    val bases = buildList {
        if (base.isNotEmpty()) add("$origin/$base")
        add(origin)
    }

    return bases
        .flatMap { prefix -> paths.map { path -> "$prefix/$path" } }
        .distinct()
        .mapNotNull { candidate ->
            try {
                URL(candidate)
            } catch (_: Throwable) {
                null
            }
        }
}
