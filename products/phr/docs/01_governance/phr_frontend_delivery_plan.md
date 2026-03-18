# PHR Platform — Frontend Delivery Plan

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** Frontend Lead  
**Approval status:** Draft for delivery review  
**Classification:** Internal

| Field | Value |
| --- | --- |
| Primary consumers | Frontend, design system, QA, product |
| Source-of-truth inputs | [Frontend route map](../04_design_and_workflows/phr_frontend_route_and_component_map.md), [Screen matrix](../04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md), [Route contract pack](../04_design_and_workflows/phr_mvp_route_contract_pack.md), [Traceability matrix](../01_governance/phr_requirements_traceability_matrix.md), [Design system spec](../04_design_and_workflows/ghatana_design_system_complete_spec.md) |
| Companion execution docs | [Core OpenAPI DTO drafts](../04_design_and_workflows/phr_core_openapi_dto_drafts.md), [Core OpenAPI backlog](../04_design_and_workflows/phr_core_openapi_backlog.md), [QA delivery plan](phr_qa_delivery_plan.md) |

This document translates the active PHR documentation into an execution plan for web, mobile, and desktop-delivered frontend surfaces.

---

## 1. Delivery intent

Frontend delivery is organized to keep the information architecture stable while the backend surface matures. The sequence is:

1. ship the shared shell and route guards
2. build patient and provider core screens around frozen DTO contracts
3. add offline-aware and accessibility-complete behavior to MVP surfaces
4. ship caregiver, payment, referral, imaging, FCHV, and emergency QR experiences without splitting the design system

---

## 2. Delivery principles

- Reuse `@ghatana/design-system`, `@ghatana/charts`, `@ghatana/i18n`, and `@ghatana/platform-shell` before creating local components.
- Local healthcare-specific components stay under the PHR product until reuse justifies promotion.
- Every screen ships with loading, empty, error, unauthorized, and offline states.
- Nepali and English localization are part of MVP acceptance, not a later enhancement.
- UI state uses Jotai; server state uses TanStack Query; route composition follows the existing route map.

---

## 3. Planned frontend workspace shape

```text
products/phr/
  apps/
    web/src/
      app/
      routes/
      features/
      shared/
      test/
    mobile/src/
      navigation/
      screens/
      features/
      test/
    desktop/src/
      shell/
      routes/
  packages/
    schemas/
    ui-healthcare/
    feature-flags/
```

Planned shared frontend locations:

- `packages/schemas`: shared Zod contracts and inferred TypeScript types
- `packages/ui-healthcare`: PHR-local components such as `PatientSummaryCard`, `EncounterTimeline`, `MedicationTable`
- `apps/web/src/routes`: web route groups from the route map
- `apps/mobile/src/screens`: mobile parity screens and FCHV flows

---

## 4. Frontend execution waves

### 4.1 Wave 0 — shell, auth, and localization foundation

| Workstream | Deliverables |
| --- | --- |
| App shell | shared layout, auth bootstrap, tenant context, navigation slots |
| Route guards | authenticated, role-based, patient-scope, emergency-route guards |
| Localization | Nepali and English dictionaries, Devanagari numeral/date formatting hooks |
| Accessibility baseline | focus handling, landmarks, live regions, high-contrast tokens |
| Query foundation | TanStack Query client, error boundary policy, retry defaults |

### 4.2 Wave 1 — patient and provider Core MVP surfaces

| Priority | Surface | Dependencies |
| --- | --- | --- |
| P0 | sign in and session restore | auth DTOs |
| P0 | patient registration and profile | patient DTOs, shared form schemas |
| P0 | patient timeline and observations | timeline, observation DTOs |
| P0 | provider patient summary and encounter detail | patient, encounter DTOs |
| P0 | consent and access grants | grant DTOs, audit messaging |
| P1 | appointments and reminders | appointment DTOs |
| P1 | documents and OCR review | document, OCR DTOs |
| P1 | insurance summary and eligibility | insurance DTOs |
| P1 | emergency QR screen | emergency summary DTO |
| P1 | provider voice dictation and patient voice input | ASR DTOs and review queue states |

### 4.3 Wave 2 — role-specific enhancements still within MVP

| Surface | Notes |
| --- | --- |
| FCHV dashboard and registration | simplified touch-first UI, scoped offline queue indicator for field capture |
| caregiver portal baseline | delegated dependent list and dependent summary with explicit grant-state messaging |
| offline patient behaviors | cached reads, queued writes, sync-status visibility, and conflict review for approved MVP actions |
| payments, referrals, and imaging | patient-facing financial, care-coordination, and study-viewer routes under the shared shell |
| export and portability | patient-managed export request and artifact download states |
| accessibility hardening | WCAG manual audit fixes and screen-reader verification |
| desktop shell polish | provider-heavy density and keyboard-first workflows |

