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

import com.saulhdev.feeder.utils.SyncLog
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.SAFFile
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.entity.BookmarksExportContainer
import com.saulhdev.feeder.data.entity.toArticle
import com.saulhdev.feeder.data.entity.toBookmarkedExport
import com.saulhdev.feeder.manager.sync.requestFeedSync
import com.saulhdev.feeder.utils.extensions.ToastMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.java.KoinJavaComponent.inject
import kotlin.system.measureTimeMillis
import kotlin.time.Clock

/**
 * Exports OPML on a background thread
 */
suspend fun ContentResolver.exportOpml(uri: Uri, tagsFeedMap: Map<String, List<Feed>>) =
    withContext(Dispatchers.IO) {
        try {
            val time = measureTimeMillis {
                takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                openOutputStream(uri)?.let {
                    writeOutputStream(
                        it,
                        tagsFeedMap
                    )
                }
            }
            Log.d("OPML", "Exported OPML in $time ms")
        } catch (e: Throwable) {
            Log.e("OPML", "Failed to export OPML", e)
            val toastMaker: ToastMaker by inject(ToastMaker::class.java)
            toastMaker.makeToast(R.string.failed_to_export_OPML)
            (e.localizedMessage ?: e.message)?.let { message ->
                toastMaker.makeToast(message)
            }
        }
    }

/**
 * Imports OPML on a background thread
 */
suspend fun ContentResolver.importOpml(uri: Uri) = withContext(Dispatchers.IO) {
    try {
        val time = measureTimeMillis {
            val parser = OPMLParser(SourceToRoom())

            openInputStream(uri).use {
                it?.let { stream ->
                    parser.parseInputStream(stream)
                }
            }
            requestFeedSync(origin = SyncLog.ORIGIN_IMPORT)
        }
        Log.d("OPML", "Imported OPML in $time ms")
    } catch (e: Throwable) {
        Log.e("OPML", "Failed to import OPML", e)
        val toastMaker: ToastMaker by inject(ToastMaker::class.java)
        toastMaker.makeToast(R.string.failed_to_import_OPML)
        (e.localizedMessage ?: e.message)?.let { message ->
            toastMaker.makeToast(message)
        }
    }
}

/**
 * Exports bookmarked articles to JSON format
 */
suspend fun ContentResolver.exportBookmarks(
    context: Context,
    uri: Uri,
    bookmarkedItems: List<FeedItem>
) = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        val exportData = BookmarksExportContainer(
            version = 1,
            exportDate = Clock.System.now().toEpochMilliseconds(),
            articles = bookmarkedItems.map { it.toBookmarkedExport() }
        )

        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

        val jsonString = json.encodeToString(exportData)
        SAFFile.write(context, uri, jsonString)
        Log.d("BOOKMARKS", "Exported Bookmarks successfully")
    } catch (e: Throwable) {
        Log.e("BOOKMARKS", "Failed to export Bookmarks", e)
        val toastMaker: ToastMaker by inject(ToastMaker::class.java)
        toastMaker.makeToast(R.string.failed_to_import_OPML)
        (e.localizedMessage ?: e.message)?.let { message ->
            toastMaker.makeToast(message)
        }
    }
}

/**
 * Imports bookmarked articles from JSON format
 * Reconnects articles to feeds using feed URLs and marks them as bookmarked
 */
suspend fun ContentResolver.importBookmarks(
    context: Context,
    uri: Uri,
): Pair<Int, Int> = withContext(Dispatchers.IO) {
    try {
        takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )

        var successCount = 0
        var failedCount = 0
        val jsonString = SAFFile(context, uri).read()
            ?: return@withContext Pair(successCount, failedCount)
        val bookmarksHandler = BookmarksToRoom()
        val sourceHandler = SourceToRoom()

        val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

        val importData = json.decodeFromString<BookmarksExportContainer>(jsonString)

        importData.articles.forEach { exportedArticle ->
            try {
                val feed = sourceHandler.getItem(exportedArticle.feedUrl)

                if (feed != null) {
                    val article = exportedArticle.toArticle(feed.id)

                    bookmarksHandler.saveItem(article)
                    successCount++
                } else {
                    // Feed doesn't exist (could optionally create it)
                    failedCount++
                }
            } catch (e: Exception) {
                failedCount++
            }
        }

        Log.d("BOOKMARKS", "Imported $successCount Bookmarks successfully; failed on $failedCount")
        Pair(successCount, failedCount)
    } catch (e: Throwable) {
        Log.e("BOOKMARKS", "Failed to export Bookmarks", e)
        val toastMaker: ToastMaker by inject(ToastMaker::class.java)
        toastMaker.makeToast(R.string.failed_to_import_OPML)
        (e.localizedMessage ?: e.message)?.let { message ->
            toastMaker.makeToast(message)
        }
        Pair(0, 0)
    }
}