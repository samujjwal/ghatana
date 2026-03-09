# AI Registry HTTP Service

## Purpose

REST API service exposing the AI Model Registry over HTTP for:
- Model registration and version control
- Model deployment status tracking
- Model metadata queries
- Integration with ML pipelines

## Architecture

```
HTTP Clients (ML Pipelines, Dashboards)
    ↓
AiRegistryServiceLauncher (HTTP REST API)
    ↓
ModelRegistryService (libs:ai-platform:registry)
    ↓
PostgreSQL (model_metadata table)
```

## Endpoints

### Health Check
```
GET /health
Response: {"status": "healthy"}
```

### Register Model
```
POST /api/v1/models
Content-Type: application/json

{
  "tenantId": "tenant-1",
  "name": "fraud-detector",
  "version": "1.0.0",
  "modelType": "classification",
  "framework": "sklearn",
  "metadata": {
    "accuracy": 0.95,
    "f1_score": 0.92
  }
}

Response: 201 Created
{
  "id": "model-uuid",
  "status": "DEVELOPMENT",
  ...
}
```

### Get Model by ID
```
GET /api/v1/models/{id}
Response: 200 OK
{
  "id": "model-uuid",
  "name": "fraud-detector",
  "version": "1.0.0",
  "status": "PRODUCTION",
  ...
}
```

### List Models
```
GET /api/v1/models?status=PRODUCTION
Response: 200 OK
[
  {"id": "...", "name": "...", "status": "PRODUCTION"},
  ...
]
```

### List Model Versions
```
GET /api/v1/models/{name}/versions
Response: 200 OK
[
  {"version": "1.0.0", "status": "DEPRECATED"},
  {"version": "2.0.0", "status": "PRODUCTION"}
]
```

### Update Model Status
```
PUT /api/v1/models/{id}/status
Content-Type: application/json

{
  "status": "PRODUCTION"
}

Response: 200 OK
```

## Configuration

Set environment variables:
- `DATABASE_URL` - PostgreSQL connection string
- `DATABASE_USER` - DB username
- `DATABASE_PASSWORD` - DB password
- `PORT` - HTTP port (default: 8080)

## Running

```bash
# Development
./gradlew :products:shared-services:ai-registry:run

# Production (after build)
java -jar products/shared-services/ai-registry/build/libs/ai-registry.jar
```

## Dependencies

- `libs:ai-platform:registry` - Model registry service
- `libs:http-server` - HTTP abstractions
- `libs:observability` - Metrics collection
- ActiveJ HTTP for async request handling

## Metrics

Emitted metrics:
- `http.requests.total` - Total HTTP requests (by endpoint, status)
- `http.request.duration` - Request latency (p50, p95, p99)
- `http.errors` - HTTP errors (by endpoint)
- `model.registry.*` - Passthrough from ModelRegistryService

## Thread Safety

Thread-safe - uses ActiveJ Eventloop for single-threaded async execution.

## Performance

Target: 1000 req/sec with <50ms p99 latency for model lookups.

See `libs:ai-platform:registry` for database schema and service implementation details.
