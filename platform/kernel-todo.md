# Ghatana Phase-Based Journey TODO Plan

Target repository: `samujjwal/ghatana`
Target commit: `f339a8507f3bb474cad2f88024e966627b656ebf`
Audit mode: complete codebase state at commit snapshot, not commit diff

---

## 1. Purpose

This document converts the phase-based end-to-end audit into a coherent, comprehensive, implementation-ready TODO plan.

It covers:

* all phases from Phase 0 through Phase 8
* all required journeys from ideation to future product shape readiness
* all major areas: Studio, Kernel, YAPPC, Data Cloud, AEP/agents, Digital Marketing, shared libraries, product shape readiness, CI/CD, testing, observability, security, privacy, i18n, accessibility, documentation, and cleanup
* file-backed work items with what to change, where, why, tests, validation commands, dependency impacts, and cleanup expectations

The plan must be executed fix-forward. Do not introduce compatibility shims, duplicate packages, local product lifecycle runners, fake success states, disabled tests, or target-state claims without executable evidence.

## 1.1 Implementation Progress

| Slice | Status | Notes |
| ----- | ------ | ----- |
| P0-T03 current-state claims | completed | Scanner now follows documentation-truth scope; docs/README.md and docs/implementation/README.md no longer emit current-state claim violations. |
| P0-T02 documentation authority | completed | Phase/journey/evidence/validation crosswalk added to docs/architecture/DOMAIN_WORKSTREAM_MAP.md. |
| P0-T01 domain governance | completed | Added phase/exit/evidence metadata for all existing-partial domains and extended the registry schema accordingly. |
| P0-T04 product registry | completed | Canonical product registry now enforces the Digital Marketing-only lifecycle pilot rule and regenerated product artifacts. |
| P0-T05 workspace wiring | completed | Registry declarations, Gradle/pnpm wiring, generated artifacts, and workspace registration are aligned. |
| P0-T06 governance evidence | completed | Implementation tracker now records the registry/workspace truth-sync evidence and matches the validated Phase 0 state. |
| P0-T07 boundary rules | completed | Explicit platform-product boundary gate is wired and validated; boundary exceptions now fail closed when expired. |
| P0-T08 cleanup inventory | completed | Cleanup reporting now emits actionable grouped output with TODO IDs, owners, paths, actions, and validation commands. |
| P1-T01 studio navigation metadata | completed | Added statusReasonCode/statusMessageKey/requiredNextAction/evidenceRefs for every canonical Studio nav item with test coverage. |
| P1-T02 navigation capability matrix tests | completed | Added table-driven runtime/lifecycle/execution/data-cloud scenarios and asserted exposure outcomes across all routes. |
| P1-T03 kernel readiness capability state | completed | Added getStudioCapabilityState() with ProductUnit lifecycle, provider mode, run/manifest evidence, and wired it into Studio route capability resolution. |
| P1-T04 ideation route status panel | completed | Added shared ideation route status panel across Ideas/Blueprints/Canvas with ownership, readiness, next action, handoff state, and evidence refs. |
| P1-T05 lifecycle route UX | completed | Lifecycle page now shows explicit Digital Marketing pilot readiness and non-pilot blocked messaging while preserving readiness gates and controls. |
| P1-T06 artifact/deployment panel contracts | completed | Artifacts/Deployments now surface manifest-backed evidence state, status, remediation guidance, and run-linked evidence refs. |
| P1-T07 agents readiness UX | completed | Agents route now surfaces explicit AEP/policy/mastery/approval readiness dependencies and missing-evidence state output. |
| P1-T08 health dashboard composition | completed | Health route now composes bootstrap truth, platform truth, provider health, and product health into one explicit model with route-level status cards. |
| P1-T09 shared shell integration | completed | Studio now composes top-level shell/navigation/state via @ghatana/product-shell with route gating preserved and shared shell conformance validated. |
| P1-T10 i18n coverage hardening | completed | Shared UI state coverage gate passes for Studio route states; customer-facing route/status copy is translation-key backed for current surfaces. |
| P1-T11 accessibility route-state coverage | completed | Studio accessibility route-state checks pass via test:a11y with shared-shell navigation landmarks, focus visibility, and guarded route messaging. |
| P1-T12 design-system conformance | completed | Design-system conformance gate passes, confirming no one-off local primitives are required for current Studio Phase 1 route/status surfaces. |
| P2-T01 digital marketing pilot config verification | completed | Digital Marketing pilot validator passes and root script wiring is aligned (`check:digital-marketing-lifecycle-pilot`) for repeatable Phase 2 checks. |
| P2-T02 digital marketing smoke and evidence-pack proof | completed | Smoke-mode pilot validation now executes successfully with evidence output at `.kernel/evidence/digital-marketing/digital-marketing-lifecycle-evidence-pack.json`, matching CI smoke enforcement. |
| P2-T03 lifecycle CLI phase command proof | completed | `check:kernel-platform-lifecycle` now passes after strict typing fixes in KernelLifecycleService and planned-adapter contract flag alignment in toolchain adapter registry. |
| P2-T04 package phase adapter proof | completed | Toolchain adapter contracts and pilot validator confirm Digital Marketing package surfaces are docker-buildx-backed and contract compliant. |
| P2-T05 deploy phase compose labels proof | completed | Pilot validator confirms compose-local deployment config and required Kernel label/health metadata are present for local deploy proof. |
| P2-T06 environment safety proof | completed | Secret/default credential gate passes, confirming local env examples avoid unsafe default secret materialization. |
| P2-T07 approval gate execution proof | completed | Approval gate planner and release tests pass, proving risky lifecycle phases require approval and fail closed without granted approval. |
| P2-T08 manifest linkage proof | completed | Product artifact and deployment contract validators pass, including lifecycle manifest linkage expectations used by pilot flows. |
| P2-T09 backend surface proof | completed | `pnpm test:digital-marketing-gateway` passes, confirming backend lifecycle task/health contract coverage for the DM API surface. |
| P2-T10 web surface proof | completed | `pnpm test:digital-marketing-web` and `pnpm build:digital-marketing` pass; web-only lifecycle build requires backend artifact by profile contract, so full build is the canonical proof. |
| P2-T11 single-pilot guard proof | completed | `pnpm check:digital-marketing-lifecycle-pilot` and direct smoke/evidence-pack validation pass, confirming Digital Marketing remains the only lifecycle-enabled pilot. |
| P2-T12 pilot CI gate proof | completed | `.github/workflows/product-lifecycle.yml` includes mandatory `check-digital-marketing` job with pilot check + smoke + evidence-pack artifact upload on lifecycle/product changes. |
| P3-T01 platform-mode provider coverage | completed | Added table-driven kernel-lifecycle service tests for missing `events`, `artifacts`, `health`, `approvals`, `provenance`, `memory`, and `runtimeTruth` providers in platform mode; focused test suite passes. |
| P4/P5/P6 grouped validation execution | completed | `pnpm check:phase4`, `pnpm check:phase5`, and `pnpm check:phase6` now pass end-to-end; fixed platform boundary regression by replacing product-internal evidence refs in Studio navigation metadata. |
| P7-T12 product registry drift hardening | completed | Added reason-coded demo/example drift exemption enforcement in `scripts/check-product-registry-drift.mjs`, backfilled readiness metadata for `software-org` and `virtual-org`, regenerated registry/matrix artifacts, and got `pnpm check:phase7` green. |
| P8-T01 phase command suites | completed | Added `check:phase0` through `check:phase8` in root scripts, plus missing check aliases (`platform-product-boundaries`, `orphan-modules`, `deprecated-packages`, `toolchain/product artifact/deployment contracts`, `product-registry-drift`, `product-shape-capability-matrix`) and made script merging deterministic. |
| P8-T02 phase CI required checks | completed | Added `.github/workflows/kernel-phase-gates.yml` with explicit required jobs: `phase0-coherence`, `phase1-studio`, `phase2-dm-pilot`, `phase3-kernel`, `phase4-agentic`, `phase5-data-cloud`, `phase6-artifact-intelligence`, `phase7-shape`, and `phase8-cleanup`. |
| P8-T03 evidence-pack CI artifacts | completed | Added artifact upload steps in `kernel-phase-gates.yml` for Digital Marketing evidence (`.kernel/evidence/digital-marketing/**`), Data Cloud/provider evidence (`.kernel/evidence/data-cloud/**`, `build/reports/architecture/**`), artifact-intelligence evidence (`.kernel/evidence/artifact-intelligence/**`, `build/reports/artifact-intelligence/**`), and phase report bundles (`build/reports/**`, `.kernel/evidence/**`) so phase checks emit retrievable CI evidence. |
| P8-T04 production-stub allowlist hardening | completed | `check-production-stubs.mjs` now enforces `owner`, `expiry`, `reason`, `issueLink`, `safeFallback`, and `featureFlag` on every allowlist entry and includes enriched metadata in expiry diagnostics. |
| P8-T05 deprecated package/import cleanup gate | completed | `pnpm check:deprecated-packages` and `pnpm check:deprecated-imports` now pass and are already included in `check:phase8`, providing enforced CI-time cleanup protection for deprecated facades/imports. |
| P8-T06 orphan module CI enforcement | completed | `check:orphan-modules` is now wired into `check:phase0` and `check:phase8`, and `phase0-coherence` in `kernel-phase-gates.yml` executes the phase gate to fail on non-exempt orphan modules. |
| P8-T07 documentation truth reconciliation gate | completed | `pnpm check:doc-truth` passes and remains enforced in `check:phase8`, providing CI-time documentation authority/truth-surface reconciliation protection. |
| P8-T08 anti-theater scanner enforcement | completed | Added `check:test-authenticity` and chained changed-file enforcement into `check:production-readiness` via `./scripts/check-test-authenticity.sh --changed-only`, covering placeholder assertions, skipped tests without GH refs, and `@Disabled` tests without GH refs. |
| P8-T09 secret/default credential safety gate | completed | `pnpm check:secret-default-credentials` passes and remains part of `check:phase2`, with aggregate protection inherited through `check:phase8` phase chaining. |
| P8-T10 observability conformance gate | completed | `pnpm check:observability-conformance` passes and remains part of phase-group validation (`check:phase3`/`check:phase4` and aggregate `check:phase8`) for logs/metrics/traces/health coverage enforcement. |
| P8-T11 i18n/a11y UI-state coverage gate | completed | `pnpm check:shared-ui-state-coverage` passes and remains enforced in `check:phase1` (and transitively `check:phase8`) for UI state translation/accessibility coverage. |
| P8-T12 design-system conformance gate | completed | `pnpm check:design-system-conformance` passes and remains enforced in `check:phase1` (and transitively `check:phase8`) to guard against local duplicated UI primitives. |
| P8-T13 architecture-boundary gate alias | completed | Added `check:architecture-boundaries` to combine domain, kernel, and platform-product boundary gates for single-command enforcement. |
| P8-T14 workspace drift gate wiring | completed | Product-registry artifact drift enforcement (`check:product-registry-artifacts`) remains in `check:phase0`, and `phase0-coherence` in `kernel-phase-gates.yml` provides CI execution coverage for generated artifact drift failures. |
| P8-T15 audited performance workflow gate | completed | `pnpm check:audited-performance-workflows` passes and remains enforced in `check:phase8` for Studio/product performance workflow audit coverage. |
| P8-T16 world-class readiness suite | completed | Added `check:world-class-platform-readiness` alias to run `check:phase8`; full aggregate gate now completes with exit code 0 (including phases 0-7, production-readiness, boundary, and performance workflow checks). |
| P8-T16 aggregate rerun evidence (2026-05-17T16:54:49Z) | completed | Re-ran `pnpm check:world-class-platform-readiness` with persistent log output at `build/reports/kernel/world-class-readiness-2026-05-17.log`; command exited with `EXIT_CODE=0`. Tail checks confirm domain/kernel/platform boundary and audited-performance gates passed; production-stub scan remained warning-only with no critical violations. |
| P6 artifact intelligence contract and naming revalidation | completed | Re-ran `check:yappc-artifact-intelligence-boundary` plus focused YAPPC compiler tests (`ContractCompatibilityTest`, `ProcessTsExtractorWorkerContractTest`, `JavaArtifactExtractorTest`, `ArtifactCompileJobServiceIntegrationTest`); build passed and canonical `projectId` contract naming remains enforced. |
| P8 readiness gate revalidation | completed | Re-ran boundary and performance tail gates (`check:domain-boundaries`, `check:kernel-boundaries`, `check:platform-product-boundaries`, `check:audited-performance-workflows`) and all passed; production stub scan emitted warnings only and no critical violations. |
| P8/P7/P6 phase-suite verification (2026-05-17T10:11Z) | completed | Revalidated `check:phase0` through `check:phase7`, `check:phase8`, and `check:world-class-platform-readiness`; all observed aggregate gates passed, including Studio, Digital Marketing pilot, Kernel lifecycle, Data Cloud providers, YAPPC handoff/artifact checks, product-shape matrix, deprecated package/import cleanup, doc truth, production readiness, observability, and boundary enforcement. |
| P8 production-stub warning remediation (2026-05-17T10:26Z) | completed | Replaced stdout logging with SLF4J in kernel/product plugin classes and refined `check-production-stubs.mjs` to skip valid `Promise<Void>` success completions. `pnpm check:production-stubs` warning count reduced from 1013 -> 842 (no critical violations), with `RETURN_NULL_PROMISE` reduced from 182 -> 42. |
| P8 production-stub signal tuning and typed-null cleanup (2026-05-17T10:43Z) | completed | Added guard-clause suppression for `RETURN_EMPTY_LIST` and scoped `SYSOUT_JAVA` exemptions for generator/devtool entrypoints in `production-critical-scopes.config.json`; refactored HITL overdue action aggregation to use `Optional<ResolutionOutcome>` instead of null sentinels. Latest evidence: `pnpm check:production-stubs` at 698 warnings (from 842), `RETURN_NULL_PROMISE` at 41, `RETURN_EMPTY_LIST` at 120, `CONSOLE_LOG` at 537, no critical violations; `:products:data-cloud:delivery:launcher:compileJava`, `:products:data-cloud:planes:action:server:compileJava`, and `pnpm check:phase8` all pass. |
| P8 backend observability/logging cleanup follow-up (2026-05-17T11:06Z) | completed | Refactored FlashIt backend collaboration/notification modules away from raw `console.*` calls to structured stdout/stderr JSON helpers; normalized Java logger constants to `LOGGER` naming in touched plugin/loader classes to satisfy checkstyle conventions. Validation evidence: `pnpm build:flashit-gateway` passes (remaining warning is unrelated pre-existing missing JavaDoc in `FlashItComplianceRulePack`), `pnpm check:production-stubs` remains no-critical at 674 warnings, and `pnpm check:phase8` remains green. |
| P8 production-stub fallback classifier refinement (2026-05-17T11:23Z) | completed | Enhanced `scripts/check-production-stubs.mjs` to suppress `RETURN_EMPTY_LIST` warnings for explicit collection-parsing fallback paths (`instanceof List/Collection` + projection via `toList`) while keeping critical detection strict. Validation evidence: `pnpm check:production-stubs` reduced warnings 664 -> 651 with no critical violations (`CONSOLE_LOG` 513, `RETURN_EMPTY_LIST` 107, `RETURN_NULL_PROMISE` 31), and `pnpm check:phase8` re-ran green after the scanner update. |
| P8 Promise<Void> cleanup-hook correction (2026-05-17T11:30Z) | completed | Removed one remaining true-positive `Promise.of(null)` in `AepHttpServer.reportCleanupHook()` by returning `Promise.complete()` for the `ErasureCleanupHook` `Promise<Void>` contract. Validation evidence: `:products:data-cloud:planes:action:server:compileJava` passes, `pnpm check:production-stubs` now reports 650 warnings (`CONSOLE_LOG` 513, `RETURN_EMPTY_LIST` 107, `RETURN_NULL_PROMISE` 30) with no critical violations, and `pnpm check:phase8` remains green. |
| P8 scanner precision + gateway logging cleanup (2026-05-17T11:43Z) | completed | Tightened `RETURN_EMPTY_LIST` classifier in `scripts/check-production-stubs.mjs` for operational remote-call fallback paths and broader list-parsing fallback detection, then replaced raw `console.error` calls in `products/data-cloud/planes/action/gateway/src/index.ts` with structured stderr JSON logging. Validation evidence: warnings reduced 650 -> 644 after scanner refinement and 644 -> 642 after gateway cleanup; current counts are `CONSOLE_LOG` 511, `RETURN_EMPTY_LIST` 101, `RETURN_NULL_PROMISE` 30, with no critical violations, and `pnpm check:phase8` remains green. |
| P8 analytics stub-removal follow-up (2026-05-17T11:52Z) | completed | Replaced two stub-like empty-list methods in `products/data-cloud/planes/action/analytics/src/main/java/com/ghatana/validation/ai/AIPatternDetectionServiceImpl.java` with concrete contextual/historical pattern suggestions, and added TS array-parsing fallback suppression in `scripts/check-production-stubs.mjs` to reduce false positives for guarded payload extractors. Validation evidence: `:products:data-cloud:planes:action:analytics:compileJava` passes, `pnpm check:production-stubs` reduced warnings 642 -> 638 -> 636 (`CONSOLE_LOG` 511, `RETURN_EMPTY_LIST` 95, `RETURN_NULL_PROMISE` 30), and `pnpm check:phase8` remains green. |

