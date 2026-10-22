package com.sift.photos.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sift.photos.data.ExifFacts
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.data.SampleData
import com.sift.photos.data.SmartFeature
import com.sift.photos.ui.LocalActions
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.ConfidenceMeter
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.LocalMediaRepository
import com.sift.photos.ui.components.OriginBadge
import com.sift.photos.ui.components.Overline
import com.sift.photos.ui.components.Pill
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.SiftSheet
import com.sift.photos.ui.components.SiftToggle
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.components.WhyLine
import com.sift.photos.ui.components.scrollY
import com.sift.photos.ui.theme.PhotoScrim
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun SiftViewModel.isHero(item: PhotoItem) = sample && item.key == SampleData.heroPhoto.key

/** The "CR" Content Credentials marker shown on AI-edited photos. */
@Composable
fun CrPill(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(Shapes.badge).background(PhotoScrim).padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clip(Shapes.xs).background(Color.White).padding(horizontal = 3.dp)) {
            T("CR", Sift.type.dataMedium.copy(fontSize = Sift.type.data.fontSize * 0.85f), color = Color.Black)
        }
        Spacer(Modifier.width(6.dp))
        T("AI-edited", Sift.type.labelM, color = Color.White)
    }
}

@Composable
fun ViewerScreen(vm: SiftViewModel, screen: Screen.Viewer) {
    val c = Sift.colors
    val actions = LocalActions.current
    val context = LocalContext.current
    val items = screen.items
    if (items.isEmpty()) { vm.pop(); return }
    val pager = rememberPagerState(initialPage = screen.index.coerceIn(0, items.lastIndex)) { items.size }
    val item = vm.live(items[pager.currentPage.coerceIn(0, items.lastIndex)])
    var chrome by remember { mutableStateOf(true) }
    var info by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val hero = vm.isHero(item) && vm.ai && vm.settings.feature(SmartFeature.Edits)
    val editsLive = hero && vm.editsReverted.size < 4

    Box(Modifier.fillMaxSize().background(c.surface)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { items[it].key }) { page ->
            val p = items[page]
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { chrome = !chrome }) },
                contentAlignment = Alignment.Center,
            ) {
                val ratio = if (p.width > 0 && p.height > 0) p.width.toFloat() / p.height else 4f / 3f
                val maxPx = LocalConfiguration.current.let { maxOf(it.screenWidthDp, it.screenHeightDp) }.dp
                Box(Modifier.fillMaxWidth().aspectRatio(ratio, matchHeightConstraintsFirst = landscape)) {
                    PhotoView(p.ref, Modifier.fillMaxSize(), thumbSize = maxPx, full = true, contentScale = ContentScale.Fit)
                    if (vm.isHero(p) && editsLive && !showOriginal) CrPill(Modifier.align(Alignment.BottomStart).padding(12.dp))
                }
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopBar(onBack = { vm.pop() }) {
                if (vm.isHero(item)) {
                    Column(Modifier.weight(1f)) {
                        T("Sun 4 Oct · 18:52", Sift.type.titleM, color = c.onSurface)
                        T("Cais do Sodré, Lisbon", Sift.type.bodyS, color = c.onSurfaceVariant)
                    }
                } else {
                    Column(Modifier.weight(1f)) {
                        T(SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(item.dateMillis)), Sift.type.titleM, color = c.onSurface, maxLines = 1)
                        T(item.place ?: item.bucket ?: item.name, Sift.type.bodyS, color = c.onSurfaceVariant, maxLines = 1)
                    }
                }
                IconBtn("info", "Info", { info = true })
                Box {
                    IconBtn("more_vert", "More options", { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { T("Use its color", Sift.type.bodyL) }, leadingIcon = { Sym("colorize") }, onClick = {
                            menu = false; vm.useColorOf(item)
                        })
                        if (vm.isHero(item)) DropdownMenuItem(text = { T("Content Credentials", Sift.type.bodyL) }, leadingIcon = { Sym("verified") }, onClick = {
                            menu = false; vm.push(Screen.Credentials(item))
                        })
                    }
                }
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editsLive) {
                    AiCard(
                        icon = "auto_fix_high",
                        eyebrow = "Auto-edit applied · original kept",
                        title = "${4 - vm.editsReverted.size} changes, 1 waiting for you",
                        why = "Inês's face was 1.3 stops under the sky, and the shade made her skin read cool.",
                        confidence = 0.92,
                    ) {
                        Btn("What changed", { vm.push(Screen.EditPanel(item)) }, kind = BtnKind.Text, color = c.primary)
                        Spacer(Modifier.weight(1f))
                        Btn("Revert all", {
                            val before = vm.editsReverted.toList()
                            vm.editsReverted.clear(); vm.editsReverted.addAll(SampleData.edits.take(4).map { it.name })
                            vm.toast.show("Auto-edit reverted. Original restored.", icon = "undo") {
                                vm.editsReverted.clear(); vm.editsReverted.addAll(before)
                            }
                        }, kind = BtnKind.Text, icon = "undo")
                    }
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp).clip(Shapes.full).background(c.containerHigh)
                            .pointerInput(Unit) { detectTapGestures(onPress = { showOriginal = true; tryAwaitRelease(); showOriginal = false }) }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Sym("compare", size = 18.dp)
                            Spacer(Modifier.width(6.dp))
                            T(if (showOriginal) "Showing original" else "Hold to see original", Sift.type.labelL, color = c.onSurface)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    val fav = vm.isFavorite(item)
                    IconBtn("favorite", if (fav) "Remove from favorites" else "Add to favorites", { actions.favorite(item, !fav) }, fill = fav, tint = c.onSurface)
                    IconBtn("share", "Share", { actions.share(listOf(item)) }, tint = c.onSurface)
                    IconBtn("tune", "Edit", { vm.push(Screen.Editor(item)) }, tint = c.onSurface)
                    IconBtn("delete", "Move to Trash", {
                        actions.trash(listOf(item))
                        if (item.ref is PhotoRef.Art) vm.pop()
                    }, tint = c.onSurface)
                }
            }
        }
    }

    if (info) InfoSheet(vm, item, panel = landscape) { info = false }
}

