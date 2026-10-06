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

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * A text field that takes focus only when a finger has touched it.
 *
 * Data sources' search field took focus by itself every time the page was
 * rebuilt on the way back from a screen its menu opened, and the keyboard
 * came up over the list. The report was unambiguous: "sources search gained
 * focus" twenty milliseconds after the page appeared, with no tap anywhere
 * near it. Clearing focus on the way out — the previous fix — could not help,
 * because nothing was holding it; Compose handed it to the first field of the
 * rebuilt page.
 *
 * So the field refuses focus until pressed, and goes back to refusing once it
 * lets go. Two ways in stay open regardless: TalkBack, which focuses a field
 * by a double tap that is not a touch on the field, and a hardware keyboard,
 * whose Tab key is the only way there is.
 */
fun Modifier.focusOnlyWhenTapped(): Modifier = composed {
    val context = LocalContext.current
    val inputMode = LocalInputModeManager.current
    val talkBack = remember {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    var pressed by remember { mutableStateOf(false) }
    this
        // Initial pass, so the press is seen before the field's own tap
        // handling asks for focus on the release.
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
            }
        }
        .focusProperties {
            canFocus = pressed || talkBack || inputMode.inputMode == InputMode.Keyboard
        }
        .onFocusChanged { if (!it.isFocused) pressed = false }
}
