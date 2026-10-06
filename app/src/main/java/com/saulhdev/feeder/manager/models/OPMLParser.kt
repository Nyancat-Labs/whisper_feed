package com.saulhdev.feeder.manager.models

import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.sloppyLinkToStrictURL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ccil.cowan.tagsoup.Parser
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.InputSource
import org.xml.sax.Locator
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.util.Stack

class OPMLParser(private val opmlToDb: ParserToDatabase<Feed>) : ContentHandler {

    private val parser: Parser = Parser()
    private val tagStack: Stack<String> = Stack()
    private var isFeedTag = false
    private var ignoring = 0
    var feeds: MutableList<Feed> = mutableListOf()

    init {
        parser.contentHandler = this
    }

    @Throws(IOException::class, SAXException::class)
    suspend fun parseInputStream(inputStream: InputStream) = withContext(Dispatchers.IO) {
        feeds = mutableListOf()
        tagStack.clear()
        isFeedTag = false
        ignoring = 0

        parser.parse(InputSource(escapingBareAmpersands(inputStream)))

        for (feed in feeds) {
            opmlToDb.saveItem(feed)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if ("outline" == localName) {
            when {
                ignoring > 0 -> ignoring--
                isFeedTag -> isFeedTag = false
                else -> tagStack.pop()
            }
        }
    }

    override fun processingInstruction(target: String?, data: String?) {
    }

    override fun startPrefixMapping(prefix: String?, uri: String?) {
    }

    override fun ignorableWhitespace(ch: CharArray?, start: Int, length: Int) {
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
    }

    override fun endDocument() {
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, atts: Attributes?) {
        if ("outline" == localName) {
            when {
                // Nesting not allowed
                ignoring > 0 || isFeedTag -> ignoring++
                outlineIsFeed(atts) -> {
                    isFeedTag = true
                    val feedTitle = unescape(
                        atts?.getValue("title") ?: atts?.getValue("text")
                        ?: ""
                    )
                    val feed = Feed(
                        title = feedTitle,
                        tag = if (tagStack.isNotEmpty()) tagStack.peek() else "",
                        url = sloppyLinkToStrictURL(atts?.getValue("xmlurl") ?: "")
                    )

                    feeds.add(feed)
                }

                else -> tagStack.push(
                    unescape(
                        atts?.getValue("title")
                            ?: atts?.getValue("text")
                            ?: ""
                    )
                )
            }
        }
    }

    private fun outlineIsFeed(atts: Attributes?): Boolean =
        atts?.getValue("xmlurl") != null

    override fun skippedEntity(name: String?) {
    }

    override fun setDocumentLocator(locator: Locator?) {
    }

    override fun endPrefixMapping(prefix: String?) {
    }

    override fun startDocument() {
    }
}

/**
 * Escapes an `&` that is not already starting an entity.
 *
 * Exports in the wild carry raw ampersands in titles and query strings — "AT&T",
 * `?a=1&b=2` — which are not well-formed XML, and a strict parser stops at the
 * first one. The file is otherwise fine and the reader has no way to know what
 * went wrong, so the character is repaired rather than the import refused.
 *
 * **This is an encoding repair, not a security measure.** It was called
 * `xmlSafe`, which read as though it hardened the parse and did no such thing:
 * it does nothing about external entities, nothing about a DOCTYPE, and
 * nothing about expansion. A name that claims safety is worse than no guard at
 * all, because the next person to add a parser reaches for it and stops
 * looking. Where hardening is actually needed, it is on the parser — see
 * `FeedLibrary`.
 *
 * Whole file in memory rather than streamed: an OPML of a thousand feeds is a
 * couple of hundred kilobytes, and a streaming fixer would have to buffer
 * across chunk boundaries to recognise an entity anyway.
 */
internal fun escapingBareAmpersands(input: InputStream): InputStream {
    // Whole file in memory rather than streamed: an OPML of a thousand feeds
    // is a couple of hundred kilobytes, and a streaming fixer would have to
    // buffer across chunk boundaries to recognise an entity anyway.
    val text = input.bufferedReader().use { it.readText() }
    val fixed = BARE_AMPERSAND.replace(text, "&amp;")
    return fixed.byteInputStream()
}

/** An `&` not already starting a named, decimal or hexadecimal entity. */
private val BARE_AMPERSAND =
    Regex("&(?!(?:#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);)")
