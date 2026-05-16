# Artifact Compiler/Decompiler Production-Readiness Audit and Implementation Plan

Repository: `samujjwal/ghatana`  
Target commit: `0a4b71042b7751969195645b231005b3671f4351`

## Implementation Progress

Last updated: 2026-05-15

Completed in current workspace state:

- `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`
	- Added structured `skippedArtifacts` emission for `.gitignore`, `excludeGlobs`, `maxFileSize`, `symlink`, and `readError` paths.
	- Added focused regression coverage in `src/inventory/scanner.skipped-artifacts.test.ts`.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/model/types.ts`
	- Kept `SemanticProductModel.id` as UUID and added `sourceModelRef` for deterministic source identity.
	- Added focused schema regression coverage in `src/model/__tests__/semantic-product-model-schema.test.ts`.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/pipeline.ts`
	- Low-confidence model elements now become source-faithful residuals with raw source, checksum, raw fragment ref, review reason, risk, and full-file span metadata.
	- Exposed assembled `residualIslands` on pipeline results and added focused coverage in `src/synthesis/__tests__/pipeline.low-confidence-residual.test.ts`.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/residual-preserver.ts`
	- Non-verbatim regeneration strategies are now blocked for production use instead of emitting warning comments, TODO stubs, or throwing placeholders.
	- Updated compile-back regression expectations accordingly.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/react-patch-emitter.ts`
	- Removed placeholder sync diffs; real patch generation stays on the async source-aware path.
	- Added focused regression coverage in `src/compile-back/react-patch-emitter.test.ts`.
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`
	- Ingest, analyze, and merge requests are now normalized to the authenticated principal tenant before entering the service layer.
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactRequestScope.java`
	- Added an explicit service-layer scope object so ingest/analyze/merge no longer rely on body DTO scope for tenant/product routing.
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphService.java`
	- Ingest, analyze, and merge now accept explicit `ArtifactRequestScope` alongside the request payload.
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`
	- Service cache, persistence, and versioning flows now read tenant/product scope from `ArtifactRequestScope` instead of the request DTO.
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`
	- Node persistence now writes server-provided tenant/product scope instead of trusting node payload scope fields.
	- Repository logs when incoming node payload scope is ignored.
- `products/yappc/frontend/apps/api/src/routes/source-imports.ts`
	- Governed source-import route now accepts repository-backed `github` imports, routes repo sources through `yappc-artifact-compiler` provider registry + synthesis pipeline, persists snapshot/summary/residual metadata onto jobs, and awaits all rejection/failure job updates before responding.
	- Added focused route regressions in `src/routes/__tests__/source-imports.test.ts` for repo import success and awaited unsupported-type rejection state.
- `products/yappc/frontend/web/src/services/compiler/ImportSourceWorkflow.ts`
	- Removed the duplicate frontend residual shape in favor of the canonical compiler `ResidualIsland` type.
	- Normalized governed server residual payloads into the compiler schema and fixed repo confidence extraction to read canonical pipeline `model.elements` instead of a non-existent `semanticModel` field.
	- Added focused workflow regression coverage in `src/services/compiler/__tests__/ImportSourceWorkflow.test.ts`.
- `products/yappc/frontend/web/src/services/compiler/__tests__/import-source-flow.spec.ts`
	- Added a governed async import flow integration test that exercises server start + job polling, verifies terminal review status, and asserts canonical residual normalization under scoped import headers.
- `products/yappc/frontend/web/src/services/compiler/__tests__/patch-review-flow.spec.ts`
	- Added a frontend-facing patch review integration test that exercises `buildChangePlan`, `PatchCoordinator.buildPatchSet`, `validateChangePlan`, `dryRunPatchSet`, and `buildReviewBundle` against a real temporary TSX source file.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/types.ts`
	- Provider contracts now accept typed `SourceLocator` inputs through the registry/providers, support governed `SourceScopeContext` on options, and reject raw token/username/password credentials for browser-scoped resolution.
	- Exported typed locator helpers through the source-provider barrel and added focused contract regressions in `src/__tests__/source-providers.test.ts`.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/local-folder-provider.ts`
	- Local folder snapshots now detect dirty git worktrees, fall back to content-hash pinning when the worktree is dirty or git metadata is unavailable, and emit structured diagnostics when manual review is required.
	- Added focused provider regressions in `src/__tests__/source-providers.test.ts` for dirty-worktree review diagnostics and deterministic non-git content pinning.
- `products/yappc/frontend/apps/api/src/services/job-repository.ts`
	- Added a database-backed `DatabaseJobRepository` using the existing Prisma raw-SQL pattern for auxiliary tables, with lazy table/index creation and JSONB job payload storage.
	- Kept `FileJobRepository` as the default for test/dev and added explicit backend selection via `YAPPC_SOURCE_IMPORT_JOB_BACKEND`/`SOURCE_IMPORT_JOB_BACKEND` or production `DATABASE_URL` detection.
	- Added focused durability/selector coverage in `src/services/__tests__/job-repository.test.ts`.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/types.ts`
	- Added first-class patch metadata for `ranges`, `baseChecksum`, `targetChecksum`, and `validationStatus`, and aligned emitted `TextPatch` objects with the structured metadata contract.
	- `buildChangePlan(...)` now emits explicit `add-prop`, `remove-prop`, and `update-prop-type` operations for component prop diffs instead of collapsing every prop change into a generic update.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/react-patch-emitter.ts`
	- Async React patch emission now writes structured `ranges`, `baseChecksum`, and `targetChecksum` metadata directly onto emitted patches instead of embedding range JSON into diff comments.
	- Added focused emitter regressions in `src/compile-back/react-patch-emitter.test.ts` for structured range and checksum emission.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/patch-coordinator.ts`
	- Added injectable logger and validator hooks, conflict-aware review metadata, validation-status assignment on generated review patches, structured range/checksum pass-through into review bundles, and dry-run workspace validation for missing targets, diff-header mismatches, and stale base checksums.
	- Replaced default console logger usage with structured `stderr` output to keep library logging explicit and injectable in production flows.
	- Added focused coordinator regressions in `src/compile-back/patch-coordinator.test.ts`.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/symbol-index.ts`
	- Split symbol indexing and path normalization into a dedicated module.
	- Added configurable alias/workspace package normalization helpers used by the resolver.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/symbol-resolver.ts`
	- Resolver now consumes the dedicated symbol-index module and accepts configurable resolver options (`pathAliases`, `workspacePackagePrefixes`) for monorepo/tsconfig-style import resolution.
	- Hardened unresolved-vs-cross-repo classification to avoid misclassifying normalized internal workspace paths.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/types.ts`
	- Added first-class graph query pagination/filter controls (`cursor`, `includeUnresolvedEdges`, `unresolvedStatuses`).
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`
	- Removed null/TODO ingest metadata placeholders by extracting `snapshotId`, `versionId`, and `contentChecksum` from incoming node/edge metadata for repository/version persistence.
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`
	- Fixed cursor pagination SQL by selecting `updated_at` in paginated node/edge queries before building `nextCursor`.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/__tests__/symbol-resolver.test.ts`
	- Added alias + workspace-package regression coverage for resolver options.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/__tests__/graph-query-schema.test.ts`
	- Added schema regression coverage for graph query cursor and unresolved-edge filters.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.test.ts`
	- Replaced current-workspace smoke assertions with deterministic fixture-backed scanner tests.
	- Added fixture-backed monorepo package-boundary coverage using scanner `packageBoundaries` output.
