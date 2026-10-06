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

import org.jsoup.Jsoup
import com.saulhdev.feeder.utils.registrableDomain
import java.net.URL

/**
 * Which sites the articles somebody actually read keep pointing at.
 *
 * This is the whole of the app's discovery mechanism, and it is deliberately
 * small. There is no profile, no model and nothing sent anywhere: it counts
 * links in articles already on the phone, in articles the reader has already
 * read, and says which domain keeps coming up. The evidence is a number the
 * reader can be shown — "six articles you read linked to this" — which is both
 * the reason for the suggestion and the whole of it.
 *
 * What it will never do is match one reader's subscriptions against another's.
 * That is the mechanism that makes Discover work and it is the one thing this
 * reader must not do.
 */
object LinkHarvest {

    /**
     * How many separate read articles have to link to a domain before it is
     * worth mentioning.
     *
     * One article linking somewhere is a citation, not a pattern. Three
     * separate articles is a habit worth a suggestion, and it is high enough
     * that a single link-heavy roundup cannot produce one on its own.
     */
    const val MENTIONS_NEEDED = 3

    /**
     * Domains that are never a suggestion however often they appear.
     *
     * Share buttons, image hosts and the platforms every article links to for
     * reasons that have nothing to do with what the reader wants to follow.
     * Counting these would produce the same four suggestions for everybody,
     * which is the opposite of the point.
     *
     * Matched on the registrable domain rather than the exact host, so
     * `www.facebook.com`, `m.facebook.com` and `web.facebook.com` are all
     * caught by the one entry.
     */
    private val NEVER = setOf(
        "facebook.com", "twitter.com", "x.com", "instagram.com", "threads.net",
        "linkedin.com", "reddit.com", "pinterest.com", "tiktok.com",
        "whatsapp.com", "telegram.me", "t.me", "mastodon.social",
        "google.com", "goo.gl", "bit.ly", "t.co", "ow.ly", "buff.ly",
        "amazon.com", "amzn.to", "apple.com", "play.google.com",
        "gravatar.com", "gstatic.com", "googleapis.com", "cloudfront.net",
        "wp.com", "wordpress.org", "creativecommons.org", "archive.org",
        "doubleclick.net", "outbrain.com", "taboola.com",
    )

    /**
     * The domains linked to from one article's HTML.
     *
     * A set rather than a list: an article that links to the same site nine
     * times is one article that found it worth linking to, and counting it
     * nine times would let a single page manufacture a suggestion.
     */
    fun domainsIn(html: String, ownHost: String? = null): Set<String> {
        val own = ownHost?.let(::registrableDomain)
        return runCatching {
            Jsoup.parse(html).select("a[href]")
                .asSequence()
                .mapNotNull { hostOf(it.attr("href")) }
                .map(::registrableDomain)
                .filter { it.isNotBlank() && it != own && it !in NEVER }
                .toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Counts one article's worth of domains into a running tally.
     *
     * Kept separate from [domainsIn] so the caller can stream articles past it
     * without holding every article's HTML at once — there can be thousands,
     * and they are gzipped files on disk rather than rows in memory.
     */
    fun tally(into: MutableMap<String, Int>, domains: Set<String>) {
        domains.forEach { into[it] = (into[it] ?: 0) + 1 }
    }

    /** The domains worth suggesting, most-linked first. */
    fun candidates(
        counts: Map<String, Int>,
        alreadySubscribed: Set<String>,
        minimum: Int = MENTIONS_NEEDED,
    ): List<Pair<String, Int>> =
        counts.asSequence()
            .filter { (host, n) -> n >= minimum && registrableDomain(host) !in alreadySubscribed }
            .sortedByDescending { it.value }
            .map { it.key to it.value }
            .toList()

    private fun hostOf(href: String): String? =
        runCatching { URL(href).host }.getOrNull()?.takeIf { it.isNotBlank() }
}
