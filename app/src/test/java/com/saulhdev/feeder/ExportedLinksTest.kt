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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No deep link takes an address.
 *
 * MainActivity is exported, as the launcher entry must be, and every intent
 * it receives goes to navigation. So each deep link is something any installed
 * app can call. Opening a fixed screen, or an article by its id, is harmless;
 * opening an address of the caller's choosing inside Whisper's own browser
 * was a page that looked like part of the app, JavaScript on.
 */
class ExportedLinksTest {

    private val navigation = File("src/main/java/com/saulhdev/feeder/ui/navigation/NavigationManager.kt")

    private fun patterns(): List<String> =
        Regex("""uriPattern\s*=\s*"([^"]*)"""").findAll(navigation.readText())
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `the deep links are actually being read`() {
        // Settings, broken feeds, main and the article: a path mistake would
        // make the check below pass on nothing.
        assertTrue(patterns().toString(), patterns().size >= 4)
    }

    @Test
    fun `no deep link carries an address`() {
        val carrying = patterns().filter {
            Regex("""\{(url|link|href|address|uri)\}""", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }
        assertEquals("these deep links take an address: $carrying", emptyList<String>(), carrying)
    }

    @Test
    fun `the in-app browser is not reachable from outside`() {
        val source = navigation.readText()
        val webView = source.substring(source.indexOf("composable<NavRoute.WebView>"))
            .substringBefore("composable<NavRoute.ArticleView>")
        assertTrue(webView, !webView.contains("deepLinks"))
    }
}
