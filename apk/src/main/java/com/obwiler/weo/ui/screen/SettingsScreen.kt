package com.obwiler.weo.ui.screen

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.config.AppConfig
import com.obwiler.weo.ui.focus.DpadFocusContainer
import com.obwiler.weo.ui.focus.DpadFocusable
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreenCC
import com.obwiler.weo.ui.theme.WeoGreenFF
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreen66

@Composable
fun SettingsScreen(
    config: AppConfig,
    onNavigateToAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val networkStatus = remember {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val net = cm.activeNetwork
            val caps = if (net != null) cm.getNetworkCapabilities(net) else null
            val hasNet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val ssid = if (isWifi) {
                try {
                    wm.connectionInfo?.ssid?.removeSurrounding("\"") ?: ""
                } catch (_: SecurityException) {
                    ""
                }
            } else ""
            when {
                isWifi && ssid.isNotEmpty() -> "WiFi: $ssid"
                hasNet -> "\u7F51\u7EDC: \u5DF2\u8FDE\u63A5"
                else -> "\u7F51\u7EDC: \u672A\u8FDE\u63A5"
            }
        } catch (_: Exception) {
            "\u7F51\u7EDC: \u672A\u77E5"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        Text(
            text = "\u8BBE\u7F6E",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = WeoGreenFF,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 16.dp)
        )

        DpadFocusContainer(
            itemCount = 6,
            initialFocusIndex = 0,
            scrollState = scrollState,
            itemHeightDp = 60,
            modifier = Modifier.weight(1f),
        ) { focusIndex, registerOnClick ->

            Column(modifier = Modifier.fillMaxSize()) {

                // ── 可滚动区域 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp)
                ) {
                    // 0: API 地址
                    DpadFocusable(
                        index = 0,
                        isFocused = focusIndex == 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 4.dp)
                            .border(1.dp, WeoGreen66, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "API \u5730\u5740",
                                fontSize = 17.sp,
                                color = WeoGreenFF,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = config.apiBaseUrl.ifBlank { "\u672A\u914D\u7F6E" },
                                fontSize = 15.sp,
                                color = WeoGreen99,
                                maxLines = 1
                            )
                        }
                    }

                    // 1: API Key
                    DpadFocusable(
                        index = 1,
                        isFocused = focusIndex == 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 4.dp)
                            .border(1.dp, WeoGreen66, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "API Key",
                                fontSize = 17.sp,
                                color = WeoGreenFF,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = config.apiKey.let {
                                    if (it.length > 4) it.take(4) + "****" else if (it.isNotEmpty()) it else "\u672A\u914D\u7F6E"
                                },
                                fontSize = 15.sp,
                                color = WeoGreen99,
                                maxLines = 1
                            )
                        }
                    }

                    // 2: 模型
                    DpadFocusable(
                        index = 2,
                        isFocused = focusIndex == 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 4.dp)
                            .border(1.dp, WeoGreen66, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u6A21\u578B",
                                fontSize = 17.sp,
                                color = WeoGreenFF,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = config.modelName.ifBlank { "\u672A\u914D\u7F6E" },
                                fontSize = 15.sp,
                                color = WeoGreen99,
                                maxLines = 1
                            )
                        }
                    }

                    // 3: 网络状态
                    DpadFocusable(
                        index = 3,
                        isFocused = focusIndex == 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 4.dp)
                            .border(1.dp, WeoGreen66, RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u7F51\u7EDC\u72B6\u6001",
                                fontSize = 17.sp,
                                color = WeoGreenFF,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = networkStatus,
                                fontSize = 15.sp,
                                color = WeoGreen99,
                                maxLines = 1
                            )
                        }
                    }

                    // 4: 关于
                    DpadFocusable(
                        index = 4,
                        isFocused = focusIndex == 4,
                        onClick = { onNavigateToAbout() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(vertical = 4.dp)
                            .border(1.dp, WeoGreen66, RoundedCornerShape(8.dp)),
                    ) {
                        Text(
                            text = "\u5173\u4E8E",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeoGreenFF,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
                registerOnClick(4) { onNavigateToAbout() }

                // ── 底部 返回 ──
                DpadFocusable(
                    index = 5,
                    isFocused = focusIndex == 5,
                    onClick = { onBack() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeoBlack)
                        .padding(vertical = 14.dp),
                ) {
                    Text(
                        text = "\u8FD4\u56DE",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeoGreenFF,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                registerOnClick(5) { onBack() }
            }
        }
    }
}
