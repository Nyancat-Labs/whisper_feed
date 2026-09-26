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

import com.saulhdev.feeder.manager.sync.greader.GoogleReaderState
import com.saulhdev.feeder.manager.sync.greader.MissingKind
import com.saulhdev.feeder.manager.sync.service.refusalCode
import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.manager.sync.AUTOMATIC_SYNC_WORK
import androidx.work.WorkInfo
import androidx.core.content.ContextCompat
import android.content.IntentFilter
import com.saulhdev.feeder.manager.sync.PERIODIC_SYNC_WORK
import com.saulhdev.feeder.manager.sync.SyncWatchdog
import androidx.work.WorkManager
import androidx.work.NetworkType
import android.os.BatteryManager
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Self-service diagnostics, so a problem can be reported from the device alone.
 *
 * This app is developed against a phone with no desktop attached, and adb's
 * wireless route needs a network that is not always there. An app can read its
 * own logcat without any permission, though, so it can collect what would
 * otherwise need a host machine.
 *
 * The report goes to the shared Downloads collection rather than the app's own
 * storage, because app-private files and `Android/data` are both unreadable by
 * other apps on modern Android — Downloads is somewhere a terminal or file
 * manager can actually reach it.
 */
object Diagnostics : KoinComponent {

    private const val LOG_LINE_LIMIT = 2000

    /** Enough to see the shape of a feed list without printing forty lines. */
    private const val MAX_SOURCES_LISTED = 20

    /**
     * The whole report, with addresses taken out. Scrubbed as one piece
     * rather than section by section, so a section added later cannot forget
     * to be. See [LogScrub].
     */
    suspend fun collect(context: Context): String = LogScrub.scrub(draft(context), ownValues())

    /**
     * What is this reader's own and must not travel with the report: the sync
     * server and account name, and the glance row's place. Each read on its
     * own, so one that cannot be read leaves the others.
     */
    private suspend fun ownValues(): Map<String, String> = buildMap {
        runCatching {
            val account = get<SyncAccount>()
            val server = account.serverUrl.trim()
            (server.toHttpUrlOrNull() ?: "https://$server".toHttpUrlOrNull())?.host
                ?.let { put(it, "<server>") }
            account.username.takeIf { it.isNotBlank() }?.let { put(it, "<account>") }
        }
        runCatching {
            val prefs = get<FeedPreferences>()
            val place = prefs.glancePlaceName.getValue().trim()
            if (place.isNotEmpty()) {
                put(place, LogScrub.PLACE)
                put(place.substringBefore(',').trim(), LogScrub.PLACE)
            }
            prefs.glancePlaceCoords.getValue().split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { put(it, LogScrub.PLACE) }
        }
    }

