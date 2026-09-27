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

import com.saulhdev.feeder.data.FeedLibrary
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import javax.xml.validation.Schema

/**
 * Every pack in the library opens with the feeds its index promises, on a
 * parser that behaves as Android's does.
 *
 * The desktop's parser, which the tests run on, lets XInclude be switched
 * off. Android's throws at the question, and from 22 September that throw
 * emptied every pack on a phone while the tests passed. The factory here
 * refuses what Android's refuses, so the next such call fails here first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FeedLibraryPacksTest {

    /** Android's factory, as far as this matters: no XInclude, no unknown features. */
    private class AndroidLikeFactory : SAXParserFactory() {
        private val inner = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        override fun newSAXParser(): SAXParser = inner.newSAXParser()
        override fun setFeature(name: String?, value: Boolean) =
            throw org.xml.sax.SAXNotRecognizedException(name)
        override fun getFeature(name: String?): Boolean = throw org.xml.sax.SAXNotRecognizedException(name)
        override fun setXIncludeAware(state: Boolean) =
            throw UnsupportedOperationException("This SAXParserFactory does not support XInclude")
        override fun setSchema(schema: Schema?) = throw UnsupportedOperationException()
    }

    private val dir = File("src/main/assets/library")

    @Test
    fun `a pack opens on a parser that refuses XInclude`() {
        val feeds = File(dir, "apple.opml").inputStream().use {
            FeedLibrary.parsePack(it, AndroidLikeFactory())
        }
        assertEquals(16, feeds.size)
        assertTrue(feeds.all { it.category == "Apple" && it.url.startsWith("http") })
    }

    @Test
    fun `every pack holds what the index says`() {
        val index = JSONArray(File(dir, "index.json").readText())
        assertTrue("found only ${index.length()} packs", index.length() > 80)
        val wrong = (0 until index.length()).map { index.getJSONObject(it) }.mapNotNull { pack ->
            val slug = pack.getString("slug")
            val found = runCatching {
                File(dir, "$slug.opml").inputStream().use { FeedLibrary.parsePack(it, AndroidLikeFactory()).size }
            }.getOrElse { -1 }
            if (found == pack.getInt("count")) null else "$slug: $found, index says ${pack.getInt("count")}"
        }
        assertEquals(wrong.joinToString("\n"), emptyList<String>(), wrong)
    }
}
