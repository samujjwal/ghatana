# Artifact Compiler/Decompiler Production Implementation TODO

**Source**: Derived from `docs/archive/comp-decomp-todo-2026-04-18.md` audit
**Status**: COMPLETED - Phases 1-6.3
**Last Updated**: 2026-05-17

## Overview

This document tracks the production-grade implementation of all tasks required to make the Artifact Compiler/Decompiler system production-ready. All tasks must be implemented with zero compromises, following guidelines from `.github/copilot-instructions.md`.

## Current Maturity Level

The Artifact Compiler/Decompiler is **production-ready foundation completed**.

**Completed Milestones:**
1. Java/TypeScript contract alignment - COMPLETED
2. Source acquisition with governed credentials - COMPLETED
3. Residual preservation with full fidelity - COMPLETED
4. Compile-back with Java governance - COMPLETED
5. Backend graph persistence with semantic model - COMPLETED
6. Stable repository snapshots and inventory - COMPLETED
7. Frontend UX for import and patch review - COMPLETED
8. Scanner semantics consolidation - COMPLETED

**Remaining Work:**
- None

**Verification Evidence (2026-05-17):**
- `pnpm check:yappc-artifact-intelligence-boundary` passed
- `./gradlew :products:yappc:core:yappc-services:test --tests '*ContractCompatibilityTest' --tests '*ProcessTsExtractorWorkerContractTest' --tests '*JavaArtifactExtractorTest' --tests '*ArtifactCompileJobServiceIntegrationTest'` passed with BUILD SUCCESSFUL
- Contract naming normalization validated in `ContractCompatibilityTest` (canonical `projectId`, no legacy `productId` in canonical artifact-compiler contract fields)

## Implementation Phases

### Phase 1: Contract and Fidelity Hardening (P0)

**Goal**: Java, TypeScript, and proto agree on the same compiler/decompiler contract.

#### Task 1.1: Make proto the single source of truth
- **Files**: `artifact_compiler.proto`, `ArtifactCompilerClient.ts`
- **Owner**: Contract only
- **Done when**: Java DTOs, TS worker IO, TS client, and proto agree exactly
- **Test**: Generated contract compatibility test

#### Task 1.2: Preserve full residual islands across Java ingest/persistence
- **Files**: `ArtifactGraphIngestRequest.java`, `ResidualIslandDto.java`, `ArtifactGraphServiceImpl.java`, `ArtifactGraphRepository.java`
- **Owner**: Java canonical, TS detection allowed
- **Done when**: Original source, span, checksum, raw fragment, risk, and review state survive source→worker→backend
- **Test**: `ArtifactGraphServiceResidualPreservationTest.java`

#### Task 1.3: Fix source provider credentials and GitLab correctness
- **Files**: `SourceCredentialResolver.java`, `GitHubSourceProvider.java`, `GitLabSourceProvider.java`
- **Owner**: Java
- **Done when**: Private repo imports use governed credential refs and GitLab project/file paths are encoded and paginated correctly
- **Test**: GitHub/GitLab provider integration tests

#### Task 1.4: Remove public unscoped import job access
- **Files**: `SourceImportJobRepository.java`, `SourceImportJobService.java`
- **Owner**: Java
- **Done when**: Every read/list/update requires tenant/workspace/project
- **Test**: `SourceImportJobRepositoryScopeTest.java`

#### Task 1.5: Add graph validation service
- **Files**: `ArtifactGraphValidator.java`, `ArtifactGraphController.java`
- **Owner**: Java
- **Done when**: Centralized validation for node IDs, resolved edges, unresolved edges, source locations, provenance, confidence, residual refs, snapshot consistency
- **Test**: `ArtifactGraphValidatorTest.java`

### Phase 2: Stable Repository Snapshot and Inventory (P0-P1)

**Goal**: Every source import produces an immutable, deterministic, auditable snapshot and inventory.

