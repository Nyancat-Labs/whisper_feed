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
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.entity.SourceEditViewState
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

@OptIn(ExperimentalCoroutinesApi::class)
class SourceEditViewModel : NeoViewModel() {
    private val repository: SourcesRepository by inject(SourcesRepository::class.java)
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    private val _feedId: MutableSharedFlow<Long> = MutableSharedFlow(replay = 1)

    fun setFeedId(value: Long) {
        _feedId.tryEmit(value)
    }

    suspend fun loadFeed(feedId: Long): Feed? = repository.loadFeedById(feedId)

    private val feed = _feedId.mapLatest {
        repository.loadFeedById(it) ?: Feed()
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        Feed()
    )

    /**
     * Saves the edits and returns.
     *
     * Not suspend, and not run on the caller's scope. Saving used to be
     * awaited by the Save button before it dismissed the screen, and the wait
     * is not the database write — it is what follows: enqueuing a WorkManager
     * job writes to WorkManager's own database, and a filter change deletes
     * every article for the source first. Pressing Save did nothing visible
     * for a second or two, which reads as a button that has not worked.
     *
     * It also ran on `rememberCoroutineScope()`, so navigating away could
     * cancel the save halfway through — the screen leaving is exactly when
     * this must not stop.
     */
    /**
     * Edits that could not be saved, with the name of the source they were for.
     *
     * A SharedFlow rather than state: this is an event, and an event replayed
     * into a screen that has just been reopened would announce a failure the
     * reader already saw and already dealt with.
     */
    private val _saveFailed = MutableSharedFlow<String>()
    val saveFailed: SharedFlow<String> = _saveFailed.asSharedFlow()


    fun updateFeed(state: SourceEditViewState) {
        viewModelScope.launch { applyUpdate(state) }
    }

    private suspend fun applyUpdate(state: SourceEditViewState) {
        val feedId = _feedId.replayCache.firstOrNull() ?: -1L
        val currentFeed = repository.loadFeedById(feedId) ?: return
        val needsResync = currentFeed.fullTextByDefault != state.fullTextByDefault
                || currentFeed.isEnabled != state.isEnabled

        val saved = repository.updateSource(
            feed = currentFeed.copy(
                title = state.title,
                url = sloppyLinkToStrictURL(state.url),
                tag = state.tag,
                fullTextByDefault = state.fullTextByDefault,
                isEnabled = state.isEnabled,
            ),
            resync = needsResync
        )
        // updateSource refuses rather than crashes when another subscription
        // already holds the address, and this threw that answer away: the
        // sheet closed, nothing was written, and nothing said so. A save that
        // silently does nothing is worse than the crash it replaced, because
        // the crash at least told you something had gone wrong.
        //
        // Reported rather than returned, because saving is deliberately
        // fire-and-forget — the screen dismisses before the write finishes, so
        // there is nobody left on it to hand a result to. The sources list
        // outlives the editor and has the snackbar.
        if (!saved) _saveFailed.emit(state.title.ifBlank { state.url })

    }

    fun deleteFeed(feedId: Long) {
        viewModelScope.launch {
            repository.deleteFeed(feedId)
        }
    }

    val viewState = feed.map { feed: Feed ->
        SourceEditViewState(
            title = feed.title,
            url = feed.url.toString(),
            tag = feed.tag,
            fullTextByDefault = feed.fullTextByDefault,
            isEnabled = feed.isEnabled,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        SourceEditViewState()
    )
}