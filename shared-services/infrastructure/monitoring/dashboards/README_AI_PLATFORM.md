# AI Platform Monitoring Dashboard

## Overview

Grafana dashboard for monitoring AI Platform components:
- **Model Registry** - Model registration, queries, deployment status
- **Feature Store** - Feature ingestion, write latency, hot/cold storage
- **AI Model Inference** - Request rate, quality scores, data drift
- **HTTP Services** - AI Registry service metrics

## Panels

### Model Registry
1. **Registrations per Minute** - Track model registration rate
2. **Query Latency (p50, p95, p99)** - Model lookup performance
3. **Models by Deployment Status** - Distribution across DEVELOPMENT, STAGED, PRODUCTION, CANARY, DEPRECATED, RETIRED

### Feature Store
4. **Ingestion Rate** - Features ingested per second
5. **Write Latency** - Feature store write performance (p99)

### AI Model Serving
6. **Inference Request Rate** - Model inference requests per second (by model)
7. **Quality Scores** - Model accuracy, F1, precision, recall over time
8. **Data Drift Detection** - Alert when drift magnitude > 0.3

### EventCloud Integration
9. **Partition Lag** - Events behind real-time (feature ingestion backlog)

### HTTP Services
10. **Request Rate** - AI Registry service HTTP request rate (by endpoint, status)
11. **Request Latency p99** - HTTP endpoint performance

## Alerts

### High Model Drift
- **Trigger**: `ai_model_drift_magnitude > 0.3` for 5 minutes
- **Severity**: Warning
- **Action**: Investigate model performance degradation, consider retraining

### Feature Ingestion Lag
- **Trigger**: `eventcloud_lag > 10000` for 2 minutes
- **Severity**: Critical
- **Action**: Check feature store service health, EventCloud connectivity

### Low Model Quality
- **Trigger**: `ai_model_quality_score < 0.80` for 10 minutes
- **Severity**: Warning
- **Action**: Review model predictions, check for data quality issues

## Metrics Reference

### Model Registry Metrics
- `model_registry_register_count` - Total model registrations
- `model_registry_register_duration` - Registration latency
- `model_registry_query_duration` - Query latency
- `model_registry_status_update_count` - Status transitions
- `model_deployment_status_count` - Models by status (gauge)

### Feature Store Metrics
- `feature_ingestion_rate` - Features/sec ingested
- `feature_extraction_duration` - Feature extraction latency
- `feature_store_write_duration` - Write latency
- `backpressure_throttle_count` - Backpressure events

### AI Model Metrics
- `ai_model_inference_count` - Inference requests
- `ai_model_inference_duration` - Inference latency
- `ai_model_quality_score` - Model quality (accuracy, F1, etc.)
- `ai_model_drift_magnitude` - Data/concept drift

### HTTP Service Metrics
- `http_requests_total` - Total HTTP requests (by endpoint, method, status)
- `http_request_duration` - Request latency histogram
- `http_errors` - HTTP error count (by endpoint)

## Installation

```bash
# Import dashboard
curl -X POST http://localhost:3000/api/dashboards/db \
  -H "Content-Type: application/json" \
  -d @monitoring/dashboards/ai-platform.json
```

## Usage

1. Open Grafana: http://localhost:3000
2. Navigate to Dashboards → AI Platform
3. Set time range (default: Last 1 hour)
4. Adjust refresh rate (default: 30 seconds)

## Data Sources

- **Prometheus**: http://localhost:9090 (default)
- **Metrics scrape interval**: 15 seconds
- **Retention**: 15 days (default Prometheus config)

## See Also

- `libs/java/ai-platform/registry/README.md` - Model Registry implementation
- `libs/java/ai-platform/feature-store/README.md` - Feature Store implementation
- `libs/java/ai-platform/observability/README.md` - AI metrics emitter
- `products/shared-services/ai-registry/README.md` - HTTP service
- `products/shared-services/feature-store-ingest/README.md` - Ingestion service
