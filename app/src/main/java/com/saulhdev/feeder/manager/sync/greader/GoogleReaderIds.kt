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
package com.saulhdev.feeder.manager.sync.greader

/**
 * The Google Reader protocol's identifiers, which are three shapes of the same
 * thing and the usual source of bugs in a client.
 *
 * An item arrives as a long form in stream contents:
 *
 *     tag:google.com,2005:reader/item/00000000cafebabe
 *
 * and as a short decimal in the ids endpoint:
 *
 *     3405691582
 *
 * They are the same item. The hex is sixteen digits, unsigned, and larger than
 * `Long.MAX_VALUE` for half the range — parsing it with `toLong()` throws on
 * exactly the ids whose top bit is set, which is why a naive client works for
 * months and then falls over on one article. `toULong(16).toLong()` keeps the
 * bit pattern, which is what the protocol means, and the decimal form is the
 * same number printed signed.
 *
 * Streams, tags and feeds have their own prefixes, and the protocol is
 * inconsistent about when a feed id includes the `feed/` prefix, so it is
 * added and stripped in exactly one place: here.
 */
object GoogleReaderIds {

    const val STREAM_READING_LIST = "user/-/state/com.google/reading-list"
    const val STREAM_STARRED = "user/-/state/com.google/starred"
    const val TAG_READ = "user/-/state/com.google/read"
    const val TAG_STARRED = "user/-/state/com.google/starred"

    private const val ITEM_PREFIX = "tag:google.com,2005:reader/item/"

    /**
     * The item id as the protocol's decimal form, whichever form arrived.
     *
     * Returns null rather than throwing: a server that sends something
     * unexpected should cost one article, not the whole sync.
     */
    fun itemId(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val prefixed = trimmed.startsWith(ITEM_PREFIX)
        val hex = trimmed.removePrefix(ITEM_PREFIX)
        // Hex only with the prefix, or with a letter in it. It used to be any
        // sixteen characters too, and FreshRSS's decimal ids are timestamps
        // in microseconds: sixteen digits. Every id in the unread list was
        // read as hex, matched nothing, and each sync marked every matched
        // article read.
        val looksHex = prefixed || (hex.length == 16 && hex.any { it in 'a'..'f' || it in 'A'..'F' })
        return if (looksHex) {
            runCatching { hex.toULong(16).toLong().toString() }.getOrNull()
        } else {
            // Already decimal. Round-tripped so a malformed one is rejected
            // here rather than at the point it is used.
            runCatching { trimmed.toLong().toString() }.getOrNull()
        }
    }

    /** The long form, for endpoints that will not take the short one. */
    fun longItemId(decimal: String): String? =
        runCatching {
            ITEM_PREFIX + decimal.toLong().toULong().toString(16).padStart(16, '0')
        }.getOrNull()

    /** A feed's stream id, with the prefix the protocol wants exactly once. */
    fun feedStream(url: String): String =
        if (url.startsWith("feed/")) url else "feed/$url"

    /** The feed URL back out of a stream id. */
    fun feedUrl(stream: String): String = stream.removePrefix("feed/")

    /** A folder's stream id. Folders are labels in this protocol. */
    fun labelStream(name: String): String =
        if (name.startsWith("user/-/label/")) name else "user/-/label/$name"

    /** The folder name back out, for the category it becomes locally. */
    fun labelName(stream: String): String = stream.substringAfterLast("user/-/label/")
}
