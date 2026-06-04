"""Device bar — comprehensive device status with dashboard info."""

from PySide6.QtCore import Qt
from PySide6.QtWidgets import QWidget, QHBoxLayout, QLabel

from core.device import DeviceInfo


class DeviceBar(QWidget):
    """Top bar: device indicator + model + android + battery + WEO status + storage."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._labels = {}
        self._setup_ui()
        self.set_disconnected()

    def _setup_ui(self):
        self.setFixedHeight(40)
        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 0, 12, 0)
        layout.setSpacing(10)

        # Dot indicator
        self._dot = QLabel("●")
        self._dot.setFixedWidth(16)
        self._dot.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self._dot)

        # Fields: model | android | battery | WEO | storage
        fields = [
            ("model", 120),
            ("android", 100),
            ("battery", 64),
            ("weo", 80),
            ("storage", 100),
        ]
        for key, width in fields:
            lbl = QLabel("-")
            lbl.setFixedWidth(width)
            lbl.setAlignment(Qt.AlignmentFlag.AlignLeft | Qt.AlignmentFlag.AlignVCenter)
            self._labels[key] = lbl
            layout.addWidget(lbl)

        layout.addStretch()

    def set_connected(self, info: DeviceInfo):
        self._dot.setObjectName("device_dot_connected")

        self._labels["model"].setText(info.model or "-")
        self._labels["android"].setText(f"Android {info.android_version}" if info.android_version else "-")

        if info.battery_level >= 0:
            chg = "⚡" if info.battery_charging else ""
            self._labels["battery"].setText(f"{chg}{info.battery_level}%")
        else:
            self._labels["battery"].setText("-")

        weo_parts = []
        if info.weo_installed:
            weo_parts.append("已安装")
        if info.weo_running:
            weo_parts.append("运行中")
        self._labels["weo"].setText(" | ".join(weo_parts) if weo_parts else "-")

        if info.storage_used and info.storage_total:
            self._labels["storage"].setText(f"{info.storage_used}/{info.storage_total}")
        else:
            self._labels["storage"].setText("-")

    def set_disconnected(self):
        self._dot.setObjectName("device_dot_disconnected")
        for lbl in self._labels.values():
            lbl.setText("-")
