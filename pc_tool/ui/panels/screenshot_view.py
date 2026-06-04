"""Screenshot viewer panel — capture, display, save."""

from datetime import datetime

from PySide6.QtCore import Signal, Qt
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QFileDialog, QScrollArea, QCheckBox, QSizePolicy,
)

from app.application import App
from core.screenshot import ScreenshotCapture


class ScreenshotPanel(QWidget):
    """Capture and view device screenshots."""

    log_line = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._capture_thread: ScreenshotCapture | None = None
        self._png_data: bytes | None = None
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(8)

        # toolbar
        toolbar = QHBoxLayout()
        toolbar.setSpacing(6)

        self._capture_btn = QPushButton("\U0001F4F7 \u622A\u56FE")
        self._capture_btn.setMinimumHeight(32)
        self._capture_btn.clicked.connect(self._on_capture)

        self._save_btn = QPushButton("\U0001F4BE \u4FDD\u5B58")
        self._save_btn.setMinimumHeight(32)
        self._save_btn.setEnabled(False)
        self._save_btn.clicked.connect(self._on_save)

        self._auto_cb = QCheckBox("\u81EA\u52A8\u5237\u65B0")
        self._auto_cb.setChecked(False)

        toolbar.addWidget(self._capture_btn)
        toolbar.addWidget(self._save_btn)
        toolbar.addWidget(self._auto_cb)
        toolbar.addStretch()
        layout.addLayout(toolbar)

        # image display
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setAlignment(Qt.AlignmentFlag.AlignCenter)

        self._image_label = QLabel("\u70B9\u51FB\u201C\u622A\u56FE\u201D\u6355\u6349\u8BBE\u5907\u5C4F\u5E55")
        self._image_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self._image_label.setMinimumSize(320, 240)
        scroll.setWidget(self._image_label)

        layout.addWidget(scroll, 1)

    def _current_serial(self) -> str | None:
        dm = self._app.device_manager
        if dm.is_connected and dm.current:
            return dm.current.serial
        return None

    def _on_capture(self):
        serial = self._current_serial()
        if not serial:
            self.log_line.emit("[\u622A\u56FE] \u65E0\u8BBE\u5907\u8FDE\u63A5")
            return

        if self._capture_thread and self._capture_thread.isRunning():
            return

        self._capture_btn.setEnabled(False)
        self._capture_btn.setText("\u6355\u6349\u4E2D...")
        self.log_line.emit("[\u622A\u56FE] \u6355\u6349\u4E2D...")

        self._capture_thread = ScreenshotCapture(self._app.adb_client, serial, self)
        self._capture_thread.finished.connect(self._on_capture_done)
        self._capture_thread.finished.connect(self._capture_thread.deleteLater)
        self._capture_thread.start()

    def _on_capture_done(self, ok: bool, data):
        self._capture_btn.setEnabled(True)
        self._capture_btn.setText("\U0001F4F7 \u622A\u56FE")
        self._capture_thread = None

        if not ok:
            self.log_line.emit(f"[\u622A\u56FE] \u5931\u8D25: {data}")
            return

        self._png_data = data
        pixmap = QPixmap()
        pixmap.loadFromData(data)
        if not pixmap.isNull():
            self._image_label.setPixmap(pixmap.scaled(
                self._image_label.size(), Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation,
            ))
            self._save_btn.setEnabled(True)
            self.log_line.emit(f"[\u622A\u56FE] \u6355\u6349\u6210\u529F ({pixmap.width()}x{pixmap.height()})")

        if self._auto_cb.isChecked():
            from PySide6.QtCore import QTimer
            QTimer.singleShot(2000, self._on_capture)

    def _on_save(self):
        if not self._png_data:
            return
        ts = datetime.now().strftime("%Y%m%d_%H%M%S")
        default_name = f"weo_screenshot_{ts}.png"
        path, _ = QFileDialog.getSaveFileName(
            self, "\u4FDD\u5B58\u622A\u56FE", default_name, "PNG (*.png)",
        )
        if path:
            try:
                with open(path, "wb") as f:
                    f.write(self._png_data)
                self.log_line.emit(f"[\u622A\u56FE] \u5DF2\u4FDD\u5B58: {path}")
            except OSError as e:
                self.log_line.emit(f"[\u622A\u56FE] \u4FDD\u5B58\u5931\u8D25: {e}")

    def resizeEvent(self, event):
        super().resizeEvent(event)
        if self._image_label.pixmap() and not self._image_label.pixmap().isNull():
            self._image_label.setPixmap(self._image_label.pixmap().scaled(
                self._image_label.size(), Qt.AspectRatioMode.KeepAspectRatio,
                Qt.TransformationMode.SmoothTransformation,
            ))
