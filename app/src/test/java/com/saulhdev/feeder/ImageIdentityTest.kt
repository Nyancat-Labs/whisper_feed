package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ImageTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pictures are fetched as something a server will serve.
 *
 * The image loader was built with the network guards and nothing else, so
 * every picture went out as `okhttp/5.5.0` with no Accept header. HttpIdentity
 * already said why that matters — "a consent page, a stripped variant, a
 * challenge, or a flat 403" — and already defined two identities. Neither was
 * applied here, which is the same omission the network guards had before they
 * were given one name and one test.
 *
 * What it asks for is a measurement, not a preference. A trace of a real
 * scroll timed one AVIF image at 87.9ms against 29.1ms for the JPEGs beside
 * it, because Android decodes AVIF through a software AV1 codec. A
 * content-negotiating CDN serves the best format the client claims, so
 * claiming AVIF is asking for the slow one.
 */
class ImageIdentityTest {

    private val identity = File(
        "src/main/java/com/saulhdev/feeder/utils/HttpIdentity.kt"
    ).readText()
    private val app = File("src/main/java/com/saulhdev/feeder/NeoApp.kt").readText()

    @Test
    fun `the image loader carries an identity`() {
        assertTrue(
            "the image loader fetches as okhttp again",
            app.contains(".asImageFetcher()"),
        )
        // The guards stay. They are a separate concern and were here first.
        assertTrue("the image loader lost its network guards", app.contains(".onlyPublicHttps()"))
    }

    @Test
    fun `images do not advertise avif`() {
        val accept = Regex("""IMAGE_ACCEPT\s*[:=][\s\S]{0,200}?"([^"]+(?:"\s*\+\s*"[^"]+)*)"""")
            .find(identity)?.value.orEmpty()
        assertTrue("IMAGE_ACCEPT is gone", accept.isNotEmpty())
        assertTrue(
            "images ask for avif, which this device decodes in software at 3x the cost",
            !accept.contains("image/avif"),
        )
        assertTrue("images no longer accept anything as a fallback", accept.contains("*/*"))

        // Preference by measurement: across one scroll, webp averaged 45.8ms
        // to decode against jpeg's 24.4ms and png's 8.4ms. Listing webp first
        // — which this did — moved publishers onto the slower format, and the
        // feeds served almost none before the header existed.
        val jpeg = accept.indexOf("image/jpeg")
        val webp = accept.indexOf("image/webp")
        assertTrue("images no longer ask for jpeg", jpeg > 0)
        assertTrue("images no longer accept webp at all", webp > 0)
        assertTrue("webp is preferred over jpeg again, and decodes twice as slowly", jpeg < webp)
        assertTrue(
            "webp is no longer asked for at a lower quality value, so a CDN may still prefer it",
            Regex("""image/webp;q=0\.[0-9]""").containsMatchIn(accept),
        )
    }

    @Test
    fun `the article identity still asks for avif, because a page is not an image`() {
        // HTML_ACCEPT is what a browser sends and exists to get the same
        // document a browser would. Trimming it to match IMAGE_ACCEPT would be
        // applying an image decision to a page request.
        val htmlAccept = identity.substringAfter("HTML_ACCEPT").substringBefore("FEED_ACCEPT")
        assertTrue("HTML_ACCEPT stopped looking like a browser", htmlAccept.contains("image/avif"))
    }

    @Test
    fun `a failed image is counted, and named without naming the page`() {
        // Failures were invisible: the trace counted successes and decodes, so
        // an image that never arrived left no mark at all. When article images
        // came back as placeholders the report could say nothing about them.
        assertEquals("http 403", ImageTrace.reasonOf(RuntimeException("HTTP 403 Forbidden")))
        assertEquals("http 404", ImageTrace.reasonOf(java.io.IOException("... code=404 ...")))
        assertEquals("SocketTimeoutException", ImageTrace.reasonOf(java.net.SocketTimeoutException()))
        assertEquals("unknown", ImageTrace.reasonOf(null))
    }

    @Test
    fun `a failure reason never carries the address`() {
        // These lines go into a file the reader sends to a stranger, and a URL
        // names the publisher and usually the article.
        val reason = ImageTrace.reasonOf(
            java.io.IOException("Failed for https://cdn.example.com/secret/article-42.jpg")
        )
        assertTrue("the failure reason leaked the address: $reason", !reason.contains("example.com"))
    }
}
