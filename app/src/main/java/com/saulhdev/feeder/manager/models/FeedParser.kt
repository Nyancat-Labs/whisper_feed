/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

package com.saulhdev.feeder.manager.models

import android.util.Log
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import com.saulhdev.feeder.data.entity.JsonFeed
import com.saulhdev.feeder.utils.HttpIdentity.asFeedReader
import com.saulhdev.feeder.utils.ByteCounter
import com.saulhdev.feeder.utils.HttpStatusException
import com.saulhdev.feeder.utils.JsonFeedParser
import com.saulhdev.feeder.utils.extensions.asFeed
import com.saulhdev.feeder.utils.relativeLinkIntoAbsolute
import com.saulhdev.feeder.utils.relativeLinkIntoAbsoluteOrThrow
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Credentials
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import com.saulhdev.feeder.utils.FEED_MAX_BYTES
import com.saulhdev.feeder.utils.PAGE_MAX_BYTES
import com.saulhdev.feeder.utils.bytesAtMost
import com.saulhdev.feeder.utils.refuseMedia
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.Charset
import java.net.MalformedURLException
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val YOUTUBE_CHANNEL_ID_ATTR = "data-channel-external-id"

class FeedParser {
    private val client = OkHttpClient.Builder()
        .asFeedReader()
        // Every hop, including the ones OkHttp follows on its own. A feed URL
        // can come from a bookmarks file or a page's own head, so no address
        // reached from here has been vouched for by anybody.
        // Before the connection, so a feed still stored as http is asked
        // over https rather than refused.
        .onlyPublicHttps()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonFeedParser: JsonFeedParser = JsonFeedParser()

    private suspend fun getFeedIconAtUrl(url: URL): String? {
        return try {
            val html = curl(url)
            when {
                html != null -> getFeedIconInHtml(html, baseUrl = url)
                else -> null
            }
        } catch (t: Throwable) {
            Log.e("FeedParser", "Error when fetching feed icon", t)
            null
        }
    }

    /**
     * A site's own mark, for the line under a headline.
     *
     * Cheapest first, and the cheap one is also the best: the page's own
     * `<link rel="icon">` is what the site says its mark is, at whatever size
     * it chose to publish. `/favicon.ico` is the fallback for sites that
     * declare nothing and rely on the convention.
     *
     * Deliberately not used: a third-party favicon service. Google's
     * `s2/favicons` is one request away and would tell Google every source the
     * reader is subscribed to. The site's own server already knows we read it.
     */
    suspend fun findSiteIcon(siteUrl: URL): String? {
        getFeedIconAtUrl(siteUrl)?.let { return it }

        // The conventional filenames, decodable one first. /apple-touch-icon
        // .png is a PNG by definition and is present on most sites built this
        // decade; /favicon.ico is the older convention and often really is an
        // ICO, which Android cannot render.
        val origin = "${siteUrl.protocol}://${siteUrl.authority}"
        for (path in listOf("/apple-touch-icon.png", "/favicon.ico")) {
            val candidate = try {
                URL(origin + path)
            } catch (_: Throwable) {
                continue
            }
            val ok = try {
                var success = false
                curlAndOnResponse(candidate) { success = it.isSuccessful }
                success
            } catch (_: Throwable) {
                false
            }
            if (ok) return candidate.toString()
        }
        return null
    }

    private fun String.isIco(): Boolean =
        substringBefore('?').endsWith(".ico", ignoreCase = true)

    private fun getFeedIconInHtml(
        html: String,
        baseUrl: URL? = null,
    ): String? {
        val doc = Jsoup.parse(html.byteInputStream(), "UTF-8", "")

        val candidates = (
                doc.getElementsByAttributeValue("rel", "apple-touch-icon") +
                        doc.getElementsByAttributeValue("rel", "icon") +
                        doc.getElementsByAttributeValue("rel", "shortcut icon")
                )
            .filter { it.hasAttr("href") }
            .map {
                when {
                    baseUrl != null -> relativeLinkIntoAbsolute(
                        base = baseUrl,
                        link = it.attr("href")
                    )

                    else -> sloppyLinkToStrictURL(it.attr("href")).toString()
                }
            }

        // Android's decoder handles PNG, JPEG, WebP and friends — not ICO. A
        // site declaring both a PNG and a favicon.ico must yield the PNG, or
        // the card falls back to a monogram for a source that does have a
        // usable mark. .ico is kept as a last resort rather than dropped: some
        // servers put a PNG behind that filename, and the decoder sniffs the
        // bytes rather than trusting the extension.
        return candidates.firstOrNull { !it.isIco() } ?: candidates.firstOrNull()
    }

