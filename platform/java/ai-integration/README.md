# AI Platform

## Overview

The AI Platform provides shared infrastructure for managing AI models, features, and observability across all Ghatana products.

## Purpose

The AI Platform capabilities (Model Registry, Feature Store, Observability) are integrated as packages within this module:

- `com.ghatana.aiplatform.registry.*` - Model registry capabilities
- `com.ghatana.aiplatform.observability.*` - AI observability capabilities
- `com.ghatana.aiplatform.featurestore.*` - Feature store capabilities

These are currently in development. The module provides LLM/embedding integrations via the ai-integration package.

## Current Implementation

The ai-integration module provides:

- LLM client integrations (OpenAI, etc.)
- Embedding generation
- AI gateway abstractions
- Vector store integration
- Policy guard rails

## Package Structure

```
com.ghatana.ai.*                      — LLM clients, embeddings, prompts, vector store
com.ghatana.aiplatform.registry.*     — Model registry, version control, deployment tracking
com.ghatana.aiplatform.observability.* — AI metrics, cost tracking, drift detection
com.ghatana.aiplatform.featurestore.*  — Feature engineering, storage, and serving
```

## Planned Key Capabilities

### Model Registry (PLANNED)

- Model metadata storage (name, version, framework, parameters)
- Deployment status tracking (staged, production, deprecated)
- Model lineage and provenance
- A/B testing support

### Feature Store (PLANNED)

- Real-time feature computation from EventCloud
- Historical feature storage (Postgres/ClickHouse)
- Feature serving API with low latency (<10ms p99)
- Feature versioning and schema evolution

### Observability (PLANNED)

- Model performance metrics (latency, throughput, error rate)
- Prediction quality tracking (accuracy, F1, precision, recall)
- Data drift detection
- Model staleness alerts

## Integration Points

Products can integrate with the AI Platform for:

- Feature computation from event patterns
- Model serving in real-time pipelines
- Prediction result events back to event systems
- Agent behavior models
- Task recommendation engines
- KPI prediction models
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
# Run ai-integration tests
./gradlew :platform:java:ai-integration:test
```

## Documentation

Submodule documentation (planned, not yet implemented):

- Model Registry - Planned
- Feature Store - Planned
- AI Observability - Planned

## Related Modules

- `libs:ai-integration` - LLM/embedding integrations (OpenAI)
- Product-specific modules for real-time event processing and AI features

@doc.type library
@doc.purpose Shared AI infrastructure for model management and feature serving
@doc.layer platform
@doc.pattern Platform Service
