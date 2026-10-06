package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ImageTrace
import com.saulhdev.feeder.utils.dropRepeatedImages
import com.saulhdev.feeder.utils.ensureLeadImage
import com.saulhdev.feeder.utils.isTooSmallToStretch
import com.saulhdev.feeder.utils.usableImageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup
import java.io.File

/**
 * The second device report's image failures, each named and each closed.
 *
 * 66 of 91 failures read `other: Invalid URL ""`. The real message was
 * OkHttp's `Invalid URL host: ""` — the report's sanitiser had taken `host:`
 * for an address — and the cause was a picture stored with no host at all,
 * which `java.net.URL` accepts and OkHttp refuses, every time the card
 * scrolls into view.
 */
class ImageAddressTest {

    @Test
    fun `an address with no host is no picture`() {
        assertNull("one slash", usableImageUrl("https:/wp-content/x.jpg"))
        assertNull("three slashes", usableImageUrl("https:///x.jpg"))
        assertNull("scheme only", usableImageUrl("https://"))
        assertNull("relative", usableImageUrl("/wp-content/x.jpg"))
        assertNull("protocol-relative", usableImageUrl("//cdn.example.com/x.jpg"))
        assertNull("blank", usableImageUrl("   "))
        assertNull(usableImageUrl(null))
    }

    @Test
    fun `an ordinary address is kept exactly as it was`() {
        val url = "https://cdn.example.com/a/b.jpg?w=1200"
        assertEquals(url, usableImageUrl(url))
        assertEquals("http://cdn.example.com/a.jpg", usableImageUrl("http://cdn.example.com/a.jpg"))
        assertEquals("HTTPS://CDN.EXAMPLE.COM/A.JPG", usableImageUrl("HTTPS://CDN.EXAMPLE.COM/A.JPG"))
    }

    @Test
    fun `cards and weighting read the checked address`() {
        val card = File("src/main/java/com/saulhdev/feeder/ui/overlay/ArticleCard.kt").readText()
        val weight = File("src/main/java/com/saulhdev/feeder/ui/overlay/FeedWeight.kt").readText()
        assertFalse("a card reads the raw column", card.contains("article.imageUrl"))
        assertFalse(
            "weighting judges an article by a picture its card will not draw",
            weight.contains("article.imageUrl"),
        )
        val article = File("src/main/java/com/saulhdev/feeder/data/db/models/Article.kt").readText()
        assertTrue("checked on the way in too", article.contains("imageUrl = usableImageUrl("))
    }

    @Test
    fun `the report keeps the word that says what was missing`() {
        assertEquals(
            "IllegalArgumentException: Invalid URL host: \"\"",
            ImageTrace.reasonOf(IllegalArgumentException("Invalid URL host: \"\"")),
        )
        // Addresses still go.
        val reason = ImageTrace.reasonOf(IllegalStateException("failed https://example.com/a.png"))
        assertFalse(reason.contains("example"))
    }

    /** Gear Patrol: the swap happened and the srcset quietly overrode it. */
    @Test
    fun `pointing the body at the cached copy removes its srcset`() {
        val body = Jsoup.parse(
            "<img src=\"https://gp.example/Lead-1.jpg?w=1920\" " +
                "srcset=\"https://gp.example/Lead-1.jpg 2400w, https://gp.example/Lead-1.jpg?resize=650 650w\" " +
                "sizes=\"100vw\"><p>x</p>",
        ).body()
        ensureLeadImage(body, "https://gp.example/Lead-1.webp")
        val img = body.selectFirst("img")!!
        assertEquals("https://gp.example/Lead-1.webp", img.attr("src"))
        assertFalse("srcset is read first; left in place it wins", img.hasAttr("srcset"))
        assertFalse(img.hasAttr("sizes"))
    }

    /** The Verge's byline card: one photograph, two sizes, shown twice. */
    @Test
    fun `a picture already shown is not shown again`() {
        val body = Jsoup.parse(
            "<img src=\"https://cdn.example/Emma.jpg?w=96\">" +
                "<p>Emma Roth</p>" +
                "<img srcset=\"https://cdn.example/Emma.jpg?w=48 1x, https://cdn.example/Emma.jpg?w=96 2x\">" +
                "<img src=\"https://cdn.example/other.jpg\">",
        ).body()
        dropRepeatedImages(body)
        val left = body.select("img").map { it.attr("src").ifBlank { "srcset-only" } }
        assertEquals(listOf("https://cdn.example/Emma.jpg?w=96", "https://cdn.example/other.jpg"), left)
    }

    @Test
    fun `an image with no address is not taken for a repeat`() {
        val body = Jsoup.parse("<img alt=\"a\"><img alt=\"b\">").body()
        dropRepeatedImages(body)
        assertEquals(2, body.select("img").size)
    }

    /** How-To Geek's and The Verge's avatars, blown up to the whole column. */
    @Test
    fun `a small picture is not stretched past twice its size`() {
        assertTrue("a 96px avatar in a 1000px column", isTooSmallToStretch(96, 1000))
        assertFalse("a photograph that fills it", isTooSmallToStretch(1000, 1000))
        assertFalse("just inside twice", isTooSmallToStretch(500, 1000))
        assertFalse("not decoded yet: full width, never start small", isTooSmallToStretch(0, 1000))
        assertFalse("unmeasured column", isTooSmallToStretch(96, 0))
    }

    /**
     * The report is most wanted when the app is struggling, which is when the
     * database is busiest — so no one section may hold the rest hostage, and
     * a tap must say something straight away.
     */
    @Test
    fun `the diagnostics report cannot be held up by a busy database`() {
        val diagnostics = File("src/main/java/com/saulhdev/feeder/utils/Diagnostics.kt").readText()
        val collect = diagnostics.substringAfter("suspend fun collect(")
            .substringBefore("private fun readOwnLogcat")
        val timeouts = Regex("""withTimeout\(SECTION_TIMEOUT_MS\)""").findAll(collect).count()
        val repositories = Regex("""get<\w+Repository>\(\)""").findAll(collect).count()
        assertTrue("every database section is bounded", timeouts >= 3)
        assertTrue("and there are database sections to bound", repositories >= 3)

        val prefs = File("src/main/java/com/saulhdev/feeder/data/content/FeedPreferences.kt").readText()
        assertEquals(
            "both buttons announce themselves and refuse a second run",
            2,
            Regex("""if \(!Diagnostics\.begin\(context\)\) return@StringPref""").findAll(prefs).count(),
        )
    }
}
