/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.utils

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Process
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo

/**
 * What the phone is doing, as far as a sync cares: power and network.
 *
 * Shared by the diagnostics report, which describes the phone now, and the
 * sync record, which describes it at the moment each sync started. One reading
 * for both, so the two can never disagree about what "plugged in" means.
 */

/**
 * Plugged in or not, charging or held, and the level.
 *
 * Read from the sticky battery broadcast, which needs no permission and no
 * receiver - it hands back the last state the system announced.
 */
internal class PowerState(
    val pluggedIn: Boolean,
    val source: String,
    val status: Int,
    val percent: Int,
) {
    /** Roughly the system's own "battery low": under 15% and on battery. */
    val low: Boolean get() = !pluggedIn && percent in 0 until LOW_BATTERY_PERCENT

    fun describe(): String {
        val level = if (percent >= 0) "battery $percent%" else "battery unknown"
        if (!pluggedIn) return "on battery, $level"
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            // Plugged in and not taking charge: adaptive charging or a charge
            // limit holding it. Named, because it reads as "unplugged" to
            // anything that only asks whether it is charging.
            else -> "plugged in but held, not charging"
        }
        return "plugged in ($source), $state, $level"
    }

    /** The sync record's version: "USB charging 64%", "battery 41%", "USB held 80%". */
    fun short(): String {
        val level = if (percent >= 0) "$percent%" else "?%"
        if (!pluggedIn) return "battery $level"
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            else -> "held"
        }
        return "$source $state $level"
    }
}

/** Where Android calls the battery low, near enough; the exact line is the device's. */
private const val LOW_BATTERY_PERCENT = 15

internal fun powerState(context: Context): PowerState = runCatching {
    val battery = ContextCompat.registerReceiver(
        context,
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    PowerState(
        pluggedIn = plugged != 0,
        source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "mains"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "other"
        },
        status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1,
        percent = if (level >= 0 && scale > 0) level * 100 / scale else -1,
    )
}.getOrDefault(PowerState(pluggedIn = false, source = "unknown", status = -1, percent = -1))

internal fun isUnmetered(context: Context): Boolean = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
}.getOrDefault(false)

/**
 * Whether a background sync started now would lose its network: off Wi-Fi,
 * with Android keeping Whisper's mobile data for when it is on screen.
 *
 * A report showed eight scheduled syncs in half an hour on mobile data, each
 * cut off within seconds of Whisper leaving the screen and each retried - all
 * of the waking and none of the articles. Asked before a run, so the run
 * never starts. See [dataSaverState] for why this cannot say which setting
 * is responsible.
 */
internal fun backgroundMobileDataBlocked(context: Context): Boolean =
    !isUnmetered(context) && restrictsBackground(backgroundStatus(context))

/** Android's answer, or "not restricted" when it cannot be asked. */
internal fun backgroundStatus(context: Context): Int = runCatching {
    context.getSystemService(ConnectivityManager::class.java).restrictBackgroundStatus
}.getOrDefault(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED)

/** Only the plain "restricted": exempt counts as allowed. */
internal fun restrictsBackground(status: Int): Boolean =
    status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED

/**
 * Bytes Whisper has received since the phone started, or null where Android
 * does not count per app. Read before and after a run; see [bytesSince].
 */
internal fun receivedBytes(): Long? = runCatching {
    TrafficStats.getUidRxBytes(Process.myUid())
}.getOrNull()?.takeIf { it >= 0 }

/** What arrived since [before]; null if either reading is missing or it went backwards. */
internal fun bytesSince(before: Long?): Long? {
    val after = receivedBytes() ?: return null
    return before?.let { after - it }?.takeIf { it >= 0 }
}

/**
 * Whether Whisper has a network it may use right now: one Android is not
 * keeping it off. False offline, in a tunnel, or with the background block
 * on; true when it cannot be asked, so nothing is excused by accident.
 */
internal fun whisperHasNetwork(context: Context): Boolean = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}.getOrDefault(true)

/** WorkManager's stop reason, in words. */
internal fun stopReasonName(reason: Int): String = when (reason) {
    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelled by the app"
    WorkInfo.STOP_REASON_PREEMPT -> "pre-empted by other work"
    WorkInfo.STOP_REASON_TIMEOUT -> "ran out of time"
    WorkInfo.STOP_REASON_DEVICE_STATE -> "device state changed"
    WorkInfo.STOP_REASON_CONSTRAINT_BATTERY_NOT_LOW -> "battery went low"
    WorkInfo.STOP_REASON_CONSTRAINT_CHARGING -> "came off the charger"
    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "network changed or dropped"
    WorkInfo.STOP_REASON_CONSTRAINT_DEVICE_IDLE -> "device stopped being idle"
    WorkInfo.STOP_REASON_CONSTRAINT_STORAGE_NOT_LOW -> "storage ran low"
    WorkInfo.STOP_REASON_QUOTA -> "out of background quota"
    WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "background use restricted"
    WorkInfo.STOP_REASON_APP_STANDBY -> "app put in standby"
    WorkInfo.STOP_REASON_USER -> "stopped by the user"
    WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "system busy"
    WorkInfo.STOP_REASON_UNKNOWN -> "no reason given"
    else -> "reason code $reason"
}

