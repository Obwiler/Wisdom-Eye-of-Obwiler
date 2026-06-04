package com.obwiler.weo.ui.screen

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.obwiler.weo.camera.CameraService
import com.obwiler.weo.camera.PreviewTransform
import com.obwiler.weo.sensor.ImuProvider
import com.obwiler.weo.ui.focus.DpadFocusContainer
import com.obwiler.weo.ui.focus.DpadFocusable
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreenFF
import com.obwiler.weo.ui.theme.WeoGreen99

@Composable
fun CameraScreen(
    cameraService: CameraService,
    imuProvider: ImuProvider,
    onShutter: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val textureView = remember { TextureView(context) }

    DisposableEffect(Unit) {
        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                cameraService.bindSurface(surface)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                val w = cameraService.previewSize.width
                val h = cameraService.previewSize.height
                val o = cameraService.sensorOrientation
                textureView.setTransform(
                    PreviewTransform.compute(width, height, w, h, o)
                )
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return true
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

    val camW = cameraService.previewSize.width
    val camH = cameraService.previewSize.height
    val orientation = cameraService.sensorOrientation
    val swapped = orientation == 90 || orientation == 270
    val displayW = if (swapped) camH else camW
    val displayH = if (swapped) camW else camH

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        val areaW = constraints.maxWidth.toFloat()
        val areaH = constraints.maxHeight.toFloat()
        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WeoBlack),
            contentAlignment = Alignment.Center
        ) {
            if (displayW > 0 && displayH > 0 && areaW > 0 && areaH > 0) {
                val scale = minOf(areaW / displayW, areaH / displayH)
                val previewW = (displayW * scale).toInt()
                val previewH = (displayH * scale).toInt()
                val padDp = with(density) { 2.dp }

                Box(
                    modifier = Modifier
                        .size(
                            width = with(density) { previewW.toDp() },
                            height = with(density) { previewH.toDp() }
                        )
                        .border(padDp, WeoGreenFF)
                ) {
                    AndroidView(
                        factory = { textureView },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // WEO 角标
        val miniSize = (minOf(areaW, areaH) * 0.25f).toInt()
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .size(
                    width = with(density) { miniSize.toDp() },
                    height = with(density) { miniSize.toDp() }
                )
                .border(1.dp, WeoGreenFF)
                .background(WeoBlack),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "WEO",
                color = WeoGreenFF,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        // ── DPAD 焦点区：底部快门 / 返回 ──
        DpadFocusContainer(
            itemCount = 2,
            initialFocusIndex = 0,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { focusIndex, registerOnClick ->

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DpadFocusable(
                    index = 0,
                    isFocused = focusIndex == 0,
                    onClick = { onShutter() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Text(
                        text = "\u5FEB\u95E8",
                        color = WeoGreenFF,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 14.dp)
                    )
                }
                registerOnClick(0) { onShutter() }

                DpadFocusable(
                    index = 1,
                    isFocused = focusIndex == 1,
                    onClick = { onBack() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    Text(
                        text = "\u8FD4\u56DE",
                        color = WeoGreen99,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(vertical = 14.dp)
                    )
                }
                registerOnClick(1) { onBack() }
            }
        }
    }
}
