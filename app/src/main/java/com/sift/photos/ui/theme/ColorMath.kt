package com.sift.photos.ui.theme

import kotlin.math.cbrt
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure color math for the Sift accent pipeline. Mirrors the reference implementation in the
 * Stage 1 design file: seeds are clamped to HCT-style tones (L*), chroma is capped, and every
 * role pair is verified against WCAG 2.2 AA before it reaches the UI.
 *
 * Colors are plain 0xRRGGBB ints so this file has no Android dependencies and is unit-testable.
 */
object ColorMath {

    fun parse(hex: String): Int = hex.removePrefix("#").toLong(16).toInt() and 0xFFFFFF

    fun hex(c: Int): String = "#" + (c and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

    private fun channels(c: Int) = intArrayOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    private fun linearize(v: Int): Double {
        val x = v / 255.0
        return if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Int): Double {
        val (r, g, b) = channels(c).map(::linearize)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** CIE L* (0–100), the "tone" axis of HCT. */
    fun lstar(c: Int): Double {
        val y = luminance(c)
        return if (y > 0.008856) 116 * cbrt(y) - 16 else 903.3 * y
    }

    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05) / (min(x, y) + 0.05)
    }

    /** Returns [h (0–1), s (0–1), l (0–1)]. */
    fun toHsl(c: Int): DoubleArray {
        val (r, g, b) = channels(c).map { it / 255.0 }
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        var h = 0.0
        var s = 0.0
        val l = (mx + mn) / 2
        if (mx != mn) {
            val d = mx - mn
            s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
            h = when (mx) {
                r -> (g - b) / d + (if (g < b) 6 else 0)
                g -> (b - r) / d + 2
                else -> (r - g) / d + 4
            }
            h /= 6
        }
        return doubleArrayOf(h, s, l)
    }

    fun fromHsl(h: Double, s: Double, l: Double): Int {
        fun f(n: Int): Int {
            val k = (n + h * 12) % 12
            val a = s * min(l, 1 - l)
            val v = l - a * max(-1.0, min(min(k - 3, 9 - k), 1.0))
            return floor(255 * v + 0.5).toInt().coerceIn(0, 255)
        }
        return (f(0) shl 16) or (f(8) shl 8) or f(4)
    }

    /** Same hue as [seed], saturation capped at [cap], lightness solved so L* ≈ [t]. */
    fun tone(seed: Int, t: Double, cap: Double): Int {
        val hsl = toHsl(seed)
        val s = min(hsl[1], cap)
        var lo = 0.0
        var hi = 1.0
        repeat(32) {
            val m = (lo + hi) / 2
            if (lstar(fromHsl(hsl[0], s, m)) < t) lo = m else hi = m
        }
        return fromHsl(hsl[0], s, (lo + hi) / 2)
    }

    /** Linear blend of two colors; [t] = 0 gives [a]. */
    fun mix(a: Int, b: Int, t: Double): Int {
        val ca = channels(a)
        val cb = channels(b)
        val o = IntArray(3) { floor(ca[it] + (cb[it] - ca[it]) * t + 0.5).toInt().coerceIn(0, 255) }
        return (o[0] shl 16) or (o[1] shl 8) or o[2]
    }
}

/** Accent roles derived from one seed for one theme. */
data class AccentRoles(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val inversePrimary: Int,
    /** Selection check badge: primary, nudged until it clears 3:1 on primaryContainer. */
    val selectionBadge: Int,
    /** How many 3-unit tone steps primary needed to clear AA. Logged in Settings → Appearance. */
    val steps: Int,
)

object AccentPipeline {
    const val AA_TEXT = 4.5
    const val AA_UI = 3.0
    private const val PRIMARY_CAP = 0.62

