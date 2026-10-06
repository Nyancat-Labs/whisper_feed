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

import com.saulhdev.feeder.utils.SyncLog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.mutableStateMapOf
import com.saulhdev.feeder.R
import com.saulhdev.feeder.data.StarterSource
import com.saulhdev.feeder.data.StarterSources
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import com.saulhdev.feeder.data.content.FeedPreferences
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.backup.BackupStore
import com.saulhdev.feeder.manager.backup.BackupWorker
import kotlinx.coroutines.withContext
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.ui.components.starterSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The first screen anybody sees.
 *
 * Three panes and a button. It says what the app is for and gets out of the
 * way — the controls are taught by the tour, standing on the real screen,
 * because a picture of a button somebody has not got to yet teaches nothing.
 */
@Composable
fun OnboardingPage(
    sourcesRepo: SourcesRepository = koinInject(),
    syncClient: SyncRestClient = koinInject(),
    backupStore: BackupStore = koinInject(),
    prefs: FeedPreferences = koinInject(),
    onDone: () -> Unit,
) {
    // The starter list is a step of its own rather than a fourth pane. It
    // needs to scroll, and a scrolling list inside a horizontal pager fights
    // the pager for every drag — which on the reader's first minute with the
    // app would read as the app being broken rather than as a gesture clash.
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        StarterSourcesStep(
            sourcesRepo = sourcesRepo,
            syncClient = syncClient,
            backupStore = backupStore,
            prefs = prefs,
            onDone = onDone,
        )
        return
    }
    val panes = listOf(
        OnboardingPane(
            titleId = R.string.onboarding_welcome_title,
            bodyId = R.string.onboarding_welcome_body,
            showMark = true,
        ),
        OnboardingPane(
            titleId = R.string.onboarding_sources_title,
            bodyId = R.string.onboarding_sources_body,
        ),
        OnboardingPane(
            titleId = R.string.onboarding_calm_title,
            bodyId = R.string.onboarding_calm_body,
        ),
    )
    val pagerState = rememberPagerState { panes.size }
    val scope = rememberCoroutineScope()
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Getting out of onboarding must never need a forward tap. Back on the
    // first pane leaves it entirely rather than trapping the reader in a
    // three-pane carousel on their first minute with the app.
    BackHandler {
        if (pagerState.currentPage == 0) onDone()
        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Picked by what actually got painted rather than by the theme
        // setting, the same way articlePlaceholder() does: the theme has three
        // values and one of them is pure black.
        Image(
            painter = painterResource(
                if (dark) R.drawable.onboarding_bg_dark else R.drawable.onboarding_bg_light
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // The artwork is calm at the top and busy lower down, and text over the
        // busy part is unreadable at some crops. A wash under the lower half
        // costs nothing and means the copy is legible on every screen shape.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                        1f to MaterialTheme.colorScheme.surface,
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(24.dp),
        ) {
            Spacer(Modifier.weight(1f))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val pane = panes[page]
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (pane.showMark) {
                        Image(
                            painter = painterResource(
                                if (dark) R.drawable.lockup_horizontal_notag_dark
                                else R.drawable.lockup_horizontal_notag_light
                            ),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(72.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                    Text(
                        text = stringResource(pane.titleId),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(pane.bodyId),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                repeat(panes.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            val last = pagerState.currentPage == panes.lastIndex
            Button(
                onClick = {
                    if (last) picking = true
                    else scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (last) R.string.onboarding_get_started else R.string.tour_next
                    )
                )
            }

            // Reachable from every pane, not only the last. Somebody who
            // already knows what an RSS reader is should not have to swipe
            // through three panes to be let in.
            TextButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.tour_skip))
            }
        }
    }
}

private data class OnboardingPane(
    val titleId: Int,
    val bodyId: Int,
    val showMark: Boolean = false,
)

/**
 * The starter feeds, offered.
 *
 * Everything is ticked when the screen opens except a regional feed that does
 * not match where the phone says it is — so the default is a working feed
 * rather than an empty one, and the reader can see every name they are
 * agreeing to before agreeing to it.
 */
@Composable
private fun StarterSourcesStep(
    sourcesRepo: SourcesRepository,
    syncClient: SyncRestClient,
    backupStore: BackupStore,
    prefs: FeedPreferences,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var restoring by remember { mutableStateOf(false) }
    // Seeded where the map is created rather than in a second remember whose
    // only job was the side effect. A remember that returns Unit is not
    // guaranteed to run — Compose is free to skip it — so the ticks could
    // simply not appear.
    val selected = remember {
        mutableStateMapOf<String, Boolean>().apply {
            StarterSources.defaultSelection().forEach { put(it, true) }
        }
    }
    val chosen = StarterSources.ALL.filter { selected[it.url] == true }
    val context = LocalContext.current

    val subscribe = {
        val picked: List<StarterSource> = chosen
        // Left before the network finishes. Subscribing is the part the reader
        // asked for; waiting for nine feeds to fetch would hold them on an
        // onboarding screen watching a spinner, and the feed fills in behind
        // them either way.
        scope.launch(Dispatchers.IO) {
            StarterSources.subscribe(sourcesRepo, picked) { context.getString(it) }
            syncClient.syncAllFeeds(origin = SyncLog.ORIGIN_ONBOARDING)
        }
        onDone()
    }

    // A folder, not a file. Somebody restoring has one backup folder holding
    // both files, and asking them to find each of the two in it would be
    // ceremony — on a fresh phone there are no settings to lose, which is the
    // only reason the settings screen keeps the two apart.
    val chooseBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        restoring = true
        scope.launch(Dispatchers.IO) {
            backupStore.remember(uri)
            val result = backupStore.restoreFolder(uri)
            withContext(Dispatchers.Main) {
                restoring = false
                when (result) {
                    is BackupStore.Result.FolderRestored -> {
                        // They have just told the app where their backups
                        // live. Asking the same question again on the settings
                        // screen later would be asking one already answered.
                        scope.launch(Dispatchers.IO) {
                            prefs.backupFolder.setValue(uri.toString())
                        }
                        BackupWorker.schedule(context, uri.toString())
                        // Their own list is back; a starter list would be nine
                        // feeds they did not ask for on top of the ones they
                        // spent years choosing.
                        onDone()
                    }

                    else -> Toast.makeText(
                        context,
                        context.getString(R.string.backup_nothing_found),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    // Opaque, because this is drawn over the live feed. The welcome panes
    // carry artwork and are solid by accident of that; this step had nothing
    // behind it, so the header, the glance row and the first article showed
    // through its text and the screen read as two screens at once.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(R.string.starter_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.starter_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Above the list rather than under it. A returning reader should
            // not have to scroll past nine feeds they do not want to find the
            // one thing on the screen that is for them.
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.starter_returning_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.starter_returning_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { chooseBackup.launch(null) },
                        enabled = !restoring,
                    ) {
                        Text(
                            stringResource(
                                if (restoring) R.string.starter_restoring
                                else R.string.starter_restore
                            )
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                starterSources(
                    sources = StarterSources.ALL,
                    selected = selected.filterValues { it }.keys,
                    onToggle = { source ->
                        selected[source.url] = selected[source.url] != true
                    },
                )
            }

            Button(
                onClick = subscribe,
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    pluralStringResource(R.plurals.starter_add, chosen.size, chosen.size)
                )
            }
            TextButton(
                onClick = onDone,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(stringResource(R.string.starter_none))
            }
        }
    }
}
