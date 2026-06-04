# 奥贝之眼 PC 工具 — UX 重构任务计划书

**项目路径**: `pc_tool/`
**目标**: 左侧 iPod 式投屏控制台 + 右侧工具箱布局
**版本**: v2.0.0

---

## 设计目标

```
┌──────────────────────────────────────────────────────┐
│ DeviceBar (设备型号 | 电池 | WEO 版本 | 存储)          │
├─────────────────┬────────────────────────────────────┤
│                 │  Tab Bar: [WEO管理] [文件] [Shell]  │
│   Scrcpy        │           [截图] [设置]             │
│   Mirror        ├────────────────────────────────────┤
│   (480x640)    │                                     │
│   aspect-ratio  │  操作面板                            │
│   自动缩放       │  (Tab 切换)                         │
│                 │                                     │
│  ┌───┬───┬───┐ │                                     │
│  │   │ ↑ │   │ │                                     │
│  ├───┼───┼───┤ │                                     │
│  │ ← │ ● │ → │ │                                     │
│  ├───┼───┼───┤ │                                     │
│  │   │ ↓ │   │ │                                     │
│  └───┴───┴───┘ │                                     │
│  [返回] [HOME]  │                                     │
│                 │                                     │
├─────────────────┴────────────────────────────────────┤
│ LogPanel (可折叠)                                      │
├──────────────────────────────────────────────────────┤
│ StatusBar                                             │
└──────────────────────────────────────────────────────┘
```

**核心原则**:
- 投屏画面始终可见，D-Pad 在投屏正下方，形成完整的"虚拟眼镜操作台"
- 右侧工具箱独立切换，操作时不遮挡投屏
- 去除非 WEO 相关功能，工具定位聚焦

---

## 任务拆解

### T1: 删除应用商店及其依赖
- **文件**: `ui/panels/app_store.py` → 删除
- **文件**: `core/app_catalog.py` → 删除  
- **文件**: `resources/app_catalog.json` → 删除
- **目录**: `lib/apk_cache/` → 删除 (126 个第三方应用缓存 ~95MB)
- **文件**: `ui/panels/__init__.py` → 移除 AppStorePanel 导出
- **文件**: `ui/main_window.py` → 移除 app_store 引用和 PANEL_APP_STORE 常量
- **验证**: 模块导入无误，exe 体积降至 ~25MB

### T2: 重构主窗口布局
- **文件**: `ui/main_window.py` — 核心改动
  - 移除 NavRail（56px 导航栏）
  - 主体分为左右两栏:
    - 左侧: 投屏面板 + 指环 D-Pad 面板 (垂直排列，不切换)
    - 右侧: 水平 TabBar + QStackedWidget
  - 左右比例约 40:60，支持拖拽分隔条
  - 指环遥控面板从 QStackedWidget 中取出，固定嵌入左侧底部
  - DeviceBar 扩展为承载完整设备信息 (型号/电池/安卓版本/WEO 状态/存储)
  - LogPanel 可折叠/展开
- **新增**: `ui/widgets/device_bar.py` — 扩展信息展示
- **文件**: `ui/widgets/nav_rail.py` → 归档不删除

### T3: 合并 WEO 配置 + 部署为单一面板
- **新增**: `ui/panels/weo_manager.py` — 合并 weo_config + weo_deploy
  - 上半区: 配置表单 (API Key / 模型 / 参数)
  - 下半区: 部署区域 (安装状态 / APK 选择 / 部署按钮 / 进度条)
  - 部署流程扩展自动 keyevent:
    1. `adb install -r` 安装 APK
    2. `adb shell am start` 启动 WEO
    3. 如弹出权限对话框 → `adb shell input keyevent 66` (确认)
    4. 轮询 `pidof com.obwiler.weo` 验证运行状态
- **文件**: `ui/panels/weo_config.py` → 归档
- **文件**: `ui/panels/weo_deploy.py` → 归档

### T4: 右侧 Tab 面板调整
- 最终 Tab 列表 (5 个):
  1. **WEO 管理** (新的 weo_manager)
  2. **文件管理** (files, 现有)
  3. **Shell** (shell_term, 现有)
  4. **截图** (screenshot_view, 现有)
  5. **设置** (settings dialog 改为内联面板)
- Dashboard 面板: 信息合并到 DeviceBar，面板本身删除
- 指环遥控: 合并到左侧固定区域，不再作为独立 Tab
- 设置: 从 QDialog 改为右侧内联面板 `ui/panels/settings_panel.py`

### T5: PyInstaller 打包更新
- **文件**: `WEO-PC工具.spec` — 移除 apk_cache、app_catalog 数据
- 预期体积: ~25MB (当前 120MB)
- **验证**: 构建 → 启动 → 窗口可见

### T6: 全量回归验证
- 27 个模块全部导入通过
- 主窗口构造无误，左右布局正确渲染
- 设备连接 → 投屏自动适配分辨率
- D-Pad 按键发送正常
- WEO 管理面板: 配置拉取/推送/部署正常
- 文件管理、Shell、截图功能正常
- PyInstaller exe 生成并启动验证

---

## 改动统计

| 类型 | 文件数 |
|---|---|
| 新增 | 2 (`weo_manager.py`, `settings_panel.py`) |
| 修改 | 3 (`main_window.py`, `device_bar.py`, `WEO-PC工具.spec`) |
| 删除/归档 | 7 (`app_store.py`, `app_catalog.py`, `app_catalog.json`, `weo_config.py`, `weo_deploy.py`, `nav_rail.py`, `dashboard.py`) |
| 删除目录 | 1 (`lib/apk_cache/`) |

**净效果**: 删 > 增，代码量下降，exe 从 120MB → ~25MB

---

## 验收标准

1. 投屏画面在左侧始终可见，D-Pad 在正下方可点击
2. 右侧 5 个 Tab 正常切换，不遮挡左侧
3. 设备连接后投屏自动启动 (或一键启动)
4. 部署流程全自动 (无需碰眼镜)
5. 应用商店及 128 个缓存完全移除
6. `dist/WEO-PC工具.exe` 正常启动，窗口可见
