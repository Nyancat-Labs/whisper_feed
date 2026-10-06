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

import com.saulhdev.feeder.manager.backup.BackupFolder
import com.saulhdev.feeder.manager.backup.makeRoomFor
import com.saulhdev.feeder.manager.backup.previousName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 4 October: a backup made, the app reinstalled, the same folder chosen in the
 * new install - and the backup replaced by the empty app's within a second,
 * because choosing a folder wrote to it at once and nothing was kept.
 */
class BackupOverwriteTest {

    /** A folder as a map of names to contents. */
    private class Folder(
        vararg files: Pair<String, String>,
        val canRename: Boolean = true,
    ) : BackupFolder {
        val files = linkedMapOf(*files)
        override fun exists(name: String) = name in files
        override fun delete(name: String) {
            files.remove(name)
        }
        override fun rename(from: String, to: String): Boolean {
            if (!canRename) return false
            files[to] = files.remove(from) ?: return false
            return true
        }
    }

    @Test
    fun `the copy a backup replaces is kept beside it`() {
        val folder = Folder("whisper-subscriptions.opml" to "126 feeds")
        makeRoomFor(folder, "whisper-subscriptions.opml")
        assertEquals(mapOf("whisper-subscriptions.previous.opml" to "126 feeds"), folder.files)
    }

    @Test
    fun `one copy back, not a series`() {
        val folder = Folder(
            "whisper-settings.json" to "yesterday",
            "whisper-settings.previous.json" to "the day before",
        )
        makeRoomFor(folder, "whisper-settings.json")
        assertEquals(mapOf("whisper-settings.previous.json" to "yesterday"), folder.files)
    }

    @Test
    fun `an empty folder is left alone`() {
        val folder = Folder("whisper-subscriptions.previous.opml" to "older")
        makeRoomFor(folder, "whisper-subscriptions.opml")
        assertEquals(mapOf("whisper-subscriptions.previous.opml" to "older"), folder.files)
    }

    @Test
    fun `a folder that cannot rename still makes room`() {
        val folder = Folder("whisper-subscriptions.opml" to "126 feeds", canRename = false)
        makeRoomFor(folder, "whisper-subscriptions.opml")
        assertTrue(folder.files.isEmpty())
    }

    @Test
    fun `the previous name keeps the extension last`() {
        assertEquals("whisper-subscriptions.previous.opml", previousName("whisper-subscriptions.opml"))
        assertEquals("whisper-settings.previous.json", previousName("whisper-settings.json"))
        assertEquals("backup.previous", previousName("backup"))
    }

    @Test
    fun `choosing a folder with a backup in it asks before writing`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt").readText()
        val chooser = page.substringAfter("val chooseFolder = rememberLauncherForActivityResult(")
            .substringBefore("val chooseSettings")
        assertTrue(chooser, chooser.contains("store.hasBackup(uri)"))
        assertTrue(chooser, chooser.indexOf("store.hasBackup(uri)") < chooser.indexOf("useFolder(uri)"))
        assertTrue("the chooser itself writes nothing", !chooser.contains("store.backUp("))
        // Restore reads before the folder becomes the destination.
        val dialog = page.substringAfter("existing?.let { uri ->").substringBefore("ViewWithActionBar(")
        assertTrue(dialog.indexOf("store.restoreFolder(uri)") < dialog.indexOf("BackupWorker.schedule"))
    }

    @Test
    fun `both backup files keep their previous copy`() {
        val store = File("src/main/java/com/saulhdev/feeder/manager/backup/BackupStore.kt").readText()
        assertEquals(2, Regex("makeRoomFor\\(DocumentBackupFolder\\(tree\\), ").findAll(store).count())
        assertTrue(!store.contains("tree.findFile(FILE_NAME)?.delete()"))
        assertTrue(!store.contains("tree.findFile(SettingsBackup.FILE_NAME)?.delete()"))
    }
}
