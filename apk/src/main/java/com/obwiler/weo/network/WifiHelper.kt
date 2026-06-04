package com.obwiler.weo.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val level: Int,           // 0-5 signal level
    val frequency: Int,
    val isSaved: Boolean,
)

class WifiHelper(private val context: Context) {

    companion object {
        private const val TAG = "WEO/WiFi"
    }

    private val appContext = context.applicationContext
    private val wifiManager: WifiManager =
        appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    // ---- WiFi state ----

    /** Check if WiFi is currently enabled (snapshot). */
    fun isWifiEnabled(): Boolean = wifiManager.isWifiEnabled

    /** Reactive Flow of WiFi enabled state via WIFI_STATE_CHANGED_ACTION broadcast. */
    fun wifiStateFlow(): Flow<Boolean> = callbackFlow {
        trySend(wifiManager.isWifiEnabled)

        val filter = IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val state = intent?.getIntExtra(
                    WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN
                ) ?: return
                when (state) {
                    WifiManager.WIFI_STATE_ENABLED -> trySend(true)
                    WifiManager.WIFI_STATE_DISABLED -> trySend(false)
                }
            }
        }

        try {
            appContext.registerReceiver(receiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register WiFi state receiver", e)
        }

        awaitClose {
            try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    /** Whether programmatic WiFi toggle is blocked (Android 10+ for non-system apps). */
    fun isWifiToggleBlockedBySystem(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Attempt to set WiFi enabled (blocked on Android 10+). Returns actual result. */
    fun setWifiEnabled(enabled: Boolean): Boolean {
        return try {
            wifiManager.isWifiEnabled = enabled
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "setWifiEnabled blocked by system (API ${Build.VERSION.SDK_INT})")
            false
        }
    }

    /** Open system WiFi settings panel so the user can toggle WiFi. */
    fun openWifiPanel() {
        try {
            val intent = Intent(Settings.Panel.ACTION_WIFI)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Cannot open WiFi settings", e2)
            }
        }
    }

    // ---- SSID / IP ----

    /** Get current connected SSID, or null if not connected. */
    fun currentSsid(): String? {
        val info = wifiManager.connectionInfo
        return info?.ssid?.removeSurrounding("\"")?.takeIf { it != "<unknown ssid>" && it.isNotBlank() }
    }

    /** Get current IP address. */
    fun currentIp(): Int? {
        val info = wifiManager.connectionInfo
        return info?.ipAddress?.takeIf { it != 0 }
    }

    // ---- Scan helpers ----

    private fun mapScanResults(savedConfigs: Map<String, WifiConfiguration>): List<WifiNetwork> {
        return try {
            wifiManager.scanResults
                ?.filter { it.SSID.isNotBlank() }
                ?.map { result ->
                    val rawSsid = result.SSID.removeSurrounding("\"")
                    WifiNetwork(
                        ssid = rawSsid,
                        bssid = result.BSSID,
                        capabilities = result.capabilities,
                        level = WifiManager.calculateSignalLevel(result.level, 5),
                        frequency = result.frequency,
                        isSaved = savedConfigs.containsKey(rawSsid),
                    )
                }
                ?.sortedByDescending { it.level }
                ?: emptyList()
        } catch (e: SecurityException) {
            Log.w(TAG, "Permission denied reading scan results", e)
            emptyList()
        }
    }

    // ---- Scan Flow ----

    /** Scan for available WiFi networks. Uses broadcast receiver + polling fallback. */
    fun scanResults(): Flow<List<WifiNetwork>> = callbackFlow {
        if (!wifiManager.isWifiEnabled) {
            Log.d(TAG, "scanResults: WiFi disabled, returning empty")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val savedConfigs = try {
            wifiManager.configuredNetworks?.associateBy { it.SSID.removeSurrounding("\"") } ?: emptyMap()
        } catch (e: SecurityException) {
            emptyMap()
        }

        // Try to send immediately available results (from a prior scan)
        val existing = mapScanResults(savedConfigs)
        if (existing.isNotEmpty()) {
            trySend(existing)
        }

        var broadcastReceived = false

        // Polling fallback: if broadcast does not fire within 3s, read results directly
        val pollJob = launch {
            delay(3000)
            if (!broadcastReceived) {
                Log.d(TAG, "scanResults: broadcast timeout, polling directly")
                val results = mapScanResults(savedConfigs)
                trySend(results)
                close()
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true) {
                    broadcastReceived = true
                    pollJob.cancel()
                    val results = mapScanResults(savedConfigs)
                    Log.d(TAG, "scanResults: broadcast received, ${results.size} networks")
                    trySend(results)
                    close()
                }
            }
        }

        try {
            appContext.registerReceiver(
                receiver,
                IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register scan receiver", e)
            pollJob.cancel()
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val scanOk = try {
            wifiManager.startScan()
        } catch (e: SecurityException) {
            Log.e(TAG, "Scan permission denied", e)
            false
        }

        Log.d(TAG, "scanResults: startScan returned $scanOk")

        if (!scanOk) {
            Log.w(TAG, "startScan returned false -- scan may be throttled; waiting for poll fallback")
        }

        awaitClose {
            pollJob.cancel()
            try { appContext.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // ---- Saved networks ----

    fun savedNetworks(): List<WifiConfiguration> {
        return try {
            wifiManager.configuredNetworks ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun connectToSaved(networkId: Int): Boolean {
        return try {
            wifiManager.disconnect()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to connect to network $networkId", e)
            false
        }
    }

    fun connectWpa2(ssid: String, password: String): Boolean {
        return try {
            val config = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                allowedProtocols.set(WifiConfiguration.Protocol.RSN)
                allowedProtocols.set(WifiConfiguration.Protocol.WPA)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP)
                allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP)
                allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP)
            }
            val networkId = wifiManager.addNetwork(config)
            if (networkId == -1) {
                Log.e(TAG, "addNetwork failed for $ssid")
                return false
            }
            wifiManager.disconnect()
            wifiManager.enableNetwork(networkId, true)
            wifiManager.reconnect()
            Log.d(TAG, "Connecting to $ssid (id=$networkId)")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to connect to $ssid", e)
            false
        }
    }

    fun forgetNetwork(networkId: Int): Boolean {
        return try {
            wifiManager.removeNetwork(networkId)
            wifiManager.saveConfiguration()
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
