# Unified Implementation Plan — AppPlatform Multi-Domain Operating System

**Version**: 2.5.0  
**Date**: 2026-03-12 (Updated: 2026-01-19)  
**Status**: Living Document — Authority Level 2  
**Purpose**: Consolidated implementation strategy derived from and aligned to all source documents

---

## 1. Document Authority & Conflict Resolution

This plan is the **Authority Level 2** implementation baseline. For all questions, documents are interpreted in this order:

| Priority | Document                                                                                                                                      | Scope                                      |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------ |
| 1        | **ADR-011** — Stack Standardization and Ghatana Platform Alignment                                                                            | Stack, runtime, framework choices          |
| 2        | **This document** — Unified Implementation Plan                                                                                               | Sprint plan, story counts, milestone gates |
| 3        | **Accepted Finance ADRs** (ADR-001 through ADR-010)                                                                                           | Architectural binding decisions            |
| 4        | **Normative specifications** (DOMAIN_PACK_INTERFACE_SPECIFICATION v2.0.0, INTER_DOMAIN_PACK_COMMUNICATION_SPEC, PLUGIN_SANDBOX_SPECIFICATION) | Interface contracts                        |
| 5        | **Epic files** (EPIC-K-\*, EPIC-D-\*, EPIC-W-\*, etc.)                                                                                        | Story-level functional requirements        |
| 6        | **LLD files** (lld/\*.md)                                                                                                                     | Component-level design                     |
| 7        | **Older C4 / research documents**                                                                                                             | Historical context only                    |

**Conflict rule**: Higher-priority documents win. If this plan conflicts with ADR-011, ADR-011 wins. If an epic file conflicts with this plan, this plan wins.

---

## 2. Executive Summary

This unified implementation plan consolidates all documentation into a single, consistent execution strategy for building the AppPlatform as a multi-domain operating system. Crucially, this plan adopts a **Platform-First side-by-side execution model**: the platform kernel serves as the stable baseline, while the Capital Markets domain pack is constructed simultaneously as a reference implementation. This ensures all kernel services (Event Routing, Config, Audit, IAM) are instantly validated by real-world domain requirements. The plan maintains a balanced 8-month timeline while retiring all duplicate schedules.

**Verified scope**: 310 kernel stories / 807 story points across 19 kernel modules (K-01–K-19); 229 Capital Markets domain stories (D-01–D-14) as the reference domain implementation; 8 cross-cutting epics (W-01, W-02, P-01, T-01, T-02, O-01, R-01, R-02, PU-004).

---

## 3. Architecture Overview

### 3.1 Multi-Domain Platform Architecture

See [Finance-Ghatana Integration Plan](../finance-ghatana-integration-plan.md) for detailed platform component reuse strategy.

```
Layer 2: Portals & Workflows
├── Admin Portal (K-13)
├── Regulator Portal (R-01)
├── Operator Console (O-01)
└── Client Onboarding (W-02)

Layer 1: Domain Packs (Pluggable)
├── Capital Markets (Siddhanta) - D-01 through D-14   [Reference Implementation]
├── Banking (Template)
├── Healthcare (Template)
└── Insurance (Template)

Layer 0: Platform Kernel (K-01 through K-19)
├── Identity & Access (K-01, K-14)
├── Configuration & Rules (K-02, K-03)
├── Plugin & Event System (K-04, K-05, K-17)
├── Observability & Audit (K-06, K-07, K-18, K-19)
├── Data & AI Governance (K-08, K-09, K-16)
└── Platform Services (K-10, K-11, K-12, K-13, K-15)
```

### 3.2 Technology Stack (ADR-011 — Authority Level 1)

| Scope                                                   | Standard                                                                                                                           |
| ------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| Architecture                                            | 7-layer microservices, DDD boundaries, event-driven, CQRS, event sourcing, dual-calendar native, multi-tenant, T1/T2/T3 pack model |
| Kernel, domain, event, workflow, data platform services | **Java 21 + ActiveJ + Gradle**                                                                                                     |
| User-facing API and control-plane CRUD services         | **Node.js LTS + TypeScript + Fastify + Prisma**                                                                                    |
| AI/ML execution only                                    | **Python 3.11 + FastAPI**                                                                                                          |
| External API contract                                   | REST + OpenAPI                                                                                                                     |
| Internal service contract                               | **gRPC + protobuf**                                                                                                                |
| Real-time transport                                     | WebSocket where required                                                                                                           |
| Frontend                                                | React 18 + TypeScript + Tailwind CSS + Jotai + TanStack Query + React Router                                                       |
| Primary database                                        | PostgreSQL 15+                                                                                                                     |
| Time-series                                             | TimescaleDB                                                                                                                        |
| Cache and ephemeral state                               | Redis 7+                                                                                                                           |
| Search and log indexing                                 | OpenSearch                                                                                                                         |
| Object storage                                          | Ceph / S3-compatible                                                                                                               |
| Vector search                                           | **pgvector on PostgreSQL**                                                                                                         |
| Event backbone                                          | Kafka 3+                                                                                                                           |
| Ingress and gateway                                     | Envoy/Istio ingress + K-11 gateway control plane                                                                                   |
| Container orchestration                                 | Docker + Kubernetes + Istio                                                                                                        |
| Delivery                                                | Terraform + Helm + ArgoCD + Gitea                                                                                                  |
| Observability                                           | Micrometer + OpenTelemetry + Prometheus + Grafana + Jaeger + OpenSearch                                                            |
| Monorepo tooling                                        | Gradle + pnpm                                                                                                                      |
| Testing                                                 | JUnit 5 + Testcontainers (Java); Jest + React Testing Library + Playwright (web)                                                   |
| Deployment topologies                                   | **5 topologies: SaaS, Dedicated, On-Prem, Hybrid, Air-Gap**                                                                        |

**Backend partitioning rule (ADR-011 §3)**: Java 21 + ActiveJ is the default for kernel, domain, event processing, workflow, audit, governance, and latency-sensitive financial paths. Node.js + Fastify is limited to user API surfaces, admin/control-plane CRUD, and portal-facing orchestration. Python exists only for model training and inference that requires Python ML libraries. Go is deferred.

**Ghatana platform reuse (ADR-011 §4)** — AppPlatform MUST use these instead of re-inventing. Gradle artifact paths are the canonical dependency reference:

| Concern                                                   | Ghatana Module                         | Gradle Artifact                     |
| --------------------------------------------------------- | -------------------------------------- | ----------------------------------- |
| Event processing & orchestration                          | `products/aep` — Ghatana AEP           | `:products:aep:platform`            |
| Workflow runtime                                          | `platform/java/workflow`               | `:platform:java:workflow`           |
| Data governance + EventLogStore persistence               | `products/data-cloud` platform         | `:products:data-cloud:platform`     |
| EventLogStore SPI (minimal interface)                     | `products/data-cloud` SPI              | `:products:data-cloud:spi`          |
| AI integration (gateway, feature-store, eval, training)   | `platform/java/ai-integration`         | `:platform:java:ai-integration`     |
| Agent framework (TypedAgent, LangChain4j, YAML config)    | `platform/java/agent-framework`        | `:platform:java:agent-framework`    |
| Agent dispatch (Tier-J/S/L routing)                       | `platform/java/agent-dispatch`         | `:platform:java:agent-dispatch`     |
| Agent memory (episodic, semantic, procedural)             | `platform/java/agent-memory`           | `:platform:java:agent-memory`       |
| Agent learning (eval gates, consolidation, skills)        | `platform/java/agent-learning`         | `:platform:java:agent-learning`     |
| Agent registry (discovery + Data Cloud persistence)       | `platform/java/agent-registry`         | `:platform:java:agent-registry`     |
| Resilience (circuit breaker, retry, bulkhead)             | `platform/java/agent-resilience`       | `:platform:java:agent-resilience`   |
| HTTP server/client + middleware                           | `platform/java/http`                   | `:platform:java:http`               |
| Security (JWT, OAuth2, RBAC, encryption)                  | `platform/java/security`               | `:platform:java:security`           |
| Multi-tenancy (tenant extraction/interceptors)            | `platform/java/governance`             | `:platform:java:governance`         |
| Configuration (HOCON/YAML, schema validation)             | `platform/java/config`                 | `:platform:java:config`             |
| Database (HikariCP, JPA/Hibernate, Flyway, Redis)         | `platform/java/database`               | `:platform:java:database`           |
| Observability (Micrometer, Prometheus, OpenTelemetry)     | `platform/java/observability`          | `:platform:java:observability`      |
| Observability HTTP handlers                               | `platform/java/observability-http`     | `:platform:java:observability-http` |
| Audit SDK (abstract AuditEvent, event-cloud-backed)       | `platform/java/audit`                  | `:platform:java:audit`              |
| Plugin loading framework                                  | `platform/java/plugin`                 | `:platform:java:plugin`             |
| Event streaming abstractions (EventRecord, EventLogStore) | `platform/java/event-cloud`            | `:platform:java:event-cloud`        |
| Event schema storage + compatibility enforcement          | `platform/java/schema-registry`        | `:platform:java:schema-registry`    |
| Kafka + PostgreSQL outbox connectors                      | `platform/java/connectors`             | `:platform:java:connectors`         |
| Data ingestion pipelines                                  | `platform/java/ingestion`              | `:platform:java:ingestion`          |
| Core runtime lifecycle                                    | `platform/java/runtime`                | `:platform:java:runtime`            |
| Core types and base abstractions                          | `platform/java/core`                   | `:platform:java:core`               |
| AI model registry microservice                            | `shared-services/ai-registry`          | deployed service                    |
| AI inference gateway microservice                         | `shared-services/ai-inference-service` | deployed service                    |
| Feature store ingestion microservice                      | `shared-services/feature-store-ingest` | deployed service                    |
| OAuth2/OIDC authentication microservice                   | `shared-services/auth-service`         | deployed service                    |
| JWT-validation API gateway                                | `shared-services/auth-gateway`         | deployed service                    |

> **AEP transitive scope**: `:products:aep:platform` transitively includes `workflow`, all `agent-*` libs, `connectors`, `plugin`, `observability`, `audit`, `security`, `database`, `http`, and `config`. Modules depending on AEP get all of these transitively.

### 3.3 Eight Governing Design Principles

1. **Zero Hardcoding** — All domain-specific rules externalized to versioned Domain Packs and Content Packs (T1/T2/T3). No business logic compiled into kernel binaries.
2. **Event-Sourced** — All state changes are immutable append-only events in K-05 Event Store. State is always re-derivable from the event log.
3. **Multi-Calendar at Data Layer** — Pluggable `CalendarDate` type (open map of `CalendarId → CalendarDateTime`) in every domain-significant timestamp, enriched by K-15. BS↔Gregorian is the first implementation via a T1 Config Pack.
4. **No Kernel Duplication** — Domain packs access kernel exclusively via the K-12 Platform SDK. No domain logic may call kernel internals directly.
5. **Single Pane of Glass** — Centralized admin, configuration, and observability via K-13 and K-06. No per-domain siloed dashboards.
6. **Domain Pack Isolation** — Domain packs are pluggable and isolated from kernel logic. Packs communicate only via K-05 Event Bus; no direct pack-to-pack RPC.
7. **Multi-Domain Support** — Kernel supports multiple domain packs simultaneously without modification or redeployment.
8. **AI as Substrate (Principle 17)** — AI/ML enrichment is native and implicit to every platform operation, not an add-on. **K-09 is the mandatory intermediary for ALL AI/ML inference.** No module, domain pack, or plugin may call a model endpoint directly. This ensures: (a) tenant-aware inference routing with jurisdiction compliance; (b) model governance (registration, bias assessment, drift monitoring) applies uniformly; (c) `AiAnnotation` fields in K-05 event envelopes make AI outputs traceable and auditable at the event sourcing layer; (d) HITL feedback loops are consistently captured and channeled to fine-tuning pipelines.

### 3.4 Kernel Module → Ghatana Library Reuse Matrix (Implementation-Binding)

> **Mandatory**: Each K-module MUST depend only on the listed Gradle artifacts. Re-implementing capabilities present in these libraries is a violation of ADR-011 §4. Extensions required beyond existing capabilities are explicitly listed in the final column.

