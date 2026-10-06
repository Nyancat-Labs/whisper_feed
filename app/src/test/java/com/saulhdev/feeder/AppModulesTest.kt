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
package com.saulhdev.feeder

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkManager
import com.saulhdev.feeder.data.content.FeedPreferences.Companion.prefsModule
import com.saulhdev.feeder.manager.sync.service.RssServiceDispatcher
import org.junit.Test
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Checks that every constructor-injected dependency has something to come from.
 *
 * This exists because of a crash that reached testers. FeedParser was resolved
 * from Koin by two things and registered by none, so opening "Feeds that
 * stopped working" killed the process and the suggestion worker failed on
 * every run. Nothing in the build could see it: a missing definition is found
 * when somebody opens the screen, which is the worst possible moment and the
 * only moment there was.
 *
 * `verifyAll` walks the constructor of every definition declared with the
 * `singleOf` / `viewModelOf` DSL and fails if a parameter type has no
 * definition anywhere in the graph. It resolves nothing and touches no
 * Android framework class, so it runs as a plain JVM test.
 *
 * Its blind spot is worth stating: a definition written as a lambda —
 * `single { Thing(androidContext(), get()) }` — is opaque to it, because the
 * body is only a function until it runs. Those are checked by the app
 * starting. What this covers is the constructor-reference form, which is most
 * of the graph and all of the view models.
 */
class AppModulesTest {

    /**
     * Types the graph consumes but does not declare.
     *
     * [Context] and [Application] come from `androidContext()` at startup, and
     * the rest are declared in lambdas within these same modules, which
     * verification cannot see into. Listing one here asserts it is provided
     * some other way; the list is deliberately short, because every entry is a
     * thing this test has stopped checking.
     */
    private val providedAtRuntime = listOf(
        Context::class,
        Application::class,
        ContentResolver::class,
        WorkManager::class,
        DataStore::class,
        Preferences::class,
    )

    /**
     * Constructor parameters the graph does not supply by type.
     *
     * RssServiceDispatcher takes a factory function for the Google Reader
     * service, built in place by the definition's own lambda rather than
     * resolved. Verification reflects over the constructor either way and
     * cannot see that, so it is told here instead of being silenced with
     * `Function0` in the list above — which would have excused every missing
     * function dependency in the graph, not this one.
     */
    private val builtInPlace = injectedParameters(
        definition<RssServiceDispatcher>(Function0::class),
        // Registered as `single { SavedStateHandle() }`, whose no-argument
        // form is a default. Reflection sees the parameter, not the default.
        definition<SavedStateHandle>(Map::class),
    )

    @Test
    fun `every injected dependency is registered`() {
        // One module including the other four, not verifyAll over a list:
        // verifyAll checks each module against itself, so anything crossing a
        // module boundary — a repository in dataModule wanting the preferences
        // from prefsModule — reads as missing. Merging first asks the question
        // the app actually asks, which is whether the whole graph resolves.
        module { includes(coreModule, prefsModule, dataModule, modelModule) }
            .verify(extraTypes = providedAtRuntime, injections = builtInPlace)
    }
}
