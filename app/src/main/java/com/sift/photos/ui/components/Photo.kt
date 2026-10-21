package com.sift.photos.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sift.photos.data.MediaRepository
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.ui.theme.Motion
import com.sift.photos.ui.theme.PhotoScrim
import com.sift.photos.ui.theme.Sift
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LocalMediaRepository = staticCompositionLocalOf<MediaRepository?> { null }

/** Draws a photo: a gradient stand-in for sample items, or a MediaStore thumbnail. Photos are never tinted. */
@Composable
fun PhotoView(ref: PhotoRef?, modifier: Modifier = Modifier, thumbSize: Dp = 160.dp, full: Boolean = false, contentScale: ContentScale = ContentScale.Crop) {
    when (ref) {
        null -> Box(modifier.background(Sift.colors.containerHigh))
        is PhotoRef.Art -> Box(modifier.drawBehind { drawRect(ref.art.brush(size)) })
        is PhotoRef.Media -> {
            val repo = LocalMediaRepository.current
            val px = with(LocalDensity.current) { thumbSize.roundToPx() }
            val bitmap by produceState<ImageBitmap?>(null, ref.mediaId, px, full) {
                value = (if (full) repo?.display(ref, px) else repo?.thumbnail(ref, px))?.asImageBitmap()
            }
            Box(modifier.background(Sift.colors.containerHigh)) {
                bitmap?.let { Image(it, contentDescription = null, contentScale = contentScale, modifier = Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun BoxScope.CornerBadge(alignment: Alignment, label: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .align(alignment)
            .padding(6.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun RoundBadge(icon: String, fill: Boolean = false) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(PhotoScrim), contentAlignment = Alignment.Center) {
        Sym(icon, size = 16.dp, weight = 400, fill = fill, tint = Color.White)
    }
}

/**
 * Photo grid cell: square, edge to edge, 2 dp gutter. Corner badges: duration · recognized document ·
 * stored in cloud · favorite. Selected: photo insets 12 dp and morphs to 16 dp on primaryContainer.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoCell(
    item: PhotoItem,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    cellRadius: Dp = 0.dp,
) {
    val c = Sift.colors
    val reduce = Sift.reduceMotion
    val inset by animateDpAsState(if (selected) 12.dp else 0.dp, Motion.spatialDefault(reduce), label = "inset")
    val radius by animateDpAsState(if (selected) 16.dp else cellRadius, Motion.spatialDefault(reduce), label = "radius")
    Box(
        modifier
            .aspectRatio(1f)
            .background(if (selected) c.primaryContainer else Color.Transparent)
            .semantics {
                contentDescription = describe(item)
                if (selectionMode) this.selected = selected
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Select"),
    ) {
        PhotoView(item.ref, Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(radius)))
        if (!selectionMode || !selected) {
            item.duration?.let { d ->
                CornerBadge(Alignment.TopEnd, "Video, $d") {
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(PhotoScrim).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        T(d, Sift.type.dataMedium.copy(fontSize = Sift.type.data.fontSize * 0.92f), color = Color.White)
                    }
                }
            }
            if (item.duration == null && item.favorite) CornerBadge(Alignment.TopEnd, "Favorite") { RoundBadge("favorite", fill = true) }
            item.docIcon?.let { CornerBadge(Alignment.BottomStart, if (it == "screenshot") "Screenshot" else "Document detected") { RoundBadge(it) } }
            if (item.cloud) CornerBadge(Alignment.BottomEnd, "Stored in cloud") { RoundBadge("cloud") }
        }
        if (selectionMode) {
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(c.surface)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(c.selectionBadge),
                    contentAlignment = Alignment.Center,
                ) { Sym("check", size = 16.dp, weight = 600, tint = c.onPrimary) }
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0x33000000))
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

private val describeFormat = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

fun describe(item: PhotoItem): String = buildString {
    val video = (item.ref as? PhotoRef.Media)?.isVideo == true || item.duration != null
    append(if (video) "Video" else if (item.docIcon == "screenshot") "Screenshot" else "Photo")
    item.duration?.let { append(", $it") }
    append(", ")
    append(describeFormat.format(Date(item.dateMillis)))
    item.place?.let { append(", $it") }
    if (item.favorite) append(", favorite")
    if (item.cloud) append(", stored in cloud")
    if (item.docIcon == "receipt_long") append(", document detected")
}

@Composable
fun Thumb(ref: PhotoRef?, size: Dp, radius: Dp = 8.dp, modifier: Modifier = Modifier) {
    PhotoView(ref, modifier.size(size).clip(RoundedCornerShape(radius)), thumbSize = size)
}

@Composable
fun WideThumb(ref: PhotoRef?, modifier: Modifier = Modifier, ratio: Float = 1f, radius: Dp = 16.dp) {
    PhotoView(ref, modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(radius)), thumbSize = 400.dp)
}
