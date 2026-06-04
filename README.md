# 奥贝之眼 · Wisdom Eye of Obwiler (WEO)

[![Version](https://img.shields.io/badge/version-0.3.4-blue)]()
[![Android](https://img.shields.io/badge/Android-12%2B-green)]()
[![License](https://img.shields.io/badge/license-MIT-orange)]()

**WEO** is an AI-powered visual recognition app for MicroLED smart glasses (480x640 monochrome display). Point your glasses at anything, press a button, and get instant AI analysis — right in your field of view.

> Built for the [Rokid RG-glasses](https://www.rokid.com) (Snapdragon AR1, Android 12, 12MP camera, 1.8GB RAM).

```text
┌──────────────────────────────────────────────┐
│   Press shutter  →  AI analyzes  →  Result   │
│       🔘              ☁️🤖              📟      │
│    Glasses          Any OpenAI-     Green-on-  │
│    camera           compatible      black      │
│    12MP             vision API      display    │
└──────────────────────────────────────────────┘
```

## Features

- **One-button capture** — Physical shutter button on the glasses triggers instant photo + AI analysis
- **Bring your own AI** — Compatible with any OpenAI Chat Completions API (DeepSeek, Doubao, SiliconFlow, MIMO, GPT-4o...)
- **Image pipeline** — IMU-based tilt correction for readable text even when your head is slightly tilted
- **PC management tool** — Desktop companion for APK deployment, config sync, screen mirroring (scrcpy), file browsing, and shell access — all over ADB
- **MicroLED-optimized UI** — Pure black + green monochrome theme, 44dp touch targets, 6-8 line readable density
- **D-pad navigation** — Full keyboardless operation via glasses touchpad or ADB key injection
- **WiFi hotspot auto-management** — Phone-free operation; glasses create their own hotspot for API calls
- **Smart photo naming** — AI-generated filenames based on image content, stored to both MediaStore and private directory
- **Gallery** — Browse past captures with AI summaries, all on-device

## Architecture

```
  ┌──────────────────────────────────────────────────┐
  │             PC Tool (PySide6, Windows)            │
  │  Config · Deploy · Mirror · Shell · Screenshots   │
  └──────────────────────┬───────────────────────────┘
                         │ ADB (USB)
                         ▼
  ┌──────────────────────────────────────────────────┐
  │         Glasses APK (Kotlin, Jetpack Compose)     │
  │                                                   │
  │  Camera2 ──→ ImagePipeline ──→ HttpAiClient ──→ ☁️ │
  │                (IMU tilt)       (OpenAI API)       │
  │                                                   │
  │  UI: Home → Camera → Result ← Settings ← About    │
  │            ↓ Gallery                              │
  └──────────────────────────────────────────────────┘
```

### Sub-projects

| Directory | Tech | Purpose |
|-----------|------|---------|
| `apk/` | Kotlin + Compose + Camera2 | Android app running on the glasses |
| `pc_tool/` | Python + PySide6 + ADB | Windows desktop management tool |

### Key Modules (Glasses APK)

| Module | Path | Role |
|--------|------|------|
| AI Client | `apk/.../ai/HttpAiClient.kt` | OpenAI-compatible vision API calls with retry |
| Camera | `apk/.../camera/CameraHolder.kt` | Camera2 capture + TextureView preview |
| Image Pipeline | `apk/.../image/ImagePipeline.kt` | Tilt correction, scale, JPEG encode |
| IMU Sensor | `apk/.../sensor/ImuCollector.kt` | Game Rotation Vector for head orientation |
| Config | `apk/.../config/ConfigHolder.kt` | JSON config with FileObserver live reload |
| HTTP Server | `apk/.../server/WeoHttpServer.kt` | Zero-dependency REST API for PC tool |
| Keep-Alive | `apk/.../service/KeepAliveService.kt` | Foreground service prevents app from being killed |

### AI Provider Configuration

Edit `weo_config.json` (synced via PC tool) to switch AI providers:

```json
{
  "apiBaseUrl": "https://api.deepseek.com/v1",
  "apiKey": "sk-your-key-here",
  "modelName": "deepseek-chat",
  "systemPrompt": "Analyze the image and respond in Chinese.",
  "userPrompt": "What do you see in this image?",
  "temperature": 0.3,
  "maxTokens": 1500,
  "timeoutMs": 30000
}
```

Pre-configured providers: DeepSeek, Doubao (Volcengine), SiliconFlow, OpenAI, Xiaomi MIMO.

## Getting Started

### Prerequisites

- Rokid RG-glasses with USB debugging enabled
- Windows PC with ADB installed
- Android SDK / Android Studio (for APK builds)

### Quick Start (PC Tool)

1. Download the latest `WEO-PC工具.exe` from [Releases]()
2. Connect glasses via USB, enable ADB debugging
3. Launch the PC tool — it auto-detects the device
4. Configure your AI provider in the **WEO** tab
5. Click **Push Config** to sync to the glasses
6. Use the mirror panel or look through the glasses to operate

### Build from Source

```bash
# Android APK
cd apk
./gradlew assembleDebug

# PC Tool
cd pc_tool
pip install -r requirements.txt
python main.py

# Build standalone EXE
python -m PyInstaller "WEO-PC工具.spec" --noconfirm
```

### Device Specs (Rokid RG-glasses)

| Parameter | Value |
|-----------|-------|
| Chipset | Snapdragon AR1 Gen 1 |
| Android | 12 (SDK 32) |
| RAM | 1.8 GB |
| Display | 480×640 MicroLED (monochrome green) |
| Camera | 12MP (2048×1536) |
| Sensors | Accelerometer, Gyroscope, Game Rotation Vector |
| Input | Touchpad (D-pad gestures) + physical buttons |

## Development

```text
Wisdom Eye of Obwiler/
├── apk/                    # Android app (Kotlin, Gradle)
│   └── src/main/java/com/obwiler/weo/
│       ├── ai/             # AI client (OpenAI API)
│       ├── app/            # Application + DI
│       ├── camera/         # Camera2 capture + preview
│       ├── config/         # JSON config management
│       ├── event/          # Internal event bus
│       ├── image/          # Image processing pipeline
│       ├── network/        # WiFi management
│       ├── photo/          # Photo storage + naming
│       ├── sensor/         # IMU orientation
│       ├── server/         # Embedded HTTP server
│       ├── service/        # Foreground keep-alive
│       └── ui/             # Compose screens + theme
├── pc_tool/                # Windows PC management tool
│   ├── app/                # Application singleton + theme
│   ├── core/               # ADB, deploy, config sync
│   ├── resources/          # Theme, icons, providers
│   └── ui/                 # Panels + widgets (PySide6)
├── data/                   # Config templates + schemas
├── docs/                   # Architecture docs, specs
└── compliance/             # Privacy policy, licenses
```

## License

MIT © 2026 Obwiler

---

*"Wisdom at the speed of sight."*
