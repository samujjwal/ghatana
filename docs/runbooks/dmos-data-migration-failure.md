# DMOS Data Migration Failure Runbook

## Summary
This runbook covers diagnosing and responding to data migration failures in DMOS.

## Symptoms
- Migration job failing
- Data inconsistencies between source and target
- Migration progress stalled
- Errors in migration logs

## Diagnosis

### 1. Check Migration Status
```bash
# Check migration job status
kubectl get job dmos-migration-job -o yaml

# Check migration logs
kubectl logs job/dmos-migration-job
```

### 2. Check Database State
```bash
# Check Flyway migration status
curl http://dmos-application:8081/actuator/flyway

# Check database connection
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
```

### 3. Check Data Consistency
```bash
# Check row counts
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -c "SELECT COUNT(*) FROM dmos_approval_snapshots;"
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -c "SELECT COUNT(*) FROM dmos_ai_action_log;"

# Check for data integrity issues
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -c "SELECT COUNT(*) FROM dmos_approval_snapshots WHERE version IS NULL;"
```

### 4. Check Migration Logs
```bash
# Check for migration errors
kubectl logs job/dmos-migration-job | grep -i error

# Check for validation errors
kubectl logs job/dmos-migration-job | grep -i validation
```

## Response

### Flyway Migration Failure
1. Identify the failing migration script
2. Check SQL syntax and logic
3. Check for data conflicts (duplicate keys, foreign key constraints)
4. Fix the migration script
5. Mark migration as repaired or create repair migration
6. Re-run migration

### Data Validation Failure
1. Identify validation rules that failed
2. Check source data for issues
3. Fix source data or adjust validation rules
4. Re-run validation
5. Proceed with migration if validation passes

### Rollback Failure
1. Check if rollback script exists
2. Verify rollback script is correct
3. Manually revert changes if needed
4. Create new rollback script
5. Test rollback in non-production environment

### Connection Issues
1. Check database connectivity
2. Verify database credentials
3. Check network policies
4. Verify database is not in maintenance mode
5. Retry migration after connectivity restored

## Manual Data Recovery

### Partial Migration Recovery
```bash
# Identify last successful migration
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -c "SELECT * FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;"

# Manually apply remaining changes if safe
# Or mark failed migration as resolved and continue
```

### Data Consistency Check
```bash
# Run data consistency checks
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -c "SELECT COUNT(*) FROM dmos_approval_snapshots WHERE workspace_id IS NULL;"

# Fix orphaned records if safe to do so
```

## Re-run Migration

### Flyway Repair
```bash
# Repair Flyway schema history if checksum mismatch
kubectl exec -it dmos-migration-job -- flyway repair

# Re-run migration
kubectl delete job dmos-migration-job
# Re-apply migration job
```

### Manual Migration
```bash
# Apply migration SQL manually if automated approach fails
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -f /path/to/migration.sql

# Update Flyway schema history
kubectl exec -it dmos-postgres-0 -- psql -U dmos_user -d dmos_db -c "INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) VALUES (1, 'V1__description', 'description', 'SQL', 'V1__description.sql', 'checksum', 'user', NOW(), 1000, true);"
```

## Verification
- Migration job completes successfully
- Flyway schema history shows all migrations applied
- Data consistency checks pass
- Application starts successfully
- No errors in application logs related to database schema

## Prevention
- Test migrations in non-production environment first
- Use database transactions in migration scripts
- Add rollback scripts for critical migrations
- Implement data validation before and after migration
- Set up alerts for migration failures

## Escalation
- If migration fails in production, escalate to on-call engineer
- If data loss occurs, escalate to engineering team and DBA
- If migration cannot be repaired, escalate to platform team
- If rollback is needed but not possible, declare incident

## Related Dashboards
- DMOS API Health: `/d/dmos-api-health`
- Database metrics in Prometheus
