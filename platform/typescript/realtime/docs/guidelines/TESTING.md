# Shared TypeScript Realtime – Testing Guidelines

## 1. Goals

- Ensure realtime helpers behave correctly under normal and adverse network conditions.

## 2. Tests

- Unit-test connection and subscription logic with mocked transports.
- Add integration tests in consuming apps for end-to-end realtime flows where feasible.

This document is self-contained and explains how to test the Shared TypeScript Realtime library.
