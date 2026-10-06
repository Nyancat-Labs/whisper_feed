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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.manager.glance.GlancePlace
import com.saulhdev.feeder.manager.glance.GlanceStateHolder
import com.saulhdev.feeder.manager.glance.WeatherRepository
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.navigation.LocalNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import com.saulhdev.feeder.data.content.asState

/**
 * Picks the place the weather and sunset chips describe.
 *
 * A searched place rather than the device's location, so the app never asks for
 * a location permission and the only coordinate that ever leaves the device is
 * one the user typed in themselves.
 */
@Composable
fun GlanceLocationPage(
    prefs: FeedPreferences = koinInject(),
    weatherRepo: WeatherRepository = koinInject(),
    glanceHolder: GlanceStateHolder = koinInject(),
) {
    val navController = LocalNavController.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GlancePlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val current by prefs.glancePlaceName.asState()

    // Debounced so typing does not fire a request per keystroke.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        searching = true
        kotlinx.coroutines.delay(400)
        results = weatherRepo.searchPlaces(query)
        searching = false
    }

    ViewWithActionBar(
        title = stringResource(R.string.pref_glance_place),
        largeTitle = true,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = current.ifBlank { stringResource(R.string.pref_glance_place_none) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.pref_glance_place_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (searching) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }

            LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
                items(results) { place ->
                    Card(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    prefs.glancePlaceName.setValue(place.name)
                                    prefs.glancePlaceCoords.setValue(
                                        "${place.latitude},${place.longitude}"
                                    )
                                }
                                glanceHolder.refreshIfStale()
                                navController.popBackStack()
                            }
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }

            if (current.isNotBlank()) {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            prefs.glancePlaceName.setValue("")
                            prefs.glancePlaceCoords.setValue("")
                        }
                    }
                }) {
                    Text(stringResource(R.string.action_reset))
                }
            }
        }
    }
}
