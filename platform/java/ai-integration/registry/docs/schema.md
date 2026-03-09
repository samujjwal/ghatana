# Model Registry Database Schema

## Tables

### model_registry

Primary table for storing ML model metadata and deployment tracking.

```sql
CREATE TABLE model_registry (
    -- Identity
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    
    -- Model identification
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    framework VARCHAR(50),
    
    -- Deployment tracking
    deployment_status VARCHAR(50) NOT NULL,
    
    -- Flexible metadata storage
    metadata JSONB,
    training_metrics JSONB,
    
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_at TIMESTAMP,
    deprecated_at TIMESTAMP,
    
    -- Constraints
    UNIQUE (tenant_id, name, version),
    CHECK (deployment_status IN ('DEVELOPMENT', 'STAGED', 'PRODUCTION', 'CANARY', 'DEPRECATED', 'RETIRED'))
);

-- Indexes for common queries
CREATE INDEX idx_model_registry_tenant_status 
    ON model_registry(tenant_id, deployment_status);
    
CREATE INDEX idx_model_registry_tenant_name 
    ON model_registry(tenant_id, name);
    
CREATE INDEX idx_model_registry_created_at 
    ON model_registry(tenant_id, created_at DESC);
```

## Columns

### Identity Columns
- **id**: UUID primary key, auto-generated
- **tenant_id**: Tenant identifier for multi-tenancy isolation (NOT NULL)

### Model Identification
- **name**: Model name (e.g., "pattern-recommender", "churn-predictor")
- **version**: Semantic version string (e.g., "v1.2.0", "v2.0.0-beta")
- **framework**: ML framework used (e.g., "tensorflow", "pytorch", "scikit-learn")

### Deployment Tracking
- **deployment_status**: Current deployment state
  - DEVELOPMENT: Under development
  - STAGED: Ready for testing
  - PRODUCTION: Actively serving
  - CANARY: Partial rollout
  - DEPRECATED: Scheduled for removal
  - RETIRED: No longer available

### Metadata
- **metadata**: JSONB column for flexible model metadata
  ```json
  {
    "description": "Pattern recommendation model",
    "author": "data-science-team",
    "training_dataset": "events_2024_q1",
    "hyperparameters": {
      "learning_rate": 0.001,
      "batch_size": 32,
      "epochs": 100
    }
  }
  ```

- **training_metrics**: JSONB column for training performance metrics
  ```json
  {
    "accuracy": 0.92,
    "precision": 0.89,
    "recall": 0.91,
    "f1_score": 0.90,
    "loss": 0.15
  }
  ```

### Timestamps
- **created_at**: When model was first registered (NOT NULL)
- **updated_at**: Last metadata update (NOT NULL)
- **deployed_at**: When promoted to PRODUCTION (nullable)
- **deprecated_at**: When marked as DEPRECATED (nullable)

## Constraints

### Uniqueness
- **(tenant_id, name, version)** - No duplicate model versions per tenant

### Check Constraints
- **deployment_status** must be one of the valid enum values

### NOT NULL Constraints
- tenant_id, name, version, deployment_status, created_at, updated_at must have values

## Indexes

### Performance Indexes
1. **idx_model_registry_tenant_status** - Fast queries by deployment status
   - Used for: `findByStatus(tenant, status)`
   - Cardinality: Low (6 status values)
   - Selectivity: Medium

2. **idx_model_registry_tenant_name** - Fast queries by model name
   - Used for: `findByName(tenant, name)`, `listVersions(tenant, name)`
   - Cardinality: Medium (dozens to hundreds of models)
   - Selectivity: High

3. **idx_model_registry_created_at** - Fast chronological queries
   - Used for: Audit queries, recent model queries
   - Cardinality: High (timestamp precision)
   - Selectivity: Very high

## Query Patterns

### Find Specific Model Version
```sql
SELECT * FROM model_registry
WHERE tenant_id = ? AND name = ? AND version = ?;
```
**Performance**: O(1) lookup via unique index, <5ms

### Find All Production Models
```sql
SELECT * FROM model_registry
WHERE tenant_id = ? AND deployment_status = 'PRODUCTION'
ORDER BY updated_at DESC;
```
**Performance**: Index scan, <20ms for typical deployments

### List All Versions
```sql
SELECT * FROM model_registry
WHERE tenant_id = ? AND name = ?
ORDER BY created_at DESC;
```
**Performance**: Index scan, <10ms

### Update Deployment Status
```sql
UPDATE model_registry
SET deployment_status = ?,
    updated_at = ?,
    deployed_at = CASE WHEN ? = 'PRODUCTION' THEN ? ELSE deployed_at END
WHERE tenant_id = ? AND id = ?;
```
**Performance**: Single row update via PK, <5ms

## Migration Scripts

### Initial Schema
See: `libs/java/ai-platform/registry/src/main/resources/db/migration/V001__create_model_registry.sql`

### Adding Columns
Always use ALTER TABLE with IF NOT EXISTS logic:
```sql
ALTER TABLE model_registry
ADD COLUMN IF NOT EXISTS new_column VARCHAR(255);
```

### Backfilling Data
Use batched updates with WHERE clauses to avoid table locks:
```sql
UPDATE model_registry
SET new_column = 'default_value'
WHERE new_column IS NULL
LIMIT 1000;
```

## Best Practices

### Tenant Isolation
- ALWAYS filter by tenant_id in queries
- Use prepared statements to prevent SQL injection
- Validate tenant access at application layer before queries

### JSONB Usage
- Keep metadata JSONB documents reasonably sized (<10KB)
- Use GIN indexes for JSONB queries if needed
- Validate JSON structure at application layer

### Timestamps
- Use `Instant.now()` for consistency (UTC)
- Never use database timestamps directly in application logic
- Always set updated_at when mutating rows

### Versioning
- Use semantic versioning (major.minor.patch)
- Never reuse version numbers
- Archive old versions rather than deleting

## Maintenance

### Vacuum
Regular VACUUM to reclaim space from updates:
```sql
VACUUM ANALYZE model_registry;
```

### Statistics
Update statistics for query planner:
```sql
ANALYZE model_registry;
```

### Archival
Move RETIRED models to archive table after 90 days:
```sql
INSERT INTO model_registry_archive
SELECT * FROM model_registry
WHERE deployment_status = 'RETIRED'
  AND deprecated_at < NOW() - INTERVAL '90 days';

DELETE FROM model_registry
WHERE deployment_status = 'RETIRED'
  AND deprecated_at < NOW() - INTERVAL '90 days';
```
