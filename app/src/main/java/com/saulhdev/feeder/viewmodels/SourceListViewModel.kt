package com.saulhdev.feeder.viewmodels

import androidx.annotation.StringRes
import com.saulhdev.feeder.R
import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.isFeedHost
import com.saulhdev.feeder.utils.registrableDomain
import com.saulhdev.feeder.utils.sameSiteGroups
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * How many sources may be kept at the top of the list.
 *
 * Five. The number is the feature: a list where everything is a pinned is
 * a list in its original order, and a small cap makes choosing one an actual
 * decision rather than a shrug. Small enough, too, that the pinned still
 * read as a group at a glance rather than as the first screenful.
 */
const val MAX_PINNED_SOURCES = 5

/** The ordering inputs, together, because combine's typed arity stops at five. */
private data class Order(
    val sort: SourceSort,
    val ascending: Boolean,
    val pinned: Set<Long>,
)

class SourceListViewModel(
    private val feedsRepo: SourcesRepository,
    private val articleRepo: ArticleRepository,
    private val prefs: FeedPreferences,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    /** What the user typed into the search field, if anything. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * One category to narrow the list to, or null for all of them.
     *
     * Separate from the search field even though searching a category's name
     * very nearly does the same thing: typing "Tech" also matches a source
     * called "Tech Review" and a URL containing the word, so the two are
     * different questions and a chip that quietly answered the looser one
     * would be a chip nobody could trust.
     */
    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> = _category.asStateFlow()

    init {
        // A chosen category can stop existing while the reader is looking at
        // it, and moving the last source out of it is all it takes — which is
        // exactly what the edit screen is for. Recategorising the only feed in
        // "Android Development" left the filter holding a name nothing carries
        // any more, so every source was filtered out; and because the chip row
        // draws the categories that exist, the chip that would have cleared it
        // was gone too. The result was an empty list with no visible cause and
        // no way back except leaving the screen and returning, which threw the
        // view model away and took the filter with it.
        //
        // So the filter follows the categories rather than outliving them.
        // Renames and merges land here as well: all three are the same event
        // seen from this side, a tag that is no longer in the set.
        ioScope.launch {
            feedsRepo.getAllTagsFlow().collect { tags ->
                _category.value = survivingCategory(_category.value, tags)
            }
        }
    }

    /** Whether the list is narrowed to sources that have a twin. */
    private val _duplicatesOnly = MutableStateFlow(false)
    val duplicatesOnly: StateFlow<Boolean> = _duplicatesOnly.asStateFlow()

    /** Ids of sources sharing an address, recomputed when the filter is armed. */
    private val _duplicateIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Whether the armed filter is the loose one.
     *
     * Two questions share the id-set machinery because they narrow the list
     * the same way, and differ entirely in what they mean. The exact filter
     * answers "which of these are literally the same address", and acting on
     * its answer is safe. This one answers "which of these look like the same
     * publication", which is a guess — so the banner has to say which is on,
     * or somebody deletes a Guardian section thinking it is a duplicate.
     */
    private val _sameSite = MutableStateFlow(false)
    val sameSite: StateFlow<Boolean> = _sameSite.asStateFlow()

    /** How many groups the loose filter found, for the banner to report. */
    /**
     * The duplicate sets, as the screen shows them: a label and its members.
     *
     * Ids rather than feeds, looked up against the live list when drawn, so a
     * source deleted while the filter is armed leaves its group rather than
     * lingering in a snapshot taken when the filter was switched on.
     */
    private val _duplicateGroups = MutableStateFlow<List<SourceGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<SourceGroup>> = _duplicateGroups.asStateFlow()

    private val _sameSiteGroupCount = MutableStateFlow(0)
    val sameSiteGroupCount: StateFlow<Int> = _sameSiteGroupCount.asStateFlow()

    /**
     * How many feeds are subscribed more than once, for the notice at the top
     * of the list.
     *
     * Offered rather than waited for. "Find duplicates" sat in the overflow
     * menu, and a list with four feeds subscribed twice never had it opened.
     * Worked out again only when a source is added or removed: a sync writes
     * to every source, and none of those writes changes the answer.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val duplicateCount: StateFlow<Int> = feedsRepo.getAllSourcesFlow()
        .map { it.size }
        .distinctUntilChanged()
        .mapLatest { feedsRepo.duplicateGroups().size }
        .stateIn(ioScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * How the list is ordered, read straight from the stored preference.
     *
     * Derived rather than held: a MutableStateFlow seeded from DataStore would
     * be a second copy of the same fact, and the two would drift the first
     * time anything else wrote to the preference.
     *
     * An unrecognised name falls back to Title. The stored value is the enum's
     * name rather than its ordinal precisely so that removing or reordering an
     * option cannot silently change what somebody's phone is sorted by.
     */
    val sort: StateFlow<SourceSort> = prefs.sourcesSort.get()
        .map(SourceSort::byName)
        .stateIn(ioScope, SharingStarted.Eagerly, SourceSort.Title)

    /** Which way round that order runs. */
    val ascending: StateFlow<Boolean> = prefs.sourcesSortAsc.get()
        .stateIn(ioScope, SharingStarted.Eagerly, true)

    /**
     * Ids picked out for a bulk action.
     *
     * Held here rather than in the screen so that a rotation, or the list being
     * rebuilt by a sync finishing mid-selection, does not silently drop what
     * the user had chosen.
     */
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    val state = combine(
        feedsRepo.getAllSourcesFlow(),
        feedsRepo.getAllTagsFlow(),
        // Three narrowing controls travelling as one value, because combine's
        // typed arity stops at five and these change together anyway.
        combine(_query, _category, _duplicatesOnly, _duplicateIds, _sameSite) { q, c, d, ids, same ->
            Narrowing(q, c, d, ids, same)
        },
        combine(sort, ascending, prefs.pinnedSources.get()) { sort, ascending, pinned ->
            Order(
                sort = sort,
                ascending = ascending,
                pinned = pinned.mapNotNull(String::toLongOrNull).toSet(),
            )
        },
        articleRepo.latestArticlePerFeed(),
    ) { allSources, allTags, narrowing, order, latestPosts ->
        val sort = order.sort
        val ascending = order.ascending
        val pinned = order.pinned
        // By name while the loose filter is on, whatever the chosen sort:
        // members of a group are only recognisable as a group when they sit
        // next to each other, and they nearly always share a title.
        val comparator = if (narrowing.sameSite) {
            compareBy<com.saulhdev.feeder.data.db.models.Feed> { it.title.lowercase() }
        } else {
            val base = sort.comparator(latestPosts)
            if (ascending) base else base.reversed()
        }
        val matching = allSources
            .filter { narrowing.keeps(it) }
            .sortedWith(comparator)
            // Pinned first, then whatever the chosen sort produced.
            // sortedBy is stable, so this lifts a handful to the top without
            // disturbing the order of anything — including the pinned
            // among themselves, which keep the sort the reader asked for
            // rather than the order they happened to tick them in.
            .sortedByDescending { it.id in pinned }
        val (enabledSources, disabledSources) = matching.partition { it.isEnabled }
        SourceListState(
            allSources = allSources,
            enabledSources = enabledSources,
            disabledSources = disabledSources,
            tagsSourcesMap = groupByTag(allSources, allTags),
            allTags = allTags,
            pinned = pinned,
        )
    }
        // A sync writes to every feed row twice — once to mark it syncing and
        // once to record when it finished — and each of those invalidates the
        // query behind this. Without conflation the list is rebuilt, sorted
        // and regrouped for every one of those writes, which is why adding a
        // source felt slow: the add triggers a sync, and the sync then talks
        // over the update the reader was waiting for.
        .conflate()
        .flowOn(Dispatchers.Default)
        .stateIn(
            ioScope,
            SharingStarted.Eagerly,
            SourceListState()
        )

    /**
     * The saved articles, fetched when something actually needs them.
     *
     * This used to be a fifth flow in the combine above, which meant a join
     * across Article and Feeds re-ran on every article a sync inserted — to
     * keep a list ready for a button pressed once in a blue moon, if ever.
     */
    /**
     * Adds or removes a pinned, refusing to go past the cap.
     *
     * Returns false when the cap stopped it, so the screen can say so. The
     * limit is the point of the feature rather than a safeguard on it: a
     * list where everything is at the top is a list in its original order,
     * and five is few enough that picking one is a decision.
     */
    suspend fun togglePinned(id: Long): Boolean {
        val current = prefs.pinnedSources.getValue()
        val key = id.toString()
        if (key in current) {
            prefs.pinnedSources.setValue(current - key)
            return true
        }
        if (current.size >= MAX_PINNED_SOURCES) return false
        prefs.pinnedSources.setValue(current + key)
        return true
    }

    /**
     * Drops a pinned that no longer names a source.
     *
     * Removing a subscription leaves its id behind in the set, which is
     * harmless until the reader has five of those and cannot pinned
     * anything. Called when the list is known, because that is the only place
     * the answer is known.
     */
    fun forgetMissingPins(existing: Set<Long>) {
        ioScope.launch {
            val current = prefs.pinnedSources.getValue()
            val alive = current.filter { (it.toLongOrNull() ?: -1L) in existing }.toSet()
            if (alive.size != current.size) prefs.pinnedSources.setValue(alive)
        }
    }

    suspend fun bookmarksForExport(): List<FeedItem> =
        articleRepo.getBookmarkedFeedItems().first()

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Narrows to one category, or clears it by picking the same one again. */
    fun setCategory(value: String?) {
        _category.value = if (_category.value == value) null else value
        if (_category.value != null) _duplicatesOnly.value = false
    }

    /**
     * Arms or clears the duplicates filter.
     *
     * The set of duplicates is computed once when the filter goes on rather
     * than kept in the state flow: it is a question asked deliberately, and
     * grouping every source by normalised URL on each of a sync's writes to
     * answer a question nobody asked is exactly the cost that made this
     * screen slow in the first place.
     */
    fun setDuplicatesOnly(value: Boolean) {
        if (!value) {
            _duplicatesOnly.value = false
            _sameSite.value = false
            _duplicateIds.value = emptySet()
            _sameSiteGroupCount.value = 0
            _duplicateGroups.value = emptyList()
            return
        }
        _category.value = null
        _query.value = ""
        ioScope.launch {
            val groups = feedsRepo.duplicateGroups()
            _duplicateIds.value = groups.flatten().map(Feed::id).toSet()
            _duplicateGroups.value = groups.map { it.toSourceGroup() }
            _sameSite.value = false
            _duplicatesOnly.value = true
        }
    }

    /**
     * Removes every copy but the oldest in each duplicate group, with the
     * usual undo.
     *
     * The oldest because it is the one the reader subscribed to: it carries
     * the read history, the time on screen and whatever the weighting learned.
     * A newer copy came from an import, a restore or a server, and holds the
     * same articles with none of that.
     */
    fun removeDuplicateCopies() {
        val extra = _duplicateGroups.value.flatMap { it.ids.sorted().drop(1) }.toSet()
        if (extra.isEmpty()) return
        viewModelScope.launch {
            feedsRepo.deleteSources(extra)
            setDuplicatesOnly(false)
        }
    }

    /**
     * Arms the loose filter: sources that look like the same publication.
     *
     * Catches what the exact filter cannot, which is most of what a list this
     * old actually contains — a FeedBurner alias beside a site's own address,
     * two paths to one feed, a publication subscribed to twice under slightly
     * different names. It is a suggestion, and the screen says so.
     */
    fun setSameSiteOnly() {
        _category.value = null
        _query.value = ""
        ioScope.launch {
            val groups = sameSiteGroups(
                feedsRepo.getAllSources(),
                { it.url },
                { it.title },
            )
            _duplicateIds.value = groups.flatten().map(Feed::id).toSet()
            _duplicateGroups.value = groups.map { it.toSourceGroup() }
            _sameSiteGroupCount.value = groups.size
            _sameSite.value = true
            _duplicatesOnly.value = true
        }
    }

    /**
     * Picks an order, or reverses the one already picked.
     *
     * Choosing the row that is already chosen is not a no-op — it is the only
     * way to ask for the other direction, and it is what tapping a sorted
     * column header does everywhere else. Choosing a different one always
     * starts ascending rather than inheriting the last direction, so the same
     * tap always produces the same result.
     */
    fun setSort(value: SourceSort) {
        // On the IO scope because setValue is a blocking DataStore write, and
        // this is called straight from a tap.
        ioScope.launch {
            if (sort.value == value) prefs.sourcesSortAsc.setValue(!ascending.value)
            else {
                prefs.sourcesSort.setValue(value.name)
                prefs.sourcesSortAsc.setValue(true)
            }
        }
    }

    /* Selection */

    /**
     * Where the last deliberate pick was made, for extending a range from.
     *
     * Cleared with the selection: an anchor pointing at a source nobody has
     * selected would make the next long-press sweep up an arbitrary run of
     * the list.
     */
    private var anchor: Long? = null

    fun toggleSelected(id: Long) {
        _selection.value = if (id in _selection.value) _selection.value - id
        else _selection.value + id
        anchor = id
    }

    /**
     * Takes everything between the last pick and this one.
     *
     * `shown` is the list as drawn, so the run selected is the run the user
     * can see between their finger and the anchor — sorting or filtering the
     * list changes what "between" means, and the answer should be the one on
     * screen rather than one computed from the database's order.
     *
     * With no anchor, or an anchor that has since been filtered away, this is
     * an ordinary toggle. Extending from nothing has no sensible meaning, and
     * guessing one selects rows the user never pointed at.
     */
    fun extendSelection(shown: List<Long>, id: Long) {
        val run = rangeBetween(shown, anchor, id)
        if (run.isEmpty()) {
            toggleSelected(id)
            return
        }
        _selection.value = _selection.value + run
        anchor = id
    }

    fun selectAll(ids: Collection<Long>) {
        _selection.value = _selection.value + ids
    }

    fun clearSelection() {
        _selection.value = emptySet()
        anchor = null
    }

    /* Bulk actions. Each one clears the selection: leaving twelve sources
       highlighted after acting on them invites acting on them twice. */

    fun setSelectedEnabled(enabled: Boolean) {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { feedsRepo.setEnabled(ids, enabled) }
    }

    fun editSelectedTags(
        add: Set<String> = emptySet(),
        remove: Set<String> = emptySet(),
        replaceWith: Set<String>? = null,
    ) {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { feedsRepo.editTags(ids, add, remove, replaceWith) }
    }

    fun deleteSelected() {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { feedsRepo.deleteSources(ids) }
    }

    /**
     * Empties the selected sources, keeping the sources themselves.
     *
     * Reports the number of articles removed through [articlesCleared], which
     * the screen drains into a snackbar: bookmarked and pinned articles are
     * kept, so the count is the only honest way to say what happened.
     */
    private val _articlesCleared = MutableStateFlow<Int?>(null)
    val articlesCleared: StateFlow<Int?> = _articlesCleared.asStateFlow()

    fun clearSelectedArticles() {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { _articlesCleared.value = feedsRepo.clearArticles(ids) }
    }

    fun forgetArticlesCleared() {
        _articlesCleared.value = null
    }

    /** Turns full-article fetching on or off across the selection. */
    fun setSelectedFullText(enabled: Boolean) {
        val ids = _selection.value
        clearSelection()
        viewModelScope.launch { feedsRepo.setFullTextByDefault(ids, enabled) }
    }

    /* Category management */

    /** The categories in [order], as the reader dragged them. */
    fun setCategoryOrder(order: List<String>) = feedsRepo.setCategoryOrder(order)

    fun renameTag(from: String, to: String) {
        viewModelScope.launch { feedsRepo.renameTag(from, to) }
    }

    fun deleteTag(tag: String) {
        viewModelScope.launch { feedsRepo.deleteTag(tag) }
    }

    val recentlyDeletedMany = feedsRepo.recentlyDeletedMany

    fun undoDeleteSources() = feedsRepo.undoDeleteSources()

    fun forgetDeletedSources() = feedsRepo.forgetDeletedSources()

    fun insertFeed(feed: Feed) {
        viewModelScope.launch {
            feedsRepo.insertSource(feed)
        }
    }

    fun updateFeed(source: Feed, enable: Boolean) {
        viewModelScope.launch {
            feedsRepo.updateSource(source, enable)
        }
    }

    /**
     * Subscribes to one search result.
     *
     * Deliberately one, not a list: this used to take every result the search
     * produced and add all of them when the screen closed.
     */
    fun addFeed(result: SearchResult) {
        if (result.isError) return
        viewModelScope.launch {
            val url = sloppyLinkToStrictURL(result.url)
            // Re-checked here rather than trusted from the UI: results can be
            // minutes old, and Feeds.url is uniquely indexed with an insert
            // strategy of REPLACE — an unnoticed duplicate would delete the
            // original row and cascade to its articles.
            if (feedsRepo.findSourceByUrl(url) != null) return@launch
            feedsRepo.insertSource(
                // feedImage is the source's *mark*, not its address. Writing
                // the feed URL here made every new subscription claim an icon
                // it does not have, which the card would then try to load.
                // Sync fills it in properly on the first fetch.
                Feed(
                    title = result.title,
                    description = result.description,
                    url = url,
                )
            )
        }
    }

    /**
     * Files a source under a category, found by its address.
     *
     * By url rather than by id because the screen that needs this has just
     * added the feed and never saw the id — the insert happens in a coroutine
     * and returns nothing. The url is what the reader was looking at, and it
     * is unique in the table.
     */
    fun setCategory(url: String, tag: String) {
        viewModelScope.launch {
            val feed = feedsRepo.findSourceByUrl(sloppyLinkToStrictURL(url)) ?: return@launch
            feedsRepo.updateSource(feed.copy(tag = tag))
        }
    }

    val recentlyDeleted = feedsRepo.recentlyDeleted

    fun undoDelete() = feedsRepo.undoDeleteSource()

    fun forgetDeleted() = feedsRepo.forgetDeletedSource()
}