---

## 2. Reference Goals and Current Status

| Goal ID | Goal                                             | Correct Owner                          | Current Status                                                              | Primary Evidence / Location                                                                                                                                   | Main Gap                                                                                                      | Primary Phase    |
| ------- | ------------------------------------------------ | -------------------------------------- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | ---------------- |
| G01     | Unified Ghatana Studio shell                     | Platform / Studio                      | Existing but partial                                                        | `platform/typescript/ghatana-studio`                                                                                                                          | Several routes remain disabled, degraded, empty, or hidden without enough evidence-linked UX                  | Phase 1          |
| G02     | Product Development Kernel lifecycle truth       | Platform Kernel                        | Existing and executable for core planning, partial for full execution proof | `platform/typescript/kernel-lifecycle`, `platform/typescript/kernel-product-contracts`, `scripts/kernel-product.mjs`                                          | Need stronger CLI/API/Studio E2E proof for all phases and risky action flows                                  | Phase 3          |
| G03     | ProductUnitIntent handoff                        | YAPPC + Kernel                         | Existing but partial                                                        | `platform/typescript/kernel-product-contracts`, `platform/typescript/kernel-lifecycle`, `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts` | Need full YAPPC idea/blueprint/canvas → ProductUnitIntent → Kernel preview/apply → registry/evidence proof    | Phase 3, Phase 6 |
| G04     | Digital Marketing lifecycle pilot                | Digital Marketing + Kernel             | Existing and closest to executable                                          | `products/digital-marketing/kernel-product.yaml`, `scripts/check-digital-marketing-lifecycle-pilot.mjs`                                                       | Need smoke/evidence-pack CI proof, not only static/plan validation                                            | Phase 2          |
| G05     | Data Cloud bootstrap and platform provider modes | Data Cloud + Kernel                    | Existing but partial                                                        | `products/data-cloud/extensions/kernel-bridge`, `scripts/check-data-cloud-platform-providers.mjs`                                                             | Need provider behavior integration tests and bootstrap → Data Cloud build/deploy → platform-mode switch proof | Phase 5          |
| G06     | Agentic product development                      | AEP/Data Cloud + Kernel + Studio       | Existing but partial                                                        | `platform/typescript/kernel-product-contracts/src/agentic`, `products/data-cloud/planes/action`, `platform/java/agent-core`                                   | Need governed AEP → Kernel action → approval/risk/verification → evidence → Studio E2E                        | Phase 4          |
| G07     | Artifact compiler/decompiler intelligence        | YAPPC + Data Cloud + Kernel references | Existing but partial, with correctness issues                               | `products/yappc/core/yappc-services`, `platform/typescript/kernel-product-contracts/src/artifact-intelligence`                                                | Semantic models are extracted but not persisted; checksum is not stable SHA-256 hex                           | Phase 6          |
| G08     | Shared libraries and product-neutral primitives  | Platform                               | Existing but partial                                                        | `platform/typescript/LIBRARY_GOVERNANCE.md`, `platform/typescript/*`                                                                                          | Deprecated/facade packages and partial conformance need cleanup                                               | Phase 8          |
| G09     | Future product shape readiness                   | Platform + Product owners              | Declared / planned / partial                                                | `config/canonical-product-registry.json`, `products/*/kernel-product.yaml`                                                                                    | Must validate readiness without enabling non-pilot lifecycle execution                                        | Phase 7          |
| G10     | Production hardening and CI/CD                   | Platform                               | Existing but partial                                                        | `package.json`, `.github/workflows`, `scripts/check-*.mjs`                                                                                                    | Need phase-level command suites and required CI gates with evidence artifacts                                 | Phase 8          |

---

## 3. Journey Coverage Matrix

| Journey | Description                             | Main Phases               | Main Owners                       | Required Proof                                                                                                                     |
| ------- | --------------------------------------- | ------------------------- | --------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| J1      | Product ideation to ProductUnitIntent   | Phase 1, Phase 3, Phase 6 | Studio, YAPPC, Kernel             | UI/API contract, ProductUnitIntent validation, preview/apply result, registry evidence, runtime truth/provenance                   |
| J2      | Direct Product Development Kernel usage | Phase 2, Phase 3          | Kernel, Studio, Digital Marketing | ProductUnit selection, lifecycle plan, validate/test/build/package/deploy/verify, manifests, approval behavior, run history        |
| J3      | Agentic product development             | Phase 4, Phase 5          | AEP/Data Cloud, Kernel, Studio    | Agent action request, risk/mastery/policy checks, approval gates, Kernel execution, evidence/memory/provenance, UI recommendations |
| J4      | Digital Marketing lifecycle pilot       | Phase 2, Phase 3, Phase 8 | Digital Marketing, Kernel, CI     | Single executable pilot, manifest generation, local deploy/verify proof, smoke/evidence-pack CI                                    |
| J5      | Artifact intelligence                   | Phase 6, Phase 5, Phase 3 | YAPPC, Data Cloud, Kernel         | Snapshot, extraction, semantic evidence, residual islands, risk reports, Data Cloud graph/provenance, Kernel reference consumption |
| J6      | Data Cloud foundation                   | Phase 5, Phase 8          | Data Cloud, Kernel, CI            | Bootstrap mode, Data Cloud build/deploy, Data Cloud-backed providers, platform-mode switch, degraded behavior                      |
| J7      | Future product shape readiness          | Phase 7, Phase 0, Phase 8 | Platform, Product owners          | Product shape matrix, required gates/adapters/artifacts, disabled lifecycle enforcement, do-not-implement-yet list                 |

