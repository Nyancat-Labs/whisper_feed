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

package com.saulhdev.feeder.utils


import com.saulhdev.feeder.data.entity.JsonFeed
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.ResponseBody
import java.io.IOException

fun feedAdapter(): JsonAdapter<JsonFeed> =
    Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(JsonFeed::class.java)

/**
 * Parses a JSONFeed that has already been fetched.
 *
 * It used to be able to fetch one too, through its own OkHttpClient built by a
 * `cachingHttpClient` whose `trustAllCerts` parameter defaulted to *true* — an
 * all-accepting X509TrustManager and a hostname verifier that returned true for
 * everything, which is to say no TLS at all. Nothing ever called that path:
 * [com.saulhdev.feeder.manager.models.FeedParser] fetches with its own guarded
 * client and hands the body here, so the only live entry point is [parseJson].
 *
 * A disabled-TLS client sitting unused one constructor call away from being
 * used is not worth keeping for the day somebody wants a download here. The
 * fetching is gone; if it comes back it comes back through the guarded clients
 * like everything else.
 */
class JsonFeedParser(
    private val jsonJsonFeedAdapter: JsonAdapter<JsonFeed> = feedAdapter()
) {

    /**
     * Parse a JSONFeed
     */
    fun parseJson(responseBody: ResponseBody): JsonFeed =
        parseJson(responseBody.string())

    /**
     * Parse a JSONFeed
     */
    fun parseJson(json: String): JsonFeed = jsonJsonFeedAdapter.fromJson(json)
        ?: throw IOException("Failed to parse JSONFeed")
}