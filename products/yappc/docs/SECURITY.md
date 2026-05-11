# YAPPC Security and Authentication Policy

**Status:** Active  
**Last Updated:** 2026-04-27  
**Owner:** Security Team

> **Source of Truth:** This document defines the canonical authentication and authorization policy for YAPPC.

---

## Overview

YAPPC uses a dual-mode authentication architecture:
- **Browser/Web UI**: Cookie-based session authentication (httpOnly cookie `yappc_session`)
- **Service-to-Service**: API key (`X-API-Key` header) or JWT Bearer token (`Authorization: Bearer <token>`)

This separation ensures:
- Browser clients use secure, server-managed sessions without exposing tokens to JavaScript
- Service-to-service communication uses machine-readable credentials for automation
- Clear security boundaries between human and machine access patterns

---

## Authentication Modes

### Mode 1: Browser/Web UI (Cookie-Session)

**Use Case:** Web UI and browser-based access by human users

**Mechanism:** httpOnly cookie-based session

**Cookie Details:**
- **Name:** `yappc_session`
- **Attributes:** `httpOnly`, `secure`, `SameSite=strict`
- **Management:** Server-managed, no client-side storage required

**Endpoints:**
- `POST /api/auth/login` - Establish session, sets cookie
- `POST /api/auth/refresh` - Refresh session, updates cookie
- `POST /api/auth/logout` - Invalidate session, clears cookie
- `GET /api/auth/validate` - Validate current session
- `GET /api/auth/me` - Get current user info

**Frontend Usage:**
```typescript
// Login - server sets httpOnly cookie
const login = async (email: string, password: string) => {
  const response = await fetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
    credentials: 'same-origin', // Required for cookie handling
  });
  const session = await response.json() as LoginSessionResponse;
  // No localStorage needed - server manages session cookie
};

// Refresh - cookie automatically sent
const refresh = async () => {
  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    credentials: 'same-origin',
  });
  return response.json() as RefreshTokenResponse;
};

// Logout - cookie cleared by server
const logout = async () => {
  const response = await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
  });
  // Server clears cookie
};
```

**Security Properties:**
- Tokens never exposed to JavaScript (httpOnly)
- CSRF protection via SameSite=strict
- Automatic session expiration managed by server
- No client-side token storage required

### Mode 2: Service-to-Service (API Key / Bearer Token)

**Use Case:** Backend services, automated tools, CI/CD pipelines

**Mechanism:** API key or JWT Bearer token

**API Key Authentication:**
- **Header:** `X-API-Key`
- **Use Case:** Internal service-to-service communication
- **Management:** Environment variables or secret management systems
- **Example:**
```bash
curl -H "X-API-Key: ${YAPPC_API_KEY}" http://localhost:8082/api/v1/projects
```

**JWT Bearer Token Authentication:**
- **Header:** `Authorization: Bearer <token>`
- **Use Case:** External integrations, third-party services
- **Management:** Issued by authentication service, validated by JWT provider
- **Example:**
```bash
curl -H "Authorization: Bearer ${JWT_TOKEN}" http://localhost:8082/api/v1/projects
```

**Security Properties:**
- Machine-readable credentials for automation
- No browser security context required
- Suitable for non-interactive access patterns

---

## Authorization Scope Extraction

### Scope Extraction Policy

The backend extracts authorization scopes from requests using a priority-based extraction policy:

**Priority Order:** `path > query > header`

1. **Path Parameters (Highest Priority)**
   - For routes with path parameters (e.g., `/api/v1/projects/{projectId}`), scope is inferred from the resource context
   - Example: `/api/v1/projects/{projectId}` → infers `project:read` scope
   - Path-based extraction is automatic and preferred when available

2. **Query Parameters (Medium Priority)**
   - Explicit scope can be passed as a query parameter: `?scope=project:read`
   - Used when path-based inference is not available
   - Example: `/api/v1/projects?scope=project:read`

3. **Headers (Lowest Priority)**
   - Scope can be passed via the `X-Scope` header: `X-Scope: project:read`
   - Used as a fallback when query parameter passing is not practical
   - Example: `X-Scope: project:read`

### Common Authorization Scopes

- `workspace:read` - Read access to workspace resources
- `workspace:write` - Write access to workspace resources
- `project:read` - Read access to project resources
- `project:write` - Write access to project resources
- `admin` - Administrative access across resources

### Frontend Usage

The frontend provides scoped request helpers to pass scopes according to this policy:

```typescript
import { getScoped, postScoped, ScopedRequestOptions } from '@/lib/api/client';

// Query parameter scope (default)
getScoped('/api/v1/projects', 'context', {
  scope: 'project:read',
  scopeLocation: 'query'
});

// Header-based scope
getScoped('/api/v1/projects', 'context', {
  scope: 'project:read',
  scopeLocation: 'header'
});
```

---

## Route Authorization

### Route Manifest as Source of Truth

**Route Manifest (`products/yappc/docs/api/route-manifest.yaml`)** defines authorization requirements for all routes.

