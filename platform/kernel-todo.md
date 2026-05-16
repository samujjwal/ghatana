## Execution basis

I executed the audit prompt against target commit `6e82fd932eae27940057b76d5674286851ff66bd`. The commit exists and is titled **“test fixes 4”**; the commit itself is primarily focused on YAPPC artifact compiler compatibility/test fixes and records a green artifact compiler test result of **32/32 test files and 262/262 tests passed**. 

I used the repo rules, hardened blueprint, and domain map as audit guardrails. The repo rules require reuse-first implementation, explicit boundaries, no silent failures, no unsafe defaults, strict TypeScript, tests for meaningful changes, Java `@doc.*` tags, and observability for important flows.  The hardened blueprint defines Ghatana Studio, Product Development Kernel, YAPPC, and Data Cloud ownership and first-release packaging boundaries.  The domain map confirms the core need for independently improvable domains plus a cross-cutting Platform Coherence domain to prevent drift and duplication. 

I could not run the repository build locally in this environment. This is a source-grounded codebase audit based on the target commit’s repository files, registry/config, package wiring, Kernel/Studio/YAPPC/Data Cloud/Digital Marketing files, and validation scripts inspected through GitHub.

## Progress snapshot

**Session date:** 2026-05-16

**Implementation status:** in progress

**Completed in this session:**

* Studio client now maps Kernel lifecycle API errors from the shared `ApiError.response.data` payload.
* Kernel lifecycle service now redacts persisted plan/result manifests and has regression coverage for redaction.
* Studio lifecycle page now renders translated lifecycle status labels instead of raw status codes.
* Studio lifecycle page now renders translated reason codes, blocked reasons, required gates, and next-work items.
* Focused tests for the Studio client, Kernel lifecycle service, and Lifecycle page all pass.
* Kernel lifecycle API handlers now require authentication by default, with a regression test proving the unauthenticated default behavior.
* HealthPage now renders translated health signal statuses and has a focused route regression test.
* AgentsPage now renders translated proposal badges for risk, governance, health, and rollback readiness.
* HomePage, DevelopPage, and CanvasPage now render translated status and risk labels with focused route regression tests.
* LearnPage now renders translated highest-risk labels in the recommendations and risk summary view.
* DeploymentsPage now renders translated verifier status labels, and ArtifactsPage now renders translated found/missing badges.
* Combined Studio route regression pass is green across Lifecycle, Health, Agents, Home, Develop, Canvas, Learn, Deployments, and Artifacts.
* YAPPC artifact compiler deterministic ID schemas now accept deterministic non-empty IDs (not UUID-only), and residual/model mapping contract tests are green.
* Compile-back change-op kind contract tests now align with the canonical kind-aware operation set (`add-component`, `remove-component`, `update-component-props`, `rename-component`, `manual-review`, `unsupported-operation`).
* SourceProviderRegistry Java tests are hardened with strict Mockito-safe stubbing and pass.
* ArtifactGraphRepository workspace scope Java tests are hardened to use ActiveJ async test execution patterns and pass.
* Full non-watch YAPPC artifact compiler test suite is green: 35/35 files and 296/296 tests passed.
* Kernel lifecycle API now accepts ProductUnitIntent preview/apply mutation metadata (`providerMode`, `correlationId`, `evidenceRefs`) and preserves auth + correlation behavior through the mutation path.
* Studio lifecycle client now has typed `previewProductUnitIntent` and `applyProductUnitIntent` regression coverage against `/api/kernel/lifecycle/product-unit-intents` for both preview and apply actions.
* Studio Ideas and Blueprints routes now have focused tests proving ProductUnitIntent preview/apply handoff actions are wired and callable from the UI layer.
* Focused post-change validation is green: kernel-lifecycle API handler/auth suites (266/266 tests) and ghatana-studio focused suites (94/94 tests).

**Current focus:** continue Release 0 by expanding Studio lifecycle journey depth (route behavior and evidence surfacing) while keeping ProductUnitIntent handoff and compiler/decompiler stabilization green

**Next measurable milestone:** extend ProductUnitIntent handoff beyond focused route/client tests into broader Studio lifecycle route integration coverage and follow-on Kernel/Data Cloud evidence-path validations

**Notes:** this file is now the live tracker for progress updates; code changes should be recorded here only after the corresponding slice is implemented and validated

---

# A. Executive summary

## Current status

Ghatana is **structurally close to the desired architecture**, but it is **not yet a world-class end-to-end product experience**. The repo already has strong foundations:

1. **Canonical monorepo wiring exists.** Root `package.json` exposes broad build, typecheck, lifecycle, governance, production-readiness, Kernel, Data Cloud, Studio, product-shape, security, observability, and Digital Marketing lifecycle checks. 

2. **pnpm workspace is generated from the canonical product registry**, covering platform TypeScript libraries, Studio, Product packages, Data Cloud, YAPPC, FlashIt, Tutorputor, DCMAAR, Audio-Video, and shared-service UIs. 

