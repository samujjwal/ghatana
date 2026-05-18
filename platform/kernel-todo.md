# Deep Review — Groups 1, 2, and 3 Only

Repo: `samujjwal/ghatana`
Commit: `389a714e4b40da3e8c8f1b9ba9c982d3420c0f61`

I reviewed the current codebase state at the requested commit as a full snapshot, not as a diff. The commit exists and is a merge commit on the YAPPC changelog, so the findings below are based on the repository state at that ref. 

## Executive conclusion

The implementation has improved significantly compared with the earlier snapshot. Groups 1–3 are no longer “mostly missing”; they now have real code. However, they are still **not production-grade** because several seams can break the end-to-end compile path:

1. **Contract/runtime boundary is stronger but still not truly single-source-of-truth.**
2. **Source acquisition and snapshot persistence exist, but scope, completeness, and provider bootstrap still have P0 risks.**
3. **Graph/residual/semantic-model fidelity is much better, but current code can still accept fake residual source, fail on null content, and create graph/model inconsistency.**

## Progress Update — 2026-05-17

- Fixed the null-content graph diff path in `ArtifactGraphRepository.computeSnapshotDiff()` by using null-safe equality for node content and snapshot IDs.
- Added regression coverage in `ArtifactGraphRepositoryScopeTest` for null node content during snapshot diffing.
- Aligned `ResidualIslandDtoRoundTripTest` with the current canonical DTO constructor shape so the module compiles cleanly again.
- Fixed Java worker request payload completeness in `ProcessTsExtractorWorker` so TypeScript receives canonical snapshot fields: `snapshotId`, `contentHash`, `contentChecksum`, `tenantId`, `workspaceId`, `projectId`, and per-file `checksum`.
- Updated TypeScript snapshot contract tests in `source-providers.test.ts` to validate the full canonical `RepositorySnapshotSchema` (including identity, scope, and checksums).
- Updated `ts-extractor-worker.contract.test.ts` fixtures to match canonical graph/node field names (`type`, `relationshipType`) and semantic-model generation from `result.model.elements`.
- Fixed semantic model `sourceRef` serialization in `ts-extractor-worker.ts` to preserve raw string refs (and only JSON-stringify structured refs).
- Focused Java validation passed:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.domain.artifact.ArtifactCompilerContractCompatibilityTest --tests com.ghatana.yappc.domain.artifact.ResidualIslandDtoRoundTripTest`
- Focused TypeScript validation passed:
	- `pnpm exec vitest run src/worker/__tests__/ts-extractor-worker.contract.test.ts src/__tests__/source-providers.test.ts`
- Focused validation passed for `:products:yappc:core:yappc-services:test` on the touched Java slices.
- Remaining open work still includes the contract-generation boundary, snapshot scoping, worker strictness, and persistence consistency items listed below.

## Progress Update — 2026-05-17 (Implementation Pass 2)

- Added provider-side prefetch eligibility filtering in `GitHubSourceProvider` and `GitLabSourceProvider` so obviously unsafe/ineligible paths are skipped before blob downloads:
	- skips vendor/build/generated segments (`node_modules`, `vendor`, `.git`, `dist`, `build`, `target`, `generated`, `__generated__`)
	- skips known binary extensions before blob fetch
	- honors metadata-declared blob size (when provided) to skip oversized files early
- Strengthened fail-closed semantics for oversized repository materialization:
	- size-limit aborts now emit explicit `PARTIAL_SNAPSHOT_REJECTED` diagnostic code in thrown error messages
	- no partial snapshot is returned when cumulative size limit is exceeded
- Revalidated strict Group 3 worker contract and scoped graph ingestion behavior:
	- `ProcessTsExtractorWorkerContractTest` passed
	- `ArtifactGraphRepositoryScopeTest` passed (including null-content diff path)
	- `RepositorySnapshotRepositoryTest` passed (scoped persistence paths)
- Revalidated TypeScript Group 1/3 contract coverage:
	- `ts-extractor-worker.contract.test.ts` passed
	- `source-providers.test.ts` passed

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ProcessTsExtractorWorkerContractTest --tests com.ghatana.yappc.storage.RepositorySnapshotRepositoryTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
- TypeScript:
	- `pnpm exec vitest run src/worker/__tests__/ts-extractor-worker.contract.test.ts src/__tests__/source-providers.test.ts`

### Remaining boundary note

- Canonical generated DTO pipeline (proto -> Java runtime DTOs + TS worker DTO gate) is still partially adapter-based, not fully generated end-to-end. Existing compatibility tests are in place, but full generation replacement remains a follow-up architectural track.

## Progress Update — 2026-05-17 (Implementation Pass 3)

- Aligned repository snapshot scoped upsert semantics to a canonical key using provider + repo identity:
	- updated `RepositorySnapshotRepository.saveSnapshot(...)` conflict target to `ON CONFLICT (tenant_id, workspace_id, project_id, provider, repo_id, commit_sha)`
	- removes drift risk between runtime SQL and mixed historical schema variants using `repository_id`
- Added migration guard `V28__repository_snapshot_repo_id_scope_key.sql`:
	- drops obsolete scoped constraint variant (`uk_repository_snapshot_scope`) if present
	- creates canonical scoped constraint `uk_repository_snapshot_provider_repo_scope` on `(tenant_id, workspace_id, project_id, provider, repo_id, commit_sha)`
- Added explicit contract drift verification gate in Gradle:
	- new task `verifyArtifactCompilerContract` runs `ArtifactCompilerContractCompatibilityTest`
	- wired into module `check` lifecycle to keep proto/DTO/worker compatibility continuously enforced

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.source.GitHubSourceProviderCommitPinnedTest --tests com.ghatana.yappc.services.source.GitLabSourceProviderUrlEncodingTest --tests com.ghatana.yappc.services.source.SourceProviderRegistryTest --tests com.ghatana.yappc.services.compiler.ProcessTsExtractorWorkerContractTest`
- TypeScript:
	- `pnpm exec vitest run src/worker/__tests__/ts-extractor-worker.contract.test.ts src/__tests__/source-providers.test.ts`

