# Deep Review — Groups 1, 2, and 3 Only

Repo: `samujjwal/ghatana`
Commit: `389a714e4b40da3e8c8f1b9ba9c982d3420c0f61`

I reviewed the current codebase state at the requested commit as a full snapshot, not as a diff. The commit exists and is a merge commit on the YAPPC changelog, so the findings below are based on the repository state at that ref. 

## Executive conclusion

The implementation has improved significantly compared with the earlier snapshot. Groups 1–3 are no longer “mostly missing”; they now have real code. However, they are still **not production-grade** because several seams can break the end-to-end compile path:

1. **Contract/runtime boundary is stronger but still not truly single-source-of-truth.**
2. **Source acquisition and snapshot persistence exist, but scope, completeness, and provider bootstrap still have P0 risks.**
3. **Graph/residual/semantic-model fidelity is much better, but current code can still accept fake residual source, fail on null content, and create graph/model inconsistency.**

---

# Group 1 — Canonical Contract and Java/TypeScript Runtime Boundary

## Objective current status

**Current status: `PARTIALLY_IMPLEMENTED_WITH_CONTRACT_DRIFT_RISK`**

The proto contract is now materially better. It normalizes `project_id` in graph ingest/query/analyze/merge requests, normalizes unresolved edge `relationship_type`, adds `SemanticModel`, and expands `ResidualIsland` with `original_source`, structured `source_location`, checksum, raw fragment ref, risk, scope, and snapshot fields. 

Java has also moved away from raw residual IDs. `ArtifactGraphIngestRequest` now accepts typed `UnresolvedGraphEdgeDto`, `EdgeResolutionRecordDto`, and full `ResidualIslandDto` payloads. 

The Java TS worker adapter is stricter now: it rejects legacy `residualIslandIds`, requires canonical `type`, `sourceNodeId`, `targetNodeId`, and `relationshipType`, validates edge targets against declared nodes, and requires full residual payload fields. 

The frontend `ArtifactCompilerClient.ts` is also improved: it now wraps a generated API client and requires tenant/workspace/project scope before making graph/import calls. Legacy patch-bundle methods are feature-gated. 

## Deep findings

### G1.P0 — Proto exists, generated client exists, but Java DTOs are still hand-maintained

The proto contract is richer and appears intended to be canonical, but Java records such as `ArtifactGraphIngestRequest`, `ArtifactNodeDto`, `ArtifactEdgeDto`, `ResidualIslandDto`, `UnresolvedGraphEdgeDto`, `EdgeResolutionRecordDto`, and `SemanticModelDto` are still manually maintained Java records rather than visibly generated from the proto.    

**Production risk:** contract drift can return immediately. The TS frontend may use generated OpenAPI types, proto may define another shape, and Java runtime DTOs may accept a third shape.

**Required fix:** decide one canonical contract generation pipeline for Java REST DTOs, Java gRPC DTOs, TS frontend client, and TS worker IO. Proto can remain the source, but only if the build actually generates and validates the downstream types.

### G1.P0 — TypeScript source-provider snapshot schema is behind Java snapshot semantics

The TypeScript `RepositorySnapshotSchema` includes `snapshotRef`, `localRootPath`, files, `snapshotAt`, `shallow`, and diagnostics, but `SnapshotFileSchema` does not include file checksum, and the repository snapshot schema does not include `snapshotId`, `contentHash`, tenant ID, workspace ID, or project ID. 

Java persistence depends on `snapshotId`, `checksum`, `contentHash`, and per-file `contentChecksum`. 

**Production risk:** the Java→TS worker boundary cannot fully represent the durable Java snapshot. The worker can run extraction, but it does not receive all canonical identity/scope/checksum information needed for airtight provenance and round-trip traceability.

**Required fix:** update the TS `RepositorySnapshotSchema` to match canonical Java/proto snapshot identity and file checksum fields.

### G1.P1 — Frontend client is generated-backed, but legacy manual HTTP methods remain in the same production wrapper

`ArtifactCompilerClient.ts` now uses generated API services for graph/import flows, which is correct. However, the same client still carries manual legacy patch-bundle HTTP methods behind `enableLegacyPatchBundleMethods`. 

