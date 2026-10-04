package com.saulhdev.feeder

import com.saulhdev.feeder.utils.blobFile
import com.saulhdev.feeder.utils.blobFullFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An article's cached text cannot be written outside the app's files.
 *
 * The file name is built by interpolating the article's id, and nothing at
 * that line said the id was safe to interpolate. Every id is a
 * `UUID.randomUUID().toString()` — generated locally, never taken from a feed,
 * and not the publisher's guid, which is a string somebody else wrote. That
 * was traced through the sync and the Google Reader client before the guard
 * was added, and it held.
 *
 * The guard exists because the invariant lived in a different file from the
 * code depending on it, which is the arrangement that stops being true without
 * anyone noticing. An id containing `../` would put an article's text into the
 * databases directory or over the shared preferences.
 */
class BlobPathTest {

    private val dir = File("/data/data/com.nyancatlabs.whisper/files")

    @Test
    fun `a real article id is unchanged`() {
        val id = "3f2b7c18-9a41-4e2d-8b55-0c1d2e3f4a5b"
        assertEquals("$id.txt.gz", blobFile(id, dir).name)
        assertEquals("$id.full.html.gz", blobFullFile(id, dir).name)
        assertEquals(dir, blobFile(id, dir).parentFile)
    }

    @Test
    fun `an id that would climb out of the directory is refused`() {
        listOf(
            "../databases/whisper",
            "../../shared_prefs/whisper_account",
            "..",
        ).forEach { id ->
            assertThrows("$id was accepted as a file name", IllegalArgumentException::class.java) {
                blobFile(id, dir)
            }
            assertThrows(IllegalArgumentException::class.java) { blobFullFile(id, dir) }
        }
    }

    @Test
    fun `an id carrying a path separator is refused`() {
        listOf("a/b", "a\\b", "/etc/passwd").forEach { id ->
            assertThrows("$id was accepted as a file name", IllegalArgumentException::class.java) {
                blobFile(id, dir)
            }
        }
    }

    @Test
    fun `an empty id is refused`() {
        // Not traversal, but it names the directory's own dotfile rather than
        // an article, and two empty ids are the same file.
        assertThrows(IllegalArgumentException::class.java) { blobFile("", dir) }
    }

    @Test
    fun `the check refuses rather than sanitises`() {
        // Stripping the offending characters would map two different ids onto
        // one file, so an article could be served another article's text.
        // This app has already had that bug for different reasons, and it took
        // two builds to find.
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            blobFile("../../a", dir)
        }
        assertTrue(
            "the refusal names the article id it was given: ${thrown.message}",
            thrown.message.orEmpty().contains("id", ignoreCase = true) &&
                    !thrown.message.orEmpty().contains(".."),
        )
    }
}
