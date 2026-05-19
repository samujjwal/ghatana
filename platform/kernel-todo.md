# Ghatana Five-Phase Platform Implementation Plan

**Repository:** `samujjwal/ghatana`  
**Target commit head:** `f9e7f49d6eed70cb6d2c0fa71b1013d42e2e6af3`  
**Plan mode:** granular, prescriptive, coherent, independently executable  
**Opening pilots:** `digital-marketing` and `phr`

---

## Implementation Progress

| Task | Status | Completed |
|------|--------|-----------|
| **Phase 1 — Establish governance truth** | | |
| Task 1.1 Canonicalize domain map | ✅ COMPLETE | prior session |
| Task 1.2 Validate product registry against generated includes | ✅ COMPLETE | prior session |
| Task 1.3 Split lifecycle pilot checks | ✅ COMPLETE | prior session |
| Task 1.4 Boundary, duplication, and package governance | ✅ COMPLETE | 2026-05-26 |
| Task 1.5 CI integration | ✅ COMPLETE | 2026-05-26 |
| **Phase 2 — Stabilize Kernel lifecycle foundation** | | |
| Task 2.1 Harden `@ghatana/kernel-product-contracts` | ✅ COMPLETE | 2026-05-26 |
| Task 2.2 Harden lifecycle planning | ✅ COMPLETE | 2026-05-26 |
| Task 2.3 Harden lifecycle execution and result collection | ✅ COMPLETE | 2026-05-26 |
| Task 2.4 Harden toolchain adapters | ✅ COMPLETE | 2026-05-26 |
| Task 2.5 Gate provider and gate result manifests | ✅ COMPLETE | 2026-05-26 |
| Task 2.6 UI-facing lifecycle summaries | ✅ COMPLETE | 2026-05-26 |
| **Phase 3 — Stabilize shared UI/intelligence primitives** | | |
| Task 3.1 Canvas hardening | ✅ COMPLETE | 2026-05-26 |
| Task 3.2 UI Builder hardening | ✅ COMPLETE | 2026-05-27 |
| Task 3.3 Design system, registry, and generator | ✅ COMPLETE | 2026-05-27 |
| Task 3.4 Ghatana Studio pilot lifecycle UI | ✅ DONE | 2026-05-27 |
| **Phase 4 — Wire runtime truth and provenance** | | |
| Task 4.1 Provider interfaces | ✅ COMPLETE | 2026-05-19 |
| Task 4.2 Lifecycle event schemas | ✅ COMPLETE | 2026-05-19 |
| Task 4.3 Artifact and deployment provenance | ✅ COMPLETE | 2026-05-19 |
| Task 4.4 Runtime truth read models and Studio integration | ✅ COMPLETE | 2026-05-19 |
| Task 4.5 Agent action evidence | ✅ COMPLETE | 2026-05-19 |
| **Phase 5 — Expand product validation** | | |
| Task 5.1 Digital Marketing validation | ✅ COMPLETE | 2026-05-19 |
| Task 5.2 PHR validation | ✅ COMPLETE | 2026-05-19 |
| Task 5.3 Finance shape validation | ✅ COMPLETE | 2026-05-19 |
| Task 5.4 FlashIt shape validation | ✅ COMPLETE | 2026-05-19 |
| Task 5.5 YAPPC platform-provider validation | ✅ COMPLETE | 2026-05-19 |
| Task 5.6 Data Cloud platform-provider validation | ✅ COMPLETE | 2026-05-19 |

> Last updated: 2026-05-27 — All 26 tasks across all 5 phases are COMPLETE. Build health: fixed 5 `@doc.*` tag violations (checkDocTags passes for 6705 files); fixed Checkstyle StackOverflow crash on `LakehouseConnector.java` (simplified `{@code}` Javadoc block, fixed query-method indentation, added `-Xss4m` to Gradle daemon JVM args in `gradle.properties`). Full validation: all governance scripts pass, all lifecycle dry-runs generate valid manifests, all TypeScript package tests pass (123 ghatana-studio + 214 kernel-product-contracts + 133 kernel-providers + 415 kernel-lifecycle + 48 kernel-artifacts + 94 kernel-deployment + 64 kernel-release + 107 platform-events + 45 events), all Java Gradle tests BUILD SUCCESSFUL.

---

This plan implements the five phases defined in `ghatana_domain_workstream_map.md`:

1. Establish governance truth.
2. Stabilize Kernel lifecycle foundation.
3. Stabilize shared UI/intelligence primitives.
4. Wire runtime truth and provenance.
5. Expand product validation.

The plan must be executed from the target commit snapshot, not from prior TODOs or stale architecture claims.

---

## 0. Target-commit baseline

### 0.1 Repository anchors

Use these current files as source-of-truth anchors:

```text
settings.gradle.kts
config/generated/settings-gradle-includes.kts
pnpm-workspace.yaml
config/canonical-product-registry.json
config/domain-registry.json
scripts/kernel-product.mjs
platform/typescript/kernel-product-contracts
platform/typescript/kernel-lifecycle
platform/typescript/kernel-providers
platform/typescript/kernel-toolchains
platform/typescript/kernel-artifacts
platform/typescript/kernel-deployment
platform/typescript/kernel-release
platform/typescript/canvas
platform/typescript/ui-builder
platform/typescript/ds-schema
platform/typescript/ds-registry
platform/typescript/ds-generator
platform/typescript/ghatana-studio
products/digital-marketing
products/phr
products/data-cloud
products/yappc
```

