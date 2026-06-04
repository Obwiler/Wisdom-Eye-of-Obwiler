"""ADB client — stateless, retry-aware, with startup verification."""

import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

_WIN = sys.platform == "win32"

_TIMEOUT = {
    "shell": 15,
    "push": 30,
    "install": 120,
    "uninstall": 30,
    "devices": 10,
    "version": 5,
}


class AdbError(Exception):
    """Raised when an ADB command fails."""

    def __init__(self, message: str, returncode: int = -1, stderr: str = ""):
        super().__init__(message)
        self.returncode = returncode
        self.stderr = stderr


@dataclass
class AdbResult:
    returncode: int
    stdout: str
    stderr: str

    @property
    def ok(self) -> bool:
        return self.returncode == 0


class AdbClient:
    """Stateless ADB command client.

    Usage:
        client = AdbClient("C:/tools/adb.exe")
        client.verify()
        devices = client.devices()
        client.shell("getprop ro.product.model", serial="abc123")
    """

    def __init__(self, adb_path: str):
        self._adb = adb_path

    @property
    def adb_path(self) -> str:
        return self._adb

    def _run(self, *args, timeout: int = 15) -> AdbResult:
        cmd = [self._adb, *args]
        kw = dict(capture_output=True, text=True, timeout=timeout)
        if _WIN:
            kw["creationflags"] = subprocess.CREATE_NO_WINDOW
        try:
            r = subprocess.run(cmd, **kw)
        except FileNotFoundError:
            raise AdbError(f"ADB executable not found: {self._adb}")
        except subprocess.TimeoutExpired:
            raise AdbError(f"ADB command timed out ({timeout}s): {' '.join(args)}")
        return AdbResult(r.returncode, r.stdout, r.stderr)

    def _run_for_serial(self, serial, *args, timeout: int = 15) -> AdbResult:
        cmd_args = []
        if serial:
            cmd_args += ["-s", serial]
        cmd_args += list(args)
        return self._run(*cmd_args, timeout=timeout)

    def verify(self):
        if not Path(self._adb).exists():
            raise AdbError(f"ADB binary not found at: {self._adb}")
        r = self._run("version", timeout=_TIMEOUT["version"])
        if not r.ok:
            raise AdbError(
                f"ADB version check failed (rc={r.returncode}): {r.stderr.strip()}",
                returncode=r.returncode, stderr=r.stderr,
            )

    def devices(self) -> list:
        r = self._run("devices", "-l", timeout=_TIMEOUT["devices"])
        result = []
        for line in r.stdout.strip().split("\n")[1:]:
            line = line.strip()
            if not line:
                continue
            parts = line.split()
            if len(parts) >= 2 and parts[1] == "device":
                dev = {"serial": parts[0]}
                for p in parts[2:]:
                    if ":" in p:
                        k, v = p.split(":", 1)
                        dev[k] = v
                result.append(dev)
        return result

    def first_online(self) -> Optional[str]:
        devs = self.devices()
        return devs[0]["serial"] if devs else None

    def shell(self, command: str, serial=None, timeout=None) -> str:
        t = timeout or _TIMEOUT["shell"]
        r = self._run_for_serial(serial, "shell", command, timeout=t)
        if not r.ok and r.stderr.strip():
            raise AdbError(
                f"shell '{command[:60]}' failed: {r.stderr.strip()}",
                returncode=r.returncode, stderr=r.stderr,
            )
        return r.stdout.strip()

    def push(self, local: str, remote: str, serial=None) -> bool:
        for attempt in range(2):
            r = self._run_for_serial(serial, "push", local, remote, timeout=_TIMEOUT["push"])
            if r.ok:
                return True
            if attempt == 0:
                time.sleep(0.5)
        return False

    def pull(self, remote: str, local: str, serial=None) -> bool:
        for attempt in range(2):
            r = self._run_for_serial(serial, "pull", remote, local, timeout=_TIMEOUT["push"])
            if r.ok:
                return True
            if attempt == 0:
                time.sleep(0.5)
        return False

    def install(self, apk_path: str, serial=None) -> bool:
        for attempt in range(3):
            r = self._run_for_serial(serial, "install", "-r", apk_path, timeout=_TIMEOUT["install"])
            if r.ok and "Success" in r.stdout:
                return True
            if attempt < 2:
                time.sleep(1.0 + attempt * 0.5)
        return False

    def uninstall(self, pkg: str, serial=None) -> bool:
        for attempt in range(2):
            r = self._run_for_serial(serial, "uninstall", pkg, timeout=_TIMEOUT["uninstall"])
            if r.ok and "Success" in r.stdout:
                return True
            if attempt == 0:
                time.sleep(0.5)
        return False

    def is_installed(self, pkg: str, serial=None) -> bool:
        r = self._run_for_serial(serial, "shell", "pm", "list", "packages", pkg, timeout=_TIMEOUT["shell"])
        return pkg in r.stdout

    def force_stop(self, pkg: str, serial=None):
        self._run_for_serial(serial, "shell", "am", "force-stop", pkg, timeout=_TIMEOUT["shell"])

    def start_activity(self, activity: str, serial=None):
        self._run_for_serial(serial, "shell", "am", "start", "-n", activity, timeout=_TIMEOUT["shell"])

    def broadcast(self, action: str, extras=None, serial=None):
        args = ["shell", "am", "broadcast", "-a", action]
        if extras:
            for k, v in extras.items():
                args += ["--es", k, str(v)]
        self._run_for_serial(serial, *args, timeout=_TIMEOUT["shell"])

    def getprop(self, prop: str, serial=None) -> str:
        return self.shell(f"getprop {prop}", serial=serial)

    def get_device_info(self, serial=None) -> dict:
        info = {}
        try:
            info["model"] = self.shell("getprop ro.product.model", serial=serial)
            info["android"] = self.shell("getprop ro.build.version.release", serial=serial)
        except AdbError:
            pass
        return info
