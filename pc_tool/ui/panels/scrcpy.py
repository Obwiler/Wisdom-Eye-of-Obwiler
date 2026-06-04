"""Scrcpy panel — embedded screen mirroring via SDL HWND capture."""

import ctypes
from ctypes import wintypes
from pathlib import Path

from PySide6.QtCore import Signal, Qt, QTimer, QProcess, QProcessEnvironment
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel, QFrame, QSizePolicy,
)

from app.application import App

user32 = ctypes.windll.user32

GWL_STYLE = -16
GWL_EXSTYLE = -20
WS_CHILD = 0x40000000
WS_VISIBLE = 0x10000000
WS_CAPTION = 0x00C00000
WS_THICKFRAME = 0x00040000
WS_EX_TOOLWINDOW = 0x00000080
WS_EX_APPWINDOW = 0x00040000
SWP_NOZORDER = 0x0004
SWP_FRAMECHANGED = 0x0020

MAX_POLL = 80
POLL_MS = 150

_SDL_CLASSES = ["SDL_app", "scrcpy", "scrcpy-win"]


def _find_sdl_hwnd(pid: int) -> int:
    """Enumerate windows to find the SDL window owned by the given process."""
    result = ctypes.c_ulong(0)

    @ctypes.WINFUNCTYPE(ctypes.c_bool, wintypes.HWND, wintypes.LPARAM)
    def callback(hwnd, lparam):
        cpid = ctypes.c_ulong()
        user32.GetWindowThreadProcessId(hwnd, ctypes.byref(cpid))
        if cpid.value == pid and user32.IsWindowVisible(hwnd):
            buf = ctypes.create_unicode_buffer(256)
            user32.GetClassNameW(hwnd, buf, 256)
            if buf.value in _SDL_CLASSES:
                nonlocal result
                result.value = hwnd
                return False
        return True

    user32.EnumWindows(callback, 0)
    return result.value


def _detach_parent(hwnd: int):
    """Restore SDL window to a standalone top-level window."""
    if not hwnd or not user32.IsWindow(hwnd):
        return
    user32.SetParent(hwnd, 0)
    style = user32.GetWindowLongW(hwnd, GWL_STYLE)
    style = (style & ~WS_CHILD) | WS_CAPTION | WS_THICKFRAME | WS_VISIBLE
    user32.SetWindowLongW(hwnd, GWL_STYLE, style)
    ex = user32.GetWindowLongW(hwnd, GWL_EXSTYLE)
    ex = (ex & ~WS_EX_TOOLWINDOW) | WS_EX_APPWINDOW
    user32.SetWindowLongW(hwnd, GWL_EXSTYLE, ex)
    user32.SetWindowPos(hwnd, 0, 0, 0, 0, 0, SWP_NOZORDER | SWP_FRAMECHANGED)


