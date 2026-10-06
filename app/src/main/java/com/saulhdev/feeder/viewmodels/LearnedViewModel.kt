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
package com.saulhdev.feeder.viewmodels

import com.saulhdev.feeder.utils.usableImageUrl
import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.ui.overlay.ArticleWeight
import com.saulhdev.feeder.ui.overlay.habitWindowStart
import com.saulhdev.feeder.ui.overlay.parseAffinity
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/**
 * One source, and everything Whisper has concluded about it.
 *
 * @param nudge the weight this source's articles get from the two learned
 *   signals together — the number the feed actually uses, rather than the raw
 *   counts it was derived from. Showing the inputs without the output would
 *   explain the arithmetic and not the effect.
 */
data class LearnedSource(
    val id: Long,
    val title: String,
    val iconUrl: String?,
    val affinity: Int,
    /** Articles opened in full — the one signal that is not inferred. */
    val opened: Int,
    /** Articles that reached the screen, opened or not. */
    val seen: Int,
    val nudge: Float,
    val hidden: Boolean,
)

/**
 * What the reader has taught the app, laid out so it can be disagreed with.
 *
 * `docs/REFERENCES.md` §4 found no open-source prior art for transparent,
 * resettable preference learning in a feed reader. This is the transparent
 * half: every number the weighting reads from the reader's own behaviour is
 * on one screen, attributed to the source it came from, next to the button
 * that throws it away.
 *
 * Nothing here is inferred or modelled. Affinity is the count of More and Less
 * presses. The other two are what happened to this source's articles: how many
 * reached the screen at all, and how many were opened.
 *
 * Both are shown rather than the weighted total, and deliberately. The
 * ordering works from a score that bands those encounters by how much time
 * each one got, and "score 84" is not something anybody can check against
 * their own memory. "Nine opened, forty seen" is. A number nobody can audit is
 * a number nobody can correct, which would defeat the whole screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LearnedViewModel(
    private val articleRepo: ArticleRepository,
    private val sourcesRepo: SourcesRepository,
    private val prefs: FeedPreferences,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    val sources: StateFlow<List<LearnedSource>> = combine(
        sourcesRepo.getAllSourcesFlow(),
        prefs.sourceAffinity.get(),
        // Re-reads on every reset, so the screen empties as soon as the button
        // is pressed rather than at the next time the page is opened.
        prefs.learnedResetAt.get().flatMapLatest { resetAt ->
            articleRepo.engagementPerSource(habitWindowStart(resetAt))
        },
    ) { feeds, affinityRaw, engagement ->
        val affinity = parseAffinity(affinityRaw)
        val most = engagement.values.maxOfOrNull { it.score }?.takeIf { it > 0 }
        feeds.map { feed ->
            val id = feed.id
            val score = affinity[id.toString()] ?: 0
            val row = engagement[id]
            val habit = if (most == null || row == null) 0f else row.score.toFloat() / most
            LearnedSource(
                id = id,
                title = feed.title,
                iconUrl = usableImageUrl(feed.feedImage.toString())
                    ?.takeIf { it != feed.url.toString() },
                affinity = score,
                opened = row?.opened ?: 0,
                seen = row?.seen ?: 0,
                // The same arithmetic the weighting does, not an approximation
                // of it: a screen that explains a different sum than the one
                // being run is worse than no screen.
                nudge = score.coerceIn(-3, 3) * 0.35f + habit * ArticleWeight.HABIT_MAX,
                // One state now: a hidden source is a source that is off,
                // so this reads the feed's own column rather than a set that
                // said the same thing in a second place.
                hidden = !feed.isEnabled,
            )
        }.sortedWith(
            compareByDescending<LearnedSource> { it.nudge }.thenBy { it.title.lowercase() }
        )
    }.stateIn(ioScope, SharingStarted.Eagerly, emptyList())

    /** Puts one source back in the feed, without touching anything else. */
    fun unhide(id: Long) {
        ioScope.launch { sourcesRepo.setEnabled(listOf(id), enabled = true) }
    }

    /** Forgets one source's scores, leaving every other source alone. */
    fun forget(id: Long) {
        ioScope.launch {
            val remaining = prefs.sourceAffinity.getValue()
                .filterNot { it.substringBeforeLast(':') == id.toString() }
                .toSet()
            prefs.sourceAffinity.setValue(remaining)
        }
    }

    /**
     * Throws away everything learned, in one action.
     *
     * Read state is deliberately not cleared. It is a record of what happened
     * rather than an opinion about it — clearing it would mark a year of read
     * articles unread, which is not what "forget what you have learned" means
     * to anyone who presses it. What is cleared is the *use* made of it: the
     * habit window restarts from now, so the counts this screen shows go to
     * zero and the weight they carried goes with them.
     */
    fun resetAll() {
        ioScope.launch {
            prefs.sourceAffinity.setValue(emptySet())
            // Every source back on, which is what "shows any hidden sources
            // again" has always promised — it just used to mean a separate
            // set rather than the switch.
            sourcesRepo.setEnabled(
                sourcesRepo.getAllSources().map { it.id },
                enabled = true,
            )
            // Without this the button cleared the smaller half of what the
            // screen shows and left the larger one: every source kept its read
            // count and most of its weight, so "Forget everything" visibly did
            // not. See FeedPreferences.learnedResetAt for why the counts are
            // restarted rather than the articles unmarked.
            prefs.learnedResetAt.setValue(System.currentTimeMillis())
        }
    }
}
