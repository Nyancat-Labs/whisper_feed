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
package com.saulhdev.feeder.viewmodels

/**
 * How far back "Mark all as read" reaches, by the date the article carries.
 *
 * By publication date rather than by when it was fetched: "older than an hour"
 * means what the card says, and the card shows the article's own age.
 */
enum class MarkReadRange(private val olderThanMs: Long?) {
    Everything(null),
    OlderThanHour(60 * 60 * 1000L),
    OlderThanDay(24 * 60 * 60 * 1000L),
    OlderThanTwoDays(48 * 60 * 60 * 1000L);

    /**
     * The cut-off: articles dated before this are marked. Everything has none,
     * which includes an article a site dated in the future.
     */
    fun before(nowMs: Long): Long = olderThanMs?.let { nowMs - it } ?: Long.MAX_VALUE
}
