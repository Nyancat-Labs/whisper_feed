/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
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
 */

package com.saulhdev.feeder.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A picture from the reader, saved to the phone or handed to another app.
 *
 * Both go through the image loader, which has the picture already - on
 * screen, in its cache, or kept on the phone for a saved article - so neither
 * fetches it again when it need not. Written as JPEG, or PNG when the picture
 * has transparency that JPEG would fill with black.
 */
object PictureFiles {

    /**
     * Whether saving needs no permission here. From Android 10 an app adds to
     * Pictures through MediaStore without asking; before that it needs the
     * storage permission, which Whisper does not ask for, so the viewer offers
     * Share alone and the share sheet's own "Save" covers it.
     */
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Saves [model] to Pictures/Whisper. The name it was saved under, or null. */
    suspend fun save(context: Context, model: Any): String? = withContext(Dispatchers.IO) {
        if (!canSave) return@withContext null
        runCatching {
            val bitmap = load(context, model) ?: return@runCatching null
            writeToPictures(context, bitmap)
        }.getOrNull()
    }

    /** Opens the share sheet with [model] as an image. False if it could not be loaded. */
    suspend fun share(context: Context, model: Any): Boolean {
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = load(context, model) ?: return@runCatching null
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                // One file, replaced each time: what was shared before has
                // already been read by the app it went to.
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "picture." + extensionFor(bitmap))
                file.outputStream().use { compress(bitmap, it) }
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            }.getOrNull()
        } ?: return false
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private suspend fun load(context: Context, model: Any): Bitmap? {
        val result = context.imageLoader.execute(
            ImageRequest.Builder(context).data(model).allowHardware(false)
                // Smaller pictures come as they are; only an enormous original
                // is scaled down, rather than decoded whole into memory.
                .size(FULL_SCREEN_MAX_PX).scale(Scale.FIT).precision(Precision.INEXACT)
                .build()
        )
        return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToPictures(context: Context, bitmap: Bitmap): String {
        val name = "Whisper-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) +
            "." + extensionFor(bitmap)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/" + if (bitmap.hasAlpha()) "png" else "jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Whisper")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore rejected the insert")
        resolver.openOutputStream(uri)?.use { compress(bitmap, it) }
            ?: error("Could not open $uri for writing")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return name
    }

    private fun compress(bitmap: Bitmap, out: java.io.OutputStream) {
        if (bitmap.hasAlpha()) bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        else bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
    }

    private fun extensionFor(bitmap: Bitmap) = if (bitmap.hasAlpha()) "png" else "jpg"

    /** The cache folder the share sheet is given, and the only one. See file_paths.xml. */
    const val SHARE_DIR = "shared_pictures"

    private const val JPEG_QUALITY = 95

    /** The longest side a full-screen, saved or shared picture is decoded at. */
    const val FULL_SCREEN_MAX_PX = 4096
}