| K-Module                    | Primary Gradle Artifacts                                                                                                                                                                                                                                                                                                                                                                                                                                             | Reuse                | Extensions (Net New)                                                                                                                                                                 |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **K-01** IAM                | `:platform:java:security` (JWT/OAuth2/RBAC, Nimbus JOSE+JWT, BouncyCastle)<br>`:platform:java:governance` (TenantExtractionFilter, TenantGrpcInterceptor)<br>`shared-services/auth-service` (OAuth2/OIDC microservice — deployed)<br>`shared-services/auth-gateway` (JWT validation proxy — deployed)                                                                                                                                                                | 85%                  | Beneficial ownership graph (GAP-002); TOTP MFA extension; break-glass workflow; T3 national-ID plugin interface; refresh-token family tracking                                       |
| **K-02** Config Engine      | `:platform:java:config` (HOCON/YAML, networknt schema validation)<br>`:platform:java:database` (PostgreSQL `LISTEN/NOTIFY` hot-reload)<br>`:platform:java:governance` (tenant-scoped config isolation)                                                                                                                                                                                                                                                               | 70%                  | 5-level hierarchy merge (GLOBAL→JURISDICTION→TENANT→USER→SESSION); `CalendarDate` effective dates; air-gap bundle signing (Ed25519 via `:security`); maker-checker workflow          |
| **K-03** Rules Engine       | `:platform:java:plugin` (plugin/sandbox isolation for rule packs)<br>`:products:aep:platform` (Tier-J/S rule agent dispatch)<br>`:platform:java:agent-resilience` (circuit breaker on evaluation path)                                                                                                                                                                                                                                                               | 60%                  | OPA/Rego evaluation wrapper (gRPC); T2 V8/WASM sandbox (64MB / 5s); jurisdiction routing; dry-run evaluation mode                                                                    |
| **K-04** Plugin Runtime     | `:platform:java:plugin` (plugin loading, Ed25519 manifest verification, hot-swap, capability management, ai-integration + event-cloud enabled)                                                                                                                                                                                                                                                                                                                       | 80%                  | T1/T2/T3 tier enforcement; capability approval workflow; per-plugin resource quotas; data exfiltration prevention                                                                    |
| **K-05** Event Bus          | `:products:aep:platform` (**sole event backend**)<br>`:platform:java:connectors` (KafkaConnector exactly-once; PostgreSQL outbox SKIP LOCKED)<br>`:platform:java:schema-registry` (Avro/Protobuf schema compat enforcement)<br>`:products:data-cloud:spi` (EventLogStore append interface)                                                                                                                                                                           | 90%                  | Finance event envelope (`AiAnnotation`, `CalendarDate`); saga policy definitions; 100K TPS tuning; replay API; consumer backpressure control                                         |
| **K-06** Observability      | `:platform:java:observability` (Micrometer, Prometheus, OpenTelemetry, Jaeger)<br>`:platform:java:observability-http` (Prometheus /metrics + health endpoints)                                                                                                                                                                                                                                                                                                       | ~100%                | SLO/SLA error budget tracking; PII masking rules; ML-based anomaly detection (via `:platform:java:ai-integration`)                                                                   |
| **K-07** Audit Framework    | `:platform:java:audit` (AuditEvent abstractions; event-cloud-backed persistence)<br>`:products:data-cloud:platform` (durable EventLogStore write backend)                                                                                                                                                                                                                                                                                                            | 90%                  | SHA-256 hash chain (genesis=`"0"×64`); Merkle root anchoring every 1,000 events / 60 min; 7yr financial / 5yr operational retention policy enforcement                               |
| **K-08** Data Governance    | `:products:data-cloud:platform` (data catalog, lineage tracking, classification, quality rules, residency, right-to-erasure, PII masking)                                                                                                                                                                                                                                                                                                                            | 95%                  | Finance-specific classification taxonomy (MNRE, SEBON); automated regulatory breach response workflow                                                                                |
| **K-09** AI Governance      | `:platform:java:ai-integration` (gateway, registry, feature-store, evaluation, observability, training sub-modules)<br>`:platform:java:agent-framework` (HITL workflow patterns)<br>`:platform:java:agent-learning` (evaluation gates, consolidation)<br>`shared-services/ai-registry` (model registry — deployed)<br>`shared-services/ai-inference-service` (inference gateway — deployed)<br>`shared-services/feature-store-ingest` (feature ingestion — deployed) | 80%                  | SHAP/LIME explainability wrappers; feature + concept drift detector with 60s auto-rollback; bias monitoring per demographic slice; TIER_1/2/3 risk tiering; ONNX-only T3 enforcement |
| **K-10** Deployment         | `:platform:java:runtime` (ActiveJ service lifecycle management)<br>Kubernetes HPA (custom metrics from K-05 consumer lag via Prometheus)                                                                                                                                                                                                                                                                                                                             | 50%                  | Deployment orchestrator service; canary strategy (10-min gate); IaC drift scanner; environment registry; deployment audit (K-07)                                                     |
| **K-11** API Gateway        | `:platform:java:http` (ActiveJ HTTP server/client, OkHttp, middleware)<br>`shared-services/auth-gateway` (JWT validation, tenant rate limiting — deployed)<br>`:platform:java:governance` (tenant-aware routing)                                                                                                                                                                                                                                                     | 80%                  | Envoy/Istio ingress configuration; OWASP Top 10 WAF rules; jurisdiction-based routing; schema validation at gateway (`:platform:java:schema-registry`)                               |
| **K-12** Platform SDK       | All `:platform:java:*` modules + `:products:aep:platform` + `:products:data-cloud:spi` (aggregation only)                                                                                                                                                                                                                                                                                                                                                            | 100% composition     | OpenAPI + event schema code generation; PACT contract test harness; domain pack scaffold generator; developer portal                                                                 |
| **K-13** Admin Portal       | React 18 + Tailwind CSS + Jotai + TanStack Query (frontend)<br>Node.js + Fastify + Prisma (BFF API)                                                                                                                                                                                                                                                                                                                                                                  | 0% Java reuse        | Entire UI shell; micro-frontend composition; plugin registry UI; maker-checker task center; K-02 config management UI; K-06 observability hub                                        |
| **K-14** Secrets Management | `:platform:java:security` (BouncyCastle AES-256-GCM for local provider; Nimbus JOSE for JWT key management)<br>`:platform:java:database` (secret version history persistence)                                                                                                                                                                                                                                                                                        | 60%                  | HashiCorp Vault KV v2 adapter; PKCS#11 HSM integration; auto-rotation scheduler; break-glass forensic workflow (K-07 audit)                                                          |
| **K-15** Dual-Calendar      | `:platform:java:domain` (contribute `CalendarDate` type back here)<br>`:platform:java:database` (holiday calendar CRUD persistence)                                                                                                                                                                                                                                                                                                                                  | 0% (net contributor) | Entire JDN-based BS↔Gregorian math library; `CalendarDate` open-map type definition; T+n settlement date calculator; storage enrichment middleware                                   |
| **K-16** Ledger Framework   | `:platform:java:database` (JPA/Hibernate DECIMAL(28,12); Flyway migrations; HikariCP)<br>`:platform:java:audit` (hash chain integrity pattern)<br>`:platform:java:governance` (tenant-isolated accounts)                                                                                                                                                                                                                                                             | 40%                  | Double-entry accounting engine; Money value object; multi-currency journal posting; reconciliation engine; nightly balance verification; maker-checker for account creation          |
| **K-17** DTC                | `:platform:java:connectors` (KafkaConnector exactly-once; PostgreSQL SKIP LOCKED outbox relay)<br>`:products:aep:platform` (saga coordination engine)<br>`:platform:java:database` (idempotency store + outbox tables)                                                                                                                                                                                                                                               | 85%                  | Finance saga policies (Order→Position→Ledger→Settlement); version vector causal ordering; DTC saga monitoring REST API                                                               |
| **K-18** Resilience Library | `:platform:java:agent-resilience` (circuit breaker, retry with jitter, bulkhead, health monitoring)<br>`:platform:java:config` (K-02 profile hot-reload)                                                                                                                                                                                                                                                                                                             | 90%                  | STRICT/STANDARD/RELAXED named profiles; `X-Request-Deadline` cascading timeout header; TRADING_CRITICAL composite profile; runtime profile switching via K-02                        |
| **K-19** DLQ Management     | `:products:aep:platform` (dead-letter handling + replay infrastructure)<br>`:platform:java:ai-integration` (ML failure classifier sub-module)<br>`:platform:java:connectors` (Kafka DLQ topic management)                                                                                                                                                                                                                                                            | 90%                  | Finance-specific DLQ routing policy; payload inspector; bulk replay scheduler; DLQ SLA dashboard; poison-pill detection + discard/archive workflow                                   |

> **Architectural constraint**: `platform:java:event-cloud` is a **platform-internal abstraction only** and is never called directly by K-module or domain pack code. The mandatory access path is: `K-module code → K-05 Event Bus API → :products:aep:platform → :platform:java:event-cloud`. Domain packs access events only via the K-12 SDK.

---

## 4. Security Architecture (Zero-Trust, ADR-006)

The platform enforces a **Zero-Trust** security posture: "Never trust, always verify." Security is applied at 6 layers:

| Layer             | Controls                                                                                                                                                |
| ----------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Network**       | mTLS between all internal services via Istio service mesh; TLS 1.3 for all external traffic; network policies enforcing micro-segmentation              |
| **Identity**      | OAuth 2.0 + PKCE, TOTP MFA, refresh token family tracking, K-01 session isolation per tenant                                                            |
| **Authorization** | RBAC with Redis-cached permissions; ABAC via K-03 OPA integration; break-glass access with mandatory K-07 forensic audit                                |
| **Data**          | AES-256-GCM encryption at rest; Row-Level Security (RLS) in PostgreSQL per tenant; K-08 dynamic data masking for PII; K-14 HSM-backed key management    |
| **Application**   | K-11 WAF for OWASP Top 10; request schema validation at gateway; K-04 capability-based plugin sandboxing; K-09 prompt injection prevention              |
| **Audit**         | K-07 hash-chained immutable audit log; external Merkle root anchoring every 1,000 events or 60 minutes; 7-year financial / 5-year operational retention |

**Key constraints**:

- Secrets are NEVER logged (K-14 enforcement)
- PII is NEVER in plaintext in logs or traces (K-06 PII masking)
- All inter-service communication uses mTLS; no plaintext internal HTTP
- Air-gap bundles signed with Ed25519 HSM-backed keys

---

## 5. AI Governance Framework (ADR-005 + Principle 17)

### 5.1 K-09 as Mandatory AI Gateway

All AI inference, prediction, and model interaction in the platform is routed exclusively through K-09. There are no exceptions. Domain packs, kernel services, and plugins call the K-09 AI SDK; they never invoke model endpoints directly.

### 5.2 Three Agent Categories (ADR-005)

| Category       | Risk Level  | Use Cases                                                                        | Controls                                                                   |
| -------------- | ----------- | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| **Autonomous** | Low-risk    | Anomaly flagging, log clustering, auto-classification                            | Executed without human approval; full audit trail in K-07                  |
| **Advisory**   | Medium-risk | Fat-finger detection, risk alerts, pattern recommendations                       | Results presented to human operators; override captured in feedback loop   |
| **Supervised** | High-risk   | Regulatory report generation, order routing recommendations, sanctions decisions | Mandatory HITL approval gate; K-07 records operator decision and rationale |

### 5.3 Model Governance

