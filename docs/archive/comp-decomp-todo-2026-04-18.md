# Artifact Compiler/Decompiler Audit — `samujjwal/ghatana` at `9498ac6df6790297579c4bce7f141a4cfb14fe45`

Commit inspected: `9498ac6df6790297579c4bce7f141a4cfb14fe45`. The commit itself is a merge/changelog commit, so this audit treats it as the full repository snapshot target rather than a diff-only review. 

## A. Executive Summary

### Current maturity level

The Artifact Compiler/Decompiler is **foundation-present but not production-ready**.

Current implementation has meaningful building blocks:

* Java-side source provider abstraction, source import jobs, repository snapshot DTO, source providers, compile-job orchestration, graph ingest/query/analyze API, graph persistence, and scoped backend APIs.
* TypeScript-side artifact compiler package with inventory scanning, graph schemas, synthesis pipeline, residual island schemas, extractor worker, and compile-back/patch-planning library.
* Java-orchestrated TypeScript worker boundary exists through `ArtifactCompileJobService` and `ProcessTsExtractorWorker`.

However, the system is **not yet true round-trip capable** because compile-back/patch generation is primarily TypeScript-library-level, while durable backend patch planning, validation, review, apply, rollback, governance, and no-op round-trip guarantees are not implemented as a production Java-owned workflow. The frontend client even marks patch review as a placeholder endpoint. 

### Biggest blockers

1. **Java/TypeScript contract drift is P0.**
   Java DTOs use `projectId` and `relationshipType`, while the TypeScript client still uses `productId` and `relationship`. This can break graph ingest and query behavior even when both sides compile independently.  

2. **Source acquisition is partially implemented but not trustworthy enough.**
   Java has `SourceProvider`, `SourceProviderRegistry`, `SourceLocator`, and `RepositorySnapshot`, and registers GitHub, GitLab, local-folder, and archive providers.    
   But GitHub/GitLab credential references are not actually resolved into provider credentials, `.gitignore` matching is simplified, GitLab path/project encoding is unsafe, GitLab pagination is incomplete, and GitHub/GitLab bounded concurrency is claimed but implemented as sequential loop + semaphore.  

3. **Residual preservation is lossy at the Java ingest boundary.**
   TypeScript defines rich residual islands with original source, source location, raw fragment ref, checksum, risk, and related graph nodes. 
   Java ingest currently accepts only `residualIslandIds` in the DTO and `ArtifactGraphServiceImpl` synthesizes generic residual records from IDs, losing original source span/raw fragment/checksum unless another path persists them.  

4. **Compile-back is not production-integrated.**
   TypeScript has `ChangeOp`, `PatchSet`, `PatchEmitter`, `PatchContext`, `ModelChange`, `ChangePlan`, `FilePatch`, `ReviewBundle`, and `RollbackMetadata` types. 
   It also has a `PatchCoordinator`, but this is TypeScript-side orchestration, not a durable Java-owned backend patch lifecycle. 

5. **Backend graph persistence exists but is still not sufficient for full source→model→patch→source trust.**
   Java persists nodes/edges with tenant/workspace/project filters and stores snapshot/version fields, confidence, provenance, source refs, symbol refs, residual refs, and tombstone state. 
   But the graph API is still ingest/query/analyze/merge focused, not full compiler/decompiler lifecycle focused. 

### Current capability classification

```text
Source acquisition: partial repo-capable, not production-trustworthy
Inventory: partial deterministic scanner, not complete production repository inventory
Graph: graph-capable and partially unresolved-aware
Semantic model: partial TypeScript-side synthesis, not fully backend-persisted canonical model
Compile-back: partial TypeScript library only, not backend/governed/round-trip-safe
Overall: not production-ready
```

Recommended next milestone:

```text
Milestone 1: Stable Repository IR and Source Snapshot Compiler
```

This milestone should not attempt full AI editing or patch application first. It must first make source acquisition, inventory, graph, unresolved edge lifecycle, residual preservation, and Java/TS contracts trustworthy.

---

## B. Objective Current Status

