package com.obwiler.weo.photo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PhotoRecord(
    val uri: Uri,           // MediaStore URI (system gallery visible)
    val filePath: String,   // app-private file path
    val displayName: String,// current filename
    val dateTaken: Long,    // epoch millis
    val aiSummary: String,  // AI analysis summary (for gallery caption)
)

object PhotoRepository {
    private const val TAG = "WEO/Photo"
    private const val RELATIVE_PATH = "Pictures/WEO"
    private const val PHOTO_SUBDIR = "photos"

    /** Save JPEG bytes to both MediaStore and app-private storage.
     *  Returns the PhotoRecord with URI and path. */
    fun save(context: Context, jpegBytes: ByteArray): PhotoRecord? {
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(now))
        val displayName = "IMG_${dateStr}.jpg"

        // 1. Save to app-private storage (for in-app gallery fast access)
        val privateDir = File(context.filesDir, PHOTO_SUBDIR)
        if (!privateDir.exists()) privateDir.mkdirs()
        val privateFile = File(privateDir, displayName)
        try {
            privateFile.writeBytes(jpegBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Private save failed", e)
            return null
        }

        // 2. Save to MediaStore (system gallery visible)
        val uri = saveToMediaStore(context, jpegBytes, displayName, now)

        return PhotoRecord(
            uri = uri ?: Uri.fromFile(privateFile),
            filePath = privateFile.absolutePath,
            displayName = displayName,
            dateTaken = now,
            aiSummary = "",
        )
    }

    /** Rename a photo (both in MediaStore and app-private). */
    fun rename(context: Context, record: PhotoRecord, newName: String): PhotoRecord? {
        // Rename app-private file
        val oldFile = File(record.filePath)
        if (!oldFile.exists()) return null
        val newFile = File(oldFile.parentFile!!, newName)
        if (!oldFile.renameTo(newFile)) {
            Log.w(TAG, "Rename private file failed: ${oldFile.name} -> $newName")
            return null
        }

        // Update MediaStore display name
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, newName)
            }
            context.contentResolver.update(record.uri, values, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore rename failed", e)
        }

        return record.copy(filePath = newFile.absolutePath, displayName = newName)
    }

    /** Load all photos from app-private storage, sorted by date (newest first). */
    fun loadAll(context: Context): List<PhotoRecord> {
        val dir = File(context.filesDir, PHOTO_SUBDIR)
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.name.endsWith(".jpg") } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            PhotoRecord(
                uri = Uri.fromFile(f),
                filePath = f.absolutePath,
                displayName = f.name,
                dateTaken = f.lastModified(),
                aiSummary = "",
            )
        }
    }

    /** Update AI summary for a photo record (in-memory, not persisted to MediaStore). */
    fun updateSummary(record: PhotoRecord, summary: String): PhotoRecord {
        return record.copy(aiSummary = summary.take(80))
    }

    /** Delete a photo from both storages. */
    fun delete(context: Context, record: PhotoRecord): Boolean {
        var ok = true
        // Delete private file
        try {
            File(record.filePath).delete()
        } catch (e: Exception) {
            ok = false
        }
        // Delete from MediaStore
        try {
            context.contentResolver.delete(record.uri, null, null)
        } catch (e: Exception) {
            ok = false
        }
        return ok
    }

    private fun saveToMediaStore(
        context: Context, jpeg: ByteArray, displayName: String, dateTaken: Long
    ): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
            put(MediaStore.Images.Media.DATE_TAKEN, dateTaken)
        }
        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return null

        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(jpeg)
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            Log.d(TAG, "MediaStore saved: $displayName")
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore write failed", e)
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) {}
            return null
        }
    }
}
