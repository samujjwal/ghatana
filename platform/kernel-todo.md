## Execution note

## Implementation progress (2026-05-17)

- [x] P1-02 API path normalization: Studio ProductUnitIntent client now targets /api/v1/kernel/lifecycle/product-unit-intents for both preview and apply.
- [x] P5-02 platform-mode defaults hardening: YAPPC ProductUnitIntent route removed implicit Data Cloud defaults (localhost/default-*), and now fails closed when explicit Data Cloud scope config is missing.
- [x] P5-02 tests updated: product-unit-intents route tests now set explicit Data Cloud scope env vars and cover missing-scope rejection.
- [x] P2-01/P8-02 CI toolchain drift remediation (lifecycle workflow set): migrated product lifecycle build/validate/package/deploy-local/release workflows to Node 22 + pnpm 10.33.0 with frozen lockfile installs.
- [x] P0-01 registry metadata hygiene: removed stale PHR readiness reference to virtual-org and pointed evidence refs to PHR-owned artifacts.
- [x] Group 1 TS worker request boundary: extractor worker now accepts canonical request shape only.
- [x] Group 1 TS worker output fidelity: worker nodes now emit required extractor/provenance/source fields; semantic model payload expanded with richer fields.
- [x] Group 1 Java TS routing: ProcessTsExtractorWorker now sends only routed TS/JS files to the worker payload.
- [x] Group 3 provenance normalization: Java semantic model mapping now normalizes provenance to canonical values.
- [x] Group 3 cache isolation: ArtifactGraphService cache key now includes tenant + workspace + project.
- [x] Group 3 consistency improvement: compile flow now ingests graph before persisting semantic models, preventing orphan semantic rows when ingest fails.
- [x] Group 2 snapshot schema alignment started: added forward migration V23__align_repository_snapshot_schema.sql to align legacy and canonical snapshot columns.
- [x] Group 2 deep migration verification: added RepositorySnapshotMigrationShapeTest covering V15/V17/V21/V23 chain and canonical backfill assertions.
- [x] Group 5 Data Cloud endpoint-backed coverage expanded: added provider tests for health, approvals, provenance, memory queries, policy evidence, and telemetry metric/event paths.
- [x] Group 4 agentic lifecycle E2E proof strengthened: guard now enforces service-level tests for governed execution, raw-command rejection, and provider writes (provenance/runtime truth/memory).
- [x] Group 6 artifact-intelligence E2E proof strengthened: boundary guard now enforces Data Cloud evidence persistence tests plus import/synthesis residual proof coverage.
- [x] Phase-level validation expanded: `check:phase4` and `check:phase6` now pass after proof-guard and provider coverage hardening.
- [x] Full readiness validation completed: `check:world-class-platform-readiness` passed after regenerating stale product-registry artifacts via `scripts/generate-product-registry-artifacts.mjs`.

- [x] Focused TS validations completed:
	- yappc-artifact-compiler worker contract test passed.
	- YAPPC ProductUnitIntent route test suite passed.
	- Studio kernelLifecycleClient test suite passed.
- [x] Java validation (targeted) completed: `RepositorySnapshotMigrationShapeTest` now passes via Gradle scoped test run.
- [x] Pending deep migration verification: add or extend migration-shape tests for upgraded V15/V17/V21 -> V23 paths.
- [x] Pending completeness tasks: artifact intelligence E2E and agentic lifecycle E2E proofs now have enforced guard coverage and passing focused test runs; Data Cloud endpoint-backed provider tests pass after catalog fix (`yaml` added to workspace catalog) and dependency sync.

I executed the audit as a **static source audit** against `samujjwal/ghatana` at commit `c5d4066edc67e4f1bc37610fef594b12e159aada`. I could access the commit and repo through the GitHub connector, but I could not run local build/test commands because the execution container could not resolve `github.com` for cloning. The validation commands below are therefore the required next run, not commands I successfully executed locally.

The target commit is reachable and primarily changes Digital Marketing lifecycle CI/evidence behavior, including repo-relative manifest refs and a Digital Marketing CI workflow. It also removes legacy canvas facade test jobs. 

---

# Executive summary

**Current verdict:** Ghatana is structurally moving in the right direction, but the platform is not yet world-class end-to-end. The **Digital Marketing lifecycle pilot** is the strongest executable path. The **Kernel contracts/lifecycle foundation** are strong. The **Studio shell, Data Cloud platform-mode providers, AEP/agentic development, artifact intelligence E2E, and future product readiness** remain partial.