### 0.2 Important current-state correction

The older domain workstream map identified Digital Marketing as the executable pilot and PHR as a shape validator. At the target commit, `config/canonical-product-registry.json` and `products/phr/kernel-product.yaml` show PHR is now enabled as a second parallel lifecycle pilot.

Therefore, implementation must treat:

```text
digital-marketing = enabled opening pilot
phr = enabled opening pilot
finance = planned/disabled shape validator
flashit = planned/disabled shape validator
data-cloud = platform-provider validator
yappc = platform-provider validator
```

### 0.3 Immediate consistency defect to fix first

`scripts/check-digital-marketing-lifecycle-pilot.mjs` currently contains a Digital-Marketing-only global exclusivity rule: it fails when another product is lifecycle-enabled. That is now inconsistent with PHR being enabled. Fix this before hardening any other checks.

Required refactor:

```text
scripts/check-opening-lifecycle-pilots.mjs
  - validates enabled lifecycle pilot set = [digital-marketing, phr]

scripts/check-digital-marketing-lifecycle-pilot.mjs
  - validates only Digital Marketing-specific pilot requirements

scripts/check-phr-lifecycle-pilot.mjs
  - validates only PHR-specific healthcare pilot requirements
```

---

## 1. Universal execution rules

### 1.1 Before editing any file

Run or perform equivalent inspection:

```bash
git checkout f9e7f49d6eed70cb6d2c0fa71b1013d42e2e6af3
git status --short
find platform/typescript -maxdepth 2 -name package.json | sort
find platform/java -maxdepth 2 -name build.gradle.kts | sort
find products/digital-marketing -maxdepth 4 -type f | sort
find products/phr -maxdepth 5 -type f | sort
```

### 1.2 No-regression implementation rules

Do not introduce:

```text
- product-specific behavior in platform packages
- Kernel imports from products/yappc implementation internals
- Kernel imports from products/data-cloud/planes internals
- product-local lifecycle runners
- duplicate gate engines
- duplicate artifact/deployment manifest schemas
- deprecated @ghatana package imports
- TODO/FIXME in production code
- fake-success adapters
- object-literal tests
- tests that import no production code
- hardcoded secrets or unsafe env defaults
```

### 1.3 Required PR evidence

Every PR must include:

```text
- target commit inspected
- source-of-truth files inspected
- changed files
- existing patterns reused
- boundary impact
- tests added/updated
- commands run
- regression risk
- rollback/revert notes
- current-state vs target-state classification
```

---

# Phase 1 — Establish governance truth

## Objective

Create one executable governance truth layer across domain registry, product registry, generated Gradle includes, pnpm workspace, architecture docs, and validation checks.

## Exit criteria

```text
- docs/architecture/DOMAIN_WORKSTREAM_MAP.md is canonical.
- config/domain-registry.json represents real modules and products at the target commit.
- config/canonical-product-registry.json validates against generated Gradle and pnpm workspace includes.
- Digital Marketing and PHR are the explicit opening lifecycle pilots.
- Finance and FlashIt remain disabled/fail-closed.
- Data Cloud and YAPPC remain platform-provider validators.
- Current-state vs target-state claims are checked.
- Deprecated package imports are checked.
- Product/platform boundaries are checked.
- Digital Marketing-specific checks no longer reject PHR.
```

## Task 1.1 Canonicalize domain map

### Files

```text
docs/architecture/DOMAIN_WORKSTREAM_MAP.md
config/domain-registry.json
.github/copilot-instructions.md
platform/typescript/LIBRARY_GOVERNANCE.md
```

### Steps

1. Confirm `docs/architecture/DOMAIN_WORKSTREAM_MAP.md` exists.
2. Ensure it lists all 17 domains.
3. Ensure each domain entry has:
   ```text
   id, name, ownerLayer, classification, primaryLocations, secondaryLocations,
   sourceOfTruth, allowedConsumers, forbiddenDependencies, requiredChecks,
   independentExecutionChecks, fullRegressionChecks, productAssociations,
   boundaryPolicy, phase, journey, exitCriteria, blockingGaps, evidenceRequired
   ```
4. Classify each domain as exactly one of:
   ```text
   existing-executable, existing-partial, declared-only, target-architecture, anti-pattern
   ```
5. Add explicit pilot association:
   ```text
   product-development-kernel-lifecycle -> digital-marketing, phr
   product-domain-packs -> digital-marketing, phr, finance, flashit
   security-privacy-policy -> phr, digital-marketing
   observability-health -> phr, digital-marketing
   ```

### Validation

```bash
node scripts/validate-domain-registry.mjs
node scripts/check-doc-authority.mjs
node scripts/check-current-state-claims.mjs
```

### Tests to add

```text
scripts/__tests__/validate-domain-registry.test.mjs
scripts/__tests__/check-doc-authority.test.mjs
scripts/__tests__/check-current-state-claims.test.mjs
```

Minimum assertions:

```text
- rejects unknown classification
- rejects missing sourceOfTruth
- rejects non-existing primary location unless target-architecture
- accepts Digital Marketing and PHR as opening pilots
- rejects product runtime ownership inside Platform Coherence & Governance
```

---

