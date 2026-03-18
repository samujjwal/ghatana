# PHR Workflow — Consent and Access Grants

**Version:** 2.0  
**Date:** 2026-01-19
**Phase:** Core MVP

| Field              | Value                                                                                                                                                             |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                                                 |
| **Classification** | Internal — Restricted                                                                                                                                             |
| **Last Review**    | 2026-01-19                                                                                                                                                        |
| **Companion Docs** | [ConsentService Interface](../03_architecture/phr_nestjs_modules_detailed_architecture.md), [Sequence Diagrams](../03_architecture/phr_core_sequence_diagrams.md) |

> **📌 What changed in v2.0:** Added emergency break-the-glass consent flow, caregiver delegation consent model, document-level granular consent, consent audit trail patient visibility (inspired by Estonia X-Road), and consent cache invalidation rules.

---

## 1. Goal

Allow the patient to grant, inspect, and revoke access to their records in a way that is enforceable at runtime and auditable afterward.

---

## 2. Primary actors

- patient
- provider as recipient of access
- caregiver as an active delegated-access recipient in MVP

---

## 3. Entry points

- patient access page

APIs:

- `GET /api/v1/patients/:id/access-grants`
- `POST /api/v1/access-grants`
- `PATCH /api/v1/access-grants/:id/revoke`

---

## 4. Preconditions

- patient is authenticated
- patient context matches target patient
- recipient identity is resolvable

---

## 5. Data touched

- `ConsentGrant`
- `AuditLog`

---

## 6. Happy path

1. patient opens access page
2. active and historical grants are loaded
3. patient creates a new grant with recipient, scope, and expiry
4. duplicate/conflicting grants are checked
5. grant is persisted
6. policy layer begins honoring the grant
7. audit record is written
8. patient may revoke later and runtime access ends immediately

---

## 7. Alternate and failure paths

- duplicate active grant -> conflict
- expiry before start -> validation error
- revoke already inactive grant -> idempotent success or conflict per policy
- attempted access with expired grant -> forbidden and audited

---

## 8. Acceptance criteria

- patient can view all active grants
- grant scope and expiry are enforced on read paths
- revoked grants stop access immediately
- create/revoke actions are audited
- forbidden access attempts are auditable

---

## 9. Break-the-Glass Emergency Access (Added in v2.0)

**Trigger:** Provider selects "Emergency Access" when patient is incapacitated or unable to consent.

**Flow:**

1. Provider authenticates and selects "Emergency Access" on patient lookup
2. System presents break-the-glass confirmation dialog with legal notice
3. Provider enters justification (free text + category: trauma, unconscious, minor-without-guardian)
4. System creates **time-limited emergency grant** (default: 4 hours, max: 24 hours)
5. Provider gains read access to: allergies, active medications, blood type, emergency contacts
6. Provider does NOT gain access to: mental health notes, reproductive health, HIV status
7. Patient receives **immediate notification** (push + SMS) that emergency access was granted
8. Audit log records: provider ID, facility, justification, access scope, timestamp
9. Provider MUST submit a formal justification review within 24 hours
10. If no justification submitted → escalate to facility compliance officer

**Abuse prevention:**

- Rate limit: max 3 break-the-glass events per provider per 30 days
- All break-the-glass events flagged for monthly compliance review
- Repeated unjustified use triggers account suspension workflow

**Global precedent:** Australia My Health Record emergency access, UK NHS Break-the-Seal audit.

---

## 10. Caregiver Delegation Consent Model (Added in v2.0)

Nepal has strong family-based caregiving. The consent model supports delegated access:

| Delegation Type                | Duration                      | Scope                                   | Approval                                          |
| ------------------------------ | ----------------------------- | --------------------------------------- | ------------------------------------------------- |
| Parent → child (minor)         | Until child turns 18          | Full (minus reproductive health at 16+) | Automatic                                         |
| Adult → spouse/family          | Configurable (default 1 year) | Patient-selected resources              | Patient explicit consent                          |
| Adult → FCHV                   | Per visit (expires 48h)       | Vitals, medications, appointments       | Patient or proxy consent                          |
| Elderly → designated caregiver | Ongoing (annual renewal)      | Full or patient-selected                | Patient + witness signature (digital or physical) |

This delegation model is part of the Core MVP baseline and governs caregiver access to dependent summaries, appointments, medications, documents, referrals, imaging, and billing surfaces where the grant scope explicitly allows those actions.

**Revocation:** All delegated consent is revocable instantly by the patient. For minors, the minor gains progressive consent rights at age 16 (reproductive health, mental health).

---

## 11. Document-Level Granular Consent (Added in v2.0)

Beyond resource-type consent (e.g., "all medications"), patients can set consent on individual documents:

- **Upload-time consent**: When uploading a document, patient selects visibility (private, shared-with-provider, shared-with-all-granted)
- **Retroactive consent change**: Patient can change individual document visibility at any time
- **Consent inheritance**: New grants inherit the document-level visibility settings
- **Metadata vs. content**: Document metadata (type, date, size) may be visible even if content is restricted (configurable)

---

## 12. Patient-Visible Consent Audit Trail (Added in v2.0)

**Inspired by:** Estonia X-Road "Who has viewed my data" transparency log.

Patients see a simplified audit trail:

| Column | Description                                                 |
| ------ | ----------------------------------------------------------- |
| Date   | When access occurred                                        |
| Who    | Provider name + facility                                    |
| What   | Resource type accessed (e.g., "Medications", "Lab results") |
| Why    | Appointment, Emergency, or Referral                         |
| Action | View, Download, or Print                                    |

**Rules:**

- Audit trail is read-only for all actors (immutable append-only log)
- Patient can flag a suspicious access → triggers compliance review
- Audit entries retained for 7 years (Nepal Privacy Act 2075 alignment)
- Break-the-glass entries highlighted in red with justification visible

---

## 13. Consent Cache Invalidation (Added in v2.0)

Consent decisions are cached (Valkey L2) for performance. Invalidation rules:

| Event                          | Cache Action                             | Max Staleness |
| ------------------------------ | ---------------------------------------- | ------------- |
| Grant created                  | Invalidate grantee's consent cache       | 0 (immediate) |
| Grant revoked                  | Invalidate grantee's consent cache       | 0 (immediate) |
| Grant expired (TTL)            | Automatic eviction by Valkey TTL         | 0             |
| Document-level consent changed | Invalidate all grantees for that patient | 0 (immediate) |
| Break-the-glass grant created  | Bypass cache, direct DB check            | N/A           |
| Patient profile deactivate     | Invalidate ALL caches for patient        | 0 (immediate) |

**Security rule:** On any cache miss, the system MUST query the DB. A cache hit for "denied" is authoritative; a cache hit for "allowed" is verified against DB if the grant is within 5 minutes of expiry.
