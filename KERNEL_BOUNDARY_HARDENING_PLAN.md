# Kernel and Platform Plugin Boundary Hardening Plan (Revised)

Last Updated: 2026-05-01
Scope: kernel-dev.md items #1-#60
Strategy: Fix-forward only. No aliases, no compatibility shims, no staged deprecation.

---

## Executive Verdict

This revised plan incorporates production-critical corrections identified during follow-up review:

1. Adds a mandatory Phase 0 for build and API baseline correctness before refactors.
2. Consolidates four boundary resolver artifacts, not only one interface.
3. Replaces narrow map-based boundary loading with a rule-based policy model.
4. Refactors risk plugin toward a generic model engine, not only clinical key removal.
5. Resolves kernel-core versus kernel-plugin plugin API ownership before manifest/schema changes.
6. Wires observability module before telemetry enforcement.
7. Uses kernel-owned bridge ports for authorization/audit/health abstraction.
8. Hardens CI gates across source, resources, docs/examples, and stale generated artifacts.

---

## Confirmed Repository Facts That Drive This Plan

1. Duplicate boundary contracts and implementations exist in both packages:
   - platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/boundary/BoundaryPolicyResolver.java
   - platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/boundary/DefaultBoundaryPolicyResolver.java
   - platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/policy/BoundaryPolicyResolver.java
   - platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/policy/DefaultBoundaryPolicyResolver.java

2. Composite build/internal path wiring needs correction:
   - platform-kernel/kernel-plugin/build.gradle.kts references project(":platform-kernel:kernel-core") instead of project(":kernel-core")
   - platform-kernel/kernel-persistence/build.gradle.kts has the same issue

3. Plugin API is duplicated and split:
   - kernel-core contains com.ghatana.kernel.plugin.PluginManifest
   - kernel-plugin contains com.ghatana.platform.plugin.PluginManifest
   - platform plugins currently extend com.ghatana.platform.plugin.Plugin

4. core-observability is present but not included in plugin composite settings:
   - platform-plugins/settings.gradle.kts includes seven plugin modules only

5. Risk plugin contains product/domain bleed beyond one method:
   - RiskType includes CLINICAL
   - Javadocs mention Trading risk and Clinical risk
   - RiskLimits fields are finance-shaped (position/portfolio/VaR)

---

## Revised Sequencing (Production-Grade)

Phase 0: Build and API baseline
Phase 1: Kernel boundary consolidation
Phase 2: Platform plugin purity
Phase 3: Product pack model and validation
Phase 4: Bridge hardening
Phase 5: Documentation and generated matrices
Phase 6: Strategic backlog

Do not reorder. Each phase unlocks assumptions for the next phase.

---

## Phase 0 - Build and API Baseline (Mandatory)

### 0.1 Composite build and task-path sanity

Objective: establish one reliable command set before source refactors.

Actions:
1. Validate two execution modes and document both:
   - Root mode: ./gradlew <task>
   - Included-build mode: ./gradlew -p platform-kernel <task> and ./gradlew -p platform-plugins <task>
2. Replace ambiguous acceptance criteria that assume :platform-kernel:kernel-core:* always resolves from root.
3. Add command matrix to docs/BUILD.md for platform-kernel and platform-plugins modules.

Acceptance:
1. Command matrix committed.
2. CI executes at least one root-mode and one included-build-mode verification step.

### 0.2 Fix intra-composite project dependency paths

Actions:
1. Update platform-kernel/kernel-plugin/build.gradle.kts:
   - api(project(":platform-kernel:kernel-core")) -> api(project(":kernel-core"))
2. Update platform-kernel/kernel-persistence/build.gradle.kts similarly.
3. Verify all platform-kernel module dependencies use local included-build project paths consistently.

Acceptance:
1. ./gradlew -p platform-kernel :kernel-core:build :kernel-plugin:build :kernel-persistence:build passes.

### 0.3 Canonicalize plugin API ownership (kernel-core versus kernel-plugin)

Objective: decide single source of truth before adding config schema or lifecycle rules.

Decision options:
1. Option A (recommended): kernel-plugin owns plugin API (Plugin, PluginManifest, PluginContext, etc.), kernel-core owns runtime/orchestration and ports only.
2. Option B: kernel-core owns plugin API, kernel-plugin merged/removed.

