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
package com.saulhdev.feeder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.saulhdev.feeder.utils.withLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * An article's links, now that each is its own span rather than one
 * tappable paragraph: a tap on a link opens that link's address, through
 * the same onLinkClick as before, and the text looks as it did.
 */
class ArticleLinksTest {

    private val paragraph = buildAnnotatedString {
        append("Read ")
        pushStringAnnotation("URL", "https://example.org/one")
        withStyle(SpanStyle(color = Color.Blue)) { append("the first") }
        pop()
        append(" or ")
        pushStringAnnotation("URL", "https://example.org/two")
        append("the second")
        pop()
        append(".")
    }

    @Test
    fun `each link opens its own address`() {
        val opened = mutableListOf<String>()
        val linked = withLinks(paragraph) { opened += it }
        val links = linked.getLinkAnnotations(0, linked.length)
        assertEquals(listOf("the first", "the second"), links.map { linked.text.substring(it.start, it.end) })
        links.forEach { range ->
            val link = range.item as LinkAnnotation.Clickable
            link.linkInteractionListener!!.onClick(link)
        }
        assertEquals(listOf("https://example.org/one", "https://example.org/two"), opened)
    }

    @Test
    fun `the text and its styling are unchanged`() {
        val linked = withLinks(paragraph) {}
        assertEquals(paragraph.text, linked.text)
        assertEquals(paragraph.spanStyles, linked.spanStyles)
    }

    @Test
    fun `a paragraph without links is left as it is`() {
        val plain = buildAnnotatedString { append("No links here.") }
        assertSame(plain, withLinks(plain) {})
    }
}
