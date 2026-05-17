## Audit execution basis

Executed against `samujjwal/ghatana` at commit `e10d360a3fe5b0298cdb4b02cc0d4cd62148202f`.

This audit is based on the complete codebase state visible at the target commit snapshot, not only the commit diff. I used current code, configs, scripts, committed lifecycle evidence, registry files, and canonical uploaded guidance as source of truth. I did not run repository commands locally; validation status below is based on committed scripts/evidence and repository inspection.

The repo already contains phase scripts, domain registry classifications, lifecycle evidence, and several executable governance checks, so this is **not** a start-from-zero plan. The highest-value next work is to make the partial areas consistently executable, portable, CI-backed, and aligned with phase terminology.

---

# A. Executive summary

## What is close to world-class

The repo has a strong governance foundation. `package.json` already defines phase-level checks from `check:phase0` through `check:phase8`, plus `check:world-class-platform-readiness`, Kernel checks, product-shape checks, Digital Marketing lifecycle checks, Data Cloud provider checks, Studio/API checks, artifact intelligence checks, production stub checks, deprecated package checks, and architecture boundary checks. 

The canonical product registry correctly treats **Digital Marketing** as the executable lifecycle pilot. Its registry entry has lifecycle enabled, ready migration status, backend and web surfaces, Gradle and pnpm adapters, required artifacts, local deployment, bridge conformance, and `lifecycleExecutionAllowed: true`. 

Digital Marketing has committed lifecycle evidence showing planned phases passing and smoke execution passing for `validate`, `test`, `build`, `package`, `deploy`, and `verify`, with lifecycle plans, lifecycle results, artifact manifests, deployment manifests, verify health reports, lifecycle health snapshots, gate result manifests, lifecycle events, run IDs, correlation IDs, and bootstrap provider mode recorded. 

Kernel contracts are no longer merely aspirational. `ProductUnitIntent` exists as a public contract with application result, provider mode, registry/source provider IDs, lifecycle event refs, provenance refs, runtime truth refs, blocked reasons, and errors.  Agentic lifecycle contracts also exist with policy, mastery, approval, verification, rollback, privacy classification, retention, redaction, and raw-command blocking. 

Ghatana Studio has a canonical navigation model for Home, Ideas, Blueprints, Canvas, Develop, Lifecycle, Agents, Artifacts, Deployments, Health, Learn, and Settings, with ownership, status, exposure policy, evidence refs, and capability gating. 

## What is still missing or risky

Several domains remain **existing but partial**, including Platform Coherence & Governance, artifact/provenance, deployment/release, Data Cloud runtime truth, AEP agent runtime governance, semantic artifact intelligence, canvas/diagramming, UI builder preview, design system registry/generator, and Ghatana Studio route/data surfaces. The domain registry explicitly records these classifications and blocking gaps.  

The implementation tracker still uses **Release** terminology, while the current planning model should use **Phase** terminology. This creates avoidable confusion and should be reconciled into one phase-based status model. 

The Digital Marketing evidence pack stores absolute local filesystem paths under `/Users/samujjwal/Development/ghatana/...`. That is useful as local proof but not ideal as portable CI/runtime truth. The next hardening step should emit logical refs or repo-relative refs and archive the underlying evidence in CI. 

Data Cloud and YAPPC are correctly classified as **platform-provider** products with lifecycle execution disabled, but their platform-provider path is still partial. Data Cloud needs bootstrap/platform separation and runtime truth provider completion; YAPPC needs creator lifecycle separation, ProductUnitIntent export proof, and artifact intelligence evidence contracts before ordinary lifecycle execution should be considered. 

---

# B. Goal and status register

