import json
import os
from dataclasses import dataclass
from pathlib import Path


@dataclass
class WEOConfig:
    pkg_name: str = "com.obwiler.weo"
    activity: str = "com.obwiler.weo/.MainActivity"
    config_device_dir: str = "/sdcard/Android/data/com.obwiler.weo/files"
    config_device_file: str = "weo_config.json"
    config_broadcast_action: str = "com.obwiler.weo.CONFIG_UPDATED"
    apk_filename: str = "WEO-debug.apk"
    adb_exe_relative: str = "lib/scrcpy/adb.exe"
    scrcpy_exe_relative: str = "lib/scrcpy/scrcpy.exe"
    scrcpy_max_size: int = 640

    def json_path(self, base_dir: Path) -> Path:
        return base_dir / self.config_device_file


def load_config(config_path: str | None = None) -> WEOConfig:
    cfg = WEOConfig()
    if config_path and os.path.exists(config_path):
        with open(config_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
            for k, v in data.items():
                if hasattr(cfg, k):
                    setattr(cfg, k, v)
    return cfg

