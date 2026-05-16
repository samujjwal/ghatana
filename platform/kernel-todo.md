# Full End-to-End Ghatana World-Class Product Audit

**Repository:** `samujjwal/ghatana`  
**Target commit:** `17eaf77d6c15c8f35397669724ea051011377e71`  
**Commit title:** `kernel + com-decompiler`  
**Audit posture:** Complete codebase snapshot audit, not commit-diff-only review.  
**Execution boundary:** GitHub connector inspection was used against the target ref. Local clone/build/test execution was not possible in this sandbox because outbound GitHub DNS resolution failed. The validation commands below are therefore required execution gates, not commands I can truthfully claim passed in this run.

---

## 1. Required Sources of Truth Used

This audit is grounded in:

1. `.github/copilot-instructions.md` — repo-specific rules: reuse before creating, explicit boundaries, no silent failures, strict TypeScript, tests as part of every change, required Java doc tags, and observability as part of features.
2. Hardened Ghatana Unified Product Development Blueprint — canonical ownership: Ghatana Studio as unified UX, Product Development Kernel as lifecycle truth, YAPPC as creation/artifact intelligence, Data Cloud as runtime truth/event/provenance/memory foundation, and agents operating through governed Kernel contracts.
3. Ghatana Domain Workstream Map — current domains, owners, repo associations, and current-state classification model.
4. Current repository files at target commit `17eaf77d6c15c8f35397669724ea051011377e71`.

---

## 2. Executive Summary

Ghatana is structurally moving in the right direction. The repo has a serious platform foundation: root pnpm and Gradle wiring, a canonical product registry, Product Development Kernel packages, Data Cloud Action Plane gateway routes, Ghatana Studio shell, Digital Marketing lifecycle pilot, and a large suite of governance/check scripts.

The system is **not yet world-class end-to-end executable** because the remaining blockers are correctness and coherence blockers, not missing feature lists:

1. **Data Cloud platform provider contracts are not aligned with Kernel provider contracts.** Runtime truth, artifact, health, approval, provenance, and memory provider endpoint schemas in the Action Plane gateway do not match the canonical Kernel provider contracts strongly enough.
2. **Data Cloud provider persistence can silently fall back to in-memory storage.** This violates the blueprint’s durable runtime truth requirement and the production-readiness rules.
3. **Studio duplicates Kernel API contracts locally.** `@ghatana/ghatana-studio` does not depend on `@ghatana/kernel-lifecycle`, while its client duplicates lifecycle phases, statuses, failure codes, and response schemas.
4. **Lifecycle execution enablement is still too easy to drift.** The product registry clearly marks only Digital Marketing as enabled, while PHR, Finance, FlashIt, Tutorputor, Data Cloud, YAPPC, Aura, DCMAAR, and Audio-Video are planned, partial, disabled, or platform-provider shaped. Kernel and Studio must enforce that distinction everywhere.
5. **Ghatana Studio exists and is coherent, but it is still partial.** Navigation and Lifecycle UI are present; however, lifecycle readiness, ProductUnitIntent handoff, agentic development, artifact intelligence, and Data Cloud-backed provider evidence are not fully unified.
6. **Digital Marketing is the right lifecycle pilot.** It should be the only first fully executable product until platform-mode provider correctness, artifact/deployment truth, and E2E validation are hardened.
7. **YAPPC artifact intelligence has strong pieces but the end-to-end handoff is not done.** The correct flow is YAPPC semantic evidence → Data Cloud graph/provenance → Kernel references/gates → Studio visibility.
8. **The repo has many guardrails, but they need to become release gates.** Scripts exist for architecture, Kernel, product registry, boundaries, production readiness, design-system conformance, observability, and product lifecycle checks. The next improvement is not adding more checks; it is making the critical subset mandatory and non-bypassable.

### Top 10 Fixes

| Rank | Fix | Priority | Why it matters |
|---:|---|---|---|
| 1 | Align Data Cloud provider endpoint schemas with `LifecycleProviders.ts` contracts | P0 | Platform mode cannot be trusted if runtime truth/provider records are not canonical. |
| 2 | Remove unsafe in-memory provider fallback from production gateway config | P0 | Runtime truth/provenance/memory cannot be ephemeral. |
| 3 | Export and reuse Kernel API schemas instead of duplicating them in Studio | P0 | Prevents API drift between Studio and Kernel. |
| 4 | Enforce `lifecycle.enabled` / `lifecycleStatus` / `lifecycleExecutionAllowed` consistently | P0 | Prevents planned/target products from executing as if ready. |
| 5 | Surface `lifecycleReadiness` and blocked reason codes in Studio ProductUnit UX | P1 | Gives users clear, actionable blocked states. |
| 6 | Make Digital Marketing lifecycle pilot the release-1 golden path | P1 | Proves real validate/test/build/package/deploy/verify. |
| 7 | Add agentic lifecycle action E2E path through Kernel contracts | P1 | Prevents agents from bypassing lifecycle truth. |
| 8 | Complete YAPPC artifact intelligence evidence contracts and handoff | P1 | Enables scan/decompile → semantic evidence → Kernel gates. |
| 9 | Harden Studio i18n/a11y and remove hardcoded user-facing strings | P2 | Required by blueprint and repo standards. |
| 10 | Convert validation command suite into mandatory CI workflow gates | P2 | Prevents regressions and stale target-state claims. |

---

## 3. System Architecture Map

