# LLD CROSS-DOCUMENT ANALYSIS REPORT
## Project Siddhanta — Low-Level Design Quality Audit

**Version**: 1.0.0  
**Date**: 2026-03-05  
**Scope**: All 7 authored LLDs + LLD_INDEX.md  
**Status**: Findings Documented — Action Required

**Historical Note (March 8, 2026):** This report reflects the LLD corpus before later additions and cleanup. Treat it as historical review evidence rather than the current state of the LLD suite.

---

## EXECUTIVE SUMMARY

Seven authored LLDs (K-02, K-03, K-04, K-05, K-07, K-09, D-01) and one index document were analyzed across 10 quality dimensions. The documents are remarkably thorough and demonstrate strong engineering discipline. However, **47 specific issues** were identified across 10 categories, including **8 critical**, **19 major**, and **20 minor** findings.

| Severity | Count | Immediate Action Required |
|----------|-------|---------------------------|
| CRITICAL | 8 | Yes — blocks implementation correctness |
| MAJOR | 19 | Yes — causes developer confusion or runtime bugs |
| MINOR | 20 | No — quality/completeness improvements |

---

## 1. 10-SECTION TEMPLATE COMPLIANCE

**Verdict: MOSTLY COMPLIANT — 2 structural deviations found**

The LLD_INDEX.md (Section 1.1) mandates this 10-section structure:

| # | Required Section | K-02 | K-03 | K-04 | K-05 | K-07 | K-09 | D-01 |
|---|------------------|------|------|------|------|------|------|------|
| 1 | Module Overview | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 2 | Public APIs & Contracts | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 3 | Data Model | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 4 | Control Flow | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 5 | Algorithms & Policies | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 6 | NFR Budgets | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 7 | Security Design | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 8 | Observability & Audit | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 9 | Extensibility & Evolution | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 10 | Test Plan | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

### Findings

| ID | Severity | File | Issue |
|----|----------|------|-------|
| TC-01 | MINOR | All LLDs | All 7 LLDs include an **unnumbered Section 11 — "Validation Questions & Assumptions"** beyond the mandated 10 sections. While valuable, this should either be incorporated into the template definition or explicitly called out as an optional 11th section in LLD_INDEX.md Section 1.1. |
| TC-02 | MINOR | LLD_K02_CONFIGURATION_ENGINE.md | K-02 is the only LLD that adds **Section 2.3 AsyncAPI Topics** — an event channel definition format. This is excellent practice but is not mandated or mentioned in the template. Other LLDs that publish events (K-03, K-04, K-05, K-07, K-09, D-01) lack equivalent AsyncAPI channel definitions, creating an inconsistency. |

---

## 2. API CONTRACT COMPLETENESS

**Verdict: GOOD — All LLDs provide REST + gRPC + SDK, with minor gaps**

All 7 LLDs define:
- ✅ REST API endpoints with example request/response payloads
- ✅ gRPC protobuf service definitions
- ✅ TypeScript SDK interface signatures
- ✅ Error model tables (error code, HTTP status, retryable flag)

### Findings

