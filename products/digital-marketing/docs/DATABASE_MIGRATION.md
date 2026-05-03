# DMOS Database Migration Guide

## Overview

DMOS uses Flyway for database migrations. Migrations are located in `dm-persistence/src/main/resources/db/migration/`.

## Running Migrations

### All Migrations

```bash
../../gradlew :dm-persistence:flywayMigrate
```

### Specific Migration

```bash
../../gradlew :dm-persistence:flywayMigrate -Dflyway.target=V8
```

## Creating a New Migration

### 1. Create the Migration File

Name the file following Flyway convention: `V{version}__{description}.sql`

Example: `V11__add_new_feature.sql`

### 2. Write the Migration SQL

```sql
-- V11__add_new_feature.sql
CREATE TABLE dmos_new_feature (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_new_feature_tenant_workspace ON dmos_new_feature(tenant_id, workspace_id);
```

### 3. Test the Migration

```bash
../../gradlew :dm-persistence:flywayMigrate
```

### 4. Add Rollback (Optional)

Flyway doesn't support rollbacks by default. Create a new migration to revert changes:

```sql
-- V12__revert_new_feature.sql
DROP INDEX IF EXISTS idx_new_feature_tenant_workspace;
DROP TABLE IF EXISTS dmos_new_feature;
```

## Migration Best Practices

- Use descriptive names for migrations
- Include indexes for frequently queried columns
- Add foreign key constraints where appropriate
- Use `NOT NULL` constraints for required fields
- Add comments for complex logic
- Test migrations in development first

## Troubleshooting

### Migration Fails

1. Check the Flyway schema history table:
   ```sql
   SELECT * FROM flyway_schema_history ORDER BY installed_rank;
   ```

2. Identify the failed migration

3. Fix the SQL in the migration file

4. Repair the Flyway schema:
   ```bash
   ../../gradlew :dm-persistence:flywayRepair
   ```

5. Rerun the migration

### Schema Drift

If the schema has drifted from Flyway's expected state:

1. Compare actual schema with Flyway history
2. Create a new migration to align the schema
3. Do not manually modify existing migrations

## Existing Migrations

- V1: Initial schema
- V2: Add approvals table
- V3: Add strategies table
- V4: Add campaigns table
- V5: Add optimistic version columns
- V6: Add suppression hash column
- V7: Add credential revocation columns
- V8: Create API keys table
- V9: Create data subject requests table
- V10: Seed demo data
