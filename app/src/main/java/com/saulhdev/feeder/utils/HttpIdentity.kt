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

import com.saulhdev.feeder.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Who Whisper says it is, and what it says it will accept.
 *
 * Every request went out with no `User-Agent` at all, which OkHttp fills in as
 * `okhttp/5.x`, and no `Accept`. That is a request from nothing in particular,
 * and a lot of sites answer it differently from a browser: a consent page, a
 * stripped variant, a challenge, or a flat 403. It was the reason articles
 * opened in the reader could be shorter than the same article in a browser.
 *
 * There are two identities on purpose, because the two jobs are not the same.
 */
object HttpIdentity {

    /**
     * Fetching a feed: say who we are.
     *
     * A feed is published to be fetched by readers, and the ones that block do
     * it because an anonymous client tells them nothing. The comment-style
     * token is the long-standing convention for a non-browser client, and it
     * carries the project's address so an administrator seeing it in a log can
     * find out what it is.
     */
    const val FEED_AGENT: String =
        "Mozilla/5.0 (compatible; Whisper/${BuildConfig.VERSION_NAME}; " +
                "+https://github.com/Nyancat-Labs/whisper_feed)"

    /**
     * Fetching an article to read: ask for the page a browser would get.
     *
     * The point of the full-text fetch is to end up with the same document the
     * reader would have seen had they opened the link, so this asks as that
     * reader's browser. It is not a disguise for getting past a paywall — it
     * carries no cookies and no account, so anything gated stays gated; it is
     * about not being handed the no-JavaScript stub when the real page was
     * available all along.
     */
    const val ARTICLE_AGENT: String =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Mobile Safari/537.36"

    /** What a browser sends. Some servers branch on this as much as on the agent. */
    private const val HTML_ACCEPT =
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif," +
                "image/webp,*/*;q=0.8"

    /**
     * Fetching a picture: ask as the page it belongs to would.
     *
     * Images had no identity at all — the loader was built with the network
     * guards and nothing else, so every picture went out as `okhttp/5.5.0`
     * with no Accept header. This file's own reasoning applies to them as
     * much as to pages: a publisher's image CDN is exactly the sort of host
     * that answers an unknown client with a 403, and hotlink protection is
     * usually implemented as precisely that check.
     *
     * The order is measured rather than assumed, and it has been wrong once
     * already. This first listed WebP on the reasoning that it is smaller than
     * JPEG and cheap to decode. The first half is true and the second is not,
     * on this hardware: across one scroll, 49 WebP images averaged **45.8ms**
     * to decode against 28 JPEGs at **24.4ms** and 17 PNGs at 8.4ms. Before
     * that header existed the same feeds served almost no WebP at all — so
     * asking for it moved publishers onto the slower format and made decoding
     * the feed nearly twice as expensive.
     *
     * AVIF is worse again and is not advertised at all: 144.6ms for a single
     * image in the same scroll, because Android decodes it through a software
     * AV1 codec, one MediaCodec instance at a time.
     *
     * So JPEG and PNG are preferred outright, WebP is accepted at a lower
     * quality value, and AVIF is left off. A content-negotiating CDN sends the
     * best format the client claims to want, and what this app wants is the
     * one that draws fastest — bytes over the wire are cheap next to a decode
     * that competes with the scroll.
     */
    private const val IMAGE_ACCEPT =
        "image/jpeg,image/png,image/gif,image/webp;q=0.7,*/*;q=0.5"

    /** What a feed reader should ask for first, falling back to anything. */
    private const val FEED_ACCEPT =
        "application/atom+xml,application/rss+xml,application/xml;q=0.9," +
                "application/feed+json,text/xml;q=0.8,*/*;q=0.5"

    /**
     * Adds the headers, without overwriting any a call site set for itself.
     */
    private fun interceptor(agent: String, accept: String) = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        if (original.header("User-Agent") == null) builder.header("User-Agent", agent)
        if (original.header("Accept") == null) builder.header("Accept", accept)
        if (original.header("Accept-Language") == null) {
            builder.header("Accept-Language", "en-GB,en;q=0.9")
        }
        chain.proceed(builder.build())
    }

    /**
     * Identifies as a browser, and asks for an image.
     *
     * The browser agent rather than Whisper's own: a picture is a subresource
     * of a page, and the hosts that serve them are checking for the browser
     * that would have requested it alongside the article.
     */
    fun OkHttpClient.Builder.asImageFetcher(): OkHttpClient.Builder =
        addInterceptor(interceptor(ARTICLE_AGENT, IMAGE_ACCEPT))

    /** Identifies as Whisper, and asks for a feed. */
    fun OkHttpClient.Builder.asFeedReader(): OkHttpClient.Builder =
        addInterceptor(interceptor(FEED_AGENT, FEED_ACCEPT))

    /** Identifies as a browser, and asks for a page. */
    fun OkHttpClient.Builder.asArticleReader(): OkHttpClient.Builder =
        addInterceptor(interceptor(ARTICLE_AGENT, HTML_ACCEPT))
}
