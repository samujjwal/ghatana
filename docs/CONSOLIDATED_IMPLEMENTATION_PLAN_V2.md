# Consolidated Implementation Plan — Data Cloud + AEP Stabilization & Runtime Centralization

Date: 2026-03-20  
Repository: `ghatana`  
Source documents:
- `docs/V2_DATA_CLOUD_AEP_CONSOLIDATED_AUDIT_AND_AEP_RUNTIME_PLAN_2026-03-20.md`
- `docs/V2_DATA_CLOUD_AEP_CONSOLIDATED_AUDIT_AND_RUNTIME_CENTRALIZATION_PLAN_2026-03-20.md`

Status: **Active — Execution Ready**

---

## Overview

This plan consolidates all remediation, stabilization, and centralization tasks from the two V2 audit documents into a single, non-redundant, sequenced execution plan. Tasks are organized into phases with clear entry/exit criteria.

**Strategic decisions:**
- **V2 Release:** No-Go (AEP has P0 blockers; Data Cloud has fixable P1 blockers)
- **Data Cloud Staging Push:** Conditional Go after immediate fixes
- **AEP Runtime Centralization Program:** Go with phased execution and guardrails

---

## Phase: Immediate Fixes (Release Blockers)

These must be completed before any release or migration work begins.

### IMM-1: Fix AEP SSE Authentication Bypass [P0 Security]

**Problem:** `AepAuthFilter.java:47` exempts `/events/stream` from auth. `SseController.java:51` accepts any caller without identity or tenant validation. `AepAuthFilter.java:80` disables auth entirely when `AEP_JWT_SECRET` is absent.

