package com.sift.photos.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ImportProgress(
    val running: Boolean = false,
    val imported: Int = 0,
    val skipped: Int = 0,
    val albums: Int = 0,
    val bytesRead: Long = 0,
    val bytesTotal: Long = 0,
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * Imports a Google Takeout export (one or more .zip parts) into Pictures/Google Photos/<album>.
 * Dates come from Takeout's JSON sidecars, so photos land on the day they were taken, not the day
 * they were downloaded. Google keeps its copy; nothing is uploaded anywhere.
 */
class TakeoutImporter(private val context: Context) {
    var progress by mutableStateOf(ImportProgress())
        private set

    private val media = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "dng", "mp4", "mov", "3gp", "webm", "m4v")
    private val videos = setOf("mp4", "mov", "3gp", "webm", "m4v")

    private class Counting(input: InputStream, val onRead: (Long) -> Unit) : FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) onRead(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) onRead(it.toLong()) }
    }

    suspend fun import(uris: List<Uri>, skipExisting: Boolean) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val total = uris.sumOf { u ->
            resolver.query(u, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
        }
        progress = ImportProgress(running = true, bytesTotal = total * 2)
        try {
            // Pass 1: sidecars. Keyed by album folder + original title.
            val taken = HashMap<String, Long>()
            var read = 0L
            for (u in uris) {
                ZipInputStream(Counting(resolver.openInputStream(u)!!) { read += it; progress = progress.copy(bytesRead = read) }).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        ensureActive()
                        if (!e.isDirectory && e.name.endsWith(".json")) {
                            runCatching {
                                val json = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                                val title = json.optString("title")
                                val ts = json.optJSONObject("photoTakenTime")?.optString("timestamp")?.toLongOrNull()
                                if (title.isNotEmpty() && ts != null) taken[folderOf(e.name) + "/" + title] = ts * 1000
                            }
                        }
                    }
                }
            }
            val existing = if (skipExisting) existingNames() else emptySet()
            val albums = HashSet<String>()
            var imported = 0
            var skipped = 0
            for (u in uris) {
                ZipInputStream(Counting(resolver.openInputStream(u)!!) { read += it; progress = progress.copy(bytesRead = read) }).use { zip ->
                    while (true) {
                        val e = zip.nextEntry ?: break
                        ensureActive()
                        val name = e.name.substringAfterLast('/')
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (e.isDirectory || ext !in media) continue
                        val folder = folderOf(e.name)
                        val album = albumName(folder)
                        if (skipExisting && name.lowercase() in existing) { skipped++; progress = progress.copy(skipped = skipped); continue }
                        val video = ext in videos
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Google Photos/" + album)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                            taken["$folder/$name"]?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
                        }
                        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        val target = resolver.insert(collection, values) ?: continue
                        resolver.openOutputStream(target)!!.use { out -> zip.copyTo(out) }
                        resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                        imported++
                        albums += album
                        progress = progress.copy(imported = imported, albums = albums.size)
                    }
                }
            }
            progress = progress.copy(running = false, done = true, bytesRead = progress.bytesTotal)
        } catch (t: Throwable) {
            progress = progress.copy(running = false, error = if (t is kotlinx.coroutines.CancellationException) "Paused" else (t.message ?: "Couldn't read this file"))
            if (t is kotlinx.coroutines.CancellationException) throw t
        }
    }

    private fun folderOf(path: String) = path.substringBeforeLast('/', "")

    /** "Takeout/Google Photos/Lisbon trip" → "Lisbon trip"; year folders collapse into one. */
    private fun albumName(folder: String): String {
        val last = folder.substringAfterLast('/').ifEmpty { "Imported" }
        return if (last.startsWith("Photos from ")) "Library" else last.replace(Regex("[\\\\:*?\"<>|]"), "_")
    }

    private fun existingNames(): Set<String> {
        val out = HashSet<String>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out += it.lowercase() } }
        return out
    }
}
