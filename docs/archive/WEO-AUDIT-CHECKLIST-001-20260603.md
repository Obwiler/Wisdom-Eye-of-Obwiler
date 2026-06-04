# WEO 审计核查表

## 档案编号：WEO-AUDIT-CHECKLIST-001

| 属性 | 值 |
|------|-----|
| 编制日期 | 2026-06-03 |
| 覆盖范围 | `apk/`（17 源文件） + `pc_tool/`（12 源文件） + `data/` + `compliance/` |
| 编制依据 | WEO-PC-AUDIT-001 审计报告 + 2026-06-03 全量源码审查 |
| 状态 | 待执行 |

---

### 严重度图例

| 符号 | 含义 |
|------|------|
| 🔴 | 致命——UI 冻结、程序不可用 |
| 🟠 | 严重——明显影响用户体验或数据安全 |
| 🟡 | 中等——存在隐患但暂不致命 |
| 🟢 | 建议——改进项，非阻塞 |

---

## A. PC 工具 —— 主线程阻塞（线程模型）

- [ ] **A-1** 🔴 定时轮询中 ADB 调用全在主线程
  - 文件：`pc_tool/ui/main_window.py`，`_on_timer()` → `_start_poll_worker()`
  - 当前状态：`PollWorker` 类已存在但仅包装了设备检测，`ApkPanel.refresh()` 的 `is_installed()` 仍需确认是否已移入工作线程
  - 修复：确认所有轮询周期内的 `subprocess.run()` 均只在 `PollWorker.run()` 内执行；主线程仅接收 `result` 信号更新 UI
  - 验证：启动后观察 5 分钟，轮询期间 UI 应无任何可感知卡顿

- [ ] **A-2** 🔴 指环按键在主线程同步执行 ADB
  - 文件：`pc_tool/ui/main_window.py`，`_on_ring_button()` 方法
  - 当前状态：已有 `_KeyThread`，但需确认 `_on_ring_button` 是否已完全通过 `_KeyThread` 执行（而非主线程直接调用 `self._adb.shell()`）
  - 修复：确保 `_KeyThread` 覆盖全部 9 个按键码；移除主线程路径中的任何直接 ADB 调用
  - 验证：连续快速点击指环按键（上下左右），UI 应无冻结

- [ ] **A-3** 🟠 配置推拉按钮在主线程同步阻塞
  - 文件：`pc_tool/ui/config_panel.py`，`_on_push()` / `_on_pull()`
  - 当前状态：已有 `_PushPullThread` 类
  - 修复：确认 `_PushPullThread` 已正确连接 `finished` 信号并在线程内执行所有 ADB 操作
  - 验证：点击"推送配置"按钮后，UI 可继续交互，不应卡住

- [ ] **A-4** 🟠 scrcpy 停止时 `waitForFinished(3000)` 阻塞主线程 3 秒
  - 文件：`pc_tool/ui/scrcpy_panel.py`，`stop_scrcpy()` 方法
  - 修复：改为 `terminate()` 后连接 `QProcess.finished` 信号异步清理，移除 `waitForFinished`
  - 验证：点击停止投屏按钮，UI 立即响应，无 3 秒冻结

- [ ] **A-5** 🟠 scrcpy 启动时同步查询分辨率
  - 文件：`pc_tool/ui/scrcpy_panel.py`，`self._adb.shell("wm size")`
  - 修复：将分辨率查询移入独立 `QThread`，获取结果后再启动 scrcpy 进程；或使用默认分辨率先行启动，异步更新

- [ ] **A-6** 🟡 窗口构造期间 `first_online()` 阻塞启动
  - 文件：`pc_tool/ui/main_window.py` → `AdbBridge.first_online()`
  - 修复：构造完成后通过 `QTimer.singleShot(0, ...)` 延迟到事件循环首次空闲时执行设备扫描

---

## B. PC 工具 —— 弹窗策略

- [ ] **B-1** 🟠 部署成功弹出 `QMessageBox.information` 打断用户
  - 文件：`pc_tool/ui/main_window.py`，部署完成回调
  - 修复：改为日志面板输出 `[部署] 成功`，绿色高亮
  - 例外：部署失败保留弹窗（用户需要明确知晓失败原因）

