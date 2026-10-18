/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saulhdev.feeder

import android.app.Application
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.authorName
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.manager.models.Page
import com.saulhdev.feeder.manager.models.isThinExtraction
import com.saulhdev.feeder.manager.models.pageDocument
import com.saulhdev.feeder.utils.getImageSource
import com.saulhdev.feeder.utils.imageIdentity
import com.saulhdev.feeder.utils.normalizedHeading
import com.saulhdev.feeder.utils.readerBody
import com.saulhdev.feeder.utils.stripPageChrome
import com.saulhdev.feeder.utils.usableImageUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import net.dankito.readability4j.extended.Readability4JExtended
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * The reader over real feeds, for looking at rather than for passing.
 *
 * Skipped unless REAL_FEED_DIR names a folder that scripts/real-feed-check.py
 * has filled. Each feed goes through the app's parser and each article
 * through the path the full-text fetch and the reader take: stripPageChrome,
 * Readability, the thin-page guard, then readerBody. Where the page did not
 * come back the feed's own text is read instead, as the app falls back to.
 * What the reader would show is checked for the faults found by hand so far,
 * and written to results.json, with each article's body in bodies/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RealFeedReport {

    private class Flag(val kind: String, val detail: String)

    @Test
    fun `the reader over real feeds`() {
        val dir = System.getenv("REAL_FEED_DIR")?.let(::File)
        assumeTrue("REAL_FEED_DIR not set", dir?.resolve("index.json")?.isFile == true)
        dir!!
        val bodies = dir.resolve("bodies").apply { mkdirs() }
        val perFeed = (System.getenv("REAL_FEED_PER_FEED") ?: "4").toInt()
        val index = Json.parseToJsonElement(dir.resolve("index.json").readText()).jsonArray

        val results = buildJsonArray {
            index.map { it.jsonObject }.forEach { feed ->
                add(buildJsonObject {
                    put("title", feed.string("title").orEmpty().trim())
                    put("url", feed.string("url"))
                    val file = feed.string("file")
                    if (file == null) {
                        put("problem", "feed not fetched: ${feed.string("status")}")
                        put("articles", JsonArray(emptyList()))
                        return@buildJsonObject
                    }
                    val pages = feed["pages"]!!.jsonArray.map { it.jsonObject }
                    val articles = runCatching { readFeed(dir, feed, file, pages, perFeed, bodies) }
                    articles.onFailure { put("problem", "parse failed: ${it.message?.take(200)}") }
                    put("articles", JsonArray(articles.getOrDefault(emptyList())))
                })
            }
        }
        dir.resolve("results.json").writeText(results.toString())
    }

    private fun readFeed(
        dir: File,
        feed: JsonObject,
        file: String,
        pages: List<JsonObject>,
        perFeed: Int,
        bodies: File,
    ): List<JsonObject> {
        val finalUrl = feed.string("finalUrl") ?: feed.string("url")!!
        val bytes = dir.resolve(file).readBytes()
        val parsed = runBlocking {
            FeedParser().parseFeedResponse(
                url = URL(finalUrl),
                responseBody = bytes.toResponseBody(feed.string("contentType")?.toMediaTypeOrNull()),
                findIcon = false,
            )
        }
        return parsed.items.orEmpty().take(perFeed).map { item ->
            val article = Article().updateFromParsedEntry(item, (item.id ?: item.url).toString(), parsed, 1L)
            val link = article.link.orEmpty()
            val feedText = item.content_html ?: item.content_text ?: ""
            val page = pages.firstOrNull { it.string("url") == link || it.string("finalUrl") == link }

            // What the full-text fetch would have stored, or why the feed's own text is read.
            var source = "feed"
            var stored = feedText
            val pageFile = page?.string("file")
            if (pageFile == null) {
                source = "feed (page ${page?.string("status") ?: "not tried"})"
            } else {
                val fetched = Page(dir.resolve(pageFile).readBytes(), charsetOf(page.string("contentType")))
                val extracted = runCatching {
                    val document = pageDocument(fetched, link).also(::stripPageChrome)
                    Readability4JExtended(link, document).parse()
                }.getOrNull()
                val chars = extracted?.textContent.orEmpty().trim().length
                when {
                    extracted == null -> source = "feed (extraction failed)"
                    isThinExtraction(chars, Jsoup.parse(feedText).text().trim().length) -> source = "feed (page thinner)"
                    else -> {
                        source = "page"
                        stored = extracted.contentWithUtf8Encoding.orEmpty()
                    }
                }
            }

            val author = authorName(article.author)
            val card = usableImageUrl(article.imageUrl)
            val reader = readerBody(
                stored.byteInputStream(), link,
                articleTitle = article.title, leadImageUrl = card, author = author,
            )
            val name = hash(link.ifEmpty { article.title })
            bodies.resolve("$name.html").writeText(
                "<!-- ${article.title.replace("--", "-")} | $link | $source | lead ${reader.leadImage} -->\n" +
                    reader.body.html()
            )
            buildJsonObject {
                put("title", article.title)
                put("link", link)
                put("source", source)
                put("author", author)
                put("lead", reader.leadImage)
                put("chars", reader.body.text().length)
                put("images", reader.body.select("img").size)
                put("body", "bodies/$name.html")
                put("flags", buildJsonArray {
                    check(reader.body, article.title, author, reader.leadImage, source, link).forEach {
                        add(buildJsonObject { put("kind", it.kind); put("detail", it.detail) })
                    }
                })
            }
        }
    }

    /** What looks wrong in what the reader would draw. */
    private fun check(
        body: Element,
        title: String,
        author: String?,
        lead: String?,
        source: String,
        link: String,
    ): List<Flag> {
        val flags = mutableListOf<Flag>()
        val lines = lines(body)

        // A caption or credit that has lost its picture.
        lines.filter { line ->
            val text = line.text().trim()
            text.length <= 200 && (line.tagName() == "figcaption" || CREDIT.containsMatchIn(text)) &&
                !nearPicture(line)
        }.forEach { flags += Flag("caption without picture", it.text().trim()) }

        // The same picture twice, or the lead again in the text.
        val seen = mutableSetOf<String>()
        lead?.let { seen += plain(imageIdentity(it)) }
        body.select("img").forEach { img ->
            val identity = plain(imageIdentity(firstAddress(img)))
            if (identity.isNotEmpty() && !seen.add(identity)) flags += Flag("picture twice", identity)
        }

        // A picture the reader cannot draw.
        body.select("img").forEach { img ->
            val candidates = getImageSource(link, img)
            val address = firstAddress(img)
            if (!candidates.hasImage || !(address.startsWith("http://") || address.startsWith("https://"))) {
                flags += Flag("picture with no usable address", img.outerHtml().take(200))
            }
        }

        // The headline again at the top of the text.
        val wanted = normalizedHeading(title)
        lines.take(3).firstOrNull { wanted.isNotEmpty() && normalizedHeading(it.text()) == wanted }
            ?.let { flags += Flag("headline repeated", it.text().trim()) }

        // A byline that is not a name.
        if (author != null && ("@" in author || author.lowercase().startsWith("by ") || author.startsWith("http"))) {
            flags += Flag("odd byline", author)
        }
        lines.take(4).firstOrNull { it.text().trim().let { t -> t.length < 80 && BY_LINE.containsMatchIn(t) } }
            ?.let { flags += Flag("byline left in text", it.text().trim()) }

        // Not much of an article.
        val chars = body.text().trim().length
        if (source == "page" && chars < 400 || chars < 120) flags += Flag("nearly empty", "$chars characters, $source")

        // Page furniture that came through.
        lines.filter { it.text().trim().let { t -> t.length <= 60 && FURNITURE.matches(t) } }
            .forEach { flags += Flag("page furniture", it.text().trim()) }

        // Markup or encoding showing as text.
        val text = body.text()
        RAW.find(text)?.let { flags += Flag("markup or mojibake in text", text.around(it.range.first)) }

        return flags
    }

    /** Lines of text as the reader lays them out. */
    private fun lines(body: Element): List<Element> =
        body.select("p, h1, h2, h3, h4, h5, h6, li, figcaption, blockquote, small, cite, div, span")
            .filter { e ->
                e.ownText().isNotBlank() &&
                    (e.tagName() !in setOf("span", "small", "cite") || e.parent()?.ownText().isNullOrBlank())
            }

    /** Whether a picture sits in the same figure, or right before or after. */
    private fun nearPicture(line: Element): Boolean {
        var e: Element? = line
        repeat(3) {
            val here = e ?: return false
            if (here.tagName() == "figure" && here.selectFirst("img, picture, video, iframe") != null) return true
            listOfNotNull(here.previousElementSibling(), here.nextElementSibling()).forEach { sibling ->
                if (sibling.`is`("img, picture, video, iframe") || sibling.selectFirst("img, picture, video, iframe") != null) {
                    return true
                }
            }
            e = here.parent()
        }
        return false
    }

    private fun firstAddress(img: Element): String =
        img.attr("abs:src").ifBlank { img.attr("src") }
            .ifBlank { img.attr("abs:srcset").substringBefore(',').trim().substringBefore(' ') }

    private fun plain(identity: String) = identity.replace(Regex("\\.[a-z0-9]{1,5}$"), "")

    private fun charsetOf(contentType: String?): Charset? =
        contentType?.toMediaTypeOrNull()?.charset()

    private fun hash(text: String) =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    private fun String.around(at: Int) = substring((at - 40).coerceAtLeast(0), (at + 60).coerceAtMost(length))

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    private companion object {
        val CREDIT = Regex(
            """\b(getty images|reuters|ap photo|afp|shutterstock|image credit|photo credit|photograph:|""" +
                """photo:|credit:|courtesy of|via getty|bloomberg via|\(c\)|©)""",
            RegexOption.IGNORE_CASE,
        )
        val BY_LINE = Regex("""^(by|written by|words by)\s+\S""", RegexOption.IGNORE_CASE)
        val FURNITURE = Regex(
            """(?i)^(share( this( article| story)?)?|advertisement|sponsored|sign up.*|subscribe.*|related( articles| stories)?|""" +
                """read more.*|recommended( for you)?|follow us.*|newsletter.*|comments?|\d+ comments?|skip to.*|""" +
                """accept( all)?( cookies)?|log ?in|sign ?in|you may also like|most popular|trending( now)?|""" +
                """more from .*|see also|click here.*|loading\W*)$""",
        )
        val RAW = Regex("""</?(p|div|span|br|img|a)\b|&(amp|lt|gt|quot|nbsp);|â€|Ã[\u0080-¿]|\]\]>""")
    }
}
