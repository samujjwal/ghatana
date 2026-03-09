# Feature Store

## Overview

Feature Store provides real-time feature computation, storage, and serving for ML pipelines across the Ghatana platform.

## Purpose

- **Feature Engineering**: Compute features from EventCloud events in real-time
- **Feature Storage**: Store historical features in Postgres/ClickHouse
- **Feature Serving**: Low-latency feature retrieval (<10ms p99) for inference
- **Feature Versioning**: Track feature schema evolution over time
- **Feature Caching**: Redis-based caching for hot features

## Key Components

### FeatureStoreService
Core service for feature operations:
- `ingest()` - Store computed features
- `getFeatures()` - Retrieve features for inference
- `computeFromEvents()` - Compute features from event streams

### Feature
Value object representing a single feature:
- Entity ID (user, session, device)
- Feature name and value
- Timestamp and version

## Architecture

```
EventCloud → Feature Computation → Feature Store → Redis Cache
                                         ↓
                                   PostgreSQL
```

## Usage

```java
FeatureStoreService featureStore = new FeatureStoreService(dataSource, redis, eventCloud);

// Ingest feature
Feature feature = Feature.builder()
    .name("user_event_count_7d")
    .entityId("user-123")
    .value(42.0)
    .timestamp(Instant.now())
    .build();

featureStore.ingest("tenant-123", feature);

// Serve features
Map<String, Double> features = featureStore.getFeatures(
    "tenant-123",
    "user-123",
    List.of("user_event_count_7d", "avg_session_duration")
);
```

## Performance Targets

- Feature serving: <10ms p99 (with cache)
- Feature ingestion: >10k features/sec
- Cache hit rate: >95%

@doc.type library
@doc.purpose Feature engineering and serving for ML
@doc.layer platform
@doc.pattern Service + Repository
