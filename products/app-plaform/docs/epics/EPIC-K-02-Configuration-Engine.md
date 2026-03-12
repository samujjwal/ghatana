EPIC-ID: EPIC-K-02
EPIC NAME: Configuration Engine
LAYER: KERNEL
MODULE: K-02 Configuration Engine
VERSION: 1.1.1

---

#### Section 1 — Objective

Provide a robust, hierarchical Configuration Engine (K-02) that serves as the single source of truth for all configurable system behaviors across the platform. This epic fulfills Principle 2 (Full Externalization) and ensures that all jurisdiction, operator, tenant, account, and user-specific configurations are resolved deterministically at runtime. It eliminates hardcoded values and supports hot reloads, canary rollouts, and offline distribution for air-gapped environments.

---

#### Section 2 — Scope

- **In-Scope:**
  1. Versioned Config Schema Registry with backward/forward compatibility rules.
  2. Hierarchical resolution: `Global → Jurisdiction → Operator → Tenant → Account → User`.
  3. Hot reload / dynamic update delivery via K-05 Event Bus.
  4. Tamper-evident audit trail for all configuration changes.
  5. Offline/air-gapped distribution tooling (signed bundles).
  6. Platform SDK integration (`ConfigClient`).
- **Out-of-Scope:**
  1. Actual business data (e.g., specific tax rates) - these are authored in T1 Config Packs, not in the core engine logic.
  2. Policy evaluation (handled by K-03 Rules Engine).
- **Dependencies:** EPIC-K-05 (Event Bus), EPIC-K-07 (Audit Framework), EPIC-K-15 (Dual-Calendar)
- **Kernel Readiness Gates:** N/A
- **Module Classification:** Generic Core

---

#### Section 3 — Functional Requirements (FR)

1. **FR1 Schema Registration:** The engine must enforce that all config packs validate against a pre-registered, versioned schema.
2. **FR2 Resolution Hierarchy:** The engine must merge config packs in the exact precedence order, producing a resolved configuration object for any given context.
3. **FR3 Hot Reload:** The engine must emit `ConfigUpdatedEvent` when configurations change, allowing domain modules to reload gracefully without restarts.
4. **FR4 Dual-Calendar Activation:** Config changes must support an `effective_date` specifying both BS and Gregorian dates for scheduled activation.
5. **FR5 Immutable Audit Trail:** Every config creation, update, or deletion must be logged with before/after states in the Audit Framework.
6. **FR6 Air-Gap Support:** The engine must ingest cryptographically signed configuration bundles for offline/on-prem environments.
7. **FR7 Maker-Checker Integration:** Critical config changes (defined via T2 Rule Packs) must enforce maker-checker approval workflows before activation.
8. **FR8 CQRS Structure:** Write model handles config ingestion, validation, and approval; Read model handles high-performance resolution queries.
9. **FR9 Mid-Session Change Behavior:** When a config change activates during an active trading session, the engine must: (a) apply the change only to new operations (orders, evaluations) initiated after activation, (b) preserve the config snapshot used by in-flight operations until completion, (c) emit `ConfigMidSessionActivatedEvent` with both old and new version IDs so domain modules can audit the transition boundary. [ARB D.6]

---

#### Section 4 — Jurisdiction Isolation Requirements

1. **Generic Core:** The engine logic (validation, resolution, distribution) is entirely jurisdiction-agnostic.
2. **Jurisdiction Plugin:** Jurisdictions provide T1 Config Packs (e.g., tax tables, holidays) that the engine ingests.
3. **Resolution Flow:** Context parameters (e.g., `tenant_id`, `jurisdiction_code`) map to the appropriate Jurisdiction Pack during the resolution hierarchy.
4. **Hot Reload:** Applicable.
5. **Backward Compatibility:** Deprecated config keys remain available for an established window (e.g., 90 days).
6. **Future Jurisdiction:** New jurisdictions simply submit new T1 Config Packs; the engine requires zero changes.

---

#### Section 5 — Data Model Impact

- **New Entities:**
  - `ConfigSchema`: `{ schema_id: String, version: String, json_schema: JSON, status: Enum }`
  - `ConfigPack`: `{ pack_id: String, type: Enum (GLOBAL, JURISDICTION, etc.), context_key: String, payload: JSON, effective_date: DualDate, status: Enum }`
  - `ResolvedConfigCache`: `{ cache_key: String, payload: JSON, expires_at: Timestamp }`
