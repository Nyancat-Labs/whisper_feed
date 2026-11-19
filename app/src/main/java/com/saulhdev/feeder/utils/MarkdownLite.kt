/*
 * This file is part of Whisper.
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

/**
 * Markdown sent as a feed's content, turned into the HTML the rest of the app
 * reads (sheet 45).
 *
 * Cyberspace (cyberspace.online) writes each post's Markdown straight into
 * the feed, with `<br>` for line breaks and no other HTML. Read as HTML, its
 * pictures were text ("![IMG_….webp](https://…)"), so no card picture and a
 * file name for a headline, and its code blocks of ASCII art lost their
 * spacing.
 *
 * Deliberately small: code blocks, pictures, links, headings, bold, italic
 * with asterisks, inline code, lists and quotes. And deliberately careful
 * about when it runs: only content with no HTML but `<br>`, and with a clear
 * Markdown mark, so an ordinary feed is never touched.
 */
object MarkdownLite {

    private val BR = Regex("""(?i)<br\s*/?>""")
    private val ANY_TAG = Regex("""<\s*/?\s*([a-zA-Z][a-zA-Z0-9]*)[^>]*>""")
    private val MARKS = listOf(
        Regex("""!\[[^\]]*]\([^)\s]+\)"""),
        Regex("""(?m)^\s*```"""),
        Regex("""(?m)^#{1,6}\s+\S"""),
        Regex("""\*\*[^*\n]+\*\*"""),
        Regex("""\[[^\]]+]\(https?://[^)\s]+\)"""),
    )

    /** Whether [content] is Markdown rather than HTML. */
    fun isMarkdown(content: String?): Boolean {
        if (content.isNullOrBlank()) return false
        val tags = ANY_TAG.findAll(content).map { it.groupValues[1].lowercase() }.toSet()
        if (tags.any { it != "br" }) return false
        val text = content.replace(BR, "\n")
        return MARKS.any { it.containsMatchIn(text) }
    }

    /** [content] as HTML if it is Markdown, otherwise unchanged. */
    fun htmlIfMarkdown(content: String?): String? =
        if (isMarkdown(content)) toHtml(content!!) else content

    fun toHtml(markdown: String): String {
        val lines = markdown.replace(BR, "\n").replace("\r\n", "\n").split('\n')
        val out = StringBuilder()
        var paragraph = mutableListOf<String>()
        var list: String? = null
        var i = 0

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                out.append("<p>").append(paragraph.joinToString("<br>") { inline(it) }).append("</p>")
                paragraph = mutableListOf()
            }
        }
        fun closeList() {
            if (list != null) {
                out.append("</").append(list).append(">")
                list = null
            }
        }

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flushParagraph(); closeList()
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        code += lines[i]
                        i++
                    }
                    // Kept as it is, spaces and all: ASCII art is the point.
                    out.append("<pre><code>").append(escapeCode(code.joinToString("\n"))).append("</code></pre>")
                }
                trimmed.isEmpty() -> { flushParagraph(); closeList() }
                HEADING.matches(trimmed) -> {
                    flushParagraph(); closeList()
                    val m = HEADING.matchEntire(trimmed)!!
                    val level = m.groupValues[1].length
                    out.append("<h$level>").append(inline(m.groupValues[2])).append("</h$level>")
                }
                trimmed.startsWith(">") -> {
                    flushParagraph(); closeList()
                    out.append("<blockquote><p>").append(inline(trimmed.removePrefix(">").trim()))
                        .append("</p></blockquote>")
                }
                BULLET.matches(trimmed) -> {
                    flushParagraph()
                    if (list != "ul") { closeList(); out.append("<ul>"); list = "ul" }
                    out.append("<li>").append(inline(BULLET.matchEntire(trimmed)!!.groupValues[1])).append("</li>")
                }
                NUMBERED.matches(trimmed) -> {
                    flushParagraph()
                    if (list != "ol") { closeList(); out.append("<ol>"); list = "ol" }
                    out.append("<li>").append(inline(NUMBERED.matchEntire(trimmed)!!.groupValues[1])).append("</li>")
                }
                else -> { closeList(); paragraph += trimmed }
            }
            i++
        }
        flushParagraph(); closeList()
        return out.toString()
    }

    private val HEADING = Regex("""(#{1,6})\s+(.+?)\s*#*""")
    private val BULLET = Regex("""[-*+]\s+(.+)""")
    private val NUMBERED = Regex("""\d{1,3}[.)]\s+(.+)""")

    private val IMAGE = Regex("""!\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
    private val LINK = Regex("""\[([^\]]+)]\(([^)\s]+)(?:\s+"[^"]*")?\)""")
    private val BOLD = Regex("""\*\*([^*\n]+?)\*\*""")
    private val ITALIC = Regex("""(?<![*\w])\*([^*\n]+?)\*(?![*\w])""")
    private val CODE = Regex("""`([^`\n]+)`""")

    /**
     * One line's inline marks. The text is already HTML-escaped by the feed
     * (it arrives with `&apos;` and the like), so it is not escaped again;
     * only a stray `<` that would start a tag is, which the check above has
     * already ruled out apart from `<br>`.
     */
    private fun inline(text: String): String {
        val codes = mutableListOf<String>()
        var s = CODE.replace(text) { codes += it.groupValues[1]; "\u0000${codes.size - 1}\u0000" }
        s = IMAGE.replace(s) { m ->
            val src = safeUrl(m.groupValues[2]) ?: return@replace m.value
            """<img src="$src" alt="${attr(m.groupValues[1])}">"""
        }
        s = LINK.replace(s) { m ->
            val href = safeUrl(m.groupValues[2]) ?: return@replace m.value
            """<a href="$href">${m.groupValues[1]}</a>"""
        }
        s = BOLD.replace(s) { "<strong>${it.groupValues[1]}</strong>" }
        s = ITALIC.replace(s) { "<em>${it.groupValues[1]}</em>" }
        return Regex("\u0000(\\d+)\u0000").replace(s) { "<code>${escapeCode(codes[it.groupValues[1].toInt()])}</code>" }
    }

    /** Web and relative addresses only: no `javascript:` or `data:` from a feed. */
    private fun safeUrl(url: String): String? {
        val u = url.trim()
        val scheme = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*):""").find(u)?.groupValues?.get(1)?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https" && scheme != "mailto") return null
        return attr(u)
    }

    private fun attr(s: String) = s.replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    /** Code keeps entities the feed already wrote; only bare `<` and `>` are made safe. */
    private fun escapeCode(s: String) = s.replace("<", "&lt;").replace(">", "&gt;")
}
