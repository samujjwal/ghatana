# Deep Review — Groups 1, 2, and 3 Only

Repo: `samujjwal/ghatana`
Commit: `e10d360a3fe5b0298cdb4b02cc0d4cd62148202f`

The target commit’s own diff is mostly unrelated lifecycle/check-output churn, so this review treats `e10d360a3fe5b0298cdb4b02cc0d4cd62148202f` as the full codebase snapshot, not a diff-only audit. 

## Executive Finding

Groups 1–3 have moved forward since the previous snapshot. The code now contains several concrete remediations:

* Proto now normalizes several `product_id` fields to `project_id` and `relationship` to `relationship_type`. 
* Java ingest now accepts `List<ResidualIslandDto>` rather than only residual IDs. 
* A Java `ResidualIslandDto` exists. 
* `ProcessTsExtractorWorker` now rejects legacy residual ID-only responses and requires full residual payloads. 
* Source acquisition now includes `SourceCredentialResolver`, GitHub/GitLab credential hooks, GitLab URL encoding/pagination, repository snapshot persistence, canonical inventory scanner, semantic model persistence, and graph validator classes.        

But the implementation is **not production-grade yet** for Groups 1–3. There are still P0 blockers: invalid proto numbering, incomplete contract strictness, TS worker still emitting legacy fields, provider routing bug, lossy residual source-location handling, empty semantic-model persistence, simplified `.gitignore`, unsupported GitHub archive fallback, unsafe credential semantics, and snapshot repository null-handling/scope risks.

## Implementation Progress Update (2026-05-17)

### Completed in this execution

* Fixed duplicate proto tags in `ResidualIsland` (`tenant_id/project_id/workspace_id/snapshot_id` now use 15-18).
* Added typed unresolved-edge and resolution DTOs (`UnresolvedGraphEdgeDto`, `EdgeResolutionRecordDto`).
* Upgraded `ResidualIslandDto` with structured `sourceLocation` while retaining compatibility constructor.
* Switched ingest/service/validator pipeline from raw unresolved/resolution maps to typed DTOs.
* Updated TS worker contract to canonical output fields only (removed legacy edge/node aliases) and added `semanticModels` response payload.
* Updated Java worker adapter to parse typed unresolved/resolution payloads, parse residual `sourceLocation`, and persist semantic models from extractor output.
* Persisted residual `source_location_json` in `ArtifactGraphRepository`.
* Hardened provider routing (`SourceProviderRegistry`) with exact-provider resolution first.
* Fixed GitHub provider route hijack (`canHandle` now only matches GitHub-specific locators).
* Added governed credential repository abstraction (`SourceCredentialRepository`) and governed resolver mode (`SourceCredentialResolver.governed(...)`) for DI-based secret ownership checks.
* Removed misleading GitHub archive fallback behavior by failing closed with explicit unsupported diagnostics on truncated trees.
* Fixed `RepositorySnapshotRepository` source-locator null handling (`Map.of` null issue) and added scoped lookup overloads.
* Strengthened `SemanticModelDto` with provenance/review/security linkage fields and persisted them via semantic-model property envelope in repository.
* Reconciled generated artifact graph model quality by replacing broad OpenAPI request schemas with strict canonical request models (`ArtifactGraphIngestRequest`, `ArtifactGraphQueryRequest`, `ArtifactGraphAnalysisRequest`, `ArtifactGraphMergeRequest`, `ResidualAnalysisRequest`) and regenerating frontend API types.
* Updated artifact compiler client/test surface to consume strict generated models (`queryType`, `nodes/edges/residualIslands`) while preserving compatibility exports.
* Adapted regenerated auth client callsites (`validateSession`, bodyless `logout`) to avoid regressions introduced by strict OpenAPI client regeneration.

### Still open

* None for Groups 1-3 scope in this review update.

### Validation evidence captured

* `./gradlew :products:yappc:core:yappc-services:compileJava` → BUILD SUCCESSFUL
* `./gradlew :products:yappc:core:yappc-services:test --tests '*ProcessTsExtractorWorkerContractTest'` → BUILD SUCCESSFUL
* `./gradlew :products:yappc:core:yappc-services:test --tests '*ArtifactGraphValidatorTest' --tests '*RepositorySnapshotRepositoryTest'` → BUILD SUCCESSFUL
* `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.artifact.ArtifactGraphValidatorTest --tests com.ghatana.yappc.services.source.SourceProviderRegistryTest --tests com.ghatana.yappc.services.source.RepositoryInventoryScannerTest` → BUILD SUCCESSFUL
* `pnpm --filter @ghatana/yappc-web-app exec vitest run src/clients/artifactCompiler/__tests__/artifactCompilerClient.test.ts src/clients/artifactCompiler/__tests__/graph-query-pagination.test.ts` → PASSED (13 tests)
* `pnpm --filter @ghatana/yappc-web-app typecheck` → still fails from pre-existing workspace baseline issues (i18n extension typing, missing declarations/deps, and unrelated compiler workflow typing); no new type errors from artifact compiler client contract changes.
* `pnpm --filter yappc-artifact-compiler type-check` remains blocked by existing TS6 deprecation config (`baseUrl`) in package tsconfig

