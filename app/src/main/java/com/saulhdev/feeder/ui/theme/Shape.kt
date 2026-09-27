package com.saulhdev.feeder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * The shape scale, as the theme's third axis alongside colour and type.
 *
 * MaterialTheme was only ever given a colour scheme and a typography, so
 * `MaterialTheme.shapes` sat at the Material default while the components that
 * needed a corner each picked their own number — 24dp here, 20dp there, 16dp,
 * 14dp, 12dp. Corner radius is part of what makes a surface look Material You
 * rather than generic, and scattering it meant the app could not be made
 * rounder or squarer without hunting through files.
 *
 * The steps follow Material 3's roles and are one notch rounder than its
 * defaults (4/8/12/16/28), which is what Material You's own surfaces look like
 * on a Pixel. Everything that draws a corner now names a role here rather than
 * a number, so the whole app's shape language moves from this one place.
 */
val WhisperShapes = Shapes(
    /** Interior corners of a grouped list — barely rounded, so the group reads as one block. */
    extraSmall = RoundedCornerShape(8.dp),
    /** Small inline elements. */
    small = RoundedCornerShape(12.dp),
    /** Thumbnails and icon tiles. */
    medium = RoundedCornerShape(16.dp),
    /** Cards, chips and images — the shape seen most. */
    large = RoundedCornerShape(20.dp),
    /** Sheets and dialogs. */
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun groupItemShape(index: Int, lastIndex: Int) = RoundedCornerShape(
    topStart = if (index == 0) MaterialTheme.shapes.large.topStart
    else MaterialTheme.shapes.extraSmall.topStart,
    topEnd = if (index == 0) MaterialTheme.shapes.large.topEnd
    else MaterialTheme.shapes.extraSmall.topEnd,
    bottomStart = if (index == lastIndex) MaterialTheme.shapes.large.bottomStart
    else MaterialTheme.shapes.extraSmall.bottomStart,
    bottomEnd = if (index == lastIndex) MaterialTheme.shapes.large.bottomEnd
    else MaterialTheme.shapes.extraSmall.bottomEnd
)