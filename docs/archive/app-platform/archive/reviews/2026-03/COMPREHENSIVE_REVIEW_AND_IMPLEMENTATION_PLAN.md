# COMPREHENSIVE REVIEW & IMPLEMENTATION PLAN
## Project Siddhanta — All-In-One Capital Markets Platform

**Version:** 1.0  
**Date:** March 2026  
**Scope:** Full document suite review (60+ files) + phased implementation plan  
**Status:** Final

**Historical Note (March 8, 2026):** This document captures the pre-hardening review backlog and initial implementation sequencing. Many documentation remediation items listed below were later closed in subsequent hardening passes. Use this document primarily for phase sequencing and team planning, not as the current defect ledger.

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [Document Suite Health Scorecard](#2-document-suite-health-scorecard)
3. [Cross-Document Consistency Findings](#3-cross-document-consistency-findings)
4. [Architecture Review: Strengths](#4-architecture-review-strengths)
5. [Architecture Review: Gaps & Risks](#5-architecture-review-gaps--risks)
6. [Extensibility & Plugin Model Assessment](#6-extensibility--plugin-model-assessment)
7. [Real-World Problem Coverage](#7-real-world-problem-coverage)
8. [Future-Proofing Assessment](#8-future-proofing-assessment)
9. [Prioritized Remediation Backlog](#9-prioritized-remediation-backlog)
10. [Implementation Plan](#10-implementation-plan)

---

## 1. EXECUTIVE SUMMARY

### Overview

A comprehensive review of **60+ documents** spanning architecture specs (6 files, ~8,000 lines), C4 diagrams (5 files), LLDs (16 files), epics (42 files), and supporting documentation (12+ files) was conducted. The platform design is **architecturally sound, well-structured, and addresses real-world capital markets problems** for Nepal (SEBON/NRB/NEPSE) as the first instantiation with a jurisdiction-neutral core.

### Overall Grade: **B+ (85/100)**

| Dimension | Score | Verdict |
|-----------|:-----:|---------|
| Consistency | 78/100 | NFR mismatches in §1.6; legacy Platform SDK ID drift; dependency drift |
| Expressiveness | 90/100 | Rich code samples; clear bounded contexts; well-documented invariants |
| Extensibility | 88/100 | T1/T2/T3 taxonomy is strong; plugin manifest missing `tier` field |
| Scalability | 92/100 | Good NFR targets; caching strategy; async processing; HPA |
| Real-World Fit | 94/100 | Excellent Nepal/India market coverage; regulatory-grounded |
| Future-Proofing | 72/100 | CBDC/ESG/PQC/T+0 promised but zero content authored |

### Key Findings Summary

- **14 Critical Issues** requiring immediate fix before development begins
- **22 Major Issues** requiring fix in Sprint 0
- **18 Medium Issues** to be addressed in first 2 sprints
- **The architecture is implementation-ready** once document remediation is complete

---

## 2. DOCUMENT SUITE HEALTH SCORECARD

### By Document Category

| Category | Files | Completeness | Consistency | Quality |
|----------|:-----:|:------------:|:-----------:|:-------:|
| Architecture Specs (§1-15) | 6 | 95% | 78% | 90% |
| C4 Diagrams (C1-C4 + Index) | 5 | 75% | 80% | 85% |
| LLDs (16 modules) | 16 | 88% | 82% | 85% |
| Kernel Epics (K-01 to K-19) | 19 | 92% | 76% | 88% |
| Domain Epics (D-01 to D-14) | 14 | 90% | 80% | 86% |
| Other Epics (W/P/T/O/R/PU) | 9 | 88% | 72% | 84% |
| Supporting Docs | 12+ | 85% | 70% | 82% |

### By Invariant Compliance

| Invariant | Architecture | C4 | LLDs | Epics | Overall |
|-----------|:-----------:|:--:|:----:|:-----:|:-------:|
| NFR Values (≤2ms/12ms, 99.999%, 50K/100K) | ❌ §1.6 stale | ❌ C1 §5.6 stale | ⚠️ 3/16 explicit | ⚠️ K-06 is 99.99% | **PARTIAL** |
| Dual-Calendar (BS+Gregorian) | ⚠️ Event schemas lack BS | ❌ C4 entities lack `_bs` | ⚠️ 31 missing `_bs` cols | ✅ All applicable | **PARTIAL** |
| K-05 Event Envelope | ❌ §2-3 non-standard | ❌ C4 lacks envelope fields | ⚠️ D-01/K-02 deviate | ⚠️ Only K-04 declares | **FAIL** |
| T1/T2/T3 Taxonomy | ✅ §5.2 canonical | ⚠️ Not in C4 | ✅ Where applicable | ✅ Where applicable | **PASS** |
| Zero-Trust Security (7 layers) | ✅ §9.2 | ✅ | ✅ | ✅ | **PASS** |
| 10yr Data Retention | ✅ §7.3 clarification | — | ⚠️ Not explicit in all | ✅ All state 10yr | **PASS** |

---

## 3. CROSS-DOCUMENT CONSISTENCY FINDINGS

### 3.1 Critical Inconsistencies

| # | Issue | Files Affected | Impact |
|---|-------|---------------|--------|
| **C-01** | **NFR values in §1.6 are stale**: Order latency "< 1ms" (should be ≤2ms/≤12ms), Availability "99.99%" (should be 99.999%), Throughput missing "50K sustained" | ARCHITECTURE_SPEC_PART_1_SECTIONS_1-3.md, ARCHITECTURE_AND_DESIGN_SPECIFICATION.md (KPI section) | Developers may target wrong SLAs |
| **C-02** | **Legacy Platform SDK ID drift**: 10+ files referenced a non-existent Platform SDK epic ID even though the Platform SDK is K-12 | K-15, K-16, K-17, K-18, K-19, DEPENDENCY_MATRIX.md | Broken dependency graph |
| **C-03** | **K-05 event envelope universally violated**: Architecture §2-3 code samples, C4 `OrderEvent` class, D-01/K-02 LLDs all use non-standard event structures lacking `timestamp_bs`, `tenant_id`, `causation_id` etc. | 8+ files | Event contract confusion during implementation |
| **C-04** | **C1 order latency says `< 100ms`**: Pre-hardening vestige, should be ≤2ms/≤12ms | C4_C1_CONTEXT_SIDDHANTA.md §5.6 | Incorrect C1 performance claim |
| **C-05** | **C2 internal NFR contradiction**: Item 1 says "P99 ≤ 12ms (internal)" but item 2 correctly says "≤ 2ms internal" | C4_C2_CONTAINER_SIDDHANTA.md §5.5 | Self-contradicting spec |
| **C-06** | **C4 entities lack dual-calendar**: `Order` and `Position` Java classes have no `createdAtBs`/`updatedAtBs` fields | C4_C4_CODE_SIDDHANTA.md | Code template violates invariant |
| **C-07** | **31 missing `_bs` columns across 9 LLD modules** | D-01, K-02, K-05, K-09 (worst offenders) | DB schema violates dual-calendar mandate |
| **C-08** | **`TradeExecuted` event source misattributed**: D-03, D-08, D-09, D-10 say source is "D-01 OMS" but it's actually emitted by D-02 EMS | 4 domain epics | Incorrect event lineage |
| **C-09** | **JWT uses HS256 (symmetric)** in security architecture code | ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md §9.3 | Violates zero-trust (needs RS256/ES256) |
| **C-10** | **`Math.random()` for security backup codes** | ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md §9.4 | Cryptographically insecure |
| **C-11** | **Jurisdiction logic hardcoded in compliance code**: `formatReport()` has `if (regulator === 'SEBI')` | ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md §12.4 | Violates jurisdiction-neutrality principle |
| **C-12** | **LLD_INDEX tech stack omits Java/Spring** — says "TypeScript (Node.js)" as backend | LLD_INDEX.md §6.1 | Contradicts C2/C3/C4 which show Java as primary backend |
| **C-13** | **Section 13/14 numbering**: All LLDs place extensibility in §9 instead of §13/§14 | All 16 LLDs | Structural inconsistency with epic template |
| **C-14** | **D-13 and D-14 missing Threat Model (§14.5)** despite handling client money and sanctions | EPIC-D-13, EPIC-D-14 | Security-critical modules unmodeled |

### 3.2 Major Inconsistencies

| # | Issue | Files Affected |
|---|-------|---------------|
| **M-01** | C2 diagram missing 8 of 13 external systems from C1 (Clearing House, SEBON, Tax Authority, etc.) | C4_C2_CONTAINER_SIDDHANTA.md |
| **M-02** | C3 only covers 3 of ~10 services (OMS, PMS, CMS) — missing RMS, Identity, Ledger, Compliance, etc. | C4_C3_COMPONENT_SIDDHANTA.md |
| **M-03** | 8+ dependency mismatches between epic files and DEPENDENCY_MATRIX.md | K-13, K-19, R-01, R-02, W-01, W-02 |
| **M-04** | 7+ undefined compliance codes referenced by epics | COMPLIANCE_CODE_REGISTRY.md |
| **M-05** | EPIC_VERSIONING_STRATEGY.md stale — shows all v1.0.0, missing 7 ARB-created epics | EPIC_VERSIONING_STRATEGY.md |
| **M-06** | No epic references its corresponding LLD document (universal gap) | All 42 epics |
| **M-07** | 26 of 42 epics lack LLD documents with no documented rationale | LLD_INDEX.md |
| **M-08** | Plugin manifest JSON lacks `tier` field for T1/T2/T3 classification | ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md §5.4 |
| **M-09** | Plugin sandbox has no per-tier isolation differentiation | ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md §5.7 |
| **M-10** | MongoDB schemas (`corporate_actions`, `regulatory_filings`) lack BS companion fields | ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md §7.4 |
| **M-11** | All SQL DDL examples in C4 lack `*_bs` dual-calendar columns | C4_C4_CODE_SIDDHANTA.md |
| **M-12** | 12+ event-consumption dependencies not declared in epic Section 2 | Multiple domain epics |
| **M-13** | PU-004 header mixed the Secrets Management ID with the Platform Manifest ID | EPIC-PU-004 |
| **M-14** | README Epic Layers table has 3 factual errors (PU scope, Testing scope, Kernel omissions) | README.md |
| **M-15** | K-08, K-10, K-11 have no LLD documents at all | — |
| **M-16** | Missing Threat Models for 10+ security-sensitive epics | K-13, W-02, P-01, K-17, PU-004, O-01, R-02 |
| **M-17** | RLS (Row-Level Security) missing on multi-tenant tables in D-01, K-03, K-04, K-09 | 4 LLDs |
| **M-18** | Traceability matrix refs wrong epics: D-05→"Corporate-Actions" (actual: Pricing-Engine), D-08→"Security" (actual: Surveillance) | ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md §14.2 |
| **M-19** | Pack Index revision history date error: v2.1 dated 2025-06 appears before v2.0 dated 2026-03-03 | C4_DIAGRAM_PACK_INDEX.md |
| **M-20** | K-05 event envelope uses `causality_id`/`payload` in K-02 LLD vs `causation_id`/`data` in K-05 LLD | LLD_K02, LLD_K05 |
| **M-21** | Only 3 of 16 LLDs explicitly state the 99.999% availability SLO target | All LLDs |
| **M-22** | K-01 IAM references K-14 Secrets Manager which has no LLD document | LLD_K01_IAM.md |

---

## 4. ARCHITECTURE REVIEW: STRENGTHS

### 4.1 What the Architecture Gets Right

| Strength | Evidence |
|----------|---------|
| **Jurisdiction-neutral core** | No regulatory logic embedded in kernel — all via T1/T2/T3 packs. First proven with Nepal, extensible to India, Bangladesh, etc. |
| **T1/T2/T3 Content Pack Taxonomy** | Best-in-class extensibility model. T1 (Config-only: tax tables, calendars), T2 (Rules: OPA/Rego compliance), T3 (Executable: signed exchange adapters). Clear isolation, signing, and approval tiers. |
| **Dual-Calendar Mandate** | BS + Gregorian at data layer. Explicit invariant box in §7.3 with clear guidance on when BS companions are needed vs. query-time conversion. |
| **Event Sourcing + CQRS** | Full event sourcing with snapshot optimization, schema versioning (Avro), upcasting, idempotency handler. Production-grade code samples. |
| **7-Layer Architecture** | Clean separation: Presentation → API Gateway → Application → Domain → Integration → Persistence → Infrastructure. Each layer well-documented with code examples. |
| **Zero-Trust Security** | 7 security layers properly enumerated. RBAC with wildcard patterns and role inheritance. Encryption (AES-256-GCM). mTLS with Istio. |
| **Real-World Nepal Market Coverage** | NEPSE minimum lot sizes, SEBON margin rules, NRB capital adequacy, CDS&C settlement, Sun-Thu trading week, promoter lock-in, BS-dated NAV — all addressed in domain epics. |
| **Production-Grade Code Samples** | PostgreSQL with RLS, partitioning, covering indexes. Event store with snapshot optimization. Airflow DAGs. CI/CD with Trivy scanning. |
| **Comprehensive Epic Structure** | All 42 epics follow 14+ section template with NFRs, extension points, future-safe evaluation. Consistent formatting. |
| **ARB Stress Test Remediation** | 20 findings all resolved with traceability. New epics (K-17, K-18, K-19, D-13, D-14, R-02, T-02) created to fill gaps. |

### 4.2 Architectural Decision Quality

| Decision | Assessment |
|----------|-----------|
| **Microservices + DDD** | Correct for the scale (42 bounded contexts). Well-defined aggregate boundaries. |
| **Event Sourcing for financial data** | Excellent — provides full audit trail, temporal queries, replay capability. Critical for regulatory compliance. |
| **Polyglot Persistence** | PostgreSQL (OLTP), TimescaleDB (time-series), MongoDB (documents), Redis (cache), ClickHouse (analytics) — each chosen for the right workload. |
| **Kafka for Event Bus** | Right choice for throughput (50K/100K TPS), ordering guarantees, replay, and multi-consumer patterns. |
| **Kubernetes + Istio** | Standard for microservices orchestration. Supports multi-cloud and air-gapped deployment requirements. |
| **OPA/Rego for Rules Engine** | Declarative policy engine is the right fit for T2 compliance rules. Enables hot-swap without code deployment. |

---

## 5. ARCHITECTURE REVIEW: GAPS & RISKS

### 5.1 Architectural Gaps

| # | Gap | Severity | Impact |
|---|-----|----------|--------|
| **G-01** | **No dedicated API versioning strategy** in architecture specs (only §13 covers it briefly). No URL vs. header vs. content-type negotiation decision documented. | HIGH | Breaking changes during multi-client rollout |
| **G-02** | **No data migration architecture** for moving between schema versions during zero-downtime deployments. §13 covers DB migration briefly but no orchestration strategy. | HIGH | Risky schema migrations |
| **G-03** | **No multi-region architecture** documented despite "multi-cloud/air-gap" being a key decision. How does state replicate? Cross-region event bus topology? | HIGH | Deployment complexity |
| **G-04** | **No disaster recovery runbook** — §DR/BC mentions RTO <15min, RPO <1sec but no step-by-step recovery procedures. | MEDIUM | DR failure during incidents |
| **G-05** | **No capacity planning model** — NFR targets exist but no growth projections, right-sizing guidance, or scaling triggers beyond HPA rules. | MEDIUM | Over/under-provisioning |
| **G-06** | **No inter-service communication contract** — REST vs gRPC vs async decision not documented per service pair. C2 shows both but no decision matrix. | MEDIUM | Inconsistent communication patterns |
| **G-07** | **No data lake query patterns** — Airflow DAGs ingest data but no analytics query patterns, semantic layer, or BI integration documented. | LOW | Analytics team left without guidance |

### 5.2 Technical Debt Risks

| Risk | Probability | Impact | Mitigation |
|------|:-----------:|:------:|------------|
| Event envelope divergence during implementation | HIGH | HIGH | Fix C-03 before Sprint 1; publish envelope as shared library |
| NFR target confusion (stale vs. canonical) | HIGH | MEDIUM | Fix C-01 immediately; single source of truth in §11.2 |
| Plugin sandbox security (no per-tier isolation) | MEDIUM | HIGH | Implement tier-aware sandbox before T3 packs are loaded |
| MongoDB dual-calendar gaps | MEDIUM | MEDIUM | Add `_bs` fields to all MongoDB schemas in Sprint 0 |
| Missing LLDs for 26 epics | HIGH | MEDIUM | Prioritize LLDs for Phase 1-2 epics; others can be Sprint 0 of their phase |

---

## 6. EXTENSIBILITY & PLUGIN MODEL ASSESSMENT

### 6.1 What Works

The **T1/T2/T3 Content Pack Taxonomy** (§5.2) is the platform's strongest extensibility feature:

| Tier | Type | Isolation | Examples | Assessment |
|------|------|-----------|----------|------------|
| T1 | Config (data-only) | In-process | Tax tables, calendars, holidays, fee schedules | ✅ Well-defined; schema-validated, hot-swappable |
| T2 | Rules (OPA/Rego) | Sandboxed OPA | AML rules, margin rules, compliance policies | ✅ Strong; OPA is the right engine choice |
| T3 | Executable (signed code) | Process/network isolated | Exchange adapters, FIX connectors, pricing models | ✅ Highest value; enables jurisdiction-specific market connectivity |

**Extension points per epic** are documented in Section 13 of all 42 epics. K-04 (Plugin Runtime) and K-05 (Event Bus) have the most comprehensive SDK contracts.

### 6.2 What Needs Fixing

| Issue | Impact | Fix |
|-------|--------|-----|
| Plugin manifest JSON lacks `tier` field | Runtime cannot enforce tier-based isolation | Add `"tier": "T1" | "T2" | "T3"` to manifest schema |
| Plugin sandbox has single isolation mode | T1 gets unnecessary overhead; T3 gets insufficient isolation | Implement per-tier isolation: T1=in-process, T2=OPA sandbox, T3=process+network |
| No Ed25519 signature verification code shown | T3 packs could be unsigned | Implement signature verification in plugin loader |
| Only K-04 explicitly states K-05 envelope conformance | Events emitted by plugins may not follow standard | Mandate K-05 envelope in plugin SDK; validate at emit time |
| Plugin example hardcodes NSE/BSE market hours | Violates jurisdiction-neutrality | Move market hours to T1 config pack |

### 6.3 Extensibility Recommendations

1. **Plugin Marketplace Architecture** — Define a plugin discovery, versioning, and distribution architecture (feeds into P-01 Pack Certification)
2. **Plugin Testing Framework** — Extend T-01 to include plugin-specific test harnesses (unit, integration, certification)
3. **Plugin Observability** — K-06 should define per-plugin metrics dashboards and alerts
4. **Plugin Dependency Resolution** — If T3 packs can depend on other packs, define a dependency DAG and resolution strategy

---

## 7. REAL-WORLD PROBLEM COVERAGE

### 7.1 Nepal Capital Markets (Primary Instantiation)

| Problem | Epic(s) | Status |
|---------|---------|--------|
| NEPSE order routing (TMS connectivity) | D-01, D-02 | ✅ FIX/API adapters |
| NEPSE minimum lot size (10 kitta) | D-01 | ✅ Validation rule |
| SEBON 30% initial margin requirement | D-06 | ✅ Risk engine rule |
| NRB capital adequacy compliance | D-06, D-10 | ✅ Risk + reporting |
| T+2 settlement on NEPSE | D-09 | ✅ Post-trade |
| CDS&C depository integration | D-09 | ✅ T3 adapter |
| EDIS authorization for delivery | D-09 | ✅ Post-trade workflow |
| Sun–Thursday trading week | D-04, K-15 | ✅ Dual-calendar |
| Bikram Sambat date handling | K-15 (all modules) | ✅ Platform-wide |
| SEBON promoter lock-in (3 years) | D-07 | ✅ Compliance rule |
| SEBON quarterly broker reporting | D-10 | ✅ Reg reporting |
| SEBON insider trading list | D-07, D-08 | ✅ Compliance + surveillance |
| Nepal bonus share ratios | D-12 | ✅ Corporate actions |
| 5% TDS on dividends | D-12 | ✅ Tax computation |
| Daily client money segregation | D-13 | ✅ Reconciliation |
| NEPSE circuit breakers (4%/5%/6%) | D-04 | ✅ Market data rules |
| Anti-money laundering (NRB/SEBON) | D-07, D-14 | ✅ AML + sanctions |
| Nepal sector classification | D-11 | ✅ Reference data |
| NEPSE scrip codes | D-11 | ✅ Reference data |
| Fuzzy name matching (Nepali/Hindi) | D-14 | ✅ Transliteration support |

**Coverage: 20/20 identified Nepal market requirements** ✅

### 7.2 India Capital Markets (Secondary Target)

| Problem | Epic(s) | Status |
|---------|---------|--------|
| NSE/BSE dual-exchange routing | D-02 (T3 adapter) | ✅ Via plugin |
| SEBI margin framework (peak, ELM, VaR) | D-06 (T2 rule) | ✅ Via rules pack |
| T+1 settlement (India transition) | D-09 | ✅ Configurable via K-02 |
| CDSL/NSDL depository integration | D-09 (T3 adapter) | ✅ Via plugin |
| GST/TDS tax computation | D-12, D-05 (T1 config) | ✅ Via config pack |
| SEBI insider trading regulations | D-07, D-08 | ✅ Extensible via rules |
| XBRL regulatory filing | D-10 | ✅ Format adapter |

**Coverage: 7/7 identified India market requirements** ✅ (all via plugin model, no hardcoding)

### 7.3 Gaps in Real-World Coverage

| Gap | Impact | Recommendation |
|-----|--------|----------------|
| **No wealth management workflow** (portfolio construction, rebalancing, advisory) | Wealth managers mentioned as users in C1 but no workflow epic | Add EPIC-W-03 Wealth Advisory Workflow |
| **No margin funding/lending workflow** | Common brokerage product; margin calls exist but not the funding lifecycle | Add to D-06 or create separate EPIC-D-15 |
| **No IPO bookbuilding workflow** despite merchant banking being a target user | Merchant banker listed in C1 but only mentioned in D-03 | Add EPIC-W-04 Primary Market Issuance |
| **No FX settlement architecture** despite global custodian in C1 | Cross-border settlement needs FX conversion | Add to D-09 or create EPIC-D-16 |

---

## 8. FUTURE-PROOFING ASSESSMENT

### 8.1 Promised vs. Delivered

| Topic | Promised (in changelogs/summaries) | Actual Content | Verdict |
|-------|-----------------------------------|----------------|---------|
| **Digital Assets / Tokenized Securities** | §1.2, §14, changelogs | Zero architectural content | ❌ **Not delivered** |
| **CBDC Integration** | Changelogs, §14 mentions | Zero content | ❌ **Not delivered** |
| **ESG (Environmental, Social, Governance)** | Changelogs | Zero content | ❌ **Not delivered** |
| **Post-Quantum Cryptography (PQC)** | §9 changelog | Zero content | ❌ **Not delivered** |
| **T+0 / Instant Settlement** | §14 mentions | Zero content | ❌ **Not delivered** |
| **India/Bangladesh expansion** | All epic §14 sections | ✅ Template questions in every epic | ⚠️ Formulaic |
| **Air-gapped deployment** | K-10 epic | ✅ Core requirement in deployment | ✅ **Delivered** |
| **API versioning** | §13 | Brief coverage | ⚠️ Needs expansion |
| **Multi-cloud** | K-10, §8 | ✅ K8s-based abstraction | ✅ **Delivered** |

### 8.2 Future-Proofing Recommendations

| Topic | Recommended Action | Priority |
|-------|-------------------|----------|
| **Digital Assets** | Create §15.1 "Digital Asset Extension Architecture" covering tokenized securities, custody, DeFi settlement adapter (T3), regulatory sandbox mode | HIGH (market moving fast) |
| **CBDC** | Create §15.2 "CBDC Integration Architecture" — payment rail adapter (T3), dual settlement (fiat + CBDC), NRB digital currency readiness | MEDIUM |
| **ESG** | Add ESG scoring to D-05 Pricing Engine as T1 config pack; add ESG reporting to D-10 | MEDIUM |
| **PQC** | Add §9.9 "Post-Quantum Migration Path" — hybrid key exchange (Kyber + X25519), hash-based signatures (SPHINCS+), timeline | MEDIUM |
| **T+0 Settlement** | Extend D-09 §14 with T+0 architecture implications (real-time gross settlement, intraday netting, liquidity management) | LOW (regulatory timeline) |

---

## 9. PRIORITIZED REMEDIATION BACKLOG

### P0 — Must Fix Before Development (Sprint 0, Week 1)

| # | Action | Files | Effort |
|---|--------|-------|--------|
| **P0-01** | Fix NFR values in §1.6 SLA table to match §11.2 canonical (≤2ms/≤12ms, 99.999%, 50K/100K) | ARCH_SPEC_PART_1, ARCH_AND_DESIGN_SPEC | 1h |
| **P0-02** | Replace the legacy Platform SDK epic ID with K-12 across all files | 10+ files | 1h |
| **P0-03** | Fix K-05 event envelope in all architecture code samples (add `timestamp_bs`, `tenant_id`, `causation_id`) | ARCH_SPEC_PART_1 §2-3 | 2h |
| **P0-04** | Fix C4 entities: add `_bs` fields to Order, Position classes | C4_C4_CODE | 1h |
| **P0-05** | Fix C4 `OrderEvent`: add all K-05 envelope fields | C4_C4_CODE | 1h |
| **P0-06** | Fix C1 order latency from `< 100ms` to `≤ 2ms / ≤ 12ms e2e` | C4_C1_CONTEXT | 30m |
| **P0-07** | Fix C2 NFR contradiction (item 1 vs item 2) | C4_C2_CONTAINER | 30m |
| **P0-08** | Fix JWT from HS256 → RS256/ES256 in security code | ARCH_SPEC_PART_2 §9.3 | 1h |
| **P0-09** | Fix `Math.random()` → `crypto.randomBytes()` for backup codes | ARCH_SPEC_PART_2 §9.4 | 30m |
| **P0-10** | Fix hardcoded SEBI/NSE in compliance code (§12.4) — use registry lookup | ARCH_SPEC_PART_3 §12.4 | 1h |
| **P0-11** | Fix hardcoded NSE/BSE market hours in plugin example — use T1 config | ARCH_SPEC_PART_1 §5 | 30m |
| **P0-12** | Fix `TradeExecuted` source attribution from D-01 → D-02 in D-03, D-08, D-09, D-10 | 4 epic files | 1h |
| **P0-13** | Reconcile DEPENDENCY_MATRIX.md with all epic Section 2 declarations | DEPENDENCY_MATRIX + 8 epics | 2h |
| **P0-14** | Fix PU-004 header "K-14" → "PU-004" | EPIC-PU-004 | 10m |

**Total P0 Effort: ~13 hours (2 developer-days)**

### P1 — Fix in Sprint 0 (Week 2)

| # | Action | Files | Effort |
|---|--------|-------|--------|
| **P1-01** | Add 31 missing `_bs` columns across 9 LLD database schemas | 9 LLD files | 4h |
| **P1-02** | Standardize K-05 envelope across D-01 and K-02 LLDs (`causation_id`/`data` not `causality_id`/`payload`) | LLD_D01, LLD_K02 | 2h |
| **P1-03** | Add RLS policies to multi-tenant tables in D-01, K-03, K-04, K-09 LLDs | 4 LLD files | 3h |
| **P1-04** | Add `"tier"` field to plugin manifest JSON schema | ARCH_SPEC_PART_1 §5.4 | 30m |
| **P1-05** | Document per-tier sandbox isolation (T1=in-process, T2=OPA, T3=process) | ARCH_SPEC_PART_1 §5.7 | 2h |
| **P1-06** | Define 7 missing compliance codes in COMPLIANCE_CODE_REGISTRY.md | COMPLIANCE_CODE_REGISTRY | 2h |
| **P1-07** | Update EPIC_VERSIONING_STRATEGY.md with current versions + 7 new epics | EPIC_VERSIONING_STRATEGY | 1h |
| **P1-08** | Add Threat Models to D-13, D-14, K-13, W-02, P-01, K-17 | 6 epic files | 6h |
| **P1-09** | Add "All events conform to K-05 standard envelope" to Section 6 of all epics | 38 epic files | 3h |
| **P1-10** | Add LLD cross-references to all 42 epic headers | 42 epic files | 2h |
| **P1-11** | Add MongoDB `_bs` companion fields to corporate_actions and regulatory_filings | ARCH_SPEC_PART_2 §7.4 | 1h |
| **P1-12** | Expand C3 to cover all 10+ core services (not just OMS, PMS, CMS) | C4_C3_COMPONENT | 4h |
| **P1-13** | Expand C2 to include all 13 external systems from C1 | C4_C2_CONTAINER | 2h |
| **P1-14** | Fix LLD_INDEX tech stack to include Java/Spring | LLD_INDEX | 30m |
| **P1-15** | Fix traceability matrix epic references (D-05, D-08) | ARCH_SPEC_PART_3 §14.2 | 30m |
| **P1-16** | Fix README Epic Layers table (PU scope, Testing scope, Kernel scope) | README.md | 30m |

**Total P1 Effort: ~34 hours (4-5 developer-days)**

### P2 — Fix in Sprint 1-2

| # | Action | Effort |
|---|--------|--------|
| **P2-01** | Author LLDs for K-08, K-10, K-11 (3 missing kernel LLDs) | 12h |
| **P2-02** | Expand Acceptance Criteria for K-08, K-10, K-11, D-05, D-08, D-11 to ≥5 ACs each | 4h |
| **P2-03** | Add K-06 as explicit dependency to all applicable epics | 2h |
| **P2-04** | Decide K-06 availability (99.99% vs 99.999%) and document justification | 1h |
| **P2-05** | Enrich Section 14 with P-01, W-02, R-02 evaluation across all epics | 4h |
| **P2-06** | Add 99.999% availability target explicitly in all LLD NFR tables | 2h |
| **P2-07** | Update Compliance Code Registry "Referenced By" lists for all new epics | 2h |
| **P2-08** | Fix K-05 saga tables to add `_bs` columns | 1h |
| **P2-09** | Fix Pack Index revision history date | 10m |
| **P2-10** | Add key rotation mechanism to encryption architecture (§9.5) | 2h |
| **P2-11** | Fix K-09 drift metric inconsistency (AC3 vs FR8 PSI thresholds) | 30m |
| **P2-12** | Fix `order_value_inr` metric name → `order_value_base_currency` | 10m |
| **P2-13** | Update K-10 to version 1.1.0 with same hardening as other epics | 2h |
| **P2-14** | Fix K-08 FR duplication (Section 2 vs Section 3) | 30m |
| **P2-15** | Add missing event-consumption dependencies to 12+ domain epics | 3h |
| **P2-16** | Standardize Section 13 detail level across all epics using K-04/K-05 as template | 8h |
| **P2-17** | Add Changelog sections to all v1.1.0 epics | 2h |
| **P2-18** | Fix date discrepancies in ARB_STRESS_TEST_REVIEW.md and REGULATORY_ARCHITECTURE_DOCUMENT.md | 30m |

**Total P2 Effort: ~47 hours (6 developer-days)**

---

## 10. IMPLEMENTATION PLAN

### 10.1 Foundational Principles

1. **Platform-First**: Build kernel (K-*) before domain (D-*) — domain modules are consumers of kernel services
2. **Event-Driven**: K-05 Event Bus must be operational before any domain service can publish/consume
3. **Config-Driven**: K-02 Configuration Engine enables all T1/T2/T3 pack loading — required before any jurisdiction-specific behavior
4. **Test-Driven**: Each phase includes dedicated testing milestones aligned with T-01 and T-02
5. **Compliance-Embedded**: Compliance verification integrated into every sprint, not deferred to a "compliance phase"

### 10.2 Phase Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    PROJECT SIDDHANTA IMPLEMENTATION TIMELINE                  │
├────────────┬────────────┬──────────────┬──────────────┬────────────────────┤
│  Sprint 0  │  Phase 1   │   Phase 2    │   Phase 3    │      Phase 4       │
│  (2 weeks) │ (3 months) │  (3 months)  │  (3 months)  │    (3 months)      │
│            │            │              │              │                    │
│ Doc Fixes  │  Kernel    │ Core Domain  │  Advanced    │  Ops, Packs,       │
│ Dev Setup  │  Services  │  Services    │  Domain +    │  Scale, Harden     │
│ CI/CD      │            │              │  Workflows   │                    │
└────────────┴────────────┴──────────────┴──────────────┴────────────────────┘
  Weeks 1-2     Months 1-3    Months 4-6     Months 7-9     Months 10-12
```

---

### 10.3 Sprint 0 — Foundation & Document Remediation (Weeks 1-2)

**Objective**: Fix all P0/P1 document issues, set up development infrastructure, establish coding standards.

#### Sprint 0A: Document Remediation (Week 1)

| Task | Priority | Owner | Deliverable |
|------|----------|-------|-------------|
| Execute P0-01 through P0-14 | P0 | Tech Lead | All critical doc fixes merged |
| Execute P1-01 through P1-16 | P1 | Tech Lead + Arch | All major doc fixes merged |
| Publish K-05 Event Envelope as shared TypeScript/Java types library | P0 | Kernel Architect | `@siddhanta/event-envelope` package |
| Publish DualDate utility as shared library | P0 | Kernel Architect | `@siddhanta/dual-calendar` package |

#### Sprint 0B: Infrastructure Setup (Week 2)

| Task | Owner | Deliverable |
|------|-------|-------------|
| Monorepo scaffold (Nx or Turborepo) | DevOps | Workspace with service templates |
| CI/CD pipeline (GitLab CI per §8.6) | DevOps | Build → Test → Scan → Deploy pipeline |
| K8s cluster provisioning (dev, staging) | DevOps | Namespaces per service |
| PostgreSQL + TimescaleDB + Redis + Kafka provisioned | DevOps | Data layer ready |
| Coding standards document (TypeScript, Java, Go, Python) | Tech Lead | Standards wiki |
| Shared libraries: Event Envelope, DualDate, SDK base | Kernel Team | npm + Maven packages |
| Test harness setup (Jest, JUnit, pytest, Playwright) | QA Lead | CI-integrated test runner |

---

### 10.4 Phase 1 — Kernel Services (Months 1-3)

**Objective**: Build the platform kernel — all services that domain modules depend on.

#### Phase 1 Sprint Plan

```
Sprint 1 (Weeks 3-4):   K-01 IAM + K-02 Config Engine
Sprint 2 (Weeks 5-6):   K-05 Event Bus + K-15 Dual-Calendar
Sprint 3 (Weeks 7-8):   K-03 Rules Engine + K-04 Plugin Runtime
Sprint 4 (Weeks 9-10):  K-07 Audit Framework + K-06 Observability
Sprint 5 (Weeks 11-12): K-14 Secrets + K-16 Ledger + K-11 API Gateway
Sprint 6 (Weeks 13-14): K-17 DTC + K-18 Resilience + K-19 DLQ
```

#### Phase 1 Detail

| Sprint | Epic(s) | Architecture Section | LLD Reference | Key Deliverables | Dependencies |
|--------|---------|---------------------|---------------|-----------------|--------------|
| **S1** | **K-01 IAM** | §9 Security Architecture | LLD_K01_IAM.md | OAuth 2.0 + JWT (RS256), RBAC engine, MFA (TOTP), tenant isolation | None (foundation) |
| | **K-02 Config Engine** | §4 Configuration Resolution | LLD_K02_CONFIGURATION_ENGINE.md | 6-level infra + 5-level business hierarchy, Vault integration, T1 pack loader | K-01 |
| **S2** | **K-05 Event Bus** | §3 Event-Driven/CQRS | LLD_K05_EVENT_BUS.md | Kafka cluster, standard event envelope, schema registry (Avro), event store | K-01, K-02 |
| | **K-15 Dual-Calendar** | §4 (dual-calendar invariant) | LLD_K15_DUAL_CALENDAR.md | BS↔Gregorian conversion, calendar math, business day calculation, Sun-Thu support | K-02 |
| **S3** | **K-03 Rules Engine** | §5 Plugin Runtime (T2) | LLD_K03_RULES_ENGINE.md | OPA/Rego integration, T2 pack loading, rule evaluation pipeline, sandbox | K-02, K-04, K-05 |
| | **K-04 Plugin Runtime** | §5 Plugin Runtime | LLD_K04_PLUGIN_RUNTIME.md | T1/T2/T3 lifecycle management, per-tier sandbox, signature verification, plugin registry | K-02, K-03, K-05 |
| **S4** | **K-07 Audit Framework** | §9.8 Audit, §12 Compliance | LLD_K07_AUDIT_FRAMEWORK.md | Immutable audit log (hash-chain), 10yr retention, dual-calendar timestamps, export | K-01, K-05, K-15 |
| | **K-06 Observability** | §10 Observability | LLD_K06_OBSERVABILITY.md | Prometheus metrics, Jaeger tracing (OpenTelemetry), ELK logging, Grafana dashboards, PII masking | K-02, K-05 |
| **S5** | **K-14 Secrets** | §9.5 Encryption | (Author LLD in sprint) | HashiCorp Vault integration, key rotation, secret injection, envelope encryption | K-01, K-02 |
| | **K-16 Ledger** | §7 Data Architecture | LLD_K16_LEDGER_FRAMEWORK.md | Double-entry bookkeeping, immutable postings, multi-currency, BS-dated closing | K-05, K-07, K-15 |
| | **K-11 API Gateway** | §2 (API Gateway layer) | (Author LLD in sprint) | Kong/NGINX, rate limiting, auth forwarding, request validation, WebSocket proxy | K-01, K-02, K-06 |
| **S6** | **K-17 DTC** | §3 (Saga orchestration) | LLD_K17_DTC.md | Saga orchestrator, compensation engine, timeout handling, DLQ integration | K-05, K-07, K-18 |
| | **K-18 Resilience** | §11 Performance | LLD_K18_RESILIENCE.md | Circuit breaker, retry with backoff, bulkhead, timeout, fallback patterns | K-02, K-06 |
| | **K-19 DLQ** | §11 (async processing) | LLD_K19_DLQ.md | Dead letter queue management, replay, poison pill detection, alerting | K-05, K-06, K-07 |

#### Phase 1 Exit Criteria

- [ ] All 15 kernel services deployed to staging
- [ ] K-05 event envelope validated end-to-end with dual-calendar timestamps
- [ ] K-01 RBAC with 5+ role definitions per tenant
- [ ] K-02 loading T1 config packs from registry
- [ ] K-04 successfully loading T1/T2/T3 packs with per-tier isolation
- [ ] K-07 audit trail with hash-chain verification passing
- [ ] K-15 dual-calendar conversion with edge cases (Chaitra 30 → Baisakh 1)
- [ ] Integration test suite covering all kernel cross-dependencies
- [ ] Performance benchmark: K-05 publish ≤ 2ms P99

---

### 10.5 Phase 2 — Core Domain Services (Months 4-6)

**Objective**: Build the core trading and settlement pipeline.

#### Phase 2 Sprint Plan

```
Sprint 7 (Weeks 15-16):  D-11 Reference Data + D-04 Market Data
Sprint 8 (Weeks 17-18):  D-01 OMS + D-06 Risk Engine
Sprint 9 (Weeks 19-20):  D-02 EMS + D-05 Pricing Engine
Sprint 10 (Weeks 21-22): D-03 PMS + D-09 Post-Trade
Sprint 11 (Weeks 23-24): D-12 Corporate Actions + K-13 Admin Portal
Sprint 12 (Weeks 25-26): Integration Testing + Performance Testing
```

#### Phase 2 Detail

| Sprint | Epic(s) | Architecture Section | LLD Reference | Key Deliverables | Dependencies |
|--------|---------|---------------------|---------------|-----------------|--------------|
| **S7** | **D-11 Reference Data** | §7 Data Architecture | (Author LLD) | Instrument master, jurisdiction classification, corporate hierarchy, T1 pack for Nepal sectors/scrip codes | K-02, K-05, K-15 |
| | **D-04 Market Data** | §7 (TimescaleDB), §11 | (Author LLD) | Real-time tick ingestion, NEPSE feed handler, circuit breaker detection, OHLCV aggregation | K-02, K-05, K-18, D-11 |
| **S8** | **D-01 OMS** | §2 (Order aggregate), §3 (CQRS) | LLD_D01_OMS.md | Order placement (≤2ms/≤12ms P99), validation pipeline, NEPSE lot size rules, order book | K-01 through K-07, D-11, D-04 |
| | **D-06 Risk Engine** | §6 (AI), §11 (perf) | (Author LLD) | Pre-trade risk check, margin calculation (SEBON 30%), exposure limits, VaR, capital adequacy | K-02, K-03, K-05, D-01, D-04 |
| **S9** | **D-02 EMS** | §2 (Integration layer) | (Author LLD) | NEPSE TMS adapter (T3), FIX engine, smart order routing, best execution | K-04, K-05, K-18, D-01 |
| | **D-05 Pricing Engine** | §7 (Analytics) | (Author LLD) | Mark-to-market, NAV calculation, yield curves, T1 pricing config packs | K-02, K-03, D-04, D-11 |
| **S10** | **D-03 PMS** | §7 (PostgreSQL schemas) | (Author LLD) | Position tracking, P&L (realized/unrealized), portfolio aggregation, NAV (BS-dated) | K-05, K-15, D-01, D-02 |
| | **D-09 Post-Trade** | §7, §12 (compliance) | (Author LLD) | T+2 settlement, CDS&C (T3 adapter), EDIS auth, netting, obligation management | K-04, K-05, K-16, K-17, D-02, D-03 |
| **S11** | **D-12 Corporate Actions** | §7.4 (MongoDB) | (Author LLD) | Bonus shares, dividends (5% TDS), rights issues, stock splits, ex-date processing | K-02, K-05, K-15, D-03, D-11 |
| | **K-13 Admin Portal** | §2 (Presentation layer) | (Author LLD) | Operator dashboard, system configuration UI, user management, tenant administration | K-01, K-02, K-06, K-07 |
| **S12** | **T-01 Integration Testing** | §Testing Strategy | — | End-to-end test suites: Order → Execution → Settlement → Position Update. Performance benchmarks. | All Phase 1+2 services |

#### Phase 2 Exit Criteria

- [ ] Full order lifecycle: Place → Risk Check → Route → Execute → Settle → Update Position
- [ ] D-01 OMS ≤ 2ms internal / ≤ 12ms e2e P99 benchmark passing
- [ ] NEPSE adapter (T3) successfully routing orders to test environment
- [ ] T+2 settlement workflow completing in staging
- [ ] Market data ingestion at 50K TPS sustained
- [ ] All dual-calendar conversions verified for Nepal fiscal quarters
- [ ] K-13 Admin Portal operational for system configuration
- [ ] Integration test coverage ≥ 80% for critical paths

---

### 10.6 Phase 3 — Advanced Domain + Workflows (Months 7-9)

**Objective**: Build compliance, surveillance, reconciliation, and operator workflows.

#### Phase 3 Sprint Plan

```
Sprint 13 (Weeks 27-28): D-07 Compliance + D-14 Sanctions
Sprint 14 (Weeks 29-30): D-08 Surveillance + D-10 Regulatory Reporting
Sprint 15 (Weeks 31-32): D-13 Client Money Recon + W-01 Workflow Orchestration
Sprint 16 (Weeks 33-34): W-02 Client Onboarding + K-08 Data Governance
Sprint 17 (Weeks 35-36): K-09 AI Governance + R-01 Regulator Portal
Sprint 18 (Weeks 37-38): R-02 Incident Notification + T-02 Chaos Engineering
```

#### Phase 3 Detail

| Sprint | Epic(s) | Architecture Section | LLD Reference | Key Deliverables |
|--------|---------|---------------------|---------------|-----------------|
| **S13** | **D-07 Compliance** | §12 Compliance | (Author LLD) | AML rules (T2), promoter lock-in, insider list, maker-checker enforcement, SEBON compliance pipeline |
| | **D-14 Sanctions** | §12, §9 (security) | LLD_D14_SANCTIONS.md | OFAC/UN/EU list screening, fuzzy Nepali/Hindi name matching, real-time PEP checks, false-positive workflow |
| **S14** | **D-08 Surveillance** | §6 (AI), §12 | (Author LLD) | Wash-trade detection, front-running, insider trading patterns, alerting, SEBON reporting |
| | **D-10 Regulatory Reporting** | §12, §7 | (Author LLD) | SEBON quarterly reports, XBRL, BS-dated periods, automated filing pipeline, R-01 integration |
| **S15** | **D-13 Client Money Recon** | §7, §16 (Ledger) | LLD_D13_CLIENT_MONEY.md | Daily segregation check, bank statement import (MT940/CAMT.053), variance detection, regulatory alerts |
| | **W-01 Workflow Orchestration** | §5 (Plugin Runtime) | (Author LLD) | Camunda BPM integration, DSL for workflow definitions, approval chains, escalation, SLA tracking |
| **S16** | **W-02 Client Onboarding** | §9 (Security), §12 | (Author LLD) | KYC pipeline, eKYC integration (T3), document verification (AI), AML screening, account activation |
| | **K-08 Data Governance** | §8 (Data), §7 | (Author LLD — P2-01) | Data classification, PII tagging, retention policies, lineage tracking, GDPR right-to-erasure |
| **S17** | **K-09 AI Governance** | §6 AI Governance | LLD_K09_AI_GOVERNANCE.md | Model registry (MLflow), drift detection (PSI), explainability (SHAP), approval workflow, audit trail |
| | **R-01 Regulator Portal** | §12, §9 | (Author LLD) | SEBON read-only access, evidence export (signed), audit trail access, report generation, secure portal |
| **S18** | **R-02 Incident Notification** | §12, §10 | (Author LLD) | Regulatory incident detection, notification workflow, SEBON/NRB reporting, breach timeline, evidence packaging |
| | **T-02 Chaos Engineering** | §15 Risks | (Author LLD) | Fault injection framework, network partition simulation, Kafka failure scenarios, safety controls |

#### Phase 3 Exit Criteria

- [ ] AML pipeline processing 100% of new orders against rules
- [ ] Sanctions screening with <500ms P99 for real-time checks
- [ ] Surveillance detecting synthetic wash-trade patterns in test data
- [ ] Client onboarding workflow completing end-to-end (KYC → AML → Account)
- [ ] Daily client money reconciliation automated
- [ ] Regulator portal accessible with signed evidence export
- [ ] Chaos engineering: System survives Kafka partition, DB failover, and 3x traffic spike

---

### 10.7 Phase 4 — Operations, Packs, Scale & Harden (Months 10-12)

**Objective**: Production hardening, pack ecosystem, operational readiness.

#### Phase 4 Sprint Plan

```
Sprint 19 (Weeks 39-40): P-01 Pack Certification + K-10 Deployment Abstraction
Sprint 20 (Weeks 41-42): K-12 Platform SDK + PU-004 Platform Manifest
Sprint 21 (Weeks 43-44): O-01 Operator Workflows + Nepal T1/T2/T3 Packs
Sprint 22 (Weeks 45-46): Performance Hardening + Security Audit
Sprint 23 (Weeks 47-48): UAT + Regulatory Certification
Sprint 24 (Weeks 49-50): Production Deployment + Cutover
```

#### Phase 4 Detail

| Sprint | Epic(s) | Key Deliverables |
|--------|---------|-----------------|
| **S19** | **P-01 Pack Certification** | Pack submission pipeline, automated testing, security scanning, approval workflow, pack registry |
| | **K-10 Deployment Abstraction** | Multi-environment provisioning, air-gapped support, hybrid cloud sync, blue-green deployment |
| **S20** | **K-12 Platform SDK** | TypeScript + Java SDKs, API documentation, developer portal, code generation |
| | **PU-004 Platform Manifest** | Platform version management, module compatibility matrix, upgrade orchestration |
| **S21** | **O-01 Operator Workflows** | Runbook execution, operational dashboards, alerting triage, change management (BS-dated) |
| | **Nepal Content Packs** | T1: SEBON fee schedule, NRB rates, Nepal holidays, BS calendar; T2: SEBON margin rules, AML rules, promoter lock-in; T3: NEPSE TMS adapter, CDS&C adapter |
| **S22** | **Performance Hardening** | Load testing (50K sustained TPS), P99 latency verification, connection pool tuning, cache warming, DB query optimization |
| | **Security Audit** | Penetration testing, OWASP Top 10 verification, secrets rotation test, mTLS end-to-end, RLS verification |
| **S23** | **UAT** | User acceptance testing with SEBON scenarios, broker workflow validation, merchant banker workflow, audit trail verification |
| | **Regulatory Certification** | SEBON compliance demonstration, NRB capital adequacy proof, audit trail completeness, evidence export validation |
| **S24** | **Production Deployment** | Blue-green deployment, data migration, DNS cutover, monitoring alerts live, 24/7 on-call |
| | **Cutover** | Parallel run (2 weeks), reconciliation between old and new systems, final cutover, post-go-live support |

#### Phase 4 Exit Criteria

- [ ] All 42 module deployments passing in production K8s cluster
- [ ] Nepal T1/T2/T3 content packs certified and loaded through P-01 pipeline
- [ ] 50K TPS sustained load test passing for 4+ hours
- [ ] 99.999% availability target achieved over 30-day pre-production monitoring
- [ ] Security audit: Zero critical, zero high findings
- [ ] SEBON regulatory certification obtained
- [ ] Disaster recovery drill: RTO < 15 min, RPO < 1 sec validated
- [ ] Post-go-live support runbook documented and tested

---

### 10.8 Team Structure Recommendation

| Team | Size | Scope | Phase Focus |
|------|:----:|-------|-------------|
| **Kernel Platform** | 5 | K-01 to K-19 | Phases 1, 4 |
| **Trading Core** | 4 | D-01, D-02, D-04, D-05, D-06 | Phase 2 |
| **Post-Trade & Settlement** | 3 | D-03, D-09, D-12, D-13, K-16 | Phase 2-3 |
| **Compliance & Regulatory** | 3 | D-07, D-08, D-10, D-14, R-01, R-02 | Phase 3 |
| **Workflows & Onboarding** | 2 | W-01, W-02, O-01 | Phase 3-4 |
| **Platform Engineering** | 3 | K-10, K-12, K-13, P-01, PU-004 | Phase 4 |
| **QA & Reliability** | 3 | T-01, T-02, cross-service testing | All phases |
| **DevOps** | 2 | CI/CD, K8s, monitoring infrastructure | All phases |

**Total: ~25 engineers** (can be scaled based on budget constraints)

---

### 10.9 Risk-Adjusted Timeline

| Scenario | Duration | Assumptions |
|----------|----------|-------------|
| **Optimistic** | 10 months | Full team, no major blockers, regulatory alignment |
| **Planned** | 12 months | Standard velocity, 1-2 sprint slips, iterative regulatory feedback |
| **Conservative** | 14 months | Team ramp-up delay, NEPSE API instability, regulatory change requests |

### 10.10 Key Dependencies & Critical Path

```
K-01 IAM ─┬─ K-02 Config ─┬─ K-05 Event Bus ─┬─ D-01 OMS ─── D-02 EMS ─── D-09 Post-Trade
           │               │                   │
           │               ├─ K-03 Rules ──────┤
           │               │                   │
           │               ├─ K-04 Plugin ─────┘
           │               │
           │               └─ K-15 Calendar ──── D-03 PMS ──── D-12 Corp Actions
           │
           └─ K-07 Audit ── K-16 Ledger ──── D-13 Client Money
```

**Critical Path**: K-01 → K-02 → K-05 → D-01 → D-02 → D-09 (Order → Execute → Settle)

This is the **minimum viable trading pipeline** and should be the team's primary focus through Phases 1-2.

---

### 10.11 LLD Authoring Schedule

| Phase | LLDs to Author | Priority |
|-------|----------------|----------|
| Sprint 0 | K-08, K-10, K-11 (3 missing kernel LLDs) | P1 — block Phase 1 |
| Phase 2 S7 | D-11 Reference Data, D-04 Market Data | Before implementation |
| Phase 2 S8 | D-06 Risk Engine | Before implementation |
| Phase 2 S9 | D-02 EMS, D-05 Pricing Engine | Before implementation |
| Phase 2 S10 | D-03 PMS, D-09 Post-Trade | Before implementation |
| Phase 2 S11 | D-12 Corporate Actions, K-13 Admin Portal | Before implementation |
| Phase 3 | D-07, D-08, D-10, W-01, W-02, K-08, R-01, R-02 | 1 sprint before coding |

**Rule**: Every epic must have its LLD authored **at least 1 sprint before implementation begins**.

---

## APPENDIX A: EPIC → ARCHITECTURE → LLD TRACEABILITY MATRIX

| Epic | Arch Section(s) | LLD | C4 Coverage | Phase |
|------|-----------------|-----|-------------|-------|
| K-01 IAM | §9 | LLD_K01_IAM | C3 Identity | 1 |
| K-02 Config Engine | §4 | LLD_K02_CONFIG | C3 Config | 1 |
| K-03 Rules Engine | §5 | LLD_K03_RULES | C3 Rules | 1 |
| K-04 Plugin Runtime | §5 | LLD_K04_PLUGIN | C3 Plugin | 1 |
| K-05 Event Bus | §3 | LLD_K05_EVENT_BUS | C3 Event | 1 |
| K-06 Observability | §10 | LLD_K06_OBS | C2 Monitoring | 1 |
| K-07 Audit | §9.8, §12 | LLD_K07_AUDIT | — | 1 |
| K-08 Data Governance | §7, §8 | **PENDING** | — | 3 |
| K-09 AI Governance | §6 | LLD_K09_AI | C3 AI | 3 |
| K-10 Deployment | §8 | **PENDING** | C2 K8s | 4 |
| K-11 API Gateway | §2 | **PENDING** | C2 Gateway | 1 |
| K-12 Platform SDK | §5 | — | — | 4 |
| K-13 Admin Portal | §2 | — | C2 Portal | 2 |
| K-14 Secrets | §9.5 | — | — | 1 |
| K-15 Dual-Calendar | §4 | LLD_K15_CAL | — | 1 |
| K-16 Ledger | §7 | LLD_K16_LEDGER | — | 1 |
| K-17 DTC | §3 | LLD_K17_DTC | — | 1 |
| K-18 Resilience | §11 | LLD_K18_RES | — | 1 |
| K-19 DLQ | §11 | LLD_K19_DLQ | — | 1 |
| D-01 OMS | §2, §3 | LLD_D01_OMS | C3 OMS | 2 |
| D-02 EMS | §2 | **PENDING** | C3 OMS (partial) | 2 |
| D-03 PMS | §7 | **PENDING** | C3 PMS | 2 |
| D-04 Market Data | §7 | **PENDING** | — | 2 |
| D-05 Pricing Engine | §7 | **PENDING** | — | 2 |
| D-06 Risk Engine | §6, §11 | **PENDING** | C3 RMS | 2 |
| D-07 Compliance | §12 | **PENDING** | C3 Compliance | 3 |
| D-08 Surveillance | §6, §12 | **PENDING** | — | 3 |
| D-09 Post-Trade | §7, §12 | **PENDING** | — | 2 |
| D-10 Reg Reporting | §12 | **PENDING** | — | 3 |
| D-11 Reference Data | §7 | **PENDING** | — | 2 |
| D-12 Corporate Actions | §7 | **PENDING** | — | 2 |
| D-13 Client Money | §7, §16 | LLD_D13_CMR | — | 3 |
| D-14 Sanctions | §9, §12 | LLD_D14_SANC | — | 3 |
| W-01 Workflow | §5 | **PENDING** | — | 3 |
| W-02 Onboarding | §9, §12 | **PENDING** | — | 3 |
| P-01 Pack Certification | §5 | **PENDING** | — | 4 |
| T-01 Integration Testing | Testing strategy | — | — | 2 |
| T-02 Chaos Engineering | §15 | — | — | 3 |
| O-01 Operator Workflows | §2 | — | — | 4 |
| R-01 Regulator Portal | §12 | **PENDING** | — | 3 |
| R-02 Incident Notification | §12, §10 | **PENDING** | — | 3 |
| PU-004 Platform Manifest | — | — | — | 4 |

---

## APPENDIX B: DOCUMENT REVISION PRIORITIES

| Document | Current State | Required Action | When |
|----------|--------------|-----------------|------|
| ARCHITECTURE_SPEC_PART_1_SECTIONS_1-3.md | §1.6 NFR stale; event schemas lack BS | Fix NFRs; add BS to events | Sprint 0 |
| ARCHITECTURE_SPEC_PART_1_SECTIONS_4-5.md | Plugin manifest lacks tier; sandbox not per-tier | Add tier field; implement per-tier isolation | Sprint 0 |
| ARCHITECTURE_SPEC_PART_2_SECTIONS_6-8.md | MongoDB schemas lack BS fields | Add _bs companions | Sprint 0 |
| ARCHITECTURE_SPEC_PART_2_SECTIONS_9-10.md | HS256 JWT; Math.random(); no key rotation | Fix security code; add PQC roadmap | Sprint 0 |
| ARCHITECTURE_SPEC_PART_3_SECTIONS_11-15.md | Hardcoded SEBI/NSE; traceability refs wrong | Fix jurisdiction violations; fix refs | Sprint 0 |
| C4_C1_CONTEXT_SIDDHANTA.md | Stale latency value | Fix to ≤2ms/12ms | Sprint 0 |
| C4_C2_CONTAINER_SIDDHANTA.md | NFR contradiction; missing external systems | Fix NFR; expand external systems | Sprint 0 |
| C4_C3_COMPONENT_SIDDHANTA.md | Only 3 of 10 services covered | Expand to all core services | P1 |
| C4_C4_CODE_SIDDHANTA.md | Entities lack _bs; events lack K-05 envelope | Add dual-calendar + envelope fields | Sprint 0 |
| DEPENDENCY_MATRIX.md | Legacy Platform SDK ID drift; 8+ mismatches | Full reconciliation | Sprint 0 |
| COMPLIANCE_CODE_REGISTRY.md | 7+ undefined codes | Define missing codes | P1 |
| EPIC_VERSIONING_STRATEGY.md | Stale; missing 7 ARB epics | Full update | P1 |
| README.md | 3 factual errors in layers table | Fix table | Sprint 0 |
| LLD_INDEX.md | Tech stack omits Java | Fix tech stack | Sprint 0 |
| All 42 Epics | No LLD cross-refs; K-05 envelope not declared | Add LLD refs; add envelope clause | P1 |

---

**END OF DOCUMENT**

*This document should be reviewed with all stakeholders before Sprint 0 begins. All P0 items are blockers for development kickoff.*