- **Dual-Calendar Fields:** `effective_date` in `ConfigPack`.
- **Event Schema Changes:** `ConfigSchemaRegisteredEvent`, `ConfigPackActivatedEvent`, `ConfigRolledBackEvent`, `ConfigMidSessionActivatedEvent`.

---

#### Section 6 — Event Model Definition

| Field             | Description                                                                                                                   |
| ----------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Event Name        | `ConfigPackActivatedEvent`                                                                                                    |
| Schema Version    | `v1.0.0`                                                                                                                      |
| Trigger Condition | A config pack reaches its effective date and becomes active.                                                                  |
| Payload           | `{ "pack_id": "...", "type": "JURISDICTION", "context_key": "NP", "effective_date_bs": "...", "effective_date_greg": "..." }` |
| Consumers         | All Domain Modules (to flush local caches), Audit Framework                                                                   |
| Idempotency Key   | `hash(pack_id + version + activation_timestamp)`                                                                              |
| Replay Behavior   | Updates the materialized view of active configs without triggering side-effects in domain modules.                            |
| Retention Policy  | Permanent (system core state).                                                                                                |

| Field             | Description                                                                                                      |
| ----------------- | ---------------------------------------------------------------------------------------------------------------- | --------- |
| Event Name        | `ConfigRolledBackEvent`                                                                                          |
| Schema Version    | `v1.0.0`                                                                                                         |
| Trigger Condition | An administrator rolls back a config pack to a previous version.                                                 |
| Payload           | `{ "pack_id": "...", "rolled_back_from": "v2.1", "rolled_back_to": "v2.0", "reason": "...", "actor_id": "..." }` |
| Consumers         | All Domain Modules (cache invalidation), Audit Framework, Admin Portal                                           |
| Idempotency Key   | `hash(pack_id + rolled_back_to + timestamp)`                                                                     |
| Replay Behavior   | Restores the materialized config state to the target version.                                                    |
| Retention Policy  | Permanent.                                                                                                       | [ARB D.3] |

---

#### Section 7 — Command Model Definition

| Field            | Description                                                               |
| ---------------- | ------------------------------------------------------------------------- |
| Command Name     | `UpdateConfigCommand`                                                     |
| Schema Version   | `v1.0.0`                                                                  |
| Validation Rules | Valid schema, passes validation rules, maker-checker approval if required |
| Handler          | `ConfigCommandHandler` in K-02 Config Engine                              |
| Success Event    | `ConfigUpdated`                                                           |
| Failure Event    | `ConfigUpdateFailed`                                                      |
| Idempotency      | Command ID must be unique; duplicate commands return original result      |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `RollbackConfigCommand`                                              |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Target version exists, requester authorized                          |
| Handler          | `ConfigCommandHandler` in K-02 Config Engine                         |
| Success Event    | `ConfigRolledBack`                                                   |
| Failure Event    | `ConfigRollbackFailed`                                               |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

| Field            | Description                                                          |
| ---------------- | -------------------------------------------------------------------- |
| Command Name     | `PublishConfigCommand`                                               |
| Schema Version   | `v1.0.0`                                                             |
| Validation Rules | Config validated, canary deployment strategy defined                 |
| Handler          | `ConfigCommandHandler` in K-02 Config Engine                         |
| Success Event    | `ConfigPublished`                                                    |
| Failure Event    | `ConfigPublishFailed`                                                |
| Idempotency      | Command ID must be unique; duplicate commands return original result |

---

#### Section 8 — AI Integration Requirements

- **AI Hook Type:** Anomaly Detection / Copilot Assist
- **Workflow Steps Exposed:** Config Pack submission/validation.
- **Model Registry Usage:** `config-validator-copilot-v1`
- **Explainability Requirement:** AI flags potentially dangerous configuration changes (e.g., dropping a margin rate from 50% to 5%) and requires explicit human confirmation.
- **Human Override Path:** Administrator can proceed despite AI warning (audited).
- **Drift Monitoring:** Flag rate vs override rate monitored.
- **Fallback Behavior:** Standard strict JSON schema validation applies.

---

#### Section 9 — NFRs

