package com.obwiler.weo.server

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import java.lang.reflect.Method

/**
 * WiFi 热点管理器 — 眼镜端自动开启本地热点供手机连接。
 *
 * 策略：
 * - API 26+: 使用 WifiManager.startLocalOnlyHotspot()（无需系统权限，不共享互联网）
 * - 降级方案: 反射调用 setWifiApEnabled（部分设备需要）
 *
 * 热点信息通过 WeoApiHandler 暴露为 /api/status。
 */
class WifiHotspotManager(private val context: Context) {

    companion object {
        private const val TAG = "WEO/Hotspot"
        private const val HOTSPOT_SSID = "WEO-Glasses"
        private const val HOTSPOT_PASS = "weo123456"
    }

    private val wifiManager: WifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var localOnlyReservation: WifiManager.LocalOnlyHotspotReservation? = null

    /** 热点信息快照 */
    data class HotspotInfo(
        val ssid: String,
        val passphrase: String,
        val ipAddress: String?,
        val port: Int,
        val active: Boolean,
    )

    var hotspotInfo: HotspotInfo = HotspotInfo("", "", null, 0, false)
        private set

    /**
     * 开启本地热点。异步操作，结果通过 hotspotInfo 反映。
     */
    fun startHotspot(serverPort: Int) {
        if (hotspotInfo.active) {
            Log.d(TAG, "Hotspot already active: ${hotspotInfo.ssid}")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startLocalOnlyHotspot(serverPort)
            } else {
                startLegacyHotspot(serverPort)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hotspot", e)
            updateInfoWithCurrentNetwork(serverPort)
        }
    }

    private fun startLocalOnlyHotspot(serverPort: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        var resolved = false

        // 5s timeout fallback — some ROMs never fire any callback
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!resolved) {
                Log.w(TAG, "LocalOnlyHotspot timed out after 5s, falling back to current network")
                updateInfoWithCurrentNetwork(serverPort)
            }
        }, 5000)

        wifiManager.startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
            override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                resolved = true
                localOnlyReservation = reservation
                val config = reservation.wifiConfiguration
                val info = HotspotInfo(
                    ssid = config?.SSID?.removeSurrounding("\"") ?: HOTSPOT_SSID,
                    passphrase = config?.preSharedKey?.removeSurrounding("\"") ?: HOTSPOT_PASS,
                    ipAddress = getLocalIpAddress(),
                    port = serverPort,
                    active = true,
                )
                hotspotInfo = info
                Log.i(TAG, "Local-only hotspot started: SSID=${info.ssid}, IP=${info.ipAddress}")
            }

            override fun onStopped() {
                Log.w(TAG, "Local-only hotspot stopped by system")
                hotspotInfo = hotspotInfo.copy(active = false)
                localOnlyReservation = null
            }

            override fun onFailed(reason: Int) {
                resolved = true
                Log.e(TAG, "Local-only hotspot failed: reason=$reason")
                updateInfoWithCurrentNetwork(serverPort)
            }
        }, null)
    }

    private fun startLegacyHotspot(serverPort: Int): Boolean {
        try {
            val config = WifiConfiguration().apply {
                SSID = "\"$HOTSPOT_SSID\""
                preSharedKey = "\"$HOTSPOT_PASS\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }
            val method: Method = wifiManager.javaClass.getMethod(
                "setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType
            )
            method.invoke(wifiManager, config, true)

            hotspotInfo = HotspotInfo(
                ssid = HOTSPOT_SSID,
                passphrase = HOTSPOT_PASS,
                ipAddress = "192.168.43.1",
                port = serverPort,
                active = true,
            )
            Log.i(TAG, "Legacy hotspot started: SSID=$HOTSPOT_SSID")
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Legacy hotspot not available", e)
            updateInfoWithCurrentNetwork(serverPort)
            return false
        }
    }

    /** 停止热点 */
    fun stopHotspot() {
        try {
            localOnlyReservation?.close()
        } catch (_: Exception) {}
        localOnlyReservation = null

        try {
            val method: Method = wifiManager.javaClass.getMethod(
                "setWifiApEnabled", WifiConfiguration::class.java, Boolean::class.javaPrimitiveType
            )
            method.invoke(wifiManager, null, false)
        } catch (_: Exception) {}

        hotspotInfo = HotspotInfo("", "", null, 0, false)
        Log.i(TAG, "Hotspot stopped")
    }

    /**
     * 更新热点信息快照（用于热点未开启但服务器仍在运行的情况）。
     */
    fun updateInfoWithCurrentNetwork(serverPort: Int) {
        if (!wifiManager.isWifiEnabled) {
            hotspotInfo = HotspotInfo("", "", null, serverPort, false)
            return
        }
        val info = wifiManager.connectionInfo
        hotspotInfo = HotspotInfo(
            ssid = info?.ssid?.removeSurrounding("\"") ?: "",
            passphrase = "",
            ipAddress = getLocalIpAddress(),
            port = serverPort,
            active = true,
        )
    }

    private fun getLocalIpAddress(): String? {
        val info = wifiManager.connectionInfo
        val ip = info?.ipAddress ?: return null
        return if (ip != 0) {
            "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
        } else null
    }
}
