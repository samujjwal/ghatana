# Integration Guide – @ghatana/ui

This document describes how to integrate `@ghatana/ui` with platform‑level collaboration features and surrounding systems. It focuses on the collaboration integration API implemented in `src/integration/collaboration.ts` and is intended for application and platform engineers.

---

## 1. Collaboration Features Overview

`@ghatana/ui` exposes a high‑level **Collaboration Integration** facade that groups several services:

- `ThemeSynchronizationService` – real‑time theme/token update propagation.
- `ComponentUsageAnalyticsService` – component usage and props analytics.
- `DesignSystemVersioningService` – design‑system version history and compatibility checks.
- `CrossApplicationConsistencyService` – cross‑app adoption and compliance reporting.
- `CollaborationIntegration` – convenience object that wires these services together.

These services are **headless**: they do not depend on any particular app framework beyond the browser APIs used in `ThemeSynchronizationService` (WebSocket). Apps are expected to:

- Provide **persistence and transport** (e.g. actual analytics backend, REST/WebSocket endpoints).
- Decide when and where to emit events (e.g. from routers, app shells, or UI wrappers).

---

## 2. Real‑Time Theme Synchronization

### 2.1 Concepts & Types

Key types for theme synchronization:

- `ThemeUpdateEvent` – a single update notification containing:
  - `type`: `"token-update" | "theme-create" | "theme-delete" | "theme-activate"`.
  - `changes`: array of `ThemeChange` records (`path`, `oldValue`, `newValue`, `type`).
  - `affectedComponents`: optional list of logical component IDs.
  - `userId`, `timestamp`, `id`.
- `ThemeSubscription` – how consumers subscribe to updates:
  - `componentIds`: which components are relevant.
  - `filters`: array of `ThemeFilter` objects (`type`, `value`).

### 2.2 Service API

`ThemeSynchronizationService` supports:

- `subscribe({ userId, componentIds, filters, callback })` → subscription ID.
- `unsubscribe(id)` – stop receiving updates.
- `publishThemeUpdate(event)` – publish an update event; the service will:
  - enqueue it for processing,
  - notify matching subscribers locally,
  - broadcast over WebSocket when available.
- `applyThemeUpdate(event)` – apply a received update to the local runtime and re‑render affected components.

> **Note** > `applyThemeUpdate` currently logs token updates and re‑render operations. In a full integration, apps should connect this to the actual theme/token runtime (e.g. `@ghatana/theme` + `@ghatana/tokens`).

### 2.3 Integration Pattern (Apps)

A typical app integration will:

1. Create a single `ThemeSynchronizationService` instance at app startup (or use `CollaborationIntegration.themeSynchronization`).
2. Bridge between incoming messages (e.g. WebSocket messages, polling APIs) and `publishThemeUpdate`.
3. Hook into the local theme system so that `applyThemeUpdate` actually mutates the active theme and forces re‑rendering.

---

## 3. Component Usage Analytics

### 3.1 Events

`ComponentUsageEvent` captures usage from the app:

- Identity: `componentId`, `instanceId`, `sessionId`, optional `userId`.
- Action: `"mount" | "unmount" | "prop-change" | "interaction"`.
- Context: page URL, user agent, viewport, active theme.
- Arbitrary `properties` (usually props or interaction metadata).

### 3.2 Analytics Data

`ComponentUsageAnalytics` aggregates:

- `totalUsage`, `activeInstances`.
- `commonProps`: frequency and typical values per prop.
- `usageTrend`: high‑level usage over time.
- `performanceMetrics`: surface for render/mount times, memory, bundle size.
- `errorRate`: placeholder for future error tracking.

### 3.3 Service API

`ComponentUsageAnalyticsService` provides:

- `trackEvent(event)` – record a usage event and update analytics.
- `getAnalytics(componentId)` – analytics for a specific component.
- `getAllAnalytics()` – analytics for all tracked components.
- `generateReport(dateRange?)` – generate a summarized `UsageReport`.

