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
package com.saulhdev.feeder.ui.pages

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.DayCount
import com.saulhdev.feeder.data.db.models.HourCount
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.STATS_WINDOW_DAYS
import com.saulhdev.feeder.viewmodels.SourceShare
import com.saulhdev.feeder.viewmodels.StatisticsState
import com.saulhdev.feeder.viewmodels.StatisticsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What the reader's own reading looks like, over the last month.
 *
 * Every figure comes from two columns the app already keeps for its own
 * purposes — when an article reached the screen, and when one was opened. No
 * new collection was added to build this, nothing here is sent anywhere, and
 * turning the screen off would not make the app record less. It is a view onto
 * data that was already there, which is the only kind of statistics screen a
 * reader who chose this app would want.
 *
 * Two charts, because there are two questions. "How much am I reading?" is a
 * series over days. "When do I read?" is a shape over the hours of one day,
 * and it is the more interesting of the two: a month of days answers whether
 * last week was busy, but the hours answer what kind of reader somebody is.
 *
 * Both are stacked rather than paired, because opened articles are a subset of
 * seen ones and not a second measurement. Two bars side by side would invite
 * the reader to add them together.
 */
@Composable
fun StatisticsPage() {
    val viewModel: StatisticsViewModel = koinNeoViewModel()
    val state by viewModel.state.collectAsState()

    ViewWithActionBar(
        title = stringResource(R.string.pref_statistics),
        largeTitle = true,
        showBackButton = true,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.loading) return@LazyColumn

            // Said before anything is drawn, not after. A reader who scrolls
            // past a chart believing it is theirs has already been misled, and
            // a footnote underneath does not undo it.
            if (state.isExample) item { ExampleNotice() }

            item { Summary(state) }

            item {
                ChartCard(
                    title = stringResource(R.string.stats_by_day),
                    caption = stringResource(R.string.stats_by_day_caption, STATS_WINDOW_DAYS),
                ) {
                    DayChart(state.byDay)
                }
            }

            if (state.openingUncounted) item { OpeningNotice() }

            item {
                ChartCard(
                    title = stringResource(R.string.stats_by_hour),
                    caption = stringResource(R.string.stats_by_hour_caption),
                    legend = { DayBandLegend() },
                ) {
                    HourChart(state.byHour)
                }
            }

            if (state.sources.isNotEmpty()) item {
                ChartCard(
                    title = stringResource(R.string.stats_by_source),
                    caption = stringResource(R.string.stats_by_source_caption),
                    legend = {},
                ) {
                    SourceChart(state.sources)
                }
            }

            item { Footnote() }
        }
    }
}

/* ---------------------------------------------------------------- pieces -- */

/**
 * The two series, named in words.
 *
 * Present whenever a chart has more than one series, without exception: a
 * stacked bar encodes identity in colour alone, and colour alone is not an
 * encoding for the eight percent of men who cannot separate two of them.
 */
@Composable
private fun Legend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp),
    ) {
        LegendKey(openedColor(), stringResource(R.string.stats_opened))
        LegendKey(passedColor(), stringResource(R.string.stats_passed))
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        // Text keeps its own ink rather than the series colour: a coloured
        // label beside a coloured mark says the same thing twice and fails
        // the same readers the mark already failed.
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChartCard(
    title: String,
    caption: String,
    legend: @Composable () -> Unit = { Legend() },
    chart: @Composable () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            chart()
            legend()
        }
    }
}

/**
 * The headline figures.
 *
 * Above the charts because they answer the question most people came with,
 * and a chart is slower to read than a number. The charts are for the reader
 * who has a second question.
 */
