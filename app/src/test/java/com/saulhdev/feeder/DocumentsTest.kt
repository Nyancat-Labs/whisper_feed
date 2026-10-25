package com.saulhdev.feeder

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.saulhdev.feeder.ui.pages.Documents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * About's changelog and licence, read from the repository's own files. The
 * hand-kept copies they replace stopped at 1.0.0 and named Neo Feed's author
 * and source.
 */
class DocumentsTest {

    private val changelog = File("../CHANGELOG.md").readText()

    @Test
    fun `every Whisper release is in the changelog, and nothing of Neo Feed's`() {
        val blocks = Documents.changelog(changelog, unreleased = null)
        val headings = blocks.filterIsInstance<Documents.Heading>().map { it.text }
        val releases = Regex("(?m)^(1\\.\\d+\\.\\d+(\\.\\d+)? — .+)\\n-{3,}").findAll(changelog).map { it.groupValues[1] }.toList()
        assertTrue(releases.isNotEmpty())
        assertEquals(releases, headings)
        assertTrue(headings.last().startsWith("1.0.0"))
        assertFalse(blocks.any { "Neo Feed" in it.text && it is Documents.Heading })
        assertFalse("a release build shows no unreleased work", headings.any { "next release" in it })
    }

    @Test
    fun `a test build shows what is coming, first`() {
        val blocks = Documents.changelog(changelog, unreleased = "Coming in the next release")
        assertEquals("Coming in the next release", blocks.first().text)
    }

    @Test
    fun `bullets keep their wrapped lines together`() {
        val md = "CHANGELOG\n=========\n\nIntro.\n\n1.0.1 — 5 October 2026\n----------------------\n\n- **One** goes\n  on here.\n- Two.\n\n---\n\nOld\n===\n"
        val blocks = Documents.changelog(md, null)
        assertEquals(
            listOf(
                Documents.Heading("1.0.1 — 5 October 2026"),
                Documents.Bullet("**One** goes on here."),
                Documents.Bullet("Two."),
            ),
            blocks,
        )
    }

    @Test
    fun `marks become styles, and repository links go to GitHub`() {
        val s = Documents.styled("See **this** and [`ROADMAP.md`](ROADMAP.md).", Color.Red)
        assertEquals("See this and ROADMAP.md.", s.text)
        val url = s.getLinkAnnotations(0, s.length).single().item as LinkAnnotation.Url
        assertEquals("https://github.com/Nyancat-Labs/whisper_feed/blob/main/ROADMAP.md", url.url)
        assertEquals("https://example.org", Documents.linkTarget("https://example.org"))
    }

    @Test
    fun `the licence reflows to the screen and names its sections`() {
        val blocks = Documents.licence(File("../LICENSE").readText())
        val headings = blocks.filterIsInstance<Documents.Heading>().map { it.text }
        assertTrue("GNU GENERAL PUBLIC LICENSE" in headings)
        assertTrue("0. Definitions." in headings)
        assertTrue("15. Disclaimer of Warranty." in headings)
        assertFalse("no hard line breaks left", blocks.any { '\n' in it.text })
    }

    @Test
    fun `the licence page names Whisper's source, not Neo Feed's`() {
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains("<string name=\"licence_source\">"))
        assertEquals("https://github.com/Nyancat-Labs/whisper_feed", Documents.SOURCE)
        assertFalse(File("src/main/assets/license.htm").exists())
        val build = File("build.gradle.kts").readText()
        assertTrue(build.contains("rootProject.file(\"CHANGELOG.md\"), rootProject.file(\"LICENSE\")"))
    }
}
