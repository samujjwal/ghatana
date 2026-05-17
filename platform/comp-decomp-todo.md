# Focused deep review at `c5d4066edc67e4f1bc37610fef594b12e159aada`

Scope: **Group 1, Group 2, Group 3 only**

1. **Group 1 — Canonical Contract and Java/TypeScript Runtime Boundary**
2. **Group 2 — Governed Source Acquisition, Snapshot, and Inventory**
3. **Group 3 — Artifact Graph, Residual Islands, and Semantic Model Fidelity**

I treated `c5d4066...` as the target repo snapshot, not a diff-only commit. The commit itself is unrelated to Artifact Compiler and mostly touches digital-marketing CI/evidence, so the conclusions below come from the relevant YAPPC files at that ref. 

---

# Executive finding

The codebase has moved forward significantly from the earlier state. The biggest improvements are:

* Proto now normalizes `project_id` and `relationship_type`, adds full residual payload fields, and marks `residual_island_ids` as deprecated. 
* Java ingest now uses typed `UnresolvedGraphEdgeDto`, `EdgeResolutionRecordDto`, and `ResidualIslandDto` instead of raw maps and residual IDs only. 
* Java `ProcessTsExtractorWorker` now strictly rejects legacy worker fields such as `kind`, `source`, `target`, `relationship`, and `residualIslandIds`, and requires full residual payloads. 
* Source providers now have credential resolver abstractions and GitLab URL encoding/pagination improvements.  
* Java now has `RepositorySnapshotRepository`, `SemanticModelDto`, `SemanticModelRepository`, and `ArtifactGraphValidator`.    

But the current state is still **not production-grade** for Groups 1–3 because the deepest risks are now integration and correctness risks:

1. **P0 migration/schema drift can break snapshot persistence.** `RepositorySnapshotRepository` inserts into columns such as `snapshot_id`, `materialized_root`, `checksum`, `content_hash`, and `source_locator_json`, while `V15__create_repository_snapshots.sql` creates `id`, `local_root_path`, and `content_checksum`; `V21` adds `checksum` and `source_locator_json` but not `snapshot_id`, `materialized_root`, or a safe migration from `id` to `snapshot_id`.   
2. **Group 1 is better but still not truly generated-contract safe.** TS frontend uses generated API types for graph/import calls, but still has manual compatibility patch-bundle methods and local wrapper response types. 
3. **Group 2 has source acquisition abstractions, but provider behavior is still not fully production-grade.** GitHub has no real archive fallback despite mentioning it, local-folder trusted root is still `System.getProperty("user.dir")`, inventory `.gitignore` is still documented as simplified, and checksum/scanning reads files fully into memory.   
4. **Group 3 has strong DTO/validator improvements but semantic model persistence appears schema-risky.** `SemanticModelRepository` expects a `semantic_models` table, but I did not find a migration in search results for `CREATE TABLE semantic_models`; this should be treated as a P0 verification/fix before relying on compile jobs. 
5. **Compile orchestration references checked exceptions inside ActiveJ promise lambdas.** `ArtifactCompileJobService.compile()` calls `inventoryScanner.scanRepository(...)`, which throws `IOException`, inside a lambda without visible local handling in the fetched file. This should be verified by compilation and fixed with explicit exception-to-Promise conversion if failing.  

---

# Group 1 — Canonical Contract and Java/TypeScript Runtime Boundary

## Objective current status

**Current status: `PARTIALLY_IMPLEMENTED`, improved, but not fully contract-safe**

### What is good now

The proto has moved in the right direction. It now uses `project_id` instead of `product_id` for ingest, analysis, query, and merge requests, and unresolved edges now use `relationship_type`, matching resolved edge naming. It also adds `ResidualIsland.original_source`, structured `SourceLocation`, `checksum`, `raw_fragment_ref`, and marks `residual_island_ids` as deprecated. 

Java ingest now aligns more closely with this direction. `ArtifactGraphIngestRequest` uses `projectId`, typed `UnresolvedGraphEdgeDto`, typed `EdgeResolutionRecordDto`, and full `List<ResidualIslandDto>` payloads. It still has a `fromLegacyMaps` helper, but the main record no longer uses raw map fields. 