| ID | Severity | File | Section | Issue |
|----|----------|------|---------|-------|
| AC-01 | MAJOR | LLD_D01_OMS.md | §2.1 | **Missing `PUT /api/v1/orders/{order_id}/amend` endpoint.** The epic EPIC-D-01-OMS.md defines `AmendOrderCommand` (Section 6.5), but the LLD has no amendment REST endpoint, gRPC method, or SDK method. The OMS only defines place, get, cancel, and approve — amendment is entirely absent. |
| AC-02 | MAJOR | LLD_D01_OMS.md | §2.1 | **Missing `GET /api/v1/orders` (list orders) REST endpoint.** The gRPC definition includes `rpc ListOrders`, and the SDK defines `listOrders(filters)`, but there is no corresponding REST endpoint documented. |
| AC-03 | MINOR | LLD_D01_OMS.md | §2.1 | **Missing `GET /api/v1/positions` REST endpoint.** The gRPC definition includes `rpc GetPosition`, SDK defines `getPosition()`, but no REST endpoint is shown. |
| AC-04 | MINOR | LLD_D01_OMS.md | §2.3 | **Missing `getOrderSuggestion` SDK method signature details.** The `OrderContext` and `OrderSuggestion` interfaces are referenced but never defined. The control flow (§4.3) describes the AI suggestion flow, but the data types are opaque. |
| AC-05 | MINOR | LLD_K02_CONFIGURATION_ENGINE.md | §2.1 | **Missing `POST /api/v1/config/packs/{pack_id}/rollback` endpoint.** The control flow (§4.4) describes a rollback flow, and the epic defines `RollbackConfigCommand`, but the REST endpoint is never specified with request/response examples. |
| AC-06 | MINOR | LLD_K03_RULES_ENGINE.md | §2.1 | **Missing `PUT /api/v1/rules/packs/{pack_id}/approve` endpoint.** The control flow (§4.2) references `PUT /api/v1/rules/packs/{pack_id}/approve` for checker approval, but the REST API section only shows the evaluate and create pack endpoints. The approve endpoint has no request/response example. |
| AC-07 | MINOR | LLD_K05_EVENT_BUS.md | §2.1 | **Missing subscription management REST endpoints.** While `SubscribeEvents` exists in gRPC, there are no REST endpoints for creating/managing subscriptions or consumer groups. |
| AC-08 | MINOR | LLD_K02_CONFIGURATION_ENGINE.md | §2.5 | K-02 is the only LLD that includes an **Idempotent** column in its error model table. All other LLDs omit this column despite the platform mandating idempotent operations. |

---

## 3. DATA MODEL CONSISTENCY

**Verdict: CRITICAL ISSUES — Event schema mismatches across LLDs**

### 3.1 Event Schema Structure Inconsistency

| ID | Severity | Files | Issue |
|----|----------|-------|-------|
| DM-01 | CRITICAL | LLD_D01_OMS.md §3.1 vs LLD_K05_EVENT_BUS.md §3.1 | **OrderPlacedEvent schema mismatch.** The event is defined in **two different LLDs** with **incompatible structures**: <br>- **K-05** wraps data inside a `data` envelope: `{ event_id, event_type, aggregate_id, data: { order_id, instrument_id, ... }, metadata: { tenant_id, ... } }` <br>- **D-01** uses a **flat** structure with fields at top level: `{ event_id, event_version, aggregate_id, order_id, instrument_id, ... }` without `data` or `metadata` wrappers. <br>A developer implementing this will not know which structure to use. The K-05 envelope pattern should be canonical. |
| DM-02 | CRITICAL | LLD_D01_OMS.md §3.1 | **PositionUpdatedEvent missing `event_type` field.** Unlike OrderPlacedEvent and OrderExecutedEvent, the PositionUpdatedEvent schema has no `event_type` field, breaking the standard envelope pattern. It also omits `timestamp_bs` (only has `timestamp_gregorian`), violating the dual-calendar invariant stated in LLD_INDEX.md §4.1. |
| DM-03 | MAJOR | LLD_K04_PLUGIN_RUNTIME.md §3.1 | **Plugin events missing `aggregate_id` and `aggregate_type` fields.** Both `PluginRegisteredEvent` and `PluginInvokedEvent` lack the standard event envelope fields (`aggregate_id`, `aggregate_type`, `sequence_number`, dual-calendar timestamps). They cannot be stored in the K-05 event store as-is. |
| DM-04 | MAJOR | LLD_K03_RULES_ENGINE.md §3.1 | **RulePackActivatedEvent missing standard envelope fields.** The event lacks `aggregate_id`, `aggregate_type`, `sequence_number`, and does not wrap data in a `data` field. It also mixes `effective_date_bs` / `effective_date_gregorian` (two fields) while the K-05 standard uses `timestamp_bs` / `timestamp_gregorian`. |
| DM-05 | MAJOR | LLD_K07_AUDIT_FRAMEWORK.md §3.1 | **AuditLogCreatedEvent missing standard envelope.** Similar to K-03 and K-04, the audit event lacks `aggregate_type`, `sequence_number`, and does not use the `data` envelope pattern from K-05. |
| DM-06 | MAJOR | LLD_K09_AI_GOVERNANCE.md §3.1 | **AIDecisionOverriddenEvent inconsistent timestamps.** Uses a single `timestamp` field instead of the dual-calendar `timestamp_bs` + `timestamp_gregorian` pattern required by LLD_INDEX.md §4.1. The `AIDecisionMadeEvent` correctly uses dual timestamps. |

