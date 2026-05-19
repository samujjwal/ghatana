# Ghatana PHR + Digital Marketing Parallel Pilot Implementation Plan

**Target repo:** `samujjwal/ghatana`  
**Target commit snapshot:** `04f03168e597cca638110f0025bd6231ac636fe5`  
**Execution mode:** This file is independently executable by product-pilot teams. It focuses on PHR and Digital Marketing implementation, validation, testing, regression protection, and product correctness while consuming platform contracts rather than modifying platform ownership. It assumes the platform foundation plan may proceed separately.

---

## IMPLEMENTATION PROGRESS (Updated 2026-05-19)

### PHR Lifecycle Phases

| Phase        | Status    | Evidence runId                                          |
| ------------ | --------- | ------------------------------------------------------- |
| validate     | ✅ PASSED | `2026-05-19T03-18-16-3d5fd5e0`                          |
| test         | ✅ PASSED | `2026-05-19T03-19-25-4dc07ea5`                          |
| build        | ✅ PASSED | `2026-05-19T03-20-01-b94dab22`                          |
| package      | ✅ PASSED | Docker image `ghatana/phr-api:local` built successfully |
| deploy:local | ✅ PASSED | `2026-05-19T05-57-30-129cf86e`                          |
| verify:local | ✅ PASSED | `2026-05-19T05-58-00-9128a962`                          |

### Digital Marketing Lifecycle Phases

| Phase        | Status    | Evidence runId                 |
| ------------ | --------- | ------------------------------ |
| validate     | ✅ PASSED | `2026-05-19T03-57-36-55501824` |
| test         | ✅ PASSED | `2026-05-19T05-58-09-93b9f81e` |
| build        | ✅ PASSED | `2026-05-19T05-58-43-417998c4` |
| package      | ✅ PASSED | `2026-05-19T05-59-26-081c9b81` |
| deploy:local | ✅ PASSED | `2026-05-19T05-59-55-7d967e22` |
| verify:local | ✅ PASSED | `2026-05-19T06-00-10-bb0d82dd` |

### Phase 1 — Product Truth and Governance Alignment

| Check                                     | Status                                                                                     |
| ----------------------------------------- | ------------------------------------------------------------------------------------------ |
| `check:phr-lifecycle-readiness`           | ✅ PASSED                                                                                  |
| `check:digital-marketing-lifecycle-pilot` | ✅ PASSED                                                                                  |
| `check:product-workspace-registration`    | ✅ PASSED                                                                                  |
| `check:bridge-compliance`                 | ✅ PASSED                                                                                  |
| `check:dmos-boundary-workflow-coverage`   | ✅ PASSED                                                                                  |
| `check:route-entitlement-contracts`       | ✅ PASSED                                                                                  |
| `check:design-system-conformance`         | ✅ PASSED (8 UI surfaces, 440 files)                                                       |
| `check:product-registry`                  | ✅ PASSED                                                                                  |
| `check:doc-claims-evidence`               | ✅ PASSED                                                                                  |
| `check:current-state-claims`              | ✅ PASSED                                                                                  |
| `check:product-doc-taxonomy`              | ✅ PASSED (created 06-IMPLEMENTATION_PLAN.md for phr, finance, digital-marketing, flashit) |
| `check:domain-boundaries`                 | ✅ PASSED                                                                                  |
| `check:platform-product-boundaries`       | ✅ PASSED                                                                                  |
| `check:product-kind-classification`       | ✅ PASSED                                                                                  |
| `check:secret-default-credentials`        | ✅ PASSED                                                                                  |
| `check:production-stubs`                  | ✅ PASSED (615 warnings, no critical violations)                                           |
| `check:production-readiness`              | ✅ PASSED                                                                                  |
| `check:cleanup-gate`                      | ✅ PASSED                                                                                  |
| `check:product-manifest-contracts`        | ✅ PASSED                                                                                  |
| `check:product-ci-matrices`               | ✅ PASSED                                                                                  |

### Phase 4 — Cross-Product Stability Gates

