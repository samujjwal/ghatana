# LLD Suite Implementation Readiness Review Report — Project Siddhanta

**Reviewer**: Architecture Review Board  
**Date**: March 2026  
**Documents Reviewed**: 16 LLDs + LLD_INDEX.md (v2.2.0)  
**Total Lines Analyzed**: ~15,250  
**Overall Readiness Score**: **7.4 / 10**

**Historical Note (March 8, 2026):** This review is retained as a design audit snapshot. Some gaps described here were later closed in follow-up hardening and cleanup passes.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Review Criteria](#2-review-criteria)
3. [Per-LLD Assessments](#3-per-lld-assessments)
   - [K-01 IAM](#k-01-iam)
   - [K-02 Configuration Engine](#k-02-configuration-engine)
   - [K-03 Rules Engine](#k-03-rules-engine)
   - [K-04 Plugin Runtime](#k-04-plugin-runtime)
   - [K-05 Event Bus](#k-05-event-bus)
   - [K-06 Observability](#k-06-observability)
   - [K-07 Audit Framework](#k-07-audit-framework)
   - [K-09 AI Governance](#k-09-ai-governance)
   - [K-15 Dual-Calendar](#k-15-dual-calendar)
   - [K-16 Ledger Framework](#k-16-ledger-framework)
   - [K-17 Distributed Transaction Coordinator](#k-17-distributed-transaction-coordinator)
   - [K-18 Resilience Patterns](#k-18-resilience-patterns)
   - [K-19 DLQ Management](#k-19-dlq-management)
   - [D-01 OMS](#d-01-oms)
   - [D-13 Client Money Reconciliation](#d-13-client-money-reconciliation)
   - [D-14 Sanctions Screening](#d-14-sanctions-screening)
4. [Cross-Cutting Issues](#4-cross-cutting-issues)
5. [Issue Catalogue by Severity](#5-issue-catalogue-by-severity)
6. [NFR Alignment Analysis](#6-nfr-alignment-analysis)
7. [Security Posture Summary](#7-security-posture-summary)
8. [Implementation Roadmap Recommendations](#8-implementation-roadmap-recommendations)
9. [Overall Assessment](#9-overall-assessment)

---

## 1. Executive Summary

The 16-LLD suite for Project Siddhanta represents a **comprehensive, implementation-ready design** for a multi-tenant capital markets platform targeting Nepal (SEBON) as the primary jurisdiction with cross-jurisdiction extensibility (SEBI, MiFID II).

**Strengths at a Glance**:
- Consistent 10-section template across all LLDs (APIs, data model, control flow, algorithms, NFR, security, observability, extensibility, test plan, validation questions)
- Rigorous dual-calendar (Bikram Sambat + Gregorian) treatment across all modules
- Well-defined T1/T2/T3 plugin taxonomy consistently adopted
- Event sourcing with a standard K-05 envelope used uniformly
- Maker-checker patterns where appropriate
- Each LLD declares explicit non-goals and validation assumptions

**Key Risks**:
- **RLS/tenant isolation gaps** in 5 of 16 LLDs — critical for multi-tenant SaaS
- **Missing cross-LLD state references** (D-01 `COMPLIANCE_HOLD` status referenced by D-14 but absent from D-01 state machine)
- **Phantom dependency**: K-01 references K-14 (Secrets Management) which does not exist in the 16-LLD set
- **Observability LLD (K-06)** is the thinnest at 566 lines and missing key detail
- **Storage estimates in K-07** project 63 PB over 10 years — needs re-validation

**Summary by Readiness Tier**:

| Tier | LLDs | Count |
|------|------|-------|
| **High Readiness (8-9)** | K-02, K-05, K-15, K-16, K-19, D-13, D-14 | 7 |
| **Medium Readiness (7)** | K-01, K-04, K-07, K-09, K-17, K-18, D-01 | 7 |
| **Needs Work (5-6)** | K-03, K-06 | 2 |

---

## 2. Review Criteria

Each LLD was evaluated across 8 dimensions:

| # | Criterion | Weight | Description |
|---|-----------|--------|-------------|
| 1 | **API Completeness** | 15% | REST + gRPC + SDK all defined; error model complete; pagination/filtering for list endpoints |
| 2 | **Data Model Quality** | 15% | Tables with DDL, indexes, RLS, dual-calendar columns, FK relationships, event schemas K-05 compliant |
| 3 | **NFR Alignment** | 15% | Latency/throughput/storage targets present, realistic, and consistent with INDEX targets |
| 4 | **Security Design** | 15% | RLS, encryption, access control, maker-checker, air-gap support |
| 5 | **Test Plan Quality** | 10% | Unit + integration + chaos tests; edge cases; regulatory scenarios |
| 6 | **Cross-Module Consistency** | 10% | References to other LLDs accurate; shared contracts (events, envelopes) aligned |
| 7 | **T1/T2/T3 Extension Points** | 10% | Jurisdiction extensibility via content packs; clear plugin interfaces |
| 8 | **Real-World Gaps** | 10% | Missing operational scenarios; production deployment concerns |

---

## 3. Per-LLD Assessments

---

### K-01 IAM

**Implementation Readiness: 7 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 8 | REST + gRPC + SDK defined; comprehensive error codes (IAM_E001–E008) |
| Data Model | 7 | 7 tables with RLS on all; missing explicit FK between `user_roles` and `roles` in DDL |
| NFR Alignment | 8 | Login 300ms P99, token validation 2ms cached, 100K/sec — realistic |
| Security | 8 | bcrypt cost 12, Ed25519 JWT, progressive lockout, 90-day rotation, 12-password history |
| Test Plan | 6 | Tests listed but notably less code-level detail than K-02 or K-05 |
| Cross-Module | 6 | **References K-14 Secrets Management** — not in LLD set (§1.3 Dependencies) |
| T1/T2/T3 | 7 | KYC provider routing via T3 plugins; jurisdiction-specific auth rules via T2 |
| Real-World Gaps | 6 | No session revocation broadcast (how to invalidate cached JWT across nodes?); no WebAuthn attestation flow detail |

**Key Strengths**:
- Progressive lockout algorithm with configurable tiers (§5.1)
- Multi-scope RBAC model (own/tenant/global) well-designed
- MFA with TOTP + WebAuthn options

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K01-1 | CRITICAL | §1.3 | References K-14 Secrets Management — phantom dependency; no LLD exists |
| K01-2 | HIGH | §3.2 | No explicit JWT revocation list (JRL) or token blacklist mechanism |
| K01-3 | MEDIUM | §10 | Test plan lacks integration test with K-03 for permission evaluation |
| K01-4 | LOW | §6.1 | Token validation "2ms P99 cached" — no uncached latency stated |

**Missing Items Blocking Implementation**:
- Resolution of K-14 dependency — either create the LLD or explain secret management inline
- JWT revocation mechanism for stateless tokens (short-lived + refresh, or token blacklist?)

---

### K-02 Configuration Engine

**Implementation Readiness: 9 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 9 | REST + gRPC + SDK; set/get/resolve/validate; canary rollout support; batch operations |
| Data Model | 9 | `config_schemas`, `config_packs`, `resolved_config_cache` (materialized view); RLS present |
| NFR Alignment | 9 | Cache hit 2ms P99, miss 30ms P99, 20K TPS — well-decomposed |
| Security | 9 | Maker-checker, config signing, air-gap bundles with Ed25519 verification |
| Test Plan | 10 | **Most comprehensive** — unit, contract, integration, replay, chaos, security tests with code |
| Cross-Module | 8 | No dependencies (foundational); well-referenced by all other LLDs |
| T1/T2/T3 | 9 | 6-level hierarchy (GLOBAL→JURISDICTION→OPERATOR→TENANT→ACCOUNT→USER) |
| Real-World Gaps | 8 | 5 explicit validation assumptions flagged; tenant:jurisdiction 1:1 assumption needs confirmation |

**Key Strengths**:
- Deep merge algorithm with clear precedence rules (§5.1)
- Canary rollout strategy for configuration changes (§4.4)
- Hot-reload via K-05 events with cache invalidation
- Air-gap bundle format with Ed25519 signature

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K02-1 | MEDIUM | §11 | Assumption #3: "1:1 tenant-to-jurisdiction" — multi-jurisdiction tenants may break hierarchy |
| K02-2 | LOW | §3.2 | `resolved_config_cache` as materialized view — refresh frequency not specified |
| K02-3 | LOW | §11 | 5 open validation questions need stakeholder sign-off before implementation |

**Missing Items Blocking Implementation**:
- Stakeholder answers to 5 validation questions (§11)

---

### K-03 Rules Engine

**Implementation Readiness: 6 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 7 | REST + gRPC + SDK defined; evaluate/register/validate endpoints |
| Data Model | 4 | **No RLS on `rule_packs` table**; **no `tenant_id` column** on `rule_packs` |
| NFR Alignment | 7 | 10ms P99 cached, 50K TPS — reasonable for OPA |
| Security | 6 | Sandbox with denied builtins (http.send, opa.runtime, trace) — good; but no RLS |
| Test Plan | 7 | Sandbox escape tests included; but fewer test cases than average |
| Cross-Module | 7 | Jurisdictional fallback (jurisdiction → GLOBAL) well-defined |
| T1/T2/T3 | 8 | T2 OPA/Rego packs per jurisdiction — core extensibility mechanism |
| Real-World Gaps | 5 | No guidance on policy versioning conflicts when multiple jurisdictions overlap |

**Key Strengths**:
- Sandboxed OPA execution with explicit denied builtins list (§5.1)
- Jurisdictional fallback resolution with GLOBAL defaults (§5.2)
- 100ms execution timeout protection

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K03-1 | CRITICAL | §3.2 | `rule_packs` table has **no RLS** — multi-tenant isolation broken |
| K03-2 | CRITICAL | §3.2 | `rule_packs` table has **no `tenant_id` column** — cannot partition by tenant |
| K03-3 | HIGH | §3.2 | `policy_decisions_audit` table — no RLS noted |
| K03-4 | MEDIUM | §9 | No policy version conflict resolution (two T2 packs for same policy path?) |
| K03-5 | LOW | §2.1 | No bulk evaluation endpoint (batch policy checks for order lists) |

**Missing Items Blocking Implementation**:
- Add `tenant_id` column + RLS to `rule_packs` and `policy_decisions_audit`
- Clarify policy precedence when multiple T2 packs define the same rule path

---

### K-04 Plugin Runtime

**Implementation Readiness: 7 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 8 | REST + gRPC + SDK; install/invoke/uninstall; hot-swap support |
| Data Model | 5 | **No RLS** on `plugins` or `plugin_invocations`; **no `tenant_id`** on `plugins` table |
| NFR Alignment | 7 | invoke() 50ms P99 cached, 10K TPS; 100MB memory limit per T2 — reasonable |
| Security | 9 | **Excellent**: Ed25519 signature verification, YARA malware scanning, capability-based access, T3 network isolation |
| Test Plan | 7 | Good coverage of sandbox escape and capability enforcement |
| Cross-Module | 7 | Well-integrated with K-02 (config), K-05 (events), K-03 (T2 rules) |
| T1/T2/T3 | 9 | Three-tier isolation model (process / V8 sandbox / container) is well-architected |
| Real-World Gaps | 6 | No plugin marketplace/discovery mechanism; no plugin dependency conflict resolution |

**Key Strengths**:
- Three-tier isolation model (T1=process, T2=V8 sandbox, T3=container) (§5.1)
- YARA malware scanning before artifact loading (§7.2)
- Graceful degradation with fallback behavior on plugin failure (§5.4)
- Capability-based access control per plugin

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K04-1 | CRITICAL | §3.2 | `plugins` table has **no RLS** and **no `tenant_id`** — platform-global vs tenant-scoped unclear |
| K04-2 | HIGH | §3.2 | `plugin_invocations` table has **no RLS** — tenant isolation gap |
| K04-3 | MEDIUM | §9 | No plugin dependency graph / conflict detection between T3 plugins |
| K04-4 | LOW | §6.1 | T3 container cold-start latency not specified |

**Missing Items Blocking Implementation**:
- Clarify plugin scoping model: are plugins tenant-scoped or platform-global?
- Add `tenant_id` + RLS to `plugins` and `plugin_invocations` if tenant-scoped

---

### K-05 Event Bus

**Implementation Readiness: 8 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 9 | REST + gRPC + SDK; publish/subscribe/replay; saga orchestration; dead-letter routing |
| Data Model | 7 | Standard event envelope excellent; **RLS uses JSONB metadata field** (performance concern); `processed_events` DDL missing |
| NFR Alignment | 9 | Critical-path publish 2ms P99, 100K TPS publish, 200K TPS consume — ambitious but defended |
| Security | 8 | Event encryption at rest, event signing, RLS on events table |
| Test Plan | 8 | Comprehensive unit + integration + schema evolution + chaos tests |
| Cross-Module | 9 | **Highest cross-module impact** — standard envelope adopted by all 15 other LLDs |
| T1/T2/T3 | 7 | Saga DSL for custom saga definitions via T1 |
| Real-World Gaps | 7 | Saga compensation failure only logs error (§5.2) — should escalate to K-19 |

**Key Strengths**:
- Standard event envelope (§3.1) uniformly adopted across all LLDs — truly foundational
- Idempotent consumer support with deduplication
- Schema evolution with event_version and upcasting
- Event partitioning by aggregate_id for ordering guarantees

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K05-1 | HIGH | §3.2 | RLS policy accesses `metadata->>'tenant_id'` on JSONB — PostgreSQL will do seq scan unless functional index exists; **performance risk at 100K TPS** |
| K05-2 | HIGH | §3.2 | `processed_events` table referenced in idempotency code but **DDL not defined** in data model section |
| K05-3 | MEDIUM | §5.2 | Saga compensation failure only logs error — should escalate to K-19 DLQ (K-17 §4.2 handles this, but K-05 §5.2 doesn't mention it) |
| K05-4 | LOW | §3.1 | `acks=1` for critical topics trades durability for latency — document explicit data loss risk window |

**Missing Items Blocking Implementation**:
- Add functional GIN or expression index on `(metadata->>'tenant_id')` for RLS performance
- Add `processed_events` table DDL

---

### K-06 Observability

**Implementation Readiness: 5 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 4 | REST endpoints minimal; **no gRPC service definition** |
| Data Model | 4 | `slo_definitions` and `alert_rules` tables — **no RLS, no `tenant_id`** |
| NFR Alignment | 6 | 1M data points/min metrics, 500K events/sec logs — targets stated but architecture to achieve them unclear |
| Security | 3 | **No RLS on any table; no `tenant_id`**; no metric access control per tenant |
| Test Plan | 5 | Relatively sparse compared to other LLDs |
| Cross-Module | 7 | Referenced by all LLDs for metrics/logs/traces — but underspecified |
| T1/T2/T3 | 5 | SLO definitions via config but no T1/T2 pack examples |
| Real-World Gaps | 4 | No log sampling strategy at 500K events/sec; no metric cardinality enforcement DDL; no multi-tenant metrics isolation |

**Key Strengths**:
- Adaptive sampling strategy (§5.2) — traces down-sampled based on importance
- Meta-observability concept (monitoring the monitors) (§5.4)
- SLO burn-rate alerting per Google SRE book (§5.3)
- Metric cardinality guard (10K max per metric name)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K06-1 | CRITICAL | §3 | **No RLS** on `slo_definitions` or `alert_rules` — any tenant can see/modify all SLOs |
| K06-2 | CRITICAL | §3 | **No `tenant_id`** on any table — multi-tenant observability not addressed |
| K06-3 | HIGH | §2 | **No gRPC service definition** — only LLD in the suite missing gRPC |
| K06-4 | HIGH | All | **Thinnest LLD** at 566 lines — other kernel LLDs average ~900+ lines |
| K06-5 | MEDIUM | §6.2 | "500K events/sec logs" — no log pipeline architecture (ELK scaling, Kafka buffering) |
| K06-6 | MEDIUM | §9 | No T1 config pack for jurisdiction-specific SLO thresholds |
| K06-7 | LOW | §10 | Test plan has no chaos tests (only unit and integration) |

**Missing Items Blocking Implementation**:
- Add `tenant_id` + RLS to all tables
- Add gRPC service definition
- Detail log pipeline scaling architecture for 500K events/sec
- Add chaos test scenarios (ELK down, Prometheus OOM, etc.)

---

### K-07 Audit Framework

**Implementation Readiness: 7 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 8 | REST + gRPC + SDK; log/search/verify/export; async export support |
| Data Model | 6 | Hash chain well-designed; **no RLS on `audit_logs`** (critical — audit data is per-tenant) |
| NFR Alignment | 6 | log() 30ms P99, 50K TPS — reasonable; **storage estimate of 63 PB over 10yr needs re-validation** |
| Security | 8 | Immutability triggers, access control (admin/auditor roles), encryption at rest |
| Test Plan | 8 | Performance tests, security tests (tamper detection), chain verification tests |
| Cross-Module | 8 | Referenced by almost all other LLDs for audit logging |
| T1/T2/T3 | 6 | Compliance report templates configurable; no T1 pack example |
| Real-World Gaps | 6 | GDPR right-to-erasure flagged as open question; no hash chain recovery procedure after corruption |

**Key Strengths**:
- SHA-256 hash chain per tenant with genesis hash (§5.1) — strong immutability guarantee
- Immutability enforced at PostgreSQL trigger level (§7.1) — defense in depth
- Evidence export to CSV/JSON/PDF for regulatory compliance (§5.3)
- Per-tenant sequence numbering separate from K-05 sequence (§5.1)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K07-1 | CRITICAL | §3 | **No RLS on `audit_logs`** — tenant A could read tenant B's audit trail |
| K07-2 | HIGH | §6.3 | Storage estimate: 8.64B events/day × 2KB = 17.28 TB/day → **63 PB over 10yr** — unrealistic without partitioning/archival detail |
| K07-3 | HIGH | §11 | **GDPR right-to-erasure** vs. immutable audit logs — needs pseudonymization strategy |
| K07-4 | MEDIUM | §5.1 | Hash chain recovery/repair procedure not defined if corruption detected |
| K07-5 | LOW | §10 | No retention policy enforcement test (test the CRON-based archival job) |

**Missing Items Blocking Implementation**:
- Add RLS to `audit_logs`, `audit_retention_policies`, `audit_exports`
- GDPR pseudonymization strategy for immutable audit records
- Hash chain repair/re-anchor procedure

---

### K-09 AI Governance

**Implementation Readiness: 7 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 9 | REST + gRPC + SDK; register/predict/override/drift/rollback/prompt; A/B testing |
| Data Model | 7 | 4 tables; `ai_decisions` has tenant_id + indexes; but **no RLS on any table** |
| NFR Alignment | 7 | predict() 500ms P99 without explanation, 2s with — reasonable for ML inference |
| Security | 8 | Model artifact encryption, HITL authorization, model poisoning detection |
| Test Plan | 8 | Explainability tests, drift detection, security tests — good variety |
| Cross-Module | 7 | Depends on K-02, K-05, K-07; events K-05 compliant |
| T1/T2/T3 | 6 | Custom explainability via plugin interface, but no T1/T2 pack examples |
| Real-World Gaps | 6 | Jinja2 prompt rendering is a security risk (template injection); no model serving infrastructure detail |

**Key Strengths**:
- SHAP + LIME explainability with pluggable providers (§5.1)
- PSI-based drift detection with clear thresholds (§5.2)
- HITL override mechanism with evidence capture (§4.3)
- Model poisoning detection via z-score anomaly detection (§7.3)
- A/B testing framework for model comparison (§9.2)
- Fairness metrics (demographic parity, equal opportunity) (§9.3)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K09-1 | HIGH | §3.2 | **No RLS on `ai_models`, `ai_decisions`, `ai_prompts`, `model_drift_metrics`** |
| K09-2 | HIGH | §5.4 | Jinja2 `Template(template).render(**variables)` — **template injection risk** if user-supplied variables contain Jinja2 syntax |
| K09-3 | MEDIUM | §1.3 | Depends on "MLflow/Model Registry" as external — no fallback if MLflow is unavailable |
| K09-4 | MEDIUM | §6.2 | predict() 1K TPS target — significantly lower than other modules; may be a bottleneck for high-frequency screening |
| K09-5 | LOW | §11 | 5 open assumptions (stateless models, post-hoc explainability, artifact size <1GB, etc.) |

**Missing Items Blocking Implementation**:
- Add RLS to all tables (at minimum `ai_decisions` and `ai_prompts`)
- Sanitize Jinja2 template rendering (use `SandboxedEnvironment`)
- MLflow unavailability fallback strategy

---

### K-15 Dual-Calendar

**Implementation Readiness: 9 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 9 | REST + gRPC + SDK; convert/dual-date/business-days/holidays/fiscal-year; batch support |
| Data Model | 8 | In-memory lookup + SQL tables for holidays & fiscal config; no RLS but tables are reference data |
| NFR Alignment | 10 | **Sub-millisecond P99** (0.1ms for conversion); 10M/sec in-process — extremely well-budgeted |
| Security | 8 | Signed lookup tables, maker-checker for holiday updates, air-gap support |
| Test Plan | 9 | 12 unit tests, 6 integration, 4 performance, 3 chaos — excellent coverage |
| Cross-Module | 9 | Used by all 15 other LLDs; zero dependencies beyond K-02 and K-05 |
| T1/T2/T3 | 9 | Holiday calendar T1 packs per jurisdiction; T3 plugin for additional calendar systems (Hijri, Thai Solar) |
| Real-World Gaps | 8 | Nepal Patro as authoritative source clearly stated; CBDC T+0 settlement addressed |

**Key Strengths**:
- Pre-computed lookup table with O(1) JDN conversion (§5.2)
- Memory footprint: only ~50KB for 100 years of data (§6.3)
- Weekend configuration per jurisdiction (NP=Sat, IN=Sat+Sun, BD=Fri+Sat) (§5.3)
- Startup integrity checksum on lookup tables (§4.4)
- Extension interface for additional calendar systems (§9.1)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K15-1 | MEDIUM | §3.2 | `holiday_calendars` table has no RLS — holidays are reference data, but tenant-specific holidays (e.g., company holidays) would need isolation |
| K15-2 | LOW | §3.1 | Java code in a TypeScript/Node.js platform — implementation language should be TypeScript |
| K15-3 | LOW | §5.1 | JDN epoch anchor stated as 2000-01-01 BS = 1943-04-14 AD — needs verification against Nepal Patro |

**Missing Items Blocking Implementation**:
- None critical; verify epoch anchor with authoritative source

---

### K-16 Ledger Framework

**Implementation Readiness: 8 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 9 | REST + gRPC + SDK; journal posting, balance query, statement, reconciliation, integrity check |
| Data Model | 9 | 4 core tables with DDL; `REVOKE UPDATE, DELETE` on journal_entries; RLS on accounts + journal_entries; NUMERIC(18,6) precision |
| NFR Alignment | 8 | Journal posting 30ms P99 (2-entry), balance query 10ms P99 (snapshot hit) — realistic |
| Security | 9 | Immutability at SQL level (REVOKE + trigger), RLS, segregation of duties |
| Test Plan | 8 | Unit, integration, chaos; append-only enforcement tests; integrity check tests |
| Cross-Module | 8 | Depends on K-05, K-07, K-15, K-17; well-integrated |
| T1/T2/T3 | 8 | Chart of accounts via T1; reconciliation adapters via T3; multi-currency/CBDC support |
| Real-World Gaps | 7 | No explicit partitioning strategy for journal_entries (2B entries over 10yr); no dead-letter handling for failed journal postings |

**Key Strengths**:
- Double-entry validation with tolerance for precision (§5.1)
- Balance snapshots for O(1) historical balance queries (§4.2)
- Nightly integrity verification (full recalculation vs. snapshot) (§5.2)
- Break escalation tiers matching D-13 (§5.3)
- Multi-currency + digital asset support (NPR, USD, BTC, NPR_CBDC) (§9.3)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K16-1 | HIGH | §3.1 | No table partitioning for `journal_entries` — 2B entries over 10yr will degrade query performance |
| K16-2 | MEDIUM | §3.1 | `balance_snapshots` has no RLS — though journal_entries does, snapshots should also be isolated |
| K16-3 | MEDIUM | §4.1 | No mention of dead-letter handling if EventBus.publish(JournalPostedEvent) fails after COMMIT |
| K16-4 | LOW | §6.3 | Storage estimate of 200GB for 2B entries — seems low (~100 bytes/entry); verify |

**Missing Items Blocking Implementation**:
- Add table partitioning strategy (by tenant + date range)
- RLS on `balance_snapshots` and `reconciliation_runs`

---

### K-17 Distributed Transaction Coordinator

**Implementation Readiness: 7 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 8 | REST + gRPC + SDK; saga start/status/compensate; outbox flush; idempotent execution |
| Data Model | 8 | Outbox, saga_definitions, saga_instances, idempotency_keys, version_vectors — comprehensive |
| NFR Alignment | 7 | Outbox 5ms P99, saga start 30ms P99, synchronous (T+0) 100ms P99 — reasonable |
| Security | 7 | AES-256 encryption at rest; RLS mentioned but **no DDL shown** |
| Test Plan | 7 | Good chaos tests (DB crash mid-saga, concurrent saga conflict) |
| Cross-Module | 8 | Central to K-16 (ledger postings), D-01 (trade settlement) |
| T1/T2/T3 | 7 | Custom saga definitions via T1; DvP cross-chain for digital assets |
| Real-World Gaps | 6 | No saga step retry with compensation delay; outbox polling at 100ms may lag; no partition tolerance for split-brain |

**Key Strengths**:
- Transactional outbox pattern guaranteeing at-least-once delivery (§4.1)
- Version vectors for cross-stream causal ordering (§5.2)
- Synchronous mode for T+0 atomic settlement (§4.4)
- Saga definition DSL with step-level compensation (§5.3)
- 3 saga execution modes: ORCHESTRATED, CHOREOGRAPHED, SYNCHRONOUS (§2.2)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K17-1 | HIGH | §7.3 | "RLS on saga_instances and outbox tables" — **no DDL shown for RLS policies** |
| K17-2 | MEDIUM | §4.1 | Outbox polling every 100ms with `FOR UPDATE SKIP LOCKED` — at 50K events/sec, SELECT LIMIT 100 may fall behind |
| K17-3 | MEDIUM | §3.2 | `saga_instances.version` for optimistic locking — no explicit index for concurrent step updates |
| K17-4 | LOW | §4.3 | Timeout watchdog runs every 10s — sagas could overshoot timeout by up to 10s |

**Missing Items Blocking Implementation**:
- Show explicit RLS DDL for outbox and saga_instances
- Address outbox throughput at scale (partitioned outbox or parallel pollers?)

---

### K-18 Resilience Patterns

**Implementation Readiness: 7 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 7 | SDK builder + decorator APIs in TypeScript and Java; REST management API for circuit breakers |
| Data Model | 7 | In-memory state model well-specified; K-02 config for profile storage |
| NFR Alignment | 9 | Full pipeline overhead 0.05ms P99 — negligible; memory <1MB per service |
| Security | 6 | Admin API for CB reset; but no audit trail for manual state overrides |
| Test Plan | 8 | Good chaos tests (50% failure, load spike, latency injection) |
| Cross-Module | 8 | Events emitted to K-05; metrics to K-06; profiles from K-02 |
| T1/T2/T3 | 8 | Custom profiles via T1 (e.g., SEBON_REPORTING); custom failure classifiers via T3 |
| Real-World Gaps | 6 | No distributed CB coordination (each instance has local state — state divergence) |

**Key Strengths**:
- 5 pre-defined profiles (CRITICAL_PATH, BEST_EFFORT, COMPLIANCE_SENSITIVE, REAL_TIME_DATA, SETTLEMENT) (§2.3)
- Composable pipeline: Timeout → Bulkhead → Retry → Circuit Breaker (§4.1)
- Cascading timeout validation at build time (§4.3)
- Both TypeScript and Java SDK implementations shown

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K18-1 | MEDIUM | §3.1 | Circuit breaker state is local to each service instance — no distributed CB state synchronization; could lead to uneven traffic during half-open probes |
| K18-2 | MEDIUM | §7 | No K-07 audit for manual circuit breaker reset — privileged operation should be audited |
| K18-3 | LOW | §2.3 | CRITICAL_PATH profile has 1 retry — may be too aggressive for a capital markets order path |
| K18-4 | LOW | §4.3 | Cascading timeout example shows "12000ms" for API Gateway but INDEX says ≤12ms P99 — conflicting units (ms vs seconds?) |

**Missing Items Blocking Implementation**:
- Clarify whether distributed CB synchronization is needed across replicas
- Audit manual CB resets via K-07

---

### K-19 DLQ Management

**Implementation Readiness: 8 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 9 | REST + gRPC + SDK; single/bulk replay; dry-run support; dashboard summary; RCA enforcement |
| Data Model | 9 | 3 tables with DDL; **RLS present** on `dlq_events`; failure history + replay audit trail |
| NFR Alignment | 8 | Ingestion 30ms P99, single replay 50ms P99, 10yr retention |
| Security | 8 | Granular permissions (view/rca/replay/bulk/bypass/discard); maker-checker for discard |
| Test Plan | 8 | Poison pill flood test, mid-failure bulk replay, tenant isolation |
| Cross-Module | 8 | Integrates with K-05, K-07, K-17; auto-retry rules bridge to K-05 |
| T1/T2/T3 | 8 | Auto-retry rules via T1; custom DLQ handlers via T3 |
| Real-World Gaps | 7 | RCA enforcement is excellent; but no time-based auto-escalation for unresolved DLQ events |

**Key Strengths**:
- RCA requirement before replay — prevents "just retry it" anti-pattern (§4.2)
- Poison pill detection with configurable thresholds (§5.1)
- Dry-run for bulk replay with idempotency conflict detection (§4.3)
- Auto-retry rules for transient failures (§5.2)
- Comprehensive dashboard summary endpoint (§2.1)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| K19-1 | MEDIUM | §5.2 | Auto-retry `rca_auto_tag` bypasses human RCA — may conflict with audit requirements |
| K19-2 | LOW | §3.1 | `dlq_failure_history` and `dlq_replay_history` have no explicit RLS (only `dlq_events` does) |
| K19-3 | LOW | §5.3 | Replay rate limiter uses `time.sleep()` — blocking in async context; should use async delay |

**Missing Items Blocking Implementation**:
- Add RLS to `dlq_failure_history` and `dlq_replay_history`
- Clarify whether auto-retry satisfies audit/compliance requirements

---

### D-01 OMS

**Implementation Readiness: 7 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 8 | REST + gRPC + SDK; place/cancel/amend/list; pre-trade validation |
| Data Model | 5 | **No RLS** on `orders`, `executions`, `positions`; **no `tenant_id`** on `orders` table |
| NFR Alignment | 9 | processOrder 2ms P99, placeOrder 12ms end-to-end with budget decomposition (OMS 2ms + K-03 5ms + K-05 2ms + overhead 3ms) — excellent |
| Security | 6 | Maker-checker for order amendments; but no RLS, no encryption at rest mentioned |
| Test Plan | 7 | State machine tests, pre-trade validation, position calculation |
| Cross-Module | 6 | **D-14 references `COMPLIANCE_HOLD` order status** — not in D-01 state machine (§4.1) |
| T1/T2/T3 | 7 | Pre-trade validation rules via T2 (K-03); instrument config via T1 |
| Real-World Gaps | 6 | No partial fill handling detail; no FIX protocol mention for exchange connectivity |

**Key Strengths**:
- 9-state order state machine well-defined (§4.1)
- Latency budget decomposition across modules (§6.1)
- Position calculator with weighted average cost (§5.2)
- Pre-trade validation delegated to K-03 (separation of concerns)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| D01-1 | CRITICAL | §3 | **No RLS, no `tenant_id`** on `orders`, `executions`, `positions` tables |
| D01-2 | HIGH | §4.1 | **No `COMPLIANCE_HOLD` status** in state machine — D-14 sanctions screening needs this state to hold suspicious orders |
| D01-3 | HIGH | §3 | No `tenant_id` column on `orders` table — multi-tenant broken at data level |
| D01-4 | MEDIUM | §4.1 | Partial fill state transition not detailed (PENDING_EXECUTION → ? when 50% filled) |
| D01-5 | LOW | §2 | No FIX protocol adapter for exchange connectivity |

**Missing Items Blocking Implementation**:
- Add `tenant_id` + RLS to all tables
- Add `COMPLIANCE_HOLD` state to order state machine
- Clarify partial fill handling

---

### D-13 Client Money Reconciliation

**Implementation Readiness: 8 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 8 | REST + gRPC + SDK; run/status/breaks/segregation endpoints |
| Data Model | 9 | 4 tables with RLS and dual-calendar columns; break severity classification |
| NFR Alignment | 8 | Full recon 3-15min for 12.5K accounts, 500K max — realistic |
| Security | 8 | RLS on all tables; segregation formula protects client assets |
| Test Plan | 8 | Regulatory scenario tests included; chaos tests for source unavailability |
| Cross-Module | 8 | Depends on K-05, K-06, K-07, K-15, K-16, K-18, D-09 — proper integration |
| T1/T2/T3 | 7 | Break threshold configs via T1; external source adapters could use T3 |
| Real-World Gaps | 7 | No intraday recon trigger (only daily); no pre-market recon for assurance |

**Key Strengths**:
- Break severity classification (INFO/WARNING/CRITICAL) with aging escalation (3/5/10 business days) (§5.2)
- Multi-source reconciliation (bank, CDSC, custodian, ledger) (§4.1)
- Segregation ratio calculation for client money protection (§5.3)
- Comprehensive regulatory scenario tests

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| D13-1 | MEDIUM | §4 | Only daily scheduled reconciliation — no intraday or event-triggered recon |
| D13-2 | MEDIUM | §1.3 | References D-09 (Post-Trade) and R-02 — neither has an LLD in this suite |
| D13-3 | LOW | §6.1 | 500K max accounts — adequate for Nepal but may need scaling for multi-jurisdiction |

**Missing Items Blocking Implementation**:
- None critical

---

### D-14 Sanctions Screening

**Implementation Readiness: 8 / 10**

| Criterion | Score | Notes |
|-----------|-------|-------|
| API Completeness | 9 | REST + gRPC + SDK; screen/whitelist/update-lists; real-time + batch modes |
| Data Model | 9 | 4 tables with RLS on screening/match tables; GIN indexes for FTS/phonetic search |
| NFR Alignment | 9 | P99 <50ms real-time, 10K screenings/sec, 5M entries, <0.01% false negative — ambitious but well-architected |
| Security | 9 | Air-gap Ed25519-signed bundles; encryption at rest; audit trail for match reviews |
| Test Plan | 8 | 5 accuracy test cases (exact, fuzzy, phonetic, PEP, whitelist); performance tests |
| Cross-Module | 7 | References D-01 `COMPLIANCE_HOLD` status — which D-01 doesn't define (§4.3) |
| T1/T2/T3 | 8 | Sanctions list bundles for air-gap; screening rule configuration via T1 |
| Real-World Gaps | 7 | BK-tree requires pre-built index — no index rebuild procedure documented |

**Key Strengths**:
- Composite fuzzy matching: Levenshtein 0.4 + Jaro-Winkler 0.4 + Soundex 0.1 + Double Metaphone 0.1 (§5.1)
- BK-tree for efficient fuzzy search at scale (§5.2)
- Whitelist cache with 24h TTL and justification tracking (§5.3)
- Air-gap bundle support with Ed25519 signatures (§7.3)
- Comprehensive error model (SAN_E001–E007)

**Specific Issues**:
| # | Severity | Section | Issue |
|---|----------|---------|-------|
| D14-1 | HIGH | §4.3 | References D-01 `COMPLIANCE_HOLD` order status — **not defined in D-01 state machine** |
| D14-2 | MEDIUM | §5.2 | BK-tree index rebuild procedure not documented — what happens when sanctions list is updated? |
| D14-3 | LOW | §6.1 | <0.01% false negative target — no validation test explicitly measures false negative rate |

**Missing Items Blocking Implementation**:
- D-01 must add `COMPLIANCE_HOLD` status (or D-14 must work around it)
- Document BK-tree rebuild procedure on list update

---

## 4. Cross-Cutting Issues

### 4.1 RLS & Tenant Isolation Gaps

**Severity: CRITICAL** — This is the #1 systemic risk across the LLD suite.

| LLD | Tables Missing RLS | Tables Missing `tenant_id` |
|-----|-------------------|---------------------------|
| K-03 Rules Engine | `rule_packs`, `policy_decisions_audit` | `rule_packs` |
| K-04 Plugin Runtime | `plugins`, `plugin_invocations` | `plugins` |
| K-06 Observability | `slo_definitions`, `alert_rules` | All tables |
| K-07 Audit Framework | `audit_logs`, `audit_retention_policies`, `audit_exports` | None (uses tenant_id from event) |
| K-09 AI Governance | `ai_models`, `ai_decisions`, `ai_prompts`, `model_drift_metrics` | `ai_models`, `ai_prompts`, `model_drift_metrics` |
| D-01 OMS | `orders`, `executions`, `positions` | `orders` |

**Total**: 17 tables across 6 LLDs missing RLS; 9 columns missing `tenant_id`.

**Recommendation**: Establish a **mandatory checklist** — every table with application data MUST have:
1. `tenant_id UUID NOT NULL` column
2. RLS policy `USING (tenant_id = current_setting('app.current_tenant')::uuid)`
3. Index on `tenant_id` (or included in composite primary key)

### 4.2 Cross-LLD State Reference Conflict

| Source LLD | Target LLD | Issue |
|-----------|------------|-------|
| D-14 §4.3 | D-01 §4.1 | D-14 references `COMPLIANCE_HOLD` order status; D-01 state machine has no such state |
| K-01 §1.3 | K-14 (phantom) | K-01 depends on K-14 Secrets Management — no such LLD exists |
| K-05 §5.2 | K-19 | Saga compensation failure in K-05 only logs error; K-17 §4.2 properly escalates to K-19 |
| D-13 §1.3 | D-09, R-02 | D-13 depends on D-09 (Post-Trade) and R-02 (Regulatory Reporting) — neither has an LLD |

### 4.3 Event Envelope Compliance

All 16 LLDs reference the K-05 standard event envelope. Compliance is **strong**:
- ✅ `event_id`, `event_type`, `event_version`, `aggregate_id`, `aggregate_type`, `sequence_number` consistently present
- ✅ `timestamp_bs` and `timestamp_gregorian` dual timestamps consistently used
- ✅ `metadata.tenant_id`, `metadata.trace_id`, `metadata.causation_id`, `metadata.correlation_id` present
- ⚠️ K-18 uses a slightly different event type naming convention (`siddhanta.resilience.circuit_breaker.state_changed` with dots instead of camelCase used elsewhere)

### 4.4 Language Consistency

The platform declares TypeScript/Node.js as the primary backend language. However:
- K-15 shows **Java** implementations for JDN conversion (§5.1, §5.2) and thread pool bulkhead
- K-18 shows **Java** SDK alongside TypeScript
- K-07, K-09, D-14 show **Python** for algorithm implementations

**Recommendation**: Clarify language strategy — "TypeScript/Node.js for services, Python for ML, Java pseudo-code for algorithms" or explicitly support polyglot runtime.

---

## 5. Issue Catalogue by Severity

### CRITICAL (8 issues — must fix before implementation)

| # | LLD | Issue |
|---|-----|-------|
| 1 | K-03 | No RLS or tenant_id on `rule_packs` — multi-tenant isolation broken |
| 2 | K-06 | No RLS or tenant_id on any table — observability data exposed cross-tenant |
| 3 | K-07 | No RLS on `audit_logs` — audit data (most sensitive) exposed cross-tenant |
| 4 | D-01 | No RLS or tenant_id on `orders`/`executions`/`positions` — order data exposed |
| 5 | K-01 | Phantom dependency on K-14 Secrets Management — no LLD exists |
| 6 | K-04 | No RLS on `plugins`/`plugin_invocations` — scoping model undefined |
| 7 | K-09 | No RLS on 4 AI tables — AI decision data exposed cross-tenant |
| 8 | D-01/D-14 | `COMPLIANCE_HOLD` order status cross-reference conflict |

### HIGH (12 issues)

| # | LLD | Issue |
|---|-----|-------|
| 1 | K-05 | RLS on JSONB metadata field — performance risk at 100K TPS without functional index |
| 2 | K-05 | `processed_events` table DDL missing |
| 3 | K-06 | No gRPC service definition (only LLD missing this) |
| 4 | K-06 | Thinnest LLD (566 lines) — underspecified relative to its importance |
| 5 | K-07 | 63 PB 10-year storage estimate — unrealistic / needs partitioning plan |
| 6 | K-07 | GDPR right-to-erasure vs. immutable audit logs — unresolved |
| 7 | K-09 | Jinja2 template injection risk in prompt rendering |
| 8 | K-16 | No partitioning strategy for `journal_entries` (2B records over 10yr) |
| 9 | K-17 | RLS mentioned but no DDL shown for outbox/saga tables |
| 10 | D-01 | No JWT revocation mechanism in K-01 |
| 11 | D-01 | Partial fill state transition undefined |
| 12 | D-14 | BK-tree index rebuild on sanctions list update not documented |

### MEDIUM (16 issues)

| # | LLD | Issue Summary |
|---|-----|--------------|
| 1 | K-02 | 1:1 tenant-to-jurisdiction assumption |
| 2 | K-03 | No policy version conflict resolution |
| 3 | K-04 | No plugin dependency conflict detection |
| 4 | K-05 | Saga compensation failure should escalate to K-19 |
| 5 | K-06 | Log pipeline scaling architecture missing |
| 6 | K-06 | No T1 pack for SLO thresholds |
| 7 | K-07 | Hash chain recovery procedure missing |
| 8 | K-09 | MLflow unavailability fallback missing |
| 9 | K-09 | predict() 1K TPS may bottleneck |
| 10 | K-15 | Holiday table has no RLS for tenant-specific holidays |
| 11 | K-16 | `balance_snapshots` no RLS |
| 12 | K-16 | No dead-letter handling for failed event publish after journal COMMIT |
| 13 | K-17 | Outbox polling may fall behind at 50K events/sec |
| 14 | K-18 | Local-only circuit breaker state — no distributed synchronization |
| 15 | K-18 | No K-07 audit for manual CB reset |
| 16 | D-13 | No intraday reconciliation trigger |

### LOW (12 issues)

| # | LLD | Issue Summary |
|---|-----|--------------|
| 1 | K-01 | Token validation uncached latency not stated |
| 2 | K-02 | `resolved_config_cache` refresh frequency unspecified |
| 3 | K-03 | No bulk evaluation endpoint |
| 4 | K-04 | T3 container cold-start latency unspecified |
| 5 | K-05 | `acks=1` data loss risk window undocumented |
| 6 | K-06 | No chaos tests in test plan |
| 7 | K-07 | No retention policy enforcement test |
| 8 | K-15 | Java code in TypeScript platform |
| 9 | K-16 | Storage estimate seems low |
| 10 | K-18 | CRITICAL_PATH 1-retry may be too aggressive |
| 11 | K-19 | `dlq_failure_history` / `dlq_replay_history` no RLS |
| 12 | D-14 | No false negative rate validation test |

---

## 6. NFR Alignment Analysis

### Latency Budget Alignment (Critical Path: placeOrder ≤12ms P99)

The INDEX (§NFR Summary Table) declares `D-01 placeOrder ≤12ms P99 end-to-end`. D-01 §6.1 decomposes this as:

| Component | Budget | Source LLD | LLD Target | Status |
|-----------|--------|------------|------------|--------|
| D-01 OMS processOrder | 2ms P99 | D-01 §6.1 | 2ms P99 | ✅ Aligned |
| K-03 Rules evaluate | 5ms P99 | D-01 §6.1 | 10ms P99 (cached) | ⚠️ K-03 targets 10ms but budget allocates 5ms |
| K-05 Event publish | 2ms P99 | D-01 §6.1 | 2ms P99 (critical-path) | ✅ Aligned |
| Overhead (network, auth) | 3ms | D-01 §6.1 | — | ✅ Reasonable |
| K-15 Calendar conversion | (within overhead) | — | 0.1ms P99 | ✅ Negligible |
| K-18 Resilience overhead | (within overhead) | — | 0.05ms P99 | ✅ Negligible |

**Gap**: K-03 targets 10ms P99 but the critical path budget only allows 5ms. Either K-03 must tighten to 5ms or D-01 must increase the allocation.

### Throughput Targets

| Module | Target TPS | Peak TPS | Consistency |
|--------|------------|----------|-------------|
| D-01 OMS | 50K | — | INDEX says 50K TPS ✅ |
| K-05 Event publish | 100K | — | Supports 2× headroom ✅ |
| K-03 Rules evaluate | 50K | — | Matches D-01 throughput ✅ |
| K-02 Config resolve | 20K | — | Below 50K order rate — may bottleneck if every order resolves config |
| K-07 Audit log | 50K | 100K | Matches D-01 throughput ✅ |
| K-09 AI predict | 1K | 5K | Significantly lower — ensure not in critical path |

### Storage / Availability

| Metric | Target | Support |
|--------|--------|---------|
| 99.999% availability (trading hours) | All LLDs | K-18 resilience patterns + K-17 compensation |
| 10-year data retention | K-07 (63PB concern), K-16, K-19 | Archive strategies defined but K-07 estimate needs re-validation |
| RPO = 0 | K-05 event store | acks=1 on critical topics introduces non-zero RPO — **conflict** |

---

## 7. Security Posture Summary

### Defense-in-Depth Assessment

| Layer | Implementation | Gaps |
|-------|---------------|------|
| **Authentication** | OAuth 2.0 + JWT (EdDSA/Ed25519) via K-01 | No token revocation mechanism |
| **Authorization** | RBAC with 3 scopes + OPA policies via K-03 | K-03 missing tenant isolation |
| **Row-Level Security** | K-02, K-05, K-16, K-19, D-13, D-14 ✅ | K-03, K-04, K-06, K-07, K-09, D-01 ❌ |
| **Encryption at Rest** | K-07, K-09, K-17 mention it | No consistent platform-wide encryption standard |
| **Encryption in Transit** | mTLS via Istio (Architecture Spec) | Not re-stated in individual LLDs |
| **Immutability** | K-07 triggers, K-16 REVOKE | Strong — dual enforcement (app + DB) |
| **Air-Gap Support** | K-02, K-14, D-14 — Ed25519 bundles | Consistent pattern |
| **Maker-Checker** | K-02, K-15, K-16 | Not consistently enforced across all admin operations |

### Recommendations
1. **Immediate**: Fix RLS gaps in 6 LLDs (17 tables)
2. **Pre-Production**: Establish JWT revocation strategy (short-lived tokens + refresh)
3. **Pre-Production**: Standardize encryption-at-rest across all modules (not just mentioned ones)

---

## 8. Implementation Roadmap Recommendations

Based on the review findings, the recommended implementation order (adjusted from INDEX §Implementation Roadmap):

### Phase 0: Foundation Fixes (Week 0-1)
- [ ] Fix RLS gaps in K-03, K-04, K-06, K-07, K-09, D-01 (17 tables)
- [ ] Add `COMPLIANCE_HOLD` state to D-01 order state machine
- [ ] Resolve K-14 phantom dependency in K-01
- [ ] Add `processed_events` DDL to K-05
- [ ] Add gRPC definition to K-06

### Phase 1: Kernel Layer (Weeks 1-8)
1. K-02 Configuration Engine (no dependencies — start first)
2. K-15 Dual-Calendar (depends on K-02)
3. K-05 Event Bus (depends on K-02)
4. K-07 Audit Framework (depends on K-05)
5. K-01 IAM (depends on K-02, K-05)
6. K-03 Rules Engine (depends on K-02, K-05)
7. K-04 Plugin Runtime (depends on K-02, K-03, K-05)

### Phase 2: Infrastructure Kernel (Weeks 5-12)
8. K-06 Observability (depends on K-02, K-05)
9. K-16 Ledger Framework (depends on K-05, K-07, K-15)
10. K-17 DTC (depends on K-05, K-19)
11. K-18 Resilience Patterns (depends on K-02, K-05, K-06)
12. K-19 DLQ Management (depends on K-05, K-07)

### Phase 3: Domain & AI (Weeks 9-16)
13. D-01 OMS (depends on K-01, K-03, K-05, K-18)
14. D-14 Sanctions Screening (depends on K-01, K-05, K-07)
15. D-13 Client Money Reconciliation (depends on K-05, K-07, K-15, K-16)
16. K-09 AI Governance (depends on K-02, K-05, K-07)

---

## 9. Overall Assessment

### Suite Maturity: **7.4 / 10 — Implementation Ready with Known Gaps**

| Dimension | Score | Commentary |
|-----------|-------|------------|
| **Architectural Consistency** | 8/10 | 10-section template, standard envelope, dual-calendar consistently adopted |
| **API Contract Quality** | 8/10 | REST + gRPC + SDK in 15/16 LLDs; consistent error models |
| **Data Model Rigor** | 6/10 | DDL present but **RLS gaps in 6 LLDs** — #1 systemic risk |
| **NFR Discipline** | 8/10 | Budgets decomposed with cross-LLD validation; one K-03/D-01 gap |
| **Security Posture** | 6/10 | Strong in some LLDs (K-16, D-14) but inconsistent across suite |
| **Test Coverage Design** | 7/10 | All LLDs have test plans; K-02 exemplary; K-06 sparse |
| **Extensibility** | 8/10 | T1/T2/T3 taxonomy consistently used; jurisdiction-neutral design |
| **Cross-Module Integration** | 7/10 | Event envelope strong; but 4 cross-reference conflicts identified |

### Go/No-Go Recommendation

**Conditional GO** — The LLD suite is implementation-ready provided:

1. **Must-Fix (Phase 0)**: RLS gaps in 6 LLDs, COMPLIANCE_HOLD state conflict, K-14 phantom dependency
2. **Must-Resolve**: K-07 storage estimate validation, K-05 JSONB RLS performance, K-06 expansion
3. **Must-Track**: 5 LLDs have open validation questions requiring stakeholder sign-off

The design is comprehensive, consistently structured, and demonstrates strong domain understanding for a capital markets platform. The gaps identified are **correctable without architectural changes** — they are completeness issues, not design flaws.

---

*Generated by Architecture Review Board — March 2026*
*Total Issues: 48 (8 Critical, 12 High, 16 Medium, 12 Low)*
