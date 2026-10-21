package com.sift.photos.ui.screens

import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sift.photos.data.Album
import com.sift.photos.data.SampleData
import com.sift.photos.data.SmartFeature
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.CenterMessage
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.Overline
import com.sift.photos.ui.components.PhotoCell
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.SiftChip
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.theme.PhotoScrim
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift

@Composable
fun AlbumsScreen(vm: SiftViewModel) {
    val c = Sift.colors
    var filter by remember { mutableIntStateOf(0) }
    val albums = vm.albums.filter {
        when (filter) {
            1 -> it.icon != "group" && it.icon != "memory"
            2 -> it.icon == "group"
            3 -> it.icon == "memory" || it.icon == "luggage"
            else -> true
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    T("Albums", Sift.type.headlineL, Modifier.weight(1f).semantics { heading() }, color = c.onSurface)
                    IconBtn("add", "New album", { vm.toast.show("Select photos in Library, then Move to a new album", icon = "info", onAction = null) }, tint = c.onSurface)
                }
                if (vm.sample) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("All", "Mine", "Shared", "Auto").forEachIndexed { i, l -> SiftChip(l, filter == i, { filter = i }) }
                    }
                }
            }
        }
        if (albums.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(320.dp)) {
                    CenterMessage("photo_album", "No albums yet", "Folders on this phone appear here as albums once Sift can see your photos.")
                }
            }
        }
        items(albums, key = { it.key }) { album ->
            Column(
                Modifier.clip(Shapes.l).clickable { vm.push(Screen.AlbumDetail(album)) }.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(Shapes.l)) {
                    PhotoView(album.cover, Modifier.fillMaxSize(), thumbSize = 220.dp)
                    album.icon?.let { icon ->
                        Box(
                            Modifier.align(Alignment.TopStart).padding(8.dp).size(28.dp).clip(CircleShape).background(PhotoScrim)
                                .semantics { contentDescription = albumKind(icon) },
                            contentAlignment = Alignment.Center,
                        ) { Sym(icon, size = 18.dp, weight = 400, tint = Color.White) }
                    }
                }
                Column(Modifier.padding(horizontal = 4.dp)) {
                    T(album.name, Sift.type.titleS, color = c.onSurface, maxLines = 1)
                    T(album.count, Sift.type.bodyS, color = c.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(4.dp))
                Overline("Private")
                SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                    LinkRow("lock", "Locked folder", if (vm.sample) "9 items · not backed up" else "Not backed up · hidden from search") { vm.push(Screen.Locked) }
                    Divider(Modifier.padding(horizontal = 16.dp))
                    LinkRow(
                        "delete", "Trash",
                        if (vm.sample) "38 items · deleted after 30 days" else "%,d items · deleted after 30 days".format(vm.deviceTrash.size),
                    ) { vm.push(Screen.Trash) }
                }
            }
        }
    }
}

private fun albumKind(icon: String) = when (icon) {
    "luggage" -> "Trip album"
    "group" -> "Shared album"
    "memory" -> "Made on device"
    "screenshot" -> "Screenshots"
    "photo_camera" -> "Camera"
    else -> "Album"
}

