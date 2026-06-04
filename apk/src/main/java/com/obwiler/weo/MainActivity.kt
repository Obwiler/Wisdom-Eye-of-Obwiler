package com.obwiler.weo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.BitmapFactory.Options
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.obwiler.weo.ai.AiClient
import com.obwiler.weo.ai.AiResult
import com.obwiler.weo.ai.ErrorCategory
import com.obwiler.weo.ai.HttpAiClient
import com.obwiler.weo.app.WEOApplication
import com.obwiler.weo.camera.CameraHolder
import com.obwiler.weo.config.AppConfig
import com.obwiler.weo.config.ConfigHolder
import com.obwiler.weo.event.AppEvents
import com.obwiler.weo.image.ImagePipeline
import com.obwiler.weo.photo.PhotoRepository
import com.obwiler.weo.photo.PhotoRename
import com.obwiler.weo.sensor.ImuProvider
import com.obwiler.weo.server.WifiHotspotManager
import com.obwiler.weo.service.KeepAliveService
import com.obwiler.weo.ui.Screen
import com.obwiler.weo.ui.screen.CameraScreen
import com.obwiler.weo.ui.screen.HomeScreen
import com.obwiler.weo.ui.screen.ResultScreen
import com.obwiler.weo.ui.screen.SettingsScreen
import com.obwiler.weo.ui.screen.AboutScreen
import com.obwiler.weo.ui.screen.GalleryScreen
import com.obwiler.weo.ui.screen.WifiScreen
import com.obwiler.weo.ui.theme.WeoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WEO/Main"
    }

    private lateinit var cameraHolder: CameraHolder
    private lateinit var imuProvider: ImuProvider
    private lateinit var configHolder: ConfigHolder
    private var aiClient: AiClient? = null
    private var serviceStarted = false
    private var backgroundMode = false

    private val navStack = ArrayDeque<Screen>().apply { addLast(Screen.Home) }
    private val currentScreenState = mutableStateOf<Screen>(Screen.Home)

    // ---- navigation helpers ----

    private fun goTo(screen: Screen) {
        navStack.addLast(screen)
        currentScreenState.value = screen
    }

    private fun goBack() {
        if (navStack.size > 1) {
            navStack.removeLast()
            currentScreenState.value = navStack.last()
        }
    }

    // ---- broadcast receiver (config updates from PC tool) ----

    private val keyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.obwiler.weo.CONFIG_UPDATED" -> {
                    Log.d(TAG, "CONFIG_UPDATED received")
                    configHolder.invalidate()
                    AppEvents.configUpdated.trySend(Unit)
                }
            }
        }
    }

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val cameraOk = granted[Manifest.permission.CAMERA] == true
            val storageOk = granted[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            Log.d(TAG, "Permissions: CAMERA=$cameraOk, STORAGE=$storageOk")
            if (cameraOk) {
                AppEvents.cameraPermissionGranted.trySend(Unit)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermission.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ))

        val app = application as WEOApplication
        cameraHolder = app.cameraHolder
        imuProvider = app.imuCollector
        configHolder = app.configHolder
        aiClient = HttpAiClient()

        setContent {
            WeoTheme {
                var currentScreen by remember { currentScreenState }
                var showExitDialog by remember { mutableStateOf(false) }

                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    AppEvents.shutterRequest.receiveAsFlow().collect {
                        scope.launch { handleShutter() }
                    }
                }

                when (currentScreen) {
                    is Screen.Home -> {
                        val hotspot = (application as WEOApplication).hotspotManager.hotspotInfo
                        val serverAddr = if (hotspot.active && hotspot.ipAddress != null) {
                            "${hotspot.ipAddress}:${hotspot.port}"
                        } else null
                        HomeScreen(
                            onNavigate = { goTo(it) },
                            onExit = { showExitDialog = true },
                            serverInfo = serverAddr,
                        )
                    }
                    is Screen.Camera -> {
                        CameraScreen(
                            cameraService = cameraHolder,
                            imuProvider = imuProvider,
                            onShutter = { AppEvents.shutterRequest.trySend(Unit) },
                            onBack = { goBack() },
                        )
                    }
                    is Screen.Result -> {
                        val r = currentScreen as Screen.Result
                        ResultScreen(
                            result = AiResult(answer = r.answer, steps = r.steps),
                            photoPath = r.photoPath,
                            onBack = { goBack() },
                            onRetake = {
                                if (navStack.lastOrNull() is Screen.Result) navStack.removeLast()
                                goTo(Screen.Camera)
                            },
                        )
                    }
                    is Screen.Settings -> {
                        val cfg by configHolder.config.collectAsState()
                        SettingsScreen(
                            config = cfg,
                            onNavigateToAbout = { goTo(Screen.About) },
                            onBack = { goBack() },
                        )
                    }
                    is Screen.About -> {
                        AboutScreen(onBack = { goBack() })
                    }
                    is Screen.Gallery -> {
                        GalleryScreen(onBack = { goBack() })
                    }
                    is Screen.Wifi -> {
                        WifiScreen(onBack = { goBack() })
                    }
                }

                // ---- Exit Dialog ----
                if (showExitDialog) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { showExitDialog = false },
                        title = { androidx.compose.material3.Text("退出应用") },
                        text = { androidx.compose.material3.Text("请选择退出方式") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showExitDialog = false
                                // 完全退出：关闭 HTTP 服务器和所有服务
                                shutdownServer()
                                finish()
                            }) { androidx.compose.material3.Text("完全退出") }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                showExitDialog = false
                                backgroundMode = true
                                moveTaskToBack(true)
                            }) { androidx.compose.material3.Text("后台运行") }
                        },
                    )
                }
            }
        }
    }

    // ---- key dispatch ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (navStack.size > 1) {
                        goBack()
                        return true
                    }
                    // 根页面按返回不拦截，让系统处理（退出或后台）
                    return false
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (!serviceStarted) {
            try {
                val intent = Intent(this, KeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                serviceStarted = true
            } catch (e: Exception) {
                Log.w(TAG, "KeepAliveService start failed", e)
            }
        }

        val filter = IntentFilter().apply {
            addAction("com.obwiler.weo.CONFIG_UPDATED")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keyReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(keyReceiver) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(keyReceiver) } catch (_: Exception) {}
        // 确保资源释放（Android 不保证 onTerminate 被调用）
        if (!backgroundMode) {
            shutdownServer()
        }
        stopService(Intent(this, KeepAliveService::class.java))
        configHolder.stopWatching()
        cameraHolder.release()
    }

    private fun shutdownServer() {
        val app = application as? WEOApplication ?: return
        app.hotspotManager.stopHotspot()
        app.httpServer?.stop()
        Log.i(TAG, "HTTP server and hotspot shut down")
    }

    private suspend fun handleShutter() {
        Log.d(TAG, "handleShutter: starting capture on IO dispatcher")
        val rawBytes = try {
            withContext(Dispatchers.IO) { cameraHolder.capture().also { Log.d(TAG, "handleShutter: captured ${it.size} bytes") } }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            goTo(Screen.Result(answer = "拍摄失败", steps = emptyList(), photoPath = null))
            return
        }

        val config = configHolder.load()
        val processed = withContext(Dispatchers.IO) {
            val tDecode = System.currentTimeMillis()
            // Decode at 1/2 resolution to halve memory on the 1.8GB device.
            // The RG-glasses capture at 2048x1536; inSampleSize=2 yields
            // 1024x768 which is plenty for AI analysis.
            val opts = Options().apply { inSampleSize = 2 }
            val src = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                ?: throw RuntimeException("Bitmap decode failed")
            Log.d(TAG, "handleShutter: decoded ${src.width}x${src.height} in ${System.currentTimeMillis() - tDecode}ms")
            val result = ImagePipeline.process(
                original = src,
                pitchDeg = imuProvider.pitchDeg,
                rollDeg = imuProvider.rollDeg,
                enableCorrection = config.textCorrectionEnabled,
            )
            src.recycle()
            result
        }

        var photoPath: String? = null
        var photoRecord: com.obwiler.weo.photo.PhotoRecord? = null
        withContext(Dispatchers.IO) {
            photoRecord = PhotoRepository.save(this@MainActivity, processed)
            photoPath = photoRecord?.filePath
            Log.d(TAG, "Photo archived: $photoPath")
        }

        val _aiStartMs = System.currentTimeMillis()
        Log.d(TAG, "Calling AI: url=${config.apiBaseUrl.take(50)}, key=${config.apiKey.take(4)}..., model=${config.modelName}")
        val aiResult = aiClient?.let {
            try {
                it.analyze(processed, config)
            } catch (e: Exception) {
                Log.e(TAG, "AI analysis failed", e)
                AiResult(answer = "AI 分析失败", steps = emptyList(), error = e.message, errorCategory = ErrorCategory.UNKNOWN)
            }
        } ?: AiResult(answer = "", steps = emptyList(), error = "AI 服务未就绪", errorCategory = ErrorCategory.UNKNOWN)

        Log.d(TAG, "AI done in ${System.currentTimeMillis() - _aiStartMs}ms, answer=${aiResult.answer.take(30)}")

        val record = photoRecord
        if (record != null && aiResult.answer.isNotBlank() && aiResult.error == null) {
            withContext(Dispatchers.IO) {
                val newName = PhotoRename.generateName(aiResult.answer)
                val renamed = PhotoRepository.rename(this@MainActivity, record, newName)
                if (renamed != null) {
                    photoPath = renamed.filePath
                    photoRecord = PhotoRepository.updateSummary(renamed, aiResult.answer.take(80))
                    Log.d(TAG, "Renamed: $newName")
                }
            }
        }

        val resultScreen = Screen.Result(
            answer = aiResult.answer,
            steps = aiResult.steps,
            photoPath = photoPath
        )

        if (navStack.lastOrNull() is Screen.Camera) navStack.removeLast()
        navStack.addLast(resultScreen)
        currentScreenState.value = resultScreen
    }
}