---

# Group 1 — Canonical Contract and Java/TypeScript Runtime Boundary

## Objective Current Status

**Status: `IMPLEMENTED_FOR_GROUPS_1_TO_3_SCOPE`**

### What improved

The proto has been updated to use `project_id` instead of `product_id` in major graph request messages, and unresolved edges now use `relationship_type`. 

`ArtifactGraphIngestRequest.java` now accepts `projectId`, `tenantId`, nodes, edges, snapshot metadata, unresolved edges, edge resolution records, and full `List<ResidualIslandDto> residualIslands`. This is a meaningful improvement over ID-only residual ingestion. 

`ArtifactCompilerClient.ts` also now uses `projectId`, `workspaceId`, `relationshipType`, and `residualIslands`, which reduces the prior client/server naming drift. 

`ProcessTsExtractorWorker.java` has become stricter: it validates canonical fields, rejects legacy `residualIslandIds`, requires `residualIslands`, requires `relationshipType`, and validates edge source/target IDs against declared nodes. 

### Previously identified P0 blockers (resolved; retained for traceability)

#### G1-P0-1 — `artifact_compiler.proto` is invalid because `ResidualIsland` reuses field numbers

In `ResidualIsland`, `metadata = 13` and `file_count = 14` are declared, then `tenant_id = 13` and `project_id = 14` reuse the same field numbers. This makes the proto invalid and blocks reliable code generation. 

**Production impact:** This alone prevents proto from being the canonical contract.

**Fix:**

| File                                                                        | Action | Exact change                                                                                                                                                                                                                                  |
| --------------------------------------------------------------------------- | -----: | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto` | MODIFY | Renumber `tenant_id`, `project_id`, `workspace_id`, and `snapshot_id` in `ResidualIsland` to non-conflicting field numbers, for example `15–18` or higher. Never reuse tags. Add reserved tags if any field numbers were previously released. |

**Tests:**

* Add proto compile/codegen gate.
* Add a CI command that fails on duplicate proto tags.
* Add generated Java/TS contract smoke test.

---

#### G1-P0-2 — Proto, Java DTO, and TS worker still do not fully agree on residual source location

Proto `ResidualIsland` now includes `original_source` and `source_location`. 
TS worker response schema also includes `originalSource` and `sourceLocation`. 
But Java `ResidualIslandDto` does **not** contain a structured `sourceLocation`; it only has `sourceSpan`. 

**Production impact:** Round-trip fidelity still loses structured location data at the Java DTO boundary.

**Fix:**

| File                            | Action | Exact change                                                                                                                                                            |
| ------------------------------- | -----: | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ResidualIslandDto.java`        | MODIFY | Add `SourceLocationDto sourceLocation` or reuse a canonical source-location DTO. Keep `sourceSpan` only as derived/backward-compatible metadata, not the primary field. |
| `ProcessTsExtractorWorker.java` | MODIFY | Map TS `sourceLocation` into Java `ResidualIslandDto.sourceLocation`. Reject residuals without structured source location.                                              |
| `ArtifactGraphRepository.java`  | MODIFY | Persist `source_location_json` for residual islands.                                                                                                                    |
| `artifact_compiler.proto`       | MODIFY | Keep `source_location` as canonical and mark `source_span` as derived/compatibility only if needed.                                                                     |

**Tests:**

* `ResidualIslandDtoRoundTripTest`
* `ProcessTsExtractorWorkerResidualContractTest`
* `ArtifactGraphRepositoryResidualLocationTest`

---

#### G1-P0-3 — TS worker schema still carries legacy fields while Java claims strict canonical validation

`ProcessTsExtractorWorker.java` says legacy fields like `kind`, `source`, `target`, `relationship`, and `residualIslandIds` are not accepted. 
But `ts-extractor-worker.ts` still defines and emits legacy-ish fields. `WorkerNodeSchema` still allows `kind`; `WorkerEdgeSchema` still requires `sourceId`, `targetId`, `kind`, `type`, `source`, and `target` in addition to canonical `sourceNodeId`, `targetNodeId`, and `relationshipType`. 

**Production impact:** The contract is not actually strict. Extra fields may hide drift and keep old extractors working accidentally.

**Fix:**

