# Data-Cloud Canary Rollout & Rollback Playbook

> **Version**: 1.0.0 (2026-01 — Sprint 6 DC-E6)  
> **Owner**: Data-Cloud Platform Team  
> **Status**: Active  

## Overview

This playbook governs progressive delivery of new Data-Cloud releases using a
**canary rollout** strategy: traffic is shifted incrementally across four stages,
with automated go/no-go checks at each stage boundary.  If any threshold is
breached, an automated or manual **rollback** is triggered within the defined
SLOs.

---

## 1. Release Stages

| Stage     | Traffic to new version | Wait window | Auto-promote? |
|-----------|----------------------|-------------|---------------|
| Canary 5  | 5%                   | 15 minutes  | Yes (if green)|
| Canary 25 | 25%                  | 30 minutes  | Yes (if green)|
| Canary 50 | 50%                  | 30 minutes  | No — manual approval required |
| Full      | 100%                 | 60 minutes  | No — manual approval required |

> **Rationale**: The 50% → 100% step requires manual approval to prevent the
> blast radius of an automated full production rollout.

---

## 2. Go/No-Go Criteria (per stage)

A stage is **green** (eligible for auto-promotion) when ALL of the following hold
over the trailing wait window for the canary pod set:

| Signal                                   | Green threshold         | Source                        |
|------------------------------------------|-------------------------|-------------------------------|
| HTTP 5xx error rate                      | < 0.1%                  | `dc.http.server.errors[5xx]`  |
| P99 request latency                      | < 500 ms                | `dc.http.server.latency_ms`   |
| AI recommendation fallback rate          | < 30%                   | `dc.ai.recommendation.requests[fallback=true]` |
| Voice intent classification failure rate | < 5%                    | `dc.voice.intent.classification.errors`        |
| JVM heap usage                           | < 85% of max            | JVM metrics via OpenTelemetry |
| Health endpoint                          | `GET /ready` → 200      | `run-smoke-e2e.sh`            |
| Primary smoke checks                     | 0 FAIL (WARN acceptable)| `run-smoke-e2e.sh --warn-only`|

A stage is **red** (rollback trigger) when ANY of the following occur:

| Condition                                             | Threshold              |
|-------------------------------------------------------|------------------------|
| HTTP 5xx error rate                                   | > 1%                   |
| P99 request latency                                   | > 2 000 ms             |
| `GET /health` returns non-200                         | 1 occurrence           |
| JVM heap usage                                        | > 95% of max           |
| AI recommendation fallback rate                       | > 50%                  |
| Voice intent classification failure rate              | > 20%                  |
| Any data-integrity audit alarm from event store       | 1 occurrence           |

---

## 3. Step-by-Step Rollout Procedure

### Prerequisites

```bash
# All commands assume kubectl context is set to the target cluster
kubectl config use-context data-cloud-prod     # or staging

# Verify current deployment
kubectl get deploy data-cloud -n data-cloud -o yaml | grep image:
```

### 3.1 Deploy Canary (5%)

```bash
# Set the canary image tag
CANARY_TAG="v$(cat products/data-cloud/version.txt)-$(git rev-parse --short HEAD)"

# Apply canary deployment with 5% weight
kubectl apply -f k8s/data-cloud-canary.yaml \
  --patch "{\"spec\":{\"selector\":{\"matchLabels\":{\"version\":\"${CANARY_TAG}\"}}}}"

# Configure traffic split (Istio VirtualService or Nginx Ingress weight annotation)
kubectl patch virtualservice data-cloud -n data-cloud --type=json \
  -p="[{'op':'replace','path':'/spec/http/0/route/0/weight','value':95},
       {'op':'replace','path':'/spec/http/0/route/1/weight','value':5}]"
```

### 3.2 Monitor (15 minutes)

```bash
# Stream canary error rate
kubectl top pods -l version="${CANARY_TAG}" -n data-cloud

# Run smoke validation against canary endpoint
DC_BASE_URL="https://canary.data-cloud.internal" \
DC_API_TOKEN="${STAGING_API_TOKEN}" \
bash products/data-cloud/scripts/run-smoke-e2e.sh --warn-only

# Check Prometheus canary metrics
# dashboard: http://grafana.internal/d/dc-canary
```

### 3.3 Promote to 25%

```bash
kubectl patch virtualservice data-cloud -n data-cloud --type=json \
  -p="[{'op':'replace','path':'/spec/http/0/route/0/weight','value':75},
       {'op':'replace','path':'/spec/http/0/route/1/weight','value':25}]"
```

