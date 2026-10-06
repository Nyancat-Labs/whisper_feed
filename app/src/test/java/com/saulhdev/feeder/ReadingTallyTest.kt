package com.saulhdev.feeder

import com.saulhdev.feeder.data.repository.READ_CAP_MS
import com.saulhdev.feeder.data.repository.appliedReading
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tally records what was applied, not what was asked for.
 *
 * The reading clock ticks forward every second and flushes every five, for as
 * long as an article is on screen. An article's own `readMs` is capped, so
 * past the cap those flushes change nothing — but the tally is a separate
 * running total, and a tally that adds the *request* would keep climbing for
 * an article left open on a desk. Over months that is a reading total that
 * drifts upward on its own, which is the kind of wrong nobody catches by
 * looking at it.
 */
class ReadingTallyTest {

    @Test
    fun `an ordinary tick is applied in full`() {
        assertEquals(5_000L, appliedReading(before = 30_000L, requested = 5_000L, cap = READ_CAP_MS))
    }

    @Test
    fun `a tick that crosses the cap is applied only up to it`() {
        // The article is 2s short of the cap and a 5s tick arrives.
        val before = READ_CAP_MS - 2_000L
        assertEquals(2_000L, appliedReading(before, requested = 5_000L, cap = READ_CAP_MS))
    }

    @Test
    fun `an article already at the cap applies nothing`() {
        // The case that matters: an article left open after the cap is
        // reached goes on flushing forever and must stop counting.
        assertEquals(0L, appliedReading(READ_CAP_MS, requested = 5_000L, cap = READ_CAP_MS))
    }

    @Test
    fun `an article somehow past the cap does not go negative`() {
        // A cap lowered in a later release would leave stored totals above it.
        // Subtracting would take time off the chart for reading that happened.
        assertEquals(0L, appliedReading(READ_CAP_MS + 60_000L, 5_000L, READ_CAP_MS))
    }

    @Test
    fun `a zero or negative tick applies nothing`() {
        assertEquals(0L, appliedReading(before = 1_000L, requested = 0L, cap = READ_CAP_MS))
        assertEquals(0L, appliedReading(before = 1_000L, requested = -5_000L, cap = READ_CAP_MS))
    }
}
