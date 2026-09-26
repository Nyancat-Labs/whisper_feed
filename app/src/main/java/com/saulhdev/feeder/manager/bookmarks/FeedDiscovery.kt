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
package com.saulhdev.feeder.manager.bookmarks

import android.util.Log
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URL

/** What was found at a site, and how confidently. */
data class DiscoveredFeed(
    val site: String,
    val feedUrl: String,
    val title: String,
    /**
     * Whether the site said so.
     *
     * Two states, not three. Either the page declared this feed in its head or
     * Whisper guessed at a common path — and a middle band adds a word to the
     * screen without changing any decision the reader makes.
     */
    val declared: Boolean,
)

/**
 * Finding the feed behind a website.
 *
 * One entry point, used by the bookmark import and available to everything
 * else that needs it. The alternative — the add-feed screen keeping its own,
 * the importer growing another, broken-feed recovery a third — is four
 * half-implementations, which is how this goes wrong.
 *
 * **Grouped by domain and probed at the origin**, both of which matter at
 * scale. Fifteen BBC bookmarks are one site to look at, and asking about the
 * origin rather than each bookmarked path is the difference between one
 * request and fifteen; a scan of a thousand cannot afford what a single
 * add-feed can.
 */
class FeedDiscovery(private val parser: FeedParser) {

    /**
     * Looks at one site.
     *
     * Returns null rather than throwing: in a scan of forty, one site being
     * unreachable is not a reason to stop, and the reader is going to see a
     * list of what was found rather than a list of what was not.
     */
    suspend fun discover(site: String): DiscoveredFeed? = withContext(Dispatchers.IO) {
        val origin = "https://$site"
        if (!SafeAddress.schemeAllowed(origin)) return@withContext null
        // The reader's own network is not somewhere to go looking. A bookmark
        // pointing at a router would otherwise have the phone probing the
        // house from inside the firewall.
        if (!SafeAddress.isPublicHost(site)) {
            Log.i(TAG, "Refusing to probe a bookmark: not a public address")
            return@withContext null
        }

        val url = sloppyLinkToStrictURLNoThrows(origin)

        // What the site says about itself, first and usually last.
        runCatching { parser.getAlternateFeedLinksAtUrl(url) }
            .getOrDefault(emptyList())
            .firstOrNull()
            ?.let { (feedUrl, _) ->
                return@withContext DiscoveredFeed(
                    site = site,
                    feedUrl = feedUrl,
                    title = titleOf(feedUrl) ?: site,
                    declared = true,
                )
            }

        // Otherwise the handful of paths that are worth a guess. Deliberately
        // few: this runs once per site in a scan, and every miss is a request
        // that cost the reader's data allowance to return a 404.
        GUESSES.forEach { path ->
            val candidate = "$origin$path"
            val title = titleOf(candidate)
            if (title != null) {
                return@withContext DiscoveredFeed(
                    site = site,
                    feedUrl = candidate,
                    title = title,
                    declared = false,
                )
            }
        }
        null
    }

    /**
     * Looks at many sites, a few at a time.
     *
     * Bounded concurrency for the same reason the feed sync has it: forty
     * simultaneous connections is not four times faster than ten, it is a
     * radio held open and a heap full of half-parsed HTML.
     */
    suspend fun discoverAll(
        sites: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<DiscoveredFeed> = coroutineScope {
        val gate = Semaphore(MAX_CONCURRENT)
        var done = 0
        sites.map { site ->
            async {
                gate.withPermit {
                    val found = discover(site)
                    synchronized(sites) { onProgress(++done, sites.size) }
                    found
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun titleOf(feedUrl: String): String? =
        runCatching { parser.parseFeedUrl(URL(feedUrl))?.title }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "FeedDiscovery"
        const val MAX_CONCURRENT = 6

        /**
         * Where a feed lives when a site does not say.
         *
         * Short on purpose. The CMS-specific rules this could carry —
         * WordPress, Ghost, Substack, Blogger, Medium — all advertise their
         * feeds in the head, so autodiscovery above has already caught them
         * and a page of special cases would earn nothing.
         */
        val GUESSES = listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml")
    }
}
