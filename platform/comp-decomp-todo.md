# Artifact Compiler/Decompiler Audit Report — `samujjwal/ghatana`

Target commit audited: `42d9daef08790834371ac0a93e9b16cd86a1cd0f`

The target commit itself is a changelog-only commit, so this audit treats it as the repository snapshot point and audits the codebase state at that SHA, not the changelog diff. 

---

## A. Executive Summary

### Current maturity verdict

The current Artifact Compiler/Decompiler is **foundation-present but not production-ready**.

It has meaningful building blocks:

* Java backend routes for import jobs and artifact graph operations are wired in `YappcHttpServer`.
* Java has `SourceLocator`, `RepositorySnapshot`, `SourceProvider`, `GitHubSourceProvider`, `LocalFolderSourceProvider`, and `ArchiveSourceProvider`.
* Java has a durable `source_import_jobs` table and repository.
* Java has graph ingest/query/analyze/merge/residual APIs.
* TypeScript has a richer artifact compiler library with source providers, graph schema, semantic model schema, extractors, synthesis, residuals, and compile-back/patch generation.
* TypeScript compile-back can produce patch sets and preserve residuals.

But the system is **not truly round-trip safe** yet.

### Current capability classification

```text
Source acquisition: partial repo-capable, not production-grade
Inventory: partial provider-level file listing, not canonical deterministic inventory
Graph: partial graph-capable, with persistence and analysis, but scope/query/residual gaps
Semantic model: TypeScript-side model-capable, not clearly backend-canonical
Compile-back: partial TypeScript-side patch-capable, not backend-integrated round-trip
Overall: not production-ready; foundation-ready after P0 fixes
```

### Biggest blockers

| Blocker                                                                                                                                     | Severity | Why it matters                                                                     |
| ------------------------------------------------------------------------------------------------------------------------------------------- | -------: | ---------------------------------------------------------------------------------- |
| Workspace scope is missing in repository pagination and snapshot diff queries                                                               |       P0 | Can leak graph data across workspaces under same tenant/project.                   |
| Snapshot IDs are random in Java providers                                                                                                   |       P0 | Breaks deterministic scan, stable artifact IDs, no-op round-trip, drift detection. |
| Local/archive checksums ignore file content in critical places                                                                              |       P0 | Same-size content changes can be missed.                                           |
| Unknown file types can silently produce empty graph output                                                                                  |       P0 | Violates source fidelity and residual island requirements.                         |
| Java and TypeScript both implement source providers/import orchestration                                                                    |       P1 | Creates conflicting runtime ownership and inconsistent contracts.                  |
| Java import orchestration depends on a `TsExtractorWorker` interface, but a concrete Java sidecar runner was not verified in inspected code |       P1 | Import pipeline may be structurally defined but not reliably executable.           |
| Compile-back exists mostly on TypeScript side and is not exposed through Java durable/governed backend routes                               |       P1 | No true governed source → model → patch → validation → review → apply loop.        |

---

## B. Objective Current Status

