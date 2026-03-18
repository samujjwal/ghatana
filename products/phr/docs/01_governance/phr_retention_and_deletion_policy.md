# PHR Platform - Retention and Deletion Policy

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Document owner:** Privacy and Compliance Lead  
**Approval status:** Active planning artifact  
**Classification:** Internal

This document defines the policy model for record retention, deletion, anonymization, legal hold, and evidence preservation across the PHR platform. It is the source of truth when retention or erasure language in other planning documents needs operational interpretation.

---

## 1. Policy principles

1. retention is configurable by policy profile, but policy values MUST NOT go below statutory or contractual minimums
2. legal hold always overrides scheduled deletion, anonymization, purge, and artifact expiry
3. deletion is outcome-based, not always full physical purge
4. patient rights workflows MUST distinguish between data that can be purged, data that must be anonymized, and data that must be retained as evidence
5. auditability is mandatory for all retention-policy changes, deletion requests, hold placements, and deletion outcomes

---

## 2. Supported deletion outcomes

| Outcome | When used | Required evidence |
| --- | --- | --- |
| `PURGE` | temporary or derivative artifacts with no legal retention floor | request id, actor, timestamp, resource reference |
| `ANONYMIZE` | patient-linked records that may be retained in non-identifying form | policy basis, fields transformed, verifier |
| `TOMBSTONE` | user-facing deletion acknowledgement where referential evidence must remain | resource id, deleted-at timestamp, actor, policy reason |
| `RETAIN_UNDER_HOLD` | legal, regulatory, security, or fraud investigation hold | hold id, authority, hold reason, review date |

---

## 3. Baseline retention model

| Data class | Default baseline | Notes |
| --- | --- | --- |
| patient clinical records | 7 years minimum | may extend by facility or program policy |
| identity and access evidence | 7 years minimum | includes actor linkage and consent evidence |
| audit logs | policy-driven archival baseline subject to legal minimums | searchable retention may differ from cold archive duration |
| export artifacts | short-lived by default | artifact expiry should be measured in hours or days, not years |
| OCR and ASR intermediates | short-lived by default | retain only while review, provenance, or incident handling requires |
| offline drafts and sync payloads | short-lived by default | purge after successful sync and verification unless policy exception applies |
| security investigation records | until incident closure plus policy baseline | legal hold can extend indefinitely until released |

Policy baselines may vary by tenant, facility class, program, or legal region, but every variant MUST be traceable to an approved policy profile.

---

## 4. Resource-specific rules

| Resource or artifact | Default action | Constraints |
| --- | --- | --- |
| `Patient`, `Encounter`, `Observation`, `MedicationRequest`, `DocumentReference`, `Coverage` | retain to legal minimum, anonymize only where policy allows | clinical truth cannot be silently hard-deleted before minimum retention ends |
| `ConsentGrant` | retain evidence to legal minimum | revocation changes access; it does not erase grant history |
| `AuditLog` | archive under policy, never mutable | append-only evidence record |
| `StoredObject` and derived uploads | purge object when no legal basis remains | malware or integrity evidence may require hold |
| `OcrExtractionResult`, `AudioTranscription`, `ReviewQueueItem`, `InputProvenance` | purge or anonymize after review window | provenance needed for accepted clinical data must remain evidentiary |
| export job records and generated artifacts | keep job metadata for audit; expire downloadable artifact aggressively | downloadable files must be encrypted, access-scoped, and time-limited |
| offline queue payloads | purge after sync success and reconciliation | failed sync payloads remain only until recovery or operator resolution |

---

## 5. Patient deletion request workflow

1. validate requester identity and request scope
2. evaluate active legal minimums, holds, investigations, and payment or insurance obligations
3. classify each affected resource into purge, anonymize, tombstone, or retain-under-hold outcome
4. execute deletion plan with audit logging for each step
5. issue user-facing completion summary that explains what was deleted, anonymized, retained, or deferred

Deletion requests MUST NOT promise unconditional physical erasure of every related record. The workflow outcome is policy-driven and legally constrained.

---

## 6. Legal hold handling

- legal hold can be placed by compliance, security, or authorized administrative actors
- hold scope may target patient, tenant, artifact class, incident, export job, or audit slice
- held data is excluded from purge and anonymization timers until the hold is lifted
- every hold requires a reason, authority, placed-at timestamp, and periodic review date

---

## 7. Operational requirements

- retention policy profiles MUST be versioned and change-controlled
- policy evaluation results MUST be visible to deletion workflows and background cleanup jobs
- export artifacts MUST have explicit expiry timestamps and revocation behavior
- background cleanup jobs MUST be idempotent and produce audit evidence
- tenant-specific overrides MUST inherit platform legal floors rather than replace them

---

## 8. Testing and audit obligations

- verify policy floors cannot be configured below legal minimums
- verify legal hold prevents purge and anonymization
- verify export artifacts expire and become inaccessible after policy timeout
- verify offline drafts are removed after successful sync and reconciliation
- verify deletion summaries distinguish purged, anonymized, tombstoned, and retained-under-hold outcomes
- verify audit evidence exists for policy changes, deletion requests, hold placement, hold release, and cleanup execution

---

## 9. Cross-document usage

Use this document when interpreting or updating:

- MVP and phase release definitions
- requirements traceability and privacy requirements
- DPIA, QA, and compliance test plans
- data classification and schema planning
- export, document, OCR, ASR, and offline workflow specifications