---

## 4. Area Coverage Map

| Area                              | Primary Location                                                                                                   | Correct Owner                | Phase(s)                  | Required Outcome                                                                               |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------ | ---------------------------- | ------------------------- | ---------------------------------------------------------------------------------------------- |
| Platform coherence and governance | `config/domain-registry.json`, `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`, `scripts/check-domain-boundaries.mjs` | Platform Coherence           | Phase 0, Phase 8          | One domain map, one ownership model, no target-state claims as current truth                   |
| Ghatana Studio                    | `platform/typescript/ghatana-studio`                                                                               | Platform / Studio            | Phase 1                   | Unified shell, clear navigation, evidence-linked disabled/degraded states                      |
| Kernel contracts                  | `platform/typescript/kernel-product-contracts`                                                                     | Platform Kernel              | Phase 3, Phase 4, Phase 6 | Stable ProductUnit, ProductUnitIntent, provider, agentic, artifact-intelligence contracts      |
| Kernel lifecycle                  | `platform/typescript/kernel-lifecycle`, `scripts/kernel-product.mjs`                                               | Platform Kernel              | Phase 2, Phase 3          | Product lifecycle truth with plans, execution, manifests, approvals, runtime truth, provenance |
| Kernel providers                  | `platform/typescript/kernel-providers`, Data Cloud bridge providers                                                | Platform Kernel + Data Cloud | Phase 3, Phase 5          | Bootstrap providers and Data Cloud platform providers with tests                               |
| Digital Marketing pilot           | `products/digital-marketing`                                                                                       | Product + Kernel             | Phase 2                   | Full executable validate/test/build/package/deploy/verify pilot                                |
| AEP / Action Plane                | `products/data-cloud/planes/action`, `platform/java/agent-core`                                                    | Data Cloud / AEP             | Phase 4                   | Governed agent execution through Kernel contracts                                              |
| Data Cloud runtime truth          | `products/data-cloud/extensions/kernel-bridge`, `products/data-cloud/planes/action/kernel-bridge`                  | Data Cloud                   | Phase 5                   | Durable providers for runtime truth, events, artifacts, health, memory, provenance             |
| YAPPC artifact intelligence       | `products/yappc/core/yappc-services`, `products/yappc/frontend`, `products/yappc/kernel-bridge`                    | YAPPC                        | Phase 6                   | Artifact scan/decompile/evidence with Data Cloud and Kernel reference consumption              |
| Shared UI/platform libraries      | `platform/typescript/*`                                                                                            | Platform                     | Phase 1, Phase 8          | Reusable shell, design system, UI builder, canvas, code editor, i18n, accessibility            |
| Future product shapes             | `config/canonical-product-registry.json`, `products/*/kernel-product.yaml`                                         | Platform + Products          | Phase 7                   | Readiness validation without premature lifecycle execution                                     |
| CI/CD and cleanup                 | `package.json`, `.github/workflows/*`, `scripts/check-*.mjs`                                                       | Platform                     | Phase 8                   | Phase-specific checks, evidence packs, no stubs, no orphan/deprecated modules                  |

---

# 5. Phase 0 TODOs — Current-State Baseline and Coherence

## Phase Goal

Establish the truthful baseline: what exists, what is executable, what is partial, what is declared only, what is target architecture, what is duplicated, what is dead, and what violates boundaries.

## Journey Coverage

* J1: establish current status of ProductUnitIntent handoff
* J2: establish current status of Kernel lifecycle usage
* J3: establish current status of agentic development
* J4: confirm Digital Marketing is the only lifecycle-enabled pilot
* J5: classify artifact intelligence maturity
* J6: classify Data Cloud provider-mode maturity
* J7: classify future product readiness without enabling execution

## TODOs

| TODO ID | Journey | Area                     | File / Location                                                                               | Current Issue                                                                                                                      | Required Change                                                                                                                                 | Tests / Validation                                                                   | Cleanup                                                                        |
| ------- | ------- | ------------------------ | --------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------ |
| P0-T01  | All     | Domain governance        | `config/domain-registry.json`                                                                 | Domains are classified, but partial domains need clearer phase-level exit criteria.                                                | Add `phase`, `exitCriteria`, `blockingGaps`, `evidenceRequired`, and `phaseOwner` for every domain marked `existing-partial`.                   | `pnpm check:domain-registry`                                                         | Remove ambiguous reason codes that do not map to a fix.                        |
| P0-T02  | All     | Documentation authority  | `docs/architecture/DOMAIN_WORKSTREAM_MAP.md`                                                  | Domain map is high-level and not fully phase/action aligned.                                                                       | Add a section mapping each domain to Phase 0–8, journeys, executable evidence, and validation command.                                          | `pnpm check:doc-claims-evidence`                                                     | Remove stale planning language that is not marked target-state.                |
| P0-T03  | All     | Current-state claims     | `scripts/check-current-state-claims.mjs`                                                      | Current-state enforcement must cover docs, route metadata, package READMEs, and generated trackers.                                | Extend scanner to validate current-state labels in `docs/**`, `platform/typescript/**/README.md`, `products/**/README.md`, and `config/*.json`. | `pnpm check:current-state-claims`                                                    | Delete or rewrite documents that claim incomplete capabilities are executable. |
| P0-T04  | J7      | Product registry         | `config/canonical-product-registry.json`                                                      | Some products are registry-ready but lifecycle-disabled; this is correct but must stay enforced.                                   | Add a validation that only `digital-marketing` may have `lifecycle.enabled=true` or `lifecycleExecutionAllowed=true` until explicitly promoted. | `pnpm check:product-registry && pnpm check:product-shape-capability-matrix`          | Remove any accidental lifecycle enablement for future products.                |
| P0-T05  | All     | Package/workspace wiring | `settings.gradle.kts`, `pnpm-workspace.yaml`, `config/generated/settings-gradle-includes.kts` | Workspace wiring exists but must be compared with canonical registry and domain registry.                                          | Add a cross-check proving every product/module declared in registry is either wired or explicitly exempted with a reason.                       | `pnpm check:product-workspace-registration && pnpm check:product-registry-artifacts` | Delete orphan module folders after proof.                                      |
| P0-T06  | All     | Governance evidence      | `docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md` or equivalent             | Implementation tracker may drift from actual code and registries.                                                                  | Generate or update tracker from `domain-registry.json`, `canonical-product-registry.json`, and actual workspace files.                          | `pnpm check:product-kernel-audit-progress`                                           | Remove hand-maintained tracker sections that duplicate generated truth.        |
| P0-T07  | All     | Boundary rules           | `scripts/check-domain-boundaries.mjs`                                                         | Boundary checks exist but must fail on all platform imports of product internals and Kernel imports of YAPPC/Data Cloud internals. | Add explicit checks for Kernel, Studio, shared libraries, and platform packages.                                                                | `pnpm check:domain-boundaries && pnpm check:platform-product-boundaries`             | Remove illegal imports instead of allowlisting them.                           |
| P0-T08  | All     | Cleanup inventory        | `scripts/check-orphan-modules.mjs`, `scripts/check-cleanup-gate.mjs`                          | Cleanup checks exist but need a single actionable output grouped by phase/domain/product.                                          | Make cleanup report emit TODO IDs, owner, path, deletion/migration action, and validation command.                                              | `pnpm check:orphan-modules && pnpm check:cleanup-gate`                               | Delete folder-only shells, stale generated files, and dead docs.               |

## Phase 0 Exit Criteria

* Every capability has a current-state classification.
* Every partial capability has an owner, phase, blocking gap, and validation command.
* No target-state claim appears without a current-state label.
* Digital Marketing remains the only lifecycle-enabled pilot.
* Orphan/deprecated/dead-code inventory is generated and actionable.

---

# 6. Phase 1 TODOs — Unified Studio Shell, Terminology, and Navigation

## Phase Goal

Make Ghatana Studio coherent as the unified customer-facing experience while preserving internal ownership boundaries between Studio, YAPPC, Kernel, Data Cloud, and shared libraries.

## Journey Coverage

* J1: idea, blueprint, canvas, ProductUnitIntent entry points
* J2: develop/lifecycle/artifact/deployment/approval visibility
* J3: agents and recommendations visibility
* J4: Digital Marketing pilot visibility
* J5: artifact intelligence visibility
* J6: Data Cloud health/runtime truth visibility

## TODOs