| Goal ID | Goal                               |                         Correct owner | Current status                                   | Evidence                                                                                        | Gap                                                                                           |   Phase |
| ------- | ---------------------------------- | ------------------------------------: | ------------------------------------------------ | ----------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- | ------: |
| G-01    | Platform Coherence & Governance    |                              Platform | Existing but partial                             | `config/domain-registry.json` defines governance domain, source of truth, checks, phase `[0,8]` | Boundary enforcement and current-state claim review remain partially manual                   |    0, 8 |
| G-02    | Phase-based execution model        |                              Platform | Existing but partial                             | `package.json` has `check:phase0` through `check:phase8`                                        | Tracker/docs still use “Release” terminology                                                  |    0, 8 |
| G-03    | Ghatana Studio unified shell       |                              Platform | Existing but partial                             | Studio nav model has route ownership/status/exposure                                            | Some route/data surfaces remain partial/degraded/disabled                                     |       1 |
| G-04    | ProductUnitIntent handoff          |                        Kernel + YAPPC | Existing but partial                             | Contract, applier, Studio Blueprints route exist                                                | Needs full YAPPC UI/API → Kernel preview/apply → runtime truth/provenance E2E proof           | 1, 3, 6 |
| G-05    | Kernel lifecycle truth             |            Product Development Kernel | Existing and executable for pilot                | Domain registry marks Kernel lifecycle executable; Digital Marketing evidence passes            | Needs more product-shape validation without enabling unsafe execution                         | 2, 3, 7 |
| G-06    | Digital Marketing lifecycle pilot  |                      Product + Kernel | Existing and executable                          | Registry enabled and evidence pack passed                                                       | Needs portable CI evidence, rollback/promotion runtime proof, and local absolute path cleanup |       2 |
| G-07    | Toolchain adapters                 |                                Kernel | Existing and executable for current pilot        | Domain registry marks toolchain runtime executable                                              | Mobile and future product-shape adapters remain planned/partial                               |    3, 7 |
| G-08    | Artifact/provenance chain          |                   Kernel + Data Cloud | Existing but partial                             | Artifact/provenance domain classified partial                                                   | Need stronger run → artifact → deployment → provenance linkage across providers               |    3, 5 |
| G-09    | Deployment/release/rollback        |                                Kernel | Existing but partial                             | Deployment/release domain classified partial                                                    | Promotion semantics and rollback evidence coverage incomplete                                 |       3 |
| G-10    | Agentic product development        |               Kernel + AEP/Data Cloud | Existing but partial                             | Agent contract and service exist                                                                | Needs AEP/Data Cloud-backed end-to-end execution, approval UI, and trace ledger proof         |       4 |
| G-11    | Data Cloud platform-mode providers |                            Data Cloud | Existing but partial                             | Data Cloud runtime truth domain classified partial                                              | Provider coverage incomplete; lifecycle execution correctly disabled                          |       5 |
| G-12    | Artifact intelligence              | YAPPC + Kernel contracts + Data Cloud | Existing but partial                             | Semantic artifact intelligence domain classified partial                                        | Semantic persistence and residual reporting remain partial                                    |       6 |
| G-13    | Future product shape readiness     |                   Platform + Products | Existing but partial / declared only             | PHR and FlashIt are planned/disabled; Data Cloud/YAPPC platform-provider disabled               | Need product-shape matrix hardening without premature lifecycle enablement                    |       7 |
| G-14    | O11y/security/privacy/testing      |                              Platform | Mixed: executable core, partial product coverage | Domain registry marks security, observability, and testing executable                           | Product-by-product conformance and CI artifact evidence still need expansion                  |       8 |

---

# C. System architecture map

```text
Ghatana Studio
  Status: existing but partial
  Owns: unified shell, route exposure, customer UX, lifecycle visibility
  Must not own: lifecycle truth or product domain logic

YAPPC
  Status: existing but partial
  Owns: ideation, blueprinting, canvas workflows, ProductUnitIntent export, artifact intelligence
  Must not own: Kernel lifecycle execution

Product Development Kernel
  Status: existing and executable for Digital Marketing pilot
  Owns: ProductUnit contracts, ProductUnitIntent application, lifecycle plans/results, adapters, gates, artifacts, deployments, approvals, runtime truth contracts

Data Cloud
  Status: existing but partial
  Owns: runtime truth, events, provenance, memory, knowledge, provider bridges, AEP/action foundation
  Must not be required for Kernel bootstrap mode

AEP / agents
  Status: existing but partial
  Owns: governed action execution and agent runtime integration
  Must execute through Kernel contracts for product lifecycle work

Digital Marketing
  Status: executable pilot
  Owns: domain behavior and product-specific lifecycle config
  Proves: validate/test/build/package/deploy/verify through Kernel

PHR / Finance / FlashIt / others
  Status: shape-validation targets
  Own: product domain behavior and product-specific gates
  Must not force Kernel into product-specific assumptions
```