| Area                                          | Status                    | Evidence                                                                                                                                                                          | Objective conclusion                                  | Production impact                                                                                       |
| --------------------------------------------- | ------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| Java import endpoint                          | PARTIALLY_IMPLEMENTED     | `YappcHttpServer` wires `POST /api/v1/yappc/artifact/import-source` and job status route.                                                                                         | Java backend import API exists.                       | Good foundation, but job execution/cancel/resume and status scoping need hardening.                     |
| Java graph endpoints                          | PARTIALLY_IMPLEMENTED     | `YappcHttpServer` wires graph ingest/analyze/merge/query/residual routes.                                                                                                         | API surface exists.                                   | Needs query correctness, scope consistency, residual fidelity, typed DTOs.                              |
| Tenant/project validation in graph controller | PARTIALLY_IMPLEMENTED     | `ArtifactGraphController` derives tenant from `Principal`, workspace/project from headers, rejects tenant/project mismatch, and validates edge endpoints during ingest.           | Controller-level scope enforcement is improved.       | Repository layer still has workspace leaks.                                                             |
| Java source provider abstraction              | IMPLEMENTED               | `SourceProvider` defines provider contract and `ScopeContext`.                                                                                                                    | Canonical Java interface exists.                      | Should be production owner.                                                                             |
| Java source provider registry                 | PARTIALLY_IMPLEMENTED     | Default registry registers GitHub, local-folder, and archive providers.                                                                                                           | Registry exists but omits GitLab.                     | GitLab source type is supported in TS but not Java canonical runtime.                                   |
| Java `SourceLocator`                          | PARTIALLY_IMPLEMENTED     | Includes provider, repoId, ref, path, credentialRef, tenant/workspace/project.                                                                                                    | Core locator exists.                                  | Needs provider enum/validation and credential governance integration.                                   |
| Java `RepositorySnapshot`                     | PARTIALLY_IMPLEMENTED     | Snapshot includes snapshotId, provider, repoId, commitSha/contentHash, materializedRoot, files, checksum, diagnostics, tenant/workspace/project.                                  | Snapshot DTO exists.                                  | Snapshot IDs are provider-generated and currently random in Java providers.                             |
| GitHub provider                               | PARTIALLY_IMPLEMENTED     | Resolves commit SHA, reads recursive tree, fails closed on truncated tree, materializes blobs, but uses random snapshotId.                                                        | GitHub repo import exists.                            | Not production-safe for large repos, credentials, deterministic IDs, `.gitignore`, skip classification. |
| Local folder provider                         | UNSAFE_FOR_PRODUCTION     | Walks all files and computes file checksum from path + size, not content; snapshotId is random.                                                                                   | Local scan exists but not source-faithful.            | Can miss same-size content changes and scans too broadly.                                               |
| Archive provider                              | PARTIALLY_IMPLEMENTED     | ZIP extraction has zip-slip and total-size protection but uses random snapshotId and archive checksum from path + size.                                                           | ZIP provider exists.                                  | Not deterministic enough; no tar/tgz; memory risk from `readAllBytes`.                                  |
| Durable import job schema                     | PARTIALLY_IMPLEMENTED     | `source_import_jobs` table includes tenant/workspace/project, source, status, progress, result JSON, residual status, cancellation fields.                                        | Durable job table exists.                             | Repository/status scoping and cancellation semantics are flawed.                                        |
| Source import job repository                  | UNSAFE_FOR_PRODUCTION     | Has default constructor using `Runnable::run`; `cancelJob` sets status `FAILED`; `findJobById` filters only by `job_id`.                                                          | Persistence exists but unsafe.                        | Event-loop blocking risk and cross-scope job lookup risk.                                               |
| Java compile orchestration                    | PARTIALLY_IMPLEMENTED     | `ArtifactCompileJobService` resolves provider snapshot, invokes `TsExtractorWorker`, and ingests graph.                                                                           | High-level pipeline exists.                           | Concrete worker execution/contract validation needs hardening.                                          |
| Java graph persistence                        | PARTIALLY_IMPLEMENTED     | Repository upserts nodes/edges with source location, extractor, confidence, provenance, privacy/security flags, residual IDs, sourceRef/symbolRef, snapshot/version, tombstones.  | Good persistence foundation.                          | Pagination/diff queries omit workspace; residual data is incomplete.                                    |
| Graph query pagination                        | UNSAFE_FOR_PRODUCTION     | `findNodesPaginated`/`findEdgesPaginated` filter by tenant and project only, not workspace.                                                                                       | Workspace isolation is incomplete.                    | P0 data isolation risk.                                                                                 |
| Graph query correctness                       | PARTIALLY_IMPLEMENTED     | `queryGraph` computes orphan/dependency/dependent/stats from paginated current page only.                                                                                         | Results can be incomplete or misleading.              | Not reliable for large graphs.                                                                          |
| Residual preservation in Java                 | PARTIALLY_IMPLEMENTED     | `mapResidualIslands` creates records from IDs with unknown path and minimal metadata.                                                                                             | Residual IDs are stored, but source fidelity is weak. | Cannot prove unsupported source is preserved.                                                           |
| Unknown file residual handling                | UNSAFE_FOR_PRODUCTION     | Unknown file type returns empty nodes/edges.                                                                                                                                      | Unsupported source can be silently dropped.           | Violates source fidelity.                                                                               |
| TypeScript artifact compiler package          | PARTIALLY_IMPLEMENTED     | TS package exports inventory, graph, source providers, compile-back, model, provenance, residual, extractors, synthesis, merge, builder, worker.                                  | TS side is broad and mature.                          | Needs clear production boundary with Java.                                                              |
| TS source providers                           | DUPLICATED_OR_CONFLICTING | TS registry includes local-folder, archive, zip, GitHub, GitLab.                                                                                                                  | Duplicates Java provider runtime.                     | Must be dev/test/worker-only or contract-aligned.                                                       |
| TS graph schema                               | IMPLEMENTED               | TS graph schema includes deterministic ID helper, node/edge kinds, unresolved edges, resolution records.                                                                          | Rich graph IR exists.                                 | Java DTO/schema alignment must be enforced.                                                             |
| TS semantic model                             | IMPLEMENTED               | Semantic model has provenance, confidence, reviewRequirement, security/privacy flags, graphNodeIds, sourceRefs, residualIslandIds.                                                | Good product-facing model schema.                     | Backend persistence/versioning boundary unclear.                                                        |
| TS extractors                                 | PARTIALLY_IMPLEMENTED     | Exports TS component/page/state, Storybook, Prisma extractors and canonical registry.                                                                                             | Useful TS-native extraction exists.                   | Should run as Java-orchestrated worker for production.                                                  |
| TS extractor worker                           | PARTIALLY_IMPLEMENTED     | Worker reads JSON from stdin, runs synthesis pipeline, outputs nodes/edges/unresolved/residual IDs as unknown arrays.                                                             | Worker exists.                                        | Needs strict contract validation, timeouts, resource limits, and Java runner.                           |
| TS compile-back                               | PARTIALLY_IMPLEMENTED     | TS compile-back exports patch coordinator, emitters, residual preserver, apply/rollback functions.                                                                                | Partial compile-back exists.                          | Not governed/durable backend round-trip.                                                                |
| Patch coordinator                             | PARTIALLY_IMPLEMENTED     | Builds PatchSet, validates files/checksums/diff headers, preserves residuals.                                                                                                     | Strong TS patch foundation.                           | Needs Java orchestration, persistence, review/apply APIs.                                               |
| Fastify source import route                   | DUPLICATED_OR_CONFLICTING | TS route can run legacy pipeline but defaults to proxy Java API.                                                                                                                  | Duplicate import flow remains.                        | Must be removed or permanently dev-only.                                                                |

