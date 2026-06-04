# WEO PC 工具优化计划书 v0.3.0

> 制定日期: 2026-06-03
> 前置审计: 2026-06-03 PC 工具全量代码审计（10 个源文件）
> 关联: APK 端同步修正至 v0.2.0

---

## 零、约束条件

1. **不改变现有窗口布局** — 左侧投屏+指环 / 右侧配置+安装+日志+状态栏 的结构不动
2. **不改变 Qt 框架** — 继续使用 PySide6
3. **兼容 APK v0.2.0 的输入模型** — RingPanel 按键仅 ENTER 和 BACK 会被 APK 消费
4. **Windows 专属** — scrcpy 嵌入逻辑仅支持 Windows HWND 操作

---

## 一、ADB 桥接层加固 (core/adb_bridge.py)

### 1.1 命令执行可靠性

**P0: install/push/uninstall 的返回值判断不健壮**
- 当前: `install()` 用 `'Success' in r.stdout` 字符串匹配，不同 ADB 版本输出格式不同
- 当前: `push()` 仅检查 `r.returncode == 0`，但不捕获 stderr
- 修正: 统一改为检查 returncode，同时在 stderr 有内容时记录警告

**P1: _cmd() 异常处理不完整**
- subprocess.run() 可能抛出 FileNotFoundError（ADB 不存在）、TimeoutExpired
- 修正: 在 _cmd() 中捕获并转为明确的 AdbError 自定义异常，上层调用方可统一处理

**P1: shell() 丢失 stderr**
- 当前 shell() 仅返回 stdout.strip()，排查问题时看不到错误信息
- 修正: shell() 返回 (stdout, stderr) 元组，或至少将 stderr 合并到返回值中

### 1.2 启动校验

**P1: 启动时不验证 ADB 可执行性**
- 当前 AdbBridge 构造时不检查 adb_path 是否存在、是否可执行
- 修正: 添加 `verify()` 方法（运行 `adb version`），MainWindow 启动时调用并显示错误

---

## 二、配置同步加固 (core/config_sync.py)

### 2.1 临时文件安全

**P1: 临时文件清理使用 try/finally 但无 finally 保护 os.unlink**
- 当前 `push()` 中 `os.unlink(tmp_path)` 在 finally 块中，但如果 `adb.shell()` 或 `adb.push()` 抛出异常，`os.unlink` 仍会执行，这是对的
- 但 NamedTemporaryFile 的 delete=False + 手动 unlink 不如直接用 `delete=True` 搭配上下文管理器
- 修正: 改用 `with tempfile.NamedTemporaryFile(..., delete=True) as f:` 不手动 unlink

### 2.2 配置校验

**P1: push 前不校验配置合法性**
- API URL 可以是空字符串、不是 URL 格式、缺少 https://
- API Key 可以是任意字符串
- 修正: 添加 ConfigValidator 工具函数，验证 URL 格式（http/https scheme）、Key 长度下限

### 2.3 pull 容错

**P2: pull() 用 cat 读取配置，权限受限时失败**
- 某些 Android 设备 /sdcard 权限模型可能阻止 `adb shell cat`
- 修正: cat 失败时回退到 `adb pull` 到临时文件再读取

---

## 三、APK 管理器修正 (core/apk_manager.py)

### 3.1 架构修正

**P0: install_only()/uninstall_only() 内建 WEOConfig() 丢失配置**
- _InstallThread 和 _UninstallThread 中创建 `ApkManager(self._adb, WEOConfig(), ...)` 
- 新 WEOConfig() 用的是默认值，丢失了 pkg_name、apk_filename 等
- 修正: 接收 ApkManager 实例而非重新构造

### 3.2 部署可靠性

**P1: force_stop 后无等待**
- `deploy()` 中 force_stop → install 之间没有等待，进程可能还在退出
- 修正: force_stop 后 `time.sleep(1.0)`

**P2: 部署步骤无重试**
- install 失败不会重试
- 修正: install 失败时重试一次（某些情况下第一次安装因残留进程失败）

---

## 四、UI 主窗口优化 (ui/main_window.py)

### 4.1 版本号一致

**P1: 启动闪屏硬编码版本号**
- `_make_splash()` 中写死 `v0.1.0` 而非用 `main.py` 的 VERSION 常量
- 修正: 在 main.py 导出 VERSION，闪屏使用它；同步更新至 v0.3.0

### 4.2 设备轮询优化

**P2: 每次 poll 都调用 load_config()**
- `_start_poll_worker()` 每次创建 PollWorker 时调用 `load_config()` 仅为了取 pkg_name
- pkg_name 是常量（`com.obwiler.weo`）
- 修正: 将 pkg_name 缓存为 MainWindow 属性，构造时读取一次

### 4.3 按键映射注释

**P2: _KEY_MAP 包含 APK 不再处理的按键**
- 上/下/左/右/Home/Power/Menu 这些键 APK v0.2.0 不再消费
- RingPanel 仍发送它们（发给系统层面，用于 Rokid 桌面导航）
- 修正: 添加注释标注哪些键被 WEO 消费（ENTER/BACK），哪些仅用于系统导航

### 4.4 KeyThread 串行状态保护

**P2: serial 备份/恢复在 QThread 中非原子**
- `_KeyThread.run()` 中先备份 `self._adb.serial`，执行 keyevent，再恢复
- 如果两个 KeyThread 同时运行，serial 可能被交错覆盖
- 修正: _KeyThread 构造时捕获 serial 快照，使用局部 AdbBridge 实例，不修改共享实例