    /**
     * Returns all alternate links in the header of an HTML/XML document pointing to feeds.
     */
    suspend fun getAlternateFeedLinksAtUrl(url: URL): List<Pair<String, String>> {
        return try {
            val html = curl(url)
            when {
                html != null -> getAlternateFeedLinksInHtml(html, baseUrl = url)
                else -> emptyList()
            }
        } catch (t: Throwable) {
            Log.e("FeedParser", "Error when fetching alternate links", t)
            emptyList()
        }
    }

    /**
     * Returns all alternate links in the HTML/XML document pointing to feeds.
     */
    private fun getAlternateFeedLinksInHtml(
        html: String,
        baseUrl: URL? = null,
    ): List<Pair<String, String>> {
        val doc = Jsoup.parse(html.byteInputStream(), "UTF-8", "")

        val feeds = doc.getElementsByAttributeValue("rel", "alternate")
            .filter { element ->
                element.hasAttr("href") && element.hasAttr("type")
            }
            .filter { element ->
                val t = element.attr("type").lowercase(Locale.getDefault())
                when {
                    t.contains("application/atom") -> true
                    t.contains("application/rss") -> true
                    // Youtube for example has alternate links with application/json+oembed type.
                    t == "application/json" -> true
                    else -> false
                }
            }
            .filter { element ->
                val l = element.attr("href").lowercase(Locale.getDefault())
                try {
                    if (baseUrl != null) {
                        relativeLinkIntoAbsoluteOrThrow(base = baseUrl, link = l)
                    } else {
                        URL(l)
                    }
                    true
                } catch (_: MalformedURLException) {
                    false
                }
            }
            .map {
                when {
                    baseUrl != null -> relativeLinkIntoAbsolute(
                        base = baseUrl,
                        link = it.attr("href")
                    ) to it.attr("type")

                    else -> sloppyLinkToStrictURL(it.attr("href")).toString() to it.attr("type")
                }
            }

        return when {
            feeds.isNotEmpty() -> feeds
            baseUrl?.host == "www.youtube.com" || baseUrl?.host == "youtube.com" -> findFeedLinksForYoutube(
                doc
            )

            else -> emptyList()
        }
    }

    private fun findFeedLinksForYoutube(doc: Document): List<Pair<String, String>> {
        val channelId: String? = doc.body().getElementsByAttribute(YOUTUBE_CHANNEL_ID_ATTR)
            .firstOrNull()
            ?.attr(YOUTUBE_CHANNEL_ID_ATTR)

        return when (channelId) {
            null -> emptyList()
            else -> listOf("https://www.youtube.com/feeds/videos.xml?channel_id=$channelId" to "atom")
        }
    }

    /**
     * @throws IOException if request fails due to network issue for example
     */
    private suspend fun curl(url: URL) = client.curl(url)

    /**
     * @throws IOException if request fails due to network issue for example
     */
    private suspend fun curlAndOnResponse(url: URL, block: (suspend (Response) -> Unit)) =
        client.curlAndOnResponse(url, block)

    @Throws(FeedParsingError::class)
    suspend fun parseFeedUrl(url: URL): JsonFeed? {
        try {
            var result: JsonFeed? = null
            curlAndOnResponse(url) {
                result = parseFeedResponse(it)
            }
            // Preserve original URL to maintain authentication data and/or tokens in query params
            return result?.copy(feed_url = url.toString())
        } catch (e: Throwable) {
            throw FeedParsingError(url, e)
        }
    }

    /**
     * A feed fetched to be added or checked, read no further than a sync
     * would read it.
     *
     * Only the sync applied [FEED_MAX_BYTES]. Adding a feed, the discovery
     * pass and the https check came here and read the body whole, so an
     * address that never stopped sending, one a website's link could hand to
     * the weekly discovery pass, read until the app ran out of memory.
     */
    @Throws(FeedParsingError::class)
    suspend fun parseFeedResponse(response: Response): JsonFeed {
        return response.body.use {
            it.refuseMedia("a feed")
            parseFeedResponse(
                response.request.url.toUrl(),
                it.bytesAtMost(FEED_MAX_BYTES).toResponseBody(it.contentType()),
            )
        }
    }

    /**
     * Takes body as bytes to handle encoding correctly
     */
    @Throws(FeedParsingError::class)
    suspend fun parseFeedResponse(
        url: URL,
        responseBody: ResponseBody,
        /**
         * Whether a feed that declares no icon should have its site's home
         * page fetched to find one. Yes when a feed is being added; no from a
         * sync, which keeps the icon it found the first time. It was yes on
         * every sync of every such feed: a whole home page downloaded, each
         * time, to learn what was already stored.
         */
        findIcon: Boolean = true,
    ): JsonFeed {
        try {
            val feed = when (responseBody.contentType()?.subtype?.contains("json")) {
                true -> jsonFeedParser.parseJson(responseBody)
                else -> parseRssAtom(url, responseBody, findIcon)
            }

            return if (feed.feed_url == null) {
                // Nice to return non-null value here
                feed.copy(feed_url = url.toString())
            } else {
                feed
            }
        } catch (e: Throwable) {
            throw FeedParsingError(url, e)
        }
    }

