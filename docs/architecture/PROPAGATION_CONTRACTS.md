# Tenant / Auth / Correlation / Audit Propagation Contracts

> **Status**: Authoritative  
> **Scope**: All Ghatana services, products, and platform modules  
> **Enforced by**: `TenantExtractionFilter`, `AuditLogger`, `platform:java:observability`

---

## 1. Tenant Propagation Contract

### Rule
Every HTTP request crossing a service boundary **MUST** carry the tenant identifier as a header.  
Every async event/message **MUST** embed `tenantId` in the envelope.  
No service may serve data from one tenant to a request carrying a different tenant identity.

### Header
```
X-Tenant-Id: <tenant-id>
```

### Canonical Implementation
**All products MUST use** `platform/java/http`'s `TenantExtractor` and `TenantExtractionFilter`:

```java
// Strict mode: 403 if X-Tenant-Id is absent
TenantExtractionFilter.strict()

// Lenient mode: fallback to "default-tenant" (development only)
TenantExtractionFilter.lenient()
```

Product-local tenant extraction classes **MUST NOT** be created. The `auth-gateway`'s
`TenantExtractor` provides additional multi-strategy extraction (JWT, path, subdomain)
specifically for authentication-layer needs and is the only permitted exception.

### Event Envelope
All platform events carry `tenantId` in the canonical envelope:

```json
{
  "id": "<uuid>",
  "tenantId": "<tenant-id>",
  "eventType": "<domain>.<action>",
  "occurredAtMs": 1700000000000,
  "correlationId": "<uuid>",
  "payload": "{...}"
}
```

### Validation
- `tenantId` MUST be non-empty.
- `tenantId` MUST match `^[a-zA-Z0-9_-]{1,64}$`.
- Services MUST reject requests with missing or malformed tenant IDs in strict mode.
- Load tests (`load-test-tenant-boundary.js`) validate that cross-tenant data leakage rate = 0.

---

## 2. Auth Propagation Contract

### Rule
Every authenticated request **MUST** carry a signed JWT as a Bearer token.  
Downstream services validating the JWT **MUST** call the auth-gateway's `/auth/validate`
endpoint or use the shared `JwtTokenProvider` from `platform:java:security`.

### Header
```
Authorization: Bearer <jwt>
```

### Token Types

| Type       | Issuer              | TTL        | Usage                                    |
|------------|---------------------|------------|------------------------------------------|
| `ACCESS`   | auth-gateway        | 1 hour     | Per-user, per-product API access         |
| `REFRESH`  | auth-gateway        | 7 days     | Token renewal only via `/auth/refresh`   |
| `PLATFORM` | auth-gateway/exchange | 15 min   | Cross-product service-to-service calls   |

### JWT Claims (Minimum Required)

```json
{
  "sub":       "<username>",
  "roles":     ["<role>"],
  "email":     "<email>",
  "tenantId":  "<tenant-id>",
  "tokenType": "ACCESS | REFRESH | PLATFORM",
  "exp":       1700000000,
  "iat":       1699996400
}
```

### Cross-Product Token Exchange
Products acquire a `PLATFORM` token by posting their product-scoped JWT to:
```
POST /auth/exchange
Authorization: Bearer <product-jwt>
```
The response contains `platformToken` (15-min TTL) accepted by all services.

### Blocklist
Revoked `REFRESH` tokens are tracked via `TokenBlocklist` (JDBC-backed in production).  
Services MUST check the blocklist before accepting a refresh token.

### Enforcement Points
- `auth-gateway`: login, refresh, validate, exchange, logout, tenant endpoints
- All product HTTP servers: `TenantExtractionFilter.strict()` on authenticated routes
- Contract tests: `AuthGatewayContractTest`, `AuthGatewayApiContractTest`

---

## 3. Correlation ID Propagation Contract

### Rule
Every request entering the system boundary **MUST** be assigned a correlation ID.  
The correlation ID **MUST** be propagated to all downstream synchronous and asynchronous calls.  
All log entries for a request **MUST** include the correlation ID.