Actions:
1. ADR decision recorded under docs/adr.
2. Remove duplicate PluginManifest surface from non-canonical module.
3. Update imports in platform plugins and tests to canonical package only.

Acceptance:
1. Exactly one canonical PluginManifest remains in platform-kernel.
2. No duplicate plugin contract classes across kernel-core and kernel-plugin.

### 0.4 Exclude stale generated and compiled artifacts from purity scans

Actions:
1. Define scan exclusions for build, out, bin, target, generated directories.
2. Ensure new scan tasks evaluate only tracked source/resource/doc trees intended for policy checks.

Acceptance:
1. Purity tasks do not report false positives from compiled/generated output.

### 0.5 Establish baseline build gates before refactor

Actions:
1. Record pre-change baseline status for:
   - platform-kernel build/test
   - platform-plugins build/test
2. Save baseline report artifact in build/reports or docs/generated.

Acceptance:
1. Baseline captured and attached to PR implementing Phase 1.

---

## Phase 1 - Kernel Boundary Consolidation

### 1.1 Consolidate four boundary resolver artifacts

Objective: eliminate duplicate contracts and duplicate defaults.

Target files:
1. platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/boundary/BoundaryPolicyResolver.java
2. platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/boundary/DefaultBoundaryPolicyResolver.java
3. platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/policy/BoundaryPolicyResolver.java
4. platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/policy/DefaultBoundaryPolicyResolver.java

Canonical target:
1. Keep com.ghatana.kernel.policy.BoundaryPolicyResolver (richer decision model).
2. Remove boundary package versions after migrations.

Actions:
1. Migrate all call sites (including ScopeBoundaryEnforcer and integration tests) to canonical policy package.
2. Delete duplicate boundary package contract and implementation.
3. Normalize decision shape to include requiredFeatures and decisionMetadata.

Acceptance:
1. No references to com.ghatana.kernel.boundary.BoundaryPolicyResolver remain.
2. No references to com.ghatana.kernel.boundary.DefaultBoundaryPolicyResolver remain.

### 1.2 Replace narrow map-based policy loading with rule model

Problem with prior approach: loadScopeDependencies/loadRegionalRestrictions alone cannot encode resource/action/classification/consent/audit/approval semantics.

New model:
1. BoundaryPolicyStore
2. BoundaryPolicyLoadContext
3. BoundaryPolicyRule
4. BoundaryPolicyEvaluationRequest
5. BoundaryPolicyEvaluationResult
6. BoundaryPolicyRuleParser
7. BoundaryPolicyRuleValidator

Recommended SPI:
1. BoundaryPolicyStore.loadRules(BoundaryPolicyLoadContext context)

BoundaryPolicyRule minimum fields:
1. ruleId
2. sourceScopePattern
3. targetScopePattern
4. resourcePattern
5. actions
6. classificationCondition
7. requiredFeatures
8. requiresConsent
9. requiresAudit
10. effect (ALLOW, DENY, REQUIRE_APPROVAL)
11. metadata

Behavioral requirements:
1. Denial reasons must be explicit and testable.
2. Tenant and region overrides supported via context.
3. Rule parser and validator must fail startup on invalid policy pack input.

Acceptance:
1. DefaultBoundaryPolicyResolver no longer contains product terms or resource-action hardcoding.
2. Rule validation test suite covers malformed and conflicting rule scenarios.

### 1.3 Remove LegacyCapabilityAdapter

Actions:
1. Validate zero usage across repo.
2. Delete file.
3. Replace any remaining imports directly with canonical capability model.

Acceptance:
1. LegacyCapabilityAdapter removed and build green.

### 1.4 Neutralize kernel tests and fixtures

Actions:
1. Replace product terms in kernel tests with neutral identifiers.
2. Keep intent and coverage unchanged.

Acceptance:
1. Kernel test fixtures use neutral terms only.

### 1.5 Add strict kernel purity gates

Actions:
1. Add checkKernelPurity for src/main/java.
2. Add checkKernelResourcePurity for src/main/resources.
3. Add checkKernelDocsPurity for platform-kernel/docs and docs/examples/product-on-kernel.
4. Use allowlist file for approved terms that appear in governance references only.

