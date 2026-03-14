# Platform Shared Libraries Audit

Date: 2026-03-14

## Scope

Reviewed every module under `platform/*` for:

- over-engineering
- under-engineering
- missing features
- restructuring opportunities
- test gaps
- migration to product-specific libraries
- deprecated code paths
- TODO/mock/stub/compatibility leftovers

This audit also includes a consumer migration check for deprecated shared-library entry points.

## Current State

The platform cleanup is in progress, but the repository is in a mixed state:

- boundary hardening has started, but not all guardrails are complete
- deprecated modules still have active consumers
- several "new" modules still publish old package names or compatibility surfaces
- some deleted implementations still had stale tests/resources referencing them
- TypeScript workspace/package health is inconsistent enough to block clean installs in places

The highest-risk theme is not raw code quality. It is **partial migration**: modules were split, renamed, or quarantined, but many consumers still depend on old APIs and some replacements are not actually clean replacements yet.

## Safe Removals Applied In This Batch

- strengthened `gradle/platform-boundary-check.gradle` to inspect declared project dependencies instead of attempting to infer them from `resolve()`
- aligned `platform/typescript/canvas/flow-canvas` back to the active consumer package name `@ghatana/flow-canvas`
- removed stale connector SPI resources/tests that referenced deleted `GrpcEventLogStore` and `HttpEventLogStore` implementations
- removed the stale schema-registry test that still referenced deleted `DataCloudSchemaRegistry`
- replaced deprecated intra-platform `@ghatana/ui` usage in `platform/typescript/canvas`
- removed the circular `DataTable -> @ghatana/ui` re-export in `design-system`
- removed the invalid `@types/react-router-dom` dependency from the deprecated `@ghatana/ui` package
- neutralized `AgentRegistry` and `ai-integration` docs that still implied product-specific ownership or incorrect replacement namespaces

## Cross-Cutting Findings

### 1. Deprecated consumer migration is incomplete

- `@ghatana/ui` still has widespread product consumption across `software-org`, `tutorputor`, `dcmaar`, `yappc`, `data-cloud`, `aep`, `audio-video`, and `flashit`
- `@ghatana/ui/components/ErrorBoundary` and `@ghatana/ui/icons` still have direct consumers
- Java AI consumers still import `com.ghatana.ai.*` from shared-services and multiple product modules

### 2. Replacements are uneven

- `platform/java/ai-api` is described as a stable API module, but it still contains concrete implementations like `DefaultLLMGateway`, `PgVectorStore`, `OllamaCompletionService`, and `ToolAwareAnthropicCompletionService`
- `platform/java/ai-experimental` still uses the legacy `com.ghatana.ai.*` namespace, so the module split is real but the package split is not
- `platform/typescript/ui` is marked deprecated, but design-system parity is not complete enough to remove it safely today
- `platform/typescript/ui-integration` still contains mock/placeholder behavior
- `platform/typescript/canvas/flow-canvas` was nested deeply enough that PNPM was not picking it up as a workspace package until this batch

### 3. TypeScript package hygiene is blocking migration

- `platform/typescript/design-system` and `platform/typescript/ui` both have substantial pre-existing type-check failures
- `platform/typescript/canvas` still has local type-shape and React version drift issues even after the syntax fix in `TasksPanel`
- after fixing the invalid platform dependency, clean workspace install is now blocked by downstream consumer `products/data-cloud/ui` pinning non-existent `vitest-axe@^0.1.8`

### 4. Product-specific code is still present in nominally shared modules

- `platform/typescript/canvas/flow-canvas` still encodes AEP pipeline nodes and Data-Cloud/EventCloud topology nodes
- `platform/java/agent-registry`, `schema-registry`, and `connectors` have improved boundaries, but the historical product coupling is still visible in docs, tests, and migration assumptions

### 5. Test posture is uneven

- some modules are healthy and well-covered
- several important platform modules are now interface-only or mostly API-only with very thin or missing tests
- some previous tests were invalid because they exercised already-removed implementations

## Module Verdicts

### `platform/agent-catalog`

- Verdict: Keep shared
- Strengths: clear catalog intent, useful schema inventory
- Issues: needs automated schema validation and ownership checks in CI
- Gaps: no strong enforcement that catalog entries stay consistent with runtime modules

