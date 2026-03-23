# Week-by-Week Implementation Plan — AppPlatform Multi-Domain Operating System

**Version**: 3.0.0  
**Date**: March 13, 2026  
**Status**: Active operational execution plan  
**Schedule**: March 9, 2026 - April 30, 2027  
**Cadence**: 60 working weeks / 30 two-week sprints  
**Scope**: 654 stories across 42 epics plus GA hardening  
**Change Log**: v3.0.0 — Phase 0 bootstrap inserted; missing LLD creation tracked; sprint source-set gaps closed; Ghatana reuse references normalized; production-grade gates strengthened; velocity guardrails added; orphaned docs integrated; cross-document consistency verified against all authority sources.

---

## 1. Purpose

This document turns the active AppPlatform backlog into a concrete operating plan. It replaces the earlier 14-sprint draft and aligns the execution schedule to the normalized backlog in:

- `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md`
- `DOCUMENT_AUTHORITY_MAP.md`
- `UNIFIED_IMPLEMENTATION_PLAN.md`
- `stories/STORY_INDEX.md`
- the milestone story files identified by `stories/STORY_INDEX.md`
- `epics/DEPENDENCY_MATRIX.md`
- `lld/LLD_INDEX.md`
- `tdd_spec_master_index_v2.1.md`
- `../finance-ghatana-integration-plan.md`

The following documents are in scope for specific sprints and MUST be consulted when the sprint touches their subject area:

- `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md` — inter-pack event and API contracts (Sprints 5-10, 17-18)
- `PLUGIN_SANDBOX_SPECIFICATION.md` — T1/T2/T3 sandbox isolation (Sprints 3-4, 15-16, 18, 21)
- `EXTENSION_POINT_CATALOG.md` — kernel extension point registry (Sprints 3-4, 13-14)
- `DOMAIN_PACK_INTERFACE_SPECIFICATION.md` — domain pack contracts (Sprints 5-10)
- `DOMAIN_PACK_DEVELOPMENT_GUIDE.md` — domain pack authoring guide (Sprints 5-10, 15-16)
- `tdd_spec_banking_integration_v1.md` — banking pack certification test template (Sprint 18)
- `tdd_expansion_strategy_v2.1.md` — test expansion methodology and coverage mapping (reference for all sprints)

This plan is operational, not speculative: it only references documents and repo paths that exist today.

---

## 2. Planning Rules