@Composable
private fun InfoSheet(vm: SiftViewModel, item: PhotoItem, panel: Boolean, onDismiss: () -> Unit) {
    val c = Sift.colors
    val repo = LocalMediaRepository.current
    val exif by produceState<ExifFacts?>(null, item.key) {
        value = (item.ref as? PhotoRef.Media)?.let { repo?.exif(it) }
    }
    val rows: List<Triple<String, String, String>> = if (vm.isHero(item)) SampleData.heroInfo else buildList {
        val d = Date(item.dateMillis)
        add(Triple("event", SimpleDateFormat("EEE d MMM yyyy · HH:mm", Locale.getDefault()).format(d), SimpleDateFormat("zzzz", Locale.getDefault()).format(d)))
        exif?.camera?.let { add(Triple("photo_camera", it, exif?.exposure ?: "")) }
        val mp = if (item.width > 0) " · %.1f MP · %d × %d".format(item.width * item.height / 1e6, item.width, item.height) else ""
        add(Triple(if (item.duration != null) "movie" else "image", item.name, formatBytes(item.sizeBytes) + mp))
        exif?.latLong?.let { add(Triple("location_on", "Photo location", "%.4f, %.4f".format(it[0], it[1]))) }
        add(Triple("memory", if (item.ref is PhotoRef.Media) "On this phone" else "Sample library", item.relativePath ?: "placeholder photo"))
    }
    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        if (!panel) T("Info", Sift.type.titleL, color = c.onSurface)
        T("Add a description", Sift.type.bodyL, Modifier.padding(vertical = 8.dp), color = c.onSurfaceVariant)
        rows.forEach { (icon, a, b) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Sym(icon, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    T(a, Sift.type.bodyL, color = c.onSurface)
                    if (b.isNotEmpty()) T(b, Sift.type.data, color = c.onSurfaceVariant)
                }
            }
        }
        if (vm.ai) {
            Spacer(Modifier.height(4.dp))
            Btn("Use its color for the accent", { vm.useColorOf(item); onDismiss() }, kind = BtnKind.Outlined, icon = "colorize")
        }
    }
    if (panel) {
        // Landscape: info sits beside the photo instead of covering it.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.fillMaxHeight().width(360.dp).background(c.modal)
                    .then(if (c.hairline != null) Modifier.border(1.dp, c.hairline, androidx.compose.foundation.shape.RoundedCornerShape(0.dp)) else Modifier)
                    .statusBarsPadding().navigationBarsPadding().scrollY().padding(horizontal = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Info", Sift.type.titleL, Modifier.weight(1f), color = c.onSurface)
                    IconBtn("close", "Close info", onDismiss, tint = c.onSurface)
                }
                body()
            }
        }
    } else SiftSheet(onDismiss) { body() }
}