| Area                             |                      Status | Evidence                                                                                                                                                | Objective conclusion                                                                                                                                         | Production impact                                                          |
| -------------------------------- | --------------------------: | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| Java source provider abstraction |     `PARTIALLY_IMPLEMENTED` | `SourceProvider` defines provider ID, capability check, resolve, and scope context.                                                                     | Good canonical start. Missing stronger capability model, credential contract, retry/cancel/progress hooks per provider.                                      | Foundation exists but needs hardening.                                     |
| Source provider registry         |     `PARTIALLY_IMPLEMENTED` | Registers GitHub, local-folder, archive, GitLab.                                                                                                        | Canonical registry exists. Needs provider health/capability discovery endpoint and feature flags for unsafe providers.                                       | Extensible but not fully governed.                                         |
| SourceLocator                    |     `PARTIALLY_IMPLEMENTED` | Contains provider, repoId, ref, path, credentialRef, tenant/workspace/project IDs.                                                                      | Correct concept. Credential ref is not consistently resolved by providers.                                                                                   | Private repo support is not trustworthy.                                   |
| RepositorySnapshot               |     `PARTIALLY_IMPLEMENTED` | Immutable-style DTO with snapshotId, provider, repoId, commit/content hash, materialized root, files, checksum, diagnostics, tenant/workspace/project.  | Correct DTO. Missing durable snapshot repository/table as canonical source of truth.                                                                         | Snapshot identity can be lost outside graph ingest.                        |
| GitHub provider                  |     `PARTIALLY_IMPLEMENTED` | Resolves commit, fetches recursive tree/blobs, materializes files, deterministic snapshot ID.                                                           | Useful but not production-safe: credentialRef unused, simplified `.gitignore`, no actual concurrent fetch scheduling, API tree limitations.                  | Can import some public repos, but not trustworthy for large/private repos. |
| GitLab provider                  |     `UNSAFE_FOR_PRODUCTION` | Similar implementation to GitHub.                                                                                                                       | Project/path URL encoding and pagination are incomplete; credentialRef unused.                                                                               | Likely fails or silently under-scans real GitLab repos.                    |
| Local folder provider            |     `PARTIALLY_IMPLEMENTED` | Uses `RepositoryInventoryScanner`, restricts path to workspace root, deterministic snapshot ID.                                                         | Good start. Path policy is too rigid for trusted runtime roots and needs explicit allowed-root config.                                                       | Usable for local dev, not yet robust product runtime.                      |
| Archive provider                 |     `PARTIALLY_IMPLEMENTED` | ZIP extraction with zip-slip protection, streaming, size limits, deterministic snapshot ID.                                                             | ZIP-only; no tar support despite target scope; snapshot ID depends on archive path.                                                                          | Same archive in different path can become different snapshot identity.     |
| Java inventory scanner           |     `PARTIALLY_IMPLEMENTED` | Classifies file type and parses `.gitignore` with simplified matching.                                                                                  | Missing authoritative ignore semantics, binary detection, generated/vendor skip reasons, package boundary model, stable sorted walk, large-file streaming.   | Scanner cannot be trusted as canonical first compiler pass.                |
| TypeScript inventory scanner     |     `PARTIALLY_IMPLEMENTED` | Has richer deterministic scan config, generated/binary classification, package boundary detection, gitignore matcher, skip artifacts.                   | Better than Java scanner, but now competes with Java scanner. Needs clear ownership: Java inventory canonical, TS can assist only for TS extraction context. | Duplicate scanner semantics can cause drift.                               |
| Java import job lifecycle        |     `PARTIALLY_IMPLEMENTED` | `ImportController` creates import jobs, validates principal/scope, submits job, triggers async compile.                                                 | Durable job exists. Needs stronger cancellation/resume/retry and job phase result persistence.                                                               | Good foundation, incomplete operations.                                    |
| Source import persistence        |     `PARTIALLY_IMPLEMENTED` | JDBC repository persists source import jobs, status, progress, cancellation, scoped lookup.                                                             | Scoped lookup exists, but deprecated unscoped lookup remains and list queries are under-scoped.                                                              | Possible future leakage/misuse risk.                                       |
| Java compile pipeline            |     `PARTIALLY_IMPLEMENTED` | `ArtifactCompileJobService` resolves source, invokes TS worker, ingests graph.                                                                          | Correct high-level boundary. Missing Java inventory persistence, Java extractors, semantic model persistence, patch lifecycle.                               | Import→graph works partially; not full compiler/decompiler.                |
| Java-orchestrated TS worker      |     `PARTIALLY_IMPLEMENTED` | `ProcessTsExtractorWorker` runs configured worker command, sends snapshot JSON, applies timeout, validates basic response.                              | Good hybrid pattern. Needs command allowlist, generated contract validation, no legacy field fallbacks, sandbox/resource limits.                             | Worker boundary can drift or be unsafe.                                    |
| TS extractor worker              |     `PARTIALLY_IMPLEMENTED` | Normalizes Java worker request, runs `SynthesisPipeline`, serializes graph, unresolved edges, residual IDs.                                             | Strong start. It emits residual IDs only to Java ingest, causing lossy backend state.                                                                        | Round-trip fidelity risk.                                                  |
| Graph schema                     |     `PARTIALLY_IMPLEMENTED` | TS graph schema separates resolved edges and unresolved edges; deterministic node ID helper exists.                                                     | Good IR model. Deterministic ID falls back to random without snapshot ref.                                                                                   | No-snapshot flows are not deterministic.                                   |
| Java graph DTOs                  |     `PARTIALLY_IMPLEMENTED` | Node DTO carries source location, extractor metadata, confidence, provenance, privacy/security flags, residual refs, sourceRef, symbolRef.              | Good graph node payload. Needs generated contract alignment with TS/proto.                                                                                   | Hand-maintained drift risk.                                                |
| Java graph edge DTO              |     `PARTIALLY_IMPLEMENTED` | Edge DTO requires resolved source/target node IDs and relationshipType.                                                                                 | Correct direction. TS client still uses `relationship`.                                                                                                      | API contract mismatch.                                                     |
| Graph controller                 |     `PARTIALLY_IMPLEMENTED` | Controller enforces principal tenant, workspace/project headers, request scope mismatch rejection, basic node/edge validation.                          | Stronger than many parts. Still has raw-map residual analysis and validation local to controller.                                                            | Needs central validator and typed residual API.                            |
| Graph service/repository         |     `PARTIALLY_IMPLEMENTED` | Service upserts nodes/edges, saves unresolved/resolution/residual records, uses blocking executor for JGraphT.                                          | Solid start. Residual mapping is lossy; query/analyze not full compiler lifecycle.                                                                           | Graph foundation exists, but not round-trip safe.                          |
| Proto contract                   |     `PARTIALLY_IMPLEMENTED` | Proto includes SourceLocator, RepositorySnapshot, SourceFileRef, ArtifactNode/Edge, unresolved edge, residual island, patch set, review bundle.         | Contract is broader than implementation and not clearly generated into Java/TS DTOs.                                                                         | Contract drift already visible.                                            |
| Semantic model                   |     `PARTIALLY_IMPLEMENTED` | TS `SynthesisPipeline` builds semantic product model from graph/extraction results.                                                                     | TS-only synthesis exists. Java durable model versioning is not clearly canonical.                                                                            | UI/model edits cannot reliably become governed backend changes.            |
| Residual islands                 |     `PARTIALLY_IMPLEMENTED` | TS residual schema is rich and production-minded.                                                                                                       | Java ingest path loses rich residual data.                                                                                                                   | Source fidelity and round-trip safety are blocked.                         |
| Compile-back                     |     `PARTIALLY_IMPLEMENTED` | TS compile-back exports patch types, patch coordinator, emitters, apply/rollback helpers.                                                               | Useful library; not backend-owned durable workflow.                                                                                                          | Not production round-trip capable.                                         |
| Frontend client                  | `DUPLICATED_OR_CONFLICTING` | TS client has typed methods but mismatched fields and placeholder patch review.                                                                         | Must be regenerated from canonical contract or corrected immediately.                                                                                        | Frontend/backend integration can fail silently.                            |

---

## C. Architecture Decisions

### Decision 1: Java owns durable compiler orchestration

**Decision:** Java is canonical for source providers, import jobs, snapshot lifecycle, graph persistence, graph validation, tenant/workspace/project enforcement, validation orchestration, review/apply/rollback workflow, audit events, and long-running/heavy work.

