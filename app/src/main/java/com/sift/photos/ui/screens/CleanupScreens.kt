package com.sift.photos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.data.SampleData
import com.sift.photos.ui.LocalActions
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.CenterMessage
import com.sift.photos.ui.components.ConfidenceMeter
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.HoldButton
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.OriginBadge
import com.sift.photos.ui.components.Overline
import com.sift.photos.ui.components.PhotoCell
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.SiftChip
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.Thumb
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.components.TrustRow
import com.sift.photos.ui.components.WhyLine
import com.sift.photos.ui.components.scrollY
import com.sift.photos.ui.theme.PhotoScrim
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatBytes(b: Long): String = when {
    b >= 1_000_000_000 -> "%.1f GB".format(b / 1e9)
    b >= 1_000_000 -> "%.0f MB".format(b / 1e6)
    b >= 1_000 -> "%.0f KB".format(b / 1e3)
    else -> "$b B"
}

private const val THIRTY_DAYS = 30L * 24 * 3600 * 1000

/** Real-library cleanup is limited to rules that need no model: screenshots older than 30 days. */
private fun oldScreenshots(vm: SiftViewModel): List<PhotoItem> {
    val cutoff = System.currentTimeMillis() - THIRTY_DAYS
    return vm.deviceItems.filter { it.docIcon == "screenshot" && it.dateMillis < cutoff && !it.favorite }
}

private fun recoverableUntil(): String =
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(System.currentTimeMillis() + THIRTY_DAYS))

