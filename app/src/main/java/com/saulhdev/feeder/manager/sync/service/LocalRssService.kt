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

import com.saulhdev.feeder.manager.sync.backgroundFeedDeadline
import android.content.Context
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.manager.sync.syncFeeds
import com.saulhdev.feeder.utils.StepTrace

/**
 * Whisper with no account: fetch the feeds, store the articles, done.
 *
 * This is the default and the complete implementation, not a placeholder. It
 * is what the app has always done, given a name so that a remote service has
 * something to be additive to.
 */
class LocalRssService(
    private val context: Context,
    private val articles: ArticleRepository,
) : RssService() {

    override suspend fun sync(
        forceNetwork: Boolean,
        retryRefused: Boolean,
        trace: StepTrace?,
        background: Boolean,
    ): SyncOutcome =
        runCatching {
            syncFeeds(context = context, forceNetwork = forceNetwork, feedDeadlineMs = backgroundFeedDeadline(background))
        }
            .fold(
                onSuccess = { SyncOutcome.Success(feeds = it) },
                onFailure = { SyncOutcome.Failed(it) },
            )

    override suspend fun setRead(articleId: String, read: Boolean) {
        if (read) articles.markRead(articleId) else articles.unmarkRead(listOf(articleId))
    }

    override suspend fun setStarred(articleId: String, starred: Boolean) {
        articles.bookmarkArticle(articleId, starred)
    }
}