### `platform/contracts`

- Verdict: Keep shared
- Strengths: strongest shared-module candidate in the repo
- Issues: generator/layout duplication should be consolidated
- Gaps: deprecated proto fields exist for compatibility and should be documented, not silently accumulated

### `platform/java/core`

- Verdict: Keep shared
- Strengths: good foundational abstractions, architecture guardrails are valuable
- Issues: still carries product-language residue in docs/tests
- Gaps: guardrails need a TS-side equivalent and ongoing enforcement

### `platform/java/domain`

- Verdict: Keep shared
- Strengths: appropriately generic
- Issues: some event-cloud/domain language is still broader than necessary
- Gaps: none critical

### `platform/java/config`

- Verdict: Keep shared
- Strengths: right-sized and reusable
- Issues: moderate TODO/compatibility noise
- Gaps: more configuration validation tests would help

### `platform/java/runtime`

- Verdict: Keep shared
- Strengths: compact and generally well-scoped
- Issues: small module with modest test depth
- Gaps: lifecycle edge-case coverage

### `platform/java/yaml-template`

- Verdict: Keep shared
- Strengths: narrow and useful
- Issues: some legacy/TODO signal for module size
- Gaps: low test depth

### `platform/java/http`

- Verdict: Keep shared
- Strengths: useful shared transport layer
- Issues: high signal count suggests migration residue and compatibility baggage
- Gaps: more focused tests around deprecated paths and error mapping

### `platform/java/security`

- Verdict: Keep shared
- Strengths: broad and fairly well-tested
- Issues: module breadth could become a dumping ground
- Gaps: periodically audit for product-auth leakage

### `platform/java/governance`

- Verdict: Keep shared
- Strengths: correctly platform-oriented
- Issues: none major
- Gaps: modest but acceptable test surface

### `platform/java/audit`

- Verdict: Keep shared
- Strengths: small, focused, reusable
- Issues: none major
- Gaps: low but acceptable

### `platform/java/observability`

- Verdict: Keep shared
- Strengths: important platform capability
- Issues: under-tested relative to module size
- Gaps: instrumentation contract tests and integration-style verification

### `platform/java/observability-http`

- Verdict: Keep shared
- Strengths: scoped transport integration
- Issues: some migration debt remains
- Gaps: acceptable

### `platform/java/observability-clickhouse`

- Verdict: Keep shared
- Strengths: focused exporter-style module
- Issues: none major
- Gaps: acceptable

### `platform/java/event-cloud`

- Verdict: Keep shared
- Strengths: strong shared abstraction candidate
- Issues: naming may still read product-specific to some teams
- Gaps: no critical issues found

### `platform/java/ingestion`

- Verdict: Keep shared
- Strengths: small and specific
- Issues: limited breadth may indicate under-engineering if ingestion needs expand
- Gaps: acceptable for current size

### `platform/java/workflow`

- Verdict: Keep shared
- Strengths: useful cross-product abstraction layer
- Issues: should be monitored for product-specific workflow semantics
- Gaps: acceptable

### `platform/java/plugin`

- Verdict: Keep shared
- Strengths: strong platform concern
- Issues: AI plugin SPI still depends on legacy AI packages
- Gaps: migration to cleaned AI contracts is still pending

### `platform/java/database`

- Verdict: Keep shared core, quarantine demos/placeholders
- Strengths: shared DB concerns belong here
- Issues: cache warming code was previously placeholder/demo quality and has already been moved out
- Gaps: replacement strategy needs explicit ownership and tests

### `platform/java/connectors`

- Verdict: Split conceptually, keep shared core
- Strengths: generic connector primitives remain useful
- Issues: stale Data-Cloud transport resources/tests were still present after implementation removal
- Restructure: keep generic source/sink/connector contracts here; move product transport adapters to product modules or dedicated integration modules
- Gaps: event-log adapter coverage is now gone because the implementations are gone

### `platform/java/agent-framework`

- Verdict: Keep shared
- Strengths: large but coherent platform capability
- Issues: docs and builders still assume legacy/shared AI runtime surfaces
- Gaps: verify boundary purity as AI packages settle

