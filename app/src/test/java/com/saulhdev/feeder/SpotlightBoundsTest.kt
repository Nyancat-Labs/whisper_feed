package com.saulhdev.feeder

import androidx.compose.ui.geometry.Rect
import com.saulhdev.feeder.ui.onboarding.spotlightBounds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The spotlight ring stays on screen.
 *
 * A full-width card and the gear in the corner touch the edge of the display;
 * padded outwards, their ring ran off it and two sides of the box were lost.
 */
class SpotlightBoundsTest {

    private val width = 1080f
    private val height = 2400f

    @Test
    fun `a control in the middle is padded on every side`() {
        val lit = spotlightBounds(Rect(100f, 200f, 300f, 400f), 15f, 5f, width, height)
        assertEquals(Rect(85f, 185f, 315f, 415f), lit)
    }

    @Test
    fun `a full-width card keeps both sides of its ring on screen`() {
        val lit = spotlightBounds(Rect(0f, 800f, width, 1600f), 15f, 5f, width, height)
        assertEquals(5f, lit.left)
        assertEquals(width - 5f, lit.right)
        assertEquals(785f, lit.top)
    }

    @Test
    fun `a control in the corner keeps its ring off the edge`() {
        val lit = spotlightBounds(Rect(950f, 0f, width, 130f), 15f, 5f, width, height)
        assertEquals(5f, lit.top)
        assertEquals(width - 5f, lit.right)
    }
}
