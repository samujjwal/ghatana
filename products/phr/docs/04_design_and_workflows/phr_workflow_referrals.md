# PHR Workflow — Referrals

**Version:** 2.0  
**Date:** 2026-03-17
**Phase:** Core MVP

| Field              | Value                                                                                                                                 |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                     |
| **Classification** | Internal — Restricted                                                                                                                 |
| **Last Review**    | 2026-03-17                                                                                                                            |
| **Companion Docs** | [Route Contract Pack](phr_mvp_route_contract_pack.md), [Screen Matrix](phr_screen_by_screen_mvp_implementation_matrix.md), [Timeline workflow](phr_workflow_timeline.md) |

> **📌 What changed in v2.0:** Added provider referral creation, medical-summary attachment rules, referral status tracking, caregiver visibility constraints, and referral audit requirements for MVP.

---

## 1. Goal

Support cross-facility care coordination by allowing providers to create referrals, attach a medical summary, and let patients or authorized caregivers track referral status through completion.

---

## 2. Primary actors

- referring provider
- patient
- caregiver with referral read scope
- receiving organization staff

---

## 3. Entry points

- provider patient summary
- patient referrals page
- caregiver dependent summary referral card

APIs:

- `POST /api/v1/referrals`
- `GET /api/v1/patients/:id/referrals`
- `GET /api/v1/referrals/:id`

---

## 4. Preconditions

- provider has authorized relationship to the patient
- receiving organization is resolvable in the current tenant or approved network directory
- optional medical summary document is available and permitted for sharing
- referral priority and reason are present

---

## 5. Data touched

- `ServiceRequest`
- referral status event tables
- `DocumentReference`
- `AuditLog`

---

## 6. Happy path

### 6.1 Create referral

1. provider opens referral composer in patient context
2. provider selects receiving organization, specialty, priority, reason, and optional summary attachment
3. platform validates attachment ownership, consent compatibility, and organization availability
4. referral is created as a `ServiceRequest`-shaped record with initial status timeline entry
5. audit log records referral issuance and attachment linkage

### 6.2 Track referral

1. patient or authorized caregiver opens referrals page
2. platform returns only referrals visible under the current patient and grant context
3. list shows receiving facility, priority, and current status
4. detail page shows ordered status timeline and linked summary reference where allowed

---

## 7. Alternate and failure paths

- receiving organization missing or inactive -> validation error
- summary attachment exists but is outside consent scope -> attachment rejected or omitted
- caregiver without referral scope -> list hidden or forbidden response
- referral status event write fails after create -> transaction rollback or explicit recovery queue; partial create is not acceptable
- receiving facility unavailable -> referral can still be created if asynchronous acceptance is supported, but status must remain explicit

---

## 8. UX requirements

- referral composer should show specialty and facility selection clearly
- patient-facing referral views must explain status in plain language, not only clinical codes
- caregiver views must label when they are seeing dependent referral data
- timeline states must not imply receiving-facility acceptance before confirmation exists

---

## 9. Acceptance criteria

- provider can create a referral with required core fields
- medical-summary attachment is linked only when sharing policy permits it
- patient and caregiver can track referral state without seeing provider-only authoring controls
- referral reads and writes are fully audited
- status ordering and timestamp integrity remain stable across retries and updates

---

## 10. Status model

Core MVP statuses:

- `draft`
- `sent`
- `accepted`
- `booked`
- `closed`

Rules:

- `draft` exists only briefly during provider authoring if persisted drafts are enabled
- `sent` is the default post-create state
- `accepted` requires acknowledgement from the receiving side
- `booked` indicates downstream appointment scheduling is confirmed
- `closed` indicates the referral has been completed, cancelled, or superseded with an explicit closing note

---

## 11. Security and compliance notes

- referral attachments must respect document-level consent and minimum necessary disclosure
- patient and caregiver list views must not expose internal facility triage notes unless explicitly allowed
- every referral status change must record actor, role, timestamp, and note provenance
- advanced referral marketplace behavior, routing automation, and multi-hop coordination remain Phase 2