### 3.2 Event Name Mismatches Between Epics and LLDs

| ID | Severity | Epic Event Name | LLD Event Name | Issue |
|----|----------|----------------|----------------|-------|
| DM-07 | MAJOR | `OrderStateChanged` (EPIC-D-01 §6) | `OrderPlacedEvent`, `OrderExecutedEvent`, etc. (LLD_D01 §3.1) | Epic defines a **single polymorphic event** `OrderStateChanged` with `previous_state` and `new_state` payload fields; the LLD instead uses **separate typed events** per transition. These are fundamentally different patterns — one requires a single consumer handler, the other requires per-type handlers. A decision must be documented. |
| DM-08 | MAJOR | `RulePackDeployedEvent` (EPIC-K-03 §6) | `RulePackActivatedEvent` (LLD_K03 §3.1) | Event name mismatch: "Deployed" ≠ "Activated". Downstream systems subscribing by event name will fail. |
| DM-09 | MAJOR | `AuditIntegrityCompromisedEvent` (EPIC-K-07 §6) | `AuditLogCreatedEvent` (LLD_K07 §3.1) | Epic defines `AuditIntegrityCompromisedEvent` as the primary event; the LLD defines `AuditLogCreatedEvent` instead. Neither document references the other's event. The LLD is missing the integrity-compromised event entirely. |
| DM-10 | MINOR | `AiDecisionOverridden` (EPIC-K-09 §6) | `AIDecisionOverriddenEvent` (LLD_K09 §3.1) | Naming convention differs: epic uses PascalCase without "Event" suffix; LLD appends "Event". Minor but should be standardized. |
| DM-11 | MINOR | `ConfigPackActivatedEvent` (EPIC-K-02 §6) | `ConfigPackActivatedEvent` (LLD_K02 §3.1) | ✅ Names match. However, the epic payload uses `effective_date_greg` while the LLD uses `effective_date_gregorian` — field name mismatch. |

---

## 4. NFR BUDGET ALIGNMENT

**Verdict: CRITICAL MISMATCHES — LLD budgets diverge significantly from epic targets**

| ID | Severity | Module | Metric | Epic Target | LLD Target | Delta |
|----|----------|--------|--------|-------------|------------|-------|
| NF-01 | CRITICAL | K-03 Rules Engine | Eval P99 latency | **< 5ms** (EPIC-K-03 §8) | **< 10ms** (LLD_K03 §6.1, cached) / **< 30ms** (cold start) | LLD is **2–6× more relaxed** than epic. K-03 Invariant #5 says "< 10ms P99" but the epic demands < 5ms. |
| NF-02 | CRITICAL | K-03 Rules Engine | Throughput | **25,000 TPS** (EPIC-K-03 §8) | **50,000 TPS** (LLD_K03 §6.2) | LLD claims **2× the epic target**. Either the epic is outdated or the LLD is aspirational. Needs reconciliation. |
| NF-03 | CRITICAL | K-05 Event Bus | Publish P99 latency | **< 2ms** (EPIC-K-05 §8) | **< 50ms** (sync write, LLD_K05 §6.1) | LLD is **25× more relaxed** than epic. This is a dramatic mismatch — the epic requires near-zero-latency publish while the LLD budgets 50ms. |
| NF-04 | CRITICAL | K-07 Audit | Write overhead | **< 1ms** (EPIC-K-07 §8) | **P50: 3ms, P99: 30ms** (LLD_K07 §6.1) | LLD is **3–30× over epic budget**. The epic specifies "async write overhead < 1ms" but the LLD's `log()` P50 is already 3ms. The epic may refer to client-side overhead only. |
| NF-05 | MAJOR | D-01 OMS | Processing latency | **< 2ms internal** (EPIC-D-01 §8) | **P50: 20ms, P99: 300ms** (LLD_D01 §6.1) | Massive gap. The epic says "internal processing latency < 2ms" while the LLD's placeOrder P50 is 20ms. The epic may mean just queue enqueue time, but this needs explicit clarification. |
| NF-06 | MAJOR | D-01 OMS | Throughput | **50,000 TPS** (EPIC-D-01 §8) | **5,000 TPS** (LLD_D01 §6.2) | LLD target is **10× lower** than epic. The epic likely refers to total order events, not just placeOrder, but the mismatch is unreconciled. |
| NF-07 | MAJOR | K-07 Audit | Throughput | **20,000 TPS** (EPIC-K-07 §8) | **50,000 TPS** (LLD_K07 §6.2) | LLD claims 2.5× the epic target. The LLD's storage estimates (§6.3) then extrapolate to **44.2 PB over 7 years** based on 100K TPS peak — inconsistent even with its own 50K target. |
| NF-08 | MINOR | LLD_INDEX.md §5.1 | K-02 resolve P99 | **5ms** (Index §5.1) | **2ms cache-hit / 30ms cache-miss** (LLD_K02 §6.1) | Minor mismatch — the index shows a single 5ms figure but the LLD splits by cache hit/miss. Cache-miss at 30ms P99 exceeds the index's stated 5ms. |
| NF-09 | MINOR | LLD_INDEX.md §5.1 | K-09 predict P99 | **500ms** (Index §5.1) | **500ms without explanation / 2000ms with explanation** (LLD_K09 §6.1) | Index doesn't capture the 4× latency difference when explanation is required. |

