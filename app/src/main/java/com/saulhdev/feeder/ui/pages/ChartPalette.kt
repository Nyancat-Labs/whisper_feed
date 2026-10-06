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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colours for the charts, in two sets, one per theme.
 *
 * ## Why these do not come from the theme
 *
 * Everything else in Whisper takes its colour from the Material scheme, and
 * with dynamic colour on that scheme is built from the reader's wallpaper.
 * That is right for chrome and wrong for a chart with more than two series:
 * a categorical palette has to stay separable — including for the eight per
 * cent of men who cannot tell red from green — and a set of hues derived from
 * whatever photograph somebody has on their home screen offers no such
 * guarantee. Material gives three or four harmonious hues; it does not promise
 * they are *distinguishable*, which is the only thing a colour carrying
 * identity has to be.
 *
 * So the marks are fixed and validated, and everything around them — every
 * label, axis, surface and legend word — stays on theme tokens. The chart sits
 * in the reader's colours and encodes its data in colours that were checked.
 *
 * ## Checked how
 *
 * `validate_palette.js` from the data-viz method, both palettes, both modes:
 * lightness band, chroma floor, CVD separation on the adjacent pairs, the
 * normal-vision floor, and contrast against the surface. Every gate passes.
 *
 * Three light-mode hues sit below 3:1 against a light surface — aqua at 2.74,
 * yellow 2.11, magenta 2.62 — which the method allows only with relief:
 * visible labels rather than colour alone. Both charts that use these carry
 * direct labels for that reason, and it is not a detail to drop later.
 *
 * Hues and steps are the method's reference palette rather than anything
 * invented here, which is the point: they are a set somebody validated as a
 * set, and re-stepping one by eye would void the result.
 */
object ChartPalette {

    /** Categorical slots, in the validated order. */
    private val LIGHT = listOf(
        Color(0xFF2A78D6), // blue
        Color(0xFFEB6834), // orange
        Color(0xFF1BAF7A), // aqua
        Color(0xFFEDA100), // yellow
        Color(0xFFE87BA4), // magenta
    )

    private val DARK = listOf(
        Color(0xFF3987E5),
        Color(0xFFD95926),
        Color(0xFF199E70),
        Color(0xFFC98500),
        Color(0xFFD55181),
    )

    /**
     * The four parts of a day, in the order the hours run.
     *
     * Not the first four categorical slots: those are ordered for separability
     * alone, and here the colours also have to mean something. Violet for the
     * small hours, orange for the morning, aqua for the afternoon and blue for
     * the evening reads as a day passing, and the ordering was re-validated
     * after the change rather than assumed to survive it — worst adjacent CVD
     * ΔE 9.2 light and 9.4 dark, both clear of the 8 target.
     */
    private val BANDS_LIGHT = listOf(
        Color(0xFF4A3AA7), // night — violet
        Color(0xFFEB6834), // morning — orange
        Color(0xFF1BAF7A), // afternoon — aqua
        Color(0xFF2A78D6), // evening — blue
    )

    private val BANDS_DARK = listOf(
        Color(0xFF9085E9),
        Color(0xFFD95926),
        Color(0xFF199E70),
        Color(0xFF3987E5),
    )

    /**
     * Which set to use, decided from the surface rather than the system.
     *
     * `isSystemInDarkTheme` would be wrong three ways over: the reader can
     * force light or dark against the system, can choose pure black, and can
     * have a dynamic scheme that is darker or lighter than either. The surface
     * actually being painted is the only thing the contrast figures were
     * measured against, so it is the thing to ask.
     */
    @Composable
    @ReadOnlyComposable
    private fun dark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    /** One categorical slot, wrapping round if a chart asks for more than five. */
    @Composable
    @ReadOnlyComposable
    fun slot(index: Int): Color {
        val set = if (dark()) DARK else LIGHT
        return set[index.mod(set.size)]
    }

    /** The colour for one of the four parts of the day, 0 to 3. */
    @Composable
    @ReadOnlyComposable
    fun band(index: Int): Color {
        val set = if (dark()) BANDS_DARK else BANDS_LIGHT
        return set[index.coerceIn(0, set.size - 1)]
    }

    /** How many distinct sources a chart may colour before folding the rest. */
    const val MAX_SLOTS = 5
}

/**
 * Which part of the day an hour belongs to.
 *
 * Six-hour blocks, which is blunt and is the point: the boundaries are there
 * to make a shape legible, not to assert that reading at 11:59 is a different
 * activity from reading at 12:01.
 */
fun dayBandOf(hour: Int): Int = when {
    hour < 6  -> 0
    hour < 12 -> 1
    hour < 18 -> 2
    else      -> 3
}