- [ ] **B-2** 🟠 诊断结果弹出 `QMessageBox.information` 打断用户
  - 文件：`pc_tool/ui/main_window.py`，诊断完成回调
  - 修复：诊断结果改为在日志面板逐行输出，或在状态栏显示摘要

---

## C. PC 工具 —— 线程安全（信号/槽）

- [ ] **C-1** 🟡 `_DeployThread` 作为局部变量，快速重复点击时旧线程信号未断开
  - 文件：`pc_tool/ui/main_window.py`，`_on_deploy()` 方法
  - 修复：在创建新线程前检查并 `disconnect` 旧线程的 `finished` 信号，或禁用按钮期间忽略旧线程回调（使用递增序列号比对）

- [ ] **C-2** 🟡 `_InstallThread` / `_UninstallThread` 缺少前序线程完成等待
  - 文件：`pc_tool/ui/apk_panel.py`
  - 修复：按钮禁用 + 线程 `isRunning()` 守卫作为双重防护；或引入 `QThread.wait()` 超时机制

- [ ] **C-3** 🟢 轮询工作线程可能堆积（旧线程未结束时新轮询触发）
  - 文件：`pc_tool/ui/main_window.py`，`_start_poll_worker()` 方法
  - 当前状态：已有 `isRunning()` 守卫
  - 验证：确认守卫覆盖所有路径，如有遗漏补充

---

## D. Android 端 —— 图像管线性能

- [ ] **D-1** 🟡 `perspectiveWarp()` 逐像素双线性插值在 Kotlin/JVM 上性能偏低
  - 文件：`apk/src/main/java/com/obwiler/weo/image/ImagePipeline.kt`，`perspectiveWarp()` 方法
  - 当前影响：文档校正模式下，400×? 采样输出可接受；若未来提高输出分辨率（如 1280px），Kotlin 逐像素循环将成为瓶颈
  - 建议方案（三选一，按投入递增）：
    1. 将逐像素循环用 `android.graphics.Bitmap` + `Canvas.drawBitmapMesh()` 替代透视变换
    2. 将 `bilinearSample` + `perspectiveWarp` 核心循环改为并行 `Coroutine` 分块处理
    3. 未来若引入 OpenCV Android SDK，替换透视变换和直方图拉伸
  - 验证：在低端设备（如骁龙 662）上测量文档校正的端到端耗时，目标 < 500ms

- [ ] **D-2** 🟢 `correctDocument()` 中对已回收的 `bitmap` 再次调用 `recycle()` 无害但多余
  - 文件：`apk/src/main/java/com/obwiler/weo/image/ImagePipeline.kt`，`process()` 中 `bitmap.recycle()` 后 `current` 指向新对象
  - 建议：保留当前写法（逻辑正确），仅在代码审查时注意

---

## E. Android 端 —— AI 集成

- [ ] **E-1** 🟢 用户消息中的硬编码中文提示词 `"请分析图片内容"` 与可配置 `systemPrompt` 不一致
  - 文件：`apk/src/main/java/com/obwiler/weo/ai/HttpAiClient.kt`，`callApi()` 方法
  - 建议：将用户消息文本也纳入配置项 `userPrompt`，或从 `config` 中读取；当前硬编码不影响功能，但降低了 prompt 自定义灵活性

- [ ] **E-2** 🟢 `parseResponse()` 中 `content.take(500)` 截断可能丢失关键信息
  - 文件：`apk/src/main/java/com/obwiler/weo/ai/HttpAiClient.kt`
  - 建议：将截断上限改为与 `maxTokens` 一致或提升到 2000+；或在前端 `ResultScreen` 中提供"查看完整回复"按钮

- [ ] **E-3** 🟡 未处理 `content` 为 JSON 数组（多模态返回）的情况
  - 文件：`apk/src/main/java/com/obwiler/weo/ai/HttpAiClient.kt`
  - 部分模型（如 GPT-4o）可能返回 `content` 为 `[{type:"text", text:"..."}]` 格式
  - 修复：增加 `content` 类型判断——字符串直接使用，数组则提取 text 字段拼接

---

## F. 配置与数据

- [ ] **F-1** 🟠 签名密钥以明文 fallback 写入 `build.gradle.kts`
  - 文件：`apk/build.gradle.kts`，`signingConfigs.release` 块
  - 风险：当 `keystore.properties` 不存在时，使用硬编码的 `storePassword = "temporary"` 和 `keyPassword = "temporary"`
  - 修复：移除硬编码 fallback，改为构建失败并提示创建 `keystore.properties`；或将密钥机密移入 CI/CD 环境变量

