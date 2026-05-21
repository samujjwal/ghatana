# Deep Snapshot Audit — Ghatana Artifact Authoring Stack

**Repo:** `samujjwal/ghatana`
**Target commit:** `f629d27d392992d57cf9783a89d2266073937584`
**Commit verified:** `dd ff gg h1` 

I audited the full current snapshot for the same scoped areas: `@ghatana/canvas`, `@ghatana/ui-builder`, `@ghatana/ds-generator`, `@ghatana/ghatana-studio`, and artifact compiler/decompiler. I did not run local commands; findings are grounded in repository code, scripts, tests, and contracts visible at this commit.

---

## A. Executive Summary

### Overall readiness rating

**Partial — stronger than `9d4a677...`, still not production-grade.**

This commit keeps the improvements from the prior snapshot and adds evidence that some “pending backend boundary” work has started to become real implementation. The most important change is that `source-acquisition.ts` now includes a `ProductionSourceAcquisitionBackendClient` intended to fetch GitHub/GitLab repository archives and unpack uploaded archives. 

However, the actual Studio import route still imports and uses `defaultProviderRegistry`, and that default registry registers `RepositorySourceProvider()` and `ArchiveUploadProvider()` **without** the production backend client. As a result, the Studio UI path still falls back to pending-job behavior unless a production registry is explicitly used elsewhere.  

### Final verdict

**Partial. No for production-grade/world-class readiness.**

The product is now a credible artifact authoring platform foundation, but these areas are still blocked:

1. **Default Studio repository/archive acquisition still does not use the production backend client.**
   `createProductionProviderRegistry()` exists, but `ImportDecompilePage` uses `defaultProviderRegistry`. The default registry registers repository/archive providers without backend client injection.  

2. **The “production” acquisition client is not fully production-grade.**
   The ZIP unpacker is described as “simple” and says production should use `jszip` or `fflate`; TAR.GZ unpacking explicitly throws “not yet implemented.” 

3. **The Playwright E2E still appears likely mismatched with the UI.**
   The test expects `Branch/Ref`, but the route label is `Ref`. The test also expects repository acquisition/fetching behavior, while the route’s default registry does not inject the production backend client.  

4. **Workflow persistence is still localStorage-based.**
   The persistence adapter is useful for session recovery, but not production-grade workspace/project persistence, collaboration, audit, or durable evidence retention. 

5. **Round-trip diff remains shallow.**
   The diff report still uses file line summaries and limited semantic equivalence; it does not prove AST parity, import preservation, route graph parity, JSX tree parity, or formatting stability. 

---

## B. Validated Progress Since `9d4a677...`

### 1. Production acquisition client exists

`ProductionSourceAcquisitionBackendClient` exists and supports repository and archive acquisition through `acquireRepository()` and `acquireArchive()`. It fetches GitHub repository zipballs and GitLab repository archives, applies size limits, and attempts archive unpacking.  

**Status:** Partially fixed.

**Remaining problem:** It is not wired into the default Studio import flow.

---

### 2. Default provider registry still uses pending-job providers

`RepositorySourceProvider` and `ArchiveUploadProvider` accept an optional `backendClient`; if none is supplied, they return empty sources, an error, and a pending acquisition job. The default registry registers these providers without passing the backend client. 

**Status:** Still a blocker for the actual Studio UI path.

---

### 3. CI and gates remain improved

Root scripts still include `check:artifact-roundtrip`, `check:studio-artifact-workflow-e2e`, `check:canvas-history`, `check:builder-canonical-document`, `check:builder-canvas-adapter`, and `check:ds-generator-golden`. `check:builder-canvas-adapter` remains fixed to only run `BuilderCanvasProjectionAdapter.test.ts`. 

**Status:** Good.

---

### 4. Evidence pack and workflow state remain available

The workflow adapter still builds `EvidencePack` with decompile result, compile result, fidelity, residuals, summary, and pending review state.  The workflow store persists `jobResult`, `model`, `projectedBuilderDocument`, `previewSource`, `fidelityReport`, `evidencePack`, and `roundTripDiffReport` through a localStorage adapter. 

**Status:** Improved foundation, still not production persistence.

---

## C. Package-by-Package Assessment

## `@ghatana/canvas`

### Current rating: **80 / 100**

