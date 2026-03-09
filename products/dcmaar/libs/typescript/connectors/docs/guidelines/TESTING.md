# DCMaar – Connectors – Testing Guidelines

## 1. Goals

- Ensure connectors behave correctly and degrade gracefully under failure.

## 2. Unit & Integration Tests

- Use Jest for unit tests on core logic.
- Add integration tests that exercise real or simulated endpoints.

## 3. Performance Tests

- Use dedicated performance tests to validate batching and throughput behavior.

This document is self-contained and explains how to test `@dcmaar/connectors`.
