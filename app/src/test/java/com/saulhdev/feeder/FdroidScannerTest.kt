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
 * 5 October: F-Droid built 1.0.1 and then failed its APK scan, reading the
 * launcher panel's classes, under com.google.android.libraries.gsa, and the
 * data-binding class that module's namespace generated, as Google's
 * proprietary library. They are open code inherited from Neo Feed; only the
 * name was Google's. They live under Whisper's own name now.
 *
 * The Binder interfaces in launcherclient keep Google's name on purpose: the
 * descriptor strings in them are the protocol Lawnchair speaks.
 */
class FdroidScannerTest {

    private val module = File("../google-gsa")

    @Test
    fun `no class of ours sits in the package F-Droid reads as Google's library`() {
        val offenders = File(module, "src/main/java").walkTopDown()
            .filter { it.isFile && it.path.replace('\\', '/').contains("com/google/android/libraries/gsa") }
            .toList()
        assertEquals(emptyList<File>(), offenders)
        File(module, "src/main/java/com/saulhdev/feeder/launcherpanel").listFiles().orEmpty().forEach {
            assertTrue(it.name, it.readText().contains("package com.saulhdev.feeder.launcherpanel"))
        }
    }

    @Test
    fun `the module generates nothing under Google's name`() {
        val gradle = File(module, "build.gradle.kts").readText()
        assertTrue(gradle.contains("namespace = \"com.saulhdev.feeder.launcherpanel\""))
        assertTrue(gradle.contains("dataBinding = false"))
    }

    @Test
    fun `the shrinker keeps the panel under its new name`() {
        val rules = File("proguard-rules.pro").readText()
        assertTrue(rules.contains("-keep class com.saulhdev.feeder.launcherpanel.** { *; }"))
        assertFalse(rules.contains("gsa.d.a"))
    }
}
