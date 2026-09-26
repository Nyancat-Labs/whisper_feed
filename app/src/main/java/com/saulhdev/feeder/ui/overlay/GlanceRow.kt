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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.glance.GlanceState
import com.saulhdev.feeder.manager.glance.weatherLook
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * The least a chip is: three lines at the normal text size, with room over.
 *
 * It used to be all a chip could be. At 130% text on a 360 dp phone the
 * third line, the place, was cut in half, and at 150% on 412 dp only a sliver
 * of it showed. The height now follows the text size; see [glanceChipHeight].
 */
private val CHIP_MIN_HEIGHT = 84.dp

/** Above and below the chip's text. */
private val CHIP_PADDING_V = 10.dp

/**
 * How wide a chip gets on a wide screen.
 *
 * Half the width, less the margins, is right on a phone (159 dp at 360,
 * 185 dp at 412) and wrong on anything wider: on a phone on its side, a
 * Fold's inner screen or a tablet, two chips stretched to half the screen
 * each. Capped, all three fit and the row starts from the left.
 */
private val CHIP_MAX_WIDTH = 220.dp

/** The space between two chips, and part of the sum that sizes them. */
private val CHIP_GAP = 10.dp


/** The supplied artwork is rendered at this size; see docs/brand/08_icons. */
private val ICON_SIZE = 32.dp

/** Between the text and the artwork. */
private val ICON_GAP = 10.dp

/** Small enough to read as an annotation on the temperature, not a second icon. */
private val RAIN_ICON_SIZE = 16.dp

/** Between the temperature and the rain figure. */
private val RAIN_GAP = 6.dp

/**
 * Below this, the chance of rain is not worth the space.
 *
 * A forecast that says 5% is saying "no", and repeating that on every dry day
 * would make the chip busier without making it more useful.
 */
private const val RAIN_WORTH_MENTIONING = 20

private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The status strip above the category filters.
 *
 * Deliberately separate from [CategoryChipRow] even though both are rows of
 * rounded things: those chips are controls that change what the feed shows,
 * these are read-only status. Mixing them would make the row's behaviour
 * unguessable — half of it filters, half of it does nothing when tapped.
 *
 * A scrolling row of equally sized chips.
 *
 * It has been both other things. Sizing each chip to its own content made a
 * long place name half again as wide as its neighbours, with an extra line of
 * text that made it taller too, so the strip read as three unrelated boxes.
 * Fixing that with three equal thirds went too far the other way: everything
 * fitted exactly, which left each chip a 71dp text column and no room at all.
 *
 * One fixed width, just under half the screen, gets both — the chips are
 * identical, and the third being visibly cut off is what tells you the row
 * scrolls.
 */
@Composable
fun GlanceRow(
    state: GlanceState,
    onSetLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.enabled) return
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    if (!glanceFits(
            widthDp = with(density) { window.width.toDp() }.value,
            heightDp = with(density) { window.height.toDp() }.value,
        )
    ) return

    val weather = state.weather

    // Measured, not guessed. The width was 45.5% of the screen — a fraction
    // chosen to be "just under half", which meant two chips plus their margins
    // and the gap between them never added up to the width available, so the
    // pair sat off-centre with the second one clipped at the edge.
    //
    // Two chips, two margins and one gap is an equation with one answer.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val chipWidth = glanceChipWidth(maxWidth)
        val labelStyle = MaterialTheme.typography.labelMedium
        val valueStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        val measurer = rememberTextMeasurer()

        // A line of each, measured rather than worked out from the style.
        // Under Android's non-linear text scaling the line height grows with
        // the font, not by the scale's own table: at 200% a label line is
        // 32 dp where the arithmetic said 28, and the chips were cut short by
        // exactly the difference.
        val (labelLine, valueLine) = remember(labelStyle, valueStyle, density) {
            with(density) {
                measurer.measure("Ag", labelStyle, maxLines = 1).size.height.toDp() to
                    measurer.measure("Ag", valueStyle, maxLines = 1).size.height.toDp()
            }
        }

        val temperature = weather?.let { "${it.temperatureC.roundToInt()}°" }
        // Only when it is worth knowing. A chip that reads "3%" every dry
        // day is noise, and the number is only ever useful as the answer
        // to "do I need a coat".
        val rain = weather?.precipitationChance?.takeIf { it >= RAIN_WORTH_MENTIONING }?.let { "$it%" }
        val rainBelow = temperature != null && rain != null &&
            remember(temperature, rain, labelStyle, valueStyle, density, chipWidth) {
                with(density) {
                    !rainFitsBeside(
                        valueWidth = measurer.measure(temperature, valueStyle, maxLines = 1).size.width,
                        gapAndIcon = (RAIN_GAP + RAIN_ICON_SIZE).roundToPx(),
                        rainWidth = measurer.measure(rain, labelStyle, maxLines = 1).size.width,
                        available = glanceTextWidth(chipWidth).roundToPx(),
                    )
                }
            }

        // One height for the row, so every chip matches whether or not it
        // has a third line, or a fourth for the rain.
        val chipHeight = glanceChipHeight(labelLine, valueLine, labelLines = if (rainBelow) 3 else 2)
        val listState = rememberLazyListState()

        LazyRow(
            state = listState,
            // So a swipe always comes to rest on a pair rather than halfway
            // between two. There are three chips and room for two, which
            // without this leaves the third permanently half-visible.
            flingBehavior = rememberSnapFlingBehavior(listState),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = CARD_MARGIN, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        ) {
        if (weather != null) {
            val look = weatherLook(weather.weatherCode, weather.isDay)
            // Condition on top and the place underneath. Place names run long,
            // and putting the one that can still overflow on the line that
            // matters least means the truncation lands where it costs least.
            item {
                GlanceChip(
                    width = chipWidth,
                    height = chipHeight,
                    label = stringResource(look.labelRes),
                    value = temperature.orEmpty(),
                    caption = weather.place.substringBefore(","),
                    iconRes = look.iconRes,
                    rain = rain,
                    rainBelow = rainBelow,
                )
            }
            // After dark, today's sunset is behind us and repeating it is
            // stale — the next thing that happens is sunrise, so the chip
            // becomes that, artwork and label together.
            val sunUp = weather.isDay
            val next = if (sunUp) weather.nextSunset else weather.nextSunrise
            item {
                GlanceChip(
                    width = chipWidth,
                    height = chipHeight,
                    label = stringResource(
                        if (sunUp) R.string.glance_sunset else R.string.glance_sunrise
                    ),
                    value = next?.format(HOUR_MINUTE) ?: "—",
                    iconRes = if (sunUp) R.drawable.ic_glance_sunset
                    else R.drawable.ic_glance_sunrise,
                )
            }
        } else {
            // The row is on but has nothing to show without a place, so the chip
            // is the way to fix that rather than a dead end.
            item {
                GlanceChip(
                    width = chipWidth,
                    height = chipHeight,
                    label = stringResource(R.string.pref_glance_place),
                    value = stringResource(R.string.glance_set_location),
                    iconRes = R.drawable.ic_glance_location,
                    onClick = onSetLocation,
                )
            }
        }

            item {
                GlanceChip(
                    width = chipWidth,
                    height = chipHeight,
                    label = stringResource(R.string.glance_read_today),
                    value = state.readToday.toString(),
                    iconRes = R.drawable.ic_glance_articles_read,
                )
            }
        }
    }
}

