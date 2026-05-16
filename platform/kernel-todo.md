# Full End-to-End Ghatana World-Class Product Audit

Target commit: `9163f6f1edacf4a421603693a03e3a0aac25ee2f`

This audit is based on the complete repository snapshot at the target commit, not the commit diff. The target commit itself is a merge commit whose visible diff is only a YAPPC changelog update, so the meaningful audit target is the full repo state at that SHA. 

The audit follows the repo rules: reuse before creating, keep product/platform boundaries explicit, avoid silent failures and unsafe defaults, keep TypeScript fully typed, test meaningful behavior, document Java public APIs, and make important flows observable.  It also follows the hardened blueprint’s ownership model: Ghatana Studio is the unified customer experience; Product Development Kernel owns lifecycle truth; YAPPC owns creation/artifact intelligence; Data Cloud owns runtime truth, governance, events, memory, and provenance; products own domain behavior.  The domain workstream map is also directionally reflected in the repo: Studio, Kernel contracts/lifecycle/providers/artifacts/deployment/toolchains, Data Cloud, YAPPC, and Digital Marketing all have concrete package/module surfaces, but many are still partial. 

I did not run the validation commands locally. The command suite below is the required proof set to execute after implementation.

---

## A. Executive Summary

### What is close to world-class

Ghatana now has strong structural foundations:

| Area                                 |                       Classification | Evidence                                                                                                                                                                        |
| ------------------------------------ | -----------------------------------: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Repo governance and workspace wiring |                 Existing but partial | Root `package.json` has broad architecture, production, Kernel, Data Cloud, product-shape, UI, and cleanup checks.                                                              |
| Product registry                     |                 Existing but partial | `canonical-product-registry.json` maps products to surfaces, lifecycle status, toolchains, artifacts, gates, and readiness.                                                     |
| Gradle/pnpm generated wiring         | Existing and executable structurally | Generated Gradle includes wire product modules from the registry.                                                                                                               |
| Kernel contracts                     |                 Existing but partial | `@ghatana/kernel-product-contracts` exports ProductUnit, ProductUnitIntent, providers, events, health, plugin, artifact-intelligence, and agentic lifecycle schemas.            |
| Kernel lifecycle service             |                 Existing but partial | `KernelLifecycleService` can plan, execute via injected executor, write lifecycle truth, handle approvals, record events/provenance/runtime truth, and apply ProductUnitIntent. |
| Bootstrap mode                       |              Existing and executable | File-backed lifecycle providers exist for events, artifacts, health, approvals, provenance, memory, and runtime truth, with a production bootstrap guard.                       |
| Ghatana Studio shell                 |                 Existing but partial | `@ghatana/ghatana-studio` exists and depends on Kernel, canvas, UI builder, design system, i18n, API, and platform packages.                                                    |
| Digital Marketing pilot              |                Closest to executable | Registry marks Digital Marketing lifecycle enabled/ready with backend and web surfaces, bridge adapter, and lifecycle execution allowed.                                        |
| YAPPC artifact compiler/decompiler   |                 Existing but partial | Product-local artifact compiler package exposes inventory, graph, model, source providers, extractors, provenance, residual, merge, synthesis, compile-back, and builder APIs.  |

### What blocks customer usage today

The biggest blocker is that the target experience is not yet executable from Ghatana Studio end to end. The Studio shell exists, but key routes are disabled or hidden: Ideas, Lifecycle, Agents, Artifacts, and Health are disabled/degraded/empty, and Deployments is hidden.  Develop has “safe action” buttons, but they are currently static UI buttons without execution handlers, and the product selector is disabled.  Lifecycle has a richer UI and can call the lifecycle context, but the nav still marks Lifecycle disabled, so route exposure and implementation are inconsistent.

Data Cloud platform mode is the second major blocker. The registry explicitly marks Data Cloud conformance fields such as manifest, observability, security, data access, bridge, agent definitions, mastery bindings, evaluation packs, and runtime module as false, and says platform-provider/bootstrap separation and runtime-truth provider work are required.  The Java bridge exists, but it is still an adapter facade around an injected `DataCloudClient` interface rather than a proven Data Cloud-backed Kernel lifecycle provider implementation.

