# PROJECT SIDDHANTA — ENTERPRISE DELIVERY PROGRAM PLAN

## Full System Delivery Plan: Epics → Features → Stories → Sprints

**Version**: 1.1.0  
**Generated**: 2026-03-08  
**Program Duration**: 30 Sprints (60 weeks)  
**Sprint Cadence**: 2-week sprints  
**Current Backlog Story Points**: ~1,930 (execution baseline)

> Authority note: Use `plans/CURRENT_EXECUTION_PLAN.md`, `stories/STORY_INDEX.md`, and `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md` as the current execution baseline. This document remains the long-form delivery decomposition and staffing model.

---

# 1. SYSTEM ARCHITECTURE SUMMARY

## 1.1 Platform Overview

Project Siddhanta is a **jurisdiction-neutral, regulator-grade capital markets operating system** built on a 7-layer microservices architecture with CQRS + Event Sourcing, T1/T2/T3 plugin taxonomy, and dual-calendar (Bikram Sambat + Gregorian) native support.

## 1.2 Platform Modules (42 Modules)

### Kernel Layer (19 Modules)

| ID   | Module                              | Responsibility                                          |
| ---- | ----------------------------------- | ------------------------------------------------------- |
| K-01 | IAM                                 | Authentication, authorization, MFA, KYC/AML integration |
| K-02 | Configuration Engine                | Hierarchical config, hot-reload, schema validation      |
| K-03 | Rules Engine                        | OPA/Rego policy evaluation, T2 sandboxing               |
| K-04 | Plugin Runtime                      | T1/T2/T3 plugin lifecycle, sandbox isolation            |
| K-05 | Event Bus & Store                   | Append-only events, Kafka delivery, saga orchestration  |
| K-06 | Observability                       | Metrics, tracing, logging, SLO management               |
| K-07 | Audit Framework                     | Immutable hash-chained audit trail                      |
| K-08 | Data Governance                     | Lineage, residency, retention, encryption               |
| K-09 | AI Governance                       | Model registry, explainability, HITL, drift detection   |
| K-10 | Deployment Abstraction              | SaaS/on-prem/air-gap packaging, feature flags           |
| K-11 | API Gateway                         | JWT/mTLS, rate limiting, WAF, jurisdiction routing      |
| K-12 | Platform SDK                        | Multi-language SDK auto-generation                      |
| K-13 | Admin Portal                        | React micro-frontend admin console                      |
| K-14 | Secrets Management                  | Multi-provider vault, auto-rotation, HSM                |
| K-15 | Dual-Calendar                       | BS ↔ Gregorian conversion, fiscal year, holidays        |
| K-16 | Ledger Framework                    | Double-entry bookkeeping, reconciliation                |
| K-17 | Distributed Transaction Coordinator | Outbox pattern, saga, compensation                      |
| K-18 | Resilience Patterns                 | Circuit breaker, bulkhead, retry, timeout               |
| K-19 | DLQ Management                      | Dead-letter monitoring, replay, quarantine              |

### Domain Layer (14 Modules)

| ID   | Module               | Responsibility                                          |
| ---- | -------------------- | ------------------------------------------------------- |
| D-01 | OMS                  | Order lifecycle, pre-trade validation, routing          |
| D-02 | EMS                  | Smart order routing, execution algorithms, FIX adapters |
| D-03 | PMS                  | Portfolio construction, NAV, rebalancing                |
| D-04 | Market Data          | Feed normalization, L1/L2/L3, circuit breakers          |
| D-05 | Pricing Engine       | Yield curves, MTM, T3 model plugins                     |
| D-06 | Risk Engine          | Pre-trade risk, VaR, margin calls, liquidation          |
| D-07 | Compliance Engine    | KYC/AML, lock-in enforcement, restricted lists          |
| D-08 | Surveillance         | Wash trade/spoofing detection, case management          |
| D-09 | Post-Trade           | Settlement lifecycle, netting, DVP                      |
| D-10 | Regulatory Reporting | Multi-format reports, portal submission                 |
| D-11 | Reference Data       | Instruments, entities, benchmarks, versioned data       |
| D-12 | Corporate Actions    | Dividends, bonus, rights, tax, ledger posting           |
| D-13 | Client Money Recon   | Daily reconciliation, break detection, segregation      |
| D-14 | Sanctions Screening  | Real-time screening, fuzzy matching, review workflow    |

### Supporting Layers (9 Modules)

| ID     | Module                 | Layer          |
| ------ | ---------------------- | -------------- |
| W-01   | Workflow Orchestration | Workflow       |
| W-02   | Client Onboarding      | Workflow       |
| O-01   | Operator Console       | Operations     |
| P-01   | Pack Certification     | Platform       |
| R-01   | Regulator Portal       | Regulatory     |
| R-02   | Incident Response      | Regulatory     |
| T-01   | Integration Testing    | Testing        |
| T-02   | Chaos Engineering      | Testing        |
| PU-004 | Platform Manifest      | Platform Unity |

## 1.3 Data Stores

| Store           | Technology                                                              | Purpose                                                    |
| --------------- | ----------------------------------------------------------------------- | ---------------------------------------------------------- |
| Operational DB  | PostgreSQL 15+                                                          | Transactional data, CQRS command store                     |
| Event Store     | PostgreSQL + Kafka                                                      | Immutable event log                                        |
| Ledger DB       | PostgreSQL (REVOKE UPDATE/DELETE)                                       | Double-entry accounting                                    |
| Time-Series DB  | TimescaleDB                                                             | Market data ticks, OHLC                                    |
| Cache           | Redis 7+                                                                | Session, reference data, rate-limit counters               |
| Search          | Elasticsearch                                                           | Full-text search, audit log queries                        |
| Analytics Views | PostgreSQL/TimescaleDB projections plus Ghatana Data Cloud abstractions | Reporting, regulatory extracts, and analytical read models |
| Object Storage  | S3/MinIO                                                                | Documents, reports, evidence packages                      |
| Vector Store    | pgvector                                                                | AI embeddings (future)                                     |

## 1.4 Integration Interfaces

| Interface       | Protocol            | Purpose                             |
| --------------- | ------------------- | ----------------------------------- |
| API Gateway     | REST/gRPC/WebSocket | External client access              |
| FIX Gateway     | FIX 4.4/5.0         | Exchange connectivity               |
| Event Bus       | Kafka topics        | Async inter-service messaging       |
| Integration Hub | REST/SOAP           | 3rd-party (banks, KYC, depository)  |
| Service Mesh    | mTLS/gRPC           | Inter-service communication         |
| WebSocket       | WSS                 | Real-time market data/notifications |
| Notification    | SMTP/SMS/Push       | Multi-channel alerts                |

---

# 2. CAPABILITY MAP

```
Siddhanta Platform
├── Identity & Access
│   ├── Authentication (OAuth 2.0, MFA, SSO, DID)
│   ├── Authorization (RBAC, ABAC, maker-checker)
│   ├── Session Management (multi-tenant, dual-calendar)
│   └── KYC/AML Integration (national ID, screening)
│
├── Platform Configuration
│   ├── Schema Registration & Validation
│   ├── Hierarchical Resolution (GLOBAL→JURISDICTION→TENANT→USER→SESSION)
│   ├── Hot-Reload & Canary Rollout
│   └── Air-Gap Signed Config Bundles
│
├── Policy & Rules
│   ├── Declarative Evaluation (OPA/Rego)
│   ├── T2 Sandboxed Execution
│   ├── Jurisdiction-Specific Routing
│   └── Maker-Checker Rule Deployment
│
├── Plugin Platform
│   ├── Plugin Registration & Verification (Ed25519)
│   ├── Tier Isolation (T1-config, T2-sandbox, T3-network)
│   ├── Hot-Swap & Graceful Degradation
│   └── Resource Quotas & Exfiltration Prevention
│
├── Event Infrastructure
│   ├── Append-Only Event Store
│   ├── Schema Validation & Evolution
│   ├── Saga Orchestration & Compensation
│   ├── Idempotency Framework
│   └── Projection Rebuild & Replay
│
├── Observability
│   ├── Metrics Collection (Prometheus)
│   ├── Distributed Tracing (Jaeger/OTel)
│   ├── Centralized Logging (ELK)
│   ├── SLO/SLA Management & Error Budgets
│   └── PII Detection & Masking
│
├── Audit & Compliance Infrastructure
│   ├── Immutable Hash-Chain Audit Trail
│   ├── Evidence Export (CSV, JSON, PDF)
│   ├── Retention Policy Enforcement
│   └── External Hash Anchoring
│
├── Data Governance
│   ├── Data Lineage Graph
│   ├── Residency Enforcement
│   ├── Retention Lifecycle
│   ├── Encryption Abstraction (AES-256-GCM)
│   └── GDPR Data Subject Rights
│
├── AI & ML Platform
│   ├── Model Registry & Versioning
│   ├── Explainability (SHAP/LIME)
│   ├── HITL Override Mechanism
│   ├── Drift Detection & Auto-Rollback
│   └── Prompt Injection Prevention
│
├── Security Infrastructure
│   ├── Secrets Management (multi-provider vault)
│   ├── Certificate Lifecycle
│   ├── HSM Integration
│   ├── Break-Glass Access
│   └── Zero-Trust Mesh (mTLS)
│
├── API Management
│   ├── JWT/mTLS Authentication
│   ├── Rate Limiting (tenant-aware)
│   ├── Jurisdiction-Aware Routing
│   ├── WAF (OWASP Top 10)
│   └── OpenAPI Schema Validation
│
├── Calendar & Time
│   ├── BS ↔ Gregorian Conversion
│   ├── Fiscal Year Boundaries
│   ├── Holiday Calendar Management
│   └── Settlement Date T+n Calculation
│
├── Ledger & Accounting
│   ├── Double-Entry Bookkeeping
│   ├── Multi-Currency/Multi-Asset Balances
│   ├── Temporal Queries & Snapshots
│   └── Reconciliation Engine
│
├── Transaction Coordination
│   ├── Outbox Pattern (guaranteed publish)
│   ├── Saga State Machine
│   ├── Version Vectors (causal ordering)
│   └── Compensation Orchestration
│
├── Resilience
│   ├── Circuit Breaker (configurable profiles)
│   ├── Bulkhead Isolation
│   ├── Retry with Backoff & Jitter
│   └── Cascading Timeout Budgets
│
├── DLQ & Error Recovery
│   ├── DLQ Monitoring Dashboard
│   ├── Poison Pill Quarantine
│   ├── Safe Replay with Dry-Run
│   └── Automated Transient-Failure Retry
│
├── Trading & Execution
│   ├── Order Management (capture, state machine, routing)
│   ├── Execution Management (SOR, VWAP/TWAP, FIX adapters)
│   ├── Pre-Trade Risk Checks (<5ms)
│   └── Position Tracking & P&L
│
├── Portfolio Management
│   ├── Portfolio Construction & Optimization
│   ├── NAV Calculation (TWR/MWR)
│   ├── Drift Detection & Rebalancing
│   └── What-If Scenario Analysis
│
├── Market Data
│   ├── Feed Normalization (multi-vendor)
│   ├── L1/L2/L3 Order Book Distribution
│   ├── Circuit Breaker Detection
│   ├── Historical Tick Storage
│   └── WebSocket Streaming
│
├── Pricing & Valuation
│   ├── Real-Time & EOD Pricing
│   ├── Yield Curve Construction
│   ├── Mark-to-Market Batch
│   └── T3 Custom Pricing Model Plugins
│
├── Risk Management
│   ├── VaR Calculation (parametric, historical, Monte Carlo)
│   ├── Margin Calculation & Calls
│   ├── Forced Liquidation
│   └── Stress Testing & Scenario Analysis
│
├── Compliance & Controls
│   ├── Lock-In Period Enforcement
│   ├── Beneficial Ownership Tracking
│   ├── Restricted List Management
│   └── Compliance Attestation Workflows
│
├── Surveillance
│   ├── Wash Trade Detection
│   ├── Spoofing/Layering Detection
│   ├── Front-Running Detection
│   ├── AI Anomaly Detection
│   └── Case Management
│
├── Post-Trade & Settlement
│   ├── Trade Confirmation
│   ├── Netting Engine (bilateral/multilateral)
│   ├── Settlement Lifecycle (T+0 to T+n)
│   ├── CSD/Depository Integration
│   └── Settlement Failure Management
│
├── Reporting
│   ├── Multi-Format Rendering (PDF/CSV/XBRL)
│   ├── Regulatory Portal Submission
│   ├── ACK/NACK Tracking
│   └── Regulatory Deadline Tracking
│
├── Reference Data
│   ├── Instrument Master (ISIN, symbol)
│   ├── Entity Master (issuers, brokers)
│   ├── Benchmark/Index Management
│   └── Temporal Validity & Point-in-Time Queries
│
├── Corporate Actions
│   ├── Lifecycle Management (announce → settle)
│   ├── Entitlement Calculation
│   ├── Tax Withholding
│   ├── Election Management
│   └── Ledger Posting (securities + cash)
│
├── Client Money
│   ├── Daily Automated Reconciliation
│   ├── Break Detection & Aging
│   ├── Segregation Verification
│   └── Escalation Workflow
│
├── Sanctions
│   ├── Real-Time Screening (<50ms)
│   ├── Fuzzy Matching (Levenshtein, Jaro-Winkler)
│   ├── Match Review Workflow
│   └── Air-Gap Signed List Bundles
│
├── Workflow Orchestration
│   ├── Workflow Definition DSL
│   ├── Workflow Execution Engine
│   ├── Workflow Monitoring & History
│   └── Workflow Versioning
│
├── Client Onboarding
│   ├── KYC Document Collection
│   ├── Identity Verification
│   ├── Risk Assessment & Scoring
│   └── Account Setup
│
├── Operations
│   ├── Incident Management & Runbooks
│   ├── Disaster Recovery Automation
│   ├── Change Management
│   └── Post-Mortem Workflow
│
├── Pack Certification
│   ├── Pack Testing & Compatibility
│   ├── Security Scanning
│   ├── Performance Benchmarking
│   └── Marketplace & Registry
│
├── Regulatory
│   ├── Regulator Authentication & Access
│   ├── On-Demand Data Access
│   ├── Incident Response & Escalation
│   └── Transparency Reports
│
├── Deployment & Infrastructure
│   ├── Service Deployment Pipeline
│   ├── Environment Management
│   ├── Rollback & Recovery
│   └── Configuration Drift Detection
│
├── Admin Portal
│   ├── Platform Configuration UI
│   ├── User & Role Management UI
│   ├── Audit Log Explorer
│   └── Observability Hub
│
├── Platform SDK
│   ├── Multi-Language Support (Go/Java/Python/TS/C#)
│   ├── Protocol Abstraction
│   ├── Auto-Generation from OpenAPI/Protobuf
│   └── Middleware Hooks
│
└── Testing Infrastructure
    ├── E2E Scenario Library
    ├── Performance/Load Testing
    ├── Chaos Engineering & Fault Injection
    └── Security Penetration Testing
```

