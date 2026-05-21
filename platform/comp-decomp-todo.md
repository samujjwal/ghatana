# Development-First Kernel Platform + PHR + Digital Marketing Deep Implementation Plan

**Repository:** `samujjwal/ghatana`  
**Target commit snapshot:** `9d4a677a90cfe4a4a514361e78af83abf3f3e888`  
**Audit mode:** full codebase snapshot review, not commit-diff-only  
**Planning posture:** development-first, implementation-oriented, prescriptive, evidence-backed, no fake validation

---

## 1. Title and Target Commit

This plan executes the development-first Product Development Kernel prompt against commit `9d4a677a90cfe4a4a514361e78af83abf3f3e888`.

The target commit materially changes the current planning baseline in three ways:

1. It refreshes lifecycle evidence for both enabled pilots, PHR and Digital Marketing, under `.kernel/evidence/**`.
2. It strengthens repo-level validation scripts, including `check:full-repo-test`, artifact round-trip checks, Studio artifact workflow E2E, and world-class phase gates.
3. It adds/strengthens Data Cloud launcher boundary and route-manifest/runtime-truth drift checks, making Data Cloud platform-provider boundaries a first-class dependency of the Kernel plan.

This plan expands the previous Kernel/PHR/Digital Marketing implementation roadmap with deeper focus on real Kernel lifecycle execution, pilot product feature completeness, Data Cloud route/runtime-truth and Action Plane boundary discipline, Studio artifact compiler/decompiler workflow, artifact contracts, source acquisition, round-trip fidelity, repository-scan evidence, evidence freshness, and no-regression gates.

---

## 2. Current-State Summary

### 2.1 Repo and governance

**Status: existing and executable, with expanding gate coverage.**

The root `package.json` declares Node `>=22`, pnpm `>=10`, and a large set of product/platform checks. The target snapshot adds `check:full-repo-test`, which chains TypeScript tests, Gradle checks, Digital Marketing lifecycle smoke, and PHR lifecycle smoke. It also keeps broad phase gates and world-class readiness checks.

**Current executable anchors**

- `pnpm check:product-registry`
- `pnpm check:architecture-boundaries`
- `pnpm check:kernel-product-boundary-audit`
- `pnpm check:phase0` through `pnpm check:phase8`
- `pnpm check:world-class-platform-readiness`
- `pnpm check:full-repo-test`

**Current gap:** the repo has many valuable gates, but the operational taxonomy is not explicit enough. Some gates are development-time, some are release-time, some are product-specific, and some are platform-provider-specific. The next implementation step is to classify commands by owner, cost, scope, cadence, and evidence output.

### 2.2 Product registry

**Status: existing and executable.**

PHR and Digital Marketing are both enabled business-product lifecycle pilots.

**PHR current facts**

- `productId`: `phr`
- `kind`: `business-product`
- surfaces: `backend-api`, `web`
- lifecycle status: `enabled`
- lifecycle execution allowed: true
- required healthcare gates: consent, PII classification, audit evidence, FHIR contract validation, tenant data sovereignty
- rollback readiness: target/partial

**Digital Marketing current facts**

- `productId`: `digital-marketing`
- `kind`: `business-product`
- surfaces: `backend-api`, `web`
- lifecycle status: `enabled`
- lifecycle execution allowed: true
- bridge conformance: true
- adapter set: `gradle-java-service`, `pnpm-vite-react`
- deployment target: `compose-local`
- supports validate/test/build/package/deploy/verify/promote/rollback flows

### 2.3 Gradle and pnpm workspace wiring

**Status: existing and executable.**

The root Gradle settings include platform Java modules, `platform-kernel`, platform plugins, product modules generated from the canonical registry, PHR healthcare domain, Data Cloud/YAPPC kernel bridges, shared services, and integration tests. The pnpm workspace is generated from the canonical registry and includes platform TypeScript packages, PHR web, Digital Marketing UI, Data Cloud UI/libs/action packages, YAPPC frontend/tool packages, FlashIt, Audio-Video, DCMAAR, Tutorputor, and shared service UIs.

**Gap:** generated workspace and Gradle includes are strong, but product lifecycle enablement must never silently rely on modules that are not wired into Gradle or pnpm.

### 2.4 Kernel contracts

**Status: existing but partial-to-strong.**

`@ghatana/kernel-product-contracts` exports ProductUnit, ProductUnitIntent, provider, event, health, provenance, runtime-truth, plugin, artifact-intelligence, agentic lifecycle, lifecycle plan/result, gate, artifact, deployment, rollback, and UI lifecycle summary contracts.

**Gap:** the contracts are broad, but the next step is to prove all of them through executable lifecycle behavior, not just exported types. Required hardening includes schema-level validation for every emitted manifest, versioned compatibility checks for lifecycle/gate/artifact/deployment/verify/rollback manifests, and contract tests for incomplete providers, missing approvals, partial artifacts, stale runtime truth, and blocked environments.

### 2.5 Kernel lifecycle engine

**Status: existing and executable, but needs deeper failure semantics.**

The CLI and lifecycle planner/executor support planning, execution, surfaces, environments, provider modes, approval safety, adapter compliance, dry-run gates, manifest pointer collection, and explain mode.