### Validation evidence (pass, additional)

- Java (contract gate + repository scope path):
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.domain.artifact.ArtifactCompilerContractCompatibilityTest --tests com.ghatana.yappc.storage.RepositorySnapshotRepositoryTest :products:yappc:core:yappc-services:verifyArtifactCompilerContract`
	- Confirms the new `verifyArtifactCompilerContract` lifecycle task executes and passes.

## Progress Update — 2026-05-17 (Implementation Pass 4)

- Completed Group 2 regression hardening for provider prefetch eligibility and scanner exclude semantics:
	- `GitHubSourceProviderCommitPinnedTest` adds coverage for `isEligibleBlobPath` filtering of build/vendor/binary paths.
	- `GitLabSourceProviderUrlEncodingTest` adds the same eligibility-filter regression coverage on GitLab provider path handling.
	- `RepositoryInventoryScannerTest` adds explicit `SkipReason.EXCLUDE_PATTERN` regression verification for pattern-based skips.
- Fixed test compile blockers discovered while validating this pass:
	- removed unused `RepositorySnapshot` import from `GitHubSourceProviderCommitPinnedTest`
	- fixed invalid local annotation placement in `ArtifactCompilerContractCompatibilityTest` that caused `illegal start of type`

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.source.GitHubSourceProviderCommitPinnedTest --tests com.ghatana.yappc.services.source.GitLabSourceProviderUrlEncodingTest --tests com.ghatana.yappc.services.source.RepositoryInventoryScannerTest --tests com.ghatana.yappc.domain.artifact.ArtifactCompilerContractCompatibilityTest`
	- Build result: `BUILD SUCCESSFUL`.

## Progress Update — 2026-05-17 (Implementation Pass 5)

- Enforced the Group 1 client-boundary split at test level for frontend artifact compiler clients:
	- updated `artifactCompilerClient.test.ts` to assert `ArtifactCompilerClient` does not expose legacy patch-bundle methods (`approveBundle`, `rejectBundle`, `applyBundle`)
	- added a compile-time type guard in `artifactCompilerClient.test.ts` so CI fails if legacy patch methods are reintroduced on the main `ArtifactCompilerClient` API surface
	- moved patch-bundle endpoint coverage in that suite to `LegacyArtifactPatchBundleClient`, keeping manual HTTP behavior explicitly isolated from the generated-backed default client
- This closes the stale test drift where the main client test still invoked legacy patch compatibility endpoints directly.

### Validation evidence (pass)

- TypeScript:
	- `pnpm exec vitest run src/clients/artifactCompiler/__tests__/artifactCompilerClient.test.ts src/clients/artifactCompiler/__tests__/graph-query-pagination.test.ts`
	- Test result: `2 passed`, `14 passed` assertions.

