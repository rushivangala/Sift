package com.sift.photos.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

data class StorageStats(
    val totalBytes: Long,
    val freeBytes: Long,
    val photoBytes: Long,
    val videoBytes: Long,
    val photoCount: Int,
    val videoCount: Int,
    val screenshotBytes: Long,
    val screenshotCount: Int,
)

data class ExifFacts(
    val camera: String?,
    val exposure: String?,
    val latLong: DoubleArray?,
)

/** Reads the device library through MediaStore. Nothing is uploaded; nothing leaves the phone. */
class MediaRepository(private val context: Context) {

    private val resolver get() = context.contentResolver
    private val thumbs = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    fun access(): MediaAccess {
        fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            granted(Manifest.permission.READ_MEDIA_IMAGES) || granted(Manifest.permission.READ_MEDIA_VIDEO) -> MediaAccess.Full
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> MediaAccess.Partial
            else -> MediaAccess.None
        }
    }

    fun observe(onChange: () -> Unit): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        resolver.registerContentObserver(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), true, observer)
        return observer
    }

    fun unobserve(observer: ContentObserver) = resolver.unregisterContentObserver(observer)

    suspend fun load(trashed: Boolean = false): List<PhotoItem> = withContext(Dispatchers.IO) {
        if (access() == MediaAccess.None) return@withContext emptyList()
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val args = Bundle().apply {
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            )
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )
            if (trashed) putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }
        val out = ArrayList<PhotoItem>()
        resolver.query(uri, projection, args, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iFav = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            val iExp = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: (c.getLong(iAdded) * 1000)
                val path = c.getString(iPath)
                val isShot = path?.contains("Screenshots", ignoreCase = true) == true
                out += PhotoItem(
                    key = "m$id",
                    ref = PhotoRef.Media(ContentUris.withAppendedId(base, id), video, id),
                    dateMillis = taken,
                    name = c.getString(iName) ?: "",
                    duration = if (video) formatDuration(c.getLong(iDur)) else null,
                    docIcon = if (isShot) "screenshot" else null,
                    favorite = c.getInt(iFav) == 1,
                    sizeBytes = c.getLong(iSize),
                    width = c.getInt(iW),
                    height = c.getInt(iH),
                    bucket = c.getString(iBucket),
                    mime = c.getString(iMime),
                    relativePath = path,
                    expiresMillis = c.getLong(iExp) * 1000,
                )
            }
        }
        out
    }

    suspend fun thumbnail(ref: PhotoRef.Media, px: Int): Bitmap? {
        val key = "${ref.mediaId}@$px"
        thumbs.get(key)?.let { return it }
        val bmp = withContext(Dispatchers.IO) {
            runCatching { resolver.loadThumbnail(ref.uri, Size(px, px), null) }.getOrNull()
        } ?: if (!ref.isVideo) display(ref, px) else null
        return bmp?.also { thumbs.put(key, it) }
    }

    /** Full-screen decode, downsampled to the display so a 50 MP photo never allocates 200 MB. */
    suspend fun display(ref: PhotoRef.Media, maxPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (ref.isVideo) return@withContext thumbnail(ref, maxPx)
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, ref.uri)) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val scale = maxOf(1f, maxOf(w, h) / maxPx.toFloat())
                decoder.setTargetSize((w / scale).toInt(), (h / scale).toInt())
                decoder.allocator = ImageDecoder.ALLOCATOR_HARDWARE
            }
        }.getOrNull()
    }

    /** A small software bitmap for color sampling ("Match my photo"). */
    suspend fun samplePixels(ref: PhotoRef.Media): IntArray? = withContext(Dispatchers.IO) {
        val bmp = runCatching { resolver.loadThumbnail(ref.uri, Size(64, 64), null) }.getOrNull() ?: return@withContext null
        val soft = if (bmp.config == Bitmap.Config.HARDWARE) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
        IntArray(soft.width * soft.height).also { soft.getPixels(it, 0, soft.width, 0, 0, soft.width, soft.height) }
    }

    suspend fun exif(ref: PhotoRef.Media): ExifFacts? = withContext(Dispatchers.IO) {
        if (ref.isVideo) return@withContext null
        runCatching {
            val uri = if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED)
                MediaStore.setRequireOriginal(ref.uri) else ref.uri
            resolver.openInputStream(uri)?.use { stream ->
                val e = ExifInterface(stream)
                val model = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL))
                    .joinToString(" ").trim().ifEmpty { null }
                val parts = buildList {
                    e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { add("f/%.1f".format(it)) }
                    e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                        add(if (it < 1) "1/${(1 / it).toInt()} s" else "%.1f s".format(it))
                    }
                    e.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0).takeIf { it > 0 }?.let { add("ISO $it") }
                    e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { add("%.0f mm".format(it)) }
                }
                val ll = FloatArray(2)
                val latLong = if (e.getLatLong(ll)) doubleArrayOf(ll[0].toDouble(), ll[1].toDouble()) else null
                ExifFacts(model, parts.joinToString(" · ").ifEmpty { null }, latLong)
            }
        }.getOrNull()
    }

    suspend fun storage(items: List<PhotoItem>): StorageStats = withContext(Dispatchers.IO) {
        val fs = StatFs(Environment.getDataDirectory().path)
        val videos = items.filter { (it.ref as? PhotoRef.Media)?.isVideo == true }
        val photos = items - videos.toSet()
        val shots = items.filter { it.docIcon == "screenshot" }
        StorageStats(
            totalBytes = fs.totalBytes,
            freeBytes = fs.availableBytes,
            photoBytes = photos.sumOf { it.sizeBytes },
            videoBytes = videos.sumOf { it.sizeBytes },
            photoCount = photos.size,
            videoCount = videos.size,
            screenshotBytes = shots.sumOf { it.sizeBytes },
            screenshotCount = shots.size,
        )
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val s = ms / 1000
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
        }

        fun dayKey(millis: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = millis }
            return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
        }
    }
}

fun Uri.asMediaRef(isVideo: Boolean, id: Long) = PhotoRef.Media(this, isVideo, id)