#### Task 2.1: Persist repository snapshots
- **Files**: `RepositorySnapshotRepository.java`, snapshot migrations
- **Owner**: Java
- **Done when**: Immutable snapshots, files, diagnostics, checksums, source locator refs, tenant/workspace/project persisted
- **Test**: `RepositorySnapshotRepositoryTest.java`

#### Task 2.2: Replace Java inventory scanner with canonical deterministic scanner
- **Files**: `RepositoryInventoryScanner.java`
- **Owner**: Java
- **Done when**: Stable sorted walk, authoritative `.gitignore`, include/exclude rules, generated/vendor/binary/large skip reasons, package/workspace boundary detection
- **Test**: `RepositoryInventoryScannerGoldenTest.java`

#### Task 2.3: Fix archive provider determinism
- **Files**: `ArchiveSourceProvider.java`
- **Owner**: Java
- **Done when**: Tar/tar.gz support, content-based snapshot ID (not path-based), canonical inventory after extraction
- **Test**: `ArchiveSourceProviderDeterministicTest.java`

### Phase 3: Graph Correctness and Semantic Model Persistence (P1)

**Goal**: Backend stores source-faithful graph and provenance-rich semantic model.

#### Task 3.1: Persist unresolved edges separately
- **Files**: `ArtifactGraphRepository.java`, migrations
- **Owner**: Java
- **Done when**: Unresolved edges persisted separately from resolved edges
- **Test**: Graph unresolved edge lifecycle test

#### Task 3.2: Persist edge resolution records
- **Files**: `ArtifactGraphRepository.java`, migrations
- **Owner**: Java
- **Done when**: Resolution records tracked for cross-snapshot drift
- **Test**: Edge resolution record persistence test

#### Task 3.3: Persist semantic model versions
- **Files**: `ArtifactCompileJobService.java`, semantic model DTO/repository/migration
- **Owner**: Java canonical, TS synthesis helper
- **Done when**: Model elements have provenance, confidence, graph-node mapping, residual refs, version history
- **Test**: Semantic model synthesis/persistence integration test

### Phase 4: Java-Governed Compile-Back (P0-P1)

**Goal**: Model edits produce safe, minimal, validated, reviewable patches.

#### Task 4.1: Add Java patch lifecycle controller
- **Files**: `ArtifactPatchController.java`
- **Owner**: Java
- **Done when**: Create change plan, build patch set, validate, create review bundle, approve/reject, apply, rollback, status endpoints
- **Test**: `ArtifactPatchControllerScopeTest.java`

#### Task 4.2: Add Java patch service
- **Files**: `ArtifactPatchService.java`
- **Owner**: Java canonical with TS emitter workers
- **Done when**: Java-governed patch workflow, TS patch worker for React/TS emitters, residual-overlap validation, no-op round-trip validation
- **Test**: `ArtifactPatchServiceRoundTripTest.java`

#### Task 4.3: Add patch persistence
- **Files**: `PatchSetRepository.java`, patch migrations
- **Owner**: Java
- **Done when**: Change plans, patch sets, file patches, validation results, review bundles, rollback metadata persisted
- **Test**: `PatchSetRepositoryTest.java`

#### Task 4.4: Strictly validate TS worker contract
- **Files**: `ProcessTsExtractorWorker.java`, `ts-extractor-worker.ts`
- **Owner**: Hybrid Java-orchestrated TS worker
- **Done when**: Worker cannot emit legacy fallback edge fields or lossy residuals
- **Test**: Java/TS worker contract tests

### Phase 5: UX Integration (P1)

**Goal**: Users can import, inspect, edit, validate, and review changes with low cognitive load.

#### Task 5.1: Build import UX
- **Files**: `ArtifactImportPanel.tsx`, `ArtifactImportSummary.tsx`
- **Owner**: TypeScript frontend
- **Done when**: Source provider picker, repo/ref/archive/local input, progress, import summary, confidence/residual/skipped sections
- **Test**: Component + Playwright tests

