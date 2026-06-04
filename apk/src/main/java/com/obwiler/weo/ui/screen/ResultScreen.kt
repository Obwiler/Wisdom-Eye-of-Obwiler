package com.obwiler.weo.ui.screen

import android.graphics.BitmapFactory
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.ai.AiResult
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreen1A
import com.obwiler.weo.ui.theme.WeoGreen33
import com.obwiler.weo.ui.theme.WeoGreen66
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreenCC
import com.obwiler.weo.ui.theme.WeoGreenFF
import kotlin.math.ceil
import kotlinx.coroutines.launch

/** Estimated max chars per screen line at 17sp on the glasses display. */
private const val CHARS_PER_LINE = 24

/** Show a page of the answer text, with DPAD UP/DOWN to scroll and CENTER to go back. */
@Composable
fun ResultScreen(
    result: AiResult,
    photoPath: String?,
    onRetake: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val hasError = result.error != null
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    val photoBitmap = remember(photoPath) {
        photoPath?.let {
            try { BitmapFactory.decodeFile(it)?.asImageBitmap() } catch (_: Exception) { null }
        }
    }

    // Calculate page height in pixels for DPAD page-up/down
    val pageHeightPx = with(density) { 320.dp.toPx() }.toInt()

    // Request focus so this Column receives DPAD events
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WeoBlack)
    ) {
        // Scrollable content area ? receives DPAD UP/DOWN for scrolling
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            scope.launch {
                                val target = (scrollState.value - pageHeightPx).coerceAtLeast(0)
                                scrollState.scrollTo(target)
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            scope.launch {
                                val target = (scrollState.value + pageHeightPx)
                                    .coerceAtMost(scrollState.maxValue)
                                scrollState.scrollTo(target)
                            }
                            true
                        }
                        else -> false
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Scroll indicator (top)
            if (scrollState.value > 0) {
                Text(
                    text = "▲ 上滑查看更多",
                    fontSize = 10.sp,
                    color = WeoGreen66,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

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
                Text(
                    text = "分析失败",
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
                        text = "类型: ${result.errorCategory.name}",
                        fontSize = 14.sp,
                        color = WeoGreen99,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
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
                        text = "分析步骤",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeoGreenFF,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )

                    result.steps.forEachIndexed { index, step ->
                        Text(
                            text = "${index + 1}. $step",
                            fontSize = 14.sp,
                            color = WeoGreen99,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }

            // Scroll indicator (bottom)
            if (scrollState.value < scrollState.maxValue) {
                Text(
                    text = "▼ 下滑查看更多",
                    fontSize = 10.sp,
                    color = WeoGreen66,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Bottom "??" bar ? CENTER to go back
        Box(
            modifier = Modifier
                .fillMaxWidth()
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

        // Separate focusable row for CENTER = back
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                         event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)) {
                        onBack()
                        true
                    } else false
                }
        )
    }
}