**Closest to production-grade:**

1. **Digital Marketing pilot** is the most concrete executable lifecycle path: registry marks it as enabled, pilot=true, lifecycleExecutionAllowed=true, and ready; its `kernel-product.yaml` defines required manifests, phases, gates, policies, approvals, provider modes, package/deploy/verify config; compose, env example, and secret gitignore are present.      
2. **Kernel ProductUnit/ProductUnitIntent contracts** are concrete and schema-backed, exporting ProductUnit, ProductUnitIntent, provider, event, health, plugin, and artifact-intelligence contracts. 
3. **Kernel lifecycle service** has real planning, execution, event writing, runtime truth, provenance, approval, provider-mode validation, and manifest-pointer behavior.   
4. **Phase-based validation scripts already exist** in `package.json`, including phase 0–8 checks and `check:world-class-platform-readiness`. 

**Highest-risk gaps:**

1. **Digital Marketing CI runtime drift:** the new workflow uses Node 20 and installs `pnpm@9`, while root `package.json` requires Node `>=22.0.0`, pnpm `>=10.0.0`, and `packageManager: pnpm@10.33.0`. This can make CI validate a different toolchain than the repo contract.  
2. **Studio/YAPPC/Kernel API path drift:** Studio posts ProductUnitIntent to `/api/kernel/lifecycle/product-unit-intents`, while the YAPPC API route forwards to `/api/v1/kernel/lifecycle/product-unit-intents`. Unless an alias/gateway exists, this is an integration inconsistency.  
3. **Data Cloud platform-mode provider work is still partial:** domain registry explicitly classifies Data Cloud runtime truth as `existing-partial` with runtime-truth/provider-mode gaps, even though bridge provider classes exist.   
4. **YAPPC platform-mode route has unsafe/default-ish runtime fallbacks** such as `http://localhost:8080`, `default-tenant`, `default-workspace`, and `default-project`; those should be dev-only or validated configuration, not implicit platform-mode assumptions. 
5. **Future products must remain disabled until platform gaps are closed:** PHR, FlashIt, Data Cloud, YAPPC, TutorPutor, Audio-Video, DCMAAR, and Aura are not ready for ordinary lifecycle execution.   

---

# Goal and Status Register

| Goal ID | Goal                                         |                         Correct owner | Current status                            | Evidence                                                                                                                       | Gap                                                                                          | Phase |
| ------- | -------------------------------------------- | ------------------------------------: | ----------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------- | ----: |
| G01     | Platform coherence/governance                |                    Platform Coherence | Existing but partial                      | Domain registry marks governance partial with current-state and boundary gaps.                                                 | Manual review still needed for current-state claims and boundaries.                          |  0, 8 |
| G02     | Unified Ghatana Studio shell                 |                     Platform / Studio | Existing but partial                      | Navigation model exists with ownership/status/exposure.                                                                        | Many routes are degraded/disabled/hidden pending lifecycle/Data Cloud evidence.              |     1 |
| G03     | Product Development Kernel lifecycle truth   |                                Kernel | Existing and executable, hardening needed | Lifecycle service plans, executes, writes truth/provenance/events.                                                             | Artifact provenance, deployment, and release are still partial by domain registry.           |     3 |
| G04     | ProductUnit / ProductUnitIntent contracts    |                      Kernel contracts | Existing and executable                   | Contracts exported with schemas/providers/events/artifact intelligence.                                                        | API path/version alignment needed across Studio/YAPPC/Kernel.                                |  1, 3 |
| G05     | Digital Marketing lifecycle pilot            |                      Product + Kernel | Existing and executable                   | Registry enabled, pilot ready, lifecycleExecutionAllowed true.                                                                 | CI runtime drift; compose proof should be enforced in a dedicated environment.               |     2 |
| G06     | Data Cloud bootstrap/platform provider model |                   Data Cloud + Kernel | Existing but partial                      | Bridge providers exist; registry/domain classify provider mode partial.                                                        | Need endpoint-backed contract/integration tests and provider health proof.                   |     5 |
| G07     | Agentic product development                  |               AEP/Data Cloud + Kernel | Existing but partial                      | Agent runtime governance is partial in domain registry.                                                                        | Need E2E proof that agents invoke Kernel contracts, not raw tools.                           |     4 |
| G08     | YAPPC ProductUnitIntent handoff              |                        YAPPC + Kernel | Existing but partial                      | Route validates ProductUnitIntent, evidence, scope, platform mode, permissions.                                                | Runtime config defaults and API path mismatch must be resolved.                              |  1, 6 |
| G09     | Artifact intelligence                        | YAPPC + Data Cloud + Kernel contracts | Existing but partial                      | Strong schemas exist for evidence, confidence, provenance, privacy, retention.                                                 | Need full compiler → Data Cloud → Kernel planning/gates → Studio visualization E2E.          |     6 |
| G10     | Shared UI/design/canvas/builder              |                              Platform | Existing but partial                      | Domain registry marks canvas, UI builder, design registry, Studio partial.                                                     | Deprecated canvas package cleanup and product UI reuse gates remain.                         |  1, 8 |
| G11     | Future product shape readiness               |                    Product + Platform | Existing but partial / planned            | PHR and FlashIt are planned/disabled; Data Cloud and YAPPC are platform-provider disabled; TutorPutor partial but disabled.    | Keep disabled; close platform capability gaps first.                                         |     7 |
| G12     | CI/CD and production hardening               |                    Platform Coherence | Existing but partial                      | Phase checks exist in root scripts.                                                                                            | New CI workflow uses conflicting Node/pnpm versions; local validation not yet executed here. |     8 |

