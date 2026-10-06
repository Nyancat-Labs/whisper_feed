package com.saulhdev.feeder

import androidx.work.workDataOf
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.openedSyncDue
import com.saulhdev.feeder.manager.sync.requestAutomaticFeedSync
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.sync.AUTOMATIC_SYNC_WORK
import com.saulhdev.feeder.manager.sync.PERIODIC_SYNC_WORK
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.withResumed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.utils.FrameWatch
import com.saulhdev.feeder.manager.sync.FeedSyncer
import com.saulhdev.feeder.manager.sync.SyncWatchdog
import com.saulhdev.feeder.ui.navigation.NAV_BASE
import com.saulhdev.feeder.ui.navigation.NavigationManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.saulhdev.feeder.ui.pages.WhisperSplash
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import com.saulhdev.feeder.ui.theme.AppTheme
import com.saulhdev.feeder.utils.THEME_DARK
import com.saulhdev.feeder.utils.THEME_LIGHT
import android.view.KeyEvent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.saulhdev.feeder.utils.VolumeScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import com.saulhdev.feeder.utils.BrowserReadTimer
import org.koin.java.KoinJavaComponent.inject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import com.saulhdev.feeder.data.content.asState

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    private val viewModel: ArticleListViewModel by inject(ArticleListViewModel::class.java)
    private val sources: SourcesRepository by inject(SourcesRepository::class.java)

    /**
     * Cached rather than read per press: reading the preference goes through
     * DataStore with runBlocking, and a key handler runs on the main thread.
     */
    @Volatile
    private var volumeKeyScroll = false

    /** Nothing to do with the answer: a refusal simply means no notices. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, or the window splash never installs.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        lifecycleScope.launch {
            prefs.volumeKeyScroll.get().collect { volumeKeyScroll = it }
        }
        // Frame times for the diagnostics trace, while Debugging is on.
        lifecycleScope.launch { FrameWatch.watch(window, prefs.debugging.get()) }
        // A deep link — from the launcher overlay, or a notification — carries
        // data; a tap on the app icon does not. Read here rather than in
        // composition, where a later onNewIntent could change the answer
        // mid-session.
        val launchedDirectly = intent?.data == null
        setContent {
            navController = rememberNavController()
            // Collected rather than read once, so changing the theme or the
            // typeface in Settings is reflected immediately instead of on the
            // next app start. This is also why the activity no longer
            // recreate()s itself on a theme change: a full restart to repaint
            // colours Compose already tracks cost a frame budget and flashed
            // the splash screen on its way back.
            val themeMode by prefs.overlayTheme.asState()
            val dynamic by prefs.dynamicColor.asState()
            val fontPref by prefs.appFont.asState()
            val pureBlack by prefs.pureBlack.asState()

            // One place decides light or dark, and the bars follow it rather
            // than re-deriving it from the preference on their own.
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                THEME_LIGHT -> false
                THEME_DARK -> true
                else -> systemDark
            }
            TransparentSystemBars(dark = dark)

            AppTheme(
                mode = themeMode,
                dynamicColor = dynamic,
                pureBlack = pureBlack,
                fontPref = fontPref,
            ) {
                // The window background comes from the XML theme, whose parent
                // is a DayNight one — so it follows the *system* light/dark
                // setting, not the app's own theme preference. With the system
                // in light mode and the app set to Dark or Pure black, every
                // pixel Compose did not paint showed through as white. Painting
                // the background here makes the app's choice the only one that
                // matters.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // "Ready" is the first feed emission that actually has
                    // articles. The state flow starts eagerly with an empty
                    // list, so an emptiness check is the only signal available
                    // — which is why the splash is also capped by a timeout,
                    // for the case where the feed is genuinely empty.
                    val feedState by viewModel.articleListState.collectAsState()
                    WhisperSplash(
                        ready = feedState.articles.isNotEmpty(),
                        // Only when the app is opened as an app. Arriving from
                        // the launcher feed means tapping Settings and being
                        // shown a brand screen on the way to a preferences
                        // list — the splash belongs to starting Whisper, not
                        // to every screen it can be deep-linked into.
                        enabled = launchedDirectly,
                    ) {
                        NavigationManager(
                            modifier = Modifier.imePadding(),
                            navController = navController,
                        )
                    }
                }
            }
        }

        // Off the main thread. It reads three preferences, and on a cold start
        // those are the first DataStore reads of the process — a file opened
        // from disk, blocking, inside onCreate, which is the worst moment in
        // the app's life to do it. Nothing on screen depends on the result.
        //
        // Collected rather than called once, which is a fix as much as a
        // feature. The schedule was configured in onCreate and never again, so
        // a constraint changed in Settings reached WorkManager only on the next
        // cold start: turning "Sync on Wifi Only" on and watching it go on
        // syncing over mobile data was the behaviour, not a bug report waiting
        // to happen. The first emission is the cold-start configuration this
        // line always did; the rest are the reader changing their mind.
        //
        // Re-enqueuing is cheap and keeps the period — the work is enqueued
        // under one name with ExistingPeriodicWorkPolicy.UPDATE.
        lifecycleScope.launch(Dispatchers.IO) {
            combine(
                prefs.syncFrequency.get(),
                prefs.syncOnlyOnWifi.get(),
                prefs.syncOnlyWhenCharging.get(),
            ) { frequency, wifiOnly, chargingOnly ->
                Triple(frequency, wifiOnly, chargingOnly)
            }
                .distinctUntilChanged()
                .collect { configurePeriodicSync() }
        }
        // The check that speaks up when syncing has stopped working. Kept
        // apart from the schedule above because it must run when the
        // schedule cannot; see SyncWatchdog.
        SyncWatchdog.schedule(WorkManager.getInstance(applicationContext))
        askForNotificationsOnce()
        handleDeepLink(intent)
    }

    /**
     * Closes off a read that happened in an external browser.
     *
     * Resume rather than anything finer-grained, because coming back from the
     * browser is precisely a resume and there is no more specific event to
     * hook. The timer discards anything it is not sure about — no trip
     * outstanding, a trip longer than the limit, one the launcher panel
     * already settled — so this writes nothing in every case but the one it
     * is for.
     */
    override fun onResume() {
        super.onResume()
        BrowserReadTimer.settle()?.let { (id, millis) -> viewModel.addReading(id, millis) }
        syncIfStale()
    }

    /**
     * Syncs when the app comes to the front with a stale feed. See
     * openedSyncDue. Through the automatic request, so it waits for Wi-Fi or
     * a charger when the switches say to, and Battery Saver still pauses it.
     */
    private fun syncIfStale() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val newest = sources.newestSync.first()
                if (openedSyncDue(System.currentTimeMillis(), newest, prefs.syncFrequency.getValue())) {
                    requestAutomaticFeedSync(origin = SyncLog.ORIGIN_OPENED)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Asks to be allowed notifications, once, when there is a reason to.
     *
     * Android 13 onwards starts every app with notifications off, so without
     * this the stuck-sync notice could never be seen. Asked only once the
     * welcome and the tour are done, never over them, and never again after
     * the first answer.
     */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        lifecycleScope.launch {
            combine(
                prefs.onboardingSeen.get(),
                prefs.tourSeen.get(),
                prefs.notificationPermissionAsked.get(),
            ) { welcomed, toured, asked -> welcomed && toured && !asked }
                .first { it }
            lifecycle.withResumed {
                val granted = ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            withContext(Dispatchers.IO) { prefs.notificationPermissionAsked.setValue(true) }
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent == null) return
        if (::navController.isInitialized) {
            navController.handleDeepLink(intent)
        }
    }

    /**
     * @param dark whether the resolved scheme is a dark one, passed in rather
     *   than read here. It used to call `isDarkTheme` and `overlayTheme
     *   .getValue()`, both of which are blocking DataStore reads, as
     *   DisposableEffect *keys* — and keys are re-evaluated on every
     *   recomposition, so this blocked the main thread once per frame.
     */
    @Composable
    fun TransparentSystemBars(dark: Boolean) {
        DisposableEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { dark },
            )
            onDispose {}
        }
    }

    private fun configurePeriodicSync() {
        val workManager = WorkManager.getInstance(this)
        val shouldSync = (prefs.syncFrequency.getValue().toDouble()) > 0
        if (!shouldSync) {
            workManager.cancelUniqueWork(PERIODIC_SYNC_WORK)
            workManager.cancelUniqueWork(AUTOMATIC_SYNC_WORK)
            return
        }

        val constraints = Constraints.Builder()
        if (prefs.syncOnlyOnWifi.getValue()) {
            constraints.setRequiredNetworkType(NetworkType.UNMETERED)
        } else {
            constraints.setRequiredNetworkType(NetworkType.CONNECTED)
        }
        // Nobody with a phone at four percent wants it fetching forty
        // feeds. This is the scheduled sync, which nobody asked for at
        // this particular moment — it waits, and runs when the phone can
        // afford it. Pull-to-refresh is a different request and carries no
        // such constraint, because that one was asked for.
        constraints.setRequiresBatteryNotLow(true)

        // Stricter than battery-not-low, and off unless asked for.
        // Not-low is satisfied most of the time; charging is satisfied for
        // a few hours a night, so WorkManager can hold this work for a
        // long time with nothing on screen saying why. The source list's
        // never-updated and not-updating marks are what make that legible,
        // and pull-to-refresh, which carries no constraints at all, is
        // what makes it recoverable. See prefs.syncOnlyWhenCharging.
        if (prefs.syncOnlyWhenCharging.getValue()) {
            constraints.setRequiresCharging(true)
        }
        val wanted = constraints.build()
        val timeInterval = (prefs.syncFrequency.getValue().toDouble() * 60).toLong()

        // Nothing to do when the schedule already says this. The collector
        // above calls here on every cold start as well as on every change,
        // and re-enqueuing with UPDATE is not free: the report caught it
        // cancelling a scheduled sync that had just started ("stopped:
        // cancelled by the app", 0s) - and the panel sync cleared below would
        // have gone with it, running or not. So only a real change to the
        // settings touches WorkManager at all.
        if (scheduleMatches(workManager, wanted, TimeUnit.MINUTES.toMillis(timeInterval))) return

        // A panel sync already waiting was queued under the old switches, and
        // KEEP means the next panel open will not replace it. Cleared here so
        // the next one is queued under the settings the reader has now.
        workManager.cancelUniqueWork(AUTOMATIC_SYNC_WORK)

        val syncWork = PeriodicWorkRequestBuilder<FeedSyncer>(timeInterval, TimeUnit.MINUTES)
            .setConstraints(wanted)
            .addTag("PeriodicFeedSyncer")
            .setInputData(workDataOf(SyncLog.ORIGIN_KEY to SyncLog.ORIGIN_SCHEDULED))
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncWork
        )
    }

    /** Whether the scheduled sync is already waiting with exactly these conditions. */
    private fun scheduleMatches(workManager: WorkManager, wanted: Constraints, intervalMs: Long): Boolean =
        runCatching {
            val existing = workManager.getWorkInfosForUniqueWork(PERIODIC_SYNC_WORK).get()
                .firstOrNull { !it.state.isFinished } ?: return@runCatching false
            existing.constraints == wanted &&
                existing.periodicityInfo?.repeatIntervalMillis == intervalMs
        }.getOrDefault(false)

    /**
     * Volume keys page the feed, when the reader has asked for it.
     *
     * Both down and up are consumed so the system's volume panel does not
     * appear over the article — handling only the down event still lets the
     * up event through, and the slider slides in a moment later.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyScroll) when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                VolumeScroll.scroll(1)
                return true
            }

            KeyEvent.KEYCODE_VOLUME_UP   -> {
                VolumeScroll.scroll(-1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeKeyScroll &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) return true
        return super.onKeyUp(keyCode, event)
    }

    companion object {
        fun navigateIntent(context: Context, destination: String): Intent {
            val uri = "$NAV_BASE$destination".toUri()
            return Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java)
        }

        // A start()/createResultReceiver pair stood here, putting a caller's
        // Intent into an extra called "intent" and a ResultReceiver into one
        // called "callback", for MainActivity to pull out and act on. Nothing
        // ever called it and nothing ever read those extras — but the shape is
        // the classic intent-redirection hole, and this activity is exported,
        // so the next person to wire it up would have handed every app on the
        // device a way to launch arbitrary intents with Whisper's identity.
    }
}