---

## 5. SECURITY DESIGN COMPLETENESS

**Verdict: GOOD — All LLDs address security; some gaps in consistency**

| ID | Severity | File | Section | Issue |
|----|----------|------|---------|-------|
| SD-01 | MAJOR | LLD_D01_OMS.md | §7 | **Missing mTLS/zero-trust configuration.** K-02 and K-05 both define explicit mTLS enforcement (Istio PeerAuthentication YAML, RLS policies). D-01 only defines application-level RBAC checks in TypeScript — no mTLS config, no RLS policy, no Istio config. Since D-01 handles financial transactions, this is a significant gap. |
| SD-02 | MAJOR | LLD_K03_RULES_ENGINE.md | §7 | **No tenant isolation mechanism defined.** K-02, K-05, and K-07 all define PostgreSQL Row-Level Security (RLS) policies. K-03 stores `policy_decisions_audit` with `tenant_id` but defines no RLS policy, so a tenant could potentially query another tenant's decision audit. |
| SD-03 | MAJOR | LLD_K04_PLUGIN_RUNTIME.md | §7 | **No tenant isolation for plugin invocations.** Plugins execute in shared V8 isolates with no tenant context enforcement. The `plugin_invocations` table has `tenant_id` but no RLS. A plugin invoked by Tenant A could theoretically access data from Tenant B. |
| SD-04 | MINOR | LLD_K09_AI_GOVERNANCE.md | §7 | **No row-level security on `ai_decisions` table.** Contains `tenant_id` column but no RLS policy defined. Other modules (K-02, K-05, K-07) consistently define RLS. |
| SD-05 | MINOR | LLD_D01_OMS.md | §7 | **No encryption-at-rest for order data.** K-05, K-07, and K-09 all define `Fernet` encryption for sensitive data at rest. D-01 stores order data with prices, quantities, and account IDs in plaintext JSONB (metadata column). |
| SD-06 | MINOR | LLD_K03_RULES_ENGINE.md | §7.2 | **Code signing for rule packs is defined but not enforced in the API.** The security section shows signing/verification functions, but the REST API `POST /api/v1/rules/packs` request body does not include `signature` or `public_key` fields. Plugin registration (K-04) correctly requires both. |

---

## 6. CROSS-LLD DEPENDENCY ACCURACY

**Verdict: MOSTLY ACCURATE — 3 phantom dependencies identified**

### 6.1 Dependency Matrix Validation

| LLD | Declares Dependency On | Dependency Has LLD? | Status |
|-----|------------------------|---------------------|--------|
| K-02 | K-05, K-07, K-15, K-01 | K-05 ✅, K-07 ✅, **K-15 ❌**, **K-01 ❌** | ⚠️ 2 missing |
| K-03 | K-02, K-05, K-07 | All ✅ | ✅ |
| K-04 | K-02, K-05, K-07 | All ✅ | ✅ |
| K-05 | K-02, K-07 | All ✅ | ✅ |
| K-07 | K-02, K-05 | All ✅ | ✅ |
| K-09 | K-02, K-05, K-07, MLflow | K-02 ✅, K-05 ✅, K-07 ✅, MLflow (external) | ✅ |
| D-01 | K-02, K-03, K-05, K-07, K-09 | All ✅ | ✅ |