- **Model Registry**: All models registered in K-09 before any invocation. Lifecycle: `DRAFT → VALIDATED → DEPLOYED → DEPRECATED`.
- **Risk Tiering**: TIER_1 (low impact), TIER_2 (medium impact), TIER_3 (high impact / regulatory). TIER_3 requires additional governance sign-off.
- **Explainability**: SHAP feature attribution + LIME local explanation MUST be provided for every prediction surfaced to users or included in regulatory reports.
- **Model Card**: Mandatory for every registered model (training data provenance, bias metrics, performance thresholds, intended use).
- **Drift Detection**: Feature drift and concept drift monitored continuously. Auto-rollback to previous stable model version within **60 seconds** of drift threshold breach. Fallback strategy: log and route to default rule-based fallback.
- **Bias Monitoring**: Fairness metrics computed per demographic slice on live predictions; alerts routed via K-06 if bias threshold exceeded.
- **HITL Feedback Loop**: All Supervised-category decisions and Advisory overrides captured in K-09's labeled feedback store, batched and routed to fine-tuning pipelines.
- **T3 AI Packs**: AI models bundled as T3 plugins must use ONNX format, stateless operation, and observe the 2 GB memory cap. No Python runtime in production T3 execution.
- **Prompt Injection Prevention**: All LLM prompt templates and external inputs sanitized before reaching inference gateway (K-09 ARB FR9).

---

## 6. Kernel Module Inventory (K-01 through K-19)

All kernel modules are **domain-agnostic**. Domain packs access them exclusively via the K-12 Platform SDK. The table below shows the per-module NFR envelope for implementation validation. For the full library-to-module dependency mapping, see §3.4.

| Module                          | Purpose                                                                                 | Primary Ghatana Library (Gradle Artifact)                                                                                                  | Key NFR                                                           | ARB Findings                                     |
| ------------------------------- | --------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------------------------------------- | ------------------------------------------------ |
| **K-01** IAM                    | OAuth 2.0, RBAC/ABAC, MFA, beneficial ownership graph                                   | `:platform:java:security` + `:platform:java:governance` + `shared-services/auth-service`                                                   | 5K TPS, P99 <10ms auth, 99.999% uptime                            | GAP-002 (beneficial ownership graph)             |
| **K-02** Config Engine          | 5-level hierarchy (GLOBAL→JURISDICTION→TENANT→USER→SESSION), maker-checker, hot-reload  | `:platform:java:config` + `:platform:java:database` (LISTEN/NOTIFY)                                                                        | <0.5ms cached reads (Redis)                                       |                                                  |
| **K-03** Rules Engine           | OPA/Rego policy evaluation, T2 sandbox (V8/WASM), jurisdiction routing                  | `:platform:java:plugin` + `:products:aep:platform`                                                                                         | P99 <10ms evaluation, propagation <5s                             | P0-02 (circuit breaker for K-03)                 |
| **K-04** Plugin Runtime         | Tier-aware (T1/T2/T3) sandbox isolation, hot-swap, Ed25519 sig verify                   | `:platform:java:plugin`                                                                                                                    | T2: 64MB/100ms/no-net                                             |                                                  |
| **K-05** Event Bus              | Append-only event store, Kafka outbox, saga orchestration, replay engine                | `:products:aep:platform` + `:platform:java:connectors` + `:platform:java:schema-registry`                                                  | **100K TPS**, P99 publish <2ms, replay 50K/sec, consumer lag <30s | FR7 (blockchain anchor), FR8 (100K event buffer) |
| **K-06** Observability          | Logging, tracing, metrics, SLO framework, PII detection, AIOps                          | `:platform:java:observability` + `:platform:java:observability-http`                                                                       | Full distributed tracing; PII never plaintext                     |                                                  |
| **K-07** Audit Framework        | Hash-chained append-only audit, Merkle root anchoring, retention enforcement            | `:platform:java:audit` + `:products:data-cloud:platform`                                                                                   | 10K events/sec, 10-year retention, P99 search <500ms              | FR7 (blockchain anchor), FR8 (100K buffer)       |
| **K-08** Data Governance        | Lineage, residency, classification, quality, right-to-erasure, PII masking              | `:products:data-cloud:platform`                                                                                                            | RLS per tenant; automated breach response                         | FR7 (RLS), FR8 (automated breach)                |
| **K-09** AI Governance          | Model registry, SHAP/LIME explainability, drift detection, bias monitoring, HITL        | `:platform:java:ai-integration` + `:platform:java:agent-learning` + `shared-services/ai-registry` + `shared-services/ai-inference-service` | Drift auto-rollback **within 60s**, 500ms P99 trading path        | FR8 (auto-rollback), FR9 (prompt injection)      |
| **K-10** Deployment Abstraction | Canary upgrades, IaC drift scan, autoscaling, environment registry                      | `:platform:java:runtime` + Kubernetes/Helm                                                                                                 | Zero-downtime upgrades, canary auto-rollback                      |                                                  |
| **K-11** API Gateway            | Envoy/Istio gateway, rate limiting, WAF, schema validation, jurisdiction routing        | `:platform:java:http` + `shared-services/auth-gateway`                                                                                     | **50K TPS**, P99 <2ms overhead, 99.999% uptime                    |                                                  |
| **K-12** Platform SDK           | Multi-language SDK, OpenAPI codegen, PACT contract testing, pack scaffold               | All `:platform:java:*` + `:products:aep:platform`                                                                                          | <1ms SDK overhead, 10K concurrent clients, 2-version compat       |                                                  |
| **K-13** Admin Portal           | React micro-frontend, tenant/role/config/plugin management, maker-checker task center   | React 18 + Fastify + Prisma (new Node.js stack)                                                                                            | Page load <1s, API <200ms, 99.99% uptime                          |                                                  |
| **K-14** Secrets Management     | Multi-provider vault (HashiCorp, local), auto-rotation, HSM key operations, break-glass | `:platform:java:security` (BouncyCastle + Nimbus JOSE)                                                                                     | P99 <10ms cached / <50ms vault, 10K TPS, 99.999% uptime           |                                                  |
| **K-15** Dual-Calendar          | BS↔Gregorian JDN conversion, `CalendarDate` type, holiday calendar, settlement dates    | NEW — contributes `CalendarDate` to `:platform:java:domain`                                                                                | P99 <2ms, 10K TPS, 0 mismatches per 1M conversions                | P2-18 (>500 edge case assertions)                |
| **K-16** Ledger Framework       | Immutable double-entry ledger, **DECIMAL(28,12)**, multi-currency, maker-checker        | `:platform:java:database` (JPA/Hibernate/Flyway) + `:platform:java:audit`                                                                  | 10K TPS posting, P99 <5ms commit, nightly balance verify          | P0-03 (nightly balance verification)             |
| **K-17** DTC                    | Outbox relay, exactly-once Kafka publish, causal ordering, saga coordination            | `:platform:java:connectors` + `:products:aep:platform`                                                                                     | Outbox lag <100ms P99, 50K sagas/sec, 10-year log                 | P0-01 (event ordering guarantees)                |
| **K-18** Resilience Library     | Circuit breakers (STRICT/STANDARD/RELAXED), bulkheads, retries, cascading timeouts      | `:platform:java:agent-resilience` + `:platform:java:config` (K-02 hot-reload)                                                              | SDK overhead <0.1ms, zero allocation in hot path                  | P0-02 (circuit breaker for K-03)                 |
| **K-19** DLQ Management         | Poison message handling, ML failure classification, replay, payload transformation      | `:products:aep:platform` + `:platform:java:ai-integration`                                                                                 | Transient auto-retry 3×; permanent → DLQ immediately              | P0-04 (DLQ management)                           |

### 6.1 Bootstrap Cycle Resolution (K-05 / K-07)

K-05 depends on K-07 for audit logging; K-07 depends on K-05 for event streaming. This circular dependency is resolved via **phased implementation**:

1. **Phase 1** (Sprint 1): K-07 bootstraps with a simplified synchronous write path (direct PostgreSQL insert only; no Kafka).
2. **Phase 2** (Sprint 2): K-05 starts with audit via direct K-07 synchronous API call.
3. **Phase 3** (Sprint 2 completion): Once K-05 outbox relay is operational, K-07 switches to full event-sourced write path through K-05 outbox. Both modules are now fully operational.

### 6.2 Maker-Checker Mandate

The following modules MUST implement maker-checker approval workflows for all state mutations that affect financial output, compliance posture, or access control:

