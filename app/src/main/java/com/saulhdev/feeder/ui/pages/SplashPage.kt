/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.theme.WhisperCobalt
import kotlinx.coroutines.delay

/**
 * How long the branded screen stays up even if the app is ready sooner.
 *
 * Was 700ms: on a phone whose feed is ready in a tenth of a second, every
 * launch waited most of a second for the logo. Google asks for the system
 * splash alone; the owner kept this one for the brand, but short (25
 * October). Long enough not to flash, no longer.
 */
internal const val MINIMUM_MS = 300L

/** And the longest it will wait for content that may never arrive. */
private const val MAXIMUM_MS = 2_500L

/** How tall the symbol draws here. */
private val MARK_HEIGHT = 150.dp

/**
 * The loading screen, over the app until it has something to show.
 *
 * There are two splashes and they do different jobs. The window splash — the
 * theme's `SplashTheme` — covers the gap between tapping the icon and the first
 * frame, which is the part that would otherwise flash the theme's light
 * background. It can only draw an icon on a colour; the system gives it no room
 * for a wordmark, a spinner or a line of copy.
 *
 * This is the second one, and it carries the brand while the first feed query
 * runs. It is bounded at both ends on purpose: a [MINIMUM_MS] floor so a warm
 * start does not flash it for 40ms, and a [MAXIMUM_MS] ceiling so an empty or
 * broken feed cannot leave the user staring at it. Between the two it leaves as
 * soon as [ready] goes true.
 */
@Composable
fun WhisperSplash(
    ready: Boolean,
    content: @Composable () -> Unit,
) = WhisperSplash(ready = ready, enabled = true, content = content)

/**
 * @param enabled false when the app was deep-linked into rather than launched,
 *   in which case the content is shown straight away. A brand screen on the way
 *   to a settings list the user asked for from another surface is an
 *   interruption, not an introduction.
 */
@Composable
fun WhisperSplash(
    ready: Boolean,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    var minimumElapsed by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(MINIMUM_MS)
        minimumElapsed = true
        delay(MAXIMUM_MS - MINIMUM_MS)
        timedOut = true
    }

    val visible = !timedOut && !(ready && minimumElapsed)

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        AnimatedVisibility(visible = visible, exit = fadeOut()) {
            // Read from the resolved scheme rather than the system setting, so
            // an app forced to Dark or Pure black gets the dark ground too.
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorResource(R.color.whisper_splash_bg))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // The full gradient symbol, on the ground the app icon uses.
                // Its middle blade is within a few points of WhisperCobalt, so
                // on the cobalt this screen used to have, two of the three
                // blades disappeared into the background.
                // ic_brand_mark, not ic_splash_mark: the latter is padded to
                // the platform's splash-icon spec, and that transparent margin
                // counts in layout — it put 90dp of air under the symbol.
                Image(
                    painter = painterResource(R.drawable.ic_brand_mark),
                    contentDescription = null,
                    modifier = Modifier.height(MARK_HEIGHT),
                )
                Spacer(Modifier.height(28.dp))
                // The wordmark artwork is the name only — it does not carry
                // the tagline, which is why removing the text line beneath it
                // lost the tagline entirely rather than deduplicating it.
                Image(
                    painter = painterResource(
                        if (dark) R.drawable.ic_wordmark_light
                        else R.drawable.ic_wordmark_navy
                    ),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dark) Color.White.copy(alpha = 0.78f)
                    else colorResource(R.color.whisper_slate),
                )
                Spacer(Modifier.height(56.dp))
                CircularProgressIndicator(
                    // Cobalt is legible on the light ground but sits close to
                    // the dark one; sky is the mark's own top blade.
                    color = if (dark) colorResource(R.color.whisper_sky)
                    else WhisperCobalt,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }
}
