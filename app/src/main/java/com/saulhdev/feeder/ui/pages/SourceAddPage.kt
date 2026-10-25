/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Neo Feed Team <saulhdev@hotmail.com>
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

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.saulhdev.feeder.data.FeedLibrary
import com.saulhdev.feeder.utils.FeedNameSearch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.utils.extensions.StableHolder
import com.saulhdev.feeder.utils.extensions.interceptKey
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.utils.extensions.safeSemantics
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.navigation.LocalNavController
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.Info
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import com.saulhdev.feeder.viewmodels.SearchFeedViewModel
import com.saulhdev.feeder.viewmodels.SearchResult
import com.saulhdev.feeder.viewmodels.SourceListViewModel
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import java.net.MalformedURLException
import java.net.URL

@Composable
fun SourceAddPage(
    searchFeedViewModel: SearchFeedViewModel = koinNeoViewModel(),
    sourcesViewModel: SourceListViewModel = koinNeoViewModel(),
    /** A name or address to look for at once, from the Sources search. */
    query: String = "",
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val navController = LocalNavController.current
    val title = stringResource(id = R.string.add_rss)

    var results by rememberSaveable {
        mutableStateOf(listOf<SearchResult>())
    }

    // Filing a feed used to mean adding it, leaving, finding it in the source
    // list and opening the editor — four steps to answer a question the reader
    // was already thinking about when they added it.
    val sourcesState by sourcesViewModel.state.collectAsState()
    val filed = remember { mutableStateMapOf<String, String>() }

    // "Subscribed already" and "just subscribed" both read as Added, which
    // meant searching for a feed you already follow looked exactly like
    // successfully adding it. The search already knows the difference — it
    // checks the database per candidate — so the screen only had to say it.
    val addedNow = remember { mutableStateListOf<String>() }

    // Subscribing happens when a result is tapped. It used to happen when the
    // screen was *left*: every feed the search turned up was added, whether or
    // not the user wanted any of them, and tapping one did nothing at all. On a
    // site publishing a feed per category that silently added a dozen.
    ViewWithActionBar(
        title = title,
        onBackAction = { navController.popBackStack() }
    ) { paddingValues ->
        var currentlySearching by rememberSaveable {
            mutableStateOf(false)
        }
        var errors by rememberSaveable {
            mutableStateOf(listOf<SearchResult>())
        }
        var feedUrl by remember { mutableStateOf(query) }

        // An address is searched as one; anything else as a name, through the
        // built-in library and the addresses the name most likely has. See
        // FeedNameSearch: no search service is asked.
        val search: (String) -> Unit = { text ->
            results = emptyList()
            errors = emptyList()
            currentlySearching = true
            coroutineScope.launch {
                val found = if (FeedNameSearch.looksLikeAddress(text)) {
                    searchFeedViewModel.searchForFeeds(sloppyLinkToStrictURLNoThrows(text.trim()))
                } else {
                    searchFeedViewModel.searchByName(
                        name = text,
                        country = phoneCountry(context),
                        library = FeedLibrary.allFeeds(context).map { it.second },
                    )
                }
                found
                    .onCompletion { currentlySearching = false }
                    .collect {
                        if (it.isError) errors = errors + it else results = results + it
                    }
            }
        }
        // Arrived with something to look for: look for it.
        LaunchedEffect(query) {
            if (query.isNotBlank() && results.isEmpty()) search(query)
        }

        Column(
            modifier = Modifier.padding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding(), start = 8.dp, end = 8.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AddFeedView(
                feedUrl = feedUrl,
                onUrlChanged = {
                    feedUrl = it
                },
                onSearch = search,
                results = StableHolder(results),
                errors = if (currentlySearching) StableHolder(emptyList()) else StableHolder(errors),
                currentlySearching = currentlySearching,
                onClick = { result ->
                    if (!result.alreadyAdded) {
                        sourcesViewModel.addFeed(result)
                        addedNow += result.url
                        results = results.map {
                            if (it.url == result.url) it.copy(alreadyAdded = true) else it
                        }
                    }
                },
                allTags = sourcesState.allTags,
                addedNow = addedNow,
                filedAs = filed,
                onFile = { result, tag ->
                    filed[result.url] = tag
                    sourcesViewModel.setCategory(result.url, tag)
                }
            )
        }
    }
}

