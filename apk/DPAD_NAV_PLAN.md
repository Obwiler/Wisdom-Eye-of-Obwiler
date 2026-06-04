# 奥贝之眼 APK — DPAD 导航重构任务计划书

**项目路径**: `apk/`
**目标**: 眼镜端全界面 DPAD 导航 + 可见绿色焦点框 + PC D-Pad 完全可控
**版本**: v0.2.0
**父文档**: 旧版 v0 已归档至 `docs/archived/DPAD_NAV_PLAN_v0.md`

---

## 问题诊断

当前 APK 五个页面全部使用 Compose `Modifier.clickable`，无一使用 `focusable`。
`MainActivity.dispatchKeyEvent` 仅拦截 BACK（全局导航）和 CENTER（Camera 页快门），方向键 UP/DOWN 经 `super.dispatchKeyEvent` 转发至 Compose，但 Compose 焦点树为空，方向键直接丢失。

PC 端 D-Pad 通过 ADB broadcast 发送 `KEY_UP` / `KEY_DOWN` / `KEY_ENTER` / `KEY_BACK`，
`MainActivity.keyReceiver` 将其转为 `KeyEvent` 再 `dispatchKeyEvent`。
方向键在 Camera 以外页面无响应，属于设计级缺口。

---

## 设计决策

### 焦点架构

不使用 Compose 内置 FocusManager 自动焦点遍历（在不同容器布局间行为不一致）。
改为容器级手动焦点管理：

- **`DpadFocusContainer`**：`Box` + `Modifier.focusable()` + `Modifier.onKeyEvent`。
  拦截 DPAD_UP / DPAD_DOWN / DPAD_CENTER / ENTER，维护 `focusIndex` 状态。
  维护 `Map<Int, () -> Unit>` 的 onClick 注册表，CENTER 触发当前焦点项的 onClick。
  每屏一个实例，是焦点管理的唯一入口。

- **`DpadFocusable`**：子项包装器。接收 `isFocused: Boolean`，聚焦时叠加 2dp 纯绿边框。
  保留 `Modifier.clickable` 确保触控不受影响。`onClick` 通过注册表注册到容器。

### 关键约束

- 仅竖向导航（眼镜 480×640 竖屏，DPAD_LEFT/RIGHT 不处理）
- 焦点循环（UP 到头跳末尾，DOWN 到尾跳开头）
- Settings/Result 滚动页面：容器注入 `ScrollState`，焦点切换时 `animateScrollTo` 滚动到可见位置
- 触控 clickable 行为与 DPAD 聚焦完全正交，互不干扰

---

## 任务拆解

### T1: 新建焦点基础设施

- **新增**: `ui/focus/DpadFocusable.kt`
  - `Modifier.weoFocusBorder(focused: Boolean)` — 聚焦态 2dp 纯绿外框，圆角 8dp
  - `DpadFocusContainer(itemCount, initialFocusIndex, scrollState?, modifier, content)` —
    焦点容器，`Box` + `focusable()` + `onKeyEvent`，管理 focusIndex + onClick 注册表
  - `DpadFocusable(index, isFocused, onClick?, modifier, content)` —
    子项包装器，`Box` + 条件边框 + `clickable`

### T2: 主题扩展

- **文件**: `ui/theme/Theme.kt`
  - 新增 `WeoFocusBorder = Color(0xFF00FF00)` — 焦点框纯绿
  - 新增 `WeoFocusGlow = Color(0x3300FF00)` — 聚焦背景微光（备用）

### T3: 改造 HomeScreen

- **文件**: `ui/screen/HomeScreen.kt`
  - 三个按钮 "拍照识图" / "设置" / "退出" 从 `Box(clickable)` 改为 `DpadFocusable`
  - 包入 `DpadFocusContainer(itemCount = 3, initialFocusIndex = 0)`
  - 初始焦点 0 = "拍照识图"

### T4: 改造 CameraScreen

- **文件**: `ui/screen/CameraScreen.kt`
  - 底部 "快门" / "返回" 两个按钮改为 `DpadFocusable`
  - 包入 `DpadFocusContainer(itemCount = 2, initialFocusIndex = 0)`
  - 初始焦点 0 = "快门"
  - 预览画面、WEO 角标不参与焦点

### T5: 改造 SettingsScreen

- **文件**: `ui/screen/SettingsScreen.kt`
  - "API 地址" / "API Key" / "模型" / "网络状态" → 只读 `DpadFocusable`（无 onClick，仅展示焦点框）
  - "关于" / "返回" → `DpadFocusable` 带 onClick
  - 全包入 `DpadFocusContainer(itemCount = 6, initialFocusIndex = 0, scrollState)`
  - 焦点切换时自动滚动

### T6: 改造 ResultScreen

- **文件**: `ui/screen/ResultScreen.kt`
  - 底部 "返回" → `DpadFocusable` 带 onClick
  - 步骤文本项 → 只读 `DpadFocusable`（可聚焦滚动，不响应 CENTER）
  - itemCount = steps.size + 1（返回按钮），initialFocusIndex = steps.size
  - 包入 `DpadFocusContainer` + `scrollState`

### T7: 改造 AboutScreen

- **文件**: `ui/screen/AboutScreen.kt`
  - 底部 "返回" → `DpadFocusable` 带 onClick
  - 包入 `DpadFocusContainer(itemCount = 1, initialFocusIndex = 0)`

### T8: 清理 MainActivity

- **文件**: `MainActivity.kt`
  - `dispatchKeyEvent`: 删除 `KEYCODE_DPAD_CENTER` / `KEYCODE_ENTER` 的 Camera 快门硬编码；
    方向键不再拦截；仅保留 BACK 的 `goBack()` 导航逻辑
  - `keyReceiver`: 删除 `KEY_UP` / `KEY_DOWN` / `KEY_ENTER` / `KEY_BACK` 四个 action；
    仅保留 `CONFIG_UPDATED`
  - `onResume` IntentFilter: 对应裁剪

### T9: 编译安装验证

- `./gradlew assembleDebug` 编译通过
- 安装至 RG 眼镜，PC D-Pad 逐屏验证 Home → Camera → Settings → About → Result 焦点移动、点击、滚动
- 触控点击所有按钮功能不受影响

---

## 改动统计

| 类型 | 文件数 | 说明 |
|---|---|---|
| 新增 | 1 | `ui/focus/DpadFocusable.kt` |
| 修改 | 7 | `Theme.kt` + 5 Screen + `MainActivity.kt` |
| 净增代码 | ~180 行 | |

---

## 验收标准

1. Home 页：↑↓ 在 "拍照识图" / "设置" / "退出" 间循环移动绿色焦点框，CENTER 触发导航
2. Camera 页：↑↓ 在 "快门" / "返回" 间切换，CENTER 在快门上触发拍照
3. Settings 页：↑↓ 遍历全部 6 个配置行和按钮，CENTER 在 "关于" 上打开关于页，列表随焦点自动滚动
4. Result 页：↑↓ 遍历步骤文本和 "返回" 按钮，CENTER 点 "返回" 回到上一屏，列表随焦点滚动
5. About 页：CENTER 点 "返回" 回到设置页
6. 任意页按 BACK 返回上一级，Home 页按 BACK 退出应用
7. 触控点击所有按钮功能不受影响
8. `assembleDebug` 编译无报错