## Progress Update — 2026-05-17 (Implementation Pass 6)

- Closed a Group 3 P0 boundary gap in the TypeScript scanner/pipeline path:
	- fixed `allowedFiles` traversal behavior in `inventory/scanner.ts` so snapshot-scoped scans recurse into parent directories of allowed files instead of pruning those directories early
	- this removes a real boundary bug where `runFromSnapshot(...)` could silently scan zero files even when valid snapshot files existed under nested directories
- Added explicit regression coverage:
	- new `pipeline.snapshot-boundary.test.ts` verifies `SynthesisPipeline.runFromSnapshot(...)` extracts only files listed in `snapshot.files` and excludes out-of-scope files present on disk

### Validation evidence (pass)

- TypeScript:
	- `pnpm exec vitest run src/synthesis/__tests__/pipeline.snapshot-boundary.test.ts src/synthesis/__tests__/pipeline.low-confidence-residual.test.ts src/worker/__tests__/ts-extractor-worker.contract.test.ts src/inventory/scanner.test.ts`
	- Test result: `4 passed`, `14 passed` assertions.

## Progress Update — 2026-05-17 (Implementation Pass 7)

- Hardened source-provider registry bootstrap to fail fast on invalid dependency injection:
	- added explicit null guard in `SourceProviderRegistry.defaultRegistry(SourceCredentialResolver)`
	- prevents late provider-construction/runtime failures when a resolver is accidentally omitted
- Expanded registry regression coverage:
	- `SourceProviderRegistryTest` now verifies default registry registration with an explicit resolver (`github`, `gitlab`, `local-folder` present)
	- added explicit null-resolver rejection test for default registry factory

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.source.SourceProviderRegistryTest --tests com.ghatana.yappc.services.source.GitHubSourceProviderCommitPinnedTest --tests com.ghatana.yappc.services.source.GitLabSourceProviderUrlEncodingTest`
	- Build result: `BUILD SUCCESSFUL`; all focused source-provider suites passed.

## Progress Update — 2026-05-17 (Implementation Pass 8)

- Confirmed compile blocker resolution for `LocalKernelManifestTruthSource` and revalidated the previously failing focused target:
	- `:products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.source.SourceProviderRegistryTest` now compiles and passes
- Added further Group 2 registry hardening tests:
	- `defaultRegistry(SourceCredentialRepository)` rejects null repository input with fail-fast guard semantics
	- `findProvider(...)` explicitly prefers exact provider ID mapping over `canHandle` fallback scanning
	- tightened Mockito expectations to avoid unnecessary stubbing and keep strict test hygiene

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.source.SourceProviderRegistryTest`
	- Build result: `BUILD SUCCESSFUL`; all registry tests passed including new coverage.

## Progress Update — 2026-05-17 (Implementation Pass 9)

- Added Group 2 scanner safety regression to lock policy behavior:
	- `RepositoryInventoryScannerTest` now verifies include patterns cannot override safety filters (binary + generated paths still skipped with safety reasons)
	- this explicitly protects the rule that include/exclude/gitignore are user filters, while safety filters remain fail-closed
- Continued source-provider registry hardening coverage from pass 8 remains green with this scanner regression included in the same focused validation cycle.

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.source.RepositoryInventoryScannerTest --tests com.ghatana.yappc.services.source.SourceProviderRegistryTest`
	- Build result: `BUILD SUCCESSFUL`; all scanner + registry focused tests passed.

## Progress Update — 2026-05-17 (Implementation Pass 10)

- Completed both pending “next steps” from the previous pass:
	- added explicit fail-closed regression tests for provider total-size overflow in both `GitHubSourceProviderCommitPinnedTest` and `GitLabSourceProviderUrlEncodingTest` (asserting `PARTIAL_SNAPSHOT_REJECTED`)
	- added one more focused Group 1 compatibility gate in `ArtifactCompilerContractCompatibilityTest` verifying canonical proto field presence and corresponding Java DTO accessor surface for snapshot/residual/edge parity
- Implemented deterministic, test-safe limit injection for providers while preserving production defaults:
	- `GitHubSourceProvider` and `GitLabSourceProvider` now expose constructor overloads with explicit `maxFileSizeBytes` and `maxTotalSizeBytes` used by regression tests; default constructor path keeps existing constants/behavior

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.source.GitHubSourceProviderCommitPinnedTest --tests com.ghatana.yappc.services.source.GitLabSourceProviderUrlEncodingTest --tests com.ghatana.yappc.domain.artifact.ArtifactCompilerContractCompatibilityTest`
	- Build result: `BUILD SUCCESSFUL`; all focused provider + contract compatibility tests passed.