| Check                                        | Status                               |
| -------------------------------------------- | ------------------------------------ |
| `check:kernel-lifecycle-truth`               | ✅ PASSED                            |
| `check:studio-kernel-api`                    | ✅ PASSED                            |
| `check:yappc-product-unit-intent-handoff`    | ✅ PASSED (17/17 tests)              |
| `check:yappc-artifact-intelligence-boundary` | ✅ PASSED (3618 files, 0 violations) |
| `check:data-cloud-platform-providers`        | ✅ PASSED                            |

### Phase 5 — No-Regression Gates

| Check                               | Status                                    |
| ----------------------------------- | ----------------------------------------- |
| `check:digital-marketing-root-docs` | ✅ PASSED (created 3 redirect stub files) |
| `check:security-workflow-coverage`  | ✅ PASSED                                 |

### Key Fixes Applied

1. **`local.properties` Docker exclusion**: Added to all 4 `Dockerfile.dockerignore` files — prevents macOS `java.home` path from breaking Linux container Gradle builds.
2. **`products/phr/apps/web/README.md`**: Created — missing module documentation.
3. **`products/phr/domains/healthcare/README.md`**: Created — missing module documentation.
4. **`06-IMPLEMENTATION_PLAN.md`**: Created for `phr`, `finance`, `digital-marketing`, `flashit` — required by `check:product-doc-taxonomy`.
5. **Digital Marketing root-doc stubs**: Created `digital-marketing-product-architecture{,-canonical,-v2}.md` — required by `check:digital-marketing-root-docs`.
6. **Dockerfile stub-dir fix**: Added `RUN grep 'include(' ... | mkdir -p` step to PHR and DM Dockerfiles — Gradle requires ALL registered project directories to exist at configuration time; stub dirs prevent ENOENT failures for excluded products.

---

---

## 0. Pilot strategy

At `04f03168e597cca638110f0025bd6231ac636fe5`, both PHR and Digital Marketing are enabled lifecycle pilots. This plan treats them as parallel first-release pilots:

- **Digital Marketing** proves the standard web + backend API product shape, product bridge conformance, campaign workflow correctness, and non-regulated customer-data policy packs.
- **PHR** proves the regulated healthcare web + backend API product shape, including consent, PII classification, audit evidence, FHIR R4 contract validation, and tenant data sovereignty.

Both products must use Product Development Kernel lifecycle execution. Neither product may implement its own lifecycle runner.

---

## 1. Shared product-pilot rules

1. Product-specific behavior stays under `products/phr/**` or `products/digital-marketing/**`.
2. Generic lifecycle, manifest, artifact, deployment, release, approval, policy, observability, security, privacy, and UI primitives must be consumed from platform packages/contracts.
3. Product bridge implementations are product-owned but must implement platform contracts.
4. Product gates must fail closed with actionable reason codes.
5. PHR healthcare gates may not be bypassed to keep lifecycle green.
6. Digital Marketing must not shape Kernel around itself.
7. Product UIs must reuse design-system/product-shell primitives where available.
8. Product manifests must match `config/canonical-product-registry.json` and `products/*/kernel-product.yaml`.
9. Product-local tests must test real production code, not object literals or fake success.
10. Every pilot task must produce evidence in `.kernel/evidence/<product>/<runId>/` or a product-local evidence folder referenced by manifests.

---

## 2. Current snapshot facts to preserve

## 2.1 PHR

Current PHR status at `04f03168e597cca638110f0025bd6231ac636fe5`:

- product ID: `phr`
- lifecycle status: `enabled`
- lifecycle execution allowed: `true`
- surfaces: `backend-api`, `web`
- backend source: `products/phr`
- web source: `products/phr/apps/web`
- backend adapter: `gradle-java-service`
- web adapter: `pnpm-vite-react`
- deployment target: `compose-local`
- local services: `phr-api`, `phr-web`
- required healthcare gates:
  - `consent`
  - `pii-classification`
  - `audit-evidence`
  - `fhir-contract-validation`
  - `tenant-data-sovereignty`
- evidence refs include readiness evidence and gate packs under `products/phr/lifecycle/**`

## 2.2 Digital Marketing

Current Digital Marketing status at `04f03168e597cca638110f0025bd6231ac636fe5`:

