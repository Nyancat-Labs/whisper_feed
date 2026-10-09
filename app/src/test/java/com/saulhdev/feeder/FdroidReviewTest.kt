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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What F-Droid's review of merge request !51197 asked for, 8 October 2026. */
class FdroidReviewTest {

    @Test
    fun `no multidex library, which minSdk 26 does not need`() {
        assertFalse(File("build.gradle.kts").readText().contains("multidex"))
        assertFalse(File("../gradle/libs.versions.toml").readText().contains("multidex"))
        assertFalse(File("src/main/java/com/saulhdev/feeder/NeoApp.kt").readText().contains("MultiDexApplication"))
    }

    @Test
    fun `the store description names the weather service`() {
        val description = File("../fastlane/metadata/android/en-US/full_description.txt").readText()
        val weather = description.lines().single { it.contains("Weather in the glance row") }
        assertTrue(weather, weather.contains("Open-Meteo"))
    }
}
