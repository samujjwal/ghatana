# DCMaar Common – Testing Guidelines

## 1. Goals

- Ensure shared utilities and error types behave as expected and do not introduce regressions.

## 2. Unit Tests

- Cover error conversion and formatting behavior.
- Cover logging/tracing helper behavior where feasible.

## 3. Determinism

- Keep tests deterministic and fast; no network or heavy IO.

This document is self-contained and explains how to test `dcmaar-common`.
