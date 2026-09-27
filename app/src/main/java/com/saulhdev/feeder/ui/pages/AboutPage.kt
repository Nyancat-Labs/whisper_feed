/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team
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


package com.saulhdev.feeder.ui.pages


import androidx.core.graphics.createBitmap
import androidx.compose.ui.platform.LocalResources
import android.content.ActivityNotFoundException
import android.graphics.Canvas
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import coil.annotation.ExperimentalCoilApi
import com.saulhdev.feeder.BuildConfig
import com.saulhdev.feeder.R
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.ui.navigation.PageItem
import com.saulhdev.feeder.ui.components.ContributorRow
import com.saulhdev.feeder.ui.icons.phosphor.HeartStraight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.height
import com.saulhdev.feeder.ui.components.LinkItem
import com.saulhdev.feeder.ui.components.PagePreference
import com.saulhdev.feeder.ui.components.PreferenceGroupHeading
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Browser
import com.saulhdev.feeder.ui.icons.phosphor.Info
import com.saulhdev.feeder.ui.icons.phosphor.EyeSlash
import com.saulhdev.feeder.ui.icons.phosphor.BracketsSquare
import com.saulhdev.feeder.ui.icons.phosphor.GithubLogo
import com.saulhdev.feeder.ui.icons.phosphor.Megaphone
import com.saulhdev.feeder.ui.icons.phosphor.TelegramLogo
import com.saulhdev.feeder.utils.urlDecode

@OptIn(ExperimentalCoilApi::class)
@Composable
fun AboutPage() {
    val title = stringResource(id = R.string.title_about)
    ViewWithActionBar(
        title = title,
        largeTitle = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(8.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.shapes.extraLarge
                        )
                        .clip(MaterialTheme.shapes.extraLarge),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                        ),
                        leadingContent = {
                            ResourcesCompat.getDrawable(
                                LocalResources.current,
                                R.mipmap.ic_launcher,
                                LocalContext.current.theme
                            )?.let { drawable ->
                                val bitmap = createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight)
                                val canvas = Canvas(bitmap)
                                drawable.setBounds(0, 0, canvas.width, canvas.height)
                                drawable.draw(canvas)
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .requiredSize(84.dp)
                                        .clip(MaterialTheme.shapes.large)
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary,
                                // No decorative face here. This was drawing
                                // the app's own name in a display font
                                // inherited from upstream, which is not the
                                // wordmark, does not match a single other
                                // screen, and rendered "Whisper" as something
                                // the brand board would not recognise.
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = stringResource(id = R.string.app_version) + ": "
                                            + BuildConfig.VERSION_NAME + " ( Build " + BuildConfig.VERSION_CODE + " )",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = BuildConfig.APPLICATION_ID,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                // Who is answerable for the app, which until
                                // now appeared nowhere in it. The contributor
                                // rows below name the people who wrote it;
                                // this names the party the privacy notice
                                // makes the data controller.
                                Text(
                                    text = stringResource(id = R.string.about_publisher),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        items(links) { link ->
                            LinkItem(
                                icon = link.icon,
                                label = stringResource(id = link.labelResId),
                                url = link.url,
                            )
                        }
                    }
                }
            }
            item {
                PreferenceGroupHeading(heading = stringResource(id = R.string.about_whisper_team))
            }
            itemsIndexed(whisperTeam) { i, it ->
                ContributorRow(
                    nameId = it.name,
                    roleId = it.descriptionRes,
                    photoUrl = it.photoUrl,
                    url = it.webpage,
                    index = i,
                    groupSize = whisperTeam.size
                )
            }
            item {
                PreferenceGroupHeading(heading = stringResource(id = R.string.about_support))
            }
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.about_support_summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinkItem(
                            icon = Phosphor.HeartStraight,
                            label = stringResource(R.string.about_support),
                            url = SUPPORT_URL,
                        )
                    }
                }
            }
            item {
                PreferenceGroupHeading(heading = stringResource(id = R.string.about_team))
            }
            itemsIndexed(contributors) { i, it ->
                ContributorRow(
                    nameId = it.name,
                    roleId = it.descriptionRes,
                    photoUrl = it.photoUrl,
                    url = it.webpage,
                    index = i,
                    groupSize = contributors.size
                )
            }
            item {
                PreferenceGroupHeading(heading = stringResource(id = R.string.about_build_information))
            }
            itemsIndexed(listOf(PageItem.AboutLicense, PageItem.AboutChangelog)) { i, it ->
                PagePreference(
                    titleId = it.titleId,
                    icon = it.icon,
                    route = it.route,
                    index = i,
                    groupSize = 2
                )
            }
        }
    }
}


