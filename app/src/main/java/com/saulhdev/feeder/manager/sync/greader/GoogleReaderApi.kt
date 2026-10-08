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
package com.saulhdev.feeder.manager.sync.greader

import com.saulhdev.feeder.manager.bookmarks.allowingPrivateNetworks
import com.saulhdev.feeder.utils.HttpIdentity.asFeedReader
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import com.saulhdev.feeder.utils.ByteCounter
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * A client for the Google Reader API, as spoken by FreshRSS, Miniflux,
 * Inoreader and BazQux.
 *
 * Google's own Reader has been gone since 2013; what survived is its HTTP
 * interface, which those services implement because everything already spoke
 * it. One client therefore buys several services, needs no Google account and
 * no Play Services, and works against something a reader runs themselves.
 * `docs/REFERENCES.md` §2 records why this rather than Feedly, whose API turned
 * out to be enterprise-gated.
 *
 * The protocol is old and shows it. Authentication is a form post that returns
 * a token in a `key=value` body rather than JSON; writes need a *second*,
 * short-lived token fetched separately; and identifiers come in three shapes
 * (see [GoogleReaderIds]). None of that is avoidable, so it is all handled
 * here and nowhere else.
 *
 * This class does the protocol and nothing else: no database, no reconciling,
 * no decisions about what a sync means. That belongs above it.
 */
