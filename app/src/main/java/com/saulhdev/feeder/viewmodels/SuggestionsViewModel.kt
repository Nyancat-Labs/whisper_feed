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
package com.saulhdev.feeder.viewmodels

import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.Suggestion
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus

class SuggestionsViewModel(
    db: NeoFeedDb,
    private val sources: SourcesRepository,
) : NeoViewModel() {

    private val ioScope = viewModelScope.plus(Dispatchers.IO)
    private val dao = db.suggestionDao()

    val suggestions: StateFlow<List<Suggestion>> =
        dao.getSuggestions().stateIn(ioScope, SharingStarted.Eagerly, emptyList())

    /**
     * Subscribes, and stops suggesting it.
     *
     * Removed rather than marked dismissed: it is not refused, it is taken up,
     * and there is nothing left to offer.
     */
    suspend fun accept(suggestion: Suggestion) {
        val url = sloppyLinkToStrictURL(suggestion.feedUrl)
        if (sources.findSourceByUrl(url) == null) {
            sources.insertSource(Feed(title = suggestion.title, url = url))
        }
        dao.remove(suggestion.host)
    }

    /** Kept rather than deleted, so the next pass does not ask again. */
    suspend fun dismiss(suggestion: Suggestion) = dao.dismiss(suggestion.host)
}
