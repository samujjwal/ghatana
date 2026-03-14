# Platform Shared Libraries Remaining Work Plan

Date: 2026-03-14

Scope: `platform/*` and remaining product consumers of deprecated shared-library surfaces

References:

- `docs/PLATFORM_SHARED_LIBRARIES_AUDIT_2026-03-14.md`
- `docs/PLATFORM_SHARED_LIBRARIES_REMEDIATION_PLAN_2026-03-13.md`
- `docs/PLATFORM_SHARED_LIBRARIES_REMEDIATION_EXECUTION_SUMMARY_2026-03-13.md`

## Purpose

This document is the current execution plan for the work that is still open after the earlier audit and remediation batches.

It is intentionally narrower than the full audit:

- it only tracks work that is still remaining
- it reflects migration progress already completed
- it organizes the remaining work into waves with entry and exit criteria
- it calls out where the correct fix is a product-specific library, not more platform abstraction
- it is grounded in the current repo tree, package names, scripts, and workflows

## Current Snapshot

### Work already completed

The following cleanup has already happened and should not be re-planned:

- boundary hardening for platform Java modules has started
- major `@ghatana/ui` consumer migrations were completed in `products/software-org/client/web`
- `products/dcmaar/apps/parent-dashboard` is off active `@ghatana/ui` imports
- `products/data-cloud/ui` and `products/aep/ui` no longer have active `@ghatana/ui` imports
- stale `@ghatana/ui` manifest dependencies were removed from:
  - `products/data-cloud/ui`
  - `products/aep/ui`
  - `platform/typescript/charts`
  - `products/dcmaar/libs/typescript/shared-ui-tailwind`
  - `products/tutorputor/services/tutorputor-platform`
- source-link aliases were added in `data-cloud` and `aep-ui` so they can consume shared package source directly during migration

### What is still actively blocking completion

The cleanup is no longer blocked by missing migration intent. It is blocked by a smaller set of structural issues:

1. Shared TypeScript package health is not good enough.
   - `platform/typescript/design-system` has real type drift
   - `platform/typescript/canvas/flow-canvas` has real `@xyflow/react` generic/component typing breakage
   - `platform/typescript/charts` cannot cleanly type-check when source-linking `@ghatana/theme`
   - `platform/typescript/theme` still has an unresolved MUI version baseline mismatch

2. Remaining deprecated `@ghatana/ui` consumers are now concentrated, not scattered.
   - `products/yappc` is the largest cluster
   - `products/tutorputor` is the next largest cluster

3. Several "shared" modules still need product-boundary decisions, not just cleanup.
   - `flow-canvas`
   - Java registry/connectors modules
   - AI packages
   - some shell/canvas code

4. Some products that no longer use `@ghatana/ui` still fail type-check because the shared replacements are not healthy enough yet.
   - `products/data-cloud/ui`
   - `products/aep/ui`

## Open Inventory

### 1. Deprecated `@ghatana/ui` migration backlog

As of this plan, the active code backlog is concentrated in these areas:

- `products/yappc`: about 480 files still import `@ghatana/ui`
- `products/tutorputor`: about 56 files still import `@ghatana/ui`
- package manifests still depending on `@ghatana/ui`:
  - `products/tutorputor/apps/tutorputor-web/package.json`
  - `products/tutorputor/apps/tutorputor-admin/package.json`
  - `products/yappc/frontend/apps/web/package.json`
  - `products/yappc/frontend/libs/canvas/package.json`

There are also a few non-blocking but still real leftovers:

- Tailwind content globs that still point at `@ghatana/ui` or `@ghatana/ui/dist`
- local ESLint rules and migration scripts that still encourage `@ghatana/ui`
- compatibility comments/docs in `platform/typescript/ui`, `platform/typescript/design-system`, and `platform/typescript/ui-integration`
- active Tutorputor source under `products/tutorputor/apps/tutorputor-student/src` that still imports `@ghatana/ui`, even though that app does not currently have its own package manifest in this workspace

