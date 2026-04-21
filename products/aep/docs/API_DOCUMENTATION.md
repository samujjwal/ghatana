# AEP API Documentation

**Version**: 2026.4.20  
**Last Updated**: April 20, 2026

## Canonical Contract

The canonical machine-readable contract lives in:

- `products/aep/contracts/openapi.yaml`

The server resource copy at `products/aep/server/src/main/resources/openapi.yaml` must stay identical for runtime packaging and UI alignment.

This document is a human guide to the route families AEP actually exposes today. It replaces older docs that described legacy `/api/*` surfaces and a pattern-centric product identity.

## Authentication and Tenancy

- Most AEP endpoints accept `tenantId` as a query parameter and/or `X-Tenant-Id` header.
- Session-backed authenticated flows issue `X-AEP-Session` tokens through `POST /api/v1/session`.
- Some endpoints intentionally return a truthful configured/unconfigured response instead of 501 so operator surfaces can degrade honestly.

## Route Families

### Health and observability

- `GET /health`
- `GET /ready`
- `GET /live`
- `GET /info`
- `GET /metrics`
- `GET /health/deep`
- `GET /metrics/slo`

These routes expose liveness, readiness, dependency depth, and metrics shape. `GET /metrics` may return Prometheus text or a JSON fallback depending on the registry wiring.

### Events and patterns

- `POST /api/v1/events`
- `POST /api/v1/events/batch`
- `GET /api/v1/patterns`
- `POST /api/v1/patterns`
- `GET /api/v1/patterns/{patternId}`
- `DELETE /api/v1/patterns/{patternId}`

These are the remaining event-facing routes. They exist inside the broader execution runtime and are no longer the best description of the whole product.

### Agents and runtime memory

- `GET /api/v1/agents`
- `GET /api/v1/agents/{agentId}`
- `POST /api/v1/agents/{agentId}/execute`
- `GET /api/v1/agents/{agentId}/memory`

The runtime may truthfully report `configured: false` with an empty set when the backing registry or store is absent.

### Pipelines and runs

- `GET /api/v1/pipelines`
- `POST /api/v1/pipelines`
- `GET /api/v1/pipelines/{pipelineId}`
- `GET /api/v1/pipelines/{pipelineId}/versions`
- `POST /api/v1/pipelines/{pipelineId}/publish`
- `POST /api/v1/pipelines/{pipelineId}/rollback`
- `GET /api/v1/runs`
- `GET /api/v1/runs/{runId}`
- `POST /api/v1/runs/{runId}/cancel`

`GET /api/v1/runs/{runId}` is the operator detail surface and can include evidence arrays for:

- `lineage`
- `decisions`
- `policies`

### HITL and learning

- `GET /api/v1/hitl/pending`
- `POST /api/v1/hitl/{reviewId}/approve`
- `POST /api/v1/hitl/{reviewId}/reject`
- `POST /api/v1/hitl/{reviewId}/escalate`
- `GET /api/v1/learning/episodes`
- `GET /api/v1/learning/policies`
- `POST /api/v1/learning/policies/{policyId}/approve`
- `POST /api/v1/learning/policies/{policyId}/reject`
- `POST /api/v1/learning/reflect`

These routes support the operator review loop and post-run learning lifecycle.

`GET /api/v1/hitl/pending` accepts the following queue-inspection controls:

- `tenantId`: tenant scope for queue reads
- `thresholdSeconds`: overdue threshold in seconds; overrides the tenant timeout policy for the current request and otherwise defaults to the server runtime setting or `1800`
- `autoEscalate`: when `true`, overdue items are resolved during the read according to the tenant timeout policy

Tenant timeout policies are configured with `AEP_HITL_TIMEOUT_POLICIES` using `tenant=thresholdSeconds:action[:destinationType[:destination]]` entries separated by `;`. Supported actions are `escalate`, `auto_approve`, and `auto_reject`.

The pending-queue response includes:

- `pending`: the current review items
- `count`: total pending item count returned
- `overdueCount`: how many items were already overdue at read time
- `autoEscalatedCount`: how many overdue items were escalated in the same request
- `autoApprovedCount`: how many overdue items were auto-approved by policy
- `autoRejectedCount`: how many overdue items were auto-rejected by policy
- `policyAction`: the timeout policy action applied for the request or tenant
- `escalationDestinationType` and `escalationDestination`: optional routing metadata for the current tenant policy
- `escalationTimeoutSeconds`: the threshold that was applied

Manual escalation remains available through `POST /api/v1/hitl/{reviewId}/escalate`, which returns the escalated review item plus `escalatedAt`, `reason`, `policyAction`, and optional `destinationType` / `destination` metadata.

### Governance, compliance, and audit

- `GET /governance/kill-switch`
- `POST /governance/kill-switch/activate`
- `POST /governance/kill-switch/deactivate`
- `GET /governance/degradation`
- `POST /governance/degradation`
- `POST /governance/policy/evaluate`
- `GET /governance/compliance/summary`
- `GET /governance/audit/summary`
- `GET /governance/security/egress`
- `POST /governance/security/scan`
- `POST /api/v1/compliance/gdpr/access`
- `POST /api/v1/compliance/gdpr/erasure`
- `POST /api/v1/compliance/gdpr/portability`
- `POST /api/v1/compliance/ccpa/opt-out`
- `GET /api/v1/compliance/soc2/report`

The summary routes are the operator-friendly views used by the AEP governance UI. More detailed compliance operations remain under `/api/v1/compliance/*`.

For GDPR subject operations, AEP now walks all matching Data Cloud result pages instead of stopping at the first batch. `POST /api/v1/compliance/gdpr/erasure` also runs post-erasure cleanup hooks for process-local state, including pattern metadata cache invalidation in server deployments. Embedded/library deployments keep this behavior optional and only run the cleanup hooks they wire explicitly.

### Analytics, reports, deployments, and session lifecycle

- `POST /api/v1/analytics/anomalies`
- `GET /api/v1/analytics/anomalies`
- `POST /api/v1/analytics/forecast`
- `POST /api/v1/analytics/kpis`
- `POST /api/v1/analytics/query`
- `POST /api/v1/reports`
- `POST /api/v1/deployments`
- `DELETE /api/v1/deployments/{deploymentId}`
- `POST /api/v1/session`

## Runtime Config Surface

`AepDynamicConfigService` is the runtime override layer used by server deployments and embedded/library integrations. The authoritative schema for the mutable overlay lives at:

- `products/aep/docs/config/aep-dynamic-config.schema.json`

The schema currently documents the validated overlay keys for:

- `rabbitmq.port`
- `redis.port`
- `db.pool.size`
- `consolidation.interval.hours`
- `KAFKA_BOOTSTRAP_SERVERS`
- `APP_ENV`

Listener failures and invalid writes roll back atomically and are recorded in the service audit history. AEP does not currently expose a public admin HTTP endpoint for remote config mutation, so there is no separate OpenAPI route for this surface yet.

## Documentation Discipline

- When public routes change, update both OpenAPI copies and this document in the same change.
- The server test suite includes route/spec drift coverage for exercised public endpoints; that is the guardrail against silently stale docs.
- If an endpoint is present but only partially backed by runtime dependencies, document that degraded behavior explicitly instead of claiming full production readiness.

## SLO Snapshot

`GET /metrics/slo` is the operator-friendly snapshot endpoint for runtime SLO state when you want a JSON view instead of scraping Prometheus text. The payload includes:

- `runs`: completed, failed, total, success-rate, and failure-rate snapshots
- `replay`: replay attempt, success, and failure counters plus rates
- `agentExecution`: agent execution attempt, success, and failure counters plus rates

---

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
