# PHR Documentation — Refinement Backlog And Next Steps

**Version:** 2.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** PHR Technical Lead  
**Classification:** Internal

This document records the next refinement pass needed after the current doc reorganization and formalization work.

Archive note:

- this document is retained for historical planning context
- active scope, MVP decisions, and governance interpretation now live in the active docs tree, especially the release definitions, traceability matrix, retention and deletion policy, route contract pack, and QA plans
- statements below that describe missing artifacts or undecided MVP scope should be read as pre-resolution backlog context unless explicitly updated in Section 1.1 or later annotations

---

## 1. Review summary

The review summary below captures the state before the execution-preparation wave and later MVP harmonization decisions were fully propagated.

The active PHR docs are now logically organized and materially stronger than the earlier flat set. The structure is good enough to support implementation planning, but a few areas still need tightening before development starts at full speed.

Current strengths:

- Core MVP and Phase 2 are formally separated with clear change control
- runtime, workflow, schema, and test layers now exist
- requirements are traceable to at least an initial set of APIs, screens, and tests
- global best practices (India ABDM, Estonia X-Road, Australia MHR, UK NHS) are now referenced
- security exit criteria are hardened with OWASP, SAST/DAST, pen-test requirements
- risk registers added to both release definitions
- Nepal-specific innovations documented, including the FCHV bridge that was later confirmed as an MVP pilot alongside export baseline and degraded offline read behavior

Main remaining weaknesses:

- some traceability is still representative rather than exhaustive
- some architecture/docs still reference files by bare filename instead of folder path
- test packs are designed, but not yet mapped to actual automation locations
- schema delta is implementation-ready at table level, but not yet at final field/constraint level for every model
- **critical:** consent enforcement implementation is scattered across docs with no unified ConsentService interface
- **critical:** multi-tenancy enforcement strategy is mentioned but not operationalized (no RLS or application-level filter spec)
- **critical:** secrets management strategy is undefined (no vault/KMS playbook)
- OCR/ASR confidence thresholds and escalation rules are not quantified
- CI/CD pipeline specification is absent
- infrastructure cost model and sizing estimates are missing
- data classification framework not applied to all tables

---

## 1.1 Implementation update — 2026-03-17

The execution-preparation document wave described in this backlog has now been created.

Artifacts added in this pass:

- implementation delivery plans for backend, frontend, and QA
- implementation status board
- Core MVP Prisma draft overlay
- Core MVP OpenAPI DTO drafts and OpenAPI backlog tracker
- testcase-to-suite automation mapping and fixture plan
- ConsentService interface spec
- multi-tenancy enforcement spec
- secrets management playbook
- data classification matrix, retention and deletion policy, and DPIA template
- incident response, audit preparation, CI/CD, DR, monitoring, sizing, and capacity docs
- global data flow and module interconnection diagrams

Subsequent harmonization after this document's original backlog pass also confirmed:

- FCHV as an MVP pilot scope rather than a deferred-only concept
- patient export as a concrete MVP portability surface
- degraded read-only offline behavior for approved MVP patient surfaces, with scoped offline queueing for FCHV workflows
- policy-driven retention, deletion, anonymization, and legal-hold handling rather than fixed simplistic purge assumptions

As of this update, the remaining work is no longer missing-document creation. The remaining work is:

- architecture and security approval of the new blocking specs
- promotion of DTO drafts into shared schemas and generated OpenAPI artifacts
- creation of the planned code and test directories referenced by the delivery plans
- execution of the staging, security, and recovery gates defined by the new runbooks

---

## 2. Refinement backlog

### 2.1 Governance (priority: high)

- ✅ add document owners and approval status to all top-level source-of-truth docs
- ✅ add `last reviewed` date and `next review due` fields to release-definition docs
- ✅ define change-control rule for moving a Phase 2 item into Core MVP
- add RACI matrix for each document (Responsible, Accountable, Consulted, Informed)
- create formal document review cadence (monthly for active docs, quarterly for reference docs)
- add regulatory change monitoring process (Directive 2081 amendments, IT Bill progress, HIB policy updates)

### 2.2 Requirements (priority: high)

- complete the traceability matrix for all `REQ-*` items that remain in active scope
- mark every requirement as `implemented`, `designed`, `deferred`, or `reference-only`
- split non-functional requirements into explicit acceptance criteria by subsystem
- add requirement status tracking columns (implementation status, test status, compliance status)
- cross-reference requirements to global standards (WHO SMART Guidelines, HL7 FHIR R4, WCAG 2.2 AA)
- add data classification tags to requirements involving PII/PHI

### 2.3 Architecture (priority: critical)

