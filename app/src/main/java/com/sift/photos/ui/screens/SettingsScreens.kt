package com.sift.photos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sift.photos.BuildConfig
import com.sift.photos.data.AccentSource
import com.sift.photos.data.PhotoArt
import com.sift.photos.data.PhotoRef
import com.sift.photos.data.SmartFeature
import com.sift.photos.data.ThemeMode
import com.sift.photos.ui.LocalActions
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.Overline
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.SiftChip
import com.sift.photos.ui.components.SiftDialog
import com.sift.photos.ui.components.SiftToggle
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.ToggleRow
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.components.scrollY
import com.sift.photos.ui.theme.AccentPipeline
import com.sift.photos.ui.theme.ColorMath
import com.sift.photos.ui.theme.CuratedAccent
import com.sift.photos.ui.theme.Neutrals
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import com.sift.photos.ui.theme.toColor

private val photoSamples = listOf(
    PhotoArt.SampleBay to 0x2F7FB5, PhotoArt.SampleSunset to 0xC2453A,
    PhotoArt.SampleField to 0xE2B93B, PhotoArt.SampleForest to 0x3C7D4E,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val s = vm.settings
    Column(Modifier.fillMaxSize()) {
        TopBar("Settings", onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Overline("Appearance")
            SiftCard(spacing = 14.dp) {
                T("Theme", Sift.type.titleM, color = c.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m -> SiftChip(m.name, s.themeMode == m, { s.themeMode = m }) }
                }
                Row(
                    Modifier.toggleable(s.trueBlack, role = Role.Switch) { s.trueBlack = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        T("True black", Sift.type.bodyL, color = c.onSurface)
                        T("OLED pixels off in dark theme", Sift.type.bodyS, color = c.onSurfaceVariant)
                    }
                    SiftToggle(s.trueBlack, null)
                }
                Divider()
                T("Accent", Sift.type.titleM, color = c.onSurface)
                SourceTabs(s.accentSource) { s.accentSource = it }
                when (s.accentSource) {
                    AccentSource.Wallpaper -> {
                        val seed = vm.wallpaperSeed()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(Shapes.m).background((seed ?: s.curated.seed).toColor()))
                            Spacer(Modifier.width(12.dp))
                            T(
                                if (seed != null) "Material You · from your wallpaper" else "Wallpaper colors unavailable · using ${s.curated.label}",
                                Sift.type.bodyM, color = c.onSurfaceVariant,
                            )
                        }
                    }
                    AccentSource.Curated -> {
                        T("Six accents, each tuned to stay quiet next to skin, sky and foliage.", Sift.type.bodyS, color = c.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CuratedAccent.entries.forEach { a ->
                                val role = AccentPipeline.roles(a.seed, if (c.isDark) (if (c.isBlack) Neutrals.Black else Neutrals.Dark) else Neutrals.Light).primary.toColor()
                                val sel = s.curated == a
                                Column(
                                    Modifier.selectable(sel, role = Role.RadioButton) { s.curated = a }.semantics { contentDescription = "${a.label} accent" },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(
                                        Modifier.size(48.dp).then(if (sel) Modifier.border(3.dp, c.onSurface, CircleShape) else Modifier).padding(5.dp)
                                            .clip(CircleShape).background(role),
                                        contentAlignment = Alignment.Center,
                                    ) { if (sel) Sym("check", size = 20.dp, weight = 500, tint = c.onPrimary) }
                                    T(a.label, Sift.type.labelM, color = c.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    AccentSource.Photo -> {
                        T("In any photo: More → Use its color. Samples the most prominent non-skin, non-neutral color, on device.", Sift.type.bodyS, color = c.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photoSamples.forEach { (art, hex) ->
                                val sel = s.photoSeed == hex
                                Box(
                                    Modifier.weight(1f).height(72.dp).clip(Shapes.l)
                                        .then(if (sel) Modifier.border(3.dp, c.onSurface, Shapes.l) else Modifier)
                                        .selectable(sel, role = Role.RadioButton) { s.photoSeed = hex }
                                        .semantics { contentDescription = "Sample photo, ${ColorMath.hex(hex)}" },
                                ) { PhotoView(PhotoRef.Art(art), Modifier.fillMaxSize()) }
                            }
                        }
                    }
                }
                PipelineNote(vm.seed())
            }

            Overline("Library")
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                ToggleRow(
                    "Demo library",
                    if (s.sampleLibrary) "On. You're seeing made-up photos (Lisbon, Oct 2026) that show every feature. Turn off to see your own photos."
                    else "Off. Turn on to try every feature with made-up photos. Your own photos are never changed.",
                    s.sampleLibrary, { s.sampleLibrary = it },
                )
                if (!s.sampleLibrary) {
                    val actions = LocalActions.current
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val managed = remember(vm.access) { android.provider.MediaStore.canManageMedia(ctx) }
                    Divider(Modifier.padding(horizontal = 16.dp))
                    LinkRow(
                        "verified", "Media management",
                        if (managed) "Allowed · favorite and trash without asking each time" else "Allow so favorites and Trash don't ask each time",
                    ) {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_REQUEST_MANAGE_MEDIA)
                                    .setData(android.net.Uri.parse("package:" + ctx.packageName))
                            )
                        }
                    }
                    Divider(Modifier.padding(horizontal = 16.dp))
                    LinkRow("photo_library", "Photo access", when (vm.access) {
                        com.sift.photos.data.MediaAccess.Full -> "All photos and videos"
                        com.sift.photos.data.MediaAccess.Partial -> "Selected photos only · change"
                        com.sift.photos.data.MediaAccess.None -> "Not allowed · tap to allow"
                    }) { actions.requestAccess() }
                }
            }

            Overline("Storage")
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                LinkRow("hard_drive", "Storage and locations", if (vm.sample) "Home NAS 412 GB of 2 TB" else "This phone only") {
                    vm.popTo { it == Screen.Home }; vm.tab = com.sift.photos.ui.components.Tab.Storage
                }
                Divider(Modifier.padding(horizontal = 16.dp))
                LinkRow("delete", "Trash", "Items stay 30 days") { vm.push(Screen.Trash) }
            }

            Overline("Privacy")
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                ToggleRow("Strip location when sharing", "Default for new shares", s.stripLocation, { s.stripLocation = it })
                ToggleRow("Require biometrics for Locked folder", null, s.lockedBiometrics, { s.lockedBiometrics = it }, divider = true)
                ToggleRow("Hide previews in app switcher", null, s.hidePreviews, { s.hidePreviews = it }, divider = true)
            }

            Overline("Smart features")
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                LinkRow("memory", "Smart features", if (s.aiEnabled) "On · all on this phone" else "Off · library still works") { vm.push(Screen.SmartFeatures) }
            }

            Overline("About")
            SiftCard(spacing = 6.dp) {
                T("Sift ${BuildConfig.VERSION_NAME}", Sift.type.titleM, color = c.onSurface)
                T("No account. No ads. No storage to buy. Photos stay plain files.", Sift.type.bodyS, color = c.onSurfaceVariant)
                T("Hanken Grotesk and IBM Plex Mono (SIL OFL 1.1) · Material Symbols (Apache 2.0)", Sift.type.data, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun SourceTabs(current: AccentSource, onSelect: (AccentSource) -> Unit) {
    val c = Sift.colors
    Row(Modifier.fillMaxWidth().clip(Shapes.full).background(c.containerHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            Triple(AccentSource.Wallpaper, "wallpaper", "Wallpaper"),
            Triple(AccentSource.Curated, "palette", "Curated"),
            Triple(AccentSource.Photo, "colorize", "Photo"),
        ).forEach { (src, icon, label) ->
            val sel = src == current
            Row(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(Shapes.full).background(if (sel) c.inverseSurface else c.containerHigh)
                    .selectable(sel, role = Role.Tab) { onSelect(src) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                val fg = if (sel) c.inverseOnSurface else c.onSurface
                Sym(icon, size = 20.dp, weight = 400, tint = fg)
                Spacer(Modifier.width(6.dp))
                T(label, Sift.type.labelL, color = fg, maxLines = 1)
            }
        }
    }
}

/** "seed → clamp tone · cap chroma · verify ≥ 4.5:1 · step tone if short", with the steps logged. */
@Composable
private fun PipelineNote(seed: Int) {
    val c = Sift.colors
    val steps = listOf("Light" to Neutrals.Light, "Dark" to Neutrals.Dark, "True black" to Neutrals.Black)
        .mapNotNull { (label, n) -> AccentPipeline.roles(seed, n).steps.takeIf { it > 0 }?.let { "$label stepped ${it * 3} tones" } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).clip(Shapes.badge).background(seed.toColor()))
        Spacer(Modifier.width(8.dp))
        T(
            "seed ${ColorMath.hex(seed)} → clamp tone · cap chroma · verify ≥ 4.5:1 · " +
                (if (steps.isEmpty()) "no adjustment needed" else steps.joinToString(", ")),
            Sift.type.data, color = c.onSurfaceVariant,
        )
    }
}

@Composable
fun SmartFeaturesScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val s = vm.settings
    var deleteDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Smart features", onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SiftCard(color = if (s.aiEnabled) c.primaryContainer else c.containerHigh, spacing = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T("Smart features", Sift.type.titleL, color = if (s.aiEnabled) c.onPrimaryContainer else c.onSurface)
                        T(
                            if (s.aiEnabled) "Master switch. Off stops every feature below and clears nothing."
                            else "Off. Search, faces, suggestions and cleanup are paused. Your library still works.",
                            Sift.type.bodyM, color = if (s.aiEnabled) c.onPrimaryContainer else c.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    SiftToggle(s.aiEnabled, { s.aiEnabled = it })
                }
            }
            if (s.aiEnabled) {
                Row(Modifier.fillMaxWidth().clip(Shapes.l).background(c.containerLow).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.sift.photos.ui.components.OriginBadge()
                    Spacer(Modifier.width(10.dp))
                    T("All features run on this phone. Cloud processing is off and asks each time.", Sift.type.bodyS, color = c.onSurfaceVariant)
                }
            }
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp), modifier = Modifier.alpha(1f)) {
                SmartFeature.entries.forEachIndexed { i, f ->
                    ToggleRow(
                        f.title, f.sub, s.featureSetting(f) && s.aiEnabled, { s.setFeature(f, it) },
                        icon = f.icon, enabled = s.aiEnabled, divider = i > 0,
                    )
                }
            }
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                ToggleRow("Cloud processing", if (s.cloudProcessing) "On · still asks each time" else "Off · asks each time", s.cloudProcessing, { s.cloudProcessing = it }, icon = "cloud", enabled = s.aiEnabled)
            }
            if (s.aiEnabled) {
                T("Every suggestion shows why it was made and how sure Sift is, and can be undone.", Sift.type.bodyS, color = c.onSurfaceVariant)
            } else {
                Row {
                    Sym("info", tint = c.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    T("Existing faces and tags stay on this phone. Delete AI data removes them.", Sift.type.bodyS, color = c.onSurfaceVariant)
                }
            }
            Btn("Delete AI data…", { deleteDialog = true }, kind = BtnKind.Outlined, icon = "delete")
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
    if (deleteDialog) {
        SiftDialog(
            icon = "delete",
            title = "Delete AI data?",
            body = "Removes faces, names, read text and suggestions from this phone. Photos and albums you made are untouched. Features rebuild their data if you turn them on again.",
            confirm = "Keep data",
            onConfirm = { deleteDialog = false },
            dismiss = "Delete",
            onDismiss = { deleteDialog = false },
            onSecondary = {
                deleteDialog = false
                vm.actionsDone.clear(); vm.editsReverted.clear(); vm.cleanupDone = false
                vm.toast.show("AI data deleted from this phone", icon = "delete", onAction = null)
            },
        )
    }
}