### 2. Shared TypeScript blockers

These are the highest-leverage shared-library fixes because they block multiple products at once.

#### `platform/typescript/design-system`

Remaining work:

- fix the actual router and render-path drift in `src/organisms/ProtectedRoute.tsx`
- keep `src/organisms/__tests__/ProtectedRoute.test.tsx` and `ProtectedRoute.stories.tsx` aligned with the component contract
- remove duplicate or ambiguous exports from `src/index.ts`
- resolve the remaining type surface drift around public atoms/molecules already exported from the root barrel
- remove stale `@ghatana/ui` references in docs/examples/comments
- make type-check green when consumed from source, not only from built `dist`

Known hotspot files:

- `platform/typescript/design-system/src/organisms/ProtectedRoute.tsx`
- `platform/typescript/design-system/src/index.ts`
- `platform/typescript/design-system/src/atoms/BottomNavigation.tsx`
- `platform/typescript/design-system/src/atoms/Fab.tsx`
- `platform/typescript/design-system/src/atoms/ToggleButton.tsx`
- `platform/typescript/design-system/src/atoms/Tooltip.tsx`
- `platform/typescript/design-system/src/molecules/UsageStatsCard.tsx`

#### `platform/typescript/canvas/flow-canvas`

Remaining work:

- decide whether this stays shared or becomes a thinner substrate under product-local adapters
- fix `@xyflow/react` generic usage for nodes/edges
- fix the `ReactFlow` JSX typing issues in the current source layout
- verify the shared types in `src/types.ts` do not force product assumptions
- add tests once the public API and ownership boundary are stable

Known hotspot files:

- `platform/typescript/canvas/flow-canvas/src/FlowCanvas.tsx`
- `platform/typescript/canvas/flow-canvas/src/edges/DataFlowEdge.tsx`
- `platform/typescript/canvas/flow-canvas/src/types.ts`
- built-in nodes under `platform/typescript/canvas/flow-canvas/src/nodes`

#### `platform/typescript/charts`

Remaining work:

- choose one supported consumption mode:
  - built packages only, or
  - source-linked packages via project references and path aliases
- remove the current `rootDir` mismatch when source-linking `@ghatana/theme`
- make package exports, `tsconfig`, and consumer aliasing consistent with the chosen strategy
- add a package-level smoke type-check that matches the chosen strategy

This is currently a packaging and build-graph problem, not a chart API problem.

#### `platform/typescript/ui`

Remaining work:

- freeze it as read-only compatibility only
- stop adding new components or behavior here
- remove duplicated logic that should live in `design-system` or `ui-integration`
- prepare final shutdown once product consumers hit zero

This package should not receive new feature work.

#### `platform/typescript/ui-integration`

Remaining work:

- quarantine or replace placeholder behavior in AI and page-builder integrations
- split stable shared integration contracts from experimental feature code
- remove documentation that implies it is a full clean replacement where it is not
- clean literal `@ghatana/ui` references in generated templates and docs

Known hotspot files:

- `platform/typescript/ui-integration/src/integration/aiFeatures.ts`
- `platform/typescript/ui-integration/src/integration/pageBuilder.ts`
- `platform/typescript/ui-integration/src/integration/collaboration.ts`

#### `platform/typescript/theme`

Remaining work:

- align peer and dev dependency baselines
- ensure source-link consumption works consistently
- settle one MUI baseline instead of the current peer-dev mismatch
- keep MUI compatibility in one clearly defined place

#### `platform/typescript/platform-shell`

Remaining work:

- decide whether this is actually shared across multiple products
- if not, move shell-specific behavior into product-local packages

### 3. Product-specific library migrations

These are not just "replace imports" exercises. They need local canonical targets.

#### YAPPC

Recommended target:

- treat `products/yappc/frontend/libs/ui` and `products/yappc/frontend/libs/canvas` as the product-owned canonical surfaces
- move YAPPC-only wrappers, shell pieces, canvas panels, and product affordances there
- stop making `platform/typescript/ui` carry YAPPC product semantics