---

# 3. FINAL EPIC LIST (42 Validated Epics)

## 3.1 Kernel Epics (19)

| #   | Epic ID | Name                                | FRs | ACs | Priority |
| --- | ------- | ----------------------------------- | --- | --- | -------- |
| 1   | K-01    | Identity & Access Management        | 13  | 5   | P0       |
| 2   | K-02    | Configuration Engine                | 9   | 5   | P0       |
| 3   | K-03    | Policy / Rules Engine               | 10  | 4   | P0       |
| 4   | K-04    | Plugin Runtime & SDK                | 10  | 4   | P0       |
| 5   | K-05    | Event Bus, Event Store & Workflows  | 14  | 7   | P0       |
| 6   | K-06    | Observability Stack                 | 10  | 6   | P0       |
| 7   | K-07    | Audit Framework                     | 8   | 4   | P0       |
| 8   | K-08    | Data Governance                     | 8   | 3   | P1       |
| 9   | K-09    | AI Governance                       | 9   | 4   | P1       |
| 10  | K-10    | Deployment Abstraction              | 6   | 3   | P1       |
| 11  | K-11    | API Gateway                         | 8   | 3   | P0       |
| 12  | K-12    | Platform SDK                        | 10  | 10  | P2       |
| 13  | K-13    | Admin Portal                        | 6   | 3   | P1       |
| 14  | K-14    | Secrets Management                  | 10  | 7   | P0       |
| 15  | K-15    | Dual-Calendar Service               | 10  | 8   | P0       |
| 16  | K-16    | Ledger Framework                    | 10  | 3   | P0       |
| 17  | K-17    | Distributed Transaction Coordinator | 8   | 5   | P0       |
| 18  | K-18    | Resilience Patterns                 | 8   | 5   | P0       |
| 19  | K-19    | DLQ Management                      | 8   | 5   | P1       |

## 3.2 Domain Epics (14)

| #   | Epic ID | Name                        | FRs | ACs | Priority |
| --- | ------- | --------------------------- | --- | --- | -------- |
| 20  | D-01    | Order Management System     | 7   | 4   | P0       |
| 21  | D-02    | Execution Management System | 6   | 3   | P0       |
| 22  | D-03    | Portfolio Management System | 5   | 3   | P1       |
| 23  | D-04    | Market Data Service         | 6   | 3   | P0       |
| 24  | D-05    | Pricing Engine              | 5   | 2   | P1       |
| 25  | D-06    | Risk Engine                 | 6   | 3   | P0       |
| 26  | D-07    | Compliance Engine           | 6   | 3   | P0       |
| 27  | D-08    | Market Surveillance         | 7   | 2   | P1       |
| 28  | D-09    | Post-Trade Processing       | 6   | 3   | P0       |
| 29  | D-10    | Regulatory Reporting        | 8   | 3   | P1       |
| 30  | D-11    | Reference Data Service      | 5   | 2   | P0       |
| 31  | D-12    | Corporate Actions           | 6   | 3   | P1       |
| 32  | D-13    | Client Money Reconciliation | 9   | 5   | P0       |
| 33  | D-14    | Sanctions Screening         | 8   | 5   | P0       |

## 3.3 Supporting Epics (9)

| #   | Epic ID | Name                   | FRs | ACs | Priority |
| --- | ------- | ---------------------- | --- | --- | -------- |
| 34  | W-01    | Workflow Orchestration | 10  | 7   | P1       |
| 35  | W-02    | Client Onboarding      | 10  | 7   | P1       |
| 36  | O-01    | Operator Console       | 10  | 7   | P2       |
| 37  | P-01    | Pack Certification     | 10  | 7   | P2       |
| 38  | R-01    | Regulator Portal       | 10  | 7   | P1       |
| 39  | R-02    | Incident Response      | 8   | 5   | P1       |
| 40  | T-01    | Integration Testing    | 10  | 7   | P1       |
| 41  | T-02    | Chaos Engineering      | 8   | 5   | P2       |
| 42  | PU-004  | Platform Manifest      | 6   | 3   | P1       |

**Totals: 42 Epics | 341 Functional Requirements | 192 Acceptance Criteria**

## 3.4 Epic Validation

- ✅ **Completeness**: All 33 LLD modules + 9 supporting concerns covered
- ✅ **Architecture Alignment**: Each epic maps to exactly one LLD bounded context
- ✅ **No Duplication**: No overlapping responsibilities between epics
- ✅ **No Missing Capabilities**: Cross-referenced against C4 containers and HLD layers
- ✅ **Dependency Model**: Validated with one intentional bootstrap cycle between K-05 and K-07, with staged execution defined in the dependency matrix and current execution plan

---

# 4. FEATURE BREAKDOWN

## 4.1 Kernel Features

### Epic K-05: Event Bus, Event Store & Workflow Orchestration

| Feature ID | Feature                                      | Stories Est. |
| ---------- | -------------------------------------------- | ------------ |
| K05-F01    | Append-only event store (PostgreSQL + Kafka) | 5            |
| K05-F02    | Schema registry & validation                 | 3            |
| K05-F03    | At-least-once delivery with consumer groups  | 4            |
| K05-F04    | Idempotency framework (dedup store)          | 3            |
| K05-F05    | Saga orchestration engine & compensation     | 5            |
| K05-F06    | Event replay & projection rebuild            | 4            |
| K05-F07    | Backpressure & flow control                  | 2            |
| K05-F08    | DLQ integration                              | 2            |
| K05-F09    | Schema evolution (backward compatibility)    | 2            |
| K05-F10    | Trace correlation for sagas                  | 2            |

### Epic K-01: Identity & Access Management

| Feature ID | Feature                                    | Stories Est. |
| ---------- | ------------------------------------------ | ------------ |
| K01-F01    | OAuth 2.0 + JWT authentication with MFA    | 5            |
| K01-F02    | Multi-tenant session management            | 3            |
| K01-F03    | RBAC + ABAC authorization engine           | 4            |
| K01-F04    | National ID adapter plugin interface (T3)  | 2            |
| K01-F05    | Service-to-service identity (mTLS)         | 2            |
| K01-F06    | Audit logging integration                  | 2            |
| K01-F07    | Beneficial ownership graph                 | 3            |
| K01-F08    | Approval rate limiting & anomaly detection | 2            |

### Epic K-02: Configuration Engine

| Feature ID | Feature                          | Stories Est. |
| ---------- | -------------------------------- | ------------ |
| K02-F01    | Schema registration & validation | 3            |
| K02-F02    | 5-level hierarchy resolution     | 3            |
| K02-F03    | Hot-reload with canary rollout   | 3            |
| K02-F04    | Dual-calendar effective dates    | 2            |
| K02-F05    | Air-gap signed config bundles    | 2            |
| K02-F06    | Maker-checker for config changes | 2            |
| K02-F07    | CQRS command/query separation    | 2            |

### Epic K-07: Audit Framework

| Feature ID | Feature                           | Stories Est. |
| ---------- | --------------------------------- | ------------ |
| K07-F01    | Audit SDK enforcement             | 2            |
| K07-F02    | Hash-chain immutability (SHA-256) | 3            |
| K07-F03    | Standardized event schema         | 2            |
| K07-F04    | Evidence export (CSV/JSON/PDF)    | 3            |
| K07-F05    | Retention policy enforcement      | 2            |
| K07-F06    | Maker-checker linkage             | 2            |
| K07-F07    | External hash anchoring           | 2            |

### Epic K-15: Dual-Calendar Service

| Feature ID | Feature                                   | Stories Est. |
| ---------- | ----------------------------------------- | ------------ |
| K15-F01    | BS ↔ Gregorian conversion (JDN + lookup)  | 3            |
| K15-F02    | DualDate generation & storage enforcement | 2            |
| K15-F03    | Holiday calendar management (T1 config)   | 2            |
| K15-F04    | Fiscal year boundary calculation          | 2            |
| K15-F05    | Settlement T+n with weekend awareness     | 2            |
| K15-F06    | Edge cases & leap year handling           | 2            |

### Epic K-16: Ledger Framework

| Feature ID | Feature                                    | Stories Est. |
| ---------- | ------------------------------------------ | ------------ |
| K16-F01    | Double-entry posting engine                | 4            |
| K16-F02    | Append-only storage (REVOKE UPDATE/DELETE) | 2            |
| K16-F03    | Temporal balance queries & snapshots       | 3            |
| K16-F04    | Multi-currency / multi-asset support       | 3            |
| K16-F05    | Reconciliation engine with break aging     | 3            |
| K16-F06    | Chart of accounts (T1 config)              | 2            |
| K16-F07    | Precision & rounding rules                 | 2            |

### Epic K-03: Policy / Rules Engine

| Feature ID | Feature                           | Stories Est. |
| ---------- | --------------------------------- | ------------ |
| K03-F01    | OPA/Rego evaluation runtime       | 3            |
| K03-F02    | T2 sandbox isolation              | 3            |
| K03-F03    | Hot-reload of rule packs          | 2            |
| K03-F04    | Jurisdiction-specific routing     | 2            |
| K03-F05    | Maker-checker for rule deployment | 2            |
| K03-F06    | Circuit breaker & degraded mode   | 2            |

