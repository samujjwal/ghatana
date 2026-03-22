# YAPPC Restructuring Execution Plan

**Status:** Executed (Phases A–F complete)  
**Date:** 2026-03-22  
**Scope:** products/yappc  
**Builds On:** ADR-011 YAPPC Modular Service Architecture Refactoring

## Objective

Reduce YAPPC's operational module surface from the current fragmented structure to a smaller set of durable domains without undoing the architectural direction already accepted in ADR-011.

This plan is intentionally an execution plan, not a new architecture decision. ADR-011 already established the service-oriented direction. The remaining problem is packaging granularity: YAPPC still exposes too many Gradle modules for the amount of code and ownership they carry.

## Current State

YAPPC currently declares 34 product-local modules in its standalone settings file, with several additional monorepo includes resolved through helper functions. The module graph still contains multiple thin modules with very small source footprints and unclear ownership boundaries.

The consolidation pressure is highest in these areas:

| Module | Observed source footprint | Problem |
|:---|:---|:---|
| services/ai | 5 source files | Too small to justify separate build and ownership surface |
| services/scaffold | 4 source files | Thin facade, overlaps with lifecycle/scaffold flow |
| core/scaffold/packs | 5 source files | Packaging split is finer than the code warrants |
| backend/websocket | 11 source files | Distinct transport concern, but too small for a standalone Gradle module |
| infrastructure/security | 4 source files | Security surface is narrow and better owned with auth |
| launcher | 2 source files | Entry-point only module with near-zero independent lifecycle |

The practical result is avoidable build configuration overhead, extra dependency edges, more settings maintenance, and weaker ownership clarity.

## Target State

The immediate execution target is to reduce YAPPC from its current 34 product-local modules to roughly 29 by removing the weakest module boundaries first.

That intermediate state should then serve as the staging point for a later domain-level consolidation pass toward the longer-term audit goal of approximately 18 durable modules.

Target domains:

1. Runtime and entry points
2. Backend API and transport
3. Product services
4. Core domain and agents
5. Infrastructure integrations
6. Shared product libraries, examples, and validation tooling

This preserves the intent of ADR-011 while removing modules that are too small to justify independent build boundaries.

## Consolidation Decisions

### 1. Merge services/ai into services/lifecycle

Reasoning:

- Both modules participate in orchestration-heavy product flows.
- The AI service footprint is too small to justify separate Gradle isolation.
- Lifecycle is already the natural coordination layer for AI-assisted planning and generation.

Result:

- Remove the standalone `:services:ai` include.
- Move sources into `services/lifecycle` under a dedicated package namespace.
- Keep package names explicit enough to preserve conceptual separation inside the merged module.

### 2. Merge services/scaffold into services/lifecycle

Reasoning:

- Scaffold operations are lifecycle-adjacent and frequently change together.
- The current module size does not justify a separate build boundary.
- This reduces service-layer fragmentation without flattening domain packages.

Result:

- Remove `:services:scaffold`.
- Retain `scaffold` packages within the merged lifecycle service module.

### 3. Merge core/scaffold/packs into core/scaffold/core

Reasoning:

- The split between scaffold packs and scaffold core is too fine-grained for the current code volume.
- The small source surface means the current separation is mostly organizational, not architectural.

Result:

- Remove `:core:scaffold:packs`.
- Fold the implementation into `:core:scaffold:core`.
- Keep pack-specific concepts in package namespaces, not as separate Gradle modules.

### 4. Merge backend/websocket into backend/api

Reasoning:

- WebSocket handling is a transport concern adjacent to the backend API surface.
- Separate ownership is not justified by the current implementation size.
- Keeping both transports in one backend boundary simplifies deployment wiring.

Result:

- Remove `:backend:websocket`.
- Move runtime and route registration into `backend/api`.
- Preserve transport-specific packages under `websocket` within the merged module.

### 5. Merge infrastructure/security into backend/auth

Reasoning:

- The current security implementation is narrow and tightly coupled to authentication concerns.
- Security policy and auth wiring should be reviewed together in product space.
- This reduces cross-module coordination for identity changes.

Result:

- Remove `:infrastructure:security`.
- Move implementation into `backend/auth`.
- Keep package names explicit for encryption, policy, or token concerns if they remain distinct.

### 6. Fold launcher into platform or backend/api

Preferred direction:

- Move the thin entry-point code into `backend/api` if it is purely application bootstrap.
- If it exists only to assemble runtime modules, fold it into `platform` instead.

Decision rule:

- If the launcher owns HTTP/server startup, merge into `backend/api`.
- If it owns composition and wiring only, merge into `platform`.

This should not remain a dedicated module unless it grows a real independent lifecycle.

