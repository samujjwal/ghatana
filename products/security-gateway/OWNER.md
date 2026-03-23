# Owner: Security Gateway

**Team:** Security Team  
**Slack:** #platform-security  
**On-call:** Security on-call rotation  
**Architecture lead:** Security Tech Lead  
**Boundary audit score:** 5/10 (2026-03-22) — active, single module, limited scope

## Responsibility

Security Gateway is the **central authentication, authorization, and security enforcement layer** for the Ghatana platform. All products route their auth flows through this gateway. It is responsible for:
- JWT issuance and validation
- Role-based access control (RBAC)
- Per-tenant security policy enforcement
- Security event logging and audit

**Domain boundary:** Security Gateway is a platform-adjacent product providing foundational security services. Consumers should use the gateway's API rather than importing its internal modules. For low-level crypto primitives, use `platform:java:security` instead.

## Relationship to Other Auth Components

| Component | Responsibility | Owner |
|-----------|---------------|-------|
| `shared-services/auth-gateway` | Generic credential management, rate limiting, session management | Platform |
| `products/security-gateway` | Authorization decisions, JWT lifecycle, per-product policy enforcement | Security Team |
| `products/yappc/backend/auth` | YAPPC-specific persona-based permissions | YAPPC Team |

## Architecture

See [README.md](README.md) for the full component table and responsibility breakdown.

## Known Issues

- `OWNER.md` was missing as of the 2026-03-22 boundary audit (accountability gap)
- Potential merge candidate with `shared-services/auth-gateway` — evaluate in next boundary review
- Score of 5/10: single-module limited scope is appropriate for current state; grow with product demand
