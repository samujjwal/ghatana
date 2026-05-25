# Owner: Feature-Store Ingest Service

**Team:** Data-Cloud Team  
**Slack:** #data-cloud  
**On-call:** Data-Cloud on-call rotation  
**Decision:** ADR-013 (migrated from shared-services 2026-03-22)

## Responsibility

Real-time feature ingestion pipeline that consumes events from the Data-Cloud EventLog and writes features into the Feature Store for ML pipelines.

## Service Details

- **Entry point:** `FeatureStoreIngestLauncher` (ActiveJ Eventloop)
- **Dependencies:** `platform:java:ai-integration`, Data-Cloud EventLog SPI, PostgreSQL, Redis

## Previous Location

This service was previously located at `shared-services/feature-store-ingest`. It was migrated to `products/data-cloud/planes/intelligence/feature-ingest` per the [Shared-Services Strategy](../../docs/SHARED_SERVICES_STRATEGY.md) and ADR-013, because it is product-specific to the Data-Cloud ingestion pipeline.