1. `ADR-011` is the stack authority. No sprint may introduce a conflicting runtime, framework, or platform service.
2. `UNIFIED_IMPLEMENTATION_PLAN.md` controls milestone order. `stories/STORY_INDEX.md` controls story counts and sprint allocation.
3. The Capital Markets pack is implemented side-by-side with the kernel once prerequisite kernel contracts are stable; domain work does not wait for all kernel modules to finish.
4. Every sprint backlog item must trace to a story ID, epic requirement, and test-spec entry before coding begins, plus an active LLD section where one exists; if the active baseline has no dedicated LLD or detailed test spec for that scope, record the controlling epic sections, milestone story sections, and validation path explicitly.
5. `../finance-ghatana-integration-plan.md` is mandatory whenever a sprint touches Ghatana reuse, shared libraries, AEP, Data Cloud, AI platform services, or platform SDK concerns. The `§5.1 Kernel Module Reuse Matrix` is the binding reference for reuse percentages and Gradle artifact paths.
6. If a sprint changes customer-facing, regulatory, or market-fact language, update `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, and `Claim_Traceability_Matrix.md` in the same pass.
7. Prefer extending an existing module, contract, schema, workflow, or document before creating a new service, abstraction, file, or process; every net-new artifact must include a reuse rejection rationale.
8. Right-size every change: satisfy the current story and NFRs completely, but do not introduce speculative generality, duplicate helper layers, or story-local shortcuts that will need immediate rework.
9. Sprint velocity MUST be tracked against the capacity model in §4.1. Any sprint exceeding 40 stories requires explicit load-balancing across teams or scope deferral with documented rationale.
10. Security scanning (SAST, dependency audit) runs every sprint. Performance baselines are captured in M2A and validated continuously from M4 Sprint 19 onward.

---

## 3. Standard Two-Week Sprint Rhythm

### Week 1

- Monday: confirm target stories, dependencies, source bundle, and reuse plan.
- Tuesday-Wednesday: finalize contracts first: APIs, events, schemas, config, workflow metadata, audit points, observability.
- Thursday-Friday: write or update tests first, then land the first working vertical slice.

### Week 2

- Monday-Wednesday: complete implementation, integration coverage, performance/security checks, and documentation updates.
- Thursday: run sprint gate checks, close traceability gaps, and stage demo evidence.
- Friday: demo completed stories, capture carryover explicitly, and update sprint readiness for the next dependency set.

### Mandatory Sprint Gates

- Story IDs mapped to epic, LLD, and test references
- TDD and integration tests updated and passing
- K-07 audit path and K-06 observability path present where state changes occur
- K-02 configuration path used for variable behavior; no hardcoded jurisdiction rules
- Existing reusable module/doc path evaluated before adding a new abstraction or duplicate file
- Design scope is proportional to the story: no speculative frameworks, no one-off hacks
- Performance, security, and resilience checks executed when the changed story affects those NFRs
- Link and reference integrity preserved in touched docs
- SAST scan passes with zero critical/high findings on changed code
- Dependency audit (`./gradlew dependencyCheckAnalyze`) passes with no new unaddressed CVEs
- All public Java classes have `@doc.*` tags per Ghatana coding standards
- Async Java tests extend `EventloopTestBase`; no raw `.getResult()` calls on Promises
- Ghatana reuse decision logged for every kernel module touched (reuse / extend / new with rationale)

---

## 4. Milestone Frame

| Milestone | Sprints | Dates                                 | Stories | Story Points | Primary Scope                                |
| --------- | ------- | ------------------------------------- | ------- | ------------ | -------------------------------------------- |
| M1A       | 1-2     | March 9, 2026 - April 3, 2026         | 78      | ~220         | K-05, K-07, K-02, K-15                       |
| M1B       | 3-4     | April 6, 2026 - May 1, 2026           | 147     | ~443         | Core kernel services                         |
| M2A       | 5-9     | May 4, 2026 - July 10, 2026           | 125     | 372          | Trading and compliance core                  |
| M2B       | 8-10    | June 15, 2026 - July 24, 2026         | 104     | 316          | Post-trade, risk, reporting                  |
| M3A       | 11-12   | July 27, 2026 - August 21, 2026       | 56      | 164          | Data governance, AI, DLQ, deployment         |
| M3B       | 13-14   | August 24, 2026 - September 18, 2026  | 58      | 154          | Admin portal, SDK, workflows                 |
| M3C       | 15-16   | September 21, 2026 - October 16, 2026 | 56      | 167          | Operator, regulator, certification, manifest |
| M4        | 17-30   | October 19, 2026 - April 30, 2027     | 30      | 94           | Integration, chaos, GA hardening             |

M2A and M2B overlap intentionally in Sprints 8-9. `stories/STORY_INDEX.md` is authoritative for that overlap and Sprint 8 is the transition point where M2B work starts while the last M2A stories close.

### 4.1 Sprint Velocity & Capacity Model

The 654 stories are distributed across 6 teams (Alpha, Beta, Gamma, Delta, Epsilon, Zeta) as defined in `stories/STORY_INDEX.md`. Average team velocity is ~22 stories per sprint across teams.

| Sprint Range           | Avg Stories/Sprint | Load Assessment | Notes                                                                              |
| ---------------------- | ------------------ | --------------- | ---------------------------------------------------------------------------------- |
| 1-2 (M1A)              | 39                 | HIGH            | Bootstrap phase; 4 foundational epics, strong Ghatana reuse reduces net-new effort |
| 3-4 (M1B)              | 73.5               | CRITICAL        | Highest density; 9 epics in parallel. Requires all 6 teams at full capacity        |
| 5-7 (M2A early)        | 25                 | MODERATE        | Domain decomposition begins; specialized team assignment                           |
| 8-10 (M2A/M2B overlap) | ~38                | HIGH            | Milestone transition overlap; two milestone story files active concurrently        |
| 11-16 (M3A/B/C)        | 28                 | MODERATE        | Governance, tooling, portals; some teams idle on kernel can absorb                 |
| 17-30 (M4)             | ~2.1               | LOW             | Testing, chaos, and hardening; quality-focused, not volume-focused                 |

**Risk**: Sprints 3-4 carry 147 stories (73.5/sprint), which is 3.3× the global average. Mitigations:

1. M1B stories have 85-100% Ghatana reuse for K-06, K-18, K-19 (per `finance-ghatana-integration-plan.md §5.1`), reducing implementation effort to integration and extension.
2. Sprint 3 focuses on first slices only; Sprint 4 completes remaining work. No epic must be fully finished in Sprint 3.
3. If Sprint 3 carryover exceeds 20%, escalate to architecture team per §8.

### 4.2 Phase 0: Bootstrap (Pre-Sprint Activity)

Phase 0 covers repository setup, service templates, shared contracts, local runtime stack, and CI pipeline. This work is validated by `tdd_spec_phase0_bootstrap_v2.1.md` (30 test cases) and MUST be complete before Sprint 1 begins.

| Phase 0 Task                                                       | Validation                  | Owner         | Status Gate                                          |
| ------------------------------------------------------------------ | --------------------------- | ------------- | ---------------------------------------------------- |
| Gradle multi-project structure with `platform:java:*` dependencies | TC-P0-001 through TC-P0-010 | Platform Team | Build compiles, all platform deps resolve            |
| Service template (ActiveJ HTTP + EventloopTestBase)                | TC-P0-011 through TC-P0-018 | Platform Team | Template service starts, health check returns 200    |
| Shared contracts (protobuf, OpenAPI stubs)                         | TC-P0-019 through TC-P0-024 | Platform Team | Proto generation succeeds, OpenAPI validation passes |
| Local runtime (Docker Compose: PostgreSQL, Kafka, Redis)           | TC-P0-025 through TC-P0-028 | DevOps Team   | `docker compose up` succeeds, Testcontainers work    |
| CI pipeline (build, test, lint, SAST, dependency check)            | TC-P0-029 through TC-P0-030 | DevOps Team   | Pipeline runs green on template service              |

**Source**: `tdd_spec_phase0_bootstrap_v2.1.md`, `UNIFIED_IMPLEMENTATION_PLAN.md`

### 4.3 Missing LLD Tracking

Six modules in M3B/M3C currently have no dedicated LLD. The sprint plan acknowledges this explicitly. LLD creation is tracked below and MUST be completed during the first sprint of each module's work (Week 1 contract-design phase).

| Module                      | Epic                                  | Sprint      | LLD Creation Deadline | Validation                                                  |
| --------------------------- | ------------------------------------- | ----------- | --------------------- | ----------------------------------------------------------- |
| W-01 Workflow Orchestration | `EPIC-W-01-Workflow-Orchestration.md` | 13 (Week 1) | August 25, 2026       | LLD follows 10-section template per `lld/LLD_INDEX.md §1.1` |
| W-02 Client Onboarding      | `EPIC-W-02-Client-Onboarding.md`      | 13 (Week 1) | August 25, 2026       | LLD follows 10-section template                             |
| O-01 Operator Workflows     | `EPIC-O-01-Operator-Workflows.md`     | 15 (Week 1) | September 22, 2026    | LLD follows 10-section template                             |
| R-01 Regulator Portal       | `EPIC-R-01-Regulator-Portal.md`       | 15 (Week 1) | September 22, 2026    | LLD follows 10-section template                             |
| R-02 Incident Notification  | `EPIC-R-02-Incident-Notification.md`  | 15 (Week 1) | September 22, 2026    | LLD follows 10-section template                             |
| PU-004 Platform Manifest    | `EPIC-PU-004-Platform-Manifest.md`    | 15 (Week 1) | September 22, 2026    | LLD follows 10-section template                             |

---

## 5. Sprint-by-Sprint Plan

The "Primary Source Set" column names the minimum controlling bundle for the sprint. If a touched story has a more specific epic section, LLD section, TDD file, or traceability document, add it to the working set rather than relying on the table alone.

| Sprint | Dates                               | Focus                                                                                                                                                     | Primary Source Set                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Exit Gate                                                                                                                                 |
| ------ | ----------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 1      | March 9 - March 20, 2026            | K-05 core bus/event store plus K-07 bootstrap audit path                                                                                                  | `stories/MILESTONE_1A_STORIES.md`, `epics/EPIC-K-05-Event-Bus.md`, `epics/EPIC-K-07-Audit-Framework.md`, `lld/LLD_K05_EVENT_BUS.md`, `lld/LLD_K07_AUDIT_FRAMEWORK.md`, `tdd_spec_k05_event_bus_expanded_v2.1.md`, `tdd_spec_k07_audit_framework_expanded_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           | K-05 append/publish path works and K-07 synchronous audit bootstrap is live                                                               |
| 2      | March 23 - April 3, 2026            | Close K-05/K-07 bootstrap cycle, deliver K-02 hierarchy and K-15 calendar core                                                                            | `stories/MILESTONE_1A_STORIES.md`, `epics/EPIC-K-02-Configuration-Engine.md`, `epics/EPIC-K-15-Dual-Calendar-Service.md`, `lld/LLD_K02_CONFIGURATION_ENGINE.md`, `lld/LLD_K15_DUAL_CALENDAR.md`, `tdd_spec_k02_configuration_engine_expanded_v2.1.md`, `tdd_spec_k15_dual_calendar_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1 (K-02: 70% reuse; K-15: net contributor back to Ghatana)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | K-05 to K-07 audit hook enabled, config hierarchy works, calendar correctness suite passes                                                |
| 3      | April 6 - April 17, 2026            | Sprint 3 first slices: K-01 and K-14 plus initial K-06, K-11, K-16, K-17, and K-18 work                                                                   | `stories/MILESTONE_1B_STORIES.md`, `epics/EPIC-K-01-IAM.md`, `epics/EPIC-K-14-Secrets-Management.md`, `epics/EPIC-K-06-Observability.md`, `epics/EPIC-K-11-API-Gateway.md`, `epics/EPIC-K-16-Ledger-Framework.md`, `epics/EPIC-K-17-Distributed-Transaction-Coordinator.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `lld/LLD_K01_IAM.md`, `lld/LLD_K14_SECRETS_MANAGEMENT.md`, `lld/LLD_K06_OBSERVABILITY.md`, `lld/LLD_K11_API_GATEWAY.md`, `lld/LLD_K16_LEDGER_FRAMEWORK.md`, `lld/LLD_K17_DISTRIBUTED_TRANSACTION_COORDINATOR.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `epics/DEPENDENCY_MATRIX.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1 (K-06: ~100%, K-18: 90%, K-11: 80% reuse)                                                                                                                                                                          | Early auth, secrets, observability, gateway, ledger, DTC, and resilience slices are stable enough for Sprint 4 completion work            |
| 4      | April 20 - May 1, 2026              | Sprint 4 completion: finish M1B, complete remaining K-01/K-14 work, and add K-03/K-04 without duplicating earlier kernel work                             | `stories/MILESTONE_1B_STORIES.md`, `epics/EPIC-K-01-IAM.md`, `epics/EPIC-K-14-Secrets-Management.md`, `epics/EPIC-K-03-Rules-Engine.md`, `epics/EPIC-K-04-Plugin-Runtime.md`, `epics/EPIC-K-06-Observability.md`, `epics/EPIC-K-11-API-Gateway.md`, `epics/EPIC-K-16-Ledger-Framework.md`, `epics/EPIC-K-17-Distributed-Transaction-Coordinator.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `lld/LLD_K01_IAM.md`, `lld/LLD_K14_SECRETS_MANAGEMENT.md`, `lld/LLD_K03_RULES_ENGINE.md`, `lld/LLD_K04_PLUGIN_RUNTIME.md`, `lld/LLD_K06_OBSERVABILITY.md`, `lld/LLD_K11_API_GATEWAY.md`, `lld/LLD_K16_LEDGER_FRAMEWORK.md`, `lld/LLD_K17_DISTRIBUTED_TRANSACTION_COORDINATOR.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `PLUGIN_SANDBOX_SPECIFICATION.md`, `EXTENSION_POINT_CATALOG.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1 (K-03: 60%, K-04: 80%, K-01: 85% reuse) | Core kernel readiness gates met for domain-pack consumption                                                                               |
| 5      | May 4 - May 15, 2026                | D-11 reference data, D-04 market data, D-01 OMS tracer bullet                                                                                             | `stories/MILESTONE_2A_STORIES.md`, `epics/EPIC-D-11-Reference-Data.md`, `epics/EPIC-D-04-Market-Data.md`, `epics/EPIC-D-01-OMS.md`, `lld/LLD_D11_REFERENCE_DATA.md`, `lld/LLD_D04_MARKET_DATA.md`, `lld/LLD_D01_OMS.md`, `tdd_spec_d01_oms_expanded_v2.1.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`, `DOMAIN_PACK_INTERFACE_SPECIFICATION.md`, `../finance-ghatana-integration-plan.md` §3.1 (AEP for event processing), §3.2 (Data Cloud for reference data)                                                                                                                                                                                                                                                                                                                                                                                                                   | Reference data, market data, and OMS first order flow integrate through K-05/K-03/K-16                                                    |
| 6      | May 18 - May 29, 2026               | D-07 compliance, D-14 sanctions, D-06 risk, D-01 continuation                                                                                             | `stories/MILESTONE_2A_STORIES.md`, `epics/EPIC-D-07-Compliance.md`, `epics/EPIC-D-14-Sanctions-Screening.md`, `epics/EPIC-D-06-Risk-Engine.md`, `epics/EPIC-D-01-OMS.md`, `lld/LLD_D07_COMPLIANCE_ENGINE.md`, `lld/LLD_D14_SANCTIONS_SCREENING.md`, `lld/LLD_D06_RISK_ENGINE.md`, `lld/LLD_D01_OMS.md`, `tdd_spec_d01_oms_expanded_v2.1.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`, `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md`, `../finance-ghatana-integration-plan.md` §3.1 (AEP for compliance event streams)                                                                                                                                                                                                                                                 | Pre-trade compliance and sanctions controls are enforceable in OMS/risk path                                                              |
| 7      | June 1 - June 12, 2026              | D-02 EMS                                                                                                                                                  | `stories/MILESTONE_2A_STORIES.md`, `epics/EPIC-D-02-EMS.md`, `lld/LLD_D02_EMS.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.2                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | EMS order-routing path works against approved OMS and market-data contracts                                                               |
| 8      | June 15 - June 26, 2026             | D-02 EMS finish plus D-09 post-trade start                                                                                                                | `stories/MILESTONE_2A_STORIES.md`, `stories/MILESTONE_2B_STORIES.md`, `epics/EPIC-D-02-EMS.md`, `epics/EPIC-D-09-Post-Trade.md`, `lld/LLD_D02_EMS.md`, `lld/LLD_D09_POST_TRADE.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`, `../finance-ghatana-integration-plan.md` §3.1 (AEP for post-trade event streams)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | Execution-to-post-trade handoff is stable and audit-complete                                                                              |
| 9      | June 29 - July 10, 2026             | D-13 reconciliation, D-03 PMS, D-05 pricing                                                                                                               | `stories/MILESTONE_2B_STORIES.md`, `epics/EPIC-D-13-Client-Money-Reconciliation.md`, `epics/EPIC-D-03-PMS.md`, `epics/EPIC-D-05-Pricing-Engine.md`, `lld/LLD_D13_CLIENT_MONEY_RECONCILIATION.md`, `lld/LLD_D03_PMS.md`, `lld/LLD_D05_PRICING_ENGINE.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`, `../finance-ghatana-integration-plan.md` §3.2 (Data Cloud for portfolio and reconciliation data)                                                                                                                                                                                                                                                                                                                                                                                                                             | Pricing, portfolio, and reconciliation flows consume settled domain events correctly                                                      |
| 10     | July 13 - July 24, 2026             | D-08 surveillance, D-10 reporting, D-12 corporate actions                                                                                                 | `stories/MILESTONE_2B_STORIES.md`, `epics/EPIC-D-08-Surveillance.md`, `epics/EPIC-D-10-Regulatory-Reporting.md`, `epics/EPIC-D-12-Corporate-Actions.md`, `lld/LLD_D08_SURVEILLANCE.md`, `lld/LLD_D10_REGULATORY_REPORTING.md`, `lld/LLD_D12_CORPORATE_ACTIONS.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`, `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md`, `../finance-ghatana-integration-plan.md` §3.3 (AI Platform for surveillance pattern detection)                                                                                                                                                                                                                                                                                                                                                    | Domain pack reaches complete reference-business coverage for trading through reporting                                                    |
| 11     | July 27 - August 7, 2026            | Sprint 11 first slices across K-08, K-09, K-19, and K-10                                                                                                  | `stories/MILESTONE_3A_STORIES.md`, `epics/EPIC-K-08-Data-Governance.md`, `epics/EPIC-K-09-AI-Governance.md`, `epics/EPIC-K-19-DLQ-Management.md`, `epics/EPIC-K-10-Deployment-Abstraction.md`, `lld/LLD_K08_DATA_GOVERNANCE.md`, `lld/LLD_K09_AI_GOVERNANCE.md`, `lld/LLD_K19_DLQ_MANAGEMENT.md`, `lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md`, `../finance-ghatana-integration-plan.md` §5.1 (K-08: 95% Data Cloud reuse, K-09: 80% AI platform reuse, K-19: 90% AEP DLQ reuse)                                                                                                                                                                                                                                                                                           | Governance, DLQ, and deployment first slices are stable and dependency-safe for Sprint 12 completion                                      |
| 12     | August 10 - August 21, 2026         | Sprint 12 completion across K-08, K-09, K-19, and K-10                                                                                                    | `stories/MILESTONE_3A_STORIES.md`, `epics/EPIC-K-08-Data-Governance.md`, `epics/EPIC-K-09-AI-Governance.md`, `epics/EPIC-K-19-DLQ-Management.md`, `epics/EPIC-K-10-Deployment-Abstraction.md`, `lld/LLD_K08_DATA_GOVERNANCE.md`, `lld/LLD_K09_AI_GOVERNANCE.md`, `lld/LLD_K19_DLQ_MANAGEMENT.md`, `lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Replay, rollback, governance, and deployment guardrails are production-ready                                                              |
| 13     | August 24 - September 4, 2026       | Sprint 13 first slices across K-13, K-12, W-01, and W-02. **LLD creation gate**: W-01 and W-02 LLDs must be authored in Week 1 (see §4.3)                 | `stories/MILESTONE_3B_STORIES.md`, `epics/EPIC-K-13-Admin-Portal.md`, `epics/EPIC-K-12-Platform-SDK.md`, `epics/EPIC-W-01-Workflow-Orchestration.md`, `epics/EPIC-W-02-Client-Onboarding.md`, `lld/LLD_K13_ADMIN_PORTAL.md`, `lld/LLD_K12_PLATFORM_SDK.md`, `EXTENSION_POINT_CATALOG.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1 (K-12 SDK: 100% composition of platform artifacts)                                                                                                                                                                                                                                                                                                                                                                                                                                 | Admin, SDK, workflow, and onboarding first slices are integrated without duplicating control-plane behavior; W-01 and W-02 LLDs delivered |
| 14     | September 7 - September 18, 2026    | Sprint 14 completion across K-13, K-12, W-01, and W-02                                                                                                    | `stories/MILESTONE_3B_STORIES.md`, `epics/EPIC-K-13-Admin-Portal.md`, `epics/EPIC-K-12-Platform-SDK.md`, `epics/EPIC-W-01-Workflow-Orchestration.md`, `epics/EPIC-W-02-Client-Onboarding.md`, `lld/LLD_K13_ADMIN_PORTAL.md`, `lld/LLD_K12_PLATFORM_SDK.md`, `lld/LLD_W01_WORKFLOW_ORCHESTRATION.md` (created Sprint 13), `lld/LLD_W02_CLIENT_ONBOARDING.md` (created Sprint 13), `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                            | End-to-end onboarding and workflow orchestration work through workflow, compliance, and audit layers                                      |
| 15     | September 21 - October 2, 2026      | Sprint 15 first slices for O-01, P-01, R-01, R-02, and PU-004. **LLD creation gate**: O-01, R-01, R-02, PU-004 LLDs must be authored in Week 1 (see §4.3) | `stories/MILESTONE_3C_STORIES.md`, `epics/EPIC-O-01-Operator-Workflows.md`, `epics/EPIC-P-01-Pack-Certification.md`, `epics/EPIC-R-01-Regulator-Portal.md`, `epics/EPIC-R-02-Incident-Notification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `lld/LLD_P01_PACK_CERTIFICATION.md`, `PLUGIN_SANDBOX_SPECIFICATION.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Operator, regulator, certification, and manifest foundations are integrated; O-01, R-01, R-02, PU-004 LLDs delivered                      |
| 16     | October 5 - October 16, 2026        | Sprint 16 completion across O-01, P-01, R-01, R-02, and PU-004                                                                                            | `stories/MILESTONE_3C_STORIES.md`, `epics/EPIC-O-01-Operator-Workflows.md`, `epics/EPIC-P-01-Pack-Certification.md`, `epics/EPIC-R-01-Regulator-Portal.md`, `epics/EPIC-R-02-Incident-Notification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `lld/LLD_P01_PACK_CERTIFICATION.md`, `lld/LLD_O01_OPERATOR_WORKFLOWS.md` (created Sprint 15), `lld/LLD_R01_REGULATOR_PORTAL.md` (created Sprint 15), `lld/LLD_R02_INCIDENT_NOTIFICATION.md` (created Sprint 15), `lld/LLD_PU004_PLATFORM_MANIFEST.md` (created Sprint 15), `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                | Pack certification and platform-manifest flows are stable enough for M4 validation                                                        |
| 17     | October 19 - October 30, 2026       | T-01 end-to-end order-to-settlement and compliance path                                                                                                   | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md`, referenced domain/kernel stories and LLDs for the exercised flow                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Golden-path E2E suite passes against the full reference pack                                                                              |
| 18     | November 2 - November 13, 2026      | T-01 plugin and platform-upgrade integration scenarios                                                                                                    | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-P-01-Pack-Certification.md`, `PLUGIN_SANDBOX_SPECIFICATION.md`, `tdd_spec_banking_integration_v1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          | Upgrade, certification, and rollback scenarios are reproducible; banking pack template validated                                          |
| 19     | November 16 - November 27, 2026     | T-01 performance baseline for OMS, K-05, K-11                                                                                                             | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, performance NFRs from exercised epics and LLDs                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Core latency/throughput benchmarks meet committed targets or raise tracked gaps                                                           |
| 20     | November 30 - December 11, 2026     | T-01 database performance and service-contract validation                                                                                                 | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, exercised LLD contracts and SDK contracts                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Contract, schema, and persistence-layer compatibility is locked                                                                           |
| 21     | December 14 - December 25, 2026     | T-01 plugin contracts plus T-02 pod/network chaos start                                                                                                   | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Plugin isolation and first chaos scenarios pass with documented failure behavior                                                          |
| 22     | December 28, 2026 - January 8, 2027 | T-02 resource exhaustion, dependency failure, latency injection                                                                                           | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `epics/EPIC-K-06-Observability.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `lld/LLD_K06_OBSERVABILITY.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | Resilience patterns respond as specified and are observable end-to-end                                                                    |
| 23     | January 11 - January 22, 2027       | T-02 poison-pill handling, DR drill, backup/restore                                                                                                       | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `epics/EPIC-K-19-DLQ-Management.md`, `epics/EPIC-K-10-Deployment-Abstraction.md`, `lld/LLD_K19_DLQ_MANAGEMENT.md`, `lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Recovery and replay drills succeed with audit evidence                                                                                    |
| 24     | January 25 - February 5, 2027       | T-02 resilience scorecard and continuous-chaos operating model                                                                                            | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, runbooks and operations docs                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Continuous resilience program is defined and repeatable                                                                                   |
| 25     | February 8 - February 19, 2027      | GA security validation and platform hardening start                                                                                                       | `stories/MILESTONE_4_STORIES.md` (Feature GA-F01), `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md`, `epics/EPIC-K-01-IAM.md`, `epics/EPIC-K-11-API-Gateway.md`, `epics/EPIC-K-14-Secrets-Management.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `lld/LLD_K01_IAM.md`, `lld/LLD_K11_API_GATEWAY.md`, `lld/LLD_K14_SECRETS_MANAGEMENT.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                  | Security blockers and high-risk findings are closed or formally waived                                                                    |
| 26     | February 22 - March 5, 2027         | GA SLA validation, runbook library, operational sign-off prep                                                                                             | `stories/MILESTONE_4_STORIES.md` (Feature GA-F02), `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-O-01-Operator-Workflows.md`, `epics/EPIC-R-01-Regulator-Portal.md`, `epics/EPIC-R-02-Incident-Notification.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         | Runbooks, SLAs, and operating evidence are ready for release review                                                                       |
| 27     | March 8 - March 19, 2027            | GA remediation buffer sprint                                                                                                                              | carryover from Sprints 17-26 only; no speculative net-new scope                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            | All remaining P0/P1 defects have owners and closure dates                                                                                 |
| 28     | March 22 - April 2, 2027            | GA readiness checklist sprint                                                                                                                             | `stories/MILESTONE_4_STORIES.md` (Feature GA-F03, STORY-GA-005), `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | Readiness checklist is complete and evidence-backed                                                                                       |
| 29     | April 5 - April 16, 2027            | Final hardening and migration rehearsal                                                                                                                   | `stories/MILESTONE_4_STORIES.md`, `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-P-01-Pack-Certification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `lld/LLD_P01_PACK_CERTIFICATION.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              | Cutover rehearsal succeeds end-to-end                                                                                                     |
| 30     | April 19 - April 30, 2027           | Launch and hypercare setup                                                                                                                                | `stories/MILESTONE_4_STORIES.md` (Feature GA-F03, STORY-GA-006), `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-R-02-Incident-Notification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | GA release decision can be made with full implementation evidence                                                                         |