**Gap:** lifecycle phases can run, but phase graph dependencies, retries, resumability, durable run history, platform provider mode, and failure taxonomy still need hardening.

### 2.6 Kernel providers

**Status: existing but partial.**

Bootstrap file-backed providers exist for registry, lifecycle events, artifacts, health, approvals, provenance, memory, runtime truth, and gates. Data Cloud-backed providers also exist in the generic provider package.

**Critical boundary issue:** Data Cloud-backed provider implementations under generic `platform/typescript/kernel-providers` are potentially risky if they embed Data Cloud endpoint assumptions. The blueprint says Data Cloud-backed providers that depend on Data Cloud internals belong under `products/data-cloud/extensions/kernel-bridge`. The generic platform provider package may define contracts and thin HTTP clients, but must not import Data Cloud plane internals or encode product-specific behavior.

### 2.7 Kernel toolchains

**Status: existing and executable for current pilots.**

The default registry registers Gradle Java service, pnpm/Vite React, Docker Buildx, and Compose local adapters.

**Gap:** output validation, failure classification, structured logs, environment preflight, and retry semantics need hardening.

### 2.8 Artifact compiler/decompiler and Studio artifact workflow

**Status: existing but partial-to-strong.**

The target snapshot has platform packages for `@ghatana/artifact-contracts`, `@ghatana/artifact-compiler-ts`, and `@ghatana/ghatana-studio`. `artifact-compiler-ts` exports TSX decompile, React compile, Builder projection, Canvas projection, DS projection, fidelity scoring, residual island detection, round-trip diff, and repository scan. Studio depends on artifact contracts/compiler, canvas, design-system, DS generator/registry/schema, Kernel lifecycle/artifact/deployment/release/contracts, i18n, platform-events, product-shell, and UI builder.

**Boundary interpretation:** this is a platform primitive pipeline, not the full YAPPC artifact-intelligence product workflow. Platform can own generic artifact contracts, compiler/decompiler primitives, fidelity scoring, residual detection, and Studio import/decompile UX. YAPPC still owns higher-order creator workflows, semantic product modeling, artifact graph intelligence, residual island review workflows, learning, and generated ProductUnitIntent handoff.

**Gap:** Studio artifact workflow must graduate from component-level workflow to production-ready source acquisition and repository scanning. Repository and archive acquisition must produce real backend jobs or truthful `pending/not-ready` state, not fake success.

### 2.9 Data Cloud and Action Plane

**Status: existing but partial, now with stronger boundary gates.**

The target commit strengthens Data Cloud with launcher plane boundary tests, route manifest/runtime-truth drift checks, OpenAPI route parity, strict tenant identity semantics, and Action Plane positioning. AEP is now the runtime implementation inside Data Cloud Action Plane, not a standalone product boundary.

**Gap:** these are essential platform-provider foundations, but Kernel platform mode still needs explicit provider negotiation, health checks, and fail-closed behavior. Data Cloud must not become a product-specific lifecycle runner.

### 2.10 Digital Marketing

**Status: existing and executable lifecycle pilot; product still needs deeper feature completion.**

Digital Marketing has enabled lifecycle config, policy packs, plugin bindings, backend/web surfaces, deploy/promote/rollback config, and refreshed lifecycle evidence.

**Gap:** lifecycle evidence proves platform smoke execution. It does not prove complete Digital Marketing product behavior. Required development remains customer/account management, campaign lifecycle, lead/conversion tracking, audience segmentation, channel configuration, Google Ads connector readiness, reporting dashboards, notification retry/DLQ, operator/admin workflows, API contracts, and UI route completeness.

### 2.11 PHR

**Status: existing and executable lifecycle pilot with regulated gates; product still needs deeper feature completion.**

PHR has enabled lifecycle config, healthcare gates, backend/web surfaces, Docker package config, compose local deployment, verify health report fields, and refreshed lifecycle evidence.

**Gap:** PHR lifecycle evidence does not yet prove complete healthcare behavior. Required development remains patient profile, record summary, encounters, medications, allergies, conditions, labs, immunizations, documents, care team, consent, sharing authorization, audit access history, FHIR R4 validation, PII classification, tenant data sovereignty evidence, role-based privacy controls, and rollback enablement.

---

## 3. Development-First Principles

1. Build real capability first; validate only to prove the capability.
2. Keep Kernel product-neutral; products configure lifecycle, gates, artifacts, deployments, and policies.
3. Keep Product Development Kernel separate from YAPPC creator lifecycle and Data Cloud plane internals.
4. Keep Data Cloud as runtime truth/provider foundation, not a product-local workflow owner.
5. Treat PHR and Digital Marketing as active first-release pilots.
6. Preserve current-state versus target-state discipline.
7. Every lifecycle phase must produce truth: plan, result, events, health, gates, artifacts, deployments, rollback evidence where applicable.
8. Every critical behavior must have logs, metrics, traces or structured evidence, correlation IDs, and failure reason codes.
9. No fake success: adapters, connectors, providers, source acquisition, and gates must report `blocked`, `not-ready`, `environment-blocked`, or `dependency-blocked` rather than succeeding without work.
10. Tests must invoke production code and cover meaningful behavior.

