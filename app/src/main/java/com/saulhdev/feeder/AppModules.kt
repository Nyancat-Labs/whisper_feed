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

import android.widget.Toast
import androidx.work.WorkManager
import com.saulhdev.feeder.data.content.SyncAccount
import com.saulhdev.feeder.data.db.NeoFeedDb
import com.saulhdev.feeder.data.repository.ArticleRepository
import com.saulhdev.feeder.data.repository.SourcesRepository
import com.saulhdev.feeder.manager.backup.BackupStore
import com.saulhdev.feeder.manager.bookmarks.FeedDiscovery
import com.saulhdev.feeder.manager.glance.GlanceStateHolder
import com.saulhdev.feeder.manager.glance.WeatherRepository
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.manager.sync.SyncRestClient
import com.saulhdev.feeder.manager.sync.service.GoogleReaderService
import com.saulhdev.feeder.manager.sync.service.LocalRssService
import com.saulhdev.feeder.manager.sync.service.RssServiceDispatcher
import com.saulhdev.feeder.utils.ApplicationCoroutineScope
import com.saulhdev.feeder.utils.extensions.ToastMaker
import com.saulhdev.feeder.viewmodels.AccountViewModel
import com.saulhdev.feeder.viewmodels.ArticleListViewModel
import com.saulhdev.feeder.viewmodels.ArticleViewModel
import com.saulhdev.feeder.viewmodels.BookmarkImportViewModel
import com.saulhdev.feeder.viewmodels.BrokenFeedsViewModel
import com.saulhdev.feeder.viewmodels.InsecureFeedsViewModel
import com.saulhdev.feeder.viewmodels.LearnedViewModel
import com.saulhdev.feeder.viewmodels.StatisticsViewModel
import com.saulhdev.feeder.viewmodels.SearchFeedViewModel
import com.saulhdev.feeder.viewmodels.SortFilterViewModel
import com.saulhdev.feeder.viewmodels.SourceEditViewModel
import com.saulhdev.feeder.viewmodels.SourceListViewModel
import com.saulhdev.feeder.viewmodels.SuggestionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/*
 * The dependency graph, as four modules.
 *
 * These were instance properties of [NeoApp], which carried a standing
 * `// TODO Move to its class` and one concrete cost: nothing outside an
 * running app could look at them. "Feeds that stopped working" shipped with
 * FeedParser missing from the graph entirely, and the only thing that could
 * discover that was a reader opening the screen and watching the app die.
 *
 * Top-level, they can be verified by a unit test — see AppModulesTest, which
 * walks every constructor-injected definition and fails the build on a type
 * nothing provides.
 *
 * The Application is reached through `androidContext()` rather than a captured
 * `this@NeoApp`. Both resolve to the same object; only the first can be
 * declared outside the Application class.
 */

/**
 * View models.
 *
 * Every one is declared with `viewModelOf`, which hands the constructor to
 * Koin as a reference rather than a lambda — which is exactly what lets the
 * verification test see what each one needs.
 */
val modelModule = module {
    viewModelOf(::SourceEditViewModel)
    viewModelOf(::LearnedViewModel)
    viewModelOf(::StatisticsViewModel)
    viewModelOf(::AccountViewModel)
    viewModelOf(::SearchFeedViewModel)
    viewModelOf(::ArticleListViewModel)
    viewModelOf(::SourceListViewModel)
    viewModelOf(::ArticleViewModel)
    viewModelOf(::SortFilterViewModel)
    viewModelOf(::SuggestionsViewModel)
    viewModelOf(::BookmarkImportViewModel)
    viewModelOf(::BrokenFeedsViewModel)
    viewModelOf(::InsecureFeedsViewModel)
}

/** The database, the repositories, and everything that talks to a network. */
val dataModule = module {
    single<NeoFeedDb> { NeoFeedDb.getInstance(androidContext()) }
    single { get<NeoFeedDb>().feedArticleDao() }
    single { get<NeoFeedDb>().feedSourceDao() }
    singleOf(::ArticleRepository)
    singleOf(::SourcesRepository)
    singleOf(::SyncRestClient)
    // Two things resolve this from Koin — FeedDiscovery and DiscoveryWorker —
    // and nothing ever registered it, so "Feeds that stopped working" crashed
    // the app the moment it was opened and the suggestion worker threw on
    // every run. A singleton rather than a factory because each instance
    // builds its own OkHttpClient, with its own connection and thread pools,
    // for a class that holds no other state.
    singleOf(::FeedParser)
    singleOf(::FeedDiscovery)
    single { SyncAccount(androidContext()) }
    single { BackupStore(androidContext(), get(), get()) }
    single { LocalRssService(androidContext(), get()) }
    single {
        RssServiceDispatcher(
            account = get(),
            local = get(),
            googleReaderFactory = {
                GoogleReaderService(androidContext(), get(), get(), get())
            },
        )
    }
    singleOf(::WeatherRepository)
    singleOf(::GlanceStateHolder)
}

/** Android's own services, and the app-wide scope. */
val coreModule = module {
    single { androidContext().contentResolver }
    single { WorkManager.getInstance(androidContext()) }
    single<ToastMaker> {
        object : ToastMaker {
            override suspend fun makeToast(text: String) = withContext(Dispatchers.Main) {
                Toast.makeText(get(), text, Toast.LENGTH_SHORT).show()
            }

            override suspend fun makeToast(resId: Int) = withContext(Dispatchers.Main) {
                Toast.makeText(get(), resId, Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Was an NeoApp property registered by capture. Built here instead, so
    // there is one of these and one place it comes from; NeoApp now asks for
    // it like everything else does.
    singleOf(::ApplicationCoroutineScope)
    single<NeoApp> { androidApplication() as NeoApp }
}
