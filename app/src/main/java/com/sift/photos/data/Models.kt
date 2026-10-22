package com.sift.photos.data

import android.net.Uri
import androidx.compose.runtime.Immutable

/** Where a photo's pixels come from: a placeholder from the sample library, or a real MediaStore item. */
@Immutable
sealed interface PhotoRef {
    data class Art(val art: PhotoArt) : PhotoRef
    data class Media(val uri: Uri, val isVideo: Boolean, val mediaId: Long) : PhotoRef
}

@Immutable
data class PhotoItem(
    val key: String,
    val ref: PhotoRef,
    val dateMillis: Long,
    val name: String,
    /** Corner badges: one per corner, max two per cell. */
    val duration: String? = null,
    val docIcon: String? = null,
    val cloud: Boolean = false,
    val favorite: Boolean = false,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val place: String? = null,
    val bucket: String? = null,
    val mime: String? = null,
    val relativePath: String? = null,
    val expiresMillis: Long = 0,
    val modifiedSec: Long = 0,
)

@Immutable
data class DaySection(val title: String, val subtitle: String, val items: List<PhotoItem>)

@Immutable
data class Album(
    val key: String,
    val name: String,
    val count: String,
    val cover: PhotoRef?,
    val icon: String? = null,
    val items: List<PhotoItem> = emptyList(),
)

/** Confidence bands from Stage 1: High may act (with Undo), Medium suggests, Low asks. */
enum class Band(val label: String) { High("High"), Medium("Med"), Low("Low") }

fun bandOf(c: Double): Band = when {
    c >= 0.90 -> Band.High
    c >= 0.70 -> Band.Medium
    else -> Band.Low
}

enum class MediaAccess { None, Partial, Full }

enum class ThemeMode { System, Light, Dark }

enum class AccentSource { Wallpaper, Curated, Photo }
