package com.saulhdev.feeder.manager.sync

import com.saulhdev.feeder.utils.backgroundMobileDataBlocked
import com.saulhdev.feeder.utils.skipForBlockedData
import com.saulhdev.feeder.utils.skipAfterCutOff
import com.saulhdev.feeder.utils.whisperOnScreen
import com.saulhdev.feeder.utils.bytesSince
import com.saulhdev.feeder.utils.receivedBytes
import com.saulhdev.feeder.utils.isPowerSaveMode
import com.saulhdev.feeder.utils.powerState
import com.saulhdev.feeder.utils.stopReasonName
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.StepTrace
import com.saulhdev.feeder.data.db.ID_ALL
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.Data
import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import com.saulhdev.feeder.manager.sync.greader.problemFrom
import androidx.work.CoroutineWorker
import com.saulhdev.feeder.manager.sync.service.LocalRssService
import com.saulhdev.feeder.manager.sync.service.RssServiceDispatcher
import com.saulhdev.feeder.manager.sync.service.SyncOutcome
import org.koin.java.KoinJavaComponent.inject
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saulhdev.feeder.R
import com.saulhdev.feeder.utils.SyncResult
import com.saulhdev.feeder.utils.syncOutcome
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.ID_UNSET
import com.saulhdev.feeder.data.repository.SourcesRepository
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull

