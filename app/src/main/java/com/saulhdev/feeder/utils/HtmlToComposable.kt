/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.saulhdev.feeder.R
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.ui.components.Table
import com.saulhdev.feeder.ui.components.TableCell
import com.saulhdev.feeder.ui.components.TextComposer
import com.saulhdev.feeder.ui.components.WithBidiDeterminedLayoutDirection
import com.saulhdev.feeder.ui.components.withAnnotation
import com.saulhdev.feeder.ui.components.withComposableStyle
import com.saulhdev.feeder.ui.components.withParagraph
import com.saulhdev.feeder.ui.components.withStyle
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Play
import com.saulhdev.feeder.ui.theme.blockQuoteStyle
import com.saulhdev.feeder.ui.theme.codeBlockBackground
import com.saulhdev.feeder.ui.theme.codeBlockStyle
import com.saulhdev.feeder.ui.theme.codeInlineStyle
import com.saulhdev.feeder.ui.theme.linkTextStyle
import com.saulhdev.feeder.ui.theme.LocalDimens
import org.jsoup.Jsoup
import org.jsoup.internal.StringUtil
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How a paragraph of an article is set.
 *
 * Ragged right, unhyphenated. This was justified with hyphenation switched
 * on, on the reasoning that a narrow justified column opens rivers unless the
 * breaks can fall mid-word — which is true, and is an argument against
 * justifying a column this narrow rather than for hyphenating it.
 *
 * Justification needs a measure wide enough that the spaces have somewhere to
 * go. A phone gives roughly forty characters, so every line was stretched to
 * fit and no two lines were stretched the same amount; the word gaps changed
 * size down the page. Android's automatic hyphenation then broke words
 * wherever the line needed it — "automot-ive", "Activ-ision", "Actu-ally" —
 * and a reader tracking a sentence had to reassemble words as well as follow
 * shifting gaps. Reported as disorienting, which is exactly what it is.
 *
 * Ragged right gives every space the same width and every word one piece. The
 * right-hand edge is uneven, and that is the trade: an edge nobody reads
 * against gaps and breaks inside the words they do. It is what every reading
 * app on a phone does, for this reason.
 *
 * [LineBreak.Paragraph] stays. It is the slower, whole-paragraph break
 * strategy, and it is what keeps the ragged edge shallow rather than saw
 * toothed — it has nothing to do with justification and was never the problem.
 */
@Composable
private fun bodyStyle() = MaterialTheme.typography.bodyLarge.merge(
    TextStyle(
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Start,
        hyphens = Hyphens.None,
        lineBreak = LineBreak.Paragraph,
    )
)

fun LazyListScope.htmlFormattedText(
    inputStream: InputStream,
    baseUrl: String,
    @DrawableRes imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    /**
     * The headline the screen has already drawn, if any.
     *
     * Given so a body that opens by repeating it can have that copy taken
     * out. See [stripRepeatedTitle].
     */
    articleTitle: String? = null,
    /**
     * The article's own picture, the one its card in the feed shows.
     *
     * Given so a body that arrived without it can have it put back. See
     * [ensureLeadImage].
     */
    leadImageUrl: String? = null,
) {
    Jsoup.parse(inputStream, null, baseUrl)
        .body()
        .let { body ->
            stripRepeatedTitle(body, articleTitle)
            ensureLeadImage(body, leadImageUrl)
            dropRepeatedImages(body)
            formatBody(
                element = body,
                imagePlaceholder = imagePlaceholder,
                onLinkClick = onLinkClick,
                baseUrl = baseUrl,
            )
        }
}

/**
 * Takes out a body's opening heading when it only repeats the article's title.
 *
 * The reader draws the headline itself, from the feed, above the source and
 * the byline. Plenty of publishers also open the article body with the same
 * words in an `h1`, and Readability keeps it — so the story arrived with its
 * title twice, once in the reader's own type and again in the body's, set
 * differently and reading like a mistake because it is one.
 *
 * Only the *first* text-bearing block is considered, and only when it is a
 * heading. A later heading with the same words is a real section of the
 * article — a list piece whose first entry repeats the title, say — and
 * removing that would take away content rather than a duplicate.
 */
internal fun stripRepeatedTitle(body: Element, articleTitle: String?) {
    val wanted = normalizedHeading(articleTitle.orEmpty())
    if (wanted.isEmpty()) return
    val first = body.select("h1, h2, h3, h4, p, li, blockquote, pre, td")
        .firstOrNull { it.text().isNotBlank() }
        ?: return
    if (first.tagName() !in HEADING_TAGS) return
    val heading = normalizedHeading(first.text())
    if (heading != wanted && heading != withoutSiteName(wanted) && withoutSiteName(heading) != wanted) return
    first.remove()
}

/**
 * Puts the article's own picture back when the body arrived without it.
 *
 * The Verge's articles opened with no picture at all while the same story's
 * card in the feed carried one. Fetching the page and reading it settles why:
 * the lead image is a Next.js fill image, an `<img>` with no `src` and no
 * `srcset`, whose real address is supplied by JavaScript after the page
 * loads. There is no `<picture>`, no `data-src`, no `<noscript>` copy — in
 * the HTML a fetcher receives, the address appears only in `og:image`. So it
 * is not there to be found by looking harder at the body, and every reader
 * that works from the page's markup loses it the same way.
 *
 * What makes this fixable is that the app already has the picture: it came
 * with the feed entry, and the card has been showing it all along. Nothing
 * needs fetching or guessing — the image the reader was looking at a moment
 * ago is simply drawn again above the article, where the publisher put it.
 *
 * ## Only when it is genuinely missing
 *
 * Most publishers do put the lead image in the body, so this has to recognise
 * it there or every article gains a duplicate. Matched on host and path with
 * the query string dropped, because the same asset is routinely served at
 * several sizes: `…/STK048.png?w=1200` in the page and `…/STK048.png?w=560`
 * in the feed is one picture, and comparing whole addresses would call it two.
 */
