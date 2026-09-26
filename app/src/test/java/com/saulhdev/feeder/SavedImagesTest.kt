package com.saulhdev.feeder

import com.saulhdev.feeder.utils.SavedImages
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files

/**
 * A saved article's pictures, kept for offline reading. Against a real HTTP
 * server on this machine; the app's own client refuses local addresses, so a
 * plain one stands in for it here.
 */
class SavedImagesTest {

    private lateinit var server: HttpServer
    private lateinit var dir: File
    private val hits = mutableListOf<String>()
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before
    fun start() {
        dir = Files.createTempDirectory("saved").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            hits += ex.requestURI.path
            if (ex.requestURI.path == "/cut.jpg") {
                // Says a thousand bytes, sends ten, and hangs up: a signal
                // lost halfway through a picture.
                ex.sendResponseHeaders(200, 1000)
                ex.responseBody.write(ByteArray(10))
                ex.close()
                return@createContext
            }
            val body = when (ex.requestURI.path) {
                "/missing.jpg" -> null
                else -> ("image:" + ex.requestURI.path).toByteArray()
            }
            if (body == null) { ex.sendResponseHeaders(404, -1); ex.close(); return@createContext }
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
        dir.deleteRecursively()
    }

    private fun download(html: String, lead: String? = null, id: String = "a1") = runBlocking {
        SavedImages.download(
            itemId = id, html = html, baseUrl = "$base/article", leadImageUrl = lead,
            widthPx = 1080, density = 2.6f, filesDir = dir, client = OkHttpClient(),
        )
    }

    @Test
    fun `the pictures are stored and the reader finds them by any address the image offers`() {
        val html = """<p>Text</p>
            <img src="$base/one.jpg">
            <img src="$base/small.jpg" srcset="$base/small.jpg 400w, $base/big.jpg 1080w">"""
        assertEquals(2, download(html))
        // The srcset candidate for a 1080px screen was the one fetched.
        assertTrue(hits.contains("/big.jpg"))
        assertFalse(hits.contains("/small.jpg"))
        assertTrue(SavedImages.isDone("a1", dir))
        assertNotNull(SavedImages.local(dir, listOf("$base/one.jpg")))
        // A narrower column asks for the small one first and still finds the
        // kept copy under the image's other address.
        assertNotNull(SavedImages.local(dir, listOf("$base/small.jpg", "$base/big.jpg")))
        assertNull(SavedImages.local(dir, listOf("$base/elsewhere.jpg")))
    }

    @Test
    fun `relative addresses are resolved`() {
        assertEquals(1, download("""<p>Text</p><img src="/rel.jpg">"""))
        assertNotNull(SavedImages.local(dir, listOf("$base/rel.jpg")))
    }

    @Test
    fun `the feed's lead picture is kept when the reader would add it`() {
        // A body with no picture of its own: the reader puts the card's
        // picture at the top, so that is the one to keep.
        assertEquals(1, download("""<p>Only text here.</p>""", lead = "$base/lead.jpg"))
        assertNotNull(SavedImages.local(dir, listOf("$base/lead.jpg")))
    }

    @Test
    fun `a picture that fails is left out and the article still counts as done`() {
        assertEquals(1, download("""<img src="$base/ok.jpg"><img src="$base/missing.jpg">"""))
        assertTrue(SavedImages.isDone("a1", dir))
        assertNull(SavedImages.local(dir, listOf("$base/missing.jpg")))
    }

    @Test
    fun `removing the save deletes what it brought in`() {
        download("""<img src="$base/one.jpg">""")
        val kept = SavedImages.local(dir, listOf("$base/one.jpg"))!!
        SavedImages.delete("a1", dir)
        assertFalse(kept.exists())
        assertFalse(SavedImages.isDone("a1", dir))
    }

    @Test
    fun `a picture two saves share stays until the last of them goes`() {
        // One story saved from two feeds: the same picture, stored once.
        download("""<img src="$base/shared.jpg"><img src="$base/only-a1.jpg">""", id = "a1")
        download("""<img src="$base/shared.jpg">""", id = "a2")
        val shared = SavedImages.local(dir, listOf("$base/shared.jpg"))!!
        val own = SavedImages.local(dir, listOf("$base/only-a1.jpg"))!!

        SavedImages.delete("a1", dir)
        assertTrue("the other save lost its picture", shared.exists())
        assertFalse(own.exists())

        SavedImages.delete("a2", dir)
        assertFalse(shared.exists())
    }

    @Test
    fun `a picture cut off halfway leaves nothing behind`() {
        assertEquals(0, download("""<img src="$base/cut.jpg">"""))
        assertNull(SavedImages.local(dir, listOf("$base/cut.jpg")))
        val parts = SavedImages.folder(dir).listFiles { f -> f.name.endsWith(".part") }.orEmpty()
        assertEquals(parts.joinToString(), 0, parts.size)
    }

    @Test
    fun `a part file left by a download that died is swept, a fresh one is not`() {
        SavedImages.folder(dir).mkdirs()
        val stale = File(SavedImages.folder(dir), "0".repeat(40) + ".part").apply {
            writeText("half a picture")
            setLastModified(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
        }
        val running = File(SavedImages.folder(dir), "1".repeat(40) + ".part").apply { writeText("in progress") }
        download("""<img src="$base/one.jpg">""")
        assertFalse(stale.exists())
        assertTrue(running.exists())
    }

    @Test
    fun `the reader looks for a kept copy before asking the web`() {
        val render = File("src/main/java/com/saulhdev/feeder/utils/HtmlToComposable.kt").readText()
        assertTrue(render.contains("SavedImages.local(context.filesDir, listOf(src) + imageCandidates.allUrls())"))
        assertTrue(render.contains(".data(kept ?: src)"))
        val app = File("src/main/java/com/saulhdev/feeder/NeoApp.kt").readText()
        assertTrue(app.contains("articles.onUnsaved = { id -> runCatching { SavedImages.delete(id, filesDir) } }"))
    }
}
