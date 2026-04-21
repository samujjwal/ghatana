# AEP Folder Audit

Date: 2026-04-21  
Target root: `/Users/samujjwal/Development/ghatana/products/aep`  
Output file: `docs/aep-folder-audit-4-21.md`

## Executive Verdict

Current status: **Not production-ready as a fully coherent product tree**, despite strong signals in the canonical server path.

The strongest evidence is mixed:

- The contract path is healthy: `:products:aep:contracts:validateAepSpec` passes.
- The canonical server golden path is healthy: `:products:aep:server:test --tests com.ghatana.aep.server.AepGoldenPathSystemTest` passes all 13 tests.
- The TypeScript gateway is healthy: `pnpm --dir products/aep/gateway test` passes all 36 tests.
- The AEP UI is not healthy: `pnpm --dir products/aep/ui test --run` fails 45 tests across 6 files, with the dominant failure `TypeError: localStorage.clear is not a function`.
- The product tree still contains non-module or partially wired surfaces that violate repo module wiring rules: `products/aep/aep-observability` and `products/aep/aep-runtime` exist as source-only directories without module manifests.
- The orchestration path still contains an intentional no-op registry client stub, which means part of the runtime graph remains incomplete by design.

## Scope Reviewed

Top-level review covered 29 directories under `products/aep`, including:

- 17 Gradle-backed Java modules: `aep-agent-runtime`, `aep-analytics`, `aep-api`, `aep-central-runtime`, `aep-compliance`, `aep-engine`, `aep-event-cloud`, `aep-identity`, `aep-operator-contracts`, `aep-registry`, `aep-runtime-core`, `aep-scaling`, `aep-security`, `contracts`, `kernel-bridge`, `orchestrator`, `server`
- 2 TypeScript apps/packages: `gateway`, `ui`
- 2 source-only directories: `aep-observability`, `aep-runtime`
- Product support areas: `agent-catalog`, `docs`, `docs-generated`, `helm`, `k8s`, `services`, `test-scripts`, generated build output folders

## Method

This audit combined:

- direct filesystem inspection of the AEP tree
- direct reads of product docs and build files
- targeted source/test inspection in `server`, `aep-engine`, `orchestrator`, `contracts`, `ui`, and `gateway`
- focused executable validation using the product's own documented verification commands

This execution did **not** perform a full remediation sweep across the entire AEP product. No production code or test files were changed as part of this audit run. Because of that, the product does **not** satisfy the source prompt's “100% meaningful coverage” end state; the report explicitly identifies what blocks that state.

## Exclusions

Excluded from deep behavioral review, but still considered structurally:

- generated and transient output: `build/`, `bin/`, `dist/`, `node_modules/`, `playwright-report/`, `test-results/`
- generated documentation snapshots in `docs-generated/`

These were excluded because they are derivative artifacts, not primary implementation surfaces.

## Executable Validation

| Check                                                                                        | Result | Evidence                                | Notes                                                                             |
| -------------------------------------------------------------------------------------------- | ------ | --------------------------------------- | --------------------------------------------------------------------------------- |
| `./gradlew :products:aep:contracts:validateAepSpec`                                          | Pass   | OpenAPI contract validated successfully | Confirms the current `products/aep/contracts/openapi.yaml` is syntactically valid |
| `./gradlew :products:aep:server:test --tests com.ghatana.aep.server.AepGoldenPathSystemTest` | Pass   | 13 tests passed                         | Confirms the canonical HTTP server golden path is alive                           |
| `pnpm --dir products/aep/gateway test`                                                       | Pass   | 36 tests passed in 2 files              | Gateway test surface is currently stable                                          |
| `pnpm --dir products/aep/ui test --run`                                                      | Fail   | 45 tests failed across 6 files          | Primary failure: `TypeError: localStorage.clear is not a function`                |

## High-Severity Findings

### 1. Source-only directories violate module wiring rules

Severity: **Blocker**

Evidence:

- `products/aep/aep-observability` contains real source and tests under `src/`, but has no `build.gradle.kts`, no `package.json`, and no settings inclusion.
- `products/aep/aep-runtime` contains only `src/` and no build manifest. File search found no Java files under it.

Why this matters:

- Repo policy is explicit: a folder with only `src/` is not a valid module.
- `aep-observability` is especially risky because it looks like live code, but it is not actually wired into the build graph.
- This creates false confidence, dead code drift, and test invisibility.

Required action:

- Convert `aep-observability` into a real module or delete/migrate it.
- Delete `aep-runtime` if dead, or wire it as a real module if still intended.

