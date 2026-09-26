package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every image this app loads is bounded before it is decoded.
 *
 * Coil takes a decode size from whichever bound it can find — the layout
 * constraints, or an explicit size on the request — and falls back to the
 * picture's own dimensions when it finds neither. A feed photograph published
 * at 4000px, decoded whole to be drawn a few hundred wide, costs around fifty
 * times the memory it needs, on the one code path that runs while the reader
 * is scrolling.
 *
 * Nothing in the ImageLoader prevents this, though a comment there used to
 * claim otherwise. The bounds are in the composables, which means the
 * invariant is spread across five files and any new image is one modifier away
 * from breaking it — silently, because the picture looks identical either way
 * and only the allocation figures differ.
 *
 * So it is checked here instead: every image call site in the app, and the
 * bound it relies on.
 */
class ImageSizingTest {

    private fun read(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    /** A call site, as the span of source from its opening to its closing. */
    private fun callSites(source: String, opener: String): List<String> {
        val sites = mutableListOf<String>()
        var from = 0
        while (true) {
            val start = source.indexOf(opener, from)
            if (start < 0) break
            var depth = 0
            var i = source.indexOf('(', start)
            val open = i
            while (i < source.length) {
                when (source[i]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) break
                    }
                }
                i++
            }
            sites += source.substring(open, minOf(i + 1, source.length))
            from = start + opener.length
        }
        return sites
    }

    @Test
    fun `the feed cards are bounded by their layout`() {
        // The scroll path, and the one that matters most: these decode while
        // the reader is moving. A bound from the layout is preferred here
        // because the card already states its shape — an explicit request size
        // would be a second, less precise copy of the same number.
        val source = read("ui/overlay/ArticleCard.kt")
        val sites = callSites(source, "AsyncImage(")
        assertTrue("ArticleCard's images moved", sites.size == 4)

        var from = 0
        sites.forEachIndexed { index, site ->
            val start = source.indexOf(site, from)
            from = start + 1

            // fillMaxSize is a bound or it is nothing, depending entirely on
            // the parent: the constraint it fills is whatever it is given. The
            // hero image is written that way — it fills a Box that carries the
            // aspectRatio — so the shape it decodes to is stated one composable
            // up, and looking only at the call site would call it unbounded.
            val ownBound = site.contains("aspectRatio(") ||
                    Regex("""\.size\([^)]""").containsMatchIn(site)
            // The lead picture's Box is sized by leadPicture since C5: four by
            // three, capped at half the window, measured with fixed
            // constraints. Just as much a stated shape as aspectRatio.
            val parentBound = site.contains("fillMaxSize()") &&
                    source.substring(maxOf(0, start - 500), start).let {
                        it.contains("aspectRatio(") || it.contains(".leadPicture(")
                    }

            assertTrue(
                "feed image $index has no bound, so it decodes at the picture's own size",
                ownBound || parentBound,
            )
        }
    }

    @Test
    fun `the article body images carry a size on the request`() {
        // These cannot take one from the layout: fillMaxWidth with the height
        // left to the picture is an unbounded constraint, which is no bound.
        val sites = callSites(read("utils/HtmlToComposable.kt"), "AsyncImage(")
        assertTrue("the article body's images moved", sites.size == 3)
        sites.forEachIndexed { index, site ->
            assertTrue(
                "body image $index lost its request size and will decode whole",
                site.contains(".size(imageWidth)"),
            )
        }
    }

    @Test
    fun `the small round images are bounded too`() {
        val mark = callSites(read("ui/overlay/SourceMark.kt"), "AsyncImage(")
        assertTrue("the source icon moved", mark.size == 1)
        assertTrue("the source icon lost its size", mark[0].contains(".size(size)"))

        val contributor = read("ui/components/ContributorRow.kt")
        assertTrue(
            "the contributor photo lost its size",
            contributor.contains(".size(30.dp)"),
        )
    }

    @Test
    fun `no new image call sites have appeared unchecked`() {
        // The three tests above name their files. An image loaded somewhere
        // else is one nothing is watching, which is how this invariant would
        // come apart.
        val known = setOf(
            "ui/overlay/ArticleCard.kt",
            "ui/overlay/SourceMark.kt",
            "ui/components/ContributorRow.kt",
            "utils/HtmlToComposable.kt",
            "ui/pages/AboutPage.kt",
        )
        val found = File("src/main/java/com/saulhdev/feeder")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter {
                val text = it.readText()
                text.contains("AsyncImage(") || text.contains("rememberAsyncImagePainter")
            }
            .map { it.path.substringAfter("com/saulhdev/feeder/") }
            .toSet()
        assertTrue(
            "images are now loaded in a file no test bounds: ${found - known}",
            (found - known).isEmpty(),
        )
    }

    @Test
    fun `the decoded-image cache is big enough to be worth having`() {
        // A measured value, not a preference. The trace of a real scroll
        // showed decodes tracking non-memory hits exactly: every picture the
        // cache dropped was decoded again in full, at 28-36ms for a
        // full-width photograph. At 20% the cache held fewer than two screens
        // of cards, so scrolling back a little re-decoded everything.
        //
        // Pinned because it is a bare number in a builder: lowering it looks
        // like prudence and costs a decode per card.
        val app = read("NeoApp.kt")
        val fraction = Regex("""MEMORY_CACHE_FRACTION = ([0-9.]+)""")
            .find(app)?.groupValues?.get(1)?.toDouble()
        assertTrue("the memory cache fraction is gone", fraction != null)
        assertTrue(
            "the image cache is back below what the scroll trace showed it needs: $fraction",
            fraction!! >= 0.30,
        )
        // The other direction matters too. Images can always be fetched again;
        // this is the first thing to hurt under pressure on a smaller device.
        assertTrue("the image cache would crowd out everything else: $fraction", fraction <= 0.50)
        assertTrue(
            "the loader no longer uses the named fraction",
            app.contains("maxSizePercent(MEMORY_CACHE_FRACTION)"),
        )
    }

    @Test
    fun `the loader does not claim to do the sizing`() {
        val app = read("NeoApp.kt")
        val builder = app.substring(app.indexOf("override fun newImageLoader()"))
        assertTrue(
            "the loader now sets a size, so this test and its comment are stale",
            !Regex("""\n\s*\.size\(""").containsMatchIn(builder.take(1200)),
        )
    }
}
