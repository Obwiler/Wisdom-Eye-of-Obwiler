# WEO PC 工具 v1.0 架构重构与功能集成方案

> 制定日期: 2026-06-03
> 参考项目: AndroidWatch_ADB_ToolBox (tkinter, 100+ APK 应用商店)
> 目标: 架构现代化 + 功能集成 + UI 升级

---

## 零、设计哲学

**"眼镜管家，不止投屏"** — WEO PC 工具从单一配置面板升级为智能眼镜全生命周期管理平台。

三种操作模式自然共存：
- **WEO 核心流**: AI 配置 → 一键部署 → 投屏预览 → 指环遥控
- **设备管理流**: 仪表盘 → 文件管理 → 截图 → Shell → 应用商店
- **开发者流**: ADB 命令行 → 日志查看 → 批量操作

---

## 一、新架构总览

```
pc_tool/
├── main.py                          # 入口（精简到 30 行）
├── app/
│   ├── __init__.py
│   ├── application.py               # App 单例: 全局状态/设备信号/主题
│   └── theme.py                     # 主题引擎: JSON 驱动, 热切换
├── core/                            # 无 UI 依赖的纯逻辑层
│   ├── __init__.py
│   ├── adb_client.py                # ADB 命令客户端 (重写)
│   ├── device.py                    # 设备信息模型 + 发现/连接管理
│   ├── deploy.py                    # WEO 部署编排器
│   ├── config_sync.py               # WEO 配置推送/拉取
│   ├── file_mgr.py                  # 文件浏览/推送/拉取
│   ├── screenshot.py                # 截图捕获 (adb exec-out screencap -p)
│   ├── app_catalog.py               # 应用目录: JSON 驱动元数据 + 本地缓存
│   └── shell.py                     # 交互式 Shell 客户端
├── ui/                              # 所有 UI (纯展示+交互, 不含业务逻辑)
│   ├── __init__.py
│   ├── main_window.py               # 窗口框架: 导航栏 + 内容区 + 状态栏
│   ├── widgets/                     # 通用组件库
│   │   ├── __init__.py
│   │   ├── device_bar.py            # 顶部设备选择/连接状态条
│   │   ├── nav_rail.py              # 左侧图标导航栏
│   │   ├── log_panel.py             # 底部可折叠日志
│   │   ├── status_bar.py            # 底部状态栏
│   │   ├── card.py                  # 通用卡片容器
│   │   ├── icon_btn.py              # 图标按钮
│   │   └── toast.py                 # 浮动提示
│   ├── panels/                      # 功能面板（一个 tab 一个面板）
│   │   ├── __init__.py
│   │   ├── dashboard.py             # 设备仪表盘（首页）
│   │   ├── scrcpy.py                # 投屏面板
│   │   ├── weo_config.py            # WEO AI 配置
│   │   ├── weo_deploy.py            # WEO 一键部署
│   │   ├── app_store.py             # 手表应用商店
│   │   ├── files.py                 # 文件管理器
│   │   ├── screenshot_view.py       # 截图查看器
│   │   ├── shell_term.py            # ADB Shell 终端
│   │   └── ring.py                  # 指环遥控器
│   └── dialogs/
│       ├── __init__.py
│       └── settings.py              # 全局设置对话框
├── resources/
│   ├── theme.json                   # 主题配置
│   ├── providers.json               # AI 服务商
│   ├── app_catalog.json             # 应用商店元数据
│   └── icons/                       # SVG 图标集
├── lib/
│   ├── scrcpy/                      # scrcpy + adb 二进制
│   └── apk/                         # WEO APK 存放
└── requirements.txt
```

---

## 二、核心层设计 (core/)

### 2.1 AdbClient — 命令客户端重写

```
设计: 无状态函数式风格, 每次调用创建独立 subprocess
改进点:
  - 统一返回 (returncode, stdout, stderr) 三元组
  - 自定义 AdbError 异常 + 错误码枚举
  - 内置重试 (install/push 最多 2 次)
  - 构造函数校验 adb.exe 存在性
  - 支持 serial 参数每次调用覆盖
  - 超时按操作类型分级 (shell 15s / install 120s / push 30s)
```

### 2.2 Device — 设备信息模型

```
@dataclass DeviceInfo:
    serial: str
    model: str
    android_version: str
    sdk_level: int
    battery_level: int          # dumpsys battery
    battery_charging: bool
    storage_total: str          # df -h /sdcard
    storage_used: str
    ram_total: str              # free -m
    screen_resolution: str      # wm size
    screen_density: int         # wm density
    wifi_ssid: str
    weo_installed: bool
    weo_running: bool

class DeviceManager(QObject):
    signal device_connected(DeviceInfo)
    signal device_disconnected()
    
    - refresh() → DeviceInfo     # 全量刷新
    - quick_poll() → bool        # 仅检查在线状态
    - get_battery() → int
```