class FeedSyncer(val context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {

    private val TAG = "FeedSyncer"
    private val dispatcher: RssServiceDispatcher by inject(RssServiceDispatcher::class.java)
    private val sources: SourcesRepository by inject(SourcesRepository::class.java)
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    private val notificationManager: NotificationManagerCompat =
        NotificationManagerCompat.from(context)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo(context, notificationManager)
    }

    override suspend fun doWork(): Result {
        var result: SyncResult
        // What went wrong with the account, for the account screen to say.
        var output = Data.EMPTY
        // Written before anything else can fail, so even a run that dies
        // immediately leaves a line saying it started. See SyncLog.
        val origin = inputData.getString(SyncLog.ORIGIN_KEY) ?: "unlabelled"
        val run = SyncLog.started(applicationContext, origin)

        // Battery Saver pauses the syncs nobody asked for at this moment -
        // the schedule and the panel's - and nothing else. It is the reader
        // saying "use as little battery as you can", and a background fetch
        // of every feed is precisely what that is meant to stop. There is no
        // WorkManager constraint for it, so it is asked here.
        //
        // Success rather than retry: the scheduled sync comes round again at
        // its next slot, and a retry would back off and try again while the
        // saver was still on. Plugging in turns Battery Saver off by itself,
        // so this can never hold a sync that "Sync only while charging" is
        // waiting to run. Pull to refresh and the rest go through.
        if (origin in SyncLog.AUTOMATIC_ORIGINS && isPowerSaveMode(applicationContext)) {
            SyncLog.finished(applicationContext, run, "skipped: Battery Saver")
            return Result.success()
        }

        // On mobile data with Android keeping Whisper off it in the
        // background, a run in the background is cut off and retried, again
        // and again. Skipped the same way as for Battery Saver: the next slot
        // may be on Wi-Fi. But only in the background - on screen, Android
        // lets Whisper use the data, so the run goes ahead, as a foreground
        // task below so that it keeps the network if the reader then leaves.
        // Settings says which switch lifts the block; see BackgroundDataHint.
        val automatic = origin in SyncLog.AUTOMATIC_ORIGINS
        val dataBlocked = automatic && backgroundMobileDataBlocked(applicationContext)
        // Asked once: every question below is about the moment the run began.
        val onScreen = whisperOnScreen()
        if (skipForBlockedData(automatic, dataBlocked, onScreen)) {
            SyncLog.finished(applicationContext, run, "skipped: background mobile data blocked")
            return Result.success()
        }

        // Cut off a moment ago, in the background: started again at once, it
        // would only be cut off again. See skipAfterCutOff.
        if (skipAfterCutOff(
                automatic = automatic,
                onScreen = onScreen,
                entries = SyncLog.entries(applicationContext),
                nowMs = System.currentTimeMillis(),
                current = run,
            )
        ) {
            SyncLog.finished(applicationContext, run, "skipped: cut off a moment ago")
            return Result.success()
        }

        // A sync the reader asked for keeps its network when they leave.
        // Sync now on the account screen too: run from the screen itself, it
        // lost the network the moment the reader switched apps, and said the
        // server could not be found.
        //
        // Every pull to refresh in one day's reports was cut off the moment
        // Whisper left the screen - "stopped: network changed or dropped",
        // ending "blocked for Whisper" even on Wi-Fi - while the scheduled and
        // panel syncs, which Android starts itself, ran to the end in the
        // background. A foreground task is how Android lets work the reader
        // started go on after they have looked away; it has to show a
        // notification, which is the silent "Syncing" line in the shade, and
        // Android holds that back for the first ten seconds so a quick pull
        // never shows it at all. Asked while the app is still on screen,
        // which is when a pull starts; if Android refuses, the sync simply
        // runs as it did before.
        //
        // And any sync that starts while Whisper is on screen, whoever asked
        // for it. The panel's and the schedule's, begun with the reader
        // looking, were stopped the moment they looked away - "device state
        // changed", six times one day on Wi-Fi, each a minute or two into
        // the feeds and never reaching the account, so twenty-five reads sat
        // unsent all afternoon.
        //
        // And any sync that starts on the charger. A night's report had the
        // background syncs about ten times slower than the ones begun on
        // screen, on the same Wi-Fi: the feeds 129-203 s against 10-32 s,
        // matching 0.6 s an item against 0.05, and one run 426 s into its
        // 480. Android gives work in the background the slow end of the
        // phone; a foreground task gets what an open app gets. Android may
        // refuse to start one from the background, and then the run goes on
        // as before, saying so in its line, so a report shows which it was.
        val pluggedIn = powerState(applicationContext).pluggedIn
        val foreground = runInForeground(origin, dataBlocked, onScreen, pluggedIn)
        var inForeground = false
        var foregroundRefused = false
        if (foreground) {
            try {
                setForeground(getForegroundInfo())
                inForeground = true
            } catch (e: CancellationException) {
                SyncLog.finished(applicationContext, run, "stopped: before starting")
                throw e
            } catch (e: Exception) {
                foregroundRefused = true
                Log.w(TAG, "Could not run the sync in the foreground", e)
            }
        }
        // In the background, at the slow end of the phone, the run is a
        // lighter one: see RssService.sync. In the foreground - begun on
        // screen, or asked for - it does everything.
        val background = !inForeground
        // Whether another sync holds the lock, so a run that spent its first
        // minutes waiting is not mistaken for a slow one.
        val queued = syncMutex.isLocked
        // Where the account sync got to, for a run stopped before it can say.
        val trace = StepTrace()
        // Counted from here: what this run cost in data. See SyncResult.bytes.
        val receivedBefore = receivedBytes()

        try {
            // Stopped here, short of Android's ten minutes, rather than there:
            // a run Android stops is cut off wherever it is, and one morning
            // three syncs on the charger each hit the limit. Ended here, what
            // it did is kept - the feeds it fetched and the articles it
            // matched - and the retry carries on from it. See resumeFrom and
            // mapRemoteIds.
            val done = withTimeoutOrNull(SYNC_TIME_BUDGET_MS) {
                val feedId = inputData.getLong("feed_id", ID_UNSET)
                val feedTag = inputData.getString("feed_tag") ?: ""
                // False unless the request says so. This defaulted to true, and the
                // scheduled sync's request carries no such key - so every
                // scheduled sync forced a fetch of every feed however recently it
                // had been fetched. The report caught it: plugging in released
                // the panel's sync and the scheduled one together, the lock let
                // the panel's go first, and the scheduled one then waited three
                // minutes and downloaded all 120 feeds again. Only a sync the
                // reader asked for should skip the freshness check.
                val forceNetwork = inputData.getBoolean("force_network", false)
                val minFeedAgeMinutes = inputData.getInt("min_feed_age_minutes", 5)

                // A whole-feed refresh goes through whichever service is in
                // charge; a single feed or one tag is always local, because
                // neither the protocol nor this worker has a notion of syncing
                // part of an account.
                //
                // This used to call syncFeeds directly in every case, which meant
                // an account was reconciled only when its settings screen was
                // open: subscriptions added on another device did not arrive, and
                // read state never moved unless somebody went looking for it. The
                // scheduled sync was local-only without saying so.
                // Both ways of saying "every feed": the schedule leaves the id
                // unset, the panel and the app say ID_ALL. Only the first counted,
                // so a panel or app-opened sync with an account signed in fetched
                // the feeds and never spoke to the server.
                val wholeFeed = isWholeFeed(feedId, feedTag)
                val service = dispatcher.current()

                if (wholeFeed && service !is LocalRssService) {
                    // An account says only whether it worked, so its line has no
                    // feed count; see SyncResult.counted.
                    when (val outcome = service.sync(
                        forceNetwork = forceNetwork,
                        retryRefused = origin == SyncLog.ORIGIN_ACCOUNT,
                        trace = trace,
                        background = background,
                    )) {
                        is SyncOutcome.Success -> outcome.feeds ?: SyncResult.uncounted
                        SyncOutcome.SignedOut -> {
                            // The token is gone, and retrying will not bring it
                            // back. Reported as success so WorkManager does not
                            // back off and retry a thing that needs the reader.
                            Log.w(TAG, "Account signed out; scheduled sync stopped")
                            output = accountProblemData(AccountProblem.SIGNED_OUT, null)
                            SyncResult(due = 0, error = "signed out")
                        }

                        is SyncOutcome.Failed -> {
                            Log.e(TAG, "Account sync failed", outcome.cause)
                            output = accountProblemData(problemFrom(outcome.cause), outcome.cause?.message)
                            outcome.cause?.let(SyncResult::broken) ?: SyncResult(due = 0, error = "error")
                        }
                    }
                } else {
                    syncFeeds(
                        context = context,
                        feedId = feedId,
                        feedTag = feedTag,
                        forceNetwork = forceNetwork,
                        minFeedAgeMinutes = minFeedAgeMinutes,
                        feedDeadlineMs = backgroundFeedDeadline(background),
                    )
                }
            }
            if (done == null) {
                SyncLog.finished(applicationContext, run, withTrace(OUT_OF_TIME_OUTCOME, trace))
                // Retried soon for the syncs nobody asked for, which pick up
                // where this stopped. A forced one would start again from the
                // top, so it is left there.
                return if (automatic) Result.retry() else Result.success(output)
            }
            result = done
        } catch (e: CancellationException) {
            // Cancellation is the reader leaving, not a sync that went wrong.
            // Reported as a failure it would retry on a backoff and log an
            // error for something nobody did wrong. See RssLocalSync.
            //
            // WorkManager stops a worker by cancelling it, so this is also
            // where a run cut off by a lost constraint ends - and its reason
            // is the thing "attempts 2" could not explain.
            // The worker can only ask why from Android 12; before that,
            // WorkManager's own record in the report is the only answer.
            val why = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                stopReasonName(stopReason)
            } else {
                "no reason given"
            }
            SyncLog.finished(applicationContext, run, withTrace("stopped: $why", trace))
            throw e
        } catch (e: Exception) {
            result = SyncResult.broken(e)
            Log.e(TAG, "Failure during sync", e)
        }

        val notes = listOfNotNull(
            if (queued) "queued" else null,
            if (inForeground) "foreground" else null,
            if (foregroundRefused) "foreground refused" else null,
        )
        result = result.copy(bytes = bytesSince(receivedBefore))
        SyncLog.finished(
            applicationContext,
            run,
            syncOutcome(result, notes),
        )
        // Signed out stays a success to WorkManager: retrying cannot bring a
        // token back, and a backoff would only repeat the same line.
        val success = result.ok || result.error == "signed out"
        // A notice that syncing had stopped is taken back by the sync that
        // proves it has started again.
        if (success) {
            try {
                SyncWatchdog.clearIfCurrent(applicationContext, sources, prefs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not clear the stuck-sync notice", e)
            }
        }
        return when (success) {
            true  -> Result.success(output)
            false -> Result.failure(output)
        }
    }
}

