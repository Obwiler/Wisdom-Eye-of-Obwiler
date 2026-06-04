package com.obwiler.weo.ui.focus

import android.view.KeyEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.obwiler.weo.ui.theme.WeoFocusBorder

/** 2dp 纯绿焦点外框修饰器，圆角 8dp。聚焦时启用，否则无效果。 */
fun Modifier.weoFocusBorder(focused: Boolean): Modifier =
    if (focused) this.border(2.dp, WeoFocusBorder, RoundedCornerShape(8.dp)) else this

/**
 * DPAD 焦点容器。包裹整屏可聚焦内容，拦截 DPAD 方向键与 CENTER。
 *
 * @param itemCount  可聚焦项总数，用于焦点循环
 * @param initialFocusIndex  初始焦点索引，默认 0
 * @param scrollState  可选，提供时焦点切换自动 animateScrollTo 到预估位置
 * @param itemHeightDp  每项预估高度 (dp)，用于滚动位置计算，默认 60
 * @param content  内容 lambda，接收 [focusIndex] 和 [registerOnClick] 注册函数
 */
@Composable
fun DpadFocusContainer(
    itemCount: Int,
    initialFocusIndex: Int = 0,
    scrollState: ScrollState? = null,
    itemHeightDp: Int = 60,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(focusIndex: Int, registerOnClick: (Int, () -> Unit) -> Unit) -> Unit,
) {
    var focusIndex by remember { mutableStateOf(initialFocusIndex) }
    val clickActions = remember { mutableMapOf<Int, () -> Unit>() }
    val focusRequester = remember { FocusRequester() }

    // 焦点索引变化时自动滚动
    if (scrollState != null) {
        val density = LocalDensity.current
        LaunchedEffect(focusIndex) {
            val targetPx = with(density) { (focusIndex * itemHeightDp).dp.toPx() }.toInt()
            scrollState.animateScrollTo(targetPx.coerceIn(0, scrollState.maxValue))
        }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (itemCount > 0) focusIndex = ((focusIndex - 1) + itemCount) % itemCount
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (itemCount > 0) focusIndex = (focusIndex + 1) % itemCount
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        clickActions[focusIndex]?.invoke()
                        true
                    }
                    else -> false
                }
            }
    ) {
        content(focusIndex) { index, action ->
            clickActions[index] = action
        }
    }

    // 挂载后立即请求焦点，确保接收 DPAD 事件
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

/**
 * DPAD 可聚焦子项。聚焦时叠加绿色外框，触控仍可点击。
 *
 * @param index      此项在焦点列表中的序号
 * @param isFocused  当前是否聚焦
 * @param onClick    点击回调（触控或 DPAD CENTER 均可触发）
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun DpadFocusable(
    index: Int,
    isFocused: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .weoFocusBorder(isFocused)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        content = content,
    )
}