/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saulhdev.feeder.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowSquareOut
import com.saulhdev.feeder.ui.icons.phosphor.DownloadSimple
import com.saulhdev.feeder.ui.icons.phosphor.ShareNetwork
import com.saulhdev.feeder.ui.icons.phosphor.X
import com.saulhdev.feeder.utils.PictureFiles
import kotlinx.coroutines.launch

/**
 * A picture to show full screen: what to load (an address, or the file kept
 * for a saved article), what it shows for TalkBack, and the link it sat in,
 * if any.
 */
data class PictureRequest(val model: Any, val description: String? = null, val link: String? = null)

/**
 * Opens [PictureViewer] from anywhere in the reader. Null where there is no
 * viewer to open - the picture then does what it did before, following its
 * link if it has one.
 */
val LocalPictureViewer = staticCompositionLocalOf<((PictureRequest) -> Unit)?> { null }

/**
 * A reader's picture, full screen on black. Asked for by a reader on 16
 * October 2026, with saving.
 *
 * Pinch to zoom, double-tap to zoom in or back out, drag when zoomed; at its
 * own size a swipe down or back closes it. Save, Share, and Open link when
 * the picture was a link: tapping a linked picture used to follow the link,
 * which made the tap mean two things, so a tap always enlarges and the link
 * is a button here. Save only from Android 10, where it needs no permission;
 * see PictureFiles.canSave.
 */
@Composable
fun PictureViewer(request: PictureRequest, onOpenLink: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismissAfterPx = with(LocalDensity.current) { 120.dp.toPx() }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var busy by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = DOUBLE_TAP_SCALE
                        }
                    })
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                        offset = if (scale > 1f) offset + pan
                        // At its own size only downwards moves it, towards closing.
                        else Offset(0f, (offset.y + pan.y).coerceAtLeast(0f))
                        if (scale == 1f && offset.y > dismissAfterPx) onDismiss()
                    }
                },
        ) {
            // Bounded like every other picture: twice the screen, for detail
            // when zoomed, and never past FULL_SCREEN_MAX_PX. A page's
            // largest offer can be a 6000-pixel original, and decoded whole
            // that alone is over a hundred megabytes.
            val bound = with(LocalDensity.current) {
                val screen = LocalConfiguration.current
                (2 * maxOf(screen.screenWidthDp, screen.screenHeightDp).dp.toPx()).toInt()
                    .coerceAtMost(PictureFiles.FULL_SCREEN_MAX_PX)
            }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(request.model)
                    .size(bound)
                    .scale(Scale.FIT)
                    .precision(Precision.INEXACT)
                    .build(),
                contentDescription = request.description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )

            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss) {
                        Icon(Phosphor.X, contentDescription = stringResource(R.string.picture_close), tint = Color.White)
                    }
                }
                Box(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (PictureFiles.canSave) {
                        ViewerAction(Phosphor.DownloadSimple, R.string.picture_save, enabled = !busy) {
                            busy = true
                            scope.launch {
                                val saved = PictureFiles.save(context, request.model)
                                busy = false
                                Toast.makeText(
                                    context,
                                    if (saved != null) R.string.picture_saved else R.string.picture_not_saved,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    ViewerAction(Phosphor.ShareNetwork, R.string.picture_share, enabled = !busy) {
                        busy = true
                        scope.launch {
                            val shared = PictureFiles.share(context, request.model)
                            busy = false
                            if (!shared) Toast.makeText(context, R.string.picture_not_loaded, Toast.LENGTH_SHORT).show()
                        }
                    }
                    request.link?.let { link ->
                        ViewerAction(Phosphor.ArrowSquareOut, R.string.picture_open_link) {
                            onDismiss()
                            onOpenLink(link)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text(
            text = stringResource(label),
            color = Color.White,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
