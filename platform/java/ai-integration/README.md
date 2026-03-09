# AI Platform

## Overview

The AI Platform provides shared infrastructure for managing AI models, features, and observability across all Ghatana products (Virtual-Org, Software-Org, YAPPC, AEP).

## Purpose

Centralized platform for:
- **Model Registry** - Version control, metadata, deployment tracking for ML models
- **Feature Store** - Feature engineering, storage, and serving for ML pipelines
- **Observability** - AI-specific metrics (latency, drift, accuracy) and monitoring

## Architecture

```
ai-platform/
├── registry/          # Model version control & deployment
├── feature-store/     # Feature ingestion & serving
└── observability/     # AI metrics & monitoring
```

## Key Capabilities

### Model Registry
- Model metadata storage (name, version, framework, parameters)
- Deployment status tracking (staged, production, deprecated)
- Model lineage and provenance
- A/B testing support

### Feature Store
- Real-time feature computation from EventCloud
- Historical feature storage (Postgres/ClickHouse)
- Feature serving API with low latency (<10ms p99)
- Feature versioning and schema evolution

### Observability
- Model performance metrics (latency, throughput, error rate)
- Prediction quality tracking (accuracy, F1, precision, recall)
- Data drift detection
- Model staleness alerts

## Usage

### Registry Example
```java
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.aiplatform.registry.ModelMetadata;

ModelRegistryService registry = new ModelRegistryService(dataSource, metrics);

// Register new model
ModelMetadata model = ModelMetadata.builder()
    .name("pattern-recommender")
    .version("v1.2.0")
    .framework("tensorflow")
    .deploymentStatus(DeploymentStatus.STAGED)
    .build();

registry.register("tenant-123", model);

// Query deployed models
List<ModelMetadata> prodModels = registry.findByStatus(
    "tenant-123", 
    DeploymentStatus.PRODUCTION
);
```

### Feature Store Example
```java
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.featurestore.Feature;

FeatureStoreService featureStore = new FeatureStoreService(dataSource, eventCloud);

// Ingest feature from event
Feature feature = Feature.builder()
    .name("user_event_count_7d")
    .entityId("user-123")
    .value(42.0)
    .timestamp(Instant.now())
    .build();

featureStore.ingest("tenant-123", feature);

// Serve feature for inference
Map<String, Double> features = featureStore.getFeatures(
    "tenant-123",
    "user-123",
    List.of("user_event_count_7d", "avg_session_duration")
);
```

### Observability Example
```java
import com.ghatana.aiplatform.observability.AiMetricsEmitter;

AiMetricsEmitter aiMetrics = new AiMetricsEmitter(metricsCollector);

// Track model inference
aiMetrics.recordInference(
    "pattern-recommender",
    "v1.2.0",
    Duration.ofMillis(45),
    true // success
);

// Track prediction quality
aiMetrics.recordPredictionQuality(
    "pattern-recommender",
    "v1.2.0",
    0.87 // F1 score
);
```

## Integration Points

### With AEP
- Feature computation from event patterns
- Model serving in real-time pipelines
- Prediction result events back to EventCloud

### With Virtual-Org
- Agent behavior models
- Task recommendation engines
- KPI prediction models

### With Software-Org
- Code quality models
- Sprint velocity predictions
- Incident classification models

## Dependencies

- `libs:database` - Postgres for metadata and features
- `libs:event-cloud` - Real-time feature computation
- `libs:observability` - Platform metrics integration
- `libs:redis-cache` - Feature caching

## Performance Targets

- Model registry queries: <50ms p99
- Feature serving: <10ms p99
- Feature ingestion throughput: >10k features/sec
- Feature cache hit rate: >95%

## Security

- Tenant isolation enforced at all layers
- Model access controlled via RBAC
- Feature data encrypted at rest
- Audit logs for all model deployments

## Testing

```bash
# Run all tests
./gradlew :libs:ai-platform:test

# Run specific module tests
./gradlew :libs:ai-platform:registry:test
./gradlew :libs:ai-platform:feature-store:test
./gradlew :libs:ai-platform:observability:test
```

## Documentation

Detailed module documentation:
- [Model Registry](./registry/README.md)
- [Feature Store](./feature-store/README.md)
- [AI Observability](./observability/README.md)

## Related Modules

- `libs:ai-integration` - LLM/embedding integrations (OpenAI)
- `products:agentic-event-processor` - Real-time event processing
- `products:yappc` - Low-code app platform with AI features

@doc.type library
@doc.purpose Shared AI infrastructure for model management and feature serving
@doc.layer platform
@doc.pattern Platform Service