- `products/yappc/frontend/libs/yappc-artifact-compiler/test/fixtures/small-react-app/*`
	- Added deterministic React fixture inputs for scanner regression coverage.
- `products/yappc/frontend/libs/yappc-artifact-compiler/test/fixtures/pnpm-monorepo/*`
	- Added deterministic pnpm workspace fixture inputs for package-boundary detection coverage.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/github-provider.ts`
	- GitHub snapshots now emit structured diagnostics for oversized skipped files, metadata-only fallback entries after blob materialization failures, and max-file cutoffs.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/gitlab-provider.ts`
	- GitLab snapshots now emit structured diagnostics for oversized skipped files, metadata-only fallback entries after materialization failures, and max-file cutoffs.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/__tests__/e2e-patch-review.test.ts`
	- Replaced object-literal “e2e” assertions with a real compile-back review flow that exercises `buildChangePlan`, `PatchCoordinator.buildPatchSet`, `validateChangePlan`, `dryRunPatchSet`, `buildReviewBundle`, and rollback metadata creation against a real temporary source file.
- `products/yappc/frontend/libs/yappc-artifact-compiler/src/__tests__/e2e-import-job.test.ts`
	- Replaced object-literal “e2e” assertions with a real source acquisition + synthesis flow that exercises `LocalFolderProvider.resolve(...)` and `SynthesisPipeline.runFromSnapshot(...)` through a dirty-worktree snapshot.

Already satisfied in current source, not reimplemented:

- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`
	- The previously-audited malformed `analyzeGraph` blocking-executor call is already fixed in the workspace snapshot. The current implementation uses `Promise.ofBlocking(blockingExecutor, ...)` correctly.

Remaining high-priority work from this plan:

- No remaining high-priority items are open in the current workspace slice; broader end-to-end product/UI validation remains a future expansion area rather than an outstanding implementation defect in this tranche.

Validation executed in this workspace:

- `pnpm vitest run src/inventory/scanner.skipped-artifacts.test.ts src/model/__tests__/semantic-product-model-schema.test.ts src/__tests__/compile-back.test.ts src/synthesis/__tests__/pipeline.low-confidence-residual.test.ts src/compile-back/react-patch-emitter.test.ts`
	- Result: 5 test files passed, 20 tests passed.
- `./gradlew :products:yappc:core:yappc-services:compileJava --no-daemon`
	- Result: BUILD SUCCESSFUL.
- `./gradlew :products:yappc:core:yappc-services:compileJava --no-daemon` (after introducing `ArtifactRequestScope`)
	- Result: BUILD SUCCESSFUL.
- `cd products/yappc/frontend/apps/api && pnpm vitest run src/routes/__tests__/source-imports.test.ts`
	- Result: 1 test file passed, 6 tests passed.
- `cd products/yappc/frontend/web && pnpm vitest run src/services/compiler/__tests__/ImportSourceWorkflow.test.ts`
	- Result: 1 test file passed, 15 tests passed.
- `cd products/yappc/frontend/web && pnpm vitest run src/services/compiler/__tests__/import-source-flow.spec.ts src/services/compiler/__tests__/patch-review-flow.spec.ts`
	- Result: 2 test files passed, 2 tests passed.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/__tests__/source-providers.test.ts`
	- Result: 1 test file passed, 21 tests passed.
- `cd products/yappc/frontend/apps/api && pnpm vitest run src/services/__tests__/job-repository.test.ts`
	- Result: 1 test file passed, 2 tests passed.
- `cd products/yappc/frontend/apps/api && pnpm vitest run src/routes/__tests__/source-imports.test.ts src/services/__tests__/job-repository.test.ts`
	- Result: 2 test files passed, 8 tests passed.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/compile-back/patch-coordinator.test.ts`
	- Result: 1 test file passed, 2 tests passed.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/compile-back/react-patch-emitter.test.ts src/compile-back/patch-coordinator.test.ts`
	- Result: 2 test files passed, 4 tests passed.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/__tests__/source-providers.test.ts`
	- Result: 1 test file passed, 24 tests passed.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/compile-back/patch-coordinator.test.ts`
	- Result: 1 test file passed, 3 tests passed.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/__tests__/compile-back.test.ts src/__tests__/round-trip-golden.test.ts src/__tests__/e2e-patch-review.test.ts src/__tests__/e2e-import-job.test.ts src/__tests__/source-providers.test.ts`
	- Result: 5 test files passed, 54 tests passed.
- `cd products/yappc/frontend/apps/api && pnpm vitest run src/routes/__tests__/source-imports.test.ts src/services/__tests__/job-repository.test.ts`
	- Result: 2 test files passed, 8 tests passed.
