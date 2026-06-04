"""Status bar — single-line status at the very bottom."""

from PySide6.QtWidgets import QWidget, QHBoxLayout, QLabel


class StatusBar(QWidget):
    """Bottom status bar showing transient messages and persistent info."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()

    def _setup_ui(self):
        self.setFixedHeight(28)
        layout = QHBoxLayout(self)
        layout.setContentsMargins(12, 0, 12, 0)
        layout.setSpacing(8)

        self._message = QLabel()
        self._message.setObjectName("status_message")
        self._right_label = QLabel()
        self._right_label.setObjectName("status_right")

        layout.addWidget(self._message)
        layout.addStretch()
        layout.addWidget(self._right_label)

    def set_message(self, text: str):
        self._message.setText(text)

    def set_right(self, text: str):
        self._right_label.setText(text)