---

## 6. Definition of Ready for Every Sprint Story

Before a story enters active development, the team must be able to answer all of the following with exact repo references:

- What story ID and milestone file authorize the work?
- Which epic sections and, where an active LLD exists, which LLD sections govern the behavior?
- Which TDD spec, phase-level test spec, or integration test cases prove completion?
- Which Ghatana component is reused, extended, or intentionally not reused? (Cite `finance-ghatana-integration-plan.md §5.1` artifact path.)
- Which event, API, schema, config, workflow, audit, and observability contracts change?
- Which source-trace docs change if wording, market facts, or regulatory claims are touched?
- Is the story's module LLD authored? If not, the LLD creation is a blocking prerequisite (see §4.3).

If any answer is missing, the story is not ready.

---

## 7. Definition of Done for Every Sprint

- Story acceptance criteria met in the milestone story file
- Relevant epic contracts implemented or updated, and relevant LLD contracts updated where an active LLD exists
- Tests updated and passing at the correct layer (unit, component, integration per `tdd_spec_master_index_v2.1.md`)
- All 20 ARB findings remain covered (no test regression — validate against ARB coverage matrix in TDD master index)
- Reuse decision documented when Ghatana components were in scope
- All public Java classes have JavaDoc and `@doc.*` tags (type, purpose, layer, pattern)
- Async Java tests extend `EventloopTestBase`; no raw `.getResult()` calls
- `./gradlew spotlessApply` passes; zero checkstyle/PMD warnings
- SAST scan passes with zero critical/high findings on changed code
- Documentation and cross-links updated in the same change
- No broken links introduced in active docs
- Open risks and deferred work explicitly captured, not implied

