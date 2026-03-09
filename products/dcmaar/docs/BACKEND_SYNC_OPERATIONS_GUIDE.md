# Guardian Backend Sync - Operations & Deployment Guide

## Table of Contents

- [Pre-Deployment Checklist](#pre-deployment-checklist)
- [Deployment Steps](#deployment-steps)
- [Configuration Management](#configuration-management)
- [Monitoring](#monitoring)
- [Maintenance](#maintenance)
- [Disaster Recovery](#disaster-recovery)

---

## Pre-Deployment Checklist

### Code Quality

- [ ] All TypeScript files compile without errors

  ```bash
  cd browser-extension && npm run build
  cd backend && npx tsc --noEmit
  ```

- [ ] Tests pass

  ```bash
  npm run test
  npm run test:integration
  ```

- [ ] Linting passes

  ```bash
  npm run lint
  ```

- [ ] No console.log statements (use logger instead)
- [ ] Error messages are descriptive
- [ ] No hardcoded secrets or credentials

### Security

- [ ] CORS configuration reviewed
- [ ] Authentication tokens validated
- [ ] Rate limiting configured
- [ ] Input validation on all endpoints
- [ ] SQL injection prevention verified
- [ ] XSS prevention verified
- [ ] CSRF tokens present

### Performance

- [ ] Event batching configured appropriately
- [ ] Database indexes created

  ```sql
  CREATE INDEX idx_events_device_id ON guardian_events(device_id);
  CREATE INDEX idx_events_created_at ON guardian_events(created_at);
  CREATE INDEX idx_commands_device_id ON guardian_commands(device_id);
  ```

- [ ] Query performance tested
- [ ] Connection pooling configured
- [ ] Memory usage within limits

### Infrastructure

- [ ] Database backup strategy defined
- [ ] Log aggregation configured
- [ ] Monitoring/alerting setup
- [ ] SSL/TLS certificates valid
- [ ] Firewall rules configured
- [ ] Load balancing tested (if applicable)

---

## Deployment Steps

### 1. Backend Deployment

**Build**:

```bash
cd products/dcmaar/apps/guardian/apps/backend
npm run build
```

**Test**:

```bash
npm run test
npm run test:integration
```

**Deploy to staging**:

```bash
npm run deploy:staging
# OR manual deployment:
# 1. Copy dist/ to staging server
# 2. Install dependencies: npm install --production
# 3. Start service: pm2 start server.ts --name guardian-api
# 4. Verify: curl https://staging-api.guardian.com/health
```

**Smoke tests on staging**:

```bash
# Test events endpoint
curl -X POST https://staging-api.guardian.com/api/events \
  -H "Content-Type: application/json" \
  -d '{"events": []}'

# Should return 202 Accepted
```

**Deploy to production**:

```bash
npm run deploy:production
```

**Post-deployment verification**:

```bash
curl -v https://api.guardian.com/health
# Should return 200 OK
```

### 2. Extension Deployment

**Build all platforms**:

```bash
cd products/dcmaar/apps/guardian/apps/browser-extension
npm run build
# Generates dist/chrome, dist/firefox, dist/edge
```

**Publish to Chrome Web Store**:

```bash
# Using Chrome Web Store API
npm run publish:chrome
```

**Publish to Firefox Add-ons**:

```bash
npm run publish:firefox
```

**Publish to Edge Add-ons**:

```bash
npm run publish:edge
```

**Verification**:

- Test in private/incognito window
- Verify settings UI loads
- Test manual sync
- Check console for errors

### 3. Database Migration

**Backup before migration**:

```bash
pg_dump -h localhost -U guardian guardian_db > backup_$(date +%Y%m%d_%H%M%S).sql
```

**Run migrations**:

```bash
npm run migrate:up
```

**Verify schema**:

```bash
psql -U guardian -d guardian_db -c "\d guardian_events"
```

**Test with sample data**:

```bash
npm run seed:test
npm run test:integration
```

---

## Configuration Management

### Environment Variables

**Backend (.env)**:

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_USER=guardian
DB_PASSWORD=secure_password
DB_NAME=guardian_db

# API
API_PORT=3001
API_HOST=0.0.0.0
NODE_ENV=production

# Auth
JWT_SECRET=your-secret-key
JWT_EXPIRY=24h

# CORS
CORS_ORIGIN=https://app.guardian.com,https://admin.guardian.com

# Logging
LOG_LEVEL=info
LOG_FORMAT=json

# Sentry (error tracking)
SENTRY_DSN=https://your-sentry-url
```

**Extension (config)**:

```typescript
// dist/chrome/config.json
{
  "backendUrl": "https://api.guardian.com",
  "version": "1.0.0",
  "features": {
    "backendSync": true,
    "commandExecution": true,
    "policyUpdates": true
  }
}
```

### Secrets Management

**Using environment variables** (simple):

```bash
export DB_PASSWORD=$(aws secretsmanager get-secret-value \
  --secret-id guardian/db/password \
  --query SecretString --output text)
```

**Using HashiCorp Vault** (recommended for production):

```bash
vault login
vault read secret/guardian/db
vault kv get secret/guardian/api-keys
```

**Using AWS Secrets Manager**:

```bash
aws secretsmanager get-secret-value \
  --secret-id guardian/production \
  --query SecretString --output text | jq '.'
```

---

## Monitoring

### Application Metrics

**Health endpoint**:

```bash
curl https://api.guardian.com/health | jq '.data'
# Response includes:
# - uptime
# - version
# - database: connected|disconnected
# - memory: usage
# - requests: count, latency
```

**Prometheus metrics**:

```bash
curl https://api.guardian.com/metrics
# Includes:
# - http_request_duration_seconds
# - guardian_events_received_total
# - guardian_commands_executed_total
# - database_query_duration_seconds
```

**Set up Grafana dashboards**:

```yaml
# grafana/dashboards/guardian.json
{
  "dashboard":
    {
      "title": "Guardian Sync Dashboard",
      "panels":
        [
          {
            "title": "Events Per Second",
            "targets": [{ "expr": "rate(guardian_events_received_total[1m])" }],
          },
          {
            "title": "Sync Success Rate",
            "targets":
              [
                {
                  "expr": "rate(guardian_sync_successful_total[5m]) / rate(guardian_sync_total[5m])",
                },
              ],
          },
        ],
    },
}
```

### Logging

**Application logs**:

```bash
# Real-time tail
tail -f logs/guardian-api.log

# Search for errors
grep "ERROR" logs/guardian-api.log | tail -20

# Count by level
grep -o '\[.*\]' logs/guardian-api.log | sort | uniq -c
```

**Structured logging (JSON)**:

```bash
# Pretty print
cat logs/guardian-api.log | jq '.'

# Filter by severity
cat logs/guardian-api.log | jq 'select(.level=="ERROR")'

# Filter by service
cat logs/guardian-api.log | jq 'select(.service=="events-service")'
```

**CloudWatch Logs** (AWS):

```bash
aws logs tail /aws/guardian/api --follow

aws logs filter-log-events \
  --log-group-name /aws/guardian/api \
  --filter-pattern "ERROR"
```

### Alerting

**PagerDuty integration**:

```yaml
# prometheus/rules/guardian.yml
groups:
  - name: guardian
    rules:
      - alert: HighEventErrorRate
        expr: rate(guardian_events_error_total[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High event error rate detected"

      - alert: DatabaseConnectionLost
        expr: guardian_db_connected == 0
        for: 1m
        labels:
          severity: critical
```

**Slack notifications**:

```bash
# Send notification
curl -X POST https://hooks.slack.com/services/YOUR/WEBHOOK \
  -H 'Content-Type: application/json' \
  -d '{
    "text": "Guardian API: Event sync latency elevated",
    "attachments": [{
      "color": "danger",
      "text": "p95 latency > 1000ms for past 5 minutes"
    }]
  }'
```

---

## Maintenance

### Regular Tasks

**Daily**:

- [ ] Check error logs for critical issues
- [ ] Monitor event ingestion rates
- [ ] Verify database connectivity
- [ ] Check disk space usage

**Weekly**:

- [ ] Review performance metrics
- [ ] Check for abandoned commands (>7 days old)
- [ ] Verify backup integrity
- [ ] Update security patches

**Monthly**:

- [ ] Database optimization: `VACUUM ANALYZE`
- [ ] Review access logs
- [ ] Capacity planning analysis
- [ ] Security audit

**Quarterly**:

- [ ] Disaster recovery drill
- [ ] Performance baseline update
- [ ] Dependency updates
- [ ] Security assessment

### Database Maintenance

**Vacuum and analyze**:

```bash
psql -U guardian -d guardian_db -c "VACUUM ANALYZE;"
```

**Check bloat**:

```bash
psql -U guardian -d guardian_db -c \
  "SELECT schemaname, tablename,
    ROUND(100.0 * (pg_relation_size(schemaname||'.'||tablename) -
           pg_relation_size(schemaname||'.'||tablename, 'main')) /
           pg_relation_size(schemaname||'.'||tablename), 2) AS waste_ratio
   FROM pg_tables
   WHERE schemaname = 'public'
   ORDER BY waste_ratio DESC;"
```

**Reindex if necessary**:

```bash
psql -U guardian -d guardian_db -c "REINDEX DATABASE guardian_db;"
```

**Archive old events**:

```bash
# Keep only 90 days of events
DELETE FROM guardian_events
WHERE created_at < NOW() - INTERVAL '90 days';
```

### Cache Management

**Clear expired cache**:

```bash
redis-cli EVAL "return redis.call('del', unpack(redis.call('keys', 'guardian:*:expired')))" 0
```

**Monitor cache hit rate**:

```bash
redis-cli INFO stats | grep hits
```

### Log Rotation

**logrotate configuration**:

```bash
# /etc/logrotate.d/guardian
/var/log/guardian/*.log {
  daily
  rotate 30
  compress
  delaycompress
  notifempty
  create 0640 guardian guardian
  sharedscripts
  postrotate
    systemctl reload guardian-api > /dev/null 2>&1 || true
  endscript
}
```

---

## Disaster Recovery

### Backup Strategy

**Automated backups**:

```bash
# Daily backup at 2 AM UTC
0 2 * * * /usr/local/bin/backup-guardian.sh

# Script: /usr/local/bin/backup-guardian.sh
#!/bin/bash
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/guardian"
mkdir -p $BACKUP_DIR

# Database backup
pg_dump -h localhost -U guardian guardian_db | \
  gzip > $BACKUP_DIR/db_$DATE.sql.gz

# Retention: keep 30 days
find $BACKUP_DIR -name "db_*.sql.gz" -mtime +30 -delete

# Upload to S3
aws s3 cp $BACKUP_DIR/db_$DATE.sql.gz s3://guardian-backups/
```

**Backup verification**:

```bash
# Monthly test restore
gzip -dc $BACKUP_DIR/db_$(date -d '7 days ago' +%Y%m%d)_*.sql.gz | \
  psql -h localhost -U guardian -d guardian_db_test
```

### Recovery Procedures

**Database Recovery**:

```bash
# 1. Stop application
systemctl stop guardian-api

# 2. Restore from backup
psql -U guardian -d guardian_db < backup_20251201_000000.sql

# 3. Verify integrity
psql -U guardian -d guardian_db -c "SELECT COUNT(*) FROM guardian_events;"

# 4. Restart application
systemctl start guardian-api

# 5. Verify
curl https://localhost:3001/health
```

**Service Recovery**:

```bash
# Restart individual service
systemctl restart guardian-api

# Check status
systemctl status guardian-api

# Check logs
journalctl -u guardian-api -f
```

**Partial Data Recovery**:

```bash
# If specific records are corrupted, restore point-in-time:
# (requires WAL archiving enabled)

psql -U guardian -d guardian_db -c \
  "SELECT pg_restore_to_time('2025-12-01 10:00:00');"
```

### Failover Procedures

**Switch to standby** (if using hot standby):

```bash
# On standby server
sudo -u postgres pg_ctl promote -D /var/lib/postgresql/14/main

# Verify it's now primary
psql -U guardian -d guardian_db -c "SELECT pg_is_in_recovery();"
# Should return: f (false)

# Update application config to point to new primary
# Restart application
systemctl restart guardian-api
```

---

## Rollback Procedures

### Rolling Back Backend

**If deployment introduces bugs**:

```bash
# 1. Identify last good version
git log --oneline | head -5

# 2. Build previous version
git checkout v1.0.1
npm run build

# 3. Deploy previous version
npm run deploy:production

# 4. Verify
curl https://api.guardian.com/health
```

### Rolling Back Extension

**Manual rollback** (users):

1. Open `chrome://extensions/`
2. Find Guardian
3. Click menu → "Manage extensions"
4. Uninstall
5. Search Chrome Web Store for older version
6. Install

**Automatic rollback** (if version has kill switch):

```javascript
// In latest version manifest
if (VERSION < MINIMUM_REQUIRED) {
  chrome.management.uninstallSelf();
}
```

---

## Scaling Considerations

### Horizontal Scaling

**Adding backend instances**:

```bash
# 1. Deploy new instance with same config
npm run deploy:production --instance=2

# 2. Update load balancer
# Add new instance IP to load balancer group

# 3. Verify traffic routing
curl -v https://api.guardian.com/health
# Should see different instance IDs on repeated requests
```

**Database read replicas**:

```yaml
# docker-compose for development
services:
  postgres-primary:
    image: postgres:14
    environment:
      POSTGRES_REPLICATION_MODE: master

  postgres-replica:
    image: postgres:14
    environment:
      POSTGRES_REPLICATION_MODE: slave
      POSTGRES_MASTER_SERVICE: postgres-primary
```

### Vertical Scaling

**Increase resources**:

```bash
# On server, add CPU cores (if VM)
# Verify PostgreSQL uses more:
psql -U guardian -d guardian_db -c "SHOW shared_buffers;"

# Adjust for more RAM:
psql -U guardian -d guardian_db -c \
  "ALTER SYSTEM SET shared_buffers = '2GB';
   SELECT pg_reload_conf();"
```

### Cache Layer

**Add Redis for command cache**:

```typescript
// In CommandSyncSource
const redis = new Redis();
const cacheKey = `commands:${deviceId}`;

// Check cache first
let commands = await redis.get(cacheKey);
if (!commands) {
  commands = await fetchFromBackend();
  await redis.setex(cacheKey, 60, commands); // 1 min TTL
}
```

---

## Documentation

- Keep runbooks updated with each deployment
- Document any manual interventions
- Record incident post-mortems
- Update architecture diagrams
- Maintain deployment checklist

**Deployment checklist template**:

```markdown
# Deployment Checklist - Version X.Y.Z

Date: 2025-12-01
Deployed by: Your Name
Environment: Production

- [ ] Pre-deployment tests passed
- [ ] Database migrations completed successfully
- [ ] Smoke tests passed
- [ ] Metrics normal
- [ ] No error spikes in logs
- [ ] Monitoring alerts verified
- [ ] Communication sent to stakeholders
- [ ] Documentation updated

Issues encountered:
(None)

Rollback plan (if needed):
Git tag: vX.Y.Z-previous
Estimated rollback time: 10 minutes
```
