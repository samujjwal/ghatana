# ADR-DC-005: Production Policy, Audit, Tenant Requirements — Fail Closed

**Status:** Accepted
**Date:** 2026-04-26
**Authors:** Data Cloud Architecture Team

## Context

Governance endpoints (purge, redaction, retention classification, compliance summary) previously tolerated missing audit and policy dependencies. In production profiles this is unacceptable: a missing audit log means actions are invisible, and a missing policy engine means restrictions are unenforced.

## Decision

1. In **production** and **strict-tenant** profiles, Data Cloud MUST refuse to start if any of the following are unavailable:
   - Authentication provider (API key resolver or JWT provider)
   - Tenant resolver
   - Policy engine
   - Audit service
2. All default/implicit tenant fallback paths MUST be removed in production.
3. `X-Tenant-ID` or JWT tenant claim MUST be enforced on every request.
4. Policy and audit dependencies MUST be non-nullable in production — handlers MUST throw on null rather than degrade silently.
5. Governance routes MUST fail closed when backing services are unavailable (return 503, not optimistic success).
6. Legal hold enforcement MUST block purge/redaction/export when a hold is active.
7. Destructive actions (purge, redaction, bulk delete, PII export) MUST require an approval flow.

## Consequences

- **Positive:** Enterprise trust. No silent bypass. Compliance posture matches runtime enforcement.
- **Negative:** Local/development setups must explicitly opt out with `DATACLOUD_PROFILE=local`.

## Related

- P0.4, P0.5 in `data-cloud-implementation-tasks.md`
- `DataCloudHttpServer.java` (`validateProductionDependencies`)
- `DataLifecycleHandler.java`
