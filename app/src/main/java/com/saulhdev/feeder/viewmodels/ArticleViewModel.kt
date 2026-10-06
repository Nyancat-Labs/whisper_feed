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

import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.models.fullTextClient
import com.saulhdev.feeder.manager.models.parseFullArticleIfMissing
import com.saulhdev.feeder.utils.blobFullFile
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.plus

/** @see com.saulhdev.feeder.ui.overlay.TrackArticleReading */
class ArticleViewModel(
    private val articleRepo: ArticleRepository,
    private val sourcesRepo: SourcesRepository,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    private val articleId: MutableStateFlow<String> = MutableStateFlow("")

    fun setArticleId(value: String) {
        articleId.update { value }
    }

    /**
     * Adds a measured chunk of reading time to an article.
     *
     * An amount, not a total: see [TrackArticleReading], which is the only
     * thing that calls this and explains why the distinction is the whole
     * point rather than a detail of the signature.
     */
    fun addReading(id: String, millis: Long) {
        ioScope.launch { articleRepo.addReading(id, millis) }
    }

    /**
     * Whether the readable version of the open article is here yet.
     *
     * A feed entry is usually a paragraph and a link — that is what RSS is —
     * so a reader that shows only what the feed sent shows a teaser. The full
     * page is fetched and put through Readability, which is what "open in the
     * browser" was really being used for.
     */
    enum class FullText { Absent, Loading, Ready, Failed }

    /**
     * A full-text state, and the article it is about.
     *
     * The id is the whole point. This view model is scoped to the activity —
     * see koinNeoViewModel, which does that deliberately so screens share one
     * instance across navigation — so every article the reader opens uses the
     * same one, and during a navigation transition the outgoing and incoming
     * pages are both composed against it at once.
     *
     * A bare state could not survive that. The outgoing page's disposal calls
     * resetFullText, and it can land after the incoming page has already
     * reported Ready: the new article then renders the paragraph the feed sent
     * instead of the page that was just fetched for it. The reverse happens
     * too — a Ready left by the previous article, read by the next one before
     * its own file exists, shows "not found" for an article that is fine.
     *
     * Neither is reliable enough to see every time, which is exactly what
     * makes them expensive to chase: they follow how fast the database
     * answered and how long the transition took.
     *
     * With the id attached, a state belonging to another article is simply
     * ignored, and no ordering between the two pages can be wrong.
     */
    data class FullTextState(
        val articleId: String = "",
        val state: FullText = FullText.Absent,
    )

    private val _fullText = MutableStateFlow(FullTextState())
    val fullText: StateFlow<FullTextState> = _fullText.asStateFlow()

    /**
     * Fetches the readable article unless it is already stored.
     *
     * On demand, when the article is opened, rather than for every article of
     * every feed during sync: the reader is opened deliberately, and one page
     * fetched on a tap costs far less than the browser it replaces. A feed
     * with "fetch full articles" set still prefetches during sync, and this
     * finds that work already done.
     */
    fun loadFullText(id: String, link: String?, filesDir: File) {
        if (link.isNullOrBlank()) {
            _fullText.value = FullTextState(id, FullText.Failed)
            return
        }
        if (blobFullFile(id, filesDir).isFile) {
            _fullText.value = FullTextState(id, FullText.Ready)
            return
        }
        _fullText.value = FullTextState(id, FullText.Loading)
        ioScope.launch {
            val ok = parseFullArticleIfMissing(
                feedItem = ArticleIdWithLink(uuid = id, link = link),
                okHttpClient = fullTextClient,
                filesDir = filesDir,
            )
            // A fetch takes seconds, which is long enough for the reader to
            // have moved on twice. Reporting unconditionally would hand this
            // article's outcome to whichever one is open now.
            if (_fullText.value.articleId != id) return@launch
            _fullText.value =
                FullTextState(id, if (ok) FullText.Ready else FullText.Failed)
        }
    }

    /**
     * Reset when the reader moves off [id].
     *
     * Ignored when the state has already moved to another article, because
     * this is called from a page being disposed and that disposal can happen
     * after the next page has set its own. Without the check it clears the
     * state belonging to the article the reader is now looking at.
     */
    fun resetFullText(id: String) {
        if (_fullText.value.articleId != id) return
        _fullText.value = FullTextState()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val articleState = articleId.flatMapLatest {
        articleRepo.getArticleById(it)
    }.mapLatest {
        it?.let {
            ArticlePageState(
                article = it,
                source = sourcesRepo.loadFeedById(it.feedId),
                isBookmarked = it.bookmarked
            )
        } ?: ArticlePageState()
    }
        .stateIn(
            ioScope,
            SharingStarted.Lazily,
            ArticlePageState()
        )

    /** Holds an article at the top of the feed, or lets it go. */
    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            articleRepo.setPinned(id, pinned)
        }
    }

    fun bookmarkArticle(id: String, boolean: Boolean) {
        viewModelScope.launch {
            articleRepo.bookmarkArticle(id, boolean)
        }
    }
}

data class ArticlePageState(
    val article: Article? = null,
    val source: Feed? = null,
    val isBookmarked: Boolean = false,
)