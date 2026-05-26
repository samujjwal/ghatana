# PHR Consent Revocation — Operational Runbook

**Document type:** Runbook  
**Layer:** Product  
**Last updated:** 2026-05-02  
**Audience:** On-call engineers, product admins  

---

## 1. Overview

PHR consent grants can be revoked by the patient who issued them or by an admin for governance reasons. Upon revocation, the provider loses immediate access to the patient's PHI. This runbook covers the end-to-end revocation flow, cache invalidation, and escalation paths.

---

## 2. Revocation Flow

### Patient-Initiated (Web)

1. Patient navigates to `/consents`.
2. Patient taps "Revoke" on an active grant.
3. `ConsentPage.tsx` calls `POST /consents/grants/:grantId/revoke`.
4. Backend transitions the FHIR `Consent` resource status to `inactive`.
5. An audit event of type `CONSENT_REVOKE` is emitted.
6. Provider's subsequent requests to access the patient's PHI return `403 Forbidden`.

### Admin-Initiated

1. Admin uses internal tools or the API directly.
2. Same `POST /consents/grants/:grantId/revoke` endpoint with `X-Role: admin`.
3. Backend validates admin role and revokes the grant.
4. Patient receives a notification of admin-initiated revocation (if push notifications are enabled).

---

## 3. Cache Invalidation Requirements

**Target:** Provider-facing PHI responses cached by CDN, API gateway, or server-side cache must be invalidated within **60 seconds** of revocation.

| Layer | Invalidation method |
|---|---|
| Backend in-memory cache | `ConsentCache.invalidate(patientId, recipientId)` on successful revoke |
| CDN (if applicable) | Purge by cache key derived from `patientId` |
| API gateway response cache | Tag-based purge on `tenantId + patientId` |
| Client cache (provider web app) | Provider receives `403` on next request; React Query cache must be invalidated client-side |

**Verification:** After revocation, call `GET /consents/check?patientId=:id&recipientId=:providerId` and verify the response returns `{ granted: false }`.

---

## 4. Troubleshooting Revocation Failures

| Symptom | Possible cause | Action |
|---|---|---|
| Revoke returns 404 | Grant ID does not exist or already revoked | Check current grant status: `GET /consents?patientId=:id` |
| Revoke returns 403 | Caller is neither the issuing patient nor an admin | Verify `X-Role` and `X-Principal-Id` headers |
| Revoke succeeds but provider still has access | Cache invalidation failed | Manually purge cache entries; check `ConsentCache` metrics |
| Revoke reverts on page refresh | Frontend state not cleared | Check React Query `queryClient.invalidateQueries(['consents'])` is called after revoke |
| Audit event missing | Event bus failure | Check `phr_audit_events_failed_total` metric; replay from consent state |

---

## 5. Escalation

| Condition | Action |
|---|---|
| Provider continues accessing PHI > 5 minutes after revocation confirmed | Escalate to platform on-call to manually purge API gateway cache |
| Revocation fails with 500 | Check backend logs with the `X-Correlation-ID` of the failed request; page on-call engineer |
| Patient reports inappropriate access after revocation | Privacy incident — file immediately; escalate to privacy officer |

---

## 6. Investigating a Specific Revocation Event

```bash
# Check consent status
curl -X GET "https://api.phr.example.com/consents?patientId=PATIENT_ID" \
  -H "X-Tenant-Id: TENANT_ID" \
  -H "X-Principal-Id: PATIENT_PRINCIPAL_ID" \
  -H "X-Role: patient" \
  -H "Authorization: Bearer <patient-token>"

# Force revoke as admin
curl -X POST "https://api.phr.example.com/consents/grants/GRANT_ID/revoke" \
  -H "X-Tenant-Id: TENANT_ID" \
  -H "X-Principal-Id: ADMIN_PRINCIPAL_ID" \
  -H "X-Role: admin" \
  -H "Authorization: Bearer <admin-token>"
```

Use the `X-Correlation-ID` from the response to trace the full revocation request in the structured log stream.

---

## 7. Monitoring

| Metric | Description | Alert threshold |
|---|---|---|
| `phr_consent_revocations_total` | Counter of all revocations | Baseline alert if sudden spike (> 10x normal rate) |
| `phr_consent_cache_invalidation_latency_ms` | p99 invalidation time | > 10,000 ms → alert |
| `phr_consent_check_denied_total` | Provider 403s after revocation | Spike after revocation expected; plateau indicates cache miss |

---

## 8. Related Documents

- [phr_access_policy_matrix.md](./phr_access_policy_matrix.md)
- [phr_emergency_access.md](./phr_emergency_access.md)
