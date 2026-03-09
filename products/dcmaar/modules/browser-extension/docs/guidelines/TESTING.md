# DCMaar Browser Extension – Testing Guidelines

## 1. Goals

- Ensure extension behavior is correct, privacy-safe, and performant across supported browsers.

## 2. Unit & Integration Tests

- Cover:
  - Domain allowlist and redaction logic.
  - Messaging flows between background, content scripts, and UI.
- Use browser extension test harnesses or automation scripts to simulate events.

## 3. Manual / Exploratory Testing

- Validate behavior against representative sites and scenarios.
- Verify that disallowed domains are never captured.

## 4. Stability & Performance

- Watch for long-running content scripts or excessive message volume.

This document is self-contained and explains how to test the DCMaar Browser Extension.