| System | Correct owner | Current evidence | Current state | Audit conclusion |
|---|---|---|---|---|
| Ghatana Studio | Product-neutral unified UX shell | `@ghatana/ghatana-studio` exists, depends on design system, canvas, UI builder, Kernel artifacts/deployment/release/contracts, i18n, API, and platform libs. | Existing but partial | Shell and lifecycle UI exist; contract reuse and journey completeness need hardening. |
| Product Development Kernel | Lifecycle execution truth | Kernel packages and API handlers exist; bootstrap providers exist; lifecycle service writes plan/result/runtime truth/provenance. | Existing but partial | Strong foundation; platform mode and product readiness gating are the critical hardening areas. |
| YAPPC | Creation, ProductUnitIntent, artifact intelligence, decompiler/compiler | Registry marks YAPPC as platform-provider with required gates around ProductUnitIntent export, artifact-intelligence boundary, and creator/Kernel separation. | Existing but partial | Keep YAPPC implementation internals product-local; export evidence through shared contracts only. |
| Data Cloud | Runtime truth, events, provenance, memory, Action Plane/AEP foundation | Data Cloud has large module graph and Action gateway; registry conformance remains false for manifest/observability/security/dataAccess/bridge. | Existing but partial | Provider schemas and durable store are the biggest correctness blockers. |
| AEP / Agents | Governed action runtime through Data Cloud Action Plane | Action gateway imports agent lifecycle action schema and has gateway-level JWT/scope enforcement. | Existing but partial | Agentic product-development flow must be wired through Kernel contracts with evidence and approval gates. |
| Digital Marketing | First executable lifecycle pilot | Registry marks lifecycle enabled and migration ready; bridge adapter tests are listed. | Existing and pilot-ready | Make this the only Release 1 golden path until all manifests/gates are verified. |
| Future products | Product-specific domain behavior | PHR/Finance/FlashIt/Tutorputor/Aura/DCMAAR/Audio-Video entries show planned, partial, or disabled readiness states. | Target / partial | Do not enable lifecycle execution until product-specific gates and adapters are validated. |
| Shared platform libraries | Product-neutral primitives | pnpm workspace includes platform TypeScript libs, canvas, Studio, agent-catalog, products, shared services. | Existing but partial | Good workspace shape; prevent product-specific drift into platform packages. |

---

## 4. Journey-by-Journey Findings

### Journey 1 — Product Ideation to ProductUnitIntent

**Expected flow**

```text
Idea in Studio/YAPPC → blueprint/canvas → AI-assisted generation → ProductUnitIntent → Kernel validation/application → ProductUnit registry entry
```

**Current flow**

- Studio routes for Ideas, Blueprints, Canvas, Develop, Lifecycle, Agents, Artifacts, Deployments, Health, Learn, and Settings exist.
- Kernel service has `applyProductUnitIntent(...)`, validates `ProductUnitIntent`, supports preview/apply, emits events/runtime truth/provenance when providers exist.
- YAPPC registry readiness still calls out required work around ProductUnitIntent export and creator/Kernel lifecycle separation.

**Gaps**

| Gap | Current state | Required fix |
|---|---|---|
| ProductUnitIntent UX | Partial / likely route-level placeholder outside Lifecycle page | Add ProductUnitIntent preview/apply workflow in Studio Ideas/Blueprints/Canvas or YAPPC shell. |
| Contract handoff | Kernel service supports it, but Studio/YAPPC handoff is not proven | Add shared client/API path for ProductUnitIntent preview/apply with runtime truth/provenance refs. |
| Data Cloud evidence | Provider contracts exist, platform provider backing incomplete | Store ProductUnitIntent evidence/provenance in Data Cloud platform mode. |
| Tests | Not enough end-to-end evidence | Add Studio → Kernel API → registry preview/apply contract tests. |

**File-by-file plan**

