package com.sift.photos.ui.screens

import android.content.Intent
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import androidx.compose.foundation.background
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sift.photos.data.EditParams
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.ui.SiftViewModel
import com.sift.photos.ui.components.Btn
import com.sift.photos.ui.components.BtnKind
import com.sift.photos.ui.components.IconBtn
import com.sift.photos.ui.components.PhotoView
import com.sift.photos.ui.components.Sym
import com.sift.photos.ui.components.T
import com.sift.photos.ui.components.TopBar
import com.sift.photos.ui.theme.Shapes
import com.sift.photos.ui.theme.Sift
import kotlinx.coroutines.launch

/**
 * Non-destructive editor: Crop (rotate, flip), Light (exposure, contrast) and Color (saturation, warmth).
 * The preview is a GPU color filter; Save writes a new JPEG next to your photos and never touches the original.
 */
@Composable
fun EditorScreen(vm: SiftViewModel, item: PhotoItem) {
    val c = Sift.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(1) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var warmth by remember { mutableFloatStateOf(0f) }
    var rotation by remember { mutableIntStateOf(0) }
    var flip by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val params = EditParams(exposure, contrast, saturation, warmth, rotation, flip)
    val media = item.ref as? PhotoRef.Media
    val canSave = media != null && !media.isVideo && params.changed && !saving
    val maxDp = LocalConfiguration.current.let { maxOf(it.screenWidthDp, it.screenHeightDp) }.dp

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
                scope.launch {
                    vm.saveEdit(item, params)
                    saving = false
                }
            }, kind = BtnKind.Text, color = c.primary, enabled = canSave)
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            val ratio = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height else 4f / 3f
            val quarter = (rotation / 90) % 2 == 1
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio, matchHeightConstraintsFirst = ratio < 1f)
                    .graphicsLayer {
                        rotationZ = rotation.toFloat()
                        val fit = if (quarter) minOf(ratio, 1f / ratio) else 1f
                        scaleX = fit * (if (flip) -1f else 1f)
                        scaleY = fit
                        renderEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(params.colorMatrix()))
                            .asComposeRenderEffect()
                    }
                    .semantics { contentDescription = "Edit preview" },
            ) {
                PhotoView(item.ref, Modifier.fillMaxSize(), thumbSize = maxDp, full = true, contentScale = ContentScale.Fit)
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (tab) {
                0 -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("Rotate", { rotation = (rotation + 270) % 360 }, kind = BtnKind.Tonal, icon = "rotate_90_degrees_ccw")
                    Btn("Flip", { flip = !flip }, kind = BtnKind.Tonal, icon = "flip")
                }
                1 -> {
                    EditSlider("Exposure", "%+.1f EV".format(exposure), exposure) { exposure = it }
                    EditSlider("Contrast", "%+d".format((contrast * 100).toInt()), contrast) { contrast = it }
                }
                else -> {
                    EditSlider("Saturation", "%+d".format((saturation * 100).toInt()), saturation) { saturation = it }
                    EditSlider("Warmth", "%+d".format((warmth * 100).toInt()), warmth) { warmth = it }
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
                if (params.changed) Btn("Reset", {
                    exposure = 0f; contrast = 0f; saturation = 0f; warmth = 0f; rotation = 0; flip = false
                }, kind = BtnKind.Text)
            }
        }

        Row(Modifier.fillMaxWidth().background(c.containerLow).navigationBarsPadding().height(80.dp)) {
            listOf("crop" to "Crop", "brightness_6" to "Light", "palette" to "Color").forEachIndexed { i, (icon, label) ->
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

@Composable
private fun EditSlider(label: String, valueText: String, value: Float, onChange: (Float) -> Unit) {
    val c = Sift.colors
    Column(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(label, Sift.type.bodyM, Modifier.weight(1f), color = c.onSurface)
            T(valueText, Sift.type.dataMedium, color = c.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = -1f..1f,
            colors = SliderDefaults.colors(
                thumbColor = c.onSurface,
                activeTrackColor = c.primary,
                inactiveTrackColor = c.containerHigh,
            ),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}