Why:

- the remaining footprint is too large for a naive direct migration to `design-system`
- much of the YAPPC surface is canvas-heavy and product-shaped
- the repo already has `@ghatana/yappc-ui` and `@ghatana/yappc-canvas`

Important repo-specific cleanup already identified:

- `scripts/codemods/migrate-yappc-ui.ts` still rewrites `@ghatana/yappc-ui` imports toward `@ghatana/ui`
- `products/yappc/frontend/update-deps.js` still points at `@ghatana/ui`
- YAPPC local ESLint rules still encourage `@ghatana/ui`

#### Tutorputor

Recommended target:

- use `products/tutorputor/libs/tutorputor-ui-shared` as the canonical Tutorputor-owned surface where product wrappers are needed
- move imports to `design-system` directly for generic primitives
- reserve Tutorputor-local wrappers for domain-specific UI only

Why:

- the remaining imports are concentrated in admin and web
- Tutorputor already has product-local shared UI infrastructure
- that package is currently intentionally thin and should only grow where there is real cross-app reuse

Additional completeness note:

- the Tutorputor cleanup wave should include active `products/tutorputor/apps/tutorputor-student/src` imports, not only `tutorputor-admin` and `tutorputor-web`

#### DCMAAR leftovers

Remaining work:

- decide the future of `products/dcmaar/libs/typescript/shared-ui-tailwind`
- either:
  - keep it as a temporary product-local compatibility package, or
  - migrate `device-health` and delete it

This package is no longer a platform deprecation blocker, but it is still a local cleanup candidate.

### 4. Java shared-library backlog

#### `platform/java/agent-registry`

Remaining work:

- keep only neutral SPI and contracts in platform
- ensure no product-backed implementation remains under `platform/*`
- add contract tests for registry expectations

#### `platform/java/schema-registry`

Remaining work:

- same pattern as `agent-registry`
- keep storage-neutral shared interfaces only
- move product-backed implementations to product modules

#### `platform/java/connectors`

Remaining work:

- split generic connector abstractions from product transport adapters
- keep shared contracts in platform
- move product-specific event-log adapters into product code

#### `platform/java/ai-api`, `platform/java/ai-experimental`, `platform/java/ai-integration`

Remaining work:

- settle stable vs experimental module boundaries
- remove legacy `com.ghatana.ai.*` package drift where it prevents clean migration
- move concrete runtime implementations out of API-only modules
- quarantine or replace placeholder and mock behavior in runtime paths
- ensure consumers use the new package and module names, not deprecated legacy imports

Known repo-specific references:

- `platform/java/ai-experimental/src/main/java/service/OpenAIService.java`
- `platform/java/ai-experimental/src/main/java/embedding/OpenAIEmbeddingService.java`
- `platform/java/ai-integration/src/main/java/com/ghatana/ai/package-info.java`
- `platform/java/ai-integration/quarantine/README.md`

#### `platform/java/core`

Remaining work:

- move product-specific feature flags and defaults out of shared core
- keep only truly platform-wide flags in `platform/java/core`

#### `platform/java/plugin`

Remaining work:

- move AI-plugin contracts away from legacy AI package assumptions once the AI split is stable

### 5. Test and confidence backlog

High-priority test gaps still open:

- `platform/typescript/canvas`
- `platform/typescript/canvas/flow-canvas`
- `platform/typescript/platform-shell`
- `platform/typescript/design-system` public API smoke coverage
- `platform/typescript/api`
- `platform/java/observability`
- `platform/java/agent-memory`
- `platform/java/agent-registry`
- `platform/java/schema-registry`

Additional cleanup still needed:

- delete deprecated docs/examples/comments once the actual code migration is complete
- remove dead compatibility exports after consumer count reaches zero
- ensure quarantined placeholder code is either replaced or permanently deleted

## Modules With Low or No Material Remaining Work

