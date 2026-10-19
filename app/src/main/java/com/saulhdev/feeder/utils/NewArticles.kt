/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
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
 */

package com.saulhdev.feeder.utils

import com.saulhdev.feeder.data.db.models.FeedItem

/**
 * How many of [articles] are new: arrived after [seenAtMs], when the reader
 * last left the feed, and not yet read.
 *
 * Asked for by a reader on 16 October 2026 as "how many new articles a sync
 * brought". Counted since the reader last looked rather than per sync: with
 * syncs every couple of hours in the background, the last one alone could say
 * "2 new" over thirty arrivals. Counted from the list on screen, so a tab, a
 * source kept out of All, blocked words and one source narrowed to all count
 * only what is there to be seen. Read articles are not new, whenever they
 * came. Nothing before the first look.
 */
fun newSinceSeen(articles: List<FeedItem>, seenAtMs: Long): Int =
    if (seenAtMs <= 0L) 0
    else articles.count { it.article.readAt == 0L && it.article.firstSyncedTime.toEpochMilliseconds() > seenAtMs }
