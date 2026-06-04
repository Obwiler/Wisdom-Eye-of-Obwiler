"""File manager — ADB-based file browse, push, pull, delete operations."""

from dataclasses import dataclass

from core.adb_client import AdbClient, AdbError


@dataclass
class FileEntry:
    name: str
    is_dir: bool
    size: int = 0
    permissions: str = ""
    modified: str = ""

    @property
    def size_display(self) -> str:
        if self.is_dir:
            return "<DIR>"
        if self.size < 1024:
            return f"{self.size} B"
        if self.size < 1024 * 1024:
            return f"{self.size / 1024:.1f} KB"
        return f"{self.size / (1024 * 1024):.1f} MB"


class FileManager:
    """Stateless file operations on a connected device."""

    def __init__(self, adb: AdbClient):
        self._adb = adb

    def list_dir(self, path: str, serial: str | None = None) -> list[FileEntry]:
        """List files and directories at a given path. Returns [] on error."""
        try:
            raw = self._adb.shell(f"ls -la " + chr(39) + path + "/" + chr(39), serial=serial)
        except AdbError:
            return []

        entries = []
        for line in raw.split("\n"):
            line = line.strip()
            if not line or line.startswith("total "):
                continue
            entry = self._parse_ls_line(line)
            if entry:
                entries.append(entry)
        # dirs first, then alphabetical
        entries.sort(key=lambda e: (not e.is_dir, e.name.lower()))
        return entries

    def push(self, local: str, remote: str, serial: str | None = None) -> bool:
        return self._adb.push(local, remote, serial=serial)

    def pull(self, remote: str, local: str, serial: str | None = None) -> bool:
        return self._adb.pull(remote, local, serial=serial)

    def delete(self, path: str, serial: str | None = None) -> bool:
        try:
            r = self._adb.shell(f"rm -rf {path}", serial=serial)
            return True
        except AdbError:
            return False

    def mkdir(self, path: str, serial: str | None = None) -> bool:
        try:
            self._adb.shell(f"mkdir -p {path}", serial=serial)
            return True
        except AdbError:
            return False

    @staticmethod
    def _parse_ls_line(line: str) -> FileEntry | None:
        """Parse a single line of 'ls -la' output."""
        parts = line.split()
        if len(parts) < 6:
            return None
        perms = parts[0]
        is_dir = perms.startswith("d")
        # skip the link count, owner, group
        # find size and name
        try:
            # format: perms links owner group size month day time/year name
            size = int(parts[4])
            name = " ".join(parts[7:]) if len(parts) > 7 else parts[-1]
        except (ValueError, IndexError):
            return None
        return FileEntry(
            name=name,
            is_dir=is_dir,
            size=size if not is_dir else 0,
            permissions=perms,
        )



