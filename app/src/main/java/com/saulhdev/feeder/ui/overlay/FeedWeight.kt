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
package com.saulhdev.feeder.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.db.models.FeedItem
import com.saulhdev.feeder.data.db.models.SourceEngagement
import com.saulhdev.feeder.data.db.models.SourcePace
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.utils.READ_DIM
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.asState

/**
 * How much of the screen an article has earned.
 *
 * The Mosaic sizes were positional — item 0 and every eleventh got the big
 * tile, whatever they happened to be — which meant a two-line aggregator stub
 * could take both columns while the story of the day sat in a thumbnail. The
 * weight below is a property of the *article*, so it does not change when a
 * sync reorders the feed: the same article gets the same size wherever it
 * lands, which is the thing the positional rule was protecting against and
 * gets for free here.
 *
 * Every signal is something already known locally. Nothing is inferred from a
 * server, and nothing here needs an account.
 *
 * The numbers are deliberately blunt and all in one place. They are the part
 * that wants tuning against a real feed rather than reasoning.
 */
object ArticleWeight {

    /** No picture, so the big shapes are out however good the article is. */
    const val NO_IMAGE = 0f

    /** Every article with an image starts here. */
    const val BASE = 1f

    /**
     * At or above this an article can take both columns.
     *
     * Two point two put the hero out of reach of anything more than six hours
     * old. Freshness is the only term here with real range — plus 1.2 down to
     * minus 0.4, where everything else moves the total by a few tenths — so the
     * band an article landed in was, in practice, its age. A feed read in the
     * evening is a feed of afternoon articles, and a probe over a plausible
     * sixty-article sync produced exactly one large tile, which was the opening
     * anchor rather than anything the scores had chosen.
     *
     * Two point zero brings the whole of today within reach, which hands the
     * question of how many heroes there are to [LARGE_GAP], where it belongs:
     * the weight says an article is worth the big slot, the gap says not this
     * one, not yet. Those are different questions and they were being answered
     * by the same number.
     */
    const val LARGE_AT = 2.0f

    /**
     * At or above this it keeps its own column at full height.
     *
     * Low enough that a picture is close to sufficient. An article with an
     * image, a headline of ordinary length and a line of summary scores 1.3 at
     * its oldest, and at 1.6 that article was a thumbnail row — so a feed more
     * than three days stale rendered as a list of rows with pictures beside
     * them, whatever was in it.
     *
     * The judgement is that a picture earns the card and the penalties take it
     * away again: read is minus 1.5 and drops straight through this, and so
     * does a twenty-word headline with no summary. Small is for what has been
     * marked down, not for the ordinary case.
     */
    const val MEDIUM_AT = 1.2f

    /**
     * How many tiles must pass between two large ones.
     *
     * Without it a burst of fresh articles from a favourite source — exactly
     * what a morning sync produces — turns the whole first screen into full
     * width tiles, which is a list, not a mosaic.
     *
     * Four rather than six now that [LARGE_AT] is reachable. Six was never the
     * binding constraint — the scores were — so it was spacing heroes that did
     * not exist. At one in five, with a hero around 400dp and a card around
     * 300, roughly every other screenful opens on one.
     */
    const val LARGE_GAP = 4

    /**
     * The most a reading habit can add.
     *
     * Deliberately smaller than a single freshness step. "Favour what they
     * read" converges on one site if you let it — shown more, so read more, so
     * weighted higher — and the counter to that is a cap plus the structural
     * rules below, not a smaller number alone. This is enough to break a tie
     * between two similar articles and never enough to outrank a fresh story
     * from somewhere else.
     */
    const val HABIT_MAX = 0.4f

    /** How far back reading habits are counted. */
    const val HABIT_WINDOW_DAYS = 30L

    /*
     * Freshness measured against the source's own pace.
     *
     * The absolute curve below is right for news and wrong for everything
     * else. It gives its largest step to the first two hours and turns
     * negative after three days, which describes a wire service exactly and a
     * weekly blog not at all: Hackaday, Quanta and most of what anybody
     * actually subscribes to publish on a rhythm where three days old is the
     * newest thing there is. Under the absolute curve alone those sources
     * could not reach the hero band at any hour of any day, and a feed of them
     * drew as a list of rows however well it scored on everything else.
     *
     * So freshness is asked twice — how old is this, and how old is this *for
     * this source* — and the better answer wins. Taking the better rather than
     * replacing the first is what makes this a lift for slow feeds instead of
     * a demotion for fast ones: a wire story an hour old still scores what it
     * always did.
     */

    /**
     * The fastest a source is allowed to be considered.
     *
     * Without a floor, a firehose posting every few minutes would have a pace
     * so short that an article an hour old was dozens of intervals stale, and
     * the relative answer would start pulling news *down* — which is the
     * opposite of the point. Two hours.
     */
    const val PACE_MIN_HOURS = 2f