private const val syncNotificationId = 42624
private const val syncChannelId = "feederSyncNotifications"
private const val syncNotificationGroup = "com.saulhdev.neofeed.SYNC"

private fun createNotificationChannel(
    context: Context,
    notificationManager: NotificationManagerCompat
) {
    val name = context.getString(R.string.sync_status)
    val description = context.getString(R.string.sync_status)

    val channel =
        NotificationChannel(syncChannelId, name, NotificationManager.IMPORTANCE_LOW)
    channel.description = description

    notificationManager.createNotificationChannel(channel)
}

fun createForegroundInfo(
    context: Context,
    notificationManager: NotificationManagerCompat
): ForegroundInfo {
    createNotificationChannel(context, notificationManager)

    val syncingText = context.getString(R.string.syncing)

    val notification =
        NotificationCompat.Builder(context.applicationContext, syncChannelId)
            .setContentTitle(syncingText)
            .setTicker(syncingText)
            .setGroup(syncNotificationGroup)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            // A status line, not an alert: no sound, no vibration, no pop-up.
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()

    return ForegroundInfo(
        syncNotificationId,
        notification,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        else 0
    )
}

const val ACCOUNT_PROBLEM_KEY = "account_problem"
const val ACCOUNT_DETAIL_KEY = "account_detail"

