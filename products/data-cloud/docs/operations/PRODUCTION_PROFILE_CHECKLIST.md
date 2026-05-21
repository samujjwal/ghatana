# Production Profile Checklist

This checklist is the operator-facing companion to the production readiness gate. A Data Cloud deployment is production-like when `deploymentMode` or `DC_RUNTIME_PROFILE` resolves to `production`, `staging`, or `sovereign`.

## Required Runtime Inputs

- Set one canonical runtime profile source before startup: `DC_RUNTIME_PROFILE` or `dc.runtime.profile`.
- Align `deploymentMode` with the runtime profile; do not run production deployments with the implicit `LOCAL` default.
- Provide tenant identity, auth, policy, audit, and persistence configuration through environment or deployment manifests.
- Run `products/data-cloud/scripts/verify-production-readiness.sh --profile local` before promoting an image.

## Durable Stores

- Context Plane must be wired with a durable `ContextStore`; `InMemoryContextStore` is allowed only in `local` and `test`.
- Use `JdbcContextStore` or another durable implementation for production-like deployments.
- Verify `/api/v1/surfaces` reports `contextStoreDurable=true` and the expected `contextStoreMode`.
- Ensure database credentials, migration ownership, backups, and retention policy are documented in the deployment runbook.

## Route And Contract Truth

- `RouteSecurityRegistry` must contain every runtime route from `DataCloudRouterBuilder`.
- `products/data-cloud/scripts/generate-route-manifest.mjs --check` must pass.
- `products/data-cloud/scripts/check-openapi-drift.sh` must pass without rewriting `/api/v1/action/*` to legacy root paths.
- `data-cloud.yaml` owns non-Action Data Cloud routes; `action-plane.yaml` owns canonical `/api/v1/action/*` routes; `aep.yaml` is compatibility-only.
- Regenerate UI Runtime Truth posture after any router or registry change.

## Security, Policy, And Audit

- Unknown route metadata must fail closed in production-like profiles.
- Enforcement must use `RouteSecurityMetadata` fields: `requiresAuth`, `requiresTenant`, `requiredAccess`, `requiresPolicy`, `requiresBlockingAudit`, `idempotent`, and `legacyStatus`.
- Critical routes must have policy decisions and blocking audit persistence enabled.
- Break-glass access must be explicitly configured, logged, and audited.

## Event And Action Plane Integrity

- Event append, query, get-by-offset, tail, replay, and Action Plane bridge tests must preserve the canonical envelope.
- Action Plane OpenAPI routes must remain under `/api/v1/action/*`.
- Compatibility aliases must be documented separately and must not become the canonical contract.

## Release Gate

- Full launcher test suite passes.
- Route/security drift scripts pass.
- OpenAPI validation passes.
- PMD and architecture boundary checks pass.
- Readiness output is archived with the release candidate evidence bundle.
