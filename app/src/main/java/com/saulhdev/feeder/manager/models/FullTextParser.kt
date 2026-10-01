package com.saulhdev.feeder.manager.models

import com.saulhdev.feeder.utils.stripPageChrome
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import com.saulhdev.feeder.utils.HttpIdentity.asArticleReader
import com.saulhdev.feeder.utils.blobFile
import com.saulhdev.feeder.utils.blobFullFile
import com.saulhdev.feeder.utils.blobInputStream
import com.saulhdev.feeder.utils.blobFullInputStream
import com.saulhdev.feeder.utils.SavedImages
import com.saulhdev.feeder.utils.usableImageUrl
import com.saulhdev.feeder.utils.blobFullFailedFile
import com.saulhdev.feeder.utils.FullTextAttempts
import com.saulhdev.feeder.utils.HttpStatusException
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.utils.bytesSince
import com.saulhdev.feeder.utils.fullTextOutcome
import com.saulhdev.feeder.utils.isPermanentHttpFailure
import com.saulhdev.feeder.utils.receivedBytes
import com.saulhdev.feeder.utils.backgroundMobileDataBlocked
import com.saulhdev.feeder.utils.isUnmetered
import com.saulhdev.feeder.utils.countsAgainstSource
import com.saulhdev.feeder.utils.whisperHasNetwork
import com.saulhdev.feeder.utils.shouldPrefetchFullText
import kotlinx.coroutines.CancellationException
import com.saulhdev.feeder.utils.blobFullOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.OkHttpClient
import org.koin.java.KoinJavaComponent.inject
import java.io.File
import java.net.URL
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/**
 * The advance download after a sync, or when a setting asks for more.
 *
 * It replaced itself, and every sync asks for one, so a pull to refresh or
 * the sync on opening the app stopped a pass partway through. Now it waits
 * behind a running pass instead; see [enqueueUnlessWaiting].
 */
fun scheduleFullTextParse() {
    Log.i("FeederFullText", "Scheduling a full text parse work")
    val workRequest = OneTimeWorkRequestBuilder<FullTextWorker>()
        .addTag("FullTextWorker")
        .setConstraints(fullTextConstraints())
        .keepResultsForAtLeast(1, TimeUnit.MINUTES)
    val workManager: WorkManager by inject(WorkManager::class.java)
    enqueueUnlessWaiting(workManager, FULL_TEXT_WORK, workRequest.build())
}

private const val FULL_TEXT_WORK = "FullTextWorker"

/**
 * Whether another run is worth adding. Not while one is waiting: it has yet
 * to start, and when it does it looks again at everything still missing.
 */
internal fun worthAnotherRun(states: Collection<WorkInfo.State>): Boolean =
    states.none { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }

/**
 * Adds [request] to the unique work [name], unless a run is already waiting.
 *
 * Behind a running one, so neither the pass in progress nor the new request
 * is lost; nothing, when one is waiting. At most one running and one waiting,
 * however many saves or syncs ask. Appending every time let the queue grow
 * without limit while the conditions were not met (no Wi-Fi with full
 * articles off on mobile data, a low battery), and all of it ran back to back
 * on the next Wi-Fi.
 *
 * Asked without blocking: this is called from the app's start, on the main
 * thread.
 */
