# Data Cloud REST API Documentation

This document is the human-readable companion to the canonical contract in `products/data-cloud/api/openapi.yaml`. When there is any mismatch, the OpenAPI file wins.

## Request Model

- Base API prefix: `/api/v1`
- Health and probe endpoints live outside `/api/v1`
- Auth: local profile can run without auth; non-local deployments should use API key or JWT auth
- Tenant resolution: `X-Tenant-ID` is the default tenant boundary header unless JWT tenant claims are configured
- Content type: write routes expect `application/json`
- Rate limiting: enforced in the launcher via configurable sliding-window limits

## UI Surface Truth Notes

- Canonical route-truth source: `products/data-cloud/ROUTE_TRUTH_MATRIX_2026-04-17.md`.
- The current primary UI flows map to `/`, `/data`, `/pipelines`, and `/query`.
- `/trust`, `/insights`, `/events`, and `/settings` are disclosed by shell role rather than promoted to every user by default.
- `/alerts` is a live operator-facing triage surface backed by launcher alert list, acknowledge, resolve, grouping, suggestion, rule, and stream routes, but it remains hidden from primary-user discovery.
- `/fabric` remains a preview-only operator surface.
- `/settings` remains an explicit admin-only boundary page rather than a general end-user settings workflow.
- Collection CRUD in the current UI is backed by entity endpoints such as `/api/v1/entities/dc_collections`; older `/api/v1/collections` CRUD examples should be treated as deprecated compatibility routes, not the primary runtime contract.

## Core Examples

Create an entity:

```json
POST /api/v1/entities/tickets
{
  "title": "login failure investigation",
  "summary": "password reset token expired"
}
```

Similarity search:

```json
GET /api/v1/entities/tickets/similar?id=11111111-1111-1111-1111-111111111111&k=5
```

Publish a data product:

```json
POST /api/v1/data-products
{
  "name": "Support Tickets",
  "collection": "tickets",
  "description": "Support search and analytics",
  "sla": {
    "freshnessSeconds": 600,
    "completenessTarget": 0.95
  }
}
```

Grounded retrieval:

```json
POST /api/v1/context/tickets/rag
{
  "question": "How should I troubleshoot an expired password reset token?",
  "k": 3
}
```

## Endpoint Inventory

### Probes And Runtime Info

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Liveness summary |
| GET | `/health/detail` | Detailed subsystem health |
| GET | `/ready` | Readiness probe |
| GET | `/live` | Liveness probe |
| GET | `/info` | Runtime info |
| GET | `/metrics` | Metrics export |

### Entities And Search

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/entities/:collection` | Save or upsert an entity |
| GET | `/api/v1/entities/:collection` | Query entities |
| GET | `/api/v1/entities/:collection/:id` | Fetch one entity |
| DELETE | `/api/v1/entities/:collection/:id` | Delete one entity |
| POST | `/api/v1/entities/:collection/batch` | Batch upsert |
| DELETE | `/api/v1/entities/:collection/batch` | Batch delete |
| GET | `/api/v1/entities/:collection/:id/history` | Point-in-time entity snapshot |
| GET | `/api/v1/entities/:collection/search` | Full-text search |
| GET | `/api/v1/entities/:collection/similar` | Semantic similarity search |
| GET | `/api/v1/entities/:collection/stream` | CDC stream for a collection |
| GET | `/api/v1/entities/:collection/query/stream` | Streaming query SSE |
| GET | `/api/v1/entities/:collection/export` | Bulk export |
| POST | `/api/v1/entities/:collection/anomalies` | On-demand anomaly detection |
| GET | `/api/v1/anomalies` | Query persisted anomaly results |
| POST | `/api/v1/entities/:collection/validate` | Validate one payload |
| POST | `/api/v1/entities/:collection/validate/batch` | Batch validation |
| POST | `/api/v1/entities/:collection/suggest` | AI-assisted entity suggestions |

### Events

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/events` | Append an event |
| GET | `/api/v1/events` | Query events |
| GET | `/api/v1/events/:offset` | Fetch one event by offset |

### Pipelines And Checkpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/pipelines` | List pipelines |
| POST | `/api/v1/pipelines` | Save pipeline |
| GET | `/api/v1/pipelines/:pipelineId` | Get pipeline |
| PUT | `/api/v1/pipelines/:pipelineId` | Update pipeline |
| DELETE | `/api/v1/pipelines/:pipelineId` | Delete pipeline |
| GET | `/api/v1/checkpoints` | List checkpoints |
| POST | `/api/v1/checkpoints` | Save checkpoint |
| GET | `/api/v1/checkpoints/:checkpointId` | Get checkpoint |
| DELETE | `/api/v1/checkpoints/:checkpointId` | Delete checkpoint |
| POST | `/api/v1/pipelines/:pipelineId/optimise-hint` | AI optimisation hint |

