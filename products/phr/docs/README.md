# PHR Documentation Set

**Version:** 2.0  
**Updated:** 2026-01-19

| Field                | Value                                                         |
| -------------------- | ------------------------------------------------------------- |
| Product              | Ghatana PHR — Personal Health Record Platform                 |
| Target Market        | Nepal (primary), South Asia (expansion)                       |
| Regulatory Framework | Nepal Directive 2081, Privacy Act 2075, HIB/openIMIS, FHIR R4 |
| Classification       | C2 — Internal                                                 |

> 📌 **What changed in v2.0:** All documents enhanced with global PHR analysis (ABDM, X-Road, MHR, NHS, OpenMRS, WHO SMART), security hardening (OWASP Top 10, DPIA, SAST/DAST), Nepal-specific innovations (FCHV, Emergency QR, Nepali ASR, NRN health corridor, openIMIS), data classification scheme (C1–C4), multi-tenancy architecture, consent model enhancements, comprehensive test suites, and cross-document traceability.

This folder contains the active planning, requirements, architecture, design, workflow, and testing documents for the PHR product.

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

Release boundaries, traceability, refinement backlog, and cross-requirement traceability matrix.

### `02_strategy_and_requirements/`

Strategic framing, global PHR comparative analysis, requirement inventory, feature list, and market positioning.

### `03_architecture/`

Activation plan, runtime architecture (multi-tenancy, caching, resilience, deployment), schema specifications, migration plan, NestJS module architecture (ConsentService, secrets management), sequence diagrams (including error paths and emergency flows), and error/idempotency models.

### `04_design_and_workflows/`

API contracts (including Emergency QR, rate limiting, circuit breaker, tenant isolation, security error guidelines), screen matrices (including FCHV and Emergency QR screens), frontend design (including FCHV routes, offline indicators, accessibility), workflow packs (registration, consent, timeline, medications, appointments, documents, insurance, telemedicine — all with v2.0 enhancements), and Ghatana design system (including Nepali formatting, WCAG checklist, offline components).

### `05_testing/`

Detailed test case packs by layer: API tests (OWASP, rate limiting, circuit breaker, tenant isolation), service/integration tests (consent cache, document integrity, FCHV flows), UI E2E tests (offline, Nepali locale, WCAG audit, browser matrix), and non-functional/compliance tests (OWASP Top 10 suite, DPIA, pen test, load/stress, breach notification).

### `archive/`

Historical or superseded documents.

## Cross-Cutting Concerns (v2.0)

These concerns span all documents and must be maintained consistently:

| Concern                 | Description                                                                                    | Key Documents                                            |
| ----------------------- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| **Data Classification** | C1 (Public), C2 (Internal), C3 (Confidential), C4 (Restricted) with encryption/retention rules | Requirements traceability, Activation plan, Schema delta |
| **Multi-Tenancy**       | Application-layer + RLS defense-in-depth, X-Tenant-Id header, AsyncLocalStorage context        | Runtime architecture, NestJS modules, Route contracts    |
| **ConsentService**      | Mandatory consent check before any data access, cache invalidation, break-the-glass            | NestJS modules, Consent workflow, Sequence diagrams      |
| **Emergency QR**        | Auto-generated health card (blood type, allergies, meds, contacts), privacy-safe               | Registration workflow, Frontend routes, API contracts    |
| **FCHV Integration**    | Icon-based mobile UI for community health volunteers, offline-capable                          | Registration workflow, Screen matrix, Frontend routes    |
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
6. [03_architecture/phr_runtime_architecture.md](03_architecture/phr_runtime_architecture.md)
   Runtime service boundaries, multi-tenancy, caching, resilience, and deployment.
7. [03_architecture/phr_core_sequence_diagrams.md](03_architecture/phr_core_sequence_diagrams.md)
   Baseline and error-path end-to-end runtime sequences.
8. [03_architecture/phr_error_model_and_idempotency_spec.md](03_architecture/phr_error_model_and_idempotency_spec.md)
   Cross-platform error, security-sensitive error handling, and retry/idempotency policy.
9. [03_architecture/phr_core_schema_delta_spec.md](03_architecture/phr_core_schema_delta_spec.md)
   Implementation-ready schema gap specification.
10. [04_design_and_workflows/phr_mvp_route_contract_pack.md](04_design_and_workflows/phr_mvp_route_contract_pack.md)
    API contracts including Emergency QR, rate limiting, circuit breaker, and tenant headers.
11. [04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md](04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md)
    Screen/module/API/QA matrix including FCHV and Emergency QR screens.
12. [04_design_and_workflows/phr_frontend_route_and_component_map.md](04_design_and_workflows/phr_frontend_route_and_component_map.md)
    Frontend information architecture including FCHV routes, offline indicators, and error boundaries.
13. Workflow packs in `04_design_and_workflows/`
14. Test packs in `05_testing/`
15. [03_architecture/phr_nestjs_modules_detailed_architecture.md](03_architecture/phr_nestjs_modules_detailed_architecture.md)
    Backend module reference including ConsentService, multi-tenancy, and secrets management.
16. [03_architecture/phr_migration_plan_and_module_ownership_matrix.md](03_architecture/phr_migration_plan_and_module_ownership_matrix.md)
    Data ownership and migration sequencing reference.
17. [04_design_and_workflows/ghatana_design_system_complete_spec.md](04_design_and_workflows/ghatana_design_system_complete_spec.md)
    Shared UI, accessibility, Nepali formatting, and WCAG compliance baseline.
18. [04_design_and_workflows/phr_telemedicine_contract_and_workflow_pack.md](04_design_and_workflows/phr_telemedicine_contract_and_workflow_pack.md)
    Phase 2 telemedicine design pack with 2G fallback, FCHV mediation, and E2E encryption.
19. [02_strategy_and_requirements/phr-consolidated-report-v2.md](02_strategy_and_requirements/phr-consolidated-report-v2.md)
    Strategic, market, regulatory, global PHR analysis, and Nepal innovation planning.
20. [02_strategy_and_requirements/phr-feature-list.md](02_strategy_and_requirements/phr-feature-list.md)
    Compact feature inventory including Emergency QR, FCHV, and Nepal innovation features.

## Document Version Summary

| #   | Document                         | Version | Last Updated |
| --- | -------------------------------- | ------- | ------------ |
| 1   | Core MVP Release Definition      | v2.0    | 2026-01-19   |
| 2   | Phase 2 Release Definition       | v2.0    | 2026-01-19   |
| 3   | Refinement Backlog               | v2.0    | 2026-01-19   |
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
| 16  | All 4 Testing docs               | v2.0    | 2026-01-19   |

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

The current set is now logically grouped, version-tracked, and cross-referenced.
The main remaining refinement work is tracked in:

- [01_governance/phr_refinement_backlog_and_next_steps.md](01_governance/phr_refinement_backlog_and_next_steps.md)

## Archived documents

Documents that were useful during feasibility or early ideation, but are no longer authoritative, are moved under `archive/`.

When an archived document conflicts with an active document, the active document wins.
