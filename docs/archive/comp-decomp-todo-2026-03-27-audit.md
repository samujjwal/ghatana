# Artifact Compiler/Decompiler Production-Readiness Audit — `samujjwal/ghatana`

**Target commit:** `8d05e45efc19a70821778fca554e402863feab3a`

The target commit exists and is a merge commit whose visible diff only updates `products/yappc/CHANGELOG.md`; this audit treats the repository state at that commit as the source of truth, not the commit diff.  The uploaded prompt requires objective current status first, followed by prescriptive file-by-file TODOs with exact validation. 

I inspected the high-leverage artifact compiler/decompiler paths at the target ref. Areas not directly inspected are marked `UNVERIFIED` instead of assumed.

---

## Section A: Objective Current Status

| Area                               |                                                   Status | Evidence from current code                                                                                                                                                                                                                                                  | Objective conclusion                                                                                                                                                                        | Production impact                                            |
| ---------------------------------- | -------------------------------------------------------: | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| Artifact compiler package          |                                  `PARTIALLY_IMPLEMENTED` | Package exports inventory, graph, model, extractors, provenance, residual, merge, synthesis, source-providers, compile-back, builder.  Root barrel re-exports these modules.                                                                                                | Real package exists, but production capability depends on actual extractor, backend, UX, persistence, and patch integration.                                                                | Good foundation, not enough for world-class round-trip.      |
| Source provider abstraction        |                                  `PARTIALLY_IMPLEMENTED` | `SourceProvider`, `SourceLocator`, `RepositorySnapshot`, registry, credential policy exist.                                                                                                                                                                                 | Abstraction exists, but registry dispatch ignores typed provider intent and uses `canHandle` on normalized strings.                                                                         | Provider ambiguity and governance gaps.                      |
| GitHub source acquisition          |                                  `PARTIALLY_IMPLEMENTED` | GitHub provider resolves commit SHA, fetches recursive tree, materializes files, fails on truncated tree.                                                                                                                                                                   | Repo snapshot support exists, but uses raw token option, random temp roots, no durable backend job integration, no retry/rate-limit governance.                                             | Repo import possible, but not production-governed.           |
| GitLab source acquisition          |                                  `PARTIALLY_IMPLEMENTED` | GitLab provider resolves commit, paginates repository tree, materializes files.                                                                                                                                                                                             | Basic support exists, but nested GitLab groups, credentialRef resolution, retry/rate limits, and typed dispatch are weak.                                                                   | Partial provider readiness.                                  |
| Local folder source acquisition    |                                  `PARTIALLY_IMPLEMENTED` | Local provider reads git metadata, remote URL, dirty status, file metadata, and content hash fallback.                                                                                                                                                                      | Useful local snapshot support exists, but trust boundary and server runtime policy are not fully modeled.                                                                                   | Unsafe if exposed beyond trusted runtime.                    |
| ZIP source acquisition             |                                  `PARTIALLY_IMPLEMENTED` | ZIP provider parses central directory, content-hashes archive, extracts with zip-slip containment guard.                                                                                                                                                                    | ZIP exists, but tar archive support, skipped-entry diagnostics, unsupported compression diagnostics, and credential governance are missing.                                                 | Archive import is incomplete.                                |
| Durable source import jobs         |    `PARTIALLY_IMPLEMENTED` / `DUPLICATED_OR_CONFLICTING` | Fastify source import route creates and updates jobs through `getJobRepository`.  DB migration creates `source_import_jobs`.  Search also shows Java `SourceImportJobRepository`.                                                                                           | Durable job concept exists, but ownership is split between Fastify, DB migration, and Java service.                                                                                         | Operational consistency risk.                                |
| Deterministic inventory scanner    |                                  `PARTIALLY_IMPLEMENTED` | Scanner config includes snapshotRef, `.gitignore`, include/exclude, max size, symlink policy; directory entries are sorted; IDs use deterministic helper when snapshotRef exists.  Inventory schema records snapshotRef, skipped artifacts, package boundaries, summaries.  | Scanner is real, but deterministic identity falls back to random UUID without snapshotRef; `.gitignore` parser is homegrown; bounded concurrency and exact gitignore parity are not proven. | Foundation is useful but not fully trustable.                |
| Artifact graph schema              | `IMPLEMENTED` frontend / `PARTIALLY_IMPLEMENTED` backend | TS graph schema has nodes, resolved edges, unresolved edges, resolution records, source locations, provenance, confidence, residual refs.  Java DTOs are much thinner.                                                                                                      | Frontend IR is richer than backend persistence contract.                                                                                                                                    | Fidelity is lost when graph crosses backend boundary.        |
| Resolved/unresolved edge lifecycle |                                  `PARTIALLY_IMPLEMENTED` | TS graph separates `GraphEdge` from `UnresolvedGraphEdge`; resolver emits resolved edges and records.   Graph validation checks edge source/target IDs exist.                                                                                                               | Frontend lifecycle exists; backend ingest has no first-class unresolved edge or resolution-record DTO/table.                                                                                | Backend can persist invalid or lossy graph semantics.        |
| Semantic product model             |                                  `PARTIALLY_IMPLEMENTED` | Model schemas include component, page, layout, token/theme/style, data entity, API, state, interaction, cache, workflow, provenance, graphNodeIds, residualIslandIds.                                                                                                       | Rich schema exists, but synthesis completeness depends on extractors; model ID is random and backend model persistence is not coherently wired.                                             | Model layer exists but is not production round-trip durable. |
| Residual islands                   |                                  `PARTIALLY_IMPLEMENTED` | Residual schema preserves originalSource, reason, sourceLocation, extractor metadata, rawFragmentRef, checksum, risk, related graph nodes.  Pipeline also creates residuals for low-confidence extraction.                                                                  | Schema is strong, but production detection, backend persistence, UI visibility, and patch preservation are incomplete.                                                                      | Source fidelity not guaranteed end-to-end.                   |
| Synthesis pipeline                 |                                  `PARTIALLY_IMPLEMENTED` | Pipeline runs scan → extract → resolve → assemble graph → validate → model.  Source import route invokes pipeline with `extractors: []`.                                                                                                                                    | Pipeline skeleton exists, but current API path produces no extracted graph/model content for repository imports.                                                                            | Major production blocker.                                    |
| Compile-back / patch generation    |          `STUB_OR_PLACEHOLDER` / `PARTIALLY_IMPLEMENTED` | Compile-back types and coordinator exist.   React emitter sync `emit()` deliberately no-ops and async supports only rename/add-prop.                                                                                                                                        | Not true round-trip. It is a limited React patch prototype.                                                                                                                                 | Cannot safely modify source at production level.             |
| Backend graph API                  |                                  `PARTIALLY_IMPLEMENTED` | Java controller exposes ingest/analyze/merge/query/residual and resolves tenant from `Principal`.  Service and repository persist graph nodes/edges.                                                                                                                        | Tenant is protected better than before, but product/project scope still comes from request payload and no workspace/project registry validation is visible.                                 | Cross-project access risk remains.                           |
| Backend graph persistence          |                                  `PARTIALLY_IMPLEMENTED` | Tables for nodes/edges exist.  Snapshot/tombstone columns added in V11 and again in V14.   Repository has upsert/tombstone/pagination methods.                                                                                                                              | Persistence exists, but migration intent is duplicated, unresolved edges/residuals/patches are not first-class, and `contentChecksum` parameter is ignored by upsert alias.                 | History and drift support remain incomplete.                 |
| Frontend/API source import         |                                  `PARTIALLY_IMPLEMENTED` | Fastify route validates scope headers, source type, source locator, runs provider resolution, creates review-required job.                                                                                                                                                  | It is import/review-only and uses no extractors for repo imports; web UI integration was not directly verified.                                                                             | User-facing compiler flow is not end-to-end.                 |
| Tests and fixtures                 |                                             `UNVERIFIED` | Package has test scripts in `package.json`.  Specific golden round-trip fixture tests were not verified.                                                                                                                                                                    | Test presence is not evidence of required coverage.                                                                                                                                         | Production confidence missing.                               |
| Overall round-trip capability      |                                  `UNSAFE_FOR_PRODUCTION` | Compile-back is limited; source import route has empty extractor pipeline; backend DTO loses IR fidelity.                                                                                                                                                                   | Current implementation is not true round-trip capable.                                                                                                                                      | P0 blocker.                                                  |

