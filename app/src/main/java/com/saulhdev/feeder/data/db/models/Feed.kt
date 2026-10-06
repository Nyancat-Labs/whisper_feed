/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import java.net.URL
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * A feed that has never been fetched.
 *
 * Zero rather than a null column: `lastSync` is not null anywhere else and
 * making it nullable would put a `?` on every read for the sake of one case
 * that has a perfectly good value already.
 */
val NEVER_SYNCED: Instant = Instant.fromEpochMilliseconds(0)

@Entity(
    tableName = "Feeds",
    indices = [
        Index(value = ["url"], unique = true),
        Index(value = ["id", "url", "title"], unique = true)
    ]
)
data class Feed(
    @PrimaryKey(autoGenerate = true)
    val id: Long = ID_UNSET,
    val title: String = "",
    val description: String = "",
    val url: URL = sloppyLinkToStrictURL(""),
    val feedImage: URL = sloppyLinkToStrictURL(""),
    /**
     * When Whisper last fetched this feed successfully, or [NEVER_SYNCED].
     *
     * The default used to be `Clock.System.now()`, which is the moment the
     * *row* was built rather than the moment a sync succeeded — so a feed that
     * had never once been fetched was indistinguishable from one fetched the
     * instant it was added. Importing an OPML wrote the same millisecond to
     * every row in the file, and the feeds in it that could not be fetched at
     * all then sat looking perfectly healthy: the "Not updating" badge only
     * appears after three days without a sync, and their clock had been
     * started for them.
     *
     * One import produced six such feeds out of twenty on a test device, and
     * the only reason anybody noticed was six rows carrying an identical
     * timestamp to the millisecond in a diagnostics report. Starting at zero
     * makes the distinction the database was already supposed to be making.
     */
    @ColumnInfo(typeAffinity = ColumnInfo.INTEGER)
    val lastSync: Instant = NEVER_SYNCED,
    val alternateId: Boolean = false,
    val fullTextByDefault: Boolean = false,
    val tag: String = "",
    val currentlySyncing: Boolean = false,
    val isEnabled: Boolean = true,

    /**
     * When the reader removed this source, or 0 while they still have it.
     *
     * A removed source is normally deleted outright. It is kept, marked, and
     * hidden from every listing only when it still holds articles the reader
     * bookmarked or pinned — because the articles carry a foreign key onto
     * this row with `onDelete = CASCADE`, so deleting it is what destroyed
     * them. A bookmark is meant to last until it is taken back, and
     * unsubscribing from a site is not taking it back.
     *
     * The row is reaped as soon as the last saved article from it is
     * un-bookmarked, so this is not a graveyard that fills up. Re-adding the
     * same address resurrects it instead of creating a second one, which also
     * means the reader gets their saved articles back.
     */
    @ColumnInfo(defaultValue = "0")
    val removedAt: Long = 0L,

    /**
     * How many syncs in a row have failed for this feed.
     *
     * A failure used to be logged and forgotten: the syncing flag was cleared,
     * `lastSync` was left alone, and nothing anywhere said the feed had
     * stopped working. A reader could only find out by noticing that a source
     * had gone quiet — which is indistinguishable from a source that has
     * nothing to say.
     *
     * Reset to zero by any success, so one bad afternoon on somebody's server
     * does not accumulate into a claim that their feed is dead.
     */
    @ColumnInfo(defaultValue = "0")
    val consecutiveFailures: Int = 0,

    /** When the run of failures started, so "since Tuesday" can be said. */
    @ColumnInfo(defaultValue = "0")
    val failingSince: Long = 0L,
) {
    /**
     * The feed's categories.
     *
     * `tag` is a comma-separated list, not a single value — the source editor
     * writes one and the tag list splits one. Several places treated it as a
     * single value anyway and quietly failed on any feed with more than one
     * category, so the split lives here now and everything reads it from one
     * place.
     */
    val tags: List<String>
        get() = tag.split(",").map(String::trim).filter(String::isNotEmpty)
}