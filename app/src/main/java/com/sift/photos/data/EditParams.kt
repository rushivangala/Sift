package com.sift.photos.data

import android.graphics.ColorMatrix
import kotlin.math.pow

/** One set of editor adjustments. Each slider runs −1…1; rotation is a multiple of 90°. */
data class EditParams(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val rotation: Int = 0,
    val flip: Boolean = false,
) {
    val changed: Boolean
        get() = exposure != 0f || contrast != 0f || saturation != 0f || warmth != 0f || rotation != 0 || flip

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
}
