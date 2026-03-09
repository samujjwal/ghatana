# Guardian – Agent Desktop – Testing Guidelines

## 1. Goals

- Verify correct desktop signal collection and normalization.
- Protect against regressions across Windows, macOS, and Linux.

## 2. Tests

- Use `tokio-test` and `mockall` for unit tests of collection logic.
- Add integration tests that simulate event flows through `agent-storage` and `agent-connectors`.

This document is self-contained and explains how to test the Guardian Agent Desktop plugin.
