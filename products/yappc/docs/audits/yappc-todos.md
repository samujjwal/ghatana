# Artifact Compiler/Decompiler Production-Readiness Audit and Implementation Plan

**Repository:** `samujjwal/ghatana`
**Target commit:** `3ec6c221ceab86479a1885da00669bb2058ae081`
**Commit evidence:** GitHub resolved the target commit as `compiler - decompiler 1`. 

**Audit basis:** static source inspection through the GitHub connector. I did not execute `pnpm`, `gradle`, Vitest, Playwright, database migrations, or runtime services. Validation commands below are required next-step verification, not completed runtime evidence.

---

## Section A: Objective Current Status

| Area                            |                  Status | Evidence from current code                                                                                                                                                         | Objective conclusion                                                                                                                                 | Production impact                                                                                                                 |
| ------------------------------- | ----------------------: | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Artifact compiler package shell | `PARTIALLY_IMPLEMENTED` | Root package exports inventory, graph, source providers, compile-back, model, provenance, residual, extractors, synthesis, merge, and builder.                                     | The package has real module structure, not only docs.                                                                                                | Good foundation, but not production-ready because several exports, schemas, runtime APIs, and backend contracts are inconsistent. |
| Package public exports          | `PARTIALLY_IMPLEMENTED` | `package.json` exports `inventory`, `graph`, `model`, `extractors`, `provenance`, `residual`, `merge`, and `synthesis`, but not `source-providers`, `compile-back`, or `builder`.  | Internal root index and published subpath exports do not match.                                                                                      | Consumers cannot reliably use key compiler/decompiler modules as public API.                                                      |
| README implementation claim     |   `DEPRECATED_OR_STALE` | README claims bidirectional round-trip code generation and shows `scanRepository` imported from `@yappc/artifact-compiler/inventory`.                                              | README overstates runtime readiness and documents an import path that the inventory barrel does not expose.                                          | Misleads implementation planning and consumers.                                                                                   |
| Inventory scanner               | `PARTIALLY_IMPLEMENTED` | `scanner.ts` implements scanning, `.gitignore` matcher, package boundary detection, binary/generated heuristics, checksums, and summary.                                           | Repo-wide scanning exists, but skipped reasons, deterministic full inventory output, binary checksums, concurrency, and durable jobs are incomplete. | Useful prototype/foundation, not production-grade repository IR.                                                                  |
| Inventory public barrel         | `PARTIALLY_IMPLEMENTED` | `inventory/index.ts` exports only schemas/types, not `scanRepository`, `ScannerConfig`, or defaults.                                                                               | Public API is incomplete.                                                                                                                            | README usage fails through package subpath.                                                                                       |
| Artifact IDs                    | `PARTIALLY_IMPLEMENTED` | Graph helper returns deterministic URN when `snapshotRef` exists, random UUID otherwise.                                                                                           | IDs are deterministic only when snapshotRef is supplied and correctly propagated.                                                                    | Ad-hoc scans and model IDs still break repeatable round-trip identity.                                                            |
| Source providers                | `PARTIALLY_IMPLEMENTED` | Source provider abstraction, registry, local folder, GitHub, GitLab, and ZIP providers exist.                                                                                      | Provider layer exists but lacks governed locator schema, credential references, durable jobs, scope, provider diagnostics, and safe skip summaries.  | Not safe enough for governed multi-tenant source acquisition.                                                                     |
| GitHub provider                 | `UNSAFE_FOR_PRODUCTION` | GitHub provider resolves commit SHA and recursive tree, but continues on truncated tree with `console.warn`.                                                                       | Large repo snapshots can silently be incomplete.                                                                                                     | Breaks source fidelity and audit trust.                                                                                           |
| GitLab provider                 | `UNSAFE_FOR_PRODUCTION` | GitLab provider creates temp root inside per-file loop and later returns a separate temp root.                                                                                     | Snapshot file paths can point outside returned `localRootPath`.                                                                                      | Pipeline can scan empty or inconsistent snapshots.                                                                                |
| ZIP provider                    | `UNSAFE_FOR_PRODUCTION` | ZIP extraction uses `join(tempRoot, relativePath)` without visible normalized containment guard.                                                                                   | Zip-slip path traversal is not blocked.                                                                                                              | P0 source import security risk.                                                                                                   |
| Artifact graph schema           | `PARTIALLY_IMPLEMENTED` | Graph schema includes node/edge kinds, source location, provenance, confidence, privacy flags, unresolved edges, and resolution records.                                           | TS graph schema is strong, but validation that resolved edges point to real node IDs is not enforced by schema.                                      | Invalid graph can still be assembled/persisted.                                                                                   |
| Graph query schema              | `UNSAFE_FOR_PRODUCTION` | `GraphQuerySchema` requires `fromNodeId`/`toNodeId` as UUIDs while node IDs may be deterministic URNs.                                                                             | Query schema conflicts with actual ID strategy.                                                                                                      | Graph query APIs can reject valid artifact URNs.                                                                                  |
| Symbol resolver                 | `PARTIALLY_IMPLEMENTED` | Resolver builds symbol index and separates resolved vs unresolved/ambiguous/cross-repo references.                                                                                 | Good lifecycle shape, but no relative import resolution, extension/index lookup, tsconfig path alias support, or package exports support.            | Many real repo references remain unresolved or misclassified.                                                                     |
| Semantic model schema           | `PARTIALLY_IMPLEMENTED` | Rich model schemas exist for components, pages, layouts, tokens, data, APIs, state, interactions, cache, and workflows.                                                            | Model layer is broad, but does not strongly map elements back to graph node IDs/source refs.                                                         | Weak round-trip traceability.                                                                                                     |
| Residual islands                | `PARTIALLY_IMPLEMENTED` | Residual schema exists with original source, reason, review, strategy, location, extractor, confidence, linked element IDs.                                                        | Residual concept exists, but lacks raw fragment checksum/ref, risk level, graph node links, and enforced patch preservation.                         | Unsupported source can still be mishandled during compile-back.                                                                   |
| Synthesis pipeline              | `PARTIALLY_IMPLEMENTED` | Pipeline scans, extracts, resolves symbols, assembles graph, returns model elements/residuals/stats.                                                                               | Pipeline exists, but it returns loose model elements, not a complete versioned `SemanticProductModel`; residual creation is lossy.                   | Not yet a complete source → graph → semantic model compiler.                                                                      |
| Extractor plugin contract       | `PARTIALLY_IMPLEMENTED` | Extractor interface exists with identity, `canExtract`, `extract`, nodes, edges, unresolved edges, model elements, residuals, errors/warnings.                                     | Contract is solid, but public index exports extractor functions, not registry-ready extractor instances/capabilities.                                | Extensibility is not yet runtime-discoverable.                                                                                    |
| Compile-back / patch generation |   `STUB_OR_PLACEHOLDER` | Compile-back exposes change ops, patch set, coordinator, React emitter, and residual preserver.                                                                                    | Compile-back exists but is not true round-trip capable.                                                                                              | Cannot safely apply model edits back to source.                                                                                   |
| React patch emitter             | `UNSAFE_FOR_PRODUCTION` | Sync emitter returns placeholder comments; async emitter supports only rename/add-prop and emits full-file replacement hunks.                                                      | Not minimal patch generation and not complete React transformation.                                                                                  | Fails no-op/minimal-diff round-trip requirements.                                                                                 |
| Patch coordinator               | `PARTIALLY_IMPLEMENTED` | Coordinator routes ops to emitters, preserves residuals by element ID, and flags low confidence.                                                                                   | No validation, rollback metadata, review bundle, structured failures, or conflict detection.                                                         | Patch sets are not production-applyable.                                                                                          |
| Backend proto                   |   `DEPRECATED_OR_STALE` | Proto uses older node/edge DTOs with tenant/product fields and lacks snapshot refs, unresolved edges, residuals, confidence, provenance, source locations, and patch APIs.         | Backend contract lags TS compiler schema.                                                                                                            | Frontend compiler and Java backend cannot share a canonical artifact graph contract.                                              |
| Backend controller              | `PARTIALLY_IMPLEMENTED` | Controller requires principal and rejects mismatched tenant IDs.                                                                                                                   | Tenant guard exists, but controller passes original request through when body tenant is null and does not derive/validate project/product ownership. | Scope enforcement remains incomplete.                                                                                             |
| Backend service                 | `PARTIALLY_IMPLEMENTED` | Service supports ingest, analysis, merge, query, and residual analysis.                                                                                                            | No import jobs, snapshot APIs, patch APIs, validation APIs, or paginated graph APIs.                                                                 | Backend is graph-analysis capable, not artifact compiler/decompiler capable.                                                      |
| Backend implementation          | `UNSAFE_FOR_PRODUCTION` | Service implementation tombstones graph then saves all nodes/edges, has default `Runnable::run` executor, stubs unsupported parsers, and shallow residual analysis.                | Event-loop safety, incremental updates, parser support boundaries, and residual analysis are incomplete.                                             | Production import/analysis can block, lose history semantics, or return stub results.                                             |
| DB schema                       | `UNSAFE_FOR_PRODUCTION` | Repository writes `content_checksum`, `snapshot_id`, `version_id`, `is_tombstone`, but V10 table migration does not define those columns.                                          | Runtime persistence will fail unless an uninspected migration exists; V11 only creates model versions.                                               | P0 migration/runtime mismatch.                                                                                                    |
| Java HTTP route wiring          | `PARTIALLY_IMPLEMENTED` | Java server wires artifact graph ingest/analyze/merge/query/residual endpoints.                                                                                                    | No Java source snapshot/import/compile-back/patch/re-scan routes.                                                                                    | Backend does not expose the target lifecycle.                                                                                     |
| Fastify source import           | `PARTIALLY_IMPLEMENTED` | Fastify route supports single source import with in-memory job map, headers for tenant/workspace/project, and HTTPS/artifact locator fetch.                                        | It is single-file/source import, not full repo snapshot compiler; jobs are not durable.                                                              | UI import flow is not production source acquisition.                                                                              |
| Knowledge graph duplication     |            `UNVERIFIED` | Search found artifact graph mapper and graph node/edge models in `products/yappc/core/knowledge-graph`.                                                                            | Needs deeper inspection before declaring duplication, but likely overlaps with yappc-services graph logic.                                           | Canonical ownership is unclear.                                                                                                   |

