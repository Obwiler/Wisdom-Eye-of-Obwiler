package com.obwiler.weo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 绿色单色亮度阶梯 (MicroLED 480x640 单色屏专用) ──
// 屏幕仅能显示绿色通道亮度，无红/蓝色素。
// 用显式 green 值替代 alpha 合成，确保驱动层映射一致性。

val WeoGreenFF = Color(0xFF00FF00)   // 100%  — 标题 / 聚焦 / 错误强调
val WeoGreenCC = Color(0xFF00CC00)   // ~80%  — 正文 / 高亮信息
val WeoGreen99 = Color(0xFF009900)   // ~60%  — 辅助文本 / 状态标签
val WeoGreen66 = Color(0xFF006600)   // ~40%  — 次级标签 / 聚焦外边框
val WeoGreen33 = Color(0xFF003300)   // ~20%  — 不可用 / 占位 / 分割提示
val WeoGreen1A = Color(0xFF001A00)   // ~10%  — 卡片底色 (极淡绿)
val WeoBlack   = Color(0xFF000000)    // OLED 纯黑 (像素关闭)

val WeoFocusBorder = WeoGreenFF
val WeoFocusGlow   = WeoGreen33

// 别名字段，兼容旧引用
// (逐步迁移后可删除)
@Deprecated("Use WeoGreenFF", ReplaceWith("WeoGreenFF"))
val WeoGreen = WeoGreenFF
@Deprecated("Use WeoGreen99", ReplaceWith("WeoGreen99"))
val WeoGreenAlpha = WeoGreen99
@Deprecated("Use WeoBlack", ReplaceWith("WeoBlack"))
val WeoDark = WeoBlack

// ── Material3 配色 ──
private val WeoColorScheme = darkColorScheme(
    primary = WeoGreenFF,
    background = WeoBlack,
    surface = Color(0xFF000000),      // 纯黑 surface，不发光
    onPrimary = WeoBlack,
    onBackground = WeoGreenFF,
    onSurface = WeoGreenCC,
)

@Composable
fun WeoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WeoColorScheme, content = content)
}
