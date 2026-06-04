package com.obwiler.weo.ui.component

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obwiler.weo.ui.theme.WeoBlack
import com.obwiler.weo.ui.theme.WeoGreen1A
import com.obwiler.weo.ui.theme.WeoGreen33
import com.obwiler.weo.ui.theme.WeoGreen66
import com.obwiler.weo.ui.theme.WeoGreen99
import com.obwiler.weo.ui.theme.WeoGreenCC
import com.obwiler.weo.ui.theme.WeoGreenFF

/**
 * 为 dpad 设备设计的字符选择键盘。
 *
 * 操作：
 *   UP/DOWN     — 切换行
 *   LEFT/RIGHT  — 切换列
 *   ENTER       — 输入当前字符
 *   BACK        — 删除上一个字符
 *
 * @param onTextChanged  密码文本变化回调
 * @param onConfirm      用户按下 [OK] 按钮时的回调
 */
@Composable
fun DpadKeyboard(
    onTextChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var password by remember { mutableStateOf("") }
    var row by remember { mutableIntStateOf(0) }
    var col by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf(KeyboardMode.UPPER) }

    // Current layout
    val rows = remember(mode) { mode.layout() }

    LaunchedEffect(mode) {
        if (row >= rows.size) row = rows.size - 1
        val currentRowLen = rows.getOrNull(row)?.size ?: 1
        if (col >= currentRowLen) col = currentRowLen - 1
    }

    fun inputChar(c: Char) {
        password = password + c
        onTextChanged(password)
    }

    fun deleteLast() {
        if (password.isNotEmpty()) {
            password = password.dropLast(1)
            onTextChanged(password)
        }
    }

    Column(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        row = ((row - 1) + rows.size) % rows.size
                        val currentRowLen = rows[row].size
                        if (col >= currentRowLen) col = currentRowLen - 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        row = (row + 1) % rows.size
                        val currentRowLen = rows[row].size
                        if (col >= currentRowLen) col = currentRowLen - 1
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val currentRowLen = rows[row].size
                        col = ((col - 1) + currentRowLen) % currentRowLen
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val currentRowLen = rows[row].size
                        col = (col + 1) % currentRowLen
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        val key = rows.getOrNull(row)?.getOrNull(col) ?: return@onKeyEvent true
                        when (key) {
                            KeyboardKey.MODE_UPPER -> mode = KeyboardMode.UPPER
                            KeyboardKey.MODE_LOWER -> mode = KeyboardMode.LOWER
                            KeyboardKey.MODE_NUM   -> mode = KeyboardMode.NUMBERS
                            KeyboardKey.MODE_SYM   -> mode = KeyboardMode.SYMBOLS
                            KeyboardKey.SPACE      -> inputChar(' ')
                            KeyboardKey.CONFIRM    -> onConfirm()
                            is KeyboardKey.Char    -> inputChar(key.char)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        if (password.isEmpty() && onCancel != null) {
                            onCancel()
                        } else {
                            deleteLast()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DEL -> {
                        deleteLast()
                        true
                    }
                    else -> false
                }
            },
    ) {
        // Password display
        PasswordDisplay(
            password = password,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Keyboard rows
        for ((r, keys) in rows.withIndex()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((c, key) in keys.withIndex()) {
                    val isFocused = r == row && c == col
                    KeyCap(
                        label = key.label(),
                        isFocused = isFocused,
                        isSpecial = key !is KeyboardKey.Char,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun PasswordDisplay(password: String, modifier: Modifier) {
    val display = if (password.isEmpty()) {
        "输入密码"
    } else {
        // Show dots + last char briefly visible
        val masked = "●".repeat(maxOf(0, password.length - 1))
        masked + password.last()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(WeoGreen1A)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = display,
            fontSize = 18.sp,
            color = if (password.isEmpty()) WeoGreen66 else WeoGreenFF,
            fontWeight = if (password.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun KeyCap(
    label: String,
    isFocused: Boolean,
    isSpecial: Boolean,
    modifier: Modifier,
) {
    val bg = if (isFocused) WeoGreenFF else WeoGreen1A
    val fg = if (isFocused) WeoBlack else if (isSpecial) WeoGreen99 else WeoGreenCC
    val borderMod = if (isFocused) Modifier.border(1.5.dp, WeoGreenFF, RoundedCornerShape(4.dp))
                    else Modifier.border(1.dp, WeoGreen33, RoundedCornerShape(4.dp))

    Box(
        modifier = modifier
            .size(width = 34.dp, height = 40.dp)
            .then(borderMod)
            .clip(RoundedCornerShape(4.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (label.length > 2) 9.sp else 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = fg,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Keyboard model ──

sealed class KeyboardKey {
    data class Char(val char: kotlin.Char) : KeyboardKey()
    data object MODE_UPPER : KeyboardKey()
    data object MODE_LOWER : KeyboardKey()
    data object MODE_NUM   : KeyboardKey()
    data object MODE_SYM   : KeyboardKey()
    data object SPACE      : KeyboardKey()
    data object CONFIRM    : KeyboardKey()

    fun label(): String = when (this) {
        is Char       -> char.uppercase()
        MODE_UPPER    -> "aA"
        MODE_LOWER    -> "AA"
        MODE_NUM      -> "#+"
        MODE_SYM      -> "@&"
        SPACE         -> "SP"
        CONFIRM       -> "OK"
    }
}

enum class KeyboardMode {
    UPPER, LOWER, NUMBERS, SYMBOLS;

    fun layout(): List<List<KeyboardKey>> = when (this) {
        UPPER -> listOf(
            listOf('1','2','3','4','5','6','7','8','9','0').map { KeyboardKey.Char(it) },
            listOf('Q','W','E','R','T','Y','U','I','O','P').map { KeyboardKey.Char(it) },
            listOf('A','S','D','F','G','H','J','K','L').map { KeyboardKey.Char(it) },
            listOf('Z','X','C','V','B','N','M').map { KeyboardKey.Char(it) },
            listOf(KeyboardKey.MODE_LOWER, KeyboardKey.MODE_NUM, KeyboardKey.MODE_SYM, KeyboardKey.SPACE, KeyboardKey.CONFIRM),
        )
        LOWER -> listOf(
            listOf('1','2','3','4','5','6','7','8','9','0').map { KeyboardKey.Char(it) },
            listOf('q','w','e','r','t','y','u','i','o','p').map { KeyboardKey.Char(it) },
            listOf('a','s','d','f','g','h','j','k','l').map { KeyboardKey.Char(it) },
            listOf('z','x','c','v','b','n','m').map { KeyboardKey.Char(it) },
            listOf(KeyboardKey.MODE_UPPER, KeyboardKey.MODE_NUM, KeyboardKey.MODE_SYM, KeyboardKey.SPACE, KeyboardKey.CONFIRM),
        )
        NUMBERS -> listOf(
            listOf('1','2','3','4','5','6','7','8','9','0').map { KeyboardKey.Char(it) },
            listOf(KeyboardKey.MODE_LOWER, KeyboardKey.MODE_SYM, KeyboardKey.SPACE, KeyboardKey.CONFIRM),
        )
        SYMBOLS -> listOf(
            listOf('@','.','-','_','/',':','+','=','!','?').map { KeyboardKey.Char(it) },
            listOf('%','&','*','(',')','[',']','{','}','|').map { KeyboardKey.Char(it) },
            listOf(KeyboardKey.MODE_UPPER, KeyboardKey.MODE_NUM, KeyboardKey.SPACE, KeyboardKey.CONFIRM),
        )
    }
}