@Composable
private fun Summary(state: StatisticsState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat(
                value = state.seen.toString(),
                label = stringResource(R.string.stats_seen),
                modifier = Modifier.weight(1f),
                tone = StatTone.Neutral,
            )
            Stat(
                value = state.opened.toString(),
                label = stringResource(R.string.stats_opened),
                modifier = Modifier.weight(1f),
                tone = StatTone.Primary,
            )
            Stat(
                value = state.perActiveDay.toString(),
                label = stringResource(R.string.stats_per_day),
                modifier = Modifier.weight(1f),
                tone = StatTone.Neutral,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val spent = state.readingHoursMinutes
            Stat(
                // A dash rather than "0m": nothing measured is not the same
                // statement as no time spent, and a read can go unmeasured for
                // honest reasons.
                value = spent?.let { (h, m) ->
                    if (h > 0) stringResource(R.string.stats_hours_minutes, h, m)
                    else stringResource(R.string.stats_minutes, m)
                } ?: stringResource(R.string.stats_unmeasured),
                label = stringResource(R.string.stats_time_reading),
                modifier = Modifier.weight(1f),
                tone = if (spent != null) StatTone.Secondary else StatTone.Neutral,
            )
            Stat(
                value = state.busiestHour
                    ?.let { stringResource(R.string.stats_hour_of_day, it) }
                    ?: stringResource(R.string.stats_unmeasured),
                label = stringResource(R.string.stats_busiest),
                modifier = Modifier.weight(1f),
                tone = StatTone.Tertiary,
            )
            Stat(
                value = pluralStringResource(
                    R.plurals.stats_streak_days, state.streak, state.streak
                ),
                label = stringResource(R.string.stats_streak),
                modifier = Modifier.weight(1f),
                tone = StatTone.Secondary,
            )
        }
    }
}

/**
 * How much a tile should stand out, and in which of the theme's families.
 *
 * Theme containers rather than the chart palette, and the distinction is the
 * whole reason there are two sets of colour on this screen. A tile's colour
 * does not encode anything — the number is the number whatever shade sits
 * behind it — so it is chrome, and chrome follows the reader's scheme,
 * including whatever Material You built from their wallpaper. The chart marks
 * do encode something, so they come from a palette that was checked.
 */
private enum class StatTone { Neutral, Primary, Secondary, Tertiary }

