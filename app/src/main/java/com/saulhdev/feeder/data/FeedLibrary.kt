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

import android.content.Context
import android.util.Log
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.models.escapingBareAmpersands
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory

/** One bundled collection: a topic like Photography, or a country. */
data class LibraryPack(
    val slug: String,
    val name: String,
    /** "topic" or "country", which is the only grouping the screen needs. */
    val kind: String,
    val count: Int,
)

/** One feed inside a pack. */
data class LibraryFeed(
    val title: String,
    val url: String,
    /** The pack's own grouping, which becomes the category if it is subscribed. */
    val category: String,
)

/**
 * A directory of feeds to browse, bundled with the app.
 *
 * Whisper can already add a feed by address, import OPML, read a browser's
 * bookmarks and notice what the reader's own articles link to. What it could
 * not do was answer "what is there?" for somebody who does not already know —
 * which is the question a reader has on their first day and never again.
 *
 * **Bundled, not fetched, and for the same reason the starter list is.** A
 * directory served over the network would mean the app asking somewhere what
 * feeds exist, and a query like "photography" or "local politics" says quite a
 * lot about a person. The privacy notice lists exactly one developer-initiated
 * third-party call, and a feed catalogue is not worth making it two.
 *
 * The contents come from plenaryapp/awesome-rss-feeds, which is CC0 — public
 * domain, no attribution required, no licence friction with GPL-3.0. Every
 * address was fetched and parsed before it shipped, and the ones that no
 * longer serve a feed were dropped rather than passed on; see
 * docs/FEED_LIBRARY.md for what that removed and what it could not check.
 */
object FeedLibrary {

    private const val DIR = "library"
    private const val TAG = "FeedLibrary"

    /** The packs, in the order the manifest lists them. */
    suspend fun packs(context: Context): List<LibraryPack> = withContext(Dispatchers.IO) {
        val json = runCatching {
            context.assets.open("$DIR/index.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return@withContext emptyList()

        runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                LibraryPack(
                    slug = o.getString("slug"),
                    name = o.getString("name"),
                    kind = o.getString("kind"),
                    count = o.getInt("count"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * The feeds in one pack.
     *
     * Read through the same ampersand fixer the importer uses. The files here
     * were repaired before bundling, so this is belt and braces — but the
     * alternative is two parsers with different tolerances, and the one that
     * gets exercised least is the one that breaks quietly.
     */
    /**
     * A SAX parser that will not go and fetch anything.
     *
     * The default one resolves external entities and honours a DOCTYPE, which
     * is how an XML parse turns into a file read or an outbound request. These
     * packs ship inside the APK, so nothing hostile can be in them and this
     * changes nothing today — it is here because the alternative is a parser
     * built with defaults, one import away from being pointed at a file
     * somebody sent.
     *
     * The companion to that is the name of the ampersand repair: it used to be
     * called `xmlSafe`, which read as though this had already been done.
     *
     * Each feature is set on its own, because a parser that refuses one
     * unknown feature must not lose the others with it.
     *
     * XInclude too. Android's factory throws at the very question, even to
     * turn it off, and it has no XInclude to turn off. Asked outside a
     * runCatching, that throw emptied every pack in the library on a phone
     * from 22 September, while the tests, on the desktop's parser, passed.
     */
    internal fun hardenedSaxParser(factory: SAXParserFactory = SAXParserFactory.newInstance()) =
        factory.apply {
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
            ).forEach { (feature, value) ->
                runCatching { setFeature(feature, value) }
            }
            runCatching { isXIncludeAware = false }
        }.newSAXParser()

    /** The feeds in one pack's OPML, read as [feeds] reads them. */
    internal fun parsePack(
        stream: java.io.InputStream,
        factory: SAXParserFactory = SAXParserFactory.newInstance(),
    ): List<LibraryFeed> {
        val handler = PackHandler()
        hardenedSaxParser(factory).parse(InputSource(escapingBareAmpersands(stream)), handler)
        return handler.feeds
    }

    suspend fun feeds(context: Context, pack: LibraryPack): List<LibraryFeed> =
        withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("$DIR/${pack.slug}.opml").use { parsePack(it) }
            }.getOrElse {
                // Said, at least. This used to be silent, which is how every
                // pack could be empty for five days with nothing in a report.
                Log.w(TAG, "Could not read the ${pack.name} pack", it)
                emptyList()
            }
        }

    /**
     * Every bundled feed, with the pack it came from.
     *
     * Used by the weekly discovery pass, which needs to know which packs the
     * reader's own sources sit in. Reads all eighty-odd files, which is fine
     * once a week in a worker and would not be on a screen.
     */
    suspend fun allFeeds(context: Context): List<Pair<LibraryPack, LibraryFeed>> =
        withContext(Dispatchers.IO) {
            packs(context).flatMap { pack -> feeds(context, pack).map { pack to it } }
        }

    /**
     * Subscribes to the chosen feeds, skipping any already followed.
     *
     * Deliberately the same shape as StarterSources.subscribe: description and
     * icon are left for the first sync to fill in from the feed itself, rather
     * than shipping somebody else's guess at both.
     */
    suspend fun subscribe(
        repository: SourcesRepository,
        chosen: Collection<LibraryFeed>,
    ): Int {
        // One batch, so what is already subscribed is read once rather than
        // once per feed in the pack. See SourcesRepository.insertSources; the
        // duplicate check it does is the one that used to be here, and it also
        // catches two spellings of the same address inside a single pack,
        // which this loop could not.
        val incoming = chosen.mapNotNull { feed ->
            val url = runCatching { sloppyLinkToStrictURL(feed.url) }.getOrNull()
                ?: return@mapNotNull null
            Feed(title = feed.title, url = url, tag = feed.category)
        }
        return repository.insertSources(incoming)
    }
}

/**
 * Pulls feeds out of a pack, remembering which group they sat under.
 *
 * The OPML nests one level: a category outline with no address, holding the
 * feeds. That outer title is worth keeping — it is what the feed's category
 * becomes, so importing a country pack files its feeds under News and Sport
 * rather than all under the country.
 */
private class PackHandler : DefaultHandler() {
    val feeds = mutableListOf<LibraryFeed>()
    private var group = ""

    override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes?) {
        if (localName != "outline" && qName != "outline") return
        val url = a?.getValue("xmlUrl")
        val title = a?.getValue("title") ?: a?.getValue("text") ?: return
        if (url.isNullOrBlank()) {
            group = title
        } else {
            feeds += LibraryFeed(title = title, url = url, category = group)
        }
    }
}