- product ID: `digital-marketing`
- lifecycle status: `enabled`
- lifecycle execution allowed: `true`
- surfaces: `backend-api`, `web`
- backend source: `products/digital-marketing/dm-api`
- web source: `products/digital-marketing/ui`
- backend adapter: `gradle-java-service`
- web adapter: `pnpm-vite-react`
- bridge module: `products/digital-marketing/dm-kernel-bridge`
- deployment target: `compose-local`
- local services: `digital-marketing-api`, `digital-marketing-web`
- policy packs include web/API security baseline, container image integrity, customer-data minimization, and marketing consent boundary
- approvals exist for deploy/promote/rollback

---

# Phase 1 — Product truth and governance alignment

## Phase objective

Make PHR and Digital Marketing product truth internally consistent across registry, manifests, generated includes, workspaces, product docs, gates, lifecycle configs, and product evidence.

## Shared Phase 1 tasks

### 1.1 Validate product registry entries

Check:

```text
config/canonical-product-registry.json
config/generated/settings-gradle-includes.kts
pnpm-workspace.yaml
products/phr/kernel-product.yaml
products/digital-marketing/kernel-product.yaml
products/phr/domain-pack-manifest.yaml
products/digital-marketing/dm-domain-packs/domain-pack.json
```

Required invariants:

- product ID matches path and manifest
- lifecycle status matches execution flag
- surface paths exist
- Gradle modules exist and are included
- pnpm packages exist and are included
- required gates are registered
- expected outputs are declared
- deployment targets are declared
- product docs identify owner and status

Validation:

```bash
pnpm check:product-registry
pnpm check:product-registry-artifacts
pnpm check:product-registry-drift
pnpm check:product-workspace-registration
pnpm check:product-manifest-contracts
pnpm check:product-ci-matrices
```

### 1.2 Validate product boundary modes

PHR and Digital Marketing are business products. They must not import each other. They must not import platform source paths directly if a package export exists.

Validation:

```bash
pnpm check:domain-boundaries
pnpm check:platform-product-boundaries
pnpm check:bridge-compliance
pnpm check:product-kind-classification
```

### 1.3 Validate no unsafe defaults

Check:

```bash
pnpm check:secret-default-credentials
pnpm check:production-stubs
pnpm check:production-readiness
pnpm check:cleanup-gate
```

Product-specific requirements:

- PHR cannot ship fake consent or fake PII classification as success.
- Digital Marketing cannot fake connector readiness or approval success.
- Missing optional external connectors must report `NOT_READY` or degraded state, not success.

---

## Phase 1 — PHR tasks

### PHR-1.1 Harden healthcare gate packs

Files/areas:

```text
products/phr/lifecycle/readiness-evidence.yaml
products/phr/lifecycle/gate-packs/consent.yaml
products/phr/lifecycle/gate-packs/pii-classification.yaml
products/phr/lifecycle/gate-packs/audit-evidence.yaml
products/phr/lifecycle/gate-packs/fhir-contract-validation.yaml
products/phr/lifecycle/gate-packs/tenant-data-sovereignty.yaml
products/phr/schema-packs/schema-registry.yaml
products/phr/kernel-product.yaml
```

Tasks:

- verify every required gate has a gate pack
- verify every gate pack has owner, schema version, purpose, evidence inputs, pass/fail reason codes, and validation command
- verify gate packs map to lifecycle phases: validate, build, deploy
- verify missing evidence blocks lifecycle rather than warns
- verify gate outputs are referenced in lifecycle manifests
- verify PHR docs do not claim full healthcare production readiness unless evidence proves it

Tests:

- gate pack parser accepts valid gate pack
- gate pack parser rejects missing evidence inputs
- lifecycle readiness fails when required gate pack is absent
- PHR lifecycle readiness passes only with all required gate packs present

Validation:

```bash
pnpm check:phr-lifecycle-readiness
pnpm plan:validate:phr
pnpm validate:phr
```

### PHR-1.2 Align PHR web/backend product docs

Files/areas:

```text
products/phr/README.md
products/phr/apps/web/README.md
products/phr/domains/healthcare/README.md
products/phr/domain-pack-manifest.yaml
```

Tasks:

- identify implemented vs partial vs target capabilities
- document lifecycle command path
- document healthcare gates
- document local deployment/verify behavior
- document what evidence is required before promoting beyond local pilot

Validation:

```bash
pnpm check:doc-claims-evidence
pnpm check:current-state-claims
pnpm check:product-doc-taxonomy
```

---

## Phase 1 — Digital Marketing tasks

### DM-1.1 Harden Digital Marketing lifecycle manifest alignment

Files/areas:

```text
products/digital-marketing/kernel-product.yaml
products/digital-marketing/dm-domain-packs/domain-pack.json
products/digital-marketing/README.md
products/digital-marketing/dm-kernel-bridge/**
products/digital-marketing/deploy/**
```

Tasks:

- verify phases include dev, validate, test, build, package, deploy, promote, rollback
- verify required manifests are declared for build/package/deploy/verify/rollback
- verify policy packs are product-configured, not implemented in Kernel
- verify approvals for deploy/promote/rollback are contract-backed
- verify Digital Marketing bridge is product-owned and Kernel-contract-backed

Validation:

```bash
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:digital-marketing-root-docs
pnpm check:bridge-compliance
pnpm plan:validate:digital-marketing
pnpm validate:digital-marketing
```

### DM-1.2 Validate bridge adapter evidence

Files/areas:

```text
products/digital-marketing/dm-kernel-bridge/src/main/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImpl.java
products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/DigitalMarketingKernelAdapterImplTest.java
products/digital-marketing/dm-kernel-bridge/src/test/java/com/ghatana/digitalmarketing/bridge/NotificationRetryAndDlqTest.java
```

Tasks:

- verify bridge implements platform contract only
- verify retries and DLQ behavior are observable
- verify no product lifecycle runner exists in bridge
- verify bridge failures return typed reason codes
- verify bridge tests invoke production bridge logic

Validation:

```bash
./gradlew :products:digital-marketing:dm-kernel-bridge:test
pnpm check:bridge-compliance
pnpm check:dmos-boundary-workflow-coverage
```

---

# Phase 2 — Lifecycle execution hardening

## Phase objective

Prove PHR and Digital Marketing can execute Kernel lifecycle phases with real adapters, real outputs, real tests, real manifests, and fail-closed behavior.

## Shared lifecycle command matrix

| Phase             | PHR command                  | Digital Marketing command                  |
| ----------------- | ---------------------------- | ------------------------------------------ |
| plan dev          | `pnpm plan:dev:phr`          | `pnpm plan:dev:digital-marketing`          |
| dev               | `pnpm dev:phr`               | `pnpm dev:digital-marketing`               |
| plan validate     | `pnpm plan:validate:phr`     | `pnpm plan:validate:digital-marketing`     |
| validate          | `pnpm validate:phr`          | `pnpm validate:digital-marketing`          |
| plan test         | `pnpm plan:test:phr`         | `pnpm plan:test:digital-marketing`         |
| test              | `pnpm test:phr`              | `pnpm test:digital-marketing`              |
| plan build        | `pnpm plan:build:phr`        | `pnpm plan:build:digital-marketing`        |
| build             | `pnpm build:phr`             | `pnpm build:digital-marketing`             |
| plan package      | `pnpm plan:package:phr`      | `pnpm plan:package:digital-marketing`      |
| package           | `pnpm package:phr`           | `pnpm package:digital-marketing`           |
| plan deploy local | `pnpm plan:deploy:local:phr` | `pnpm plan:deploy:local:digital-marketing` |
| deploy local      | `pnpm deploy:local:phr`      | `pnpm deploy:local:digital-marketing`      |
| plan verify local | `pnpm plan:verify:local:phr` | `pnpm plan:verify:local:digital-marketing` |
| verify local      | `pnpm verify:local:phr`      | `pnpm verify:local:digital-marketing`      |

## Shared Phase 2 tasks

### 2.1 Plan phase proof

For each product:

- plan command emits `lifecycle-plan.json`
- plan includes product ID, surfaces, adapters, gates, expected outputs, environment, and correlation ID
- plan rejects unknown surface
- plan rejects blocked gates before execution
- plan classifies target/partial capabilities honestly

Test cases:

- valid PHR build plan
- valid Digital Marketing build plan
- invalid surface fails
- missing product manifest fails
- platform-provider product is not accidentally executed as business product

### 2.2 Validate/test/build proof

For each product:

- validate runs configured gates
- test runs backend and web tests
- build produces expected outputs
- failed backend blocks lifecycle
- failed web blocks lifecycle
- missing artifacts block lifecycle
- results include timings, failure reason codes, and evidence refs

### 2.3 Package/deploy/verify proof

For each product:

- package produces container artifact manifest or `NOT_READY` if Docker path is not implemented
- deploy uses compose-local adapter
- verify uses health checks from product manifest
- health checks have retries/timeouts
- failure status is explicit
- rollback readiness is documented if rollback is configured

---

## Phase 2 — PHR tasks

### PHR-2.1 Backend lifecycle execution

Files/areas:

```text
products/phr/build.gradle.kts
products/phr/launcher/**
products/phr/src/main/**
products/phr/src/test/**
products/phr/kernel-product.yaml
```

Tasks:

- verify `:products:phr` builds under Java 21
- verify health endpoint `/health/ready` exists and is real
- verify backend tests cover healthcare domain validation, consent enforcement, audit evidence, FHIR schema validation, and tenant scoping
- verify no blocking I/O in ActiveJ event-loop paths unless wrapped by approved blocking bridge
- verify public Java APIs have `@doc.*` tags

Validation:

```bash
./gradlew :products:phr:check
pnpm test:phr-gateway
pnpm build:phr-gateway
```

### PHR-2.2 Web lifecycle execution

Files/areas:

```text
products/phr/apps/web/package.json
products/phr/apps/web/src/**
products/phr/apps/web/vitest.config.*
products/phr/apps/web/playwright.config.*
```

Tasks:

- verify React/TypeScript strict typing
- verify no `any` in new code
- verify patient/record/consent flows render without backend fake success
- verify loading/empty/error/forbidden states
- verify no hardcoded final user-facing strings where i18n path exists
- verify accessible labels and keyboard navigation for forms

Validation:

```bash
pnpm --dir products/phr/apps/web type-check
pnpm --dir products/phr/apps/web test
pnpm build:phr-web
pnpm test:phr-web
```

### PHR-2.3 PHR lifecycle proof

Run:

```bash
pnpm plan:validate:phr
pnpm validate:phr
pnpm plan:test:phr
pnpm test:phr
pnpm plan:build:phr
pnpm build:phr
pnpm plan:package:phr
pnpm package:phr
pnpm plan:deploy:local:phr
pnpm deploy:local:phr
pnpm plan:verify:local:phr
pnpm verify:local:phr
```

Acceptance:

- evidence is generated under `.kernel/evidence/phr/<runId>/`
- healthcare gate results appear in lifecycle result
- verify report includes backend and web health checks
- missing consent/FHIR/PII/audit/sovereignty evidence fails closed

---

## Phase 2 — Digital Marketing tasks

### DM-2.1 Backend lifecycle execution

Files/areas:

```text
products/digital-marketing/dm-api/build.gradle.kts
products/digital-marketing/dm-api/src/main/**
products/digital-marketing/dm-api/src/test/**
products/digital-marketing/dm-application/**
products/digital-marketing/dm-domain/**
products/digital-marketing/dm-persistence/**
products/digital-marketing/dm-infra/**
products/digital-marketing/dm-connector-google-ads/**
```

Tasks:

- verify API build/test/check tasks match `kernel-product.yaml`
- verify `/health/live` and `/health/ready` are real
- verify campaign/workspace/tenant workflows are covered
- verify connector readiness reports degraded/NOT_READY when external credentials are absent
- verify audit/consent/suppression behavior is tested
- verify no unsafe defaults or fake connector success

Validation:

```bash
./gradlew :products:digital-marketing:dm-api:check
./gradlew :products:digital-marketing:dm-integration-tests:check
pnpm test:digital-marketing-gateway
pnpm build:digital-marketing-gateway
```

### DM-2.2 Web lifecycle execution

Files/areas:

```text
products/digital-marketing/ui/package.json
products/digital-marketing/ui/src/**
products/digital-marketing/ui/vitest.config.*
products/digital-marketing/ui/playwright.config.*
```

