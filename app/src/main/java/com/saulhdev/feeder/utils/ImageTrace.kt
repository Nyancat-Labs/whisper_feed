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

import coil.EventListener
import coil.decode.DataSource
import coil.decode.DecodeResult
import coil.decode.Decoder
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.Options
import coil.request.SuccessResult

/**
 * Times image decodes and records what format they were.
 *
 * A scroll's logcat showed six `c2.android.av1-dav1d.decoder` instances being
 * created — a full MediaCodec each, with its own native buffer pools. Nothing
 * in this app plays video. They are AVIF images: Android decodes AVIF and HEIF
 * through MediaCodec rather than through the ordinary bitmap path, so a
 * publisher serving AVIF silently turns each picture into a codec
 * instantiation.
 *
 * Worse than it first looks, and the codec's name is the reason. `c2.android.*`
 * are the AOSP *software* codecs; a hardware AV1 decoder would carry the
 * vendor's prefix. So these frames are being decoded on the CPU, one image at
 * a time, on a phone that has a hardware decoder it is not being handed.
 *
 * Which leaves a question worth answering before anything is changed: whether
 * it costs enough to matter. The obvious remedy is to stop advertising AVIF in
 * the Accept header, so a content-negotiating CDN sends WebP or JPEG instead —
 * but that trades a smaller download for a cheaper decode, and which way that
 * trade falls depends on numbers nobody has yet. Six decoder instantiations in
 * a minute might be forty milliseconds each or four.
 *
 * So this measures it: how many images were served, where from, how many
 * needed decoding at all, and how long each format took. The cache figures
 * come along for free and answer the other open question — whether the memory
 * cache is big enough, or whether scrolling back re-decodes everything.
 *
 * One instance per request, which is what makes the timing simple: the start
 * time is an ordinary field rather than an entry in a map that has to be
 * cleaned up on every path a request can fail down.
 *
 * **Formats and durations only, never addresses.** A URL names a publisher and
 * often an article; these lines go into a file the reader sends to a stranger.
 * The mime type is the whole of what is needed.
 */
class ImageTrace private constructor() : EventListener {

    private var decodeStartedAt = 0L
    private var mimeType: String? = null

    override fun fetchEnd(
        request: ImageRequest,
        fetcher: Fetcher,
        options: Options,
        result: FetchResult?,
    ) {
        mimeType = (result as? SourceResult)?.mimeType
    }

    override fun decodeStart(request: ImageRequest, decoder: Decoder, options: Options) {
        decodeStartedAt = System.nanoTime()
    }

    override fun decodeEnd(
        request: ImageRequest,
        decoder: Decoder,
        options: Options,
        result: DecodeResult?,
    ) {
        if (decodeStartedAt == 0L) return
        FeedTrace.imageDecoded(
            format = shortFormat(mimeType),
            micros = (System.nanoTime() - decodeStartedAt) / 1_000,
        )
        decodeStartedAt = 0L
    }