data class SourceListState(
    val allSources: List<Feed> = emptyList(),
    val enabledSources: List<Feed> = emptyList(),
    val disabledSources: List<Feed> = emptyList(),
    val tagsSourcesMap: Map<String, List<Feed>> = emptyMap(),
    val allTags: List<String> = emptyList(),
    /** Ids of the sources kept at the top, so a row can show it is one. */
    val pinned: Set<Long> = emptySet(),
) {
    /**
     * The sources actually on screen, in the order they are drawn.
     *
     * Select-all and range-select both work from this rather than from
     * [allSources]. Select-all used to take every source in the database,
     * which meant searching for "bbc", starting a selection and tapping
     * Select all quietly picked up two hundred sources the user could not
     * see — in a bar whose next button is Delete.
     */
    val shownSources: List<Feed> get() = enabledSources + disabledSources
}

/**
 * How the source list is ordered.
 *
 * Each comparator here is the *ascending* one; the view model reverses it when
 * the reader taps the order they are already on.
 *
 * The comparator is a function rather than a value because one of these needs
 * something that is not on a Feed: when its newest article arrived. That lives
 * in the Article table, so it is handed in.
 */
enum class SourceSort(@StringRes val labelId: Int) {
    Title(R.string.sort_by_title) {
        override fun comparator(latestPosts: Map<Long, Long>) =
            compareBy(String.CASE_INSENSITIVE_ORDER, Feed::title)
    },

