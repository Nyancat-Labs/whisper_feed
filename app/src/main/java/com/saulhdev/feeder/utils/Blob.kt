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
package com.saulhdev.feeder.utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The characters an article's id may contain.
 *
 * Every id is a `UUID.randomUUID().toString()` — generated locally, never
 * taken from a feed, and not the publisher's guid, which is a string somebody
 * else wrote. That was traced through the sync and the Google Reader client
 * before this was added, and it holds today.
 *
 * It is enforced anyway, because the file name is built by interpolation and
 * nothing at that line said the id was safe to interpolate. An id containing
 * `../` would put an article's cached text outside `filesDir` — into the
 * databases directory, or over the shared preferences — and the invariant
 * that prevents it lives in a different file from the code that depends on
 * it. That is exactly the arrangement that stops being true without anyone
 * noticing.
 *
 * Hyphens and word characters cover a UUID with room to spare. Anything else
 * is a mistake worth hearing about rather than a case worth handling.
 */
private val SAFE_ID = Regex("[A-Za-z0-9_-]+")

/**
 * The id, checked before it becomes part of a path.
 *
 * Throws rather than sanitising. Stripping the offending characters would
 * turn two different ids into one file, so an article could silently be
 * served another article's text — which is a worse bug than the one being
 * prevented, and one this app has already had for different reasons.
 */
internal fun safeArticleId(itemId: String): String = safeId(itemId)

private fun safeId(itemId: String): String {
    require(SAFE_ID.matches(itemId)) { "Unsafe article id for a file name" }
    return itemId
}

fun blobFile(itemId: String, filesDir: File): File =
    File(filesDir, "${safeId(itemId)}.txt.gz")

@Throws(IOException::class)
fun blobInputStream(itemId: String, filesDir: File): InputStream =
    GZIPInputStream(blobFile(itemId = itemId, filesDir = filesDir).inputStream())

@Throws(IOException::class)
fun blobOutputStream(itemId: String, filesDir: File): OutputStream =
    GZIPOutputStream(blobFile(itemId = itemId, filesDir = filesDir).outputStream())

/** Marks a full-article fetch that failed; see FullTextAttempts. */
fun blobFullFailedFile(itemId: String, filesDir: File): File =
    File(filesDir, "${safeId(itemId)}.full.failed")

/**
 * Everything stored for one article: its summary, its full text, and any
 * record of a full-text fetch that failed.
 *
 * Cleanup used to delete the summary alone. The full text, which is the
 * larger file by far and is written for every article when "Fetch full
 * articles for every feed" is on, stayed behind for every article ever
 * cleaned up.
 */
fun deleteArticleFiles(itemId: String, filesDir: File) {
    blobFile(itemId, filesDir).delete()
    blobFullFile(itemId, filesDir).delete()
    blobFullFailedFile(itemId, filesDir).delete()
    SavedImages.delete(itemId, filesDir)
}

private val ARTICLE_FILE = Regex("""([A-Za-z0-9_-]+)\.(txt\.gz|full\.html\.gz|full\.failed)""")

/**
 * Article files whose article is gone, from a directory listing.
 *
 * Only files named the way this app names them, and only those older than
 * [minAgeMs] before [nowMs]: a sync writes an article's file around the same
 * moment it inserts the row, and a sweep that read the ids between the two
 * would otherwise take a new article's text for litter.
 */
fun orphanArticleFiles(
    files: List<Pair<String, Long>>,
    knownIds: Set<String>,
    nowMs: Long,
    minAgeMs: Long,
): List<String> = files.mapNotNull { (name, modifiedMs) ->
    val id = ARTICLE_FILE.matchEntire(name)?.groupValues?.get(1) ?: return@mapNotNull null
    name.takeIf { id !in knownIds && nowMs - modifiedMs > minAgeMs }
}

fun blobFullFile(itemId: String, filesDir: File): File =
    File(filesDir, "${safeId(itemId)}.full.html.gz")

@Throws(IOException::class)
fun blobFullInputStream(itemId: String, filesDir: File): InputStream =
    GZIPInputStream(blobFullFile(itemId = itemId, filesDir = filesDir).inputStream())

@Throws(IOException::class)
fun blobFullOutputStream(itemId: String, filesDir: File): OutputStream =
    GZIPOutputStream(blobFullFile(itemId = itemId, filesDir = filesDir).outputStream())
