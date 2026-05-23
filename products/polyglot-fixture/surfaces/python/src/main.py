"""
Polyglot Fixture Python Worker

Demonstrates Python worker surface integration with the Ghatana platform.

@doc.type module
@doc.purpose Python worker surface for polyglot fixture product
@doc.layer product
@doc.pattern Service
"""

from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional
import time

app = FastAPI(title="Polyglot Fixture Python Worker")


class HealthResponse(BaseModel):
    status: str
    service: str
    version: str


class PingResponse(BaseModel):
    message: str
    timestamp: int


@app.get("/health", response_model=HealthResponse)
async def health():
    """Health check endpoint"""
    return HealthResponse(
        status="UP",
        service="python-worker",
        version="1.0.0"
    )


@app.get("/api/ping", response_model=PingResponse)
async def ping():
    """Ping endpoint for connectivity testing"""
    return PingResponse(
        message="pong",
        timestamp=int(time.time())
    )


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="127.0.0.1", port=3003)
