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
package com.saulhdev.feeder.viewmodels

import android.app.Application
import androidx.work.WorkManager
import com.saulhdev.feeder.data.db.ID_ALL
import com.saulhdev.feeder.manager.sync.ACCOUNT_DETAIL_KEY
import com.saulhdev.feeder.manager.sync.ACCOUNT_PROBLEM_KEY
import com.saulhdev.feeder.manager.sync.oneTimeSyncName
import com.saulhdev.feeder.manager.sync.requestFeedSync
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.lifecycle.viewModelScope
import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.manager.sync.greader.AccountTally
import com.saulhdev.feeder.manager.sync.greader.AccountTallyStore
import com.saulhdev.feeder.manager.sync.greader.DayTotals
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderApi
import com.saulhdev.feeder.manager.sync.greader.GoogleReaderState
import com.saulhdev.feeder.manager.sync.greader.MissingFeed
import com.saulhdev.feeder.manager.sync.greader.serverCandidates
import com.saulhdev.feeder.manager.sync.greader.signInAtAny
import com.saulhdev.feeder.utils.extensions.NeoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import com.saulhdev.feeder.manager.sync.greader.AccountProblem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** What the account screen is doing, and what it has to say about it. */
data class AccountState(
    val signedIn: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val lastSync: Long = 0L,
    /** What the last sync did with the server, and which feeds it would not take. */
    val tally: AccountTally? = null,
    val today: DayTotals? = null,
    val notOnServer: List<MissingFeed> = emptyList(),
    /** The phone's secure storage could not be read, and the account was signed out. */
    val storeReset: Boolean = false,
    val busy: Boolean = false,
    /**
     * Set when something went wrong, cleared when the reader changes anything.
     *
     * A problem rather than a sentence, so the wording and its translations
     * stay on the screen that shows them. [detail] is whatever the server or
     * the exception said, kept for the cases with nothing better to offer.
     */
    val error: AccountProblem? = null,
    val detail: String? = null,
)

class AccountViewModel(
    /** The application, never a screen: this outlives the account screen's own. */
    private val app: Application,
    private val account: SyncAccount,
) : NeoViewModel() {
    private val ioScope = viewModelScope.plus(Dispatchers.IO)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    private fun read() = AccountState(
        signedIn = account.isSignedIn,
        serverUrl = account.serverUrl,
        username = account.username,
        lastSync = account.lastSync,
        tally = if (account.isSignedIn) AccountTallyStore.read(app) else null,
        today = if (account.isSignedIn) AccountTallyStore.today(app) else null,
        notOnServer = if (account.isSignedIn) GoogleReaderState.notOnServer(app) else emptyList(),
        storeReset = account.wasReset && !account.isSignedIn,
    )

    fun clearError() {
        _state.value = _state.value.copy(error = null, detail = null)
    }

    /**
     * Signs in, then immediately syncs.
     *
     * One action rather than two: somebody who has just typed a server address
     * and a password wants their feeds, and a screen that accepts the
     * credential and then sits there is asking them to guess what happens next.
     */
    fun signIn(server: String, username: String, password: String) {
        _state.value = _state.value.copy(busy = true, error = null)
        ioScope.launch {
            val (address, result) = signInAtAny(serverCandidates(server)) {
                GoogleReaderApi(it).signIn(username, password)
            }
            when (result) {
                is GoogleReaderApi.AuthResult.Success -> {
                    account.signIn(address, username, result.token)
                    _state.value = read().copy(busy = true)
                    syncNow()
                }

                is GoogleReaderApi.AuthResult.Failed ->
                    _state.value = _state.value.copy(
                        busy = false,
                        error = result.problem,
                        detail = result.detail,
                    )
            }
        }
    }

    fun signOut() {
        account.signOut()
        _state.value = read()
    }

    /**
     * Asks the sync worker for a full sync, as pull to refresh does.
     *
     * This ran on the screen itself, and Android takes the network from an
     * app the moment it leaves the screen: switching apps mid-sync came back
     * to "No server was found at that address", on Wi-Fi, with the server
     * fine. The worker runs it as a foreground task, which keeps the network,
     * writes the history line and says what went wrong; this screen watches.
     */
    fun syncNow() {
        _state.value = _state.value.copy(busy = true, error = null)
        requestFeedSync(feedId = ID_ALL, forceNetwork = true, origin = SyncLog.ORIGIN_ACCOUNT)
    }

    init {
        watchSync()
    }

    /**
     * Busy while a full sync is queued or running, and its problem once it
     * ends - including one started before this screen opened, so coming back
     * mid-sync shows the spinner rather than an idle screen. A sync that had
     * already ended before the screen saw it running says nothing: its
     * result is old news, and the history has it.
     */
    private fun watchSync() {
        viewModelScope.launch {
            var seenActive = false
            WorkManager.getInstance(app)
                .getWorkInfosForUniqueWorkFlow(oneTimeSyncName(ID_ALL))
                .map { works -> works.firstOrNull { !it.state.isFinished } ?: works.lastOrNull() }
                .distinctUntilChanged()
                .collect { info ->
                    if (info == null) return@collect
                    if (!info.state.isFinished) {
                        seenActive = true
                        _state.value = _state.value.copy(busy = true, error = null)
                        return@collect
                    }
                    if (!seenActive) return@collect
                    seenActive = false
                    val problem = info.outputData.getString(ACCOUNT_PROBLEM_KEY)
                        ?.let { name -> AccountProblem.entries.firstOrNull { it.name == name } }
                    if (problem == AccountProblem.SIGNED_OUT) account.signOut()
                    _state.value = read().copy(
                        error = problem,
                        detail = info.outputData.getString(ACCOUNT_DETAIL_KEY),
                    )
                }
        }
    }
}
