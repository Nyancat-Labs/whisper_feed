package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Leaving the feed does not mark its sources as broken.
 *
 * The per-feed sync caught `Throwable`, which includes
 * `CancellationException` — so closing the launcher panel mid-sync recorded a
 * failure against every feed still in flight. Three of those and a publisher
 * crosses the threshold into "Feeds that stopped working", accused of being
 * broken because the reader swiped away. A device report showed five sources
 * newly failing with "Job was cancelled" as the only reason in the log.
 *
 * Swallowing it is the other half of the same mistake: cancellation has to
 * propagate, or the coroutine that was cancelled carries on.
 */
class SyncCancellationTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    private val sync = read("manager/sync/RssLocalSync.kt")

    @Test
    fun `a cancelled feed is not recorded as a failure`() {
        val at = sync.indexOf("catch (e: CancellationException)")
        val counted = sync.indexOf("catch (e: Throwable)", at)
        assertTrue("cancellation is caught with everything else again", at > 0)
        assertTrue("there is no longer a separate catch for real failures", counted > at)

        val cancelled = sync.substring(at, counted)
        assertTrue(
            "a cancelled sync counts against the feed again",
            !cancelled.contains("recordFailure"),
        )
        assertTrue("cancellation is swallowed rather than propagated", cancelled.contains("throw e"))
    }

    @Test
    fun `a cancelled feed still stops looking like it is syncing`() {
        // The coroutine is already cancelled, so a suspending write would be
        // cancelled with it — leaving the feed marked as syncing for ever,
        // which is a spinner that never stops.
        val at = sync.indexOf("catch (e: CancellationException)")
        val cancelled = sync.substring(at, sync.indexOf("catch (e: Throwable)", at))
        assertTrue("the syncing flag is left set", cancelled.contains("setCurrentlySyncingOn"))
        // The call, not the word: the comment above it explains why
        // NonCancellable is there, so a substring match passes with the guard
        // deleted. That mistake has now been made twice in this suite — once
        // where "onImage" satisfied a search for "age" — and both times the
        // test went green against code that was wrong.
        assertTrue(
            "the flag is cleared on a cancelled coroutine, so the write is cancelled too",
            cancelled.contains("withContext(NonCancellable)"),
        )
    }

    @Test
    fun `a cancelled sync does not stamp the clock`() {
        // Nothing was fetched, so the feed is exactly as stale as it was.
        val at = sync.indexOf("catch (e: CancellationException)")
        val cancelled = sync.substring(at, sync.indexOf("catch (e: Throwable)", at))
        assertTrue("a cancelled sync claims to have updated", !cancelled.contains("lastSync"))
    }

    @Test
    fun `the outer handlers pass cancellation through too`() {
        listOf(
            "manager/sync/RssLocalSync.kt" to "Outer error",
            "manager/sync/FeedSyncer.kt" to "Failure during sync",
        ).forEach { (path, log) ->
            val text = read(path)
            val at = text.indexOf(log)
            assertTrue("$path stopped logging \"$log\"", at > 0)
            // The catch just before the failure handler must be the
            // cancellation one. Asked of the structure, not of a distance: a
            // character window broke the first time that block grew a comment.
            val before = text.substring(0, at)
            val failureCatch = before.lastIndexOf("catch (")
            val previousCatch = before.lastIndexOf("catch (", failureCatch - 1)
            assertTrue(
                "$path reports cancellation as a sync failure",
                previousCatch >= 0 &&
                    before.startsWith("catch (e: CancellationException)", previousCatch),
            )
        }
    }
}
