package com.obwiler.weo.camera

import android.graphics.SurfaceTexture
import android.util.Size

interface CameraService {
    val previewSize: Size
    val sensorOrientation: Int
    fun bindSurface(surface: SurfaceTexture)
    suspend fun capture(): ByteArray
    suspend fun awaitReady(): Boolean
    fun release()
}
