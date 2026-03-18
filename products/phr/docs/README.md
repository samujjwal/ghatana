# PHR Documentation Set

**Version:** 2.1  
**Updated:** 2026-03-17

| Field                | Value                                                         |
| -------------------- | ------------------------------------------------------------- |
| Product              | Ghatana PHR — Personal Health Record Platform                 |
| Target Market        | Nepal (primary), South Asia (expansion)                       |
| Regulatory Framework | Nepal Directive 2081, Privacy Act 2075, HIB/openIMIS, FHIR R4 |
| Classification       | C2 — Internal                                                 |

> 📌 **What changed in v2.0:** All documents enhanced with global PHR analysis (ABDM, X-Road, MHR, NHS, OpenMRS, WHO SMART), security hardening (OWASP Top 10, DPIA, SAST/DAST), Nepal-specific innovations (FCHV, Emergency QR, Nepali ASR, NRN health corridor, openIMIS), data classification scheme (C1–C4), multi-tenancy architecture, consent model enhancements, comprehensive test suites, and cross-document traceability.

This folder contains the active planning, requirements, architecture, design, workflow, and testing documents for the PHR product.

## Reader's Table of Contents

Use this index if you want to read the active PHR documentation from first principles through implementation readiness instead of jumping file to file.

| Step | Read this first | Why it comes here |
| --- | --- | --- |
| 1 | [01_governance/phr_core_mvp_release_definition.md](01_governance/phr_core_mvp_release_definition.md) | establishes the current MVP boundary, committed capabilities, and release gates |
| 2 | [01_governance/phr_phase2_release_definition.md](01_governance/phr_phase2_release_definition.md) | shows what is intentionally deferred so MVP and Phase 2 do not blur together |
| 3 | [02_strategy_and_requirements/phr-e2e-requirements.md](02_strategy_and_requirements/phr-e2e-requirements.md) | defines the end-to-end requirement inventory and phase assignment |
| 4 | [01_governance/phr_requirements_traceability_matrix.md](01_governance/phr_requirements_traceability_matrix.md) | connects requirements to APIs, screens, data, tests, and compliance anchors |
| 5 | [01_governance/phr_retention_and_deletion_policy.md](01_governance/phr_retention_and_deletion_policy.md) | explains how deletion, anonymization, retention, and legal hold are actually interpreted |
| 6 | [03_architecture/phr_mvp_activation_plan.md](03_architecture/phr_mvp_activation_plan.md) | translates scope into the modules and platform capabilities that must be activated |
| 7 | [03_architecture/phr_runtime_architecture.md](03_architecture/phr_runtime_architecture.md) | gives the system shape: services, tenancy, storage, integrations, resilience, and deployment model |
| 8 | [03_architecture/phr_core_sequence_diagrams.md](03_architecture/phr_core_sequence_diagrams.md) | shows the main runtime flows and failure paths after you understand the architecture |
| 9 | [03_architecture/phr_error_model_and_idempotency_spec.md](03_architecture/phr_error_model_and_idempotency_spec.md) | defines cross-cutting runtime behavior for errors, retries, and safe mutations |
| 10 | [03_architecture/phr_consent_service_interface_spec.md](03_architecture/phr_consent_service_interface_spec.md) | formalizes the patient-data access control point used across the platform |
| 11 | [03_architecture/phr_multi_tenancy_enforcement_spec.md](03_architecture/phr_multi_tenancy_enforcement_spec.md) | formalizes tenant isolation and should be read before schema or API implementation |
| 12 | [03_architecture/phr_core_schema_delta_spec.md](03_architecture/phr_core_schema_delta_spec.md) | identifies the operational data model gaps required for MVP |
| 13 | [03_architecture/phr_core_mvp_draft_schema.prisma](03_architecture/phr_core_mvp_draft_schema.prisma) | shows the draft implementation shape of those schema additions |
| 14 | [04_design_and_workflows/phr_mvp_route_contract_pack.md](04_design_and_workflows/phr_mvp_route_contract_pack.md) | freezes the main MVP API routes and request-response behavior |
| 15 | [04_design_and_workflows/phr_core_openapi_dto_drafts.md](04_design_and_workflows/phr_core_openapi_dto_drafts.md) | turns route contracts into named DTOs that backend and frontend can implement against |
| 16 | [04_design_and_workflows/phr_core_openapi_backlog.md](04_design_and_workflows/phr_core_openapi_backlog.md) | shows which contracts are frozen and which still need validation/examples/schema completion |
| 17 | [04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md](04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md) | maps each user-facing screen to modules, endpoints, data, and tests |
| 18 | [04_design_and_workflows/phr_frontend_route_and_component_map.md](04_design_and_workflows/phr_frontend_route_and_component_map.md) | explains how the application is navigated and assembled on the client side |
| 19 | [04_design_and_workflows/README.md](04_design_and_workflows/README.md) | provides the design-folder index before you drop into workflow-specific interaction packs |
| 20 | [04_design_and_workflows/phr_workflow_caregiver_dependents.md](04_design_and_workflows/phr_workflow_caregiver_dependents.md) | defines dependent discovery, delegated summary rules, and caregiver-scoped actions |
| 21 | [04_design_and_workflows/phr_workflow_payments.md](04_design_and_workflows/phr_workflow_payments.md) | defines bill presentation, wallet payment initiation, settlement confirmation, and receipt behavior |
| 22 | [04_design_and_workflows/phr_workflow_referrals.md](04_design_and_workflows/phr_workflow_referrals.md) | defines provider referral authoring, summary attachment rules, and patient tracking behavior |
| 23 | [04_design_and_workflows/phr_workflow_imaging.md](04_design_and_workflows/phr_workflow_imaging.md) | defines imaging metadata access, embedded viewer launch, and secure study download behavior |
| 24 | [01_governance/phr_backend_delivery_plan.md](01_governance/phr_backend_delivery_plan.md) | turns the source-of-truth set into backend execution order |
| 25 | [01_governance/phr_frontend_delivery_plan.md](01_governance/phr_frontend_delivery_plan.md) | turns the same source-of-truth set into frontend execution order |
| 26 | [01_governance/phr_qa_delivery_plan.md](01_governance/phr_qa_delivery_plan.md) | shows how testing, environments, and release gates are meant to operate |
| 27 | [01_governance/phr_implementation_status_board.md](01_governance/phr_implementation_status_board.md) | gives the fastest current-status view across all MVP capabilities |
| 28 | Test packs in [05_testing](05_testing/README.md) | contain the detailed API, service, UI, and NFR cases used to verify the product |
| 29 | [05_testing/phr_test_automation_mapping.md](05_testing/phr_test_automation_mapping.md) | tells you where those tests are intended to live in the codebase |
| 30 | [05_testing/phr_seed_data_and_test_fixture_plan.md](05_testing/phr_seed_data_and_test_fixture_plan.md) | defines the tenants, actors, and scenarios used to make test execution deterministic |