These modules should stay on monitor-only unless new findings appear:

- `platform/contracts`
- `platform/java/domain`
- `platform/java/runtime`
- `platform/java/config`
- `platform/java/yaml-template`
- `platform/java/security`
- `platform/java/governance`
- `platform/java/audit`
- `platform/java/event-cloud`
- `platform/java/ingestion`
- `platform/java/workflow`
- `platform/java/agent-dispatch`
- `platform/java/agent-resilience`
- `platform/typescript/utils`
- `platform/typescript/i18n`
- `platform/typescript/realtime`
- `platform/typescript/tokens`

These should still participate in final regression gates, but they do not need a dedicated remediation wave right now.

## Recommended Approach

### Principle 1: Ban new deprecated usage before removing old usage

Add or tighten guardrails so no new code can:

- import `@ghatana/ui`
- add `@ghatana/ui` to `package.json`
- add new platform-to-product Java dependencies

Without this, cleanup work will churn.

### Principle 2: Fix shared blockers before large consumer migrations

Do not start the big YAPPC migration while:

- `design-system` type-check is broken
- `flow-canvas` public API is unstable
- source-linked package consumption still fails in products

That would turn one migration into many local workarounds.

### Principle 3: Use product-local canonical packages when the domain is product-shaped

Do not force every remaining consumer directly onto `design-system` if the UI is heavily product-specific.

Preferred pattern:

- generic primitives -> `@ghatana/design-system`
- product-specific wrappers and adapters -> product-local packages

This is especially important for:

- YAPPC
- Tutorputor
- DCMAAR device-health, if `shared-ui-tailwind` remains temporarily

### Principle 4: Prefer codemods for repetitive import replacement

Manual file-by-file migration is appropriate for small clusters only.

For the remaining large clusters:

- define import mapping
- codemod imports
- hand-fix only the files that fail after codemod

### Principle 5: Delete in two steps

For every deprecated surface:

1. drive active consumer count to zero
2. remove the deprecated surface and its docs/examples/tests in one cleanup wave

This keeps deprecation work measurable and avoids half-deleted compatibility layers.

## Execution Waves

The wave order below keeps the original sequencing, but the concrete tasks and commands are grounded in the current repo state.

## Wave 0: Freeze Drift

**Status: READY TO START**

Goal:

- stop new deprecated usage and new platform-boundary drift from landing while cleanup continues

Repo-grounded work:

1. Extend the existing Java boundary guardrail where it already lives:
   - `build.gradle.kts`
   - `gradle/platform-boundary-check.gradle`

2. Add a repo-level `@ghatana/ui` usage check that can be invoked from existing CI entry points rather than inventing new workflow files.
   - likely homes: `ci.yml`, `pr-checks.yml`, and the product-specific frontend workflows that already validate package health
   - acceptable implementation forms:
     - a shared shell script under `scripts/`
     - a root package script
     - an inline `rg` check reused across existing workflows

3. Block new manifest and config drift, not just source imports.
   - `package.json` dependencies
   - Tailwind content globs
   - local migration scripts that still point to deprecated packages
   - local ESLint rules that still enforce `@ghatana/ui`

4. Make the policy explicit: `platform/*` cannot depend on `products/*`, and new product code should not add fresh `@ghatana/ui` imports.

Verification:

- `rg -n '@ghatana/ui' . -g '!**/*.md' -g '!**/pnpm-lock.yaml'`
- `rg -n '"@ghatana/ui": "workspace:\\*"' . -g 'package.json'`
- `./gradlew :platform:java:agent-registry:compileJava :platform:java:schema-registry:test :platform:java:connectors:test --no-daemon`

Exit criteria:

- no new `@ghatana/ui` imports can land without failing an existing CI path
- no new `platform/* -> products/*` Java dependency can land silently
- the remaining exceptions are visible via one repeatable repo-level scan

## Wave 1: Shared TypeScript Stabilization

**Status: READY AFTER WAVE 0**

Goal:

- make the replacement shared packages healthy enough that product migrations stop tripping over them

Repo-grounded work:

1. Stabilize `platform/typescript/design-system`.
   - fix the real failure path in `src/organisms/ProtectedRoute.tsx`
   - align `ProtectedRoute` tests and stories with the real contract
   - clean duplicate or ambiguous exports in `src/index.ts`
   - resolve type drift in already-exported public components before widening the surface further
   - clean stale `@ghatana/ui` branding from comments and examples in active source

2. Stabilize `platform/typescript/canvas/flow-canvas`.
   - fix `@xyflow/react` usage in `src/FlowCanvas.tsx`
   - fix edge typing in `src/edges/DataFlowEdge.tsx`
   - verify `src/types.ts` and built-in nodes stay generic enough for shared use
   - decide what remains shared substrate versus what must move behind product-local YAPPC and AEP adapters

3. Stabilize `platform/typescript/charts`.
   - choose a single supported consumption model
   - align `package.json`, exports, `tsconfig`, and source-linking with that model
   - remove the `rootDir` mismatch currently exposed by source-linked `@ghatana/theme`

4. Stabilize `platform/typescript/theme`.
   - pick one MUI baseline and make peer and dev dependencies match it
   - verify that `design-system`, `charts`, `data-cloud`, and `aep` all consume it consistently

5. Re-validate migrated product consumers once the shared fixes land.
   - `products/data-cloud/ui`
   - `products/aep/ui`

Verification:

- `pnpm --filter @ghatana/design-system run type-check`
- `pnpm --filter @ghatana/flow-canvas run typecheck`
- `pnpm --filter @ghatana/charts run type-check`
- `pnpm --filter @ghatana/theme run type-check`
- `pnpm --filter @ghatana/data-cloud-ui run type-check`
- `pnpm --filter @ghatana/aep-ui run type-check`

Exit criteria:

- the shared TypeScript packages above type-check with the chosen source or build strategy
- `data-cloud` and `aep-ui` no longer fail because of shared-package breakage
- the Wave 3 and Wave 4 migrations can proceed without local package workarounds

## Wave 2: Residual Config, Docs, and Policy Cleanup

**Status: READY AFTER WAVE 1**

Goal:

- close the low-risk leftovers outside the two big product clusters

Repo-grounded work:

1. Clean Tailwind config drift that still references `@ghatana/ui`.
   - `products/tutorputor/apps/tutorputor-admin/tailwind.config.js`
   - `products/tutorputor/apps/tutorputor-web/tailwind.config.js`
   - `products/software-org/client/web/tailwind.config.ts`
   - `products/dcmaar/apps/parent-dashboard/tailwind.config.js`
   - any other matches found by search at execution time

2. Clean stale compatibility guidance in shared packages.
   - `platform/typescript/design-system`
   - `platform/typescript/ui-integration`
   - any active READMEs or source comments still telling consumers to use `@ghatana/ui`

3. Retire or rewrite local rules and scripts that still normalize deprecated usage.
   - `products/software-org/client/web/eslint-local-rules/rules/prefer-ghatana-ui.ts`
   - `products/dcmaar/apps/software-org/apps/web/eslint-local-rules/index.js`
   - YAPPC rules can be deferred to Wave 4 if they are still actively needed during migration, but they must be tracked now

4. Decide the fate of `products/dcmaar/libs/typescript/shared-ui-tailwind`.
   - keep temporarily as a product-local adapter, or
   - migrate remaining consumers and delete it

Verification:

- `rg -n '@ghatana/ui' products/software-org products/dcmaar products/data-cloud products/aep products/tutorputor -g 'tailwind.config.*' -g '*.ts' -g '*.tsx' -g '*.js' -g 'package.json'`

Exit criteria:

- no residual config or policy drift remains outside Tutorputor and YAPPC
- clean products stay clean
- the DCMAAR `shared-ui-tailwind` decision is documented and executed or explicitly deferred

## Wave 3: Tutorputor Migration

