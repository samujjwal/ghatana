# YAPPC Database Migrations

This directory contains Flyway database migrations for the YAPPC platform.

## Migration Naming Convention

Flyway uses a strict naming convention for migration files:

### Versioned Migrations
Format: `V{version}__{description}.sql`

Examples:
- `V1__Initial_schema.sql` — Initial database schema
- `V2__Add_user_preferences.sql` — Add user preferences table
- `V3__Add_approval_workflow_indexes.sql` — Performance optimization

### Repeatable Migrations
Format: `R__{description}.sql`

Examples:
- `R__Create_views.sql` — Database views (re-run on change)
- `R__Seed_reference_data.sql` — Reference data (re-run on change)

## Migration Execution

Migrations run automatically on application startup via `FlywayConfiguration.runMigrations()`.

### Execution Order
1. Baseline (if needed) — Version 0
2. Versioned migrations in order (V1, V2, V3, ...)
3. Repeatable migrations (alphabetically)

### Migration State
Flyway tracks migration state in the `flyway_schema_history` table:

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

## Creating New Migrations

### Step 1: Create Migration File

```bash
cd products/yappc/backend/api/src/main/resources/db/migration

# Next version number (check existing files)
touch V4__Add_feature_flags.sql
```

### Step 2: Write Migration SQL

```sql
-- V4__Add_feature_flags.sql
CREATE TABLE feature_flags (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL REFERENCES tenants(id),
    flag_key VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, flag_key)
);

CREATE INDEX idx_feature_flags_tenant ON feature_flags(tenant_id);
```

### Step 3: Test Migration

```bash
# Start PostgreSQL
docker-compose up -d postgres

# Run application (migrations run automatically)
cd products/yappc
./gradlew :backend:api:bootRun
```

### Step 4: Verify Migration

```sql
-- Check migration was applied
SELECT * FROM flyway_schema_history WHERE version = '4';

-- Verify table exists
\d feature_flags
```

## Best Practices

### ✅ DO

- **Use sequential version numbers** — V1, V2, V3 (no gaps)
- **Make migrations idempotent** — Use `IF NOT EXISTS`, `IF EXISTS`
- **Test migrations locally** — Before committing
- **Include rollback notes** — In migration comments
- **Keep migrations small** — One logical change per migration
- **Add indexes** — For foreign keys and frequently queried columns
- **Use transactions** — Flyway wraps each migration in a transaction

### ❌ DON'T

- **Modify existing migrations** — Once applied, migrations are immutable
- **Use database-specific syntax** — Unless necessary (document it)
- **Include data that changes** — Use repeatable migrations for reference data
- **Skip version numbers** — Maintain sequential order
- **Delete migrations** — Even if not yet applied to production

## Migration Patterns

### Adding a Column

```sql
-- V5__Add_user_avatar.sql
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500);
```

### Adding a Table with Foreign Keys

```sql
-- V6__Add_notifications.sql
CREATE TABLE notifications (
    id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL REFERENCES tenants(id),
    user_id VARCHAR(255) NOT NULL REFERENCES users(id),
    message TEXT NOT NULL,
    read BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notifications_tenant ON notifications(tenant_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_notifications_read ON notifications(read);
```

### Adding an Index

```sql
-- V7__Add_audit_event_indexes.sql
CREATE INDEX idx_audit_events_event_type ON audit_events(event_type);
CREATE INDEX idx_audit_events_action ON audit_events(action);
```

### Data Migration

```sql
-- V8__Migrate_legacy_statuses.sql
UPDATE requirements 
SET status = 'IN_PROGRESS' 
WHERE status = 'STARTED';

UPDATE requirements 
SET status = 'COMPLETED' 
WHERE status = 'DONE';
```

## Rollback Strategy

Flyway does not support automatic rollback. For rollback:

1. **Create a new migration** that reverses the change
2. **Document rollback SQL** in migration comments

Example:

```sql
-- V9__Add_user_bio.sql
-- Rollback: ALTER TABLE users DROP COLUMN bio;

ALTER TABLE users ADD COLUMN bio TEXT;
```

To rollback:

```sql
-- V10__Rollback_user_bio.sql
ALTER TABLE users DROP COLUMN bio;
```

## Troubleshooting

### Migration Failed

```
ERROR: Migration V5__Add_user_avatar.sql failed
```

**Solution:**
1. Fix the SQL in the migration file
2. Manually repair the migration state:

```sql
-- Mark failed migration as resolved
DELETE FROM flyway_schema_history WHERE version = '5';
```

3. Restart application to re-run migration

### Out of Order Migration

```
ERROR: Detected resolved migration not applied to database: V3
```

**Solution:**
- Ensure all migrations are in the classpath
- Check migration file naming
- Verify `flyway_schema_history` table

### Checksum Mismatch

```
ERROR: Migration checksum mismatch for migration V2
```

**Cause:** Migration file was modified after being applied

**Solution:**
- **Never modify applied migrations**
- Create a new migration instead
- If absolutely necessary, repair checksum:

```sql
UPDATE flyway_schema_history 
SET checksum = <new_checksum> 
WHERE version = '2';
```

## Environment-Specific Migrations

### Development

Migrations run automatically on startup.

### Staging/Production

Migrations run automatically on deployment. Monitor logs:

```bash
# Check migration logs
kubectl logs -f deployment/yappc-api | grep Flyway
```

## References

- [Flyway Documentation](https://flywaydb.org/documentation/)
- [Flyway SQL Migrations](https://flywaydb.org/documentation/concepts/migrations#sql-based-migrations)
- [Flyway Best Practices](https://flywaydb.org/documentation/bestpractices)