private fun accountProblemData(problem: AccountProblem, detail: String?): Data =
    workDataOf(ACCOUNT_PROBLEM_KEY to problem.name, ACCOUNT_DETAIL_KEY to detail)

/** The unique name one-time syncs of [feedId] are queued under. */
fun oneTimeSyncName(feedId: Long) = "feeder_sync_onetime_$feedId"

fun requestFeedSync(
    feedId: Long = ID_UNSET,
    feedTag: String = "",
    forceNetwork: Boolean = false,
    /** What asked for it, for the sync record. See SyncLog. */
    origin: String,
) {
    val workManager: WorkManager by inject(WorkManager::class.java)
    val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    
    val data = workDataOf(
        "feed_id" to feedId,
        "feed_tag" to feedTag,
        "force_network" to forceNetwork,
        SyncLog.ORIGIN_KEY to origin,
    )
    Log.d(TAG, "requestFeedSync: $data")
    val constraints = Constraints.Builder()
    if (prefs.syncOnlyOnWifi.getValue() && !forceNetwork) {
        constraints.setRequiredNetworkType(NetworkType.UNMETERED)
    } else {
        constraints.setRequiredNetworkType(NetworkType.CONNECTED)
    }

    val workRequest = OneTimeWorkRequestBuilder<FeedSyncer>()
        .addTag("FeedSyncer")
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .keepResultsForAtLeast(1, TimeUnit.MINUTES)
        .setConstraints(constraints.build())
        .setInputData(data)
        .build()

    workManager.enqueueUniqueWork(
        oneTimeSyncName(feedId),
        ExistingWorkPolicy.REPLACE,
        workRequest
    )
}

/**
 * A whole-feed sync that waits for the scheduled sync's conditions.
 *
 * Its own unique name, and KEEP rather than REPLACE, for two reasons. REPLACE
 * under the one-time name would cancel a pull-to-refresh already running the
 * moment the launcher recreated the panel. And a request that is waiting for a
 * charger needs no second copy queued behind it every time the panel opens.
 */
