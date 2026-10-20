/*
 * This file is part of Neo Feed
 * Copyright (c) 2025   Neo Feed Team
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package com.saulhdev.feeder.data.entity

data class JsonFeed(
    val version: String? = "https://jsonfeed.org/version/1.1",
    val title: String?,
    val home_page_url: String? = null,
    val feed_url: String? = null,
    val description: String? = null,
    val user_comment: String? = null,
    val next_url: String? = null,
    val icon: String? = null,
    val favicon: String? = null,
    val author: Author? = null,
    val expired: Boolean? = null,
    val hubs: List<Hub>? = null,
    val items: List<Item>?
)

data class Author(
    val name: String? = null,
    val url: String? = null,
    val avatar: String? = null
)

data class Item(
    val id: String?,
    val url: String? = null,
    val external_url: String? = null,
    val title: String? = null,
    val content_html: String? = null,
    val content_text: String? = null,
    val summary: String? = null,
    val image: String? = null,
    val banner_image: String? = null,
    val date_published: String? = null,
    val date_modified: String? = null,
    val author: Author? = null,
    val tags: List<String>? = null,
    val attachments: List<Attachment>? = null,
    /**
     * Where the item is discussed, when the feed says: RSS `<comments>`, or
     * an Atom link with `rel="replies"`. Not part of JSON Feed; Whisper's own.
     */
    val comments_url: String? = null,
)

data class Attachment(
    val url: String?,
    val mime_type: String? = null,
    val title: String? = null,
    val size_in_bytes: Long? = null,
    val duration_in_seconds: Long? = null
)

data class Hub(
    val type: String?,
    val url: String?
)
