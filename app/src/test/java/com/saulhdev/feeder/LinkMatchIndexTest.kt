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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.db.NeoFeedDb
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An account sync matches up to two thousand of the server's articles to
 * these by link, each with its own query. Each must be a lookup, not a read
 * of the whole table: on 29 September, at thirteen thousand articles, the
 * scans made every sync take eight minutes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LinkMatchIndexTest {

    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        NeoFeedDb::class.java,
    ).allowMainThreadQueries().build()

    @After
    fun close() = db.close()

    private fun plan(sql: String): String =
        db.openHelper.writableDatabase.query("EXPLAIN QUERY PLAN $sql").use { c ->
            buildString { while (c.moveToNext()) appendLine(c.getString(c.getColumnIndexOrThrow("detail"))) }
        }

    @Test
    fun `matching an article by link is a lookup, not a scan`() {
        val plan = plan("UPDATE Article SET remoteId = 'x' WHERE link = 'https://example.org/a' AND remoteId IS NULL")
        assertTrue(plan, plan.contains("USING INDEX index_Article_link"))
        assertTrue(plan, !plan.contains("SCAN Article"))
    }
}
