/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.saulhdev.feeder.data.db.dao.FeedArticleDao
import com.saulhdev.feeder.data.db.dao.ReadingTallyDao
import com.saulhdev.feeder.data.db.dao.SuggestionDao
import com.saulhdev.feeder.data.db.models.ReadingTally
import com.saulhdev.feeder.data.db.models.Suggestion
import com.saulhdev.feeder.data.db.dao.FeedSourceDao
import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.db.models.ArticleIdWithLink
import com.saulhdev.feeder.data.db.models.Feed
import org.threeten.bp.ZonedDateTime
import java.util.UUID

const val ID_UNSET: Long = 0
const val ID_ALL: Long = -1L

@Database(
    entities = [
        Feed::class,
        Article::class,
        Suggestion::class,
        ReadingTally::class,
    ],
    version = 26,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
            spec = NeoFeedDb.MigrationMoveToArticle::class
        ),
        AutoMigration(
            from = 4,
            to = 5,
            spec = NeoFeedDb.MigrationRemoveFeedArticle::class
        ),
        AutoMigration(
            from = 5,
            to = 6,
            spec = NeoFeedDb.ReplaceThreetenInstances::class
        ),
        AutoMigration(
            from = 6,
            to = 7,
            spec = NeoFeedDb.RemoveLegacyPubDate::class
        ),
    ],
    views = [
        ArticleIdWithLink::class,
    ],
)
@TypeConverters(Converters::class)
abstract class NeoFeedDb : RoomDatabase() {
    abstract fun feedSourceDao(): FeedSourceDao
    abstract fun feedArticleDao(): FeedArticleDao
    abstract fun suggestionDao(): SuggestionDao

    abstract fun readingTallyDao(): ReadingTallyDao

    companion object {
        /** The database's file name; the diagnostics report measures it. */
        const val NAME = "NeoFeed"

        @Volatile
        private var instance: NeoFeedDb? = null

        fun getInstance(context: Context): NeoFeedDb {
            return instance ?: synchronized(this) {
                instance ?: buildDatabase(context).also { instance = it }
            }
        }

        private fun buildDatabase(context: Context): NeoFeedDb {
            return Room.databaseBuilder(context, NeoFeedDb::class.java, NAME)
                .addMigrations(*allMigrations)
                .build()
        }
    }