private data class Link(
    val icon: ImageVector,
    @StringRes val labelResId: Int,
    val url: String
)

private data class TeamMember(
    @StringRes val name: Int,
    @StringRes val descriptionRes: Int,
    val photoUrl: String,
    val webpage: String
)

/**
 * Where this came from.
 *
 * There were four links here and all of them belonged to Neo Feed: its
 * repository under "Source code", and its Telegram and Matrix rooms under
 * "Channel" and "Community". Sending a Whisper reader to another project's
 * chat rooms to ask about this app helps nobody in either place.
 *
 * Whisper's own repository leads the list now that it is renamed and public.
 * It was deliberately left out while `076feed` was still the name, because a
 * link that 404s the week after release is worse than no link.
 */
private val links = listOf(
    Link(
        icon = Phosphor.GithubLogo,
        labelResId = R.string.about_source_code,
        url = "https://github.com/defsix/whisper_feed"
    ),
    // The app had no route to either of these at all: a reader who wanted to
    // know what Whisper does with their reading had to find the repository
    // and go looking. They lead the rest of the row for that reason, and
    // point at the files in the repository rather than a copy on a website,
    // so the text cannot drift from the version the source was checked
    // against.
    Link(
        icon = Phosphor.EyeSlash,
        labelResId = R.string.about_privacy,
        url = "https://github.com/defsix/whisper_feed/blob/main/PRIVACY.md"
    ),
    Link(
        icon = Phosphor.Info,
        labelResId = R.string.about_disclaimer,
        url = "https://github.com/defsix/whisper_feed/blob/main/DISCLAIMER.md"
    ),
    Link(
        icon = Phosphor.Browser,
        labelResId = R.string.about_website,
        url = "https://nyancatlabs.com"
    ),
    Link(
        icon = Phosphor.GithubLogo,
        labelResId = R.string.about_upstream,
        url = "https://github.com/NeoApplications/Neo-Feed"
    ),
    // The root of the line, and not a footnote: HomeFeeder's SDK package is
    // still named in this app's ProGuard rules, and Lawnchair's feed whitelist
    // still carries its package.
    Link(
        icon = Phosphor.GithubLogo,
        labelResId = R.string.about_origin,
        url = "https://github.com/iTaysonLab/HomeFeeder"
    ),
)

/**
 * Where to chip in, for anyone who wants to.
 *
 * A link out rather than an in-app purchase, and that is a decision rather
 * than a stage on the way to one. Play's billing library is proprietary, and
 * it would be the only proprietary dependency in an app whose whole claim is
 * that it has none — F-Droid would flag it, and the privacy statement would
 * have to grow a paragraph explaining an exception. For a voluntary tip that
 * gates nothing, a link costs the reader one tap and costs the app nothing.
 *
 * It sits at the bottom of About, on its own, once. Nothing in the app is
 * withheld from anyone who ignores it, and nothing changes for anyone who
 * does not — which is the only arrangement that leaves the reader free to
 * decide it is not for them.
 *
 * Ko-fi rather than GitHub Sponsors: it needed no enrolment queue, and it
 * takes nothing from a one-off tip.
 */
private const val SUPPORT_URL = "https://ko-fi.com/defsix"

