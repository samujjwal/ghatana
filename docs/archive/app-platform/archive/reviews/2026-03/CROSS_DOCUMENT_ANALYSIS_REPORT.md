# CROSS-DOCUMENT ANALYSIS REPORT

**Project**: Siddhanta Capital Markets Platform  
**Scope**: 16 documentation files across Architecture Specs, Epics, LLDs, and Supporting docs  
**Date**: 2025  
**Status**: Analysis Complete  

**Historical Note (March 8, 2026):** This analysis reflects an earlier documentation state. Some inconsistencies called out here were subsequently addressed during the March 2026 hardening work; keep this file as review history, not as the current defect list.

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Architectural & Design Inconsistencies](#2-architectural--design-inconsistencies)
3. [Missing Extensibility Patterns](#3-missing-extensibility-patterns)
4. [Gaps in Real-World Problem Coverage](#4-gaps-in-real-world-problem-coverage)
5. [Future-Proofing Gaps](#5-future-proofing-gaps)
6. [Terminology Inconsistencies](#6-terminology-inconsistencies)
7. [Version & Date Inconsistencies](#7-version--date-inconsistencies)
8. [Missing Cross-References](#8-missing-cross-references)
9. [NFR & SLA Inconsistencies](#9-nfr--sla-inconsistencies)
10. [Recommendations](#10-recommendations)

---

## 1. EXECUTIVE SUMMARY

This analysis covers 16 documents from the Siddhanta platform documentation suite:

| Category | Documents |
|----------|-----------|
| **Architecture Specs** | `ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md`, `ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`, `ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md`, `ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md` |
| **Epics** | `EPIC-K-02-Configuration-Engine.md`, `EPIC-K-03-Rules-Engine.md`, `EPIC-K-04-Plugin-Runtime.md`, `EPIC-K-05-Event-Bus.md` |
| **LLDs** | `LLD_K02_CONFIGURATION_ENGINE.md`, `LLD_K03_RULES_ENGINE.md`, `LLD_K04_PLUGIN_RUNTIME.md`, `LLD_K05_EVENT_BUS.md` |
| **Supporting** | `REGULATORY_ARCHITECTURE_DOCUMENT.md`, `EPIC_TEMPLATE.md`, `DEPENDENCY_MATRIX.md`, `COMPLIANCE_CODE_REGISTRY.md` |

**Key Finding**: The Architecture Spec documents (v1.0, January 2025) represent a significantly earlier design epoch than the Epics (v1.1.0, post-ARB remediation) and the Regulatory Architecture Document (v2.0.0, March 2025). The Epics and LLDs were substantially revised through an Architecture Review Board (ARB) process that introduced new concepts (T1/T2/T3 tier model, dual-calendar, maker-checker, CQRS/event sourcing, saga orchestration, DLQ management) which the Architecture Specs have **never been updated to reflect**. This creates a dangerous documentation divergence where the "source of truth" Architecture Specs actively contradict the implementation-ready LLDs.

**Total Findings**: 47 distinct issues across 8 categories.

---

## 2. ARCHITECTURAL & DESIGN INCONSISTENCIES

### INC-01: Configuration Hierarchy — Fundamental Model Mismatch

**Severity**: CRITICAL

The Architecture Spec and Epic/LLD define **completely different** configuration hierarchies that are conceptually incompatible.

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md`):
> ```
> Default → Environment → Service → Feature Flags → Runtime Overrides → Environment Variables
> ```
This is an **environment-centric** model focused on deployment concerns (dev/staging/prod), with layers like "Feature Flags" and "Environment Variables" that treat configuration as a DevOps concern.

**Epic K-02** (`EPIC-K-02-Configuration-Engine.md`):
> ```
> Global → Jurisdiction → Operator → Tenant → Account → User
> ```
This is a **multi-tenant, jurisdiction-aware** model that treats configuration as a business/regulatory concern.

**LLD K-02** (`LLD_K02_CONFIGURATION_ENGINE.md`) confirms the Epic model with a formal resolution algorithm:
> ```python
> RESOLUTION_ORDER = ["GLOBAL", "JURISDICTION", "OPERATOR", "TENANT", "ACCOUNT", "USER"]
> ```

**Impact**: Any implementer reading the Architecture Spec would build the wrong configuration system. The Architecture Spec model has no concept of jurisdiction, operator, or tenant—concepts fundamental to a multi-tenant capital markets platform.

---

### INC-02: Plugin Architecture — Missing Tier Model

**Severity**: CRITICAL

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md`) defines plugins by **functional type**:
> ```typescript
> type PluginType = 'order-validator' | 'execution-strategy' | 'risk-calculator' | ...
> ```
With a manifest file named `plugin.json` and a single SDK package `@siddhanta/plugin-sdk`.

**Epic K-04** (`EPIC-K-04-Plugin-Runtime.md`) defines a completely different **tier-based isolation model**:
> - **T1**: Validation/configuration plugins (minimal isolation)
> - **T2**: Rules Engine integration via K-03 (sandboxed)
> - **T3**: Isolated process/container with full network restrictions

**LLD K-04** (`LLD_K04_PLUGIN_RUNTIME.md`) confirms the tier model with specific isolation mechanisms:
> - T1: In-process execution
> - T2: V8 sandbox with 100MB memory limit
> - T3: Kubernetes container with `NetworkPolicy` blocking all egress

The Architecture Spec uses `plugin.json` as the manifest, while LLD K-04 uses `plugin_manifest.yaml`. The plugin lifecycle states also differ:
- **Arch Spec**: `INSTALLED → ENABLED → RUNNING → DISABLED → UNINSTALLED`
- **LLD K-04**: `REGISTERED → ENABLED → ACTIVE → DISABLED → DEPRECATED → DELETED`

---

### INC-03: Regulatory Jurisdiction — SEBI (India) vs SEBON (Nepal)

**Severity**: CRITICAL

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md`, Section 12) references **SEBI** (Securities and Exchange Board of India) and **RBI** (Reserve Bank of India) throughout the compliance section:
> "SEBI/RBI/MiFID II/GDPR"

**ALL other documents** reference **SEBON** (Securities Board of Nepal) and **NRB** (Nepal Rastra Bank):
- `REGULATORY_ARCHITECTURE_DOCUMENT.md`: "SEBON", "NRB"
- `COMPLIANCE_CODE_REGISTRY.md`: "SEBON Securities Act, 2063 (Nepal)"
- `EPIC-K-02-Configuration-Engine.md`: Nepal (NP) jurisdiction context
- All LLDs: Nepal-specific BS (Bikram Sambat) calendar, SEBON references

The platform is being built for **Nepal**, not India. The Architecture Spec references the wrong regulatory bodies entirely.

---

### INC-04: Missing Dual-Calendar in Architecture Spec

**Severity**: HIGH

The Architecture Spec (all 4 parts) contains **zero mention** of the Bikram Sambat (BS) calendar system. Every epic, LLD, and the Regulatory Architecture Document treat dual-calendar (BS + Gregorian) as a fundamental platform requirement:

- `LLD_K05_EVENT_BUS.md`: Events carry both `timestamp_bs` and `timestamp_gregorian`
- `LLD_K02_CONFIGURATION_ENGINE.md`: Config pack effective dates stored in both calendars
- `REGULATORY_ARCHITECTURE_DOCUMENT.md`: Rule packs use BS dates for lock-in periods (e.g., `lock_in_expiry_bs`)
- `EPIC-K-05-Event-Bus.md`: "Dual-calendar timestamps (BS + Gregorian)" listed as core requirement

The Architecture Spec's data model, API specs, and deployment configs have no dual-calendar support.

---

### INC-05: Missing CQRS/Event Sourcing in Architecture Spec

**Severity**: HIGH

The Architecture Spec's data architecture (`ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`, Section 7) describes a standard CRUD model with PostgreSQL, TimescaleDB, MongoDB, and Redis. There is **no mention** of:
- CQRS (Command Query Responsibility Segregation)
- Event sourcing
- Event store
- Saga orchestration
- Projections/read models

Meanwhile, the Epic K-05 and LLD K-05 establish CQRS/event sourcing as the **foundational architectural pattern**:
- `EPIC-K-05-Event-Bus.md`: Defines Event Store, Saga Orchestration, Projection Rebuilds, DLQ Management
- `LLD_K05_EVENT_BUS.md`: PostgreSQL `events` table with `sequence_number`, saga execution tables, projection rebuild at ">50,000 events/sec"

---

### INC-06: Missing Maker-Checker in Architecture Spec

**Severity**: HIGH

The Architecture Spec (Sections 4-15) contains no mention of maker-checker workflows. The concept is pervasive in all Epics, LLDs, and the Regulatory Architecture Document:

- `LLD_K02_CONFIGURATION_ENGINE.md`: Complete maker-checker approval workflow for config changes
- `LLD_K03_RULES_ENGINE.md`: Maker-checker for rule pack deployment
- `REGULATORY_ARCHITECTURE_DOCUMENT.md`: "Maker-Checker: Case closure requires approval from compliance officer (checker ≠ investigator)"

---

### INC-07: JWT Algorithm — HS256 vs Asymmetric

**Severity**: MEDIUM

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md`, Section 9) specifies JWT with **HS256** (symmetric HMAC):
> Algorithm: HS256

HS256 uses a shared secret, which is inappropriate for a zero-trust, microservices architecture where any service with the secret can forge tokens. The LLDs use mTLS with X.509 certificates and the Regulatory doc references Ed25519 signatures. No other document references HS256.

**Expected**: RS256, ES256, or Ed25519 for asymmetric token verification.

---

### INC-08: Database Partitioning — Temporal Gap

**Severity**: MEDIUM

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`, Section 7) defines DB partitions only through Q4 2024:
> ```sql
> CREATE TABLE orders (...) PARTITION BY RANGE (created_at);
> CREATE TABLE orders_2024_q3 PARTITION OF orders FOR VALUES FROM ('2024-07-01') TO ('2024-10-01');
> CREATE TABLE orders_2024_q4 PARTITION OF orders FOR VALUES FROM ('2024-10-01') TO ('2025-01-01');
> ```

Documents dated 2025-03 (Regulatory Architecture, Epics, LLDs) would need 2025+ partitions. More critically, no automated partition creation is defined. The `REGULATORY_ARCHITECTURE_DOCUMENT.md` discusses data retention up to 7 years (2555 days) but the Architecture Spec has no automated partition management.

---

### INC-09: Load Balancer Mismatch

**Severity**: LOW

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md`) specifies **NGINX** for load balancing:
> "NGINX load balancing"

The Deployment Architecture section (`ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`, Section 8) specifies **Istio** as the service mesh. In a Kubernetes/Istio deployment, Envoy sidecar proxies handle load balancing, making a separate NGINX redundant. The LLDs consistently reference Istio/Envoy for traffic management.

---

## 3. MISSING EXTENSIBILITY PATTERNS

### EXT-01: No Plugin Marketplace or Discovery

**Severity**: MEDIUM

The LLD K-04 (`LLD_K04_PLUGIN_RUNTIME.md`) defines plugin registration, invocation, and lifecycle but lacks:
- Plugin discovery/marketplace API for operators
- Plugin rating/review system
- Plugin dependency resolution (plugin A depends on plugin B)
- Plugin conflict detection (two plugins claiming the same capability)

The extensibility section (Section 9) defines SDK versioning and lifecycle hooks but assumes plugins are independently installable.

---

### EXT-02: No Custom Event Type Registration

**Severity**: MEDIUM

`LLD_K05_EVENT_BUS.md` defines event schemas in a registry but the extensibility section (Section 9.1) only shows **handler registration**, not how T3 plugins or operators would **register new event types** with custom schemas. The schema registry evolution validation exists, but the onboarding path for entirely new event types from external plugins is undefined.

---

### EXT-03: No Cross-Module Plugin Orchestration

**Severity**: MEDIUM

Each LLD defines extension points independently:
- K-02: Custom resolution plugins
- K-03: Custom Rego builtins
- K-04: Plugin lifecycle hooks
- K-05: Custom event handlers

But there is no **cross-module plugin orchestration** pattern. For example: a T3 plugin that needs to register a custom config schema (K-02), a custom rule pack (K-03), and subscribe to specific events (K-05) has no single onboarding API or manifest that coordinates across modules.

---

### EXT-04: Config Engine Lacks Dynamic Schema Generation

**Severity**: LOW

`LLD_K02_CONFIGURATION_ENGINE.md` (Section 9.1) defines extension points for custom resolution logic, schema validators, cache strategies, and event transformers. However, there is no extension point for **dynamic schema generation** — e.g., when a new jurisdiction is onboarded and needs auto-generated config schemas from regulatory templates. The current model assumes schemas are manually defined and deployed.

---

### EXT-05: No Webhook/External Notification Extension Point

**Severity**: LOW

The Event Bus LLD (`LLD_K05_EVENT_BUS.md`) publishes events internally (Kafka + subscribers) but has no extension point for **external webhooks** or notification channels. Operators who need real-time notifications of events (e.g., order fills, compliance alerts) to external systems have no defined integration path.

---

## 4. GAPS IN REAL-WORLD PROBLEM COVERAGE

### GAP-01: No Market Halt / Circuit Breaker (Exchange-Level) Handling

**Severity**: HIGH

No document defines how the platform handles exchange-level market halts or circuit breakers (e.g., SEBON's price-band triggers). The OMS (D-01) is only referenced epics, and K-03 Rules Engine defines order-level validation. But the scenario where SEBON declares a market-wide halt mid-session — requiring all pending orders to be frozen, notifications sent, and resumption handled — is not covered in any of the 16 documents.

---

### GAP-02: No Network Partition / Split-Brain Handling

**Severity**: HIGH

The Architecture Spec defines multi-region deployment (primary: ap-south-1, DR: ap-southeast-1) and automated failover. LLD K-05 defines Kafka replication factor 3. But **no document** addresses split-brain scenarios:
- What happens if event store writes succeed in region A but Kafka replication to region B fails?
- How are saga compensations handled during a network partition?
- The `LLD_K05_EVENT_BUS.md` assumption #2 states: "Saga compensation always succeeds" — this is unrealistic during partitions.

---

### GAP-03: No Bulk/Batch Operations Path

**Severity**: MEDIUM

All LLD API definitions are single-entity operations:
- K-02: Resolve one config at a time
- K-03: Evaluate one policy at a time
- K-04: Invoke one plugin at a time
- K-05: Publish one event at a time

Real-world capital markets operations often require batch processing:
- End-of-day settlement for thousands of trades
- Bulk KYC re-verification across all clients
- Mass config updates during regulatory changes (e.g., new tax rate for all tenants)

No batch/bulk API patterns are defined.

---

### GAP-04: No Data Migration Strategy

**Severity**: MEDIUM

While `LLD_K02_CONFIGURATION_ENGINE.md` defines schema evolution and backward compatibility rules, **no document** addresses:
- Data migration between schema versions (when payload structure changes)
- Tenant data migration during platform upgrades
- Historical event schema migration in the event store

The `LLD_K05_EVENT_BUS.md` defines schema evolution validation (backward/forward compatibility) but this only prevents incompatible changes — it doesn't address migrating existing events to a new schema version.

---

### GAP-05: No Rate Limiting Consistency Across Documents

**Severity**: MEDIUM

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md`) defines rate limits:
> - Global: 1000/15min
> - API: 100/min
> - Orders: 10/min

These are unrealistically low for a capital markets platform that targets "100K orders/sec" and "50K concurrent users" (same Architecture Spec, Section 11). An order rate limit of 10/min per... what? Per user? Per tenant? Per API key? The scope is undefined, and the limits conflict with the performance targets.

No LLD or Epic references these rate limits.

---

### GAP-06: No Graceful Degradation for Multi-Module Cascading Failures

**Severity**: MEDIUM

The `REGULATORY_ARCHITECTURE_DOCUMENT.md` (Section 6.2) defines degraded behavior for individual module failures. EPIC K-03 defines circuit breakers. But no document defines the **cascading failure scenario**:
- K-02 Config Engine goes down → K-03 Rules Engine can't load rule packs → K-04 Plugin Runtime can't resolve plugin capabilities → D-01 OMS can't validate orders

The dependency chain K-02 → K-03 → K-04 → D-01 is documented in `DEPENDENCY_MATRIX.md` but the cascading degradation behavior is not.

---

### GAP-07: No Audit Log Rotation / Archival for High-Volume Scenarios

**Severity**: LOW

`REGULATORY_ARCHITECTURE_DOCUMENT.md` states K-07 audit logs have RPO=0 with synchronous replication to 3 replicas. The retention policy defines 7-year retention. But for a platform processing 100K events/sec, the audit log volume would be enormous. No document defines:
- Audit log compression strategy
- Hot/warm/cold tiering for audit data
- Query performance on years-old audit logs
- Archival format for regulator evidence (beyond the export format)

---

## 5. FUTURE-PROOFING GAPS

### FP-01: Kubernetes Version Pinned to 1.28

**Severity**: MEDIUM

**Architecture Spec** (`ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`, Section 8) pins Kubernetes to version 1.28:
> "EKS K8s version 1.28"

Kubernetes 1.28 was released August 2023 and will be out of support by ~October 2024. No K8s upgrade strategy or version compatibility matrix is defined. The `DEPENDENCY_MATRIX.md` does not include infrastructure version dependencies.

---

### FP-02: No Multi-Cloud or Cloud-Agnostic Abstraction

**Severity**: MEDIUM

The Architecture Spec is tightly coupled to AWS:
- EKS (Kubernetes)
- S3 + Glacier (storage)
- AWS KMS (key management)
- Athena (analytics)

`REGULATORY_ARCHITECTURE_DOCUMENT.md` references `ap-south-1` and `ap-southeast-1` (AWS regions). The `EPIC_TEMPLATE.md` mentions K-10 Deployment Abstraction, but no document among the 16 defines how the platform would run on Azure, GCP, or on-premises without AWS — despite the Regulatory Architecture Document's Section 7.5 listing "On-Premises" as a deployment mode requiring "full sovereignty."

---

### FP-03: No AI/ML Framework Migration Path

**Severity**: LOW

Architecture Spec Section 6 pins to:
- MLflow for model registry
- sklearn for ML
- SHAP for explainability

The `REGULATORY_ARCHITECTURE_DOCUMENT.md` references PyTorch (Section 8.1: `"framework": "PyTorch"`), which contradicts the Architecture Spec's sklearn choice. No document defines a framework abstraction layer or migration path if the ML ecosystem evolves.

---

### FP-04: No Quantum-Safe Cryptography Roadmap

**Severity**: LOW

All documents specify Ed25519 signatures and AES-256-GCM encryption. Given the 7-year data retention requirement (`REGULATORY_ARCHITECTURE_DOCUMENT.md`, Section 4.2), data encrypted today must remain secure through 2032+. No document discusses post-quantum cryptography migration (e.g., CRYSTALS-Dilithium for signatures, CRYSTALS-Kyber for key exchange).

---

### FP-05: No Event Schema Versioning Strategy for Long-Term Replay

**Severity**: MEDIUM

`LLD_K05_EVENT_BUS.md` defines schema evolution validation and event replay, and the `REGULATORY_ARCHITECTURE_DOCUMENT.md` requires 7-year retention. Events stored in 2025 with schema v1.0 must remain replayable through 2032. But no document defines:
- How to replay v1.0 events through a v3.0 projection handler
- Schema version migration for the event store
- Whether old event formats are auto-upgraded or kept as-is

The LLD assumption states: "Event replay is infrequent (admin operation)" — this doesn't address the regulatory obligation to replay for audit.

---

## 6. TERMINOLOGY INCONSISTENCIES

### TERM-01: Plugin Manifest File Name

| Document | Term |
|----------|------|
| `ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md` | `plugin.json` |
| `EPIC-K-04-Plugin-Runtime.md` | `plugin_manifest.yaml` |
| `LLD_K04_PLUGIN_RUNTIME.md` | `manifest` (within `plugins` table, `manifest JSONB`) |

Three different names/formats for the same concept.

---

### TERM-02: Plugin States

| Document | States |
|----------|--------|
| `ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md` | `INSTALLED → ENABLED → RUNNING → DISABLED → UNINSTALLED` |
| `LLD_K04_PLUGIN_RUNTIME.md` | `REGISTERED → ENABLED → ACTIVE → DISABLED → DEPRECATED → DELETED` |

Different state names and lifecycle stages. "INSTALLED" vs "REGISTERED", "RUNNING" vs "ACTIVE", and the LLD adds "DEPRECATED" which the Arch Spec lacks.

---

### TERM-03: "Rule Pack" vs "Policy" vs "Bundle"

| Document | Preferred Term |
|----------|----------------|
| `EPIC-K-03-Rules-Engine.md` | "Rule Pack" |
| `LLD_K03_RULES_ENGINE.md` | "Rule Pack" (but uses `policy` in API: `/evaluate-policy`) |
| `REGULATORY_ARCHITECTURE_DOCUMENT.md` | "T2 Rule Pack" |
| `ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md` | No mention of rule packs (uses generic "configuration") |
| `LLD_K02_CONFIGURATION_ENGINE.md` | "Config Pack" (but references T2 = rule packs in Section 9.5) |

The API endpoint in LLD K-03 uses "policy" (`/evaluate-policy`) while all documentation says "rule pack." This creates confusion between OPA policy files and the Siddhanta "rule pack" concept.

---

### TERM-04: Tier Terminology Overload

The tier terminology is overloaded across documents:

| Context | T1 | T2 | T3 |
|---------|----|----|-----|
| **Config Packs** (K-02, Regulatory Doc) | Jurisdiction config data | Jurisdiction rules (Rego) | Jurisdiction executables |
| **Plugin Isolation** (K-04, LLD K-04) | In-process validation | V8 sandbox | Container isolation |
| **AI Risk Tiers** (Regulatory Doc, S8.1) | Critical (auto-decisions) | High (recommendations) | Medium (operational) |

The same T1/T2/T3 labels mean completely different things depending on context. The Regulatory Architecture Document uses both config-tier and AI-tier meanings in the same document without disambiguation.

---

### TERM-05: "Event Bus" vs "Event Store" vs "Event Broker"

| Document | Usage |
|----------|-------|
| `EPIC-K-05-Event-Bus.md` | Title says "Event Bus" but defines Event Bus + Event Store + Workflow Orchestration |
| `LLD_K05_EVENT_BUS.md` | Uses "Event Bus" for the system and "event_store" for the PostgreSQL table |
| `ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md` | No explicit mention of event store |
| `REGULATORY_ARCHITECTURE_DOCUMENT.md` | "K-05 Event Store" (different name than epic/LLD title) |

The Regulatory Architecture Document calls it "K-05 Event Store" (Section 6.1 table), while the Epic and LLD name it "Event Bus."

---

### TERM-06: Config Engine vs Config Service

| Document | Term |
|----------|------|
| `EPIC-K-02-Configuration-Engine.md` | "Configuration Engine" |
| `LLD_K02_CONFIGURATION_ENGINE.md` | "Configuration Engine" (title) but `config-service` in structured logs and API definitions |
| `REGULATORY_ARCHITECTURE_DOCUMENT.md` | "K-02 Config Engine" |

The service implementation name (`config-service`) differs from the architectural name ("Configuration Engine" / "Config Engine").

---

## 7. VERSION & DATE INCONSISTENCIES

### VER-01: Document Version Matrix

| Document | Version | Date | Status |
|----------|---------|------|--------|
| `ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md` | 1.0 | January 2025 | Implementation-Ready |
| `ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md` | 1.0 | January 2025 | Implementation-Ready |
| `ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md` | 1.0 | January 2025 | Implementation-Ready |
| `ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md` | 1.0 | January 2025 | Implementation-Ready |
| `EPIC-K-02-Configuration-Engine.md` | 1.1.0 | Post-ARB | Implementation-Ready |
| `EPIC-K-03-Rules-Engine.md` | 1.1.0 | Post-ARB | Implementation-Ready |
| `EPIC-K-04-Plugin-Runtime.md` | 1.1.0 | Post-ARB | Implementation-Ready |
| `EPIC-K-05-Event-Bus.md` | 1.1.0 | Post-ARB | Implementation-Ready |
| `LLD_K02_CONFIGURATION_ENGINE.md` | 1.0.0 | N/A | Implementation-Ready |
| `LLD_K03_RULES_ENGINE.md` | 1.0.0 | N/A | Implementation-Ready |
| `LLD_K04_PLUGIN_RUNTIME.md` | 1.0.0 | N/A | Implementation-Ready |
| `LLD_K05_EVENT_BUS.md` | 1.0.0 | N/A | Implementation-Ready |
| `REGULATORY_ARCHITECTURE_DOCUMENT.md` | 2.0.0 | 2025-03-03 | Pending Approval |
| `DEPENDENCY_MATRIX.md` | N/A | March 3, 2026 | Generated |
| `COMPLIANCE_CODE_REGISTRY.md` | N/A | March 2, 2026 | Generated |
| `EPIC_TEMPLATE.md` | N/A | N/A | Template |

**Critical Issues**:

1. **Architecture Specs are v1.0 but marked "Implementation-Ready"** — they predate the ARB process that fundamentally changed the design. They should be v2.0+ or marked as "Superseded."

2. **DEPENDENCY_MATRIX.md is dated "March 3, 2026"** which is in the future relative to all other documents. Either this is a typo (should be 2025) or a generated date that is wrong.

3. **COMPLIANCE_CODE_REGISTRY.md is dated "March 2, 2026"** — same future-date issue.

4. **LLDs have no date field** — impossible to tell if they were written before or after the ARB remediation. They are v1.0.0 but reference ARB items, suggesting they were written post-ARB.

---

### VER-02: ARB Remediation Traceability Gap

The Epics reference specific ARB findings (e.g., `[ARB P0-02]`, `[ARB P1-10]`, `[ARB D.6]`), and the `REGULATORY_ARCHITECTURE_DOCUMENT.md` references them in the degraded mode matrix. However:
- The Architecture Specs contain **zero ARB references**
- The LLDs contain **zero ARB references**
- No standalone ARB findings document exists in the 16-document scope

The ARB remediation trail is incomplete — you can see what was remediated in the Epics but cannot trace back to the original finding document.

---

### VER-03: Dependency Matrix Version Misalignment

`DEPENDENCY_MATRIX.md` includes a version compatibility matrix showing:
> "ARB-updated epics at v1.1.0"

But the Architecture Specs are still at v1.0 and marked "Implementation-Ready." The Dependency Matrix should flag this as a version conflict, but it does not. There is no automated validation that Architecture Spec → Epic → LLD versions are aligned.

---

## 8. MISSING CROSS-REFERENCES

### XREF-01: Architecture Spec → Epic Links

The Architecture Spec documents contain **zero cross-references** to specific Epic IDs. For example:
- Section 4 (Configuration) never references `EPIC-K-02`
- Section 5 (Plugin Runtime) never references `EPIC-K-04`
- Section 12 (Compliance) never references `EPIC-D-07`

This makes it impossible to trace from architecture decisions to implementation specifications.

---

### XREF-02: LLD → Architecture Spec Section Links

The LLDs reference Epics (e.g., `LLD_K02_CONFIGURATION_ENGINE.md` traces to `EPIC-K-02`) but never reference specific Architecture Spec sections. Since the Architecture Spec fundamentally disagrees with the LLDs on several points (INC-01 through INC-06), this missing link means implementers cannot resolve conflicts by checking the authoritative source.

---

### XREF-03: Missing K-01 IAM LLD References

All four LLDs reference K-01 IAM for authentication/authorization but no LLD for K-01 exists in the reviewed scope. Cross-references to K-01 behavior are present but unverifiable:
- `LLD_K02_CONFIGURATION_ENGINE.md`: "OAuth 2.0 Bearer tokens (issued by K-01 IAM)"
- `LLD_K03_RULES_ENGINE.md`: "Service identity verification"
- `LLD_K04_PLUGIN_RUNTIME.md`: "Capability-based ACL"

---

### XREF-04: Missing K-07 Audit Framework LLD References

Every module audits to K-07, and every LLD emits audit events to K-07. The `LLD_K07_AUDIT_FRAMEWORK.md` exists but was not part of this analysis scope. The audit event schemas defined in each LLD may not be consistent with K-07's expected schema.

---

### XREF-05: Regulatory Architecture Document → Epic Mapping Completeness

The `REGULATORY_ARCHITECTURE_DOCUMENT.md` references epics using "**Epic Reference:**" tags, which is good. However, it references epics that don't exist in the reviewed scope:
- K-08 Data Governance
- K-14 Secrets Management
- K-16 Ledger Framework
- K-17 Distributed Transaction Coordinator
- K-18 Resilience Patterns Library
- K-19 DLQ Management & Event Replay
- D-08 Surveillance
- R-01 Regulator Portal
- R-02 Incident Notification
- T-01 Integration Testing
- T-02 Chaos Engineering

These are mentioned but their specs were not in scope to verify consistency.

---

### XREF-06: COMPLIANCE_CODE_REGISTRY.md Lacks LLD Mapping

The `COMPLIANCE_CODE_REGISTRY.md` maps compliance codes (AUDIT-001, SOD-001, etc.) to epics but not to specific LLD sections or API endpoints. For an implementer, knowing that "AUDIT-001 maps to K-07" is insufficient — they need to know which specific API endpoint or data table satisfies the control.

---

## 9. NFR & SLA INCONSISTENCIES

### NFR-01: Rules Engine TPS — Epic vs LLD Conflict

**Severity**: CRITICAL

| Metric | Epic K-03 | LLD K-03 |
|--------|-----------|----------|
| **Target TPS** | 25,000 | 50,000 |
| **P99 Latency** | < 5ms | < 10ms |

`EPIC-K-03-Rules-Engine.md` states:
> "25,000 TPS" and "P99 < 5ms"

`LLD_K03_RULES_ENGINE.md` states:
> "50,000 TPS" and "eval P99 = 10ms"

The LLD promises 2x the throughput at 2x the latency. These cannot both be correct. The Epic's stricter latency with lower throughput suggests a different performance model than the LLD's higher throughput with relaxed latency.

---

### NFR-02: Event Bus Publish Latency — Epic vs LLD Conflict

**Severity**: CRITICAL

| Metric | Epic K-05 | LLD K-05 |
|--------|-----------|----------|
| **Event Publish P99** | < 2ms | 50ms |

`EPIC-K-05-Event-Bus.md` states:
> "P99 publish < 2ms"

`LLD_K05_EVENT_BUS.md` states:
> "publish sync P99 = 50ms"

This is a **25x discrepancy**. The Epic target of <2ms implies an in-memory or local-write path, while the LLD's 50ms suggests PostgreSQL write + Kafka publish. These reflect fundamentally different design decisions about synchronous vs asynchronous publishing.

---

### NFR-03: Configuration Engine Latency Discrepancy

**Severity**: HIGH

| Metric | Epic K-02 | LLD K-02 |
|--------|-----------|----------|
| **Read P99** | < 5ms | 2ms (cache hit), 30ms (cache miss) |

`EPIC-K-02-Configuration-Engine.md`:
> "Read P99 < 5ms"

`LLD_K02_CONFIGURATION_ENGINE.md`:
> "resolve() - cache hit: P99 = 2ms" and "resolve() - cache miss: P99 = 30ms"

The Epic's "P99 < 5ms" is only achievable at >95% cache hit rate. The LLD's cache miss P99 of 30ms is 6x the Epic's target. If cache hit rate drops below 95%, the SLO is breached. The LLD defines a cache hit rate SLO of ">95%" which is the minimum to meet the Epic's target — a razor-thin margin.

---

### NFR-04: Architecture Spec Performance vs Epic/LLD Mismatch

**Severity**: HIGH

The Architecture Spec (`ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`) defines:
> - "Order < 1ms"
> - "Market Data < 100μs"
> - "API p95 < 50ms"
> - "DB p99 < 10ms"
> - "100K orders/sec"
> - "50K concurrent users"

These numbers appear nowhere in the Epics or LLDs reviewed. The 100K orders/sec target is not broken down into component-level budgets in a way that's traceable. The "Order < 1ms" target conflicts with the Event Bus publish latency of 50ms (LLD K-05), since order processing presumably requires event publishing.

---

### NFR-05: Uptime Target Consistency

**Severity**: LOW

All four Epics specify "99.999% uptime" (five nines). The Architecture Spec does not specify a system-wide availability target. The LLD K-02 SLO table states:
> "Availability: 99.999%"

Five nines means < 5.26 minutes downtime/year. This is extremely ambitious for a system with 20+ microservices, PostgreSQL, Kafka, Redis, and Kubernetes. No document discusses how composite availability is calculated from individual service availability. If each of 20 services has 99.999% availability independently, the composite availability would be much lower.

---

### NFR-06: Plugin Invocation Latency Inconsistency

**Severity**: MEDIUM

`LLD_K04_PLUGIN_RUNTIME.md` defines two very different latency targets:
> - "invoke cached P99 = 50ms"
> - "cold start P99 = 500ms"

`EPIC-K-04-Plugin-Runtime.md` specifies:
> "Sandbox IPC < 2ms"

The Epic's 2ms IPC target doesn't account for actual plugin execution time, while the LLD's 50ms cached invocation does. These measure different things, and neither document clarifies the relationship.

---

### NFR-07: Event Bus TPS Alignment

**Severity**: LOW

Both Epic K-05 and LLD K-05 agree on 100,000 TPS:
> `EPIC-K-05-Event-Bus.md`: "100,000 TPS"
> `LLD_K05_EVENT_BUS.md`: "100,000 TPS"

However, the Architecture Spec's "100K orders/sec" target implies the Event Bus must handle **multiples** of 100K TPS, since each order generates multiple events (OrderPlaced, OrderValidated, OrderExecuted, OrderSettled = 4 events minimum). At 100K orders/sec, the Event Bus needs at least 400K TPS. This is 4x the defined target.

---

## 10. RECOMMENDATIONS

### Priority 1 — Critical (Must Fix Before Implementation)

| # | Action | Documents Affected |
|---|--------|-------------------|
| **R-01** | **Update Architecture Specs to v2.0** reflecting ARB remediation: new config hierarchy, tier model, CQRS/event sourcing, maker-checker, dual-calendar, SEBON/NRB jurisdiction | All 4 Architecture Spec files |
| **R-02** | **Resolve NFR conflicts** between Epics and LLDs (K-03 TPS, K-05 publish latency). Establish which document is authoritative for NFRs and cascade corrections. | Epic K-03, K-05; LLD K-03, K-05 |
| **R-03** | **Define cascading failure degradation** for the K-02 → K-03 → K-04 dependency chain. Add to Regulatory Architecture Document Section 6.2. | `REGULATORY_ARCHITECTURE_DOCUMENT.md`, all LLDs |

### Priority 2 — High (Fix Before Beta)

| # | Action | Documents Affected |
|---|--------|-------------------|
| **R-04** | **Add cross-reference links** from Architecture Spec sections to Epic IDs and from LLDs to Arch Spec sections | All 16 documents |
| **R-05** | **Standardize tier terminology** — introduce explicit namespacing (Config-T1/T2/T3, Plugin-T1/T2/T3, AI-Tier-1/2/3/4) | All documents using T1/T2/T3 |
| **R-06** | **Fix the DEPENDENCY_MATRIX.md and COMPLIANCE_CODE_REGISTRY.md dates** (2026 → 2025) | `DEPENDENCY_MATRIX.md`, `COMPLIANCE_CODE_REGISTRY.md` |
| **R-07** | **Add batch/bulk API patterns** to LLDs K-02, K-03, K-04, K-05 | All 4 LLD files |
| **R-08** | **Replace HS256 with RS256/ES256** in Architecture Spec JWT configuration | `ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md` |

### Priority 3 — Medium (Fix Before GA)

| # | Action | Documents Affected |
|---|--------|-------------------|
| **R-09** | **Define composite availability calculation** showing how 99.999% is achievable across 20+ services | Architecture Spec, all Epics |
| **R-10** | **Add automated partition management** for PostgreSQL time-series tables | `ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md`, LLD K-05 |
| **R-11** | **Define event schema versioning strategy** for long-term replay (7-year retention) | `LLD_K05_EVENT_BUS.md` |
| **R-12** | **Add plugin marketplace/discovery** extension point | `LLD_K04_PLUGIN_RUNTIME.md` |
| **R-13** | **Add cloud-agnostic abstraction** documentation or ensure K-10 Deployment Abstraction Epic addresses AWS dependency | Architecture Spec Section 8 |

### Priority 4 — Low (Track for Future Releases)

| # | Action | Documents Affected |
|---|--------|-------------------|
| **R-14** | **Update Kubernetes version** from 1.28 and define upgrade strategy | `ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md` |
| **R-15** | **Add quantum-safe cryptography roadmap** | Security sections of Arch Spec and LLDs |
| **R-16** | **Standardize service naming** (Configuration Engine vs Config Engine vs config-service) | All documents |
| **R-17** | **Add COMPLIANCE_CODE_REGISTRY → LLD endpoint mapping** | `COMPLIANCE_CODE_REGISTRY.md` |

---

## APPENDIX A: FINDING SEVERITY MATRIX

| Severity | Count | Definition |
|----------|-------|------------|
| **CRITICAL** | 5 | Architectural contradictions that would cause incorrect implementation |
| **HIGH** | 7 | Significant gaps that would cause production issues |
| **MEDIUM** | 17 | Design gaps requiring attention before beta |
| **LOW** | 18 | Minor inconsistencies or improvement opportunities |
| **Total** | **47** | |

---

**END OF CROSS-DOCUMENT ANALYSIS REPORT**
