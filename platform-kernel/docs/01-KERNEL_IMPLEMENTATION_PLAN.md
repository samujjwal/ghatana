# Deep + Wide Production-Readiness Audit

## Ghatana Studio, Artifact Authoring Stack, Product Interaction Gates, and Release Readiness

**Repo:** `samujjwal/ghatana`
**Target commit:** `c5388f6712cc3280f7ff71198b595cab9cb1d980`
**Commit verified:** `df fdasf fdsaf` 

I audited the current snapshot for the artifact-authoring stack and the wider platform/product release-readiness gates. I did not run local commands; findings are grounded in repository files and scripts visible at this commit.

---

## A. Executive Summary

### Overall readiness rating

**Partial, broader and more ambitious than `6a42f9...`, but with new gate-consistency risk.**

This commit expands the release/readiness posture significantly. The root `package.json` now includes deeper gates for product interactions, plugin interactions, language/toolchain adapters, affected-surface execution, lifecycle history, artifact round-trip generated validation, Studio production profile, Studio workflow persistence, Studio source acquisition worker, product accessibility, i18n, AI governance, runtime failure injection, atomic workflow proof, and release quality. `check:world-class-platform-readiness` now delegates to `check:release-gate`, which itself depends on `check:phase8` plus Data Cloud release/runtime/UI/product release checks. 

However, the wider the gate becomes, the more important script correctness becomes. I found a **high-risk gate consistency issue**: `check:phase8` references `pnpm check:studio-deep-interactions`, `pnpm check:studio-workflow-persistence-contracts`, and `pnpm check:studio-source-acquisition-worker`, but I did not find direct definitions for those script names in the inspected root script block around the phase gates. The visible package scripts define many related checks, including `check:studio-artifact-workflow-e2e` and `check:studio-production-profile`, but not those three names in the same inspected block.  

### Final verdict

**Partial. Not production-grade until release gates are proven executable end-to-end.**

The current snapshot is much closer to a world-class readiness framework, but it is not enough to have comprehensive gate names. Every command in `check:phase8` and `check:release-gate` must exist, run deterministically, and validate behavior rather than presence. The biggest risk at this commit is **gate sprawl without guaranteed executability**.

---

## B. Major Improvements Validated

### 1. Artifact round-trip now includes generated validation

`check:artifact-roundtrip` now runs the existing round-trip, protected-region, roundtrip-diff, repository-scan tests, and adds `generated-validation.test.ts`. 

The generated artifact validation test proves the validator passes valid TSX and catches TypeScript diagnostics with source locations.  The validator uses an in-memory TypeScript compiler host, ambient React/JSX types, syntactic and semantic diagnostics, and returns a `ValidationPipelineResult`. 

**Assessment:** Strong improvement. This moves beyond source/model/source diff into generated artifact type/syntax validation. It is still not full install/lint/test/build, but it is a meaningful validation layer.

---

### 2. Studio production profile gate exists

`check:studio-production-profile` is now present in root scripts and phase-8.  The script enforces production deployment profile configuration, production acquisition settings, kernel-backed source acquisition requirements, HTTPS kernel API base URL, required workflow persistence variables, and `VITE_STUDIO_REQUIRE_KERNEL_WORKFLOW_PERSISTENCE=true` in production. 

**Assessment:** Good production-profile hardening. One concern: root `check:studio-production-profile` runs `--mode=staging --no-strict`, so the root command is not proving strict production mode by default. 

---

### 3. Source acquisition remains environment-resolved and more production-capable

`ImportDecompilePage` still resolves the provider registry through `resolveProviderRegistryForEnv(env)` and falls back to `defaultProviderRegistry`. It also exposes stable test IDs, acquisition status, and workflow orchestration for decompile, compile, re-import, diff, evidence pack, and persistence. 

`source-acquisition.ts` still contains `ProductionSourceAcquisitionBackendClient`, GitHub/GitLab archive download, ZIP/TAR/TAR.GZ archive handling, default registry, production registry factory, and env-based resolver.   

**Assessment:** Better than earlier snapshots. Still client-heavy and env-gated.

---

### 4. Workflow persistence has kernel adapter

`artifactWorkflowStore.ts` retains `KernelWorkflowPersistenceAdapter`, which persists workflow state to `/api/v1/studio/workflow-state`, loads and clears the same endpoint, and optionally persists evidence packs to `/api/v1/studio/workflow-evidence` with tenant/workspace/project/auth headers. 

**Assessment:** Correct client-side abstraction. Backend endpoint existence and authorization behavior still need proof.

---

### 5. Round-trip diff is AST/signature-aware