Each route entry includes:
- **auth**: Authentication level (`public`, `required`, `optional`)
- **scopes**: Required OAuth scopes (when auth=required)
- **boundary**: Architectural boundary (`YAPPC`, `DATA_CLOUD_AEP`)
- **privacyClassification**: Data sensitivity (`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, `RESTRICTED`)

### Auth Levels

**public:** No authentication required
- Example: `/health`, `/ready`, `/api/v1/yappc/info`
- Use case: Health checks, public information

**required:** Authentication required
- Browser: Session cookie required
- Service: API key or Bearer token required
- Example: `/api/v1/projects`, `/api/v1/phase/packet`

**optional:** Authentication optional
- Guest access allowed
- Enhanced with authentication when available
- Example: (currently not used, reserved for future)

### Generated Authorization Registry

The `RouteAuthorizationRegistry` is generated from the route manifest at build time:
- Routes are loaded from manifest (no manual duplication)
- Auth level determines credential requirements
- Scopes are mapped to permissions
- Privacy classification determines audit logging

---

## Security Best Practices

### For Browser/Web UI

1. **Always use `credentials: 'same-origin'`** for cookie-based endpoints
2. **Never store tokens in localStorage** - use server-managed sessions
3. **Validate session on page load** - call `/api/auth/validate` or `/api/auth/me`
4. **Handle session expiration** - redirect to login on 401 responses
5. **Use HTTPS in production** - httpOnly cookies require secure transport

### For Service-to-Service

1. **Use environment variables** for API keys and secrets
2. **Rotate credentials regularly** - implement rotation strategy
3. **Use secret management** - Vault, AWS Secrets Manager, etc.
4. **Never commit secrets** to repository
5. **Use least privilege** - assign minimal required scopes

### For Both Modes

1. **Validate all inputs** at boundaries
2. **Log authentication events** for audit trails
3. **Implement rate limiting** to prevent brute force attacks
4. **Use HTTPS** in production environments
5. **Monitor for anomalies** - failed auth attempts, unusual patterns

---

## OpenAPI Security Schemes

The OpenAPI specification defines three security schemes:

### CookieAuth
```yaml
CookieAuth:
  type: apiKey
  in: cookie
  name: yappc_session
  description: Cookie-based session authentication for browser clients
```

### ApiKeyAuth
```yaml
ApiKeyAuth:
  type: apiKey
  in: header
  name: X-API-Key
  description: API key for internal service-to-service authentication
```

### BearerAuth
```yaml
BearerAuth:
  type: http
  scheme: bearer
  bearerFormat: JWT
  description: JWT Bearer token for internal service authentication
```

---

## Migration from Token-Oriented Auth

### Previous Architecture (Deprecated)

**Old Pattern:** Token-oriented authentication
- Login returned `accessToken` and `refreshToken`
- Tokens stored in localStorage
- Client manually managed token refresh
- Tokens exposed to JavaScript

### Current Architecture (Canonical)

**New Pattern:** Cookie-session authentication
- Login returns session info, server sets httpOnly cookie
- No client-side token storage required
- Server manages session lifecycle
- Tokens never exposed to JavaScript

### Migration Impact

**Frontend Changes:**
- Remove localStorage token storage
- Add `credentials: 'same-origin'` to fetch calls
- Update auth types to use `LoginSessionResponse` instead of `AuthTokenResponse`
- Remove manual token refresh logic

**Backend Changes:**
- Update auth filter to support session cookie extraction
- Keep API key and Bearer token support for service-to-service
- Update OpenAPI to document cookie-session auth for browser endpoints

**See:** `docs/api/API_CLIENT_MIGRATION_GUIDE.md` for detailed migration steps.

---

## Related Components

### Backend (Java)
- `com.ghatana.yappc.governance.auth.YappcApiAuthFilter` - Auth filter supporting cookie, API key, and Bearer
- `com.ghatana.yappc.governance.route.RouteAuthorizationRegistry` - Generated from route manifest
- `com.ghatana.yappc.services.security.LifecycleLoginController` - Login endpoint
- `com.ghatana.platform.security.port.JwtTokenProvider` - JWT token provider

### Frontend (TypeScript)
- `frontend/web/src/lib/api/client.ts` - API client with scoped request helpers
- `frontend/web/src/services/auth/AuthService` - Auth service for session management
- `frontend/web/src/clients/generated/` - OpenAPI-generated client

### Documentation
- `products/yappc/docs/api/route-manifest.yaml` - Source of truth for route authorization
- `products/yappc/docs/api/openapi.yaml` - OpenAPI specification with security schemes
- `products/yappc/docs/api/API_CLIENT_MIGRATION_GUIDE.md` - Client migration guide

---

## References

- Task 1.1: Choose canonical browser auth mode (cookie-session)
- Task 1.2: Normalize scope propagation (path > query > header)
- Task 4.1: Public vs required auth semantics
- Task 7.2: Align openapi.yaml auth/session, route paths, operationIds, security schemes
- Platform module: `platform:java:security`