---

# Journey findings

## Journey 1 — Product ideation to ProductUnitIntent

**Status:** Existing but partial.

YAPPC has a schema-backed route that validates ProductUnitIntent, scope, evidence bundles, platform-mode Data Cloud evidence, and apply permissions. It blocks apply without explicit permission and blocks platform mode without Data Cloud evidence. 

**Key gaps:**

* Align ProductUnitIntent API path/version across Studio and YAPPC.
* Remove or dev-scope Data Cloud fallback defaults in platform mode.
* Add a real UI journey test from Ideas/Blueprint/Canvas → ProductUnitIntent preview/apply → Kernel result.

## Journey 2 — Direct Product Development Kernel usage

**Status:** Existing and executable, but hardening remains.

Kernel can list/get ProductUnits, create plans, execute plans, read run summaries/manifests, handle approvals, validate provider context, write runtime truth, append events, and record provenance.  

**Key gaps:**

* Add end-to-end tests through Studio using the typed lifecycle client.
* Complete artifact/deployment/release hardening called out as partial in domain registry.
* Validate all API errors consistently with scoped auth/authz headers.

## Journey 3 — Agentic product development

**Status:** Existing but partial.

The domain registry explicitly marks AEP/agent runtime governance as partial with agent-governance and central-registry gaps. 

**Key gaps:**

* Add test proof that agents call `AgentLifecycleActionRequest` / Kernel lifecycle tool catalog instead of raw Gradle/pnpm/Docker.
* Persist agent action evidence to Data Cloud provenance/memory/runtime truth.
* Surface pending approval, risk, verification proof, and rollback/fallback in Studio.

## Journey 4 — Digital Marketing lifecycle pilot

**Status:** Existing and executable; strongest path.

Digital Marketing is the only enabled lifecycle pilot in the registry. Its lifecycle config defines manifests, plugins, policy packs, phases, gates, package adapters, deployment, approvals, provider modes, retention, and verify checks.   

**Key gaps:**

* Fix CI runtime drift: Node 20/pnpm 9 conflicts with root Node 22/pnpm 10.33.
* Enforce compose proof in a controlled CI job.
* Add Studio-level E2E for selecting Digital Marketing, running lifecycle phases, and reading manifests/health.

## Journey 5 — Artifact intelligence

**Status:** Existing but partial.

Artifact intelligence contracts are strong: they include evidence ID, tenant/workspace/project/productUnit, confidence, provenance refs, privacy classification, retention, graph evidence, residual island reports, risk hotspots, and generated changesets. 

**Key gaps:**

* Prove actual YAPPC compiler/decompiler output conforms to these contracts.
* Persist outputs to Data Cloud graph/provenance/memory.
* Ensure Kernel consumes only semantic references and never imports YAPPC internals.

## Journey 6 — Data Cloud foundation

**Status:** Existing but partial.

Data Cloud-backed providers exist for events, artifacts, health, approvals, provenance, memory, runtime truth, policy evidence, and telemetry, but the domain registry still classifies runtime truth/provider mode as partial.   

**Key gaps:**