### Maturity verdict

```text
Current capability classification:
- Source acquisition: snapshot-capable library, partially governed API
- Inventory: repo-capable, partially deterministic
- Graph: source-faithful schema in TypeScript, lossy backend persistence
- Semantic model: schema/provenance-aware, synthesis incomplete in API path
- Compile-back: limited React prototype, not round-trip safe
- Overall: not production-ready; foundation exists but is not trustworthy end-to-end
```

---

## Section B: Evidence-Based Current Code Map

| Capability area       | Current file/module                                                 | What exists objectively                                                                                          | What is missing objectively                                                                                            | Keep/modify/remove/consolidate                            | Evidence quality |
| --------------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- | ---------------- |
| Package boundary      | `products/yappc/frontend/libs/yappc-artifact-compiler/package.json` | Published library shape with many exports.                                                                       | No proof all exported capabilities are production-complete.                                                            | Keep; harden exports and tests.                           | `DIRECT_CODE`    |
| Root exports          | `src/index.ts`                                                      | Barrel exports all compiler layers.                                                                              | Does not prove wiring into app/backend.                                                                                | Keep.                                                     | `DIRECT_CODE`    |
| Inventory types       | `src/inventory/types.ts`                                            | File classification, skipped artifacts, package boundaries, inventory summary.                                   | Skipped reasons missing generated/vendor/binary first-class reasons; source-file refs are not strong enough.           | Modify.                                                   | `DIRECT_CODE`    |
| Scanner               | `src/inventory/scanner.ts`                                          | Recursive local scan, `.gitignore`, generated/binary heuristics, boundaries, deterministic IDs with snapshotRef. | No proven bounded concurrency; gitignore parity is partial; volatile timestamps complicate deterministic golden tests. | Modify.                                                   | `DIRECT_CODE`    |
| Source provider core  | `src/source-providers/types.ts`                                     | `SourceProvider`, `SourceLocator`, `RepositorySnapshot`, registry, credential policy.                            | Typed provider dispatch and secret-reference resolver are incomplete.                                                  | Modify.                                                   | `DIRECT_CODE`    |
| GitHub provider       | `src/source-providers/github-provider.ts`                           | Commit pinning and recursive tree materialization.                                                               | Durable server job integration, credentialRef, retry/rate-limit, cleanup, large repo fallback.                         | Modify.                                                   | `DIRECT_CODE`    |
| GitLab provider       | `src/source-providers/gitlab-provider.ts`                           | Commit pinning and paginated materialization.                                                                    | Nested groups, credentialRef, provider dispatch safety.                                                                | Modify.                                                   | `DIRECT_CODE`    |
| ZIP provider          | `src/source-providers/zip-provider.ts`                              | ZIP extraction with zip-slip guard.                                                                              | Tar support, skipped diagnostics, compression diagnostics.                                                             | Modify/add archive provider.                              | `DIRECT_CODE`    |
| Graph schema          | `src/graph/types.ts`                                                | Rich IR with nodes, edges, unresolved edges, source metadata.                                                    | Backend parity; query result cursor; stronger invariants.                                                              | Modify.                                                   | `DIRECT_CODE`    |
| Graph validation      | `src/graph/validateGraph.ts`                                        | Checks duplicate nodes, missing edge endpoints, confidence, index consistency.                                   | Not exported from graph barrel; lacks unresolved lifecycle and raw-name edge validation.                               | Modify/export.                                            | `DIRECT_CODE`    |
| Synthesis pipeline    | `src/synthesis/pipeline.ts`                                         | Full orchestrator skeleton.                                                                                      | API route passes no extractors; deterministic model identity incomplete.                                               | Modify.                                                   | `DIRECT_CODE`    |
| Symbol resolver       | `src/synthesis/symbol-resolver.ts`                                  | Resolves unresolved edges and records outcomes.                                                                  | Edge IDs are ad hoc strings; alias config not sourced from scan config/package metadata.                               | Modify.                                                   | `DIRECT_CODE`    |
| Semantic model        | `src/model/types.ts`                                                | Rich model schemas with provenance and graph links.                                                              | Backend persistence, deterministic identity, model diffs/version storage.                                              | Modify + backend additions.                               | `DIRECT_CODE`    |
| Residuals             | `src/residual/types.ts`                                             | Residual schema with source, checksum, risk.                                                                     | `placeholder-stub` strategy is allowed; backend/UI preservation unverified.                                            | Modify.                                                   | `DIRECT_CODE`    |
| Compile-back types    | `src/compile-back/types.ts`                                         | Change ops, patch sets, validation, review bundle, rollback metadata.                                            | Too React/component-heavy; no broad model change coverage.                                                             | Modify.                                                   | `DIRECT_CODE`    |
| Patch coordinator     | `src/compile-back/patch-coordinator.ts`                             | Emits patches, dry-run validation, review bundle.                                                                | No apply, no command validation, stderr logger, unsupported op handling is weak.                                       | Modify.                                                   | `DIRECT_CODE`    |
| React patch emitter   | `src/compile-back/react-patch-emitter.ts`                           | Async rename/add-prop patch support.                                                                             | Sync emits empty; unsupported operations silently no-op; regex-based instead of AST-safe.                              | Modify.                                                   | `DIRECT_CODE`    |
| Java graph API        | `ArtifactGraphController.java`                                      | ActiveJ endpoints and principal-derived tenant.                                                                  | Project/workspace authorization not proven; graph validation not enforced before persistence.                          | Modify.                                                   | `DIRECT_CODE`    |
| Java graph service    | `ArtifactGraphServiceImpl.java`                                     | Ingest, analysis, merge, query, residual analysis.                                                               | TS/JS parse stub, residual analysis placeholder, query ignores pagination methods.                                     | Modify/remove stub behavior.                              | `DIRECT_CODE`    |
| Java graph repository | `ArtifactGraphRepository.java`                                      | JDBC persistence, upsert, tombstone, pagination helpers.                                                         | Default `Runnable::run`, contentChecksum ignored, unresolved/residual/patch tables absent.                             | Modify.                                                   | `DIRECT_CODE`    |
| Fastify source import | `source-imports.ts`                                                 | Scope headers, source validation, provider registry, jobs, audit.                                                | Uses `extractors: []`; does not persist full graph/model/patch; header auth inconsistent with Java principal.          | Modify/consolidate.                                       | `DIRECT_CODE`    |
| DB migrations         | `V10`, `V11`, `V14`                                                 | Artifact graph and source job tables.                                                                            | Snapshot columns duplicated in V11 and V14; missing snapshot inventory, unresolved edge, residual, patch tables.       | Add corrective migration; do not edit applied migrations. | `DIRECT_CODE`    |

