# Model Registry

## Overview

The Model Registry provides version control, metadata management, and deployment tracking for machine learning models used across the Ghatana platform.

## Purpose

- **Version Control**: Track model versions with semantic versioning
- **Metadata Storage**: Store model framework, parameters, training metrics
- **Deployment Tracking**: Monitor which models are staged/production/deprecated
- **Lineage**: Track model provenance and training data
- **A/B Testing**: Support gradual rollout and experiment tracking

## Key Components

### ModelMetadata
Value object representing model metadata:
- Model name, version, framework (TensorFlow, PyTorch, etc.)
- Training metrics (accuracy, loss, F1)
- Deployment status (staged, production, deprecated)
- Creation and deployment timestamps

### ModelRegistryService
Core service for CRUD operations:
- `register()` - Register new model version
- `findByName()` - Query models by name
- `findByStatus()` - Find models by deployment status
- `updateStatus()` - Promote/demote models
- `listVersions()` - Get all versions of a model

### ModelDeploymentStatus
Enum representing deployment lifecycle:
- `DEVELOPMENT` - Under development
- `STAGED` - Ready for testing
- `PRODUCTION` - Actively serving traffic
- `CANARY` - Partial rollout
- `DEPRECATED` - Scheduled for removal
- `RETIRED` - No longer in use

## Architecture

```
┌─────────────────────────────────────┐
│   ModelRegistryService              │
├─────────────────────────────────────┤
│ + register(tenant, metadata)        │
│ + findByName(tenant, name)          │
│ + findByStatus(tenant, status)      │
│ + updateStatus(tenant, id, status)  │
│ + listVersions(tenant, name)        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   PostgreSQL Database                │
├─────────────────────────────────────┤
│ Table: model_registry                │
│ - id (UUID, PK)                      │
│ - tenant_id (VARCHAR, NOT NULL)      │
│ - name (VARCHAR, NOT NULL)           │
│ - version (VARCHAR, NOT NULL)        │
│ - framework (VARCHAR)                │
│ - deployment_status (VARCHAR)        │
│ - metadata (JSONB)                   │
│ - created_at (TIMESTAMP)             │
│ - updated_at (TIMESTAMP)             │
│                                       │
│ Indexes:                              │
│ - (tenant_id, name, version) UNIQUE  │
│ - (tenant_id, deployment_status)     │
└─────────────────────────────────────┘
```

## Usage

### Register a Model
```java
ModelMetadata model = ModelMetadata.builder()
    .name("pattern-recommender")
    .version("v1.2.0")
    .framework("tensorflow")
    .deploymentStatus(DeploymentStatus.STAGED)
    .trainingMetrics(Map.of(
        "accuracy", 0.92,
        "f1_score", 0.89
    ))
    .build();

modelRegistry.register("tenant-123", model);
```

### Query Models
```java
// Find specific model
Optional<ModelMetadata> model = modelRegistry.findByName(
    "tenant-123",
    "pattern-recommender",
    "v1.2.0"
);

// Find all production models
List<ModelMetadata> prodModels = modelRegistry.findByStatus(
    "tenant-123",
    DeploymentStatus.PRODUCTION
);

// List all versions
List<ModelMetadata> versions = modelRegistry.listVersions(
    "tenant-123",
    "pattern-recommender"
);
```

### Update Deployment Status
```java
// Promote to production
modelRegistry.updateStatus(
    "tenant-123",
    modelId,
    DeploymentStatus.PRODUCTION
);

// Deprecate old version
modelRegistry.updateStatus(
    "tenant-123",
    oldModelId,
    DeploymentStatus.DEPRECATED
);
```

## Database Schema

```sql
CREATE TABLE model_registry (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    version VARCHAR(50) NOT NULL,
    framework VARCHAR(50),
    deployment_status VARCHAR(50) NOT NULL,
    metadata JSONB,
    training_metrics JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deployed_at TIMESTAMP,
    deprecated_at TIMESTAMP,
    UNIQUE (tenant_id, name, version)
);

CREATE INDEX idx_model_registry_tenant_status 
    ON model_registry(tenant_id, deployment_status);
    
CREATE INDEX idx_model_registry_tenant_name 
    ON model_registry(tenant_id, name);
```

## Integration Points

### With Feature Store
- Models reference feature sets for inference
- Training pipeline registers models after training on features

### With AEP
- Model predictions emitted as events to EventCloud
- Pattern matching models registered for real-time inference

### With Observability
- Model performance metrics tracked via `AiMetricsEmitter`
- Alerts on model drift or degradation

## Performance Characteristics

- Query by name: O(1) with index, <10ms p99
- List by status: O(log n) with index, <50ms p99
- Registration: O(1), <100ms p99
- Tenant isolation: All queries filtered by tenant_id

## Security

- Tenant isolation enforced at query level
- RBAC for model registration/promotion
- Audit log for all state changes
- Metadata sanitized before storage

## Testing

```bash
# Run unit tests
./gradlew :libs:ai-platform:registry:test

# Run integration tests with Testcontainers
./gradlew :libs:ai-platform:registry:integrationTest
```

## Metrics

Emitted via `libs:observability`:
- `model.registry.register.count` - Model registrations
- `model.registry.register.duration` - Registration latency
- `model.registry.query.duration` - Query latency
- `model.registry.status.update.count` - Status changes

## Future Enhancements

- Model artifact storage (S3/GCS integration)
- Automatic drift detection
- Model comparison and A/B test analytics
- Rollback automation
- Model approval workflows

## Related Documentation

- [Feature Store](../feature-store/README.md)
- [AI Observability](../observability/README.md)
- [Database Schema](./docs/schema.md)
- [API Reference](./docs/api.md)

@doc.type library
@doc.purpose ML model version control and deployment tracking
@doc.layer platform
@doc.pattern Repository + Service
