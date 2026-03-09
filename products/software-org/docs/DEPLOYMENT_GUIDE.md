# Software-Org Deployment Guide

**Version:** 1.0.0  
**Last Updated:** 2025-11-16  
**Status:** Production Ready

## Overview

This guide covers deploying software-org to production, including infrastructure setup, configuration, monitoring, and operational procedures.

## Pre-Deployment Checklist

- [ ] All tests passing: `./gradlew :products:software-org:test`
- [ ] Performance benchmarks verified: 50k events/sec, <10ms p99 latency
- [ ] Security audit completed: zero critical findings
- [ ] All secrets configured in secrets manager (not in git)
- [ ] Database migrations prepared and tested
- [ ] Monitoring dashboards deployed
- [ ] Runbook documentation reviewed by ops team

## 1. Infrastructure Setup

### 1.1 Kubernetes Deployment

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: software-org
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: software-org-api
  namespace: software-org
spec:
  replicas: 3
  selector:
    matchLabels:
      app: software-org-api
  template:
    metadata:
      labels:
        app: software-org-api
    spec:
      containers:
      - name: software-org-api
        image: ghatana/software-org:latest
        ports:
        - containerPort: 8080
        env:
        - name: EVENTCLOUD_BOOTSTRAP_SERVERS
          valueFrom:
            secretKeyRef:
              name: software-org-secrets
              key: eventcloud-bootstrap
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: software-org-secrets
              key: db-host
        - name: TENANT_ID
          value: "default"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /api/v1/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /api/v1/health
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
```

### 1.2 Database Setup

```sql
-- Create software-org schema
CREATE SCHEMA software_org;

-- Create main tables
CREATE TABLE software_org.organizations (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    UNIQUE(tenant_id, name)
);

