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
package com.saulhdev.feeder.ui.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import com.saulhdev.feeder.data.content.FeedPreferences
import org.koin.java.KoinJavaComponent.inject

private const val FOCUS_TAG = "FocusTrace"

/**
 * One line when a field takes or loses keyboard focus.
 *
 * A diagnostics report showed the keyboard being hidden and shown again
 * twenty milliseconds later, four times in fifteen seconds, while somebody was
 * editing categories. The show cancels the hide before it finishes, so nothing
 * is visible on screen — but something is dropping focus and something is
 * taking it straight back, and reading the source found neither. The field in
 * question is always the first slot of its list, so it is not being re-slotted;
 * the only `clearFocus` calls are ordinary Done actions.
 *
 * Rather than guess at focus handling — which is easy to make worse, and was
 * guessed at twice already this week at the reader's expense — this says which
 * field it was and in which order. The IME lines in the same report carry
 * timestamps, so the two interleave into an account of what happened.
 *
 * Never the field's contents. A category, a search term and a server address
 * are all things somebody typed, this goes into a file they send to a
 * stranger, and the name of the field is the whole of what is needed.
 *
 * `Log.println` rather than `Log.d`, for the reason
 * [com.saulhdev.feeder.ui.overlay.gateLog] gives: proguard-rules.pro strips
 * `Log.d`/`v`/`i`, so a trace written with it exists only in the debug build,
 * which is the one build where this question cannot come up.
 */
fun Modifier.traceFocus(field: String): Modifier = composed {
    val trace = rememberTracingFocus()
    if (!trace) {
        this
    } else {
        onFocusChanged { state ->
            Log.println(
                Log.DEBUG,
                FOCUS_TAG,
                if (state.isFocused) "$field gained focus" else "$field lost focus",
            )
        }
    }
}

/**
 * Whether the reader has debugging switched on.
 *
 * Read from the cache rather than the datastore, and remembered: this is a
 * diagnostic, and blocking composition on a disk read to decide whether to
 * write a log line would cost more than the line is worth.
 */
@Composable
private fun rememberTracingFocus(): Boolean {
    val prefs: FeedPreferences by inject(FeedPreferences::class.java)
    return remember { prefs.debugging.peekOrDefault() }
}
