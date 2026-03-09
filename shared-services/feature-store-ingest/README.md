# Feature Store Ingest Service

## Purpose

Real-time feature extraction and ingestion pipeline from EventCloud to Feature Store for ML model serving.

## Architecture

```
EventCloud (event stream)
    ↓
EventCloudTailOperator (subscription)
    ↓
FeatureExtractor (feature engineering logic)
    ↓
FeatureStoreService (Redis + PostgreSQL)
    ↓
ML Models (real-time inference)
```

## Features

- **Real-time Processing**: <10ms p99 latency from event to feature
- **EventCloud Tailing**: Partition-aware subscription with auto-recovery
- **Feature Engineering**: Extractable features from events
- **Backpressure Handling**: Auto-throttle when downstream slow
- **Checkpoint Recovery**: Resume from last committed offset

## Feature Extraction Examples

### Fraud Detection
From transaction events, extract:
- `transaction_amount` - Transaction value
- `user_history_score` - Historical user reputation
- `device_fingerprint` - Device ID hash
- `geolocation` - User location
- `time_of_day` - Hour of day (0-23)

### Recommendation Engine
From user activity events, extract:
- `user_item_affinity` - User-item interaction score
- `item_popularity` - Item view/purchase count
- `session_duration` - Current session length
- `category_preference` - Category affinity scores

### Predictive Maintenance
From sensor events, extract:
- `sensor_reading` - Raw sensor value
- `rolling_avg_24h` - 24-hour moving average
- `anomaly_score` - Statistical deviation
- `failure_probability` - Predicted failure likelihood

## Configuration

Set environment variables:
- `EVENTCLOUD_URL` - EventCloud connection string
- `EVENTCLOUD_TENANT` - Tenant ID for subscription
- `REDIS_URL` - Redis connection for hot features
- `DATABASE_URL` - PostgreSQL for cold features
- `CHECKPOINT_INTERVAL` - Checkpoint frequency (default: 1000 events)

## Running

```bash
# Development
./gradlew :products:shared-services:feature-store-ingest:run

# Production
java -jar products/shared-services/feature-store-ingest/build/libs/feature-store-ingest.jar
```

## Metrics

Emitted metrics:
- `feature.ingestion.rate` - Features ingested per second
- `feature.extraction.duration` - Feature extraction latency (p50, p95, p99)
- `eventcloud.lag` - Partition lag (events behind real-time)
- `feature.store.write.duration` - Feature store write latency
- `backpressure.throttle.count` - Number of backpressure throttles

## Performance Targets

- **Throughput**: 10k events/sec sustained, 50k burst
- **Latency**: <10ms p99 end-to-end (event → feature store)
- **Recovery**: <5s to resume after restart

## Dependencies

- `libs:ai-platform:feature-store` - Feature storage
- `libs:event-cloud` - EventCloud client
- `libs:event-runtime` - Event processing
- `libs:observability` - Metrics collection
- ActiveJ Datastream for stream processing

## Thread Safety

Thread-safe - uses single ActiveJ Eventloop thread for all processing.

## Backpressure Strategy

When feature store writes slow down:
1. Reduce EventCloud fetch rate
2. Buffer up to 10k events in memory
3. Emit `backpressure.throttle.count` metric
4. Resume normal rate when writes recover

## Checkpoint Strategy

Commits offset to EventCloud every:
- 1000 events processed, OR
- 10 seconds elapsed

On restart, resumes from last committed offset.

See `libs:ai-platform:feature-store` for feature storage implementation details.
