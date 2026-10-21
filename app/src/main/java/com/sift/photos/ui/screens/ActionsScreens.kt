package com.sift.photos.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sift.photos.data.PhotoArt
import com.sift.photos.data.PhotoRef
import com.sift.photos.data.SampleData
import com.sift.photos.data.SmartFeature
import com.sift.photos.ui.Screen
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.AiCard
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.CenterMessage
import com.sift.photos.ui.components.ConfidenceMeter
import com.sift.photos.ui.components.Divider
import com.sift.photos.ui.components.Overline
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
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import java.util.Calendar

@Composable
fun ActionsScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val enabled = vm.ai && vm.settings.feature(SmartFeature.Documents)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            T("Actions", Sift.type.headlineL, Modifier.semantics { heading() }, color = c.onSurface)
            T("Receipts, tickets and cards in your photos. Nothing happens until you confirm.", Sift.type.bodyM, color = c.onSurfaceVariant)
        }
        when {
            !enabled -> Box(Modifier.fillMaxSize()) {
                CenterMessage(
                    "bolt", "Actions are paused",
                    "Receipts and documents is off in Smart features, so photos aren't read for text. Your library still works.",
                ) { Btn("Open Smart features", { vm.push(Screen.SmartFeatures) }, kind = BtnKind.Outlined) }
            }
            !vm.sample -> Box(Modifier.fillMaxSize()) {
                CenterMessage(
                    "bolt", "No actions yet",
                    "Reading receipts, tickets and cards needs the on-device text model, which this build doesn't include yet. Turn on the sample library in Settings to try the flow.",
                ) { Btn("Open Settings", { vm.push(Screen.Settings) }, kind = BtnKind.Outlined) }
            }
            else -> Column(Modifier.fillMaxSize().scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val pending = listOf("receipt", "ticket", "card").filter { it !in vm.actionsDone }
                if (pending.isNotEmpty()) Overline("Suggested · not done yet")
                if ("receipt" in pending) AiCard(
                    icon = "receipt_long", eyebrow = "Receipt → expense", title = "Cervejaria Ramiro · €86.40",
                    why = "merchant, date, total and VAT printed clearly; line items add up to the total.", confidence = 0.95,
                    thumb = { PhotoView(PhotoRef.Art(PhotoArt.ReceiptBrown), Modifier.fillMaxSize()) },
                    onClick = { vm.push(Screen.Receipt) },
                ) { Btn("Review", { vm.push(Screen.Receipt) }, kind = BtnKind.Text, color = c.primary) }
                if ("ticket" in pending) AiCard(
                    icon = "confirmation_number", eyebrow = "Ticket → calendar", title = "Rodrigo Leão · Sat 7 Nov, 21:30",
                    why = "ticket layout with venue, date, time and seat; matched the venue's address from your maps cache.", confidence = 0.93,
                    thumb = { PhotoView(PhotoRef.Art(PhotoArt.Shot), Modifier.fillMaxSize()) },
                    onClick = { vm.push(Screen.Ticket) },
                ) { Btn("Review", { vm.push(Screen.Ticket) }, kind = BtnKind.Text, color = c.primary) }
                if ("card" in pending) AiCard(
                    icon = "contact_page", eyebrow = "Not saved · 2 fields need you", title = "Save contact: Mara Lindqvist",
                    why = "glare covers part of the email, and the website is cut off at the card's edge.", whyLead = "Why it's asking:",
                    confidence = 0.62, accentTile = false,
                    thumb = { PhotoView(PhotoRef.Art(PhotoArt.Paper), Modifier.fillMaxSize()) },
                    onClick = { vm.push(Screen.Card) },
                ) { Btn("Answer 2 questions", { vm.push(Screen.Card) }, kind = BtnKind.Text, color = c.primary) }

                Spacer(Modifier.height(4.dp))
                Overline("Done · undo any time")
                if (!vm.flightUndone) {
                    AiCard(
                        icon = "event", eyebrow = "Added to Calendar", title = "Flight LH 1167 · Fri 24 Oct, 09:40",
                        why = "boarding pass in IMG_2041. Read airline, flight number, date and gate. Matched no existing event.",
                        confidence = 0.94,
                        thumb = { PhotoView(PhotoRef.Art(PhotoArt.Paper), Modifier.fillMaxSize()) },
                    ) {
                        Btn("Undo", {
                            vm.flightUndone = true
                            vm.toast.show("Flight LH 1167 removed from Calendar", icon = "event_busy") { vm.flightUndone = false }
                        }, kind = BtnKind.Text, icon = "undo", color = c.primary)
                        Btn("Edit event", { vm.toast.show("Opens the event in Calendar", icon = "event", onAction = null) }, kind = BtnKind.Text)
                        Spacer(Modifier.weight(1f))
                        Btn("Don't do this again", {
                            vm.toast.show("Boarding passes won't be added automatically", icon = "block", onAction = null)
                        }, kind = BtnKind.Text, color = c.onSurfaceVariant)
                    }
                }
                listOf("receipt" to "Expense added · Cervejaria Ramiro €86.40", "ticket" to "Sent to Calendar · Rodrigo Leão, Sat 7 Nov", "card" to "Sent to Contacts · Mara Lindqvist")
                    .filter { it.first in vm.actionsDone }
                    .forEach { (key, text) ->
                        SiftCard(spacing = 8.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Sym("check_circle", tint = c.primary, fill = true)
                                Spacer(Modifier.width(12.dp))
                                T(text, Sift.type.bodyM, Modifier.weight(1f), color = c.onSurface)
                                if (key == "receipt") Btn("Undo", {
                                    vm.actionsDone.remove(key)
                                    vm.toast.show("Expense removed", icon = "undo") { vm.actionsDone.add(key) }
                                }, kind = BtnKind.Text, color = c.primary)
                            }
                        }
                    }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SourceHeader(art: PhotoArt, date: String, file: String) {
    val c = Sift.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Thumb(PhotoRef.Art(art), 72.dp, radius = 12.dp)
        Spacer(Modifier.width(16.dp))
        Column {
            T(date, Sift.type.titleM, color = c.onSurface)
            T(file, Sift.type.data, color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun FieldRows(fields: List<Pair<String, String>>) {
    val c = Sift.colors
    SiftCard(spacing = 0.dp) {
        fields.forEachIndexed { i, (k, v) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                T(k, Sift.type.bodyM, Modifier.width(110.dp), color = c.onSurfaceVariant)
                T(v, Sift.type.bodyL, color = c.onSurface)
            }
        }
    }
}

@Composable
private fun ConfirmFooter(note: String?, primary: String, onPrimary: () -> Unit, secondary: String = "Edit", onSecondary: () -> Unit, primaryEnabled: Boolean = true) {
    val c = Sift.colors
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(secondary, onSecondary, Modifier.weight(1f), kind = BtnKind.Outlined, large = true)
            Btn(primary, onPrimary, Modifier.weight(1f), large = true, enabled = primaryEnabled)
        }
        if (note != null) T(note, Sift.type.bodyS, Modifier.align(Alignment.CenterHorizontally), color = c.onSurfaceVariant)
    }
}

/** [undoable] is false when the result now lives in another app, where Sift can't take it back. */
private fun SiftViewModel.complete(key: String, message: String, icon: String, undoable: Boolean = true) {
    actionsDone.add(key)
    pop()
    if (undoable) toast.show(message, icon = icon) { actionsDone.remove(key) } else toast.show(message, icon = icon, onAction = null)
}

@Composable
fun ReceiptScreen(vm: SiftViewModel) {
    val c = Sift.colors
    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SourceHeader(PhotoArt.ReceiptBrown, "Fri 15 May · 22:14", "IMG_2207.HEIC · Anjos, Lisbon")
            Column {
                T("Suggested · not done yet", Sift.type.labelM, color = c.onSurfaceVariant)
                T("Add to expenses", Sift.type.headlineS, color = c.onSurface)
            }
            Row(
                Modifier.heightIn(min = 48.dp).clip(Shapes.full).background(c.containerHigh).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("work", size = 20.dp)
                Spacer(Modifier.width(8.dp))
                T("Work · Expensify", Sift.type.labelL, color = c.onSurface)
                Spacer(Modifier.width(4.dp))
                Sym("expand_more", size = 20.dp)
            }
            FieldRows(SampleData.receiptFields)
            SiftCard(spacing = 8.dp) {
                T("Category · suggested", Sift.type.labelM, color = c.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Meals · client", Sift.type.titleM, Modifier.weight(1f), color = c.onSurface)
                    ConfidenceMeter(0.81)
                }
                T("You filed 6 similar dinners as Meals · client.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
            WhyLine("merchant, date, total and VAT printed clearly; line items add up to the total. You filed 6 similar dinners as Meals · client.")
            TrustRow(0.95)
        }
        ConfirmFooter(
            "Nothing is sent to Expensify until you tap Add.", "Add expense",
            { vm.complete("receipt", "Expense added to Work · Expensify", "receipt_long") },
            onSecondary = { vm.toast.show("Fields are editable in the full app", icon = "edit", onAction = null) },
        )
    }
}

@Composable
fun TicketScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val context = LocalContext.current
    var endSet by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SourceHeader(PhotoArt.Shot, "Fri 16 Oct · 13:20", "Screenshot_20261016.png")
            Column {
                T("Suggested · not done yet", Sift.type.labelM, color = c.onSurfaceVariant)
                T("Add to calendar", Sift.type.headlineS, color = c.onSurface)
            }
            SiftCard(spacing = 0.dp) {
                SampleData.ticketFields.forEachIndexed { i, f ->
                    if (i > 0) Divider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Sym(f.icon, tint = c.onSurfaceVariant)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            T(f.value, Sift.type.bodyL, color = c.onSurface)
                            T(f.detail, Sift.type.bodyS, color = c.onSurfaceVariant)
                        }
                    }
                }
            }
            SiftCard(color = if (endSet) c.containerLow else c.containerHigh, spacing = 8.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Sym("schedule", tint = c.onSurface)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(if (endSet) "Ends 23:30" else "Ends 23:30?", Sift.type.titleM, color = c.onSurface)
                        T(if (endSet) "Set by you" else "Not on the ticket · estimated 2 h · Low 0.52", Sift.type.bodyS, color = c.onSurfaceVariant)
                    }
                    if (!endSet) Btn("Set end", { endSet = true }, kind = BtnKind.Outlined)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym("event_available", tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                T("Free that evening in Personal and Work", Sift.type.bodyM, color = c.onSurfaceVariant)
            }
            WhyLine("ticket layout with venue, date, time and seat; matched the venue's address from your maps cache.")
            TrustRow(0.93)
        }
        ConfirmFooter(
            "Opens your calendar app to confirm. Nothing is added until you save there.", "Add event",
            {
                val start = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 7, 21, 30, 0) }.timeInMillis
                val end = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 7, 23, 30, 0) }.timeInMillis
                val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, "Rodrigo Leão")
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, "Coliseu dos Recreios, Rua das Portas de Santo Antão 96, Lisboa")
                    .putExtra(CalendarContract.Events.DESCRIPTION, "Plateia · Row F · Seat 12. Doors 20:30. Added by Sift from Screenshot_20261016.png.")
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
                try {
                    context.startActivity(intent)
                    vm.complete("ticket", "Sent to Calendar · Rodrigo Leão, Sat 7 Nov", "event", undoable = false)
                } catch (_: ActivityNotFoundException) {
                    vm.toast.show("No calendar app found", icon = "info", onAction = null)
                }
            },
            onSecondary = { vm.toast.show("Fields are editable in the full app", icon = "edit", onAction = null) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardScreen(vm: SiftViewModel) {
    val c = Sift.colors
    val context = LocalContext.current
    var email by remember { mutableStateOf<String?>(null) }
    var site by remember { mutableStateOf<String?>(null) }
    var cloudDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(null, onBack = { vm.pop() })
        Column(Modifier.weight(1f).scrollY().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SourceHeader(PhotoArt.Paper, "Thu 15 Oct · 17:46", "LX Factory, Lisbon")
            Column {
                T("Not saved · 2 fields need you", Sift.type.labelM, color = c.onSurfaceVariant)
                T("Save contact: Mara Lindqvist", Sift.type.headlineS, color = c.onSurface)
            }
            WhyLine("glare covers part of the email, and the website is cut off at the card's edge.", lead = "Why it's asking:")
            TrustRow(0.62)
            FieldRows(SampleData.cardFields)
            SiftCard(spacing = 8.dp) {
                T("Which email is it?", Sift.type.titleM, color = c.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SampleData.emailOptions.forEach { e -> SiftChip(e, email == e, { email = e }) }
                    SiftChip("Type it", email == "", { email = "" }, icon = "edit")
                }
            }
            SiftCard(spacing = 8.dp) {
                T("Website", Sift.type.titleM, color = c.onSurface)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SampleData.websiteOptions.forEach { w -> SiftChip(w, site == w, { site = w }) }
                    SiftChip("Leave empty", site == "", { site = "" })
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                Sym("cloud", size = 18.dp, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                T("Cloud reads only this card image, once, then deletes it. Nothing is kept or used for training. Undo removes the saved fields.", Sift.type.bodyS, color = c.onSurfaceVariant)
            }
        }
        ConfirmFooter(
            null, "Save contact",
            {
                val intent = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                    .putExtra(ContactsContract.Intents.Insert.NAME, "Mara Lindqvist")
                    .putExtra(ContactsContract.Intents.Insert.JOB_TITLE, "Head of Partnerships")
                    .putExtra(ContactsContract.Intents.Insert.COMPANY, "Nordvik Studio")
                    .putExtra(ContactsContract.Intents.Insert.PHONE, "+46 70 412 88 31")
                    .apply { if (!email.isNullOrEmpty()) putExtra(ContactsContract.Intents.Insert.EMAIL, email) }
                    .apply { if (!site.isNullOrEmpty()) putExtra(ContactsContract.Intents.Insert.NOTES, "https://$site") }
                try {
                    context.startActivity(intent)
                    vm.complete("card", "Sent to Contacts · Mara Lindqvist", "contact_page", undoable = false)
                } catch (_: ActivityNotFoundException) {
                    vm.toast.show("No contacts app found", icon = "info", onAction = null)
                }
            },
            secondary = "Read with cloud once",
            onSecondary = { cloudDialog = true },
            primaryEnabled = email != null && site != null,
        )
    }
    if (cloudDialog) {
        com.sift.photos.ui.components.SiftDialog(
            icon = "cloud",
            title = "Read this card with the cloud?",
            body = "Only this card image leaves the device, once, then it's deleted. Nothing is kept or used for training.",
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
