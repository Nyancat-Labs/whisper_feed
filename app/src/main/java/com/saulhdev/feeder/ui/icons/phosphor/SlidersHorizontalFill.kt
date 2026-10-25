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

package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

/** Phosphor's SlidersHorizontal, fill weight: the same sheet, while it is narrowing the feed. */
val Phosphor.SlidersHorizontalFill: ImageVector
    get() {
        _slidersHorizontalFill?.let { return it }
        return ImageVector.Builder(
            name = "SlidersHorizontalFill",
            defaultWidth = 24.0.dp,
            defaultHeight = 24.0.dp,
            viewportWidth = 256.0f,
            viewportHeight = 256.0f,
        ).addPath(
            pathData = addPathNodes(
                "M32,80a8,8,0,0,1,8-8H77.17a28,28,0,0,1,53.66,0H216a8,8,0,0,1,0,16H130.83a28,28,0,0,1-53.66,0H40A8,8," +
                    "0,0,1,32,80Zm184,88H194.83a28,28,0,0,0-53.66,0H40a8,8,0,0,0,0,16H141.17a28,28,0,0,0,53.66,0H216a8,8," +
                    "0,0,0,0-16Z"
            ),
            fill = SolidColor(Color(0xFF000000)),
        ).build().also { _slidersHorizontalFill = it }
    }

private var _slidersHorizontalFill: ImageVector? = null