/**
 * Which network the phone is on, and whether it is metered.
 *
 * Both, because WorkManager's "unmetered" is not the same question as "Wi-Fi":
 * a metered hotspot is Wi-Fi, and an unlimited mobile plan can report itself
 * unmetered. The sync switch is about the second, the reader thinks in terms
 * of the first, and the record keeps them apart so neither is mistaken for
 * the other.
 */
internal class NetworkState(val kind: String, val metered: Boolean?) {
    fun short(): String = when (metered) {
        null -> kind
        true -> "$kind (metered)"
        false -> "$kind (unmetered)"
    }
}

internal fun networkState(context: Context): NetworkState = runCatching {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        ?: return@runCatching NetworkState(
            if (anyNetworkUp(connectivity)) "blocked for Whisper" else "offline",
            null,
        )
    val kind = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "other network"
    }
    NetworkState(kind, !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
}.getOrDefault(NetworkState("unknown", null))

/**
 * Whether the phone has a working network that Whisper is being kept off.
 *
 * `activeNetwork` is null in two very different cases: no network at all, and
 * a network Android will not let this app use - Data Saver, or background data
 * switched off for the app, both cut a backgrounded app off from mobile data
 * while leaving it working for everything in the foreground. Every sync one
 * afternoon started on mobile data and ended "offline" within half a minute,
 * which is the second case wearing the first one's name. Asking whether any
 * network with internet exists tells them apart.
 */
@Suppress("DEPRECATION") // allNetworks: the one call that sees past the block.
private fun anyNetworkUp(connectivity: ConnectivityManager): Boolean =
    connectivity.allNetworks.any { network ->
        connectivity.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

/** What [dataSaverState] says when Android keeps Whisper off mobile data in the background. */
internal const val BACKGROUND_DATA_BLOCKED = "background mobile data blocked"

/**
 * Whether Android lets Whisper use mobile data while it is in the background.
 *
 * This read "Data Saver on" until a reader with Data Saver switched off saw
 * it on nearly every line. Android answers the same "restricted" for two
 * different settings: Data Saver for the whole phone, and this app's own
 * "Background data" switch under Mobile data - and the second is checked
 * first. Nothing an app can call tells the two apart, so the line names the
 * effect, which is what matters to a sync, rather than guessing the cause.
 */
internal fun dataSaverState(context: Context): String = runCatching {
    when (context.getSystemService(ConnectivityManager::class.java).restrictBackgroundStatus) {
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> BACKGROUND_DATA_BLOCKED
        ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "Data Saver on, Whisper exempt"
        else -> "background mobile data allowed"
    }
}.getOrDefault("background mobile data unknown")

/**
 * Whether Whisper is on screen, running something the reader can see, or in
 * the background - which is what decides whether Data Saver lets it use
 * mobile data at that moment.
 */
internal fun appVisibility(): String = runCatching {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    when {
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "on screen"
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "visible"
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "background, working"
        else -> "background"
    }
}.getOrDefault("unknown")

/**
 * Whether Whisper is in the foreground: the app open, or its launcher panel
 * showing, so Android lets it use mobile data even with background data off.
 * The same question [appVisibility] answers "on screen" to.
 */
internal fun whisperOnScreen(): Boolean = runCatching {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}.getOrDefault(false)

/** The battery setting "Restricted" for Whisper, which holds background work back. */
internal fun isBackgroundRestricted(context: Context): Boolean = runCatching {
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P &&
        context.getSystemService(ActivityManager::class.java).isBackgroundRestricted
}.getOrDefault(false)

/** Whether Battery Saver is on. See FeedSyncer for what it pauses. */
internal fun isPowerSaveMode(context: Context): Boolean = runCatching {
    context.getSystemService(PowerManager::class.java).isPowerSaveMode
}.getOrDefault(false)

/** Whether the phone is dozing: screen off, still, and deferring background work. */
internal fun isDeviceIdle(context: Context): Boolean = runCatching {
    context.getSystemService(PowerManager::class.java).isDeviceIdleMode
}.getOrDefault(false)

/**
 * Everything a sync's conditions depend on, read at one moment.
 *
 * The record keeps one of these at the start of every sync and another at
 * its end, so a run that began on the charger and on Wi-Fi and ended on
 * battery and mobile data says so - which is the combination that shows
 * whether a constraint was honoured or merely true when the run began.
 */
internal fun deviceSnapshot(context: Context): String {
    val power = powerState(context)
    val flags = buildList {
        if (isPowerSaveMode(context)) add("Battery Saver")
        if (isDeviceIdle(context)) add("dozing")
        // Only when on: the ordinary case is off, and a word on every line
        // would bury the one line where it matters.
        dataSaverState(context).takeIf { it == BACKGROUND_DATA_BLOCKED }?.let(::add)
        if (isBackgroundRestricted(context)) add("battery Restricted")
    }
    return "${networkState(context).short()}, ${power.short()}, ${appVisibility()}" +
        if (flags.isEmpty()) "" else ", " + flags.joinToString(", ")
}