package com.obwiler.weo.camera

import android.graphics.SurfaceTexture
import android.util.Size
import kotlinx.coroutines.flow.StateFlow

interface CameraService {
    /** Observable ready state -- true when preview is live. */
    val ready: StateFlow<Boolean>

    /** Current preview resolution (native camera buffer size). */
    val previewSize: StateFlow<Size>

    /** Camera sensor orientation in degrees (0, 90, 180, 270). */
    val sensorOrientation: StateFlow<Int>

    /** Still-capture resolution (JPEG output size). */
    val captureSize: StateFlow<Size>

    /**
     * Start camera preview on the given surface.
     *
     * Safe to call multiple times ? each call closes any previous session
     * and opens a new one.  If CAMERA permission is not granted, this is
     * a no-op and [ready] stays false.
     */
    fun startPreview(texture: SurfaceTexture)

    /** Stop the current preview session without releasing the camera thread. */
    fun stopPreview()

    /**
     * Capture a JPEG still frame at [captureSize] resolution.
     * Must be called when [ready] is true.
     */
    suspend fun capture(): ByteArray

    /** Full teardown -- closes camera, surfaces, and ImageReader. */
    fun release()
}