internal fun enqueueUnlessWaiting(workManager: WorkManager, name: String, request: OneTimeWorkRequest) {
    val infos = workManager.getWorkInfosForUniqueWork(name)
    infos.addListener({
        val states = runCatching { infos.get().map { it.state } }.getOrDefault(emptyList())
        if (worthAnotherRun(states)) {
            workManager.enqueueUniqueWork(name, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }, Executor { it.run() })
}

/**
 * Downloads the whole of every saved article that does not have it yet, now.
 *
 * Saving is reading later, and reading later has to work offline: the empty
 * Saved screen promises exactly that. Saved articles were already in the
 * advance download whatever their source's setting, but that runs after a
 * sync, so an article saved on the train waited up to an hour — and one
 * saved just before losing signal was not there to read at all.
 *
 * Its own unique work, so it never cancels the after-sync run, and never more
 * than one waiting: each run looks again at what is still missing. Same
 * conditions as every other advance download, the mobile-data one included.
 */
fun scheduleSavedFullText() {
    val request = OneTimeWorkRequestBuilder<FullTextWorker>()
        .addTag("FullTextWorker")
        .setConstraints(fullTextConstraints())
        .setInputData(workDataOf(SAVED_ONLY to true))
        .keepResultsForAtLeast(1, TimeUnit.MINUTES)
        .build()
    val workManager: WorkManager by inject(WorkManager::class.java)
    enqueueUnlessWaiting(workManager, SAVED_WORK, request)
}

private const val SAVED_ONLY = "saved_only"
private const val SAVED_WORK = "FullTextSaved"

// This fetches and parses the body of every article the reader has, which is
// the heaviest thing the app does — and it carried no constraints at all. With
// no network requirement it would start on a phone with no connection, fail
// article by article, and be retried; with no battery requirement it would do
// that on a flat one.
private fun fullTextConstraints(): Constraints {
    val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    return Constraints.Builder()
        .setRequiredNetworkType(
            fullTextNetwork(
                syncOnlyOnWifi = prefs.syncOnlyOnWifi.getValue(),
                fullTextOnMobile = prefs.fullTextOnMobile.getValue(),
            )
        )
        .setRequiresBatteryNotLow(true)
        .build()
}

/**
 * How many pages one after-sync run downloads.
 *
 * A backlog of four hundred went in one run: over three minutes, cut off as
 * Whisper left the screen, started again from the top by the next sync and
 * cut off again, the battery going down by a fifth in an evening. Fifty a run
 * keeps each one short enough to finish, and the backlog goes over the next
 * few syncs, newest first.
 */
internal const val FULL_TEXT_PER_RUN = 50

/**
 * The pages this run downloads, of the [waiting] ones, which come saved first
 * and then newest. All of them for a run of saved articles only: the reader
 * asked for each of those, and they are never many.
 */
internal fun <T> fullTextBatch(waiting: List<T>, savedOnly: Boolean): List<T> =
    if (savedOnly) waiting else waiting.take(FULL_TEXT_PER_RUN)

class FullTextWorker(
    val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val okHttpClient: OkHttpClient = fullTextClient
    val repository: ArticleRepository by inject(ArticleRepository::class.java)
    private val prefs: FeedPreferences by inject(FeedPreferences::class.java)

    override suspend fun doWork(): Result {
        Log.i("FeederFullText", "Parsing full texts for articles if missing")
        // Asked again here, not only in the constraint: work enqueued before
        // the switch changed still carries the old one. And off Wi-Fi with
        // Android keeping Whisper off mobile data in the background, a run
        // lasts until Whisper leaves the screen - the report showed seven in
        // a morning stopped with nothing fetched. The next sync enqueues it
        // again, so the pages still come on Wi-Fi.
        if (!isUnmetered(context) &&
            (!prefs.fullTextOnMobile.getValue() || backgroundMobileDataBlocked(context))
        ) {
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val filesDir = context.filesDir
        // Chosen before anything is fetched, so a run with nothing to do
        // leaves no line in the history: it follows every sync, and twenty
        // lines of "nothing to fetch" would push the syncs themselves out.
        val savedOnly = inputData.getBoolean(SAVED_ONLY, false)
        val waiting = withContext(Dispatchers.IO) {
            (if (savedOnly) repository.savedArticleIdLinks()
            else repository.getFeedsItemsWithDefaultFullTextParse(
                allFeeds = prefs.fullTextForAllFeeds.getValue()
            ).firstOrNull().orEmpty())
                .filter { item ->
                    !blobFullFile(item.uuid, filesDir).isFile &&
                        shouldPrefetchFullText(now, readAttempts(item.uuid, filesDir))
                }
        }
        if (waiting.isEmpty()) {
            if (savedOnly) keepSavedImages(filesDir)
            return Result.success()
        }
        // Saved first, then newest; see fullTextBatch.
        val toFetch = fullTextBatch(waiting, savedOnly)
        val held = waiting.size - toFetch.size

        val run = SyncLog.started(applicationContext, SyncLog.ORIGIN_FULL_TEXT)
        val receivedBefore = receivedBytes()
        var fetched = 0
        var failed = 0
        var unreached = 0
        try {
            for ((i, item) in toFetch.withIndex()) {
                when (prefetchFullArticle(context, item, okHttpClient, filesDir)) {
                    Prefetch.Fetched -> fetched++
                    Prefetch.Failed -> failed++
                    // The network is gone, so is every page after this one:
                    // stop, and leave them all for the next run.
                    Prefetch.Unreached -> {
                        unreached = toFetch.size - i
                        break
                    }
                }
            }
        } catch (e: CancellationException) {
            SyncLog.finished(applicationContext, run, "stopped after $fetched")
            throw e
        }
        SyncLog.finished(
            applicationContext,
            run,
            fullTextOutcome(fetched, failed, bytesSince(receivedBefore), unreached, held),
        )
        if (savedOnly) keepSavedImages(filesDir)
        // Success whatever happened to individual pages: each failure is
        // remembered against its article, and a retry of the whole run would
        // only fetch the same refusals again.
        return Result.success()
    }

    /**
     * The pictures of every saved article whose text is here and whose
     * pictures are not; see SavedImages.
     *
     * Stops at the first sign the network has gone, and forgets the article
     * it was on, so a signal lost halfway is not recorded as that article's
     * pictures being done.
     */
    private suspend fun keepSavedImages(filesDir: File) {
        val metrics = context.resources.displayMetrics
        for (ref in repository.savedArticleRefs()) {
            if (SavedImages.isDone(ref.uuid, filesDir)) continue
            if (!blobFullFile(ref.uuid, filesDir).isFile) continue
            if (!whisperHasNetwork(context)) return
            val html = withContext(Dispatchers.IO) {
                runCatching {
                    blobFullInputStream(ref.uuid, filesDir).bufferedReader().use { it.readText() }
                }.getOrNull()
            } ?: continue
            SavedImages.download(
                itemId = ref.uuid,
                html = html,
                baseUrl = ref.link.orEmpty(),
                leadImageUrl = usableImageUrl(ref.imageUrl),
                widthPx = metrics.widthPixels,
                density = metrics.density,
                filesDir = filesDir,
            )
            if (!whisperHasNetwork(context)) {
                SavedImages.manifest(ref.uuid, filesDir).delete()
                return
            }
        }
    }
}

/**
 * One prefetch, remembered if it fails.
 *
 * A refusal the server means (see isPermanentHttpFailure) is never asked
 * again; anything else - a timeout, a dropped connection, a server error - is
 * tried up to MAX_FULL_TEXT_ATTEMPTS times, FULL_TEXT_RETRY_AFTER_MS apart.
 * Opening the article still fetches it on the spot whatever this says; this
 * only stops the background asking.
 */
private enum class Prefetch { Fetched, Failed, Unreached }

private suspend fun prefetchFullArticle(
    context: Context,
    item: ArticleIdWithLink,
    okHttpClient: OkHttpClient,
    filesDir: File,
): Prefetch {
    val (ok, error) = parseFullArticle(item, okHttpClient, filesDir)
    // parseFullArticle catches everything, the worker being stopped included;
    // that is not the page failing, and must not count against it.
    if (error is CancellationException) throw error
    if (ok) return Prefetch.Fetched
    // Nor is a connection that could not be made because Whisper had no
    // network: three of those used to spend an article's three tries in the
    // time it takes to walk out of Wi-Fi range. See countsAgainstSource.
    val code = (error as? HttpStatusException)?.code
    if (!countsAgainstSource(code, whisperHasNetwork(context))) {
        return Prefetch.Unreached
    }
    val permanent = item.link.isBlank() || error is ThinExtraction ||
        (code != null && isPermanentHttpFailure(code))
    val previous = readAttempts(item.uuid, filesDir) ?: FullTextAttempts.none
    withContext(Dispatchers.IO) {
        runCatching {
            blobFullFailedFile(item.uuid, filesDir)
                .writeText(previous.next(System.currentTimeMillis(), permanent).encode())
        }
    }
    return Prefetch.Failed
}

private fun readAttempts(uuid: String, filesDir: File): FullTextAttempts? =
    runCatching {
        blobFullFailedFile(uuid, filesDir).takeIf { it.isFile }?.readText()
    }.getOrNull()?.let(FullTextAttempts::decode)

/**
 * What the advance download waits for: Wi-Fi, unless both switches allow
 * mobile data. "Sync on Wi-Fi only" keeps everything off mobile data;
 * [fullTextOnMobile] is the second, narrower question.
 */
fun fullTextNetwork(syncOnlyOnWifi: Boolean, fullTextOnMobile: Boolean): NetworkType =
    if (syncOnlyOnWifi || !fullTextOnMobile) NetworkType.UNMETERED else NetworkType.CONNECTED

/**
 * The client full-article fetches share.
 *
 * One per process rather than one per worker or per call: OkHttp's value is
 * its connection pool, and a client made for a single request throws that away
 * before it can be used.
 */
val fullTextClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .asArticleReader()
        // The same guard the feed client has, and needed here more than there.
        // A feed address is at least something the reader typed or imported;
        // an article link is a string a publisher put in an XML file, and this
        // client fetches every one of them in the background. A hostile — or
        // merely compromised — feed listing <link>http://192.168.1.1/admin</link>
        // would otherwise have the phone reach into its own network and store
        // what came back. A network interceptor rather than a check on the
        // address, because OkHttp follows redirects itself and a public host
        // redirecting inward would walk straight past a check made up front.
        .onlyPublicHttps()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}

suspend fun parseFullArticleIfMissing(
    feedItem: ArticleIdWithLink,
    okHttpClient: OkHttpClient,
    filesDir: File
): Boolean {
    val fullArticleFile = blobFullFile(itemId = feedItem.uuid, filesDir = filesDir)
    return fullArticleFile.isFile || parseFullArticle(
        feedItem = feedItem,
        okHttpClient = okHttpClient,
        filesDir = filesDir
    ).first
}

suspend fun parseFullArticle(
    feedItem: ArticleIdWithLink,
    okHttpClient: OkHttpClient,
    filesDir: File
): Pair<Boolean, Throwable?> = withContext(Dispatchers.Default) {
    return@withContext try {
        val url = feedItem.link
        Log.d("FeederFullText", "Fetching full page ${feedItem.link}")
        val fetched = okHttpClient.fetchPage(URL(url)) ?: return@withContext false to null

        Log.i("FeederFullText", "Parsing article ${feedItem.link}")
        // Cleaned as a document first: the byline blocks and asides are only
        // recognisable by their class names, which extraction discards. See
        // stripPageChrome. In the page's own encoding; see pageDocument.
        val page = pageDocument(fetched, url).also(::stripPageChrome)
        val article = Readability4JExtended(url, page).parse()

        // A page that yields less than the feed already gave is not the
        // article: an aggregator's page (Techmeme's is a list of links) came
        // back as its timestamp alone, and replaced the feed's own paragraph
        // and picture in the reader. Kept as the feed has it instead.
        val extracted = article.textContent.orEmpty().trim().length
        if (isThinExtraction(extracted, feedTextLength(feedItem.uuid, filesDir))) {
            Log.i("FeederFullText", "Page gave less than the feed for ${feedItem.uuid}; keeping the feed's")
            return@withContext false to ThinExtraction()
        }

        // TODO set image on item if none already
        // naiveFindImageLink(article.content)?.let { Parser.unescapeEntities(it, true) }

        Log.d("FeederFullText", "Writing article ${feedItem.link}")
        withContext(Dispatchers.IO) {
            blobFullOutputStream(feedItem.uuid, filesDir).bufferedWriter().use { writer ->
                writer.write(article.contentWithUtf8Encoding)
            }
        }
        // Fetched after all, perhaps from the reader opening it: the record
        // of earlier failures no longer applies.
        withContext(Dispatchers.IO) { blobFullFailedFile(feedItem.uuid, filesDir).delete() }
        true to null
    } catch (e: Throwable) {
        Log.e(
            "FeederFullText",
            // The article's id, not its link: this ends up in a shared report.
            "Failed to get fulltext for ${feedItem.uuid}: ${e.message}",
            e
        )
        false to e
    }
}

/** The page's text came out shorter than the feed's own; see parseFullArticle. */
class ThinExtraction : java.io.IOException("the page gave less than the feed")

/**
 * Whether [extractedChars] from the page is worth less than the [feedChars]
 * the feed already carried. A full article is more than its excerpt; one that
 * is not was extracted from the wrong part of the page. A feed with no text
 * of its own takes whatever the page gives.
 */
internal fun isThinExtraction(extractedChars: Int, feedChars: Int): Boolean =
    feedChars > 0 && extractedChars < feedChars

/** The visible length of the feed's own text for an article, or 0. */
private fun feedTextLength(uuid: String, filesDir: File): Int = runCatching {
    if (!blobFile(uuid, filesDir).isFile) return@runCatching 0
    blobInputStream(uuid, filesDir).bufferedReader().use { org.jsoup.Jsoup.parse(it.readText()).text().trim().length }
}.getOrDefault(0)