---

## 5. Route-group delivery plan

| Route group | Main screens | Primary API dependencies | Delivery notes |
| --- | --- | --- | --- |
| `/sign-in` | sign in, MFA, session restore | `/auth/login`, `/auth/me` | must land first for all surfaces |
| `/app/patient` | dashboard, profile, timeline, observations, medications | patient, timeline, observation, medication routes | patient-facing MVP spine |
| `/app/patient/documents` | list, upload, OCR review | document and OCR routes | include hash/upload status feedback |
| `/app/patient/appointments` | list and booking | appointment routes | support retry-safe create flow |
| `/app/patient/access` | grants list, create, revoke | access grant routes | must show expiry and revocation state clearly |
| `/app/patient/export` | export request, status, artifact download | patient export routes | portability evidence and artifact expiry messaging required |
| `/app/patient/emergency-qr` | card display, print/export | emergency QR route | privacy-safe by design |
| `/app/patient/payments` | bills, payment initiation, receipt view | billing and payment routes | explicit pending, success, failure, and retry states required |
| `/app/patient/referrals` | referral list and detail | referral routes | receiving-facility and status history must be legible on mobile |
| `/app/patient/imaging/:id` | study viewer and report | imaging routes | performance and secure-download states required |
| `/app/caregiver` | dependents list and delegated summary | family routes | grant-scope disclosure must remain visible on every dependent view |
| `/app/fchv` | dashboard, register patient, sync state | FCHV patient routes | scoped offline capture with clear sync state expectations |
| `/app/provider` | dashboard, patient list, patient summary, encounter, meds | patient, encounter, medication, observation routes | keyboard-first and dense information layouts |
| `/app/admin/audit` | audit search and export | audit routes | tenant-safe filtering and export gating |

---

## 6. Component delivery strategy

### 6.1 Reuse-first component map

| Component need | Source |
| --- | --- |
| buttons, form controls, drawers, tables, alerts | `@ghatana/design-system` |
| chart primitives | `@ghatana/charts` |
| auth and shell scaffolding | `@ghatana/platform-shell`, `@ghatana/sso-client` |
| i18n and locale switching | `@ghatana/i18n` |
| audio and dictation UI | `@ghatana/audio-video-ui` |

### 6.2 PHR-local components to build

| Component | Initial owner | Used by |
| --- | --- | --- |
| `PatientSummaryCard` | Frontend Lead | patient dashboard, provider summary |
| `EncounterTimeline` | Frontend Lead | timeline and summary views |
| `VitalsTrendChart` | Frontend Lead | observations screen |
| `MedicationTable` | Frontend Lead | patient/provider medication views |
| `EmergencyQrCard` | Frontend Lead | emergency QR route, print flow |
| `ConsentGrantTable` | Frontend Lead | patient access management |
| `OfflineSyncBanner` | Frontend Lead | patient mobile and FCHV flows |
| `DependentSummaryCard` | Frontend Lead | caregiver portal |
| `PaymentStatusCard` | Frontend Lead | payments route |
| `ReferralTimeline` | Frontend Lead | referrals route |
| `ImagingViewerShell` | Frontend Lead | imaging study route |

---

## 7. Frontend dependency gates

No screen moves from design to build until all of the following exist:

- linked requirement IDs
- route contract and DTO draft
- screen states defined
- test IDs mapped in QA docs
- data classification known for displayed fields
- accessibility expectations documented

---

## 8. Release sequencing by capability

| Capability | Frontend status target | Blocking dependency |
| --- | --- | --- |
| Auth shell | ready first | identity DTO freeze |
| Registration and profile | sprint 1 | patient create and update DTOs |
| Timeline and observations | sprint 2 | timeline read model and trend DTOs |
| Consent management | sprint 2 | ConsentService runtime contract |
| Documents and OCR review | sprint 3 | document upload/OCR job contracts |
| Insurance eligibility | sprint 3 | openIMIS adapter error semantics |
| Emergency QR | sprint 3 | privacy-safe summary projection |
| Export and portability | sprint 3 | export route and artifact expiry contract |
| Caregiver portal | sprint 3 | delegated summary contracts and grant projection |
| Payments, referrals, and imaging | sprint 4 | billing, referral, and imaging DTO freezes |
| FCHV flow and generalized offline sync | sprint 4 | offline storage, queue, and conflict contract |

---

## 9. Frontend release exit criteria

- every MVP screen implemented in web and mapped to mobile or desktop parity
- all screens have empty, loading, error, and forbidden states
- Nepali locale verified for critical patient flows
- axe-core and manual WCAG checks pass on shipped MVP routes
- UI E2E scenarios have planned automation locations and owners
- no screen depends on undocumented backend behavior

This plan is complete when every Core MVP surface has a route owner, DTO dependency, component strategy, and QA gate.