    private suspend fun draft(context: Context): String = buildString {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        appendLine("Whisper diagnostics")
        appendLine("Generated: $now")
        appendLine()

        appendLine("== Build ==")
        appendLine("Package:     ${BuildConfig.APPLICATION_ID}")
        appendLine("Version:     ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Build type:  ${BuildConfig.BUILD_TYPE}")
        appendLine()

        appendLine("== Device ==")
        appendLine("Model:       ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android:     ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Photo decodes: ${imageDecodeSummary(imageDecodeLimit(context))}")
        appendLine()

        appendLine("== State ==")
        // The overlay needs this to launch articles; see UPSTREAM_NOTES.md 3b.
        appendLine("Can draw overlays: ${Settings.canDrawOverlays(context)}")
        runCatching {
            val prefs = get<FeedPreferences>()
            appendLine("Article open mode: ${prefs.articleOpenMode.getValue()}")
            // Keep, dim or hide. Keep is the default and changes nothing on
            // screen when an article is read, which reads as marking having
            // failed; the report has to be able to say which it is.
            appendLine("Read articles:     ${prefs.readVisibility.getValue()}")
            appendLine("Overlay theme:     ${prefs.overlayTheme.getValue()}")
            // Two different things, easily confused: categoryFilter is what the
            // chips select (include), tagsFilter is what the filter sheet mutes
            // (exclude). Reporting only one of them is how a misleading report
            // gets written.
            appendLine("Selected categories: ${prefs.categoryFilter.getValue()}")
            appendLine("Muted tags:          ${prefs.tagsFilter.getValue()}")
            appendLine("Muted sources:       ${prefs.sourcesFilter.getValue()}")
        }.onFailure { appendLine("Preferences unavailable: $it") }

        appendLine()
        appendLine("== Sync ==")
        // What the scheduled sync is set to, what WorkManager actually holds,
        // and what the phone is doing right now - the three things a "why has
        // it not synced" question turns on, and none of which could be seen
        // from the report before. The last-sync times further down say
        // *whether* a sync ran; this says why one has not.
        runCatching { withTimeout(SECTION_TIMEOUT_MS) { appendSync(context) } }
            .onFailure { appendLine("Sync state unavailable: $it") }

        appendLine()
        appendLine("== Sync history (newest first) ==")
        // What started each of the last syncs, and what the phone was doing
        // at the time. The sync section says what is waiting; this says what
        // actually ran, and on whose say-so. See SyncLog.
        val history = SyncLog.entries(context)
        if (history.isEmpty()) appendLine("  none recorded yet")
        val stamp = SimpleDateFormat("dd HH:mm:ss", Locale.US)
        history.forEach {
            val took = if (it.end > 0L) "${(it.end - it.start) / 1000}s" else "-"
            appendLine(
                "  ${stamp.format(Date(it.start))}  ${it.origin.padEnd(15)} " +
                    "${it.outcome.padEnd(10)} ${took.padStart(5)}"
            )
            // Both readings, on their own lines so neither is cut short. The
            // end is shown only when it differs: an unchanged phone is the
            // ordinary case and a second identical line would bury the rest.
            appendLine("      start: ${it.phone}")
            if (it.phoneAtEnd.isNotBlank() && it.phoneAtEnd != it.phone) {
                appendLine("      end:   ${it.phoneAtEnd}")
            }
        }

        appendLine()
        appendLine("== Images ==")
        // Since this launch, not since the last five-second window, which is
        // all the log below can show. A picture that failed to appear an hour
        // into a session is still counted here. See FeedTrace.imageTally.
        appendLine(FeedTrace.imageTally())

        // Worth reporting explicitly: an empty tag list is the ordinary reason the
        // category chip row renders nothing, and it is indistinguishable from a
        // failure without looking.
        runCatching { withTimeout(SECTION_TIMEOUT_MS) {
            val tags = get<SourcesRepository>().getAllTagsFlow().first()
            appendLine("Source tags:       ${tags.filter { it.isNotBlank() }}")
            appendLine("Untagged sources:  ${tags.any { it.isBlank() }}")
        } }.onFailure { appendLine("Sources unavailable: $it") }
        appendLine()

        // The counts, which are what an empty feed actually turns on. Without
        // them a report of "the feed is empty" cannot distinguish an empty
        // database from a full one being filtered to nothing, and those have
        // nothing in common but the symptom.
        appendLine("== Counts ==")
        runCatching { withTimeout(SECTION_TIMEOUT_MS) {
            // Every subscription, switched on or not. This used to ask
            // getAllSources, which is the sync's question - enabled and not
            // removed - so "Enabled" matched "Sources" by construction and a
            // feed that had been switched off or hidden (hiding switches a
            // source off) could not appear in the report at all. A reader
            // subscribed to 140-odd saw 120 and was right to query it.
            val sources = get<SourcesRepository>().getAllSourcesFlow().first()
            appendLine("Sources:           ${sources.size}")
            appendLine("Enabled:           ${sources.count { it.isEnabled }}")
            appendLine("Switched off:      ${sources.count { !it.isEnabled }} (hidden sources count here)")

            // Counted over all of them, not just the ones printed below. A
            // sample of twenty is how the last round of this was noticed at
            // all, and only because six rows happened to share a timestamp;
            // the totals say it outright.
            val never = sources.count { it.isEnabled && it.lastSync.toEpochMilliseconds() <= 0L }
            val failing = sources.count { it.isEnabled && it.consecutiveFailures > 0 }
            appendLine("Never synced:      $never")
            appendLine("Currently failing: $failing")
            // Each is downloaded once per subscription on every sync. Titles
            // only, which the list below already carries.
            val duplicates = get<SourcesRepository>().duplicateGroups()
            appendLine("Subscribed twice:  ${duplicates.size}")
            duplicates.forEach { group ->
                appendLine("  " + group.joinToString(" = ") { it.title.take(28) })
            }

            // Worst first: a report is read from the top, and the feeds that
            // are not working are the reason anybody is reading it.
            val ordered = sources.sortedWith(
                compareByDescending<com.saulhdev.feeder.data.db.models.Feed> {
                    it.isEnabled && it.lastSync.toEpochMilliseconds() <= 0L
                }.thenByDescending { it.consecutiveFailures }
            )
            ordered.take(MAX_SOURCES_LISTED).forEach {
                val last = if (it.lastSync.toEpochMilliseconds() <= 0L) "never" else "${it.lastSync}"
                val fails = if (it.consecutiveFailures > 0) {
                    " fails ${it.consecutiveFailures} since ${it.failingSince}"
                } else ""
                appendLine(
                    "  ${if (it.isEnabled) "on " else "off"} " +
                            "${it.title.take(28).padEnd(28)} last sync $last$fails"
                )
            }
            if (sources.size > MAX_SOURCES_LISTED) {
                appendLine("  … and ${sources.size - MAX_SOURCES_LISTED} more")
            }
        } }.onFailure { appendLine("Source counts unavailable: $it") }

        runCatching { withTimeout(SECTION_TIMEOUT_MS) {
            val repo = get<ArticleRepository>()
            appendLine("Articles stored:   ${repo.countAll()}")
            appendLine("Unread:            ${repo.countUnread().first()}")
            appendLine("Bookmarked:        ${repo.getBookmarkedFeedItems().first().size}")
        } }.onFailure { appendLine("Article counts unavailable: $it") }
        appendLine()

        // Every feed's own last few fetches, which the sync lines above
        // cannot give: they say "4 failed", not which four, and their data
        // figure is the whole app's. Feeds with a recent failure first.
        appendLine("== Feeds (last ${FeedHistory.MAX_PER_FEED} fetches, newest first) ==")
        runCatching { withTimeout(SECTION_TIMEOUT_MS) {
            val sources = get<SourcesRepository>().getAllSourcesFlow().first()
            FeedHistory.keepOnly(context, sources.map { it.id }.toSet())
            FeedDigest.keepOnly(context, sources.map { it.id }.toSet())
            val histories = sources.associateWith { FeedHistory.read(context, it.id) }
            FeedHistoryCodec.summary(histories.map { (feed, fetches) -> feed.title to fetches }).forEach(::appendLine)
            histories.entries
                .sortedWith(
                    compareByDescending<Map.Entry<com.saulhdev.feeder.data.db.models.Feed, List<FeedFetch>>> { (_, fetches) ->
                        fetches.any { it.kind == FetchKind.Failed }
                    }.thenBy { it.key.title.lowercase() }
                )
                .forEach { (feed, fetches) -> appendLine(FeedHistoryCodec.line(feed.title, fetches)) }
        } }.onFailure { appendLine("Feed history unavailable: $it") }
        appendLine()

        appendLine("== Log (last $LOG_LINE_LIMIT lines, this app only) ==")
        appendLine(readOwnLogcat())
    }

