"""APK Manager panel --- install / uninstall / manage APK lifecycle."""

import shutil
from datetime import datetime
from pathlib import Path

from PySide6.QtCore import Signal, Qt, QThread
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QScrollArea, QProgressBar, QFrame, QTextEdit, QFileDialog,
)

from app.application import App
from core.deploy import deploy, check_status, install_only, uninstall_only


class _DeployThread(QThread):
    finished = Signal(bool, str)

    def __init__(self, adb_client, config, apk_path: Path, config_dict: dict,
                 serial: str | None, parent=None):
        super().__init__(parent)
        self._adb = adb_client
        self._config = config
        self._apk_path = apk_path
        self._config_dict = config_dict
        self._serial = serial

    def run(self):
        ok, msg = deploy(self._adb, self._config, self._apk_path,
                         config_dict=self._config_dict, serial=self._serial)
        self.finished.emit(ok, msg)


class _InstallThread(QThread):
    finished = Signal(bool, str)

    def __init__(self, adb_client, apk_path: str, serial: str | None, parent=None):
        super().__init__(parent)
        self._adb = adb_client
        self._apk_path = apk_path
        self._serial = serial

    def run(self):
        ok, msg = install_only(self._adb, self._apk_path, serial=self._serial)
        self.finished.emit(ok, msg)


class _UninstallThread(QThread):
    finished = Signal(bool, str)

    def __init__(self, adb_client, pkg: str, serial: str | None, parent=None):
        super().__init__(parent)
        self._adb = adb_client
        self._pkg = pkg
        self._serial = serial

    def run(self):
        ok, msg = uninstall_only(self._adb, self._pkg, serial=self._serial)
        self.finished.emit(ok, msg)


