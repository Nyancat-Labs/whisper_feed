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
package com.saulhdev.feeder.ui.pages

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The changelog and the licence, drawn as ordinary screens.
 *
 * Both used to be HTML pages in a WebView with their own stylesheet: grey,
 * orange links, a blue gradient button, whatever the app's theme. They were
 * also hand-kept copies. The changelog stopped at 1.0.0, and the licence
 * page named Neo Feed's author and pointed at Neo Feed's source, which the
 * GPL asks to be Whisper's. The text now comes from the repository's own
 * CHANGELOG.md and LICENSE, copied in by the build (see copyDocsToAssets).
 */
object Documents {

    const val SOURCE = "https://github.com/Nyancat-Labs/whisper_feed"
    private const val BLOB = "$SOURCE/blob/main/"

    sealed interface Block {
        val text: String
    }

    data class Heading(override val text: String) : Block
    data class Paragraph(override val text: String) : Block
    data class Bullet(override val text: String) : Block

    private val underline = Regex("^(-{3,}|={3,})\\s*$")

    /**
     * Whisper's releases from CHANGELOG.md, newest first: what follows the
     * opening note, up to the rule above Neo Feed's history. Unreleased is
     * kept only where [unreleased] names it (test builds), under that title.
     */
    fun changelog(markdown: String, unreleased: String?): List<Block> {
        val lines = markdown.lines()
        val blocks = mutableListOf<Block>()
        var started = false
        var skipping = false
        val text = StringBuilder()
        var bullet = false

        fun flush() {
            if (text.isNotEmpty() && started && !skipping) {
                val t = text.toString().trim()
                blocks += if (bullet) Bullet(t) else Paragraph(t)
            }
            text.clear()
            bullet = false
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val next = lines.getOrNull(i + 1)
            when {
                // The rule between Whisper's releases and Neo Feed's.
                line.trim() == "---" && started -> break
                line.isNotBlank() && next != null && underline.matches(next) -> {
                    flush()
                    val title = line.trim()
                    if (title == "CHANGELOG") {
                        i += 2
                        continue
                    }
                    started = true
                    skipping = title == "Unreleased" && unreleased == null
                    if (!skipping) blocks += Heading(if (title == "Unreleased") unreleased!! else title)
                    i += 2
                    continue
                }
                line.isBlank() -> flush()
                line.startsWith("- ") -> {
                    flush()
                    bullet = true
                    text.append(line.removePrefix("- ").trim())
                }
                else -> {
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(line.trim())
                }
            }
            i++
        }
        flush()
        return blocks
    }

    /**
     * The GPL as paragraphs that wrap to the screen. LICENSE is hard-wrapped
     * at 77 characters, which on a phone breaks every line in two.
     */
    fun licence(text: String): List<Block> =
        text.split(Regex("\\n\\s*\\n")).flatMap { para ->
            val lines = para.lines().map { it.trim() }.filter { it.isNotEmpty() }
            // "GNU GENERAL PUBLIC LICENSE" sits on the line above its version.
            val first = lines.firstOrNull()
            if (first != null && lines.size > 1 && isLicenceHeading(first)) {
                listOf(Heading(first), Paragraph(lines.drop(1).joinToString(" ")))
            } else {
                val joined = lines.joinToString(" ")
                if (joined.isEmpty()) emptyList()
                else listOf(if (isLicenceHeading(joined)) Heading(joined) else Paragraph(joined))
            }
        }

    private fun isLicenceHeading(p: String): Boolean =
        p.length < 80 && (
            (p == p.uppercase() && p.any { it.isLetter() }) ||
                Regex("^\\d+\\. [A-Z][^.]*\\.$").matches(p) ||
                p == "Preamble" || p == "How to Apply These Terms to Your New Programs"
            )

    /** Where a link in CHANGELOG.md goes; repository paths go to GitHub. */
    fun linkTarget(href: String): String =
        if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:")) href
        else BLOB + href.removePrefix("./")

    private val inline = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|\[(.+?)]\((.+?)\)""")

    /** The few Markdown marks CHANGELOG.md uses, as styled text. */
    fun styled(text: String, link: Color): AnnotatedString = buildAnnotatedString {
        var at = 0
        inline.findAll(text).forEach { m ->
            append(text.substring(at, m.range.first))
            val (bold, italic, code, label, href) = m.destructured
            when {
                bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(bold) }
                italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(italic) }
                code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
                else -> withLink(
                    LinkAnnotation.Url(linkTarget(href), TextLinkStyles(SpanStyle(color = link)))
                ) { append(label.removeSurrounding("`")) }
            }
            at = m.range.last + 1
        }
        append(text.substring(at))
    }
}

@Composable
private fun rememberAsset(name: String): String? {
    val context = LocalContext.current
    val text by produceState<String?>(null, name) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    return text
}

@Composable
private fun DocumentList(title: String, header: List<Documents.Block>, blocks: List<Documents.Block>) {
    val link = MaterialTheme.colorScheme.primary
    ViewWithActionBar(title = title) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding() + 8.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp,
            ),
        ) {
            items(header + blocks) { block ->
                when (block) {
                    is Documents.Heading -> Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(top = 24.dp, bottom = 8.dp)
                            .semantics { heading() },
                    )
                    is Documents.Paragraph -> Text(
                        text = Documents.styled(block.text, link),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    is Documents.Bullet -> Row(Modifier.padding(bottom = 10.dp)) {
                        Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(16.dp))
                        Text(
                            text = Documents.styled(block.text, link),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChangelogPage() {
    val markdown = rememberAsset("CHANGELOG.md")
    // Test builds show what is coming; a release shows only what it has.
    val unreleased = if (BuildConfig.DEV_TOOLS) stringResource(R.string.changelog_unreleased) else null
    val blocks = markdown?.let { Documents.changelog(it, unreleased) }.orEmpty()
    DocumentList(stringResource(R.string.about_changelog), emptyList(), blocks)
}

@Composable
fun LicensePage() {
    val text = rememberAsset("LICENSE")
    val header = listOf(
        Documents.Paragraph(stringResource(R.string.licence_copyright)),
        Documents.Paragraph(stringResource(R.string.licence_fork)),
        Documents.Paragraph(stringResource(R.string.licence_source, Documents.SOURCE)),
    )
    DocumentList(stringResource(R.string.about_licenses), header, text?.let { Documents.licence(it) }.orEmpty())
}