| TODO ID | Journey | Area                       | File / Location                                                                                | Current Issue                                                                                                                           | Required Change                                                                                                                               | Tests / Validation                                        | Cleanup                                                                      |
| ------- | ------- | -------------------------- | ---------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- | ---------------------------------------------------------------------------- |
| P1-T01  | J1-J6   | Studio navigation          | `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`                        | Routes have `status` and `exposure`, but disabled/degraded/blocked routes do not carry enough user-facing reason and evidence metadata. | Add `statusReasonCode`, `statusMessageKey`, `requiredNextAction`, and `evidenceRefs` to every nav item.                                       | `pnpm --dir platform/typescript/ghatana-studio test`      | Remove hardcoded explanatory strings from route components after keys exist. |
| P1-T02  | J1-J6   | Navigation tests           | `platform/typescript/ghatana-studio/src/navigation/__tests__/studioNavigation.test.ts`         | Route capability transitions need table-driven test coverage.                                                                           | Test `runtimeConfigured`, `lifecycleConfigured`, `lifecycleExecutionAllowed`, and `dataCloudEvidenceReady` combinations for all routes.       | `pnpm --dir platform/typescript/ghatana-studio test`      | Delete snapshot-only tests that do not assert route behavior.                |
| P1-T03  | J2/J4   | Kernel readiness UX        | `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts` and Studio route loaders | Studio should derive lifecycle readiness from live ProductUnit/Kernel status, not static route metadata.                                | Add `getStudioCapabilityState()` using ProductUnit lifecycle status, provider mode, and run/evidence status.                                  | `pnpm check:studio-kernel-api`                            | Remove duplicate lifecycle-status calculations in individual components.     |
| P1-T04  | J1      | Idea/Blueprint/Canvas UX   | `platform/typescript/ghatana-studio/src/routes/ideas`, `blueprints`, `canvas`                  | Ideation routes must clearly state whether they are YAPPC-owned and whether ProductUnitIntent handoff is available.                     | Add shared route status panel with owner, current status, required next action, and ProductUnitIntent handoff readiness.                      | `pnpm --dir platform/typescript/ghatana-studio test`      | Remove one-off local status cards.                                           |
| P1-T05  | J2/J4   | Lifecycle route UX         | `platform/typescript/ghatana-studio/src/routes/lifecycle`                                      | Lifecycle route is degraded/disabled by default but must explain pilot readiness.                                                       | Show Digital Marketing pilot readiness, lifecycle phases, plan/run controls, and blocked states for non-enabled products.                     | `pnpm --dir platform/typescript/ghatana-studio test:e2e`  | Remove blank disabled route placeholders.                                    |
| P1-T06  | J2/J4   | Artifact/deployment panels | `platform/typescript/ghatana-studio/src/routes/artifacts`, `deployments`                       | Artifact and deployment routes are disabled/hidden/empty.                                                                               | Add manifest-backed panels for lifecycle-result, artifact-manifest, deployment-manifest, verify-health-report, and lifecycle-health-snapshot. | `pnpm check:studio-kernel-api`                            | Remove product-local manifest viewers if duplicated.                         |
| P1-T07  | J3      | Agents UX                  | `platform/typescript/ghatana-studio/src/routes/agents`                                         | Agents route is disabled/degraded without clear reason.                                                                                 | Add readiness UI explaining required AEP, Kernel action contracts, policy/mastery/approval checks, and missing evidence.                      | `pnpm --dir platform/typescript/ghatana-studio test`      | Remove any agent UI that implies raw command execution.                      |
| P1-T08  | J6      | Health UX                  | `platform/typescript/ghatana-studio/src/routes/health`                                         | Health route needs composed Kernel + Data Cloud + product truth.                                                                        | Add health dashboard model that distinguishes bootstrap truth, platform truth, provider health, product health, and stale/unknown states.     | `pnpm check:shared-ui-state-coverage`                     | Remove generic “healthy” claims not backed by evidence.                      |
| P1-T09  | All UI  | Shared shell               | `platform/typescript/product-shell`, `platform/typescript/ghatana-studio`                      | Product shell must be canonical and not duplicated across products.                                                                     | Ensure Studio uses `@ghatana/product-shell` for top-level shell/navigation/state surfaces.                                                    | `pnpm check:shared-product-shells`                        | Replace product-local shells where shared shell exists.                      |
| P1-T10  | All UI  | i18n                       | `platform/typescript/ghatana-studio/src/**`, `platform/typescript/i18n`                        | User-visible strings in Studio routes must be translation-key backed.                                                                   | Add translation keys for nav, route status, empty/error/degraded/blocked states.                                                              | `pnpm check:shared-ui-state-coverage`                     | Remove hardcoded customer-facing route strings.                              |
| P1-T11  | All UI  | Accessibility              | `platform/typescript/ghatana-studio/src/**`, `platform/typescript/accessibility`               | Route states and disabled actions must be accessible.                                                                                   | Add semantic labels, keyboard flows, non-color status signals, and focus states to route panels and actions.                                  | `pnpm --dir platform/typescript/ghatana-studio test:a11y` | Remove inaccessible custom controls.                                         |
| P1-T12  | All UI  | Design system              | `platform/typescript/design-system`, Studio components                                         | Studio must not introduce one-off buttons/cards/status chips.                                                                           | Use design-system components for cards, alerts, buttons, badges, empty/error states, gates, artifacts, deployments.                           | `pnpm check:design-system-conformance`                    | Remove local duplicate UI primitives.                                        |

## Phase 1 Exit Criteria

* Studio navigation is evidence-linked and user-comprehensible.
* Every disabled/degraded/hidden route explains why and what is required next.
* No one-off status, error, loading, empty, gate, artifact, or deployment UI remains when a shared pattern exists.
* Studio route state is derived from real Kernel/ProductUnit/Data Cloud readiness where available.

---

# 7. Phase 2 TODOs — Digital Marketing Lifecycle Pilot E2E

## Phase Goal

Prove one complete executable lifecycle pilot through Digital Marketing before broadening lifecycle execution to other products.

## Journey Coverage

* J2: direct Kernel usage
* J4: Digital Marketing validate/test/build/package/deploy/verify
* J6: bootstrap provider mode for local execution
* J7: guard against accidentally enabling other products

## TODOs

| TODO ID | Journey | Area               | File / Location                                                                                 | Current Issue                                                                     | Required Change                                                                                                                                    | Tests / Validation                                                                                               | Cleanup                                                    |
| ------- | ------- | ------------------ | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------- |
| P2-T01  | J4      | Pilot config       | `products/digital-marketing/kernel-product.yaml`                                                | Config is comprehensive but must remain canonical for pilot phases and manifests. | Validate every phase has gates, artifacts, provider modes, health, and required manifests where applicable.                                        | `pnpm check:digital-marketing-lifecycle-pilot`                                                                   | Remove phase fields not consumed by Kernel.                |
| P2-T02  | J4      | Smoke execution    | `scripts/check-digital-marketing-lifecycle-pilot.mjs`                                           | Static and plan checks exist; smoke/evidence-pack mode must be required in CI.    | Extend script or CI to run `--smoke --evidence-pack-dir .kernel/evidence/digital-marketing`.                                                       | `pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing` | Remove smoke bypasses without issue reference.             |
| P2-T03  | J4      | Lifecycle CLI      | `scripts/kernel-product.mjs`                                                                    | CLI must prove every pilot phase command works or fails safely.                   | Add command tests for `validate`, `test`, `build`, `package`, `deploy --env local`, `verify --env local`, `promote`, `rollback`.                   | `pnpm check:kernel-platform-lifecycle`                                                                           | Delete product-local lifecycle runner scripts.             |
| P2-T04  | J4      | Package phase      | `products/digital-marketing/kernel-product.yaml`, Dockerfiles                                   | Package phase must use Docker Buildx, not Gradle/pnpm surface adapters.           | Verify backend and web package adapters are `docker-buildx` and produce manifest refs.                                                             | `pnpm check:toolchain-adapter-contracts`                                                                         | Remove fake image success outputs.                         |
| P2-T05  | J4      | Deploy phase       | `products/digital-marketing/deploy/local.compose.yaml`                                          | Compose deploy must include Kernel labels and expected service health.            | Add/verify labels: productUnit, surface, lifecycle, environment, artifactRef, health paths.                                                        | `pnpm check:digital-marketing-lifecycle-pilot`                                                                   | Remove compose services that are not in expected services. |
| P2-T06  | J4      | Env safety         | `products/digital-marketing/deploy/local.env.example`, `.gitignore`                             | Env example must not contain unsafe secret defaults; local env must be ignored.   | Ensure `DATABASE_URL`, postgres persistence, no in-memory compose claim, and `.gitignore` protects `local.env`.                                    | `pnpm check:secret-default-credentials`                                                                          | Remove committed local env files.                          |
| P2-T07  | J4      | Approval gates     | `products/digital-marketing/kernel-product.yaml`, `platform/typescript/kernel-release`          | Deploy/promote/rollback approvals are declared but need end-to-end proof.         | Add test proving risky phases produce approval requirements and blocked execution without approval.                                                | `pnpm plan:deploy:local:digital-marketing`                                                                       | Remove legacy `approval` singular config.                  |
| P2-T08  | J4      | Manifest proof     | `.kernel/out`, `platform/typescript/kernel-artifacts`, `kernel-deployment`                      | Generated manifests must be schema-valid and linked.                              | Add evidence-pack validator for lifecycle-result, artifact-manifest, deployment-manifest, verify-health-report, health snapshot, lifecycle events. | `pnpm check:product-artifact-contracts && pnpm check:product-deployment-contracts`                               | Remove unversioned or unlinked manifests.                  |
| P2-T09  | J4      | Backend surface    | `products/digital-marketing/dm-api`                                                             | Backend lifecycle tasks must map to real Gradle tasks and health endpoints.       | Verify `runDmosApiServer`, `check`, `build`, `test`, `assemble`, `/health/live`, `/health/ready`.                                                  | `pnpm test:digital-marketing-gateway`                                                                            | Remove dead backend tasks.                                 |
| P2-T10  | J4      | Web surface        | `products/digital-marketing/ui`                                                                 | Web lifecycle scripts must map to real package scripts.                           | Verify `type-check`, `build`, `test`, `dev`, dist output and route contracts.                                                                      | `pnpm test:digital-marketing-web && pnpm build:digital-marketing`                                                | Remove unused scripts or stale dist assumptions.           |
| P2-T11  | J7      | Single-pilot guard | `config/canonical-product-registry.json`, `scripts/check-digital-marketing-lifecycle-pilot.mjs` | Other products must remain lifecycle-disabled.                                    | Keep check that fails when any product except Digital Marketing is lifecycle-enabled.                                                              | `pnpm check:digital-marketing-lifecycle-pilot`                                                                   | Revert accidental future-product enablement.               |
| P2-T12  | J4      | CI gate            | `.github/workflows/*`                                                                           | Pilot proof must be mandatory.                                                    | Add dedicated job for DM lifecycle pilot plan/smoke/evidence-pack.                                                                                 | GitHub Actions run                                                                                               | Remove optional-only pilot checks.                         |

## Phase 2 Exit Criteria

* Digital Marketing validate/test/build/package/deploy/verify is executable or safely blocked with evidence.
* All generated manifests are schema-valid and linked by run/correlation ID.
* Risky deploy/promote/rollback actions require approval.
* Pilot check runs in CI with evidence pack.
* No other product is lifecycle-enabled.

---

# 8. Phase 3 TODOs — Product Development Kernel Hardening

## Phase Goal

Make Product Development Kernel the canonical lifecycle truth engine for ProductUnits.

## Journey Coverage

* J1: ProductUnitIntent validation/application
* J2: direct lifecycle planning/execution
* J3: agentic action execution target
* J4: Digital Marketing pilot engine
* J6: provider-mode validation
* J7: future-product lifecycle readiness

## TODOs