## Recommended Reading Paths

### Path A — Fast product orientation

Read these if you need the shortest path to understanding what the product is, what MVP includes, and what is deferred:

1. [01_governance/phr_core_mvp_release_definition.md](01_governance/phr_core_mvp_release_definition.md)
2. [01_governance/phr_phase2_release_definition.md](01_governance/phr_phase2_release_definition.md)
3. [02_strategy_and_requirements/phr-e2e-requirements.md](02_strategy_and_requirements/phr-e2e-requirements.md)
4. [01_governance/phr_requirements_traceability_matrix.md](01_governance/phr_requirements_traceability_matrix.md)

### Path B — Architecture and platform controls

Read these if you are designing or reviewing backend, platform, data, security, or integration behavior:

1. [03_architecture/phr_mvp_activation_plan.md](03_architecture/phr_mvp_activation_plan.md)
2. [03_architecture/phr_runtime_architecture.md](03_architecture/phr_runtime_architecture.md)
3. [03_architecture/phr_core_sequence_diagrams.md](03_architecture/phr_core_sequence_diagrams.md)
4. [03_architecture/phr_error_model_and_idempotency_spec.md](03_architecture/phr_error_model_and_idempotency_spec.md)
5. [03_architecture/phr_consent_service_interface_spec.md](03_architecture/phr_consent_service_interface_spec.md)
6. [03_architecture/phr_multi_tenancy_enforcement_spec.md](03_architecture/phr_multi_tenancy_enforcement_spec.md)
7. [03_architecture/phr_core_schema_delta_spec.md](03_architecture/phr_core_schema_delta_spec.md)
8. [03_architecture/phr_core_mvp_draft_schema.prisma](03_architecture/phr_core_mvp_draft_schema.prisma)

