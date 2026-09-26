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

package com.saulhdev.feeder.viewmodels

import android.os.Parcelable
import android.util.Log
import androidx.compose.runtime.Immutable
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.utils.candidateFeedUrls
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.normalizeFeedUrl
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.parcelize.Parcelize
import java.net.URL

class SearchFeedViewModel(
    private val sourcesRepo: SourcesRepository,
) : NeoViewModel() {
    private val feedParser: FeedParser = FeedParser()

    /**
     * Turns whatever the user typed into feeds they can subscribe to.
     *
     * Three stages, and the order is the point. Whatever was typed is tried
     * first, because most of the time it is already a feed. Then the site's own
     * `<link rel="alternate">` declarations, which are the site telling us the
     * answer. Only then the conventional paths — `/feed`, `/rss.xml` and the
     * rest — because that stage is guessing, and on a site that advertises its
     * feed properly a guess can land on something worse: a `/feed` that
     * redirects to the homepage, or a partial feed where the declared one is
     * complete.
     *
     * Probing stops as soon as a stage finds anything, so pasting a working
     * feed URL costs one request and `howtogeek.com` costs two. Only a site
     * with no feed declaration at all pays for the path probing, and even then
     * it stops at the first path that parses.
     *
     * Results are emitted as they are found rather than collected at the end,
     * so the first feed appears while the rest are still being checked.
     */
    fun searchForFeeds(url: URL): Flow<SearchResult> = flow {
        val seen = mutableSetOf<String>()

        suspend fun tryUrl(candidate: URL): SearchResult? {
            if (!seen.add(normalizeFeedUrl(candidate))) return null
            return try {
                feedParser.parseFeedUrl(candidate)?.let { feed ->
                    SearchResult(
                        title = feed.title.orEmpty(),
                        url = feed.feed_url ?: candidate.toString(),
                        description = feed.description.orEmpty(),
                        isError = false,
                        alreadyAdded = sourcesRepo.findSourceByUrl(candidate) != null,
                    )
                }
            } catch (_: Throwable) {
                null
            }
        }

        // 1. What was typed.
        val direct = tryUrl(url)
        if (direct != null) {
            emit(direct)
            return@flow
        }

        // 2. What the page declares.
        var found = false
        feedParser.getAlternateFeedLinksAtUrl(url).forEach { (link, _) ->
            tryUrl(sloppyLinkToStrictURL(link))?.let {
                found = true
                emit(it)
            }
        }
        if (found) return@flow

        // 3. Where feeds conventionally live.
        for (candidate in candidateFeedUrls(url)) {
            tryUrl(candidate)?.let {
                found = true
                emit(it)
            }
            if (found) return@flow
        }

        // Nothing anywhere. One failure for the address the user gave, rather
        // than one per candidate — a wall of errors for paths they never typed
        // says nothing useful.
        Log.d(TAG, "No feed found at or under the address given")
        emit(
            SearchResult(
                title = FAILED_TO_PARSE_PLACEHOLDER,
                url = url.toString(),
                description = "",
                isError = true,
            )
        )
    }.flowOn(Dispatchers.IO)

    companion object {
        const val FAILED_TO_PARSE_PLACEHOLDER = "failed_to_parse"
        private const val TAG = "SearchFeedViewModel"
    }
}

@Immutable
@Parcelize
data class SearchResult(
    val title: String,
    val url: String,
    val description: String,
    val isError: Boolean,
    val alreadyAdded: Boolean = false,
) : Parcelable