- **K-02** — All configuration changes at GLOBAL / JURISDICTION / TENANT levels
- **K-03** — All rule policy changes before deployment
- **K-07** — Approval chain audit trail linkage
- **K-16** — Account creation and chart-of-accounts changes
- **K-01** — Role / permission assignment at TENANT level

### 6.3 Critical Data Constraints

| Constraint                | Scope                      | Value                                                          |
| ------------------------- | -------------------------- | -------------------------------------------------------------- |
| Ledger precision          | K-16 all financial amounts | `DECIMAL(28,12)`                                               |
| Rounding                  | K-16                       | HALF_EVEN (NPR 4dp, USD/EUR 2dp, crypto 8dp, securities 0dp)   |
| Audit retention           | K-07                       | 7 years (financial records), 5 years (operational)             |
| Transaction log retention | K-17                       | 10 years                                                       |
| Event store mutation      | K-05, K-07, K-16           | `REVOKE UPDATE/DELETE` on all append-only tables               |
| Idempotency TTL           | K-05, K-17                 | 7-day Redis TTL with PostgreSQL fallback                       |
| Hash chain genesis        | K-07                       | `"0" × 64` (64 zeroes)                                         |
| Merkle anchoring cadence  | K-07                       | Every 1,000 events OR every 60 minutes (whichever comes first) |

---

## 7. Cross-Cutting Epics

These epics cut across the kernel and domain layers. They do not belong to individual kernel modules; they depend on multiple completed modules before they can be started.

| Epic       | Name                                   | Layer          | Sprint Gate | Key Dependencies                               |
| ---------- | -------------------------------------- | -------------- | ----------- | ---------------------------------------------- |
| **W-01**   | Cross-Domain Workflow Orchestration    | Workflow       | After M1B   | K-01, K-02, K-05, K-07, K-13, K-15             |
| **W-02**   | Client Onboarding                      | Workflow       | After M2A   | K-01, K-02, K-07, K-16, D-01, D-06, D-07, W-01 |
| **P-01**   | Pack Certification & Marketplace       | Packs          | After M1B   | K-02, K-04, K-07, K-09, PU-004                 |
| **T-01**   | Integration Testing Framework          | Testing        | Continuous  | All K-\* and D-\* modules                      |
| **T-02**   | Chaos Engineering & Resilience Testing | Testing        | After M3A   | K-05, K-06, K-10, K-18, T-01                   |
| **O-01**   | Operator Console                       | Operations     | After M3B   | K-06, K-07, K-10, K-13, PU-004                 |
| **R-01**   | Regulator Portal                       | Regulatory     | After M2B   | K-01, K-05, K-07, K-16, D-10                   |
| **R-02**   | Incident Response & Escalation         | Regulatory     | After R-01  | K-01, K-05, K-06, K-07, K-08, R-01             |
| **PU-004** | Platform Manifest                      | Platform Unity | After M1A   | K-04, K-05, K-07, K-10                         |

### 7.1 W-01 — Cross-Domain Workflow Orchestration

W-01 delivers a **declarative workflow DSL** (YAML/JSON) for long-running multi-domain processes (days to weeks). It builds on K-05 saga primitives with a higher-level orchestration engine supporting:

- Human task integration (approvals, exceptions, data entry forms)
- Workflow versioning with in-flight migration
- SLA tracking and breach alerting
- Compensation and rollback logic
- Event-driven triggers from K-05
- Dual-calendar SLA deadlines (BS + Gregorian)
- Schema-driven human task forms resolved at runtime from K-02
- Value catalog binding for routing decisions (K-02 scoped overrides)

### 7.2 P-01 — Pack Certification & Marketplace

P-01 is the **governance framework** for all T1/T2/T3 packs. Key elements:

- Automated testing pipeline: unit, integration, security scan (CVE + static analysis), performance benchmark
- Certification workflow: Submit → Test → Review → Approve → Publish (maker-checker gated)
- Pack signing via **Ed25519 HSM-backed** signing key (ACA — AppPlatform Certification Authority)
- Certificate validity: **90 days**
- Coverage gate: **≥90%** test coverage before certification proceeds
- Automated gate SLA: **≤30 minutes**
- T3 NETWORK/FILESYSTEM capability packs: human security review SLA **≤3 business days**
- Pack Author Agreement (PAA) required before first submission
- Marketplace registry with semantic versioning, dependency resolution, rollback history

### 7.3 PU-004 — Platform Manifest

PU-004 maintains the **immutable system state snapshot** of the running platform — what packs are installed, at what version, with what configuration checksum. It provides:

- Manifest API (read-only for domain packs, write via K-10 deployment)
- Cryptographically signed manifest bundle for air-gap deployments
- Drift detection between expected and actual installed state
- Dependency graph query for impact analysis before upgrades

---

## 8. Domain Pack Architecture

### 8.1 Normative Domain Pack Interface (DOMAIN_PACK_INTERFACE_SPECIFICATION v2.0.0)

```typescript
interface DomainPackManifest {
  // Pack identification
  id: string; // Globally unique reverse-DNS format
  name: string;
  version: string; // SemVer
  description: string;

  // Domain classification — OPEN string type (NOT an enum; forward-compatible)
  domainTypes: string[]; // e.g. ["CAPITAL_MARKETS", "CUSTODY"]

  // Core capabilities provided — CLOSED kernel-defined enum
  capabilities: CoreDomainCapability[];

  // Extended domain-specific capabilities — free-form
  extendedCapabilities: string[];

  // Platform compatibility
  platformMinVersion: string;
  platformMaxVersion?: string;

  // Required kernel modules
  kernelConstraints: KernelModuleConstraint[];

  // Pack component taxonomy (T1/T2/T3)
  dataModels: DataModelReference[]; // schemas
  businessRules: BusinessRuleReference[]; // T2 Rule Packs
  workflows: WorkflowReference[];
  integrations: IntegrationReference[]; // T3 adapters
  aiModels: AiModelReference[]; // T3 ONNX only
  userInterface: UIReference[];
  configuration: ConfigurationReference[]; // T1 Config Packs

  // Governance
  author: AuthorInfo;
  certification: CertificationInfo; // ACA-issued, Ed25519-signed
  featureFlags: FeatureFlagDefinition[]; // pack-defined feature toggles
  crossPackDependencies: DomainDependency[]; // explicit cross-pack deps

  // Compliance traceability
  complianceMappings: ComplianceClaimReference[]; // LCA-*/ASR-* mappings

  // Lifecycle hooks
  lifecycleHooks: LifecycleHooks;
}

// CLOSED kernel-defined enum — do not extend in domain packs
enum CoreDomainCapability {
  WORKFLOW_ORCHESTRATION, // complex multi-step operational processes
  STATE_MACHINE_MANAGEMENT, // lifecycle state machines
  VALIDATION_ENGINE, // input/output validation
  DATA_INGESTION, // external data import pipelines
  DATA_NORMALIZATION, // canonical data format translation
  MASTER_DATA_MANAGEMENT, // golden record management
  CALCULATION_ENGINE, // pricing, risk, analytics calculations
  RULE_EVALUATION, // business rule application
  RISK_ASSESSMENT, // risk score computation
  EXTERNAL_ADAPTERS, // integration with external systems
  PROTOCOL_HANDLERS, // financial protocol adapters (FIX, SWIFT)
  API_INTEGRATION, // vendor/exchange REST/gRPC integration
  POLICY_ENFORCEMENT, // regulatory / compliance policy application
  COMPLIANCE_REPORTING, // regulatory report generation
  AUDIT_TRAIL_GENERATION, // domain-level audit event emission
  DASHBOARD_GENERATION, // operational reporting UIs
  FORM_GENERATION, // schema-driven data entry
  NOTIFICATION_MANAGEMENT, // alert / notification emission
}
```

> **Important**: `domainTypes` is an **open string type** — receiving kernel services MUST handle unknown values gracefully (forward-compatibility). `CoreDomainCapability` is a **closed enum** — all valid values are defined here and in the kernel.

### 8.2 Inter-Domain Communication Rules (INTER_DOMAIN_PACK_COMMUNICATION_SPEC)

**Guiding principles** (G-1 through G-6):