* Add contract tests against actual Data Cloud provider endpoints.
* Prove Kernel bootstrap mode can build/deploy Data Cloud without Data Cloud providers.
* Prove platform mode switches to Data Cloud providers after Data Cloud is available.

## Journey 7 — Future product shape readiness

**Status:** Planned/partial; do not enable yet.

PHR requires consent, PII classification, audit evidence, FHIR validation, and data sovereignty. FlashIt requires mobile adapters, preview security, personal-data classification, and IPA/AAB artifact manifests. Data Cloud and YAPPC are platform-provider products and must not be ordinary lifecycle products until bootstrap/provider constraints are solved.  

---

# Phase-based TODO plan

| TODO ID | Phase | Domain                    | Journey       | File(s)                                                                                                                                              | Required change                                                                                                                                         | Validation                                                                                                       |
| ------- | ----: | ------------------------- | ------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| P0-01   |     0 | Platform Coherence        | Baseline      | `config/canonical-product-registry.json`                                                                                                             | Fix stale/cross-product readiness text in PHR metadata that references virtual-org evidence/work. This creates current-state confusion.                 | `pnpm check:product-registry && pnpm check:current-state-claims`                                                 |
| P0-02   |     0 | Platform Coherence        | All           | `config/domain-registry.json`, `scripts/check-current-state-claims.mjs`, docs scope files                                                            | Convert remaining “manual review” boundary/current-state gaps into executable checks where possible.                                                    | `pnpm check:phase0`                                                                                              |
| P1-01   |     1 | Studio Shell              | Journey 1/2/4 | `platform/typescript/ghatana-studio/src/navigation/studioNavigation.ts`                                                                              | Add/verify tests that route exposure, disabled/hidden/preview states, label keys, and ownership match lifecycle/provider state.                         | `pnpm check:shared-product-shells && pnpm check:studio-kernel-api`                                               |
| P1-02   |     1 | API Contracts             | Journey 1     | `platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts`, `products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts`         | Standardize ProductUnitIntent endpoint versioning: either `/api/v1/kernel/...` everywhere or an explicit gateway alias with contract tests.             | `pnpm check:studio-kernel-api && pnpm check:yappc-product-unit-intent-handoff`                                   |
| P2-01   |     2 | Digital Marketing Pilot   | Journey 4     | Digital Marketing CI workflow changed by commit                                                                                                      | Align CI with root engine contract: Node 22 and pnpm 10.33 via corepack/packageManager. Do not install pnpm 9.                                          | `pnpm check:phase2`                                                                                              |
| P2-02   |     2 | Digital Marketing Pilot   | Journey 4     | `scripts/check-digital-marketing-lifecycle-pilot.mjs`                                                                                                | Keep repo-relative manifest ref validation and add regression test for absolute path leakage in evidence packs.                                         | `pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing` |
| P2-03   |     2 | Digital Marketing Pilot   | Journey 4     | `products/digital-marketing/kernel-product.yaml`, `products/digital-marketing/deploy/*`                                                              | Add a separate compose-proof CI job with env provisioning; keep normal smoke path fast and deterministic.                                               | `pnpm check:digital-marketing-lifecycle-pilot -- --compose-proof`                                                |
| P3-01   |     3 | Kernel Lifecycle          | Journey 2     | `platform/typescript/kernel-artifacts`, `platform/typescript/kernel-providers/src/provenance`                                                        | Complete artifact provenance and supply-chain tracking; domain registry marks this partial.                                                             | `pnpm check:product-artifact-contracts`                                                                          |
| P3-02   |     3 | Kernel Deployment/Release | Journey 2/4   | `platform/typescript/kernel-deployment`, `platform/typescript/kernel-release`                                                                        | Complete deployment, environment, release, promotion, and rollback contracts; domain registry marks this partial.                                       | `pnpm check:product-deployment-contracts`                                                                        |
| P4-01   |     4 | AEP / Agents              | Journey 3     | `products/data-cloud/planes/action/server`, `platform/java/agent-core`, Kernel action contracts                                                      | Add E2E proof that agents use Kernel lifecycle action contracts and cannot run raw lifecycle commands directly.                                         | `pnpm check:agentic-lifecycle-action-contracts && ./gradlew :platform:java:agent-core:check`                     |
| P5-01   |     5 | Data Cloud Providers      | Journey 6     | `products/data-cloud/libs/kernel-bridge-providers/src/index.ts`                                                                                      | Add endpoint-backed contract tests for all provider paths: events, artifacts, health, approvals, provenance, memory, runtime truth, telemetry.          | `pnpm --dir products/data-cloud/libs/kernel-bridge-providers test && pnpm check:data-cloud-platform-providers`   |
| P5-02   |     5 | Data Cloud / YAPPC        | Journey 1/6   | `products/yappc/frontend/apps/api/src/routes/product-unit-intents.ts`                                                                                | Remove implicit platform-mode defaults or guard them behind dev-only config validation. Fail closed when tenant/workspace/project/base URL are missing. | `pnpm check:yappc-product-unit-intent-handoff && pnpm check:secret-default-credentials`                          |
| P6-01   |     6 | Artifact Intelligence     | Journey 5     | YAPPC compiler/decompiler packages, `platform/typescript/kernel-product-contracts/src/artifact-intelligence`, Data Cloud memory/provenance providers | Add E2E: scan/decompile → schema-backed evidence → Data Cloud persistence → Kernel consumes references → Studio displays risks/residual islands.        | `pnpm check:yappc-artifact-intelligence-boundary && pnpm check:yappc-product-unit-intent-handoff`                |
| P7-01   |     7 | Product Shape Matrix      | Journey 7     | `config/canonical-product-registry.json`, product `kernel-product.yaml` files                                                                        | Keep PHR/Finance/FlashIt/Data Cloud/YAPPC disabled until gates/adapters/artifacts are real; add blocker matrix tests per product shape.                 | `pnpm check:product-shape-capability-matrix && pnpm check:product-registry-drift`                                |
| P8-01   |     8 | Cleanup                   | All           | CI workflows, `platform/typescript/canvas/*`, package governance scripts                                                                             | Finish deprecated canvas facade cleanup. Removing tests is not enough if packages/imports remain.                                                       | `pnpm check:deprecated-packages && pnpm check:deprecated-imports && pnpm check:platform-package-governance`      |
| P8-02   |     8 | CI/CD                     | All           | `package.json`, workflows                                                                                                                            | Ensure every phase check is wired into CI with the same Node/pnpm/Java versions declared by the repo.                                                   | `pnpm check:world-class-platform-readiness && ./gradlew check`                                                   |

