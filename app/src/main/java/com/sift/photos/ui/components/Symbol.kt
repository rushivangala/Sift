package com.sift.photos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sift.photos.R
import com.sift.photos.ui.theme.Sift

private val families = HashMap<Int, FontFamily>()

/** Material Symbols Rounded with live axes: wght 300 by default, FILL 0 → 1 on active, GRAD −25 on dark. */
@OptIn(ExperimentalTextApi::class)
private fun symbolsFamily(weight: Int, fill: Boolean, grad: Int, opsz: Int): FontFamily {
    val key = weight * 1_000_000 + (if (fill) 1 else 0) * 100_000 + (grad + 50) * 100 + opsz
    return families.getOrPut(key) {
        FontFamily(
            Font(
                R.font.material_symbols_rounded,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.Setting("FILL", if (fill) 1f else 0f),
                    FontVariation.Setting("GRAD", grad.toFloat()),
                    FontVariation.Setting("opsz", opsz.toFloat()),
                ),
            )
        )
    }
}

/**
 * Draws a Material Symbol by name. Decorative by default (hidden from TalkBack);
 * pass [label] when the icon carries meaning on its own.
 */
@Composable
fun Sym(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    weight: Int = 300,
    fill: Boolean = false,
    tint: Color = LocalContentColor.current,
    label: String? = null,
) {
    val glyph = SymbolCodepoints.map[name] ?: return
    val density = LocalDensity.current
    val opsz = size.value.toInt().coerceIn(20, 48)
    val grad = if (Sift.colors.isDark) -25 else 0
    val fontSize = with(density) { size.toSp() }
    val semantics = if (label != null) Modifier.clearAndSetSemantics { contentDescription = label }
    else Modifier.clearAndSetSemantics { }
    Box(modifier.size(size).then(semantics), contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            color = tint,
            textAlign = TextAlign.Center,
            softWrap = false,
            maxLines = 1,
            style = TextStyle(
                fontFamily = symbolsFamily(weight, fill, grad, opsz),
                fontSize = fontSize,
                lineHeight = fontSize,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
    }
}