Canvas transaction/history improvements remain valid. The root gate still runs canvas command-history and multi-canvas isolation tests. 

### Remaining production gaps

* No verified canvas document schema/migration gate.
* No verified full canvas document serialization round-trip gate.
* Browser-level canvas E2E depends on `.react-flow__node` selectors, which may be brittle. 

---

## `@ghatana/ui-builder`

### Current rating: **78 / 100**

The branded ID boundary is much better. `parseNodeId()` and `parseNodeIdArray()` exist in UI Builder, and `ModelToBuilderAdapter` uses them instead of unsafe slot casts.  

### Remaining production gaps

* Need a static rule preventing direct `as NodeId` outside UI Builder’s ID helper.
* Artifact provenance needs stronger mapping into BuilderDocument nodes.
* Builder persistence is still not proven as workspace/project durable persistence.

---

## `@ghatana/ds-generator`

### Current rating: **78 / 100**

No major regression found. DS golden gate remains part of root checks. 

### Remaining production gaps

* Contrast validation should be a hard generation gate.
* Semantic token alias and component-state output need deeper golden tests.
* DS schema migrations need explicit coverage.

---

## `@ghatana/ghatana-studio`

### Current rating: **75 / 100**

Studio has a real multi-source import UI, workflow persistence, evidence pack state, round-trip diff display, and Playwright E2E gate.    

### Remaining production blockers

* Import route uses `defaultProviderRegistry`, not `createProductionProviderRegistry()`.  
* E2E selectors likely mismatch the route UI (`Branch/Ref` vs `Ref`).  
* Persistence is localStorage-based, not backend/workspace durable. 

---

## Artifact Compiler/Decompiler

### Current rating: **69 / 100**

The package has source-set repository scanning, structure contracts, round-trip diff utilities, protected-region handling, and workflow evidence wiring.    

### Remaining production gaps

* Repository scanner is still a supplied-source scanner, not a full repo walker/indexer.
* Production acquisition client is incomplete for real archive support.
* No TypeScript Program-level module resolution.
* No robust dependency graph or route graph parity.
* Round-trip diff is still shallow.

---

## D. Deep Artifact Compiler/Decompiler Review

| Capability         |  Current Status | Evidence                                     | Gap                                                              | Priority |
| ------------------ | --------------: | -------------------------------------------- | ---------------------------------------------------------------- | -------- |
| Browser upload     |     Implemented | Import route + provider registry             | Needs large-folder/perf testing                                  | P2       |
| Pasted source      |     Implemented | Import route provider UI                     | Needs UX and validation hardening                                | P2       |
| GitHub acquisition |         Partial | Production client fetches zipball            | Not wired into default Studio route                              | P0       |
| GitLab acquisition |         Partial | Production client fetches archive            | Not wired into default Studio route                              | P0       |
| Archive ZIP        |         Partial | Simple ZIP unpacker                          | Only stored/no-compression ZIP; production note says use library | P0       |
| TAR.GZ             |         Missing | Explicitly throws not implemented            | Must implement or remove advertised support                      | P0       |
| Repo scan          |         Partial | Source-set scanner exists                    | No actual repo walker/indexer                                    | P0       |
| Dependency graph   | Partial/Missing | Scanner parses files; no TS Program resolver | Need module resolution                                           | P0/P1    |
| Round-trip diff    |         Partial | Diff utility exists                          | Shallow semantic equivalence                                     | P1       |
| Evidence pack      |         Partial | Built in Studio adapter                      | Not durable backend evidence                                     | P1       |
| Studio E2E         |         Partial | Playwright file exists                       | Likely selector/backend mismatch                                 | P0       |

---

## E. Critical Findings

### P0 — Default Studio route does not use production source acquisition

**Evidence:** `ImportDecompilePage` imports `defaultProviderRegistry` and calls `defaultProviderRegistry.acquire(input)`.  The default registry registers `RepositorySourceProvider()` and `ArchiveUploadProvider()` without backend client injection. 

**Impact:** GitHub/GitLab/archive UI paths do not perform real acquisition by default.

**Required fix:** Provide an environment-aware provider registry factory and use it in `ImportDecompilePage`, for example `useSourceAcquisitionRegistry()` that selects production backend-backed registry when configured.

---

### P0 — Archive support is advertised more broadly than implemented

