# 奥贝之眼 APK — DPAD 导航重构任务计划书

**项目路径**: `apk/`
**目标**: 眼镜端全焦面 DPAD 导航 + 可见焦点框 + PC D-Pad 完全可控
**版本**: v0.2.0

---

## 设计目标

眼镜端每屏的可交互元素形成纵向焦点列表。PC 端 D-Pad ↑↓ 移动焦点，CENTER 触发点击，BACK 返回导航。聚焦元素显示 2dp 绿色外框——用户在 PC 端操作时，眼镜屏幕所见即所得。

以 HomeScreen 为例：

```
┌──────────────────────────────┐
│                              │
│        奥贝之眼               │
│     WEO · Wisdom Eye        │
│                              │
│   ┏━━━━━━━━━━━━━━━━━━━┓     │
│   ┃   拍照识图          ┃     │  ← 绿色焦点框 (当前聚焦)
│   ┗━━━━━━━━━━━━━━━━━━━┛     │
│                              │
│   ┌───────────────────┐     │
│   │   设置              │     │  ← 无框 (未聚焦)
│   └───────────────────┘     │
│                              │
│         退出                  │  ← 无框 (未聚焦)
│                              │
└──────────────────────────────┘
```

**核心原则**:
- 焦点管理由 Compose 内置 `FocusManager` + `FocusRequester` 驱动，不自定义键盘事件路由
- DPAD 仅竖向导航（眼镜 480×640 竖屏），不处理横向
- `clickable` 与 `focusable` 完全等效——触控和 DPAD 是同一交互的两条路径
- 滚动列表（设置/结果页）内聚焦项自动滚动到可见区域
- Activity 层仅保留 BACK 导航拦截，其余按键全部下发给 Compose 焦点系统

---

## 任务拆解

### T1: 新建焦点基础设施

- **新增**: `ui/focus/DpadFocusable.kt`
  - `DpadFocusable` — 单元素包装：`Box` + `focusable()` + `clickable`，聚焦态附加绿色外框
  - `DpadFocusColumn` — 纵向焦点容器：维护 N 个 `FocusRequester`，拦截 DPAD_UP/DOWN 移动焦点，CENTER 触发当前项 onClick
  - `Modifier.weoFocusBorder(focused)` — 聚焦态 2dp 绿色外框修饰器
  - 不依赖第三方库，仅用 Compose 内置 `FocusRequester` / `onKeyEvent`

### T2: 主题扩展

- **文件**: `ui/theme/Theme.kt` — 新增颜色常量
  - `WeoFocusBorder = Color(0xFF00FF00)` — 焦点框色，不透明纯绿
  - `WeoFocusGlow = Color(0x3300FF00)` — 聚焦背景微光

### T3: 改造 HomeScreen

- **文件**: `ui/screen/HomeScreen.kt`
  - "拍照识图" / "设置" / "退出" 三个按钮从 `Box(clickable = {})` → `DpadFocusable(onClick = {})`
  - 三个项统一包在 `DpadFocusColumn` 内
  - 初始焦点 "拍照识图"

### T4: 改造 CameraScreen

- **文件**: `ui/screen/CameraScreen.kt`
  - 底部 "快门" / "返回" 改为 `DpadFocusable`，包入 `DpadFocusColumn`
  - 初始焦点 "快门"
  - 预览画面不参与焦点列表

### T5: 改造 SettingsScreen

- **文件**: `ui/screen/SettingsScreen.kt`
  - API 地址 / Key / 模型 / 网络状态 四个只读行 → 可聚焦但不可点击（`DpadFocusable` 无 onClick）
  - "关于" / "返回" 按钮 → `DpadFocusable` 带 onClick
  - 全包入 `DpadFocusColumn`
  - 聚焦项自动 `animateScrollToItem`
  - 初始焦点 "API 地址"

### T6: 改造 ResultScreen

- **文件**: `ui/screen/ResultScreen.kt`
  - 底部 "返回" 按钮 → `DpadFocusable`
  - 步骤文本项 → 只读 `DpadFocusable`（可聚焦滚动，不响应 CENTER）
  - 初始焦点 "返回"

### T7: 改造 AboutScreen

- **文件**: `ui/screen/AboutScreen.kt`
  - 底部 "返回" 按钮 → `DpadFocusable`
  - 初始焦点 "返回"

### T8: 清理 MainActivity 键盘事件

- **文件**: `MainActivity.kt`
  - `dispatchKeyEvent`: 删除 Camera 页硬编码的 CENTER→拍照逻辑，方向键不再拦截直接交给 Compose
  - BACK 键保持 `goBack()`，Home 页 BACK 由系统处理
  - `keyReceiver`: 删除 `KEY_UP/DOWN/ENTER/BACK` 四个广播 action，仅保留 `CONFIG_UPDATED`

### T9: 编译安装验证

- **验证**: `./gradlew assembleDebug` 编译通过
- **验证**: 安装至 RG 眼镜，PC 端 D-Pad 逐屏测试 Home → Camera → Result → Settings → About 焦点移动与点击
- **验证**: 触控操作不受影响

---

## 改动统计

| 类型 | 文件数 | 说明 |
|---|---|---|
| 新增 | 1 | `ui/focus/DpadFocusable.kt` |
| 修改 | 7 | `Theme.kt` + 5 Screen + `MainActivity.kt` |
| 净增代码 | ~200 行 | |

---

## 验收标准

1. Home 页：↑↓ 在 "拍照识图" / "设置" / "退出" 间移动绿色焦点框，CENTER 触发导航
2. Camera 页：↑↓ 在 "快门" / "返回" 间切换，CENTER 在快门上触发拍照
3. Settings 页：↑↓ 遍历全部配置行和按钮，CENTER 在 "关于" 上打开关于页，列表随焦点自动滚动
4. Result 页：CENTER 点 "返回" 回到上一屏
5. About 页：CENTER 点 "返回" 回到设置页
6. 任意页按 BACK 返回上一级，Home 页按 BACK 退出应用
7. 触控点击所有按钮功能不受影响
8. `assembleDebug` 编译无报错