internal fun ensureLeadImage(body: Element, leadImageUrl: String?) {
    val lead = leadImageUrl?.trim().orEmpty()
    if (lead.isEmpty()) return
    val wanted = imageIdentity(lead)
    if (wanted.isEmpty()) return

    val images = body.select("img").filter { srcOf(it).isNotBlank() }
    // The same address, so the same request and the same cache entry. Nothing
    // to do; this is most publishers.
    if (images.any { imageIdentity(srcOf(it)) == wanted }) return

    val first = images.firstOrNull()
    if (first != null && sameAsset(imageIdentity(srcOf(first)), wanted)) {
        // The same photograph at a different address, which is the ordinary
        // case and was costing a second download of a picture already on the
        // phone. One Gear Patrol item offers the feed
        // `...16x9-Lead-1.webp` and the body `...16x9-Lead-1.jpg?w=1920`: one
        // asset, two addresses, two cache entries, and two separate chances to
        // fail - which is how an article came to show a placeholder where its
        // own card, a moment earlier, had shown the picture.
        first.attr("src", lead)
        // And the srcset with it, or the swap does nothing. The reader picks
        // from srcset first and only falls back to src, so a body image that
        // carries both went on requesting its own copy while this line
        // appeared to have changed it - which is how the Gear Patrol picture
        // stayed a placeholder after the first version of this fix.
        first.removeAttr("srcset")
        first.removeAttr("sizes")
        return
    }

    // Put back only when the body has no opening picture of its own. BGR,
    // Engadget and SlashGear offer the feed `l-intro-123.jpg` and open the
    // body with `intro-123.jpg` - two files, one photograph - and adding the
    // feed's copy above theirs was the reader's own duplicate, introduced by
    // the first version of this function. An opening picture, whatever its
    // name, means the publisher put the lead where it belongs already.
    if (opensWithImage(body)) return
    body.prependChild(Element("img").attr("src", lead))
}

/**
 * Whether a picture comes before the article really starts.
 *
 * "Really starts" is the second paragraph of any substance, not the first. A
 * dek sits between headline and photograph on a great many sites - Engadget's
 * "It will offer several privacy-focused options for users..." comes before
 * its lead image - and counting it as the start made this answer no for
 * exactly the articles that had a lead image, which put the feed's copy above
 * theirs. Short lines such as a kicker or a lone "TL;DR" do not count at all.
 *
 * An image that says it is small - both dimensions under [SMALL_IMAGE_PX], an
 * avatar or an icon - is not a lead image however early it comes; How-To
 * Geek's byline avatar is 90 by 90 and sits right at the top.
 */
internal fun opensWithImage(body: Element): Boolean {
    var paragraphs = 0
    for (e in body.select("img, p, li, blockquote, h2, h3")) {
        if (e.tagName() == "img") {
            if (srcOf(e).isBlank() && e.attr("srcset").isBlank()) continue
            if (isDeclaredSmall(e)) continue
            return true
        }
        if (e.text().trim().length >= OPENING_PARAGRAPH_CHARS) {
            paragraphs++
            if (paragraphs >= OPENING_PARAGRAPHS_ALLOWED + 1) return false
        }
    }
    return false
}

/** Paragraphs allowed above the lead image before it stops being the lead: the dek. */
private const val OPENING_PARAGRAPHS_ALLOWED = 1

private fun isDeclaredSmall(img: Element): Boolean {
    val w = img.attr("width").toIntOrNull() ?: return false
    val h = img.attr("height").toIntOrNull() ?: return false
    return w in 1 until SMALL_IMAGE_PX && h in 1 until SMALL_IMAGE_PX
}

private const val OPENING_PARAGRAPH_CHARS = 40
private const val SMALL_IMAGE_PX = 150

/**
 * Keeps the first of several images that are the same picture.
 *
 * The Verge's byline card carries its author's photograph twice - once as a
 * fill image and once as a small avatar, one asset at two sizes - and
 * Readability keeps the card, so the reader showed the same face twice in a
 * row. Compared the way [ensureLeadImage] compares, host and path with the
 * sizing query dropped, so two sizes of one file count as one.
 *
 * Every later copy goes, not only an adjacent one: a photograph repeated
 * further down is still a photograph already shown.
 */
internal fun dropRepeatedImages(body: Element) {
    val seen = HashSet<String>()
    body.select("img").forEach { img ->
        // The srcset's first candidate when there is no src: The Verge's
        // avatar has only a srcset, and an image this cannot name cannot be
        // recognised as a repeat either.
        val src = srcOf(img).ifBlank {
            img.attr("srcset").substringBefore(',').trim().substringBefore(' ')
        }
        if (src.isBlank()) return@forEach
        val identity = imageIdentity(src)
        if (identity.isEmpty()) return@forEach
        if (!seen.add(identity)) img.remove()
    }
}

private fun srcOf(img: Element): String =
    img.attr("abs:src").ifBlank { img.attr("src") }

/**
 * Whether two image addresses are the same photograph in different clothes.
 *
 * Identical but for the file extension. A CDN offering one picture as both
 * webp and jpeg is naming a single asset twice, and treating those as two
 * pictures is what makes the reader fetch what the feed already holds.
 *
 * Deliberately no looser than that. Sizes and crops live in the query string,
 * which [imageIdentity] has already dropped; anything differing beyond the
 * extension is a different file and may well be a different picture.
 */
private fun sameAsset(a: String, b: String): Boolean =
    a.isNotEmpty() && b.isNotEmpty() && withoutExtension(a) == withoutExtension(b)

private val IMAGE_EXTENSION = Regex("\\.[A-Za-z0-9]{1,5}$")

private fun withoutExtension(identity: String): String =
    identity.replace(IMAGE_EXTENSION, "")

/**
 * An image address reduced to the thing that identifies the picture.
 *
 * Host and path, lowercased, with the query string and the fragment dropped —
 * those carry the size and the crop, which is exactly what differs between
 * the copy in the feed and the copy in the page.
 */
internal fun imageIdentity(url: String): String =
    url.substringBefore('#')
        .substringBefore('?')
        .substringAfter("://")
        .trimEnd('/')
        .lowercase()

private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4")

/**
 * A title with a trailing site name taken off: "... - BGR", "... | SlashGear".
 *
 * Page titles carry one far more often than feed titles, and the same
 * headline with and without it is still the same headline. Only the last
 * segment goes, and only after a spaced separator, so a hyphenated word or a
 * title that is itself a list of phrases keeps everything but its tail.
 */