    @Throws(FeedParsingError::class)
    internal suspend fun parseRssAtom(baseUrl: URL, responseBody: ResponseBody, findIcon: Boolean = true): JsonFeed {
        try {
            responseBody.byteStream().use { bs ->
                val feed = XmlReader(bs, true, responseBody.contentType()?.charset()?.name()).use {
                    SyndFeedInput()
                        .apply {
                            isPreserveWireFeed = true
                        }
                        .build(it)
                }
                return feed.asFeed(baseUrl = baseUrl) { siteUrl ->
                    if (findIcon) getFeedIconAtUrl(siteUrl) else null
                }
            }
        } catch (t: Throwable) {
            throw FeedParsingError(baseUrl, t)
        }
    }

    class FeedParsingError(val url: URL, e: Throwable) : Exception(e.message, e)
}

/** Whether a password written into a feed's address may answer this challenge. */
internal fun answersFeedChallenge(asking: okhttp3.HttpUrl, feedHost: String): Boolean =
    asking.isHttps && asking.host.equals(feedHost, ignoreCase = true)

suspend fun OkHttpClient.getResponse(
    url: URL,
    forceNetwork: Boolean = false,
    /** Hears this request's bytes, where the client listens; see ByteCounter. */
    byteCounter: ByteCounter? = null,
): Response {
    val request = Request.Builder()
        .url(url)
        .cacheControl(
            CacheControl.Builder()
                .let {
                    if (forceNetwork) {
                        // Force a cache revalidation
                        it.maxAge(0, TimeUnit.SECONDS)
                    } else {
                        // Do a cache revalidation at most every minute
                        it.maxAge(1, TimeUnit.MINUTES)
                    }
                }
                .build()
        )
        .apply { byteCounter?.let { tag(ByteCounter::class.java, it) } }
        .build()

    val clientToUse = if (url.userInfo?.isNotBlank() == true) {
        val parts = url.userInfo.split(':')
        val user = parts.first()
        val pass = if (parts.size > 1) {
            parts[1]
        } else {
            ""
        }
        val decodedUser = withContext(IO) {
            URLDecoder.decode(user, "UTF-8")
        }
        val decodedPass = withContext(IO) {
            URLDecoder.decode(pass, "UTF-8")
        }
        val credentials = Credentials.basic(decodedUser, decodedPass)
        val feedHost = url.host
        // The feed's own server asks, over https, and nobody else. These
        // answered any challenge: a host the feed redirected to got the
        // feed's password for asking, and so did any proxy on the way,
        // which is a different thing with its own credentials.
        newBuilder()
            .authenticator { _, response ->
                when {
                    response.request.header("Authorization") != null -> null
                    !answersFeedChallenge(response.request.url, feedHost) -> null
                    else -> response.request.newBuilder()
                        .header("Authorization", credentials)
                        .build()
                }
            }
            .build()
    } else {
        this
    }

    return withContext(IO) {
        clientToUse.newCall(request).execute()
    }
}

/**
 * A page as it came: its bytes, and the charset the server named, if it
 * named one. Kept as bytes so a page that names its encoding only in a
 * `<meta>` tag can be decoded by what it says; see [pageDocument].
 */
class Page(val bytes: ByteArray, val charset: Charset?)

/**
 * A page, no larger than [PAGE_MAX_BYTES] and not a picture, a sound or a
 * film. It was read whole, whatever it was.
 */
suspend fun OkHttpClient.fetchPage(url: URL): Page? {
    var page: Page? = null
    curlAndOnResponse(url) { response ->
        val body = response.body
        body.refuseMedia("a page")
        page = Page(body.bytesAtMost(PAGE_MAX_BYTES), body.contentType()?.charset())
    }
    return page
}

/**
 * A page as a document, in the encoding it names: the server's header if it
 * gave one, otherwise the page's own `<meta charset>`, otherwise UTF-8.
 * Decoding before jsoup could look meant a page in windows-1252 or Shift_JIS
 * with no header was read as UTF-8, and every accented letter came out as a
 * question mark in a box.
 */
fun pageDocument(page: Page, url: String): Document =
    Jsoup.parse(ByteArrayInputStream(page.bytes), page.charset?.name(), url)

suspend fun OkHttpClient.curl(url: URL): String? =
    fetchPage(url)?.let { String(it.bytes, it.charset ?: Charsets.UTF_8) }

suspend fun OkHttpClient.curlAndOnResponse(url: URL, block: (suspend (Response) -> Unit)) {
    // Closed on the failure path too. It used to throw before the use block,
    // leaving every refused response - and its connection - open.
    getResponse(url).use { response ->
        if (!response.isSuccessful) throw HttpStatusException(response.code)
        block(response)
    }
}
