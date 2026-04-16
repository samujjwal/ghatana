# AEP Operational Runbook

**Product**: Agentic Event Processor (AEP)  
**Version**: 2026.3.1  
**Last Updated**: March 19, 2026  
**Owner**: @ghatana/aep-team

---

## Table of Contents

1. [Deployment Procedures](#deployment-procedures)
2. [Troubleshooting Guide](#troubleshooting-guide)
3. [Rollback Procedures](#rollback-procedures)
4. [Incident Response](#incident-response)
5. [Scaling Guide](#scaling-guide)
6. [Backup and Recovery](#backup-and-recovery)
7. [Monitoring and Alerts](#monitoring-and-alerts)
8. [Common Issues](#common-issues)

---

## 1. Deployment Procedures

### 1.1 Pre-Deployment Checklist

**Required Checks:**
- [ ] All tests passing (unit, integration, E2E)
- [ ] Code review approved
- [ ] Security scan completed
- [ ] Performance benchmarks validated
- [ ] Database migrations tested
- [ ] Configuration validated
- [ ] Rollback plan prepared
- [ ] Stakeholders notified

**Environment Verification:**
```bash
# Verify Kubernetes cluster access
kubectl cluster-info

# Verify namespace exists
kubectl get namespace aep-production

# Verify secrets are configured
kubectl get secrets -n aep-production

# Verify ConfigMaps
kubectl get configmaps -n aep-production
```

### 1.2 Deployment Steps (Kubernetes)

**Step 1: Build and Push Container Image**
```bash
# Build Docker image
cd products/aep
docker build -t ghcr.io/ghatana/aep:${VERSION} -f Dockerfile .

# Push to registry
docker push ghcr.io/ghatana/aep:${VERSION}

# Tag as latest (production only)
docker tag ghcr.io/ghatana/aep:${VERSION} ghcr.io/ghatana/aep:latest
docker push ghcr.io/ghatana/aep:latest
```

**Step 2: Update Helm Values**
```bash
# Update version in values.yaml
cd helm/aep
vim values-production.yaml

# Update image.tag to new version
image:
  repository: ghcr.io/ghatana/aep
  tag: "${VERSION}"
```

**Step 3: Deploy with Helm**
```bash
# Dry run first
helm upgrade --install aep ./helm/aep \
  --namespace aep-production \
  --values helm/aep/values-production.yaml \
  --dry-run --debug

# Actual deployment
helm upgrade --install aep ./helm/aep \
  --namespace aep-production \
  --values helm/aep/values-production.yaml \
  --wait --timeout 10m

# Verify deployment
kubectl rollout status deployment/aep -n aep-production
```

**Step 4: Smoke Tests**
```bash
# Health check
curl https://aep.ghatana.com/health

# Readiness check
curl https://aep.ghatana.com/ready

# Metrics endpoint
curl https://aep.ghatana.com/metrics

# Test pipeline execution
curl -X POST https://aep.ghatana.com/api/pipelines/test \
  -H "Content-Type: application/json" \
  -d '{"test": "smoke"}'
```

### 1.3 Database Migration

**Pre-Migration:**
```bash
# Backup database
kubectl exec -n aep-production aep-postgres-0 -- \
  pg_dump -U aep aep_db > backup-$(date +%Y%m%d-%H%M%S).sql

# Verify backup
ls -lh backup-*.sql
```

**Run Migration:**
```bash
# Apply Flyway migrations
kubectl exec -n aep-production deployment/aep -- \
  java -jar /app/flyway.jar migrate

# Verify migration status
kubectl exec -n aep-production deployment/aep -- \
  java -jar /app/flyway.jar info
```

### 1.4 Post-Deployment Verification

**Verification Checklist:**
- [ ] All pods running (kubectl get pods -n aep-production)
- [ ] Health endpoints responding
- [ ] Metrics being collected
- [ ] Logs flowing to aggregator
- [ ] Database connections healthy
- [ ] Redis connections healthy
- [ ] Kafka consumers active
- [ ] No error spikes in logs
- [ ] Response times within SLA
- [ ] Pipeline execution working

**Monitoring Dashboard:**
```
https://grafana.ghatana.com/d/aep-production
```

---

## 2. Troubleshooting Guide

### 2.1 Pod Crashes / CrashLoopBackOff

**Symptoms:**
- Pods restarting frequently
- CrashLoopBackOff status
- Application not starting

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -n aep-production

# View pod logs
kubectl logs -n aep-production deployment/aep --tail=100

# Describe pod for events
kubectl describe pod -n aep-production <pod-name>

# Check resource limits
kubectl top pods -n aep-production
```

**Common Causes:**
1. **Out of Memory (OOM)**
   ```bash
   # Increase memory limits in values.yaml
   resources:
     limits:
       memory: 4Gi  # Increase from 2Gi
   ```

2. **Database Connection Failure**
   ```bash
   # Verify database credentials
   kubectl get secret aep-db-credentials -n aep-production -o yaml
   
   # Test database connection
   kubectl run -it --rm debug --image=postgres:15 \
     --restart=Never -n aep-production -- \
     psql -h aep-postgres -U aep -d aep_db
   ```

3. **Missing Environment Variables**
   ```bash
   # Check ConfigMap
   kubectl get configmap aep-config -n aep-production -o yaml
   
   # Verify all required env vars are set
   kubectl exec -n aep-production deployment/aep -- env | grep AEP_
   ```

  Production startup now fails closed unless at least these variables are present:
  - `AEP_PROFILE=production`
  - `AEP_DB_URL`
  - `AEP_JWT_SECRET`

  In production profile, agent identity resolution now depends on the persisted
  AEP agent-registration tables. If identity-dependent flows fail after startup,
  verify that the database contains active rows for the expected tenant/agent in:
  - `agent_registrations`
  - `agent_capabilities`

### 2.2 High Latency / Slow Performance

**Symptoms:**
- API response times > 1s
- Pipeline execution slow
- Timeout errors

**Diagnosis:**
```bash
# Check CPU/Memory usage
kubectl top pods -n aep-production

# View application metrics
curl https://aep.ghatana.com/metrics | grep -E "http_request_duration|pipeline_execution_time"

# Check database query performance
kubectl exec -n aep-production aep-postgres-0 -- \
  psql -U aep -d aep_db -c "SELECT * FROM pg_stat_statements ORDER BY mean_exec_time DESC LIMIT 10;"
```

**Solutions:**
1. **Scale horizontally**
   ```bash
   kubectl scale deployment aep -n aep-production --replicas=5
   ```

2. **Optimize database queries**
   - Add indexes to frequently queried columns
   - Review slow query log
   - Enable query plan analysis

3. **Increase connection pools**
   ```yaml
   # In application.properties
   spring.datasource.hikari.maximum-pool-size=50
   spring.redis.lettuce.pool.max-active=50
   ```

### 2.3 Event Processing Failures

**Symptoms:**
- Events stuck in queue
- Dead letter queue growing
- Pipeline failures

**Diagnosis:**
```bash
# Check Kafka consumer lag
kubectl exec -n aep-production deployment/aep -- \
  kafka-consumer-groups --bootstrap-server kafka:9092 \
  --group aep-consumers --describe

# View failed events
curl https://aep.ghatana.com/api/events/failed?limit=10

# Check pipeline status
curl https://aep.ghatana.com/api/pipelines/status
```

**Solutions:**
1. **Replay failed events**
   ```bash
   curl -X POST https://aep.ghatana.com/api/events/replay \
     -H "Content-Type: application/json" \
     -d '{"event_ids": ["id1", "id2"]}'
   ```

2. **Increase parallelism**
   ```yaml
   # In pipeline configuration
   parallelism: 10  # Increase from 5
   ```

3. **Review operator errors**
   ```bash
   kubectl logs -n aep-production deployment/aep | grep "OperatorException"
   ```

---

## 3. Rollback Procedures

### 3.1 Helm Rollback

**Quick Rollback:**
```bash
# List releases
helm list -n aep-production

# View release history
helm history aep -n aep-production

# Rollback to previous version
helm rollback aep -n aep-production

# Rollback to specific revision
helm rollback aep 5 -n aep-production
```

### 3.2 Database Rollback

**Restore from Backup:**
```bash
# Stop application
kubectl scale deployment aep -n aep-production --replicas=0

# Restore database
kubectl exec -n aep-production aep-postgres-0 -- \
  psql -U aep -d aep_db < backup-YYYYMMDD-HHMMSS.sql

# Restart application
kubectl scale deployment aep -n aep-production --replicas=3
```

### 3.3 Rollback Verification

**Post-Rollback Checks:**
- [ ] Application version correct
- [ ] All pods healthy
- [ ] Database schema version correct
- [ ] No error spikes
- [ ] Metrics normal
- [ ] Pipeline execution working

---

## 4. Incident Response

### 4.1 Severity Levels

**P0 - Critical (Response: Immediate)**
- Complete service outage
- Data loss or corruption
- Security breach

**P1 - High (Response: < 1 hour)**
- Partial service degradation
- Performance severely impacted
- Key features unavailable

**P2 - Medium (Response: < 4 hours)**
- Minor feature issues
- Non-critical bugs
- Performance slightly degraded

**P3 - Low (Response: < 24 hours)**
- Cosmetic issues
- Documentation errors
- Enhancement requests

### 4.2 Incident Response Steps

**Step 1: Acknowledge and Assess**
```bash
# Create incident channel
# Slack: #incident-aep-YYYYMMDD

# Gather initial information
- What is the impact?
- How many users affected?
- When did it start?
- What changed recently?
```

**Step 2: Mitigate**
```bash
# Quick fixes to restore service
- Scale up replicas
- Rollback recent deployment
- Disable problematic feature
- Route traffic to backup
```

**Step 3: Investigate**
```bash
# Collect diagnostic data
kubectl logs -n aep-production deployment/aep --since=1h > incident-logs.txt
kubectl describe pods -n aep-production > incident-pods.txt
kubectl get events -n aep-production --sort-by='.lastTimestamp' > incident-events.txt
```

**Step 4: Resolve**
```bash
# Apply permanent fix
- Deploy hotfix
- Update configuration
- Patch database
```

**Step 5: Post-Mortem**
```markdown
# Incident Post-Mortem Template

## Incident Summary
- **Date**: YYYY-MM-DD
- **Duration**: X hours
- **Severity**: PX
- **Impact**: Description

## Timeline
- HH:MM - Incident detected
- HH:MM - Team notified
- HH:MM - Mitigation applied
- HH:MM - Service restored

## Root Cause
Detailed explanation

## Resolution
Steps taken to resolve

## Action Items
- [ ] Action 1 (Owner: @person, Due: YYYY-MM-DD)
- [ ] Action 2 (Owner: @person, Due: YYYY-MM-DD)

## Lessons Learned
What we learned and how to prevent
```

---

## 5. Scaling Guide

### 5.1 Horizontal Scaling

**Manual Scaling:**
```bash
# Scale up
kubectl scale deployment aep -n aep-production --replicas=10

# Scale down
kubectl scale deployment aep -n aep-production --replicas=3
```

**Auto-Scaling (HPA):**
```yaml
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: aep-hpa
  namespace: aep-production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: aep
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

### 5.2 Vertical Scaling

**Increase Resources:**
```yaml
# values.yaml
resources:
  requests:
    cpu: 2000m      # Increase from 1000m
    memory: 4Gi     # Increase from 2Gi
  limits:
    cpu: 4000m      # Increase from 2000m
    memory: 8Gi     # Increase from 4Gi
```

### 5.3 Database Scaling

**Read Replicas:**
```bash
# Add read replica
kubectl apply -f k8s/postgres-read-replica.yaml

# Configure application to use read replica
spring.datasource.read-only.url=jdbc:postgresql://aep-postgres-read:5432/aep_db
```

**Connection Pooling:**
```properties
# Increase connection pool
spring.datasource.hikari.maximum-pool-size=100
spring.datasource.hikari.minimum-idle=20
```

---

## 6. Backup and Recovery

### 6.1 Database Backups

**Automated Backups:**
```bash
# CronJob for daily backups
apiVersion: batch/v1
kind: CronJob
metadata:
  name: aep-db-backup
  namespace: aep-production
spec:
  schedule: "0 2 * * *"  # 2 AM daily
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:15
            command:
            - /bin/sh
            - -c
            - pg_dump -h aep-postgres -U aep aep_db | gzip > /backups/aep-$(date +\%Y\%m\%d).sql.gz
```

**Manual Backup:**
```bash
# Full database backup
kubectl exec -n aep-production aep-postgres-0 -- \
  pg_dump -U aep aep_db | gzip > aep-backup-$(date +%Y%m%d-%H%M%S).sql.gz

# Specific table backup
kubectl exec -n aep-production aep-postgres-0 -- \
  pg_dump -U aep -t pipelines aep_db > pipelines-backup.sql
```

### 6.2 Configuration Backups

**Backup Kubernetes Resources:**
```bash
# Backup all AEP resources
kubectl get all,configmaps,secrets,pvc -n aep-production -o yaml > aep-k8s-backup.yaml

# Backup Helm values
cp helm/aep/values-production.yaml backups/values-$(date +%Y%m%d).yaml
```

### 6.3 Recovery Procedures

**Database Recovery:**
```bash
# Stop application
kubectl scale deployment aep -n aep-production --replicas=0

# Restore database
gunzip < aep-backup-YYYYMMDD-HHMMSS.sql.gz | \
  kubectl exec -i -n aep-production aep-postgres-0 -- \
  psql -U aep aep_db

# Restart application
kubectl scale deployment aep -n aep-production --replicas=3
```

**Disaster Recovery:**
```bash
# 1. Provision new cluster
# 2. Restore database from backup
# 3. Deploy application
helm install aep ./helm/aep \
  --namespace aep-production \
  --values backups/values-YYYYMMDD.yaml

# 4. Verify and switch traffic
```

### 6.4 Quarterly Disaster Recovery Drill Schedule

The AEP production-readiness plan left one operational follow-up open after the
DR service implementation: recurring restore drills. The quarterly cadence is:

| Quarter | Window | Primary Focus | Required Evidence |
|---------|--------|---------------|-------------------|
| Q1 | First Tuesday of February | Restore latest production backup into isolated recovery environment | Restore log, RTO measurement, dependency probe results |
| Q2 | First Tuesday of May | Tenant-scoped recovery and session/auth validation | Recovered tenant sample, auth/session smoke checks, issue log |
| Q3 | First Tuesday of August | Cross-service dependency validation (`/health/deep`, Redis, Data Cloud, execution history, memory plane) | Deep health capture, recovery checklist, rollback notes |
| Q4 | First Tuesday of November | Full tabletop plus live restore rehearsal | Incident timeline, communication log, RPO/RTO comparison |

**Execution rules:**
- Run the drill in a non-production recovery environment using the latest successful backup set.
- Measure both target and actual RTO/RPO for the tenant or environment under test.
- Verify `GET /health/deep`, `GET /metrics`, pipeline execution, execution history, and agent memory before closing the drill.
- Record findings in the AEP weekly review and create follow-up issues for any gap that misses target objectives.
- Security and platform operations must sign off on the Q2 and Q4 drills because they exercise auth/session reuse and broader incident-response coordination.

**Minimum artifacts to retain:**
- Recovery environment identifier and backup IDs used.
- Start/end timestamps for restore and verification.
- Output from health, metrics, and representative pipeline smoke checks.
- A short postmortem summarizing failures, operator actions, and remediation owners.

---

## 7. Monitoring and Alerts

### 7.1 Key Metrics

**Application Metrics:**
- Request rate (requests/sec)
- Response time (p50, p95, p99)
- Error rate (%)
- Pipeline execution time
- Event processing lag

**Infrastructure Metrics:**
- CPU utilization (%)
- Memory utilization (%)
- Disk I/O
- Network throughput
- Pod restarts

**Business Metrics:**
- Events processed/hour
- Active pipelines
- Operator success rate
- HITL review queue size

### 7.2 Alert Rules

**Critical Alerts:**
```yaml
# Prometheus alert rules
groups:
- name: aep-critical
  rules:
  - alert: AEPDown
    expr: up{job="aep"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "AEP is down"
      
  - alert: HighErrorRate
    expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
    for: 5m
    labels:
      severity: critical
    annotations:
      summary: "Error rate > 5%"
      
  - alert: DatabaseDown
    expr: pg_up{instance="aep-postgres"} == 0
    for: 1m
    labels:
      severity: critical
    annotations:
      summary: "PostgreSQL is down"
```

### 7.3 Dashboards

**Grafana Dashboards:**
- AEP Overview: https://grafana.ghatana.com/d/aep-overview
- Pipeline Metrics: https://grafana.ghatana.com/d/aep-pipelines
- Infrastructure: https://grafana.ghatana.com/d/aep-infra
- Business Metrics: https://grafana.ghatana.com/d/aep-business

---

## 8. Common Issues

### 8.1 "No reactor in current thread" Error

**Cause:** Promise.ofBlocking() called without ActiveJ reactor context

**Solution:**
```java
// WRONG
return Promise.ofBlocking(executor, () -> {
    return result;
});

// CORRECT
try {
    return Promise.of(result);
} catch (Exception e) {
    return Promise.ofException(e);
}
```

### 8.2 Redis Connection Timeout

**Cause:** Redis password not configured or network issue

**Solution:**
```bash
# Verify Redis credentials
kubectl get secret aep-redis-credentials -n aep-production -o yaml

# Test Redis connection
kubectl run -it --rm redis-test --image=redis:7 \
  --restart=Never -n aep-production -- \
  redis-cli -h aep-redis -a <password> ping
```

### 8.3 Kafka Consumer Lag

**Cause:** Processing slower than event ingestion rate

**Solution:**
```bash
# Increase consumer parallelism
kubectl set env deployment/aep -n aep-production \
  KAFKA_CONSUMER_CONCURRENCY=20

# Add more consumer instances
kubectl scale deployment aep -n aep-production --replicas=10
```

---

## Contact Information

**On-Call Rotation:**
- Primary: @ghatana/aep-oncall
- Secondary: @ghatana/platform-oncall
- Escalation: @ghatana/architecture

**Slack Channels:**
- #aep-alerts (automated alerts)
- #aep-team (team discussions)
- #incident-response (active incidents)

**Documentation:**
- Architecture: `/docs/aep/ARCHITECTURE.md`
- API Docs: `https://aep.ghatana.com/api/docs`
- Runbooks: `/docs/aep/runbooks/`

---

**Document Version**: 1.0  
**Last Review**: March 19, 2026  
**Next Review**: June 19, 2026