    /**
     * Where the picture came from, decoded or not.
     *
     * A memory-cache hit never reaches [decodeEnd], so counting only decodes
     * would say nothing about how often one was avoided — which is the whole
     * question about the cache's size.
     */
    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
        FeedTrace.imageServed(result.dataSource)
    }

    /**
     * A picture that did not arrive, and roughly why.
     *
     * The trace counted successes and decodes only, so an image that failed
     * left no trace at all — the line simply had a smaller number in it. When
     * article images came back as placeholders, the report could say nothing
     * about them, and the cause had to be argued from the source instead.
     *
     * The throwable's type and message, never the address. A URL names the
     * publisher and usually the article, and this goes into a file the reader
     * sends to a stranger. "HttpException: HTTP 403" is the whole of what is
     * needed to tell a refusal from a timeout from a bad address.
     */
    override fun onError(request: ImageRequest, result: ErrorResult) {
        FeedTrace.imageFailed(reasonOf(result.throwable), surfaceOf(request))
    }

    /**
     * Which part of the app asked for a picture that did not arrive.
     *
     * "Some images are going missing" is two different bugs depending on the
     * answer — the reader's own image pipeline, or the feed cards' — and a
     * count that does not say which leaves the next report to settle it.
     *
     * Only the article body builds an explicit [ImageRequest]; the cards and
     * the source marks hand [coil.compose.AsyncImage] a bare address and let
     * it build one. So the reader's requests are tagged and everything else
     * is [OTHER_SURFACE] by not being tagged, rather than by being labelled
     * something this cannot actually check.
     *
     * A tag is not part of the memory cache key. `setParameter` would have
     * been — it derives one from the value unless told not to — and tagging
     * every article image would then have given each one its own cache entry.
     */
    private fun surfaceOf(request: ImageRequest): String =
        request.tags.tag(ImageSurface::class.java)?.label ?: OTHER_SURFACE

    object Factory : EventListener.Factory {
        override fun create(request: ImageRequest): EventListener = ImageTrace()
    }

    companion object {
        /**
         * The part of a mime type worth counting.
         *
         * `image/avif` is the one this exists for, but bucketing everything
         * keeps the comparison honest: "avif is slow" means nothing without
         * the jpeg figure from the same scroll beside it.
         */
        /**
         * A failure, named by kind rather than by page.
         *
         * An HTTP status is the answer nine times in ten and is worth keeping
         * exactly; anything else is identified by its class, which separates
         * a timeout from a refused host from a decode that gave up.
         */
        internal fun reasonOf(t: Throwable?): String {
            if (t == null) return "unknown"
            val status = HTTP_STATUS.find(t.message.orEmpty())
            if (status != null) return "http ${status.groupValues[1]}"
            val kind = t.javaClass.simpleName.ifBlank { "unknown" }
            val detail = safeMessage(t.message)
            return if (detail.isEmpty()) kind else "$kind: $detail"
        }

        /**
         * A status code, only where the message says it is one.
         *
         * Any three digits starting 4 or 5 used to count, so "failed to
         * connect to host:443" reached the report as "http 443" - a port read
         * as a status - and a real redirect such as "HTTP 301" did not count
         * at all. Coil writes `HTTP 404: Not Found`; OkHttp writes
         * `code=403`. Those two, any status.
         */
        private val HTTP_STATUS = Regex("""(?:\bHTTP\s+|\bcode=)([1-5][0-9]{2})\b""", RegexOption.IGNORE_CASE)

        /**
         * Anything that could be an address.
         *
         * `://` rather than any colon. A bare colon is punctuation far more
         * often than it is a scheme, and treating it as an address cost the
         * one word that mattered: OkHttp's `Invalid URL host: ""` lost `host:`
         * and reached the report as `Invalid URL ""`, which reads as an empty
         * address when the address was there and only its host was missing.
         * That sent the first look at it after the wrong thing.
         */
        private val ADDRESSISH = Regex("""\S*(://|[/@]|\.[A-Za-z]{2,})\S*""")

        /**
         * The part of a failure's message that is safe to write down.
         *
         * A class name alone was not enough to act on. Two reports running,
         * "IllegalArgumentException" was the most common image failure in the
         * app and named nothing whatsoever: the class is thrown by a zero
         * decode size, by a malformed address and by a handful of things
         * inside Coil, and picking between them meant guessing at the source
         * and shipping the guess. The message says which — "px must be > 0."
         * is the whole answer to one of those.
         *
         * But these go into a file the reader sends to a stranger, and a
         * message is the one place a URL turns up uninvited. So every token
         * that could be an address — anything carrying a colon, a slash, an
         * at-sign, or a dot followed by letters — is dropped rather than
         * trimmed, and what is left is capped. A message that was nothing but
         * an address comes back empty and the class name stands alone, which
         * is where this started.
         */
        internal fun safeMessage(message: String?): String {
            if (message.isNullOrBlank()) return ""
            val cleaned = message
                .replace(ADDRESSISH, "")
                .replace(Regex("""\s+"""), " ")
                .trim()
            return if (cleaned.length <= MAX_REASON_DETAIL) cleaned
            else cleaned.take(MAX_REASON_DETAIL).trimEnd() + "\u2026"
        }

        /** Long enough for a sentence, short enough to stay a label. */
        private const val MAX_REASON_DETAIL = 60

        internal fun shortFormat(mimeType: String?): String = when {
            mimeType.isNullOrBlank() -> "unknown"
            else -> mimeType.substringAfter('/', mimeType)
                .substringBefore(';')
                .trim()
                .lowercase()
                .removePrefix("x-")
                .ifEmpty { "unknown" }
        }
    }
}

/** Short names for [DataSource], for the trace line. */
internal fun DataSource.shortName(): String = when (this) {
    DataSource.MEMORY_CACHE, DataSource.MEMORY -> "mem"
    DataSource.DISK -> "disk"
    DataSource.NETWORK -> "net"
}

/** Where an image request came from. See ImageTrace.surfaceOf. */
enum class ImageSurface(val label: String) {
    /** The built-in reader's article body. */
    Article("article"),
}

/** Anything that did not say. */
const val OTHER_SURFACE = "other"
