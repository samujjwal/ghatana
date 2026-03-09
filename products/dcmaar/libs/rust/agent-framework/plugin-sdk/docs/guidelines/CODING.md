# DCMaar Plugin SDK – Coding Guidelines

## 1. Scope

These guidelines apply to the `dcmaar-plugin-sdk` crate.

## 2. API Design

- Keep the SDK API surface **minimal and stable**.
- Favor traits and small data types over heavy dependencies.

## 3. Backwards Compatibility

- Avoid breaking existing plugin code; introduce new traits or methods instead of changing existing ones.

This document is self-contained and defines how to evolve `dcmaar-plugin-sdk`.