### Epic K-04: Plugin Runtime & SDK

| Feature ID | Feature                                   | Stories Est. |
| ---------- | ----------------------------------------- | ------------ |
| K04-F01    | Ed25519 signature verification            | 2            |
| K04-F02    | Tier-based isolation (T1/T2/T3)           | 4            |
| K04-F03    | Capability-based access control           | 2            |
| K04-F04    | Version compatibility enforcement         | 2            |
| K04-F05    | Hot-swap without downtime                 | 3            |
| K04-F06    | Resource quotas & exfiltration prevention | 2            |

### Epic K-06: Observability

| Feature ID | Feature                                 | Stories Est. |
| ---------- | --------------------------------------- | ------------ |
| K06-F01    | Unified observability SDK               | 3            |
| K06-F02    | Context propagation (correlation IDs)   | 2            |
| K06-F03    | Prometheus metrics + Grafana dashboards | 3            |
| K06-F04    | Jaeger distributed tracing              | 2            |
| K06-F05    | ELK centralized logging                 | 2            |
| K06-F06    | SLO/SLA framework & error budgets       | 3            |
| K06-F07    | PII detection & masking                 | 2            |
| K06-F08    | Alerting engine                         | 2            |

### Epic K-11: API Gateway

| Feature ID | Feature                                    | Stories Est. |
| ---------- | ------------------------------------------ | ------------ |
| K11-F01    | Request routing & service discovery        | 3            |
| K11-F02    | JWT + mTLS authentication                  | 2            |
| K11-F03    | Rate limiting (tenant-aware, token bucket) | 2            |
| K11-F04    | Jurisdiction-aware routing                 | 2            |
| K11-F05    | WAF integration (OWASP Top 10)             | 2            |
| K11-F06    | OpenAPI schema validation                  | 2            |

### Epic K-14: Secrets Management

| Feature ID | Feature                          | Stories Est. |
| ---------- | -------------------------------- | ------------ |
| K14-F01    | Multi-provider vault abstraction | 3            |
| K14-F02    | Automatic secret rotation        | 3            |
| K14-F03    | Certificate lifecycle management | 2            |
| K14-F04    | Break-glass access with MFA      | 2            |
| K14-F05    | HSM integration                  | 2            |
| K14-F06    | Secret versioning & rollback     | 2            |

### Epic K-17: Distributed Transaction Coordinator

| Feature ID | Feature                              | Stories Est. |
| ---------- | ------------------------------------ | ------------ |
| K17-F01    | Transactional outbox pattern         | 3            |
| K17-F02    | Version vectors for causal ordering  | 3            |
| K17-F03    | Saga state machine with compensation | 4            |
| K17-F04    | Idempotent command processing        | 2            |
| K17-F05    | Saga definition registry             | 2            |

### Epic K-18: Resilience Patterns

| Feature ID | Feature                              | Stories Est. |
| ---------- | ------------------------------------ | ------------ |
| K18-F01    | Circuit breaker library              | 3            |
| K18-F02    | Bulkhead isolation                   | 2            |
| K18-F03    | Retry policies with backoff & jitter | 2            |
| K18-F04    | Timeout management                   | 2            |
| K18-F05    | Pre-defined resilience profiles      | 2            |
| K18-F06    | Dependency health dashboard          | 2            |

### Epic K-08: Data Governance

| Feature ID | Feature                             | Stories Est. |
| ---------- | ----------------------------------- | ------------ |
| K08-F01    | Data lineage graph                  | 3            |
| K08-F02    | Residency enforcement               | 2            |
| K08-F03    | Retention lifecycle management      | 2            |
| K08-F04    | Encryption abstraction              | 2            |
| K08-F05    | GDPR data subject rights            | 3            |
| K08-F06    | Data classification & PII detection | 2            |

### Epic K-09: AI Governance

| Feature ID | Feature                               | Stories Est. |
| ---------- | ------------------------------------- | ------------ |
| K09-F01    | Model registry & versioning           | 3            |
| K09-F02    | Explainability (SHAP/LIME)            | 3            |
| K09-F03    | HITL override mechanism               | 2            |
| K09-F04    | Drift detection (PSI) & auto-rollback | 3            |
| K09-F05    | Prompt injection prevention           | 2            |
| K09-F06    | A/B testing framework                 | 2            |

### Epic K-10: Deployment Abstraction

| Feature ID | Feature                       | Stories Est. |
| ---------- | ----------------------------- | ------------ |
| K10-F01    | Service Deployment Pipeline   | 3            |
| K10-F02    | Environment Management        | 2            |
| K10-F03    | Rollback & Recovery           | 2            |
| K10-F04    | Configuration Drift Detection | 2            |
| K10-F05    | Resource Scaling              | 2            |
| K10-F06    | Deployment Dashboard          | 1            |

### Epic K-13: Admin Portal

| Feature ID | Feature                   | Stories Est. |
| ---------- | ------------------------- | ------------ |
| K13-F01    | Platform Configuration UI | 3            |
| K13-F02    | User & Role Management UI | 3            |
| K13-F03    | Plugin Lifecycle UI       | 3            |
| K13-F04    | Audit Log Explorer        | 2            |
| K13-F05    | Observability Hub         | 2            |
| K13-F06    | Maker-Checker Task Center | 1            |

### Epic K-19: DLQ Management

| Feature ID | Feature                           | Stories Est. |
| ---------- | --------------------------------- | ------------ |
| K19-F01    | DLQ monitoring dashboard          | 2            |
| K19-F02    | Threshold alerting                | 2            |
| K19-F03    | RCA requirement before replay     | 2            |
| K19-F04    | Safe replay with dry-run          | 3            |
| K19-F05    | Poison pill quarantine            | 2            |
| K19-F06    | Auto-retry for transient failures | 2            |

### Epic K-12: Platform SDK