Tasks:

- verify Vite/React build and tests
- verify route entitlement contracts
- verify campaign workflow UI states
- verify bridge/lifecycle status displays if consumed
- verify a11y/i18n readiness gates
- verify no duplicate design-system primitives

Validation:

```bash
pnpm --dir products/digital-marketing/ui type-check
pnpm --dir products/digital-marketing/ui test
pnpm build:digital-marketing-web
pnpm test:digital-marketing-web
```

### DM-2.3 Digital Marketing lifecycle proof

Run:

```bash
pnpm plan:validate:digital-marketing
pnpm validate:digital-marketing
pnpm plan:test:digital-marketing
pnpm test:digital-marketing
pnpm plan:build:digital-marketing
pnpm build:digital-marketing
pnpm plan:package:digital-marketing
pnpm package:digital-marketing
pnpm plan:deploy:local:digital-marketing
pnpm deploy:local:digital-marketing
pnpm plan:verify:local:digital-marketing
pnpm verify:local:digital-marketing
```

Acceptance:

- evidence is generated under `.kernel/evidence/digital-marketing/<runId>/`
- gate results include bridge compliance and marketing consent boundary
- deployment manifest references expected compose services
- verify health report includes API and web health checks
- approvals are required for risky deploy/promote/rollback paths

---

# Phase 3 — Product UI and shared primitive conformance

## Phase objective

Ensure PHR and Digital Marketing UI surfaces are consistent, accessible, i18n-ready, simple, and reusable without leaking product behavior into shared libraries.

## Shared Phase 3 tasks

### 3.1 Design-system usage audit

For both products:

- inventory buttons, inputs, cards, badges, tables, layouts, shells, alerts, modals, navigation, and forms
- replace product-local duplicates with design-system/product-shell primitives when the shared component exists
- register legitimate exceptions in duplication exception registry
- ensure status vocabulary is consistent

Validation:

```bash
pnpm check:design-system-conformance
pnpm check:shared-product-shells
pnpm check:shared-layout-primitives
pnpm check:product-ui-contracts
```

### 3.2 Accessibility and i18n audit

For both products:

- all forms have labels and validation messages
- keyboard navigation works for all flows
- focus states are visible
- error messages are not color-only
- route titles and empty states are readable
- final UI strings are translation-key ready
- date/time/number formatting is locale-aware where shown

Validation:

```bash
pnpm check:audited-ui-workflows
pnpm check:route-entitlement-contracts
pnpm check:product-ui-contracts
```

### 3.3 Product route entitlement audit

For both products:

- routes declare required permissions
- backend enforces same permissions
- unauthorized state is explicit
- forbidden actions are hidden only after server-side denial exists
- tests cover admin/user/readonly where product roles exist

Validation:

```bash
pnpm check:route-entitlement-contracts
pnpm check:security-workflow-coverage
```

---

## Phase 3 — PHR UI tasks

Required flows to audit/test:

- patient list/dashboard
- patient detail
- clinical record/document view
- consent status
- data-sharing/sovereignty status
- audit/evidence view
- empty patient state
- unauthorized patient access
- FHIR validation error state
- backend degraded/offline state

Tests:

- component tests for core cards/forms
- integration tests for API validation boundaries
- Playwright journey for patient/record/consent workflow if configured
- a11y tests for forms and navigation

---

## Phase 3 — Digital Marketing UI tasks

Required flows to audit/test:

- campaign dashboard
- campaign create/edit
- campaign approval state
- workspace/tenant switching if present
- connector health/degraded state
- suppression/consent boundary state
- audience/segment views if present
- analytics/reporting cards if present
- unauthorized route access
- backend degraded/offline state

Tests:

- component tests for campaign cards/forms
- integration tests for API validation boundaries
- Playwright campaign workflow
- a11y tests for navigation and forms

---

# Phase 4 — Runtime truth, provenance, and visibility

## Phase objective

Make PHR and Digital Marketing lifecycle and domain evidence visible through public truth contracts, not logs or product-private state.

## Shared Phase 4 tasks

### 4.1 Emit product lifecycle evidence