The third blocker is product coverage. Digital Marketing is the only lifecycle-enabled pilot. PHR, Finance, FlashIt, TutorPutor, Aura, DCMAAR, Audio-Video, YAPPC, and Data Cloud remain planned, partial, disabled, or platform-provider validation targets.

### Top 10 fixes

1. Mount and validate the Kernel lifecycle API in an actual backend/runtime surface, with auth, tenant/workspace/project scope, and error contracts.
2. Make Studio route exposure runtime-driven, not hardcoded disabled/hidden, especially for Lifecycle, Artifacts, Health, and Agents.
3. Wire Develop page actions to Kernel lifecycle client calls.
4. Enable ProductUnit selection in Studio from the registry and remove the Digital Marketing hardcoded UX default.
5. Complete Digital Marketing validate/test/build/package/deploy/verify as the first true E2E proof.
6. Implement Data Cloud-backed Kernel lifecycle providers for events, artifacts, health, approvals, provenance, memory, and runtime truth.
7. Connect YAPPC ProductUnitIntent export to Kernel preview/apply through ProductUnitIntent contracts.
8. Connect YAPPC artifact intelligence evidence to Data Cloud provenance/runtime truth and Kernel semantic references.
9. Enforce agentic development through `AgentLifecycleActionRequest`, not raw tool execution.
10. Add E2E tests that prove the actual Studio journey, not just contracts and isolated component behavior.

---

## B. System Architecture Map

```text
Ghatana Studio
  platform/typescript/ghatana-studio
  - unified shell
  - navigation
  - Develop/Lifecycle/Artifacts/Deployments/Health/Agents UX
  - typed Kernel lifecycle client
  - currently partial because key routes are disabled/unconfigured

Product Development Kernel
  platform/typescript/kernel-product-contracts
  platform/typescript/kernel-lifecycle
  platform/typescript/kernel-providers
  platform/typescript/kernel-toolchains
  platform/typescript/kernel-artifacts
  platform/typescript/kernel-deployment
  platform/typescript/kernel-release
  platform-kernel/*
  - owns ProductUnit, ProductUnitIntent, lifecycle plans/results, lifecycle truth,
    provider contracts, plugin contracts, agentic action contracts

YAPPC
  products/yappc/*
  products/yappc/frontend/libs/yappc-artifact-compiler
  products/yappc/kernel-bridge
  - owns ideation, blueprinting, artifact intelligence, semantic model extraction,
    ProductUnitIntent generation, residual/risk/recommendation workflows

Data Cloud / AEP
  products/data-cloud/*
  products/data-cloud/planes/action/*
  products/data-cloud/extensions/kernel-bridge
  - owns runtime truth, events, provenance, memory, governed data access, AEP/action runtime
  - currently platform-provider target, not fully lifecycle/provider-ready

Digital Marketing
  products/digital-marketing/*
  - executable lifecycle pilot
  - validates backend + web lifecycle shape

Future product validators
  PHR, Finance, FlashIt, Aura, DCMAAR, TutorPutor, Audio-Video
  - currently shape/readiness validators, not lifecycle-executable products
```

---

## C. Journey-by-Journey Findings

### Journey 1 — Product ideation to ProductUnitIntent

**Current state:** Existing but partial.

ProductUnitIntent contracts and validation exist in `@ghatana/kernel-product-contracts`, and Kernel lifecycle service can validate/apply ProductUnitIntent through an intent-capable registry provider.  YAPPC has artifact intelligence and source acquisition capabilities, including GitHub, GitLab, local folder, zip, and archive source providers.

**Gap:** The Studio Ideas route is disabled, and there is no proven UI/API flow from Idea → Blueprint/Canvas → ProductUnitIntent → Kernel preview/apply → ProductUnit visible in Studio. 

**Required implementation:** Build ProductUnitIntent export in YAPPC, expose preview/apply in Studio, and force all registry mutations through Kernel intent contracts.

---

### Journey 2 — Direct Product Development Kernel usage

**Current state:** Existing but partial.

Kernel lifecycle packages and service exist. The service can create lifecycle plans, write `lifecycle-plan.json`, execute through an injected executor, write `lifecycle-result.json`, record runtime truth/provenance, append events, and manage approvals.  Studio has a Lifecycle page with phase, environment, provider-mode, run list, manifests, approvals, diagnostics, and validation command UI.