`roundtrip-diff.ts` retains AST normalization, semantic signatures for imports, exports, JSX nodes/attributes, calls, event handlers, bindings, style references, plus import graph parity. 

**Assessment:** Stronger than earlier diff logic. Still not full generated-project validation, though generated TypeScript validation is now being added through a separate validator.

---

### 6. Wider product interaction gates are much broader

The root scripts now include checks for plugin interaction broker, product interaction broker, interaction performance, cross-product interaction flows, Java/TypeScript/Rust/Python adapter conformance, polyglot product fixtures, affected-surface execution, lifecycle explain/recover, and lifecycle run history. 

**Assessment:** This is a meaningful shift from artifact-specific readiness to platform/product lifecycle readiness.

---

## C. Critical Findings

### P0 — `check:phase8` appears to reference undefined or unverified scripts

`check:phase8` references `check:studio-deep-interactions`, `check:studio-workflow-persistence-contracts`, and `check:studio-source-acquisition-worker`. These names appear in the phase-8 command, but I did not find corresponding direct script definitions in the inspected root script ranges where related Studio scripts are defined.  

**Impact:** `pnpm check:phase8` and therefore `pnpm check:world-class-platform-readiness` may fail before executing meaningful checks, or these gate names may represent intended work not actually wired.

**Required action:** Add or correct these root scripts, or remove them from phase-8 until implemented. Then run `pnpm check:phase8` from a clean checkout.

**Priority:** P0.

---

### P0/P1 — Production-profile check is defined, but root command uses staging/no-strict

The script itself can enforce strict production behavior, but the root command currently runs it as:

```bash
pnpm --dir platform/typescript/kernel-lifecycle exec vitest run --root ../../.. scripts/__tests__/check-studio-production-profile.test.mjs && node ./scripts/check-studio-production-profile.mjs --mode=staging --no-strict
```

This does not prove production enforcement in the root readiness path.  The script’s strict production logic would fail production if repository/archive providers are exposed without production acquisition and kernel backend settings. 

**Required action:** Add a separate `check:studio-production-profile:strict` or run the production mode check against `.env.production.example` / deployment profile evidence.

**Priority:** P0/P1.

---

### P1 — Generated artifact validation is TypeScript-only, not full build/test parity

`validateGeneratedArtifacts()` creates an in-memory TS program and validates syntactic and semantic diagnostics with ambient React/JSX declarations.  This is valuable, but it does not install dependencies, run a real bundler, run lint, test generated artifacts, validate exports, or run preview/browser execution.

**Required action:** Add backend/CI generated artifact validation stage: typecheck, lint, build, smoke render, and re-import.

**Priority:** P1.

---

### P1 — Source acquisition remains client/runtime constrained

The production source acquisition client still runs in Studio TypeScript and uses `fetch`, `Blob`, `DecompressionStream`, manual ZIP/TAR parsing, and browser/runtime APIs.   This is not ideal for private repos, large repos, auth, rate limits, durable logs, retries, malware scanning, binary filtering, or archive-bomb protection.

**Required action:** Treat the current implementation as a browser-capable adapter; production-grade acquisition should be server-side or worker-backed with durable job evidence.

**Priority:** P1.

---

### P1 — Kernel workflow persistence client exists, server contract still unproven

The client persists to `/api/v1/studio/workflow-state` and `/api/v1/studio/workflow-evidence`, but this audit only verified the client adapter. 

**Required action:** Add and verify server handlers, auth, scoping, idempotency, evidence immutability, retention, and audit events.

**Priority:** P1.

---

## D. Package/Area Ratings

| Area                               |       Rating | Current State                                                                                                                           |
| ---------------------------------- | -----------: | --------------------------------------------------------------------------------------------------------------------------------------- |
| `@ghatana/canvas`                  | **80 / 100** | Existing canvas history and isolation gates remain. Need schema/migration/serialization and deep interaction E2E proof.                 |
| `@ghatana/ui-builder`              | **78 / 100** | Canonical model and NodeId validation path remain strong. Need provenance and durable builder persistence.                              |
| `@ghatana/ds-generator`            | **82 / 100** | DS golden gate now includes additional document/targets, canonical states/aliases, and contrast tests in root script.                   |
| `@ghatana/ghatana-studio`          | **82 / 100** | Stronger production-profile/persistence/acquisition gates, but phase-8 script mismatch risk and backend proof remain.                   |
| Artifact compiler/decompiler       | **78 / 100** | AST-aware diff and generated TS validation exist. Need full build/lint/test/generated-project validation and backend repo intelligence. |
| Wider platform/product interaction | **78 / 100** | Much broader phase-8 with plugin/product interaction and polyglot adapter checks. Need prove all commands execute.                      |
| Release readiness                  | **75 / 100** | `check:release-gate` now centralizes phase-8 plus Data Cloud release and product release checks. Needs end-to-end evidence from CI run. |

