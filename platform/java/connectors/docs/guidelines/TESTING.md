# Shared Java Connectors – Testing Guidelines

## 1. Goals

- Ensure connector helpers behave correctly and handle transient and permanent failures.

## 2. Tests

- Unit-test connector logic with mocked external systems.
- Add integration tests in consuming services against test instances of external systems where feasible.

This document is self-contained and explains how to test the Shared Java Connectors library.