### 2.3 AppCatalog — 应用商店

```
JSON 元数据格式 (app_catalog.json):
{
  "categories": [
    {"id": "social", "name": "社交", "icon": "chat"},
    {"id": "music", "name": "音乐", "icon": "music"},
    ...
  ],
  "apps": [
    {
      "id": "qq_watch",
      "name": "QQ手表版",
      "category": "social",
      "screen": "both",
      "version": "1.0",
      "size_kb": 12345,
      "description": "QQ WearOS 适配版",
      "apk_path": "cache/QQ手表版/qq.apk",
      "icon_path": "cache/QQ手表版/icon.png"
    }
  ]
}

class AppCatalog:
    - load(json_path) → self
    - search(query) → list[AppMeta]
    - filter(category, screen_type) → list[AppMeta]
    - get_app(id) → AppMeta
```

### 2.4 FileManager — 文件管理

```
class FileManager:
    - list_dir(remote_path) → list[FileEntry]
    - push(local_path, remote_path) → bool
    - pull(remote_path, local_path) → bool
    - delete(remote_path) → bool
    - get_storage_info() → dict

FileEntry: name, path, is_dir, size, permissions, modified
```

### 2.5 Screenshot — 截图

```
class Screenshot:
    - capture() → QPixmap         # adb exec-out screencap -p → 解析 PNG → QPixmap
    - save(path) → bool
    - copy_to_clipboard()
```

---

## 三、UI 层设计

### 3.1 窗口框架

```
┌─────────────────────────────────────────────────────┐
│ [设备选择器 ▼]  RG_Glasses ●  |  Android 12  |  80% █ │  ← DeviceBar
├────────┬────────────────────────────────────────────┤
│  📊    │                                            │
│  📱    │         内容区 (QStackedWidget)             │
│  ⚙     │                                            │
│  📦    │   每个图标对应一个面板                       │
│  🛒    │                                            │
│  📁    │                                            │
│  📸    │                                            │
│  💻    │                                            │
│  🎮    │                                            │
│        │                                            │
├────────┴────────────────────────────────────────────┤
│ 📋 日志面板 (可折叠)                         🟢 已连接 │  ← LogPanel + StatusBar
└─────────────────────────────────────────────────────┘
```

### 3.2 导航栏 (NavRail)

8 个图标按钮，垂直排列：
1. 📊 **仪表盘** — 设备概览、快捷操作
2. 📱 **投屏** — scrcpy 画面
3. ⚙ **WEO 配置** — AI 配置 + 部署
4. 📦 **应用管理** — APK 安装/卸载
5. 🛒 **应用商店** — 手表应用浏览安装
6. 📁 **文件** — 设备文件管理
7. 📸 **截图** — 截图捕获/预览
8. 🎮 **指环** — 虚拟遥控器
9. 💻 **Shell** — ADB 命令行

（入口过多时合并：WEO 配置+部署合并为"WEO"，应用管理+商店合并为"应用"）

### 3.3 各面板设计要点

#### 仪表盘 (Dashboard)
```
┌──────────────────────────────┐
│ 设备信息                      │
│ 型号: RG_Glasses             │
│ 系统: Android 12 (SDK 31)    │
│ 存储: 12.3G / 32G (38%)     │
│ 内存: 1.2G / 4G (30%)       │
│ 电池: ████████░░ 80% 充电中   │
│ WiFi: MyWiFi-5G              │
│ 分辨率: 480×640 (240dpi)     │
├──────────────────────────────┤
│ 奥贝之眼状态                  │
│ ● 已安装 v0.2.0              │
│ ● 正在运行                   │
│ AI: DeepSeek / deepseek-chat │
├──────────────────────────────┤
│ 快捷操作                      │
│ [📸截图] [🔄重启] [📋日志]    │
│ [🔧无线ADB] [⏻关机]          │
└──────────────────────────────┘
```

#### 应用商店 (AppStore)
```
┌──────────────────────────────┐
│ 🔍 搜索...   [分类▼] [屏幕▼] │
├──────────────────────────────┤
│ ┌──────┐ ┌──────┐ ┌──────┐  │
│ │ icon │ │ icon │ │ icon │  │
│ │QQ手表│ │微信  │ │支付宝│  │
│ │12MB  │ │45MB  │ │38MB  │  │
│ │[安装]│ │[安装]│ │[安装]│  │
│ └──────┘ └──────┘ └──────┘  │
│ ┌──────┐ ┌──────┐ ┌──────┐  │
│ │ ...  │ │ ...  │ │ ...  │  │
│ └──────┘ └──────┘ └──────┘  │
└──────────────────────────────┘
```