- `cd products/yappc/frontend/web && pnpm vitest run src/services/compiler/__tests__/ImportSourceWorkflow.test.ts`
	- Result: 1 test file passed, 15 tests passed.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/__tests__/symbol-resolver.test.ts src/graph/__tests__/graph-query-schema.test.ts src/compile-back/patch-coordinator.test.ts`
	- Result: 3 test files passed, 18 tests passed.
- `./gradlew :products:yappc:core:yappc-services:compileJava --no-daemon` (after ingest metadata extraction + pagination query fixes)
	- Result: BUILD SUCCESSFUL.
- `cd products/yappc/frontend/libs/yappc-artifact-compiler && pnpm vitest run src/inventory/scanner.test.ts src/__tests__/symbol-resolver.test.ts src/graph/__tests__/graph-query-schema.test.ts src/compile-back/patch-coordinator.test.ts`
	- Result: 4 test files passed, 27 tests passed.

Execution boundary: This implementation log includes locally executed Vitest and Gradle validations listed above, and evaluates the codebase snapshot at the referenced target SHA (whose merge diff only updated the YAPPC changelog). :contentReference[oaicite:0]{index=0}

---

## Section A: Objective Current Status

| Area | Status | Evidence from current code | Objective conclusion | Production impact |
|---|---:|---|---|---|
| Artifact compiler package boundary | `PARTIALLY_IMPLEMENTED` | `src/index.ts` exports inventory, graph, source-providers, compile-back, model, provenance, residual, extractors, synthesis, merge, and builder. :contentReference[oaicite:1]{index=1} | A coherent package boundary exists. | Good foundation, but package content is uneven and not yet production round-trip safe. |
| Source provider abstraction | `PARTIALLY_IMPLEMENTED` | `SourceProvider`, `SourceLocatorSchema`, `RepositorySnapshotSchema`, and `SourceProviderRegistry` exist. :contentReference[oaicite:2]{index=2} :contentReference[oaicite:3]{index=3} | Provider abstraction exists, but `SourceScopeContext` is not part of `SourceProvider.resolve`, and raw credentials are still accepted. | Needs governed scope and secret-reference-only acquisition before production use. |
| GitHub source provider | `PARTIALLY_IMPLEMENTED` | GitHub provider resolves commit SHA, reads recursive tree, fails closed on truncated tree, and materializes files. :contentReference[oaicite:4]{index=4} | Repo import is technically possible for GitHub, but skip diagnostics, retry/resume, durable job binding, credential governance, and large-repo fallback are incomplete. | Foundation is useful but not production-grade for enterprise repos. |
| GitLab source provider | `PARTIALLY_IMPLEMENTED` | GitLab provider resolves commits, pages tree entries, materializes files, and records non-materialized files. :contentReference[oaicite:5]{index=5} :contentReference[oaicite:6]{index=6} | GitLab support exists, but raw token handling, missing diagnostics, and registry ambiguity remain. | Needs hardening and tests. |
| Local folder provider | `PARTIALLY_IMPLEMENTED` | Local provider resolves a directory, tries Git commit SHA and remote URL, and walks files. :contentReference[oaicite:7]{index=7} | Local import exists but does not prove immutable dirty-worktree snapshots and does not record skip diagnostics. | Unsafe for trusted reproducible repository snapshots. |
| ZIP source provider | `PARTIALLY_IMPLEMENTED` | ZIP provider parses central directory, protects against zip-slip, computes archive hash, and materializes entries. :contentReference[oaicite:8]{index=8} | ZIP support exists, but tar support is absent and unsupported/oversized/skipped entries are not surfaced through diagnostics. | Good starting point, incomplete archive acquisition. |
| Durable source import jobs | `PARTIALLY_IMPLEMENTED` | Fastify route creates jobs and records progress; repository is file-based under `~/.yappc/jobs`, “designed to be replaceable with database.” :contentReference[oaicite:9]{index=9} :contentReference[oaicite:10]{index=10} | Jobs exist, but are not DB-backed production durable jobs and route supports only `tsx`, `route`, `storybook`, `artifact`, and `zip`. | Not sufficient for repo-scale governed acquisition. |
| Frontend/API source type consistency | `DUPLICATED_OR_CONFLICTING` | Frontend workflow exposes `github`, `gitlab`, and `local-folder`; API route allowed types exclude those, while job repository type includes them. :contentReference[oaicite:11]{index=11} :contentReference[oaicite:12]{index=12} :contentReference[oaicite:13]{index=13} | Source import contracts are inconsistent across frontend, API route, and job repository. | Users can select/import paths that server orchestration rejects. |
| Deterministic inventory scanner | `PARTIALLY_IMPLEMENTED` | Scanner has snapshotRef, sorted traversal, checksums, `.gitignore` matcher, generated/binary classifiers, package/workspace detection. :contentReference[oaicite:14]{index=14} :contentReference[oaicite:15]{index=15} | Scanner has real functionality. | Needs skip-reason fidelity, stable timestamp strategy, real gitignore semantics, and fixture tests. |
| Skipped artifact reporting | `UNSAFE_FOR_PRODUCTION` | Schema defines `SkippedArtifactSchema`, but `scanRepository` initializes and sorts `skippedArtifacts` without ever pushing skipped/excluded/oversized/read-error records. :contentReference[oaicite:16]{index=16} :contentReference[oaicite:17]{index=17} | Current skip visibility is effectively broken. | Source fidelity and auditability are blocked. |
| Artifact IDs | `PARTIALLY_IMPLEMENTED` | `buildDeterministicNodeId` returns deterministic URNs when `snapshotRef` exists and `crypto.randomUUID()` when absent. :contentReference[oaicite:18]{index=18} | IDs are conditionally deterministic, not universally deterministic. | No-op round-trip and diff stability require enforced snapshotRef for source-derived scans. |
| Artifact graph schema | `PARTIALLY_IMPLEMENTED` | Graph schema includes file/symbol/component/route/style/API/state/workflow node kinds, resolved edges, unresolved edges, resolution records, indexes, source locations, provenance, confidence, flags, and residual refs. :contentReference[oaicite:19]{index=19} :contentReference[oaicite:20]{index=20} | Schema is strong, but runtime lifecycle is incomplete. | Good IR foundation, needs persistence/version/query hardening. |
| Edge resolution lifecycle | `PARTIALLY_IMPLEMENTED` | Resolver builds an in-memory index by symbolRef, label, file path, basename, extensions, and hardcoded aliases. :contentReference[oaicite:21]{index=21} | Resolver exists, but alias handling is not based on `tsconfig`, no persistent/exported symbol index, and no per-package boundary resolution. | Works for simple cases, not complex monorepos. |
| Graph validation | `PARTIALLY_IMPLEMENTED` | `validateGraph` checks duplicate node IDs, edge endpoint existence, confidence, required fields, and index consistency. :contentReference[oaicite:22]{index=22} | Structural validation exists. | Needs unresolved-edge policy validation and schema/runtime enforcement in backend ingest. |
| Semantic model schema | `PARTIALLY_IMPLEMENTED` | Model schema covers components, pages, layouts, tokens, themes, styles, data entities, APIs, state stores, interactions, cache, workflows, provenance, confidence, flags, graph links, and residual IDs. :contentReference[oaicite:23]{index=23} :contentReference[oaicite:24]{index=24} | Broad model schema exists. | Actual synthesis completeness depends on extractors and has schema mismatch risk. |
| Semantic synthesis pipeline | `PARTIALLY_IMPLEMENTED` | `SynthesisPipeline` scans, extracts, resolves symbols, validates graph, and builds model. :contentReference[oaicite:25]{index=25} :contentReference[oaicite:26]{index=26} | Pipeline exists but does not perform provider acquisition internally and relies entirely on registered extractors. | Foundation exists; not complete product-model synthesis. |
| Semantic model ID validity | `UNSAFE_FOR_PRODUCTION` | `SemanticProductModelSchema.id` requires UUID, but pipeline uses `buildDeterministicNodeId`, which returns `artifact://...` URNs when snapshotRef exists. :contentReference[oaicite:27]{index=27} :contentReference[oaicite:28]{index=28} | Schema and pipeline are inconsistent. | Model validation can fail for deterministic source-derived models. |
| Residual island schema | `PARTIALLY_IMPLEMENTED` | Residual schema includes source span, original source, reason, confidence, review, raw fragment ref, checksum, risk, related graph nodes. :contentReference[oaicite:29]{index=29} | Schema is strong. | Extraction/preservation logic is weaker than schema. |
| Residual preservation | `PARTIALLY_IMPLEMENTED` | Low-confidence pipeline residuals use `originalSource: artifact.relativePath` and zero source span; preserver can emit warnings, TODO stubs, or throwing code. :contentReference[oaicite:30]{index=30} :contentReference[oaicite:31]{index=31} | Residuals are modeled but not source-faithful enough. | Round-trip safety is blocked for unsupported source. |
| Compile-back / patch generation | `STUB_OR_PLACEHOLDER` | Compile-back types exist, and coordinator defaults to `ReactPatchEmitter`; emitter has placeholder comment diffs in sync path and regex-based async edits for rename/add-prop only. :contentReference[oaicite:32]{index=32} :contentReference[oaicite:33]{index=33} :contentReference[oaicite:34]{index=34} | Not true decompiler/round-trip patch generation. | Major blocker for world-class round-trip capability. |
| Backend graph APIs | `PARTIALLY_IMPLEMENTED` | ActiveJ controller exposes ingest/analyze/merge/query/residual endpoints and checks principal presence and tenant mismatch. :contentReference[oaicite:35]{index=35} | APIs exist, but scope propagation is incomplete for typed ingest/analyze/merge objects. | Tenant isolation must be fixed before production. |
| Backend graph service build correctness | `UNSAFE_FOR_PRODUCTION` | `ArtifactGraphServiceImpl.analyzeGraph` contains an apparent malformed `g(blockingExecutor, ...)` call in the inspected source. :contentReference[oaicite:36]{index=36} | Source appears compile-broken or corrupted. | P0 build blocker. |
| Backend persistence | `PARTIALLY_IMPLEMENTED` | JDBC repository supports node/edge upsert, tombstones, snapshot/version columns, pagination methods, and snapshot diff. :contentReference[oaicite:37]{index=37} :contentReference[oaicite:38]{index=38} | Persistence exists, but `snapshotId`/`versionId` are not supplied by service and pagination SQL appears broken. | Needs migration/API/service alignment. |
| Source import UX/backend contract | `PARTIALLY_IMPLEMENTED` | Frontend workflow has server import fallback and local fallback; API route returns review-required imports, progress job, and polling. :contentReference[oaicite:39]{index=39} :contentReference[oaicite:40]{index=40} :contentReference[oaicite:41]{index=41} | UX contract exists for single remote files, not full governed repo snapshots. | Repo import UX remains disconnected. |
| Tests | `PARTIALLY_IMPLEMENTED` | Scanner tests cover basic scan, classification, checksums, exclusion, ordering, package boundary, and zip-slip path assertions. :contentReference[oaicite:42]{index=42} | Tests are mostly current-repo smoke tests, not golden fixtures or round-trip tests. | Missing tests block safe refactor and production confidence. |