### Memory, Brain, Learning

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/memory` | List stored memory across agents |
| POST | `/api/v1/memory/:agentId` | Store memory item |
| GET | `/api/v1/memory/:agentId` | Read agent memory |
| GET | `/api/v1/memory/:agentId/:tier` | Read tier-specific memory |
| POST | `/api/v1/memory/:agentId/search` | Search memory |
| DELETE | `/api/v1/memory/:agentId/:memoryId` | Delete memory item |
| PUT | `/api/v1/memory/:agentId/:memoryId/retain` | Retain memory item |
| GET | `/api/v1/brain/health` | Brain health |
| GET | `/api/v1/brain/config` | Brain config |
| GET | `/api/v1/brain/stats` | Brain stats |
| GET | `/api/v1/brain/workspace` | Brain workspace snapshot |
| GET | `/api/v1/brain/workspace/stream` | Brain workspace SSE |
| POST | `/api/v1/brain/attention/elevate` | Attention override |
| GET | `/api/v1/brain/attention/thresholds` | Read attention thresholds |
| PUT | `/api/v1/brain/attention/thresholds` | Update attention thresholds |
| GET | `/api/v1/brain/patterns` | List patterns |
| POST | `/api/v1/brain/patterns/match` | Pattern matching |
| GET | `/api/v1/brain/salience/:itemId` | Item salience lookup |
| POST | `/api/v1/brain/explain` | AI explanation |
| POST | `/api/v1/learning/trigger` | Trigger learning job |
| GET | `/api/v1/learning/status` | Learning status |
| GET | `/api/v1/learning/review` | Review queue |
| POST | `/api/v1/learning/review/:reviewId/approve` | Approve review item |
| POST | `/api/v1/learning/review/:reviewId/reject` | Reject review item |
| DELETE | `/api/v1/learning/review/completed` | Purge completed reviews |
| GET | `/api/v1/learning/stream` | Learning SSE |

### Analytics, Reports, Models, Features

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/analytics/query` | Run analytics query |
| GET | `/api/v1/analytics/query/:queryId` | Get query result |
| GET | `/api/v1/analytics/query/:queryId/plan` | Get query plan |
| POST | `/api/v1/analytics/aggregate` | Aggregate query |
| POST | `/api/v1/analytics/explain` | Explain query without running it |
| POST | `/api/v1/analytics/suggest` | AI analytics suggestions |
| POST | `/api/v1/reports` | Create report |
| GET | `/api/v1/reports` | List reports |
| GET | `/api/v1/reports/:reportId` | Get report |
| GET | `/api/v1/models` | List AI models |
| POST | `/api/v1/models` | Register AI model |
| GET | `/api/v1/models/:modelName` | Get model |
| POST | `/api/v1/models/:modelName/promote` | Promote model |
| POST | `/api/v1/features` | Ingest feature data |
| GET | `/api/v1/features/:entityId` | Read feature data |

### Governance, Lineage, Context, Data Products

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/governance/retention/classify` | Classify retention policy |
| GET | `/api/v1/governance/retention/policy` | Get retention policy |
| POST | `/api/v1/governance/retention/purge` | Purge data |
| POST | `/api/v1/governance/privacy/redact` | Redact sensitive data |
| GET | `/api/v1/governance/privacy/pii-fields` | List PII fields |
| GET | `/api/v1/governance/compliance/summary` | Compliance summary |
| GET | `/api/v1/lineage/:collection` | Collection lineage graph |
| GET | `/api/v1/lineage/:collection/impact` | Impact analysis |
| GET | `/api/v1/context` | Read runtime context |
| PUT | `/api/v1/context` | Upsert runtime context |
| DELETE | `/api/v1/context/keys/:key` | Delete one context key |
| GET | `/api/v1/context/snapshot` | Read versioned context snapshot |
| POST | `/api/v1/context/:collection/rag` | Grounded retrieval response |
| GET | `/api/v1/data-products` | List published data products |
| POST | `/api/v1/data-products` | Publish a data product descriptor |
| POST | `/api/v1/data-products/:productId/subscribe` | Subscribe to a data product |

### Capabilities, Plugins, Autonomy, Agent Catalog

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/capabilities` | Runtime capability registry |
| PUT | `/api/v1/autonomy/level` | Set global autonomy level |
| GET | `/api/v1/autonomy/level` | Get global autonomy level |
| GET | `/api/v1/autonomy/domains` | List autonomy domains |
| GET | `/api/v1/autonomy/domains/:domain` | Get domain autonomy settings |
| GET | `/api/v1/autonomy/logs` | Get autonomy audit logs |
| GET | `/api/v1/agents/catalog` | List agent catalog entries |
| GET | `/api/v1/agents/catalog/:id` | Get one agent catalog entry |
| GET | `/api/v1/plugins` | List installed plugins |
| GET | `/api/v1/plugins/:id` | Get plugin details |
| POST | `/api/v1/plugins/:id/enable` | Enable plugin |
| POST | `/api/v1/plugins/:id/disable` | Disable plugin |
| POST | `/api/v1/plugins/:id/upgrade` | Record bundled-plugin upgrade intent |

