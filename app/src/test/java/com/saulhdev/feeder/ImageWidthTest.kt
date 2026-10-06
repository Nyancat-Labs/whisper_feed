package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An article's image is never requested at zero width.
 *
 * `maxImageWidth()` reads a `BoxWithConstraints`' measured width, and that is
 * zero before the box has been measured. Coil refuses it: `Dimension.Pixels`
 * carries `require(px > 0)` and throws
 * `IllegalArgumentException("px must be > 0.")` from inside the request
 * builder, before any fetch is attempted.
 *
 * So the request never runs, the error placeholder is drawn, and nothing says
 * why — which is how an article came to show Whisper's own placeholder while
 * the same article in a browser showed the photograph. It was reported as
 * "images failed to load in the built-in reader" and argued about from the
 * source for a day; it took the image trace counting failures to find, and the
 * counts were not small — fourteen in a single five-second window.
 */
class ImageWidthTest {

    private val html = File(
        "src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt"
    ).readText()

    @Test
    fun `every article image checks its width first`() {
        val widths = Regex("""val imageWidth = maxImageWidth\(\)""").findAll(html).count()
        // One of the three also checks the address it resolved to, so the
        // guards are not all spelt the same way. What has to hold is that each
        // measured width is tested, not that the test reads identically.
        val guards = Regex("""if \(imageWidth > 0[^\n]*?\) \{""").findAll(html).count()
        assertTrue("the article images no longer measure themselves", widths > 0)
        assertEquals(
            "an article image is requested without checking the width is usable",
            widths,
            guards,
        )
    }

    @Test
    fun `the guard comes before the request, not after it`() {
        val guard = Regex("""if \(imageWidth > 0[^\n]*?\) \{""")
        var from = 0
        repeat(Regex("""val imageWidth = maxImageWidth\(\)""").findAll(html).count()) {
            val measured = html.indexOf("val imageWidth = maxImageWidth()", from)
            val guarded = guard.find(html, measured)!!.range.first
            val request = html.indexOf("AsyncImage(", measured)
            assertTrue("a request is built before its width is checked", guarded < request)
            from = measured + 1
        }
    }

    /**
     * A resolved address, not an attribute that merely exists.
     *
     * `ImageCandidates.hasImage` tests the `srcset` and `src` attributes, not
     * what they resolve to: a srcset whose candidates all carry descriptors the
     * parser does not understand, beside an empty src, satisfies it and yields
     * nothing. The device report showed the result —
     * `IllegalArgumentException: Invalid URL ""`.
     */
    @Test
    fun `an article image is never requested with a blank address`() {
        assertTrue(
            "a blank resolved address is requested again",
            html.contains("src.isNotBlank()"),
        )
    }

    @Test
    fun `the width is still capped, and cannot go negative`() {
        // The cap is what stops a 4000px newspaper photograph being decoded at
        // full size; the floor is what stops Coil being handed a number it
        // refuses.
        assertTrue("the decode cap is gone", html.contains("coerceAtMost(2000)"))
        assertTrue("the width can go negative again", html.contains("coerceAtLeast(0)"))
    }
}
