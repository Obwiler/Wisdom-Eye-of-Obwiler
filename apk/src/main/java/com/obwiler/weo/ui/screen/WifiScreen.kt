package com.obwiler.weo.ui.screen

import android.content.Context
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.network.WifiHelper
import com.obwiler.weo.network.WifiNetwork
import com.obwiler.weo.ui.component.DpadKeyboard
import com.obwiler.weo.ui.focus.DpadFocusContainer
import com.obwiler.weo.ui.focus.DpadFocusable
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreen1A
import com.obwiler.weo.ui.theme.WeoGreen33
import com.obwiler.weo.ui.theme.WeoGreen66
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreenCC
import com.obwiler.weo.ui.theme.WeoGreenFF
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private fun isLocationEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ||
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (e: Exception) {
        false
    }
}

@Composable
fun WifiScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val wifi = remember { WifiHelper(context) }
    val scope = rememberCoroutineScope()

    // Reactive WiFi state from system broadcast (not optimistic local state)
    val wifiEnabled by wifi.wifiStateFlow().collectAsState(initial = wifi.isWifiEnabled())

    // UI state
    var networks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var currentSsid by remember { mutableStateOf(wifi.currentSsid()) }
    var selectedSsid by remember { mutableStateOf<String?>(null) }
    var wifiPassword by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf("") }
    var showKeyboard by remember { mutableStateOf(false) }
    var scanTrigger by remember { mutableStateOf(0) }

    // Scan networks when WiFi becomes enabled or scanTrigger changes
    LaunchedEffect(wifiEnabled, scanTrigger) {
        if (!wifiEnabled) {
            networks = emptyList()
            isScanning = false
            if (wifi.isWifiToggleBlockedBySystem()) {
                statusMsg = "系统限制：请在 WiFi 设置中手动开启"
            } else {
                statusMsg = "WiFi 已关闭"
            }
            return@LaunchedEffect
        }

        if (!isLocationEnabled(context)) {
            statusMsg = "位置服务未开启\nAndroid 10+ 扫描 WiFi 需要开启位置"
            isScanning = false
            return@LaunchedEffect
        }

        isScanning = true
        statusMsg = "正在扫描..."
        try {
            wifi.scanResults().collectLatest { results ->
                networks = results
                isScanning = false
                if (results.isEmpty()) {
                    statusMsg = "未扫描到网络\n按「刷新扫描」重试"
                } else {
                    statusMsg = ""
                }
            }
        } catch (e: Exception) {
            isScanning = false
            statusMsg = "扫描失败: ${e.message}"
        }
    }

    // Periodically refresh SSID
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            if (wifiEnabled) {
                currentSsid = wifi.currentSsid()
            }
        }
    }

    // Item count for DPad
    val fixedTop = 2
    val fixedBottom = 1
    val dpadItemCount = if (showKeyboard) 0 else fixedTop + networks.size + fixedBottom

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WiFi",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WeoGreenFF,
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        // Keyboard area for password input
        if (showKeyboard && selectedSsid != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(WeoGreen1A)
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "连接: $selectedSsid",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeoGreenFF,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "返回",
                        fontSize = 13.sp,
                        color = WeoGreen66,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                showKeyboard = false
                                selectedSsid = null
                                wifiPassword = ""
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                DpadKeyboard(
                    onTextChanged = { wifiPassword = it },
                    onCancel = {
                        showKeyboard = false
                        selectedSsid = null
                        wifiPassword = ""
                    },
                    onConfirm = {
                        val ssid = selectedSsid ?: return@DpadKeyboard
                        val pwd = wifiPassword
                        if (pwd.isBlank()) return@DpadKeyboard
                        scope.launch {
                            statusMsg = "正在扫描..."
                            val ok = wifi.connectWpa2(ssid, pwd)
                            if (ok) {
                                delay(3000)
                                currentSsid = wifi.currentSsid()
                                statusMsg = if (currentSsid == ssid) "连接成功!" else "连接失败"
                            } else {
                                statusMsg = "连接失败"
                            }
                            showKeyboard = false
                            selectedSsid = null
                            wifiPassword = ""
                        }
                    },
                )
            }
        }

        // Main content area with DPad focus
        if (!showKeyboard) {
            DpadFocusContainer(
                itemCount = dpadItemCount,
                initialFocusIndex = 0,
            ) { focusIndex, registerOnClick ->

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                ) {
                    // Item 0: WiFi toggle
                    DpadFocusable(
                        index = 0,
                        isFocused = focusIndex == 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(WeoGreen1A)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "WiFi",
                                fontSize = 16.sp,
                                color = WeoGreenFF,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (wifiEnabled) "ON" else "OFF",
                                fontSize = 14.sp,
                                color = if (wifiEnabled) WeoGreenFF else WeoGreen66,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(WeoGreen33)
                                    .clickable {
                                        // Open system WiFi panel (app cannot toggle on Android 10+)
                                        wifi.openWifiPanel()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                    }
                    registerOnClick(0) {
                        wifi.openWifiPanel()
                    }

                    // Item 1: Scan/Refresh button
                    DpadFocusable(
                        index = 1,
                        isFocused = focusIndex == 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(WeoGreen1A)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (isScanning) "正在扫描..." else "刷新扫描",
                                fontSize = 14.sp,
                                color = if (isScanning) WeoGreen99 else WeoGreenCC,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "↻",
                                fontSize = 18.sp,
                                color = WeoGreenCC,
                            )
                        }
                    }
                    registerOnClick(1) {
                        if (!isScanning && wifiEnabled) {
                            scanTrigger++
                        }
                    }

                    // Current connection info
                    if (currentSsid != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(WeoGreen1A)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = "✓", color = WeoGreenFF, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(text = "已连接", fontSize = 11.sp, color = WeoGreen99)
                                Text(
                                    text = currentSsid!!,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeoGreenFF,
                                )
                            }
                        }
                    }

                    // Status message
                    if (statusMsg.isNotBlank()) {
                        Text(
                            text = statusMsg,
                            fontSize = 12.sp,
                            color = if (statusMsg.contains("失败") || statusMsg.contains("未开启")) WeoGreen66 else WeoGreen99,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Network list
                    if (wifiEnabled) {
                        if (networks.isEmpty() && !isScanning) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "无可用网络\n按「刷新扫描」搜索",
                                    fontSize = 13.sp,
                                    color = WeoGreen66,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            val savedFirst = networks.sortedByDescending { it.isSaved }
                            savedFirst.forEachIndexed { idx, net ->
                                val dpadIdx = fixedTop + idx
                                val isConnected = net.ssid == currentSsid
                                DpadFocusable(
                                    index = dpadIdx,
                                    isFocused = focusIndex == dpadIdx,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isConnected) WeoGreen1A else Color.Transparent)
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val bars = net.level.coerceIn(0, 5)
                                        Text(
                                            text = when {
                                                isConnected -> "✓"
                                                bars >= 4 -> "▄█"
                                                bars >= 3 -> "▄▄"
                                                bars >= 2 -> "▄▄"
                                                else -> "·"
                                            },
                                            fontSize = 14.sp,
                                            color = when {
                                                isConnected -> WeoGreenFF
                                                bars >= 3 -> WeoGreenCC
                                                else -> WeoGreen66
                                            },
                                            modifier = Modifier.width(24.dp),
                                            textAlign = TextAlign.Center,
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = net.ssid,
                                                fontSize = 15.sp,
                                                fontWeight = if (isConnected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isConnected) WeoGreenFF else WeoGreenCC,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = if (net.isSaved) "已保存"
                                                       else if (net.capabilities.contains("WPA")) "WPA"
                                                       else "开放",
                                                fontSize = 10.sp,
                                                color = WeoGreen66,
                                            )
                                        }
                                    }
                                }
                                registerOnClick(dpadIdx) {
                                    if (!isConnected) {
                                        if (net.isSaved) {
                                            val configs = wifi.savedNetworks()
                                            val cfg = configs.find {
                                                it.SSID.removeSurrounding("\"") == net.ssid
                                            }
                                            if (cfg != null) {
                                                scope.launch {
                                                    wifi.connectToSaved(cfg.networkId)
                                                    delay(3000)
                                                    currentSsid = wifi.currentSsid()
                                                    statusMsg = if (currentSsid == net.ssid) "连接成功!" else ""
                                                }
                                            }
                                        } else {
                                            selectedSsid = net.ssid
                                            wifiPassword = ""
                                            showKeyboard = true
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Back button
                    val backIdx = fixedTop + networks.size
                    DpadFocusable(
                        index = backIdx,
                        isFocused = focusIndex == backIdx,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(WeoGreen1A)
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "返回",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeoGreenFF,
                            )
                        }
                    }
                    registerOnClick(backIdx) { onBack() }
                }
            }
        }
    }
}
