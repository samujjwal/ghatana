# YAPPC Core – Scaffold – Testing Guidelines

## 1. Goals

- Ensure scaffolded outputs are correct, stable, and aligned with platform standards.

## 2. Unit Tests

- Test core generation logic with representative inputs and expected outputs.
- Keep tests deterministic; avoid depending on external network or system state.

## 3. Integration Tests

- For important templates, run end-to-end scaffolding against a temporary directory and assert on:
  - File structure.
  - Key configuration and code fragments.

## 4. Backwards Compatibility

- When changing templates or generation logic, add tests that guard against unexpected breaking changes in scaffolded projects.

This document is self-contained and describes how to test the Scaffold module.
