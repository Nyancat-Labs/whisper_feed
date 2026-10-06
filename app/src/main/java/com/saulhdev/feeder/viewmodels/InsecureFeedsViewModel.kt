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
package com.saulhdev.feeder.viewmodels

import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.net.URL

/** How many addresses are checked at once. Enough to be quick, few enough to be polite. */
private const val PARALLEL_CHECKS = 6

/** What asking the https address turned up. */
sealed interface Upgrade {
    /** Absence of a value means not looked at yet; there is no state for that. */
    data object Checking : Upgrade

    /** The https address returns a real feed. This is the one worth applying. */
    data class Works(val url: String) : Upgrade

    /**
     * An https version of this same feed is already subscribed to.
     *
     * Rewriting would collide with the unique index on the address, so this
     * is reported rather than applied — and the right fix is to drop one of
     * the two, which is a decision about which history to keep.
     */
    data object Duplicate : Upgrade

    /** The site has no working https. Left alone, and honestly so. */
    data object NoHttps : Upgrade

    data object Applied : Upgrade
}

/**
 * Moves subscriptions still stored with an `http://` address over to https.
 *
 * The app already upgrades the *request*: an interceptor rewrites the scheme
 * before the connection opens, so nothing has been fetched in the clear for
 * some time. What it does not do is fix the address that is stored, and that
 * matters in three places — the sources list shows `http://` and looks wrong,
 * an OPML export hands the old address to whatever reader imports it next,
 * and the upgrade only holds for requests that go through the clients carrying
 * that interceptor. Fixing the stored address fixes all three at once and
 * makes the interceptor a safety net rather than the mechanism.
 *
 * **Every address is checked before it is changed.** A blind rewrite would
 * cost nothing today — an http-only feed already fails, because Android is
 * told to refuse cleartext — but it would write an address that does not work
 * into the database and then into every export. An address that is wrong and
 * confident is worse than one that is right about being broken.
 */
class InsecureFeedsViewModel(
    private val sources: SourcesRepository,
    private val parser: FeedParser,
) : NeoViewModel() {

    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    /**
     * Subscriptions whose stored address is still `http://`.
     *
     * Straight from the sources flow, so applying an upgrade removes that row
     * without anything having to tell this list it changed.
     */
    val insecure: StateFlow<List<Feed>> = sources.getAllSourcesFlow()
        .map { feeds -> feeds.filter { it.url.protocol.equals("http", ignoreCase = true) } }
        .stateIn(ioScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow<Map<Long, Upgrade>>(emptyMap())
    val state: StateFlow<Map<Long, Upgrade>> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Asks every listed site whether it answers over https. */
    fun checkAll() {
        if (_busy.value) return
        ioScope.launch {
            _busy.value = true
            try {
                val gate = Semaphore(PARALLEL_CHECKS)
                coroutineScope {
                    insecure.value.map { feed ->
                        async { gate.withPermit { check(feed) } }
                    }.awaitAll()
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** Applies every upgrade that came back as working. */
    fun applyAll() {
        if (_busy.value) return
        ioScope.launch {
            _busy.value = true
            try {
                insecure.value.forEach { feed ->
                    val result = _state.value[feed.id]
                    if (result is Upgrade.Works) apply(feed, result.url)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun check(feedId: Long) {
        val feed = insecure.value.firstOrNull { it.id == feedId } ?: return
        ioScope.launch { check(feed) }
    }

    fun applyOne(feedId: Long) {
        val feed = insecure.value.firstOrNull { it.id == feedId } ?: return
        val result = _state.value[feedId]
        if (result !is Upgrade.Works) return
        ioScope.launch { apply(feed, result.url) }
    }

    private suspend fun check(feed: Feed) {
        val https = httpsOf(feed.url) ?: return
        set(feed.id, Upgrade.Checking)

        // Before the network, because a collision is not something a
        // successful fetch would tell us about and the answer is the same
        // either way: this one cannot simply be rewritten.
        //
        // Excluding this feed. findSourceByUrl matches on the normalised
        // address, which ignores the scheme — so asked whether the https form
        // is taken, it returned the http feed doing the asking. Every single
        // address came back Duplicate and the screen could not upgrade
        // anything at all.
        if (sources.findOtherSourceByUrl(https, feed.id) != null) {
            set(feed.id, Upgrade.Duplicate)
            return
        }

        val feedAtHttps = runCatching { parser.parseFeedUrl(https) }.getOrNull()
        set(
            feed.id,
            if (feedAtHttps != null) Upgrade.Works(https.toString()) else Upgrade.NoHttps,
        )
    }

    /**
     * Points the subscription at its https address.
     *
     * Updated in place rather than removed and re-added, which keeps every
     * article, the read state, the category and whatever the ordering has
     * learned about this source. No resync: the feed is the same feed and was
     * just fetched to prove it, so there is nothing new to collect.
     */
    private suspend fun apply(feed: Feed, url: String) {
        val strict = runCatching { sloppyLinkToStrictURL(url) }.getOrNull() ?: return
        // A clash can appear between the check and the apply — two http feeds
        // whose https forms are the same address, where upgrading the first
        // creates the collision for the second.
        if (!sources.updateSource(feed.copy(url = strict))) {
            set(feed.id, Upgrade.Duplicate)
            return
        }
        // A feed that failed only because its address was http has no reason
        // to still be counted as failing once that is fixed.
        sources.clearFailures(feed.id)
        set(feed.id, Upgrade.Applied)
    }

    private fun set(id: Long, value: Upgrade) {
        _state.value = _state.value + (id to value)
    }
}

/**
 * The same address over https, or null if it was not http to begin with.
 *
 * Only the scheme changes. Host, port, path and query are left exactly as
 * they were — this is the one rewrite that is safe to make without asking,
 * and guessing at anything else would be inventing an address rather than
 * securing one.
 *
 * An explicit `:80` is dropped, because carrying it over to https would ask
 * for the plaintext port on a TLS connection and fail everywhere.
 */
internal fun httpsOf(url: URL): URL? {
    if (!url.protocol.equals("http", ignoreCase = true)) return null
    val port = if (url.port == 80 || url.port == -1) "" else ":${url.port}"
    val file = url.file
    val ref = url.ref?.let { "#$it" }.orEmpty()
    return runCatching { URL("https://${url.host}$port$file$ref") }.getOrNull()
}
