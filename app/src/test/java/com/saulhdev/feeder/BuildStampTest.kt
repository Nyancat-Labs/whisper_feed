package com.saulhdev.feeder

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A test build says which commit it is.
 *
 * Every preview APK carried the same `1.0.0 (1)`, so two of them were
 * indistinguishable — in a Downloads folder, on a phone, and in a diagnostics
 * report. The reports are the whole of how this app is debugged on a device,
 * and one was read against the wrong build: a set of figures was explained
 * using code that had not been in the build that produced them.
 *
 * Preview and debug carry the commit. Release does not, because a store
 * listing's version is identified by its tag and a hash there is noise to
 * everyone who reads it.
 */
class BuildStampTest {

    private val gradle = File("build.gradle.kts").readText()

    /** One build type's block, from its opening to its closing brace. */
    private fun block(name: String): String {
        val opener = Regex("""(?:^|\n)\s*(?:create\("$name"\)|$name)\s*\{""")
            .find(gradle)
        assertTrue("the $name build type is gone", opener != null)
        var depth = 0
        var i = gradle.indexOf('{', opener!!.range.first)
        val start = i
        while (i < gradle.length) {
            when (gradle[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            i++
        }
        return gradle.substring(start, minOf(i + 1, gradle.length))
    }

    @Test
    fun `preview and debug name their commit`() {
        listOf("preview", "debug").forEach { type ->
            assertTrue(
                "a $type build no longer says which commit it came from",
                block(type).contains("versionNameSuffix"),
            )
        }
    }

    @Test
    fun `release does not`() {
        assertTrue(
            "a release build stamps a commit hash into the version people see",
            !block("release").contains("versionNameSuffix"),
        )
    }

    @Test
    fun `a modified tree is marked as one`() {
        // A preview built from a modified tree is not any commit, and one that
        // claimed to be would send somebody reading a diff that never ran.
        assertTrue(
            "a build from an uncommitted tree claims to be a clean commit",
            block("preview").contains("if (gitDirty)"),
        )
    }

    @Test
    fun `a checkout without git still builds`() {
        // A source archive with no .git, a machine without git on its path, or
        // a clone in some state git dislikes. A version string is not worth
        // breaking a build over.
        val declaration = gradle.substringAfter("val gitCommit").substringBefore("val gitDirty")
        assertTrue("the commit lookup can now fail the build", declaration.contains("runCatching"))
        assertTrue("the commit lookup no longer degrades to null", declaration.contains("getOrNull"))
        assertTrue(
            "the stamp is applied even when there is no commit to stamp",
            gradle.contains("gitCommit?.let {"),
        )
    }
}
