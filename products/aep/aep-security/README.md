# aep-security

## Purpose

`products/aep/aep-security` provides authentication, session management, and secret handling for AEP. It owns:

- `AepAuthFilter` — JWT authentication filter for all AEP HTTP and gRPC endpoints
- `SessionStore` — in-memory and persistent session store for agent sessions
- `AepSecretManager` — retrieves secrets (API keys, credentials) needed by agent runtime

## Boundaries

- **Uses:** `platform:java:security` for JWT validation primitives and credential abstractions
- **Does not own:** identity resolution — that is `aep-identity`; RBAC evaluation — that is done via `platform:java:security`
- **Fail-closed:** any auth failure results in immediate rejection; there is no anonymous fallback

## Key classes

| Class | Role |
|---|---|
| `AepAuthFilter` | Verifies JWT, extracts principal, and sets `TenantContext` |
| `SessionStore` | Thread-safe session storage with configurable TTL |
| `AepSecretManager` | Resolves named secrets from environment or a secrets backend |

## Verification

```bash
./gradlew :products:aep:aep-security:test
```
