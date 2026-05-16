# ARCHIVED: Historical Audit Snapshot

This document is archived and no longer the active execution tracker.

Canonical tracker: [platform/kernel-todo.md](platform/kernel-todo.md)

Use this file only for historical context from the earlier audit pass.

# Full end-to-end Ghatana audit result

Target commit: `dfebf19f158be07c1623132eb5c00fc652ce57ff`

I verified the target SHA exists in `samujjwal/ghatana`; it is a merge commit titled “Merge branch 'main' of [https://github.com/samujjwal/ghatana.”](https://github.com/samujjwal/ghatana.”) The audit below is based on the repository snapshot at that SHA, not the small merge diff. 

I did not run CI/build commands locally. This is a source-grounded audit from the repo files, product registry, package scripts, Kernel/Studio/Data Cloud/YAPPC/Digital Marketing source surfaces, and the supplied hardened blueprint/prompt context. The uploaded prompt requires a full codebase-state audit rather than a commit diff audit. 

---

## A. Executive summary

Ghatana is structurally close to the target architecture, but not yet world-class executable end-to-end. The repo already has strong root-level wiring: Gradle includes platform Java modules, platform contracts, platform-kernel modules, platform plugins, generated product includes, product bridge modules, shared services, and integration tests.  The pnpm workspace also includes platform TypeScript libraries, Ghatana Studio, Data Cloud, YAPPC, Digital Marketing, FlashIt, DCMAAR, Tutorputor, Audio-Video, and shared service UI packages. 

The strongest current implementation areas are:

1. **Platform structure and governance scripts** — root `package.json` contains many high-value checks for architecture boundaries, product registry drift, Kernel lifecycle, Digital Marketing lifecycle pilot, Data Cloud providers, Studio API, YAPPC handoff, production stubs, observability, data access, design-system conformance, shared shells, orphan modules, deprecated packages, and product-shape capability matrix. 
2. **Kernel contracts** — `@ghatana/kernel-product-contracts` exists with Zod-based contracts for ProductUnit, ProductUnitIntent, providers, events, health, plugins, and artifact-intelligence evidence.  
3. **Kernel lifecycle service** — `@ghatana/kernel-lifecycle` exports planning, execution, gates, manifests, API handlers, provider context checks, and ProductUnitIntent application.  
4. **Digital Marketing pilot** — it is the only clearly enabled lifecycle pilot in the canonical product registry, with backend/web surfaces, lifecycle enabled, execution allowed, local compose deployment, and Kernel bridge conformance marked true. 
5. **Ghatana Studio shell** — `@ghatana/ghatana-studio` exists and depends on Kernel, Data Cloud-facing primitives, canvas, design system, UI builder, i18n, API, theme, tokens, and product-development libraries. 

The most serious blockers are:

1. **Bootstrap gate execution is currently not trustworthy.** `FileBootstrapGateProvider.evaluateGate()` passes every non-empty gate ID and returns synthetic evidence like `bootstrap-gate:<gateId>`. This means gates such as security scan, privacy check, bridge compliance, i18n readiness, a11y readiness, rollback readiness, and supply-chain provenance can appear green without executing real checks. 
2. **Data Cloud platform mode is target/partial, not executable truth.** Kernel platform mode correctly requires Data Cloud-backed events, artifacts, health, approvals, provenance, memory, and runtimeTruth providers, but Data Cloud registry conformance is still false and lifecycle execution is disabled.  
3. **YAPPC handoff is contract-present but not proven end-to-end.** Kernel has ProductUnitIntent application and artifact-intelligence contracts, but YAPPC is registered as a platform-provider with conformance false, no product manifest, and lifecycle execution disabled.  
4. **Future product shape readiness is correctly classified as planned/partial, but not executable.** PHR, Finance, and FlashIt have detailed lifecycle readiness reason codes and remain disabled, which is correct. They must not be treated as enabled lifecycle products yet.   
5. **Toolchain maturity is mixed.** Gradle Java, pnpm Vite React, Docker Buildx, and Compose Local are marked execution-ready, but Kubernetes/Helm/Terraform/mobile adapters are planning-only or partial.  
6. **Studio UX exists but is capability-gated and incomplete.** Navigation includes the intended Home, Ideas, Blueprints, Canvas, Develop, Lifecycle, Agents, Artifacts, Deployments, Health, Learn, and Settings sections, but Ideas, Lifecycle, Agents, Artifacts, Health, and Deployments are disabled/hidden until runtime and evidence readiness conditions are met.  

---

## B. System architecture map

| Layer                      | Current state                                                                                                                                                     | Classification                                            |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| Ghatana Studio             | Unified shell exists with routes, navigation, route guards, i18n keys, lifecycle page, manifest panels, approval queue, and Kernel lifecycle client.              | Existing but partial                                      |
| Product Development Kernel | Contracts, providers, lifecycle service, planners, executors, API handlers, manifests, events, ProductUnitIntent application, runtime truth hooks exist.          | Existing but partial                                      |
| Digital Marketing          | Enabled lifecycle pilot with backend/web surfaces, gates, manifests, approvals, local deploy/verify config.                                                       | Existing and executable, but gate authenticity is partial |
| Data Cloud                 | Platform-provider product with many planes/action modules and Kernel bridge modules; conformance false and lifecycle disabled.                                    | Target/partial                                            |
| YAPPC                      | Platform-provider product with backend/web surfaces, core/scaffold/agents/knowledge/refactorer modules, Kernel bridge; conformance false and lifecycle disabled.  | Existing but partial                                      |
| AEP / agents               | Data Cloud action-plane modules are present; Kernel exports agentic lifecycle action service; registry evidence does not prove full governed execution.           | Declared/partial                                          |
| Shared libraries           | Workspace includes design system, canvas, UI builder, Studio, Kernel packages, Data Cloud/YAPPC packages.                                                         | Existing but partial                                      |
| Product shape matrix       | Scripts/checks exist; PHR/Finance/FlashIt readiness is intentionally disabled with explicit blockers.                                                             | Existing but partial                                      |

---

## C. Journey-by-journey findings

### Journey 1 — Product ideation to ProductUnitIntent

**Current flow:** Kernel-side ProductUnitIntent contracts and validators exist, and `KernelLifecycleService.applyProductUnitIntent()` validates intent, enforces provider mode, requires an intent-capable registry provider, supports preview/apply, records lifecycle events, runtime truth, and provenance.   

**Gap:** YAPPC’s end-to-end UI/API export path is not proven by the inspected evidence. Studio has Ideas/Blueprints/Canvas routes, but Ideas is initially degraded/disabled, and YAPPC registry conformance remains false with no product manifest.  

**Required implementation:**

* Harden `products/yappc/kernel-bridge`.
* Wire YAPPC creator output to `ProductUnitIntentSchema`.
* Add Studio flow from Ideas/Blueprints/Canvas → ProductUnitIntent preview → apply.
* Add tests for invalid intent, preview mode, apply mode, provider-mode rejection, provenance/event/runtimeTruth recording.

---

### Journey 2 — Direct Product Development Kernel usage

**Current flow:** Studio’s lifecycle route supports ProductUnit selection, phase selection, environment, provider mode, dry-run, execution, run list, manifest tabs, approval queue, and failure diagnostics.   Kernel lifecycle service can plan, execute, list runs, resolve manifests, request approvals, submit approvals, and record runtime truth.  

**Gap:** Execution correctness is blocked by non-real bootstrap gates. Lifecycle can look complete while gates are synthetic pass-through. 

**Required implementation:**

* Replace pass-through gate provider with command-backed, contract-backed, or provider-backed gate implementations.
* For unsupported gates, return `NOT_READY`/blocked, never success.
* Add per-gate evidence contracts and tests.

---

### Journey 3 — Agentic product development

**Current flow:** Kernel exports `AgentLifecycleActionService`, agentic action contracts, lifecycle events, provider hooks, and artifact-intelligence contracts.  

**Gap:** Data Cloud/AEP is present structurally but not proven as a governed action runtime. Data Cloud conformance is false, platform providers are not ready, and lifecycle execution is disabled. 

**Required implementation:**

* Implement agent action request → Kernel lifecycle plan → policy/risk/mastery check → approval → execution → evidence.
* Store evidence in Data Cloud runtime truth/provenance/memory.
* Add tests for approval-required, rejected, failed gate, policy-denied, and successful agent execution.

---

### Journey 4 — Digital Marketing lifecycle pilot

**Current flow:** Digital Marketing is the most concrete pilot. Its registry entry marks lifecycle enabled and execution allowed. It has backend/web surfaces, bridge adapter evidence, Gradle/pnpm toolchains, local compose deployment, artifacts, environment, and health checks.  Its `kernel-product.yaml` defines phases, gates, required manifests, plugins, policy packs, approvals, deployment, provider modes, package config, and verify report fields.  

**Gap:** The pilot is not production-trustworthy until gates execute real checks. Also verify that package-phase adapter resolution uses `docker-buildx` from phase/package config rather than the web surface’s default `pnpm-vite-react` adapter.

**Required implementation:**

* Make `pnpm check:digital-marketing-lifecycle-pilot` validate actual gate outputs.
* Assert all required manifests exist after validate/test/build/package/deploy/verify.
* Add smoke test that fails if any bootstrap gate returns synthetic success without real evidence.

---

### Journey 5 — Artifact intelligence

**Current flow:** Kernel contracts include semantic artifact references, artifact graph summaries, product shape evidence, dependency graph evidence, residual island reports, risk hotspot reports, generated change set summaries, and evidence envelopes. 

**Gap:** YAPPC implementation-to-Kernel consumption is not proven. YAPPC has Kernel bridge and many modules, but conformance remains false and lifecycle disabled. 

**Required implementation:**

* YAPPC artifact compiler/decompiler emits `SemanticArtifactEvidenceEnvelope`.
* Data Cloud stores graph/provenance/memory.
* Kernel consumes references only, not YAPPC internals.
* Studio displays residual islands, risk hotspots, and recommendation evidence.

---

### Journey 6 — Data Cloud foundation

**Current flow:** Kernel supports bootstrap mode through file-backed providers for events, artifacts, health, approvals, provenance, memory, runtime truth, and registry.   Kernel platform mode rejects missing Data Cloud-backed providers. 

**Gap:** Data Cloud itself is not yet a proven platform-mode provider because registry conformance is false and lifecycle execution is disabled. 

**Required implementation:**

* Keep bootstrap mode file-backed and production-blocked.
* Implement Data Cloud-backed provider bridge in `products/data-cloud/extensions/kernel-bridge`.
* Add platform-mode integration tests proving events, artifacts, health, approvals, provenance, memory, and runtime truth are Data Cloud-backed.

---

### Journey 7 — Future product shape readiness

**Current flow:** PHR, Finance, and FlashIt are correctly classified as planned/partial and disabled with explicit readiness blockers. PHR requires consent, PII, audit, FHIR, and data sovereignty gates.  Finance requires regulatory gates, promotion approval, multi-module validation, and portal/operator/SDK adapter readiness.  FlashIt requires mobile adapters, preview security, personal data classification, and mobile bundle manifests. 

**Gap:** The shape matrix exists as a check surface, but future products must remain disabled until executable adapters, gates, manifests, and evidence are implemented.

**Required implementation:**

* Extend `check:product-shape-capability-matrix` to fail on premature enablement.
* Add product-specific readiness contracts without enabling lifecycle execution.
* Keep platform-provider products separate from ordinary business-product lifecycle enablement.

---

## D. Capability ownership matrix

| Capability                      | Correct owner                         | Current location                                 | Classification                        | Required fix                                                                   |
| ------------------------------- | ------------------------------------- | ------------------------------------------------ | ------------------------------------- | ------------------------------------------------------------------------------ |
| ProductUnit / ProductUnitIntent | Kernel contracts                      | `platform/typescript/kernel-product-contracts`   | Existing but partial                  | Complete YAPPC handoff and provider-backed apply tests                         |
| Lifecycle planning/execution    | Product Development Kernel            | `platform/typescript/kernel-lifecycle`           | Existing but partial                  | Harden real gate execution, run history, provider-mode tests                   |
| Bootstrap providers             | Kernel providers                      | `platform/typescript/kernel-providers`           | Existing but partial                  | Replace synthetic gate success with real checks or blocked status              |
| Data Cloud platform providers   | Data Cloud bridge                     | `products/data-cloud/extensions/kernel-bridge`   | Target/partial                        | Implement provider bridge and platform-mode integration tests                  |
| Digital Marketing pilot         | Product + Kernel config               | `products/digital-marketing/kernel-product.yaml` | Existing but partial                  | Prove validate/test/build/package/deploy/verify with real gates                |
| Toolchain adapters              | Kernel toolchains                     | `platform/typescript/kernel-toolchains`          | Mixed                                 | Keep only execution-ready adapters default-safe; feature-flag partial adapters |
| Studio shell                    | Ghatana Studio                        | `platform/typescript/ghatana-studio`             | Existing but partial                  | Make lifecycle, artifacts, health, deployments truly data-backed               |
| Artifact intelligence           | YAPPC + shared contracts + Data Cloud | YAPPC + `kernel-product-contracts`               | Existing contracts, partial execution | Wire compiler evidence to Data Cloud and Kernel references                     |
| Agentic development             | Data Cloud/AEP + Kernel contracts     | Data Cloud action plane + Kernel lifecycle       | Declared/partial                      | Enforce policy/mastery/approval/evidence before execution                      |
| Product shape readiness         | Platform coherence                    | registry + checks                                | Existing but partial                  | Prevent target-state claims and premature lifecycle enablement                 |

---

## E. Prescriptive implementation plan

### Workstream 1 — Platform coherence and governance

| File/path                                              | Change                                                                                                                                                | Validation                                                                  |
| ------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `config/canonical-product-registry.json`               | Keep lifecycle status truthful. Do not allow `lifecycleExecutionAllowed: true` unless adapters, gates, manifests, tests, and evidence are executable. | `pnpm check:product-registry`, `pnpm check:product-shape-capability-matrix` |
| `config/toolchain-adapter-registry.json`               | Enforce consistency between `status`, `readiness`, `executionImplemented`, `safeForDefault`, and `lifecycleEnabled`.                                  | `pnpm check:toolchain-adapter-contracts`                                    |
| `scripts/check-current-state-claims.mjs`               | Fail docs/UI that describe target architecture as current implementation.                                                                             | `pnpm check:current-state-claims`                                           |
| `scripts/check-domain-boundaries.mjs`                  | Enforce Kernel/YAPPC/Data Cloud/product boundaries.                                                                                                   | `pnpm check:domain-boundaries`                                              |
| `scripts/check-architecture-boundaries` / root scripts | Make architecture check a required pre-merge gate.                                                                                                    | `pnpm check:architecture-boundaries`                                        |

### Workstream 2 — Kernel lifecycle hardening

| File/path                                                                      | Change                                                                                                                                                                  | Validation                                             |
| ------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------ |
| `platform/typescript/kernel-lifecycle/src/service/KernelLifecycleService.ts`   | Add stronger integration tests for plan/execute/apply intent, provider-mode failure, approval-required, manifest corruption, runtime truth failure, provenance failure. | `pnpm --dir platform/typescript/kernel-lifecycle test` |
| `platform/typescript/kernel-lifecycle/src/api/KernelLifecycleApiHandlers.ts`   | Verify auth/scope/provider-mode errors map cleanly to Studio client errors.                                                                                             | `pnpm check:studio-kernel-api`                         |
| `platform/typescript/kernel-lifecycle/src/manifest/LifecycleManifestWriter.ts` | Guarantee every required manifest is written or phase fails.                                                                                                            | `pnpm check:kernel-lifecycle-truth`                    |
| `platform/typescript/kernel-lifecycle/src/gates/GateExecutor.ts`               | Require real gate provider output and evidence refs.                                                                                                                    | `pnpm check:kernel-platform-lifecycle`                 |

### Workstream 3 — Replace fake bootstrap gate success

| File/path                                                                            | Change                                                                                                                                    | Validation                                                       |
| ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| `platform/typescript/kernel-providers/src/gates/FileBootstrapGateProvider.ts`        | Remove universal pass behavior. Return blocked/failed for unsupported gates. Execute known gates through real scripts or typed providers. | `pnpm check:production-readiness`, `pnpm check:production-stubs` |
| `platform/typescript/kernel-providers/src/factory/createBootstrapKernelProviders.ts` | Register concrete gate providers by capability, not one pass-through class for all gates.                                                 | `pnpm check:kernel-product-boundary-audit`                       |
| `products/digital-marketing/kernel-product.yaml`                                     | Ensure every listed gate maps to a real executable provider or explicit `NOT_READY` blocker.                                              | `pnpm check:digital-marketing-lifecycle-pilot`                   |

### Workstream 4 — Digital Marketing lifecycle pilot

| File/path                                              | Change                                                                                                 | Validation                                                                                     |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| `products/digital-marketing/kernel-product.yaml`       | Ensure validate/test/build/package/deploy/verify required manifests are generated and schema-valid.    | `pnpm validate:digital-marketing`, `pnpm build:digital-marketing`                              |
| `products/digital-marketing/dm-kernel-bridge/**`       | Prove bridge compliance with real integration tests.                                                   | `pnpm check:bridge-compliance`, `./gradlew :products:digital-marketing:dm-kernel-bridge:check` |
| `products/digital-marketing/deploy/local.compose.yaml` | Verify expected services and health checks align with lifecycle verify config.                         | `pnpm deploy:local:digital-marketing`, `pnpm verify:local:digital-marketing`                   |
| `products/digital-marketing/ui/**`                     | Ensure web route contract, a11y, i18n, bundle budget, and design-system conformance are actual checks. | `pnpm check:design-system-conformance`, `pnpm test:digital-marketing-web`                      |

### Workstream 5 — Toolchain adapter correctness

| File/path                                                                        | Change                                                                                                                | Validation                                               |
| -------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| `platform/typescript/kernel-toolchains/src/adapters/GradleJavaServiceAdapter.ts` | Fix dev-process semantics: do not block indefinitely; capture process metadata/PID or declare dev as supervised mode. | adapter tests + `pnpm check:toolchain-adapter-contracts` |
| `platform/typescript/kernel-toolchains/src/adapters/PnpmViteReactAdapter.ts`     | Same dev-process supervision fix; ensure package phase never conflicts with Docker Buildx package config.             | adapter tests                                            |
| `platform/typescript/kernel-toolchains/src/adapters/DockerBuildxAdapter.ts`      | Align registry support flags with Digital Marketing package usage or block unsupported provider modes explicitly.     | Docker adapter tests                                     |
| `config/toolchain-adapter-registry.json`                                         | Keep mobile/cloud adapters disabled until execution and output validation tests exist.                                | `pnpm check:toolchain-adapter-registry-schema`           |

### Workstream 6 — Studio UX and API contracts

| File/path                                                                    | Change                                                                                                                               | Validation                                           |
| ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------- |
| `platform/typescript/ghatana-studio/src/App.tsx`                             | Keep unified shell but add end-to-end route tests for all visible/disabled/hidden states.                                            | `pnpm --dir platform/typescript/ghatana-studio test` |
| `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`      | Ensure route exposure exactly reflects product capability state and current-state discipline.                                        | `pnpm check:audited-ui-workflows`                    |
| `platform/typescript/ghatana-studio/src/routes/LifecyclePage.tsx`            | Add UX evidence for blocked gates, unsupported provider mode, missing manifest, pending approval, failed execution, and remediation. | Studio route tests                                   |
| `platform/typescript/ghatana-studio/src/data/StudioLifecycleDataContext.tsx` | Replace silent no-op default actions with test-safe throwing defaults or explicit unconfigured state handlers.                       | component/context tests                              |
| `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`        | Verify API error parsing against real `ApiClient` error shape.                                                                       | API contract tests                                   |

### Workstream 7 — Data Cloud platform-mode providers

| File/path                                            | Change                                                                                                        | Validation                                                                                     |
| ---------------------------------------------------- | ------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `products/data-cloud/extensions/kernel-bridge/**`    | Implement Data Cloud-backed events, artifacts, health, approvals, provenance, memory, runtimeTruth providers. | `pnpm check:data-cloud-platform-providers`                                                     |
| `products/data-cloud/planes/action/kernel-bridge/**` | Wire AEP/action evidence to Kernel lifecycle action contracts.                                                | Data Cloud integration tests                                                                   |
| `products/data-cloud/delivery/api/**`                | Expose runtime truth/provenance/memory APIs used by Studio and Kernel.                                        | `pnpm check:data-cloud-ui-contracts`, `./gradlew :products:data-cloud:integration-tests:check` |

### Workstream 8 — YAPPC artifact intelligence and ProductUnitIntent handoff

| File/path                                                                   | Change                                                                                        | Validation                                        |
| --------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| `products/yappc/kernel-bridge/**`                                           | Implement ProductUnitIntent export/apply bridge using Kernel contracts only.                  | `pnpm check:yappc-product-unit-intent-handoff`    |
| `platform/typescript/kernel-product-contracts/src/artifact-intelligence/**` | Keep semantic evidence contracts stable and versioned.                                        | contract tests                                    |
| `products/yappc/frontend/**`                                                | Add UI path from idea/blueprint/canvas to ProductUnitIntent preview/apply.                    | YAPPC UI tests                                    |
| YAPPC artifact compiler/decompiler packages                                 | Emit semantic evidence envelopes, residual islands, risk hotspots, dependency graph evidence. | `pnpm check:yappc-artifact-intelligence-boundary` |

---

## F. Release plan

### Release 0 — Coherence and truth enforcement

Goal: prevent false confidence.

Exit criteria:

* No pass-through production gates.
* Current-state vs target-state checks fail on unclassified claims.
* Product registry and package registry are drift-free.

Validation:

```bash
pnpm check:architecture-boundaries
pnpm check:current-state-claims
pnpm check:production-stubs
pnpm check:domain-boundaries
pnpm check:deprecated-packages
pnpm check:orphan-modules
```

### Release 1 — Digital Marketing lifecycle pilot E2E

Goal: prove one product can validate/test/build/package/deploy/verify with real evidence.

Validation:

```bash
pnpm build:kernel-lifecycle-platform
pnpm check:digital-marketing-lifecycle-pilot
pnpm validate:digital-marketing
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
```

### Release 2 — Agentic development support

Goal: agent proposes action, Kernel plans, policy/gates/approval run, evidence is stored.

Validation:

```bash
pnpm check:agentic-lifecycle-action-contracts
pnpm check:kernel-lifecycle-truth
pnpm check:studio-kernel-api
```

### Release 3 — Data Cloud platform-mode providers

Goal: platform mode uses Data Cloud-backed providers and bootstrap mode remains independent.

Validation:

```bash
pnpm check:kernel-provider-mode
pnpm check:data-cloud-platform-providers
pnpm check:data-cloud-sdk-drift
pnpm check:data-cloud-runbook-smoke
```

### Release 4 — Artifact intelligence integration

Goal: YAPPC artifact compiler/decompiler emits evidence, Data Cloud stores it, Kernel consumes references, Studio visualizes risks.

Validation:

```bash
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:product-artifact-contracts
```

### Release 5 — Product shape expansion readiness

Goal: enable PHR, Finance, FlashIt, and other products only when their gates/adapters/manifests are executable.

Validation:

```bash
pnpm check:product-shape-capability-matrix
pnpm check:lifecycle-registry-config-drift
pnpm check:product-manifest-contracts
pnpm check:product-ci-matrices
```

---

## G. Validation command suite

Run this after implementing the above plan:

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
pnpm check:production-stubs
pnpm check:kernel-product-boundary-audit
pnpm check:architecture-boundaries
pnpm check:toolchain-adapter-contracts
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:data-cloud-platform-providers
./gradlew build
./gradlew check
```

---

## Top 10 fixes to prioritize

1. Replace `FileBootstrapGateProvider` pass-through success with real executable gate providers.
2. Make unsupported gates return blocked/`NOT_READY`, never success.
3. Prove Digital Marketing validate/test/build/package/deploy/verify produces all required manifests.
4. Add platform-mode Data Cloud provider bridge tests.
5. Wire YAPPC ProductUnitIntent handoff into Studio and Kernel API.
6. Add artifact-intelligence evidence flow from YAPPC → Data Cloud → Kernel → Studio.
7. Fix dev-process supervision in Gradle and pnpm adapters.
8. Verify Studio client error mapping against real API error shapes.
9. Keep PHR/Finance/FlashIt disabled until their readiness gates are real.
10. Promote architecture/coherence checks to mandatory CI gates.

Bottom line: **the architecture direction is coherent, and the repo has the right module/package skeleton plus many strong guardrails. The main production-readiness gap is proof: real gates, real provider-backed evidence, real Data Cloud platform mode, and one fully trustworthy Digital Marketing lifecycle path.**