**Why:** Java already has `ImportController`, `SourceImportJobService`, `ArtifactCompileJobService`, `SourceProviderRegistry`, `ArtifactGraphController`, `ArtifactGraphServiceImpl`, and JDBC repositories.   

**Files to keep/modify:**

* Keep/modify `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ImportController.java`
* Keep/modify `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java`
* Keep/modify `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`
* Keep/modify `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`

### Decision 2: TypeScript owns TS/TSX-native extraction and frontend UX, not durable production state

**Decision:** TypeScript remains canonical for TS/TSX/React extraction, TS patch emitters, canvas/page-builder UX, import summary UX, and patch review UX. It must not own tenant-scoped durable source import state or backend governance.

**Why:** TS has the artifact compiler package, graph schemas, synthesis pipeline, TS worker, and compile-back emitters.   

### Decision 3: Use hybrid Java-orchestrated TypeScript worker

**Decision:** The existing Java-orchestrated TS worker pattern is the right approach, but contract enforcement must be generated and strict.

**Why:** `ArtifactCompileJobService` already resolves source and invokes `TsExtractorWorker`, while `ProcessTsExtractorWorker` executes a configured worker command with timeout.  

**Required correction:** Remove ad hoc JSON field fallback and replace with generated schema/contract validation.

### Decision 4: Proto/schema must become the canonical contract

**Decision:** `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto` should be the canonical contract source, or it should be replaced by a single canonical JSON schema/OpenAPI/proto pipeline. Do not keep Java records, TS interfaces, and proto definitions independently hand-maintained.

**Why:** Proto already contains many canonical concepts, but the Java DTOs and TS client currently disagree on `product_id/projectId`, `relationship/relationshipType`, and residual payload shape.   

### Decision 5: Compile-back must be Java-governed, with TypeScript emitters as workers

**Decision:** Java owns `ChangePlan`, `PatchSet`, `ReviewBundle`, validation orchestration, governance approval, apply, rollback, audit, and persistence. TypeScript can own React/TS/Prisma/workflow patch emitters as Java-orchestrated workers.

**Why:** TS compile-back types and coordinator exist, but no Java backend patch lifecycle was found and frontend client marks patch review as placeholder.   

---

## D. Java vs TypeScript Ownership Plan

| Capability                      | Current implementation                 |                                                          Recommended owner | Why                                                                               | Runtime boundary                                  |
| ------------------------------- | -------------------------------------- | -------------------------------------------------------------------------: | --------------------------------------------------------------------------------- | ------------------------------------------------- |
| Source provider abstraction     | Java `SourceProvider`                  |                                                           `JAVA_CANONICAL` | Durable backend scope + credentials + audit.                                      | TS can display capabilities only.                 |
| GitHub provider                 | Java                                   |                                                           `JAVA_CANONICAL` | Needs credential/gov/audit and large repo controls.                               | No TS duplicate provider for production.          |
| GitLab provider                 | Java                                   |                                                           `JAVA_CANONICAL` | Same as GitHub; fix encoding/pagination.                                          | No TS production provider.                        |
| Local folder provider           | Java                                   |                                                           `JAVA_CANONICAL` | Trusted runtime path access belongs backend-side.                                 | UI only submits locator.                          |
| Archive provider                | Java                                   |                                                           `JAVA_CANONICAL` | Zip/tar safety and file limits belong backend.                                    | UI upload feeds backend.                          |
| Import job lifecycle            | Java                                   |                                                           `JAVA_CANONICAL` | Durable job status, retry/cancel/resume.                                          | TS polls/subscribes.                              |
| Repository snapshot             | Java DTO only                          |                                                           `JAVA_CANONICAL` | Snapshot identity must persist server-side.                                       | TS receives read-only summary.                    |
| Inventory scanner               | Java + TS duplicate                    |                                       `JAVA_CANONICAL` with TS helper only | Java should produce canonical inventory; TS scanner can support worker internals. | Normalize scanner output through contract.        |
| TS/TSX extractor                | TS worker                              |                                       `HYBRID_JAVA_ORCHESTRATED_TS_WORKER` | TS Compiler API/React tooling is more correct.                                    | Java invokes worker and persists output.          |
| Java extractor                  | Partial Java parser imports in service |                                                           `JAVA_CANONICAL` | JavaParser/OpenRewrite are Java-native.                                           | Add extractor registry.                           |
| Graph schema                    | TS schema + Java DTO + proto           |                                         `CONTRACT_ONLY` + Java persistence | Prevent drift.                                                                    | Generate Java/TS types.                           |
| Graph persistence               | Java JDBC                              |                                                           `JAVA_CANONICAL` | Tenant/workspace/project server-side isolation.                                   | TS never writes DB directly.                      |
| Symbol/reference index          | TS resolver + Java unresolved storage  |                                  `JAVA_CANONICAL` persisted, TS emits refs | Need durable re-resolution and cross-scan drift.                                  | TS emits unresolved refs; Java resolves/persists. |
| Semantic model synthesis        | TS pipeline                            | `HYBRID_JAVA_ORCHESTRATED_TS_WORKER` initially; Java canonical persistence | TS can synthesize; Java must persist/version/govern.                              | Worker returns model + provenance.                |
| Residual detection              | TS rich schema + Java lossy IDs        |                                      `HYBRID`, Java canonical preservation | Detection can occur in extractors; preservation must be backend durable.          | Pass full residual payload.                       |
| Change planning                 | TS library                             |                                                           `JAVA_CANONICAL` | Governance, audit, validation, review.                                            | TS can provide language-specific diff helpers.    |
| TS patch emitter                | TS library                             |                                       `HYBRID_JAVA_ORCHESTRATED_TS_WORKER` | TS-native edits should stay TS.                                                   | Java sends patch task, receives diffs.            |
| Patch validation/apply/rollback | TS helper only                         |                                                           `JAVA_CANONICAL` | Must be durable, audited, governed.                                               | TS worker emits candidate patches only.           |
| Frontend import UX              | TS frontend                            |                                                     `TYPESCRIPT_CANONICAL` | UI responsibility.                                                                | Uses generated API client.                        |
| Patch review UX                 | TS client placeholder                  |                      `TYPESCRIPT_CANONICAL` for UX, Java canonical backend | UI displays backend review bundle.                                                | No placeholder endpoint.                          |
| Generated API clients           | Manual TS client                       |                                                            `CONTRACT_ONLY` | Current drift proves manual types are unsafe.                                     | Generate from proto/OpenAPI.                      |

