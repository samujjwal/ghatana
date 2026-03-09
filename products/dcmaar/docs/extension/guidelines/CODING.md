# DCMaar Browser Extension – Coding Guidelines

## 1. Scope

These guidelines apply to the DCMaar Browser Extension implementation in `framework/browser-extension`.

## 2. Platform & APIs

- Use WebExtensions MV3 APIs for browser integration.
- Keep browser-specific conditionals minimal; rely on polyfills or shared helpers where possible.

## 3. Architecture & Layering

- Separate concerns into:
  - Background/service worker logic.
  - Content scripts.
  - UI (popup/options).
  - Messaging and configuration utilities.
- Avoid leaking page context into background logic without explicit redaction/filters.

## 4. Dependencies

- Use minimal dependencies; prioritize standard Web APIs and small helper libraries.
- Do not embed server or agent credentials in code.

## 5. Security & Privacy

- Enforce domain allowlists and path-level rules.
- Redact or avoid collecting sensitive content; never log raw page data.

This document is self-contained and defines how to structure code in the DCMaar Browser Extension.