    /**
     * And the slowest. A month between posts is treated as a week.
     *
     * A monthly publication would otherwise have a fortnight-old article
     * scoring as brand new, and a feed surface that gives its biggest slot to
     * something from a fortnight ago is not one anybody would trust — however
     * defensible the arithmetic behind it.
     */
    const val PACE_MAX_HOURS = 168f

    /**
     * Past this, the relative answer cannot claim more than "today".
     *
     * The backstop on the whole idea. A slow source's newest article is worth
     * promoting; it is not worth promoting *as breaking*, and three days is
     * where the difference stops being arguable.
     */
    const val RELATIVE_CAP_HOURS = 72f

    /** And past this it cannot claim anything at all. */
    const val RELATIVE_STALE_HOURS = 168f

    /*
     * The quiet-source rescue.
     *
     * A source can be one somebody reads almost everything from and still
     * disappear from their feed, because the only thing the sizing asks about
     * time is how long ago an article was published. Go quiet for a week and
     * your newest piece is a week old, scores accordingly, and sits in a row
     * at the bottom — the reader never sees that you are back.
     *
     * This is the one place the weighting acts on an *absence*. Everything
     * else here scores what is in front of it.
     */

    /** Silence this long makes a source worth surfacing again. */
    const val QUIET_AFTER_HOURS = 168f

    /**
     * How much of the reader's attention a source needs to earn a rescue.
     *
     * Half of what their most-read source gets. Deliberately high: this rule
     * overrides the spacing that stops one publication owning the screen, so
     * it has to be rare, and "a source you read about as much as your
     * favourite" is rare by construction.
     */
    const val RESCUE_HABIT_MIN = 0.5f

    /** At most this many rescues in one feed, however many sources went quiet. */
    const val RESCUE_MAX = 2

    /*
     * Fading a source the reader keeps skipping.
     *
     * The mirror of the rescue, and the reason both are cautious: this is the
     * weighting acting on a judgement the reader never stated, and the cost of
     * being wrong is that somebody's feed greys out a source they did in fact
     * want. It is therefore off unless asked for, needs a lot of evidence, and
     * fades rather than hides.
     */

    /**
     * How many of a source's articles must have gone past before it can be
     * judged at all.
     *
     * Thirty. A handful proves nothing — a busy week, a run of pieces on a
     * subject somebody was not in the mood for — and the difference between a
     * source that is genuinely not wanted and one that had a bad fortnight is
     * mostly a matter of how long you watched.
     */
    const val SKIPPED_MIN_SEEN = 30

    /**
     * The most engagement per article a skipped source may show.
     *
     * Half of [BAND_GLANCED], so this means "on average, not even a glance".
     * A source clearing this is one whose headlines the reader has stopped on
     * often enough to be worth keeping bright, whatever the totals say.
     */
    const val SKIPPED_SCORE_MAX = 0.5f

    /**
     * How many articles a source needs before its pace is worth estimating.
     *
     * Three, which is two intervals. One article gives no interval and two
     * give a single gap that a public holiday would distort; below this the
     * source simply has no relative answer and is scored the old way.
     */
    const val PACE_MIN_ARTICLES = 3

    /**
     * What having already read an article costs it.
     *
     * Enough to keep a read article out of the large slot whatever else it
     * has going for it: a fresh, well-titled article with a summary scores
     * 2.9, and 2.0 puts it at 0.9. It no longer decides the smaller sizes —
     * feedEmphasisFor keeps a read article at Medium if it would have been
     * Medium or larger unread — so it only ever costs the hero.
     */
    const val READ_PENALTY = 2.0f

    /*
     * What an encounter with an article is worth, in four bands.
     *
     * The habit term used to be a count of articles with readAt set, and an
     * article gets readAt set by being scrolled past — forty of them in a
     * flick of the thumb. So the signal the ordering trusted most was mostly a
     * record of scrolling speed, and a source whose headlines somebody hurried
     * past looked exactly like one they stopped to read.
     *
     * These separate the two. The numbers are relative, not absolute: the sum
     * per source is divided by the largest, so only the ratios matter, and
     * they are constants here precisely so they can be argued with.
     */

    /**
     * Scrolled past with no time on screen worth counting.
     *
     * Zero, and that is the point. Passing something is not evidence of
     * wanting it, and treating the absence of interest as a small amount of
     * interest is how the old count went wrong. A source scrolled past a
     * hundred times scores what a source never seen scores, because that is
     * what the reader has told us about it: nothing.
     */
    const val BAND_PASSED = 0

    /** On screen a second or two. A glance, and worth about as much. */
    const val BAND_GLANCED = 1

