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

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.GearSix
import com.saulhdev.feeder.utils.backgroundStatus
import com.saulhdev.feeder.utils.restrictsBackground

/**
 * Says so when Android is keeping Whisper off mobile data in the background,
 * and opens the one switch that lifts it.
 *
 * Found the long way: a day of reports showed every scheduled sync on mobile
 * data cut off seconds after Whisper left the screen, with Data Saver off and
 * Background data on. On that phone "Unrestricted mobile data usage" was the
 * switch that mattered, and nothing in Whisper said a word about it.
 *
 * Only while "Wi-Fi only" is off - with it on, no background sync would use
 * mobile data anyway - and read again on every return to the screen, so
 * coming back from Android's settings with the switch on clears it at once.
 */
@Composable
fun BackgroundDataHint(wifiOnly: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember { mutableIntStateOf(backgroundStatus(context)) }
    LifecycleResumeEffect(Unit) {
        status = backgroundStatus(context)
        onPauseOrDispose { }
    }
    if (wifiOnly || !restrictsBackground(status)) return

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.background_data_blocked_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.background_data_blocked_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            ActionButton(
                text = stringResource(R.string.background_data_blocked_action),
                icon = Phosphor.GearSix,
                onClick = { openBackgroundDataSettings(context) },
            )
        }
    }
}

/**
 * Android's page for this app's unrestricted data, or its app page where a
 * phone does not offer that one directly.
 */
private fun openBackgroundDataSettings(context: Context) {
    val app = "package:${context.packageName}".toUri()
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, app))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
    }
}