| Principle                     | Rule                                                                                                                    |
| ----------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| **G-1 Loose Coupling**        | Domain packs MUST communicate exclusively via **K-05 Event Bus** (async pub/sub). No direct pack-to-pack HTTP or gRPC.  |
| **G-2 Read-Only Kernel Data** | Packs MAY query read-only kernel data via **K-02 Query Gateway** (CQRS read side). No direct kernel DB access.          |
| **G-3 Event Attribution**     | Every cross-pack event MUST carry `source_domain_pack_id` and a correlation ID in the envelope.                         |
| **G-4 No Direct RPC**         | Pack-to-pack HTTP/gRPC calls are **FORBIDDEN**. Violations are flagged by the K-04 capability system.                   |
| **G-5 Idempotent Consumers**  | All cross-pack event consumers MUST be idempotent (K-05 idempotency store enforces dedup).                              |
| **G-6 Schema Evolution**      | Cross-pack event schemas MUST support backward-compatible evolution; breaking changes require a new event type version. |

### 8.3 Plugin Sandbox Tier Specification

| Tier   | Runtime                     | Memory          | CPU / Timeout | Network / FS          | AI Model Execution                       |
| ------ | --------------------------- | --------------- | ------------- | --------------------- | ---------------------------------------- |
| **T1** | Declarative JSON/YAML only  | 5 MB bundle max | N/A           | None                  | Not permitted                            |
| **T2** | OPA/Rego (+ V8/WASM opt-in) | 256 MB per eval | 5 s timeout   | None                  | Not permitted                            |
| **T3** | JVM Java 21; restricted SDK | 2 GB max        | Configurable  | Requires ACA approval | **ONNX only** — stateless, verified hash |

### 8.4 Pack Certification Pipeline (P-01 / ACA)

```
Pack Author
    │
    ▼
[Submit to Registry]
    │
    ▼
[Automated Gate ≤30 min]
├── Unit tests pass (pack-provided)
├── Integration tests against platform test harness pass
├── Security scan: CVE check + static analysis pass
├── Performance benchmark: latency + memory within tier limits
├── Test coverage: ≥90%
└── Manifest schema + semantic validation pass
    │
    ▼
[T3 NETWORK/FILESYSTEM Packs only — Human Security Review ≤3 business days]
    │
    ▼
[ACA Signs Certificate — Ed25519 HSM-backed, 90-day validity]
    │
    ▼
[Published to Marketplace Registry]
```

---

## 9. Deployment Topologies

The platform supports 4 deployment models (from DEPLOYMENT_MODELS.md) plus the Air-Gap variant (ADR-011):

| Model                                           | Description                                                                   | Tenant Isolation                    | Use Case                                                |
| ----------------------------------------------- | ----------------------------------------------------------------------------- | ----------------------------------- | ------------------------------------------------------- |
| **1 — Single-Domain Dedicated**                 | One domain pack per cluster; each tenant gets a dedicated deployment          | Strongest hardware isolation        | High-security / high-performance single-domain tenants  |
| **2 — Multi-Domain Shared Kernel**              | Multiple domain packs in one cluster; shared kernel infrastructure            | RLS + namespace-level pod isolation | SaaS tenants, shared multi-domain platform              |
| **3 — Federated Domain-Owned Kernel Instances** | Each domain pack owns a kernel instance; cross-domain via K-05 events only    | Domain-level kernel isolation       | Regulated industries requiring domain data sovereignty  |
| **4 — Hybrid Cloud / On-Prem**                  | Core kernel in cloud; domain packs in on-prem or edge environments            | Network-policy isolation            | Financial institutions with data residency requirements |
| **5 — Air-Gap**                                 | No outbound connectivity; Ed25519-signed offline bundles distributed via K-02 | Physical network isolation          | Government / defense / maximum-security environments    |

For all topologies:

- Row-Level Security (RLS) enforced at PostgreSQL layer per tenant
- mTLS enforced for all inter-pod communication via Istio
- Tenant namespacing applied at Kubernetes, Kafka (topic prefixes), and Redis (key prefixes) level
- PU-004 Platform Manifest tracks installed state per environment

---

## 10. Implementation Milestones

### 10.1 Story Point Summary (Verified)

| Milestone               | Sprints | Kernel Epics                                         | Stories  | SPs        |
| ----------------------- | ------- | ---------------------------------------------------- | -------- | ---------- |
| **M1A** Foundation      | 1–2     | K-02, K-05, K-07, K-15                               | 78       | 210        |
| **M1B** Core Services   | 3–4     | K-01, K-03, K-04, K-06, K-11, K-14, K-16, K-17, K-18 | 147      | 361        |
| **M2A/M2B** Domain      | 5–10    | D-01 through D-14 (side-by-side)                     | 229      | —          |
| **M3A** Advanced Kernel | 11–12   | K-08, K-09, K-10, K-19                               | 56       | 164        |
| **M3B** Platform Tools  | 13–14   | K-12, K-13                                           | 29       | 72         |
| **Total Kernel**        |         | 19 modules                                           | **310**  | **807**    |
| **Grand Total**         |         | All modules                                          | **539+** | **1,000+** |

Cross-cutting epics (W-01, W-02, P-01, T-01, T-02, O-01, R-01, R-02, PU-004) are scheduled across M2–M3 sprints as their kernel dependencies complete.

**TDD coverage**: 314+ kernel test cases defined in `tdd_spec_*.md` files. ALL async Java tests MUST extend `EventloopTestBase`.  
**LLD coverage**: 19/19 LLDs complete.  
**ARB findings**: 20/20 addressed.

### 10.2 Milestone 1A — Kernel Foundation

**March 9 – March 20, 2026 (Sprints 1–2, 78 stories / 210 SP)**

**Goal**: Bootstrap the four foundational kernel modules that every other module depends on.

**Modules**: K-05 (Event Bus), K-07 (Audit Framework), K-02 (Config Engine), K-15 (Dual-Calendar)

**Bootstrap Note**: K-07 starts with a synchronous-only write path to break the K-05/K-07 circular dependency. K-05 activates full audit integration at Sprint 2 completion.

**Gates before M1B**:

- [ ] K-05 Event Store accepting 100K TPS in load test
- [ ] K-07 hash chain verified tamper-evident
- [ ] K-02 hierarchy merge working for all 5 config levels
- [ ] K-15 conversion accuracy: 0 mismatches in 36,500-day test suite
- [ ] PU-004 Platform Manifest initial implementation
- [ ] T-01 Integration Test harness scaffolded

**Key Deliverables**:

- [x] Repository structure with `domain-packs/` directory
- [x] Local dev stack (Kubernetes, Docker Compose, Ghatana AEP, Data Cloud)
- [x] TDD/BDD testing framework setup (`EventloopTestBase`)
- [x] CI/CD pipeline with domain pack, T3 security sandbox, and LCA/ASR traceability validation
- [x] Shared contracts: event-envelope, dual-date, domain-pack-interface
- [x] Domain pack loader and registry service foundation
- [x] K-05 Event Bus (32 stories / 100 SP)
- [x] K-07 Audit Framework (16 stories / 38 SP)
- [x] K-02 Configuration Engine (17 stories / 42 SP)
- [x] K-15 Dual-Calendar Service (13 stories / 30 SP)

### 10.3 Milestone 1B — Core Services

**March 21 – April 18, 2026 (Sprints 3–4, 147 stories / 361 SP)**

**Goal**: Implement the remaining core kernel services. Capital Markets scaffolding begins in parallel.

**Modules**: K-01 (IAM), K-03 (Rules Engine), K-04 (Plugin Runtime), K-06 (Observability), K-11 (API Gateway), K-14 (Secrets Management), K-16 (Ledger Framework), K-17 (DTC), K-18 (Resilience Library)

**Gates before M2A**:

- [ ] K-01 OAuth 2.0 flows + RBAC working
- [ ] K-03 OPA policy evaluation P99 <10ms
- [ ] K-04 T1/T2/T3 sandbox isolation verified
- [ ] K-11 Gateway 50K TPS load test passing
- [ ] K-16 DECIMAL(28,12) enforced; double-entry balance verify passing
- [ ] K-17 Outbox relay lag <100ms P99
- [ ] mTLS enabled for all inter-service communication