### Current Capability Classification

```text
Source acquisition: partial repo-capable in library; single-file governed API route only.
Inventory: repo-capable but not fully source-faithful or skip-auditable.
Graph: schema-capable and resolver-capable, but not fully persisted/versioned/query-safe.
Semantic model: broad schema and partial pipeline, not proven complete synthesis.
Compile-back: stub/partial; not round-trip safe.
Overall: not production-ready; foundation exists but trust, persistence, scope, and round-trip safety are incomplete.
```

---

## Section B: Evidence-Based Current Code Map

| Capability area | Current file/module | What exists objectively | What is missing objectively | Keep/modify/remove/consolidate |
|---|---|---|---|---|
| Compiler package API | `products/yappc/frontend/libs/yappc-artifact-compiler/src/index.ts` | Modular exports for inventory, graph, source providers, compile-back, model, residuals, synthesis. :contentReference[oaicite:43]{index=43} | No top-level governed “compile repository” façade. | Keep; add canonical pipeline façade. |
| Inventory schema | `src/inventory/types.ts` | Artifact, skipped artifact, package boundary, inventory schemas. :contentReference[oaicite:44]{index=44} | Skipped schema not actually populated. | Modify scanner to honor schema. |
| Scanner | `src/inventory/scanner.ts` | File walk, sort, checksums, generated/binary detection, `.gitignore` parser, package boundaries. :contentReference[oaicite:45]{index=45} :contentReference[oaicite:46]{index=46} | Skip reasons, bounded concurrency, stable time metadata, robust glob/gitignore implementation. | Modify/split into scanner modules. |
| Source providers | `src/source-providers/*` | Local/GitHub/GitLab/Zip providers and registry. :contentReference[oaicite:47]{index=47} | Scope context, credentialRef resolution, diagnostics, retry/cancel/resume. | Keep; harden contracts. |
| Graph schema | `src/graph/types.ts` | Strong node/edge/unresolved-edge graph schema. :contentReference[oaicite:48]{index=48} :contentReference[oaicite:49]{index=49} | Cursor pagination query schema, persisted symbol index schema. | Modify. |
| Symbol resolver | `src/synthesis/symbol-resolver.ts` | In-memory resolver for unresolved edges. :contentReference[oaicite:50]{index=50} | Configurable alias/project resolution, exported index, package-aware references. | Split into symbol-index and resolver. |
| Synthesis pipeline | `src/synthesis/pipeline.ts` | Scan → extract → resolve → graph → model pipeline. :contentReference[oaicite:51]{index=51} :contentReference[oaicite:52]{index=52} | Provider acquisition, extractor registry discovery, true provenance index, schema-consistent model IDs. | Modify. |
| Semantic model | `src/model/types.ts` | Broad model types and version metadata. :contentReference[oaicite:53]{index=53} :contentReference[oaicite:54]{index=54} | Container-level snapshotRef, source identity fields, schema alignment with deterministic URNs. | Modify. |
| Residuals | `src/residual/types.ts`, `src/compile-back/residual-preserver.ts` | Rich residual schema and preservation helper. :contentReference[oaicite:55]{index=55} :contentReference[oaicite:56]{index=56} | Raw source preservation in pipeline, block-on-risk policy, no production stubs. | Modify. |
| Compile-back | `src/compile-back/*` | Change and patch schemas, coordinator, React emitter. :contentReference[oaicite:57]{index=57} :contentReference[oaicite:58]{index=58} | AST/range patching, validation runner, apply/rollback/PR integration, no-op zero-diff. | Replace emitter; extend coordinator. |
| Backend API | `ArtifactGraphController.java` | ActiveJ controller with tenant mismatch checks. :contentReference[oaicite:59]{index=59} | Principal-derived scope object passed into service, workspace/project checks, typed DTO sanitization. | Modify. |
| Backend service | `ArtifactGraphServiceImpl.java` | JGraphT analysis, merge, residual analysis, parsing for Java/SQL/YAML. :contentReference[oaicite:60]{index=60} :contentReference[oaicite:61]{index=61} | Compile correctness, snapshot/version propagation, real residual analysis, TS/backend ownership clarity. | Modify. |
| Backend persistence | `ArtifactGraphRepository.java` | JDBC upsert, tombstones, pagination methods, snapshot diff. :contentReference[oaicite:62]{index=62} :contentReference[oaicite:63]{index=63} | DB migrations verification, pagination SQL correctness, principal scope enforcement in saved nodes. | Modify. |
| Frontend import workflow | `ImportSourceWorkflow.ts` | UI service exposes TSX/route/storybook/artifact/zip/github/gitlab/local-folder and server fallback. :contentReference[oaicite:64]{index=64} | Unified source import contract, duplicate residual type removal, repo import job status integration. | Modify/consolidate. |
| Fastify import API | `source-imports.ts` | Governed source import route, progress jobs, audit event. :contentReference[oaicite:65]{index=65} :contentReference[oaicite:66]{index=66} | Repo import providers, database job store, async job awaits, full snapshot pipeline. | Modify. |
| Job repository | `job-repository.ts` | File-based durable-ish job repository. :contentReference[oaicite:67]{index=67} | DB-backed production store, indexes, TTL cleanup safety, job locking. | Replace for prod; keep as dev adapter. |
| Tests | `scanner.test.ts` | Smoke tests for scanner. :contentReference[oaicite:68]{index=68} | Golden fixtures, no-op round-trip, provider, tenant isolation, backend API, frontend E2E tests. | Add comprehensive tests. |

---

## Section C: Gap Analysis Against Target State

