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
package com.saulhdev.feeder.manager.service

import android.content.Context
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a launcher is using Whisper as its feed, and which one could.
 *
 * There is no way to ask a launcher what it has selected — the preference
 * lives in its process and its data directory — so the only honest signal
 * available is whether one has ever bound to [OverlayService] while this
 * process has been alive. That is a proof of success and not a proof of
 * failure: after a restart the flag reads false until the launcher next asks
 * for the page, which is why the screen says "connected" when it is true and
 * says nothing at all when it is not.
 */
object LauncherLink {

    private val _bound = MutableStateFlow(false)

    /** True from the moment a launcher binds until it lets go. */
    val bound: StateFlow<Boolean> = _bound

    /** True if a launcher has bound at any point since the app started. */
    var everBound: Boolean = false
        private set

    internal fun onBound() {
        _bound.value = true
        everBound = true
    }

    internal fun onUnbound() {
        _bound.value = false
    }

    /**
     * Launchers known to offer a choice of feed provider.
     *
     * Lawnchair is the one that has been tested on a device. The forks
     * inherit the same `FeedBridge`, so they very probably work, and listing
     * them costs nothing — the screen only uses this to decide whether the
     * setup instructions are worth showing at all.
     *
     * Looking any of these up needs the `<queries>` block in the manifest.
     * Without it Android 11 and later answer NameNotFound for every package,
     * installed or not, and this screen told everyone they had no launcher.
     */
    private val LAUNCHERS = listOf(
        "app.lawnchair",
        "ch.deletescape.lawnchair",
        "ch.deletescape.lawnchair.plah",
        "com.saggitt.omega",
        "app.lawnchair.debug",
    )

    /** The package name of an installed launcher that could use this, if any. */
    fun installedLauncher(context: Context): String? =
        LAUNCHERS.firstOrNull { context.isInstalled(it) }

    /**
     * That launcher's own settings, if it can be reached.
     *
     * Launchers do not publish a deep link to the feed-provider preference, so
     * this opens the launcher's settings and the instructions take it from
     * there. Better than nothing, and honest about being a starting point
     * rather than a shortcut.
     *
     * Not the launcher's launch intent: for a launcher that is the home
     * screen, and the button put the reader on their desktop. In order: the
     * settings screen an app declares for Android's own "App settings" link;
     * Lawnchair's settings by name, for a version that declares none; and
     * the launcher's page in Android's settings, which at least is a
     * settings screen for it.
     */
    fun settingsIntent(context: Context, packageName: String): Intent? {
        if (!context.isInstalled(packageName)) return null
        val pm = context.packageManager
        val candidates = listOf(
            Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(packageName),
            Intent().setClassName(packageName, LAWNCHAIR_SETTINGS),
        )
        return candidates.firstOrNull { intent ->
            pm.resolveActivity(intent, 0)?.activityInfo?.exported == true
        } ?: Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
    }

    private const val LAWNCHAIR_SETTINGS = "app.lawnchair.ui.preferences.PreferenceActivity"

    /**
     * Whether the caller asking to bind [OverlayService] is a launcher.
     *
     * The caller is read out of the bind Uri, which is where the LauncherClient
     * protocol puts it: the port is the calling uid and the host is the calling
     * package. Not from `Binder.getCallingUid()` — `onBind` is dispatched on
     * the main thread, outside the binder transaction, and there that method
     * answers with our own uid. A check written that way passes for everyone,
     * which is worse than no check because it looks like one.
     *
     * A caller naming itself is not a caller proving itself, so the package
     * manager is asked whether that package really belongs to that uid before
     * anything else is decided. Then the question that matters: does the
     * package answer `CATEGORY_HOME`? Every app that can be a home screen
     * does, no app that cannot has a reason to be here, and it needs no list of
     * package names kept up to date.
     *
     * Our own uid passes, so the settings screen and the diagnostics can bind
     * this to check the integration is alive.
     */
    fun callerIsALauncher(context: Context, intent: Intent): Boolean {
        val data = intent.data ?: return false
        val uid = data.port.takeIf { it != -1 } ?: return false
        val pkg = data.host ?: return false
        if (uid == Process.myUid()) return true

        val pm = context.packageManager
        if (pm.getPackagesForUid(uid)?.contains(pkg) != true) return false

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName == pkg }
    }

    /**
     * Whether a uid the kernel vouched for belongs to a launcher.
     *
     * The difference from [callerIsALauncher] is the whole security property.
     * That one reads a uid an app wrote into a Uri and checks it is a real
     * pairing — which proves the *pair* exists, never that the caller is it.
     * Anyone can write Lawnchair's package and Lawnchair's uid into a Uri and
     * pass. This takes a uid from a binder transaction, where it was filled in
     * by the kernel and cannot be chosen by the sender.
     *
     * Any package sharing that uid being a launcher is enough. A shared uid is
     * one trust domain — the packages in it can already read each other's
     * files — so a finer distinction here would be a distinction the platform
     * does not make.
     */
    fun uidIsALauncher(context: Context, uid: Int): Boolean {
        if (uid == Process.myUid()) return true
        val pm = context.packageManager
        val packages = pm.getPackagesForUid(uid)?.toSet() ?: return false

        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return pm.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .any { it.activityInfo?.packageName in packages }
    }

    private fun Context.isInstalled(packageName: String): Boolean = try {
        packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
