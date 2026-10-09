package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.GoogleReaderState
import com.saulhdev.feeder.data.content.SyncAccount
import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import com.saulhdev.feeder.utils.HttpIdentity.asImageFetcher
import okhttp3.OkHttpClient
import com.saulhdev.feeder.utils.orphanArticleFiles
import java.io.File
import android.util.Log
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.jakewharton.threetenabp.AndroidThreeTen
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.content.FeedPreferences.Companion.prefsModule
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.discovery.DiscoveryWorker
import com.saulhdev.feeder.manager.backup.BackupWorker
import com.saulhdev.feeder.manager.models.scheduleSavedFullText
import com.saulhdev.feeder.utils.SavedImages
import com.saulhdev.feeder.manager.service.OverlayBridge
import com.saulhdev.feeder.utils.ApplicationCoroutineScope
import com.saulhdev.feeder.utils.Diagnostics
import com.saulhdev.feeder.utils.FEED_TRACE_WINDOW_MS
import com.saulhdev.feeder.utils.FeedTrace
import com.saulhdev.feeder.utils.ImageTrace
import com.saulhdev.feeder.utils.imageDecodeLimit
import coil.imageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import com.saulhdev.feeder.utils.MainThreadWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androix.startup.KoinStartup
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.GlobalContext
import org.koin.dsl.koinConfiguration
import org.koin.java.KoinJavaComponent.inject

@OptIn(KoinExperimentalAPI::class)
// A plain Application: with minSdk 26 the platform loads every dex file
// itself, so the multidex library had nothing to do.
class NeoApp : Application(), KoinStartup, ImageLoaderFactory {
    val activityHandler = ActivityHandler()
    // Built by coreModule now, not here; see AppModules.kt.
    private val applicationCoroutineScope: ApplicationCoroutineScope by inject(
        ApplicationCoroutineScope::class.java
    )
    private val wm: WorkManager by inject(WorkManager::class.java)

    fun onAppStarted() {
        registerActivityLifecycleCallbacks(activityHandler)
        reportFeedTrace()
        stampOnboardingForExistingInstalls()
        purgeMisfiledFullText()
        sweepOrphanArticleFiles()
        // Off while suggestions are out of Settings; see DiscoveryWorker.cancel.
        DiscoveryWorker.cancel(this)
        refreshBackupSchedule()
        // Saves made before their pictures were kept get them now; a run
        // with nothing missing is a few file checks.
        scheduleSavedFullText()
    }

    /**
     * Re-states the daily backup with the rules as they are now.
     *
     * A schedule keeps the conditions it was made with, so one set up while
     * every folder waited for Wi-Fi would go on waiting until somebody chose a
     * folder again. Nothing happens without a folder.
     */
    private fun refreshBackupSchedule() {
        applicationCoroutineScope.launch(Dispatchers.IO) {
            val folder = get<FeedPreferences>().backupFolder.getValue()
            if (folder.isNotEmpty()) BackupWorker.schedule(this@NeoApp, folder)
        }
    }

    /**
     * Tells an upgrade from a fresh install, once.
     *
     * There is no version number to compare against — the preference simply
     * does not exist on either — so the question is answered by the only
     * honest signal available: an install that already has sources has been
     * used, and somebody who has been reading with this for a year must not be
     * welcomed to it as though they had just arrived.
     *
     * A genuinely new install has no sources, so this does nothing and the
     * welcome runs. It stays correct if they close the app before finishing:
     * still no sources, so they are still new.
     */
    /**
     * Throws away full-text articles cached before the mix-up was fixed.
     *
     * The reader could write one article's page into another's file — see
     * FeedPreferences.fullTextPurged — and because the fetch skips a file that
     * already exists, a wrong one stayed wrong through every reopening. The
     * fix stops new ones being written; it cannot tell which existing ones are
     * already wrong, because a correctly fetched page and a misfiled one are
     * the same kind of file.
     *
     * So they all go. These are written only when somebody opens an article,
     * so the set is small, and the cost is re-fetching pages they have read.
     * Set the preference first: a purge interrupted halfway leaves the rest to
     * the next launch, and a purge that ran but was not recorded would run
     * again on every launch for ever.
     */
    private fun purgeMisfiledFullText() {
        applicationCoroutineScope.launch(Dispatchers.IO) {
            val prefs: FeedPreferences = get()
            if (prefs.fullTextPurged.getValue()) return@launch
            runCatching {
                filesDir.listFiles { file -> file.name.endsWith(".full.html.gz") }
                    ?.forEach { it.delete() }
            }.onFailure { Log.w(TAG, "Could not clear the cached full-text articles", it) }
            prefs.fullTextPurged.setValue(true)
        }
    }

