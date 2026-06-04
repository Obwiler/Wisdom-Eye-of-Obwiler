# WEO APK 修正计划书 v0.2.0

> 制定日期: 2026-06-03  
> 目标设备: RG_glasses（乐奇AI眼镜 / Rokid Glasses）  
> 硬件档案: docs/hardware/RG_glasses_spec.md  
> 前置审计: 2026-06-03 全量代码审计

---

## 零、约束条件

1. **不使用戒指触摸板** — 易误触，仅使用物理按键（镜腿触摸条 + 戒指方向键/确认键）
2. **不与 Rokid 官方操作冲突** — 避免拦截已被 sprite.launcher / assistserver / cxrservice 占用的按键
3. **保留脱离灵珠 Agent 的方案** — AI 调用继续走 OpenAI 兼容协议，不依赖 assistserver
4. **UI 布局/样式不变** — 用户已确认满意当前界面

---

## 一、输入系统修正

### 1.1 按键冲突分析

| 按键 | Rokid 系统占用 | WEO 当前用法 | 冲突？ |
|------|---------------|-------------|--------|
| BACK | sprite.launcher 全局返回 | dispatchKeyEvent → 回 Home | ⚠️ 需确认 |
| HOME/DASHBOARD | sprite.launcher 回桌面 | 未使用 | 不动 |
| ENTER/DPAD_CENTER | 系统确认键 | 相机页→拍照 | ✅ 安全 |
| UP/DOWN/LEFT/RIGHT | 系统导航 | 未使用 | 不动 |
| PROG1/2/3 | 未知（可能精灵服务） | 未使用 | 不动 |
| CAMERA | assistserver 拍照？ | 未使用 | 不动 |
| VOICECOMMAND | 语音助手唤醒 | 未使用 | 不动 |

### 1.2 修正方案

**P0: 验证 BACK 键冲突**
- 当前: WEO 的 dispatchKeyEvent 拦截 BACK → 一律跳 Home
- 风险: 可能干扰 Rokid 桌面返回逻辑
- 方案: BACK 仅在 WEO 处于前台且非 Home 页时才拦截；Home 页按 BACK 交给系统处理
- 实现: dispatchKeyEvent 中增加页面判断

**P1: 按键白名单**
- WEO 仅主动消费: KEYCODE_DPAD_CENTER（拍照）、KEYCODE_ENTER（确认）
- BACK 条件消费（见上）
- 其余按键全部 eturn super.dispatchKeyEvent(event) 交给系统
- 去掉 BroadcastReceiver 中的 KEY_UP/KEY_DOWN/KEY_ENTER/KEY_BACK 手动映射（这些 action 来自 PC 工具 RingPanel 的 ADB input 转发，保留也无害但应标注来源）

**P2: 移除 Rokid 广播按键映射**
- 当前 BroadcastReceiver 拦截了 ACTION_SPRITE_BUTTON_CLICK → 映射为 DPAD_CENTER
- 这个 action 来自 Rokid 精灵服务，可能与官方操作冲突
- 方案: 注释掉此映射，拍照仅通过物理按键 KeyEvent 触发（镜腿 ENTER / 戒指 ENTER）
- 保留 CONFIG_UPDATED 广播处理（PC 工具配置推送用）

---

## 二、传感器修正

### 2.1 问题

ImuCollector 依赖 TYPE_ROTATION_VECTOR，但 RG_glasses **没有此传感器**。当前 pitchDeg 和 ollDeg 始终为 0。

### 2.2 修正方案

`kotlin
// 改用加速度计 + 地磁传感器
val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
// 在 onSensorChanged 中:
SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelData, magnetData)
SensorManager.getOrientation(rotationMatrix, orientation)
pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
`

- 同时注册两个传感器
- 两个传感器的数据到齐后才计算方向
- 保留 ImuProvider 接口不变，上层无感知
- 加速度计和地磁均已确认存在（pm list features 有 accelerometer + compass）

---

## 三、导航修正

### 3.1 问题

dispatchKeyEvent 中 BACK 键无条件跳 Home，无返回栈。

### 3.2 修正方案