/**
 * Whisper's author.
 *
 * Kept separate from the Neo Feed credit below rather than appended to it:
 * one list of three people under one heading would claim a team that does not
 * exist and would take credit for Neo Feed's work at the same time.
 */
private val whisperTeam = listOf(
    TeamMember(
        name = R.string.about_whisper_author,
        descriptionRes = R.string.about_whisper_role,
        photoUrl = "https://github.com/defsix.png",
        webpage = "https://github.com/defsix/whisper_feed"
    ),
)

private val contributors = listOf(
    TeamMember(
        name = R.string.about_developer,
        descriptionRes = R.string.author_role,
        photoUrl = "https://avatars.githubusercontent.com/u/6044050",
        webpage = "https://github.com/saulhdev"
    ),
    TeamMember(
        name = R.string.about_developer2,
        descriptionRes = R.string.author_role,
        photoUrl = "https://avatars.githubusercontent.com/u/40302595",
        webpage = "https://github.com/machiav3lli"
    )
)

@Composable
fun LicensePage() {
    ViewWithActionBar(
        title = stringResource(R.string.about_licenses),
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 32.dp,
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) {
            item {
                PreferencesWebView(url = "file:///android_asset/license.htm")

            }
        }
    }
}

@Composable
fun ChangelogPage() {
    ViewWithActionBar(
        title = stringResource(R.string.about_changelog),
    ) {
        PreferencesWebView(url = "file:///android_asset/changelog.htm")
        Spacer(modifier = Modifier.requiredHeight(50.dp))
    }
}

/**
 * The bundled changelog and licence pages.
 *
 * Only ever pointed at `file:///android_asset`, and it stays that way: an
 * asset page cannot be pointed somewhere else by anything the reader does,
 * and a link inside one goes to the system browser rather than opening a
 * remote page inside a WebView that trusts its own assets.
 */
@Composable
fun PreferencesWebView(url: String) {
    val context = LocalContext.current
    // The stylesheet used to be light.css whatever the phone was set to, so
    // the changelog opened as a white sheet in the middle of a dark app.
    val cssFile = if (isSystemInDarkTheme()) "dark.css" else "light.css"
    val loaded = remember { mutableStateOf<String?>(null) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.allowFileAccess = false
                settings.allowContentAccess = false

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, finishedUrl: String) {
                        if (finishedUrl.startsWith("file:///android_asset")) {
                            runCatching {
                                val encoded = Base64.encodeToString(
                                    ctx.assets.open(cssFile).use { it.readBytes() },
                                    Base64.NO_WRAP,
                                )
                                // JavaScript is turned on for the injection
                                // and left on until the page reports it has
                                // run. It used to be switched off on the very
                                // next line, while loadUrl was still queued,
                                // so whether the stylesheet applied at all
                                // came down to a race.
                                settings.javaScriptEnabled = true
                                evaluateJavascript(
                                    """
                                    (function() {
                                      var head = document.getElementsByTagName('head')[0];
                                      var style = document.createElement('style');
                                      style.type = 'text/css';
                                      style.innerHTML = window.atob('$encoded');
                                      head.appendChild(style);
                                    })()
                                    """.trimIndent()
                                ) { settings.javaScriptEnabled = false }
                            }
                        }
                        super.onPageFinished(view, finishedUrl)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        // The request's own address, not the one this screen
                        // was opened with. Testing the outer `url` meant every
                        // link on the page led back to the page itself, or
                        // opened the changelog in a browser.
                        val target = request.url.toString()
                        if (target.startsWith("file:///android_asset")) return false
                        runCatching { context.launchView(target) }
                        return true
                    }
                }
            }
        },
        // Reloaded only when the screen is pointed at a different page. It
        // used to call loadUrl on every recomposition, throwing away the
        // scroll position and re-running the stylesheet injection each time.
        update = { webView ->
            val target = url.urlDecode()
            if (loaded.value != target) {
                loaded.value = target
                webView.loadUrl(target)
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        },
    )
}