@Composable
private fun Stat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tone: StatTone = StatTone.Neutral,
) {
    val container = when (tone) {
        StatTone.Neutral   -> MaterialTheme.colorScheme.surfaceContainer
        StatTone.Primary   -> MaterialTheme.colorScheme.primaryContainer
        StatTone.Secondary -> MaterialTheme.colorScheme.secondaryContainer
        StatTone.Tertiary  -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val ink = when (tone) {
        StatTone.Neutral   -> MaterialTheme.colorScheme.onSurface
        StatTone.Primary   -> MaterialTheme.colorScheme.onPrimaryContainer
        StatTone.Secondary -> MaterialTheme.colorScheme.onSecondaryContainer
        StatTone.Tertiary  -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(shape = MaterialTheme.shapes.large, color = container, modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = ink,
                maxLines = 1,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                // Paired with its own container rather than the page surface,
                // or a label on a tinted tile fails contrast in one theme and
                // looks washed out in the other.
                color = ink.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun ExampleNotice() {
    Notice(
        title = stringResource(R.string.stats_example_title),
        body = stringResource(R.string.stats_example_body),
    )
}

@Composable
private fun OpeningNotice() {
    Notice(
        title = stringResource(R.string.stats_opening_new_title),
        body = stringResource(R.string.stats_opening_new_body),
    )
}

@Composable
private fun Notice(title: String, body: String) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Footnote() {
    Text(
        text = stringResource(R.string.stats_footnote),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/* ---------------------------------------------------------------- charts -- */

/**
 * Opened in the accent, everything else in neutral ink.
 *
 * Not a categorical pair. The two series are one quantity split in two, and
 * one half of it is the half the reader cares about — so this is emphasis
 * against recession, which is the case a categorical palette is the wrong
 * test for. Both are taken from the theme rather than written as hex, because
 * the scheme is seeded from Whisper's own blue and may be replaced wholesale
 * by Material You; a hard-coded accent would be the one thing on the screen
 * that ignored the reader's wallpaper.
 */
@Composable
private fun openedColor(): Color = MaterialTheme.colorScheme.primary

/**
 * Seventy percent rather than full strength.
 *
 * `onSurfaceVariant` is a text-grade token, so it clears 4.5:1 against the
 * surface in both themes by construction; at 0.7 it still clears the 3:1 a
 * filled shape needs, while leaving the accent the louder of the two. Full
 * strength made the bulk of every bar shout as loudly as the part worth
 * looking at.
 */
@Composable
private fun passedColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

/** A month of days. Sparse labels, because thirty dates do not fit. */
@Composable
private fun DayChart(days: List<DayCount>) {
    if (days.isEmpty()) return
    val opened = openedColor()
    val passed = passedColor()
    val max = days.maxOf { it.seen }.coerceAtLeast(1)

    val seen = days.sumOf { it.seen }
    val description = stringResource(
        R.string.stats_by_day_description,
        pluralStringResource(R.plurals.stats_articles_seen, seen, seen),
        days.sumOf { it.opened },
        pluralStringResource(R.plurals.stats_days, days.size, days.size),
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 2.dp.toPx()
        val slot = size.width / days.size
        val barWidth = (slot - gap).coerceAtLeast(1f)
        days.forEachIndexed { index, day ->
            drawStack(
                x = index * slot + gap / 2f,
                width = barWidth,
                seen = day.seen,
                opened = day.opened,
                max = max,
                openedColor = opened,
                passedColor = passed,
                segmentGap = gap,
                radius = 4.dp.toPx(),
            )
        }
    }

    // Three dates rather than thirty: the ends say what the window is and the
    // middle one stops it being guesswork.
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    val labels = listOf(0, days.size / 2, days.size - 1)
        .map { days[it].day.toLocalDateOrNull()?.format(fmt).orEmpty() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEach { label -> AxisLabel(label) }
    }
}

/** One day, in twenty-four buckets. */
@Composable
private fun HourChart(hours: List<HourCount>) {
    if (hours.isEmpty()) return
    val opened = openedColor()
    val passed = passedColor()
    val max = hours.maxOf { it.seen }.coerceAtLeast(1)

    val busiest = hours.filter { it.seen > 0 }.maxByOrNull { it.seen }?.hour
    val description = if (busiest == null) stringResource(R.string.stats_by_hour_empty)
    else stringResource(R.string.stats_by_hour_description, busiest)

    // One colour per part of the day. The height already carries the value, so
    // this is not encoding it twice — it answers a different question, and the
    // one people actually ask of this chart: what kind of reader am I. A wall
    // of identical bars makes you count gridlines to find out; four blocks of
    // colour make "evenings, mostly" readable at a glance.
    val bandColors = (0..3).map { ChartPalette.band(it) }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .semantics { contentDescription = description },
    ) {
        val gap = 2.dp.toPx()
        val slot = size.width / hours.size
        val barWidth = (slot - gap).coerceAtLeast(1f)
        hours.forEachIndexed { index, hour ->
            val band = bandColors[dayBandOf(index)]
            drawStack(
                x = index * slot + gap / 2f,
                width = barWidth,
                seen = hour.seen,
                opened = hour.opened,
                max = max,
                // Opened keeps the band's own colour; the rest of the bar is
                // the same hue held back, so the part of the day still reads
                // as one block while the opened share stays legible inside it.
                openedColor = band,
                passedColor = band.copy(alpha = 0.4f),
                segmentGap = gap,
                radius = 4.dp.toPx(),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("00", "06", "12", "18", "23").forEach { AxisLabel(it) }
    }
}

/**
 * Names the four blocks of colour.
 *
 * Required rather than decorative. Three of these hues sit below 3:1 against a
 * light surface, which the palette is allowed to do only where the marks are
 * labelled rather than left to colour alone — see [ChartPalette].
 */
@Composable
private fun DayBandLegend() {
    val labels = listOf(
        R.string.stats_band_night,
        R.string.stats_band_morning,
        R.string.stats_band_afternoon,
        R.string.stats_band_evening,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        labels.forEachIndexed { index, label ->
            LegendKey(ChartPalette.band(index), stringResource(label))
        }
    }
}

@Composable
private fun AxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One stacked bar, sitting on the baseline.
 *
 * Opened at the bottom against the axis, because that is the series being
 * compared across bars and a segment that floats cannot be compared by eye.
 * Only the top of the stack is rounded — a rounded foot would lift the bar off
 * its own baseline and make short bars look shorter than they are — and the
 * two segments are separated by a gap of the surface rather than a border, so
 * the division survives both themes without a colour of its own.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStack(
    x: Float,
    width: Float,
    seen: Int,
    opened: Int,
    max: Int,
    openedColor: Color,
    passedColor: Color,
    segmentGap: Float,
    radius: Float,
) {
    if (seen <= 0) return
    val full = size.height * (seen.toFloat() / max)
    val openedHeight = size.height * (opened.toFloat() / max)
    val base = size.height

    // A bar a fraction of a pixel high reads as an empty slot, which is a
    // different fact from "one article". Anything non-zero gets a visible foot.
    val minimum = 2f
    val total = full.coerceAtLeast(minimum)

    if (opened in 1..<seen) {
        val openPart = openedHeight.coerceAtLeast(minimum)
        val passedTop = base - total
        val passedBottom = (base - openPart - segmentGap).coerceAtLeast(passedTop)
        drawTopRounded(x, passedTop, width, passedBottom - passedTop, radius, passedColor)
        drawTopRounded(x, base - openPart, width, openPart, 0f, openedColor)
    } else if (opened >= seen) {
        drawTopRounded(x, base - total, width, total, radius, openedColor)
    } else {
        drawTopRounded(x, base - total, width, total, radius, passedColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTopRounded(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
    color: Color,
) {
    if (height <= 0f) return
    // Never a radius larger than half the bar, or the two corners meet and the
    // top turns into a dome that no longer reads as a measured height.
    val r = radius.coerceAtMost(minOf(width, height) / 2f)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(x, y, x + width, y + height),
                topLeft = CornerRadius(r, r),
                topRight = CornerRadius(r, r),
                bottomRight = CornerRadius.Zero,
                bottomLeft = CornerRadius.Zero,
            )
        )
    }
    drawPath(path, color)
}

/** `YYYY-MM-DD` as SQLite wrote it, or null if a filled gap left it blank. */
private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()


/**
 * Where the feed's volume actually comes from.
 *
 * Horizontal, because the labels are source names and a name under a vertical
 * bar is either rotated or truncated, and both are worse than turning the
 * chart on its side. Each source keeps its own colour, which is the one use of
 * colour on this screen that carries identity rather than emphasis — and the
 * reason a validated categorical palette exists here at all.
 *
 * Every bar is labelled with its source and its count, so nothing depends on
 * telling two hues apart. That is also what lets the palette use steps which
 * sit below 3:1 on a light surface; see [ChartPalette].
 */
@Composable
private fun SourceChart(sources: List<SourceShare>) {
    val max = sources.maxOf { it.seen }.coerceAtLeast(1)
    val otherColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    val otherLabel = stringResource(R.string.stats_other_sources)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sources.forEachIndexed { index, row ->
            // The folded remainder is not one source, so it does not take a
            // categorical slot — a colour that says "identity" over a bar that
            // has none would be the chart telling a small lie.
            val color = if (row.other) otherColor else ChartPalette.slot(index)
            val name = when {
                row.other -> otherLabel
                row.title.isBlank() -> stringResource(R.string.stats_removed_source)
                else -> row.title
            }
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = row.seen.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                ) {
                    val full = size.width * (row.seen.toFloat() / max)
                    val r = size.height / 2f
                    // The track, so a short bar still reads as a share of
                    // something rather than as a stray mark.
                    drawRoundRect(
                        color = color.copy(alpha = 0.15f),
                        cornerRadius = CornerRadius(r, r),
                    )
                    if (full > 0f) {
                        drawRoundRect(
                            color = color,
                            size = androidx.compose.ui.geometry.Size(
                                width = full.coerceAtLeast(size.height),
                                height = size.height,
                            ),
                            cornerRadius = CornerRadius(r, r),
                        )
                    }
                }
            }
        }
    }
}
