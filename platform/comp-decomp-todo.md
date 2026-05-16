# Artifact Compiler/Decompiler Audit Report

Repo: `samujjwal/ghatana`
Target commit: `6e82fd932eae27940057b76d5674286851ff66bd`
Commit title: `test fixes 4` 

I executed a targeted current-state audit of the Artifact Compiler/Decompiler scope across the TypeScript artifact compiler package, frontend source-import API, frontend integration surface, and Java artifact graph backend. I did not modify the repo.

---

## A. Executive Summary

### Current capability classification

| Capability                   |                              Current classification | Evidence-based conclusion                                                                                                                                                                                                  |
| ---------------------------- | --------------------------------------------------: | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Source acquisition           | `snapshot-capable in TS, not durable backend-owned` | TS has `SourceProvider`, `RepositorySnapshot`, registry, local/GitHub/GitLab/zip/archive providers, and credential policy hooks. Java does not yet own durable source import orchestration.                                |
| Inventory                    | `repo-capable, deterministic only with snapshotRef` | Scanner has `.gitignore`, package boundary detection, bounded concurrency, deterministic sort, checksums, generated/vendor/binary classification, but ad-hoc scans without `snapshotRef` can still generate random IDs.    |
| Artifact graph               |           `graph-capable, not fully Java-canonical` | TS graph schema supports resolved/unresolved edge split, provenance, confidence, validation. Java can persist/query/analyze graph nodes and edges, but contracts diverge.                                                  |
| Semantic model               |  `partial, broad schema with ID consistency issues` | Semantic model covers components/pages/layouts/tokens/styles/entities/APIs/state/workflows, but several nested references still require UUIDs while deterministic artifact/model IDs are strings.                          |
| Compile-back                 |              `partial, not true round-trip capable` | Change plan, patch set, review bundle, rollback metadata, dry-run validation, and React patch coordination exist, but supported emission is limited and no-op full repo round-trip is not proven.                          |
| Backend production readiness |                             `partially implemented` | Java backend enforces principal tenant and project header checks and uses blocking executor for heavy graph analysis, but source import/snapshot lifecycle and compile-back workflow are not Java-canonical yet.           |
| Overall                      |           `foundation exists, not production-ready` | The TS package is broad and test-green at package level, but the product is still split across TS API, frontend compiler, and Java graph backend without a single durable compile/decompile runtime.                       |

### Biggest blockers

1. **Runtime ownership is inverted.** Most source acquisition, repository scanning, synthesis, and compile-back logic lives in the frontend TypeScript package/API. Durable source import jobs, repository snapshots, governance, persistence, and long-running orchestration should be Java-canonical.

2. **Not true round-trip capable yet.** Compile-back has types and React patch support, but no evidence of full source → graph → model → no-op patch → zero diff → re-scan loop across fixtures.

3. **Java/TS contract divergence.** TS graph/model schemas, Java DTOs, Java repository tables, and frontend import responses are not clearly generated from one canonical contract.

4. **ID model inconsistency.** TS graph/model now supports deterministic string IDs, but several semantic model and residual references still require UUIDs, which conflicts with deterministic URN IDs.

5. **Workspace isolation is incomplete.** Java controller requires `X-Workspace-ID`, but service/repository scope shown here is tenant/project based, not tenant/workspace/project persisted and enforced end-to-end.  

---

## B. Objective Current Status