This matches the uploaded audit standard: one capability, one correct owner, one canonical contract, one reusable implementation, many consumers, and no hidden duplicates or boundary leaks. 

---

# D. Journey-by-journey findings

## Journey 1 — Product ideation to ProductUnitIntent

**Current flow:** ProductUnitIntent is a typed Kernel contract, the lifecycle applier delegates to `KernelLifecycleService`, and Studio’s Blueprints route displays ProductUnitIntent candidate data, evidence refs, provenance, preview/apply buttons, and handoff result state.   

**Status:** Existing but partial.

**Gaps:** The contract and UI exist, but this still needs full E2E proof from YAPPC creator/canvas/API through Kernel preview/apply, including runtime truth, provenance, event refs, failure states, and CI evidence.

**Required TODOs:** Add/complete E2E tests for YAPPC export → Studio preview/apply → Kernel application result. Ensure Blueprints route has no hardcoded user-facing status strings and emits observable error states instead of relying only on context-captured errors.

---

## Journey 2 — Direct Product Development Kernel usage

**Current flow:** Kernel lifecycle is executable for Digital Marketing. The root scripts include lifecycle build/test/validate/package/deploy/verify commands, phase checks, Kernel platform lifecycle checks, and Digital Marketing lifecycle pilot checks. 

**Status:** Existing and executable for the pilot; partial as a general platform capability.

**Gaps:** Digital Marketing is validated; future product shapes are not all execution-ready. Studio exposes lifecycle routes but some remain degraded/disabled until runtime and Data Cloud evidence are ready.  

**Required TODOs:** Keep Digital Marketing as the only fully executable pilot until future product gates/adapters are proven. Add richer run-history, approval, artifact, deployment, and health panels backed by real lifecycle manifests.

---

## Journey 3 — Agentic product development

**Current flow:** AgentLifecycleActionRequest exists with strict schema validation, policy/mastery/approval/verification fields, evidence refs, rollback plan refs, privacy fields, and raw-command rejection.  AgentLifecycleActionService validates requests, enforces provider requirements in platform mode, records runtime truth/provenance/memory when providers exist, and uses Kernel planner/executor boundaries.  

**Status:** Existing but partial.

**Gaps:** Contract and service are strong, but AEP/Data Cloud end-to-end runtime proof, trace ledger, central agent registry governance, approval UI, and failure/rollback proof still need to be completed.

**Required TODOs:** Wire AEP/Action Plane execution through `AgentLifecycleActionService`, add Data Cloud-backed governance providers, expose approvals and audit trails in Studio, and test denial/approval/verification/failure paths.

---

## Journey 4 — Digital Marketing lifecycle pilot

**Current flow:** Digital Marketing has lifecycle enabled in the product registry and has a detailed `kernel-product.yaml` with required manifests, plugin bindings, policy packs, backend/web surfaces, adapters, expected outputs, gates, local deployment config, provider modes, approvals, package config, and verify checks.   

**Status:** Existing and executable.

**Gaps:** Evidence pack confirms validate/test/build/package/deploy/verify smoke phases, but rollback is only shown as planned phase `ok`, not as a smoke-executed phase with a rollback manifest. Evidence paths are absolute local paths, which should be converted to portable evidence references. 

**Required TODOs:** Add rollback and approval-path smoke proof, emit repo-relative/logical evidence refs, archive `.kernel/out` manifests in CI, and include promotion/rollback validation once safe.

---

## Journey 5 — Artifact intelligence

**Current flow:** Semantic artifact intelligence is represented in Kernel contracts and boundary checks, and YAPPC is registered as a platform-provider product with artifact-intelligence readiness requirements.  

