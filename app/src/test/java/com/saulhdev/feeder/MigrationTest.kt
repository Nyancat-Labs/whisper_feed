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
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.db.allMigrations
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Every exported version of the database upgrades to the current one, and
 * keeps its rows on the way.
 *
 * Each old database is built from its exported schema, the statements Room
 * itself wrote down for that version, and then opened with Room exactly as
 * the app opens it. Room runs the migrations and checks where they land: the
 * schema must be exactly the current one, or it throws, and on a phone that is
 * a crash at the update for everybody who had the version before.
 * scripts/check_migrations.py replays the hand-written SQL from version 7;
 * this runs the real thing from version 3, including the four automatic
 * migrations and the Kotlin that moves rows between tables, in the ordinary
 * test suite.
 *
 * Room's own MigrationTestHelper does the same, but reads the schemas from
 * assets, and a unit test here only sees the debug build's assets: the
 * schemas would have had to ship inside the debug app to be found.
 *
 * Each old database is seeded with two rows in every table, one with every
 * column filled and one with every optional column empty, since a migration
 * that copies a table is exactly where an empty value turns into a lost row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {

    private val current = 25
    private val first = 3
    private val schemas = File("schemas/${NeoFeedDb::class.java.name}")

    @get:Rule
    val folder = TemporaryFolder()

    private fun database(version: Int): JsonObject =
        Json.parseToJsonElement(File(schemas, "$version.json").readText())
            .jsonObject["database"]!!.jsonObject

    /**
     * The database as it stood at [version]: its tables, indices and views
     * from the schema's own statements, Room's bookkeeping from its setup
     * queries, and the version number, which is what tells Room to upgrade.
     */
    private fun createAt(version: Int, file: File, seed: (SQLiteDatabase) -> Unit) {
        val database = database(version)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            database["entities"]!!.jsonArray.map { it.jsonObject }.forEach { entity ->
                val table = entity.string("tableName")
                db.execSQL(entity.string("createSql").replace("\${TABLE_NAME}", table))
                entity["indices"]?.jsonArray.orEmpty().forEach {
                    db.execSQL(it.jsonObject.string("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            database["views"]?.jsonArray.orEmpty().map { it.jsonObject }.forEach { view ->
                db.execSQL(view.string("createSql").replace("\${VIEW_NAME}", view.string("viewName")))
            }
            database["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
            seed(db)
            db.version = version
        }
    }

    private class Table(val name: String, val fields: List<Field>, val parents: Map<String, String>)
    private class Field(val name: String, val affinity: String, val notNull: Boolean)

    /** The tables at [version], parents before the tables that point at them. */
    private fun tables(version: Int): List<Table> {
        val tables = database(version)["entities"]!!.jsonArray.map { it.jsonObject }.map { entity ->
            Table(
                name = entity.string("tableName"),
                fields = entity["fields"]!!.jsonArray.map { it.jsonObject }.map {
                    Field(
                        name = it.string("columnName"),
                        affinity = it.string("affinity"),
                        notNull = it["notNull"]?.jsonPrimitive?.boolean ?: false,
                    )
                },
                // column -> the table it refers to
                parents = entity["foreignKeys"]?.jsonArray.orEmpty().map { it.jsonObject }
                    .flatMap { fk ->
                        fk["columns"]!!.jsonArray.map { it.jsonPrimitive.content to fk.string("table") }
                    }
                    .toMap(),
            )
        }
        return tables.sortedBy { it.parents.size }
    }

    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.contentOrNull!!

    /**
     * Two rows. The first fills every column; the second leaves empty every
     * column that may be. Keys are the row's number, and a column pointing at
     * another table points at that table's first row.
     */
    private fun seed(db: SQLiteDatabase, table: Table) {
        for (row in 1..2) {
            val values = ContentValues()
            table.fields.forEach { field ->
                if (row == 2 && !field.notNull) {
                    values.putNull(field.name)
                    return@forEach
                }
                val parent = table.parents[field.name]
                when (field.affinity) {
                    "INTEGER" -> values.put(field.name, if (parent != null) 1L else row.toLong())
                    "REAL" -> values.put(field.name, row + 0.5)
                    "BLOB" -> values.put(field.name, byteArrayOf(row.toByte()))
                    else -> values.put(field.name, textFor(field.name, row, parent))
                }
            }
            db.insertOrThrow(table.name, null, values)
        }
    }

    /** Text a real row could hold, which for some columns means an address. */
    private fun textFor(column: String, row: Int, parent: String?): String = when {
        parent != null -> "seed-$parent-1"
        column.contains("url", ignoreCase = true) || column.contains("link", ignoreCase = true) ||
            column == "feedImage" -> "https://example.org/$column/$row"
        column == "pubDate" -> "2026-09-2${row}T10:00:00Z"
        else -> "seed-$column-$row"
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun `the schemas are actually being read`() {
        assertTrue(schemas.path, File(schemas, "$current.json").isFile)
        assertEquals(listOf("Feeds", "FeedArticle"), tables(first).map { it.name })
    }

    @Test
    fun `every exported version upgrades to the current one with its rows`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val failures = mutableListOf<String>()
        for (from in first until current) {
            val file = folder.root.resolve("upgrade-from-$from.db")
            val seeded = tables(from)
            runCatching {
                createAt(from, file) { db -> seeded.forEach { seed(db, it) } }
                // Built as NeoFeedDb.buildDatabase builds it. Opening it is
                // what migrates and validates, as on the phone.
                val room = Room.databaseBuilder(context, NeoFeedDb::class.java, file.absolutePath)
                    .addMigrations(*allMigrations)
                    .allowMainThreadQueries()
                    .build()
                try {
                    val db = room.openHelper.writableDatabase
                    val names = seeded.map { it.name }
                    // What each surviving table should still hold. Version 3
                    // kept its articles in FeedArticle, and the move to
                    // Article is one of the things being tested.
                    val expected = buildMap {
                        put("Feeds", 2)
                        if ("Article" in names || "FeedArticle" in names) put("Article", 2)
                        if ("Suggestion" in names) put("Suggestion", 2)
                        if ("ReadingTally" in names) put("ReadingTally", 2)
                    }
                    expected.forEach { (table, rows) ->
                        val found = count(db, table)
                        if (found != rows) failures += "from $from: $table has $found rows, expected $rows"
                    }
                } finally {
                    room.close()
                }
            }.onFailure { failures += "from $from: ${it.javaClass.simpleName}: ${it.message?.take(300)}" }
        }
        assertEquals(failures.joinToString("\n"), emptyList<String>(), failures)
    }
}
