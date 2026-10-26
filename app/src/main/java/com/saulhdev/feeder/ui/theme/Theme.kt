package com.saulhdev.feeder.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * The app's theme.
 *
 * This used to be a second, independent implementation of the same rule the
 * overlay applies, and the two had already drifted: it took a plain
 * `darkTheme: Boolean`, so the "Black" and "Automatic (from system; black)"
 * options collapsed into ordinary dark, and it carried an unused Purple/Pink
 * palette left over from the project template. It now delegates to
 * [OverlayTheme.schemeFor], which is what the launcher surface uses, so both
 * answer the colour question the same way.
 *
 * @param mode a key from `getThemes()`: auto_system, light or dark.
 * @param dynamicColor whether to derive colours from the wallpaper.
 * @param pureBlack true black rather than dark grey, in dark modes.
 * @param fontPref the saved `appFont` value: "inter" or "system".
 *
 * Material 3 Expressive (roadmap §19e): its springs for motion, and the
 * defaults components take under it (ripples, button shapes, progress
 * indicators). The overlay uses the same theme; see [WhisperMotion].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(
    mode: String,
    dynamicColor: Boolean,
    pureBlack: Boolean,
    fontPref: String,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = OverlayTheme.schemeFor(context, mode, dynamicColor, pureBlack),
        motionScheme = WhisperMotion,
        typography = typographyFor(fontFamilyFor(fontPref)),
        shapes = WhisperShapes,
        content = content
    )
}

/** Expressive's motion: springs with a little give, for app and overlay alike. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val WhisperMotion: MotionScheme = MotionScheme.expressive()