3. **Gradle root wiring is coherent**: Java 21 is enforced, build-logic is included, platform Java modules, platform contracts, platform-kernel modules, platform plugins, generated product includes, Data Cloud/YAPPC bridges, shared services, and integration tests are wired. 

4. **Kernel contracts are real and broad.** `@ghatana/kernel-product-contracts` exports ProductUnit, ProductUnitIntent, provider contracts, provider modes, lifecycle provider contracts, events, health snapshots, plugin contracts, and artifact intelligence contracts. 

5. **Kernel lifecycle service is no longer just a script router.** It can list/get ProductUnits, create lifecycle plans, execute plans through an executor, write manifests, maintain pointer state, emit lifecycle events, record runtime truth/provenance when providers exist, manage approvals, and apply ProductUnitIntent.  

6. **Ghatana Studio shell exists.** It has a unified navigation model, route ownership, route guards, lifecycle data context, typed Kernel lifecycle client, manifest panels, approval queue, and failure diagnostics.  

7. **Digital Marketing is correctly selected as the lifecycle pilot.** The registry marks it lifecycle-enabled, ready, pilot=true, with backend-api/web surfaces and lifecycle execution allowed. 

8. **YAPPC artifact intelligence is materially advanced.** The artifact compiler package is a bidirectional artifact-to-model pipeline with graph, model, residual, provenance, source-provider, merge, synthesis, compile-back, and builder exports.  

9. **Data Cloud bridge/provider direction exists.** Data Cloud bridge modules are product-side, depend on Kernel ports, and register Data Cloud-backed event, artifact, health, provenance, memory, knowledge, runtime truth, and policy evidence providers.  

## Main blockers

1. **The full Studio journey is not yet executable.** Studio routes exist, but Ideas, Lifecycle, Agents, Artifacts, Health, and Deployments are disabled/hidden until runtime, lifecycle, and Data Cloud evidence are available. 

2. **Product ideation → ProductUnitIntent → Kernel apply is not fully proven end-to-end in Studio.** Kernel has ProductUnitIntent application logic, and YAPPC has artifact/creator capabilities, but the customer-visible Studio journey needs a real UI/API handoff and evidence trail. 

3. **Only Digital Marketing is lifecycle-enabled.** PHR, Finance, FlashIt, Data Cloud, YAPPC, Audio-Video, DCMAAR, and others are correctly classified as planned/partial/disabled or platform-provider shapes, not customer-ready lifecycle products.   

4. **Platform mode is still not production-proven.** Kernel enforces missing provider checks for platform mode, and Data Cloud bridges exist, but Data Cloud registry conformance remains false/manifest-exempt and lifecycle execution is disabled.  

5. **The audit/check layer is broad but partly string-presence based.** For example, `check-data-cloud-platform-providers.mjs` verifies required files and required substrings, which is useful but not enough as a production-grade behavioral proof. 

6. **Agentic product development is still mostly contract/service-level.** Kernel exports `AgentLifecycleActionService`, but the full AEP/Data Cloud agent runtime → Kernel action → approval → evidence → Studio result loop still needs E2E implementation and tests. 

7. **Error model drift exists between API and client expectations.** API handlers return Kernel-safe error responses with `reasonCode` and `message`, while the Studio client schema expects an `error` field and attempts to parse an `Error` object directly.  

8. **The target commit adds legacy compatibility wrappers inside production artifact compiler source.** This fixed tests, but the wrapper naming and compatibility posture should be treated as temporary debt because the repo’s policy favors fix-forward migrations over compatibility shims. 

## Top 10 fixes

1. Wire **Studio Ideas/Blueprints/Canvas → YAPPC ProductUnitIntent export → Kernel preview/apply**.
2. Make **Digital Marketing lifecycle E2E** executable from Studio in bootstrap mode with clear local-dev scope/auth behavior.
3. Align **Kernel API error envelope** and **Studio client error parsing**.
4. Convert critical string-presence checks into **contract/integration tests**.
5. Finish **Data Cloud platform-mode providers** with durable backing stores, privacy/redaction enforcement, runtime truth, and integration tests.
6. Implement **agentic lifecycle action E2E** through AEP/Data Cloud → Kernel → approval → evidence → Studio.
7. Connect **artifact intelligence evidence bundles** into Data Cloud and Kernel gates.
8. Add **Studio artifact/residual/risk panels** backed by shared artifact intelligence contracts.
9. Keep future products disabled but enforce **shape-readiness gates** for PHR, Finance, FlashIt, Audio-Video, DCMAAR, TutorPutor, Aura, and external ProductUnits.
10. Clean up compatibility wrappers, stale TODO trackers, dead modules, deprecated package names, and duplicate UI/state patterns.

---

# B. System architecture map

