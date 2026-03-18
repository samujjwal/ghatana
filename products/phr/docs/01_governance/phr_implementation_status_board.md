# PHR Platform — Implementation Status Board

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-03-31  
**Document owner:** PHR Technical Lead  
**Approval status:** Active tracking artifact  
**Classification:** Internal

This board shows the execution-preparation state for each Core MVP capability. It reflects documentation readiness and immediate blockers, not code completion.

Status legend:

- `Ready` means the artifact exists and is usable for implementation.
- `In progress` means the artifact exists but still needs refinement.
- `Blocked` means delivery cannot safely begin.

---

## 1. Core MVP capability board

| Capability | Requirement anchors | Doc ready | Schema ready | API ready | UI ready | Tests ready | Blocked by dependency | Owner |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Auth and session | `REQ-ADMIN-001`, `REQ-PATIENT-020` | Ready | Ready | In progress | In progress | In progress | shared DTO freeze | Identity Lead |
| Patient registration and profile | `REQ-PROFILE-001`, `REQ-PROFILE-004` | Ready | In progress | In progress | Ready | In progress | tenant and consent control points | Patient Lead |
| Timeline and summary views | `REQ-PATIENT-001`, `REQ-PROVIDER-001` | Ready | In progress | In progress | Ready | In progress | read-model and aggregation contract | Encounter Lead |
| Clinical write flows | `REQ-PROVIDER-003`, `REQ-PROVIDER-004` | Ready | In progress | In progress | In progress | In progress | consent runtime contract | Clinical Lead |
| Observation trends | `REQ-PATIENT-006` | Ready | In progress | In progress | Ready | In progress | observation index hardening | Observation Lead |
| Appointments and reminders | `REQ-APT-001`, `REQ-PATIENT-011` | Ready | In progress | In progress | In progress | In progress | reminder table and worker contract | Appointment Lead |
| Documents and OCR | `REQ-PATIENT-002`, `REQ-MED-004` | Ready | In progress | In progress | In progress | In progress | storage, quarantine, OCR threshold spec | Document Lead |
| Voice input and transcription | `REQ-PATIENT-010` | Ready | In progress | In progress | In progress | In progress | ASR confidence thresholds and review rules | Data Input Lead |
| Insurance eligibility | `REQ-HIB-002`, `REQ-INS-006` | Ready | Ready | In progress | In progress | In progress | openIMIS adapter and secrets | Insurance Lead |
| Caregiver and dependent portal | `REQ-PATIENT-007`, `REQ-PATIENT-015`, `REQ-FAMILY-007` | Ready | In progress | In progress | In progress | In progress | delegated projection and grant reflection contract | Family Lead |
| Offline sync and conflict handling | `REQ-PATIENT-009` | Ready | In progress | Blocked | In progress | In progress | queue, replay, and conflict-policy contract | Platform Client Lead |
| Payments and receipts | `REQ-INS-024`, `REQ-INT-PAY-001` | Ready | In progress | In progress | In progress | In progress | gateway callback contract and shared DTO freeze | Billing Lead |
| Referrals | `REQ-PROVIDER-008`, `REQ-REF-001` | Ready | In progress | In progress | In progress | In progress | referral status event model and facility routing | Referral Lead |
| Imaging viewer | `REQ-IMG-001` | Ready | In progress | In progress | In progress | In progress | signed viewer/download contract and report linkage | Imaging Lead |
| Export and portability | `REQ-PATIENT-008`, `REQ-EXPORT-001` | Ready | In progress | Blocked | In progress | In progress | export route freeze and shared schema promotion | Interoperability Lead |
| Consent and access grants | `REQ-PATIENT-003`, `REQ-SEC-PRIVACY-002` | Ready | Ready | Blocked | In progress | In progress | ConsentService agreement | Consent Lead |
| Emergency QR | `REQ-PATIENT-016` | Ready | In progress | In progress | In progress | In progress | privacy-safe projection and missing-field rules | Patient Lead |
| Audit and compliance visibility | `REQ-MOHP-005` | Ready | Ready | In progress | In progress | In progress | audit retention and export policy | Audit Lead |
| FCHV assisted flows | `REQ-FCHV-001`, `REQ-FCHV-002`, `REQ-FCHV-003` | Ready | In progress | In progress | In progress | In progress | scoped offline queue contract and confirmation rules | Community Care Lead |

---

## 2. Cross-cutting blockers board

| Priority | Item | Status | Blocks |
| --- | --- | --- | --- |
| P0 | ConsentService interface | Ready for review | all patient-data route implementation |
| P0 | Multi-tenancy enforcement strategy | Ready for review | Prisma and repository implementation |
| P0 | Secrets management playbook | Ready for review | staging, integrations, workers |
| P1 | Data classification matrix | Ready | security review and retention policy |
| P1 | CI/CD pipeline specification | Ready for review | merge and deployment automation |
| P1 | DPIA template | Ready | public release approval |
| P1 | Security test suite definition | Ready | QA execution |
| P2 | Prisma draft models | Ready | backend implementation |
| P2 | OpenAPI DTO drafts | Ready | backend and frontend parallelization |
| P2 | Test automation mapping | Ready | QA ownership and rollout |

---

## 3. Immediate next actions

1. Approve the ConsentService and tenancy specs.
2. Freeze the shared DTO set for all Core MVP routes.
3. Create the planned test suite locations and seed fixtures.
4. Provision staging secrets using the approved secrets playbook.

This board should be updated at least weekly during execution preparation.