@Composable
fun AddFeedView(
    feedUrl: String = "",
    onUrlChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    results: StableHolder<List<SearchResult>>,
    errors: StableHolder<List<SearchResult>>,
    currentlySearching: Boolean,
    onClick: (SearchResult) -> Unit,
    allTags: List<String> = emptyList(),
    addedNow: List<String> = emptyList(),
    filedAs: Map<String, String> = emptyMap(),
    onFile: (SearchResult, String) -> Unit = { _, _ -> },
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val scrollState = rememberScrollState()
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(8.dp)
        ) {
            SearchFeedUI(
                feedUrl = feedUrl,
                onUrlChanged = onUrlChanged,
                onSearch = onSearch,
                focusManager = focusManager,
                keyboardController = keyboardController
            )
            SearchResult(
                results = results,
                errors = errors,
                currentlySearching = currentlySearching,
                onClick = onClick,
                allTags = allTags,
                addedNow = addedNow,
                filedAs = filedAs,
                onFile = onFile,
            )
        }
    }
}

@Composable
fun SearchFeedUI(
    feedUrl: String,
    onUrlChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    focusManager: FocusManager,
    keyboardController: SoftwareKeyboardController?
) {
    // A name is as good as an address now; only an address can be wrong.
    val isNotValidUrl by remember(feedUrl) {
        derivedStateOf {
            FeedNameSearch.looksLikeAddress(feedUrl) && isNotValidUrl(feedUrl)
        }
    }
    val isValidUrl by remember(feedUrl) {
        derivedStateOf {
            feedUrl.isNotBlank() && !isNotValidUrl
        }
    }
    OutlinedTextField(
        value = feedUrl,
        onValueChange = onUrlChanged,
        modifier = Modifier
            .fillMaxWidth()
            .interceptKey(Key.Enter) {
                if (isValidUrl) {
                    onSearch(feedUrl)
                    keyboardController?.hide()
                }
            }
            .interceptKey(Key.Escape) {
                focusManager.clearFocus()
            },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12F),
        ),

        isError = isNotValidUrl,
        keyboardOptions = KeyboardOptions.Default.copy(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            // Text, not Uri: a name has spaces, and the address keyboard on
            // many phones has no space bar.
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                if (isValidUrl) {
                    onSearch(feedUrl)
                    keyboardController?.hide()
                }
            }
        ),
        shape = MaterialTheme.shapes.medium,
        label = { Text(text = stringResource(id = R.string.add_feed_search_hint)) }
    )

    OutlinedButton(
        enabled = isValidUrl,
        onClick = {
            if (isValidUrl) {
                onSearch(feedUrl)
                focusManager.clearFocus()
            }
        }
    ) {
        Text(
            stringResource(android.R.string.search_go)
        )
    }
}

@Composable
fun SearchResult(
    results: StableHolder<List<SearchResult>>,
    errors: StableHolder<List<SearchResult>>,
    currentlySearching: Boolean,
    onClick: (SearchResult) -> Unit,
    allTags: List<String> = emptyList(),
    addedNow: List<String> = emptyList(),
    filedAs: Map<String, String> = emptyMap(),
    onFile: (SearchResult, String) -> Unit = { _, _ -> },
) {
    if (results.item.isEmpty()) {
        for (error in errors.item) {
            val title = stringResource(
                R.string.failed_to_parse,
                error.url
            )
            ErrorResultView(
                title = title,
                description = error.description
            )
        }
    }
    for (result in results.item) {
        SearchResultView(
            title = result.title,
            url = result.url,
            description = result.description,
            alreadyAdded = result.alreadyAdded,
            justAdded = result.url in addedNow,
            allTags = allTags,
            filedAs = filedAs[result.url],
            onFile = { onFile(result, it) },
        ) {
            onClick(result)
        }
    }
    AnimatedVisibility(visible = currentlySearching) {
        SearchingIndicator()
    }
}

