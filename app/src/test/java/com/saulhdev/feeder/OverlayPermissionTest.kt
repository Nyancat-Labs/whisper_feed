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

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.utils.openOverlaySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The launcher page's permission buttons open a screen that exists, or none,
 * and never throw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OverlayPermissionTest {

    @Test
    fun `a phone without the permission screen does not crash the app`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // As on a phone with neither screen: starting either throws.
        shadowOf(app).checkActivities(true)
        assertFalse(openOverlaySettings(app))
    }

    @Test
    fun `nothing starts the permission screen except the guarded helper`() {
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "OverlayPermission.kt" }
            .filter { it.readText().contains("ACTION_MANAGE_OVERLAY_PERMISSION") }
            .map { it.path }
            .toList()
        assertEquals("these start it unguarded: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `only the launcher page asks about the overlay permission`() {
        // Only the launcher panel needs it. A prompt anywhere else tells every
        // reader something is broken when nothing is. Diagnostics may read it.
        val allowed = setOf("LauncherPage.kt", "Diagnostics.kt", "OverlayPermission.kt")
        val offenders = File("src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .filter { f -> f.readText().let { "canDrawOverlays" in it || "openOverlaySettings(" in it } }
            .map { it.path }
            .toList()
        assertEquals("these ask outside the launcher page: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `the unused permission dialog is gone`() {
        assertFalse(File("src/main/java/com/saulhdev/feeder/ui/components/dialog/PermissionDialog.kt").exists())
    }
}
