"""
Tests for Polyglot Fixture Python Worker

@doc.type module
@doc.purpose Tests for Python worker surface
@doc.layer product
@doc.pattern Test
"""

import pytest
from httpx import AsyncClient
from main import app


@pytest.mark.asyncio
async def test_health_endpoint():
    """Test health endpoint returns UP"""
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "UP"
        assert data["service"] == "python-worker"


@pytest.mark.asyncio
async def test_ping_endpoint():
    """Test ping endpoint returns pong"""
    async with AsyncClient(app=app, base_url="http://test") as client:
        response = await client.get("/api/ping")
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "pong"
        assert data["timestamp"] is not None
