package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ImageTrace
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The format label the image trace counts by.
 *
 * It comes from a Content-Type header, which is to say from a publisher's
 * server, so it arrives in whatever shape that server felt like sending:
 * absent, with a charset stapled on, capitalised, or one of the x- spellings
 * that predate a format being registered. Every one of those is a separate
 * bucket in the report if it is not normalised, and a count split four ways
 * across spellings of the same format answers nothing.
 *
 * The AVIF figure is the one this exists to produce, so getting `image/avif`
 * down to `avif` is not cosmetic — it is the measurement.
 */
class ImageTraceTest {

    private fun label(mimeType: String?) = ImageTrace.shortFormat(mimeType)

    @Test
    fun `the ordinary formats reduce to their names`() {
        assertEquals("avif", label("image/avif"))
        assertEquals("jpeg", label("image/jpeg"))
        assertEquals("png", label("image/png"))
        assertEquals("webp", label("image/webp"))
        assertEquals("heic", label("image/heic"))
    }

    @Test
    fun `a charset or other parameter is dropped`() {
        // Servers do send this on images, and "avif" and "avif;charset=binary"
        // counted separately is two rows saying half a thing each.
        assertEquals("avif", label("image/avif;charset=binary"))
        assertEquals("jpeg", label("image/jpeg; charset=utf-8"))
    }

    @Test
    fun `case does not make a second bucket`() {
        assertEquals("avif", label("IMAGE/AVIF"))
        assertEquals("jpeg", label("Image/JPEG"))
    }

    @Test
    fun `the x- spellings fold into the registered ones`() {
        assertEquals("icon", label("image/x-icon"))
        assertEquals("png", label("image/x-png"))
    }

    @Test
    fun `a missing or unusable type is named rather than blank`() {
        // A blank label would leave a row in the report with a count and no
        // name, which reads as a bug in the report rather than a fact about
        // the server.
        assertEquals("unknown", label(null))
        assertEquals("unknown", label(""))
        assertEquals("unknown", label("   "))
        assertEquals("unknown", label("image/"))
    }

    @Test
    fun `a type with no slash is kept whole`() {
        // Malformed, but it is what the server said, and inventing "unknown"
        // for it would hide a real answer behind a generic one.
        assertEquals("octet-stream", label("octet-stream"))
    }
}
