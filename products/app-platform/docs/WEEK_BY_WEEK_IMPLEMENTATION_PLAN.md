# Week-by-Week Implementation Plan — AppPlatform Multi-Domain Operating System

**Version**: 2.0.0  
**Date**: March 13, 2026  
**Status**: Active operational execution plan  
**Schedule**: March 9, 2026 - April 30, 2027  
**Cadence**: 60 working weeks / 30 two-week sprints  
**Scope**: 654 stories across 42 epics plus GA hardening

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

This plan is operational, not speculative: it only references documents and repo paths that exist today.

---

## 2. Planning Rules

1. `ADR-011` is the stack authority. No sprint may introduce a conflicting runtime, framework, or platform service.
2. `UNIFIED_IMPLEMENTATION_PLAN.md` controls milestone order. `stories/STORY_INDEX.md` controls story counts and sprint allocation.
3. The Capital Markets pack is implemented side-by-side with the kernel once prerequisite kernel contracts are stable; domain work does not wait for all kernel modules to finish.
4. Every sprint backlog item must trace to a story ID, epic requirement, and test-spec entry before coding begins, plus an active LLD section where one exists; if the active baseline has no dedicated LLD or detailed test spec for that scope, record the controlling epic sections, milestone story sections, and validation path explicitly.
5. `../finance-ghatana-integration-plan.md` is mandatory whenever a sprint touches Ghatana reuse, shared libraries, AEP, Data Cloud, AI platform services, or platform SDK concerns.
6. If a sprint changes customer-facing, regulatory, or market-fact language, update `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, and `Claim_Traceability_Matrix.md` in the same pass.
7. Prefer extending an existing module, contract, schema, workflow, or document before creating a new service, abstraction, file, or process; every net-new artifact must include a reuse rejection rationale.
8. Right-size every change: satisfy the current story and NFRs completely, but do not introduce speculative generality, duplicate helper layers, or story-local shortcuts that will need immediate rework.

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

---

## 4. Milestone Frame

| Milestone | Sprints | Dates | Stories | Story Points | Primary Scope |
| --------- | ------- | ----- | ------- | ------------ | ------------- |
| M1A | 1-2 | March 9, 2026 - April 3, 2026 | 78 | ~220 | K-05, K-07, K-02, K-15 |
| M1B | 3-4 | April 6, 2026 - May 1, 2026 | 147 | ~443 | Core kernel services |
| M2A | 5-9 | May 4, 2026 - July 10, 2026 | 125 | 372 | Trading and compliance core |
| M2B | 8-10 | June 15, 2026 - July 24, 2026 | 104 | 316 | Post-trade, risk, reporting |
| M3A | 11-12 | July 27, 2026 - August 21, 2026 | 56 | 164 | Data governance, AI, DLQ, deployment |
| M3B | 13-14 | August 24, 2026 - September 18, 2026 | 58 | 154 | Admin portal, SDK, workflows |
| M3C | 15-16 | September 21, 2026 - October 16, 2026 | 56 | 167 | Operator, regulator, certification, manifest |
| M4 | 17-30 | October 19, 2026 - April 30, 2027 | 30 | 94 | Integration, chaos, GA hardening |

M2A and M2B overlap intentionally in Sprints 8-9. `stories/STORY_INDEX.md` is authoritative for that overlap and Sprint 8 is the transition point where M2B work starts while the last M2A stories close.

---

## 5. Sprint-by-Sprint Plan

The "Primary Source Set" column names the minimum controlling bundle for the sprint. If a touched story has a more specific epic section, LLD section, TDD file, or traceability document, add it to the working set rather than relying on the table alone.

| Sprint | Dates | Focus | Primary Source Set | Exit Gate |
| ------ | ----- | ----- | ------------------ | --------- |
| 1 | March 9 - March 20, 2026 | K-05 core bus/event store plus K-07 bootstrap audit path | `stories/MILESTONE_1A_STORIES.md`, `epics/EPIC-K-05-Event-Bus.md`, `epics/EPIC-K-07-Audit-Framework.md`, `lld/LLD_K05_EVENT_BUS.md`, `lld/LLD_K07_AUDIT_FRAMEWORK.md`, `tdd_spec_k05_event_bus_expanded_v2.1.md`, `tdd_spec_k07_audit_framework_expanded_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1 | K-05 append/publish path works and K-07 synchronous audit bootstrap is live |
| 2 | March 23 - April 3, 2026 | Close K-05/K-07 bootstrap cycle, deliver K-02 hierarchy and K-15 calendar core | `stories/MILESTONE_1A_STORIES.md`, `epics/EPIC-K-02-Configuration-Engine.md`, `epics/EPIC-K-15-Dual-Calendar-Service.md`, `lld/LLD_K02_CONFIGURATION_ENGINE.md`, `lld/LLD_K15_DUAL_CALENDAR.md`, `tdd_spec_k02_configuration_engine_expanded_v2.1.md`, `tdd_spec_k15_dual_calendar_v2.1.md` | K-05 to K-07 audit hook enabled, config hierarchy works, calendar correctness suite passes |
| 3 | April 6 - April 17, 2026 | Sprint 3 first slices: K-01 and K-14 plus initial K-06, K-11, K-16, K-17, and K-18 work | `stories/MILESTONE_1B_STORIES.md`, `epics/EPIC-K-01-IAM.md`, `epics/EPIC-K-14-Secrets-Management.md`, `epics/EPIC-K-06-Observability.md`, `epics/EPIC-K-11-API-Gateway.md`, `epics/EPIC-K-16-Ledger-Framework.md`, `epics/EPIC-K-17-Distributed-Transaction-Coordinator.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `epics/DEPENDENCY_MATRIX.md`, `../finance-ghatana-integration-plan.md` §5.1 | Early auth, secrets, observability, gateway, ledger, DTC, and resilience slices are stable enough for Sprint 4 completion work |
| 4 | April 20 - May 1, 2026 | Sprint 4 completion: finish M1B, complete remaining K-01/K-14 work, and add K-03/K-04 without duplicating earlier kernel work | `stories/MILESTONE_1B_STORIES.md`, `epics/EPIC-K-01-IAM.md`, `epics/EPIC-K-14-Secrets-Management.md`, `epics/EPIC-K-03-Rules-Engine.md`, `epics/EPIC-K-04-Plugin-Runtime.md`, `epics/EPIC-K-06-Observability.md`, `epics/EPIC-K-11-API-Gateway.md`, `epics/EPIC-K-16-Ledger-Framework.md`, `epics/EPIC-K-17-Distributed-Transaction-Coordinator.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `lld/LLD_K01_IAM.md`, `lld/LLD_K14_SECRETS_MANAGEMENT.md`, `lld/LLD_K03_RULES_ENGINE.md`, `lld/LLD_K04_PLUGIN_RUNTIME.md`, `lld/LLD_K06_OBSERVABILITY.md`, `lld/LLD_K11_API_GATEWAY.md`, `lld/LLD_K16_LEDGER_FRAMEWORK.md`, `lld/LLD_K17_DISTRIBUTED_TRANSACTION_COORDINATOR.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.1 | Core kernel readiness gates met for domain-pack consumption |
| 5 | May 4 - May 15, 2026 | D-11 reference data, D-04 market data, D-01 OMS tracer bullet | `stories/MILESTONE_2A_STORIES.md`, `epics/EPIC-D-11-Reference-Data.md`, `epics/EPIC-D-04-Market-Data.md`, `epics/EPIC-D-01-OMS.md`, `lld/LLD_D11_REFERENCE_DATA.md`, `lld/LLD_D04_MARKET_DATA.md`, `lld/LLD_D01_OMS.md`, `tdd_spec_d01_oms_expanded_v2.1.md`, `tdd_spec_phase3_trading_mvp_v2.1.md` | Reference data, market data, and OMS first order flow integrate through K-05/K-03/K-16 |
| 6 | May 18 - May 29, 2026 | D-07 compliance, D-14 sanctions, D-06 risk, D-01 continuation | `stories/MILESTONE_2A_STORIES.md`, `epics/EPIC-D-07-Compliance.md`, `epics/EPIC-D-14-Sanctions-Screening.md`, `epics/EPIC-D-06-Risk-Engine.md`, `epics/EPIC-D-01-OMS.md`, `lld/LLD_D07_COMPLIANCE_ENGINE.md`, `lld/LLD_D14_SANCTIONS_SCREENING.md`, `lld/LLD_D06_RISK_ENGINE.md`, `lld/LLD_D01_OMS.md`, `tdd_spec_d01_oms_expanded_v2.1.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md` | Pre-trade compliance and sanctions controls are enforceable in OMS/risk path |
| 7 | June 1 - June 12, 2026 | D-02 EMS | `stories/MILESTONE_2A_STORIES.md`, `epics/EPIC-D-02-EMS.md`, `lld/LLD_D02_EMS.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `../finance-ghatana-integration-plan.md` §5.2 | EMS order-routing path works against approved OMS and market-data contracts |
| 8 | June 15 - June 26, 2026 | D-02 EMS finish plus D-09 post-trade start | `stories/MILESTONE_2A_STORIES.md`, `stories/MILESTONE_2B_STORIES.md`, `epics/EPIC-D-02-EMS.md`, `epics/EPIC-D-09-Post-Trade.md`, `lld/LLD_D02_EMS.md`, `lld/LLD_D09_POST_TRADE.md`, `tdd_spec_phase3_trading_mvp_v2.1.md` | Execution-to-post-trade handoff is stable and audit-complete |
| 9 | June 29 - July 10, 2026 | D-13 reconciliation, D-03 PMS, D-05 pricing | `stories/MILESTONE_2B_STORIES.md`, `epics/EPIC-D-13-Client-Money-Reconciliation.md`, `epics/EPIC-D-03-PMS.md`, `epics/EPIC-D-05-Pricing-Engine.md`, `lld/LLD_D13_CLIENT_MONEY_RECONCILIATION.md`, `lld/LLD_D03_PMS.md`, `lld/LLD_D05_PRICING_ENGINE.md`, `tdd_spec_phase3_trading_mvp_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Pricing, portfolio, and reconciliation flows consume settled domain events correctly |
| 10 | July 13 - July 24, 2026 | D-08 surveillance, D-10 reporting, D-12 corporate actions | `stories/MILESTONE_2B_STORIES.md`, `epics/EPIC-D-08-Surveillance.md`, `epics/EPIC-D-10-Regulatory-Reporting.md`, `epics/EPIC-D-12-Corporate-Actions.md`, `lld/LLD_D08_SURVEILLANCE.md`, `lld/LLD_D10_REGULATORY_REPORTING.md`, `lld/LLD_D12_CORPORATE_ACTIONS.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md` | Domain pack reaches complete reference-business coverage for trading through reporting |
| 11 | July 27 - August 7, 2026 | Sprint 11 first slices across K-08, K-09, K-19, and K-10 | `stories/MILESTONE_3A_STORIES.md`, `epics/EPIC-K-08-Data-Governance.md`, `epics/EPIC-K-09-AI-Governance.md`, `epics/EPIC-K-19-DLQ-Management.md`, `epics/EPIC-K-10-Deployment-Abstraction.md`, `lld/LLD_K08_DATA_GOVERNANCE.md`, `lld/LLD_K09_AI_GOVERNANCE.md`, `lld/LLD_K19_DLQ_MANAGEMENT.md`, `lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md` | Governance, DLQ, and deployment first slices are stable and dependency-safe for Sprint 12 completion |
| 12 | August 10 - August 21, 2026 | Sprint 12 completion across K-08, K-09, K-19, and K-10 | `stories/MILESTONE_3A_STORIES.md`, `epics/EPIC-K-08-Data-Governance.md`, `epics/EPIC-K-09-AI-Governance.md`, `epics/EPIC-K-19-DLQ-Management.md`, `epics/EPIC-K-10-Deployment-Abstraction.md`, `lld/LLD_K08_DATA_GOVERNANCE.md`, `lld/LLD_K09_AI_GOVERNANCE.md`, `lld/LLD_K19_DLQ_MANAGEMENT.md`, `lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md`, `tdd_spec_phase2_kernel_completion_v2.1.md` | Replay, rollback, governance, and deployment guardrails are production-ready |
| 13 | August 24 - September 4, 2026 | Sprint 13 first slices across K-13, K-12, W-01, and W-02 | `stories/MILESTONE_3B_STORIES.md`, `epics/EPIC-K-13-Admin-Portal.md`, `epics/EPIC-K-12-Platform-SDK.md`, `epics/EPIC-W-01-Workflow-Orchestration.md`, `epics/EPIC-W-02-Client-Onboarding.md`, `lld/LLD_K13_ADMIN_PORTAL.md`, `lld/LLD_K12_PLATFORM_SDK.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` (W-01 and W-02 currently rely on epic sections plus milestone stories because no dedicated active LLD exists) | Admin, SDK, workflow, and onboarding first slices are integrated without duplicating control-plane behavior |
| 14 | September 7 - September 18, 2026 | Sprint 14 completion across K-13, K-12, W-01, and W-02 | `stories/MILESTONE_3B_STORIES.md`, `epics/EPIC-K-13-Admin-Portal.md`, `epics/EPIC-K-12-Platform-SDK.md`, `epics/EPIC-W-01-Workflow-Orchestration.md`, `epics/EPIC-W-02-Client-Onboarding.md`, `lld/LLD_K13_ADMIN_PORTAL.md`, `lld/LLD_K12_PLATFORM_SDK.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` (W-01 and W-02 currently rely on epic sections plus milestone stories because no dedicated active LLD exists) | End-to-end onboarding and workflow orchestration work through workflow, compliance, and audit layers |
| 15 | September 21 - October 2, 2026 | Sprint 15 first slices for O-01, P-01, R-01, R-02, and PU-004 | `stories/MILESTONE_3C_STORIES.md`, `epics/EPIC-O-01-Operator-Workflows.md`, `epics/EPIC-P-01-Pack-Certification.md`, `epics/EPIC-R-01-Regulator-Portal.md`, `epics/EPIC-R-02-Incident-Notification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `lld/LLD_P01_PACK_CERTIFICATION.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` (O-01, R-01, R-02, and PU-004 currently rely on epic sections plus milestone stories because no dedicated active LLD exists) | Operator, regulator, certification, and manifest foundations are integrated |
| 16 | October 5 - October 16, 2026 | Sprint 16 completion across O-01, P-01, R-01, R-02, and PU-004 | `stories/MILESTONE_3C_STORIES.md`, `epics/EPIC-O-01-Operator-Workflows.md`, `epics/EPIC-P-01-Pack-Certification.md`, `epics/EPIC-R-01-Regulator-Portal.md`, `epics/EPIC-R-02-Incident-Notification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `lld/LLD_P01_PACK_CERTIFICATION.md`, `tdd_spec_phase2_kernel_completion_v2.1.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` (O-01, R-01, R-02, and PU-004 currently rely on epic sections plus milestone stories because no dedicated active LLD exists) | Pack certification and platform-manifest flows are stable enough for M4 validation |
| 17 | October 19 - October 30, 2026 | T-01 end-to-end order-to-settlement and compliance path | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, referenced domain/kernel stories and LLDs for the exercised flow | Golden-path E2E suite passes against the full reference pack |
| 18 | November 2 - November 13, 2026 | T-01 plugin and platform-upgrade integration scenarios | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-P-01-Pack-Certification.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Upgrade, certification, and rollback scenarios are reproducible |
| 19 | November 16 - November 27, 2026 | T-01 performance baseline for OMS, K-05, K-11 | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, performance NFRs from exercised epics and LLDs | Core latency/throughput benchmarks meet committed targets or raise tracked gaps |
| 20 | November 30 - December 11, 2026 | T-01 database performance and service-contract validation | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, exercised LLD contracts and SDK contracts | Contract, schema, and persistence-layer compatibility is locked |
| 21 | December 14 - December 25, 2026 | T-01 plugin contracts plus T-02 pod/network chaos start | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-01-Integration-Testing.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Plugin isolation and first chaos scenarios pass with documented failure behavior |
| 22 | December 28, 2026 - January 8, 2027 | T-02 resource exhaustion, dependency failure, latency injection | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `epics/EPIC-K-06-Observability.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `lld/LLD_K06_OBSERVABILITY.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Resilience patterns respond as specified and are observable end-to-end |
| 23 | January 11 - January 22, 2027 | T-02 poison-pill handling, DR drill, backup/restore | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `epics/EPIC-K-19-DLQ-Management.md`, `epics/EPIC-K-10-Deployment-Abstraction.md`, `lld/LLD_K19_DLQ_MANAGEMENT.md`, `lld/LLD_K10_DEPLOYMENT_ABSTRACTION.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Recovery and replay drills succeed with audit evidence |
| 24 | January 25 - February 5, 2027 | T-02 resilience scorecard and continuous-chaos operating model | `stories/MILESTONE_4_STORIES.md`, `epics/EPIC-T-02-Chaos-Engineering.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md`, runbooks and operations docs | Continuous resilience program is defined and repeatable |
| 25 | February 8 - February 19, 2027 | GA security validation and platform hardening start | `stories/MILESTONE_4_STORIES.md` (Feature GA-F01), `adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md`, `epics/EPIC-K-01-IAM.md`, `epics/EPIC-K-11-API-Gateway.md`, `epics/EPIC-K-14-Secrets-Management.md`, `epics/EPIC-K-18-Resilience-Patterns.md`, `lld/LLD_K01_IAM.md`, `lld/LLD_K11_API_GATEWAY.md`, `lld/LLD_K14_SECRETS_MANAGEMENT.md`, `lld/LLD_K18_RESILIENCE_PATTERNS.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Security blockers and high-risk findings are closed or formally waived |
| 26 | February 22 - March 5, 2027 | GA SLA validation, runbook library, operational sign-off prep | `stories/MILESTONE_4_STORIES.md` (Feature GA-F02), `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-O-01-Operator-Workflows.md`, `epics/EPIC-R-01-Regulator-Portal.md`, `epics/EPIC-R-02-Incident-Notification.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Runbooks, SLAs, and operating evidence are ready for release review |
| 27 | March 8 - March 19, 2027 | GA remediation buffer sprint | carryover from Sprints 17-26 only; no speculative net-new scope | All remaining P0/P1 defects have owners and closure dates |
| 28 | March 22 - April 2, 2027 | GA readiness checklist sprint | `stories/MILESTONE_4_STORIES.md` (Feature GA-F03, STORY-GA-005), `Authoritative_Source_Register.md`, `Legal_Claim_Citation_Appendix.md`, `Claim_Traceability_Matrix.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Readiness checklist is complete and evidence-backed |
| 29 | April 5 - April 16, 2027 | Final hardening and migration rehearsal | `stories/MILESTONE_4_STORIES.md`, `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-P-01-Pack-Certification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `lld/LLD_P01_PACK_CERTIFICATION.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | Cutover rehearsal succeeds end-to-end |
| 30 | April 19 - April 30, 2027 | Launch and hypercare setup | `stories/MILESTONE_4_STORIES.md` (Feature GA-F03, STORY-GA-006), `DOMAIN_PACK_UPGRADE_RUNBOOK.md`, `epics/EPIC-R-02-Incident-Notification.md`, `epics/EPIC-PU-004-Platform-Manifest.md`, `tdd_spec_phase4_5_operational_hardening_v2.1.md` | GA release decision can be made with full implementation evidence |

---

## 6. Definition of Ready for Every Sprint Story

Before a story enters active development, the team must be able to answer all of the following with exact repo references:

- What story ID and milestone file authorize the work?
- Which epic sections and, where an active LLD exists, which LLD sections govern the behavior?
- Which TDD spec, phase-level test spec, or integration test cases prove completion?
- Which Ghatana component is reused, extended, or intentionally not reused?
- Which event, API, schema, config, workflow, audit, and observability contracts change?
- Which source-trace docs change if wording, market facts, or regulatory claims are touched?

If any answer is missing, the story is not ready.

---

## 7. Definition of Done for Every Sprint

- Story acceptance criteria met in the milestone story file
- Relevant epic contracts implemented or updated, and relevant LLD contracts updated where an active LLD exists
- Tests updated and passing at the correct layer
- Reuse decision documented when Ghatana components were in scope
- Documentation and cross-links updated in the same change
- No broken links introduced in active docs
- Open risks and deferred work explicitly captured, not implied

---

## 8. Escalation Rules

1. If a sprint plan conflicts with `ADR-011`, stop and resolve at the architecture layer first.
2. If story counts or sprint assignments conflict with a narrative plan, use `stories/STORY_INDEX.md`.
3. If a supporting design doc still says "kernel first, domain later," follow this plan and refresh the supporting doc.
4. If a work item depends on a file or module that does not exist in the repo, create a tracked prerequisite instead of pretending it exists.
