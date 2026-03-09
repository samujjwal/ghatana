# DCMaar – Plugin Abstractions – Testing Guidelines

## 1. Goals

- Ensure plugin abstraction interfaces and reference implementations behave as expected and are safe for plugin authors.

## 2. Unit Tests

- Use Jest to cover:
  - Interface default implementations.
  - Helper functions and reference implementations.

## 3. Integration Tests

- Where practical, simulate host–plugin interactions using these abstractions.

This document is self-contained and explains how to test `@dcmaar/plugin-abstractions`.
