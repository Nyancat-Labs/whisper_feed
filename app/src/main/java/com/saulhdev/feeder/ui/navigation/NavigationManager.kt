/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saulhdev.feeder.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.ui.icons.phosphor.Graph
import com.saulhdev.feeder.ui.icons.phosphor.Info
import com.saulhdev.feeder.ui.pages.AboutPage
import com.saulhdev.feeder.ui.pages.ArticleListPage
import com.saulhdev.feeder.ui.pages.ArticlePage
import com.saulhdev.feeder.ui.pages.ChangelogPage
import com.saulhdev.feeder.ui.pages.BlockedWordsPage
import com.saulhdev.feeder.ui.pages.AccountPage
import com.saulhdev.feeder.ui.pages.BackupPage
import com.saulhdev.feeder.ui.pages.LearnedPage
import com.saulhdev.feeder.ui.pages.LicensePage
import com.saulhdev.feeder.ui.pages.MainPage
import com.saulhdev.feeder.ui.pages.GlanceLocationPage
import com.saulhdev.feeder.ui.pages.PreferencesPage
import com.saulhdev.feeder.ui.pages.CategoryListPage
import com.saulhdev.feeder.ui.pages.LauncherPage
import com.saulhdev.feeder.ui.theme.reducedMotion
import com.saulhdev.feeder.ui.pages.BookmarkImportPage
import com.saulhdev.feeder.ui.pages.BrokenFeedsPage
import com.saulhdev.feeder.ui.pages.FeedLibraryPage
import com.saulhdev.feeder.ui.pages.InsecureFeedsPage
import com.saulhdev.feeder.ui.pages.StatisticsPage
import com.saulhdev.feeder.ui.pages.SuggestionsPage
import com.saulhdev.feeder.ui.pages.SourceAddPage
import com.saulhdev.feeder.ui.pages.SourceListPage
import com.saulhdev.feeder.ui.views.ComposeWebView
import kotlinx.serialization.Serializable

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

const val NAV_BASE = "nf-navigation://androidx.navigation/"

@Composable
fun NavigationManager(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(
        LocalNavController provides navController
    ) {
        // Screens slide, unless the reader has told the system they would
        // rather things did not. A whole screen sweeping in is the largest
        // movement the app makes, so it is the first one that should stop.
        val animate = !reducedMotion()

        NavHost(
            modifier = modifier,
            navController = navController,
            startDestination = NavRoute.Main(),
            enterTransition = {
                if (animate) fadeIn() + slideInHorizontally { it } else fadeIn()
            },
            exitTransition = {
                if (animate) fadeOut() + slideOutHorizontally { -it / 2 } else fadeOut()
            },
            popEnterTransition = {
                if (animate) fadeIn() + slideInHorizontally { -it } else fadeIn()
            },
            popExitTransition = {
                if (animate) fadeOut() + slideOutHorizontally { it / 2 } else fadeOut()
            },
        ) {

            composable<NavRoute.Main>(
                deepLinks = listOf(navDeepLink { uriPattern = "$NAV_BASE${Routes.MAIN}/{page}" })
            ) {
                val args = it.toRoute<NavRoute.Main>()
                MainPage(args.page)
            }
            // Reachable by deep link so the launcher overlay can open it
            // directly. It used to send Main/1, from the days when Settings was
            // the second page of a pager; MainPage has ignored that index since
            // the pager was removed, so "Settings" in the overlay quietly
            // opened the article list instead.
            composable<NavRoute.Settings>(
                deepLinks = listOf(navDeepLink { uriPattern = "$NAV_BASE${Routes.SETTINGS}" })
            ) { PreferencesPage() }
            composable<NavRoute.GlanceLocation> { GlanceLocationPage() }
            composable<NavRoute.Sources> { SourceListPage() }
            composable<NavRoute.About> { AboutPage() }
            composable<NavRoute.License> { LicensePage() }
            composable<NavRoute.Changelog> { ChangelogPage() }
            composable<NavRoute.SourceAdd> { SourceAddPage() }
            composable<NavRoute.Categories> { CategoryListPage() }
            composable<NavRoute.Learned> { LearnedPage() }
            composable<NavRoute.Account> { AccountPage() }
            composable<NavRoute.Backup> { BackupPage() }
            composable<NavRoute.Launcher> { LauncherPage() }
            composable<NavRoute.Suggestions> { SuggestionsPage() }
            composable<NavRoute.BookmarkImport> { BookmarkImportPage() }
            // Deep-linked for the "feeds have stopped working" notification,
            // which opens the page that lists them and can fix them.
            composable<NavRoute.BrokenFeeds>(
                deepLinks = listOf(navDeepLink { uriPattern = "$NAV_BASE${Routes.BROKEN_FEEDS}" })
            ) { BrokenFeedsPage() }
            composable<NavRoute.InsecureFeeds> { InsecureFeedsPage() }
            composable<NavRoute.FeedLibrary> { FeedLibraryPage() }
            composable<NavRoute.Statistics> { StatisticsPage() }
            composable<NavRoute.BlockedWords> { BlockedWordsPage() }
            // No deep link, on purpose. MainActivity is exported, as the
            // launcher entry has to be, and hands every intent it receives to
            // navigation: a link here let any installed app open a page of its
            // choosing inside Whisper's own browser, looking like part of the
            // app. Nothing in the app ever used it.
            composable<NavRoute.WebView> {
                val args = it.toRoute<NavRoute.WebView>()
                ComposeWebView(args.url)
            }
            composable<NavRoute.ArticleView>(
                deepLinks = listOf(navDeepLink {
                    uriPattern = "$NAV_BASE${Routes.ARTICLE_VIEW}/{uuid}"
                })
            ) {
                val args = it.toRoute<NavRoute.ArticleView>()
                ArticlePage(args.uuid)
            }
        }
    }
}

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ARTICLE_VIEW = "article_page"
    const val BROKEN_FEEDS = "broken_feeds"
}

sealed class NavItem(
    val title: Int,
    val icon: ImageVector,
    val content: @Composable () -> Unit = {}
) {
    data object Feed :
        NavItem(R.string.home, Phosphor.Info, {
            ArticleListPage()
        })

}

@Serializable
open class NavRoute {
    @Serializable
    data object Settings : NavRoute()

    @Serializable
    data object Sources : NavRoute()

    @Serializable
    data object GlanceLocation : NavRoute()

    @Serializable
    data object About : NavRoute()

    @Serializable
    data object SourceAdd : NavRoute()

    @Serializable
    data object Categories : NavRoute()

    @Serializable
    data object Learned : NavRoute()

    @Serializable
    data object Account : NavRoute()

    @Serializable
    data object Backup : NavRoute()

    @Serializable
    data object Launcher : NavRoute()

    @Serializable
    data object Suggestions : NavRoute()

    @Serializable
    data object BookmarkImport : NavRoute()

    @Serializable
    data object BrokenFeeds : NavRoute()

    @Serializable
    data object InsecureFeeds : NavRoute()

    @Serializable
    data object FeedLibrary : NavRoute()

    @Serializable
    data object Statistics : NavRoute()

    @Serializable
    data object BlockedWords : NavRoute()

    @Serializable
    data class ArticleView(val uuid: String = "") : NavRoute()

    @Serializable
    data object Changelog : NavRoute()

    @Serializable
    data class Main(val page: Int = 0) : NavRoute()

    @Serializable
    data object License : NavRoute()

    @Serializable
    data class WebView(val url: String = "") : NavRoute()
}