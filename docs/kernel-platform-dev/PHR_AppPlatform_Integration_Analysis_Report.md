# PHR-AppPlatform Integration Analysis Report

**Date**: March 19, 2026  
**Version**: 2.0  
**Status**: Reviewed and Expanded  
**Classification**: Internal - Restricted

---

## Executive Summary

This document re-reviews the PHR to AppPlatform integration strategy against the **actual repository state** in `products/app-platform` and `products/phr`.

The previous version was directionally useful, but it overstated platform readiness in a few places and underused the already-rich PHR requirement/specification set. The most important corrections are:

- **PHR is no longer well-described as a NestJS migration target only.**
  `products/phr/README.md` now states the planned backend as **Java 21 + ActiveJ**, even though several detailed architecture docs still describe a modular NestJS + Prisma design. That means the current PHR document set should be treated as a **requirements and contract source**, not as a finalized implementation stack.
- **AppPlatform is more multi-domain in intent than in current implementation.**
  Its ADRs repeatedly say the kernel should be generic, but several current kernel modules still embed capital-markets concepts directly.
- **A healthcare domain pack does not yet exist in the repo.**
  `products/app-platform/domain-packs/` contains finance/compliance packs only. Healthcare exists as a documented target, not as an implemented pack.
- **The right path is not “migrate PHR into AppPlatform as-is.”**
  The right path is:
  1. **make the AppPlatform kernel truly generic**
  2. **build a healthcare/PHR domain pack and product slices on top of it**
  3. **tailor PHR requirements/specs where they are overly NestJS-specific, while preserving their business, API, data, and compliance intent**

### Updated Recommendation

Proceed, but with a stricter sequence:

1. **Kernel cleanup first** for modules that currently leak finance/jurisdiction-specific behavior.
2. **Create a healthcare pack baseline** before implementing PHR modules on the platform.
3. **Use `products/phr` as the source of truth** for MVP scope, route contracts, data model, consent, tenancy, compliance, testing, and rollout.
4. **Translate stack-specific PHR docs into platform-aligned implementation docs** instead of discarding them.

---

## 1. Review Scope

This review was grounded in:

- `products/app-platform/kernel/**`
- `products/app-platform/domain-packs/**`
- `products/app-platform/docs/adr/**`
- `products/phr/docs/**`
- `products/phr/src/**`

---

## 2. Repo-Grounded Findings

### 2.1 AppPlatform intent is generic, but implementation still leaks domain specifics

AppPlatform documentation clearly states the kernel should remain domain-agnostic:

- `products/app-platform/docs/adr/ADR-003_PLUGIN_ARCHITECTURE.md`
- `products/app-platform/docs/adr/ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md`

However, several current kernel modules embed capital-markets workflows directly:

- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/TradeSettlementWorkflowService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/CorporateActionWorkflowService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/RegulatoryReportSubmissionWorkflowService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/JurisdictionRegistryService.java`

These are not generic workflow/runtime primitives. They are domain workflows or highly domain-biased operator models and should not remain in the generic kernel in their current form.

### 2.2 The “19 kernel modules” framing is no longer a clean reflection of the repo

The repo currently has **25 top-level kernel directories** under `products/app-platform/kernel/`, including:

- `workflow-orchestration`
- `operator-workflows`
- `client-onboarding`
- `regulator-portal`
- `incident-management`
- `integration-testing`

That means the current report should not present AppPlatform as a neatly finished “K-01 to K-19” implementation without qualification. The architecture may still use that conceptual model, but the repo has grown beyond it.

### 2.3 Healthcare domain-pack support is planned, not implemented

Current implemented domain packs are:

- `compliance`
- `corporate-actions`
- `ems`
- `market-data`
- `oms`
- `pms`
- `post-trade`
- `pricing`
- `reconciliation`
- `reference-data`
- `regulatory-reporting`
- `risk-engine`
- `sanctions`
- `surveillance`

There is **no** `products/app-platform/domain-packs/healthcare/` yet.

There are docs referencing healthcare as a target domain and even calling out a future healthcare pack/template:

- `products/app-platform/docs/GENERIC_PLATFORM_EXPANSION_ANALYSIS.md`
- `products/app-platform/docs/DOMAIN_PACK_CUSTOMIZATION_GUIDE.md`

So the correct statement is:

> AppPlatform is designed to support healthcare, but the healthcare pack is still to be created.

### 2.4 PHR is already heavily specified

`products/phr` is not an empty concept. It already contains:

- MVP scope and release definition
- end-to-end requirements
- traceability matrix
- runtime architecture
- route contracts
- module ownership and schema deltas
- multi-tenancy spec
- consent control-point spec
- testing plans

Most important source documents:

- `products/phr/docs/02_strategy_and_requirements/phr-e2e-requirements.md`
- `products/phr/docs/01_governance/phr_core_mvp_release_definition.md`
- `products/phr/docs/01_governance/phr_requirements_traceability_matrix.md`
- `products/phr/docs/03_architecture/phr_runtime_architecture.md`
- `products/phr/docs/03_architecture/phr_multi_tenancy_enforcement_spec.md`
- `products/phr/docs/03_architecture/phr_consent_service_interface_spec.md`
- `products/phr/docs/03_architecture/phr_core_schema_delta_spec.md`
- `products/phr/docs/04_design_and_workflows/phr_mvp_route_contract_pack.md`

This is enough to build a concrete implementation program now.

### 2.5 PHR stack assumptions are internally mixed and must be normalized

There are two different PHR “implementation stories” in the repo:

1. **Current product README direction**
   - `products/phr/README.md`
   - planned backend: **Java 21 + ActiveJ**

2. **Older detailed architecture set**
   - modular NestJS
   - Prisma
   - NestJS module ownership and route specs

This is not a blocker, but it changes the job:

- The PHR docs should be split into:
  - **business/contract truths to preserve**
  - **stack-specific implementation assumptions to translate**

### 2.6 Current PHR code is skeletal and should be treated as exploratory

There is some Java code in `products/phr/src/**`, including:

- `PhrKernelModule`
- `FhirInteropKernelPlugin`
- `PHRProductPlugin`
- service and extension stubs

This is useful for direction, but it is not yet the implementation backbone for the documented PHR MVP. The docs remain the stronger source of truth.

---

## 3. What Can Be Reused from AppPlatform Right Now

The following platform areas are good reuse candidates with low conceptual risk:

| Area | Reuse value for PHR | Notes |
| --- | --- | --- |
| IAM | High | roles, auth context, tenant-aware actor resolution |
| Config engine | High | tenant config, feature flags, policy packs |
| Rules engine | High | validation, policy rules, eligibility policies, reminder logic |
| Plugin runtime | High | healthcare adapters, OCR/ASR adapters, FHIR import/export plugins |
| Event store | Medium-High | audit/event propagation, export jobs, document workflow events |
| Audit trail | High | consent, patient-data access, security evidence |
| Data governance | High | classification, retention, policy metadata |
| AI governance | High | OCR, voice transcription, human-in-the-loop review, model approval |
| API gateway | High | FHIR and product API ingress, auth, rate limiting, observability |
| Secrets management | High | PHI-related secrets, integration credentials, signing keys |
| Resilience patterns | High | openIMIS, SMS, email, payment, OCR/ASR, external provider integrations |
| DLQ management | Medium-High | failed eligibility checks, failed exports, failed notification deliveries |
| Observability SDK | High | compliance and runtime visibility |
| Calendar service | Medium | appointment scheduling and Nepal calendar support, if generalized properly |

The following areas are reusable **only after cleanup or narrowing**:

| Area | Current issue | Required change |
| --- | --- | --- |
| Workflow orchestration | contains trade/CA/regulatory workflows in kernel | extract generic engine; move workflows to domain packs |
| Operator workflows | jurisdiction model contains settlement/doc assumptions | generalize or re-scope to shared tenant/locale/compliance primitives |
| Ledger framework | naming and docs skew finance-first | keep generic engine, rename/document as multi-domain balance/ledger primitive |
| Regulator portal / client onboarding | conceptually useful but currently finance-oriented in naming/scope | isolate domain-specific behavior into packs or rename as generic portals/workflows |

---

## 4. What Must Not Stay in the Generic AppPlatform Kernel

This is the first expansion requested by the review.

### 4.1 Kernel boundary rule

A module may stay in the kernel only if all of the following are true:

1. it solves a cross-domain technical concern
2. its core vocabulary is domain-neutral
3. it exposes extension points instead of embedding a vertical workflow
4. a healthcare, insurance, banking, or capital-markets pack could all use it without semantic distortion

If not, it belongs in:

- a domain pack
- a content pack
- a product module on top of the kernel

### 4.2 Concrete de-domainization candidates

#### A. `workflow-orchestration`

Current problem:

- contains prebuilt domain flows such as trade settlement, corporate actions, and regulatory submission

Target state:

- kernel keeps only:
  - workflow definition model
  - step execution primitives
  - wait/correlation
  - retries and compensation hooks
  - workflow versioning
  - metrics and testing harness
- finance-specific flows move to:
  - `domain-packs/post-trade`
  - `domain-packs/corporate-actions`
  - `domain-packs/regulatory-reporting`

PHR implication:

- PHR can then add healthcare workflows such as document OCR review, eligibility checks, reminder scheduling, export approval, referral coordination, and break-glass approval on top of a neutral engine.

#### B. `operator-workflows`

Current problem:

- `JurisdictionRegistryService` combines legitimate cross-domain locale/compliance concerns with finance-specific settlement-cycle and document semantics

Target state:

- split into:
  - generic `TenantPolicyRegistry` or `TenantLocaleRegistry`
  - optional domain-specific jurisdiction/compliance adapters in domain packs

PHR implication:

- PHR needs tenant policy, facility, locale, data-residency, and policy metadata
- it does **not** need trade settlement cycle concepts in the kernel

#### C. `ledger-framework`

Current problem:

- cross-domain capability exists, but naming and surrounding examples still read finance-first

Target state:

- retain engine, but document as a generic immutable ledger/balance primitive supporting:
  - finance postings
  - healthcare billing
  - claim settlement traces
  - account balances
  - subscription or wallet balances

#### D. Kernel docs and examples

Current problem:

- many ADRs/examples are generic in theory but capital-markets in examples, metrics, and narratives

Target state:

- every kernel doc should separate:
  - generic kernel contract
  - example domain instantiations

### 4.3 Exit criteria for “kernel is generic enough for PHR”

PHR should not commit to deep platform reuse until these are true:

1. no finance-specific workflow classes remain inside generic kernel modules
2. no kernel APIs require trade/order/settlement/regulator concepts in their contract
3. operator metadata models are valid for healthcare tenants without semantic hacks
4. one healthcare-oriented example exists for each reused kernel area
5. platform docs explicitly distinguish kernel primitives from domain-pack implementations

---

## 5. Concrete Plan to Implement PHR from `products/phr`

This is the second expansion requested by the review.

The implementation plan below uses `products/phr` requirements/specs as the source of truth and tailors them for AppPlatform where needed.

### 5.1 Source-of-truth hierarchy for PHR implementation

For PHR-on-AppPlatform execution, use this order:

1. `products/phr/docs/01_governance/phr_core_mvp_release_definition.md`
2. `products/phr/docs/02_strategy_and_requirements/phr-e2e-requirements.md`
3. `products/phr/docs/01_governance/phr_requirements_traceability_matrix.md`
4. `products/phr/docs/04_design_and_workflows/phr_mvp_route_contract_pack.md`
5. `products/phr/docs/03_architecture/*.md`
6. existing `products/phr/src/**` code only where it aligns with the above

### 5.2 Translation rule for old PHR architecture docs

Keep the following from the older NestJS/Prisma-oriented docs:

- module boundaries
- route contracts
- ownership rules
- data model intent
- consent behavior
- tenancy rules
- error model
- testing obligations

Translate, do not preserve literally:

- NestJS-specific DI patterns
- Prisma-specific repository assumptions
- module naming that implies a single-process monolith when the platform requires service separation

### 5.3 PHR platform architecture target

Recommended target:

- **Kernel and shared platform concerns**: AppPlatform generic kernel
- **Healthcare shared domain layer**: new `domain-packs/healthcare`
- **PHR product modules**: `products/phr` product services and plugins
- **Contracts and policy**: derived from existing PHR docs

Recommended split:

| Layer | Responsibility |
| --- | --- |
| AppPlatform kernel | IAM, config, rules, plugin runtime, workflow engine, audit, data governance, AI governance, gateway, secrets, resilience, observability |
| Healthcare domain pack | FHIR adapters, consent policy templates, patient record services, healthcare workflows, openIMIS integration, OCR/ASR healthcare tuning |
| PHR product | patient app, provider app, caregiver flows, FCHV flows, activation flags, Nepal-specific rollout packaging |

### 5.3A Deployment model requirement

The plan should explicitly support **both** of these deployment modes:

1. **Single-domain deployment**
   - one kernel deployment
   - one active domain pack family
   - one product surface for that domain
   - example: PHR only

2. **Multi-domain shared-kernel deployment**
   - one kernel deployment
   - multiple active domain packs on the same platform installation
   - one or more products exposed together
   - example: PHR + insurance + banking products sharing the same kernel estate

This is consistent with the current platform intent:

- AppPlatform ADRs describe a multi-domain platform with domain-pack isolation and independent service deployment.
- `products/app-platform/docs/DOMAIN_PACK_INTERFACE_SPECIFICATION.md` defines packs as separate units integrating with the kernel.
- `products/app-platform/docs/DOMAIN_PACK_UPGRADE_RUNBOOK.md` explicitly supports **per-tenant blue-green domain pack activation**, which strongly implies packs are independently activatable.
- `products/phr/docs/04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md` says service-specific route groups should not imply separate deployables, which means PHR contracts must survive either topology.

### 5.3B Required deployment invariants

To make both deployment modes safe, the platform plan must preserve these invariants:

1. **Kernel is deployable without any one specific domain pack**
2. **Each domain pack is installable, activatable, upgraded, and rolled back independently**
3. **A domain pack cannot require another domain pack through hidden code coupling**
4. **Cross-domain collaboration must happen through explicit APIs/events/contracts, not shared internal tables**
5. **Tenant activation must be able to enable one pack, many packs, or no optional packs**
6. **Route grouping must not be used as evidence of process boundaries**
7. **Data isolation must be enforceable whether packs run in one deployment unit or many**

### 5.3C Recommended topology rules

Use these rules in the implementation plan:

#### Rule 1: Packaging boundary != deployment boundary

A domain pack should be a packaging and ownership boundary first.
It may be deployed:

- as its own service set
- or together with other packs in the same runtime estate

provided the runtime contract boundaries remain intact.

#### Rule 2: Keep runtime contracts stable across both topologies

Whether domains are deployed separately or together, they should interact through the same:

- REST/gRPC contracts
- events
- workflow triggers
- configuration and feature-flag interfaces

Do not allow “same deployment” to justify direct package-private coupling or cross-domain repository writes.

#### Rule 3: Support a modular-monolith-first product surface where helpful

For early PHR delivery, some product-facing APIs may reasonably ship in a shared deployable while still preserving internal ownership boundaries.
This is already consistent with the PHR docs that use service-specific route groups without requiring separate deployables.

That means:

- **separate deployability** must remain possible
- **separate deployment** is not mandatory on day one for every bounded context

#### Rule 4: Promote independent activation over forced co-deployment

Even in a shared-kernel installation, a tenant should be able to:

- run only healthcare/PHR
- run healthcare + insurance
- run healthcare + insurance + another domain

without rebuilding the kernel for each combination.

#### Rule 5: Cross-domain shared concerns belong in kernel; cross-domain business logic does not

Shared concerns that justify kernel placement:

- IAM
- tenancy
- audit
- policy/config
- workflow primitives
- observability
- secrets
- resilience

Business logic that should stay out of kernel even if multiple domains use it:

- claims workflows
- healthcare consent semantics
- trade settlement
- regulatory filing flows
- product-specific document review logic

### 5.3D Concrete deployment recommendation for PHR

Recommended rollout path:

1. **Phase 1**
   - deploy PHR as the only active domain on a generic kernel
   - keep healthcare pack and PHR product boundaries explicit
   - allow some product APIs to share a deployable for speed

2. **Phase 2**
   - validate that healthcare pack can be activated independently per tenant
   - validate blue-green pack upgrade and rollback
   - validate no hidden dependency on finance-oriented packs

3. **Phase 3**
   - support optional co-deployment with other domains on the same kernel estate
   - only through explicit contracts and tenant-scoped activation

### 5.3E Additional exit criteria

The platform plan is not deployment-ready until these are proven:

1. PHR can run with only kernel + healthcare pack + PHR product enabled
2. kernel startup does not require finance domain packs
3. healthcare pack can be upgraded or rolled back without redeploying unrelated domain packs
4. at least one integration test proves multi-pack coexistence on one kernel deployment
5. at least one integration test proves healthcare-only deployment on the same kernel codebase
6. tenant activation matrices are documented for:
   - healthcare only
   - healthcare + insurance
   - multi-domain shared-kernel deployment

### 5.4 PHR MVP modules to implement first

The repo already makes the MVP explicit. Implement in this order:

#### Wave 0: architecture alignment and platform fit

- normalize the PHR stack decision
- convert stack-specific docs into platform-aligned implementation docs
- create the healthcare domain-pack skeleton
- define kernel-vs-healthcare-pack-vs-product ownership

Primary inputs:

- `products/phr/README.md`
- `products/phr/docs/03_architecture/phr_runtime_architecture.md`
- `products/phr/docs/03_architecture/phr_migration_plan_and_module_ownership_matrix.md`

Deliverables:

- PHR-on-AppPlatform ADR
- healthcare pack directory structure
- ownership matrix rewritten for platform alignment

#### Wave 1: platform control points needed before feature work

- tenant enforcement
- consent control point
- audit classification and policy mapping
- actor/profile and patient identity baseline

Primary inputs:

- `phr_multi_tenancy_enforcement_spec.md`
- `phr_consent_service_interface_spec.md`
- `phr_requirements_traceability_matrix.md`
- `phr_core_schema_delta_spec.md`

Deliverables:

- `ConsentService` implementation contract on platform
- tenant context propagation + PostgreSQL RLS rollout plan
- PHI/PII data classification map in data governance
- actor profile and patient root schema/service baseline

#### Wave 2: core MVP patient record backbone

- `PatientModule`
- `EncounterModule`
- `ObservationModule`
- `ConditionModule`
- `MedicationModule`
- timeline/read aggregation

Primary inputs:

- `phr_core_mvp_release_definition.md`
- `phr-e2e-requirements.md`
- `phr_core_schema_delta_spec.md`
- `phr_mvp_route_contract_pack.md`

Platform tailoring:

- keep FHIR-shaped data and route contracts
- implement service boundaries on platform primitives instead of NestJS module assumptions

#### Wave 3: documents, OCR, voice, and review workflow

- `DocumentModule`
- `DataInputModule`
- object storage metadata
- OCR and transcription pipelines
- human review queues

Primary inputs:

- `phr_core_mvp_release_definition.md`
- `phr_core_schema_delta_spec.md`
- `phr_mvp_route_contract_pack.md`

Platform tailoring:

- use AI governance for model approval, inference routing, audit, retention, and HITL
- use plugin runtime for OCR/ASR providers and parsers

#### Wave 4: appointments, reminders, insurance eligibility

- `AppointmentModule`
- reminder planning
- `InsuranceModule`
- openIMIS eligibility adapter

Primary inputs:

- `phr_workflow_appointments.md`
- `phr_workflow_insurance_baseline.md`
- `phr_requirements_traceability_matrix.md`

Platform tailoring:

- use calendar service only if its abstractions are healthcare-safe after review
- use resilience + DLQ for openIMIS and reminder-provider integrations

#### Wave 5: consent sharing, export, caregiver, FCHV

- access grants
- export jobs
- caregiver/dependent flows
- FCHV-assisted registration

Primary inputs:

- `phr_workflow_consent.md`
- `phr_workflow_caregiver_dependents.md`
- `phr_mvp_route_contract_pack.md`
- `phr_test_automation_mapping.md`

Platform tailoring:

- export flow should use audit + workflow + policy checks
- caregiver and FCHV flows must inherit the same tenant and consent control points

#### Wave 6: billing, referrals, imaging

- `BillingModule`
- `ReferralModule`
- `ImagingModule`

Primary inputs:

- `phr_workflow_payments.md`
- `phr_workflow_referrals.md`
- `phr_workflow_imaging.md`
- `phr_core_mvp_release_definition.md`

Platform tailoring:

- billing may reuse generalized ledger/balance primitives after kernel cleanup
- imaging should stay healthcare-pack/product-level, not in kernel

### 5.5 PHR Phase 2 modules

Keep these explicitly out of Core MVP implementation except for interface preparation:

- telemedicine
- insurance claim submission/tracking
- advanced family hub analytics

That matches the current release-governance docs and should not be silently pulled forward.

---

## 6. Required Tailoring of PHR Specs for AppPlatform

The specs in `products/phr` are strong, but some need targeted tailoring.

### 6.1 Replace “NestJS module” language with platform service ownership

Where docs say “owning NestJS module,” reinterpret as:

- owning service or bounded context
- owning healthcare-pack component
- owning product API surface

Do **not** change the ownership intent.

### 6.2 Replace Prisma-specific assumptions with storage contracts

Where docs say Prisma:

- preserve table/model ownership
- preserve schema delta and migration intent
- preserve queryability and audit requirements
- translate repository details into the chosen platform persistence approach

### 6.3 Keep route contracts stable

The route contract pack is valuable and should remain the public and frontend-facing contract baseline even if backend implementation shifts.

### 6.4 Promote consent and tenancy specs into platform-level healthcare controls

The following should become first-class platform-backed healthcare controls, not just product conventions:

- `ConsentService`
- tenant propagation and RLS
- audit-required access decisions
- emergency/break-glass policy handling

### 6.5 Keep testing traceability intact

The requirements matrix and testing docs are one of the strongest assets in `products/phr`.
Do not lose the linkage among:

- requirement id
- route
- schema
- owner
- test ids
- data classification

---

## 7. Delivery Plan

### 7.1 Workstream A: Make AppPlatform kernel generic

**Goal**: remove domain-specific logic from the reusable substrate.

Steps:

1. classify every current kernel module as:
   - generic kernel
   - shared operator/control-plane service
   - domain-pack implementation currently misplaced
2. extract finance workflows from `workflow-orchestration`
3. refactor `operator-workflows` metadata models to domain-neutral primitives
4. rename or re-document finance-biased kernel concepts
5. scrub docs/examples/tests so generic modules no longer speak finance by default

Output:

- kernel boundary matrix
- moved workflow classes
- updated ADR/doc set
- healthcare-safe kernel reuse checklist

### 7.2 Workstream B: Create healthcare pack foundation

**Goal**: establish reusable healthcare domain capabilities before product assembly.

Steps:

1. create `products/app-platform/domain-packs/healthcare/`
2. define healthcare pack submodules for:
   - patient identity/profile
   - consent and access policy
   - clinical record services
   - documents and data input
   - appointments and reminders
   - insurance eligibility
   - interoperability/FHIR
3. move healthcare-cross-cutting adapters here, not into kernel and not all into `products/phr`

Output:

- healthcare pack baseline
- shared healthcare policy/content/config contracts

### 7.3 Workstream C: Implement PHR MVP product

**Goal**: deliver the documented MVP using the healthcare pack plus generic kernel.

Steps:

1. align PHR docs to platform implementation vocabulary
2. implement control points first: auth context, tenant, consent, audit
3. build the clinical backbone
4. add document/OCR/voice workflows
5. add appointment/insurance/giver/export flows
6. finish billing/referral/imaging baseline

Output:

- PHR MVP product slices
- verified requirement-to-test traceability

---

## 8. Suggested Phase Timeline

### Phase 0: 2 weeks

- freeze source-of-truth docs
- issue PHR-on-AppPlatform ADR
- produce kernel boundary matrix
- create healthcare pack skeleton

### Phase 1: 3 to 4 weeks

- extract domain workflows from kernel
- stabilize consent, tenancy, audit, and data-classification control points

### Phase 2: 4 to 6 weeks

- implement patient/clinical/document/data-input MVP backbone

### Phase 3: 3 to 4 weeks

- implement appointment, reminders, insurance eligibility, sharing, export

### Phase 4: 2 to 3 weeks

- implement billing, referrals, imaging baseline
- finalize QA traceability, pilot-readiness checklist, and deployment hardening

This is a more realistic and repo-consistent plan than the earlier generic 24-week migration framing.

---

## 9. Decision Summary

### 9.1 Strategic decision

Proceed with PHR on AppPlatform, but only under a **generic-kernel-first** execution model.

### 9.2 Architecture decision

Do **not** place healthcare-specific workflows or healthcare data semantics into the AppPlatform kernel.

Instead:

- keep kernel generic
- create healthcare shared domain capabilities in a healthcare pack
- implement PHR as a product using those capabilities

### 9.3 Documentation decision

Treat `products/phr` as a mature specification set that needs **translation and alignment**, not replacement.

---

## 10. Immediate Next Steps

1. Create a **kernel/domain ownership matrix** for all current `products/app-platform/kernel/*` modules.
2. Open refactor tasks to move:
   - trade settlement workflow
   - corporate action workflow
   - regulatory report workflow
   out of the generic kernel.
3. Create `products/app-platform/domain-packs/healthcare/` with an initial structure aligned to the current PHR MVP modules.
4. Write a short **PHR-on-AppPlatform ADR** resolving the current Java/ActiveJ versus NestJS/Prisma document mismatch.
5. Convert the following PHR docs from stack-specific to platform-aligned execution docs:
   - runtime architecture
   - module ownership matrix
   - route contract pack
   - schema delta spec
6. Start implementation with:
   - tenant enforcement
   - consent control point
   - audit/data classification
   - patient identity/profile baseline

---

## 11. Final Conclusion

The business case for PHR on AppPlatform is still strong, but the implementation path is more specific than the earlier report suggested.

The platform is **not yet fully generic in practice**, and healthcare reuse is **not yet plug-and-play**. At the same time, the PHR product already has a surprisingly strong requirement and architecture base in `products/phr`, enough to drive a concrete execution plan immediately.

The correct next move is therefore:

> **genericize the kernel, create the healthcare pack, then implement PHR against the already-defined PHR requirements/specs.**

That sequence keeps AppPlatform reusable across domains and gives PHR a credible, implementation-ready path without losing the work already captured in `products/phr`.
