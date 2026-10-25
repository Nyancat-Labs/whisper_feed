package com.saulhdev.feeder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import com.materialkolor.dynamicColorScheme
import com.saulhdev.feeder.ui.overlay.PICTURE_FADE
import com.saulhdev.feeder.ui.overlay.READ_ALPHA
import com.saulhdev.feeder.ui.theme.WhisperCobalt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A read card fades, but its text stays readable: 4.5:1 or better, what
 * Android asks of small text. At 55% for the whole card it was not.
 */
class ReadFadeTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb)
    }

    /** The card drawn at [READ_ALPHA] over the feed's background. */
    private fun faded(c: Color, bg: Color) = c.copy(alpha = READ_ALPHA).compositeOver(bg)

    @Test
    fun `a read card's headline and summary pass contrast, light, dark and black`() {
        listOf(false, true).forEach { dark ->
            val scheme = dynamicColorScheme(seedColor = WhisperCobalt, isDark = dark, isAmoled = false)
            val backgrounds = if (dark) listOf(scheme.background, Color.Black) else listOf(scheme.background)
            backgrounds.forEach { bg ->
                val panel = faded(scheme.surfaceContainer, bg)
                listOf("headline" to scheme.onSurface, "summary" to scheme.onSurfaceVariant).forEach { (what, text) ->
                    val ratio = contrast(faded(text.compositeOver(scheme.surfaceContainer), bg), panel)
                    assertTrue("$what, dark=$dark, bg=$bg: $ratio", ratio >= 4.5)
                }
            }
        }
    }

    @Test
    fun `the picture fades to about half, further than the text`() {
        val picture = READ_ALPHA * PICTURE_FADE
        assertTrue(picture in 0.45f..0.55f)
        assertTrue(READ_ALPHA >= 0.75f)
    }
}