---

## 4. Kernel Platform Development Plan

### 4.1 ProductUnit and ProductUnitIntent contracts

| Field | Plan |
|---|---|
| Current state | Broad contract exports exist. Product registry and lifecycle configs provide ProductUnit facts. |
| Gap | Not all ProductUnit dimensions are fully proven by runtime execution and Studio visualization. |
| Target behavior | ProductUnit becomes the canonical product-development runtime object across CLI, Studio, providers, Data Cloud, and YAPPC handoff. |
| Locations | `platform/typescript/kernel-product-contracts/src/product-unit/**`, `src/lifecycle/**`, `src/ui-summary/**`, `config/canonical-product-registry.json`, `products/*/kernel-product.yaml`. |
| Development steps | Add lifecycle profile, product shape, surface, environment, provider mode, plugin binding, gate binding, policy pack, artifact, deployment, release, health, semantic artifact, and agentic action references to a single validated ProductUnit projection. Add ProductUnitIntent apply/preview conformance tests for PHR and Digital Marketing. |
| Tests | Contract parse tests, invalid config tests, ProductUnitIntent preview/apply tests, registry-to-ProductUnit golden tests. |
| Validation | `pnpm check:kernel-product-unit-provider-contracts`, `pnpm check:yappc-product-unit-intent-handoff`, `pnpm check:product-registry`. |
| Done | Studio, CLI, providers, and product manifests resolve the same ProductUnit truth with no duplicate lifecycle enums. |

### 4.2 Lifecycle planner and executor

| Field | Plan |
|---|---|
| Current state | CLI creates plans and executes with adapters; approval safety and adapter contract compliance exist. |
| Gap | Phase graph, retries, resumability, platform provider mode, failure taxonomy, and run history need deeper implementation. |
| Target behavior | Deterministic lifecycle plan graph with explicit dependencies, per-surface/multi-surface execution, replay-safe run IDs, durable run history, and fail-closed provider/gate behavior. |
| Locations | `platform/typescript/kernel-lifecycle/src/planning/**`, `src/execution/**`, `src/service/**`, `scripts/kernel-product.mjs`. |
| Development steps | Add `LifecyclePhaseGraph`, `LifecycleRunHistoryStore`, `LifecycleFailureClassifier`, and `LifecycleResumePolicy`. Expand plan explain output to include dependency graph, provider checks, gate checks, artifact expectations, approval policy, and environment preflight. |
| Tests | Planner graph tests, partial execution tests, provider-blocked tests, approval-blocked tests, adapter failure classification tests, manifest pointer tests. |
| Validation | `pnpm check:kernel-lifecycle-service`, `pnpm check:kernel-platform-lifecycle`, `pnpm check:kernel-lifecycle-truth`. |
| Done | Every failed lifecycle run gives actionable reason code and reproducible evidence path. |

### 4.3 Kernel providers

| Field | Plan |
|---|---|
| Current state | Bootstrap providers exist; Data Cloud-backed providers exist. |
| Gap | Data Cloud provider boundary must be enforced so platform code does not leak Data Cloud plane internals. |
| Target behavior | Bootstrap mode is file-backed and Data Cloud-independent. Platform mode is Data Cloud-backed through approved provider bridge contracts. |
| Locations | `platform/typescript/kernel-providers/**`, `products/data-cloud/extensions/kernel-bridge/**`, `products/data-cloud/planes/action/kernel-bridge/**`. |
| Development steps | Define provider capability negotiation. Move product-specific Data Cloud assumptions behind `products/data-cloud/extensions/kernel-bridge` where needed. Add `KernelProviderHealthMatrix`. Add conformance tests for events, artifacts, approvals, health, provenance, memory, runtime truth, telemetry, environment, and secrets providers. |
| Tests | Provider contract suite; bootstrap-provider tests; Data Cloud bridge conformance tests; platform-mode blocked tests when Data Cloud unavailable. |
| Validation | `pnpm check:kernel-provider-mode`, `pnpm check:data-cloud-platform-providers`, `pnpm check:data-cloud-platform-provider-readiness`. |
| Done | Kernel can run PHR/DMOS in bootstrap mode without Data Cloud and can switch to platform mode only when Data Cloud provider health is real. |

### 4.4 Toolchain adapters

| Field | Plan |
|---|---|
| Current state | Gradle, pnpm/Vite, Docker Buildx, Compose adapters are registered. |
| Gap | Output validation, failure classification, structured logs, environment preflight, and retry semantics need hardening. |
| Target behavior | Every adapter has metadata, preflight, safe command mapping, timeout/retry policy, structured output events, and artifact output verification. |
| Locations | `platform/typescript/kernel-toolchains/src/**`, `config/toolchain-adapter-registry.json`. |
| Development steps | Add `AdapterPreflightResult`, `AdapterCapabilityMetadata`, `AdapterSafetyPolicy`, `AdapterOutputValidator`, and `AdapterFailureClassification`. Enforce no raw command execution outside registered adapters. |
| Tests | Adapter unit tests using fake command runner; integration tests for Gradle/pnpm when available; Docker environment-blocked tests; Compose health-check tests. |
| Validation | `pnpm check:toolchain-adapter-contracts`, `pnpm check:product-artifact-contracts`, `pnpm check:product-deployment-contracts`. |
| Done | Docker/Buildx unavailable is reported as environment-blocked, not success or generic failure. |

