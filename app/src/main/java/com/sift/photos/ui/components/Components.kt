package com.sift.photos.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sift.photos.data.Band
import com.sift.photos.data.bandOf
import com.sift.photos.ui.theme.Motion
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Text helpers
// ---------------------------------------------------------------------------------------------

@Composable
fun T(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(
        text = text,
        style = style,
        color = if (color == Color.Unspecified) LocalContentColor.current else color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = align,
    )
}

/** Mono overline label used for section names (e.g. "BY TYPE"). */
@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    T(text.uppercase(), Sift.type.overline, modifier.semantics { heading() }, color = Sift.colors.onSurfaceVariant)
}

// ---------------------------------------------------------------------------------------------
// Buttons. Every control has a 48 dp touch target even when it draws at 40 dp.
// ---------------------------------------------------------------------------------------------

@Composable
fun IconBtn(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    fill: Boolean = false,
    background: Color = Color.Transparent,
    visual: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(visual).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
            Sym(icon, size = iconSize, tint = tint, fill = fill)
        }
    }
}

enum class BtnKind { Filled, Tonal, Outlined, Text }

@Composable
fun Btn(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Filled,
    icon: String? = null,
    large: Boolean = false,
    color: Color? = null,
    enabled: Boolean = true,
) {
    val c = Sift.colors
    val bg = when (kind) {
        BtnKind.Filled -> c.primary
        BtnKind.Tonal -> c.containerHigh
        else -> Color.Transparent
    }
    val fg = color ?: when (kind) {
        BtnKind.Filled -> c.onPrimary
        else -> c.onSurface
    }
    val h = if (large) 48.dp else 40.dp
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(Shapes.full)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = h)
                .clip(Shapes.full)
                .background(bg)
                .then(if (kind == BtnKind.Outlined) Modifier.border(1.dp, c.outline, Shapes.full) else Modifier)
                .padding(horizontal = if (kind == BtnKind.Text) 12.dp else if (large) 24.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Sym(icon, size = 18.dp, weight = 500, tint = fg)
                Spacer(Modifier.width(6.dp))
            }
            T(text, if (large) Sift.type.titleS else Sift.type.labelL.copy(fontWeight = FontWeight.SemiBold), color = fg, maxLines = 2, align = TextAlign.Center)
        }
    }
}

/** Over 100 items, a bulk delete needs a 1.5 s press-and-hold. A tap alone won't do it. */
@Composable
fun HoldButton(text: String, holdMillis: Long = 1500, onConfirmed: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sift.colors
    val haptics = LocalHapticFeedback.current
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (holding) {
            val start = System.nanoTime()
            while (holding && progress < 1f) {
                progress = ((System.nanoTime() - start) / 1e6f / holdMillis).coerceAtMost(1f)
                delay(16)
            }
            if (progress >= 1f) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirmed()
            }
        } else {
            progress = 0f
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(Shapes.full)
                .background(c.primaryContainer)
                .drawBehind {
                    drawRoundRect(c.primary, size = size.copy(width = size.width * progress), cornerRadius = CornerRadius(size.height / 2))
                }
                .semantics { contentDescription = "$text. Press and hold for 1.5 seconds." }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        holding = true
                        waitForUpOrCancellation()
                        holding = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val fg = if (progress > 0.5f) c.onPrimary else c.onPrimaryContainer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sym("touch_app", size = 20.dp, weight = 400, tint = fg)
                Spacer(Modifier.width(8.dp))
                T(text, Sift.type.titleS, color = fg)
            }
        }
        Spacer(Modifier.height(6.dp))
        T(
            if (progress > 0f) "holding · %.1f of %.1f s".format(progress * holdMillis / 1000f, holdMillis / 1000f) else "Press and hold",
            Sift.type.data, color = c.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Trust pattern: reason, confidence and origin on every AI element.
// ---------------------------------------------------------------------------------------------

/** On device is filled; cloud is always outlined so it never reads as the default. */
@Composable
fun OriginBadge(cloud: Boolean = false, text: String? = null, compact: Boolean = true) {
    val c = Sift.colors
    val label = text ?: if (cloud) "Cloud" else "On device"
    Row(
        Modifier
            .heightIn(min = if (compact) 24.dp else 28.dp)
            .clip(Shapes.badge)
            .then(if (cloud) Modifier.border(1.dp, c.outline, Shapes.badge) else Modifier.background(c.containerHigh))
            .padding(start = 6.dp, end = 8.dp)
            .semantics { contentDescription = if (cloud) "Processed in the cloud: $label" else "Processed on this device" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(if (cloud) "cloud" else "memory", size = 15.dp, weight = 400, tint = c.onSurface)
        Spacer(Modifier.width(4.dp))
        T(label, Sift.type.labelM, color = c.onSurface, maxLines = 1)
    }
}

@Composable
fun ConfidenceMeter(confidence: Double, modifier: Modifier = Modifier) {
    val c = Sift.colors
    val band = bandOf(confidence)
    val filled = when {
        confidence >= 0.90 -> 4
        confidence >= 0.70 -> 3
        confidence >= 0.55 -> 2
        else -> 1
    }
    val bandName = when (band) { Band.High -> "High"; Band.Medium -> "Medium"; Band.Low -> "Low" }
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "$bandName confidence, %.2f".format(confidence)
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(14.dp, 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .then(if (i < filled) Modifier.background(c.primary) else Modifier.border(1.dp, c.outline, RoundedCornerShape(3.dp)))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        T("${if (band == Band.Medium) "Medium" else band.label} · %.2f".format(confidence), Sift.type.dataMedium, color = c.onSurface)
    }
}

@Composable
fun TrustRow(confidence: Double, cloud: Boolean = false, originText: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ConfidenceMeter(confidence)
        Spacer(Modifier.weight(1f))
        OriginBadge(cloud, originText)
    }
}

@Composable
fun WhyLine(text: String, lead: String = "Why:", icon: Boolean = true, modifier: Modifier = Modifier) {
    val c = Sift.colors
    Row(modifier) {
        if (icon) {
            Sym("info", size = 18.dp, tint = c.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = c.onSurface, fontWeight = FontWeight.Medium)) { append(lead) }
                append(" ")
                append(text)
            },
            style = Sift.type.bodyM,
            color = c.onSurfaceVariant,
        )
    }
}