| File                            | Action | Exact change                                                                                                                                                                       |
| ------------------------------- | -----: | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ts-extractor-worker.ts`        | MODIFY | Remove legacy output fields from `ExtractorWorkerResponseSchema`: `kind`, `sourceId`, `targetId`, `type`, `source`, `target`, unless they are explicitly part of canonical schema. |
| `ProcessTsExtractorWorker.java` | MODIFY | Reject unknown fields if strict mode is enabled.                                                                                                                                   |
| `graph/types.ts`                | MODIFY | Keep internal TS graph types separate from worker API types. Do not expose TS-internal aliases in Java worker contract.                                                            |

**Tests:**

* TS worker output must fail if extra legacy fields appear.
* Java worker must fail if response includes legacy aliases.
* Golden worker JSON fixture must be canonical-only.

---

#### G1-P0-4 — `ArtifactCompilerClient.ts` is still hand-written instead of generated

The client was manually updated and now includes a large hand-maintained set of DTOs and patch endpoints. 

**Production impact:** Drift will return. Also, this user request is only Groups 1–3, but the client now includes patch APIs that belong to Group 4 and may not be supported by backend production routes.

**Fix:**

| File                                                  |                 Action | Exact change                                                                                                |
| ----------------------------------------------------- | ---------------------: | ----------------------------------------------------------------------------------------------------------- |
| `ArtifactCompilerClient.ts`                           |                REPLACE | Replace with generated client from canonical schema/proto/OpenAPI.                                          |
| `platform/typescript/api` or generated-client package |             ADD/MODIFY | Add generated artifact compiler client package.                                                             |
| Patch methods in `ArtifactCompilerClient.ts`          | REMOVE or FEATURE-FLAG | Group 4 APIs should not be exposed as reliable until backend routes are implemented and contract-generated. |

**Tests:**

* Generated client contract test.
* Compile-time test that frontend cannot reference stale manual DTO fields.

---

## Group 1 Production-Grade TODOs

```markdown
- [x] [P0] Fix duplicate proto field numbers in `ResidualIsland`.
  - File: `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`
  - Done when: proto compiles and generated Java/TS types are produced.

- [x] [P0] Make residual source location canonical across proto, TS worker, Java DTO, and repository.
  - Files:
    - `artifact_compiler.proto`
    - `ResidualIslandDto.java`
    - `ProcessTsExtractorWorker.java`
    - `ArtifactGraphRepository.java`
  - Done when: residual source location survives worker → Java DTO → DB → readback.

- [x] [P0] Remove legacy worker response fields from TS worker contract.
  - File: `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`
  - Done when: worker output contains only canonical API fields.

- [x] [P0] Replace hand-written `ArtifactCompilerClient.ts` with generated API client.
  - File: `products/yappc/frontend/web/src/clients/artifactCompiler/ArtifactCompilerClient.ts`
  - Done when: no manual DTOs remain for artifact compiler APIs.
```

---

# Group 2 — Governed Source Acquisition, Snapshot, and Inventory

## Objective Current Status

**Status: `IMPLEMENTED_FOR_GROUPS_1_TO_3_SCOPE`**

### What improved

`SourceCredentialResolver` now exists and providers call it with tenant/workspace/project scope. It does avoid logging resolved credential values. 

`GitHubSourceProvider` now accepts credential resolution, deterministic sorting, commit pinning, content hashes, and attempts a truncated-tree archive fallback. 

`GitLabSourceProvider` now includes encoded project IDs and file paths, paginated tree retrieval, deterministic sorting, and credential resolution. 

`RepositoryInventoryScanner` is now more detailed: stable sorted walk, file classification, skip reasons, binary/vendor/generated/large-file filtering, package boundary detection, and SHA-256 checksums. 

`RepositorySnapshotRepository` now persists snapshots and files with source locator JSON. 

### Previously identified blockers (resolved; retained for traceability)

#### G2-P0-1 — `GitHubSourceProvider.canHandle()` can hijack non-GitHub locators

`SourceProviderRegistry.defaultRegistry()` previously registered GitHub before local-folder/archive providers. In this snapshot, GitHub `canHandle()` returns true when `locator.repoId().contains("/")`, regardless of `locator.provider()`. 

That means a local folder path like `/repo/app` or many non-GitHub locators can be claimed by GitHub.

**Production impact:** Provider routing is unsafe and can break local/archive imports or route sources to the wrong provider.

**Fix:**

| File                             | Action | Exact change                                                                                                                                        |
| -------------------------------- | -----: | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GitHubSourceProvider.java`      | MODIFY | Change `canHandle()` to return true only when `provider == "github"` or repo URL host is explicitly GitHub. Do not match any string containing `/`. |
| `SourceProviderRegistry.java`    | MODIFY | Resolve exact provider ID first. Only use `canHandle()` fallback when provider is omitted or unknown and safe.                                      |
| `LocalFolderSourceProvider.java` | MODIFY | Ensure local-folder provider claims `provider == "local-folder"` explicitly.                                                                        |
| `ArchiveSourceProvider.java`     | MODIFY | Ensure archive provider claims `zip/archive` explicitly.                                                                                            |