用 ArrayDeque<Screen> 做轻量导航栈:

`kotlin
private val navStack = ArrayDeque<Screen>().apply { addLast(Screen.Home) }
`

- Camera → 拍照后在 Camera 页（不入栈）
- Camera 按 BACK → pop 回 Home
- Settings 按 BACK → pop 回 Home
- About 按 BACK → pop 回 Settings
- Result 按 BACK → pop 回 Home
- Home 按 BACK → 交给系统（可能退出应用）

---

## 四、相机修正

### 4.1 Camera2 废弃 API

CameraHolder 使用 createCaptureSession(List<Surface>, ...) 在 targetSdk 34 已废弃。

**修正**: 迁移到 createCaptureSession(SessionConfiguration) + CameraCaptureSession.StateCallback:
`kotlin
val sessionConfig = SessionConfiguration(
    SessionConfiguration.SESSION_REGULAR,
    outputSurfaces,
    cameraHandler.looper.executor,
    captureSessionCallback
)
cameraDevice.createCaptureSession(sessionConfig)
`

### 4.2 预览流配置

当前使用时 @Suppress("DEPRECATION") — 修正后移除。

---

## 五、图像管线修正

### 5.1 Bitmap 生命周期

当前 ImagePipeline.process() 及其子函数约定"吃掉输入 Bitmap 并回收"，设计脆弱。

**修正**: 统一去掉所有 itmap.recycle() / src.recycle() 调用，让 GC 管理。

### 5.2 变量命名混淆

process(bitmap: Bitmap, ...) 中 ar current = bitmap + itmap.recycle() 极易误读。

**修正**: 参数重命名为 original，局部变量保持 current。

---

## 六、AI 客户端修正

### 6.1 缺少重试

HttpAiClient 对网络、超时、429 均无重试。

**修正**: 添加最多 2 次重试 + 指数退避（1s, 2s），429 响应特别加长等待。

`kotlin
repeat(MAX_RETRIES) { attempt ->
    try {
        return callApi(image, config)
    } catch (e: SocketTimeoutException) {
        if (attempt == MAX_RETRIES - 1) throw e
        delay(1000L * (attempt + 1))
    }
}
`

---

## 七、其他修正

| # | 问题 | 修正 |
|---|------|------|
| 7.1 | WiFi SSID 需位置权限 | SettingsScreen 中 SSID 读取包裹 try-catch，返回 <unknown ssid> 时显示 "WiFi: 已连接"（不显示 SSID） |
| 7.2 | ProGuard 规则过宽 | 改 -keep class com.obwiler.weo.** { *; } 为仅 keep 反射/序列化需要的类 |
| 7.3 | 缺单元测试 | 为 ImagePipeline、HttpAiClient、ConfigHolder 添加单测 |
| 7.4 | Settings 只读 | 保持只读（配置由 PC 工具推送），不加设备端编辑 |

---

## 八、执行顺序

| 步骤 | 内容 | 预计影响 |
|------|------|---------|
| **S1** | 按键冲突分析 + BACK 键修正 + 白名单 | 交互行为变更，需实机验证 |
| **S2** | ImuCollector 传感器修正 | ImagePipeline 倾斜校正将生效 |
| **S3** | 导航栈 | 返回逻辑改善 |
| **S4** | Camera2 废弃 API 迁移 | 需实机验证预览+拍照正常 |
| **S5** | ImagePipeline 内存清理 | 无行为变化，低风险 |
| **S6** | HttpAiClient 重试 | 网络不稳定时体验改善 |
| **S7** | ProGuard + WiFi SSID + 单测 | 低风险 |
| **S8** | 实机全流程测试 | 拍照→处理→AI→结果 |

---

## 九、不做的

- ❌ 不接入灵珠 Agent（assistserver）— 等审核通过后再议
- ❌ 不调用 CXR SDK — 按键已通过标准 KeyEvent 通道可用
- ❌ 不用戒指触摸板 — 误触风险，不引入
- ❌ 不改 UI 布局/样式
- ❌ 不在设备端做配置编辑 — PC 工具推送是唯一配置入口