| Area                         | Status                      | Evidence                                                                                                                                                        | Objective conclusion                                                                               | Production impact                                                                    |
| ---------------------------- | --------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| TS artifact compiler package | `IMPLEMENTED`               | Package exports inventory, graph, source providers, compile-back, model, provenance, residual, extractors, synthesis, merge, builder, and TS extractor worker.  | Broad compiler library exists.                                                                     | Good foundation, but too much durable runtime logic currently sits TS-side.          |
| Source provider abstraction  | `PARTIALLY_IMPLEMENTED`     | `SourceProvider`, `SourceLocator`, `RepositorySnapshot`, registry, credential resolver exist.                                                                   | Implemented in TS; not Java-canonical.                                                             | Blocks durable server-side source acquisition.                                       |
| GitHub provider              | `PARTIALLY_IMPLEMENTED`     | GitHub provider resolves commit SHA, uses tree/blob APIs, materializes temp files, handles retry/rate-limit diagnostics.                                        | Functional TS provider, but not durable Java job and fails closed on GitHub tree truncation.       | Large repo support, retry/resume, credential governance, and persistence incomplete. |
| Local folder provider        | `PARTIALLY_IMPLEMENTED`     | Uses git SHA or content hash; walks directory and skips static dirs.                                                                                            | Useful provider, but local-folder trust boundary and skipped-file reporting are not Java-governed. | Unsafe for production unless server-side trusted runtime rules are enforced.         |
| Inventory scanner            | `PARTIALLY_IMPLEMENTED`     | Scanner supports `.gitignore`, deterministic mode, bounded concurrency, package boundaries, generated/vendor/binary classification.                             | Good TS implementation, but not Java-orchestrated and IDs can be random without snapshotRef.       | Determinism depends on caller discipline.                                            |
| Artifact graph schema        | `IMPLEMENTED`               | Resolved edge, unresolved edge, resolution records, source locations, confidence, provenance, privacy/security flags exist.                                     | TS graph IR is solid foundation.                                                                   | Needs canonical contract generation and Java parity.                                 |
| Graph validation             | `PARTIALLY_IMPLEMENTED`     | Validates duplicate nodes, missing edge source/target, required fields, source ranges, unresolved lifecycle.                                                    | Structural validation exists in TS.                                                                | Needs Java-side validation before persistence and generated shared contract.         |
| Symbol/reference resolution  | `PARTIALLY_IMPLEMENTED`     | `resolveSymbols` builds index and resolves references with aliases, relative paths, extension fallback, deterministic edge IDs.                                 | In-memory TS resolver exists.                                                                      | No durable Java symbol index or re-resolution lifecycle.                             |
| Semantic model               | `PARTIALLY_IMPLEMENTED`     | Model covers many product concepts with provenance/confidence/review/security/privacy fields.                                                                   | Broad schema exists.                                                                               | ID constraints and version/diff model are inconsistent with deterministic IDs.       |
| Residual islands             | `PARTIALLY_IMPLEMENTED`     | Residual schema includes raw fragment ref, checksum, risk, source location, regeneration strategy.                                                              | Preservation concept exists.                                                                       | Fragment precision and production-safe regeneration strategy need hardening.         |
| Synthesis pipeline           | `PARTIALLY_IMPLEMENTED`     | Pipeline runs scan → extract → resolve → graph → model, validates graph, creates residuals for low confidence.                                                  | Good TS pipeline skeleton.                                                                         | Not durable, not Java-orchestrated, not persisted as job/snapshot/model versions.    |
| Compile-back                 | `PARTIALLY_IMPLEMENTED`     | ChangeOps, PatchSet, FilePatch, ValidationResult, ReviewBundle, RollbackMetadata exist.                                                                         | Types exist; implementation limited.                                                               | Not true production round-trip.                                                      |
| Patch coordinator            | `PARTIALLY_IMPLEMENTED`     | Dry-run validates file existence, diff header, base checksum; buildPatchSet preserves residuals and routes to emitters.                                         | Useful partial coordinator.                                                                        | Needs full apply/rollback/PR workflow and more emitters.                             |
| Java artifact graph API      | `PARTIALLY_IMPLEMENTED`     | Controller has ingest/analyze/merge/query/residual endpoints and principal tenant checks.                                                                       | Backend graph API exists.                                                                          | Source import/compile-back APIs missing or proxied from TS API.                      |
| Java graph service           | `PARTIALLY_IMPLEMENTED`     | Service interface covers ingest/analyze/merge/query/residual only.                                                                                              | No Java source import job/snapshot/patch workflow service.                                         | Blocks production runtime ownership.                                                 |
| Java graph persistence       | `PARTIALLY_IMPLEMENTED`     | Repository persists nodes/edges with tenant/project, snapshot/version IDs, checksum, tombstone support.                                                         | Good graph persistence base.                                                                       | Needs workspace scope, snapshot/import job tables, canonical schema migration.       |
| TS Fastify import API        | `DUPLICATED_OR_CONFLICTING` | TS API either proxies to Java import API when legacy flag disabled or runs legacy TS import path when enabled.                                                  | Migration to Java is started but incomplete.                                                       | Two possible import paths create production ambiguity.                               |
| Frontend import job UX/API   | `PARTIALLY_IMPLEMENTED`     | TS route creates job progress steps, validates scope/source, records audit, returns review-required import payload.                                             | Review-required UX flow exists at API level.                                                       | Needs Java-owned durable job and frontend-only client/view responsibility.           |
| Test health                  | `PARTIALLY_IMPLEMENTED`     | Commit note reports `32/32` test files and `262/262` tests passing in TS artifact compiler package.                                                             | TS package tests are green.                                                                        | Green package tests do not prove full product round-trip or Java/TS integration.     |