### `platform/java/agent-memory`

- Verdict: Keep shared
- Strengths: solid platform candidate
- Issues: relatively large implementation surface for modest test depth
- Gaps: more persistence and failure-path testing

### `platform/java/agent-learning`

- Verdict: Keep shared, watch dependencies
- Strengths: reasonable scope
- Issues: depends on AI runtime packages that are still mid-migration
- Gaps: replacement AI contract stability

### `platform/java/agent-dispatch`

- Verdict: Keep shared
- Strengths: test-rich relative to size
- Issues: no major boundary concern found
- Gaps: none urgent

### `platform/java/agent-resilience`

- Verdict: Keep shared
- Strengths: small and well-targeted
- Issues: none major
- Gaps: acceptable

### `platform/java/agent-registry`

- Verdict: Keep as SPI only
- Strengths: boundary is cleaner now that product-specific implementations are gone
- Issues: interface docs still assumed Data-Cloud until this cleanup; module now has almost no implementation and no tests
- Restructure: product-backed registries should live in product modules
- Gaps: contract tests for SPI expectations

### `platform/java/schema-registry`

- Verdict: Keep shared API/model layer
- Strengths: generic schema concepts are correctly shared
- Issues: deleted product-specific implementation still had lingering test references
- Restructure: shared contracts here, backing stores elsewhere
- Gaps: no remaining concrete implementation tests after stale test removal

### `platform/java/testing`

- Verdict: Keep shared and canonical
- Strengths: good shared testing foundation
- Issues: must remain the only authoritative test-support package
- Gaps: none major

### `platform/java/ai-api`

- Verdict: Split further
- Strengths: useful place for shared AI contracts
- Issues: over-engineered for an "API" module because it still contains concrete gateways, vector-store implementations, and provider-specific behavior
- Restructure: keep interfaces/value objects in `ai-api`; move providers, default gateways, and storage implementations out
- Gaps: only thin regression coverage

### `platform/java/ai-experimental`

- Verdict: Keep experimental, not stable shared
- Strengths: correct place for unstable provider integrations
- Issues: still returns placeholder or mock behavior in OpenAI services, and still uses legacy package names
- Restructure: fail-fast or complete implementations; do not silently return fake embeddings/content
- Gaps: zero tests

### `platform/java/ai-integration`

- Verdict: Remove after migration
- Strengths: still useful as a compatibility waypoint
- Issues: deprecated compatibility layer still duplicates active API surface and keeps consumers on old module coordinates
- Restructure: remove once consumers depend directly on `ai-api` and `ai-experimental`
- Gaps: deprecation migration is incomplete

### `platform/typescript/utils`

- Verdict: Keep shared
- Strengths: right-sized utility package
- Issues: none major
- Gaps: low risk

### `platform/typescript/i18n`

- Verdict: Keep shared
- Strengths: clear shared concern
- Issues: modest test posture only
- Gaps: acceptable

### `platform/typescript/realtime`

- Verdict: Keep shared
- Strengths: reusable concern
- Issues: moderate TODO/compatibility noise for size
- Gaps: transport/reconnect edge cases

### `platform/typescript/api`

- Verdict: Keep shared
- Strengths: useful thin client layer
- Issues: tests are shallow and transport-focused
- Gaps: auth/error/backoff coverage

### `platform/typescript/accessibility-audit`

- Verdict: Keep shared, finish migration
- Strengths: substantial useful capability
- Issues: still exports deprecated legacy types and `legacyAudit()` compatibility surface
- Restructure: remove legacy report model after downstream migration
- Gaps: migration guide for consumers of legacy output types

### `platform/typescript/tokens`

- Verdict: Keep shared
- Strengths: strong shared design primitive
- Issues: still carries flat legacy aliases for backward compatibility
- Restructure: deprecate alias access on a schedule, not indefinitely
- Gaps: consumer migration tracking

### `platform/typescript/theme`

- Verdict: Keep shared
- Strengths: correct shared ownership
- Issues: small remaining TODO/placeholder areas in schema validation
- Gaps: theme-schema validation completeness

### `platform/typescript/charts`

- Verdict: Keep shared
- Strengths: broad component surface
- Issues: no major issues surfaced in this pass
- Gaps: verify package-level API governance because of size

