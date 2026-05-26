# PHR Emergency Access — Operational Runbook

**Document type:** Runbook  
**Layer:** Product  
**Last updated:** 2026-05-02  
**Audience:** On-call engineers, product admins  

---

## 1. Overview

PHR emergency access (break-glass) allows any authenticated user — primarily providers — to access a patient's PHI in a life-threatening or time-critical situation without a pre-existing consent grant. Every emergency access event is logged to the audit trail and appears in the admin review queue.

---

## 2. Emergency Access Flow

### Web

1. Authenticated user navigates to `/emergency`.
2. User selects the patient (by patient ID or search) and enters a mandatory `reason` field.
3. Frontend calls `POST /emergency/access` with `{ patientId, reason }` + auth context headers.
4. Backend validates request, writes an audit event of type `EMERGENCY_ACCESS`, and returns the patient's dashboard PHI.
5. The event is visible to admins at `GET /emergency/reviews/pending`.

### Mobile

1. Authenticated patient taps the emergency tab.
2. The `EmergencyAccessScreen` triggers a biometric re-challenge (`LocalAuthentication`).
3. On biometric success, the same `POST /emergency/access` call is made with the device's session context.
4. PHI is shown in-screen for the session duration only (not cached offline).

---

## 3. Admin Review Process

| Step | Action | Actor |
|---|---|---|
| 1 | Event appears in `GET /emergency/reviews/pending` | System |
| 2 | Admin opens `EmergencyReviewPage` at `/admin/emergency` | Admin |
| 3 | Admin reads `accessedAt`, `principalId`, `patientId`, `reason` | Admin |
| 4 | Admin submits review note via `POST /emergency/reviews/:eventId` | Admin |
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
curl -X GET "https://api.phr.example.com/emergency/reviews/pending" \
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