| Capability | Current state | Gap | Severity | Required fix |
|---|---|---|---:|---|
| Build correctness | Backend service appears to contain malformed Java in `analyzeGraph`. :contentReference[oaicite:69]{index=69} | Build may fail before runtime validation. | P0 | Fix `ArtifactGraphServiceImpl.analyzeGraph` to use `Promise.ofBlocking(blockingExecutor, ...)` correctly and add compile test. |
| Server-side scope enforcement | Controller validates tenant mismatch but does not normalize typed ingest/analyze/merge DTOs to principal tenant. :contentReference[oaicite:70]{index=70} | Null/body-supplied scope can flow into service/repository. | P0 | Introduce `ArtifactRequestScope` and make service methods accept server-derived scope separately from payload. |
| Node persistence scope | `saveNodes` accepts product/tenant parameters but writes `node.tenantId()` and `node.projectId()`. :contentReference[oaicite:71]{index=71} | Node payload can override server scope. | P0 | Persist tenant/project from method arguments only; reject mismatching node DTO scope. |
| Skipped source fidelity | `skippedArtifacts` exists but is never populated. :contentReference[oaicite:72]{index=72} :contentReference[oaicite:73]{index=73} | Users cannot see ignored/skipped/residual files. | P0 | Record skip decisions at walker/scanner boundary with explicit reasons. |
| Semantic model validation | Pipeline returns deterministic URN model IDs while schema requires UUID. :contentReference[oaicite:74]{index=74} :contentReference[oaicite:75]{index=75} | Model may fail schema validation. | P0 | Change schema to accept `sourceModelRef` separately or make pipeline generate UUID model IDs. |
| Compile-back | Current emitter is regex/placeholder-based and limited to React component operations. :contentReference[oaicite:76]{index=76} | Not round-trip safe. | P0 | Replace with AST-backed patch emitters and no-op zero-diff tests. |
| Source provider governance | `ProviderCredentials` accepts raw token/password and providers read raw token. :contentReference[oaicite:77]{index=77} :contentReference[oaicite:78]{index=78} | Secrets can leak through options/logging/UI. | P0 | Use credentialRef only in governed flows; resolve secrets server-side. |
| Fastify repo import | API route excludes GitHub/GitLab/local-folder even though frontend and job repo support them. :contentReference[oaicite:79]{index=79} :contentReference[oaicite:80]{index=80} :contentReference[oaicite:81]{index=81} | Full repo import UX cannot be server-governed. | P1 | Extend API route to provider-backed repository import jobs. |
| Source import job durability | Job repository is file-based under user home. :contentReference[oaicite:82]{index=82} | Not production-durable or horizontally scalable. | P1 | Add DB-backed job repository and keep file repo as dev/test adapter. |
| Backend pagination | Repository paginated methods read `updated_at` cursor without selecting `updated_at`. :contentReference[oaicite:83]{index=83} | Runtime SQL/result mapping failure. | P1 | Select `updated_at` and add pagination tests. |
| Snapshot/version propagation | Service sets `snapshotId`, `versionId`, and checksum to null/TODO. :contentReference[oaicite:84]{index=84} | Graph persistence is not truly snapshot-aware. | P1 | Add DTO metadata fields and pass snapshot/version/checksum through service/repository. |
| Residual preservation | Low-confidence residuals do not preserve raw source; preserver can emit stubs/TODO/throw. :contentReference[oaicite:85]{index=85} :contentReference[oaicite:86]{index=86} | Unsupported source is not safely round-tripped. | P1 | Preserve raw spans and block patching when preservation cannot be exact. |
| Tests | Existing tests are scanner smoke tests. :contentReference[oaicite:87]{index=87} | No golden fixtures or round-trip evidence. | P1 | Add golden fixture suite and backend/frontend regression gates. |

---

## Section D: Architecture Decisions

### Decision 1: Canonical artifact compiler ownership

- **Decision:** Keep `products/yappc/frontend/libs/yappc-artifact-compiler` as the canonical TypeScript compiler/decompiler library for source acquisition, inventory, extraction, graph IR, semantic model, residuals, and compile-back contracts.
- **Why:** It already exports the relevant subdomains from a single package boundary. :contentReference[oaicite:88]{index=88}
- **Files to modify:** `src/index.ts`, `src/synthesis/pipeline.ts`, `src/source-providers/types.ts`.
- **Alternatives rejected:** Moving TS/TSX parsing into Java backend; backend itself says TS/JS should use frontend artifact compiler. :contentReference[oaicite:89]{index=89}

### Decision 2: Backend graph service ownership

- **Decision:** Keep durable graph persistence and analysis in `products/yappc/core/yappc-services`, but make it consume canonical graph/model DTOs generated from the TypeScript package/proto contract.
- **Why:** Java service already owns ActiveJ/JDBC/JGraphT persistence and analysis. :contentReference[oaicite:90]{index=90} :contentReference[oaicite:91]{index=91}
- **Files to modify:** `ArtifactGraphController.java`, `ArtifactGraphService.java`, `ArtifactGraphServiceImpl.java`, `ArtifactGraphRepository.java`.

### Decision 3: Source acquisition jobs

- **Decision:** Move production source import jobs from file-based repo to database-backed repository while retaining the file repository as a dev/test adapter.
- **Why:** Current repository is explicitly file-based under `~/.yappc/jobs`. :contentReference[oaicite:92]{index=92}
- **Files to modify/add:** `job-repository.ts`, `db-backed-job-repository.ts`, DB migration.

### Decision 4: Compile-back strategy

- **Decision:** Replace placeholder/regex compile-back with AST/range-based emitters and patch validation.
- **Why:** Current sync React emitter returns comment placeholders, and async path only supports limited regex edits. :contentReference[oaicite:93]{index=93}
- **Files to replace/modify:** `react-patch-emitter.ts`, `patch-coordinator.ts`, `compile-back/types.ts`.

### Decision 5: Residual strategy

- **Decision:** Residuals must default to verbatim preservation or block patch generation; production emitters must not generate stubs or throwing code for unknown source.
- **Why:** Current residual preserver can emit TODO/stub/throwing fallback content. :contentReference[oaicite:94]{index=94}
- **Files to modify:** `residual/types.ts`, `compile-back/residual-preserver.ts`, `synthesis/pipeline.ts`.

---

## Section E: Prescriptive File-by-File TODO Plan

