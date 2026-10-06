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
package com.saulhdev.feeder.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the reader has asked the system for less movement.
 *
 * Android has no "reduce motion" switch of its own the way iOS does; what it
 * has is the developer-options animation scale, which accessibility guidance
 * treats as the signal — set it to zero and the system itself stops animating.
 * Somebody who has turned that off has said something, and an app that keeps
 * sliding things around anyway is ignoring it.
 *
 * Read once per composition rather than per frame: it is a Settings lookup,
 * and it does not change while a screen is open without the process being
 * restarted for other reasons anyway.
 */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.animationsDisabled() }
}

/** The same question, outside a composition. */
fun Context.animationsDisabled(): Boolean =
    Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    ) == 0f
