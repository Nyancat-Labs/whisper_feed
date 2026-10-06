/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
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

package com.saulhdev.feeder.ui.theme

/**
 * The colour roles the overlay's View code reads, and whether the scheme is
 * light, by index into a
 * SparseIntArray. The values come from [OverlayTheme.toCardColors], from the
 * same scheme Compose uses.
 */
object CardTheme {
    enum class Colors {
        CARD_BG,
        TEXT_COLOR_PRIMARY,
        TEXT_COLOR_SECONDARY,
        ACCENT_COLOR,
        OVERLAY_BG,
        IS_LIGHT
    }
}