### 7. Remove empty or parent-only aggregation modules from standalone settings where possible

Candidate cleanup:

- `:core:scaffold` if it remains only a structural parent with no meaningful build logic.

Reasoning:

- Parent-only includes increase settings noise and perceived complexity.
- Structural grouping should exist only when it provides actual Gradle value.

## Proposed Target Module Layout

The exact final count may vary by one or two modules depending on launcher placement, but the intended layout is:

1. `:platform`
2. `:services`
3. `:services:platform`
4. `:services:lifecycle`
5. `:backend:api`
6. `:backend:auth`
7. `:backend:persistence`
8. `:backend:deployment`
9. `:core:domain`
10. `:core:domain:service`
11. `:core:domain:task`
12. `:core:scaffold:api`
13. `:core:scaffold:core`
14. `:core:ai`
15. `:core:agents`
16. `:core:agents:runtime`
17. `:core:agents:workflow`
18. `:core:agents:specialists`
19. `:core:refactorer:api`
20. `:core:refactorer:engine`
21. `:core:cli-tools`
22. `:core:knowledge-graph`
23. `:core:lifecycle`
24. `:core:framework`
25. `:core:spi`
26. `:infrastructure:datacloud`
27. `:libs:java:yappc-domain`
28. `:examples:sample-build-generator-plugin`
29. `:tools:validation-tests`

This is still larger than the long-term ideal, but it removes the weakest boundaries first and creates a stable intermediate state that can be delivered safely.

## Execution Sequence

### Phase A. Settings and dependency graph preparation

1. Freeze new module creation in `products/yappc` during the consolidation window.
2. Enumerate all inter-module dependencies in `products/yappc/settings.gradle.kts` and affected build files.
3. Map source sets, test fixtures, and runtime wiring for each merge candidate.
4. Confirm no consumer outside YAPPC depends on the soon-to-be-removed modules.

### Phase B. Service-layer consolidation

1. Merge `services/ai` into `services/lifecycle`.
2. Merge `services/scaffold` into `services/lifecycle`.
3. Update package imports and project dependencies.
4. Run targeted build and tests.

### Phase C. Scaffold core consolidation

1. Merge `core/scaffold/packs` into `core/scaffold/core`.
2. Remove redundant API exposure if both modules export the same concepts.
3. Validate scaffolding integration paths.

### Phase D. Backend and security consolidation

1. Merge `backend/websocket` into `backend/api`.
2. Merge `infrastructure/security` into `backend/auth`.
3. Re-run deployment wiring and route registration checks.

### Phase E. Entry-point cleanup

1. Decide whether `launcher` belongs in `backend/api` or `platform`.
2. Remove the standalone launcher module.
3. Simplify startup scripts and documentation.

### Phase F. Settings cleanup and enforcement

1. Remove obsolete includes from `products/yappc/settings.gradle.kts`.
2. Delete redundant build files and empty module directories.
3. Add an architecture test or settings audit to prevent re-introduction of ultra-thin modules.

## Validation Gates

Each merge must pass these checks before the next merge begins:

1. `./gradlew :products:yappc:build`
2. `./gradlew test`
3. No new cross-product dependencies
4. No duplicate abstractions created instead of moved
5. Startup path still works for standalone YAPPC build
6. Module removal reflected in docs and settings

## Rollback Strategy

Rollback should happen at the merge-step level, not as one large revert.

Rules:

1. Land each merge as an isolated changeset.
2. Keep package moves and dependency cleanup in the same changeset.
3. If a merge destabilizes startup or ownership, revert only that merge.
4. Do not partially restore deleted modules without also restoring settings and dependency declarations.

## Risks

| Risk | Impact | Mitigation |
|:---|:---|:---|
| Hidden runtime wiring in launcher or websocket modules | Startup regressions | Validate bootstrap and transport startup after each merge |
| Accidental widening of service responsibilities | New monolith inside a module | Keep package boundaries explicit after merge |
| Downstream imports to removed module paths | Compile failures outside YAPPC | Search monorepo before deleting module includes |
| Build logic drift across merged modules | Hard-to-diagnose Gradle issues | Normalize build files during the same step as merge |

## Success Criteria

This plan is complete when all of the following are true:

1. YAPPC removes the thin modules identified above.
2. Standalone and monorepo builds continue to work.
3. Ownership is clearer at the module level.
4. The settings file is materially shorter and easier to reason about.
5. No new duplicate platform-like abstractions are introduced during the merge work.

## Recommendation

Do not attempt to collapse YAPPC to the final ideal module count in one pass. Execute the six consolidations above first. They remove the weakest module boundaries with low conceptual risk and create a cleaner base for any later domain-level unification.