    Category(R.string.sort_by_category) {
        override fun comparator(latestPosts: Map<Long, Long>) =
            compareBy(String.CASE_INSENSITIVE_ORDER) { feed: Feed ->
                feed.tags.firstOrNull().orEmpty()
            }
    },

    /**
     * Oldest first ascending, newest first descending.
     *
     * There is no "added" column and none is needed: `Feeds.id` is
     * autoGenerate, so id order is insertion order.
     *
     * Two honest caveats. An OPML import arrives in file order, so a hundred
     * sources imported at once are "added" in whatever order the file listed
     * them rather than all at the same moment. And undoing a removal
     * re-inserts under a fresh id, so a restored source counts as newly added.
     */
    Added(R.string.sort_by_added) {
        override fun comparator(latestPosts: Map<Long, Long>) = compareBy(Feed::id)
    },

    /**
     * When the feed itself last published, which is the question people
     * actually mean by "least recently updated".
     *
     * This replaces a sort on `Feeds.lastSync`, and the difference matters:
     * lastSync is when *Whisper* last fetched the feed, so a feed checked
     * faithfully every hour that has published nothing since March had the
     * most recent lastSync in the list. It answered "which feeds am I failing
     * to reach" when what was wanted was "which feeds have gone quiet".
     *
     * Ascending puts the quietest first, which is the useful end.
     *
     * A feed with no articles at all sorts as zero — before everything —
     * which is right: it is the quietest of the lot, and usually broken.
     */
    LatestPost(R.string.sort_by_latest_post) {
        override fun comparator(latestPosts: Map<Long, Long>) =
            compareBy { feed: Feed -> latestPosts[feed.id] ?: 0L }
    },
    ;