### Path C — Product design and API delivery

Read these if you are implementing or reviewing contracts, screens, routes, workflows, or UX completeness:

1. [04_design_and_workflows/phr_mvp_route_contract_pack.md](04_design_and_workflows/phr_mvp_route_contract_pack.md)
2. [04_design_and_workflows/phr_core_openapi_dto_drafts.md](04_design_and_workflows/phr_core_openapi_dto_drafts.md)
3. [04_design_and_workflows/phr_core_openapi_backlog.md](04_design_and_workflows/phr_core_openapi_backlog.md)
4. [04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md](04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md)
5. [04_design_and_workflows/phr_frontend_route_and_component_map.md](04_design_and_workflows/phr_frontend_route_and_component_map.md)
6. [04_design_and_workflows/README.md](04_design_and_workflows/README.md)
7. [04_design_and_workflows/phr_workflow_caregiver_dependents.md](04_design_and_workflows/phr_workflow_caregiver_dependents.md)
8. [04_design_and_workflows/phr_workflow_payments.md](04_design_and_workflows/phr_workflow_payments.md)
9. [04_design_and_workflows/phr_workflow_referrals.md](04_design_and_workflows/phr_workflow_referrals.md)
10. [04_design_and_workflows/phr_workflow_imaging.md](04_design_and_workflows/phr_workflow_imaging.md)

### Path D — Delivery, readiness, and verification

Read these if you need the execution plan and the evidence model for build readiness:

1. [01_governance/phr_backend_delivery_plan.md](01_governance/phr_backend_delivery_plan.md)
2. [01_governance/phr_frontend_delivery_plan.md](01_governance/phr_frontend_delivery_plan.md)
3. [01_governance/phr_qa_delivery_plan.md](01_governance/phr_qa_delivery_plan.md)
4. [01_governance/phr_implementation_status_board.md](01_governance/phr_implementation_status_board.md)
5. [05_testing/README.md](05_testing/README.md)
6. [05_testing/phr_test_automation_mapping.md](05_testing/phr_test_automation_mapping.md)
7. [05_testing/phr_seed_data_and_test_fixture_plan.md](05_testing/phr_seed_data_and_test_fixture_plan.md)

### Path E — Compliance, privacy, and operations

Read these if you are reviewing security, regulatory posture, or production-operational readiness:

1. [01_governance/phr_retention_and_deletion_policy.md](01_governance/phr_retention_and_deletion_policy.md)
2. [01_governance/phr_data_protection_impact_assessment.md](01_governance/phr_data_protection_impact_assessment.md)
3. [01_governance/phr_data_classification_matrix.md](01_governance/phr_data_classification_matrix.md)
4. [01_governance/phr_incident_response_playbook.md](01_governance/phr_incident_response_playbook.md)
5. [01_governance/phr_security_audit_preparation_checklist.md](01_governance/phr_security_audit_preparation_checklist.md)
6. [03_architecture/phr_secrets_management_playbook.md](03_architecture/phr_secrets_management_playbook.md)
7. [03_architecture/phr_ci_cd_pipeline_specification.md](03_architecture/phr_ci_cd_pipeline_specification.md)
8. [03_architecture/phr_disaster_recovery_plan.md](03_architecture/phr_disaster_recovery_plan.md)
9. [03_architecture/phr_monitoring_and_alerting_runbook.md](03_architecture/phr_monitoring_and_alerting_runbook.md)
10. [03_architecture/phr_infrastructure_sizing_and_cost_model.md](03_architecture/phr_infrastructure_sizing_and_cost_model.md)
11. [03_architecture/phr_capacity_planning_model.md](03_architecture/phr_capacity_planning_model.md)

## Reading Rules

- If two documents disagree, follow the Source-of-truth hierarchy in this README.
- If you are new to the product, start with the Reader's Table of Contents before using section-specific READMEs.
- If you only need detailed workflow behavior, do not start in `archive/`; finish the active design and testing docs first.
- If you are reviewing implementation readiness, always read the delivery plans together with the test mapping and status board.

