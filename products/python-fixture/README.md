# Python Fixture Product

A minimal Python FastAPI service and library used to validate the PythonPyprojectAdapter can handle Python projects through the full lifecycle.

This fixture is backend-only: it exposes SDK and service surfaces without a web or mobile client package.

## Purpose

This fixture product validates:
- PythonPyprojectAdapter validate phase (mypy, ruff)
- PythonPyprojectAdapter test phase (pytest)
- PythonPyprojectAdapter build phase (python -m build)
- PythonPyprojectAdapter package phase (python -m build)
- Environment strategy detection (venv, uv, poetry, system)

## Structure

- `src/message.py` - Simple library with Message class and greet function
- `src/main.py` - FastAPI service with health and greet endpoints
- `pyproject.toml` - Package configuration with dependencies and tool settings
- `tests/test_message.py` - Unit tests for the Message class

## Lifecycle Phases

```bash
# Validate (runs mypy, ruff)
python -m compileall src
python -m mypy src
ruff check src

# Test (runs pytest)
pytest

# Build (runs python -m build)
python -m build

# Package (runs python -m build)
python -m build
```

## Dependencies

- fastapi
- uvicorn
- pytest
- mypy
- ruff

## Artifacts

- Library: `dist/python_fixture-0.1.0-py3-none-any.whl`
- Service: Same wheel package (Python services are packaged as wheels)
