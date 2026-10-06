package com.saulhdev.feeder

import com.saulhdev.feeder.utils.FeedTrace
import com.saulhdev.feeder.utils.OTHER_SURFACE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Image failures are still in the report an hour after they happened.
 *
 * The trace's counters are drained every five seconds and the report carries
 * the last two thousand log lines, so a failure reached the developer only if
 * it happened in roughly the last window before the reader pressed the button.
 * Reported as "I thought some images were going missing but I wasn't able to
 * catch it" — which is that gap, seen from the outside.
 *
 * The tally runs from launch and is never drained. It is an object rather than
 * an instance, so each test seeds what it needs rather than relying on the
 * order JUnit happens to run them in.
 */
class ImageTallyTest {

    private fun seed() {
        FeedTrace.imageFailed("IllegalArgumentException: px must be > 0.", "article")
        FeedTrace.imageFailed("http 404", OTHER_SURFACE)
    }

    @Test
    fun `the tally survives the window being drained`() {
        seed()
        // What the five-second report does, every five seconds.
        FeedTrace.reset()

        val tally = FeedTrace.imageTally()
        assertTrue("the failure is still counted: $tally", tally.contains("px must be > 0."))
        assertTrue("and so is the other one: $tally", tally.contains("http 404"))
    }

    @Test
    fun `failures are counted against the images that did arrive`() {
        seed()
        val tally = FeedTrace.imageTally()
        // A bare count of failures invites the wrong reading: nine is a broken
        // feature or a rounding error depending entirely on the denominator.
        assertTrue("says how many were asked for: $tally", tally.contains("requested"))
        assertTrue("and what share failed: $tally", tally.contains("%"))
    }

    @Test
    fun `the surface that asked is named`() {
        seed()
        val tally = FeedTrace.imageTally()
        assertTrue(
            "the reader's images are told apart from everything else: $tally",
            tally.contains("article:"),
        )
        assertTrue(tally.contains("$OTHER_SURFACE:"))
    }

    /**
     * Only the article body builds an explicit request, so it is the only one
     * that can carry a tag — and a tag rather than a parameter, because
     * `setParameter` derives a memory cache key from its value unless told
     * otherwise, and tagging every article image that way would give each
     * picture its own cache entry.
     */
    @Test
    fun `the reader tags its requests, and not with a parameter`() {
        val html = File(
            "src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt"
        ).readText()
        val tags = Regex("""\.tag\(ImageSurface::class\.java""").findAll(html).count()
        val builders = Regex("""ImageRequest\.Builder\(""").findAll(html).count()
        assertEquals("every request the reader builds", builders, tags)
        assertTrue("and there are some", builders >= 3)
        assertTrue(
            "a parameter would fragment the memory cache",
            !html.contains("setParameter(\"surface\""),
        )
    }

    @Test
    fun `the report carries it`() {
        val diagnostics = File(
            "src/main/java/com/saulhdev/feeder/utils/Diagnostics.kt"
        ).readText()
        assertTrue(diagnostics.contains("== Images =="))
        assertTrue(diagnostics.contains("FeedTrace.imageTally()"))
    }
}
