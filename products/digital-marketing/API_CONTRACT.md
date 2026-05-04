# DMOS API Contract

This document defines the canonical API contract for the Digital Marketing Operating System (DMOS).

## Overview

- **Base URL**: `https://api.ghatana.com/dmos/v1`
- **Content-Type**: `application/json`
- **Character Encoding**: UTF-8

## Standard Headers

### Mandatory Headers (All Requests)

| Header | Description | Required |
|--------|-------------|----------|
| `X-Tenant-ID` | Tenant identifier for multi-tenancy | Yes (production) |
| `X-Principal-ID` | Authenticated user principal identifier | Yes (production) |
| `X-Session-ID` | Session identifier for session management | Yes (production) |
| `X-Correlation-ID` | Request correlation ID for tracing | Recommended (auto-generated if missing) |
| `X-Idempotency-Key` | Idempotency key for write operations | Yes (non-idempotent writes) |

### Optional Headers

| Header | Description |
|--------|-------------|
| `Accept` | Response content type (default: application/json) |
| `User-Agent` | Client identification |

## Standard Error Envelope

All error responses follow this structure:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
      "field": "Additional context about the error"
    },
    "requestId": "correlation-id-from-request"
  }
}
```

### Common Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `UNAUTHORIZED` | 401 | Missing or invalid authentication headers |
| `FORBIDDEN` | 403 | Insufficient permissions for the requested resource |
| `NOT_FOUND` | 404 | Requested resource does not exist |
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `CONFLICT` | 409 | Resource conflict (e.g., duplicate idempotency key) |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |

## Idempotency Semantics

### Write Operations

All non-idempotent write operations (POST, PUT, DELETE) MUST include the `X-Idempotency-Key` header.

- **Key Format**: UUID v4 string
- **Uniqueness Scope**: Per tenant, per operation type
- **Behavior**: Subsequent requests with the same key return the cached response
- **TTL**: Idempotency records are retained for 24 hours

### Example

```http
POST /v1/workspaces/{workspaceId}/campaigns
X-Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
X-Tenant-ID: tenant-123
X-Principal-ID: user-456
X-Session-ID: session-789
Content-Type: application/json

{
  "name": "Summer Campaign 2026",
  "type": "SEARCH",
  "budget": 50000
}
```

## API Endpoints

### Workspaces

#### Create Workspace
```
POST /v1/workspaces
```

#### Get Workspace
```
GET /v1/workspaces/{workspaceId}
```

### Campaigns

#### Create Campaign
```
POST /v1/workspaces/{workspaceId}/campaigns
X-Idempotency-Key: <uuid>
```

Request Body:
```json
{
  "name": "string",
  "type": "SEARCH|DISPLAY|SOCIAL",
  "budget": "number",
  "startDate": "ISO-8601-date",
  "endDate": "ISO-8601-date"
}
```

#### Get Campaign
```
GET /v1/workspaces/{workspaceId}/campaigns/{campaignId}
```

#### Launch Campaign
```
POST /v1/workspaces/{workspaceId}/campaigns/{campaignId}/launch
X-Idempotency-Key: <uuid>
```

#### Pause Campaign
```
POST /v1/workspaces/{workspaceId}/campaigns/{campaignId}/pause
X-Idempotency-Key: <uuid>
```

### Approvals

#### Get Pending Approvals (Reviewer Queue)
```
GET /v1/workspaces/{workspaceId}/approvals/pending?reviewerId={principalId}
```

Query Parameters:
- `reviewerId` (required): Principal ID of the reviewer
- `limit` (optional): Maximum results (default: 50)
- `offset` (optional): Pagination offset (default: 0)

#### Get Approval Details
```
GET /v1/workspaces/{workspaceId}/approvals/{requestId}
```

#### Approve Request
```
POST /v1/workspaces/{workspaceId}/approvals/{requestId}/approve
X-Idempotency-Key: <uuid>
```

Request Body:
```json
{
  "comment": "string (optional)"
}
```

#### Reject Request
```
POST /v1/workspaces/{workspaceId}/approvals/{requestId}/reject
X-Idempotency-Key: <uuid>
```

Request Body:
```json
{
  "reason": "string (required)"
}
```

### Strategy

#### Generate Strategy
```
POST /v1/workspaces/{workspaceId}/strategy
X-Idempotency-Key: <uuid>
```

Request Body:
```json
{
  "campaignId": "string",
  "objectives": ["string"],
  "targetAudience": "string"
}
```

### Budget

#### Get Budget Recommendation
```
POST /v1/workspaces/{workspaceId}/budget/recommend
X-Idempotency-Key: <uuid>
```

Request Body:
```json
{
  "campaignType": "string",
  "targetMarket": "string",
  "durationDays": "number"
}
```

### AI Actions

#### List AI Actions
```
GET /v1/workspaces/{workspaceId}/ai-actions
```

Query Parameters:
- `limit` (optional): Maximum results (default: 50)
- `offset` (optional): Pagination offset (default: 0)

#### Get AI Action Details
```
GET /v1/workspaces/{workspaceId}/ai-actions/{actionId}
```

## Request Field Naming Conventions

- **camelCase** for JSON field names (e.g., `workspaceId`, `campaignName`)
- **kebab-case** for URL path segments (e.g., `/workspaces/{workspaceId}`)
- **PascalCase** for enum values (e.g., `SEARCH`, `DISPLAY`)

## Response Envelope

Success responses return the requested resource directly (no envelope wrapper).

Example:
```json
{
  "id": "campaign-123",
  "name": "Summer Campaign 2026",
  "type": "SEARCH",
  "budget": 50000,
  "createdAt": "2026-01-15T10:30:00Z",
  "updatedAt": "2026-01-15T10:30:00Z"
}
```

## Pagination

List endpoints support pagination via `limit` and `offset` query parameters.

Response includes pagination metadata:
```json
{
  "data": [...],
  "pagination": {
    "limit": 50,
    "offset": 0,
    "total": 150
  }
}
```

## Rate Limiting

- **Default Limit**: 100 requests per minute per tenant
- **Headers**: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`
- **Response**: HTTP 429 when limit exceeded

## Versioning

API version is specified in the URL path: `/v1/`. Breaking changes will increment the version number.

## Compliance

- **GDPR**: All endpoints respect data subject rights
- **HIPAA**: PHI is encrypted at rest and in transit
- **SOC 2**: Audit logging enabled for all operations