Acceptance:
1. All three tasks wired into check pipeline.
2. False positive process documented.

---

## Phase 2 - Platform Plugin Purity

### 2.1 Rename plugin-billing-ledger to plugin-ledger

Actions:
1. Rename module folder and include in settings.
2. Rename Java packages com.ghatana.plugin.billing to com.ghatana.plugin.ledger.
3. Rename class/interface identifiers accordingly.
4. Update plugin IDs, bindings, docs, generated matrices, integration tests, and architecture tests.

Architecture rule updates:
1. ARCH-011 wording: audit plugin must not import ledger implementation.
2. ARCH-012 wording: ledger plugin must not import fraud/risk/compliance implementations.

Acceptance:
1. No references to plugin-billing-ledger or com.ghatana.plugin.billing remain.

### 2.2 Refactor risk plugin to generic decision/risk engine

Problem with prior approach: removing evaluateClinicalFactor only is insufficient.

New platform contract shape:
1. RiskProfile
2. RiskModelId (string value object)
3. RiskFactor
4. RiskLimit
5. RiskScore
6. RiskDecision
7. RiskNormalizer
8. RiskRulePack

Actions:
1. Replace enum RiskType with extensible RiskModelId.
2. Remove CLINICAL and other product/domain type assumptions from platform enum model.
3. Remove finance-shaped RiskLimits fields from platform contract; keep generic limit descriptors.
4. Replace hardcoded factor switches with registered model evaluators.
5. Move market/portfolio/clinical/credit specific normalization and factor semantics into product rule/model packs.
6. Rewrite javadocs to avoid product mentions in platform contract.

Acceptance:
1. platform-plugins/plugin-risk-management/src/main/java contains no product domain terms in production code.
2. Risk engine runs using externally supplied model/rule packs.

### 2.3 Refactor compliance plugin to generic rule engine

Actions:
1. Remove hardcoded SOX/HIPAA/GDPR/PCI-DSS static rule sets from production plugin implementation.
2. Keep platform plugin generic: parse, validate, evaluate, explain, and report.
3. Introduce rule-pack schema and version/compatibility metadata.
4. Make rule pack data/config first; Java loader second.
5. Remove regulation names from platform javadocs where they imply built-in defaults.

Acceptance:
1. Regulation-specific rule content is product/domain pack owned.
2. Platform compliance plugin contains no regulation-default assumptions.

### 2.4 Plugin API config schema (only after Phase 0.3)

Actions:
1. Add config schema to canonical PluginManifest only.
2. Add validation in plugin lifecycle install path.
3. Add secret-handling constraints and validation errors with actionable messages.

Acceptance:
1. No schema is added to a non-canonical manifest.

### 2.5 Wire observability module before telemetry enforcement

Actions:
1. Add include("core-observability") in platform-plugins/settings.gradle.kts.
2. Add module dependencies from each plugin to core-observability.
3. Enforce telemetry instrumentation for all plugin public operations.

Enforcement style:
1. Prefer operation-level instrumentation contract over strict inheritance-only requirement.
2. Allow base class, wrapper, annotation, or contract-test based instrumentation if equivalent.

Acceptance:
1. Telemetry enforcement rule enabled only after module wiring is complete.

### 2.6 Plugin fixture neutralization and stale scan gates

Actions:
1. Replace product-specific fixture strings in plugin tests.
2. Add plugin purity scans for src/main/java and selected resources/docs examples.

Acceptance:
1. Plugin tests remain authentic and pass after neutralization.

---

## Phase 3 - Product Pack Model and Validation

### 3.1 PHR packs

Create and validate:
1. policy-packs
2. compliance rule packs
3. plugin bindings
4. product startup registration path to kernel policy/rule stores

### 3.2 Finance packs

Create and validate the same artifacts for finance.

### 3.3 Pack schema validation gates

Add schema and validation tasks for:
1. domain-pack-manifest
2. policy-pack
3. compliance-rule-pack
4. plugin-binding

Validation behavior:
1. Fail on schema violations.
2. Fail on unknown plugin IDs.
3. Fail on incompatible version ranges.

