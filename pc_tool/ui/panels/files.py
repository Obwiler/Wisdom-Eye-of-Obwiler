"""File manager panel — browse, upload, download, delete files on device."""

import os

from PySide6.QtCore import Signal, Qt, QThread
from PySide6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QPushButton, QLabel,
    QTreeWidget, QTreeWidgetItem, QFileDialog, QMessageBox, QLineEdit,
)

from app.application import App
from core.file_mgr import FileManager, FileEntry


class _TransferThread(QThread):
    finished = Signal(bool, str)

    def __init__(self, mgr: FileManager, action: str,
                 src: str, dst: str, serial: str | None, parent=None):
        super().__init__(parent)
        self._mgr = mgr
        self._action = action
        self._src = src
        self._dst = dst
        self._serial = serial

    def run(self):
        if self._action == "push":
            ok = self._mgr.push(self._src, self._dst, serial=self._serial)
        elif self._action == "pull":
            ok = self._mgr.pull(self._src, self._dst, serial=self._serial)
        elif self._action == "delete":
            ok = self._mgr.delete(self._src, serial=self._serial)
        else:
            ok = False
        self.finished.emit(ok, self._action)


class FilesPanel(QWidget):
    """File browser with upload, download, delete."""

    log_line = Signal(str)

    def __init__(self, parent=None):
        super().__init__(parent)
        self._app = App.instance()
        self._mgr = FileManager(self._app.adb_client)
        self._cwd = "/sdcard"
        self._transfer_thread: _TransferThread | None = None
        self._setup_ui()
        self._connect_signals()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(8, 8, 8, 8)
        layout.setSpacing(6)

        # path bar
        path_row = QHBoxLayout()
        path_row.setSpacing(4)
        self._up_btn = QPushButton("\u2191")
        self._up_btn.setFixedWidth(32)
        self._up_btn.clicked.connect(self._on_up)
        self._path_input = QLineEdit(self._cwd)
        self._path_input.returnPressed.connect(self._on_path_enter)
        self._home_btn = QPushButton("\u2302")
        self._home_btn.setFixedWidth(32)
        self._home_btn.clicked.connect(self._on_home)
        path_row.addWidget(self._up_btn)
        path_row.addWidget(self._path_input, 1)
        path_row.addWidget(self._home_btn)
        layout.addLayout(path_row)

        # file tree
        self._tree = QTreeWidget()
        self._tree.setHeaderLabels(["\u540D\u79F0", "\u5927\u5C0F", "\u6743\u9650"])
        self._tree.setColumnWidth(0, 260)
        self._tree.setColumnWidth(1, 80)
        self._tree.setColumnWidth(2, 100)
        self._tree.itemDoubleClicked.connect(self._on_item_double_click)
        layout.addWidget(self._tree, 1)

        # action buttons
        btn_row = QHBoxLayout()
        btn_row.setSpacing(6)
        self._upload_btn = QPushButton("\U0001F4E4 \u4E0A\u4F20")
        self._upload_btn.clicked.connect(self._on_upload)
        self._download_btn = QPushButton("\U0001F4E5 \u4E0B\u8F7D")
        self._download_btn.clicked.connect(self._on_download)
        self._delete_btn = QPushButton("\U0001F5D1 \u5220\u9664")
        self._delete_btn.clicked.connect(self._on_delete)
        self._refresh_btn = QPushButton("\U0001F504 \u5237\u65B0")
        self._refresh_btn.clicked.connect(self._refresh)
        btn_row.addWidget(self._upload_btn)
        btn_row.addWidget(self._download_btn)
        btn_row.addWidget(self._delete_btn)
        btn_row.addStretch()
        btn_row.addWidget(self._refresh_btn)
        layout.addLayout(btn_row)

    def _connect_signals(self):
        dm = self._app.device_manager
        dm.device_connected.connect(lambda s: self._refresh())
        dm.device_disconnected.connect(lambda: self._tree.clear())

    def _current_serial(self) -> str | None:
        dm = self._app.device_manager
        if dm.is_connected and dm.current:
            return dm.current.serial
        return None

    def _refresh(self):
        serial = self._current_serial()
        if not serial:
            self._tree.clear()
            return
        entries = self._mgr.list_dir(self._cwd, serial=serial)
        self._tree.clear()
        for entry in entries:
            icon = "\U0001F4C1" if entry.is_dir else "\U0001F4C4"
            item = QTreeWidgetItem([f"{icon} {entry.name}", entry.size_display, entry.permissions])
            item.setData(0, Qt.ItemDataRole.UserRole, entry)
            self._tree.addTopLevelItem(item)
        self._path_input.setText(self._cwd)

    def _navigate(self, path: str):
        self._cwd = path.rstrip("/") or "/"
        self._refresh()

    def _on_up(self):
        if self._cwd == "/":
            return
        parent = os.path.dirname(self._cwd.rstrip("/")) or "/"
        self._navigate(parent)

    def _on_home(self):
        self._navigate("/sdcard")

    def _on_path_enter(self):
        self._navigate(self._path_input.text().strip() or "/sdcard")

    def _on_item_double_click(self, item: QTreeWidgetItem, column: int):
        entry: FileEntry = item.data(0, Qt.ItemDataRole.UserRole)
        if entry and entry.is_dir:
            self._navigate(f"{self._cwd.rstrip('/')}/{entry.name}")

    def _selected_entry(self) -> FileEntry | None:
        items = self._tree.selectedItems()
        if items:
            return items[0].data(0, Qt.ItemDataRole.UserRole)
        return None

    def _selected_remote_path(self) -> str | None:
        entry = self._selected_entry()
        if entry:
            return f"{self._cwd.rstrip('/')}/{entry.name}"
        return None

    def _on_upload(self):
        serial = self._current_serial()
        if not serial:
            return
        local, _ = QFileDialog.getOpenFileName(self, "\u4E0A\u4F20\u6587\u4EF6")
        if not local:
            return
        remote = f"{self._cwd.rstrip('/')}/{os.path.basename(local)}"
        self._run_transfer("push", local, remote, serial)

    def _on_download(self):
        serial = self._current_serial()
        remote = self._selected_remote_path()
        if not serial or not remote:
            return
        entry = self._selected_entry()
        if entry and entry.is_dir:
            self.log_line.emit("[\u6587\u4EF6] \u4E0D\u652F\u6301\u4E0B\u8F7D\u6587\u4EF6\u5939")
            return
        local, _ = QFileDialog.getSaveFileName(
            self, "\u4E0B\u8F7D\u5230", entry.name if entry else "download")
        if not local:
            return
        self._run_transfer("pull", remote, local, serial)

    def _on_delete(self):
        serial = self._current_serial()
        remote = self._selected_remote_path()
        if not serial or not remote:
            return
        r = QMessageBox.question(
            self, "\u786E\u8BA4\u5220\u9664",
            f"\u5220\u9664 {remote}?", QMessageBox.Yes | QMessageBox.No,
        )
        if r == QMessageBox.Yes:
            self._run_transfer("delete", remote, "", serial)

    def _run_transfer(self, action: str, src: str, dst: str, serial: str):
        if self._transfer_thread and self._transfer_thread.isRunning():
            return
        self._transfer_thread = _TransferThread(self._mgr, action, src, dst, serial, self)
        self._transfer_thread.finished.connect(self._on_transfer_done)
        self._transfer_thread.finished.connect(self._transfer_thread.deleteLater)
        self._transfer_thread.start()
        self.log_line.emit(f"[\u6587\u4EF6] {action} {src}")

    def _on_transfer_done(self, ok: bool, action: str):
        self._transfer_thread = None
        icon = "OK" if ok else "FAIL"; self.log_line.emit(f"[\u6587\u4EF6] {action} {icon}")
        self._refresh()