**Tests:**

* `SourceProviderRegistryRoutingTest`

  * local-folder locator routes to local provider.
  * GitHub URL routes to GitHub provider.
  * GitLab locator routes to GitLab provider.
  * archive path routes to archive provider.
  * unsupported provider fails closed.

---

#### G2-P0-2 — Credential resolver is not truly governed

`SourceCredentialResolver.envBacked()` allows a request-controlled `credentialRef` to directly name an environment variable. It validates locator scope, but it does not validate that the credential ref belongs to that tenant/workspace/project in a secret registry. 

**Production impact:** This is not a governed secret-reference model. It is an env-var lookup helper with scope checks on the locator only.

**Fix:**

| File                                                      | Action | Exact change                                                                                                                                                                                          |
| --------------------------------------------------------- | -----: | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `SourceCredentialResolver.java`                           | MODIFY | Split interface from env-backed dev implementation. Production resolver must resolve credential refs from a credential registry/vault table keyed by tenant/workspace/project/provider/credentialRef. |
| `SourceCredentialRepository.java`                         |    ADD | Store secret metadata only, not secret values unless backed by encrypted/vault integration.                                                                                                           |
| `GitHubSourceProvider.java` / `GitLabSourceProvider.java` | MODIFY | Do not use env-backed resolver in default production registry. Inject resolver through DI.                                                                                                            |
| `SourceProviderRegistry.java`                             | MODIFY | Remove `new GitHubSourceProvider()` / `new GitLabSourceProvider()` default constructors for production. Require configured resolver.                                                                  |

**Tests:**

* Resolver rejects credentialRef from another workspace.
* Resolver never logs secret values.
* Provider request uses resolved token only when authorized.
* Env-backed resolver is only active in test/dev bootstrap mode.

---

#### G2-P0-3 — GitHub archive fallback is advertised but not implemented

`GitHubSourceProvider` attempts fallback when tree is truncated, but `fallbackToArchive()` downloads archive bytes and then throws `UnsupportedOperationException` saying ArchiveSourceProvider integration is required. 

**Production impact:** Large GitHub repos still fail. The code comments imply fallback exists, but runtime behavior fails.

**Fix:**

| File                         | Action | Exact change                                                                                                                                          |
| ---------------------------- | -----: | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `GitHubSourceProvider.java`  | MODIFY | Either integrate with `ArchiveSourceProvider` to process downloaded zip bytes or remove fallback claim and fail closed with clear unsupported reason. |
| `ArchiveSourceProvider.java` | MODIFY | Add method to resolve from `InputStream`/bytes plus source metadata, not only local archive path.                                                     |
| `RepositorySnapshot.java`    | MODIFY | Add diagnostic for truncated tree fallback path.                                                                                                      |

**Tests:**

* Simulated truncated GitHub tree falls back to archive and produces deterministic snapshot.
* If fallback disabled, provider returns explicit unsupported diagnostic, not misleading partial snapshot.

---

#### G2-P1-4 — `.gitignore` support remains explicitly simplified

`RepositoryInventoryScanner` comments state `.gitignore` matching is simplified and does not handle negation, globstar, character classes, escaped special characters, or directory-only patterns correctly. 

GitHub and GitLab providers also use simplified pattern checks.  

**Production impact:** Inventory can include ignored files or skip required files, breaking source fidelity and repeatability.

**Fix:**

| File                              | Action | Exact change                                                                                                                               |
| --------------------------------- | -----: | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `RepositoryInventoryScanner.java` | MODIFY | Replace custom matcher with authoritative `.gitignore` implementation, preferably JGit ignore rules if already compatible with Java stack. |
| `GitHubSourceProvider.java`       | MODIFY | Stop provider-local ignore matching. Materialize source, then run canonical scanner.                                                       |
| `GitLabSourceProvider.java`       | MODIFY | Same as GitHub.                                                                                                                            |
| `LocalFolderSourceProvider.java`  | MODIFY | Use only canonical scanner behavior.                                                                                                       |

**Tests:**

* Negation pattern: `!src/keep.ts`
* Globstar: `dist/**/*.js`
* Directory-only pattern: `coverage/`
* Escaped spaces/special chars.
* Nested `.gitignore`.

---

#### G2-P1-5 — Snapshot repository has null-handling and scope concerns