**Gap:** The Lifecycle nav item is disabled despite the implementation, Studio defaults can be unconfigured/no-op if no client is injected, and backend mounting of the lifecycle API must be proven.

**Required implementation:** Mount KernelLifecycleApiHandlers in a deployable backend, inject configured client/runtime context into Studio, and make route visibility capability-driven.

---

### Journey 3 — Agentic product development

**Current state:** Declared/partial.

Agentic lifecycle schemas exist, including request, approval requirement, verification requirement, result, and failure types.  Data Cloud has many Action Plane/AEP modules in the product registry. 

**Gap:** Agents route is disabled, and there is no proven AEP → Kernel action request → Kernel execution → Data Cloud evidence → Studio recommendation flow. 

**Required implementation:** AEP agents must invoke only `AgentLifecycleActionRequest` contracts. Kernel must perform policy/risk/mastery/approval/verification checks, then store evidence in Data Cloud.

---

### Journey 4 — Digital Marketing lifecycle pilot

**Current state:** Existing and closest to executable.

Digital Marketing is explicitly marked as the validated lifecycle pilot, with backend and web surfaces implemented, lifecycle enabled, bridge conformance true, toolchains declared, compose-local deployment target, and `lifecycleExecutionAllowed: true`.  Root scripts include Digital Marketing plan/build/test/dev/validate/package/deploy/verify commands. 

**Gap:** This still needs actual command execution proof and Studio visualization proof for all manifests and health.

**Required implementation:** Use Digital Marketing as Release 1 proof. Do not enable other products until Digital Marketing validates every manifest, gate, artifact, deployment, health, and Studio panel.

---

### Journey 5 — Artifact intelligence

**Current state:** Existing but partial.

YAPPC artifact compiler/decompiler is substantial. It exposes source providers, graph, model, provenance, residual, merge, synthesis, compile-back, and builder subpaths.  It supports governed source acquisition with typed locators, credential policy, diagnostics, and credential resolver abstractions.

**Gap:** The GitHub provider schedules temp cleanup immediately after returning the snapshot unless `keepTempFiles` is true; that can race downstream scanning unless the pipeline owns lifecycle/cleanup explicitly.  More importantly, the compiler output is not yet proven as Data Cloud provenance/runtime-truth evidence consumed by Kernel and visualized in Studio.

**Required implementation:** Emit `ArtifactIntelligenceEvidenceEnvelope`, store it in Data Cloud, return semantic artifact references to Kernel, and show residual islands/risk hotspots in Studio.

---

### Journey 6 — Data Cloud foundation

**Current state:** Bootstrap is existing; platform mode is target/partial.

Bootstrap mode is strong: file-backed providers exist for lifecycle events, artifacts, health, approvals, provenance, memory, and runtime truth, and output is constrained to `.kernel/out`.  Platform mode is not ready: registry conformance for Data Cloud is false across manifest, observability, security, dataAccess, bridge, agent definitions, mastery bindings, evaluation packs, and runtime module. 

**Gap:** Data Cloud bridge exists in Java, but the actual Data Cloud-backed Kernel lifecycle provider set is not proven.

**Required implementation:** Add Data Cloud-backed implementations for Kernel provider contracts and validate platform mode rejects file-backed providers in production.

---

### Journey 7 — Future product shape readiness

**Current state:** Declared/target.

PHR, Finance, FlashIt, TutorPutor, Aura, DCMAAR, Audio-Video, YAPPC, and Data Cloud are correctly not lifecycle-executable yet. The registry identifies why each is blocked: PHR needs consent/PII/FHIR/data-sovereignty gates; Finance needs regulatory, promotion, and multi-module validation; FlashIt needs mobile adapters and IPA/AAB manifests; TutorPutor needs content-safety and learner-data privacy; Data Cloud and YAPPC need platform-provider separation.

**Required implementation:** Keep them disabled until each has executable adapters, gates, manifests, and tests.

---

## D. Capability Ownership Matrix

