package com.sift.photos.ui.screens

import android.content.Intent
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sift.photos.data.Analysis
import com.sift.photos.data.Aspect
import com.sift.photos.data.EditParams
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.LocalMediaRepository
import com.sift.photos.ui.components.OriginBadge
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.SiftChip
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Non-destructive editor: Crop (aspect, straighten, rotate, flip), Light (with an explained Auto),
 * Color (looks), and Pro (tone curve and levels). Save writes a new JPEG; the original is never touched.
 */
@Composable
fun EditorScreen(vm: SiftViewModel, item: PhotoItem) {
    val c = Sift.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(1) }
    var p by remember { mutableStateOf(EditParams()) }
    var saving by remember { mutableStateOf(false) }
    var autoApplied by remember { mutableStateOf<EditParams?>(null) }
    var proLevels by remember { mutableStateOf(false) }
    val media = item.ref as? PhotoRef.Media
    val canSave = media != null && !media.isVideo && p.changed && !saving
    val maxDp = LocalConfiguration.current.let { maxOf(it.screenWidthDp, it.screenHeightDp) }.dp
    val repo = LocalMediaRepository.current
    // Auto exposure suggestion from the photo's mean brightness, measured on device.
    val luma by produceState<Double?>(null, item.key) {
        value = vm.entry(item)?.luma ?: (media?.let { repo?.samplePixels(it) }?.let { Analysis.meanLuma(it) })
    }
    val autoEv = luma?.let { (ln(118.0 / it.coerceAtLeast(8.0)) / ln(2.0)).toFloat().coerceIn(-1f, 1f) }

    Column(Modifier.fillMaxSize()) {
        TopBar(item.name, onBack = { vm.pop() }, backIcon = "close", backLabel = "Discard edits", titleStyle = Sift.type.titleM) {
            if (media != null) IconBtn("open_in_new", "Edit in another app", {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_EDIT).setDataAndType(media.uri, item.mime ?: "image/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
                            "Edit with",
                        )
                    )
                }.onFailure { vm.toast.show("No other photo editor is installed", icon = "info", onAction = null) }
            })
            Btn(if (saving) "Saving…" else "Save", {
                saving = true
                scope.launch { vm.saveEdit(item, p); saving = false }
            }, kind = BtnKind.Text, color = c.primary, enabled = canSave)
        }

        // Preview: crop frame at the chosen aspect; photo straightened, filled and color-graded on the GPU.
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val photoRatio = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else 4f / 3f
            val quarter = (p.rotation / 90) % 2 == 1
            val baseRatio = if (quarter) 1f / photoRatio else photoRatio
            val frame = p.aspect.ratio ?: baseRatio
            val effect = remember(p) {
                val color = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(p.colorMatrix()))
                // If the GPU can't compile the tone shader, preview color only; Save still applies the curve.
                val tone = if (!p.toneChanged) null else runCatching {
                    val shader = RuntimeShader(EditParams.TONE_SHADER).apply {
                        setFloatUniform("black", p.black); setFloatUniform("white", p.white); setFloatUniform("gamma", p.gamma)
                        setFloatUniform("c1", p.c1); setFloatUniform("c2", p.c2); setFloatUniform("c3", p.c3)
                    }
                    RenderEffect.createChainEffect(RenderEffect.createRuntimeShaderEffect(shader, "content"), color)
                }.getOrNull()
                (tone ?: color).asComposeRenderEffect()
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(frame, matchHeightConstraintsFirst = frame < 1f).clip(Shapes.xs)
                    .semantics { contentDescription = "Edit preview" },
                contentAlignment = Alignment.Center,
            ) {
                // Cover the frame with the photo's own aspect, then apply rotation, flip and straighten.
                val cover = if (baseRatio > frame) Modifier.fillMaxHeight().aspectRatio(baseRatio, matchHeightConstraintsFirst = true)
                else Modifier.fillMaxWidth().aspectRatio(baseRatio)
                Box(cover.graphicsLayer {
                    rotationZ = p.rotation.toFloat() + p.straighten
                    val fit = if (quarter) minOf(photoRatio, 1f / photoRatio) else 1f
                    val fill = p.fillScale(frame)
                    scaleX = fit * fill * (if (p.flip) -1f else 1f)
                    scaleY = fit * fill
                    renderEffect = effect
                }) {
                    val r = if (quarter) Modifier.fillMaxSize().aspectRatio(photoRatio) else Modifier.fillMaxSize()
                    PhotoView(item.ref, r, thumbSize = maxDp, full = true, contentScale = ContentScale.Crop)
                }
                if (tab == 0) Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    for (i in 1..2) {
                        drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), Offset(w * i / 3, 0f), Offset(w * i / 3, h), 1.dp.toPx())
                        drawLine(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f), Offset(0f, h * i / 3), Offset(w, h * i / 3), 1.dp.toPx())
                    }
                    drawRect(androidx.compose.ui.graphics.Color.White, style = Stroke(2.dp.toPx()))
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (tab) {
                0 -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Aspect.entries.forEach { a -> SiftChip(a.label, p.aspect == a, { p = p.copy(aspect = a) }) }
                    }
                    EditSlider("Straighten", "%+.1f°".format(p.straighten), p.straighten / 15f) { p = p.copy(straighten = it * 15f) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Rotate", { p = p.copy(rotation = (p.rotation + 270) % 360) }, kind = BtnKind.Tonal, icon = "rotate_90_degrees_ccw")
                        Btn("Flip", { p = p.copy(flip = !p.flip) }, kind = BtnKind.Tonal, icon = "flip")
                    }
                }
                1 -> {
                    if (autoEv != null && kotlin.math.abs(autoEv) >= 0.1f) {
                        Row(
                            Modifier.fillMaxWidth().clip(Shapes.l).background(c.containerLow).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                T(
                                    "Why: average brightness %d of 255; Auto sets exposure %+.1f EV.".format(luma!!.roundToInt(), autoEv),
                                    Sift.type.bodyS, color = c.onSurfaceVariant,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    T("Medium · 0.80", Sift.type.data, color = c.onSurface)
                                    Spacer(Modifier.size(8.dp))
                                    OriginBadge()
                                }
                            }
                            val on = autoApplied != null
                            Btn(if (on) "Undo Auto" else "Auto", {
                                if (on) { p = autoApplied!!; autoApplied = null } else { autoApplied = p; p = p.copy(exposure = autoEv) }
                            }, kind = if (on) BtnKind.Outlined else BtnKind.Tonal, icon = "auto_fix_high")
                        }
                    }
                    EditSlider("Exposure", "%+.1f EV".format(p.exposure), p.exposure) { p = p.copy(exposure = it) }
                    EditSlider("Contrast", "%+d".format((p.contrast * 100).toInt()), p.contrast) { p = p.copy(contrast = it) }
                }
                2 -> {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EditParams.looks.forEach { (name, look) ->
                            val selected = p.copy(aspect = Aspect.Free, rotation = 0, flip = false, straighten = 0f) == look
                            Column(
                                Modifier.selectable(selected, role = Role.RadioButton) {
                                    p = look.copy(aspect = p.aspect, rotation = p.rotation, flip = p.flip, straighten = p.straighten)
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val lookEffect = remember(look) { RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(look.colorMatrix())).asComposeRenderEffect() }
                                Box(
                                    Modifier.size(64.dp).clip(Shapes.m).then(if (selected) Modifier.border(3.dp, c.primary, Shapes.m) else Modifier)
                                        .graphicsLayer { renderEffect = lookEffect },
                                ) { PhotoView(item.ref, Modifier.fillMaxSize(), thumbSize = 64.dp) }
                                T(name, Sift.type.labelM, color = if (selected) c.onSurface else c.onSurfaceVariant)
                            }
                        }
                    }
                    EditSlider("Saturation", "%+d".format((p.saturation * 100).toInt()), p.saturation) { p = p.copy(saturation = it) }
                    EditSlider("Warmth", "%+d".format((p.warmth * 100).toInt()), p.warmth) { p = p.copy(warmth = it) }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SiftChip("Curves", !proLevels, { proLevels = false })
                        SiftChip("Levels", proLevels, { proLevels = true })
                    }
                    if (!proLevels) {
                        CurveEditor(p) { p = it }
                        Row {
                            T("Drag the three points. Input ¼, ½, ¾.", Sift.type.bodyS, Modifier.weight(1f), color = c.onSurfaceVariant)
                            Btn("Reset curve", { p = p.copy(c1 = 0.25f, c2 = 0.5f, c3 = 0.75f) }, kind = BtnKind.Text)
                        }
                    } else {
                        EditSlider("Black", "%d".format((p.black * 255).roundToInt()), p.black * 2 - 1) { p = p.copy(black = ((it + 1) / 2).coerceAtMost(p.white - 0.05f)) }
                        EditSlider("Gamma", "%.2f".format(p.gamma), ln(p.gamma) / ln(3f)) { p = p.copy(gamma = Math.pow(3.0, it.toDouble()).toFloat()) }
                        EditSlider("White", "%d".format((p.white * 255).roundToInt()), p.white * 2 - 1) { p = p.copy(white = ((it + 1) / 2).coerceAtLeast(p.black + 0.05f)) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(
                    when {
                        media == null -> "Demo photos can't be saved. Try this on one of your own photos."
                        media.isVideo -> "Video editing isn't available yet."
                        else -> "Saves a copy in Pictures/Sift. The original stays untouched."
                    },
                    Sift.type.bodyS, Modifier.weight(1f), color = c.onSurfaceVariant,
                )
                if (p.changed) Btn("Reset", { p = EditParams(); autoApplied = null }, kind = BtnKind.Text)
            }
        }

        Row(Modifier.fillMaxWidth().background(c.containerLow).navigationBarsPadding().height(80.dp)) {
            listOf("crop" to "Crop", "brightness_6" to "Light", "palette" to "Color", "show_chart" to "Pro").forEachIndexed { i, (icon, label) ->
                val active = tab == i
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(active, role = Role.Tab) { tab = i },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(64.dp, 32.dp).clip(Shapes.full).background(if (active) c.primaryContainer else c.containerLow), contentAlignment = Alignment.Center) {
                        Sym(icon, fill = active, weight = if (active) 400 else 300, tint = if (active) c.onPrimaryContainer else c.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    T(label, Sift.type.labelM.copy(fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium), color = if (active) c.onSurface else c.onSurfaceVariant)
                }
            }
        }
    }
}