    /**
     * Reads this app's logcat. Since Android 4.1 a process only sees its own
     * entries, which is exactly the scope wanted and needs no permission.
     */
    private suspend fun StringBuilder.appendSync(context: Context) {
        val prefs = get<FeedPreferences>()
        val hours = prefs.syncFrequency.getValue().toDoubleOrNull() ?: 0.0
        val wifiOnly = prefs.syncOnlyOnWifi.getValue()
        val chargingOnly = prefs.syncOnlyWhenCharging.getValue()
        appendLine(
            "Settings:     " +
                (if (hours > 0) "every ${formatHours(hours)}" else "off") +
                ", Wi-Fi only ${yesNo(wifiOnly)}, charging only ${yesNo(chargingOnly)}"
        )

        val onWifi = isUnmetered(context)
        val power = powerState(context)
        val saver = isPowerSaveMode(context)
        appendLine("Phone now:    ${networkState(context).short()}, ${power.describe()}")
        appendLine(
            "              Battery Saver ${if (saver) "on" else "off"}, " +
                "dozing ${yesNo(isDeviceIdle(context))}, ${dataSaverState(context)}, " +
                "battery setting ${if (isBackgroundRestricted(context)) "Restricted" else "not restricted"}"
        )

        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(PERIODIC_SYNC_WORK)
            .first()
            .firstOrNull()
        if (work == null) {
            appendLine("Scheduled:    nothing - " + if (hours > 0) "expected a schedule and found none" else "off, as set")
        } else {
            val next = work.nextScheduleTimeMillis
            val inMinutes = (next - System.currentTimeMillis()) / 60_000
            val nextText = if (next == Long.MAX_VALUE || next <= 0L) "unknown"
                else SimpleDateFormat("HH:mm", Locale.US).format(Date(next)) +
                    if (inMinutes >= 0) " (in $inMinutes min)" else " (${-inMinutes} min overdue)"
            appendLine("Scheduled:    ${work.state}, next $nextText, attempts ${work.runAttemptCount}")
            // Why the last attempt did not finish, in WorkManager's own words.
            // "Attempts 2" with the slot still overdue said two runs had been
            // started and stopped, and nothing in the report could say by what.
            if (work.stopReason != WorkInfo.STOP_REASON_NOT_STOPPED) {
                appendLine("Last stopped: ${stopReasonName(work.stopReason)}")
            }

            val needs = work.constraints
            val wantsUnmetered = needs.requiredNetworkType == NetworkType.UNMETERED
            appendLine(
                "Requires:     network ${needs.requiredNetworkType}, charging ${yesNo(needs.requiresCharging())}, " +
                    "battery not low ${yesNo(needs.requiresBatteryNotLow())}"
            )
            // The line the switch test turns on: what the schedule is waiting
            // for that the phone is not doing.
            //
            // "A charger" is asked of *plugged in*, not of charging. The first
            // version asked BatteryManager.isCharging, and a Pixel on its
            // charger overnight is often not charging at all - adaptive
            // charging holds it at 80% until near the alarm - so a phone
            // sitting on its charger was reported as waiting for one. Whether
            // the scheduler counts a held charge as charging is the phone's
            // decision rather than something this can see, so that state is
            // named as what it is and the verdict does not guess.
            val unmet = buildList {
                if (wantsUnmetered && !onWifi) add("Wi-Fi")
                if (needs.requiresCharging() && !power.pluggedIn) add("a charger")
                if (needs.requiresBatteryNotLow() && power.low) add("more battery")
                // Not a WorkManager constraint, so WorkManager will start the
                // run - and the worker will skip it. Said here so a skipped
                // run in the history is not a mystery.
                if (saver) add("Battery Saver to be off")
                // Data Saver keeps a backgrounded app off mobile data, and a
                // scheduled sync always runs in the background - so on mobile
                // data with Data Saver on it can never start, whatever the
                // switches say. Named, because nothing else would explain it -
                // unless "Wi-Fi only" already asked for Wi-Fi, which read
                // "Wi-Fi and Wi-Fi (...)".
                if (!onWifi && !wantsUnmetered && dataSaverState(context) == BACKGROUND_DATA_BLOCKED) {
                    add("Wi-Fi (Android blocks Whisper's mobile data in the background)")
                }
            }
            appendLine(
                "Waiting for:  " + if (unmet.isEmpty()) "nothing it can see - due at its next slot" else unmet.joinToString(" and ")
            )
            // Settings that disagree with the schedule mean the schedule was
            // not updated when the switch was changed - the bug the settings
            // collector exists to prevent, and worth saying outright if it
            // ever comes back.
            if (wantsUnmetered != wifiOnly || needs.requiresCharging() != chargingOnly) {
                appendLine("MISMATCH:     the schedule does not match the settings above")
            }
        }

        // The panel's own sync, which waits for the same conditions as the
        // scheduled one. Before it did, it was the sync that ran against the
        // switches, so it is reported beside them.
        val automatic = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(AUTOMATIC_SYNC_WORK)
            .first()
            .firstOrNull()
        appendLine(
            "Panel sync:   " + if (automatic == null) "none queued"
            else "${automatic.state}, requires charging ${yesNo(automatic.constraints.requiresCharging())}, " +
                "network ${automatic.constraints.requiredNetworkType}"
        )

        val newest = get<SourcesRepository>().getAllSources()
            .maxOfOrNull { it.lastSync.toEpochMilliseconds() } ?: 0L
        if (newest > 0L) {
            val ago = (System.currentTimeMillis() - newest) / 60_000
            appendLine(
                "Last sync:    " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(newest)) +
                    " ($ago min ago)"
            )
        } else {
            appendLine("Last sync:    never")
        }
        appendLine("Problems:     " + SyncWatchdog.describe(context))
        appendLine("Account:      " + accountLine(context))
        if (get<SyncAccount>().isSignedIn) {
            runCatching { GoogleReaderState.notOnServer(context) }.getOrDefault(emptyList()).let { missing ->
                if (missing.isNotEmpty()) appendLine("Not on the server: ${missing.size}")
                missing.forEach { feed ->
                    val why = when (feed.kind) {
                        MissingKind.REFUSED -> "refused by the server" + refusalCode(feed.status) + ", kept on this phone"
                        MissingKind.REMOVED_THERE -> "removed on the server, kept here"
                        MissingKind.NO_WRITE -> "not sent, no write access"
                    }
                    appendLine("  ${feed.title.take(28).padEnd(28)} $why")
                }
            }
        }
    }

    /**
     * Whether an account is signed in, and where its sync stands. Counts and
     * times only: the server's address is somebody's own machine, and never
     * goes in a file that is sent to a stranger.
     */
    private suspend fun accountLine(context: Context): String = runCatching {
        if (!get<SyncAccount>().isSignedIn) return@runCatching "none"
        val waiting = GoogleReaderState.outbox(context)
        val mapped = get<ArticleRepository>().mappedArticles().size
        val mappedAt = GoogleReaderState.mappedAt(context)
        val clock = SimpleDateFormat("MM-dd HH:mm", Locale.US)
        listOf(
            "signed in",
            "${waiting.pending.size} changes waiting (${waiting.read.size} read, ${waiting.unread.size} unread, " +
                "${waiting.star.size} saved, ${waiting.unstar.size} unsaved)",
            "$mapped articles matched",
            if (mappedAt > 0) "last matched ${clock.format(Date(mappedAt))}" else "not matched yet",
            "${GoogleReaderState.everOnServer(context).size} feeds seen on the server",
        ).joinToString(", ")
    }.getOrDefault("unknown")

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"


    private fun formatHours(hours: Double): String =
        if (hours < 1.0) "${(hours * 60).toInt()} min"
        else if (hours % 1.0 == 0.0) "${hours.toInt()} h"
        else "$hours h"



    /**
     * How long any one database section may take before the report goes on
     * without it. A section that times out says so and the rest still arrives:
     * a report that is missing its counts is still a report, and one that
     * never finishes is nothing at all.
     */
    private const val SECTION_TIMEOUT_MS = 4_000L


    private fun readOwnLogcat(): String = runCatching {
        val process = Runtime.getRuntime().exec(
            // "*:V" explicitly, rather than relying on the default filter:
            // the gate trace and anything else written at DEBUG is the point
            // of reading this at all, and a default of *:I would drop it.
            arrayOf("logcat", "-d", "-v", "time", "-t", LOG_LINE_LIMIT.toString(), "*:V")
        )
        process.inputStream.bufferedReader().use { it.readText() }
            .ifBlank { "(no log entries)" }
    }.getOrElse { "Could not read logcat: $it" }

    /**
     * Whether a report is being put together right now.
     *
     * Both buttons said nothing until the report was finished, and finishing
     * waits on the database - which, during a sync, is the busiest thing in
     * the app. So a tap looked like nothing at all, a second tap started a
     * second report behind the first, and the whole thing read as a button
     * that did not work. The one moment a diagnostics report is most wanted,
     * the app misbehaving, is the moment it was slowest to arrive.
     */
    private val collecting = AtomicBoolean(false)

    /**
     * Says the report has started, or refuses a second one. False when one is
     * already on its way, so the caller does nothing rather than queue another.
     */
    fun begin(context: Context): Boolean {
        if (!collecting.compareAndSet(false, true)) return false
        Toast.makeText(context, R.string.diagnostics_collecting, Toast.LENGTH_SHORT).show()
        return true
    }

    fun end() {
        collecting.set(false)
    }

    /**
     * Writes the report to Downloads and returns a human-readable location, or
     * null if it could not be written.
     */
    suspend fun export(context: Context): String? {
        val report = collect(context)
        val name = "whisper-diagnostics-" +
            SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(context, name, report)
            } else {
                writeToLegacyDownloads(name, report)
            }
        }.getOrNull()
    }

    // Guarded at the call site, and said again here so the compiler and lint
    // can both see it rather than taking the branch on trust.
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(context: Context, name: String, report: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore rejected the insert")

        resolver.openOutputStream(uri)?.use { it.write(report.toByteArray()) }
            ?: error("Could not open $uri for writing")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        return "Download/$name"
    }

    /**
     * Bundles the report and hands it to the share sheet.
     *
     * The export above writes to Downloads, which is right for the developer's
     * own phone and useless to a tester: it leaves them to find a file manager,
     * locate the file and attach it themselves, and most people stop before
     * the end of that sentence. This puts the same report into whichever app
     * they already use to talk to us.
     *
     * @param note what the tester typed. It goes at the top of the report
     *   rather than in the message body, because a message body is easy to
     *   lose track of and the file is the thing that gets kept.
     */
    suspend fun share(context: Context, note: String) {
        val report = buildString {
            if (note.isNotBlank()) {
                appendLine("== What happened ==")
                appendLine(note.trim())
                appendLine()
            }
            append(collect(context))
        }

        val name = "whisper-report-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        // The provider is scoped to exactly this directory; see file_paths.xml.
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        // Yesterday's reports are of no use to anyone and would otherwise
        // accumulate for the life of the install.
        dir.listFiles()?.forEach { it.delete() }

        val file = File(dir, name)
        file.writeText(report)

        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "Whisper ${BuildConfig.VERSION_NAME} — ${Build.MANUFACTURER} ${Build.MODEL}",
            )
            if (note.isNotBlank()) putExtra(Intent.EXTRA_TEXT, note.trim())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(send, context.getString(R.string.report_problem))
        // Settings is an activity, but this is called from a preference's
        // onClick with the application context in scope for other call sites.
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    @Suppress("DEPRECATION") // The permission is declared with maxSdkVersion 28.
    private fun writeToLegacyDownloads(name: String, report: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(dir, name)
        file.writeText(report)
        return file.absolutePath
    }

    /**
     * Writes the stack trace to Downloads when the app dies unexpectedly.
     *
     * A crash on launch is the one failure this app had no way to report. The
     * diagnostics above are collected from a running app by somebody pressing
     * a button, and an app that dies before its first frame offers neither —
     * so the only evidence was a host machine running adb, which is exactly
     * what this project does not assume anybody has. Three release cycles were
     * spent guessing at a crash for want of one stack trace.
     *
     * Deliberately small. It runs inside a process that is already dying, so
     * it collects what is already in memory — the exception, the build, the
     * device — and writes it. No database, no preferences, no logcat: each of
     * those can block or throw, and a crash reporter that crashes reports
     * nothing.
     *
     * The previous handler is always called afterwards. Swallowing it would
     * leave the process hung instead of dying, and would take away the system
     * dialog that is the only sign to the reader that anything happened.
     */
    fun installCrashLog(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { writeCrash(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun writeCrash(context: Context, thread: Thread, error: Throwable) {
        val report = buildString {
            appendLine("Whisper crash")
            appendLine("When:        ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("Version:     ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build type:  ${BuildConfig.BUILD_TYPE}")
            appendLine("Device:      ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android:     ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread:      ${thread.name}")
            appendLine()
            appendLine("== Stack trace ==")
            appendLine(error.stackTraceToString())

            // Printed separately as well as inside the trace above, because a
            // cause chain is where the answer usually is and `Caused by` is
            // easy to lose in a hundred lines of framework frames.
            var cause = error.cause
            var depth = 0
            while (cause != null && depth < 8) {
                appendLine()
                appendLine("== Cause ${depth + 1} ==")
                appendLine(cause.stackTraceToString())
                cause = cause.cause
                depth++
            }
        }

        val name = "whisper-crash-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, name, report)
        } else {
            writeToLegacyDownloads(name, report)
        }
    }
}