#### Task 5.2: Build patch review UX
- **Files**: `PatchReviewPanel.tsx`
- **Owner**: TypeScript frontend
- **Done when**: Display backend review bundle, diffs, validation errors, residual overlaps, approve/reject/apply actions
- **Test**: `artifact-patch-review.spec.ts`

#### Task 5.3: Use generated API client
- **Files**: `ArtifactCompilerClient.ts`
- **Owner**: Contract only
- **Done when**: Manual DTOs replaced with generated client/types
- **Test**: `ArtifactCompilerClient.contract.test.ts`

### Phase 6: Consolidation and Cleanup (P2-P3)

**Goal**: Remove duplicates, consolidate semantics, clean stale docs.

#### Task 6.1: Consolidate Java and TypeScript scanner semantics
- **Files**: `RepositoryInventoryScanner.java`, `scanner.ts`
- **Owner**: Java canonical, TS worker helper
- **Done when**: TS scanner cannot disagree with canonical inventory contract
- **Test**: Cross-runtime scanner fixture parity test

#### Task 6.2: Clean stale docs and TODOs
- **Files**: `platform/comp-decomp-todo.md`, archived docs
- **Owner**: Docs
- **Done when**: Current docs match implementation, archived docs not used as source of truth
- **Test**: Docs link/source-of-truth check
- **Status**: COMPLETED (2026-05-17)

#### Task 6.3: Normalize naming from product/project across all APIs
- **Files**: Proto, Java DTOs, TS generated client, UI code
- **Owner**: Contract only
- **Done when**: One term canonical externally, internal aliases removed
- **Test**: Contract lint test
- **Status**: COMPLETED (2026-05-17)

## File-by-File Implementation Plan

### P0 Tasks (Must complete first)

| Priority | Phase | File path | Action | Required change | Tests |
|----------|-------|-----------|--------|-----------------|-------|
| P0 | Contract foundation | `artifact_compiler.proto` | MODIFY | Make canonical source for all types, remove product_id naming drift | Generated-contract compatibility tests |
| P0 | Contract foundation | `ArtifactCompilerClient.ts` | REPLACE | Replace manual DTOs with generated client, fix projectId/relationshipType | `ArtifactCompilerClient.contract.test.ts` |
| P0 | Worker contract | `ProcessTsExtractorWorker.java` | MODIFY | Enforce generated schema, reject legacy fallbacks, add command allowlist | `ProcessTsExtractorWorkerContractTest.java` |
| P0 | Worker contract | `ts-extractor-worker.ts` | MODIFY | Emit exact generated contract fields, return full residuals not just IDs | `ts-extractor-worker.contract.test.ts` |
| P0 | Residual fidelity | `ArtifactGraphIngestRequest.java` | MODIFY | Replace residualIslandIds with typed List<ResidualIslandDto>, add typed unresolved/resolution DTOs | `ArtifactGraphIngestRequestRoundTripTest.java` |
| P0 | Residual fidelity | `ResidualIslandDto.java` | ADD | Add fields matching TS/proto: id, kind, originalSource, sourceLocation, checksum, risk, etc. | `ResidualIslandDtoRoundTripTest.java` |
| P0 | Residual fidelity | `ArtifactGraphServiceImpl.java` | MODIFY | Stop synthesizing residuals from IDs, persist exact payload, reject lossy ingest | `ArtifactGraphServiceResidualPreservationTest.java` |
| P0 | Graph validation | `ArtifactGraphController.java` | MODIFY | Move validation to ArtifactGraphValidator, typed residual request, scope helper | `ArtifactGraphControllerScopeTest.java` |
| P0 | Graph validation | `ArtifactGraphValidator.java` | ADD | Validate node IDs, resolved/unresolved edges, source locations, provenance, confidence, residual refs, snapshot consistency | `ArtifactGraphValidatorTest.java` |
| P0 | Source import isolation | `SourceImportJobRepository.java` | MODIFY | Remove deprecated unscoped findJobById, add workspace/project filters to all list methods | `SourceImportJobRepositoryScopeTest.java` |
| P0 | Source import isolation | `SourceImportJobService.java` | MODIFY | Remove public unscoped lookup, add retry/resume/cancel with persisted cancellation token | `SourceImportJobServiceLifecycleTest.java` |
| P0 | Source credentials | `SourceCredentialResolver.java` | ADD | Resolve governed credential refs without logging secrets, enforce tenant/workspace/project ownership | `SourceCredentialResolverTest.java` |
| P0 | GitHub provider | `GitHubSourceProvider.java` | MODIFY | Use SourceCredentialResolver, authoritative .gitignore, real bounded parallel fetch, archive fallback | `GitHubSourceProviderCommitPinnedTest.java` |
| P0 | GitLab provider | `GitLabSourceProvider.java` | MODIFY | URL-encode project/file paths, paginate tree, use credentials, fail closed on incomplete tree | `GitLabSourceProviderUrlEncodingTest.java` |
| P0 | Patch backend | `ArtifactPatchController.java` | ADD | Create change plan, build patch set, validate, review bundle, approve/reject, apply, rollback endpoints | `ArtifactPatchControllerScopeTest.java` |
| P0 | Patch backend | `ArtifactPatchService.java` | ADD | Java-governed patch workflow, TS patch worker for React/TS, residual-overlap validation | `ArtifactPatchServiceRoundTripTest.java` |
| P0 | Patch backend | `PatchSetRepository.java` | ADD | Persist change plans, patch sets, file patches, validation results, review bundles, rollback metadata | `PatchSetRepositoryTest.java` |

