# DCMaar – Types – Testing Guidelines

## 1. Goals

- Ensure type changes do not break dependent packages unexpectedly.

## 2. Tests

- Use minimal runtime tests where needed, but most validation comes from TypeScript compilation of dependents.

This document is self-contained and explains how to test `@dcmaar/types` changes safely.