---

## C. Gap Analysis

| Capability                 | Current state                                                              | Gap                                                                                      | Severity | Required fix                                                                                             |
| -------------------------- | -------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- | -------: | -------------------------------------------------------------------------------------------------------- |
| Workspace isolation        | Controllers validate scope, but repository pagination/diff omit workspace. | Workspace leakage possible.                                                              |       P0 | Add workspace scope to paginated/diff repository methods and service/controller DTOs.                    |
| Deterministic snapshots    | Java providers use random UUID snapshot IDs.                               | No stable snapshot identity.                                                             |       P0 | Derive snapshotId from provider + repoId + commit/content checksum.                                      |
| Content fidelity           | Local/archive checksums use path + size in places.                         | Same-size content changes missed.                                                        |       P0 | Use SHA-256 of actual content for files and snapshot checksum.                                           |
| Source fidelity            | Unknown file types return empty graph.                                     | Silent source drop.                                                                      |       P0 | Emit residual island for every unsupported/unknown file.                                                 |
| Durable source acquisition | Job table/repo exists.                                                     | Job execution is not fully durable/resumable/cancellable.                                |       P1 | Add durable job runner with retry/cancel/resume and persisted phase state.                               |
| Source providers           | Java has GitHub/local/archive; TS has GitLab too.                          | Inconsistent provider capability.                                                        |       P1 | Java owns production providers; add Java GitLab or mark unsupported through capability registry.         |
| Inventory scanner          | Providers produce file lists directly.                                     | No canonical scanner with `.gitignore`, skip reasons, generated/vendor/binary detection. |       P0 | Add Java `RepositoryInventoryScanner` and snapshot inventory schema.                                     |
| TS worker boundary         | TS worker outputs `unknown[]`.                                             | Contract drift risk.                                                                     |       P1 | Validate JSON in/out using shared schema/proto-generated contract.                                       |
| Graph query                | Query computes only current page.                                          | Incorrect stats/dependencies for large graphs.                                           |       P1 | Move query semantics into repository with graph-specific SQL/adjacency pagination.                       |
| Residual islands           | Residual IDs stored, but metadata is weak.                                 | Cannot prove preservation.                                                               |       P0 | Persist full residual schema: source span, checksum, raw fragment ref, reason, risk, review requirement. |
| Compile-back               | TS patch generation exists.                                                | No Java durable backend patch workflow.                                                  |       P1 | Add Java patch job orchestration + TS emitter worker + review/apply/rollback APIs.                       |
| Frontend UX                | Import route/proxy exists.                                                 | Full import summary, residual review, patch review, drift UX not verified.               |       P1 | Implement end-to-end UI around Java job APIs and patch review APIs.                                      |

---

## D. Architecture Decisions

### Decision 1: Java owns production source acquisition and durable import orchestration

