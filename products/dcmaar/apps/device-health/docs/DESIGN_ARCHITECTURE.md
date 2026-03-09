# DCMaar – Device Health Extension – Design & Architecture

## 1. Purpose

`@dcmaar/device-health-extension` is the **browser extension application** that surfaces device health monitoring to end users. It uses the DCMaar browser extension framework and plugins to capture, process, and visualize device metrics.

## 2. Responsibilities

- Host device-monitoring plugins from `@dcmaar/plugin-extension`.
- Use `@dcmaar/connectors` to communicate with backend/agent services.
- Provide a browser-based UI using `@dcmaar/browser-extension-core`, `@dcmaar/browser-extension-ui`, `@dcmaar/shared-ui-*`.

## 3. Architectural Position

- MV3/WebExtensions app built with Vite + React.
- Depends heavily on platform libs:
  - Types/config: `@dcmaar/types`, `@dcmaar/config-presets`.
  - Connectors: `@dcmaar/connectors`.
  - UI: `@dcmaar/shared-ui-core`, `@dcmaar/shared-ui-tailwind`, `@dcmaar/shared-ui-charts`.
  - Plugins: `@dcmaar/plugin-abstractions`, `@dcmaar/plugin-extension`.
  - Extension runtime: `@dcmaar/browser-extension-core`.

## 4. High-level Components

- Extension UI (React views, panels, dashboards).
- Plugin orchestration for device metrics.
- Connector configuration and usage.
- Extensive test and CI flows (Vitest, Playwright, lint/type-check, SBOM).

This document is self-contained and summarizes the architecture and responsibilities of the Device Health extension app.