fun requestAutomaticFeedSync(
    /** What asked for it: the panel, or the app being opened. See SyncLog. */
    origin: String = SyncLog.ORIGIN_PANEL,
) {
    val workManager: WorkManager by inject(WorkManager::class.java)
    val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(
            if (prefs.syncOnlyOnWifi.getValue()) NetworkType.UNMETERED else NetworkType.CONNECTED
        )
        .setRequiresCharging(prefs.syncOnlyWhenCharging.getValue())
        .setRequiresBatteryNotLow(true)
        .build()

    val workRequest = OneTimeWorkRequestBuilder<FeedSyncer>()
        .addTag("FeedSyncer")
        .setConstraints(constraints)
        .setInputData(
            workDataOf(
                "feed_id" to ID_ALL,
                "feed_tag" to "",
                "force_network" to false,
                SyncLog.ORIGIN_KEY to origin,
            )
        )
        .build()

    workManager.enqueueUniqueWork(AUTOMATIC_SYNC_WORK, ExistingWorkPolicy.KEEP, workRequest)
}

/**
 * How long a sync runs before it stops itself and keeps what it has done.
 * Android stops background work at ten minutes; this leaves it two.
 */
const val SYNC_TIME_BUDGET_MS = 8 * 60_000L

/**
 * How long a background run spends starting feeds, of its eight minutes.
 *
 * Half, so the account's matching and sending always have time after it: a
 * night on the charger had the feeds take 235 s and the matching 243 s, and
 * the run was stopped at the limit. Feeds not started by then are first next
 * time.
 */
const val BACKGROUND_FEEDS_BUDGET_MS = 4 * 60_000L

/** When a run's feeds stop being started: four minutes on in the background, never otherwise. */
fun backgroundFeedDeadline(background: Boolean, nowMs: Long = System.currentTimeMillis()): Long? =
    if (background) nowMs + BACKGROUND_FEEDS_BUDGET_MS else null

/** The history's line for a sync that stopped itself at [SYNC_TIME_BUDGET_MS]. */
const val OUT_OF_TIME_OUTCOME = "stopped: eight minutes up, progress kept"

/**
 * A stopped run's history line with where it got to, when it got anywhere:
 * "stopped: eight minutes up, progress kept; reached sign-in 0.5s, feeds 41s;
 * stopped in matching after 380s". The stop comes first, so the line still
 * starts with what the hold and the pick-up look for; see skipAfterCutOff.
 */
fun withTrace(stopped: String, trace: StepTrace): String =
    trace.describe()?.let { "$stopped; $it" } ?: stopped

/**
 * Whether a sync runs as a foreground task, keeping its network when the
 * reader leaves: one they asked for, one going ahead on mobile data Android
 * would otherwise keep from it, any begun while Whisper is on screen, and
 * any begun on the charger, where a background run was ten times slower.
 */
fun runInForeground(
    origin: String,
    dataBlocked: Boolean,
    onScreen: Boolean,
    pluggedIn: Boolean = false,
): Boolean = origin in SyncLog.ASKED_ORIGINS || dataBlocked || onScreen || pluggedIn

/** Whether a sync request is for every feed rather than one feed or one tag. */
fun isWholeFeed(feedId: Long, feedTag: String): Boolean =
    (feedId == ID_UNSET || feedId == ID_ALL) && feedTag.isEmpty()

/** The name the panel's automatic sync is enqueued under. See requestAutomaticFeedSync. */
const val AUTOMATIC_SYNC_WORK = "feeder_sync_automatic"

/**
 * The name the scheduled sync is enqueued under.
 *
 * One constant rather than a string in two places: the diagnostics report
 * asks WorkManager about the work by this name, and a report that looked
 * under a name the scheduler had stopped using would say "not scheduled"
 * about a sync that was running perfectly well.
 */
const val PERIODIC_SYNC_WORK = "feeder_periodic_3"