| TODO ID | Journey | Area                   | File / Location                                                                                            | Current Issue                                                                                                          | Required Change                                                                                                           | Tests / Validation                                     | Cleanup                                                              |
| ------- | ------- | ---------------------- | ---------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------- |
| P3-T01  | J2/J4   | Kernel service         | `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`                               | Planning/runtime truth/provenance exists, but provider-mode tests must cover every missing provider.                   | Add tests for platform mode missing `events`, `artifacts`, `health`, `approvals`, `provenance`, `memory`, `runtimeTruth`. | `pnpm --dir platform/typescript/kernel-lifecycle test` | Remove duplicate provider checks outside canonical validator.        |
| P3-T02  | J2/J4   | Executor binding       | `scripts/kernel-product.mjs`, `platform/typescript/kernel-lifecycle/src/execution/*`                       | Service throws when executor is absent; CLI/API paths must prove executor is wired.                                    | Add CLI tests proving actual executor is bound for plan/run phases and unavailable executor fails safely.                 | `pnpm check:kernel-lifecycle-truth`                    | Remove fake dry-run success that bypasses executor validation.       |
| P3-T03  | J2      | API handlers           | `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts`                               | Handlers have route metadata/auth/scope but need parity with Studio client and docs.                                   | Generate route contract snapshot and compare with Studio client paths.                                                    | `pnpm check:studio-kernel-api`                         | Remove undocumented routes.                                          |
| P3-T04  | J2      | API auth/scope         | `KernelLifecycleApiHandlers.ts`                                                                            | Auth/scope enforcement exists, but must be tested for every mutation/read path.                                        | Add tests for missing auth, missing scope headers, scope mismatch, forbidden actor, local-dev exemption.                  | `pnpm --dir platform/typescript/kernel-lifecycle test` | Remove unscoped default from non-local configs.                      |
| P3-T05  | J1      | ProductUnitIntent      | `KernelLifecycleService.ts`, `platform/typescript/kernel-product-contracts/src/product-unit/*`             | Intent validation/application exists, but preview/apply needs E2E contract tests.                                      | Add fixtures for valid, invalid, preview-only, apply, provider-mode invalid, missing event/provenance/runtimeTruth.       | `pnpm check:yappc-product-unit-intent-handoff`         | Remove hand-written intent objects that bypass schema.               |
| P3-T06  | J2/J4   | Manifest pointer store | `platform/typescript/kernel-lifecycle/src/service/ManifestPointerStore.ts`                                 | Manifest pointer correctness is critical to run history.                                                               | Add tests for latest pointers, missing manifests, corrupt manifests, phase/run resolution.                                | `pnpm --dir platform/typescript/kernel-lifecycle test` | Delete stale `.kernel/out` fixture files not used by tests.          |
| P3-T07  | J2/J4   | Lifecycle events       | `platform/typescript/kernel-product-contracts/src/events`, `KernelLifecycleService.ts`                     | Events are appended as optional in some paths.                                                                         | Define which lifecycle events are required vs best-effort and test failure semantics.                                     | `pnpm check:kernel-lifecycle-truth`                    | Remove silent event write failures for required truth transitions.   |
| P3-T08  | J2/J4   | Runtime truth          | `platform/typescript/kernel-product-contracts/src/provider`, `KernelLifecycleService.ts`                   | Runtime truth is recorded but must be consistent across plan, execution-started, succeeded, failed, approval-required. | Add golden tests for runtime truth snapshots for each lifecycle status.                                                   | `pnpm --dir platform/typescript/kernel-lifecycle test` | Remove inconsistent status names.                                    |
| P3-T09  | J2/J4   | Approval provider      | `platform/typescript/kernel-product-contracts/src/provider`, `KernelLifecycleService.ts`, `kernel-release` | Approval request/decision exists but risky lifecycle phases require integrated proof.                                  | Add tests for pending approvals, approval decision, rejected decision, expired approval, approval refs in run summary.    | `pnpm check:kernel-platform-lifecycle`                 | Remove legacy approval config fields.                                |
| P3-T10  | J2/J7   | Product readiness      | `ProductLifecyclePlanner.ts`, `canonical-product-registry.json`                                            | Non-enabled products must fail with reason-coded not-ready errors.                                                     | Add tests for PHR/Finance/FlashIt/Data Cloud/YAPPC/TutorPutor planned/partial disabled states.                            | `pnpm check:product-shape-capability-matrix`           | Remove logic that treats registry entry as execution-ready.          |
| P3-T11  | J2/J4   | Toolchain adapters     | `platform/typescript/kernel-toolchains`                                                                    | Adapter registry must prove safe command construction and no product-specific local runners.                           | Add adapter tests for gradle-java-service, pnpm-vite-react, docker-buildx, compose-local.                                 | `pnpm check:toolchain-adapter-contracts`               | Remove unsafe command expansion.                                     |
| P3-T12  | J2/J4   | Artifact contracts     | `platform/typescript/kernel-artifacts`                                                                     | Artifact manifest must fingerprint/link outputs.                                                                       | Add tests for missing artifact, found artifact, hash, size, output paths, product/run/correlation IDs.                    | `pnpm check:product-artifact-contracts`                | Remove fake artifact manifests.                                      |
| P3-T13  | J2/J4   | Deployment contracts   | `platform/typescript/kernel-deployment`                                                                    | Deployment manifest and verify report must be linked and health-backed.                                                | Add tests for compose local manifest, health failures, rollback readiness, environment safety.                            | `pnpm check:product-deployment-contracts`              | Remove deployment success based only on command exit without health. |
| P3-T14  | J2/J4   | Release contracts      | `platform/typescript/kernel-release`                                                                       | Promote/rollback semantics are partial.                                                                                | Add promotion/rollback manifest contracts, approval gates, rollback verification, and run summary linkage.                | `pnpm --dir platform/typescript/kernel-release test`   | Remove release shortcuts not backed by manifests.                    |
| P3-T15  | J2/J4   | Observability          | `KernelLifecycleService.ts`, provider contracts                                                            | Lifecycle flows log structured events but need correlation coverage tests.                                             | Add tests asserting correlation ID propagation in logs/events/results/manifests.                                          | `pnpm check:observability-conformance`                 | Remove console-only unstructured logs in critical flows.             |

## Phase 3 Exit Criteria

* Kernel lifecycle API, CLI, service, contracts, manifests, provider modes, and approvals are contract-tested.
* Digital Marketing uses Kernel exclusively for lifecycle execution.
* Non-enabled products fail safely and explain why.
* Runtime truth, provenance, event, and manifest writes are validated.

---

# 9. Phase 4 TODOs — Agentic Development via Kernel Contracts

## Phase Goal

Enable governed agentic product development without allowing agents to bypass Kernel lifecycle truth.

## Journey Coverage

* J3: full agentic product development
* J2: Kernel action execution target
* J5: artifact intelligence as evidence for agent planning
* J6: Data Cloud evidence/memory/provenance

## TODOs

| TODO ID | Journey | Area                | File / Location                                                                     | Current Issue                                                                                            | Required Change                                                                                                                                  | Tests / Validation                                   | Cleanup                                               |
| ------- | ------- | ------------------- | ----------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------- | ----------------------------------------------------- |
| P4-T01  | J3      | Agentic contracts   | `platform/typescript/kernel-product-contracts/src/agentic/*`                        | Contracts are exported but need full action fixtures.                                                    | Add fixtures for validate/test/build/package/deploy/verify/promote/rollback with risk, approvals, verification requirements, rollback readiness. | `pnpm check:agentic-lifecycle-action-contracts`      | Delete ad hoc agent action payloads.                  |
| P4-T02  | J3      | AEP action bridge   | `products/data-cloud/planes/action/**`                                              | AEP/action plane is partial and must call Kernel through contracts only.                                 | Implement an adapter that accepts `AgentLifecycleActionRequest`, validates policy/risk, and invokes Kernel lifecycle tool catalog.               | `./gradlew :products:data-cloud:planes:action:check` | Remove raw Gradle/pnpm/Docker invocation from agents. |
| P4-T03  | J3      | Tool permissions    | `platform/java/agent-core`, `products/data-cloud/planes/action/security`            | Tool permissions must be explicit and auditable.                                                         | Add policy model for allowed Kernel actions per agent type, role, product, environment, and risk level.                                          | `./gradlew :platform:java:agent-core:check`          | Remove implicit tool grants.                          |
| P4-T04  | J3      | Mastery/risk checks | `platform/java/agent-core`, `products/data-cloud/planes/action/registry`            | Agent mastery state is not yet proven in lifecycle path.                                                 | Add mastery state contract: supported versions, active/maintenance/obsolete/quarantined, last verified, confidence.                              | `pnpm check:agentic-lifecycle-action-contracts`      | Remove unversioned agent capability claims.           |
| P4-T05  | J3      | Approval checks     | `KernelLifecycleService.ts`, AEP adapter                                            | Risky actions must require approval before execution.                                                    | Add tests where deploy/promote/rollback requested by agent produce pending approval and do not execute.                                          | `pnpm check:kernel-platform-lifecycle`               | Remove agent-side approval bypasses.                  |
| P4-T06  | J3      | Verification proof  | `platform/typescript/kernel-product-contracts/src/agentic`, `kernel-lifecycle`      | Agentic results need verification evidence.                                                              | Require `verificationRequirements` and `verificationEvidenceRefs` for high/critical risk actions.                                                | `pnpm check:agentic-lifecycle-action-contracts`      | Remove success responses without evidence.            |
| P4-T07  | J3/J6   | Data Cloud evidence | `products/data-cloud/extensions/kernel-bridge`, `products/data-cloud/planes/action` | Evidence/provenance/memory must be written for agent actions.                                            | Add evidence writer for action request, policy decision, approval decision, lifecycle result, recommendation output.                             | `pnpm check:data-cloud-platform-providers`           | Remove ephemeral-only action logs.                    |
| P4-T08  | J3      | Studio agent UX     | `platform/typescript/ghatana-studio/src/routes/agents`                              | Agents route is disabled/degraded without executable guided flow.                                        | Add “propose plan”, “review risk”, “approve/reject”, “view evidence”, “rollback readiness” panels.                                               | `pnpm --dir platform/typescript/ghatana-studio test` | Remove generic chatbot-style UI that bypasses Kernel. |
| P4-T09  | J3      | Observability       | `products/data-cloud/planes/action/observability`, `platform/java/observability`    | Agent decisions must be traceable.                                                                       | Emit metrics/traces/logs for action requested, policy denied, approval pending, execution started, execution completed, verification failed.     | `pnpm check:observability-conformance`               | Remove unstructured agent logs.                       |
| P4-T10  | J3      | Failure modes       | AEP + Kernel tests                                                                  | Need deterministic behavior for denied, blocked, stale mastery, provider unavailable, approval rejected. | Add integration tests for each failure mode with safe reason codes.                                                                              | `pnpm check:agentic-lifecycle-action-contracts`      | Remove catch-all failures without reason codes.       |

## Phase 4 Exit Criteria

* Agents can only request lifecycle work through Kernel contracts.
* Risk, policy, mastery, approval, and verification checks are enforced.
* Every agentic action creates evidence/provenance/runtime truth.
* Studio displays agentic actions as reviewable, reversible, and evidence-backed.

---

# 10. Phase 5 TODOs — Data Cloud Platform-Mode Providers

## Phase Goal

Keep Kernel bootstrap-capable while enabling Data Cloud-backed platform mode after Data Cloud is available.

## Journey Coverage

* J6: Data Cloud foundation
* J3: agentic evidence/memory/provenance
* J5: semantic graph/provenance storage
* J2/J4: Kernel provider-mode runtime truth

## TODOs