**Status:** Existing but partial.

**Gaps:** Semantic model persistence, residual island reporting, Data Cloud graph/provenance storage, and Studio visualization are still partial. Kernel must continue consuming references/evidence only, not YAPPC internals.

**Required TODOs:** Complete semantic evidence contracts, residual island reports, RiskHotspotReport, Data Cloud graph storage, and Studio visualizations. Strengthen `check:yappc-artifact-intelligence-boundary`.

---

## Journey 6 — Data Cloud foundation

**Current flow:** Data Cloud is a platform-provider product with many modules and provider bridge locations, but lifecycle execution is disabled. The registry lists blockers: platform-provider mode, bootstrap/platform separation, and runtime truth provider requirements. 

**Status:** Existing but partial.

**Gaps:** Platform mode depends on incomplete provider coverage. Data Cloud must remain disabled for ordinary lifecycle execution until Kernel can build/deploy it without depending on Data Cloud providers.

**Required TODOs:** Complete Data Cloud-backed providers for events, artifacts, health, provenance, memory, runtime truth, telemetry, and policy evidence. Add circular bootstrap checks that prove Kernel bootstrap mode can build/deploy Data Cloud independently.

---

## Journey 7 — Future product shape readiness

**Current flow:** PHR, FlashIt, Data Cloud, and YAPPC are explicitly not ordinary executable lifecycle products. PHR is planned/disabled and requires healthcare gates such as consent, PII classification, audit evidence, FHIR contract validation, and tenant-data sovereignty.  FlashIt is planned/disabled and requires mobile adapters, preview-security gates, personal data classification, and IPA/AAB artifact contracts. 

**Status:** Existing but partial / declared only depending product.

**Gaps:** Future shape readiness is correctly modeled, but not execution-ready. The next step is matrix hardening, not migration.

**Required TODOs:** Keep PHR/FlashIt/Data Cloud/YAPPC execution disabled until required gates/adapters/manifests are proven. Expand product shape matrix with exact missing Kernel, Data Cloud, YAPPC, and product-owned gaps.

---

# E. Audit dimension findings

## 1. Architecture and ownership

The domain registry is a strong source of truth. It separates Platform Coherence, Kernel lifecycle, toolchain runtime, artifact/provenance, deployment/release, Data Cloud runtime truth, AEP governance, semantic artifact intelligence, canvas, UI builder, design system, Studio, product packs, event streaming, security/privacy, observability, and testing.   

**Main issue:** Domain registry and phase scripts are ahead of some docs/tracker terminology.

## 2. UI/UX

Studio has a coherent route model with ownership and exposure gates. 

**Main issue:** Some routes are intentionally disabled/degraded, and route-body i18n/status vocabulary is not fully complete.

## 3. API contracts

ProductUnitIntent and AgentLifecycleAction contracts are strong and schema-backed.  

**Main issue:** More E2E contract tests are needed across YAPPC → Studio → Kernel → Data Cloud.

## 4. Backend and storage

Bootstrap file-backed lifecycle evidence is strong for Digital Marketing, but Data Cloud platform-mode providers remain partial.  

## 5. AI/ML-native behavior

Agentic contracts include evidence, approvals, verification, mastery, privacy, retention, and redaction. 

**Main issue:** Agent execution is not yet fully proven through AEP/Data Cloud runtime truth.

## 6. Observability/security/privacy/i18n/a11y

Security, observability, and testing domains are classified executable, but product-by-product proof still varies. 

## 7. Testing and CI/CD

The repo has extensive checks and phase scripts.  The copilot rules prohibit TODO/FIXME in production paths, stubs in production-critical paths, test-only mocks in production, object-literal assertions, and disabled tests without issue references. 

**Main issue:** Remaining production-stub warning backlog and CI portability of generated evidence.

## 8. Cleanup and consolidation

The domain registry explicitly calls out deprecated split canvas packages, deprecated package aliases, partial current-state claim checks, and partially manual boundary enforcement. 

## 9. Current-state vs target-state discipline

This is mostly implemented through registry classifications, but implementation tracker language still creates confusion because it mixes “Release” and “Workstream” terminology while the current governance model uses phases. 