/** Curve and levels graph: grid, identity line and a spline through three draggable points. */
@Composable
private fun CurveEditor(p: EditParams, onChange: (EditParams) -> Unit) {
    val c = Sift.colors
    var dragging by remember { mutableIntStateOf(-1) }
    Canvas(
        Modifier.fillMaxWidth().height(150.dp).clip(Shapes.m).background(c.containerLow)
            .semantics { contentDescription = "Tone curve. Shadows %.2f, midtones %.2f, highlights %.2f".format(p.c1, p.c2, p.c3) }
            .pointerInput(p.c1, p.c2, p.c3) {
                detectDragGestures(
                    onDragStart = { o ->
                        val xs = listOf(0.25f, 0.5f, 0.75f)
                        dragging = xs.indices.minBy { kotlin.math.abs(xs[it] * size.width - o.x) }
                    },
                    onDragEnd = { dragging = -1 },
                ) { change, _ ->
                    val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                    onChange(when (dragging) { 0 -> p.copy(c1 = v); 1 -> p.copy(c2 = v); else -> p.copy(c3 = v) })
                }
            },
    ) {
        val w = size.width; val h = size.height
        for (i in 1..3) {
            drawLine(c.outlineVariant, Offset(w * i / 4, 0f), Offset(w * i / 4, h), 1.dp.toPx())
            drawLine(c.outlineVariant, Offset(0f, h * i / 4), Offset(w, h * i / 4), 1.dp.toPx())
        }
        drawLine(c.outline, Offset(0f, h), Offset(w, 0f), 1.dp.toPx())
        val path = Path()
        for (i in 0..64) {
            val x = i / 64f
            val y = p.curve(x).coerceIn(0f, 1f)
            if (i == 0) path.moveTo(0f, h - y * h) else path.lineTo(x * w, h - y * h)
        }
        drawPath(path, c.primary, style = Stroke(2.5f.dp.toPx()))
        listOf(0.25f to p.c1, 0.5f to p.c2, 0.75f to p.c3).forEach { (x, y) ->
            drawCircle(c.surface, 9.dp.toPx(), Offset(x * w, h - y * h))
            drawCircle(c.primary, 6.dp.toPx(), Offset(x * w, h - y * h))
        }
    }
}

@Composable
private fun EditSlider(label: String, valueText: String, value: Float, onChange: (Float) -> Unit) {
    val c = Sift.colors
    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, Sift.type.bodyM, Modifier.weight(1f), color = c.onSurface)
            T(valueText, Sift.type.dataMedium, color = c.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(-1f, 1f),
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(thumbColor = c.onSurface, activeTrackColor = c.primary, inactiveTrackColor = c.containerHigh),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