@Composable
fun OnboardingScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(32.dp))
            SiftMark(64.dp, tile = c.surface, background = c.onSurface)
            T("Allow what Sift needs", Sift.type.headlineL, color = c.onSurface)
            T("Everything is processed on this phone. You can change each permission later in Settings.", Sift.type.bodyL, color = c.onSurfaceVariant)
            SiftCard(spacing = 0.dp, padding = PaddingValues(vertical = 4.dp)) {
                listOf(
                    Triple("photo_library", "Photos and videos", "Needed to build your library. Files never move without your say.") to "Required",
                    Triple("location_on", "Photo locations", "Reads GPS stored in your photos for the info panel.") to "Optional",
                    Triple("fingerprint", "Biometrics", "Unlocks the Locked folder. Sift never sees your fingerprint.") to "Optional",
                ).forEachIndexed { i, (p, tag) ->
                    if (i > 0) Divider(Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Sym(p.first, tint = c.onSurface)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            T(p.second, Sift.type.titleM, color = c.onSurface)
                            T(p.third, Sift.type.bodyM, color = c.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.clip(Shapes.badge).background(if (i == 0) c.containerHigh else c.containerLow).padding(horizontal = 8.dp, vertical = 2.dp)) {
                            T(tag, Sift.type.labelM, color = c.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym("memory", size = 18.dp, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                T("No account needed. Nothing is uploaded.", Sift.type.bodyM, color = c.onSurfaceVariant)
            }
        }
        // Equal weight on purpose: the audit removed the nudge toward full access.
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Allow photo access", {
                actions.requestAccess()
                vm.finishOnboarding(sample = false)
            }, Modifier.fillMaxWidth(), large = true)
            Btn("Explore with the demo library", { vm.finishOnboarding(sample = true) }, Modifier.fillMaxWidth(), kind = BtnKind.Outlined, large = true)
        }
    }
}
