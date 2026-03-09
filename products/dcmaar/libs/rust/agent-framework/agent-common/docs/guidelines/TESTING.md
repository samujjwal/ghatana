# DCMaar Agent Common – Testing Guidelines

## 1. Goals

- Ensure shared types and helpers are correct, robust, and safe to reuse across binaries.

## 2. Unit Tests

- Cover:
  - Serialization/deserialization of core types.
  - Validation rules for configuration and runtime models.
  - Edge cases for time and UUID handling.

## 3. Integration Tests

- Where storage or gRPC features are enabled, add integration tests that exercise those paths using in-memory or temporary resources.

## 4. Determinism

- Keep tests deterministic; avoid reliance on real time or randomness without control.

This document is self-contained and explains how to test `dcmaar-agent-common`.
