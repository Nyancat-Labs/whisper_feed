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
package com.saulhdev.feeder.manager.backup

import com.saulhdev.feeder.data.db.dao.FeedArticleDao
import com.saulhdev.feeder.data.db.dao.FeedSourceDao
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.entity.BookmarksExportContainer
import com.saulhdev.feeder.data.entity.toArticle
import com.saulhdev.feeder.data.entity.toBookmarkedExport
import com.saulhdev.feeder.utils.isSameFeedUrl
import com.saulhdev.feeder.utils.preferringHttps
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import kotlinx.serialization.json.Json

/**
 * Saved articles, as the third file of a backup.
 *
 * The subscriptions and the settings were backed up and the Bookmarks were
 * not, so moving between Play, F-Droid and GitHub - which means uninstalling,
 * since each is signed with a different key - lost every one of them.
 *
 * The same file as Sources → Export bookmarks writes, so either can be read
 * by the other. The article as the feed gave it, not the full text or the
 * pictures kept with it: those are fetched again once it is restored, and
 * leaving them out keeps the file to text.
 */
object SavedArticlesBackup {

    const val FILE_NAME = "whisper-saved.json"

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(items: List<FeedItem>, at: Long): String = json.encodeToString(
        BookmarksExportContainer(
            exportDate = at,
            articles = items.map { it.toBookmarkedExport() },
        )
    )

    fun decode(text: String): BookmarksExportContainer = json.decodeFromString(text)

    /** What [restore] needs of the database, so it can be tested without one. */
    interface Store {
        /** The source at this address, or a hidden one made to hold its saved articles. */
        suspend fun sourceFor(url: String, title: String?): Long?
        suspend fun article(guid: String, feedId: Long): Article?
        suspend fun write(article: Article)
    }

    /**
     * Puts the saved articles back, and returns how many were not saved here
     * before.
     *
     * Matched on source and guid, the way a sync matches: an article this
     * phone already has is marked saved in place rather than added a second
     * time, which is what restoring onto a phone that has synced since does.
     * One the phone has never seen is added as it was saved.
     */
    suspend fun restore(container: BookmarksExportContainer, store: Store): Int {
        var restored = 0
        container.articles.forEach { saved ->
            val feedId = store.sourceFor(saved.feedUrl, saved.feedTitle) ?: return@forEach
            val here = store.article(saved.guid, feedId)
            when {
                here == null -> {
                    store.write(saved.toArticle(feedId))
                    restored++
                }
                !here.bookmarked -> {
                    store.write(here.copy(bookmarked = true, pinned = here.pinned || saved.pinned))
                    restored++
                }
            }
        }
        return restored
    }
}

/** [SavedArticlesBackup.Store] on the app's own database. */
class RoomSavedStore(
    private val feeds: FeedSourceDao,
    private val articles: FeedArticleDao,
    private val now: () -> Long = System::currentTimeMillis,
) : SavedArticlesBackup.Store {

    /**
     * A source restored with the subscriptions is found here. One that was
     * removed while it held saved articles is not in the subscriptions file,
     * so it is made again the way it was kept: removed, hidden from every
     * list, there only for its saved articles. Adding the address again
     * brings it back with them, as it does on the phone they came from.
     */
    override suspend fun sourceFor(url: String, title: String?): Long? {
        val address = sloppyLinkToStrictURLNoThrows(url).preferringHttps()
        if (address.host.isNullOrBlank()) return null
        feeds.getFeedByURL(address)?.let { return it.id }
        feeds.loadAllFeeds().firstOrNull { isSameFeedUrl(it.url, address) }?.let { return it.id }
        feeds.findRemovedByUrl(address)?.let { return it.id }
        return feeds.insert(
            Feed(
                title = title?.takeIf { it.isNotBlank() } ?: address.host,
                url = address,
                removedAt = now(),
            )
        )
    }

    override suspend fun article(guid: String, feedId: Long): Article? =
        articles.loadArticle(guid, feedId)

    override suspend fun write(article: Article) = articles.upsert(article)
}
