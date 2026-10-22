package com.sift.photos.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sift.photos.data.PhotoArt
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.CenterMessage
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.Overline
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.RadioRow
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.Thumb
import com.sift.photos.ui.components.ToggleRow
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.components.scrollY
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val gpsTags = listOf(
    ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF, ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF, ExifInterface.TAG_GPS_DEST_LATITUDE, ExifInterface.TAG_GPS_DEST_LONGITUDE,
)
private val cameraTags = listOf(
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, "LensMake", "LensModel",
    "BodySerialNumber", "LensSerialNumber", ExifInterface.TAG_SOFTWARE,
)

/**
 * Makes share-ready copies in app cache. Originals are never changed. JPEGs keep their pixels and lose
 * the chosen tags; other formats are re-encoded as JPEG, which drops all metadata.
 */
private suspend fun prepareShare(context: Context, items: List<PhotoItem>, stripLocation: Boolean, stripCamera: Boolean): ArrayList<Uri> =
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val resolver = context.contentResolver
        val out = ArrayList<Uri>()
        items.forEach { item ->
            val ref = item.ref as? PhotoRef.Media ?: return@forEach
            if (ref.isVideo || (!stripLocation && !stripCamera)) { out += ref.uri; return@forEach }
            val isJpeg = item.mime == "image/jpeg"
            val file = File(dir, item.name.substringBeforeLast('.') + ".jpg")
            runCatching {
                if (isJpeg) {
                    resolver.openInputStream(ref.uri)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
                    ExifInterface(file.path).apply {
                        if (stripLocation) gpsTags.forEach { setAttribute(it, null) }
                        if (stripCamera) cameraTags.forEach { setAttribute(it, null) }
                        saveAttributes()
                    }
                } else {
                    val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, ref.uri)) { d, _, _ -> d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                }
                out += FileProvider.getUriForFile(context, context.packageName + ".share", file)
            }
        }
        out
    }

@Composable
fun ShareScreen(vm: SiftViewModel, items: List<PhotoItem>) {
    val c = Sift.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stripLocation by remember { mutableStateOf(vm.settings.stripLocation) }
    var stripCamera by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val withGps = items.count { vm.entry(it)?.lat != null || (it.ref is PhotoRef.Art && it.place != null) }
    val videos = items.count { (it.ref as? PhotoRef.Media)?.isVideo == true || it.duration != null }
    val demo = items.all { it.ref is PhotoRef.Art }
    Column(Modifier.fillMaxSize()) {
        TopBar("${items.size} selected", onBack = { vm.pop() }, backIcon = "close", backLabel = "Cancel", titleStyle = Sift.type.titleM)
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("Share ${items.size} ${if (items.size == 1) "photo" else "photos"}", Sift.type.headlineS, color = c.onSurface)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.take(20).forEach { Thumb(it.ref, 64.dp, radius = 12.dp) }
            }
            Overline("Privacy")
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                ToggleRow(
                    "Remove location",
                    when {
                        withGps == 0 -> "No GPS found in these photos"
                        else -> "$withGps of ${items.size} photos have GPS"
                    },
                    stripLocation, { stripLocation = it },
                )
                ToggleRow("Remove camera details", "Hides model, lens and serial number.", stripCamera, { stripCamera = it }, divider = true)
            }
            if (videos > 0) Row {
                Sym("info", size = 18.dp, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                T("$videos ${if (videos == 1) "video is" else "videos are"} sent as they are. Sift can't edit video metadata yet.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
            Row {
                Sym("memory", size = 18.dp, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                T("Copies are cleaned on this phone before sharing. Your originals keep everything.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Btn(if (busy) "Preparing…" else "Choose who to send to", {
                if (demo) {
                    vm.toast.show("Demo photos are placeholders and can't be shared", icon = "info", onAction = null)
                    return@Btn
                }
                busy = true
                scope.launch {
                    val uris = prepareShare(context, items, stripLocation, stripCamera)
                    busy = false
                    if (uris.isEmpty()) { vm.toast.show("Couldn't prepare these photos", icon = "error", onAction = null); return@launch }
                    val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
                    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    intent.type = if (videos == 0) "image/*" else "*/*"
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent, null))
                    vm.pop()
                }
            }, Modifier.fillMaxWidth(), large = true, icon = "share", enabled = !busy)
        }
    }
}