## Task 1.2 Validate product registry against generated includes

### Files

```text
config/canonical-product-registry.json
config/generated/settings-gradle-includes.kts
pnpm-workspace.yaml
scripts/generate-product-registry-artifacts.mjs
scripts/validate-product-registry.mjs
scripts/check-product-generated-includes.mjs
```

### Steps

1. Validate every product Gradle module in the registry is present in `config/generated/settings-gradle-includes.kts`.
2. Validate every product pnpm package is present in `pnpm-workspace.yaml`.
3. For lifecycle-enabled products, enforce:
   ```text
   lifecycleStatus = enabled
   lifecycle.enabled = true
   lifecycleExecutionAllowed = true
   lifecycleConfigPath exists
   surfaces exist
   toolchain adapters exist
   artifacts exist
   deployment targets exist
   environments exist
   ```
4. For lifecycle-disabled products, enforce:
   ```text
   lifecycleExecutionAllowed = false
   reasonCodes exist
   requiredGates exist where applicable
   nextRequiredWork exists
   evidenceRefs exist
   no safe-by-default use of partial adapters
   ```

### Validation

```bash
node scripts/generate-product-registry-artifacts.mjs --check
node scripts/validate-product-registry.mjs
node scripts/check-product-generated-includes.mjs
```

### Tests to add

```text
- PHR backend-api/web surfaces validate.
- Digital Marketing backend-api/web surfaces validate.
- Finance remains lifecycleExecutionAllowed=false.
- FlashIt remains lifecycleExecutionAllowed=false.
- Data Cloud and YAPPC are platform-provider products.
- Generated Gradle includes match registry modules.
- pnpm workspace includes match registry pnpm packages.
```

---

## Task 1.3 Split lifecycle pilot checks

### Files

```text
scripts/check-opening-lifecycle-pilots.mjs
scripts/check-digital-marketing-lifecycle-pilot.mjs
scripts/check-phr-lifecycle-pilot.mjs
scripts/check-kernel-platform-lifecycle.mjs
```

### Steps

1. Add `scripts/check-opening-lifecycle-pilots.mjs`.
2. Move global enabled-product logic into that script.
3. Update `scripts/check-digital-marketing-lifecycle-pilot.mjs` so it validates only Digital Marketing-specific requirements.
4. Add `scripts/check-phr-lifecycle-pilot.mjs`.
5. Update `scripts/check-kernel-platform-lifecycle.mjs` to call all three.

### `check-opening-lifecycle-pilots.mjs` must validate

```text
- enabled lifecycle products exactly equal [digital-marketing, phr]
- both have lifecycleExecutionAllowed=true
- both have valid kernel-product.yaml
- both can plan dev, validate, test, build, package, deploy, verify
- Finance and FlashIt fail closed by default
- Data Cloud and YAPPC are not treated as ordinary lifecycle products
```

### `check-digital-marketing-lifecycle-pilot.mjs` must validate

```text
- lifecycleStatus enabled
- lifecycleExecutionAllowed true
- metadata.pilot true
- standard-web-api-product profile
- backend-api surface with gradle-java-service adapter
- web surface with pnpm-vite-react adapter
- bridge adapter evidence exists
- required manifests by phase exist
- package phase uses container artifacts
- deploy phase uses compose-local
- health endpoints are declared
- env example has no unsafe secrets
- gates include bridge, consent boundary, data minimization, typecheck, a11y, i18n, bundle budget
```

### `check-phr-lifecycle-pilot.mjs` must validate

```text
- lifecycleStatus enabled
- lifecycleExecutionAllowed true
- status/executionEnabled true in products/phr/kernel-product.yaml
- backend-api surface with gradle-java-service adapter
- web surface with pnpm-vite-react adapter
- required healthcare gates:
  consent
  pii-classification
  audit-evidence
  fhir-contract-validation
  tenant-data-sovereignty
- gate pack files exist
- readiness evidence exists
- schema registry exists
- local compose target exists
- health endpoints are declared
- required build/deploy manifests are declared
- no PHR domain code appears in platform Kernel packages
```

### Validation

```bash
node scripts/check-opening-lifecycle-pilots.mjs
node scripts/check-digital-marketing-lifecycle-pilot.mjs
node scripts/check-phr-lifecycle-pilot.mjs
node scripts/check-kernel-platform-lifecycle.mjs
```

---

## Task 1.4 Boundary, duplication, and package governance

### Files

```text
scripts/check-domain-boundaries.mjs
scripts/check-deprecated-imports.mjs
scripts/check-package-registry.mjs
scripts/check-duplicate-platform-capabilities.mjs
config/duplication-exception-registry.json
platform/typescript/.dependency-cruiser.cjs
```

### Steps

1. Fail platform packages importing `products/**`.
2. Fail Kernel packages importing YAPPC or Data Cloud internals.
3. Fail product-local generic lifecycle runners.
4. Fail product-local generic gate engines.
5. Fail duplicate artifact/deployment/health schemas.
6. Fail deprecated imports:
   ```text
   @ghatana/ui
   @ghatana/utils
   @ghatana/accessibility-audit
   @ghatana/canvas-core
   @ghatana/canvas-react
   @ghatana/canvas-plugins
   @ghatana/canvas-tools
   @ghatana/canvas-chrome
   ```
