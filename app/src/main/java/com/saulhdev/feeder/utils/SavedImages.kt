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

import com.saulhdev.feeder.manager.bookmarks.onlyPublicHttps
import com.saulhdev.feeder.utils.HttpIdentity.asImageFetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The pictures of saved articles, kept on the phone so they read offline.
 *
 * Saving is reading later, and the text of a saved article was already kept.
 * Its pictures were not: the reader asked the web for each one on opening,
 * so offline a saved article came up as text between grey placeholders —
 * unless Coil's disk cache happened to still hold them, which for a cache
 * the whole feed shares is luck rather than a promise.
 *
 * Stored by address, in one folder, so the reader can find a picture from
 * nothing but the address it was about to ask for. Each saved article also
 * gets a list of the pictures it brought in, which is what removing the save
 * deletes, and whose presence says the job is done.
 */
object SavedImages {

    /** Enough for a long feature; a gallery page beyond it keeps its first forty. */
    const val MAX_PER_ARTICLE = 40

    /** Larger than any sensible article picture. Anything bigger is skipped. */
    const val MAX_BYTES = 8L * 1024 * 1024

    fun folder(filesDir: File): File = File(filesDir, "saved_images")

    /** The list of what one saved article brought in; see [download]. */
    fun manifest(itemId: String, filesDir: File): File =
        File(filesDir, "${safeArticleId(itemId)}$MANIFEST")

    private const val MANIFEST = ".images.txt"
    private const val PART = ".part"

    /** A download's part file older than this was left by one that died. */
    private const val PART_STALE_MS = 60L * 60 * 1000

    fun fileFor(filesDir: File, url: String): File = File(folder(filesDir), nameFor(url))

    /** The first of [urls] kept on the phone, if any. Cheap: a stat per address. */
    fun local(filesDir: File, urls: List<String>): File? =
        urls.asSequence()
            .filter { it.isNotBlank() }
            .map { fileFor(filesDir, it) }
            .firstOrNull { it.isFile && it.length() > 0 }

    fun isDone(itemId: String, filesDir: File): Boolean = manifest(itemId, filesDir).isFile

    /**
     * Downloads the pictures of one article's full text.
     *
     * Parsed the way the reader parses it, lead picture included, and each
     * picture chosen the way the reader chooses: the srcset candidate for
     * [widthPx] at [density], which is the phone's own screen. The reader
     * looks for a local copy under every candidate an image offers, so a
     * narrower column on a tablet still finds the one fetched here.
     *
     * Writes the manifest even when some pictures fail, so a picture that
     * will never download is not asked for on every save. Returns how many
     * were stored.
     */
    suspend fun download(
        itemId: String,
        html: String,
        baseUrl: String,
        leadImageUrl: String?,
        widthPx: Int,
        density: Float,
        filesDir: File,
        client: OkHttpClient = savedImageClient,
    ): Int = withContext(Dispatchers.IO) {
        val body = Jsoup.parse(html, baseUrl).body()
        ensureLeadImage(body, leadImageUrl)
        val urls = body.select("img")
            .asSequence()
            .map { getImageSource(baseUrl, it) }
            .filter { it.hasImage }
            .map { runCatching { it.getBestImageForMaxSize(widthPx.coerceAtLeast(1), density) }.getOrDefault("") }
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
            .take(MAX_PER_ARTICLE)
            .toList()
        folder(filesDir).mkdirs()
        sweepStaleParts(filesDir)
        val kept = urls.filter { url -> fetch(client, url, fileFor(filesDir, url)) }
        manifest(itemId, filesDir).writeText(kept.joinToString("\n") { nameFor(it) })
        kept.size
    }

    /**
     * Deletes what one article brought in that no other saved article still
     * names, and its list.
     *
     * Pictures are stored once per address, so one story saved from two
     * feeds, or a site's standard header, is one file for both. Unsaving
     * either deleted it, and the other showed a grey box offline.
     */
    fun delete(itemId: String, filesDir: File) {
        val list = manifest(itemId, filesDir)
        runCatching {
            if (list.isFile) {
                val stillNamed = namedByOthers(list, filesDir)
                list.readLines()
                    .filter { NAME.matches(it) && it !in stillNamed }
                    .forEach { File(folder(filesDir), it).delete() }
            }
        }
        list.delete()
    }

    /** Every picture the other saved articles' lists name. */
    private fun namedByOthers(except: File, filesDir: File): Set<String> =
        filesDir.listFiles { file -> file.isFile && file.name.endsWith(MANIFEST) && file != except }
            .orEmpty()
            .flatMapTo(HashSet()) { runCatching { it.readLines() }.getOrDefault(emptyList()) }

    /**
     * Part files left by a download that never finished: the app stopped
     * mid-picture. A fresh one may belong to a download running now, so only
     * old ones go.
     */
    private fun sweepStaleParts(filesDir: File) {
        val cutoff = System.currentTimeMillis() - PART_STALE_MS
        folder(filesDir).listFiles { file -> file.name.endsWith(PART) && file.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    private fun fetch(client: OkHttpClient, url: String, target: File): Boolean {
        if (target.isFile && target.length() > 0) return true
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                val body = response.body
                if (!response.isSuccessful) return false
                val length = body.contentLength()
                if (length > MAX_BYTES) return false
                val part = File(target.parentFile, target.name + PART)
                var written = 0L
                try {
                    body.byteStream().use { input ->
                        part.outputStream().use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                written += n
                                if (written > MAX_BYTES) return false
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                    written > 0 && part.renameTo(target)
                } finally {
                    // Gone whatever happened: renamed into place, too large,
                    // or cut off by the network. Only the too-large case
                    // used to clean up, and the rest stayed for ever.
                    part.delete()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** A hash of the address: short, safe as a file name, the same every time. */
    internal fun nameFor(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(40)

    private val NAME = Regex("[0-9a-f]{40}")
}

/**
 * Pictures go out with the image fetcher's identity, through the same guard
 * as everything else: an address in an article is a string a publisher wrote,
 * and this fetches it in the background.
 */
val savedImageClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .asImageFetcher()
        .onlyPublicHttps()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
}