The TS worker now emits canonical `type`, `relationshipType`, `sourceNodeId`, `targetNodeId`, `residualIslands`, and `semanticModels`, and its response schema no longer includes legacy `kind/source/target/relationship` fields. 

Java `ProcessTsExtractorWorker` now rejects legacy `residualIslandIds`, rejects node `kind`, rejects edge `source/target`, rejects edge `relationship/type/kind`, validates resolved edge endpoints against declared nodes, and requires full residual payload fields. 

The frontend `ArtifactCompilerClient` now imports generated API types and service calls from `@/clients/generated/api`, which is a good shift away from fully hand-written graph/import DTOs. 

### What is still not production-grade

The contract is still not fully single-source-truth because:

* Proto exists, generated OpenAPI client exists, Java DTOs exist, and TS worker Zod schemas exist. The code does not prove these are generated from one canonical schema or validated as a unified contract in CI.
* `ArtifactCompilerClient` still defines manual wrapper response types and manual patch-bundle request/response types outside generated API coverage. 
* `ProcessTsExtractorWorker.extract(snapshot, tsFiles)` ignores the `tsFiles` argument and sends `snapshot.files()` to the worker. That weakens the intended language routing boundary introduced in `ArtifactCompileJobService`.  
* `ts-extractor-worker.ts` still accepts three request shapes: canonical snapshot, nested Java request, and flat Java request. That helps migration, but it is not a strict production boundary. 

## Group 1 production objective

By the end of Group 1:

* There is exactly one canonical contract generation path.
* Java DTOs, TS worker schemas, frontend generated API client, and proto/OpenAPI cannot drift.
* TS extractor worker accepts exactly one production request shape.
* Java worker adapter sends only filtered files it intends to extract.
* Manual frontend patch compatibility methods are either generated or feature-flagged unsupported.

## Group 1 file-by-file implementation plan

| Priority | File path                                                                                                            | Action | Exact change                                                                                                                                                                                                                                                                                   | Tests                                               |
| -------: | -------------------------------------------------------------------------------------------------------------------- | ------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- |
|       P0 | `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`                                          | MODIFY | Decide whether proto is truly canonical. If yes, add all missing worker request/response and semantic model fields required by TS worker and Java repositories, including `semantic_models`, `source_location`, `graph_node_ids`, `residual_island_ids`, review flags, and extractor metadata. | Add proto compatibility/generation test.            |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`                             | MODIFY | Remove `FlatJavaExtractorWorkerRequestSchema` and legacy/nested compatibility after migration. Keep only the canonical worker request shape. Ensure `semanticModels` carries full `SemanticModelDto` parity fields, not just id/type/name/file/provenance.                                     | `ts-extractor-worker.contract.test.ts`              |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ProcessTsExtractorWorker.java` | MODIFY | Use the `tsFiles` argument when building the worker payload instead of `snapshot.files()`. Validate returned node file paths are within `tsFiles` unless explicitly cross-file references.                                                                                                     | `ProcessTsExtractorWorkerFilteredFilesTest.java`    |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ArtifactGraphIngestRequest.java` | MODIFY | Remove `fromLegacyMaps` once all callers are migrated. Until then, feature-flag it as migration-only and add tests proving legacy `relationship` is rejected.                                                                                                                                  | `ArtifactGraphIngestRequestStrictContractTest.java` |
|       P1 | `products/yappc/frontend/web/src/clients/artifactCompiler/ArtifactCompilerClient.ts`                                 | MODIFY | Move manual patch-bundle methods behind `artifactPatchApiEnabled` capability or replace with generated patch APIs. Remove local response types once generated equivalents exist.                                                                                                               | `ArtifactCompilerClientGeneratedOnlyTest.ts`        |
|       P1 | `products/yappc/core/yappc-services/src/test/java/.../contract/ArtifactCompilerContractCompatibilityTest.java`       | ADD    | Load canonical JSON fixtures for worker response, ingest request, residual payload, semantic model payload, and verify Java DTO deserialization plus validator behavior.                                                                                                                       | New test                                            |
|       P1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/__tests__/contract/java-worker-fixtures.test.ts`           | ADD    | Use same fixtures as Java tests and validate with Zod schemas.                                                                                                                                                                                                                                 | New test                                            |

## Group 1 acceptance criteria

