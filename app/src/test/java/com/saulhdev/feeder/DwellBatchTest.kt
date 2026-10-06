package com.saulhdev.feeder

import com.saulhdev.feeder.data.repository.DwellBatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dwell batch, which exists to stop a scroll re-running the feed query.
 *
 * Every write to the Article table invalidates it, and the query returns five
 * hundred whole rows including their full text. One write per article leaving
 * the screen was measured in the field at 100–200MB collected per cycle
 * against a 256MB heap, every six to eight seconds, for as long as the reader
 * kept scrolling.
 *
 * What the batch must get right is all timing, and timing is where a change
 * like this quietly fails: a window that keeps being pushed back never fires,
 * and a drain ordered after the write loses exactly the articles the reader
 * was looking at. Virtual time makes both of those ordinary assertions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DwellBatchTest {

    @Test
    fun `nothing is written before the window is up`() = runTest {
        val written = mutableListOf<Map<String, Long>>()
        val batch = DwellBatch(this, 5_000L) { written += it }

        batch.add("a", 500L)
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue("wrote before the window closed", written.isEmpty())

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(1, written.size)
        assertEquals(mapOf("a" to 500L), written[0])
    }

    @Test
    fun `increments for one article add up into a single row`() = runTest {
        val written = mutableListOf<Map<String, Long>>()
        val batch = DwellBatch(this, 5_000L) { written += it }

        repeat(4) { batch.add("a", 250L) }
        advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(1, written.size)
        assertEquals(
            "four sightings of one article must be one write of their total",
            mapOf("a" to 1_000L),
            written[0],
        )
    }

    @Test
    fun `a continuous scroll still flushes on schedule`() = runTest {
        // The failure this rules out: a window restarted by each new increment
        // never expires while the reader keeps scrolling, which is the case
        // the batch exists for. It would look like batching and behave like a
        // leak.
        val written = mutableListOf<Map<String, Long>>()
        val batch = DwellBatch(this, 5_000L) { written += it }

        // An article leaving the screen every half second for fifteen seconds.
        repeat(30) {
            batch.add("article-$it", 500L)
            advanceTimeBy(500L)
            runCurrent()
        }

        assertTrue(
            "a scroll that never pauses never flushed: ${written.size} flushes",
            written.size >= 2,
        )
        val rows = written.sumOf { it.size }
        assertTrue("wrote $rows rows across ${written.size} flushes", rows > 0)
        assertTrue(
            "batching achieved nothing: $rows rows in ${written.size} writes",
            written.size < rows,
        )
    }

    @Test
    fun `an increment arriving mid-write joins the next batch`() = runTest {
        // Drain-then-write, not write-then-clear. The difference only shows
        // when the two overlap, and what it costs is the articles on screen at
        // the moment of the flush.
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val written = mutableListOf<Map<String, Long>>()
        val batch = DwellBatch(this, 5_000L) { entries ->
            written += entries
            if (written.size == 1) {
                started.complete(Unit)
                release.await()
            }
        }

        batch.add("first", 100L)
        advanceTimeBy(5_001L)
        runCurrent()
        started.await()

        // Lands while the first write is still in flight.
        launch { batch.add("second", 200L) }
        runCurrent()
        release.complete(Unit)
        runCurrent()

        advanceTimeBy(5_001L)
        runCurrent()

        assertEquals(2, written.size)
        assertEquals(mapOf("first" to 100L), written[0])
        assertEquals(
            "the increment that arrived during the write was lost",
            mapOf("second" to 200L),
            written[1],
        )
    }

    @Test
    fun `an explicit flush writes immediately and empties the batch`() = runTest {
        val written = mutableListOf<Map<String, Long>>()
        val batch = DwellBatch(this, 5_000L) { written += it }

        batch.add("a", 300L)
        assertEquals(1, batch.flush())
        assertEquals(listOf(mapOf("a" to 300L)), written)

        // The timer still fires, and must find nothing left to write rather
        // than writing the same milliseconds a second time — dwell is an
        // increment, so a duplicate flush is a double count.
        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals("the timer wrote the same increments again", 1, written.size)
    }

    @Test
    fun `flushing nothing writes nothing`() = runTest {
        var calls = 0
        val batch = DwellBatch(this, 5_000L) { calls++ }
        assertEquals(0, batch.flush())
        assertEquals("an empty flush still opened a transaction", 0, calls)
    }

    @Test
    fun `a zero or negative increment is ignored`() = runTest {
        var calls = 0
        val batch = DwellBatch(this, 5_000L) { calls++ }
        batch.add("a", 0L)
        batch.add("b", -5L)
        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(0, calls)
    }
}