    /** Stopped at for five seconds or more: read in place, or nearly. */
    const val BAND_HELD = 3

    /**
     * Opened, and nothing more known.
     *
     * Eight, so that one article somebody chose to open outweighs a handful
     * they merely lingered on.
     *
     * This used to be the top of the scale, and it was carrying more than it
     * could bear: opening an article and bouncing straight back out is a
     * judgement that it was not worth reading, and it scored the same as
     * reading the thing to the end. The two bands above separate them.
     */
    const val BAND_OPENED = 8

    /**
     * Opened and stayed a while: read, rather than merely opened.
     *
     * Reached by either clock: the in-app one that ticks while the article is
     * on screen, or the coarser browser one that measures how long the app was
     * away. An article whose reading could not be measured at all — a browser
     * trip too long to trust, or a process killed mid-read — scores
     * [BAND_OPENED] rather than less. Unmeasured is not the same as read for
     * no time, and must not be punished as though it were.
     */
    const val BAND_READ = 14

    /**
     * Stayed long enough to have read most of it.
     *
     * Twenty rather than something larger because these are divided by the
     * largest before use: what matters is that a source whose articles get
     * read properly outweighs one whose articles get opened and abandoned by
     * about two and a half to one, not the figure itself.
     */
    const val BAND_FINISHED = 20

    /** Time on screen that separates a glance from a look. */
    const val GLANCED_MS = 1_000L

    /** And a look from having read it where it sat. */
    const val HELD_MS = 5_000L

    /**
     * Time inside an article that separates opening it from reading it.
     *
     * Half a minute. Short enough that a genuine skim of a short piece
     * counts, long enough that opening something, seeing what it is and
     * coming straight back does not.
     */
    const val READING_MS = 30_000L

    /** And reading it from having read the whole thing. */
    const val FINISHED_MS = 120_000L

    /**
     * What a story several sources are covering is worth.
     *
     * The largest single term, and deliberately so. This is not a second
     * mechanism sitting beside the weighting and competing with it for the
     * hero slot — it is the weighting's answer to the same question everything
     * else here answers, and the answer that ought to win. Scaled by how many
     * sources are covering it, because three is a story and eight is the story.
     */
    fun breakingBonus(sources: Int): Float = when {
        sources < Clustering.MIN_SOURCES -> 0f
        sources < 5                      -> 1.2f
        sources < 8                      -> 1.6f
        else                             -> 2.0f
    }

    /**
     * How many items must pass before a source may take a second large slot.
     *
     * The first of the two structural diversity rules. A coefficient can be
     * tuned down but it still compounds; this cannot. Whatever the weights
     * say, one source does not own the screen.
     *
     * Measured in items, so it has to move with [LARGE_GAP] to keep meaning
     * the same thing. Twenty-four items when heroes were seven apart meant a
     * source could hold one hero in about three; sixteen when they are four
     * apart means the same, which is the ratio that was argued for rather
     * than the number that expressed it.
     */
    const val SAME_SOURCE_LARGE_GAP = 16

    /**
     * The longest run of articles from one source before the rest are
     * displaced further down.
     *
     * Displaced, never dropped: a source posting six times in an hour is still
     * six articles the reader subscribed to, and they stay in the feed. They
     * just stop being the whole of it.
     */
    const val MAX_CONSECUTIVE_FROM_SOURCE = 3

    /**
     * How far down the feed to look for an opening anchor.
     *
     * A feed of nothing but yesterday's articles scores nothing above
     * [LARGE_AT], and opening on a flat grid of small tiles reads as a bug. The
     * best of the first few gets promoted instead — best of what is actually
     * there, rather than whatever sits at index 0.
     */
    const val ANCHOR_WITHIN = 6
}

/**
 * Freshness on the clock, which is the right question for news.
 *
 * Unchanged from what this has always done, and still the only answer for a
 * source whose pace is unknown.
 */
fun absoluteFreshness(ageHours: Float): Float = when {
    ageHours < 2f  -> 1.2f
    ageHours < 6f  -> 0.8f
    ageHours < 24f -> 0.4f
    ageHours < 72f -> 0f
    else           -> -0.4f
}

/**
 * Freshness measured in how many of this source's own intervals have passed.
 *
 * A post half an interval old is the newest thing that source has, whether
 * that is twenty minutes on a wire or four days on a blog. Six intervals is
 * old by the same reasoning.
 *
 * Returns null when there is nothing to say — no pace known — so the caller
 * can tell "not fresh" from "no opinion", which matters because the two are
 * combined by taking the better and null must not win.
 */