7. Add duplication exception registry with:
   ```text
   id, owner, category, affectedPaths, reason, severity, expiryDate, removalPlan, validationCheck
   ```

### Validation

```bash
node scripts/check-domain-boundaries.mjs
node scripts/check-deprecated-imports.mjs
node scripts/check-package-registry.mjs
node scripts/check-duplicate-platform-capabilities.mjs
```

---

## Task 1.5 CI integration

### Steps

Add a governance CI job for changes under:

```text
config/**
docs/**
scripts/**
platform/**
products/**
settings.gradle.kts
pnpm-workspace.yaml
```

Run:

```bash
pnpm install --frozen-lockfile
node scripts/validate-domain-registry.mjs
node scripts/validate-product-registry.mjs
node scripts/check-product-generated-includes.mjs
node scripts/check-doc-authority.mjs
node scripts/check-current-state-claims.mjs
node scripts/check-domain-boundaries.mjs
node scripts/check-deprecated-imports.mjs
node scripts/check-opening-lifecycle-pilots.mjs
```

---

# Phase 2 — Stabilize Kernel lifecycle foundation

## Objective

Make Product Development Kernel executable, deterministic, contract-backed, observable, and safe for both opening pilots.

## Exit criteria

```text
- Kernel contracts cover ProductUnit, lifecycle, gates, artifacts, deployments, health, events, approvals, and agentic actions.
- Kernel lifecycle validates registry/config/manifests before execution.
- Digital Marketing and PHR can plan and dry-run dev, validate, test, build, package, deploy, verify.
- Disabled products fail closed.
- Required manifests are produced or execution fails.
- Gate failures are structured.
- Correlation IDs are propagated.
- No product-specific implementation code exists inside Kernel packages.
```

---

## Task 2.1 Harden `@ghatana/kernel-product-contracts`

### Files

```text
platform/typescript/kernel-product-contracts/src/**
platform/typescript/kernel-product-contracts/README.md
```

### Add/harden schemas

```text
ProductUnit
ProductUnitSurface
ProductUnitKind
ProductUnitIntent
LifecyclePhase
LifecyclePlan
LifecyclePlanStep
LifecycleExecutionContext
LifecycleResult
LifecycleStepResult
LifecycleFailure
LifecycleEvent
GateDefinition
GateResult
GateResultManifest
ArtifactReference
DeploymentReference
EnvironmentReference
HealthSnapshot
ApprovalRequirement
AgentLifecycleActionRequest
SemanticArtifactReference
```

### Required fields

Execution-related contracts must include:

```text
schemaVersion
productUnitId
phase
runId
correlationId
sourceRef
createdAt/emittedAt
status
reasonCode
actionableMessage
evidenceRefs
diagnostics
```

### Validation

```bash
pnpm --dir platform/typescript/kernel-product-contracts typecheck
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-product-contracts build
```

### Tests

```text
- Digital Marketing fixture parses.
- PHR fixture parses.
- Finance disabled fixture parses but is not executable.
- invalid lifecycle phase fails.
- missing productUnitId fails.
- high-risk AgentLifecycleActionRequest requires approval metadata.
```

---

## Task 2.2 Harden lifecycle planning

### Files

```text
platform/typescript/kernel-lifecycle/src/**
scripts/kernel-product.mjs
products/digital-marketing/kernel-product.yaml
products/phr/kernel-product.yaml
```

### Plan validation must check

```text
product exists
execution allowed
phase is valid
phase exists in manifest
surfaces exist
adapters exist
gates exist
required manifests exist
default environment exists for deploy/verify/promote/rollback
```

### Plan output must include

```text
productUnitId
phase
surfaces
environment
mode
ordered steps
adapter ids
gates
requiredManifests
expectedArtifacts
health checks
approval requirements
correlationId
warnings
blockingReasons
```

### Validation

```bash
pnpm --dir platform/typescript/kernel-lifecycle typecheck
pnpm --dir platform/typescript/kernel-lifecycle test
node scripts/kernel-product.mjs product plan digital-marketing validate --json
node scripts/kernel-product.mjs product plan phr validate --json
node scripts/kernel-product.mjs product plan finance validate --json
```

Expected: Finance fails closed unless explicit shape-validation mode exists.

---

## Task 2.3 Harden lifecycle execution and result collection

### Files

```text
platform/typescript/kernel-lifecycle/src/service/**
platform/typescript/kernel-lifecycle/src/execution/**
platform/typescript/kernel-lifecycle/src/result/**
platform/typescript/kernel-lifecycle/src/events/**
```

### Execution rules

```text
- execution is plan-driven
- each step produces LifecycleStepResult
- failures stop downstream risky phases
- missing required manifest fails execution
- every run has correlationId
- every run records sourceRef
- no success is inferred from unstructured logs alone
```

### Required output files by phase where applicable

```text
lifecycle-plan.json
lifecycle-result.json
gate-result-manifest.json
artifact-manifest.json
deployment-manifest.json
verify-health-report.json
rollback-manifest.json
lifecycle-health-snapshot.json
lifecycle-events.json
```

### Validation

```bash
node scripts/kernel-product.mjs product validate digital-marketing --dry-run --json --output-dir .kernel-runs/digital-marketing/validate
node scripts/kernel-product.mjs product validate phr --dry-run --json --output-dir .kernel-runs/phr/validate
node scripts/check-lifecycle-result-manifests.mjs .kernel-runs/digital-marketing/validate
node scripts/check-lifecycle-result-manifests.mjs .kernel-runs/phr/validate
```