private fun withoutSiteName(title: String): String {
    val cut = maxOf(title.lastIndexOf(" - "), title.lastIndexOf(" | "))
    return if (cut > 0) title.substring(0, cut).trim() else title
}

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Two headings compared the way a reader would compare them.
 *
 * The feed's title and the page's own rarely match byte for byte: one has
 * curly quotes where the other has straight ones, an em dash against a hyphen,
 * a non-breaking space, a trailing full stop. None of those is a different
 * headline, and a comparison that says they are would leave the duplicate on
 * screen for most of the publishers this exists for.
 */
internal fun normalizedHeading(text: String): String =
    text.replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace('\u201C', '"')
        .replace('\u201D', '"')
        .replace('\u2013', '-')
        .replace('\u2014', '-')
        .replace('\u00A0', ' ')
        .lowercase()
        .replace(WHITESPACE_RUN, " ")
        .trim()
        .trim('.', ',', ':', ';', '-', '|', '\u2026')
        .trim()

private fun LazyListScope.formatBody(
    element: Element,
    @DrawableRes imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    baseUrl: String,
) {
    val composer = TextComposer { paragraphBuilder ->
        item {
            val dimens = LocalDimens.current
            val paragraph = paragraphBuilder.toComposableAnnotatedString()

            WithBidiDeterminedLayoutDirection(paragraph.text) {
                // ClickableText prevents taps from deselecting selected text
                // So use regular Text if possible
                if (
                    paragraph.getStringAnnotations("URL", 0, paragraph.length)
                        .isNotEmpty()
                ) {
                    ClickableText(
                        text = paragraph,
                        style = bodyStyle(),
                        modifier = Modifier.width(dimens.maxContentWidth)
                    ) { offset ->
                        paragraph.getStringAnnotations("URL", offset, offset)
                            .firstOrNull()
                            ?.let {
                                onLinkClick(it.item)
                            }
                    }
                } else {
                    Text(
                        text = paragraph,
                        style = bodyStyle(),
                        modifier = Modifier.width(dimens.maxContentWidth)
                    )
                }
            }
        }
    }

    composer.appendTextChildren(
        element.childNodes(),
        lazyListScope = this,
        imagePlaceholder = imagePlaceholder,
        onLinkClick = onLinkClick,
        baseUrl = baseUrl,
    )

    composer.terminateCurrentText()
}

private fun LazyListScope.formatCodeBlock(
    element: Element,
    @DrawableRes imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    baseUrl: String,
) {
    val composer = TextComposer { paragraphBuilder ->
        item {
            val dimens = LocalDimens.current
            val scrollState = rememberScrollState()
            Surface(
                color = codeBlockBackground(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .width(dimens.maxContentWidth)
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .horizontalScroll(state = scrollState)
                        .padding(8.dp)
                ) {
                    Text(
                        text = paragraphBuilder.toComposableAnnotatedString(),
                        style = codeBlockStyle(),
                        softWrap = false
                    )
                }
            }
        }
    }

    composer.appendTextChildren(
        element.childNodes(),
        preFormatted = true,
        lazyListScope = this,
        imagePlaceholder = imagePlaceholder,
        onLinkClick = onLinkClick,
        baseUrl = baseUrl,
    )

    composer.terminateCurrentText()
}

