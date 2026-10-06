package com.saulhdev.feeder

import com.saulhdev.feeder.ui.overlay.passedIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an article counts as having been read past.
 *
 * This exists because of a bug worth not repeating: articles were marked the
 * moment they had been on screen long enough, so one being read carefully
 * faded out under the reader mid-sentence. Time on screen is the qualifier;
 * being scrolled past is the trigger.
 */
class ReadOnScrollTest {

    @Test
    fun `an article above everything on screen has been passed`() {
        val position = mapOf("a" to 2, "b" to 3)
        assertEquals(listOf("a"), passedIds(setOf("a", "b"), position, topmostVisible = 3))
    }

    @Test
    fun `an article still on screen has not been passed`() {
        // The bug in one line: this is the article being read, and it must not
        // be marked while the reader is still looking at it.
        val position = mapOf("reading-it" to 5)
        assertTrue(passedIds(setOf("reading-it"), position, topmostVisible = 5).isEmpty())
    }

    @Test
    fun `an article below the viewport has not been passed`() {
        // Scrolled back up, not read past. It keeps the time it earned and is
        // marked when the reader eventually goes by it.
        val position = mapOf("below" to 9)
        assertTrue(passedIds(setOf("below"), position, topmostVisible = 4).isEmpty())
    }

    @Test
    fun `nothing is passed when nothing is on screen`() {
        // An empty layout means the list is still being measured. Treating it
        // as "everything scrolled past" would mark the whole feed read on a
        // rotation.
        val position = mapOf("a" to 0, "b" to 1, "c" to 2)
        assertTrue(passedIds(setOf("a", "b", "c"), position, topmostVisible = null).isEmpty())
    }

    @Test
    fun `a run of articles passed together are all marked`() {
        val position = mapOf("a" to 0, "b" to 1, "c" to 2, "d" to 7)
        val ready = setOf("a", "b", "c", "d")
        assertEquals(setOf("a", "b", "c"), passedIds(ready, position, topmostVisible = 6).toSet())
    }

    @Test
    fun `an article that has left the feed is not passed`() {
        // The case the loop can now meet and could not before: it used to be
        // restarted by any change to the list, so an article in `ready` was
        // always still in the feed. Now the loop outlives the list, and an
        // article that is gone has no position to judge it by.
        assertTrue(passedIds(setOf("gone"), position = emptyMap(), topmostVisible = 6).isEmpty())
    }

    @Test
    fun `a position is read from the current feed, not from when it was ready`() {
        // A sync inserting a newer article pushes everything down by one. The
        // article at 2 is now at 3, and the answer has to follow it: a
        // remembered position would call it passed when it is on screen.
        val before = mapOf("a" to 2)
        val after = mapOf("a" to 3)
        assertEquals(listOf("a"), passedIds(setOf("a"), before, topmostVisible = 3))
        assertTrue(passedIds(setOf("a"), after, topmostVisible = 3).isEmpty())
    }
}