    abstract fun comparator(latestPosts: Map<Long, Long>): Comparator<Feed>

    companion object {
        /**
         * The order stored under this name, or the default if there isn't one.
         *
         * Stored by name rather than by ordinal, and resolved rather than
         * indexed, because a preference outlives the code that wrote it. An
         * order removed in a later version leaves that name sitting in
         * somebody's DataStore, and an ordinal would quietly resolve it to
         * whichever order happens to sit at that position now. A name that no
         * longer exists resolves to nothing, and nothing means Title.
         */
        fun byName(name: String?): SourceSort =
            entries.firstOrNull { it.name == name } ?: Title
    }
}

/**
 * Every tag mapped to the sources carrying it, plus "" for the untagged.
 *
 * One pass over the sources rather than one pass per tag. `Feed.tags`
 * splits a comma-separated string every time it is read, so the old shape
 * — filtering the whole list once for each tag — split every source's tags
 * once per tag that exists. Ten categories and a hundred sources meant a
 * thousand string splits, on every one of those sync writes.
 *
 * OPML export reads this map, and a source in no bucket is a source that
 * does not get exported.
 */
internal fun groupByTag(sources: List<Feed>, tags: List<String>): Map<String, List<Feed>> {
    val byTag = tags.associateWithTo(HashMap()) { mutableListOf<Feed>() }
    val untagged = mutableListOf<Feed>()
    sources.forEach { source ->
        // Matched against the split list rather than with `tag.contains`,
        // which made "New" match a source tagged "News" — and made the ""
        // bucket match every source, so an export wrote each feed twice.
        val own = source.tags
        if (own.isEmpty()) untagged += source
        else own.forEach { byTag[it]?.add(source) }
    }
    return byTag + ("" to untagged)
}