private fun TextComposer.appendTextChildren(
    nodes: List<Node>,
    preFormatted: Boolean = false,
    lazyListScope: LazyListScope,
    @DrawableRes imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    baseUrl: String,
) {
    var node = nodes.firstOrNull()
    while (node != null) {
        when (node) {
            is TextNode -> {
                if (preFormatted) {
                    append(node.wholeText)
                } else {
                    node.appendCorrectlyNormalizedWhiteSpace(
                        this,
                        stripLeading = endsWithWhitespace
                    )
                }
            }

            is Element  -> {
                val element = node
                when (element.tagName().lowercase()) {
                    "div"                    -> {
                        // Handle div based on its role/class
                        when {
                            element.hasClass("readability-styled") -> {
                                // Handle readability-styled divs similar to p tags
                                appendTextChildren(
                                    element.childNodes(),
                                    lazyListScope = lazyListScope,
                                    imagePlaceholder = imagePlaceholder,
                                    onLinkClick = onLinkClick,
                                    baseUrl = baseUrl,
                                )
                            }

                            element.hasClass("blockextract")       -> {
                                withParagraph {
                                    withComposableStyle(
                                        style = { blockQuoteStyle() }
                                    ) {
                                        appendTextChildren(
                                            element.childNodes(),
                                            lazyListScope = lazyListScope,
                                            imagePlaceholder = imagePlaceholder,
                                            onLinkClick = onLinkClick,
                                            baseUrl = baseUrl,
                                        )
                                    }
                                }
                            }

                            element.attr("role") == "alert"        -> {
                                withParagraph {
                                    withComposableStyle(
                                        style = { SpanStyle(color = MaterialTheme.colorScheme.error) }
                                    ) {
                                        appendTextChildren(
                                            element.childNodes(),
                                            lazyListScope = lazyListScope,
                                            imagePlaceholder = imagePlaceholder,
                                            onLinkClick = onLinkClick,
                                            baseUrl = baseUrl,
                                        )
                                    }
                                }
                            }

                            else                                   -> {
                                // General div handling - treat as a block-level container
                                withParagraph {
                                    appendTextChildren(
                                        element.childNodes(),
                                        lazyListScope = lazyListScope,
                                        imagePlaceholder = imagePlaceholder,
                                        onLinkClick = onLinkClick,
                                        baseUrl = baseUrl,
                                    )
                                }
                            }
                        }
                    }

                    "p"                      -> {
                        // Readability4j inserts p-tags in divs for algorithmic purposes.
                        // They screw up formatting.
                        if (node.hasClass("readability-styled")) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        } else {
                            withParagraph {
                                appendTextChildren(
                                    element.childNodes(),
                                    lazyListScope = lazyListScope,
                                    imagePlaceholder = imagePlaceholder,
                                    onLinkClick = onLinkClick,
                                    baseUrl = baseUrl,
                                )
                            }
                        }
                    }

                    "br"                     -> append('\n')
                    "h1"                     -> {
                        withParagraph {
                            withComposableStyle(
                                style = { MaterialTheme.typography.headlineMedium.toSpanStyle() }
                            ) {
                                element.appendCorrectlyNormalizedWhiteSpaceRecursively(
                                    this,
                                    stripLeading = endsWithWhitespace
                                )
                            }
                        }
                    }

                    "h2"                     -> {
                        withParagraph {
                            withComposableStyle(
                                style = { MaterialTheme.typography.headlineSmall.toSpanStyle() }
                            ) {
                                element.appendCorrectlyNormalizedWhiteSpaceRecursively(
                                    this,
                                    stripLeading = endsWithWhitespace
                                )
                            }
                        }
                    }

                    // A caption, set as one: small and quiet, directly under
                    // its picture. It fell through to the default branch and
                    // was set as body text, so "Credit: Tim Brookes / How-To
                    // Geek" read like the first sentence of the article.
                    "figcaption"             -> {
                        withParagraph {
                            withComposableStyle(
                                style = {
                                    MaterialTheme.typography.labelMedium.toSpanStyle()
                                        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            ) {
                                element.appendCorrectlyNormalizedWhiteSpaceRecursively(
                                    this,
                                    stripLeading = endsWithWhitespace
                                )
                            }
                        }
                    }

                    "h3"                     -> {
                        withParagraph {
                            withComposableStyle(
                                style = { MaterialTheme.typography.titleLarge.toSpanStyle() }
                            ) {
                                element.appendCorrectlyNormalizedWhiteSpaceRecursively(
                                    this,
                                    stripLeading = endsWithWhitespace
                                )
                            }
                        }
                    }

                    "h4"                     -> {
                        withParagraph {
                            withComposableStyle(
                                style = { MaterialTheme.typography.titleMedium.toSpanStyle() }
                            ) {
                                element.appendCorrectlyNormalizedWhiteSpaceRecursively(
                                    this,
                                    stripLeading = endsWithWhitespace
                                )
                            }
                        }
                    }

                    "h5"                     -> {
                        withParagraph {
                            withComposableStyle(
                                style = { MaterialTheme.typography.titleSmall.toSpanStyle() }
                            ) {
                                element.appendCorrectlyNormalizedWhiteSpaceRecursively(
                                    this,
                                    stripLeading = endsWithWhitespace
                                )
                            }
                        }
                    }

                    "h6"                     -> {
                        withParagraph {
                            withComposableStyle(
                                style = { MaterialTheme.typography.labelLarge.toSpanStyle() }
                            ) {
                                element.appendCorrectlyNormalizedWhiteSpaceRecursively(
                                    this,
                                    stripLeading = endsWithWhitespace
                                )
                            }
                        }
                    }

                    "strong", "b"            -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        }
                    }

                    "i", "em", "cite", "dfn" -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        }
                    }

                    "tt"                     -> {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        }
                    }

                    "u"                      -> {
                        withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        }
                    }

                    "sup"                    -> {
                        withStyle(SpanStyle(baselineShift = BaselineShift.Superscript)) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        }
                    }

                    "sub"                    -> {
                        withStyle(SpanStyle(baselineShift = BaselineShift.Subscript)) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        }
                    }

                    "font"                   -> {
                        val fontFamily: FontFamily? = element.attr("face").asFontFamily()
                        withStyle(SpanStyle(fontFamily = fontFamily)) {
                            appendTextChildren(
                                element.childNodes(),
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        }
                    }

                    "pre"                    -> {
                        terminateCurrentText()
                        lazyListScope.formatCodeBlock(
                            element = element,
                            imagePlaceholder = imagePlaceholder,
                            onLinkClick = onLinkClick,
                            baseUrl = baseUrl,
                        )
                    }

                    "code"                   -> {
                        if (element.parent()?.tagName() == "pre") {
                            terminateCurrentText()
                            lazyListScope.formatCodeBlock(
                                element = element,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl,
                            )
                        } else {
                            // inline code
                            withComposableStyle(
                                style = { codeInlineStyle() }
                            ) {
                                appendTextChildren(
                                    element.childNodes(),
                                    preFormatted = preFormatted,
                                    lazyListScope = lazyListScope,
                                    imagePlaceholder = imagePlaceholder,
                                    onLinkClick = onLinkClick,
                                    baseUrl = baseUrl,
                                )
                            }
                        }
                    }

                    "blockquote"             -> {
                        withParagraph {
                            withComposableStyle(
                                style = { blockQuoteStyle() }
                            ) {
                                appendTextChildren(
                                    element.childNodes(),
                                    lazyListScope = lazyListScope,
                                    imagePlaceholder = imagePlaceholder,
                                    onLinkClick = onLinkClick,
                                    baseUrl = baseUrl,
                                )
                            }
                        }
                    }

                    "a"                      -> {
                        withComposableStyle(
                            style = { linkTextStyle().toSpanStyle() }
                        ) {
                            withAnnotation("URL", element.attr("abs:href") ?: "") {
                                appendTextChildren(
                                    element.childNodes(),
                                    lazyListScope = lazyListScope,
                                    imagePlaceholder = imagePlaceholder,
                                    onLinkClick = onLinkClick,
                                    baseUrl = baseUrl,
                                )
                            }
                        }
                    }

                    "img"                    -> handleImage(
                        element,
                        lazyListScope,
                        onLinkClick,
                        imagePlaceholder,
                        baseUrl,
                    )

                    "ul"                     -> {
                        element.children()
                            .filter { e -> e.tagName() == "li" }
                            .forEach { listItem ->
                                withParagraph {
                                    // no break space
                                    append("• ")
                                    appendTextChildren(
                                        listItem.childNodes(),
                                        lazyListScope = lazyListScope,
                                        imagePlaceholder = imagePlaceholder,
                                        onLinkClick = onLinkClick,
                                        baseUrl = baseUrl,
                                    )
                                }
                            }
                    }

                    "ol"                     -> {
                        element.children()
                            .filter { e -> e.tagName() == "li" }
                            .forEachIndexed { i, listItem ->
                                withParagraph {
                                    // no break space
                                    append("${i + 1}. ")
                                    appendTextChildren(
                                        listItem.childNodes(),
                                        lazyListScope = lazyListScope,
                                        imagePlaceholder = imagePlaceholder,
                                        onLinkClick = onLinkClick,
                                        baseUrl = baseUrl,
                                    )
                                }
                            }
                    }

                    "table"                  -> handleTable(
                        element,
                        preFormatted,
                        lazyListScope,
                        onLinkClick,
                        imagePlaceholder,
                        baseUrl,
                    )

                    "iframe"                 -> handleIFrame(element, lazyListScope, onLinkClick)

                    "rt"                     -> {
                        append(" (")
                        appendTextChildren(
                            element.childNodes(),
                            lazyListScope = lazyListScope,
                            imagePlaceholder = imagePlaceholder,
                            onLinkClick = onLinkClick,
                            baseUrl = baseUrl,
                        )
                        append(")")
                    }

                    "rp"                     -> {
                        // Typically used to provide fallback parentheses for browsers that don't support ruby
                        // We can choose to render or ignore these depending on our needs
                        appendTextChildren(
                            element.childNodes(),
                            lazyListScope = lazyListScope,
                            imagePlaceholder = imagePlaceholder,
                            onLinkClick = onLinkClick,
                            baseUrl = baseUrl,
                        )
                    }

                    "video"                  -> handleVideo(element, lazyListScope, onLinkClick)
                    "del", "s", "strike"     -> handleStrikethrough(
                        element,
                        lazyListScope,
                        imagePlaceholder,
                        onLinkClick,
                        baseUrl,
                    )

                    "mark"                   -> handleHighlight(
                        element,
                        lazyListScope,
                        imagePlaceholder,
                        onLinkClick,
                        baseUrl,
                    )

                    "q"                      -> handleInlineQuote(
                        element,
                        lazyListScope,
                        imagePlaceholder,
                        onLinkClick,
                        baseUrl,
                    )

                    "abbr"                   -> handleAbbreviation(
                        element,
                        lazyListScope,
                        imagePlaceholder,
                        onLinkClick,
                        baseUrl,
                    )

                    "hr"                     -> lazyListScope.handleHorizontalRule()
                    else                     -> {
                        appendTextChildren(
                            nodes = element.childNodes(),
                            preFormatted = preFormatted,
                            lazyListScope = lazyListScope,
                            imagePlaceholder = imagePlaceholder,
                            onLinkClick = onLinkClick,
                            baseUrl = baseUrl,
                        )
                    }
                }
            }
        }

        node = node.nextSibling()
    }
}

