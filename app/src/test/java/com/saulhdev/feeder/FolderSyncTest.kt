package com.saulhdev.feeder

import com.saulhdev.feeder.manager.sync.greader.planFolders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Review 59: a category changed on the phone came back after the next sync. */
class FolderSyncTest {

    @Test
    fun `a category changed on the phone is sent, not undone`() {
        val plan = planFolders(local = listOf("Tech"), server = listOf("Test"), last = setOf("test"))
        assertNull("Whisper keeps the reader's choice", plan.local)
        assertEquals(setOf("Test"), plan.removeOnServer)
        assertEquals(setOf("Tech"), plan.addOnServer)
        assertEquals(setOf("tech"), plan.agreed)
    }

    @Test
    fun `a category changed on the server comes to the phone`() {
        val plan = planFolders(local = listOf("Test"), server = listOf("Tech"), last = setOf("test"))
        assertEquals(listOf("Tech"), plan.local)
        assertTrue(plan.addOnServer.isEmpty() && plan.removeOnServer.isEmpty())
    }

    @Test
    fun `changed on both sides, the phone wins`() {
        val plan = planFolders(local = listOf("News"), server = listOf("Tech"), last = setOf("test"))
        assertNull(plan.local)
        assertEquals(setOf("News"), plan.addOnServer)
        assertEquals(setOf("Tech"), plan.removeOnServer)
    }

    @Test
    fun `a category deleted on the phone is removed on the server`() {
        val plan = planFolders(local = emptyList(), server = listOf("Test"), last = setOf("test"))
        assertEquals(setOf("Test"), plan.removeOnServer)
        assertTrue(plan.addOnServer.isEmpty())
        assertTrue(plan.agreed.isEmpty())
    }

    @Test
    fun `first sight takes the server's, unless it has none`() {
        assertEquals(listOf("Test"), planFolders(listOf("Tech"), listOf("Test"), last = null).local)
        val sent = planFolders(listOf("Tech"), emptyList(), last = null)
        assertNull(sent.local)
        assertEquals(setOf("Tech"), sent.addOnServer)
    }

    @Test
    fun `spelling and order are not a change`() {
        val plan = planFolders(listOf("Tech ", "news"), listOf("News", "tech"), last = null)
        assertNull(plan.local)
        assertTrue(plan.addOnServer.isEmpty() && plan.removeOnServer.isEmpty())
    }

    @Test
    fun `the sync removes before it adds, and remembers only what was sent`() {
        val service = File("src/main/java/com/saulhdev/feeder/manager/sync/service/GoogleReaderService.kt").readText()
        val removes = service.indexOf("plan.removeOnServer.all")
        val adds = service.indexOf("plan.addOnServer.all")
        assertTrue("FreshRSS keeps one folder: remove first", removes in 0 until adds)
        assertTrue(service.contains("sent -> nextFolders[key] = plan.agreed"))
        assertTrue(service.contains("GoogleReaderState.setFolders(context, nextFolders)"))
    }
}