### 3.4 Promote to 50% (manual approval required)

```bash
# Requires SRE sign-off in the deployment PR or Slack approval thread
# Record the approval in the audit log:
echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] 50% approval by ${APPROVER}" \
  >> products/data-cloud/docs/CANARY_AUDIT_LOG.txt

kubectl patch virtualservice data-cloud -n data-cloud --type=json \
  -p="[{'op':'replace','path':'/spec/http/0/route/0/weight','value':50},
       {'op':'replace','path':'/spec/http/0/route/1/weight','value':50}]"
```

### 3.5 Full Rollout (100%, manual approval required)

```bash
echo "[$(date -u +'%Y-%m-%dT%H:%M:%SZ')] Full rollout approval by ${APPROVER}" \
  >> products/data-cloud/docs/CANARY_AUDIT_LOG.txt

# Replace the stable deployment image
kubectl set image deployment/data-cloud \
  data-cloud="${CANARY_TAG}" \
  -n data-cloud --record

# Scale down canary deployment
kubectl delete deployment data-cloud-canary -n data-cloud

# Reset traffic weights to 100% stable
kubectl patch virtualservice data-cloud -n data-cloud --type=json \
  -p="[{'op':'replace','path':'/spec/http/0/route/0/weight','value':100}]"
```

---

## 4. Rollback Procedure

### 4.1 Immediate rollback (automated trigger)

The canary controller (Argo Rollouts or Flagger) will attempt an automated
rollback if any red-threshold metric fires.  Verify with:

```bash
kubectl argo rollout get rollout data-cloud -n data-cloud
```

### 4.2 Manual rollback

If automated rollback has not fired within 2 minutes of detecting a red signal,
execute the following:

```bash
# Step 1: Shift all traffic back to stable
kubectl patch virtualservice data-cloud -n data-cloud --type=json \
  -p="[{'op':'replace','path':'/spec/http/0/route/0/weight','value':100},
       {'op':'replace','path':'/spec/http/0/route/1/weight','value':0}]"

# Step 2: Scale down canary pods
kubectl scale deployment/data-cloud-canary \
  --replicas=0 -n data-cloud

# Step 3: Validate stable is healthy
DC_BASE_URL="https://data-cloud.internal" \
bash products/data-cloud/scripts/run-smoke-e2e.sh

# Step 4: Delete canary deployment resources
kubectl delete deployment data-cloud-canary -n data-cloud
```

**Expected recovery time: < 2 minutes** from rollback initiation.

### 4.3 Post-rollback checklist

- [ ] Confirm `GET /health` → 200 on all stable pods
- [ ] Confirm error rate returns to baseline (< 0.1%) within 5 minutes
- [ ] Run full smoke matrix: `bash products/data-cloud/scripts/run-smoke-e2e.sh`
- [ ] Open incident ticket with `severity: SEV-2` label
- [ ] Capture canary metrics snapshot from Grafana for post-mortem
- [ ] Notify data-cloud-oncall Slack channel: `@data-cloud-oncall rollback complete`

---

## 5. Helm Chart References

| Resource | File |
|---|---|
| Stable deployment | `products/data-cloud/helm/templates/deployment.yaml` |
| Canary deployment | `products/data-cloud/helm/templates/deployment-canary.yaml` |
| VirtualService | `products/data-cloud/k8s/virtual-service.yaml` |
| HPA | `products/data-cloud/k8s/hpa.yaml` |

---

## 6. SLOs and Error Budget

| SLO | Target | Measurement window |
|-----|--------|--------------------|
| Availability (5xx rate) | ≥ 99.9% | 30-day rolling |
| P99 latency < 500 ms | ≥ 99.0% | 30-day rolling |
| Smoke check pass rate | ≥ 100% | Every deployment |

If a canary rollout consumes more than **5% of the monthly error budget**, it is
automatically flagged for SRE review and the Canary 50% → Full promotion is
blocked until the budget is restored.

---

## 7. Contacts & Escalation

| Role | Contact | Escalation SLA |
|------|---------|----------------|
| On-call SRE | `#data-cloud-oncall` | 15 min (SEV-1), 1 hr (SEV-2) |
| Release Manager | `@dc-release-manager` | Required for 50% and Full approvals |
| Platform Security | `#platform-security` | Required if data-integrity alarm fires |