/**
 * The ids between two picks, in the order the list is drawn.
 *
 * Empty when there is nothing to extend from — no anchor, or an anchor that
 * has since been filtered off the screen — which the caller treats as a plain
 * toggle. Selecting a run computed from a row the user can no longer see would
 * sweep up an arbitrary stretch of the list.
 *
 * The two ends are included, and the direction does not matter: dragging a
 * selection upwards is the same gesture as dragging it down.
 */
internal fun rangeBetween(shown: List<Long>, anchor: Long?, id: Long): List<Long> {
    val from = shown.indexOf(anchor ?: return emptyList())
    val to = shown.indexOf(id)
    if (from < 0 || to < 0) return emptyList()
    val range = if (from <= to) from..to else to..from
    return range.map(shown::get)
}

/**
 * The three ways the list can be narrowed, as one value.
 *
 * They compose rather than replace one another — a search inside a category is
 * a reasonable thing to want — with one exception enforced at the setters:
 * arming the duplicates filter clears the other two, because "duplicates, but
 * only the ones matching 'bbc'" is a question nobody is asking while trying to
 * clean up a list.
 */
internal data class Narrowing(
    val query: String = "",
    val category: String? = null,
    val duplicatesOnly: Boolean = false,
    val duplicateIds: Set<Long> = emptySet(),
    val sameSite: Boolean = false,
) {
    fun keeps(feed: Feed): Boolean {
        if (duplicatesOnly && feed.id !in duplicateIds) return false
        if (category != null && category !in feed.tags) return false
        return feed.matches(query)
    }
}

