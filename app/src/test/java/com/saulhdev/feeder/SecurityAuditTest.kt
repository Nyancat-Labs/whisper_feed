package com.saulhdev.feeder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** What the security audit of 9 November 2026 fixed, kept fixed. */
class SecurityAuditTest {

    private val gradle = File("build.gradle.kts").readText()

    @Test
    fun `the preview tooling is in debug builds only`() {
        // ui-tooling brings an exported PreviewActivity into the APK.
        assertTrue(gradle.contains("debugImplementation(libs.compose.ui.tooling)"))
        assertFalse(gradle.contains("\n    implementation(libs.compose.ui.tooling)\n"))
    }

    @Test
    fun `only the main screen and the launcher panel are open to other apps`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertEquals(2, Regex("""android:exported="true"""").findAll(manifest).count())
        assertTrue(manifest.contains("""android:name=".MainActivity""""))
        assertTrue(manifest.contains("""android:name=".manager.service.OverlayService""""))
    }

    @Test
    fun `no jar is built in from a folder, and Rome's stray jars are left out`() {
        assertFalse(gradle.contains("implementation(fileTree("))
        assertTrue(gradle.contains("""resources.excludes.add("rome-2.1.0.jar")"""))
        assertTrue(gradle.contains("""resources.excludes.add("rome-utils-2.1.0.jar")"""))
    }

    @Test
    fun `the Gradle download is checked against its checksum`() {
        val wrapper = File("../gradle/wrapper/gradle-wrapper.properties").readText()
        assertTrue(Regex("""distributionSha256Sum=[0-9a-f]{64}""").containsMatchIn(wrapper))
    }

    @Test
    fun `the workflow can only read, runs the tests, and pins its actions`() {
        val ci = File("../.github/workflows/android.yml").readText()
        assertTrue(ci.contains("permissions:\n  contents: read"))
        assertTrue(ci.contains(":app:testDebugUnitTest"))
        assertTrue(ci.contains(":app:lintDebug"))
        Regex("""uses: (\S+)""").findAll(ci).forEach {
            assertTrue("pinned to a commit: ${it.groupValues[1]}",
                Regex("""@[0-9a-f]{40}$""").containsMatchIn(it.groupValues[1]))
        }
    }

    @Test
    fun `the feed library carries nobody's email address as its owner`() {
        File("src/main/assets/library").listFiles().orEmpty().forEach {
            assertFalse(it.name, it.readText().contains("<ownerEmail>"))
        }
    }

    @Test
    fun `there is a way to report a vulnerability, and update alerts`() {
        assertTrue(File("../SECURITY.md").readText().contains("Reporting a vulnerability"))
        assertTrue(File("../.github/dependabot.yml").readText().contains("package-ecosystem: gradle"))
    }
}
