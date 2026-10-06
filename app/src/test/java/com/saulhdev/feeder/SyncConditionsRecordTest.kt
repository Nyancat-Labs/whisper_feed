package com.saulhdev.feeder

import android.os.BatteryManager
import com.saulhdev.feeder.utils.PowerState
import com.saulhdev.feeder.utils.SyncEntry
import com.saulhdev.feeder.utils.SyncHistory
import com.saulhdev.feeder.utils.SyncLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Enough recorded to tell every combination apart: network and whether it
 * is metered, plugged in or not and whether charging or held, battery level,
 * Battery Saver and Doze - at the start of every sync and again at its end.
 */
class SyncConditionsRecordTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `the power reading names charging, held and on battery apart`() {
        fun p(plugged: Boolean, status: Int, pct: Int) = PowerState(plugged, "USB", status, pct).short()
        assertEquals("USB charging 64%", p(true, BatteryManager.BATTERY_STATUS_CHARGING, 64))
        assertEquals("a Pixel holding at 80% is still plugged in", "USB held 80%", p(true, BatteryManager.BATTERY_STATUS_NOT_CHARGING, 80))
        assertEquals("USB full 100%", p(true, BatteryManager.BATTERY_STATUS_FULL, 100))
        assertEquals("battery 41%", p(false, BatteryManager.BATTERY_STATUS_DISCHARGING, 41))
    }

    @Test
    fun `a run keeps the phone as it ended as well as began`() {
        val started = SyncHistory.start(emptyList(), SyncEntry(1, 0, "scheduled", "running", "Wi-Fi (unmetered), USB charging 70%"))
        val ended = SyncHistory.finish(started, 1, 30, "stopped: came off the charger", "mobile (metered), battery 69%")
        assertEquals("mobile (metered), battery 69%", ended.single().phoneAtEnd)
        assertEquals("Wi-Fi (unmetered), USB charging 70%", ended.single().phone)
        assertEquals(ended, SyncHistory.decode(SyncHistory.encode(ended)))
    }

    @Test
    fun `a line written before the end reading existed still reads`() {
        val old = "1\t9\tscheduled\tok\tplugged in, Wi-Fi"
        val read = SyncHistory.decode(old).single()
        assertEquals("ok", read.outcome)
        assertEquals("", read.phoneAtEnd)
    }

    @Test
    fun `the snapshot carries every condition`() {
        val device = source("utils/DeviceState.kt")
        val snapshot = device.substringAfter("internal fun deviceSnapshot(")
        assertTrue("network and metered", snapshot.contains("networkState(context).short()"))
        assertTrue("power and level", snapshot.contains("power.short()"))
        assertTrue("Battery Saver", snapshot.contains("isPowerSaveMode(context)"))
        assertTrue("Doze", snapshot.contains("isDeviceIdle(context)"))
        val log = source("utils/SyncLog.kt")
        assertEquals("read at the start and at the end", 2, Regex("""deviceSnapshot\(context\)""").findAll(log).count())
    }

    /**
     * Battery Saver pauses the schedule and the panel, and nothing the reader
     * asked for.
     */
    @Test
    fun `Battery Saver pauses automatic syncs only`() {
        assertEquals(setOf(SyncLog.ORIGIN_SCHEDULED, SyncLog.ORIGIN_PANEL, SyncLog.ORIGIN_OPENED), SyncLog.AUTOMATIC_ORIGINS)
        assertFalse(SyncLog.ORIGIN_PULL in SyncLog.AUTOMATIC_ORIGINS)
        val worker = source("manager/sync/FeedSyncer.kt").substringAfter("override suspend fun doWork()")
        val check = worker.indexOf("origin in SyncLog.AUTOMATIC_ORIGINS && isPowerSaveMode(applicationContext)")
        assertTrue("checked", check > 0)
        assertTrue("and before anything is fetched", check < worker.indexOf("syncFeeds("))
        assertTrue("recorded as skipped", worker.contains("\"skipped: Battery Saver\""))
    }

    /**
     * "Offline" and "blocked for Whisper" are told apart.
     *
     * Every sync one afternoon started on mobile data and ended "offline"
     * within half a minute. activeNetwork is null both when there is no
     * network and when Android is keeping this app off one - Data Saver, or
     * background data switched off - and the record said the first when the
     * pattern said the second.
     */
    @Test
    fun `a network Whisper is kept off is not reported as no network`() {
        val device = source("utils/DeviceState.kt")
        assertTrue(device.contains("if (anyNetworkUp(connectivity)) \"blocked for Whisper\" else \"offline\""))
        val snapshot = device.substringAfter("internal fun deviceSnapshot(")
        assertTrue("Data Saver is recorded", snapshot.contains("dataSaverState(context)"))
        // Named by effect: Android reports the app's own Background data
        // switch and Data Saver as the same thing.
        assertTrue(device.contains("RESTRICT_BACKGROUND_STATUS_ENABLED -> BACKGROUND_DATA_BLOCKED"))
        assertFalse(device.contains("-> \"Data Saver on\""))
        assertTrue("and whether Whisper was on screen", snapshot.contains("appVisibility()"))
        assertTrue("and the Restricted battery setting", snapshot.contains("isBackgroundRestricted(context)"))
        val report = source("utils/Diagnostics.kt")
        assertTrue(
            "the verdict names Data Saver when it is the reason",
            report.contains("Wi-Fi (Android blocks Whisper's mobile data in the background)"),
        )
        assertTrue(
            "but not a second time when Wi-Fi only already asked for Wi-Fi",
            report.contains("if (!onWifi && !wantsUnmetered && dataSaverState(context) == BACKGROUND_DATA_BLOCKED)"),
        )
    }

    @Test
    fun `a pull on the panel is told from a pull in the app`() {
        assertTrue(SyncLog.ORIGIN_PULL != SyncLog.ORIGIN_PULL_PANEL)
        val overlay = source("manager/service/OverlayView.kt")
        assertTrue(overlay.contains("syncAllFeeds(origin = SyncLog.ORIGIN_PULL_PANEL)"))
        assertFalse("the panel never takes the app's label", Regex("""syncAllFeeds\(\)""").containsMatchIn(overlay))
    }
}
