package com.obwiler.weo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.ui.Screen
import com.obwiler.weo.ui.focus.DpadFocusContainer
import com.obwiler.weo.ui.focus.DpadFocusable
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreenFF
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreen66
import com.obwiler.weo.ui.theme.WeoGreen1A

@Composable
fun HomeScreen(
    onNavigate: (Screen) -> Unit,
    onExit: () -> Unit,
    serverInfo: String? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        // 主内容 — 居中
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "\u5965\u8D1D\u4E4B\u773C",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = WeoGreenFF,
                textAlign = TextAlign.Center
            )

            Text(
                text = "WEO \u00B7 Wisdom Eye of Obwiler",
                fontSize = 15.sp,
                color = WeoGreen99,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 36.dp)
            )

            DpadFocusContainer(
                itemCount = 4,
                initialFocusIndex = 0,
            ) { focusIndex, registerOnClick ->

                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    DpadFocusable(
                        index = 0,
                        isFocused = focusIndex == 0,
                        onClick = { onNavigate(Screen.Camera) },
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(WeoGreen1A, RoundedCornerShape(10.dp))
                            .padding(vertical = 16.dp),
                    ) {
                        Text(
                            text = "\u62CD\u7167\u8BC6\u56FE",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeoGreenFF,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    registerOnClick(0) { onNavigate(Screen.Camera) }

                    DpadFocusable(
                        index = 1,
                        isFocused = focusIndex == 1,
                        onClick = { onNavigate(Screen.Settings) },
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(WeoGreen1A, RoundedCornerShape(10.dp))
                            .padding(vertical = 16.dp),
                    ) {
                        Text(
                            text = "\u8BBE\u7F6E",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeoGreenFF,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    registerOnClick(1) { onNavigate(Screen.Settings) }

                    DpadFocusable(
                        index = 2,
                        isFocused = focusIndex == 2,
                        onClick = { onNavigate(Screen.Wifi) },
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .background(WeoGreen1A, RoundedCornerShape(10.dp))
                            .padding(vertical = 16.dp),
                    ) {
                        Text(
                            text = "WiFi",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeoGreenFF,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    registerOnClick(2) { onNavigate(Screen.Wifi) }

                    DpadFocusable(
                        index = 3,
                        isFocused = focusIndex == 3,
                        onClick = { onExit() },
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .fillMaxWidth()
                            .padding(top = 32.dp, bottom = 6.dp)
                            .padding(vertical = 14.dp),
                    ) {
                        Text(
                            text = "\u9000\u51FA",
                            fontSize = 18.sp,
                            color = WeoGreen66,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    registerOnClick(3) { onExit() }
                }
            }
        }

        // 左下角网络状态指示器
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态灯
            val isOnline = !serverInfo.isNullOrBlank()
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) WeoGreenFF else WeoGreen66)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isOnline) serverInfo!! else "\u79BB\u7EBF",
                fontSize = 12.sp,
                color = if (isOnline) WeoGreen99 else WeoGreen66,
                fontWeight = if (isOnline) FontWeight.Normal else FontWeight.Normal,
            )
        }
    }
}