**Status: READY AFTER WAVES 0-2**

Goal:

- eliminate `@ghatana/ui` from the remaining Tutorputor code paths

Repo-grounded work:

1. Use the existing canonical product-local target:
   - `@ghatana/tutorputor-ui-shared`

2. Keep `@ghatana/tutorputor-ui-shared` intentionally thin.
   - generic primitives should go directly to `@ghatana/design-system`
   - only add Tutorputor-local wrappers when reused across multiple Tutorputor apps
   - do not invent a broad component surface if the repo does not need one

3. Migrate the actual active Tutorputor clusters.
   - `products/tutorputor/apps/tutorputor-admin`
   - `products/tutorputor/apps/tutorputor-web`
   - active `products/tutorputor/apps/tutorputor-student/src` imports

4. Clean Tutorputor package and Tailwind drift.
   - remove `@ghatana/ui` from package manifests when consumer count reaches zero
   - remove `@ghatana/ui/dist` from Tailwind content globs

5. Only after the import migration is stable, decide whether any Tutorputor-specific wrapper should move into `@ghatana/tutorputor-ui-shared`.

Verification:

- `pnpm --filter @ghatana/tutorputor-admin run typecheck`
- `pnpm --filter @ghatana/tutorputor-web run type-check`
- `rg -n '@ghatana/ui' products/tutorputor -g '*.ts' -g '*.tsx' -g '*.js' -g 'package.json'`

Verification note:

- `tutorputor-student` currently has active source imports but no separate package manifest in this workspace. Its migration still needs to be included in the source cleanup, and any package-level verification gap should be called out explicitly until that app is wired into the same validation path.

Exit criteria:

- zero active `@ghatana/ui` imports in Tutorputor code
- zero Tutorputor package dependencies on `@ghatana/ui`
- Tutorputor verification paths pass with the new import surface

## Wave 4: YAPPC Migration

**Status: READY AFTER WAVES 0-3**

Goal:

- remove the largest remaining deprecated consumer cluster

Repo-grounded work:

1. Use the existing YAPPC-owned packages.
   - `@ghatana/yappc-ui`
   - `@ghatana/yappc-canvas`

2. Remove YAPPC-local drift that currently pushes in the wrong direction.
   - retire or rewrite `scripts/codemods/migrate-yappc-ui.ts`
   - update `products/yappc/frontend/update-deps.js`
   - rewrite or remove YAPPC ESLint rules that currently prefer `@ghatana/ui`

3. Migrate by slice instead of across the entire product at once.
   - YAPPC local UI library
   - YAPPC local canvas library
   - `products/yappc/frontend/apps/web` canvas-heavy app surface
   - any remaining shared helper barrels

4. Keep the ownership split clear.
   - generic primitives -> `@ghatana/design-system`
   - YAPPC-specific canvas behavior -> `@ghatana/yappc-canvas`
   - YAPPC-specific UI wrappers -> `@ghatana/yappc-ui`
   - avoid inventing new package names or renaming existing product packages mid-migration

5. Revisit Tailwind only if the YAPPC source locations change. The current YAPPC Tailwind configuration already points at local source surfaces more than the deprecated package.

Verification:

- `pnpm --filter @ghatana/yappc-canvas run typecheck`
- `pnpm --filter @ghatana/yappc-ui run build:types`
- `pnpm --filter @ghatana/yappc-web-app run typecheck`
- `pnpm --filter @ghatana/yappc-web-app run test:smoke`
- `rg -n '@ghatana/ui' products/yappc -g '*.ts' -g '*.tsx' -g '*.js' -g 'package.json'`

Exit criteria:

- zero active `@ghatana/ui` imports in YAPPC
- zero YAPPC package dependencies on `@ghatana/ui`
- YAPPC app and local libraries validate against their product-owned replacement surfaces
- product-specific canvas behavior no longer leaks back into platform packages

## Wave 5: Java Shared-Library Extraction and AI Cleanup