---

## Task 2.4 Harden toolchain adapters

### Files

```text
platform/typescript/kernel-toolchains/src/**
config/command-registry-manifest.json
scripts/check-toolchain-adapter-contracts.mjs
```

### Adapter metadata must include

```text
adapterId
supportedPhases
supportedSurfaceTypes
safeForDefault
requiresApproval
expectedOutputs
timeout
retryPolicy
environmentPolicy
outputValidation
tests
```

### Initial production-safe adapters

```text
gradle-java-service
pnpm-vite-react
compose-local
```

### Not safe by default until proven

```text
xcode-ios
gradle-android
kubernetes
helm
terraform
```

### Validation

```bash
pnpm --dir platform/typescript/kernel-toolchains typecheck
pnpm --dir platform/typescript/kernel-toolchains test
node scripts/check-toolchain-adapter-contracts.mjs
node scripts/check-toolchain-adapter-registry-schema.mjs
```

---

## Task 2.5 Gate provider and gate result manifests

### Files

```text
platform/typescript/kernel-product-contracts/src/gates/**
platform/typescript/kernel-lifecycle/src/gates/**
platform-plugins/plugin-consent
platform-plugins/plugin-compliance
platform-plugins/plugin-audit-trail
platform-plugins/core-observability
products/phr/lifecycle/gate-packs/**
products/digital-marketing/kernel-product.yaml
```

### Implement generic contracts

```text
GateProvider
GateExecutionRequest
GateExecutionResult
GateResultManifest
```

### Required initial providers/configurations

```text
registry-validation
manifest-validation
lifecycle-contract-validation
bridge-compliance
consent
pii-classification
audit-evidence
fhir-contract-validation
tenant-data-sovereignty
marketing-consent-boundary
non-regulated-customer-data-minimization
unit-test-coverage
integration-test-coverage
contract-test-coverage
```

### Validation

```bash
node scripts/check-gate-result-manifest-completeness.mjs
node scripts/check-phr-lifecycle-pilot.mjs
node scripts/check-digital-marketing-lifecycle-pilot.mjs
```

---

## Task 2.6 UI-facing lifecycle summaries

### Files

```text
platform/typescript/kernel-product-contracts/src/ui-summary/**
platform/typescript/ghatana-studio/src/api/kernelLifecycleClient.ts
platform/typescript/ghatana-studio/src/sections/**
```

### Add contracts

```text
LifecycleRunSummary
LifecycleGateSummary
LifecycleArtifactSummary
LifecycleDeploymentSummary
LifecycleHealthSummary
```

### Rule

Studio must consume summaries and public lifecycle truth only. It must not parse stdout, private logs, or product implementation files.

### Validation

```bash
pnpm --dir platform/typescript/ghatana-studio type-check
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio test:e2e
```

---

# Phase 3 — Stabilize shared UI/intelligence primitives

## Objective

Harden shared primitives used by Studio, YAPPC, Kernel, Data Cloud, Digital Marketing, and PHR without making shared packages product-specific.

## Exit criteria

```text
- @ghatana/canvas public API is stable and product-neutral.
- @ghatana/ui-builder BuilderDocument v1 and preview protocol are stable.
- @ghatana/ds-schema and @ghatana/ds-registry are authoritative.
- @ghatana/ds-generator is ready for token, docs, examples, and builder binding generation.
- YAPPC Java BuilderDocument schema aligns with TypeScript schema.
- Studio uses shared lifecycle/gate/artifact/health summaries.
- PHR and Digital Marketing use canonical design-system patterns where appropriate.
```

## Task 3.1 Canvas hardening ✅ COMPLETE (2026-05-26)

### Files

```text
platform/typescript/canvas/package.json
platform/typescript/canvas/src/public/index.ts
platform/typescript/canvas/src/types/**
platform/typescript/canvas/src/plugins/**
platform/typescript/canvas/src/hybrid/**
platform/typescript/canvas/src/telemetry/**
```

### Steps

1. Keep `src/public/index.ts` as the public API boundary.
2. Add import boundary tests to prevent products importing internal source paths.
3. Add semantic zoom types:
   ```text
   SemanticZoomLevel
   ContextShiftPolicy
   FocusPath
   DetailLevel
   ```
4. Add diagram presets:
   ```text
   lifecycle-plan, dependency-graph, topology, provenance-graph, gate-flow
   ```
5. Add keyboard graph navigation tests.
6. Add performance fixture for large lifecycle graphs.

### Validation

```bash
pnpm --dir platform/typescript/canvas type-check
pnpm --dir platform/typescript/canvas test
pnpm --dir platform/typescript/canvas build
```

---

## Task 3.2 UI Builder hardening

### Files

```text
platform/typescript/ui-builder/src/**
platform/typescript/ui-builder/src/schema/builder-document-v1.schema.json
products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/api/BuilderDocumentSchema.java
platform/typescript/testing/src/builder-preview.ts
```

### Steps

1. Make BuilderDocument v1 explicit.
2. Export JSON schema.
3. Add migrations for schema versions.
4. Align Java and TypeScript schema.
5. Add preview protocol:
   ```text
   PreviewRequest
   PreviewResult
   PreviewSecurityDecision
   PreviewDiagnostics
   PreviewArtifactReference
   ```
