package com.saulhdev.feeder

import com.saulhdev.feeder.ui.components.settleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** A title let go halfway (test t003-06) and a short page that gives (review 81). */
class TitleSettleTest {

    private val bar = File("src/main/java/com/saulhdev/feeder/ui/components/ViewWithActionBar.kt").readText()

    @Test
    fun `a half-open title goes to the nearer end`() {
        assertEquals(0f, settleTarget(-30f, -100f))
        assertEquals(-100f, settleTarget(-70f, -100f))
    }

    @Test
    fun `a title already at an end, or one that cannot move, stays`() {
        assertNull(settleTarget(0f, -100f))
        assertNull(settleTarget(-100f, -100f))
        assertNull(settleTarget(0f, 0f))
    }

    @Test
    fun `settling waits for the finger and does not depend on a fling`() {
        assertTrue(bar.contains("snapshotFlow { appBarState.heightOffset to fingerDown }"))
        assertTrue(bar.contains("PointerEventPass.Initial"))
        assertTrue(bar.contains("delay(SETTLE_DELAY_MS)"))
    }

    @Test
    fun `a page that cannot scroll still stretches`() {
        assertTrue(bar.contains("val stretch = rememberOverscrollEffect()"))
        assertTrue(bar.contains(".overscroll(stretch)"))
        assertTrue(bar.contains("stretch.applyToScroll(Offset(0f, left.y), source) { Offset.Zero }"))
        assertTrue(bar.contains("stretch.applyToFling("))
    }
}