---

# F. Capability ownership matrix

| Capability              | Correct owner                         | Current status                | Problem                                     | Required fix                               |   Phase |
| ----------------------- | ------------------------------------- | ----------------------------- | ------------------------------------------- | ------------------------------------------ | ------: |
| Phase governance        | Platform Coherence                    | Existing but partial          | Tracker still says Release                  | Convert tracker/docs to phase model        |    0, 8 |
| Studio shell            | Platform Studio                       | Existing but partial          | Disabled/degraded route surfaces remain     | Complete route data states and tests       |       1 |
| ProductUnitIntent       | Kernel contracts + YAPPC producer     | Existing but partial          | E2E proof incomplete                        | Add YAPPC → Kernel preview/apply tests     |    1, 3 |
| Kernel lifecycle        | Product Development Kernel            | Existing executable for pilot | Needs non-pilot shape hardening             | Keep execution limited; expand matrix      | 2, 3, 7 |
| Digital Marketing pilot | Product + Kernel                      | Existing executable           | Absolute evidence paths; rollback smoke gap | Portable evidence + rollback proof         |       2 |
| Data Cloud providers    | Data Cloud                            | Existing but partial          | Platform provider coverage incomplete       | Complete provider bridge contracts         |       5 |
| Agentic lifecycle       | Kernel + AEP/Data Cloud               | Existing but partial          | Runtime/AEP proof incomplete                | Wire governance providers and trace ledger |       4 |
| Artifact intelligence   | YAPPC + Kernel contracts + Data Cloud | Existing but partial          | Persistence/residual reporting partial      | Complete semantic evidence flow            |       6 |
| Canvas/UI builder       | Platform libraries                    | Existing but partial          | Deprecated split package/preview gates      | Remove aliases, harden preview security    |    1, 8 |
| Testing/governance      | Platform                              | Existing executable           | Warning backlog and portability gaps        | Burn down stubs and evidence drift         |       8 |

---

# G. Phase-by-phase implementation plan and TODOs

## Phase 0 — Current-state baseline and coherence

| TODO ID | File(s)                                                                                | Current issue                                                         | Required change                                                                                    | Validation                                                          |
| ------- | -------------------------------------------------------------------------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| P0-01   | `docs/implementation/GHATANA_WORLD_CLASS_IMPLEMENTATION_TRACKER.md`                    | Uses “Release 0” terminology                                          | Convert to phase terminology and add phase/journey/dimension crosswalk                             | `pnpm check:current-state-claims && pnpm check:doc-claims-evidence` |
| P0-02   | `config/domain-registry.json`, `scripts/validate-domain-registry.mjs`                  | Some classifications are accurate but still manually interpreted      | Require every domain to include phase, journey, owner, evidence, blocking gaps, exit criteria      | `pnpm check:domain-registry && pnpm check:phase0`                   |
| P0-03   | `scripts/check-current-state-claims.mjs`                                               | Complete/partial claims can drift between tracker and domain registry | Fail when tracker says complete but domain registry says partial without explicit scoped rationale | `pnpm check:current-state-claims`                                   |
| P0-04   | `scripts/check-domain-boundaries.mjs`, `scripts/check-platform-product-boundaries.mjs` | Boundary enforcement remains partially manual                         | Add strict mode for Kernel/YAPPC/Data Cloud/provider boundaries                                    | `pnpm check:architecture-boundaries`                                |

## Phase 1 — Unified Studio shell, terminology, and navigation

