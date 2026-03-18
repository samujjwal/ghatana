# PHR Platform — Data Classification Matrix

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** Security Lead  
**Approval status:** Active control matrix  
**Classification:** Internal — Restricted

This matrix applies the PHR data classification framework to the primary Core MVP and planned extension tables. It closes the backlog item requiring table-level classification coverage.

Retention baselines in this matrix are policy defaults, not hard-coded purge dates. Tenant or regulatory policy may extend or specialize them, but no configuration may go below the applicable legal minimum or bypass legal hold.

---

## 1. Classification rules

| Class | Meaning | Encryption | Access |
| --- | --- | --- | --- |
| `C1` | public or non-sensitive reference | TLS in transit | authenticated or public per route |
| `C2` | internal operational metadata | TLS plus at-rest | role-based |
| `C3` | PII and contact data | TLS plus AES-256 at-rest | consent and RBAC |
| `C4` | clinical and financial health data | TLS plus AES-256 plus field-level protection where needed | consent, RBAC, and audit |

---

## 2. Table matrix

| Table | Primary class | Example sensitive columns | Retention baseline | Notes |
| --- | --- | --- | --- | --- |
| `Tenant` | `C2` | tenant metadata | lifecycle | no patient data |
| `Organization` | `C2` | facility details | lifecycle | organization data only |
| `Practitioner` | `C3` | identifiers, profile data | employment or policy lifecycle | provider PII |
| `Patient` | `C3` | names, demographics, address, contacts | 7 years minimum | base profile root |
| `Identifier` | `C3` | national identifiers, policy ids | 7 years minimum | do not expose raw identifiers broadly |
| `HumanName` | `C3` | legal names | 7 years minimum | PII |
| `Address` | `C3` | residence and district | 7 years minimum | PII |
| `ContactPoint` | `C3` | phone, email | 7 years minimum | PII |
| `AuditLog` | `C2` | actor ids, patient ids | policy-driven archival baseline subject to legal minimums | searchable only by authorized roles |
| `ExportJob` | `C2` | actor ids, patient ids, export format, job state | short-lived artifact with policy-driven metadata retention | generated files expire aggressively; metadata remains auditable |
| `ConsentGrant` | `C3` | patient and grantee linkage | 7 years minimum | consent metadata |
| `Encounter` | `C4` | encounter reason, clinical notes | 7 years minimum | clinical truth |
| `Observation` | `C4` | vital values, lab results | 7 years minimum | clinical truth |
| `Condition` | `C4` | diagnoses | 7 years minimum | clinical truth |
| `Medication` | `C4` | formulary and medication details | 7 years minimum when patient-linked | reference and clinical use |
| `MedicationRequest` | `C4` | prescription details | 7 years minimum | clinical truth |
| `Appointment` | `C3` | patient and provider linkage, booking reason | 7 years minimum | operational and health context |
| `DocumentReference` | `C4` | document type and linkage | 7 years minimum | metadata may reveal clinical context |
| `Coverage` | `C4` | payer membership details | 7 years minimum | financial health data |
| `Claim` | `C4` | billing and financial claim data | 7 years minimum | Phase 2 |
| `ClaimResponse` | `C4` | reimbursement outcomes | 7 years minimum | Phase 2 |
| `RelatedPerson` | `C3` | family/caregiver data | 7 years minimum | MVP caregiver baseline |
| `Schedule` | `C2` | provider availability | 1 year minimum | operational |
| `Slot` | `C2` | slot availability | 1 year minimum | operational |
| `ActorProfile` | `C3` | subject linkage | 7 years minimum | auth-to-domain mapping |
| `StoredObject` | `C4` | file checksum and storage link | 7 years minimum | contents usually clinical |
| `DocumentVersion` | `C4` | document history | 7 years minimum | clinical document lineage |
| `EligibilityCheckLog` | `C4` | eligibility request and response payloads | 7 years minimum | payer interaction evidence |
| `OcrExtractionResult` | `C4` | extracted clinical content | 7 years minimum | contains derived clinical data |
| `AudioTranscription` | `C4` | transcript text | 7 years minimum | may include sensitive dictation |
| `InputProvenance` | `C2` | source-to-target linkage | 7 years minimum | operational traceability |
| `ReviewQueueItem` | `C2` | assignment and workflow metadata | 1 year minimum | operational queue metadata |
| `ReminderPlan` | `C2` | schedule metadata | 1 year minimum | operational |
| `NotificationDelivery` | `C2` | masked destination, status | 1 year minimum | no plaintext message body retention |
| `FeatureFlag` | `C1` or `C2` | rollout rules | lifecycle | tenant-specific flags are `C2` |
| `OutboxEvent` | `C2` or `C4` | event payload | align to payload sensitivity | classify by payload contents |
| `ClaimSubmissionAttempt` | `C4` | financial payloads | 7 years minimum | Phase 2 |
| `CaregiverRelationship` | `C3` | family and delegate linkage | 7 years minimum | MVP caregiver baseline |
| `TelemedicineSession` | `C4` | participant and visit metadata | 7 years minimum | Phase 2 |
| `TelemedicineParticipant` | `C3` | participant identity | 7 years minimum | Phase 2 |
| `TranscriptAsset` | `C4` | consultation transcript | 7 years minimum | Phase 2 |
| `RecordingAsset` | `C4` | audio or video recording | 7 years minimum | Phase 2 |

---

## 3. Enforcement notes

- `C3` and `C4` tables are always tenant-scoped and audited for sensitive access.
- document binaries inherit the stricter of table classification or actual content classification.
- exports must preserve classification labels so downstream handling rules remain explicit.

This matrix must be referenced by schema changes, retention jobs, backup policies, and security test suites.