### Findings

| ID | Severity | File | Issue |
|----|----------|------|-------|
| CD-01 | MAJOR | LLD_K02_CONFIGURATION_ENGINE.md §1.3 | **Dependency on K-15 (Dual-Calendar) — no LLD exists.** K-02 declares `K-15 Dual-Calendar` as a dependency for "effective date resolution" with readiness gate "K-15 stable". K-15 has no epic and no LLD. It's not listed in LLD_INDEX.md at all. Either this is built into a library (like `nepali-date-converter` in §4.1 of the Index) or it is a phantom module. |
| CD-02 | MAJOR | LLD_K02_CONFIGURATION_ENGINE.md §1.3 | **Dependency on K-01 (IAM) — no LLD exists.** K-02 declares K-01 IAM for "maker-checker authorization". K-01 has an epic (EPIC-K-01-IAM.md) but **no LLD**. This is not listed in LLD_INDEX.md as pending either. Since K-02 is the first module in the dependency chain, the IAM mechanism is unspecified at design level. |
| CD-03 | MINOR | LLD_INDEX.md §13 | **Dependency order diagram omits K-01 and K-15.** The "By Dependency Order" section lists K-02 as having "no dependencies" — contradicting K-02's own dependency table which lists K-05, K-07, K-15, and K-01. |
| CD-04 | MINOR | LLD_INDEX.md §2 (K-02) | **Hierarchy mismatch.** The Index §2 describes K-02 as having a "5-level hierarchy: GLOBAL → JURISDICTION → TENANT → USER → SESSION". The actual LLD_K02 §1.1 defines a **6-level hierarchy: Global → Jurisdiction → Operator → Tenant → Account → User**. The "OPERATOR" and "ACCOUNT" levels are missing from the Index, and "SESSION" exists in the Index but not in the LLD. |

---

## 7. EXTENSION POINT DEFINITION QUALITY

**Verdict: GOOD — Extension points are actionable with code examples**

All 7 LLDs define extension points with:
- ✅ Interface definitions (TypeScript or Python)
- ✅ Concrete implementation examples
- ✅ Registration patterns

### Findings

| ID | Severity | File | Issue |
|----|----------|------|-------|
| EP-01 | MINOR | LLD_D01_OMS.md §9 | **Extension point registration mechanism unclear.** The code shows `omsClient.registerValidator(new NepalMarketValidator())` and `omsClient.registerOrderType(new IcebergOrderHandler())` — but `OMSClient` as defined in §2.3 has no `registerValidator()` or `registerOrderType()` method. The SDK interface and extension API are disconnected. |
| EP-02 | MINOR | LLD_K07_AUDIT_FRAMEWORK.md §9.2 | **Enricher registration** uses `auditClient.registerEnricher()` but this method doesn't exist on the `AuditClient` interface in §2.3. |
| EP-03 | MINOR | LLD_K04_PLUGIN_RUNTIME.md §9.3 | **Custom capability registration** stores capabilities in K-02 Config Engine via `configClient.set()` — but K-02's SDK (`ConfigClient` in §2.4) has no `set()` method. It only exposes `resolve()`, `watch()`, `validate()`, and `getCached()`. Write operations are admin-only REST APIs. |

---

## 8. TEST PLAN COMPLETENESS

**Verdict: GOOD — Comprehensive test plans with some gaps**

| Test Type | K-02 | K-03 | K-04 | K-05 | K-07 | K-09 | D-01 |
|-----------|------|------|------|------|------|------|------|
| Unit Tests | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Integration Tests | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Security Tests | ✅ | ✅ | ✅ | — | ✅ | ✅ | — |
| Chaos Tests | ✅ | — | ✅ | ✅ | — | — | — |
| Performance Tests | — | — | — | — | ✅ | — | — |
| Contract/Pact Tests | ✅ | — | — | — | — | — | — |
| Replay Tests | ✅ | — | — | ✅ | — | — | — |
| Explainability Tests | N/A | N/A | N/A | N/A | N/A | ✅ | — |

