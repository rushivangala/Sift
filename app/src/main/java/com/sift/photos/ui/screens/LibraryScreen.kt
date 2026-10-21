package com.sift.photos.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sift.photos.data.DaySection
import com.sift.photos.data.MediaAccess
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.SampleData
import com.sift.photos.data.SmartFeature
import com.sift.photos.ui.LocalActions
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.AskBarIdle
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.CenterMessage
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.PhotoCell
import com.sift.photos.ui.components.RadioRow
import com.sift.photos.ui.components.SelectionBar
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.SiftChip
import com.sift.photos.ui.components.SiftSheet
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.Tab
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift

/** The Sift mark: a 3 × 3 grid whose last tiles are still settling, round and translucent, into place. */
@Composable
fun SiftMark(size: Dp, tile: Color = Sift.colors.onSurface, background: Color? = null) {
    Canvas(Modifier.size(size).semantics { contentDescription = "Sift" }) {
        val u = this.size.width / 64f
        if (background != null) drawRoundRect(background, cornerRadius = CornerRadius(20 * u))
        fun r(x: Float, y: Float, rad: Float, a: Float = 1f) =
            drawRoundRect(tile.copy(alpha = a), Offset(x * u, y * u), Size(10 * u, 10 * u), CornerRadius(rad * u))
        r(14f, 14f, 3f); r(27f, 14f, 3f); r(40f, 14f, 3f)
        r(14f, 27f, 3f); r(27f, 27f, 3f); r(40f, 31f, 5f, 0.55f)
        r(14f, 40f, 3f); r(29f, 44f, 5f, 0.35f)
    }
}