class GoogleReaderApi(
    private val serverUrl: String,
    client: OkHttpClient = defaultClient,
) {

    /**
     * Every byte this server has sent this instance, counted as it arrives.
     *
     * The match's size came from Android's count for the whole app, which on
     * some syncs had not caught up by the time it was read: 484 items reported
     * as "<1 KB". Counted here, it is this server's bytes and nobody else's,
     * and it is up to date the moment a response has been read.
     */
    private val received = ByteCounter()
    val receivedBytes: Long get() = received.bytes

    private val client: OkHttpClient = client.newBuilder().eventListenerFactory(ByteCounter.factory).build()

    /**
     * What a sign-in produced, or why it did not.
     *
     * [Failed] carries an [AccountProblem] rather than a sentence. This class
     * speaks the protocol and has no business choosing words for a screen it
     * cannot see, let alone words that would then need translating from here.
     * [Failed.detail] is the original text, kept for the cases where there is
     * nothing better to say than what the server or the exception said.
     */
    sealed interface AuthResult {
        data class Success(val token: String) : AuthResult
        data class Failed(
            val problem: AccountProblem,
            val detail: String? = null,
        ) : AuthResult
    }

    private val base: HttpUrl? =
        serverUrl.trimEnd('/').toHttpUrlOrNull()

    /**
     * Signs in with ClientLogin, the protocol's own scheme.
     *
     * Not OAuth: these are self-hosted services, and most of them issue an
     * application password for exactly this. The response is a plain-text body
     * of `key=value` lines, of which `Auth` is the one that matters.
     */
    suspend fun signIn(username: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            val url = base?.newBuilder()
                ?.addPathSegments("accounts/ClientLogin")
                ?.build()
                ?: return@withContext AuthResult.Failed(AccountProblem.BAD_ADDRESS)

            val body = FormBody.Builder()
                .add("Email", username)
                .add("Passwd", password)
                .build()

            try {
                postSignIn(url, body)
            } catch (t: Throwable) {
                AuthResult.Failed(problemFrom(t), t.message)
            }
        }

    /**
     * The sign-in request, following a redirect only to the same server.
     *
     * OkHttp follows a 307 or 308 by sending the same request again, body and
     * all, so a server that pointed the sign-in elsewhere handed the email
     * address and password to wherever it pointed. The same server moving its
     * own path (a trailing slash, a new folder) is still followed, since the
     * password goes nowhere new.
     */
    private fun postSignIn(url: HttpUrl, body: FormBody): AuthResult {
        val signInClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        var target = url
        repeat(MAX_SIGN_IN_REDIRECTS + 1) {
            signInClient.newCall(Request.Builder().url(target).post(body).build()).execute().use { r ->
                if (!r.isRedirect) return readSignIn(r)
                val next = r.header("Location")?.let { target.resolve(it) }
                    ?: return AuthResult.Failed(AccountProblem.NO_TOKEN)
                if (!sameServer(target, next)) return AuthResult.Failed(AccountProblem.MOVED, next.host)
                target = next
            }
        }
        return AuthResult.Failed(AccountProblem.UNKNOWN, "Too many redirects")
    }

    private fun readSignIn(r: Response): AuthResult {
        if (!r.isSuccessful) {
            return AuthResult.Failed(problem = problemFromStatus(r.code), detail = r.code.toString())
        }
        val auth = r.body.string()
            .lineSequence()
            .firstOrNull { it.startsWith("Auth=") }
            ?.removePrefix("Auth=")
            ?.trim()
        return if (auth.isNullOrEmpty()) AuthResult.Failed(AccountProblem.NO_TOKEN) else AuthResult.Success(auth)
    }

    /**
     * The write token, which is separate from the auth token and short-lived.
     *
     * Every mutating call needs one, and servers expire them aggressively, so
     * it is fetched per batch of writes rather than cached for the session.
     */
    suspend fun writeToken(auth: String): String? = withContext(Dispatchers.IO) {
        runCatching { getString(auth, "reader/api/0/token").trim() }.getOrNull()
    }

    /** Every feed the account subscribes to, with the folders it is filed in. */
    suspend fun subscriptions(auth: String): List<Subscription> = withContext(Dispatchers.IO) {
        val json = getString(auth, "reader/api/0/subscription/list", "output" to "json")
        subscriptionsAdapter.fromJson(json)?.subscriptions.orEmpty()
    }

    /** The ids of everything in a stream, for working out what is unread. */
    suspend fun itemIds(
        auth: String,
        stream: String = GoogleReaderIds.STREAM_READING_LIST,
        excludeTag: String? = null,
        limit: Int = 1000,
        since: Long? = null,
    ): List<String> = itemIdPage(auth, stream, excludeTag, limit, since, null).first

    /** One page of [itemIds], and where the next one starts, if there is one. */
    private suspend fun itemIdPage(
        auth: String,
        stream: String,
        excludeTag: String?,
        limit: Int,
        since: Long?,
        continuation: String?,
    ): Pair<List<String>, String?> = withContext(Dispatchers.IO) {
        val params = buildList {
            add("s" to stream)
            add("n" to limit.toString())
            add("output" to "json")
            excludeTag?.let { add("xt" to it) }
            since?.let { add("ot" to (it / 1000).toString()) }
            continuation?.let { add("c" to it) }
        }
        val json = getString(auth, "reader/api/0/stream/items/ids", *params.toTypedArray())
        val page = itemRefsAdapter.fromJson(json)
        page?.itemRefs.orEmpty().mapNotNull { GoogleReaderIds.itemId(it.id) } to
            page?.continuation?.takeIf { it.isNotBlank() }
    }

    /**
     * Every id in a stream, page by page, and whether that was all of them.
     *
     * One page of a thousand was all this ever asked for, and an account
     * with more unread than that - any account that has imported a hundred
     * feeds - got the first thousand and took them for the whole answer.
     * [Paged.complete] is false when the pages ran out before the server did,
     * and anything that reads meaning into what is *missing* from the list
     * has to check it.
     */
    suspend fun allItemIds(
        auth: String,
        stream: String = GoogleReaderIds.STREAM_READING_LIST,
        excludeTag: String? = null,
        since: Long? = null,
        pageSize: Int = 10_000,
        maxPages: Int = 10,
    ): Paged<String> = collectPages(maxPages) { c -> itemIdPage(auth, stream, excludeTag, pageSize, since, c) }

    /**
     * The items in a stream, with the addresses they point at.
     *
     * This is the only call that says which server id belongs to which
     * article, and the app needs that because it does not take its articles
     * from the server: they are fetched from the feeds themselves, so every
     * one has a local id the server has never seen. The link is the one thing
     * both sides know.
     *
     * Only the id and the alternate link are read. The rest of the payload is
     * the article itself, which this app already has in a better form —
     * extracted full text, a chosen image, a built summary — so taking the
     * server's copy would mean losing those or fetching twice.
     */
    suspend fun streamContents(
        auth: String,
        stream: String = GoogleReaderIds.STREAM_READING_LIST,
        limit: Int = 1000,
        since: Long? = null,
    ): List<StreamItem> = contentsPage(auth, stream, limit, since, null).first

    suspend fun contentsPage(
        auth: String,
        stream: String,
        limit: Int,
        since: Long?,
        continuation: String?,
        /** Oldest first, so a match can save its progress page by page. */
        oldestFirst: Boolean = false,
    ): Pair<List<StreamItem>, String?> = withContext(Dispatchers.IO) {
        val params = buildList {
            add("n" to limit.toString())
            add("output" to "json")
            since?.let { add("ot" to (it / 1000).toString()) }
            if (oldestFirst) add("r" to "o")
            continuation?.let { add("c" to it) }
        }
        val json = getString(
            auth,
            "reader/api/0/stream/contents/" + stream,
            *params.toTypedArray(),
        )
        val page = streamContentsAdapter.fromJson(json)
        page?.items.orEmpty() to page?.continuation?.takeIf { it.isNotBlank() }
    }

    /**
     * Every item in a stream since [since], page by page. Heavier than the
     * ids - each item carries its article - so [since] is what keeps it
     * small: only what arrived since the last time articles were matched.
     */
    suspend fun allStreamContents(
        auth: String,
        stream: String = GoogleReaderIds.STREAM_READING_LIST,
        since: Long? = null,
        pageSize: Int = 1000,
        maxPages: Int = 20,
    ): Paged<StreamItem> = collectPages(maxPages) { c -> contentsPage(auth, stream, pageSize, since, c) }

    /**
     * The items with these ids, with their addresses: the same payload as a
     * stream, for a list of ids instead of a stream. FreshRSS takes the ids in
     * decimal, as [GoogleReaderIds.itemId] gives them, repeated one per `i`.
     */
    suspend fun itemsContents(auth: String, ids: List<String>): List<StreamItem> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val url = base?.newBuilder()?.addPathSegments("reader/api/0/stream/items/contents")
            ?.addQueryParameter("output", "json")?.build()
            ?: throw IOException("Not a valid server address")
        val body = FormBody.Builder().apply { ids.forEach { add("i", it) } }.build()
        client.newCall(authorised(auth, url).post(body).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for stream/items/contents")
            streamContentsAdapter.fromJson(r.body.string())?.items.orEmpty()
        }
    }

    /** Marks items read or unread, starred or not, in one call per batch. */
    suspend fun editTag(
        auth: String,
        token: String,
        itemIds: List<String>,
        addTag: String? = null,
        removeTag: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (itemIds.isEmpty()) return@withContext true
        val url = base?.newBuilder()?.addPathSegments("reader/api/0/edit-tag")?.build()
            ?: return@withContext false

        val body = FormBody.Builder().apply {
            add("T", token)
            addTag?.let { add("a", it) }
            removeTag?.let { add("r", it) }
            // One parameter per item, repeated — the protocol has no list form.
            itemIds.forEach { add("i", it) }
        }.build()

        runCatching {
            client.newCall(authorised(auth, url).post(body).build()).execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** Subscribes, unsubscribes, renames or refiles one feed. */
    suspend fun editSubscription(
        auth: String,
        token: String,
        action: String,
        feedUrl: String,
        title: String? = null,
        addLabel: String? = null,
        removeLabel: String? = null,
    ): Boolean = editSubscriptionStatus(auth, token, action, feedUrl, title, addLabel, removeLabel) in 200..299

    /**
     * [editSubscription], with the server's status: 0 when there was no
     * answer at all. The report says which, so a refused feed is not a mystery.
     */
    suspend fun editSubscriptionStatus(
        auth: String,
        token: String,
        action: String,
        feedUrl: String,
        title: String? = null,
        addLabel: String? = null,
        removeLabel: String? = null,
    ): Int = withContext(Dispatchers.IO) {
        val url = base?.newBuilder()?.addPathSegments("reader/api/0/subscription/edit")?.build()
            ?: return@withContext 0

        val body = FormBody.Builder().apply {
            add("T", token)
            add("ac", action)
            add("s", GoogleReaderIds.feedStream(feedUrl))
            title?.let { add("t", it) }
            addLabel?.let { add("a", GoogleReaderIds.labelStream(it)) }
            removeLabel?.let { add("r", GoogleReaderIds.labelStream(it)) }
        }.build()

        runCatching {
            client.newCall(authorised(auth, url).post(body).build()).execute()
                .use { it.code }
        }.getOrDefault(0)
    }

    private fun authorised(auth: String, url: HttpUrl) = Request.Builder()
        .url(url)
        .tag(ByteCounter::class.java, received)
        // The protocol's own header, unchanged since Google Reader.
        .header("Authorization", "GoogleLogin auth=$auth")

    private fun getString(auth: String, path: String, vararg params: Pair<String, String>): String {
        val url = base?.newBuilder()
            ?.addPathSegments(path)
            ?.apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            ?.build()
            ?: throw IOException("Not a valid server address: $serverUrl")

        client.newCall(authorised(auth, url).build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} for $path")
            return r.body.string()
        }
    }

    companion object {
        /**
         * Identified as Whisper, not as a browser. This is an API belonging to
         * a server the reader chose, so there is nothing to be gained by
         * pretending to be anything else.
         */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .asFeedReader()
                // The one client allowed onto the reader's own network, and
                // the one that takes no https upgrade. Both follow from the
                // same fact: this address was typed into a sign-in form by the
                // reader rather than supplied by a publisher. Self-hosted
                // servers live on the LAN, so refusing private addresses here
                // would unsupport the feature; and the scheme is corrected
                // where they can see it happen, at sign-in, rather than
                // silently on the way to the socket.
                .allowingPrivateNetworks()
                .build()
        }

        private val moshi: Moshi =
            Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

        internal val subscriptionsAdapter = moshi.adapter(SubscriptionList::class.java)
        internal val itemRefsAdapter = moshi.adapter(ItemRefList::class.java)
        internal val streamContentsAdapter = moshi.adapter(StreamContents::class.java)
    }
}

