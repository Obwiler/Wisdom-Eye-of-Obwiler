"""Interactive ADB shell terminal panel."""

from PySide6.QtCore import Signal, Qt
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPlainTextEdit, QLineEdit,
    QPushButton, QScrollArea,
)

from app.application import App
from core.shell import ShellClient


class ShellTermPanel(QWidget):
    """ADB shell terminal with command input, history, and quick buttons."""

    log_line = Signal(str)

    _QUICK_COMMANDS = [
        ("ls /sdcard", "ls"),
        ("pm list packages", "packages"),
        ("getprop ro.product.model", "model"),
        ("getprop ro.build.version.release", "version"),
        ("dumpsys battery", "battery"),
        ("wm size", "screen"),
        ("logcat -d -t 20", "logcat"),
    ]

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._shell = ShellClient(self._app.adb_client)
        self._setup_ui()
        self._connect_signals()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        # output area
        self._output = QPlainTextEdit()
        self._output.setReadOnly(True)
        self._output.setObjectName("shell_output")
        self._output.setMaximumBlockCount(2000)
        layout.addWidget(self._output, 1)

        # quick commands
        quick_row = QHBoxLayout()
        quick_row.setSpacing(4)
        for cmd, label in self._QUICK_COMMANDS:
            btn = QPushButton(label)
            btn.setFixedHeight(24)
            btn.clicked.connect(lambda checked, c=cmd: self._run_command(c))
            quick_row.addWidget(btn)
        btn_clear = QPushButton("\u2715 \u6E05\u5C4F")
        btn_clear.setFixedHeight(24)
        btn_clear.clicked.connect(lambda: self._output.clear())
        quick_row.addStretch()
        quick_row.addWidget(btn_clear)
        layout.addLayout(quick_row)

        # input row
        input_row = QHBoxLayout()
        input_row.setSpacing(6)
        self._input = QLineEdit()
        self._input.setPlaceholderText("adb shell \u547D\u4EE4... (Enter \u6267\u884C, \u2191/\u2193 \u5386\u53F2)")
        self._input.returnPressed.connect(self._on_enter)
        self._input.installEventFilter(self)
        self._send_btn = QPushButton("\u23CE \u6267\u884C")
        self._send_btn.clicked.connect(self._on_enter)
        input_row.addWidget(self._input, 1)
        input_row.addWidget(self._send_btn)
        layout.addLayout(input_row)

        self._write_prompt()

    def _connect_signals(self):
        dm = self._app.device_manager
        dm.device_connected.connect(lambda s: self._write_prompt())
        dm.device_disconnected.connect(lambda: self._write_prompt())

    def _current_serial(self) -> str | None:
        dm = self._app.device_manager
        if dm.is_connected and dm.current:
            return dm.current.serial
        return None

    def _write_prompt(self):
        serial = self._current_serial()
        if serial:
            self._output.appendPlainText(f"\u2500\u2500\u2500 {serial} \u2500\u2500\u2500")
        else:
            self._output.appendPlainText("\u2500\u2500\u2500 \u65E0\u8BBE\u5907 \u2500\u2500\u2500")

    def _run_command(self, cmd: str):
        serial = self._current_serial()
        if not serial:
            self._output.appendPlainText("[!] \u65E0\u8BBE\u5907\u8FDE\u63A5")
            return
        self._output.appendPlainText(f"$ {cmd}")
        result = self._shell.run(cmd, serial=serial)
        if result:
            for line in result.split("\n"):
                self._output.appendPlainText(line)
        self._scroll_to_bottom()

    def _on_enter(self):
        cmd = self._input.text().strip()
        if cmd:
            self._run_command(cmd)
            self._input.clear()

    def eventFilter(self, obj, event):
        if obj is self._input and event.type() == event.Type.KeyPress:
            if event.key() == Qt.Key.Key_Up:
                prev = self._shell.history_prev()
                if prev is not None:
                    self._input.setText(prev)
                return True
            if event.key() == Qt.Key.Key_Down:
                nxt = self._shell.history_next()
                if nxt is not None:
                    self._input.setText(nxt)
                return True
        return super().eventFilter(obj, event)

    def _scroll_to_bottom(self):
        sb = self._output.verticalScrollBar()
        sb.setValue(sb.maximum())