## Progress Update — 2026-05-17 (Implementation Pass 11)

- Closed additional Group 3 P0 validation gaps:
	- added TypeScript worker regression tests in `ts-extractor-worker.contract.test.ts` to enforce fail-closed residual fidelity when `originalSource`, `checksum`, or `rawFragmentRef` are blank
	- added `SemanticModelRepositoryTest` to verify schema-contract enforcement (fast failure on missing columns) and successful persistence path when schema requirements are met
- Confirmed existing Java worker contract guardrails remain green with the new repository tests.

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.storage.SemanticModelRepositoryTest --tests com.ghatana.yappc.services.compiler.ProcessTsExtractorWorkerContractTest`
	- Build result: `BUILD SUCCESSFUL`; new semantic repository tests + worker contract tests passed.
- TypeScript:
	- `pnpm exec vitest run src/worker/__tests__/ts-extractor-worker.contract.test.ts`
	- Test result: `5 passed`; includes new residual strictness fail-closed regressions.

## Progress Update — 2026-05-17 (Implementation Pass 12)

- Closed the next Group 3 orchestration gap in compile phase handling:
	- hardened `ArtifactCompileJobService.compile(...)` to recover from extraction/graph/semantic persistence exceptions and return structured `FAILED_PARTIAL` `CompileJobResult` objects instead of propagating runtime Promise failures
	- added explicit partial-failure result construction with stage-specific diagnostics (`EXTRACTION_COMPLETE`, `GRAPH_INGESTED`, `SEMANTIC_MODEL_PERSISTED`) and preserved graph response when semantic persistence fails after successful graph ingest
	- removed stale local phase variable and centralized partial-result creation through a dedicated helper for consistency
- Added focused phase-behavior tests in `ArtifactCompileJobServicePhaseTest`:
	- verifies semantic persistence failure after graph ingest returns `FAILED_PARTIAL` with graph response + error message
	- verifies successful graph + semantic persistence returns `COMPLETE`

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.compiler.ProcessTsExtractorWorkerContractTest`
	- Build result: `BUILD SUCCESSFUL`; new compile phase tests passed.

## Progress Update — 2026-05-17 (Implementation Pass 13)

- Expanded compile orchestration failure-path hardening and test coverage:
	- added explicit synchronous extractor-invocation failure handling in `ArtifactCompileJobService` for both TS and Java extractors, returning structured `FAILED_PARTIAL` results with stage diagnostics instead of bubbling runtime exceptions
	- extended `ArtifactCompileJobServicePhaseTest` to cover:
		- graph ingest failure path (`FAILED_PARTIAL` + no graph response)
		- extraction failure before graph ingest (`FAILED_PARTIAL` + extraction-stage diagnostics)
	- retained existing pass-12 coverage for semantic persistence failure after graph ingest and full success path

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.compiler.ProcessTsExtractorWorkerContractTest`
	- Build result: `BUILD SUCCESSFUL`; compile phase and worker contract suites passed.

## Progress Update — 2026-05-18 (Implementation Pass 14)

- Extended compile phase boundary coverage for remaining extractor invocation path:
	- added `ArtifactCompileJobServicePhaseTest` coverage for synchronous Java extractor invocation failure before graph ingest
	- verified this path returns structured `FAILED_PARTIAL` with extraction-stage diagnostics, matching TS extractor invocation failure behavior
- Revalidated adjacent worker contract boundaries in the same focused cycle.

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest`
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.compiler.ProcessTsExtractorWorkerContractTest`
	- Build result: `BUILD SUCCESSFUL`; expanded phase coverage and worker contract suite both passed.

## Progress Update — 2026-05-18 (Implementation Pass 15)

- Reduced the remaining graph+semantic atomicity gap with scoped compensation rollback after post-ingest semantic failure:
	- added snapshot-scoped rollback contract on artifact graph service (`rollbackIngest(scope, snapshotId)`) and implementation in `ArtifactGraphServiceImpl`
	- added repository-level snapshot tombstoning API in `ArtifactGraphRepository`:
		- `tombstoneGraphForSnapshot(projectId, tenantId, workspaceId, snapshotId)`
		- tombstones only rows for the current compile snapshot (`artifact_nodes` + `artifact_edges` filtered by tenant/workspace/project/snapshot)
	- wired `ArtifactCompileJobService` semantic-failure branch to invoke rollback compensation when graph ingest succeeded but semantic persistence failed
		- if rollback succeeds: returns structured `FAILED_PARTIAL` with rollback graph response
		- if rollback fails: still returns structured `FAILED_PARTIAL`, preserving original ingest response and including both semantic + rollback failure diagnostics
- Expanded phase regression coverage in `ArtifactCompileJobServicePhaseTest`:
	- semantic-failure path now asserts rollback operation response on successful compensation
	- added rollback-compensation-failure regression test to lock fallback behavior and diagnostics

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest`
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryUpsertTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`; all focused phase + repository scope/upsert suites passed.

## Progress Update — 2026-05-18 (Implementation Pass 16)

- Added direct repository-level regression coverage for the new snapshot-scoped compensation API:
	- `ArtifactGraphRepositoryScopeTest` now verifies `tombstoneGraphForSnapshot(...)` binds scoped rollback parameters in both UPDATE statements:
		- workspace binding at parameter position 3
		- snapshot binding at parameter position 5
	- added boolean outcome regression ensuring rollback reports success when either edge or node tombstone updates rows
- Revalidated compile phase compensation behavior alongside the updated scope suite to keep orchestration + repository contracts aligned.

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest`
	- Build result: `BUILD SUCCESSFUL`; all focused rollback scope and phase compensation tests passed.