### 4.5 Artifact and supply-chain system

| Field | Plan |
|---|---|
| Current state | Kernel artifact manifests exist; artifact compiler/decompiler platform packages exist. |
| Gap | Build artifacts, compiler/decompiler artifacts, semantic artifacts, provenance, and Studio views need a unified trust model. |
| Target behavior | Artifact manifest records source linkage, fingerprint, trust state, lifecycle phase, producing adapter, provenance reference, and deployment linkage. |
| Locations | `platform/typescript/kernel-artifacts/**`, `platform/typescript/artifact-contracts/**`, `platform/typescript/artifact-compiler-ts/**`, `platform/typescript/ghatana-studio/**`. |
| Development steps | Add artifact trust state, source-to-artifact lineage, artifact-to-deployment linkage, round-trip fidelity evidence, residual-island evidence, repository-scan evidence, and SBOM fields only when real generation exists. |
| Tests | Artifact manifest schema tests; round-trip diff tests; fidelity gate tests; repository-scan tests; no fake SBOM tests. |
| Validation | `pnpm check:artifact-roundtrip`, `pnpm check:product-artifact-contracts`, `pnpm check:studio-artifact-workflow-e2e`. |
| Done | Studio can show exact source, produced artifact, fidelity, residual islands, trust state, and lifecycle evidence for PHR/DMOS artifacts. |

### 4.6 Deployment, release, promotion, rollback

| Field | Plan |
|---|---|
| Current state | Digital Marketing has deploy/promote/rollback config; PHR has deploy/verify and rollback target-partial. |
| Gap | PHR rollback is intentionally target-partial; promotion policy needs production-grade approval and risk evidence. |
| Target behavior | Deploy/promote/rollback produce versioned manifests with approval, risk, artifact, deployment, environment, health, and post-action verification evidence. |
| Locations | `platform/typescript/kernel-deployment/**`, `platform/typescript/kernel-release/**`, `products/*/kernel-product.yaml`. |
| Development steps | Add release manifest, promotion manifest, rollback manifest, approval evidence contract, risk classification, rollback impact analysis, previous-artifact selection policy, post-rollback verification gates. |
| Tests | Release/rollback contract tests; approval-blocked tests; post-rollback health tests; PHR rollback-disabled tests. |
| Validation | `pnpm promote:local:digital-marketing`, `pnpm rollback:local:digital-marketing`, `pnpm plan:rollback:local:phr`, `pnpm check:kernel-lifecycle-truth`. |
| Done | Digital Marketing rollback works with real evidence; PHR rollback stays blocked until healthcare rollback conditions are implemented. |

---

## 5. Kernel Infrastructure Development Plan

### 5.1 Governance and command taxonomy

Create `docs/kernel/CHECK_COMMAND_TAXONOMY.md` and classify each command by owner domain, cost, scope, execution cadence, required evidence output, and failure interpretation.

### 5.2 Registry generation and drift

Harden `config/canonical-product-registry.json`, `config/domain-registry.json`, `config/generated/settings-gradle-includes.kts`, `pnpm-workspace.yaml`, product manifests, and product lifecycle configs. Add drift checks for registry-to-Gradle, registry-to-pnpm, registry-to-ProductUnit, lifecycle config to evidence pack, and route contract to UI manifest.

### 5.3 Data Cloud route runtime truth

Promote Data Cloud route manifest generation to platform-provider release criteria. The source is `RouteSecurityRegistry.java`; generated route manifest and UI runtime truth must be committed; route sensitivity/auth/tenant/policy metadata must be non-stale.

Validation:

```bash
cd products/data-cloud
node scripts/generate-route-manifest.mjs --check
./gradlew :products:data-cloud:delivery:launcher:test --tests com.ghatana.datacloud.launcher.arch.DataCloudPlaneBoundaryTest --no-daemon
```

### 5.4 Evidence storage conventions

Formalize:

```text
.kernel/out/products/<product>/<phase>/<runId>/**
.kernel/evidence/<product>/<product>-lifecycle-evidence-pack.json
.kernel/evidence/<product>/<runId>/**
.kernel/evidence/platform/**
```

Add retention rules: keep latest evidence pointer, retain release evidence permanently, clean ephemeral dev evidence by age/size, redact absolute local paths, and redact secrets/tenant-sensitive data.

---

## 6. Digital Marketing Full Development Plan

### 6.1 Product workflows to build

Implement complete pilot workflows: customer/account management, workspace/team/role management, campaign planning, campaign lifecycle, campaign activation, lead capture, conversion tracking, audience/segment management, channel configuration, Google Ads connector readiness, reporting/dashboard workflows, consent/privacy, operator workflows, approval workflows, notification retry/DLQ, feature flags, and admin configuration.