## Directory layout

```text
docs/
  README.md
  01_governance/
  02_strategy_and_requirements/
  03_architecture/
  04_design_and_workflows/
  05_testing/
  archive/
```

### `01_governance/`

Release boundaries, traceability, refinement backlog, delivery plans, implementation status tracking, DPIA, retention and deletion policy, data classification, and security governance playbooks.

### `02_strategy_and_requirements/`

Strategic framing, global PHR comparative analysis, requirement inventory, feature list, and market positioning.

### `03_architecture/`

Activation plan, runtime architecture (multi-tenancy, caching, resilience, deployment), schema specifications, Prisma drafts, migration plan, ConsentService and tenancy enforcement specs, secrets management, CI/CD, sizing, disaster recovery, monitoring, module graphs, and global data-flow diagrams.

### `04_design_and_workflows/`

API contracts (including Emergency QR, export portability, caregiver delegation, payments, referrals, imaging, rate limiting, circuit breaker, tenant isolation, security error guidelines), DTO drafts, OpenAPI backlog tracking, screen matrices (including FCHV, export, caregiver, payment, referral, imaging, and Emergency QR screens), frontend design (including FCHV routes, offline read/write indicators, accessibility), dedicated workflow packs, and Ghatana design system.

### `05_testing/`

Detailed test case packs by layer: API tests (OWASP, rate limiting, circuit breaker, tenant isolation), service/integration tests (consent cache, document integrity, FCHV flows), UI E2E tests (offline, Nepali locale, WCAG audit, browser matrix), non-functional/compliance tests, automation mapping, and seed-data and fixture planning.

### `archive/`

Historical or superseded documents.

## Cross-Cutting Concerns (v2.1)

These concerns span all documents and must be maintained consistently:

| Concern                 | Description                                                                                    | Key Documents                                            |
| ----------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| **Data Classification** | C1 (Public), C2 (Internal), C3 (Confidential), C4 (Restricted) with encryption/retention rules | Requirements traceability, Activation plan, Schema delta |
| **Multi-Tenancy**       | Application-layer + RLS defense-in-depth, X-Tenant-Id header, AsyncLocalStorage context        | Runtime architecture, NestJS modules, Route contracts    |
| **ConsentService**      | Mandatory consent check before any data access, cache invalidation, break-the-glass            | NestJS modules, Consent workflow, Sequence diagrams      |
| **Emergency QR**        | Auto-generated health card (blood type, allergies, meds, contacts), privacy-safe               | Registration workflow, Frontend routes, API contracts    |
| **FCHV Integration**    | Icon-based mobile UI for community health volunteers with scoped offline capture                | Registration workflow, Screen matrix, Frontend routes    |
| **Export Portability**  | Patient-managed export jobs, time-limited artifacts, and audit evidence                         | Route contracts, DTO drafts, QA plan                     |
| **Retention Policy**    | Configurable deletion, anonymization, tombstones, and legal hold with legal minimum floors      | Governance policy, DPIA, classification, compliance tests |
| **Execution Readiness** | Delivery plans, status board, DTO drafts, Prisma drafts, and mapped test suites                 | Governance, OpenAPI backlog, Testing mapping             |
| **OWASP Compliance**    | Top 10 security controls, security-sensitive error handling, pen test                          | Error model, API contracts, NFR test cases               |
| **Nepal Compliance**    | Directive 2081 (21 modules), Privacy Act 2075, HIB/openIMIS, data sovereignty                  | Consolidated report, Requirements, Traceability matrix   |
| **Accessibility**       | WCAG 2.2 AA, bilingual (Nepali + English), Bikram Sambat calendar                              | Design system, Frontend routes, UI E2E tests             |

## Source-of-truth hierarchy

Use the documents in this order when resolving conflicts:

1. [01_governance/phr_core_mvp_release_definition.md](01_governance/phr_core_mvp_release_definition.md)
   Formal Core MVP delivery boundary.
2. [01_governance/phr_phase2_release_definition.md](01_governance/phr_phase2_release_definition.md)
   Formal committed Phase 2 delivery boundary.
3. [03_architecture/phr_mvp_activation_plan.md](03_architecture/phr_mvp_activation_plan.md)
   Current resource/module activation plan.
