# Current Execution Plan

## Project Siddhanta

**Date:** 2026-03-12  
**Status:** Current planning baseline (aligned with UNIFIED_IMPLEMENTATION_PLAN.md)  
**Supersedes:** `archive/reviews/2026-03/COMPREHENSIVE_REVIEW_AND_IMPLEMENTATION_PLAN.md`

**Note**: This document provides detailed milestone execution. For the complete unified implementation strategy including domain pack architecture, see [UNIFIED_IMPLEMENTATION_PLAN.md](../UNIFIED_IMPLEMENTATION_PLAN.md).

---

## 1. Current Repository State

The repository now separates:

- **Current baseline documents** in `adr/`, `architecture/`, `plans/`, `epics/`, `stories/`, `lld/`, and the current specification set in `docs/`
- **Historical review snapshots** in `archive/reviews/2026-03/`

This means the program is now in a cleaner state:

- Architecture baseline is current
- Epic baseline is current
- LLD baseline is current
- Historical review artifacts are preserved for traceability, not execution
- The repository is still **specification-first** and **implementation-light**; no application scaffold or runtime services exist yet

---

## 2. Planning Assumptions

These assumptions drive the execution sequence below:

1. The repo starts implementation on **Monday, March 9, 2026**.
2. The first implementation target is a **kernel-first platform bootstrap**, not a domain UI or exchange adapter.
3. The first business milestone is a **staging-grade trading control path**, not production launch.
4. Standardized implementation profile per [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md):
   - **Java 21 + ActiveJ** for kernel, domain, event, workflow, and data platform services
   - **Node.js LTS + TypeScript + Fastify + Prisma** for user-facing API and control-plane CRUD services
   - **Python 3.11 + FastAPI** only where AI/ML execution is required
   - Reuse Ghatana platform products for AI/ML, event processing, workflow, and data management
5. All jurisdiction behavior remains pack-driven:
   - T1 Config Packs
   - T2 Rules Packs
   - T3 Executable Packs
6. Runtime business-process definitions, human-task schemas, and operator-facing value catalogs must be metadata-driven and versioned; tenant-specific changes in step order, allowed values, or approval forms should not require core-service code changes unless the underlying contract changes.

---

## 3. Program Milestones

### Milestone A — Executable Platform Skeleton

Target date: **March 20, 2026**

Success means:

- Monorepo layout exists
- Local dev stack boots
- Delivery pipeline
  - Lint, test, contract validation, container build, security scan
- Shared contract packages exist for event envelope and dual-date

### Milestone B — Kernel Foundation in Staging

Target date: **May 15, 2026**

Success means:

- K-05 Event Bus core path works
- K-07 Audit Framework works with hash-chain verification
- K-02 Config resolution works with T1 packs
- K-15 Dual-Calendar conversion is available as service and library

### Milestone C — Kernel Completion

Target date: **July 10, 2026**

Success means:

- Security, plugin, resilience, observability, ledger, manifest, and control-plane services are running in staging
- Kernel gates for domain work are cleared

### Milestone D — Trading MVP Path

Target date: **September 18, 2026**

Success means the following flow works in staging:

`Reference Data -> Market Data -> OMS -> Risk -> Compliance -> EMS -> Post-Trade -> Ledger -> PMS`

### Milestone E — Operational and Regulatory Hardening

Target date: **November 13, 2026**

Success means:

- Reporting, sanctions, reconciliation, operator console workflows, regulator workflows, and certification paths exist
- Integration and chaos testing are active

---

## 4. Detailed Phase Plan

### Phase 0 — Execution Bootstrap (March 9–20, 2026)

### Objectives

1. **Repository Foundation**: Establish monorepo structure with domain pack support
2. **Shared Contracts**: Create domain-agnostic contracts and schemas
3. **Local Runtime**: Multi-domain development environment
4. **Delivery Pipeline**: Domain pack certification and deployment
5. **Engineering Standards**: Multi-domain development guidelines

### Key Changes for Multi-Domain Support

- **Domain Pack Structure**: Added support for pluggable domain packs
- **Generic Kernel**: Kernel modules remain domain-agnostic
- **Domain Registry**: Service for managing domain pack lifecycle
- **Multi-Domain Testing**: Test framework for domain pack validation
- **Domain Marketplace**: Platform for distributing certified domain packs

