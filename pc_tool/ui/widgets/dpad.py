"""Compact D-Pad widget — 5-way navigation + back/home keys."""

from PySide6.QtCore import Signal, Qt
from PySide6.QtWidgets import QWidget, QGridLayout, QHBoxLayout, QPushButton, QSizePolicy

from app.application import App


_KEY_MAP = {
    "up": 19, "down": 20, "left": 21, "right": 22,
    "enter": 66, "back": 4, "home": 3,
}


class DPad(QWidget):
    """Compact directional pad for device navigation, iPod-style."""

    log_line = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._setup_ui()

    def _setup_ui(self):
        self.setSizePolicy(QSizePolicy.Preferred, QSizePolicy.Fixed)
        layout = QGridLayout(self)
        layout.setContentsMargins(4, 4, 4, 4)
        layout.setSpacing(3)

        buttons = [
            (0, 1, "↑", "up"),
            (1, 0, "←", "left"),
            (1, 1, "●", "enter"),
            (1, 2, "→", "right"),
            (2, 1, "↓", "down"),
        ]

        for row, col, label, key in buttons:
            btn = QPushButton(label)
            btn.setFixedSize(40, 40)
            btn.setObjectName("dpad_btn")
            btn.clicked.connect(lambda checked, k=key: self._on_key(k))
            layout.addWidget(btn, row, col, Qt.AlignmentFlag.AlignCenter)

        # Bottom row: back + home
        bottom = QHBoxLayout()
        bottom.setSpacing(4)
        btn_back = QPushButton("↩")
        btn_back.setFixedSize(40, 36)
        btn_back.setToolTip("返回")
        btn_back.clicked.connect(lambda: self._on_key("back"))

        btn_home = QPushButton("⌂")
        btn_home.setFixedSize(40, 36)
        btn_home.setToolTip("Home")
        btn_home.clicked.connect(lambda: self._on_key("home"))

        bottom.addStretch()
        bottom.addWidget(btn_back)
        bottom.addWidget(btn_home)
        bottom.addStretch()

        layout.addLayout(bottom, 3, 0, 1, 3)

    def _on_key(self, key: str):
        code = _KEY_MAP.get(key)
        if code is None:
            return
        dm = self._app.device_manager
        if not dm.is_connected:
            self.log_line.emit("[D-Pad] 无设备连接，无法发送按键")
            return
        dm.keyevent(code)
        self.log_line.emit(f"[D-Pad] {key}")
