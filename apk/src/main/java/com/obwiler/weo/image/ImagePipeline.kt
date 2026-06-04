package com.obwiler.weo.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

object ImagePipeline {

    private const val TAG = "WEO/Image"
    private const val JPEG_QUALITY = 92
    private const val MAX_SIZE_KB = 800
    private const val MIN_QUALITY = 75

    fun process(
        original: Bitmap,
        pitchDeg: Float = 0f,
        rollDeg: Float = 0f,
        enableCorrection: Boolean = false,
    ): ByteArray {
        val t0 = System.currentTimeMillis()
        var current = original
        Log.d(TAG, "process: input=${current.width}x${current.height} pitch=$pitchDeg roll=$rollDeg")

        // Downscale to 1280px max to keep memory low on the 1.8GB device.
        // Use inSampleSize on decode is better but would require changing
        // the call site; scaling here prevents OOM on repeated captures.
        val maxDim = maxOf(current.width, current.height)
        val targetMax = 1280
        if (maxDim > targetMax) {
            val scale = targetMax.toFloat() / maxDim
            val newW = (current.width * scale).toInt()
            val newH = (current.height * scale).toInt()
            current = Bitmap.createScaledBitmap(current, newW, newH, true)
            Log.d(TAG, "process: scaled ${newW}x${newH} in ${System.currentTimeMillis() - t0}ms")
        }

        // Light tilt correction: clamp roll to +/-5 deg so we only fix
        // minor head tilt. Large pitch (glasses pointing down) is normal
        // and should NOT be corrected -- it distorts the image for AI.
        if (enableCorrection && (abs(pitchDeg) > 2f || abs(rollDeg) > 2f)) {
            val t1 = System.currentTimeMillis()
            current = tiltCorrectionLight(current, pitchDeg, rollDeg)
            Log.d(TAG, "process: tiltCorrection in ${System.currentTimeMillis() - t1}ms")
        }

        // histogramStretch removed: it took 2.8s and allocated 11MB on
        // a 1920x1440 image, causing OOM/LMK kill on second capture.
        // AI vision models handle lighting variance naturally.

        val t3 = System.currentTimeMillis()
        val jpeg = compressToJpeg(current)
        Log.d(TAG, "process: compressToJpeg in ${System.currentTimeMillis() - t3}ms")

        Log.d(TAG, "process: total=${System.currentTimeMillis() - t0}ms size=${jpeg.size}B")
        return jpeg
    }