@Composable
fun AlbumDetailScreen(vm: SiftViewModel, album: Album) {
    val c = Sift.colors
    var tripUndone by remember { mutableStateOf(false) }
    val trip = vm.sample && album.key == "lisbon" && vm.ai && vm.settings.feature(SmartFeature.Trips)
    LazyVerticalGrid(
        GridCells.Fixed(3),
        Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TopBar(null, onBack = { vm.pop() }) {
                    IconBtn("share", "Share album", { vm.toast.show("Sharing opens from a selection in this build", icon = "info", onAction = null) })
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    T(album.name, Sift.type.headlineL, color = c.onSurface)
                    T(if (album.key == "lisbon") "214 photos · 2–7 Oct 2026" else album.count, Sift.type.bodyM, color = c.onSurfaceVariant)
                    if (trip && !tripUndone) {
                        AiCard(
                            icon = "luggage", eyebrow = "Trip album · made by Sift", title = "4 places in 6 days",
                            why = "214 photos taken 2–7 Oct, 280 km from home, grouped by place and day.", confidence = 0.96,
                        ) {
                            Btn("Undo album", {
                                tripUndone = true
                                vm.toast.show("Trip album removed. Photos untouched.", icon = "undo") { tripUndone = false }
                            }, kind = BtnKind.Text, icon = "undo", color = c.primary)
                            Btn("Rename", {}, kind = BtnKind.Text)
                            Spacer(Modifier.weight(1f))
                            Btn("Don't do this again", {
                                vm.settings.setFeature(SmartFeature.Trips, false)
                                vm.toast.show("Trip albums turned off", icon = "block") { vm.settings.setFeature(SmartFeature.Trips, true) }
                            }, kind = BtnKind.Text, color = c.onSurfaceVariant)
                        }
                    }
                    if (album.key == "lisbon") {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                            T("Sun 4 Oct", Sift.type.titleM, color = c.onSurface)
                            Spacer(Modifier.width(8.dp))
                            T("Cais do Sodré · 38 photos", Sift.type.bodyM, color = c.onSurfaceVariant)
                        }
                    } else Spacer(Modifier.height(8.dp))
                }
            }
        }
        items(album.items, key = { it.key }) { item ->
            PhotoCell(item, selectionMode = false, selected = false, onClick = {
                vm.push(Screen.Viewer(album.items, album.items.indexOf(item)))
            }, onLongClick = {})
        }
    }
}

@Composable
fun LockedScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(!vm.settings.lockedBiometrics) }
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = {
        val signal = CancellationSignal()
        val manager = context.getSystemService(BiometricManager::class.java)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager?.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            error = "Set a screen lock to protect the Locked folder."
        } else {
            BiometricPrompt.Builder(context)
                .setTitle("Unlock Locked folder")
                .setSubtitle("Confirm it's you with your fingerprint.")
                .setAllowedAuthenticators(authenticators)
                .build()
                .authenticate(signal, context.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                        unlocked = true; error = null
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        error = errString?.toString()
                    }
                })
        }
    }
    LaunchedEffect(Unit) { if (!unlocked) prompt() }

    if (!unlocked) {
        Column(Modifier.fillMaxSize()) {
            TopBar(null, onBack = { vm.pop() })
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CenterMessage(
                    "lock", "Locked folder",
                    (if (vm.sample) "9 items. " else "") + "Not backed up, not synced, hidden from search and shared albums.",
                ) {
                    if (error != null) T(error ?: "", Sift.type.bodyS, color = c.error)
                    Btn("Unlock", { prompt() }, icon = "fingerprint", large = true)
                }
            }
        }
        return
    }
    val items = if (vm.sample) SampleData.lockedItems else emptyList()
    LazyVerticalGrid(
        GridCells.Fixed(3), Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TopBar("Locked folder", onBack = { vm.pop() }) {
                    IconBtn("lock_open", "Lock now", { vm.pop() })
                }
                Row(Modifier.padding(16.dp).fillMaxWidth().clip(Shapes.card).background(c.containerLow).padding(16.dp)) {
                    Sym("info", tint = c.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    T(
                        "Stored only on this phone. If you uninstall Sift or lose this phone, " +
                            (if (vm.sample) "these 9 items are gone." else "items here are gone."),
                        Sift.type.bodyM, color = c.onSurfaceVariant,
                    )
                }
                if (items.isEmpty()) {
                    T("Nothing here yet. Moving photos into the Locked folder arrives in a later build.", Sift.type.bodyM, Modifier.padding(16.dp), color = c.onSurfaceVariant)
                }
            }
        }
        items(items, key = { it.key }) { item ->
            PhotoCell(item, selectionMode = false, selected = false, onClick = { vm.push(Screen.Viewer(items, items.indexOf(item))) }, onLongClick = {})
        }
    }
}
