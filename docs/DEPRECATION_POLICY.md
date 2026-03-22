# Module Deprecation Policy

> **Status**: Active  
> **Owner**: Platform Team  
> **Last Updated**: 2026-01-19

---

## Purpose

This policy defines the lifecycle stages and required steps for deprecating and retiring any Gradle module, TypeScript package, or shared service in the Ghatana monorepo. Formal deprecation ensures consumers have adequate time to migrate, and prevents accumulation of dead code.

---

## Lifecycle Stages

```
ACTIVE → DEPRECATED → SUNSET → RETIRED
```

| Stage | Description | Duration |
|-------|-------------|----------|
| **ACTIVE** | Module is in active use and maintenance | — |
| **DEPRECATED** | Scheduled for removal; no new consumers allowed; existing consumers must migrate | Minimum 3 months |
| **SUNSET** | Build file disabled; code still in repo but excluded from CI | 1 month |
| **RETIRED** | Code deleted; entry removed from `settings.gradle.kts` / `pnpm-workspace.yaml` | Final |

---

## Deprecation Requirements

### To Move ACTIVE → DEPRECATED

1. **ADR Required**: Create an ADR in `docs/adr/` explaining why the module is being deprecated and what replaces it.
2. **Migration Guide**: Add a `MIGRATION.md` inside the module directory with step-by-step migration instructions.
3. **Announcement**: Add a `@deprecated` JavaDoc tag to all public classes in the module (Java), or add a `deprecated` field to `package.json` with a message (TypeScript).
4. **Build Warning**: Add a `logger.warn("DEPRECATED: ...")` to the module's main entry point (or Spring Boot startup event).
5. **Consumer Notification**: Open issues in all known consumer repositories.
6. **CI Label**: Add a comment `// DEPRECATED(YYYY-MM): migrating to :replacement:path` to the `include()` line in `settings.gradle.kts`.
7. **Architecture Board Review**: The deprecation must be approved by the Architecture Board.

```kotlin
// settings.gradle.kts — example
include(":platform:java:legacy-module")  // DEPRECATED(2026-04): migrating to :platform:java:new-module — ADR-019
```

### To Move DEPRECATED → SUNSET

1. **Zero active consumers**: All consuming modules must have their dependencies updated.
2. **Tests pass without the module**: Run `./gradlew build -x :legacy:module:*` to confirm.
3. **3-month minimum**: Module must have been DEPRECATED for at least 90 days.
4. **Platform Team sign-off**: Required.

### To Move SUNSET → RETIRED

1. **Disable in settings**: Remove `include()` from `settings.gradle.kts`.
2. **1-month soak**: Wait 30 days in SUNSET before deleting source.
3. **Archive snapshot**: Tag the last commit before deletion with `deprecated/<module-name>` for future reference.
4. **Delete source directory**: Permanent removal from the repo.

---

## Fast-Track Deprecation (Security or Critical Bug)

When a module must be retired urgently (e.g., critical vulnerability, license violation):

- Minimum deprecation window drops to **2 weeks**.
- Architecture Board and Security Team must both approve.
- Consumers receive a P0 migration ticket immediately.

---

## Currently Deprecated Modules

| Module | Deprecated Since | Replacement | Target Retirement |
|--------|-----------------|-------------|-------------------|
| _(none)_ | — | — | — |

---

## Governance Enforcement

- `scripts/architecture-score-gate.sh` warns if DEPRECATED modules have been in DEPRECATED state for > 180 days without moving to SUNSET.
- `scripts/run-quarterly-audit.sh` includes a deprecation status review.
- The Platform Team reviews this table quarterly as part of the module health review.

---

## Related Documents

- [Module Admission Checklist](MODULE_ADMISSION_CHECKLIST.md)
- [Governance Freeze Rules](GOVERNANCE_FREEZE_RULES.md)
- `docs/adr/` — Architecture Decision Records