---

## 8. Production-Grade Quality Gates

### 8.1 Security Gates (Every Sprint)

| Gate             | Tool / Process                                           | Threshold               | Enforcement                |
| ---------------- | -------------------------------------------------------- | ----------------------- | -------------------------- |
| SAST             | `./gradlew checkstyleMain pmdMain`                       | Zero critical/high      | CI pipeline blocks merge   |
| Dependency Audit | OWASP dependency-check (`config/owasp-suppressions.xml`) | No new unaddressed CVEs | CI pipeline blocks merge   |
| Secret Scanning  | Pre-commit hook                                          | No secrets in source    | Git pre-commit             |
| Container Scan   | Trivy on Docker images                                   | No critical/high        | CI pipeline for Sprint 17+ |

### 8.2 Performance Gates (Sprint 5+)

| Metric              | Target      | Measured From     | Enforcement                       |
| ------------------- | ----------- | ----------------- | --------------------------------- |
| K-05 event append   | < 5ms P99   | Sprint 5 baseline | Performance regression test in CI |
| K-11 API gateway    | < 50ms P95  | Sprint 5 baseline | Load test in Sprint 19            |
| D-01 OMS order path | < 100ms P99 | Sprint 5 baseline | E2E perf test in Sprint 17        |
| D-04 market data    | 50K TPS     | Sprint 5 baseline | Load test in Sprint 19            |

