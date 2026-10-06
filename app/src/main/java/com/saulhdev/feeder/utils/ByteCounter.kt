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

import okhttp3.Call
import okhttp3.EventListener
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts the body bytes one request received over the wire.
 *
 * The per-sync figure is everything the app received while the sync ran,
 * which a full-article download running alongside can double. This is one
 * feed's own cost: attached to its request as a tag, and heard by the
 * client's [factory] as the body arrives - compressed, as it was sent, and
 * zero for a 304 or an answer from the cache.
 */
class ByteCounter {
    private val total = AtomicLong(0)
    val bytes: Long get() = total.get()

    private val listener = object : EventListener() {
        override fun responseBodyEnd(call: Call, byteCount: Long) {
            total.addAndGet(byteCount)
        }
    }

    companion object {
        /** For a client builder: requests tagged with a counter report to it. */
        val factory = EventListener.Factory { call ->
            call.request().tag(ByteCounter::class.java)?.listener ?: EventListener.NONE
        }
    }
}
