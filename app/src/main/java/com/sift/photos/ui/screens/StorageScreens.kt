package com.sift.photos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sift.photos.data.PhotoArt
import com.sift.photos.data.PhotoRef
import com.sift.photos.data.SampleData
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.ConfidenceMeter
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.MeterSegment
import com.sift.photos.ui.components.Overline
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.RadioRow
import com.sift.photos.ui.components.SegmentedBar
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.StorageMeter
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.components.TrustRow
import com.sift.photos.ui.components.WhyLine
import com.sift.photos.ui.components.scrollY
import com.sift.photos.ui.theme.PhotoScrim
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift

@Composable
fun StorageScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val stats = vm.storage
    Column(Modifier.fillMaxSize().statusBarsPadding().scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T("Storage", Sift.type.headlineL, Modifier.weight(1f).semantics { heading() }, color = c.onSurface)
            Btn("Export all", {
                vm.toast.show(
                    if (vm.sample) "Export writes plain folders by year and month, with XMP sidecars" else "Your photos are already plain files in DCIM and Pictures",
                    icon = "folder", onAction = null,
                )
            }, kind = BtnKind.Text, icon = "download")
        }
        if (vm.sample) {
            StorageMeter(
                "312 GB library", "3 locations",
                listOf(
                    MeterSegment("This phone", "56 GB · originals", 0.18f, c.primary),
                    MeterSegment("Home NAS · synced 11:12", "200 GB · originals", 0.64f, c.onSurfaceVariant),
                    MeterSegment("Dropbox · synced 10:58", "56 GB · backup", 0.18f, c.outline, 0.6f),
                ),
                "Plain folders, standard files, sidecar XMP. Export everything anytime.",
            )
        } else {
            val lib = (stats?.photoBytes ?: 0) + (stats?.videoBytes ?: 0)
            StorageMeter(
                "${formatBytes(lib)} library", "1 location",
                listOf(MeterSegment("This phone", "${formatBytes(lib)} · originals", 1f, c.primary)),
                "Plain files in your phone's own folders. Nothing is copied anywhere else.",
            )
        }

        Overline("By type")
        val types: List<Triple<String, String, Float>> = if (vm.sample) SampleData.types else {
            val total = ((stats?.photoBytes ?: 0) + (stats?.videoBytes ?: 0)).coerceAtLeast(1)
            listOf(
                Triple("Photos", "${formatBytes((stats?.photoBytes ?: 0) - (stats?.screenshotBytes ?: 0))} · %,d".format((stats?.photoCount ?: 0) - (stats?.screenshotCount ?: 0)), ((stats?.photoBytes ?: 0) - (stats?.screenshotBytes ?: 0)).toFloat() / total),
                Triple("Videos", "${formatBytes(stats?.videoBytes ?: 0)} · %,d".format(stats?.videoCount ?: 0), (stats?.videoBytes ?: 0).toFloat() / total),
                Triple("Screenshots", "${formatBytes(stats?.screenshotBytes ?: 0)} · %,d".format(stats?.screenshotCount ?: 0), (stats?.screenshotBytes ?: 0).toFloat() / total),
            )
        }
        SiftCard(spacing = 12.dp) {
            types.forEach { (name, detail, frac) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        T(name, Sift.type.bodyM, Modifier.weight(1f), color = c.onSurface)
                        T(detail, Sift.type.data, color = c.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.containerHigh)) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0.01f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.onSurfaceVariant))
                    }
                }
            }
        }

        val free = if (vm.sample) 41_200_000_000 else stats?.freeBytes ?: 0
        val total = (if (vm.sample) 256_000_000_000 else stats?.totalBytes ?: 1).coerceAtLeast(1)
        val sift = if (vm.sample) 56_000_000_000 else (stats?.photoBytes ?: 0) + (stats?.videoBytes ?: 0)
        val other = (total - free - sift).coerceAtLeast(0)
        SiftCard(spacing = 12.dp) {
            Row(verticalAlignment = Alignment.Bottom) {
                T("This phone", Sift.type.titleM, Modifier.weight(1f), color = c.onSurface)
                T("${formatBytes(free)} free of ${formatBytes(total)}", Sift.type.data, color = c.onSurfaceVariant)
            }
            SegmentedBar(
                listOf(
                    MeterSegment("Sift", "", sift.toFloat() / total, c.primary),
                    MeterSegment("Other apps", "", other.toFloat() / total, c.onSurfaceVariant),
                    MeterSegment("Free", "", free.toFloat() / total, c.containerHigh),
                )
            )
            Row {
                T("Sift ${formatBytes(sift)}", Sift.type.data, Modifier.weight(1f), color = c.onSurfaceVariant)
                T("Other apps ${formatBytes(other)}", Sift.type.data, color = c.onSurfaceVariant)
            }
            if (free < 8_000_000_000) {
                T("Space is getting tight. Sift won't remove anything on its own.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
        }

        if (vm.sample && vm.ai) {
            AiCard(
                icon = "cloud_upload", eyebrow = "Smart offload", title = "Free 38.4 GB on this phone",
                why = "old originals already copied to Home NAS.", confidence = 0.97,
                onClick = { vm.push(Screen.Offload) },
            ) { Btn("Review", { vm.push(Screen.Offload) }, kind = BtnKind.Text, color = c.primary) }
        }

        SiftCard(spacing = 0.dp, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
            LinkRow("add_circle", "Add a location", "Dropbox, Drive, WebDAV, S3") { vm.push(Screen.ChooseStorage) }
            Divider(Modifier.padding(horizontal = 16.dp))
            LinkRow("cloud_sync", "Back up on Wi‑Fi and charging", if (vm.sample) "Home NAS first, then Dropbox" else "Add a location to turn on") { vm.push(Screen.ChooseStorage) }
            Divider(Modifier.padding(horizontal = 16.dp))
            LinkRow("delete_sweep", "Free up space", if (vm.sample) "4.1 GB already backed up" else "Review old screenshots") { vm.push(Screen.Cleanup) }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun LinkRow(icon: String, title: String, sub: String?, onClick: () -> Unit) {
    val c = Sift.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon, tint = c.onSurface)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            T(title, Sift.type.bodyL, color = c.onSurface)
            if (!sub.isNullOrEmpty()) T(sub, Sift.type.bodyS, color = c.onSurfaceVariant)
        }
        Sym("chevron_right", tint = c.onSurfaceVariant)
    }
}

