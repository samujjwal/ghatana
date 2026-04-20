# YAPPC Operations Runbook

> **Purpose:** Comprehensive operations guide for YAPPC production deployment and maintenance
> **Last Updated:** 2026-04-19
> **Version:** 1.0

---

## Table of Contents

1. [Overview](#overview)
2. [Deployment Procedures](#deployment-procedures)
3. [Health Checks and Monitoring](#health-checks-and-monitoring)
4. [Common Troubleshooting Scenarios](#common-troubleshooting-scenarios)
5. [Backup and Restore Procedures](#backup-and-restore-procedures)
6. [Scaling Guidelines](#scaling-guidelines)
7. [Incident Response Procedures](#incident-response-procedures)
8. [Configuration Management](#configuration-management)
9. [Security Hardening Checklist](#security-hardening-checklist)

---

## Overview

### System Architecture

YAPPC consists of the following components:

- **Backend Services (Java 21)**
  - Lifecycle Service (port 8082)
  - AI Service
  - Canvas Service
  - Knowledge Service
  - Data Cloud Integration

- **Frontend (Node.js + React)**
  - Web Application (React Router v7)
  - API Gateway (Fastify)

- **Infrastructure**
  - PostgreSQL Database
  - Redis (caching, sessions)
  - Message Queue (for async processing)
  - Object Storage (artifacts, documents)

### Service Dependencies

```
Frontend (Web App)
    ↓
API Gateway (Fastify)
    ↓
Backend Services (ActiveJ)
    ↓
PostgreSQL + Redis + Message Queue
```

---

## Deployment Procedures

### Prerequisites

- Java 21+ (Temurin recommended)
- Node.js 20+ with pnpm 10.28.2
- Docker & Docker Compose
- 8GB+ RAM recommended

### Environment Variables

#### Required Variables (Production)

```bash
# Database
DB_HOST=your-db-host
DB_PORT=5432
DB_NAME=yappc_prod
DB_USER=ghatana
DB_PASSWORD=<secure-password>

# Redis
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=<secure-password>

# JWT (Critical - must be 32+ characters)
YAPPC_JWT_SECRET=<secure-32-char-secret>

# Application
YAPPC_PROFILE=production
NODE_ENV=production

# AI Service
AI_SERVICE_ENDPOINT=your-ai-service-endpoint
AI_SERVICE_API_KEY=<api-key>
```

#### Optional Variables

```bash
# Observability
ENABLE_METRICS=true
ENABLE_TRACING=true
OTEL_ENDPOINT=http://otel-collector:4317

# Feature Flags
ENABLE_DEV_AUTH_BYPASS=false  # MUST be false in production
```

### Deployment Steps

#### 1. Pre-Deployment Checklist

- [ ] All environment variables are set and verified
- [ ] JWT secret is 32+ characters (not default placeholder)
- [ ] Database migrations are prepared
- [ ] Backup of current database is created
- [ ] Health check endpoints are configured
- [ ] Monitoring and alerting are enabled
- [ ] Security scan passed

#### 2. Database Migration

```bash
# Run database migrations
cd products/yappc
./scripts/db/migrate.sh
```

#### 3. Backend Deployment

```bash
# Build backend services
./gradlew clean build -x test

# Run with production profile
java -jar platform/build/libs/yappc-platform.jar --spring.profiles.active=production
```

#### 4. Frontend Deployment

```bash
cd frontend
pnpm install
pnpm build
pnpm start:prod
```

#### 5. Post-Deployment Verification

```bash
# Check health endpoints
curl http://localhost:8082/health
curl http://localhost:8082/health/ready

# Verify database connectivity
curl http://localhost:8082/api/v1/health/db

# Check metrics endpoint
curl http://localhost:8082/metrics
```

---

## Health Checks and Monitoring

### Health Check Endpoints

#### Liveness Probe (Kubernetes/Health Check)

```
GET /health
```

**Response (200 OK):**
```json
{
  "status": "UP",
  "timestamp": "2026-04-19T12:00:00Z"
}
```

#### Readiness Probe

```
GET /health/ready
```

**Response (200 OK):**
```json
{
  "status": "READY",
  "checks": {
    "database": "UP",
    "redis": "UP",
    "aiService": "UP"
  }
}
```

**Response (503 Service Unavailable):**
```json
{
  "status": "NOT_READY",
  "checks": {
    "database": "DOWN",
    "redis": "UP",
    "aiService": "UP"
  }
}
```

### Key Metrics to Monitor

#### Application Metrics (Prometheus)

- `yappc_lifecycle_phase_transitions_total` - Count of phase transitions
- `yappc_canvas_operations_total` - Canvas operation count
- `yappc_ai_requests_total` - AI service request count
- `yappc_ai_request_duration_seconds` - AI request latency
- `yappc_http_requests_total` - HTTP request count by endpoint
- `yappc_http_request_duration_seconds` - HTTP request latency

#### Infrastructure Metrics

- Database connection pool usage
- Redis memory usage
- JVM heap memory usage
- Garbage collection frequency
- Thread pool utilization

### Alerting Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| CPU Usage | >70% | >90% |
| Memory Usage | >75% | >90% |
| Database Connections | >70% of pool | >90% of pool |
| HTTP Error Rate | >5% | >15% |
| Request Latency (p95) | >2s | >5s |
| AI Request Latency | >10s | >30s |

---

## Common Troubleshooting Scenarios

### Scenario 1: Service Fails to Start

**Symptoms:**
- Service exits immediately on startup
- Logs show "JWT secret too short" or "Invalid configuration"

**Diagnosis:**
```bash
# Check logs
tail -f logs/yappc.log

# Verify environment variables
env | grep YAPPC
```

**Resolution:**
1. Ensure `YAPPC_JWT_SECRET` is set and is 32+ characters
2. Verify `YAPPC_PROFILE=production` is set
3. Check database connectivity
4. Verify Redis is accessible

### Scenario 2: High Memory Usage

**Symptoms:**
- JVM heap usage >80%
- Frequent garbage collection
- OutOfMemoryError in logs

**Diagnosis:**
```bash
# Check JVM metrics
curl http://localhost:8082/metrics | grep jvm_memory
```

**Resolution:**
1. Increase heap size: `-Xmx4g -Xms4g`
2. Check for memory leaks in application code
3. Review canvas node count (large canvases consume memory)
4. Enable GC logging for detailed analysis

### Scenario 3: Database Connection Pool Exhaustion

**Symptoms:**
- "Connection pool exhausted" errors
- Slow database queries
- Timeouts on database operations

**Diagnosis:**
```bash
# Check connection pool metrics
curl http://localhost:8082/metrics | grep hikari
```

**Resolution:**
1. Increase connection pool size in configuration
2. Identify and fix slow queries
3. Check for connection leaks (unclosed connections)
4. Restart service if connections are stuck

### Scenario 4: AI Service Unresponsive

**Symptoms:**
- AI requests timing out
- "AI service unavailable" errors
- Degraded user experience

**Diagnosis:**
```bash
# Check AI service health
curl $AI_SERVICE_ENDPOINT/health
```

**Resolution:**
1. Verify AI service is running and accessible
2. Check API key validity
3. Implement circuit breaker pattern for AI calls
4. Enable fallback to rule-based assistance

### Scenario 5: Canvas Performance Degradation

**Symptoms:**
- Slow canvas rendering
- Laggy node manipulation
- High CPU usage

**Diagnosis:**
```bash
# Check canvas metrics
curl http://localhost:8082/metrics | grep canvas
```

**Resolution:**
1. Check canvas node count (should be <5000 nodes)
2. Enable virtualization for large canvases
3. Review canvas optimization settings
4. Clear cached canvas data if corrupted

---

## Backup and Restore Procedures

### Backup Strategy

**RPO (Recovery Point Objective):** 5 minutes  
**RTO (Recovery Time Objective):** 1 hour

### Automated Backups

Configure automated backups via cron:

```bash
# Hourly backups (retained for 24 hours)
0 * * * * /path/to/scripts/db/backup.sh

# Daily backups (retained for 30 days)
0 2 * * * /path/to/scripts/db/backup.sh
```

### Manual Backup

```bash
cd products/yappc
./scripts/db/backup.sh
```

**Environment Variables:**
```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=yappc_prod
DB_USER=ghatana
DB_PASSWORD=<password>
BACKUP_DIR=/path/to/backups
```

### Restore Procedure

```bash
cd products/yappc
./scripts/db/restore.sh /path/to/backup.dump yappc_restore
```

**Steps:**
1. Stop application services
2. Restore database from backup
3. Run database migrations
4. Restart application services
5. Verify health endpoints
6. Run smoke tests

### Backup Verification

```bash
# Verify backup integrity
./scripts/db/verify-backup-restore.sh
```

---

## Scaling Guidelines

### Horizontal Scaling

**When to Scale:**
- CPU usage consistently >70%
- Request latency p95 >2s
- Error rate >5%

**Scaling Steps:**
1. Deploy additional instances behind load balancer
2. Ensure shared state (Redis, PostgreSQL) can handle increased load
3. Configure session affinity if needed
4. Update monitoring thresholds

### Vertical Scaling

**When to Scale:**
- Memory usage consistently >75%
- Single-threaded bottlenecks
- Large canvas operations

**Scaling Steps:**
1. Increase JVM heap size
2. Add more CPU cores
3. Increase database connection pool
4. Optimize garbage collection settings

### Database Scaling

**Read Scaling:**
- Add read replicas for reporting
- Use connection pooling
- Implement query caching

**Write Scaling:**
- Partition large tables by tenant
- Use connection pooling
- Optimize write-heavy operations

---

## Incident Response Procedures

### Severity Levels

| Severity | Description | Response Time |
|----------|-------------|---------------|
| P1 | Critical system outage | 15 minutes |
| P2 | Major functionality loss | 1 hour |
| P3 | Minor functionality loss | 4 hours |
| P4 | Cosmetic issues | 1 business day |

### Incident Response Process

1. **Detection**
   - Automated alert received
   - User report submitted
   - Monitoring dashboard anomaly

2. **Triage**
   - Assign severity level
   - Identify affected components
   - Estimate impact

3. **Mitigation**
   - Implement temporary fix
   - Restore service functionality
   - Communicate with stakeholders

4. **Resolution**
   - Implement permanent fix
   - Verify fix resolves issue
   - Update documentation

5. **Post-Mortem**
   - Document root cause
   - Identify preventive measures
   - Update runbook

### Communication Channels

- **Internal:** Slack #yappc-ops
- **External:** Status page (https://status.ghatana.ai)
- **Escalation:** on-call rotation

---

## Configuration Management

### Configuration Sources

1. **Environment Variables** (production secrets)
2. **Configuration Files** (application.yml)
3. **Feature Flags** (dynamic toggles)

### Configuration Hierarchy

```
Environment Variables > Feature Flags > Configuration Files > Defaults
```

### Updating Configuration

1. Update configuration files
2. Deploy new version
3. Verify configuration loaded correctly
4. Monitor for issues
5. Rollback if needed

### Feature Flags

Enable/disable features without deployment:

```bash
# Via API
curl -X POST http://localhost:8082/api/v1/admin/features \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"feature": "canvas-ai-suggestions", "enabled": true}'
```

---

## Security Hardening Checklist

### Pre-Production Security

- [ ] All default passwords changed
- [ ] JWT secret is 32+ characters
- [ ] TLS/SSL enabled for all endpoints
- [ ] Database access restricted to application hosts
- [ ] Redis password set
- [ ] API rate limiting enabled
- [ ] Input validation on all endpoints
- [ ] SQL injection prevention (parameterized queries)
- [ ] XSS protection headers set
- [ ] CORS configured correctly
- [ ] Security headers (CSP, HSTS, X-Frame-Options)
- [ ] Dependency scan passed
- [ ] Secrets not in code or git history

### Runtime Security

- [ ] Regular security updates applied
- [ ] Log monitoring for suspicious activity
- [ ] Audit logging enabled
- [ ] Access logs reviewed
- [ ] Failed login attempts monitored
- [ ] Database access logged
- [ ] API access logged
- [ ] Anomaly detection enabled

### Incident Response

- [ ] Security incident response plan documented
- [ ] Team trained on security procedures
- [ ] Contact information for security team
- [ ] Backup and restore tested
- [ ] Disaster recovery plan tested

---

## Appendix

### Useful Commands

```bash
# Check service health
curl http://localhost:8082/health

# View metrics
curl http://localhost:8082/metrics

# Check logs
tail -f logs/yappc.log

# Database connection test
psql -h localhost -U ghatana -d yappc_prod -c "SELECT 1"

# Redis connection test
redis-cli -h localhost ping

# Restart service
systemctl restart yappc

# Check JVM stats
jstat -gcutil <pid> 1000
```

### Contact Information

- **Operations Team:** ops@ghatana.ai
- **On-Call:** +1-555-XXX-XXXX
- **Emergency:** emergency@ghatana.ai

### Related Documentation

- [YAPPC README](../README.md)
- [JWT Authentication](./JWT_AUTHENTICATION.md)
- [Audit Remediation Progress](./AUDIT_REMEDIATION_PROGRESS.md)
- [P3 Tasks Implementation Notes](./P3_TASKS_IMPLEMENTATION_NOTES.md)