### 8.3 Observability Gates (Sprint 2+)

Every service deployed from Sprint 2 onward MUST expose:

- Prometheus `/metrics` endpoint (via `platform:java:observability-http`)
- Health check endpoint (`/health/live`, `/health/ready`)
- Structured JSON logging with trace correlation IDs
- K-07 audit events for all state-changing operations

### 8.4 Test Coverage Gates

| Phase                   | Minimum Coverage                      | Enforcement         |
| ----------------------- | ------------------------------------- | ------------------- |
| M1A (Sprints 1-2)       | 224 TCs across K-05/K-07/K-02/K-15    | TDD spec validation |
| M1B (Sprints 3-4)       | 284 TCs across 16 kernel modules      | TDD spec validation |
| M2A/M2B (Sprints 5-10)  | 186 TCs across 9 domain modules       | TDD spec validation |
| M3A/B/C (Sprints 11-16) | 136 TCs across 12 operational modules | TDD spec validation |
| M4 (Sprints 17-30)      | All 660 TCs green; 0 regression       | Full suite CI gate  |

---

## 9. Ghatana Platform Reuse Summary

This section consolidates the reuse decisions from `finance-ghatana-integration-plan.md §5.1` for quick sprint-level reference. The integration plan is the binding source; this summary is for convenience.