---

# Audit dimension status

| Dimension                       | Status               | Summary                                                                                                       |
| ------------------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------- |
| Architecture and ownership      | Existing but partial | Domain registry is strong, but several domains still list manual boundary/current-state gaps.                 |
| UI/UX                           | Existing but partial | Studio navigation and route exposure exist, but multiple customer-visible areas are disabled/degraded/hidden. |
| API contracts                   | Existing but partial | Good Zod schemas and typed client; API version/path mismatch needs correction.                                |
| Backend/storage                 | Existing but partial | Kernel lifecycle is strong; Data Cloud providers are present but still classified partial.                    |
| AI/ML-native behavior           | Existing but partial | Artifact evidence schemas support confidence/provenance/privacy; full E2E flow is missing.                    |
| O11y/security/privacy/i18n/a11y | Existing but partial | Many checks and policy packs exist; route defaults and CI/tooling drift need hardening.                       |
| Testing and CI/CD               | Existing but partial | Large command suite exists; target commit introduces Digital Marketing CI drift against root engine versions. |
| Cleanup/consolidation           | Existing but partial | Deprecated canvas facade test removal is positive, but package/import cleanup still needs validation.         |
| Current-state discipline        | Existing but partial | Registries classify status, but stale/cross-product text still appears in product metadata.                   |

---

# Required validation suite

Run with **Node 22**, **pnpm 10.33.0**, and **Java 21**:

```bash
pnpm install --frozen-lockfile

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

pnpm build:kernel-lifecycle-platform
pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing
pnpm check:digital-marketing-lifecycle-pilot -- --compose-proof

pnpm build
pnpm test
pnpm typecheck

./gradlew build
./gradlew check
```

---

# Final assessment

The correct next focus is **not** to expand more products yet. The next highest-leverage path is:

1. Fix CI/toolchain drift.
2. Lock Digital Marketing as the single green lifecycle pilot.
3. Normalize Studio/YAPPC/Kernel API paths.
4. Harden Data Cloud provider-mode contract tests.
5. Add one true artifact-intelligence E2E.
6. Keep all future product shapes disabled until the matrix gates are real and executable.
