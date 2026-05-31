# PHR Emergency Access — Operational Runbook

**Document type:** Runbook  
**Layer:** Product  
**Last updated:** 2026-05-31  
**Audience:** On-call engineers, product admins  

---

## 1. Overview

PHR emergency access (break-glass) allows authorized clinical/admin roles to request time-critical PHI access without a pre-existing consent grant. The request must include a patient identifier, documented justification, tenant/principal/role/persona/tier/facility context, and correlation ID. Every approved or denied emergency access path is policy evaluated, audited, and visible to the admin review queue.

---

## 2. Emergency Access Flow

### Web

1. Authenticated user navigates to `/emergency`.
2. User selects the patient (by patient ID or search) and enters a mandatory `reason` field.
3. Frontend calls `POST /api/v1/emergency/access` with `{ patientId, reason }` plus authenticated context headers.
4. Backend validates context, dispatches the Kernel policy plugin through the PHR emergency decision provider, writes audit telemetry, and returns only the emergency payload allowed by policy.
5. The event is visible to admins through `GET /api/v1/emergency/reviews/pending` and `GET /api/v1/emergency/reviews/overdue`.

### Mobile

1. Authenticated user opens the emergency surface.
2. `EmergencyAccessScreen` requires patient ID and documented reason before enabling the request.
3. The screen triggers a biometric/device re-challenge before any PHI request is sent.
4. On biometric success, the mobile client calls `POST /api/v1/emergency/access` with the full mobile session context.
5. PHI is shown in-screen only after server authorization and is not cached offline.

---

## 3. Admin Review Process

| Step | Action | Actor |
|---|---|---|
| 1 | Event appears in `GET /emergency/reviews/pending` | System |
| 2 | Admin opens `EmergencyReviewsPage` at `/emergency/reviews` | Admin |
| 3 | Admin reads `accessedAt`, `principalId`, `patientId`, `reason` | Admin |
| 4 | Admin submits review note via `POST /api/v1/emergency/reviews/:eventId` | Admin |
| 5 | Event moves out of pending queue; marked `reviewed` | System |

**SLA:** Emergency access events must be reviewed within 24 hours. Unreviewed events trigger an admin notification.

---

## 4. Escalation

| Condition | Action |
|---|---|
| More than 5 unreviewed events | Page on-call admin immediately |
| Emergency access reason is blank or placeholder ("test", "na") | Flag as suspicious; escalate to privacy officer |
| Same provider accessed >3 patients without consent in 1 hour | Trigger automatic account flag; notify privacy officer |
| Emergency access made outside business hours | Included in next-day privacy review report |

---

## 5. How to Investigate a Specific Event

```bash
# Retrieve a single emergency access event (replace EVENT_ID and TENANT_ID)
curl -X GET "https://api.phr.example.com/api/v1/emergency/reviews/pending" \
  -H "X-Tenant-Id: TENANT_ID" \
  -H "X-Principal-Id: ADMIN_PRINCIPAL_ID" \
  -H "X-Role: admin" \
  -H "Authorization: Bearer <admin-token>"
```

Log correlation: search structured logs by `correlationId` from the event's `X-Correlation-ID` header. The full request chain — login, access, PHI retrieval — will share the same `correlationId` if the client is compliant.

---

## 6. Reverting Inappropriate Emergency Access

Emergency access is read-only (no data mutation). If inappropriate access is confirmed:

1. Lock the offending principal's account (identity provider action).
2. File a privacy incident report.
3. Notify the affected patient within 72 hours per applicable regulations.
4. Mark the event as `POLICY_VIOLATION` in the review note.

---

## 7. Monitoring

| Metric | Alert threshold |
|---|---|
| `phr_emergency_access_total` (Prometheus counter) | > 10/hour across tenant → notify on-call |
| `phr_emergency_reviews_pending_total` | > 20 unreviewed → page privacy officer |
| `phr_emergency_review_latency_hours` | p99 > 24h → alert |

---

## 8. Related Documents

- [phr_access_policy_matrix.md](./phr_access_policy_matrix.md)
- [phr_consent_revocation.md](./phr_consent_revocation.md)