    class MigrationMoveToArticle : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            super.onPostMigrate(db)
            val cursor = db.query("SELECT * FROM FeedArticle")
            val insertStmt = db.compileStatement(
                """
                INSERT INTO Article (
                    uuid, guid, title, plainTitle, imageUrl, enclosureLink,
                    plainSnippet, description, author,
                    pubDate, link, feedId, firstSyncedTime, primarySortTime,
                    categories, pinned, bookmarked
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                val guid = cursor.getString(cursor.getColumnIndexOrThrow("guid"))
                val title = cursor.getString(cursor.getColumnIndexOrThrow("title"))
                val plainTitle = cursor.getString(cursor.getColumnIndexOrThrow("plainTitle"))
                val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow("imageUrl"))
                val enclosureLink = cursor.getString(cursor.getColumnIndexOrThrow("enclosureLink"))
                val plainSnippet = cursor.getString(cursor.getColumnIndexOrThrow("plainSnippet"))
                val description = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                val contentHtml = cursor.getString(cursor.getColumnIndexOrThrow("content_html"))
                val author = cursor.getString(cursor.getColumnIndexOrThrow("author"))
                val pubDate = cursor.getLong(cursor.getColumnIndexOrThrow("pubDate"))
                val link = cursor.getString(cursor.getColumnIndexOrThrow("link"))
                val feedId = cursor.getLong(cursor.getColumnIndexOrThrow("feedId"))
                val firstSyncedTime =
                    cursor.getLong(cursor.getColumnIndexOrThrow("firstSyncedTime"))
                val primarySortTime =
                    cursor.getLong(cursor.getColumnIndexOrThrow("primarySortTime"))
                val categoriesString = cursor.getString(cursor.getColumnIndexOrThrow("categories"))
                val pinned = cursor.getInt(cursor.getColumnIndexOrThrow("pinned"))
                val bookmarked = cursor.getInt(cursor.getColumnIndexOrThrow("bookmarked"))

                val uuid = UUID.randomUUID().toString()

                insertStmt.bindString(1, uuid)
                insertStmt.bindString(2, guid)
                insertStmt.bindString(3, title)
                insertStmt.bindString(4, plainTitle)

                if (imageUrl != null)
                    insertStmt.bindString(5, imageUrl)
                else
                    insertStmt.bindNull(5)

                if (enclosureLink != null)
                    insertStmt.bindString(6, enclosureLink)
                else
                    insertStmt.bindNull(6)

                insertStmt.bindString(7, plainSnippet)
                insertStmt.bindString(8, description)

                if (author != null)
                    insertStmt.bindString(9, author)
                else
                    insertStmt.bindNull(9)

                // pubDate can be null, so bind accordingly
                if (pubDate != 0L)
                    insertStmt.bindLong(10, pubDate)
                else
                    insertStmt.bindNull(10)

                if (link != null)
                    insertStmt.bindString(11, link)
                else
                    insertStmt.bindNull(11)

                insertStmt.bindLong(12, feedId)
                insertStmt.bindLong(13, firstSyncedTime)
                insertStmt.bindLong(14, primarySortTime)
                insertStmt.bindString(15, categoriesString)
                insertStmt.bindLong(16, pinned.toLong())
                insertStmt.bindLong(17, bookmarked.toLong())

                insertStmt.executeInsert()
            }
            cursor.close()
        }
    }

    @DeleteTable(tableName = "FeedArticle")
    class MigrationRemoveFeedArticle : AutoMigrationSpec

    class ReplaceThreetenInstances : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            // Copy data from legacy pubDate to pubDateV2 before the column is deleted
            val cursor = db.query("SELECT uuid, pubDate FROM article")
            while (cursor.moveToNext()) {
                val uuid = cursor.getString(0)
                val pubDateStr = cursor.getString(1)

                // Convert pubDateStr to epoch millis for pubDateV2
                val pubDateMillis = convertZDT2Millis(pubDateStr)

                val stmt = db.compileStatement("UPDATE article SET pubDateV2 = ? WHERE uuid = ?")
                stmt.bindLong(1, pubDateMillis)
                stmt.bindString(2, uuid)
                stmt.executeUpdateDelete()
            }
            cursor.close()
        }

        // TODO remove in two releases
        private fun convertZDT2Millis(pubDateStr: String?): Long {
            if (pubDateStr == null) return 0L
            return try {
                val zdt = ZonedDateTime.parse(pubDateStr)
                zdt.toInstant().toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }
    }

    @DeleteColumn(tableName = "Article", columnName = "pubDate")
    class RemoveLegacyPubDate : AutoMigrationSpec
}

val allMigrations = arrayOf(
    MIGRATION_25_26,
    MIGRATION_24_25,
    MIGRATION_23_24,
    MIGRATION_22_23,
    MIGRATION_21_22,
    MIGRATION_20_21,
    MIGRATION_19_20,
    MIGRATION_18_19,
    MIGRATION_17_18,
    MIGRATION_16_17,
    MIGRATION_15_16,
    MIGRATION_14_15,
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
)

@Suppress("ClassName")
object MIGRATION_25_26 : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Matching an account's articles by link; see the index on Article.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Article_link` ON `Article` (`link`)")
    }
}

@Suppress("ClassName")
object MIGRATION_24_25 : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nothing has been dismissed yet, so every existing article is simply
        // one the reader has not told to stop shouting.
        db.execSQL("ALTER TABLE Article ADD COLUMN dismissedAt INTEGER NOT NULL DEFAULT 0")
    }
}

@Suppress("ClassName")
object MIGRATION_23_24 : Migration(23, 24) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nothing is backfilled, and there is nothing to backfill from: the
        // whole reason this table exists is that the articles the old charts
        // counted have been deleted. Reconstructing a history out of the
        // survivors would produce exactly the decay curve this replaces, with
        // the added insult of looking deliberate. The charts start from today
        // and are honest from today.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ReadingTally` (
                `day` TEXT NOT NULL,
                `hour` TEXT NOT NULL,
                `seen` INTEGER NOT NULL DEFAULT 0,
                `opened` INTEGER NOT NULL DEFAULT 0,
                `readMs` INTEGER NOT NULL DEFAULT 0,
                `timed` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`day`, `hour`)
            )
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_22_23 : Migration(22, 23) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Mastodon is gone from the app, so its rows and its columns go too.
        //
        // The sources go first, and their articles by hand rather than by
        // cascade: Room runs migrations with foreign keys switched off, so the
        // `onDelete = CASCADE` on Article.feedId does not fire here and the
        // articles would be orphaned onto a feed id that no longer exists.
        //
        // Nothing is being rescued. A Mastodon source's articles are toots
        // fetched through an API that this version cannot speak to; leaving
        // them would leave a source in the list that can never sync again.
        db.execSQL(
            """
            DELETE FROM Article WHERE feedId IN (
                SELECT id FROM Feeds WHERE sourceType = 'mastodon'
            )
            """.trimIndent()
        )
        db.execSQL("DELETE FROM Feeds WHERE sourceType = 'mastodon'")

        // SQLite before 3.35 has no DROP COLUMN, and the minSdk reaches back
        // further than that, so the four columns go the long way round.
        //
        // The view goes first, and this is not tidiness. A view is stored as
        // text and resolved lazily, so dropping the table underneath one is
        // allowed and silent — but from SQLite 3.25 an ALTER TABLE ... RENAME
        // re-parses every view in the schema to fix up references, and a view
        // pointing at a table that no longer exists stops the rename dead:
        //
        //     error in view ArticleIdWithLink: no such table: main.feeds
        //
        // which fails the migration, rolls it back, and leaves the app
        // crashing on launch for everybody who had the previous version.
        db.execSQL("DROP VIEW IF EXISTS `ArticleIdWithLink`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Feeds_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `url` TEXT NOT NULL,
                `feedImage` TEXT NOT NULL,
                `lastSync` INTEGER NOT NULL,
                `alternateId` INTEGER NOT NULL,
                `fullTextByDefault` INTEGER NOT NULL,
                `tag` TEXT NOT NULL,
                `currentlySyncing` INTEGER NOT NULL,
                `isEnabled` INTEGER NOT NULL,
                `removedAt` INTEGER NOT NULL DEFAULT 0,
                `consecutiveFailures` INTEGER NOT NULL DEFAULT 0,
                `failingSince` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `Feeds_new` (
                `id`, `title`, `description`, `url`, `feedImage`, `lastSync`,
                `alternateId`, `fullTextByDefault`, `tag`, `currentlySyncing`,
                `isEnabled`, `removedAt`, `consecutiveFailures`, `failingSince`
            )
            SELECT
                `id`, `title`, `description`, `url`, `feedImage`, `lastSync`,
                `alternateId`, `fullTextByDefault`, `tag`, `currentlySyncing`,
                `isEnabled`, `removedAt`, `consecutiveFailures`, `failingSince`
            FROM `Feeds`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `Feeds`")
        db.execSQL("ALTER TABLE `Feeds_new` RENAME TO `Feeds`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Feeds_url` ON `Feeds` (`url`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_Feeds_id_url_title` " +
                    "ON `Feeds` (`id`, `url`, `title`)"
        )

        // Put back character for character. Room compares a view's stored SQL
        // against the text it expects and rejects the database on any
        // difference, so this is not a place to tidy the whitespace or the
        // capitalisation of `feeds`. It is the string from the exported
        // schema, and `scripts/check_migrations.py` compares what this
        // produces against that file rather than trusting the comparison to a
        // careful reading.
        db.execSQL(
            "CREATE VIEW `ArticleIdWithLink` AS SELECT Article.uuid, Article.link\n" +
                    "    FROM Article\n" +
                    "    JOIN feeds f ON Article.feedId = f.id\n" +
                    "    WHERE f.fulltextByDefault = 1 OR Article.bookmarked = 1"
        )
    }
}

@Suppress("ClassName")
object MIGRATION_21_22 : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nothing has ever been removed-but-kept, so every existing source is
        // simply a source the reader still has.
        db.execSQL("ALTER TABLE Feeds ADD COLUMN removedAt INTEGER NOT NULL DEFAULT 0")
    }
}

@Suppress("ClassName")
object MIGRATION_20_21 : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Everything already recorded came from link harvesting, which is the
        // default, so the existing rows are correct without being touched.
        db.execSQL("ALTER TABLE Suggestion ADD COLUMN kind TEXT NOT NULL DEFAULT 'links'")
        db.execSQL("ALTER TABLE Suggestion ADD COLUMN context TEXT NOT NULL DEFAULT ''")
    }
}

@Suppress("ClassName")
object MIGRATION_19_20 : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Zero, and it means "never measured" rather than "read for no time".
        // Nothing before this release could have recorded it, and there is no
        // history to reconstruct: the app has never had a clock to reconstruct
        // it from.
        db.execSQL("ALTER TABLE Article ADD COLUMN readMs INTEGER NOT NULL DEFAULT 0")
    }
}

@Suppress("ClassName")
object MIGRATION_18_19 : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Zero everywhere, and honestly so: no install has ever recorded
        // either of these, so there is no history to reconstruct and nothing
        // to guess at. Both start accumulating from the next article opened
        // and the next one looked at.
        db.execSQL("ALTER TABLE Article ADD COLUMN openedAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Article ADD COLUMN dwellMs INTEGER NOT NULL DEFAULT 0")
    }
}

@Suppress("ClassName")
object MIGRATION_17_18 : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // An index and nothing else: no column added, no row rewritten, no
        // behaviour changed. SQLite builds it in one pass over a table that is
        // thousands of rows rather than millions, so the upgrade is not
        // something anyone will see happen.
        //
        // The name has to match what Room generates for the entity, or Room's
        // own schema validation fails on the next open and the app will not
        // start.
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Article_readAt` ON `Article` (`readAt`)")
    }
}

@Suppress("ClassName")
object MIGRATION_16_17 : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Zero for everything: no feed has a failure history because none was
        // ever kept. The count starts building from the next sync.
        db.execSQL("ALTER TABLE Feeds ADD COLUMN consecutiveFailures INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Feeds ADD COLUMN failingSince INTEGER NOT NULL DEFAULT 0")
    }
}

@Suppress("ClassName")
object MIGRATION_15_16 : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Empty on every existing install: a suggestion is evidence about what
        // somebody read, and there is no evidence until a pass has run.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS Suggestion (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                host TEXT NOT NULL,
                feedUrl TEXT NOT NULL,
                title TEXT NOT NULL,
                mentions INTEGER NOT NULL,
                foundAt INTEGER NOT NULL,
                dismissedAt INTEGER NOT NULL
            )
            """
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Suggestion_host ON Suggestion (host)")
    }
}

