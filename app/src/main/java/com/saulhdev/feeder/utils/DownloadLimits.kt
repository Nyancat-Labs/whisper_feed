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

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import java.io.IOException

/**
 * How much of a feed is read before it is refused. Real feeds are rarely more
 * than a megabyte; the ones that send every article whole reach a few.
 */
const val FEED_MAX_BYTES = 10L * 1024 * 1024

/** The same for an article's page, fetched for the full text. */
const val PAGE_MAX_BYTES = 5L * 1024 * 1024

/**
 * A download refused for what it is rather than lost to the network.
 *
 * [kind] is what the feed's history says: readable in a release build, where
 * an exception's class name is shortened to three letters.
 */
class DownloadRefused(val kind: String, message: String) : IOException(message)

/**
 * A picture, a sound or a film. A feed or a page is text, and nothing in this
 * app can read these; a feed address that has turned into a podcast episode
 * used to be read whole, all of it, on mobile data too, before failing.
 */
fun MediaType?.isMedia(): Boolean = this != null && type in MEDIA_TYPES

private val MEDIA_TYPES = setOf("audio", "video", "image")

/** Throws, before a byte is read, if the body is not text. */
fun ResponseBody.refuseMedia(what: String) {
    val type = contentType()
    if (type.isMedia()) throw DownloadRefused("not $what", "Not $what: ${type?.type}/${type?.subtype}")
}

/**
 * The whole body, if it is no larger than [limit].
 *
 * A body declared larger is refused before any of it is read, and one that
 * does not say is read no further than a byte past the limit. Either way the
 * rest is never downloaded: the connection closes with the response.
 */
fun ResponseBody.bytesAtMost(limit: Long): ByteArray {
    if (contentLength() > limit) throw tooLarge(limit)
    val buffer = Buffer()
    val source = source()
    while (buffer.size <= limit) {
        if (source.read(buffer, READ_STEP) == -1L) return buffer.readByteArray()
    }
    throw tooLarge(limit)
}

private const val READ_STEP = 64L * 1024

private fun tooLarge(limit: Long) =
    DownloadRefused("too large", "Larger than ${limit / (1024 * 1024)} MB")