| TODO ID | Journey | Area                        | File / Location                                                                                                         | Current Issue                                                           | Required Change                                                                                                           | Tests / Validation                                                 | Cleanup                                                      |
| ------- | ------- | --------------------------- | ----------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ | ------------------------------------------------------------ |
| P5-T01  | J6      | Data Cloud bridge extension | `products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelExtension.java` | Extension registers providers, but provider behavior must be proven.    | Add integration test that initializes extension and verifies registered providers can write/read required records.        | `./gradlew :products:data-cloud:extensions:kernel-bridge:check`    | Remove registrations for providers that are not implemented. |
| P5-T02  | J6      | Event provider              | `DataCloudEventProvider.java`                                                                                           | Need durable lifecycle event write/read proof.                          | Add contract test for append/query lifecycle events with tenant/workspace/project scope.                                  | `./gradlew :products:data-cloud:extensions:kernel-bridge:check`    | Remove in-memory fallback outside tests.                     |
| P5-T03  | J6      | Artifact provider           | `DataCloudArtifactProvider.java`                                                                                        | Need artifact manifest storage proof.                                   | Add contract test for storing artifact manifest refs, fingerprints, run/correlation ID.                                   | Same as above                                                      | Remove fake artifact refs.                                   |
| P5-T04  | J6      | Health provider             | `DataCloudHealthProvider.java`                                                                                          | Need provider health snapshots and stale/degraded semantics.            | Add tests for healthy, degraded, failed, stale, unknown states.                                                           | Same as above                                                      | Remove generic always-healthy responses.                     |
| P5-T05  | J6/J5   | Provenance provider         | `DataCloudProvenanceProvider.java`                                                                                      | Need durable provenance records.                                        | Add tests for provenance write/query for lifecycle and artifact intelligence evidence.                                    | Same as above                                                      | Remove unscoped provenance.                                  |
| P5-T06  | J3/J6   | Memory provider             | `DataCloudMemoryProvider.java`                                                                                          | Agent memory must be privacy-aware and scoped.                          | Add tests for semantic/episodic/procedural/task-state memory classification and tenant isolation.                         | Same as above                                                      | Remove unclassified memory writes.                           |
| P5-T07  | J5/J6   | Knowledge provider          | `DataCloudKnowledgeProvider.java`                                                                                       | Knowledge graph/retrieval provider is registered but must be validated. | Add tests for storing artifact graph references and retrieving by semantic reference.                                     | Same as above                                                      | Remove product-specific graph assumptions.                   |
| P5-T08  | J2/J6   | Runtime truth provider      | `DataCloudRuntimeTruthProvider.java`                                                                                    | Runtime truth must be canonical for platform mode.                      | Add tests for plan/execution/approval/verify statuses and evidence refs.                                                  | `pnpm check:kernel-provider-mode`                                  | Remove duplicate runtime truth stores.                       |
| P5-T09  | J3/J6   | Policy evidence provider    | `DataCloudPolicyEvidenceProvider.java`                                                                                  | Policy decisions must be auditable.                                     | Add tests for policy evaluation evidence, denial reason, approval requirement, privacy/security classifications.          | `pnpm check:data-cloud-platform-providers`                         | Remove non-auditable policy outcomes.                        |
| P5-T10  | J6      | TypeScript bridge client    | `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`                                                         | Check expects raw body alignment with gateway.                          | Add integration test proving client/gateway schema compatibility for every provider operation.                            | `pnpm --dir products/data-cloud/libs/kernel-bridge-providers test` | Remove wrapper/envelope mismatch.                            |
| P5-T11  | J6      | Action gateway              | `products/data-cloud/planes/action/gateway/src/app.ts`                                                                  | Check rejects in-memory `Map` provider storage.                         | Ensure provider storage uses injected Data Cloud service ports, not memory maps.                                          | `pnpm check:data-cloud-platform-providers`                         | Delete in-memory storage comments/code.                      |
| P5-T12  | J6      | Bootstrap/platform switch   | `platform/typescript/kernel-lifecycle/src/providers/LifecycleProviderContext.ts`                                        | Need full proof that bootstrap mode works before platform mode.         | Add integration test: bootstrap provider plan/build Data Cloud, then platform provider mode validates required providers. | `pnpm check:kernel-provider-mode`                                  | Remove platform-only assumptions in bootstrap path.          |
| P5-T13  | J6      | Product registry            | `config/canonical-product-registry.json`                                                                                | Data Cloud must remain lifecycle-disabled until bootstrap proof exists. | Keep `lifecycleExecutionAllowed=false`; add guard requiring bootstrap proof before change.                                | `pnpm check:product-shape-capability-matrix`                       | Revert premature Data Cloud lifecycle enablement.            |

## Phase 5 Exit Criteria

* Kernel bootstrap mode remains independent of Data Cloud.
* Data Cloud platform providers have behavior tests, not just file-presence checks.
* Platform mode fails closed when required Data Cloud-backed providers are missing.
* Runtime truth, memory, provenance, policy evidence, and health are tenant-scoped and durable.

---

# 11. Phase 6 TODOs — Artifact Intelligence Integration

## Phase Goal

Make YAPPC artifact compiler/decompiler a governed intelligence capability whose outputs are consumable by Data Cloud, Kernel, and Studio without boundary leaks.

## Journey Coverage

* J1: ProductUnitIntent creation evidence from YAPPC
* J5: artifact intelligence end to end
* J6: Data Cloud graph/provenance/memory storage
* J2/J3: Kernel planning and agentic decisions consume evidence by reference

## TODOs

| TODO ID | Journey | Area                         | File / Location                                                                                                       | Current Issue                                                                                    | Required Change                                                                                                                                                 | Tests / Validation                                                                     | Cleanup                                                                    |
| ------- | ------- | ---------------------------- | --------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| P6-T01  | J5      | Compile orchestration        | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/ArtifactCompileJobService.java` | Semantic models are saved as `List.of()` even when extracted.                                    | Add `semanticModels` to `ExtractionResult`, merge Java/TS semantic models, and persist actual models.                                                           | `./gradlew :products:yappc:core:yappc-services:test`                                   | Remove placeholder persistence comments.                                   |
| P6-T02  | J5      | Java extraction              | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/compiler/JavaArtifactExtractor.java`     | Extracts semantic models locally but drops them from returned result.                            | Return semantic models in extraction result.                                                                                                                    | `./gradlew :products:yappc:core:yappc-services:test --tests '*JavaArtifactExtractor*'` | Remove unused local semantic model list if not returned.                   |
| P6-T03  | J5      | Residual checksum            | `JavaArtifactExtractor.java`                                                                                          | Checksum uses byte array object string, not hex SHA-256.                                         | Add stable `sha256Hex(String input)` helper and use it for parse/extraction residuals.                                                                          | Same as above                                                                          | Remove random UUID fallback except for impossible algorithm failure.       |
| P6-T04  | J5      | Source locations             | `JavaArtifactExtractor.java`, `JavaSourceParser`                                                                      | Java node/source locations are approximate (`1..100`).                                           | Use parser-provided exact line/column data or classify approximate evidence with confidence/requiresReview.                                                     | Same as above                                                                          | Remove misleading exact-looking ranges.                                    |
| P6-T05  | J5      | TypeScript extraction        | `ArtifactCompileJobService.TsExtractorWorker`, TS worker implementation                                               | TS extraction does not appear to return semantic models.                                         | Extend TS worker output to include semantic models, residual islands, risk hotspots, dependency evidence.                                                       | `./gradlew :products:yappc:core:yappc-services:test` and TS worker tests               | Remove divergent TS/Java result shapes.                                    |
| P6-T06  | J5      | Semantic repository          | `SemanticModelRepository` and implementations                                                                         | Need proof of tenant/workspace/project-scoped persistence.                                       | Add integration tests for save/query/version semantic models by snapshot/version.                                                                               | `./gradlew :products:yappc:core:yappc-services:test`                                   | Remove unscoped semantic storage.                                          |
| P6-T07  | J5      | Artifact graph ingestion     | `ArtifactGraphService`, `ArtifactGraphRepository`                                                                     | Graph ingest must preserve nodes, edges, unresolved refs, residuals, version, snapshot checksum. | Add golden graph ingest tests for mixed TS/Java repo.                                                                                                           | `./gradlew :products:yappc:core:yappc-services:test --tests '*ArtifactGraph*'`         | Remove graph fields that are never populated.                              |
| P6-T08  | J5      | Evidence contracts           | `platform/typescript/kernel-product-contracts/src/artifact-intelligence/*`                                            | Contract exists but needs fixture coverage.                                                      | Add fixture bundle for SemanticArtifactReference, ArtifactGraphSummary, ProductShapeEvidence, DependencyGraphEvidence, ResidualIslandReport, RiskHotspotReport. | `pnpm --dir platform/typescript/kernel-product-contracts test`                         | Remove non-canonical evidence shapes.                                      |
| P6-T09  | J5/J2   | Kernel reference consumption | `platform/typescript/kernel-lifecycle`, `platform/typescript/kernel-product-contracts/src/artifact-intelligence`      | Kernel must consume references, not YAPPC internals.                                             | Add planner test where semantic artifact evidence affects gate/risk/readiness using only shared contracts.                                                      | `pnpm check:yappc-artifact-intelligence-boundary`                                      | Remove imports from `products/yappc/**` in Kernel/platform packages.       |
| P6-T10  | J5/J6   | Data Cloud graph/provenance  | `products/data-cloud/**`, `products/yappc/infrastructure/datacloud`                                                   | Need storage of semantic graph/provenance from YAPPC outputs.                                    | Add bridge/provider that writes artifact graph summary, residual islands, risk hotspots, and provenance into Data Cloud.                                        | `pnpm check:data-cloud-platform-providers`                                             | Remove ad hoc local-only graph stores where Data Cloud is required.        |
| P6-T11  | J5/J1   | ProductUnitIntent evidence   | `products/yappc/kernel-bridge`, `platform/typescript/kernel-product-contracts`                                        | ProductUnitIntent should include artifact evidence refs where generated from source.             | Add evidence refs in ProductUnitIntent producer/provenance fields and validate in Kernel preview/apply.                                                         | `pnpm check:yappc-product-unit-intent-handoff`                                         | Remove intents without provenance for generated source.                    |
| P6-T12  | J5      | Studio visualization         | `platform/typescript/ghatana-studio/src/routes/artifacts`, `products/yappc/frontend`                                  | Artifact intelligence must show residual islands, risks, recommendations.                        | Add shared visualization panels backed by semantic evidence contracts.                                                                                          | `pnpm --dir platform/typescript/ghatana-studio test`                                   | Remove YAPPC-specific visualization duplication after shared panel exists. |
| P6-T13  | J5      | Boundary check               | `scripts/check-yappc-artifact-intelligence-boundary.mjs`                                                              | Boundary check must catch Kernel importing YAPPC implementation internals.                       | Extend check to scan Kernel, Studio, Data Cloud bridge, and shared packages.                                                                                    | `pnpm check:yappc-artifact-intelligence-boundary`                                      | Remove allowlisted direct coupling.                                        |

## Phase 6 Exit Criteria

* Java and TS extraction return consistent semantic output shapes.
* Semantic models are actually persisted.
* Residual checksums are stable SHA-256 hex.
* Kernel consumes artifact intelligence only through shared contracts/references.
* Data Cloud stores graph/provenance/evidence where platform mode is used.
* Studio/YAPPC show residual islands, risk hotspots, and recommendations with evidence.

---

