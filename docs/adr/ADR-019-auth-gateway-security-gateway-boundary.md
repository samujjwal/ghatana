# ADR-019: Auth-Gateway vs Security-Gateway Boundary

**Status:** Accepted  
**Date:** 2026-03-31  
**Authors:** Platform Team  
**Related:** ADR-013 (Shared-Services Ownership), `docs/SHARED_SERVICES_STRATEGY.md`

---

## Context

Two components handle authentication and security enforcement in the Ghatana platform:

1. **`shared-services/auth-gateway`** — a cross-product runtime service responsible for
   OIDC/OAuth2-based login and canonical JWT issuance.
2. **`products/security-gateway`** — a product-level enforcement layer responsible for
   JWT validation, RBAC authorization, WAF, IP blocking, webhook verification, and
   gRPC policy enforcement.

Both components deal with JWTs, creating ambiguity about which is the canonical JWT
issuer and how tokens flow between them.

The V4 security-gateway audit (2026-03-31) identified:
- Overlap between `products/security-gateway`'s `JwtTokenProviderImpl` (issuance +
  validation) and `shared-services/auth-gateway`'s JWT issuance after OIDC login.
- No authoritative integration test proving that a token issued in one layer is
  accepted by the other.
- Hardcoded / in-memory RBAC role mappings in `security-gateway` that require redeployment.

---

## Decision

### 1. Canonical Token Issuance Path

**`shared-services/auth-gateway` is the CANONICAL JWT ISSUER for federated (OIDC/OAuth2) login.**

Token lifecycle:
```
Client --> auth-gateway (OIDC flow) --> platform JWT issued
       --> security-gateway (validates/enforces) on every product request
```

**`security-gateway`'s `AuthenticationServiceImpl` may issue JWTs for direct (username/password)
product-local auth**, but MUST use the same `platform/java/security` `JwtTokenProvider` port.
No product may instantiate its own independent JWT library. All JWT keys and signing must
route through `platform:java:security`.

### 2. Responsibility Split

| Concern | Owner |
|---------|-------|
| OIDC/OAuth2 login flow | `shared-services/auth-gateway` |
| Canonical platform JWT issuance (OIDC) | `shared-services/auth-gateway` |
| Direct username/password auth (internal) | `products/security-gateway` |
| JWT signing key management + rotation | `platform/java/security` (JwtKeyRotationServiceImpl) |
| JWT token validation on every request | `products/security-gateway` |
| RBAC authorization | `products/security-gateway` |
| WAF / IP blocking / rate limiting | `products/security-gateway` |
| Webhook signature verification | `products/security-gateway` |
| gRPC policy enforcement | `products/security-gateway` |
| Session store (Redis / JPA) | `products/security-gateway` |
| Credential store (JDBC) | `shared-services/auth-gateway` |

### 3. Prohibited Patterns

- Products MUST NOT instantiate their own JWT libraries directly.
- Products MUST NOT bypass `security-gateway` for token validation.
- `shared-services/auth-gateway` MUST NOT duplicate RBAC enforcement logic (that
  belongs in `security-gateway`).
- `shared-services` services MUST reference JWT functionality only through
  `platform:java:security` ports (see `SharedServicesJwtBoundaryTest`).

### 4. Required Integration Contract

A cross-service token validation test MUST exist in
`products/security-gateway/src/test/java/**/CrossServiceTokenValidationTest.java`
proving that:
- A JWT issued by the `platform:java:security` `JwtTokenProvider` (as `auth-gateway` uses)
  is successfully validated by `security-gateway`'s validation pipeline.
- A token with an expired claim is rejected.
- A token with the wrong issuer is rejected.

---

## Consequences

**Positive:**
- One canonical issuance path, one canonical validation path.
- All JWT key management stays in `platform:java:security`.
- Security boundary violations are surfaced by existing lint/architecture rules.

**Negative:**
- Products with direct auth flows must verify they use the `security-gateway` validation
  path rather than a local copy.

---

## Migration Notes

Any product-local `JwtTokenProvider` instantiation found during the next boundary audit
MUST be replaced with dependency injection from `platform:java:security` via the approved
`JwtTokenProviders` factory, consistent with the shared-services boundary test.
