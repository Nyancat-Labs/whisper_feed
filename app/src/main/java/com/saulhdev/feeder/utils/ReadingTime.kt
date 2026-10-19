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

import org.jsoup.Jsoup
import kotlin.math.roundToInt

/**
 * "4 min", from the best text Whisper has, counted once when it is stored.
 *
 * Most pages are never downloaded, so the feed's own text is used when it is
 * the whole article, and nothing at all when it is a teaser: forty words
 * would say "1 min" for a three-thousand-word feature.
 */
object ReadingTime {

    /** An unhurried adult reading on a screen. */
    const val WORDS_PER_MINUTE = 230

    /** Below this a feed's text is taken for a teaser, not the article. */
    const val WHOLE_ARTICLE_WORDS = 250

    /** How a feed says "the rest is on the site". */
    private val TEASER_END = Regex(
        """(…|\.\.\.|\[…]|\[\.\.\.]|read more|continue reading|read the full (story|article))\W*$""",
        RegexOption.IGNORE_CASE,
    )

    fun words(text: String): Int =
        text.split(Regex("\\s+")).count { word -> word.any { it.isLetterOrDigit() } }

    fun minutes(words: Int): Int =
        if (words <= 0) 0 else maxOf(1, (words.toFloat() / WORDS_PER_MINUTE).roundToInt())

    private fun plain(html: String): String = Jsoup.parse(html).text()

    /** From a downloaded page, which is the article by definition. */
    fun fromPage(html: String): Int = minutes(words(plain(html)))

    /** From a feed's own text, or 0 when it looks like a teaser. */
    fun fromFeed(html: String): Int {
        val text = plain(html).trim()
        if (TEASER_END.containsMatchIn(text)) return 0
        val count = words(text)
        return if (count < WHOLE_ARTICLE_WORDS) 0 else minutes(count)
    }
}
