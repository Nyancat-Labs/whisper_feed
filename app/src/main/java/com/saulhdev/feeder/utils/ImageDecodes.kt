package com.saulhdev.feeder.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/**
 * How many photos may be decoded at the same time.
 *
 * Coil decodes up to four at once. On a current phone that is fine. On the
 * Galaxy Tab S5e (Android 11, two fast cores and six slow ones) a report
 * showed 18% of frames slow while photos were decoding and under 1% while
 * none were, with single WebP decodes taking 100-240ms. Four decodes at once
 * leave the screen's own thread competing for the two fast cores.
 *
 * So an older device decodes two at a time. Photos arrive a little later on a
 * fast fling; scrolling itself stays smoother. Anything newer keeps Coil's
 * default, so the phone this was all tuned on behaves exactly as before.
 */
const val IMAGE_DECODES_DEFAULT = 4

/** The limit on an older or low-memory device. */
const val IMAGE_DECODES_OLDER_DEVICE = 2

/** Android 11 and below counts as older: the version the slow tablet runs. */
const val OLDER_DEVICE_MAX_SDK = Build.VERSION_CODES.R

/**
 * Less memory than this, and no media performance class, counts as older too.
 * A phone sold with 4 GB reports about 3.7 of it, and one sold with 6 about 5.5.
 */
const val OLDER_DEVICE_MAX_RAM = 5L * 1024 * 1024 * 1024

/**
 * @param performanceClass Android's media performance class: the year of the
 *   standard the phone says it meets, or 0 for none. From Android 12; flagship
 *   and upper mid-range phones declare one, entry-level ones do not.
 * @param totalRam the phone's memory, in bytes.
 *
 * The version and the low-memory flag alone missed the phones that sell most.
 * A Galaxy A07 runs Android 15 on an entry-level chip and does not call itself
 * low on memory, so it got four decodes at once, the setting that made the
 * Tab S5e stutter. A phone with no performance class and under 5 GB is one of
 * those.
 */
fun imageDecodeLimit(
    sdk: Int,
    lowRam: Boolean,
    performanceClass: Int = 0,
    totalRam: Long = Long.MAX_VALUE,
): Int {
    val entryLevel = performanceClass == 0 && totalRam < OLDER_DEVICE_MAX_RAM
    return if (sdk <= OLDER_DEVICE_MAX_SDK || lowRam || entryLevel) IMAGE_DECODES_OLDER_DEVICE
    else IMAGE_DECODES_DEFAULT
}

fun imageDecodeLimit(context: Context): Int {
    val activity = context.getSystemService(ActivityManager::class.java)
    val memory = ActivityManager.MemoryInfo().also { activity?.getMemoryInfo(it) }
    return imageDecodeLimit(
        sdk = Build.VERSION.SDK_INT,
        lowRam = activity?.isLowRamDevice == true,
        performanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0,
        // Unknown when the service is not there: taken as plenty, as before.
        totalRam = memory.totalMem.takeIf { it > 0 } ?: Long.MAX_VALUE,
    )
}

/** The diagnostics line, so a report says which limit was in force. */
fun imageDecodeSummary(limit: Int): String =
    if (limit < IMAGE_DECODES_DEFAULT) "$limit at once (older device)" else "$limit at once"