    /**
     * Light tilt correction: only fixes roll up to +/-5 deg.
     * Does NOT apply pitch perspective correction ? glasses naturally
     * point downward (pitch=-78 deg) and correcting that distorts the
     * image for AI analysis.
     */
    private fun tiltCorrectionLight(src: Bitmap, pitchDeg: Float, rollDeg: Float): Bitmap {
        val cr = rollDeg.coerceIn(-5f, 5f)
        if (cr == 0f) return src
        val matrix = Matrix().apply {
            postRotate(-cr, src.width / 2f, src.height / 2f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    fun correctDocument(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val sampleScale = (400f / w).coerceIn(0.15f, 1f)
        val sw = (w * sampleScale).toInt().coerceAtLeast(40)
        val sh = (h * sampleScale).toInt().coerceAtLeast(40)
        val sampled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val pixels = IntArray(sw * sh)
        sampled.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        val gray = IntArray(sw * sh) { i ->
            val p = pixels[i]
            (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)).toInt()
        }

        val grad = FloatArray(sw * sh)
        var maxGrad = 0f
        for (y in 1 until sh - 1) {
            for (x in 1 until sw - 1) {
                val idx = y * sw + x
                val gx = -gray[idx - sw - 1] - 2 * gray[idx - 1] - gray[idx + sw - 1] +
                    gray[idx - sw + 1] + 2 * gray[idx + 1] + gray[idx + sw + 1]
                val gy = -gray[idx - sw - 1] - 2 * gray[idx - sw] - gray[idx - sw + 1] +
                    gray[idx + sw - 1] + 2 * gray[idx + sw] + gray[idx + sw + 1]
                val mag = sqrt(gx.toFloat() * gx + gy.toFloat() * gy)
                grad[idx] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        if (maxGrad < 1f) {
            return Bitmap.createScaledBitmap(src, sw, sh, true)
        }

        val threshold = maxGrad * 0.12f
        val edgeMap = BooleanArray(sw * sh) { grad[it] > threshold }
        val cx = sw / 2f
        val cy = sh / 2f

        data class CP(val x: Int, val y: Int, val d: Float)

        fun findCorner(xStart: Int, xEnd: Int, yStart: Int, yEnd: Int, dx: Int, dy: Int): CP {
            var best = CP(xStart, yStart, 0f)
            var y = yStart
            val stepY = if (yStart < yEnd) 1 else -1
            while (y != yEnd) {
                var x = xStart
                val stepX = if (xStart < xEnd) 1 else -1
                while (x != xEnd) {
                    if (edgeMap[y * sw + x]) {
                        val d = ((x - cx) * dx + (y - cy) * dy).toFloat() /
                            sqrt((dx * dx + dy * dy).toFloat())
                        if (d > best.d) best = CP(x, y, d)
                    }
                    x += stepX
                }
                y += stepY
            }
            return best
        }

        val tl = findCorner(0, sw / 2, 0, sh / 2, -1, -1)
        val tr = findCorner(sw / 2, sw, 0, sh / 2, 1, -1)
        val br = findCorner(sw / 2, sw, sh / 2, sh, 1, 1)
        val bl = findCorner(0, sw / 2, sh / 2, sh, -1, 1)

        val invScale = 1f / sampleScale
        val srcX = floatArrayOf(tl.x * invScale, tr.x * invScale, br.x * invScale, bl.x * invScale)
        val srcY = floatArrayOf(tl.y * invScale, tr.y * invScale, br.y * invScale, bl.y * invScale)

        return perspectiveWarp(src, srcX, srcY, sw, sh)
    }

    private fun perspectiveWarp(
        src: Bitmap, srcX: FloatArray, srcY: FloatArray, outW: Int, outH: Int,
    ): Bitmap {
        val dstX = floatArrayOf(0f, outW.toFloat(), outW.toFloat(), 0f)
        val dstY = floatArrayOf(0f, 0f, outH.toFloat(), outH.toFloat())

        val A = FloatArray(64)
        val B = FloatArray(8)
        for (i in 0..3) {
            val sx = srcX[i]; val sy = srcY[i]
            val dx = dstX[i]; val dy = dstY[i]
            A[i * 8 + 0] = sx; A[i * 8 + 1] = sy; A[i * 8 + 2] = 1f
            A[i * 8 + 3] = 0f; A[i * 8 + 4] = 0f; A[i * 8 + 5] = 0f
            A[i * 8 + 6] = -dx * sx; A[i * 8 + 7] = -dx * sy
            B[i] = dx
            A[(i + 4) * 8 + 0] = 0f; A[(i + 4) * 8 + 1] = 0f; A[(i + 4) * 8 + 2] = 0f
            A[(i + 4) * 8 + 3] = sx; A[(i + 4) * 8 + 4] = sy; A[(i + 4) * 8 + 5] = 1f
            A[(i + 4) * 8 + 6] = -dy * sx; A[(i + 4) * 8 + 7] = -dy * sy
            B[i + 4] = dy
        }

        val H = gaussianElimination(A, B)
        val h00 = H[0]; val h01 = H[1]; val h02 = H[2]
        val h10 = H[3]; val h11 = H[4]; val h12 = H[5]
        val h20 = H[6]; val h21 = H[7]; val h22 = 1f

        val srcPixels = IntArray(src.width * src.height)
        src.getPixels(srcPixels, 0, src.width, 0, 0, src.width, src.height)
        val outPixels = IntArray(outW * outH)

        for (oy in 0 until outH) {
            for (ox in 0 until outW) {
                val denom = h20 * ox + h21 * oy + h22
                val sx = ((h00 * ox + h01 * oy + h02) / denom)
                val sy = ((h10 * ox + h11 * oy + h12) / denom)
                outPixels[oy * outW + ox] = bilinearSample(srcPixels, src.width, src.height, sx, sy)
            }
        }

        return Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, outW, 0, 0, outW, outH)
        }
    }

    private fun bilinearSample(pixels: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, w - 2)
        val y0 = y.toInt().coerceIn(0, h - 2)
        val fx = x - x0; val fy = y - y0
        val fx1 = 1f - fx; val fy1 = 1f - fy
        val p00 = pixels[y0 * w + x0]; val p10 = pixels[y0 * w + x0 + 1]
        val p01 = pixels[(y0 + 1) * w + x0]; val p11 = pixels[(y0 + 1) * w + x0 + 1]
        val a = (Color.alpha(p00) * fx1 * fy1 + Color.alpha(p10) * fx * fy1 +
            Color.alpha(p01) * fx1 * fy + Color.alpha(p11) * fx * fy).toInt().coerceIn(0, 255)
        val r = (Color.red(p00) * fx1 * fy1 + Color.red(p10) * fx * fy1 +
            Color.red(p01) * fx1 * fy + Color.red(p11) * fx * fy).toInt().coerceIn(0, 255)
        val g = (Color.green(p00) * fx1 * fy1 + Color.green(p10) * fx * fy1 +
            Color.green(p01) * fx1 * fy + Color.green(p11) * fx * fy).toInt().coerceIn(0, 255)
        val b = (Color.blue(p00) * fx1 * fy1 + Color.blue(p10) * fx * fy1 +
            Color.blue(p01) * fx1 * fy + Color.blue(p11) * fx * fy).toInt().coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun gaussianElimination(A: FloatArray, B: FloatArray): FloatArray {
        val n = 8
        val M = FloatArray(n * (n + 1))
        for (i in 0 until n) {
            for (j in 0 until n) M[i * (n + 1) + j] = A[i * n + j]
            M[i * (n + 1) + n] = B[i]
        }
        for (col in 0 until n) {
            var maxRow = col
            var maxVal = abs(M[col * (n + 1) + col])
            for (row in col + 1 until n) {
                val v = abs(M[row * (n + 1) + col])
                if (v > maxVal) { maxVal = v; maxRow = row }
            }
            if (maxRow != col) {
                for (j in 0..n) {
                    val tmp = M[col * (n + 1) + j]
                    M[col * (n + 1) + j] = M[maxRow * (n + 1) + j]
                    M[maxRow * (n + 1) + j] = tmp
                }
            }
            val pivot = M[col * (n + 1) + col]
            if (abs(pivot) < 1e-9f) continue
            for (row in 0 until n) {
                if (row == col) continue
                val factor = M[row * (n + 1) + col] / pivot
                for (j in col..n) M[row * (n + 1) + j] -= factor * M[col * (n + 1) + j]
            }
        }
        val X = FloatArray(n)
        for (i in 0 until n) {
            val piv = M[i * (n + 1) + i]
            X[i] = if (abs(piv) > 1e-9f) M[i * (n + 1) + n] / piv else 0f
        }
        return X
    }

    private fun tiltCorrection(src: Bitmap, pitchDeg: Float, rollDeg: Float): Bitmap {
        if (pitchDeg == 0f && rollDeg == 0f) return src
        val cp = pitchDeg.coerceIn(-30f, 30f)
        val cr = rollDeg.coerceIn(-30f, 30f)
        val matrix = Matrix().apply {
            postRotate(-cr, src.width / 2f, src.height / 2f)
            val scaleX = kotlin.math.cos(Math.toRadians(cp.toDouble())).toFloat()
            postScale(scaleX, 1f, src.width / 2f, src.height / 2f)
        }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun histogramStretch(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        if (w * h <= 0) return src
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        for (p in pixels) hist[rgbToLuminance(p)]++
        val cdf = IntArray(256)
        cdf[0] = hist[0]
        for (i in 1..255) cdf[i] = cdf[i - 1] + hist[i]
        val total = cdf[255]
        if (total == 0) return src
        val p5Thresh = (total * 0.05f).toInt()
        val p95Thresh = (total * 0.95f).toInt()
        var p5 = 0; var p95 = 255
        for (i in 0..255) if (cdf[i] <= p5Thresh) p5 = i
        for (i in 255 downTo 0) if (cdf[i] >= p95Thresh) p95 = i
        val range = (p95 - p5).coerceAtLeast(1)
        val newPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val asr = ((Color.red(pixel) - p5).toFloat() / range * 255f).toInt().coerceIn(0, 255)
            val asg = ((Color.green(pixel) - p5).toFloat() / range * 255f).toInt().coerceIn(0, 255)
            val asb = ((Color.blue(pixel) - p5).toFloat() / range * 255f).toInt().coerceIn(0, 255)
            newPixels[i] = Color.argb(Color.alpha(pixel), asr, asg, asb)
        }
        val result = Bitmap.createBitmap(w, h, src.config)
        result.setPixels(newPixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun rgbToLuminance(pixel: Int): Int {
        return (0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel))
            .toInt().coerceIn(0, 255)
    }

    private fun compressToJpeg(src: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        src.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        val output = stream.toByteArray()
        stream.close()
        if (output.size <= MAX_SIZE_KB * 1024) {
            Log.d(TAG, "JPEG: quality=$JPEG_QUALITY, size=${output.size}B")
            return output
        }
        val stream2 = ByteArrayOutputStream()
        src.compress(Bitmap.CompressFormat.JPEG, MIN_QUALITY, stream2)
        val output2 = stream2.toByteArray()
        stream2.close()
        Log.d(TAG, "JPEG: retry quality=$MIN_QUALITY, size=${output2.size}B (was ${output.size}B)")
        return output2
    }
}