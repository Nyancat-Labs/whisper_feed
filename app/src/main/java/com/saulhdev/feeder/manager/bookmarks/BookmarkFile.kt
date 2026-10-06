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
package com.saulhdev.feeder.manager.bookmarks

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** A folder of bookmarks, and the addresses in it. */
data class BookmarkFolder(
    /** The folder's name, or its path when it is nested: "News/UK". */
    val name: String,
    val urls: List<String>,
) {
    /** Distinct sites, which is what a scan actually costs. */
    val domains: Int get() = urls.mapNotNull(hostOf).distinct().size
}

/**
 * The bookmarks file every browser exports, read into folders.
 *
 * Folders are the point rather than a refinement. A whole bookmark collection
 * is a junk drawer — shopping, a router's admin page, forty half-read
 * documentation tabs — and most of it publishes no feed, so scanning all of it
 * means reviewing four hundred results to keep thirty. A folder somebody
 * *named* is different: they curated it by hand, over years, because they read
 * those sites. Scanning only chosen folders takes the yield from "a fraction
 * of what is found is wanted" to "nearly all of it".
 *
 * The format is Netscape's, unchanged since 1994 and emitted by Chrome,
 * Firefox, Safari and Edge alike: an `<H3>` naming a folder, followed by a
 * `<DL>` holding its `<A HREF>` entries and any folders nested inside it. It
 * is famously not valid HTML — tags go unclosed — which is why this uses a
 * parser that tolerates that rather than anything stricter.
 */
object BookmarkFile {

    /**
     * Every folder in the file, nested ones included, deepest name first.
     *
     * Bookmarks sitting outside any folder are gathered under [LOOSE]: they
     * are usually the toolbar, they are usually junk, and leaving them
     * unnamed would mean they could not be excluded.
     */
    fun folders(html: String): List<BookmarkFolder> {
        val document = runCatching { Jsoup.parse(html) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<BookmarkFolder>()

        document.select("h3").forEach { heading ->
            val list = heading.nextElementSibling()?.takeIf { it.tagName() == "dl" } ?: return@forEach
            val urls = directLinks(list)
            if (urls.isNotEmpty()) out += BookmarkFolder(pathOf(heading), urls)
        }

        val filed = document.select("h3")
            .mapNotNull { it.nextElementSibling()?.takeIf { s -> s.tagName() == "dl" } }
            .flatMap { directLinks(it) }
            .toSet()
        val loose = document.select("a[href]")
            .mapNotNull { usable(it.attr("href")) }
            .filterNot { it in filed }
            .distinct()
        if (loose.isNotEmpty()) out += BookmarkFolder(LOOSE, loose)

        return out.sortedByDescending { it.urls.size }
    }

    const val LOOSE = "Unfiled"

    /**
     * The links belonging to this folder and not to one inside it.
     *
     * A nested folder is a folder of its own with its own name, and counting
     * its bookmarks in the parent too would mean a parent's count promised
     * work that selecting it does not do.
     */
    private fun directLinks(list: Element): List<String> =
        list.children()
            .filter { it.tagName() == "dt" }
            .flatMap { it.children() }
            .filter { it.tagName() == "a" }
            .mapNotNull { usable(it.attr("href")) }
            .distinct()

    /** "News/UK" for a folder inside a folder, so two named "UK" are told apart. */
    private fun pathOf(heading: Element): String {
        val names = mutableListOf(heading.text().trim())
        var node: Element? = heading.parent()
        while (node != null) {
            if (node.tagName() == "dl") {
                val parentHeading = node.previousElementSibling()
                    ?.takeIf { it.tagName() == "h3" }
                if (parentHeading != null) names += parentHeading.text().trim()
            }
            node = node.parent()
        }
        return names.filter { it.isNotEmpty() }.reversed().joinToString("/")
    }

    /**
     * Addresses worth scanning at all.
     *
     * `javascript:` bookmarklets, `place:` queries and `chrome://` pages are
     * every export's furniture and none of them are websites.
     */
    private fun usable(href: String): String? {
        val trimmed = href.trim()
        if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
            return null
        }
        return trimmed
    }
}

private val hostOf: (String) -> String? = { url ->
    runCatching { java.net.URL(url).host?.lowercase()?.removePrefix("www.") }.getOrNull()
}