fun relativeFreshness(ageHours: Float, paceHours: Float?): Float? {
    if (paceHours == null || paceHours <= 0f) return null
    val pace = paceHours.coerceIn(ArticleWeight.PACE_MIN_HOURS, ArticleWeight.PACE_MAX_HOURS)
    val intervals = ageHours / pace
    val score = when {
        intervals < 0.25f -> 1.2f
        intervals < 0.75f -> 0.8f
        intervals < 2f    -> 0.4f
        intervals < 6f    -> 0f
        else              -> -0.4f
    }
    // The backstops. However slow the source, the calendar still applies.
    return when {
        ageHours > ArticleWeight.RELATIVE_STALE_HOURS -> minOf(score, 0f)
        ageHours > ArticleWeight.RELATIVE_CAP_HOURS   -> minOf(score, 0.4f)
        else                                          -> score
    }
}

/**
 * The freshness term: the better of the two readings.
 *
 * Better rather than blended, and better rather than replaced. A blend would
 * dilute both answers into one that is right for neither kind of source, and
 * replacing the absolute answer would mark down the news feeds that the
 * absolute answer was correct about all along.
 */
fun freshnessFor(ageHours: Float, paceHours: Float?): Float =
    maxOf(absoluteFreshness(ageHours), relativeFreshness(ageHours, paceHours) ?: -Float.MAX_VALUE)

/**
 * How often each source publishes, in hours, from the spans the query returns.
 *
 * The mean interval, not the median: the median would mean carrying every
 * timestamp out of the database to sort them, and the clamping either side of
 * this makes the difference between the two academic. A source whose whole
 * history arrived in one backfill has a span near zero and lands on the floor,
 * which is the old behaviour and the right fallback.
 */
fun sourcePaceHours(rows: List<SourcePace>): Map<Long, Float> =
    rows.mapNotNull { row ->
        if (row.articles < ArticleWeight.PACE_MIN_ARTICLES) return@mapNotNull null
        val span = (row.newest - row.oldest).coerceAtLeast(0L)
        val gaps = (row.articles - 1).coerceAtLeast(1)
        row.feedId to (span.toFloat() / gaps / 3_600_000f)
    }.toMap()

/**
 * The weight for one article.
 *
 * @param affinity "more like this" scores by source id, as recorded by the
 *   article menu. This is the first thing that reads them back.
 * @param nowMs passed in rather than read, so a list is scored against one
 *   instant and an article near a boundary cannot be scored twice at two
 *   different ages within the same pass.
 */
fun articleWeight(
    item: FeedItem,
    affinity: Map<String, Int>,
    nowMs: Long,
    habit: Map<Long, Float> = emptyMap(),
    clusters: Map<String, StoryCluster> = emptyMap(),
    pace: Map<Long, Float> = emptyMap(),
): Float {
    if (item.imageUrl.isNullOrBlank()) return ArticleWeight.NO_IMAGE

    var weight = ArticleWeight.BASE

    // Freshness, asked both ways: how old is this, and how old is this for the
    // source it came from. A news surface that gives its biggest slot to
    // something from Tuesday is not a news surface — unless Tuesday is the
    // last time that source published anything, which is the case the second
    // reading exists for.
    val ageHours = ((nowMs - item.timeMillis).coerceAtLeast(0L)) / 3_600_000f
    weight += freshnessFor(ageHours, pace[item.feed.id])

    // What the reader has said about the source, clamped so a dozen taps on
    // one source cannot make every one of its articles large for ever.
    weight += (affinity[item.sourceId] ?: 0).coerceIn(-3, 3) * 0.35f

    // Headline length. This is about the shape on screen, not the writing: a
    // twenty-word headline set at titleMedium fills the large tile and pushes
    // the summary off it, which wastes the width it was given.
    val words = item.contentTitle.trim().split(Regex("\\s+")).size
    weight += when {
        words < 3   -> -0.2f
        words <= 12 -> 0.4f
        words <= 18 -> 0f
        else        -> -0.25f
    }

    // The large tile shows a line of summary. Without one it is a picture and a
    // headline in a lot of empty space.
    weight += if (item.article.description.isNotBlank()) 0.3f else -0.3f

    // Several sources on the same story. Only the lead is promoted: the others
    // are the same headline, and five large cards saying it would be worse
    // than one. They keep whatever they earned on their own.
    clusters[item.id]?.let { cluster ->
        if (cluster.leadId == item.id) weight += ArticleWeight.breakingBonus(cluster.sources)
    }

    // What the reader actually reads, as opposed to what they said. Taps on
    // "more like this" are rare and deliberate; opening an article is neither,
    // which is why this is capped so much lower than affinity.
    weight += (habit[item.feed.id] ?: 0f) * ArticleWeight.HABIT_MAX

    // Already read: it has had its turn — unless it is pinned, which is the
    // reader saying it has not. A pin is held at the top of the feed until it
    // is unpinned, so it is read within moments of being opened once; taking
    // two points off it then would shrink the card of the story somebody is
    // deliberately following, which is the pin failing at its only job.
    //
    // Two rather than the one and a half this started at, because the penalty
    // has to clear [ArticleWeight.MEDIUM_AT] and that floor came down. At 1.5
    // against a floor of 1.2, an article read an hour ago still scored 1.4 and
    // kept its card — so the strongest negative signal there is, the reader
    // having actually seen the thing, stopped being able to shrink anything
    // fresh. This is the one term that should beat freshness outright.
    if (readForSize(item)) weight -= ArticleWeight.READ_PENALTY

    // Saved, and pinned, are the reader saying this one matters.
    if (item.bookmarked) weight += 0.4f
    if (item.pinned) weight += 1.5f

    return weight
}