Apps typically:

- Call `trackEvent` from component wrappers or app‑level hooks (e.g. when mounting, unmounting, or changing props).
- Periodically call `generateReport` and forward the result to their own analytics backend.

---

## 4. Design‑System Versioning

### 4.1 Version Model

`DesignSystemVersion` includes:

- `version` (string) and parsed `semver` (major/minor/patch).
- `changelog`: list of `ChangelogEntry` records (added/changed/deprecated/removed/fixed/security).
- `components`: per‑component version info (`ComponentVersion`).
- `breakingChanges` & `deprecations` derived from the changelog.

### 4.2 Service API

`DesignSystemVersioningService` provides:

- `createVersion(version, changelog, components)` → `DesignSystemVersion`.
- `getVersionHistory()` – sorted by timestamp.
- `getCurrentVersion()` – the active version.
- `checkCompatibility(fromVersion, toVersion)` → `CompatibilityReport` summarizing:
  - breaking changes,
  - whether migration is required,
  - migration complexity (`low | medium | high`).

This is intended to power:

- Release dashboards.
- Automated checks in CI to prevent incompatible upgrades.
- Migration tooling and UI messaging.

---

## 5. Cross‑Application Consistency

### 5.1 Application‑Level Reports

`CrossApplicationConsistencyService` aggregates information about multiple consuming apps:

- Registers apps with `registerApplication({ appId, name, version, endpoint })`.
- Analyzes each application for:
  - `componentUsage` (via integration hooks),
  - `themeCompliance` (violations/customizations),
  - app‑specific issues.

### 5.2 Consistency Reports

`generateConsistencyReport()` returns a `ConsistencyReport` with:

- Per‑app reports and scores.
- Overall platform‑level score.
- `ConsistencyIssue[]` and `ConsistencyRecommendation[]` for version mismatches, theme violations, etc.

The initial implementation uses placeholders for data acquisition. Apps or platform services should:

- Populate usage and theme data from their own observability/analytics backends.
- Consume the `ConsistencyReport` in admin dashboards or governance tooling.

---

## 6. High‑Level Facade: `CollaborationIntegration`

For convenience, `src/integration/collaboration.ts` exposes a singleton‑style facade:

- `CollaborationIntegration.themeSynchronization`
- `CollaborationIntegration.usageAnalytics`
- `CollaborationIntegration.versioning`
- `CollaborationIntegration.consistency`
- `CollaborationIntegration.initialize()` – hook for bootstrapping collaboration features.
- `CollaborationIntegration.getStatus()` – summarized collaboration status.
- `CollaborationIntegration.generateReport()` – combined usage + consistency report.

This facade is a good entry point for **platform services** and **admin UIs** that need a consolidated view over collaboration data.

---

## 7. Integration Guidelines

### 7.1 Safety & Environment

- The current implementation uses `WebSocket` and basic in‑memory stores.
- In production integrations, you should:
  - Provide configurable endpoints rather than relying on hard‑coded URLs.
  - Plug in real storage/analytics systems for events and reports.
  - Ensure PII/user identifiers are handled according to your privacy policy.

### 7.2 Recommended Usage Patterns

- **App Shell**: initialize `CollaborationIntegration` once at app startup.
- **Design System Dashboards**: call `generateReport()` on demand to show usage and compliance.
- **CI Pipelines**: use `DesignSystemVersioningService.checkCompatibility()` to guard major upgrades.

---

## 8. Future Enhancements

Planned/possible follow‑ups (subject to design decisions):

- Tight integration with `@ghatana/tokens` and `@ghatana/theme` for live token/theme updates.
- Hooks or middleware for popular frontend frameworks (React, Next.js, Vite) to emit usage events automatically.
- Visualization components in `@ghatana/ui` that render usage/consistency reports directly.

These enhancements should be tracked in `IMPLEMENTATION_GAP_TASKS.md` and product‑level design‑system plans.