- [ ] **F-2** 🟢 `providers.json` 在两处重复
  - 文件：`pc_tool/resources/providers.json` 和 `data/providers.json`
  - 建议：确认两文件内容是否一致；如一致，PC 工具仅加载 `resources/providers.json`，`data/` 下的副本可移入 archive

- [ ] **F-3** 🟢 `weo_config_schema.json` 未在任何代码中被引用校验
  - 文件：`data/weo_config_schema.json`
  - 建议：在 `ConfigPanel.get_config_dict()` 或 `ConfigSync.push()` 中增加 JSON Schema 校验，防止非法配置推送到设备

---

## G. 文档与合规

- [ ] **G-1** 🟢 隐私政策仅有 Markdown 和 HTML 版本，缺少独立可打印 PDF
  - 文件：`compliance/PRIVACY_POLICY.md`，`compliance/PRIVACY_POLICY.html`
  - 建议：生成 `PRIVACY_POLICY.pdf` 放入 `compliance/`，便于分发

- [ ] **G-2** 🟢 缺少 API 使用说明文档
  - 建议：在 `docs/` 下新增 `API.md`，说明 AI 服务商配置格式、支持的模型、请求/响应示例

- [ ] **G-3** 🟢 缺少开发者构建指南
  - 建议：在仓库根目录新增 `BUILDING.md`，覆盖 APK 构建（Android Studio / Gradle CLI）、PC 工具打包（PyInstaller）、环境依赖

---

## H. 测试覆盖

- [ ] **H-1** 🟡 两个子工程均无自动化测试
  - Android：无 `src/test/` 或 `src/androidTest/` 目录
  - PC 工具：无 `tests/` 目录，无 pytest 配置
  - 优先覆盖：
    1. `ImagePipeline.process()` —— 输入已知测试图片，验证输出尺寸和 JPEG 字节范围
    2. `HttpAiClient.buildUrl()` —— 验证多种 base URL 格式的补齐逻辑
    3. `AdbBridge._cmd()` —— Mock `subprocess.run`，验证设备列表解析
    4. `ConfigPanel.get_config_dict()` —— 验证空输入和完整输入下的输出结构

---

## I. 依赖与版本

- [ ] **I-1** 🟢 Gradle 工具链版本可升级
  - 当前：AGP 8.1.4, Kotlin 1.9.22, Compose BOM 2024.01.00
  - 建议：下一个迭代窗口升级到 AGP 8.7+ / Kotlin 2.0+ / Compose BOM 2024.09+，获取编译器性能提升和 bug 修复

- [ ] **I-2** 🟢 `pc_tool/requirements.txt` 缺少版本锁定
  - 当前：`pyside6>=6.7`, `adbutils>=2.9`, `Pillow>=10.0`
  - 建议：添加 `requirements-freeze.txt` 记录已知可用的精确版本号，避免 CI/同事环境安装不兼容新版

---

## 统计

| 分类 | 致命 🔴 | 严重 🟠 | 中等 🟡 | 建议 🟢 | 合计 |
|------|---------|---------|---------|---------|------|
| A. 主线程阻塞 | 2 | 3 | 1 | — | 6 |
| B. 弹窗策略 | — | 2 | — | — | 2 |
| C. 线程安全 | — | — | 2 | 1 | 3 |
| D. 图像管线 | — | — | 1 | 1 | 2 |
| E. AI 集成 | — | — | 1 | 2 | 3 |
| F. 配置与数据 | — | 1 | — | 2 | 3 |
| G. 文档合规 | — | — | — | 3 | 3 |
| H. 测试覆盖 | — | — | 1 | — | 1 |
| I. 依赖版本 | — | — | — | 2 | 2 |
| **总计** | **2** | **6** | **6** | **11** | **25** |

---

> **执行建议：优先处理 🔴 A-1 和 A-2（它们直接导致 UI 不可用），然后按 🟠→🟡→🟢 顺序推进。预计 A 类全部修复后，用户体验将有质变。**
>
> 下一动作：逐项勾选执行，完成后更新本核查表状态并归档新版本。