    /**
     * Stage 1 role recipe with the Stage 5 audit fixes applied:
     *  - primary must clear 4.5:1 on every surface it can sit on, not only the base surface;
     *  - inversePrimary (toast Undo) is stepped against the inverse surface;
     *  - the selection badge is nudged to clear 3:1 against primaryContainer.
     */
    fun roles(seed: Int, neutrals: Neutrals): AccentRoles {
        val light = !neutrals.isDark
        var t = if (light) 40.0 else 80.0
        var p = ColorMath.tone(seed, t, PRIMARY_CAP)
        var steps = 0
        while (neutrals.primarySurfaces.minOf { ColorMath.contrast(p, it) } < AA_TEXT && steps < 10) {
            t += if (light) -3 else 3
            p = ColorMath.tone(seed, t, PRIMARY_CAP)
            steps++
        }
        var onP = if (light) 0xFFFFFF else ColorMath.tone(seed, 20.0, 0.6)
        if (!light) {
            var ot = 20.0
            while (ColorMath.contrast(onP, p) < AA_TEXT && ot > 0) {
                ot -= 3; onP = ColorMath.tone(seed, ot, 0.6)
            }
        }
        val pc = if (light) ColorMath.tone(seed, 90.0, 0.5) else ColorMath.tone(seed, 30.0, 0.42)
        var onPc = if (light) ColorMath.tone(seed, 10.0, 0.6) else ColorMath.tone(seed, 90.0, 0.5)
        var onPcT = if (light) 10.0 else 90.0
        while (ColorMath.contrast(onPc, pc) < AA_TEXT && onPcT in 1.0..99.0) {
            onPcT += if (light) -3 else 3
            onPc = ColorMath.tone(seed, onPcT, if (light) 0.6 else 0.5)
        }
        var ipT = if (light) 80.0 else 40.0
        var invP = ColorMath.tone(seed, ipT, 0.6)
        while (ColorMath.contrast(invP, neutrals.inverseSurface) < AA_TEXT && ipT in 1.0..99.0) {
            ipT += if (light) 3 else -3
            invP = ColorMath.tone(seed, ipT, 0.6)
        }
        var badge = p
        var bt = t
        while (ColorMath.contrast(badge, pc) < AA_UI && bt in 1.0..99.0) {
            bt += if (light) -3 else 3
            badge = ColorMath.tone(seed, bt, PRIMARY_CAP)
        }
        return AccentRoles(p, onP, pc, onPc, invP, badge, steps)
    }
}

/** Warm, near-achromatic neutrals. They carry 95% of the UI. */
data class Neutrals(
    val key: String,
    val isDark: Boolean,
    val isBlack: Boolean,
    val surface: Int,
    val containerLow: Int,
    val containerHigh: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val modal: Int,
    val error: Int,
) {
    val primarySurfaces: List<Int> get() = listOf(surface, containerLow, containerHigh, modal)

    companion object {
        val Light = Neutrals(
            key = "light", isDark = false, isBlack = false,
            surface = 0xF7F6F3, containerLow = 0xF0EEEA, containerHigh = 0xE9E7E2,
            onSurface = 0x1B1B1A, onSurfaceVariant = 0x5E5D59,
            outline = 0x85837E, outlineVariant = 0xD3D0CA,
            inverseSurface = 0x2F2F2D, inverseOnSurface = 0xF2F0EB,
            modal = 0xFFFFFF, error = 0xB3261E,
        )
        val Dark = Neutrals(
            key = "dark", isDark = true, isBlack = false,
            surface = 0x151515, containerLow = 0x1E1E1D, containerHigh = 0x292927,
            onSurface = 0xE6E4DF, onSurfaceVariant = 0xA3A19B,
            outline = 0x7D7B75, outlineVariant = 0x3A3936,
            inverseSurface = 0xE6E4DF, inverseOnSurface = 0x1B1B1A,
            modal = 0x252524, error = 0xF2B8B5,
        )
        val Black = Neutrals(
            key = "black", isDark = true, isBlack = true,
            surface = 0x000000, containerLow = 0x0F0F0F, containerHigh = 0x1C1C1B,
            onSurface = 0xE6E4DF, onSurfaceVariant = 0xA3A19B,
            outline = 0x75736E, outlineVariant = 0x2E2D2B,
            inverseSurface = 0xE6E4DF, inverseOnSurface = 0x1B1B1A,
            modal = 0x0F0F0F, error = 0xF2B8B5,
        )
        val All = listOf(Light, Dark, Black)
    }
}

/** The six curated accents. Seeds are inputs only and never ship as text or icon colors. */
enum class CuratedAccent(val label: String, val seed: Int) {
    Slate("Slate", 0x4A6CB8),
    Moss("Moss", 0x4F8A5B),
    Clay("Clay", 0xC0623F),
    Plum("Plum", 0x8E5A9C),
    Ochre("Ochre", 0xC99A2E),
    Tide("Tide", 0x2A8C92),
}
