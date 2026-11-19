/*
 * Phosphor Icons (envelope-simple, regular), MIT licence, (c) 2023 Phosphor Icons.
 * Path data copied unchanged from @phosphor-icons/core 2.1.1. See ATTRIBUTION.md.
 */

package com.saulhdev.feeder.ui.icons.phosphor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.ui.icons.Phosphor

val Phosphor.EnvelopeSimple: ImageVector
    get() = _envelopeSimple

private val _envelopeSimple: ImageVector by lazy {
    ImageVector.Builder(
        name = "EnvelopeSimple",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 256f,
        viewportHeight = 256f,
    ).addPath(
        pathData = addPathNodes("M224,48H32a8,8,0,0,0-8,8V192a16,16,0,0,0,16,16H216a16,16,0,0,0,16-16V56A8,8,0,0,0,224,48ZM203.43,64,128,133.15,52.57,64ZM216,192H40V74.19l82.59,75.71a8,8,0,0,0,10.82,0L216,74.19V192Z"),
        fill = SolidColor(Color(0xFF000000)),
    ).build()
}
