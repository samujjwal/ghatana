# @tutorputor/auth-client

Shared client-side auth utilities for all TutorPutor surfaces (web, admin, mobile).

## Purpose

Eliminates per-app duplication of:
- JWT payload decode (browser-safe, no signature verification)
- Token claim extraction and expiry checks
- Token pair storage interface and platform implementations
- Canonical `buildAuthHeaders()` replacing per-app `authHeaders()` copies

## Exports

| Subpath | Purpose |
|---|---|
| `@tutorputor/auth-client` | All exports (barrel) |
| `@tutorputor/auth-client/token` | JWT decode, claim extraction, expiry |
| `@tutorputor/auth-client/storage` | `AuthTokenStorage` interface + `LocalStorageAuthTokenStorage`, `InMemoryAuthTokenStorage` |
| `@tutorputor/auth-client/headers` | `buildAuthHeaders()`, `buildMultipartAuthHeaders()`, `extractBearerToken()` |

## Usage

```ts
import { extractTokenClaims, isTokenExpired } from "@tutorputor/auth-client/token";
import { LocalStorageAuthTokenStorage } from "@tutorputor/auth-client/storage";
import { buildAuthHeaders } from "@tutorputor/auth-client/headers";

// Store tokens after login
const storage = new LocalStorageAuthTokenStorage();
await storage.store({ accessToken, refreshToken });

// Check expiry before making API calls
if (isTokenExpired(accessToken)) {
  // trigger refresh flow
}

// Build headers for every request
const headers = buildAuthHeaders(accessToken);
```

## Mobile

For React Native, implement `AuthTokenStorage` using `SecureKeyManager` + MMKV from `tutorputor-mobile` instead of `LocalStorageAuthTokenStorage`.

## Security notes

- Client-side JWT decode does NOT verify signatures. Signature verification is always server-side.
- Tokens are stored in `localStorage` on web. For higher-security contexts, supply a `sessionStorage`-backed or in-memory implementation.
