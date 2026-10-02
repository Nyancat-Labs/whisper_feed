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
package com.saulhdev.feeder.utils

/**
 * Where a sync had got to, for a run that is stopped before it can say.
 *
 * A finished sync writes how long each step took. One that is stopped - by
 * Android, or by its own eight minutes - wrote nothing about its steps, so a
 * run that used all eight minutes on mobile data left no clue where they went.
 * This is kept by the worker and filled by the service as it goes; the
 * worker reads it when the run is stopped.
 */
class StepTrace(private val clock: () -> Long = System::currentTimeMillis) {

    private val lock = Any()
    private val done = mutableListOf<Pair<String, Long>>()
    private var current: Pair<String, Long>? = null

    fun started(step: String) = synchronized(lock) { current = step to clock() }

    fun finished(step: String, ms: Long) = synchronized(lock) {
        done += step to ms
        if (current?.first == step) current = null
    }

    /**
     * "reached sign-in 0.5s, feeds 41s; stopped in matching after 380s", or
     * null when nothing was traced.
     */
    fun describe(): String? = synchronized(lock) {
        val reached = done.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = "reached ") { (step, ms) -> "$step ${seconds(ms)}" }
        val inStep = current?.let { (step, since) -> "stopped in $step after ${seconds(clock() - since)}" }
        listOfNotNull(reached, inStep).joinToString("; ").ifEmpty { null }
    }
}