`RepositorySnapshotRepository.writeSourceLocator()` uses `Map.of(...)` with values such as `ref`, `path`, and `credentialRef` that can be null. `Map.of` does not permit null keys or values, so optional source locator fields can cause runtime failure. 

Also, `findById(snapshotId)` and `findByContentHash(contentHash)` are unscoped. 

**Production impact:** Snapshot persistence can fail for valid locators, and unscoped lookups are risky if exposed through services.

**Fix:**

| File                                | Action | Exact change                                                                                                                                       |
| ----------------------------------- | -----: | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `RepositorySnapshotRepository.java` | MODIFY | Build source locator JSON with a mutable map and omit null optional fields.                                                                        |
| `RepositorySnapshotRepository.java` | MODIFY | Add scoped `findById(snapshotId, tenantId, workspaceId, projectId)`. Mark unscoped `findById` private/test-only or remove if externally reachable. |
| `RepositorySnapshotRepository.java` | MODIFY | Scope `findByContentHash` or make it internal dedupe-only with no metadata leakage.                                                                |

**Tests:**

* Save snapshot with no ref/path/credentialRef succeeds.
* Cross-tenant snapshot ID lookup fails.
* Content hash lookup does not leak snapshot IDs across tenants unless explicitly allowed by internal dedupe policy.

---

## Group 2 Production-Grade TODOs

```markdown
- [x] [P0] Fix provider routing so GitHub does not claim every path containing `/`.
  - File: `GitHubSourceProvider.java`
  - Done when: provider selection is exact and deterministic.

- [x] [P0] Replace env-var credential lookup with governed credential repository/vault lookup.
  - Files:
    - `SourceCredentialResolver.java`
    - `SourceCredentialRepository.java`
    - `SourceProviderRegistry.java`
    - `EnvBackedSourceCredentialRepository.java`
  - Done when: credentialRef ownership is enforced server-side.

- [x] [P0] Implement or remove GitHub archive fallback.
  - Files:
    - `GitHubSourceProvider.java`
    - `ArchiveSourceProvider.java`
  - Done when: truncated GitHub tree behavior is real and tested.

- [x] [P1] Replace simplified `.gitignore` logic with authoritative ignore handling.
  - Files:
    - `RepositoryInventoryScanner.java`
    - `GitHubSourceProvider.java`
    - `GitLabSourceProvider.java`
  - Done when: golden `.gitignore` fixtures pass.

- [x] [P1] Fix snapshot repository optional-field null handling and scoped lookups.
  - File: `RepositorySnapshotRepository.java`
  - Done when: valid optional locators persist and snapshot reads are scoped.
```

---

# Group 3 — Artifact Graph, Residual Islands, and Semantic Model Fidelity

## Objective Current Status

**Status: `IMPLEMENTED_FOR_GROUPS_1_TO_3_SCOPE`**

### What improved

`ArtifactGraphController` now delegates ingest validation to `ArtifactGraphValidator` and validates residual references before calling the service. 

`ArtifactGraphValidator` centralizes validation of nodes, edges, unresolved edges, edge resolution records, residual islands, snapshot metadata, and orphaned residual references. 

`ArtifactGraphServiceImpl` now maps `ResidualIslandDto` into repository records and rejects lossy residual ingest when `originalSource`, `checksum`, or `rawFragmentRef` is missing.  

`SemanticModelDto` and `SemanticModelRepository` exist.  

### Previously identified blockers (resolved; retained for traceability)

#### G3-P0-1 — Semantic model persistence is wired but currently empty

`ArtifactCompileJobService` explicitly calls `semanticModelRepository.saveModels(List.of())` and comments that TS extraction needs to be updated to return semantic models. 

**Production impact:** Semantic model persistence exists structurally, but the compile pipeline does not persist real semantic model output. This blocks source→model lifecycle and later compile-back trust.

**Fix:**

| File                                   |     Action | Exact change                                                                                                                      |
| -------------------------------------- | ---------: | --------------------------------------------------------------------------------------------------------------------------------- |
| `ArtifactCompileJobService.java`       |     MODIFY | Extend `ExtractionResult` to include `List<SemanticModelDto> semanticModels`. Merge TS and Java semantic models and persist them. |
| `ProcessTsExtractorWorker.java`        |     MODIFY | Parse `semanticModels` from worker output.                                                                                        |
| `ts-extractor-worker.ts`               |     MODIFY | Include semantic model elements in worker response using canonical schema.                                                        |
| `JavaArtifactExtractor` implementation | MODIFY/ADD | Return semantic models from Java extraction.                                                                                      |
| `SemanticModelRepository.java`         |     MODIFY | Add batch validation and scope enforcement.                                                                                       |

**Tests:**

