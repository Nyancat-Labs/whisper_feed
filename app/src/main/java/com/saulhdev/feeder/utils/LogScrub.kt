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
 * Takes addresses out of a diagnostics report before anybody else reads it.
 *
 * The report is written to be shared, and it carries the app's own log, where
 * warnings and errors survive a release build. Some of those printed a feed's
 * full address, which for some feeds carries a private token; the weather
 * request, whose query is the reader's location or the place they typed; and
 * exception messages quoting the sync server. Each is mended where it was
 * written. This is the net under all of them, for the next line nobody
 * notices in review.
 *
 * Deliberately narrow. What goes: anything with a scheme (`https://…`,
 * `content://…`), IP addresses, a latitude or longitude, and the few values
 * that are this reader's own, such as the sync server. What stays is what the
 * report is for: feed titles, class names, stack frames, times and counts. A
 * wider rule, "anything that looks like a domain", would have taken those
 * too, since `com.rometools.rome.io` looks like one.
 */
object LogScrub {

    const val ADDRESS = "<address>"
    const val IP = "<ip>"
    const val PLACE = "<place>"

    /** Shorter than this, a value of the reader's own is left alone. */
    private const val MIN_OWN = 3

    /** A scheme, and everything up to the next space, quote or bracket. */
    private val URL = Regex("""\b[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s"'<>]+""")

    /** Four numbers of up to three digits. A version like 130.0.6723.58 is not one. */
    private val IPV4 = Regex("""(?<![\w.])(?:\d{1,3}\.){3}\d{1,3}(?![\w.])""")

    /**
     * Eight groups, or fewer around a `::`. Never three with single colons:
     * the log's own timestamps are 21:49:11, and a rule that took those
     * would take the report with them.
     */
    private val IPV6 = Regex(
        "(?<![\\w:.])(?:" +
            "(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}" +
            "|(?:[0-9a-fA-F]{1,4}:){1,7}(?::[0-9a-fA-F]{1,4}){1,7}" +
            "|(?:[0-9a-fA-F]{1,4}:){1,7}:" +
            "|:(?::[0-9a-fA-F]{1,4}){1,7}" +
            ")(?![\\w:])"
    )

    /** A latitude or longitude by name, as a query parameter or a JSON field. */
    private val COORDINATE = Regex(
        """(?i)\b(lat|latitude|lon|lng|longitude)(["']?\s*[:=]\s*)-?\d{1,3}(?:\.\d+)?"""
    )

    /** Two decimals and a comma, the way a place is written: 51.5521, -9.2633. */
    private val COORDINATE_PAIR = Regex("""(?<![\w.])-?\d{1,3}\.\d{3,}\s*,\s*-?\d{1,3}\.\d{3,}(?![\w.])""")

    /**
     * @param own the reader's own values, each with what to show instead:
     *   the sync server's host, the account name, the glance row's place.
     *   Matched whole and regardless of case. Anything shorter than three
     *   characters is left alone, since a two-letter account name would take
     *   half the words in the log with it.
     */
    fun scrub(text: String, own: Map<String, String> = emptyMap()): String {
        var out = URL.replace(text, ADDRESS)
        own.entries
            .filter { it.key.trim().length >= MIN_OWN }
            // Longest first, so "Skibbereen, Cork, Ireland" goes whole before
            // "Skibbereen" can take its first word and leave the rest.
            .sortedByDescending { it.key.length }
            .forEach { (value, label) ->
                out = ownValue(value.trim()).replace(out, Regex.escapeReplacement(label))
            }
        out = COORDINATE.replace(out) { "${it.groupValues[1]}${it.groupValues[2]}$PLACE" }
        out = COORDINATE_PAIR.replace(out, PLACE)
        out = IPV4.replace(out, IP)
        out = IPV6.replace(out, IP)
        return out
    }

    private fun ownValue(value: String) =
        Regex("""(?<![\w.\-])${Regex.escape(value)}(?![\w\-])""", RegexOption.IGNORE_CASE)
}