@JsonClass(generateAdapter = true)
data class SubscriptionList(val subscriptions: List<Subscription> = emptyList())

@JsonClass(generateAdapter = true)
data class Subscription(
    val id: String = "",
    val title: String = "",
    val url: String? = null,
    val htmlUrl: String? = null,
    val iconUrl: String? = null,
    val categories: List<SubscriptionCategory> = emptyList(),
) {
    /** The feed's address, from whichever field this server chose to fill in. */
    val feedUrl: String get() = url ?: GoogleReaderIds.feedUrl(id)

    /**
     * The folders it is filed in, as the category names Whisper uses.
     *
     * Without the server's catch-all folder, which is its name for "none":
     * FreshRSS files anything without a folder under "Uncategorized", and
     * taken as a category it turned up in Whisper's list as if the reader had
     * made it. See [isCatchAllFolder].
     */
    val folders: List<String>
        get() = categories.map { GoogleReaderIds.labelName(it.id) }.filterNot(::isCatchAllFolder)
}

@JsonClass(generateAdapter = true)
data class SubscriptionCategory(val id: String = "", val label: String? = null)

@JsonClass(generateAdapter = true)
data class ItemRefList(val itemRefs: List<ItemRef> = emptyList(), val continuation: String? = null)

@JsonClass(generateAdapter = true)
data class ItemRef(val id: String = "")