### `platform/typescript/canvas`

- Verdict: Keep shared core, move product-specialized layers out
- Strengths: general canvas/runtime capability belongs in platform
- Issues: previously depended on deprecated `@ghatana/ui`; internal migration started in this batch
- Issues: package still has unresolved type errors in `TasksPanel` and React type drift in `GraphLayer`
- Restructure: keep renderer/editor primitives here; avoid product diagram vocab in core package
- Gaps: more consumer-focused tests

### `platform/typescript/canvas/flow-canvas`

- Verdict: Decide soon: genericize fully or move
- Strengths: actively used by multiple products
- Issues: still encodes Data-Cloud/EventCloud tiers and AEP pipeline/agent semantics, so it is not truly neutral
- Restructure: either make node types product-agnostic or move to product-specific shared UI packages
- Gaps: package naming drift was fixed in this batch, but conceptual ownership is still unresolved

### `platform/typescript/design-system`

- Verdict: Keep shared
- Strengths: proper destination for shared UI primitives and composites
- Issues: still contains old `@ghatana/ui` references in examples/comments; one circular `DataTable` export was fixed in this batch
- Gaps: parity checklist versus `@ghatana/ui` is still needed before full migration

### `platform/typescript/ui-integration`

- Verdict: Keep only if it becomes real
- Strengths: conceptually useful place for AI/collaboration/page-builder features
- Issues: under-engineered today; contains mock implementations and TODO-driven placeholders
- Restructure: split mature integration helpers from speculative demos
- Gaps: almost no real verification

### `platform/typescript/ui`

- Verdict: Remove after migration
- Strengths: currently helps avoid breaking consumers
- Issues: huge deprecated compatibility package, largely duplicating `design-system`
- Issues: compatibility package still depends on downstream parity work before removal
- Restructure: migrate consumers to `design-system` and `ui-integration`, then reduce `ui` to a short-lived shim or delete it
- Gaps: feature-parity map and consumer migration automation

### `platform/typescript/platform-shell`

- Verdict: Keep only if multiple products actually share it
- Strengths: plausible shared shell layer
- Issues: under-tested and not clearly proven as cross-product
- Gaps: no meaningful tests

## Deprecated Consumer Status

### `@ghatana/ui`

- Status: cannot be removed yet
- Why: many product packages still import it directly, including root exports plus subpaths like `components/ErrorBoundary` and `icons`
- Safe next step: migrate design-system-only consumers first, then track remaining integration-only usage

### `@ghatana/flow-canvas`

- Status: active, not removable
- Why: AEP and Data-Cloud currently consume it directly
- Safe next step: decide whether it remains a shared package or becomes product-scoped shared UI

### `:platform:java:ai-integration`

- Status: deprecated but not removable yet
- Why: consumers still rely on legacy `com.ghatana.ai.*` classes and the replacement modules still export those same package names
- Safe next step: finish module dependency migration first; do package renames only when there is a real namespace plan

## Highest-Priority Follow-Ups

1. Build a parity-based migration plan for `@ghatana/ui` to `@ghatana/design-system` and `@ghatana/ui-integration`.
2. Decide whether `canvas/flow-canvas` is truly shared or should move into product-owned UI libraries.
3. Split `ai-api` into real contracts versus concrete implementations.
4. Make `ai-experimental` fail fast instead of returning placeholder embeddings/content.
5. Add contract tests for interface-only modules now that stale implementation tests are gone.
6. Extend boundary enforcement to TypeScript package dependency rules, not just Gradle modules.

## Immediate Do-Not-Delete List

These are deprecated or transitional, but not yet safe to remove without additional migration work:

- `platform/typescript/ui`
- `platform/java/ai-integration`
- legacy `com.ghatana.ai.*` package names
- `platform/typescript/accessibility-audit` legacy output types
- `platform/typescript/tokens` flat legacy aliases

## Summary

The platform is moving in the right direction, but cleanup has outpaced migration discipline in a few places. The biggest remaining job is not finding more deprecated code. It is making sure the replacement surfaces are genuinely ready, then migrating consumers in a measured order so deprecated modules can actually disappear instead of lingering as permanent compatibility layers.
