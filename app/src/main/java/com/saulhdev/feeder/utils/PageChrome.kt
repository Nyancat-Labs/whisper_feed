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
package com.saulhdev.feeder.utils

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Takes the page's own furniture out before the article is extracted.
 *
 * Readability keeps more than the article, and what it keeps most reliably is
 * the byline block. Run over six real articles, the leftovers were:
 *
 * - How-To Geek: the author's 90px avatar, then six paragraphs of his biography
 *   ("Ever since he got his first smartphone, the legendary Nokia 6600...")
 *   before the article's first sentence;
 * - The Verge: the author card - photograph, name, "Posts from this author
 *   will be added to your daily email digest" - and five topic-follow widgets
 *   saying the same about Gaming, Microsoft, News, Tech and Xbox.
 *
 * Every one of those duplicates something the reader already shows - the
 * author and the date are in its own header - or is a control that does
 * nothing here. It has to happen *before* extraction because extraction is
 * what forgets which element was which: the extracted HTML keeps no class
 * names, and a byline paragraph is a paragraph like any other once it has been
 * through. In the page they are labelled plainly - `w-author`,
 * `article-header-author-img`, `duet--article--article-byline`.
 *
 * Two rules, both general and both about what an element says it is:
 *
 * - **`<aside>`** goes. It is the element for content tangential to the page:
 *   The Verge's follow popovers are asides, and so are most sites' pull
 *   quotes, which repeat a sentence of the article in larger type - a
 *   duplicate by design. Mozilla's own Readability drops them for the same
 *   reason; the port this app uses does not.
 * - **Anything whose class or id names it an author or byline block** goes,
 *   as long as it is not most of the page. The guard matters: a theme that
 *   tags the whole article `author-jane` must lose its label, not its
 *   article.
 * - **Anything marked `data-nosnippet`** goes, under the same guard. It is
 *   Google's attribute for "this text is not the article; do not quote it in
 *   search results", and publishers put it on precisely this furniture.
 *   How-To Geek's six-paragraph author biography carries no author label at
 *   all - it sits in `div.with-excerpt` - but it is marked `data-nosnippet`,
 *   and so is its byline. A few sites mark whole paywalled sections the same
 *   way, which is what the size guard is for.
 *
 * Only new fetches benefit: an article whose full text is already cached was
 * extracted before this existed, and stays as it was until it is fetched
 * again.
 */
fun stripPageChrome(document: Document) {
    document.select("aside").remove()

    val body = document.body()
    val pageText = body.text().length
    body.select("[class], [id], [data-nosnippet]")
        .filter {
            it.tagName() !in NEVER_STRIPPED &&
                (it.hasAttr("data-nosnippet") || namesItselfByline(it))
        }
        // Outermost first, so a removed parent is not measured again through
        // its children.
        .forEach { element ->
            if (element.parent() == null) return@forEach
            if (element.ownerDocument() == null) return@forEach
            val share = if (pageText == 0) 0.0 else element.text().length.toDouble() / pageText
            if (share <= MAX_BYLINE_SHARE) element.remove()
        }

    // Words inside a sentence are part of it, whatever their wrapper is called.
    // Readability drops elements named like clutter ("popup", "share",
    // "sidebar"...), and Investing.com wraps every ticker in
    // span.aqPopupWrapper: "Goosehead Insurance Inc. (NASDAQ:GSHD)" came out
    // as "Goosehead Insurance Inc. ()". Inline elements inside running text
    // lose their names, after the byline rule above has used them.
    body.select(INLINE_TAGS)
        .filter { inline -> inline.parents().any { it.tagName() in TEXT_BLOCKS } }
        .forEach { it.removeAttr("class").removeAttr("id") }
}

private const val INLINE_TAGS =
    "span, a, em, strong, b, i, u, s, abbr, small, sup, sub, mark, cite, q, time, code, font"
private val TEXT_BLOCKS = setOf("p", "li", "blockquote", "dd", "figcaption")

/**
 * Whether an element's class or id says "author" or "byline" as a word.
 *
 * As a word, split on anything that is not a letter or a digit, so
 * `w-author`, `article-header-author-img` and `author_byline_lead` all
 * qualify and `coauthored` or `authority` do not - Android Authority's
 * elements should keep its name in them without losing their contents.
 */
internal fun namesItselfByline(element: Element): Boolean {
    val label = element.className() + " " + element.id()
    return label.lowercase().split(NON_WORD).any { it in BYLINE_WORDS }
}

private val NON_WORD = Regex("[^a-z0-9]+")
private val BYLINE_WORDS = setOf("author", "authors", "byline")
private val NEVER_STRIPPED = setOf("html", "head", "body", "main", "article")

/**
 * The most of a page's text a byline block may hold and still be removed.
 * How-To Geek's, biography included, is a few percent of its article.
 */
private const val MAX_BYLINE_SHARE = 0.25
