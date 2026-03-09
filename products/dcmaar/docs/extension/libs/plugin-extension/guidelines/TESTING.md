# DCMaar – Plugin Extension – Testing Guidelines

## 1. Goals

- Ensure device monitoring plugins operate correctly and handle edge cases across platforms.

## 2. Unit Tests

- Use Jest to cover plugin behavior in isolation.
- Mock dependencies from `@dcmaar/types`, `@dcmaar/plugin-abstractions`, and connectors.

## 3. Integration Tests

- Where possible, simulate host–plugin interactions (e.g., within `device-health`) exercising real plugin code.

## 4. Determinism

- Avoid flaky tests by controlling time, random sources, and environment fingerprints.

This document is self-contained and explains how to test `@dcmaar/plugin-extension`.
