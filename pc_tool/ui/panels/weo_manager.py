"""WEO Manager panel — merged AI config + one-click deploy."""

import json
import shutil
from datetime import datetime
from pathlib import Path

from PySide6.QtCore import Signal, Qt, QThread
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QLineEdit, QComboBox, QTextEdit, QSlider, QScrollArea, QSpinBox,
    QProgressBar, QFrame, QFileDialog,
)

from app.application import App
from core.config_sync import ConfigSync
from core.deploy import deploy, check_status, uninstall_only
from urllib.parse import urlparse

class _PushPullThread(QThread):
    finished = Signal(bool, str, object)

    def __init__(self, sync: ConfigSync, action: str, serial: str | None = None,
                 config_dict: dict | None = None, parent=None):
        super().__init__(parent)
        self._sync = sync
        self._action = action
        self._serial = serial
        self._cfg = config_dict

    def run(self):
        if self._action == "push":
            ok = self._sync.push(self._cfg, serial=self._serial)
            self.finished.emit(ok, "\u63A8\u9001" + ("\u6210\u529F" if ok else "\u5931\u8D25"), None)
        else:
            cfg = self._sync.pull(serial=self._serial)
            if cfg:
                self.finished.emit(True, "\u5DF2\u62C9\u53D6", cfg)
            else:
                self.finished.emit(False, "\u8BBE\u5907\u4E0A\u65E0\u914D\u7F6E\u6587\u4EF6", None)


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
        ok, msg = deploy(
            self._adb, self._config, self._apk_path,
            config_dict=self._config_dict, serial=self._serial,
        )
        self.finished.emit(ok, msg)

