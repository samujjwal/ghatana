# DCMAAR Contracts Overview

This module owns the **canonical protobuf contracts** for the DCMAAR platform. All framework components (agent-daemon, server, desktop, browser extension) and product apps (Guardian, Device Health, others) must depend on these contracts rather than defining ad-hoc message schemas.

Source protos live under:

- `products/dcmaar/contracts/proto-core/dcmaar/v1/*.proto`

Generated code is produced into language-specific modules (e.g., `core/proto/gen/rust`, `core/proto/gen/go`), but **`proto-core` is the single source of truth**.

---

## Packages and Files

All contracts share the `dcmaar.v1` package and are split by concern.

### 1. Common & Enums

- `common.proto`
  - Cross-cutting types:
    - `TenantContext`, `ResourceId`, `TimeRange`, etc.
    - Common identifiers, timestamps, pagination tokens.
- `enums.proto`
  - Shared enumerations for telemetry and policy:
    - Signal kinds, severity levels, status codes, etc.

These files are imported by most other contracts and should change rarely.

---

### 2. Telemetry & Events

- `metrics.proto`
  - Time-series metric model (counters, gauges, histograms).
  - Labels/attributes for service, instance, tenant, region, environment, etc.
- `events.proto`
  - Discrete event/log model for audit, errors, lifecycle changes.
  - Used by ingest, query, and automation layers.
- `correlation.proto`
  - Correlation identifiers and groups (e.g., `CorrelationId`, `SpanContext`).
  - Enables cross-signal correlation and end-to-end tracing across systems.

These contracts define **what** telemetry DCMAAR stores and how it can be correlated.

---

### 3. Ingest & Storage

- `ingest.proto`
  - Agent-facing ingest API messages:
    - `AgentBatch`, `IngestRequest`, `IngestResponse`.
  - Service contracts consumed by `IngestService`.
- `storage.proto`
  - Internal storage abstractions:
    - Logical models used by the server when persisting to ClickHouse/Postgres.

These contracts sit between **agents** and the **server ingest pipeline**.

---

### 4. Query & Analytics

- `query.proto`
  - Query request/response types:
    - Time-range queries, filters, aggregations, sorting, pagination.
  - Service contracts for `QueryService`.

All dashboards (desktop, web, product apps) should query via these APIs.

---

### 5. Policy & Actions

- `policy.proto`
  - Policy definitions and evaluation interfaces:
    - Subjects, resources, conditions, effects.
    - Request/response types for `PolicyService`.
- `actions.proto`
  - Action definitions and execution results:
    - Used by automation, remediation, and control-plane actions.

These contracts power **DevSecOps policies** and **automation workflows**.

---

### 6. Extension & Desktop

- `extension.proto`
  - Browser/extension-facing contracts:
    - Extension plugin interfaces, connector messages, telemetry envelopes.
  - Consumed by TypeScript runtime packages (e.g., `@dcmaar/browser-extension-core`).
- `desktop.proto`
  - Desktop app integration contracts:
    - Requests used by the desktop to talk to DCMAAR server and agents.

These files define how **UI runtimes** integrate with DCMAAR data and control-plane.

---

### 7. Sync & Control

- `sync.proto`
  - Configuration and state synchronization primitives:
    - Agent/extension config bundles.
    - Heartbeats and status updates.

Used by agents and extensions to stay in sync with the control plane.

---

## Usage Guidelines

- **Framework components** (agent-daemon, server, desktop, browser extension):
  - Must import contracts from `dcmaar.v1` only.
  - Must not define product-specific messages inside these files.
- **Product apps** (Guardian, Device Health, etc.):
  - Define their own protobuf packages (`guardian.*`, `devicehealth.*`) in their app trees.
  - Extend, wrap, or reference `dcmaar.v1` types as needed, but **do not fork** them.
- **Generation:**
  - Use the existing Buf config (`buf.gen.*.yaml`) to generate language-specific stubs.
  - Rust: `core/proto/gen/rust` (crate `dcmaar-pb` / `dcmaar_proto`).
  - Go: `core/proto/gen/go/pb` module for any remaining Go consumers.

This contracts module is the canonical reference for DCMAAR’s platform-neutral API and schema. All new framework and product work should start by adding or extending types here, rather than introducing ad-hoc schemas elsewhere.