### Header
```
X-Correlation-ID: <uuid-v4>
```

### Assignment
- Assigned by the ingress gateway / API gateway on first entry if absent.
- Services MUST preserve the incoming value and never generate a new one mid-flow.
- If absent at service entry (direct call), the service generates one and logs a warning.

### Async Propagation
Events and messages carry `correlationId` in the envelope (see §1 above).  
Worker threads restore the correlation ID from the message envelope before processing.

### Observability
```java
// Platform observability wires correlation ID into MDC automatically.
// Use platform:java:observability — do not wire MDC manually.
CorrelationContext.current() // reads the active correlation ID
```

### Enforcement
- Correlation ID is included in every structured log entry (JSON key: `correlationId`).
- Traces include the correlation ID as a span attribute.
- Tests use `X-Correlation-ID` headers and assert propagation in cross-service integration tests.

---

## 4. Audit Propagation Contract

### Rule
Every security-significant action **MUST** emit an audit event.  
Audit events are write-through: failures to write MUST NOT silently swallow the error.  
Audit events are append-only and MUST NOT be deleted or modified.

### Required Audit Events

| Event Type                   | Trigger                                        |
|------------------------------|------------------------------------------------|
| `AUTH_LOGIN_SUCCESS`         | Successful user login                          |
| `AUTH_LOGIN_FAILED`          | Failed login attempt                           |
| `AUTH_TOKEN_ISSUED`          | Access or refresh token minted                 |
| `AUTH_TOKEN_REVOKED`         | Logout / explicit revocation                   |
| `AUTH_TOKEN_BLOCKED`         | Blocked refresh token reuse attempt            |
| `TENANT_ACCESS`              | Tenant identity extracted and bound            |
| `RESOURCE_ACCESSED`          | Sensitive resource read (data-cloud entities)  |
| `RESOURCE_MODIFIED`          | Sensitive resource written or deleted          |
| `PERMISSION_DENIED`          | 403 returned to a caller                       |
| `COMPLIANCE_GDPR_ACCESS`     | GDPR access request processed                  |
| `COMPLIANCE_GDPR_ERASURE`    | GDPR erasure request processed                 |

### Audit Event Envelope

```json
{
  "eventType":     "AUTH_TOKEN_ISSUED",
  "userId":        "<username>",
  "tenantId":      "<tenant-id>",
  "correlationId": "<uuid>",
  "severity":      "INFO | WARN | ERROR",
  "message":       "<human-readable summary>",
  "occurredAtMs":  1700000000000,
  "metadata":      { "<key>": "<value>" }
}
```

### Canonical Implementation
Use `AuditLogger` from `shared-services/auth-gateway` for auth events.  
Platform modules use `platform:java:audit` where available.  
Products MUST NOT implement ad-hoc audit logging — use the platform contracts.

### Retention
Audit records MUST be retained for a minimum of **90 days** in production.  
GDPR and SOC2-related audit records MUST be retained per the applicable compliance window.

### Enforcement
- `AuditLogger` tests: `AuditLoggerPersistenceTest`
- Contract tests: `EventMessageContractTest` (envelope schema)
- Load tests validate audit write throughput implicitly via login/logout sequences

---

## 5. Summary: Header Quick Reference

| Concern         | Header / Mechanism          | Mandatory On        |
|-----------------|-----------------------------|---------------------|
| Tenant identity | `X-Tenant-Id`               | All service calls   |
| Authentication  | `Authorization: Bearer JWT` | All auth'd routes   |
| Correlation     | `X-Correlation-ID`          | All service calls   |
| Audit trail     | `AuditLogger` (server-side) | All security events |

---

## 6. Violations

Any violation of these contracts MUST:
1. Return an appropriate HTTP error code (401 / 403 / 400).
2. Emit an audit event with severity `WARN` or `ERROR`.
3. Log a structured warning with `correlationId`, `tenantId` (if known), and `userId` (if known).
4. Never expose internal details (stack traces, DB errors) to the caller.