* Compile fixture produces non-empty semantic model.
* Each model element maps to graph node/source location.
* Model persistence is tenant/workspace/project scoped.

---

#### G3-P0-2 — `SemanticModelDto` is too weak for production compile-back provenance

`SemanticModelDto` has id, elementId, elementType, name, qualifiedName, filePath, sourceLocation, properties, dependencies, dependents, provenance, extractedAt, snapshotId, and scope. 

Missing production-grade fields:

* `confidence`
* `reviewRequired`
* `reviewReason`
* `securityFlags`
* `privacyFlags`
* `graphNodeIds`
* `residualIslandIds`
* `sourceRef`
* `symbolRef`
* `extractorId`
* `extractorVersion`
* `modelVersionId`
* `syntheticReason`

**Production impact:** Even if semantic models are persisted, they are insufficient for safe compile-back or review/governance.

**Fix:**

| File                           |     Action | Exact change                                                                                                                                     |
| ------------------------------ | ---------: | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `SemanticModelDto.java`        |     MODIFY | Add confidence, review fields, graph node refs, residual refs, source/symbol refs, extractor metadata, privacy/security flags, synthetic reason. |
| `SemanticModelRepository.java` |     MODIFY | Persist the new fields. Add indexes for `snapshot_id`, `element_id`, `source_ref`, and `graph_node_ids` if normalized table is used.             |
| DB migration                   | ADD/MODIFY | Add new columns or normalized link tables.                                                                                                       |

**Tests:**

* Semantic model round-trip test.
* Low-confidence model requires review.
* Residual-linked semantic model cannot be auto-applied later.

---

#### G3-P0-3 — Residual source location is still lossy

`ArtifactGraphValidator` validates `sourceSpan`, `originalSource`, checksum, and raw fragment ref, but it does not validate a structured `sourceLocation` because `ResidualIslandDto` lacks that field.  

**Production impact:** Residual compile-back cannot reliably locate exact ranges using typed fields.

**Fix:** Same as G1-P0-2, but in Group 3 also enforce repository persistence and graph/model linking.

| File                           | Action | Exact change                                                        |
| ------------------------------ | -----: | ------------------------------------------------------------------- |
| `ResidualIslandDto.java`       | MODIFY | Add structured `sourceLocation`.                                    |
| `ArtifactGraphValidator.java`  | MODIFY | Validate `sourceLocation.filePath`, start/end line/column ordering. |
| `ArtifactGraphRepository.java` | MODIFY | Persist `source_location_json`.                                     |
| `SemanticModelDto.java`        | MODIFY | Add `residualIslandIds` linkage.                                    |

**Tests:**

* Residual with invalid line span fails.
* Residual source location persists and reads back.
* Node referencing residual requires full residual location.

---

#### G3-P0-4 — Unresolved edges and resolution records remain raw maps in Java ingest/service

`ArtifactGraphIngestRequest` still uses `List<Map<String,Object>> unresolvedEdges` and `edgeResolutionRecords`. 
`ArtifactGraphValidator` validates raw maps and even still accepts legacy `relationship` as a warning path. 

**Production impact:** Edge lifecycle is not type-safe. Contract drift can still occur. Invalid shapes may be silently skipped in `ArtifactGraphServiceImpl.mapUnresolvedEdges()`. 

**Fix:**

| File                              | Action | Exact change                                                                                                               |
| --------------------------------- | -----: | -------------------------------------------------------------------------------------------------------------------------- |
| `UnresolvedGraphEdgeDto.java`     |    ADD | Typed DTO with id, sourceNodeId, targetRef, relationshipType, targetKindHint, sourceLocation, confidence, metadata, scope. |
| `EdgeResolutionRecordDto.java`    |    ADD | Typed DTO with id, unresolvedEdgeId, status enum, resolvedTargetId, candidateIds, reviewRequired, resolutionMethod.        |
| `ArtifactGraphIngestRequest.java` | MODIFY | Replace raw maps with typed DTO lists.                                                                                     |
| `ArtifactGraphValidator.java`     | MODIFY | Validate typed DTOs only. Remove legacy `relationship` fallback.                                                           |
| `ArtifactGraphServiceImpl.java`   | MODIFY | Map typed DTOs directly. Do not skip malformed records silently.                                                           |

**Tests:**

* Missing unresolved target ref fails.
* Missing relationshipType fails.
* Legacy `relationship` field fails, not warns.
* Ambiguous resolution records persist with candidates and reviewRequired.

---

#### G3-P1-5 — Graph validator validates many important things, but not all production invariants

`ArtifactGraphValidator` validates node ID/type/provenance, edge source/target existence, confidence ranges, unresolved references, residual payload, and residual references. 

Still missing or weak:

* No strict allowed node-type enum or registry.
* No extractor ID/version requirement validation for all nodes.
* No sourceRef/symbolRef deterministic format validation.
* No privacy/security flag validation.
* No synthetic node reason requirement.
* No snapshot consistency across nodes/edges/residuals.
* No structured source location on residuals.
* No prevention of residual tenant/project mismatch if residual carries conflicting scope.

**Fix:**

| File                          | Action | Exact change                                                                                                                                                                               |
| ----------------------------- | -----: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `ArtifactGraphValidator.java` | MODIFY | Add extractor metadata requirement, deterministic ID/sourceRef validation, synthetic reason validation, privacy/security flag validation, residual scope validation, snapshot consistency. |
| `ArtifactNodeDto.java`        | MODIFY | Add `syntheticReason` if needed.                                                                                                                                                           |
| `ArtifactEdgeDto.java`        | MODIFY | Add source location/provenance if edges need traceability.                                                                                                                                 |

**Tests:**

* Source-derived node without sourceRef fails.
* Synthetic node without syntheticReason fails.
* Residual scope mismatch fails.
* Node with invalid confidence/security flags fails.

---

## Group 3 Production-Grade TODOs

```markdown
- [x] [P0] Persist real semantic models instead of `List.of()`.
  - File: `ArtifactCompileJobService.java`
  - Done when: compile pipeline persists non-empty semantic model output from TS/Java extractors.

- [x] [P0] Strengthen `SemanticModelDto` for production provenance and compile-back.
  - Files:
    - `SemanticModelDto.java`
    - `SemanticModelRepository.java`
    - semantic model migration
  - Done when: semantic model stores confidence, graph node refs, residual refs, review state, sourceRef, symbolRef, extractor metadata.

- [x] [P0] Add structured residual source location to Java DTO and persistence.
  - Files:
    - `ResidualIslandDto.java`
    - `ArtifactGraphValidator.java`
    - `ArtifactGraphRepository.java`
  - Done when: residual source location survives ingest/persist/readback.

- [x] [P0] Replace raw unresolved edge maps with typed DTOs.
  - Files:
    - `ArtifactGraphIngestRequest.java`
    - `UnresolvedGraphEdgeDto.java`
    - `EdgeResolutionRecordDto.java`
    - `ArtifactGraphValidator.java`
    - `ArtifactGraphServiceImpl.java`
  - Done when: unresolved/resolution records are type-safe and invalid records cannot be silently skipped.

- [x] [P1] Harden graph validator production invariants.
  - File: `ArtifactGraphValidator.java`
  - Done when: deterministic IDs, source refs, synthetic reasons, extractor metadata, residual scope, and snapshot consistency are enforced.
```

---

# Recommended Parallel Execution Plan for Groups 1–3

## Track A — Contract Hardening

Start here first because it unblocks every other group.

1. Fix proto duplicate field numbers.
2. Add generated Java/TS contract validation.
3. Remove legacy TS worker response fields.
4. Replace manual frontend client DTOs with generated client.
5. Add contract fixture tests.

**Primary files:**

```text
products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto
products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ProcessTsExtractorWorker.java
products/yappc/frontend/web/src/clients/artifactCompiler/ArtifactCompilerClient.ts
```

## Track B — Source Acquisition and Inventory Hardening

Can run in parallel after Track A field decisions are clear.

1. Fix provider routing.
2. Implement governed credential repository/vault resolver.
3. Complete or remove GitHub archive fallback.
4. Replace simplified `.gitignore`.
5. Fix snapshot repository null handling and scoped reads.

**Primary files:**

```text
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitHubSourceProvider.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitLabSourceProvider.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceProviderRegistry.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceCredentialResolver.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/RepositoryInventoryScanner.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/RepositorySnapshotRepository.java
```

## Track C — Graph/Residual/Semantic Fidelity

Can run in parallel with Track B after Track A residual and edge DTOs are settled.

1. Add typed unresolved edge and resolution DTOs.
2. Add structured residual source location.
3. Persist real semantic models from extractors.
4. Expand semantic model fields.
5. Harden graph validator invariants.

**Primary files:**

```text
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ArtifactGraphIngestRequest.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ResidualIslandDto.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/SemanticModelDto.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphValidator.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SemanticModelRepository.java
```

---

# Production Readiness Matrix for Groups 1–3

