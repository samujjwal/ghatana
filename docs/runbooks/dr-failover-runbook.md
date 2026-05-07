# Disaster Recovery Failover Runbook

**Document type:** Operational runbook  
**Product scope:** All products (AEP, Data Cloud, YAPPC)  
**Severity classification:** P0 — complete service loss or multi-region failure  
**Last updated:** 2026-05-02  
**Owner:** Platform SRE  
**Review cadence:** Quarterly and after every DR drill

---

## Purpose

This runbook guides on-call engineers through a full disaster recovery (DR) failover when the primary region becomes unreachable, critically degraded, or must be evacuated due to infrastructure failure. It covers AEP pipeline continuity, Data Cloud tenant data integrity, and YAPPC project execution safety.

> **Prerequisite**: Before executing this runbook, confirm with at least two leads that primary-region recovery is not possible within the SLO window. Do not initiate failover on the basis of a single monitoring alert.

---

## 1. Decision Criteria for Initiating DR Failover

Trigger this runbook when ALL of the following are true:

| Check | Signal | Source |
|---|---|---|
| Primary region unavailable | `up{job="aep-server"} == 0` for ≥ 5 minutes | Prometheus |
| Health probe failing | `/health` returns non-2xx for ≥ 3 consecutive checks | Synthetic monitor |
| Data Cloud API unreachable | `datacloud_api_requests_total` has no new samples for ≥ 5 min | Prometheus |
| On-call lead confirms outage | Verbal or Slack confirmation from a second lead | Communication |

**Do NOT trigger DR failover for:**
- Single pod failures (handled by Kubernetes pod restart)
- Temporary elevated error rates below 25%
- Deployment rollout failures (use the [Rollback Runbook](rollback-runbook.md) instead)

---

## 2. Immediate Response (T+0 to T+10 minutes)

### Step 1: Confirm scope and page additional responders

```
Incident commander: Page the Data Platform lead, AEP lead, and YAPPC lead.
Declare a P0 incident in PagerDuty. Open the Slack channel #incident-dr-active.
```

### Step 2: Verify current state of all DR replicas

```bash
# Verify DR region Data Cloud replica lag
# Expected: replica lag < 5 minutes (RPO target)
kubectl --context=dr-region get pods -n data-cloud
kubectl --context=dr-region exec -n data-cloud deploy/data-cloud-api \
  -- curl -s localhost:8080/health/replication | jq .

# Verify AEP DR replicas are healthy
kubectl --context=dr-region get pods -n aep
kubectl --context=dr-region exec -n aep deploy/aep-server \
  -- curl -s localhost:8080/health | jq .
```

**Expected output**: All pods `Running`, replication lag < 5 minutes.  
**If lag > 5 minutes**: Notify stakeholders of data loss risk before proceeding.

### Step 3: Capture a snapshot of primary state (if accessible)

```bash
# If primary is accessible but degraded, take a snapshot before failover
kubectl --context=primary exec -n data-cloud deploy/data-cloud-api \
  -- curl -s -X POST localhost:8080/admin/snapshot | jq .

# Record the snapshot ID for audit trail
echo "DR_SNAPSHOT_ID=$(date -u +%Y%m%dT%H%M%SZ)" >> /tmp/dr-incident.env
```

---

## 3. Failover Execution (T+10 to T+30 minutes)

### Step 4: Promote DR region Data Cloud replicas to primary

```bash
# Promote replica — this is irreversible until re-sync completes
kubectl --context=dr-region exec -n data-cloud deploy/data-cloud-api \
  -- curl -s -X POST localhost:8080/admin/promote-replica | jq .

# Verify promotion completed
kubectl --context=dr-region exec -n data-cloud deploy/data-cloud-api \
  -- curl -s localhost:8080/health | jq .replication.isPrimary
# Expected: true
```

### Step 5: Switch AEP DNS to point to DR region

```bash
# Update DNS record (TTL should already be ≤ 60s for DR-prepared zones)
# Replace with your DNS provider CLI or Terraform apply
aws route53 change-resource-record-sets \
  --hosted-zone-id $HOSTED_ZONE_ID \
  --change-batch file://dr-dns-failover.json

# Verify DNS propagation (wait up to 60 seconds)
for i in 1 2 3 4 5; do
  dig +short aep.ghatana.internal && break
  sleep 15
done
```

### Step 6: Scale up YAPPC execution nodes in DR region

