"""Reusable card and icon-button widgets."""

from typing import Optional

from PySide6.QtWidgets import QFrame, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QWidget


class Card(QFrame):
    """A raised card container with an optional header."""

    def __init__(self, title: str = "", parent=None):
        super().__init__(parent)
        self.setObjectName("card")
        layout = QVBoxLayout(self)
        layout.setContentsMargins(12, 10, 12, 10)
        layout.setSpacing(8)

        self._header_label: Optional[QLabel] = None
        if title:
            self._header_label = QLabel(title)
            self._header_label.setObjectName("card_header")
            layout.addWidget(self._header_label)

        self._content = QVBoxLayout()
        self._content.setSpacing(6)
        layout.addLayout(self._content)

    def add_widget(self, widget: QWidget):
        self._content.addWidget(widget)

    def add_layout(self, layout):
        self._content.addLayout(layout)

    def set_header(self, text: str):
        if self._header_label:
            self._header_label.setText(text)


class IconBtn(QPushButton):
    """Square icon button with tooltip."""

    def __init__(self, icon: str, tooltip: str = "", size: int = 36, parent=None):
        super().__init__(icon, parent)
        self.setFixedSize(size, size)
        if tooltip:
            self.setToolTip(tooltip)
        self.setObjectName("icon_btn")
