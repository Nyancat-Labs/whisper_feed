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

import com.saulhdev.feeder.data.db.dao.SourceArticleCount
import com.saulhdev.feeder.data.db.dao.StorageCounts
import java.io.File
import java.util.Locale

/**
 * The diagnostics report's storage section: what the phone holds, how old it
 * is, and whether the clean-up is keeping up with what comes in.
 *
 * Asked for when the article count went from 12,884 to 19,398 in three days
 * with the sync range at a week, and nothing in the report could say whether
 * the week was still filling after a large import or the clean-up had
 * stopped. Counts, ages, sizes and source titles only.
 */
internal object StorageReport {

    const val LARGEST_SOURCES = 5

    private const val DAY_MS = 24 * 60 * 60_000L

    /** The cut-offs [StorageCounts] is asked with, in the order the query takes them. */
    fun cutoffs(nowMs: Long, rangeDays: Int): List<Long> = listOf(
        nowMs - DAY_MS,
        nowMs - 3 * DAY_MS,
        nowMs - rangeDays * DAY_MS,
        nowMs + DAY_MS,
    )

    fun lines(
        counts: StorageCounts,
        nowMs: Long,
        rangeDays: Int,
        itemsPerFeed: String,
        fullTextForAll: Boolean,
        fullTextOnMobile: Boolean,
        largest: List<SourceArticleCount>,
        disk: DiskUse?,
    ): List<String> = buildList {
        val range = rangeName(rangeDays)
        add("Settings:          keep $range, $itemsPerFeed items per feed per fetch, " +
            "full text for all feeds ${yesNo(fullTextForAll)}, on mobile data ${yesNo(fullTextOnMobile)}")
        add("Articles:          ${counts.total} (unread ${counts.unread}, saved ${counts.saved}, pinned ${counts.pinned})")
        add("By date:           under 1 day ${counts.underDay} · 1-3 days ${counts.dayToThree} · " +
            "3 days to $range ${counts.threeToRange} · older than $range, not saved ${counts.pastRange}")
        add("Oldest kept:       " + (counts.oldest?.let { days(nowMs - it) + " old (not saved or pinned)" } ?: "none"))
        add("Added last 24 h:   ${counts.addedDay}")
        add("Dated ahead:       ${counts.datedAhead} (more than a day in the future; they wait that long to age out)")
        add("Undated:           ${counts.undated}")
        if (largest.isNotEmpty()) {
            add("Most stored:       " + largest.joinToString(", ") { "${it.title.take(24)} ${it.articles}" })
        }
        disk?.let { add("On disk:           ${it.describe()}") }
    }

    fun rangeName(days: Int): String = when (days) {
        1 -> "1 day"
        7 -> "1 week"
        30 -> "1 month"
        else -> "$days days"
    }

    private fun days(ms: Long): String = String.format(Locale.US, "%.1f days", ms / DAY_MS.toDouble())

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}

/** What Whisper keeps on disk: the database, and the article files by kind. */
internal data class DiskUse(
    val databaseBytes: Long,
    val feedTextFiles: Int,
    val feedTextBytes: Long,
    val fullPageFiles: Int,
    val fullPageBytes: Long,
    val otherBytes: Long,
    /** True when [measure] ran out of time and the file counts are short. */
    val partial: Boolean = false,
) {
    fun describe(): String =
        "database ${formatBytes(databaseBytes)}, feed text $feedTextFiles files ${formatBytes(feedTextBytes)}, " +
            "full pages $fullPageFiles files ${formatBytes(fullPageBytes)}, other ${formatBytes(otherBytes)}" +
            if (partial) " (counting stopped early)" else ""

    companion object {
        /**
         * Measured from [databaseFiles] (the database and its journal) and
         * everything under [filesDir], by the names Blob gives article files.
         * Stops at [stopAt], a clock time, and says so: tens of thousands of
         * files on a slow phone must not cost the report its other sections.
         */
        fun measure(databaseFiles: List<File>, filesDir: File, stopAt: Long = Long.MAX_VALUE): DiskUse {
            var textFiles = 0
            var textBytes = 0L
            var fullFiles = 0
            var fullBytes = 0L
            var other = 0L
            var partial = false
            for (f in filesDir.walkTopDown()) {
                if (System.currentTimeMillis() > stopAt) {
                    partial = true
                    break
                }
                if (!f.isFile) continue
                val size = f.length()
                when {
                    f.name.endsWith(".full.html.gz") -> { fullFiles++; fullBytes += size }
                    f.name.endsWith(".txt.gz") -> { textFiles++; textBytes += size }
                    else -> other += size
                }
            }
            return DiskUse(
                databaseBytes = databaseFiles.filter { it.isFile }.sumOf { it.length() },
                feedTextFiles = textFiles,
                feedTextBytes = textBytes,
                fullPageFiles = fullFiles,
                fullPageBytes = fullBytes,
                otherBytes = other,
                partial = partial,
            )
        }
    }
}