/** Day sections laid into a 3-column, 2 dp-gutter grid. Shared by Library, Album and Locked folder. */
fun LazyGridScope.daySections(
    sections: List<DaySection>,
    selection: List<String>,
    onOpen: (PhotoItem) -> Unit,
    onToggle: (PhotoItem) -> Unit,
    selectionMode: Boolean,
) {
    sections.forEach { section ->
        item(key = "h:" + section.title + section.subtitle, span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp).semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.Bottom,
            ) {
                T(section.title, Sift.type.titleM, color = Sift.colors.onSurface)
                if (section.subtitle.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    T(section.subtitle, Sift.type.bodyM, color = Sift.colors.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        items(section.items, key = { it.key }) { item ->
            PhotoCell(
                item = item,
                selectionMode = selectionMode,
                selected = item.key in selection,
                onClick = { if (selectionMode) onToggle(item) else onOpen(item) },
                onLongClick = { onToggle(item) },
            )
        }
    }
}

@Composable
fun LibraryScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val actions = LocalActions.current
    val selection = remember { mutableStateListOf<String>() }
    var moveSheet by remember { mutableStateOf(false) }
    val sections = vm.sections
    val all = remember(sections) { sections.flatMap { it.items } }
    val selectionMode = selection.isNotEmpty()
    val showLibrary = vm.sample || vm.access != MediaAccess.None

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiftMark(28.dp, tile = c.surface, background = c.onSurface)
                        Spacer(Modifier.width(12.dp))
                        T("Sift", Sift.type.titleL, Modifier.weight(1f).semantics { heading() }, color = c.onSurface)
                        if (vm.ai && (vm.sample || vm.access != MediaAccess.None)) IconBtn("cleaning_services", "Cleanup", { vm.push(Screen.Cleanup) }, tint = c.onSurface)
                        IconBtn("settings", "Settings", { vm.push(Screen.Settings) }, tint = c.onSurface)
                    }
                    if (showLibrary) {
                        Spacer(Modifier.height(8.dp))
                        T(if (vm.ai && vm.settings.feature(SmartFeature.Search)) "Ask your photos" else "Search your photos", Sift.type.headlineL, color = c.onSurface)
                        Spacer(Modifier.height(16.dp))
                        AskBarIdle(
                            placeholder = if (vm.ai) "Receipts, people, places, anything" else "Dates, places, albums",
                            showOrigin = vm.ai,
                            onClick = { vm.push(Screen.Ask()) },
                            onVoice = { vm.push(Screen.Ask()) },
                        )
                        if (vm.sample && vm.ai) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SampleData.askSuggestions.forEach { q ->
                                    SiftChip(q, selected = false, onClick = { vm.push(Screen.Ask(q, submitted = true)) })
                                }
                            }
                        }
                        if (vm.access == MediaAccess.Partial && !vm.sample) {
                            Spacer(Modifier.height(12.dp))
                            PartialAccessCard(vm.deviceItems.size) { actions.requestAccess() }
                        }
                        if (vm.sample && vm.ai && vm.settings.feature(SmartFeature.Documents) && vm.actionsDone.size < 3) {
                            Spacer(Modifier.height(12.dp))
                            AiCard(
                                icon = "bolt",
                                eyebrow = "Found this week · not acted on",
                                title = "${3 - vm.actionsDone.size} photos can become actions",
                                why = "a receipt, a concert ticket and a business card in photos from 13–18 Oct.",
                                confidence = 0.91,
                                onClick = { vm.tab = Tab.Actions },
                            )
                        }
                    }
                }
            }
            if (showLibrary) {
                daySections(sections, selection, onOpen = { item ->
                    vm.push(Screen.Viewer(all, all.indexOf(item).coerceAtLeast(0)))
                }, onToggle = { item ->
                    if (item.key in selection) selection.remove(item.key) else selection.add(item.key)
                }, selectionMode = selectionMode)
            }
        }

        if (!showLibrary) {
            CenterMessage(
                icon = "photo_library",
                title = "Your library is empty",
                body = "Photos and videos on this phone appear here once Sift can see them. It all stays on this phone.",
            ) {
                Btn("Allow photo access", { actions.requestAccess() }, Modifier.fillMaxWidth(), large = true)
                Btn("Explore the sample library", { vm.settings.sampleLibrary = true }, Modifier.fillMaxWidth(), kind = BtnKind.Outlined, large = true)
            }
        } else if (vm.sections.isEmpty() && !vm.loading) {
            Box(Modifier.fillMaxSize().padding(top = 280.dp)) {
                CenterMessage(
                    icon = "photo_camera",
                    title = "No photos yet",
                    body = "Take a photo or copy some to this phone. Nothing is uploaded.",
                )
            }
        }

        if (selectionMode) {
            SelectionBar(
                count = selection.size,
                onClose = { selection.clear() },
                onShare = { actions.share(all.filter { it.key in selection }) },
                onMove = { moveSheet = true },
                onDelete = {
                    actions.trash(all.filter { it.key in selection })
                    selection.clear()
                },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (moveSheet) {
        MoveSheet(count = selection.size, onDismiss = { moveSheet = false }) {
            moveSheet = false
            val n = selection.size
            selection.clear()
            vm.toast.show(
                if (vm.sample) "$n photos queued to move to Home NAS" else "Add a storage location first. Nothing moved.",
                icon = if (vm.sample) "dns" else "info",
                onAction = null,
            )
        }
    }
}

@Composable
private fun PartialAccessCard(count: Int, onAllowAll: () -> Unit) {
    val c = Sift.colors
    SiftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym("photo_library", tint = c.onSurface)
            Spacer(Modifier.width(12.dp))
            T("Sift can see $count photos", Sift.type.titleM, color = c.onSurface)
        }
        T("You chose selected photos only. Everything else stays private and is not read.", Sift.type.bodyM, color = c.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Select more", onAllowAll, kind = BtnKind.Outlined)
            Btn("Allow all photos", onAllowAll, kind = BtnKind.Text, color = c.primary)
        }
    }
}

@Composable
fun MoveSheet(count: Int, onDismiss: () -> Unit, onMove: () -> Unit) {
    val c = Sift.colors
    var choice by remember { mutableStateOf(1) }
    SiftSheet(onDismiss) { close ->
        T("Move $count ${if (count == 1) "photo" else "photos"} to", Sift.type.titleL, color = c.onSurface)
        T("Originals move. Nothing is re-encoded.", Sift.type.bodyM, Modifier.padding(bottom = 6.dp), color = c.onSurfaceVariant)
        listOf(
            Triple("smartphone", "This phone", "41.2 GB free"),
            Triple("dns", "Home NAS", "1.6 TB free · local network"),
            Triple("cloud", "Dropbox", "88 GB free · cloud"),
        ).forEachIndexed { i, (icon, name, sub) ->
            RadioRow(icon, name, sub, selected = choice == i, onClick = { choice = i })
        }
        Spacer(Modifier.height(10.dp))
        val target = listOf("this phone", "Home NAS", "Dropbox")[choice]
        Btn("Move to $target", { onMove(); close() }, Modifier.fillMaxWidth(), large = true)
    }
}

@Composable
fun SectionGap(h: Dp = 24.dp) = Spacer(Modifier.height(h))

@Composable
fun MonoTag(text: String) {
    val c = Sift.colors
    Box(Modifier.clip(Shapes.s).background(c.containerHigh).padding(horizontal = 8.dp, vertical = 4.dp)) {
        T(text, Sift.type.data, color = c.onSurfaceVariant)
    }
}
