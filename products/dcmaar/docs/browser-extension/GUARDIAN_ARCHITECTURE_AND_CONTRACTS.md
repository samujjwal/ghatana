# Guardian Architecture & Contracts

This document summarizes how the **Guardian** parental control product is layered on top of the **DCMAAR** platform and which contracts it uses.

It is derived from the detailed integration guide in `docs/PC_REQ/GUARDIAN_DCMAAR_INTEGRATION.md` and serves as a concise, canonical reference for architecture and contracts.

---

## 1. High-Level Architecture

Guardian is a **product** built on the DCMAAR platform, not a fork of it.

- **Platform (DCMAAR)**
  - Agents (Rust agent-daemon, browser extension framework).
  - Server stack (Java + Node.js) exposing `dcmaar.v1` ingest, query, and policy APIs.
  - Desktop app (Tauri + React) as a framework-owned UI shell.

- **Guardian (Product)**
  - **Backend** – Java/ActiveJ-based services for:
    - Policy management & evaluation.
    - Usage ingest & analytics on top of ClickHouse/Postgres.
    - Device and child status tracking.
  - **Agents** – mobile apps and desktop integration:
    - Android: Kotlin app using DCMAAR agent patterns.
    - iOS: Swift app using ScreenTime / FamilyControls.
    - Windows/desktop: DCMAAR Rust agent daemon + desktop app (no dedicated C# agent).
  - **Dashboards & Apps** – React-based parent UIs:
    - Web dashboards.
    - Optional views plugged into DCMAAR desktop via UI plugin/config slots.

Guardian does **not** own its own desktop binary; it reuses shared dashboards and the framework desktop where a desktop experience is required.

---

## 2. Platform Contracts (Consumed by Guardian)

Guardian builds on DCMAAR’s `dcmaar.v1` contracts, defined under `products/dcmaar/contracts/proto-core/dcmaar/v1`.

Key groups:

- **Telemetry & Events**
  - `metrics.proto`, `events.proto`, `correlation.proto`.
  - Used for storing device usage, status, and audit events.

- **Ingest**
  - `ingest.proto` – `IngestService` + envelope-based ingestion:
    - `MetricEnvelopeBatch`, `EventEnvelopeBatch`.
  - Guardian agents and extensions send usage/health data through these APIs.

- **Query**
  - `query.proto` – `QueryService`:
    - Time-range queries, aggregations, filters.
  - Guardian dashboards query usage metrics/events via these contracts.

- **Policy & Actions**
  - `policy.proto` – generic policy model and `PolicyService`.
  - `actions.proto` – generic actions API for automation/remediation.
  - Guardian can reuse DCMAAR’s policy engine where appropriate, or layer its own domain-specific rules on top.

- **Extension & Desktop**
  - `extension.proto` – base contracts for browser/extension plugins.
  - `desktop.proto` – desktop ↔ server/agent contracts.
  - Guardian’s extension- and desktop-facing UIs integrate through these surfaces.

Guardian **does not modify** these platform contracts; it only consumes them.

---

## 3. Guardian-Specific Contracts

Guardian defines its own domain-specific contracts under a separate logical namespace, e.g. `guardian.*`.

### 3.1 Core Messages

The integration guide defines these core concepts:

- `UsageEvent`
  - Represents granular usage (app, website, duration) per device/child.
- `PolicyAction`
  - Represents the outcome of evaluating Guardian policies, e.g.: `ALLOW`, `BLOCK`, `LIMIT_TIME`, `NOTIFY_PARENT`.
- `DevicePolicy`
  - Configuration for a child’s device:
    - App/website limits and schedules.

These types are **compatible with DCMAAR telemetry IDs and timestamps**, so they can be stored and correlated with platform data.

### 3.2 Storage-Level Schema (PostgreSQL + ClickHouse)

Guardian’s backend schema targets two stores:

- **PostgreSQL** – configuration and relationships:
  - Families, family members, devices.
  - Policies and rules.
  - Audit log.

- **ClickHouse** – time-series usage data:
  - `device_usage` table containing usage metrics per device/app/website.
  - `device_status` table for online/offline and status tracking.

The schema in the integration guide is the canonical source; future migrations should be kept in `apps/guardian/backend`.

### 3.3 Separation from DCMAAR Framework

- Guardian-specific tables such as `usage_sessions`, `usage_events`, and policy tables live in Guardian’s own database schema and migrations.
- DCMAAR’s core agent storage (`agent-storage` crate) now only owns **product-neutral** tables.
- Any Guardian-specific SQLite schemas used by desktop agents are defined in Guardian code (e.g., `apps/guardian/apps/agent-desktop`), not in framework crates.

---

## 4. Data Flows

### 4.1 Device → Guardian Backend via DCMAAR

1. **Collection**
   - Mobile and desktop agents collect raw events (apps, websites, screen time) following DCMAAR’s agent patterns.
2. **Ingest**
   - Agents send events as DCMAAR `EventEnvelopeBatch` / `MetricEnvelopeBatch` to the DCMAAR ingest service.
3. **Storage & Analytics**
   - DCMAAR server writes normalized telemetry into ClickHouse tables.
   - Guardian backend queries these tables and/or maintains Guardian-specific views.
4. **Policy Evaluation**
   - Guardian backend evaluates `guardian.*` policies (time limits, schedules, filters).
   - Returns `PolicyAction` decisions to agents via gRPC.

### 4.2 Guardian Dashboards

- Guardian dashboards issue queries through DCMAAR’s `QueryService` and Guardian’s own backend APIs.
- Device- and child-level analytics are built on joins between Guardian relational tables and DCMAAR time-series data.

---

## 5. Design Rules

- **No DCMAAR core dependencies on Guardian**
  - Framework crates (agent, server, desktop, extension) must not import `guardian.*` packages.
  - Guardian-specific plugins, migrations, and schemas live under `apps/guardian/**`.

- **Contracts-first**
  - All new Guardian features should start from clear contracts (protobuf or TS interfaces), then be wired into backend, agents, and UIs.
  - Cross-product contracts must be in `dcmaar.v1` rather than ad-hoc message shapes.

- **Extensibility via Plugins**
  - Guardian uses DCMAAR’s plugin mechanisms:
    - Agent plugins for usage collection and enforcement.
    - Extension plugins for browser-side behavior.
    - Desktop UI plugins/config for dashboards (when using desktop).

This document should be kept in sync with the integration guide and updated as new Guardian capabilities and contracts are introduced.
