# Owner: YAPPC Frontend API

**Team:** YAPPC Frontend Team
**Slack:** #yappc-frontend
**Parent ownership:** `products/yappc/OWNER.md`
**Last Updated:** 2026-04-29

## Module Purpose

TypeScript/Fastify API gateway for the YAPPC frontend. Provides:

- REST routes for workspaces, projects, devsecops, canvas, lifecycle, AI, planning, security scans, and auth
- GraphQL endpoint (schema-driven, backed by resolvers in `src/graphql/`)
- WebSocket support via `@fastify/websocket` for real-time canvas events
- Proxy/gateway to the canonical Java lifecycle backend (`JAVA_BACKEND_URL`)
- Auth middleware (JWT validation, dev bypass guard) and audit middleware

## Ownership Map

| Area | Path | Owned By |
|------|------|---------|
| API entry point | `src/index.ts` | YAPPC Frontend |
| Auth service (proxy) | `src/services/auth/` | YAPPC Frontend |
| Security scan services | `src/services/security/` | YAPPC Frontend / Security |
| Route handlers | `src/routes/` | YAPPC Frontend |
| GraphQL schema + resolvers | `src/graphql/` | YAPPC Frontend |
| Middleware (auth, audit, rate-limit) | `src/middleware/` | YAPPC Frontend |
| Observability (tracing, metrics) | `src/utils/` | YAPPC Platform |

## Dependency Rules

- This package MUST NOT contain domain business logic; it delegates to the Java backend.
- Auth is proxied through `ProxyAuthService` to the Java lifecycle service — no duplicate JWT issuance here.
- Security scan endpoints wire to `VulnerabilityScanService`, `ComplianceScanService`, and `SecurityScanService`.
- No direct database access; all persistence goes through the Java backend or the Prisma client wrapper in `src/database/`.

## Production Stability Notes

- `devAuthBypass` is gated by `assertDevAuthBypassAllowed()` — must never enable in production.
- `X-Correlation-ID` is mandatory in production; generated for dev/test only.
- All environment validation occurs at startup in `createApp()` — missing required vars abort boot.
- Metrics endpoint is protected in production: requires authenticated user, internal network, or `METRICS_API_KEY`.
