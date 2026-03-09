# Software-Org Incident Response Runbook

**Version:** 1.0.0  
**Last Updated:** 2025-11-16  
**Maintenance:** Update when new scenarios identified

---

## Quick Reference

| Scenario | Severity | Response Time | Escalation |
|---|---|---|---|
| High Event Processing Latency | P2 | 15 min | Page infrastructure |
| Event Processing Failures | P1 | 5 min | Page on-call + infrastructure |
| Multi-Tenant Isolation Breach | P0 | 1 min | Executive escalation |
| Deployment Failure | P2 | 10 min | Page infrastructure + SRE |
| Database Unavailable | P1 | 2 min | Page DBA + infrastructure |
| Security Breach Suspected | P0 | 1 min | Security team + executive |

---

## 1. High Event Processing Latency

### Detection

**Alert:** `software_org.event.processing_duration_seconds > 10ms (p99)`  
**Dashboard:** Grafana → Software-Org → Event Processing

### Diagnosis

```bash
# 1. Check pod resource usage
kubectl top pods -n software-org

# 2. Review event handler logs
kubectl logs -n software-org -l app=software-org-api --tail=200 | grep -i latency

# 3. Check EventCloud lag
kafkacat -b eventcloud:9092 -g software-org-event-processor -d | grep lag

# 4. Query slow events (database)
SELECT event_id, event_type, processing_time_ms 
FROM software_org.event_log 
WHERE processing_time_ms > 10 
ORDER BY processing_time_ms DESC 
LIMIT 10;

# 5. Check database query performance
EXPLAIN ANALYZE
SELECT * FROM software_org.kpi_snapshots 
WHERE recorded_at > NOW() - INTERVAL '1 hour';
```

### Resolution

**Option A: Scale Horizontally** (5 min fix)
```bash
kubectl scale deployment software-org-api --replicas=5 -n software-org
kubectl rollout status deployment/software-org-api -n software-org
```

**Option B: Optimize Indexes** (15 min fix)
```bash
# Add index if missing
CREATE INDEX CONCURRENTLY idx_kpi_snapshots_timestamp 
ON software_org.kpi_snapshots(recorded_at DESC);

REINDEX INDEX idx_kpi_snapshots_timestamp;
```

**Option C: Increase EventCloud Partitions** (30 min fix)
```bash
kafka-topics --alter \
  --topic software-org-events \
  --partitions 16 \
  --bootstrap-server eventcloud:9092
```

### Validation

```bash
# Verify latency has improved
kubectl exec -n software-org -it deployment/software-org-api -- \
  curl http://localhost:8080/metrics | grep event_processing_duration

# Expected: p99 < 5ms
```

### Follow-Up

- [ ] Review event payload sizes
- [ ] Analyze handler logic for inefficiencies
- [ ] Add caching if applicable
- [ ] Schedule capacity planning

---

## 2. Event Processing Failures

### Detection

**Alert:** `rate(software_org.errors_total[5m]) > 0.01`  
**Dashboard:** Grafana → Software-Org → Error Rate

### Diagnosis

```bash
# 1. Get error details
kubectl logs -n software-org -l app=software-org-api --tail=500 | grep -i error

# 2. Check failed event types
curl http://localhost:8080/metrics | grep 'errors_total{.*}'

# 3. Check database connectivity
kubectl exec -n software-org -it deployment/software-org-api -- \
  psql -h postgres-master -d software_org -c "SELECT 1"

# 4. Verify EventCloud access
kafkacat -b eventcloud:9092 -L | head -20

# 5. Check handler state
curl http://localhost:8080/api/v1/admin/handler-status
```

### Root Cause Analysis

**Error Type: Database Connection Failure**
```sql
-- Check database
SELECT datname, numbackends 
FROM pg_stat_database 
WHERE datname = 'software_org';

-- If connection count at limit, increase
ALTER SYSTEM SET max_connections = 300;
SELECT pg_reload_conf();
```

**Error Type: EventCloud Availability**
```bash
# Check broker status
for broker in eventcloud-{0,1,2}; do
  echo "Checking $broker:"
  nc -zv $broker 9092
done
```

**Error Type: Handler Logic Exception**
```bash
# Get stack trace
kubectl logs -n software-org -l app=software-org-api --tail=1000 | \
  grep -A 10 "Exception\|Error:" | head -50
```

### Resolution

**For Database Issues:**
```bash
# 1. Check pool exhaustion
SELECT sum(numbackends) FROM pg_stat_database;

# 2. Restart connection pool
kubectl rollout restart deployment/software-org-api -n software-org

# 3. Monitor recovery
watch -n 1 'kubectl get pods -n software-org'
```

**For EventCloud Issues:**
```bash
# 1. Check broker health
kubectl exec -n eventcloud eventcloud-0 -- \
  /kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092

# 2. Restart if needed
kubectl rollout restart statefulset/eventcloud -n eventcloud

# 3. Verify rebalance
kafkacat -b eventcloud:9092 -g software-org-event-processor -d
```