### 6.2 Backend development

| Area | Tasks |
|---|---|
| `dm-core-contracts` | Make route, workflow, event, consent, connector, approval, notification, and reporting contracts canonical. Generate UI route manifest from contracts. |
| `dm-domain-packs` | Complete domain pack for customer, campaign, audience, lead, conversion, channel, connector, report, notification, approval. |
| `dm-domain` | Add aggregate invariants: campaign state machine, budget constraints, consent requirements, connector readiness, lead/conversion linkage. |
| `dm-application` | Implement use cases for campaign lifecycle, approvals, activation, retry/DLQ, reporting queries. |
| `dm-infra` | Implement observability, feature flag integration, config validation, connector clients. |
| `dm-persistence` | Add repositories and migrations for campaigns, customers, leads, conversions, segments, connector accounts, approvals, notifications. |
| `dm-connector-google-ads` | No fake success. Return `NOT_READY`, `AUTH_FAILED`, `RATE_LIMITED`, `REMOTE_VALIDATION_FAILED`, `PUBLISH_FAILED`, or `ENVIRONMENT_BLOCKED` as appropriate. |
| `dm-api` | Expose typed endpoints with request/response validation, auth/tenant/workspace scope, route capability metadata, OpenAPI contract. |
| `dm-kernel-bridge` | Ensure plugin bindings emit audit, consent, approval, risk, notification, and health evidence. |
| `dm-integration-tests` | Cover API-service-db flows, connector fake-remote tests, retry/DLQ, approval, reporting, route entitlements. |

### 6.3 Frontend development

In `products/digital-marketing/ui`, use `@ghatana/product-shell` and `@ghatana/design-system`; keep route manifest generated/contract-backed; implement dashboards, campaigns, customers/leads, segments, channels, connectors, reports, approvals, AI action log, admin/settings; add loading/empty/error/degraded/access-denied states; use typed TanStack Query DTOs; add keyboard and a11y tests; add Playwright journeys for campaign lifecycle.

### 6.4 Lifecycle integration

| Phase | Expected development proof |
|---|---|
| dev | backend and web start with health endpoints and no unsafe defaults |
| validate | registry, manifest, bridge, consent boundary, minimization, route contract pass |
| test | domain, app, persistence, connector, API, UI, E2E tests pass |
| build | backend jar and web bundle produced and fingerprinted |
| package | real container images or environment-blocked classification |
| deploy | compose local manifest with expected services |
| verify | real health report for API and web |
| promote | approval and promotion manifest |
| rollback | rollback manifest, previous artifact selection, post-rollback verify |

### 6.5 Digital Marketing done criteria

Digital Marketing is done when all pilot workflows work end-to-end, Google Ads cannot fake success, approval and notification retry/DLQ are implemented, reports are backed by real domain data, API contracts and UI routes match, lifecycle phases produce versioned evidence, and Studio shows lifecycle/artifact/deployment/health truth.

---

## 7. PHR Full Development Plan

### 7.1 Product workflows to build

Implement complete PHR pilot workflows: patient profile, health record summary, encounter history, medications, allergies, conditions, lab results, immunizations, documents, care team, consent management, data sharing authorization, audit access history, FHIR R4 resource handling, tenant/workspace scoping, patient privacy controls, data sovereignty evidence, and break-glass access.

### 7.2 Backend development

| Area | Tasks |
|---|---|
| `products/phr` | Add secure API surfaces for patient records, consent, sharing, audit, FHIR resources, and health/readiness. |
| `products/phr/domains/healthcare` | Implement FHIR R4 resource validators and healthcare domain invariants. |
| `products/phr/launcher` | Wire health, readiness, metrics, tenant/auth filters, secure config, deployability. |
| schema packs | Make schema registry authoritative and versioned. Add fixture and contract tests. |
| gate packs | Make each gate evidence-backed and fail-closed. |
| persistence/data access | Enforce tenant/workspace/patient scope and data sovereignty rules. |
| observability | Emit audit, privacy, consent, FHIR validation, and tenant-scope reason codes. |

### 7.3 Frontend development

In `products/phr/apps/web`, implement patient dashboard, record summary, timeline, medications, allergies, labs, documents, consent, sharing, audit/evidence, settings; use role manifest and route access for patient/caregiver/clinician/admin; add consent and privacy notices; add access denied states and emergency/break-glass warning states; support loading/empty/error/degraded states; add keyboard and screen-reader coverage; add Playwright healthcare journeys.

### 7.4 Healthcare gates

| Gate | Required implementation |
|---|---|
| consent | prove access and sharing operations check active consent, revocation, expiry, purpose, actor, and patient scope |
| PII classification | classify patient-identifying fields, enforce redaction/minimization, and emit classification evidence |
| audit evidence | write immutable audit events for record access, sharing, consent change, break-glass, export |
| FHIR contract validation | validate supported FHIR R4 resources against schema packs and reject invalid resource shapes |
| tenant data sovereignty | prove tenant/workspace/patient data never crosses scope; record storage and export location evidence |

### 7.5 Lifecycle integration