For every lifecycle run:

- product ID
- run ID
- correlation ID
- phase
- surface
- adapter
- gate results
- artifact refs
- deployment refs
- verify health refs
- reason codes
- timestamps

### 4.2 Product-specific evidence

PHR evidence:

- consent gate result
- PII classification result
- audit evidence result
- FHIR contract validation result
- tenant data sovereignty result

Digital Marketing evidence:

- bridge compliance result
- marketing consent boundary result
- customer data minimization result
- campaign workflow coverage result
- connector readiness result

### 4.3 Data Cloud bridge preparation

Do not make PHR/DM directly depend on Data Cloud internals. Publish through Kernel/Data Cloud bridge contracts.

Required evidence output paths:

```text
.kernel/evidence/phr/<runId>/**
.kernel/evidence/digital-marketing/<runId>/**
products/phr/lifecycle/**
products/digital-marketing/lifecycle/**  # create only if product-local lifecycle evidence is needed
```

### 4.4 Studio/YAPPC visibility

YAPPC/Studio must consume:

- public lifecycle result
- public gate result manifest
- public artifact/deployment manifests
- public health snapshot
- public provider status

They must not:

- parse private logs
- infer success from stdout/stderr
- mutate product registry
- reach into product-private internals

Validation:

```bash
pnpm check:kernel-lifecycle-truth
pnpm check:studio-kernel-api
pnpm check:yappc-product-unit-intent-handoff
pnpm check:yappc-artifact-intelligence-boundary
pnpm check:data-cloud-platform-providers
```

---

# Phase 5 — Product validation and rollout readiness

## Phase objective

Validate that both pilots are product-correct, not merely lifecycle-green.

---

## Phase 5 — PHR rollout readiness

### PHR-5.1 Healthcare correctness

Required review areas:

- FHIR R4 resource shape
- patient identity and record ownership
- consent model
- PII classification
- audit evidence
- tenant/workspace/org scoping
- data sovereignty controls
- regulated deployment gates
- health endpoint correctness
- failure/degraded states

Required tests:

- FHIR contract validation positive/negative cases
- consent required/denied/granted cases
- PII classification enforcement
- tenant isolation tests
- audit trail written on sensitive access
- unauthorized record access denied server-side
- web unauthorized state matches backend denial

Validation:

```bash
pnpm check:phr-lifecycle-readiness
./gradlew :products:phr:check
./gradlew :products:phr:domains:healthcare:check
pnpm test:phr
pnpm build:phr
pnpm validate:phr
```

### PHR-5.2 PHR no-regression gate

Run before merge:

```bash
pnpm plan:validate:phr
pnpm validate:phr
pnpm plan:test:phr
pnpm test:phr
pnpm plan:build:phr
pnpm build:phr
pnpm plan:package:phr
pnpm package:phr
pnpm plan:deploy:local:phr
pnpm deploy:local:phr
pnpm plan:verify:local:phr
pnpm verify:local:phr
pnpm check:phr-lifecycle-readiness
pnpm check:production-readiness
pnpm check:security-workflow-coverage
```

PHR is rollout-ready for local pilot only when:

- backend and web surfaces build/test
- healthcare gates execute and fail closed
- local deploy/verify works or fails with actionable `NOT_READY`
- evidence pack is generated
- no product lifecycle runner exists
- no healthcare domain behavior leaks into platform packages

---

## Phase 5 — Digital Marketing rollout readiness

### DM-5.1 Business workflow correctness

Required review areas:

- campaign lifecycle
- workspace/tenant boundaries
- route entitlement
- approval workflow
- audit and consent/suppression handling
- connector readiness/degraded behavior
- data minimization
- local deployment health
- UI state correctness

Required tests:

- campaign create/update/approve/reject cases
- workspace/tenant isolation
- connector missing credentials returns degraded/NOT_READY
- consent/suppression enforcement
- route access denied server-side and UI-side
- audit events emitted for important workflow changes
- backend health and web health checks pass

Validation:

```bash
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:dmos-boundary-workflow-coverage
pnpm check:digital-marketing-root-docs
./gradlew :products:digital-marketing:dm-api:check
./gradlew :products:digital-marketing:dm-integration-tests:check
pnpm test:digital-marketing
pnpm build:digital-marketing
pnpm validate:digital-marketing
```

