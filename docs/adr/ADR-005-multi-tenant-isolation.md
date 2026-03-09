# ADR-005: Multi-Tenant Isolation via Thread-Local TenantContext

**Status:** Accepted  
**Date:** 2026-01-25  
**Decision Makers:** Platform Team, Security Team  
**Phase:** 4 — Production Hardening  

## Context

The platform is multi-tenant — multiple organizations share the same deployment. Tenant data must never leak across boundaries. The isolation mechanism must be:
- Transparent to business logic (no explicit tenant parameter threading)
- Enforced at the infrastructure level (data stores, checkpoints, audit)
- Compatible with async execution (thread pools, event loops)
- Testable for correctness under concurrent load

## Decision

Use **thread-local `TenantContext`** with a **`Principal`** value object for tenant identity propagation:

```java
// At request entry point
try (AutoCloseable scope = TenantContext.scope(principal)) {
    // All downstream code implicitly knows the tenant
    String tenantId = TenantContext.getCurrentTenantId();
}
// Context automatically restored on scope exit
```

**Key design choices:**
1. `TenantContext` uses `ThreadLocal<Principal>` + `ThreadLocal<String>` for tenant ID
2. `Principal` is an immutable value object: `name`, `roles` (List), `tenantId`
3. `scope(Principal)` returns `AutoCloseable` — try-with-resources ensures cleanup
4. Nested scopes restore the previous context on exit (stack semantics)
5. Default tenant is `"default-tenant"` when no context is set (development/single-tenant fallback)
6. Cross-thread transfer requires explicit capture and re-scope in the child thread

**Data layer enforcement:**
- `CheckpointStore`: all queries filter by `tenantId` parameter
- `DataCloudClient`: every method takes `tenantId` as first argument
- `PipelineExecutionContext`: carries `tenantId` bound to the execution
- `AgentContext`: exposes `getTenantId()` for agent processing
- `JdbcPersistentAuditService`: all SQL queries include `WHERE tenant_id = ?`

**Authorization layer:**
- `AgentAuthorizationService`: per-agent, per-tenant grant/revoke with role-based checks
- `PolicyService`: RBAC policies checked per-request via `RBACFilter`
- `AccessDeniedException`: thrown when authorization fails

## Rationale

- **Thread-local** is the standard Java pattern for implicit context propagation
- **Scope pattern** with `AutoCloseable` prevents leaked context (cannot forget to clean up)
- **Explicit tenant parameter** on data access methods provides belt-and-suspenders isolation
- **Principal equality** based on `name + tenantId` ensures cross-tenant identity collision is impossible
- 94 tenant isolation tests verify correctness across all three stabilized modules

## Consequences

- Async operations that switch threads (e.g., thread pools, ActiveJ Eventloop) must explicitly transfer context
- Default `"default-tenant"` can mask missing context setup — tests should assert explicit tenant IDs
- Four copies of `TenantContext` exist across packages — consolidation is a future task
- The `@RequiresPermission` annotation exists but has no AOP interceptor yet — enforcement is manual

## Alternatives Considered

1. **Explicit tenantId parameter everywhere** — rejected; too verbose, error-prone to forget
2. **Request-scoped DI** — rejected; ActiveJ DI doesn't natively support request scoping
3. **InheritableThreadLocal** — rejected; unreliable with thread pools, can leak context
4. **Context propagation library (context-propagation)** — deferred; would add dependency for marginal benefit