PHR must exercise dev, validate, test, build, package, deploy, and verify. Rollback remains target-partial until stable deployment manifest history, previous-artifact selection policy, healthcare post-rollback verification gates, and rollback approval contract are implemented.

### 7.6 PHR done criteria

PHR is done when backend and web support pilot healthcare workflows; FHIR validation, consent enforcement, PII classification, audit evidence, and tenant sovereignty are real; lifecycle evidence is current; rollback is correctly blocked or safely enabled; Studio can show healthcare gate truth.

---

## 8. Shared Studio and UI Development Plan

### 8.1 Studio Kernel views

Build or harden product registry view, ProductUnit detail, lifecycle plan viewer, lifecycle run history, phase result viewer, gate result viewer, artifact manifest viewer, deployment manifest viewer, verify health report viewer, rollback manifest viewer, approval viewer, and provider/plugin health viewer.

### 8.2 Artifact workflow views

Build or harden source acquisition page, import/decompile page, round-trip diff view, fidelity report page, residual island report, semantic artifact reference viewer, DS projection viewer, Builder projection viewer, Canvas projection viewer, and generated change-set summary.

### 8.3 Shared UI components

Promote shared components only after two real consumers need them: lifecycle status badge, gate result table, artifact trust card, deployment health card, approval timeline, provider health matrix, source acquisition error panel, fidelity score summary, residual island list, and access denied/blocked/degraded states.

---

## 9. Data Cloud and YAPPC Integration Plan

### 9.1 Data Cloud provider bridge

Develop only through bridge contracts: runtime truth provider, event provider, artifact provider, health provider, provenance provider, policy evidence provider, and memory provider where appropriate.

Hard rules:

- Kernel must not import Data Cloud plane internals.
- Data Cloud must not implement product lifecycle execution.
- Platform mode must fail closed if Data Cloud provider health is missing.
- Tenant identity must derive from authenticated identity in production/staging/sovereign profiles.
- Route manifest/runtime truth drift must block Data Cloud provider release.

### 9.2 YAPPC handoff

Develop only through handoff contracts: ProductUnitIntent export, semantic artifact references, artifact graph summary, product shape evidence, generated change-set summary, risk hotspot report, and dependency graph evidence.

Hard rules:

- Kernel consumes evidence and references, not YAPPC compiler internals.
- YAPPC does not run Kernel lifecycle phases directly.
- Studio visualizes handoff truth without importing product-specific YAPPC internals.

---

## 10. Gap Matrix

| ID | Area | Current state | Desired state | Development task | Owner module | Tests | Validation | Severity | Dependency | Done criteria |
|---|---|---|---|---|---|---|---|---|---|---|
| K-001 | Kernel contracts | Broad exports exist | Runtime-proven ProductUnit truth | Add ProductUnit projection and golden registry tests | `kernel-product-contracts` | contract tests | `check:kernel-product-unit-provider-contracts` | High | registry | same truth in CLI/Studio/providers |
| K-002 | Lifecycle executor | Executes phases | durable graph + run history | Add phase graph, run store, resume policy | `kernel-lifecycle` | planner/executor tests | `check:kernel-lifecycle-service` | High | contracts | replay-safe, queryable runs |
| K-003 | Provider mode | bootstrap works, platform partial | Data Cloud provider negotiation | Health matrix + fail-closed platform mode | `kernel-providers`, Data Cloud bridge | provider conformance | `check:kernel-provider-mode` | High | Data Cloud bridge | platform mode only when healthy |
| K-004 | Toolchains | adapters exist | preflight + classified failures | Add failure classifier/output validator | `kernel-toolchains` | adapter tests | `check:toolchain-adapter-contracts` | High | lifecycle | no fake success |
| K-005 | Artifacts | manifests + compiler packages | unified trust/provenance | Add artifact trust/source/deployment linkage | `kernel-artifacts`, `artifact-contracts` | manifest/fidelity tests | `check:artifact-roundtrip` | High | Studio | source→artifact→deploy trace |
| K-006 | Studio artifact workflow | partial-to-strong | production import/decompile flow | implement backend acquisition jobs and provenance | `ghatana-studio` | Vitest + Playwright | `check:studio-artifact-workflow-e2e` | High | artifact contracts | no pending job fake success |
| DC-001 | Data Cloud boundary | new ArchUnit gate | enforced provider boundary | expand plane boundary checks and CI docs | Data Cloud launcher | ArchUnit | launcher boundary test | Critical | Data Cloud | no plane/launcher/AEP internals leak |
| DC-002 | Route runtime truth | generator exists | release-blocking drift check | wire generator into platform-provider gates | Data Cloud scripts/UI | generator tests | `generate-route-manifest --check` | High | RouteSecurityRegistry | generated truth committed |
| DM-001 | Campaign workflows | partial | complete lifecycle | state machine + APIs + UI + E2E | `products/digital-marketing` | domain/API/E2E | `test:digital-marketing` | Critical | persistence | campaign end-to-end works |
| DM-002 | Google Ads connector | connector module exists | no fake success | typed remote adapter + readiness states | `dm-connector-google-ads` | connector integration | `dm-integration-tests` | High | secrets/config | real not-ready/errors |
| DM-003 | Reporting | route contracts exist | real KPI/report queries | implement analytics queries and dashboards | `dm-api`, `ui` | API/UI/E2E | route + reporting tests | High | domain data | reports match data |
| PHR-001 | FHIR validation | gate declared | real validation | schema-backed validators and API rejection | `phr/domains/healthcare` | FHIR tests | `check:phr-lifecycle-pilot` | Critical | schema packs | invalid FHIR rejected |
| PHR-002 | Consent | gate declared | enforced workflow | consent grants/revocations/access checks | `products/phr` | consent tests | `test:phr` | Critical | auth/scoping | access blocked without consent |
| PHR-003 | PII classification | gate declared | classification/redaction | classifiers + redaction tests | `products/phr` | privacy tests | `check:phr-lifecycle-readiness` | Critical | schemas | evidence generated |
| PHR-004 | Audit evidence | gate declared | immutable access history | audit store and UI | `products/phr` | audit tests | `test:phr` | High | persistence | audit visible and queryable |
| PHR-005 | Rollback | target-partial | gated enablement | previous artifact + healthcare post-checks | `kernel-release`, PHR config | rollback tests | `plan:rollback:local:phr` | Medium | deploy history | enabled only when safe |
| UI-001 | Shared states | partial | reusable status UX | extract after PHR/DMOS reuse | design-system/product-shell | component tests | `check:design-system-conformance` | Medium | reuse proven | no product duplicate |
| GOV-001 | Command sprawl | many scripts | command taxonomy | docs + metadata + cadence | docs/scripts | doc checks | `check:doc-truth` | Medium | package scripts | engineers know what to run |