| Priority | Phase | File path | Action | Current issue | Required change | Tests |
|---:|---|---|---|---|---|---|
| P0 | 1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java` | MODIFY | `analyzeGraph` contains apparent malformed `g(blockingExecutor, ...)`. :contentReference[oaicite:95]{index=95} | Replace with valid `Promise.ofBlocking(blockingExecutor, () -> runJGraphTAnalysis(...))`; ensure cache update stays outside CPU work. | Add/repair Java compile test and service unit test for `analyzeGraph`. |
| P0 | 1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java` | MODIFY | Controller validates mismatch but passes request DTO unchanged for ingest/analyze/merge. :contentReference[oaicite:96]{index=96} | Build server-derived `ArtifactRequestScope` from `Principal`; reject body tenant/workspace/project fields or overwrite with principal scope before service call. | Controller tests for missing principal, mismatched tenant, omitted tenant, valid principal. |
| P0 | 1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphService.java` | MODIFY | Service methods take request DTOs that can contain body scope. :contentReference[oaicite:97]{index=97} | Change signatures to `ingestGraph(scope, request)`, `analyzeGraph(scope, request)`, `mergeModels(scope, request)`. | Compile + service tests. |
| P0 | 1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java` | MODIFY | `saveNodes` writes `node.tenantId()` / `node.projectId()` instead of server params. :contentReference[oaicite:98]{index=98} | Persist `tenantId` and `productId/projectId` from method args only; validate node payload scope if present. | Tenant isolation persistence test. |
| P0 | 1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts` | MODIFY | `skippedArtifacts` never populated. :contentReference[oaicite:99]{index=99} | Refactor walker to emit `{file, skip}` events; push skip records for `.gitignore`, exclude glob, max size, symlink, read error, binary-not-extracted. | `scanner.skipped-artifacts.test.ts`. |
| P0 | 1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/model/types.ts` | MODIFY | Model ID schema requires UUID while pipeline can generate URN. :contentReference[oaicite:100]{index=100} | Add `sourceModelRef`/`deterministicRef`; keep `id` UUID, or allow `id` UUID-or-URN consistently across schemas. | Schema validation test for `SynthesisPipeline.runFromSnapshot`. |
| P0 | 1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/pipeline.ts` | MODIFY | Low-confidence residuals store path as `originalSource` and zero source span. :contentReference[oaicite:101]{index=101} | Read raw file content/span; create residual with checksum, rawFragmentRef, actual source span, and risk/review reason. | Residual preservation golden test. |
| P0 | 1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/react-patch-emitter.ts` | REPLACE | Sync path emits placeholder comment diffs; async path is regex-only. :contentReference[oaicite:102]{index=102} | Implement TypeScript Compiler API range edits for rename/add/update props; no placeholder diffs allowed. | No-op zero diff, rename minimal diff, add prop minimal diff tests. |
| P0 | 1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/residual-preserver.ts` | MODIFY | Strategies can emit warning comments, TODO stubs, or throwing code. :contentReference[oaicite:103]{index=103} | In production mode, only `verbatim-preserve` may emit; other strategies must mark patch blocked/review-required. | Residual overlap patch-blocking test. |
| P1 | 2 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/types.ts` | MODIFY | `SourceScopeContext` exists but is not used by provider API; raw credentials allowed. :contentReference[oaicite:104]{index=104} | Change provider resolve contract to accept typed `SourceLocator` and governed `SourceScopeContext`; deprecate raw token/password for browser flows. | Provider contract tests. |
| P1 | 2 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/github-provider.ts` | MODIFY | Uses raw `options.credentials.token`; skip/oversize/materialization failures have no diagnostics. :contentReference[oaicite:105]{index=105} | Resolve credentialRef server-side; add diagnostics for skipped files, maxFiles, maxFileSize, materialization failures. | GitHub provider mocked API tests. |
| P1 | 2 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/gitlab-provider.ts` | MODIFY | Same raw token/diagnostics gap. :contentReference[oaicite:106]{index=106} :contentReference[oaicite:107]{index=107} | Same as GitHub provider. | GitLab provider mocked API tests. |
| P1 | 2 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/local-folder-provider.ts` | MODIFY | Does not prove immutable dirty-worktree snapshot. :contentReference[oaicite:108]{index=108} | Add dirty status detection and content tree hash fallback; mark dirty snapshots as review-required. | Dirty repo snapshot test. |
| P1 | 2 | `products/yappc/frontend/apps/api/src/routes/source-imports.ts` | MODIFY | Allowed route types exclude GitHub/GitLab/local-folder. :contentReference[oaicite:109]{index=109} | Add repo import source types and dispatch to provider registry/pipeline. | API integration tests for GitHub/GitLab/local-folder. |
| P1 | 2 | `products/yappc/frontend/apps/api/src/services/job-repository.ts` | REPLACE | File-based job persistence only. :contentReference[oaicite:110]{index=110} | Add DB-backed `SourceImportJobRepository`; keep file repo for tests/dev. | Repository contract tests. |
| P1 | 2 | `products/yappc/frontend/apps/api/src/routes/source-imports.ts` | MODIFY | Some error responses assign `job: completeSourceImportJob(...)` without `await`. :contentReference[oaicite:111]{index=111} | Await every job update before response. | Route test asserts serialized job object, not Promise. |
| P1 | 3 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/symbol-resolver.ts` | SPLIT | Symbol index is private and aliases are hardcoded. :contentReference[oaicite:112]{index=112} | Add `symbol-index.ts`; load `tsconfig.paths`, package boundaries, workspace aliases. | Resolver tests for tsconfig paths and monorepo packages. |
| P1 | 3 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/types.ts` | MODIFY | Query schema has `limit` but no cursor/page token. :contentReference[oaicite:113]{index=113} | Add cursor pagination schema and unresolved-edge query filters. | Graph query schema tests. |
| P1 | 4 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java` | MODIFY | Paginated methods read `updated_at` without selecting it. :contentReference[oaicite:114]{index=114} | Add `updated_at` to select list or use selected cursor column. | Pagination integration test. |
| P1 | 4 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java` | MODIFY | `snapshotId`, `versionId`, `contentChecksum` are null/TODO. :contentReference[oaicite:115]{index=115} | Add request metadata DTO fields and pass through to repository/version repository. | Snapshot-aware ingest test. |
| P1 | 5 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/types.ts` | MODIFY | `TextPatch` lacks structured range metadata while emitter prepends range as comment. :contentReference[oaicite:116]{index=116} :contentReference[oaicite:117]{index=117} | Add first-class `ranges`, `baseChecksum`, `targetChecksum`, `validationStatus`. | Patch schema tests. |
| P1 | 5 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/patch-coordinator.ts` | MODIFY | Coordinator logs to `console.error`, has no validation runner or patch apply dry-run. :contentReference[oaicite:118]{index=118} | Add injectable logger, validator registry, dry-run apply, conflict detection. | Patch validation tests. |
| P1 | 6 | `products/yappc/frontend/web/src/services/compiler/ImportSourceWorkflow.ts` | CONSOLIDATE | Defines local `ResidualIsland` shape separate from compiler residual schema. :contentReference[oaicite:119]{index=119} | Import residual type from `yappc-artifact-compiler`; normalize server/client residual payloads. | Type test and UI import result test. |
| P2 | 6 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.test.ts` | REPLACE | Tests scan `process.cwd()` and are not golden fixture-based. :contentReference[oaicite:120]{index=120} | Move to deterministic fixtures under `test/fixtures/*`; remove current-repo dependent assertions. | Golden fixture test suite. |
| P2 | 6 | `products/yappc/frontend/libs/yappc-artifact-compiler/test/fixtures/*` | ADD | Required golden fixtures absent from inspected tests. :contentReference[oaicite:121]{index=121} | Add `small-react-app`, `react-router-app`, `prisma-fullstack-app`, `java-service`, `pnpm-monorepo`, etc. | All compiler golden tests. |

---

## Section F: Phase Plan

### Phase 1: Foundation Hardening

**Goal:** Make the existing code compile, preserve source-fidelity metadata, and prevent tenant-scope leakage.

**Files in scope:**

- `ArtifactGraphServiceImpl.java`
- `ArtifactGraphController.java`
- `ArtifactGraphService.java`
- `ArtifactGraphRepository.java`
- `src/inventory/scanner.ts`
- `src/model/types.ts`
- `src/synthesis/pipeline.ts`
- `src/compile-back/react-patch-emitter.ts`
- `src/compile-back/residual-preserver.ts`

**Validation:**

- Java compile passes.
- Scanner skipped-artifact fixture tests pass.
- Synthesis output validates against model schema.
- Tenant isolation tests pass.
- No placeholder patch diffs are emitted.

**Exit criteria:**

- No malformed backend source.
- Source-derived scans produce visible skip reasons.
- Semantic model IDs validate.
- Backend writes cannot use request-body tenant/project scope.
- Unsupported residuals block unsafe patch generation.

---

### Phase 2: Source Provider and Snapshot Layer

**Goal:** Make repository acquisition governed, scoped, diagnostic-rich, and durable.

**Files in scope:**

- `src/source-providers/types.ts`
- `github-provider.ts`
- `gitlab-provider.ts`
- `local-folder-provider.ts`
- `zip-provider.ts`
- `source-imports.ts`
- `job-repository.ts`
- DB migration for source import jobs and repository snapshots.

**Validation:**

- GitHub/GitLab/local/ZIP provider tests.
- DB-backed job repository tests.
- API route tests for source import job lifecycle.

**Exit criteria:**

- GitHub/GitLab/local/ZIP imports create durable scoped jobs.
- SnapshotRef is commit/content pinned.
- Skipped/unmaterialized files are visible with diagnostics.
- Raw credentials are not accepted in browser-governed flows.

