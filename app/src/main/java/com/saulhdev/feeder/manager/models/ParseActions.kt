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

import com.saulhdev.feeder.manager.backup.BackupStore
import com.saulhdev.feeder.manager.backup.SavedArticlesBackup
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
import com.saulhdev.feeder.manager.sync.requestFeedSync
import com.saulhdev.feeder.utils.extensions.ToastMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

        // The same file the daily backup writes; see SavedArticlesBackup.
        val jsonString = SavedArticlesBackup.encode(
            bookmarkedItems,
            Clock.System.now().toEpochMilliseconds(),
        )
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

        val jsonString = SAFFile(context, uri).read()
            ?: return@withContext Pair(0, 0)

        // Through the backup's own restore. This wrote each article by its
        // id, so one already on this phone under another id - every article
        // a sync had fetched since - was added a second time; and an article
        // whose source was not subscribed was dropped.
        val backup: BackupStore by inject(BackupStore::class.java)
        val container = SavedArticlesBackup.decode(jsonString)
        val restored = (backup.restoreSaved(uri) as? BackupStore.Result.SavedRestored)?.count ?: 0

        Log.d("BOOKMARKS", "Imported $restored of ${container.articles.size} Bookmarks")
        Pair(restored, container.articles.size - restored)
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