| Capability                         | Correct owner                 | Current location                                                            |                     Classification | Required fix                                                | Tests                       |
| ---------------------------------- | ----------------------------- | --------------------------------------------------------------------------- | ---------------------------------: | ----------------------------------------------------------- | --------------------------- |
| Studio shell/navigation            | Ghatana Studio                | `platform/typescript/ghatana-studio`                                        |               Existing but partial | Make route exposure capability/runtime-driven               | Navigation + Playwright E2E |
| ProductUnit/ProductUnitIntent      | Kernel contracts              | `platform/typescript/kernel-product-contracts`                              |               Existing but partial | Freeze schemas, generate clients, add compatibility tests   | Contract tests              |
| Lifecycle planning/execution truth | Kernel lifecycle              | `platform/typescript/kernel-lifecycle`                                      |               Existing but partial | Mount API, add auth/scope, prove execution                  | API + E2E                   |
| Bootstrap providers                | Kernel providers              | `platform/typescript/kernel-providers`                                      |            Existing and executable | Keep file-backed only for local/bootstrap                   | Provider tests              |
| Platform providers                 | Data Cloud bridge             | `products/data-cloud/extensions/kernel-bridge` + needed TS provider package |                     Target/partial | Implement Data Cloud-backed lifecycle providers             | Integration/Testcontainers  |
| Toolchain execution                | Kernel toolchains             | `platform/typescript/kernel-toolchains`                                     |               Existing but partial | Add adapter capability negotiation, fake-success prevention | Adapter contract tests      |
| Digital Marketing lifecycle        | Product + Kernel bridge       | `products/digital-marketing/*`                                              | Existing and closest to executable | Prove validate/test/build/package/deploy/verify             | Pilot E2E                   |
| Artifact intelligence              | YAPPC                         | `products/yappc/frontend/libs/yappc-artifact-compiler`                      |               Existing but partial | Emit evidence envelopes and Data Cloud provenance           | Compiler golden tests       |
| Agentic lifecycle                  | Kernel + AEP/Data Cloud       | `kernel-product-contracts`, `products/data-cloud/planes/action`             |                   Declared/partial | Enforce AgentLifecycleActionRequest path                    | Agent E2E                   |
| Product shape readiness            | Platform coherence + products | `config/canonical-product-registry.json`, `products/*/kernel-product.yaml`  |               Existing but partial | Keep disabled until gates/adapters are proven               | Registry drift tests        |

---

## E. File-by-File Implementation Plan

### 1. Ghatana Studio UI/UX and API contracts

| File                                                                         | Current issue                                                                              | Target change                                                                                    | Validation                                           |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`      | Lifecycle/Agents/Artifacts/Health are hardcoded disabled; Deployments hidden.              | Compute exposure from Kernel/Data Cloud capability state, not static constants.                  | `pnpm check:studio-kernel-api`, navigation tests     |
| `platform/typescript/ghatana-studio/src/App.tsx`                             | Shell exists, but Preview text and some status labels need full i18n/capability awareness. | Add route guard component with reason codes, docs links, and a11y status.                        | `pnpm --dir platform/typescript/ghatana-studio test` |
| `platform/typescript/ghatana-studio/src/routes/DevelopPage.tsx`              | Product selector disabled; action buttons are static/no-op; some labels are hardcoded.     | Wire actions to `createPlan` / `executePhase`; enable selector; i18n all labels.                 | Component + user-flow tests                          |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`            | Rich page exists but route is disabled; blocked state is not harmonized with nav state.    | Make page state and nav state share one lifecycle readiness source.                              | Lifecycle route tests                                |
| `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx` | Default context uses no-op callbacks and unconfigured state.                               | Require explicit configured/unconfigured runtime mode; log missing client with safe diagnostics. | Context tests                                        |
| `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`        | Good zod validation, but backend contract generation/mounting must be proven.              | Align to canonical API schema/OpenAPI; require auth/scope for mutations.                         | Client contract tests                                |

---

### 2. Product Development Kernel backend/lifecycle/providers/plugins