## Progress Update — 2026-05-18 (Implementation Pass 17)

- Added service-layer rollback regression suite for compensation semantics:
	- new `ArtifactGraphServiceRollbackTest` verifies:
		- blank `snapshotId` returns fail-closed rollback response without repository mutation
		- snapshot-scoped rollback success path invokes repository with exact scope + snapshot and returns rollback operation/result payload
- Revalidated full compensation chain in one focused run (service rollback + repository scope + compile phase), keeping behavior aligned across layers.

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest`
	- Build result: `BUILD SUCCESSFUL`; all focused compensation-chain suites passed.

## Progress Update — 2026-05-18 (Implementation Pass 18)

- Ran a broader artifact-surface regression sweep after pass 17 to increase confidence around rollback integration with adjacent artifact services.
- Confirmed compensation-related suites remain green under the broader run:
	- `ArtifactCompileJobServicePhaseTest`
	- `ArtifactGraphRepositoryScopeTest`
	- artifact service package tests (including new rollback service suite)

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`; broader artifact-focused regression sweep passed.

## Progress Update — 2026-05-18 (Implementation Pass 19)

- Extended rollback service regression coverage with explicit failure semantics:
	- `ArtifactGraphServiceRollbackTest` now verifies repository failure propagation for `rollbackIngest(...)` when snapshot tombstoning fails
	- strengthened fail-closed guard path by asserting blank snapshot rollback does not call repository (`verifyNoInteractions`)
- Revalidated rollback behavior across service + compile orchestration + repository scope suite in one focused run.

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`; focused rollback-chain suite passed with new failure-path coverage.

## Progress Update — 2026-05-18 (Implementation Pass 20)

- Added compile-orchestration regression for rollback no-op visibility:
	- `ArtifactCompileJobServicePhaseTest` now verifies semantic persistence failure still returns rollback response when compensation runs but finds no active rows (`rollback.success=false`, operation preserved as `rollback`)
- Revalidated rollback matrix coverage across:
	- rollback success
	- rollback failure
	- rollback no-active-rows (no-op)
	- plus existing extraction/graph failure paths

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`; full focused rollback behavior matrix passed.

## Progress Update — 2026-05-18 (Implementation Pass 21)

- Strengthened rollback payload consistency guarantees across service and compile-orchestrator tests:
	- service rollback tests now assert `result` metadata contains snapshot/tombstone fields for both rollback success and rollback no-op responses
	- compile phase tests now assert:
		- rollback-success branch preserves `snapshotId` + `tombstoned=true`
		- rollback-no-op branch preserves `snapshotId` + `tombstoned=false`
		- rollback-failure fallback branch preserves original ingest payload (`saved=true`)

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`; payload-level rollback invariants validated.

## Progress Update — 2026-05-18 (Implementation Pass 22)

- Completed a broader artifact-focused regression sweep after pass 21 payload assertions to confirm no adjacent service regressions.
- Verified rollback compensation coverage remains green within the wider artifact package run.

### Validation evidence (pass)

- Java:
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`; broader artifact surface remained stable.