---

## E. Gap Analysis

| Capability               | Current state                                    | Gap                                                                                                                   | Severity | Required fix                                                                                    |
| ------------------------ | ------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------- | -------: | ----------------------------------------------------------------------------------------------- |
| Java/TS contract         | Proto, Java DTOs, TS interfaces all exist        | Names and shapes drift: `projectId/productId`, `relationship/relationshipType`, residual IDs vs full residual payload |     `P0` | Generate Java/TS contracts from one source and remove hand-written client DTO drift.            |
| Residual preservation    | TS rich schema, Java ingest IDs only             | Backend cannot preserve original source/raw fragment/checksum from IDs alone                                          |     `P0` | Add Java `ResidualIslandDto`, ingest full residuals, persist exact fields.                      |
| Compile-back             | TS types/coordinator exist                       | No durable Java patch workflow/API/repository                                                                         |     `P0` | Add Java patch service/controller/repository and TS emitter worker integration.                 |
| Source credentials       | `SourceLocator.credentialRef` exists             | Providers do not resolve credential refs                                                                              |     `P0` | Add credential resolver interface and enforce no raw tokens/log leakage.                        |
| GitLab provider          | Exists                                           | URL encoding, file path encoding, pagination, credentials incomplete                                                  |     `P0` | Fix GitLab API semantics and add integration tests.                                             |
| `.gitignore` correctness | Java simplified; TS richer                       | Duplicate/unequal behavior and simplified matching                                                                    |     `P1` | Use one canonical scanner behavior; preferably Java scanner with proven ignore matcher/library. |
| Inventory classification | Java basic, TS richer                            | Missing canonical skip reasons, binary/generated/vendor/large classification in Java                                  |     `P1` | Move canonical inventory model to Java; keep TS scanner only as worker helper.                  |
| Snapshot persistence     | Snapshot DTO exists                              | No verified durable `repository_snapshots` table/repository                                                           |     `P1` | Add `RepositorySnapshotRepository` and migration.                                               |
| Source import jobs       | Durable repo exists                              | Deprecated unscoped lookup and under-scoped list methods remain                                                       |     `P0` | Remove/replace unscoped APIs; require tenant/workspace/project everywhere.                      |
| Worker boundary          | Process worker exists                            | Command string is arbitrary; response schema allows legacy fallbacks                                                  |     `P1` | Add command allowlist/config object, strict schema, resource/sandbox controls.                  |
| Graph validation         | Controller validates only payload-local node IDs | Does not centralize repo-aware validation or cross-snapshot validation                                                |     `P1` | Add `ArtifactGraphValidator` service and use in controller/service/repository.                  |
| Semantic model           | TS synthesis builds model                        | No durable Java canonical model API/persistence proven                                                                |     `P1` | Add semantic model repository/version API or connect to existing model version repository.      |
| Patch review client      | Placeholder endpoint                             | UI can call API that backend does not implement                                                                       |     `P0` | Feature-flag or remove until Java patch API exists.                                             |

---

## F. Prescriptive File-by-File TODO Plan