**Key Deliverables**:

- [x] K-01 IAM (23 stories / 62 SP) including beneficial ownership graph
- [x] K-03 Rules Engine (14 stories / 30 SP) with OPA + T2 sandbox
- [x] K-04 Plugin Runtime (15 stories / 33 SP) with Ed25519 verification
- [x] K-06 Observability (22 stories / 57 SP) with PII masking
- [x] K-11 API Gateway (13 stories / 30 SP) with WAF
- [x] K-14 Secrets Management (14 stories / 35 SP) with HSM operations
- [x] K-16 Ledger Framework with DECIMAL(28,12) and maker-checker
- [x] K-17 DTC with outbox relay and saga coordination
- [x] K-18 Resilience Library (STRICT/STANDARD/RELAXED profiles)

### 10.4 Milestones 2A/2B — Capital Markets Reference Domain

**April 19 – July 11, 2026 (Sprints 5–10, 229 domain stories)**

**Goal**: Implement the complete Capital Markets domain pack (Siddhanta) as the living reference implementation concurrently with remaining kernel services. Every domain feature validates its corresponding kernel module.

**Domain Epics**: D-01 OMS → D-02 EMS → D-11 Reference Data → D-04 Market Data → D-05 Pricing Engine → D-03 PMS → D-06 Risk Engine → D-07 Compliance → D-08 Surveillance → D-09 Post-Trade → D-12 Corporate Actions → D-13 Client Money Recon → D-14 Sanctions Screening → D-10 Regulatory Reporting

**Reference Domain Pack side-by-side approach**:

- Define all D-series entities, events, and APIs in `domain-packs/capital-markets/`
- Tracer bullet: Issue Order (D-01) → route via K-05 → store in Event Log → validate K-07 immutable trail
- Validate K-03 Rules Engine with trading and risk rules (Rego)
- Validate K-09 AI Governance with fat-finger detection models (ONNX, via K-09 mandatory gateway)
- Validate K-16 Ledger with settlement entries; DECIMAL(28,12) precision checks
- Client Onboarding (W-02) and Regulator Portal (R-01) start in Sprint 9–10

### 10.5 Milestone 3A — Advanced Kernel

**July 12 – August 8, 2026 (Sprints 11–12, 56 stories / 164 SP)**

**Modules**: K-08 (Data Governance), K-09 (AI Governance), K-10 (Deployment Abstraction), K-19 (DLQ Management)

**Key Deliverables**:

- [x] K-08 Data Governance with full lineage, RLS, right-to-erasure
- [x] K-09 AI Governance (Principle 17) — model registry, SHAP/LIME, drift detection, HITL, T3 ONNX enforcement
- [x] K-10 Deployment Abstraction — canary upgrades, IaC drift scan, HPA with K-05 consumer lag metrics
- [x] K-19 DLQ Management — ML failure classifier, poison pill detection, replay dashboard
- [x] T-02 Chaos Engineering — fault injection, resilience testing against K-05/K-10/K-18

### 10.6 Milestone 3B — Platform Tools

**August 9 – September 5, 2026 (Sprints 13–14, 29 stories / 72 SP)**

**Modules**: K-12 (Platform SDK), K-13 (Admin Portal)

**Key Deliverables**:

- [x] K-12 Platform SDK — multi-language, OpenAPI + event schema codegen, PACT contract testing, pack scaffold, certification tests
- [x] K-13 Admin Portal — full micro-frontend with maker-checker task center, schema-driven forms, dynamic value catalogs

### 10.7 Phase 4 — Multi-Domain Expansion

**September 6 – October 15, 2026**

**Key Deliverables**:

- [x] P-01 Pack Certification & Marketplace — ACA signing, 90-day certs, automated gates
- [x] W-01 Cross-Domain Workflow Orchestration — declarative DSL, human tasks, SLA tracking
- [x] O-01 Operator Console
- [x] R-02 Incident Response & Escalation
- [x] Banking domain pack template
- [x] Healthcare domain pack template
- [x] Third-party developer onboarding
- [x] Integration and chaos testing
- [x] Operational hardening and regulatory compliance

---

## 11. Capital Markets Reference Domain Pack Structure

```
domain-packs/capital-markets/         # D-01 through D-14
├── pack.yaml                         # Normative DomainPackManifest
├── schemas/
│   ├── entities.json                 # Domain entity definitions
│   ├── events.json                   # Domain event schemas (Avro/Protobuf)
│   └── apis.json                     # OpenAPI/gRPC service definitions
├── rules/                            # T2 Rule Packs (OPA/Rego)
│   ├── trading-rules.rego
│   ├── risk-rules.rego
│   └── compliance-rules.rego
├── workflows/                        # W-01 workflow definitions (YAML)
│   ├── order-lifecycle.yaml
│   ├── settlement.yaml
│   └── client-onboarding.yaml
├── integrations/                     # T3 adapters (JVM, signed)
│   ├── exchange-adapter.jar
│   └── regulator-adapter.yaml
├── ai-models/                        # T3 AI — ONNX ONLY
│   └── fat-finger-detector.onnx
├── ui/                               # Domain UI components
│   └── compliance-ui.json
└── config/                           # T1 Config Packs
    ├── bs-calendar-config.json
    └── jurisdiction-overrides.json
```

### 11.1 Capital Markets Domain Module Summary

| Epic | Module               | Domain Function           | Key Integration                                                 |
| ---- | -------------------- | ------------------------- | --------------------------------------------------------------- |
| D-01 | OMS                  | Order Management          | K-03 (pre-trade rules), K-05 (order events), K-16 (margin)      |
| D-02 | EMS                  | Execution Management      | D-01, K-05 (fill events), K-11 (exchange APIs)                  |
| D-03 | PMS                  | Portfolio Management      | D-01, K-16 (P&L), K-15 (BS/Gregorian NAV)                       |
| D-04 | Market Data          | Real-time prices          | K-04 (data provider plugins), K-05 (price events)               |
| D-05 | Pricing Engine       | Instrument pricing        | D-04, K-03 (pricing rules)                                      |
| D-06 | Risk Engine          | Pre/post-trade risk       | D-01, D-03, K-03, K-16, K-18                                    |
| D-07 | Compliance           | Rule enforcement          | K-03 (OPA policies), K-09 (AI flags), K-07 (immutable trail)    |
| D-08 | Surveillance         | Market surveillance       | K-09 (ML anomaly detection), K-03 (alert rules)                 |
| D-09 | Post-Trade           | Settlement, DVP, STP      | D-01, D-02, K-16 (settlement entries), K-15 (T+n)               |
| D-10 | Regulatory Reporting | SEBON/SWIFT reports       | D-07, D-08, K-07 (audit), K-15 (BS dates), K-16 (balances)      |
| D-11 | Reference Data       | Instruments, issuers      | K-02 (config), K-04 (feed adapters), K-15 (corporate calendar)  |
| D-12 | Corporate Actions    | Dividends, splits, rights | D-11, K-16 (entries), K-15 (record/payment dates)               |
| D-13 | Client Money Recon   | Client fund segregation   | K-16 (reconciliation), K-07 (audit), R-02 (incident escalation) |
| D-14 | Sanctions Screening  | Real-time watchlist       | K-01 (identity), K-07 (match audit), D-07 (compliance policy)   |

---

## 12. Implementation Dependencies

### 12.1 Critical Path

1. **K-05 and K-07 first** — Every other module depends on event sourcing and audit. Bootstrap sequence (§6.1) resolves the circular dependency.
2. **K-02 and K-15 alongside K-05/K-07** — Configuration hierarchy and calendar support are needed before any domain pack can configure jurisdiction-specific behavior.
3. **K-04 Plugin Runtime before domain packs** — Domain packs are loaded by K-04; T1/T2/T3 sandbox must be operational first.
4. **K-12 Platform SDK is the last kernel module** — SDK wraps all other K-01–K-19; it can only be finalized once all modules have stable APIs.
5. **Domain Packs are side-by-side** — D-series epics begin at Sprint 5 (M2A) and proceed with kernel modules they depend on already stable.
6. **Cross-Cutting Epics are gated** — W-01, P-01, O-01, R-01/R-02 all require multiple kernel and domain modules to be live before they begin.

