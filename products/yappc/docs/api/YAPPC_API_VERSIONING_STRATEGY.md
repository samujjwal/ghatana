# YAPPC API Versioning Strategy

## Purpose
Define a consistent API versioning and deprecation policy across YAPPC Node gateway and Java backend services.

## Canonical Version
- Current stable version: `v1`.
- Canonical surface: `/api/v1/*`.
- Compatibility surfaces retained during migration: `/api/*` and `/v1/*`.

## Request Version Negotiation
Clients may request an API version using one of the following headers:
1. `X-API-Version`
2. `Accept-Version`

Supported values:
- `v1`
- `1`

Unsupported versions must return `406 Not Acceptable` with a machine-readable error body.

## Response Version Headers
Versioned API responses include:
- `x-api-version: 1`
- `x-api-compatibility: v1`
- `deprecation: false` (unless endpoint-specific deprecation is active)

## Backward Compatibility Rules
1. Breaking changes require a new major version path (for example, `v2`).
2. Non-breaking additive fields are allowed in-place for `v1`.
3. Existing required fields cannot be removed from active major versions.
4. OpenAPI contract updates are mandatory for all public API behavior changes.

## Deprecation Timeline Policy
When introducing a new major version:
1. Announce deprecation in release notes and docs at least 90 days before removal.
2. Mark deprecated endpoints with `deprecation: true` and publish sunset date.
3. Keep compatibility paths available for the deprecation window unless emergency security fixes require earlier retirement.
4. Remove deprecated version only after migration evidence and operational sign-off.

## Enforcement and Tests
- Node gateway enforcement and response headers:
  - `frontend/apps/api/src/middleware/apiVersioning.ts`
  - `frontend/apps/api/src/index.ts`
- Java backend enforcement wrapper:
  - `core/yappc-services/src/main/java/com/ghatana/yappc/api/ApiVersionPolicy.java`
  - `core/yappc-services/src/main/java/com/ghatana/yappc/api/YappcHttpServer.java`
- Contract and policy tests:
  - `frontend/apps/api/src/__tests__/api-versioning.test.ts`
  - `frontend/apps/api/src/__tests__/openapi-contract.test.ts`
  - `core/yappc-services/src/test/java/com/ghatana/yappc/api/YappcHttpServerAuthTest.java`
