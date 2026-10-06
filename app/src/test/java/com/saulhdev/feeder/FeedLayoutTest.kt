package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.FeedCardShape
import com.saulhdev.feeder.ui.overlay.FeedEmphasis
import com.saulhdev.feeder.ui.overlay.feedCardShape
import com.saulhdev.feeder.ui.overlay.feedLayoutIsGrid
import com.saulhdev.feeder.utils.LAYOUT_CARDS
import com.saulhdev.feeder.utils.LAYOUT_LIST
import com.saulhdev.feeder.utils.LAYOUT_MAGAZINE
import com.saulhdev.feeder.utils.LAYOUT_MOSAIC
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedLayoutTest {

    // These three used to assert the positional rule — a hero at 0 and every
    // eighth item after it. That rule is gone: which article gets the big
    // shape is now decided by weight, and this function only draws the
    // decision. The property worth pinning is that it draws it faithfully.

    @Test
    fun `cards draws whatever emphasis it is handed`() {
        assertEquals(
            FeedCardShape.Hero,
            feedCardShape(0, hasImage = true, layout = LAYOUT_CARDS, emphasis = FeedEmphasis.Large),
        )
        assertEquals(
            FeedCardShape.Card,
            feedCardShape(3, hasImage = true, layout = LAYOUT_CARDS, emphasis = FeedEmphasis.Medium),
        )
        assertEquals(
            FeedCardShape.Compact,
            feedCardShape(7, hasImage = true, layout = LAYOUT_CARDS, emphasis = FeedEmphasis.Small),
        )
    }

    @Test
    fun `position no longer decides the shape, only emphasis does`() {
        // The whole point of the change: the same article gets the same shape
        // wherever a sync happens to have put it.
        for (i in 0 until 32) {
            assertEquals(
                "index $i changed the shape",
                FeedCardShape.Hero,
                feedCardShape(i, hasImage = true, layout = LAYOUT_CARDS, emphasis = FeedEmphasis.Large),
            )
        }
    }

    @Test
    fun `an imageless article never gets an image-led shape, however well it scores`() {
        for (layout in listOf(LAYOUT_CARDS, LAYOUT_MAGAZINE)) {
            for (emphasis in FeedEmphasis.entries) {
                val shape = feedCardShape(0, hasImage = false, layout = layout, emphasis = emphasis)
                assertTrue(
                    "$layout gave $shape at $emphasis",
                    shape == FeedCardShape.Compact || shape == FeedCardShape.Text,
                )
            }
        }
    }

    @Test
    fun `list is text at every position, with or without an image`() {
        for (i in 0 until 16) {
            assertEquals(FeedCardShape.Text, feedCardShape(i, true, LAYOUT_LIST))
            assertEquals(FeedCardShape.Text, feedCardShape(i, false, LAYOUT_LIST))
        }
    }

    @Test
    fun `mosaic is tiles throughout, so no full-width row lands in a grid cell`() {
        for (i in 0 until 16) {
            assertEquals(FeedCardShape.Tile, feedCardShape(i, true, LAYOUT_MOSAIC))
            assertEquals(FeedCardShape.Tile, feedCardShape(i, false, LAYOUT_MOSAIC))
        }
    }

    @Test
    fun `magazine never goes compact when there is an image to show`() {
        for (i in 0 until 16) {
            assertEquals(FeedCardShape.Card, feedCardShape(i, true, LAYOUT_MAGAZINE))
        }
    }

    @Test
    fun `only mosaic asks for a grid`() {
        assertTrue(feedLayoutIsGrid(LAYOUT_MOSAIC))
        assertFalse(feedLayoutIsGrid(LAYOUT_CARDS))
        assertFalse(feedLayoutIsGrid(LAYOUT_MAGAZINE))
        assertFalse(feedLayoutIsGrid(LAYOUT_LIST))
    }

    @Test
    fun `an unknown layout falls back to cards rather than crashing`() {
        assertEquals(FeedCardShape.Hero, feedCardShape(0, true, "nonsense", FeedEmphasis.Large))
        assertFalse(feedLayoutIsGrid("nonsense"))
    }
}
