# @ghatana/aep-gateway

## Purpose

`products/aep/gateway` is the AEP API gateway — a Fastify BFF (backend-for-frontend) proxy. It owns:

- JWT authentication and CORS enforcement for all requests to the AEP Java backend
- WebSocket forwarding for real-time agent execution events
- Route-level request/response transformation and schema validation

This is the **only public entry point** for AEP. Direct calls from browsers or external services to the Java backend are not permitted.

## Boundaries

- **Technology:** Node.js, Fastify, TypeScript
- **Upstream:** AEP Java `server` module (gRPC / HTTP)
- **Downstream:** AEP `ui` and cross-product consumers
- **Does not own:** business logic — all domain logic lives in the Java backend

## Usage

```bash
# Development
pnpm dev

# Production build
pnpm build && node dist/index.js
```

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `AEP_BACKEND_URL` | ✅ | Base URL of the AEP Java server |
| `JWT_PUBLIC_KEY` | ✅ | RS256 public key for JWT verification |
| `PORT` | ✗ | HTTP listen port (default: 3000) |
| `CORS_ORIGINS` | ✗ | Comma-separated allowed origins |

## Verification

```bash
pnpm test
```