    /**
     * Deletes the files of articles that no longer exist.
     *
     * Cleanup removed an article's summary and left its full text, and
     * removing a source or clearing its articles left both. With "Fetch full
     * articles for every feed" on, that is a page for every article ever
     * cleaned up, kept for good. Run at each start, off the main thread; a
     * listing and one query, and nothing to do once it has caught up.
     */
    private fun sweepOrphanArticleFiles() {
        applicationCoroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val listing = filesDir.listFiles()?.map { it.name to it.lastModified() }.orEmpty()
                val known = get<ArticleRepository>().allArticleIds()
                val orphans = orphanArticleFiles(
                    files = listing,
                    knownIds = known,
                    nowMs = System.currentTimeMillis(),
                    minAgeMs = 60 * 60_000L,
                )
                orphans.forEach { File(filesDir, it).delete() }
                if (orphans.isNotEmpty()) Log.i(TAG, "Deleted ${orphans.size} files of removed articles")
            }.onFailure { Log.w(TAG, "Could not sweep old article files", it) }
        }
    }

    private fun stampOnboardingForExistingInstalls() {
        applicationCoroutineScope.launch(Dispatchers.IO) {
            val prefs: FeedPreferences = get()
            if (prefs.onboardingSeen.getValue()) return@launch
            val sources: SourcesRepository = get()
            if (sources.getAllSources().isNotEmpty()) {
                prefs.onboardingSeen.setValue(true)
                prefs.tourSeen.setValue(true)
            }
        }
    }

    @KoinExperimentalAPI
    override fun onKoinStartup() = koinConfiguration {
        androidLogger()
        androidContext(this@NeoApp)
        modules(coreModule, prefsModule, dataModule, modelModule)
    }

    override fun onCreate() {
        super.onCreate()
        // Before anything else that can throw. A crash on launch was the one
        // failure this app could not report: the diagnostics screen needs a
        // running app to press a button in, so the only evidence was a host
        // machine with adb, which this project does not assume anybody has.
        Diagnostics.installCrashLog(this)
        // Then this, so it sees everything that follows — including this
        // class's own startup work, which is where main-thread disk reads
        // hide best.
        MainThreadWatch.install()
        instance = this
        // TODO remove on future release
        AndroidThreeTen.init(this)
        DynamicColors.applyToActivitiesIfAvailable(
            this,
            com.google.android.material.color.DynamicColorsOptions.Builder()
                .setPrecondition { _, _ -> DynamicColors.isDynamicColorAvailable() }
                .build()
        )
        wm.pruneWork()
        carryOverHiddenSources()
        reapRemovedSourcesOnUnsave()
        queueChangesForAccount()
        onAppStarted()
    }

    /**
     * Turns off anything hidden under the old two-state arrangement.
     *
     * Hiding a source and switching it off used to be separate: both kept its
     * articles out of the feed, and only one of them stopped it syncing.
     * They are one thing now, so a source somebody hid before this release
     * has to end up off rather than quietly coming back the moment the set
     * stopped being read.
     *
     * Runs once and clears the set, so it costs a preference read on
     * subsequent starts and nothing else. Off the main thread, because it is
     * a DataStore read and a database write and neither belongs in onCreate.
     */
    /**
     * Drops a removed source when the last thing saved from it is unsaved.
     *
     * Wired here because articles cannot hold a reference to sources without
     * making a cycle of a dependency that already runs the other way.
     */
    /**
     * Keeps what the reader reads and saves, for the account's next sync.
     *
     * Only while an account is signed in. Without one there is nobody to
     * tell, and a queue kept anyway would arrive at whichever account was
     * signed into next as a pile of changes it never asked for.
     */
    private fun queueChangesForAccount() {
        val articles: ArticleRepository by inject(ArticleRepository::class.java)
        val account: SyncAccount by inject(SyncAccount::class.java)
        articles.onReadChanged = { ids, read ->
            if (account.isSignedIn) GoogleReaderState.updateOutbox(this) { it.withRead(ids, read) }
        }
        articles.onStarredChanged = { id, starred ->
            if (account.isSignedIn) GoogleReaderState.updateOutbox(this) { it.withStar(id, starred) }
        }
    }

    private fun reapRemovedSourcesOnUnsave() {
        val articles: ArticleRepository by inject(ArticleRepository::class.java)
        val sources: SourcesRepository by inject(SourcesRepository::class.java)
        articles.onSaved = { scheduleSavedFullText() }
        articles.onUnsaved = { id -> runCatching { SavedImages.delete(id, filesDir) } }
        articles.onSavedRemoved = { feedId ->
            applicationCoroutineScope.launch(Dispatchers.IO) {
                runCatching { sources.reapIfEmpty(feedId) }
            }
        }
    }

    /**
     * Writes one FeedTrace line per window, while Debugging is on.
     *
     * Follows the preference rather than reading it once, so turning tracing
     * on is enough — the reader is already being asked to reproduce something,
     * and telling them to restart the app as well is how a report comes back
     * with the lines missing. That happened once already: a scroll report
     * arrived with no ReadGate lines in it at all.
     *
     * The counters themselves run whether or not anyone is watching; they are
     * atomic adds on a path that allocates five hundred article rows, and
     * making them conditional would cost more in branches than it saved. This
     * only decides whether they are ever read out.
     */
    private fun reportFeedTrace() {
        applicationCoroutineScope.launch(Dispatchers.IO) {
            val prefs: FeedPreferences = get()
            prefs.debugging.get().collectLatest { on ->
                if (!on) return@collectLatest
                // Whatever accumulated while nobody was reading would
                // otherwise arrive as one enormous first window and read as a
                // spike that never happened.
                FeedTrace.reset()
                while (true) {
                    delay(FEED_TRACE_WINDOW_MS)
                    // Read here rather than inside FeedTrace, which has no
                    // business knowing about an image loader — and read every
                    // window, because occupancy is the question the hit rate
                    // alone could not answer.
                    val cache = imageLoader.memoryCache?.let {
                        FeedTrace.CacheState(
                            usedBytes = it.size,
                            maxBytes = it.maxSize,
                            entries = it.keys.size,
                        )
                    }
                    FeedTrace.report(FEED_TRACE_WINDOW_MS, cache)
                }
            }
        }
    }

    private fun carryOverHiddenSources() {
        val prefs: FeedPreferences by inject(FeedPreferences::class.java)
        val sources: SourcesRepository by inject(SourcesRepository::class.java)
        applicationCoroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val hidden = prefs.hiddenSources.getValue()
                if (hidden.isEmpty()) return@runCatching
                sources.setEnabled(hidden.mapNotNull(String::toLongOrNull), enabled = false)
                prefs.hiddenSources.setValue(emptySet())
            }
        }
    }

    /**
     * The one image loader, instead of the default Coil builds for itself.
     *
     * Two reasons, and the first is the same one the feed and full-text
     * clients have: every `<img src>` in the app comes out of somebody else's
     * XML, so an article can point the phone at an address on its own network
     * and learn whether it answered. Images went out through a client with no
     * such guard.
     *
     * The second is that the default loader keeps no disk cache tuned for this
     * and a memory cache sized for a gallery app. A feed is a long scroll of
     * pictures the reader passes once, so the useful cache is on disk, where a
     * revisit is free and the heap is not the thing paying for it.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            // asImageFetcher, which this had been going without: every picture
            // went out as okhttp/5.5.0 with no Accept header, and a
            // publisher's image CDN is exactly the host that answers an
            // unknown client with a 403. Two identities were defined in
            // HttpIdentity and neither was applied here.
            OkHttpClient.Builder()
                .asImageFetcher()
                .onlyPublicHttps()
                .build()
        }
        // Two at a time on an older device, Coil's four elsewhere. See
        // imageDecodeLimit.
        .bitmapFactoryMaxParallelism(imageDecodeLimit(this))
        .memoryCache {
            MemoryCache.Builder(this)
                // Raised from 0.20 on measurement, not preference.
                //
                // A trace of a real scroll showed the decode count tracking
                // the non-memory hits exactly — 59 images served, 25 from
                // memory, 34 decodes; 80 served, 57 from memory, 23 decodes.
                // Every picture the cache has dropped is decoded again in
                // full, and a full-width article photograph costs 28-36ms of
                // that. Nothing else in the feed comes close now.
                //
                // At 20% the cache held fewer than two screens of full-width
                // cards, so scrolling back a little re-decoded everything it
                // passed. Meanwhile the heap sat at 33MB of 256, 87% free, for
                // the whole run: the app had stopped needing the room the
                // cache was being denied.
                //
                // Not higher than this. The cache is the first thing to hurt
                // under memory pressure on a device smaller than the one
                // measured, and images are the part of a feed that can always
                // be fetched again — unlike the article text, which cannot.
                .maxSizePercent(MEMORY_CACHE_FRACTION)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                .build()
        }
        // Nothing in this builder controls decode size, and a comment that
        // stood in this spot said it did — that a 4000px newspaper photo would
        // otherwise be decoded at full size to be drawn 300px wide. The
        // concern is real; the answer is not here. Coil sizes a decode from
        // whichever bound it can find, and every image in this app has one:
        //
        //  - the feed cards in ArticleCard take theirs from the layout — an
        //    aspectRatio on the three that run the card's width, a fixed size
        //    on the compact thumbnail — so each is sampled down to the space
        //    it occupies without anything being said about it;
        //  - the article-body images in HtmlToComposable cannot, because they
        //    are fillMaxWidth with the height left to the picture, which is
        //    an unbounded constraint and no bound at all. Those carry an
        //    explicit .size() on the request instead.
        //
        // So the sizing is real but it is distributed, and that is why the old
        // comment did harm out of proportion to its length: read as a guard
        // living here, it made those modifiers look decorative. They are the
        // guard. Unbind one and the full-size decode it warned about starts
        // happening, with nothing in this file to catch it.

        // Feed images are served with cache headers written for browsers —
        // no-store on a CDN's hot path, a few minutes on the rest — and
        // honouring them means re-fetching a picture the reader scrolled past
        // a moment ago. They are article illustrations: once published they do
        // not change, and the URL changes when the picture does.
        .respectCacheHeaders(false)
        .crossfade(true)
        // Counts decodes by format and where each picture came from; see
        // ImageTrace. Always attached, never reported unless Debugging is on:
        // a per-request object that records two longs costs nothing beside a
        // decode, and a listener wired in only when a preference is set is a
        // listener that is missing from the one report that needed it.
        // The *factory*, not a listener. One instance per request is what
        // makes the timing a plain field rather than a map keyed by request
        // that has to be swept on every path a decode can fail down — and a
        // single shared listener would have each request's decodeStart
        // overwriting the last one's.
        .eventListenerFactory(ImageTrace.Factory)
        .build()

    override fun onTerminate() {
        super.onTerminate()
        GlobalContext.get().close()
    }

    companion object {
        private const val TAG = "NeoFeed"

        /**
         * How much of the available memory the decoded-image cache may hold.
         *
         * Named rather than inline so the figure can be found from the trace
         * that justifies it: FeedTrace reports images served against images
         * decoded, and the gap between them is what this number moves.
         */
        private const val MEMORY_CACHE_FRACTION = 0.35

        @JvmStatic
        var instance: NeoApp? = null
            private set

        val bridge = OverlayBridge()
    }
}

class ActivityHandler : ActivityLifecycleCallbacks {
    val activities = HashSet<Activity>()
    var foregroundActivity: Activity? = null
    private var startedActivities = 0


    fun finishAll(recreateApp: Boolean = true) {
        HashSet(activities).forEach { if (recreateApp) it.recreate() else it.finish() }
    }

    override fun onActivityPaused(activity: Activity) {
        if (activity == foregroundActivity) {
            foregroundActivity = null
        }
    }

    override fun onActivityResumed(activity: Activity) {
        foregroundActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivities += 1
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (activity == foregroundActivity)
            foregroundActivity = null
        activities.remove(activity)
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivities = (startedActivities - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activities.add(activity)
    }
}

/**
 * How much disk the image cache may take: 128 MB.
 *
 * Coil's default is 2% of the free space on the data partition, which on a
 * mostly empty phone is gigabytes for pictures nobody will look at twice, and
 * on a full one is a few megabytes — the wrong way round on both.
 */
private const val IMAGE_DISK_CACHE_BYTES = 128L * 1024 * 1024
