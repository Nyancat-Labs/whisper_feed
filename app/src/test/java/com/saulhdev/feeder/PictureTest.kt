package com.saulhdev.feeder

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.components.GroupedMenu
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowsDownUp
import com.saulhdev.feeder.ui.pages.ErrorResultView
import com.saulhdev.feeder.ui.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Pictures of parts of screens, drawn on the computer and compared with the
 * saved ones in src/test/screenshots (20 November 2026). They catch what the
 * unit tests can't see: an icon too small to read (the old A-Z sort icon), a
 * loading indicator off to one side.
 *
 *   ./gradlew recordRoborazziDebug   saves new pictures (after a change you meant)
 *   ./gradlew verifyRoborazziDebug   fails if a picture changed
 *
 * The ordinary test run draws them but compares nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-xhdpi")
class PictureTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Themed(dark: Boolean = false, content: @Composable () -> Unit) {
        AppTheme(mode = if (dark) "dark" else "light", dynamicColor = false, pureBlack = false, fontPref = "inter") {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) { content() }
        }
    }

    /** Every icon in the app at the size it's shown, with its name. */
    @OptIn(ExperimentalLayoutApi::class)
    @Test
    @Config(qualifiers = "w400dp-h1900dp-xhdpi")
    fun `icon sheet`() {
        val folder = File("src/main/java/com/saulhdev/feeder/ui/icons/phosphor")
        val icons: List<Pair<String, ImageVector>> = folder.listFiles()!!.filter { it.extension == "kt" }.sorted()
            .mapNotNull { file ->
                val name = Regex("""val Phosphor\.(\w+)""").find(file.readText())?.groupValues?.get(1)
                    ?: return@mapNotNull null
                val holder = Class.forName("com.saulhdev.feeder.ui.icons.phosphor.${file.nameWithoutExtension}Kt")
                name to (holder.getMethod("get$name", Phosphor::class.java).invoke(null, Phosphor) as ImageVector)
            }
        compose.setContent {
            Themed {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    icons.forEach { (name, icon) ->
                        Column(Modifier.width(70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(icon, contentDescription = name, modifier = Modifier.size(24.dp))
                            Text(name, fontSize = 8.sp, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/icon-sheet.png")
    }

    /** The sort button on Sources (sheet 127). */
    @Test
    fun `sort button`() {
        compose.setContent {
            Themed { GroupedMenu(icon = Phosphor.ArrowsDownUp, description = R.string.sort_by_title, sections = emptyList()) }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/sort-button.png")
    }

    /** Add source when a site turns apps away (sheet 117). */
    private fun blocksApps(dark: Boolean) {
        compose.setContent {
            Themed(dark) {
                ErrorResultView(
                    title = "Couldn't get the feed from itsecurityguru.org",
                    description = "This site blocks reading apps. The feed is probably fine, but the site " +
                        "doesn't let apps like Whisper fetch it.",
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/blocks-apps-${if (dark) "dark" else "light"}.png")
    }

    @Test
    fun `blocks reading apps, light`() = blocksApps(dark = false)

    @Test
    fun `blocks reading apps, dark`() = blocksApps(dark = true)
}