| Layer                      |                                                                                      Current state | Correct role                                                                          | Assessment                                                |
| -------------------------- | -------------------------------------------------------------------------------------------------: | ------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| Ghatana Studio             | Exists as `@ghatana/ghatana-studio`; shell, nav, typed Kernel client, lifecycle page, route guards | Unified customer experience for idea → lifecycle → health → learning                  | Existing but partial                                      |
| YAPPC                      |        Large product-provider with frontend/backend/core modules; artifact compiler package exists | Ideation, blueprinting, canvas, generation, artifact intelligence, learning/evolution | Existing but partial                                      |
| Product Development Kernel |                          Kernel TS packages + Java platform-kernel modules + lifecycle service/API | ProductUnit lifecycle truth, plans, gates, manifests, execution, providers, approvals | Existing but partial                                      |
| Data Cloud                 |                             Platform-provider product, action plane modules, kernel bridge modules | Runtime truth, events, provenance, memory, Action Plane/AEP                           | Existing but partial / platform mode not proven           |
| Digital Marketing          |                                                                            Lifecycle-enabled pilot | First E2E lifecycle validation product                                                | Existing and pilot-ready, but needs full Studio E2E proof |
| PHR / Finance / FlashIt    |                                                     Registry entries, disabled lifecycle execution | Future shape-readiness products                                                       | Planned/partial only                                      |
| Shared libraries           |          design-system, canvas, ui-builder, code-editor, API, i18n, state, events, Kernel packages | Reusable platform primitives                                                          | Existing but needs conformance hardening                  |
| CI/checks                  |                                                                            Very broad script suite | Guardrails and regression gates                                                       | Existing but needs more behavioral proof                  |

---

# C. Journey-by-journey findings

## Journey 1 — Product ideation to ProductUnitIntent

**Current flow:** YAPPC and artifact compiler foundations exist. Kernel can validate/apply `ProductUnitIntent`. Studio has Ideas/Blueprints/Canvas routes, but Ideas is currently disabled by default, while Blueprints and Canvas are visible. 

**Expected flow:** User creates idea → builds blueprint/canvas → YAPPC exports `ProductUnitIntent` → Kernel previews/applies intent → ProductUnit appears in registry → lifecycle becomes available.

**Gaps:**

* Studio does not yet show a complete ProductUnitIntent export/apply workflow.
* Kernel has `applyProductUnitIntent`, but Studio client does not expose this operation yet.
* ProductUnitIntent evidence/provenance exists at Kernel service level only when providers are available.
* No visible customer flow proves YAPPC creator lifecycle remains separate from Kernel lifecycle.

**File-level plan:**

* `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts` — add typed `previewProductUnitIntent` and `applyProductUnitIntent` methods.
* `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts` — add ProductUnitIntent preview/apply route metadata and handlers.
* `platform/typescript/ghatana-studio/src/routes/IdeasPage.tsx` — replace disabled/placeholder experience with intent capture and handoff state.
* `platform/typescript/ghatana-studio/src/routes/BlueprintsPage.tsx` — add “Export ProductUnitIntent” action.
* `products/yappc/frontend/apps/api/src/**/product-unit-intents*` — make this the canonical YAPPC producer boundary.
* `platform/typescript/kernel-product-contracts/src/product-unit/**` — verify ProductUnitIntent schemas include evidence, producer, target, governance, and lifecycle request fields.
* Tests: Studio RTL tests for preview/apply states, API handler contract tests, YAPPC ProductUnitIntent generation tests, Kernel apply tests.

---

## Journey 2 — Direct Product Development Kernel usage

**Current flow:** Kernel lifecycle service, API handlers, lifecycle client, Studio LifecyclePage, manifest panels, approval queue, and Digital Marketing lifecycle config exist.  

**Expected flow:** User selects ProductUnit → views shape/readiness → creates lifecycle plan → runs validate/test/build/package/deploy/verify → inspects gates/artifacts/deployment/health → approves risky actions.

**Gaps:**

* Studio Lifecycle route is dynamically disabled until runtime/lifecycle is configured.
* LifecyclePage uses raw status/reason strings in some places, which weakens i18n and product vocabulary consistency.
* `safePlan` and `safeResult` currently return raw plan/result structures without redaction or explicit UI-safe projection. 
* API handlers support authorizer injection, but `requireAuthentication` defaults to false. 
* Digital Marketing has strong config, but full local E2E execution must be proven continuously.

**File-level plan:**

* `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts` — replace `safePlan`/`safeResult` pass-through with explicit redaction/projection.
* `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts` — align error schema and require authentication in non-local runtime.
* `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx` — convert raw status/reason/environment labels to i18n keys and shared status components.
* `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx` — add explicit unauthorized/blocked/degraded operator diagnostics.
* `products/digital-marketing/kernel-product.yaml` — keep as canonical pilot config; validate required manifests, approvals, provider modes, deploy/verify health.  
* Tests: API contract tests, Studio lifecycle E2E, Digital Marketing lifecycle smoke with manifest schema validation.