/** Whether the read penalty applies: read, and not pinned. */
private fun readForSize(item: FeedItem): Boolean = item.article.readAt != 0L && !item.pinned

/**
 * Sizes for a whole list, in order.
 *
 * A list rather than a per-item function because two of the rules are about
 * neighbours — large tiles have to be spaced out, and the top of the feed needs
 * an anchor — and neither can be answered by looking at one article.
 */
fun feedEmphasisFor(
    items: List<FeedItem>,
    affinity: Map<String, Int>,
    nowMs: Long,
    habit: Map<Long, Float> = emptyMap(),
    clusters: Map<String, StoryCluster> = emptyMap(),
    pace: Map<Long, Float> = emptyMap(),
): List<FeedEmphasis> {
    val weights = items.map { articleWeight(it, affinity, nowMs, habit, clusters, pace) }
    val sizes = MutableList(items.size) { FeedEmphasis.Small }

    var lastLarge = -ArticleWeight.LARGE_GAP - 1
    // The second structural rule: a source that has just had the big slot does
    // not get another one straight away, however well its next article scores.
    // Without it, a morning sync from a favourite source takes every large slot
    // on the screen and the feed reads as one publication.
    val lastLargeBySource = HashMap<String, Int>()
    weights.forEachIndexed { index, weight ->
        val source = items[index].sourceId
        val sourceGap = index - (lastLargeBySource[source] ?: Int.MIN_VALUE / 2)
        sizes[index] = when {
            weight >= ArticleWeight.LARGE_AT &&
                    index - lastLarge > ArticleWeight.LARGE_GAP &&
                    sourceGap > ArticleWeight.SAME_SOURCE_LARGE_GAP -> {
                lastLarge = index
                lastLargeBySource[source] = index
                FeedEmphasis.Large
            }

            // A large-weight article that lands inside either gap still
            // deserves more than the smallest tile.
            weight >= ArticleWeight.MEDIUM_AT                       -> FeedEmphasis.Medium

            // Read, but it would have earned a card unread: it keeps the card.
            // The penalty still costs it the large slot; it no longer drops it
            // to a thin row. Reads arrive from the account too, so on a second
            // device most of the feed was read on the first before it was
            // ever drawn here, and a tablet showed a wall of rows where the
            // phone showed cards for the same articles.
            readForSize(items[index]) &&
                    weight + ArticleWeight.READ_PENALTY >= ArticleWeight.MEDIUM_AT -> FeedEmphasis.Medium

            else                                                    -> FeedEmphasis.Small
        }
    }

    // The quiet-source rescue, before the anchor so a rescued article can be
    // what the feed opens on.
    //
    // Applied after the ordinary pass rather than as a term in the weight,
    // because it is a statement about a source's silence rather than about an
    // article, and because it has to be able to override the spacing rule —
    // which a number added to a weight cannot do.
    rescueQuietSources(items, sizes, habit, nowMs)

    // The opening anchor, if the scores did not produce one.
    val head = minOf(ArticleWeight.ANCHOR_WITHIN, items.size)
    if (head > 0 && sizes.take(head).none { it == FeedEmphasis.Large }) {
        val best = (0 until head)
            .filter { weights[it] > ArticleWeight.NO_IMAGE }
            .maxByOrNull { weights[it] }
        if (best != null) sizes[best] = FeedEmphasis.Large
    }

    return sizes
}

/**
 * Gives a guaranteed large slot to sources that went quiet and came back.
 *
 * Only the newest article from each rescued source, only sources the reader
 * demonstrably reads, and only [ArticleWeight.RESCUE_MAX] of them — so the
 * exception stays an exception. A source is rescued at most once per feed
 * however many articles it has just published.
 *
 * Mutates `sizes` in place, which is ugly and is the honest shape: it is one
 * more pass over the same array the loop above filled, and returning a copy
 * would suggest the two could be applied independently.
 */
