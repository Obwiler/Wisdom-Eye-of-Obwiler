# DPad WiFi 密码输入方案调研

> 日期: 2026-06-04
> 目标: 为 RG_glasses (480×640 MicroLED, 仅 dpad/戒指 输入) 设计可用的 WiFi 密码输入方案
> 原则: 不依赖触摸屏、不依赖语音输入、不依赖外接键盘

---

## 一、设备输入能力

| 输入设备 | 可用按键 | 适用操作 |
|---------|---------|---------|
| 镜腿触摸条 | UP/DOWN/LEFT/RIGHT/ENTER/BACK | 方向导航 + 确认/返回 |
| RGR06 戒指 | 同上 + 触摸板 (4096×4096) | dpad 导航 + 手势（可后续扩展） |

结论：只有 **5 向 dpad**（四方向 + 确认）可用，无触摸，无键盘。

---

## 二、GitHub 参考方案

### 2.1 Steam Daisywheel 风格
- **仓库**: [Briaoeuidhtns/android-daisywheel](https://github.com/Briaoeuidhtns/android-daisywheel)
- **语言**: Kotlin
- **原理**: Steam Controller 的"菊花轮"键盘——字符排列成圆环，摇杆指向某个方向即选中该扇区的字符
- **对本项目的参考价值**: 高。可改编为 dpad 网格版本：UP/DOWN 换行，LEFT/RIGHT 选字，ENTER 确认

### 2.2 Keytty — 游戏手柄键盘
- **仓库**: [kalugny/Keytty](https://github.com/kalugny/Keytty)
- **语言**: Java
- **原理**: 将游戏手柄按键映射为键盘输入，用组合键切换字符集
- **对本项目的参考价值**: 中。字符集切换思路可借鉴（长按 dpad 方向切换字母/数字/符号模式）

### 2.3 Android TV Leanback IME
- **来源**: AOSP 内置
- **原理**: 标准 TV 遥控器输入法，字符网格 + dpad 导航
- **对本项目的参考价值**: 低。Android TV IME 需要系统级集成，WEO 是普通 App 无法注册为系统 IME，且界面复杂不适合 480×640 小屏

---

## 三、推荐方案：DPad 字符选择键盘

### 3.1 核心交互

```
     [←] [→]  — 水平移动焦点
     [↑] [↓]  — 垂直换行
     [ENTER]  — 输入选中字符
     [BACK]   — 删除最后一个字符
```

### 3.2 布局设计（适配 480×640 单色屏）

```
┌──────────────────────────────┐
│  WiFi: MyNetwork             │  ← 当前选中的 SSID
│  ┌──────────────────────────┐│
│  │ ●●●●●●●●                 ││  ← 密码输入框（已输入用圆点显示）
│  └──────────────────────────┘│
│                              │
│  1  2  3  4  5  6  7  8  9  0│  ← 数字行
│  Q  W  E  R  T  Y  U  I  O  P│  ← 大写字母行
│  A  S  D  F  G  H  J  K  L  │  ← 大写字母行（续）
│  Z  X  C  V  B  N  M        │  ← 大写字母行（续）
│  aA  @  .  -  _  [SP]  [OK] │  ← 控制行（切换大小写/符号/空格/确认）
└──────────────────────────────┘
```

设计要点：
- 每个字符占位 36-40dp，一行约 10 个字符，正好填满 480px 宽度
- 高亮选中的字符用绿色反色（WeoGreenFF 背景 + WeoBlack 文字）
- 输入框实时显示：密码用圆点，最后一个字符短暂明文显示 500ms 后变圆点
- 控制行包含：大小写切换、符号切换、空格、连接

### 3.3 字符集切换

| 模式 | 显示内容 | 切换方式 |
|------|---------|---------|
| 大写字母 | A-Z | 默认 |
| 小写字母 | a-z | 按 `aA` 按钮 |
| 数字 | 0-9 | 按 `#+` 按钮或自动（密码末尾字符决定） |
| 符号 | @ . - _ / : + = ! ? % & * ( ) | 按 `@&` 按钮 |

### 3.4 备选方案：PC 端配置

对于完全不想在眼镜上输入的场景，通过 PC 工具（ADB push）配置：

```json
{
  "wifiSsid": "MyNetwork",
  "wifiPassword": "mypassword"
}
```

写入 `/sdcard/Android/data/com.obwiler.weo/files/wifi_config.json`，APK 的 ConfigHolder FileObserver 自动检测更新。

---

## 四、实现计划

| 步骤 | 内容 | 优先级 |
|------|------|--------|
| 1 | 创建 `ui/component/DpadKeyboard.kt` — 可复用的 dpad 字符选择组件 | P0 |
| 2 | 修改 `WifiScreen.kt` — 集成 DPadKeyboard 替换原生 TextField | P0 |
| 3 | PC 端 ADB push WiFi 配置支持 | P1 |
| 4 | 可选：快速连接模式（扫描二维码） | P2 |

---

## 五、参考链接

- Steam Daisywheel IME: https://github.com/Briaoeuidhtns/android-daisywheel
- Keytty gamepad keyboard: https://github.com/kalugny/Keytty
- Android TV Leanback: https://developer.android.com/training/tv/start
- WEO 设备硬件规格: `docs/hardware/RG_glasses_spec.md`