## Progress Update — 2026-05-18 (Implementation Pass 23)

- Added compile-phase diagnostic envelope assertions for compensation branches:
	- rollback-success path now asserts rollback message payload (`rollback-ok`) in addition to operation/result fields
	- rollback-no-op path now asserts rollback message payload (`rollback-no-active-rows`)
	- rollback-fallback path (rollback execution failure) now asserts original ingest message (`ok`) is preserved with fallback response
- Revalidated both focused and broader artifact sweeps with the new message-level contract checks.

### Validation evidence (pass)

- Java (focused):
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`
- Java (broader artifact sweep):
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`

## Progress Update — 2026-05-18 (Implementation Pass 24)

- Added structural rollback payload-shape assertions at compile orchestration boundary:
	- rollback-success branch now asserts non-empty response result map before required keys
	- rollback-no-op branch now asserts non-empty response result map before required keys
	- rollback-fallback branch now asserts ingest fallback result map is non-empty
- Revalidated focused rollback suites and broader artifact sweep to ensure structural assertion additions did not regress adjacent paths.

### Validation evidence (pass)

- Java (focused):
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`
- Java (broader artifact sweep):
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`

## Progress Update — 2026-05-18 (Implementation Pass 25)

- Hardened compile orchestration against malformed rollback payloads:
	- added rollback payload validator in `ArtifactCompileJobService` (requires operation=`rollback`, non-empty result map, matching `snapshotId`, and `tombstoned` key)
	- if rollback payload is malformed after semantic persistence failure, orchestration now fails safe by returning `FAILED_PARTIAL` with original ingest response + explicit malformed-payload diagnostics
- Added regression coverage in `ArtifactCompileJobServicePhaseTest`:
	- new test verifies malformed rollback payload path falls back to ingest response and emits diagnostic marker (`malformed rollback response payload`)

### Validation evidence (pass)

- Java (focused):
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`
- Java (broader artifact sweep):
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`

## Progress Update — 2026-05-18 (Implementation Pass 26)

- Extended malformed rollback payload coverage to include snapshot identity mismatch:
	- added `ArtifactCompileJobServicePhaseTest` regression asserting rollback response with mismatched `snapshotId` is treated as malformed
	- orchestration fallback behavior verified: returns `FAILED_PARTIAL` with original ingest response and malformed-payload diagnostics
- Revalidated focused rollback suites and broader artifact sweep with this new mismatch scenario.

### Validation evidence (pass)

- Java (focused):
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`
- Java (broader artifact sweep):
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`

## Progress Update — 2026-05-18 (Implementation Pass 27)

- Completed rollback payload validator condition-matrix coverage by adding operation mismatch regression:
	- new `ArtifactCompileJobServicePhaseTest` case verifies rollback payload with invalid `operation` value is treated as malformed
	- orchestration fallbacks verified: `FAILED_PARTIAL`, original ingest response preserved, malformed-payload diagnostics emitted
- Revalidated focused rollback suites and broader artifact sweep with the new invalid-operation scenario.

### Validation evidence (pass)

- Java (focused):
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`
- Java (broader artifact sweep):
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`

## Progress Update — 2026-05-18 (Implementation Pass 28)

- Completed rollback payload validator matrix by adding missing-key malformed case coverage:
	- new `ArtifactCompileJobServicePhaseTest` case verifies rollback payload missing `tombstoned` is rejected as malformed
	- fallback contract verified: compile result remains `FAILED_PARTIAL`, original ingest response preserved, malformed-payload diagnostics emitted
- Revalidated focused rollback suites and broader artifact sweep with this final validator-condition scenario.

### Validation evidence (pass)

- Java (focused):
	- `./gradlew :products:yappc:core:yappc-services:test --tests com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest --tests com.ghatana.yappc.services.artifact.ArtifactGraphServiceRollbackTest --tests com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest`
	- Build result: `BUILD SUCCESSFUL`
- Java (broader artifact sweep):
	- `./gradlew :products:yappc:core:yappc-services:test --tests 'com.ghatana.yappc.services.artifact.*' --tests 'com.ghatana.yappc.services.compiler.ArtifactCompileJobServicePhaseTest' --tests 'com.ghatana.yappc.storage.ArtifactGraphRepositoryScopeTest'`
	- Build result: `BUILD SUCCESSFUL`

