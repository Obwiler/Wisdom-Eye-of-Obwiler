"""Settings panel — inline ADB path, scrcpy size, about."""

from PySide6.QtCore import Signal, Qt
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QLineEdit, QSpinBox, QTabWidget, QFormLayout, QFileDialog,
)

from app.application import App, APP_NAME


class SettingsPanel(QWidget):
    """Application-wide settings as an inline panel."""

    log_line = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._setup_ui()
        self._load()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 12, 12, 12)
        layout.setSpacing(0)

        tabs = QTabWidget()
        tabs.addTab(self._build_general_tab(), "\u4E00\u822C")
        tabs.addTab(self._build_about_tab(), "\u5173\u4E8E")
        layout.addWidget(tabs)

    def _build_general_tab(self) -> QWidget:
        w = QWidget()
        form = QFormLayout(w)
        form.setSpacing(10)
        form.setContentsMargins(12, 12, 12, 12)

        # ADB path
        adb_row = QHBoxLayout()
        self._adb_path = QLineEdit()
        self._adb_path.setPlaceholderText("\u81EA\u52A8\u68C0\u6D4B")
        self._adb_path.textChanged.connect(self._on_adb_changed)
        adb_browse = QPushButton("...")
        adb_browse.setFixedWidth(32)
        adb_browse.clicked.connect(self._browse_adb)
        adb_row.addWidget(self._adb_path)
        adb_row.addWidget(adb_browse)
        form.addRow("ADB \u8DEF\u5F84", adb_row)

        # scrcpy max size
        self._scrcpy_size = QSpinBox()
        self._scrcpy_size.setRange(320, 1920)
        self._scrcpy_size.setSingleStep(80)
        self._scrcpy_size.setSuffix(" px")
        self._scrcpy_size.valueChanged.connect(self._on_size_changed)
        form.addRow("Scrcpy \u6700\u5927\u5C3A\u5BF8", self._scrcpy_size)

        # log max lines
        self._log_max = QSpinBox()
        self._log_max.setRange(100, 5000)
        self._log_max.setSingleStep(100)
        self._log_max.setSuffix(" \u884C")
        self._log_max.valueChanged.connect(self._on_log_max_changed)
        form.addRow("\u65E5\u5FD7\u6700\u5927\u884C\u6570", self._log_max)

        return w

    def _build_about_tab(self) -> QWidget:
        w = QWidget()
        layout = QVBoxLayout(w)
        layout.setSpacing(8)
        layout.setContentsMargins(12, 12, 12, 12)

        name = QLabel(APP_NAME)
        name.setObjectName("about_name")
        name.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(name)

        info = QLabel(
            "\u667A\u80FD\u773C\u955C AI \u89C6\u89C9\u8BC6\u522B\u5DE5\u5177\n\n"
            "MicroLED \u7EFF\u8272\u5355\u8272\u663E\u793A | 480\u00D7640\n"
            "RG_Glasses \u5E73\u53F0\u9002\u914D\n\n"
            "\u6784\u5EFA\u8FED\u4EE3: Phase 4"
        )
        info.setAlignment(Qt.AlignmentFlag.AlignCenter)
        info.setWordWrap(True)
        layout.addWidget(info)

        layout.addStretch()
        return w

    def _browse_adb(self):
        path, _ = QFileDialog.getOpenFileName(
            self, "\u9009\u62E9 ADB \u53EF\u6267\u884C\u6587\u4EF6", "",
            "Executable (*.exe);;All (*.*)",
        )
        if path:
            self._adb_path.setText(path)

    def _load(self):
        cfg = self._app.persistent
        self._adb_path.setText(cfg.get("adb_path_override", ""))
        self._scrcpy_size.setValue(
            cfg.get("scrcpy_max_size", self._app.config.scrcpy_max_size)
        )
        self._log_max.setValue(cfg.get("log_max_lines", 500))

    def _on_adb_changed(self, text: str):
        self._app.save_state("adb_path_override", text.strip())
        self.log_line.emit(f"[\u8BBE\u7F6E] ADB \u8DEF\u5F84\u5DF2\u66F4\u65B0")

    def _on_size_changed(self, val: int):
        self._app.save_state("scrcpy_max_size", val)

    def _on_log_max_changed(self, val: int):
        self._app.save_state("log_max_lines", val)