**Objective:** Convert the repository from document-only to implementation-ready workspace.

**Workstreams**

- Monorepo structure
  - Create `services/`, `packages/`, `apps/`, `infra/`, `schemas/`, `domain-packs/`, `tools/`
  - Establish service template for kernel services
  - Create domain pack template for multi-domain development
- Shared contracts
  - Publish initial `event-envelope` package from K-05 envelope contract
  - Publish initial `dual-date` package from K-15 contract
  - Publish common protobuf/OpenAPI/schema workspace
  - Create domain pack interface contracts
- Local platform runtime
  - Docker Compose or lightweight Kubernetes dev profile
  - PostgreSQL, Redis, Kafka, object storage, OpenTelemetry collector
  - Domain pack loading and testing environment
- Delivery: Terraform + Helm + ArgoCD + Gitea
  - Domain pack certification pipeline
  - Multi-domain deployment support
- Engineering standards
  - Service naming conventions
  - API versioning standards
  - Event schema versioning
  - Migration policy
  - Branch and release conventions
  - Domain pack development guidelines
  - Multi-domain testing standards
  - apply [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md) as stack authority

**Phase 0 Deliverables**

- `services/` scaffold with at least one kernel service template
- `packages/contracts/event-envelope`
- `packages/contracts/dual-date`
- `packages/contracts/domain-pack-interface`
- `domain-packs/capital-markets/` template (refactored from D-01 through D-14)
- `domain-packs/templates/banking/` domain pack template
- `domain-packs/templates/healthcare/` domain pack template
- `infra/dev/` local runtime stack with domain pack support
- CI workflow for validation and packaging
- Domain pack certification pipeline
- Domain registry service scaffold
- ADR set for repo layout, service template, and interface definition format

**Exit Criteria**

- Every new service can be created from template in under 30 minutes
- Developers can run the local stack with one documented command
- Envelope and dual-date contracts are versioned and importable
- CI blocks schema-breaking changes

---

### Phase 1 — Kernel Foundation

**Dates:** March 23, 2026 to May 15, 2026

**Objective:** Build the non-negotiable kernel primitives that every domain module depends on.

**In Scope**

- K-05 Event Bus
- K-07 Audit Framework
- K-02 Configuration Engine
- K-15 Dual-Calendar Service

**Recommended Build Order**

1. K-05 core event publication/consumption path
2. K-15 conversion library and service
3. K-07 immutable audit append + verification path
4. K-02 schema registry, resolution, and T1 pack activation

**Practical Bootstrap Note**

K-05 and K-07 reference each other conceptually. Implementation should break that cycle as follows:

1. Ship K-05 core bus/event store without audit callback enforcement
2. Ship K-07 audit service and verification
3. Add K-05 -> K-07 publication hooks once both foundations exist

**Detailed Outputs**

- K-05
  - canonical event envelope
  - producer SDK
  - consumer contract
  - schema registry
  - replay API
- K-15
  - BS <-> Gregorian conversion
  - holiday calendar pack support
  - settlement date helper
- K-07
  - append-only audit log
  - hash chain verification job
  - evidence export stub
- K-02
  - config schema registration
  - hierarchical resolution
  - maker-checker activation flow
  - T1 pack loader
  - metadata catalog support for value sets, task schemas, and process-template parameters

**Exit Criteria**

- K-05 critical publish path meets the documented `2ms P99` target in isolated benchmarks
- K-15 dual-date conversion is available in both service and package form
- K-07 chain verification passes replay tests
- K-02 resolves config and metadata deterministically for `GLOBAL -> JURISDICTION -> OPERATOR -> TENANT -> ACCOUNT -> USER`

---

### Phase 2 — Kernel Completion and Control Plane

**Dates:** May 18, 2026 to July 10, 2026

**Objective:** Clear the kernel readiness gates required for domain implementation.

**In Scope**

- K-01 IAM
- K-14 Secrets Management
- K-03 Rules Engine
- K-04 Plugin Runtime
- K-06 Observability
- K-16 Ledger Framework
- K-17 Distributed Transaction Coordinator
- K-18 Resilience Patterns
- K-19 DLQ Management
- K-08 Data Governance
- K-09 AI Governance
- K-10 Deployment Abstraction
- K-11 API Gateway
- K-13 Admin Portal
- PU-004 Platform Manifest
- K-12 Platform SDK continues across the full phase