/**
 * Whether the window has room for the glance row at all.
 *
 * On a short screen, a Flip's cover or any phone on its side, the header,
 * this row and the chips were the whole first screen, and not one story
 * showed without a scroll. In a split screen a fifth of the width, every chip
 * was cut to dots. The row is the tallest part of the header and the least
 * needed, so there it steps aside; it comes back with the room.
 */
internal fun glanceFits(widthDp: Float, heightDp: Float): Boolean =
    heightDp >= GLANCE_MIN_HEIGHT_DP && widthDp >= GLANCE_MIN_WIDTH_DP

/** The shortest window, and the narrowest, that keeps the glance row. */
private const val GLANCE_MIN_HEIGHT_DP = 480f
private const val GLANCE_MIN_WIDTH_DP = 280f

/**
 * Half the width less the margins and the gap, up to [CHIP_MAX_WIDTH].
 */
internal fun glanceChipWidth(available: Dp): Dp =
    ((available - CARD_MARGIN * 2 - CHIP_GAP) / 2).coerceAtMost(CHIP_MAX_WIDTH)

/** Where a chip's text has to fit: its width less the padding and the artwork. */
internal fun glanceTextWidth(chipWidth: Dp): Dp = chipWidth - CARD_MARGIN * 2 - ICON_SIZE - ICON_GAP

/**
 * Tall enough for the value and [labelLines] lines of label size at the text
 * size in use, and never less than [CHIP_MIN_HEIGHT].
 */
internal fun glanceChipHeight(labelLine: Dp, valueLine: Dp, labelLines: Int = 2): Dp =
    maxOf(CHIP_MIN_HEIGHT, labelLine * labelLines + valueLine + CHIP_PADDING_V * 2)

/**
 * Whether the rain figure fits beside the temperature, all in pixels.
 *
 * The figure is the part of the chip worth reading ("do I need a coat"),
 * and beside the temperature it was the part cut off: "100%" read "100" on
 * every 360 dp phone, "10" at 130% text and "1" at 200%. Where it will not
 * fit whole it takes a line of its own under the temperature, and every chip
 * in the row grows by that line.
 */
internal fun rainFitsBeside(valueWidth: Int, gapAndIcon: Int, rainWidth: Int, available: Int): Boolean =
    valueWidth + gapAndIcon + rainWidth <= available

/**
 * One chip. Label above, value below, the artwork on the right.
 *
 * [caption] is optional and the chip is the same height without it, so a chip
 * that has a third line does not push its neighbours around.
 */
@Composable
private fun GlanceChip(
    width: Dp,
    height: Dp,
    label: String,
    value: String,
    @DrawableRes iconRes: Int,
    caption: String? = null,
    rain: String? = null,
    rainBelow: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val shape = MaterialTheme.shapes.large
    val chipModifier = Modifier
        .width(width)
        .height(height)

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CARD_MARGIN, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                val labelStyle = MaterialTheme.typography.labelMedium
                Text(
                    text = label,
                    style = labelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (rain != null && !rainBelow) {
                        Spacer(Modifier.size(RAIN_GAP))
                        RainFigure(rain, labelStyle)
                    }
                }
                // On its own line where it would not fit whole beside the
                // temperature, rather than cut to "10".
                if (rain != null && rainBelow) RainFigure(rain, labelStyle)
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = labelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.size(ICON_GAP))

            // Drawn, not tinted: these are full-colour illustrations, so the
            // tinted circular badge that suited a monochrome symbol is gone and
            // the artwork sits directly on the chip.
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
    }

    if (onClick != null) {
        Card(onClick = onClick, modifier = chipModifier, shape = shape, colors = colors) { content() }
    } else {
        Card(modifier = chipModifier, shape = shape, colors = colors) { content() }
    }
}

/**
 * The supplied rain artwork at a small size, and the chance beside it: "20%"
 * on its own, next to a temperature, could be anything. Never shortened.
 */
@Composable
private fun RainFigure(text: String, style: TextStyle) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_glance_weather_rain),
            contentDescription = null,
            modifier = Modifier.size(RAIN_ICON_SIZE),
        )
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}
