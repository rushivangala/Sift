package com.sift.photos.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sift.photos.data.DocParser
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.SmartFeature
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.CenterMessage
import com.sift.photos.ui.components.ConfidenceMeter
import com.sift.photos.ui.components.Divider
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

/** "Getting your library ready": shows what the on-device index is doing and lets you pause it. */
@Composable
fun IndexProgressCard(vm: SiftViewModel) {
    val c = Sift.colors
    val p = vm.index.progress
    if (!p.running || p.total == 0) return
    SiftCard(spacing = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sym("hourglass_empty", tint = c.onSurface)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T("Getting your library ready", Sift.type.titleM, color = c.onSurface)
                T("%,d of %,d read · %s".format(p.done, p.total, p.stage), Sift.type.data, color = c.onSurfaceVariant)
            }
            OriginBadge()
        }
        LinearProgressIndicator(
            progress = { p.done.toFloat() / p.total },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(Shapes.full),
            color = c.primary,
            trackColor = c.containerHigh,
        )
        T(
            "Search covers the %d%% read so far. Sharing, editing and albums work now.".format(p.done * 100 / p.total),
            Sift.type.bodyS, color = c.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Pause", { vm.stopIndexing() }, kind = BtnKind.Outlined)
            Btn("See details", { vm.push(Screen.FirstSync) }, kind = BtnKind.Text)
        }
    }
}

// ---- Cleanup on your own library ----------------------------------------------------------

