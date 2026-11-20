/*
 * Phosphor Icons (arrows-down-up, regular), MIT licence, (c) 2023 Phosphor Icons.
 * Path data copied unchanged from @phosphor-icons/core 2.1.1. See ATTRIBUTION.md.
 */

package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

/**
 * Sort. Two arrows read at 24dp; the A-Z mark before it had letters too
 * small to make out (owner's screenshot, 20 November).
 */
val Phosphor.ArrowsDownUp: ImageVector
    get() = _arrowsDownUp

private val _arrowsDownUp: ImageVector by lazy {
    ImageVector.Builder(
        name = "ArrowsDownUp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).addPath(
        pathData = addPathNodes("M117.66,170.34a8,8,0,0,1,0,11.32l-32,32a8,8,0,0,1-11.32,0l-32-32a8,8,0,0,1,11.32-11.32L72,188.69V48a8,8,0,0,1,16,0V188.69l18.34-18.35A8,8,0,0,1,117.66,170.34Zm96-96-32-32a8,8,0,0,0-11.32,0l-32,32a8,8,0,0,0,11.32,11.32L168,67.31V208a8,8,0,0,0,16,0V67.31l18.34,18.35a8,8,0,0,0,11.32-11.32Z"),
        fill = SolidColor(Color(0xFF000000)),
    ).build()
}
