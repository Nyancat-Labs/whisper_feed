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

import android.os.StrictMode
import com.saulhdev.feeder.BuildConfig

/**
 * Makes main-thread disk work visible, in debug builds only.
 *
 * The preference reads this exists to police were found by reading fifty call
 * sites and reasoning about each one, which is slow and misses things. StrictMode
 * answers the same question by watching: it names the thread, the file and the
 * stack, and it does so for the next one somebody adds as well as for the ones
 * already there.
 *
 * Logged rather than fatal. A crash on the first violation sounds rigorous and
 * is not: the platform itself trips this — the first WebView load, resource
 * loading, some vendor code in the launcher binding path — so a fatal policy
 * would mean a build nobody can run, which is a build nobody turns on.
 *
 * Debug only, and not because it is expensive. StrictMode's cost is small, but
 * a release build should not be watching itself, and the violations are for
 * whoever is working on the app rather than for the reader.
 */
object MainThreadWatch {

    fun install() {
        if (!BuildConfig.DEBUG) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                // An Activity or a Service held past its death is the leak
                // that shows up as an out-of-memory hours later, a long way
                // from whatever caused it.
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}
