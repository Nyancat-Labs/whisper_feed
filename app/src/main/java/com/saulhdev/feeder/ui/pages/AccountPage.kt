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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.DayTotals
import com.saulhdev.feeder.manager.sync.greader.MissingFeed
import com.saulhdev.feeder.manager.sync.greader.MissingKind
import com.saulhdev.feeder.manager.sync.greader.normalisedServerUrl
import com.saulhdev.feeder.ui.components.ActionButton
import com.saulhdev.feeder.ui.components.OutlinedActionButton
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowCounterClockwise
import com.saulhdev.feeder.ui.icons.phosphor.Check
import com.saulhdev.feeder.ui.icons.phosphor.CloudArrowDown
import com.saulhdev.feeder.ui.icons.phosphor.Power
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.viewmodels.AccountViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Connecting Whisper to a feed service, or getting along without one.
 *
 * The copy does the work here. Somebody arriving at this screen has to
 * understand two things that no other reader has to explain: that an account
 * is *optional*, and that it is their own server rather than ours. Both are
 * unusual enough that saying nothing would read as an omission.
 */
@Composable
fun AccountPage(
    viewModel: AccountViewModel = koinNeoViewModel(),
) {
    val state by viewModel.state.collectAsState()

    var server by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    // Set when the address was corrected, so the screen can say so. The
    // correction itself is visible in the field; this is the sentence that
    // explains why it moved, which is the whole point of doing it here rather
    // than silently inside the HTTP client.
    var corrected by remember { mutableStateOf(false) }
    var username by remember(state.username) { mutableStateOf(state.username) }
    var password by remember { mutableStateOf("") }

    ViewWithActionBar(
        title = stringResource(R.string.pref_account),
        largeTitle = true,
        showBackButton = true,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.account_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (state.signedIn) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(state.username, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = state.serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (state.lastSync == 0L) {
                                stringResource(R.string.account_never_synced)
                            } else {
                                stringResource(
                                    R.string.account_last_sync,
                                    DateFormat.getDateTimeInstance(
                                        DateFormat.MEDIUM, DateFormat.SHORT
                                    ).format(Date(state.lastSync)),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                state.tally?.let { tally ->
                    item { SyncTally(tally, state.today, state.notOnServer) }
                }
                item {
                    ActionButton(
                        text = stringResource(R.string.account_sync_now),
                        icon = Phosphor.CloudArrowDown,
                        modifier = Modifier.fillMaxWidth(),
                        positive = true,
                        onClick = viewModel::syncNow,
                    )
                }
                item {
                    OutlinedActionButton(
                        text = stringResource(R.string.account_sign_out),
                        icon = Phosphor.Power,
                        modifier = Modifier.fillMaxWidth(),
                        positive = false,
                        onClick = viewModel::signOut,
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.account_sign_out_keeps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                if (state.storeReset) {
                    item {
                        Text(
                            text = stringResource(R.string.account_store_reset),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = server,
                        onValueChange = {
                            server = it
                            corrected = false
                            viewModel.clearError()
                        },
                        label = { Text(stringResource(R.string.account_server)) },
                        // One line, like the field: a placeholder that wrapped sat on two
                        // lines with the cursor alone on the first.
                        placeholder = {
                            Text(
                                "https://freshrss.example.com",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        supportingText = {
                            Text(
                                stringResource(
                                    if (corrected) R.string.account_server_upgraded
                                    else R.string.account_server_hint,
                                ),
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                        // Corrected as the reader leaves the field rather than
                        // as they type it, which would make `http` impossible
                        // to type and look like the app fighting them.
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focus ->
                                if (focus.isFocused || server.isBlank()) return@onFocusChanged
                                val fixed = normalisedServerUrl(server)
                                if (fixed != server) {
                                    corrected = true
                                    server = fixed
                                }
                            },
                    )
                }
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; viewModel.clearError() },
                        label = { Text(stringResource(R.string.account_username)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next,
                        ),
                        // Named for autofill, so a password manager puts the
                        // username here and the password below rather than
                        // guessing from the order of the fields.
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Username },
                    )
                }
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; viewModel.clearError() },
                        label = { Text(stringResource(R.string.account_password)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.large,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentType = ContentType.Password },
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.account_password_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    ActionButton(
                        text = stringResource(R.string.account_sign_in),
                        icon = Phosphor.Check,
                        modifier = Modifier.fillMaxWidth(),
                        positive = true,
                        // Corrected again on the way out, for the reader who
                        // types an address and presses Sign in without the
                        // field ever losing focus. Assigning it back means
                        // they still see what is being used.
                        onClick = {
                            val fixed = normalisedServerUrl(server)
                            corrected = fixed != server.trim()
                            server = fixed
                            viewModel.signIn(fixed, username.trim(), password)
                        },
                    )
                }
            }

            if (state.busy) {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                }
            }

            state.error?.let { problem ->
                item {
                    Text(
                        text = accountProblemText(problem, state.detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.account_what_syncs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/**
 * A sentence for a connection problem, with the next thing to try in it.
 *
 * Here rather than in the view model because it is wording, and wording gets
 * translated. What arrives from below is an [AccountProblem]; what the reader
 * gets is a sentence naming what went wrong and what to do about it, instead
 * of the exception text the screen used to print.
 *
 * Three of them carry the original message. There is genuinely nothing better
 * to say about a 502 than that the server said 502, and throwing that away to
 * look tidy would take the one fact worth having.
 */
@Composable
private fun accountProblemText(problem: AccountProblem, detail: String?): String = when (problem) {
    AccountProblem.BAD_ADDRESS -> stringResource(R.string.account_problem_bad_address)
    AccountProblem.CERTIFICATE -> stringResource(R.string.account_problem_certificate)
    AccountProblem.HOST_NOT_FOUND -> stringResource(R.string.account_problem_host_not_found)
    AccountProblem.REFUSED -> stringResource(R.string.account_problem_refused)
    AccountProblem.TIMEOUT -> stringResource(R.string.account_problem_timeout)
    AccountProblem.CLEARTEXT -> stringResource(R.string.account_problem_cleartext)
    AccountProblem.CREDENTIALS -> stringResource(R.string.account_problem_credentials)
    AccountProblem.NOT_FOUND -> stringResource(R.string.account_problem_not_found)
    AccountProblem.NO_TOKEN -> stringResource(R.string.account_problem_no_token)
    AccountProblem.SIGNED_OUT -> stringResource(R.string.account_problem_signed_out)
    AccountProblem.MOVED -> stringResource(R.string.account_problem_moved, detail.orEmpty())

    AccountProblem.SERVER_ERROR ->
        stringResource(R.string.account_problem_server_error, detail.orEmpty())

    AccountProblem.UNKNOWN ->
        if (detail.isNullOrBlank()) stringResource(R.string.account_problem_unknown_bare)
        else stringResource(R.string.account_problem_unknown, detail)
}

/**
 * What the last sync did with the server, line by line: the answer to "did it
 * all get there?" without a shell on the server. Every line is shown with its
 * zeros, because "0 sent" is itself the confirmation.
 */
@Composable
private fun SyncTally(tally: AccountTally, today: DayTotals?, notOnServer: List<MissingFeed>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.account_tally_title),
            style = MaterialTheme.typography.titleSmall,
        )
        val lines = listOfNotNull(
            stringResource(R.string.account_tally_feeds, tally.serverFeeds),
            if (tally.feedsSent + tally.feedsRemoved + tally.feedsAdded > 0) {
                stringResource(R.string.account_tally_feed_changes, tally.feedsSent, tally.feedsRemoved, tally.feedsAdded)
            } else null,
            stringResource(R.string.account_tally_matched, tally.matched),
            stringResource(R.string.account_tally_sent, tally.readSent, tally.unreadSent, tally.savedSent, tally.unsavedSent),
            if (tally.changesKept > 0) stringResource(R.string.account_tally_kept, tally.changesKept) else null,
            stringResource(R.string.account_tally_received, tally.readHere, tally.unreadHere, tally.savedHere),
        )
        lines.forEach { TallyLine(it) }
        // Whenever there has been a sync today, zeros and all: hidden until
        // something moved, it looked like a feature that had not arrived.
        today?.let { day ->
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.account_today_title), style = MaterialTheme.typography.titleSmall)
                if (day.feedsChanged) {
                    TallyLine(stringResource(R.string.account_tally_feed_changes, day.feedsSent, day.feedsRemoved, day.feedsAdded))
                }
                TallyLine(stringResource(R.string.account_tally_sent, day.readSent, day.unreadSent, day.savedSent, day.unsavedSent))
                TallyLine(stringResource(R.string.account_tally_received, day.readHere, day.unreadHere, day.savedHere))
            }
        }
        MissingGroup(
            feeds = notOnServer.filter { it.kind == MissingKind.REFUSED },
            heading = R.string.account_phone_only_title,
            explanation = R.string.account_phone_only_explained,
            footnote = R.string.account_phone_only_admin,
        )
        MissingGroup(
            feeds = notOnServer.filter { it.kind == MissingKind.REMOVED_THERE },
            heading = R.string.account_removed_there_title,
            explanation = R.string.account_removed_there_explained,
        )
        MissingGroup(
            feeds = notOnServer.filter { it.kind == MissingKind.NO_WRITE },
            heading = R.string.account_no_write_title,
            explanation = R.string.account_no_write_explained,
        )
    }
}

/**
 * Feeds the server lacks, for one reason, said once for all of them.
 *
 * Not in the error colour. A feed the site keeps from the server is not
 * something the reader did wrong or can fix from here: it keeps updating on
 * the phone, and the only thing it does not do is sync.
 */
@Composable
private fun MissingGroup(
    feeds: List<MissingFeed>,
    heading: Int,
    explanation: Int,
    footnote: Int? = null,
) {
    if (feeds.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(
            stringResource(heading, feeds.size),
            style = MaterialTheme.typography.titleSmall,
        )
        TallyLine(stringResource(explanation))
        feeds.forEach { Text("\u2022 ${it.title}", style = MaterialTheme.typography.bodySmall) }
        footnote?.let { TallyLine(stringResource(it)) }
    }
}

@Composable
private fun TallyLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