@Composable
fun RealCleanupScreen(vm: SiftViewModel) {
    val c = Sift.colors
    LaunchedEffect(vm.index.progress.running, vm.deviceItems.size) { vm.computeCleanup() }
    val groups = vm.cleanup
    Column(Modifier.fillMaxSize()) {
        TopBar("Cleanup", onBack = { vm.pop() }) {
            com.sift.photos.ui.components.IconBtn("delete", "Trash", { vm.push(Screen.Trash) })
        }
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            T("Scanned %,d items on this phone".format(vm.deviceItems.size), Sift.type.data, color = c.onSurfaceVariant)
            IndexProgressCard(vm)
            if (groups == null) {
                T("Looking for duplicates…", Sift.type.bodyL, color = c.onSurfaceVariant)
            } else {
                Column {
                    T(formatBytes(groups.sumOf { it.bytes }), Sift.type.displayL, color = c.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("could be freed after you review", Sift.type.bodyL, Modifier.weight(1f), color = c.onSurfaceVariant)
                        OriginBadge()
                    }
                }
                if (groups.isEmpty()) {
                    SiftCard { T("Nothing to clean up. No duplicates, blurry shots or old screenshots found.", Sift.type.bodyM, color = c.onSurfaceVariant) }
                } else {
                    SiftCard(spacing = 0.dp, padding = PaddingValues(0.dp)) {
                        groups.forEachIndexed { i, g ->
                            if (i > 0) Divider(Modifier.padding(horizontal = 16.dp))
                            Column(Modifier.fillMaxWidth().clickable { vm.push(Screen.CleanupGroupReview(g.key)) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(40.dp).clip(Shapes.m).background(c.containerHigh), contentAlignment = Alignment.Center) { Sym(g.icon, size = 22.dp) }
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        T(g.name, Sift.type.titleM, color = c.onSurface)
                                        T(g.count, Sift.type.bodyS, color = c.onSurfaceVariant)
                                    }
                                    T(formatBytes(g.bytes), Sift.type.dataMedium, color = c.onSurface)
                                }
                                WhyLine(g.why, icon = false)
                                ConfidenceMeter(g.confidence)
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clip(Shapes.card).background(c.containerLow).padding(16.dp)) {
                Sym("shield_lock", tint = c.onSurface)
                Spacer(Modifier.width(12.dp))
                T("%,d favorites are always protected, and anything edited in the last 30 days is never suggested.".format(vm.deviceItems.count { it.favorite }), Sift.type.bodyM, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            T("Nothing is deleted until you confirm.", Sift.type.bodyS, color = c.onSurfaceVariant)
        }
    }
}

@Composable
fun CleanupGroupReviewScreen(vm: SiftViewModel, key: String) {
    val c = Sift.colors
    val group = vm.cleanup?.firstOrNull { it.key == key } ?: run {
        LaunchedEffect(Unit) { vm.pop() }
        return
    }
    val marked = remember(key) { mutableStateListOf<String>().apply { addAll(group.suggested) } }
    val all = group.sets.flatten()
    Column(Modifier.fillMaxSize()) {
        TopBar(group.name, onBack = { vm.pop() }, backIcon = "close", backLabel = "Close review") {
            Btn(if (marked.isEmpty()) "Use suggestions" else "Keep all", {
                if (marked.isEmpty()) marked.addAll(group.suggested) else marked.clear()
            }, kind = BtnKind.Text)
        }
        if (group.sets.size == 1) {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhyLine(group.why)
                        TrustRow(group.confidence)
                        T("Tap to keep or remove. Marked photos go to Trash for 30 days.", Sift.type.bodyS, color = c.onSurfaceVariant)
                    }
                }
                items(group.sets[0], key = { it.key }) { item ->
                    PhotoCell(item, selectionMode = true, selected = item.key in marked, onClick = {
                        if (item.key in marked) marked.remove(item.key) else marked.add(item.key)
                    }, onLongClick = { vm.push(Screen.Viewer(group.sets[0], group.sets[0].indexOf(item))) })
                }
            }
        } else {
            Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WhyLine(group.why)
                group.sets.take(200).forEach { set ->
                    SiftCard(spacing = 10.dp) {
                        T(DocParser.formatDay(set.first().dateMillis) + " · ${set.size} shots", Sift.type.data, color = c.onSurfaceVariant)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            set.forEach { item ->
                                val keep = item.key !in marked
                                Box(
                                    Modifier.size(width = 110.dp, height = 132.dp).clip(Shapes.l)
                                        .then(if (keep) Modifier.border(3.dp, c.primary, Shapes.l) else Modifier)
                                        .clickable { if (keep) marked.add(item.key) else marked.remove(item.key) }
                                        .semantics { contentDescription = if (keep) "Keep" else "Will move to Trash"; selected = keep },
                                ) {
                                    PhotoView(item.ref, Modifier.fillMaxSize())
                                    Row(
                                        Modifier.align(Alignment.BottomStart).padding(6.dp).clip(Shapes.badge)
                                            .background(if (keep) c.primary else PhotoScrim).padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Sym(if (keep) "check" else "delete", size = 14.dp, weight = 500, tint = if (keep) c.onPrimary else Color.White)
                                        if (keep) { Spacer(Modifier.width(2.dp)); T("Keep", Sift.type.labelM, color = c.onPrimary) }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.containerLow).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T("${marked.size} marked", Sift.type.titleM, color = c.onSurface)
                T(formatBytes(all.filter { it.key in marked }.sumOf { it.sizeBytes }), Sift.type.data, color = c.onSurfaceVariant)
            }
            Btn("Continue", {
                CleanupPending.items = all.filter { it.key in marked }
                vm.push(Screen.CleanupConfirm)
            }, enabled = marked.isNotEmpty())
        }
    }
}

// ---- Photos that act, on your own library -------------------------------------------------

@Composable
fun RealActionsContent(vm: SiftViewModel) {
    val c = Sift.colors
    val found = vm.foundDocs.sortedByDescending { it.first.dateMillis }
    val expenses = vm.index.expenses
    Column(Modifier.fillMaxSize().scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IndexProgressCard(vm)
        if (found.isEmpty() && !vm.index.progress.running) {
            SiftCard {
                T("No receipts, tickets or cards found yet", Sift.type.titleM, color = c.onSurface)
                T("Sift reads text in your photos on this phone. Take a photo of a receipt or save a ticket screenshot and it appears here.", Sift.type.bodyM, color = c.onSurfaceVariant)
            }
        }
        if (found.isNotEmpty()) Overline("Suggested · not done yet")
        found.take(50).forEach { (item, doc) ->
            val low = doc.confidence < 0.7
            AiCard(
                icon = doc.kind.icon,
                eyebrow = if (low) "Not saved · ${doc.unsure.size.coerceAtLeast(1)} fields need you" else doc.kind.label,
                title = doc.title,
                why = doc.why,
                whyLead = if (low) "Why it's asking:" else "Why:",
                confidence = doc.confidence,
                accentTile = !low,
                thumb = { PhotoView(item.ref, Modifier.fillMaxSize()) },
                onClick = { vm.push(Screen.DocConfirm(item)) },
            ) {
                Btn("Review", { vm.push(Screen.DocConfirm(item)) }, kind = BtnKind.Text, color = c.primary)
                Spacer(Modifier.weight(1f))
                Btn("Not a ${doc.kind.name.lowercase()}", {
                    val id = vm.mediaId(item)
                    vm.index.decide(id, "doc")
                    vm.toast.show("Dismissed. Sift won't suggest it again.", icon = "block") { vm.index.decide(id, "doc", false) }
                }, kind = BtnKind.Text, color = c.onSurfaceVariant)
            }
        }
        if (expenses.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Overline("Expenses on this phone")
            SiftCard(spacing = 0.dp) {
                expenses.forEachIndexed { i, e ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(e.merchant, Sift.type.bodyL, color = c.onSurface)
                            T(e.date?.let { DocParser.formatDay(it) } ?: "No date", Sift.type.bodyS, color = c.onSurfaceVariant)
                        }
                        T(e.total, Sift.type.dataMedium, color = c.onSurface)
                    }
                }
            }
            ExportExpensesButton(vm)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ExportExpensesButton(vm: SiftViewModel) {
    val context = LocalContext.current
    Btn("Share as CSV", {
        val csv = buildString {
            appendLine("merchant,date,total")
            vm.index.expenses.forEach { e -> appendLine("\"${e.merchant.replace("\"", "'")}\",${e.date?.let { DocParser.formatDay(it) } ?: ""},${e.total}") }
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_TEXT, csv), "Share expenses"))
    }, kind = BtnKind.Outlined, icon = "share")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocConfirmScreen(vm: SiftViewModel, item: PhotoItem) {
    val c = Sift.colors
    val context = LocalContext.current
    val doc = vm.entry(item)?.doc ?: run {
        LaunchedEffect(Unit) { vm.pop() }
        return
    }
    val id = vm.mediaId(item)
    var email by remember { mutableStateOf(doc.email.singleOrNull()) }
    fun done(msg: String, icon: String, undo: (() -> Unit)?) {
        vm.index.decide(id, "doc"); vm.index.decide(id, "done")
        vm.pop()
        if (undo != null) vm.toast.show(msg, icon = icon) { undo(); vm.index.decide(id, "doc", false); vm.index.decide(id, "done", false) }
        else vm.toast.show(msg, icon = icon, onAction = null)
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(item.ref, 72.dp, radius = 12.dp, modifier = Modifier.clickable { vm.push(Screen.Viewer(listOf(item), 0)) })
                Spacer(Modifier.width(16.dp))
                Column {
                    T(DocParser.formatDay(item.dateMillis), Sift.type.titleM, color = c.onSurface)
                    T(item.name, Sift.type.data, color = c.onSurfaceVariant)
                }
            }
            Column {
                T("Suggested · not done yet", Sift.type.labelM, color = c.onSurfaceVariant)
                T(when (doc.kind) { DocParser.Kind.Receipt -> "Add to expenses"; DocParser.Kind.Ticket -> "Add to calendar"; DocParser.Kind.Card -> doc.title }, Sift.type.headlineS, color = c.onSurface)
            }
            SiftCard(spacing = 0.dp) {
                doc.fields.forEachIndexed { i, (k, v) ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(k, Sift.type.bodyM, Modifier.width(110.dp), color = c.onSurfaceVariant)
                        T(v, Sift.type.bodyL, color = c.onSurface)
                    }
                }
            }
            if (doc.kind == DocParser.Kind.Card && doc.email.size > 1) {
                SiftCard(spacing = 8.dp) {
                    T("Which email is it?", Sift.type.titleM, color = c.onSurface)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        doc.email.forEach { e -> SiftChip(e, email == e, { email = e }) }
                        SiftChip("Leave empty", email == "", { email = "" })
                    }
                }
            }
            doc.unsure.filter { it != "Email" }.takeIf { it.isNotEmpty() }?.let { unsure ->
                SiftCard(color = c.containerHigh, spacing = 6.dp) {
                    T("Not on the photo: ${unsure.joinToString(", ")}", Sift.type.titleS, color = c.onSurface)
                    T("You can fill these in the next step. Sift doesn't guess them.", Sift.type.bodyS, color = c.onSurfaceVariant)
                }
            }
            WhyLine(doc.why, lead = if (doc.confidence < 0.7) "Why it's asking:" else "Why:")
            TrustRow(doc.confidence)
            T("Read on this phone from the text in the photo. Nothing leaves the device.", Sift.type.bodyS, color = c.onSurfaceVariant)
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("Not now", { vm.pop() }, Modifier.weight(1f), kind = BtnKind.Outlined, large = true)
                when (doc.kind) {
                    DocParser.Kind.Receipt -> Btn("Add expense", {
                        vm.index.addExpense(id, doc.field("Merchant") ?: "Receipt", doc.total ?: "", doc.dateMillis)
                        done("Added to expenses on this phone", "receipt_long") { vm.index.removeExpense(id) }
                    }, Modifier.weight(1f), large = true)
                    DocParser.Kind.Ticket -> Btn("Add event", {
                        val start = doc.dateMillis ?: item.dateMillis
                        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, doc.title)
                            .putExtra(CalendarContract.Events.DESCRIPTION, doc.field("Seat") ?: "Added by Sift from ${item.name}")
                            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 2 * 3_600_000L)
                        try { context.startActivity(intent); done("Sent to Calendar · ${doc.title}", "event", null) }
                        catch (_: ActivityNotFoundException) { vm.toast.show("No calendar app found", icon = "info", onAction = null) }
                    }, Modifier.weight(1f), large = true)
                    DocParser.Kind.Card -> Btn("Save contact", {
                        val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                            .apply {
                                doc.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
                                doc.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
                                if (!email.isNullOrEmpty()) putExtra(ContactsContract.Intents.Insert.EMAIL, email)
                            }
                        try { context.startActivity(intent); done("Sent to Contacts", "contact_page", null) }
                        catch (_: ActivityNotFoundException) { vm.toast.show("No contacts app found", icon = "info", onAction = null) }
                    }, Modifier.weight(1f), large = true, enabled = doc.email.size <= 1 || email != null)
                }
            }
        }
    }
}

/** Whether the real library has anything for the Library home card. */
fun SiftViewModel.realActionCount(): Int =
    if (ai && settings.feature(SmartFeature.Documents)) foundDocs.size else 0