@Composable
fun SearchingIndicator() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .safeSemantics {
                testTag = "searchingIndicator"
            }
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun SearchResultView(
    title: String,
    url: String,
    description: String,
    alreadyAdded: Boolean = false,
    justAdded: Boolean = false,
    allTags: List<String> = emptyList(),
    filedAs: String? = null,
    onFile: (String) -> Unit = {},
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = !alreadyAdded,
        modifier = Modifier
            .fillMaxWidth()
            .safeSemantics {
                testTag = "searchResult"
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (alreadyAdded) {
                    // A tick in the primary colour is the language of "that
                    // worked". A feed the reader already had did not just
                    // work — nothing happened — so it says so plainly and in
                    // the quieter colour.
                    val tint = if (justAdded) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(
                        imageVector = if (justAdded) Phosphor.Check else Phosphor.Info,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(
                            if (justAdded) R.string.already_subscribed
                            else R.string.feed_already_yours
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = tint,
                    )
                }
            }
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description.isNotBlank()) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Only after it is in. Asking before would put a decision between
            // the reader and the one thing they came here to do, and a feed
            // with no category is a perfectly good feed — this is an offer at
            // the moment it is easiest to accept, not a required field.
            if (justAdded) {
                CategoryPicker(
                    allTags = allTags,
                    chosen = filedAs,
                    onChoose = onFile,
                )
            }
        }
    }
}

/**
 * Where to file the feed that has just been added.
 *
 * The categories that already exist, plus room to type a new one. Choosing
 * writes it straight away rather than waiting for a Save: there is nothing
 * else on this screen to save, and a button that only sometimes appears is
 * worse than no button.
 */
@Composable
private fun CategoryPicker(
    allTags: List<String>,
    chosen: String?,
    onChoose: (String) -> Unit,
) {
    var typed by remember { mutableStateOf("") }
    val named = remember(allTags) { allTags.filter { it.isNotBlank() }.sorted() }

    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.add_feed_category),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        named.forEach { tag ->
            FilterChip(
                selected = tag == chosen,
                onClick = { onChoose(tag) },
                label = { Text(tag) },
            )
        }
    }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        label = { Text(stringResource(R.string.sources_add_tag)) },
        keyboardOptions = KeyboardOptions.Default.copy(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                // A category typed but never committed is still a category the
                // reader asked for; the same trap the edit screen already
                // guards against.
                val value = typed.trim()
                if (value.isNotEmpty()) onChoose(value)
            }
        ),
        trailingIcon = {
            val value = typed.trim()
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChoose(value) }) {
                    Icon(
                        imageVector = Phosphor.Check,
                        contentDescription = stringResource(R.string.sources_add_tag),
                    )
                }
            }
        },
    )
}

@Composable
fun ErrorResultView(
    title: String,
    description: String,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .safeSemantics {
                testTag = "errorResult"
            }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall
                    .copy(color = MaterialTheme.colorScheme.error)
            )
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun isValidUrl(url: String): Boolean {
    if (url.isBlank()) {
        return false
    }
    return try {
        try {
            URL(url)
            true
        } catch (_: MalformedURLException) {
            URL("http://$url")
            true
        }
    } catch (e: Exception) {
        false
    }
}

private fun isNotValidUrl(url: String) = !isValidUrl(url)

/**
 * The phone's country, for which domain a name is tried on first: the
 * network's, then the SIM's, then the language setting's. An Irish reader
 * whose phone is set to English (UK) is still in Ireland.
 */
private fun phoneCountry(context: android.content.Context): String {
    val telephony = context.getSystemService(android.content.Context.TELEPHONY_SERVICE)
        as? android.telephony.TelephonyManager
    return listOfNotNull(
        runCatching { telephony?.networkCountryIso }.getOrNull(),
        runCatching { telephony?.simCountryIso }.getOrNull(),
        context.resources.configuration.locales[0].country,
    ).firstOrNull { it.isNotBlank() }.orEmpty()
}

@Preview
@Composable
fun SourceAddPagePreview() {
    SourceAddPage()
}