package com.sift.photos.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sift.photos.data.SampleData
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.OriginBadge
import com.sift.photos.ui.components.RadioRow
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.ToggleRow
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.components.scrollY
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift

/** Onboarding: 1 permissions, 2 where originals live, 3 Google Photos import, 4 first sync. */
@Composable
fun OnboardingScreen(vm: SiftViewModel) {
    when (vm.onboardingStep) {
        0 -> PermissionsStep(vm) { vm.onboardingStep = 1 }
        1 -> StorageStep(vm)
        2 -> ImportBody(vm, step = true)
        else -> FirstSyncBody(vm, step = true)
    }
}

@Composable
private fun StepBar(vm: SiftViewModel, step: Int) {
    TopBar("$step / 4", onBack = { vm.onboardingStep = (vm.onboardingStep - 1).coerceAtLeast(0) }, titleStyle = Sift.type.data)
}

@Composable
private fun StorageStep(vm: SiftViewModel) {
    val c = Sift.colors
    var choice by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        StepBar(vm, 2)
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Where should originals live?", Sift.type.headlineS, color = c.onSurface)
            T("Sift keeps plain files in plain folders. Add more locations any time.", Sift.type.bodyL, color = c.onSurfaceVariant)
            SampleData.providers.forEachIndexed { i, p ->
                val available = i == 0
                Column(Modifier.alpha(if (available) 1f else 0.38f)) {
                    RadioRow(
                        p.icon, p.name, if (available) vm.storage?.let { "${formatBytes(it.freeBytes)} free · nothing leaves" } ?: p.detail else "Connector coming later",
                        choice == i, { if (available) choice = i }, highlight = true,
                    )
                }
            }
            T("Standard files, sidecar XMP. Export everything anytime.", Sift.type.bodyS, color = c.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            Btn("Keep originals on this phone", { vm.onboardingStep = 2 }, Modifier.fillMaxWidth(), large = true)
        }
    }
}

@Composable
fun ImportScreen(vm: SiftViewModel) = ImportBody(vm, step = false)

