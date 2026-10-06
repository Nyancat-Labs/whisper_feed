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
package com.saulhdev.feeder.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.FeedItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/**
 * A day heading in the feed: "Today", "Yesterday", "Wednesday", and so on.
 *
 * The feed said only "Updated 47m ago" at the top, and a hundred cards down
 * there was nothing to say whether they were from this morning or last week.
 */
data class DayBreak(
    /** The article the heading goes above, by its place in the feed. */
    val index: Int,
    val day: LocalDate,
)

/**
 * Where the headings go, from each article's day.
 *
 * A heading stands above the first article of each day, and only the first:
 * the feed is newest first, but pinned articles and a held breaking story sit
 * at the top whatever their date, and a heading per change of day would name
 * a day twice. Pinned articles ([days] null) get none; they are at the top
 * because they are pinned, not because of when they were published.
 */
fun dayBreaks(days: List<LocalDate?>): List<DayBreak> {
    val named = HashSet<LocalDate>()
    return days.mapIndexedNotNull { index, day ->
        if (day != null && named.add(day)) DayBreak(index, day) else null
    }
}

/** [dayBreaks] for the feed as drawn, in the phone's own time zone. */
fun feedDayBreaks(articles: List<FeedItem>, zone: ZoneId = ZoneId.systemDefault()): List<DayBreak> =
    dayBreaks(articles.map { item ->
        if (item.article.pinned) null
        else Instant.ofEpochMilli(item.article.primarySortTime.toEpochMilliseconds()).atZone(zone).toLocalDate()
    })

/** A run of the feed under one heading, or under none at the very top. */
data class FeedSegment(val day: LocalDate?, val from: Int, val until: Int)

/** The feed cut at its headings, so each run can be drawn after its own. */
fun feedSegments(count: Int, breaks: List<DayBreak>): List<FeedSegment> {
    if (breaks.isEmpty()) return if (count == 0) emptyList() else listOf(FeedSegment(null, 0, count))
    val out = mutableListOf<FeedSegment>()
    if (breaks.first().index > 0) out += FeedSegment(null, 0, breaks.first().index)
    breaks.forEachIndexed { i, b ->
        val end = breaks.getOrNull(i + 1)?.index ?: count
        if (end > b.index) out += FeedSegment(b.day, b.index, end)
    }
    return out
}

/**
 * Whether the feed opens on a day heading, which is then where the update
 * time goes. Not when pinned stories come first: the heading is below them.
 */
fun firstSegmentHasHeading(segments: List<FeedSegment>): Boolean =
    segments.firstOrNull()?.let { it.day != null && it.from == 0 } == true

/**
 * Where the article at [position] is in the list that draws it: past the
 * header, and past every heading at or above it.
 */
fun feedListIndex(position: Int, breaks: List<DayBreak>): Int =
    position + FEED_ARTICLES_START + breaks.count { it.index <= position }

/** The heading's key: named, so read-on-scroll passes over it like the header. */
fun dayHeadingKey(day: LocalDate): String = "day-$day"

/** How a day is named, relative to [today]. */
enum class DayName { Today, Yesterday, Weekday, Date }

fun dayName(day: LocalDate, today: LocalDate): DayName = when {
    day == today -> DayName.Today
    day == today.minusDays(1) -> DayName.Yesterday
    day.isAfter(today.minusDays(7)) && !day.isAfter(today) -> DayName.Weekday
    else -> DayName.Date
}

/**
 * A day's heading, with room at its right for [trailing].
 *
 * The first heading carries "Updated 12m ago" there: on its own line under
 * the chips it left a row that was empty from a few words in, directly above
 * a heading that was empty from one word in.
 *
 * The first heading sits close under the chips and closer still to the story
 * it names. The 16dp above the others is the gap between one day and the
 * next; under the chips it stacked with their own padding and left the
 * heading floating halfway between the two, belonging to neither.
 */
@Composable
fun DayHeading(
    day: LocalDate,
    modifier: Modifier = Modifier,
    first: Boolean = false,
    trailing: (@Composable (Modifier) -> Unit)? = null,
) {
    // From the configuration, so a change of language redraws the headings.
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now()
    val text = when (dayName(day, today)) {
        DayName.Today -> stringResource(R.string.feed_day_today)
        DayName.Yesterday -> stringResource(R.string.feed_day_yesterday)
        DayName.Weekday -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        DayName.Date -> day.format(
            DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale)
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (first) 8.dp else 16.dp,
                bottom = if (first) 2.dp else 6.dp,
            ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .alignByBaseline()
                .semantics { heading() },
        )
        // Takes what is left and sets its words against the right edge, so a
        // longer message wraps within that space rather than under the day.
        trailing?.invoke(Modifier.weight(1f).alignByBaseline())
    }
}