### 3.4 Product-side contract tests

Actions:
1. Keep kernel tests platform-neutral.
2. Move product scenario assertions to product-owned test suites.
3. Add negative tests proving kernel does not import product pack classes.

Acceptance:
1. Product contract tests pass independently and confirm extension-point integration.

---

## Phase 4 - Bridge Hardening

### 4.1 AbstractKernelBridge

Actions:
1. Introduce base bridge abstraction for Promise wrapping, lifecycle, and common hooks.
2. Refactor DataCloud and AEP adapters to extend it.

### 4.2 Kernel-owned bridge ports (avoid concrete security coupling)

Introduce ports in kernel-core:
1. BridgeAuthorizationService
2. BridgeAuditEmitter
3. BridgeHealthIndicator

Rationale:
1. Kernel owns abstractions.
2. Runtime/product wiring binds ports to concrete security/observability implementations.

### 4.3 Context propagation and resilience requirements

Add:
1. tenant/principal/correlation propagation
2. idempotency keys for write operations
3. timeout and retry policy
4. circuit breaker and degraded-mode behavior
5. sensitive metadata redaction

Acceptance:
1. Bridge integration tests cover denial, timeout, retry, and degraded-mode paths.

---

## Phase 5 - Documentation and Generated Matrices

Create or update:
1. KERNEL_PURITY_RULES.md
2. PLUGIN_PURITY_RULES.md
3. PRODUCT_DEVELOPMENT_GUIDE.md
4. CAPABILITY_MATRIX.md (regenerated from canonical plugin/module metadata)
5. docs/examples/product-on-kernel (fictional domains only)

Requirements:
1. No real product domain terms in platform examples.
2. Generated matrices must use canonical plugin/module names after ledger rename.

---

## Phase 6 - Strategic Backlog (No Mixing with Hardening)

Keep strategic items #45-#60 in separate planning and PR streams.
Do not mix them into boundary hardening implementation PRs.

---

## CI Gate Matrix (Revised)

### Required blocking checks

1. buildBaseline
2. checkKernelPurity
3. checkKernelResourcePurity
4. checkKernelDocsPurity
5. checkPluginPurity
6. validateDomainPacks
7. validatePolicyPacks
8. validateComplianceRulePacks
9. validatePluginBindings
10. architecture tests (kernel and plugin)
11. contract parity tests
12. product-side kernel plugin contract tests

### Scan scope and exclusions

Include:
1. src/main/java
2. src/main/resources
3. selected docs/examples directories

Exclude:
1. build
2. out
3. bin
4. target
5. generated artifacts unless explicitly governed by schema checks

---

## Acceptance Criteria by Phase

### Phase 0 complete when

1. Composite command matrix documented and verified.
2. Intra-composite dependency path issues fixed.
3. Plugin API ownership ADR merged.
4. Scan exclusions finalized.
5. Baseline build report captured.

### Phase 1 complete when

1. Four boundary resolver artifacts consolidated to one canonical contract and implementation path.
2. Rule-based boundary policy model live.
3. Legacy adapter removed.
4. Kernel tests neutralized.
5. Kernel purity gates green.

### Phase 2 complete when

1. plugin-ledger rename fully propagated.
2. Risk plugin generalized and product-agnostic.
3. Compliance plugin generalized and pack-driven.
4. Config schema added only on canonical plugin manifest.
5. core-observability wired before telemetry enforcement.

### Phase 3 complete when

1. PHR and Finance packs implemented and schema-validated.
2. Product startup registration path implemented.
3. Product-side contract tests green.

### Phase 4 complete when

1. AbstractKernelBridge in use by AEP and DataCloud adapters.
2. Authorization, audit, and health done via kernel-owned ports.
3. Resilience and context-propagation requirements implemented and tested.

### Phase 5 complete when

1. Purity and product-on-kernel documentation updated.
2. Capability matrix regenerated and aligned with canonical names.

---

## Implementation Policy

1. Every hardening PR must include tests and CI evidence.
2. No placeholder/stub adapters in production paths.
3. No object-literal test theatre.
4. No silent fallback behavior for policy evaluation.
5. No product-domain defaults in kernel-core or platform-plugins.
