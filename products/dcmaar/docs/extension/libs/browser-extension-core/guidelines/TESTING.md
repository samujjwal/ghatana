# DCMaar – Browser Extension Core – Testing Guidelines

## 1. Goals

- Ensure Source–Processor–Sink pipelines behave correctly and are safe to reuse across extensions.

## 2. Unit Tests

- Use Vitest to cover:
  - Individual sources (events emitted as expected).
  - Processors (transformations and filters).
  - Sinks (correct handling of processed events).

## 3. Integration Tests

- Compose pipelines in tests to validate end-to-end behavior on sample inputs.

## 4. Determinism

- Avoid real browser/global side effects by using mocks or simulation utilities.

This document is self-contained and explains how to test `@dcmaar/browser-extension-core`.