/**
 * AI result card: what was found or done, why, how sure, and where it ran.
 * High ≥ 0.90 may act (always with Undo); Medium suggests; Low asks.
 */
@Composable
fun AiCard(
    icon: String,
    eyebrow: String,
    title: String,
    why: String,
    confidence: Double,
    modifier: Modifier = Modifier,
    cloud: Boolean = false,
    originText: String? = null,
    accentTile: Boolean = true,
    whyLead: String = "Why:",
    thumb: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    actionsDivider: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Sift.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(c.containerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(Shapes.m).background(if (accentTile) c.primaryContainer else c.containerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Sym(icon, size = 22.dp, weight = if (accentTile) 400 else 300, tint = if (accentTile) c.onPrimaryContainer else c.onSurface)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                T(eyebrow, Sift.type.labelM, color = c.onSurfaceVariant)
                T(title, Sift.type.titleM.copy(lineHeight = Sift.type.titleM.fontSize * 1.375f), color = c.onSurface)
            }
            if (thumb != null) {
                Spacer(Modifier.width(12.dp))
                Box(Modifier.size(44.dp).clip(Shapes.s)) { thumb() }
            }
        }
        WhyLine(why, lead = whyLead)
        TrustRow(confidence, cloud, originText)
        if (actions != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (actionsDivider) Modifier.drawBehind { drawLine(c.outlineVariant, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
                    .padding(top = if (actionsDivider) 6.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = actions,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Inputs and selection controls
// ---------------------------------------------------------------------------------------------

@Composable
fun SiftChip(label: String, selected: Boolean, onClick: () -> Unit, icon: String? = null, trailingClose: Boolean = false) {
    val c = Sift.colors
    val bg by animateColorAsState(if (selected) c.primaryContainer else Color.Transparent, Motion.effectsFast(Sift.reduceMotion), label = "chip")
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .clip(Shapes.s)
                .background(bg)
                .then(if (!selected) Modifier.border(1.dp, c.outline, Shapes.s) else Modifier)
                .padding(start = if (selected || icon != null) 8.dp else 14.dp, end = if (trailingClose) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val fg = if (selected) c.onPrimaryContainer else c.onSurface
            when {
                selected && !trailingClose -> { Sym("check", size = 18.dp, weight = 500, tint = fg); Spacer(Modifier.width(6.dp)) }
                icon != null -> { Sym(icon, size = 18.dp, tint = fg); Spacer(Modifier.width(6.dp)) }
            }
            T(label, Sift.type.labelL, color = fg, maxLines = 1)
            if (trailingClose) { Spacer(Modifier.width(4.dp)); Sym("close", size = 18.dp, tint = fg) }
        }
    }
}

/** Toggle 52 × 32. On: accent track with a check in the thumb. Off: outlined track. */
@Composable
fun SiftToggle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val c = Sift.colors
    val reduce = Sift.reduceMotion
    val offset by animateDpAsState(if (checked) 24.dp else 6.dp, Motion.chrome(reduce), label = "thumbX")
    val thumb by animateDpAsState(if (checked) 24.dp else 16.dp, Motion.chrome(reduce), label = "thumb")
    val track by animateColorAsState(if (checked) c.primary else c.containerHigh, Motion.effectsFast(reduce), label = "track")
    Box(
        Modifier
            .size(52.dp, 48.dp)
            .then(
                if (onCheckedChange != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                else Modifier
            )
            .alpha(if (enabled) 1f else 0.38f),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(52.dp, 32.dp)
                .clip(Shapes.full)
                .background(track)
                .then(if (!checked) Modifier.border(2.dp, c.outline, Shapes.full) else Modifier)
        )
        Box(
            Modifier
                .padding(start = if (checked) offset - 2.dp else offset)
                .size(thumb)
                .clip(CircleShape)
                .background(if (checked) c.onPrimary else c.outline),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Sym("check", size = 16.dp, weight = 600, tint = c.primary)
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    sub: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: String? = null,
    enabled: Boolean = true,
    divider: Boolean = false,
) {
    val c = Sift.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (divider) Modifier.drawBehind { drawLine(c.outlineVariant, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Sym(icon, tint = c.onSurface)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            T(title, Sift.type.bodyL.copy(fontWeight = FontWeight.Medium), color = c.onSurface)
            if (!sub.isNullOrEmpty()) T(sub, Sift.type.bodyS, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        SiftToggle(checked, null, enabled)
    }
}

@Composable
fun RadioDot(selected: Boolean) {
    val c = Sift.colors
    Box(
        Modifier.size(20.dp).clip(CircleShape).border(2.dp, if (selected) c.primary else c.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary))
    }
}

@Composable
fun RadioRow(icon: String, title: String, sub: String, selected: Boolean, onClick: () -> Unit, highlight: Boolean = false) {
    val c = Sift.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Shapes.l)
            .background(if (highlight && selected) c.containerHigh else Color.Transparent)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym(icon, tint = c.onSurface)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            T(title, Sift.type.bodyL, color = c.onSurface)
            T(sub, Sift.type.data, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        RadioDot(selected)
    }
}

// ---------------------------------------------------------------------------------------------
// Ask bar · 56 dp · full
// ---------------------------------------------------------------------------------------------

@Composable
fun AskBarIdle(placeholder: String, showOrigin: Boolean, onClick: () -> Unit, onVoice: () -> Unit) {
    val c = Sift.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Shapes.full)
            .background(c.containerHigh)
            .clickable(onClickLabel = "Ask your library", onClick = onClick)
            .padding(start = 18.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("search", tint = c.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        T(placeholder, Sift.type.bodyL, Modifier.weight(1f), color = c.onSurfaceVariant, maxLines = 1)
        if (showOrigin) {
            Row(
                Modifier.heightIn(min = 28.dp).clip(Shapes.s).background(c.containerLow).padding(start = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sym("memory", size = 16.dp, weight = 400, tint = c.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                T("On device", Sift.type.labelM, color = c.onSurfaceVariant)
            }
        }
        IconBtn("mic", "Voice search", onVoice, tint = c.onSurface)
    }
}

// ---------------------------------------------------------------------------------------------
// Containers
// ---------------------------------------------------------------------------------------------

@Composable
fun SiftCard(
    modifier: Modifier = Modifier,
    color: Color = Sift.colors.containerLow,
    padding: PaddingValues = PaddingValues(16.dp),
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().clip(Shapes.card).background(color).padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    val c = Sift.colors
    Box(modifier.fillMaxWidth().height(1.dp).background(c.outlineVariant))
}

@Composable
fun TopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backIcon: String = "arrow_back",
    backLabel: String = "Back",
    titleStyle: TextStyle = Sift.type.titleL,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Sift.colors
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconBtn(backIcon, backLabel, onBack, tint = c.onSurface) else Spacer(Modifier.width(12.dp))
        if (title != null) {
            T(title, titleStyle, Modifier.weight(1f).padding(start = 4.dp).semantics { heading() }, color = c.onSurface, maxLines = 1)
        } else {
            Spacer(Modifier.weight(1f))
        }
        CompositionLocalProvider(LocalContentColor provides c.onSurface) { actions() }
    }
}

// ---------------------------------------------------------------------------------------------
// Storage meter · bring your own
// ---------------------------------------------------------------------------------------------

data class MeterSegment(val label: String, val detail: String, val fraction: Float, val color: Color, val alpha: Float = 1f)

@Composable
fun SegmentedBar(segments: List<MeterSegment>, height: Dp = 12.dp) {
    Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val total = segments.sumOf { it.fraction.toDouble() }.toFloat().coerceAtLeast(0.0001f)
        segments.forEachIndexed { i, s ->
            if (s.fraction <= 0f) return@forEachIndexed
            val shape = RoundedCornerShape(
                topStart = if (i == 0) 6.dp else 2.dp, bottomStart = if (i == 0) 6.dp else 2.dp,
                topEnd = if (i == segments.lastIndex) 6.dp else 2.dp, bottomEnd = if (i == segments.lastIndex) 6.dp else 2.dp,
            )
            Box(Modifier.weight(s.fraction / total).fillMaxHeight().alpha(s.alpha).clip(shape).background(s.color))
        }
    }
}

@Composable
fun StorageMeter(title: String, meta: String, segments: List<MeterSegment>, footnote: String?, onClick: (() -> Unit)? = null) {
    val c = Sift.colors
    SiftCard(
        Modifier.then(if (onClick != null) Modifier.clip(Shapes.card).clickable(onClick = onClick) else Modifier),
        spacing = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(title, Sift.type.titleM, Modifier.weight(1f), color = c.onSurface)
            T(meta, Sift.type.data, color = c.onSurfaceVariant)
        }
        SegmentedBar(segments)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).alpha(s.alpha).clip(RoundedCornerShape(3.dp)).background(s.color))
                    Spacer(Modifier.width(10.dp))
                    T(s.label, Sift.type.bodyM, Modifier.weight(1f), color = c.onSurface)
                    T(s.detail, Sift.type.data, color = c.onSurfaceVariant)
                }
            }
        }
        if (footnote != null) T(footnote, Sift.type.labelM.copy(fontWeight = FontWeight.Normal), color = c.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------------------------
// Toast · inverse surface · 4 s + Undo
// ---------------------------------------------------------------------------------------------

@Stable
class ToastState {
    data class Toast(val message: String, val icon: String?, val action: String?, val onAction: (() -> Unit)?, val id: Long = System.nanoTime())

    var current by mutableStateOf<Toast?>(null)
        private set

    fun show(message: String, icon: String? = "memory", action: String? = "Undo", onAction: (() -> Unit)? = null) {
        current = Toast(message, icon, if (onAction != null) action else null, onAction)
    }

    fun dismiss() { current = null }
}

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val c = Sift.colors
    val toast = state.current
    LaunchedEffect(toast?.id) {
        if (toast != null) {
            delay(4000)
            if (state.current?.id == toast.id) state.dismiss()
        }
    }
    val reduce = Sift.reduceMotion
    AnimatedVisibility(
        visible = toast != null,
        modifier = modifier,
        enter = fadeIn(Motion.effectsDefault(reduce)) + slideInVertically(Motion.effectsDefault(reduce)) { it / 3 },
        exit = fadeOut(Motion.effectsDefault(reduce)) + slideOutVertically(Motion.effectsDefault(reduce)) { it / 3 },
    ) {
        val t = toast ?: state.current ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(Shapes.m)
                .background(c.inverseSurface)
                .semantics(mergeDescendants = true) {}
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (t.icon != null) {
                Sym(t.icon, size = 20.dp, tint = c.inverseOnSurface)
                Spacer(Modifier.width(12.dp))
            }
            T(t.message, Sift.type.bodyM, Modifier.weight(1f).padding(vertical = 12.dp), color = c.inverseOnSurface)
            if (t.action != null && t.onAction != null) {
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(Shapes.full)
                        .clickable(role = Role.Button) { t.onAction?.invoke(); state.dismiss() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) { T(t.action, Sift.type.labelL.copy(fontWeight = FontWeight.SemiBold), color = c.inversePrimary) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Dialog · xl 28. The private choice is the primary action; cloud is never the filled button.
// ---------------------------------------------------------------------------------------------

@Composable
fun SiftDialog(
    icon: String?,
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String?,
    onDismiss: () -> Unit,
    dismissOutlined: Boolean = true,
    onSecondary: (() -> Unit)? = null,
) {
    val c = Sift.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(Shapes.xl)
                .background(c.modal)
                .then(if (c.hairline != null) Modifier.border(1.dp, c.hairline, Shapes.xl) else Modifier)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (icon != null) Sym(icon, Modifier.align(Alignment.CenterHorizontally), tint = c.onSurfaceVariant)
            T(title, Sift.type.headlineS, Modifier.fillMaxWidth(), color = c.onSurface, align = TextAlign.Center)
            T(body, Sift.type.bodyM, color = c.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Btn(confirm, onConfirm, kind = BtnKind.Text, color = c.primary)
                if (dismiss != null) {
                    Spacer(Modifier.width(8.dp))
                    Btn(dismiss, onSecondary ?: onDismiss, kind = if (dismissOutlined) BtnKind.Outlined else BtnKind.Text)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom sheet · top xl 28
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiftSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val c = Sift.colors
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = Shapes.sheet,
        containerColor = c.modal,
        contentColor = c.onSurface,
        scrimColor = c.scrim,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(Modifier.padding(top = 14.dp, bottom = 14.dp).size(32.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(c.outline))
        },
        modifier = if (c.hairline != null) Modifier.border(1.dp, c.hairline, Shapes.sheet) else Modifier,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content(close) }
    }
}

// ---------------------------------------------------------------------------------------------
// Bottom navigation · 80 dp
// ---------------------------------------------------------------------------------------------

enum class Tab(val icon: String, val label: String) {
    Library("photo_library", "Library"),
    Albums("photo_album", "Albums"),
    Actions("bolt", "Actions"),
    Storage("hard_drive", "Storage"),
}

@Composable
fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit, actionsDot: Boolean) {
    val c = Sift.colors
    val reduce = Sift.reduceMotion
    Column(Modifier.fillMaxWidth().background(c.containerLow)) {
        if (c.hairline != null) Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { tab ->
                val active = tab == selected
                val pill by animateDpAsState(if (active) 64.dp else 32.dp, Motion.chrome(reduce), label = "pill")
                val pillColor by animateColorAsState(if (active) c.primaryContainer else Color.Transparent, Motion.effectsFast(reduce), label = "pillColor")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(active, role = Role.Tab) { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(64.dp, 32.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(pill, 32.dp).clip(Shapes.full).background(pillColor))
                        Sym(tab.icon, fill = active, weight = if (active) 400 else 300, tint = if (active) c.onPrimaryContainer else c.onSurfaceVariant)
                        if (tab == Tab.Actions && actionsDot) {
                            Box(Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 2.dp).size(8.dp).clip(CircleShape).background(c.primary))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    T(
                        tab.label,
                        Sift.type.labelM.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (active) c.onSurface else c.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

// ---------------------------------------------------------------------------------------------
// Selection state toolbar
// ---------------------------------------------------------------------------------------------

@Composable
fun SelectionBar(count: Int, onClose: () -> Unit, onShare: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sift.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Shapes.l)
            .background(c.containerLow)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconBtn("close", "Clear selection", onClose, tint = c.onSurface)
        T("$count selected", Sift.type.titleL.copy(fontSize = Sift.type.titleL.fontSize * 0.82f), Modifier.weight(1f), color = c.onSurface)
        IconBtn("share", "Share", onShare, tint = c.onSurface)
        IconBtn("drive_file_move", "Move", onMove, tint = c.onSurface)
        IconBtn("delete", "Delete", onDelete, tint = c.onSurface)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, icon: String? = null, shape: Shape = Shapes.badge) {
    Row(
        modifier.heightIn(min = 24.dp).clip(shape).background(bg).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Sym(icon, size = 15.dp, weight = 400, tint = fg); Spacer(Modifier.width(4.dp)) }
        T(text, Sift.type.labelM, color = fg, maxLines = 1)
    }
}

@Composable
fun BoxScope.CenterMessage(icon: String, title: String, body: String, actions: @Composable ColumnScope.() -> Unit = {}) {
    val c = Sift.colors
    Column(
        Modifier.align(Alignment.Center).widthIn(max = 360.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(72.dp).clip(Shapes.card).background(c.containerHigh), contentAlignment = Alignment.Center) {
            Sym(icon, size = 32.dp, tint = c.onSurface)
        }
        T(title, Sift.type.headlineS, color = c.onSurface, align = TextAlign.Center)
        T(body, Sift.type.bodyM, color = c.onSurfaceVariant, align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        actions()
    }
}

/** Fades and rises 8 dp on enter — how AI results arrive, so lists never jump. */
@Composable
fun RiseIn(key: Any?, content: @Composable () -> Unit) {
    val reduce = Sift.reduceMotion
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, Motion.effectsDefault(reduce), label = "rise")
    Box(Modifier.graphicsLayer { alpha = a; translationY = if (reduce) 0f else 8.dp.toPx() * (1 - a) }) { content() }
}

@Composable
fun Modifier.scrollY(): Modifier = this.verticalScroll(rememberScrollState())