---

## Journey 3 — Agentic product development

**Current flow:** Kernel exports `AgentLifecycleActionService`, and Kernel/ProductUnit contracts include agentic lifecycle action surfaces.  Data Cloud has Action Plane/AEP modules in the registry, but lifecycle execution for Data Cloud remains disabled. 

**Expected flow:** Agent proposes action → Kernel checks policy/mastery/risk/approval/verification → Kernel executes adapter → Data Cloud stores evidence/provenance/memory → Studio displays result.

**Gaps:**

* No proven E2E path from Data Cloud Action Plane/AEP agent runtime into Kernel lifecycle execution.
* Studio Agents route is disabled unless Data Cloud evidence is ready. 
* Mastery/risk/approval checks need observable evidence, not just contracts.
* Agent-generated lifecycle actions need rollback/reversibility and policy denial tests.

**File-level plan:**

* `platform/typescript/kernel-lifecycle/src/agentic/AgentLifecycleActionService.ts` — harden risk/mastery/approval/evidence checks.
* `products/data-cloud/planes/action/**` — expose canonical AEP → Kernel action adapter.
* `products/data-cloud/libs/kernel-bridge-providers/src/index.ts` — add agent evidence/provenance provider paths and tests.
* `platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx` — display proposed actions, risk, required approval, evidence, rollback, verification status.
* Tests: agent action contract tests, policy-denied tests, approval-required tests, Data Cloud evidence persistence integration tests.

---

## Journey 4 — Digital Marketing lifecycle pilot

**Current flow:** Digital Marketing is the only lifecycle-enabled pilot in the registry, with backend-api and web surfaces, Gradle and pnpm adapters, compose-local deployment, lifecycleExecutionAllowed=true, and pilot=true.  Its `kernel-product.yaml` declares manifests, plugins, policy packs, local environment, telemetry, backend/web surfaces, phases, gates, approvals, package, deployment, provider modes, retention, and verify checks.  

**Expected flow:** Select Digital Marketing → run validate/test/build/package/deploy/verify → inspect manifests → verify health → view recommendations.

**Gaps:**

* Current validation script is strong but still partly static/script-based.
* Studio needs a guaranteed “happy path” seeded runtime context for the pilot.
* Approval flow exists but must be verified across deploy/promote/rollback with actual provider behavior.
* Gate evidence must not be synthetic; the script already checks for synthetic bootstrap-gate evidence in smoke mode. 

**File-level plan:**

* `scripts/check-digital-marketing-lifecycle-pilot.mjs` — keep static checks but add executable fixture assertions for each manifest type.
* `products/digital-marketing/deploy/local.compose.yaml` — verify labels and health paths remain Kernel-compatible.
* `products/digital-marketing/deploy/local.env.example` — enforce safe secret defaults and postgres persistence.
* `products/digital-marketing/dm-kernel-bridge/**` — add bridge behavior tests beyond registry presence.
* `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx` — add Digital Marketing “pilot runbook mode” with clear commands and evidence panels.
* Tests: `pnpm check:digital-marketing-lifecycle-pilot --smoke`, Playwright route test for Studio lifecycle pilot.

---

## Journey 5 — Artifact intelligence

**Current flow:** YAPPC artifact compiler is a real package with inventory, graph, model, provenance, residual, extractors, synthesis, merge, source-providers, compile-back, and builder surfaces.  The target commit specifically improves artifact compiler compatibility and records green package tests.  Shared artifact intelligence contracts exist for semantic refs, graph summaries, product shape evidence, dependency evidence, residual island reports, risk hotspots, generated change sets, bundles, and envelopes. 

**Expected flow:** YAPPC imports source → compiler produces semantic evidence → Data Cloud stores graph/provenance → Kernel consumes references → Studio displays residual islands/risks/recommendations.

**Gaps:**

* Evidence contracts are strong, but actual Data Cloud persistence and Kernel gate consumption need E2E proof.
* Studio does not yet expose first-class residual/risk/artifact-intelligence panels.
* Legacy compatibility wrappers added in the target commit should be removed after tests migrate to canonical operation contracts.

**File-level plan:**

* `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/**` — replace legacy compatibility wrappers with canonical contract-based test fixtures.
* `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**` — keep as only shared contract; do not import YAPPC internals into Kernel.
* `products/data-cloud/libs/kernel-bridge-providers/src/index.ts` — add artifact intelligence evidence write/list providers.
* `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx` — add graph summary, residual islands, risk hotspots, evidence refs.
* Tests: artifact compiler package tests, evidence schema contract tests, Data Cloud evidence persistence tests, Studio artifact intelligence UI tests.

---

## Journey 6 — Data Cloud foundation

**Current flow:** Kernel supports bootstrap mode by default and validates platform mode provider completeness.   Data Cloud bridge registers platform providers on the product side.  Data Cloud provider client sends tenant/workspace/project/provider-mode/correlation/auth headers and exposes instrumentation hooks. 

