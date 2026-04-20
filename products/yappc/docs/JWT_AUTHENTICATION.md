# YAPPC JWT Authentication Architecture

## Overview

YAPPC uses JWT (JSON Web Tokens) for authentication across both the Node.js BFF (Backend for Frontend) and the Java lifecycle service. This document describes the single JWT authority pattern and configuration requirements.

## Single JWT Authority

**Principle:** All JWT tokens are issued and validated by a single authority - the Java lifecycle service's `JwtTokenProvider` from `platform:java:security`.

### Token Issuance Flow

1. **Login Request** → `POST /api/auth/login` (Java `LifecycleLoginController`)
2. **Token Generation** → Java `JwtTokenProvider` creates signed JWT
3. **Token Storage** → Frontend stores in localStorage (P1-1: migrate to httpOnly cookie)
4. **Token Validation** → Both Node BFF and Java service validate using the same secret

### Configuration

#### Java Service (Lifecycle Service)

**Environment Variable:** `YAPPC_JWT_SECRET`

```bash
# Required for production
export YAPPC_JWT_SECRET="<cryptographically-random-32+chars>"

# Development (not for production)
export YAPPC_JWT_SECRET="dev-secret-do-not-use-in-production"
```

**Location:** `LifecycleServiceModule.java` binds `JwtTokenProvider` using the secret from `YAPPC_JWT_SECRET`.

#### Node.js BFF

**Environment Variables:** 
- `JWT_ACCESS_SECRET` (should match `YAPPC_JWT_SECRET`)
- `JWT_REFRESH_SECRET` (separate secret for refresh tokens)

```bash
# Must match Java service's YAPPC_JWT_SECRET
export JWT_ACCESS_SECRET="${YAPPC_JWT_SECRET}"
export JWT_REFRESH_SECRET="<separate-cryptographically-random-secret>"
```

**Location:** `frontend/apps/api/src/services/auth/jwt-config.ts`

## Security Requirements

1. **Secret Length:** Minimum 32 characters for HMAC-SHA256
2. **Secret Generation:** Use cryptographically secure random generator
3. **Secret Rotation:** Implement rotation strategy without service disruption
4. **Secret Storage:** Use secure secret management (Vault, AWS Secrets Manager, etc.)
5. **No Hardcoded Secrets:** Never commit secrets to repository

## Token Structure

### Access Token Payload

```typescript
{
  userId: string;
  email: string;
  role: string; // "ADMIN" | "USER" | "VIEWER"
  tenantId: string;
  workspaceId?: string;
  iat: number; // Issued at
  exp: number; // Expiration (default 15m)
}
```

### Refresh Token Payload

```typescript
{
  userId: string;
  tokenId: string;
  iat: number;
  exp: number; // Expiration (default 7d)
}
```

## Validation Endpoints

- **Java:** `GET /api/auth/validate` - Validates JWT and returns user info
- **Java:** `GET /api/auth/me` - Returns current authenticated user
- **Node:** Middleware validates Bearer token on protected routes

## Migration Path (P1-1)

**Current:** Tokens stored in localStorage  
**Target:** httpOnly secure cookies for access tokens

### Implementation Steps

1. Update frontend to use httpOnly cookies
2. Modify login endpoint to set `Set-Cookie` header
3. Update auth middleware to read from cookies
4. Maintain localStorage for refresh tokens (or migrate to httpOnly as well)

## Fail-Safe Validation

The `YappcEnvironmentConfig` validates that:
- `YAPPC_JWT_SECRET` is set in production
- Secret is not a default placeholder value
- Secret meets minimum length requirements

## Related Components

- **Java:** `com.ghatana.platform.security.port.JwtTokenProvider`
- **Java:** `com.ghatana.yappc.services.security.LifecycleLoginController`
- **Java:** `com.ghatana.yappc.services.security.JwtAuthController`
- **Node:** `frontend/apps/api/src/services/auth/jwt-config.ts`
- **Node:** `frontend/apps/api/src/middleware/auth.middleware.ts`

## References

- P0-3: Align JWT secret between Node BFF and Java service
- P1-1: Migrate JWT from localStorage to httpOnly secure cookie
- Platform module: `platform:java:security`