@JsonClass(generateAdapter = true)
data class StreamContents(val items: List<StreamItem> = emptyList(), val continuation: String? = null)

/** Items from every page asked for, and whether that was all the server had. */
data class Paged<T>(val items: List<T>, val complete: Boolean)

/**
 * Follows continuations until the server has no more or [maxPages] have been
 * read. A continuation that repeats is the end too: a server that hands back
 * the same one would otherwise be asked for the same page until the cap.
 */
suspend fun <T> collectPages(
    maxPages: Int,
    page: suspend (continuation: String?) -> Pair<List<T>, String?>,
): Paged<T> {
    val items = mutableListOf<T>()
    var continuation: String? = null
    val seen = HashSet<String>()
    repeat(maxPages) {
        val (got, next) = page(continuation)
        items += got
        if (next == null || !seen.add(next)) return Paged(items, complete = true)
        continuation = next
    }
    return Paged(items, complete = false)
}

@JsonClass(generateAdapter = true)
data class StreamItem(
    val id: String = "",
    val alternate: List<StreamLink> = emptyList(),
    /** When the server took the item in, in milliseconds, as text. */
    val crawlTimeMsec: String? = null,
    /** The same, in microseconds; some servers send only this. */
    val timestampUsec: String? = null,
) {
    /** When the server took the item in, in milliseconds, or null if it does not say. */
    fun crawledAt(): Long? =
        crawlTimeMsec?.toLongOrNull() ?: timestampUsec?.toLongOrNull()?.div(1000)

    /**
     * The article's address, and the server's id for it in the short form.
     *
     * Null when either is missing, which is how a malformed item costs one
     * article rather than the whole mapping.
     */
    fun mapping(): Pair<String, String>? {
        val link = alternate.firstOrNull { !it.href.isNullOrBlank() }?.href ?: return null
        val item = GoogleReaderIds.itemId(id) ?: return null
        return link to item
    }
}

@JsonClass(generateAdapter = true)
data class StreamLink(val href: String? = null, val type: String? = null)

/**
 * A server's folder for feeds that have no folder.
 *
 * FreshRSS calls it "Uncategorized", in English whatever the account's
 * language, and other servers use the same word or its British spelling.
 */
fun isCatchAllFolder(name: String): Boolean =
    name.trim().lowercase() in setOf("uncategorized", "uncategorised")

/** More than a server moving its own path needs. */
private const val MAX_SIGN_IN_REDIRECTS = 3

/** Same scheme, host and port: a redirect the password may follow. */
internal fun sameServer(from: HttpUrl, to: HttpUrl): Boolean =
    to.scheme == from.scheme && to.host.equals(from.host, ignoreCase = true) && to.port == from.port