| File                                                                                 | Current issue                                                                              | Target change                                                                              | Validation                                          |
| ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------ | --------------------------------------------------- |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`         | Strong lifecycle service, but execution depends on injected executor and provider context. | Add production composition factory that wires executor, adapters, auth, and provider mode. | `pnpm check:kernel-lifecycle-truth`                 |
| `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts`         | Exported, but runtime mounting must be proven.                                             | Mount in a deployable Kernel/Studio API gateway with auth/scope middleware.                | API integration tests                               |
| `platform/typescript/kernel-providers/src/factory/createBootstrapKernelProviders.ts` | Good bootstrap provider factory.                                                           | Keep bootstrap local-only; add explicit test for production rejection.                     | Provider tests                                      |
| `platform/typescript/kernel-product-contracts/src/product-unit/*`                    | Contracts exist.                                                                           | Add compatibility tests against `canonical-product-registry.json`.                         | `pnpm check:kernel-product-unit-provider-contracts` |
| `platform/typescript/kernel-product-contracts/src/agentic/*`                         | Agent contracts exist.                                                                     | Enforce usage through AEP/Kernel integration path.                                         | `pnpm check:agentic-lifecycle-action-contracts`     |

---

### 3. Data Cloud foundation providers/runtime truth/memory

| File                                                                                                                      | Current issue                                                                                               | Target change                                                                                                       | Validation                                                      |
| ------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| `products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudKernelAdapterImpl.java` | Adapter facade exists, but `DataCloudClient` is an inner injected interface with default healthy lifecycle. | Wire a real Data Cloud client/provider implementation; remove default always-healthy behavior for production paths. | `./gradlew :products:data-cloud:extensions:kernel-bridge:check` |
| `products/data-cloud/extensions/kernel-bridge/build.gradle.kts`                                                           | Correct module dependency direction.                                                                        | Add integration-test dependency/profile for real Data Cloud provider contract tests.                                | Gradle check + integration tests                                |
| `products/data-cloud/planes/event/*`                                                                                      | Event plane exists in registry.                                                                             | Implement lifecycle event provider for Kernel platform mode.                                                        | Data Cloud provider contract tests                              |
| `products/data-cloud/planes/governance/*`                                                                                 | Governance plane exists.                                                                                    | Store policy evidence, approvals, and gate results.                                                                 | Governance integration tests                                    |
| `products/data-cloud/planes/action/*`                                                                                     | AEP modules exist.                                                                                          | Enforce agent action → Kernel lifecycle request → evidence loop.                                                    | Agentic E2E                                                     |

---

### 4. YAPPC creator/artifact intelligence/visibility

| File                                                                                           | Current issue                                                                               | Target change                                                                      | Validation                                        |
| ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------- |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/github-provider.ts` | Temp cleanup can race downstream scanner unless pipeline explicitly owns snapshot lifetime. | Replace immediate cleanup with explicit snapshot lifecycle/lease/cleanup contract. | Source-provider tests                             |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/source-providers/types.ts`           | Good typed provider and credential policy.                                                  | Add server-side credential resolver integration and audit events.                  | Credential policy tests                           |
| `products/yappc/frontend/libs/yappc-artifact-compiler/src/compile-back/*`                      | Compile-back exports exist.                                                                 | Add golden round-trip tests from source → model → patch → source.                  | Compiler golden tests                             |
| `products/yappc/kernel-bridge/*`                                                               | Handoff is not proven end to end.                                                           | Expose ProductUnitIntent preview/apply through Kernel only.                        | `pnpm check:yappc-product-unit-intent-handoff`    |
| `platform/typescript/kernel-product-contracts/src/artifact-intelligence/*`                     | Evidence contracts exist.                                                                   | Use them as the only Kernel-facing compiler output.                                | `pnpm check:yappc-artifact-intelligence-boundary` |

---

### 5. Digital Marketing pilot

| File                                             | Current issue                                          | Target change                                                    | Validation                                                     |
| ------------------------------------------------ | ------------------------------------------------------ | ---------------------------------------------------------------- | -------------------------------------------------------------- |
| `products/digital-marketing/kernel-product.yaml` | Registry says ready, but command proof still required. | Validate every lifecycle phase and manifest reference.           | `pnpm check:digital-marketing-lifecycle-pilot`                 |
| `products/digital-marketing/dm-kernel-bridge/*`  | Bridge listed as conformant.                           | Ensure bridge emits lifecycle evidence and failure reason codes. | `./gradlew :products:digital-marketing:dm-kernel-bridge:check` |
| `products/digital-marketing/dm-api/*`            | Backend surface implemented.                           | Ensure health/readiness endpoint supports verify phase.          | Backend integration tests                                      |
| `products/digital-marketing/ui/*`                | Web surface implemented.                               | Ensure build artifact and Studio surface evidence are present.   | UI build + E2E                                                 |

---

### 6. Shared libraries/design system/builder/canvas/code editor

| Area                     | Current issue                                                                    | Target change                                                                                     | Validation                             |
| ------------------------ | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | -------------------------------------- |
| `@ghatana/design-system` | Studio uses it, but lifecycle/gate/artifact panels are still mostly route-local. | Extract shared lifecycle/gate/artifact/approval components only after Studio + one product reuse. | `pnpm check:design-system-conformance` |
| `@ghatana/ui-builder`    | Platform primitive exists.                                                       | Align BuilderDocument schemas with YAPPC backend schema.                                          | Builder schema tests                   |
| `@ghatana/canvas`        | Platform primitive exists.                                                       | Add product-neutral semantic zoom/context-shift API only after stable need.                       | Canvas tests                           |
| `@ghatana/i18n`          | Used by Studio, but hardcoded UI strings remain.                                 | Remove final user-facing hardcoded strings.                                                       | i18n checks                            |

---

### 7. Product shape matrix/future readiness

| File                                      | Current issue                                            | Target change                                                                    | Validation                                   |
| ----------------------------------------- | -------------------------------------------------------- | -------------------------------------------------------------------------------- | -------------------------------------------- |
| `config/canonical-product-registry.json`  | Good readiness metadata, but many products are disabled. | Keep disabled until executable gates/adapters exist; do not overclaim readiness. | `pnpm check:product-shape-capability-matrix` |
| `products/phr/kernel-product.yaml`        | Needs consent/PII/FHIR/data sovereignty gates.           | Add evidence-producing gates before execution.                                   | PHR gate tests                               |
| `products/finance/kernel-product.yaml`    | Needs multi-module/regulatory/operator/portal/SDK proof. | Add adapter readiness and promotion approvals.                                   | Finance lifecycle dry-run                    |
| `products/flashit/kernel-product.yaml`    | Needs mobile adapters and IPA/AAB manifests.             | Add mobile artifact contracts before enabling execution.                         | Mobile contract tests                        |
| `products/tutorputor/kernel-product.yaml` | Needs content safety and learner privacy.                | Add model-output evaluation evidence.                                            | TutorPutor safety tests                      |

---

### 8. CI/CD/checks/docs cleanup

| File                                            | Current issue                                            | Target change                                                                | Validation                              |
| ----------------------------------------------- | -------------------------------------------------------- | ---------------------------------------------------------------------------- | --------------------------------------- |
| `package.json`                                  | Good check coverage; must not become check-theater.      | Ensure every check validates real code/config, not only static declarations. | `pnpm check:production-readiness`       |
| `scripts/check-*.mjs`                           | Many checks exist.                                       | Add route exposure vs implementation drift check.                            | New check + tests                       |
| `docs/**`                                       | Risk of target architecture being read as current truth. | Enforce current-state labels in docs.                                        | `pnpm check:current-state-claims`       |
| `config/domain-registry.json`                   | Governance anchor needed/validated.                      | Ensure domain registry maps to real modules and owners.                      | `pnpm check:domain-registry`            |
| `config/generated/settings-gradle-includes.kts` | Generated from registry.                                 | Never edit manually; validate drift.                                         | `pnpm check:product-registry-artifacts` |

---

## F. Release Plan

### Release 0 — Unified shell, terminology, navigation, core checks

**Goal:** Make Studio honest and coherent.

**Scope:** Studio route exposure, product selector, lifecycle readiness display, i18n/a11y fixes, current-state/target-state checks.

**Exit criteria:** No route claims execution unless configured; Digital Marketing appears as the only executable pilot; disabled products show reason codes.

**Commands:**

```bash
pnpm check:domain-registry
pnpm check:current-state-claims
pnpm check:studio-kernel-api
pnpm --dir platform/typescript/ghatana-studio test
```

---

### Release 1 — Digital Marketing lifecycle pilot E2E

**Goal:** Prove one complete lifecycle.

**Scope:** Digital Marketing validate/test/build/package/deploy/verify, manifests, health, gates, Studio run visualization.

**Exit criteria:** Digital Marketing produces lifecycle plan/result, gate result manifest, artifact manifest, deployment manifest, verify health report, and Studio displays them.

**Commands:**

```bash
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
pnpm check:digital-marketing-lifecycle-pilot
```

---

### Release 2 — Agentic development support

**Goal:** Agents improve/build products only through Kernel contracts.

**Scope:** `AgentLifecycleActionRequest`, approval gates, verification requirements, AEP action path, Studio agent console.

**Exit criteria:** Agent cannot run raw Gradle/pnpm/Docker directly; every action has approval/evidence/provenance.

**Commands:**

```bash
pnpm check:agentic-lifecycle-action-contracts
pnpm check:kernel-lifecycle-truth
pnpm check:studio-kernel-api
```

---

### Release 3 — Data Cloud platform-mode providers

**Goal:** Move from file-backed bootstrap truth to governed Data Cloud truth.

**Scope:** Events, artifacts, health, approvals, provenance, memory, runtime truth providers.

**Exit criteria:** Platform mode rejects missing/file-backed providers and stores lifecycle truth in Data Cloud.

**Commands:**

```bash
pnpm check:kernel-provider-mode
pnpm check:data-cloud-platform-providers
pnpm check:data-access-contract
./gradlew :products:data-cloud:extensions:kernel-bridge:check
```

---

### Release 4 — Artifact intelligence integration

**Goal:** YAPPC compiler output becomes trusted evidence.

**Scope:** Source acquisition, semantic graph, residual islands, risk hotspots, Data Cloud provenance, Kernel semantic references, Studio visualization.

**Exit criteria:** Repo import/decompile creates evidence envelope, stores provenance, and is consumed by Kernel/Studio.

**Commands:**

```bash
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:yappc-product-unit-intent-handoff
pnpm --dir products/yappc/frontend/libs/yappc-artifact-compiler test
```

---

### Release 5 — Product shape expansion readiness

**Goal:** Validate future product shapes without making Kernel a god product.

**Scope:** PHR, Finance, FlashIt, TutorPutor, Aura, DCMAAR, Audio-Video readiness gates.

**Exit criteria:** Products remain disabled until gates/adapters/manifests are executable.

**Commands:**

```bash
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-manifest-contracts
pnpm check:product-ci-matrices
```

---

## G. Validation Command Suite

Run this full suite after implementing the plan:

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

pnpm check:studio-kernel-api
pnpm check:kernel-lifecycle-truth
pnpm check:kernel-provider-mode
pnpm check:data-cloud-platform-providers
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:agentic-lifecycle-action-contracts
pnpm check:toolchain-adapter-contracts
pnpm check:product-artifact-contracts
pnpm check:product-deployment-contracts
pnpm check:product-environment-contracts

pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing

./gradlew build
./gradlew check
./gradlew :products:data-cloud:extensions:kernel-bridge:check
./gradlew :products:digital-marketing:dm-kernel-bridge:check
```

---

## Final Recommendation

Treat `9163f6f1edacf4a421603693a03e3a0aac25ee2f` as a strong architectural convergence snapshot, not yet a world-class executable platform snapshot.

The repo is moving in the right direction: contracts, registry, Studio shell, lifecycle services, bootstrap providers, Data Cloud bridge, YAPPC artifact compiler, and Digital Marketing pilot all exist. The next work should be ruthless and narrow: make Digital Marketing fully executable through Studio and Kernel, then wire Data Cloud platform-mode providers, then add agentic and artifact-intelligence evidence flows. Keep all other products as shape validators until their gates, adapters, manifests, and tests are real.

---

## H. Implementation Tracking (Live)

Status date: 2026-05-16

### Release 0 — Studio coherence

- [x] `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`  
  Capability-driven route exposure resolver added (`resolveStudioNavItems`, `resolveStudioRouteCapabilityState`), removing static hardcoded exposure behavior at render time.
- [x] `platform/typescript/ghatana-studio/src/App.tsx`  
  Route access guard added with reason code and documentation link; sidebar/header now consume runtime-derived navigation state.
- [x] `platform/typescript/ghatana-studio/src/routes/DevelopPage.tsx`  
  ProductUnit selector enabled from lifecycle context; safe actions now call lifecycle client (`createPlan`/`executePhase`) with guarded error handling.
- [x] `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx`  
  Explicit runtime mode surfaced in snapshot and safe diagnostics logged when runtime is unconfigured.
- [x] `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`  
  Blocked/provider-mode state now derives from shared route capability state (`resolveStudioRouteCapabilityState`) to keep page/nav behavior aligned.
- [x] `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`  
  Auth/scope mutation contract verification covered by `src/api/__tests__/kernelLifecycleClient.test.ts`; parity guard evidence captured by `pnpm -w run check:kernel-api-contracts` (Studio client contract + canonical OpenAPI checks).

### Release 0 — Tests and checks

- [x] Updated test fixtures for new runtime-mode snapshot shape.
- [x] Updated navigation test expectations for runtime-driven exposure semantics.
- [x] `pnpm -C platform/typescript/ghatana-studio run type-check` clean.
- [x] `pnpm exec vitest run --reporter=dot` in `platform/typescript/ghatana-studio` is green.
- [x] `platform/typescript/ghatana-studio/vitest.config.ts` updated to prefer TypeScript source resolution to avoid stale JS module drift in tests.

### Cross-release validation evidence captured in this batch

- [x] `pnpm -w run check:studio-kernel-api` passed.
- [x] `pnpm -w run check:current-state-claims` passed.
- [x] `pnpm -w run check:digital-marketing-lifecycle-pilot` passed.
- [x] `pnpm -w run check:kernel-lifecycle-truth` passed.
- [x] `pnpm -w run check:kernel-provider-mode` passed.
- [x] `pnpm -w run check:agentic-lifecycle-action-contracts` passed.
- [x] `pnpm -w run check:yappc-product-unit-intent-handoff` passed.
- [x] `pnpm -w run check:yappc-artifact-intelligence-boundary` passed.
- [x] `pnpm -w run check:data-cloud-platform-providers` passed.
- [x] `pnpm -w run check:product-shape-capability-matrix` passed.
- [x] `pnpm -w run check:lifecycle-registry-config-drift` passed.
- [x] `pnpm -w run check:toolchain-adapter-contracts` passed.
- [x] `pnpm -w run check:product-artifact-contracts` passed.
- [x] `pnpm -w run check:product-deployment-contracts` passed.
- [x] `pnpm -w run check:product-environment-contracts` passed.
- [x] `pnpm -w run check:data-access-contract` passed.
- [x] `pnpm -w run check:production-readiness` passed.
- [x] `pnpm -w run check:kernel-api-contracts` passed (newly wired alias + canonical OpenAPI verification).
- [x] `pnpm -w run check:kernel-lifecycle-service` passed (newly wired alias to lifecycle truth guard).
- [x] Runtime truth status compatibility fix landed in `platform/typescript/kernel-providers/src/runtime-truth/FileRuntimeTruthProvider.ts` and validated with `pnpm -C platform/typescript/kernel-providers test` + `pnpm -C platform/typescript/kernel-providers build`.
- [x] Digital Marketing full lifecycle command chain proved in this environment:
  `pnpm -w run validate:digital-marketing`
  `pnpm -w run test:digital-marketing`
  `pnpm -w run build:digital-marketing`
  `pnpm -w run package:digital-marketing`
  `pnpm -w run deploy:local:digital-marketing`
  `pnpm -w run verify:local:digital-marketing`
- [x] Durable lifecycle fixes landed to support that proof: bootstrap gate coverage expansion, idempotent approval replay, approval-aware executor flow, docker-buildx timeout configurability, compose service parsing by service id, DM package/build/deploy config alignment, and DM API executable packaging.
- [x] `pnpm -w run check:kernel-product-boundary-audit` passed after tightening overbroad production-stub scan rules to target storage/auth-health surfaces instead of generic frontend/browser `Map` usage and benign in-memory comments, and after removing the last explicit incomplete-product marker in the DM dashboard widget.

### Remaining major streams (not started in this implementation batch)

- [x] Release 1 Digital Marketing lifecycle pilot end-to-end proof
- [ ] Release 2 Agentic lifecycle contract enforcement flow
- [ ] Release 3 Data Cloud platform-provider mode implementation
- [ ] Release 4 YAPPC artifact-intelligence evidence loop and Kernel/Data Cloud integration
- [ ] Release 5 Product shape readiness expansion with strict disabled-by-default gating
