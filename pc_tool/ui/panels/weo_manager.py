"""WEO Manager panel --- AI provider config + system prompt + push/pull."""

import json
from pathlib import Path

from PySide6.QtCore import Signal, Qt, QThread
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QLineEdit, QComboBox, QTextEdit, QSlider, QScrollArea, QSpinBox,
    QProgressBar, QFrame,
)

from app.application import App
from core.config_sync import ConfigSync
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


class WeoManagerPanel(QWidget):
    """AI provider config + system prompt + push/pull to device."""

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

        # advanced --- collapsible
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

        row3 = QHBoxLayout()
        self._text_correction = QComboBox()
        self._text_correction.addItems(["\u5F00\u542F", "\u5173\u95ED"])
        self._text_correction.setCurrentIndex(0)
        self._text_correction.currentIndexChanged.connect(lambda _: self._save_config_locally())
        row3.addWidget(QLabel("\u6587\u6863\u6821\u6B63"))
        row3.addWidget(self._text_correction)
        row3.addStretch()
        adv_layout.addLayout(row3)

        row4 = QHBoxLayout()
        row4.addWidget(QLabel("maxAnswerChars"))
        self._max_answer_chars = QSpinBox()
        self._max_answer_chars.setRange(100, 10000)
        self._max_answer_chars.setSingleStep(100)
        self._max_answer_chars.setValue(2000)
        self._max_answer_chars.valueChanged.connect(lambda _: self._save_config_locally())
        row4.addWidget(self._max_answer_chars)
        row4.addStretch()
        adv_layout.addLayout(row4)

        self._adv_area.setVisible(False)
        layout.addWidget(self._adv_area)

        # push/pull buttons
        btn_row = QHBoxLayout()
        self._pull_btn = QPushButton("\U0001F4E5 \u62C9\u53D6\u914D\u7F6E")
        self._push_btn = QPushButton("\U0001F4E4 \u63A8\u9001\u914D\u7F6E")
        self._pull_btn.clicked.connect(self._on_pull)
        self._push_btn.clicked.connect(self._on_push)
        btn_row.addWidget(self._pull_btn)
        btn_row.addWidget(self._push_btn)
        btn_row.addStretch()
        layout.addLayout(btn_row)

        layout.addStretch()
        scroll.setWidget(inner)

        root = QVBoxLayout(self)
        root.setContentsMargins(0, 0, 0, 0)
        root.addWidget(scroll)

    # --- local persistence ---

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
            "textCorrectionEnabled": self._text_correction.currentIndex() == 0,
            "maxAnswerChars": self._max_answer_chars.value(),
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
        self._text_correction.setCurrentIndex(0 if config.get("textCorrectionEnabled", True) else 1)
        self._max_answer_chars.setValue(config.get("maxAnswerChars", 2000))
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
            return "\u8BF7\u586B\u5199 API \u5730\u5740"
        parsed = urlparse(url)
        if parsed.scheme not in ("http", "https"):
            return "API \u5730\u5740\u5FC5\u987B\u4EE5 http:// \u6216 https:// \u5F00\u5934"
        if "." not in (parsed.netloc or ""):
            return "API \u5730\u5740\u683C\u5F0F\u65E0\u6548"
        if not key:
            return "\u8BF7\u586B\u5199 API Key"
        if len(key) < 8:
            return "API Key \u592A\u77ED\uFF08\u81F3\u5C11 8 \u4E2A\u5B57\u7B26\uFF09"
        return None

    # push/pull via QThread
    def _on_push(self):
        err = self._validate_config()
        if err:
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