---

### Phase 3: Canonical Compile Pipeline

**Goal:** Make snapshot → inventory → extraction → graph → semantic model → residuals deterministic and extensible.

**Files in scope:**

- `src/synthesis/pipeline.ts`
- `src/synthesis/symbol-resolver.ts`
- `src/synthesis/symbol-index.ts`
- `src/extractors/types.ts`
- `src/graph/types.ts`

**Validation:**

- Golden fixture extraction tests.
- Resolver tests for relative imports, aliases, package boundaries, ambiguous references.
- Graph validation tests.

**Exit criteria:**

- Resolved graph edges only contain valid graph node IDs.
- Unresolved/cross-repo/ambiguous references are preserved and queryable.
- Semantic elements include graph provenance and confidence.

---

### Phase 4: Backend Production Hardening

**Goal:** Persist and query graph/model data with snapshot awareness, pagination, and auditability.

**Files in scope:**

- `ArtifactGraphController.java`
- `ArtifactGraphServiceImpl.java`
- `ArtifactGraphRepository.java`
- `ArtifactModelVersionRepository.java`
- DB migrations.

**Validation:**

- Backend integration tests for ingest/query/pagination/snapshot diff.
- Tenant isolation tests.
- Performance test for large graph query.

**Exit criteria:**

- Graph ingest carries snapshot/version/checksum.
- Query APIs paginate.
- CPU-heavy graph analysis runs on blocking executor.
- All graph endpoints enforce principal-derived scope.

---

### Phase 5: Compile-Back and Patch Generation

**Goal:** Produce minimal, validated, reviewable patch sets.

**Files in scope:**

- `compile-back/types.ts`
- `react-patch-emitter.ts`
- `patch-coordinator.ts`
- `residual-preserver.ts`
- new validator/apply modules.

**Validation:**

- No-op round-trip zero diff.
- Simple React rename minimal diff.
- Add prop minimal diff.
- Residual overlap blocks unsafe patch.
- Patch validation rejects stale checksum.

**Exit criteria:**

- No placeholder patch diffs.
- PatchSet includes checksums, ranges, validation result, review bundle.
- Unsupported edits require manual review.

---

### Phase 6: UX and Continuous Evolution

**Goal:** Provide no-cognitive-load repo import, import summary, confidence, residuals, patch review, and drift detection.

**Files in scope:**

- `ImportSourceWorkflow.ts`
- import API client files
- import/review UI components
- canvas/page builder bridge.

**Validation:**

- Frontend E2E import job flow.
- Patch review UX test.
- Re-scan/drift test.

**Exit criteria:**

- User can select provider/ref, start import, see progress, review summary, inspect residuals, generate patch, validate, and accept/reject changes.

---

## Section G: Cleanup and Consolidation Plan

| Priority | Path | Current problem | Action | Target canonical path |
|---:|---|---|---|---|
| P0 | `src/compile-back/react-patch-emitter.ts` | Placeholder sync diffs and regex-only async patches. :contentReference[oaicite:122]{index=122} | REPLACE | Same file with AST/range emitter. |
| P0 | `src/compile-back/residual-preserver.ts` | Production strategies can emit stubs/TODO/throwing source. :contentReference[oaicite:123]{index=123} | MODIFY | Preserve-or-block policy. |
| P0 | `ArtifactGraphServiceImpl.java` | Apparent malformed source in graph analysis. :contentReference[oaicite:124]{index=124} | MODIFY | Valid ActiveJ blocking executor pattern. |
| P1 | `ImportSourceWorkflow.ts` | Duplicate frontend residual type. :contentReference[oaicite:125]{index=125} | CONSOLIDATE | `yappc-artifact-compiler/src/residual/types.ts`. |
| P1 | `job-repository.ts` | File-based prod-like job store. :contentReference[oaicite:126]{index=126} | SPLIT | `FileJobRepository` dev adapter + DB repository. |
| P1 | `source-imports.ts` | Allowed source types conflict with frontend/job repo. :contentReference[oaicite:127]{index=127} :contentReference[oaicite:128]{index=128} | MODIFY | Single source import contract. |
| P1 | `scanner.test.ts` | Current-repo smoke tests. :contentReference[oaicite:129]{index=129} | REPLACE | Golden fixture tests. |
| P2 | `synthesis/symbol-resolver.ts` | Private index + hardcoded aliases. :contentReference[oaicite:130]{index=130} | SPLIT | `symbol-index.ts` + resolver. |
| P2 | `source-providers/types.ts` | Raw credentials and scope context not enforced in API. :contentReference[oaicite:131]{index=131} | MODIFY | Governed typed locator/scope contract. |

---

## Section H: Test Plan

| Priority | Test type | Test file path | Scenario | Expected assertion |
|---:|---|---|---|---|
| P0 | Java compile | `products/yappc/core/yappc-services/.../ArtifactGraphServiceImplTest.java` | Compile and call `analyzeGraph`. | No malformed source; analysis returns result. |
| P0 | Backend tenant isolation | `ArtifactGraphControllerScopeTest.java` | Body tenant omitted/mismatched. | Principal tenant is used; mismatch returns 403. |
| P0 | Repository scope | `ArtifactGraphRepositoryScopeTest.java` | Node DTO tenant differs from method tenant. | Persisted tenant equals server tenant or request rejected. |
| P0 | Scanner fixture | `src/inventory/scanner.skipped-artifacts.test.ts` | `.gitignore`, excludeGlob, oversize, symlink, readError. | `skippedArtifacts` contains exact reasons. |
| P0 | Model schema | `src/synthesis/pipeline.schema.test.ts` | Run pipeline with snapshotRef. | `SemanticProductModelSchema.parse(model)` succeeds. |
| P0 | Round-trip | `src/compile-back/roundtrip.test.ts` | No-op source → model → patch. | Patch set is empty / zero diff. |
| P0 | Residual safety | `src/compile-back/residual-preserver.test.ts` | Patch overlaps residual. | Patch is blocked or review-required; no stub emitted. |
| P1 | GitHub provider | `src/source-providers/github-provider.test.ts` | Resolve branch to commit SHA. | SnapshotRef has resolved commit SHA and diagnostics for skipped files. |
| P1 | GitLab provider | `src/source-providers/gitlab-provider.test.ts` | Resolve paginated repo tree. | All pages processed; oversize files recorded. |
| P1 | Local provider | `src/source-providers/local-folder-provider.test.ts` | Dirty git worktree. | Snapshot is marked dirty/review-required or content-hash pinned. |
| P1 | API route | `source-imports.test.ts` | GitHub import job. | Job persists, progresses, has snapshotRef. |
| P1 | Job repo | `db-job-repository.test.ts` | Create/update/poll/delete expired. | DB-backed job lifecycle works across process restart. |
| P1 | Graph resolver | `symbol-resolver.test.ts` | TS path alias and ambiguous imports. | Resolved, ambiguous, cross-repo statuses correct. |
| P1 | Backend pagination | `ArtifactGraphRepositoryPaginationTest.java` | Fetch nodes/edges with cursor. | No SQL column error; nextCursor returned. |
| P1 | Frontend E2E | `import-source-flow.spec.ts` | Start repo import and review summary. | UI shows progress, skipped files, residuals, confidence. |
| P1 | Patch UX | `patch-review-flow.spec.ts` | Generate patch, view validation, reject/apply. | Diff and validation are visible; actions work. |

---