| Priority | Phase                      | File path                                                                                                             |              Action | Required change                                                                                                                                                                                                                                                                                                                                                                                 | Tests                                                      |
| -------: | -------------------------- | --------------------------------------------------------------------------------------------------------------------- | ------------------: | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
|       P0 | Contract foundation        | `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`                                           |            `MODIFY` | Make this the canonical source for `SourceLocator`, `RepositorySnapshot`, `ArtifactNode`, `ArtifactEdge`, `UnresolvedGraphEdge`, `EdgeResolutionRecord`, `ResidualIsland`, `SemanticProductModel`, `ModelChange`, `ChangePlan`, `PatchSet`, `ReviewBundle`, `ValidationResult`, `RollbackMetadata`. Remove `product_id` naming where Java/UI use `projectId`, or generate aliases consistently. | Add generated-contract compatibility tests in Java and TS. |
|       P0 | Contract foundation        | `products/yappc/frontend/web/src/clients/artifactCompiler/ArtifactCompilerClient.ts`                                  |           `REPLACE` | Replace manual DTOs with generated client/types. Until generated, change `productId`→`projectId`, `relationship`→`relationshipType`, add `workspaceId` and `projectId` headers, remove or feature-flag `reviewPatch`.                                                                                                                                                                           | `ArtifactCompilerClient.contract.test.ts`                  |
|       P0 | Worker contract            | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ProcessTsExtractorWorker.java`  |            `MODIFY` | Enforce generated schema. Reject edges that use `source`/`target` fallback unless a legacy migration flag is enabled. Validate `relationshipType`, snapshot ID, extractor metadata, residual payload. Add worker command allowlist/config object.                                                                                                                                               | `ProcessTsExtractorWorkerContractTest.java`                |
|       P0 | Worker contract            | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`                              |            `MODIFY` | Emit exact generated Java contract fields. Return full `residualIslands`, not only `residualIslandIds`. Ensure every edge includes `snapshotId`, `versionId`, `relationshipType`, `sourceNodeId`, `targetNodeId`.                                                                                                                                                                               | `ts-extractor-worker.contract.test.ts`                     |
|       P0 | Residual fidelity          | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ArtifactGraphIngestRequest.java`  |            `MODIFY` | Replace `List<String> residualIslandIds` with typed `List<ResidualIslandDto>` while keeping IDs only as deprecated transitional field. Add typed unresolved/resolution DTOs instead of raw maps.                                                                                                                                                                                                | `ArtifactGraphIngestRequestRoundTripTest.java`             |
|       P0 | Residual fidelity          | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ResidualIslandDto.java`           |               `ADD` | Add fields matching TS/proto residual schema: id, kind/type, originalSource/rawFragmentRef, sourceLocation/sourceSpan, checksum, reason, confidence, risk, reviewRequired, regenerationStrategy, relatedGraphNodeIds, linkedModelElementIds, tenant/workspace/project/snapshot.                                                                                                                 | `ResidualIslandDtoRoundTripTest.java`                      |
|       P0 | Residual fidelity          | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`  |            `MODIFY` | Stop synthesizing generic residual records from IDs. Persist exact residual payload. Reject lossy residual ingest when graph contains residual refs without full residual records.                                                                                                                                                                                                              | `ArtifactGraphServiceResidualPreservationTest.java`        |
|       P0 | Graph validation           | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactGraphController.java`                 |            `MODIFY` | Move duplicate node/edge validation into `ArtifactGraphValidator`. Replace raw-map `analyzeResidual` with typed request. Validate workspace/project/tenant through shared scope helper.                                                                                                                                                                                                         | `ArtifactGraphControllerScopeTest.java`                    |
|       P0 | Graph validation           | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphValidator.java`    |               `ADD` | Validate node IDs, resolved edge IDs, unresolved edge separation, source locations, provenance, confidence range, residual refs, snapshot consistency, and repo-aware edge targets.                                                                                                                                                                                                             | `ArtifactGraphValidatorTest.java`                          |
|       P0 | Source import isolation    | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/SourceImportJobRepository.java`           |            `MODIFY` | Remove or make private deprecated unscoped `findJobById`. Add workspace/project filters to all list methods. Store `snapshotId`, `versionId`, and terminal result metadata.                                                                                                                                                                                                                     | `SourceImportJobRepositoryScopeTest.java`                  |
|       P0 | Source import isolation    | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/import_/SourceImportJobService.java`     |            `MODIFY` | Remove public unscoped job lookup. Add retry/resume/cancel state transitions with persisted cancellation token and phase result.                                                                                                                                                                                                                                                                | `SourceImportJobServiceLifecycleTest.java`                 |
|       P0 | Source credentials         | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/SourceCredentialResolver.java`    |               `ADD` | Resolve governed credential refs into provider-scoped tokens without logging secrets. Enforce tenant/workspace/project ownership.                                                                                                                                                                                                                                                               | `SourceCredentialResolverTest.java`                        |
|       P0 | GitHub provider            | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitHubSourceProvider.java`        |            `MODIFY` | Use `SourceCredentialResolver`; replace simplified `.gitignore`; implement real bounded parallel fetch or remove claim; add archive-download fallback for large repos; sort files before checksum.                                                                                                                                                                                              | `GitHubSourceProviderCommitPinnedTest.java`                |
|       P0 | GitLab provider            | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/GitLabSourceProvider.java`        |            `MODIFY` | URL-encode project ID and file paths correctly, paginate tree results, use credentials, fail closed on incomplete tree, sort files deterministically.                                                                                                                                                                                                                                           | `GitLabSourceProviderUrlEncodingTest.java`                 |
|       P1 | Inventory canonicalization | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/RepositoryInventoryScanner.java`  |           `REPLACE` | Upgrade to canonical scanner with stable sorted walk, authoritative `.gitignore`, include/exclude rules, generated/vendor/binary/large skip reasons, package/workspace boundary detection, streaming checksum.                                                                                                                                                                                  | `RepositoryInventoryScannerGoldenTest.java`                |
|       P1 | TS scanner boundary        | `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`                                       |            `MODIFY` | Mark as worker-local scanner or align output exactly to Java canonical inventory contract. Remove conflicting semantics or generate shared types.                                                                                                                                                                                                                                               | `scanner.contract.test.ts`                                 |
|       P1 | Archive provider           | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/ArchiveSourceProvider.java`       |            `MODIFY` | Add tar/tar.gz support or explicitly feature-flag unsupported archive types. Make snapshot ID content-based, not path-based. Run canonical inventory after extraction.                                                                                                                                                                                                                          | `ArchiveSourceProviderDeterministicTest.java`              |
|       P1 | Snapshot persistence       | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/RepositorySnapshotRepository.java`        |               `ADD` | Persist immutable snapshots, files, diagnostics, checksums, source locator refs, tenant/workspace/project, createdBy, createdAt.                                                                                                                                                                                                                                                                | `RepositorySnapshotRepositoryTest.java`                    |
|       P1 | Snapshot schema            | `products/yappc/core/yappc-services/src/main/resources/db/migration/V___artifact_repository_snapshots.sql`            |               `ADD` | Add `repository_snapshots`, `repository_snapshot_files`, indexes by tenant/workspace/project/snapshot/content hash.                                                                                                                                                                                                                                                                             | Migration test                                             |
|       P1 | Compile orchestration      | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java` |            `MODIFY` | Persist snapshot before extraction, run canonical Java inventory, route TS files to TS worker, route Java files to Java extractor, persist semantic model/version, update job progress per phase.                                                                                                                                                                                               | `ArtifactCompileJobServiceIntegrationTest.java`            |
|       P1 | Java extractor             | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/parser/JavaSourceParser.java`   |            `MODIFY` | Treat as Java extractor plugin or replace with `JavaArtifactExtractor`; include source locations, symbol refs, unresolved refs, confidence/provenance.                                                                                                                                                                                                                                          | `JavaArtifactExtractorTest.java`                           |
|       P1 | Patch backend              | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/ArtifactPatchController.java`                 |               `ADD` | Add create change plan, build patch set, validate, create review bundle, approve/reject, apply, rollback, status endpoints.                                                                                                                                                                                                                                                                     | `ArtifactPatchControllerScopeTest.java`                    |
|       P1 | Patch backend              | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/patch/ArtifactPatchService.java`         |               `ADD` | Java-governed patch workflow. Invoke TS patch worker only for TS/React emitters. Enforce residual overlap checks and validation gates.                                                                                                                                                                                                                                                          | `ArtifactPatchServiceRoundTripTest.java`                   |
|       P1 | Patch persistence          | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/storage/PatchSetRepository.java`                  |               `ADD` | Persist change plans, patch sets, file patches, validation results, review bundles, rollback metadata.                                                                                                                                                                                                                                                                                          | `PatchSetRepositoryTest.java`                              |
|       P1 | TS patch worker            | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/patch-coordinator.ts`                          |            `MODIFY` | Keep as worker library, not canonical backend workflow. Remove production stderr logger default; require injected logger in worker. Add no-op zero-diff mode.                                                                                                                                                                                                                                   | `patch-coordinator.roundtrip.test.ts`                      |
|       P1 | Frontend UX                | `products/yappc/frontend/web/src/components/canvas/page/ArtifactImportPanel.tsx`                                      |        `ADD/MODIFY` | Source provider picker, repo/ref/archive/local input, progress, import summary, confidence/residual/skipped sections.                                                                                                                                                                                                                                                                           | Component + Playwright tests                               |
|       P1 | Frontend UX                | `products/yappc/frontend/web/src/components/canvas/page/PatchReviewPanel.tsx`                                         |               `ADD` | Display backend review bundle, diffs, validation errors, residual overlaps, approve/reject/apply actions.                                                                                                                                                                                                                                                                                       | `artifact-patch-review.spec.ts`                            |
|       P2 | Cleanup                    | `platform/comp-decomp-todo.md`                                                                                        | `DEPRECATE_OR_MOVE` | Do not use as implementation source of truth if stale; move to archive or replace with current architecture status.                                                                                                                                                                                                                                                                             | Docs lint/check                                            |
|       P2 | Cleanup                    | `docs/archive/comp-decomp-todo-2026-03-27-audit.md`                                                                   |     `KEEP_ARCHIVED` | Keep archived only; ensure no current code links to it as authoritative.                                                                                                                                                                                                                                                                                                                        | Link check                                                 |

---

## G. Phase Plan

### Phase 1: Contract and Fidelity Hardening

**Goal:** Java, TypeScript, and proto agree on the same compiler/decompiler contract.

**Files:**

* `artifact_compiler.proto`
* `ArtifactGraphIngestRequest.java`
* `ArtifactNodeDto.java`
* `ArtifactEdgeDto.java`
* `ArtifactCompilerClient.ts`
* `ts-extractor-worker.ts`
* `ProcessTsExtractorWorker.java`

**Tasks:**

1. Generate or strictly mirror Java and TypeScript types from the canonical proto/schema.
2. Fix `projectId/productId` drift.
3. Fix `relationship/relationshipType` drift.
4. Replace residual ID-only ingestion with full residual island payload.
5. Remove permissive worker field fallbacks.

**Exit criteria:**

* TS worker output round-trips into Java DTOs without adapter hacks.
* Contract tests fail if any field drifts.
* Residual source, span, checksum, and raw fragment ref survive ingest.

### Phase 2: Stable Repository Snapshot and Inventory

**Goal:** Every source import produces an immutable, deterministic, auditable snapshot and inventory.

**Files:**

* `SourceProvider.java`
* `SourceLocator.java`
* `RepositorySnapshot.java`
* `RepositoryInventoryScanner.java`
* `GitHubSourceProvider.java`
* `GitLabSourceProvider.java`
* `ArchiveSourceProvider.java`
* `LocalFolderSourceProvider.java`
* `RepositorySnapshotRepository.java`
* snapshot migrations

**Tasks:**

1. Persist snapshots before extraction.
2. Make inventory scanner canonical on Java side.
3. Fix GitHub/GitLab credentials, pagination, ignore matching, and deterministic ordering.
4. Produce explicit skip reasons.
5. Detect package/workspace boundaries.

**Exit criteria:**

* Same commit/source produces same snapshot ID and same ordered inventory.
* `.gitignore`, generated, vendor, binary, and large files are explicitly handled.
* Snapshot can be reloaded and used for re-scan/drift detection.

### Phase 3: Graph Correctness and Semantic Model Persistence

**Goal:** The backend stores a source-faithful graph and a provenance-rich semantic model.

**Files:**

* `ArtifactGraphController.java`
* `ArtifactGraphServiceImpl.java`
* `ArtifactGraphRepository.java`
* `ArtifactGraphValidator.java`
* semantic model repository/version files

**Tasks:**

1. Centralize graph validation.
2. Persist unresolved edges separately.
3. Persist edge resolution records.
4. Persist residual islands fully.
5. Persist semantic model with graph node mapping, confidence, provenance, review requirements.

**Exit criteria:**

* No fake resolved edges.
* Unresolved references are explicit.
* Semantic model can be traced back to graph nodes/source spans.

### Phase 4: Java-Governed Compile-Back

**Goal:** Model edits produce safe, minimal, validated, reviewable patches.

**Files:**

* `ArtifactPatchController.java`
* `ArtifactPatchService.java`
* `PatchSetRepository.java`
* `patch-coordinator.ts`
* TS patch worker files

**Tasks:**

1. Add Java patch lifecycle endpoints.
2. Add change plan and patch persistence.
3. Add TS patch worker for React/TS emitters.
4. Add residual-overlap validation.
5. Add no-op round-trip zero-diff validation.
6. Add rollback metadata.

**Exit criteria:**

* No-op source→model→patch produces zero diff.
* Simple component prop edit produces minimal patch.
* Low-confidence/residual-overlap changes require review.
* Patch apply/rollback is audited and scoped.

### Phase 5: UX Integration

**Goal:** Users can import, inspect, edit, validate, and review changes with low cognitive load.

**Files:**

* `ArtifactCompilerClient.ts`
* `ArtifactImportPanel.tsx`
* `ArtifactImportSummary.tsx`
* `PatchReviewPanel.tsx`
* canvas/page-builder integration files

**Tasks:**

1. Use generated client.
2. Show source provider capabilities.
3. Show import progress and summary.
4. Show understood/skipped/residual/confidence state.
5. Show patch diff, validation, residual overlap, approve/reject/apply.

**Exit criteria:**

* UI never hides residuals or low-confidence changes.
* UI cannot apply patch without backend validation/review state.
* E2E import→model→edit→patch review path passes.

---

## H. Test Plan

| Priority | Test file                                                                                                                                | Scenario                           | Expected assertion                                                                                      |
| -------: | ---------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------- |
|       P0 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/compiler/ProcessTsExtractorWorkerContractTest.java`         | TS worker emits generated contract | Java rejects missing `relationshipType`, missing residual payload, or fake edge targets.                |
|       P0 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.contract.test.ts`                                   | Worker response contract           | Output matches canonical schema exactly.                                                                |
|       P0 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceResidualPreservationTest.java` | Residual ingest                    | Original source, source span, checksum, raw fragment ref, risk, and reviewRequired survive persistence. |
|       P0 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/api/ArtifactGraphControllerScopeTest.java`                           | Tenant/project mismatch            | Body-supplied tenant/project cannot override principal/header scope.                                    |
|       P0 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/storage/SourceImportJobRepositoryScopeTest.java`                     | Job lookup/list isolation          | Jobs cannot be read/listed across tenant/workspace/project.                                             |
|       P0 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/patch/ArtifactPatchServiceRoundTripTest.java`               | No-op round-trip                   | Source→snapshot→graph→model→patch produces zero file diffs.                                             |
|       P0 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/patch/ArtifactPatchServiceResidualOverlapTest.java`         | Patch overlaps residual            | Patch is blocked or review-required.                                                                    |
|       P1 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/RepositoryInventoryScannerGoldenTest.java`           | Stable scan                        | Same fixture produces same ordered artifact IDs/checksums/skips.                                        |
|       P1 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/GitHubSourceProviderCommitPinnedTest.java`           | GitHub commit pinning              | Branch/ref resolves to immutable commit SHA and stable snapshot ID.                                     |
|       P1 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/GitLabSourceProviderUrlEncodingTest.java`            | GitLab namespace/project path      | Encodes project and file paths correctly and paginates.                                                 |
|       P1 | `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/source/ArchiveSourceProviderDeterministicTest.java`         | Same archive in different path     | Same content produces same snapshot ID.                                                                 |
|       P1 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/patch-coordinator.roundtrip.test.ts`                              | TS patch coordinator no-op         | No changes produce empty patch set.                                                                     |
|       P1 | `products/yappc/frontend/web/e2e/artifact-import.spec.ts`                                                                                | Import UX                          | User sees provider, progress, summary, skipped files, residuals, confidence.                            |
|       P1 | `products/yappc/frontend/web/e2e/artifact-patch-review.spec.ts`                                                                          | Patch review UX                    | User sees diff, validation, residual overlaps, apply/reject disabled until backend allows.              |