**Expected flow:** Kernel can build Data Cloud without Data Cloud; after Data Cloud runs, Kernel platform mode uses Data Cloud-backed providers for runtime truth/events/provenance/memory.

**Gaps:**

* Registry still marks Data Cloud conformance false and lifecycle execution disabled.
* Some provider validation scripts verify file existence/substrings rather than durable behavior.
* Redaction helper exists in provider client, but the audited section does not show it wired into instrumentation/logging.
* Platform-mode readiness must prove storage durability, tenant isolation, retention/redaction, and failure behavior.

**File-level plan:**

* `products/data-cloud/libs/kernel-bridge-providers/src/index.ts` — wire redaction into instrumentation payloads and add typed schemas for every provider response.
* `products/data-cloud/planes/action/gateway/src/app.ts` — ensure provider storage is real service-port backed, not in-memory.
* `products/data-cloud/extensions/kernel-bridge/src/main/java/**` — add integration tests for provider registration and health degradation.
* `scripts/check-data-cloud-platform-providers.mjs` — add behavioral tests or delegate to an integration test target.
* Tests: Data Cloud provider contract tests, Testcontainers/integration tests, tenant isolation tests, retention/redaction tests.

---

## Journey 7 — Future product shape readiness

**Current flow:** Product registry correctly prevents false readiness. PHR and Finance have lifecycle entries but are not allowed to execute; FlashIt is blocked until mobile adapters and privacy/security gates are real; Data Cloud and YAPPC are platform-provider products, not ordinary lifecycle products.   

**Expected flow:** Future products remain disabled until shape-readiness criteria are executable, evidenced, and tested.

**Gaps:**

* Shape readiness must become a product-facing matrix with explainable gaps.
* Product-specific gates need standardized evidence formats.
* Mobile/regulated/compliance-heavy products require adapter and policy packs before lifecycle enablement.

**File-level plan:**

* `config/canonical-product-registry.json` — keep lifecycleExecutionAllowed=false until proof exists.
* `config/generated/product-shape-capability-matrix.json` — regenerate and make it Studio-visible.
* `scripts/check-product-shape-capability-matrix.mjs` — enforce reason codes, required gates, missing evidence, and next work.
* `platform/typescript/ghatana-studio/src/routes/HealthPage.tsx` — show product shape readiness matrix.
* Tests: registry drift tests, shape matrix tests, product readiness UI tests.

---

# D. Capability ownership matrix

| Capability                         | Correct owner                    | Current location                                       | Problem                                 | Required fix                               | Tests                        |
| ---------------------------------- | -------------------------------- | ------------------------------------------------------ | --------------------------------------- | ------------------------------------------ | ---------------------------- |
| Unified Studio shell               | Ghatana Studio / shared platform | `platform/typescript/ghatana-studio`                   | Shell exists, but many routes disabled  | Wire runtime and pilot defaults            | Studio RTL + Playwright      |
| ProductUnit contracts              | Kernel contracts                 | `platform/typescript/kernel-product-contracts`         | Strong baseline                         | Keep as canonical; block duplicates        | public export + schema tests |
| ProductUnitIntent apply            | Kernel lifecycle                 | `KernelLifecycleService.ts`                            | Exists but not exposed in Studio client | Add API/client/UI flow                     | API + Studio tests           |
| YAPPC ProductUnitIntent generation | YAPPC                            | `products/yappc/**`                                    | Not fully proven in Studio              | Add explicit handoff endpoint and UI       | YAPPC API tests              |
| Lifecycle execution                | Product Development Kernel       | `platform/typescript/kernel-lifecycle`                 | Executor-dependent; UI not fully E2E    | Harden executor wiring and errors          | lifecycle service/API tests  |
| Toolchain adapters                 | Kernel toolchains                | `platform/typescript/kernel-toolchains`                | Need capability negotiation proof       | Add adapter contract tests                 | adapter tests                |
| Digital Marketing lifecycle pilot  | Product + Kernel config          | `products/digital-marketing/kernel-product.yaml`       | Strong pilot, needs E2E proof           | Add Studio pilot run and smoke gates       | smoke + E2E                  |
| Artifact intelligence compiler     | YAPPC                            | `products/yappc/frontend/libs/yappc-artifact-compiler` | Green tests but legacy wrappers added   | Migrate tests, delete wrappers             | compiler tests               |
| Artifact intelligence evidence     | Shared Kernel contract           | `kernel-product-contracts/src/artifact-intelligence`   | Strong schemas; needs persistence/use   | Data Cloud + Kernel gate wiring            | schema + integration         |
| Runtime truth/provenance/memory    | Data Cloud                       | Data Cloud bridge/provider libs                        | Bridge exists, platform mode not proven | Durable providers and tests                | provider integration         |
| Agentic lifecycle actions          | AEP/Data Cloud + Kernel          | Kernel agent service + Data Cloud action plane         | Not E2E                                 | Implement governed action path             | agent E2E                    |
| Approval flow                      | Kernel + Studio                  | Kernel service/API + LifecyclePage                     | Exists, needs auth/provider proof       | Enforce auth and provider-backed approvals | approval tests               |
| Observability                      | Cross-cutting                    | scripts + service loggers + providers                  | Logs exist; metrics/traces incomplete   | Add metrics/traces per critical flow       | o11y conformance             |
| i18n/a11y                          | Studio/shared UI                 | Studio routes + design system                          | Partial; raw codes remain               | Shared status labels and a11y tests        | i18n/a11y tests              |
| Platform coherence                 | Cross-cutting governance         | scripts/docs/config                                    | Good start; needs behavior checks       | Formalize domain registry and enforce      | architecture checks          |

