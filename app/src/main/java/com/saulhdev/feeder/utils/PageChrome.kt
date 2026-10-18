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

import org.jsoup.Jsoup
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
    imagesFromNoscript(document)
    joinSplitArticle(document)
    figuresFromCredits(document)

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

/**
 * Brings out pictures a page offers only inside `<noscript>`.
 *
 * Inverse draws every picture in its articles with a script and puts the
 * real `<picture>` in a `<noscript>` beside it for browsers without one.
 * Readability removes `noscript` blocks, so those articles came out with
 * their captions and none of their pictures. Mozilla's Readability unwraps
 * such images first; the port used here does not. A `noscript` holding a
 * picture is replaced by its contents, unless its parent already has a
 * working picture of its own, which is then the one to keep. A blank
 * stand-in beside it goes.
 */
internal fun imagesFromNoscript(document: Document) {
    document.body().select("noscript").forEach { noscript ->
        val inner = Jsoup.parseBodyFragment(noscript.html(), document.location()).body()
        if (inner.select("img").none { it.attr("src").isNotBlank() || it.attr("srcset").isNotBlank() }) return@forEach
        val parent = noscript.parent() ?: return@forEach
        val working = parent.select("img").any { img ->
            img.parents().none { it.tagName() == "noscript" } && img.attr("src").let { it.startsWith("http") || it.startsWith("/") }
        }
        if (working) return@forEach
        // The stand-in the script would have filled: blank, with nowhere to get a picture from.
        parent.select("img")
            .filter { img ->
                img.attr("src").let { it.isBlank() || it.startsWith("data:") } &&
                    img.attributes().none { it.key.startsWith("data-") && it.key.endsWith("src") }
            }
            .forEach { it.remove() }
        inner.childNodes().toList().forEach { noscript.before(it) }
        noscript.remove()
    }
}

/**
 * Puts an article the page split around its adverts back into one piece.
 *
 * Ars Technica sets each article in several `div.post-content` blocks, two
 * to eight of them, each in its own wrapper with an advert between. Readability
 * takes the largest single block for the article, so every Ars article lost
 * the rest: one opened on "…the company said" with the eight paragraphs
 * saying which company gone. Blocks carrying the same article-body class,
 * none inside another, are joined into the first, in page order.
 */
internal fun joinSplitArticle(document: Document) {
    document.body().select("[class]")
        .filter { ARTICLE_BODY_CLASS.containsMatchIn(it.className()) }
        .groupBy { it.className() }
        .values
        .forEach { parts ->
            val outermost = parts.filter { part -> part.parents().none { it in parts } }
            if (outermost.size < 2) return@forEach
            val first = outermost.first()
            outermost.drop(1).forEach { part ->
                part.childNodes().toList().forEach { first.appendChild(it) }
                part.remove()
            }
        }
}

private val ARTICLE_BODY_CLASS = Regex(
    """(?:^|\s)(?:post-content|post-body|article-body|article-content|article__body|entry-content|story-body)(?:\s|$)""",
)

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

/**
 * A picture and its credit side by side in a plain wrapper, made a figure.
 *
 * Engadget sets its pictures as `div > picture + span.gallery-image-credit`,
 * no `<figure>`, and extraction then drops the class that said which line
 * was the credit. When the reader moved the opening picture above the
 * headline, "Joshua Westbrook/Getty Images" stayed behind as a paragraph of
 * its own (18 October 2026). Said with the elements made for it - figure and
 * figcaption - the pairing survives extraction, a lifted picture takes its
 * credit along, and a picture further down keeps its credit under it.
 *
 * Only a wrapper holding one picture and nothing else but the caption or
 * credit, so a container of article text is never renamed.
 */
internal fun figuresFromCredits(document: Document) {
    document.body().select("[class*=credit], [class*=caption]")
        .filter { it.tagName() != "figcaption" && it.closest("figure") == null }
        .forEach { credit ->
            val wrapper = credit.parent() ?: return@forEach
            if (wrapper == document.body() || wrapper.tagName() == "figure") return@forEach
            if (wrapper.select("img").size != 1) return@forEach
            val others = wrapper.children().filter { it != credit && it.select("img").isEmpty() }
            if (others.any { it.text().isNotBlank() }) return@forEach
            if (wrapper.ownText().isNotBlank()) return@forEach
            wrapper.tagName("figure")
            credit.tagName("figcaption")
        }
}