CREATE TABLE software_org.departments (
    id UUID PRIMARY KEY,
    org_id UUID NOT NULL REFERENCES software_org.organizations(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(org_id, name)
);

CREATE TABLE software_org.kpi_snapshots (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES software_org.departments(id),
    metric_name VARCHAR(255) NOT NULL,
    metric_value FLOAT NOT NULL,
    recorded_at TIMESTAMP NOT NULL,
    INDEX(department_id, metric_name, recorded_at)
);

-- Create event log table
CREATE TABLE software_org.event_log (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    department_id UUID REFERENCES software_org.departments(id),
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX(created_at),
    INDEX(event_type)
);
```

### 1.3 EventCloud Configuration

```properties
# EventCloud client configuration
eventcloud.bootstrap.servers=eventcloud-broker-0:9092
eventcloud.client.id=software-org-api
eventcloud.security.protocol=SASL_SSL
eventcloud.sasl.mechanism=SCRAM-SHA-256
eventcloud.sasl.username=${EVENTCLOUD_USER}
eventcloud.sasl.password=${EVENTCLOUD_PASSWORD}

# Topics (auto-created if not exist)
eventcloud.topic.events=software-org-events
eventcloud.topic.kpi-updates=software-org-kpi-updates
eventcloud.topic.audit-logs=software-org-audit-logs

# Consumer configuration
eventcloud.consumer.group=software-org-event-processor
eventcloud.consumer.max.poll.records=500
eventcloud.consumer.session.timeout.ms=30000
```

## 2. Configuration Management

### 2.1 Environment Variables

```bash
# API Configuration
API_PORT=8080
API_CONTEXT_PATH=/api/v1
API_MAX_THREADS=200

# Database
DB_DRIVER=org.postgresql.Driver
DB_URL=jdbc:postgresql://postgres-master:5432/software_org
DB_USER=${DB_USER}
DB_PASSWORD=${DB_PASSWORD}
DB_POOL_SIZE=20

# EventCloud
EVENTCLOUD_BOOTSTRAP_SERVERS=eventcloud:9092
EVENTCLOUD_USER=${EVENTCLOUD_USER}
EVENTCLOUD_PASSWORD=${EVENTCLOUD_PASSWORD}
EVENTCLOUD_SECURITY_PROTOCOL=SASL_SSL

# Multi-tenancy
DEFAULT_TENANT_ID=default
TENANT_HEADER_NAME=X-Tenant-Id

# Monitoring
METRICS_ENABLE=true
TRACING_ENABLE=true
JAEGER_ENDPOINT=http://jaeger:14268/api/traces

# Security
JWT_ISSUER=https://auth.ghatana.com
JWT_AUDIENCE=software-org-api
TLS_ENABLED=true
TLS_CERT_PATH=/etc/tls/cert.pem
TLS_KEY_PATH=/etc/tls/key.pem

# Performance
EVENT_HANDLER_TIMEOUT_MS=5000
KPI_AGGREGATION_INTERVAL_SECONDS=60
MAX_CONCURRENT_EVENTS=50000
```

### 2.2 Feature Flags

```yaml
features:
  enableAdvancedAnalytics: true
  enableIncidentDetection: true
  enablePredictiveMetrics: false  # Phase 5
  enableMLRecommendations: false  # Phase 5
  enableCustomReporting: true
  enableAuditLogging: true
  enableRateLimiting: true
```

## 3. Deployment Process

### 3.1 Pre-Deployment Steps

```bash
#!/bin/bash
set -e

# 1. Build docker image
docker build -t ghatana/software-org:v1.0.0 .

# 2. Run tests
./gradlew :products:software-org:test

# 3. Run performance benchmarks
./gradlew :products:software-org:jmh --benchmark=".*KpiPipeline.*"

# 4. Security scan
./gradlew :products:software-org:dependencyCheckAnalyze

# 5. Tag and push image
docker tag ghatana/software-org:v1.0.0 gcr.io/ghatana/software-org:v1.0.0
docker push gcr.io/ghatana/software-org:v1.0.0

# 6. Deploy to staging first
kubectl apply -f k8s/staging/software-org-deployment.yaml
kubectl rollout status deployment/software-org-api -n software-org-staging

# 7. Run smoke tests
./scripts/smoke-tests/run.sh staging

echo "✅ Pre-deployment checks passed"
```

### 3.2 Production Deployment

```bash
#!/bin/bash
set -e

DEPLOYMENT_DATE=$(date +%Y-%m-%d_%H-%M-%S)
BACKUP_DIR="backups/${DEPLOYMENT_DATE}"

echo "🚀 Deploying software-org to production..."

# 1. Backup current state
mkdir -p ${BACKUP_DIR}
kubectl get all -n software-org -o yaml > ${BACKUP_DIR}/k8s-state.yaml
pg_dump software_org > ${BACKUP_DIR}/database.sql

# 2. Deploy new version
kubectl set image deployment/software-org-api \
  software-org-api=gcr.io/ghatana/software-org:v1.0.0 \
  -n software-org \
  --record

# 3. Monitor rollout
kubectl rollout status deployment/software-org-api -n software-org --timeout=10m

# 4. Health checks
./scripts/health-checks/verify-deployment.sh production

# 5. Run smoke tests
./scripts/smoke-tests/run.sh production

echo "✅ Deployment successful"
echo "📊 Monitoring dashboard: https://grafana.ghatana.com/d/software-org"
```

### 3.3 Rollback Procedure

```bash
#!/bin/bash
set -e

echo "⚠️  Initiating rollback..."

# 1. Revert to previous image
kubectl set image deployment/software-org-api \
  software-org-api=gcr.io/ghatana/software-org:v0.9.0 \
  -n software-org

# 2. Monitor rollback
kubectl rollout status deployment/software-org-api -n software-org --timeout=5m

# 3. Verify health
./scripts/health-checks/verify-deployment.sh production

# 4. Database rollback (if needed)
# psql software_org < backups/latest/database.sql

echo "✅ Rollback completed"
```

## 4. Monitoring & Observability

### 4.1 Key Metrics to Monitor

```
software_org.events.processed_total (counter)
software_org.event.processing_duration_seconds (histogram)
software_org.kpi.aggregation_duration_seconds (histogram)
software_org.api.request_duration_seconds (histogram)
software_org.errors_total (counter)
software_org.deployments_total (counter)
```

### 4.2 Alert Configuration

```yaml
groups:
- name: software-org
  rules:
  - alert: HighEventProcessingLatency
    expr: |
      histogram_quantile(0.99, software_org.event.processing_duration_seconds) > 0.01
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Event processing p99 latency > 10ms"
  
  - alert: EventProcessingFailures
    expr: |
      rate(software_org.errors_total[5m]) > 0.01
    for: 2m
    labels:
      severity: critical
    annotations:
      summary: "Event processing error rate > 1%"
  
  - alert: LowThroughput
    expr: |
      rate(software_org.events.processed_total[5m]) < 40000
    for: 5m
    labels:
      severity: warning
    annotations:
      summary: "Event throughput < 40k/sec (target: 50k/sec)"
```

## 5. Operational Procedures

### 5.1 Database Migrations

```bash
# Create migration
flyway -locations=filesystem:sql/migrations create

# Deploy migrations
flyway -url=jdbc:postgresql://localhost/software_org \
       -user=postgres \
       -password=secret \
       migrate

# Validate
flyway validate
```

### 5.2 Scaling Operations

```bash
# Horizontal scaling (add replicas)
kubectl scale deployment software-org-api --replicas=5 -n software-org

# Monitor scaling
kubectl get hpa software-org-api -n software-org

# Vertical scaling (update resources)
kubectl set resources deployment software-org-api \
  --limits=cpu=4000m,memory=4Gi \
  --requests=cpu=2000m,memory=2Gi \
  -n software-org
```

### 5.3 Log Collection

```bash
# View recent logs
kubectl logs -n software-org -l app=software-org-api --tail=100

# Stream logs
kubectl logs -n software-org -l app=software-org-api -f

# Export logs for analysis
kubectl logs -n software-org -l app=software-org-api > /tmp/software-org.log
```

## 6. Troubleshooting

### 6.1 High Latency

1. Check pod resource usage: `kubectl top pods -n software-org`
2. Review event handler metrics in Grafana
3. Analyze slow queries in database
4. Check EventCloud broker lag

### 6.2 Event Processing Failures

1. Check application logs: `kubectl logs -n software-org deployment/software-org-api`
2. Verify database connectivity
3. Confirm EventCloud access
4. Review error metrics

### 6.3 Multi-tenant Isolation Issues

1. Verify X-Tenant-Id header extraction
2. Check database tenant filtering
3. Review EventCloud partition assignment
4. Audit access logs

## 7. Performance SLAs

| Metric | Target | P99 | Alert Threshold |
|--------|--------|-----|-----------------|
| Event Processing | <1ms | <5ms | >10ms |
| KPI Aggregation | <1ms | <5ms | >10ms |
| API Response | <50ms | <100ms | >200ms |
| Deployment | <5min | N/A | >10min |
| Throughput | 50k events/sec | N/A | <40k events/sec |

## 8. Support & Escalation

**On-Call:** See #software-org-oncall  
**Status Page:** https://status.ghatana.com  
**Incident Response:** See INCIDENT_RESPONSE_RUNBOOK.md  
**Escalation:** Page infrastructure team if SLA breached
