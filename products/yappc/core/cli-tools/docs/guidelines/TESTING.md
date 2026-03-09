# YAPPC Core – KG CLI Tools – Testing Guidelines

## 1. Goals

- Ensure CLI commands behave correctly and predictably for typical and edge-case inputs.

## 2. Unit Tests

- Test command handlers and helper functions without invoking real subprocesses or external systems where possible.
- Use in-memory or temporary directories for filesystem-related operations.

## 3. Integration Tests

- For key commands, run end-to-end tests that:
  - Invoke the CLI entry point with sample arguments.
  - Assert on exit codes and selected output.

## 4. Determinism & Isolation

- Keep tests deterministic and independent of local machine configuration.
- Avoid relying on global state (e.g., environment variables) unless explicitly controlled in tests.

This document is self-contained and describes how to test the KG CLI Tools module.