**For Handler Logic Issues:**
```bash
# 1. Identify failing event type
curl http://localhost:8080/metrics | grep 'errors_total' | sort

# 2. Review handler code
grep -n "handleXxxEvent" src/main/java/.../handlers/*.java

# 3. Deploy patch if available
git checkout main && git pull
./gradlew :products:software-org:build -x test
kubectl set image deployment/software-org-api \
  software-org-api=gcr.io/ghatana/software-org:latest -n software-org
```

### Validation

```bash
# Monitor error rate drop
kubectl exec -n software-org -it deployment/software-org-api -- \
  curl http://localhost:8080/metrics | grep errors_total

# Expected: 0 increase over 5 minutes
```

### Follow-Up

- [ ] Add alert for specific error types
- [ ] Implement circuit breaker pattern
- [ ] Add dead-letter queue for unprocessable events
- [ ] Schedule post-mortem

---

## 3. Multi-Tenant Isolation Breach (CRITICAL)

### Detection

**Alert:** Manual - suspected unauthorized data access  
**Severity:** P0 - Immediate escalation required

### Immediate Actions (First 60 seconds)

```bash
# 1. ALERT: Page security team
pagerduty trigger incident "Tenant isolation breach detected"

# 2. ISOLATE: Take application offline
kubectl scale deployment software-org-api --replicas=0 -n software-org

# 3. PRESERVE: Capture audit logs
kubectl exec -n software-org -it postgresql-pod -- \
  pg_dump -t software_org.audit_log > /tmp/audit-$(date +%s).sql

# 4. NOTIFY: Alert compliance officer
# Internal: #security-incident
# External: security@ghatana.com
```

### Investigation (Next 30 minutes)

```bash
# 1. Identify breach scope
SELECT 
  tenant_id,
  COUNT(*) as access_count,
  MIN(accessed_at) as first_access,
  MAX(accessed_at) as last_access
FROM software_org.audit_log
WHERE action = 'UNAUTHORIZED_ACCESS'
GROUP BY tenant_id;

# 2. List affected data
SELECT DISTINCT
  tenant_id,
  resource_type,
  COUNT(*) as records_exposed
FROM software_org.audit_log
WHERE accessed_at > NOW() - INTERVAL '1 hour'
  AND unauthorized = true
GROUP BY tenant_id, resource_type;

# 3. Identify source
SELECT 
  user_id,
  ip_address,
  request_count,
  unique_resources_accessed
FROM software_org.access_patterns
WHERE suspicious_activity = true
ORDER BY request_count DESC;
```

### Containment

```bash
# 1. Revoke compromised credentials
-- Get affected tokens
SELECT token_id, user_id, tenant_id
FROM software_org.auth_tokens
WHERE created_at > NOW() - INTERVAL '2 hours';

-- Revoke immediately
UPDATE software_org.auth_tokens
SET revoked_at = NOW()
WHERE token_id IN (...)
  AND authorized = false;

# 2. Block suspicious IPs
kubectl exec -n nginx ingress -- \
  ./scripts/block-ip.sh 192.168.1.100

# 3. Notify affected tenants
for tenant in $(affected_tenants); do
  send_breach_notification "$tenant"
done
```

### Recovery

```bash
# 1. Deploy security patch
git checkout security/isolation-fix
./gradlew :products:software-org:build -x test

# 2. Verify isolation in staging
kubectl exec -n software-org-staging -it deployment/software-org-api -- \
  ./scripts/test-tenant-isolation.sh

# 3. Restore application
kubectl scale deployment software-org-api --replicas=3 -n software-org

# 4. Monitor closely
watch -n 5 'kubectl logs -n software-org -l app=software-org-api'
```

### Post-Incident

- [ ] Conduct forensic analysis
- [ ] File breach notification as required
- [ ] Implement additional monitoring
- [ ] Schedule security architecture review
- [ ] Update incident response procedures

---

## 4. Deployment Failure

### Detection

**Alert:** `kubectl rollout status` timeout  
**Evidence:** Pod CrashLoopBackOff or pending

### Quick Diagnosis

```bash
# 1. Check pod status
kubectl get pods -n software-org -o wide

# 2. Get detailed error
kubectl describe pod <pod-name> -n software-org

# 3. Review logs
kubectl logs <pod-name> -n software-org

# 4. Check resource availability
kubectl describe node <node-name>
```

### Common Issues & Fixes

**Issue: CrashLoopBackOff**
```bash
# Check application startup logs
kubectl logs <pod-name> -n software-org --previous

# Likely causes:
# 1. Database connection timeout
#    → Verify DATABASE_URL, DB_PASSWORD
# 2. EventCloud unavailable
#    → Check EventCloud brokers
# 3. Port already in use
#    → Check for port conflicts
```

**Issue: Pending Pods**
```bash
# Check resource requests
kubectl describe pod <pod-name> -n software-org

# Add nodes or reduce resource requests
kubectl scale nodes --increment 1 --region us-central1 ...
```

