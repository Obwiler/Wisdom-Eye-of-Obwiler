"""ADB shell client — wraps AdbClient.shell() with command safety and history."""

from collections import deque
from core.adb_client import AdbClient, AdbError

# commands that require extra caution
_DANGEROUS_PREFIXES = ("rm -rf /", "dd if=", "mkfs.", "reboot", "pm uninstall -k --user 0")


class ShellClient:
    """Thin wrapper around AdbClient shell with history tracking."""

    MAX_HISTORY = 200

    def __init__(self, adb: AdbClient):
        self._adb = adb
        self._history: deque[str] = deque(maxlen=self.MAX_HISTORY)
        self._history_idx = -1

    @property
    def history(self) -> list[str]:
        return list(self._history)

    def is_dangerous(self, command: str) -> bool:
        cmd = command.strip()
        for prefix in _DANGEROUS_PREFIXES:
            if cmd.startswith(prefix):
                return True
        return False

    def run(self, command: str, serial: str | None = None, timeout: int = 20) -> str:
        """Execute a shell command. Returns combined stdout+stderr or error text.

        Dangerous commands are blocked and return a warning string.
        """
        cmd = command.strip()
        if not cmd:
            return ""
        if self.is_dangerous(cmd):
            return f"[BLOCKED] Dangerous command: {cmd[:60]}"

        self._history.append(cmd)
        self._history_idx = -1
        try:
            return self._adb.shell(cmd, serial=serial, timeout=timeout)
        except AdbError as e:
            return f"[ERROR] {e}"

    def history_prev(self) -> str | None:
        """Navigate to previous history entry. Returns None at boundaries."""
        if not self._history:
            return None
        if self._history_idx == -1:
            self._history_idx = len(self._history) - 1
        elif self._history_idx > 0:
            self._history_idx -= 1
        else:
            return None
        return self._history[self._history_idx]

    def history_next(self) -> str | None:
        """Navigate to next history entry."""
        if not self._history or self._history_idx == -1:
            return None
        if self._history_idx < len(self._history) - 1:
            self._history_idx += 1
            return self._history[self._history_idx]
        self._history_idx = -1
        return None