---

## 11. Phased Roadmap

### Phase 0 — Foundation alignment and executable baseline

Confirm registry, Gradle, pnpm, product manifests, and evidence packs. Classify current PHR/DMOS lifecycle truth. Add command taxonomy. Preserve Data Cloud route/runtime-truth drift gate.

Validation:

```bash
pnpm check:phase0
pnpm check:product-registry
pnpm check:product-workspace-registration
pnpm check:architecture-boundaries
```

### Phase 1 — Kernel contract and infrastructure hardening

Make ProductUnit/ProductUnitIntent fully canonical, close provider boundary ambiguity, and enrich lifecycle failure model.

Validation:

```bash
pnpm build:kernel-lifecycle-platform
pnpm check:kernel-product-unit-provider-contracts
pnpm check:kernel-provider-mode
pnpm check:kernel-lifecycle-service
```

### Phase 2 — Kernel lifecycle execution and adapter development

Improve real execution semantics for phases, adapters, artifacts, deployments, and failure classification.

Validation:

```bash
pnpm check:phase2
pnpm validate:digital-marketing
pnpm validate:phr
pnpm build:digital-marketing
pnpm build:phr
```

### Phase 3 — Digital Marketing full product development

Complete DMOS pilot product, not just lifecycle smoke.

Validation:

```bash
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm package:digital-marketing
pnpm deploy:local:digital-marketing
pnpm verify:local:digital-marketing
pnpm promote:local:digital-marketing
pnpm rollback:local:digital-marketing
```

### Phase 4 — PHR full product development

Complete regulated healthcare pilot workflows and gates.

Validation:

```bash
pnpm test:phr
pnpm build:phr
pnpm package:phr
pnpm deploy:local:phr
pnpm verify:local:phr
pnpm plan:rollback:local:phr --json
```

### Phase 5 — Studio artifact and developer workflow hardening

Make Studio a true product-development console with import/decompile/fidelity/residual views and backend acquisition jobs.

Validation:

```bash
pnpm check:artifact-roundtrip
pnpm check:studio-artifact-workflow-e2e
pnpm check:builder-canonical-document
pnpm check:builder-canvas-adapter
pnpm check:ds-generator-golden
```

### Phase 6 — Data Cloud/YAPPC provider integration

Make platform mode real without boundary leaks.

Validation:

```bash
pnpm check:data-cloud-platform-providers
pnpm check:data-cloud-platform-provider-readiness
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
cd products/data-cloud && node scripts/generate-route-manifest.mjs --check
```

### Phase 7 — Diverse product shape expansion

Validate Kernel generality without becoming a god product.

Validation:

```bash
pnpm check:product-shape-capability-matrix
pnpm check:finance-lifecycle-readiness
pnpm check:flashit-lifecycle-readiness
pnpm check:phase7
```

### Phase 8 — Release-grade no-regression gate

Prove platform and pilots are coherent.

Validation:

```bash
pnpm check:full-repo-test
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

---

## 12. Testing Plan

### Kernel tests

ProductUnit contract tests, ProductUnitIntent preview/apply tests, lifecycle planner graph tests, executor partial phase and multi-surface tests, adapter preflight/failure classification tests, provider conformance tests, artifact/deployment/release/rollback manifest tests, plugin gate result tests, CLI explain/error tests, and no-fake-success tests.

### Digital Marketing tests

Domain unit tests for campaign/customer/lead/conversion/segment; application service tests for lifecycle and approvals; persistence tests for scoped queries; Google Ads connector tests with realistic remote error modes; API contract tests; kernel bridge tests; UI route/component/integration tests; Playwright campaign lifecycle E2E; accessibility tests; lifecycle smoke tests.

### PHR tests

FHIR validation tests, consent enforcement tests, PII classification/redaction tests, audit evidence tests, tenant data sovereignty tests, patient/caregiver/clinician/admin route access tests, UI component and integration tests, Playwright healthcare journeys, accessibility tests, and lifecycle smoke tests.

### Data Cloud/YAPPC tests

Data Cloud plane boundary ArchUnit tests, route manifest drift tests, OpenAPI route parity tests, tenant strict-mode tests, runtime truth tests, YAPPC ProductUnitIntent handoff tests, artifact intelligence boundary tests, and agentic lifecycle action contract tests.

### Cross-product tests

Registry and domain registry, workspace registration, platform/product boundaries, Kernel/YAPPC boundary, Kernel/Data Cloud boundary, production stubs, deprecated package/imports, orphan modules, design-system conformance, shared product shell conformance, evidence freshness, and manifest completeness.

---

## 13. Validation and No-Regression Plan

| Deliverable | Focused command | Broad command | Evidence | Failure interpretation |
|---|---|---|---|---|
| Product registry | `pnpm check:product-registry` | `pnpm check:phase0` | registry validation report | product/config failure |
| Kernel contracts | `pnpm check:kernel-product-unit-provider-contracts` | `pnpm check:kernel-product-boundary-audit` | contract test output | code/contract failure |
| Lifecycle engine | `pnpm check:kernel-lifecycle-service` | `pnpm check:kernel-platform-lifecycle` | lifecycle test report | code failure |
| Toolchains | `pnpm check:toolchain-adapter-contracts` | `pnpm check:phase2` | adapter contract output | code/env/dependency failure |
| Artifacts | `pnpm check:artifact-roundtrip` | `pnpm check:phase8` | fidelity/diff reports | code/artifact failure |
| Studio artifact workflow | `pnpm check:studio-artifact-workflow-e2e` | `pnpm check:phase8` | Vitest/Playwright output | UI/workflow failure |
| Digital Marketing | `pnpm check:digital-marketing-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/digital-marketing` | `pnpm check:full-repo-test` | `.kernel/evidence/digital-marketing/**` | product/code/env failure |
| PHR | `pnpm check:phr-lifecycle-pilot -- --smoke --evidence-pack-dir .kernel/evidence/phr` | `pnpm check:full-repo-test` | `.kernel/evidence/phr/**` | product/gate/env failure |
| Data Cloud route truth | `cd products/data-cloud && node scripts/generate-route-manifest.mjs --check` | Data Cloud CI | route manifest + generated UI truth | route/runtime-truth drift |
| Data Cloud boundaries | launcher boundary ArchUnit test | Data Cloud CI | Gradle test output | boundary leak |
| Final gate | `pnpm check:world-class-platform-readiness` | `pnpm check:full-repo-test` | all reports | release blocker |

---

## 14. Execution Order

1. Freeze current-state facts at `9d4a677a90cfe4a4a514361e78af83abf3f3e888` and publish this plan.
2. Implement command taxonomy and evidence freshness checks.
3. Harden Kernel ProductUnit/ProductUnitIntent projection.
4. Harden lifecycle graph, run history, and failure classifier.
5. Harden adapters and manifest validation.
6. Harden artifact compiler/decompiler trust/provenance and Studio workflow.
7. Complete Digital Marketing workflows and connector behavior.
8. Complete PHR healthcare workflows and gates.
9. Integrate Data Cloud platform provider mode with route/runtime-truth gates.
10. Integrate YAPPC artifact intelligence handoff through contracts only.
11. Enable broader product shape expansion without turning Kernel into a god product.
12. Run `pnpm check:full-repo-test`, `pnpm check:phase8`, and `pnpm check:world-class-platform-readiness`.

---

## 15. Definition of Done

The overall initiative is done when:

1. Kernel can plan, execute, verify, promote, rollback, and explain ProductUnit lifecycle with durable evidence.
2. PHR and Digital Marketing are complete pilot products, not merely lifecycle smoke examples.
3. Bootstrap mode and platform mode are both supported with clear provider health and fail-closed behavior.
4. Data Cloud route/runtime-truth and Action Plane boundaries are enforced.
5. YAPPC handoff uses ProductUnitIntent and artifact intelligence evidence without Kernel importing YAPPC internals.
6. Studio shows real lifecycle, artifact, deployment, health, provider, plugin, and product gate truth.
7. Artifact compiler/decompiler supports import/decompile/compile/projection/fidelity/residual/repository scan with provenance and no fake repository/archive success.
8. All critical paths have tests at the correct layer.
9. All failures are classified clearly.
10. No product-local lifecycle runners exist.
11. No product logic leaks into generic platform packages.
12. No fake success, stub production path, hardcoded secret, untyped TypeScript, or object-literal test theater is introduced.
13. `pnpm check:full-repo-test`, `pnpm check:phase8`, and `pnpm check:world-class-platform-readiness` pass or report a precise environment/dependency blocker.
