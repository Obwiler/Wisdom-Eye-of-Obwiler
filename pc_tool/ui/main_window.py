"""Main application window — iPod-style left mirror + right tools."""

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QMainWindow, QWidget, QHBoxLayout, QVBoxLayout, QStackedWidget,
    QSplitter, QPushButton, QSizePolicy,
)

from app.application import App, APP_NAME
from core.device import DeviceInfo
from ui.widgets.device_bar import DeviceBar
from ui.widgets.log_panel import LogPanel
from ui.widgets.status_bar import StatusBar
from ui.widgets.dpad import DPad
from ui.panels.scrcpy import ScrcpyPanel
from ui.panels.weo_manager import WeoManagerPanel
from ui.panels.files import FilesPanel
from ui.panels.screenshot_view import ScreenshotPanel
from ui.panels.shell_term import ShellTermPanel
from ui.panels.settings_panel import SettingsPanel


TAB_WEO = 0
TAB_FILES = 1
TAB_SHELL = 2
TAB_SCREENSHOT = 3
TAB_SETTINGS = 4

TAB_NAMES = ["WEO", "\u6587\u4EF6\u7BA1\u7406", "Shell", "\u622A\u56FE", "\u8BBE\u7F6E"]


class MainWindow(QMainWindow):

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()

        self.setWindowTitle(APP_NAME)
        self.setMinimumSize(1100, 620)

        self._setup_ui()
        self._connect_signals()
        self._start_device_poll()
        self._restore_state()

    def _setup_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # ---- top: device bar ----
        self._device_bar = DeviceBar()
        root.addWidget(self._device_bar)

        # ---- body: left (mirror+dpad) | right (tabs+stack) ----
        self._splitter = QSplitter(Qt.Orientation.Horizontal)
        self._splitter.setHandleWidth(2)

        # Left panel
        left = QWidget()
        left.setObjectName("left_panel")
        left.setMinimumWidth(300)
        left_layout = QVBoxLayout(left)
        left_layout.setContentsMargins(0, 0, 0, 0)
        left_layout.setSpacing(0)

        self._scrcpy = ScrcpyPanel(display_w=480, display_h=640)
        self._scrcpy.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        left_layout.addWidget(self._scrcpy, 1)

        self._dpad = DPad()
        left_layout.addWidget(self._dpad)

        self._splitter.addWidget(left)

        # Right panel
        right = QWidget()
        right.setObjectName("right_panel")
        right_layout = QVBoxLayout(right)
        right_layout.setContentsMargins(0, 0, 0, 0)
        right_layout.setSpacing(0)

        # Tab bar
        tab_layout = QHBoxLayout()
        tab_layout.setContentsMargins(4, 4, 4, 4)
        tab_layout.setSpacing(2)
        self._tab_btns = []
        for i, name in enumerate(TAB_NAMES):
            btn = QPushButton(name)
            btn.setCheckable(True)
            btn.setFixedHeight(32)
            btn.clicked.connect(lambda checked, idx=i: self._on_tab(idx))
            tab_layout.addWidget(btn)
            self._tab_btns.append(btn)
        tab_layout.addStretch()
        right_layout.addLayout(tab_layout)

        # Stack
        self._stack = QStackedWidget()
        right_layout.addWidget(self._stack, 1)

        self._splitter.addWidget(right)

        # Default split: 440px left
        self._splitter.setSizes([440, 660])
        root.addWidget(self._splitter, 1)

        # ---- log panel (collapsible) ----
        self._log_header = QPushButton("\u65E5\u5FD7 \u25BC")
        self._log_header.setFixedHeight(22)
        self._log_header.setObjectName("log_toggle")
        self._log_header.clicked.connect(self._toggle_log)
        root.addWidget(self._log_header)

        self._log_panel = LogPanel()
        self._log_panel._log_view.document().setMaximumBlockCount(
            self._app.load_state("log_max_lines", 500)
        )
        root.addWidget(self._log_panel)

        # ---- status bar ----
        self._status_bar = StatusBar()
        root.addWidget(self._status_bar)

        # ---- populate stack ----
        self._weo_manager = WeoManagerPanel()
        self._stack.addWidget(self._weo_manager)

        self._files = FilesPanel()
        self._stack.addWidget(self._files)

        self._shell_term = ShellTermPanel()
        self._stack.addWidget(self._shell_term)

        self._screenshot = ScreenshotPanel()
        self._stack.addWidget(self._screenshot)

        self._settings = SettingsPanel()
        self._stack.addWidget(self._settings)

        # Wire log signals
        for panel in [self._scrcpy, self._dpad, self._weo_manager,
                      self._files, self._shell_term, self._screenshot,
                      self._settings]:
            panel.log_line.connect(self._log_panel.append)

        # Select first tab
        self._tab_btns[TAB_WEO].setChecked(True)

    def _toggle_log(self):
        visible = self._log_panel.isVisible()
        self._log_panel.setVisible(not visible)
        self._log_header.setText("\u65E5\u5FD7 \u25B2" if visible else "\u65E5\u5FD7 \u25BC")

    def _on_tab(self, idx: int):
        self._stack.setCurrentIndex(idx)
        for i, btn in enumerate(self._tab_btns):
            btn.setChecked(i == idx)

    # ---- signals ----
    def _connect_signals(self):
        dm = self._app.device_manager
        dm.device_connected.connect(self._on_device_connected)
        dm.device_disconnected.connect(self._on_device_disconnected)
        dm.device_updated.connect(self._on_device_updated)
        dm.error_occurred.connect(self._on_device_error)

    def _start_device_poll(self):
        self._app.device_manager.start()
        self._status_bar.set_right("WEO PC \u5DE5\u5177 | \u2192 \u53F3\u952E\u83DC\u5355 \u8BBE\u7F6E")

    # ---- state ----
    def _restore_state(self):
        geo = self._app.load_state("window_geometry")
        if geo:
            try:
                self.restoreGeometry(bytes.fromhex(geo))
            except (ValueError, TypeError):
                self.resize(1250, 750)
        else:
            self.resize(1250, 750)

        last_tab = self._app.load_state("last_tab", TAB_WEO)
        if isinstance(last_tab, int) and 0 <= last_tab < self._stack.count():
            self._on_tab(last_tab)

    def _save_state(self):
        self._app.save_state("window_geometry", self.saveGeometry().toHex().data().decode())
        self._app.save_state("last_tab", self._stack.currentIndex())
        self._app.shutdown()

    # ---- slots ----
    def _on_device_connected(self, serial: str):
        self._log_panel.append(f"[\u8BBE\u5907] \u5DF2\u8FDE\u63A5 {serial}")

    def _on_device_disconnected(self):
        self._log_panel.append("[\u8BBE\u5907] \u5DF2\u65AD\u5F00")

    def _on_device_updated(self, info: DeviceInfo):
        self._device_bar.set_connected(info)
        if info.screen_resolution:
            parts = info.screen_resolution.lower().replace("physical size: ", "").strip().split("x")
            if len(parts) == 2:
                try:
                    self._scrcpy.set_display_size(int(parts[0]), int(parts[1]))
                except ValueError:
                    pass

    def _on_device_error(self, msg: str):
        self._log_panel.append(f"[\u9519\u8BEF] {msg}")

    def log(self, text: str):
        self._log_panel.append(text)

    # ---- settings ----
    def contextMenuEvent(self, event):
        from PySide6.QtWidgets import QMenu

        menu = QMenu(self)
        settings_action = menu.addAction("\u2699 \u8BBE\u7F6E...")
        action = menu.exec(event.globalPos())
        if action == settings_action:
            self._on_tab(TAB_SETTINGS)

    def closeEvent(self, event):
        self._scrcpy.stop_scrcpy()
        self._app.device_manager.stop()
        self._save_state()
        event.accept()