### P1 Tasks

| Priority | Phase | File path | Action | Required change | Tests |
|----------|-------|-----------|--------|-----------------|-------|
| P1 | Inventory canonicalization | `RepositoryInventoryScanner.java` | REPLACE | Canonical scanner with stable sorted walk, authoritative .gitignore, skip reasons, package boundaries | `RepositoryInventoryScannerGoldenTest.java` |
| P1 | TS scanner boundary | `scanner.ts` | MODIFY | Mark as worker-local or align to Java canonical contract, remove conflicting semantics | `scanner.contract.test.ts` |
| P1 | Archive provider | `ArchiveSourceProvider.java` | MODIFY | Add tar/tar.gz support, content-based snapshot ID, canonical inventory after extraction | `ArchiveSourceProviderDeterministicTest.java` |
| P1 | Snapshot persistence | `RepositorySnapshotRepository.java` | ADD | Persist immutable snapshots, files, diagnostics, checksums, source locator refs, tenant/workspace/project | `RepositorySnapshotRepositoryTest.java` |
| P1 | Snapshot schema | Migration file | ADD | Add repository_snapshots, repository_snapshot_files tables with indexes | Migration test |
| P1 | Compile orchestration | `ArtifactCompileJobService.java` | MODIFY | Persist snapshot before extraction, run canonical inventory, route to appropriate extractors, persist semantic model | `ArtifactCompileJobServiceIntegrationTest.java` |
| P1 | Java extractor | `JavaSourceParser.java` | MODIFY | Treat as extractor plugin, include source locations, symbol refs, unresolved refs, confidence/provenance | `JavaArtifactExtractorTest.java` |
| P1 | TS patch worker | `patch-coordinator.ts` | MODIFY | Keep as worker library, require injected logger, add no-op zero-diff mode | `patch-coordinator.roundtrip.test.ts` |
| P1 | Frontend UX | `ArtifactImportPanel.tsx` | ADD/MODIFY | Source provider picker, progress, import summary, confidence/residual/skipped sections | Component + Playwright tests |
| P1 | Frontend UX | `PatchReviewPanel.tsx` | ADD | Display review bundle, diffs, validation errors, residual overlaps, approve/reject/apply | `artifact-patch-review.spec.ts` |