### Operations And Streaming

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/v1/pipelines/:pipelineId/execute` | Execute a pipeline through the runtime workflow plugin |
| GET | `/api/v1/pipelines/:pipelineId/executions` | List pipeline executions |
| GET | `/api/v1/pipelines/:pipelineId/executions/:executionId` | Get workflow-scoped execution summary |
| POST | `/api/v1/pipelines/:pipelineId/executions/:executionId/cancel` | Cancel a pipeline execution |
| GET | `/api/v1/executions/:executionId` | Get execution detail |
| GET | `/api/v1/executions/:executionId/logs` | Get execution logs |
| POST | `/api/v1/executions/:executionId/cancel` | Cancel an execution by id |
| GET | `/events/stream` | Global SSE stream |
| GET | `/api/v1/voice/intents` | List supported voice intents |
| POST | `/api/v1/voice/intent` | Resolve voice intent |
| POST | `/api/v1/voice/intent/classify` | Classify voice input only |
| GET | `/api/v1/queries/estimate` | Estimate storage cost for a query |
| GET | `/api/v1/collections/:id/cost-report` | Collection cost report for operator review |
| POST | `/api/v1/queries/federated` | Federated query execution |
| POST | `/api/v1/collections/:id/migrate` | Manual tier migration |
| WS | `/ws` | WebSocket change notifications |

## Common Response Patterns

- Validation failures: `400` or `422` with an `error` field
- Missing resources: `404`
- Unsupported optional subsystem: `501` or `503` depending on handler behavior
- Successful mutations usually return JSON with IDs, timestamps, and request correlation fields

## Notes On Accuracy

- This file reflects the routes currently registered in the launcher.
- Schema-level request and response details should be taken from `products/data-cloud/api/openapi.yaml` and generated SDKs.
- If you add a route to `DataCloudHttpServer`, update both the OpenAPI contract and this document in the same change.
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 950
X-RateLimit-Reset: 1708976400
```

---

## Multi-Tenancy

All endpoints enforce strict multi-tenant isolation:

1. **Tenant ID Extraction:** Extracted from `X-Tenant-ID` header
2. **Query Filtering:** All database queries automatically filter by tenant ID
3. **Permission Checks:** RBAC validation ensures user can access tenant data
4. **Audit Logging:** All operations logged with tenant and user context

**Tenant Isolation Guarantees:**
- Users cannot access other tenants' collections
- Database queries are automatically scoped by tenant
- Cross-tenant data access results in 403 Forbidden responses

---

## Performance Considerations

1. **Async Operations:** All endpoints use Promise-based async I/O
2. **Pagination:** Large result sets must use pagination (max page size: 100)
3. **Indexing:** Frequently filtered fields should be indexed in collection schema
4. **Caching:** Collections are cached (5-minute TTL) - updates may have slight delay

---

## Webhook API

See [WebhookController](./WebhookController.java) for webhook management endpoints:
- `POST /api/v1/webhooks` - Create webhook
- `GET /api/v1/webhooks` - List webhooks
- `GET /api/v1/webhooks/{webhookId}` - Get webhook details
- `PUT /api/v1/webhooks/{webhookId}` - Update webhook
- `DELETE /api/v1/webhooks/{webhookId}` - Delete webhook

---

## Generated OpenAPI Specification

For programmatic API integration, generate OpenAPI spec with:
```bash
# Using API documentation endpoint (if available)
curl https://your-domain/api/v1/openapi.json

# Or manually generate from source code
./gradlew generateOpenApiDocs
```
