# Data-Cloud REST API Documentation

**Version:** 2026.3.1  
**Framework:** ActiveJ HTTP Server  
**Base URL:** `/api/v1`

This document describes the Data-Cloud product API surface for data management, analytics, governance, AI/ML-native features, and execution persistence.

Boundary note: Data-Cloud is an independent AI/ML-native product, but it does not host agentic orchestration. AEP performs agentic processing by consuming Data-Cloud public contracts and event-cloud streams, then writing results and execution state back into Data-Cloud.

Canonical contract note: the machine-readable source of truth is `products/data-cloud/docs/openapi.yaml`; this document is a human-oriented companion.

---

## Collections API

### POST /api/v1/collections

Create a new collection.

**Authentication:**
- Required header: `X-Tenant-ID` (tenant identifier)
- Optional header: `X-User-ID` (user identifier for audit)

**Request Body:**
```json
{
  "name": "string (required)",
  "displayName": "string (optional)",
  "description": "string (optional)",
  "schema": {
    "fields": [
      {
        "name": "string (required)",
        "type": "STRING|NUMBER|BOOLEAN|DATE|TIMESTAMP|JSON",
        "required": "boolean (optional, default: false)",
        "indexed": "boolean (optional, default: false)",
        "displayHint": "string (optional)"
      }
    ]
  },
  "storageProfile": {
    "tier": "HOT|WARM|COLD (default: HOT)",
    "ttlDays": "integer (optional)"
  }
}
```

**Response (201 Created):**
```json
{
  "id": "uuid",
  "tenantId": "string",
  "name": "string",
  "displayName": "string",
  "description": "string",
  "createdAt": "timestamp",
  "updatedAt": "timestamp",
  "recordCount": 0,
  "storageProfile": {
    "tier": "string",
    "ttlDays": "integer"
  }
}
```

**Error Responses:**
- **400 Bad Request:** Invalid request format or missing required fields
  ```json
  {"error": "Invalid collection schema: field 'name' is required"}
  ```
- **401 Unauthorized:** Missing `X-Tenant-ID` header
  ```json
  {"error": "X-Tenant-Id header is required"}
  ```
- **403 Forbidden:** User lacks permission to create collections
  ```json
  {"error": "User does not have permission to create collections"}
  ```
- **500 Internal Server Error:** Database or service error

---

### GET /api/v1/collections

List all collections for a tenant (with pagination).

**Authentication:**
- Required header: `X-Tenant-ID`

**Query Parameters:**
- `page`: Page number (0-indexed, default: 0)
- `size`: Page size (default: 20, max: 100)
- `sortBy`: Sort field: `name|createdAt|updatedAt` (default: `createdAt`)
- `sortOrder`: `ASC|DESC` (default: `DESC`)
- `filter`: Optional JSON filter criteria

**Response (200 OK):**
```json
{
  "content": [
    {
      "id": "uuid",
      "name": "string",
      "displayName": "string",
      "createdAt": "timestamp",
      "recordCount": "integer"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8,
  "hasMore": true
}
```

**Error Responses:**
- **400 Bad Request:** Invalid pagination parameters
- **401 Unauthorized:** Missing `X-Tenant-ID` header
- **500 Internal Server Error:** Database error

---

### GET /api/v1/collections/{collectionId}

Get a specific collection by ID.

**Authentication:**
- Required header: `X-Tenant-ID`

**Path Parameters:**
- `collectionId`: UUID of the collection

**Response (200 OK):**
```json
{
  "id": "uuid",
  "tenantId": "string",
  "name": "string",
  "displayName": "string",
  "description": "string",
  "createdAt": "timestamp",
  "updatedAt": "timestamp",
  "recordCount": "integer",
  "schema": {
    "fields": [
      {
        "name": "string",
        "type": "string",
        "required": "boolean",
        "indexed": "boolean"
      }
    ]
  },
  "storageProfile": {
    "tier": "string",
    "ttlDays": "integer"
  }
}
```

**Error Responses:**
- **401 Unauthorized:** Missing `X-Tenant-ID` header
- **403 Forbidden:** Collection belongs to different tenant
- **404 Not Found:** Collection does not exist
- **500 Internal Server Error:** Database error

---

### PUT /api/v1/collections/{collectionId}

Update an existing collection.

**Authentication:**
- Required header: `X-Tenant-ID`

**Path Parameters:**
- `collectionId`: UUID of the collection

**Request Body:**
```json
{
  "displayName": "string (optional)",
  "description": "string (optional)",
  "storageProfile": {
    "tier": "HOT|WARM|COLD (optional)",
    "ttlDays": "integer (optional)"
  }
}
```

**Response (200 OK):**
```json
{
  "id": "uuid",
  "name": "string",
  "displayName": "string",
  "updatedAt": "timestamp",
  "storageProfile": {
    "tier": "string",
    "ttlDays": "integer"
  }
}
```

**Error Responses:**
- **400 Bad Request:** Invalid update request
- **401 Unauthorized:** Missing `X-Tenant-ID` header
- **403 Forbidden:** User lacks permission or collection belongs to different tenant
- **404 Not Found:** Collection does not exist
- **500 Internal Server Error:** Database error

---

### DELETE /api/v1/collections/{collectionId}

Delete a collection.

**Authentication:**
- Required header: `X-Tenant-ID`

**Path Parameters:**
- `collectionId`: UUID of the collection

**Response (204 No Content):** Collection deleted successfully

**Error Responses:**
- **401 Unauthorized:** Missing `X-Tenant-ID` header
- **403 Forbidden:** User lacks permission or collection belongs to different tenant
- **404 Not Found:** Collection does not exist
- **409 Conflict:** Collection has active records or operations
- **500 Internal Server Error:** Database error

---

## Common Error Codes

| Code | Status | Description |
|------|--------|-------------|
| `MISSING_TENANT_ID` | 401 | X-Tenant-ID header is required |
| `UNAUTHORIZED` | 401 | User is not authenticated |
| `FORBIDDEN` | 403 | User lacks required permissions |
| `NOT_FOUND` | 404 | Resource does not exist |
| `INVALID_REQUEST` | 400 | Request validation failed |
| `CONFLICT` | 409 | Resource is in conflicting state |
| `INTERNAL_ERROR` | 500 | Unexpected server error |

---

## Rate Limiting

The API implements tenant-level rate limiting:
- **Rate Limit:** 1000 requests per minute per tenant
- **Response Headers:**
  - `X-RateLimit-Limit`: Maximum requests per minute
  - `X-RateLimit-Remaining`: Remaining quota in current window
  - `X-RateLimit-Reset`: Unix timestamp when quota resets

**Example:**
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