---

## 五、配置面板增强 (ui/config_panel.py)

### 5.1 拉取时自动匹配服务商

**P1: _apply_config() 不更新 provider 下拉框**
- 从设备拉取配置后，URL 和 model 填入输入框，但 provider 下拉框不动
- 修正: 拉取后遍历 provider_list 匹配 URL 前缀，自动选中对应服务商

### 5.2 高级参数暴露

**P1: get_config_dict() 硬编码 userPrompt/maxTokens/timeoutMs**
- 用户无法调整请求超时、最大 token 数、用户提示词
- 修正: 添加可折叠的"高级设置"区域，暴露 userPrompt（QLineEdit）、maxTokens（QSpinBox）、timeoutMs（QSpinBox，单位秒）

### 5.3 Provider 切换不覆盖已编辑字段

**P1: _on_provider_changed 无条件覆盖 URL 和 model**
- 用户手动编辑了 URL，然后切换 provider 再切回来 → 编辑内容丢失
- 修正: 仅当 URL 输入框为空或等于上一个 provider 的 URL 时才自动填充

---

## 六、投屏面板加固 (ui/scrcpy_panel.py)

### 6.1 进程可靠性

**P1: _poll_for_window 轮询上限后无提示**
- 80 次 × 150ms = 12s 后静默失败
- 修正: 超时时给用户明确提示"无法找到 scrcpy 窗口，请确认 scrcpy 是否正常运行"

**P2: _find_sdl_hwnd 无备选窗口类名**
- 仅查找 SDL_app，如果 scrcpy 版本变更窗口类名则永远找不到
- 修正: 添加备选类名列表 `["SDL_app", "scrcpy", "scrcpy-win"]`

### 6.2 启动前置校验

**P1: start_scrcpy 不检查 exe 是否存在**
- 如果 scrcpy.exe 缺失，QProcess 静默失败
- 修正: 在 start_scrcpy 中预先 `Path(exe).exists()` 检查，失败时弹日志提示

---

## 七、APK 面板修正 (ui/apk_panel.py)

### 7.1 安装前检查

**P1: 无 APK 文件存在性检查**
- 如果 lib/apk/WEO-debug.apk 不存在，install 会失败但错误信息不明确
- 修正: _on_install 中检查 apk 文件路径，提前报错

### 7.2 安装进度

**P2: 安装期间无进度反馈**
- install 超时 120s，期间按钮禁用但无任何视觉反馈
- 修正: 安装期间显示不确定进度条（QProgressBar.setRange(0, 0)）

---

## 八、指环面板对齐 APK v0.2.0 (ui/ring_panel.py)

### 8.1 标注有效按键

**P2: 所有 7 个按键均发送，但 APK 仅消费 ENTER 和 BACK**
- 上下左右在 Rokid 桌面上仍有导航作用（系统层面）
- Home 触发系统回桌面
- 修正: 在按键上方添加分隔线和标签："→ WEO 响应"（ENTER、BACK）、"→ 系统导航"（其余）

---

## 九、其他修正

| # | 文件 | 问题 | 修正 |
|---|------|------|------|
| 9.1 | main.py | 闪屏版本号硬编码 | 改用 VERSION 常量 |
| 9.2 | main.py | 退出时 persistent_config 可能为 None | 添加 None 保护 |
| 9.3 | main_window.py | _DeployThread / _DiagThread 散落在主窗口文件 | 提取到 ui/deploy_threads.py |
| 9.4 | log_panel.py | verticalScrollBar 无谓的 if sb: 守卫 | 移除 |
| 9.5 | constants.py | WEOConfig.apk_filename 写死 debug | 改为可被外部 JSON 覆盖 |
| 9.6 | constants.py | THEME 写死 | 移到 resources/theme.json，load_config 时加载 |
| 9.7 | 全局 | PySide6 无异常处理器 | main.py 中添加 sys.excepthook 捕获 Qt 线程异常并记日志 |

---

## 十、执行顺序

| 步骤 | 内容 | 预计影响 | 文件数 |
|------|------|---------|--------|
| **P1** | ADB 桥接层加固 + 启动校验 | adb_bridge.py | 1 |
| **P2** | 配置同步加固 + 配置校验 | config_sync.py + 新建 config_validator.py | 2 |
| **P3** | APK 管理器修正 (install_only/uninstall_only) | apk_manager.py, apk_panel.py | 2 |
| **P4** | 主窗口优化 (版本号/轮询/KeyThread) | main.py, main_window.py | 2 |
| **P5** | 配置面板增强 (服务商匹配/高级参数/provider锁定) | config_panel.py | 1 |
| **P6** | 投屏面板加固 (窗口检测/scrcpy校验) | scrcpy_panel.py | 1 |
| **P7** | 指环面板标注 + APK安装检查 | ring_panel.py, apk_panel.py | 2 |
| **P8** | 代码整理 (线程提取/主题外置/异常处理) | main.py, main_window.py, constants.py | 3 |

---

## 十一、不做的

- ❌ 不改为多设备选择 — 当前场景单设备连接，复杂度不值
- ❌ 不接入 scrcpy 的 Java 绑定或 ffmpeg 方案 — 当前 SDL HWND 嵌入已够用
- ❌ 不添加自动更新检查 — 手动发布模式
- ❌ 不添加国际化 — 目标用户均为中文
- ❌ 不改窗口布局和视觉风格