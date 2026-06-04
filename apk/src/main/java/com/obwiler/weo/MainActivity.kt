package com.obwiler.weo

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import com.obwiler.weo.camera.CameraHolder
import com.obwiler.weo.config.AppConfig
import com.obwiler.weo.config.ConfigHolder
import com.obwiler.weo.event.AppEvents
import com.obwiler.weo.image.ImagePipeline
import com.obwiler.weo.sensor.ImuProvider
import com.obwiler.weo.service.KeepAliveService
import com.obwiler.weo.ui.Screen
import com.obwiler.weo.ui.screen.CameraScreen
import com.obwiler.weo.ui.screen.HomeScreen
import com.obwiler.weo.ui.screen.ResultScreen
import com.obwiler.weo.ui.screen.SettingsScreen
import com.obwiler.weo.ui.screen.AboutScreen
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
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermission.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ))

        val app = application as com.obwiler.weo.app.WEOApplication
        cameraHolder = app.cameraHolder
        imuProvider = app.imuCollector
        configHolder = ConfigHolder(getExternalFilesDir(null)!!, "weo_config.json")
        configHolder.startWatching()
        aiClient = HttpAiClient()

        setContent {
            WeoTheme {
                var currentScreen by remember { currentScreenState }

                val scope = rememberCoroutineScope()

                LaunchedEffect(Unit) {
                    AppEvents.shutterRequest.receiveAsFlow().collect {
                        scope.launch { handleShutter() }
                    }
                }

                when (currentScreen) {
                    is Screen.Home -> {
                        HomeScreen(
                            onNavigate = { goTo(it) },
                            onExit = { finish() },
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
                            result = AiResult(
                                answer = r.answer,
                                steps = r.steps,
                            ),
                            photoPath = r.photoPath,
                            onBack = { goBack() },
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
                        AboutScreen(
                            onBack = { goBack() },
                        )
                    }
                }
            }
        }
    }

    // ---- key dispatch: only BACK for navigation; all DPAD keys pass to Compose ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (navStack.size > 1) {
                        goBack()
                        return true
                    }
                    // Home screen: let system handle BACK
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
                Log.w(TAG, "KeepAliveService start failed (background restricted)", e)
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
        try {
            unregisterReceiver(keyReceiver)
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(keyReceiver)
        } catch (_: Exception) {
        }
        stopService(Intent(this, KeepAliveService::class.java))
        configHolder.stopWatching()
        cameraHolder.release()
    }

    private suspend fun handleShutter() {
        val rawBytes = try {
            withContext(Dispatchers.IO) { cameraHolder.capture() }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed", e)
            goTo(Screen.Result(answer = "\u62CD\u6444\u5931\u8D25", steps = emptyList(), photoPath = null))
            return
        }

        val config = configHolder.load()
        val processed = withContext(Dispatchers.IO) {
            val src = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: throw RuntimeException("Bitmap decode failed")
            ImagePipeline.process(
                original = src,
                pitchDeg = imuProvider.pitchDeg,
                rollDeg = imuProvider.rollDeg,
                enableCorrection = config.textCorrectionEnabled,
            )
        }

        val photoPath = withContext(Dispatchers.IO) { savePhoto(processed) }

        val aiResult = aiClient?.let {
            try {
                it.analyze(processed, config)
            } catch (e: Exception) {
                Log.e(TAG, "AI analysis failed", e)
                AiResult(
                    answer = "AI \u5206\u6790\u5931\u8D25",
                    steps = emptyList(),
                    error = e.message,
                    errorCategory = ErrorCategory.UNKNOWN
                )
            }
        } ?: AiResult(
            answer = "",
            steps = emptyList(),
            error = "AI \u670D\u52A1\u672A\u5C31\u7EEA",
            errorCategory = ErrorCategory.UNKNOWN
        )

        val resultScreen = Screen.Result(
            answer = aiResult.answer,
            steps = aiResult.steps,
            photoPath = photoPath
        )

        // replace Camera with Result in nav stack
        if (navStack.lastOrNull() is Screen.Camera) {
            navStack.removeLast()
        }
        navStack.addLast(resultScreen)
        currentScreenState.value = resultScreen
    }

    private fun savePhoto(bytes: ByteArray): String? {
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "WEO"
            )
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "IMG_${timestamp}.jpg")
            file.writeBytes(bytes)
            Log.d(TAG, "Photo saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Save photo failed", e)
            null
        }
    }
}