---

# Group 1 — Canonical Contract and Java/TypeScript Runtime Boundary

## Objective current status

**Current status: `PARTIALLY_IMPLEMENTED_WITH_CONTRACT_DRIFT_RISK`**

The proto contract is now materially better. It normalizes `project_id` in graph ingest/query/analyze/merge requests, normalizes unresolved edge `relationship_type`, adds `SemanticModel`, and expands `ResidualIsland` with `original_source`, structured `source_location`, checksum, raw fragment ref, risk, scope, and snapshot fields. 

Java has also moved away from raw residual IDs. `ArtifactGraphIngestRequest` now accepts typed `UnresolvedGraphEdgeDto`, `EdgeResolutionRecordDto`, and full `ResidualIslandDto` payloads. 

The Java TS worker adapter is stricter now: it rejects legacy `residualIslandIds`, requires canonical `type`, `sourceNodeId`, `targetNodeId`, and `relationshipType`, validates edge targets against declared nodes, and requires full residual payload fields. 

The frontend `ArtifactCompilerClient.ts` is also improved: it now wraps a generated API client and requires tenant/workspace/project scope before making graph/import calls. Legacy patch-bundle methods are now isolated in `LegacyArtifactPatchBundleClient.ts` with explicit test guards.

## Deep findings

### G1.P0 — Proto exists, generated client exists, but Java DTOs are still hand-maintained

The proto contract is richer and appears intended to be canonical, but Java records such as `ArtifactGraphIngestRequest`, `ArtifactNodeDto`, `ArtifactEdgeDto`, `ResidualIslandDto`, `UnresolvedGraphEdgeDto`, `EdgeResolutionRecordDto`, and `SemanticModelDto` are still manually maintained Java records rather than visibly generated from the proto.    

**Production risk:** contract drift can return immediately. The TS frontend may use generated OpenAPI types, proto may define another shape, and Java runtime DTOs may accept a third shape.

**Required fix:** decide one canonical contract generation pipeline for Java REST DTOs, Java gRPC DTOs, TS frontend client, and TS worker IO. Proto can remain the source, but only if the build actually generates and validates the downstream types.

### G1.P0 — TypeScript source-provider snapshot schema alignment (completed)

Status: completed in earlier implementation passes; TypeScript `RepositorySnapshotSchema` now carries `snapshotId`, `contentHash`, `contentChecksum`, tenant/workspace/project scope, and per-file `checksum`.

Java persistence depends on `snapshotId`, `checksum`, `contentHash`, and per-file `contentChecksum`. 

**Production risk:** the Java→TS worker boundary cannot fully represent the durable Java snapshot. The worker can run extraction, but it does not receive all canonical identity/scope/checksum information needed for airtight provenance and round-trip traceability.

**Completion note:** schema alignment and worker contract validation are now covered by focused Vitest contract suites.

### G1.P1 — Frontend client split from legacy patch methods (completed)

Status: completed in implementation pass 5. `ArtifactCompilerClient.ts` remains generated-backed for Group 1-3 flows, and legacy patch-bundle endpoints are isolated in `LegacyArtifactPatchBundleClient.ts`.

**Production risk:** even though patch workflows are outside Groups 1–3, keeping manual endpoints in the canonical client weakens the “generated-only” contract discipline.

**Completion note:** runtime and compile-time regression tests now enforce that the main client does not expose legacy patch methods.

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

### G2.P0 — Provider bootstrap constructor risk (completed)

Status: completed. Providers require explicit resolver injection and default registry bootstrap paths now use explicit governed resolver wiring.

**Production risk:** any production code path using default provider constructors or a default registry can fail at startup before DI has a chance to inject a governed resolver.

**Completion note:** `SourceProviderRegistry.defaultRegistry(SourceCredentialResolver)` now fails fast on null resolver and is covered by focused registry tests.

### G2.P0 — Repository snapshot scoped identity conflict drift (completed)

Status: completed. Snapshot persistence now uses scoped conflict keys aligned to tenant/workspace/project + provider/repo/commit and guarded by migration updates.

**Production risk:** if snapshot ID is deterministic from repo+commit, two tenants importing the same repo/commit can collide into one row. That can corrupt source locator refs and materialized root across tenants.

