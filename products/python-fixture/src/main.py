"""FastAPI service for Python fixture."""

from fastapi import FastAPI
from python_fixture.message import Message

app = FastAPI(title="Python Fixture API")


@app.get("/")
def read_root() -> dict[str, str]:
    """Root endpoint."""
    return {"message": "Python Fixture Service"}


@app.get("/greet/{name}")
def greet(name: str) -> dict[str, str]:
    """Greet endpoint."""
    msg = Message(text=name)
    return {"greeting": msg.greet()}


@app.get("/health")
def health() -> dict[str, str]:
    """Health check endpoint."""
    return {"status": "healthy"}


def main() -> None:
    """Main entry point."""
    import uvicorn

    uvicorn.run(app, host="127.0.0.1", port=8081)


if __name__ == "__main__":
    main()
