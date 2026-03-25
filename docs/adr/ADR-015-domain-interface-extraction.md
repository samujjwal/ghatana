# ADR-015: Domain Interface Extraction to Break core↔domain Circular Dependency

**Status:** Accepted  
**Date:** 2026-03-25  
**Deciders:** Platform Architecture Team  
**Source Finding:** SHARED_MODULES_AUDIT_REPORT.md CRIT-001

---

## Context

The `platform/java/core` and `platform/java/domain` modules have a circular dependency that violates clean architecture principles. Specifically:

- `platform/java/domain` imports service-layer classes from `com.ghatana.core.*` (e.g. `AbstractOperator`, `OperatorId`, `OperatorResult`, `Event`).
- `platform/java/core` in turn depends on `platform/java/domain` types for schema validation and event typing.

This circular dependency causes:
1. **Build instability** — parallel module compilation cannot clean-separate the two
2. **Test isolation failures** — domain tests pull in core service implementations
3. **Maintenance nightmare** — changes to core ripple unpredictably into domain and vice versa
4. Evidence: 463 files across 174 domain files import `com.ghatana.core.*` packages (as of audit date)

The ArchUnit test `PlatformArchitectureTest.DependencyRules` now enforces the new dependency rules via `domainMustNotImportCoreOperators`, `platformDomainMustNotImportCoreStateImpl`, and `coreMustNotImportDomainPipelineSpecs`.

---

## Decision

We will extract domain-level **interfaces** for `Event`, `Operator`, `OperatorResult`, and related types into `platform/java/domain`. Core service implementations will then implement these interfaces. This restores a strict downward dependency flow:

```
contracts  (no dependencies)
    ↓
domain     (depends on contracts, types only)
    ↓
core       (depends on domain interfaces, not vice versa)
    ↓
...higher modules (http, security, observability, etc.)
```

---

## Migration Phases

### Phase 1 — Introduce interfaces (non-breaking)
Create the following interfaces in `platform/java/domain`:

| New Interface | Package | Purpose |
|--------------|---------|---------|
| `DomainEvent` | `com.ghatana.platform.domain.event` | Replaces direct import of core `Event` class |
| `OperatorContract` | `com.ghatana.platform.domain.operator` | Replaces direct import of `AbstractOperator` |
| `OperatorResultContract` | `com.ghatana.platform.domain.operator` | Replaces direct import of `OperatorResult` |

Core implementations implement these interfaces. Both old and new types coexist.

### Phase 2 — Migrate callsites (incremental)
Update the 463+ files importing from `com.ghatana.core.*` in domain contexts to use the new domain interfaces instead.  
**Priority order:**
1. `platform/java/domain` itself (7 files estimated)
2. `platform/java/workflow` (depends on both core and domain)
3. `platform/java/agent-core` (depends on both)
4. Product modules: `agentic-event-processor`, `data-cloud`

### Phase 3 — Remove bidirectional imports
Once all callsites are migrated, `platform/java/domain` must no longer import `com.ghatana.core.*` service classes. Enforce via the ArchUnit rules added in `PlatformArchitectureTest`.

### Phase 4 — Flip `core` build dependency (breaking change, versioned)
Remove `implementation(project(":platform:java:domain"))` from `core/build.gradle.kts` if it exists after Phase 3.

---

## Consequences

**Positive:**
- Clean architecture is restored; each module can be compiled and tested independently
- `domain` module becomes a true anti-corruption layer
- Easier to onboard new developers (clear layer boundaries)
- ArchUnit tests now actively prevent regression

**Negative:**
- Large callsite migration (463+ files) requires careful incremental execution
- Temporary coexistence of old and new interfaces increases API surface

**Neutral:**
- No runtime behaviour change — interfaces add a layer of indirection at compile time only
- All existing tests remain valid during migration window

---

## Alternatives Considered

**A. No change / tolerate circular deps**  
Rejected — build instability is already manifesting; no path to clean architecture without this fix.

**B. Merge core and domain into one module**  
Rejected — violates the principle of bounded contexts; would create an unmaintainable god module.

**C. Use abstract classes instead of interfaces**  
Rejected — interfaces enforce a looser contract and allowing multiple inheritance; abstract classes would create tighter coupling.

---

## References

- SHARED_MODULES_AUDIT_REPORT.md — FINDING-001 (CRIT-001)
- `platform/java/testing/src/test/java/.../PlatformArchitectureTest.java` — enforcement tests
- ADR-004 (ActiveJ framework) — async patterns that domain interfaces must respect
