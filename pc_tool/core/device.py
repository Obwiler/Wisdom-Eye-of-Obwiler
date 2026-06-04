"""Device model and connection manager."""

from __future__ import annotations

import logging
from dataclasses import dataclass
from typing import Optional

from PySide6.QtCore import QObject, Signal, QTimer

from core.adb_client import AdbClient, AdbError
from core.constants import WEOConfig

_log = logging.getLogger(__name__)


@dataclass
class DeviceInfo:
    """Snapshot of a connected device'"s state."""

    serial: str = ""
    model: str = ""
    android_version: str = ""
    sdk_level: int = 0
    battery_level: int = -1
    battery_charging: bool = False
    storage_total: str = ""
    storage_used: str = ""
    ram_total: str = ""
    screen_resolution: str = ""
    screen_density: int = 0
    wifi_ssid: str = ""
    weo_installed: bool = False
    weo_running: bool = False

    @classmethod
    def empty(cls) -> "DeviceInfo":
        return cls()


class DeviceManager(QObject):
    """Manages device discovery and periodic polling."""

    device_connected = Signal(str)
    device_disconnected = Signal()
    device_updated = Signal(DeviceInfo)
    error_occurred = Signal(str)

    POLL_INTERVAL_FAST = 3000
    POLL_INTERVAL_NORMAL = 15000

    def __init__(self, adb: AdbClient, parent=None):
        super().__init__(parent)
        self._adb = adb
        self._current: Optional[DeviceInfo] = None
        self._fast_count = 0
        self._config = WEOConfig()

        self._timer = QTimer(self)
        self._timer.setInterval(self.POLL_INTERVAL_NORMAL)
        self._timer.timeout.connect(self._poll)

    @property
    def current(self) -> Optional[DeviceInfo]:
        return self._current

    @property
    def is_connected(self) -> bool:
        return self._current is not None and bool(self._current.serial)

    def start(self):
        self._poll()
        self._timer.start()

    def stop(self):
        self._timer.stop()

    def _poll(self):
        try:
            devices = self._adb.devices()
        except AdbError as e:
            _log.warning("Device poll failed: %s", e)
            return

        if not devices:
            if self._current is not None:
                self._current = None
                self.device_disconnected.emit()
                self._enter_fast_poll()
            return

        serial = devices[0]["serial"]
        if self._current is None or self._current.serial != serial:
            self._current = DeviceInfo(serial=serial)
            self.device_connected.emit(serial)
        self._refresh_info()
        self._exit_fast_poll()

    def _refresh_info(self):
        if not self._current:
            return
        serial = self._current.serial
        updated = DeviceInfo(serial=serial)
        try:
            updated.model = self._adb.getprop("ro.product.model", serial=serial)
            updated.android_version = self._adb.getprop("ro.build.version.release", serial=serial)
            sdk = self._adb.getprop("ro.build.version.sdk", serial=serial)
            updated.sdk_level = int(sdk) if sdk.isdigit() else 0
            updated.screen_resolution = self._adb.shell("wm size", serial=serial).replace("Physical size: ", "").strip()
            density = self._adb.shell("wm density", serial=serial)
            for line in density.split("\n"):
                line = line.strip()
                if "Override density:" in line:
                    val = line.split(":")[-1].strip()
                    if val.isdigit():
                        updated.screen_density = int(val)
                        break
                elif "Physical density:" in line:
                    val = line.split(":")[-1].strip()
                    if val.isdigit():
                        updated.screen_density = int(val)
        except (AdbError, ValueError):
            pass

        # Battery info
        try:
            batt = self._adb.shell("dumpsys battery", serial=serial)
            for line in batt.split("\n"):
                line = line.strip()
                if line.startswith("level:"):
                    updated.battery_level = int(line.split(":")[-1].strip())
                elif line.startswith("AC powered:") or line.startswith("USB powered:"):
                    if "true" in line.lower():
                        updated.battery_charging = True
                elif line.startswith("status:"):
                    status = line.split(":")[-1].strip()
                    if status in ("2", "5"):  # Charging or Full
                        updated.battery_charging = True
        except AdbError:
            pass

        # Storage info (df -h /sdcard)
        try:
            df = self._adb.shell("df -h /sdcard", serial=serial)
            for line in df.split("\n"):
                parts = line.split()
                if len(parts) >= 4 and ("/sdcard" in line or "Filesystem" not in line):
                    updated.storage_total = parts[1]
                    updated.storage_used = parts[2]
                    break
        except AdbError:
            pass

        # WEO app status
        try:
            updated.weo_installed = self._adb.is_installed(self._config.pkg_name, serial=serial)
        except AdbError:
            pass

        if updated.weo_installed:
            try:
                pid = self._adb.shell(f"pidof {self._config.pkg_name}", serial=serial)
                updated.weo_running = bool(pid)
            except AdbError:
                pass

        self._current = updated
        self.device_updated.emit(updated)

    def _enter_fast_poll(self):
        self._fast_count = 0
        self._timer.setInterval(self.POLL_INTERVAL_FAST)

    def _exit_fast_poll(self):
        self._fast_count = 0
        self._timer.setInterval(self.POLL_INTERVAL_NORMAL)

    def keyevent(self, code: int):
        if self._current:
            self._adb.shell(f"input keyevent {code}", serial=self._current.serial)

