package com.sift.photos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.SampleData
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.OriginBadge
import com.sift.photos.ui.components.Overline
import com.sift.photos.ui.components.PhotoCell
import com.sift.photos.ui.components.Pill
import com.sift.photos.ui.components.RiseIn
import com.sift.photos.ui.components.scrollY
import com.sift.photos.ui.components.SiftCard
import com.sift.photos.ui.components.SiftChip
import com.sift.photos.ui.components.SiftDialog
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.Thumb
import com.sift.photos.ui.components.TrustRow
import com.sift.photos.ui.components.WhyLine
import com.sift.photos.data.PhotoRef
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

@Composable
fun AskScreen(vm: SiftViewModel, screen: Screen.Ask) {
    val c = Sift.colors
    var field by remember(screen) { mutableStateOf(TextFieldValue(screen.query, TextRange(screen.query.length))) }
    var submitted by remember(screen) { mutableStateOf(screen.submitted) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (!screen.submitted) focus.requestFocus() }

    fun submit(q: String) {
        if (q.isBlank()) return
        field = TextFieldValue(q, TextRange(q.length))
        submitted = true
        keyboard?.hide()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn("arrow_back", "Back", { vm.pop() }, tint = c.onSurface)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(Shapes.full)
                    .background(c.containerHigh)
                    .then(if (focused) Modifier.border(2.dp, c.primary, Shapes.full) else Modifier)
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (field.text.isEmpty()) T(if (vm.ai) "Ask your library" else "Dates, places, albums", Sift.type.bodyL, color = c.onSurfaceVariant)
                    BasicTextField(
                        value = field,
                        onValueChange = { field = it; submitted = false },
                        singleLine = true,
                        textStyle = Sift.type.bodyL.copy(color = c.onSurface),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(field.text) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
                    )
                }
                if (field.text.isNotEmpty()) {
                    IconBtn("close", "Clear", { field = TextFieldValue(""); submitted = false; focus.requestFocus() }, tint = c.onSurfaceVariant)
                    IconBtn("arrow_upward", "Search", { submit(field.text) }, tint = c.onPrimary, background = c.primary, visual = 40.dp, iconSize = 22.dp)
                }
            }
        }
        if (submitted) {
            Results(vm, field.text)
        } else {
            Typeahead(vm, field.text, onPick = { submit(it) }, onFill = { field = TextFieldValue(it, TextRange(it.length)) })
        }
    }
}

@Composable
private fun Typeahead(vm: SiftViewModel, q: String, onPick: (String) -> Unit, onFill: (String) -> Unit) {
    val c = Sift.colors
    val suggestions = if (vm.sample) {
        SampleData.typeahead.filter { q.isBlank() || it.text.startsWith(q.trim(), ignoreCase = true) || it.text.contains(q.trim(), ignoreCase = true) }
    } else {
        realSuggestions(vm, q)
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        suggestions.take(6).forEach { s ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Shapes.l).clickable { onPick(s.text) }.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym(s.icon, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    T(s.text, Sift.type.bodyL, color = c.onSurface, maxLines = 1)
                    T(s.meta, Sift.type.data, color = c.onSurfaceVariant, maxLines = 1)
                }
                IconBtn("north_west", "Fill in “${s.text}”", { onFill(s.text) }, tint = c.onSurfaceVariant)
            }
        }
        val understood = vm.sample && vm.ai && q.contains("receipt", true) && (q.isBlank() || "receipts from may".startsWith(q.trim().lowercase()))
        if (understood) {
            Spacer(Modifier.height(16.dp))
            SiftCard(spacing = 12.dp) {
                Overline("Top match understood as")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Type: receipt", c.primaryContainer, c.onPrimaryContainer, Modifier.heightIn(min = 32.dp), icon = "receipt_long", shape = Shapes.s)
                    Pill("1–31 May 2026", c.containerHigh, c.onSurface, Modifier.heightIn(min = 32.dp), icon = "calendar_month", shape = Shapes.s)
                }
                WhyLine("\"May\" read as your most recent May. Counts come from receipts already read on this phone.", icon = false)
                TrustRow(0.96)
            }
        }
        if (!vm.ai) {
            Spacer(Modifier.height(16.dp))
            T("Smart features are off, so search matches dates, places, albums and file names only.", Sift.type.bodyS, color = c.onSurfaceVariant)
        }
    }
}

private fun realSuggestions(vm: SiftViewModel, q: String): List<SampleData.Suggestion> {
    val needle = q.trim()
    val albums = vm.albums.filter { needle.isEmpty() || it.name.contains(needle, true) }
        .map { SampleData.Suggestion("photo_album", it.name, it.count) }
    val months = DateFormatSymbols(Locale.getDefault()).months.filter { it.isNotEmpty() && (needle.isEmpty() || it.startsWith(needle, true)) }
        .map { m ->
            val n = vm.deviceItems.count { monthName(it.dateMillis).equals(m, true) }
            SampleData.Suggestion("calendar_month", m, "%,d items".format(n))
        }.filter { !it.meta.startsWith("0 ") }
    return albums + months
}

private fun monthName(millis: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return DateFormatSymbols(Locale.getDefault()).months[cal.get(Calendar.MONTH)]
}

