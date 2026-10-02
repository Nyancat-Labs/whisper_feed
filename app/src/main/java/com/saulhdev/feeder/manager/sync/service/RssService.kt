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
package com.saulhdev.feeder.manager.sync.service

import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.utils.StepTrace

/**
 * How Whisper gets its articles, whether or not there is an account.
 *
 * The shape is taken from ReadYou's `AbstractRssRepository`, recorded in
 * `docs/REFERENCES.md` §2, and the important property is which method is
 * abstract: **only [sync] is.** Everything else has a working local
 * implementation here, and a remote provider overrides only what its service
 * actually supports.
 *
 * That is what keeps sync additive rather than a mode. [LocalRssService] is
 * not a null object standing in until a real one arrives — it is the real
 * default and the app is complete without ever having an account. A provider
 * adds replication to something that already works, and nothing above this
 * layer has to ask which case it is in.
 *
 * The alternative shape, a bare `pull()`/`push()` adapter, forces every caller
 * to know whether an adapter exists and what it can do, and puts the
 * local-only path on the error branch of code written for the remote one. That
 * is how local-first quietly becomes remote-first with an offline mode.
 */
abstract class RssService {

    /**
     * Brings local and remote into agreement.
     *
     * The only thing a provider must implement. For the local service this is
     * fetching the feeds; for a remote one it is a reconciliation, which is a
     * different job with the same name — and the reason it is the one method
     * nothing can be assumed about.
     */
    abstract suspend fun sync(
        /**
         * Fetch every feed now, as a pull to refresh does. False for the syncs
         * nobody asked for, so slow feeds rest and fresh ones are left alone;
         * see syncFeeds. It was always true, so with an account signed in
         * every scheduled sync fetched all of them.
         */
        forceNetwork: Boolean = false,
        /**
         * Offer the server again every feed it refused, however recently.
         * Only Sync now on the account screen: see refusedDueForRetry.
         */
        retryRefused: Boolean = false,
        /** Told each step as it starts and ends, for a run that is stopped; see StepTrace. */
        trace: StepTrace? = null,
    ): SyncOutcome

    /**
     * Whether this service can accept a change made offline.
     *
     * A remote service usually can, by queueing it. It is asked rather than
     * assumed because the answer decides whether the interface should offer
     * an action at all when the network is gone.
     */
    open val acceptsOfflineChanges: Boolean get() = true

    /** Marks an article read or unread, wherever that needs recording. */
    open suspend fun setRead(articleId: String, read: Boolean) = Unit

    /** Saves or unsaves an article. */
    open suspend fun setStarred(articleId: String, starred: Boolean) = Unit

    /** Subscribes to a feed. */
    open suspend fun subscribe(url: String, title: String?, folder: String?) = Unit

    /** Unsubscribes from a feed. */
    open suspend fun unsubscribe(url: String) = Unit

    /** Renames a feed, or refiles it. */
    open suspend fun editFeed(url: String, title: String?, folder: String?) = Unit
}

/** What a sync did, in terms the interface can say out loud. */
sealed interface SyncOutcome {
    data class Success(
        val at: Long = System.currentTimeMillis(),
        /**
         * What fetching the feeds did, for the sync history: an account sync
         * fetches them too, and its line said only "ok".
         */
        val feeds: com.saulhdev.feeder.utils.SyncResult? = null,
    ) : SyncOutcome

    /** The credential is no longer good. Distinct because it needs the reader. */
    data object SignedOut : SyncOutcome

    /** Something transient. Worth retrying without telling anyone. */
    data class Failed(val cause: Throwable?) : SyncOutcome
}

/**
 * The history line's view of an outcome. One place, because the worker and
 * the account screen's "Sync now" both write one.
 */
fun SyncOutcome.toSyncResult(): com.saulhdev.feeder.utils.SyncResult = when (this) {
    is SyncOutcome.Success -> feeds ?: com.saulhdev.feeder.utils.SyncResult.uncounted
    SyncOutcome.SignedOut -> com.saulhdev.feeder.utils.SyncResult(due = 0, error = "signed out")
    is SyncOutcome.Failed -> cause?.let(com.saulhdev.feeder.utils.SyncResult::broken)
        ?: com.saulhdev.feeder.utils.SyncResult(due = 0, error = "error")
}

/**
 * Which service is in charge, from the account there is.
 *
 * A single place that maps the account to a provider, so nothing else in the
 * app branches on whether there is one. Adding a second protocol later means
 * adding a branch here and nothing anywhere else.
 */
class RssServiceDispatcher(
    private val account: SyncAccount,
    private val local: LocalRssService,
    private val googleReaderFactory: () -> RssService,
) {
    fun current(): RssService =
        if (account.isSignedIn) googleReaderFactory() else local
}