### Current capability classification

```text
Source acquisition: partial repo-capable library, not governed/durable production source acquisition
Inventory: repo-capable partial scanner, not fully deterministic or skip-reason complete
Graph: schema-capable and unresolved-aware in TypeScript, stale/incomplete in backend proto/persistence
Semantic model: broad schema partial, weak graph/provenance binding
Compile-back: stub/partial, not minimal-patch or round-trip safe
Overall: not production-ready; foundation exists but P0 correctness/security/schema mismatches block trust
```

---

## Section B: Evidence-Based Current Code Map

| Capability area        | Current file/module                                                                | Current symbols/classes/functions                                                        | What exists objectively                                  | What is missing objectively                                                                                | Keep/modify/remove/consolidate                         | Evidence quality |
| ---------------------- | ---------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | -------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ | ---------------- |
| Package root           | `products/yappc/frontend/libs/yappc-artifact-compiler/src/index.ts`                | Barrel exports                                                                           | Canonical module grouping exists.                        | Export strategy does not match `package.json`.                                                             | `MODIFY` package exports and root/subpath consistency. | `DIRECT_CODE`    |
| Package metadata       | `products/yappc/frontend/libs/yappc-artifact-compiler/package.json`                | `exports`, scripts, deps                                                                 | Build/test/type-check scripts exist.                     | Missing public subpaths for source providers, compile-back, builder.                                       | `MODIFY`                                               | `DIRECT_CODE`    |
| Inventory types        | `src/inventory/types.ts`                                                           | `ArtifactRecordSchema`, `ArtifactInventorySchema`                                        | Repository inventory schema exists.                      | Skipped file records/reasons and source snapshot ID are incomplete.                                        | `MODIFY`                                               | `DIRECT_CODE`    |
| Inventory scanner      | `src/inventory/scanner.ts`                                                         | `scanRepository`, `ScannerConfig`                                                        | Real scanner implementation.                             | Public export, stable binary checksum, skipped reasons, deterministic sorting, bounded concurrency.        | `MODIFY`                                               | `DIRECT_CODE`    |
| Graph schema           | `src/graph/types.ts`                                                               | `GraphNodeSchema`, `GraphEdgeSchema`, `UnresolvedGraphEdgeSchema`, `ArtifactGraphSchema` | Strong TS graph schema.                                  | Graph-level edge validation and query ID compatibility.                                                    | `MODIFY`                                               | `DIRECT_CODE`    |
| Source provider types  | `src/source-providers/types.ts`                                                    | `SourceProvider`, `RepositorySnapshot`, `SourceProviderRegistry`                         | Provider abstraction exists.                             | `SourceLocator`, credential refs, scope context, skipped diagnostics, provider capability metadata.        | `MODIFY`                                               | `DIRECT_CODE`    |
| GitHub source provider | `src/source-providers/github-provider.ts`                                          | `GitHubProvider`                                                                         | Commit-pinned GitHub tree materialization.               | Fail-closed truncation, skip reasons, retry/rate-limit, governed credential refs.                          | `MODIFY`                                               | `DIRECT_CODE`    |
| GitLab source provider | `src/source-providers/gitlab-provider.ts`                                          | `GitLabProvider`                                                                         | GitLab API materialization attempt.                      | Correct temp root lifecycle and unambiguous routing.                                                       | `MODIFY`                                               | `DIRECT_CODE`    |
| ZIP source provider    | `src/source-providers/zip-provider.ts`                                             | `ZipProvider`                                                                            | ZIP extraction exists.                                   | Zip-slip defense, archive bomb guard, unsupported compression diagnostics.                                 | `MODIFY`                                               | `DIRECT_CODE`    |
| Extractor contract     | `src/extractors/types.ts`                                                          | `ArtifactExtractor`, `ExtractionResult`                                                  | Clean extractor interface.                               | Runtime registry/capability discovery and support levels.                                                  | `MODIFY` / `ADD` registry                              | `DIRECT_CODE`    |
| Extractor exports      | `src/extractors/index.ts`                                                          | extractor functions                                                                      | TS, page, state, Storybook, Prisma functions exported.   | Registered extractor instances not exposed.                                                                | `MODIFY`                                               | `DIRECT_CODE`    |
| Synthesis pipeline     | `src/synthesis/pipeline.ts`                                                        | `SynthesisPipeline`                                                                      | Full scan/extract/resolve/assemble orchestration exists. | SemanticProductModel container, provenance index, source acquisition job integration, raw residual source. | `MODIFY`                                               | `DIRECT_CODE`    |
| Symbol resolution      | `src/synthesis/symbol-resolver.ts`                                                 | `resolveSymbols`                                                                         | Unresolved-to-resolved lifecycle exists.                 | Relative import resolution, path alias support, package exports.                                           | `MODIFY`                                               | `DIRECT_CODE`    |
| Semantic model         | `src/model/types.ts`                                                               | `SemanticProductModelSchema`                                                             | Broad model types exist.                                 | Graph node/sourceRef mapping and model version/diff schemas.                                               | `MODIFY`                                               | `DIRECT_CODE`    |
| Residuals              | `src/residual/types.ts`                                                            | `ResidualIslandSchema`                                                                   | Residual schema exists.                                  | Raw fragment ref/checksum, risk level, graph links, safe strategy restrictions.                            | `MODIFY`                                               | `DIRECT_CODE`    |
| Compile-back types     | `src/compile-back/types.ts`                                                        | `ChangeOp`, `PatchSet`, `buildChangePlan`                                                | Basic component change plan exists.                      | `ModelChange`, `ChangePlan`, `FilePatch`, `ReviewBundle`, `ValidationResult`, `RollbackMetadata`.          | `MODIFY`                                               | `DIRECT_CODE`    |
| React patch emitter    | `src/compile-back/react-patch-emitter.ts`                                          | `ReactPatchEmitter`                                                                      | Rename/add-prop heuristic exists.                        | Minimal diffs, AST transforms, import/comment/residual preservation.                                       | `REPLACE`                                              | `DIRECT_CODE`    |
| Backend graph API      | `ArtifactGraphController.java`                                                     | `ingest`, `analyze`, `merge`, `query`, `analyzeResidual`                                 | Authenticated endpoints exist.                           | Principal-derived product/project scope and normalized request construction.                               | `MODIFY`                                               | `DIRECT_CODE`    |
| Backend graph service  | `ArtifactGraphServiceImpl.java`                                                    | `ingestGraph`, `analyzeGraph`, `queryGraph`, `parseSourceArtifact`                       | JGraphT analysis, merge, query, parse stubs exist.       | Real incremental upsert, pagination, executor requirement, no production stubs.                            | `MODIFY`                                               | `DIRECT_CODE`    |
| Backend persistence    | `ArtifactGraphRepository.java`                                                     | `saveNodes`, `saveEdges`, tombstone methods                                              | JDBC repository with tombstone-aware code exists.        | Migration columns are missing; pagination and snapshot queries incomplete.                                 | `MODIFY` + `ADD` migration                             | `DIRECT_CODE`    |
| DB migrations          | `V10__create_artifact_graph_tables.sql`, `V11__create_artifact_model_versions.sql` | Graph tables, model versions                                                             | Base graph and version tables exist.                     | Snapshot/version/tombstone/checksum columns missing from V10.                                              | `ADD` corrective migration                             | `DIRECT_CODE`    |
| Fastify source import  | `frontend/apps/api/src/routes/source-imports.ts`                                   | `/yappc/artifact/import-source`                                                          | Governed single-source import route exists.              | Durable jobs and repo source providers absent.                                                             | `CONSOLIDATE` / `REPLACE`                              | `DIRECT_CODE`    |

