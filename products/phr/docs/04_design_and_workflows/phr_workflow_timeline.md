# PHR Workflow — Timeline and Clinical Record Read

**Version:** 2.0  
**Date:** 2026-01-19
**Phase:** Core MVP

| Field              | Value                                                                                                                        |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                            |
| **Classification** | Internal                                                                                                                     |
| **Last Review**    | 2026-01-19                                                                                                                   |
| **Companion Docs** | [Sequence Diagrams](../03_architecture/phr_core_sequence_diagrams.md), [Route Contract Pack](phr_mvp_route_contract_pack.md) |

> **📌 What changed in v2.0:** Added consent-denied error path, offline cached timeline behavior, data freshness indicators, and cross-module data assembly consent verification.

---

## 1. Goal

Provide a single longitudinal read surface across encounters, observations, conditions, medications, and documents.

---

## 2. Primary actors

- patient
- provider
- delegated caregiver in scoped MVP flows

---

## 3. Entry points

- patient dashboard summary widgets
- medical history timeline
- provider patient summary

APIs:

- `GET /api/v1/patients/:id/timeline`
- `GET /api/v1/patients/:id/summary`
- `GET /api/v1/patients/:id/encounters`

---

## 4. Preconditions

- actor is authenticated
- patient target is resolved
- policy/consent check passes

---

## 5. Data touched

Read:

- `Encounter`
- `Observation`
- `Condition`
- `MedicationRequest`
- `DocumentReference`

Write:

- `AuditLog`

---

## 6. Happy path

1. actor requests patient timeline
2. policy layer validates actor-to-patient access
3. timeline query handler fans out to owning modules
4. results are normalized into a common timeline item format
5. filters/grouping are applied
6. sensitive read audit is written
7. response is returned with paging/filter metadata

---

## 7. Alternate and failure paths

- no records -> valid empty timeline state
- partial module read failure -> fail request or degrade according to agreed policy, but do not return silent partial data without explicit status
- consent expired -> forbidden
- provider lacks relationship -> forbidden

---

## 8. UX requirements

- clear loading state
- empty state with next actions
- date/category/provider filters
- stable chronology ordering
- abnormal/result highlight rules where applicable

---

## 9. Audit requirements

- record read audit for timeline and summary views
- forbidden read attempts

---

## 10. Acceptance criteria

- timeline includes all active in-scope record types
- filters do not break consent boundaries
- items link to detail views where defined
- no-data state is explicit and user-friendly
- read audit exists for successful and failed sensitive access

---

## 11. Consent-Denied Error Path (Added in v2.0)

When a provider requests a patient's timeline but consent has expired or been revoked:

1. ConsentModule returns `CONSENT_DENIED` with grant status and expiry date
2. Timeline API returns `403 Forbidden` with body: `{ "code": "CONSENT_EXPIRED", "message": "Patient consent has expired or been revoked." }`
3. UI shows: "You no longer have access to this patient's records. Request access to continue."
4. UI provides "Request Access" button → triggers consent request workflow
5. Audit log records: actor ID, patient ID, attempted resource, deny reason, timestamp

**Security rule:** Error response MUST NOT reveal what records exist or any clinical data.

---

## 12. Offline Cached Timeline (Added in v2.0)

Mobile app maintains a local timeline cache for offline viewing:

- Last viewed timeline cached locally (encrypted SQLite)
- Cache refreshes on each successful online timeline load
- Offline display shows cached timeline with **amber banner**: "Showing cached data from {timestamp}. Connect to see latest."
- New records created offline are visible in the local cache immediately
- On reconnection, local cache merges with server data (server authoritative for conflicts)

**Cache scope:** Patient self views and caregiver-dependent summary timelines may be cached when the active grant permits it. Provider views are NOT cached offline.

---

## 13. Data Freshness Indicators (Added in v2.0)

Each timeline item shows freshness context:

| Indicator | Meaning                           | Visual                                     |
| --------- | --------------------------------- | ------------------------------------------ |
| Live      | Fetched in current session        | No indicator (default)                     |
| Recent    | From cache, < 1 hour old          | Grey timestamp                             |
| Stale     | From cache, > 1 hour old          | Amber timestamp + "May not reflect latest" |
| Offline   | From local cache, no connectivity | Amber banner at top                        |

---

## 14. Consent Verification on Timeline Reads (Added in v2.0)

Every timeline read verifies consent at two levels:

1. **Resource-level consent:** Does the actor have an active grant to view this resource type?
2. **Document-level consent:** For documents, is the individual document's access level compatible with the actor?

**Performance:** Consent check uses Valkey cache (L2). Cache miss triggers DB query. See consent workflow doc Section 13 for invalidation rules.

**Partial consent:** If an actor has consent for medications but not lab results, the timeline shows medication items and omits lab results WITHOUT indicating that hidden items exist.