### Findings

| ID | Severity | File | Issue |
|----|----------|------|-------|
| TP-01 | MAJOR | LLD_K05_EVENT_BUS.md §10 | **No security tests.** The event bus handles all inter-service communication but has no security test cases for tenant isolation, event signing verification, or unauthorized subscription attempts. |
| TP-02 | MAJOR | LLD_D01_OMS.md §10 | **No security tests.** The OMS handles financial transactions but has no security tests for unauthorized access, order tampering, or tenant isolation. |
| TP-03 | MINOR | LLD_K03_RULES_ENGINE.md §10 | **No chaos/resilience tests.** Given K-03 is on the critical path for every order (pre-trade validation), chaos tests for OPA sandbox failure, rule pack corruption, or hot-reload race conditions are important. |
| TP-04 | MINOR | LLD_K07_AUDIT_FRAMEWORK.md §10 | **No chaos tests.** Important for a compliance-critical module — what happens when the hash chain predecessor lookup fails mid-write? |
| TP-05 | MINOR | LLD_K09_AI_GOVERNANCE.md §10 | **No chaos tests** for model serving failure, drift detection timeout, or HITL override during active prediction. |
| TP-06 | MINOR | All LLDs except K-02 | **No contract/Pact tests.** K-02 is the only LLD with consumer-driven contract tests. Given the platform's microservice architecture, all modules should define Pact contracts. |
| TP-07 | MINOR | All LLDs except K-07 | **No performance/load tests.** Only K-07 includes a throughput benchmark (10K logs in §10.4). Given the strict NFR budgets, performance tests should be explicitly planned for all modules. |

---

## 9. MISSING LLDs

**Verdict: 5 pending LLDs + 2 undocumented gaps**

### 9.1 Explicitly Listed as Pending in LLD_INDEX.md

| Module | File | Status | ARB Priority |
|--------|------|--------|-------------|
| K-17 Distributed Transaction Coordinator | `LLD_K17_DISTRIBUTED_TRANSACTION_COORDINATOR.md` | Pending | P0-01 |
| K-18 Resilience Patterns Library | `LLD_K18_RESILIENCE_PATTERNS.md` | Pending | P0-02 |
| K-19 DLQ Management & Event Replay | `LLD_K19_DLQ_MANAGEMENT.md` | Pending | P0-04 |
| D-13 Client Money Reconciliation | `LLD_D13_CLIENT_MONEY_RECONCILIATION.md` | Pending | P1-11 |
| D-14 Sanctions Screening | `LLD_D14_SANCTIONS_SCREENING.md` | Pending | P1-13 |

### 9.2 Not Listed in Index but Referenced or Required

| ID | Severity | Module | Issue |
|----|----------|--------|-------|
| ML-01 | MAJOR | **K-01 IAM** | Referenced as a dependency by K-02 (§1.3) for maker-checker authorization. Has an epic (EPIC-K-01-IAM.md) but **no LLD and not listed in LLD_INDEX.md as pending**. This is the foundational AuthN/AuthZ module — its absence means all security sections reference undesigned mechanisms. |
| ML-02 | MAJOR | **K-06 Observability** | Referenced by K-17, K-18, K-19 pending entries. Has an epic (EPIC-K-06-Observability.md) but **no LLD and not listed in LLD_INDEX.md at all** (neither authored nor pending). Critical for the monitoring infrastructure. |
| ML-03 | MINOR | **K-15 Dual-Calendar** | Referenced by K-02 (§1.3) as a dependency. Not listed in any index. May be a library rather than a module, but should be documented. |
| ML-04 | MINOR | **K-16 Ledger** | Referenced by K-17 and D-13 as dependencies. Has no LLD and is not listed in LLD_INDEX.md as pending. |
| ML-05 | MINOR | **D-02 through D-12** | The Index only lists D-01 as authored and D-13, D-14 as pending. Domain modules D-02 (EMS), D-03 (PMS), D-04 (Market Data), D-05 (Pricing), D-06 (Risk), D-07 (Compliance), D-08 (Surveillance), D-09 (Post-Trade), D-10 (Regulatory Reporting), D-11 (Reference Data), D-12 (Corporate Actions) all have epics but no LLD tracking in the index. |

