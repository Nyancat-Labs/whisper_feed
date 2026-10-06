/*
 * This file is part of Neo Feed
 * Copyright (c) 2022   Saul Henriquez <henriquez.saul@gmail.com>
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

package com.saulhdev.feeder.manager.sync

import com.saulhdev.feeder.utils.SyncLog
import com.saulhdev.feeder.data.db.ID_ALL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncRestClient() {
    /** A sync somebody asked for - pull to refresh. It ignores the sync switches. */
    suspend fun syncAllFeeds(origin: String = SyncLog.ORIGIN_PULL) = withContext(Dispatchers.IO) {
        requestFeedSync(feedId = ID_ALL, forceNetwork = true, origin = origin)
    }

    /**
     * A sync nobody asked for at this moment, which waits for what the
     * scheduled sync would wait for.
     *
     * The launcher panel started a full sync whenever it was created - which
     * is whenever the launcher shows the home screen after the process has
     * gone - and it went through [syncAllFeeds], so it ignored both switches.
     * With "Sync only while charging" on, the scheduled sync sat correctly at
     * its slot waiting for a charger while this synced anyway: at 06:41 as the
     * process started, and at 08:07 over mobile data after the phone came off
     * the car charger. The switch said one thing and the panel did another.
     */
    suspend fun syncAllFeedsWhenAllowed() = withContext(Dispatchers.IO) {
        requestAutomaticFeedSync()
    }
}