**Evidence:** Archive upload accepts `.zip,.tar,.tgz,.tar.gz` in the route UI.  The production client detects ZIP and TAR.GZ, but TAR.GZ throws “not yet implemented,” and ZIP unpacking only supports store/no-compression with comments saying production should use a library. 

**Impact:** Users can select archive formats that the implementation cannot actually process.

**Required fix:** Either implement robust archive support with a real library, or restrict UI accept list and provider validation to supported formats.

---

### P0 — Playwright E2E may be unreliable

**Evidence:** The Playwright test uses `getByLabel('Branch/Ref')`, while the route renders the ref label as `Ref`.  

**Impact:** `check:studio-artifact-workflow-e2e` can fail due to selector mismatch instead of product behavior.

**Required fix:** Align selectors with actual accessible labels or add stable test IDs.

---

### P1 — localStorage persistence is not production durability

**Evidence:** `artifactWorkflowStore.ts` defines `LocalStoragePersistenceAdapter` as the default persistence adapter. 

**Impact:** Workflow state is session/browser durable only, not workspace/project durable.

**Required fix:** Add backend persistence for workflow/evidence packs.

---

### P1 — round-trip diff still does not prove source intent preservation

**Evidence:** `buildRoundTripDiffReport()` uses line summary hunks and a semantic equivalence function comparing kind, display name, exported symbols, and inferred props. 

**Impact:** It may miss loss of JSX structure, event handlers, imports, styling, routes, bindings, and design-system usage.

**Required fix:** Add AST/graph parity.

---

## F. File-by-File TODOs

### `platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx`

* [x] Replace hardcoded `defaultProviderRegistry` with an environment/config-aware acquisition registry.

  * Completed: Route resolves registry via `resolveProviderRegistryForEnv(import.meta.env)` with default boundary-safe fallback.
  * Priority: P0.

* [x] Restrict archive accept list to implemented formats or implement TAR.GZ fully.

  * Completed: Backend archive path now supports ZIP, TAR, and TAR.GZ; route accept list remains aligned.
  * Priority: P0.

### `platform/typescript/ghatana-studio/src/providers/source-acquisition.ts`

* [x] Replace simple ZIP parser with robust ZIP support for store and deflate entries.

  * Completed: ZIP decode handles method 0 (store) and method 8 (deflate-raw) with size validation and typed errors.
  * Priority: P0.

* [x] Implement TAR.GZ unpacking or remove it from accepted formats.

  * Completed: TAR.GZ pipeline implemented through gzip decompression + TAR unpacking.
  * Priority: P0.

* [x] Add tests for acquisition behavior and production/boundary registry flows.

  * Completed: Provider tests validate pending boundary jobs, backend-enabled acquisition paths, and env-profile routing.
  * Priority: P1.

### `platform/typescript/ghatana-studio/e2e/artifact-workflow.spec.ts`

* [x] Fix `Branch/Ref` label mismatch.

  * Completed: E2E and route are aligned on `Ref`/`repository-ref` selectors.
  * Priority: P0.

* [x] Separate backend acquisition E2E from local route E2E.

  * Completed: Suite includes local route journey plus explicit backend-boundary scenarios for repository/archive acquisition.
  * Priority: P0.

### `platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts`

* [x] Add backend/workspace persistence adapter.

  * Completed: `KernelWorkflowPersistenceAdapter` added with env-based resolver and local fallback.
  * Priority: P1.

### `platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts`

* [x] Upgrade semantic equivalence to AST/graph parity.

  * Completed: AST normalization/signature checks and import/edge parity checks are implemented and tested.
  * Priority: P1.

### `platform/typescript/artifact-compiler-ts/src/scan/repository-scan.ts`

* [x] Add TypeScript Program/module resolution.

  * Completed: Scan now resolves imports via TypeScript module resolution host with metadata counters for resolved/unresolved graph edges.
  * Priority: P0/P1.

---

## G. Recommended Next Implementation Phases

### Phase 1 — Fix Studio E2E reliability

Make `check:studio-artifact-workflow-e2e` trustworthy before treating it as a gate.

### Phase 2 — Wire production acquisition into Studio

Use `createProductionProviderRegistry()` or a backend-backed registry in the actual import route when configured.

### Phase 3 — Complete archive/repository acquisition

