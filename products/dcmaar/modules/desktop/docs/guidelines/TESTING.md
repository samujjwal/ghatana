# DCMaar Desktop – Testing Guidelines

## 1. Goals

- Ensure the desktop UI and Tauri backend are reliable, responsive, and correct.

## 2. Frontend Tests

- Use Vitest and Testing Library for unit and integration tests.
- Cover key flows (navigation, forms, connector setup, dashboards).
- Use MSW or equivalent to mock server/agent APIs.

## 3. Backend (Tauri) Tests

- Use Rust tests to cover gRPC integration, config handling, and storage.
- Add integration tests for Tauri commands where feasible.

## 4. E2E and Storybook

- Use E2E tests for critical flows.
- Use Storybook for visual regression checks of core components.

This document is self-contained and explains how to test the DCMaar Desktop app.
