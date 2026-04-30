# YAPPC OpenAPI Contract Ownership

**Status:** Authoritative
**Last Updated:** 2026-04-29
**Relates to:** YAPPC-014

---

## Contract Files — Single Sources of Truth

YAPPC exposes three distinct OpenAPI contracts, each owned by a different service boundary.
They are **not duplicates** — they serve separate runtimes and audiences.

| File | Service | Runtime | Port | Version | Owned By |
|------|---------|---------|------|---------|---------|
| `products/yappc/docs/api/openapi.yaml` | YAPPC Lifecycle (Java backend) | Java / ActiveJ | 7003 | 2.0.0 | YAPPC Backend Team |
| `products/yappc/api/yappc-api.openapi.yaml` | YAPPC Product API (frontend gateway) | Node.js / Fastify | 7002 | 1.0.0 | YAPPC Frontend Team |
| `products/yappc/api/yappc-refactorer.openapi.yaml` | YAPPC Refactorer Service (standalone) | Java / ActiveJ | 8082 | 1.0.0 | YAPPC Refactor Team |

---

## Contract Descriptions

### 1. `docs/api/openapi.yaml` — Lifecycle API (Canonical for parity checks)

- **Purpose**: Documents all 8-phase lifecycle REST routes served by `yappc-services` and `yappc-api` Java modules.
- **Parity enforcement**: The `validateOpenApiParity` Gradle task in `core/yappc-api/build.gradle.kts` validates that every route in `docs/api/route-manifest.yaml` appears in this file. Fails `check` on drift.
- **Route manifest**: `docs/api/route-manifest.yaml` is the authoritative route registry for this contract.
- **Update rule**: Any new Java route must be added to both `route-manifest.yaml` AND this file before merging.

### 2. `api/yappc-api.openapi.yaml` — Product API (Gateway surface)

- **Purpose**: Documents the full public-facing API surface exposed by the Node.js gateway (`frontend/apps/api`), including workspace, project, workflow, agent, vector, design, code-generation, artifact, and refactoring endpoints.
- **Parity enforcement**: Not yet wired to a build-failing check. **Action (YAPPC-014)**: Wire a parity task against this contract in the frontend workspace.
- **Update rule**: Any new Node.js route must be reflected here before merging.

### 3. `api/yappc-refactorer.openapi.yaml` — Refactorer Service API

- **Purpose**: Documents the standalone refactoring job lifecycle API served by the refactorer process (separate from the main YAPPC server).
- **Parity enforcement**: Not yet wired to a build-failing check. Routes in `docs/api/route-manifest.yaml` under `refactorer-api:` provide the manifest.
- **Update rule**: Any new refactorer route must be reflected here before merging.

---

## Drift Prevention Rules

1. **Do not merge** a new backend route without updating the relevant OpenAPI file.
2. **Do not merge** a change to `docs/api/openapi.yaml` without also updating `docs/api/route-manifest.yaml` if the route set changes.
3. The `validateOpenApiParity` task is wired to `check` — it will fail the build on drift for the lifecycle contract.
4. For `yappc-api.openapi.yaml` and `yappc-refactorer.openapi.yaml`, parity is currently enforced by code review convention. Automated checks are tracked as YAPPC-014 work.

---

## Versioning Policy

See `docs/api/YAPPC_API_VERSIONING_STRATEGY.md` for the full versioning and deprecation policy.

Summary:
- Current stable version: `v1` (canonical path: `/api/v1/*`)
- Breaking changes require a new major version path segment
- Non-breaking additive fields are allowed in-place for `v1`
- OpenAPI contract updates are mandatory for all public API behavior changes

---

## Ownership Contacts

| Contract | Team | Slack |
|----------|------|-------|
| `docs/api/openapi.yaml` | YAPPC Backend Team | #yappc-core |
| `api/yappc-api.openapi.yaml` | YAPPC Frontend Team | #yappc-frontend |
| `api/yappc-refactorer.openapi.yaml` | YAPPC Refactor Team | #yappc-core |
