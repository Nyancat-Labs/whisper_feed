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
package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R

/**
 * The mark and the name, as the app's header and the panel's both draw them.
 *
 * ic_brand_mark rather than the launcher icon's foreground layer: that is an
 * adaptive-icon layer whose mark fills half its canvas, so a 34 dp box drew
 * a 17 dp mark. This artwork is cropped to its own bounds, so the size given
 * is the size drawn, standing about twice the name's cap height.
 *
 * The name never wraps. Beside the mark and four buttons it needs about
 * 355 dp at the normal text size, which leaves a 360 dp phone almost nothing:
 * at 130% it broke to "Whispe / r", and on the Fold6's cover screen to
 * "Whis / per" at any size. Where it will not fit on one line it is not drawn,
 * and the mark, which is the brand too, stands alone. TalkBack still reads
 * the name, since only its drawing is hidden.
 */
@Composable
fun BrandTitle(modifier: Modifier = Modifier) {
    var nameFits by remember { mutableStateOf(true) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier.height(36.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            onTextLayout = { nameFits = !it.hasVisualOverflow },
            modifier = Modifier
                .weight(1f, fill = false)
                // Read at draw time, so the answer from this frame's layout
                // is the one drawn: no frame shows the name cut in half.
                .graphicsLayer { alpha = if (nameFits) 1f else 0f },
        )
    }
}