---

## Section C: Gap Analysis Against Target State

| Capability                  | Current state                                          | Evidence                                                            | Gap                                                                                                               | Severity | Required fix                                                                                        | Files impacted                                                                                               | Tests required                                                       |
| --------------------------- | ------------------------------------------------------ | ------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------- | -------: | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------- |
| Governed source acquisition | Partial library providers + single-file Fastify import | Source provider types and Fastify import route exist.               | No canonical `SourceLocator`, durable jobs, credential refs, retry/cancel/resume, tenant/workspace/project scope. |       P0 | Add governed source acquisition schema, job store, credential refs, scope context, and diagnostics. | `src/source-providers/types.ts`, `frontend/apps/api/src/routes/source-imports.ts`, new job store files       | Source provider scope, durable job lifecycle, credential redaction   |
| Deterministic inventory     | Partial                                                | Scanner exists.                                                     | Sequential, skipped reasons missing, binary checksum size-only, package boundary pnpm heuristic bug.              |       P0 | Fix scanner determinism, checksum, package system detection, skipped record capture.                | `src/inventory/scanner.ts`, `src/inventory/types.ts`, `src/inventory/index.ts`                               | Deterministic scan, binary checksum, skipped reasons, package system |
| Source-faithful graph       | Partial                                                | TS graph schema supports unresolved edges.                          | Resolved edges not graph-validated; backend proto stale.                                                          |       P0 | Add graph validator and align backend contract.                                                     | `src/graph/types.ts`, new `src/graph/validateGraph.ts`, `artifact_compiler.proto`                            | Invalid edge rejection, unresolved preservation                      |
| Semantic model              | Partial                                                | Broad model schema exists.                                          | Weak graph mapping, no diff/version schema.                                                                       |       P1 | Add graph mappings, model diffs, model version metadata.                                            | `src/model/types.ts`, `src/synthesis/pipeline.ts`                                                            | Provenance/graph mapping tests                                       |
| Residual islands            | Partial                                                | Residual schema exists.                                             | No checksum/raw ref/risk; preservation not enforced in emitters.                                                  |       P0 | Add residual fragment ref/checksum/risk and overlap enforcement.                                    | `src/residual/types.ts`, `src/compile-back/residual-preserver.ts`, `src/compile-back/react-patch-emitter.ts` | Residual preservation and no-overwrite tests                         |
| Compile-back                | Stub/partial                                           | Patch emitter uses placeholders/full-file hunk.                     | No true minimal patch generation, validation, rollback.                                                           |       P0 | Replace with AST/range-based emitters and review/validation bundle.                                 | `src/compile-back/*`                                                                                         | No-op zero diff, rename minimal patch, invalid patch rejection       |
| Backend APIs                | Partial                                                | Java graph endpoints wired.                                         | No source snapshot/import/patch endpoints.                                                                        |       P1 | Add snapshot/import/patch/re-scan API routes or consolidate Fastify/Java ownership.                 | `YappcHttpServer.java`, `ArtifactGraphController.java`, new controllers                                      | API contract tests                                                   |
| Backend persistence         | Unsafe                                                 | Repository expects columns absent in migration.                     | Runtime SQL mismatch.                                                                                             |       P0 | Add corrective migration and migration test.                                                        | New `V12__artifact_graph_snapshot_columns.sql`                                                               | Migration compatibility test                                         |
| Frontend UX                 | Partial                                                | ImportWizard exists from search; Fastify import is single-source.   | No full repo provider import/progress/residual/patch review flow.                                                 |       P1 | Wire provider import UI to durable job APIs and compiler result.                                    | `ImportWizard.tsx`, `ImportSourceWorkflow.ts`, `ImportOrchestrationService.ts`                               | E2E import/review/patch flow                                         |
| Extensibility               | Partial                                                | Extractor interface exists.                                         | No runtime capability registry for providers/extractors/emitters/validators.                                      |       P1 | Add capability registry and public discovery API.                                                   | new `src/capabilities/*`, `src/extractors/index.ts`, `src/source-providers/index.ts`                         | Capability discovery tests                                           |

