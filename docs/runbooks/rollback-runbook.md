# Deployment Rollback Runbook

**Document type:** Operational runbook  
**Product scope:** All products (AEP, Data Cloud, YAPPC)  
**Severity classification:** P1 — failed deployment causing elevated error rates or degraded functionality  
**Last updated:** 2026-05-02  
**Owner:** Platform SRE / Release Engineering  
**Review cadence:** After every production incident involving a deployment

---

## Purpose

This runbook guides engineers through rolling back a failed or problematic deployment for AEP, Data Cloud, or YAPPC. A rollback restores the last known-good artifact without database migration side effects.

> **Scope**: This runbook covers application-layer rollbacks (container images, configuration). Schema migration rollbacks are covered separately in `docs/adr/` migration ADRs. Do not rollback schema changes without the Data Platform lead present.

---

## 1. Decision Criteria for Triggering a Rollback

Trigger this runbook when ALL of the following are true:

| Check | Signal | Threshold |
|---|---|---|
| Error rate elevated | `rate(http_server_requests_errors_total[5m]) / rate(http_server_requests_total[5m])` | > 5% for ≥ 5 minutes |
| Deployment recently completed | Deployment completed within the last 60 minutes | Deployment log |
| Roll-forward not feasible | The fix cannot be implemented and deployed within 15 minutes | Engineering judgment |

**Do NOT rollback if:**
- The error rate is below 1% and trending downward
- The deployment has been running for > 60 minutes without this degradation
- A targeted hotfix is already building and will deploy in < 15 minutes

---

## 2. Determine What Changed

### Step 1: Identify the failing service and current image

```bash
# AEP server
kubectl -n aep get deployment aep-server -o jsonpath='{.spec.template.spec.containers[0].image}'

# Data Cloud API
kubectl -n data-cloud get deployment data-cloud-api \
  -o jsonpath='{.spec.template.spec.containers[0].image}'

# YAPPC runtime
kubectl -n yappc get deployment yappc-runtime \
  -o jsonpath='{.spec.template.spec.containers[0].image}'
```

### Step 2: Find the previous image from rollout history

```bash
# AEP — show last 5 revisions
kubectl -n aep rollout history deployment/aep-server

# Data Cloud
kubectl -n data-cloud rollout history deployment/data-cloud-api

# YAPPC
kubectl -n yappc rollout history deployment/yappc-runtime
```

### Step 3: Confirm the rollback target revision

```bash
# Inspect a specific revision before rolling back
kubectl -n aep rollout history deployment/aep-server --revision=<N>
```

---

## 3. Execute the Rollback

### Option A: Kubernetes rollout undo (fastest)

Use when the immediately previous deployment was known-good:

```bash
# AEP
kubectl -n aep rollout undo deployment/aep-server
kubectl -n aep rollout status deployment/aep-server

# Data Cloud
kubectl -n data-cloud rollout undo deployment/data-cloud-api
kubectl -n data-cloud rollout status deployment/data-cloud-api

# YAPPC
kubectl -n yappc rollout undo deployment/yappc-runtime
kubectl -n yappc rollout status deployment/yappc-runtime
```

### Option B: Pin to a specific revision (when skipping more than one release)

```bash
# Rollback to a known-good specific revision
kubectl -n aep rollout undo deployment/aep-server --to-revision=<N>
kubectl -n aep rollout status deployment/aep-server
```

### Option C: Re-deploy a known-good image tag via CI

Use when the Kubernetes rollout history does not contain the target revision (e.g., history limit reached):

```bash
# Set image directly to the known-good tag
kubectl -n aep set image deployment/aep-server \
  aep-server=ghcr.io/ghatana/aep-server:<GOOD_TAG>
kubectl -n aep rollout status deployment/aep-server
```

---

## 4. Validate the Rollback

### Step 4: Confirm pods are running the previous image

```bash
# Verify image tag reverted
kubectl -n aep get pods -o jsonpath='{range .items[*]}{.spec.containers[0].image}{"\n"}{end}'
```

### Step 5: Run health checks against the restored deployment

```bash
# AEP health probe
curl -s https://aep.ghatana.internal/health | jq .status
# Expected: "healthy"

# Data Cloud health probe
curl -s https://data-cloud.ghatana.internal/health | jq .status
# Expected: "healthy"

# YAPPC runtime health probe
curl -s https://yappc.ghatana.internal/health | jq .status
# Expected: "healthy"
```

### Step 6: Verify error rate returns to baseline

Monitor for 10 minutes post-rollback:

```promql
# AEP error rate — target < 1%
rate(aep_http_requests_errors_total[5m]) / rate(aep_http_requests_total[5m])

# Data Cloud API error rate
rate(datacloud_api_requests_errors_total[5m]) / rate(datacloud_api_requests_total[5m])
```

**Pass criteria**: Error rate drops below 1% within 10 minutes of rollback completion.

### Step 7: Smoke test critical flows

```bash
# AEP event ingestion
curl -s -X POST https://aep.ghatana.internal/api/v1/events \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: smoke-test-tenant" \
  -d '{"type":"smoke.test","payload":{}}' | jq .success
# Expected: true

# Data Cloud query
curl -s https://data-cloud.ghatana.internal/api/v1/collections \
  -H "X-Tenant-ID: smoke-test-tenant" | jq '.collections | length'
# Expected: integer ≥ 0

# YAPPC project creation
curl -s -X POST https://yappc.ghatana.internal/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: smoke-test-tenant" \
  -d '{"name":"smoke-test-rollback","description":"Rollback validation"}' | jq .id
# Expected: non-null project ID
```

---

## 5. Communication and Documentation

### Notify stakeholders

```
1. Update the status page: "Rollback in progress" → "Rollback complete, service restored"
2. Post to #engineering: "Deployment for [service] rolled back to [tag] — monitoring for 30 min"
3. Notify the release engineer who deployed the rolled-back version
```

### Document in the post-mortem

Record the following in the incident post-mortem:

- Deployment time
- First alert / detection time
- Rollback decision time
- Rollback completion time  
- Restored error rate (with time to recovery)
- Root cause (if known at time of rollback)
- Whether schema migrations were involved

---

## 6. Blocked Rollback Scenarios

### Schema migration was applied with the failed deployment

**Do not rollback automatically.** Schema migrations require data-safe reversal:

1. Page the Data Platform lead immediately
2. Assess whether the migration is additive (safe to leave in place) or destructive
3. If additive: rollback the application code only, leave the schema in place
4. If destructive: follow the schema rollback procedure documented in the relevant ADR

### The previous image is no longer available in the container registry

```bash
# Check image availability
docker manifest inspect ghcr.io/ghatana/aep-server:<GOOD_TAG>
```

If the image was garbage-collected:
1. Trigger a CI rebuild from the known-good Git commit hash
2. Wait for the build to complete (typically < 10 minutes)
3. Deploy the rebuilt image using Option C above

---

## 7. Prevention and Post-Rollback Actions

After a rollback, the following must happen before the next release attempt:

- [ ] Root cause identified and documented
- [ ] Failing tests that should have caught this added to the pipeline
- [ ] Deployment canary analysis reviewed and hardened if applicable
- [ ] Schema migration (if any) validated to be safely idempotent on re-apply
- [ ] Post-mortem opened with assigned owner and 5-day completion target

---

## 8. Related Documents

- [Disaster Recovery Failover Runbook](dr-failover-runbook.md)
- [AEP Degraded Mode Runbook](aep-degraded-mode-runbook.md)
- [AEP SLO Dashboard](../../monitoring/grafana/dashboards/aep-slos.json)
- [Incident runbooks overview](README.md)
