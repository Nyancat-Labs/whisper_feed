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
package com.saulhdev.feeder.manager.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import com.saulhdev.feeder.utils.UpdateCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.java.KoinJavaComponent.inject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Asks GitHub, once a day, for the newest release, and says when it is newer.
 *
 * Says, never installs: installing would need REQUEST_INSTALL_PACKAGES, which
 * Play restricts and F-Droid flags. The notification opens the release page.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    override suspend fun doWork(): Result {
        // Checked again here, not only when scheduling: a switch turned off
        // between the schedule and the run means no request at all.
        if (!prefs.checkForUpdates.getValue() || !isGitHubInstall(applicationContext)) {
            return Result.success()
        }
        val tag = latestTag() ?: return Result.success()
        prefs.latestRelease.setValue(tag)
        if (UpdateCheck.shouldNotify(tag, BuildConfig.VERSION_NAME, prefs.notifiedRelease.getValue())) {
            if (notify(applicationContext, tag)) prefs.notifiedRelease.setValue(tag)
        }
        return Result.success()
    }

    /**
     * The one request. No id, no counts, and no version in the user agent,
     * which GitHub requires to be present but not what it says.
     */
    private suspend fun latestTag(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(UpdateCheck.LATEST_RELEASE_API)
                .header("User-Agent", "Whisper")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                UpdateCheck.tagFrom(response.body.string())
            }
        }.onFailure { Log.w(TAG, "Update check failed", it) }.getOrNull()
    }

    companion object {
        private const val TAG = "UpdateCheck"
        private const val WORK_NAME = "update_check"
        private const val NOW_WORK_NAME = "update_check_now"
        private const val CHANNEL_ID = "updates"
        private const val NOTIFICATION_ID = 7301

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .onlyPublicHttps()
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        private val connected = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Daily while the switch is on, and nothing at all while it is off.
         * Called at start and whenever the switch changes.
         */
        fun schedule(context: Context, on: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!on || !isGitHubInstall(context)) {
                workManager.cancelUniqueWork(WORK_NAME)
                workManager.cancelUniqueWork(NOW_WORK_NAME)
                return
            }
            val daily = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(connected)
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, daily)
        }

        /** Turning the switch on asks straight away, rather than tomorrow. */
        fun checkNow(context: Context) {
            val once = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                .setConstraints(connected)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, once)
        }

        /** See [UpdateCheck.isGitHubInstall]. False if either fact cannot be read. */
        fun isGitHubInstall(context: Context): Boolean = runCatching {
            UpdateCheck.isGitHubInstall(signerDigests(context), installer(context))
        }.getOrDefault(false)

        private fun installer(context: Context): String? {
            val pm = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        }

        /** SHA-256 of the key this copy is signed with now, in hex. */
        @SuppressLint("PackageManagerGetSignatures")
        private fun signerDigests(context: Context): List<String> {
            val pm = context.packageManager
            val certificates = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo ?: return emptyList()
                // The current signer: after a key rotation the history also
                // holds the old one, which no longer says where this came from.
                if (info.hasMultipleSigners()) info.apkContentsSigners.toList()
                else listOfNotNull(info.signingCertificateHistory?.lastOrNull())
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                    .signatures?.toList().orEmpty()
            }
            return certificates.map { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                    .joinToString("") { "%02X".format(it) }
            }
        }

        /** True if the notification was shown. */
        private fun notify(context: Context, tag: String): Boolean {
            if (!canNotify(context)) return false
            val manager = NotificationManagerCompat.from(context)
            // Its own channel, so it can be switched off without losing the
            // sync-problems notice, and the other way round.
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.update_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.update_channel_description) }
            )
            val open = Intent(Intent.ACTION_VIEW, UpdateCheck.releasePage(tag).toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                open,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = context.getString(R.string.update_notification_text, BuildConfig.VERSION_NAME)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.update_available, tag.removePrefix("v")))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            return try {
                manager.notify(NOTIFICATION_ID, notification)
                true
            } catch (_: SecurityException) {
                // Permission withdrawn between the check and the call.
                false
            }
        }

        /** Switched off, or never allowed: say nothing, and ask nothing. */
        private fun canNotify(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
    }
}
