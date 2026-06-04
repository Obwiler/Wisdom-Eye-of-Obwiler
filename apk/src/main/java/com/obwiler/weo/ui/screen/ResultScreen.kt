package com.obwiler.weo.ui.screen

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.ai.AiResult
import com.obwiler.weo.ui.focus.DpadFocusContainer
import com.obwiler.weo.ui.focus.DpadFocusable
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreenCC
import com.obwiler.weo.ui.theme.WeoGreenFF
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreen66

@Composable
fun ResultScreen(
    result: AiResult,
    photoPath: String?,
    onBack: () -> Unit,
) {
    val hasError = result.error != null
    val scrollState = rememberScrollState()
    val stepCount = result.steps.size
    val itemCount = stepCount + 1  // steps + 返回按钮

    val photoBitmap = remember(photoPath) {
        photoPath?.let {
            try { BitmapFactory.decodeFile(it)?.asImageBitmap() } catch (_: Exception) { null }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        DpadFocusContainer(
            itemCount = itemCount,
            initialFocusIndex = stepCount,
            scrollState = scrollState,
            itemHeightDp = 28,
            modifier = Modifier.weight(1f),
        ) { focusIndex, registerOnClick ->

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (photoBitmap != null) {
                    Image(
                        bitmap = photoBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 120.dp, height = 90.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (hasError) {
                    // 错误页 —— 最亮绿色吸引注意
                    Text(
                        text = "\u5206\u6790\u5931\u8D25",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeoGreenFF,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = result.error ?: "",
                        fontSize = 16.sp,
                        color = WeoGreenCC,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                    if (result.errorCategory != null) {
                        Text(
                            text = "\u7C7B\u578B: ${result.errorCategory.name}",
                            fontSize = 14.sp,
                            color = WeoGreen99,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    // 答案正文 —— 高亮但不刺眼
                    if (result.answer.isNotBlank()) {
                        Text(
                            text = result.answer,
                            fontSize = 17.sp,
                            color = WeoGreenCC,
                            lineHeight = 26.sp,
                        )
                    }

                    if (result.steps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "\u5206\u6790\u6B65\u9AA4",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeoGreenFF,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                        )

                        result.steps.forEachIndexed { index, step ->
                            DpadFocusable(
                                index = index,
                                isFocused = focusIndex == index,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                            ) {
                                Text(
                                    text = "${index + 1}. $step",
                                    fontSize = 14.sp,
                                    color = WeoGreen99,
                                    lineHeight = 22.sp,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // ── 底部 返回 ──
            DpadFocusable(
                index = stepCount,
                isFocused = focusIndex == stepCount,
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
            registerOnClick(stepCount) { onBack() }
        }
    }
}
