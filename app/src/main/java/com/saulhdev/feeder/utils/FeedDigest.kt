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

import android.content.Context
import androidx.core.content.edit
import java.security.MessageDigest

/**
 * A fingerprint of the last download of each feed that was processed.
 *
 * About a dozen feeds never say "nothing new": they send the whole feed every
 * time, Gear Patrol 297 KB and Inverse 179 KB of it, with nothing new in it.
 * The download cannot be avoided - the server has to be asked - but reading
 * it can. Parsing a feed is what fills the heap during a sync, and a download
 * identical to the last one parses into exactly the articles already stored.
 *
 * One SharedPreferences key per feed, for the reason FeedHistory gives.
 */
object FeedDigest {
    private const val FILE = "feed_digest"

    fun read(context: Context, feedId: Long): String? = runCatching {
        prefs(context).getString(feedId.toString(), null)
    }.getOrNull()

    fun write(context: Context, feedId: Long, digest: String) {
        runCatching { prefs(context).edit { putString(feedId.toString(), digest) } }
    }

    /** Drops feeds that no longer exist. */
    fun keepOnly(context: Context, feedIds: Set<Long>) {
        runCatching {
            val prefs = prefs(context)
            val stale = prefs.all.keys.filter { it.toLongOrNull() !in feedIds }
            if (stale.isNotEmpty()) prefs.edit { stale.forEach(::remove) }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * The fingerprint of one download, as it would be processed.
 *
 * [settings] goes in with the bytes, because the same download processed
 * under different settings is a different result: raise the number of
 * articles kept per feed, or take a word off the blocked list, and articles
 * that were left out last time belong in this time. See [parseSettingsKey].
 */
fun feedDigest(body: ByteArray, settings: String): String {
    val sha = MessageDigest.getInstance("SHA-256")
    sha.update(body)
    sha.update(0)
    sha.update(settings.toByteArray())
    return sha.digest().joinToString("") { "%02x".format(it) }
}

/** Everything that decides which of a feed's articles are kept. */
fun parseSettingsKey(maxItems: Int, days: Int, blockedWords: Collection<String>): String =
    "$maxItems|$days|" + blockedWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }.sorted()
        .joinToString("\u0000")

/**
 * When each source's site was last asked for an icon it did not have.
 *
 * A source whose feed and site both offer none would otherwise be asked on
 * every sync, for ever: its home page, then two conventional file names.
 */
object IconLookup {
    private const val FILE = "icon_lookup"

    /** A week: a site that had no icon on Monday rarely has one by Tuesday. */
    const val RETRY_AFTER_MS = 7 * 24 * 60 * 60_000L

    fun due(context: Context, feedId: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        iconLookupDue(
            runCatching { prefs(context).getLong(feedId.toString(), 0L) }.getOrDefault(0L),
            nowMs,
        )

    fun tried(context: Context, feedId: Long, nowMs: Long = System.currentTimeMillis()) {
        runCatching { prefs(context).edit { putLong(feedId.toString(), nowMs) } }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Whether to ask again, given the last time a lookup was tried: 0 for never,
 * which is always long enough ago.
 */
fun iconLookupDue(lastTriedMs: Long, nowMs: Long): Boolean =
    nowMs - lastTriedMs >= IconLookup.RETRY_AFTER_MS
