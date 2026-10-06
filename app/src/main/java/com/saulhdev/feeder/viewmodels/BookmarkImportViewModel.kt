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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.isFeedHost
import com.saulhdev.feeder.manager.bookmarks.BookmarkFile
import com.saulhdev.feeder.manager.bookmarks.BookmarkFolder
import com.saulhdev.feeder.manager.bookmarks.DiscoveredFeed
import com.saulhdev.feeder.manager.bookmarks.FeedDiscovery
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/** Where the import has got to. */
sealed interface ImportStage {
    data object PickFile : ImportStage
    data class ChooseFolders(val folders: List<BookmarkFolder>) : ImportStage
    data class Scanning(val done: Int, val total: Int) : ImportStage
    data class Review(val found: List<DiscoveredFeed>, val category: Map<String, String>) :
        ImportStage

    data object Empty : ImportStage
}

class BookmarkImportViewModel(
    private val sources: SourcesRepository,
    private val discovery: FeedDiscovery,
    private val articles: ArticleRepository,
) : NeoViewModel() {

    private val _stage = MutableStateFlow<ImportStage>(ImportStage.PickFile)
    val stage: StateFlow<ImportStage> = _stage.asStateFlow()

    /** Folders the reader has ticked. */
    private val _selectedFolders = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolders: StateFlow<Set<String>> = _selectedFolders.asStateFlow()

    /** Feeds the reader has ticked on the review screen. */
    private val _selectedFeeds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFeeds: StateFlow<Set<String>> = _selectedFeeds.asStateFlow()

    private var folders: List<BookmarkFolder> = emptyList()

    fun load(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val html = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
            }.getOrNull()

            folders = html?.let(BookmarkFile::folders).orEmpty()
            _stage.value =
                if (folders.isEmpty()) ImportStage.Empty
                else ImportStage.ChooseFolders(folders)
        }
    }

    fun toggleFolder(name: String) {
        _selectedFolders.value = _selectedFolders.value.toMutableSet().apply {
            if (!add(name)) remove(name)
        }
    }

    fun toggleFeed(feedUrl: String) {
        _selectedFeeds.value = _selectedFeeds.value.toMutableSet().apply {
            if (!add(feedUrl)) remove(feedUrl)
        }
    }

    /**
     * Scans the chosen folders.
     *
     * Grouped by site before anything is fetched, which is the single change
     * that makes this affordable: fifteen bookmarks on one publication are one
     * site to ask. Already-subscribed sites are dropped before the scan rather
     * than after, so the reader does not pay for a request whose answer is
     * "you already have this".
     */
    fun scan() {
        val chosen = folders.filter { it.name in _selectedFolders.value }
        if (chosen.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _stage.value = ImportStage.Scanning(0, 0)

            // A hosted feed's address names the service, not the publisher, so
            // a FeedBurner subscription to Android Authority registered as
            // "feeds.feedburner.com" and left androidauthority.com looking
            // unfollowed — which this then helpfully offered to add again.
            // The articles know where they live; see isFeedHost.
            val subscribed = sources.getAllSources()
                .flatMap { feed ->
                    val own = runCatching { feed.url.host }.getOrNull()
                    if (own != null && !isFeedHost(own)) return@flatMap listOf(own)
                    val link = articles.publisherLink(feed.id)
                    listOfNotNull(own, runCatching { URL(link ?: "").host }.getOrNull())
                }
                .map { it.lowercase().removePrefix("www.") }
                .toSet()

            // Site to folder, so a feed found can be filed where its bookmark
            // was. The folder names are the categories — that is most of the
            // value of importing by folder rather than by file.
            val siteFolder = LinkedHashMap<String, String>()
            chosen.forEach { folder ->
                folder.urls.forEach { url ->
                    val host = runCatching { URL(url).host?.lowercase()?.removePrefix("www.") }
                        .getOrNull() ?: return@forEach
                    if (host in subscribed) return@forEach
                    siteFolder.putIfAbsent(host, folder.name)
                }
            }

            val found = discovery.discoverAll(siteFolder.keys.toList()) { done, total ->
                _stage.value = ImportStage.Scanning(done, total)
            }

            // Everything found is ticked: the reader chose these folders
            // because they read those sites, so the default should be yes.
            _selectedFeeds.value = found.map { it.feedUrl }.toSet()
            _stage.value = ImportStage.Review(
                found = found,
                category = found.associate { it.feedUrl to siteFolder[it.site].orEmpty() },
            )
        }
    }

    /** Subscribes to what is ticked, filed under the folder it came from. */
    suspend fun addSelected(): Int = withContext(Dispatchers.IO) {
        val review = _stage.value as? ImportStage.Review ?: return@withContext 0
        // One batch. The duplicate check is still done and still re-checked
        // rather than trusted from the scan — Feeds.url is uniquely indexed
        // with REPLACE, so an unnoticed duplicate takes the original's
        // articles down with it — but it now happens once against a set built
        // from a single read, instead of a full scan per bookmark. A browser
        // export is the largest thing this app imports, which made it the
        // worst case for the shape this replaces.
        sources.insertSources(
            review.found
                .filter { it.feedUrl in _selectedFeeds.value }
                .mapNotNull { feed ->
                    val url = runCatching { sloppyLinkToStrictURL(feed.feedUrl) }.getOrNull()
                        ?: return@mapNotNull null
                    Feed(
                        title = feed.title,
                        url = url,
                        tag = review.category[feed.feedUrl].orEmpty(),
                    )
                }
        )
    }

    fun restart() {
        folders = emptyList()
        _selectedFolders.value = emptySet()
        _selectedFeeds.value = emptySet()
        _stage.value = ImportStage.PickFile
    }
}