@Composable
fun CleanupScreen(vm: SiftViewModel) {
    val c = Sift.colors
    Column(Modifier.fillMaxSize()) {
        TopBar("Cleanup", onBack = { vm.pop() }) {
            IconBtn("delete", "Trash", { vm.push(Screen.Trash) })
        }
        if (vm.sample && vm.cleanupDone) {
            Box(Modifier.fillMaxSize()) {
                CenterMessage("check_circle", "Cleanup reviewed", "342 items are in Trash and recoverable until ${recoverableUntil()}.") {
                    Btn("Open Trash", { vm.push(Screen.Trash) }, kind = BtnKind.Outlined)
                }
            }
            return@Column
        }
        val shots = if (vm.sample) emptyList() else oldScreenshots(vm)
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val scanned = if (vm.sample) "18,402" else "%,d".format(vm.deviceItems.size)
            T("Scanned $scanned items on this phone" + if (vm.sample) " · 2 min 10 s" else "", Sift.type.data, color = c.onSurfaceVariant)
            Column {
                T(if (vm.sample) "5.6 GB" else formatBytes(shots.sumOf { it.sizeBytes }), Sift.type.displayL, color = c.onSurface)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("could be freed after you review", Sift.type.bodyL, Modifier.weight(1f), color = c.onSurfaceVariant)
                    OriginBadge()
                }
            }
            SiftCard(spacing = 0.dp, padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                if (vm.sample) {
                    SampleData.cleanupGroups.forEachIndexed { i, g ->
                        if (i > 0) Divider(Modifier.padding(horizontal = 16.dp))
                        GroupRow(g.icon, g.name, g.count, g.size, g.why, g.confidence) { vm.push(Screen.CleanupReview) }
                    }
                } else {
                    GroupRow(
                        "screenshot", "Screenshots", "%,d older than 30 days".format(shots.size), formatBytes(shots.sumOf { it.sizeBytes }),
                        "in your Screenshots folder, older than 30 days, not a favorite.", 1.0,
                    ) { if (shots.isNotEmpty()) vm.push(Screen.CleanupReview) }
                    Divider(Modifier.padding(horizontal = 16.dp))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sym("hourglass_empty", tint = c.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        T("Duplicates, near-duplicates and blur need the on-device models, which this build doesn't include yet.", Sift.type.bodyS, color = c.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(Shapes.card).background(c.containerLow).padding(16.dp)) {
                Sym("shield_lock", tint = c.onSurface)
                Spacer(Modifier.width(12.dp))
                T(
                    (if (vm.sample) "1,206" else "%,d".format(vm.deviceItems.count { it.favorite })) +
                        " items always protected. Favorites, shared albums, people you named, and anything edited in the last 30 days are never suggested.",
                    Sift.type.bodyM, color = c.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Btn(
                if (vm.sample) "Review 4 groups" else "Review screenshots",
                { vm.push(Screen.CleanupReview) }, Modifier.fillMaxWidth(), large = true,
                enabled = vm.sample || shots.isNotEmpty(),
            )
            T("Nothing is deleted until you confirm.", Sift.type.bodyS, Modifier.padding(top = 6.dp), color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupRow(icon: String, name: String, count: String, size: String, why: String, conf: Double, onClick: () -> Unit) {
    val c = Sift.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(Shapes.m).background(c.containerHigh), contentAlignment = Alignment.Center) { Sym(icon, size = 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(name, Sift.type.titleM, color = c.onSurface)
                T(count, Sift.type.bodyS, color = c.onSurfaceVariant)
            }
            T(size, Sift.type.dataMedium, color = c.onSurface)
        }
        WhyLine(why, icon = false)
        ConfidenceMeter(conf)
    }
}

@Composable
fun CleanupReviewScreen(vm: SiftViewModel) {
    if (!vm.sample) { RealReview(vm); return }
    val c = Sift.colors
    // keep[setIndex] = index of the photo kept, or -1 for "keep all"; absent = not decided.
    val keep = remember { mutableStateMapOf<Int, Int>().apply { SampleData.bursts.forEachIndexed { i, b -> if (b.keep != null) put(i, b.keep) } } }
    val tab = remember { mutableStateListOf(1) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Near-duplicates", onBack = { vm.pop() }, backIcon = "close", backLabel = "Close review") {
            Btn("Keep all", { SampleData.bursts.indices.forEach { keep[it] = -1 } }, kind = BtnKind.Text)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Duplicates 214", "Near-dupes 96", "Blurry 61", "Screenshots 388").forEachIndexed { i, l ->
                SiftChip(l, tab.first() == i, { tab[0] = i })
            }
        }
        T("set 3 of 96", Sift.type.data, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = c.onSurfaceVariant)
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SampleData.bursts.forEachIndexed { si, set ->
                SiftCard(spacing = 12.dp) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        T(set.title, Sift.type.titleM, color = c.onSurface)
                        Spacer(Modifier.width(8.dp))
                        T(set.meta, Sift.type.data, color = c.onSurfaceVariant, maxLines = 1)
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        set.shots.forEachIndexed { pi, art ->
                            val k = keep[si]
                            val kept = k == -1 || k == pi
                            val decided = k != null
                            Box(
                                Modifier
                                    .size(width = if (pi == 0) 132.dp else 96.dp, height = 132.dp)
                                    .clip(Shapes.l)
                                    .then(if (kept && decided && k != -1) Modifier.border(3.dp, c.primary, Shapes.l) else Modifier)
                                    .clickable { keep[si] = pi }
                                    .semantics {
                                        contentDescription = "Shot ${pi + 1} of ${set.shots.size}" + when {
                                            !decided -> ", undecided"
                                            kept -> ", keep"
                                            else -> ", will move to Trash"
                                        }
                                        selected = kept && decided
                                    },
                            ) {
                                PhotoView(PhotoRef.Art(art), Modifier.fillMaxSize())
                                if (decided) {
                                    Row(
                                        Modifier.align(Alignment.BottomStart).padding(6.dp).clip(Shapes.badge)
                                            .background(if (kept) c.primary else PhotoScrim).padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Sym(if (kept) "check" else "delete", size = 14.dp, weight = 500, tint = if (kept) c.onPrimary else Color.White)
                                        if (kept) { Spacer(Modifier.width(2.dp)); T("Keep", Sift.type.labelM, color = c.onPrimary) }
                                    }
                                }
                            }
                        }
                    }
                    WhyLine(set.why, lead = if (set.keep != null) "Why keep this one:" else "Why it's asking:", icon = false)
                    TrustRow(set.confidence)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.containerLow).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T("289 marked", Sift.type.titleM, color = c.onSurface)
                T("3.3 GB · 2 groups left", Sift.type.data, color = c.onSurfaceVariant)
            }
            Btn("Continue", { vm.push(Screen.CleanupConfirm) })
        }
    }
}