/**
 * Whether a source matches what was typed in the search field.
 *
 * Title, address and categories all count: people look for a source by the
 * name they gave it, by the site it comes from, or by what they filed it
 * under, and which of the three they reach for is not predictable.
 */
internal fun Feed.matches(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return title.contains(q, ignoreCase = true) ||
            url.toString().contains(q, ignoreCase = true) ||
            tags.any { it.contains(q, ignoreCase = true) }
}

/**
 * The chosen category, if it still exists, and otherwise nothing.
 *
 * A function rather than two lines inside a collector, so the rule can be
 * argued with in a test instead of only on a phone. The bug it fixes was
 * invisible in every other way: the list was empty, the cause was a string
 * held by a view model, and the only cure was leaving the screen.
 */
internal fun survivingCategory(chosen: String?, tags: List<String>): String? =
    chosen?.takeIf { it in tags }

/**
 * One set of sources that look like the same thing, with something to call it.
 *
 * @param label what the heading says: the site they share, or their common
 *   title where the site is a feed service and says nothing about who
 *   publishes them.
 * @param ids the members, in the order the grouping found them.
 */
data class SourceGroup(val label: String, val ids: List<Long>)

internal fun List<Feed>.toSourceGroup() = SourceGroup(
    label = groupLabel(this),
    ids = map(Feed::id),
)

/**
 * What to call a group of duplicates.
 *
 * The site they share, which is what the reader recognises and what the
 * grouping was mostly done on. A feed service is no answer — sixteen
 * publications behind one FeedBurner domain are sixteen publications, and
 * naming the group "feedburner.com" would describe the plumbing rather than
 * the thing — so those fall back to the title the members have in common,
 * which is what joined them in the first place.
 */
internal fun groupLabel(feeds: List<Feed>): String {
    val site = feeds.asSequence()
        .mapNotNull { runCatching { it.url.host }.getOrNull() }
        .filterNot(::isFeedHost)
        .map(::registrableDomain)
        .firstOrNull { it.isNotBlank() }
    if (site != null) return site
    return feeds.firstOrNull { it.title.isNotBlank() }?.title.orEmpty()
}
