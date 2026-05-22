"""Integration tests for Python fixture service."""

import pytest
from fastapi.testclient import TestClient
from python_fixture.main import app


@pytest.fixture
def client():
    """Create a test client."""
    return TestClient(app)


def test_root_endpoint(client):
    """Test root endpoint."""
    response = client.get("/")
    assert response.status_code == 200
    assert response.json() == {"message": "Python Fixture Service"}


def test_greet_endpoint(client):
    """Test greet endpoint."""
    response = client.get("/greet/World")
    assert response.status_code == 200
    assert "greeting" in response.json()
    assert "Hello, World!" in response.json()["greeting"]


def test_health_endpoint(client):
    """Test health endpoint."""
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "healthy"}
