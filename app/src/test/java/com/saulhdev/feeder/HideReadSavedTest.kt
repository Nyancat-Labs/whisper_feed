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

import com.saulhdev.feeder.viewmodels.hiddenAsRead
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Hide them", and saved articles: asked on 17 October 2026 that they be only
 * in Bookmarks once read, rather than left in the feed.
 */
class HideReadSavedTest {

    @Test
    fun `a read article goes, saved or not`() {
        // Saved is not an argument: Bookmarks still has it.
        assertTrue(hiddenAsRead(hideRead = true, readAt = 1L, pinned = false))
    }

    @Test
    fun `pinned stays, and unread stays`() {
        assertFalse(hiddenAsRead(hideRead = true, readAt = 1L, pinned = true))
        assertFalse(hiddenAsRead(hideRead = true, readAt = 0L, pinned = false))
    }

    @Test
    fun `with read articles kept, nothing goes`() {
        assertFalse(hiddenAsRead(hideRead = false, readAt = 1L, pinned = false))
    }
}
