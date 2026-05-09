# Data Cloud REST API Documentation

**DC-CON-001: OpenAPI is the single source of truth for API contracts.** This document provides human-readable explanations and usage patterns. For the complete, authoritative API specification including all endpoints, schemas, and examples, see the canonical OpenAPI contract at `products/data-cloud/contracts/openapi/data-cloud.yaml`.

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

**DC-CON-001: The complete endpoint inventory is maintained in the canonical OpenAPI contract at `products/data-cloud/contracts/openapi/data-cloud.yaml`.** Do not manually duplicate endpoint lists here—this creates drift risk. Use the OpenAPI spec or generated documentation for the authoritative endpoint reference.

Key endpoint groups (see OpenAPI spec for complete list):
- Probes and runtime info (`/health`, `/ready`, `/live`, `/metrics`)
- Entities and search (`/api/v1/entities/*`)
- Events (`/api/v1/events/*`)
- Pipelines and checkpoints (`/api/v1/pipelines/*`, `/api/v1/checkpoints/*`)
- Memory, Brain, Learning (`/api/v1/memory/*`, `/api/v1/brain/*`, `/api/v1/learning/*`)
- Analytics, Reports, Models, Features (`/api/v1/analytics/*`, `/api/v1/reports/*`, `/api/v1/models/*`, `/api/v1/features/*`)
- Governance, Lineage, Context, Data Products (`/api/v1/governance/*`, `/api/v1/lineage/*`, `/api/v1/context/*`, `/api/v1/data-products/*`)
- Capabilities, Plugins, Autonomy, Agent Catalog (`/api/v1/surfaces`, `/api/v1/autonomy/*`, `/api/v1/plugins/*`, `/api/v1/agents/*`)
- Operations and Streaming (`/api/v1/pipelines/*/execute`, `/api/v1/executions/*`, `/ws`)

## Common Response Patterns

- Validation failures: `400` or `422` with an `error` field
- Missing resources: `404`
- Unsupported optional subsystem: `501` or `503` depending on handler behavior
- Successful mutations usually return JSON with IDs, timestamps, and request correlation fields

## Runtime Truth Surface

The canonical Runtime Truth endpoint is `/api/v1/surfaces`. DC-P1.12: The compatibility endpoint `/api/v1/capabilities` has been removed; all callers must use `/api/v1/surfaces`.

The `/api/v1/surfaces` response includes the full plane/surface map as registered by the Runtime Truth Registry at startup. Callers should treat the surface list as read-only and authoritative for surface discovery.

## Notes On Accuracy

- **DC-CON-001: OpenAPI is the single source of truth.** This document provides explanatory context and usage patterns only.
- Schema-level request and response details should be taken from `products/data-cloud/contracts/openapi/data-cloud.yaml` and generated SDKs.
- Do not manually duplicate endpoint lists or route documentation in this file—refer to the OpenAPI spec instead.
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