private fun TextComposer.handleImage(
    element: Element,
    lazyListScope: LazyListScope,
    onLinkClick: (String) -> Unit,
    imagePlaceholder: Int,
    baseUrl: String,
) {
    val imageCandidates = getImageSource(baseUrl, element)
    if (imageCandidates.hasImage) {
        // Some sites are silly and insert formatting in alt text
        val alt = stripHtml(element.attr("alt") ?: "")
        appendImage(onLinkClick = onLinkClick) { onClick ->
            lazyListScope.item {
                val dimens = LocalDimens.current
                Column(
                    modifier = Modifier
                        .width(dimens.maxContentWidth)
                ) {
                    DisableSelection {
                        BoxWithConstraints(
                            modifier = Modifier
                                .clip(RectangleShape)
                                .clickable(
                                    enabled = onClick != null
                                ) {
                                    onClick?.invoke()
                                }
                                .fillMaxWidth()
                            // This makes scrolling a pain, find a way to solve that
//                                            .pointerInput("imgzoom") {
//                                                detectTransformGestures { centroid, pan, zoom, rotation ->
//                                                    val z = zoom * scale.value
//                                                    scale.value = when {
//                                                        z < 1f -> 1f
//                                                        z > 3f -> 3f
//                                                        else -> z
//                                                    }
//                                                }
//                                            }
                        ) {
                            val imageWidth = maxImageWidth()
                            // Zero until the box has been measured, and Coil refuses a zero
                            // dimension with an IllegalArgumentException thrown from inside the
                            // request builder — no fetch, no retry, just the error placeholder.
                            // Skipping the frame costs nothing: BoxWithConstraints recomposes
                            // with the real width and the picture arrives then.
                            // `hasImage` above is a cheap pre-check on the
                            // attributes, not a promise of an address: a srcset
                            // whose candidates all carry descriptors this cannot
                            // parse, next to an empty src, satisfies it and
                            // resolves to nothing. Coil then reports
                            // `IllegalArgumentException: Invalid URL ""`, which
                            // is what the device report showed. The resolved
                            // address is the thing worth testing, so it is.
                            val src = imageCandidates.getBestImageForMaxSize(
                                pixelDensity = pixelDensity(),
                                maxWidth = imageWidth.coerceAtLeast(1),
                            )
                            // The width the picture decoded at, once it has. Coil
                            // decodes no larger than the source at INEXACT, so for a
                            // small image this is the image's own size.
                            var decodedWidth by remember(src) { mutableIntStateOf(0) }
                            // A saved article's picture, kept on the phone, if
                            // there is one under any address this image offers:
                            // what makes Read later readable offline. See
                            // SavedImages.
                            val context = LocalContext.current
                            val kept = remember(src) {
                                SavedImages.local(context.filesDir, listOf(src) + imageCandidates.allUrls())
                            }
                            if (imageWidth > 0 && src.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(kept ?: src)
                                    .placeholder(imagePlaceholder)
                                    .error(imagePlaceholder)
                                    .scale(Scale.FIT)
                                    .size(imageWidth)
                                    .precision(Precision.INEXACT)
                                    .tag(ImageSurface::class.java, ImageSurface.Article)
                                    .build(),
                                contentScale = ContentScale.FillWidth,
                                contentDescription = alt,
                                onSuccess = { state ->
                                    decodedWidth = state.painter.intrinsicSize.width
                                        .takeIf { it.isFinite() }?.roundToInt() ?: 0
                                },
                                modifier = Modifier.articleImageWidth(decodedWidth, imageWidth)
                            )
                            }
                        }
                    }

                    // No caption from the alt text. Alt is the picture described
                    // for someone who cannot see it - it is the contentDescription
                    // above, which is where TalkBack reads it - and no browser
                    // draws it under an image that loaded. Drawn here it
                    // described the photograph in words directly beneath the
                    // photograph ("Two jackets shown close-up: one is orange and
                    // brown with a visible BEAMS logo...") and then the page's
                    // real caption or credit followed it, so most images carried
                    // two. The page's <figcaption> is the caption; see the
                    // "figcaption" branch.

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun TextComposer.handleTable(
    element: Element,
    preFormatted: Boolean,
    lazyListScope: LazyListScope,
    onLinkClick: (String) -> Unit,
    imagePlaceholder: Int,
    baseUrl: String,
) {
    appendTable {
        lazyListScope.item {
            // Create a separate TextComposer for building cell contents
            val cellComposer = TextComposer { builder ->
                // This won't be called immediately, just storing the builder
                builder
            }

            element.children()
                .filter { e -> e.tagName() == "caption" }
                .forEach {
                    Text(
                        text = it.text(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                }

            val rows = element.children()
                .filter { e -> e.tagName() == "thead" || e.tagName() == "tbody" || e.tagName() == "tfoot" }
                .flatMap { section ->
                    section.children()
                        .filter { child -> child.tagName() == "tr" }
                }
                .map { row ->
                    row.children()
                        .filter { cell -> cell.tagName() == "td" || cell.tagName() == "th" }
                        .map { cell ->
                            // Build cell content using the cell composer
                            cellComposer.appendTextChildren(
                                cell.childNodes(),
                                preFormatted = preFormatted,
                                lazyListScope = lazyListScope,
                                imagePlaceholder = imagePlaceholder,
                                onLinkClick = onLinkClick,
                                baseUrl = baseUrl
                            )
                            // Get the built content and reset composer for next cell
                            val content = cellComposer.builder.toComposableAnnotatedString()
                            cellComposer.terminateCurrentText()

                            TableCell(
                                content = content,
                                colspan = cell.attr("colspan").toIntOrNull() ?: 1,
                                rowspan = cell.attr("rowspan").toIntOrNull() ?: 1,
                            )
                        }
                }
            Table(rows)
        }
    }
}

private fun TextComposer.handleIFrame(
    element: Element,
    lazyListScope: LazyListScope,
    onLinkClick: (String) -> Unit,
) {
    // TODO support code and others in iframe
    val video: Video? = getVideo(element.attr("abs:src"))

    if (video != null) {
        appendImage(onLinkClick = onLinkClick) {
            lazyListScope.item {
                val dimens = LocalDimens.current
                Column(
                    modifier = Modifier
                        .width(dimens.maxContentWidth)
                ) {
                    DisableSelection {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val imageWidth = maxImageWidth()
                            // Zero until the box has been measured, and Coil refuses a zero
                            // dimension with an IllegalArgumentException thrown from inside the
                            // request builder — no fetch, no retry, just the error placeholder.
                            // Skipping the frame costs nothing: BoxWithConstraints recomposes
                            // with the real width and the picture arrives then.
                            if (imageWidth > 0) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    // The video's own still. This request was
                                    // built without any data at all, so every
                                    // embedded video in every article drew the
                                    // fallback mark and none of them ever tried
                                    // to fetch the thumbnail they had.
                                    .data(video.imageUrl)
                                    .placeholder(R.drawable.ic_youtube)
                                    .error(R.drawable.ic_youtube)
                                    .scale(Scale.FIT)
                                    .size(imageWidth)
                                    .precision(Precision.INEXACT)
                                    .tag(ImageSurface::class.java, ImageSurface.Article)
                                    .build(),
                                contentScale = ContentScale.FillWidth,
                                contentDescription = stringResource(R.string.touch_to_play_video),
                                modifier = Modifier
                                    .clickable {
                                        onLinkClick(video.link)
                                    }
                                    .fillMaxWidth()
                            )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.touch_to_play_video),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun TextComposer.handleVideo(
    element: Element,
    lazyListScope: LazyListScope,
    onLinkClick: (String) -> Unit,
) {
    val videoSrc = element.attr("src")
    val posterSrc = element.attr("poster")

    appendImage(onLinkClick = onLinkClick) {
        lazyListScope.item {
            val dimens = LocalDimens.current
            Column(
                modifier = Modifier
                    .width(dimens.maxContentWidth)
            ) {
                DisableSelection {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val imageWidth = maxImageWidth()
                        // Zero until the box has been measured, and Coil refuses a zero
                        // dimension with an IllegalArgumentException thrown from inside the
                        // request builder — no fetch, no retry, just the error placeholder.
                        // Skipping the frame costs nothing: BoxWithConstraints recomposes
                        // with the real width and the picture arrives then.
                        if (imageWidth > 0) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(posterSrc.ifEmpty { R.drawable.ic_youtube })
                                .placeholder(R.drawable.ic_youtube)
                                .error(R.drawable.ic_youtube)
                                .scale(Scale.FIT)
                                .size(imageWidth)
                                .precision(Precision.INEXACT)
                                .tag(ImageSurface::class.java, ImageSurface.Article)
                                .build(),
                            contentScale = ContentScale.FillWidth,
                            contentDescription = stringResource(R.string.touch_to_play_video),
                            modifier = Modifier
                                .clickable {
                                    onLinkClick(videoSrc)
                                }
                                .fillMaxWidth()
                        )
                        }

                        // Play button overlay
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Phosphor.Play,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                    .padding(8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.touch_to_play_video),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun TextComposer.handleStrikethrough(
    element: Element,
    lazyListScope: LazyListScope,
    imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    baseUrl: String,
) {
    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
        appendTextChildren(
            element.childNodes(),
            lazyListScope = lazyListScope,
            imagePlaceholder = imagePlaceholder,
            onLinkClick = onLinkClick,
            baseUrl = baseUrl,
        )
    }
}

private fun TextComposer.handleHighlight(
    element: Element,
    lazyListScope: LazyListScope,
    imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    baseUrl: String,
) {
    withComposableStyle(
        style = { SpanStyle(background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)) }
    ) {
        appendTextChildren(
            element.childNodes(),
            lazyListScope = lazyListScope,
            imagePlaceholder = imagePlaceholder,
            onLinkClick = onLinkClick,
            baseUrl = baseUrl,
        )
    }
}

private fun TextComposer.handleInlineQuote(
    element: Element,
    lazyListScope: LazyListScope,
    imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    baseUrl: String,
) {
    append("\"\"\"")
    appendTextChildren(
        element.childNodes(),
        lazyListScope = lazyListScope,
        imagePlaceholder = imagePlaceholder,
        onLinkClick = onLinkClick,
        baseUrl = baseUrl,
    )
    append("\"\"\"")
}

private fun TextComposer.handleAbbreviation(
    element: Element,
    lazyListScope: LazyListScope,
    imagePlaceholder: Int,
    onLinkClick: (String) -> Unit,
    baseUrl: String,
) {
    val title = element.attr("title")
    withAnnotation("TOOLTIP", title) {
        appendTextChildren(
            element.childNodes(),
            lazyListScope = lazyListScope,
            imagePlaceholder = imagePlaceholder,
            onLinkClick = onLinkClick,
            baseUrl = baseUrl,
        )
    }
}

private fun LazyListScope.handleHorizontalRule() {
    item {
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    }
}

private fun String.asFontFamily(): FontFamily? = when (this.lowercase()) {
    "monospace"  -> FontFamily.Monospace
    "serif"      -> FontFamily.Serif
    "sans-serif" -> FontFamily.SansSerif
    else         -> null
}

@Preview
@Composable
private fun TestIt() {
    val context = LocalContext.current
    val html = """
        <p>In Gimp you go to <em>Image</em> in the top menu bar and select <em>Mode</em> followed by <em>Indexed</em>. Now you see a popup where you can select the number of colors for a generated optimum palette.</p> <p>You&rsquo;ll have to experiment a little because it will depend on your image.</p> <p>I used this approach to shrink the size of the cover image in <a href="https://cowboyprogrammer.org/2016/08/zopfli_all_the_things/">the_zopfli post</a> from a 37KB (JPG) to just 15KB (PNG, all PNG sizes listed include Zopfli compression btw).</p> <h2 id="straight-jpg-to-png-conversion-124kb">Straight JPG to PNG conversion: 124KB</h2> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things.png" alt="PNG version RGB colors" /></p> <p>First off, I exported the JPG file as a PNG file. This PNG file had a whopping 124KB! Clearly there was some bloat being stored.</p> <h2 id="256-colors-40kb">256 colors: 40KB</h2> <p>Reducing from RGB to only 256 colors has no visible effect to my eyes.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_256.png" alt="256 colors" /></p> <h2 id="128-colors-34kb">128 colors: 34KB</h2> <p>Still no difference.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_128.png" alt="128 colors" /></p> <h2 id="64-colors-25kb">64 colors: 25KB</h2> <p>You can start to see some artifacting in the shadow behind the text.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_64.png" alt="64 colors" /></p> <h2 id="32-colors-15kb">32 colors: 15KB</h2> <p>In my opinion this is the sweet spot. The shadow artifacting is barely noticable but the size is significantly reduced.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_32.png" alt="32 colors" /></p> <h2 id="16-colors-11kb">16 colors: 11KB</h2> <p>Clear artifacting in the text shadow and the yellow (fire?) in the background has developed an outline.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_16.png" alt="16 colors" /></p> <h2 id="8-colors-7-3kb">8 colors: 7.3KB</h2> <p>The broom has shifted in color from a clear brown to almost grey. Text shadow is just a grey blob at this point. Even clearer outline developed on the yellow background.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_8.png" alt="8 colors" /></p> <h2 id="4-colors-4-3kb">4 colors: 4.3KB</h2> <p>Interestingly enough, I think 4 colors looks better than 8 colors. The outline in the background has disappeared because there&rsquo;s not enough color spectrum to render it. The broom is now black and filled areas tend to get a white separator to the outlines.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_4.png" alt="4 colors" /></p> <h2 id="2-colors-2-4kb">2 colors: 2.4KB</h2> <p>Well, at least the silhouette is well defined at this point I guess.</p> <p><img src="https://cowboyprogrammer.org/images/2017/10/zopfli_all_the_things_2.png" alt="2 colors" /></p> <hr/> <p>Other posts in the <b>Migrating from Ghost to Hugo</b> series:</p> <ul class="series"> <li>2016-10-21 &mdash; Reduce the size of images even further by reducing number of colors with Gimp </li> <li>2016-08-26 &mdash; <a href="https://cowboyprogrammer.org/2016/08/zopfli_all_the_things/">Compress all the images!</a> </li> <li>2016-07-25 &mdash; <a href="https://cowboyprogrammer.org/2016/07/migrating_from_ghost_to_hugo/">Migrating from Ghost to Hugo</a> </li> </ul>
    """.trimIndent()

    html.byteInputStream().use { stream ->
        LazyColumn {
            htmlFormattedText(
                inputStream = stream,
                baseUrl = "https://cowboyprogrammer.org",
                imagePlaceholder = R.drawable.placeholder_image_article_day,
                onLinkClick = context::launchView
            )
        }
    }
}

@Composable
private fun pixelDensity() = with(LocalDensity.current) {
    density
}

@Composable
/**
 * How wide to decode an article's image, or zero if that is not known yet.
 *
 * Zero is a real answer and has to be handled by the caller. A
 * `BoxWithConstraints` can be measured with no width — during a lazy list's
 * first pass, or while a pane is collapsed — and `maxWidth.toPx()` is then 0.
 *
 * Coil refuses it: `Dimension.Pixels` carries `require(px > 0)` and throws
 * `IllegalArgumentException("px must be > 0.")` from inside the request
 * builder, before any fetch is attempted. The request never runs, the error
 * placeholder is drawn, and nothing anywhere says why — which is how article
 * images came to show Whisper's own placeholder while the same article in a
 * browser showed the photograph. It took the image trace counting failures to
 * see it at all.
 */
fun BoxWithConstraintsScope.maxImageWidth() = with(LocalDensity.current) {
    maxWidth.toPx().roundToInt().coerceAtMost(2000).coerceAtLeast(0)
}

/**
 * How wide to draw an article image: the column, unless that would stretch it.
 *
 * Every image filled the column whatever its size, so a byline's avatar -
 * The Verge's and How-To Geek's are both around a hundred pixels - was blown
 * up to the full width of the phone, blurred, and read as the article's
 * photograph. No picture gains anything from being drawn at more than twice
 * the pixels it has, so past that it is drawn at its own size instead.
 *
 * Full width until the decode says otherwise, so a photograph never starts
 * small and grows; only an image that turns out to be small shrinks, once.
 */
@Composable
private fun Modifier.articleImageWidth(decodedPx: Int, columnPx: Int): Modifier =
    if (isTooSmallToStretch(decodedPx, columnPx)) {
        with(LocalDensity.current) { width(decodedPx.toDp()) }
    } else {
        fillMaxWidth()
    }

/** Whether filling [columnPx] would stretch a [decodedPx]-wide image past [MAX_IMAGE_UPSCALE]x. */
internal fun isTooSmallToStretch(decodedPx: Int, columnPx: Int): Boolean =
    decodedPx > 0 && columnPx > 0 && decodedPx * MAX_IMAGE_UPSCALE < columnPx

/** The most an article image is ever enlarged to fill the column. */
internal const val MAX_IMAGE_UPSCALE = 2

/**
 * Gets the url to the image in the <img> tag - could be from srcset or from src
 */
internal fun getImageSource(baseUrl: String, element: Element) = ImageCandidates(
    baseUrl = baseUrl,
    srcSet = element.attr("srcset") ?: "",
    absSrc = element.attr("abs:src") ?: "",
)

internal class ImageCandidates(
    val baseUrl: String,
    val srcSet: String,
    val absSrc: String
) {
    val hasImage: Boolean = srcSet.isNotBlank() || absSrc.isNotBlank()

    /** Every address on offer, resolved: each srcset candidate, then the src. */
    fun allUrls(): List<String> =
        (srcSet.splitToSequence(",")
            .map { it.trim().split(SpaceRegex).first() }
            .filter { it.isNotBlank() }
            .map { StringUtil.resolve(baseUrl, it) }
            .toList() + listOf(absSrc).filter { it.isNotBlank() }.map { StringUtil.resolve(baseUrl, it) })
            .distinct()

    /**
     * Might throw if hasImage returns false
     */
    fun getBestImageForMaxSize(maxWidth: Int, pixelDensity: Float): String {
        val setCandidate = srcSet.splitToSequence(",")
            .map { it.trim() }
            .map { it.split(SpaceRegex).take(2).map { x -> x.trim() } }
            .fold(100f to "") { acc, candidate ->
                val candidateSize = if (candidate.size == 1) {
                    // Assume it corresponds to 1x pixel density
                    1.0f / pixelDensity
                } else {
                    val descriptor = candidate.last()
                    when {
                        descriptor.endsWith("w", ignoreCase = true) -> {
                            descriptor.substringBefore("w").toFloat() / maxWidth
                                .toFloat()
                        }

                        descriptor.endsWith("x", ignoreCase = true) -> {
                            descriptor.substringBefore("x").toFloat() / pixelDensity
                        }

                        else                                        -> {
                            return@fold acc
                        }
                    }
                }

                if (abs(candidateSize - 1.0f) < abs(acc.first - 1.0f)) {
                    candidateSize to candidate.first()
                } else {
                    acc
                }
            }
            .second

        if (setCandidate.isNotBlank()) {
            return StringUtil.resolve(baseUrl, setCandidate)
        }
        return StringUtil.resolve(baseUrl, absSrc)
    }
}

private val SpaceRegex = Regex("\\s+")

/**
 * Can't use JSoup's text() method because that strips invisible characters
 * such as ZWNJ which are crucial for several languages.
 */
fun TextNode.appendCorrectlyNormalizedWhiteSpace(
    builder: TextComposer,
    stripLeading: Boolean
) {
    val string = wholeText

    var reachedNonWhite = false
    var lastWasWhite = false
    var i = 0
    while (i < string.length) {
        val code = string.codePointAt(i)

        // Unicode smileys are an example of where toChar() won't work. Needs to be String.
        val char = String(intArrayOf(code), 0, 1)
        i += Character.charCount(code)

        lastWasWhite = if (isCollapsableWhiteSpace(char)) {
            if (!(stripLeading && !reachedNonWhite || lastWasWhite)) {
                builder.append(' ')
            }
            true
        } else {
            reachedNonWhite = true
            builder.append(char)
            false
        }
    }
}

fun Element.appendCorrectlyNormalizedWhiteSpaceRecursively(
    builder: TextComposer,
    stripLeading: Boolean
) {
    for (child in childNodes()) {
        when (child) {
            is TextNode -> child.appendCorrectlyNormalizedWhiteSpace(builder, stripLeading)
            is Element  -> child.appendCorrectlyNormalizedWhiteSpaceRecursively(
                builder,
                stripLeading
            )
        }
    }
}

private const val space = ' '
private const val tab = '\t'
private const val linefeed = '\n'
private const val carriageReturn = '\r'

// 12 is form feed which as no escape in kotlin
private const val formFeed = 12.toChar()

// 160 is &nbsp; (non-breaking space). Not in the spec but expected.
private const val nonBreakableSpace = 160.toChar()

private fun isCollapsableWhiteSpace(c: String) =
    c.firstOrNull()?.let { isCollapsableWhiteSpace(it) } ?: false

private fun isCollapsableWhiteSpace(c: Char) =
    c == space || c == tab || c == linefeed || c == carriageReturn || c == formFeed || c == nonBreakableSpace

/**
 * Super basic function to strip html formatting from alt-texts.
 */
fun stripHtml(html: String): String {
    val result = StringBuilder()

    var skipping = false

    for (char in html) {
        if (!skipping) {
            if (char == '<') {
                skipping = true
            } else {
                result.append(char)
            }
        } else {
            if (char == '>') {
                skipping = false
            } else {
                // Skipping char
            }
        }
    }

    return result.toString()
}
