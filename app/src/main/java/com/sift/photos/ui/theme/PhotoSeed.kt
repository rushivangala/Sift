package com.sift.photos.ui.theme

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Match my photo": samples the most prominent non-skin, non-neutral color cluster from a photo.
 * Runs on device over a downscaled pixel array (ARGB ints). Pure Kotlin for unit testing.
 */
object PhotoSeed {
    private const val HUE_BUCKETS = 36

    fun extract(pixels: IntArray): Int? {
        val weight = DoubleArray(HUE_BUCKETS)
        val sumR = DoubleArray(HUE_BUCKETS)
        val sumG = DoubleArray(HUE_BUCKETS)
        val sumB = DoubleArray(HUE_BUCKETS)
        for (px in pixels) {
            if ((px ushr 24) < 0x80 && (px ushr 24) != 0) continue
            val rgb = px and 0xFFFFFF
            val (h, s, l) = ColorMath.toHsl(rgb).let { Triple(it[0], it[1], it[2]) }
            if (s < 0.22 || l < 0.12 || l > 0.9) continue // neutral, too dark or blown out
            if (isSkin(h * 360, s, l)) continue
            val b = ((h * HUE_BUCKETS).toInt()).coerceIn(0, HUE_BUCKETS - 1)
            // Favor saturated mid-tones: they read as "the color of the photo".
            val w = s * (1 - abs(l - 0.5) * 1.4)
            weight[b] += w
            sumR[b] += ((rgb shr 16) and 0xFF) * w
            sumG[b] += ((rgb shr 8) and 0xFF) * w
            sumB[b] += (rgb and 0xFF) * w
        }
        // Smooth across neighbouring hue buckets so gradients count as one cluster.
        var best = -1
        var bestW = 0.0
        for (i in 0 until HUE_BUCKETS) {
            val w = weight[i] + 0.5 * (weight[(i + 1) % HUE_BUCKETS] + weight[(i + HUE_BUCKETS - 1) % HUE_BUCKETS])
            if (w > bestW) { bestW = w; best = i }
        }
        if (best < 0 || weight[best] <= 0.0) return null
        val w = weight[best]
        val r = (sumR[best] / w).roundToInt().coerceIn(0, 255)
        val g = (sumG[best] / w).roundToInt().coerceIn(0, 255)
        val b = (sumB[best] / w).roundToInt().coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    /** Rough skin-tone gate in HSL: warm hues with moderate saturation. */
    internal fun isSkin(hueDeg: Double, s: Double, l: Double): Boolean =
        hueDeg in 5.0..42.0 && s in 0.22..0.62 && l in 0.3..0.82
}