---

## Section C: Gap Analysis Against Target State

| Capability                    | Current state                                                                  | Gap                                                                               | Severity | Required fix                                                                                                                    |
| ----------------------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------- | -------: | ------------------------------------------------------------------------------------------------------------------------------- |
| True round-trip               | Limited compile-back prototype.                                                | No source → model → patch → source → model guarantee.                             |       P0 | Build no-op round-trip harness, patch apply/dry-run, residual-preservation validation, and fixture tests before broad emitters. |
| Repository import through API | Provider can snapshot repo, but route runs pipeline with `extractors: []`.     | API succeeds with no meaningful extraction.                                       |       P0 | Register real extractor capability registry or return explicit unsupported state.                                               |
| Backend graph fidelity        | TS graph is rich; Java DTOs are thin.                                          | SourceLocation, confidence, provenance, unresolved edges, residual refs are lost. |       P0 | Extend DTOs/tables and validate graph before ingest.                                                                            |
| Scope enforcement             | Controller enforces tenant from principal; productId remains payload-derived.  | Project/workspace authorization not proven.                                       |       P0 | Resolve workspace/project from principal/resource registry server-side.                                                         |
| Durable source jobs           | Job concepts exist in Fastify and DB; Java job repo found.                     | Split ownership.                                                                  |       P1 | Pick one canonical source import job service and make other layer a client.                                                     |
| Deterministic scan            | SnapshotRef IDs are deterministic; missing snapshotRef falls back random.      | Golden no-op scans can drift.                                                     |       P1 | Require snapshotRef for repo scans; isolate volatile timestamps from identity comparisons.                                      |
| GitHub/GitLab governance      | Raw tokens accepted in server options; browser only rejects raw creds.         | Secret references not actually resolved.                                          |       P1 | Add credential resolver interface and audit-redacted provider diagnostics.                                                      |
| Patch generation              | React emitter claims several ops but only implements async rename/add-prop.    | Silent no-op patch risk.                                                          |       P0 | Make unsupported ops explicit `review-required/unsupported`, not empty success.                                                 |
| DB model                      | Nodes/edges exist; snapshot tracking duplicated across migrations.             | Missing repository snapshots, inventory, unresolved edges, residuals, patches.    |       P1 | Add V15 corrective migration with full IR persistence.                                                                          |
| Backend parsing               | Java service returns stub-like unparsed nodes for unsupported languages.       | Production stub behavior remains.                                                 |       P0 | Remove from production path or gate behind explicit unsupported feature flag.                                                   |
| UI import/review              | API supports review-required response.                                         | Web UI path and canvas integration not verified.                                  |       P1 | Add import summary, residuals, confidence, patch review, drift UI wired to real backend.                                        |

---

## Section D: Architecture Decisions

### Decision 1: Canonical compiler/decompiler engine ownership

* **Decision:** `products/yappc/frontend/libs/yappc-artifact-compiler` remains the canonical source-to-IR/model/patch engine.
* **Why:** It already owns source providers, inventory, graph, synthesis, residual, and compile-back exports. 
* **Files to keep:** `products/yappc/frontend/libs/yappc-artifact-compiler/src/**`
* **Files to modify:** scanner, source providers, graph validation, synthesis, compile-back.
* **Alternatives rejected:** Java service as primary parser for TS/JS, because Java currently returns stub-like unsupported nodes for many languages. 

### Decision 2: Canonical durable governance/API ownership

* **Decision:** Java `yappc-services` owns durable graph/model/snapshot/patch persistence and server-side authorization.
* **Why:** Java controller already uses authenticated `Principal` for tenant scope and repository persists graph nodes/edges.  
* **Files to modify:** `ArtifactGraphController.java`, `ArtifactGraphServiceImpl.java`, `ArtifactGraphRepository.java`, domain DTOs, migrations.
* **Alternatives rejected:** Fastify route as sole durable owner, because durable tables and graph service already live in Java.