class ScrcpyPanel(QWidget):
    """Embedded scrcpy mirroring with start/stop controls."""

    log_line = Signal(str)

    def __init__(self, display_w: int = 480, display_h: int = 640, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._process: QProcess | None = None
        self._hwnd = 0
        self._poll_count = 0
        self._stopping = False
        self._dev_w = display_w
        self._dev_h = display_h

        self._resize_timer = QTimer(self)
        self._resize_timer.setSingleShot(True)
        self._resize_timer.timeout.connect(self._relocate)

        self._setup_ui()

    def set_display_size(self, w: int, h: int):
        self._dev_w = w
        self._dev_h = h

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(4, 4, 4, 4)
        layout.setSpacing(4)

        hdr = QHBoxLayout()
        self._start_btn = QPushButton("\u25B6 \u542F\u52A8\u6295\u5C4F")
        self._stop_btn = QPushButton("\u25A0 \u505C\u6B62")
        self._stop_btn.setEnabled(False)
        hdr.addWidget(self._start_btn)
        hdr.addWidget(self._stop_btn)
        hdr.addStretch()
        layout.addLayout(hdr)

        self._container = QFrame()
        self._container.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        self._container.setMinimumSize(200, 200)
        cl = QVBoxLayout(self._container)
        cl.setContentsMargins(0, 0, 0, 0)
        self._placeholder = QLabel("\u672A\u542F\u52A8\u6295\u5C4F")
        self._placeholder.setAlignment(Qt.AlignmentFlag.AlignCenter)
        cl.addWidget(self._placeholder)
        layout.addWidget(self._container, 1)

        self._start_btn.clicked.connect(self.start_scrcpy)
        self._stop_btn.clicked.connect(self.stop_scrcpy)

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._resize_timer.start(80)

    def start_scrcpy(self):
        if self._process is not None:
            return

        scrcpy_exe = str(self._app.resolve_path(self._app.config.scrcpy_exe_relative))
        adb_exe = str(self._app.resolve_path(self._app.config.adb_exe_relative))

        if not Path(scrcpy_exe).exists():
            self.log_line.emit(f"[\u6295\u5C4F] scrcpy.exe \u4E0D\u5B58\u5728: {scrcpy_exe}")
            return

        self._start_btn.setEnabled(False)
        self._placeholder.hide()
        self._poll_count = 0
        self._stopping = False

        self._process = QProcess(self)
        self._process.finished.connect(self._on_finished)
        self._process.readyReadStandardError.connect(self._on_stderr)
        self._process.started.connect(self._poll_for_window)

        env = QProcessEnvironment.systemEnvironment()
        env.insert("ADB", adb_exe)
        self._process.setProcessEnvironment(env)

        args = [
            f"--max-size={self._app.config.scrcpy_max_size}",
            "--no-audio", "--stay-awake", "--video-codec=h264",
        ]
        dm = self._app.device_manager
        if dm.is_connected and dm.current:
            args.insert(0, f"--serial={dm.current.serial}")
        self._process.start(scrcpy_exe, args)

    def _poll_for_window(self):
        if self._process is None or self._stopping:
            return
        pid = self._process.processId()
        if not pid:
            QTimer.singleShot(POLL_MS, self._poll_for_window)
            return

        hwnd = _find_sdl_hwnd(pid)
        if hwnd and user32.IsWindow(hwnd):
            self._hwnd = hwnd
            self._embed()
            self._stop_btn.setEnabled(True)
            self.log_line.emit("[\u6295\u5C4F] \u5DF2\u8FDE\u63A5")
            return

        self._poll_count += 1
        if self._poll_count >= MAX_POLL:
            self.log_line.emit("[\u6295\u5C4F] \u8D85\u65F6\uFF1A\u65E0\u6CD5\u627E\u5230 scrcpy \u7A97\u53E3\uFF0C\u8BF7\u786E\u8BA4 scrcpy \u662F\u5426\u6B63\u5E38\u8FD0\u884C")
            self.stop_scrcpy()
            return
        QTimer.singleShot(POLL_MS, self._poll_for_window)

    def _embed(self):
        if not self._hwnd:
            return
        chwnd = int(self._container.winId())
        user32.SetParent(self._hwnd, chwnd)
        style = user32.GetWindowLongW(self._hwnd, GWL_STYLE)
        style = (style & ~WS_CAPTION & ~WS_THICKFRAME) | WS_CHILD | WS_VISIBLE
        user32.SetWindowLongW(self._hwnd, GWL_STYLE, style)
        ex = user32.GetWindowLongW(self._hwnd, GWL_EXSTYLE)
        ex = (ex | WS_EX_TOOLWINDOW) & ~WS_EX_APPWINDOW
        user32.SetWindowLongW(self._hwnd, GWL_EXSTYLE, ex)
        user32.SetWindowPos(self._hwnd, 0, 0, 0, 0, 0, SWP_NOZORDER | SWP_FRAMECHANGED)
        self._relocate()

    def _relocate(self):
        if not self._hwnd or not user32.IsWindow(self._hwnd):
            return
        cw = self._container.width()
        ch = self._container.height()
        if cw <= 0 or ch <= 0:
            return
        dw = self._dev_w or 640
        dh = self._dev_h or 480
        tw = cw
        th = int(cw * dh / dw)
        if th > ch:
            th = ch
            tw = int(ch * dw / dh)
        x = (cw - tw) // 2
        y = (ch - th) // 2
        user32.MoveWindow(self._hwnd, x, y, tw, th, True)

    def stop_scrcpy(self):
        self._stopping = True
        if self._hwnd:
            _detach_parent(self._hwnd)
            self._hwnd = 0
        if self._process:
            self._process.terminate()
        self._poll_count = 0
        self._placeholder.show()
        self._start_btn.setEnabled(True)
        self._stop_btn.setEnabled(False)

    def _on_stderr(self):
        if self._process:
            text = self._process.readAllStandardError().data().decode(errors="replace")
            if text.strip():
                self.log_line.emit(f"[scrcpy] {text.strip()}")

    def _on_finished(self, exit_code):
        self._hwnd = 0
        self._poll_count = 0
        self._stopping = False
        self._process = None
        self._start_btn.setEnabled(True)
        self._stop_btn.setEnabled(False)
        self._placeholder.show()
        if exit_code != 0:
            self.log_line.emit(f"[\u6295\u5C4F] scrcpy \u9000\u51FA (code={exit_code})")