private fun rescueQuietSources(
    items: List<FeedItem>,
    sizes: MutableList<FeedEmphasis>,
    habit: Map<Long, Float>,
    nowMs: Long,
) {
    if (items.isEmpty() || habit.isEmpty()) return

    // The newest article from each source, which is the only candidate: a
    // source that went quiet and published three times is back, and three
    // large cards is a takeover rather than a welcome.
    val newestBySource = HashMap<Long, Int>()
    items.forEachIndexed { index, item ->
        val at = newestBySource[item.feed.id]
        if (at == null || item.timeMillis > items[at].timeMillis) newestBySource[item.feed.id] = index
    }

    newestBySource.entries
        .filter { (feedId, index) ->
            val ageHours = ((nowMs - items[index].timeMillis).coerceAtLeast(0L)) / 3_600_000f
            val wellRead = (habit[feedId] ?: 0f) >= ArticleWeight.RESCUE_HABIT_MIN
            // No picture, no large tile — the shape needs one, and a rescue
            // that produced an empty grey rectangle would help nobody.
            wellRead &&
                    ageHours >= ArticleWeight.QUIET_AFTER_HOURS &&
                    !items[index].imageUrl.isNullOrBlank() &&
                    sizes[index] != FeedEmphasis.Large
        }
        // Most-read source first, so a cap of two spends itself on the sources
        // the reader would most want back rather than on list order.
        .sortedByDescending { (feedId, _) -> habit[feedId] ?: 0f }
        .take(ArticleWeight.RESCUE_MAX)
        .forEach { (_, index) ->
            // One adjacency guard. The rescue overrides the spacing rule by
            // design, but two full-width cards touching reads as a layout
            // fault rather than as emphasis.
            val neighbourLarge = sizes.getOrNull(index - 1) == FeedEmphasis.Large ||
                    sizes.getOrNull(index + 1) == FeedEmphasis.Large
            if (!neighbourLarge) sizes[index] = FeedEmphasis.Large
        }
}

/**
 * The affinity preference, as a map.
 *
 * Stored as `sourceId:score` strings because DataStore has no map type; parsed
 * from the last colon so a source id containing one still splits correctly.
 */
fun parseAffinity(raw: Set<String>): Map<String, Int> = raw.mapNotNull { entry ->
    val at = entry.lastIndexOf(':')
    if (at <= 0) null
    else entry.substring(0, at) to (entry.substring(at + 1).toIntOrNull() ?: 0)
}.toMap()

/**
 * The size each article has already been given, kept beyond any one screen.
 *
 * This was a `remember` inside the sizing composable, which fixed the overlay
 * and left the app broken in a way that looked unrelated. The overlay's feed
 * composes once and stays composed — opening an article launches a separate
 * activity over the top of it. The app's feed is the list pane of a
 * ListDetailPaneScaffold, and on a phone opening an article *replaces* it:
 * come back and the whole feed has been composed afresh, with an empty map,
 * so every size is recalculated — now with the article you just read weighed
 * down for having been read. The article you had just finished reading was the
 * one that shrank, which is why it read as a deliberate behaviour rather than
 * a bug.
 *
 * Held here, outside composition, so both surfaces answer the same way and
 * neither depends on how long its own screen happens to live.
 *
 * Synchronised rather than concurrent: it is touched once per feed reload,
 * from a background thread (see rememberFeedFrame), by two surfaces that are
 * never both on screen. The lock is for the case where they are both
 * composed — the app behind, the panel in front — rather than for contention.
 */
internal object FeedEmphasisMemory {

    private val settled = LinkedHashMap<String, FeedEmphasis>()

    /**
     * Beyond this many remembered sizes, forget the ones not on screen.
     *
     * Generous on purpose. The feed window is a few hundred articles, and the
     * cost of forgetting one is that it changes size the next time it is seen
     * — so this should only ever be reached by somebody who has scrolled
     * through several filtered views in one sitting.
     */
    private const val MAX_REMEMBERED = 2_000

    @Synchronized
    fun settle(articles: List<FeedItem>, fresh: List<FeedEmphasis>): List<FeedEmphasis> {
        articles.forEachIndexed { index, item ->
            settled.getOrPut(item.id) { fresh.getOrNull(index) ?: FeedEmphasis.Medium }
        }
        if (settled.size > MAX_REMEMBERED) {
            val onScreen = articles.mapTo(HashSet()) { it.id }
            settled.keys.retainAll { it in onScreen }
        }
        return articles.map { settled[it.id] ?: FeedEmphasis.Medium }
    }

    /** For tests, which must not inherit sizes from one another. */
    @Synchronized
    fun forget() = settled.clear()
}

