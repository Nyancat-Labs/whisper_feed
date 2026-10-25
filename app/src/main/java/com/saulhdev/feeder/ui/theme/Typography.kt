package com.saulhdev.feeder.ui.theme

import androidx.compose.ui.unit.isSpecified
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.saulhdev.feeder.R

/**
 * Inter, bundled — the typeface the brand specifies.
 *
 * Four static weights rather than the variable font, so rendering is identical
 * on every supported release without depending on variable-font support.
 * Licensed under the SIL Open Font License; see docs/licenses/Inter-OFL.txt.
 */
val InterFontFamily = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * The Material 3 type scale, restated over one family.
 *
 * Only `bodyLarge` used to be overridden, so every other style fell back to the
 * M3 default and the app could never actually be set in one typeface. Each
 * style is now derived from the M3 baseline with the family swapped, which
 * keeps Material's sizes and spacing while letting the whole app be Inter — or
 * the device font, if that is what the user prefers.
 */
fun typographyFor(family: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        headlineLarge = base.headlineLarge.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        headlineSmall = base.headlineSmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
    )
}

/**
 * The same scale, every style's size and line height multiplied by [factor].
 *
 * For the reader's text size: the article is set from the type scale (body,
 * headings, byline), so scaling the scale moves all of it together and keeps
 * their proportions. Line heights move with the sizes, or larger text would
 * crowd its own lines. Unspecified units stay unspecified.
 */
fun Typography.scaled(factor: Float): Typography {
    if (factor == 1f) return this
    fun TextStyle.scaled() = copy(
        fontSize = if (fontSize.isSpecified) fontSize * factor else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * factor else lineHeight,
    )
    return Typography(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}

/** Resolves the saved `appFont` preference to a family. */
fun fontFamilyFor(pref: String): FontFamily = when (pref) {
    "system" -> FontFamily.Default
    else     -> InterFontFamily
}

/** Kept for callers that want the default without reading the preference. */
val Typography = typographyFor(InterFontFamily)

@Composable
fun linkTextStyle(): TextStyle =
    TextStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )

@Composable
fun codeInlineStyle(): SpanStyle =
    SpanStyle(
        background = codeBlockBackground(),
        fontFamily = FontFamily.Monospace
    )

/**
 * Has no background because it is meant to be put over a Surface which has the proper background.
 */
@Composable
fun codeBlockStyle(): TextStyle =
    MaterialTheme.typography.bodyMedium.merge(
        SpanStyle(
            fontFamily = FontFamily.Monospace
        )
    )

@Composable
fun codeBlockBackground(): Color =
    MaterialTheme.colorScheme.surfaceVariant

@Composable
fun blockQuoteStyle(): SpanStyle =
    MaterialTheme.typography.bodyLarge.toSpanStyle().merge(
        SpanStyle(
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
        )
    )

val kingthingsPrintingkit = FontFamily(
    Font(R.font.kingthings_printingkit)
)