| TODO ID | File(s)                                                                        | Current issue                                                              | Required change                                                                            | Validation                                                                                                      |
| ------- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------- |
| P1-01   | `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`        | Good route model, but disabled/degraded routes still require runtime proof | Add per-route journey/dimension mapping and evidence-required fields                       | `pnpm check:phase1`                                                                                             |
| P1-02   | `platform/typescript/ghatana-studio/src/routes/*.tsx`                          | Some route-body strings/statuses remain literal                            | Move all customer-facing route strings/status labels to i18n/status vocabulary             | `pnpm --dir platform/typescript/ghatana-studio test && pnpm --dir platform/typescript/ghatana-studio test:a11y` |
| P1-03   | `BlueprintsPage.tsx`, `StudioLifecycleDataContext`                             | ProductUnitIntent UI exists but needs stronger failure telemetry           | Add observable error reason codes and visible error diagnostics for preview/apply failures | `pnpm check:studio-kernel-api`                                                                                  |
| P1-04   | `AgentsPage.tsx`, `ArtifactsPage.tsx`, `HealthPage.tsx`, `DeploymentsPage.tsx` | Several routes are disabled/degraded due missing runtime evidence          | Add explicit blocked/degraded panels tied to provider readiness evidence                   | `pnpm check:shared-ui-state-coverage`                                                                           |

## Phase 2 — Digital Marketing lifecycle pilot E2E

| TODO ID | File(s)                                                                                       | Current issue                                                                    | Required change                                                     | Validation                                                                                                       |
| ------- | --------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| P2-01   | `.kernel/evidence/digital-marketing/*`, `scripts/check-digital-marketing-lifecycle-pilot.mjs` | Evidence uses absolute local paths                                               | Emit repo-relative/logical manifest refs and archive evidence in CI | `pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing` |
| P2-02   | `products/digital-marketing/kernel-product.yaml`                                              | Rollback is planned but not proven as smoke runtime path                         | Add rollback smoke proof with rollback manifest and health snapshot | `pnpm check:phase2`                                                                                              |
| P2-03   | `products/digital-marketing/kernel-product.yaml`, `platform/typescript/kernel-release`        | Promote/rollback approval paths are declared but need stronger proof             | Add approval accepted/rejected/pending runtime evidence tests       | `pnpm check:product-deployment-contracts && pnpm check:kernel-lifecycle-truth`                                   |
| P2-04   | Digital Marketing UI/API route tests                                                          | Pilot backend/web execution is strong, but customer UI proof should be recursive | Add Studio-visible lifecycle pilot E2E flow                         | `pnpm check:audited-e2e-workflow`                                                                                |

## Phase 3 — Kernel hardening

| TODO ID | File(s)                                                                   | Current issue                                                                 | Required change                                                                    | Validation                                                                           |
| ------- | ------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| P3-01   | `platform/typescript/kernel-artifacts`, `kernel-providers/src/provenance` | Artifact/provenance is partial                                                | Link lifecycle run → artifact manifest → deployment manifest → provenance refs     | `pnpm check:product-artifact-contracts`                                              |
| P3-02   | `platform/typescript/kernel-deployment`, `kernel-release`                 | Promotion semantics need broader evidence                                     | Add promotion and rollback contract tests with manifest refs and health refs       | `pnpm check:product-deployment-contracts`                                            |
| P3-03   | `platform/typescript/kernel-lifecycle`                                    | Kernel executable for pilot, but product-general readiness must remain strict | Add product-shape-only mode tests for PHR/FlashIt/Data Cloud/YAPPC                 | `pnpm check:kernel-platform-lifecycle && pnpm check:product-shape-capability-matrix` |
| P3-04   | `scripts/kernel-product.mjs`                                              | Need prevent raw tool bypass and hidden product assumptions                   | Add explicit checks that lifecycle execution always goes through adapter contracts | `pnpm check:toolchain-adapter-contracts`                                             |

## Phase 4 — Agentic development through Kernel contracts

| TODO ID | File(s)                                                        | Current issue                                      | Required change                                                                              | Validation                                                                                                             |
| ------- | -------------------------------------------------------------- | -------------------------------------------------- | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| P4-01   | `AgentLifecycleActionService.ts`, Data Cloud Action Plane      | Service exists, AEP E2E proof partial              | Wire AEP gateway/server to submit governed Kernel lifecycle action requests                  | `pnpm check:agentic-lifecycle-action-contracts`                                                                        |
| P4-02   | `platform/typescript/ghatana-studio/src/routes/AgentsPage.tsx` | Agent UI needs provider-backed trace/approval data | Add policy/mastery/approval/verification trace panels                                        | `pnpm check:phase4`                                                                                                    |
| P4-03   | `products/data-cloud/planes/action/**`                         | Agent runtime governance partial                   | Add central registry + trace ledger + rollback/fallback proof                                | `./gradlew :platform:java:agent-core:check && pnpm check:phase4`                                                       |
| P4-04   | Agent lifecycle tests                                          | Need negative-path proof                           | Add tests for raw-command denial, missing evidence, approval rejection, verification failure | `pnpm --dir platform/typescript/kernel-product-contracts test && pnpm --dir platform/typescript/kernel-lifecycle test` |