* **Decision:** `JAVA_CANONICAL`
* **Why:** Source acquisition is tenant-scoped, long-running, security-sensitive, durable, observable, and governance-heavy.
* **Evidence:** Java import route is wired through secured ActiveJ backend.  Java has source provider abstraction and registry.
* **Files to keep/modify:**

  * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/*`
  * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ImportController.java`
* **Files to consolidate:**

  * `products/yappc/frontend/apps/api/src/routes/source-imports.ts`
  * `products/yappc/frontend/apps/api/src/services/job-repository.ts`
* **Rejected alternative:** TS Fastify as production import owner. It duplicates Java and cannot be the durable canonical backend.

### Decision 2: TypeScript owns TS/TSX-native extraction and TS/React patch emission

* **Decision:** `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`
* **Why:** TypeScript Compiler API and React ecosystem tooling are more accurate for TS/TSX than Java.
* **Evidence:** TS extractor worker exists and runs the synthesis pipeline.  TS extractors include TS component/page/state, Storybook, and Prisma extractors. 
* **Boundary:** Java creates snapshot and job; TS worker receives snapshot contract; TS returns canonical graph/model facts; Java persists, validates, and governs.

### Decision 3: Java owns graph persistence, graph query, validation orchestration, and governance

* **Decision:** `JAVA_CANONICAL`
* **Why:** Graph data is tenant/workspace/project scoped and must be durable, queryable, auditable, and governed.
* **Evidence:** Java graph repository persists nodes/edges with provenance/confidence/security fields. 
* **Required fix:** Close workspace scope gaps in pagination/diff methods. 

### Decision 4: TypeScript semantic model schema is strong, but backend must own durable semantic model versions

* **Decision:** `HYBRID`
* **Why:** TS schema is rich and useful for frontend/canvas/compiler intelligence, but durable model versions must be backend-governed.
* **Evidence:** TS semantic model includes provenance, confidence, review requirements, flags, graph IDs, source refs, and residual IDs. 
* **Required fix:** Add Java semantic model version persistence contract aligned to TS schema.

### Decision 5: Compile-back must be Java-orchestrated with TS/Java emitters

* **Decision:** `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`
* **Why:** Patch jobs need governance, validation, audit, review, rollback, and PR/apply workflows. TS emitters remain best for TS/React. Java/OpenRewrite should handle Java transformations.
* **Evidence:** TS compile-back and patch coordinator exist.
* **Required fix:** Add backend patch job API and persistence.

---

## E. Prescriptive File-by-File TODO Plan

| Priority | Phase | File path                                                                                                               | Action              | Current issue                                                                                                   | Required change                                                                                                                                                                | Tests                                                |
| -------- | ----: | ----------------------------------------------------------------------------------------------------------------------- | ------------------- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`               | MODIFY              | `findNodesPaginated`, `findEdgesPaginated`, and `computeSnapshotDiff` omit workspace scope.                     | Add `workspaceId` parameter and `workspace_id = ?` to all paginated/diff queries. Include workspace in selected/mapped DTOs where needed.                                      | `ArtifactGraphRepositoryWorkspaceIsolationTest.java` |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`    | MODIFY              | `queryGraph` calls repository pagination without workspace and computes query results from one page only.       | Change `queryGraph` to accept `ArtifactRequestScope`. Use query-specific repository methods for stats/dependencies/dependents/orphans. Wire `snapshotId` and unresolved edges. | `ArtifactGraphServiceQueryCorrectnessTest.java`      |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`                   | MODIFY              | Query parses `snapshotId` and `includeUnresolvedEdges` but does not pass them to service.                       | Replace raw map parsing with typed `ArtifactGraphQueryRequest`; pass full `ArtifactRequestScope`, snapshotId, includeUnresolvedEdges, cursor, limit.                           | `ArtifactGraphControllerScopeTest.java`              |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SourceImportJobRepository.java`             | MODIFY              | Default constructor uses `Runnable::run`; job lookup is by jobId only; cancel sets status `FAILED`.             | Remove default constructor, require executor, add scoped `findJobById(jobId, tenantId, workspaceId, projectId)`, set cancel status to `CANCELLED`.                             | `SourceImportJobRepositoryScopeTest.java`            |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ImportController.java`                          | MODIFY              | Status route reads by jobId without visible scope enforcement; async compile is fire-and-forget promise chain.  | Require principal + workspace/project headers for status; call scoped job lookup; move compile execution into durable job runner/executor.                                     | `ImportControllerScopeTest.java`                     |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitHubSourceProvider.java`          | MODIFY              | Snapshot ID is random; no credentials; no `.gitignore`; no skip classification; fetches blobs synchronously.    | Deterministic snapshotId, credentialRef resolver, bounded concurrency, file-size limits, skip reasons, `.gitignore`, generated/vendor/binary classification.                   | `GitHubSourceProviderCommitPinningTest.java`         |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/LocalFolderSourceProvider.java`     | MODIFY              | File checksum uses path + size, snapshotId is random, no `.gitignore` or skip classification.                   | Use content SHA-256, deterministic snapshotId, canonical scanner integration, skip reasons.                                                                                    | `LocalFolderSourceProviderDeterminismTest.java`      |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/ArchiveSourceProvider.java`         | MODIFY              | ZIP only; snapshotId random; archive checksum path+size; reads entries into memory.                             | Stream entries, content checksum, deterministic snapshotId, per-entry limit, tar/tgz unsupported diagnostic or implementation.                                                 | `ArchiveSourceProviderSafetyTest.java`               |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`    | MODIFY              | Unknown file type returns empty nodes/edges.                                                                    | Emit residual island for every unsupported/unknown file, including checksum, source span, reason, review requirement.                                                          | `ArtifactGraphServiceUnsupportedParserTest.java`     |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/RepositoryInventoryScanner.java`    | ADD                 | No canonical Java inventory scanner.                                                                            | Add deterministic scanner: `.gitignore`, include/exclude, generated/vendor/binary/large classification, stable source file refs.                                               | `RepositoryInventoryScannerTest.java`                |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/source/SourceFileRef.java`                   | ADD                 | No canonical durable file identity object.                                                                      | Add provider/repo/ref/path/contentChecksum/size/classification/skippedReason.                                                                                                  | `SourceFileRefTest.java`                             |
| P0       |     1 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V17__create_repository_snapshots_and_inventory.sql` | ADD                 | Snapshot metadata is not durably persisted as first-class table.                                                | Add `repository_snapshots`, `repository_snapshot_files`, skipped reason, checksum, provider, commit/content hash, tenant/workspace/project indexes.                            | Migration test                                       |
| P1       |     2 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitLabSourceProvider.java`          | ADD                 | Java registry lacks GitLab while TS supports GitLab.                                                            | Add GitLab provider or expose unsupported capability explicitly.                                                                                                               | `GitLabSourceProviderTest.java`                      |
| P1       |     2 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceProviderRegistry.java`        | MODIFY              | Default registry omits GitLab.                                                                                  | Register GitLab provider or capability entry marked unsupported with reason.                                                                                                   | `SourceProviderRegistryCapabilitiesTest.java`        |
| P1       |     2 | `products/yappc/frontend/apps/api/src/routes/source-imports.ts`                                                         | CONSOLIDATE         | Legacy TS route duplicates Java import pipeline, while default proxies to Java.                                 | Remove production legacy path; keep only Java proxy or move legacy pipeline to dev/test tool.                                                                                  | API route tests                                      |
| P1       |     2 | `products/yappc/frontend/apps/api/src/services/job-repository.ts`                                                       | REMOVE_OR_DEPRECATE | TS job repository duplicates Java durable jobs.                                                                 | Remove from production path; keep test-only if needed.                                                                                                                         | Typecheck + route tests                              |
| P1       |     3 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/SubprocessTsExtractorWorker.java` | ADD                 | Java defines `TsExtractorWorker` interface but concrete sidecar runner was not verified.                        | Add process-based worker with timeout, max stdout, stderr redaction, schema validation, worker version metadata.                                                               | `SubprocessTsExtractorWorkerContractTest.java`       |
| P1       |     3 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`                                | MODIFY              | Worker returns `unknown[]` without contract validation.                                                         | Validate input/output with zod contract aligned to Java DTO/proto; emit extractor versions and diagnostics.                                                                    | `ts-extractor-worker.contract.test.ts`               |
| P1       |     3 | `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`                                             | MODIFY              | Contract boundary must be canonical.                                                                            | Add SourceLocator, RepositorySnapshot, SourceFileRef, GraphNode, GraphEdge, UnresolvedEdge, ResidualIsland, PatchSet messages.                                                 | Proto generation test                                |
| P1       |     4 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ResidualIslandService.java`       | ADD                 | Residuals are stored as weak IDs/unknown path.                                                                  | Persist full residual island schema and validate preservation during patch generation.                                                                                         | `ResidualIslandServiceTest.java`                     |
| P1       |     5 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactPatchController.java`                   | ADD                 | No Java durable patch generation/review/apply API verified.                                                     | Add plan/generate/validate/review/apply/reject/rollback endpoints.                                                                                                             | `ArtifactPatchControllerScopeTest.java`              |
| P1       |     5 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/patch/ArtifactPatchJobService.java`        | ADD                 | TS compile-back exists but is not backend-governed.                                                             | Java orchestrates patch jobs, invokes TS emitters for TS/React, stores validation/review/rollback metadata.                                                                    | `ArtifactPatchJobServiceTest.java`                   |
| P1       |     5 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V18__create_artifact_patch_sets.sql`                | ADD                 | PatchSet persistence missing.                                                                                   | Add patch_jobs, patch_sets, file_patches, validation_results, review_decisions, rollback_metadata.                                                                             | Migration test                                       |
| P1       |     6 | `products/yappc/frontend/web/src/services/compiler/ImportSourceWorkflow.ts`                                             | MODIFY              | UI workflow must consume Java canonical import jobs.                                                            | Call typed Java API client; show progress, skipped files, residuals, confidence, review requirements.                                                                          | Frontend integration test                            |
| P1       |     6 | `products/yappc/frontend/web/src/components/compiler/ImportSummaryPanel.tsx`                                            | ADD                 | Import summary UX not clearly present.                                                                          | Add understood/skipped/residual/confidence/review-required summary.                                                                                                            | Playwright import flow                               |
| P1       |     6 | `products/yappc/frontend/web/src/components/compiler/PatchReviewPanel.tsx`                                              | ADD                 | Patch review UX not clearly present.                                                                            | Show diff, validation results, residual risk, apply/reject/rollback.                                                                                                           | Playwright patch review flow                         |
| P2       |     6 | `products/yappc/docs/architecture/ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md`                                         | MODIFY              | Architecture must match Java/TS ownership.                                                                      | Update canonical runtime boundary, contracts, milestones, unsupported states.                                                                                                  | Docs lint/review                                     |

---

## F. Java vs TypeScript Ownership Plan

| Capability                      | Recommended owner                                | Why                                                                      | Files                                                        |
| ------------------------------- | ------------------------------------------------ | ------------------------------------------------------------------------ | ------------------------------------------------------------ |
| Source provider abstraction     | JAVA_CANONICAL                                   | Durable, governed, tenant-scoped acquisition belongs backend-side.       | `services/source/SourceProvider.java`                        |
| GitHub provider                 | JAVA_CANONICAL                                   | Commit pinning and credential governance must be backend-controlled.     | `GitHubSourceProvider.java`                                  |
| GitLab provider                 | JAVA_CANONICAL                                   | TS has provider, Java does not; add Java provider or mark unsupported.   | `GitLabSourceProvider.java`                                  |
| Local folder provider           | JAVA_CANONICAL                                   | Trusted runtime path access must be backend-controlled.                  | `LocalFolderSourceProvider.java`                             |
| Archive provider                | JAVA_CANONICAL                                   | Archive extraction is security-sensitive.                                | `ArchiveSourceProvider.java`                                 |
| Durable import jobs             | JAVA_CANONICAL                                   | Durable state, retry/cancel/resume, scope.                               | `SourceImportJobRepository.java`, `ImportController.java`    |
| Repository snapshot             | JAVA_CANONICAL                                   | Snapshot is source-of-truth for persistence and graph versioning.        | `RepositorySnapshot.java`                                    |
| Inventory scanner               | JAVA_CANONICAL                                   | Long-running scan, skip rules, durability.                               | `RepositoryInventoryScanner.java`                            |
| TS/TSX extraction               | HYBRID_JAVA_ORCHESTRATED_TS_WORKER               | TS Compiler API is best, but Java owns orchestration.                    | `ts-extractor-worker.ts`, `SubprocessTsExtractorWorker.java` |
| React/page/route extraction     | HYBRID_JAVA_ORCHESTRATED_TS_WORKER               | React ecosystem tooling is TS-native.                                    | TS extractors                                                |
| Java extraction                 | JAVA_CANONICAL                                   | JavaParser/OpenRewrite belong Java-side.                                 | `JavaSourceParser.java` and future OpenRewrite module        |
| Graph persistence               | JAVA_CANONICAL                                   | Tenant-scoped durable graph.                                             | `ArtifactGraphRepository.java`                               |
| Symbol/reference index          | JAVA_CANONICAL                                   | Central graph correctness and re-resolution.                             | Add `SymbolReferenceIndexService.java`                       |
| Edge resolver                   | JAVA_CANONICAL                                   | Resolved graph integrity must be backend-governed.                       | Add `GraphEdgeResolver.java`                                 |
| Semantic synthesis              | HYBRID                                           | TS extraction can produce facts; Java persists canonical model versions. | TS `synthesis`, Java model repository                        |
| Residual islands                | JAVA_CANONICAL persistence, TS detection allowed | Preservation must be durable and reviewable.                             | Add Java residual service; TS residual schema stays helper   |
| Patch generation                | HYBRID_JAVA_ORCHESTRATED_TS_WORKER               | Java governs jobs/review; TS emits TS/React patches.                     | `ArtifactPatchJobService.java`, TS compile-back              |
| TS/React patch emitter          | TYPESCRIPT_CANONICAL                             | TS AST/formatting is ecosystem-native.                                   | TS compile-back emitters                                     |
| Java patch emitter              | JAVA_CANONICAL                                   | OpenRewrite should handle Java source transforms.                        | Add Java patch emitter module                                |
| Patch validation                | JAVA_CANONICAL orchestration                     | Needs durable, governed validation.                                      | Java patch service                                           |
| Frontend import UX              | TYPESCRIPT_CANONICAL                             | UI belongs frontend.                                                     | frontend web components                                      |
| Patch review UX                 | TYPESCRIPT_CANONICAL                             | UI belongs frontend.                                                     | frontend web components                                      |
| Generated API clients/contracts | CONTRACT_ONLY                                    | Prevent Java/TS schema drift.                                            | proto/OpenAPI + generated TS clients                         |

### Recommended approach

Use **Java as the canonical production runtime** for long-running, durable, heavy, governed work:

* source acquisition,
* snapshot lifecycle,
* inventory scanning,
* job orchestration,
* graph persistence,
* graph query,
* graph validation,
* edge resolution,
* semantic model versioning,
* patch job orchestration,
* validation,
* review/apply/rollback,
* audit/governance.

Use **TypeScript as a Java-orchestrated worker** where language-native tooling is more correct:

* TS/TSX extraction,
* React component/page/route extraction,
* Storybook extraction,
* TS/React patch emission,
* UI preview support.

Do **not** let TypeScript own production durable source import state or canonical graph/model persistence.

---

## G. Test Plan

| Priority | Test file path                                                                                                                        | Scenario                                                       | Expected assertion                                             |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | -------------------------------------------------------------- |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/storage/ArtifactGraphRepositoryWorkspaceIsolationTest.java`       | Two workspaces share tenant/project and query paginated nodes. | Workspace A never receives Workspace B nodes.                  |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceQueryCorrectnessTest.java`  | Dependency query over graph larger than one page.              | Result includes correct dependencies, not only current page.   |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/storage/SourceImportJobRepositoryScopeTest.java`                  | Lookup job by ID from wrong workspace.                         | Returns null/forbidden.                                        |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/LocalFolderSourceProviderDeterminismTest.java`    | Same-size content change in file.                              | Snapshot checksum changes.                                     |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/GitHubSourceProviderCommitPinningTest.java`       | Resolve branch to commit SHA twice.                            | Snapshot ID stable for same commit.                            |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/RepositoryInventoryScannerTest.java`              | `.gitignore`, vendor, generated, binary, large files.          | Files are classified/skipped with explicit reasons.            |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceUnsupportedParserTest.java` | Unknown extension source artifact.                             | Emits residual island, never empty silent output.              |
| P1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/__tests__/ts-extractor-worker.contract.test.ts`                      | Worker receives Java snapshot contract.                        | Output validates against graph DTO schema.                     |
| P1       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/compiler/SubprocessTsExtractorWorkerContractTest.java`   | Java invokes TS worker with timeout.                           | Valid output persisted; invalid output rejected.               |
| P1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/__tests__/noop-roundtrip.test.ts`                              | Source → model → no edit → patch.                              | PatchSet contains zero file patches.                           |
| P1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/__tests__/simple-model-edit-to-patch.test.ts`                  | Rename/update supported React component prop.                  | Minimal patch generated with checksum.                         |
| P1       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/ArtifactPatchControllerScopeTest.java`                        | Patch apply from wrong tenant/workspace.                       | Forbidden.                                                     |
| P1       | `products/yappc/frontend/web/tests/e2e/artifact-import-flow.spec.ts`                                                                  | Import repo, view summary, residuals, skipped files.           | UX shows progress, confidence, residuals, review requirements. |
| P1       | `products/yappc/frontend/web/tests/e2e/artifact-patch-review.spec.ts`                                                                 | Generate patch, validate, approve/reject.                      | Diff and validation results visible; apply/reject works.       |

---

## H. Critical Questions

### 1. Is the current system truly round-trip capable?

**Answer: No.**

Evidence:

* TS compile-back and patch coordination exist.
* Java backend routes do not include patch generation/review/apply/rollback under artifact compiler routes. 

Required fix:

* Add Java patch job APIs, persistence, validation, review/apply/rollback, and TS/Java emitter workers.

### 2. Can it scan a full GitHub repo today?

**Answer: Partial.**

Evidence:

* Java GitHub provider resolves commit SHA and materializes recursive tree. 
* It lacks production-grade large-repo controls, credentials, `.gitignore`, skip classification, bounded concurrency, deterministic snapshot ID.

Required fix:

* Harden `GitHubSourceProvider` and move inventory into canonical scanner.

### 3. Are artifact IDs deterministic?

**Answer: Partial / unsafe.**

Evidence:

* TS graph has deterministic node ID helper when snapshotRef exists. 
* Java providers use random snapshot IDs.

Required fix:

* Deterministic snapshot IDs and deterministic source file refs from provider/repo/commit/path/checksum.

### 4. Are graph edges valid and resolved?

**Answer: Partial.**

Evidence:

* Controller validates ingest edges reference existing node IDs in payload. 
* TS schema separates resolved edges and unresolved references. 

Required fix:

* Add Java canonical symbol/reference index and edge resolver lifecycle.

### 5. Is there a complete synthesis pipeline?

**Answer: Partial.**

Evidence:

* TS worker runs `SynthesisPipeline` with canonical extractors. 
* Java orchestrator calls `TsExtractorWorker` and ingests graph. 

Required fix:

* Validate worker boundary, persist semantic model versions, and connect canvas/UI review.

### 6. Is compile-back/patch generation implemented?

**Answer: Partial.**

Evidence:

* TS compile-back and patch coordinator exist.
* Java artifact routes do not expose patch workflow. 

Required fix:

* Add Java patch orchestration and durable review/apply APIs.

### 7. Are residual islands preserved?

**Answer: Partial.**

Evidence:

* TS PatchCoordinator preserves residuals. 
* Java stores residual island IDs, but maps them to weak records with unknown file path. 
* Unknown file types can return empty graph output. 

Required fix:

* Persist full residual island schema and never allow unsupported source to disappear silently.

### 8. Are source import jobs durable?

**Answer: Partial.**

Evidence:

* Durable DB table exists. 
* Repository supports save/progress/status/cancel/find. 

Required fix:

* Fix cancellation status, scoped job reads, executor usage, retry/resume, and phase persistence.

### 9. Is tenant/workspace/project scope enforced consistently?

**Answer: No.**

Evidence:

* Controllers enforce principal/header scope.
* Repository pagination and snapshot diff omit workspace. 
* Job lookup is by job ID only. 

Required fix:

* Enforce tenant/workspace/project at every repository query and every status/read route.

### 10. Are there duplicate or conflicting implementations?

**Answer: Yes.**

Evidence:

* Java source provider registry exists. 
* TS source provider registry also exists and includes GitLab. 
* TS Fastify import route can execute legacy import pipeline but defaults to proxy Java.

Required fix:

* Java production canonical; TS providers only dev/test/worker helper or removed from production path.

---

## I. Recommended First Milestone

## Milestone 1: Stable Repository IR and Source Snapshot Compiler

Deliver this before adding broader decompiler UX.

| Item                                       | Owner                                | Required state                                                            |
| ------------------------------------------ | ------------------------------------ | ------------------------------------------------------------------------- |
| Canonical Java source provider abstraction | Java                                 | GitHub/local/archive/GitLab capabilities exposed through Java registry.   |
| Deterministic repository snapshot          | Java                                 | snapshotId stable for same source/ref/content.                            |
| Canonical inventory scanner                | Java                                 | `.gitignore`, skip reasons, vendor/generated/binary/large classification. |
| Content-based checksums                    | Java                                 | Same-size content changes detected.                                       |
| Durable snapshot persistence               | Java                                 | snapshots and files persisted by tenant/workspace/project.                |
| Graph edge lifecycle                       | Java                                 | unresolved references stay separate; resolved edges use node IDs only.    |
| TS extractor sidecar                       | Hybrid                               | Java invokes TS worker with schema validation, timeout, diagnostics.      |
| Residual island preservation               | Java canonical, TS detection allowed | unsupported/unknown source never disappears.                              |
| Golden scan tests                          | Java + TS                            | Deterministic fixture tests pass.                                         |
| No-op round-trip scaffold                  | Hybrid                               | Test exists and is expected to pass when compile-back phase is complete.  |

---

## J. Prioritized TODO Checklist

### P0 — correctness, isolation, source fidelity

* [ ] [P0] Fix workspace isolation in artifact graph repository queries.

  * Files:

    * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`
    * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`
    * `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`
  * Done when: every graph read/write/query/diff uses tenant + workspace + project.
  * Test: `ArtifactGraphRepositoryWorkspaceIsolationTest.java`

* [ ] [P0] Make repository snapshots deterministic.

  * Files:

    * `GitHubSourceProvider.java`
    * `LocalFolderSourceProvider.java`
    * `ArchiveSourceProvider.java`
    * `RepositorySnapshot.java`
  * Done when: same repo/ref/content produces same snapshotId and checksum.
  * Test: `SourceProviderDeterminismTest.java`

* [ ] [P0] Replace provider-level file walking with canonical inventory scanner.

  * Files:

    * `RepositoryInventoryScanner.java`
    * `SourceFileRef.java`
    * `V17__create_repository_snapshots_and_inventory.sql`
  * Done when: `.gitignore`, vendor/generated/binary/large files are classified with explicit reasons.
  * Test: `RepositoryInventoryScannerTest.java`

* [ ] [P0] Prevent silent source loss for unsupported file types.

  * Files:

    * `ArtifactGraphServiceImpl.java`
    * `ResidualIslandService.java`
  * Done when: every unsupported/unknown file creates a residual island with source span/checksum/reason.
  * Test: `ArtifactGraphServiceUnsupportedParserTest.java`

* [ ] [P0] Fix source import job scoping and cancellation semantics.

  * Files:

    * `SourceImportJobRepository.java`
    * `ImportController.java`
  * Done when: status lookup is scoped and cancellation uses `CANCELLED`.
  * Test: `SourceImportJobRepositoryScopeTest.java`

### P1 — production readiness

* [ ] [P1] Add Java GitLab provider or explicit unsupported capability.

  * Files:

    * `GitLabSourceProvider.java`
    * `SourceProviderRegistry.java`
  * Done when: GitLab source type cannot silently route to GitHub or fail ambiguously.
  * Test: `GitLabSourceProviderTest.java`

* [ ] [P1] Add Java-orchestrated TS extractor sidecar.

  * Files:

    * `SubprocessTsExtractorWorker.java`
    * `ts-extractor-worker.ts`
    * `artifact_compiler.proto`
  * Done when: Java invokes TS worker through validated contract with timeout and diagnostics.
  * Test: `SubprocessTsExtractorWorkerContractTest.java`

* [ ] [P1] Consolidate TS Fastify source import route behind Java canonical API.

  * Files:

    * `products/yappc/frontend/apps/api/src/routes/source-imports.ts`
    * `products/yappc/frontend/apps/api/src/services/job-repository.ts`
  * Done when: production path cannot execute duplicate TS import job state.
  * Test: API route contract test.

* [ ] [P1] Add Java patch generation/review/apply backend.

  * Files:

    * `ArtifactPatchController.java`
    * `ArtifactPatchJobService.java`
    * `V18__create_artifact_patch_sets.sql`
  * Done when: PatchSet generation is durable, validated, reviewable, auditable, and reversible.
  * Test: `ArtifactPatchControllerScopeTest.java`

### P2 — extensibility and UX

* [ ] [P2] Add capability registry endpoint.

  * Files:

    * `SourceProviderRegistry.java`
    * `YappcHttpServer.java`
  * Done when: UI can query supported providers/extractors/emitters/validators and support levels.
  * Test: capability endpoint test.

* [ ] [P2] Add import summary and patch review UX.

  * Files:

    * `ImportSourceWorkflow.ts`
    * `ImportSummaryPanel.tsx`
    * `PatchReviewPanel.tsx`
  * Done when: user sees progress, skipped files, residuals, confidence, risks, validation, and apply/reject actions.
  * Test: Playwright import/edit/patch/re-scan flow.

### P3 — cleanup and docs

* [ ] [P3] Update architecture doc to reflect Java/TS boundary.

  * Files:

    * `products/yappc/docs/architecture/ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md`
  * Done when: docs match production runtime ownership and unsupported states.
  * Test: docs review.

* [ ] [P3] Keep archived audit docs archived and prevent stale docs from driving implementation.

  * Files:

    * `docs/archive/comp-decomp-todo-2026-03-27-audit.md`
  * Done when: archived docs are clearly non-authoritative.
  * Test: documentation review.

---

## Final Recommendation

The best approach is **not** “all Java” or “all TypeScript.”

Use this production split:

```text
Java:
  canonical source acquisition
  durable import jobs
  repository snapshots
  inventory scanner
  graph persistence/query
  edge resolution
  semantic model versioning
  patch job orchestration
  validation/governance/audit/review/apply/rollback

TypeScript:
  frontend UX
  TS/TSX/React extraction
  Storybook/Prisma/frontend-specific extraction
  TS/React patch emitters
  local preview/dev tooling

Hybrid:
  Java orchestrates TypeScript workers through strict contracts.
  TypeScript workers never own durable production state.
```

The smallest trustworthy milestone is **Stable Repository IR and Source Snapshot Compiler**: deterministic source snapshots, canonical inventory, stable IDs, residual preservation, graph ingest with strict scope, and Java-orchestrated TS extraction.