特点：
- 网格卡片布局，3 列自适配
- 每张卡片：应用图标 + 名称 + 大小 + 安装按钮
- 安装中显示进度，安装完成显示"已安装"绿色标记
- 支持搜索（名称拼音首字母）
- 支持分类筛选 + 屏幕类型筛选（方屏/圆屏）

#### 文件管理器 (Files)
```
┌──────────────────────────────┐
│ 📁 /sdcard/          [⬆上传]│
├──────────────────────────────┤
│ 📁 Android/                  │
│ 📁 DCIM/                     │
│ 📁 Download/                 │
│ 📁 Music/                    │
│ 📁 Pictures/                 │
│   📁 WEO/                    │
│     🖼 IMG_20260603_1730.jpg │
│     🖼 IMG_20260603_1725.jpg │
│ 📄 weo_config.json    347B   │
└──────────────────────────────┘
```

双击文件夹进入，双击文件下载/预览图片。

#### Shell 终端 (Shell)
```
┌──────────────────────────────┐
│ 快捷命令: [dumpsys battery]  │
│          [pm list packages]  │
│          [logcat -s WEO]     │
│          [wm size]           │
├──────────────────────────────┤
│ $ ls /sdcard/                │
│ Android  DCIM  Download ...  │
│ $ █                          │
└──────────────────────────────┘
```

单行命令执行，保留历史（上下箭头）。非交互式（不支持 vi/top 等 curses 程序）。

---

## 四、主题系统

### 4.1 theme.json 结构

```json
{
  "name": "WEO Dark",
  "colors": {
    "bg_primary": "#0A0A0A",
    "bg_secondary": "#141414",
    "bg_card": "#1A1A1A",
    "accent": "#00FF00",
    "accent_dim": "#00CC00",
    "accent_subtle": "rgba(0,255,0,0.08)",
    "text_primary": "#E0E0E0",
    "text_secondary": "#888888",
    "text_dim": "#555555",
    "border": "rgba(0,255,0,0.15)",
    "danger": "#FF4444",
    "warning": "#FFAA00",
    "success": "#00CC66"
  },
  "fonts": {
    "main": "Segoe UI",
    "mono": "Cascadia Code",
    "size_xs": 10,
    "size_sm": 11,
    "size_md": 13,
    "size_lg": 16,
    "size_xl": 22
  },
  "spacing": {
    "xs": 2, "sm": 4, "md": 8, "lg": 16, "xl": 24
  },
  "radius": {
    "sm": 4, "md": 6, "lg": 8
  }
}
```

### 4.2 设计语言