### P2-P3 Tasks

| Priority | Phase | File path | Action | Required change | Tests |
|----------|-------|-----------|--------|-----------------|-------|
| P2 | Cleanup | `platform/comp-decomp-todo.md` | DEPRECATE_OR_MOVE | Do not use as implementation source of truth if stale | Docs lint/check |
| P2 | Cleanup | Archived docs | KEEP_ARCHIVED | Ensure no current code links to them as authoritative | Link check |
| P3 | Naming normalization | All contract files | MODIFY | Normalize from product/project across all APIs | Contract lint test |

## Exit Criteria

### Phase 1: Contract and Fidelity Hardening
- TS worker output round-trips into Java DTOs without adapter hacks
- Contract tests fail if any field drifts
- Residual source, span, checksum, and raw fragment ref survive ingest

### Phase 2: Stable Repository Snapshot and Inventory
- Same commit/source produces same snapshot ID and same ordered inventory
- .gitignore, generated, vendor, binary, and large files explicitly handled
- Snapshot can be reloaded and used for re-scan/drift detection

### Phase 3: Graph Correctness and Semantic Model Persistence
- No fake resolved edges
- Unresolved references are explicit
- Semantic model can be traced back to graph nodes/source spans

### Phase 4: Java-Governed Compile-Back
- No-op source→model→patch produces zero diff
- Simple component prop edit produces minimal patch
- Low-confidence/residual-overlap changes require review
- Patch apply/rollback is audited and scoped

### Phase 5: UX Integration
- UI never hides residuals or low-confidence changes
- UI cannot apply patch without backend validation/review state
- E2E import→model→edit→patch review path passes

### Phase 6: Consolidation and Cleanup
- Current docs match implementation
- Archived docs not used as source of truth
- One term canonical externally, internal aliases removed

## Test Fixtures Required

```
products/yappc/core/yappc-services/src/test/resources/fixtures/artifact-compiler/
  - small-react-app
  - react-router-app
  - nextjs-app
  - prisma-fullstack-app
  - java-service
  - openapi-client-app
  - github-actions-workflow-project
  - pnpm-monorepo
  - polyglot-frontend-backend-db-workflow-app
```

## Critical Questions Answered

1. **Is the current system truly round-trip capable?** No - backend patch lifecycle missing
2. **Can it scan a full GitHub repo today?** Partial - needs credentialRef, authoritative .gitignore, deterministic inventory
3. **Are artifact IDs deterministic?** Partial - needs snapshotRef requirement
4. **Are graph edges valid and resolved?** Partial - needs central validator
5. **Is there a complete synthesis pipeline?** Partial - needs semantic model persistence
6. **Is compile-back/patch generation implemented?** Partial, TS-library only - needs Java governance
7. **Are residual islands preserved?** Partial - needs full payload ingestion
8. **Are source import jobs durable?** Partial - needs unscoped removal, phase result persistence
9. **Is tenant/workspace/project scope enforced consistently?** Partial - needs unscoped removal
10. **Is backend artifact graph logic in the right canonical module?** Partial - needs knowledge-graph inspection
11. **Are there duplicate or conflicting implementations?** Yes - needs canonical Java inventory + generated contracts
12. **What is the smallest milestone that makes the foundation trustworthy?** Stable Repository IR and Source Snapshot Compiler

## Implementation Notes

- All Java code must follow ActiveJ Promise patterns, never block event loop
- All TypeScript code must be fully typed, no `any` types
- All public Java APIs require @doc.* tags
- All changes must include appropriate unit/integration tests
- Follow existing Ghatana naming conventions and architecture patterns
- Reuse existing platform modules before creating new abstractions
- No hardcoded secrets or unsafe defaults
- Observability (logging, metrics, tracing) required for all critical flows
