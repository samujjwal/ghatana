"""Tests for Message class."""

from python_fixture.message import Message


def test_greet() -> None:
    """Test greet method."""
    msg = Message(text="World")
    assert msg.greet() == "Hello, World!"


def test_message_creation() -> None:
    """Test message creation."""
    msg = Message(text="Test")
    assert msg.text == "Test"
