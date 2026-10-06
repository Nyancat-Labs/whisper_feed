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
package com.saulhdev.feeder.manager.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

/**
 * The reader's answer to Android's own backup, asked before anything is sent.
 *
 * `allowBackup` is a manifest attribute read by the system at install time and
 * there is no API to change it — `BackupManager` can request a backup pass but
 * cannot enable or disable one. So a settings switch cannot turn the platform
 * backup on and off directly. What it *can* do is decide what the app hands
 * over when the system asks, which is what this is: the system starts a backup,
 * this agent looks at what the reader chose, and writes nothing if they have
 * not asked for it.
 *
 * **The two routes are separate questions and get separate answers.** A
 * device-to-device transfer is a direct copy to the reader's next phone with
 * no server anywhere in it. A cloud backup puts the same data in Google's
 * hands. Somebody can quite reasonably want the first and not the second, and
 * `getTransportFlags()` says which one is running, so they are asked twice
 * rather than once.
 *
 * Both are off until turned on. A default that uploads somebody's reading
 * history before they have opened the app is not a choice they made.
 */
class PlatformBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        // getTransportFlags arrived in API 28, and this app runs on 26. Below
        // that there is no device-to-device transfer to tell apart — the
        // feature did not exist — so every backup is a cloud backup and is
        // gated by the stricter of the two switches.
        val deviceToDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (data.transportFlags and FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0
        val allowed = runCatching { isAllowed(deviceToDevice) }.getOrDefault(false)

        if (!allowed) {
            // Returning without calling super writes nothing at all. The
            // system sees an app with no data rather than an error, which is
            // exactly what somebody who left both switches off asked for.
            Log.i(TAG, "Declined ${route(deviceToDevice)}: not enabled by the reader")
            return
        }

        Log.i(TAG, "Allowing ${route(deviceToDevice)}")
        super.onFullBackup(data)
    }

    /**
     * Reads the choice, refusing if it cannot be read.
     *
     * The agent runs in the app's own process, so the DataStore singleton is
     * the one everything else uses — opening a second one against the same
     * file would throw. If the graph is not up for any reason, the answer is
     * no: failing closed sends nothing, and failing open would send everything.
     */
    private fun isAllowed(deviceToDevice: Boolean): Boolean {
        val koin = GlobalContext.getOrNull() ?: return false
        val dataStore: DataStore<Preferences> = koin.get()
        val key = booleanPreferencesKey(
            if (deviceToDevice) KEY_DEVICE_TRANSFER else KEY_CLOUD
        )
        return runBlocking { dataStore.data.first()[key] } == true
    }

    private fun route(deviceToDevice: Boolean) =
        if (deviceToDevice) "device-to-device transfer" else "cloud backup"

    // Key/value backup is not used — everything here is full-data backup, and
    // these two are abstract on the base class rather than optional.
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    private companion object {
        const val TAG = "PlatformBackup"

        // Named here rather than imported from FeedPreferences: this class is
        // built by the system before anything else, and reaching into the
        // preference graph for two strings would be a lot of machinery to load
        // at the one moment it is least wanted.
        const val KEY_DEVICE_TRANSFER = "pref_platform_backup_device"
        const val KEY_CLOUD = "pref_platform_backup_cloud"
    }
}