---

# E. File-by-file implementation plan

## Workstream 1 — Ghatana Studio UI/UX and API contracts

| File                                                                         | Required change                                                                                                                       |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `platform/typescript/ghatana-studio/src/App.tsx`                             | Keep unified shell, but make disabled/hidden states explainable with current-state classification and direct next actions.            |
| `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`      | Replace route exposure decisions with capability metadata that can be rendered in Health and Settings; keep current route ownership.  |
| `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`        | Align error model with API handlers; add ProductUnitIntent preview/apply methods; support abort signals and typed retries where safe. |
| `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx` | Add unauthorized/blocked/degraded diagnostics; stop treating all non-manifest failures as generic unavailable.                        |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`            | Replace raw reason/status/environment rendering with i18n/status vocabulary; add pilot runbook mode; surface manifest trust state.    |
| `platform/typescript/ghatana-studio/src/routes/IdeasPage.tsx`                | Implement idea capture → ProductUnitIntent draft action.                                                                              |
| `platform/typescript/ghatana-studio/src/routes/BlueprintsPage.tsx`           | Add ProductUnitIntent export and preview.                                                                                             |
| `platform/typescript/ghatana-studio/src/routes/CanvasPage.tsx`               | Add canvas evidence export into YAPPC/Kernel contract format.                                                                         |
| `platform/typescript/ghatana-studio/src/routes/ArtifactsPage.tsx`            | Add artifact graph, residual islands, risk hotspots, provenance refs.                                                                 |
| `platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx`               | Add governed agent action proposals, risk, approval, verification, rollback readiness.                                                |
| `platform/typescript/ghatana-studio/src/routes/HealthPage.tsx`               | Add ProductUnit lifecycle readiness and product shape matrix.                                                                         |

## Workstream 2 — Product Development Kernel backend/lifecycle/providers/plugins

| File                                                                               | Required change                                                                                                                |
| ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`       | Replace `safePlan`/`safeResult` pass-through with UI-safe projections and redaction; add explicit provider result diagnostics. |
| `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts`       | Add ProductUnitIntent routes; align error envelope with Studio client; require auth outside explicit local-dev mode.           |
| `platform/typescript/kernel-lifecycle/src/planning/ProductLifecyclePlanner.ts`     | Ensure readiness blockers include required gates/evidence and are consumed by Studio without metadata guessing.                |
| `platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleExecutor.ts`   | Prove execution result determinism, no fake success, manifest linkage.                                                         |
| `platform/typescript/kernel-lifecycle/src/execution/ProductLifecycleStepRunner.ts` | Add adapter capability negotiation, timeout, retry, and structured output contracts.                                           |
| `platform/typescript/kernel-lifecycle/src/manifest/LifecycleManifestWriter.ts`     | Validate all generated manifests against schema before writing pointers.                                                       |
| `platform/typescript/kernel-product-contracts/src/provider/**`                     | Keep provider-mode requirements canonical; add typed provider health/failure reason contracts.                                 |
| `platform/typescript/kernel-product-contracts/src/events/**`                       | Add event version compatibility tests.                                                                                         |
| `platform/typescript/kernel-product-contracts/src/plugin/**`                       | Ensure approval/security/privacy/o11y plugins have evidence requirements.                                                      |

## Workstream 3 — Data Cloud foundation providers/runtime truth/memory