@Suppress("ClassName")
object MIGRATION_14_15 : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Null for everything that already exists, which is correct: no server
        // has claimed any of these articles yet, and a sync fills it in for
        // the ones a server turns out to know about.
        db.execSQL("ALTER TABLE Article ADD COLUMN remoteId TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_Article_remoteId ON Article (remoteId)")
    }
}

@Suppress("ClassName")
object MIGRATION_13_14 : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Pinning has never been reachable from the interface: the only thing
        // that ever set this column was bookmarking, which set both at once.
        // So every pinned row in an existing database is a saved article
        // rather than a pinned one, and leaving them would put every article
        // the reader has ever saved at the top of the feed the moment pinning
        // starts meaning something.
        db.execSQL("UPDATE Article SET pinned = 0")
    }
}

@Suppress("ClassName")
object MIGRATION_12_13 : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Read state did not exist. Existing articles start unread rather than
        // being guessed at, so the first count after upgrading is honest.
        db.execSQL("ALTER TABLE Article ADD COLUMN readAt INTEGER NOT NULL DEFAULT 0")
    }
}

@Suppress("ClassName")
object MIGRATION_11_12 : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Every feed query orders by primarySortTime, which had no index.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_Article_primarySortTime ON Article (primarySortTime)"
        )
        // Repair rows written before updateFromParsedEntry's primarySortTime was
        // fixed. It referenced the pre-copy() pubDate, which is 0 for a newly
        // parsed article, so every article stored the moment it was synced
        // instead of the moment it was published. Recompute it the way the fixed
        // code does: the earlier of first sync and publication.
        db.execSQL(
            """
            UPDATE Article
            SET primarySortTime = MIN(firstSyncedTime, pubDateV2)
            WHERE pubDateV2 > 0 AND primarySortTime > pubDateV2
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_10_11 : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN excludeReplies INTEGER NOT NULL DEFAULT 1
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_9_10 : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN requireLink INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN requireImage INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE Feeds SET requireLink = 1, requireImage = 1 WHERE sourceType = 'mastodon'
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_7_8 : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // No schema changes between 7 and 8.
    }
}

@Suppress("ClassName")
object MIGRATION_8_9 : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN sourceType TEXT NOT NULL DEFAULT 'rss'
            """.trimIndent()
        )
        db.execSQL(
            """
            UPDATE Feeds SET sourceType = 'mastodon' WHERE url LIKE 'http://mastodon://%'
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_1_2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN fullTextByDefault INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
    }
}

@Suppress("ClassName")
object MIGRATION_2_3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE Feeds ADD COLUMN isEnabled INTEGER NOT NULL DEFAULT 1
            """.trimIndent()
        )
    }
}