| File | Required action | Tests |
|---|---|---|
| `platform/typescript/kernel-product-contracts/src/ProductUnitIntent*` | Export Zod request/response schemas for preview/apply, including producer, target provider, evidence refs, and lifecycle readiness. | Contract schema tests. |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts` | Ensure `applyProductUnitIntent` returns stable, user-visible blocked reason codes and writes runtime truth/provenance in platform mode. | Unit tests for preview, apply, invalid intent, missing provider, platform mode. |
| `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts` | Add ProductUnitIntent preview/apply routes or route metadata if not already exposed elsewhere. | API handler tests for auth/scope/provider errors. |
| `platform/typescript/ghatana-studio/src/routes/IdeasPage.tsx` | Add action to create ProductUnitIntent candidate from idea/blueprint. | RTL interaction test. |
| `platform/typescript/ghatana-studio/src/routes/BlueprintsPage.tsx` | Add preview/apply ProductUnitIntent panel with blocked states. | RTL + client mock tests. |
| `products/yappc/kernel-bridge/**` | Emit ProductUnitIntent and semantic artifact evidence through shared contracts only. | Boundary tests: no Kernel internals imported by YAPPC. |

---

### Journey 2 — Direct Product Development Kernel Usage

**Expected flow**

```text
Open Kernel area → select ProductUnit → inspect product shape → create lifecycle plan → run validate/test/build/package/deploy/verify → inspect gates/artifacts/deployments/approvals/health
```

**Current flow**

- Studio Lifecycle page has product selector, phase selector, environment selector, provider-mode selector, dry-run toggle, execute button, run list, manifest panels, approval queue, failure diagnostics, and validation command display.
- Kernel lifecycle API handlers expose product units, plan/execute, runs, manifests, and approvals.
- Kernel service writes plan/result JSON, pointer refs, events, runtime truth, and provenance.

**Gaps**

| Gap | Current state | Required fix |
|---|---|---|
| Contract drift | Studio duplicates Kernel schemas locally | Export Kernel response schemas and import them into Studio. |
| Product readiness gating | Registry distinguishes enabled/planned/partial, but UI and Kernel must enforce explicitly | Add lifecycle execution allowed guard and show `lifecycleReadiness` reasons. |
| Platform mode | UI exposes platform mode, but provider backing is not complete | Hide or disable platform mode unless Data Cloud provider context passes health/contract checks. |
| Manifest completeness | UI shows missing/corrupt/unavailable states; backend must produce all manifests deterministically | Enforce manifest completeness checks per phase. |

**File-by-file plan**

| File | Required action | Tests |
|---|---|---|
| `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts` | Export canonical response schemas or move them to `@ghatana/kernel-product-contracts`; stop Studio contract duplication. | API response schema tests. |
| `platform/typescript/ghatana-studio/package.json` | Add `@ghatana/kernel-lifecycle` dependency if schemas remain there. | Workspace dependency check. |
| `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts` | Replace duplicated phase/status/failure-code schemas with shared canonical schemas. | Client contract tests. |
| `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts` | Enforce product readiness and lifecycle allowed state before plan generation. | Tests: Digital Marketing allowed; PHR/Finance/FlashIt/YAPPC/Data Cloud blocked. |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts` | Return NOT_READY/gateOutputs for disabled/planned products and record blocked runtime truth. | Service tests for blocked lifecycle execution. |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx` | Show lifecycle readiness reason codes, required gates, and next actions from ProductUnit metadata. | RTL test for blocked planned products. |

---

### Journey 3 — Agentic Product Development

**Expected flow**

```text
User asks agent → agent proposes lifecycle/action plan → Kernel policy/mastery/risk/approval checks → Kernel executes adapters → Data Cloud stores evidence/provenance/memory → Studio displays results
```

**Current flow**

- Data Cloud Action gateway imports `AgentLifecycleActionRequestSchema`.
- Kernel provider contracts include lifecycle truth/event/provenance/memory contracts.
- Blueprint requires agents to execute through Kernel contracts and never run raw Gradle/pnpm/Docker commands directly.

**Gaps**

| Gap | Current state | Required fix |
|---|---|---|
| Agent action E2E | Partial; schema import exists, full UX/API path not proven | Implement gateway route and Studio Agents page flow around `AgentLifecycleActionRequest`. |
| Mastery/risk/approval | Target/partial | Add policy/risk/approval gate evaluation before Kernel action execution. |
| Evidence/memory | Provider contracts exist, platform persistence incomplete | Persist action evidence, provenance, memory, and evaluation refs through Data Cloud. |

**File-by-file plan**

| File | Required action | Tests |
|---|---|---|
| `platform/typescript/kernel-product-contracts/src/agentic-lifecycle/*` | Ensure request/result schemas include tool permissions, risk, approvals, evidence, rollback, and verification refs. | Contract tests. |
| `products/data-cloud/planes/action/gateway/src/app.ts` | Add explicit governed agent lifecycle action endpoint if not fully present; no generic raw-command path. | Auth/scope/policy tests. |
| `platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx` | Add agent action plan preview, approval requirement, execution result, evidence/provenance refs. | RTL + accessibility tests. |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts` | Add service-level action entrypoint or adapter so agents call Kernel actions, not raw tools. | Unit/integration tests. |

---

### Journey 4 — Digital Marketing Lifecycle Pilot

**Expected flow**

```text
Select Digital Marketing → inspect ProductUnit shape → run validate/test/build/package/deploy/verify → inspect manifests → verify local health → Studio recommendations
```

**Current flow**

- Digital Marketing is the only product marked lifecycle `enabled` and migration `ready`.
- Registry lists backend-api and web surfaces, bridge conformance, bridge adapter tests, toolchains, artifacts, deployment target, and local environment.
- Root scripts include Digital Marketing build/test/validate/package/deploy/verify/plan commands.

**Gaps**

| Gap | Current state | Required fix |
|---|---|---|
| Golden path proof | Registry declares ready, but this audit did not execute commands | Make CI run full Digital Marketing lifecycle smoke. |
| Manifest details | Artifact/deployment/health panels exist | Ensure manifests are complete, linked, and generated every run. |
| Studio recommendations | Health/recommendation logic is partial | Add recommendation panel from gate/artifact/deployment evidence. |

**File-by-file plan**

| File | Required action | Tests |
|---|---|---|
| `products/digital-marketing/kernel-product.yaml` | Verify all lifecycle phases, artifacts, gates, health checks, and local deploy config are complete. | Manifest conformance tests. |
| `products/digital-marketing/dm-kernel-bridge/src/main/java/...` | Ensure bridge emits product-neutral Kernel evidence and no product-local lifecycle runner. | Bridge tests + ArchUnit boundary tests. |
| `platform/typescript/kernel-lifecycle/src/execution/*` | Ensure Digital Marketing run produces plan/result/gate/artifact/deployment/verify manifests. | E2E smoke test. |
| `.github/workflows/*` | Add required Digital Marketing lifecycle pilot job. | CI required check. |

---

### Journey 5 — Artifact Intelligence

**Expected flow**

```text
YAPPC imports/decompiles source → compiler/decompiler produces semantic evidence → Data Cloud stores graph/provenance → Kernel consumes references → Studio displays risks/residuals/recommendations
```

**Current flow**

- YAPPC is a platform-provider product with a large backend/frontend/module graph.
- Registry explicitly requires artifact-intelligence boundary and creator/Kernel lifecycle separation before lifecycle enablement.
- Commit title and implementation log indicate artifact compiler/decompiler work is active, but this audit is snapshot-oriented and treats the end-to-end journey as partial until the handoff is verified.

**Gaps**

| Gap | Current state | Required fix |
|---|---|---|
| Evidence contracts | Target/partial | Export `ArtifactGraphSummary`, `ProductShapeEvidence`, `ResidualIslandReport`, `RiskHotspotReport`, `GeneratedChangeSetSummary` through shared contract package. |
| Data Cloud storage | Partial | Persist graph/provenance/memory with tenant/workspace/project scope. |
| Kernel consumption | Partial | Kernel consumes references only, not YAPPC internals. |
| Studio visualization | Partial | Add artifact intelligence panels under Artifacts/Canvas/Develop. |

**File-by-file plan**

| File | Required action | Tests |
|---|---|---|
| `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**` | Canonicalize evidence contracts and schema versions. | Contract schema tests. |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/**` | Emit semantic evidence references without importing Kernel internals. | Boundary + golden fixture tests. |
| `products/yappc/kernel-bridge/**` | Publish evidence refs to Data Cloud and Kernel handoff contracts. | Integration tests. |
| `products/data-cloud/**/provenance|graph|memory/**` | Store semantic graph/provenance with retention/privacy metadata. | Repository/integration tests. |
| `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx` | Display residual islands, risk hotspots, evidence refs, and recommendations. | RTL + a11y tests. |

---

### Journey 6 — Data Cloud Foundation

**Expected flow**

```text
Kernel bootstrap mode works without Data Cloud → Data Cloud is built/deployed → platform mode uses Data Cloud providers → runtime truth/events/provenance/memory flow through Data Cloud
```

**Current flow**

- Bootstrap file-backed providers exist and are guarded against production by `assertBootstrapOnly`.
- Platform mode validation rejects file-backed providers and requires events/artifacts/health/approvals/provenance/memory/runtimeTruth.
- Data Cloud Action gateway exposes provider endpoints, but schemas and persistence are not production-grade yet.

**Critical gaps**

| Gap | Current state | Required fix |
|---|---|---|
| Runtime truth schema mismatch | Gateway runtime truth schema uses `{ productUnitId, observedAt, state }`; Kernel contract expects runId, phase, status, evidenceRefs, etc. | Replace gateway provider schemas with canonical Kernel provider schemas. |
| In-memory fallback | Gateway falls back to `new InMemoryProviderStore()` | Disallow in production; require durable Data Cloud provider store. |
| Scope filtering | Provider records contain tenant/workspace/project but query paths filter primarily by tenant/providerType | Enforce tenant + workspace + project isolation on every provider query. |
| Platform bridge | Partial | Implement actual Data Cloud-backed provider classes with `backingStore: 'data-cloud'`. |

**File-by-file plan**

| File | Required action | Tests |
|---|---|---|
| `products/data-cloud/planes/action/gateway/src/app.ts` | Replace inline provider schemas with canonical contract schemas; remove production in-memory fallback; enforce workspace/project filters. | `kernel-provider-routes.contract.test.ts`, authz tests. |
| `products/data-cloud/planes/action/gateway/src/provider-store.ts` | Add durable provider store or fail closed when not configured outside test/local. | Durable repository tests. |
| `products/data-cloud/extensions/kernel-bridge/**` | Implement Data Cloud-backed Kernel providers for events/artifacts/health/approvals/provenance/memory/runtimeTruth. | Contract tests against `LifecycleProviders.ts`. |
| `platform/typescript/kernel-product-contracts/src/provider/LifecycleProviders.ts` | Export Zod schemas and provider record contracts used by gateway and providers. | Schema tests. |
| `scripts/check-data-cloud-platform-providers.mjs` | Fail on in-memory provider fallback and schema drift. | Script fixture tests. |

---

### Journey 7 — Future Product Shape Readiness

| Product | Registry current state | Required next action |
|---|---|---|
| PHR | Lifecycle planned, enabled false, requires consent, PII, audit, FHIR, data-sovereignty gates | Keep blocked; implement regulated gates before execution. |
| Finance | Lifecycle planned, enabled false, requires regulatory gates, promotion approval, multi-module build validation, portal/operator/SDK adapters | Keep blocked; validate multi-module Gradle graph and adapters. |
| FlashIt | Lifecycle planned, enabled false, requires mobile adapters, preview security, personal-data classification, IPA/AAB artifacts | Keep blocked; do not enable mobile execution until adapters/artifact contracts exist. |
| Data Cloud | Platform-provider, conformance false, requires bootstrap/platform separation and runtime truth provider | Do not treat as ordinary product; finish provider bridge. |
| YAPPC | Platform-provider, conformance false, requires ProductUnitIntent export, artifact-intelligence boundary, creator/Kernel separation | Do not enable ordinary lifecycle until handoff boundary is proven. |
| Tutorputor | Partial lifecycle, enabled false, requires content safety, learner-data privacy, model-output evaluation | Keep blocked; add AI/content safety gates. |
| Audio-Video | Disabled, requires executable surfaces and lifecycle profile | Define lifecycle surfaces and media privacy/retention gates. |
| DCMAAR | Disabled, requires threat-model/security gates | Define executable surfaces and security evidence. |
| Aura | Demo/example, disabled, requires recommendation safety/explainability evidence | Keep disabled until product scope is formalized. |

---

## 5. Capability Ownership Matrix

| Capability | Correct owner | Current location | Problem | Required move/fix | Required tests |
|---|---|---|---|---|---|
| ProductUnit contracts | Kernel contracts | `platform/typescript/kernel-product-contracts` | Good location; response schemas not fully reused by Studio | Export all API/response schemas or canonicalize in Kernel lifecycle | Contract tests |
| ProductUnitIntent application | Kernel service + YAPPC producer | `KernelLifecycleService.applyProductUnitIntent`, YAPPC bridge | API/UX handoff not proven | Add preview/apply routes and Studio/YAPPC flow | E2E handoff tests |
| Lifecycle execution truth | Product Development Kernel | `platform/typescript/kernel-lifecycle` | Strong foundation; product readiness gating needs hard enforcement | Enforce lifecycle allowed before plan/execute | Disabled-product tests |
| Bootstrap providers | Kernel providers | `platform/typescript/kernel-providers` | Good file-backed mode; production guard exists | Keep bootstrap local/test only | Bootstrap provider tests |
| Platform providers | Data Cloud bridge | `products/data-cloud/.../kernel-bridge`, gateway provider routes | Schema mismatch and in-memory fallback | Implement durable Data Cloud-backed providers | Provider contract tests |
| Studio lifecycle UX | Ghatana Studio | `platform/typescript/ghatana-studio` | UI exists but duplicates API contracts and has partial i18n | Import shared schemas; expose readiness/actionable blocked states | RTL/a11y/client tests |
| Digital Marketing pilot | Product + Kernel bridge | `products/digital-marketing/**` | Correct pilot; must be golden-path CI | Run full validate/test/build/package/deploy/verify smoke | E2E lifecycle tests |
| Artifact intelligence | YAPPC | `products/yappc/**`, shared artifact contracts target | Strong workstream but handoff incomplete | Publish semantic evidence refs only | Golden fixture + boundary tests |
| Runtime truth | Data Cloud | Action gateway/provider store | In-memory fallback and schema mismatch | Durable scoped provider store | Integration tests |
| Agentic lifecycle | AEP/Data Cloud + Kernel | Data Cloud Action Plane + Kernel contracts | Schema present, E2E not proven | Route all product-development agent actions through Kernel | Agent lifecycle E2E |
| UI consistency/i18n/a11y | Shared DS + Studio/products | Studio and product UIs | Studio is partial; hardcoded strings remain | Translation keys, semantic panels, keyboard navigation | a11y tests |
| CI/governance | Platform Coherence | root scripts, workflows | Many scripts exist; release gates must be enforced | Required CI matrix | Workflow checks |

---

## 6. File-by-File Implementation Plan

### Workstream 1 — Ghatana Studio UI/UX and API Contracts

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `platform/typescript/ghatana-studio/package.json` | Depends on Kernel artifact/deployment/release/contracts but not `@ghatana/kernel-lifecycle` | Add dependency or move schemas into `@ghatana/kernel-product-contracts` and import there | Workspace dependency check | `pnpm --dir platform/typescript/ghatana-studio type-check` |
| `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts` | Duplicates phases/statuses/failure codes and Zod schemas | Import canonical schemas; remove local finite drift lists where possible | `src/api/__tests__/kernelLifecycleClient.test.ts` | `pnpm --dir platform/typescript/ghatana-studio test` |
| `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx` | Product readiness and scope/auth behavior are partial | Pass runtime identity/scope consistently; surface disabled/planned product metadata; preserve degraded states | Data provider tests for unconfigured/degraded/blocked | `pnpm --dir platform/typescript/ghatana-studio test` |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx` | Good lifecycle UI, but hardcoded visible strings and readiness UX gaps remain | Use translation keys for all text; show lifecycleReadiness reason codes, gates, next work; disable execution for non-ready products | RTL + a11y tests | `pnpm --dir platform/typescript/ghatana-studio test:a11y` |
| `platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx` | Agentic product-development journey not proven | Add agent action-plan preview, approval, execute, evidence result panel | RTL + mocked client tests | `pnpm --dir platform/typescript/ghatana-studio test` |
| `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx` | Artifact evidence/risk/residual journey partial | Display semantic evidence refs, residual islands, risk hotspots, generated change sets | Artifact panel tests | `pnpm --dir platform/typescript/ghatana-studio test` |

### Workstream 2 — Product Development Kernel Backend / Lifecycle / Providers / Plugins

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `platform/typescript/kernel-product-contracts/src/provider/LifecycleProviders.ts` | Interfaces exist but gateway cannot import matching runtime validation schemas | Add/export Zod schemas for events/artifacts/health/approvals/provenance/memory/runtimeTruth provider records | Provider schema tests | `pnpm --dir platform/typescript/kernel-product-contracts test` |
| `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts` | Good handlers; response schemas not shared with Studio | Export response schemas or consume schemas from contracts; include readiness metadata | API handler contract tests | `pnpm --dir platform/typescript/kernel-lifecycle test` |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts` | Runtime truth/provenance present; readiness guard needs strengthening | Block plan/execute when lifecycle disabled/planned/not allowed; record blocked truth with reason codes | Service tests | `pnpm --dir platform/typescript/kernel-lifecycle test` |
| `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts` | Must verify surfaces/adapters/gates before plan | Add deterministic NOT_READY plan/gate output for missing surfaces/adapters/gates | Planner tests | `pnpm --dir platform/typescript/kernel-lifecycle test` |
| `platform/typescript/kernel-providers/src/factory/createBootstrapKernelProviders.ts` | Good bootstrap guard exists | Keep file-backed bootstrap local/test only; add docs/tests for production guard | Factory tests | `pnpm --dir platform/typescript/kernel-providers test` |
| `scripts/check-kernel-platform-lifecycle.mjs` | Needs to be release gate | Validate lifecycle truth outputs and provider mode constraints | Script fixture tests | `pnpm check:kernel-platform-lifecycle` |

### Workstream 3 — Data Cloud Foundation Providers / Runtime Truth / Memory

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `products/data-cloud/planes/action/gateway/src/app.ts` | Provider endpoint schemas drift from Kernel contracts; in-memory fallback can be used | Use canonical provider schemas; fail closed when durable store missing outside local/test; scope filters by tenant/workspace/project | Provider route contract/authz tests | `pnpm --dir products/data-cloud/planes/action/gateway test` |
| `products/data-cloud/planes/action/gateway/src/provider-store.ts` | Needs durable production-backed store and scoped queries | Add Data Cloud-backed durable store interface/implementation, cursor pagination, retention/redaction | Repository tests | `pnpm --dir products/data-cloud/planes/action/gateway test` |
| `products/data-cloud/extensions/kernel-bridge/**` | Platform provider bridge is not fully validated | Implement DataCloud lifecycle providers with `backingStore: 'data-cloud'` | Contract tests with `validateKernelLifecycleProviderContext` | `pnpm check:data-cloud-platform-providers` |
| `products/data-cloud/planes/action/kernel-bridge/**` | Bridge boundaries need clarity | Keep bridge product-owned; no platform package importing Data Cloud internals | Boundary tests | `pnpm check:kernel-data-cloud-boundary` if available; otherwise `pnpm check:domain-boundaries` |
| `scripts/check-data-cloud-platform-providers.mjs` | Needs to catch in-memory and schema drift | Fail on `new InMemoryProviderStore()` production path and non-canonical provider schemas | Script tests | `pnpm check:data-cloud-platform-providers` |

### Workstream 4 — YAPPC Creator / Artifact Intelligence / Visibility

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**` | Evidence contract target exists but must be authoritative | Version `ArtifactGraphSummary`, `ProductShapeEvidence`, `ResidualIslandReport`, `RiskHotspotReport`, `GeneratedChangeSetSummary` | Schema tests | `pnpm --dir platform/typescript/kernel-product-contracts test` |
| `products/yappc/kernel-bridge/**` | Handoff boundary not fully proven | Emit only ProductUnitIntent + evidence references; do not expose compiler internals | Boundary tests | `pnpm check:yappc-product-unit-intent-handoff` |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/**` | Compiler/decompiler is active but journey remains partial until evidence publication is proven | Publish semantic evidence refs, source checksums, residuals, risk reports through contracts | Golden fixtures + round-trip tests | `pnpm --dir products/yappc/frontend/libs/yappc-artifact-compiler test` |
| `products/yappc/frontend/web/src/**` | Creator lifecycle must stay distinct from Kernel product lifecycle | UI labels and actions distinguish YAPPC Creator Lifecycle vs Kernel Product Lifecycle | UI tests | `pnpm check:yappc-artifact-intelligence-boundary` |

### Workstream 5 — Digital Marketing Pilot

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `products/digital-marketing/kernel-product.yaml` | Must remain the only enabled lifecycle golden path | Verify lifecycle phases, surfaces, artifacts, health checks, local deployment config | Manifest tests | `pnpm check:digital-marketing-lifecycle-pilot` |
| `products/digital-marketing/dm-kernel-bridge/src/main/java/**` | Bridge must remain product-specific and Kernel-contract based | Ensure no product-local lifecycle runner and all evidence emits through Kernel bridge | Bridge tests | `./gradlew :products:digital-marketing:dm-kernel-bridge:check` |
| `products/digital-marketing/ui/**` | Studio/product UI consistency should be proven | Use shared design system/status vocabulary/empty-error states | UI tests | `pnpm --dir products/digital-marketing/ui test` |
| `.github/workflows/**` | Pilot needs CI proof | Add validate/test/build/package/deploy/verify local smoke job | Workflow check | `pnpm check:digital-marketing-lifecycle-pilot` |

### Workstream 6 — Shared Libraries / Design System / Builder / Canvas / Code Editor

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `platform/typescript/design-system/**` | Must be the single source of UI primitives | Add/verify shared LifecycleStatus, GateResult, ManifestState, ApprovalState components only after proven reuse | Component tests | `pnpm check:design-system-conformance` |
| `platform/typescript/ui-builder/**` | Builder contracts must stay product-neutral | Keep BuilderDocument generic; no YAPPC creator-specific logic | Contract/golden tests | `pnpm --dir platform/typescript/ui-builder test` |
| `platform/typescript/canvas/**` | Canvas must remain reusable primitive | Add semantic zoom/context shift API only product-neutral | Canvas tests | `pnpm --dir platform/typescript/canvas test` |
| `platform/typescript/i18n/**` | Studio/product messages need i18n readiness | Add keys for lifecycle/manifest/approval/readiness text | i18n tests | `pnpm check:shared-ui-state-coverage` |

### Workstream 7 — Product Shape Matrix / Future Product Readiness

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `config/canonical-product-registry.json` | Product lifecycle readiness is present but must be explicit and enforced | Add explicit `lifecycleExecutionAllowed` for every product; only Digital Marketing true until validated | Registry tests | `pnpm check:product-registry` |
| `config/canonical-product-registry.schema.json` | Diff indicates new fields; enforce them | Require/validate `lifecycleExecutionAllowed` and `gateOutputs.NOT_READY` where applicable | Schema tests | `pnpm check:lifecycle-registry-config-drift` |
| `scripts/check-product-shape-capability-matrix.mjs` | Must validate future shapes without enabling them | Fail if planned products are executable or if missing required gates/adapters | Script tests | `pnpm check:product-shape-capability-matrix` |
| `products/*/kernel-product.yaml` | Some products are planned/partial | Add product-local readiness evidence only; do not enable execution prematurely | Manifest tests | `pnpm check:product-manifest-contracts` |

### Workstream 8 — CI/CD / Checks / Docs Cleanup

| File | Current issue | Target change | Tests | Validation |
|---|---|---|---|---|
| `.github/workflows/**` | Many checks exist as scripts; not all are necessarily enforced | Create required workflow matrix for architecture, Kernel, Data Cloud providers, Studio API, Digital Marketing pilot, production readiness | Workflow dry-run / CI | GitHub Actions required checks |
| `scripts/check-production-readiness.mjs` | Needs to remain authoritative | Add checks for provider schema drift, in-memory fallback, hardcoded Studio strings, disabled-product execution | Script fixture tests | `pnpm check:production-readiness` |
| `scripts/check-current-state-claims.mjs` | Critical for target/current distinction | Fail target-state claims without classification | Script fixture tests | `pnpm check:current-state-claims` |
| `docs/architecture/**` | Avoid duplicate source-of-truth docs | Mark authoritative docs and make dependent docs reference them | Doc checks | `pnpm check:doc-truth` |
| `docs/implementation/**` | Implementation trackers can become stale | Either classify as tracker/history or exclude from truth sources | Doc checks | `pnpm check:doc-claims-evidence` |

---

## 7. Release Plan

### Release 0 — Unified shell, terminology, navigation, and core checks

**Goal:** Make Studio/Kernel/Data Cloud/YAPPC terminology and readiness states coherent.  
**Scope:** Studio schema reuse, product readiness display, lifecycle blocking, CI guardrails.  
**Exit criteria:**

- Studio imports canonical lifecycle schemas.
- Disabled/planned products cannot execute lifecycle phases.
- Product readiness reasons display in Studio.
- Data Cloud provider schema drift check exists.

**Validation:**

```bash
pnpm check:architecture-boundaries
pnpm check:studio-kernel-api
pnpm check:kernel-product-unit-provider-contracts
pnpm check:lifecycle-registry-config-drift
pnpm check:product-shape-capability-matrix
pnpm check:production-readiness
```

### Release 1 — Digital Marketing lifecycle pilot E2E

**Goal:** Prove one complete product lifecycle end-to-end.  
**Scope:** Digital Marketing validate/test/build/package/deploy/verify, manifests, health, Studio display.  
**Exit criteria:**

- Digital Marketing local lifecycle commands succeed.
- Every lifecycle run writes plan/result/gate/artifact/deployment/verify manifests.
- Studio displays manifests, approvals, failures, and health.

**Validation:**

```bash
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
pnpm check:digital-marketing-lifecycle-pilot
```

### Release 2 — Agentic development support

**Goal:** Agents propose and execute product-development actions only through Kernel contracts.  
**Scope:** AgentLifecycleActionRequest, risk/mastery/policy/approval checks, Studio Agents page, evidence storage.  
**Exit criteria:**

- No agent raw Gradle/pnpm/Docker execution path for product lifecycle.
- Every agent action has risk, approval, evidence, runtime truth, and rollback/verification refs.

**Validation:**

```bash
pnpm check:agentic-lifecycle-action-contracts
pnpm check:kernel-platform-lifecycle
pnpm check:observability-conformance
```

### Release 3 — Data Cloud platform-mode providers

**Goal:** Make platform mode real and durable.  
**Scope:** Data Cloud-backed lifecycle providers, durable provider store, runtime truth/provenance/memory, tenant/workspace/project isolation.  
**Exit criteria:**

- Platform mode rejects file/in-memory providers.
- Provider records are durable and scoped.
- Runtime truth schema matches Kernel contract.

**Validation:**

```bash
pnpm check:data-cloud-platform-providers
pnpm check:kernel-provider-mode
pnpm check:data-access-contract
pnpm check:observability-conformance
```

### Release 4 — Artifact intelligence integration

**Goal:** Connect YAPPC artifact compiler/decompiler to Data Cloud evidence and Kernel gates.  
**Scope:** Semantic evidence contracts, residual/risk visualization, ProductShapeEvidence references, Data Cloud graph/provenance.  
**Exit criteria:**

- YAPPC publishes semantic evidence refs.
- Kernel consumes references only.
- Studio displays residuals, risks, and recommendations.

**Validation:**

```bash
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:yappc-product-unit-intent-handoff
pnpm --dir products/yappc/frontend/libs/yappc-artifact-compiler test
```

### Release 5 — Product shape expansion readiness

**Goal:** Enable additional products only when their shape-specific gates are executable.  
**Scope:** PHR, Finance, FlashIt, Tutorputor, Data Cloud, YAPPC, Aura, DCMAAR, Audio-Video shape readiness.  
**Exit criteria:**

- Each product has explicit lifecycleExecutionAllowed state.
- Planned products stay blocked.
- Product-specific gates/adapters/artifacts are validated before enabling.

**Validation:**

```bash
pnpm check:product-shape-capability-matrix
pnpm check:product-manifest-contracts
pnpm check:product-ci-matrices
pnpm check:product-registry-drift
```

---

## 8. Validation Command Suite

Run these as the complete post-implementation gate:

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-platform-lifecycle
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:shared-ui-state-coverage
pnpm check:production-readiness
pnpm check:secret-default-credentials
pnpm check:observability-conformance
pnpm check:data-access-contract
./gradlew build
./gradlew check
```

Targeted commands:

```bash
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-providers test
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio test:a11y
pnpm --dir products/data-cloud/planes/action/gateway test
pnpm --dir products/yappc/frontend/libs/yappc-artifact-compiler test
pnpm --dir products/digital-marketing/ui test
./gradlew :products:digital-marketing:dm-kernel-bridge:check
./gradlew :products:digital-marketing:dm-integration-tests:check
```

---

## 9. Final Verdict

Ghatana at commit `17eaf77d6c15c8f35397669724ea051011377e71` is **architecturally coherent and structurally promising**, but **not yet world-class executable end-to-end**.

The most important fix is not adding more features. The most important fix is making the existing architecture truthful and enforceable:

```text
Digital Marketing = executable pilot.
PHR / Finance / FlashIt / Tutorputor / Aura / DCMAAR / Audio-Video = blocked until product-specific gates/adapters/evidence are real.
Data Cloud / YAPPC = platform providers, not ordinary lifecycle products yet.
Kernel = only lifecycle truth executor.
YAPPC = creator/artifact-intelligence producer.
Data Cloud = durable runtime truth/provenance/memory provider.
Studio = unified, contract-backed UX over the above.
```

Implement Release 0 first. It will reduce repeated 40+ item audits by removing root drift: provider contract mismatch, in-memory runtime truth, Studio/Kernel schema duplication, and disabled-product execution ambiguity.




# Ghatana World-Class Product Audit TODO Checklist

**Repo:** `samujjwal/ghatana`  
**Commit:** `17eaf77d6c15c8f35397669724ea051011377e71`

## P0 — Must fix first

| ID | File(s) | TODO | Validation |
|---|---|---|---|
| P0-01 | `products/data-cloud/planes/action/gateway/src/app.ts` | Replace inline provider schemas with canonical Kernel provider schemas for events/artifacts/health/approvals/provenance/memory/runtimeTruth. Runtime truth must include runId, phase, status, evidenceRefs, providerMode/privacy/retention where applicable. | `pnpm --dir products/data-cloud/planes/action/gateway test` |
| P0-02 | `products/data-cloud/planes/action/gateway/src/app.ts`, `provider-store.ts` | Remove production fallback to `new InMemoryProviderStore()`. Fail closed unless a durable Data Cloud provider store is configured. Keep in-memory only behind explicit local/test config. | `pnpm check:data-cloud-platform-providers` |
| P0-03 | `products/data-cloud/planes/action/gateway/src/provider-store.ts` | Enforce tenant + workspace + project scoping on all provider list/latest queries. | `pnpm check:data-access-contract` |
| P0-04 | `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts` | Remove duplicated lifecycle phases/statuses/failure code schemas; import canonical schemas from Kernel lifecycle/contracts. | `pnpm check:studio-kernel-api` |
| P0-05 | `platform/typescript/ghatana-studio/package.json` | Add canonical dependency on `@ghatana/kernel-lifecycle` if response schemas live there. | `pnpm install --lockfile-only && pnpm check:cross-workspace-deps` |
| P0-06 | `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`, `KernelLifecycleService.ts` | Enforce lifecycle execution allowed: only Digital Marketing should execute until other products are validated. Planned/partial/platform-provider products must return NOT_READY with reason codes. | `pnpm check:product-shape-capability-matrix` |
| P0-07 | `config/canonical-product-registry.json`, schema | Add explicit `lifecycleExecutionAllowed` to every product entry and enforce schema. | `pnpm check:lifecycle-registry-config-drift` |

### P0 Progress Update (2026-05-16)

| ID | Status | Evidence | Notes |
|---|---|---|---|
| P0-04 | ✅ Completed | `pnpm --dir platform/typescript/kernel-lifecycle test` (22 files, 261 tests passed) | `ghatana-studio` now reuses canonical lifecycle constants exported by `@ghatana/kernel-lifecycle`; duplicated local phase/status/failure lists removed. |
| P0-05 | ✅ Completed | `pnpm install --lockfile-only` | Added `@ghatana/kernel-lifecycle` workspace dependency in Studio package manifest and synced lockfile. |
| P0-06 | ✅ Completed | `pnpm --dir platform/typescript/kernel-lifecycle test` (includes planner + disabled-product suites) | Planner/service enforce NOT_READY behavior for execution-blocked products via `ProductLifecycleNotReadyError` mapped to `KernelLifecycleError` and API 409 semantics. |
| P0-07 | ✅ Completed | `pnpm --dir platform/typescript/kernel-lifecycle test` (disabled-product and planner coverage) | Canonical registry schema requires `lifecycleExecutionAllowed`; registry entries now explicitly set execution eligibility. |

Validation note: `pnpm --dir platform/typescript/ghatana-studio type-check` still reports pre-existing `@ghatana/ui-builder` export/type issues unrelated to P0-04..P0-07.

### Follow-up Remediation Delta (2026-05-16)

| Area | Status | Evidence | Fix summary |
|---|---|---|---|
| Kernel lifecycle compile health | ✅ Clean | `pnpm --dir platform/typescript/kernel-lifecycle build` | Fixed `KernelLifecycleService` typing regressions (registry provider assignment + exact optional property handling for `correlationId`). |
| Kernel lifecycle regression coverage | ✅ Clean | `pnpm --dir platform/typescript/kernel-lifecycle test` (22 files, 261 tests passed) | Confirmed planner/service not-ready gating and manifest/runtime-truth paths remain stable. |
| Data Cloud gateway auth/lifecycle route tests | ✅ Clean | `pnpm --dir products/data-cloud/planes/action/gateway test` (12 files, 130 tests passed) | Updated JWT fixtures to use valid HMAC signatures and aligned agentic/error route tests with current scope/schema guards. |
| Studio type-check residual blocker | ✅ Resolved | `pnpm --dir platform/typescript/ghatana-studio type-check` | Resolved package resolution blocker for `@ghatana/kernel-lifecycle` after rebuilding package declarations and workspace install refresh. |

## P1 — Release 1/2 hardening

| ID | File(s) | TODO | Validation |
|---|---|---|---|
| P1-01 | `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx` | Display lifecycleReadiness reason codes, required gates, and nextRequiredWork for blocked products. | `pnpm --dir platform/typescript/ghatana-studio test` |
| P1-02 | `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx` | Replace hardcoded user-facing strings with i18n keys. | `pnpm --dir platform/typescript/ghatana-studio test:a11y` |
| P1-03 | `products/digital-marketing/kernel-product.yaml`, `dm-kernel-bridge/**` | Make Digital Marketing the golden lifecycle pilot with validated plan/result/gate/artifact/deployment/verify manifests. | `pnpm check:digital-marketing-lifecycle-pilot` |
| P1-04 | `.github/workflows/**` | Add required Digital Marketing lifecycle smoke job. | GitHub Actions required checks |
| P1-05 | `platform/typescript/kernel-product-contracts/src/agentic-lifecycle/**` | Harden AgentLifecycleActionRequest/Result contracts with risk, approval, evidence, verification, rollback refs. | `pnpm check:agentic-lifecycle-action-contracts` |
| P1-06 | `products/data-cloud/planes/action/gateway/src/app.ts` | Add or harden governed agent lifecycle action route; prevent raw tool execution. | Gateway route tests |
| P1-07 | `platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx` | Add agent lifecycle action plan preview, approval, execution result, evidence panel. | Studio route tests |

## P2 — Artifact intelligence and shared UX hardening

| ID | File(s) | TODO | Validation |
|---|---|---|---|
| P2-01 | `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**` | Canonicalize semantic evidence contracts: ArtifactGraphSummary, ProductShapeEvidence, ResidualIslandReport, RiskHotspotReport, GeneratedChangeSetSummary. | Contract tests |
| P2-02 | `products/yappc/kernel-bridge/**` | Publish ProductUnitIntent and artifact evidence refs only; do not leak YAPPC internals into Kernel. | `pnpm check:yappc-product-unit-intent-handoff` |
| P2-03 | `products/yappc/frontend/libs/yappc-artifact-compiler/src/**` | Emit semantic evidence refs with source checksums/residuals/risk reports and Data Cloud provenance targets. | Artifact compiler golden tests |
| P2-04 | `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx` | Display residual islands, risk hotspots, evidence refs, and recommendations. | Studio artifact tests |
| P2-05 | `platform/typescript/design-system/**` | Extract shared status/manifest/approval components only after two consumers prove reuse. | `pnpm check:design-system-conformance` |

## P3 — Governance and cleanup

| ID | File(s) | TODO | Validation |
|---|---|---|---|
| P3-01 | `scripts/check-production-readiness.mjs` | Detect provider schema drift, in-memory provider fallback, hardcoded Studio strings, disabled-product execution. | `pnpm check:production-readiness` |
| P3-02 | `scripts/check-current-state-claims.mjs` | Fail target-state claims without current-state classification. | `pnpm check:current-state-claims` |
| P3-03 | `docs/architecture/**`, `docs/implementation/**` | Mark authoritative documents; make implementation trackers historical/non-authoritative where appropriate. | `pnpm check:doc-truth` |
| P3-04 | `.github/workflows/**` | Make architecture, Kernel, Data Cloud provider, Studio API, Digital Marketing pilot, and production readiness checks required. | GitHub branch protection |
