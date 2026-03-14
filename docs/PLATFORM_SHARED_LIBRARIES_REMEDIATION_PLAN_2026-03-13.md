# Platform Shared Libraries Remediation Plan

Date: 2026-03-13

Scope: `platform/*`

## Objective

Reduce boundary drift in shared libraries, quarantine placeholder runtime code, move product-specific adapters out of `platform/*`, and raise confidence with targeted guardrails and tests.

## Top Risks

1. Shared Java modules depend directly on `products/*` and on Data-Cloud-specific backends.
2. Runtime code in `platform/java/ai-integration` and `platform/java/database` still contains mock, placeholder, or demo behavior.
3. TypeScript shared packages mix platform-generic, app-shell, and product-specific concerns.
4. Package metadata and dependency baselines are inconsistent across TypeScript libraries.
5. Test density is low in several large modules.

## Guardrails First

Before moving code, add enforcement so new drift stops immediately.

- Add a build check that fails if any `platform/java/*/build.gradle*` references `project(":products:`.
- Extend the existing ArchUnit guardrails to cover all platform packages that currently escape the rule set.
- Add a TypeScript package check that publishable packages must export `dist/*`, not `src/*`.
- Add a CI grep-based policy for `TODO`, `mock`, `placeholder`, `demo`, `old`, and `Stub` in `src/main` and `src/` with an allowlist file.
- Add a module inventory report to CI with source-file count, test-file count, and `products/*` dependency count.

## Module Decisions

Keep shared:

- `platform/agent-catalog`
- `platform/contracts`
- `platform/java/core` except product feature flags
- `platform/java/domain`
- `platform/java/http`
- `platform/java/runtime`
- `platform/java/config`
- `platform/java/event-cloud`
- `platform/java/security`
- `platform/java/governance`
- `platform/java/audit`
- `platform/java/workflow`
- `platform/java/plugin`
- `platform/java/ingestion`
- `platform/java/observability-clickhouse`
- `platform/typescript/utils`
- `platform/typescript/i18n`
- `platform/typescript/api`
- `platform/typescript/realtime`
- `platform/typescript/tokens`
- `platform/typescript/charts`
- `platform/typescript/accessibility-audit`

Keep shared, but restructure internally:

- `platform/java/core`
- `platform/java/database`
- `platform/java/observability`
- `platform/java/observability-http`
- `platform/java/agent-framework`
- `platform/java/agent-memory`
- `platform/java/agent-learning`
- `platform/java/agent-dispatch`
- `platform/java/agent-resilience`
- `platform/java/ai-integration`
- `platform/java/testing`
- `platform/typescript/theme`
- `platform/typescript/ui`
- `platform/typescript/canvas`
- `platform/typescript/platform-shell`

Move product-specific implementations out of `platform/*`:

- `platform/java/agent-registry` -> move Data-Cloud-backed implementation into `products/data-cloud/*`
- `platform/java/schema-registry` -> move Data-Cloud-backed implementation into `products/data-cloud/*`
- `platform/java/connectors` event-log adapters -> move Data-Cloud transports into `products/data-cloud/*`
- `platform/typescript/flow-canvas` -> move to AEP/Data-Cloud product libraries or rename as a product adapter package
- YAPPC-specific canvas theme, docs, tests, and adapters under `platform/typescript/canvas` -> move to `products/yappc/*`

Merge or remove:

- `platform/java/testing/test-utils` -> merge into `platform/java/testing` or replace with pure dependency-only wrapper with no duplicate classes

## Concrete Refactors

### 1. Java platform boundary cleanup

- Move Data-Cloud-backed classes out of `platform/java/agent-registry` and keep only neutral interfaces if still needed.
- Move `DataCloudSchemaRegistry` out of `platform/java/schema-registry` and keep a storage-neutral `SchemaRegistry` API.
- Split `platform/java/connectors` into:
  - generic connector contracts
  - Data-Cloud event-log client adapters
- Remove Data-Cloud-specific wording from SPIs in `agent-framework`.

### 2. Placeholder runtime quarantine

- Move mock AI services into `src/test`, `examples`, or `devsupport` packages.
- Replace placeholder production implementations with either:
  - real implementations, or
  - fail-fast stubs that clearly signal unsupported behavior.
- Do the same for cache warmer implementations in `platform/java/database`.

### 3. TypeScript package rationalization

- `platform/typescript/ui`
  - split into `ui-core` style primitives and a separate shell/integration layer
  - remove duplicate component implementations where only one should be canonical
  - remove commented-out exports once package boundaries are clean
- `platform/typescript/canvas`
  - keep only generic canvas core here
  - move YAPPC-specific theme, docs, actions, and tests into product space
  - finish or remove no-op API hooks such as grouping and callback subscriptions
- `platform/typescript/flow-canvas`
  - either rename to reflect AEP/Data-Cloud scope or rebuild it around pluggable node registries
- `platform/typescript/platform-shell`
  - keep shared only if multiple products truly use the same shell semantics
  - otherwise move it to a platform-app or product layer

### 4. Product feature flag cleanup

- Move product-specific feature enums and default enablement out of `platform/java/core`.
- Keep only truly platform-wide flags in shared core.
- Put product defaults in product modules or product bootstrap code.

## Test Backlog

Highest priority:

- `platform/java/agent-registry` because it currently has no tests
- `platform/java/schema-registry` beyond the single happy-path module test
- `platform/java/observability` because it is large and lightly tested
- `platform/java/agent-memory` because of high code volume versus test volume
- `platform/typescript/canvas` because it has one test for a very large surface area
- `platform/typescript/flow-canvas` because it currently has no tests
- `platform/typescript/platform-shell` because it currently has no tests
- `platform/typescript/ui` because the library is broad and has many TODOs
- `platform/typescript/api` because some tests do not actually assert the advertised behavior

Add tests for:

- package export correctness
- product-boundary guardrails
- fail-fast behavior for unsupported adapters
- module-level public API snapshots

## Recommended Execution Order

### Wave 1: Stop drift

- Add build and CI guardrails
- Fix TypeScript package export metadata
- Mark or quarantine runtime placeholders
- Freeze new `platform/* -> products/*` dependencies

### Wave 2: Remove conflicting infrastructure

- Merge `platform/java/testing/test-utils` into `platform/java/testing`
- Normalize React, router, and MUI dependency baselines across TypeScript packages
- Remove duplicate UI component implementations where one is canonical

### Wave 3: Move product adapters out

- Extract Data-Cloud-backed Java implementations
- Move `flow-canvas` out or rename it
- Move YAPPC-specific canvas code out of shared canvas

### Wave 4: Simplify large shared libraries

- Split `ui` into design-system and integration layers
- Split `ai-integration` into stable shared APIs versus experimental/product modules
- Move product feature defaults out of core

### Wave 5: Test backfill and deletion

- Add focused regression tests for moved modules
- Delete dead legacy build files and unused source trees
- Remove archived and duplicate artifacts that no longer serve the active build

## Immediate First Batch

If work starts now, the safest highest-leverage batch is:

1. Add Java boundary guardrails for all platform modules.
2. Fix `platform/typescript/canvas/package.json` exports.
3. Merge or neutralize `platform/java/testing/test-utils`.
4. Quarantine placeholder AI and cache-warming implementations.
5. Align TypeScript package baselines for React, router, and MUI.

## Success Criteria

- No `platform/java/*` build file depends on `products/*`.
- No shared runtime module returns mock or placeholder data in production code paths.
- Shared TypeScript packages expose built artifacts and share a consistent dependency baseline.
- Product-specific adapters live under product modules.
- Large shared modules have focused regression tests for their public APIs.