---

## Section D: Architecture Decisions

### Decision 1: Canonical artifact compiler package ownership

* **Decision:** Keep TypeScript `products/yappc/frontend/libs/yappc-artifact-compiler` as the canonical source acquisition, inventory, extraction, synthesis, residual, and compile-back engine.
* **Current evidence:** The package already owns scanner, provider, graph, model, synthesis, residual, and compile-back modules. 
* **Why:** TypeScript Compiler API and frontend/page/canvas model integration belong here; Java backend should persist/query/authorize, not duplicate TS parsing.
* **Files to keep:** all current compiler package modules.
* **Files to modify:** package exports, scanner, providers, graph/model/residual/compile-back schemas.
* **Files to consolidate:** backend Java parser stubs should become explicit unsupported adapters or move to a Java extractor module.
* **Alternatives rejected:** making Java backend the canonical TS/JS parser, because current backend returns TS/JS stubs. 
* **Validation:** compiler package can run source → snapshot → inventory → graph → model → residuals → no-op patch fixture locally.

### Decision 2: Backend graph canonical ownership

* **Decision:** Java `yappc-services` owns authenticated graph persistence/query/analysis, but `knowledge-graph` must be inspected and either made a lower-level graph library or consolidated.
* **Current evidence:** `yappc-services` has graph controller/service/repository; search also found graph mapper/node/edge classes in `knowledge-graph`.   
* **Why:** Avoid duplicate graph models and split persistence/query semantics.
* **Files to modify:** `ArtifactGraphController.java`, `ArtifactGraphServiceImpl.java`, `ArtifactGraphRepository.java`.
* **Files to inspect/consolidate:** `products/yappc/core/knowledge-graph/src/main/java/com/ghatana/yappc/knowledge/artifact/ArtifactGraphMapper.java`, `YAPPCGraphNode.java`, `YAPPCGraphEdge.java`.
* **Alternatives rejected:** leaving both graph domains independently evolving.
* **Validation:** one canonical graph DTO/schema path is referenced by all APIs and persistence.

### Decision 3: Patch generation strategy

* **Decision:** Replace placeholder/full-file React patching with emitter-specific minimal patch generation using TypeScript AST/range transforms for TS/TSX and review-required unsupported emitters for everything else.
* **Current evidence:** React emitter has placeholder sync diffs and full-file replacement helper. 
* **Why:** Round-trip safety requires minimal, reviewable diffs.
* **Files affected:** `src/compile-back/react-patch-emitter.ts`, `src/compile-back/types.ts`, `src/compile-back/patch-coordinator.ts`.
* **Alternatives rejected:** full-file rewrites and placeholder diffs.
* **Validation:** no-op fixture produces zero diff; rename/add-prop fixtures produce minimal unified diff.

### Decision 4: Source provider security strategy

* **Decision:** Providers must accept a typed `SourceLocator` plus `credentialRef`; raw tokens in request options must be removed from production call paths.
* **Current evidence:** current `ProviderCredentials` allows raw token/username/password. 
* **Why:** Governance requires secrets not to enter logs/UI/payloads.
* **Files affected:** `src/source-providers/types.ts`, `github-provider.ts`, `gitlab-provider.ts`, Fastify/Java import routes.
* **Validation:** tests assert raw token is rejected or redacted and audit logs never contain secrets.

---

## Section E: Prescriptive File-by-File TODO Plan