## Section I: Critical Questions — Direct Answers

### 1. Is the current system truly round-trip capable?

**Answer:** No.

**Evidence:** Compile-back contracts and coordinator exist, but React emitter still emits placeholder comment diffs in sync mode and limited regex-based patches in async mode. :contentReference[oaicite:132]{index=132} :contentReference[oaicite:133]{index=133} :contentReference[oaicite:134]{index=134}

**Required fix:** Replace placeholder/regex patching with AST/range emitters, patch validation, and no-op round-trip zero-diff tests.

---

### 2. Can it scan a full GitHub repo today, or only import individual sources/files?

**Answer:** Partial.

**Evidence:** GitHub provider can resolve repo/ref to commit SHA and materialize recursive tree files. :contentReference[oaicite:135]{index=135} However, Fastify governed source import route only allows `tsx`, `route`, `storybook`, `artifact`, and `zip`, not `github`. :contentReference[oaicite:136]{index=136}

**Required fix:** Wire GitHub/GitLab/local-folder providers into the governed API job route.

---

### 3. Are artifact IDs deterministic?

**Answer:** Partial.

**Evidence:** `buildDeterministicNodeId` is deterministic only when `snapshotRef` is present; otherwise it returns `crypto.randomUUID()`. :contentReference[oaicite:137]{index=137}

**Required fix:** Require snapshotRef for source-derived scans; reserve UUIDs only for manual/user-created artifacts.

---

### 4. Are graph edges valid and resolved?

**Answer:** Partial.

**Evidence:** Graph schema separates resolved and unresolved edges, resolver emits resolved edges only when a target node is found, and graph validation checks edge endpoints. :contentReference[oaicite:138]{index=138} :contentReference[oaicite:139]{index=139} :contentReference[oaicite:140]{index=140}

**Required fix:** Persist unresolved-edge lifecycle, make symbol index configurable, and validate that backend ingest rejects fake resolved edges.

---

### 5. Is there a complete synthesis pipeline?

**Answer:** Partial.

**Evidence:** `SynthesisPipeline` scans, extracts, resolves, validates, and builds graph/model. :contentReference[oaicite:141]{index=141} :contentReference[oaicite:142]{index=142} It relies entirely on registered extractors and has model ID schema mismatch.

**Required fix:** Add provider acquisition, extractor registry/capability discovery, schema-valid model IDs, and golden fixture coverage.

---

### 6. Is compile-back/patch generation implemented?

**Answer:** Partial / stub.

**Evidence:** Types and coordinator exist, but patch emitter is limited and placeholder-based in sync path. :contentReference[oaicite:143]{index=143} :contentReference[oaicite:144]{index=144}

**Required fix:** Implement AST-backed patch emitters and validation/apply pipeline.

---

### 7. Are residual islands preserved?

**Answer:** Partial.

**Evidence:** Residual schema is rich, but pipeline-generated low-confidence residuals do not preserve raw source content/span, and preserver can emit stubs. :contentReference[oaicite:145]{index=145} :contentReference[oaicite:146]{index=146} :contentReference[oaicite:147]{index=147}

**Required fix:** Preserve raw spans and block unsafe regeneration.

---

### 8. Are source import jobs durable?

**Answer:** Partial.

**Evidence:** File-backed job repository persists JSON jobs under `~/.yappc/jobs`; it is described as initially file-based and replaceable with database. :contentReference[oaicite:148]{index=148}

**Required fix:** Implement DB-backed repository and job locking.

---

### 9. Is tenant/workspace/project scope enforced consistently?

**Answer:** Partial.

**Evidence:** Controller checks principal tenant for graph APIs, but service/repository still accept scope from DTOs in several paths. :contentReference[oaicite:149]{index=149} :contentReference[oaicite:150]{index=150}

**Required fix:** Pass server-derived scope separately and persist only server-derived scope.

---

### 10. Is backend artifact graph logic in the right canonical module?

**Answer:** Partial.

**Evidence:** `yappc-services` contains API/service/repository implementation for graph operations. :contentReference[oaicite:151]{index=151} :contentReference[oaicite:152]{index=152} :contentReference[oaicite:153]{index=153}

**Required fix:** Keep durable backend graph operations in `yappc-services`, but align DTOs with the TypeScript compiler graph schema and verify/consolidate any overlapping `knowledge-graph` implementations in the next pass.

---

### 11. Are there duplicate or conflicting implementations?

**Answer:** Yes.

**Evidence:** Frontend import workflow, API route, and job repository disagree on supported source types; frontend also defines a duplicate residual shape. :contentReference[oaicite:154]{index=154} :contentReference[oaicite:155]{index=155} :contentReference[oaicite:156]{index=156}

**Required fix:** Create one shared source import contract and one shared residual DTO.

---

### 12. What is the smallest milestone that makes the foundation trustworthy?

**Answer:** Milestone 1: stable repository IR and source snapshot compiler.

**Required first files:**

- `scanner.ts`
- `types.ts` for source providers/model/graph
- `pipeline.ts`
- `symbol-resolver.ts`
- `ArtifactGraphController.java`
- `ArtifactGraphServiceImpl.java`
- `ArtifactGraphRepository.java`
- `source-imports.ts`
- DB migration for jobs/snapshots/graph versions.

---

### 13. What files must be changed first?

**Answer:**

1. `ArtifactGraphServiceImpl.java`
2. `ArtifactGraphController.java`
3. `ArtifactGraphRepository.java`
4. `scanner.ts`
5. `model/types.ts`
6. `pipeline.ts`
7. `react-patch-emitter.ts`
8. `source-imports.ts`
9. `job-repository.ts`

---

### 14. What tests must fail today and pass after implementation?

**Answer:**

- Backend compile test for `ArtifactGraphServiceImpl`.
- Tenant isolation test where body tenant is omitted/mismatched.
- Scanner skipped-artifact test.
- Synthesis model schema validation test.
- No-op round-trip zero-diff test.
- Residual preservation test.
- GitHub provider diagnostics test.
- DB-backed job repository test.
- Repo import API route test.
- Backend pagination test.

---

### 15. What legacy/stale/deprecated code or docs must be removed or consolidated?

**Answer:**

- Placeholder patch emission in `react-patch-emitter.ts`.
- Production stub/throw residual strategies in `residual-preserver.ts`.
- Duplicate residual type in `ImportSourceWorkflow.ts`.
- File-based job repository as production default.
- Current-repo scanner smoke tests as primary confidence tests.
- Conflicting source type lists across frontend/API/job repository.

---

## Section J: Definition of Done

Implementation is done when:

1. Java backend compiles cleanly.
2. Repository source can be imported as immutable, scoped snapshot.
3. Source providers use governed credential references, not raw browser tokens.
4. Inventory records deterministic artifacts and explicit skipped artifacts.
5. Graph resolved edges only contain valid node IDs.
6. Unresolved, ambiguous, and cross-repo references are persisted and reviewable.
7. Semantic model validates and carries provenance/confidence.
8. Residual islands preserve raw source spans and checksums.
9. No-op round-trip produces zero diff.
10. Supported edits produce minimal AST/range-backed patches.
11. Patch validation blocks stale, risky, or residual-overlapping edits.
12. Backend graph APIs enforce principal-derived tenant/workspace/project scope.
13. Import jobs are DB-backed, auditable, retryable, and pollable.
14. UI shows provider/ref selection, progress, import summary, skipped files, confidence, residuals, validation, and patch review.
15. Golden fixture tests and E2E import/edit/patch/re-scan tests pass.

---
