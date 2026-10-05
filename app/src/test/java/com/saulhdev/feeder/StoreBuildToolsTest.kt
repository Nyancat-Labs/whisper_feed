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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 5 October, before 1.0.1: the store builds keep the detailed-logs switch, so
 * a reader reporting a problem can be asked to turn it on, under a name that
 * says what it is for; the developer's test notice stays in the test builds.
 */
class StoreBuildToolsTest {

    private val gradle = File("build.gradle.kts").readText()

    @Test
    fun `developer tools are off unless a build type turns them on`() {
        val defaults = gradle.substringAfter("defaultConfig {").substringBefore("\n    }\n")
        assertTrue(defaults.contains("""buildConfigField("boolean", "DEV_TOOLS", "false")"""))
        val release = gradle.substringAfter("        release {").substringBefore("\n        }\n")
        assertFalse("release turns nothing on", release.contains("DEV_TOOLS"))
        assertEquals("debug and preview turn them on", 2, Regex(""""DEV_TOOLS", "true"""").findAll(gradle).count())
    }

    @Test
    fun `the test notice is listed only in a build with developer tools`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/PreferencesPage.kt").readText()
        assertTrue(page.contains("prefs.testSyncNotice.takeIf { debugging && BuildConfig.DEV_TOOLS }"))
    }

    @Test
    fun `the logs switch is named for what it is for`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains(""""pref_detailed_logs">Detailed logs for diagnostics<"""))
        assertFalse("no logcat in a reader's settings", strings.contains("logcat printing"))
    }
}