**Files:**
- `products/aep/platform-security/src/main/java/com/ghatana/aep/security/AepAuthFilter.java`
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/SseController.java`

**Actions:**
1. Remove `/events/stream` from the public-path exemption list in `AepAuthFilter`
2. Add authentication enforcement to the SSE endpoint in `SseController`
3. Add tenant membership validation before subscribing a caller to tenant events
4. Remove the fallback that disables auth when `AEP_JWT_SECRET` is absent (fail closed instead)
5. Add authz and tenant-isolation regression tests

**Exit signal:** SSE endpoint requires valid auth token and tenant membership; tests prove cross-tenant subscription is rejected.

---

### IMM-2: Fix AEP platform-registry Compile Dependencies [P0 Build]

**Problem:** `platform-registry/build.gradle.kts` does not declare `platform:java:security`, `platform:java:connectors`, Hikari, or Jedis even though sources import `SessionManager`, `SessionFilter`, `ConnectorRegistry`, `FileConnector`, and `KafkaConnector`.

**Files:**
- `products/aep/platform-registry/build.gradle.kts`

**Actions:**
1. Add missing dependency declarations: `platform:java:security`, `platform:java:connectors`, Hikari, Jedis
2. Verify `compileJava` passes for `platform-registry`

**Exit signal:** `./gradlew :products:aep:platform-registry:compileJava` succeeds.

---

### IMM-3: Fix AEP platform-core Test Dependencies [P0 Build]

**Problem:** `platform-core/build.gradle.kts` does not declare AssertJ, Mockito JUnit, `platform:java:testing`, or JMH test dependencies even though tests import `EventloopTestBase`, AssertJ, and JMH types.

**Files:**
- `products/aep/platform-core/build.gradle.kts`

**Actions:**
1. Add missing test dependency declarations: AssertJ, Mockito JUnit, `platform:java:testing`, JMH
2. Verify `compileTestJava` passes for `platform-core`

**Exit signal:** `./gradlew :products:aep:platform-core:compileTestJava` succeeds; `./gradlew :products:aep:platform-core:test` passes.

---

### IMM-4: Implement AEP Pipeline Create/Update [P1 Functionality]

**Problem:** `PipelineController.java:117` — create/update are placeholders returning stub responses without parsing, validating, or persisting pipeline definitions.

**Files:**
- `products/aep/server/src/main/java/com/ghatana/aep/server/http/controllers/PipelineController.java`

**Actions:**
1. Implement real pipeline definition parsing and validation
2. Persist pipeline definitions durably
3. Return correct response shapes
4. Add endpoint tests for create/update flows

**Exit signal:** Pipeline create/update endpoints parse, validate, persist, and return real responses; tests cover success and validation-failure cases.

---

### IMM-5: Fix Data Cloud UI Build Script [P1 Delivery]

**Problem:** `products/data-cloud/ui/package.json:8` references a missing `scripts/ensure-lib-built.js` prebuild helper, blocking `npm run build`.

**Files:**
- `products/data-cloud/ui/package.json`

**Actions:**
1. Either restore the missing `scripts/ensure-lib-built.js` helper, or
2. Remove the prebuild dependency if the script is no longer needed
3. Verify `npm run build` passes in `products/data-cloud/ui`

**Exit signal:** `npm run build` in `products/data-cloud/ui` succeeds cleanly.

---

### IMM-6: Fix Malformed Workspace JSON [P1 Delivery]

**Problem:** `products/yappc/frontend/libs/component-traceability/package.json:26` contains malformed JSON, blocking all root-level `pnpm --filter` commands.

**Files:**
- `products/yappc/frontend/libs/component-traceability/package.json`

**Actions:**
1. Fix the malformed JSON at line 26
2. Verify `pnpm --filter` commands pass from repo root

**Exit signal:** Root `pnpm` workspace commands succeed.

---

### IMM-7: Fix AEP UI Codegen Path [P1 Contract]

**Problem:** `products/aep/ui/package.json:17` references `../../contracts/openapi.yaml` but the correct relative path is `../contracts/openapi.yaml`.

**Files:**
- `products/aep/ui/package.json`

**Actions:**
1. Fix the OpenAPI reference path to `../contracts/openapi.yaml`
2. Automate client generation from the canonical contract

**Exit signal:** `generate:api` script resolves the correct OpenAPI file.

---

### IMM-8: Fix Data Cloud Analytics Contract Drift [P2 Backend]

**Problem:** `data-cloud/launcher:test` fails 1 of 170 tests — analytics response shape mismatch between `AnalyticsHandler.java` output and the test expectation.

**Files:**
- `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AnalyticsHandler.java`
- Corresponding test file

**Actions:**
1. Align response shape between handler and test contract (decide which is canonical)
2. Add regression coverage for the analytics plan payload shape

**Exit signal:** `./gradlew :products:data-cloud:launcher:test` passes all 170 tests.

---

### IMM-9: Consolidate AEP Frontend API Clients [P1 Code Quality]

**Problem:** Multiple API client patterns exist: `api-client.ts`, `pipeline.api.ts`, `aep.api.ts` — with inconsistent base URL and auth behavior.

**Files:**
- `products/aep/ui/src/lib/api-client.ts`
- `products/aep/ui/src/api/pipeline.api.ts`
- `products/aep/ui/src/api/aep.api.ts`

**Actions:**
1. Unify to one canonical API client with consistent base URL and auth handling
2. Remove or refactor duplicate API layers

**Exit signal:** AEP UI uses a single API client strategy; all API calls go through one authenticated client.

---

### IMM-10: Eliminate Duplicate AEP OpenAPI Spec [P1 Contract]

**Problem:** `products/aep/contracts/openapi.yaml` and `products/aep/server/src/main/resources/openapi.yaml` are manually maintained copies with drift risk.

**Files:**
- `products/aep/contracts/openapi.yaml`
- `products/aep/server/src/main/resources/openapi.yaml`

**Actions:**
1. Generate the runtime-served spec from the canonical contract during build, or
2. Symlink the runtime resource to the canonical contract
3. Add CI check that the two never diverge

**Exit signal:** Only one manually-maintained OpenAPI spec exists; runtime spec is derived.

---

## Phase 0: Governance Freeze

**Goal:** Stop new runtime duplication while centralization proceeds.

**Entry criteria:** Immediate fixes IMM-1 through IMM-3 are complete (build is green).

### P0-1: Announce Governance Freeze

**Actions:**
1. Announce freeze on new product-local `*AgentRegistry`, `*RegistryHandler`, `*AgentCatalog`, `*CatalogLoader` classes
2. Announce freeze on new direct filesystem scans for agent definitions outside approved AEP loader

### P0-2: Add CI Architecture Rules

**Actions:**
1. Add grep/arch rules that fail CI if non-AEP products introduce:
   - `*AgentRegistry` classes
   - `*RegistryHandler` classes
   - `*AgentCatalog` classes
   - `*CatalogLoader` classes
   - Direct filesystem scans for agent definitions
2. Add root JSON validation for all `package.json` files
3. Add missing-script validation for referenced build helpers
4. Add OpenAPI sync check (AEP canonical vs runtime-served)

### P0-3: Document Existing Exceptions

**Actions:**
1. Record all known violations as migration backlog:
   - `YAPPCAgentRegistry.java`
   - `YappcAgentCatalog.java`
   - `products/data-cloud/agent-registry/...`
   - Reflective/hybrid runtime bootstraps in `AgenticDataProcessor.java`
2. Map each exception to an owner and migration phase

**Exit signal for Phase 0:** CI blocks new duplication; existing violations documented with owners.

---

## Phase 1: Extract Minimal Shared API/SPI Contract

**Goal:** Shrink shared platform to product-agnostic API and SPI only.

**Entry criteria:** Phase 0 complete; AEP compile/test graph is green.

### P1-1: Inventory Shared Agent Modules

**Actions:**
1. Inventory all classes in:
   - `platform/java/agent-framework`
   - `platform/java/agent-dispatch`
   - `platform/java/agent-memory`
   - `platform/java/agent-learning`
   - `platform/java/agent-registry`
2. Classify each class as: **keep in shared API** / **keep as shared SPI** / **move to AEP runtime** / **delete/archive**
3. Produce class-by-class move inventory spreadsheet

### P1-2: Create Target Shared Modules

**Actions:**
1. Create `platform:java:agent-api` module (contracts: `TypedAgent`, `AgentContext`, `AgentResult`, `AgentConfig`, `AgentDescriptor`, `AgentType`)
2. Create `platform:java:agent-spi` module (provider interfaces: `AgentRegistry` SPI, `AgentLogicProvider`)
3. Move only contract/SPI classes first
4. Update build files and dependency declarations

### P1-3: Update Product Imports

**Actions:**
1. Replace product imports to compile against API/SPI surface where possible
2. Create must-fix list for each downstream product
3. Update dependency graph documentation

**Exit signal for Phase 1:** Shared platform no longer contains execution, catalog discovery, or registry endpoint logic; products still compile.

---

## Phase 2: Build Central AEP Catalog Service

**Goal:** One AEP-owned path to discover and validate all product agent definitions.

**Entry criteria:** Phase 1 complete (API/SPI extraction done).

### P2-1: Implement Multi-Root Catalog Loader

**Actions:**
1. Create central catalog loader in AEP that scans configured product roots
2. Define `agent-catalog.yaml` as the manifest entrypoint
3. Support temporary compatibility for legacy layouts:
   - `products/aep/agent-catalog`
   - `products/data-cloud/agent-catalog`
   - `products/yappc/config/agents`

### P2-2: Build Merged Catalog Index

**Actions:**
1. Implement merged index building:
   - Deduplicate by `agentId`
   - Enforce product ownership
   - Validate referenced definitions, instances, assets, and schemas
   - Validate `implementationRef` and provider resolution
2. Produce validation report output
3. Make AEP server/runtime consume only the central loader

### P2-3: Add Catalog Conformance Tests

**Actions:**
1. Add multi-root conformance tests for legacy and canonical layouts
2. Add cross-product catalog validation tests

**Exit signal for Phase 2:** AEP builds one merged catalog from all product roots; product-local catalog providers are no longer needed.

---

## Phase 3: Introduce `AgentLogicProvider` SPI

**Goal:** Products contribute logic without owning runtime.

**Entry criteria:** Phase 2 complete (central catalog works).

### P3-1: Define Provider Interface

**Actions:**
1. Define `AgentLogicProvider` SPI:
   - Provider ID
   - Supported `implementationRef`s or agent IDs
   - Factory method creating concrete agent logic
2. Support registration via Java SPI or explicit DI

### P3-2: Build AEP Materializer/Factory

**Actions:**
1. Build AEP factory that reads YAML → resolves provider → loads assets → creates instance
2. Add provider validation rules (every `implementationRef` resolves deterministically)

### P3-3: Create Sample Providers

**Actions:**
1. Implement sample `AgentLogicProvider` in YAPPC
2. Implement sample `AgentLogicProvider` in one additional product
3. Remove product-local factory patterns that bypass central flow

**Exit signal for Phase 3:** YAML + provider is sufficient to instantiate a product agent; no product-local registry bootstrap needed.

---

## Phase 4: Centralize Registry APIs & Runtime Operations

**Goal:** AEP becomes the only registry and runtime admin surface.

**Entry criteria:** Phase 3 complete (provider SPI works).

### P4-1: Implement AEP Registry Endpoints

**Actions:**
1. Implement registry read/write/query endpoints only in AEP server
2. Move/wrap existing shared registry behavior into AEP-owned services
3. Standardize lifecycle operations: register, materialize, initialize, health check, execute, shutdown

### P4-2: Retire Product-Local Registry Surfaces

**Actions:**
1. Ensure all admin/discovery UIs call AEP, not product-local registries
2. Replace `AgentRegistryHttpServer.java` in `aep/platform-agent` with AEP server-owned endpoints
3. Produce retirement list for product-local registry/controller endpoints

### P4-3: Publish Migration Notes

**Actions:**
1. Create migration guide for downstream consumers
2. Define per-product cutover path

**Exit signal for Phase 4:** No product exposes registry APIs directly; AEP is the single runtime admin surface.

---

## Phase 5: Product Migration Wave 1 — YAPPC

**Goal:** Remove the largest current duplication first.

**Entry criteria:** Phase 4 complete (AEP is the sole runtime).

### P5-1: Migrate YAPPC Catalog

**Actions:**
1. Replace `registry.yaml`-centric loading with `agent-catalog.yaml` + split catalogs
2. Load YAPPC definitions through central AEP loader
3. Normalize YAPPC layout to canonical product structure

### P5-2: Remove YAPPC Local Runtime Ownership

**Actions:**
1. Remove or deprecate `YAPPCAgentRegistry.java`
2. Remove or deprecate `YappcAgentCatalog.java`
3. Remove YAPPC-local catalog bootstrap logic
4. Convert YAPPC logic classes to `AgentLogicProvider`

### P5-3: Rewire YAPPC Integration

**Actions:**
1. Rewire runtime calls to AEP registry/runtime APIs
2. Convert local phase/step indexing to derived views from AEP-backed state
3. Add YAPPC migration acceptance tests

**Exit signal for Phase 5:** YAPPC runs with zero product-local registry ownership; contributes only definitions + logic providers.

---

## Phase 6: Product Migration Wave 2

**Goal:** Move all remaining downstream consumers to the canonical model.

**Entry criteria:** Phase 5 complete (YAPPC migration validated).

### P6-1: Data Cloud

- Keep persistence backend role only if needed
- Move agent catalog loading to AEP
- Replace reflective AEP detection in `AgenticDataProcessor.java` with explicit integration contract
- Decide one role: storage backend or AEP runtime client

### P6-2: Software Org

- Migrate config-driven agents to canonical product layout
- Add provider module if executable logic exists
- Remove local runtime ownership assumptions

### P6-3: Virtual Org

- Remove AEP/platform hybrid ownership patterns
- Keep only definitions and providers
- Close operator/pipeline boundary TODOs

### P6-4: App Platform

- Replace direct AEP-internal reuse in SDK and DLQ modules with approved public API/SPI
- Ensure reuse flows through AEP runtime APIs

### P6-5: Finance

- Classify agents as provider-backed logic over shared API/SPI
- Remove hidden assumption Finance needs separate agent runtime

### P6-6: Tutorputor

- Migrate agent logic to provider model
- Align content-agent assets with canonical layout

**Exit signal for Phase 6:** All migrated products follow the same contribution model; no product-local registry implementation remains.

---

## Phase 7: Legacy Cleanup & Long-Term Improvements

**Goal:** Finish ownership move and simplify topology.

### P7-1: Remove Legacy Shared Runtime Modules

- Deprecate remaining runtime-heavy shared modules
- Move remaining implementation classes to AEP
- Delete/archive stale adapters and helpers
- Run final cross-product conformance audit

### P7-2: Structural Improvements

- Split `data-cloud/platform` into smaller bounded modules (storage/event, entity/config, memory/learning, analytics/search, launcher/api)
- Refactor `DataCloudHttpServer.java` (2033 LOC) toward transport-only composition
- Refactor `AepHttpServer.java` (940 LOC) toward transport-only composition
- Rename `products/aep/platform` to `products/aep/resources` or `products/aep/platform-bundle`
- Decide on `aep/gateway` role — keep as first-class or remove
- Reduce `AIAgentOrchestrationManagerImpl.java` (783 LOC) hotspot

### P7-3: Frontend Improvements

- Reduce Data Cloud UI `mock-data.ts` (1062 LOC) to production-safe patterns
- Fix Data Cloud UI MSW unhandled-request warnings
- Fix Data Cloud UI `act(...)` async test warnings
- Improve AEP UI chunk splitting (554 kB + 373 kB chunks)
- Increase `@ghatana/design-system` and `@ghatana/theme` adoption in AEP UI

### P7-4: Test Coverage Expansion

- Add smoke/unit coverage to all currently untested AEP backend modules:
  - `aep/platform-analytics` (0 tests)
  - `aep/platform-connectors` (0 tests)
  - `aep/platform-api` (0 tests)
  - `aep/platform-scaling` (0 tests)
  - `aep/gateway` (0 tests)
- Add provider-resolution tests for `implementationRef`
- Add per-product migration acceptance tests

### P7-5: CI / Lint Hardening

- Require targeted Gradle product compile/test tasks to pass before merge
- Require local package `build` and `test` for touched UI packages
- Add dependency rules preventing new product code from depending on runtime-heavy shared modules
- Establish architecture score gates and runtime conformance gates in CI

**Exit signal for Phase 7:** Shared platform contains only contracts/SPI; AEP owns runtime end-to-end; all in-scope products follow the canonical contribution model.

---

## Canonical Product Asset Layout (Target)

```text
products/<product>/agents/
├── agent-catalog.yaml          # Entrypoint manifest
├── definitions/                # Agent YAML definitions
├── instances/                  # Tenant-scoped instance configs
├── prompts/                    # Prompt templates
├── templates/                  # Other templates
├── policies/                   # Governance policies
├── event-schemas/              # Input/output JSON schemas
└── eval/                       # Evaluation assets
```

## Canonical Agent Definition Schema

```yaml
apiVersion: ghatana.agent/v1
kind: AgentDefinition
metadata:
  id: agent.<product>.<name>
  product: <product>