4. [02_strategy_and_requirements/phr-e2e-requirements.md](02_strategy_and_requirements/phr-e2e-requirements.md)
   Requirement inventory and phase assignment.
5. [01_governance/phr_requirements_traceability_matrix.md](01_governance/phr_requirements_traceability_matrix.md)
   Requirement-to-screen/API/data/test linkage.
6. [01_governance/phr_retention_and_deletion_policy.md](01_governance/phr_retention_and_deletion_policy.md)
   Policy source for configurable retention, deletion, anonymization, and legal hold handling.
7. [03_architecture/phr_runtime_architecture.md](03_architecture/phr_runtime_architecture.md)
   Runtime service boundaries, multi-tenancy, caching, resilience, and deployment.
8. [03_architecture/phr_core_sequence_diagrams.md](03_architecture/phr_core_sequence_diagrams.md)
   Baseline and error-path end-to-end runtime sequences.
9. [03_architecture/phr_error_model_and_idempotency_spec.md](03_architecture/phr_error_model_and_idempotency_spec.md)
   Cross-platform error, security-sensitive error handling, and retry/idempotency policy.
10. [03_architecture/phr_core_schema_delta_spec.md](03_architecture/phr_core_schema_delta_spec.md)
   Implementation-ready schema gap specification.
11. [04_design_and_workflows/phr_mvp_route_contract_pack.md](04_design_and_workflows/phr_mvp_route_contract_pack.md)
    API contracts including Emergency QR, export portability, rate limiting, circuit breaker, and tenant headers.
12. [04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md](04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md)
    Screen/module/API/QA matrix including FCHV, export, and Emergency QR screens.
13. [04_design_and_workflows/phr_frontend_route_and_component_map.md](04_design_and_workflows/phr_frontend_route_and_component_map.md)
    Frontend information architecture including FCHV routes, caregiver flows, offline read/write indicators, export flows, and error boundaries.
14. [04_design_and_workflows/phr_workflow_caregiver_dependents.md](04_design_and_workflows/phr_workflow_caregiver_dependents.md)
    Caregiver-dependent discovery, delegated summary assembly, and scoped action rules.
15. [04_design_and_workflows/phr_workflow_payments.md](04_design_and_workflows/phr_workflow_payments.md)
    Billing presentation, payment initiation, confirmation, reconciliation, and receipt behavior.
16. [04_design_and_workflows/phr_workflow_referrals.md](04_design_and_workflows/phr_workflow_referrals.md)
    Referral authoring, summary-sharing controls, status progression, and patient tracking.
17. [04_design_and_workflows/phr_workflow_imaging.md](04_design_and_workflows/phr_workflow_imaging.md)
    Imaging-study access, embedded viewer behavior, download controls, and audit expectations.
18. Test packs in `05_testing/`
19. [03_architecture/phr_consent_service_interface_spec.md](03_architecture/phr_consent_service_interface_spec.md)
    Shared runtime contract for patient-data access checks.
20. [03_architecture/phr_multi_tenancy_enforcement_spec.md](03_architecture/phr_multi_tenancy_enforcement_spec.md)
    Defense-in-depth tenant isolation decision and implementation contract.
21. [03_architecture/phr_core_mvp_draft_schema.prisma](03_architecture/phr_core_mvp_draft_schema.prisma)
    Draft operational Prisma overlay for Core MVP execution.
22. [04_design_and_workflows/phr_core_openapi_dto_drafts.md](04_design_and_workflows/phr_core_openapi_dto_drafts.md)
    Named DTO drafts for all Core MVP routes.
23. [05_testing/phr_test_automation_mapping.md](05_testing/phr_test_automation_mapping.md)
    Testcase-to-suite ownership mapping.
24. [03_architecture/phr_nestjs_modules_detailed_architecture.md](03_architecture/phr_nestjs_modules_detailed_architecture.md)
    Backend module reference including ConsentService, multi-tenancy, and secrets management.
25. [03_architecture/phr_migration_plan_and_module_ownership_matrix.md](03_architecture/phr_migration_plan_and_module_ownership_matrix.md)
    Data ownership and migration sequencing reference.
26. [04_design_and_workflows/ghatana_design_system_complete_spec.md](04_design_and_workflows/ghatana_design_system_complete_spec.md)
    Shared UI, accessibility, Nepali formatting, and WCAG compliance baseline.