| Feature ID | Feature                                       | Stories Est. |
| ---------- | --------------------------------------------- | ------------ |
| K12-F01    | SDK code generator (OpenAPI/Protobuf)         | 4            |
| K12-F02    | Multi-language targets (Go/Java/Python/TS/C#) | 5            |
| K12-F03    | Error translation & middleware hooks          | 2            |
| K12-F04    | Connection pooling & circuit breaker          | 2            |
| K12-F05    | Version management & publishing               | 2            |

## 4.2 Domain Features

### Epic D-01: Order Management System

| Feature ID | Feature                              | Stories Est. |
| ---------- | ------------------------------------ | ------------ |
| D01-F01    | Order capture & validation           | 3            |
| D01-F02    | Order state machine (9 states)       | 4            |
| D01-F03    | Pre-trade evaluation pipeline (K-03) | 3            |
| D01-F04    | Maker-checker for large orders       | 2            |
| D01-F05    | Order routing engine                 | 3            |
| D01-F06    | Position update projections          | 3            |

### Epic D-02: Execution Management System

| Feature ID | Feature                             | Stories Est. |
| ---------- | ----------------------------------- | ------------ |
| D02-F01    | Smart Order Router (SOR)            | 4            |
| D02-F02    | Execution algorithms (VWAP/TWAP/IS) | 5            |
| D02-F03    | T3 exchange adapter framework (FIX) | 4            |
| D02-F04    | Transaction Cost Analysis (TCA)     | 3            |
| D02-F05    | Circuit breaker handling            | 2            |
| D02-F06    | Partial fill aggregation            | 2            |

### Epic D-04: Market Data Service

| Feature ID | Feature                                       | Stories Est. |
| ---------- | --------------------------------------------- | ------------ |
| D04-F01    | Feed normalization engine                     | 3            |
| D04-F02    | L1/L2/L3 order book distribution              | 3            |
| D04-F03    | Feed arbitration (primary/secondary)          | 2            |
| D04-F04    | Circuit breaker detection & MarketHalt events | 2            |
| D04-F05    | Historical tick storage (TimescaleDB)         | 3            |
| D04-F06    | WebSocket streaming for subscribers           | 2            |

### Epic D-06: Risk Engine

| Feature ID | Feature                                    | Stories Est. |
| ---------- | ------------------------------------------ | ------------ |
| D06-F01    | Pre-trade risk checks (<5ms)               | 3            |
| D06-F02    | VaR calculation (parametric/historical/MC) | 5            |
| D06-F03    | Margin calculation engine                  | 3            |
| D06-F04    | Margin call generation & escalation        | 3            |
| D06-F05    | Forced liquidation trigger                 | 2            |
| D06-F06    | Stress testing & scenario analysis         | 3            |

### Epic D-07: Compliance Engine

| Feature ID | Feature                                  | Stories Est. |
| ---------- | ---------------------------------------- | ------------ |
| D07-F01    | Rule orchestration pipeline (K-03)       | 3            |
| D07-F02    | Lock-in period enforcement (BS calendar) | 2            |
| D07-F03    | KYC/AML gateway                          | 3            |
| D07-F04    | Beneficial ownership tracking            | 2            |
| D07-F05    | Restricted list management               | 2            |
| D07-F06    | Compliance attestation workflows         | 2            |

### Epic D-09: Post-Trade Processing

| Feature ID | Feature                                 | Stories Est. |
| ---------- | --------------------------------------- | ------------ |
| D09-F01    | Trade confirmation generation           | 2            |
| D09-F02    | Netting engine (bilateral/multilateral) | 3            |
| D09-F03    | Settlement lifecycle (T+0 to T+n)       | 4            |
| D09-F04    | CSD/depository adapter (T3)             | 3            |
| D09-F05    | Ledger posting (K-16)                   | 2            |
| D09-F06    | Settlement failure & buy-in management  | 2            |

### Epic D-11: Reference Data

| Feature ID | Feature                                      | Stories Est. |
| ---------- | -------------------------------------------- | ------------ |
| D11-F01    | Instrument master data management            | 3            |
| D11-F02    | Entity master (issuers, brokers, custodians) | 2            |
| D11-F03    | Benchmark/index definitions                  | 2            |
| D11-F04    | External feed adapters (T3)                  | 2            |
| D11-F05    | Point-in-time historical queries             | 2            |

### Epic D-03: Portfolio Management System

| Feature ID | Feature                               | Stories Est. |
| ---------- | ------------------------------------- | ------------ |
| D03-F01    | Portfolio construction & optimization | 3            |
| D03-F02    | NAV calculation (TWR/MWR)             | 3            |
| D03-F03    | Drift detection & rebalancing         | 3            |
| D03-F04    | What-if scenario analysis             | 2            |
| D03-F05    | Maker-checker for rebalance orders    | 2            |

### Epic D-05: Pricing Engine

| Feature ID | Feature                            | Stories Est. |
| ---------- | ---------------------------------- | ------------ |
| D05-F01    | Real-time & EOD instrument pricing | 3            |
| D05-F02    | Yield curve bootstrapping          | 3            |
| D05-F03    | T3 pricing model plugins           | 2            |
| D05-F04    | Mark-to-market batch               | 2            |
| D05-F05    | Price challenge workflow           | 2            |

### Epic D-08: Market Surveillance

| Feature ID | Feature                     | Stories Est. |
| ---------- | --------------------------- | ------------ |
| D08-F01    | Wash trade detection        | 3            |
| D08-F02    | Spoofing/layering detection | 3            |
| D08-F03    | Front-running detection     | 2            |
| D08-F04    | AI anomaly detection (K-09) | 3            |
| D08-F05    | Case management & evidence  | 3            |

### Epic D-10: Regulatory Reporting

| Feature ID | Feature                                 | Stories Est. |
| ---------- | --------------------------------------- | ------------ |
| D10-F01    | Report definition & template management | 2            |
| D10-F02    | Multi-format rendering (PDF/CSV/XBRL)   | 3            |
| D10-F03    | T3 regulatory portal submission         | 2            |
| D10-F04    | ACK/NACK tracking                       | 2            |
| D10-F05    | Real-time trade reporting               | 2            |
| D10-F06    | Report reconciliation                   | 2            |

### Epic D-12: Corporate Actions

| Feature ID | Feature                                 | Stories Est. |
| ---------- | --------------------------------------- | ------------ |
| D12-F01    | Corporate action lifecycle engine       | 3            |
| D12-F02    | Entitlement calculation                 | 3            |
| D12-F03    | Tax withholding (T2 jurisdiction rules) | 2            |
| D12-F04    | Election management (rights issues)     | 2            |
| D12-F05    | Ledger posting (K-16 securities + cash) | 2            |

### Epic D-13: Client Money Reconciliation

| Feature ID | Feature                            | Stories Est. |
| ---------- | ---------------------------------- | ------------ |
| D13-F01    | Daily automated recon workflow     | 3            |
| D13-F02    | Statement ingestion (multi-source) | 3            |
| D13-F03    | Matching engine                    | 3            |
| D13-F04    | Break detection & classification   | 2            |
| D13-F05    | Segregation verification           | 2            |
| D13-F06    | Escalation workflow (aging tiers)  | 2            |

### Epic D-14: Sanctions Screening

| Feature ID | Feature                                   | Stories Est. |
| ---------- | ----------------------------------------- | ------------ |
| D14-F01    | Real-time screening engine (<50ms)        | 3            |
| D14-F02    | Fuzzy matching (Levenshtein/Jaro-Winkler) | 3            |
| D14-F03    | Match review workflow (maker-checker)     | 2            |
| D14-F04    | Sanctions list management & ingestion     | 2            |
| D14-F05    | Batch re-screening                        | 2            |
| D14-F06    | Air-gap signed list bundles               | 2            |

## 4.3 Supporting Features

### Epic W-01: Workflow Orchestration

| Feature ID | Feature                       | Stories Est. |
| ---------- | ----------------------------- | ------------ |
| W01-F01    | Workflow definition DSL       | 3            |
| W01-F02    | Workflow Execution Engine     | 4            |
| W01-F03    | Workflow Monitoring & History | 2            |
| W01-F04    | Built-in Financial Workflows  | 4            |
| W01-F05    | Sub-Workflow & Reuse          | 1            |
| W01-F06    | Workflow Versioning           | 2            |

### Epic W-02: Client Onboarding

| Feature ID | Feature                   | Stories Est. |
| ---------- | ------------------------- | ------------ |
| W02-F01    | KYC Document Collection   | 3            |
| W02-F02    | Identity Verification     | 3            |
| W02-F03    | Risk Assessment & Scoring | 2            |
| W02-F04    | Account Setup             | 2            |
| W02-F05    | Onboarding Dashboard      | 1            |
| W02-F06    | Periodic Review           | 2            |

### Epic R-01: Regulator Portal

| Feature ID | Feature                           | Stories Est. |
| ---------- | --------------------------------- | ------------ |
| R01-F01    | Regulator Authentication & Access | 2            |
| R01-F02    | On-Demand Data Access             | 3            |
| R01-F03    | Investigation Workflow            | 2            |
| R01-F04    | Transparency Reports              | 2            |
| R01-F05    | Regulatory Dashboard              | 1            |
| R01-F06    | AI Regulatory Intelligence        | 1            |

### Epic R-02: Incident Response & Escalation

| Feature ID | Feature                  | Stories Est. |
| ---------- | ------------------------ | ------------ |
| R02-F01    | Incident detection       | 2            |
| R02-F02    | Communications           | 2            |
| R02-F03    | Post-incident review     | 2            |
| R02-F04    | Incident analytics       | 2            |
| R02-F05    | Incident playbooks       | 2            |
| R02-F06    | AI incident intelligence | 2            |

### Epic O-01: Operator Console

| Feature ID | Feature                       | Stories Est. |
| ---------- | ----------------------------- | ------------ |
| O01-F01    | Multi-firm tenancy management | 3            |
| O01-F02    | License & feature flags       | 2            |
| O01-F03    | Platform health & ops         | 3            |
| O01-F04    | Multi-jurisdiction management | 2            |
| O01-F05    | Billing & commercial          | 2            |
| O01-F06    | AI operations intelligence    | 2            |

### Epic P-01: Pack Certification

| Feature ID | Feature                     | Stories Est. |
| ---------- | --------------------------- | ------------ |
| P01-F01    | Pack testing framework      | 3            |
| P01-F02    | Compatibility validation    | 2            |
| P01-F03    | Security scanning           | 2            |
| P01-F04    | Pack registry & marketplace | 3            |
| P01-F05    | Deprecation management      | 2            |

### Epic PU-004: Platform Manifest

| Feature ID | Feature                            | Stories Est. |
| ---------- | ---------------------------------- | ------------ |
| PU04-F01   | Manifest schema (source of truth)  | 2            |
| PU04-F02   | Compatibility validation engine    | 2            |
| PU04-F03   | Append-only history & diff tooling | 2            |
| PU04-F04   | Rollback orchestration             | 2            |

### Epic T-01: Integration Testing

| Feature ID | Feature                   | Stories Est. |
| ---------- | ------------------------- | ------------ |
| T01-F01    | E2E scenario library      | 4            |
| T01-F02    | Test automation framework | 3            |
| T01-F03    | Test data management      | 2            |
| T01-F04    | Performance testing suite | 3            |
| T01-F05    | Security testing suite    | 2            |

### Epic T-02: Chaos Engineering

| Feature ID | Feature                     | Stories Est. |
| ---------- | --------------------------- | ------------ |
| T02-F01    | Fault injection framework   | 3            |
| T02-F02    | Pre-defined chaos scenarios | 2            |
| T02-F03    | DR drill automation         | 2            |
| T02-F04    | Resilience scorecard        | 2            |
| T02-F05    | GameDay framework           | 2            |

---

# 5. STORY BACKLOG (Representative Stories)

> **Full backlog**: ~700 stories estimated across 42 epics. Below are **representative stories** for each critical path epic, using the mandatory template. The complete backlog follows this pattern for all features.

---

## STORY-K05-001

**Title**: Implement append-only event store with PostgreSQL

**Epic**: K-05 Event Bus, Event Store & Workflows  
**Feature**: K05-F01 Append-only event store

**Description**: Implement the foundational event store backed by PostgreSQL with append-only semantics. All state changes in the platform are captured as immutable events. The store must support high-throughput writes, sequence numbering per aggregate, and dual-calendar timestamps.

**Business Value**: Foundation of the entire event-sourced architecture — every other module depends on reliable event storage.

**Technical Scope**:

- PostgreSQL event_store table with REVOKE UPDATE/DELETE
- Event schema (event_id, event_type, aggregate_id, sequence_number, data, metadata, timestamp_bs, timestamp_gregorian)
- Write API: `appendEvent(aggregateId, eventType, data, metadata)`
- Sequence conflict detection (optimistic concurrency)
- Database migration scripts

**Dependencies**: PostgreSQL infrastructure provisioned

**Acceptance Criteria**:

- Given valid event data, When appendEvent is called, Then event is stored with monotonically increasing sequence number
- Given duplicate event_id, When appendEvent is called, Then idempotent write (no duplicate) returns original
- Given an attempt to UPDATE or DELETE an event, When SQL is executed, Then database rejects the operation

**Edge Cases**:

- Concurrent writes to same aggregate (sequence collision)
- Event payload exceeding size limit (>1MB)
- Database connection pool exhaustion under load
- Clock skew between BS and Gregorian timestamps

**Test Plan**:

- **Unit Tests**: `appendEvent_validPayload_returnsSequence`, `appendEvent_duplicateId_idempotent`, `appendEvent_sequenceConflict_throws`
- **Integration Tests**: Event store write/read roundtrip, concurrent writer stress test
- **E2E Tests**: Full event publish → store → consume chain
- **Negative Tests**: Oversized payload rejection, corrupted JSON rejection
- **Performance Tests**: 10,000 events/sec sustained write throughput
- **Security Tests**: SQL injection prevention, UPDATE/DELETE restriction verification

**Story Points**: 5  
**Team**: Alpha (Platform)

---

## STORY-K05-002

**Title**: Implement Kafka event publisher with at-least-once delivery

**Epic**: K-05 Event Bus, Event Store & Workflows  
**Feature**: K05-F03 At-least-once delivery

**Description**: Implement the Kafka producer that publishes events from the event store to topic partitions. Must guarantee at-least-once delivery using the transactional outbox pattern (coordinate with K-17). Supports partition key routing by aggregate ID.

**Business Value**: Enables all downstream consumers (projections, sagas, audit) to receive events reliably.

**Technical Scope**:

- Kafka producer with partition key = aggregate_id
- Outbox relay (poll unpublished events, publish, mark published)
- Dead-letter handling for publish failures
- Metrics: publish latency, publish failures, outbox lag

**Dependencies**: STORY-K05-001 (event store), Kafka cluster provisioned

**Acceptance Criteria**:

- Given event stored in event_store, When outbox relay runs, Then event published to correct Kafka topic within 100ms
- Given Kafka unavailable, When publish fails, Then event remains in outbox for retry (no data loss)
- Given consumer restart, When consumer comes back, Then resumes from last committed offset

**Edge Cases**:

- Kafka broker failure during publish (retry with backoff)
- Outbox relay crash mid-batch (idempotent re-publish)
- Topic partition rebalance during publish

**Test Plan**:

- **Unit Tests**: `publishEvent_success_marksOutbox`, `publishEvent_kafkaDown_retries`
- **Integration Tests**: Outbox relay → Kafka → consumer chain
- **Performance Tests**: 50,000 events/sec throughput
- **Negative Tests**: Kafka cluster down for 30 seconds (no data loss)

**Story Points**: 5  
**Team**: Alpha (Platform)

---

## STORY-K01-001

**Title**: Implement OAuth 2.0 authentication with JWT token generation

**Epic**: K-01 IAM  
**Feature**: K01-F01 OAuth 2.0 + JWT authentication

**Description**: Implement the authentication endpoint supporting OAuth 2.0 authorization code flow and client credentials flow. Generate JWT access tokens with tenant-scoped claims. Tokens must include dual-calendar timestamps.

**Business Value**: Core identity infrastructure — no service operates without authenticated users/services.

**Technical Scope**:

- POST /auth/token endpoint (authorization_code, client_credentials, refresh_token)
- JWT generation with RS256 signing (kid rotation)
- Claims: sub, tenant_id, roles, permissions, iat, exp, iat_bs
- Refresh token rotation with family-based revocation
- Rate limiting on auth endpoints

**Dependencies**: K-14 (JWT signing keys), K-15 (dual-calendar for iat_bs)

**Acceptance Criteria**:

- Given valid credentials, When POST /auth/token called, Then JWT returned with tenant-scoped claims
- Given expired access token, When refresh_token used, Then new access token issued
- Given invalid credentials after 5 attempts, When POST /auth/token called, Then account locked for 15 minutes

**Edge Cases**:

- Clock skew between services (JWT nbf/exp validation)
- Concurrent refresh token usage (family rotation revocation)
- Token size exceeding cookie limits

**Test Plan**:

- **Unit Tests**: `generateToken_validCreds_returnsJWT`, `refreshToken_valid_rotatesFamily`, `login_lockedAccount_returns429`
- **Integration Tests**: Full login → token → API call → refresh cycle
- **Security Tests**: Brute force protection, token tampering detection, PKCE validation
- **Performance Tests**: 5,000 auth/sec sustained

**Story Points**: 5  
**Team**: Alpha (Platform)

---

## STORY-D01-001

**Title**: Implement order capture API with validation

**Epic**: D-01 OMS  
**Feature**: D01-F01 Order capture & validation

**Description**: Implement the order capture REST API (POST /orders) that accepts order requests, validates mandatory fields, checks instrument tradability via D-11, enforces minimum lot sizes per jurisdiction (T1 config), and creates the order in PENDING state. Emits OrderPlaced event.

**Business Value**: Core trading capability — without order capture, no trading occurs.

**Technical Scope**:

- POST /orders REST endpoint
- Order schema validation (instrument, quantity, side, order_type, price, client_id)
- Instrument tradability check (D-11 reference data lookup)
- Minimum lot size validation (T1 config per jurisdiction via K-02)
- Order entity creation with PENDING state
- OrderPlaced event emission (K-05)
- Dual-calendar timestamp (K-15)

**Dependencies**: K-01 (auth), K-02 (config), K-05 (events), K-15 (calendar), D-11 (reference data)

**Acceptance Criteria**:

- Given valid order request, When POST /orders is called, Then order created in PENDING state, OrderPlaced emitted
- Given sell quantity < minimum lot (e.g., 5 kitta where min=10), When POST /orders called, Then synchronous rejection 422
- Given untradable instrument (suspended), When POST /orders called, Then rejection with reason "INSTRUMENT_SUSPENDED"

**Edge Cases**:

- Concurrent order submission for same client (idempotency key)
- Instrument suspended between validation and creation
- Market closed at order submission time

**Test Plan**:

- **Unit Tests**: `captureOrder_valid_createsPending`, `captureOrder_belowMinLot_rejects`, `captureOrder_suspendedInstrument_rejects`
- **Integration Tests**: Order → event → position projection chain
- **E2E Tests**: Full order placement through UI
- **Negative Tests**: Missing fields, invalid instrument, duplicate idempotency key
- **Performance Tests**: 10,000 orders/sec sustained
- **Security Tests**: Authorization check (client can only place own orders)

**Story Points**: 5  
**Team**: Beta (Backend)

---

## STORY-D01-002

**Title**: Implement order state machine with 9-state transitions

**Epic**: D-01 OMS  
**Feature**: D01-F02 Order state machine

**Description**: Implement the order state machine supporting 9 states: DRAFT → PENDING → PENDING_APPROVAL → APPROVED → ROUTED → PARTIALLY_FILLED → FILLED → CANCELLED → REJECTED. Each transition emits a domain event and validates allowed transitions.

**Business Value**: Ensures order lifecycle integrity — invalid state transitions are impossible, providing audit-safe order history.

**Technical Scope**:

- State machine: allowed transitions matrix
- Command handlers for each transition (submit, approve, route, fill, cancel, reject)
- Domain event per transition (OrderSubmitted, OrderApproved, OrderRouted, etc.)
- Event-sourced reconstruction from event stream

**Dependencies**: STORY-D01-001 (order entity), K-05 (events)

**Acceptance Criteria**:

- Given order in PENDING state, When approved, Then state → APPROVED, OrderApproved event emitted
- Given order in FILLED state, When cancel attempted, Then transition rejected (invalid)
- Given order event stream, When reconstructed, Then current state matches latest event

**Edge Cases**:

- Concurrent state transition commands (optimistic locking)
- Partial fill followed by cancel (PARTIALLY_FILLED → CANCELLED)
- Replay produces consistent state

**Test Plan**:

- **Unit Tests**: All 9 × 9 state transition matrix validations
- **Integration Tests**: Event-sourced reconstruction consistency
- **Performance Tests**: 100K state transitions/sec

**Story Points**: 5  
**Team**: Beta (Backend)

---

## STORY-D06-001

**Title**: Implement pre-trade risk check pipeline (<5ms P99)

**Epic**: D-06 Risk Engine  
**Feature**: D06-F01 Pre-trade risk checks

**Description**: Implement the synchronous pre-trade risk validation pipeline called before every order is routed. Checks: margin sufficiency, position limits, concentration limits, and jurisdiction-specific rules via K-03. Must complete within 5ms P99.

**Business Value**: Prevents unauthorized risk exposure — regulatory requirement for every trade.

**Technical Scope**:

- RiskCheckRequest → RiskCheckResponse pipeline
- Margin sufficiency check (available margin ≥ required margin)
- Position limit check (max qty per instrument per client)
- Concentration limit check (max % portfolio in single instrument)
- K-03 rules engine integration for jurisdiction rules
- Redis cache for hot risk data (positions, limits)
- Prometheus latency histogram

**Dependencies**: K-02 (limits config), K-03 (rules), K-05 (events), D-03 (positions), Redis

**Acceptance Criteria**:

- Given order requiring $10K margin with $5K available, When risk check runs, Then synchronous DENY < 2ms
- Given order within all limits, When risk check runs, Then APPROVE < 5ms P99
- Given jurisdiction rule "max 5% single stock", When order exceeds, Then DENY with rule reference

**Edge Cases**:

- Redis cache miss (fallback to DB with degraded latency)
- Race condition: two orders consuming same margin simultaneously
- K-03 timeout (circuit breaker → default deny)

**Test Plan**:

- **Unit Tests**: `riskCheck_insufficientMargin_deny`, `riskCheck_withinLimits_approve`, `riskCheck_concentrationExceeded_deny`
- **Integration Tests**: Full order → risk check → routing chain
- **Performance Tests**: 50,000 risk checks/sec sustained at < 5ms P99
- **Negative Tests**: All checks failing simultaneously, partial data availability
- **Security Tests**: Risk check bypass attempt (must be impossible)

**Story Points**: 8  
**Team**: Beta (Backend)

---

## STORY-K16-001

**Title**: Implement double-entry posting engine with balance enforcement

**Epic**: K-16 Ledger Framework  
**Feature**: K16-F01 Double-entry posting engine

**Description**: Implement the core ledger posting engine that enforces double-entry bookkeeping (every debit has equal credit). Supports multi-currency postings with configurable precision. All entries are append-only (REVOKE UPDATE/DELETE on PostgreSQL).

**Business Value**: Financial integrity foundation — every asset movement, fee, dividend, margin call flows through the ledger.

**Technical Scope**:

- POST /ledger/postings endpoint
- JournalEntry entity: debit_account, credit_account, amount, currency, reference
- Balance enforcement: sum(debits) == sum(credits) per journal entry
- Append-only storage (REVOKE UPDATE/DELETE)
- Account balance projection (materialized view with triggers)
- Dual-calendar timestamps

**Dependencies**: K-02 (chart of accounts), K-05 (events), K-15 (calendar)

**Acceptance Criteria**:

- Given valid journal entry (debit=5000, credit=5000), When POST /ledger/postings, Then entry created, balances updated
- Given unbalanced entry (debit=5000, credit=4999), When posted, Then rejected with UNBALANCED error
- Given attempt to UPDATE ledger entry, When SQL executed, Then database rejects

**Edge Cases**:

- Multi-leg journal (3+ accounts) — sum must still balance
- Currency precision mismatch (NPR 2 decimals vs BTC 8 decimals)
- Concurrent postings to same account (consistent balance)

**Test Plan**:

- **Unit Tests**: `post_balanced_creates`, `post_unbalanced_rejects`, `post_multiLeg_balances`
- **Integration Tests**: Posting → balance query → reconciliation chain
- **Performance Tests**: 20,000 postings/sec sustained
- **Security Tests**: UPDATE/DELETE restriction, unauthorized account access

**Story Points**: 8  
**Team**: Alpha (Platform)

---

> **Pattern continues for all ~700 stories.** Each story follows the identical template above with: ID, Title, Epic, Feature, Description, Business Value, Technical Scope, Dependencies, Acceptance Criteria (Given/When/Then), Edge Cases, Test Plan (unit/integration/e2e/negative/performance/security), Story Points, and Team Assignment.

---

# 6. STORY ESTIMATIONS SUMMARY

## 6.1 Story Points by Epic

| Epic                     | Features | Est. Stories | Story Points                | Priority |
| ------------------------ | -------- | ------------ | --------------------------- | -------- |
| K-05 Event Bus           | 10       | 32           | 130                         | P0       |
| K-01 IAM                 | 8        | 23           | 90                          | P0       |
| K-02 Configuration       | 7        | 17           | 65                          | P0       |
| K-07 Audit               | 7        | 16           | 56                          | P0       |
| K-15 Dual-Calendar       | 6        | 13           | 42                          | P0       |
| K-16 Ledger              | 7        | 19           | 72                          | P0       |
| K-03 Rules Engine        | 6        | 14           | 52                          | P0       |
| K-04 Plugin Runtime      | 6        | 15           | 58                          | P0       |
| K-06 Observability       | 8        | 19           | 70                          | P0       |
| K-11 API Gateway         | 6        | 13           | 48                          | P0       |
| K-14 Secrets Mgmt        | 6        | 14           | 50                          | P0       |
| K-17 DTC                 | 5        | 14           | 54                          | P0       |
| K-18 Resilience          | 6        | 13           | 44                          | P0       |
| K-08 Data Governance     | 6        | 14           | 48                          | P1       |
| K-09 AI Governance       | 6        | 15           | 55                          | P1       |
| K-10 Deployment          | 5        | 12           | 42                          | P1       |
| K-13 Admin Portal        | 6        | 14           | 46                          | P1       |
| K-19 DLQ                 | 6        | 13           | 44                          | P1       |
| K-12 Platform SDK        | 5        | 15           | 55                          | P2       |
| D-01 OMS                 | 6        | 18           | 68                          | P0       |
| D-02 EMS                 | 6        | 20           | 78                          | P0       |
| D-04 Market Data         | 6        | 15           | 56                          | P0       |
| D-06 Risk Engine         | 6        | 19           | 72                          | P0       |
| D-07 Compliance          | 6        | 14           | 50                          | P0       |
| D-09 Post-Trade          | 6        | 16           | 60                          | P0       |
| D-11 Reference Data      | 5        | 11           | 38                          | P0       |
| D-13 Client Money        | 6        | 15           | 55                          | P0       |
| D-14 Sanctions           | 6        | 14           | 50                          | P0       |
| D-03 PMS                 | 5        | 13           | 48                          | P1       |
| D-05 Pricing Engine      | 5        | 12           | 44                          | P1       |
| D-08 Surveillance        | 5        | 14           | 54                          | P1       |
| D-10 Reg Reporting       | 6        | 13           | 46                          | P1       |
| D-12 Corporate Actions   | 5        | 12           | 44                          | P1       |
| W-01 Workflow            | 6        | 16           | 58                          | P1       |
| W-02 Onboarding          | 6        | 13           | 44                          | P1       |
| R-01 Regulator Portal    | 5        | 11           | 38                          | P1       |
| R-02 Incident Response   | 6        | 12           | 37                          | P1       |
| O-01 Operator Console    | 6        | 14           | 45                          | P2       |
| P-01 Pack Certification  | 5        | 12           | 42                          | P2       |
| PU-004 Platform Manifest | 4        | 8            | 26                          | P1       |
| T-01 Integration Testing | 5        | 14           | 50                          | P1       |
| T-02 Chaos Engineering   | 5        | 11           | 38                          | P2       |
| **TOTALS**               | **248**  | **~607**     | **~2,356 + Infrastructure** |          |

**Infrastructure & DevOps stories** (CI/CD pipeline, repo setup, Kubernetes infra, monitoring infra): **+150 SP**

**Normalization Note**: The feature-to-story rollup above is a historical planning estimate. The current sprinted execution baseline is the normalized backlog in `stories/STORY_INDEX.md`: **654 stories** and **~1,930 story points**.

---

# 7. ENGINEERING CAPACITY PLAN

## 7.1 Team Structure

| Team        | Focus Area                                                              | Members                              | SP/Sprint |
| ----------- | ----------------------------------------------------------------------- | ------------------------------------ | --------- |
| **Alpha**   | Platform Kernel (K-01 through K-19)                                     | 6 engineers (4 BE + 1 FE + 1 DevOps) | 40        |
| **Beta**    | Domain Services: Trading (D-01, D-02, D-06, D-09)                       | 5 engineers (4 BE + 1 QA)            | 35        |
| **Gamma**   | Domain Services: Data & Compliance (D-04, D-05, D-07, D-08, D-11, D-14) | 5 engineers (4 BE + 1 QA)            | 35        |
| **Delta**   | Domain Services: Post-Trade & Finance (D-03, D-10, D-12, D-13)          | 4 engineers (3 BE + 1 QA)            | 28        |
| **Epsilon** | Frontend & Portals (K-13, R-01, O-01, W-02 UI)                          | 4 engineers (3 FE + 1 UX)            | 28        |
| **Zeta**    | DevOps & Infrastructure (K-10, CI/CD, K8s, Monitoring)                  | 3 engineers (2 DevOps + 1 SRE)       | 22        |

**Total**: 27 engineers | **188 SP/sprint** | **30 sprints = 5,640 SP capacity** (buffer: ~2.9x against the current sprinted backlog)

## 7.2 Velocity Assumptions

- Sprint = 2 weeks
- 20% buffer for unplanned work, meetings, spikes
- Ramp-up: Sprints 1-2 at 60% velocity, Sprint 3 at 80%, Sprint 4+ at 100%
- QA embedded in each team (shift-left testing)

---

# 8. SPRINT PLAN

## Phase 1: Foundation (Sprints 1-4)

### Sprint 1 — Infrastructure & Bootstrap

**Focus**: Repository, CI/CD, base infrastructure  
**SP Budget**: 120 (reduced — ramp-up)

| Team    | Stories                                                                  | SP  |
| ------- | ------------------------------------------------------------------------ | --- |
| Zeta    | Monorepo setup, branch strategy, CI/CD pipeline (lint/test/build/deploy) | 22  |
| Zeta    | Kubernetes cluster provisioning (dev/staging), Helm chart templates      | 22  |
| Alpha   | PostgreSQL cluster setup (operational + event store + ledger)            | 8   |
| Alpha   | Kafka cluster setup (3-node dev), topic naming conventions               | 8   |
| Alpha   | Redis cluster setup, ELK stack deployment                                | 8   |
| Alpha   | STORY-K05-001: Event store schema & append-only table                    | 5   |
| Alpha   | STORY-K07-001: Audit log schema & hash-chain foundation                  | 5   |
| Alpha   | STORY-K15-001: BS ↔ Gregorian conversion library                         | 5   |
| Epsilon | Design system setup (shadcn/ui, TailwindCSS, Storybook)                  | 8   |
| All     | Development environment docs, coding standards, PR templates             | 5   |

**Sprint 1 Total**: ~96 SP

### Sprint 2 — Core Kernel Services (Part 1)

**Focus**: Event bus, audit, config, calendar — the 4 foundational kernels

| Team  | Stories                                                    | SP  |
| ----- | ---------------------------------------------------------- | --- |
| Alpha | STORY-K05-002: Kafka publisher with outbox relay           | 5   |
| Alpha | STORY-K05-003: Consumer group framework, offset management | 5   |
| Alpha | STORY-K05-004: Schema registry & validation                | 5   |
| Alpha | STORY-K05-005: Idempotency dedup store                     | 3   |
| Alpha | STORY-K07-002: Audit SDK (log(), hash-chain append)        | 5   |
| Alpha | STORY-K07-003: Audit event schema & retention policies     | 3   |
| Alpha | STORY-K02-001: Schema registration & validation            | 3   |
| Alpha | STORY-K02-002: 5-level hierarchy resolution                | 3   |
| Alpha | STORY-K02-003: Hot-reload with canary rollout              | 3   |
| Alpha | STORY-K15-002: DualDate generation & storage enforcement   | 3   |
| Alpha | STORY-K15-003: Holiday calendar management                 | 2   |

**Sprint 2 Total (Alpha)**: ~40 SP

### Sprint 3 — Core Kernel Services (Part 2)

**Focus**: IAM, secrets, resilience, ledger foundations

| Team  | Stories                                             | SP  |
| ----- | --------------------------------------------------- | --- |
| Alpha | STORY-K01-001: OAuth 2.0 + JWT auth                 | 5   |
| Alpha | STORY-K01-002: Multi-tenant session management      | 3   |
| Alpha | STORY-K14-001: Vault abstraction layer              | 5   |
| Alpha | STORY-K14-002: Auto-rotation framework              | 3   |
| Alpha | STORY-K18-001: Circuit breaker library              | 3   |
| Alpha | STORY-K18-002: Retry policies with backoff          | 2   |
| Alpha | STORY-K16-001: Double-entry posting engine          | 8   |
| Alpha | STORY-K16-002: Append-only storage enforcement      | 3   |
| Alpha | STORY-K17-001: Transactional outbox pattern         | 5   |
| Zeta  | Prometheus + Grafana deployment, base dashboards    | 8   |
| Zeta  | Jaeger tracing deployment, service mesh base config | 5   |

**Sprint 3 Total**: ~50 SP (Alpha: 37, Zeta: 13)

### Sprint 4 — Kernel Completion (Part 1)

**Focus**: IAM completion, rules engine, plugin runtime, API gateway, observability SDK

| Team    | Stories                                             | SP  |
| ------- | --------------------------------------------------- | --- |
| Alpha   | STORY-K01-003: RBAC + ABAC authorization engine     | 5   |
| Alpha   | STORY-K01-004: MFA (TOTP/WebAuthn)                  | 3   |
| Alpha   | STORY-K03-001: OPA/Rego evaluation runtime          | 5   |
| Alpha   | STORY-K03-002: T2 sandbox isolation                 | 3   |
| Alpha   | STORY-K04-001: Ed25519 signature verification       | 3   |
| Alpha   | STORY-K04-002: Tier-based isolation (T1/T2/T3)      | 5   |
| Alpha   | STORY-K11-001: Request routing & service discovery  | 5   |
| Alpha   | STORY-K11-002: JWT + mTLS authentication middleware | 3   |
| Alpha   | STORY-K06-001: Unified observability SDK            | 5   |
| Epsilon | K-13 Admin Portal: Micro-frontend shell setup       | 5   |

**Sprint 4 Total**: ~42 SP (Alpha: 37, Epsilon: 5)

## Phase 2: Domain Foundation (Sprints 5-10)

### Sprint 5 — Reference Data & Market Data

**Focus**: D-11 Reference Data, D-04 Market Data foundations, K-11 completion

| Team  | Stories                                          | SP  |
| ----- | ------------------------------------------------ | --- |
| Gamma | STORY-D11-001: Instrument master data management | 5   |
| Gamma | STORY-D11-002: Entity master                     | 3   |
| Gamma | STORY-D11-003: Change event emission             | 3   |
| Gamma | STORY-D04-001: Feed normalization engine         | 5   |
| Gamma | STORY-D04-002: L1/L2 order book distribution     | 5   |
| Alpha | STORY-K11-003: Rate limiting (tenant-aware)      | 3   |
| Alpha | STORY-K11-004: Jurisdiction-aware routing        | 3   |
| Alpha | STORY-K05-006: Saga orchestration engine         | 8   |
| Alpha | STORY-K05-007: Saga compensation framework       | 5   |
| Zeta  | Istio service mesh deployment, mTLS enforcement  | 8   |

**Sprint 5 Total**: ~48 SP

### Sprint 6 — OMS & Compliance Foundations

**Focus**: D-01 OMS, D-07 Compliance, D-14 Sanctions

| Team  | Stories                                             | SP  |
| ----- | --------------------------------------------------- | --- |
| Beta  | STORY-D01-001: Order capture API                    | 5   |
| Beta  | STORY-D01-002: Order state machine (9 states)       | 5   |
| Beta  | STORY-D01-003: Pre-trade evaluation pipeline (K-03) | 5   |
| Gamma | STORY-D07-001: Rule orchestration pipeline (K-03)   | 5   |
| Gamma | STORY-D07-002: Lock-in period enforcement (BS cal)  | 3   |
| Gamma | STORY-D14-001: Real-time screening engine (<50ms)   | 5   |
| Gamma | STORY-D14-002: Fuzzy matching algorithms            | 5   |
| Alpha | STORY-K03-003: Hot-reload of rule packs             | 3   |
| Alpha | STORY-K03-004: Jurisdiction-specific routing        | 3   |

**Sprint 6 Total**: ~39 SP (Beta: 15, Gamma: 18, Alpha: 6)

### Sprint 7 — Risk Engine & EMS

**Focus**: D-06 Risk, D-02 EMS, OMS completion

| Team  | Stories                                             | SP  |
| ----- | --------------------------------------------------- | --- |
| Beta  | STORY-D06-001: Pre-trade risk check pipeline (<5ms) | 8   |
| Beta  | STORY-D06-002: Margin calculation engine            | 5   |
| Beta  | STORY-D01-004: Maker-checker for large orders       | 3   |
| Beta  | STORY-D01-005: Order routing engine                 | 5   |
| Beta  | STORY-D02-001: Smart Order Router                   | 5   |
| Beta  | STORY-D02-002: T3 exchange adapter framework (FIX)  | 5   |
| Alpha | STORY-K16-003: Temporal balance queries & snapshots | 5   |
| Alpha | STORY-K16-004: Multi-currency support               | 5   |
| Gamma | STORY-D04-003: Feed arbitration                     | 3   |
| Gamma | STORY-D04-004: Circuit breaker detection            | 3   |

**Sprint 7 Total**: ~52 SP (Beta: 31, Alpha: 10, Gamma: 6)

### Sprint 8 — Post-Trade & Ledger Integration

**Focus**: D-09 Post-Trade, K-16/K-17 integration, D-02 completion

| Team  | Stories                                             | SP  |
| ----- | --------------------------------------------------- | --- |
| Beta  | STORY-D09-001: Trade confirmation generation        | 3   |
| Beta  | STORY-D09-002: Netting engine                       | 5   |
| Beta  | STORY-D09-003: Settlement lifecycle (T+n)           | 5   |
| Beta  | STORY-D02-003: VWAP algorithm                       | 5   |
| Beta  | STORY-D02-004: TWAP algorithm                       | 3   |
| Alpha | STORY-K17-002: Version vectors for causal ordering  | 5   |
| Alpha | STORY-K17-003: Saga state machine with compensation | 5   |
| Delta | STORY-D13-001: Daily automated recon workflow       | 5   |
| Delta | STORY-D13-002: Statement ingestion (multi-source)   | 5   |
| Gamma | STORY-D07-003: KYC/AML gateway                      | 5   |

**Sprint 8 Total**: ~51 SP

### Sprint 9 — Portfolio, Pricing & Surveillance

**Focus**: D-03 PMS, D-05 Pricing, D-08 Surveillance

| Team  | Stories                                           | SP  |
| ----- | ------------------------------------------------- | --- |
| Delta | STORY-D03-001: Portfolio construction             | 5   |
| Delta | STORY-D03-002: NAV calculation (TWR/MWR)          | 5   |
| Delta | STORY-D05-001: Real-time & EOD instrument pricing | 5   |
| Delta | STORY-D05-002: Yield curve bootstrapping          | 5   |
| Gamma | STORY-D08-001: Wash trade detection               | 5   |
| Gamma | STORY-D08-002: Spoofing/layering detection        | 5   |
| Gamma | STORY-D08-003: Case management & evidence         | 5   |
| Beta  | STORY-D06-003: VaR calculation (parametric)       | 5   |
| Beta  | STORY-D06-004: Margin call generation             | 5   |

**Sprint 9 Total**: ~50 SP

### Sprint 10 — Regulatory Reporting & Corporate Actions

**Focus**: D-10 Reporting, D-12 Corporate Actions, D-13 completion

| Team  | Stories                                                | SP  |
| ----- | ------------------------------------------------------ | --- |
| Delta | STORY-D10-001: Report definition & template management | 3   |
| Delta | STORY-D10-002: Multi-format rendering (PDF/CSV/XBRL)   | 5   |
| Delta | STORY-D12-001: Corporate action lifecycle engine       | 5   |
| Delta | STORY-D12-002: Entitlement calculation                 | 5   |
| Delta | STORY-D13-003: Matching engine                         | 5   |
| Delta | STORY-D13-004: Break detection & classification        | 3   |
| Gamma | STORY-D14-003: Match review workflow                   | 3   |
| Gamma | STORY-D14-004: Batch re-screening                      | 3   |
| Beta  | STORY-D09-004: CSD/depository adapter (T3)             | 5   |
| Beta  | STORY-D09-005: Ledger posting (K-16)                   | 3   |

**Sprint 10 Total**: ~45 SP

## Phase 3: Advanced Kernel & Integration (Sprints 11-16)

### Sprint 11 — Data Governance & AI

**Focus**: K-08 Data Governance, K-09 AI Governance, K-19 DLQ

| Team  | Stories                                    | SP  |
| ----- | ------------------------------------------ | --- |
| Alpha | STORY-K08-001: Data lineage graph          | 5   |
| Alpha | STORY-K08-002: Residency enforcement       | 3   |
| Alpha | STORY-K08-003: Encryption abstraction      | 3   |
| Alpha | STORY-K09-001: Model registry & versioning | 5   |
| Alpha | STORY-K09-002: Explainability (SHAP/LIME)  | 5   |
| Alpha | STORY-K19-001: DLQ monitoring dashboard    | 3   |
| Alpha | STORY-K19-002: Safe replay with dry-run    | 5   |
| Alpha | STORY-K19-003: Poison pill quarantine      | 3   |

**Sprint 11 Total (Alpha)**: ~32 SP

### Sprint 12 — Workflow Orchestration, Onboarding & Admin Portal

**Focus**: W-01 Workflow Orchestration, W-02 Client Onboarding, K-13 Admin Portal

| Team    | Stories                                                             | SP  |
| ------- | ------------------------------------------------------------------- | --- |
| Alpha   | STORY-W01-001: Implement workflow definition and storage            | 3   |
| Alpha   | STORY-W01-002: Implement workflow trigger mechanisms                | 3   |
| Alpha   | STORY-W01-003: Implement CEL (Common Expression Language) evaluator | 2   |
| Beta    | STORY-W02-001: Implement KYC workflow definition and trigger        | 2   |
| Epsilon | STORY-W02-002: Implement document upload portal                     | 3   |
| Beta    | STORY-W02-003: Implement document request and reminder engine       | 2   |
| Epsilon | STORY-K13-001: Implement admin portal shell and navigation          | 3   |
| Epsilon | STORY-K13-002: Implement K-02 configuration management UI           | 3   |

**Sprint 12 Total**: ~21 SP (Alpha: 8, Beta: 4, Epsilon: 9)

### Sprint 13 — Admin Portal & Regulator Portal

**Focus**: K-13 Admin Portal, R-01 Regulator Portal

| Team    | Stories                                                            | SP  |
| ------- | ------------------------------------------------------------------ | --- |
| Epsilon | STORY-K13-001: Implement admin portal shell and navigation         | 3   |
| Epsilon | STORY-K13-002: Implement K-02 configuration management UI          | 3   |
| Epsilon | STORY-K13-003: Implement system health overview dashboard          | 2   |
| Epsilon | STORY-R01-001: Implement regulator portal authentication           | 3   |
| Alpha   | STORY-R01-002: Implement regulator access request and provisioning | 2   |
| Epsilon | STORY-R01-003: Implement regulator data query interface            | 3   |
| Delta   | STORY-D12-003: Tax withholding                                     | 3   |
| Delta   | STORY-D12-004: Ledger posting (K-16)                               | 3   |
| Gamma   | STORY-D08-004: AI anomaly detection (K-09)                         | 5   |

**Sprint 13 Total**: ~27 SP

### Sprint 14 — Deployment & Platform Manifest

**Focus**: K-10 Deployment Abstraction, PU-004 Platform Manifest

| Team  | Stories                                                        | SP  |
| ----- | -------------------------------------------------------------- | --- |
| Zeta  | STORY-K10-001: Implement deployment orchestrator service       | 3   |
| Zeta  | STORY-K10-002: Implement canary deployment strategy            | 5   |
| Zeta  | STORY-K10-003: Implement deployment approval workflow          | 2   |
| Zeta  | STORY-K10-004: Implement environment registry and provisioning | 3   |
| Zeta  | STORY-PU004-001: Implement release manifest definition format  | 3   |
| Zeta  | STORY-PU004-002: Implement manifest signing and verification   | 2   |
| Alpha | STORY-K09-003: Implement model lifecycle management            | 3   |
| Alpha | STORY-K09-004: Implement SHAP explainability engine            | 5   |

**Sprint 14 Total**: ~26 SP

### Sprint 15 — M3C First Wave

**Focus**: R-02 Incident Response, O-01 Operator Console

| Team    | Stories                                                       | SP  |
| ------- | ------------------------------------------------------------- | --- |
| Zeta    | STORY-R02-001: Implement automated incident detection engine  | 3   |
| Zeta    | STORY-R02-002: Implement incident runbook integration         | 2   |
| Alpha   | STORY-R02-003: Implement incident communication templates     | 2   |
| Zeta    | STORY-R02-004: Implement status page integration              | 3   |
| Alpha   | STORY-O01-001: Implement multi-firm tenant registry           | 3   |
| Alpha   | STORY-O01-004: Implement license management and feature gates | 3   |
| Epsilon | STORY-O01-006: Implement operator platform health dashboard   | 3   |
| Alpha   | STORY-O01-007: Implement incident management for operators    | 3   |
| Delta   | STORY-D10-003: T3 regulatory portal submission                | 3   |
| Delta   | STORY-D10-004: ACK/NACK tracking                              | 3   |
| Delta   | STORY-D03-003: Drift detection & rebalancing                  | 5   |

**Sprint 15 Total**: ~33 SP

### Sprint 16 — M3C Completion

**Focus**: O-01, P-01, R-01, R-02, and PU-004 completion

| Team  | Stories                                                                       | SP  |
| ----- | ----------------------------------------------------------------------------- | --- |
| Alpha | STORY-O01-010: Implement cross-jurisdiction reporting aggregation             | 3   |
| Alpha | STORY-O01-011: Implement usage metering for billing                           | 3   |
| Alpha | STORY-O01-013: Implement natural language platform query engine               | 5   |
| Zeta  | STORY-R02-009: Implement automated incident playbook execution                | 3   |
| Zeta  | STORY-R02-011: Implement ML-powered incident pattern detection and clustering | 5   |
| Alpha | STORY-P01-009: Implement compliance verification checklist                    | 2   |
| Alpha | STORY-P01-011: Implement certificate revocation and emergency kill            | 2   |
| Zeta  | STORY-PU004-005: Implement platform upgrade orchestrator                      | 3   |

**Sprint 16 Total**: ~26 SP

## Phase 4: Integration & Hardening (Sprints 17-24)

### Sprint 17-18 — End-to-End Integration

**Focus**: Cross-service integration, full trading workflows

| Team    | Stories                                                 | SP/Sprint |
| ------- | ------------------------------------------------------- | --------- |
| Beta    | Order → Risk → Execution → Fill → Settlement full chain | 40/sprint |
| Gamma   | Compliance → Screening → Surveillance full chain        | 35/sprint |
| Delta   | Corporate Actions → Ledger → Reporting full chain       | 28/sprint |
| Alpha   | Event Bus → Saga → DLQ → Replay full chain              | 35/sprint |
| Epsilon | All portal integrations with backend APIs               | 28/sprint |

### Sprint 19-20 — Performance Hardening

**Focus**: Performance testing, optimization, SLA validation

| Target        | SLA                 | Stories                                     |
| ------------- | ------------------- | ------------------------------------------- |
| Order latency | < 1ms P99           | Optimize critical path: auth → risk → route |
| Market data   | < 100μs feed-to-app | Optimize WebSocket, kernel bypass           |
| Risk check    | < 5ms P99           | Redis hot cache, pre-computed limits        |
| Throughput    | 100K orders/sec     | Load test with Gatling/k6                   |
| Event bus     | 2ms publish P99     | Kafka partition tuning, batch optimization  |

### Sprint 21-22 — Security Hardening

**Focus**: Penetration testing, security audit, compliance validation

| Area           | Activities                                      |
| -------------- | ----------------------------------------------- |
| Auth           | JWT tampering, token enumeration, brute force   |
| API Gateway    | WAF bypass attempts, rate limit circumvention   |
| Data           | SQL injection, XSS, CSRF, encryption validation |
| Infrastructure | Network segmentation, mTLS enforcement          |
| Compliance     | GDPR, AML/KYC, SEBON/NRB regulatory validation  |
| Secrets        | Vault access audit, rotation verification       |

### Sprint 23-24 — Chaos Engineering & Resilience

**Focus**: T-02 Chaos Engineering scenarios

| Scenario                   | Target                                  |
| -------------------------- | --------------------------------------- |
| Kafka broker failure       | Event bus resilience, zero data loss    |
| PostgreSQL failover        | < 15s RTO, zero data loss               |
| Redis failure              | Graceful degradation to DB fallback     |
| Network partition          | Service mesh handling, circuit breakers |
| Pod kill during settlement | Saga compensation, DLQ recovery         |
| Full DR drill              | < 15 minute RTO                         |

## Phase 5: UAT & Production (Sprints 25-30)

### Sprint 25-28 — User Acceptance Testing

- Full trading workflow UAT with stakeholders
- Compliance workflow validation with regulators
- Performance validation under production-like load
- Multi-tenant isolation testing

### Sprint 29-30 — Production Readiness

- Production infrastructure provisioning
- Blue-green deployment validation
- Runbook testing & operator training
- Monitoring & alerting tuning
- Go-live preparation & checklist

## Phase 6: Follow-On Roadmap Beyond Current Baseline (Sprints 31-40)

This phase sits outside the current 30-sprint execution baseline and should be treated as a post-launch expansion roadmap.

### Sprint 31-35 — P2 Features

- K-12 Platform SDK (remaining languages: Go, Java, C#)
- P-01 Pack Marketplace
- O-01 Operator advanced workflows
- T-02 Advanced chaos scenarios
- AI features (K-09 A/B testing, advanced drift detection)

### Sprint 36-40 — Optimization & Expansion

- Multi-jurisdiction Content Pack development (SEBI/RBI)
- Advanced analytics and reporting
- Mobile app enhancements
- Plugin marketplace GA
- Performance optimization (sub-ms trading path)

---

# 9. DEPENDENCY GRAPH (Critical Paths)

```
Infrastructure (K8s, Kafka, PostgreSQL, Redis)
    ↓
K-07 Audit Framework ←→ K-05 Event Bus (co-dependent — bootstrap together)
    ↓
K-02 Configuration Engine ← K-15 Dual-Calendar
    ↓
K-14 Secrets Management → K-01 IAM
    ↓
K-03 Rules Engine ← K-04 Plugin Runtime
    ↓
K-18 Resilience ← K-17 DTC ← K-16 Ledger
    ↓
K-11 API Gateway ← K-06 Observability
    ↓
D-11 Reference Data → D-04 Market Data
    ↓
D-01 OMS → D-07 Compliance → D-14 Sanctions
    ↓
D-02 EMS → D-06 Risk Engine
    ↓
D-09 Post-Trade → D-13 Client Money Recon
    ↓
D-03 PMS → D-05 Pricing Engine
    ↓
D-08 Surveillance ← D-10 Regulatory Reporting ← D-12 Corporate Actions
    ↓
W-01 Workflow → W-02 Client Onboarding
    ↓
R-01 Regulator Portal → R-02 Incident Response & Escalation
    ↓
K-13 Admin Portal ← K-10 Deployment ← PU-004 Platform Manifest
    ↓
P-01 Pack Certification → K-12 Platform SDK
    ↓
T-01 Integration Testing → T-02 Chaos Engineering
```

### Critical Path (longest dependency chain):

```
Infrastructure → K-05/K-07 → K-02 → K-01 → K-03 → D-01 OMS → D-02 EMS → D-09 Post-Trade → D-13 Client Money → T-01 E2E
```

**Length**: ~18 sprints (36 weeks)

---

# 10. TRACEABILITY MATRIX

| Vision                 | Epic               | Feature                      | Representative Story | Component                        |
| ---------------------- | ------------------ | ---------------------------- | -------------------- | -------------------------------- |
| Unified Platform       | D-01 OMS           | D01-F01 Order Capture        | STORY-D01-001        | order-service, K-05, K-15        |
| Unified Platform       | D-02 EMS           | D02-F01 Smart Router         | STORY-D02-001        | execution-service, FIX gateway   |
| Regulatory Compliance  | D-07 Compliance    | D07-F01 Rule Pipeline        | STORY-D07-001        | compliance-service, K-03         |
| Regulatory Compliance  | D-14 Sanctions     | D14-F01 Screening            | STORY-D14-001        | sanctions-service, K-01          |
| Regulatory Compliance  | D-10 Reporting     | D10-F02 Rendering            | STORY-D10-002        | reporting-service, K-15          |
| Real-time Operations   | D-06 Risk          | D06-F01 Pre-Trade            | STORY-D06-001        | risk-service, Redis, K-03        |
| Real-time Operations   | D-04 Market Data   | D04-F01 Normalization        | STORY-D04-001        | market-data-service, TimescaleDB |
| AI-Driven Intelligence | K-09 AI Gov        | K09-F01 Model Registry       | STORY-K09-001        | ai-governance-service            |
| Scalability            | K-05 Event Bus     | K05-F01 Event Store          | STORY-K05-001        | event-store, Kafka, PostgreSQL   |
| Dual-Calendar          | K-15 Calendar      | K15-F01 Conversion           | STORY-K15-001        | calendar-service                 |
| Zero Trust Security    | K-01 IAM           | K01-F01 OAuth                | STORY-K01-001        | identity-service, K-14           |
| Zero Trust Security    | K-14 Secrets       | K14-F01 Vault                | STORY-K14-001        | secrets-service, HSM             |
| Plugin Extensibility   | K-04 Plugin        | K04-F02 Tier Isolation       | STORY-K04-002        | plugin-runtime, sandbox          |
| Plugin Extensibility   | P-01 Certification | P01-F01 Testing              | STORY-P01-001        | pack-testing-framework           |
| Content Packs          | K-03 Rules         | K03-F04 Jurisdiction Routing | STORY-K03-004        | rules-engine, K-02               |
| Financial Integrity    | K-16 Ledger        | K16-F01 Double-Entry         | STORY-K16-001        | ledger-service, PostgreSQL       |
| Financial Integrity    | D-13 Recon         | D13-F01 Daily Recon          | STORY-D13-001        | recon-service, K-16              |
| Operational Excellence | K-06 Observability | K06-F01 SDK                  | STORY-K06-001        | observability-sdk                |
| Operational Excellence | K-18 Resilience    | K18-F01 Circuit Breaker      | STORY-K18-001        | resilience-lib                   |

---

# 11. TEST COVERAGE MATRIX

| Epic               | Unit | Integration | E2E | Performance     | Security        | Chaos            |
| ------------------ | ---- | ----------- | --- | --------------- | --------------- | ---------------- |
| K-01 IAM           | ✅   | ✅          | ✅  | ✅ 5K auth/s    | ✅ Pentest      | ✅               |
| K-02 Config        | ✅   | ✅          | ✅  | ✅ Hot-reload   | ✅              | -                |
| K-03 Rules         | ✅   | ✅          | ✅  | ✅ <10ms P99    | ✅ Sandbox      | ✅               |
| K-04 Plugin        | ✅   | ✅          | ✅  | ✅ T3 isolation | ✅ Exfiltration | ✅               |
| K-05 Event Bus     | ✅   | ✅          | ✅  | ✅ 50K/s        | ✅              | ✅ Broker fail   |
| K-06 Observability | ✅   | ✅          | ✅  | ✅              | ✅ PII mask     | -                |
| K-07 Audit         | ✅   | ✅          | ✅  | ✅              | ✅ Tamper-proof | -                |
| K-11 API Gateway   | ✅   | ✅          | ✅  | ✅ Rate limit   | ✅ WAF          | ✅               |
| K-14 Secrets       | ✅   | ✅          | ✅  | ✅              | ✅ Rotation     | ✅               |
| K-15 Calendar      | ✅   | ✅          | ✅  | ✅ Edge cases   | -               | -                |
| K-16 Ledger        | ✅   | ✅          | ✅  | ✅ 20K/s        | ✅ Immutability | ✅               |
| K-17 DTC           | ✅   | ✅          | ✅  | ✅ Saga perf    | ✅              | ✅ Mid-saga fail |
| K-18 Resilience    | ✅   | ✅          | ✅  | ✅              | -               | ✅ All patterns  |
| D-01 OMS           | ✅   | ✅          | ✅  | ✅ 100K ord/s   | ✅              | ✅               |
| D-02 EMS           | ✅   | ✅          | ✅  | ✅ FIX perf     | ✅              | ✅ Exchange down |
| D-04 Market Data   | ✅   | ✅          | ✅  | ✅ <100μs       | ✅              | ✅ Feed failure  |
| D-06 Risk          | ✅   | ✅          | ✅  | ✅ <5ms P99     | ✅ Bypass test  | ✅               |
| D-07 Compliance    | ✅   | ✅          | ✅  | ✅              | ✅ Lock-in      | -                |
| D-09 Post-Trade    | ✅   | ✅          | ✅  | ✅ Netting      | ✅              | ✅ CSD failure   |
| D-13 Recon         | ✅   | ✅          | ✅  | ✅              | ✅ Segregation  | ✅               |
| D-14 Sanctions     | ✅   | ✅          | ✅  | ✅ <50ms P99    | ✅              | ✅               |

**Coverage Target**: 80% unit, 70% integration, 100% critical-path E2E, all critical-path perf/sec/chaos

---

# 12. RISK REGISTER

| #   | Risk                                                       | Probability | Impact   | Mitigation                                                     |
| --- | ---------------------------------------------------------- | ----------- | -------- | -------------------------------------------------------------- |
| R1  | K-05 Event Bus delay blocks all domain work                | Medium      | Critical | Sprint 1-2 highest priority; parallel team for Kafka infra     |
| R2  | FIX exchange adapter complexity (NEPSE proprietary)        | High        | High     | Spike in Sprint 4; engage exchange vendor early                |
| R3  | Dual-calendar edge cases (BS leap year)                    | Medium      | Medium   | Comprehensive lookup table + 500-case test suite               |
| R4  | Performance SLA <1ms order latency                         | Medium      | High     | Benchmark in Sprint 7; optimize path monthly                   |
| R5  | Regulatory requirement changes (SEBON)                     | Medium      | High     | T1/T2 content packs — zero core changes needed                 |
| R6  | AI model reliability (surveillance false positives)        | Medium      | Medium   | HITL override (K-09); tune thresholds with production data     |
| R7  | Multi-tenant data isolation breach                         | Low         | Critical | PostgreSQL RLS; integration test per sprint; pentest           |
| R8  | Saga compensation bugs (financial loss)                    | Medium      | Critical | Extensive saga testing; dry-run replay; manual resolution path |
| R9  | Key personnel dependency                                   | Medium      | High     | Cross-training; pair programming; documented runbooks          |
| R10 | Scope creep from 42-epic backlog                           | High        | Medium   | Strict P0/P1/P2 prioritization; MVP = P0 only (Sprints 1-24)   |
| R11 | Integration complexity with external systems (CDSC, banks) | High        | High     | Mock adapters in Sprint 5; phased integration                  |
| R12 | Secrets rotation causing outage                            | Low         | High     | Zero-downtime rotation design; chaos test in Sprint 23         |
| R13 | DLQ buildup blocking event processing                      | Medium      | High     | K-19 auto-retry + poison pill quarantine; alerting             |
| R14 | PostgreSQL single-point-of-failure                         | Low         | Critical | Patroni HA cluster; automated failover; DR drill               |

---

# 13. QUALITY GATES

## Gate 1: Sprint Exit (Every Sprint)

- [ ] All committed stories meet DoD
- [ ] Unit test coverage ≥ 80%
- [ ] Zero P1/critical bugs open
- [ ] CI pipeline green
- [ ] Code review completed (2 approvals)

## Gate 2: Phase Exit (Every 4-6 Sprints)

- [ ] Integration tests passing (cross-service)
- [ ] Performance benchmarks met for completed services
- [ ] Security scan clean (no critical/high)
- [ ] Architecture review completed
- [ ] Documentation updated

## Gate 3: Pre-Production (Sprint 28)

- [ ] All P0 stories complete
- [ ] E2E test coverage: 100% critical paths
- [ ] Performance: Order <1ms, Risk <5ms, Market Data <100μs
- [ ] Security: Penetration test passed, zero critical findings
- [ ] Compliance: SEBON/NRB regulatory validation signed off
- [ ] Chaos testing: All critical scenarios passed
- [ ] DR drill: RTO <15 minutes validated
- [ ] Monitoring: All SLOs configured, dashboards operational
- [ ] Runbooks: Complete for all operational scenarios

## Gate 4: Production Launch (Sprint 30)

- [ ] UAT sign-off from business stakeholders
- [ ] Regulator approval (SEBON inspection-ready)
- [ ] Production infrastructure validated
- [ ] Blue-green deployment tested
- [ ] Rollback procedure validated
- [ ] On-call rotation established
- [ ] Client communication plan approved
- [ ] Data migration verified (if applicable)

---

# 14. DELIVERY TIMELINE

```
2026
├── Mar    Sprint 1-2    Foundation (Infra + Core Kernel)
├── Apr    Sprint 3-4    Kernel Completion (IAM, Ledger, Rules, Plugins)
├── May    Sprint 5-6    Domain Foundation (Ref Data, OMS, Compliance)
├── Jun    Sprint 7-8    Risk, EMS, Post-Trade, Ledger Integration
├── Jul    Sprint 9-10   Portfolio, Pricing, Surveillance, Reporting
├── Aug    Sprint 11-12  Data Governance, AI, Workflow Orchestration
├── Sep    Sprint 13-14  Admin Portal, Regulator Portal, Deployment
├── Oct    Sprint 15-16  Incident Mgmt, Pack Cert, SDK
├── Nov    Sprint 17-18  End-to-End Integration
├── Dec    Sprint 19-20  Performance Hardening

2027
├── Jan    Sprint 21-22  Security Hardening
├── Feb    Sprint 23-24  Chaos Engineering & Resilience
├── Mar    Sprint 25-28  UAT
├── Apr    Sprint 29-30  Production Readiness & Launch ← MVP GO-LIVE
├── May    Sprint 31-33  Follow-on P2 Features (SDK, Marketplace)
├── Jun    Sprint 34-36  Follow-on Multi-Jurisdiction Expansion
├── Jul    Sprint 37-40  Follow-on Optimization & Advanced Features
```

**MVP (P0) Go-Live**: April 2027 (Sprint 30)  
**Full Platform GA**: July 2027 (Sprint 40)

---

# APPENDIX A: DEFINITION OF DONE (DoD)

A story is complete when:

- [ ] Implementation finished & compiles
- [ ] Unit tests written (≥80% coverage)
- [ ] Integration tests passing
- [ ] E2E tests validated (if applicable)
- [ ] Security checks completed (SAST/DAST)
- [ ] Performance validated against NFR budget
- [ ] Documentation updated (API docs, runbook)
- [ ] Observability added (metrics, traces, logs)
- [ ] Logging structured (JSON) with correlation ID
- [ ] CI pipeline passing (all stages green)
- [ ] Code review completed (2 approvals min)
- [ ] Dual-calendar timestamps verified
- [ ] Audit trail integration verified (K-07)

---

# APPENDIX B: TEAM RACI

| Activity                                           | Alpha   | Beta    | Gamma   | Delta   | Epsilon | Zeta    |
| -------------------------------------------------- | ------- | ------- | ------- | ------- | ------- | ------- |
| Kernel Services (K-\*)                             | **R/A** | C       | C       | C       | I       | C       |
| Trading Services (D-01, D-02, D-06, D-09)          | C       | **R/A** | C       | I       | I       | I       |
| Data & Compliance (D-04, D-07, D-08, D-11, D-14)   | C       | C       | **R/A** | I       | I       | I       |
| Finance & Reporting (D-03, D-05, D-10, D-12, D-13) | C       | I       | I       | **R/A** | I       | I       |
| UI Portals (K-13, R-01, O-01)                      | I       | I       | I       | I       | **R/A** | I       |
| Infrastructure & Deployment                        | C       | I       | I       | I       | I       | **R/A** |
| Architecture Review                                | **R**   | C       | C       | C       | C       | C       |
| Security Hardening                                 | C       | C       | C       | C       | C       | **R/A** |

R = Responsible, A = Accountable, C = Consulted, I = Informed

---

**END OF DELIVERY PROGRAM PLAN**  
**Document Version**: 1.1.0  
**Author**: Program Architecture Team  
**Next Review**: Sprint 4 retrospective (adjust velocity, re-estimate)
