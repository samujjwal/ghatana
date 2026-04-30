# YAPPC Diagnostics Burn-Down Plan (YAPPC-001)

## Current State

- **Total diagnostics issues**: 3645 (from `get_errors(products/yappc)`)
- **Issue types**: compile breaks, package mismatches, unresolved symbols, hygiene debt
- **Priority**: P0 (blocks meaningful "100% behavior coverage" claims)

## Primary Issue: Package Naming Drift

### Observed Inconsistencies

The codebase has two competing package naming conventions for domain classes:

1. **`com.ghatana.yappc.domain.*`** — Used in:
   - `core/yappc-domain-impl/src/main/java/com/ghatana/yappc/domain/`
   - `core/yappc-services/src/main/java/com/ghatana/yappc/domain/`

2. **`com.ghatana.yappc.domain.*`** — Used in:
   - `core/yappc-domain-impl/src/main/java/com/ghatana/products/yappc/`
   - `core/yappc-domain-impl/src/test/java/com/ghatana/products/yappc/`

### Impact

- Import statements across modules are inconsistent
- Some files import from `com.ghatana.yappc.domain.*`
- This causes unresolved symbol errors and package mismatch diagnostics
- Breaks IDE navigation and refactoring tools

### Canonical Convention

The established convention across the broader Ghatana codebase is:
- **`com.ghatana.yappc.domain.*`** for YAPPC domain classes
- **`com.ghatana.yappc.*`** should be the canonical package

## Migration Strategy

### Phase 1: Audit and Catalog
1. Generate a complete list of files using `com.ghatana.yappc.*` package declarations
2. Generate a complete list of files importing from `com.ghatana.yappc.*`
3. Create a mapping of old package → new package

### Phase 2: Rename Packages
1. Rename packages to use `com.ghatana.yappc` in:
   - `core/yappc-domain-impl/src/main/java/com/ghatana/products/yappc/`
   - `core/yappc-domain-impl/src/test/java/com/ghatana/products/yappc/`

2. Update all import statements across the codebase

### Phase 3: Verify
1. Run `./gradlew :products:yappc:check` to verify no unresolved symbols
2. Run `./gradlew :products:yappc:test` to verify tests still pass
3. Re-run diagnostics scan to confirm reduction

## Secondary Issue: @SuppressWarnings Annotations

### Current State

Many files use `@SuppressWarnings("unchecked")` for generic casts from workflow context maps. Examples:
- `core/agents/workflow/src/main/java/com/ghatana/yappc/agent/architecture/ValidateArchitectureStep.java`
- `core/agents/workflow/src/main/java/com/ghatana/yappc/agent/architecture/DeriveContractsStep.java`
- `core/agents/runtime/src/main/java/com/ghatana/yappc/agent/YappcAgentRegistryAdapter.java`

### Assessment

These suppressions are **legitimate** with comments like "safe by design" or "Unchecked casts from generic workflow context maps; safe by design". They are not diagnostic debt but intentional suppressions for a known pattern.

### Recommendation

**Do not remove** these `@SuppressWarnings("unchecked")` annotations. They are:
- Documented with rationale
- Safe by design (casts from trusted workflow context maps)
- Required for generic context map access pattern in workflow steps

## Other Diagnostics Issues

### Compile Breaks
- Undefined builder/getText methods in completion APIs
- Unresolved imports after package migration
- Symbol resolution errors in test files

### Hygiene Debt
- Deprecated Gradle usage
- Configuration cache warnings
- Unused imports in some files

## Execution Order

1. **Package migration** (highest impact)
   - Rename packages in `core/yappc-domain-impl`
   - Update all imports
   - Verify compilation

2. **Symbol resolution fixes**
   - Fix undefined builder/getText methods
   - Resolve remaining import errors

3. **Hygiene cleanup**
   - Remove unused imports
   - Address deprecated Gradle usage
   - Fix configuration cache warnings

## Success Criteria

- Diagnostics count reduced from 3645 to < 500
- All modules compile without errors
- All tests pass after migration
- No remaining package naming inconsistencies

## References

- Audit Report: `yappc-audit-report-2026-04-29.md`
- TODO Tracker: `yappc-audit-todo-2026-04-29.md`