**Execution Sequence**

1. Security/control plane
   - K-01
   - K-14
2. Policy/extensibility
   - K-03
   - K-04

- extend K-02 for dynamic process/value metadata catalogs

3. Operational core
   - K-06
   - K-18
   - K-19
4. Financial/core state
   - K-16
   - K-17
5. Metadata governance and runtime configurability

- K-02
- value catalog registry with scoped overrides and deprecation controls
- task/form schema registry for operator and human-in-the-loop workflows
- effective-date activation and compatibility validation for metadata assets

6. Governance/control-plane surfaces
   - K-08
   - K-09
   - K-10
   - K-11
   - K-13
   - schema-driven admin surfaces for dynamic forms, catalogs, and workflow tasks
   - PU-004

**Detailed Outputs**

- K-01
  - tenant-aware auth
  - RBAC
  - MFA
  - machine credentials
- K-03/K-04
  - T2 evaluation path
  - T1/T2/T3 registration and lifecycle
  - signature verification
  - per-tier isolation
- K-06
  - metrics
  - tracing
  - structured logs
  - SLOs
  - alert routing
- K-16/K-17/K-18/K-19
  - ledger posting
  - saga orchestration
  - circuit breaker profiles
  - replay and DLQ tooling
- K-11/K-13/PU-004
  - operator surface
  - API registration
  - deployment state manifest

**Exit Criteria**

- Domain teams can consume all kernel services through stable interfaces
- T1 and T2 packs can be loaded safely in staging
- Secrets, auth, audit, and observability are all wired into one staging environment
- DLQ/replay and saga compensation are tested before domain rollout begins

---

### Phase 3 — Trading MVP Domain Path

**Dates:** July 13, 2026 to September 18, 2026

**Objective:** Deliver the minimum viable end-to-end trading flow in staging.

**In Scope**

- D-11 Reference Data
- D-04 Market Data
- D-01 OMS
- D-06 Risk Engine
- D-07 Compliance
- D-02 EMS
- D-09 Post-Trade
- D-03 PMS
- D-05 Pricing Engine

**Implementation Order**

1. D-11 Reference Data
2. D-04 Market Data
3. D-01 OMS
4. D-06 Risk Engine and D-07 Compliance
5. D-02 EMS
6. D-09 Post-Trade
7. D-03 PMS
8. D-05 Pricing Engine

**Primary Staging Workflow**

1. Reference instrument exists
2. Market data is ingested and normalized
3. OMS accepts order
4. Risk and compliance respond within pre-trade budget
5. EMS routes and records execution
6. Post-trade creates settlement obligations
7. Ledger receives postings
8. PMS updates holdings and P&L

**Exit Criteria**

- Staging order lifecycle executes without manual database intervention
- OMS critical path respects the `<=12ms e2e P99` design target in controlled benchmark runs
- Replay from K-05 can rebuild at least one projection used by OMS or PMS
- Failure cases route to DLQ or compensating saga paths instead of ad hoc recovery

---

### Phase 4 — Reporting, Operations, and Workflow Scale-Out

**Dates:** September 21, 2026 to November 13, 2026

**Objective:** Extend the MVP into a regulator-credible operating platform.

**In Scope**

- D-10 Regulatory Reporting
- D-12 Corporate Actions
- D-13 Client Money Reconciliation
- D-14 Sanctions Screening
- W-01 Workflow Orchestration
- W-02 Client Onboarding
- O-01 Operator Console
- P-01 Pack Certification
- R-01 Regulator Portal
- R-02 Incident Response & Escalation

**Focus Areas**

- Regulatory evidence generation
- client money and sanctions controls
- onboarding and operator console workflows
- pack governance and certification
- regulator-facing exports and incident response escalation

**Exit Criteria**

- Regulator evidence export is demonstrable from staging data
- Client money and sanctions controls are integrated, not standalone
- Pack certification pipeline validates T1/T2/T3 artifacts before staging install
- Major operational incidents have runbook-backed response paths

---

### Phase 5 — Hardening, Certification, and Launch Readiness

**Dates:** November 16, 2026 to December 24, 2026

**Objective:** Prove the platform under load, failure, and audit conditions.

**In Scope**

- T-01 Integration Testing
- T-02 Chaos Engineering
- performance qualification
- security validation
- DR rehearsal
- operational readiness reviews

**Exit Criteria**