---

## C. Architecture Decisions

### Decision 1: Java owns durable production orchestration

**Decision:** Move source import job lifecycle, repository snapshot persistence, graph persistence, validation orchestration, review/apply/rollback, audit, and governance to Java.

**Why:** Java backend already owns scoped graph APIs, repository persistence, JGraphT analysis on blocking executor, and tenant/project enforcement.  

**Files affected:**

* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphService.java`
* `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`
* New `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/*`
* New `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SourceImportJobRepository.java`
* New DB migration for source import jobs and repository snapshots.

**Alternatives rejected:** Keeping Fastify TS source-import route as production owner. It already has a Java proxy mode, which signals Java should become canonical. 

---

### Decision 2: TypeScript owns TS/TSX-native extraction and frontend UX

**Decision:** Keep TS/TSX, React, route, state, Storybook, Prisma extraction in TypeScript as a Java-orchestrated worker/sidecar.

**Why:** TS compiler package already has the right ecosystem dependencies and canonical extractors for TS/React/Page/State/Storybook/Prisma.   

**Files affected:**

* `products/yappc/frontend/libs/yappc-artifact-compiler/src/extractors/*`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`
* New Java worker adapter under `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/worker/TypeScriptExtractorWorkerClient.java`

---

### Decision 3: Contracts must become canonical and generated

**Decision:** Define graph/model/snapshot/patch contracts once, then generate Java DTOs and TypeScript types.

**Why:** TS schemas use deterministic strings in some places but UUID-only references elsewhere; Java DTOs/repositories have separate shapes.   

**Files affected:**

* `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`
* `platform/typescript/api`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/types.ts`
* `products/yappc/frontend/libs/yappc-artifact-compiler/src/model/types.ts`
* Java DTOs under `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/*`

---

## D. Java vs TypeScript Ownership Plan

| Capability                  | Recommended owner                         | Why                                                                                                            | Current evidence                                             | Required action                                                                          |
| --------------------------- | ----------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ | ---------------------------------------------------------------------------------------- |
| Source provider abstraction | `JAVA_CANONICAL`                          | Durable acquisition requires backend scope, credentials, audit, retry/resume.                                  | Currently TS-only provider abstraction.                      | Add Java source provider SPI; keep TS provider only for worker/dev compatibility.        |
| GitHub/GitLab provider      | `JAVA_CANONICAL`                          | Remote credentials and large repo acquisition must be governed server-side.                                    | GitHub provider currently TS API-based.                      | Add Java providers and deprecate TS API provider in production.                          |
| Local folder provider       | `JAVA_CANONICAL`                          | Local folder requires trusted runtime boundary.                                                                | TS local provider exists.                                    | Move trusted local import to Java runtime only.                                          |
| Inventory scanner           | `JAVA_CANONICAL_WITH_TS_HELPERS`          | Large repo walk and snapshot lifecycle should be durable; language-specific classification can use TS helpers. | TS scanner is mature.                                        | Java orchestrates inventory; TS scanner can run as worker for TS project classification. |
| TS/React extractor          | `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`      | TypeScript Compiler API is correct runtime, but Java must orchestrate and persist.                             | Canonical TS extractors exist.                               | Add worker protocol and Java client.                                                     |
| Java extractor              | `JAVA_CANONICAL`                          | Java backend already has Java parser hook.                                                                     | `parseSourceArtifact` routes `.java` to `JavaSourceParser`.  | Promote to plugin extractor contract.                                                    |
| Artifact graph schema       | `CONTRACT_ONLY`                           | Must be shared across Java and TS.                                                                             | TS schema exists; Java persistence exists separately.        | Generate both sides from proto/schema.                                                   |
| Graph persistence           | `JAVA_CANONICAL`                          | Durable, tenant-scoped storage.                                                                                | Java repository persists graph nodes/edges.                  | Add snapshot/workspace/version completeness.                                             |
| Symbol/reference index      | `JAVA_CANONICAL_WITH_TS_INPUTS`           | Durable re-resolution and query need backend ownership.                                                        | TS resolver exists in-memory.                                | Add Java persisted symbol index and use TS unresolved edges as input.                    |
| Semantic model synthesis    | `JAVA_CANONICAL_WITH_TS_EXTRACTOR_OUTPUT` | Versioned product model must be backend-owned.                                                                 | TS pipeline assembles model.                                 | Move model version persistence and governance to Java.                                   |
| Compile-back planning       | `JAVA_CANONICAL`                          | Requires governance, review, apply/rollback.                                                                   | TS types/coordinator exist.                                  | Add Java ChangePlan/PatchSet orchestration.                                              |
| TS/TSX patch emission       | `HYBRID_JAVA_ORCHESTRATED_TS_WORKER`      | TS AST/formatter is best for TS/React code.                                                                    | React patch emitter/coordinator exists.                      | Java calls TS emitter worker; Java stores/reviews patch result.                          |
| Frontend import UX          | `TYPESCRIPT_CANONICAL`                    | UI belongs in frontend.                                                                                        | TS import API returns review-required job payload.           | Keep frontend UX; remove backend-like TS orchestration.                                  |
| Audit/governance            | `JAVA_CANONICAL`                          | Must be server-side and tamper-resistant.                                                                      | TS route logs audit in legacy path.                          | Move import/compile audit event emission to Java; frontend displays status.              |

---

## E. Prescriptive File-by-File TODO Plan

| Priority | Phase                 | File path                                                                                                                          | Action        | Current issue                                                                                                     | Required change                                                                                                                                                            | Tests                                                        |
| -------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ------------- | ----------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| P0       | Foundation            | `products/yappc/frontend/libs/yappc-artifact-compiler/src/model/types.ts`                                                          | `MODIFY`      | Deterministic IDs are strings, but `elementIndex`, `residualIslandIds`, and many nested IDs still require UUIDs.  | Introduce branded string schemas: `ModelElementIdSchema`, `ArtifactNodeIdSchema`, `ResidualIslandIdSchema`; replace UUID-only references unless truly user-generated UUID. | `src/model/__tests__/deterministic-id-contract.test.ts`      |
| P0       | Foundation            | `products/yappc/frontend/libs/yappc-artifact-compiler/src/residual/types.ts`                                                       | `MODIFY`      | `linkedModelElementIds` still UUID-only and `placeholder-stub` exists as regeneration strategy.                   | Allow deterministic model IDs; replace `placeholder-stub` with `require-manual-impl` or feature-flag unsupported stub generation.                                          | `src/residual/__tests__/residual-contract.test.ts`           |
| P0       | Foundation            | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/types.ts`                                                   | `MODIFY`      | `buildChangePlan` emits component add/remove for all element kinds and only deeply diffs component props.         | Make `buildChangePlan` kind-aware; unsupported model edits emit `manual-review` or `unsupported-operation`, never wrong component operations.                              | `src/compile-back/__tests__/change-plan-kind-safety.test.ts` |
| P0       | Source runtime        | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/source/SourceLocator.java`                              | `ADD`         | Source locator is TS-only.                                                                                        | Add Java canonical source locator with provider, repoId, ref, path, credentialRef, tenant/workspace/project scope metadata.                                                | `SourceLocatorTest.java`                                     |
| P0       | Source runtime        | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/source/RepositorySnapshot.java`                         | `ADD`         | Repository snapshot exists TS-side only.                                                                          | Add immutable snapshot DTO with snapshotId, provider, repoId, commitSha/contentHash, materialized root, file count, checksum, diagnostics.                                 | `RepositorySnapshotTest.java`                                |
| P0       | Source runtime        | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceProvider.java`                           | `ADD`         | Java has no canonical provider SPI.                                                                               | Add Java source provider interface: `canHandle`, `resolve`, `capabilities`, `credentialRequirements`.                                                                      | `SourceProviderRegistryTest.java`                            |
| P0       | Source runtime        | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceImportService.java`                      | `ADD`         | Java service interface lacks source import and snapshot job lifecycle.                                            | Add durable `startImport`, `getJob`, `cancelJob`, `retryJob`, `runCompilePipeline` APIs.                                                                                   | `SourceImportServiceTest.java`                               |
| P0       | Source runtime        | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`                              | `MODIFY`      | Controller requires workspace header but service scope only carries project/tenant.                               | Replace header-trust with authenticated principal/resource-registry scope resolution; pass `tenantId`, `workspaceId`, `projectId` through scope.                           | `ArtifactGraphControllerScopeTest.java`                      |
| P0       | Source runtime        | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactRequestScope.java`                   | `MODIFY`      | Scope is inferred from usage as project/tenant only.                                                              | Add `workspaceId`; reject empty scope; update all service/repository calls.                                                                                                | `ArtifactRequestScopeTest.java`                              |
| P0       | Persistence           | `products/yappc/core/yappc-services/src/main/resources/db/migration/V_NEXT__artifact_source_snapshots.sql`                         | `ADD`         | No inspected durable source job/snapshot tables.                                                                  | Add `source_import_jobs`, `repository_snapshots`, `repository_snapshot_files`, `artifact_compile_runs`, `artifact_patch_sets`. Include tenant/workspace/project indexes.   | Migration integration test                                   |
| P0       | Persistence           | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`                          | `MODIFY`      | Repository stores tenant/project but not workspace; edge conflict key appears tenant/source/target/type scoped.   | Add workspace scope; update unique constraints to include tenant/workspace/project; persist snapshot/version consistently.                                                 | `ArtifactGraphRepositoryScopeTest.java`                      |
| P0       | Contracts             | `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`                                                        | `MODIFY`      | Java/TS contracts are not visibly canonical.                                                                      | Define canonical `SourceLocator`, `RepositorySnapshot`, `ArtifactGraph`, `SemanticProductModel`, `ResidualIsland`, `ChangePlan`, `PatchSet`, `ReviewBundle`.               | Proto generation tests                                       |
| P1       | TS worker             | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`                                           | `MODIFY`      | TS worker exported, but Java orchestration boundary is not validated.                                             | Define strict JSON/proto worker request/response; include extractor ID/version, diagnostics, residuals, unresolved edges.                                                  | `worker-contract.test.ts`                                    |
| P1       | Java worker client    | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/worker/TypeScriptExtractorWorkerClient.java` | `ADD`         | Java parser explicitly says use frontend TS compiler for TS/JS.                                                   | Add Java client to invoke TS worker with timeout, bounded concurrency, structured diagnostics, no raw credentials.                                                         | `TypeScriptExtractorWorkerClientTest.java`                   |
| P1       | Pipeline              | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/pipeline.ts`                                                   | `MODIFY`      | Low-confidence residual creation preserves full file, not precise fragment.                                       | Preserve exact source span when extractor provides it; only use full-file residual as fallback with high review risk.                                                      | `pipeline-residual-span.test.ts`                             |
| P1       | Graph                 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/types.ts`                                                          | `MODIFY`      | `GraphNode.sourceLocation` is required even for synthetic nodes; no explicit synthetic rationale field.           | Add `syntheticReason` and allow source location only for source-derived nodes, or require synthetic source location convention.                                            | `graph-synthetic-node.test.ts`                               |
| P1       | Java graph query      | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`                              | `MODIFY`      | Query endpoint calls service with `cursor=null` and `pageSize=100`, ignoring payload pagination.                  | Parse and validate `cursor`, `limit`, `includeUnresolvedEdges`, `snapshotId`; pass to service.                                                                             | `ArtifactGraphQueryPaginationTest.java`                      |
| P1       | Java graph service    | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`               | `MODIFY`      | `queryGraph` computes orphaned/dependency stats on one page only.                                                 | Move query semantics into repository/query engine with full-graph or indexed query semantics; page final results only.                                                     | `ArtifactGraphQuerySemanticsTest.java`                       |
| P1       | Fastify source import | `products/yappc/frontend/apps/api/src/routes/source-imports.ts`                                                                    | `CONSOLIDATE` | Legacy TS route can still run production-like source import when flag enabled.                                    | Make route a strict proxy/client to Java API in production; move legacy TS path to dev/test-only route or remove.                                                          | `source-imports.proxy-only.test.ts`                          |
| P1       | Compile-back          | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/patch-coordinator.ts`                                       | `MODIFY`      | Review/rollback metadata exists, but no durable apply/rollback workflow.                                          | Split TS emitter coordination from Java-owned patch lifecycle; TS returns emitted patches only.                                                                            | `patch-coordinator-worker-mode.test.ts`                      |
| P2       | Extractors            | `products/yappc/frontend/libs/yappc-artifact-compiler/src/extractors/extractor-registry.ts`                                        | `MODIFY`      | Registry catches registration failure and only warns.                                                             | Add capability diagnostics and production fail-closed mode.                                                                                                                | `extractor-registry-fail-closed.test.ts`                     |
| P2       | Java parser           | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`               | `SPLIT`       | `parseSourceArtifact` is embedded in graph service and returns raw maps.                                          | Move to `JavaArtifactExtractor`, `SqlArtifactExtractor`, `WorkflowArtifactExtractor` plugins with typed DTOs.                                                              | Parser plugin tests                                          |
| P3       | Docs                  | `products/yappc/docs/architecture/ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md`                                                    | `MODIFY`      | Architecture doc exists, but implementation has shifted to Java-proxy migration and TS worker split.              | Update to Java-canonical orchestration + TS worker architecture.                                                                                                           | Docs lint/review                                             |

---

## F. Phase Plan

### Phase 1: Contract and ID hardening

**Goal:** Make graph/model/residual IDs coherent and contract-safe.

**Files:**

* `src/model/types.ts`
* `src/residual/types.ts`
* `src/compile-back/types.ts`
* `src/graph/types.ts`
* `artifact_compiler.proto`

**Exit criteria:**

* Deterministic URN IDs pass all graph/model/residual schemas.
* No UUID-only constraint remains for source-derived IDs.
* No production regeneration strategy emits placeholder stubs.

---

### Phase 2: Java source import and snapshot foundation

**Goal:** Java becomes canonical owner of governed source acquisition.

**Files to add:**

* `domain/source/SourceLocator.java`
* `domain/source/RepositorySnapshot.java`
* `services/source/SourceProvider.java`
* `services/source/SourceImportService.java`
* `storage/SourceImportJobRepository.java`
* `storage/RepositorySnapshotRepository.java`
* `db/migration/V_NEXT__artifact_source_snapshots.sql`

**Exit criteria:**

* GitHub/local/archive import creates durable job.
* Snapshot is immutable and commit/content pinned.
* Job can be queried, retried, canceled, and audited.

---

### Phase 3: Java-orchestrated TS extractor worker

**Goal:** Keep TS/TSX extraction in TypeScript, but under Java orchestration.

**Files:**

* `src/worker/ts-extractor-worker.ts`
* `services/artifact/worker/TypeScriptExtractorWorkerClient.java`
* `artifact_compiler.proto`

**Exit criteria:**

* Java can invoke TS worker for TS/TSX files.
* Worker returns nodes, unresolved edges, model elements, residuals, diagnostics.
* Java persists results with tenant/workspace/project/snapshot scope.

---

### Phase 4: Durable graph/model persistence and query correctness

**Goal:** Persist snapshot-aware graph/model versions with complete scope and query semantics.

**Files:**

* `ArtifactGraphRepository.java`
* `ArtifactModelVersionRepository.java`
* `ArtifactGraphServiceImpl.java`
* DB migrations.

**Exit criteria:**

* Graph data is scoped by tenant/workspace/project/snapshot/version.
* Query pagination does not change query semantics.
* Unresolved edges, resolution records, residuals, and model versions persist durably.

---

### Phase 5: Compile-back and review lifecycle

**Goal:** Support safe minimal patches and review bundles.

**Files:**

* Java `ChangePlanService`
* Java `PatchSetService`
* Java `ReviewBundleService`
* TS `ReactPatchEmitter`
* TS patch worker protocol.

**Exit criteria:**

* No-op round-trip produces zero diff.
* Supported TS component rename produces minimal patch.
* Unsupported/low-confidence/residual-overlap changes require review.
* Patch apply and rollback are audited.

---

### Phase 6: Frontend UX simplification

**Goal:** Frontend becomes a clean review/control surface, not compiler backend.

**Files:**

* `products/yappc/frontend/apps/api/src/routes/source-imports.ts`
* `products/yappc/frontend/web/src/services/compiler/ImportSourceWorkflow.ts`
* `products/yappc/frontend/web/src/components/canvas/page/artifactCompilerBridge.ts`

**Exit criteria:**

* UI shows provider, progress, summary, confidence, skipped files, residuals, patch review, validation, apply/reject.
* TS Fastify route is proxy-only in production.

---

## G. Test Plan

| Priority | Test file path                                                                                                                         | Scenario                                                      | Expected assertion                                              |
| -------- | -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- | --------------------------------------------------------------- |
| P0       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/model/__tests__/deterministic-id-contract.test.ts`                           | Parse model with deterministic `artifact://` graph/model IDs. | Passes without UUID validation failure.                         |
| P0       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/__tests__/change-plan-kind-safety.test.ts`                      | Add/remove page/API/data/workflow model elements.             | Does not emit component-specific operations for non-components. |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/ArtifactGraphControllerScopeTest.java`                         | Payload project/tenant mismatch and missing workspace.        | Rejects with 403/400; never trusts request body scope.          |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/SourceImportServiceTest.java`                      | Start GitHub import job with commit ref.                      | Creates durable job and immutable snapshot.                     |
| P0       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/storage/ArtifactGraphRepositoryScopeTest.java`                     | Same node IDs in two projects/workspaces.                     | No cross-project/workspace overwrite.                           |
| P0       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/__tests__/noop-roundtrip-zero-diff.test.ts`                        | Source → model → no model change → patch.                     | Patch set is empty / zero diff.                                 |
| P1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/__tests__/gitignore-edge-cases.test.ts`                            | Nested `.gitignore`, negation, anchored paths.                | Skipped artifacts exactly match git behavior.                   |
| P1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/__tests__/symbol-resolution-lifecycle.test.ts`                     | Ambiguous/unresolved/cross-repo refs.                         | Resolved edges use node IDs; unresolved refs stay separate.     |
| P1       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/artifact/worker/TypeScriptExtractorWorkerClientTest.java` | Java invokes TS worker.                                       | Timeout, diagnostics, and output contract are enforced.         |
| P1       | `products/yappc/frontend/apps/api/src/routes/__tests__/source-imports.proxy-only.test.ts`                                              | Production route with legacy flag off.                        | Proxies to Java and does not run TS local import pipeline.      |
| P1       | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/artifact/ArtifactGraphQuerySemanticsTest.java`            | Orphan/dependency query with pagination.                      | Results are semantically correct independent of page size.      |
| P1       | `products/yappc/frontend/web/src/services/compiler/__tests__/patch-review-flow.spec.ts`                                                | Review generated patch.                                       | Shows validation, residual overlaps, approve/reject state.      |

---

## H. Critical Questions

### 1. Is the current system truly round-trip capable?

**Answer:** No.

**Evidence:** Compile-back types, patch sets, review bundles, rollback metadata, and patch coordination exist, but implementation is partial and emitter coverage is limited.  

**Required fix:** Add no-op round-trip zero-diff tests, Java-owned patch lifecycle, TS worker patch emission, durable review/apply/rollback.

---

### 2. Can it scan a full GitHub repo today, or only individual sources/files?

**Answer:** Partial.

**Evidence:** TS GitHub provider resolves a recursive GitHub tree and materializes blobs into a snapshot, and the TS source-import route can run `SynthesisPipeline.runFromSnapshot`.   

**Limitation:** It is TS/Fastify-side, not Java durable, and GitHub tree truncation fails closed for large repos.

---

### 3. Are artifact IDs deterministic?

**Answer:** Partial.

**Evidence:** `buildDeterministicNodeId` returns deterministic URNs when `snapshotRef` exists, but falls back to `crypto.randomUUID()` when absent. 

**Required fix:** Require `snapshotRef` for repository imports and reject production scans without stable source anchor.

---

### 4. Are graph edges valid and resolved?

**Answer:** Partial.

**Evidence:** TS has resolved/unresolved edge schemas, symbol resolution, and graph validation.   

**Required fix:** Persist unresolved edges and resolution records through Java canonical contracts; validate before DB writes.

---

### 5. Is there a complete synthesis pipeline?

**Answer:** Partial.

**Evidence:** TS `SynthesisPipeline` scans, extracts, resolves, assembles graph, validates, and builds semantic model.  

**Required fix:** Move orchestration to durable Java jobs and call TS extractors as workers.

---

### 6. Is compile-back/patch generation implemented?

**Answer:** Partial.

**Evidence:** `ChangeOp`, `PatchSet`, `PatchEmitter`, `buildChangePlan`, `PatchCoordinator`, validation, review bundle, rollback metadata exist.  

**Required fix:** Make buildChangePlan kind-safe, add emitters, implement Java-owned apply/rollback and no-op round-trip tests.

---

### 7. Are residual islands preserved?

**Answer:** Partial.

**Evidence:** Residual schema contains raw fragment ref, checksum, risk, source location, and pipeline converts low-confidence elements to residual islands.  

**Required fix:** Preserve precise spans, remove production stub strategy, persist full residual content/metadata Java-side.

---

### 8. Are source import jobs durable?

**Answer:** Partial / conflicting.

**Evidence:** TS Fastify route creates source import jobs and polling endpoint, but production path proxies to Java when legacy flag is off; Java service interface inspected does not expose source import jobs.   

**Required fix:** Implement Java `SourceImportService` and make TS route proxy-only.

---

### 9. Is tenant/workspace/project scope enforced consistently?

**Answer:** Partial.

**Evidence:** Java controller validates principal tenant and requires workspace/project headers, but service/repository interface shown passes tenant/project, not workspace.   

**Required fix:** Add workspace scope to `ArtifactRequestScope`, repositories, tables, indexes, and all graph/import/model/patch APIs.

---

### 10. What is the smallest trustworthy milestone?

**Answer:** **Milestone 1: Java-owned Source Snapshot Compiler Foundation.**

Deliver:

1. Java `SourceImportService`.
2. Durable source import jobs.
3. Immutable repository snapshots.
4. Java-owned scope enforcement.
5. TS extractor worker boundary.
6. Deterministic inventory contract.
7. Persisted graph/unresolved/residual/model version records.
8. No-op round-trip scaffold test.

---