@Composable
private fun RealReview(vm: SiftViewModel) {
    val c = Sift.colors
    val shots = remember { oldScreenshots(vm) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(shots.map { it.key }) } }
    Column(Modifier.fillMaxSize()) {
        TopBar("Screenshots", onBack = { vm.pop() }, backIcon = "close", backLabel = "Close review") {
            Btn(if (selected.isEmpty()) "Select all" else "Keep all", {
                if (selected.isEmpty()) selected.addAll(shots.map { it.key }) else selected.clear()
            }, kind = BtnKind.Text)
        }
        LazyVerticalGrid(
            GridCells.Fixed(3), Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(shots, key = { it.key }) { item ->
                PhotoCell(item, selectionMode = true, selected = item.key in selected, onClick = {
                    if (item.key in selected) selected.remove(item.key) else selected.add(item.key)
                }, onLongClick = {})
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.containerLow).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T("${selected.size} marked", Sift.type.titleM, color = c.onSurface)
                T(formatBytes(shots.filter { it.key in selected }.sumOf { it.sizeBytes }), Sift.type.data, color = c.onSurfaceVariant)
            }
            Btn("Move to Trash", {
                LocalPending.items = shots.filter { it.key in selected }
                vm.push(Screen.CleanupConfirm)
            }, enabled = selected.isNotEmpty())
        }
    }
}

/** Hand-off between review and confirm for the real library (screens hold no arguments for this). */
private object LocalPending { var items: List<PhotoItem> = emptyList() }

@Composable
fun CleanupConfirmScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val actions = LocalActions.current
    val real = !vm.sample
    val items = if (real) LocalPending.items else emptyList()
    val count = if (real) items.size else 342
    val size = if (real) formatBytes(items.sumOf { it.sizeBytes }) else "3.5 GB"
    val confirm = {
        if (real) {
            actions.trash(items)
            vm.popTo { it == Screen.Home }
        } else {
            vm.cleanupDone = true
            vm.popTo { it == Screen.Home }
            vm.toast.show("342 items moved to Trash", icon = "delete") { vm.cleanupDone = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T("Move $count ${if (count == 1) "item" else "items"} to Trash?", Sift.type.headlineL, color = c.onSurface)
            T("$size · " + if (real) "screenshots you reviewed" else "from 3 groups you reviewed", Sift.type.bodyL, color = c.onSurfaceVariant)
            if (!real) {
                SiftCard(spacing = 10.dp) {
                    listOf("Duplicates" to "214 · 1.8 GB", "Near-duplicates" to "75 · 1.5 GB", "Blurry" to "53 · 210 MB", "Screenshots" to "not reviewed · skipped").forEach { (n, v) ->
                        Row {
                            T(n, Sift.type.bodyM, Modifier.weight(1f), color = c.onSurface)
                            T(v, Sift.type.data, color = c.onSurfaceVariant)
                        }
                    }
                }
                InfoLine("shield_lock", "9 taken out of the selection. They're in the shared album \"Porto weekend\" with Tomás.")
            }
            if (count > 100) InfoLine("back_hand", "Over 100 items, so this needs a hold. A tap alone won't do it.")
            InfoLine("restore_from_trash", "Recoverable until ${recoverableUntil()}. Space is freed when Trash empties, or sooner if you empty it.")
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (count > 100) HoldButton("Hold to move $count items", onConfirmed = confirm)
            else Btn("Move $count to Trash", confirm, Modifier.fillMaxWidth(), large = true)
            Btn("Back to review", { vm.pop() }, kind = BtnKind.Text)
        }
    }
}

