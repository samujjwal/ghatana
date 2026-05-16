# Artifact Compiler/Decompiler Audit Report

Target repo: `samujjwal/ghatana`
Target commit: `dfebf19f158be07c1623132eb5c00fc652ce57ff`
Audit basis: static repository inspection at the target commit. I did **not** run a local build or test suite.

The target commit exists and is a merge commit titled “Merge branch 'main' of [https://github.com/samujjwal/ghatana.”](https://github.com/samujjwal/ghatana.”) 

---

## Section A: Executive Summary

The Artifact Compiler/Decompiler is **not production-ready yet**, but it has meaningful building blocks.

The strongest current implementation is in the TypeScript package `products/yappc/frontend/libs/yappc-artifact-compiler`, which exports inventory, graph, source providers, compile-back, model, provenance, residual, extractors, synthesis, merge, and builder modules. 

The Java backend has artifact graph APIs, DTOs, persistence, graph analysis, pagination, tombstone support, and partial residual handling. However, it is still primarily a graph ingestion/query service rather than a canonical durable source-provider → snapshot → inventory → graph → semantic model → patch pipeline.

### Current capability classification

| Capability         |                Classification | Summary                                                                                                                                                              |
| ------------------ | ----------------------------: | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Source acquisition |          **Partial / TS-led** | TS has source providers and repo import route. Java has durable job table but no canonical Java-side source provider/job orchestration service wired end-to-end.     |
| Inventory          | **Partial / TS repo-capable** | TS scanner supports deterministic mode, `.gitignore`, package boundaries, checksums, generated/binary detection, but has scalability and canonical-ownership issues. |
| Graph              |   **Partial / split TS+Java** | TS has richer graph schema; Java persists graph nodes/edges, but persistence is lossy and unresolved/residual fields are not fully integrated.                       |
| Semantic model     |          **Partial / TS-led** | TS synthesis pipeline builds semantic model and residuals; Java lacks canonical semantic model persistence/API.                                                      |
| Compile-back       |              **Stub/partial** | TS has types, patch coordinator, limited React emitter, but patch application appends diffs as text and is not round-trip safe.                                      |
| Round-trip safety  |                        **No** | No-op round-trip zero-diff is not proven; compile-back/apply is unsafe for production.                                                                               |
| Overall            |      **Not production-ready** | Good foundation, but runtime ownership, persistence fidelity, source snapshot durability, patch correctness, and testing must be hardened.                           |

### Biggest blockers

1. **Runtime ownership is inverted/mixed**: TypeScript Fastify route currently orchestrates repository import and synthesis, while Java has the durable backend tables and graph APIs.
2. **Compile-back is not production-safe**: `applyPatch` appends unified diff text to the file instead of applying the diff. 
3. **Java graph persistence loses fidelity**: DTOs contain source location, extractor metadata, confidence, provenance, flags, residual IDs, sourceRef, symbolRef, edge metadata, snapshot/version fields, but repository mapping persists/returns only a subset and maps many fields to `null`.
4. **Unresolved edges/residuals exist as schemas/methods but are not wired into ingest**: `ArtifactGraphIngestRequest` carries unresolved edges, resolution records, and residual IDs, and repository has save methods, but `ArtifactGraphServiceImpl.ingestGraph` only upserts nodes/edges.
5. **Source snapshots are not durable enough**: GitHub provider materializes files into temp storage and schedules cleanup after resolve unless `keepTempFiles` is true; the consuming route then runs synthesis from that snapshot. This risks deleting the snapshot before or during later processing.
6. **Project/workspace scope is not fully canonical in Java**: Java controller resolves tenant from principal, but product/project comes from payload and comments identify resource-registry resolution as an integration target. 

Recommended next milestone: **Milestone 1 — Stable Repository IR and Source Snapshot Compiler**, with Java as durable orchestrator and TypeScript as TS/TSX extractor/patch worker.

---

## Section B: Objective Current Status

| Area                           | Status                      | Evidence                                                                                                                                        | Objective conclusion                                                                | Production impact                                             |
| ------------------------------ | --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| Source provider abstraction    | `PARTIALLY_IMPLEMENTED`     | TS defines `SourceProvider`, `SourceProviderRegistry`, `SourceLocator`, `RepositorySnapshot`, credential policy, and default provider registry. | Exists in TS, not canonical Java backend.                                           | Durable governed backend import is split.                     |
| GitHub source acquisition      | `PARTIALLY_IMPLEMENTED`     | TS GitHub provider resolves commit SHA, recursive tree, materializes blobs, fails on truncated tree.                                            | Functionally present, but temp cleanup and Java ownership are wrong for production. | Snapshot may disappear; backend cannot resume/retry reliably. |
| GitLab/local/archive providers | `PARTIALLY_IMPLEMENTED`     | Default registry registers LocalFolder, Archive, Zip, GitHub, GitLab.                                                                           | Present in TS package; not audited deeply file-by-file.                             | Provider capability exists but not durable/governed by Java.  |
| Durable import jobs            | `DUPLICATED_OR_CONFLICTING` | Java migration creates `source_import_jobs`; TS route uses separate job repository/status workflow.                                             | Durable DB schema exists, but TS route is doing orchestration separately.           | Operational split and status mismatch risk.                   |
| Repository snapshot            | `PARTIALLY_IMPLEMENTED`     | TS `RepositorySnapshotSchema` includes snapshotRef, localRootPath, files, diagnostics.                                                          | Good TS schema, but no Java durable snapshot table/service.                         | Cannot guarantee replay/resume/audit.                         |
| Inventory scanner              | `PARTIALLY_IMPLEMENTED`     | TS scanner supports config, deterministic mode, `.gitignore`, generated/binary classification, package boundaries.                              | Useful scanner exists but should be Java-orchestrated for production jobs.          | Large repo scan reliability and ownership unclear.            |
| Artifact graph schema          | `PARTIALLY_IMPLEMENTED`     | TS graph schema supports nodes, edges, unresolved edges, resolution records; Java DTOs mirror several fields.                                   | Schema exists but split and not fully persisted.                                    | Graph cannot be trusted as source-faithful durable IR.        |
| Resolved edge validity         | `PARTIALLY_IMPLEMENTED`     | Java controller validates edge source/target IDs exist within the ingest request; TS schema separates unresolved and resolved edges.            | Basic validation exists, but no canonical symbol index lifecycle in Java.           | Cross-snapshot and incremental resolution weak.               |
| Semantic product model         | `PARTIALLY_IMPLEMENTED`     | TS pipeline assembles semantic model from extraction results and residuals.                                                                     | Present in TS pipeline; Java persistence/version/API incomplete.                    | UI/backend may drift from canonical state.                    |
| Residual islands               | `PARTIALLY_IMPLEMENTED`     | TS pipeline creates residuals; Java service can analyze residuals; Java repository has save method.                                             | Residual concepts exist but are not durable end-to-end.                             | Unsupported source may be dropped or invisible.               |
| Compile-back                   | `UNSAFE_FOR_PRODUCTION`     | Patch types/coordinator exist, React emitter only handles rename/add-prop, and `applyPatch` appends diff text.                                  | Not true compile-back.                                                              | Round-trip edits unsafe.                                      |
| Backend graph APIs             | `PARTIALLY_IMPLEMENTED`     | Java controller exposes ingest/analyze/merge/query/residual analyze.                                                                            | Useful but incomplete source compiler API.                                          | Cannot operate full lifecycle.                                |
| Backend persistence            | `PARTIALLY_IMPLEMENTED`     | Java repository persists graph nodes/edges, pagination, snapshot diff, unresolved/residual methods.                                             | Persistence exists but loses DTO fidelity.                                          | Provenance/confidence/residual trust is weak.                 |
| Scope enforcement              | `PARTIALLY_IMPLEMENTED`     | Controller requires principal and rejects tenant mismatch, but project/product is payload-based.                                                | Tenant is improved; workspace/project still incomplete.                             | Cross-project risk remains.                                   |
| Frontend/source import UX API  | `PARTIALLY_IMPLEMENTED`     | TS route returns review-required import payload and job state.                                                                                  | API exists but should not own durable production orchestration.                     | UX can progress, but backend canonical path missing.          |
| Migration health               | `DUPLICATED_OR_CONFLICTING` | V11 and V14 both add snapshot/content/tombstone columns/indexes to artifact graph tables.                                                       | Idempotent SQL reduces runtime failure, but migration intent is duplicated.         | Maintenance confusion and repeated audit noise.               |

---

## Section C: Evidence-Based Current Code Map

| Capability area              | Current file/module                                                 | What exists                                                                                   | Missing / weak                                                                   | Decision                                                                       |
| ---------------------------- | ------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Artifact compiler TS package | `products/yappc/frontend/libs/yappc-artifact-compiler/src/index.ts` | Broad exports for compiler modules.                                                           | Runtime ownership not separated.                                                 | Keep as TS worker/library, not durable backend owner.                          |
| Source providers             | `src/source-providers/*`                                            | Provider registry, typed locator, GitHub/GitLab/local/zip/archive exports.                    | Java durable provider orchestration missing.                                     | Split: Java canonical orchestration, TS provider code only for worker/dev use. |
| GitHub provider              | `src/source-providers/github-provider.ts`                           | Commit pinning, tree fetch, blob materialization.                                             | Temp cleanup lifecycle unsafe; no durable snapshot store.                        | Modify immediately.                                                            |
| Inventory                    | `src/inventory/scanner.ts`                                          | Classification, checksums, `.gitignore`, package boundaries.                                  | Scanner queue cleanup/scalability issue; Java job ownership missing.             | Keep TS scanner as worker; Java orchestrates.                                  |
| Graph schema                 | `src/graph/types.ts`                                                | Strong TS graph model with unresolved/resolution records.                                     | Java schema/persistence not equivalent.                                          | Generate/align contracts.                                                      |
| Synthesis pipeline           | `src/synthesis/pipeline.ts`                                         | Scan→extract→resolve→graph→model pipeline.                                                    | Runs in TS route, not Java durable job.                                          | Move orchestration boundary to Java.                                           |
| Compile-back                 | `src/compile-back/*`                                                | Types, coordinator, React emitter, apply/rollback surface.                                    | Patch application is placeholder; emitter is regex and partial.                  | Keep TS emitters; replace apply path.                                          |
| Java graph API               | `ArtifactGraphController.java`                                      | ingest/analyze/merge/query/residual endpoints with principal tenant checks.                   | Project/workspace registry check incomplete; no source import pipeline endpoint. | Modify.                                                                        |
| Java graph service           | `ArtifactGraphServiceImpl.java`                                     | Upsert graph, graph analysis on blocking executor, query, residual analysis, parser dispatch. | Does not persist unresolved/residual payload; parser fallback incomplete.        | Modify/split.                                                                  |
| Java graph repository        | `ArtifactGraphRepository.java`                                      | JDBC persistence, tombstones, pagination, diff, residual methods.                             | Loses DTO fields and edge metadata on read.                                      | Modify with migration.                                                         |
| DB migrations                | `V10`, `V11`, `V14`                                                 | Graph tables, job table, snapshot/tombstone columns.                                          | Duplicate migration intent; missing repository snapshot/patch review tables.     | Add new migrations; consolidate docs.                                          |
| Source import route          | `frontend/apps/api/src/routes/source-imports.ts`                    | Governed TS Fastify import route, headers, review-required response.                          | Owns orchestration that should be Java canonical.                                | Replace/proxy/flag.                                                            |

---

## Section D: Gap Analysis Against Target State

| Capability                | Current state                          | Gap                                                     | Severity | Required fix                                                                                                       |
| ------------------------- | -------------------------------------- | ------------------------------------------------------- | -------: | ------------------------------------------------------------------------------------------------------------------ |
| Canonical source provider | TS-only canonical                      | Durable backend job orchestration missing               |       P0 | Add Java `SourceProvider`, `SourceLocator`, `RepositorySnapshot`, and provider registry; TS becomes worker/helper. |
| Immutable snapshot        | TS temp snapshot                       | Snapshot cleanup and no durable store                   |       P0 | Add Java `repository_snapshots` table/service; store snapshot manifest and content refs.                           |
| Source import jobs        | Java table + TS job route              | Split job status and ownership                          |       P0 | Java owns job lifecycle; TS route becomes proxy or disabled.                                                       |
| Graph fidelity            | Rich DTOs, lossy DB                    | Source/provenance/confidence fields null on read        |       P0 | Extend DB schema and repository mapping to persist every DTO field.                                                |
| Unresolved edges          | TS schema + Java DTO field             | Not saved during ingest                                 |       P0 | Wire `ArtifactGraphServiceImpl.ingestGraph` to save unresolved edges/resolution records/residuals transactionally. |
| Compile-back              | Partial TS patch path                  | Appends diff text; regex-only React emitter             |       P0 | Replace apply with real unified diff/AST patch application; TS emitter uses TypeScript Compiler API.               |
| Scope enforcement         | Tenant enforced, project payload-based | Workspace/project from request body still trusted       |       P0 | Resolve project/workspace from principal/resource registry; reject payload scope.                                  |
| Scanner scale             | TS concurrency config                  | Queue cleanup issue and JS job memory growth            |       P1 | Fix scanner concurrency queue; Java controls bounded worker execution.                                             |
| Migrations                | V11/V14 duplicate snapshot changes     | Confusing schema evolution                              |       P2 | Add migration audit test; document V11/V14 compatibility; avoid new duplicate DDL.                                 |
| UX import/review          | Review-required response exists        | No end-to-end patch review/apply/re-scan backed by Java |       P1 | Add frontend UX over Java import job, graph summary, residuals, patch review APIs.                                 |

---

## Section E: Architecture Decisions

### Decision 1: Java owns durable production orchestration

Java should own source import jobs, repository snapshot persistence, graph persistence, semantic model versioning, validation orchestration, governance, patch review/apply, rollback, audit, and long-running CPU-heavy graph analysis. Current Java already uses a blocking executor for JGraphT-heavy analysis, which is the right direction. 

### Decision 2: TypeScript owns TS/TSX compiler intelligence and frontend UX

TypeScript should own React/TSX extraction, route/component/style/token extraction, TS patch emission, frontend import UX, diff preview, and generated API clients. TS already has the richest compiler package and TS-native pipeline.

### Decision 3: Use a hybrid Java-orchestrated TypeScript worker

The Java backend should call a TypeScript extractor/patch worker through a typed contract. The TS worker should not own durable state, job status, tenant scope, governance, or canonical persistence.

### Decision 4: Canonical graph contracts must be shared/generated

TS graph schemas are richer than Java DB mapping. Java and TS must share generated contracts, preferably through `artifact_compiler.proto` plus generated TS types or a single schema generation path.

### Decision 5: Compile-back is not allowed to apply patches until real patch application exists

`applyPatch` must be treated as unsafe because it appends the diff to the file. It must be replaced before any apply/merge path is enabled. 

---

## Section F: Prescriptive File-by-File TODO Plan

| Priority | Phase | File path                                                                                                             | Action   | Required change                                                                                                                                                                                                                       | Tests                                         |
| -------- | ----: | --------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------- |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`                                           | ADD      | Define canonical `SourceLocator`, `RepositorySnapshot`, `SnapshotFile`, `ArtifactNode`, `ArtifactEdge`, `UnresolvedEdge`, `EdgeResolutionRecord`, `ResidualIsland`, `SemanticProductModel`, `ChangePlan`, `PatchSet`, `ReviewBundle`. | Contract generation test Java↔TS.             |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceLocator.java`               | ADD      | Java record matching canonical proto; no raw credentials; only `credentialRef`.                                                                                                                                                       | Unit parse/validation tests.                  |
| P0       |     1 | `.../services/source/RepositorySnapshot.java`                                                                         | ADD      | Durable snapshot manifest with provider, repoId, commitSha/archive checksum, local/cache refs, file manifest, diagnostics.                                                                                                            | Snapshot immutability test.                   |
| P0       |     1 | `.../services/source/SourceProvider.java`                                                                             | ADD      | Java provider interface: `canHandle`, `resolve`, `capabilities`; accepts scoped principal context.                                                                                                                                    | Provider contract test.                       |
| P0       |     1 | `.../services/source/SourceProviderRegistry.java`                                                                     | ADD      | Register Java providers and expose capabilities.                                                                                                                                                                                      | Capability discovery test.                    |
| P0       |     2 | `.../services/source/GitHubSourceProvider.java`                                                                       | ADD      | Resolve ref to commit SHA, fail closed on truncated/partial snapshot, persist snapshot manifest.                                                                                                                                      | GitHub commit pinning test.                   |
| P0       |     2 | `.../services/source/LocalFolderSourceProvider.java`                                                                  | ADD      | Trusted runtime-only local folder provider; disallow arbitrary browser/user path access.                                                                                                                                              | Path traversal test.                          |
| P0       |     2 | `.../services/source/ArchiveSourceProvider.java`                                                                      | ADD      | ZIP/tar import with zip-slip protection, max file/size limits, checksum.                                                                                                                                                              | Zip-slip and max-size tests.                  |
| P0       |     2 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V15__create_repository_snapshots.sql`             | ADD      | Create `repository_snapshots`, `repository_snapshot_files`, provider diagnostics, immutable commit/archive refs.                                                                                                                      | Migration test.                               |
| P0       |     2 | `ArtifactGraphController.java`                                                                                        | MODIFY   | Stop accepting project/product scope as authority; resolve workspace/project from principal/resource registry; reject request-body manipulation.                                                                                      | Tenant/workspace/project isolation API tests. |
| P0       |     2 | `ArtifactGraphServiceImpl.java`                                                                                       | MODIFY   | In `ingestGraph`, persist `request.unresolvedEdges`, `edgeResolutionRecords`, and `residualIslandIds`; stop extracting snapshot metadata from node/edge properties when request fields exist.                                         | Ingest persistence test.                      |
| P0       |     2 | `ArtifactGraphRepository.java`                                                                                        | MODIFY   | Persist and read all DTO fields: sourceLocation, extractorId/version, confidence, provenance, privacy flags, residualFragmentIds, sourceRef, symbolRef, edgeId, edge confidence, edge metadata, snapshot/version.                     | Round-trip repository mapping test.           |
| P0       |     2 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V16__harden_artifact_graph_fidelity.sql`          | ADD      | Add missing graph fidelity columns and constraints/FKs; add unresolved/residual project/tenant columns if absent.                                                                                                                     | DB migration + repository integration tests.  |
| P0       |     2 | `products/yappc/frontend/apps/api/src/routes/source-imports.ts`                                                       | MODIFY   | Put legacy TS import route behind `artifactCompiler.legacyTsImportApi.enabled`; default route proxies to Java import job API.                                                                                                         | API route contract test.                      |
| P0       |     2 | `products/yappc/frontend/apps/api/src/services/job-repository.ts`                                                     | MODIFY   | Remove or make read-only/proxy after Java job service is canonical; align statuses with Java enum.                                                                                                                                    | Status compatibility test.                    |
| P0       |     2 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/github-provider.ts`                        | MODIFY   | Do not auto-delete temp snapshot before consumer finalizes; make cleanup explicit via snapshot lease/finalizer.                                                                                                                       | Snapshot cleanup lifecycle test.              |
| P0       |     3 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java` | ADD      | Java orchestrator: source provider → snapshot → inventory worker → extraction worker → graph ingest → semantic model.                                                                                                                 | End-to-end local fixture compile test.        |
| P0       |     3 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`                              | ADD      | TS worker CLI/RPC entrypoint that accepts canonical snapshot/inventory and returns graph/model/residual JSON.                                                                                                                         | Worker contract fixture test.                 |
| P1       |     3 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`                                       | MODIFY   | Fix concurrency queue cleanup; make scanner memory bounded for large repos; record vendor classification explicitly.                                                                                                                  | Large repo scanner test.                      |
| P0       |     4 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/apply-patch.ts`                                | REPLACE  | Replace diff append behavior with real unified-diff parser/application or remove apply path from TS and delegate application to Java.                                                                                                 | Patch apply modifies exact range test.        |
| P0       |     4 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/react-patch-emitter.ts`                        | MODIFY   | Replace regex mutations with TypeScript Compiler API transformations for implemented ops; mark unsupported ops as manual-review.                                                                                                      | Rename/add-prop AST golden tests.             |
| P0       |     4 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/patch/PatchReviewService.java`           | ADD      | Java patch review lifecycle: validate, create review bundle, approve/reject, apply, rollback.                                                                                                                                         | Patch review API tests.                       |
| P0       |     4 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V17__create_patch_review_tables.sql`              | ADD      | Add `change_plans`, `patch_sets`, `file_patches`, `review_bundles`, `rollback_metadata`.                                                                                                                                              | Migration and repository tests.               |
| P1       |     5 | `products/yappc/frontend/web/src/services/compiler/*`                                                                 | MODIFY   | Use generated Java-backed API clients for import jobs, progress, graph summary, residuals, patch review.                                                                                                                              | Frontend integration tests.                   |
| P1       |     5 | `products/yappc/frontend/web/src/components/canvas/page/*`                                                            | MODIFY   | Show understood/partial/skipped/residual state and patch review requirements.                                                                                                                                                         | Playwright E2E import/review test.            |
| P2       |     6 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V11__create_source_import_jobs.sql`               | DOCUMENT | Keep because already applied; document V11/V14 overlap.                                                                                                                                                                               | Migration audit test.                         |
| P2       |     6 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V14__add_snapshot_tracking_to_artifact_graph.sql` | DOCUMENT | Keep idempotent migration; do not add duplicate future DDL.                                                                                                                                                                           | Migration audit test.                         |
| P2       |     6 | `platform/comp-decomp-todo.md`                                                                                        | MOVE     | Archive or replace with link to canonical architecture/implementation plan to avoid stale TODO loops.                                                                                                                                 | Docs lint.                                    |

---

## Section G: Phase Plan

### Phase 1: Foundation hardening

Goal: establish canonical contracts and runtime ownership.

Files:

* `artifact_compiler.proto`
* Java `services/source/*`
* TS generated API/types package

Validation:

* Java/TS generated contract compatibility.
* No raw provider credentials accepted.
* Contract includes unresolved edges, residuals, provenance, confidence, review requirements.

Exit criteria:

* Java and TS use the same contract definitions.
* TypeScript compiler package no longer defines production-only contracts independently.

### Phase 2: Source provider and snapshot layer

Goal: Java owns durable source acquisition.

Files:

* `GitHubSourceProvider.java`
* `LocalFolderSourceProvider.java`
* `ArchiveSourceProvider.java`
* `V15__create_repository_snapshots.sql`
* `source-imports.ts`

Validation:

* GitHub commit pinning test.
* Archive zip-slip test.
* Snapshot immutability test.
* TS route proxies to Java.

Exit criteria:

* Import job can resume from durable snapshot manifest.
* Snapshot files are not deleted before pipeline completion.

### Phase 3: Canonical compile pipeline

Goal: Java orchestrates, TS extracts.

Files:

* `ArtifactCompileJobService.java`
* `ts-extractor-worker.ts`
* `ArtifactGraphServiceImpl.java`
* `ArtifactGraphRepository.java`

Validation:

* Fixture compile from `small-react-app`.
* Graph has valid resolved edges.
* Unresolved references saved separately.
* Residuals visible and persisted.

Exit criteria:

* Snapshot → inventory → extraction → graph → model runs under one Java job ID.

### Phase 4: Compile-back and patch generation

Goal: safe model edit → patch set.

Files:

* `apply-patch.ts`
* `react-patch-emitter.ts`
* `PatchReviewService.java`
* `V17__create_patch_review_tables.sql`

Validation:

* No-op round-trip zero diff.
* Rename component minimal diff.
* Add prop minimal diff.
* Residual overlap rejected.
* Checksum mismatch rejected.

Exit criteria:

* No patch apply path appends diff text.
* Every patch is reviewable, validated, and rollback-capable.

### Phase 5: UX and continuous evolution

Goal: no-cognitive-load import/review/re-scan UX.

Files:

* `frontend/web/src/services/compiler/*`
* `frontend/web/src/components/canvas/page/*`
* source import UI components

Validation:

* Playwright E2E: import → summary → residual review → patch review → reject/apply → re-scan.
* Empty/error/unauthorized/loading states.

Exit criteria:

* Users see source status, confidence, residuals, skipped files, validation, and required review.

---

## Section H: Cleanup and Consolidation Plan

| Priority | Path                                                                                            | Problem                                                                          | Action                                                                   |
| -------- | ----------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| P0       | `products/yappc/frontend/apps/api/src/routes/source-imports.ts`                                 | TS API owns source import orchestration that should be Java durable backend.     | Convert to Java API proxy or feature-flag legacy route.                  |
| P0       | `products/yappc/frontend/apps/api/src/services/job-repository.ts`                               | Separate TS job repository/status path conflicts with Java `source_import_jobs`. | Consolidate behind Java job API.                                         |
| P0       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/apply-patch.ts`          | Placeholder patch application appends diff text.                                 | Replace or disable apply path.                                           |
| P1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/*`                   | Providers are TS-owned production acquisition layer.                             | Keep for worker/dev; Java owns durable provider registry.                |
| P1       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/parser/*` | Java parser routing is embedded in graph service.                                | Move to `services/compiler/extractors/java`, keep Java parser as plugin. |
| P2       | `V11__create_source_import_jobs.sql` and `V14__add_snapshot_tracking_to_artifact_graph.sql`     | Duplicate snapshot/tombstone column additions.                                   | Keep idempotent migrations, add migration audit and documentation.       |
| P2       | `platform/comp-decomp-todo.md`                                                                  | Stale TODO artifact likely to cause repeated work.                               | Move to archive or replace with canonical implementation plan.           |

---

## Section I: Test Plan

| Priority | Test type        | Test file path                                                  | Scenario                                | Expected assertion                                           |
| -------- | ---------------- | --------------------------------------------------------------- | --------------------------------------- | ------------------------------------------------------------ |
| P0       | Java unit        | `.../services/source/SourceLocatorTest.java`                    | Parse GitHub/local/archive locators     | Invalid locators rejected; credentialRef only.               |
| P0       | Java integration | `.../services/source/GitHubSourceProviderTest.java`             | Resolve repo branch to commit snapshot  | Snapshot has commit SHA and immutable file manifest.         |
| P0       | Java integration | `.../api/ArtifactGraphControllerScopeTest.java`                 | Body project/tenant mismatch            | Request rejected; principal/resource registry wins.          |
| P0       | Java integration | `.../storage/ArtifactGraphRepositoryFidelityTest.java`          | Save/read node/edge with full metadata  | No DTO field returns as unexpected null.                     |
| P0       | Java integration | `.../services/artifact/ArtifactGraphIngestPersistenceTest.java` | Ingest unresolved edges/residuals       | Records saved and queryable.                                 |
| P0       | TS unit          | `src/source-providers/github-provider.test.ts`                  | Snapshot cleanup lifecycle              | Temp files remain until finalizer/lease release.             |
| P0       | TS unit          | `src/compile-back/apply-patch.test.ts`                          | Apply unified diff                      | File content changes exactly; diff text not appended.        |
| P0       | TS golden        | `src/compile-back/react-patch-emitter.test.ts`                  | Rename component and add prop           | Minimal diff; TypeScript parses after patch.                 |
| P0       | End-to-end       | `tests/e2e/artifact-compiler/import-edit-patch-rescan.spec.ts`  | Import → model → edit → patch → re-scan | No-op round-trip zero diff; edit produces minimal patch.     |
| P1       | Performance      | `.../ArtifactGraphLargeQueryTest.java`                          | Large graph query                       | Cursor pagination, no event-loop blocking.                   |
| P1       | TS unit          | `src/inventory/scanner.test.ts`                                 | Large fixture scan                      | Bounded memory queue, deterministic sorted output.           |
| P1       | Security         | `.../SourceImportSecurityTest.java`                             | Archive zip-slip and secret detection   | Unsafe archive rejected; secrets redacted from logs/results. |

---

## Section J: Definition of Done

1. Java owns durable import jobs, source snapshots, graph persistence, semantic model versions, patch review, validation, governance, apply, and rollback.
2. TypeScript owns frontend UX and TS/TSX-native extraction/patch emission only.
3. GitHub repo import produces immutable, commit-pinned `RepositorySnapshot`.
4. Inventory is deterministic and `.gitignore` aware.
5. Graph nodes/edges preserve source location, extractor, confidence, provenance, privacy/security flags, residual refs, sourceRef, symbolRef, edge metadata.
6. Resolved edges use graph node IDs only.
7. Unresolved references are stored separately and re-resolvable.
8. Residual islands are durable, visible, and preserved during patch generation.
9. No-op round-trip produces zero diff.
10. Supported model edits produce minimal validated patches.
11. Patch application does not append diff text.
12. All import/graph/model/patch endpoints enforce principal-derived tenant/workspace/project scope.
13. Import/edit/patch/re-scan E2E test passes on golden fixtures.
14. Legacy TS import API is removed, proxied, or feature-flagged off by default.

---

## Section K: Java vs TypeScript Ownership Plan

| Capability                     | Current location        | Recommended owner                                  | Why                                                  |
| ------------------------------ | ----------------------- | -------------------------------------------------- | ---------------------------------------------------- |
| Source provider abstraction    | TS                      | `JAVA_CANONICAL` + generated TS contract           | Durable governed backend ownership required.         |
| GitHub provider                | TS                      | `JAVA_CANONICAL`                                   | Commit-pinned snapshots must be resumable/auditable. |
| GitLab provider                | TS                      | `JAVA_CANONICAL`                                   | Same as GitHub.                                      |
| Local folder provider          | TS                      | `JAVA_CANONICAL`                                   | Trusted runtime access must be server-controlled.    |
| Archive provider               | TS                      | `JAVA_CANONICAL`                                   | Needs zip-slip/security/size enforcement.            |
| Import job lifecycle           | Java DB + TS route      | `JAVA_CANONICAL`                                   | Single durable job state.                            |
| Repository snapshot            | TS schema               | `JAVA_CANONICAL`                                   | Must persist snapshot manifest.                      |
| Inventory scanner              | TS                      | `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`               | TS scanner useful, Java should own job execution.    |
| TS/TSX extractor               | TS                      | `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`               | TS Compiler API is best for TS/TSX.                  |
| React component extractor      | TS                      | `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`               | React AST fidelity in TS ecosystem.                  |
| Java extractor                 | Java                    | `JAVA_CANONICAL`                                   | JavaParser/OpenRewrite belong Java-side.             |
| Artifact graph schema          | TS + Java DTO           | `CONTRACT_ONLY`                                    | Generate both sides from canonical schema/proto.     |
| Graph persistence              | Java                    | `JAVA_CANONICAL`                                   | Durable backend state.                               |
| Symbol/reference index         | TS pipeline             | `JAVA_CANONICAL` with TS worker input              | Resolution must be durable and queryable.            |
| Semantic model synthesis       | TS                      | `JAVA_CANONICAL` orchestration, TS helpers allowed | Backend must version/persist model.                  |
| Patch generation               | TS partial              | `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`               | Java governs; TS emits TS patches.                   |
| Patch validation/apply         | TS partial              | `JAVA_CANONICAL`                                   | Apply/rollback is governed backend operation.        |
| Frontend import UX             | TS                      | `TYPESCRIPT_CANONICAL`                             | UI responsibility.                                   |
| Patch review UX                | TS                      | `TYPESCRIPT_CANONICAL`                             | UI responsibility.                                   |
| Observability/audit/governance | TS route + Java partial | `JAVA_CANONICAL`                                   | Must be server-side and durable.                     |
| Generated API clients          | TS                      | `TYPESCRIPT_CANONICAL`                             | Generated from Java/proto contracts.                 |

---

## Section L: Critical Questions

### 1. Is the current system truly round-trip capable?

Answer: **No**

Evidence: compile-back types and coordinator exist, but `applyPatch` appends diff text to file content, and React emitter only implements limited regex-based rename/add-prop logic.

Required fix: replace patch application and TSX patch emitters before enabling apply.

### 2. Can it scan a full GitHub repo today, or only import individual sources/files?

Answer: **Partial**

Evidence: TS GitHub provider resolves commit/tree/blobs and TS source import route runs `SynthesisPipeline.runFromSnapshot`.

But it is not Java-durable, and provider temp cleanup is unsafe for production.

### 3. Are artifact IDs deterministic?

Answer: **Partial**

Evidence: TS graph and inventory use deterministic URN helpers when `snapshotRef` is present.

But Java accepts IDs from payload and does not enforce deterministic generation.

### 4. Are graph edges valid and resolved?

Answer: **Partial**

Evidence: TS has separate unresolved and resolved edge schemas; Java controller checks edge endpoints exist within ingest request.

But Java lacks durable symbol/reference index lifecycle.

### 5. Is there a complete synthesis pipeline?

Answer: **Partial**

Evidence: TS `SynthesisPipeline` implements scan, extract, resolve, graph assembly, semantic model assembly, residuals. 

But Java does not own durable orchestration or persistence of the full pipeline.

### 6. Is compile-back/patch generation implemented?

Answer: **Partial / unsafe**

Evidence: Patch types/coordinator exist, React emitter is limited, and apply is placeholder/unsafe.

### 7. Are residual islands preserved?

Answer: **Partial**

Evidence: TS pipeline creates residuals and Java repository has save method, but ingest service does not wire them transactionally.

### 8. Are source import jobs durable?

Answer: **Partial / conflicting**

Evidence: Java migration creates `source_import_jobs`, while TS route creates and updates jobs through a TS repository.

### 9. Is tenant/workspace/project scope enforced consistently?

Answer: **Partial**

Evidence: Java controller requires principal and rejects tenant mismatch, but project/product is still read from payload and comments identify resource registry resolution as future integration. 

### 10. What is the smallest trustworthy milestone?

Answer: **Stable Repository IR and Source Snapshot Compiler**

It must include Java-owned source provider abstraction, durable snapshot, deterministic inventory, graph fidelity persistence, unresolved edge lifecycle, residual preservation, and TS worker boundary.

---

## Section M: Recommended First Milestone

### Milestone 1: Stable Repository IR and Source Snapshot Compiler

Deliver:

1. Java canonical `SourceProvider` abstraction.
2. Java GitHub provider with commit pinning.
3. Java local folder provider.
4. Java archive provider.
5. Durable `RepositorySnapshot`.
6. Durable source import job service.
7. TS extractor worker invoked by Java.
8. Deterministic inventory output.
9. Full-fidelity graph persistence.
10. Resolved/unresolved edge split.
11. Residual island persistence.
12. Basic semantic model version persistence.
13. Golden fixture tests.
14. No-op round-trip test scaffold marked failing until compile-back phase.

---

## Section N: Prioritized TODO Checklist

### P0

* [x] Make Java the canonical source import/job/snapshot orchestrator. (implemented 2026-05-16)

  * Files:

    * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/*`
    * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java`
    * `products/yappc/core/yappc-services/src/main/resources/db/migration/V15__create_repository_snapshots.sql`
  * Done when: GitHub/local/archive imports run under one durable Java job ID.
  * Test: `./gradlew :products:yappc:core:yappc-services:compileJava -x test`.
  * Progress: Added concrete providers + default `SourceProviderRegistry`, process-backed TS extractor worker adapter (`YAPPC_TS_EXTRACTOR_WORKER_CMD`), durable `SourceImportJobService` wiring, and Java import endpoints (`POST/GET /api/v1/yappc/artifact/import-source`) that execute `ArtifactCompileJobService` under durable job IDs.

* [x] Persist full graph fidelity. (implemented 2026-05-16)

  * Files:

    * `ArtifactGraphRepository.java`
    * `ArtifactGraphServiceImpl.java`
    * `V16__harden_artifact_graph_fidelity.sql`
  * Done when: every node/edge DTO field round-trips through DB.
  * Test: repository fidelity test.

* [x] Wire unresolved edges and residual islands into ingest. (implemented 2026-05-16)

  * Files:

    * `ArtifactGraphServiceImpl.java`
    * `ArtifactGraphRepository.java`
  * Done when: ingest saves nodes, edges, unresolved edges, resolution records, residual links transactionally.
  * Test: ingest persistence test.

* [x] Replace unsafe patch application. (implemented 2026-05-16)

  * Files:

    * `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/apply-patch.ts`
    * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/patch/PatchReviewService.java`
  * Done when: no path appends diff text to source.
  * Test: no-op and minimal patch golden tests.

* [x] Enforce workspace/project scope server-side. (implemented 2026-05-16)

  * Files:

    * `ArtifactGraphController.java`
    * Java resource registry integration files
  * Done when: body scope manipulation cannot read/write another project.
  * Test: `./gradlew :products:yappc:core:yappc-services:compileJava -x test`.
  * Progress: Ingest/analyze/merge/query/residual now all require `X-Workspace-ID` + `X-Project-ID` and reject payload/header scope mismatches across tenant/project checks.

### P1

* [x] Convert TS source import API to Java proxy/legacy flag. (implemented 2026-05-16)

  * File: `products/yappc/frontend/apps/api/src/routes/source-imports.ts`
  * Done when: default production flow uses Java import job API.
  * Test: route proxy contract test.

* [x] Fix TS scanner bounded concurrency. (implemented 2026-05-16)

  * File: `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`
  * Done when: large repo scan memory does not grow with all files.
  * Test: `pnpm vitest run src/inventory/scanner.test.ts`.

* [x] Replace React regex patching with TypeScript Compiler API. (implemented 2026-05-16)

  * File: `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/react-patch-emitter.ts`
  * Done when: supported TSX edits are AST/range-safe.
  * Test: `pnpm vitest run src/compile-back/react-patch-emitter.test.ts`.

### P2

* [x] Document and stop repeating duplicate migration intent. (implemented 2026-05-16)

  * Files:

    * `V11__create_source_import_jobs.sql`
    * `V14__add_snapshot_tracking_to_artifact_graph.sql`
  * Done when: migration audit explains why both remain and future DDL is centralized.
  * Test: migration audit test.

* [x] Archive stale TODO docs. (implemented 2026-05-16)

  * File: `platform/comp-decomp-todo.md`
  * Done when: stale TODOs no longer compete with canonical architecture/plan.
  * Test: docs lint.

### P3

* [x] Normalize naming from `productId` to `projectId` where artifact graph scope actually means project. (implemented 2026-05-16)

  * Files:

    * `ArtifactGraphController.java`
    * `ArtifactGraphService.java`
    * `ArtifactGraphServiceImpl.java`
    * `ArtifactRequestScope.java`
    * `ArtifactGraphIngestRequest.java`
    * `ArtifactGraphAnalysisRequest.java`
    * `ArtifactGraphMergeRequest.java`
  * Done when: artifact graph API and service contract consistently use `projectId` for project scope and reject payload-body scope drift.
  * Test: `./gradlew :products:yappc:core:yappc-services:compileJava -x test`.

### Validation Update (2026-05-16)

* [x] Artifact compiler package test health is fully green after compatibility and contract fixes.

  * Scope: scanner classification, residual/schema contracts, model mapping/schema contracts, graph query/validation contracts, compile-back emitter/coordinator compatibility, GitLab provider matcher.
  * Test: `pnpm vitest run` in `products/yappc/frontend/libs/yappc-artifact-compiler`.
  * Result: 32/32 test files passed, 262/262 tests passed.
