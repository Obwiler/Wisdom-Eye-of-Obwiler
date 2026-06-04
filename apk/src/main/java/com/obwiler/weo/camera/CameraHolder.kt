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
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Camera2 implementation for WEO on Rokid RG-glasses.
 *
 * Lifecycle follows the Camera2Basic sample pattern:
 *   1. startPreview(surface)  -- openCamera ? configureSession ? repeating request ? ready=true
 *   2. capture()             -- brief still-capture request while preview runs
 *   3. stopPreview()          -- close session + device, ready=false
 *   4. release()              -- full teardown (thread stays alive)
 *
 * All camera operations run on a dedicated background thread so the UI
 * thread is never blocked.
 */
class CameraHolder(private val context: Context) : CameraService {

    companion object {
        private const val TAG = "WEO/Camera"
    }

    // ---- Dedicated background thread (lives for app lifetime) ----
    private val cameraThread = HandlerThread("WEO-Camera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    // ---- Mutable state (all read/write on cameraHandler thread) ----
    @Volatile private var cameraDevice: CameraDevice? = null
    @Volatile private var captureSession: CameraCaptureSession? = null
    @Volatile private var previewSurface: Surface? = null
    @Volatile private var imageReader: ImageReader? = null
    @Volatile private var cameraId: String = ""

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _previewSize = MutableStateFlow(Size(640, 480))
    override val previewSize: StateFlow<Size> = _previewSize.asStateFlow()

    private val _sensorOrientation = MutableStateFlow(90)
    override val sensorOrientation: StateFlow<Int> = _sensorOrientation.asStateFlow()

    private val _captureSize = MutableStateFlow(Size(1280, 960))
    override val captureSize: StateFlow<Size> = _captureSize.asStateFlow()

    // ---- Still-capture plumbing ----
    @Volatile private var captureDeferred: CompletableDeferred<ByteArray>? = null

    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    // ========================================================================
    //  Public API
    // ========================================================================

    override fun startPreview(texture: SurfaceTexture) {
        if (!hasCameraPermission()) {
            Log.w(TAG, "startPreview: CAMERA permission not granted -- ignoring")
            _ready.value = false
            return
        }
        cameraHandler.post {
            openCamera(texture)
        }
    }

    override fun stopPreview() {
        cameraHandler.post {
            closeSession()
        }
    }

    override suspend fun capture(): ByteArray {
        return suspendCancellableCoroutine { cont ->
            cameraHandler.post {
                val session = captureSession
                val device = cameraDevice
                val reader = imageReader
                val surface = previewSurface

                if (session == null || device == null || reader == null || surface == null) {
                    if (cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("Camera not ready -- wait for ready=true")
                        )
                    }
                    return@post
                }

                cont.invokeOnCancellation {
                    captureDeferred?.completeExceptionally(
                        RuntimeException("Capture cancelled")
                    )
                }

                val deferred = CompletableDeferred<ByteArray>()
                Log.d(TAG, "capture: orient=${_sensorOrientation.value} size=${_captureSize.value}")
                captureDeferred = deferred

                val req = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(reader.surface)
                    addTarget(surface)
                    set(CaptureRequest.JPEG_ORIENTATION, _sensorOrientation.value)
                }

                try {
                    Log.d(TAG, "capture: submitting to HAL...")
                session.capture(req.build(), captureCallback, cameraHandler)
                } catch (e: Exception) {
                    captureDeferred = null
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                    return@post
                }

                deferred.invokeOnCompletion { cause ->
                    if (cont.isActive) {
                        if (cause == null) {
                            cont.resume(deferred.getCompleted())
                        } else {
                            cont.resumeWithException(cause)
                        }
                    }
                }
            }
        }
    }

    override fun release() {
        cameraHandler.post {
            closeSession()
        }
        // Thread stays alive for potential re-binding (e.g. user re-enters CameraScreen)
    }

    // ========================================================================
    //  Camera lifecycle (runs on cameraHandler thread)
    // ========================================================================

    private fun openCamera(texture: SurfaceTexture) {
        // Tear down any previous session before opening a new one.
        closeSession()

        try {
            cameraId = resolveCameraId()
            readCharacteristics(cameraId)

            texture.setDefaultBufferSize(
                _previewSize.value.width,
                _previewSize.value.height
            )
            previewSurface = Surface(texture)

            val capW = _captureSize.value.width
            val capH = _captureSize.value.height
            imageReader = ImageReader.newInstance(capW, capH, ImageFormat.JPEG, 2)
            imageReader!!.setOnImageAvailableListener(imageListener, cameraHandler)

            cameraManager.openCamera(cameraId, deviceStateCallback, cameraHandler)
        } catch (e: SecurityException) {
            Log.e(TAG, "openCamera: permission denied", e)
            _ready.value = false
        } catch (e: Exception) {
            Log.e(TAG, "openCamera failed", e)
            _ready.value = false
        }
    }

    // ---- Device state callback ----
    private val deviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            configureSession(device)
        }

        override fun onDisconnected(device: CameraDevice) {
            Log.w(TAG, "Camera disconnected")
            device.close()
            cameraDevice = null
            captureSession = null
            _ready.value = false
        }

        override fun onError(device: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            device.close()
            cameraDevice = null
            captureSession = null
            _ready.value = false
        }
    }

    // ---- Session configuration ----
    private fun configureSession(device: CameraDevice) {
        val surface = previewSurface
        val reader = imageReader

        if (surface == null || reader == null) {
            Log.e(TAG, "configureSession: surface or reader is null")
            _ready.value = false
            return
        }

        try {
            device.createCaptureSession(
                listOf(surface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            val req = device.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW
                            ).apply {
                                addTarget(surface)
                            }
                            session.setRepeatingRequest(req.build(), null, cameraHandler)
                            _ready.value = true
                            Log.d(TAG, "Preview live: " +
                                "preview=${_previewSize.value} " +
                                "capture=${_captureSize.value} " +
                                "orient=${_sensorOrientation.value}")
                        } catch (e: Exception) {
                            Log.e(TAG, "setRepeatingRequest failed", e)
                            _ready.value = false
                        }
                    }

                    override fun onConfigureFailed(s: CameraCaptureSession) {
                        Log.e(TAG, "Session configure failed")
                        _ready.value = false
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "createCaptureSession failed", e)
            _ready.value = false
        }
    }

    // ---- Teardown (runs on cameraHandler thread) ----
    private fun closeSession() {
        // Snapshot pending capture so we don't preemptively kill it
        // while the HAL callbacks still have a chance to fire.
        val pendingCapture = captureDeferred
        captureDeferred = null

        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null

        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null

        try { previewSurface?.release() } catch (_: Exception) {}
        previewSurface = null

        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null

        _ready.value = false

        // If the HAL didn't complete the capture through the normal callback
        // path (session already torn down), fail it now -- safely, in case
        // a racing callback already completed it.
        if (pendingCapture != null && !pendingCapture.isCompleted) {
            try {
                pendingCapture.completeExceptionally(
                    RuntimeException("Camera session closed")
                )
            } catch (_: Exception) {
                // Already completed by a racing callback -- fine.
            }
        }
    }

    // ========================================================================
    //  Still-capture callbacks
    // ========================================================================

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: android.hardware.camera2.CaptureFailure
        ) {
            Log.e(TAG, "Capture failed: reason=${failure.reason}")
            captureDeferred?.completeExceptionally(
                RuntimeException("Capture failed: ${failure.reason}")
            )
            captureDeferred = null
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: android.hardware.camera2.TotalCaptureResult
        ) {
            // JPEG frame arrives via ImageReader.OnImageAvailableListener
        }
    }

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = reader.acquireLatestImage()
        if (image == null) {
            captureDeferred?.completeExceptionally(
                RuntimeException("acquireLatestImage returned null")
            )
            captureDeferred = null
            return@OnImageAvailableListener
        }
        try {
            val buf = image.planes[0].buffer
            val bytes = ByteArray(buf.remaining())
            buf.get(bytes)
            captureDeferred?.complete(bytes)
        } catch (e: Exception) {
            captureDeferred?.completeExceptionally(e)
        } finally {
            image.close()
            captureDeferred = null
        }
    }

    // ========================================================================
    //  Helpers
    // ========================================================================

    private fun hasCameraPermission(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveCameraId(): String {
        val ids = cameraManager.cameraIdList
        return ids.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull() ?: "0"
    }

    private fun readCharacteristics(cameraId: String) {
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        _sensorOrientation.value = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return

        // ---- Preview: landscape 4:3 near 1280x960 ----
        val previewSizes = map.getOutputSizes(SurfaceTexture::class.java) ?: return
        if (previewSizes.isEmpty()) return

        val rotated = _sensorOrientation.value == 90 || _sensorOrientation.value == 270
        val candidates = if (rotated) {
            previewSizes.filter { it.width > it.height }
        } else {
            previewSizes.toList()
        }

        val targetRatio = 4f / 3f
        val bestPreview = candidates
            .sortedByDescending { s ->
                val ratio = s.width.toFloat() / s.height.toFloat()
                val ratioScore = if (kotlin.math.abs(ratio - targetRatio) < 0.1f) 10_000_000 else 0
                val sizeScore = -kotlin.math.abs(s.width - 1280) - kotlin.math.abs(s.height - 960)
                ratioScore + sizeScore
            }
            .firstOrNull()
            ?: candidates.maxByOrNull { it.width * it.height }
            ?: previewSizes.maxByOrNull { it.width * it.height }!!

        _previewSize.value = bestPreview

        // ---- Capture: highest 4:3 JPEG under ~4 MP ----
        val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
        if (jpegSizes != null && jpegSizes.isNotEmpty()) {
            val captureRatio = 4f / 3f
            val bestCapture = jpegSizes
                .filter { it.width * it.height <= 4_000_000 }
                .sortedByDescending { s ->
                    val ratio = s.width.toFloat() / s.height.toFloat()
                    val ratioScore = if (kotlin.math.abs(ratio - captureRatio) < 0.1f) 10_000_000 else 0
                    val sizeScore = s.width * s.height
                    ratioScore + sizeScore
                }
                .firstOrNull()
                ?: jpegSizes.maxByOrNull { it.width * it.height }!!

            _captureSize.value = bestCapture
        }

        Log.d(TAG, "Camera $cameraId: " +
            "preview=${_previewSize.value} " +
            "capture=${_captureSize.value} " +
            "orient=${_sensorOrientation.value}")
    }
}