/** On-device metadata search: dates, album names, file names. Works with Smart features off. */
private fun metadataSearch(items: List<PhotoItem>, q: String): List<PhotoItem> {
    val words = q.lowercase(Locale.getDefault()).split(' ', ',').filter { it.length > 1 && it !in setOf("from", "in", "the", "at", "of", "photos", "my") }
    if (words.isEmpty()) return emptyList()
    return items.filter { item ->
        val cal = Calendar.getInstance().apply { timeInMillis = item.dateMillis }
        val hay = listOfNotNull(
            item.name, item.bucket, item.place, monthName(item.dateMillis), cal.get(Calendar.YEAR).toString(),
            if (item.duration != null || (item.ref as? PhotoRef.Media)?.isVideo == true) "video videos" else "photo",
            if (item.docIcon == "screenshot") "screenshot screenshots" else null,
            if (item.docIcon == "receipt_long") "receipt receipts document" else null,
        ).joinToString(" ").lowercase(Locale.getDefault())
        words.all { hay.contains(it) }
    }
}

@Composable
private fun Results(vm: SiftViewModel, q: String) {
    val receipts = vm.sample && vm.ai && q.contains("receipt", true) && q.contains("may", true)
    if (receipts) ReceiptResults(vm) else MetadataResults(vm, q)
}

@Composable
private fun ReceiptResults(vm: SiftViewModel) {
    val c = Sift.colors
    var included by remember { mutableStateOf<Boolean?>(null) }
    Column(Modifier.fillMaxSize().scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SiftChip("Receipts", true, { vm.replace(Screen.Ask("May", submitted = true)) }, trailingClose = true)
            SiftChip("May 2026", true, { vm.replace(Screen.Ask("receipts", submitted = true)) }, trailingClose = true)
            SiftChip("Filter", false, { vm.toast.show("More filters arrive in a later build", icon = "info", onAction = null) }, icon = "add")
        }
        T("1–31 May 2026 · totals in EUR", Sift.type.data, color = c.onSurfaceVariant)
        RiseIn("summary") {
            SiftCard(spacing = 14.dp) {
                val count = if (included == true) 16 else 14
                val total = if (included == true) "€651.30" else "€612.40"
                T("$count receipts · $total", Sift.type.headlineS, color = c.onSurface)
                WhyLine("date read from the printed receipt, not from when the photo was taken. 3 were photographed in June.")
                TrustRow(0.93)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Export CSV", { vm.toast.show("Exported 14 receipts to Downloads/receipts-may-2026.csv", icon = "download", onAction = null) }, kind = BtnKind.Outlined)
                    Btn("Add to expenses…", { vm.push(Screen.Receipt) })
                }
            }
        }
        if (included == null) {
            RiseIn("ask") {
                AiCard(
                    icon = "help",
                    eyebrow = "Asking before including",
                    title = "2 more might be from May",
                    why = "no date printed. Photos taken Sat 30 May and Tue 2 Jun.",
                    confidence = 0.58,
                    accentTile = false,
                    actionsDivider = false,
                ) {
                    Btn("Include both", { included = true })
                    Btn("Leave out", { included = false }, kind = BtnKind.Text)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Overline("Receipts by receipt date")
        SampleData.receipts.forEach { r ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(Shapes.l).clickable { vm.push(Screen.Receipt) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Thumb(PhotoRef.Art(r.art), 44.dp, modifier = Modifier.rotate(r.rotation))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    T(r.merchant, Sift.type.bodyL.copy(fontWeight = Sift.type.titleM.fontWeight), color = c.onSurface, maxLines = 1)
                    T(r.detail, Sift.type.bodyS, color = c.onSurfaceVariant, maxLines = 1)
                }
                T(r.total, Sift.type.dataMedium.copy(fontSize = Sift.type.bodyM.fontSize), color = c.onSurface)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MetadataResults(vm: SiftViewModel, q: String) {
    val c = Sift.colors
    val source = vm.libraryItems
    val results = remember(q, source, vm.index.entries.size) {
        if (!vm.sample && vm.ai) vm.search(q).map { it.first } else metadataSearch(source, q)
    }
    var cloudDialog by remember { mutableStateOf(false) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("${results.size} ${if (results.size == 1) "result" else "results"}", Sift.type.titleM, Modifier.weight(1f), color = c.onSurface)
                    OriginBadge()
                }
                T(
                    if (!vm.sample && vm.ai) "Matched on text and objects read on this phone, plus dates, places, albums and file names."
                    else "Matched on dates, places, albums and file names.",
                    Sift.type.bodyS, color = c.onSurfaceVariant,
                )
                if (results.isEmpty() && vm.ai) {
                    SiftCard {
                        T("Nothing matched on this phone", Sift.type.titleM, color = c.onSurface)
                        T("A larger model could read the question more loosely. It would need the cloud, and only if you say so.", Sift.type.bodyM, color = c.onSurfaceVariant)
                        Btn("Ask with the cloud…", { cloudDialog = true }, kind = BtnKind.Outlined, icon = "cloud")
                    }
                }
            }
        }
        items(results, key = { it.key }) { item ->
            PhotoCell(item, selectionMode = false, selected = false, onClick = {
                vm.push(Screen.Viewer(results, results.indexOf(item)))
            }, onLongClick = {})
        }
    }
    if (cloudDialog) {
        SiftDialog(
            icon = "cloud",
            title = "Use cloud for this search?",
            body = "This question needs a larger model. Your query and text from matching photos would leave the device, be processed once, then deleted. Nothing is kept or used for training.",
            confirm = "Keep on device",
            onConfirm = { cloudDialog = false },
            dismiss = "Use cloud once",
            onDismiss = { cloudDialog = false },
            onSecondary = {
                cloudDialog = false
                vm.toast.show("No cloud provider is set up in this build. Nothing left your phone.", icon = "memory", onAction = null)
            },
        )
    }
}