6. Add codegen golden tests.
7. Add import/export validation.

### Validation

```bash
pnpm --dir platform/typescript/ui-builder type-check
pnpm --dir platform/typescript/ui-builder test
pnpm --dir platform/typescript/ui-builder build
./gradlew :products:yappc:core:yappc-services:test
```

---

## Task 3.3 Design system, registry, and generator

### Files

```text
platform/typescript/ds-schema
platform/typescript/ds-registry
platform/typescript/ds-generator
platform/typescript/design-system
platform/typescript/tokens
platform/typescript/theme
platform/typescript/domain-components
platform/typescript/accessibility
```

### Steps

1. Make `ds-schema` authoritative for component metadata.
2. Make `ds-registry` authoritative for builder-visible components.
3. Extend `ds-generator` toward:
   ```text
   token output
   component docs
   examples
   builder bindings
   a11y metadata
   i18n metadata
   test scaffold
   ```
4. Create reusable lifecycle/status components only after two consumers exist:
   ```text
   LifecycleStatusBadge
   GateResultPanel
   ArtifactSummaryCard
   DeploymentHealthCard
   ApprovalRequirementCard
   EvidenceReferenceList
   BlockedState
   DegradedState
   PrivacyWarning
   SecurityWarning
   ```

### Validation

```bash
pnpm --dir platform/typescript/ds-schema type-check
pnpm --dir platform/typescript/ds-registry type-check
pnpm --dir platform/typescript/ds-generator test
pnpm --dir platform/typescript/design-system test
pnpm --dir platform/typescript/accessibility test
```

---

## Task 3.4 Ghatana Studio pilot lifecycle UI

### Files

```text
platform/typescript/ghatana-studio/src/**
```

### Steps

1. Add opening pilot selector:
   ```text
   Digital Marketing
   PHR
   ```
2. Add lifecycle views:
   ```text
   plan
   run result
   gate result
   artifact manifest
   deployment manifest
   health snapshot
   approval requirements
   failure diagnostics
   ```
3. Add route ownership:
   ```text
   Develop/Lifecycle/Artifacts/Deployments = Kernel
   Health = composed Kernel + Data Cloud + product truth
   Ideas/Blueprints/Canvas/Learn = YAPPC
   ```
4. Add empty/loading/error/degraded/blocked states.
5. Add a11y and keyboard tests.

### Validation

```bash
pnpm --dir platform/typescript/ghatana-studio type-check
pnpm --dir platform/typescript/ghatana-studio lint
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio test:a11y
pnpm --dir platform/typescript/ghatana-studio test:e2e
```

---

# Phase 4 — Wire runtime truth and provenance

## Objective

Make lifecycle truth durable, queryable, provenance-backed, and visualizable while preserving Kernel bootstrap mode.

## Exit criteria

```text
- Kernel can run in bootstrap mode without Data Cloud.
- Provider interfaces exist for event, artifact, health, provenance, telemetry, policy evidence, memory, knowledge, runtime truth.
- Data Cloud-backed implementations live under products/data-cloud/*/kernel-bridge only.
- Lifecycle events, artifact references, health snapshots, and gate evidence can be stored.
- Studio can show lifecycle truth without log parsing.
- Agent actions have evidence and provenance references.
```

## Task 4.1 Provider interfaces

### Files

```text
platform/typescript/kernel-product-contracts/src/providers/**
platform/typescript/kernel-providers/src/**
products/data-cloud/extensions/kernel-bridge/**
products/data-cloud/planes/action/kernel-bridge/**
```

### Contracts

```text
EventProvider
ArtifactProvider
HealthProvider
ProvenanceProvider
TelemetryProvider
PolicyEvidenceProvider
RuntimeTruthProvider
MemoryProvider
KnowledgeProvider
```

### Rules

```text
platform owns interface
platform may own bootstrap/file implementation
Data Cloud owns Data Cloud-backed implementation
Kernel must not import Data Cloud plane internals
provider failures must be visible and fail-safe
```

### Validation

```bash
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-providers test
./gradlew :products:data-cloud:extensions:kernel-bridge:test
./gradlew :products:data-cloud:planes:action:kernel-bridge:test
node scripts/check-kernel-data-cloud-boundary.mjs
```

---

## Task 4.2 Lifecycle event schemas

### Files

```text
platform/typescript/platform-events
platform/typescript/events
platform/typescript/kernel-product-contracts/src/events/**
platform/typescript/kernel-lifecycle/src/events/**
products/data-cloud/planes/event/core
products/data-cloud/planes/event/store
products/data-cloud/planes/action/event-bridge
```

### Event envelope

```text
schemaVersion
eventId
eventType
productUnitId
phase
runId
correlationId
sourceRef
emittedAt
actor
tenant/workspace/project
payload
evidenceRefs
idempotencyKey
```

### Required events

```text
lifecycle.plan.created
lifecycle.run.started
lifecycle.step.started
lifecycle.step.completed
lifecycle.step.failed
lifecycle.gate.completed
lifecycle.artifact.created
lifecycle.deployment.created
lifecycle.health.snapshot.created
lifecycle.run.completed
lifecycle.run.failed
lifecycle.approval.required
lifecycle.rollback.ready
```

### Validation