/**
 * How much each source is actually read, normalised against the most-read one.
 *
 * Entirely local, and it needs no account: `readAt` has been on every article
 * since the day read state existed, so the history is already here. An account
 * would only carry these counts to a second phone, which is the sync
 * milestone's job rather than a prerequisite for this.
 *
 * Normalised rather than absolute so the term means the same thing to someone
 * who reads four articles a week and someone who reads four hundred.
 */
/**
 * Where the habit window starts, given the last "Forget everything".
 *
 * The plain thirty days, unless the reader reset more recently than that — in
 * which case counting starts at the reset. Shared so the feed's ordering and
 * the Learned screen cannot disagree about which articles are being counted.
 */
fun habitWindowStart(
    resetAt: Long,
    now: Long = System.currentTimeMillis(),
): Long = maxOf(now - ArticleWeight.HABIT_WINDOW_DAYS * 24 * 60 * 60 * 1000, resetAt)

/**
 * How often each subscribed source publishes, in hours between articles.
 *
 * Read once here and handed to both the sizing and the explanation, so the
 * two cannot disagree about how quickly a source moves. Unwindowed on
 * purpose: a slow source has few articles by definition, and asking only
 * about the last month is how you conclude a quarterly journal publishes
 * once ever.
 */
@Composable
fun rememberSourcePace(): Map<Long, Float> {
    val repo: ArticleRepository = koinInject()
    val rows by remember { repo.sourcePace() }.collectAsState(initial = emptyMap())
    return rows
}

/**
 * Whether one source's record says its articles are consistently passed over.
 *
 * A pure function rather than a line inside the composable, because it is the
 * whole judgement: everything else around it is plumbing, and a rule that
 * fades part of somebody's feed should be the part that is easiest to read
 * and to argue with.
 *
 * Both conditions matter. Without the floor on how much has been seen, a
 * source that arrived yesterday is condemned on three articles; without the
 * score, a source the reader stops at constantly is condemned for having a
 * lot of articles.
 */
fun isSkippedSource(row: SourceEngagement): Boolean =
    row.seen >= ArticleWeight.SKIPPED_MIN_SEEN &&
            row.score.toFloat() / row.seen < ArticleWeight.SKIPPED_SCORE_MAX

/**
 * Sources whose articles go past without ever being stopped at.
 *
 * Derived from the same engagement rows the sizing uses, so a source that is
 * faded here is one the Learned screen will show near the bottom — the reader
 * can check the verdict against the numbers it came from, and reset both with
 * the same button.
 *
 * Empty unless the reader has turned fading on. The work is skipped entirely
 * in that case rather than computed and ignored.
 */
@Composable
fun rememberSkippedSources(): Set<Long> {
    val repo: ArticleRepository = koinInject()
    val prefs: FeedPreferences = koinInject()
    val enabled by prefs.dimSkipped.asState()
    val resetAt by prefs.learnedResetAt.get().collectAsState(initial = 0L)
    val since = remember(resetAt) { habitWindowStart(resetAt) }
    val scores by remember(since) { repo.engagementPerSource(since) }
        .collectAsState(initial = emptyMap())
    return remember(scores, enabled) {
        if (!enabled) emptySet()
        else scores.values.filter(::isSkippedSource).mapTo(HashSet()) { it.feedId }
    }
}

@Composable
fun rememberReadingHabits(): Map<Long, Float> {
    val repo: ArticleRepository = koinInject()
    val prefs: FeedPreferences = koinInject()
    // The feed has to agree with the Learned screen about what the counts are,
    // including after a reset — otherwise "Forget everything" empties that
    // screen while the ordering carries on using what it cleared.
    val resetAt by prefs.learnedResetAt.get().collectAsState(initial = 0L)
    val since = remember(resetAt) { habitWindowStart(resetAt) }
    val scores by remember(since) { repo.engagementPerSource(since) }
        .collectAsState(initial = emptyMap())
    return remember(scores) {
        // Normalised by the largest, so this says "how much of your reading
        // goes here" rather than "how much do you read" — which is what makes
        // the term mean the same to somebody with four sources and somebody
        // with a hundred and thirty.
        val most = scores.values.maxOfOrNull { it.score }?.takeIf { it > 0 }
            ?: return@remember emptyMap()
        scores.mapValues { (_, row) -> row.score.toFloat() / most }
    }
}

/**
 * Whether read articles should be faded, for the current setting.
 *
 * A separate reader from the hide rule, which lives in the view model where
 * the list is built: fading is a rendering decision and removing is a
 * filtering one, and putting both in the same place would mean the feed had to
 * be rebuilt to change how it looks.
 */
@Composable
fun rememberDimRead(): Boolean {
    val prefs: FeedPreferences = koinInject()
    val setting by prefs.readVisibility.asState()
    return setting == READ_DIM
}

