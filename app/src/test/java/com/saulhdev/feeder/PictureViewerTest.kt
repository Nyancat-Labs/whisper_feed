package com.saulhdev.feeder

import com.saulhdev.feeder.utils.ImageCandidates
import com.saulhdev.feeder.utils.PictureFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PictureViewerTest {

    @Test
    fun `full screen takes the largest size on offer`() {
        val sora = ImageCandidates(
            "https://soranews24.com/story/",
            "https://soranews24.com/u/5.jpg?resize=150,113 150w, https://soranews24.com/u/5.jpg 1200w, " +
                "https://soranews24.com/u/5.jpg?resize=640,480 640w",
            "https://soranews24.com/u/5.jpg?w=640",
        )
        assertEquals("https://soranews24.com/u/5.jpg", sora.largestUrl())
        val dense = ImageCandidates("https://example.com/", "a.jpg 1x, b.jpg 2x", "a.jpg")
        assertEquals("https://example.com/b.jpg", dense.largestUrl())
        assertEquals("https://example.com/c.jpg", ImageCandidates("https://example.com/", "", "https://example.com/c.jpg").largestUrl())
    }

    @Test
    fun `the share sheet is given only the shared picture's folder`() {
        val paths = File("src/main/res/xml/file_paths.xml").readText()
        assertTrue(paths.contains("path=\"${PictureFiles.SHARE_DIR}/\""))
        assertTrue("nothing wider than a folder of its own", !paths.contains("path=\"/\"") && !paths.contains("path=\".\""))
    }

    @Test
    fun `saving asks for no permission`() {
        val files = File("src/main/java/com/saulhdev/feeder/utils/PictureFiles.kt").readText()
        assertTrue(files.contains("Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q"))
        val viewer = File("src/main/java/com/saulhdev/feeder/ui/components/PictureViewer.kt").readText()
        assertTrue(viewer.contains("if (PictureFiles.canSave)"))
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.substringAfter("WRITE_EXTERNAL_STORAGE").substringBefore("/>").contains("maxSdkVersion=\"28\""))
    }

    @Test
    fun `a tap on a picture enlarges it, and its link moves to the viewer`() {
        val reader = File("src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt").readText()
        assertTrue(reader.contains("viewer(PictureRequest(kept ?: largest, alt.ifBlank { null }, link))"))
        val page = File("src/main/java/com/saulhdev/feeder/ui/pages/ArticlePage.kt").readText()
        assertTrue(page.contains("CompositionLocalProvider(LocalPictureViewer provides { viewing = it })"))
        assertTrue("the opening picture too", page.contains("viewing = PictureRequest(picture)"))
        assertTrue("and says so to TalkBack", page.contains(".clickable(onClickLabel = stringResource(R.string.picture_enlarge))"))
    }

    @Test
    fun `closing follows the finger and springs, as Expressive moves`() {
        val viewer = File("src/main/java/com/saulhdev/feeder/ui/components/PictureViewer.kt").readText()
        assertTrue("Expressive's default spatial spring", viewer.contains("spring<Float>(dampingRatio = 0.8f, stiffness = 380f)"))
        assertTrue("a flick closes as well as a long pull", viewer.contains("abs(fling) > flingPxPerSecond"))
        assertTrue("less than that springs back", viewer.contains("pull.animateTo(0f, SPRING, initialVelocity = fling)"))
        assertTrue("the background thins as it is pulled", viewer.contains("Color.Black.copy(alpha = 1f - pulled)"))
    }
}
