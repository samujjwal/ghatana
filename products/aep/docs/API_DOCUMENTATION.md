# AEP API Documentation

**Version**: 2026.3.1  
**Last Updated**: March 19, 2026  
**Base URL**: `https://aep.ghatana.com/api`

---

## Table of Contents

1. [Authentication](#authentication)
2. [Core Endpoints](#core-endpoints)
3. [Pipeline Management](#pipeline-management)
4. [Agent Registry](#agent-registry)
5. [Event Processing](#event-processing)
6. [State Management](#state-management)
7. [Monitoring](#monitoring)
8. [Error Codes](#error-codes)

---

## Authentication

All API requests require authentication using JWT tokens.

### Obtain Token

```http
POST /auth/token
Content-Type: application/json

{
  "username": "user@example.com",
  "password": "secure_password"
}
```

**Response**:
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

### Using Token

Include the token in the Authorization header:

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

---

## Core Endpoints

### Health Check

Check if the service is healthy and ready to accept requests.

```http
GET /health
```

**Response**:
```json
{
  "status": "healthy",
  "timestamp": "2026-03-19T14:30:00Z",
  "version": "2026.3.1",
  "uptime_seconds": 86400
}
```

### Readiness Check

Check if the service is ready to process requests.

```http
GET /ready
```

**Response**:
```json
{
  "ready": true,
  "checks": {
    "database": "ok",
    "redis": "ok",
    "kafka": "ok"
  }
}
```

### Metrics

Prometheus-compatible metrics endpoint.

```http
GET /metrics
```

**Response**: Prometheus text format
```
# HELP aep_pipeline_executions_total Total pipeline executions
# TYPE aep_pipeline_executions_total counter
aep_pipeline_executions_total{status="success"} 1234
aep_pipeline_executions_total{status="failure"} 56
```

---

## Pipeline Management

### List Pipelines

Retrieve all pipelines for the authenticated user.

```http
GET /api/pipelines
```

**Query Parameters**:
- `page` (integer): Page number (default: 1)
- `limit` (integer): Items per page (default: 20, max: 100)
- `status` (string): Filter by status (active, paused, archived)
- `sort` (string): Sort field (name, created_at, updated_at)
- `order` (string): Sort order (asc, desc)

**Response**:
```json
{
  "pipelines": [
    {
      "id": "pipe_abc123",
      "name": "Customer Data Processing",
      "description": "Processes customer events",
      "status": "active",
      "created_at": "2026-03-01T10:00:00Z",
      "updated_at": "2026-03-19T14:00:00Z",
      "operators": 5,
      "executions_total": 1234,
      "success_rate": 0.98
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 42,
    "pages": 3
  }
}
```

### Get Pipeline

Retrieve detailed information about a specific pipeline.

```http
GET /api/pipelines/{pipeline_id}
```

**Response**:
```json
{
  "id": "pipe_abc123",
  "name": "Customer Data Processing",
  "description": "Processes customer events",
  "status": "active",
  "created_at": "2026-03-01T10:00:00Z",
  "updated_at": "2026-03-19T14:00:00Z",
  "operators": [
    {
      "id": "op_123",
      "type": "filter",
      "name": "Filter Invalid Events",
      "config": {
        "condition": "event.type != 'test'"
      },
      "position": 1
    },
    {
      "id": "op_124",
      "type": "transform",
      "name": "Enrich Customer Data",
      "config": {
        "lookup_table": "customers"
      },
      "position": 2
    }
  ],
  "metrics": {
    "executions_total": 1234,
    "executions_success": 1210,
    "executions_failure": 24,
    "avg_duration_ms": 145,
    "p95_duration_ms": 320,
    "p99_duration_ms": 580
  }
}
```

### Create Pipeline

Create a new pipeline.

```http
POST /api/pipelines
Content-Type: application/json

{
  "name": "New Pipeline",
  "description": "Pipeline description",
  "operators": [
    {
      "type": "filter",
      "name": "Filter Step",
      "config": {
        "condition": "event.value > 100"
      }
    },
    {
      "type": "transform",
      "name": "Transform Step",
      "config": {
        "mapping": {
          "output_field": "input_field"
        }
      }
    }
  ]
}
```

**Response**: 201 Created
```json
{
  "id": "pipe_xyz789",
  "name": "New Pipeline",
  "status": "active",
  "created_at": "2026-03-19T14:30:00Z"
}
```

### Update Pipeline

Update an existing pipeline.

```http
PUT /api/pipelines/{pipeline_id}
Content-Type: application/json

{
  "name": "Updated Pipeline Name",
  "description": "Updated description",
  "status": "paused"
}
```

**Response**: 200 OK

### Delete Pipeline

Delete a pipeline.

```http
DELETE /api/pipelines/{pipeline_id}
```

**Response**: 204 No Content

### Execute Pipeline

Manually trigger pipeline execution.

```http
POST /api/pipelines/{pipeline_id}/execute
Content-Type: application/json

{
  "event": {
    "type": "customer.created",
    "data": {
      "customer_id": "cust_123",
      "email": "user@example.com"
    }
  }
}
```

**Response**:
```json
{
  "execution_id": "exec_abc123",
  "pipeline_id": "pipe_abc123",
  "status": "running",
  "started_at": "2026-03-19T14:30:00Z"
}
```

---

## Agent Registry

### List Agents

Retrieve all registered agents.

```http
GET /api/agents
```

**Query Parameters**:
- `type` (string): Filter by agent type
- `status` (string): Filter by status (active, inactive)

**Response**:
```json
{
  "agents": [
    {
      "id": "agent_123",
      "name": "Data Validator",
      "type": "validator",
      "version": "1.0.0",
      "status": "active",
      "capabilities": ["validate_schema", "check_constraints"],
      "registered_at": "2026-03-01T10:00:00Z"
    }
  ]
}
```

### Register Agent

Register a new agent.

```http
POST /api/agents
Content-Type: application/json

{
  "name": "Custom Agent",
  "type": "custom",
  "version": "1.0.0",
  "capabilities": ["process_data"],
  "config": {
    "endpoint": "https://agent.example.com"
  }
}
```

**Response**: 201 Created

### Get Agent

Retrieve agent details.

```http
GET /api/agents/{agent_id}
```

### Update Agent

Update agent configuration.

```http
PUT /api/agents/{agent_id}
```

### Deregister Agent

Remove an agent from the registry.

```http
DELETE /api/agents/{agent_id}
```

---

## Event Processing

### Submit Event

Submit an event for processing.

```http
POST /api/events
Content-Type: application/json

{
  "type": "customer.updated",
  "source": "web_app",
  "data": {
    "customer_id": "cust_123",
    "email": "newemail@example.com"
  },
  "metadata": {
    "correlation_id": "req_xyz789"
  }
}
```

**Response**: 202 Accepted
```json
{
  "event_id": "evt_abc123",
  "status": "accepted",
  "timestamp": "2026-03-19T14:30:00Z"
}
```

### Get Event Status

Check the processing status of an event.

```http
GET /api/events/{event_id}
```

**Response**:
```json
{
  "event_id": "evt_abc123",
  "type": "customer.updated",
  "status": "completed",
  "submitted_at": "2026-03-19T14:30:00Z",
  "completed_at": "2026-03-19T14:30:05Z",
  "pipeline_executions": [
    {
      "pipeline_id": "pipe_abc123",
      "status": "success",
      "duration_ms": 145
    }
  ]
}
```

### List Events

Retrieve event history.

```http
GET /api/events
```

**Query Parameters**:
- `type` (string): Filter by event type
- `status` (string): Filter by status
- `from` (timestamp): Start time
- `to` (timestamp): End time
- `page` (integer): Page number
- `limit` (integer): Items per page

---

## State Management

### Get Unified State

Retrieve the current unified state.

```http
GET /api/state/unified
```

**Response**:
```json
{
  "timestamp": "2026-03-19T14:30:00Z",
  "pipelines": {
    "active": 42,
    "paused": 5,
    "total": 47
  },
  "events": {
    "processing": 123,
    "completed_today": 5678,
    "failed_today": 12
  },
  "agents": {
    "active": 15,
    "inactive": 2
  },
  "system": {
    "cpu_usage": 0.45,
    "memory_usage": 0.62,
    "disk_usage": 0.38
  }
}
```

### WebSocket: Real-time State Updates

Connect to WebSocket for real-time state updates.

```javascript
const ws = new WebSocket('wss://aep.ghatana.com/tail/events');

ws.onmessage = (event) => {
  const update = JSON.parse(event.data);
  console.log('State update:', update);
};
```

**Message Format**:
```json
{
  "type": "state_update",
  "timestamp": "2026-03-19T14:30:00Z",
  "data": {
    "pipeline_id": "pipe_abc123",
    "status": "completed",
    "duration_ms": 145
  }
}
```

---

## Monitoring

### Get System Metrics

Retrieve system-wide metrics.

```http
GET /api/monitoring/metrics
```

**Response**:
```json
{
  "timestamp": "2026-03-19T14:30:00Z",
  "pipelines": {
    "executions_per_second": 12.5,
    "avg_duration_ms": 145,
    "success_rate": 0.98
  },
  "events": {
    "ingestion_rate": 50.2,
    "processing_rate": 48.7,
    "backlog": 123
  },
  "system": {
    "cpu_cores": 8,
    "cpu_usage_percent": 45,
    "memory_total_gb": 32,
    "memory_used_gb": 20,
    "disk_total_gb": 500,
    "disk_used_gb": 190
  }
}
```

### Get Pipeline Metrics

Retrieve metrics for a specific pipeline.

```http
GET /api/monitoring/pipelines/{pipeline_id}/metrics
```

**Query Parameters**:
- `from` (timestamp): Start time
- `to` (timestamp): End time
- `granularity` (string): Time bucket (1m, 5m, 1h, 1d)

---

## Error Codes

### HTTP Status Codes

- `200 OK`: Request successful
- `201 Created`: Resource created successfully
- `202 Accepted`: Request accepted for processing
- `204 No Content`: Request successful, no content to return
- `400 Bad Request`: Invalid request parameters
- `401 Unauthorized`: Authentication required
- `403 Forbidden`: Insufficient permissions
- `404 Not Found`: Resource not found
- `409 Conflict`: Resource conflict
- `422 Unprocessable Entity`: Validation error
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Server error
- `503 Service Unavailable`: Service temporarily unavailable

### Error Response Format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid pipeline configuration",
    "details": {
      "field": "operators[0].config",
      "reason": "Missing required field 'condition'"
    },
    "request_id": "req_xyz789"
  }
}
```

### Error Codes

- `AUTHENTICATION_FAILED`: Invalid credentials
- `AUTHORIZATION_FAILED`: Insufficient permissions
- `VALIDATION_ERROR`: Request validation failed
- `RESOURCE_NOT_FOUND`: Requested resource not found
- `RESOURCE_CONFLICT`: Resource already exists
- `RATE_LIMIT_EXCEEDED`: Too many requests
- `PIPELINE_EXECUTION_FAILED`: Pipeline execution error
- `AGENT_UNAVAILABLE`: Agent not responding
- `INTERNAL_ERROR`: Internal server error

---

## Rate Limiting

API requests are rate-limited to prevent abuse:

- **Standard tier**: 1000 requests/hour
- **Premium tier**: 10000 requests/hour
- **Enterprise tier**: Unlimited

Rate limit headers:
```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 987
X-RateLimit-Reset: 1710864000
```

---

## Pagination

List endpoints support pagination:

**Request**:
```http
GET /api/pipelines?page=2&limit=20
```

**Response Headers**:
```http
Link: <https://aep.ghatana.com/api/pipelines?page=1&limit=20>; rel="first",
      <https://aep.ghatana.com/api/pipelines?page=1&limit=20>; rel="prev",
      <https://aep.ghatana.com/api/pipelines?page=3&limit=20>; rel="next",
      <https://aep.ghatana.com/api/pipelines?page=3&limit=20>; rel="last"
```

---

## Webhooks

Configure webhooks to receive event notifications.

### Register Webhook

```http
POST /api/webhooks
Content-Type: application/json

{
  "url": "https://your-app.com/webhook",
  "events": ["pipeline.completed", "pipeline.failed"],
  "secret": "your_webhook_secret"
}
```

### Webhook Payload

```json
{
  "event": "pipeline.completed",
  "timestamp": "2026-03-19T14:30:00Z",
  "data": {
    "pipeline_id": "pipe_abc123",
    "execution_id": "exec_xyz789",
    "status": "success",
    "duration_ms": 145
  },
  "signature": "sha256=..."
}
```

---

## SDK Examples

### Python

```python
import requests

# Authenticate
response = requests.post('https://aep.ghatana.com/api/auth/token', json={
    'username': 'user@example.com',
    'password': 'password'
})
token = response.json()['access_token']

# List pipelines
headers = {'Authorization': f'Bearer {token}'}
pipelines = requests.get('https://aep.ghatana.com/api/pipelines', headers=headers)
print(pipelines.json())
```

### JavaScript

```javascript
// Authenticate
const authResponse = await fetch('https://aep.ghatana.com/api/auth/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'user@example.com',
    password: 'password'
  })
});
const { access_token } = await authResponse.json();

// List pipelines
const pipelines = await fetch('https://aep.ghatana.com/api/pipelines', {
  headers: { 'Authorization': `Bearer ${access_token}` }
});
console.log(await pipelines.json());
```

### cURL

```bash
# Authenticate
TOKEN=$(curl -X POST https://aep.ghatana.com/api/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"user@example.com","password":"password"}' \
  | jq -r '.access_token')

# List pipelines
curl https://aep.ghatana.com/api/pipelines \
  -H "Authorization: Bearer $TOKEN"
```

---

## OpenAPI Specification

Full OpenAPI 3.0 specification available at:
```
https://aep.ghatana.com/api/openapi.json
```

Import into tools like Postman, Insomnia, or Swagger UI for interactive documentation.

---

**Version**: 2026.3.1  
**Last Updated**: March 19, 2026  
**Support**: api-support@ghatana.com