* Any `productId`, `relationship`, `kind`, `source`, `target`, or `residualIslandIds` production payload fails contract tests.
* `ProcessTsExtractorWorker` only sends intended TS/JS files to the worker.
* `ArtifactCompilerClient` no longer carries independent DTO truth for production graph/import/patch APIs.
* Contract fixture tests run in both Java and TS CI.

---

# Group 2 — Governed Source Acquisition, Snapshot, and Inventory

## Objective current status

**Current status: `PARTIALLY_IMPLEMENTED`, with P0 persistence/schema drift**

### What is good now

Java owns the source provider interface. `SourceProvider` defines `providerId`, `canHandle`, `resolve`, capabilities, and scope context. 

Credential resolution now exists. `SourceCredentialResolver` validates tenant/workspace/project scope and has both environment-backed and repository-backed patterns, while `SourceCredentialRepository` defines governed credential binding lookup.  

GitHub provider now uses `SourceCredentialResolver`, resolves commit SHA, sorts tree entries deterministically, materializes blobs, computes deterministic snapshot ID, and uses content hashes. 

GitLab provider now uses URL encoding, paginated tree loading, credential resolver, deterministic sorting, file-size limits, and content hashing. 

Java has a canonical `RepositoryInventoryScanner` with stable sorted walk, skip reasons, binary/vendor/generated/large-file detection, include/exclude rules, package boundary detection, and checksum computation. 

`RepositorySnapshotRepository` exists and intends to persist immutable snapshots and files with source locator JSON. 

### Deep production blockers

#### P0 — Repository snapshot persistence schema drift

`RepositorySnapshotRepository.saveSnapshot()` inserts into `repository_snapshots(snapshot_id, provider, repo_id, commit_sha, materialized_root, checksum, content_hash, created_at, tenant_id, workspace_id, project_id, created_by, diagnostics_json, source_locator_json)` and into `repository_snapshot_files(snapshot_id, relative_path, absolute_path, size_bytes, last_modified_at, content_checksum, file_type)`. 

But `V15__create_repository_snapshots.sql` creates `repository_snapshots(id, tenant_id, workspace_id, project_id, provider, repo_id, commit_sha, branch, local_root_path, content_checksum, diagnostics_json, created_at)` and `repository_snapshot_files(snapshot_id REFERENCES repository_snapshots(id), relative_path, absolute_path, size_bytes, last_modified_at, materialized, created_at)`. 

`V21__add_snapshot_source_locator.sql` adds `source_locator_json`, `checksum`, `created_by`, and file checksum/type columns, but it does not add `snapshot_id` to `repository_snapshots`, does not add `materialized_root`, and does not rename `local_root_path`. 

This means the repository code and migrations are likely incompatible in a database that applied V15 before V17/V21.

#### P0 — Duplicate snapshot table creation/migration ambiguity

`V17__repository_snapshots_and_inventory.sql` also creates `repository_snapshots` if not exists, but with columns `snapshot_id`, `repository_id`, `content_hash`, `materialized_root`, `file_count`, and `total_bytes`. Because V15 already creates the table, V17 will not repair the old shape. 

This is a production migration hazard.

#### P1 — GitHub provider claims archive fallback but fails closed

`GitHubSourceProvider` documentation says archive fallback was added, but when GitHub tree is truncated it returns an `UnsupportedOperationException` saying archive fallback is disabled until byte-stream integration exists. 

This is not wrong behavior because failing closed is safe, but the documentation/capability claim is misleading.

#### P1 — Bounded concurrency claim is misleading

Both GitHub and GitLab use a semaphore, but the fetched code performs blob/file requests in a sequential loop. The semaphore does not create parallelism by itself.  

This matters for large repositories and for accurately reporting performance capabilities.

#### P1 — Local-folder trusted root is not configurable

`LocalFolderSourceProvider` derives workspace root from `System.getProperty("user.dir")` and rejects candidates outside that root. This is safe as a default, but not production-flexible for containerized workers, mounted repositories, or controlled import roots. 

#### P1 — Inventory scanner still has memory and correctness gaps

`RepositoryInventoryScanner` documents `.gitignore` as simplified and says production should use a library; it also computes file checksums by `Files.readAllBytes(file)`, which is not suitable for large files despite size checks. 

## Group 2 production objective

By the end of Group 2:

* Snapshot persistence works against migrations from a clean DB and an upgraded DB.
* Source providers produce deterministic snapshots and canonical inventory.
* Provider capability claims match real behavior.
* Credentials are governed and secret-safe.
* Inventory scanning is stable, bounded, and sufficiently `.gitignore` correct.
* Local folder imports use configured allowed roots.
* Snapshot and source file references become durable foundation objects for Group 3.

## Group 2 file-by-file implementation plan

| Priority | File path                                                                                                             | Action                  | Exact change                                                                                                                                                                                                                                                                                                             | Tests                                                                            |
| -------: | --------------------------------------------------------------------------------------------------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------- |
|       P0 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V15__create_repository_snapshots.sql`             | CONSOLIDATE/FIX-FORWARD | Stop having incompatible old snapshot table shape. Add forward migration that renames/maps `id → snapshot_id`, `local_root_path → materialized_root`, `content_checksum → checksum`, and ensures `content_hash`, `created_by`, `source_locator_json` exist. Do not rely on `CREATE TABLE IF NOT EXISTS` to repair shape. | Migration test from empty DB and from V15-shaped DB.                             |
|       P0 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V17__repository_snapshots_and_inventory.sql`      | MODIFY                  | Remove competing `CREATE TABLE repository_snapshots` shape or convert into additive migration only. Ensure `source_file_refs` and `repository_inventory` reference the final canonical `repository_snapshots(snapshot_id)`.                                                                                              | Migration compatibility test.                                                    |
|       P0 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V21__add_snapshot_source_locator.sql`             | MODIFY                  | Add missing columns from repository code if not present: `snapshot_id`, `materialized_root`, `content_hash`; migrate data safely from old columns.                                                                                                                                                                       | Flyway migration test.                                                           |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/RepositorySnapshotRepository.java`        | MODIFY                  | Align SQL to final migration shape. Remove unscoped `findById(String snapshotId)` or mark internal only; use scoped lookup by default. Add save/load tests for source locator and file metadata.                                                                                                                         | `RepositorySnapshotRepositoryMigrationShapeTest.java`                            |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java` | MODIFY                  | Wrap `inventoryScanner.scanRepository(...)` exceptions into `Promise.ofException`. Persist inventory/skipped-file details, not just counts. Do not ignore scanner result file set when routing.                                                                                                                          | `ArtifactCompileJobServiceInventoryFailureTest.java`                             |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitHubSourceProvider.java`        | MODIFY                  | Make capability output honest: either implement archive fallback or return `supportsArchiveFallback=false`. Add real retry/backoff and rate-limit handling. Replace sequential blob loop with bounded async fetch or remove concurrency claim.                                                                           | GitHub truncated-tree/fail-closed test; capability test.                         |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitLabSourceProvider.java`        | MODIFY                  | Replace sequential file fetch loop with bounded async fetch or remove concurrency claim. Add tests for nested file path encoding, `%2F` project encoding, branch names with slashes, empty repo behavior.                                                                                                                | GitLab pagination/encoding tests.                                                |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/LocalFolderSourceProvider.java`   | MODIFY                  | Inject configured allowed import roots instead of using `user.dir`. Add capability detail listing configured-root behavior.                                                                                                                                                                                              | Allowed-root/path traversal tests.                                               |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/RepositoryInventoryScanner.java`  | MODIFY                  | Use streaming SHA-256; replace simplified gitignore with tested library or complete matcher; store skip details including matched pattern and file size; add total repo byte limits.                                                                                                                                     | Golden inventory tests for `.gitignore`, generated, binary, vendor, large files. |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceCredentialResolver.java`    | MODIFY                  | Ensure env-backed resolver is dev-only or feature-flagged; production should use `governed(...)`. Avoid logging tenant/workspace/project IDs in security exceptions if considered sensitive.                                                                                                                             | Credential scope + redaction tests.                                              |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceCredentialRepository.java`  | ADD IMPLEMENTATION      | Add JDBC-backed implementation if production needs persisted bindings. Store only secret key refs, never secrets.                                                                                                                                                                                                        | Credential repository integration test.                                          |

## Group 2 acceptance criteria

* Clean DB migration succeeds.
* Upgrade from existing V15-shaped DB succeeds.
* `RepositorySnapshotRepository.saveSnapshot()` passes against real migration schema.
* GitHub/GitLab capability responses accurately describe implemented behavior.
* Local folder imports use explicit allowed roots.
* Inventory scanning is deterministic and streaming-safe.
* Import compile job persists snapshot + inventory + skipped-file details before extraction.

---

# Group 3 — Artifact Graph, Residual Islands, and Semantic Model Fidelity

## Objective current status

**Current status: `PARTIALLY_IMPLEMENTED`, significantly improved, but still not fully production-safe**

### What is good now

`ArtifactGraphController.ingest()` now builds a scoped request from principal/header-derived tenant/workspace/project context, validates with `ArtifactGraphValidator`, validates residual references, and then calls `ArtifactGraphService.ingestGraph`. 

`ArtifactGraphValidator` is now centralized and validates scope, node structure, edge targets, unresolved edge structure, resolution records, residual payload completeness, source location, confidence, provenance, extractor metadata, source refs, symbol refs, and residual references. 

`ArtifactGraphServiceImpl` maps typed unresolved edge DTOs, resolution records, and full residual islands. It rejects residuals missing `originalSource`, `checksum`, or `rawFragmentRef` before mapping to repository records. 

`ResidualIslandDto` now carries original source, structured source location, source span, checksum, raw fragment ref, reason, confidence, review requirement, risk score, metadata, scope, and snapshot. 

`SemanticModelDto` is rich: source location, properties, dependencies, confidence, review flags, security/privacy flags, graph node IDs, residual island IDs, sourceRef, symbolRef, extractor metadata, model version, synthetic reason, provenance, snapshot, and scope. 

`SemanticModelRepository` exists and persists semantic models with tenant/workspace/project/snapshot scope. It stores governance metadata in reserved `__` properties. 

### Deep production blockers

#### P0 — Validator may reject valid current TS worker output because provenance naming is inconsistent

The validator only accepts provenance values `exact`, `inferred`, `synthesized`, `manual`, and `assumed`. 

But `ProcessTsExtractorWorker.mapSemanticModels()` defaults semantic model provenance to `"ts-extractor"`; this is for semantic models, not graph nodes, but it shows a broader terminology drift. 

Also `ts-extractor-worker.ts` maps graph node provenance directly from TypeScript graph nodes. If extractors produce non-canonical provenance tokens, Java ingest will reject. 

This is good strictness, but it requires extractor normalization tests.

#### P0 — Source refs and extractor metadata are now mandatory but TS worker mapping may not populate them correctly

`ArtifactGraphValidator` errors when graph nodes lack `provenance`, `extractorId`, `extractorVersion`, and `sourceRef`. 

`ts-extractor-worker.ts.toWorkerNode()` returns `{ ...node, type: node.kind, id, name, properties, tags }`, so it relies on the existing `GraphNode` object to already carry extractor metadata, sourceRef, provenance, confidence, privacy flags, etc. 

That may be correct if all extractors always populate those fields, but the production guarantee must be enforced with extractor contract tests.

#### P0 — Semantic model repository schema is unverified

`SemanticModelRepository` inserts into `semantic_models`, but I did not find a migration result for `CREATE TABLE IF NOT EXISTS semantic_models` during the search. The repository may fail at runtime if migrations are incomplete. 

#### P1 — Semantic model persistence compresses governance fields into `properties_json`

`SemanticModelRepository` stores `confidence`, review flags, security/privacy flags, graph node IDs, residual IDs, sourceRef, symbolRef, extractor metadata, model version, and synthetic reason inside reserved `__...` keys in `properties_json`. 

This is convenient, but not production-grade for queryability, indexing, validation, or governance. Key fields should be first-class columns or side tables.

#### P1 — Graph cache key omits workspace

`ArtifactGraphServiceImpl` cache key shown in the fetched file uses `cacheKey(scope.tenantId(), scope.projectId())`. Workspace is not visible in the cache key call. 

If `cacheKey` does not internally include workspace, there is a potential cross-workspace cache collision. This must be verified and fixed.

#### P1 — Compile orchestration saves semantic models before graph ingest succeeds

`ArtifactCompileJobService.compile()` merges extraction results, saves semantic models, and then ingests the graph. If graph ingest fails, semantic model rows may already exist for a failed graph ingest unless handled transactionally elsewhere. 

This breaks graph/model consistency.

## Group 3 production objective

By the end of Group 3:

* Graph ingest is strict and deterministic.
* All graph nodes have provenance, extractor metadata, source refs, and valid confidence.
* All resolved edges point to known graph node IDs.
* All unresolved edges remain unresolved until resolved through a symbol/reference index.
* Residual islands preserve source faithfully.
* Semantic models are persisted only after graph ingest succeeds, or both are committed in a coherent unit.
* Semantic models expose governance/provenance fields as queryable schema, not hidden reserved JSON fields.
* Workspace scope is included in all cache, query, persistence, and validation paths.

## Group 3 file-by-file implementation plan

| Priority | File path                                                                                                             | Action         | Exact change                                                                                                                                                                                                                                                                                                                                                                                                          | Tests                                                             |
| -------: | --------------------------------------------------------------------------------------------------------------------- | -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SemanticModelRepository.java`             | VERIFY/MODIFY  | Add or verify migration for `semantic_models`. If missing, add it. Align table columns with repository insert fields.                                                                                                                                                                                                                                                                                                 | `SemanticModelRepositoryMigrationShapeTest.java`                  |
|       P0 | `products/yappc/core/yappc-services/src/main/resources/db/migration/V___semantic_models.sql`                          | ADD if missing | Create `semantic_models` with first-class columns for id, element_id, element_type, name, file_path, source_location_json, confidence, review_required, review_reason, security_flags_json, privacy_flags_json, graph_node_ids_json, residual_island_ids_json, source_ref, symbol_ref, extractor_id, extractor_version, synthetic_reason, provenance, extracted_at, snapshot_id, tenant_id, workspace_id, project_id. | Flyway migration test.                                            |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/extractors/**`                                              | MODIFY         | Ensure every extractor emits canonical graph node fields: `id`, `kind`, `label`, `sourceRef`, `symbolRef`, `sourceLocation`, `extractorId`, `extractorVersion`, `confidence`, `provenance`, `privacySecurityFlags`, `residualFragmentIds`, metadata.                                                                                                                                                                  | Extractor contract tests per extractor.                           |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`                              | MODIFY         | Add final worker-side normalization that fails if any outgoing node lacks fields required by `ArtifactGraphValidator`. Do not rely on spread-only `...node`.                                                                                                                                                                                                                                                          | Worker output validator test.                                     |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ProcessTsExtractorWorker.java`  | MODIFY         | Add provenance normalization/validation for semantic models separately from graph nodes. Do not default semantic model provenance to `"ts-extractor"` if Java schema expects canonical provenance classes.                                                                                                                                                                                                            | Semantic model worker mapping test.                               |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java` | MODIFY         | Change order or transaction boundary: graph ingest and semantic model persistence must succeed/fail coherently. Prefer graph ingest first, then semantic model persistence with graph version ID, or transactional orchestration.                                                                                                                                                                                     | Compile failure consistency test.                                 |
|       P0 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphValidator.java`    | MODIFY         | Add semantic model validator or split `ArtifactGraphValidator` and `SemanticModelValidator`. Enforce graph node IDs referenced by semantic models exist in graph ingest result.                                                                                                                                                                                                                                       | `SemanticModelValidatorTest.java`                                 |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`  | MODIFY         | Include workspace ID in cache key. Audit all cache gets/puts/invalidations.                                                                                                                                                                                                                                                                                                                                           | Cross-workspace cache isolation test.                             |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/ArtifactGraphRepository.java`             | MODIFY         | Ensure residual island table stores `original_source`, structured `source_location_json`, `source_span`, `checksum`, `raw_fragment_ref`, `reason`, `confidence`, `review_required`, `risk_score`, metadata, scope, snapshot.                                                                                                                                                                                          | Residual persistence round-trip test.                             |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SemanticModelRepository.java`             | MODIFY         | Promote governance fields out of `properties_json` reserved keys into columns or normalized relation tables. Keep arbitrary model properties in `properties_json` only.                                                                                                                                                                                                                                               | Query/index tests for confidence, reviewRequired, security flags. |
|       P1 | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`                 | MODIFY         | Replace string-concatenated JSON error building with safe JSON serialization for validation errors.                                                                                                                                                                                                                                                                                                                   | Validation error escaping test.                                   |

## Group 3 acceptance criteria

* Graph ingest rejects missing provenance/sourceRef/extractor metadata consistently.
* TS extractor worker output always passes Java validator for golden fixtures.
* Residual islands round-trip with original source, exact location, checksum, raw fragment, risk, and review flag.
* Semantic models cannot persist without a valid snapshot and matching graph node references.
* Failed graph ingest cannot leave orphaned semantic model rows.
* Cache and queries are isolated by tenant + workspace + project.

---

# Recommended independent execution plan

## Track A — Group 1 contract hardening

Start this first. It unblocks the other groups.

**First PR:**

1. Add shared contract fixtures.
2. Add Java and TS contract tests.
3. Make `ProcessTsExtractorWorker` use `tsFiles`.
4. Remove/feature-flag legacy request compatibility paths.

## Track B — Group 2 snapshot/inventory persistence hardening

Can run in parallel with Track A, but must not merge until migration tests are green.

**First PR:**

1. Fix `repository_snapshots` migration shape.
2. Add migration tests for clean DB and upgraded V15 DB.
3. Align `RepositorySnapshotRepository` SQL to final schema.
4. Add GitHub/GitLab/local/archive provider capability accuracy tests.

## Track C — Group 3 graph/residual/model fidelity hardening

Can run in parallel using fixtures.

**First PR:**

1. Verify/add `semantic_models` migration.
2. Add semantic model repository migration-shape test.
3. Add extractor output contract tests.
4. Fix compile orchestration consistency: graph + semantic model persistence must be coherent.
5. Add cache isolation test.

---

# Highest-priority P0 TODOs

```markdown
- [ ] [P0] Fix repository snapshot DB schema drift.
  - Files:
    - products/yappc/core/yappc-services/src/main/resources/db/migration/V15__create_repository_snapshots.sql
    - products/yappc/core/yappc-services/src/main/resources/db/migration/V17__repository_snapshots_and_inventory.sql
    - products/yappc/core/yappc-services/src/main/resources/db/migration/V21__add_snapshot_source_locator.sql
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/RepositorySnapshotRepository.java
  - Done when:
    - Clean DB and upgraded V15-shaped DB both match RepositorySnapshotRepository SQL.
  - Test:
    - RepositorySnapshotRepositoryMigrationShapeTest.java
```

```markdown
- [ ] [P0] Make TS worker file routing real.
  - Files:
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ProcessTsExtractorWorker.java
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java
  - Done when:
    - Java sends only filtered TS/JS files to the TS worker, and Java files are not redundantly sent.
  - Test:
    - ProcessTsExtractorWorkerFilteredFilesTest.java
```

```markdown
- [ ] [P0] Verify/add semantic model migration.
  - Files:
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SemanticModelRepository.java
    - products/yappc/core/yappc-services/src/main/resources/db/migration/V___semantic_models.sql
  - Done when:
    - SemanticModelRepository works against migrated DB and all governance fields are persisted.
  - Test:
    - SemanticModelRepositoryMigrationShapeTest.java
```

```markdown
- [ ] [P0] Enforce extractor output contract.
  - Files:
    - products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts
    - products/yappc/frontend/libs/yappc-artifact-compiler/src/extractors/**
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphValidator.java
  - Done when:
    - Every emitted node has sourceRef, symbolRef, provenance, extractorId, extractorVersion, confidence, source location or synthetic reason.
  - Test:
    - ExtractorContractGoldenFixtureTest.ts
    - ArtifactGraphValidatorGoldenFixtureTest.java
```

```markdown
- [ ] [P0] Make graph and semantic model persistence coherent.
  - Files:
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SemanticModelRepository.java
    - products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java
  - Done when:
    - A failed graph ingest cannot leave semantic model rows for that failed compile.
  - Test:
    - ArtifactCompileJobServiceGraphModelConsistencyTest.java
```

---

# Bottom line

For Groups 1–3, the implementation is no longer just “stub-level.” It has real architecture and meaningful hardening already. The next step is not broad new feature work. The next step is **production correctness hardening**:

1. Fix DB migration/repository shape mismatches.
2. Enforce generated/shared contracts with fixtures.
3. Make provider capability claims match actual behavior.
4. Make TS worker file routing real.
5. Guarantee semantic model persistence is schema-backed and transactionally consistent with graph ingest.
6. Add golden fixture tests that prove the compiler foundation is deterministic, scoped, source-faithful, and residual-safe.