@Composable
fun OffloadScreen(vm: SiftViewModel) {
    val c = Sift.colors
    var keepVideos by remember { mutableStateOf<Boolean?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Smart offload", onBack = { vm.pop() }) {
            Btn("Rules", { vm.toast.show("Older than 12 months · not opened since April · verified copy", icon = "rule", onAction = null) }, kind = BtnKind.Text)
        }
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                T("38.4 GB", Sift.type.displayL, color = c.onSurface)
                T("freed on this phone · originals stay on Home NAS", Sift.type.bodyL, color = c.onSurfaceVariant)
            }
            SiftCard(spacing = 12.dp) {
                T("1,284 originals can leave this phone", Sift.type.titleM, color = c.onSurface)
                WhyLine("older than 12 months, not opened since April, and each has a verified copy on Home NAS (checksum match).")
                TrustRow(0.97)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf(PhotoArt.Sea, PhotoArt.Forest, PhotoArt.Night, PhotoArt.Gold).forEach { a ->
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(Shapes.s)) {
                            PhotoView(PhotoRef.Art(a), Modifier.fillMaxSize())
                            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).size(24.dp).clip(CircleShape).background(PhotoScrim), contentAlignment = Alignment.Center) {
                                Sym("dns", size = 16.dp, weight = 400, tint = Color.White)
                            }
                        }
                    }
                }
            }
            Overline("Stays on this phone")
            SiftCard(spacing = 10.dp) {
                StayRow("image", "Thumbnails of all 1,284 at 1080 px", "1.1 GB")
                StayRow("favorite", "Favorites, the last 90 days, albums set to Offline", null)
                StayRow("wifi", "Open any photo to fetch its original, about 2 s at home", null)
            }
            if (keepVideos == null) {
                SiftCard(color = c.containerHigh, spacing = 10.dp) {
                    T("6 videos changed after their NAS copy. Keep them here until they sync?", Sift.type.bodyL, color = c.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConfidenceMeter(0.55)
                        Spacer(Modifier.weight(1f))
                        Btn("Offload anyway", { keepVideos = false }, kind = BtnKind.Text)
                        Btn("Keep here", { keepVideos = true }, kind = BtnKind.Tonal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            val n = if (keepVideos == false) "1,290" else "1,284"
            Btn("Offload $n originals", {
                vm.pop()
                vm.toast.show("Offloading $n originals · thumbnails stay", icon = "cloud_upload", onAction = null)
            }, Modifier.fillMaxWidth(), large = true)
        }
    }
}

@Composable
private fun StayRow(icon: String, text: String, size: String?) {
    val c = Sift.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Sym(icon, tint = c.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        T(text, Sift.type.bodyM, Modifier.weight(1f), color = c.onSurface)
        if (size != null) T(size, Sift.type.data, color = c.onSurfaceVariant)
    }
}

@Composable
fun ChooseStorageScreen(vm: SiftViewModel) {
    val c = Sift.colors
    var choice by remember { mutableIntStateOf(if (vm.sample) 1 else 0) }
    val providers = SampleData.providers.map {
        if (!vm.sample && it.name == "This phone only") it.copy(detail = vm.storage?.let { s -> "${formatBytes(s.freeBytes)} free · nothing leaves" } ?: it.detail) else it
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.pop() }, backIcon = "close", backLabel = "Close")
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Where should your originals live?", Sift.type.headlineS, color = c.onSurface)
            T("Sift doesn't sell storage. Use a place you already have. You can move everything later.", Sift.type.bodyM, color = c.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                providers.forEachIndexed { i, p ->
                    RadioRow(p.icon, p.name, p.detail, choice == i, { choice = i }, highlight = true)
                }
            }
            Row(Modifier.fillMaxWidth().clip(Shapes.l).background(c.containerLow).padding(16.dp)) {
                Sym("folder", tint = c.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                T("Plain folders by year and month. HEIC, JPEG, RAW and MP4 stay as they are; edits go in XMP sidecars.", Sift.type.bodyM, color = c.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            val p = providers[choice]
            Btn("Continue with ${p.name}", {
                vm.pop()
                when {
                    choice == 0 -> vm.toast.show("Originals stay on this phone", icon = "smartphone", onAction = null)
                    vm.sample -> vm.toast.show("${p.name} set for originals", icon = p.icon, onAction = null)
                    else -> vm.toast.show("Connecting ${p.name} isn't available in this build yet. Nothing changed.", icon = "info", onAction = null)
                }
            }, Modifier.fillMaxWidth(), large = true)
        }
    }
}