class WeoManagerPanel(QWidget):
    """AI provider config + system prompt + push/pull + one-click deploy."""

    log_line = Signal(str)
    _PERSIST_KEY = "weo_config"

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._providers = self._load_providers()
        self._provider_list = self._providers.get("providers", [])
        self._presets = self._providers.get("presets", {})
        self._thread: QThread | None = None
        self._prev_provider_url = ""
        self._setup_ui()
        self._connect_persistence()
        self._load_saved_config()
        self._refresh_status()

    @staticmethod
    def _load_providers() -> dict:
        path = App.instance().resolve_path("resources", "providers.json")
        if path.exists():
            try:
                with open(path, "r", encoding="utf-8-sig") as f:
                    return json.load(f)
            except (json.JSONDecodeError, OSError):
                pass
        return {"providers": [], "presets": {}}

    def _setup_ui(self):
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        inner = QWidget()
        layout = QVBoxLayout(inner)
        layout.setSpacing(6)

        layout.addWidget(self._make_label("AI \u670D\u52A1\u5546"))
        self._provider_combo = QComboBox()
        self._provider_combo.addItems([p["name"] for p in self._provider_list])
        self._provider_combo.currentIndexChanged.connect(self._on_provider_changed)
        layout.addWidget(self._provider_combo)

        layout.addWidget(self._make_label("API \u5730\u5740"))
        self._url_input = QLineEdit()
        self._url_input.setPlaceholderText("https://api.deepseek.com/v1")
        layout.addWidget(self._url_input)

        layout.addWidget(self._make_label("API Key"))
        key_row = QHBoxLayout()
        self._key_input = QLineEdit()
        self._key_input.setEchoMode(QLineEdit.EchoMode.Password)
        self._key_input.setPlaceholderText("sk-...")
        self._key_eye = QPushButton("\U0001F441")
        self._key_eye.setFixedWidth(32)
        self._key_eye.clicked.connect(self._toggle_key_visibility)
        key_row.addWidget(self._key_input)
        key_row.addWidget(self._key_eye)
        layout.addLayout(key_row)

        layout.addWidget(self._make_label("\u6A21\u578B\u540D\u79F0"))
        self._model_input = QLineEdit()
        self._model_input.setPlaceholderText("deepseek-chat")
        layout.addWidget(self._model_input)

        layout.addWidget(self._make_label("System Prompt"))
        self._prompt_edit = QTextEdit()
        self._prompt_edit.setMaximumHeight(80)
        layout.addWidget(self._prompt_edit)

        preset_row = QHBoxLayout()
        for name, text in self._presets.items():
            btn = QPushButton(name)
            btn.setFixedWidth(56)
            btn.clicked.connect(lambda checked, t=text: self._prompt_edit.setPlainText(t))
            preset_row.addWidget(btn)
        preset_row.addStretch()
        layout.addLayout(preset_row)

        # temperature
        param_row = QHBoxLayout()
        param_row.addWidget(self._make_label("Temperature"))
        self._temp_slider = QSlider(Qt.Orientation.Horizontal)
        self._temp_slider.setRange(0, 100)
        self._temp_slider.setValue(30)
        param_row.addWidget(self._temp_slider)
        self._temp_label = QLabel("0.30")
        param_row.addWidget(self._temp_label)
        self._temp_slider.valueChanged.connect(lambda v: self._temp_label.setText(f"{v/100:.2f}"))
        layout.addLayout(param_row)

        # advanced — collapsible
        self._adv_toggle = QPushButton("\u25BC \u9AD8\u7EA7\u8BBE\u7F6E")
        self._adv_toggle.clicked.connect(self._toggle_advanced)
        layout.addWidget(self._adv_toggle)

        self._adv_area = QWidget()
        adv_layout = QVBoxLayout(self._adv_area)
        adv_layout.setContentsMargins(0, 0, 0, 0)
        adv_layout.setSpacing(6)

        row1 = QHBoxLayout()
        row1.addWidget(QLabel("maxTokens"))
        self._max_tokens = QSpinBox()
        self._max_tokens.setRange(100, 8192)
        self._max_tokens.setValue(1500)
        row1.addWidget(self._max_tokens)
        row1.addStretch()
        adv_layout.addLayout(row1)

        row2 = QHBoxLayout()
        row2.addWidget(QLabel("timeoutMs"))
        self._timeout_ms = QSpinBox()
        self._timeout_ms.setRange(5000, 120000)
        self._timeout_ms.setSingleStep(5000)
        self._timeout_ms.setValue(30000)
        self._timeout_ms.setSuffix(" ms")
        row2.addWidget(self._timeout_ms)
        row2.addStretch()
        adv_layout.addLayout(row2)

        adv_layout.addWidget(self._make_label("\u7528\u6237\u63D0\u793A\u8BCD"))
        self._user_prompt = QLineEdit()
        self._user_prompt.setText("\u8BF7\u5206\u6790\u56FE\u7247\u5185\u5BB9")
        adv_layout.addWidget(self._user_prompt)

        self._adv_area.setVisible(False)
        layout.addWidget(self._adv_area)

        # push/pull
        btn_row = QHBoxLayout()
        self._pull_btn = QPushButton("\U0001F4E5 \u62C9\u53D6\u914D\u7F6E")
        self._push_btn = QPushButton("\U0001F4E4 \u63A8\u9001\u914D\u7F6E")
        self._pull_btn.clicked.connect(self._on_pull)
        self._push_btn.clicked.connect(self._on_push)
        btn_row.addWidget(self._pull_btn)
        btn_row.addWidget(self._push_btn)
        btn_row.addStretch()
        layout.addLayout(btn_row)

        # —— deploy section ——
        sep = QFrame()
        sep.setFrameShape(QFrame.Shape.HLine)
        sep.setObjectName("section_sep")
        layout.addWidget(sep)

        layout.addWidget(self._make_label("\u90E8\u7F72"))

        self._status_label = QLabel("\u68C0\u6D4B\u4E2D...")
        self._status_label.setObjectName("deploy_status")
        layout.addWidget(self._status_label)

        # ---- APK info + management ----
        self._apk_path_label = QLabel("")
        self._apk_path_label.setObjectName("apk_path")
        self._apk_path_label.setWordWrap(True)
        layout.addWidget(self._apk_path_label)

        self._apk_info_label = QLabel("")
        self._apk_info_label.setObjectName("apk_info")
        layout.addWidget(self._apk_info_label)

        apk_btn_row = QHBoxLayout()
        self._apk_pick_btn = QPushButton("\U0001F4C1 \u914D\u7F6E APK")
        self._apk_pick_btn.setToolTip("\u6D4F\u89C8\u9009\u62E9 APK \u6587\u4EF6\u5E76\u590D\u5236\u5230\u90E8\u7F72\u76EE\u5F55")
        self._apk_pick_btn.clicked.connect(self._on_pick_apk)
        apk_btn_row.addWidget(self._apk_pick_btn)

        self._apk_del_btn = QPushButton("\U0001F5D1 \u5378\u8F7D\u773C\u955C\u7AEF")
        self._apk_del_btn.setToolTip("\u4ECE\u773C\u955C\u4E0A\u5378\u8F7D WEO \u5E94\u7528")
        self._apk_del_btn.clicked.connect(self._on_uninstall_app)
        apk_btn_row.addWidget(self._apk_del_btn)
        apk_btn_row.addStretch()
        layout.addLayout(apk_btn_row)

        self._refresh_apk_info()

        self._progress = QProgressBar()
        self._progress.setRange(0, 0)
        self._progress.setVisible(False)
        self._progress.setMaximumHeight(6)
        layout.addWidget(self._progress)

        deploy_btn_row = QHBoxLayout()
        self._deploy_btn = QPushButton("\u26A1 \u4E00\u952E\u90E8\u7F72")
        self._deploy_btn.setMinimumHeight(36)
        self._deploy_btn.clicked.connect(self._on_deploy)

        self._diag_btn = QPushButton("\U0001F4F1 \u8BCA\u65AD")
        self._diag_btn.clicked.connect(self._on_diag)

        deploy_btn_row.addWidget(self._deploy_btn)
        deploy_btn_row.addWidget(self._diag_btn)
        layout.addLayout(deploy_btn_row)

        self._output = QTextEdit()
        self._output.setReadOnly(True)
        self._output.setMaximumHeight(100)
        self._output.setPlaceholderText("\u90E8\u7F72\u65E5\u5FD7...")
        layout.addWidget(self._output)

        layout.addStretch()
        scroll.setWidget(inner)

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.addWidget(scroll)

    # —— local persistence ——

    def _connect_persistence(self):
        """Wire every config field to auto-save on change."""
        self._url_input.textChanged.connect(self._save_config_locally)
        self._key_input.textChanged.connect(self._save_config_locally)
        self._model_input.textChanged.connect(self._save_config_locally)
        self._prompt_edit.textChanged.connect(self._save_config_locally)
        self._user_prompt.textChanged.connect(self._save_config_locally)
        self._temp_slider.valueChanged.connect(lambda _: self._save_config_locally())
        self._max_tokens.valueChanged.connect(lambda _: self._save_config_locally())
        self._timeout_ms.valueChanged.connect(lambda _: self._save_config_locally())

    def _load_saved_config(self):
        """Restore UI fields from local persistent storage."""
        saved = self._app.load_state(self._PERSIST_KEY, {})
        if saved:
            self._apply_config(saved)

    def _save_config_locally(self):
        """Write current UI state to local persistent storage."""
        self._app.save_state(self._PERSIST_KEY, self.get_config_dict())

    def _make_label(self, text):
        l = QLabel(text)
        l.setObjectName("section_label")
        return l

    def _toggle_advanced(self):
        vis = not self._adv_area.isVisible()
        self._adv_area.setVisible(vis)
        self._adv_toggle.setText(
            "\u25B2 \u9AD8\u7EA7\u8BBE\u7F6E" if vis else "\u25BC \u9AD8\u7EA7\u8BBE\u7F6E"
        )

    # provider switching — only fill empty fields or fields matching previous provider
    def _on_provider_changed(self, idx):
        if 0 <= idx < len(self._provider_list):
            p = self._provider_list[idx]
            url = p.get("base_url", "")
            model = p.get("default_model", "")
            if not self._url_input.text() or self._url_input.text() == self._prev_provider_url:
                self._url_input.setText(url)
            self._prev_provider_url = url
            if not self._model_input.text():
                self._model_input.setText(model)

    def _toggle_key_visibility(self):
        self._key_input.setEchoMode(
            QLineEdit.EchoMode.Normal if self._key_input.echoMode() == QLineEdit.EchoMode.Password
            else QLineEdit.EchoMode.Password
        )

    def get_config_dict(self) -> dict:
        return {
            "apiBaseUrl": self._url_input.text().strip(),
            "apiKey": self._key_input.text().strip(),
            "modelName": self._model_input.text().strip(),
            "systemPrompt": self._prompt_edit.toPlainText().strip(),
            "userPrompt": self._user_prompt.text().strip(),
            "temperature": self._temp_slider.value() / 100,
            "maxTokens": self._max_tokens.value(),
            "timeoutMs": self._timeout_ms.value(),
            "textCorrectionEnabled": True,
            "maxAnswerChars": 2000,
        }

    def _apply_config(self, config: dict):
        if not config:
            return
        self._url_input.setText(config.get("apiBaseUrl", ""))
        self._key_input.setText(config.get("apiKey", ""))
        self._model_input.setText(config.get("modelName", ""))
        self._prompt_edit.setPlainText(config.get("systemPrompt", ""))
        self._user_prompt.setText(config.get("userPrompt", "\u8BF7\u5206\u6790\u56FE\u7247\u5185\u5BB9"))
        temp = config.get("temperature", 0.3)
        self._temp_slider.setValue(int(temp * 100))
        self._temp_label.setText(f"{temp:.2f}")
        self._max_tokens.setValue(config.get("maxTokens", 1500))
        self._timeout_ms.setValue(config.get("timeoutMs", 30000))
        # auto-match provider by URL
        url = config.get("apiBaseUrl", "")
        for idx, p in enumerate(self._provider_list):
            if url and p.get("base_url", "").startswith(url[:20]):
                self._provider_combo.setCurrentIndex(idx)
                break


    def _validate_config(self) -> str | None:
        """Return error message if config is invalid, None if OK."""
        cfg = self.get_config_dict()
        url = cfg.get("apiBaseUrl", "").strip()
        key = cfg.get("apiKey", "").strip()
        if not url:
            return "请填写 API 地址"
        parsed = urlparse(url)
        if parsed.scheme not in ("http", "https"):
            return "API 地址必须以 http:// 或 https:// 开头"
        if "." not in (parsed.netloc or ""):
            return "API 地址格式无效"
        if not key:
            return "请填写 API Key"
        if len(key) < 8:
            return "API Key 太短（至少 8 个字符）"
        return None

    # push/pull via QThread
    def _on_push(self):
        err = self._validate_config()
        if err:
            self._output.append(f"\u2717 {err}")
            self.log_line.emit(f"[\u914D\u7F6E] \u2717 {err}")
            return
        if self._thread and self._thread.isRunning():
            return
        self._push_btn.setEnabled(False)
        self._pull_btn.setEnabled(False)
        sync = ConfigSync(self._app.adb_client, self._app.config)
        serial = self._app.device_manager.current.serial if self._app.device_manager.is_connected else None
        cfg = self.get_config_dict()
        self._thread = _PushPullThread(sync, "push", serial=serial, config_dict=cfg, parent=self)
        self._thread.finished.connect(self._on_push_pull_done)
        self._thread.finished.connect(self._thread.deleteLater)
        self._thread.start()

    def _on_pull(self):
        if self._thread and self._thread.isRunning():
            return
        self._push_btn.setEnabled(False)
        self._pull_btn.setEnabled(False)
        sync = ConfigSync(self._app.adb_client, self._app.config)
        serial = self._app.device_manager.current.serial if self._app.device_manager.is_connected else None
        self._thread = _PushPullThread(sync, "pull", serial=serial, parent=self)
        self._thread.finished.connect(self._on_push_pull_done)
        self._thread.finished.connect(self._thread.deleteLater)
        self._thread.start()

    def _on_push_pull_done(self, ok: bool, msg: str, data):
        self._push_btn.setEnabled(True)
        self._pull_btn.setEnabled(True)
        self._thread = None
        self.log_line.emit(f"[\u914D\u7F6E] {msg}")
        if ok and data is not None:
            self._apply_config(data)

    # —— deploy ——

    def _current_serial(self) -> str | None:
        dm = self._app.device_manager
        if dm.is_connected and dm.current:
            return dm.current.serial
        return None

    def _refresh_status(self):
        serial = self._current_serial()
        if not serial:
            self._status_label.setText("\u26A0 \u672A\u8FDE\u63A5\u8BBE\u5907")
            self._deploy_btn.setEnabled(False)
            return
        self._deploy_btn.setEnabled(True)
        status = check_status(self._app.adb_client, self._app.config, serial=serial)
        self._status_label.setText(status)

    # —— APK management ——

    def _current_apk_path(self) -> Path:
        return self._app.external_path("lib", "apk", self._app.config.apk_filename)

    def _refresh_apk_info(self):
        p = self._current_apk_path()
        self._apk_path_label.setText(str(p))
        if p.exists():
            stat = p.stat()
            size_mb = stat.st_size / (1024 * 1024)
            mtime = datetime.fromtimestamp(stat.st_mtime).strftime("%Y-%m-%d %H:%M")
            self._apk_info_label.setText(f"\u2713 {size_mb:.1f} MB | {mtime}")
        else:
            self._apk_info_label.setText("\u2717 \u672A\u627E\u5230 APK \u6587\u4EF6")

    def _on_pick_apk(self):
        """Browse and copy an APK into the deployment directory."""
        # Start the file dialog from the APK build output dir if it exists,
        # otherwise from the configured APK dir so the user sees what''s already there.
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
        dst = self._current_apk_path().resolve()

        if src == dst:
            self._output.append(f"\u2713 APK \u5DF2\u5C31\u4F4D: {dst.name}")
            self._refresh_apk_info()
            return

        try:
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(str(src), str(dst))
            size_mb = src.stat().st_size / (1024 * 1024)
            self._output.append(f"\u2713 APK \u5DF2\u590D\u5236: {dst.name} ({size_mb:.1f} MB)")
            self._refresh_apk_info()
            self.log_line.emit(f"[\u90E8\u7F72] APK \u5DF2\u914D\u7F6E: {dst.name}")
        except OSError as e:
            self._output.append(f"\u2717 \u590D\u5236\u5931\u8D25: {e}")

    def _on_uninstall_app(self):
        """Uninstall WEO from the connected glasses via ADB."""
        serial = self._current_serial()
        if not serial:
            self._output.append("\u26A0 \u65E0\u8BBE\u5907\u8FDE\u63A5\uFF0C\u65E0\u6CD5\u5378\u8F7D")
            return

        self._output.append("\u6B63\u5728\u5378\u8F7D\u773C\u955C\u7AEF\u5E94\u7528...")
        ok, msg = uninstall_only(
            self._app.adb_client,
            self._app.config.pkg_name,
            serial=serial,
        )
        icon = "\u2713" if ok else "\u2717"
        self._output.append(f"{icon} {msg}")
        self.log_line.emit(f"[\u90E8\u7F72] {icon} {msg}")
        self._refresh_status()

    def _on_deploy(self):
        if self._thread and self._thread.isRunning():
            return
        serial = self._current_serial()
        if not serial:
            self.log_line.emit("[\u90E8\u7F72] \u65E0\u8BBE\u5907\u8FDE\u63A5")
            return

        err = self._validate_config()
        if err:
            self._output.append(f"\u2717 {err}")
            return
        apk_path = self._current_apk_path()
        if not apk_path.exists():
            self._output.append("\u2717 APK \u4E0D\u5B58\u5728: " + str(apk_path))
            return

        self._deploy_btn.setEnabled(False)
        self._diag_btn.setEnabled(False)
        self._progress.setVisible(True)
        self._output.clear()
        self._output.append("\u90E8\u7F72\u4E2D...")

        self._thread = _DeployThread(
            self._app.adb_client, self._app.config, apk_path,
            self.get_config_dict(), serial, self,
        )
        self._thread.finished.connect(self._on_deploy_done)
        self._thread.finished.connect(self._thread.deleteLater)
        self._thread.start()

    def _on_deploy_done(self, ok: bool, msg: str):
        self._thread = None
        self._progress.setVisible(False)
        self._deploy_btn.setEnabled(True)
        self._diag_btn.setEnabled(True)
        self._output.append(msg)
        icon = "\u2713" if ok else "\u2717"
        text = "\u6210\u529F" if ok else "\u5931\u8D25"
        self.log_line.emit("[\u90E8\u7F72] " + icon + " " + text)
        self._refresh_status()

    def _on_diag(self):
        serial = self._current_serial()
        if not serial:
            self._output.append("\u26A0 \u65E0\u8BBE\u5907\u8FDE\u63A5")
            return
        self._output.clear()
        try:
            adb = self._app.adb_client
            info = adb.get_device_info(serial=serial)
            self._output.append("\u8BBE\u5907: " + info.get("model", "?") + " | Android " + info.get("android", "?"))
            self._output.append("\u5E8F\u5217\u53F7: " + serial)
            self._output.append("\u5E94\u7528: " + check_status(adb, self._app.config, serial=serial))
        except Exception as e:
            self._output.append("\u8BCA\u65AD\u5931\u8D25: " + str(e))
