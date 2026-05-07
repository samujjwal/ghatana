# ADR-012: Keep AEP Gateway as First-Class Service

**Status**: Accepted  
**Date**: 2026-01-20  
**Context**: P7-2e — Decide on `aep/gateway` role

## Decision

**Keep `products/data-cloud/planes/action/gateway/` as a first-class BFF (Backend-for-Frontend) service.**

## Rationale

1. **Hybrid Backend Strategy Alignment**: The project architecture mandates Node.js/Fastify for User API concerns and Java/ActiveJ for Core Domain (see `copilot-instructions.md` §2). The gateway fulfills the Node.js User API role.

2. **Legitimate Responsibilities**:
   - JWT authentication (HS256 verification) before requests reach Java backend
   - CORS configuration (origin allowlisting)
   - WebSocket proxying for `/tail/events` real-time event streaming
   - Multi-tenant header forwarding (`X-Tenant-Id`)

3. **Thin Layer (~200 LOC)**: Small enough to maintain, focused on transport concerns only.

## Changes Made (P7-2e)

- Removed stale `package-lock.json` (pnpm workspace manages deps via root `pnpm-lock.yaml`)
- Fixed `pnpm-workspace.yaml`: corrected path from `products/data-cloud/planes/action/api` to `products/data-cloud/planes/action/gateway`
- Renamed package from `@aep/api` to `@ghatana/aep-gateway` (monorepo naming convention)
- Added description to `package.json`

## Follow-Up (P7-4)

- Add integration tests for JWT verification and proxy behavior
- Add structured logging (pino)
- Consider Dockerfile for gateway (currently only Java Dockerfile exists)