| Priority | Phase | File path                                                                                                     | Action        | Current issue                                                                                                       | Required change                                                                                                            | Exact implementation details                                                                                               | Dependencies                      | Tests to add/modify                    | Validation command                                   | Acceptance criteria                                                              |
| -------: | ----- | ------------------------------------------------------------------------------------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | --------------------------------- | -------------------------------------- | ---------------------------------------------------- | -------------------------------------------------------------------------------- |
|       P0 | 1     | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/index.ts`                                 | MODIFY        | Scanner public API is not exported.                                                                                 | Export `scanRepository`, `ScannerConfig`, `DEFAULT_SCANNER_CONFIG`.                                                        | Add exports from `./scanner`; keep schema exports.                                                                         | none                              | `src/inventory/scanner.test.ts`        | `pnpm --filter yappc-artifact-compiler test`         | README import path works.                                                        |
|       P0 | 1     | `products/yappc/frontend/libs/yappc-artifact-compiler/package.json`                                           | MODIFY        | Public exports omit `./source-providers`, `./compile-back`, `./builder`.                                            | Add subpath exports for missing modules.                                                                                   | Add dist import/types entries for each missing module.                                                                     | build output                      | `src/__tests__/public-exports.test.ts` | `pnpm --filter yappc-artifact-compiler build`        | All documented subpath imports compile.                                          |
|       P0 | 1     | `src/inventory/types.ts`                                                                                      | MODIFY        | No skipped file list/reason schema.                                                                                 | Add `SkippedArtifactSchema` and `skippedArtifacts` to inventory.                                                           | Fields: `relativePath`, `reason`, `sizeBytes?`, `matchedPattern?`, `source`, `detectedAt`.                                 | scanner                           | scanner tests                          | same                                                 | Oversized, excluded, gitignored, unreadable files are reported with reasons.     |
|       P0 | 1     | `src/inventory/scanner.ts`                                                                                    | MODIFY        | Binary checksum uses file size only.                                                                                | Compute SHA-256 for binary content or stream.                                                                              | Replace `stats.size.toString(16)` with content/stream SHA-256.                                                             | none                              | binary checksum test                   | same                                                 | Two same-size binaries produce different checksums.                              |
|       P0 | 1     | `src/inventory/scanner.ts`                                                                                    | MODIFY        | `detectNpmSystem` heuristic incorrectly returns pnpm.                                                               | Implement real ancestor file existence check.                                                                              | Make package boundary detection async-aware; check actual `pnpm-workspace.yaml`, `yarn.lock`, `package-lock.json`.         | fs/promises                       | package boundary test                  | same                                                 | npm package outside pnpm workspace is `npm`; inside pnpm workspace is `pnpm`.    |
|       P0 | 1     | `src/inventory/scanner.ts`                                                                                    | MODIFY        | Scan output order is filesystem-dependent.                                                                          | Sort directory entries and artifacts deterministically.                                                                    | Sort `entries` by name before walking; sort final artifacts/skippedArtifacts by `relativePath`.                            | none                              | deterministic scan test                | same                                                 | Repeated scan of same fixture returns stable artifact order and IDs.             |
|       P0 | 1     | `src/source-providers/zip-provider.ts`                                                                        | MODIFY        | Zip-slip path traversal risk.                                                                                       | Enforce extraction path containment.                                                                                       | Normalize resolved output path and reject if it does not start with `tempRoot + path.sep`; record skipped unsafe entries.  | path                              | zip-slip test                          | same                                                 | Archive entry `../evil.ts` is rejected and not written.                          |
|       P0 | 1     | `src/source-providers/gitlab-provider.ts`                                                                     | MODIFY        | Returned `localRootPath` can differ from file materialization roots.                                                | Create temp root once before loop and use it for all files and response.                                                   | Move `tempRoot` creation before pagination loop; remove second temp root creation.                                         | none                              | GitLab provider materialization test   | same                                                 | Every materialized file absolute path is under returned `localRootPath`.         |
|       P0 | 1     | `src/source-providers/github-provider.ts`                                                                     | MODIFY        | Truncated GitHub tree only warns and continues.                                                                     | Fail closed unless explicit `allowTruncatedSnapshot` is true.                                                              | Add option; if truncated and not allowed, throw `SourceProviderError` with diagnostic.                                     | provider options                  | GitHub truncated tree test             | same                                                 | Truncated tree does not produce silently incomplete snapshot.                    |
|       P0 | 1     | `src/source-providers/types.ts`                                                                               | MODIFY        | Raw credentials and string locator only.                                                                            | Add `SourceLocatorSchema`, `credentialRef`, `SourceScopeContext`, `ProviderDiagnostic`.                                    | Replace production credential use with `credentialRef`; keep raw credentials test-only/internal if needed.                 | governance secret provider        | provider schema tests                  | same                                                 | Providers receive typed locators and never expose raw token in diagnostics.      |
|       P0 | 2     | `src/graph/types.ts`                                                                                          | MODIFY        | Query schema requires UUID node IDs.                                                                                | Allow artifact URNs and UUIDs.                                                                                             | Replace `.uuid()` with artifact node ID string refinement; add `validateArtifactGraph(graph)`.                             | none                              | graph query schema test                | same                                                 | Valid deterministic URN node IDs pass query schema.                              |
|       P0 | 2     | `src/graph/validateGraph.ts`                                                                                  | ADD           | Resolved edge target/source existence not enforced.                                                                 | Add graph validation utility.                                                                                              | Validate every edge source/target exists; unresolved edges not in resolved edge table; duplicate IDs rejected.             | graph types                       | graph validation test                  | same                                                 | Invalid resolved edge fails validation.                                          |
|       P0 | 2     | `src/synthesis/symbol-resolver.ts`                                                                            | MODIFY        | Resolver lacks relative import and alias semantics.                                                                 | Add `ReferenceIndex` with path, export, basename, tsconfig alias, package hint resolution.                                 | Resolve `./Button`, `./Button.tsx`, `./Button/index`, path aliases; classify external deps separately.                     | inventory import/export summaries | symbol resolver tests                  | same                                                 | Relative imports resolve to graph node IDs.                                      |
|       P0 | 2     | `src/synthesis/pipeline.ts`                                                                                   | MODIFY        | Returns loose model elements, not full semantic model.                                                              | Return `SemanticProductModel` container with version, indices, residual IDs, graph mappings.                               | Add `semanticModel` to result; keep loose arrays temporarily deprecated.                                                   | model types                       | pipeline fixture test                  | same                                                 | Pipeline output contains complete model with provenance and residual references. |
|       P0 | 2     | `src/residual/types.ts`                                                                                       | MODIFY        | Residual lacks checksum/raw fragment/risk.                                                                          | Add `rawFragmentRef`, `rawFragmentChecksum`, `risk`, `relatedGraphNodeIds`.                                                | Deprecate `placeholder-stub` or gate behind explicit unsupported mode.                                                     | residual preserver                | residual schema tests                  | same                                                 | Residuals preserve raw source identity and risk.                                 |
|       P0 | 3     | `src/compile-back/types.ts`                                                                                   | MODIFY        | Patch model lacks required lifecycle objects.                                                                       | Add `ModelChange`, `ChangePlan`, `FilePatch`, `ReviewBundle`, `ValidationResult`, `RollbackMetadata`.                      | Keep existing `ChangeOp` as internal/legacy; add migration adapters.                                                       | patch coordinator                 | compile-back type tests                | same                                                 | Patch API can express review, validation, rollback metadata.                     |
|       P0 | 3     | `src/compile-back/react-patch-emitter.ts`                                                                     | REPLACE       | Placeholder diffs and full-file replacement.                                                                        | Implement AST/range-based minimal unified diffs.                                                                           | Support safe rename and prop add/update/remove with exact source ranges; unsupported ops return review-required failure.   | TypeScript compiler API           | no-op, rename, add-prop tests          | same                                                 | No-op zero diff; rename is minimal hunk, not full-file rewrite.                  |
|       P0 | 3     | `src/compile-back/residual-preserver.ts`                                                                      | MODIFY        | Preservation utility not integrated with patch spans.                                                               | Add overlap detection and hard block patching residual spans.                                                              | `assertPatchDoesNotOverlapResiduals(filePath, patchRanges, residuals)`.                                                    | residual types                    | residual overlap test                  | same                                                 | Patch overlapping residual is rejected or review-required.                       |
|       P0 | 3     | `src/compile-back/patch-coordinator.ts`                                                                       | MODIFY        | Console errors and no validation/review bundle.                                                                     | Return structured failures and validation result.                                                                          | Replace `console.error`; collect `PatchFailure[]`, run validators, create `ReviewBundle`.                                  | compile-back types                | coordinator tests                      | same                                                 | Emitter failure appears in result, not console-only.                             |
|       P0 | 4     | `products/yappc/core/yappc-services/src/main/resources/db/migration/V12__artifact_graph_snapshot_columns.sql` | ADD           | Repository expects columns absent in V10.                                                                           | Add `content_checksum`, `snapshot_id`, `version_id`, `is_tombstone`, indexes.                                              | Also add edge snapshot/version/tombstone indexes.                                                                          | DB migration order                | migration test                         | `./gradlew :products:yappc:core:yappc-services:test` | Repository SQL matches DB schema.                                                |
|       P0 | 4     | `ArtifactGraphController.java`                                                                                | MODIFY        | Tenant is checked but not normalized into request when body tenant is null; product/project ownership not enforced. | Build scoped request from principal/resource registry.                                                                     | Reject body tenant/product/project mismatch; pass principal-derived tenant and authorized product/project to service.      | auth/resource registry            | controller scope tests                 | gradle test                                          | Body scope manipulation cannot affect tenant/project.                            |
|       P0 | 4     | `ArtifactGraphServiceImpl.java`                                                                               | MODIFY        | Default executor is `Runnable::run`; query runs on event loop; ingest tombstones all rows.                          | Require blocking executor and implement true snapshot incremental upsert.                                                  | Remove public constructors that default to direct executor; use diff by snapshot/checksum; paginate queries.               | repository diff APIs              | service integration tests              | gradle test                                          | Large graph analysis/query never runs on event loop.                             |
|       P0 | 4     | `ArtifactGraphRepository.java`                                                                                | MODIFY        | No cursor/offset pagination and schema mismatch.                                                                    | Add paginated node/edge queries and snapshot diff methods.                                                                 | `findNodesByProduct(product, tenant, pageToken, limit)`, `findEdgesByProduct(...); findBySnapshotId`.                      | V12 migration                     | repository tests                       | gradle test                                          | Query returns page tokens and excludes tombstones by default.                    |
|       P1 | 4     | `artifact_compiler.proto`                                                                                     | REPLACE       | Proto is stale vs TS graph schema.                                                                                  | Align proto with SnapshotRef, SourceLocation, confidence, provenance, residuals, unresolved edges, pagination, patch APIs. | Add messages: `RepositorySnapshot`, `GraphNode`, `ResolvedGraphEdge`, `UnresolvedGraphEdge`, `ResidualIsland`, `PatchSet`. | TS schema                         | proto compatibility tests              | gradle generateProto/test                            | Backend and TS contracts share equivalent fields.                                |
|       P1 | 5     | `frontend/apps/api/src/routes/source-imports.ts`                                                              | CONSOLIDATE   | Single-source in-memory import route duplicates provider pipeline.                                                  | Route through canonical source provider + synthesis job service.                                                           | Replace `sourceImportJobs` Map with durable job repository; support GitHub/GitLab/local/archive locators.                  | provider layer, job store         | Fastify route tests                    | pnpm test                                            | Import jobs survive restart and expose progress.                                 |
|       P1 | 5     | `frontend/web/src/services/compiler/ImportSourceWorkflow.ts`                                                  | MODIFY        | Likely workflow integration must move beyond file import.                                                           | Add source provider, snapshot, inventory, graph, model, residual, patch states.                                            | Use job API and typed DTOs; remove single-file assumptions.                                                                | backend/Fastify API               | service tests                          | pnpm test                                            | Workflow can represent repo import and residual summary.                         |
|       P1 | 5     | `frontend/web/src/components/canvas/page/ImportWizard.tsx`                                                    | MODIFY        | UI is likely import-wizard level but not full lifecycle.                                                            | Add provider selector, repo/ref/archive fields, progress, summary, residuals, skipped files, confidence.                   | Keep low cognitive load; show unsupported states clearly.                                                                  | import workflow service           | Playwright import flow                 | pnpm e2e                                             | User can import repo and review summary.                                         |
|       P1 | 5     | `frontend/web/src/services/canvas/ImportOrchestrationService.ts`                                              | MODIFY        | Canvas import must connect to semantic model and graph.                                                             | Use `SemanticProductModel` and residual references from pipeline.                                                          | Map model elements to canvas/page builder with provenance.                                                                 | synthesis result                  | integration tests                      | pnpm test                                            | Canvas shows model with confidence and residual warnings.                        |
|       P1 | 6     | `src/capabilities/capability-registry.ts`                                                                     | ADD           | No runtime capability discovery.                                                                                    | Add provider/extractor/emitter/validator registry.                                                                         | Expose support level: `production`, `preview`, `unsupported`, confidence, languages/frameworks.                            | existing contracts                | capability tests                       | pnpm test                                            | UI/API can list supported providers/extractors/emitters.                         |
|       P2 | 6     | `products/yappc/docs/architecture/ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md`                               | MODIFY        | Architecture docs may overstate current implementation.                                                             | Update to reflect current status and canonical plan.                                                                       | Separate implemented/partial/planned sections.                                                                             | code map                          | doc lint                               | docs check                                           | Docs match code and no longer imply full round-trip readiness.                   |
|       P2 | 6     | `products/yappc/docs/archive/ARTIFACT_COMPILER_PROGRESS.md`                                                   | KEEP_ARCHIVED | Archived progress exists.                                                                                           | Keep archived; do not use as source of truth.                                                                              | Add note pointing to current architecture/status doc.                                                                      | none                              | docs check                             | docs check                                           | Archived doc cannot be confused with current status.                             |

---

## Section F: Phase Plan With Executable Outcomes

### Phase 1: Foundation Hardening

**Goal:** Make repository inventory and source snapshots trustworthy.

**Objective current blocker:** Scanner/provider code exists but has P0 determinism, checksum, package-boundary, source-provider, and ZIP/GitLab/GitHub safety gaps.

**Files in scope**

* `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/index.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/types.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/types.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/github-provider.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/gitlab-provider.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/zip-provider.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/package.json`

**Tasks**

1. Export scanner API publicly.
2. Add skipped artifact schema and skip reasons.
3. Fix binary checksum and package boundary detection.
4. Make scan ordering deterministic.
5. Fix GitLab materialization root.
6. Fail closed on GitHub tree truncation.
7. Add ZIP path containment guard.
8. Add provider diagnostics and typed source locator.

**Validation**

* `pnpm --filter yappc-artifact-compiler test`
* Add fixture tests for deterministic scan, package boundaries, ZIP slip, GitLab materialization, GitHub truncation.

**Exit criteria**

* Same fixture scanned twice gives stable IDs/order/checksums.
* Unsafe ZIP cannot write outside temp root.
* GitHub truncated tree cannot silently produce partial snapshot.
* Public package imports work.

---

### Phase 2: Canonical Repository IR and Graph Resolution

**Goal:** Produce valid source-faithful artifact graphs with explicit unresolved references.

**Files in scope**

* `src/graph/types.ts`
* `src/graph/validateGraph.ts`
* `src/synthesis/symbol-resolver.ts`
* `src/synthesis/pipeline.ts`
* `src/extractors/types.ts`
* `src/extractors/index.ts`

**Tasks**

1. Add graph validator.
2. Fix query schema for URN IDs.
3. Implement reference index with relative import, extension, index, alias support.
4. Expose extractor instances and capabilities.
5. Make pipeline validate graph before returning.

**Validation**

* `pnpm --filter yappc-artifact-compiler test`
* Tests for no fake resolved edges and unresolved edge preservation.

**Exit criteria**

* Every resolved edge references existing node IDs.
* Every unresolved/cross-repo/ambiguous reference is explicit and reviewable.

---

### Phase 3: Semantic Model and Residual Fidelity

**Goal:** Build a complete YAPPC semantic model with provenance, graph mapping, confidence, and residual preservation.

**Files in scope**

* `src/model/types.ts`
* `src/residual/types.ts`
* `src/synthesis/pipeline.ts`
* `src/provenance/*`

**Tasks**

1. Add `graphNodeIds`, `sourceRefs`, and `residualIslandIds` to model base.
2. Add model diff/version metadata.
3. Add residual `rawFragmentRef`, checksum, risk, and related graph node IDs.
4. Ensure low-confidence residuals include raw source span/content reference, not only path.

**Validation**

* Model synthesis fixture tests.
* Residual checksum/source span tests.

**Exit criteria**

* Every model element maps back to graph/source evidence.
* Unsupported source is preserved as residual debt, not dropped.

---

### Phase 4: Backend Production Hardening

**Goal:** Make graph persistence/API contracts match compiler data and enforce scope.

**Files in scope**

* `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`
* `ArtifactGraphController.java`
* `ArtifactGraphServiceImpl.java`
* `ArtifactGraphRepository.java`
* `V12__artifact_graph_snapshot_columns.sql`
* `YappcHttpServer.java`

**Tasks**

1. Add missing DB migration columns.
2. Enforce principal-derived tenant/project/product scope.
3. Remove direct executor defaults.
4. Add cursor pagination.
5. Align proto with TS schema.
6. Add snapshot/import/patch route ownership decision.

**Validation**

* `./gradlew :products:yappc:core:yappc-services:test`
* Migration compatibility tests.
* Tenant isolation tests.

**Exit criteria**

* Repository SQL matches migrations.
* Body scope manipulation fails.
* Large graph query and analysis are off event loop.

---

### Phase 5: Compile-Back and Patch Generation

**Goal:** Make supported model edits produce minimal, validated, reviewable patch sets.

**Files in scope**

* `src/compile-back/types.ts`
* `src/compile-back/patch-coordinator.ts`
* `src/compile-back/react-patch-emitter.ts`
* `src/compile-back/residual-preserver.ts`

**Tasks**

1. Add full patch lifecycle types.
2. Replace placeholder/full-file emitter.
3. Add validation and review bundle.
4. Enforce residual overlap blocking.
5. Add rollback metadata.

**Validation**

* No-op round-trip fixture test.
* Rename/add-prop minimal diff tests.
* Residual overlap rejection tests.

**Exit criteria**

* No-op round-trip produces zero diff.
* Supported edits produce minimal unified diffs.
* Unsupported edits become review-required, not fake patches.

---

### Phase 6: UX and Continuous Evolution

**Goal:** Provide no-cognitive-load import → review → edit → patch → re-scan UX.

**Files in scope**

* `frontend/apps/api/src/routes/source-imports.ts`
* `frontend/web/src/services/compiler/ImportSourceWorkflow.ts`
* `frontend/web/src/services/canvas/ImportOrchestrationService.ts`
* `frontend/web/src/components/canvas/page/ImportWizard.tsx`

**Tasks**

1. Replace single-source import with durable provider job flow.
2. Show provider/ref/progress/summary.
3. Show understood vs residual vs skipped source.
4. Open semantic model in canvas with provenance/confidence.
5. Add patch review and validation result UI.
6. Add drift detection/re-scan state.

**Validation**

* Playwright E2E import/edit/patch/re-scan.
* API contract tests for import job state.

**Exit criteria**

* User can import a repo, review model/residuals/skips, make supported edit, review patch, and re-scan.

---

## Section G: Cleanup and Consolidation Plan

| Priority | Path                                                             | Current problem                                                      | Evidence                                                          | Action                   | Target canonical path                      | Safe removal/consolidation steps                                                                  | Tests/validation               |
| -------: | ---------------------------------------------------------------- | -------------------------------------------------------------------- | ----------------------------------------------------------------- | ------------------------ | ------------------------------------------ | ------------------------------------------------------------------------------------------------- | ------------------------------ |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/README.md` | Overstates round-trip readiness and documents broken import path.    | README claims round-trip and `scanRepository` subpath import.     | MODIFY                   | same                                       | Update status table: implemented/partial/planned; fix imports after barrel export.                | docs lint + public import test |
|       P0 | `src/compile-back/react-patch-emitter.ts`                        | Placeholder sync diffs and full-file replacement hunk.               | Current emitter code.                                             | REPLACE                  | same                                       | Replace with AST/range emitter; preserve old only in tests if needed.                             | compile-back tests             |
|       P0 | `ArtifactGraphServiceImpl.parseSourceArtifact`                   | Returns production stub nodes for unsupported languages.             | Backend service returns `unparsed://` stub.                       | DEPRECATE_BEHIND_FLAG    | TS compiler package                        | Mark unsupported explicitly or route TS/JS to compiler pipeline; no fake production parse result. | parser unsupported test        |
|       P0 | `V10__create_artifact_graph_tables.sql` + repository             | DB schema and repository SQL mismatch.                               | Repository columns absent in migration.                           | ADD corrective migration | `V12__artifact_graph_snapshot_columns.sql` | Add missing columns/indexes; do not edit applied V10.                                             | migration test                 |
|       P1 | `frontend/apps/api/src/routes/source-imports.ts`                 | In-memory single-source import duplicates source provider direction. | Route uses `sourceImportJobs` Map and HTTPS/artifact file fetch.  | CONSOLIDATE              | canonical source acquisition job service   | Keep route temporarily as adapter to new job service; remove Map after durable store exists.      | route tests                    |
|       P1 | `products/yappc/core/knowledge-graph/...`                        | Potential duplicate graph model.                                     | Search found graph mapper/node/edge in knowledge graph.           | INSPECT + CONSOLIDATE    | decided backend graph package              | Determine whether it is library or duplicate; update imports.                                     | architecture boundary test     |
|       P2 | `products/yappc/docs/archive/*`                                  | Archived progress docs can be mistaken for source of truth.          | Archived compiler progress exists.                                | KEEP_ARCHIVED + NOTE     | current architecture/status doc            | Add banner: archived, not current source of truth.                                                | docs check                     |

---

## Section H: Test Plan

| Priority | Test type   | Test file path                                                                              | Scenario                        | Fixture/data                                | Expected assertion                                  | Fails today because                                | Passes after                 |
| -------: | ----------- | ------------------------------------------------------------------------------------------- | ------------------------------- | ------------------------------------------- | --------------------------------------------------- | -------------------------------------------------- | ---------------------------- |
|       P0 | Unit        | `src/inventory/scanner.test.ts`                                                             | Deterministic scan              | `fixtures/small-react-app`                  | Same artifact IDs/order/checksums across runs       | Ordering and timestamps not normalized             | Sorted deterministic output  |
|       P0 | Unit        | `src/inventory/scanner.test.ts`                                                             | Binary checksum                 | two same-size different binaries            | Different SHA-256 checksums                         | Binary checksum is file size                       | Content hash                 |
|       P0 | Unit        | `src/inventory/scanner.test.ts`                                                             | Package system detection        | npm package outside pnpm workspace          | `system: npm`                                       | heuristic returns pnpm                             | real ancestor manifest check |
|       P0 | Unit        | `src/source-providers/zip-provider.test.ts`                                                 | ZIP slip                        | archive with `../evil.ts`                   | Provider rejects unsafe entry                       | no containment guard                               | normalized containment guard |
|       P0 | Unit        | `src/source-providers/gitlab-provider.test.ts`                                              | Materialization root            | mocked GitLab API                           | all file paths under `localRootPath`                | temp root created per file and returned separately | single temp root             |
|       P0 | Unit        | `src/source-providers/github-provider.test.ts`                                              | Truncated tree                  | mocked `truncated: true`                    | provider throws unless explicitly allowed           | currently warns/continues                          | fail closed                  |
|       P0 | Unit        | `src/graph/validateGraph.test.ts`                                                           | No fake resolved edges          | graph edge target missing                   | validation fails                                    | no graph-level validator                           | validator rejects            |
|       P0 | Unit        | `src/synthesis/symbol-resolver.test.ts`                                                     | Relative import resolution      | `Button.tsx`, `Card.tsx` imports `./Button` | target resolves to Button node ID                   | exact label/path only                              | reference index              |
|       P0 | Integration | `src/synthesis/pipeline.test.ts`                                                            | Semantic model provenance       | small React fixture                         | every element has graph node/source mapping         | loose model elements only                          | full model container         |
|       P0 | Unit        | `src/residual/residual-preservation.test.ts`                                                | Residual source preservation    | unsupported code span                       | checksum/ref retained                               | residual lacks raw fragment checksum               | residual schema enhanced     |
|       P0 | Unit        | `src/compile-back/noop-roundtrip.test.ts`                                                   | No-op round-trip                | unchanged semantic model                    | zero patches/diff                                   | patch lifecycle incomplete                         | no-op returns zero diff      |
|       P0 | Unit        | `src/compile-back/react-patch-emitter.test.ts`                                              | Rename component                | TSX fixture                                 | minimal unified diff only changes symbol refs       | placeholder/full-file diff                         | AST/range emitter            |
|       P0 | Integration | `products/yappc/core/yappc-services/src/test/.../ArtifactGraphRepositoryMigrationTest.java` | Repository schema compatibility | migrated DB                                 | saveNodes/saveEdges succeed                         | missing columns                                    | V12 migration                |
|       P0 | API         | `ArtifactGraphControllerScopeTest.java`                                                     | Tenant/project scope            | body tenant mismatch/null                   | mismatch rejected; null body gets principal tenant  | request can pass null to service                   | normalized scoped request    |
|       P1 | E2E         | `frontend/web/e2e/artifact-import-review.spec.ts`                                           | Import review UX                | small React repo                            | progress, summary, residuals, skipped files visible | current route is single-source                     | durable import workflow      |
|       P1 | E2E         | `frontend/web/e2e/artifact-patch-review.spec.ts`                                            | Patch review UX                 | rename component                            | diff, validation, apply/reject visible              | no patch review contract                           | patch review workflow        |

---

## Section I: Critical Questions — Direct Answers

### 1. Is the current system truly round-trip capable?

**Answer:** No.

**Evidence**

* Compile-back exists but React sync emitter returns placeholder comments and async emitter handles only limited rename/add-prop with full-file replacement diff helper. 
* Patch types lack validation, rollback, review bundle, and broad model change support. 

**Required fix:** Replace compile-back with typed `ModelChange → ChangePlan → PatchSet → ValidationResult → ReviewBundle → RollbackMetadata` flow.

### 2. Can it scan a full GitHub repo today, or only import individual sources/files?

**Answer:** Partial.

**Evidence**

* GitHub provider can resolve commit SHA and recursive tree into a snapshot. 
* Fastify source import route is single-source and only supports `tsx`, `route`, `storybook`, `artifact`, `zip`. 

**Required fix:** Connect GitHub/GitLab/ZIP/local providers to a durable server-side import job and pipeline API.

### 3. Are artifact IDs deterministic?

**Answer:** Partial.

**Evidence**

* Deterministic URNs are generated only when `snapshotRef` is present; otherwise random UUID is used. 

**Required fix:** Require snapshotRef for source-derived artifacts and reserve random UUID only for manual/user-created model artifacts.

### 4. Are graph edges valid and resolved?

**Answer:** Partial.

**Evidence**

* Unresolved/resolved edge types exist. 
* Resolver produces resolved edges and resolution records. 

**Required fix:** Add graph-level validation that every resolved edge points to existing node IDs and that unresolved references never enter resolved edge storage.

### 5. Is there a complete synthesis pipeline?

**Answer:** Partial.

**Evidence**

* `SynthesisPipeline` implements scan → extract → resolve → assemble and returns graph/model elements/residuals. 
* It does not return a complete `SemanticProductModel` container.

**Required fix:** Add `semanticModel` output with indices, versioning, graph mappings, residual IDs, and provenance index.

### 6. Is compile-back/patch generation implemented?

**Answer:** Partial / unsafe.

**Evidence**

* Compile-back package exists. 
* React emitter is placeholder/full-file/limited. 

**Required fix:** Replace with AST/range-based patch emitters and validation.

### 7. Are residual islands preserved?

**Answer:** Partial.

**Evidence**

* Residual schema and preserver utility exist.  
* Emitter does not enforce residual span overlap protection.

**Required fix:** Add raw fragment checksum/ref and patch-overlap blocking.

### 8. Are source import jobs durable?

**Answer:** No.

**Evidence**

* Fastify route stores jobs in in-memory `Map`. 

**Required fix:** Add durable job persistence with retry/cancel/resume/progress events.

### 9. Is tenant/workspace/project scope enforced consistently?

**Answer:** Partial.

**Evidence**

* Java controller requires principal and rejects mismatched body tenant. 
* Fastify route validates tenant/workspace/project headers and projectId match. 

**Required fix:** Normalize principal-derived scope into service requests and validate project/product ownership server-side.

### 10. Is backend artifact graph logic in the right canonical module?

**Answer:** Partial / unknown.

**Evidence**

* `yappc-services` has controller/service/repository.  
* Search found graph artifacts in `knowledge-graph`. 

**Required fix:** Inspect `knowledge-graph` fully and consolidate ownership.

### 11. Are there duplicate or conflicting implementations?

**Answer:** Partial / likely.

**Evidence**

* Fastify source import and compiler source providers both address source import but at different maturity levels.  
* Java backend graph DTO/proto conflicts with TS graph schema.  

**Required fix:** Make compiler TS schema canonical; align backend proto/API/persistence.

### 12. What is the smallest milestone that makes the foundation trustworthy?

**Answer:** Milestone 1: Stable Repository IR and Source Snapshot Compiler.

**Required files first**

* `src/inventory/index.ts`
* `src/inventory/types.ts`
* `src/inventory/scanner.ts`
* `src/source-providers/types.ts`
* `src/source-providers/github-provider.ts`
* `src/source-providers/gitlab-provider.ts`
* `src/source-providers/zip-provider.ts`
* `package.json`

### 13. What files must be changed first?

**Answer:** The P0 foundation files above plus backend migration mismatch.

**Backend first file**

* `products/yappc/core/yappc-services/src/main/resources/db/migration/V12__artifact_graph_snapshot_columns.sql`

### 14. What tests must fail today and pass after implementation?

**Answer:** ZIP slip, GitLab localRootPath, GitHub truncation, binary checksum, package boundary, graph edge validation, no-op round-trip, React minimal patch, DB migration compatibility, tenant scope normalization.

### 15. What legacy/stale/deprecated code or docs must be removed or consolidated?

**Answer:** README status claims, Java parser stubs, Fastify in-memory source import flow, backend proto DTOs, potential knowledge-graph duplication, archived progress docs confusion.

---

## Section J: Definition of Done

Implementation is complete only when:

1. Repo source can be imported as immutable snapshot.
2. Source provider locators are typed, scoped, audited, and use credential references.
3. Inventory is deterministic and includes skipped reasons.
4. Binary/generated/vendor/large-file handling is explicit.
5. Artifact IDs are deterministic for all source-derived artifacts.
6. Resolved graph edges are validated against node IDs.
7. Unresolved references are explicit and reviewable.
8. Semantic model has graph/source provenance and confidence.
9. Residual islands include raw fragment references/checksums and are patch-protected.
10. Supported model edits produce minimal patches.
11. No-op round-trip produces zero diff.
12. Patch sets include validation, review bundle, and rollback metadata.
13. Backend schema matches repository SQL.
14. Backend APIs enforce principal-derived tenant/workspace/project/product scope.
15. Import jobs are durable, observable, retryable, cancelable, and resumable.
16. UI shows provider import, progress, summary, skipped files, residuals, confidence, validation, and patch review.
17. Golden fixture tests pass.
18. E2E import/edit/patch/re-scan passes.
19. Stale/duplicate/deprecated code paths are removed, consolidated, or feature-flagged as unsupported.
20. No production code emits fake placeholder patches or fake parsed nodes as successful production behavior.

---