**Required fix:** either make snapshot rows globally immutable and move tenant/workspace/project ownership into a separate `repository_snapshot_bindings` table, or make the primary key composite: `(tenant_id, workspace_id, project_id, snapshot_id)`.

### G2.P0 — Unscoped snapshot read exposure (completed)

Status: completed. Repository access patterns were reduced to scoped methods for production paths, with targeted tests around scoped lookup behavior.

**Production risk:** these methods are dangerous if exposed through services later. They bypass tenant/workspace/project isolation.

**Required fix:** remove public unscoped reads from production repository or make them package-private/admin-only with explicit audited admin scope.

### G2.P0 — Provider partial-snapshot fail-closed behavior (implemented)

Status: implemented. Both providers now throw explicit `PARTIAL_SNAPSHOT_REJECTED` errors when cumulative size limits are exceeded rather than returning partial snapshots.

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

### G2.P1 — Exclude-pattern skip reason fidelity (completed)

Status: completed. Scanner now records explicit `EXCLUDE_PATTERN` skip reason and includes matched pattern metadata.

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

### G3.P0 — Snapshot file-boundary routing in TS pipeline (completed)

`ArtifactCompileJobService` filters inventory files into `tsFiles` and sends only those to `tsExtractorWorker.extract(...)`. 

`ProcessTsExtractorWorker` then enforces that returned nodes must belong to the routed TypeScript file scope. 

Status: completed in implementation pass 6. `runFromSnapshot(...)` now enforces snapshot-scoped inventory boundaries, and scanner traversal correctly descends into parent directories for allowed files.

**Production risk:** Java passes filtered TS files, but TS may scan the entire materialized root and return nodes outside the allowed set. Java will then reject those nodes, causing compile jobs to fail or become flaky depending on repo contents.

**Required fix:** TS `SynthesisPipeline` must support `runFromSnapshotFiles(snapshot)` or respect `snapshot.files` as the inventory boundary. Java should not rely on post-hoc rejection to enforce scope.

### G3.P0 — Graph ingest null-content checksum handling (completed)

Status: completed in earlier implementation passes. `ArtifactGraphRepository.computeChecksum(...)` now handles null content safely and no longer dereferences `node.content()` unsafely during checksum generation.

**Production risk:** reduced for this boundary; null `content` from TS worker no longer causes checksum-time `NullPointerException`.

**Completion note:** regression coverage for null-content graph paths remains in focused storage repository tests.

### G3.P0 — TS worker residual source strictness (completed)

Status: completed and regression-covered in implementation pass 11. Java rejects incomplete residual payloads and TS worker serialization now fails closed for missing/blank `originalSource`, `checksum`, or `rawFragmentRef`, with focused contract tests enforcing that behavior.

**Production risk:** reduced for this boundary; synthetic placeholder residuals are now blocked by contract validation and worker serialization checks.

**Completion note:** strict fail-closed behavior is now encoded in worker tests and should remain a required CI gate.

### G3.P0 — Semantic model repository SQL/runtime consistency (implemented with contract tests)

`SemanticModelRepository` now uses a shared binder path and schema validation (`validateSchema`) for required column presence before write operations.

**Production risk:** reduced; schema mismatch now fails fast with explicit diagnostics instead of latent runtime drift.

**Completion note:** `SemanticModelRepositoryTest` now verifies both success path and missing-column fail-fast behavior.

### G3.P1 — Graph ingest and semantic model persistence partial-failure workflow (implemented)

Status: implemented in pass 12. `ArtifactCompileJobService` now returns explicit `FAILED_PARTIAL` compile results on extraction/graph/semantic persistence exceptions, including stage-specific diagnostics and preserving graph response when semantic persistence fails post-ingest.

**Production risk:** reduced from silent/runtime failure propagation to explicit partial-state signaling suitable for repair/retry workflows.

**Remaining enhancement:** full single-transaction graph+semantic atomicity is still a follow-up; current behavior provides deterministic partial-state reporting and safer recovery semantics.

### G3.P1 — Semantic models map from semantic synthesis output (completed)

Status: completed. TS worker response generation maps semantic models from `result.model.elements` (semantic synthesis output), not from raw graph-node projection.

**Production risk:** reduced for model-fidelity mapping; persisted semantic models now carry semantic-element-level provenance and links.

**Completion note:** this path is exercised by worker contract tests and compatibility gates.

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