```bash
# YAPPC project execution requires minimum 3 nodes in DR region
kubectl --context=dr-region scale deployment/yappc-runtime \
  --replicas=3 -n yappc

# Verify pods are running
kubectl --context=dr-region rollout status deployment/yappc-runtime -n yappc
```

### Step 7: Resume AEP event ingestion in DR region

```bash
# Un-pause AEP event processing queue in DR region
kubectl --context=dr-region exec -n aep deploy/aep-server \
  -- curl -s -X POST localhost:8080/admin/resume-ingestion | jq .

# Verify ingestion is running
kubectl --context=dr-region exec -n aep deploy/aep-server \
  -- curl -s localhost:8080/health | jq .components.eventIngestion
# Expected: "healthy"
```

---

## 4. Validation and Recovery Confirmation (T+30 to T+60 minutes)

### Step 8: Run DR validation checklist

Execute each validation and confirm pass/fail:

- [ ] `GET /health` on AEP DR endpoint returns `200 {"status": "healthy"}`
- [ ] `GET /health` on Data Cloud DR endpoint returns `200 {"status": "healthy"}`
- [ ] `POST /api/v1/events` accepts a test event and returns `200` with `"success": true`
- [ ] `GET /api/v1/agents` returns expected agent list (not empty)
- [ ] YAPPC project creation succeeds: `POST /api/v1/projects` returns `201`
- [ ] Data Cloud tenant isolation confirmed: tenant A cannot read tenant B data
- [ ] Replication lag is now `0` (DR is the primary, no longer replicating)

### Step 9: Notify stakeholders and update status page

```
Update the status page to: "Service restored — DR region active"
Notify #engineering-leadership, #product-leads, and affected tenants
Record RTO achieved: T+{elapsed} minutes (target: <60 min)
```

### Step 10: Begin post-incident timeline capture

```
Open the post-mortem template at docs/runbooks/post-mortem-template.md
Record: incident start time, detection time, failover start, service restored time
```

---

## 5. Post-Failover Operations

### Monitoring during DR operation

Keep these dashboards open continuously while running in DR mode:

- **Grafana**: AEP SLO dashboard — `monitoring/grafana/dashboards/aep-slos.json`
- **Grafana**: YAPPC SLO dashboard — `monitoring/grafana/dashboards/yappc/yappc-slos.json`
- **Prometheus**: `aep_pipeline_availability_ratio`, `datacloud_api_availability_ratio`

### Alerting during DR operation

Confirm these alerts are firing correctly in the DR region:

```promql
# AEP availability must stay above 99.5%
aep_pipeline_availability_ratio < 0.995

# Data Cloud replication lag (should be 0 when DR is primary)
datacloud_replica_lag_seconds > 0
```

### Re-syncing primary region (when recovered)

> **Do NOT perform these steps during an active incident.** Wait until the primary region is fully verified healthy.

```bash
# After primary region recovery, sync data from DR (now acting as primary) back to primary
kubectl --context=primary exec -n data-cloud deploy/data-cloud-api \
  -- curl -s -X POST localhost:8080/admin/sync-from-dr | jq .

# Monitor sync progress
kubectl --context=primary exec -n data-cloud deploy/data-cloud-api \
  -- curl -s localhost:8080/health/replication | jq .syncProgress
```

---

## 6. Contact and Escalation

| Role | Contact | Escalation trigger |
|---|---|---|
| Primary on-call | PagerDuty rotation | All P0 incidents |
| Data Platform lead | See engineering directory | DR failover decision |
| AEP lead | See engineering directory | AEP pipeline issues |
| YAPPC lead | See engineering directory | YAPPC execution issues |
| Infrastructure lead | See engineering directory | DNS / Kubernetes issues |

---

## 7. Related Documents

- [Rollback Runbook](rollback-runbook.md)
- [Degraded Mode Runbook](aep-degraded-mode-runbook.md)
- [AEP SLO Dashboard](../../monitoring/grafana/dashboards/aep-slos.json)
- [YAPPC SLO Dashboard](../../monitoring/grafana/dashboards/yappc/yappc-slos.json)
- [DisasterRecoveryE2ETest](../../products/data-cloud/extensions/plugins/src/test/java/com/ghatana/datacloud/plugins/dr/DisasterRecoveryE2ETest.java)
- [Incident runbooks overview](README.md)
