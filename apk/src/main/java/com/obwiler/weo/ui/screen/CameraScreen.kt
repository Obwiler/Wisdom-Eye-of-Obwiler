package com.obwiler.weo.ui.screen

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.obwiler.weo.camera.CameraService
import com.obwiler.weo.camera.PreviewTransform
import com.obwiler.weo.event.AppEvents
import com.obwiler.weo.sensor.ImuProvider
import com.obwiler.weo.ui.focus.DpadFocusContainer
import com.obwiler.weo.ui.focus.DpadFocusable
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreenFF
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreen66
import com.obwiler.weo.ui.theme.WeoGreen33
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
fun CameraScreen(
    cameraService: CameraService,
    imuProvider: ImuProvider,
    onShutter: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val textureView = remember { TextureView(context) }

    var isCapturing by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // Observe camera state directly — no LaunchedEffect+awaitReady race.
    val isReady by cameraService.ready.collectAsState()
    val previewSize by cameraService.previewSize.collectAsState()
    val orientation by cameraService.sensorOrientation.collectAsState()

    // Keep a reference to the current SurfaceTexture so we can retry
    // startPreview when camera permission is granted.
    var currentSurface by remember { mutableStateOf<SurfaceTexture?>(null) }

    // ---- Permission-grant retry (first-launch race) ----
    LaunchedEffect(Unit) {
        AppEvents.cameraPermissionGranted.receiveAsFlow().collect {
            currentSurface?.let { surface ->
                cameraService.startPreview(surface)
            }
        }
    }

    // ---- Preview transform: re-apply when size or orientation changes ----
    LaunchedEffect(previewSize, orientation) {
        val w = previewSize.width
        val h = previewSize.height
        if (w <= 0 || h <= 0) return@LaunchedEffect
        textureView.post {
            val vw = textureView.measuredWidth
            val vh = textureView.measuredHeight
            if (vw > 0 && vh > 0) {
                textureView.setTransform(
                    PreviewTransform.compute(vw, vh, w, h, orientation)
                )
            }
        }
    }

    // ---- Surface-texture lifecycle ----
    DisposableEffect(Unit) {
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                currentSurface = surface
                cameraService.startPreview(surface)

                val w = cameraService.previewSize.value.width
                val h = cameraService.previewSize.value.height
                val o = cameraService.sensorOrientation.value
                if (w > 0 && h > 0 && width > 0 && height > 0) {
                    textureView.setTransform(
                        PreviewTransform.compute(width, height, w, h, o)
                    )
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                val w = cameraService.previewSize.value.width
                val h = cameraService.previewSize.value.height
                val o = cameraService.sensorOrientation.value
                if (w > 0 && h > 0) {
                    textureView.setTransform(
                        PreviewTransform.compute(width, height, w, h, o)
                    )
                }
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                currentSurface = null
                cameraService.stopPreview()
                return true  // surface is released — caller creates a new one
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        textureView.surfaceTextureListener = listener
        imuProvider.start()

        onDispose {
            imuProvider.stop()
            textureView.surfaceTextureListener = null
            cameraService.release()
        }
    }

    // ---- Animation ----
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotPulse",
    )

    // ---- UI ----
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        // Full-screen TextureView — always in composition so the surface
        // stays alive during capture (previously the Image replacement
        // destroyed the surface, racing with cameraHolder.capture()).
        AndroidView(
            factory = { textureView },
            modifier = Modifier.fillMaxSize(),
        )

        // Overlay the snapshot bitmap while capturing (purely visual).
        if (isCapturing && capturedBitmap != null) {
            Image(
                bitmap = capturedBitmap!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        ViewfinderCorners()

        // Loading overlay — shown when camera not ready or capturing
        if (!isReady || isCapturing) {
            val overlayText = if (!isReady) "相机初始化中" else "分 析 中"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WeoBlack.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = overlayText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeoGreenFF,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (i in 0..2) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .padding(horizontal = 4.dp)
                                    .clip(CircleShape)
                                    .background(WeoGreenFF.copy(alpha = dotAlpha - i * 0.15f))
                            )
                        }
                    }
                }
            }
        }

        // Status bar — preview resolution + IMU angles
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(WeoBlack.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "P:${imuProvider.pitchDeg.toInt()}" + '\u00b0' +
                    " R:${imuProvider.rollDeg.toInt()}" + '\u00b0',
                fontSize = 10.sp,
                color = WeoGreen66,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${previewSize.width}" + '\u00d7' + "${previewSize.height}",
                fontSize = 10.sp,
                color = WeoGreen66,
            )
        }

        // Bottom buttons — only when ready and not capturing
        if (isReady && !isCapturing) {
            DpadFocusContainer(
                itemCount = 2,
                initialFocusIndex = 0,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            ) { focusIndex, registerOnClick ->

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back button
                    DpadFocusable(
                        index = 1,
                        isFocused = focusIndex == 1,
                        onClick = { onBack() },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(WeoGreen33),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "\u2190", color = WeoGreenFF, fontSize = 18.sp)
                        }
                    }
                    registerOnClick(1) { onBack() }

                    // Shutter button
                    DpadFocusable(
                        index = 0,
                        isFocused = focusIndex == 0,
                        onClick = {
                            try {
                                val bmp = textureView.bitmap
                                if (bmp != null) {
                                    capturedBitmap = bmp.asImageBitmap()
                                }
                            } catch (_: Exception) {}
                            isCapturing = true
                            onShutter()
                        },
                        modifier = Modifier.size(68.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(3.dp, WeoGreenFF, CircleShape)
                                .padding(5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(WeoGreenFF)
                            )
                        }
                    }
                    registerOnClick(0) {
                        try {
                            val bmp = textureView.bitmap
                            if (bmp != null) {
                                capturedBitmap = bmp.asImageBitmap()
                            }
                        } catch (_: Exception) {}
                        isCapturing = true
                        onShutter()
                    }

                    Spacer(modifier = Modifier.size(44.dp))
                }
            }
        }
    }
}

@Composable
private fun ViewfinderCorners() {
    val cornerLen = 24.dp
    val cornerW = 2.dp
    val margin = 0.dp
    val color = WeoGreen66

    Box(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopStart).padding(margin).size(cornerLen, cornerW).background(color))
        Box(Modifier.align(Alignment.TopStart).padding(margin).size(cornerW, cornerLen).background(color))
        Box(Modifier.align(Alignment.TopEnd).padding(margin).size(cornerLen, cornerW).background(color))
        Box(Modifier.align(Alignment.TopEnd).padding(margin).size(cornerW, cornerLen).background(color))
        Box(Modifier.align(Alignment.BottomStart).padding(margin).size(cornerLen, cornerW).background(color))
        Box(Modifier.align(Alignment.BottomStart).padding(margin).size(cornerW, cornerLen).background(color))
        Box(Modifier.align(Alignment.BottomEnd).padding(margin).size(cornerLen, cornerW).background(color))
        Box(Modifier.align(Alignment.BottomEnd).padding(margin).size(cornerW, cornerLen).background(color))
    }
}