```bash
pnpm --dir platform/typescript/platform-events test
pnpm --dir platform/typescript/events test
pnpm --dir platform/typescript/kernel-lifecycle test
./gradlew :products:data-cloud:planes:event:core:test
./gradlew :products:data-cloud:planes:event:store:test
```

---

## Task 4.3 Artifact and deployment provenance

### Files

```text
platform/typescript/kernel-artifacts/src/**
platform/typescript/kernel-deployment/src/**
platform/typescript/kernel-lifecycle/src/**
products/data-cloud/extensions/kernel-bridge/**
```

### Artifact manifest must include

```text
artifactId
artifactType
packaging
sourceRef
buildRunId
digest/fingerprint
producedBy
producedAt
productUnitId
surfaceId
evidenceRefs
```

### Deployment manifest must include

```text
deploymentId
environment
artifactRefs
artifactDigests
sourceRef
deployedBy
deployedAt
healthCheckRefs
rollbackRef
```

### Validation

```bash
node scripts/check-package-to-deploy-artifact-linkage.mjs
node scripts/check-artifact-manifest-completeness.mjs
node scripts/check-deployment-manifest-completeness.mjs
node scripts/check-rollback-manifest-completeness.mjs
```

---

## Task 4.4 Runtime truth read models and Studio integration

### Files

```text
products/data-cloud/extensions/kernel-bridge/**
platform/typescript/kernel-product-contracts/src/ui-summary/**
platform/typescript/ghatana-studio/src/**
```

### Read models

```text
LifecycleRunReadModel
ProductUnitHealthReadModel
GateResultReadModel
ArtifactLineageReadModel
DeploymentReadModel
ProvenanceReadModel
```

### Studio must show

```text
opening pilot lifecycle status
latest runs
failed gates
produced artifacts
active deployments
health snapshots
provenance links
degraded provider state
```

### Validation

```bash
node scripts/check-runtime-truth-provider-contracts.mjs
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio test:e2e
```

---

## Task 4.5 Agent action evidence

### Files

```text
platform/typescript/kernel-product-contracts/src/agentic/**
products/data-cloud/planes/action/**
```

### Requirements

`AgentLifecycleActionRequest` must include:

```text
actionId
productUnitId
requested phase/action
tool permissions
risk level
approval requirements
expected artifacts
verification proof
rollback plan
evidenceRefs
correlationId
agent identity
```

Agents must not run raw Gradle/pnpm/Docker commands directly.

### Validation

```bash
node scripts/check-agentic-lifecycle-action-contracts.mjs
./gradlew :products:data-cloud:planes:action:agent-runtime:test
./gradlew :products:data-cloud:planes:action:orchestrator:test
```

---

# Phase 5 — Expand product validation

## Objective

Use product shapes to prove Kernel is generic without turning it into a god product.

## Exit criteria

```text
- Digital Marketing remains executable and correct.
- PHR remains executable and healthcare-gate-correct.
- Finance has multi-module/operator/portal/SDK readiness validation but remains disabled.
- FlashIt has mobile/API/privacy/preview shape validation but remains disabled.
- YAPPC has creator lifecycle and artifact intelligence separated from Kernel lifecycle.
- Data Cloud has bootstrap/platform mode and runtime truth provider validation.
- Product shape capability matrix is generated and checked.
```

## Task 5.1 Digital Marketing validation

```bash
node scripts/check-digital-marketing-lifecycle-pilot.mjs
node scripts/kernel-product.mjs product plan digital-marketing validate --json
node scripts/kernel-product.mjs product validate digital-marketing --dry-run --json --output-dir .kernel-runs/digital-marketing/validate
node scripts/kernel-product.mjs product build digital-marketing --dry-run --json --output-dir .kernel-runs/digital-marketing/build
node scripts/kernel-product.mjs product deploy digital-marketing --dry-run --json --env local --output-dir .kernel-runs/digital-marketing/deploy
./gradlew :products:digital-marketing:dm-api:test
./gradlew :products:digital-marketing:dm-kernel-bridge:test
pnpm --dir products/digital-marketing/ui type-check
pnpm --dir products/digital-marketing/ui test
pnpm --dir products/digital-marketing/ui build
```

## Task 5.2 PHR validation

```bash
node scripts/check-phr-lifecycle-pilot.mjs
node scripts/kernel-product.mjs product plan phr validate --json
node scripts/kernel-product.mjs product validate phr --dry-run --json --output-dir .kernel-runs/phr/validate
node scripts/kernel-product.mjs product build phr --dry-run --json --output-dir .kernel-runs/phr/build
node scripts/kernel-product.mjs product deploy phr --dry-run --json --env local --output-dir .kernel-runs/phr/deploy
./gradlew :products:phr:test
./gradlew :products:phr:domains:healthcare:test
pnpm --dir products/phr/apps/web type-check
pnpm --dir products/phr/apps/web test
pnpm --dir products/phr/apps/web build
```

## Task 5.3 Finance shape validation

Keep default lifecycle disabled until blockers resolve.

Validate:

```text
multi-module Gradle graph
operator surface
portal surface
SDK packaging
regulatory-compliance gate
risk-controls gate
promotion-approval gate
multi-module-build gate
domain dependency rules
reporting evidence
```

Commands:

```bash
node scripts/check-product-shape-capability-matrix.mjs --product finance
node scripts/kernel-product.mjs product plan finance build --json
./gradlew :products:finance:build
./gradlew :products:finance:integration-testing:test
```

Expected: Kernel command fails closed unless explicit shape-validation mode exists.

## Task 5.4 FlashIt shape validation

Keep default lifecycle disabled until mobile adapters and mobile artifact contracts are executable.

Validate:

```text
backend API
web
mobile
privacy gate
preview-security gate
personal-data-classification gate
mobile ipa/aab artifact manifests
xcode-ios adapter readiness
gradle-android adapter readiness
```

Commands:

```bash
node scripts/check-product-shape-capability-matrix.mjs --product flashit
node scripts/kernel-product.mjs product plan flashit build --json
```

Expected: Kernel command fails closed unless explicit shape-validation mode exists.

## Task 5.5 YAPPC platform-provider validation

Validate:

```text
creator lifecycle distinct from Kernel lifecycle
ProductUnitIntent export
artifact intelligence evidence contracts
no Kernel lifecycle execution reimplementation
no private Kernel log parsing
public Kernel/Data Cloud truth consumption only
```

Commands:

```bash
node scripts/check-yappc-kernel-boundary.mjs
node scripts/check-yappc-artifact-intelligence-boundary.mjs
./gradlew :products:yappc:core:yappc-api:test
pnpm --dir products/yappc/frontend test
```

## Task 5.6 Data Cloud platform-provider validation

Validate:

```text
bootstrap mode separation
platform mode provider bridge
runtime truth provider
event provider
artifact provider
health provider
provenance provider
policy evidence provider
no product business logic in Data Cloud core
```

Commands:

```bash
node scripts/check-data-cloud-foundation-boundary.mjs
node scripts/check-runtime-truth-provider-contracts.mjs
./gradlew :products:data-cloud:delivery:api:test
./gradlew :products:data-cloud:extensions:kernel-bridge:test
./gradlew :products:data-cloud:planes:action:kernel-bridge:test
```

---

# Full platform no-regression suite

Run before declaring any phase complete:

```bash
# Governance
node scripts/validate-domain-registry.mjs
node scripts/validate-product-registry.mjs
node scripts/check-product-generated-includes.mjs
node scripts/check-doc-authority.mjs
node scripts/check-current-state-claims.mjs
node scripts/check-domain-boundaries.mjs
node scripts/check-deprecated-imports.mjs
node scripts/check-opening-lifecycle-pilots.mjs

# Kernel
pnpm --dir platform/typescript/kernel-product-contracts test
pnpm --dir platform/typescript/kernel-product-contracts typecheck
pnpm --dir platform/typescript/kernel-lifecycle test
pnpm --dir platform/typescript/kernel-lifecycle typecheck
pnpm --dir platform/typescript/kernel-providers test
pnpm --dir platform/typescript/kernel-toolchains test
pnpm --dir platform/typescript/kernel-artifacts test
pnpm --dir platform/typescript/kernel-deployment test
pnpm --dir platform/typescript/kernel-release test
node scripts/check-kernel-platform-lifecycle.mjs

# Shared primitives
pnpm --dir platform/typescript/canvas type-check
pnpm --dir platform/typescript/canvas test
pnpm --dir platform/typescript/ui-builder type-check
pnpm --dir platform/typescript/ui-builder test
pnpm --dir platform/typescript/ds-schema type-check
pnpm --dir platform/typescript/ds-registry type-check
pnpm --dir platform/typescript/ds-generator test
pnpm --dir platform/typescript/ghatana-studio type-check
pnpm --dir platform/typescript/ghatana-studio test
pnpm --dir platform/typescript/ghatana-studio test:e2e

# Opening pilots
node scripts/check-digital-marketing-lifecycle-pilot.mjs
node scripts/check-phr-lifecycle-pilot.mjs
./gradlew :products:digital-marketing:dm-api:test
./gradlew :products:digital-marketing:dm-kernel-bridge:test
pnpm --dir products/digital-marketing/ui type-check
pnpm --dir products/digital-marketing/ui test
./gradlew :products:phr:test
./gradlew :products:phr:domains:healthcare:test
pnpm --dir products/phr/apps/web type-check
pnpm --dir products/phr/apps/web test

# Shape validators
node scripts/check-product-shape-capability-matrix.mjs
node scripts/check-yappc-kernel-boundary.mjs
node scripts/check-data-cloud-foundation-boundary.mjs
```

---

# Final acceptance criteria

This five-phase implementation is complete when:

```text
1. Domain, product, Gradle, and pnpm registries agree.
2. Digital Marketing and PHR are the only opening lifecycle pilots.
3. Digital Marketing and PHR can plan and dry-run lifecycle phases through Kernel.
4. Finance and FlashIt fail closed by default.
5. Kernel lifecycle contracts and outputs are typed, versioned, and test-backed.
6. Every lifecycle phase emits structured truth or fails closed.
7. Shared primitives remain product-neutral.
8. Studio consumes public lifecycle truth only.
9. Data Cloud provider bridge is contract-backed.
10. Agents request lifecycle actions through Kernel contracts.
11. Security, privacy, i18n, a11y, resilience, and observability are included in touched surfaces.
12. No duplicate platform capability is unregistered.
13. No product reimplements Kernel lifecycle.
14. No Kernel/shared package imports product implementation internals.
```