### DM-5.2 Digital Marketing no-regression gate

Run before merge:

```bash
pnpm plan:validate:digital-marketing
pnpm validate:digital-marketing
pnpm plan:test:digital-marketing
pnpm test:digital-marketing
pnpm plan:build:digital-marketing
pnpm build:digital-marketing
pnpm plan:package:digital-marketing
pnpm package:digital-marketing
pnpm plan:deploy:local:digital-marketing
pnpm deploy:local:digital-marketing
pnpm plan:verify:local:digital-marketing
pnpm verify:local:digital-marketing
pnpm check:digital-marketing-lifecycle-pilot
pnpm check:bridge-compliance
pnpm check:production-readiness
```

Digital Marketing is rollout-ready for local pilot only when:

- backend and web surfaces build/test
- bridge compliance passes
- lifecycle phases generate manifests
- local deploy/verify works or fails with actionable `NOT_READY`
- campaign workflow tests pass
- no Digital Marketing-specific lifecycle code leaks into Kernel

---

## 6. Cross-pilot acceptance matrix

| Requirement                  | PHR                     | Digital Marketing                   | Required validation                       |
| ---------------------------- | ----------------------- | ----------------------------------- | ----------------------------------------- |
| Registry enabled             | yes                     | yes                                 | `pnpm check:product-registry`             |
| Lifecycle execution allowed  | yes                     | yes                                 | `pnpm check:product-registry-drift`       |
| Backend surface              | `products/phr`          | `products/digital-marketing/dm-api` | build/test commands                       |
| Web surface                  | `products/phr/apps/web` | `products/digital-marketing/ui`     | typecheck/test/build                      |
| Required gates               | healthcare gates        | marketing/security/bridge gates     | validate commands                         |
| Artifact manifest            | required                | required                            | `pnpm check:product-artifact-contracts`   |
| Deployment manifest          | required                | required                            | `pnpm check:product-deployment-contracts` |
| Health report                | required                | required                            | verify commands                           |
| Runtime truth evidence       | required                | required                            | `pnpm check:kernel-lifecycle-truth`       |
| Product-specific correctness | healthcare/FHIR/consent | campaign/tenant/connector           | product tests                             |
| Boundary safety              | no platform leakage     | no platform leakage                 | boundary checks                           |

---

## 7. Final independent execution checklist

A product-pilot team can execute this plan without owning platform internals by running:

```bash
git checkout 04f03168e597cca638110f0025bd6231ac636fe5
pnpm install --frozen-lockfile
pnpm check:product-registry
pnpm check:product-registry-artifacts
pnpm check:product-workspace-registration
pnpm check:phr-lifecycle-readiness
pnpm check:digital-marketing-lifecycle-pilot
pnpm plan:validate:phr && pnpm validate:phr
pnpm plan:test:phr && pnpm test:phr
pnpm plan:build:phr && pnpm build:phr
pnpm plan:validate:digital-marketing && pnpm validate:digital-marketing
pnpm plan:test:digital-marketing && pnpm test:digital-marketing
pnpm plan:build:digital-marketing && pnpm build:digital-marketing
pnpm check:bridge-compliance
pnpm check:route-entitlement-contracts
pnpm check:design-system-conformance
pnpm check:production-readiness
```

For full local lifecycle proof, also run package/deploy/verify commands for both products after local Docker/Compose prerequisites are available.

---

## 8. Final acceptance criteria

The PHR + Digital Marketing pilot plan is complete when:

1. both products validate, test, build, package, deploy local, and verify local through Kernel commands
2. PHR healthcare gates are active and fail closed
3. Digital Marketing bridge and campaign workflow gates pass
4. both products generate lifecycle evidence packs
5. both products use platform/shared UI primitives where appropriate
6. both products preserve domain ownership and do not leak product logic into platform packages
7. Studio/YAPPC can consume public lifecycle truth without private log parsing
8. Data Cloud bridge can accept product lifecycle evidence through provider contracts when enabled
9. all product no-regression gates pass
10. failures are actionable, observable, and test-covered
