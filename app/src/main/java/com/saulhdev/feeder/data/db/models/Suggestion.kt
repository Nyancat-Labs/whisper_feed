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
package com.saulhdev.feeder.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A site the reader keeps following links to, which publishes a feed.
 *
 * Never shown in the feed itself. A suggestion lives on a screen the reader
 * goes to, because the moment articles from sites they did not choose start
 * appearing among the ones they did, the feed stops being theirs — which is
 * the entire difference between this and the thing it replaces.
 */
@Entity(
    tableName = "Suggestion",
    indices = [Index(value = ["host"], unique = true)],
)
data class Suggestion(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** The site, as it will be compared against the subscription list. */
    val host: String = "",

    /** Its feed, found by the same autodiscovery that adding one by hand uses. */
    val feedUrl: String = "",

    /** What the feed calls itself, once it has been read. */
    val title: String = "",

    /**
     * How many separately-read articles linked here.
     *
     * This is the evidence, and it is shown to the reader rather than kept
     * behind the suggestion: "six articles you read linked to this" is both
     * the reason and the whole of it. A suggestion that cannot say why it is
     * there is the kind this app exists not to make.
     */
    val mentions: Int = 0,

    /**
     * Which mechanism found this, because they are different claims.
     *
     * [FROM_LINKS] means articles the reader read pointed here. [FROM_LIBRARY]
     * means this sits in a bundled collection alongside sources they already
     * follow. Showing both under one wording would make the weaker claim
     * borrow the stronger one's evidence — "six articles you read linked to
     * this" is a fact about the reader, and "also in United Kingdom" is a fact
     * about a file that ships with the app.
     */
    @ColumnInfo(defaultValue = FROM_LINKS)
    val kind: String = FROM_LINKS,

    /**
     * What the reason refers to — the pack's name, for a library suggestion.
     *
     * Empty for a link suggestion, whose reason is [mentions] alone.
     */
    @ColumnInfo(defaultValue = "")
    val context: String = "",

    val foundAt: Long = System.currentTimeMillis(),

    /**
     * When the reader said no.
     *
     * Kept rather than deleted, so the next pass does not cheerfully suggest
     * the same site again a week later. Saying no once should mean no.
     */
    val dismissedAt: Long = 0L,
)

/** Found because articles the reader read linked to it. */
const val FROM_LINKS = "links"

/** Found because it sits beside sources the reader already follows. */
const val FROM_LIBRARY = "library"