- **暗色基底** — 深黑底色 (#0A0A0A)，层级用不同灰度区分
- **WEO 绿点缀** — 仅用于强调元素：导航选中态、主要按钮、状态指示器、标题
- **克制配色** — 卡片/面板用 #141414 / #1A1A1A，不用花哨渐变
- **边界分隔** — 极细的半透明绿边框 (rgba(0,255,0,0.10))
- **圆角克制** — 按钮 4px，卡片 6px，面板 8px
- **6 列网格** — 应用商店卡片自适应 2/3/4 列

---

## 五、集成功能清单（对标参考工具）

| 参考工具功能 | WEO 集成方式 | 优化点 |
|-------------|-------------|--------|
| 应用商店 (100+ APK) | AppStorePanel + app_catalog.json | 分类/搜索/屏幕筛选；JSON 驱动元数据；安装状态追踪 |
| 截图捕获 | Screenshot + ScreenshotPanel | 内嵌预览 + 一键保存；支持拖拽到文件夹 |
| 文件管理 | FileManager + FilesPanel | 树形浏览 + 双击操作；上传进度条 |
| ADB Shell | ShellClient + ShellPanel | 快捷命令按钮；历史记录；只读安全模式 |
| 设备信息 | DeviceManager + Dashboard | 聚合展示 + 实时刷新；WEO 专属状态行 |
| 无线 ADB 记忆 | DeviceManager 持久化 | JSON 存储已配对设备；一键重连 |
| Fastboot 模式 | 不集成 | WEO 场景不需要刷机 |
| ADB 触摸模拟 | 不集成 | 已有指环面板 + 物理按键 |

### WEO 特色功能（参考工具没有的）

| 功能 | 说明 |
|------|------|
| WEO 一键部署 | APK 安装 + 配置推送 + 应用启动，全自动 |
| AI 配置面板 | 多服务商预设 + System Prompt 模板 + 温度调节 |
| 配置拉取/推送 | 从设备读取/写入 weo_config.json，实时生效 |
| 投屏嵌入 | scrcpy SDL 窗口嵌入 Qt，不需额外窗口 |
| 指环遥控 | 虚拟方向键 + 确认/返回，通过 ADB 转发 |
| 设备诊断 | 一键获取设备信息 + WEO 状态 + API 配置摘要 |

---

## 六、从参考工具迁移 APK 缓存

参考工具的 `cache/` 目录结构：
```
cache/
├── QQ手表版/
│   ├── qq.apk
│   └── icon.png
├── 微信WearOS手表版/
│   ├── wechat.apk
│   └── icon.png
...
```

迁移方案：
1. 在 `pc_tool/lib/apk_cache/` 下建立相同结构
2. 提取每个 APK 的元数据（包名、版本号、大小）通过 aapt 自动生成
3. 生成 `app_catalog.json` 作为索引
4. 图标统一处理为 72×72 PNG

---

## 七、实施路线图

### Phase 1: 架构搭建（文件数: 8）
1. 创建新目录结构
2. `app/application.py` — App 单例 + 信号总线
3. `app/theme.py` — 主题引擎 + JSON 加载
4. `core/adb_client.py` — 重写 AdbClient
5. `core/device.py` — DeviceManager + DeviceInfo
6. `ui/main_window.py` — 新窗口框架 + NavRail
7. `ui/widgets/` — 基础组件（DeviceBar, Card, IconBtn, LogPanel）
8. `resources/theme.json` — 主题文件

### Phase 2: 核心面板移植（文件数: 6）
1. `ui/panels/dashboard.py` — 仪表盘（新）
2. `ui/panels/scrcpy.py` — 投屏（移植）
3. `ui/panels/weo_config.py` — AI 配置（移植增强）
4. `ui/panels/weo_deploy.py` — 一键部署（从 config 中拆分）
5. `ui/panels/ring.py` — 指环（移植）
6. `core/deploy.py` + `core/config_sync.py` — 核心逻辑移植

### Phase 3: 新功能集成（文件数: 7）
1. `core/app_catalog.py` — 应用目录
2. `ui/panels/app_store.py` — 应用商店面板
3. `core/file_mgr.py` — 文件管理
4. `ui/panels/files.py` — 文件管理面板
5. `core/screenshot.py` — 截图
6. `ui/panels/screenshot_view.py` — 截图面板
7. `ui/panels/shell_term.py` — Shell 终端

### Phase 4: 打磨（文件数: 3）
1. `ui/dialogs/settings.py` — 全局设置
2. `resources/app_catalog.json` — APK 元数据生成
3. 主题热切换 + 持久化用户偏好

---

## 八、参考的开源项目

| 项目 | 参考点 |
|------|--------|
| **scrcpy** (Genymobile) | 投屏实现参考；命令行参数设计 |
| **QtScrcpy** (barry-ran) | Qt 嵌入 scrcpy 的实现模式 |
| **WearOS Toolbox** | 手表应用管理 UX 参考 |
| **adb-ui** (ashishb) | Web ADB 面板的交互设计 |
| **guiscrcpy** (srevinsaju) | PySide2 投屏 UI 布局参考 |

---

## 九、技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| GUI 框架 | PySide6 (Qt 6) | 已在使用，组件丰富，QSS 可控 |
| 打包工具 | PyInstaller | 生成单文件 exe |
| 图标库 | Lucide (lucide.dev) | 简洁现代，SVG 格式，2000+ 图标 |
| 日志组件 | 自研 LogPanel | 不需要第三方，100 行足够 |
| 终端组件 | QPlainTextEdit | 不需要 QTermWidget 的重量 |
| 配置格式 | JSON | 已有基础，人可读，工具链简单 |
| APK 扫描 | aapt (Android SDK) | 提取包名/版本号/图标 |

---

## 十、不做

- ❌ 不做 Fastboot 刷机 — WEO 场景不需要
- ❌ 不做多设备同时管理 — 智能眼镜场景单设备
- ❌ 不做 ADB over TCP 自动配置 — 手动开启已足够
- ❌ 不做应用商店的 APK 自动更新检查 — 离线场景
- ❌ 不做插件系统 — Phase 1-4 的模块化已经足够
- ❌ 不换 Electron/Web 技术栈 — PySide6 胜任且包体积小