@Composable
fun EditPanelScreen(vm: SiftViewModel, item: PhotoItem) {
    val c = Sift.colors
    var previewDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar("What changed and why", onBack = { vm.pop() }, titleStyle = Sift.type.titleM)
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().height(220.dp).clip(Shapes.card)) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PhotoView(item.ref, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color(0x33000000)))
                    Pill("Before", PhotoScrim, Color.White, Modifier.align(Alignment.TopStart).padding(8.dp))
                }
                Box(Modifier.width(2.dp).fillMaxHeight().background(c.surface))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PhotoView(item.ref, Modifier.fillMaxSize())
                    Pill("After", PhotoScrim, Color.White, Modifier.align(Alignment.TopStart).padding(8.dp))
                    CrPill(Modifier.align(Alignment.BottomEnd).padding(8.dp))
                }
            }
            T("${item.name} · original kept · ${SampleData.edits.size} steps", Sift.type.data, color = c.onSurfaceVariant)
            SampleData.edits.forEach { e ->
                val undone = e.name in vm.editsReverted
                SiftCard(spacing = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(e.name, Sift.type.titleM, Modifier.weight(1f), color = c.onSurface)
                        T(if (undone && e.applied) "${e.value} · undone" else e.value, Sift.type.dataMedium, color = c.onSurfaceVariant)
                    }
                    T(if (undone) "Undone by you. ${e.why}" else e.why, Sift.type.bodyM, color = c.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConfidenceMeter(e.confidence)
                        Spacer(Modifier.width(8.dp))
                        OriginBadge(e.cloud, if (e.cloud) "Cloud · asks first" else null)
                        Spacer(Modifier.weight(1f))
                        when {
                            !e.applied -> Btn("Preview", { previewDialog = true }, icon = "visibility")
                            undone -> Btn("Redo", { vm.editsReverted.remove(e.name) }, kind = BtnKind.Tonal, icon = "redo")
                            else -> Btn("Undo", {
                                vm.editsReverted.add(e.name)
                                vm.toast.show("${e.name} undone", icon = "undo") { vm.editsReverted.remove(e.name) }
                            }, kind = BtnKind.Outlined, icon = "undo")
                        }
                    }
                }
            }
            Btn("Content Credentials", { vm.push(Screen.Credentials(item)) }, kind = BtnKind.Text, icon = "verified", color = c.primary)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (previewDialog) {
        com.sift.photos.ui.components.SiftDialog(
            icon = "cloud",
            title = "Preview the removal in the cloud?",
            body = "Generating ~6% of the frame needs a larger model. Only this photo would leave the device, be processed once, then deleted. Nothing is kept or used for training. The result is marked as generated.",
            confirm = "Keep on device",
            onConfirm = { previewDialog = false },
            dismiss = "Use cloud once",
            onDismiss = { previewDialog = false },
            onSecondary = {
                previewDialog = false
                vm.toast.show("No cloud provider is set up in this build. Nothing left your phone.", icon = "memory", onAction = null)
            },
        )
    }
}

@Composable
fun CredentialsScreen(vm: SiftViewModel, item: PhotoItem) {
    val c = Sift.colors
    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(260.dp).clip(Shapes.card)) {
                PhotoView(item.ref, Modifier.fillMaxSize())
                CrPill(Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(Shapes.m).background(c.inverseSurface), contentAlignment = Alignment.Center) {
                    T("CR", Sift.type.dataMedium, color = c.inverseOnSurface)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    T("Content Credentials", Sift.type.labelM, color = c.onSurfaceVariant)
                    T("This photo is marked AI-edited", Sift.type.titleM, color = c.onSurface)
                }
            }
            SiftCard(spacing = 0.dp) {
                SampleData.c2pa.forEachIndexed { i, (k, v) ->
                    if (i > 0) Divider()
                    Row(Modifier.padding(vertical = 10.dp)) {
                        T(k, Sift.type.bodyM, Modifier.width(110.dp), color = c.onSurfaceVariant)
                        T(v, Sift.type.bodyM, color = c.onSurface)
                    }
                }
            }
            SiftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Marker on shared copies", Sift.type.bodyL.copy(fontWeight = FontWeight.Medium), color = c.onSurface)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Sym("lock", size = 14.dp, tint = c.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            T("Always on for AI edits", Sift.type.bodyS, color = c.onSurfaceVariant)
                        }
                    }
                    SiftToggle(true, null, enabled = false)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Share original instead", { vm.toast.show("Sample photos are placeholders and can't be shared", icon = "info", onAction = null) }, kind = BtnKind.Outlined)
                Btn("View manifest", { vm.toast.show("Manifest: Sift · C2PA 2.1 · 3 assertions · signature valid", icon = "verified", onAction = null) }, kind = BtnKind.Text)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