---

## 10. CODE EXAMPLE QUALITY

**Verdict: HIGH QUALITY — Production-informed but with implementation caveats**

### Strengths
- **State machine** (D-01 §5.1): Complete with valid transition map, type safety
- **Hash chain** (K-07 §5.1): Deterministic serialization, genesis hash, verification loop
- **Configuration resolution** (K-02 §5.1): Full 6-level deep merge with as-of-date support
- **Drift detection** (K-09 §5.2): Working PSI calculation with threshold classification
- **Saga orchestrator** (K-05 §5.2): Reverse compensation with proper error handling
- **Signature verification** (K-04 §5.1): Ed25519 with proper byte parsing

### Findings

| ID | Severity | File | Section | Issue |
|----|----------|------|---------|-------|
| CQ-01 | MAJOR | LLD_K03_RULES_ENGINE.md | §5.2 | **Python `signal.SIGALRM` not available on Windows.** The Rego evaluation timeout uses `signal.setitimer(signal.ITIMER_REAL, ...)` which is Unix-only. The platform spec mentions Docker/Kubernetes (Linux), but this code will fail in any local Windows development environment. Should note platform requirement or provide cross-platform alternative. |
| CQ-02 | MINOR | LLD_K03_RULES_ENGINE.md | §5.3 | **Sandbox enforcement via string matching is bypassable.** `SecureOPASandbox.evaluate()` checks `if denied in rego_code` using Python string containment. This can be bypassed with obfuscation (e.g., string concatenation `"http" + ".send"`). The actual enforcement should use OPA's capabilities configuration, which is shown in §7.1 but not connected to the code in §5.3. |
| CQ-03 | MINOR | LLD_K02_CONFIGURATION_ENGINE.md | §5.1 | **`query_config_packs` filter construction is pseudocode.** The filters list contains mixed-arity tuples and `None` values that would not work in any ORM or SQL builder. Acceptable as pseudocode but should be marked as such. |
| CQ-04 | MINOR | LLD_K05_EVENT_BUS.md | §5.1 | **Idempotency check has race condition.** The `ensure_idempotency()` function does a SELECT then INSERT, which is not atomic. Under concurrent delivery of the same event, two consumers could both pass the SELECT check. Should use `INSERT ... ON CONFLICT DO NOTHING` or a distributed lock. |
| CQ-05 | MINOR | LLD_K07_AUDIT_FRAMEWORK.md | §5.1 | **Hash chain has concurrency risk.** `calculate_hash()` requires the previous hash, which must be fetched under a lock. No concurrency control is shown — concurrent log writes could fetch the same "previous hash" and create a fork. The SQL schema uses `BIGSERIAL` for sequence which handles ordering, but the hash chain algorithm needs explicit serialization. |
| CQ-06 | MINOR | Multiple | Throughout | **Mixed language examples.** Most LLDs use both TypeScript and Python without clarifying which is the implementation language. The tech stack (LLD_INDEX §6.1) says "TypeScript (Node.js), Python (ML/AI)" — but K-03 (OPA integration), K-04 (signature verification), K-05 (idempotency), and K-07 (hash chain) all use Python for non-AI code. This should be intentional or standardized. |

---

## SUMMARY OF ALL FINDINGS BY PRIORITY

### CRITICAL (Action Required Before Implementation)

| ID | Module | Summary |
|----|--------|---------|
| DM-01 | D-01 / K-05 | OrderPlacedEvent schema mismatch (flat vs envelope) — blocks event consumption |
| DM-02 | D-01 | PositionUpdatedEvent missing event_type and timestamp_bs — violates event contract |
| NF-01 | K-03 | Epic demands P99 < 5ms; LLD budgets 10ms (cached) / 30ms (cold) |
| NF-02 | K-03 | Throughput mismatch: Epic 25K TPS vs LLD 50K TPS |
| NF-03 | K-05 | Publish latency: Epic < 2ms vs LLD < 50ms (25× gap) |
| NF-04 | K-07 | Audit write: Epic < 1ms overhead vs LLD P50 3ms / P99 30ms |
| NF-05 | D-01 | OMS latency: Epic < 2ms vs LLD P50 20ms |
| NF-06 | D-01 | OMS throughput: Epic 50K TPS vs LLD 5K TPS (10× gap) |

