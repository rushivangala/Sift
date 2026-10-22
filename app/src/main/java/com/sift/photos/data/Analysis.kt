package com.sift.photos.data

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Pixel-level analysis that runs on device. Inputs are ARGB int arrays, so it is unit-testable. */
object Analysis {

    private fun gray(px: Int): Int = (((px shr 16) and 0xFF) * 299 + ((px shr 8) and 0xFF) * 587 + (px and 0xFF) * 114) / 1000

    /** Difference hash from a 9 × 8 grayscale sample: 64 bits that stay stable across re-encodes and resizes. */
    fun dHash(pixels: IntArray, w: Int, h: Int): Long {
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) for (x in 0 until 8) {
            val a = sampleGray(pixels, w, h, x, y, 9, 8)
            val b = sampleGray(pixels, w, h, x + 1, y, 9, 8)
            if (a > b) hash = hash or (1L shl bit)
            bit++
        }
        return hash
    }

    /** Box-averaged gray value of cell (cx, cy) in a cols × rows grid over the image. */
    private fun sampleGray(p: IntArray, w: Int, h: Int, cx: Int, cy: Int, cols: Int, rows: Int): Int {
        val x0 = cx * w / cols
        val x1 = maxOf(x0 + 1, (cx + 1) * w / cols)
        val y0 = cy * h / rows
        val y1 = maxOf(y0 + 1, (cy + 1) * h / rows)
        var sum = 0L
        var n = 0
        for (y in y0 until minOf(y1, h)) for (x in x0 until minOf(x1, w)) { sum += gray(p[y * w + x]); n++ }
        return if (n == 0) 0 else (sum / n).toInt()
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Variance of the Laplacian: high for crisp edges, low for motion or focus blur. */
    fun sharpness(pixels: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        val g = IntArray(w * h) { gray(pixels[it]) }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = (4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]).toDouble()
            sum += lap; sumSq += lap * lap; n++
        }
        val mean = sum / n
        return sumSq / n - mean * mean
    }

    /** Maps sharpness to "how sure is this blurry": 0 when crisp, approaching 1 when very soft. */
    fun blurConfidence(sharp: Double): Double = when {
        sharp >= 120 -> 0.0
        sharp <= 15 -> 0.95
        else -> (0.95 - (sharp - 15) / 105 * 0.95).coerceIn(0.0, 0.95)
    }

    /** Mean luminance 0–255, used to suggest exposure. */
    fun meanLuma(pixels: IntArray): Double = pixels.sumOf { gray(it).toLong() }.toDouble() / pixels.size.coerceAtLeast(1)

    // ---- Grouping ------------------------------------------------------------------------

    data class Shot(val id: Long, val time: Long, val hash: Long, val sharp: Double, val size: Long)

    /** Shots within [windowMs] of each other whose hashes differ by at most [maxDistance] bits. */
    fun nearDuplicateSets(shots: List<Shot>, windowMs: Long = 10_000, maxDistance: Int = 10): List<List<Shot>> {
        val sorted = shots.sortedBy { it.time }
        val sets = mutableListOf<MutableList<Shot>>()
        var current = mutableListOf<Shot>()
        for (s in sorted) {
            val prev = current.lastOrNull()
            if (prev != null && s.time - prev.time <= windowMs && current.any { hamming(it.hash, s.hash) <= maxDistance }) {
                current += s
            } else {
                if (current.size > 1) sets += current
                current = mutableListOf(s)
            }
        }
        if (current.size > 1) sets += current
        return sets
    }

    // ---- Places --------------------------------------------------------------------------

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    data class Located(val id: Long, val time: Long, val lat: Double, val lon: Double)

    /** "Home" is the ~1 km cell where most photos were taken. */
    fun home(points: List<Located>): Pair<Double, Double>? =
        points.groupBy { (it.lat * 100).roundToInt() to (it.lon * 100).roundToInt() }
            .maxByOrNull { it.value.size }?.value?.let { cell -> cell.map { it.lat }.average() to cell.map { it.lon }.average() }

    data class Trip(val start: Long, val end: Long, val ids: List<Long>, val km: Int, val lat: Double, val lon: Double)

    /** Runs of photos taken more than [minKm] from home, split when there is a gap over [gapDays] days. */
    fun trips(points: List<Located>, minKm: Double = 50.0, gapDays: Int = 2, minPhotos: Int = 10): List<Trip> {
        val h = home(points) ?: return emptyList()
        val away = points.filter { distanceKm(h.first, h.second, it.lat, it.lon) > minKm }.sortedBy { it.time }
        val runs = mutableListOf<MutableList<Located>>()
        for (p in away) {
            val last = runs.lastOrNull()?.lastOrNull()
            if (last != null && p.time - last.time <= gapDays * 86_400_000L) runs.last() += p else runs += mutableListOf(p)
        }
        return runs.filter { it.size >= minPhotos }.map { run ->
            val lat = run.map { it.lat }.average()
            val lon = run.map { it.lon }.average()
            Trip(run.first().time, run.last().time, run.map { it.id }, distanceKm(h.first, h.second, lat, lon).roundToInt(), lat, lon)
        }.sortedByDescending { it.start }
    }

    /** Greedy clustering in screen space, for map pins: points closer than [radius] merge. */
    fun <T> cluster(points: List<T>, radius: Float, pos: (T) -> Pair<Float, Float>): List<List<T>> {
        val out = mutableListOf<MutableList<T>>()
        val centers = mutableListOf<Pair<Float, Float>>()
        for (p in points) {
            val (x, y) = pos(p)
            val i = centers.indexOfFirst { (cx, cy) -> abs(cx - x) < radius && abs(cy - y) < radius }
            if (i >= 0) out[i] += p else { out += mutableListOf(p); centers += x to y }
        }
        return out
    }
}
