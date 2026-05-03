# Database Issues Runbook

## Severity

Critical

## Symptoms

- Database connection failures
- Slow query performance
- Connection pool exhaustion
- Migration failures
- Alerts firing for database health

## Initial Response

1. Check database pod status
2. Check database logs
3. Check connection pool metrics
4. Identify which queries are slow

## Investigation Steps

### 1. Check Database Pod

```bash
kubectl get pods -l app=postgres
kubectl describe pod postgres-0
kubectl logs postgres-0 --tail=1000
```

### 2. Check Database Connection

```bash
kubectl exec -it postgres-0 -- psql -U dmos -d dmos -c "SELECT 1"
```

### 3. Check Connection Pool

Check Grafana dashboard for:
- Active connections
- Idle connections
- Pool utilization

### 4. Identify Slow Queries

```sql
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

### 5. Check Lock Contention

```sql
SELECT pid, relation, mode, granted
FROM pg_locks
WHERE NOT granted;
```

## Common Causes and Solutions

### Connection Pool Exhaustion

**Symptoms:** "Connection refused", "Pool exhausted"

**Solution:**
```bash
# Check pool size
# Increase pool size in configuration
export DATABASE_POOL_SIZE=20

# Restart application
kubectl rollout restart deployment/dmos-api
```

### Slow Queries

**Symptoms:** High query duration, timeouts

**Solution:**
- Add missing indexes
- Optimize query
- Increase database resources
- Consider read replicas

### Lock Contention

**Symptoms:** Queries waiting for locks

**Solution:**
```sql
-- Identify blocking queries
SELECT pid, query
FROM pg_stat_activity
WHERE state = 'active'
  AND pid IN (SELECT blocking_pid FROM pg_locks WHERE NOT granted);

-- Kill blocking session if safe
SELECT pg_terminate_backend(pid);
```

### Migration Failure

**Symptoms:** Migration fails to apply

**Solution:**
```bash
# Check Flyway schema history
kubectl exec -it postgres-0 -- psql -U dmos -d dmos -c "SELECT * FROM flyway_schema_history"

# Repair Flyway
../../gradlew :dm-persistence:flywayRepair

# Rerun migration
../../gradlew :dm-persistence:flywayMigrate
```

### Disk Space Full

**Symptoms:** "No space left on device"

**Solution:**
```bash
# Check disk usage
kubectl exec -it postgres-0 -- df -h

# Clean up old data
kubectl exec -it postgres-0 -- psql -U dmos -d dmos -c "VACUUM FULL;"
```

## Escalation

If unable to resolve within 15 minutes:
1. Notify on-call engineering
2. Notify DBA team
3. Consider database failover if available
4. Communicate status to stakeholders

## Recovery

1. Implement fix
2. Verify database health
3. Restart application
4. Monitor for 30 minutes
5. Document incident
6. Update runbook if needed
