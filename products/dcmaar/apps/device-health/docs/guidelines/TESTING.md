# DCMaar – Device Health Extension – Testing Guidelines

## 1. Goals

- Ensure the extension UI, pipelines, and plugins behave correctly and reliably across browsers.

## 2. Unit & Integration Tests

- Use Vitest for React component and logic tests.
- Cover plugin and connector integration via in-process tests where feasible.

## 3. E2E Tests

- Use Playwright for end-to-end tests of onboarding, alerts, export, settings, and lifecycle scenarios.
- Use the existing scripts (`test:e2e:*`) to orchestrate these flows.

This document is self-contained and explains how to test the Device Health extension.
