"""Theme engine --- loads theme.json and generates QSS stylesheets."""

import json
from pathlib import Path
from PySide6.QtCore import QObject, Signal


class ThemeManager(QObject):
    """Singleton theme manager. Loads JSON theme config and generates QSS."""

    theme_changed = Signal()

    def __init__(self, theme_path: Path, parent=None):
        super().__init__(parent)
        self._path = theme_path
        self._data: dict = {}
        self._qss: str = ""
        self._load()

    # ---- properties ----

    @property
    def data(self) -> dict:
        return self._data

    @property
    def qss(self) -> str:
        return self._qss

    def color(self, key: str) -> str:
        return self._data.get("colors", {}).get(key, "#FFFFFF")

    def font(self, key: str) -> str:
        return self._data.get("fonts", {}).get(key, "Segoe UI")

    def font_size(self, key: str) -> int:
        return self._data.get("fonts", {}).get(f"size_{key}", 12)

    def spacing(self, key: str) -> int:
        return self._data.get("spacing", {}).get(key, 4)

    def radius(self, key: str) -> int:
        return self._data.get("radius", {}).get(key, 4)

    # ---- load ----

    def reload(self):
        self._load()
        self.theme_changed.emit()

    def _load(self):
        if self._path.exists():
            try:
                with open(self._path, "r", encoding="utf-8-sig") as f:
                    self._data = json.load(f)
            except (json.JSONDecodeError, OSError):
                self._data = {}
        else:
            self._data = {}
        self._data = _merge_defaults(self._data)
        self._qss = _build_qss(self._data)


# ---- defaults ----

_DEFAULTS = {
    "colors": {
        "bg_primary": "#0A0A0A",
        "bg_secondary": "#141414",
        "bg_card": "#1A1A1A",
        "accent": "#00FF00",
        "accent_dim": "#00CC00",
        "text_primary": "#E0E0E0",
        "text_secondary": "#888888",
        "border": "rgba(0,255,0,0.15)",
        "danger": "#FF4444",
        "success": "#00CC66",
    },
    "fonts": {"main": "Segoe UI", "mono": "Cascadia Code"},
}


def _merge_defaults(data: dict) -> dict:
    for section, defaults in _DEFAULTS.items():
        if section not in data:
            data[section] = {}
        for k, v in defaults.items():
            if k not in data[section]:
                data[section][k] = v
    return data


def _build_qss(cfg: dict) -> str:
    c = cfg.get("colors", {})
    bg = c.get("bg_primary", "#0A0A0A")
    bg2 = c.get("bg_secondary", "#141414")
    bg_card = c.get("bg_card", "#1A1A1A")
    accent = c.get("accent", "#00FF00")
    accent_subtle = c.get("accent_subtle", "rgba(0,255,0,0.08)")
    border = c.get("border", "rgba(0,255,0,0.15)")
    text = c.get("text_primary", "#E0E0E0")
    text2 = c.get("text_secondary", "#888888")
    danger = c.get("danger", "#FF4444")
    success = c.get("success", "#00CC66")
    r = cfg.get("radius", {}).get("sm", 4)

    return f"""
        QMainWindow, QWidget {{ background-color: {bg}; color: {text}; }}

        /* --- sidebar --- */
        #sidebar {{
            background-color: {bg2};
            border-right: 1px solid {border};
        }}
        #sidebar_btn {{
            background-color: transparent;
            border: none;
            border-left: 3px solid transparent;
            border-radius: 0px;
            padding: 6px 8px;
            color: {text2};
            text-align: left;
            font-size: 12px;
        }}
        #sidebar_btn:hover {{
            background-color: {accent_subtle};
            color: {accent};
        }}
        #sidebar_btn:checked {{
            background-color: {accent_subtle};
            border-left: 3px solid {accent};
            color: {accent};
        }}

        /* --- general buttons --- */
        QPushButton {{
            background-color: {bg2}; border: 1px solid {border};
            border-radius: {r}px; padding: 6px 14px; color: {accent};
        }}
        QPushButton:hover {{ border-color: {accent}; }}
        QPushButton:pressed {{ background-color: rgba(0,255,0,0.15); }}
        QPushButton:disabled {{ color: {text2}; border-color: {text2}; }}

        /* --- input fields --- */
        QLineEdit, QTextEdit, QPlainTextEdit {{
            background-color: {bg2}; border: 1px solid {border};
            border-radius: {r}px; padding: 4px 8px; color: {text};
        }}
        QComboBox {{
            background-color: {bg2}; border: 1px solid {border};
            border-radius: {r}px; padding: 4px 8px; color: {accent};
        }}
        QComboBox::drop-down {{ border: none; }}
        QComboBox QAbstractItemView {{
            background-color: {bg2}; color: {text}; selection-background-color: {accent_subtle};
        }}

        /* --- tab widget --- */
        QTabWidget::pane {{ border: 1px solid {border}; background-color: {bg2}; }}
        QTabBar::tab {{
            background-color: {bg}; color: {text2}; padding: 6px 16px;
            border: 1px solid transparent; border-bottom: none;
        }}
        QTabBar::tab:selected {{ color: {accent}; background-color: {bg2}; border-color: {border}; }}

        /* --- scrollbars --- */
        QScrollBar:vertical {{ background: {bg}; width: 6px; }}
        QScrollBar::handle:vertical {{ background: {border}; border-radius: 3px; }}
        QScrollBar:horizontal {{ background: {bg}; height: 6px; }}
        QScrollBar::handle:horizontal {{ background: {border}; border-radius: 3px; }}

        /* --- tree/list --- */
        QTreeWidget, QListWidget {{
            background-color: {bg2}; border: 1px solid {border};
            border-radius: {r}px; color: {text};
        }}
        QTreeWidget::item:selected, QListWidget::item:selected {{
            background-color: {accent_subtle};
        }}

        /* --- progress bar --- */
        QProgressBar {{
            background-color: {bg}; border: 1px solid {border};
            border-radius: {r}px; text-align: center; color: {text};
        }}
        QProgressBar::chunk {{ background-color: {accent}; border-radius: {r}px; }}

        /* --- slider --- */
        QSlider::groove:horizontal {{
            background: {bg}; height: 4px; border-radius: 2px;
        }}
        QSlider::handle:horizontal {{
            background: {accent}; width: 12px; height: 12px;
            margin: -4px 0; border-radius: 6px;
        }}

        /* --- group box --- */
        QGroupBox {{
            border: 1px solid {border}; border-radius: {r}px;
            margin-top: 8px; padding-top: 12px;
        }}
        QGroupBox::title {{ color: {accent}; }}

        /* --- section separators / labels --- */
        #section_label {{
            color: {accent};
            font-size: 13px;
            font-weight: bold;
            padding-top: 4px;
        }}
        #section_sep {{
            color: {border};
            margin: 4px 0px;
        }}
        #device_status, #apk_info, #build_info, #deploy_status {{
            background-color: {bg_card};
            border: 1px solid {border};
            border-radius: {r}px;
            padding: 6px 8px;
            color: {text};
            font-size: 12px;
        }}
        #log_toggle {{
            background-color: {bg2};
            border: none;
            border-radius: 0px;
            padding: 2px 8px;
            color: {text2};
            font-size: 11px;
            text-align: left;
        }}
        #log_toggle:hover {{ color: {accent}; }}
    """