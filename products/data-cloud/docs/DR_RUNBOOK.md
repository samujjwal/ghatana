# Data-Cloud — Disaster Recovery Runbook

> **Version**: 1.0.0  
> **Last Updated**: 2026-01-19  
> **Owner**: Data-Cloud Platform Team  
> **Audience**: On-call engineers, SREs, and platform leads

---

## Table of Contents

1. [Incident Severity & SLOs](#1-incident-severity--slos)
2. [On-Call Escalation](#2-on-call-escalation)
3. [Pre-Recovery Checklist](#3-pre-recovery-checklist)
4. [ClickHouse — Backup Restore](#4-clickhouse--backup-restore)
5. [PostgreSQL — Point-in-Time Recovery](#5-postgresql--point-in-time-recovery)
6. [Kafka — Consumer Group Recovery](#6-kafka--consumer-group-recovery)
7. [OpenSearch — Index Restore](#7-opensearch--index-restore)
8. [Ceph/MinIO Blob Storage Recovery](#8-cephminio-blob-storage-recovery)
9. [Kubernetes — Pod & Deployment Recovery](#9-kubernetes--pod--deployment-recovery)
10. [Full-Cluster Failover](#10-full-cluster-failover)
11. [Post-Recovery Verification](#11-post-recovery-verification)
12. [Runbook Maintenance](#12-runbook-maintenance)

---

## 1. Incident Severity & SLOs

| Severity | Definition | Response Target | Resolution Target |
|----------|-----------|----------------|--------------------|
| **P0 — Critical** | Complete data-cloud service outage; data loss occurring | < 5 min page | RTO ≤ 1 h |
| **P1 — High** | Single tier degraded (ClickHouse/Kafka/Postgres); partial data unavailability | < 15 min page | RTO ≤ 4 h |
| **P2 — Medium** | Non-critical feature degraded; no data loss | < 30 min notify | RTO ≤ 24 h |
| **P3 — Low** | Performance degradation only | Next business day | Best effort |

**RPO targets**:
- ClickHouse time-series:  ≤ 6 h (incremental backup cadence)
- PostgreSQL entity store: ≤ 5 min (continuous WAL archiving)
- Kafka event log:          ≤ 0 h (replicated; no backup restore required for broker failures)
- OpenSearch indexes:       ≤ 24 h (daily snapshot)
- Ceph blob objects:        ≤ 24 h (daily remote sync)

---

## 2. On-Call Escalation

```
Level 1 — On-call engineer (page immediately)
Level 2 — Data-Cloud tech lead (escalate at T+30 min if unresolved)
Level 3 — VP Engineering / CTO (escalate at T+60 min for P0)
```

> **Note**: Replace with your actual contacts in your incident management system (PagerDuty, OpsGenie, etc.).

---

## 3. Pre-Recovery Checklist

Before executing any recovery procedure, complete all items:

- [ ] Create an incident ticket and write a brief impact statement.
- [ ] Notify the #incidents Slack channel with severity, scope, and your name.
- [ ] Confirm you have `kubectl` access to the target cluster with sufficient RBAC.
- [ ] Snapshot the current state: `kubectl get all -n data-cloud > /tmp/state-$(date +%s).txt`
- [ ] Identify the last known-good backup timestamp.
- [ ] Ensure you are **not** operating on production unless absolutely necessary — prefer DR environment first.
- [ ] Assign a scribe to log every command executed with timestamps.

---

## 4. ClickHouse — Backup Restore

### 4.1 Backup inventory

Backups are created by the `clickhouse-backup-daily` and `clickhouse-backup-incremental` CronJobs in `data-cloud` namespace (manifest: `products/data-cloud/k8s/clickhouse-backup-cronjob.yaml`).

```bash
# List available remote backups
kubectl exec -n data-cloud deploy/data-cloud \
  -- clickhouse-backup list remote
```

Sample output:
```
2026-01-19T02:00:00Z   full   data-cloud/2026-01-19T02-00-00Z.tar
2026-01-19T06:00:00Z   diff   data-cloud/2026-01-19T06-00-00Z.tar
2026-01-19T12:00:00Z   diff   data-cloud/2026-01-19T12-00-00Z.tar
```

### 4.2 Full restore procedure

```bash
BACKUP_NAME="2026-01-19T02-00-00Z"   # replace with your target

# 1. Stop ingestion to avoid write conflicts
kubectl scale deploy/data-cloud -n data-cloud --replicas=0

# 2. Download backup from remote S3 (Ceph RGW)
kubectl exec -n data-cloud \
  $(kubectl get pod -n data-cloud -l app.kubernetes.io/name=clickhouse-backup -o name | head -1) \
  -- clickhouse-backup download "$BACKUP_NAME"

# 3. Restore to ClickHouse
kubectl exec -n data-cloud \
  $(kubectl get pod -n data-cloud -l app.kubernetes.io/name=clickhouse-backup -o name | head -1) \
  -- clickhouse-backup restore --rm "$BACKUP_NAME"

# 4. Verify table row counts
kubectl exec -n data-cloud deploy/clickhouse \
  -- clickhouse-client --query "SELECT table, count() FROM system.tables WHERE database='datacloud' GROUP BY table"

# 5. Restart data-cloud
kubectl scale deploy/data-cloud -n data-cloud --replicas=4
```

### 4.3 Incremental restore (restore to a point between full backups)

```bash
# Restore full backup first
FULL="2026-01-19T02-00-00Z"
DIFF="2026-01-19T12-00-00Z"

kubectl exec -n data-cloud <clickhouse-backup-pod> -- clickhouse-backup download "$FULL"
kubectl exec -n data-cloud <clickhouse-backup-pod> -- clickhouse-backup restore --rm "$FULL"

kubectl exec -n data-cloud <clickhouse-backup-pod> -- clickhouse-backup download "$DIFF"
kubectl exec -n data-cloud <clickhouse-backup-pod> -- clickhouse-backup restore "$DIFF"
```

### 4.4 Verification

```sql
-- Run via clickhouse-client
SELECT count() FROM datacloud.events WHERE toDate(timestamp) = today();
SELECT count() FROM datacloud.entity_audit_log;
```

Expected: counts consistent with pre-incident monitoring graphs.

---

## 5. PostgreSQL — Point-in-Time Recovery

The warm-tier entity store uses PostgreSQL with continuous WAL archiving to S3 (configured by the DBA team; connection string in `data-cloud` namespace Secret `data-cloud-db-credentials`).

### 5.1 Identify target recovery time

```bash
# Find the last healthy checkpoint from Prometheus (Grafana → Data-Cloud → DB dashboard)
# Note the timestamp T just before the incident manifested.
```

### 5.2 PITR restore procedure

```bash
TARGET_TIME="2026-01-19 11:55:00 UTC"   # replace with your T

# 1. Stop data-cloud to prevent writes
kubectl scale deploy/data-cloud -n data-cloud --replicas=0

# 2. Scale down any other writers (AEP, gateway)
kubectl scale deploy/aep -n aep --replicas=0

# 3. Restore PostgreSQL (example using Bitnami PostgreSQL chart with WAL-G or pg_restore):
#    -- This step is cluster-specific; consult your PostgreSQL operator documentation.
#    For CloudNativePG:
kubectl apply -f - <<EOF
apiVersion: postgresql.cnpg.io/v1
kind: Cluster
metadata:
  name: data-cloud-db-pitr
  namespace: data-cloud
spec:
  instances: 1
  bootstrap:
    recovery:
      source: data-cloud-db
      recoveryTarget:
        targetTime: "${TARGET_TIME}"
  externalClusters:
    - name: data-cloud-db
      barmanObjectStore:
        destinationPath: s3://ghatana-pg-wal/data-cloud/
        s3Credentials:
          accessKeyId:
            name: data-cloud-db-backup-credentials
            key: ACCESS_KEY_ID
          secretAccessKey:
            name: data-cloud-db-backup-credentials
            key: SECRET_ACCESS_KEY
EOF

# 4. Wait for the PITR cluster to reach Running state
kubectl wait --for=condition=Ready cluster/data-cloud-db-pitr -n data-cloud --timeout=30m

# 5. Promote and update the service endpoint to point to the PITR instance.
#    Update Secret data-cloud-db-credentials with the new host.

# 6. Scale data-cloud back up
kubectl scale deploy/data-cloud -n data-cloud --replicas=4
```

### 5.3 Verification

```bash
kubectl exec -n data-cloud <data-cloud-pod> \
  -- psql "$DATABASE_URL" -c "SELECT count(*) FROM entities WHERE tenant_id IS NOT NULL;"
```

---

## 6. Kafka — Consumer Group Recovery

Kafka brokers (production: `kafka-prod.kafka.svc.cluster.local`) are replicated with `replicationFactor=3`. No data backup is required for broker node failures; replication provides automatic recovery.

### 6.1 Single broker failure (automatic)

Kafka handles broker failures automatically via partition leader election. Monitor via:

```bash
# Check under-replicated partitions (should be 0)
kubectl exec -n kafka kafka-prod-0 \
  -- kafka-topics.sh --bootstrap-server localhost:9092 \
     --describe --under-replicated-partitions
```

### 6.2 Consumer group lag spike

If `data-cloud` consumer group has fallen behind after an outage:

```bash
# List consumer groups
kubectl exec -n kafka kafka-prod-0 \
  -- kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list | grep data-cloud

# Describe the lagging group
kubectl exec -n kafka kafka-prod-0 \
  -- kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group data-cloud-event-consumer --describe

# Reset to latest (discards un-processed events after an agreed cutover)
kubectl exec -n kafka kafka-prod-0 \
  -- kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group data-cloud-event-consumer \
     --topic ghatana.events \
     --reset-offsets --to-latest --execute
```

> **Warning**: `--to-latest` discards any events that accumulated while the consumer was stopped. Only use this if data loss in the event log is acceptable or the events were already processed via another path.

### 6.3 Reset to a specific timestamp

```bash
TARGET_EPOCH_MS=1737302100000   # Unix epoch milliseconds

kubectl exec -n kafka kafka-prod-0 \
  -- kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
     --group data-cloud-event-consumer \
     --topic ghatana.events \
     --reset-offsets --to-datetime "$(date -d @$((TARGET_EPOCH_MS/1000)) --utc +'%Y-%m-%dT%H:%M:%S.000Z')" \
     --execute
```

---

## 7. OpenSearch — Index Restore

Production OpenSearch cluster: `opensearch-prod-cluster-master.opensearch.svc.cluster.local`.

### 7.1 List available snapshots

```bash
curl -s -u "$OPENSEARCH_USER:$OPENSEARCH_PASSWORD" \
  "https://opensearch-prod-cluster-master.opensearch.svc.cluster.local:9200/_snapshot/s3-backup/_all?pretty"
```

### 7.2 Restore a specific snapshot

```bash
SNAPSHOT_NAME="data-cloud-snapshot-20260119"   # replace

# Close the target index first (required before restore)
curl -s -X POST -u "$OPENSEARCH_USER:$OPENSEARCH_PASSWORD" \
  "https://opensearch-prod-cluster-master.opensearch.svc.cluster.local:9200/entities/_close"

# Restore
curl -s -X POST -u "$OPENSEARCH_USER:$OPENSEARCH_PASSWORD" \
  -H "Content-Type: application/json" \
  "https://opensearch-prod-cluster-master.opensearch.svc.cluster.local:9200/_snapshot/s3-backup/$SNAPSHOT_NAME/_restore" \
  -d '{"indices": "entities,events", "ignore_unavailable": true}'

# Monitor restore progress
curl -s -u "$OPENSEARCH_USER:$OPENSEARCH_PASSWORD" \
  "https://opensearch-prod-cluster-master.opensearch.svc.cluster.local:9200/_recovery?pretty"
```

---

## 8. Ceph/MinIO Blob Storage Recovery

Production Ceph RGW: `https://rook-ceph-rgw-prod.rook-ceph.svc.cluster.local`  
Bucket: `ghatana-data-cloud-prod`

### 8.1 Check object availability

```bash
# Using mc (MinIO Client)
mc alias set ceph-prod "$CEPH_ENDPOINT" "$ACCESS_KEY" "$SECRET_KEY"
mc ls ceph-prod/ghatana-data-cloud-prod/
```

### 8.2 Restore objects from versioning or replication

If Ceph multi-site replication is configured:

```bash
# List object versions
mc ls --versions ceph-prod/ghatana-data-cloud-prod/<tenant>/<collection>/

# Restore a deleted object (revert to previous version)
mc cp --vid <VERSION_ID> \
  ceph-prod/ghatana-data-cloud-prod/<tenant>/<collection>/<entityId>.json \
  ceph-prod/ghatana-data-cloud-prod/<tenant>/<collection>/<entityId>.json
```

### 8.3 Import from daily S3 sync

If multi-site is unavailable, restore from the S3 daily sync bucket:

```bash
mc mirror s3-dr/ghatana-data-cloud-backup/ ceph-prod/ghatana-data-cloud-prod/ --newer-than 48h
```

---

## 9. Kubernetes — Pod & Deployment Recovery

### 9.1 Pod crash-looping

```bash
# Identify crashing pods
kubectl get pods -n data-cloud --field-selector=status.phase!=Running

# Tail logs from most recent terminated container
kubectl logs -n data-cloud <pod-name> --previous

# Force restart a deployment
kubectl rollout restart deploy/data-cloud -n data-cloud

# Check rollout status
kubectl rollout status deploy/data-cloud -n data-cloud --timeout=5m
```

### 9.2 Rollback a bad deployment

```bash
# List revision history
kubectl rollout history deploy/data-cloud -n data-cloud

# Rollback to previous revision
kubectl rollout undo deploy/data-cloud -n data-cloud

# Or rollback to a specific revision
kubectl rollout undo deploy/data-cloud -n data-cloud --to-revision=7
```

### 9.3 Re-apply Helm chart from Argo CD

```bash
# Force Argo CD to sync and self-heal
argocd app sync data-cloud --force

# Or via kubectl (triggers Argo CD reconciliation)
kubectl annotate app data-cloud -n argocd \
  argocd.argoproj.io/refresh=hard --overwrite
```

### 9.4 HPA not scaling

```bash
# Check HPA status
kubectl describe hpa data-cloud -n data-cloud

# Manually scale if HPA is broken
kubectl scale deploy/data-cloud -n data-cloud --replicas=8

# Verify metrics server is healthy
kubectl top pods -n data-cloud
```

---

## 10. Full-Cluster Failover

Execute only for a P0 datacenter-level failure. Coordinate with the infrastructure team before starting.

```
┌──────────────────────────────────────────────────────────────┐
│  Failover sequence (estimated total time: 45 min)            │
│                                                              │
│  1. Convene P0 bridge call (5 min)                           │
│  2. Confirm primary cluster is unrecoverable (5 min)         │
│  3. Activate DNS cutover to DR cluster (5 min)               │
│  4. Verify DR ClickHouse restore from last backup (20 min)   │
│  5. Verify PostgreSQL PITR on DR cluster (10 min)            │
│  6. Run post-recovery smoke tests (5 min)                    │
└──────────────────────────────────────────────────────────────┘
```

```bash
# Step 3 — DNS cutover (example using external-dns annotation)
kubectl patch ingress data-cloud -n data-cloud \
  --patch '{"spec":{"rules":[{"host":"data-cloud.ghatana.io","http":{"paths":[{"path":"/","pathType":"Prefix","backend":{"service":{"name":"data-cloud-dr","port":{"number":8080}}}}]}}]}}'
```

---

## 11. Post-Recovery Verification

After any recovery procedure, verify the following before marking the incident resolved:

### 11.1 Health checks

```bash
# data-cloud REST health probe
curl -sf https://data-cloud.ghatana.io/health | jq .

# gRPC reflection (confirms the server is responding)
grpcurl -plaintext data-cloud.ghatana.io:9090 list

# Internal Kubernetes readiness
kubectl get endpoints data-cloud -n data-cloud
```

### 11.2 Smoke test — entity CRUD

```bash
# Create
curl -X POST https://data-cloud.ghatana.io/api/v1/tenants/smoke-tenant/collections/test/entities \
  -H "Content-Type: application/json" \
  -d '{"name":"dr-smoke-test","ts":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'

# Read back
curl https://data-cloud.ghatana.io/api/v1/tenants/smoke-tenant/collections/test/entities?limit=1
```

### 11.3 Metrics validation

Open Grafana → **Data-Cloud Overview** dashboard and verify:

- `data_cloud_http_requests_total` counter is increasing.
- `data_cloud_storage_error_rate` is < 0.1%.
- `data_cloud_p99_latency_seconds` is < 2 s.
- ClickHouse `system.metrics.Query` count is non-zero.
- Kafka consumer lag metric `kafka_consumergroup_lag` is < 10k.

### 11.4 Incident closure checklist

- [ ] Root cause identified and documented.
- [ ] All services confirmed healthy via health endpoint and Grafana.
- [ ] Smoke test passed.
- [ ] PagerDuty incident marked resolved.
- [ ] Post-mortem scheduled within 48 hours.
- [ ] Action items filed in GitHub Issues with `runbook:update` label if procedure needs improvement.

---

## 12. Runbook Maintenance

- **Review cadence**: Quarterly, or after every P0/P1 incident.
- **Test cadence**: DR restore drill every 6 months — restore ClickHouse from backup in a staging cluster and run the full post-recovery verification checklist.
- **Owner**: Whoever resolves the next P0 incident is responsible for updating any inaccurate steps.
- **Change process**: Submit a PR updating this file, tagged `docs:dr-runbook`. Require approval from the platform lead.