**Issue: ImagePullBackOff**
```bash
# Verify image exists
docker inspect gcr.io/ghatana/software-org:v1.0.0

# Verify credentials
kubectl get secrets -n software-org

# Re-push image if missing
docker build -t gcr.io/ghatana/software-org:v1.0.0 .
docker push gcr.io/ghatana/software-org:v1.0.0
```

### Rollback

```bash
# 1. Identify previous stable version
kubectl rollout history deployment/software-org-api -n software-org

# 2. Rollback to previous
kubectl rollout undo deployment/software-org-api -n software-org

# 3. Monitor
kubectl rollout status deployment/software-org-api -n software-org

# 4. Verify
./scripts/smoke-tests/run.sh production
```

### Follow-Up

- [ ] Root cause analysis of deployment failure
- [ ] Add automated deployment testing
- [ ] Improve pre-deployment validation
- [ ] Schedule deployment process review

---

## 5. Database Unavailable

### Detection

**Alert:** Connection refused errors  
**Dashboard:** Grafana → Database Health

### Immediate Triage

```bash
# 1. Check database pod
kubectl get pod -n postgres postgresql-master-0

# 2. Check connectivity
psql -h postgres-master -d software_org -c "SELECT 1;"

# 3. Check available connections
SELECT count(*) FROM pg_stat_activity;

# 4. Check disk space
SELECT pg_size_pretty(pg_database_size('software_org'));
```

### Resolution

**Issue: Connection Limit Reached**
```sql
-- Terminate idle connections
SELECT pg_terminate_backend(pid) 
FROM pg_stat_activity 
WHERE state = 'idle' 
  AND state_change < NOW() - INTERVAL '10 minutes';

-- Increase limit
ALTER SYSTEM SET max_connections = 500;
SELECT pg_reload_conf();
```

**Issue: Out of Disk Space**
```bash
# Check disk usage
df -h /var/lib/postgresql

# Clean up old WAL files
pg_archivecleanup -d /mnt/wal/archive/ `ls -1r /mnt/wal/archive/ | head -1`

# Or expand volume
kubectl exec -it postgresql-master-0 -n postgres -- \
  lvextend -l +100%FREE /dev/mapper/vg-lv-data
```

**Issue: High Load/Locks**
```sql
-- Find blocking queries
SELECT blocked_locks.pid, 
       blocked_locks.usename, 
       blocking_locks.pid 
FROM pg_catalog.pg_locks blocked_locks 
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype 
WHERE NOT blocked_locks.granted;

-- Kill blocking session
SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE pid = <blocking_pid>;
```

### Validation

```bash
# Health check
psql -h postgres-master -d software_org -c \
  "SELECT count(*) FROM software_org.organizations;"

# Expected: Returns count without errors
```

---

## 6. Security Incident Response

### Detection

**Indicators:**
- Unusual access patterns
- Multiple failed authentication attempts
- Deployment of unauthorized code
- Data exfiltration attempts
- Cryptographic validation failures

### Immediate Actions

```bash
# 1. ALERT (30 seconds)
pagerduty trigger incident "SECURITY: Potential breach detected"
slack @security-team "SECURITY INCIDENT"

# 2. ISOLATE (60 seconds)
# - Disable affected credentials
# - Block suspicious IPs
# - Isolate affected services

kubectl scale deployment software-org-api --replicas=0 -n software-org

# 3. PRESERVE (2 minutes)
# - Capture logs and forensic evidence
# - Enable detailed audit logging
# - Archive all access logs
```

### Investigation Template

```bash
# Timeline of events
SELECT * FROM software_org.audit_log 
WHERE created_at > NOW() - INTERVAL '24 hours'
ORDER BY created_at DESC;

# Affected users
SELECT DISTINCT user_id FROM software_org.audit_log 
WHERE result = 'SECURITY_VIOLATION';

# Affected resources
SELECT DISTINCT resource_type FROM software_org.audit_log 
WHERE result = 'SECURITY_VIOLATION';

# Attack vectors
SELECT COUNT(*), source_ip FROM software_org.access_log
WHERE created_at > NOW() - INTERVAL '1 hour'
GROUP BY source_ip
HAVING COUNT(*) > 100;
```

### Escalation

- Severity P0 → Executive + Security + Legal
- Severity P1 → Security Team + Compliance
- Severity P2 → Security Team

### Public Communication

- ✅ Status page update (within 15 min)
- ✅ Customer notification (within 1 hour)
- ✅ Regulatory notification (as required by law)
- ✅ Post-incident report (within 48 hours)

---

## 7. Support Contacts

**On-Call Rotation:** #software-org-oncall  
**Security Incidents:** security@ghatana.com  
**Executive Escalation:** cto@ghatana.com  
**Customer Support:** support@ghatana.com  

**Status Page:** https://status.ghatana.com  
**Metrics Dashboard:** https://grafana.ghatana.com/d/software-org  
**Logs:** https://grafana.ghatana.com/explore (Loki)

---

## Document Change History

| Version | Date | Changes |
|---|---|---|
| 1.0.0 | 2025-11-16 | Initial runbook |
| - | - | - |

Next review: 2026-05-16
