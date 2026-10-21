package com.sift.photos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sift.photos.R

@OptIn(ExperimentalTextApi::class)
private fun hanken(weight: Int) = Font(
    R.font.hanken_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Hanken Grotesk, variable 400–700. Bundled so it renders the same offline. */
val Hanken = FontFamily(hanken(400), hanken(500), hanken(600), hanken(700))

/** IBM Plex Mono: only for facts the system reports (confidence, sizes, EXIF, dates in reasons). */
val PlexMono = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)

private val Base = TextStyle(
    fontFamily = Hanken,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = Base.copy(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

class SiftTypography {
    val displayL = style(57, 64, 500, -0.01)
    val headlineL = style(32, 40, 500)
    val headlineS = style(24, 32, 500)
    val titleL = style(22, 28, 600)
    val titleM = style(16, 24, 600)
    /** Audit open item: 15/20 and 13/18 were in use everywhere, so they are now roles. */
    val titleS = style(15, 20, 600)
    val bodyL = style(16, 24, 400)
    val bodyM = style(14, 20, 400)
    val bodyS = style(13, 18, 400)
    val labelL = style(14, 20, 500)
    val labelM = style(12, 16, 500, 0.01)
    val data = Base.copy(fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val dataMedium = data.copy(fontWeight = FontWeight.Medium)
    val overline = data.copy(fontSize = 11.sp, letterSpacing = 0.06.em)

    val material = Typography(
        displayLarge = displayL,
        headlineLarge = headlineL,
        headlineSmall = headlineS,
        titleLarge = titleL,
        titleMedium = titleM,
        titleSmall = titleS,
        bodyLarge = bodyL,
        bodyMedium = bodyM,
        bodySmall = bodyS,
        labelLarge = labelL,
        labelMedium = labelM,
        labelSmall = labelM,
    )
}

val LocalSiftType = staticCompositionLocalOf { SiftTypography() }
