package com.saulhdev.feeder.manager.models

import com.saulhdev.feeder.data.db.dao.FeedSourceDao
import com.saulhdev.feeder.data.db.models.Feed
import com.saulhdev.feeder.utils.isSameFeedUrl
import com.saulhdev.feeder.utils.preferringHttps
import com.saulhdev.feeder.utils.sloppyLinkToStrictURLNoThrows
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.net.URL

class SourceToRoom() : ParserToDatabase<Feed>, KoinComponent {
    private val dao: FeedSourceDao by inject()

    override suspend fun getItem(id: String): Feed? =
        findExisting(sloppyLinkToStrictURLNoThrows(id))

    /**
     * Writes one source from an OPML import.
     *
     * An import must not double what is already subscribed, and matching on the
     * URL string alone is not enough for that: an export from another reader
     * routinely differs from what is stored by a `www.`, a trailing slash or a
     * scheme, all of which reach the same feed.
     *
     * When the feed is already here, the stored row wins and only its
     * categories grow. That is the opposite of what this did before — it called
     * `update(item)` with the *imported* row, which carried `id = ID_UNSET`, so
     * the update matched no primary key and silently did nothing at all, and
     * would have overwritten the user's own title and settings if it had
     * worked.
     */
    override suspend fun saveItem(item: Feed) {
        // Recorded over https, like every other way a source arrives — see
        // SourcesRepository.insertSource, whose reasoning this follows. This
        // is the one import path that writes to the DAO directly rather than
        // through the repository, so it is also the one that would have gone
        // on storing whatever scheme an exported OPML happened to carry.
        //
        // Converted before findExisting, not after: the check has to be
        // looking at the address that will be written, or an import can
        // insert a row that collides with one it just decided was different.
        val source = item.copy(url = item.url.preferringHttps())

        val existing = findExisting(source.url)
        if (existing == null) {
            dao.insert(source)
            return
        }

        val merged = (existing.tags + source.tags).distinct().joinToString(",")
        if (merged != existing.tag) {
            dao.update(existing.copy(tag = merged))
        }
    }

    private suspend fun findExisting(url: URL): Feed? =
        dao.getFeedByURL(url)
            ?: dao.loadAllFeeds().firstOrNull { isSameFeedUrl(it.url, url) }
}
