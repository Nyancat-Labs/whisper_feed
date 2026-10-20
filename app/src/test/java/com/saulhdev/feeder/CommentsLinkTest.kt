package com.saulhdev.feeder

import com.saulhdev.feeder.data.db.models.Article
import com.saulhdev.feeder.data.entity.JsonFeed
import com.saulhdev.feeder.manager.models.FeedParser
import com.saulhdev.feeder.ui.pages.commentsSite
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URL

/**
 * Where an article is discussed. On Hacker News the link is the story and the
 * discussion is only in `<comments>`, which Whisper used to throw away.
 */
class CommentsLinkTest {

    private fun parse(xml: String, type: String = "application/rss+xml"): JsonFeed = runBlocking {
        FeedParser().parseRssAtom(URL("https://example.org/feed"), xml.toResponseBody(type.toMediaType()), findIcon = false)
    }

    @Test
    fun `Hacker News's comments address is kept apart from the story's`() {
        val feed = parse(
            """<rss version="2.0"><channel><title>Hacker News</title><link>https://news.ycombinator.com/</link>
            <item><title>DeepSeek open-sourced its inference engine</title>
            <link>https://github.com/deepseek-ai/DeepInfer</link>
            <comments>https://news.ycombinator.com/item?id=50163277</comments>
            <description><![CDATA[<a href="https://news.ycombinator.com/item?id=50163277">Comments</a>]]></description>
            </item></channel></rss>"""
        )
        val item = feed.items!!.single()
        assertEquals("https://github.com/deepseek-ai/DeepInfer", item.url)
        assertEquals("https://news.ycombinator.com/item?id=50163277", item.comments_url)
    }

    @Test
    fun `an Atom replies link that is a web page counts, a feed of comments does not`() {
        val page = parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title><id>x</id><updated>2026-10-20T00:00:00Z</updated>
            <entry><title>Post</title><id>p1</id><updated>2026-10-20T00:00:00Z</updated>
            <link rel="alternate" type="text/html" href="https://blog.example/post"/>
            <link rel="replies" type="text/html" href="https://blog.example/post#comments"/>
            </entry></feed>""",
            "application/atom+xml",
        )
        assertEquals("https://blog.example/post#comments", page.items!!.single().comments_url)

        val feedOnly = parse(
            """<feed xmlns="http://www.w3.org/2005/Atom"><title>Blog</title><id>x</id><updated>2026-10-20T00:00:00Z</updated>
            <entry><title>Post</title><id>p1</id><updated>2026-10-20T00:00:00Z</updated>
            <link rel="alternate" type="text/html" href="https://blog.example/post"/>
            <link rel="replies" type="application/atom+xml" href="https://blog.example/post/comments.xml"/>
            </entry></feed>""",
            "application/atom+xml",
        )
        assertNull(feedOnly.items!!.single().comments_url)
    }

    @Test
    fun `no comments, nothing, and nothing that is not a web address`() {
        val feed = parse(
            """<rss version="2.0"><channel><title>News</title><link>https://example.org/</link>
            <item><title>A</title><link>https://example.org/a</link></item>
            <item><title>B</title><link>https://example.org/b</link><comments>javascript:alert(1)</comments></item>
            </channel></rss>"""
        )
        assertEquals(listOf(null, null), feed.items!!.map { it.comments_url })
    }

    @Test
    fun `the address reaches the article and survives the next sync`() {
        val feed = parse(
            """<rss version="2.0"><channel><title>Hacker News</title><link>https://news.ycombinator.com/</link>
            <item><title>Story</title><link>https://example.org/story</link>
            <comments>https://news.ycombinator.com/item?id=1</comments></item></channel></rss>"""
        )
        val item = feed.items!!.single()
        val stored = Article().updateFromParsedEntry(item, "g", feed, 1)
        assertEquals("https://news.ycombinator.com/item?id=1", stored.commentsLink)
        // A later fetch whose entry leaves it out keeps what was stored.
        val again = stored.updateFromParsedEntry(item.copy(comments_url = null), "g", feed, 1)
        assertEquals("https://news.ycombinator.com/item?id=1", again.commentsLink)
    }

    @Test
    fun `the button names the site it goes to`() {
        assertEquals("news.ycombinator.com", commentsSite("https://news.ycombinator.com/item?id=1"))
        assertEquals("arstechnica.com", commentsSite("https://www.arstechnica.com/a/#comments"))
    }
}
