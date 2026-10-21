package com.sift.photos.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun Int.toColor(): Color = Color(0xFF000000.toInt() or this)

@Immutable
data class SiftColors(
    val isDark: Boolean,
    val isBlack: Boolean,
    val surface: Color,
    val containerLow: Color,
    val containerHigh: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val modal: Color,
    val scrim: Color,
    val error: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val inversePrimary: Color,
    val selectionBadge: Color,
    /** True black swaps shadows for a 1 dp outlineVariant hairline. */
    val hairline: Color?,
    val accentSteps: Int,
) {
    companion object {
        fun build(seed: Int, n: Neutrals): SiftColors {
            val r = AccentPipeline.roles(seed, n)
            return SiftColors(
                isDark = n.isDark,
                isBlack = n.isBlack,
                surface = n.surface.toColor(),
                containerLow = n.containerLow.toColor(),
                containerHigh = n.containerHigh.toColor(),
                onSurface = n.onSurface.toColor(),
                onSurfaceVariant = n.onSurfaceVariant.toColor(),
                outline = n.outline.toColor(),
                outlineVariant = n.outlineVariant.toColor(),
                inverseSurface = n.inverseSurface.toColor(),
                inverseOnSurface = n.inverseOnSurface.toColor(),
                modal = n.modal.toColor(),
                scrim = when (n.key) {
                    "light" -> Color(0x521B1B1A)
                    "dark" -> Color(0x80000000)
                    else -> Color(0x99000000)
                },
                error = n.error.toColor(),
                primary = r.primary.toColor(),
                onPrimary = r.onPrimary.toColor(),
                primaryContainer = r.primaryContainer.toColor(),
                onPrimaryContainer = r.onPrimaryContainer.toColor(),
                inversePrimary = r.inversePrimary.toColor(),
                selectionBadge = r.selectionBadge.toColor(),
                hairline = if (n.isBlack) n.outlineVariant.toColor() else null,
                accentSteps = r.steps,
            )
        }
    }
}

/** Scrim behind icons and text on photos: 56% black holds 4.5:1 on the lightest photos. */
val PhotoScrim = Color(0x8F000000)

object Spacing {
    val gutter = 2.dp
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val touch = 48.dp
}

object Shapes {
    val xs = RoundedCornerShape(4.dp)
    val badge = RoundedCornerShape(6.dp)
    val s = RoundedCornerShape(8.dp)
    val m = RoundedCornerShape(12.dp)
    val l = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(20.dp)
    val xl = RoundedCornerShape(28.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val askSheet = RoundedCornerShape(32.dp)
    val full = RoundedCornerShape(percent = 50)
}

/** Springs, not durations. Reduce motion swaps springs for 150 ms fades. */
object Motion {
    fun <T> spatialFast(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) tween(150) else spring(dampingRatio = 0.9f, stiffness = 1400f)
    fun <T> spatialDefault(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) tween(150) else spring(dampingRatio = 0.9f, stiffness = 700f)
    fun <T> spatialSlow(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) tween(150) else spring(dampingRatio = 0.9f, stiffness = 300f)
    fun <T> effectsFast(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) tween(150) else spring(dampingRatio = 1f, stiffness = 3800f)
    fun <T> effectsDefault(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) tween(150) else spring(dampingRatio = 1f, stiffness = 1600f)
    /** Overshoot is allowed on chrome only (nav pill, toggle thumb). */
    fun <T> chrome(reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) tween(150) else spring(dampingRatio = 0.6f, stiffness = 1400f)
}

val LocalSiftColors = staticCompositionLocalOf { SiftColors.build(CuratedAccent.Slate.seed, Neutrals.Light) }
val LocalReduceMotion = staticCompositionLocalOf { false }

object Sift {
    val colors: SiftColors
        @Composable get() = LocalSiftColors.current
    val type: SiftTypography
        @Composable get() = LocalSiftType.current
    val reduceMotion: Boolean
        @Composable get() = LocalReduceMotion.current
}

@Composable
fun SiftTheme(seed: Int, neutrals: Neutrals, content: @Composable () -> Unit) {
    val colors = remember(seed, neutrals) { SiftColors.build(seed, neutrals) }
    val context = LocalContext.current
    val reduce = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val type = remember { SiftTypography() }
    // Material components that are used directly (text fields, ripples) read the same roles.
    val scheme = if (neutrals.isDark) {
        darkColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer, onPrimaryContainer = colors.onPrimaryContainer,
            background = colors.surface, onBackground = colors.onSurface,
            surface = colors.surface, onSurface = colors.onSurface,
            surfaceVariant = colors.containerHigh, onSurfaceVariant = colors.onSurfaceVariant,
            surfaceContainerLow = colors.containerLow, surfaceContainerHigh = colors.containerHigh, surfaceContainer = colors.modal,
            outline = colors.outline, outlineVariant = colors.outlineVariant,
            inverseSurface = colors.inverseSurface, inverseOnSurface = colors.inverseOnSurface,
            inversePrimary = colors.inversePrimary, error = colors.error, scrim = colors.scrim,
        )
    } else {
        lightColorScheme(
            primary = colors.primary, onPrimary = colors.onPrimary,
            primaryContainer = colors.primaryContainer, onPrimaryContainer = colors.onPrimaryContainer,
            background = colors.surface, onBackground = colors.onSurface,
            surface = colors.surface, onSurface = colors.onSurface,
            surfaceVariant = colors.containerHigh, onSurfaceVariant = colors.onSurfaceVariant,
            surfaceContainerLow = colors.containerLow, surfaceContainerHigh = colors.containerHigh, surfaceContainer = colors.modal,
            outline = colors.outline, outlineVariant = colors.outlineVariant,
            inverseSurface = colors.inverseSurface, inverseOnSurface = colors.inverseOnSurface,
            inversePrimary = colors.inversePrimary, error = colors.error, scrim = colors.scrim,
        )
    }
    CompositionLocalProvider(
        LocalSiftColors provides colors,
        LocalSiftType provides type,
        LocalReduceMotion provides reduce,
    ) {
        MaterialTheme(colorScheme = scheme, typography = type.material, content = content)
    }
}