private data class Member(val name: String, val role: String, val art: PhotoArt)

@Composable
fun FamilyScreen(vm: SiftViewModel) {
    val c = Sift.colors
    if (!vm.sample) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Family library", onBack = { vm.pop() })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CenterMessage(
                    "group", "Share a library at home",
                    "A family library lives on storage everyone can reach, like a home NAS. Each phone reads it directly; nothing passes through Sift's servers, because there aren't any. Connecting a NAS arrives with storage locations.",
                ) {
                    Btn("Try it in the demo library", { vm.settings.sampleLibrary = true }, kind = BtnKind.Outlined)
                }
            }
        }
        return
    }
    var permission by remember { mutableIntStateOf(1) }
    var addKids by remember { mutableStateOf<Boolean?>(null) }
    val members = listOf(
        Member("You", "owner", PhotoArt.Rose), Member("Inês", "can add", PhotoArt.Wine),
        Member("Jonas", "can view", PhotoArt.Sky), Member("Tomás", "can add", PhotoArt.Gold),
    )
    Column(Modifier.fillMaxSize()) {
        TopBar("Family library", onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                members.forEach { m ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoView(PhotoRef.Art(m.art), Modifier.size(56.dp).clip(CircleShape))
                        T(m.name, Sift.type.labelL, color = c.onSurface)
                        T(m.role, Sift.type.bodyS, color = c.onSurfaceVariant)
                    }
                }
            }
            Btn("Invite", { vm.toast.show("Invites need a connected NAS. Demo only.", icon = "info", onAction = null) }, kind = BtnKind.Outlined, icon = "person_add")
            SiftCard(spacing = 6.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sym("dns", tint = c.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        T("Stored on Home NAS", Sift.type.titleM, color = c.onSurface)
                        T("6,204 items · 38 GB · members read over your VPN", Sift.type.data, color = c.onSurfaceVariant)
                    }
                }
            }
            if (addKids == null) {
                AiCard(
                    icon = "face", eyebrow = "Suggested · asks first", title = "Add photos of Inês and Tomás",
                    why = "matched on this phone. Medium confidence asks before adding.", confidence = 0.82,
                ) {
                    Btn("Add 48 photos", { addKids = true; vm.toast.show("48 photos added to the family library", icon = "group") { addKids = null } }, kind = BtnKind.Text, color = c.primary)
                    Btn("Not now", { addKids = false }, kind = BtnKind.Text)
                }
            }
            Overline("New members can")
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                listOf(Triple("visibility", "View only", "See photos, nothing else"), Triple("add_photo_alternate", "Add photos", "Add, but not edit or delete"), Triple("edit", "Edit and add", "Full access except deleting")).forEachIndexed { i, (icon, name, sub) ->
                    if (i > 0) Divider(Modifier.padding(horizontal = 16.dp))
                    RadioRow(icon, name, sub, permission == i, { permission = i })
                }
            }
            Overline("Activity")
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    PhotoView(PhotoRef.Art(PhotoArt.Sky), Modifier.size(28.dp).clip(CircleShape))
                    PhotoView(PhotoRef.Art(PhotoArt.Wine), Modifier.padding(start = 18.dp).size(28.dp).clip(CircleShape))
                }
                Spacer(Modifier.width(12.dp))
                T("Jonas Weber added 24 photos · 2 h ago", Sift.type.bodyM, color = c.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym("lock", size = 18.dp, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                T("Members read the NAS directly. Nothing goes through a Sift server.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
