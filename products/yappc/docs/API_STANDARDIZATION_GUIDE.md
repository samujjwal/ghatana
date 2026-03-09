# YAPPC API Standardization Guide

## Overview
This guide establishes consistent API patterns across all YAPPC backend services.

## Principles

### 1. RESTful Resource Design
- Resources are nouns (workspaces, projects, agents)
- Actions are HTTP methods (GET, POST, PUT, PATCH, DELETE)
- Hierarchical relationships in URL path

### 2. Consistent URL Structure
```
/api/{version}/{resource}/{id}/{sub-resource}
```

Examples:
- `GET /api/v1/workspaces` - List workspaces
- `GET /api/v1/workspaces/{id}` - Get workspace
- `GET /api/v1/workspaces/{id}/projects` - List workspace projects
- `POST /api/v1/projects` - Create project

### 3. Standard HTTP Status Codes

| Code | Usage |
|------|-------|
| 200 | Successful GET, PUT, PATCH |
| 201 | Successful POST (resource created) |
| 204 | Successful DELETE |
| 400 | Bad request (validation error) |
| 401 | Unauthorized (authentication required) |
| 403 | Forbidden (permission denied) |
| 404 | Resource not found |
| 409 | Conflict (duplicate, state mismatch) |
| 422 | Validation error (semantic issues) |
| 429 | Rate limited |
| 500 | Internal server error |
| 503 | Service unavailable |

### 4. Request/Response Format

#### Request Headers (Required)
```
Content-Type: application/json
Accept: application/json
X-Request-ID: {uuid}           # For tracing
Authorization: Bearer {token}   # If authenticated
```

#### Response Headers (Required)
```
Content-Type: application/json
X-Request-ID: {uuid}          # Echo back for tracing
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Reset: 1234567890
```

### 5. Error Response Format
All errors use the StandardApiResponse format:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Workspace 'ws-123' not found",
  "code": "E4101",
  "requestId": "req_1234567890_abc123",
  "timestamp": "2026-03-08T12:00:00Z",
  "details": {
    "resource": "workspace",
    "id": "ws-123"
  },
  "documentation": "https://docs.yappc.dev/errors/E4101"
}
```

### 6. Success Response Format

#### Single Resource
```json
{
  "status": 200,
  "data": {
    "id": "ws-123",
    "name": "My Workspace",
    "createdAt": "2026-01-01T00:00:00Z"
  },
  "meta": {
    "requestId": "req_1234567890_abc123",
    "timestamp": "2026-03-08T12:00:00Z"
  }
}
```

#### Collection with Pagination
```json
{
  "status": 200,
  "data": [
    { "id": "ws-1", "name": "Workspace 1" },
    { "id": "ws-2", "name": "Workspace 2" }
  ],
  "meta": {
    "requestId": "req_1234567890_abc123",
    "timestamp": "2026-03-08T12:00:00Z",
    "pagination": {
      "page": 1,
      "perPage": 20,
      "total": 100,
      "totalPages": 5
    }
  }
}
```

### 7. Query Parameters

#### Pagination
- `page` - Page number (default: 1)
- `perPage` - Items per page (default: 20, max: 100)

#### Sorting
- `sort` - Field to sort by (e.g., `name`, `-createdAt` for descending)

#### Filtering
- `filter[field]` - Filter by field (e.g., `filter[status]=active`)

#### Search
- `q` - Search query string

#### Fields Selection
- `fields` - Comma-separated list of fields to return (e.g., `fields=id,name,status`)

### 8. Versioning

URL path versioning:
- `/api/v1/workspaces` - Version 1
- `/api/v2/workspaces` - Version 2 (when breaking changes needed)

Deprecation headers for old versions:
```
Deprecation: true
Sunset: Sat, 01 Jun 2026 00:00:00 GMT
Link: </api/v2/workspaces>; rel="successor-version"
```

### 9. Authentication

All endpoints (except health/public) require authentication:
```
Authorization: Bearer {jwt_token}
```

Token validation response on 401:
```json
{
  "status": 401,
  "error": "Unauthorized",
  "message": "Authentication required. Provide valid Bearer token.",
  "code": "E4001",
  "requestId": "req_...",
  "timestamp": "..."
}
```

### 10. Rate Limiting

Standard rate limits:
- 100 requests per minute for general endpoints
- 10 requests per minute for expensive operations (AI generation)

Rate limit exceeded response (429):
```json
{
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded. Try again in 45 seconds.",
  "code": "E4290",
  "requestId": "req_...",
  "timestamp": "...",
  "details": {
    "retryAfter": 45,
    "limit": 100,
    "window": "1 minute"
  }
}
```

### 11. Idempotency

For POST/PUT/PATCH operations, support idempotency keys:
```
Idempotency-Key: {uuid}
```

Response includes:
```
Idempotency-Key: {uuid}
```

Duplicate request response:
```json
{
  "status": 200,
  "data": { ... },
  "meta": {
    "requestId": "...",
    "timestamp": "...",
    "idempotent": true
  }
}
```

## Implementation Checklist

- [ ] All endpoints follow URL structure
- [ ] All responses use StandardApiResponse format
- [ ] All errors include requestId and timestamp
- [ ] All authenticated endpoints verify Bearer token
- [ ] All list endpoints support pagination
- [ ] Rate limiting implemented
- [ ] Request ID tracing implemented
- [ ] CORS configured appropriately
- [ ] API documentation generated from code

## Migration Path

### Phase 1: Response Format (Week 1)
- Update all services to use StandardApiResponse
- Add error handling middleware

### Phase 2: URL Structure (Week 2)
- Audit existing endpoints
- Add redirects for breaking changes
- Update client code

### Phase 3: Documentation (Week 3)
- Generate OpenAPI specs from code
- Publish API documentation
- Add examples

### Phase 4: Validation (Week 4)
- Run contract tests
- Performance testing
- Security audit
