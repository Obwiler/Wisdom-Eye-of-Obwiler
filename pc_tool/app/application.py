"""Application singleton — owns global references and a signal bus."""

import json
import os
from pathlib import Path
from typing import Optional

from PySide6.QtCore import QObject, Signal

from app.theme import ThemeManager

VERSION = "0.2.0"
from core.constants import WEOConfig, load_config

APP_NAME = f"奥贝之眼 · WEO v{VERSION}"
CONFIG_DIR = os.path.join(os.environ.get("APPDATA", os.path.expanduser("~")), "WEO")
CONFIG_FILE = "weo_pc_config.json"


class App(QObject):
    """Application singleton."""

    device_connected = Signal(str)
    device_disconnected = Signal()
    status_message = Signal(str)
    theme_changed = Signal()

    _instance: Optional["App"] = None

    def __init__(self, base_dir: Path, parent=None):
        if App._instance is not None:
            raise RuntimeError("App is a singleton — use App.instance()")
        super().__init__(parent)
        App._instance = self

        self._base_dir = base_dir
        self._theme: Optional[ThemeManager] = None
        self._config: Optional[WEOConfig] = None
        self._persistent: dict = {}
        self._adb_client = None
        self._device_mgr = None

    @classmethod
    def instance(cls) -> "App":
        if cls._instance is None:
            raise RuntimeError("App not initialized")
        return cls._instance

    @property
    def base_dir(self) -> Path:
        return self._base_dir

    @property
    def theme(self) -> ThemeManager:
        if self._theme is None:
            raise RuntimeError("ThemeManager not initialized")
        return self._theme

    @property
    def config(self) -> WEOConfig:
        if self._config is None:
            self._config = load_config()
        return self._config

    @property
    def persistent(self) -> dict:
        return self._persistent

    @property
    def adb_client(self):
        if self._adb_client is None:
            from core.adb_client import AdbClient
            import sys as _sys
            # Prefer external path (next to exe) for frozen builds
            # because _MEIPASS temp dir can have permission issues
            if getattr(_sys, "frozen", False):
                adb_path = str(self.external_path(self.config.adb_exe_relative))
                if not Path(adb_path).exists():
                    adb_path = str(self._base_dir / self.config.adb_exe_relative)
            else:
                adb_path = str(self._base_dir / self.config.adb_exe_relative)
            self._adb_client = AdbClient(adb_path)
        return self._adb_client

    @property
    def device_manager(self):
        if self._device_mgr is None:
            from core.device import DeviceManager
            self._device_mgr = DeviceManager(self.adb_client, self)
            self._device_mgr.device_connected.connect(self._on_device_connected)
            self._device_mgr.device_disconnected.connect(self._on_device_disconnected)
        return self._device_mgr

    def initialize(self):
        theme_path = self._base_dir / "resources" / "theme.json"
        self._theme = ThemeManager(theme_path)
        self._persistent = self._load_persistent_config()

    def shutdown(self):
        self._save_persistent_config()

    def save_state(self, key: str, value):
        self._persistent[key] = value

    def load_state(self, key: str, default=None):
        return self._persistent.get(key, default)

    def _load_persistent_config(self) -> dict:
        os.makedirs(CONFIG_DIR, exist_ok=True)
        path = os.path.join(CONFIG_DIR, CONFIG_FILE)
        if os.path.exists(path):
            try:
                with open(path, "r", encoding="utf-8") as f:
                    return json.load(f)
            except (json.JSONDecodeError, OSError):
                pass
        return {}

    def _save_persistent_config(self):
        os.makedirs(CONFIG_DIR, exist_ok=True)
        path = os.path.join(CONFIG_DIR, CONFIG_FILE)
        try:
            with open(path, "w", encoding="utf-8") as f:
                json.dump(self._persistent, f, ensure_ascii=False, indent=2)
        except OSError:
            pass

    def _on_device_connected(self, serial: str):
        self.device_connected.emit(serial)

    def _on_device_disconnected(self):
        self.device_disconnected.emit()

    def resolve_path(self, *parts: str) -> Path:
        return self._base_dir.joinpath(*parts)

    def icon_path(self) -> Path:
        return self.resolve_path("resources", "app.ico")

    def external_path(self, *parts: str) -> Path:
        """Resolve path relative to the exe/script directory — NOT _MEIPASS.
        
        Use for files that should live alongside the distributable
        rather than be bundled inside it (e.g. APK).
        """
        import sys as _sys
        if getattr(_sys, "frozen", False):
            base = Path(_sys.executable).resolve().parent
        else:
            base = self._base_dir
        return base.joinpath(*parts)
