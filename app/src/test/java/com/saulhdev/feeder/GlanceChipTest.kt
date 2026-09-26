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
package com.saulhdev.feeder

import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.overlay.glanceChipHeight
import com.saulhdev.feeder.ui.overlay.glanceChipWidth
import com.saulhdev.feeder.ui.overlay.glanceFits
import com.saulhdev.feeder.ui.overlay.leadPictureHeight
import com.saulhdev.feeder.ui.overlay.glanceTextWidth
import com.saulhdev.feeder.ui.overlay.rainFitsBeside
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glance chips at the sizes the phones that sell most have, and at the
 * text sizes people choose. The line heights are the ones measured on the
 * render harness, in Inter: at 200% a label line is 32 dp, not the 28 the
 * scale's own table gives, because the line grows with the font.
 */
class GlanceChipTest {

    @Test
    fun `a phone's chips are half its width, a wide screen's stop at the cap`() {
        assertEquals(159.dp, glanceChipWidth(360.dp))
        assertEquals(185.dp, glanceChipWidth(412.dp))
        // A phone on its side, a Fold opened, a tablet: all three fit.
        assertEquals(220.dp, glanceChipWidth(892.dp))
        assertEquals(220.dp, glanceChipWidth(1280.dp))
    }

    @Test
    fun `at the normal text size a chip is the height it always was`() {
        // 16 + 16 + 28 + the padding comes to 80, under the 84 minimum.
        assertEquals(84.dp, glanceChipHeight(labelLine = 16.dp, valueLine = 28.dp))
    }

    @Test
    fun `larger text makes a taller chip, not a cut one`() {
        assertEquals(94.dp, glanceChipHeight(labelLine = 21.dp, valueLine = 32.dp))
        assertEquals(128.dp, glanceChipHeight(labelLine = 32.dp, valueLine = 44.dp))
    }

    @Test
    fun `the rain figure's own line is one more label line`() {
        assertEquals(96.dp, glanceChipHeight(labelLine = 16.dp, valueLine = 28.dp, labelLines = 3))
    }

    @Test
    fun `the text has the chip less its padding and artwork`() {
        // 159 less 16 each side, the 32 dp artwork and its 10 dp gap.
        assertEquals(85.dp, glanceTextWidth(159.dp))
    }

    @Test
    fun `the glance row steps aside on a short screen and in a narrow split`() {
        assertTrue(glanceFits(widthDp = 360f, heightDp = 780f))
        assertTrue(glanceFits(widthDp = 323f, heightDp = 792f))
        // A Flip's cover screen, and a phone on its side.
        assertFalse(glanceFits(widthDp = 361f, heightDp = 399f))
        assertFalse(glanceFits(widthDp = 892f, heightDp = 412f))
        // A fifth of the screen in a split.
        assertFalse(glanceFits(widthDp = 220f, heightDp = 780f))
    }

    @Test
    fun `a lead picture is four by three, but never taller than half the window`() {
        // A phone: unchanged.
        assertEquals(810, leadPictureHeight(widthPx = 1080, windowHeightPx = 2410))
        // A Pixel 10 Pro Fold at 3 px a dp: 692 wide would be 519 tall of a
        // 717 dp window; capped at half of it.
        assertEquals(717 * 3 / 2, leadPictureHeight(widthPx = 692 * 3, windowHeightPx = 717 * 3))
        // Before the window is known, nothing is capped.
        assertEquals(810, leadPictureHeight(widthPx = 1080, windowHeightPx = 0))
    }

    @Test
    fun `the rain figure stays beside the temperature only when it fits whole`() {
        assertTrue(rainFitsBeside(valueWidth = 100, gapAndIcon = 60, rainWidth = 80, available = 240))
        assertFalse(rainFitsBeside(valueWidth = 100, gapAndIcon = 60, rainWidth = 81, available = 240))
    }
}
