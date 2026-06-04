package com.obwiler.weo.camera

import android.graphics.Matrix
import android.graphics.RectF

/**
 * Compute the TextureView transform matrix for a Camera2 preview.
 *
 * For rotated sensors (90?/270?), the matrix maps the buffer so that
 * the post-rotation image fills the viewport with center-crop ? exactly
 * what every native camera app does.  The logic follows Android's own
 * Camera2Basic sample, adapted for sensor orientation (glasses have a
 * fixed display, so we use sensor orientation instead of display rotation).
 *
 * For non-rotated sensors, a simple center-fit via [Matrix.setRectToRect]
 * is used.
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
                // After sensor rotation the effective image is bufH x bufW.
                val rotW = bufH.toFloat()
                val rotH = bufW.toFloat()

                val viewRect = RectF(0f, 0f, viewW.toFloat(), viewH.toFloat())
                val bufRect = RectF(0f, 0f, rotW, rotH)

                // 1. Center the (rotated) buffer rect in the viewport.
                bufRect.offset(
                    cx - bufRect.centerX(),
                    cy - bufRect.centerY()
                )

                // 2. Base scale+translate: map viewRect to fill bufRect.
                matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.FILL)

                // 3. Compensate for pre-rotation buffer aspect ratio.
                val arScale = maxOf(
                    viewH / bufH.toFloat(),
                    viewW / bufW.toFloat()
                )
                matrix.postScale(arScale, arScale, cx, cy)

                // 4. Correct sensor rotation: invert via 360-orientation for fixed display.
                matrix.postRotate((360 - orientation).toFloat(), cx, cy)
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
