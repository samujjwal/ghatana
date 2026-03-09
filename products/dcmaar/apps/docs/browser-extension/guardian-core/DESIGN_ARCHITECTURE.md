# Guardian – Browser Extension – Design & Architecture

## 1. Purpose

`@yappc/guardian-browser-extension` is the **Guardian browser performance monitoring extension**. It captures web performance and activity metrics and forwards them via `@dcmaar/browser-extension-core`.

## 2. Responsibilities

- Collect in-browser performance metrics and events.
- Display lightweight UI for status and consent.
- Forward events using the shared browser-extension-core bridge.

## 3. Architectural Position

- React + Vite + CRXJS-based MV3 extension.
- Uses TailwindCSS, lucide-react, Recharts, and `webextension-polyfill`.
- Builds per browser (Chrome, Firefox, Edge) via dedicated scripts.

This document is self-contained and summarizes the architecture and role of the Guardian Browser Extension.
