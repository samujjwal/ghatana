"""Message module for Python fixture."""

from dataclasses import dataclass


@dataclass
class Message:
    """A simple message class for testing."""
    text: str

    def greet(self) -> str:
        """Return a greeting message."""
        return f"Hello, {self.text}!"