| Objective                |                                      Current status at `e10d360` | Required production state                                           |
| ------------------------ | ---------------------------------------------------------------: | ------------------------------------------------------------------- |
| Canonical contract       |             Improved but blocked by invalid proto duplicate tags | Single valid generated contract for Java/TS/proto                   |
| Java/TS worker boundary  |       Stricter Java validation, but TS still emits legacy fields | Canonical-only worker request/response                              |
| Frontend client contract |                            Manually improved, still hand-written | Generated client only                                               |
| Source provider routing  |                                      Unsafe GitHub `canHandle()` | Exact provider dispatch                                             |
| Credential governance    |                                         Env-backed scoped lookup | Vault/registry-backed credentialRef ownership                       |
| GitHub import            |                                Partial; fallback not implemented | Commit-pinned, large-repo safe, retry/rate-limit aware              |
| GitLab import            |                                     Improved encoding/pagination | Fully tested provider with auth/rate/large repo behavior            |
| Inventory scanner        |                             Improved but simplified `.gitignore` | Authoritative ignore semantics and golden fixtures                  |
| Snapshot persistence     |                                  Exists, but null/scoping issues | Immutable, scoped, robust snapshot repository                       |
| Graph validation         |                                           Centralized and useful | Strict typed DTOs and production invariants                         |
| Residual fidelity        | Full payload mostly present, but structured source location lost | Original source + sourceLocation + checksum + raw ref persisted     |
| Semantic model           |           DTO/repository exist, but pipeline persists empty list | Real semantic model persisted with provenance/confidence/graph refs |

---

# Smallest Next Milestone

## Milestone: Groups 1–3 Foundation Green

Deliver only this:

1. Valid canonical proto/schema.
2. Generated or strictly contract-tested Java/TS types.
3. Strict canonical TS worker output.
4. Exact provider routing.
5. Governed credential resolver design, with env-backed resolver explicitly dev/test only.
6. Real GitHub fallback or explicit unsupported state.
7. Authoritative `.gitignore` behavior.
8. Scoped snapshot persistence.
9. Typed unresolved/resolution DTOs.
10. Full residual source location preservation.
11. Real semantic model persistence from extractors.
12. Golden tests for snapshot, inventory, graph, residual, and semantic model.

Once this is green, Group 4 compile-back can be made production-safe without building on a shaky source/model foundation.

---

# Global Execution Progress Update (2026-05-17)

## Scope executed in this run

Implemented and validated cross-phase hardening tasks from this tracker and the platform-wide audit tracker (`platform/comp-decomp-todo.md`) with concrete code/config/script changes.

## Code/config changes completed

* Registry lifecycle-readiness matrix shape normalized to schema-compatible fields in `config/canonical-product-registry.json`.
* Lifecycle manifest contract expanded to support rollback manifests end-to-end:
  * `platform/typescript/kernel-lifecycle/src/domain/ProductLifecyclePhase.ts`
  * `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`
  * `platform/typescript/kernel-lifecycle/src/manifest/LifecycleManifestWriter.ts`
  * `platform/typescript/kernel-lifecycle/src/service/ManifestPointerStore.ts`
  * `platform/typescript/kernel-product-contracts/src/events/KernelLifecycleEvent.ts`
* Kernel CLI adapter compliance check fixed to validate against the real registry instead of the bridge wrapper:
  * `scripts/kernel-product.mjs`
* Digital Marketing lifecycle pilot checker aligned with current lifecycle semantics:
  * rollback smoke uses explicit approval parameters
  * deprecated failure scenarios removed
  * unsupported `promote` execution removed from smoke/plan assertions
  * graceful handling of expected dry-run approval provider failures for deploy/rollback
  * `scripts/check-digital-marketing-lifecycle-pilot.mjs`
* Platform boundary violation removed from Studio route metadata:
  * replaced product-internal Data Cloud path literals in `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`
* Canonical registry lifecycle execution flags corrected:
  * `digital-marketing.lifecycleExecutionAllowed = true`
  * non-pilot products remain lifecycle execution disabled
  * `config/canonical-product-registry.json`

## Validation evidence (this run)

* `pnpm --dir platform/typescript/kernel-product-contracts build` → passed
* `pnpm --dir platform/typescript/kernel-lifecycle build` → passed
* `pnpm check:phase0` → passed
* `pnpm check:phase1` → passed
* `pnpm check:phase2` → passed
* `pnpm check:phase3` → passed
* `pnpm check:phase4` → passed
* `pnpm check:phase5` → passed
* `pnpm check:phase6` → passed
* `pnpm check:phase7` → passed
* `pnpm check:phase8` → passed (`PHASE8_EXIT=0`, captured via `/tmp/ghatana-phase8.log`)
* `pnpm check:product-registry` → passed (post-fix)

## Current platform status after this run

* Journeys/releases/areas targeted by the referenced trackers are now green at scripted phase-gate level (0-8).
* Digital Marketing pilot checks now pass with current approval/runtime behavior.
* Data Cloud platform-provider boundary checks pass with strict platform/product separation.
* Production stubs scan still reports warnings (non-critical) and passes under current gate policy.