- turn schema delta tables into concrete Prisma draft models
- define exact outbox/event table strategy rather than leaving it conceptual
- add auth/session runtime diagrams for mobile token refresh and logout
- add deployment diagrams for API, worker, Postgres, Ceph, auth, and openIMIS connectivity
- **define ConsentService interface** (checkAccess method signature, middleware injection pattern, error behavior)
- **define multi-tenancy enforcement strategy** (PostgreSQL RLS vs. application-level filtering, with explicit decision rationale)
- **create secrets management playbook** (Keycloak creds, Ceph S3 keys, ASR endpoints, SMS API keys — vault or env-injected config per environment)
- **define data classification framework** (PII, PHI, operational, public categories applied to every table)
- add circuit breaker and bulkhead patterns for external integrations (openIMIS, ASR, SMS)
- define cache layer strategy (what to cache, TTL, tenant-scoped invalidation)
- add rate limiting specification per endpoint category (public, authenticated, admin)
- define encryption key rotation strategy and schedule
- add global data flow diagram (registration → encounter → observation → timeline composition → consent checks)
- define module interconnection DAG (dependency graph for all 19 NestJS modules)

### 2.4 Design and workflows (priority: medium)

- expand workflow packs to include field-level forms and validation tables
- add a dedicated dashboard workflow pack
- add a dedicated provider patient-summary workflow pack
- add export workflow pack coverage for the committed MVP portability routes; keep import workflow planning optional or Phase 2
- enrich telemedicine pack with DTOs, media-state diagrams, and retention rules
- add error-handling sequences for each workflow (not just happy paths)
- add bulk operation workflows (import 1000 historical records, batch OCR)
- add conflict resolution patterns for concurrent edits (provider A and provider B updating same encounter)
- add time-zone handling rules (Nepal is UTC+5:45; standardize storage and display)
- add mobile-specific navigation patterns (bottom tabs, stack nav, deep linking)

### 2.5 Testing (priority: high)

- map testcase IDs to planned test files or suite names
- define test data fixtures and seed scenarios
- split smoke vs regression vs compliance suites
- add environment matrix for local, staging, and pre-prod validation
- **add security test suite** (OWASP Top 10 coverage, SQL injection, XSS, CSRF, auth bypass, privilege escalation)
- add performance test specifications (target latencies: API p50/p90/p99, concurrent user targets)
- add cross-tenant isolation test cases (user A cannot access tenant B data)
- add token expiry and refresh rotation test cases
- add data sanitization and input validation test cases
- define test pyramid ratios (unit 60% : integration 25% : E2E 15%)
- add browser/OS compatibility matrix (Chrome 90+, Firefox 90+, Safari 15+, Edge 90+; iOS 15+, Android 10+)
- add load testing plan (target: 10K concurrent users, 100 req/sec sustained)

### 2.6 Security and compliance (priority: critical — NEW)

- create Data Protection Impact Assessment (DPIA) template per Privacy Act 2075
- define policy-driven retention, deletion, anonymization, and legal-hold strategy for audit and patient-linked data
- create incident response playbook (detection → containment → eradication → recovery → lessons learned)
- define breach notification templates (72-hour MoHP notification, patient notification)
- add SAST tool selection and CI integration plan (SonarQube, Semgrep, or equivalent)
- add DAST tool selection and scheduled scan plan (OWASP ZAP or equivalent)
- add dependency vulnerability scanning (OWASP Dependency-Check, Trivy for container images)
- define session management hardening rules (token lifetime, concurrent session limits, forced logout)
- create third-party security audit preparation checklist
- document anti-malware scanning for uploaded documents (Ceph content validation)

### 2.7 Operations and infrastructure (priority: high — NEW)

- create infrastructure sizing and cost model (compute, storage, bandwidth per 10K/50K/100K users)
- define CI/CD pipeline specification (build → lint → test → security scan → staging deploy → approval gate → production)
- create disaster recovery plan (RPO < 24h, RTO < 4h, tested quarterly)
- define backup strategy (daily full, hourly incremental, encrypted, verified restore monthly)
- add runbook templates (incident response, database recovery, Ceph failover, Keycloak restart)
- define monitoring and alerting thresholds (API error rate > 1%, latency p99 > 2s, disk > 80%, etc.)
- create capacity planning model (growth projections: 10K Year 1, 50K Year 2, 100K Year 3)
- add deployment rollback procedure (blue-green or canary strategy selection)

---

## 3. Recommended next implementation-doc steps

### Step 1

Create implementation-owned companion docs:

- `phr_backend_delivery_plan.md` ✅
- `phr_frontend_delivery_plan.md` ✅
- `phr_qa_delivery_plan.md` ✅

Each should consume the active source-of-truth docs instead of redefining scope.

### Step 2

Generate concrete Prisma draft files from:

- `03_architecture/phr_core_schema_delta_spec.md`
- `03_architecture/phr_fhir_complete_starter_schema.prisma`

Status:

- `03_architecture/phr_core_mvp_draft_schema.prisma` ✅

### Step 3

Create endpoint-by-endpoint OpenAPI-first DTO drafts for every Core MVP route in:

- `04_design_and_workflows/phr_mvp_route_contract_pack.md`

Status:

- `04_design_and_workflows/phr_core_openapi_dto_drafts.md` ✅
- `04_design_and_workflows/phr_core_openapi_backlog.md` ✅

### Step 4

Create execution-ready test plans by mapping:

- `05_testing/phr_api_testcases.md`
- `05_testing/phr_service_integration_testcases.md`
- `05_testing/phr_ui_e2e_testcases.md`
- `05_testing/phr_nonfunctional_and_compliance_testcases.md`

to actual codebase suites and ownership.

Status:

- `05_testing/phr_test_automation_mapping.md` ✅
- `05_testing/phr_seed_data_and_test_fixture_plan.md` ✅
- `01_governance/phr_qa_delivery_plan.md` ✅

### Step 5

Add an `implementation status board` doc that shows, for each Core MVP capability:

- doc ready
- schema ready
- API ready
- UI ready
- tests ready
- blocked by dependency

Status:

- `01_governance/phr_implementation_status_board.md` ✅

---

## 4. Historical planned docs to create

All documents listed below were planned in this backlog and were later created. This section is retained to show the original refinement intent.

### Implementation delivery

- `phr_backend_delivery_plan.md` ✅
- `phr_frontend_delivery_plan.md` ✅
- `phr_qa_delivery_plan.md` ✅
- `phr_implementation_status_board.md` ✅
- `phr_core_openapi_backlog.md` ✅
- `phr_seed_data_and_test_fixture_plan.md` ✅

### Security and compliance (NEW)

- `phr_data_protection_impact_assessment.md` (DPIA per Privacy Act 2075) ✅
- `phr_data_classification_matrix.md` (PII/PHI/operational/public per table/column) ✅
- `phr_retention_and_deletion_policy.md` (policy-driven retention, deletion, anonymization, legal hold) ✅
- `phr_incident_response_playbook.md` (detection through lessons learned) ✅
- `phr_security_audit_preparation_checklist.md` (pre-audit readiness) ✅
- `phr_secrets_management_playbook.md` (vault/env config per environment) ✅

### Operations (NEW)

- `phr_infrastructure_sizing_and_cost_model.md` ✅
- `phr_ci_cd_pipeline_specification.md` ✅
- `phr_disaster_recovery_plan.md` ✅
- `phr_monitoring_and_alerting_runbook.md` ✅
- `phr_capacity_planning_model.md` ✅

### Architecture (NEW)

- `phr_consent_service_interface_spec.md` (ConsentService::checkAccess contract) ✅
- `phr_multi_tenancy_enforcement_spec.md` (RLS or application-level filtering decision) ✅
- `phr_global_data_flow_diagram.md` (end-to-end data pipeline visualization) ✅
- `phr_module_interconnection_graph.md` (dependency DAG for all NestJS modules) ✅

---

## 5. Historical exit condition for the documentation phase

Most artifact-creation items below were satisfied by the execution-preparation wave. Remaining gaps are primarily approval, implementation, and operational rollout tasks tracked in active governance documents.

This documentation phase is complete when:

- all Core MVP requirements have full traceability
- all Core MVP routes have DTO-level drafts
- all required Core MVP schema deltas have concrete model proposals
- all testcase IDs map to actual planned automation or manual suites
- **ConsentService interface is defined and agreed upon** (blocks all module development)
- **multi-tenancy enforcement strategy is chosen and documented** (blocks schema implementation)
- **data classification is applied to all tables** (blocks security review)
- **DPIA is completed** (blocks public release)
- **CI/CD pipeline is operational** (blocks continuous delivery)
- **secrets management is configured** (blocks staging/production deployment)

Until then, treat the current set as **implementation planning complete, execution preparation still in progress**. For current status, prefer the active implementation status board and active governance docs.

---

## 6. Prioritized execution order

| Priority | Item                                  | Blocks                 | Owner             |
| -------- | ------------------------------------- | ---------------------- | ----------------- |
| P0       | ConsentService interface spec         | All module development | Architecture Lead |
| P0       | Multi-tenancy enforcement decision    | Schema implementation  | Architecture Lead |
| P0       | Secrets management playbook           | Staging deployment     | DevOps Lead       |
| P1       | Data classification matrix            | Security review        | Security Lead     |
| P1       | CI/CD pipeline specification          | Continuous delivery    | DevOps Lead       |
| P1       | DPIA template                         | Public release         | Compliance Lead   |
| P1       | Security test suite definition        | QA execution           | QA Lead           |
| P2       | Prisma draft models from schema delta | Backend development    | Backend Lead      |
| P2       | OpenAPI DTO drafts                    | Frontend development   | API Lead          |
| P2       | Test data fixtures and seed scenarios | Test automation        | QA Lead           |
| P2       | Infrastructure sizing and cost model  | Budget approval        | DevOps Lead       |
| P3       | Monitoring and alerting runbook       | Production readiness   | DevOps Lead       |
| P3       | Disaster recovery plan                | Production readiness   | DevOps Lead       |
| P3       | Incident response playbook            | Production readiness   | Security Lead     |
