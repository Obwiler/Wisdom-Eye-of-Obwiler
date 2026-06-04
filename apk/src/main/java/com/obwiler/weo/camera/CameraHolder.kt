package com.obwiler.weo.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor

class CameraHolder(private val context: Context) : CameraService {

    companion object {
        private const val TAG = "WEO/Camera"
    }

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val cameraThread = HandlerThread("WEO-Camera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val cameraExecutor = Executor { command -> cameraHandler.post(command) }

    @Volatile private var cameraDevice: CameraDevice? = null
    @Volatile private var previewSession: CameraCaptureSession? = null
    @Volatile private var boundSurface: Surface? = null
    @Volatile private var cameraId: String = ""
    @Volatile private var closed = false

    @Volatile private var _previewSize = Size(640, 480)
    @Volatile private var _sensorOrientation = 90

    private var readyDeferred = CompletableDeferred<Boolean>()
    private var captureDeferred = CompletableDeferred<ByteArray>()

    @Volatile private var captureReader: ImageReader? = null

    override val previewSize: Size get() = _previewSize
    override val sensorOrientation: Int get() = _sensorOrientation

    override fun bindSurface(texture: SurfaceTexture) {
        cameraHandler.post {
            if (closed) return@post
            try {
                cameraId = resolveCameraId()
                readCharacteristics(cameraId)
                val swapped = _sensorOrientation == 90 || _sensorOrientation == 270
                if (swapped) {
                    texture.setDefaultBufferSize(_previewSize.height, _previewSize.width)
                } else {
                    texture.setDefaultBufferSize(_previewSize.width, _previewSize.height)
                }
                boundSurface = Surface(texture)

                val permitted = try {
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                } catch (_: Exception) { false }

                if (!permitted) {
                    Log.e(TAG, "CAMERA permission not granted")
                    readyDeferred.complete(false)
                    return@post
                }

                readyDeferred = CompletableDeferred()
                cameraManager.openCamera(cameraId, deviceCallback, cameraHandler)
            } catch (e: Exception) {
                Log.e(TAG, "bindSurface failed", e)
                readyDeferred.complete(false)
            }
        }
    }

    override suspend fun capture(): ByteArray {
        return suspendCancellableCoroutine { cont ->
            cameraHandler.post {
                val device = cameraDevice
                if (device == null) {
                    if (cont.isActive) {
                        cont.cancel(IllegalStateException("Camera not open"))
                    }
                    return@post
                }

                cont.invokeOnCancellation { cleanupCapture() }

                val reader = ImageReader.newInstance(
                    _previewSize.width, _previewSize.height, ImageFormat.JPEG, 1
                )
                captureReader = reader

                reader.setOnImageAvailableListener({ ir ->
                    val image = ir.acquireLatestImage()
                    if (image == null) {
                        cleanupCapture()
                        if (cont.isActive) {
                            cont.cancel(RuntimeException("acquireLatestImage null"))
                        }
                        return@setOnImageAvailableListener
                    }
                    val buf = image.planes[0].buffer
                    val bytes = ByteArray(buf.remaining())
                    buf.get(bytes)
                    image.close()
                    cleanupCapture()
                    if (cont.isActive) {
                        cont.resume(bytes, { cause -> })
                    }
                }, cameraHandler)

                val outputSurfaces = listOfNotNull(boundSurface, reader.surface)
                val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.JPEG_ORIENTATION, _sensorOrientation)
                }

                val outputs = outputSurfaces.map { OutputConfiguration(it) }
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    cameraExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            session.capture(req.build(), null, cameraHandler)
                        }
                        override fun onConfigureFailed(session: CameraCaptureSession) {
                            cleanupCapture()
                            if (cont.isActive) {
                                cont.cancel(RuntimeException("Capture config failed"))
                            }
                        }
                    }
                )
                device.createCaptureSession(sessionConfig)
            }
        }
    }

    override suspend fun awaitReady(): Boolean {
        return withTimeoutOrNull(5000) { readyDeferred.await() } ?: false
    }

    override fun release() {
        closed = true
        cleanupCapture()
        cameraHandler.post {
            try { previewSession?.close() } catch (_: Exception) {}
            previewSession = null
            try { cameraDevice?.close() } catch (_: Exception) {}
            cameraDevice = null
            try { boundSurface?.release() } catch (_: Exception) {}
            boundSurface = null
        }
        cameraThread.quitSafely()
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            val surface = boundSurface ?: run {
                readyDeferred.complete(false)
                return
            }
            try {
                val outputs = listOf(OutputConfiguration(surface))
                val sessionConfig = SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    outputs,
                    cameraExecutor,
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(session: CameraCaptureSession) {
                            previewSession = session
                            try {
                                val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                    addTarget(surface)
                                }
                                session.setRepeatingRequest(req.build(), null, cameraHandler)
                                Log.d(TAG, "Preview live: ${_previewSize} orient=$_sensorOrientation")
                                readyDeferred.complete(true)
                            } catch (e: Exception) {
                                readyDeferred.complete(false)
                            }
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) {
                            readyDeferred.complete(false)
                        }
                    }
                )
                device.createCaptureSession(sessionConfig)
            } catch (e: Exception) {
                readyDeferred.complete(false)
            }
        }
        override fun onDisconnected(device: CameraDevice) {
            device.close()
            cameraDevice = null
        }
        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            cameraDevice = null
            readyDeferred.complete(false)
        }
    }

    private fun resolveCameraId(): String {
        val ids = cameraManager.cameraIdList
        return ids.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull() ?: "0"
    }

    private fun readCharacteristics(cameraId: String) {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        _sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        val sizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return
        if (sizes.isEmpty()) return

        val rotated = _sensorOrientation == 90 || _sensorOrientation == 270
        val prefW = if (rotated) 480 else 640
        val prefH = if (rotated) 640 else 480

        _previewSize = sizes.minByOrNull { s ->
            kotlin.math.abs(s.width - prefW) + kotlin.math.abs(s.height - prefH)
        } ?: sizes[0]

        Log.d(TAG, "Camera $cameraId: preview=${_previewSize} orient=$_sensorOrientation")
    }

    private fun cleanupCapture() {
        captureReader?.close()
        captureReader = null
    }
}