| Module             | Ghatana Artifact                                                    | Reuse % | Sprint | Integration Type  |
| ------------------ | ------------------------------------------------------------------- | ------- | ------ | ----------------- |
| K-01 IAM           | `:platform:java:security`, `shared-services/auth-service`           | 85%     | 3-4    | Direct + Extend   |
| K-02 Config        | `:platform:java:config`, `:platform:java:database`                  | 70%     | 2      | Reuse + Extend    |
| K-03 Rules         | `:platform:java:plugin`, `:products:aep:platform`                   | 60%     | 4      | Reuse + Extend    |
| K-04 Plugin        | `:platform:java:plugin`                                             | 80%     | 4      | Direct + Extend   |
| K-05 Event Bus     | `:products:aep:platform`, `:platform:java:connectors`               | 90%     | 1-2    | Primary Reuse     |
| K-06 Observability | `:platform:java:observability`, `:platform:java:observability-http` | ~100%   | 3-4    | Direct            |
| K-07 Audit         | `:platform:java:audit`, `:products:data-cloud:platform`             | 90%     | 1-2    | Reuse + Extend    |
| K-08 Data Gov      | `:products:data-cloud:platform`                                     | 95%     | 11-12  | Direct            |
| K-09 AI Gov        | `:platform:java:ai-integration`, `shared-services/ai-registry`      | 80%     | 11-12  | Reuse + Extend    |
| K-10 Deploy        | `:platform:java:runtime`                                            | 50%     | 11-12  | Reuse + Infra     |
| K-11 Gateway       | `:platform:java:http`, `shared-services/auth-gateway`               | 80%     | 3-4    | Reuse + Extend    |
| K-12 SDK           | All `:platform:java:*` + AEP + Data Cloud SPI                       | 100%    | 13-14  | Aggregation       |
| K-13 Admin         | React 18 + Tailwind + Fastify + Prisma                              | 0% Java | 13-14  | New (Node.js)     |
| K-14 Secrets       | `:platform:java:security`                                           | 60%     | 3-4    | Reuse + Extend    |
| K-15 Calendar      | `:platform:java:domain` (contributes back)                          | 0%      | 2      | New (contributor) |
| K-16 Ledger        | `:platform:java:database`                                           | 40%     | 3-4    | New + Reuse       |
| K-17 DTC           | `:platform:java:connectors`, `:products:aep:platform`               | 85%     | 3-4    | Reuse + Extend    |
| K-18 Resilience    | `:platform:java:agent-resilience`                                   | 90%     | 3-4    | Direct + Extend   |
| K-19 DLQ           | `:products:aep:platform`, `:platform:java:ai-integration`           | 90%     | 11-12  | Direct + Extend   |