---

## E. Detailed Findings

### `package.json`

**Finding:** The release readiness surface is significantly wider. `check:phase8` now includes artifact, Studio, product interaction, plugin, polyglot, lifecycle, governance, accessibility, i18n, AI governance, runtime failure, atomic workflow, and OpenAPI release checks. `check:world-class-platform-readiness` now maps to `check:release-gate`. 

**Risk:** The same `check:phase8` command references Studio scripts that are not visibly defined in the inspected root script block: `check:studio-deep-interactions`, `check:studio-workflow-persistence-contracts`, `check:studio-source-acquisition-worker`. 

**Required change:** Add a script-existence validation gate that checks every `pnpm check:*` referenced inside aggregate gates resolves to a root script.

**Priority:** P0.

---

### `scripts/check-studio-production-profile.mjs`

**Finding:** Strong production-profile validator. It enforces deployment profile, production acquisition, kernel-backed acquisition, kernel workflow persistence, HTTPS API base URL, required tenant/workspace/project/auth values, and `VITE_STUDIO_REQUIRE_KERNEL_WORKFLOW_PERSISTENCE=true` in production. 

**Risk:** Root command currently runs staging/no-strict mode, so production correctness may only be covered by tests, not by a real production env file.

**Required change:** Validate `.env.production.example` or production deployment metadata in strict production mode.

**Priority:** P0/P1.

---

### `platform/typescript/artifact-compiler-ts/src/validate/generated-artifacts.ts`

**Finding:** Adds deterministic TypeScript generated-source validation with virtual compiler host and diagnostic mapping to `ValidationFinding`. 

**Risk:** It validates TypeScript semantics in-memory, not bundler/runtime/lint/test behavior.

**Required change:** Extend validation pipeline to include bundler build, lint, tests, preview render, and artifact export checks in backend/CI.

**Priority:** P1.

---

### `platform/typescript/ghatana-studio/src/routes/ImportDecompilePage.tsx`

**Finding:** Strong import orchestration remains: provider resolution, acquisition status, decompile, residuals, Builder projection, compile, re-import, round-trip diff, evidence pack, and workflow persistence. 

**Risk:** The production behavior still depends on env selection.

**Required change:** In production mode, fail closed if production acquisition and kernel persistence are not enabled.

**Priority:** P0/P1.

---

### `platform/typescript/ghatana-studio/src/providers/source-acquisition.ts`

**Finding:** Production registry and production acquisition client remain. `resolveProviderRegistryForEnv()` toggles production acquisition based on env. 

**Risk:** Client-side archive/repo acquisition is not the ideal production architecture.

**Required change:** Add server-side source acquisition worker/service and treat browser acquisition as local/dev/fallback.

**Priority:** P1.

---

### `platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts`

**Finding:** Kernel workflow persistence client exists and can persist workflow state and evidence packs through kernel API endpoints. 

**Risk:** Server implementation not verified here.

**Required change:** Add server handlers and contract tests; add evidence immutability and retention policy.

**Priority:** P1.

---

### `platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts`

**Finding:** AST signatures and import graph parity are implemented. 

**Risk:** Still not a full semantic/executable proof.

**Required change:** Attach generated validation pipeline results into the round-trip evidence pack.

**Priority:** P1.

---

## F. Recommended Implementation Plan

### Phase 1 — Fix aggregate gate integrity

**Goal:** Make `check:phase8`, `check:release-gate`, and `check:world-class-platform-readiness` actually executable.

Tasks:

* Add script that parses aggregate check commands and verifies every referenced `pnpm check:*` is defined.
* Define or remove `check:studio-deep-interactions`, `check:studio-workflow-persistence-contracts`, and `check:studio-source-acquisition-worker`.
* Run `pnpm check:phase8` cleanly.

### Phase 2 — Strict production profile proof

**Goal:** Ensure production Studio cannot run with local/fallback acquisition or persistence.

Tasks:

* Add `.env.production.example` validation.
* Run `check-studio-production-profile.mjs --mode=production --env-file=.env.production.example`.
* Fail release if production acquisition or kernel persistence is missing.

### Phase 3 — Server-side acquisition and persistence

**Goal:** Move production source acquisition and evidence storage out of the browser path.

Tasks:

* Add kernel/studio API endpoints for source acquisition jobs.
* Add workflow-state and workflow-evidence server handlers.
* Add tenant/workspace/project scoping and idempotency.
* Add audit events and retention policy.

### Phase 4 — Deep generated artifact validation

**Goal:** Validate generated artifacts like real code.

Tasks:

* Extend `validateGeneratedArtifacts()` with build/lint/test stages in CI.
* Add generated project fixture.
* Add bundler validation and preview render validation.
* Store validation results in evidence pack.

### Phase 5 — Deep Studio interaction E2E

**Goal:** Add back deep canvas/builder interactions without brittle selectors.

Tasks:

* Define stable Canvas node test IDs.
* Define stable Builder node/property test IDs.
* Add Playwright test for canvas move, selection, builder prop edit, preview update, and fidelity diff update.

---

## G. Exact TODOs

* [x] `package.json` — define or remove `check:studio-deep-interactions`.

  * Priority: P0.

* [x] `package.json` — define or remove `check:studio-workflow-persistence-contracts`.

  * Priority: P0.

* [x] `package.json` — define or remove `check:studio-source-acquisition-worker`.

  * Priority: P0.

* [x] `scripts/` — add aggregate-gate script resolver to fail when aggregate checks reference undefined scripts.

  * Priority: P0.

* [x] `scripts/check-studio-production-profile.mjs` — wire strict production env validation into root/release gate.

  * Priority: P0/P1.

* [x] `platform/typescript/ghatana-studio/src/state/artifactWorkflowStore.ts` — add server-side endpoint contract tests for workflow/evidence persistence.

  * Priority: P1.

* [x] `platform/typescript/ghatana-studio/src/providers/source-acquisition.ts` — move production GitHub/GitLab/archive acquisition behind backend worker/service.

  * Priority: P1.

* [x] `platform/typescript/artifact-compiler-ts/src/validate/generated-artifacts.ts` — extend validation pipeline beyond in-memory TypeScript diagnostics.

  * Priority: P1.

* [x] `platform/typescript/artifact-compiler-ts/src/diff/roundtrip-diff.ts` — include generated validation results and graph parity detail in diff/evidence output.

  * Priority: P1.

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
pnpm check:studio-production-profile
pnpm check:canvas-history
pnpm check:builder-canonical-document
pnpm check:builder-canvas-adapter
pnpm check:ds-generator-golden

pnpm check:phase8
pnpm check:release-gate
pnpm check:world-class-platform-readiness
```

Focused validations:

```bash
node ./scripts/check-studio-production-profile.mjs --mode=production --env-file=.env.production.example

pnpm --dir platform/typescript/artifact-compiler-ts exec vitest run \
  src/__tests__/generated-validation.test.ts \
  src/__tests__/roundtrip-diff.test.ts \
  src/__tests__/repository-scan.test.ts

pnpm --dir platform/typescript/ghatana-studio exec playwright test e2e/artifact-workflow.spec.ts
```

---

## Final Verdict

```markdown
Final Verdict: Partial

Are these areas feature-complete and correctly implemented for a production-grade, world-class Ghatana product-development platform?

No, but c5388f6712cc3280f7ff71198b595cab9cb1d980 is a major readiness-gate expansion.

Reason:
The snapshot adds a much broader phase-8/release-gate surface, generated artifact TypeScript validation, Studio production profile validation, deeper DS golden coverage, and wider product/plugin/polyglot/release checks. However, the aggregate phase-8 command appears to reference several Studio checks that are not visibly defined in the inspected root scripts, production profile enforcement is not run in strict production mode by the root command, source acquisition remains client-heavy, workflow/evidence persistence server handlers are not proven, and generated artifact validation is still TypeScript-only rather than full build/lint/test/runtime validation.

Required minimum work before production:
Prove aggregate gates are executable, enforce strict production Studio profile validation, implement server-side acquisition and workflow/evidence persistence, add full generated artifact build/lint/test validation, and add deep Canvas/Builder interaction E2E.

Recommended next milestone:
Executable Release Gate + Server-Side Artifact Acquisition/Persistence v1.

Recommended first implementation PR:
Add an aggregate script resolver that fails if any `pnpm check:*` referenced inside `check:phase8`, `check:release-gate`, or `check:world-class-platform-readiness` is undefined, then fix the missing Studio check scripts.

Recommended parallel workstreams:
1. Aggregate gate integrity.
2. Strict production profile validation.
3. Server-side source acquisition worker.
4. Kernel workflow/evidence persistence endpoints.
5. Generated artifact build/lint/test validation.
6. Deep Studio Canvas/Builder interaction E2E.
```