spec:
  implementationRef: <provider-id>:agent.<product>.<name>
  runtimePolicy:
    timeout: PT5M
    retries: 2
  inputSchemaRef: event-schemas/<schema>.json#/<path>
  outputSchemaRef: event-schemas/<schema>.json#/<path>
  assetRefs:
    - prompts/<name>.md
    - templates/<name>/
```

---

## Summary: Phase Roadmap

| Phase | Focus | Entry Criteria | Exit Signal |
| --- | --- | --- | --- |
| **Immediate** | Fix release blockers | — | AEP compiles, SSE secured, Data Cloud UI builds |
| **0** | Governance freeze | IMM-1 to IMM-3 done | CI blocks new duplication |
| **1** | Shared API/SPI extraction | Phase 0 done | Shared layer owns no runtime behavior |
| **2** | AEP central catalog loader | Phase 1 done | One merged catalog across products |
| **3** | `AgentLogicProvider` SPI | Phase 2 done | YAML + provider = agent instance |
| **4** | AEP registry/runtime centralization | Phase 3 done | AEP is only admin/runtime surface |
| **5** | YAPPC migration (Wave 1) | Phase 4 done | YAPPC local registry retired |
| **6** | Wave 2 migrations (6 products) | Phase 5 done | All products normalized |
| **7** | Legacy cleanup + hardening | Phase 6 done | Clean topology, full coverage |
