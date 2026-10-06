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

/**
 * A source's name as a card shows it: the publication, without the strapline
 * a feed's title often carries after it.
 *
 * "GSMArena.com - Latest articles" is GSMArena.com, and "TheJournal.ie - Read,
 * Share and Shape the News" is TheJournal.ie; on a card's one line the rest
 * pushed the age off the end. Cut at the first separator - a spaced hyphen or
 * dash, a bar, or a colon - and the part before it kept. Unless that part is
 * only a section ("Tennis | The Guardian", "Blog – Hackaday", "Company | The
 * JetBrains Blog"), when the publication is the part after it.
 *
 * Only where a card names the source. The sources list and the source's own
 * screen keep its title as it is.
 */
fun shortSourceName(name: String): String {
    val trimmed = name.trim()
    val (at, sep) = SEPARATORS
        .mapNotNull { sep -> trimmed.indexOf(sep).takeIf { it > 0 }?.let { it to sep } }
        .minByOrNull { it.first }
        ?: return trimmed
    val before = trimmed.substring(0, at).trim()
    val after = trimmed.substring(at + sep.length).trim()
    val kept = if (before.lowercase() in SECTIONS && after.isNotEmpty()) shortSourceName(after) else before
    return if (kept.length < 2) trimmed else kept
}

private val SEPARATORS = listOf(" - ", " – ", " — ", " | ", ": ")

/** Words a feed puts first that name a section of a publication rather than the publication. */
private val SECTIONS = setOf(
    "blog", "company", "home", "news", "latest", "latest news", "top stories", "world", "world news",
    "business", "opinion", "politics", "tech", "technology", "science", "health", "culture",
    "entertainment", "books", "film", "movies", "music", "tv", "television", "travel", "lifestyle",
    "sport", "sports", "football", "soccer", "rugby", "cricket", "tennis", "golf", "motorsport",
    "ireland", "uk", "us", "europe",
)
