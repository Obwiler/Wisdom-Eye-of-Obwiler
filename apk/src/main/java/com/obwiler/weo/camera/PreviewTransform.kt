package com.obwiler.weo.camera

import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log

/**
 * Compute the TextureView transform matrix for a Camera2 preview.
 *
 * The TextureView matrix maps view coordinates to texture coordinates.
 * For rotated sensors (90?/270?) we apply postRotate(orientation) to
 * upright the image, then center-crop via a uniform postScale so the
 * rotated texture fills the viewport.
 *
 * For 180? sensors a simple postRotate suffices; for 0? a center-fit
 * via [Matrix.setRectToRect] is used.
 */
object PreviewTransform {

    fun compute(
        viewW: Int,
        viewH: Int,
        bufW: Int,
        bufH: Int,
        orientation: Int,
    ): Matrix {
        val matrix = Matrix()
        if (viewW <= 0 || viewH <= 0 || bufW <= 0 || bufH <= 0) return matrix

        val cx = viewW / 2f
        val cy = viewH / 2f

        when (orientation) {
            90, 270 -> {
                // For the RG-glasses fixed landscape display, Android's
                // natural orientation is likely portrait, so the effective
                // display rotation in landscape is 90 deg.
                // Standard formula: (sensorOrientation + displayRotation*90) % 360
                //                 (270 + 90) % 360 = 0 deg -- no rotation needed.
                //
                // The buffer (bufW x bufH) is already landscape 4:3, matching
                // the display, so a simple center-crop postScale suffices.
                val scale = maxOf(
                    viewW.toFloat() / bufW.toFloat(),
                    viewH.toFloat() / bufH.toFloat()
                )
                matrix.postScale(scale, scale, cx, cy)

                android.util.Log.d("WEO/Preview", "Transform: view=${viewW}x${viewH} buf=${bufW}x${bufH} orient=$orientation scale=$scale rotation=0deg")
            }
            180 -> {
                matrix.postRotate(180f, cx, cy)
            }
            else -> {
                val src = RectF(0f, 0f, bufW.toFloat(), bufH.toFloat())
                val dst = RectF(0f, 0f, viewW.toFloat(), viewH.toFloat())
                matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER)
            }
        }

        return matrix
    }
}
