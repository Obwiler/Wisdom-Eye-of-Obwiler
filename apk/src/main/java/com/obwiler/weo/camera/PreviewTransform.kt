package com.obwiler.weo.camera

import android.graphics.Matrix
import android.graphics.RectF

object PreviewTransform {

    fun compute(
        viewW: Int,
        viewH: Int,
        bufW: Int,
        bufH: Int,
        orientation: Int,
    ): Matrix {
        val matrix = Matrix()
        if (viewW <= 0 || viewH <= 0) return matrix

        val viewRect = RectF(0f, 0f, viewW.toFloat(), viewH.toFloat())
        val cx = viewRect.centerX()
        val cy = viewRect.centerY()

        when (orientation) {
            90 -> {
                val bufRect = RectF(0f, 0f, bufH.toFloat(), bufW.toFloat())
                matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.CENTER)
                matrix.postRotate(90f, cx, cy)
            }
            270 -> {
                val bufRect = RectF(0f, 0f, bufH.toFloat(), bufW.toFloat())
                matrix.setRectToRect(viewRect, bufRect, Matrix.ScaleToFit.CENTER)
                matrix.postRotate(270f, cx, cy)
            }
            180 -> {
                matrix.setRectToRect(
                    viewRect,
                    RectF(0f, 0f, bufW.toFloat(), bufH.toFloat()),
                    Matrix.ScaleToFit.CENTER,
                )
                matrix.postRotate(180f, cx, cy)
            }
            else -> {
                matrix.setRectToRect(
                    viewRect,
                    RectF(0f, 0f, bufW.toFloat(), bufH.toFloat()),
                    Matrix.ScaleToFit.CENTER,
                )
            }
        }
        return matrix
    }
}
