package com.saulhdev.feeder

import com.saulhdev.feeder.utils.IMAGE_DECODES_DEFAULT
import com.saulhdev.feeder.utils.IMAGE_DECODES_OLDER_DEVICE
import com.saulhdev.feeder.utils.imageDecodeLimit
import com.saulhdev.feeder.utils.imageDecodeSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageDecodeLimitTest {

    @Test
    fun `the tablet decodes two photos at a time`() {
        // Galaxy Tab S5e: Android 11, SDK 30.
        assertEquals(IMAGE_DECODES_OLDER_DEVICE, imageDecodeLimit(sdk = 30, lowRam = false))
        assertEquals(2, IMAGE_DECODES_OLDER_DEVICE)
    }

    @Test
    fun `a current phone keeps Coil's default`() {
        assertEquals(IMAGE_DECODES_DEFAULT, imageDecodeLimit(sdk = 31, lowRam = false))
        assertEquals(IMAGE_DECODES_DEFAULT, imageDecodeLimit(sdk = 37, lowRam = false))
        assertEquals(4, IMAGE_DECODES_DEFAULT)
    }

    @Test
    fun `a low-memory device counts as older whatever its version`() {
        assertEquals(IMAGE_DECODES_OLDER_DEVICE, imageDecodeLimit(sdk = 35, lowRam = true))
    }

    @Test
    fun `an entry-level phone on a current Android counts as older`() {
        val gb = 1024L * 1024 * 1024
        // A Galaxy A07: Android 15, no performance class, 4 GB (3.7 reported).
        assertEquals(IMAGE_DECODES_OLDER_DEVICE, imageDecodeLimit(sdk = 35, lowRam = false, performanceClass = 0, totalRam = 3_700 * gb / 1000))
        // The same with 6 GB, and a flagship that declares a class with less.
        assertEquals(IMAGE_DECODES_DEFAULT, imageDecodeLimit(sdk = 35, lowRam = false, performanceClass = 0, totalRam = 5_500 * gb / 1000))
        assertEquals(IMAGE_DECODES_DEFAULT, imageDecodeLimit(sdk = 35, lowRam = false, performanceClass = 34, totalRam = 4 * gb))
    }

    @Test
    fun `the report says which limit is in force`() {
        assertEquals("2 at once (older device)", imageDecodeSummary(2))
        assertEquals("4 at once", imageDecodeSummary(4))
    }

    @Test
    fun `the loader and the report both use the limit`() {
        val app = File("src/main/java/com/saulhdev/feeder/NeoApp.kt").readText()
        assertTrue(app.contains(".bitmapFactoryMaxParallelism(imageDecodeLimit(this))"))
        val report = File("src/main/java/com/saulhdev/feeder/utils/Diagnostics.kt").readText()
        assertTrue(report.contains("imageDecodeSummary(imageDecodeLimit(context))"))
    }
}
