"""Collapsible log panel — shows at the bottom of the window."""

from PySide6.QtWidgets import QWidget, QVBoxLayout, QTextEdit


class LogPanel(QWidget):
    """Bottom panel with scrollable log view."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self._setup_ui()

    def _setup_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.setSpacing(0)

        self._log_view = QTextEdit()
        self._log_view.setReadOnly(True)
        self._log_view.document().setMaximumBlockCount(500)

        layout.addWidget(self._log_view)

    def append(self, text: str):
        self._log_view.append(text)
        sb = self._log_view.verticalScrollBar()
        sb.setValue(sb.maximum())

    def clear(self):
        self._log_view.clear()