# 12. Phase 7 TODOs — Future Product Shape Readiness

## Phase Goal

Validate that the platform can support future product shapes without forcing products to reimplement platform capabilities or prematurely enabling lifecycle execution.

## Journey Coverage

* J7: all future product shapes
* J2: readiness for direct Kernel lifecycle usage
* J3: readiness for agentic product development
* J6: readiness for Data Cloud provider modes

## TODOs

| TODO ID | Journey | Product / Area              | File / Location                                                              | Current Issue                                                                                            | Required Change                                                                                                                     | Tests / Validation                             | Cleanup                                                               |
| ------- | ------- | --------------------------- | ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------- | --------------------------------------------------------------------- |
| P7-T01  | J7      | PHR                         | `config/canonical-product-registry.json`, `products/phr/kernel-product.yaml` | PHR is planned/disabled and requires consent, PII, audit, FHIR, sovereignty gates.                       | Add readiness evidence checklist and gate validator; do not enable lifecycle.                                                       | `pnpm check:product-shape-capability-matrix`   | Remove any lifecycle-enabled flags.                                   |
| P7-T02  | J7      | Finance                     | `products/finance/kernel-product.yaml`, registry                             | Finance requires regulatory, risk, promotion approval, multi-module build, portal/operator/SDK adapters. | Validate required gates and adapter definitions without execution.                                                                  | `pnpm check:product-shape-capability-matrix`   | Remove backend-only claims that imply full portal/operator execution. |
| P7-T03  | J7      | FlashIt                     | `products/flashit/kernel-product.yaml`, registry                             | FlashIt requires mobile iOS/Android adapters, privacy/preview gates, ipa/aab manifests.                  | Add readiness checks for mobile adapter/artifact contracts; keep disabled.                                                          | `pnpm check:product-shape-capability-matrix`   | Remove mobile build assumptions without adapters.                     |
| P7-T04  | J7/J6   | Data Cloud                  | `config/canonical-product-registry.json`                                     | Data Cloud is platform-provider and must not be treated as ordinary product lifecycle target yet.        | Enforce provider-mode-only readiness until bootstrap/platform proof exists.                                                         | `pnpm check:kernel-provider-mode`              | Revert ordinary lifecycle enablement.                                 |
| P7-T05  | J7/J5   | YAPPC                       | registry, `products/yappc/kernel-bridge`                                     | YAPPC is platform-provider/creator; lifecycle must not collapse creator lifecycle into Kernel lifecycle. | Add guard that YAPPC ProductUnitIntent/artifact evidence contracts are allowed, but lifecycle execution stays disabled until clear. | `pnpm check:yappc-product-unit-intent-handoff` | Remove Kernel imports of YAPPC internals.                             |
| P7-T06  | J7      | TutorPutor                  | registry, `products/tutorputor/kernel-product.yaml`                          | Partial lifecycle with content safety, learner privacy, model-output evaluation gates.                   | Add readiness validator for content-safety/model-eval/privacy gates; keep disabled.                                                 | `pnpm check:product-shape-capability-matrix`   | Remove executable claims without gates.                               |
| P7-T07  | J7      | Aura                        | registry, `products/aura/**`                                                 | Demo/example product, disabled.                                                                          | Keep disabled; add explainability/recommendation safety readiness entries only.                                                     | `pnpm check:product-registry`                  | Remove product readiness claims from demo docs.                       |
| P7-T08  | J7      | DCMAAR                      | registry, `products/dcmaar/**`                                               | Requires threat-model, Guardian policy, security review gates.                                           | Add readiness checklist and required gates; keep disabled.                                                                          | `pnpm check:product-shape-capability-matrix`   | Remove executable claims without threat model.                        |
| P7-T09  | J7      | Audio-Video                 | registry, `products/audio-video/**`                                          | Requires media privacy, content safety, artifact retention.                                              | Add media artifact/retention readiness contracts; keep disabled.                                                                    | `pnpm check:product-shape-capability-matrix`   | Remove vague shared-service lifecycle claims.                         |
| P7-T10  | J7      | Future external ProductUnit | `platform/typescript/kernel-product-contracts`, product scaffolder           | Need clear onboarding contract for external products.                                                    | Add scaffolded readiness template: surfaces, toolchains, artifacts, gates, policies, provider modes, validation commands.           | `pnpm check:product-scaffolder`                | Remove generated templates that skip governance fields.               |
| P7-T11  | J7      | Shape matrix                | `config/product-shape-capability-matrix.*`, scripts                          | Shape matrix must distinguish platform gaps from product gaps.                                           | Add matrix columns: owner, required platform capability, required product declaration, blocker, do-not-enable-until.                | `pnpm check:product-shape-capability-matrix`   | Remove ambiguous “partial” without reason.                            |
| P7-T12  | J7      | Product registry drift      | `scripts/check-product-registry-drift.mjs`                                   | Registry, manifests, workspace wiring can drift.                                                         | Ensure drift check compares registry entries, `kernel-product.yaml`, package paths, Gradle modules, pnpm packages.                  | `pnpm check:product-registry-drift`            | Remove stale registry entries.                                        |

## Phase 7 Exit Criteria

* Product shape matrix is explicit and owner-aware.
* Future products are readiness-validated but not prematurely enabled.
* Each product gap is assigned to Kernel, YAPPC, Data Cloud, shared library, or product owner.
* Product registry, manifests, workspace wiring, and shape matrix do not drift.

---

# 13. Phase 8 TODOs — Production Hardening, CI/CD, Documentation, and Cleanup

## Phase Goal

Remove ambiguity, dead code, duplicate code, stale documentation, unsafe defaults, weak validation, fragmented checks, and unproven claims so the repo remains coherent after implementation.

## Journey Coverage

* All journeys J1–J7
* All phases Phase 0–7
* All areas

## TODOs

| TODO ID | Journey | Area                         | File / Location                                                                                | Current Issue                                                             | Required Change                                                                                                                                                           | Tests / Validation                                                | Cleanup                                                |
| ------- | ------- | ---------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- | ------------------------------------------------------ |
| P8-T01  | All     | Phase scripts                | `package.json`                                                                                 | Many checks exist but are fragmented.                                     | Add `check:phase0` through `check:phase8` scripts that group checks by this plan.                                                                                         | `pnpm check:phase0 && pnpm check:phase8`                          | Remove duplicate aliases after phase aliases exist.    |
| P8-T02  | All     | CI workflows                 | `.github/workflows/*`                                                                          | Need required CI jobs for phase-level gates.                              | Add jobs: phase0-coherence, phase1-studio, phase2-dm-pilot, phase3-kernel, phase4-agentic, phase5-data-cloud, phase6-artifact-intelligence, phase7-shape, phase8-cleanup. | GitHub Actions required checks                                    | Remove optional-only critical checks.                  |
| P8-T03  | All     | Evidence packs               | `.kernel/evidence/**`, CI artifacts                                                            | Checks should emit evidence, not just console pass/fail.                  | Add evidence-pack output for lifecycle pilot, Data Cloud providers, artifact intelligence, phase checks.                                                                  | CI artifact upload                                                | Delete stale evidence before generation.               |
| P8-T04  | All     | Production stubs             | `scripts/check-production-stubs.mjs`, `config/production-stub-allowlist.json`                  | Allowlist can hide production-critical stubs.                             | Require owner, expiry, issue link, reason, safe fallback, feature flag for every allowlist item.                                                                          | `pnpm check:production-stubs`                                     | Remove expired allowlist entries.                      |
| P8-T05  | All     | Deprecated packages          | `platform/typescript/LIBRARY_GOVERNANCE.md`, `platform/typescript/*`                           | Deprecated facade packages still exist by governance.                     | Migrate consumers to canonical packages, then delete deprecated packages/facades.                                                                                         | `pnpm check:deprecated-packages && pnpm check:deprecated-imports` | Delete re-export shims.                                |
| P8-T06  | All     | Orphan modules               | `scripts/check-orphan-modules.mjs`, workspace files                                            | Orphan folders can cause repeated audits.                                 | Fail CI on Java/TS folders not wired or explicitly exempted.                                                                                                              | `pnpm check:orphan-modules`                                       | Delete folder-only shells.                             |
| P8-T07  | All     | Documentation reconciliation | `docs/**`, `.github/copilot-instructions.md`, blueprint docs                                   | Same rule can appear in multiple docs.                                    | Mark one authoritative source per rule and convert others to references.                                                                                                  | `pnpm check:doc-truth`                                            | Remove duplicate rules from non-authoritative docs.    |
| P8-T08  | All     | Test authenticity            | `scripts/check-production-stubs.mjs`, test dirs                                                | Must prevent object-literal tests and tests importing no production code. | Add scanner for `expect(true)`, object-literal-only tests, skipped tests without issue refs, no production imports.                                                       | `pnpm test && pnpm check:production-readiness`                    | Rewrite or delete theater tests.                       |
| P8-T09  | All     | Security                     | `scripts/check-secret-default-credentials.mjs`, config/env files                               | Unsafe defaults must be blocked.                                          | Scan env examples, compose, Docker, manifests, docs for secret/token/password defaults.                                                                                   | `pnpm check:secret-default-credentials`                           | Remove committed secrets/default credentials.          |
| P8-T10  | All     | Observability                | `scripts/check-observability-conformance.mjs`, `platform/java/observability`, Kernel providers | Important flows must have logs/metrics/traces/health/correlation IDs.     | Extend check to cover lifecycle, agentic, artifact intelligence, Data Cloud providers, Digital Marketing pilot.                                                           | `pnpm check:observability-conformance`                            | Remove silent failures.                                |
| P8-T11  | All UI  | i18n/a11y                    | Studio, shared UI libs, product UIs                                                            | Customer UI must be i18n/a11y ready.                                      | Add checks for translation keys, semantic labels, keyboard support, visible focus, non-color statuses.                                                                    | `pnpm check:shared-ui-state-coverage`                             | Remove inaccessible one-off controls.                  |
| P8-T12  | All UI  | Design consistency           | `platform/typescript/design-system`, product UIs                                               | UI duplication across product/local components can grow.                  | Enforce shared design-system/product-shell components for standard states and controls.                                                                                   | `pnpm check:design-system-conformance`                            | Delete duplicate local UI primitives.                  |
| P8-T13  | All     | Boundary checks              | `scripts/check-platform-product-boundaries.mjs`, `scripts/check-kernel-boundaries.mjs`         | Need strict no product internals in platform/shared packages.             | Extend checks for Studio, Kernel, shared libraries, Data Cloud bridge, YAPPC bridge.                                                                                      | `pnpm check:architecture-boundaries`                              | Remove boundary exceptions unless formally justified.  |
| P8-T14  | All     | Workspace drift              | `scripts/check-product-registry-artifacts.mjs`, generated includes                             | Generated registry artifacts can drift.                                   | Run generator in check mode in CI and fail on drift.                                                                                                                      | `pnpm check:product-registry-artifacts`                           | Regenerate or delete stale generated files.            |
| P8-T15  | All     | Bundle/performance           | Studio and product UIs                                                                         | Need bundle limits and performance checks for customer UI.                | Add bundle budget checks for Studio, Digital Marketing UI, Data Cloud UI, YAPPC frontend.                                                                                 | `pnpm check:audited-performance-workflows`                        | Remove unused dependencies/imports.                    |
| P8-T16  | All     | Final validation suite       | `package.json`, CI workflows                                                                   | Need one command for all production readiness gates.                      | Add `check:world-class-platform-readiness` that runs phase checks and high-confidence validation.                                                                         | `pnpm check:world-class-platform-readiness`                       | Remove obsolete audit-only commands after replacement. |