**Status: READY AFTER WAVE 0**

Goal:

- finish the cross-product boundary cleanup outside the TypeScript UI track

Repo-grounded work:

1. `agent-registry`, `schema-registry`, and `connectors`
   - keep shared SPI and contracts only
   - move product-backed implementations into product modules
   - add contract tests for the shared interfaces
   - remove platform build coupling to product-specific storage or runtime assumptions

2. `ai-api`, `ai-experimental`, and `ai-integration`
   - settle the stable versus experimental boundary
   - move concrete runtime implementations out of API-only modules
   - replace or quarantine placeholder runtime behavior
   - complete the migration away from legacy `com.ghatana.ai.*` assumptions where active consumers still depend on them
   - use the existing quarantine structure where appropriate instead of inventing new module names in the plan

3. `core` and `plugin`
   - move product-specific feature defaults out of shared core
   - update plugin contracts only after the AI boundary is stable

4. Consumer migration
   - update product build files and imports after the shared versus product ownership split is finalized
   - avoid blind mass-rewrite commands in the plan; treat this as contract-driven refactoring

Verification:

- `./gradlew :platform:java:agent-registry:compileJava :platform:java:schema-registry:test :platform:java:connectors:test --no-daemon`
- `./gradlew :platform:java:ai-api:compileJava :platform:java:ai-experimental:compileJava :platform:java:ai-integration:compileJava --no-daemon`

Exit criteria:

- no product-backed registry, schema, or connector implementations remain under `platform/*`
- stable AI contracts are clearly separated from experimental and placeholder runtime code
- active consumers use the new shared contracts and not deprecated legacy package assumptions

## Wave 6: Deprecation Shutdown and Code Deletion

**Status: READY AFTER WAVES 0-5**

Goal:

- remove deprecated code instead of just marking it deprecated

Repo-grounded work:

1. Build the deletion checklist from the actual tree, not from guessed paths.
   - remaining source imports
   - remaining package dependencies
   - Tailwind content globs
   - local ESLint rules
   - local codemods and update scripts
   - compatibility comments, docs, and examples

2. Revisit the real compatibility surfaces before deleting anything.
   - `platform/typescript/ui`
   - `platform/typescript/design-system` comments and compatibility wording
   - `platform/typescript/ui-integration` placeholder/template references
   - YAPPC local rules and scripts that still point to `@ghatana/ui`
   - Software-org and DCMAAR local rules that still mention `@ghatana/ui`

3. Decide the `platform/typescript/ui` end state explicitly.
   - acceptable options:
     - one release of a tombstone package
     - immediate hard delete if release management and all consumers allow it
   - this should be a deliberate release decision, not an assumed implementation detail in the plan

4. Delete only repo-confirmed artifacts.
   - do not pre-commit to deleting nonexistent workflow files or archived directories
   - do not hardcode file names that are not present in this tree

Verification:

- `rg -n '@ghatana/ui' . -g '!**/*.md' -g '!**/pnpm-lock.yaml'`
- `rg -n '"@ghatana/ui": "workspace:\\*"' . -g 'package.json'`
- `pnpm why @ghatana/ui`
- `pnpm install --ignore-scripts`

Exit criteria:

- the deprecated platform UI surface is either deleted or reduced to a deliberate, release-managed tombstone
- no active code, config, package manifests, codemods, or local rules point at deprecated surfaces
- deprecated source code is deleted only after consumer count is zero

## Wave 7: Test Backfill and Final Hardening

**Status: READY AFTER WAVES 0-6**

Goal:

- leave the shared-library layer in a stable, enforceable state

Repo-grounded work:

1. Add tests to the real risk areas, not just broad snapshot coverage.
   - `design-system`:
     - `ProtectedRoute`
     - public root-barrel exports
     - any native-select and theme-provider regressions discovered during migration
   - `flow-canvas`:
     - `FlowCanvas`
     - `DataFlowEdge`
     - public export smoke tests
   - `charts`:
     - package-level validation for the chosen build and consumption model