### Decision 3: Source import job ownership

* **Decision:** Consolidate around one durable job model and expose it through both APIs only as clients.
* **Why:** Fastify route uses `getJobRepository`, DB has `source_import_jobs`, and Java repository also appears.   
* **Files affected:** `source-imports.ts`, `job-repository.ts`, `SourceImportJobRepository.java`, V15 migration.
* **Validation:** Same job can be created, polled, cancelled, resumed, and audited from a single canonical table/API.

### Decision 4: Graph schema parity

* **Decision:** Backend DTOs must mirror the TS graph IR, not compress it into generic node/edge DTOs.
* **Why:** TS graph carries sourceLocation, extractor, confidence, provenance, unresolved edges, and residual refs.  Java DTOs currently do not.  
* **Validation:** Backend rejects any resolved edge whose target node does not exist and persists unresolved edges separately.

### Decision 5: Patch generation strategy

* **Decision:** Patch emitters must be capability-declared, AST-backed where possible, and return explicit unsupported/review-required outcomes.
* **Why:** Current React emitter silently no-ops for several operations it claims to handle. 
* **Validation:** Unsupported operation creates review-required result, never an empty successful patch set.

---

## Section E: Prescriptive File-by-File TODO Plan

| Priority | Phase   | File path                                                                                                             | Action               | Current issue                                                                                                                          | Required change                                                                                                                                                                                                             | Tests                                                                              |
| -------: | ------- | --------------------------------------------------------------------------------------------------------------------- | -------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- |
|       P0 | 1       | `products/yappc/frontend/apps/api/src/routes/source-imports.ts`                                                       | MODIFY               | Repository import runs `SynthesisPipeline({ extractors: [] })`, producing no extracted graph/model.                                    | Register canonical extractor registry, or fail with `UNSUPPORTED_EXTRACTION_PIPELINE` before review success. Persist snapshot/graph/model IDs in response.                                                                  | `source-imports.repo-import.test.ts`                                               |
|       P0 | 1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/index.ts`                                             | MODIFY               | `validateGraph` exists but is not exported from graph barrel.                                                                          | Export `validateGraph`, `GraphValidationResult`, `GraphValidationError`.                                                                                                                                                    | `graph/index.exports.test.ts`                                                      |
|       P0 | 1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/validateGraph.ts`                                     | MODIFY               | Validation checks existing node IDs but not unresolved lifecycle consistency.                                                          | Add checks: every resolved edge target/source exists; no edge target is raw label when missing node; every `resolved` record target exists; every unresolved edge appears in records or remaining set; source ranges valid. | `graph/validateGraph.test.ts`                                                      |
|       P0 | 1       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ArtifactGraphIngestRequest.java`  | MODIFY               | Request has only productId, tenantId, nodes, edges.                                                                                    | Add `snapshotRef`, `snapshotId`, `versionId`, `contentChecksum`, `unresolvedEdges`, `edgeResolutionRecords`, `residualIslandIds`. Stop extracting these from node metadata.                                                 | `ArtifactGraphIngestRequestJsonTest.java`                                          |
|       P0 | 1       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ArtifactNodeDto.java`             | MODIFY               | DTO loses sourceLocation, extractor, confidence, provenance, residual refs.                                                            | Add fields matching TS `GraphNode`: sourceLocation, extractorId, extractorVersion, confidence, provenance, privacySecurityFlags, residualFragmentIds, sourceRef, symbolRef.                                                 | `ArtifactNodeDtoRoundTripTest.java`                                                |
|       P0 | 1       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ArtifactEdgeDto.java`             | MODIFY               | DTO lacks edgeId, confidence, and validation metadata.                                                                                 | Add `edgeId`, `confidence`, `bidirectional`, `metadata`, `snapshotId`, `versionId`; enforce source/target IDs as node IDs.                                                                                                  | `ArtifactEdgeDtoValidationTest.java`                                               |
|       P0 | 1       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`                 | MODIFY               | Tenant comes from principal, but product/project scope still comes from request body.                                                  | Resolve project/workspace access from principal/resource registry; reject body scope manipulation for tenant/workspace/project. Validate graph before service call.                                                         | `ArtifactGraphControllerScopeTest.java`                                            |
|       P0 | 1       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`  | MODIFY               | `parseSourceArtifact` returns stub-like unparsed nodes for unsupported TS/JS/etc.                                                      | Remove from production path or gate behind `artifactCompiler.unsupportedParserDiagnostics.enabled`; emit residual island, not stub node.                                                                                    | `ArtifactGraphServiceUnsupportedParserTest.java`                                   |
|       P0 | 1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/react-patch-emitter.ts`                        | MODIFY               | `canEmit` advertises ops that emit empty patches; sync emit always no-ops.                                                             | Restrict `canEmit` to implemented ops or return explicit unsupported result; replace regex edits with TS Compiler API/range-safe implementation.                                                                            | `react-patch-emitter.unsupported-op.test.ts`, `react-patch-emitter.rename.test.ts` |
|       P0 | 1       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/patch-coordinator.ts`                          | MODIFY               | Unsupported emitter path can produce empty patch set without hard failure.                                                             | Add `UnsupportedPatchOperation` result; fail validation when op has no emitter unless explicitly manual-review. Replace stderr logger with injected structured logger.                                                      | `patch-coordinator.unsupported.test.ts`                                            |
|       P1 | 2       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/types.ts`                                  | MODIFY               | Registry ignores typed provider and raw credentials are only blocked in browser.                                                       | Add `CredentialResolver`; dispatch typed `SourceLocator.provider` directly; reject raw credentials outside test/dev unless explicitly allowed.                                                                              | `source-provider-registry.test.ts`                                                 |
|       P1 | 2       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/github-provider.ts`                        | MODIFY               | Uses raw token and random temp root; no retry/rate limit/cleanup.                                                                      | Use credentialRef resolver, retry/backoff, rate-limit diagnostics, cleanup contract, deterministic snapshot metadata.                                                                                                       | `github-provider.commit-pinning.test.ts`                                           |
|       P1 | 2       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/gitlab-provider.ts`                        | MODIFY               | Slug parsing is simple and collides with GitHub-style slugs.                                                                           | Support nested groups and typed locator dispatch; add credentialRef and retry/backoff.                                                                                                                                      | `gitlab-provider.nested-groups.test.ts`                                            |
|       P1 | 2       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/zip-provider.ts`                           | MODIFY               | Skipped entries lack diagnostics; tar unsupported.                                                                                     | Add diagnostics for skipped/unsafe/unsupported compression; extract common archive contract.                                                                                                                                | `zip-provider.diagnostics.test.ts`                                                 |
|       P1 | 2       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/archive-provider.ts`                       | ADD                  | Target requires ZIP/tar archive support; current provider is ZIP-only.                                                                 | Add archive provider facade for zip/tar/tgz with shared safety rules.                                                                                                                                                       | `archive-provider.test.ts`                                                         |
|       P1 | 2       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/types.ts`                                         | MODIFY               | Skipped sources omit generated/vendor/binary as first-class reasons.                                                                   | Add skip sources `generated`, `vendor`, `binary`, `largeFile`; add `sourceFileRef`, `contentChecksum`, `classificationConfidence`.                                                                                          | `inventory-types.schema.test.ts`                                                   |
|       P1 | 2       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`                                       | MODIFY               | Homegrown gitignore and unbounded recursive scan.                                                                                      | Use a battle-tested ignore parser or add full fixture coverage; add bounded concurrency; expose deterministic scan mode.                                                                                                    | `scanner.gitignore.test.ts`, `scanner.determinism.test.ts`                         |
|       P1 | 3       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/pipeline.ts`                                      | MODIFY               | Model id is random; API can run with empty extractors.                                                                                 | Deterministic `SemanticProductModel.id` from snapshot + graph checksum; enforce extractor registry capability.                                                                                                              | `synthesis-pipeline.determinism.test.ts`                                           |
|       P1 | 3       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/symbol-resolver.ts`                               | MODIFY               | Edge IDs are ad hoc strings.                                                                                                           | Generate deterministic hashed edge IDs; include source location and resolver version in metadata.                                                                                                                           | `symbol-resolver.edge-id.test.ts`                                                  |
|       P1 | 3       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/symbol-index.ts`                                  | MODIFY               | Alias rules are local options/defaults only.                                                                                           | Derive aliases from tsconfig/package boundaries and scanner workspace metadata.                                                                                                                                             | `symbol-index.aliases.test.ts`                                                     |
|       P1 | 4       | `products/yappc/core/yappc-services/src/main/resources/db/migration/V15__artifact_compiler_snapshot_ir_hardening.sql` | ADD                  | Current tables lack repository snapshots, inventory, unresolved edges, residuals, patches; V11/V14 duplicate snapshot column intent.   | Add `repository_snapshots`, `artifact_inventory_items`, `artifact_unresolved_edges`, `artifact_edge_resolution_records`, `residual_islands`, `patch_sets`, `review_bundles`; add tenant/project/snapshot indexes.           | migration integration test                                                         |
|       P1 | 4       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`             | MODIFY               | `upsertNodes` ignores `contentChecksum`; default executor can be `Runnable::run`; pagination methods not used by service.              | Remove default blocking constructor or make it test-only; skip unchanged nodes by checksum; persist unresolved/residual tables; expose paginated query API.                                                                 | `ArtifactGraphRepositoryUpsertTest.java`                                           |
|       P1 | 4       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`  | MODIFY               | Query uses hard limit 10,000 and edge query without limit.                                                                             | Route graph queries through repository pagination; add query cursor response.                                                                                                                                               | `ArtifactGraphServicePaginationTest.java`                                          |
|       P1 | 5       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/types.ts`                                      | MODIFY               | Change ops are mostly component-specific.                                                                                              | Add `ModelChange` coverage for page route, layout, token, API, data entity, workflow; add unsupported/manual-review operation type.                                                                                         | `compile-back-types.schema.test.ts`                                                |
|       P1 | 5       | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/apply-patch.ts`                                | ADD                  | No actual patch application contract exists.                                                                                           | Add dry-run/apply interface with checksum guard, residual guard, rollback metadata, and validation hook.                                                                                                                    | `apply-patch.noop-roundtrip.test.ts`                                               |
|       P1 | 6       | `products/yappc/frontend/web/src/services/compiler/artifactCompilerClient.ts`                                         | ADD/MODIFY           | Web client integration not verified; API route exists.                                                                                 | Add typed client for import job, graph summary, residual review, patch review.                                                                                                                                              | `artifactCompilerClient.test.ts`                                                   |
|       P1 | 6       | `products/yappc/frontend/web/src/components/canvas/page/SourceImportPanel.tsx`                                        | ADD/MODIFY           | User-facing source provider UX not verified.                                                                                           | Add provider selector, repo/ref/archive input, progress, job polling, unsupported-state display.                                                                                                                            | Playwright import flow                                                             |
|       P1 | 6       | `products/yappc/frontend/web/src/components/canvas/page/ImportSummaryPanel.tsx`                                       | ADD                  | API returns summary/residual/skipped data.                                                                                             | Show understood vs skipped vs residual, confidence, review requirements.                                                                                                                                                    | component + E2E                                                                    |
|       P1 | 6       | `products/yappc/frontend/web/src/components/canvas/page/PatchReviewPanel.tsx`                                         | ADD                  | Patch review UI not verified.                                                                                                          | Show unified diff, validation results, residual overlaps, approve/reject.                                                                                                                                                   | Playwright patch review                                                            |
|       P2 | cleanup | `products/yappc/core/yappc-services/src/main/resources/db/migration/V11__create_source_import_jobs.sql`               | DOCUMENT/CONSOLIDATE | Snapshot columns added here and again in V14.                                                                                          | Do not edit applied migration; add V15 comment/doc explaining canonical schema and duplicate history.                                                                                                                       | migration verification                                                             |
|       P2 | cleanup | `platform/comp-decomp-todo.md`                                                                                        | CONSOLIDATE          | TODO doc exists outside canonical architecture path.                                                                                   | Move still-current content into canonical architecture/implementation plan or archive.                                                                                                                                      | docs lint                                                                          |
|       P2 | cleanup | `products/yappc/docs/architecture/ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md`                                       | MODIFY               | Architecture doc exists; content not revalidated here.                                                                                 | Update to match actual source-provider/pipeline/backend ownership after implementation.                                                                                                                                     | docs/code consistency checklist                                                    |

---

## Section F: Phase Plan

### Phase 1: Foundation hardening

**Goal:** Make current IR trustworthy before adding breadth.

**Files:** graph index/types/validation, Java DTOs/controller, source-import route, React emitter.

**Tasks:**

1. Export and enforce graph validation.
2. Extend backend DTOs to preserve TS graph fidelity.
3. Stop repo import from succeeding with `extractors: []`.
4. Stop patch emitters from silently no-oping unsupported ops.

**Validation:** graph validation tests, API repo import test, backend ingest validation test, unsupported patch op test.

**Exit criteria:** Invalid edges rejected; repo import without extractors returns explicit unsupported; no unsupported patch operation produces successful empty patch.

---

### Phase 2: Source provider and snapshot layer

**Goal:** Governed, deterministic source snapshots.

**Files:** source-provider types, GitHub/GitLab/ZIP/local providers, inventory scanner/types.

**Tasks:**

1. Add typed provider dispatch.
2. Add credentialRef resolver and raw-secret rejection policy.
3. Add provider diagnostics and cleanup lifecycle.
4. Harden scanner gitignore, generated/vendor/binary classification, and deterministic mode.

**Validation:** GitHub commit-pinning, GitLab nested groups, ZIP zip-slip, `.gitignore`, deterministic inventory tests.

**Exit criteria:** Same commit produces stable inventory identity; provider credentials never leak; skipped files have explicit reasons.

---

### Phase 3: Canonical compile pipeline

**Goal:** Snapshot → inventory → extraction → graph → semantic model → residuals works with real extractors.

**Files:** synthesis pipeline, symbol resolver/index, extractor registry.

**Tasks:**

1. Register canonical extractor capabilities.
2. Make model identity deterministic.
3. Persist unresolved edge lifecycle.
4. Link semantic elements to graph nodes and residuals.

**Validation:** small React fixture synthesis, unresolved reference fixture, residual fixture.

**Exit criteria:** Repository fixture produces non-empty graph/model/residual summary and validated graph.

---

### Phase 4: Backend production hardening

**Goal:** Durable, scoped, paginated, observable artifact graph backend.

**Files:** Java controller/service/repository/domain DTOs, V15 migration.

**Tasks:**

1. Add repository snapshots and unresolved/residual/patch persistence.
2. Enforce workspace/project authorization server-side.
3. Use paginated graph query responses.
4. Remove production parser stubs.

**Validation:** tenant/project isolation tests, repository upsert tests, graph query pagination tests.

**Exit criteria:** Cross-project reads/writes fail; graph persists without fidelity loss; large graph queries page.

---

### Phase 5: Compile-back and patch generation

**Goal:** Safe minimal patches for supported edits.

**Files:** compile-back types, patch coordinator, React emitter, apply-patch module.

**Tasks:**

1. Implement no-op round-trip test harness.
2. Replace regex-only React patching with AST/range-safe patching.
3. Add patch dry-run/apply interface with checksum and residual guards.
4. Add review bundle persistence.

**Validation:** no-op zero-diff, rename component patch, add prop patch, residual overlap rejection, stale checksum rejection.

**Exit criteria:** Supported edits produce minimal patches; unsupported edits require review; no-op round-trip is zero diff.

---

### Phase 6: UX and continuous evolution

**Goal:** No-cognitive-load import/review/patch/rescan UX.

**Files:** compiler API client, source import panel, import summary panel, patch review panel, canvas integration.

**Tasks:**

1. Provider selector and repo/ref/archive input.
2. Job progress and import summary.
3. Confidence/residual/skipped file review.
4. Patch diff and validation review.
5. Rescan/drift state.

**Validation:** Playwright E2E import → summary → model review → patch review → rescan.

**Exit criteria:** User sees clear understood/skipped/residual/patch status without compiler internals.

---

## Section G: Cleanup and Consolidation Plan

| Priority | Path                                                                                      | Current problem                                            | Action                                   | Target canonical path                              | Validation                                                                               |
| -------: | ----------------------------------------------------------------------------------------- | ---------------------------------------------------------- | ---------------------------------------- | -------------------------------------------------- | ---------------------------------------------------------------------------------------- |
|       P0 | `products/yappc/frontend/apps/api/src/routes/source-imports.ts`                           | Empty extractor pipeline gives false confidence.           | MODIFY                                   | Same file + canonical extractor registry           | Repo import test fails today, passes after non-empty extraction or explicit unsupported. |
|       P0 | `ArtifactGraphServiceImpl.java#parseSourceArtifact`                                       | Stub-like unsupported parser response in production path.  | REMOVE or FEATURE-FLAG                   | TS artifact compiler pipeline                      | Unsupported parser test.                                                                 |
|       P1 | `V11__create_source_import_jobs.sql` + `V14__add_snapshot_tracking_to_artifact_graph.sql` | Duplicate snapshot/tombstone migration intent.             | CONSOLIDATE via V15 corrective migration | `V15__artifact_compiler_snapshot_ir_hardening.sql` | Migration idempotency test.                                                              |
|       P1 | `ArtifactNodeDto.java` / `ArtifactEdgeDto.java`                                           | Backend graph DTOs are lossy.                              | MODIFY                                   | Backend IR DTO parity with TS graph                | DTO round-trip tests.                                                                    |
|       P1 | Fastify job repository + Java `SourceImportJobRepository`                                 | Duplicate job ownership.                                   | CONSOLIDATE                              | Single durable source import job API/table         | Job create/poll/cancel integration test.                                                 |
|       P1 | `react-patch-emitter.ts`                                                                  | Advertises unsupported ops.                                | MODIFY                                   | Capability-declared patch emitter                  | Unsupported-op review test.                                                              |
|       P2 | `platform/comp-decomp-todo.md`                                                            | Non-canonical TODO doc can cause repeated work.            | ARCHIVE/CONSOLIDATE                      | Canonical architecture/implementation docs         | Docs cleanup checklist.                                                                  |

