# 奥贝之眼 · Wisdom Eye of Obwiler (WEO)

[![Version](https://img.shields.io/badge/version-0.3.4-blue)]()
[![Android](https://img.shields.io/badge/Android-12%2B-green)]()
[![License](https://img.shields.io/badge/license-MIT-orange)]()

> **"所见非所得，所得皆 AI。"**
>
> 戴上一只眼看世界，AI 帮你把另一只补齐。奥贝之眼，替你把世界翻译成人话。

**WEO** 是一款面向 MicroLED 单色屏智能眼镜（480×640）的 AI 视觉识别应用。按一下快门，AI 告诉你眼前是什么。

> 基于 [Rokid RG-glasses](https://www.rokid.com) 开发（骁龙 AR1、Android 12、12MP 相机、1.8GB 内存）。

```text
┌──────────────────────────────────────────────┐
│   按下快门   →   云端 AI 分析   →   结果上屏   │
│      🔘              ☁️🤖              📟      │
│   眼镜摄像头        兼容 OpenAI      黑底绿字    │
│   12MP             任意视觉模型     MicroLED    │
└──────────────────────────────────────────────┘
```

## 功能

- **一键拍照** — 眼镜物理按键触发，无需摸手机
- **换 AI 如换衣服** — 兼容任何 OpenAI Chat Completions 接口（DeepSeek、豆包、硅基流动、MIMO、GPT-4o…随你换）
- **图像预处理** — IMU 倾斜校正，歪着头也能拍出端正的文字
- **桌面管理工具** — Windows 端一键部署、配置同步、屏幕镜像（scrcpy）、文件管理、Shell 终端
- **单色屏优化 UI** — 纯黑底 + 绿色字，≥44dp 触摸区域，6-8 行信息密度
- **纯按键操作** — 眼镜触控板 + ADB 键注入，零触屏也能用
- **WiFi 热点自建** — 眼镜自己开热点，不依赖手机热点
- **AI 智能命名** — 根据照片内容自动生成文件名，双写 MediaStore + 私有目录
- **相册回顾** — 眼镜上直接浏览历史照片和 AI 摘要

## 架构

```
  ┌──────────────────────────────────────────────────┐
  │            PC 管理工具 (PySide6, Windows)         │
  │   配置管理 · 一键部署 · 屏幕镜像 · Shell 终端     │
  └──────────────────────┬───────────────────────────┘
                         │ ADB (USB)
                         ▼
  ┌──────────────────────────────────────────────────┐
  │          眼镜端 APK (Kotlin, Jetpack Compose)     │
  │                                                   │
  │  Camera2 ──→ ImagePipeline ──→ HttpAiClient ──→ ☁️ │
  │              (IMU 倾斜校正)     (OpenAI 协议)       │
  │                                                   │
  │  界面: 主页 → 相机 → 结果 ← 设置 ← 关于           │
  │              ↓ 相册                                │
  └──────────────────────────────────────────────────┘
```

### 子项目

| 目录 | 技术栈 | 用途 |
|------|--------|------|
| `apk/` | Kotlin + Compose + Camera2 | 眼镜端 Android 应用 |
| `pc_tool/` | Python + PySide6 + ADB | Windows 桌面管理工具 |

### 核心模块（眼镜端）

| 模块 | 路径 | 职责 |
|------|------|------|
| AI 客户端 | `apk/.../ai/HttpAiClient.kt` | 兼容 OpenAI 的视觉 API 调用，带重试 |
| 相机 | `apk/.../camera/CameraHolder.kt` | Camera2 拍照 + TextureView 预览 |
| 图像处理 | `apk/.../image/ImagePipeline.kt` | 倾斜校正、缩放、JPEG 编码 |
| IMU 传感器 | `apk/.../sensor/ImuCollector.kt` | Game Rotation Vector 头部姿态 |
| 配置管理 | `apk/.../config/ConfigHolder.kt` | JSON 配置文件 + FileObserver 热加载 |
| HTTP 服务 | `apk/.../server/WeoHttpServer.kt` | 零依赖的轻量 REST API |
| 保活服务 | `apk/.../service/KeepAliveService.kt` | 前台服务防被杀 |

### AI 供应商配置

通过 PC 工具编辑 `weo_config.json` 即可切换 AI：

```json
{
  "apiBaseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-你的密钥",
  "modelName": "deepseek-chat",
  "systemPrompt": "请分析图片内容，用中文回答。",
  "userPrompt": "这张图里有什么？",
  "temperature": 0.3,
  "maxTokens": 1500,
  "timeoutMs": 30000
}
```

内置供应商预设：DeepSeek · 豆包（火山引擎） · 硅基流动 · OpenAI · 小米 MIMO。

## 快速开始

### 准备工作

- Rokid RG-glasses，已开启 USB 调试
- Windows 电脑，已安装 ADB
- （如需构建 APK）Android SDK / Android Studio

### PC 工具一键启动

1. 下载最新 `WEO-PC工具.exe`（见 [Releases]()）
2. USB 连接眼镜，开启 ADB 调试
3. 启动 PC 工具，自动识别设备
4. 在 **WEO** 面板配置 AI 供应商
5. 点击 **推送配置** 同步到眼镜
6. 看镜像面板或透过眼镜操作

### 源码构建

```bash
# Android APK
cd apk
./gradlew assembleDebug

# PC 工具
cd pc_tool
pip install -r requirements.txt
python main.py

# 打包独立 EXE
python -m PyInstaller "WEO-PC工具.spec" --noconfirm
```

### 设备参数（Rokid RG-glasses）

| 参数 | 值 |
|------|-----|
| 芯片 | 骁龙 AR1 Gen 1 |
| 系统 | Android 12 (SDK 32) |
| 内存 | 1.8 GB |
| 屏幕 | 480×640 MicroLED 单色绿 |
| 摄像头 | 12MP (2048×1536) |
| 传感器 | 加速度计、陀螺仪、Game Rotation Vector |
| 输入 | 触控板（方向手势）+ 物理按键 |

## 项目结构

```text
Wisdom Eye of Obwiler/
├── apk/                    # Android 应用 (Kotlin, Gradle)
│   └── src/main/java/com/obwiler/weo/
│       ├── ai/             # AI 客户端 (OpenAI API)
│       ├── app/            # Application + DI
│       ├── camera/         # Camera2 拍照 + 预览
│       ├── config/         # JSON 配置管理
│       ├── event/          # 内部事件总线
│       ├── image/          # 图像处理管线
│       ├── network/        # WiFi 管理
│       ├── photo/          # 照片存储 + 命名
│       ├── sensor/         # IMU 姿态
│       ├── server/         # 嵌入式 HTTP 服务
│       ├── service/        # 前台保活
│       └── ui/             # Compose 界面 + 主题
├── pc_tool/                # Windows 桌面管理工具
│   ├── app/                # Application 单例 + 主题
│   ├── core/               # ADB、部署、配置同步
│   ├── resources/          # 主题、图标、供应商列表
│   └── ui/                 # 面板 + 组件 (PySide6)
├── data/                   # 配置模板 + Schema
├── docs/                   # 架构文档、硬件规格
└── compliance/             # 隐私政策、第三方声明
```

## 许可证

MIT © 2026 Obwiler

---

*"别问我看没看见——AI 看见了。"*
