# PHR Workflow — Caregiver and Dependent Access

**Version:** 2.0  
**Date:** 2026-03-17
**Phase:** Core MVP

| Field              | Value                                                                                                                                           |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                               |
| **Classification** | Internal — Restricted                                                                                                                           |
| **Last Review**    | 2026-03-17                                                                                                                                      |
| **Companion Docs** | [Consent workflow](phr_workflow_consent.md), [Frontend Route Map](phr_frontend_route_and_component_map.md), [Route Contract Pack](phr_mvp_route_contract_pack.md) |

> **📌 What changed in v2.0:** Formalized caregiver dependent list and scoped summary flows, delegated appointment and medication-support actions, caregiver offline sync-state behavior, and grant-expiry handling for MVP.

---

## 1. Goal

Allow an authorized caregiver to discover linked dependents, open a caregiver-safe summary, and perform only the delegated actions explicitly allowed by the active grant.

---

## 2. Primary actors

- caregiver
- patient who granted access
- provider as downstream reader of caregiver-triggered actions

---

## 3. Entry points

- caregiver dependents list
- caregiver dependent summary
- delegated appointment, medication adherence, billing, and referral follow-up surfaces launched from the dependent summary

APIs:

- `GET /api/v1/caregivers/me/dependents`
- `GET /api/v1/caregivers/me/dependents/:id/summary`
- delegated patient endpoints guarded by caregiver scope checks

---

## 4. Preconditions

- caregiver is authenticated as `CAREGIVER`
- an active `ConsentGrant` or equivalent delegated-access record links caregiver to dependent
- grant scopes are resolved before any dependent data leaves the service boundary
- tenant and patient relationship checks pass

---

## 5. Data touched

- `RelatedPerson`
- `ConsentGrant`
- `Patient`
- `Appointment`
- `MedicationRequest`
- `Invoice`
- `AuditLog`

---

## 6. Happy path

### 6.1 View dependent list

1. caregiver opens dependents page
2. platform resolves all active dependent relationships for that caregiver in the current tenant
3. each dependent card is enriched with relationship label, grant status, and allowed action scopes
4. expired or revoked grants are excluded from the actionable list
5. audit record is written for dependent-list access

### 6.2 Open dependent summary

1. caregiver selects a dependent
2. policy layer evaluates the active grant and computes the allowed summary cards
3. dependent summary response returns only allowed sections such as appointments, medications, documents, or billing
4. UI renders `GrantLimitBanner` when some common sections are intentionally hidden by policy
5. sync-state badge shows `LIVE`, `OFFLINE`, or `PENDING_SYNC` for approved offline-capable actions

### 6.3 Perform delegated actions

1. caregiver selects an allowed action such as appointment booking, reminder acknowledgement, or payment initiation
2. request is tagged with caregiver actor identity plus dependent context
3. downstream module re-checks caregiver scope before write
4. mutation succeeds and emits audit evidence with caregiver actor, dependent patient, and scope used

---

## 7. Alternate and failure paths

- caregiver has no active dependents -> explicit empty state with guidance to request delegated access
- grant expired between list and summary load -> summary request returns forbidden with expiry reason
- caregiver attempts disallowed action -> UI blocks when possible; API returns forbidden and audits the attempt
- dependent belongs to different tenant -> not found or forbidden without revealing existence
- partial downstream module outage -> summary omits the affected card only when the response includes an explicit unavailable state; silent omission is not allowed

---

## 8. UX requirements

- dependent list must show relationship label and grant status clearly
- dependent summary must distinguish hidden-by-policy from no-data states
- caregiver actions must always label which dependent is in context
- grant expiry warning should appear before the user starts an action when expiry is imminent
- bilingual copy must avoid ambiguous family-role wording

---

## 9. Acceptance criteria

- caregiver sees only dependents covered by active delegated access
- dependent summary never leaks fields outside the active grant scope
- delegated actions are attributed to caregiver actor identity in audit logs
- grant revocation is reflected immediately on refresh and after cache invalidation
- caregiver offline-capable actions show sync state and last-sync timestamp consistently

---

## 10. Offline and sync behavior

For approved caregiver-capable flows:

- dependent summary metadata and selected cards may be cached locally in encrypted storage
- queued writes are allowed only for flows explicitly permitted by the grant and the module policy
- every offline write stores caregiver actor ID, dependent patient ID, device timestamp, and sync status
- conflicting clinical writes require manual review before server acceptance
- billing, imaging download, and consent management actions remain online-first unless the specific module explicitly supports queueing

---

## 11. Security and compliance notes

- every dependent read and delegated mutation is auditable as sensitive access
- caregiver access must respect document-level visibility and not only resource-level grant scope
- emergency or reproductive-health exclusions for minors must be enforced before response assembly
- caregiver session hardening should require step-up authentication for high-risk actions such as payments or export initiation