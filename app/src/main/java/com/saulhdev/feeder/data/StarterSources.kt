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
package com.saulhdev.feeder.data

import androidx.annotation.StringRes
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import java.util.Locale

/**
 * A few feeds to start with, so the first screen is not empty.
 *
 * Offered, never applied on its own: the reader sees exactly which ones they
 * are getting and can take any of them off before agreeing. That matters more
 * here than it would in most apps, because the screen this appears on says
 * "no algorithm decides what you see" — subscribing somebody silently on the
 * same screen would make that a lie.
 *
 * Nothing marks these as built in. Once subscribed they are ordinary sources,
 * indistinguishable from ones the reader added, and removed exactly the same
 * way. A "starter" flag would only exist to stop somebody deleting a feed they
 * did not choose, which is the opposite of the point.
 *
 * **Bundled rather than fetched.** A list downloaded on first launch would
 * mean the app phoning home before the reader has done anything, which is not
 * a promise worth breaking to save an app update.
 *
 * Every address here was checked against the live endpoint before it shipped.
 */
data class StarterSource(
    val title: String,
    val url: String,
    @param:StringRes val categoryId: Int,
    /**
     * The region this belongs to, or null for the ones everybody gets.
     *
     * A reader in Berlin should not be handed an Australian national
     * broadcaster by default. It is still listed and still one tap away —
     * hiding it entirely would be worse — but it starts unticked.
     */
    val region: String? = null,
)

object StarterSources {

    val ALL: List<StarterSource> = listOf(
        // Four world sources rather than two, deliberately. Breaking-news
        // clustering needs three of them carrying the same story before it
        // groups anything, so a shorter list would leave the feature switched
        // on and never firing.
        StarterSource(
            "BBC News",
            "https://feeds.bbci.co.uk/news/world/rss.xml",
            R.string.starter_category_world,
        ),
        StarterSource(
            "The Guardian",
            "https://www.theguardian.com/world/rss",
            R.string.starter_category_world,
        ),
        StarterSource(
            "Al Jazeera",
            "https://www.aljazeera.com/xml/rss/all.xml",
            R.string.starter_category_world,
        ),
        StarterSource(
            "NPR News",
            "https://feeds.npr.org/1001/rss.xml",
            R.string.starter_category_world,
        ),
        StarterSource(
            "Ars Technica",
            "https://feeds.arstechnica.com/arstechnica/index",
            R.string.starter_category_technology,
        ),
        StarterSource(
            "The Verge",
            "https://www.theverge.com/rss/index.xml",
            R.string.starter_category_technology,
        ),
        StarterSource(
            "Phys.org",
            "https://phys.org/rss-feed/",
            R.string.starter_category_science,
        ),
        StarterSource(
            "ScienceDaily",
            "https://www.sciencedaily.com/rss/all.xml",
            R.string.starter_category_science,
        ),
        StarterSource(
            "ABC News",
            "https://www.abc.net.au/news/feed/1948/rss.xml",
            R.string.starter_category_australia,
            region = "AU",
        ),
    )

    /**
     * Which ones start ticked.
     *
     * Everything without a region, plus the regional feed for wherever the
     * phone says it is.
     */
    fun defaultSelection(locale: Locale = Locale.getDefault()): Set<String> =
        ALL.filter { it.region == null || it.region == locale.country }
            .map { it.url }
            .toSet()

    /**
     * Subscribes to the chosen ones, and reports how many were new.
     *
     * Each address is re-checked against what is already subscribed rather
     * than trusted from the screen. `Feeds.url` is uniquely indexed with an
     * insert strategy of REPLACE, so adding a feed somebody already has would
     * not sit beside the original — it would replace the row and take that
     * feed's articles with it.
     *
     * `description` and `feedImage` are left empty on purpose: they are the
     * source's own words and the source's own mark, and the first sync fills
     * them in from the feed. Writing a guess here would have every new
     * subscription claiming an icon it does not have.
     */
    suspend fun subscribe(
        repository: SourcesRepository,
        chosen: Collection<StarterSource>,
        categoryName: (Int) -> String,
    ): Int {
        // One batch; see SourcesRepository.insertSources. The starter list is
        // the first thing a new reader does, on a database that is empty, so
        // the scan it saves is small — but this and the library pack are the
        // same operation and were written twice, which is how they came to
        // differ in what they counted.
        return repository.insertSources(
            chosen.map { starter ->
                Feed(
                    title = starter.title,
                    url = sloppyLinkToStrictURL(starter.url),
                    tag = categoryName(starter.categoryId),
                )
            }
        )
    }
}
