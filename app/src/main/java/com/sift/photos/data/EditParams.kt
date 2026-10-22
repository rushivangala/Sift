package com.sift.photos.data

import android.graphics.ColorMatrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** Crop aspect presets. [ratio] null keeps the photo's own aspect. */
enum class Aspect(val label: String, val ratio: Float?) {
    Free("Original", null), Square("1:1", 1f), Portrait("4:5", 0.8f), Classic("3:2", 1.5f), Wide("16:9", 16f / 9f),
}

/**
 * One set of editor adjustments. Sliders run −1…1 unless noted; rotation is a multiple of 90°.
 * The same math drives the GPU preview (color matrix + AGSL tone shader) and the CPU render on save.
 */
data class EditParams(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val rotation: Int = 0,
    val flip: Boolean = false,
    /** Fine rotation in degrees, −15…15. */
    val straighten: Float = 0f,
    val aspect: Aspect = Aspect.Free,
    /** Levels: input black and white points (0…1) and midtone gamma (0.3…3). */
    val black: Float = 0f,
    val white: Float = 1f,
    val gamma: Float = 1f,
    /** Tone curve through (0,0), (¼,[c1]), (½,[c2]), (¾,[c3]), (1,1). */
    val c1: Float = 0.25f,
    val c2: Float = 0.5f,
    val c3: Float = 0.75f,
) {
    val changed: Boolean get() = this != EditParams()
    val toneChanged: Boolean get() = black != 0f || white != 1f || gamma != 1f || c1 != 0.25f || c2 != 0.5f || c3 != 0.75f

    /** Saturation, then warmth, then contrast around mid-grey, then exposure in stops. */
    fun colorMatrix(): ColorMatrix {
        val m = ColorMatrix().apply { setSaturation(1f + saturation) }
        m.postConcat(ColorMatrix(floatArrayOf(
            1f + 0.12f * warmth, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - 0.12f * warmth, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        val k = 1f + contrast * 0.6f
        val off = 128f * (1f - k)
        m.postConcat(ColorMatrix(floatArrayOf(
            k, 0f, 0f, 0f, off,
            0f, k, 0f, 0f, off,
            0f, 0f, k, 0f, off,
            0f, 0f, 0f, 1f, 0f,
        )))
        val e = 2f.pow(exposure)
        m.postConcat(ColorMatrix().apply { setScale(e, e, e, 1f) })
        return m
    }

    /** Levels then curve, for one channel value 0…1. Mirrors [TONE_SHADER]. */
    fun tone(x: Float): Float {
        var v = ((x - black) / max(0.01f, white - black)).coerceIn(0f, 1f)
        v = v.pow(1f / gamma)
        return curve(v).coerceIn(0f, 1f)
    }

    private fun cr(t: Float, a: Float, b: Float, c: Float, d: Float) =
        0.5f * (2 * b + (-a + c) * t + (2 * a - 5 * b + 4 * c - d) * t * t + (-a + 3 * b - 3 * c + d) * t * t * t)

    fun curve(x: Float): Float {
        val y = floatArrayOf(-c1, 0f, c1, c2, c3, 1f, 2f - c3)
        val seg = (x * 4).toInt().coerceIn(0, 3)
        val t = x * 4 - seg
        return cr(t, y[seg], y[seg + 1], y[seg + 2], y[seg + 3])
    }

    fun lut(): IntArray = IntArray(256) { (tone(it / 255f) * 255f + 0.5f).toInt().coerceIn(0, 255) }

    /** Scale that keeps a straightened photo filling its crop with no empty corners. */
    fun fillScale(aspect: Float): Float {
        val a = Math.toRadians(straighten.toDouble())
        return (abs(cos(a)) + abs(sin(a)) * max(aspect, 1f / aspect)).toFloat()
    }

    companion object {
        val looks: List<Pair<String, EditParams>> = listOf(
            "Original" to EditParams(),
            "Clear" to EditParams(contrast = 0.25f, saturation = 0.2f),
            "Warm" to EditParams(warmth = 0.5f, saturation = 0.1f),
            "Fade" to EditParams(contrast = -0.25f, c1 = 0.33f, c3 = 0.72f),
            "Mono" to EditParams(saturation = -1f, contrast = 0.15f),
        )

        const val TONE_SHADER = """
            uniform shader content;
            uniform float black;
            uniform float white;
            uniform float gamma;
            uniform float c1;
            uniform float c2;
            uniform float c3;
            float cr(float t, float a, float b, float c, float d) {
                return 0.5 * (2.0 * b + (-a + c) * t + (2.0 * a - 5.0 * b + 4.0 * c - d) * t * t + (-a + 3.0 * b - 3.0 * c + d) * t * t * t);
            }
            float curve(float x) {
                float s = clamp(floor(x * 4.0), 0.0, 3.0);
                float t = x * 4.0 - s;
                if (s < 0.5) return cr(t, -c1, 0.0, c1, c2);
                if (s < 1.5) return cr(t, 0.0, c1, c2, c3);
                if (s < 2.5) return cr(t, c1, c2, c3, 1.0);
                return cr(t, c2, c3, 1.0, 2.0 - c3);
            }
            float tone(float x) {
                float v = clamp((x - black) / max(0.01, white - black), 0.0, 1.0);
                v = pow(v, 1.0 / gamma);
                return clamp(curve(v), 0.0, 1.0);
            }
            half4 main(float2 xy) {
                half4 c = content.eval(xy);
                float a = max(float(c.a), 0.0001);
                float3 rgb = float3(c.rgb) / a;
                return half4(half3(tone(rgb.r), tone(rgb.g), tone(rgb.b)) * c.a, c.a);
            }
        """
    }
}
