package com.saulhdev.feeder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Roadmap §19e: app and overlay both under MaterialExpressiveTheme. */
class ExpressiveThemeTest {

    private fun source(path: String) = File("src/main/java/com/saulhdev/feeder/$path").readText()

    @Test
    fun `app and overlay take the Expressive theme and its motion`() {
        listOf("ui/theme/Theme.kt", "manager/service/OverlayView.kt").forEach { path ->
            val text = source(path)
            assertTrue(path, text.contains("MaterialExpressiveTheme("))
            assertTrue(path, text.contains("motionScheme = WhisperMotion"))
            assertFalse(path, text.contains("import androidx.compose.material3.MaterialTheme\n"))
        }
        assertTrue(source("ui/theme/Theme.kt").contains("val WhisperMotion: MotionScheme = MotionScheme.expressive()"))
    }

    @Test
    fun `the reader's text-size theme keeps the motion it sits in`() {
        // A nested MaterialTheme without a motionScheme would quietly fall
        // back to the standard one for everything in the article.
        assertTrue(source("ui/pages/ArticlePage.kt").contains("motionScheme = MaterialTheme.motionScheme,"))
    }
}
