# Compatibility Endpoint Retirement Issue: GET /api/v1/capabilities

## Scope

- Compatibility endpoint: `GET /api/v1/capabilities`
- Canonical endpoint: `GET /api/v1/surfaces`
- Product: Data Cloud
- Plane: Runtime Truth (cross-plane consumer contract)

## Owner And Timeline

- Owner: Data Cloud API + Runtime Truth maintainers
- Created: 2026-05-07
- Target deprecation announcement: 2026-06-15
- Target removal date: 2026-08-31

## Why This Exists

`/api/v1/capabilities` is a temporary compatibility route. The canonical Runtime Truth contract is `/api/v1/surfaces`. Keeping both indefinitely increases drift risk across UI, SDKs, and operator tooling.

## Migration Plan

1. Keep `/api/v1/capabilities` and `/api/v1/surfaces` behaviorally equivalent while migration is active.
2. Move all first-party callers (UI, SDKs, tests, scripts) to `/api/v1/surfaces`.
3. Publish deprecation notice in API docs and release notes.
4. Add contract tests that fail if first-party code references `/api/v1/capabilities`.
5. Remove endpoint and compatibility tests after exit criteria are met.

## Exit Criteria (Removal Criteria)

- All first-party callers use `/api/v1/surfaces` only.
- OpenAPI, generated clients, and UI runtime-gating tests pass without `/api/v1/capabilities`.
- No references to `/api/v1/capabilities` in production code paths.
- Release notes include one full release-cycle deprecation notice before removal.
- Observability dashboards show no endpoint traffic from supported clients for 14 consecutive days.

## Verification Commands

```bash
# Check direct references in product code/docs
rg -n "/api/v1/capabilities" products/data-cloud

# Validate launcher tests and contracts remain green
./gradlew :products:data-cloud:delivery:launcher:test --no-build-cache --rerun-tasks
./gradlew :products:data-cloud:contracts:test --no-build-cache
```

## Status

- Current state: Open
- Compatibility endpoint removal: Pending migration completion