### 12.2 Key Dependency Graph (TopSort Order for Critical Path)

```
K-15 → K-05 → K-07 → K-02 → K-14 → K-01 → K-03 → K-04 → K-11
                                                              ↓
K-16 → K-17 → K-06 → K-18 → K-19 → K-08 → K-09 → K-10 → PU-004
                                                              ↓
D-11 → D-04 → D-01 → D-02 → D-09 → D-05 → D-06 → D-07 → D-08
                                ↓
                         D-12, D-13, D-14 → D-10 → R-01
                                                        ↓
                                    K-12 → K-13 → W-01 → W-02
                                                        ↓
                                              P-01 → O-01 → R-02
```

---

## 13. Risk Mitigation

### Technical Risks

| Risk                                      | Mitigation                                                                           | Status       |
| ----------------------------------------- | ------------------------------------------------------------------------------------ | ------------ |
| K-05/K-07 bootstrap cycle                 | Phased implementation documented in §6.1                                             | ✅ Resolved  |
| K-09 60s drift auto-rollback ambition     | Fallback to rule-based default defined; monitoring gate in place                     | ⚠️ Monitor   |
| K-01 beneficial ownership graph (GAP-002) | Separate tracking; MVP traversal API scoped for M1B                                  | ⚠️ In-scope  |
| K-17 STUCK saga manual resolution SLA     | SLA definition required before M1B completion                                        | ⚠️ Open      |
| T3 NETWORK/FILESYSTEM review bottleneck   | ACA review queue SLA ≤3 business days; pre-screen T3 packs                           | ✅ Governed  |
| Domain pack isolation enforcement         | K-04 capability system enforces at runtime; K-11 WAF blocks direct inter-domain HTTP | ✅ Mitigated |
| Multi-tenant RLS gaps                     | PostgreSQL RLS policies per tenant + automated integration test suite (T-01)         | ✅ Mitigated |
| Model drift in production (K-09)          | Drift monitor + auto-rollback + K-07 forensic audit trail                            | ✅ Mitigated |

### Business Risks

| Risk                         | Mitigation                                                           | Status       |
| ---------------------------- | -------------------------------------------------------------------- | ------------ |
| Ecosystem adoption           | Developer tools (K-12 SDK), templates, marketplace (P-01)            | ✅ Mitigated |
| Domain pack quality variance | P-01 Pack Certification mandatory gate before marketplace publishing | ✅ Mitigated |
| Regulatory compliance gaps   | LCA-\*/ASR-\* traceability in CI/CD pipeline; R-01 Regulator Portal  | ✅ Mitigated |

---

## 14. Success Metrics

### Technical Metrics

| Metric                      | Target                                      | Measured By                    |
| --------------------------- | ------------------------------------------- | ------------------------------ |
| K-05 Event Bus throughput   | 100K TPS sustained                          | Load test in CI gate           |
| K-11 API Gateway throughput | 50K TPS                                     | Load test in CI gate           |
| K-07 Audit ingestion        | 10K events/sec                              | Performance test               |
| K-16 Ledger precision       | DECIMAL(28,12), 0 rounding errors           | Nightly balance verification   |
| K-15 Calendar accuracy      | 0 mismatches per 1M conversions             | 36,500-day accuracy test suite |
| K-09 Drift rollback         | ≤60 seconds                                 | Chaos test (T-02)              |
| Audit retention             | 7yr financial, 5yr operational              | Automated enforcement + audit  |
| Code reuse                  | >80% kernel code shared across domain packs | Module dependency analysis     |
| Domain pack onboarding      | <2 weeks for a new domain pack              | P-01 pipeline duration         |
| Platform availability       | 99.999% uptime                              | SLO tracking (K-06)            |
| TDD coverage                | ≥90% per module                             | CI gate                        |
| All async tests             | 100% extend `EventloopTestBase`             | Static analysis CI rule        |

### Business Metrics

| Metric                  | Target                             | Horizon            |
| ----------------------- | ---------------------------------- | ------------------ |
| Market domain expansion | 3+ new domain packs certified      | 6 months post-M3B  |
| Ecosystem growth        | 10+ third-party domain packs       | 12 months post-M3B |
| Developer adoption      | 100+ active domain pack developers | 12 months post-M3B |

---

## 15. Quality Assurance & Definition of Done

### Per-Story Definition of Done

- [ ] Compiles and all tests pass
- [ ] JavaDoc + `@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern` tags on ALL public classes
- [ ] No duplicate code (checked against `libs/java/*`)
- [ ] Async Java tests extend `EventloopTestBase` (enforced by static analysis CI rule)
- [ ] Code formatted (`spotlessApply`)
- [ ] Maker-checker workflow implemented where mandated (§6.2)
- [ ] Secrets never logged (K-14 constraint)
- [ ] PII never plaintext in logs/traces (K-06 masking)
- [ ] LCA-\*/ASR-\* compliance claim mappings declared in manifest (where applicable)

### Platform-Level Quality Gates

- **TDD**: Implementation follows TDD generation specs (`tdd_spec_*.md`). 314+ kernel test cases defined.
- **Legal / Regulatory Traceability**: All domain capabilities mapped to `LCA-*` (Legal Claims) and `ASR-*` (Authoritative Sources).
- **Zero-Trust AI Sandboxing (T3)**: Automated CI pipeline rejects Python runtime deployments; enforces ONNX format, stateless operation, max 2 GB memory.
- **PACT Contract Testing**: K-12 SDK provides contract test harness; all inter-service contracts validated in CI.
- **ARB Findings**: All 20 ARB findings tracked; P0-priority findings (P0-01 Event Ordering, P0-02 Circuit Breaker, P0-03 Balance Verification, P0-04 DLQ) are M1A/M1B hard gates.

---

## 16. Next Steps

### Immediate Actions (Week 1)

1. Finalize and approve this updated unified implementation plan
2. Validate story inventory against EPIC files for any newly added stories
3. Confirm team onboarding to M1A sprint (K-05, K-07, K-02, K-15)
4. Set up static analysis CI rule: enforce `EventloopTestBase` for all async tests

### Short-term Actions (Weeks 2–4)

1. K-07 bootstrap synchronous write path — unblock K-05 integration
2. Finalize K-12 SDK pre-design to lock domain pack API surface before M2A starts
3. First TDD spec review against domain pack interface contract (DOMAIN_PACK_INTERFACE_SPECIFICATION v2.0.0)
4. P-01 certification pipeline design finalized (Ed25519 key provisioning, ACA charter)

### Medium-term Actions (Weeks 5–12)

1. Side-by-side kernel + Capital Markets implementation (M2A/M2B sprints)
2. Tracer bullet validation: Order → K-05 Event Bus → K-07 Audit → K-16 Ledger end-to-end
3. K-09 AI Governance integration: fat-finger detection model registered and validated via ONNX pipeline
4. W-01 Workflow Orchestration design complete, ready for Sprint 9

---

## Conclusion

This unified implementation plan provides a comprehensive, implementation-ready baseline for building AppPlatform as a multi-domain operating system. The plan is **derived from and aligned to** all normative source documents: ADR-011 (stack), ADR-001–010 (architecture), DOMAIN_PACK_INTERFACE_SPECIFICATION v2.0.0, INTER_DOMAIN_PACK_COMMUNICATION_SPEC, PLUGIN_SANDBOX_SPECIFICATION, MARKETPLACE_GOVERNANCE.md, DEPLOYMENT_MODELS.md, KERNEL_PLATFORM_REVIEW.md, and the full epic file set.

The platform is positioned for significant growth while maintaining technical excellence and regulatory compliance capabilities.

**Status**: ✅ READY FOR IMPLEMENTATION  
**Authority Level**: 2 (superseded only by ADR-011 and accepted finance ADRs)  
**Next Review**: April 18, 2026 (M1B completion gate)  
**Final Review**: October 15, 2026 (Phase 4 completion)