Required fixtures:

```text
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/small-react-app
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/react-router-app
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/nextjs-app
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/prisma-fullstack-app
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/java-service
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/openapi-client-app
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/github-actions-workflow-project
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/pnpm-monorepo
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/polyglot-frontend-backend-db-workflow-app
```

---

## I. Direct Answers to Critical Questions

### 1. Is the current system truly round-trip capable?

**Answer:** No.

**Evidence:** TypeScript compile-back types and patch coordinator exist, but backend patch lifecycle is missing and frontend patch review is marked as placeholder.   

**Required fix:** Add Java `ArtifactPatchController`, `ArtifactPatchService`, `PatchSetRepository`, migrations, TS patch worker, and no-op round-trip tests.

### 2. Can it scan a full GitHub repo today, or only import individual sources/files?

**Answer:** Partial.

**Evidence:** `GitHubSourceProvider` resolves commit SHA, fetches recursive tree/blobs, materializes files, and returns `RepositorySnapshot`. 

**Required fix:** Implement credentialRef resolution, authoritative `.gitignore`, large repo fallback, deterministic sorted inventory, and integration tests.

### 3. Are artifact IDs deterministic?

**Answer:** Partial.

**Evidence:** Java providers compute deterministic snapshot IDs. TS graph helper builds deterministic node IDs when `snapshotRef` exists, but falls back to random UUID without snapshotRef.  

