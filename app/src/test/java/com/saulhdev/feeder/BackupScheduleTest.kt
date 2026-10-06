package com.saulhdev.feeder

import com.saulhdev.feeder.manager.backup.backupFolderIsOnDevice
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupScheduleTest {

    @Test
    fun `a folder on the phone is on the device`() {
        assertTrue(backupFolderIsOnDevice("content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FWhisper"))
        assertTrue(backupFolderIsOnDevice("content://com.android.providers.downloads.documents/tree/downloads"))
    }

    @Test
    fun `cloud storage and anything unknown count as remote`() {
        assertFalse(backupFolderIsOnDevice("content://com.google.android.apps.docs.storage/tree/acc%3D1%3Bdoc%3Dabc"))
        assertFalse(backupFolderIsOnDevice("content://com.dropbox.android.document/tree/x"))
        assertFalse(backupFolderIsOnDevice(""))
        assertFalse(backupFolderIsOnDevice("not a uri at all %%"))
    }

    @Test
    fun `no Wi-Fi rule, a connection only for a remote folder, and an old schedule replaced`() {
        val worker = File("src/main/java/com/saulhdev/feeder/manager/backup/BackupWorker.kt").readText()
        assertTrue(worker.contains("if (backupFolderIsOnDevice(folder)) NetworkType.NOT_REQUIRED"))
        assertTrue(worker.contains("else NetworkType.CONNECTED"))
        assertFalse("backups wait for Wi-Fi again", worker.contains("NetworkType.UNMETERED"))
        assertTrue(worker.contains("ExistingPeriodicWorkPolicy.UPDATE"))
        // Restated at start, so a schedule made under the old rule is not
        // left waiting for Wi-Fi until a folder is chosen again.
        val app = File("src/main/java/com/saulhdev/feeder/NeoApp.kt").readText()
        assertTrue(app.contains("if (folder.isNotEmpty()) BackupWorker.schedule(this@NeoApp, folder)"))
    }

    @Test
    fun `the page does not say no folder is chosen once one is`() {
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/BackupPage.kt").readText()
        val at = page.indexOf("text = stringResource(R.string.backup_only_time)")
        assertTrue(at > 0)
        assertTrue(page.substring(page.lastIndexOf("if (folder.isEmpty()) {", at), at).isNotEmpty())
    }
}
