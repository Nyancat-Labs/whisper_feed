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

package com.saulhdev.feeder.ui.views

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.ui.overlay.CARD_MARGIN
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.utils.isBrowsable

/**
 * An article's web page, opened inside the app. Most pages need their scripts
 * to show at all, so JavaScript is on; what else a page can reach is turned
 * off below.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ComposeWebView(
    pageUrl: String
) {
    val context = LocalContext.current
    val navController = LocalNavController.current

    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    val fallbackTitle = stringResource(R.string.app_name)
    val title = remember { mutableStateOf(fallbackTitle) }
    val subTitle = remember { mutableStateOf("") }

    // What the WebView was last told to load, as opposed to where the reader
    // has since navigated. The update block used to compare the live URL
    // against pageUrl and call loadUrl whenever they differed — which is after
    // the first tap on any link, on the very next recomposition, of which the
    // progress bar alone causes dozens. Following a link snapped straight back
    // to the article. Held outside composition so it survives one.
    val loaded = remember { mutableStateOf<String?>(null) }
    val webView = remember { mutableStateOf<WebView?>(null) }

    // Back walks the browser's own history first, then leaves the screen. It
    // used to consult a rememberNavController() created here and attached to
    // no graph, whose current entry is therefore always null — so the else
    // branch never ran and the first press called activity.finish(), closing
    // the whole app from a page the reader had opened to read.
    BackHandler {
        val view = webView.value
        if (view != null && view.canGoBack()) view.goBack()
        else navController.popBackStack()
    }

    if (!isBrowsable(pageUrl)) {
        // Nothing to render and nothing to explain: an address this screen
        // will not open is one it was not asked to open by us.
        BackHandler(enabled = true) { navController.popBackStack() }
        return
    }

    ViewWithActionBar(
        title = title.value,
        titleSize = 16.sp,
        subTitle = subTitle.value,
        showBackButton = true,
        onBackAction = {
            val view = webView.value
            if (view != null && view.canGoBack()) view.goBack()
            else navController.popBackStack()
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    // The margin the feed uses; see ArticlePage.
                    start = CARD_MARGIN,
                    end = CARD_MARGIN,
                    bottom = paddingValues.calculateBottomPadding() + 8.dp
                ),
        ) {
            if (isLoading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        // A reader is not a general-purpose browser. None of
                        // these is needed to show an article, and each is a
                        // way for a page to reach something that is not the
                        // web: the app's own files, another app's provider,
                        // or an http subresource smuggled into an https page.
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode =
                            WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest,
                            ): Boolean {
                                val target = request.url.toString()
                                if (isBrowsable(target)) return false
                                // A tel:, mailto: or market: link is a real
                                // link and belongs to whichever app handles
                                // it. Anything else the system has no handler
                                // for is simply dropped.
                                runCatching { context.launchView(target) }
                                return true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                title.value = view?.title?.takeIf { it.isNotBlank() }
                                    ?: fallbackTitle
                                subTitle.value = url?.toUri()?.host.orEmpty()
                            }

                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                isLoading = true
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress / 100f
                            }
                        }

                        webView.value = this
                        loaded.value = pageUrl
                        loadUrl(pageUrl)
                    }
                },
                // Only when the screen is pointed at a different address —
                // never because the reader has followed a link.
                update = { view ->
                    if (loaded.value != pageUrl) {
                        loaded.value = pageUrl
                        view.loadUrl(pageUrl)
                    }
                },
                onRelease = { view ->
                    // A WebView outlives the composition unless it is told
                    // not to: it holds a render process, a JavaScript timer
                    // and a chunk of bitmap memory per instance.
                    view.stopLoading()
                    view.loadUrl("about:blank")
                    view.destroy()
                    webView.value = null
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