### 2. Orchestrator still depends on a no-op registry stub

Severity: **Blocker**

Evidence:

- `products/aep/orchestrator/src/main/java/com/ghatana/aep/integration/registry/NoOpPipelineRegistryClient.java` is explicitly documented as a stub and returns empty collections.
- The class-level docs say pipeline definitions will later move to Data Cloud and the current implementation exists only to satisfy DI.

Why this matters:

- This is not production-complete behavior.
- Any runtime path expecting real pipeline registry integration can silently degrade into empty results.
- The product tree contains a core orchestration dependency that is intentionally non-functional.

Required action:

- Replace the stub with a real backed implementation or fail closed when registry-backed features are requested.

### 3. UI test environment is broken

Severity: **Blocker**

Evidence:

- `pnpm --dir products/aep/ui test --run` fails 45 tests.
- Affected test files include `src/__tests__/AepNewPages.test.tsx`, `src/__tests__/auth-flow.test.tsx`, `src/services/__tests__/audit-log.test.ts`, `src/components/nlp/__tests__/NLQInput.test.tsx`, `src/components/voice/__tests__/VoiceInput.test.tsx`, `src/components/privacy/__tests__/ConsentManager.test.tsx`.
- The dominant failure is `TypeError: localStorage.clear is not a function`.
- `products/aep/ui/src/test-setup.ts` defines `ResizeObserver` and `EventSource` shims, but no `localStorage` normalization.

Why this matters:

- The operator cockpit cannot be called production-ready if its test environment is broken at setup level.
- This is a broad infra/test harness defect, not a single feature regression.

Required action:

- Fix the shared Vitest/jsdom storage setup first.
- Rerun the full UI suite before any feature-level UI readiness claim.

### 4. Product documentation still drifts from the actual build graph

Severity: **High**

Evidence:

- `products/aep/OWNER.md` states `aep-runtime-core` is “not included in the Gradle build.”
- `settings.gradle.kts` includes `:products:aep:aep-runtime-core`.
- `products/aep/aep-runtime-core/build.gradle.kts` exists and declares a backward-compat facade module.

Why this matters:

- Ownership docs are currently unreliable for at least one module-status claim.
- This is exactly the kind of product-level drift that causes incorrect audit, migration, and dependency decisions.

Required action:

- Update `OWNER.md` to reflect the current truth: `aep-runtime-core` is present, wired, and intentionally deprecated rather than absent.

### 5. AEP still carries compatibility facades and excluded test surfaces

Severity: **High**

Evidence:

- `products/aep/aep-runtime-core/build.gradle.kts` marks the module as deprecated facade/backward compatibility.
- The same build excludes multiple test paths because referenced production classes are missing or require unavailable external services.

Why this matters:

- This is conscious technical debt, not a completed consolidation.
- The module helps compatibility, but it also proves that coverage and feature parity are incomplete.

Required action:

- Either complete the migration away from `aep-runtime-core` or formally scope the remaining backward-compat contract and finish the excluded-test backlog.

## Medium-Severity Findings

### 6. gRPC agent creation still relies on placeholder registrations

Severity: **Medium**

Evidence:

- `products/aep/server/src/main/java/com/ghatana/aep/server/grpc/AepGrpcServer.java` creates a `PlaceholderAgent` for submitted manifests.
- The placeholder reports healthy but throws on direct execution.

Why this matters:

- This is guarded better than a silent stub, but it is still a manifest-only registration path.
- The behavior is acceptable only if every caller understands that registry presence does not imply executability.

Required action:

- Keep the execution guard, but document the manifest-only semantics in the public runtime contract and surface them in operator views.

### 7. Production behavior remains tightly coupled to external backing systems

Severity: **Medium**

Evidence:

- `products/aep/README.md` states durable run history requires Data Cloud with `EventLogStore`.
- `products/aep/server/src/main/java/com/ghatana/aep/di/AepProductionModule.java` fails closed if `AEP_DB_URL` or `AEP_JWT_SECRET` are absent in production.
- `products/aep/server/src/main/java/com/ghatana/aep/di/AepCoreModule.java` falls back to null `DataSource` when no DB URL exists outside production.

Why this matters:

- The fail-closed production posture is correct.
- The non-production fallback posture increases risk of accidental “works locally but not in real runtime” gaps.

Required action:

- Preserve fail-closed production rules.
- Tighten non-production observability so fallback mode is impossible to mistake for durable mode.

### 8. Test story is uneven across product slices

Severity: **Medium**

Evidence:

- `server` has 61 Java test files.
- `aep-engine` has 75 Java test files.
- `ui` has 15 test files but currently fails at harness level.
- `gateway` has 2 test files and currently passes.

Why this matters:

- The strongest runtime slices are well-tested.
- The product as a whole is not uniformly trustworthy until the UI slice is brought back to green and source-only directories are resolved.

## Low-Severity Findings

### 9. Contract sync controls are strong and should be preserved

Evidence:

- `products/aep/contracts/build.gradle.kts` validates the spec with `validateAepSpec`.
- `products/aep/server/build.gradle.kts` syncs the runtime OpenAPI copy from the platform canonical spec and fails on drift.

Assessment:

- This is a strong product discipline point and should remain unchanged.

### 10. Gateway slice is currently the healthiest frontend-adjacent surface

Evidence:

- `products/aep/gateway/package.json` is minimal and coherent.
- The gateway suite passes 36 tests across JWT and integration coverage.

Assessment:

- The BFF layer currently has better verification health than the operator UI.

## Folder-Level Assessment

| Area                | Role                                   | State                      | Notes                                                                  |
| ------------------- | -------------------------------------- | -------------------------- | ---------------------------------------------------------------------- |
| `server`            | Canonical HTTP/gRPC runtime            | Healthy with caveats       | Golden path passes; still contains placeholder and fallback wiring     |
| `contracts`         | OpenAPI contract module                | Healthy                    | Validation passes; good drift controls                                 |
| `gateway`           | TS BFF/proxy                           | Healthy                    | Tests pass                                                             |
| `ui`                | Operator cockpit                       | Unhealthy                  | Test harness broken                                                    |
| `aep-engine`        | Core engine                            | Strong but debt remains    | Large test surface; compatibility debt still visible via facade module |
| `orchestrator`      | Orchestration and registry integration | Incomplete                 | Still relies on no-op pipeline registry client                         |
| `aep-runtime-core`  | Compatibility facade                   | Transitional debt          | Present in build despite docs saying otherwise                         |
| `aep-observability` | Observability code/tests               | Invalid module state       | Real code exists without module wiring                                 |
| `aep-runtime`       | Legacy or dead runtime folder          | Invalid module state       | Source-only, apparently empty                                          |
| `kernel-bridge`     | Kernel integration                     | Wired but under-documented | Included in settings, absent from high-level ownership table           |

## Coverage and Test-Hardening Assessment

Meaningful-coverage verdict: **not achieved**.

Reasons:

- The UI slice is currently failing before feature semantics can be trusted.
- `aep-runtime-core` still excludes multiple tests due to missing production classes or unavailable infra.
- `aep-observability` is invisible to the build graph, so any tests under it do not prove product health.
- The orchestrator contains a documented stub in a core integration boundary.

Tests added or updated during this audit: **0**  
Uncovered flows/features/use cases closed during this audit: **0**

Blocked coverage closure:

- UI storage/test-environment fix
- source-only directory cleanup or module wiring
- real pipeline registry integration in orchestrator
- completion or retirement of excluded `aep-runtime-core` test surfaces

## Recommended Remediation Sequence

1. Fix the shared `localStorage` test harness in `products/aep/ui` and rerun the full UI suite.
2. Convert `aep-observability` into a real module or migrate/delete it.
3. Delete or formalize `aep-runtime`.
4. Replace `NoOpPipelineRegistryClient` with a real implementation or make unsupported paths fail closed.
5. Reconcile `OWNER.md` with the actual build graph, especially `aep-runtime-core` and `kernel-bridge`.
6. Decide whether `aep-runtime-core` remains a supported compatibility module or should be removed after import migration.

## Final Assessment

The AEP product has a solid canonical runtime spine: contract validation passes, the server golden path passes, and the gateway passes. That is real progress and materially better than a broad “product is broken” verdict.

The blocker is product coherence. AEP still contains invalid module shapes, incompatible documentation, an orchestrator integration stub, and a broken UI test harness. Until those are resolved, the folder as audited should be treated as a **partially hardened product tree with a healthy core path, not a fully production-ready product package**.

## Completion Summary

- Audited root path: `/Users/samujjwal/Development/ghatana/products/aep`
- Output file path: `docs/aep-folder-audit-4-21.md`
- Libraries/folders reviewed: `29` top-level directories under AEP, with direct inspection of the primary runtime, contract, UI, gateway, orchestration, and support surfaces
- Major blockers count: `5`
- High-risk items count: `8`
- Tests added/updated: `0`
- Uncovered flows/features/use cases closed: `0`