**Required fix:** Require snapshotRef for source-derived artifacts; allow random IDs only for explicitly manual/synthetic artifacts.

### 4. Are graph edges valid and resolved?

**Answer:** Partial.

**Evidence:** TS graph schema separates resolved and unresolved edges; Java edge DTO requires resolved source/target node IDs; controller validates edges against ingested node IDs.   

**Required fix:** Add central graph validator and repo-aware resolution. Stop worker/client fallback fields from masking unresolved references.

### 5. Is there a complete synthesis pipeline?

**Answer:** Partial.

**Evidence:** TS `SynthesisPipeline` scans, extracts, resolves, assembles graph, validates graph, and builds semantic model. 

**Required fix:** Persist semantic model in Java with provenance/confidence and use it as the source for governed change planning.

### 6. Is compile-back/patch generation implemented?

**Answer:** Partial, TS-library only.

**Evidence:** Compile-back types and patch coordinator exist in TS, but no backend patch controller/service was found and TS client labels patch review placeholder.   

**Required fix:** Java-governed backend patch lifecycle with TS emitters as workers.

### 7. Are residual islands preserved?

**Answer:** Partial.

**Evidence:** TS residual schema is rich, but Java ingest request only accepts residual island IDs and service maps IDs into generic placeholder residual records.   

**Required fix:** Ingest and persist full residual payloads.

### 8. Are source import jobs durable?

**Answer:** Partial.

**Evidence:** JDBC `SourceImportJobRepository` persists jobs, status, progress, cancellation, and scoped lookup. 

**Required fix:** Remove unscoped methods, scope list queries, persist phase results/snapshot IDs, support real retry/resume/cancel.

### 9. Is tenant/workspace/project scope enforced consistently?

**Answer:** Partial.

**Evidence:** `ImportController` and `ArtifactGraphController` enforce principal/header scope, but `SourceImportJobRepository` still contains deprecated unscoped lookup and under-scoped list methods.   

**Required fix:** Remove public unscoped lookup and require tenant/workspace/project scope everywhere.

### 10. Is backend artifact graph logic in the right canonical module?

**Answer:** Partial.

**Evidence:** `yappc-services` clearly owns graph controller/service/repository.   

**Required fix:** Inspect `products/yappc/core/knowledge-graph` for duplication. If duplicated, keep `yappc-services` as tenant-scoped API/persistence owner and make `knowledge-graph` a reusable graph primitive library only.

### 11. Are there duplicate or conflicting implementations?

**Answer:** Yes.

**Evidence:** Java and TS both have scanner/inventory concepts; Java/TS/proto contracts drift; TS source import API route is now only a proxy because Java owns production acquisition.    

**Required fix:** Canonical Java inventory + generated contracts + TS worker-only scanner helpers.

### 12. What is the smallest milestone that makes the foundation trustworthy?

**Answer:** Stable Repository IR and Source Snapshot Compiler.

Minimum deliverables:

1. Canonical generated contract.
2. Durable repository snapshot.
3. Canonical deterministic inventory.
4. Full residual payload preservation.
5. Strict TS worker contract.
6. Graph validation.
7. Golden scan tests.
8. Contract tests.
9. No-op round-trip test scaffold.

### 13. What files must be changed first?

