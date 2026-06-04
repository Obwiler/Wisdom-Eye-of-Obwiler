"""Screenshot capture — adb exec-out screencap -p with QThread."""


from PySide6.QtCore import QThread, Signal

from core.adb_client import AdbClient, AdbError


class ScreenshotCapture(QThread):
    """Capture a PNG screenshot from the device in a background thread."""

    finished = Signal(bool, object)  # (success, bytes_or_error_str)

    def __init__(self, adb: AdbClient, serial: str | None = None, parent=None):
        super().__init__(parent)
        self._adb = adb
        self._serial = serial

    def run(self):
        try:
            # screencap -p outputs PNG to stdout via exec-out
            import subprocess
            import sys

            cmd = [self._adb.adb_path]
            if self._serial:
                cmd += ["-s", self._serial]
            cmd += ["exec-out", "screencap", "-p"]

            kw = dict(capture_output=True, timeout=15)
            if sys.platform == "win32":
                kw["creationflags"] = subprocess.CREATE_NO_WINDOW

            r = subprocess.run(cmd, **kw)
            if r.returncode == 0 and r.stdout:
                self.finished.emit(True, r.stdout)
            else:
                err = r.stderr.decode(errors="replace") if r.stderr else "screencap returned no data"
                self.finished.emit(False, err)
        except FileNotFoundError:
            self.finished.emit(False, "ADB executable not found")
        except subprocess.TimeoutExpired:
            self.finished.emit(False, "Screenshot capture timed out (15s)")
        except Exception as e:
            self.finished.emit(False, str(e))