**Production risk:** even though patch workflows are outside Groups 1–3, keeping manual endpoints in the canonical client weakens the “generated-only” contract discipline.

**Required fix:** move legacy patch bundle methods into a separate explicitly deprecated adapter under a non-default import path.

## Group 1 file-by-file plan

| Priority | File                                                                                                   | Action           | Exact change                                                                                                                                                                     | Validation                                                               |
| -------: | ------------------------------------------------------------------------------------------------------ | ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
|       P0 | `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`                            | MODIFY           | Finalize as canonical contract for groups 1–3. Add/confirm `snapshot_id`, `content_hash`, file checksum, tenant/workspace/project, residual full payload, semantic model fields. | Proto compatibility test.                                                |
|       P0 | `products/yappc/core/yappc-services/build.gradle*` or relevant Gradle config                           | MODIFY           | Add generation task for Java DTOs/gRPC/openapi artifacts from canonical proto/schema. Fail build on stale generated code.                                                        | Build must fail when proto changes but generated outputs are stale.      |
|       P0 | `products/yappc/frontend/web/src/clients/generated/api`                                                | MODIFY/GENERATE  | Ensure generated TS API client includes all group 1–3 endpoints and exact DTO shapes.                                                                                            | TS compile + generated client contract test.                             |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/types.ts`                   | MODIFY           | Add `snapshotId`, `contentHash`, `contentChecksum`, tenant/workspace/project, and per-file checksum to TS snapshot schema.                                                       | Java worker payload validates against TS schema without lossy fields.    |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/*.java`            | REPLACE/GENERATE | Replace hand-maintained DTOs with generated DTOs or add generated compatibility layer.                                                                                           | Java DTO/proto/TS fixture round-trip tests.                              |
|       P1 | `products/yappc/frontend/web/src/clients/artifactCompiler/ArtifactCompilerClient.ts`                   | SPLIT            | Keep generated-backed Group 1–3 client here. Move legacy patch-bundle methods to `LegacyArtifactPatchBundleClient.ts`.                                                           | Import lint: default artifact client cannot call manual patch endpoints. |
|       P1 | `products/yappc/core/yappc-services/src/test/.../ArtifactCompilerContractCompatibilityTest.java`       | ADD              | Load canonical JSON fixtures and validate proto/Java DTO/TS worker payload compatibility.                                                                                        | Required CI gate.                                                        |
|       P1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.contract.test.ts` | ADD              | Validate worker request/response against canonical fixture from Java.                                                                                                            | Required CI gate.                                                        |

---

# Group 2 — Governed Source Acquisition, Snapshot, and Inventory

## Objective current status

**Current status: `PARTIALLY_IMPLEMENTED_WITH_P0_SCOPE_AND_COMPLETENESS_RISKS`**

This group has significantly more implementation now.

`SourceCredentialResolver` exists and supports dev-only env-backed resolution plus a governed resolver backed by `SourceCredentialRepository`. It explicitly says env-backed resolution is dev-only and requires `YAPPC_DEV_MODE=true` or `dev.mode=true`. 

GitHub and GitLab providers now call the credential resolver with tenant/workspace/project scope.  

GitLab is much improved: it now encodes project/file paths, paginates tree results, sorts deterministically, supports credentials, and creates deterministic snapshot IDs. 

`RepositoryInventoryScanner` is now a real canonical scanner with stable sorted walk, skip reasons, binary/vendor/generated/large-file classification, package boundary detection, streaming SHA-256, and include/exclude support. 

`RepositorySnapshotRepository` now persists snapshots and snapshot files with source locator refs, diagnostics, content hash, and scoped lookup methods. 

## Deep findings

### G2.P0 — Default GitHub/GitLab provider constructors can fail production bootstrap

`GitHubSourceProvider()` and `GitLabSourceProvider()` both call `SourceCredentialResolver.envBacked()` by default.  

But `envBacked()` throws unless dev mode is enabled. 

**Production risk:** any production code path using default provider constructors or a default registry can fail at startup before DI has a chance to inject a governed resolver.

**Required fix:** default constructors must not call dev-only resolver. They should either require explicit resolver injection or use a no-credential resolver that never throws. Production registry should be constructed through DI with `SourceCredentialResolver.governed(...)`.

### G2.P0 — Repository snapshot identity is global while persistence is scoped

`RepositorySnapshotRepository.saveSnapshot` inserts by `snapshot_id` and uses `ON CONFLICT (snapshot_id)` while storing `tenant_id`, `workspace_id`, and `project_id`. On conflict, it updates materialized root, checksum, content hash, diagnostics, and source locator JSON, but does not distinguish multiple tenants importing the same repo/commit. 

**Production risk:** if snapshot ID is deterministic from repo+commit, two tenants importing the same repo/commit can collide into one row. That can corrupt source locator refs and materialized root across tenants.

**Required fix:** either make snapshot rows globally immutable and move tenant/workspace/project ownership into a separate `repository_snapshot_bindings` table, or make the primary key composite: `(tenant_id, workspace_id, project_id, snapshot_id)`.

### G2.P0 — Unscoped snapshot reads still exist

`RepositorySnapshotRepository` has scoped methods such as `findById(snapshotId, tenantId, workspaceId, projectId)`, but also unscoped methods like `findById(snapshotId)`, `findByRepoId(repoId, limit)`, `findByContentHash(contentHash)`, `deleteOldSnapshots(olderThan)`, and `findSourceLocator(snapshotId)`. 

**Production risk:** these methods are dangerous if exposed through services later. They bypass tenant/workspace/project isolation.

**Required fix:** remove public unscoped reads from production repository or make them package-private/admin-only with explicit audited admin scope.

### G2.P0 — GitHub/GitLab source providers can return partial snapshots without failing hard

GitHub provider stops materialization when `MAX_TOTAL_SIZE_BYTES` is exceeded and then returns a snapshot with whatever files were downloaded so far. 

GitLab provider has the same total-size break behavior. 

**Production risk:** a repository snapshot can be silently incomplete. That breaks source fidelity and makes downstream graph/model results untrustworthy.

**Required fix:** total-size and file-count limits should produce explicit failed-closed snapshot diagnostics or a `PARTIAL_REVIEW_REQUIRED` status, never a normal successful snapshot.

### G2.P1 — Provider-level materialization still happens before canonical inventory filtering

GitHub and GitLab providers materialize remote blobs before the canonical Java inventory scanner runs in `ArtifactCompileJobService`.   

**Production risk:** vendor/generated/binary/large files can be fetched and written before the inventory layer classifies/skips them. This hurts performance and can hit limits before relevant source files are reached.

**Required fix:** providers need a prefetch inventory phase based on provider metadata where possible, then materialize only eligible files. For GitHub/GitLab, tree metadata should be filtered by path, extension, size where available, and configured include/exclude before blob fetch.

### G2.P1 — Inventory include rules bypass safety filters

`RepositoryInventoryScanner` says include rules take precedence and “skip all other filtering.” 

**Production risk:** include patterns can accidentally force binary/oversized/vendor/generated files into extraction. Include should override user exclude rules, but not safety rules.

**Required fix:** split filters into two classes:

* User filters: include/exclude/gitignore.
* Safety filters: path traversal, binary, generated, vendor, large file.

Include may override user filters, but not safety filters unless a privileged explicit option is used.

### G2.P1 — Exclude pattern skip reason is wrong

When `excludePatterns` match, scanner records `SkipReason.PACKAGE_BOUNDARY`. 

**Production risk:** import summaries and governance decisions will misreport why files were skipped.

**Required fix:** add `SkipReason.EXCLUDE_PATTERN` and include matched pattern in `SkippedEntry`.

## Group 2 file-by-file plan

| Priority | File                                    | Action | Exact change                                                                                                                                                                                        | Validation                                                          |
| -------: | --------------------------------------- | ------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
|       P0 | `SourceProviderRegistry.java`           | MODIFY | Do not instantiate providers with dev-only default constructors. Build registry through DI using governed credential resolver.                                                                      | Production bootstrap test with dev mode disabled.                   |
|       P0 | `GitHubSourceProvider.java`             | MODIFY | Remove default constructor or make it no-credential safe. Fail closed when total size limit is reached. Add `PARTIAL_SNAPSHOT_REJECTED` diagnostic. Materialize only eligible files where possible. | GitHub large repo partial-snapshot test.                            |
|       P0 | `GitLabSourceProvider.java`             | MODIFY | Same as GitHub: no dev-only default resolver, fail closed on limits, improve prefetch filtering.                                                                                                    | GitLab pagination + partial-snapshot test.                          |
|       P0 | `SourceCredentialResolver.java`         | MODIFY | Keep `envBacked()` dev-only, but ensure no production provider default path calls it implicitly.                                                                                                    | Unit test: default production provider construction does not throw. |
|       P0 | `RepositorySnapshotRepository.java`     | MODIFY | Replace global `ON CONFLICT(snapshot_id)` with scoped key or introduce snapshot binding table. Remove public unscoped methods.                                                                      | Cross-tenant same snapshot test.                                    |
|       P0 | `V___repository_snapshot_scope_fix.sql` | ADD    | Add composite unique key or `repository_snapshot_bindings`; migrate existing rows safely.                                                                                                           | Migration + tenant collision test.                                  |
|       P1 | `RepositoryInventoryScanner.java`       | MODIFY | Add `EXCLUDE_PATTERN`; add matched pattern to skipped entry; make safety filters non-overridable by normal include rules.                                                                           | Scanner skip-reason golden test.                                    |
|       P1 | `ArtifactCompileJobService.java`        | MODIFY | Use persisted inventory from snapshot repository instead of rescanning untracked materialized root when possible. Store inventory result and skip summary.                                          | Compile job inventory persistence test.                             |
|       P1 | `RepositorySnapshotRepository.java`     | MODIFY | Persist skip reasons, package boundaries, and inventory metadata, not only snapshot files.                                                                                                          | Snapshot inventory query test.                                      |

---

# Group 3 — Artifact Graph, Residual Islands, and Semantic Model Fidelity

## Objective current status

**Current status: `PARTIALLY_IMPLEMENTED_WITH_P0_END_TO_END_BREAKS`**

This group has improved the most structurally.

Typed residuals now exist in Java through `ResidualIslandDto`, including original source, source location, source span, checksum, raw fragment ref, confidence, risk, scope, and snapshot fields. 

Typed unresolved edges and edge resolution records now exist.  

`ArtifactGraphServiceImpl` now maps typed unresolved/resolution records and rejects residual islands missing original source, checksum, or raw fragment ref.  

`ArtifactGraphRepository.saveResidualIslands` persists full residual payload fields, including original source, source location JSON, source span, checksum, raw fragment ref, reason, confidence, review required, risk score, file count, metadata, and scope. 

Semantic model persistence now exists through `SemanticModelDto`, `SemanticModelRepository`, and migrations `V20` and `V24`.    

## Deep findings

### G3.P0 — End-to-end TS worker routing likely breaks because TS pipeline ignores Java’s filtered file list

`ArtifactCompileJobService` filters inventory files into `tsFiles` and sends only those to `tsExtractorWorker.extract(...)`. 

`ProcessTsExtractorWorker` then enforces that returned nodes must belong to the routed TypeScript file scope. 

But the TypeScript `SynthesisPipeline.runFromSnapshot(snapshot)` calls `this.run(snapshot.localRootPath, snapshot)` and then runs `scanRepository` over `rootPath`; it does not use `snapshot.files` as the scan boundary. 

**Production risk:** Java passes filtered TS files, but TS may scan the entire materialized root and return nodes outside the allowed set. Java will then reject those nodes, causing compile jobs to fail or become flaky depending on repo contents.

**Required fix:** TS `SynthesisPipeline` must support `runFromSnapshotFiles(snapshot)` or respect `snapshot.files` as the inventory boundary. Java should not rely on post-hoc rejection to enforce scope.

### G3.P0 — Graph ingest can fail because TS worker emits `content: null`, while Java checksum calculation dereferences `node.content()`

The TS worker maps graph nodes with `content: null`. 

`ArtifactGraphRepository.computeChecksum` calls `node.content().getBytes(...)`. 

**Production risk:** graph ingest can throw `NullPointerException` for normal TS worker output. This is a direct end-to-end blocker.

**Required fix:** either require non-null node content in worker output or make Java checksum calculation null-safe and based on stable fields: node ID, sourceRef, symbolRef, sourceLocation, properties, tags, extractor ID/version, and content checksum when present.

### G3.P0 — TS worker can still synthesize fake residual source that passes Java validation

Java now correctly rejects residuals without original source/checksum/raw fragment ref.  

But the TS worker creates fallback residual source. If it cannot find `originalSource`, it falls back to `source`, then `sourceSpan`, and finally `"[source-unavailable]"`. It also synthesizes checksum and `rawFragmentRef` if missing. 

**Production risk:** Java validation can be bypassed by synthetic placeholders that are non-empty. The system will think residual source is preserved when it is not.

**Required fix:** TS worker must fail closed when residual `originalSource`, exact source location, checksum, or raw fragment ref is missing. Never emit `[source-unavailable]` in production output.

### G3.P0 — Semantic model repository likely has SQL/runtime mismatch

`SemanticModelRepository.saveModel` and `saveModels` insert a large list of columns and then set statement indices through `29`, but the visible SQL `VALUES` list appears shorter than the number of columns/setters. 

**Production risk:** semantic model persistence can fail at runtime even after graph ingest succeeds.

**Required fix:** add a repository integration test immediately. Then correct SQL placeholder count and ensure `saveModel` and `saveModels` use a single shared binder method to avoid drift.

### G3.P1 — Graph ingest and semantic model persistence are not atomic

`ArtifactCompileJobService` ingests the graph first and then saves semantic models. The comment says this avoids orphaned semantic rows, but if semantic model persistence fails after graph ingest succeeds, graph rows are still persisted without semantic rows. 

**Production risk:** a snapshot can have graph data but missing semantic model data, producing inconsistent downstream state.

**Required fix:** introduce a compile transaction/workflow state: `GRAPH_INGESTED`, `SEMANTIC_MODEL_PERSISTED`, `COMPLETE`, `FAILED_PARTIAL`. Either persist graph and model in one DB transaction where possible, or mark partial state and expose repair/retry.

### G3.P1 — Semantic models generated by TS worker are node-derived, not true semantic-model-derived

The TS worker creates `semanticModels` by mapping every graph node, with empty dependencies/dependents, `reviewRequired: false`, and security/privacy flags both populated from `privacySecurityFlags`. 

**Production risk:** semantic model persistence exists, but its content may be shallow graph-node projection rather than the YAPPC semantic product model needed for compile-back and governance.

**Required fix:** map semantic models from `result.model.elements` or the true semantic synthesis output, not directly from graph nodes.

### G3.P1 — Residual DTO/source location types are duplicated

`ResidualIslandDto` defines its own nested `SourceLocation`; `UnresolvedGraphEdgeDto` also defines a nested `SourceLocation`; `SemanticModelDto` defines another nested `SourceLocation`; proto defines `SourceLocation` once.    

**Production risk:** source span semantics can drift across residuals, unresolved edges, semantic models, and graph nodes.

**Required fix:** create one Java `SourceLocationDto` generated from canonical contract and reuse it everywhere.

## Group 3 file-by-file plan

| Priority | File                                                                                     | Action       | Exact change                                                                                                                                                                              | Validation                                             |
| -------: | ---------------------------------------------------------------------------------------- | ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
|       P0 | `ArtifactGraphRepository.java`                                                           | MODIFY       | Make node checksum calculation null-safe. Do not call `node.content().getBytes()` directly. Use stable canonical fields and `content == null ? "" : content`.                             | Graph ingest test with TS worker node `content: null`. |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts` | MODIFY       | Remove `[source-unavailable]`, `sourceSpan` fallback, synthetic checksum fallback, and synthetic raw fragment fallback for residuals. Fail closed if residual source fidelity is missing. | Residual strictness test.                              |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/synthesis/pipeline.ts`         | MODIFY       | Respect `snapshot.files` as the allowed inventory boundary when running from snapshot. Do not rescan unrestricted root.                                                                   | Worker scoped-file test.                               |
|       P0 | `ProcessTsExtractorWorker.java`                                                          | MODIFY       | Keep file-scope enforcement, but return clear diagnostic that points to TS pipeline ignoring snapshot file list.                                                                          | Java worker scoped output test.                        |
|       P0 | `SemanticModelRepository.java`                                                           | MODIFY       | Fix SQL placeholder count. Extract shared binder for `saveModel` and `saveModels`. Add hard failure on mismatched schema.                                                                 | Repository integration test.                           |
|       P0 | `V___semantic_model_repository_contract_test.sql` or migration validation                | ADD          | Verify table columns match repository insert/update fields.                                                                                                                               | Migration/repository compatibility test.               |
|       P1 | `ArtifactCompileJobService.java`                                                         | MODIFY       | Add compile phase state and partial failure handling. If semantic persistence fails, mark compile job partial/failed and expose repair.                                                   | Graph/model consistency test.                          |
|       P1 | `ts-extractor-worker.ts`                                                                 | MODIFY       | Create semantic models from true semantic synthesis output, not every graph node. Preserve dependencies, dependents, review requirements, confidence, model version, residual links.      | Semantic model fidelity golden test.                   |
|       P1 | `SourceLocationDto.java`                                                                 | ADD/GENERATE | Replace nested source location records across residual, unresolved edge, and semantic model DTOs.                                                                                         | DTO compatibility test.                                |
|       P1 | `ResidualIslandDto.java`                                                                 | MODIFY       | Use shared `SourceLocationDto`; require original source and checksum in constructor.                                                                                                      | Residual DTO validation test.                          |
|       P1 | `UnresolvedGraphEdgeDto.java`                                                            | MODIFY       | Use shared `SourceLocationDto`; add validation for confidence range and allowed relationship types.                                                                                       | Unresolved edge validation test.                       |
|       P1 | `SemanticModelDto.java`                                                                  | MODIFY       | Use shared `SourceLocationDto`; enforce confidence range, provenance enum, and syntheticReason requirement for synthetic/manual provenance.                                               | Semantic DTO validation test.                          |

---

# Prioritized execution order for Groups 1–3

## First: Fix P0 end-to-end breakages

1. Make `ArtifactGraphRepository.computeChecksum` null-safe.
2. Make TS pipeline respect `snapshot.files`.
3. Remove fake residual source fallbacks from TS worker.
4. Fix `SemanticModelRepository` SQL insert/binder.
5. Fix provider default constructors so production bootstrap does not call dev-only `envBacked()`.

## Second: Fix scope and snapshot correctness

1. Change snapshot persistence keying to scoped binding or composite scope key.
2. Remove public unscoped snapshot repository methods.
3. Fail closed on partial GitHub/GitLab snapshots.
4. Persist inventory skip reasons and package boundaries.

## Third: Lock the contract

1. Generate Java/TS DTOs from canonical proto/schema.
2. Add contract compatibility tests.
3. Remove duplicate `SourceLocation` DTOs.
4. Align TS snapshot schema with Java/proto snapshot schema.

---

# Updated objective status by group

| Group                                           | Current status at `389a714e`                                                      | Production readiness verdict                                                                                                        |
| ----------------------------------------------- | --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| Group 1 — Contract/runtime boundary             | Much improved: proto normalized, frontend uses generated API, Java worker strict. | Not production-ready until DTOs are generated/contract-tested and TS snapshot schema is aligned.                                    |
| Group 2 — Source acquisition/snapshot/inventory | Real providers, credential resolver, scanner, and snapshot repository exist.      | Not production-ready due to provider bootstrap, partial snapshot, and scoped persistence risks.                                     |
| Group 3 — Graph/residual/semantic fidelity      | Real typed DTOs, residual persistence, semantic repository exist.                 | Not production-ready due to null-content ingest failure, fake residual fallback, TS scope mismatch, and semantic persistence risks. |

The next best milestone should be:

```text
Milestone: Groups 1–3 Trustworthy Foundation

Done when:
1. Java/TS/proto contracts cannot drift.
2. Source imports produce scoped, durable, complete snapshots or fail closed.
3. Inventory is deterministic and persisted with skip reasons.
4. TS worker respects Java-routed file scope.
5. Residuals are real source fragments, never placeholders.
6. Graph ingest accepts TS worker output without null-content failure.
7. Semantic model persistence is verified by integration tests.
8. Graph + semantic model state cannot become silently partial.
```
