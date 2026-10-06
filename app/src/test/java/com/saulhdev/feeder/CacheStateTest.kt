package com.saulhdev.feeder

import com.saulhdev.feeder.utils.FeedTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How full the decoded-image cache is, said plainly enough to act on.
 *
 * Raising the cache from 20% of available memory to 35% did not visibly move
 * the hit rate, and nothing in the report could say why. Two explanations fit
 * the same numbers and want opposite answers: the cache is full and still too
 * small, or it is not full and something else decides what is kept. The
 * figures that tell them apart were on the loader the whole time, unread.
 *
 * Bytes are printed as megabytes because that is the unit the decision is
 * made in — a full-width article photograph is about five of them, so "18MB
 * of 51" is immediately "three screens of cards" in a way that 18874368 is
 * not.
 */
class CacheStateTest {

    private fun state(used: Int, max: Int, entries: Int) =
        FeedTrace.CacheState(used, max, entries).toString()

    @Test
    fun `it reads as an occupancy`() {
        assertEquals(
            "cache 18/51MB (35%, 9 images)",
            state(18 * MB, 51 * MB, 9),
        )
    }

    @Test
    fun `a full cache is unmistakable`() {
        // The case that matters: full means evicting, and evicting means
        // every dropped picture is decoded again at about 28ms.
        assertTrue("a full cache does not report 100%", state(51 * MB, 51 * MB, 24).contains("100%"))
    }

    @Test
    fun `an empty cache does not divide by zero`() {
        // maxSize can be zero if the loader was built without a memory cache,
        // and a crash inside the diagnostics would take the report with it —
        // on the one run somebody was trying to capture.
        assertEquals("cache 0/0MB (0%, 0 images)", state(0, 0, 0))
    }

    @Test
    fun `partial megabytes round rather than mislead`() {
        // 0MB of 51 with entries in it would read as a bug in the report.
        val line = state(1536 * 1024, 51 * MB, 1)
        assertTrue("a small cache reads as empty: $line", line.startsWith("cache 2/51MB"))
    }

    private companion object {
        const val MB = 1_048_576
    }
}
