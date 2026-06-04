package com.obwiler.weo.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.obwiler.weo.camera.CameraHolder
import com.obwiler.weo.config.ConfigHolder
import com.obwiler.weo.sensor.ImuCollector
import com.obwiler.weo.sensor.ImuProvider
import com.obwiler.weo.server.WeoApiHandler
import com.obwiler.weo.server.WeoHttpServer
import com.obwiler.weo.server.WifiHotspotManager
import java.io.File

/**
 * WEO 应用入口。持有全局单例并管理 HTTP 服务器生命周期。
 *
 * 服务器绑定 Application 生命周期：
 * - onCreate() → 启动 HTTP 服务器 + WiFi 热点
 * - onTerminate() → 停止一切服务
 *
 * 手机端通过 WiFi 连接后访问 http://<ip>:8765/
 */
class WEOApplication : Application() {

    companion object {
        private const val TAG = "WEO/App"
        private const val SERVER_PORT = 8765
    }

    lateinit var cameraHolder: CameraHolder
        private set

    lateinit var imuCollector: ImuProvider
        private set

    lateinit var configHolder: ConfigHolder
        private set

    lateinit var hotspotManager: WifiHotspotManager
        private set

    /** HTTP 服务器实例，供外部查询状态 */
    var httpServer: WeoHttpServer? = null
        private set

    private var apiHandler: WeoApiHandler? = null

    override fun onCreate() {
        super.onCreate()

        // 核心组件初始化
        cameraHolder = CameraHolder(this)
        imuCollector = ImuCollector(this)
        configHolder = ConfigHolder(
            getExternalFilesDir(null) ?: filesDir,
            "weo_config.json"
        )

        // 启动配置热更新监听
        configHolder.startWatching()

        // WiFi 热点管理器
        hotspotManager = WifiHotspotManager(this)

        // HTTP 服务器 — 绑定应用生命周期
        startServer()

        Log.i(TAG, "WEOApplication initialized, server=${if (httpServer?.isRunning == true) "running" else "stopped"}")
    }

    override fun onTerminate() {
        stopServer()
        configHolder.stopWatching()
        cameraHolder.release()
        super.onTerminate()
        Log.i(TAG, "WEOApplication terminated")
    }

    // ---- server lifecycle ----

    private fun startServer() {
        if (httpServer?.isRunning == true) return

        val handler = WeoApiHandler(
            context = this,
            cameraHolder = cameraHolder,
            configHolder = configHolder,
            imuProvider = imuCollector,
            hotspotManager = hotspotManager,
        )
        apiHandler = handler

        val server = WeoHttpServer(port = SERVER_PORT) { request -> handler.handle(request) }
        server.start()
        httpServer = server

        // 启动 WiFi 热点（如果可用）
        hotspotManager.startHotspot(SERVER_PORT)
    }

    private fun stopServer() {
        apiHandler?.shutdown()
        apiHandler = null
        httpServer?.stop()
        httpServer = null
        hotspotManager.stopHotspot()
    }
}