class ApkManagerPanel(QWidget):
    """APK lifecycle: install latest, uninstall, manage APK files, diagnostics."""

    log_line = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._thread: QThread | None = None
        self._busy = False
        self._setup_ui()
        self._refresh_all()
        self._connect_device_signals()

    def _setup_ui(self):
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        inner = QWidget()
        layout = QVBoxLayout(inner)
        layout.setSpacing(8)

        # --- device status ---
        layout.addWidget(self._make_section("\u8BBE\u5907\u72B6\u6001"))
        self._status_label = QLabel("\u68C0\u6D4B\u4E2D...")
        self._status_label.setObjectName("device_status")
        layout.addWidget(self._status_label)

        # --- APK info ---
        layout.addWidget(self._make_section("APK \u4FE1\u606F"))
        self._apk_label = QLabel("")
        self._apk_label.setObjectName("apk_info")
        self._apk_label.setWordWrap(True)
        layout.addWidget(self._apk_label)

        # --- Build output ---
        layout.addWidget(self._make_section("\u6784\u5EFA\u8F93\u51FA"))
        self._build_label = QLabel("")
        self._build_label.setObjectName("build_info")
        self._build_label.setWordWrap(True)
        layout.addWidget(self._build_label)

        # --- actions ---
        layout.addSpacing(4)

        row1 = QHBoxLayout()
        self._install_btn = QPushButton("\u26A1 \u4E00\u952E\u5B89\u88C5\u6700\u65B0\u7248\u672C")
        self._install_btn.setMinimumHeight(40)
        self._install_btn.setToolTip("\u4ECE\u6784\u5EFA\u76EE\u5F55\u67E5\u627E\u6700\u65B0 APK \u5E76\u5B89\u88C5\u5230\u773C\u955C\u8BBE\u5907")
        self._install_btn.clicked.connect(self._on_install_latest)

        self._uninstall_btn = QPushButton("\U0001F5D1 \u4E00\u952E\u5378\u8F7D\u65E7\u7248\u672C")
        self._uninstall_btn.setMinimumHeight(40)
        self._uninstall_btn.setToolTip("\u4ECE\u773C\u955C\u8BBE\u5907\u4E0A\u5378\u8F7D WEO \u5E94\u7528")
        self._uninstall_btn.clicked.connect(self._on_uninstall_app)

        row1.addWidget(self._install_btn)
        row1.addWidget(self._uninstall_btn)
        layout.addLayout(row1)

        row2 = QHBoxLayout()
        self._pick_btn = QPushButton("\U0001F4C1 \u914D\u7F6E APK")
        self._pick_btn.setToolTip("\u6D4F\u89C8\u9009\u62E9 APK \u6587\u4EF6\u5E76\u590D\u5236\u5230\u90E8\u7F72\u76EE\u5F55")
        self._pick_btn.clicked.connect(self._on_pick_apk)

        self._deploy_btn = QPushButton("\U0001F680 \u5B8C\u6574\u90E8\u7F72")
        self._deploy_btn.setToolTip("\u505C\u6B62\u2192\u5B89\u88C5\u2192\u63A8\u9001\u914D\u7F6E\u2192\u542F\u52A8")
        self._deploy_btn.clicked.connect(self._on_deploy)

        self._diag_btn = QPushButton("\U0001F4F1 \u8BCA\u65AD")
        self._diag_btn.setToolTip("\u67E5\u770B\u8BBE\u5907\u4FE1\u606F\u53CA\u5E94\u7528\u5B89\u88C5\u72B6\u6001")
        self._diag_btn.clicked.connect(self._on_diag)

        row2.addWidget(self._pick_btn)
        row2.addWidget(self._deploy_btn)
        row2.addWidget(self._diag_btn)
        layout.addLayout(row2)

        self._progress = QProgressBar()
        self._progress.setRange(0, 0)
        self._progress.setVisible(False)
        self._progress.setMaximumHeight(6)
        layout.addWidget(self._progress)

        self._output = QTextEdit()
        self._output.setReadOnly(True)
        self._output.setMinimumHeight(120)
        self._output.setPlaceholderText("\u64CD\u4F5C\u65E5\u5FD7...")
        layout.addWidget(self._output, 1)

        layout.addStretch()
        scroll.setWidget(inner)

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.addWidget(scroll)

    def _connect_device_signals(self):
        """Auto-refresh when device connects or disconnects."""
        dm = self._app.device_manager
        dm.device_connected.connect(self._on_device_event)
        dm.device_disconnected.connect(self._on_device_event)

    def _on_device_event(self, *args):
        self._refresh_all()

    def _make_section(self, text: str) -> QLabel:
        lbl = QLabel(text)
        lbl.setObjectName("section_label")
        return lbl

    def _current_serial(self) -> str | None:
        dm = self._app.device_manager
        if dm.is_connected and dm.current:
            return dm.current.serial
        return None

    def _apk_target_path(self) -> Path:
        return self._app.external_path("lib", "apk", self._app.config.apk_filename)

    def _find_build_apk(self) -> Path | None:
        """Find the most recent APK in the build output directory."""
        build_dir = self._app.resolve_path("..", "apk", "build", "outputs", "apk", "debug")
        if not build_dir.exists():
            return None
        apks = sorted(build_dir.glob("*.apk"), key=lambda p: p.stat().st_mtime, reverse=True)
        return apks[0] if apks else None

    def _refresh_all(self):
        self._refresh_status()
        self._refresh_apk_info()
        self._refresh_build_info()
        self._refresh_buttons()

    def _refresh_buttons(self):
        connected = self._current_serial() is not None
        apk_ready = self._apk_target_path().exists()
        self._install_btn.setEnabled(connected)
        self._uninstall_btn.setEnabled(connected)
        self._deploy_btn.setEnabled(connected and apk_ready)
        self._diag_btn.setEnabled(connected)

    def _refresh_status(self):
        serial = self._current_serial()
        if not serial:
            self._status_label.setText("\u274C \u672A\u8FDE\u63A5\u8BBE\u5907")
            return
        try:
            status = check_status(self._app.adb_client, self._app.config, serial=serial)
            self._status_label.setText(f"\u2705 {status}")
        except Exception as e:
            self._status_label.setText(f"\u274C \u72B6\u6001\u68C0\u6D4B\u5931\u8D25: {e}")

    def _refresh_apk_info(self):
        p = self._apk_target_path()
        if p.exists():
            stat = p.stat()
            size_mb = stat.st_size / (1024 * 1024)
            mtime = datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M")
            self._apk_label.setText(f"\u2714 \u5DF2\u914D\u7F6E  |  {size_mb:.1f} MB  |  {mtime}\n{p}")
        else:
            self._apk_label.setText("\u26A0 \u672A\u627E\u5230 APK\n\u8BF7\u4F7F\u7528\u300C\u914D\u7F6E APK\u300D\u6216\u300C\u4E00\u952E\u5B89\u88C5\u6700\u65B0\u7248\u672C\u300D")

    def _refresh_build_info(self):
        apk = self._find_build_apk()
        if apk:
            stat = apk.stat()
            size_mb = stat.st_size / (1024 * 1024)
            mtime = datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M")
            self._build_label.setText(f"\u2714 \u6700\u65B0\u6784\u5EFA  |  {size_mb:.1f} MB  |  {mtime}\n{apk}")
        else:
            self._build_label.setText("\u26A0 \u672A\u627E\u5230\u6784\u5EFA\u8F93\u51FA\n\u8BF7\u5148\u5728 Android Studio \u4E2D\u6784\u5EFA\u9879\u76EE")

    def _on_install_latest(self):
        """Find latest build APK, copy to deploy dir, install to device."""
        if self._busy:
            return
        serial = self._current_serial()
        if not serial:
            self._output.append("❌ 无设备连接")
            return

        build_apk = self._find_build_apk()
        apk_to_install = None

        if build_apk:
            dst = self._apk_target_path()
            try:
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(str(build_apk), str(dst))
                size_mb = build_apk.stat().st_size / (1024 * 1024)
                self._output.append(f"✔ 从构建目录复制: {dst.name} ({size_mb:.1f} MB)")
                apk_to_install = dst
            except OSError as e:
                self._output.append(f"❌ 复制失败: {e}")
                return
        else:
            configured = self._apk_target_path()
            if configured.exists():
                self._output.append(f"✔ 使用已配置 APK: {configured.name}")
                apk_to_install = configured
            else:
                self._output.append("❌ 未找到 APK")
                self._output.append("   方法 1: 在 Android Studio 中构建项目后点击「一键安装」")
                self._output.append("   方法 2: 点击「配置 APK」手动选择 APK 文件")
                return

        self._refresh_apk_info()
        self._start_task("install", apk_to_install, serial)
    def _on_uninstall_app(self):
        serial = self._current_serial()
        if not serial:
            self._output.append("\u274C \u65E0\u8BBE\u5907\u8FDE\u63A5")
            return
        self._start_task("uninstall", None, serial)

    def _on_pick_apk(self):
        apk_build_dir = self._app.resolve_path("..", "apk", "build", "outputs", "apk", "debug")
        start_dir = str(apk_build_dir.resolve()) if apk_build_dir.exists() else ""

        path, _ = QFileDialog.getOpenFileName(
            self, "\u9009\u62E9 APK \u6587\u4EF6",
            start_dir,
            "APK Files (*.apk);;All Files (*)",
        )
        if not path:
            return

        src = Path(path).resolve()
        dst = self._apk_target_path().resolve()

        if src == dst:
            self._output.append(f"\u2714 APK \u5DF2\u5C31\u4F4D: {dst.name}")
            self._refresh_apk_info()
            self._refresh_buttons()
            return

        try:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(str(src), str(dst))
            size_mb = src.stat().st_size / (1024 * 1024)
            self._output.append(f"\u2714 APK \u5DF2\u590D\u5236: {dst.name} ({size_mb:.1f} MB)")
            self._refresh_apk_info()
            self._refresh_buttons()
            self.log_line.emit(f"[APK] \u5DF2\u914D\u7F6E: {dst.name}")
        except OSError as e:
            self._output.append(f"\u274C \u590D\u5236\u5931\u8D25: {e}")

    def _on_deploy(self):
        serial = self._current_serial()
        if not serial:
            self._output.append("\u274C \u65E0\u8BBE\u5907\u8FDE\u63A5")
            return

        apk_path = self._apk_target_path()
        if not apk_path.exists():
            self._output.append(f"\u274C APK \u4E0D\u5B58\u5728: {apk_path}")
            return

        saved = self._app.load_state("weo_config", {})
        self._start_task("deploy", apk_path, serial, config_dict=saved)

    def _on_diag(self):
        serial = self._current_serial()
        if not serial:
            self._output.append("\u274C \u65E0\u8BBE\u5907\u8FDE\u63A5")
            return
        try:
            adb = self._app.adb_client
            info = adb.get_device_info(serial=serial)
            self._output.append(f"\U0001F4F1 \u8BBE\u5907: {info.get('model', '?')} | Android {info.get('android', '?')}")
            self._output.append(f"   \u5E8F\u5217\u53F7: {serial}")
            self._output.append(f"   \u5E94\u7528: {check_status(adb, self._app.config, serial=serial)}")
        except Exception as e:
            self._output.append(f"\u274C \u8BCA\u65AD\u5931\u8D25: {e}")

    def _start_task(self, task: str, apk_path: Path | None, serial: str,
                    config_dict: dict | None = None):
        if self._thread and self._thread.isRunning():
            return

        self._busy = True
        self._set_buttons_enabled(False)
        self._progress.setVisible(True)

        if task == "install":
            self._output.append(f"\u23F3 \u6B63\u5728\u5B89\u88C5: {apk_path.name} ...")
            self._thread = _InstallThread(self._app.adb_client, str(apk_path), serial, self)
            self._thread.finished.connect(self._on_install_done)
        elif task == "uninstall":
            self._output.append("\u23F3 \u6B63\u5728\u5378\u8F7D\u773C\u955C\u7AEF\u5E94\u7528...")
            self._thread = _UninstallThread(self._app.adb_client, self._app.config.pkg_name, serial, self)
            self._thread.finished.connect(self._on_uninstall_done)
        elif task == "deploy":
            self._output.append("\u23F3 \u6B63\u5728\u5B8C\u6574\u90E8\u7F72...")
            self._thread = _DeployThread(self._app.adb_client, self._app.config, apk_path,
                                         config_dict or {}, serial, self)
            self._thread.finished.connect(self._on_deploy_done)

        self._thread.finished.connect(self._thread.deleteLater)
        self._thread.start()

    def _task_done(self, ok: bool, msg: str, task_name: str):
        self._thread = None
        self._progress.setVisible(False)
        self._busy = False
        self._set_buttons_enabled(True)
        icon = "\u2714" if ok else "\u274C"
        self._output.append(f"{icon} {msg}")
        self.log_line.emit(f"[APK] {icon} {task_name}: {msg}")
        self._refresh_status()
        self._refresh_buttons()

    def _on_install_done(self, ok: bool, msg: str):
        self._task_done(ok, msg, "\u5B89\u88C5")

    def _on_uninstall_done(self, ok: bool, msg: str):
        self._task_done(ok, msg, "\u5378\u8F7D")

    def _on_deploy_done(self, ok: bool, msg: str):
        self._task_done(ok, msg, "\u90E8\u7F72")

    def _set_buttons_enabled(self, enabled: bool):
        self._install_btn.setEnabled(enabled)
        self._uninstall_btn.setEnabled(enabled)
        self._pick_btn.setEnabled(enabled)
        self._deploy_btn.setEnabled(enabled)
        self._diag_btn.setEnabled(enabled)