2. Add Java contract tests where the shared boundary matters most.
   - `agent-registry`
   - `schema-registry`
   - `connectors`

3. Add repo health checks only after deciding their real home.
   - root package script, or
   - shared script under `scripts/`, or
   - existing workflow step
   - do not invent script names up front

4. Wire the final checks into existing CI paths instead of assuming new workflow files.

Verification:

- package-specific test and type-check commands for the touched modules
- existing CI workflows validate the new regression checks

Exit criteria:

- shared-package CI covers the main public API surfaces
- deprecation regressions are blocked automatically
- module ownership and boundary expectations are documented and enforceable

## Recommended Order

Recommended sequence:

1. Wave 0: Freeze Drift
2. Wave 1: Shared TypeScript Stabilization
3. Wave 2: Residual Config, Docs, and Policy Cleanup
4. Wave 3: Tutorputor Migration
5. Wave 4: YAPPC Migration
6. Wave 6: Deprecation Shutdown and Code Deletion
7. Wave 7: Test Backfill and Final Hardening

Parallel track:

- Wave 5 can begin after Wave 0 and run in parallel with Waves 3 and 4 because it is mostly independent Java boundary work

Critical path:

- Wave 0 -> Wave 1 -> Wave 3 -> Wave 4 -> Wave 6 -> Wave 7

Estimated duration:

- shared TypeScript stabilization and product migrations remain the dominant path
- the Java cleanup can shorten overall duration if run in parallel instead of waiting behind YAPPC

## Verification Gates Per Wave

Mandatory verification for every wave:

- repo search for deprecated imports and dependencies
- package-manager install sanity check when manifests change
- focused type-check or compile checks for touched packages
- targeted regression tests for the migrated surface

Additional final gates:

- zero remaining `@ghatana/ui` package dependencies
- zero remaining platform Java boundary violations
- zero placeholder or mock runtime behavior in shared production paths

## Immediate Next Batch

Start here if work continues from the current branch:

1. Extend the existing guardrails instead of inventing new integration points.
   - root Gradle boundary enforcement
   - repo-level `@ghatana/ui` scans wired into existing CI entry points

2. Fix the shared package blockers in this order:
   - `@ghatana/design-system`
   - `@ghatana/flow-canvas`
   - `@ghatana/charts`
   - `@ghatana/theme`

3. Re-run the already-migrated consumers that depend on those packages:
   - `pnpm --filter @ghatana/data-cloud-ui run type-check`
   - `pnpm --filter @ghatana/aep-ui run type-check`

4. Only after Wave 1 is green, start the remaining product migrations:
   - Tutorputor first
   - YAPPC second

Do not start the full Tutorputor or YAPPC migration while the shared replacements are still broken.

Success criteria for the immediate next batch:

- guardrails are enforced from real repo integration points
- shared TypeScript packages validate with repo-accurate commands
- `data-cloud` and `aep-ui` no longer fail because of shared package instability
- the remaining product waves can be executed without compensating local hacks

## Implementation Status Tracking

Wave status tracker:

- Wave 0: READY TO START
- Wave 1: READY AFTER WAVE 0
- Wave 2: READY AFTER WAVE 1
- Wave 3: READY AFTER WAVES 0-2
- Wave 4: READY AFTER WAVES 0-3
- Wave 5: READY AFTER WAVE 0
- Wave 6: READY AFTER WAVES 0-5
- Wave 7: READY AFTER WAVES 0-6

Primary blockers and risks:

- shared package health issues still block product migrations
- YAPPC remains the largest migration cluster and has local rules that still favor deprecated surfaces
- Tutorputor still has a few active imports outside the two main app manifests
- Java AI boundaries still contain placeholder behavior and legacy package drift

Mitigation strategy:

- freeze new drift first
- stabilize the shared replacements before the biggest migrations
- use product-local canonical packages where the UI is product-shaped
- delete deprecated code only after actual consumer count reaches zero
