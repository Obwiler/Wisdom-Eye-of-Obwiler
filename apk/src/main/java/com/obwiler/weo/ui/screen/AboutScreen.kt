package com.obwiler.weo.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.ui.focus.DpadFocusContainer
import com.obwiler.weo.ui.focus.DpadFocusable
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreenCC
import com.obwiler.weo.ui.theme.WeoGreenFF
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreen66
import com.obwiler.weo.BuildConfig

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\u5965\u8D1D\u4E4B\u773C",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = WeoGreenFF,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Wisdom Eye of Obwiler",
                fontSize = 15.sp,
                color = WeoGreen99,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "WEO v${BuildConfig.VERSION_NAME}",
                fontSize = 17.sp,
                color = WeoGreenFF,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            )

            Text(
                text = "MicroLED 480\u00D7640 \u7EFF\u8272\u5355\u8272\u5C4F \u00B7 Camera2 API \u00B7 AI \u89C6\u89C9\u8BC6\u522B",
                fontSize = 14.sp,
                color = WeoGreen99,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, WeoGreen66, RoundedCornerShape(8.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "\u672C\u5E94\u7528\u4F1A\u62CD\u6444\u7167\u7247\u5E76\u53D1\u9001\u81F3\u60A8\u914D\u7F6E\u7684\u7B2C\u4E09\u65B9 AI \u670D\u52A1\u5546\u8FDB\u884C\u5206\u6790\u3002\u8BF7\u5728\u4F7F\u7528\u524D\u9605\u8BFB\u9690\u79C1\u653F\u7B56\u3002",
                    fontSize = 14.sp,
                    color = WeoGreenCC,
                    lineHeight = 22.sp
                )
            }
        }

        // ── 底部 DPAD 焦点：返回 ──
        DpadFocusContainer(
            itemCount = 1,
            initialFocusIndex = 0,
        ) { focusIndex, registerOnClick ->

            DpadFocusable(
                index = 0,
                isFocused = focusIndex == 0,
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
            registerOnClick(0) { onBack() }
        }
    }
}
