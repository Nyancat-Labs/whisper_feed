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
package com.saulhdev.feeder.utils

/**
 * The reader's own order for categories.
 *
 * Categories are text on a source, so the database can only hand them back
 * alphabetically. The order is kept beside them, as the names in sequence,
 * and every list of categories is put through [arrange] on its way out, so
 * the chips on the sources screen and in both feeds agree.
 *
 * Stored one name to a line. A category cannot hold a comma, since a source's
 * categories are a comma-separated list, but it could in principle hold
 * anything else; a line break is the one thing no category field accepts.
 */
object CategoryOrder {

    private const val SEPARATOR = "\n"

    fun decode(stored: String): List<String> =
        stored.split(SEPARATOR).map(String::trim).filter(String::isNotEmpty).distinct()

    fun encode(order: List<String>): String = order.joinToString(SEPARATOR)

    /**
     * [tags] in the reader's order. Those they have placed come first, as
     * placed; any they have not, a category added since, follow in the order
     * they arrived, which is alphabetical. A name in the order that no source
     * carries any more is simply not there.
     */
    fun arrange(tags: List<String>, order: List<String>): List<String> {
        if (order.isEmpty()) return tags
        val present = tags.toSet()
        val placed = order.filter { it in present }
        val placedSet = placed.toSet()
        return placed + tags.filter { it !in placedSet }
    }

    /** [list] with the item at [from] moved to [to]. Out of range is no move. */
    fun move(list: List<String>, from: Int, to: Int): List<String> {
        if (from !in list.indices || to !in list.indices || from == to) return list
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** A rename keeps its place; renamed onto a name already placed, the two merge there. */
    fun renamed(order: List<String>, from: String, to: String): List<String> =
        order.map { if (it == from) to else it }.distinct()
}
