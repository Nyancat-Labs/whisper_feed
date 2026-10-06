package com.saulhdev.feeder

import com.saulhdev.feeder.ui.onboarding.TOUR_STEPS
import com.saulhdev.feeder.ui.onboarding.TourMachine
import com.saulhdev.feeder.ui.onboarding.TourTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tour's step machine, which is the part with rules in it.
 *
 * The overlay is pixels and cannot be tested here; this can, and it is where
 * the failures that matter live — a tour that stalls pointing at a control
 * that is not on screen, or a counter promising stops that never arrive.
 */
class TourMachineTest {

    private val all = TourTarget.entries.toSet()

    @Test
    fun `it walks the steps in order`() {
        var at = TourMachine.first(TOUR_STEPS, all)!!
        val visited = mutableListOf(TOUR_STEPS[at].target)
        while (true) {
            at = TourMachine.next(TOUR_STEPS, at, all) ?: break
            visited += TOUR_STEPS[at].target
        }
        assertEquals(TOUR_STEPS.map { it.target }, visited)
    }

    @Test
    fun `a control that is not on screen is stepped over, not stalled on`() {
        // The glance row is a setting somebody can turn off, and the chips
        // need categories to exist. Neither is a reason to strand the tour.
        val present = all - TourTarget.Glance - TourTarget.Chips
        val first = TourMachine.first(TOUR_STEPS, present)!!
        assertEquals(TourTarget.Article, TOUR_STEPS[first].target)

        val second = TourMachine.next(TOUR_STEPS, first, present)!!
        assertEquals(TourTarget.Bookmarks, TOUR_STEPS[second].target)
    }

    @Test
    fun `the last step is the last one that will actually be shown`() {
        val present = setOf(TourTarget.Glance, TourTarget.Chips)
        val chips = TOUR_STEPS.indexOfFirst { it.target == TourTarget.Chips }
        assertTrue(TourMachine.isLast(TOUR_STEPS, chips, present))

        val glance = TOUR_STEPS.indexOfFirst { it.target == TourTarget.Glance }
        assertFalse(TourMachine.isLast(TOUR_STEPS, glance, present))
    }

    @Test
    fun `the tour ends rather than looping`() {
        val last = TOUR_STEPS.lastIndex
        assertNull(TourMachine.next(TOUR_STEPS, last, all))
    }

    @Test
    fun `nothing on screen means there is no tour to start`() {
        assertNull(TourMachine.first(TOUR_STEPS, emptySet()))
    }

    @Test
    fun `the counter promises only the steps that exist`() {
        // "2 of 6" when four of the six are absent is a lie the reader finds
        // out about at the end, which is the worst moment to find it out.
        val present = setOf(TourTarget.Chips, TourTarget.Filter)
        val chips = TOUR_STEPS.indexOfFirst { it.target == TourTarget.Chips }
        val filter = TOUR_STEPS.indexOfFirst { it.target == TourTarget.Filter }
        assertEquals(1 to 2, TourMachine.position(TOUR_STEPS, chips, present))
        assertEquals(2 to 2, TourMachine.position(TOUR_STEPS, filter, present))
    }

    @Test
    fun `six stops is the limit and the tour is at it`() {
        // Not a style rule. Every extra stop is another chance for somebody to
        // give up before the end, and the value of the tour is in finishing it.
        assertTrue("the tour has grown past six stops", TOUR_STEPS.size <= 6)
        assertEquals(TOUR_STEPS.size, TOUR_STEPS.map { it.target }.distinct().size)
    }
}
