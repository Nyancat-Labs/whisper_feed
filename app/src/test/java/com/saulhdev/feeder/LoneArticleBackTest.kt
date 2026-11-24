package com.saulhdev.feeder

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.saulhdev.feeder.ui.navigation.NavRoute
import com.saulhdev.feeder.ui.pages.backFromLoneArticle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Szept 470: back from an article opened from the launcher panel closed the
 * app, and Lawnchair had already gone to the home screen. It goes to
 * Whisper's feed now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LoneArticleBackTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var nav: NavHostController

    private fun host(start: NavRoute) = compose.setContent {
        nav = rememberNavController()
        NavHost(nav, startDestination = start) {
            composable<NavRoute.Main> { Text("feed") }
            composable<NavRoute.ArticleView> { Text("article") }
        }
    }

    private fun onFeed() = nav.currentDestination?.hasRoute(NavRoute.Main::class) == true

    @Test
    fun `the article on its own goes back to a feed in its place`() {
        host(NavRoute.ArticleView("a1"))
        compose.runOnIdle { backFromLoneArticle(nav) }
        compose.runOnIdle {
            assertTrue(onFeed())
            assertEquals("the article is not left behind the feed", null, nav.previousBackStackEntry)
        }
    }

    @Test
    fun `with the feed beneath, it goes back to that feed`() {
        host(NavRoute.Main())
        compose.runOnIdle { nav.navigate(NavRoute.ArticleView("a1")) }
        compose.runOnIdle { backFromLoneArticle(nav) }
        compose.runOnIdle {
            assertTrue(onFeed())
            assertFalse(nav.previousBackStackEntry != null)
        }
    }
}
