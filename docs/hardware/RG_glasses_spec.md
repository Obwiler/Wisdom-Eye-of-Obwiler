# RG_glasses（乐奇AI眼镜 / Rokid Glasses）硬件规格档案

> 采集日期: 2026-06-03  
> 采集方式: ADB 实机探测  
> 设备序列号: 1901092603971563  
> 归档位置: docs/hardware/RG_glasses_spec.md

---

## 1. 系统信息

| 参数 | 值 |
|------|-----|
| 型号 | RG_glasses |
| 制造商/品牌 | Rokid |
| 主板代号 | neo（高通骁龙 AR1 Gen 1 平台） |
| CPU 架构 | arm64-v8a |
| Android 版本 | 12 (SDK 32) |
| Build ID | SKQ1.240613.001 release-keys |
| 屏幕物理尺寸 | 480x640 |
| 屏幕密度 | 物理 240 dpi / 覆盖 192 dpi |
| 屏幕方向 | 同时支持 landscape + portrait |

---

## 2. 相机

| 参数 | 值 |
|------|-----|
| 相机数量 | 1 |
| 朝向 | BACK（后置/第一视角） |
| 传感器方向 | 270° |
| 传感器分辨率 | 4032x3024（~12MP） |
| 光圈 | f/2.25 |
| 焦距 | 1.9mm |
| 对焦 | 固定焦距（AF 模式仅 OFF） |
| 闪光灯 | 无 |
| Camera2 能力 | BACKWARD_COMPATIBLE, RAW, YUV_REPROCESSING, MANUAL_SENSOR, BURST_CAPTURE, MANUAL_POST_PROCESSING |
| JPEG 缩略图尺寸 | 0x0, 176x144, 240x144, 256x144, 240x160, 256x154, 246x184, 240x240, 320x240 |
| JPEG 最大尺寸 | 18,457,096 bytes |
| HAL 版本 | legacy/1-0 (v2.7), device@3.7 |
| 视频设备 | /dev/video0/1/32/33 |
| 变焦范围 | 1.0x - 8.0x |

---

## 3. 传感器

| 传感器 | 可用性 |
|--------|--------|
| 加速度计 (accelerometer) | YES |
| 陀螺仪 (gyroscope) | YES |
| 地磁/罗盘 (compass/magnetometer) | YES |
| 光线传感器 (light) | YES |
| 距离传感器 (proximity) | YES |
| TYPE_ROTATION_VECTOR | **NO - 需用加速度计+地磁手动计算** |
| GPS | YES |

---

## 4. 输入系统

### 4.1 设备总览（戒指连接时 5 个输入设备）

| # | 设备节点 | 名称 | 通道 | 类型 |
|---|---------|------|------|------|
| 0 | /dev/input/event0 | qpnp_pon | 电源 IC | VOLUMEDOWN, MENU |
| 1 | /dev/input/event1 | ROKID,PSOC-TP-R | I2C | 镜腿触摸条 |
| 2 | /dev/input/event2 | RGR06 Keyboard | 蓝牙 uhid | 戒指全键盘 |
| 3 | /dev/input/event3 | RGR06 Consumer Control | 蓝牙 uhid | 戒指媒体控制 |
| 4 | /dev/input/event4 | RGR06 | 蓝牙 uhid | 戒指触摸板 |

### 4.2 镜腿触摸条 — ROKID,PSOC-TP-R (event1)

- 连接: I2C（非蓝牙）
- 按键: ENTER / UP / DOWN / LEFT / RIGHT / BACK / DASHBOARD / PROG1 / PROG2 / PROG3 / F13 / F14

### 4.3 RGR06 戒指 — 蓝牙 LE HID

| 属性 | 值 |
|------|-----|
| 设备名 | RGR06 |
| MAC | D1:05:77:BC:44:A1 |
| Vendor:Product | 248a:045b |
| 类型 | Bluetooth LE HID (uhid) |

**Consumer Control (event3) — WEO 相关按键:**
ENTER / UP / DOWN / LEFT / RIGHT / BACK / HOME / CAMERA / SEARCH / ASSISTANT / VOICECOMMAND / VOLUME+/- / PLAY_PAUSE / MUTE / POWER

**触摸板 (event4):**
- BTN_TOUCH + BTN_DIGI
- 绝对坐标: ABS_X 0-4095, ABS_Y 0-4095
- INPUT_PROP_POINTER

### 4.4 WEO 兼容性

- 所有输入设备均通过 Android 标准 KeyEvent 通道，dispatchKeyEvent() 可直接捕获
- 戒指触摸板可额外用于滑动/手势交互

---

## 5. 连接与网络

| 参数 | 值 |
|------|-----|
| WiFi | YES (含 WiFi Direct, Passpoint, Aware, RTT) |
| 蓝牙 | YES (已启用) |
| GPS | YES |
| USB | Host + Accessory |
| 蓝牙名称 | Glasses_1563 |
| 蓝牙 MAC | AC:86:D1:58:AD:11 |
| 已配对设备 | iPhone17 Pro, RGR06 |

---

## 6. Rokid 系统预装包

| 包名 | APK 路径 | 功能 |
|------|---------|------|
| com.rokid.os.sprite.launcher | /product/app/RokidSpriteLauncher/ | 桌面启动器 |
| com.rokid.os.sprite.live | /product/app/RokidSpriteLive/ | 精灵实时服务 |
| com.rokid.os.sprite.snapflow | /product/app/RokidSpriteSnapFlow/ | 快照流程 |
| com.rokid.os.sprite.assistserver | /product/app/RokidSpriteAssistServer/ | **灵珠 AI Agent（审核中）** |
| com.rokid.cxrservice | /system/app/CXRService/ | **CXR 桥接服务（系统级）** |
| com.rokid.sysconfig | /system/priv-app/RokidSysConfig/ | 系统配置 |
| com.rokid.glass.ota | /product/app/RokidOtaUpgrade/ | OTA 升级 |
| com.rokid.os.master.screenstream | /product/app/RokidScreenRecord/ | 屏幕串流 |

---

## 7. WEO 应用状态

| 参数 | 值 |
|------|-----|
| 包名 | com.obwiler.weo |
| 版本 | 0.1.0 |
| 状态 | 已安装，相机可正常连接 |
| 最新相机使用 | 2026-06-03 10:40, 640x480 流, ~622 帧 |

---

## 8. 关键开发注意事项

1. **无 TYPE_ROTATION_VECTOR** — ImuCollector 需改用加速度计+地磁计算方向
2. **CXR Service 已预装** — com.rokid.cxrservice 是系统应用，不必等审核即可调用
3. **灵珠 Agent (assistserver)** — 已预装但可能需审核通过才能使用 API
4. **RGR06 触摸板** — 4096x4096 触摸板可做滑动/手势交互
5. **双输入通道** — 镜腿触摸条 + 戒指走同一 KeyEvent 通道，WEO 无需区分来源
6. **ADB 侧载可用** — WEO APK 正常安装运行，相机 640x480 流稳定
