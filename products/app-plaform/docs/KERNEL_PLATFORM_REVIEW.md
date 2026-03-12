# Siddhanta Platform — Core Kernel Review & Implementation Plan

> **Document Type:** Assessment Report + Implementation Plan
> **Generated:** 2026-03-11 | **Status:** Phase 0 — Execution Bootstrap (Day 3)
> **Scope:** All 19 Kernel (K-*) modules, cross-cutting platform concerns, TDD specs, and delivery program

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Architecture Overview](#2-architecture-overview)
3. [Kernel Epics — 19 Core Modules](#3-kernel-epics--19-core-modules)
4. [Kernel Stories — Verified Inventory](#4-kernel-stories--verified-inventory)
5. [TDD Test Specifications — 314+ Test Cases](#5-tdd-test-specifications--314-test-cases)
6. [Dependency Matrix & Critical Path](#6-dependency-matrix--critical-path)
7. [Cross-Cutting Platform Concerns](#7-cross-cutting-platform-concerns)
8. [Low-Level Designs (LLD) Coverage](#8-low-level-designs-lld-coverage)
9. [Gap Analysis](#9-gap-analysis)
10. [Corrections to Initial Assessment](#10-corrections-to-initial-assessment)
11. [Summary Scorecard](#11-summary-scorecard)
12. [Detailed Implementation Plan](#12-detailed-implementation-plan)

---

## 1. Executive Summary

The Siddhanta Platform Kernel is a **jurisdiction-neutral, AI-native, event-sourced foundation** for capital markets applications. It comprises **19 kernel modules** (K-01 through K-19) that provide all infrastructure services — identity, configuration, eventing, audit, ledger, resilience, observability, governance — on top of which domain apps (OMS, EMS, PMS, Risk, Compliance, etc.) are developed or plugged in.

### Key Metrics

| Dimension | Count |
|-----------|-------|
| Kernel Epics | 19 |
| Kernel Stories (verified) | 310 |
| Kernel Story Points (verified) | 807 SP |
| TDD Test Cases (kernel) | 314+ |
| Low-Level Designs | 19/19 complete |
| ARB Findings Addressed | 20/20 |
| Delivery Milestones | 5 (A through E) |
| Target MVP | September 18, 2026 |

### Current Status

- **Phase 0 — Execution Bootstrap** (March 9–20, 2026)
- Day 3 of execution; no runtime services exist yet
- Repository is spec-first: all epics, stories, LLDs, TDD specs are authored
- Building: monorepo structure, shared contracts, local dev stack, CI/CD pipeline

---

## 2. Architecture Overview

### 2.1 Multi-Domain Platform Architecture

The Siddhanta platform is designed as a **multi-domain operating system** with a clear separation between:

- **Generic Core (Kernel)**: 19 domain-agnostic modules providing cross-cutting capabilities
- **Domain Layer**: Pluggable domain packs implementing industry-specific functionality
- **Plugin System**: T1/T2/T3 content packs for jurisdiction and tenant customization

### 2.2 Kernel Layer (Generic Core)

All 19 kernel modules (K-01 through K-19) are **completely domain-agnostic** and provide:

| Layer | Modules | Purpose |
|-------|---------|---------|
| **Identity & Access** | K-01, K-14 | Multi-tenant IAM, secrets management |
| **Configuration & Rules** | K-02, K-03 | Hierarchical config, policy evaluation |
| **Plugin & Event System** | K-04, K-05, K-17 | Plugin runtime, event sourcing, transactions |
| **Observability & Audit** | K-06, K-07, K-18, K-19 | Logging, audit, resilience, DLQ |
| **Data & AI Governance** | K-08, K-09, K-16 | Data lineage, AI governance, ledger |
| **Platform Services** | K-10, K-11, K-12, K-13, K-15 | Deployment, gateway, SDK, admin, calendar |

### 2.3 Domain Pack Architecture

Domain packs provide industry-specific capabilities while leveraging the generic kernel:

- **Capital Markets (Siddhanta)**: Trading, settlement, risk, compliance
- **Banking**: Account management, payments, loans, treasury
- **Healthcare**: Patient records, clinical workflows, billing
- **Insurance**: Policy management, claims processing, underwriting
- **Manufacturing**: Production, inventory, quality control
- **Logistics**: Supply chain, transportation, warehouse management

Each domain pack defines:
- Domain-specific data models and schemas
- Business rules and workflows
- External integrations and adapters
- User interface components
- Configuration templates

### 2.5 Domain Pack Loading & Lifecycle

Domain packs are loaded through the K-04 Plugin Runtime with additional domain-specific capabilities:

1. **Domain Pack Registration**: Manifest validation, dependency resolution
2. **Schema Registration**: Domain models, events, APIs registered in K-02
3. **Workflow Registration**: Domain workflows registered in W-01
4. **Integration Registration**: External adapters registered in K-11
5. **UI Registration**: Domain UI components registered in K-13

### 2.6 Multi-Domain Deployment

The platform supports multiple deployment models:

- **Single-Domain**: One domain pack per deployment (simplest)
- **Multi-Domain**: Multiple domain packs per deployment (shared infrastructure)
- **Domain-Specific**: Separate deployments per domain (isolation)
- **Hybrid**: Mix of domain-specific and multi-domain deployments

### 2.7 Domain Pack vs Kernel Separation

```
┌─────────────────────────────────────────────────────────┐
│  Layer 2: Portals & Workflows                           │
│  Admin Portal (K-13) · Regulator Portal (R-01)          │
│  Operator Console (O-01) · Client Onboarding (W-02)     │
├─────────────────────────────────────────────────────────┤
│  Layer 1: Domain Packs (Pluggable)                      │
│  Capital Markets · Banking · Healthcare · Insurance     │
│  Trading · Payments · Clinical · Policy Management      │
├─────────────────────────────────────────────────────────┤
│  Layer 0: Platform Kernel (K-01 through K-19)           │
│  IAM · Config · Rules · Plugin · Events · Observability │
│  Audit · Data Gov · AI Gov · Deploy · Gateway · SDK     │
│  Admin · Secrets · Calendar · Ledger · DTC · Resilience │
│  DLQ Management                                         │
├─────────────────────────────────────────────────────────┤
│  Infrastructure: PostgreSQL · Redis · Kafka · K8s       │
│  TimescaleDB · Prometheus · Grafana · Jaeger · OpenSearch      │
└─────────────────────────────────────────────────────────┘
```

### 2.5 Domain Pack vs Kernel Separation

The platform enforces strict separation between generic kernel and domain-specific logic:

| **Generic Kernel (K-01 through K-19)** | **Domain Packs** |
|--------------------------------------|-------------------|
| ✅ Identity & Access Management | ❌ Domain-specific business logic |
| ✅ Configuration & Rules Engine | ✅ Domain-specific rules & workflows |
| ✅ Plugin Runtime & SDK | ✅ Domain-specific adapters |
| ✅ Event Sourcing & Audit | ✅ Domain-specific events |
| ✅ Observability & Monitoring | ❌ Domain-specific metrics |
| ✅ Deployment & Gateway | ❌ Domain-specific APIs |

**Key Principle**: Domain packs **MUST NOT** contain generic platform logic. The Kernel **MUST NOT** contain domain-specific business rules.

### 2.6 Domain Pack Loading & Lifecycle

Domain packs are loaded through the K-04 Plugin Runtime with additional domain-specific capabilities:

1. **Domain Pack Registration**: Manifest validation, dependency resolution
2. **Schema Registration**: Domain models, events, APIs registered in K-02
3. **Workflow Registration**: Domain workflows registered in W-01
4. **Integration Registration**: External adapters registered in K-11
5. **UI Registration**: Domain UI components registered in K-13

### 2.7 Multi-Domain Deployment

The platform supports multiple deployment models:

- **Single-Domain**: One domain pack per deployment (simplest)
- **Multi-Domain**: Multiple domain packs per deployment (shared infrastructure)
- **Domain-Specific**: Separate deployments per domain (isolation)
- **Hybrid**: Mix of domain-specific and multi-domain deployments
│  Ref Data · Corp Actions · Client Money · Sanctions      │
├─────────────────────────────────────────────────────────┤
│  Layer 0: Platform Kernel (K-01 through K-19)           │
│  IAM · Config · Rules · Plugin · Events · Observability │
│  Audit · Data Gov · AI Gov · Deploy · Gateway · SDK     │
│  Admin · Secrets · Calendar · Ledger · DTC · Resilience │
│  DLQ Management                                         │
├─────────────────────────────────────────────────────────┤
│  Infrastructure: PostgreSQL · Redis · Kafka · K8s       │
│  TimescaleDB · Prometheus · Grafana · Jaeger · OpenSearch      │
└─────────────────────────────────────────────────────────┘
```

### 2.8 Design Principles

1. **Zero Hardcoding** — All domain-specific rules externalized to versioned Domain Packs and Content Packs (T1/T2/T3)
2. **Event-Sourced** — All state changes are immutable events in K-05 Event Store
3. **Dual-Calendar at Data Layer** — Bikram Sambat + Gregorian in every timestamp (K-15)
4. **No Kernel Duplication** — Domain packs access kernel exclusively via K-12 Platform SDK
5. **Single Pane of Glass** — Centralized admin, config, and observability (K-13, K-06)
6. **Domain Pack Isolation** — Domain packs are pluggable and isolated from kernel logic
7. **Multi-Domain Support** — Kernel supports multiple domains simultaneously without modification

### Technology Stack (ADR-011)

| Layer | Technology |
|-------|-----------|
| Backend (Kernel + Domain) | Java 21 + ActiveJ |
| User API | Node.js LTS + TypeScript + Fastify + Prisma |
| AI/ML | Python 3.11 + FastAPI |
| Frontend | React 18 + Jotai + TanStack Query |
| Data | PostgreSQL + Redis + Kafka + TimescaleDB |
| Observability | Prometheus + Grafana + Jaeger + OpenSearch |
| Deployment | 5 topologies: SaaS, Dedicated, On-Prem, Hybrid, Air-Gap |

---

## 3. Kernel Epics — 19 Core Modules

### 3.1 K-01: Identity & Access Management

- **Purpose:** Multi-tenant IAM with OAuth 2.0, RBAC/ABAC, MFA, beneficial ownership graph
- **Key FRs:** OAuth 2.0 (client_credentials, authorization_code+PKCE), refresh token rotation with family tracking, TOTP MFA, brute-force protection with account lockout, Redis-backed sessions with concurrent limits, cross-tenant session isolation, RBAC with Redis-cached permissions, ABAC via K-03 integration, super-admin and break-glass access, T3 national ID plugin interface, mTLS service identity, beneficial ownership graph with traversal API, login anomaly detection
- **Dependencies:** K-02, K-03, K-04, K-05, K-07, K-14, K-15
- **NFRs:** 5K TPS, P99 <10ms for auth, 99.999% uptime
- **Threat Model:** Credential stuffing, session hijacking, privilege escalation, token theft, cross-tenant leakage
- **Concerns:** Auth TPS (5K) vs Event Bus TPS (100K) throughput mismatch for auth-gated event paths; beneficial ownership graph marked GAP-002

### 3.2 K-02: Configuration Engine

- **Purpose:** 5-level hierarchical config (GLOBAL→JURISDICTION→TENANT→USER→SESSION), maker-checker approval, hot-reload, dual-calendar effective dates, CQRS
- **Key FRs:** Schema registration/validation, 5-level hierarchy with deep merge, hot-reload via PostgreSQL LISTEN/NOTIFY or Kafka, canary rollout for config changes with auto-rollback, dual-calendar effective dates, temporal queries (as-of-date), air-gap bundle generation with Ed25519 signing, maker-checker workflow, CQRS write/read sides
- **Dependencies:** K-01, K-05, K-07, K-14, K-15
- **NFRs:** <0.5ms cached reads (Redis), strong consistency for writes
- **Concerns:** Mid-session config change race conditions in distributed deployments

### 3.3 K-03: Policy / Rules Engine

- **Purpose:** OPA/Rego-based declarative policy evaluation with T2 sandbox execution, jurisdiction routing
- **Key FRs:** OPA evaluation service wrapper (gRPC/REST), policy bundle management with SHA-256 integrity, evaluation caching (Redis), T2 rule sandbox (V8/WASM: 64MB/100ms), restricted SDK for T2 (readConfig, queryRefData, readCalendar), resource accounting, hot-reload with zero-downtime, canary deployment with shadow comparison, jurisdiction-based policy routing, maker-checker for rule changes, dry-run evaluation, circuit breaker integration (K-18 STRICT profile)
- **Dependencies:** K-02, K-04, K-05, K-07, K-18
- **NFRs:** P99 <10ms evaluation, policy update propagation <5s
- **Concerns:** Compliance-mode fallback (default DENY)

### 3.4 K-04: Plugin Runtime & SDK

- **Purpose:** Tier-aware sandbox isolation for plugins (T1 config, T2 logic, T3 network)
- **Key FRs:** Ed25519 manifest signature verification, checksum validation, T1/T2/T3 tier loaders with escalating isolation, capability-based access control with approval workflow, semantic version compatibility checking, dependency resolution, hot-swap without downtime, state migration, rollback, per-plugin resource quotas, data exfiltration prevention
- **Dependencies:** K-02, K-05, K-07
- **NFRs:** T2 sandbox: 64MB memory, 100ms timeout, no network/FS access
- **Concerns:** Plugin hot-swap state migration complexity

### 3.5 K-05: Event Bus, Event Store & Workflow Orchestration

- **Purpose:** Append-only immutable event store, Kafka outbox pattern, saga orchestration, event replay/projection rebuild
- **Key FRs:** PostgreSQL append-only event store (REVOKE UPDATE/DELETE), auto-increment sequencing, dual-calendar timestamp enrichment (K-15), monthly range partitioning, Avro/Protobuf schema registry with evolution checking, Kafka outbox relay (batch 100, partition key=aggregate_id), consumer group framework with manual offset commit, error classification (transient/permanent) with DLQ routing, Redis-backed idempotency with PostgreSQL fallback, saga definition registry (DAG validation), saga orchestration engine (event-sourced state machine), compensation handler framework (3x retry, exponential backoff), saga timeout with auto-compensation, event replay engine (rate-limited, filterable, cancellable), blue-green projection rebuild, consumer backpressure (pause/resume), producer flow control, schema semantic versioning with breaking change detection
- **Dependencies:** K-07 (bootstrap cycle), K-15, K-19
- **NFRs:** **100K TPS sustained**, P99 publish <2ms, replay 50K events/sec, consumer lag <30s for critical topics, 99.999% uptime
- **Threat Model:** Event tampering, replay attacks, cross-tenant leakage, DDoS via event flood
- **Concerns:** Bootstrap cycle with K-07 (resolved via phased implementation)

### 3.6 K-06: Observability Stack

- **Purpose:** Unified logging/tracing/metrics with SLO framework, ML-based PII detection, AIOps intelligence
- **Key FRs:** Observability SDK core with context management, structured logging with enrichment, metrics helpers, W3C Trace Context propagation, Prometheus metrics per service, Grafana dashboards, Jaeger distributed tracing, OpenSearch centralized logging, SLO/SLA framework with error budgets, PII detection and masking in logs/traces, alerting rules engine with routing/escalation, ML-based metric anomaly detection, log intelligence clustering, intelligent alert noise reduction
- **Dependencies:** K-05, K-07
- **NFRs:** Full distributed tracing, PII never in plaintext logs, SLO tracking per tenant

### 3.7 K-07: Audit Framework

- **Purpose:** Immutable hash-chained audit records with external timestamp anchoring
- **Key FRs:** Audit SDK core API (auto-enrich actor, correlation, dual timestamps), Express/Fastify middleware, PostgreSQL append-only audit table (REVOKE UPDATE/DELETE, monthly partitions), SHA-256 hash chain (genesis="0"×64), chain validation verification, standardized event schema (CREATE/READ/UPDATE/DELETE/LOGIN/LOGOUT/APPROVE/REJECT), CSV/JSON/PDF evidence export, retention policy (default 7yr financial, 5yr operational) with automated enforcement, maker-checker audit linkage, approval chain trail, external hash anchoring (Merkle root every 1000 events or 1hr → blockchain/TSA)
- **Dependencies:** K-05 (bootstrap cycle), K-01, K-15
- **NFRs:** 10K events/sec ingestion, 10-year retention, tamper detection, P99 search <500ms
- **ARB:** FR7 (blockchain anchoring), FR8 (100K event buffer limit)

### 3.8 K-08: Data Governance

- **Purpose:** Data lineage, residency enforcement, classification, quality rules, retention, PII masking
- **Key FRs:** Enterprise data catalog with auto-discovery, lineage tracking for impact analysis, schema registry, data classification (regulatory compliance), tagging, quality rules/scoring/alerting, retention policies with enforcement, right-to-erasure, compliance dashboard, stewardship assignment, dynamic data masking
- **Dependencies:** K-01, K-02, K-05, K-07, K-14
- **NFRs:** Row-Level Security (RLS) per tenant (ARB FR7), automated breach response (ARB FR8)

### 3.9 K-09: AI Governance

- **Purpose:** Model registry, SHAP explainability, drift detection, bias monitoring, HITL review
- **Key FRs:** AI model registry with artifact storage, lifecycle management (DRAFT→VALIDATED→DEPLOYED), SHAP explainability for all predictions, model card generation, prediction audit log, bias detection, fairness monitoring, feature/concept drift detection, retraining pipeline, HITL review workflow, governance policy engine, model risk tiering (TIER_1/2/3)
- **Dependencies:** K-01, K-02, K-05, K-06, K-07
- **NFRs:** Drift auto-rollback within 60s (ARB FR8), prompt injection prevention (ARB FR9)
- **Concerns:** 60s auto-rollback target is ambitious; fallback strategy unclear

### 3.10 K-10: Deployment Abstraction

- **Purpose:** Unified packaging for SaaS/dedicated/on-prem/hybrid/air-gap with zero-downtime upgrades
- **Key FRs:** Deployment orchestrator service, canary strategy (error_rate/latency criteria for 10min before promotion), approval workflow, environment registry, promotion pipeline, instant rollback, deployment history/audit, IaC config drift scanner, IaC validation, horizontal pod autoscaling (custom metrics: K-05 consumer lag, business metrics), resource quota management, deployment dashboard
- **Dependencies:** K-02, K-04, K-06, K-07, PU-004
- **NFRs:** Zero-downtime upgrades, canary auto-rollback

### 3.11 K-11: Unified API Gateway

- **Purpose:** Single entry point for all APIs (REST, GraphQL, gRPC, WebSockets) with rate limiting, schema validation, WAF
- **Key FRs:** Envoy/Istio-based gateway with service registry, API versioning, request/response transformation, JWT validation at gateway, mTLS termination, tenant-aware token bucket rate limiting, internal service bypass, jurisdiction-based routing, geo-routing header injection, OWASP Top 10 WAF rules, request size/payload validation, request schema validation, schema registry
- **Dependencies:** K-01, K-02, K-05, K-06, K-07
- **NFRs:** 50K TPS, P99 <2ms overhead, 99.999% uptime, stateless edge nodes
- **Threat Model:** DDoS, API abuse, injection, MITM, cross-tenant leakage, request smuggling

### 3.12 K-12: Platform SDK

- **Purpose:** Multi-language SDK (Go, Java, Python, TypeScript, C#) for all kernel clients
- **Key FRs:** SDK core abstractions, OpenAPI/event schema code generation, test harness, contract testing (PACT), performance test utilities, plugin scaffold generator, plugin testing utilities, certification tests, auto-documentation, event catalog, developer portal, package registry, migration tooling, observability module
- **Dependencies:** All kernel modules K-01 through K-19
- **NFRs:** <1ms SDK overhead, 10K concurrent clients, 2 major version backward compatibility

### 3.13 K-13: Admin Portal

- **Purpose:** Unified web console for tenant management, config deployment, plugin lifecycle, health dashboards, audit viewing
- **Key FRs:** Admin portal shell (React), K-02 config UI, system health dashboard, user/role/permission management, API key management, plugin registry/deployment/monitoring, audit log explorer/analytics, observability hub, SLA/KPI dashboard, maker-checker task center, schema-driven forms, dynamic value catalogs
- **Dependencies:** K-01, K-02, K-04, K-06, K-07, K-10, K-15
- **NFRs:** Page load <1s, API responses <200ms, 99.99% uptime, micro-frontend architecture

### 3.14 K-14: Secrets Management & Key Vault

- **Purpose:** Unified secure abstraction for all platform secrets with multi-provider vault support
- **Key FRs:** SecretProvider interface with provider registry, HashiCorp Vault KV v2 provider, local encrypted file provider (air-gap, AES-256-GCM), auto-rotation scheduler, zero-downtime database credential rotation, JWT/API key rotation, X.509 certificate issuance/tracking/distribution, break-glass access workflow with forensic reporting, HSM key operations (PKCS#11), HSM-backed JWT signing, secret version history, rollback
- **Dependencies:** K-01, K-02, K-07, K-08
- **NFRs:** P99 <10ms cached / <50ms vault fetch, 10K TPS, 99.999% uptime, secrets never logged
- **Threat Model:** Secret exfiltration, vault compromise, rotation failure causing outage, break-glass abuse, MITM, CMK revocation

### 3.15 K-15: Dual-Calendar Service

- **Purpose:** First-class BS↔Gregorian calendar support at the data layer
- **Key FRs:** JDN-based BS↔Gregorian conversion library (2000-2100 BS range), REST API, DualDate type, storage enforcement middleware, holiday calendar CRUD, business day calculation, fiscal year boundary calculation, fiscal quarter/period, T+n settlement date calculation, configurable weekend/holiday skip logic, edge case handling (32-day months, Chaitra→Baisakh, year boundary), comprehensive accuracy test suite (36,500 days verified)
- **Dependencies:** K-02, K-05, K-07
- **NFRs:** P99 <2ms conversion, 10K TPS, 99.999% uptime, 0 mismatches per 1M conversions
- **ARB:** P2-18 (>500 edge case assertions for 2000-2100 BS)

### 3.16 K-16: Ledger Framework

- **Purpose:** Immutable double-entry ledger primitive for all financial tracking
- **Key FRs:** Append-only schema (REVOKE UPDATE/DELETE, DECIMAL(28,12)), journal creation with balance enforcement (debit==credit per currency), account balance materialization, journal reversal (contra-entries), storage-level immutability verification, hash chain per account for tamper detection, as-of-date balance queries (BS/Gregorian), periodic EOD/EOY balance snapshots, balance history report, currency registry with precision rules, multi-currency journal posting, multi-asset account support (CASH/SECURITY/UNIT), internal reconciliation engine, break aging and escalation, external statement reconciliation (CSV/MT940/XML), chart of accounts management, maker-checker for account creation, Money value object (prevents floating-point), rounding allocation for splits
- **Dependencies:** K-02, K-05, K-15
- **NFRs:** 10K TPS posting, P99 <5ms commit, DECIMAL(28,12) precision, nightly balance verification (ARB P0-03)
- **Precision Rules:** NPR 4dp, USD/EUR 2dp, crypto 8dp, securities 0dp, HALF_EVEN rounding
- **Threat Model:** Ledger tampering, double-posting, salami slicing, unauthorized accounts, recon injection, balance leakage, DoS

### 3.17 K-17: Distributed Transaction Coordinator

- **Purpose:** Cross-stream saga ordering and data consistency (Order→Position→Ledger→Settlement)
- **Key FRs:** Outbox table with relay service (SELECT FOR UPDATE SKIP LOCKED), cleanup/monitoring, exactly-once publish guarantee (idempotent Kafka producer), version vector data structure (BEFORE/AFTER/CONCURRENT), causal ordering middleware, conflict detection (default last-writer-wins, pluggable), saga policies registered in K-05 saga registry, DTC saga coordination layer using K-05 engine, compensation callbacks registered with K-05 framework, saga monitoring via K-05 lifecycle events, K-05 idempotency store extension with DTC namespace (7-day TTL), idempotency middleware on DTC command API, saga policy management REST API, saga instance query/intervention API
- **Dependencies:** K-02, K-05, K-06, K-07, K-16
- **NFRs:** Outbox relay lag <100ms P99, 50K sagas/sec, causal consistency, 10-year transaction log retention
- **ARB:** P0-01 (Event Ordering Guarantees)
- **Concerns:** Version vector handling for multi-key aggregates; STUCK saga manual resolution SLA undefined

### 3.18 K-18: Resilience Patterns Library

- **Purpose:** Standardized SDK for circuit breakers, bulkheads, retries, timeout management
- **Key FRs:** Circuit breaker state machine (CLOSED→OPEN→HALF_OPEN), execution wrapper with fallback, pre-defined profiles (STRICT/STANDARD/RELAXED, configurable via K-02), thread pool bulkhead, semaphore-based bulkhead (async Node.js), retry with exponential backoff and jitter, retry context propagation, cascading timeout budgets (X-Request-Deadline header), per-route timeout configuration, composite resilience profiles (TRADING_CRITICAL/STANDARD/BACKGROUND_JOB), profile monitoring dashboard, dependency health aggregator, dependency Grafana dashboard
- **Dependencies:** K-02, K-05, K-06, K-07
- **NFRs:** SDK overhead <0.1ms, zero allocation in hot path, in-process (no network calls)
- **ARB:** P0-02 (Circuit Breaker for K-03)

### 3.19 K-19: DLQ Management & Event Replay

- **Purpose:** Poison message handling, structured replay, ML failure classification
- **Key FRs:** DLQ service with topic isolation, metrics, single-event and bulk replay, scheduled auto-retry, payload inspector, payload transformation, discard workflow, archive/retention, DLQ dashboard, SLA tracking, poison pill detection, ML failure pattern classifier, AI-powered overflow prevention
- **Dependencies:** K-05, K-06, K-07
- **NFRs:** Transient auto-retry 3x, permanent errors → DLQ immediately
- **ARB:** P0-04 (DLQ Management)

---

## 4. Kernel Stories — Verified Inventory

### 4.1 Story Count Summary (Verified)

| Milestone | Sprints | Kernel Epics | Stories | SPs |
|-----------|---------|-------------|---------|-----|
| **M1A** Foundation | 1–2 | K-02, K-05, K-07, K-15 | 78 | 210 |
| **M1B** Core Services | 3–4 | K-01, K-03, K-04, K-06, K-11, K-14, K-16, K-17, K-18 | 147 | 361 |
| **M3A** Advanced Kernel | 11–12 | K-08, K-09, K-10, K-19 | 56 | 164 |
| **M3B** Platform Tools | 13–14 | K-12, K-13 | 29 | 72 |
| **TOTAL** | | **19 modules** | **310** | **807** |

> **Note:** Milestones 2A and 2B (Sprints 5–10) contain 229 domain stories (D-01 through D-14) with 0 kernel stories.

### 4.2 Milestone 1A — Kernel Foundation (78 Stories / 210 SP)

#### K-05 Event Bus (32 stories, 100 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K05-001 | Create event store schema with append-only table | 5 | 1 |
| K05-002 | Implement appendEvent write API | 5 | 1 |
| K05-003 | Implement getEventsByAggregate read API | 3 | 1 |
| K05-004 | Add dual-calendar timestamp enrichment | 2 | 1 |
| K05-005 | Implement event store range partitioning (monthly) | 3 | 2 |
| K05-006 | Implement event schema registry storage | 3 | 2 |
| K05-007 | Implement event validation middleware | 3 | 2 |
| K05-008 | Implement schema evolution backward compatibility check | 2 | 2 |
| K05-009 | Implement Kafka publisher with outbox relay | 5 | 2 |
| K05-010 | Implement consumer group framework | 5 | 2 |
| K05-011 | Implement offset management and checkpoint | 3 | 2 |
| K05-012 | Implement consumer error handling and DLQ routing | 3 | 2 |
| K05-013 | Implement Redis-backed idempotency dedup store | 3 | 2 |
| K05-014 | Implement idempotency guard middleware | 2 | 2 |
| K05-015 | Implement PostgreSQL fallback dedup store | 2 | 2 |
| K05-016 | Implement saga definition registry | 3 | 2 |
| K05-017 | Implement saga orchestration engine | 8 | 2 |
| K05-018 | Implement compensation handler framework | 5 | 2 |
| K05-019 | Implement saga timeout and auto-compensation | 3 | 2 |
| K05-020 | Implement saga state persistence and recovery | 3 | 2 |
| K05-021 | Implement event replay engine | 5 | 2 |
| K05-022 | Implement projection rebuild from events | 3 | 2 |
| K05-023 | Implement selective replay with filtering | 3 | 2 |
| K05-024 | Implement replay progress tracking and dashboard | 2 | 2 |
| K05-025 | Implement consumer backpressure with pause/resume | 3 | 2 |
| K05-026 | Implement producer flow control | 2 | 2 |
| K05-027 | Implement event bus DLQ topic integration | 3 | 2 |
| K05-028 | Implement DLQ routing configuration | 2 | 2 |
| K05-029 | Implement schema versioning with semantic versioning | 2 | 2 |
| K05-030 | Implement breaking change detection and migration tooling | 2 | 2 |
| K05-031 | Implement saga trace correlation ID propagation | 2 | 2 |
| K05-032 | Implement cross-service saga trace dashboard | 2 | 2 |

#### K-07 Audit Framework (16 stories, 38 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K07-001 | Implement audit SDK core API | 3 | 1 |
| K07-002 | Implement Express/Fastify audit middleware | 2 | 1 |
| K07-003 | Create audit log table with hash chain schema | 3 | 1 |
| K07-004 | Implement SHA-256 hash computation | 3 | 1 |
| K07-005 | Implement chain validation verification function | 2 | 1 |
| K07-006 | Define standardized audit event schema | 2 | 1 |
| K07-007 | Implement automatic audit enrichment | 2 | 1 |
| K07-008 | Implement CSV export | 2 | 2 |
| K07-009 | Implement JSON export with advanced filtering | 2 | 2 |
| K07-010 | Implement PDF evidence package generation | 3 | 2 |
| K07-011 | Implement retention policy configuration | 2 | 2 |
| K07-012 | Implement automated retention enforcement | 3 | 2 |
| K07-013 | Implement maker-checker audit linkage | 2 | 2 |
| K07-014 | Implement approval chain audit trail | 2 | 2 |
| K07-015 | Implement external hash anchoring endpoint | 2 | 2 |
| K07-016 | Implement hash anchor verification | 2 | 2 |

#### K-02 Configuration Engine (17 stories, 42 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K02-001 | Implement config schema registration API | 3 | 1 |
| K02-002 | Implement config value validation against schema | 3 | 1 |
| K02-003 | Implement config schema browsing and documentation | 2 | 1 |
| K02-004 | Implement GLOBAL→JURISDICTION hierarchy levels | 3 | 1 |
| K02-005 | Implement TENANT→USER→SESSION hierarchy levels | 3 | 1 |
| K02-006 | Implement config merge strategy with precedence | 2 | 1 |
| K02-007 | Implement hot-reload config change detection | 3 | 2 |
| K02-008 | Implement canary rollout for config changes | 3 | 2 |
| K02-009 | Implement config change rollback mechanism | 2 | 2 |
| K02-010 | Implement dual-calendar effective date support | 3 | 2 |
| K02-011 | Implement config temporal queries | 2 | 2 |
| K02-012 | Implement air-gap config bundle generation | 3 | 2 |
| K02-013 | Implement Ed25519 config bundle signing/verification | 2 | 2 |
| K02-014 | Implement maker-checker workflow for config changes | 3 | 2 |
| K02-015 | Implement config change approval notifications | 2 | 2 |
| K02-016 | Implement config CQRS write (command) side | 2 | 2 |
| K02-017 | Implement config CQRS read (query) side | 2 | 2 |

#### K-15 Dual-Calendar Service (13 stories, 30 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K15-001 | Implement BS↔Gregorian JDN conversion library | 5 | 1 |
| K15-002 | Implement BS month length lookup table | 2 | 1 |
| K15-003 | Implement conversion REST API endpoint | 2 | 1 |
| K15-004 | Implement DualDate type and generation utility | 2 | 1 |
| K15-005 | Implement storage enforcement middleware | 2 | 1 |
| K15-006 | Implement holiday calendar CRUD | 2 | 1 |
| K15-007 | Implement holiday-aware business day calculation | 2 | 1 |
| K15-008 | Implement BS fiscal year boundary calculation | 2 | 2 |
| K15-009 | Implement fiscal quarter and period calculation | 2 | 2 |
| K15-010 | Implement T+n settlement date calculation | 3 | 2 |
| K15-011 | Implement weekend and holiday skip logic | 2 | 2 |
| K15-012 | Implement BS calendar edge case handling | 2 | 2 |
| K15-013 | Implement comprehensive conversion accuracy test suite | 2 | 2 |

### 4.3 Milestone 1B — Core Services (147 Stories / 361 SP)

#### K-01 IAM (23 stories, 62 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K01-001 | Implement OAuth 2.0 token endpoint (client_credentials) | 5 | 3 |
| K01-002 | Implement authorization_code flow with PKCE | 5 | 3 |
| K01-003 | Implement refresh token rotation with family tracking | 3 | 3 |
| K01-004 | Implement MFA enrollment and TOTP verification | 3 | 3 |
| K01-005 | Implement brute force protection and account lockout | 2 | 3 |
| K01-006 | Implement Redis-backed session store | 3 | 3 |
| K01-007 | Implement concurrent session limits and eviction | 2 | 3 |
| K01-008 | Implement cross-tenant session isolation | 2 | 3 |
| K01-009 | Implement role-permission data model and management API | 3 | 3 |
| K01-010 | Implement authorization middleware with Redis-cached permissions | 3 | 4 |
| K01-011 | Integrate ABAC policy evaluation via K-03 | 3 | 4 |
| K01-012 | Implement super-admin and break-glass access controls | 2 | 4 |
| K01-013 | Define T3 national ID verification plugin interface | 2 | 4 |
| K01-014 | Implement Nepal NID adapter (default T3 plugin) | 3 | 4 |
| K01-015 | Implement mTLS service identity provisioning | 3 | 4 |
| K01-016 | Implement service-to-service JWT propagation | 2 | 4 |
| K01-017 | Integrate IAM events with audit framework | 2 | 3 |
| K01-018 | Implement IAM audit query API | 2 | 4 |
| K01-019 | Implement beneficial ownership data model | 3 | 4 |
| K01-020 | Implement beneficial ownership traversal API | 3 | 4 |
| K01-021 | Implement ownership change notification/compliance triggers | 2 | 4 |
| K01-022 | Implement auth endpoint rate limiting | 2 | 3 |
| K01-023 | Implement login anomaly detection | 3 | 4 |

#### K-03 Rules Engine (14 stories, 30 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K03-001 | Implement OPA evaluation service wrapper | 3 | 4 |
| K03-002 | Implement policy bundle management | 2 | 4 |
| K03-003 | Implement policy evaluation caching | 2 | 4 |
| K03-004 | Implement T2 rule execution sandbox | 3 | 4 |
| K03-005 | Implement T2 rule API surface (restricted SDK) | 2 | 4 |
| K03-006 | Implement T2 rule resource accounting | 2 | 4 |
| K03-007 | Implement rule pack hot-reload | 2 | 4 |
| K03-008 | Implement rule pack canary deployment | 2 | 4 |
| K03-009 | Implement jurisdiction-based policy routing | 3 | 4 |
| K03-010 | Implement jurisdiction rule pack registry | 2 | 4 |
| K03-011 | Implement rule change approval workflow | 2 | 4 |
| K03-012 | Implement rule dry-run evaluation | 2 | 4 |
| K03-013 | Implement rules engine circuit breaker | 2 | 4 |
| K03-014 | Implement rules engine fallback decision logging | 2 | 4 |

#### K-04 Plugin Runtime (15 stories, 33 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K04-001 | Implement plugin manifest schema and Ed25519 verification | 3 | 3 |
| K04-002 | Implement plugin checksum validation | 2 | 3 |
| K04-003 | Implement T1 config plugin loader | 2 | 3 |
| K04-004 | Implement T2 sandbox plugin runtime | 3 | 4 |
| K04-005 | Implement T3 network plugin runtime | 3 | 4 |
| K04-006 | Implement tier enforcement and promotion validation | 2 | 4 |
| K04-007 | Implement capability declaration and verification | 2 | 4 |
| K04-008 | Implement capability approval workflow | 2 | 4 |
| K04-009 | Implement semantic version compatibility checking | 2 | 4 |
| K04-010 | Implement plugin dependency resolution | 2 | 4 |
| K04-011 | Implement plugin hot-swap mechanism | 3 | 4 |
| K04-012 | Implement plugin state migration | 2 | 4 |
| K04-013 | Implement plugin rollback | 2 | 4 |
| K04-014 | Implement per-plugin resource quotas | 2 | 4 |
| K04-015 | Implement data exfiltration prevention | 3 | 4 |

#### K-06 Observability Stack (22 stories, 57 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K06-001 | Implement observability SDK core with context management | 3 | 3 |
| K06-002 | Implement structured logging with context enrichment | 2 | 3 |
| K06-003 | Implement metrics helper library | 2 | 3 |
| K06-004 | Implement W3C Trace Context propagation | 2 | 3 |
| K06-005 | Implement baggage propagation for custom context | 2 | 3 |
| K06-006 | Implement Prometheus metrics endpoint per service | 2 | 3 |
| K06-007 | Implement platform-wide Grafana dashboards | 3 | 3 |
| K06-008 | Implement custom business metrics collection | 2 | 3 |
| K06-009 | Implement OpenTelemetry tracing instrumentation | 3 | 3 |
| K06-010 | Implement trace sampling strategy | 2 | 3 |
| K06-011 | Implement log shipping to OpenSearch | 2 | 3 |
| K06-012 | Implement OpenSearch Dashboards and saved searches | 2 | 4 |
| K06-013 | Implement SLO definition and tracking | 3 | 4 |
| K06-014 | Implement error budget calculation and tracking | 3 | 4 |
| K06-015 | Implement SLA reporting for tenants | 2 | 4 |
| K06-016 | Implement PII detection rules | 2 | 4 |
| K06-017 | Implement PII masking in logs and traces | 3 | 4 |
| K06-018 | Implement alerting rules engine | 2 | 4 |
| K06-019 | Implement alert routing and escalation | 2 | 4 |
| K06-020 | Implement ML-based metric anomaly detection | 5 | 4 |
| K06-021 | Implement log intelligence clustering | 3 | 4 |
| K06-022 | Implement intelligent alert noise reduction | 5 | 4 |

#### K-11 API Gateway (13 stories, 30 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K11-001 | Implement Envoy/Istio-based API Gateway with service registry | 3 | 3 |
| K11-002 | Implement API versioning strategy | 2 | 3 |
| K11-003 | Implement request/response transformation | 2 | 3 |
| K11-004 | Implement JWT validation at gateway | 2 | 3 |
| K11-005 | Implement mTLS termination and client cert validation | 2 | 4 |
| K11-006 | Implement tenant-aware token bucket rate limiting | 3 | 4 |
| K11-007 | Implement rate limit bypass for internal services | 2 | 4 |
| K11-008 | Implement jurisdiction-based request routing | 3 | 4 |
| K11-009 | Implement geo-routing header injection | 2 | 4 |
| K11-010 | Implement OWASP Top 10 protection rules | 3 | 4 |
| K11-011 | Implement request body size and payload validation | 2 | 4 |
| K11-012 | Implement request schema validation at gateway | 2 | 4 |
| K11-013 | Implement schema registry and version management | 2 | 4 |

#### K-14 Secrets Management (14 stories, 35 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K14-001 | Define SecretProvider interface and provider registry | 3 | 3 |
| K14-002 | Implement HashiCorp Vault KV v2 provider | 3 | 3 |
| K14-003 | Implement local encrypted file provider (air-gap) | 2 | 3 |
| K14-004 | Implement secret rotation scheduler | 3 | 3 |
| K14-005 | Implement database credential rotation | 3 | 3 |
| K14-006 | Implement API key and JWT signing key rotation | 2 | 3 |
| K14-007 | Implement X.509 certificate issuance and tracking | 3 | 4 |
| K14-008 | Implement certificate distribution to services | 2 | 4 |
| K14-009 | Implement break-glass secret access workflow | 3 | 4 |
| K14-010 | Implement break-glass forensic reporting | 2 | 4 |
| K14-011 | Implement HSM key operations abstraction | 3 | 4 |
| K14-012 | Implement HSM-backed JWT signing | 2 | 4 |
| K14-013 | Implement secret version history | 2 | 4 |
| K14-014 | Implement secret rollback | 2 | 4 |

#### K-16 Ledger Framework (19 stories, 50 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K16-001 | Create ledger schema with append-only enforcement | 5 | 3 |
| K16-002 | Implement journal creation with balance enforcement | 5 | 3 |
| K16-003 | Implement account balance materialization | 3 | 3 |
| K16-004 | Implement journal reversal (correction entries) | 3 | 4 |
| K16-005 | Implement storage-level immutability verification | 2 | 3 |
| K16-006 | Implement ledger entry hash chain | 3 | 3 |
| K16-007 | Implement as-of-date balance query | 3 | 4 |
| K16-008 | Implement periodic balance snapshots | 3 | 4 |
| K16-009 | Implement balance history report | 2 | 4 |
| K16-010 | Implement currency registry and precision rules | 2 | 3 |
| K16-011 | Implement multi-currency journal posting | 3 | 4 |
| K16-012 | Implement multi-asset account support | 3 | 4 |
| K16-013 | Implement internal reconciliation engine | 3 | 4 |
| K16-014 | Implement break aging and escalation | 2 | 4 |
| K16-015 | Implement external statement reconciliation | 3 | 4 |
| K16-016 | Implement chart of accounts management | 2 | 3 |
| K16-017 | Implement account creation workflow with maker-checker | 2 | 4 |
| K16-018 | Implement monetary amount value object | 2 | 3 |
| K16-019 | Implement rounding allocation for split payments | 2 | 4 |

#### K-17 Distributed Transaction Coordinator (14 stories, 36 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K17-001 | Implement outbox table and relay service | 3 | 3 |
| K17-002 | Implement outbox cleanup and monitoring | 2 | 3 |
| K17-003 | Implement exactly-once publish guarantee | 3 | 3 |
| K17-004 | Implement version vector data structure | 3 | 4 |
| K17-005 | Implement causal ordering middleware | 3 | 4 |
| K17-006 | Implement conflict detection for concurrent writes | 2 | 4 |
| K17-007 | Define DTC saga policies in K-05 registry | 3 | 4 |
| K17-008 | Implement DTC saga coordination layer using K-05 | 5 | 4 |
| K17-009 | Register DTC compensation callbacks with K-05 | 3 | 4 |
| K17-010 | Monitor DTC saga execution via K-05 lifecycle events | 2 | 4 |
| K17-011 | Extend K-05 idempotency store with DTC namespace | 2 | 4 |
| K17-012 | Apply K-05 idempotency middleware to DTC command API | 2 | 4 |
| K17-013 | Implement DTC saga policy management REST API | 2 | 4 |
| K17-014 | Implement DTC saga instance query and intervention API | 2 | 4 |

#### K-18 Resilience Patterns (13 stories, 28 SP)

| ID | Title | SP | Sprint |
|----|-------|----|--------|
| K18-001 | Implement circuit breaker state machine | 3 | 3 |
| K18-002 | Implement circuit breaker execution wrapper | 2 | 3 |
| K18-003 | Implement pre-defined circuit breaker profiles | 2 | 3 |
| K18-004 | Implement thread pool bulkhead | 2 | 3 |
| K18-005 | Implement semaphore-based bulkhead | 2 | 3 |
| K18-006 | Implement retry with exponential backoff and jitter | 2 | 3 |
| K18-007 | Implement retry context propagation | 2 | 3 |
| K18-008 | Implement cascading timeout budgets | 3 | 4 |
| K18-009 | Implement timeout configuration per route | 2 | 4 |
| K18-010 | Implement composite resilience profiles | 2 | 4 |
| K18-011 | Implement resilience profile monitoring | 2 | 4 |
| K18-012 | Implement dependency health check aggregator | 2 | 4 |
| K18-013 | Implement dependency health Grafana dashboard | 2 | 4 |

### 4.4 Milestone 3A — Advanced Kernel (56 Stories / 164 SP)

#### K-08 Data Governance (14 stories, 43 SP)

| ID | Title | SP |
|----|-------|----|
| K08-001 | Implement enterprise data catalog | 5 |
| K08-002 | Implement data lineage tracking | 5 |
| K08-003 | Implement schema registry | 3 |
| K08-004 | Implement data classification | 3 |
| K08-005 | Implement data tagging | 2 |
| K08-006 | Implement data quality rules | 5 |
| K08-007 | Data quality score dashboard | 2 |
| K08-008 | Data quality alerting | 2 |
| K08-009 | Implement retention policies | 3 |
| K08-010 | Implement retention enforcement | 3 |
| K08-011 | Implement right-to-erasure | 3 |
| K08-012 | Data governance compliance dashboard | 2 |
| K08-013 | Data stewardship assignment | 2 |
| K08-014 | Implement dynamic data masking | 3 |

#### K-09 AI Governance (15 stories, 47 SP)

| ID | Title | SP |
|----|-------|----|
| K09-001 | Implement AI model registry | 3 |
| K09-002 | Implement model artifact storage | 2 |
| K09-003 | Implement model lifecycle management | 3 |
| K09-004 | Implement SHAP explainability | 5 |
| K09-005 | Implement model card generation | 2 |
| K09-006 | Implement prediction audit log | 3 |
| K09-007 | Implement bias detection | 5 |
| K09-008 | Implement fairness monitoring | 3 |
| K09-009 | Implement feature drift detection | 3 |
| K09-010 | Implement concept drift detection | 3 |
| K09-011 | Implement retraining pipeline | 3 |
| K09-012 | Implement HITL review workflow | 3 |
| K09-013 | Implement governance policy engine | 3 |
| K09-014 | Implement model risk tiering | 2 |
| K09-015 | Implement risk assessment report | 2 |

#### K-19 DLQ Management (15 stories, 38 SP)

| ID | Title | SP |
|----|-------|----|
| K19-001 | Implement DLQ service | 3 |
| K19-002 | Implement topic isolation | 2 |
| K19-003 | Implement DLQ metrics | 2 |
| K19-004 | Implement single-event replay | 3 |
| K19-005 | Implement bulk replay | 3 |
| K19-006 | Implement scheduled auto-retry | 2 |
| K19-007 | Implement payload inspector | 2 |
| K19-008 | Implement payload transformation | 3 |
| K19-009 | Implement discard workflow | 2 |
| K19-010 | Implement archive and retention | 2 |
| K19-011 | Implement DLQ dashboard | 3 |
| K19-012 | Implement SLA tracking | 2 |
| K19-013 | Implement poison pill detection | 3 |
| K19-014 | ML failure pattern classifier | 3 |
| K19-015 | AI-powered overflow prevention | 3 |

#### K-10 Deployment Orchestrator (12 stories, 36 SP)

| ID | Title | SP |
|----|-------|----|
| K10-001 | Deployment orchestrator service | 3 |
| K10-002 | Canary deployment strategy | 5 |
| K10-003 | Deployment approval workflow | 2 |
| K10-004 | Environment registry | 3 |
| K10-005 | Environment promotion pipeline | 3 |
| K10-006 | Instant rollback mechanism | 3 |
| K10-007 | Deployment history and audit | 2 |
| K10-008 | Config drift scanner | 3 |
| K10-009 | IaC validation | 2 |
| K10-010 | Horizontal pod autoscaling | 3 |
| K10-011 | Resource quota management | 2 |
| K10-012 | Deployment dashboard | 2 |

### 4.5 Milestone 3B — Platform Tools (29 Kernel Stories / 72 SP)

#### K-13 Admin Portal (14 stories, 37 SP)

| ID | Title | SP |
|----|-------|----|
| K13-001 | Admin portal shell | 3 |
| K13-002 | K-02 config UI | 3 |
| K13-003 | System health dashboard | 2 |
| K13-004 | User management UI | 2 |
| K13-005 | Role and permission UI | 3 |
| K13-006 | API key management | 2 |
| K13-007 | Plugin registry UI | 3 |
| K13-008 | Plugin deployment wizard | 3 |
| K13-009 | Plugin sandbox monitoring | 2 |
| K13-010 | Audit log explorer UI | 3 |
| K13-011 | Audit analytics | 2 |
| K13-012 | Observability hub | 3 |
| K13-013 | SLA/KPI dashboard | 2 |
| K13-014 | Maker-checker task center | 3 |

#### K-12 Platform SDK (15 stories, 35 SP)

| ID | Title | SP |
|----|-------|----|
| K12-001 | SDK core abstractions | 3 |
| K12-002 | OpenAPI code generation | 3 |
| K12-003 | Event schema code gen | 2 |
| K12-004 | Test harness SDK | 3 |
| K12-005 | Contract testing framework | 3 |
| K12-006 | Performance test utilities | 2 |
| K12-007 | Plugin scaffold generator | 3 |
| K12-008 | Plugin testing utilities | 2 |
| K12-009 | Plugin certification tests | 3 |
| K12-010 | Auto-documentation from OpenAPI | 2 |
| K12-011 | Event catalog documentation | 2 |
| K12-012 | Developer portal | 3 |
| K12-013 | SDK package registry | 2 |
| K12-014 | SDK migration tooling | 2 |
| K12-015 | SDK observability module | 2 |

> **Note:** Milestone 3B also includes W-01 (16 stories) and W-02 (13 stories) which are cross-cutting workflow modules, not kernel.

---

## 5. TDD Test Specifications — 314+ Test Cases

### 5.1 Test Inventory

| Spec Document | Kernel Modules | TCs | Coverage |
|---------------|---------------|-----|---------|
| Phase 0 Bootstrap | Repo, Templates, Contracts, CI | 25 | Monorepo, service scaffolding, shared contracts |
| K-02 Config Engine (Expanded) | K-02 (17 stories) | 56 | Schema, maker-checker, hierarchy, temporal, hot-reload, cache, T1 packs |
| K-05 Event Bus (Expanded) | K-05 (32 stories) | 68 | Envelope, schemas, store, Kafka, consumers, replay, sagas, idempotency, DLQ, perf |
| K-07 Audit Framework (Expanded) | K-07 (16 stories) | 52 | Validation, hash chain, tamper detection, blockchain anchoring, export, retention |
| K-15 Dual-Calendar | K-15 (13 stories) | 37 | Conversion, holidays, business days, settlement, fiscal year, batch |
| Phase 2 Kernel Completion | 16 remaining modules | 76 | All remaining kernel + cross-module scenarios |
| **TOTAL** | **All 19 modules** | **314+** | |

### 5.2 Phase 0 Bootstrap (25 TCs)

- **RS_TC_001–004**: Repository structure creation and validation (monorepo)
- **ST_TC_001–005**: Service template generation (Java, Node.js, Python), naming
- **SC_TC_001–005**: Shared contracts (event envelope, dual-date, protobuf/OpenAPI)
- **LR_TC_001–005**: Local runtime (Docker Compose, health checks, dependencies)
- **CI_TC_001–005**: CI pipeline (lint, test, security scan, fail-fast)

### 5.3 K-02 Configuration Engine (56 TCs)

| Group | TCs | Coverage |
|-------|-----|---------|
| SM (Schema Management) | 10 | Registration, validation, versioning, deprecation |
| PA (Pack Approval) | 14 | Maker-checker workflow, self-approval prevention |
| HR (Hierarchical Resolution) | 14 | 5-level hierarchy, cross-tenant isolation |
| TR (Temporal Resolution) | 10 | BS/Gregorian effective dates, future auto-activation |
| HN (Hot Reload) | 8 | Config watch, K-05 events, cache invalidation |
| HB (History & Rollback) | 6 | Immutable history, version queries, rollback |
| T1 (T1 Pack Loading) | 8 | Signature validation, checksum, startup loading |
| CM (Cache Management) | 6 | Redis caching, TTL, fallback to DB |
| INT (Integration) | 8 | K-05 events, K-07 audit, K-15 dual-calendar |

### 5.4 K-05 Event Bus (68 TCs)

| Group | TCs | Coverage |
|-------|-----|---------|
| EE (Event Envelope) | 10 | Validation, dual-calendar, causality/correlation IDs |
| SM (Schema Management) | 12 | Avro/Protobuf, evolution, field validation, deprecation |
| ES (Event Store) | 12 | Append-only, ordering, monthly partitioning, 10K bulk |
| PB (Publication) | 10 | Kafka outbox, batching (100), mTLS, partition key |
| SB (Subscription) | 10 | Consumer groups, rebalance, offset management |
| RP (Replay) | 8 | Blue-green rebuild, progress tracking, partial rebuild |
| SG (Saga Orchestration) | 15 | Registry, DAG validation, execution, compensation, timeout |
| ID (Idempotency) | 8 | Redis with PostgreSQL fallback, X-Idempotency-Key |
| CG (Consumer Groups) | 8 | Partition distribution, offset commits, lag monitoring |
| DLQ | 8 | Failed routing, metadata, threshold, poison message |
| PERF (Performance) | 8 | P99 <2ms, 100K TPS, replay 50K/sec, 100 sagas in 5s |
| SEC (Security) | 6 | mTLS, tenant isolation, payload encryption, JWT |

### 5.5 K-07 Audit Framework (52 TCs)

| Group | TCs | Coverage |
|-------|-----|---------|
| AE (Audit Event Mgmt) | 10 | Schema validation, dual-calendar, PII detection/redaction |
| AP (Audit Persistence) | 12 | Append-only, sequential, hash computation, 10K/sec |
| HV (Hash Chain Verification) | 10 | Full chain, tamper detection, blockchain anchoring (ARB FR7) |
| AS (Audit Search) | 10 | Full-text, pagination, cursor, multi-field, aggregates |
| EE (Evidence Export) | 10 | >1M records, encryption, hash verification, formats |
| MC (Maker-Checker) | 8 | Workflow linkage, split roles, emergency override |
| RC (Retention & Compliance) | 8 | 10-year enforcement, archive restoration, legal holds |
| CI (Cross-Module) | 8 | K-05 events, K-02 config, K-15 calendar, K-08 isolation |

### 5.6 K-15 Dual-Calendar (37 TCs)

| Group | TCs | Coverage |
|-------|-----|---------|
| DC (Date Conversion) | 9 | BS→Gregorian, Gregorian→BS, round-trip, bounds, leap year |
| DD (DualDate Generation) | 5 | From timestamp, formatting, component extraction |
| FY (Fiscal Year) | 5 | Calculate FY, quarters, boundaries, format labels |
| HM (Holiday Management) | 5 | Lookup, calendar updates, jurisdiction variance, caching |
| BD (Business Day) | 5 | Weekday evaluation, weekend rules, holiday combining, <2ms |
| SD (Settlement Date) | 5 | T+n, skip weekends/holidays, long settlement, tracking |
| BO (Batch Operations) | 4 | Bulk conversion, mixed valid/invalid, performance |
| Real-World Scenarios | 2 suites | Nepal trading week, fiscal year reporting |

### 5.7 Phase 2 Kernel Completion (76 TCs)

Covers 16 remaining kernel modules with 4–8 TCs each plus 6 cross-module scenarios:

| Module | TCs | Key Scenarios |
|--------|-----|--------------|
| K-01 IAM | 8 | Auth, RBAC, JWT, MFA, sessions |
| K-14 Secrets | 4 | Storage, rotation, access, audit |
| K-03 Rules | 4 | OPA evaluation, updates, caching |
| K-04 Plugins | 6 | T1/T2/T3 loading, isolation, signatures |
| K-06 Observability | 4 | Metrics, tracing, logging, alerting |
| K-18 Resilience | 4 | Circuit breaker, retry, bulkhead, fallback |
| K-19 DLQ | 4 | Routing, replay, poison message, monitoring |
| K-16 Ledger | 4 | Double-entry, balance, trial balance |
| K-17 DTC | 4 | Saga orchestration, compensation, timeouts |
| K-08 Data Governance | 4 | RLS, policies, classification |
| K-09 AI Governance | 4 | Model governance, LLM boundaries, prompt injection |
| K-10 Deployment | 4 | Promotion, rollback, abstraction |
| K-11 Gateway | 4 | Routing, rate limiting, validation, telemetry |
| K-13 Portal | 4 | Auth, RBAC, dual-calendar UI, maker-checker |
| PU-004 Manifest | 4 | State management, compatibility, versioning |
| K-12 SDK | 4 | Contracts, Java, TypeScript, compatibility |
| Cross-Module | 6 | End-to-end integration across readiness gates |

### 5.8 ARB Findings with Test Coverage

| ARB ID | Finding | Module | Test Coverage |
|--------|---------|--------|-------------|
| P0-01 | Event Ordering for Cross-Stream Sagas | K-17 | Outbox relay, version vectors, compensation |
| P0-02 | No Circuit Breaker for K-03 | K-18, K-03 | K03-013, K18-001 through K18-003 |
| P0-03 | Ledger Nightly Verification | K-16 | Nightly recompute, imbalance alerts |
| P0-04 | DLQ Management | K-19 | Full DLQ service, poison pill, ML classifier |
| FR7 | External Blockchain Anchoring | K-07 | HV_TC (Merkle root every 1000 events or 1hr) |
| FR8 | Buffer Limit Enforcement | K-07 | AP_TC (100K event max, rejection/alerts) |
| FR8 | Precision/Rounding Rules | K-16 | Fixed-point, HALF_EVEN, per-currency precision |
| FR9 | Mid-Session Config Changes | K-02 | HN_TC (graceful handling) |
| FR9 | Plugin Resource Quotas | K-04 | K04-014 (per-plugin limits) |
| FR9 | PII Detection | K-06 | K06-016, K06-017 |
| FR9 | Circuit Breaker for K-03 | K-18 | K18 profile integration |
| FR10 | Performance Under Load | K-05 | PERF_TC (100K TPS sustained) |
| FR10 | Dual-Calendar Edge Cases | K-15 | 500+ assertions (2000-2100 BS) |
| FR11 | Auth Rate Limiting | K-01 | K01-022 (per-IP, per-tenant, global) |
| FR12 | Login Anomaly Detection | K-01 | K01-023 (device, IP, travel detection) |
| FR13 | Blue-Green Projection Rebuild | K-05 | RP_TC (atomic swap, zero downtime) |
| P1-14 | Request Size Limits | K-11 | K11-011 (10MB API, 100MB upload, 1MB WS) |
| P2-18 | 500+ Edge Case Assertions | K-15 | K15-013 (36,500 day accuracy) |

---

## 6. Dependency Matrix & Critical Path

### 6.1 Module Dependencies

| Module | Depends On | Depended By |
|--------|-----------|-------------|
| K-02 Config | (foundational) | 15+ modules |
| K-05 Event Bus | K-07 (cycle) | **25+ modules** |
| K-07 Audit | K-05 (cycle) | **22+ modules** |
| K-06 Observability | K-05, K-07 | 20+ modules |
| K-01 IAM | K-02, K-05, K-07 | **18+ modules** |
| K-11 API Gateway | K-01, K-02, K-05, K-06, K-07 | 18+ modules |
| K-15 Dual-Calendar | K-02, K-05 | 12+ modules |
| K-14 Secrets | K-01, K-02, K-07, K-08 | 15+ modules |
| K-18 Resilience | K-02, K-05, K-06 | 15+ modules |
| K-03 Rules | K-02, K-04, K-05, K-07 | 10+ modules |
| K-17 DTC | K-05, K-06, K-07, K-16 | 10+ modules |
| K-04 Plugin Runtime | K-02, K-05, K-07 | 8+ modules |
| K-16 Ledger | K-02, K-05, K-15 | 8+ modules |
| K-19 DLQ | K-05, K-06, K-07 | 8+ modules |
| K-08 Data Governance | K-01, K-02, K-05, K-07, K-14 | 12+ modules |
| K-09 AI Governance | K-01, K-02, K-05, K-06, K-07 | 15+ modules |
| K-13 Admin Portal | K-01, K-02, K-06, K-07, K-10 | 8+ modules |
| K-10 Deployment | K-02, K-04, K-06, K-07, PU-004 | 5+ modules |
| K-12 Platform SDK | All kernel (K-01–K-19) | **42 modules (100%)** |

### 6.2 Bootstrap Cycle Resolution

**K-05 ↔ K-07 is the only circular dependency** (intentional, controlled):

1. Ship K-05 core event bus/store **first** (without audit enforcement)
2. Ship K-07 audit service with hash chain
3. Retrofit K-05 → K-07 publication hooks

### 6.3 Critical Path to Domain Readiness

```
Phase 1 (Sprints 1-2): K-05 → K-07 → K-02 → K-15
                        [Bootstrap cycle resolved first]
                              ↓
Phase 2 (Sprints 3-4): K-01 → K-14 → K-16 → K-17 → K-18
                        K-03 → K-04 → K-06 → K-11
                              ↓
         ═══ DOMAIN READINESS GATE ═══
         (All D-* modules can now begin)
                              ↓
Phase 3 (Sprints 5-10): Domain Modules D-01 through D-14
                              ↓
Phase 4 (Sprints 11-12): K-08 → K-09 → K-19 → K-10
                              ↓
Phase 5 (Sprints 13-14): K-12 → K-13 → W-01 → W-02
                              ↓
Phase 6 (Sprints 15-16): O-01 → P-01 → R-01 → R-02 → PU-004
                              ↓
Phase 7 (Sprints 17-30): T-01 → T-02 → GA Hardening
```

### 6.4 Readiness Gates

| Gate | Required Modules | Enables |
|------|-----------------|---------|
| **Kernel Foundation** | K-02, K-05, K-07, K-15 | Phase 2 kernel services |
| **Domain Readiness** | All 13 kernel modules (M1A+M1B) | D-01 through D-14 |
| **D-01 OMS Readiness** | K-01, K-03, K-05, K-15, K-16 | Order management |
| **Governance Readiness** | K-08, K-09 | Production compliance |
| **SDK/Portal Readiness** | K-12, K-13 | Developer/operator experience |
| **GA Readiness** | T-01, T-02, chaos testing | Production launch |

---

## 7. Cross-Cutting Platform Concerns

### 7.1 W-01: Workflow Orchestration
- Declarative DSL for multi-domain workflows with human task integration
- Builds on K-05 saga primitives
- Dependencies: K-01, K-02, K-05, K-07, K-13, K-15
- 16 stories in M3B

### 7.2 T-01: Integration Testing
- E2E test suites: Order-to-Settlement, Compliance, Plugin execution, Platform upgrade
- Data integrity: Ledger double-entry, Position reconciliation, Audit completeness
- Performance baselines: 10K orders/hr OMS, 100K events/sec bus, 5K concurrent API sessions
- Contract testing (PACT)
- 14 stories in M4

### 7.3 T-02: Chaos Engineering
- Fault injection framework (network, process, data faults)
- Pre-defined scenarios for K-03, K-05, K-07, K-16 outages
- DR drill automation with RTO/RPO measurement
- Resilience scorecard per module
- 10 stories in M4

### 7.4 PU-004: Platform Manifest
- State management, versioning, compatibility enforcement
- 8 stories in M3C

### 7.5 P-01: Pack Certification
- Automated pack testing, certification workflow, marketplace/registry
- 11 stories in M3C

---

## 8. Low-Level Designs (LLD) Coverage

**Status: 100% Complete (33/33 modules)**

| Category | Modules | LLDs | Status |
|----------|---------|------|--------|
| Kernel (K-*) | 19 | 19 | All authored |
| Domain (D-*) | 14 | 14 | All authored |
| **Total** | **33** | **33** | **100%** |

Each LLD follows the standardized 10-section template:
1. Module Overview
2. APIs
3. Data Model
4. Control Flow
5. Algorithms
6. NFRs
7. Security
8. Observability
9. Extensibility
10. Test Plans

**Document volume:** ~900 pages, 200+ API endpoints, 120+ event types, 110+ storage tables

---

## 9. Gap Analysis

### 9.1 Architecture & Design Gaps

| # | Gap | Severity | Module | Description | Recommendation |
|---|-----|----------|--------|-------------|---------------|
| G-01 | Auth vs Event Bus throughput mismatch | Medium | K-01, K-05 | K-01 auth SLA is 5K TPS while K-05 event bus is 100K TPS; auth-gated event paths may bottleneck | Define which event paths require auth-gate vs internal trust; consider service mesh identity for internal events |
| G-02 | Beneficial ownership graph incomplete | Medium | K-01 | Flagged as GAP-002 in epic; traversal API specified but edge cases for circular ownership and complex structures underspecified | Complete graph algorithm spec including cycle detection, max depth limits, and incremental update strategy |
| G-03 | Saga versioning during rolling upgrades | Medium | K-17 | What happens to v1 sagas in-flight when v2 saga definitions deploy? Not fully detailed | Specify saga version pinning: in-flight sagas use original version, new sagas use latest; migration path for long-running sagas |
| G-04 | STUCK saga intervention SLA | Low | K-17 | Manual resolution for STUCK sagas is operationally heavy; no intervention SLA defined | Define SLA tiers: CRITICAL sagas (financial) must be resolved within 1hr; NON-CRITICAL within 24hr; automated escalation at thresholds |
| G-05 | Mid-session config race conditions | Low | K-02 | Distributed deployments may have race conditions when config hot-reload propagates | K-05 event ordering with version vectors should handle this; document the specific guarantees |
| G-06 | AI drift rollback fallback | Medium | K-09 | 60s auto-rollback is ambitious; no specification for what happens if rollback itself fails | Define fallback chain: rollback → disable model → last-known-good → human override |
| G-07 | Cross-kernel integration test gap | Medium | T-01 | No explicit stories for testing K-01+K-02+K-03 orchestration under load | Add integration test stories for auth→config→rules hot path; maker-checker→config→audit chain |
| G-08 | Per-module DR SLAs missing | Low | K-10 | K-10 has DR testing but per-module backup/restore/RTO/RPO not granular | Define per-tier DR SLAs: Tier-1 (K-05, K-07, K-16) RTO <1min; Tier-2 RTO <5min; Tier-3 RTO <30min |

### 9.2 Story Gaps

| # | Gap | Severity | Description | Recommendation |
|---|-----|----------|-------------|---------------|
| S-01 | K-11 Gateway expanded TDD spec missing | Low | K-11 has 13 stories and 76 summary TCs but no expanded TDD spec like K-02/K-05/K-07/K-15 | Create expanded TDD spec for K-11 covering WAF rules, rate limiting edge cases, schema validation scenarios |
| S-02 | K-16 Ledger expanded TDD spec missing | Medium | K-16 is financially critical but only has 4 summary TCs in Phase 2 spec | Create expanded TDD spec for K-16 covering precision arithmetic, reconciliation matching, temporal query edge cases |
| S-03 | K-01 IAM expanded TDD spec missing | Medium | K-01 is security-critical but only has 8 summary TCs | Create expanded TDD spec covering OAuth flows, MFA edge cases, beneficial ownership traversal, anomaly detection |
| S-04 | Performance baseline validation stories sparse | Low | K-06 dashboard exists but per-module performance threshold validation is not a distinct story | Add performance SLA validation stories in M4 for each kernel module's NFR targets |

### 9.3 Test Gaps

| # | Gap | Severity | Description | Recommendation |
|---|-----|----------|-------------|---------------|
| T-01 | K-15 edge case categorization | Low | 500+ edge case requirement (ARB P2-18) acknowledged but no categorization scheme | Categorize: leap year (50), month boundary (80), year boundary (50), timezone (30), holiday overlap (40), etc. |
| T-02 | K-12 multi-language testing matrix | Medium | 5-language SDK with no detailed testing matrix | Define: unit tests per language, integration tests on reference impl (Java), contract tests for all languages, performance benchmarks per language |
| T-03 | K-13 schema-driven form type coverage | Low | Complex form rendering for all schema type combinations not enumerated | Define test matrix: string/number/boolean/date/enum/array/nested types × validation × conditional rendering |

### 9.4 Operational Gaps

| # | Gap | Severity | Description | Recommendation |
|---|-----|----------|-------------|---------------|
| O-01 | Break-glass escalation path thin | Low | K-14 break-glass documented but trigger conditions and escalation path thin | Define: trigger conditions (vault down >5min + critical ops), escalation chain (SRE→Lead→CTO), time limits (4hr max), mandatory review |
| O-02 | Nightly balance verification impact | Low | K-16 nightly recompute frequency, timing, and impact on real-time operations unclear | Schedule: 02:00 UTC (off-peak Nepal), read-only scan, no write locks, alert threshold >0 discrepancy |
| O-03 | K-09 model fallback chain | Medium | If auto-rollback at 60s fails, what model serves predictions? | Define: rollback → previous version → disable model → static defaults → HITL only; each stage auto-escalates |

---

## 10. Corrections to Initial Assessment

The initial assessment (generated 2026-03-10) contained several inaccuracies that have been corrected in this document:

| # | Item | Initial Report | Corrected Value | Impact |
|---|------|---------------|----------------|--------|
| 1 | M1B story count | ~116 (estimated) | **147** (verified) | K-04 (15), K-06 (22), K-11 (13) were underreported |
| 2 | M1B story points | ~271 (estimated) | **361** (verified) | 90 SP undercount |
| 3 | K-04/K-06/K-11 stories | "Not fully extracted — gap" | **Fully present** in M1B | False gap — these epics have complete story sets |
| 4 | K-13 story points | 36 | **37** | Minor |
| 5 | K-12 story points | 37 | **35** | Minor |
| 6 | Total kernel stories | ~279 | **310** | 31-story undercount due to M1B gaps |
| 7 | Total kernel SPs | ~924 | **807** | M3A 443 SP was incorrectly attributed; verified 164 SP |
| 8 | Dependency phases | Critical path only shown | **8 phases** fully defined in matrix | Assessment was incomplete |
| 9 | LLD coverage | Not assessed | **100% (33/33)** complete | Missing dimension in original report |
| 10 | Current project status | Not assessed | **Phase 0, Day 3** — spec-first, no code | Critical context missing |
| 11 | K-11 stories | "Referenced but not enumerated — gap" | **13 stories fully defined** | False gap |
| 12 | M3B kernel vs non-kernel | 29 stories listed as kernel | **29 kernel + 29 workflow** (58 total M3B) | W-01/W-02 omitted |

---

## 11. Summary Scorecard

| Dimension | Status | Score | Notes |
|-----------|--------|-------|-------|
| **Epic Completeness** | 19/19 kernel epics defined | **A** | All have FR, NFR, AC, threat models, failure modes |
| **Story Completeness** | 310 stories across 4 milestones | **A** | All stories have ID, title, SP, AC, dependencies |
| **TDD Coverage** | 314+ TCs for all 19 modules | **A-** | 4 expanded specs (K-02/K-05/K-07/K-15); remaining 15 have summary TCs. K-01/K-16 need expanded specs |
| **LLD Coverage** | 33/33 modules documented | **A** | ~900 pages, standardized 10-section template |
| **Dependency Mapping** | Complete matrix with 8 phases | **A** | 1 intentional cycle identified and mitigated |
| **ARB Remediation** | 20/20 findings addressed | **A** | All mapped to specific FR improvements and test cases |
| **Security** | Zero-trust, mTLS, encryption | **A** | Threat models per epic, OWASP controls |
| **Delivery Planning** | 30 sprints, 5 milestones | **A** | Clear phases, readiness gates, exit criteria |
| **Current Execution** | Phase 0, Day 3 | **B** | No runtime code yet; scaffold being built |
| **Gap Severity** | 8 arch + 4 story + 3 test + 3 ops | **B+** | No fundamental gaps; mostly operational detail |
| **Overall** | | **A-** | Exceptionally well-specified; execution risk is primary concern |

---

## 12. Detailed Implementation Plan

### 12.1 Program Overview

| Metric | Value |
|--------|-------|
| Total Sprints | 30 (2-week cadence) |
| Total Duration | ~60 weeks |
| Total Backlog | ~1,930 SP (807 kernel + 1,123 domain/cross-cutting) |
| Kernel Modules | 19 |
| Domain Modules | 14 |
| Cross-Cutting Modules | 9 |
| Target MVP | September 18, 2026 |
| Target GA | November 13, 2026 |

### 12.2 Phase 0 — Execution Bootstrap (March 9–20, 2026)

**Goal:** Establish repository scaffold, shared contracts, local dev stack, and CI pipeline.

| Priority | Workstream | Deliverable | Exit Criteria |
|----------|-----------|-------------|--------------|
| P1 | Monorepo Structure | `services/`, `packages/`, `apps/`, `infra/`, `schemas/`, `packs/`, `tools/` | All directories created with README |
| P2 | Shared Contracts | `event-envelope` (K-05), `dual-date` (K-15) packages | Protobuf/OpenAPI compiled, importable |
| P3 | Local Runtime | Docker Compose stack: PostgreSQL, Redis, Kafka, OTel Collector | One-command boot, all healthy |
| P4 | CI Pipeline | GitHub Actions: lint → test → contract validation → container build → security scan | Blocks schema-breaking changes |
| P5 | Engineering Standards | Service naming, API versioning, event schema versioning, migration policy | Standards doc committed, templates enforced |

**Exit Gate:** New service from template in <30 minutes; local stack boots with one command; CI blocks breaking changes.

### 12.3 Phase 1 — Kernel Foundation (Sprints 1–2, March 23 – May 15, 2026)

**Goal:** Deliver the 4 bootstrap kernel modules that all other modules depend on.

#### Sprint 1 (Weeks 1–2)

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| K-05 Event Bus | K05-001 to K05-004 | 15 | Event store schema (append-only), appendEvent API, getEvents API, dual-calendar enrichment |
| K-07 Audit | K07-001 to K07-007 | 17 | Audit SDK core, middleware, hash chain table, SHA-256 computation, chain validation, event schema, enrichment |
| K-02 Config | K02-001 to K02-006 | 16 | Schema registration, validation, browsing, GLOBAL→JURISDICTION→TENANT→USER→SESSION hierarchy, merge strategy |
| K-15 Calendar | K15-001 to K15-007 | 17 | JDN conversion library, BS month lookup, REST API, DualDate type, storage middleware, holiday CRUD, business day calc |

**Sprint 1 Total:** ~38 stories, ~65 SP
**Sprint 1 Risks:** K-05↔K-07 bootstrap cycle — mitigate by delivering K-05 core first without audit hooks
**Sprint 1 Tests:** Phase 0 TDD (25 TCs) + K-05 ES/EE TCs + K-07 AE/AP TCs + K-02 SM/HR TCs + K-15 DC/DD TCs

#### Sprint 2 (Weeks 3–4)

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| K-05 Event Bus | K05-005 to K05-032 | 85 | Partitioning, schema registry, Kafka outbox, consumers, idempotency, sagas, replay, DLQ, backpressure |
| K-07 Audit | K07-008 to K07-016 | 21 | Export (CSV/JSON/PDF), retention, maker-checker linkage, blockchain anchoring |
| K-02 Config | K02-007 to K02-017 | 26 | Hot-reload, canary, rollback, dual-calendar dates, temporal queries, air-gap bundles, maker-checker, CQRS |
| K-15 Calendar | K15-008 to K15-013 | 13 | Fiscal year/quarter, T+n settlement, weekend/holiday skip, edge cases, accuracy test suite |

**Sprint 2 Total:** ~40 stories, ~145 SP
**Sprint 2 Tests:** Full expanded TDD suites: K-05 (68 TCs), K-07 (52 TCs), K-02 (56 TCs), K-15 (37 TCs)

**Phase 1 Exit Gate (Milestone B):**
- [ ] K-05 event store append-only verified (UPDATE/DELETE blocked)
- [ ] K-05 Kafka outbox publishing at 100K TPS in load test
- [ ] K-07 hash chain unbroken across 100K entries
- [ ] K-02 5-level hierarchy resolving correctly with hot-reload
- [ ] K-15 round-trip conversion 100% accurate for 2000-2100 BS (36,500 days)
- [ ] All 213 TDD test cases passing (56+68+52+37)
- [ ] All 4 services deployed to staging with health checks green

### 12.4 Phase 2 — Kernel Services Completion (Sprints 3–4, May 15 – July 10, 2026)

**Goal:** Deliver remaining 9 kernel modules to unlock domain layer.

#### Sprint 3 (Weeks 5–6)

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| K-01 IAM | K01-001 to K01-009, K01-017, K01-022 | 31 | OAuth 2.0, MFA, brute-force, sessions, role-permission, audit integration, rate limiting |
| K-14 Secrets | K14-001 to K14-006 | 16 | Provider interface, Vault/file providers, rotation scheduler, DB cred rotation, JWT key rotation |
| K-16 Ledger | K16-001 to K16-003, K16-005, K16-006, K16-010, K16-016, K16-018 | 24 | Schema, journal creation, balance materialization, immutability, hash chain, currency registry, CoA, Money object |
| K-17 DTC | K17-001 to K17-003 | 8 | Outbox table, relay service, cleanup, exactly-once guarantee |
| K-18 Resilience | K18-001 to K18-007 | 15 | Circuit breaker, execution wrapper, profiles, bulkheads, retry, context propagation |
| K-04 Plugin | K04-001 to K04-003 | 7 | Manifest verification, checksum, T1 loader |
| K-06 Observability | K06-001 to K06-011 | 25 | SDK core, logging, metrics, tracing, context propagation, Prometheus, Grafana, Jaeger, OpenSearch |
| K-11 Gateway | K11-001 to K11-004 | 9 | Envoy/Istio gateway, API versioning, transformation, JWT validation |

**Sprint 3 Total:** ~72 stories, ~135 SP

#### Sprint 4 (Weeks 7–8)

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| K-01 IAM | K01-010 to K01-016, K01-018 to K01-021, K01-023 | 31 | RBAC middleware, ABAC, break-glass, NID plugins, mTLS, JWT propagation, beneficial ownership, anomaly detection |
| K-14 Secrets | K14-007 to K14-014 | 19 | Certificates, break-glass, HSM, version history, rollback |
| K-16 Ledger | K16-004, K16-007 to K16-009, K16-011 to K16-015, K16-017, K16-019 | 26 | Reversal, as-of queries, snapshots, multi-currency, multi-asset, reconciliation, break aging, account workflow, rounding |
| K-17 DTC | K17-004 to K17-014 | 28 | Version vectors, causal ordering, conflict detection, saga coordination, compensation, monitoring, idempotency |
| K-18 Resilience | K18-008 to K18-013 | 13 | Timeout budgets, per-route config, composite profiles, monitoring, health aggregator, Grafana |
| K-03 Rules | K03-001 to K03-014 | 30 | OPA wrapper, bundles, caching, T2 sandbox, jurisdiction routing, canary, maker-checker, circuit breaker |
| K-04 Plugin | K04-004 to K04-015 | 26 | T2/T3 runtimes, capabilities, versioning, hot-swap, state migration, quotas, exfiltration prevention |
| K-06 Observability | K06-012 to K06-022 | 32 | Kibana, SLOs, error budgets, PII detection, alerting, AIOps (anomaly, clustering, noise reduction) |
| K-11 Gateway | K11-005 to K11-013 | 21 | mTLS, rate limiting, jurisdiction routing, WAF, payload validation, schema validation |

**Sprint 4 Total:** ~75 stories, ~226 SP

**Phase 2 Exit Gate (Milestone C — Kernel Complete):**
- [ ] All 19 kernel modules deployed to staging
- [ ] K-01 auth flow: OAuth 2.0 + MFA + RBAC end-to-end
- [ ] K-14 secret rotation: zero-downtime DB credential rotation verified
- [ ] K-16 double-entry: debit==credit enforcement, hash chain unbroken
- [ ] K-17 saga: Order→Position→Ledger saga compensates correctly on failure
- [ ] K-18 circuit breaker: K-03 failure triggers degraded mode with fallback
- [ ] K-11 gateway: 50K TPS with rate limiting and JWT validation
- [ ] Phase 2 TDD (76 TCs) + all expanded TDD specs passing
- [ ] All inter-module integration tests passing
- [ ] **DOMAIN READINESS GATE OPEN** — D-* modules may begin

### 12.5 Phase 3 — Domain Trading MVP (Sprints 5–10, July 10 – September 18, 2026)

**Goal:** Build core trading domain on top of kernel.

#### Sprints 5–6 (M2A: Pre-Trade)

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| D-11 Reference Data | 13 | ~35 | Security master, instrument classification, corporate hierarchy |
| D-04 Market Data | 15 | ~40 | Real-time feeds, OHLCV, circuit breakers, data quality |
| D-01 OMS | 21 | ~60 | Order lifecycle, FIX protocol, order validation, maker-checker |
| D-07 Compliance | 17 | ~45 | Pre-trade compliance, threshold monitoring, attestation |
| D-14 Sanctions | 16 | ~40 | Screening, watchlist matching, false positive management |
| D-06 Risk | 21 | ~55 | Position limits, margin calculation, VaR, stress testing |
| D-02 EMS | 22 | ~60 | Execution strategies, smart routing, venue connectivity |

#### Sprints 7–8 (M2A Continued)

Complete remaining M2A stories.

#### Sprints 9–10 (M2B: Post-Trade & Reporting)

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| D-09 Post-Trade | 18 | ~50 | Trade confirmation, settlement, clearing |
| D-13 Client Money | 18 | ~50 | Reconciliation, segregation, daily proof |
| D-03 PMS | 13 | ~35 | Portfolio valuation, P&L, NAV |
| D-05 Pricing | 12 | ~30 | Mark-to-market, yield curves, models |
| D-08 Surveillance | 16 | ~45 | Pattern detection, alert management |
| D-10 Regulatory | 13 | ~35 | SRN reporting, SEBON filings |
| D-12 Corporate Actions | 14 | ~40 | Dividends, rights, splits, voluntary elections |

**Phase 3 Exit Gate (Milestone D — Trading MVP):**
- [ ] End-to-end order flow: Order → Compliance → Risk → Execution → Trade → Settlement → Ledger
- [ ] Reconciliation: Ledger ↔ CDSC ↔ Bank statements balanced
- [ ] All 14 domain modules functional in staging
- [ ] Performance: 10K orders/hour sustained
- [ ] All domain-specific acceptance criteria passing

### 12.6 Phase 4 — Advanced Kernel (Sprints 11–12, September 18 – October 16, 2026)

**Goal:** Governance, DLQ management, deployment orchestration.

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| K-08 Data Governance | 14 | 43 | Catalog, lineage, classification, quality, retention, PII masking |
| K-09 AI Governance | 15 | 47 | Model registry, explainability, drift, bias, HITL |
| K-19 DLQ Management | 15 | 38 | DLQ service, replay, poison pill, ML classifier |
| K-10 Deployment | 12 | 36 | Canary, promotion, rollback, drift detection, HPA |

**Phase 4 Exit Gate:**
- [ ] K-08 data lineage tracking operational for all kernel event flows
- [ ] K-09 model registry with at least one registered model (anomaly detection)
- [ ] K-19 DLQ dashboard showing real-time message counts and auto-retry status
- [ ] K-10 canary deployment: successful canary → promotion of one service

### 12.7 Phase 5 — Platform Tools & Workflows (Sprints 13–14, October 16 – November 13, 2026)

**Goal:** Developer experience (SDK, Admin Portal) and workflow orchestration.

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| K-13 Admin Portal | 14 | 37 | Portal shell, config UI, health dashboard, audit explorer, maker-checker |
| K-12 Platform SDK | 15 | 35 | Code gen, contract testing, plugin scaffold, developer portal |
| W-01 Workflow | 16 | 41 | Declarative DSL, human tasks, SLA tracking |
| W-02 Client Onboarding | 13 | ~42 | KYC, account creation, instrument allocation |

### 12.8 Phase 6 — Operations & Compliance (Sprints 15–16)

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| O-01 Operator Console | 14 | 41 | Operator workflows, dashboards |
| P-01 Pack Certification | 11 | 26 | Automated testing, certification |
| R-01 Regulator Portal | 11 | 30 | Regulatory views, report access |
| R-02 Incident Response | 12 | 34 | Breach notification, incident tracking |
| PU-004 Platform Manifest | 8 | ~19 | State management, versioning |

### 12.9 Phase 7 — Integration Testing & GA (Sprints 17–30)

**Goal:** Comprehensive testing, chaos engineering, production hardening.

| Module | Stories | SP | Focus |
|--------|---------|-----|-------|
| T-01 Integration Testing | 14 | 43 | E2E suites, contract testing, performance baselines |
| T-02 Chaos Engineering | 10 | 28 | Fault injection, DR drills, resilience scorecard |
| GA Hardening | 6 | 19 | Security audit, load testing, monitoring, runbooks |

**GA Exit Gate (Milestone E):**
- [ ] All E2E test suites passing (Order-to-Settlement, Compliance, Plugin, Upgrade)
- [ ] Chaos tests: K-05/K-07/K-16 survive Kafka/DB outage with data integrity
- [ ] DR drill: RTO <5min achieved for Tier-1 services
- [ ] Performance: 100K events/sec sustained, 10K orders/hour, 5K concurrent sessions
- [ ] Security audit complete with no Critical/High findings
- [ ] All 314+ kernel TDD test cases passing
- [ ] Operational runbooks for all 19 kernel modules complete

### 12.10 Implementation Timeline Summary

```
Mar 2026 ─── Phase 0: Bootstrap (Scaffold)
         │
Mar 2026 ─── Phase 1: Kernel Foundation
         │   Sprint 1-2: K-05, K-07, K-02, K-15
         │   [Milestone B: May 15]
         │
May 2026 ─── Phase 2: Kernel Completion
         │   Sprint 3-4: K-01, K-03, K-04, K-06, K-11, K-14, K-16, K-17, K-18
         │   [Milestone C: July 10]
         │   ═══ DOMAIN READINESS GATE ═══
         │
Jul 2026 ─── Phase 3: Domain Trading MVP
         │   Sprint 5-10: D-01 through D-14
         │   [Milestone D: September 18]
         │
Sep 2026 ─── Phase 4: Advanced Kernel
         │   Sprint 11-12: K-08, K-09, K-10, K-19
         │
Oct 2026 ─── Phase 5: Platform Tools
         │   Sprint 13-14: K-12, K-13, W-01, W-02
         │
Nov 2026 ─── Phase 6: Operations & Compliance
         │   Sprint 15-16: O-01, P-01, R-01, R-02, PU-004
         │
Nov 2026 ─── Phase 7: Testing & GA
    ──→      Sprint 17-30: T-01, T-02, GA Hardening
             [Milestone E: GA Ready]
```

### 12.11 Risk Register

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| K-05↔K-07 bootstrap cycle blocks Sprint 1 | Low | High | Phased delivery: K-05 core first → K-07 → hooks |
| Sprint 2 overloaded (145 SP) | High | Medium | Buffer from Sprint 1 (~65 SP); priority: K-05 outbox, K-07 hash chain |
| Multi-language SDK (5 languages) testing burden | Medium | Medium | Code-gen from protobuf/OpenAPI; test reference impl (Java); contract tests |
| K-09 AI drift 60s rollback target | Medium | Low | Implement as best-effort with escalation; production target may relax |
| Air-gap deployment complexity | Medium | Medium | Validate early in Phase 0 with signed config bundles |
| 100K TPS for K-05 in staging | Medium | High | Performance testing from Sprint 2; horizontal scaling strategy ready |
| K-16 precision arithmetic across 5 currency types | Low | High | Comprehensive TDD; use BigDecimal exclusively; nightly verification |
| Team ramp-up on ActiveJ framework | Medium | Medium | Phase 0 includes template services; pair programming recommended |

### 12.12 Recommended Immediate Actions

1. **Complete Phase 0** (by March 20): Monorepo, shared contracts, Docker Compose stack, CI pipeline
2. **Create expanded TDD specs** for K-01 (IAM) and K-16 (Ledger) — both are security/financial critical and deserve expanded coverage beyond summary TCs
3. **Resolve K-05↔K-07 sequence** in code: implement K-05 event store + appendEvent first, then K-07 audit table + hash chain, then retrofit K-05 audit hooks
4. **Establish performance baseline** early: K-05 outbox relay at 100K TPS should be validated by end of Sprint 2
5. **Define DR SLA tiers** for kernel modules before Phase 2 exit gate
6. **Add cross-kernel integration test stories** to M4 backlog: auth→config→rules hot path; saga compensation chain; maker-checker audit trail

---

## Appendix A: Universal Acceptance Criteria Patterns

All kernel stories follow these cross-cutting patterns:

1. **Immutability & Append-Only** (K-05, K-07, K-16): `REVOKE UPDATE/DELETE` at DB level; corrections via compensating entries
2. **Dual-Calendar Consistency** (K-15): Every timestamp stores `{utc: TIMESTAMPTZ, bs: "2081-01-01", fiscal_year}`
3. **Event-Driven Propagation** (K-05): All state changes emit events; cache invalidation and projections triggered by events
4. **Maker-Checker Enforcement** (K-01): Sensitive actions (role creation, config change, policy deploy) require dual authorization
5. **Observability Binding** (K-06): All operations export metrics + traces; standard metrics: `{op_latency, event_latency, error_rate}`
6. **Multi-Tenant Isolation** (K-01, K-02): All queries implicitly filtered by `WHERE tenant_id = current_tenant_id`; cross-tenant attempts → 403
7. **Encryption at Rest & Transit** (K-14): AES-256-GCM at rest, TLS 1.3+ in transit, mTLS for service-to-service

## Appendix B: Performance Targets

| Module | Metric | Target |
|--------|--------|--------|
| K-05 Event Bus | Publish TPS (sustained) | 100,000 |
| K-05 Event Bus | Publish latency P99 | <2ms |
| K-05 Event Bus | Replay speed | 50,000 events/sec |
| K-05 Event Bus | Consumer lag (critical) | <30s |
| K-07 Audit | Ingestion rate | 10,000 events/sec |
| K-07 Audit | Search latency P99 | <500ms |
| K-02 Config | Read latency (cached) | <0.5ms |
| K-15 Calendar | Conversion latency P99 | <2ms |
| K-01 IAM | Auth TPS | 5,000 |
| K-01 IAM | Auth latency P99 | <10ms |
| K-11 Gateway | Gateway TPS | 50,000 |
| K-11 Gateway | Gateway overhead P99 | <2ms |
| K-14 Secrets | Retrieval P99 (cached) | <10ms |
| K-14 Secrets | Retrieval P99 (vault) | <50ms |
| K-16 Ledger | Posting TPS | 10,000 |
| K-16 Ledger | Posting commit P99 | <5ms |
| K-16 Ledger | Temporal query P99 | <50ms |
| K-17 DTC | Outbox relay lag P99 | <100ms |
| K-17 DTC | Sagas/sec | 50,000 |
| K-18 Resilience | SDK overhead | <0.1ms |
| K-12 SDK | SDK call overhead | <1ms |
| K-13 Portal | Page load | <1s |
| K-13 Portal | API response | <200ms |

## Appendix C: Glossary

| Term | Definition |
|------|-----------|
| **BS** | Bikram Sambat — the official Nepali calendar |
| **T1/T2/T3** | Plugin tiers: T1 (config-only), T2 (logic sandbox), T3 (network-capable) |
| **DTC** | Distributed Transaction Coordinator |
| **DLQ** | Dead Letter Queue |
| **HITL** | Human-In-The-Loop |
| **OCC** | Optimistic Concurrency Control |
| **CQRS** | Command Query Responsibility Segregation |
| **ARB** | Architecture Review Board |
| **CoA** | Chart of Accounts |
| **CMK** | Customer-Managed Keys |
| **JDN** | Julian Day Number (used for calendar conversion) |
| **FY** | Fiscal Year |
| **SLO/SLA** | Service Level Objective / Agreement |
| **mTLS** | Mutual TLS (bidirectional certificate verification) |
| **WAF** | Web Application Firewall |
| **RTO/RPO** | Recovery Time Objective / Recovery Point Objective |
| **GA** | General Availability |

---

> **End of Document**
> This is a temporary review artifact. Content should be migrated to permanent documentation as decisions are finalized.
