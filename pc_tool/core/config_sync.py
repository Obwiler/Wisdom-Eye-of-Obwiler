"""WEO config push/pull over ADB — stateless, uses new AdbClient."""

import json
import os
import tempfile
from core.adb_client import AdbClient, AdbError
from core.constants import WEOConfig


class ConfigSync:
    """Push and pull weo_config.json to/from the device."""

    def __init__(self, adb: AdbClient, config: WEOConfig):
        self._adb = adb
        self._cfg = config

    def push(self, config_dict: dict, serial: str | None = None) -> bool:
        """Write config_dict to the device and broadcast an invalidate action."""
        with tempfile.NamedTemporaryFile(
            mode="w", suffix=".json", delete=True, encoding="utf-8"
        ) as f:
            json.dump(config_dict, f, ensure_ascii=False, indent=2)
            f.flush()
            remote = f"{self._cfg.config_device_dir}/{self._cfg.config_device_file}"
            self._adb.shell(f"mkdir -p {self._cfg.config_device_dir}", serial=serial)
            ok = self._adb.push(f.name, remote, serial=serial)
            if ok:
                self._adb.broadcast(
                    self._cfg.config_broadcast_action,
                    {"action": "invalidate"},
                    serial=serial,
                )
            return ok

    def pull(self, serial: str | None = None) -> dict | None:
        """Read config from device. Falls back to adb pull if shell cat fails."""
        remote = f"{self._cfg.config_device_dir}/{self._cfg.config_device_file}"
        # primary: shell cat
        try:
            result = self._adb.shell(f"cat {remote}", serial=serial)
            if result and "No such file" not in result:
                return json.loads(result)
        except (AdbError, json.JSONDecodeError):
            pass
        # fallback: adb pull to temp file
        tmp_path = None
        try:
            with tempfile.NamedTemporaryFile(
                mode="w", suffix=".json", delete=False, encoding="utf-8"
            ) as f:
                tmp_path = f.name
            if self._adb.pull(remote, tmp_path, serial=serial):
                with open(tmp_path, "r", encoding="utf-8") as f:
                    return json.load(f)
        except (AdbError, json.JSONDecodeError, OSError):
            pass
        finally:
            if tmp_path is not None:
                try:
                    os.unlink(tmp_path)
                except OSError:
                    pass
        return None