## Phase 5 — Data Cloud platform-mode providers

| TODO ID | File(s)                                            | Current issue                                                                | Required change                                                                                    | Validation                                         |
| ------- | -------------------------------------------------- | ---------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | -------------------------------------------------- |
| P5-01   | `products/data-cloud/libs/kernel-bridge-providers` | Provider coverage incomplete                                                 | Complete event/artifact/health/provenance/memory/runtime truth/telemetry/policy evidence providers | `pnpm check:data-cloud-platform-providers`         |
| P5-02   | `scripts/check-kernel-provider-mode.mjs`           | Need stronger bootstrap/platform separation proof                            | Add test that Data Cloud can be built/deployed in bootstrap mode without Data Cloud providers      | `pnpm check:kernel-provider-mode`                  |
| P5-03   | `config/canonical-product-registry.json`           | Data Cloud lifecycle disabled correctly, but reason codes must stay explicit | Keep disabled until bootstrap/platform proof exists; fail if accidentally enabled                  | `pnpm check:product-registry && pnpm check:phase5` |
| P5-04   | Data Cloud runtime truth docs/tests                | Runtime truth provider partial                                               | Add durable tenant-scoped runtime truth tests and query proof                                      | `pnpm check:data-access-contract`                  |

## Phase 6 — Artifact intelligence integration

| TODO ID | File(s)                                                                  | Current issue                                                       | Required change                                                                                | Validation                                        |
| ------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------- |
| P6-01   | `platform/typescript/kernel-product-contracts/src/artifact-intelligence` | Semantic artifact contracts exist but persistence/reporting partial | Finalize `SemanticArtifactReference`, `ArtifactGraphSummary`, residual island and risk reports | `pnpm check:yappc-artifact-intelligence-boundary` |
| P6-02   | `products/yappc/**artifact*`, `products/yappc/kernel-bridge`             | Need reference-only handoff to Kernel                               | Ensure Kernel consumes only shared semantic refs/evidence, never YAPPC internals               | `pnpm check:yappc-artifact-intelligence-boundary` |
| P6-03   | Data Cloud graph/provenance modules                                      | Semantic graph storage partial                                      | Store artifact graph, provenance, and memory refs in Data Cloud                                | `pnpm check:data-cloud-platform-providers`        |
| P6-04   | Studio artifact/risk pages                                               | Visualization partial                                               | Add residual island/risk hotspot panels with evidence refs                                     | `pnpm check:phase6`                               |

## Phase 7 — Future product shape readiness

| TODO ID | File(s)                                  | Current issue                                                        | Required change                                                                                      | Validation                                                                                    |
| ------- | ---------------------------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| P7-01   | `config/canonical-product-registry.json` | Product shapes are modeled but not all readiness gaps are executable | Ensure PHR, Finance, FlashIt, Data Cloud, YAPPC each has explicit blocker/gate/adapter matrix        | `pnpm check:product-shape-capability-matrix`                                                  |
| P7-02   | `products/phr/kernel-product.yaml`       | PHR requires regulated gates                                         | Add/validate consent, PII, audit, FHIR, sovereignty gates before enabling execution                  | `pnpm check:product-registry`                                                                 |
| P7-03   | `products/flashit/kernel-product.yaml`   | Mobile lifecycle requires unready adapters/artifacts                 | Keep execution disabled until xcode-ios/gradle-android and IPA/AAB manifests are real                | `pnpm check:flashit-client-conformance`                                                       |
| P7-04   | Finance lifecycle configs                | Finance validates compliance-heavy shape                             | Add shape proof for backend-heavy, operator/portal/SDK, compliance gates without premature execution | `pnpm check:finance-transaction-workflow-proof && pnpm check:product-shape-capability-matrix` |

