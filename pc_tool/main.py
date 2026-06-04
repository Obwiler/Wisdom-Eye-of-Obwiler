"""WEO PC 工具 — 入口 (new architecture)"""

import sys
import traceback
from pathlib import Path

# Ensure the project root is on sys.path (needed for embeddable Python with python311._pth)
_base_dir = Path(__file__).resolve().parent
if str(_base_dir) not in sys.path:
    sys.path.insert(0, str(_base_dir))

from PySide6.QtWidgets import QApplication
from PySide6.QtGui import QIcon

from app.application import App


def _resolve_base_dir() -> Path:
    if getattr(sys, "frozen", False):
        return Path(sys._MEIPASS)
    return Path(__file__).resolve().parent


def _excepthook(exc_type, exc_value, exc_tb):
    """Log unhandled exceptions from Qt threads."""
    msg = "".join(traceback.format_exception(exc_type, exc_value, exc_tb))
    try:
        app_inst = App.instance()
        app_inst.status_message.emit(f"未捕获异常: {exc_value}")
    except RuntimeError:
        pass
    sys.__excepthook__(exc_type, exc_value, exc_tb)
    print(msg, file=sys.stderr)


def main():
    base_dir = _resolve_base_dir()

    app = App(base_dir)
    app.initialize()

    qapp = QApplication(sys.argv)
    qapp.setApplicationName("奥贝之眼 · WEO")

    icon_path = app.icon_path()
    if icon_path.exists():
        qapp.setWindowIcon(QIcon(str(icon_path)))

    qapp.setStyleSheet(app.theme.qss)

    sys.excepthook = _excepthook

    from ui.main_window import MainWindow
    window = MainWindow()
    window.show()

    exit_code = qapp.exec()
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