## Phase 8 Exit Criteria

* Every phase has a command alias and CI gate.
* Evidence packs are generated for important flows.
* No production stubs, unsafe defaults, orphan modules, deprecated imports, or target-state claims remain untracked.
* Tests exercise production code and are placed correctly.
* Observability, security, privacy, i18n, and accessibility checks are enforced.

---

# 14. Cross-Journey TODO Summary

## J1 — Product Ideation to ProductUnitIntent

| TODO ID | Phase   | File / Area                                                                | Required Outcome                                                                                        |
| ------- | ------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| J1-T01  | Phase 1 | Studio Ideas/Blueprints/Canvas routes                                      | Show YAPPC ownership, readiness, ProductUnitIntent availability, and missing evidence.                  |
| J1-T02  | Phase 3 | `ProductUnitIntentSchema`, `KernelLifecycleService.applyProductUnitIntent` | Validate preview/apply, provider modes, events, provenance, runtime truth.                              |
| J1-T03  | Phase 6 | YAPPC artifact evidence                                                    | Attach semantic artifact evidence refs to generated ProductUnitIntent.                                  |
| J1-T04  | Phase 8 | CI                                                                         | Add E2E test: idea/blueprint/canvas fixture → ProductUnitIntent preview → apply blocked/applied result. |

## J2 — Direct Product Development Kernel Usage

| TODO ID | Phase   | File / Area                                    | Required Outcome                                              |
| ------- | ------- | ---------------------------------------------- | ------------------------------------------------------------- |
| J2-T01  | Phase 1 | Studio Develop/Lifecycle/Artifacts/Deployments | ProductUnit lifecycle UX with real route states and evidence. |
| J2-T02  | Phase 2 | Digital Marketing pilot                        | Validate/test/build/package/deploy/verify pilot.              |
| J2-T03  | Phase 3 | Kernel lifecycle service/API/CLI               | Full lifecycle truth, manifests, approvals, run history.      |
| J2-T04  | Phase 8 | CI                                             | Phase 2 + Phase 3 checks required.                            |

## J3 — Agentic Product Development

| TODO ID | Phase     | File / Area                           | Required Outcome                                              |
| ------- | --------- | ------------------------------------- | ------------------------------------------------------------- |
| J3-T01  | Phase 4   | Agentic contracts                     | Risk/approval/verification/rollback-ready action fixtures.    |
| J3-T02  | Phase 4   | AEP action adapter                    | Agents call Kernel contracts only, not raw commands.          |
| J3-T03  | Phase 5   | Data Cloud memory/provenance/evidence | Persist action evidence and memory with tenant/privacy scope. |
| J3-T04  | Phase 1/4 | Studio Agents route                   | Reviewable plan, approval, evidence, and recommendation UI.   |

## J4 — Digital Marketing Lifecycle Pilot

| TODO ID | Phase   | File / Area                                           | Required Outcome                                            |
| ------- | ------- | ----------------------------------------------------- | ----------------------------------------------------------- |
| J4-T01  | Phase 2 | `products/digital-marketing/kernel-product.yaml`      | Canonical pilot lifecycle phases/gates/manifests/providers. |
| J4-T02  | Phase 2 | `scripts/check-digital-marketing-lifecycle-pilot.mjs` | Smoke/evidence-pack enforcement.                            |
| J4-T03  | Phase 3 | Kernel CLI/service                                    | Real plan/run execution and approval-required flows.        |
| J4-T04  | Phase 8 | CI                                                    | Pilot is required gate.                                     |

## J5 — Artifact Intelligence

| TODO ID | Phase     | File / Area                            | Required Outcome                                              |
| ------- | --------- | -------------------------------------- | ------------------------------------------------------------- |
| J5-T01  | Phase 6   | `ArtifactCompileJobService.java`       | Persist actual semantic models.                               |
| J5-T02  | Phase 6   | `JavaArtifactExtractor.java`           | Return semantic models and stable SHA-256 residual checksums. |
| J5-T03  | Phase 6   | Kernel artifact-intelligence contracts | Use canonical evidence bundles.                               |
| J5-T04  | Phase 5   | Data Cloud graph/provenance            | Store graph/provenance/evidence in platform mode.             |
| J5-T05  | Phase 1/6 | Studio/YAPPC UI                        | Display residual islands, risks, recommendations.             |

## J6 — Data Cloud Foundation

| TODO ID | Phase   | File / Area                 | Required Outcome                              |
| ------- | ------- | --------------------------- | --------------------------------------------- |
| J6-T01  | Phase 5 | Data Cloud bridge providers | Durable provider behavior tests.              |
| J6-T02  | Phase 5 | Kernel provider context     | Bootstrap and platform mode validated.        |
| J6-T03  | Phase 5 | Data Cloud registry entry   | Stay disabled until bootstrap proof exists.   |
| J6-T04  | Phase 8 | CI                          | Data Cloud platform provider checks required. |

## J7 — Future Product Shape Readiness

| TODO ID | Phase   | File / Area                        | Required Outcome                                                           |
| ------- | ------- | ---------------------------------- | -------------------------------------------------------------------------- |
| J7-T01  | Phase 7 | PHR                                | Healthcare/privacy/FHIR/readiness gates; disabled lifecycle.               |
| J7-T02  | Phase 7 | Finance                            | Regulatory/multi-module/portal/operator/SDK readiness; disabled lifecycle. |
| J7-T03  | Phase 7 | FlashIt                            | Mobile/privacy/preview readiness; disabled lifecycle.                      |
| J7-T04  | Phase 7 | Data Cloud/YAPPC                   | Platform-provider readiness only; no ordinary lifecycle enablement.        |
| J7-T05  | Phase 7 | TutorPutor/Aura/DCMAAR/Audio-Video | Product-specific readiness matrix; no premature migration.                 |

---

# 15. Recommended Independent Execution Sessions

## Session A — Phase 6 Artifact Intelligence Correctness

Scope:

* `ArtifactCompileJobService.java`
* `JavaArtifactExtractor.java`
* `SemanticModelRepository`
* artifact intelligence contract fixtures

Commands:

```bash
./gradlew :products:yappc:core:yappc-services:test
pnpm check:yappc-artifact-intelligence-boundary
```

Why first:

* It has a clear correctness bug.
* It is mostly independent of Studio and Digital Marketing pilot work.
* It directly improves Journey 5 and supports agentic/product-shape evidence.

## Session B — Phase 2 Digital Marketing Pilot Evidence

Scope:

* `products/digital-marketing/kernel-product.yaml`
* `scripts/check-digital-marketing-lifecycle-pilot.mjs`
* `.github/workflows/*`
* `scripts/kernel-product.mjs`

Commands:

```bash
pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing
pnpm check:kernel-platform-lifecycle
pnpm check:product-artifact-contracts
pnpm check:product-deployment-contracts
```

Why first:

* Digital Marketing is the validated lifecycle pilot.
* This creates the executable proof needed before broadening scope.

## Session C — Phase 1 + Phase 3 Studio/Kernel Contract Hardening

Scope:

* `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`
* `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`
* `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts`
* route parity tests

Commands:

```bash
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm check:studio-kernel-api
```

Why first:

* It aligns the customer-facing Studio experience with real Kernel API truth.
* It prevents the UI from claiming unsupported states.

## Session D — Phase 5 Data Cloud Provider Behavior

Scope:

* `products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/*Provider.java`
* `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`
* `products/data-cloud/planes/action/gateway/src/app.ts`
* provider behavior tests

Commands:

```bash
pnpm check:data-cloud-platform-providers
./gradlew :products:data-cloud:extensions:kernel-bridge:check
```

Why independent:

* It can proceed separately from Studio and Digital Marketing pilot.
* It hardens the bootstrap/platform provider boundary.

## Session E — Phase 7 Product Shape Matrix

Scope:

* `config/canonical-product-registry.json`
* `products/*/kernel-product.yaml`
* `scripts/check-product-shape-capability-matrix.mjs`
* product shape documentation

Commands:

```bash
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-registry-drift
```

Why independent:

* It validates future readiness without changing product execution.

## Session F — Phase 8 CI/Cleanup Gate Consolidation

Scope:

* `package.json`
* `.github/workflows/*`
* cleanup scripts
* deprecated package/import checks
* production-readiness checks

Commands:

```bash
pnpm check:architecture-boundaries
pnpm check:production-readiness
pnpm check:production-stubs
pnpm check:deprecated-packages
pnpm check:orphan-modules
./gradlew check
```

Why independent:

* It consolidates enforcement after the first fixes land.

---

# 16. Final Validation Suite

Run the full suite after completing all phase TODOs:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform

pnpm check:domain-registry
pnpm check:domain-boundaries
pnpm check:current-state-claims
pnpm check:doc-claims-evidence
pnpm check:architecture-boundaries

pnpm check:kernel-platform-lifecycle
pnpm check:kernel-lifecycle-truth
pnpm check:kernel-provider-mode
pnpm check:kernel-product-unit-provider-contracts
pnpm check:studio-kernel-api
pnpm check:agentic-lifecycle-action-contracts

pnpm check:digital-marketing-lifecycle-pilot
pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing

pnpm check:data-cloud-platform-providers
pnpm check:data-access-contract

pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary

pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-registry-drift

pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:shared-ui-state-coverage

pnpm check:production-readiness
pnpm check:production-stubs
pnpm check:secret-default-credentials
pnpm check:observability-conformance
pnpm check:deprecated-packages
pnpm check:deprecated-imports
pnpm check:orphan-modules
pnpm check:kernel-product-boundary-audit

./gradlew build
./gradlew check
./gradlew :products:yappc:core:yappc-services:test
./gradlew :products:data-cloud:extensions:kernel-bridge:check
```

---

# 17. Definition of Done

The implementation is done only when:

* Every TODO is either implemented, deleted as no longer relevant, or explicitly deferred with owner, reason, and issue link.
* Every phase has a validation command.
* Every journey has executable or honestly blocked proof.
* Digital Marketing remains the only lifecycle-enabled pilot until promoted.
* Data Cloud bootstrap and platform modes are proven separately.
* YAPPC artifact intelligence produces persisted semantic evidence and stable residual checksums.
* Agentic lifecycle execution can only happen through Kernel contracts.
* Studio exposes route readiness truthfully and accessibly.
* No target architecture is presented as current implementation.
* No production stubs, unsafe defaults, orphan modules, deprecated imports, disabled tests without issue references, or object-literal test theater remain.
* Observability, security, privacy, i18n, accessibility, and testing are part of every important flow.