@Composable
fun InfoLine(icon: String, text: String) {
    val c = Sift.colors
    Row(verticalAlignment = Alignment.Top) {
        Sym(icon, tint = c.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        T(text, Sift.type.bodyM, color = c.onSurfaceVariant)
    }
}

@Composable
fun TrashScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val actions = LocalActions.current
    Column(Modifier.fillMaxSize()) {
        TopBar("Trash", onBack = { vm.pop() }) {
            if (!vm.sample && vm.deviceTrash.isNotEmpty()) Btn("Restore all", { actions.restore(vm.deviceTrash) }, kind = BtnKind.Text, color = c.primary)
        }
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Items stay 30 days, then are deleted for good. Restored items return to their original albums and dates.", Sift.type.bodyM, color = c.onSurfaceVariant)
            if (vm.sample) {
                if (vm.cleanupDone) {
                    SiftCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).clip(Shapes.m).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                                Sym("cleaning_services", size = 22.dp, tint = c.onPrimaryContainer)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                T("From Cleanup · Mon 19 Oct, 10:34", Sift.type.labelM, color = c.onSurfaceVariant)
                                T("342 items · 3.5 GB", Sift.type.titleM, color = c.onSurface)
                            }
                            T("30 d left", Sift.type.data, color = c.onSurfaceVariant)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Btn("Restore all 342", {
                                vm.cleanupDone = false
                                vm.toast.show("342 items restored", icon = "restore") { vm.cleanupDone = true }
                            }, icon = "restore")
                            Btn("Pick some", {}, kind = BtnKind.Text)
                        }
                    }
                }
                val trashed = vm.libraryItemsTrashed()
                trashed.forEach { item ->
                    TrashRowView(item.ref, item.name, "Deleted by you", "30 d", false) {
                        vm.sampleTrashed.remove(item.key)
                        vm.toast.show("Restored ${item.name}", icon = "restore") { vm.sampleTrashed.add(item.key) }
                    }
                }
                SampleData.trash.forEach { r ->
                    TrashRowView(PhotoRef.Art(r.art), r.name, r.sub, r.left, r.urgent) {
                        vm.toast.show("Sample items restore to their album in a real library", icon = "info", onAction = null)
                    }
                }
            } else if (vm.deviceTrash.isEmpty()) {
                Spacer(Modifier.height(80.dp))
                Box(Modifier.fillMaxWidth()) { CenterMessage("delete", "Trash is empty", "Deleted photos and videos wait here for 30 days.") }
            } else {
                T("%,d items · %s".format(vm.deviceTrash.size, formatBytes(vm.deviceTrash.sumOf { it.sizeBytes })), Sift.type.data, color = c.onSurfaceVariant)
                vm.deviceTrash.forEach { item ->
                    val days = TimeUnit.MILLISECONDS.toDays((item.expiresMillis - System.currentTimeMillis()).coerceAtLeast(0))
                    TrashRowView(item.ref, item.name, item.bucket ?: "", "$days d", days <= 3) { actions.restore(listOf(item)) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun SiftViewModel.libraryItemsTrashed(): List<PhotoItem> =
    SampleData.allItems.filter { it.key in sampleTrashed }

@Composable
private fun TrashRowView(ref: PhotoRef, name: String, sub: String, left: String, urgent: Boolean, onRestore: () -> Unit) {
    val c = Sift.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Thumb(ref, 52.dp, radius = 8.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(name, Sift.type.bodyL, color = c.onSurface, maxLines = 1)
            T(sub, Sift.type.bodyS, color = c.onSurfaceVariant, maxLines = 1)
        }
        T(left, Sift.type.dataMedium, color = if (urgent) c.error else c.onSurfaceVariant)
        IconBtn("restore", "Restore $name", onRestore, tint = c.onSurface)
    }
}