---

## Section H: Test Plan

| Priority | Test type           | Test file path                                                                                                 | Scenario                                                       | Expected assertion                                    | Fails today because                                                                 |
| -------: | ------------------- | -------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------- | ----------------------------------------------------- | ----------------------------------------------------------------------------------- |
|       P0 | API integration     | `products/yappc/frontend/apps/api/src/routes/source-imports.repo-import.test.ts`                               | Import `small-react-app` GitHub/local fixture                  | Non-empty graph/model or explicit unsupported error   | Route uses `extractors: []`.                                                        |
|       P0 | Unit                | `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/validateGraph.test.ts`                         | Edge target is raw component name not node ID                  | Validation error                                      | Current backend DTO can accept raw strings; TS validation only checks within graph. |
|       P0 | Backend API         | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/ArtifactGraphControllerScopeTest.java` | Body product/project differs from principal-authorized project | 403                                                   | Controller only proves tenant check.                                                |
|       P0 | Unit                | `src/compile-back/react-patch-emitter.unsupported-op.test.ts`                                                  | `remove-component` op                                          | Review-required unsupported result, not empty success | `canEmit` includes op but implementation returns empty.                             |
|       P0 | Golden              | `src/compile-back/noop-roundtrip.test.ts`                                                                      | source → model → patch with no change                          | zero diff                                             | Full round-trip not implemented.                                                    |
|       P1 | Unit                | `src/inventory/scanner.determinism.test.ts`                                                                    | Scan same snapshot twice                                       | Stable IDs/checksums/order                            | Random fallback exists without snapshotRef.                                         |
|       P1 | Unit                | `src/inventory/scanner.gitignore.test.ts`                                                                      | Nested `.gitignore` with negation                              | Exact skip/include behavior                           | Homegrown parser needs fixture proof.                                               |
|       P1 | Unit                | `src/source-providers/github-provider.commit-pinning.test.ts`                                                  | `owner/repo@branch`                                            | snapshotRef contains resolved commit SHA              | Provider supports commit resolve but needs regression.                              |
|       P1 | Unit                | `src/source-providers/gitlab-provider.nested-groups.test.ts`                                                   | `group/subgroup/repo`                                          | correct project path                                  | Parser currently models owner/repo.                                                 |
|       P1 | Unit                | `src/source-providers/zip-provider.security.test.ts`                                                           | zip-slip entry                                                 | skipped with diagnostic                               | Zip-slip skip exists but diagnostics absent.                                        |
|       P1 | Backend persistence | `ArtifactGraphRepositoryUpsertTest.java`                                                                       | Same checksum upsert                                           | unchanged node not rewritten; version tracked         | `contentChecksum` parameter is ignored.                                             |
|       P1 | Backend persistence | `ArtifactGraphRepositoryPaginationTest.java`                                                                   | large graph query                                              | cursor returned and honored                           | Service query uses fixed 10,000 limit.                                              |
|       P1 | E2E                 | `tests/e2e/yappc-artifact-import-review.spec.ts`                                                               | Import → summary → residual review                             | shows progress, confidence, skipped, residuals        | UI path not verified.                                                               |
|       P1 | E2E                 | `tests/e2e/yappc-patch-review.spec.ts`                                                                         | Model edit → patch review                                      | diff + validation + approve/reject                    | Patch review UX not verified.                                                       |

---

## Section I: Critical Questions — Direct Answers

| Question                                                               | Answer                                                                                                | Evidence                                                                                                       | Required fix                                                          |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------- |
| Is the current system truly round-trip capable?                        | No                                                                                                    | React emitter only supports limited async rename/add-prop; sync emit no-ops.                                   | Build no-op round-trip, patch apply, validation, residual guard.      |
| Can it scan a full GitHub repo today, or only import individual files? | Partial                                                                                               | GitHub provider can snapshot recursive tree and commit pin.  API route then runs pipeline with no extractors.  | Wire real extractor registry and durable graph/model persistence.     |
| Are artifact IDs deterministic?                                        | Partial                                                                                               | Deterministic with snapshotRef; random UUID without snapshotRef.                                               | Require snapshotRef for repo scans and deterministic model IDs.       |
| Are graph edges valid and resolved?                                    | Partial                                                                                               | TS separates resolved/unresolved and validates edge endpoints.   Backend DTO lacks unresolved edge contract.   | Add backend validation and unresolved edge persistence.               |
| Is there a complete synthesis pipeline?                                | Partial                                                                                               | Pipeline exists.  API route uses no extractors.                                                                | Capability registry + real extractors in source import.               |
| Is compile-back/patch generation implemented?                          | Partial                                                                                               | Coordinator/types exist.   Emitter is limited.                                                                 | AST-backed emitters + patch apply/dry-run.                            |
| Are residual islands preserved?                                        | Partial                                                                                               | Schema exists and pipeline can create residuals.                                                               | Persist residuals and show them in UX; patch overlap validation.      |
| Are source import jobs durable?                                        | Partial                                                                                               | API job route and DB table exist.                                                                              | Consolidate Fastify/Java job ownership.                               |
| Is tenant/workspace/project scope enforced consistently?               | Partial                                                                                               | Java tenant from principal.  Fastify headers for tenant/workspace/project.                                     | Principal-derived tenant/workspace/project in one server-side policy. |
| Is backend artifact graph logic in the right canonical module?         | Partial                                                                                               | Java owns API/persistence; TS owns compiler.                                                                   | Formalize boundary and remove parser duplication/stubs.               |
| Are there duplicate/conflicting implementations?                       | Yes                                                                                                   | V11 and V14 duplicate snapshot tracking; job repo appears in Fastify and Java.                                 | Consolidation plan and V15 corrective migration.                      |
| Smallest trustworthy milestone?                                        | Stable Repository IR and Source Snapshot Compiler                                                     | Current scanner/source/graph foundations exist.                                                                | Complete Phase 1–2 with golden tests.                                 |
| What files must change first?                                          | Source import route, graph validation/export, DTOs, controller, emitter                               | Evidence above.                                                                                                | Execute P0 TODOs.                                                     |
| What tests must fail today and pass after?                             | Repo import non-empty extraction, unsupported patch op, backend invalid edge ingest, no-op round-trip | Evidence above.                                                                                                | Add tests in Section H.                                               |
| What legacy/stale/deprecated code/docs must be removed/consolidated?   | Parser stubs, duplicate migrations intent, duplicate job ownership, external TODO doc                 | Evidence above.                                                                                                | Cleanup Section G.                                                    |

---

## Section J: Definition of Done

Implementation is done only when:

1. GitHub/GitLab/local/archive sources produce immutable `RepositorySnapshot` records.
2. Inventory is deterministic for a commit-pinned snapshot.
3. Generated/vendor/binary/large/skipped files have explicit reasons.
4. Graph resolved edges only reference existing graph node IDs.
5. Unresolved references are persisted separately with resolution records.
6. Semantic model elements have provenance, confidence, graph node IDs, and residual links.
7. Residual islands are persisted, visible, and protected from patch emitters.
8. Repository import through API produces a validated graph/model or explicit unsupported state.
9. Backend artifact APIs enforce tenant/workspace/project scope server-side.
10. Patch generation supports at least React rename/add-prop with minimal diff, checksum validation, and residual guard.
11. Unsupported compile-back operations require review and never silently succeed.
12. No-op round-trip fixture produces zero diff.
13. Golden fixtures pass for scanner, graph, synthesis, residual, and patch flows.
14. Stale/duplicate job and migration ownership is consolidated or explicitly documented.

---

## Section K: Prioritized TODO Checklist

### P0

* [ ] [P0] Stop repository import from succeeding with an empty extractor pipeline.

  * Files:

    * `products/yappc/frontend/apps/api/src/routes/source-imports.ts`
  * Why: Current route calls `SynthesisPipeline({ extractors: [] })`. 
  * Done when: repo import returns non-empty validated model/graph or explicit unsupported error.
  * Test: `source-imports.repo-import.test.ts`

* [ ] [P0] Preserve full graph fidelity across backend API.

  * Files:

    * `ArtifactGraphIngestRequest.java`
    * `ArtifactNodeDto.java`
    * `ArtifactEdgeDto.java`
    * `ArtifactGraphRepository.java`
  * Why: TS graph schema is rich, Java DTOs are lossy.   
  * Done when: sourceLocation, confidence, provenance, unresolved edges, residual refs persist round-trip.
  * Test: backend DTO/repository round-trip tests.

* [ ] [P0] Enforce workspace/project scope server-side.

  * Files:

    * `ArtifactGraphController.java`
    * `source-imports.ts`
  * Why: Java controller proves tenant enforcement, but product/project remains payload/header driven.  
  * Done when: cross-project request fails with 403.
  * Test: `ArtifactGraphControllerScopeTest.java`

* [ ] [P0] Remove silent patch no-ops.

  * Files:

    * `react-patch-emitter.ts`
    * `patch-coordinator.ts`
  * Why: emitter advertises unsupported ops and returns empty patches. 
  * Done when: unsupported ops produce explicit review-required unsupported results.
  * Test: `react-patch-emitter.unsupported-op.test.ts`

* [ ] [P0] Remove or flag production parser stubs.

  * Files:

    * `ArtifactGraphServiceImpl.java`
  * Why: unsupported TS/JS parser path returns stub-like unparsed node. 
  * Done when: unsupported language emits residual/unsupported diagnostic, not production stub node.
  * Test: `ArtifactGraphServiceUnsupportedParserTest.java`

### P1

* [ ] [P1] Harden source provider governance.

  * Files:

    * `source-providers/types.ts`
    * `github-provider.ts`
    * `gitlab-provider.ts`
    * `zip-provider.ts`
  * Why: provider abstraction exists but credentialRef, typed dispatch, retries, diagnostics are incomplete. 
  * Done when: providers use credential resolver, typed provider dispatch, redacted diagnostics.
  * Test: provider registry and provider-specific tests.

* [ ] [P1] Make inventory scan production deterministic.

  * Files:

    * `inventory/types.ts`
    * `inventory/scanner.ts`
  * Why: scanner exists but needs stronger gitignore, skip reasons, concurrency, deterministic mode.  
  * Done when: golden fixture scan is stable across repeated runs.
  * Test: scanner determinism and `.gitignore` tests.

* [ ] [P1] Add full IR persistence migration.

  * Files:

    * `V15__artifact_compiler_snapshot_ir_hardening.sql`
  * Why: current schema lacks snapshots, inventory, unresolved edges, residuals, patches; V11/V14 duplicate snapshot tracking.  
  * Done when: DB stores source snapshot → inventory → graph → residual → patch lineage.
  * Test: migration integration test.

* [ ] [P1] Add no-op round-trip harness.

  * Files:

    * `compile-back/apply-patch.ts`
    * `compile-back/patch-coordinator.ts`
    * `tests/fixtures/small-react-app`
  * Why: current compile-back is not round-trip safe.  
  * Done when: no-op round-trip produces zero diff.
  * Test: `noop-roundtrip.test.ts`

* [ ] [P1] Consolidate source import job ownership.

  * Files:

    * `source-imports.ts`
    * `job-repository.ts`
    * `SourceImportJobRepository.java`
  * Why: job flow appears split across Fastify, DB, and Java.   
  * Done when: one canonical job API/table supports create, poll, cancel, resume, audit.
  * Test: job lifecycle integration test.

### P2

* [ ] [P2] Add canvas import summary and patch review UX.

  * Files:

    * `products/yappc/frontend/web/src/services/compiler/artifactCompilerClient.ts`
    * `products/yappc/frontend/web/src/components/canvas/page/SourceImportPanel.tsx`
    * `products/yappc/frontend/web/src/components/canvas/page/ImportSummaryPanel.tsx`
    * `products/yappc/frontend/web/src/components/canvas/page/PatchReviewPanel.tsx`
  * Why: API has review concepts, but web/canvas integration was not verified.
  * Done when: user can import, review confidence/residuals/skipped files, review patch, and rescan.
  * Test: Playwright source import and patch review E2E.

* [ ] [P2] Consolidate stale compiler/decompiler TODO docs.

  * Files:

    * `platform/comp-decomp-todo.md`
    * `products/yappc/docs/architecture/ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md`
  * Why: Non-canonical TODO docs can cause repeated or stale planning.  
  * Done when: canonical architecture reflects actual code ownership and stale TODO is archived or merged.
  * Test: docs/code consistency checklist.

### P3

* [ ] [P3] Improve graph query response ergonomics.

  * Files:

    * `graph/types.ts`
    * `ArtifactGraphRepository.java`
    * `ArtifactGraphServiceImpl.java`
  * Why: TS query supports cursor/limit, but result schema lacks cursor and Java service uses fixed limits.  
  * Done when: graph queries return `items`, `nextCursor`, `totalEstimate`, and scope metadata.
  * Test: graph query pagination test.