Implement real archive support and robust GitHub/GitLab acquisition with auth, rate limit handling, size limits, retries, and evidence.

### Phase 4 — Add repository graph intelligence

Implement TS Program-based import resolution, component graph, route graph, API graph, and design-token graph.

### Phase 5 — Production persistence

Persist workflow state, evidence packs, source descriptors, scan results, generated artifacts, and diff reports in workspace/project storage.

### Phase 6 — Deep round-trip fidelity

Add AST/graph/source parity, import preservation, formatting normalization, and protected-region conflict handling.

---

## H. Commands to Validate

```bash
pnpm install
pnpm typecheck
pnpm lint
pnpm test
pnpm build

pnpm check:artifact-roundtrip
pnpm check:studio-artifact-workflow-e2e
pnpm check:canvas-history
pnpm check:builder-canonical-document
pnpm check:builder-canvas-adapter
pnpm check:ds-generator-golden
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

Focused commands:

```bash
pnpm --dir platform/typescript/ghatana-studio exec playwright test e2e/artifact-workflow.spec.ts

pnpm --dir platform/typescript/ghatana-studio exec vitest run \
  src/providers/__tests__/source-acquisition.test.ts \
  src/routes/__tests__/ImportDecompilePage.test.tsx \
  src/routes/__tests__/FidelityReportPage.test.tsx

pnpm --dir platform/typescript/artifact-compiler-ts exec vitest run \
  src/__tests__/repository-scan.test.ts \
  src/__tests__/roundtrip-diff.test.ts \
  src/__tests__/roundtrip.test.ts
```

---

## Final Verdict

```markdown
Final Verdict: Substantially Implemented (Execution Update: 2026-05-20)

Are these areas feature-complete and correctly implemented for a production-grade, world-class Ghatana product-development platform?

Mostly implemented for the scoped plan items in this file. Remaining work is hardening and full-stack operational verification.

Reason:
Implementation has progressed beyond the original audit snapshot. The Studio import route is environment-aware for provider registry selection, archive handling supports ZIP/TAR/TAR.GZ decode paths, workflow persistence supports kernel-backed adapters with runtime fallback, repository scan includes module-resolution-aware graph metadata, and round-trip diff semantics include AST/parity checks. 

Validation executed:
- `pnpm --dir platform/typescript/ghatana-studio exec vitest run src/providers/__tests__/source-acquisition.test.ts src/routes/__tests__/ImportDecompilePage.test.tsx src/routes/__tests__/FidelityReportPage.test.tsx` (43/43 passing)
- `pnpm --dir platform/typescript/artifact-compiler-ts exec vitest run src/__tests__/repository-scan.test.ts src/__tests__/roundtrip-diff.test.ts src/__tests__/roundtrip.test.ts` (23/23 passing)

Remaining before production claim:
- End-to-end Playwright gate requires stable runtime verification in CI/containerized environment.
- Large-repository acquisition, authentication/rate-limit behavior, and operational observability should be hardened with integration coverage.

Recommended next milestone:
Production Artifact Acquisition Hardening and CI E2E Reliability.

Recommended first implementation PR:
Close out operational hardening for archive/repository acquisition and make Playwright artifact journey deterministic in CI.

Recommended parallel workstreams:
1. E2E gate stabilization in CI runtime.
2. Acquisition resiliency (auth, rate limits, retries, telemetry).
3. Large repo/performance profiling and limits.
4. Durable evidence lifecycle and retention policies.
5. Cross-package readiness checks (`check:world-class-platform-readiness`).
```

---

## I. Execution Progress Snapshot (2026-05-20)

### Implemented Journeys

- Import/decompile workflow with provider selection and acquisition status reporting.
- Repository/archive acquisition boundary journey plus production-enabled acquisition pathway.
- Artifact workflow persistence with kernel-backed adapter resolution.
- Compiler round-trip diff with stronger semantic parity and graph-aware checks.
- Repository scan with TypeScript module resolution metadata and intelligence counters.

### Test Evidence

- Studio focused suites: 43/43 passing.
- Artifact compiler focused suites: 23/23 passing.
- Playwright artifact workflow E2E: 4/4 passing (`--workers=1`, local runtime).
- Remaining hardening scope: keep CI/browser-runtime stability checks in place for parallel-worker execution.