---

## 10. Cross-Document Consistency Verification

This section records the consistency checks performed and any deviations found between authority documents. It MUST be updated whenever the sprint plan or authority documents change.

### 10.1 Story Count Verification

| Source                           | M1A | M1B | M2A | M2B | M3A | M3B | M3C | M4  | Total |
| -------------------------------- | --- | --- | --- | --- | --- | --- | --- | --- | ----- |
| `STORY_INDEX.md`                 | 78  | 147 | 125 | 104 | 56  | 58  | 56  | 30  | 654   |
| `UNIFIED_IMPLEMENTATION_PLAN.md` | 78  | 147 | 125 | 104 | 56  | 58  | 56  | 30  | 654   |
| This plan (§4)                   | 78  | 147 | 125 | 104 | 56  | 58  | 56  | 30  | 654   |
| **Status**                       | ✅  | ✅  | ✅  | ✅  | ✅  | ✅  | ✅  | ✅  | ✅    |

### 10.2 Epic-to-Sprint Coverage

All 42 epics are assigned to sprints. No orphaned epics.

| Epic Category           | Count | Sprints    | Status                               |
| ----------------------- | ----- | ---------- | ------------------------------------ |
| Kernel (K-01 to K-19)   | 19    | 1-4, 11-14 | ✅ All covered                       |
| Domain (D-01 to D-14)   | 14    | 5-10       | ✅ All covered                       |
| Workflow (W-01, W-02)   | 2     | 13-14      | ✅ Covered (LLD gap tracked in §4.3) |
| Operations (O-01)       | 1     | 15-16      | ✅ Covered (LLD gap tracked in §4.3) |
| Pack Governance (P-01)  | 1     | 15-16      | ✅ Covered (LLD exists)              |
| Platform Unity (PU-004) | 1     | 15-16      | ✅ Covered (LLD gap tracked in §4.3) |
| Regulatory (R-01, R-02) | 2     | 15-16      | ✅ Covered (LLD gap tracked in §4.3) |
| Testing (T-01, T-02)    | 2     | 17-24      | ✅ Covered                           |
| GA Hardening            | N/A   | 25-30      | ✅ 6 stories, 3 features             |

### 10.3 Test Spec Coverage

All 660 test cases across 5 phases are mapped to sprints via TDD master index. No orphaned test specs.

| TDD Spec File                                        | Sprints Used            | Status                |
| ---------------------------------------------------- | ----------------------- | --------------------- |
| `tdd_spec_k05_event_bus_expanded_v2.1.md`            | 1, 2                    | ✅                    |
| `tdd_spec_k07_audit_framework_expanded_v2.1.md`      | 1                       | ✅                    |
| `tdd_spec_k02_configuration_engine_expanded_v2.1.md` | 2                       | ✅                    |
| `tdd_spec_k15_dual_calendar_v2.1.md`                 | 2                       | ✅                    |
| `tdd_spec_phase2_kernel_completion_v2.1.md`          | 3-4, 11-16              | ✅                    |
| `tdd_spec_d01_oms_expanded_v2.1.md`                  | 5, 6                    | ✅                    |
| `tdd_spec_phase3_trading_mvp_v2.1.md`                | 5-9                     | ✅                    |
| `tdd_spec_phase4_5_operational_hardening_v2.1.md`    | 6, 9-30                 | ✅                    |
| `tdd_spec_phase0_bootstrap_v2.1.md`                  | Phase 0 (pre-sprint)    | ✅                    |
| `tdd_spec_banking_integration_v1.md`                 | 18                      | ✅ (newly integrated) |
| `tdd_expansion_strategy_v2.1.md`                     | Reference (all sprints) | ✅ (newly integrated) |

### 10.4 ADR-011 Compliance Check

| ADR-011 Rule                    | Plan Compliance | Evidence                                   |
| ------------------------------- | --------------- | ------------------------------------------ |
| Java 21 + ActiveJ for kernel    | ✅              | All kernel modules (K-01 to K-19) use Java |
| Node.js + Fastify for user API  | ✅              | K-13 Admin Portal uses Node.js             |
| No Kong, GraphQL, MongoDB, etc. | ✅              | No excluded tech in any sprint             |
| Ghatana reuse before net-new    | ✅              | §9 documents all reuse decisions           |
| Kafka 3+ for event backbone     | ✅              | K-05 uses AEP/Kafka                        |
| PostgreSQL for primary DB       | ✅              | K-16 Ledger, K-05 Event Store              |

### 10.5 Dependency Matrix Alignment

The sprint order in §5 respects the 9-layer dependency structure in `epics/DEPENDENCY_MATRIX.md`:

| Dep Layer | Modules                                        | Plan Sprint | Respects Order?      |
| --------- | ---------------------------------------------- | ----------- | -------------------- |
| Bootstrap | K-05, K-07                                     | 1           | ✅                   |
| Layer 1   | K-02, K-15                                     | 2           | ✅ (after bootstrap) |
| Layer 2   | K-01, K-06, K-14                               | 3-4         | ✅ (after L1)        |
| Layer 3   | K-03, K-04, K-11, K-16, K-17, K-18             | 3-4         | ✅ (after L2)        |
| Layer 4   | D-11, D-04                                     | 5           | ✅ (after kernel)    |
| Layer 5   | D-01, D-06, D-07, D-14                         | 5-6         | ✅ (after L4)        |
| Layer 6   | D-02, D-09, D-03, D-05, D-08, D-10, D-12, D-13 | 7-10        | ✅ (after L5)        |
| Layer 7   | K-08, K-09, K-19, K-10                         | 11-12       | ✅                   |
| Layer 8   | W-01, W-02, K-13, K-12                         | 13-14       | ✅                   |
| Layer 9   | O-01, P-01, R-01, R-02, PU-004, T-01, T-02     | 15-30       | ✅                   |

---

## 11. Escalation Rules

1. If a sprint plan conflicts with `ADR-011`, stop and resolve at the architecture layer first.
2. If story counts or sprint assignments conflict with a narrative plan, use `stories/STORY_INDEX.md`.
3. If a supporting design doc still says "kernel first, domain later," follow this plan and refresh the supporting doc.
4. If a work item depends on a file or module that does not exist in the repo, create a tracked prerequisite instead of pretending it exists.
5. If a sprint's story count exceeds 40, the team lead must document the load-balance strategy or defer stories with rationale before Sprint Week 1 Monday.
6. If a missing LLD (§4.3) is not authored by its deadline, the affected stories are not ready (§6) and must be deferred.

---

## 12. Appendix: Review Findings & Resolution Log

This section records all findings from the v2.0.0 → v3.0.0 review and their resolutions.

| #    | Finding                                                                                                       | Severity | Resolution                                                              | Section          |
| ---- | ------------------------------------------------------------------------------------------------------------- | -------- | ----------------------------------------------------------------------- | ---------------- |
| F-01 | Phase 0 bootstrap (30 TCs) not scheduled in any sprint                                                        | HIGH     | Added §4.2 Phase 0 as pre-sprint activity with task breakdown           | §4.2             |
| F-02 | 6 modules (W-01, W-02, O-01, R-01, R-02, PU-004) lack LLDs                                                    | HIGH     | Added §4.3 Missing LLD Tracking with creation deadlines                 | §4.3             |
| F-03 | Sprint 2 missing `finance-ghatana-integration-plan.md` reference (K-02: 70%, K-15: contributor)               | MEDIUM   | Added reference to Sprint 2 source set                                  | §5 Sprint 2      |
| F-04 | Sprint 3 missing LLD file references (7 LLD files)                                                            | MEDIUM   | Added all 7 LLD files to Sprint 3 source set                            | §5 Sprint 3      |
| F-05 | Sprints 5-10 missing `finance-ghatana-integration-plan.md` references for domain modules using AEP/Data Cloud | MEDIUM   | Added AEP/Data Cloud references to Sprints 5, 6, 8, 9, 10               | §5               |
| F-06 | Sprints 11-12 missing `finance-ghatana-integration-plan.md` references (K-08: 95%, K-09: 80%, K-19: 90%)      | MEDIUM   | Added reuse references to Sprints 11, 12                                | §5               |
| F-07 | `INTER_DOMAIN_PACK_COMMUNICATION_SPEC.md` not referenced in domain sprints                                    | MEDIUM   | Added to Sprints 5, 6, 8, 9, 10, 17                                     | §5               |
| F-08 | `PLUGIN_SANDBOX_SPECIFICATION.md` not referenced in K-04 sprint                                               | LOW      | Added to Sprint 4 and Sprint 15                                         | §5               |
| F-09 | `EXTENSION_POINT_CATALOG.md` orphaned                                                                         | LOW      | Added to Sprint 4 and Sprint 13                                         | §1, §5           |
| F-10 | `tdd_spec_banking_integration_v1.md` orphaned                                                                 | LOW      | Integrated into Sprint 18 for banking pack template validation          | §1, §5           |
| F-11 | `tdd_expansion_strategy_v2.1.md` orphaned                                                                     | LOW      | Integrated as reference document for all sprints                        | §1               |
| F-12 | No sprint velocity analysis or capacity model                                                                 | MEDIUM   | Added §4.1 with per-sprint load assessment and risk mitigation          | §4.1             |
| F-13 | Sprint gates missing SAST, dependency audit, and Ghatana coding standards                                     | MEDIUM   | Added 5 production-grade gates to §3 Mandatory Sprint Gates             | §3               |
| F-14 | Definition of Done missing test quality, ARB coverage, and code style checks                                  | MEDIUM   | Strengthened §7 with ARB regression, JavaDoc, spotless, and SAST gates  | §7               |
| F-15 | No production-grade quality gates (security, performance, observability, test coverage)                       | HIGH     | Added §8 with 4 gate categories and specific thresholds                 | §8               |
| F-16 | No Ghatana reuse summary table for quick sprint reference                                                     | LOW      | Added §9 consolidating all reuse decisions                              | §9               |
| F-17 | No cross-document consistency verification                                                                    | HIGH     | Added §10 with 5 verification tables (stories, epics, tests, ADR, deps) | §10              |
| F-18 | Sprint 13/15 exit gates didn't require LLD delivery                                                           | MEDIUM   | Updated exit gates to include LLD creation confirmation                 | §5 Sprint 13, 15 |
| F-19 | Sprint 18 exit gate didn't mention banking pack template                                                      | LOW      | Updated exit gate                                                       | §5 Sprint 18     |
| F-20 | Definition of Ready missing LLD prerequisite check                                                            | MEDIUM   | Added LLD check to §6                                                   | §6               |