27. [04_design_and_workflows/phr_telemedicine_contract_and_workflow_pack.md](04_design_and_workflows/phr_telemedicine_contract_and_workflow_pack.md)
    Phase 2 telemedicine design pack with 2G fallback, FCHV mediation, and E2E encryption.
28. [02_strategy_and_requirements/phr-consolidated-report-v2.md](02_strategy_and_requirements/phr-consolidated-report-v2.md)
    Strategic, market, regulatory, global PHR analysis, and Nepal innovation planning.
29. [02_strategy_and_requirements/phr-feature-list.md](02_strategy_and_requirements/phr-feature-list.md)
    Compact feature inventory including Emergency QR, FCHV, and Nepal innovation features.

## Document Version Summary

| #   | Document                         | Version | Last Updated |
| --- | -------------------------------- | ------- | ------------ |
| 1   | Core MVP Release Definition      | v2.0    | 2026-01-19   |
| 2   | Phase 2 Release Definition       | v2.0    | 2026-01-19   |
| 3   | Refinement Backlog               | v2.0    | 2026-03-17   |
| 4   | Requirements Traceability Matrix | v2.0    | 2026-01-19   |
| 5   | Consolidated Report              | v3.0    | 2026-01-19   |
| 6   | E2E Requirements                 | v2.0    | 2026-01-19   |
| 7   | Feature List                     | v3.0    | 2026-01-19   |
| 8   | Runtime Architecture             | v2.0    | 2026-01-19   |
| 9   | MVP Activation Plan              | v2.0    | 2026-01-19   |
| 10  | Core Sequence Diagrams           | v2.0    | 2026-01-19   |
| 11  | Error Model & Idempotency        | v2.0    | 2026-01-19   |
| 12  | NestJS Modules Architecture      | v2.0    | 2026-01-19   |
| 13  | Schema Delta Spec                | v2.0    | 2026-01-19   |
| 14  | Migration Plan                   | v2.0    | 2026-01-19   |
| 15  | All 12 Design & Workflow docs    | v2.0    | 2026-01-19   |
| 16  | Delivery plans + status board    | v1.0    | 2026-03-17   |
| 17  | Consent + tenancy + Prisma docs  | v1.0    | 2026-03-17   |
| 18  | OpenAPI DTO + backlog docs       | v1.0    | 2026-03-17   |
| 19  | Testing mapping + fixture docs   | v1.0    | 2026-03-17   |
| 20  | Security + operations docs       | v1.0    | 2026-03-17   |
| 21  | Retention and deletion policy    | v1.0    | 2026-03-17   |

## Global PHR Systems Referenced

The v2.0 documentation draws lessons from these proven global systems:

| System                   | Country        | Key Lesson Applied                                     |
| ------------------------ | -------------- | ------------------------------------------------------ |
| **ABDM / ABHA**          | India          | Federated health ID, consent-first architecture        |
| **X-Road**               | Estonia        | "Who viewed my data" transparency, patient audit trail |
| **My Health Record**     | Australia      | Emergency QR, break-the-glass access model             |
| **NHS App**              | UK             | Bilingual interface, proxy access for dependents       |
| **OpenMRS**              | Rwanda/Kenya   | Offline-first design, CHW tablet registration          |
| **openIMIS**             | Tanzania/Nepal | Insurance integration, claim lifecycle                 |
| **WHO SMART Guidelines** | Global         | Decision-support logic, FHIR standard profiles         |
| **eSanjeevani**          | India          | Telemedicine at scale, adaptive bitrate                |

## Refinement review

The current set now includes the first execution-preparation wave requested by the refinement backlog: delivery plans, blocking architecture specs, Prisma drafts, DTO drafts, test mapping, security templates, and operations runbooks.
Any remaining work should now be treated as implementation and approval follow-through rather than missing planning artifacts.

The historical backlog context remains tracked in:

- [archive/phr_refinement_backlog_and_next_steps.md](archive/phr_refinement_backlog_and_next_steps.md)

## Archived documents

Documents that were useful during feasibility or early ideation, but are no longer authoritative, are moved under `archive/`.

When an archived document conflicts with an active document, the active document wins.