| File                                                                                                                    | Required change                                                                                                                             |
| ----------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelExtension.java` | Add integration tests proving registered providers are retrievable, tenant-scoped, and health-reported.                                     |
| `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`                                                         | Wire redaction into instrumentation/logging; add typed schemas for all write/list responses; add retries/circuit-breaker policy where safe. |
| `products/data-cloud/planes/action/gateway/src/app.ts`                                                                  | Guarantee provider storage is durable service-port backed, not in-memory.                                                                   |
| `scripts/check-data-cloud-platform-providers.mjs`                                                                       | Replace string-presence checks with behavioral contract tests or delegate to test targets.                                                  |
| `products/data-cloud/planes/action/**`                                                                                  | Wire AEP action runtime to Kernel lifecycle action contract.                                                                                |

## Workstream 4 — YAPPC creator/artifact intelligence/visibility

| File                                                                                           | Required change                                                                   |
| ---------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------- |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/index.ts`                            | Keep product-layer exports; do not leak implementation into Kernel.               |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/patch-coordinator.ts`   | Remove legacy compatibility wrapper after tests migrate to canonical operations.  |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/react-patch-emitter.ts` | Replace legacy overloads with canonical emitter contract.                         |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/graph/**`                            | Keep schema validation strict; add edge-case contract tests for graph references. |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/residual/**`                         | Make residual classification evidence-backed and privacy-aware.                   |
| `products/yappc/frontend/apps/api/src/**/product-unit-intents*`                                | Make YAPPC the canonical ProductUnitIntent producer.                              |
| `products/yappc/kernel-bridge/**`                                                              | Ensure Kernel consumes only contracts/evidence refs, never YAPPC internals.       |

## Workstream 5 — Digital Marketing pilot

| File                                                              | Required change                                                                                        |
| ----------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| `products/digital-marketing/kernel-product.yaml`                  | Keep as pilot source; validate all phase manifests, approvals, provider modes, deploy/verify evidence. |
| `products/digital-marketing/deploy/local.compose.yaml`            | Enforce Kernel labels and health check paths.                                                          |
| `products/digital-marketing/deploy/local.env.example`             | Enforce postgres persistence and safe secret placeholders.                                             |
| `products/digital-marketing/dm-kernel-bridge/**`                  | Add behavioral bridge tests, not only registry references.                                             |
| `scripts/check-digital-marketing-lifecycle-pilot.mjs`             | Keep smoke mode; add required manifest schema checks for every lifecycle phase.                        |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx` | Add Digital Marketing pilot quick-start and evidence explainability.                                   |

## Workstream 6 — Shared libraries/design system/builder/canvas/code editor

| File/area                              | Required change                                                                                                                    |
| -------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `platform/typescript/design-system/**` | Ensure shared status, gate, artifact, approval, blocked, degraded, and evidence components exist before product-local duplication. |
| `platform/typescript/ui-builder/**`    | Keep builder primitives product-neutral.                                                                                           |
| `platform/typescript/canvas/**`        | Provide product-neutral graph/canvas primitives only.                                                                              |
| `platform/typescript/code-editor/**`   | Keep code editing/AST/LSP primitives independent from YAPPC workflows.                                                             |
| `platform/typescript/i18n/**`          | Add required status/reason/gate terminology keys.                                                                                  |
| `platform/typescript/accessibility/**` | Add reusable accessibility audit helpers for Studio/product screens.                                                               |

## Workstream 7 — Product shape matrix/future product readiness

| File                                                    | Required change                                                                                                    |
| ------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `config/canonical-product-registry.json`                | Keep only Digital Marketing execution-enabled until proofs exist.                                                  |
| `config/generated/product-shape-capability-matrix.json` | Regenerate from registry and domain rules.                                                                         |
| `scripts/check-product-shape-capability-matrix.mjs`     | Enforce reason codes, required gates, missing manifests, next work, and executionAllowed=false for partial shapes. |
| `products/phr/kernel-product.yaml`                      | Add healthcare-specific gate evidence before enabling lifecycle.                                                   |
| `products/finance/kernel-product.yaml`                  | Add multi-module build and regulatory evidence before enabling lifecycle.                                          |
| `products/flashit/kernel-product.yaml`                  | Add mobile adapter and privacy/preview-security evidence before enabling lifecycle.                                |

## Workstream 8 — CI/CD/checks/docs cleanup

| File                      | Required change                                                                                                                                       |
| ------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `package.json`            | Keep broad command suite; add missing `check:agentic-development-e2e`, `check:artifact-intelligence-e2e`, and `check:studio-product-unit-intent-e2e`. |
| `scripts/check-*.mjs`     | Replace brittle substring checks with contract/integration tests where flow correctness matters.                                                      |
| `docs/architecture/**`    | Mark exactly one authoritative document per rule; other docs must reference it.                                                                       |
| `platform/kernel-todo.md` | Keep only active implementation tracker items; move test result notes to changelog or audit evidence.                                                 |
| `.github/workflows/**`    | Ensure root and targeted validation commands run in CI without unnecessary duplication.                                                               |

---

# F. Release plan

## Release 0 — Unified shell, terminology, navigation, core checks

**Goal:** Make Ghatana Studio coherent and honest.

**Scope:** Studio shell, navigation, route guards, i18n/status vocabulary, Kernel API error alignment, domain/current-state classification.

**Exit criteria:**

* Studio shows all routes with clear current-state labels.
* Kernel API and Studio client share one error envelope.
* Product shape readiness visible.
* No target-state claims shown as current truth.

**Validation:**

```bash
pnpm check:architecture-boundaries
pnpm check:studio-kernel-api
pnpm check:current-state-claims
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio build
```

## Release 1 — Digital Marketing lifecycle pilot E2E

**Goal:** Prove validate/test/build/package/deploy/verify for one product.

**Scope:** Digital Marketing lifecycle from CLI and Studio bootstrap mode.

**Exit criteria:**

* All manifests generated.
* Gate evidence is non-synthetic.
* Approval required for deploy/promote/rollback.
* Studio displays runs/manifests/approvals/failures.

**Validation:**

```bash
pnpm check:digital-marketing-lifecycle-pilot --smoke
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
```

## Release 2 — Agentic development support

**Goal:** Allow governed agent proposals, not direct tool execution.

**Scope:** AEP/Data Cloud action plane → Kernel lifecycle action → approval/risk/evidence → Studio Agents.

**Exit criteria:**

* Agent cannot run raw Gradle/pnpm/Docker outside Kernel.
* Risk and approval requirements are visible.
* Data Cloud stores evidence/provenance/memory.
* Studio displays proposal/result/rollback readiness.

**Validation:**

```bash
pnpm check:agentic-lifecycle-action-contracts
pnpm check:data-cloud-platform-providers
pnpm check:kernel-provider-mode
```

## Release 3 — Data Cloud platform-mode providers

**Goal:** Move from bootstrap-only evidence to Data Cloud-backed runtime truth.

**Scope:** Event/artifact/health/approval/provenance/memory/runtimeTruth providers.

**Exit criteria:**

* Platform mode fails closed when providers are missing.
* Data Cloud providers are durable and tenant-scoped.
* Redaction/retention tests pass.
* Kernel can switch bootstrap/platform intentionally.

**Validation:**

```bash
pnpm check:data-cloud-platform-providers
pnpm check:kernel-provider-mode
pnpm check:observability-conformance
pnpm check:data-access-contract
```

## Release 4 — Artifact intelligence integration

**Goal:** Make YAPPC artifact intelligence useful to Kernel and Studio.

**Scope:** Compiler evidence → Data Cloud graph/provenance → Kernel gates → Studio residual/risk UI.

**Exit criteria:**

* Evidence bundles stored and queryable.
* Kernel consumes evidence refs only.
* Studio shows residual islands/risk hotspots/recommendations.
* Legacy compatibility wrappers removed.

**Validation:**

```bash
pnpm --dir products/yappc/frontend/libs/yappc-artifact-compiler test
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:yappc-product-unit-intent-handoff
```

## Release 5 — Product shape expansion readiness

**Goal:** Prepare PHR, Finance, FlashIt, Data Cloud, YAPPC, Aura, DCMAAR, TutorPutor, Audio-Video, and external ProductUnits without false enablement.

**Scope:** Product-shape matrix, readiness gates, required adapters, product-specific evidence.

**Exit criteria:**

* Only execution-ready products have lifecycleExecutionAllowed=true.
* Planned/partial products show precise blockers and next work.
* Studio Health shows readiness matrix.

**Validation:**

```bash
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-registry
pnpm check:product-manifest-contracts
```

---

# G. Validation command suite

Run these after implementation slices, not all blindly after every small edit.

```bash
pnpm build
pnpm test
pnpm typecheck
pnpm build:platform
pnpm build:kernel-lifecycle-platform

pnpm check:kernel-platform-lifecycle
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:digital-marketing-lifecycle-pilot --smoke
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:shared-ui-state-coverage
pnpm check:production-readiness
pnpm check:production-stubs
pnpm check:secret-default-credentials
pnpm check:observability-conformance
pnpm check:data-access-contract
pnpm check:kernel-provider-mode
pnpm check:data-cloud-platform-providers
pnpm check:studio-kernel-api
pnpm check:kernel-api-contracts
pnpm check:agentic-lifecycle-action-contracts
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:architecture-boundaries

pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio build
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm --dir products/yappc/frontend/libs/yappc-artifact-compiler test

pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing

./gradlew build
./gradlew check
./gradlew :products:digital-marketing:dm-kernel-bridge:test
./gradlew :products:data-cloud:extensions:kernel-bridge:test
```

## Final assessment

The repo at `6e82fd932eae27940057b76d5674286851ff66bd` is **architecturally aligned and improving**, especially around Kernel contracts, Studio shell, lifecycle APIs, Digital Marketing pilot readiness, Data Cloud bridges, and YAPPC artifact intelligence. It is not yet world-class E2E because the customer journey is still fragmented across partially wired capabilities.

The most important next move is not adding another broad domain. It is to make **one complete, honest, demonstrable vertical slice** work:

```text
Studio → Digital Marketing ProductUnit → Kernel lifecycle plan → validate/test/build/package/deploy/verify → manifests/gates/approvals → Studio health/evidence
```

Once that is fully executable and regression-tested, extend the same pattern to ProductUnitIntent handoff, agentic development, Data Cloud platform mode, and future product shape readiness.
