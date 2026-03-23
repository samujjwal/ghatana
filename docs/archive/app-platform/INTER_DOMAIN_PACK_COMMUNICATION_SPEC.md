# Inter-Domain Pack Communication Specification

**Document Type**: Normative Specification  
**Authority Level**: 3 — Normative Reference  
**Version**: 1.1.0 | **Status**: Active | **Date**: 2026-01-19  
**Owner**: AppPlatform Architecture Council  
**Canonical Path**: `products/app-platform/docs/INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`

---

## Table of Contents

1. [Purpose & Scope](#1-purpose--scope)
2. [Guiding Principles](#2-guiding-principles)
3. [Communication Patterns](#3-communication-patterns)
4. [EventBus Contract (K-05)](#4-eventbus-contract-k-05)
5. [Event Routing Rules](#5-event-routing-rules)
6. [source_domain_pack_id Convention](#6-source_domain_pack_id-convention)
7. [Cross-Pack Subscriptions](#7-cross-pack-subscriptions)
8. [Inter-Pack Dependency Declarations](#8-inter-pack-dependency-declarations)
9. [Forbidden Patterns](#9-forbidden-patterns)
10. [Security & Isolation](#10-security--isolation)
11. [Operational Observability](#11-operational-observability)
12. [API Evolution Policy](#12-api-evolution-policy)

---

## 1. Purpose & Scope

This specification defines the **only permitted patterns** by which domain packs may communicate with one another inside the AppPlatform Kernel. It is binding for all domain pack authors and all teams operating the AppPlatform.

**In scope:**

- Domain Pack → Domain Pack communication
- Domain Pack → Kernel service calls (read-only)
- Cross-pack event subscriptions

**Out of scope:**

- Kernel-internal communication (see LLD files for each K-module)
- Pack-to-external-system integration (governed by `DomainPack.integrations[]`)
- UI-layer inter-component calls (governed by frontend architecture docs)

---

## 2. Guiding Principles

| #   | Principle                     | Rationale                                                                       |
| --- | ----------------------------- | ------------------------------------------------------------------------------- |
| G-1 | **Loose Coupling via Events** | Domain packs MUST NOT call each other's internal APIs directly.                 |
| G-2 | **Async by Default**          | All inter-pack communication is asynchronous via K-05 EventBus.                 |
| G-3 | **Immutable Source of Truth** | Published events are immutable. Consumers must not modify event payloads.       |
| G-4 | **Explicit Contract**         | Published event schemas are versioned contracts declared in `DomainManifest`.   |
| G-5 | **Isolation Preserved**       | A pack failure MUST NOT cascade to other packs.                                 |
| G-6 | **Traceability**              | Every cross-pack event MUST carry `source_domain_pack_id` and a correlation ID. |

---

## 3. Communication Patterns

### 3.1 Permitted Pattern: Publish / Subscribe (EventBus)

```
Pack A ──publish──▶ K-05 EventBus ──route──▶ Pack B (subscribed)
                                   └────────▶ Pack C (subscribed)
```

- **Pack A** emits a typed, versioned event.
- **K-05 EventBus** routes based on topic + tenant.
- **Pack B / C** receive events and react independently.

### 3.2 Permitted Pattern: Kernel Read (Query Gateway)

A domain pack may query **read-only** data from a Kernel module via the K-02 Query Gateway:

```
Pack A ──GET /api/v1/kernel/{module}/query──▶ K-02 Query Gateway ──▶ K-XX Module
```

- Read-only. No mutations.
- Rate-limited per tenant.
- Results are cacheable by the pack.

### 3.3 NOT Permitted: Direct Pack-to-Pack RPC

```
Pack A ──HTTP/gRPC──▶ Pack B    ❌ FORBIDDEN
```

**Why**: Direct RPC creates tight coupling, breaks tenant isolation, and bypasses audit logging.

---

## 4. EventBus Contract (K-05)

All cross-pack events conform to the `EventEnvelope<T>` schema defined in `lld/LLD_K05_EVENT_BUS.md`.

### 4.1 Required Fields

| Field                   | Type          | Description                                                                   |
| ----------------------- | ------------- | ----------------------------------------------------------------------------- |
| `event_id`              | `UUID`        | Globally unique event identifier                                              |
| `correlation_id`        | `UUID`        | Trace ID propagated across pack boundaries                                    |
| `source_domain_pack_id` | `String`      | Qualified pack ID of the publisher (e.g., `com.ghatana.capital-markets`)      |
| `event_type`            | `String`      | Namespaced event type (e.g., `com.ghatana.capital-markets.trade.executed.v1`) |
| `schema_version`        | `String`      | Semver of the event payload schema                                            |
| `timestamp`             | `ISO8601 UTC` | Wall-clock instant of occurrence                                              |
| `tenant_id`             | `String`      | Tenant scope (pack may never read another tenant's events)                    |
| `payload`               | `Object`      | Typed payload matching the declared schema                                    |

### 4.2 Optional Fields

| Field             | Type           | Description                                                                 |
| ----------------- | -------------- | --------------------------------------------------------------------------- |
| `calendar_date`   | `CalendarDate` | Multi-calendar representation via K-15 (if pack has K-15 T1 pack installed) |
| `causation_id`    | `UUID`         | ID of the event that triggered this one (for event chains)                  |
| `idempotency_key` | `String`       | Used by consumers to deduplicate                                            |

### 4.3 TypeScript Schema

```typescript
interface EventEnvelope<T> {
  event_id: string; // UUID v4
  correlation_id: string; // UUID v4
  source_domain_pack_id: string; // e.g. "com.ghatana.capital-markets"
  event_type: string; // reverse-DNS namespaced
  schema_version: string; // semver e.g. "1.2.0"
  timestamp: string; // ISO 8601 UTC
  tenant_id: string;
  payload: T;
  calendar_date?: CalendarDate; // optional, populated by K-15
  causation_id?: string;
  idempotency_key?: string;
}
```

---

## 5. Event Routing Rules

K-05 routes events based on the following precedence:

```
1. tenant_id          — strict tenant isolation, no cross-tenant leakage
2. event_type prefix  — reverse-DNS namespace routing
3. subscription filter — declarative JSON-path predicate (optional)
```

### 5.1 Topic Naming Convention

```
{pack-reverse-dns}.{aggregate}.{action}.v{major}

Examples:
  com.ghatana.capital-markets.trade.executed.v1
  com.acme.banking.account.credited.v2
  com.acme.insurance.claim.approved.v1
```

Rules:

- All lowercase, dot-separated.
- `v{major}` suffix is **required**. Minor/patch changes do not produce new topics.
- Cross-pack consumers subscribe to **published** topics, never pack-internal topics.

### 5.2 Routing Table Registration

Each domain pack declares its published and subscribed topics in its `DomainManifest`:

```typescript
// In DomainManifest
publishes: [
  { topic: "com.ghatana.capital-markets.trade.executed.v1", schemaRef: "./schemas/trade-executed.json" }
],
subscribes: [
  {
    topic: "com.acme.banking.account.credited.v2",
    filter: "$.payload.amount > 10000",   // optional JSONPath predicate
    handler: "handlers/AccountCreditedHandler"
  }
]
```

---

## 6. `source_domain_pack_id` Convention

### 6.1 Format

```
{reverse-dns-group}.{pack-name}
```

| Example                       | Pack                                                              |
| ----------------------------- | ----------------------------------------------------------------- |
| `com.ghatana.capital-markets` | Capital Markets (Siddhanta) domain pack                           |
| `com.acme.banking`            | Banking domain pack (ACME org)                                    |
| `com.acme.insurance`          | Insurance domain pack (ACME org)                                  |
| `io.appplatform.kernel.k05`   | Kernel-internal event (reserved prefix `io.appplatform.kernel.*`) |

### 6.2 Registration

`source_domain_pack_id` must match the `id` field in the pack's certified `DomainManifest`. The Platform Kernel validates this on all published events.

### 6.3 Criticality Tiers

Packs declare their criticality tier in their `DomainManifest`. K-05 uses this to apply priority routing:

| Tier       | SLA                  | Use Case                            |
| ---------- | -------------------- | ----------------------------------- |
| `CRITICAL` | p99 < 50ms delivery  | Payment settlement, fraud detection |
| `HIGH`     | p99 < 200ms delivery | Order management, ledger entries    |
| `STANDARD` | p99 < 1s delivery    | Notifications, audit logs           |
| `BATCH`    | Best-effort          | Reporting, analytics                |

---

## 7. Cross-Pack Subscriptions

### 7.1 Subscription Lifecycle

```
1. Pack B declares subscription in DomainManifest (topic + filter + handler)
2. Platform Kernel validates Pack A has published topic in its certified manifest
3. Subscription is provisioned at tenant activation
4. Events flow from K-05 to Pack B's handler
5. On Pack B upgrade: subscriptions re-validated; stale subscriptions raise P1 alert
```

### 7.2 Consumer Guarantees

| Guarantee                  | Default                 | Override                      |
| -------------------------- | ----------------------- | ----------------------------- |
| At-least-once delivery     | ✅                      | —                             |
| Ordering (per-partition)   | ✅                      | —                             |
| Exactly-once (idempotency) | Consumer responsibility | Provide `idempotency_key`     |
| Dead-letter queue          | ✅ (after 3 retries)    | Configurable per subscription |

### 7.3 Handler Interface

```java
// T2 Rule (Rego) handler — sandbox-safe
// Declared as tier: "T2" in DomainManifest

// T3 Handler (Java) — managed thread pool
public interface CrossPackEventHandler<T> {
    Promise<Void> handle(EventEnvelope<T> event, HandlerContext ctx);
}
```

### 7.4 Backpressure & Throttling

- Each pack has a per-tenant event ingestion quota (configured in `AgentInstance` / `DomainInstance`).
- Exceeding quota results in events queued to the Dead Letter Queue, not dropped.
- Packs subscribe to the `io.appplatform.kernel.k05.quota-warning.v1` internal event to self-throttle.

---

## 8. Inter-Pack Dependency Declarations

### 8.1 Manifest Declaration

If Pack A's business logic requires events from Pack B, this dependency is _declarative_, not _coupled_:

```typescript
// DomainManifest
crossPackDependencies: [
  {
    packId: "com.acme.banking", // must be a certified, installed pack
    minimumVersion: "2.0.0",
    reason: "Requires account credit events for reconciliation",
    optional: false, // pack will not activate if not satisfied
  },
];
```

### 8.2 Dependency Validation

The Platform Kernel validates `crossPackDependencies` at pack activation time:

1. The referenced pack is installed in the tenant's environment.
2. The installed version satisfies `minimumVersion` (semver `>=`).
3. All subscribed topics from the referenced pack are in its certified `publishes` list.

### 8.3 Optional Dependencies

`optional: true` dependencies degrade gracefully: the subscribing handler is invoked only when the pack is present. Feature flags (`FeatureFlagDefinition`) should gate behaviour accordingly.

---

## 9. Forbidden Patterns

| Pattern                                                          | Reason                               | Alternative                                            |
| ---------------------------------------------------------------- | ------------------------------------ | ------------------------------------------------------ |
| Direct HTTP/gRPC call to another pack's internal endpoint        | Tight coupling, bypass audit         | Publish event to K-05                                  |
| Shared database table between packs                              | Schema coupling, migration conflicts | Emit state-change events                               |
| Shared in-memory cache                                           | Race conditions, isolation violation | Use K-08 Distributed Cache with pack-scoped namespaces |
| Reading another pack's EventLog directly                         | Bypass routing, security             | Subscribe via K-05 topic                               |
| Calling a Kernel internal API not exposed via K-02 Query Gateway | Unstable internal API surface        | Use K-02                                               |
| Polling for events (HTTP long-poll/webhook)                      | Breaks push model                    | Use K-05 subscription                                  |

---

## 10. Security & Isolation

### 10.1 Tenant Isolation

K-05 enforces that events are **never** delivered across tenant boundaries. This is a kernel invariant, not a convention.

### 10.2 Pack Identity Verification

- `source_domain_pack_id` is set and validated by the Platform Kernel on publish. Packs cannot spoof other packs' IDs.
- Subscriber packs receive `source_domain_pack_id` as a read-only field for audit/filter purposes.

### 10.3 Event Payload Encryption

Events containing PII MUST be encrypted at the payload level using the tenant's K-03 Encryption Service key. The envelope fields (routing metadata) remain encrypted at transport/rest (TLS + disk encryption) but are readable by the kernel for routing.

```typescript
// Marking PII events
interface EventEnvelope<T> {
  // ... standard fields ...
  pii_encrypted: boolean; // true = payload is encrypted with K-03 tenant key
}
```

### 10.4 Schema Validation

K-05 validates the event `payload` against the schema declared in the publisher's `DomainManifest` before routing. Malformed events are rejected with a `SCHEMA_VIOLATION` error and published to `io.appplatform.kernel.k05.dlq.v1`.

---

## 11. Operational Observability

All cross-pack event flows are observable via K-17 Observability:

| Metric                         | Labels                                                          |
| ------------------------------ | --------------------------------------------------------------- |
| `k05_events_published_total`   | `source_pack_id`, `event_type`, `tenant_id`                     |
| `k05_events_delivered_total`   | `source_pack_id`, `event_type`, `consumer_pack_id`, `tenant_id` |
| `k05_events_dlq_total`         | `source_pack_id`, `event_type`, `reason`, `tenant_id`           |
| `k05_delivery_latency_seconds` | `source_pack_id`, `event_type`, `consumer_pack_id`              |
| `k05_consumer_lag`             | `consumer_pack_id`, `topic`, `tenant_id`                        |

Correlation IDs (`correlation_id`) propagate through OpenTelemetry trace contexts, enabling end-to-end distributed traces across pack boundaries.

---

## 12. API Evolution Policy

| Change Type                   | Policy                                                              | Consumer Impact                    |
| ----------------------------- | ------------------------------------------------------------------- | ---------------------------------- |
| Add optional field to payload | Non-breaking — no version bump required                             | Consumers ignore unknown fields    |
| Add required field to payload | Breaking — bump `{major}` in topic name                             | Consumer subscription must update  |
| Remove field from payload     | Breaking — bump `{major}` in topic name; old topic runs in parallel | Migration window: 2 release cycles |
| Rename event type             | Breaking — new topic; old topic deprecated with `sunset_date`       | Consumers must migrate             |
| Change field type             | Breaking — always a `{major}` bump                                  |                                    |

**Deprecation Process**: A deprecated topic runs alongside the replacement for a minimum of **90 days** before being decommissioned. A `DomainManifest` annotation `deprecates:` documents superseded topics.

### 12.1 Schema Mismatch Recovery

When a consumer is deployed expecting a newer schema version but receives an older one (e.g., consumer expects `v2`, receives `v1`), the following protocol applies:

| Scenario                                             | Detection                                                                    | Recovery                                                                                                                                  |
| ---------------------------------------------------- | ---------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Consumer expects `v2`, receives `v1` (old publisher) | K-05 sets `schema_version` header during routing; consumer checks on receipt | Consumer applies a **backward-compatibility shim** declared in `DomainManifest.schemaAdapters[]`; logs `SCHEMA_DOWNLEVEL_RECEIVED` metric |
| Consumer expects `v1`, receives `v2` (new publisher) | Unknown fields injected into consumer's parsed type                          | Consumer uses **strict forward-compatible parsing** (ignore unknown fields); no action required if field additions only                   |
| Breaking change without version bump (invalid)       | K-05 schema validator rejects on publish                                     | Event lands in DLQ with `SCHEMA_VIOLATION` reason; publisher receives rejection error                                                     |
| Consumer lacks shim for old schema                   | No shim found for `v1` on a `v2`-only consumer                               | Event lands in DLQ with `SCHEMA_ADAPTER_MISSING` reason; P1 alert triggers for the consuming pack                                         |

**Schema Adapter Declaration** (in `DomainManifest`):

```typescript
schemaAdapters: [
  {
    topic: "com.acme.banking.account.credited",
    fromVersion: "1", // schema_version received
    toVersion: "2", // schema_version the handler expects
    adapterClass: "adapters.AccountCreditedV1toV2Adapter",
  },
];
```

**Shim Contract**: A shim must be pure and stateless — it maps one JSON object to another with no side-effects. Shims are tested against 100% of schema v1 golden test cases before deployment.

**Maximum Supported Lag**: A consumer must declare and support schema adapters for at most **2 major versions back**. Older publisher versions require a platform upgrade or migration plan.

---

_This specification is maintained by the AppPlatform Architecture Council. Changes require an ADR._