| NFR Category              | Required Targets                                                                   |
| ------------------------- | ---------------------------------------------------------------------------------- |
| Latency / Throughput      | Read P99 < 5ms; handle 20,000 TPS via distributed cache                            |
| Scalability               | Fully stateless edge caches; horizontally scalable                                 |
| Availability              | 99.999% uptime                                                                     |
| Consistency Model         | Eventual consistency across edge caches (max lag < 100ms)                          |
| Security                  | Configuration payloads encrypted at rest if marked sensitive                       |
| Data Residency            | Config data replicated globally unless restricted by specific Jurisdiction schemas |
| Data Retention            | Full history retained indefinitely                                                 |
| Auditability              | Every config modification recorded                                                 |
| Observability             | Metrics: `config.resolution.latency`, `config.cache.hit_rate`                      |
| Extensibility             | N/A (Core module)                                                                  |
| Upgrade / Compatibility   | Backward compatible schema resolution                                              |
| On-Prem Constraints       | Able to bootstrap from a signed local file                                         |
| Ledger Integrity          | N/A                                                                                |
| Dual-Calendar Correctness | Correct activation on BS effective date                                            |

---

#### Section 10 — Acceptance Criteria

1. **Given** a hierarchy of Global, Jurisdiction (NP), and Tenant configs, **When** a resolution request is made, **Then** the resulting JSON correctly merges all levels with Tenant overriding Jurisdiction, and Jurisdiction overriding Global.
2. **Given** a domain module caching config, **When** a `ConfigPackActivatedEvent` is published, **Then** the domain module immediately fetches the new config without restarting.
3. **Given** a new Config Pack with an effective date in the future (BS calendar), **When** that date is reached, **Then** the engine automatically activates it and notifies consumers.
4. **Given** an invalid JSON payload against the registered schema, **When** submitted, **Then** the engine rejects the pack synchronously.
5. **Given** an air-gapped deployment, **When** a signed config bundle is loaded via CLI, **Then** the engine verifies the signature and applies the configs locally.

---

#### Section 11 — Failure Modes & Resilience

- **Database Partition:** Read requests served from local memory caches. Writes rejected until partition resolves.
- **Invalid Schema Registration:** Rejected immediately.
- **Cache Invalid Failure:** Periodic background synchronization ensures eventual consistency if an event is dropped.

---

#### Section 12 — Observability & Audit

| Telemetry Type      | Required Details                                                      |
| ------------------- | --------------------------------------------------------------------- |
| Metrics             | `config.read.latency`, `config.write.count`, `config.reload.success`  |
| Logs                | Structured logs with `pack_id`, `tenant_id`, `action`                 |
| Traces              | Span `ConfigClient.resolve`                                           |
| Audit Events        | Action: `CreateConfigPack`, `UpdateConfigPack` [LCA-AUDIT-001]        |
| Regulatory Evidence | Audit trail of all parameter changes (e.g., fee rates, margin limits) |

---

#### Section 13 — Compliance & Regulatory Traceability

- Record retention — Configuration history kept permanently [LCA-RET-001]
- Segregation of duties — Config updates require maker-checker [LCA-SOD-001]
- Audit trails [LCA-AUDIT-001]

---

#### Section 14 — Extension Points & Contracts

- **SDK Contract:** `ConfigClient.resolve(context)`, `ConfigClient.watch(keys, callback)`.
- **Jurisdiction Plugin Extension Points:** N/A (This module consumes plugins).

---

#### Section 15 — Future-Safe Architecture Evaluation

| Question                                                              | Expected Answer                                                                                                    |
| --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| Can this module support India/Bangladesh via plugin?                  | Yes, natively designed for this.                                                                                   |
| Can tax rules change without redeploy?                                | Yes, via Hot Reload.                                                                                               |
| Can settlement cycle change without code change?                      | Yes.                                                                                                               |
| Can this run in an air-gapped deployment?                             | Yes, via signed bundles.                                                                                           |
| Can this module handle digital assets (tokenized securities, crypto)? | Yes. Digital-asset-specific config namespaces (token types, chain IDs, gas limits) are T1 Config Packs.            |
| Is the design ready for CBDC integration or T+0 settlement?           | Yes. Settlement-cycle configs are hot-reloadable; switching from T+1 to T+0 is a config change, not a code change. |

---

## Changelog

### Version 1.1.1 (2026-03-10)

**Type:** PATCH  
**Changes:**

- Standardized section numbering to the sequential 16-section format.
- Added changelog metadata for future epic maintenance.
