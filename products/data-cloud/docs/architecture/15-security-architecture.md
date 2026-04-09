# Security Architecture

See [03-system-context.md](03-system-context.md), [09-api-contract-architecture.md](09-api-contract-architecture.md), and [`diagrams/trust-boundaries.mmd`](diagrams/trust-boundaries.mmd).

## Security Control Areas

| Area | Implemented Evidence | Assessment |
|---|---|---|
| HTTP authn/authz middleware | `launcher/http/DataCloudSecurityFilter.java` | implemented but optional |
| Tenant isolation | `TenantIsolationHttpFilter`, tenant-scoped repos, `V011__implement_database_level_tenant_isolation.sql` | strong intent |
| API key auth | `ApiKeyAuthFilter`, `ApiKeyResolver` hook | available but not always wired |
| Policy enforcement | `PolicyEngine` integration in security filter | partial / deployment-dependent |
| Audit | `AuditService` integration in security filter and runtime modules | partial / optional |
| Browser token storage | `ui/src/lib/auth/tokenStorage.ts` | implemented with caveats |
| Secret injection | Helm `existingSecret`, K8s secret refs, Terraform + ESO guidance | strong infra intent |

## Implemented

- `DataCloudSecurityFilter` enforces public-route bypass, API-key authentication, tenant isolation, policy checks for critical routes, and fire-and-forget audit emission.
- The filter is fail-closed for policy evaluation errors when enforcing mode is enabled.
- UI token storage is memory-first with `sessionStorage` fallback and explicitly recommends migration to httpOnly cookies.
- Deployment assets avoid hard-coded credentials and rely on Kubernetes secrets or External Secrets Operator.
- DB-level tenant isolation is represented as a Flyway migration.

## Inferred

- Production security posture expects a gateway or upstream auth layer. This is consistent with OpenAPI documentation mentioning bearer tokens via a security gateway and with the standalone HTTP bootstrap not wiring all security collaborators.

## Missing

- `DataCloudHttpLauncherBootstrap` does not call `.withApiKeyResolver(...)`, `.withPolicyEngine(...)`, or `.withAuditService(...)`, so the standalone HTTP server logs that the security filter is inactive when no resolver is supplied.
- Frontend RBAC enforcement appears thin in the product-local repo; `ui/src/components/security/RBACGuard.tsx` re-exports a shared package component instead of defining product-local policy.

## Recommended

- Make standalone mode explicit:
  - either wire security services by default for direct deployment,
  - or mark standalone as “must sit behind security-gateway” in code and docs, with startup warnings elevated to hard validation where appropriate.

## Sensitive Data Paths

| Data | Path | Controls |
|---|---|---|
| Auth tokens | browser memory/session storage -> API headers | token storage abstraction, recommended cookie migration |
| Tenant identifiers | request headers/context -> repos -> DB | tenant filters, DB-level isolation migration |
| API keys | request header -> `ApiKeyAuthFilter` | only if resolver is wired |
| DB credentials | secrets manager / k8s secret -> env vars -> datasource | no defaults in image |
| Registry and event payloads | service/runtime -> Data Cloud stores | audit hooks and tenant scoping, uneven classification |

## Auth Sequence

### Implemented

1. Non-public HTTP route enters `DataCloudSecurityFilter`.
2. API key auth executes through `ApiKeyAuthFilter`.
3. Tenant context is established through `TenantIsolationHttpFilter`.
4. Policy engine runs for critical endpoints when configured.
5. Audit event emits asynchronously for sensitive/critical routes.

### Missing

- A fully wired default bootstrap path for this sequence in standalone launcher mode.

## Security Findings

| Finding | Evidence | Impact |
|---|---|---|
| Security middleware exists but is not guaranteed active | bootstrap vs filter code | accidental under-protection |
| Browser token handling is transitional | `tokenStorage.ts` comments | XSS and header-token residual risk |
| Security ownership is split between product runtime and shared platform gateway assumptions | OpenAPI + bootstrap + shared libs | unclear deployment requirements |