/**
 * One line of the answer to "why is this the size it is".
 *
 * @param labelId what the signal is, in the reader's words.
 * @param amount how much it added or took away, so the biggest reason is
 *   identifiable rather than merely listed.
 */
data class WeightReason(
    val labelId: Int,
    val amount: Float,
)

/**
 * Why one article got the emphasis it did, in the order that matters most.
 *
 * Derived from the same terms [articleWeight] sums, and deliberately so: an
 * explanation computed separately from the thing it explains drifts the first
 * time either is edited, and then confidently says the wrong thing. Every
 * branch here mirrors one there.
 *
 * The whole point of the weighting is that a reader can disagree with it.
 * That needs the reasons visible at the article, not only the totals on a
 * settings screen — by the time someone is in Settings they have stopped
 * asking about the article that prompted the question.
 */
fun weightReasons(
    item: FeedItem,
    affinity: Map<String, Int>,
    nowMs: Long,
    habit: Map<Long, Float> = emptyMap(),
    clusters: Map<String, StoryCluster> = emptyMap(),
    pace: Map<Long, Float> = emptyMap(),
): List<WeightReason> {
    if (item.imageUrl.isNullOrBlank()) {
        return listOf(WeightReason(R.string.why_no_image, 0f))
    }

    val reasons = mutableListOf<WeightReason>()

    clusters[item.id]?.let { cluster ->
        if (cluster.leadId == item.id) {
            reasons += WeightReason(
                R.string.why_breaking,
                ArticleWeight.breakingBonus(cluster.sources),
            )
        }
    }

    // The freshness line has to name which of the two readings actually won,
    // or the explanation says "published today" beside a four-day-old article
    // and reads as a bug in the app rather than a feature of the weighting.
    val ageHours = ((nowMs - item.timeMillis).coerceAtLeast(0L)) / 3_600_000f
    val absolute = absoluteFreshness(ageHours)
    val relative = relativeFreshness(ageHours, pace[item.feed.id])
    if (relative != null && relative > absolute) {
        reasons += WeightReason(R.string.why_fresh_for_source, relative)
    } else when {
        ageHours < 2f   -> reasons += WeightReason(R.string.why_very_fresh, 1.2f)
        ageHours < 6f   -> reasons += WeightReason(R.string.why_fresh, 0.8f)
        ageHours < 24f  -> reasons += WeightReason(R.string.why_today, 0.4f)
        ageHours >= 72f -> reasons += WeightReason(R.string.why_old, -0.4f)
    }

    val score = (affinity[item.sourceId] ?: 0).coerceIn(-3, 3)
    if (score != 0) reasons += WeightReason(R.string.why_affinity, score * 0.35f)

    val read = (habit[item.feed.id] ?: 0f) * ArticleWeight.HABIT_MAX
    if (read > 0.05f) reasons += WeightReason(R.string.why_read_often, read)

    val words = item.contentTitle.trim().split(Regex("\\s+")).size
    when {
        words < 3   -> reasons += WeightReason(R.string.why_title_short, -0.2f)
        words <= 12 -> reasons += WeightReason(R.string.why_title_good, 0.4f)
        words > 18  -> reasons += WeightReason(R.string.why_title_long, -0.25f)
    }

    reasons += if (item.article.description.isNotBlank())
        WeightReason(R.string.why_has_summary, 0.3f)
    else WeightReason(R.string.why_no_summary, -0.3f)

    if (item.article.readAt != 0L && !item.pinned) {
        reasons += WeightReason(R.string.why_read, -ArticleWeight.READ_PENALTY)
    }
    if (item.bookmarked) reasons += WeightReason(R.string.why_saved, 0.4f)
    if (item.pinned) reasons += WeightReason(R.string.why_pinned, 1.5f)

    return reasons.sortedByDescending { kotlin.math.abs(it.amount) }
}

/**
 * The reasons for one article, with the signals read from preferences.
 *
 * A composable so a card can ask without being handed two maps it has no
 * other use for. It is cheap — the two flows behind it are already collected
 * once per feed by [rememberFeedFrame], and the reasons themselves are a
 * dozen comparisons.
 */
@Composable
fun rememberWeightReasons(
    item: FeedItem,
    clusters: Map<String, StoryCluster> = emptyMap(),
): List<WeightReason> {
    val prefs: FeedPreferences = koinInject()
    val raw by prefs.sourceAffinity.get().collectAsState(initial = emptySet())
    val affinity = remember(raw) { parseAffinity(raw) }
    val habit = rememberReadingHabits()
    val pace = rememberSourcePace()
    return remember(item.id, affinity, habit, clusters, pace) {
        weightReasons(item, affinity, System.currentTimeMillis(), habit, clusters, pace)
    }
}