@Composable
private fun ImportBody(vm: SiftViewModel, step: Boolean) {
    val c = Sift.colors
    val p = vm.importer.progress
    var skip by remember { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.startImport(uris, skip)
    }
    Column(Modifier.fillMaxSize()) {
        if (step) StepBar(vm, 3) else TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("Bring your Google Photos", Sift.type.headlineS, color = c.onSurface)
            T("Copied to this phone with dates and albums intact. Google keeps its copy.", Sift.type.bodyL, color = c.onSurfaceVariant)
            SiftCard(spacing = 8.dp) {
                T("How to get your export", Sift.type.titleM, color = c.onSurface)
                listOf(
                    "Open takeout.google.com and choose only Google Photos.",
                    "Pick .zip and a size. Large libraries come in several parts.",
                    "Download the parts to this phone, then choose them here.",
                ).forEachIndexed { i, s ->
                    Row { T("${i + 1}", Sift.type.dataMedium, Modifier.width(20.dp), color = c.onSurfaceVariant); T(s, Sift.type.bodyM, color = c.onSurface) }
                }
            }
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                ToggleRow("Keep album structure", "Albums become folders in Pictures/Google Photos", true, {}, enabled = false)
                ToggleRow("Keep dates", "Reads the Takeout JSON next to each file", true, {}, enabled = false, divider = true)
                ToggleRow("Skip items already on this phone", "Matched by file name", skip, { skip = it }, divider = true)
            }
            if (p.running || p.done || p.error != null) {
                SiftCard(spacing = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(
                            when {
                                p.error != null -> p.error
                                p.done -> "Imported %,d items".format(p.imported)
                                else -> "Importing · %,d so far".format(p.imported)
                            },
                            Sift.type.titleM, Modifier.weight(1f), color = c.onSurface,
                        )
                        OriginBadge()
                    }
                    if (p.bytesTotal > 0) LinearProgressIndicator(
                        progress = { (p.bytesRead.toFloat() / p.bytesTotal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(Shapes.full), color = c.primary, trackColor = c.containerHigh,
                    )
                    T("%,d albums · %,d skipped as already here".format(p.albums, p.skipped), Sift.type.data, color = c.onSurfaceVariant)
                    if (p.running) T("You can use the library while this runs.", Sift.type.bodyS, color = c.onSurfaceVariant)
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                p.running -> {
                    if (step) Btn("Continue in background", { vm.onboardingStep = 3 }, Modifier.fillMaxWidth(), large = true)
                    Btn("Pause import", { vm.pauseImport() }, Modifier.fillMaxWidth(), kind = BtnKind.Text)
                }
                else -> {
                    Btn("Choose Takeout .zip files", { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, Modifier.fillMaxWidth(), large = true, icon = "folder")
                    if (step) Btn(if (p.done) "Continue" else "Skip for now", { vm.onboardingStep = 3 }, Modifier.fillMaxWidth(), kind = BtnKind.Text)
                }
            }
        }
    }
}

@Composable
fun FirstSyncScreen(vm: SiftViewModel) = FirstSyncBody(vm, step = false)

@Composable
private fun FirstSyncBody(vm: SiftViewModel, step: Boolean) {
    val c = Sift.colors
    val p = vm.index.progress
    val total = vm.deviceItems.size
    val readAll = !p.running && p.total > 0 && p.done >= p.total
    Column(Modifier.fillMaxSize()) {
        if (step) StepBar(vm, 4) else TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            T("Sifting your library", Sift.type.headlineS, color = c.onSurface)
            T("%,d items · on this phone".format(total), Sift.type.data, color = c.onSurfaceVariant)
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                SyncStep("Indexing files", "%,d".format(total), "Read dates, sizes and folders. Nothing moved.", done = total > 0 || !vm.loading, running = vm.loading)
                Divider(Modifier.padding(horizontal = 16.dp))
                SyncStep(
                    "Reading text, objects and places", if (p.total > 0) "%,d / %,d".format(p.done, p.total) else "queued",
                    "Receipts, tickets and locations, on this phone.", done = readAll, running = p.running,
                    fraction = if (p.total > 0) p.done.toFloat() / p.total else 0f,
                )
                Divider(Modifier.padding(horizontal = 16.dp))
                SyncStep("Finding duplicates", if (readAll) "ready" else "after reading", "Review before anything is deleted.", done = readAll, running = false)
                Divider(Modifier.padding(horizontal = 16.dp))
                SyncStep("Copying originals", "this phone only", "No backup location is set up.", done = false, running = false, muted = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OriginBadge()
                Spacer(Modifier.width(8.dp))
                T("Results appear as they finish. Nothing is acted on without Undo.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            if (step) Btn("Open library", { vm.finishOnboarding(sample = false) }, Modifier.fillMaxWidth(), large = true)
            else if (p.running) Btn("Pause reading", { vm.stopIndexing() }, Modifier.fillMaxWidth(), kind = BtnKind.Outlined)
            else Btn("Resume reading", { vm.startIndexing() }, Modifier.fillMaxWidth(), kind = BtnKind.Outlined, enabled = !readAll)
        }
    }
}

@Composable
private fun SyncStep(name: String, value: String, detail: String, done: Boolean, running: Boolean, fraction: Float = 0f, muted: Boolean = false) {
    val c = Sift.colors
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym(
                if (done) "check_circle" else if (running) "hourglass_empty" else "radio_button_unchecked",
                fill = done, tint = if (done) c.primary else if (muted) c.outline else c.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            T(name, Sift.type.titleS, Modifier.weight(1f), color = if (muted) c.onSurfaceVariant else c.onSurface)
            T(value, Sift.type.data, color = c.onSurfaceVariant)
        }
        T(detail, Sift.type.bodyS, Modifier.padding(start = 36.dp), color = c.onSurfaceVariant)
        if (running && fraction > 0f) LinearProgressIndicator(
            progress = { fraction }, modifier = Modifier.padding(start = 36.dp).fillMaxWidth().height(4.dp).clip(Shapes.full),
            color = c.primary, trackColor = c.containerHigh,
        )
    }
}
