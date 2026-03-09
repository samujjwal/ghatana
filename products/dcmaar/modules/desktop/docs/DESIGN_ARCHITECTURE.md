# DCMaar Framework – Desktop App – Design & Architecture

## 1. Purpose

The DCMaar Desktop application provides a **rich client** for visualizing, configuring, and interacting with the DCMaar platform. It combines a React+TypeScript UI with a Tauri-based Rust backend.

## 2. Responsibilities

- Present dashboards and workflows built on top of DCMaar data and APIs.
- Manage local and remote configuration, including agent connectivity.
- Provide desktop-native capabilities (tray icon, notifications, secure storage).

## 3. Architectural Position

- **Frontend**: React 19, TypeScript, Vite, MUI, TanStack Query, Recharts.
- **Backend (Tauri)**: Rust (`dcmaer-desktop` crate) handling gRPC, storage, and OS integration.
- Communicates with the DCMaar Server (gRPC/HTTP) and local agent where applicable.

The desktop application is a **framework asset**. Product applications (e.g., Guardian, Device Health) surface their dashboards and workflows via shared React components and desktop UI plugin/configuration slots, rather than owning separate desktop binaries.

## 4. High-Level Components

- `src/` – React UI (pages, components, state, data fetching).
- `src-tauri/` – Rust backend (gRPC clients, persistence, system integration).
- `docs/CONNECTOR_USAGE_EXAMPLES.tsx` – UI patterns for connector usage.

## 5. Design Constraints

- Separate UI concerns from backend logic; use Tauri commands as a narrow bridge.
- Respect privacy and security: avoid writing sensitive data to logs or unencrypted storage.

This document is self-contained and summarizes the architecture and responsibilities of the DCMaar Desktop app.

## 6. Adding Product Dashboards via Configuration

Product dashboards (for apps such as Device Health) are added to the desktop via configuration and small plugin modules, not by embedding product logic into the desktop core:

- Route and panel definitions are provided as configuration (JSON/YAML/TS) that the desktop reads at startup.
- Each configured dashboard maps to queries over DCMAAR contracts (`dcmaar.v1.query`, `dcmaar.v1.metrics`, `dcmaar.v1.events`).
- Product-specific React components are loaded as plugins or federated modules and mounted into predefined layout slots.
- No direct imports from `apps/*` into the desktop core are allowed; communication flows only through contracts and plugin interfaces.

### Example: Device Health dashboard config

Device Health uses the same configuration mechanism to surface device health views:

- A dashboard config object declares:
  - the route (e.g. `/device-health`),
  - the DCMAAR queries to run (metrics/events for CPU, memory, disk, etc.),
  - and the React components to render in each panel.
- The desktop reads this config at startup and wires the dashboards without any Device Health–specific code in the desktop core.
