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
package com.saulhdev.feeder.data.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember

/**
 * A preference as Compose state, without blocking to get the first value.
 *
 * Fourteen screens wrote this out by hand as
 * `pref.get().collectAsState(initial = remember { pref.getValue() })`, and that
 * `remember` was itself a fix for something worse: without it, the initial
 * argument — a blocking read — was re-evaluated on every recomposition.
 *
 * Even remembered it blocks once per screen, during the first composition,
 * which is the frame with the least room to spare. `peekOrDefault` answers
 * from the cache that was filled at startup, before any screen existed, so the
 * first frame gets the real value and nothing waits for it.
 *
 * One helper rather than fourteen hand-written lines also means the next
 * screen cannot get it subtly wrong, which is how the un-remembered version
 * survived as long as it did.
 */
@Composable
fun <T> PrefDelegate<T>.asState(): State<T> =
    get().collectAsState(initial = remember(this) { peekOrDefault() })
