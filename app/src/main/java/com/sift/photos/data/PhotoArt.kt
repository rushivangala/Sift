package com.sift.photos.data

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * Color stand-ins for photos in the sample library, taken one-to-one from the design files'
 * CSS gradients. Angles follow CSS: 180° runs top to bottom.
 */
enum class PhotoArt(private val angle: Float, private vararg val stops: Pair<Float, Long>, private val radial: Boolean = false) {
    Sand(160f, 0f to 0xFFD8C7AD, 1f to 0xFF8F7A5E),
    Sky(170f, 0f to 0xFFA9C4D8, 1f to 0xFF3F6A8A),
    Paper(150f, 0f to 0xFFEBE5D8, 1f to 0xFFBFB39B),
    Moss(165f, 0f to 0xFF9FB391, 1f to 0xFF4B6340),
    Rose(160f, 0f to 0xFFE3B9A8, 1f to 0xFF9A5E4E),
    Night(170f, 0f to 0xFF4A4F6B, 1f to 0xFF1E2134),
    Fog(160f, 0f to 0xFFC7CCC9, 1f to 0xFF7F8A86),
    Gold(150f, 0f to 0xFFE9CF8F, 1f to 0xFFB0823A),
    Wine(165f, 0f to 0xFFD6A7AE, 1f to 0xFF7D4A58),
    Sunset(180f, 0f to 0xFFF3C58F, 0.48f to 0xFFE07B54, 1f to 0xFF5B3B4A),
    Sea(180f, 0f to 0xFFBCD9E8, 0.58f to 0xFF4F86A8, 0.59f to 0xFFD9C9A6, 1f to 0xFFB9A37C),
    Tram(160f, 0f to 0xFFF2D36B, 0.55f to 0xFFD19A2A, 1f to 0xFF4A3B2A),
    Tile(135f, 0f to 0xFF2F5F9E, 0.5f to 0xFF9CC0E6, 1f to 0xFF2F5F9E),
    Forest(175f, 0f to 0xFF7F9E7A, 1f to 0xFF2D4A33),
    Shot(180f, 0f to 0xFFFFFFFF, 0.14f to 0xFFFFFFFF, 0.14f to 0xFFE3E8EF, 1f to 0xFFE3E8EF),
    Cake(0f, 0f to 0xFFF4E7CF, 0.28f to 0xFFF4E7CF, 0.29f to 0xFFC98B4E, 0.40f to 0xFFC98B4E, 0.41f to 0xFF5A3A26, 1f to 0xFF5A3A26, radial = true),
    ReceiptDark(160f, 0f to 0xFF8A6F55, 1f to 0xFF5A4636),
    ReceiptGrey(160f, 0f to 0xFF6D6F73, 1f to 0xFF3B3D40),
    ReceiptBrown(160f, 0f to 0xFFA1876B, 1f to 0xFF5E4A39),
    ReceiptLight(160f, 0f to 0xFFC3C6C4, 1f to 0xFF868B88),
    Burst1(180f, 0f to 0xFFECC596, 0.40f to 0xFFDF9068, 0.62f to 0xFFA36658, 0.63f to 0xFFC39C76, 1f to 0xFF75563F),
    Burst2(180f, 0f to 0xFFEEC796, 0.42f to 0xFFE1926A, 0.62f to 0xFFA5685A, 0.63f to 0xFFC49D78, 1f to 0xFF77583F),
    Burst3(182f, 0f to 0xFFF0C99A, 0.40f to 0xFFE3956A, 0.60f to 0xFFA86A5C, 0.61f to 0xFFC8A07A, 1f to 0xFF7A5A44),
    Burst4(178f, 0f to 0xFFE9C292, 0.40f to 0xFFDC8D66, 0.62f to 0xFFA06356, 0.63f to 0xFFBF9873, 1f to 0xFF725440),
    WallSage(170f, 0f to 0xFFC9D6C4, 0.55f to 0xFF6E8F7A, 1f to 0xFF33473B),
    WallLilac(170f, 0f to 0xFFE3D9EF, 0.55f to 0xFF8A7BB0, 1f to 0xFF3E3557),
    WallAmber(170f, 0f to 0xFFF1DCC3, 0.55f to 0xFFC08A5A, 1f to 0xFF5B3D25),
    SampleBay(180f, 0f to 0xFF9CC7E6, 0.55f to 0xFF2F7FB5, 0.56f to 0xFFE8DCC4, 1f to 0xFFCDBB98),
    SampleSunset(150f, 0f to 0xFFE9D2C4, 0.5f to 0xFFC2453A, 1f to 0xFF5D2420),
    SampleField(160f, 0f to 0xFFFBEFC4, 0.5f to 0xFFE2B93B, 1f to 0xFF6B5A2A),
    SampleForest(170f, 0f to 0xFFC5DCB9, 0.6f to 0xFF3C7D4E, 1f to 0xFF1F3324);

    /** Approximate dominant color, used where a single tone is needed (map pins, avatars). */
    val mid: Color get() = Color(stops[stops.size / 2].second)

    fun brush(size: Size): Brush {
        val colorStops = stops.map { it.first to Color(it.second) }.toTypedArray()
        if (radial) {
            val c = Offset(size.width * 0.5f, size.height * 0.58f)
            val r = max(
                max(hypot(c.x, c.y), hypot(size.width - c.x, c.y)),
                max(hypot(c.x, size.height - c.y), hypot(size.width - c.x, size.height - c.y)),
            )
            return Brush.radialGradient(*colorStops, center = c, radius = r)
        }
        val a = Math.toRadians(angle.toDouble())
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        val len = abs(size.width * dx) + abs(size.height * dy)
        val c = Offset(size.width / 2, size.height / 2)
        val half = Offset(dx * len / 2, dy * len / 2)
        return Brush.linearGradient(*colorStops, start = c - half, end = c + half)
    }

    companion object {
        /** Deterministic cycle used by the design files: GK[(i*5+o) % n]. */
        private val cycle = listOf(Sand, Sky, Paper, Moss, Rose, Night, Fog, Gold, Wine, Sunset, Sea, Tram, Tile, Forest, Shot, Cake)
        fun cycle(i: Int, offset: Int = 0): PhotoArt = cycle[(i * 5 + offset) % cycle.size]
    }
}