## Phase 8 — Production hardening, CI/CD, docs, cleanup

| TODO ID | File(s)                                                              | Current issue                                          | Required change                                                                                     | Validation                                                        |
| ------- | -------------------------------------------------------------------- | ------------------------------------------------------ | --------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------- |
| P8-01   | `scripts/check-production-stubs.mjs`, allowlist                      | Production-stub warning backlog remains                | Burn down warnings; keep zero critical violations                                                   | `pnpm check:production-stubs`                                     |
| P8-02   | `platform/typescript/LIBRARY_GOVERNANCE.md`, deprecated packages     | Deprecated aliases/split canvas packages still flagged | Delete deprecated facades or block them fully with fix-forward migration                            | `pnpm check:deprecated-packages && pnpm check:deprecated-imports` |
| P8-03   | `.kernel/evidence/**`, evidence generation scripts                   | Local absolute paths reduce CI portability             | Emit portable evidence refs and publish artifact bundles                                            | `pnpm check:phase8`                                               |
| P8-04   | Docs under `docs/**`, `platform/kernel-todo.md`                      | Multiple status/truth surfaces can drift               | Make one authoritative status source and reference it everywhere else                               | `pnpm check:doc-truth && pnpm check:doc-claims-evidence`          |
| P8-05   | `scripts/check-cleanup-gate.mjs`, `scripts/check-orphan-modules.mjs` | Cleanup needs permanent enforcement                    | Fail on orphan modules, stale generated artifacts, deprecated package names, and folder-only shells | `pnpm check:cleanup-gate && pnpm check:orphan-modules`            |

---

# H. Validation command suite

Baseline:

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

Phase suite already present in the repo:

```bash
pnpm check:phase0
pnpm check:phase1
pnpm check:phase2
pnpm check:phase3
pnpm check:phase4
pnpm check:phase5
pnpm check:phase6
pnpm check:phase7
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

High-priority targeted commands:

```bash
pnpm check:domain-registry
pnpm check:domain-boundaries
pnpm check:architecture-boundaries
pnpm check:current-state-claims
pnpm check:doc-claims-evidence
pnpm check:kernel-provider-mode
pnpm check:data-cloud-platform-providers
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:agentic-lifecycle-action-contracts
pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing
pnpm check:production-stubs
pnpm check:deprecated-packages
pnpm check:deprecated-imports
pnpm check:cleanup-gate
pnpm check:orphan-modules
```

---

# Top 10 fixes

1. Convert tracker/docs from release/workstream language to phase/journey/dimension language.
2. Normalize Digital Marketing lifecycle evidence paths from absolute local paths to portable evidence refs.
3. Add rollback smoke proof and rollback manifest evidence for Digital Marketing.
4. Complete Data Cloud platform-mode provider coverage.
5. Add YAPPC → Kernel ProductUnitIntent E2E proof.
6. Wire AgentLifecycleActionService through AEP/Data Cloud runtime proof.
7. Complete artifact/provenance run linkage.
8. Complete release/promotion/rollback contract proof.
9. Finish semantic artifact graph/residual island/risk hotspot flow.
10. Burn down production-stub warnings and deprecated package aliases.

# Top 10 cleanup/removal actions

1. Remove or phase-rewrite “Release” status headings in active implementation docs.
2. Delete or block deprecated split canvas packages and aliases.
3. Remove stale target-state claims that are not evidence-backed.
4. Remove absolute local evidence paths from committed evidence packs.
5. Remove or quarantine legacy docs that duplicate canonical Kernel/product truth.
6. Remove product-specific assumptions from platform-level checks.
7. Remove any placeholder/stub adapter paths that can fake success.
8. Remove disabled tests without issue references.
9. Remove folder-only shell packages.
10. Remove duplicate UI/status/error patterns once shared Studio/design-system patterns exist.