1. `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`
2. `products/yappc/frontend/web/src/clients/artifactCompiler/ArtifactCompilerClient.ts`
3. `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/domain/artifact/ArtifactGraphIngestRequest.java`
4. `products/yappc/frontend/libs/yappc-artifact-compiler/src/worker/ts-extractor-worker.ts`
5. `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ProcessTsExtractorWorker.java`
6. `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/artifact/ArtifactGraphServiceImpl.java`
7. `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/source/RepositoryInventoryScanner.java`

### 14. What tests must fail today and pass after implementation?

* Contract drift test: TS client `productId/relationship` must fail against Java DTO.
* Residual preservation test: full residual source/checksum/raw fragment must fail today because Java ingest only carries IDs.
* GitLab provider URL encoding/pagination test should fail.
* Backend patch lifecycle no-op round-trip test should fail because Java patch workflow is missing.
* Source import job scoped list test should fail if list queries omit workspace/project.

### 15. What legacy/stale/deprecated code/docs must be removed or consolidated?

* Deprecated unscoped `SourceImportJobRepository.findJobById(String jobId)`.
* Manual `ArtifactCompilerClient.ts` DTOs after generated client exists.
* TS source-provider/import implementations as production paths; keep only worker/frontend helpers.
* Any stale `platform/comp-decomp-todo.md` content not matching current architecture.
* Any archived audit docs referenced by current implementation docs.

---

## J. Prioritized TODO Checklist

### P0

* [ ] [P0] Make `artifact_compiler.proto` or equivalent schema the single source of truth.

  * Files:

    * `products/yappc/core/yappc-services/src/main/proto/artifact_compiler.proto`
    * `products/yappc/frontend/web/src/clients/artifactCompiler/ArtifactCompilerClient.ts`
  * Runtime owner: Contract only
  * Done when: Java DTOs, TS worker IO, TS client, and proto agree exactly.
  * Test: Generated contract compatibility test.

* [ ] [P0] Preserve full residual islands across Java ingest/persistence.

  * Files:

    * `ArtifactGraphIngestRequest.java`
    * `ResidualIslandDto.java`
    * `ArtifactGraphServiceImpl.java`
    * `ArtifactGraphRepository.java`
  * Runtime owner: Java canonical, TS detection allowed
  * Done when: original source, span, checksum, raw fragment, risk, and review state survive source→worker→backend.
  * Test: `ArtifactGraphServiceResidualPreservationTest.java`.

* [ ] [P0] Add Java-governed patch lifecycle.

  * Files:

    * `ArtifactPatchController.java`
    * `ArtifactPatchService.java`
    * `PatchSetRepository.java`
    * patch migrations
  * Runtime owner: Java canonical with TS emitter workers
  * Done when: no-op round-trip produces zero diff and simple edit produces minimal patch.
  * Test: `ArtifactPatchServiceRoundTripTest.java`.

* [ ] [P0] Fix source provider credentials and GitLab correctness.

  * Files:

    * `SourceCredentialResolver.java`
    * `GitHubSourceProvider.java`
    * `GitLabSourceProvider.java`
  * Runtime owner: Java
  * Done when: private repo imports use governed credential refs and GitLab project/file paths are encoded and paginated correctly.
  * Test: GitHub/GitLab provider integration tests.

* [ ] [P0] Remove public unscoped import job access.

  * Files:

    * `SourceImportJobRepository.java`
    * `SourceImportJobService.java`
  * Runtime owner: Java
  * Done when: every read/list/update requires tenant/workspace/project.
  * Test: `SourceImportJobRepositoryScopeTest.java`.

### P1

* [ ] [P1] Replace Java inventory scanner with canonical deterministic repository inventory.

  * Files:

    * `RepositoryInventoryScanner.java`
    * `RepositorySnapshotRepository.java`
    * snapshot migrations
  * Runtime owner: Java
  * Done when: same source produces stable ordered inventory with explicit skip reasons.
  * Test: golden scan tests.

* [ ] [P1] Strictly validate TS worker contract.

  * Files:

    * `ProcessTsExtractorWorker.java`
    * `ts-extractor-worker.ts`
  * Runtime owner: Hybrid Java-orchestrated TS worker
  * Done when: worker cannot emit legacy fallback edge fields or lossy residuals.
  * Test: Java/TS worker contract tests.

* [ ] [P1] Persist semantic model versions in Java.

  * Files:

    * `ArtifactCompileJobService.java`
    * semantic model DTO/repository/migration files
  * Runtime owner: Java canonical, TS synthesis helper
  * Done when: model elements have provenance, confidence, graph-node mapping, residual refs, and version history.
  * Test: semantic model synthesis/persistence integration test.

* [ ] [P1] Build import and patch review UX against real backend contracts.

  * Files:

    * `ArtifactImportPanel.tsx`
    * `ArtifactImportSummary.tsx`
    * `PatchReviewPanel.tsx`
    * generated API client
  * Runtime owner: TypeScript frontend
  * Done when: UI shows provider, progress, skipped files, residuals, confidence, validation, and review decisions.
  * Test: Playwright import/edit/patch review E2E.

### P2

* [ ] [P2] Consolidate Java and TypeScript scanner semantics.

  * Files:

    * `RepositoryInventoryScanner.java`
    * `products/yappc/frontend/libs/yappc-artifact-compiler/src/inventory/scanner.ts`
  * Runtime owner: Java canonical, TS worker helper
  * Done when: TS scanner cannot disagree with canonical inventory contract.
  * Test: cross-runtime scanner fixture parity test.

* [ ] [P2] Clean stale docs and TODOs.

  * Files:

    * `platform/comp-decomp-todo.md`
    * `docs/archive/comp-decomp-todo-2026-03-27-audit.md`
    * `products/yappc/docs/architecture/ARTIFACT_COMPILER_DECOMPILER_ARCHITECTURE.md`
  * Runtime owner: Docs
  * Done when: current docs match implementation and archived docs are not used as source of truth.
  * Test: docs link/source-of-truth check.

### P3

* [ ] [P3] Normalize naming from product/project across all APIs.

  * Files:

    * proto
    * Java DTOs
    * TS generated client
    * UI code
  * Runtime owner: Contract only
  * Done when: one term is canonical externally and internal aliases are removed.
  * Test: contract lint test.
