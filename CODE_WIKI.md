# 奥贝之眼 (WEO) · Code Wiki

> **WEO** — Wisdom Eye of Obwiler  
> 版本: v0.1.0 | 最后更新: 2026-06-03

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [目录结构](#3-目录结构)
4. [Android APK 模块详解](#4-android-apk-模块详解)
   - 4.1 应用入口层
   - 4.2 AI 模块 (`ai`)
   - 4.3 相机模块 (`camera`)
   - 4.4 图像处理模块 (`image`)
   - 4.5 传感器模块 (`sensor`)
   - 4.6 配置模块 (`config`)
   - 4.7 事件模块 (`event`)
   - 4.8 UI 模块 (`ui`)
   - 4.9 服务模块 (`service`)
5. [PC 工具模块详解](#5-pc-工具模块详解)
   - 5.1 入口与主窗口
   - 5.2 核心层 (`core`)
   - 5.3 UI 面板 (`ui`)
6. [关键类与函数参考](#6-关键类与函数参考)
7. [数据流与交互时序](#7-数据流与交互时序)
8. [依赖关系](#8-依赖关系)
9. [配置体系](#9-配置体系)
10. [构建与运行](#10-构建与运行)
11. [签名与发布](#11-签名与发布)

---

## 1. 项目概述

**奥贝之眼 (WEO)** 是一款面向 **MicroLED 绿色单色屏智能眼镜 (480×640)** 的 AI 视觉识别应用。系统由两个子项目组成：

| 子项目 | 技术栈 | 说明 |
|--------|--------|------|
| `apk/` | Kotlin + Jetpack Compose + Camera2 | 运行在 Android 12+ 设备上的主应用 |
| `pc_tool/` | Python + PySide6 + ADB + scrcpy | Windows 桌面端管理工具 |

**核心功能流程**：用户通过智能眼镜按键触发拍照 → 图片经图像管线预处理 → 发送至可配置的第三方 AI 视觉 API（兼容 OpenAI Chat Completions 协议） → AI 分析结果展示在单色屏幕上。

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    PC 工具 (PySide6)                     │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌─────────┐ │
│  │ConfigPanel│ │ ApkPanel  │ │ScrcpyPanel│ │RingPanel│ │
│  └─────┬─────┘ └─────┬─────┘ └─────┬─────┘ └────┬────┘ │
│        │             │             │             │       │
│  ┌─────┴─────────────┴─────────────┴─────────────┴────┐ │
│  │            core: AdbBridge / ConfigSync / ApkMgr   │ │
│  └────────────────────────┬───────────────────────────┘ │
└───────────────────────────┼─────────────────────────────┘
                            │ ADB (USB/WiFi)
                            ▼
┌─────────────────────────────────────────────────────────┐
│              Android APK (Kotlin/Compose)                │
│                                                         │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ MainActivity │──│ ImagePipeline│──│  HttpAiClient │  │
│  └──────┬──────┘  └──────────────┘  └───────────────┘  │
│         │                                               │
│  ┌──────┴──────┐  ┌──────────────┐  ┌───────────────┐  │
│  │CameraHolder │  │ ConfigHolder │  │  ImuCollector  │  │
│  │(Camera2 API)│  │(FileObserver)│  │(RotationSensor)│  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │  Jetpack Compose UI: Home → Camera → Result      │   │
│  │                        ↘ Settings → About        │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │  AI 云端 API    │
                   │ (OpenAI 兼容)   │
                   └─────────────────┘
```

**关键设计特点**：

- **接口驱动**：`CameraService`、`AiClient`、`ConfigService`、`ImuProvider` 均定义为接口，实现可替换
- **文件驱动配置**：PC 工具通过 ADB 将 JSON 配置推送至设备存储，Android 端通过 `FileObserver` 实时监听变更
- **事件总线**：`AppEvents` 使用 Kotlin `Channel` 实现跨组件异步通信
- **非交互式硬件适配**：针对智能眼镜无触摸屏的特点，全部通过物理按键（DPAD/Enter/Back）操作

---

## 3. 目录结构

```
Wisdom Eye of Obwiler/
├── apk/                                    # Android 应用项目
│   ├── build.gradle.kts                    # Gradle 构建脚本
│   ├── gradle.properties                   # Gradle 属性
│   ├── proguard-rules.pro                  # R8 混淆规则
│   ├── .gitignore
│   └── src/main/
│       ├── AndroidManifest.xml             # 应用清单
│       ├── java/com/obwiler/weo/
│       │   ├── MainActivity.kt             # 主 Activity (应用入口)
│       │   ├── app/
│       │   │   └── WEOApplication.kt       # Application 类 (全局初始化)
│       │   ├── ai/
│       │   │   ├── AiClient.kt             # AI 客户端接口
│       │   │   ├── AiResult.kt             # AI 结果数据模型
│       │   │   └── HttpAiClient.kt         # HTTP AI 客户端实现
│       │   ├── camera/
│       │   │   ├── CameraService.kt        # 相机服务接口
│       │   │   ├── CameraHolder.kt         # Camera2 实现
│       │   │   └── PreviewTransform.kt     # 预览矩阵变换
│       │   ├── config/
│       │   │   ├── AppConfig.kt            # 配置数据类
│       │   │   ├── ConfigService.kt        # 配置服务接口
│       │   │   └── ConfigHolder.kt         # 配置实现 (FileObserver)
│       │   ├── event/
│       │   │   └── AppEvents.kt            # 全局事件通道
│       │   ├── image/
│       │   │   └── ImagePipeline.kt        # 图像处理管线
│       │   ├── sensor/
│       │   │   └── ImuCollector.kt         # IMU 传感器采集
│       │   ├── service/
│       │   │   └── KeepAliveService.kt     # 前台保活服务
│       │   └── ui/
│       │       ├── Nav.kt                  # 页面路由定义
│       │       ├── theme/
│       │       │   └── Theme.kt            # Material3 主题
│       │       └── screen/
│       │           ├── HomeScreen.kt       # 首页
│       │           ├── CameraScreen.kt     # 相机预览页
│       │           ├── ResultScreen.kt     # AI 结果页
│       │           ├── SettingsScreen.kt   # 设置页
│       │           └── AboutScreen.kt      # 关于页
│       └── res/
│           ├── values/
│           │   ├── strings.xml             # 字符串资源
│           │   └── themes.xml              # XML 主题
│           └── mipmap-*/ic_launcher.png    # 应用图标
│
├── pc_tool/                                # PC 桌面管理工具
│   ├── main.py                             # 程序入口
│   ├── core/
│   │   ├── __init__.py
│   │   ├── constants.py                    # 常量/配置/主题
│   │   ├── adb_bridge.py                   # ADB 命令封装
│   │   ├── config_sync.py                  # 配置推送/拉取
│   │   └── apk_manager.py                  # APK 安装管理
│   └── ui/
│       ├── __init__.py
│       ├── main_window.py                  # 主窗口
│       ├── config_panel.py                 # AI 配置面板
│       ├── apk_panel.py                    # APK 管理面板
│       ├── scrcpy_panel.py                 # scrcpy 投屏面板
│       ├── ring_panel.py                   # 指环按键面板
│       ├── status_panel.py                 # 设备状态栏
│       ├── log_panel.py                    # 日志面板
│       └── poll_worker.py                  # 设备轮询线程
│
└── CODE_WIKI.md                            # 本文档
```

---

## 4. Android APK 模块详解

### 4.1 应用入口层

#### `WEOApplication` — [app/WEOApplication.kt](apk/src/main/java/com/obwiler/weo/app/WEOApplication.kt)

Application 子类，在进程级别初始化全局单例资源：

| 属性 | 类型 | 说明 |
|------|------|------|
| `cameraHolder` | `CameraHolder` | Camera2 相机控制器 |
| `imuCollector` | `ImuProvider` | IMU 旋转传感器采集器 |

生命周期：`onCreate()` 初始化 → `onTerminate()` 释放相机。

#### `MainActivity` — [MainActivity.kt](apk/src/main/java/com/obwiler/weo/MainActivity.kt)

应用唯一的 Activity，承担以下职责：

- **页面路由**：通过 `currentScreenState: MutableState<Screen>` 管理 Compose 页面切换
- **权限请求**：启动时请求 `CAMERA` 和 `READ_EXTERNAL_STORAGE` 权限
- **按键分发**：覆写 `dispatchKeyEvent()` 处理 DPAD 中心键（拍照）和返回键（回首页）
- **广播接收**：注册 `BroadcastReceiver` 接收智能眼镜物理按键事件和配置更新通知
- **拍照流程编排**：`handleShutter()` 方法串联 拍照 → 图像处理 → AI 分析 → 跳转结果页 的完整流程
- **保活服务**：`onResume()` 中启动 `KeepAliveService` 前台服务

**关键方法**：

```kotlin
private suspend fun handleShutter(setScreen: (Screen) -> Unit)
```

流程：`cameraHolder.capture()` → `ImagePipeline.process()` → `savePhoto()` → `aiClient.analyze()` → 跳转 `Screen.Result`

### 4.2 AI 模块 (`ai`)

#### `AiClient` 接口 — [ai/AiClient.kt](apk/src/main/java/com/obwiler/weo/ai/AiClient.kt)

```kotlin
interface AiClient {
    suspend fun analyze(image: ByteArray, config: AppConfig): AiResult
}
```

接受图片字节数组和配置，返回 AI 分析结果。设计为接口便于未来替换实现。

#### `AiResult` 数据类 — [ai/AiResult.kt](apk/src/main/java/com/obwiler/weo/ai/AiResult.kt)

| 字段 | 类型 | 说明 |
|------|------|------|
| `answer` | `String` | AI 返回的主要文本回答 |
| `steps` | `List<String>` | 分析步骤列表 |
| `confidence` | `Float` | 置信度 (当前未使用) |
| `error` | `String?` | 错误信息 |
| `errorCategory` | `ErrorCategory?` | 错误分类枚举 |

`ErrorCategory` 枚举值：`NETWORK`、`AUTH`、`TIMEOUT`、`SERVER`、`UNKNOWN`

#### `HttpAiClient` — [ai/HttpAiClient.kt](apk/src/main/java/com/obwiler/weo/ai/HttpAiClient.kt)

基于 OkHttp 的 AI 客户端实现，兼容 **OpenAI Chat Completions API** 格式。

**核心逻辑**：

1. **前置校验**：检查 `apiBaseUrl` 和 `apiKey` 是否已配置
2. **请求构建**：将图片 Base64 编码后构造多模态消息（`image_url` 类型）
3. **URL 智能拼接**：`buildUrl()` 方法自动补全 `/chat/completions` 后缀
4. **超时控制**：使用 `withTimeout(config.timeoutMs)` 包裹调用
5. **错误分类**：根据 HTTP 状态码和异常类型映射到 `ErrorCategory`
6. **响应解析**：提取 `choices[0].message.content`，截断至 500 字符

**HTTP 客户端配置**：连接超时 10s，读取超时 30s。

### 4.3 相机模块 (`camera`)

#### `CameraService` 接口 — [camera/CameraService.kt](apk/src/main/java/com/obwiler/weo/camera/CameraService.kt)

```kotlin
interface CameraService {
    val previewSize: Size
    val sensorOrientation: Int
    fun bindSurface(surface: SurfaceTexture)
    suspend fun capture(): ByteArray
    suspend fun awaitReady(): Boolean
    fun release()
}
```

#### `CameraHolder` — [camera/CameraHolder.kt](apk/src/main/java/com/obwiler/weo/camera/CameraHolder.kt)

Camera2 API 的完整封装实现，运行在独立的 `HandlerThread` 上。

**关键设计**：

- **相机选择**：`resolveCameraId()` 优先选择后置摄像头
- **预览尺寸**：`readCharacteristics()` 从传感器能力中选取最接近 480×640 的尺寸
- **异步就绪**：使用 `CompletableDeferred<Boolean>` 实现 `awaitReady()` 挂起等待
- **拍照流程**：创建临时 `ImageReader`（JPEG 格式）→ 构建 `STILL_CAPTURE` 请求 → 通过协程挂起等待图片数据
- **资源清理**：`captureReader` 在图片获取后立即关闭

**线程模型**：所有 Camera2 操作均在 `cameraHandler`（后台 HandlerThread）上执行，协程通过 `suspendCancellableCoroutine` 桥接回调与挂起函数。

#### `PreviewTransform` — [camera/PreviewTransform.kt](apk/src/main/java/com/obwiler/weo/camera/PreviewTransform.kt)

工具对象，计算相机预览的变换矩阵：

```kotlin
fun compute(viewW: Int, viewH: Int, bufW: Int, bufH: Int, orientation: Int): Matrix
```

根据传感器方向 (0°/90°/180°/270°) 计算 `Matrix`，使预览画面在 TextureView 中正确旋转和缩放。

### 4.4 图像处理模块 (`image`)

#### `ImagePipeline` — [image/ImagePipeline.kt](apk/src/main/java/com/obwiler/weo/image/ImagePipeline.kt)

核心图像处理管线，以 `object` 单例形式提供。

**处理流程**：

```
原始 Bitmap
    │
    ▼
① 尺寸缩放 (max 1280px)
    │
    ▼
② 倾斜校正 / 文档校正 (二选一)
    │
    ▼
③ 直方图拉伸增强
    │
    ▼
④ JPEG 压缩 (quality=60, max 150KB)
    │
    ▼
ByteArray 输出
```

**核心方法**：

| 方法 | 说明 |
|------|------|
| `process()` | 主管线入口，串联所有处理步骤 |
| `correctDocument()` | 基于 Sobel 边缘检测的文档透视校正 |
| `tiltCorrection()` | 基于 IMU 数据的简单倾斜补偿 |
| `histogramStretch()` | 5%-95% 百分位直方图拉伸增强 |
| `perspectiveWarp()` | 透视变换（高斯消元法求解 8 参数单应矩阵） |
| `bilinearSample()` | 双线性插值采样 |
| `compressToJpeg()` | 自适应质量 JPEG 压缩，确保输出 ≤150KB |

**文档校正算法** (`correctDocument`)：

1. 下采样到 ~400px 宽度
2. 灰度化 + Sobel 算子计算梯度幅值
3. 阈值化生成边缘图（阈值 = 最大梯度 × 0.12）
4. 在四象限中搜索离中心最远的边缘点作为四角
5. 通过 8 参数透视变换将四角映射为矩形

### 4.5 传感器模块 (`sensor`)

#### `ImuProvider` 接口与 `ImuCollector` — [sensor/ImuCollector.kt](apk/src/main/java/com/obwiler/weo/sensor/ImuCollector.kt)

```kotlin
interface ImuProvider {
    val pitchDeg: Float    // 俯仰角（度）
    val rollDeg: Float     // 横滚角（度）
    fun start()
    fun stop()
}
```

`ImuCollector` 使用 `TYPE_ROTATION_VECTOR`（备选 `TYPE_GAME_ROTATION_VECTOR`）传感器，通过 `SensorManager.getOrientation()` 提取欧拉角，供图像倾斜校正使用。

### 4.6 配置模块 (`config`)

#### `AppConfig` 数据类 — [config/AppConfig.kt](apk/src/main/java/com/obwiler/weo/config/AppConfig.kt)

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `apiBaseUrl` | `String` | `""` | AI API 基础地址 |
| `apiKey` | `String` | `""` | API 密钥 |
| `modelName` | `String` | `""` | 模型名称 |
| `systemPrompt` | `String` | `""` | 系统提示词 |
| `temperature` | `Float` | `0.3` | 采样温度 |
| `maxTokens` | `Int` | `1500` | 最大输出 token |
| `timeoutMs` | `Long` | `30000` | 请求超时 (ms) |
| `textCorrectionEnabled` | `Boolean` | `true` | 启用文档校正 |

#### `ConfigHolder` — [config/ConfigHolder.kt](apk/src/main/java/com/obwiler/weo/config/ConfigHolder.kt)

配置管理实现，核心机制：

- **存储**：JSON 文件位于 `/sdcard/Android/data/com.obwiler.weo/files/weo_config.json`
- **实时监听**：通过 Android `FileObserver` 监听文件的 MODIFY/CREATE 事件
- **响应式**：内部维护 `MutableStateFlow<AppConfig>`，UI 通过 `collectAsState()` 响应变更
- **外部触发**：支持通过广播 `com.obwiler.weo.CONFIG_UPDATED` 手动触发重载

### 4.7 事件模块 (`event`)

#### `AppEvents` — [event/AppEvents.kt](apk/src/main/java/com/obwiler/weo/event/AppEvents.kt)

全局事件通道对象，使用 `Channel.CONFLATED` 策略（保留最新值）：

| 通道 | 用途 |
|------|------|
| `shutterRequest` | 快门事件（拍照请求） |
| `configUpdated` | 配置更新通知 |

### 4.8 UI 模块 (`ui`)

#### 页面路由 — [ui/Nav.kt](apk/src/main/java/com/obwiler/weo/ui/Nav.kt)

```kotlin
sealed class Screen {
    data object Home : Screen()
    data object Camera : Screen()
    data class Result(val answer: String, val steps: List<String>, val photoPath: String?) : Screen()
    data object Settings : Screen()
    data object About : Screen()
}
```

使用 sealed class 实现类型安全的页面导航，由 `MainActivity` 中的 `when` 表达式驱动。

#### 各页面职责

| 页面 | 文件 | 功能 |
|------|------|------|
| **HomeScreen** | [ui/screen/HomeScreen.kt](apk/src/main/java/com/obwiler/weo/ui/screen/HomeScreen.kt) | 主菜单：拍照识图、设置、退出 |
| **CameraScreen** | [ui/screen/CameraScreen.kt](apk/src/main/java/com/obwiler/weo/ui/screen/CameraScreen.kt) | 相机实时预览 + 快门/返回按钮 |
| **ResultScreen** | [ui/screen/ResultScreen.kt](apk/src/main/java/com/obwiler/weo/ui/screen/ResultScreen.kt) | AI 分析结果展示（成功/失败两种状态） |
| **SettingsScreen** | [ui/screen/SettingsScreen.kt](apk/src/main/java/com/obwiler/weo/ui/screen/SettingsScreen.kt) | 只读配置展示 + 网络状态 + 关于入口 |
| **AboutScreen** | [ui/screen/AboutScreen.kt](apk/src/main/java/com/obwiler/weo/ui/screen/AboutScreen.kt) | 版本信息 + 隐私提示 |

#### 主题 — [ui/theme/Theme.kt](apk/src/main/java/com/obwiler/weo/ui/theme/Theme.kt)

```kotlin
val WeoGreen = Color(0xFF00FF00)       // 纯绿（适配 MicroLED 单色屏）
val WeoGreenAlpha = Color(0x6600FF00)  // 半透明绿
val WeoDark = Color(0xFF0A0A0A)        // 近黑色背景
```

采用 Material3 暗色主题，全绿配色方案，针对 MicroLED 绿色单色显示屏优化。

### 4.9 服务模块 (`service`)

#### `KeepAliveService` — [service/KeepAliveService.kt](apk/src/main/java/com/obwiler/weo/service/KeepAliveService.kt)

前台服务，创建低优先级通知通道 `"weo_keepalive"`，防止系统在后台杀死应用进程。使用 `foregroundServiceType="specialUse"`。

---

## 5. PC 工具模块详解

### 5.1 入口与主窗口

#### `main.py` — 入口 [pc_tool/main.py](pc_tool/main.py)

- 持久化配置存储在 `%APPDATA%/WEO/weo_pc_config.json`
- 启动时显示闪屏 → 加载 `MainWindow` → 进入 Qt 事件循环
- 退出时自动保存持久化配置

#### `MainWindow` — [pc_tool/ui/main_window.py](pc_tool/ui/main_window.py)

主窗口布局为左右分栏：

```
┌──────────────────────────────────────────────────┐
│  左侧面板 (2/3)           │  右侧面板 (1/3)       │
│  ┌─────────────────────┐  │  ┌─────────────────┐  │
│  │   ScrcpyPanel       │  │  │ ⚡一键部署 📱诊断 │  │
│  │   (投屏预览)         │  │  ├─────────────────┤  │
│  ├─────────────────────┤  │  │ Tab: AI配置/安装 │  │
│  │   RingPanel         │  │  ├─────────────────┤  │
│  │   (指环按键)         │  │  │   LogPanel      │  │
│  └─────────────────────┘  │  ├─────────────────┤  │
│                           │  │  StatusPanel     │  │
│                           │  └─────────────────┘  │
└──────────────────────────────────────────────────┘
```

**核心功能**：

- **一键部署**：停止旧应用 → 安装 APK → 推送配置 → 启动应用（在 `_DeployThread` 中异步执行）
- **诊断**：获取设备信息、应用安装状态、配置信息（在 `_DiagThread` 中异步执行）
- **设备轮询**：每 15 秒通过 `PollWorker` 线程检测设备连接状态
- **按键转发**：将 RingPanel 按键事件通过 ADB `input keyevent` 发送到设备

### 5.2 核心层 (`core`)

#### `constants.py` — [pc_tool/core/constants.py](pc_tool/core/constants.py)

`WEOConfig` 数据类定义了所有可配置常量：

| 字段 | 默认值 | 说明 |
|------|--------|------|
| `pkg_name` | `com.obwiler.weo` | 应用包名 |
| `activity` | `com.obwiler.weo/.MainActivity` | 主 Activity |
| `config_device_dir` | `/sdcard/Android/data/com.obwiler.weo/files` | 设备配置目录 |
| `config_device_file` | `weo_config.json` | 配置文件名 |
| `config_broadcast_action` | `com.obwiler.weo.CONFIG_UPDATED` | 配置更新广播 |
| `adb_exe_relative` | `lib/scrcpy/adb.exe` | ADB 路径 |
| `scrcpy_exe_relative` | `lib/scrcpy/scrcpy.exe` | scrcpy 路径 |
| `scrcpy_max_size` | `640` | 投屏最大分辨率 |

#### `adb_bridge.py` — [pc_tool/core/adb_bridge.py](pc_tool/core/adb_bridge.py)

ADB 命令行封装，所有方法同步执行（调用方需在子线程中使用）：

| 方法 | 说明 |
|------|------|
| `devices()` | 列出已连接设备 |
| `first_online()` | 获取第一个在线设备序列号 |
| `shell(command)` | 执行 shell 命令 |
| `push(local, remote)` | 推送文件到设备 |
| `install(apk_path)` | 安装 APK (-r 覆盖安装) |
| `uninstall(pkg)` | 卸载应用 |
| `broadcast(action, extras)` | 发送广播 |
| `is_installed(pkg)` | 检查应用是否已安装 |
| `get_device_info()` | 获取设备型号和 Android 版本 |

Windows 下自动添加 `CREATE_NO_WINDOW` 标志避免弹出命令行窗口。

#### `config_sync.py` — [pc_tool/core/config_sync.py](pc_tool/core/config_sync.py)

配置同步器：

- **`push(config_dict)`**：将字典序列化为 JSON → 写入临时文件 → `adb push` 到设备 → 发送 `CONFIG_UPDATED` 广播通知应用重载
- **`pull()`**：通过 `adb shell cat` 读取设备上的配置文件并解析为字典

#### `apk_manager.py` — [pc_tool/core/apk_manager.py](pc_tool/core/apk_manager.py)

APK 部署管理器：

- **`deploy(config_dict)`**：完整部署流程 → 停止应用 → 安装 APK → 推送配置 → 启动应用
- **`check_status()`**：查询安装状态 + 设备信息
- **`install_only()` / `uninstall_only()`**：单独安装/卸载

### 5.3 UI 面板 (`ui`)

| 面板 | 文件 | 功能 |
|------|------|------|
| **ConfigPanel** | [ui/config_panel.py](pc_tool/ui/config_panel.py) | AI 服务商选择、API Key/URL/模型配置、Prompt 预设、温度调节、配置推送/拉取 |
| **ApkPanel** | [ui/apk_panel.py](pc_tool/ui/apk_panel.py) | APK 安装/卸载操作、设备安装状态显示 |
| **ScrcpyPanel** | [ui/scrcpy_panel.py](pc_tool/ui/scrcpy_panel.py) | scrcpy 投屏窗口嵌入（Windows HWND 操作） |
| **RingPanel** | [ui/ring_panel.py](pc_tool/ui/ring_panel.py) | 虚拟指环遥控器（方向键 + 确认/返回/Home） |
| **StatusPanel** | [ui/status_panel.py](pc_tool/ui/status_panel.py) | 底部状态栏：设备连接状态、型号、Android 版本 |
| **LogPanel** | [ui/log_panel.py](pc_tool/ui/log_panel.py) | 可折叠运行日志面板（最多 500 行） |
| **PollWorker** | [ui/poll_worker.py](pc_tool/ui/poll_worker.py) | 后台设备轮询线程 |

#### ConfigPanel 内置预设

**AI 服务商**（可通过 `resources/providers.json` 扩展）：

| 服务商 | 默认模型 |
|--------|----------|
| DeepSeek | deepseek-chat |
| 豆包(火山引擎) | doubao-1.5-vision-pro-32k |
| 硅基流动 | Pro/deepseek-ai/DeepSeek-R1 |
| OpenAI | gpt-4o-mini |
| 自定义 | — |

**Prompt 预设**：通用、搜题、翻译、OCR

#### ScrcpyPanel 投屏实现

使用 Windows `user32.dll` API 将 scrcpy 的 SDL 窗口嵌入 Qt 容器：
1. 启动 scrcpy 子进程
2. 轮询查找 `SDL_app` 类名的窗口句柄
3. 通过 `SetParent` 将其设为 Qt 容器子窗口
4. 移除标题栏和边框，根据设备分辨率计算居中布局

---

## 6. 关键类与函数参考

### Android 端

| 类/函数 | 所在文件 | 职责 |
|---------|---------|------|
| `MainActivity.handleShutter()` | MainActivity.kt | 拍照→处理→AI分析→结果页的完整流程 |
| `MainActivity.dispatchKeyEvent()` | MainActivity.kt | 物理按键分发（BACK→回首页，CENTER→拍照） |
| `MainActivity.savePhoto()` | MainActivity.kt | 保存处理后图片到 `Pictures/WEO/` |
| `CameraHolder.bindSurface()` | CameraHolder.kt | 绑定预览 Surface 并启动相机 |
| `CameraHolder.capture()` | CameraHolder.kt | 拍照并返回 JPEG 字节数组 |
| `ImagePipeline.process()` | ImagePipeline.kt | 图像处理主管线 |
| `ImagePipeline.correctDocument()` | ImagePipeline.kt | Sobel 边缘检测 + 透视校正 |
| `HttpAiClient.callApi()` | HttpAiClient.kt | 构建 OpenAI 格式请求并发送 |
| `ConfigHolder.invalidate()` | ConfigHolder.kt | 重新从文件加载配置 |

### PC 端

| 类/函数 | 所在文件 | 职责 |
|---------|---------|------|
| `MainWindow._on_deploy()` | main_window.py | 一键部署（安装+配置+启动） |
| `AdbBridge._cmd()` | adb_bridge.py | ADB 命令执行封装 |
| `ConfigSync.push()` | config_sync.py | 推送配置到设备 |
| `ConfigSync.pull()` | config_sync.py | 从设备拉取配置 |
| `ApkManager.deploy()` | apk_manager.py | 完整部署流程 |
| `ScrcpyPanel._embed()` | scrcpy_panel.py | 将 scrcpy 窗口嵌入 Qt 容器 |
| `PollWorker.run()` | poll_worker.py | 设备状态轮询 |

---

## 7. 数据流与交互时序

### 拍照→AI 分析完整流程

```
用户按下快门键
    │
    ▼
BroadcastReceiver 接收 ACTION / dispatchKeyEvent
    │
    ▼
AppEvents.shutterRequest.trySend(Unit)
    │
    ▼
LaunchedEffect 收集 → scope.launch { handleShutter() }
    │
    ├─① cameraHolder.capture()          [IO线程] Camera2 拍照 → ByteArray
    │
    ├─② BitmapFactory.decodeByteArray()  [IO线程] 解码为 Bitmap
    │   └─ ImagePipeline.process()       [IO线程] 缩放→校正→增强→压缩
    │
    ├─③ savePhoto(processed)            [IO线程] 保存到 Pictures/WEO/
    │
    ├─④ aiClient.analyze(processed)     [IO线程] HTTP POST → AI API
    │   └─ Base64 编码图片 → 构建 JSON → OkHttp 发送 → 解析响应
    │
    └─⑤ setScreen(Screen.Result(...))   [主线程] 跳转结果页
```

### PC 工具配置推送流程

```
用户编辑配置 → 点击 "推送配置"
    │
    ▼
ConfigPanel._on_push()
    │
    ▼
_PushPullThread (QThread)
    │
    ├─① ConfigSync.push(config_dict)
    │   ├─ 写入临时 JSON 文件
    │   ├─ adb push → /sdcard/Android/data/com.obwiler.weo/files/weo_config.json
    │   └─ adb broadcast CONFIG_UPDATED
    │
    ▼
Android 端 BroadcastReceiver 收到广播
    │
    ├─ configHolder.invalidate()         重新读取 JSON 文件
    └─ AppEvents.configUpdated.trySend() 通知 UI 刷新
```

---

## 8. 依赖关系

### Android APK 依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| `androidx.compose:compose-bom` | 2024.01.00 | Jetpack Compose BOM |
| `androidx.compose.ui:ui` | BOM 管理 | Compose UI 核心 |
| `androidx.compose.material3:material3` | BOM 管理 | Material3 组件 |
| `androidx.core:core-ktx` | 1.12.0 | AndroidX 核心扩展 |
| `androidx.activity:activity-compose` | 1.8.2 | Activity Compose 集成 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.7.0 | 生命周期感知组件 |
| `com.google.android.material:material` | 1.11.0 | Material 主题基础 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP 客户端 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.7.3 | Kotlin 协程 |
| `com.rokid.cxr:cxr-service-bridge` | 1.0-SNAPSHOT | Rokid CXR-S 桥接（已注释，阶段1启用） |

**构建工具**：
- Android Gradle Plugin: 8.1.4
- Kotlin: 1.9.22
- Compose Compiler Extension: 1.5.8
- JVM Target: 17
- Compile SDK: 34 (Android 14)
- Min SDK: 31 (Android 12)

### PC 工具依赖

| 依赖 | 用途 |
|------|------|
| `PySide6` | Qt6 GUI 框架 |
| `subprocess` (标准库) | ADB 命令执行 |
| `ctypes` (标准库) | Windows API 调用（scrcpy 窗口嵌入） |

**外部工具**（需放置在 `lib/scrcpy/` 目录下）：
- `adb.exe` — Android Debug Bridge
- `scrcpy.exe` — 屏幕投屏工具

### 模块间依赖关系图

```
MainActivity
    ├── → WEOApplication (获取 cameraHolder, imuCollector)
    ├── → ConfigHolder (配置管理)
    ├── → HttpAiClient (AI 分析)
    ├── → ImagePipeline (图像处理)
    ├── → CameraScreen → CameraService (相机操作)
    ├── → CameraScreen → ImuProvider (传感器)
    ├── → ResultScreen → AiResult (结果展示)
    ├── → SettingsScreen → AppConfig (配置展示)
    └── → AppEvents (事件通信)

WEOApplication
    ├── → CameraHolder : CameraService
    └── → ImuCollector : ImuProvider

HttpAiClient : AiClient
    └── → AppConfig (配置读取)
    └── → OkHttp (网络请求)

ConfigHolder : ConfigService
    └── → FileObserver (文件监听)
    └── → StateFlow<AppConfig> (响应式配置)
```

---

## 9. 配置体系

### Android 端配置

**文件路径**：`/sdcard/Android/data/com.obwiler.weo/files/weo_config.json`

**JSON 格式**：
```json
{
  "apiBaseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-...",
  "modelName": "deepseek-chat",
  "systemPrompt": "请分析图片内容，用中文回答。",
  "temperature": 0.3,
  "maxTokens": 1500,
  "timeoutMs": 30000,
  "textCorrectionEnabled": true
}
```

**更新机制**：
1. PC 工具通过 ADB push 文件 + 发送广播
2. Android 端 `FileObserver` 自动检测文件变更
3. `ConfigHolder.invalidate()` 重新解析 JSON → 更新 `StateFlow` → UI 自动刷新

### PC 端配置

**文件路径**：`%APPDATA%/WEO/weo_pc_config.json`

保存 PC 工具的持久化状态（窗口位置、上次使用的配置等）。

### 可配置常量 (`WEOConfig`)

PC 工具的行为参数在 `core/constants.py` 的 `WEOConfig` 数据类中定义，可通过外部 JSON 文件覆盖。

---

## 10. 构建与运行

### Android APK 构建

**前置条件**：
- JDK 17+
- Android SDK (API 34)
- Android Gradle Plugin 8.1.4

**命令行构建**：

```bash
cd apk

# Debug 构建
./gradlew assembleDebug
# 产物: apk/build/outputs/apk/debug/

# Release 构建 (需要签名配置)
./gradlew assembleRelease
# 产物: apk/build/outputs/apk/release/
```

**Android Studio**：直接打开 `apk/` 目录即可导入项目。

### PC 工具运行

**前置条件**：
- Python 3.10+
- PySide6

**安装依赖**：
```bash
pip install PySide6
```

**准备外部工具**：
```
pc_tool/lib/scrcpy/
    ├── adb.exe
    └── scrcpy.exe
```

**运行**：
```bash
cd pc_tool
python main.py
```

### 完整部署流程

1. 将智能眼镜通过 USB 连接 PC
2. 启动 PC 工具 → 自动检测设备
3. 在"AI 配置"标签页填写 API Key、选择服务商
4. 点击"⚡ 一键部署"→ 自动完成安装和配置推送
5. 应用自动启动 → 进入主界面

---

## 11. 签名与发布

### APK 签名配置

在 `apk/build.gradle.kts` 中定义了 `release` 签名配置：

- **优先读取** `keystore.properties` 文件中的自定义签名信息
- **回退使用** 内置的 `weo-temp.keystore` 临时签名（密码: `temporary`）

**`keystore.properties` 格式**：
```properties
storeFile=path/to/your.keystore
storePassword=your_password
keyAlias=your_alias
keyPassword=your_key_password
```

### ProGuard/R8 混淆规则

```
-keep class com.obwiler.weo.** { *; }    # 保留所有 WEO 类
-dontwarn okhttp3.**                      # 抑制 OkHttp 警告
-dontwarn okio.**
-keep class kotlinx.coroutines.** { *; }  # 保留协程类
-dontwarn com.rokid.**                    # 抑制 Rokid 警告
```

---

## 附录：Android 权限清单

| 权限 | 用途 |
|------|------|
| `CAMERA` | 相机拍照 |
| `INTERNET` | AI API 网络请求 |
| `READ_EXTERNAL_STORAGE` | 读取配置文件 |
| `WRITE_EXTERNAL_STORAGE` | 保存照片 |
| `ACCESS_NETWORK_STATE` | 检测网络连接状态 |
| `ACCESS_WIFI_STATE` | 获取 WiFi SSID |
| `SYSTEM_ALERT_WINDOW` | 系统级窗口（预留） |
| `FOREGROUND_SERVICE` | 保活前台服务 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 特殊用途前台服务 |
| `POST_NOTIFICATIONS` | 前台服务通知 |

## 附录：广播 Action 列表

| Action | 发送方 | 接收方 | 用途 |
|--------|--------|--------|------|
| `com.obwiler.weo.KEY_UP` | 外部应用/PC工具 | MainActivity | 模拟方向上键 |
| `com.obwiler.weo.KEY_DOWN` | 外部应用/PC工具 | MainActivity | 模拟方向下键 |
| `com.obwiler.weo.KEY_ENTER` | 外部应用/PC工具 | MainActivity | 模拟确认键 |
| `com.obwiler.weo.KEY_BACK` | 外部应用/PC工具 | MainActivity | 模拟返回键 |
| `com.android.action.ACTION_SPRITE_BUTTON_CLICK` | Rokid 眼镜 | MainActivity | 指环按钮点击 |
| `com.android.action.ACTION_TWO_FINGER_SWIPE_FORWARD` | Rokid 眼镜 | MainActivity | 双指前滑 |
| `com.android.action.ACTION_TWO_FINGER_SWIPE_BACK` | Rokid 眼镜 | MainActivity | 双指后滑 |
| `com.obwiler.weo.CONFIG_UPDATED` | PC 工具 | MainActivity | 配置已更新通知 |