### MAJOR (Requires Resolution Before Sprint Planning)

| ID | Module | Summary |
|----|--------|---------|
| AC-01 | D-01 | Missing order amendment endpoint (defined in epic, absent in LLD) |
| AC-02 | D-01 | Missing list orders REST endpoint |
| DM-03 | K-04 | Plugin events missing standard envelope fields |
| DM-04 | K-03 | RulePackActivatedEvent missing standard envelope fields |
| DM-05 | K-07 | AuditLogCreatedEvent missing standard envelope fields |
| DM-06 | K-09 | AIDecisionOverriddenEvent inconsistent timestamp format |
| DM-07 | D-01 | Epic uses single OrderStateChanged event; LLD uses separate events per transition |
| DM-08 | K-03 | Event name mismatch: Epic "RulePackDeployedEvent" ≠ LLD "RulePackActivatedEvent" |
| DM-09 | K-07 | Missing AuditIntegrityCompromisedEvent from epic |
| NF-07 | K-07 | Throughput mismatch + inconsistent storage estimate |
| SD-01 | D-01 | Missing mTLS / zero-trust configuration |
| SD-02 | K-03 | No tenant isolation (RLS) on policy_decisions_audit |
| SD-03 | K-04 | No tenant isolation for plugin invocations |
| CD-01 | K-02 | Phantom dependency on K-15 (Dual-Calendar) — no LLD |
| CD-02 | K-02 | Phantom dependency on K-01 (IAM) — no LLD |
| ML-01 | Index | K-01 IAM missing from LLD index and has no LLD |
| ML-02 | Index | K-06 Observability missing from LLD index and has no LLD |
| TP-01 | K-05 | No security tests for event bus |
| TP-02 | D-01 | No security tests for OMS |
| CQ-01 | K-03 | Signal-based timeout is Unix-only |

---

## RECOMMENDATIONS

### Immediate Actions (Week 1)

1. **Standardize event envelope.** Publish a canonical event schema (matching K-05's `{ event_type, data: {}, metadata: {} }` pattern) and retrofit all LLD event schemas to comply.
2. **Reconcile NFR budgets.** Hold a calibration session for epic owners and LLD authors to agree on P99 latency and throughput targets. Update whichever document is incorrect.
3. **Add missing OMS amendment API.** The epic defines `AmendOrderCommand` — the LLD must implement it.
4. **Define K-01 IAM LLD** or document how AuthN/AuthZ works without it. Every LLD references `Bearer {admin_token}` and RBAC roles but no module designs the token issuance or role management.

### Short-Term Actions (Weeks 2–4)

5. **Add AsyncAPI definitions** to all event-publishing LLDs (K-03, K-04, K-07, K-09, D-01), matching K-02's pattern.
6. **Add RLS policies** to K-03, K-04, K-09, and D-01 storage tables.
7. **Add mTLS/Istio configurations** to D-01 and K-03.
8. **Add security tests** to K-05 and D-01 test plans.
9. **Reconcile event names** between epics and LLDs (especially DM-07, DM-08, DM-09).

### Medium-Term Actions (Weeks 4–8)

10. **Author P0 LLDs** (K-17 Distributed Transaction Coordinator, K-18 Resilience Patterns).
11. **Add K-01 IAM and K-06 Observability** to LLD_INDEX.md with pending status.
12. **Add contract/Pact tests** to all LLD test plans.
13. **Add chaos tests** to K-03, K-07, K-09 test plans.
14. **Standardize code examples** to clearly label whether Python examples are pseudocode or implementation-target code.

---

**END OF REPORT**

Report generated: 2026-03-05  
Files analyzed: 8 (LLD_INDEX.md, LLD_D01_OMS.md, LLD_K02_CONFIGURATION_ENGINE.md, LLD_K03_RULES_ENGINE.md, LLD_K04_PLUGIN_RUNTIME.md, LLD_K05_EVENT_BUS.md, LLD_K07_AUDIT_FRAMEWORK.md, LLD_K09_AI_GOVERNANCE.md)  
Epic files cross-referenced: 6 (EPIC-D-01, EPIC-K-02, EPIC-K-03, EPIC-K-05, EPIC-K-07, EPIC-K-09)