- End-to-end automated regression covers the full trading path
- Chaos scenarios include broker outage, consumer lag, stale calendar config, and saga timeout
- DR rehearsal confirms documented recovery path
- Launch checklist is evidence-backed, not presentation-backed

---

## 5. Detailed First 30 Days

### Week 1 — March 9 to March 13

- finalize repo layout
- choose service template stack
- define shared schema source of truth
- set branch/version strategy
- stand up local data plane

### Week 2 — March 16 to March 20

- land event envelope package
- land dual-date package
- land CI gates
- create first kernel service template
- document local bootstrap

### Week 3 — March 23 to March 27

- implement K-05 publish path
- implement schema registry skeleton
- create event replay stub

### Week 4 — March 30 to April 3

- implement K-15 conversion service
- wire K-05 timestamps to dual-date package
- add contract tests for envelope compliance

### Week 5 — April 6 to April 10

- implement K-07 append-only audit path
- add hash-chain verification
- add audit export placeholders

### Week 6 — April 13 to April 17

- implement K-02 schema registration
- implement config resolution path
- activate first Nepal T1 config pack in staging

---

## 6. Recommended Repository Shape

```text
apps/
  admin-portal/
  regulator-portal/
services/
  kernel/
    k-01-iam/
    k-02-config/
    k-05-event-bus/
    k-07-audit/
    k-15-calendar/
  domain/
    d-01-oms/
    d-04-market-data/
    d-11-reference-data/
packages/
  contracts/
    event-envelope/
    dual-date/
    api-schemas/
  sdk/
    typescript/
    java/
infra/
  dev/
  staging/
  k8s/
packs/
  t1/
    nepal/
  t2/
    nepal/
  t3/
    nepal/
tools/
  generators/
  validation/
schemas/
  events/
  commands/
  apis/
```

---

## 7. Risks To Track From Day One

### Risk 1 — Kernel dependency cycles

Example: K-05 and K-07 are conceptually coupled.

**Mitigation:** implement core capability first, cross-cutting hook second.

### Risk 2 — Premature stack sprawl

The architecture allows multiple languages, but that should not become a Day 1 tax.

**Mitigation:** keep the first implementation wave mostly Java + TypeScript.

### Risk 3 — SDK too early, too broad

K-12 can become a bottleneck if treated as a full platform product before kernel APIs settle.

**Mitigation:** publish thin generated clients first, composite SDK second.

### Risk 4 — Domain work starting before kernel gates are real

Spec pressure may push OMS early.

**Mitigation:** block D-01 coding until K-05, K-02, K-15, K-01, K-03, K-07 foundations are demonstrably usable.

### Risk 5 — NFRs being treated as launch-time only

Latency, replay safety, and auditability must be designed in from the first service.

**Mitigation:** embed benchmarks and contract tests in each kernel phase.

---

## 8. Immediate Decisions Required

These have now been resolved for the baseline:

1. Monorepo tooling: **Gradle + pnpm**
2. Primary kernel/domain framework: **Java 21 + ActiveJ**
3. User-facing API/control-plane framework: **Node.js LTS + TypeScript + Fastify + Prisma**
4. Interface source of truth: **hybrid by transport**
   - REST + OpenAPI for external APIs
   - gRPC + protobuf for internal service contracts
5. Local runtime choice: **Docker Compose first**, with Kubernetes profile added after core bootstrap
6. Platform reuse requirement:
   - AI/ML: Ghatana `shared-services/ai-registry`, `shared-services/ai-inference-service`, `shared-services/feature-store-ingest`, `platform/java/ai-integration`
   - Event processing: Ghatana `products/aep/platform`, `platform/java/event-cloud`
   - Workflow: Ghatana `platform/java/workflow`
   - Data management: Ghatana `products/data-cloud/platform`, `products/data-cloud/spi`

Remaining open item:

7. Initial artifact registry strategy for SDKs and pack bundles

---

## 9. Current Recommendation

Proceed in this exact order:

1. Bootstrap the repo and shared contracts
2. Build K-05, K-15, K-07, K-02
3. Complete the rest of the kernel
4. Deliver the trading MVP path
5. Expand into regulatory, workflow, and hardening phases

Do **not** start with OMS screens, exchange adapters, or regulator portals before the kernel foundation exists.

Apply [../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md) to all new implementation scaffolding and documentation updates.
