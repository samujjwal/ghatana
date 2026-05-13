# CI Gate Matrix — Kernel Boundary Hardening

> **Owner:** Platform Kernel Team | **Status:** Enforced | **Last Updated:** 2026-05-01
> **Reference:** [KERNEL_BOUNDARY_HARDENING_PLAN.md](../KERNEL_BOUNDARY_HARDENING_PLAN.md) § Phase 0 + Phase 5

---

## Overview

This document catalogs all CI gates required for the Kernel and Platform Plugin boundary hardening initiative. Gates are organized by phase and must all pass before PRs merge.

**Execution model:**
- **Root mode** (from repo root): `./gradlew check`
- **Included-build mode**: `./gradlew -p platform-kernel check` or `./gradlew -p platform-plugins check`

---

## Phase 0 — Build and API Baseline Gates

### 0.1 Composite Build Validation

**Purpose:** Verify both root-mode and included-build-mode execution paths work.

```bash
# Root mode: build both composites
./gradlew build

# Included-build mode: build each composite independently
./gradlew -p platform-kernel build
./gradlew -p platform-plugins build
```

**Acceptance:** All commands succeed with zero failures.

---

### 0.2 Dependency Path Correctness

**Purpose:** Ensure intra-composite project references use relative paths, not full paths with composite prefix.

**Files verified:**
- `platform-kernel/kernel-plugin/build.gradle.kts` — must use `project(":kernel-core")` not `project(":platform-kernel:kernel-core")`
- `platform-kernel/kernel-persistence/build.gradle.kts` — must use `project(":kernel-core")` not `project(":platform-kernel:kernel-core")`

**Verification:**
```bash
grep -E "project\(.*platform-kernel:kernel-core" platform-kernel/*/build.gradle.kts
# Should return NO matches
```

**Acceptance:** No full-path references found.

---

### 0.3 Plugin API Ownership Enforced

**Purpose:** Ensure plugin API contracts route through canonical package only.

**Canonical module:** `kernel-plugin` owns `com.ghatana.platform.plugin.*`

**Architecture tests:**
```bash
./gradlew :platform-kernel:kernel-core:architecture-test
./gradlew :platform-plugins:architecture-test
```

**Checks:**
- `platform-plugins/*` modules import plugin API exclusively from `com.ghatana.platform.plugin.*`
- `kernel-core` does NOT import from `com.ghatana.platform.plugin.*` in production code
- Dual PluginManifest packages do not create ambiguity

**Acceptance:** All architecture tests pass.

---

### 0.4 Stale Generated Artifacts Excluded

**Purpose:** Purity scans must not report false positives from `build/`, `out/`, `bin/`, `target/` directories.

**Verification:**
```bash
# Purity task must scan only tracked source/resource/doc trees
./gradlew :platform-kernel:kernel-core:checkKernelPurity
./gradlew :platform-kernel:kernel-core:checkKernelResourcePurity
./gradlew :platform-kernel:kernel-core:checkKernelDocsPurity
```

**Acceptance:** No false positives from stale artifacts.

---

### 0.5 Baseline Build Report

**Purpose:** Document pre-change baseline status for traceability.

**Baseline capture:**
```bash
./gradlew build > build/reports/baseline-build.log 2>&1
./gradlew test > build/reports/baseline-tests.log 2>&1
```

**Artifacts:**
- `build/reports/baseline-build.log`
- `build/reports/baseline-tests.log`
- Included in PR description for traceability

**Acceptance:** Baseline logs captured and attached to PR.

---

## Phase 1 — Kernel Boundary Consolidation Gates

### 1.1–1.5 Kernel Purity Gates

**Purpose:** Enforce kernel-core remains product-agnostic.

```bash
# All three purity checks
./gradlew :platform-kernel:kernel-core:check
```

**Checks:**
1. `checkKernelPurity` — scans `src/main/java/**` for banned product terms
2. `checkKernelResourcePurity` — scans `src/main/resources/**` for banned product terms
3. `checkKernelDocsPurity` — scans `platform-kernel/docs/**` and `docs/examples/product-on-kernel` for banned product terms

**Banned patterns:**
- PHR, Finance, FINANCE, CLINICAL, phr-kernel, finance-kernel
- SOX, HIPAA, GDPR, PCI-DSS, PCIDSS
- trade\.records, patient\.records
- BillingLedger, RiskType\.CLINICAL

**Waiver process:**
1. Add term to allowlist only if it appears in governance references (not domain logic)
2. Document reason in `platform-kernel/kernel-core/build.gradle.kts` allowlist comment
3. Approve waiver in architecture review

**Acceptance:** Zero violations, or approved waivers only.

---

## Phase 2 — Platform Plugin Purity Gates

### 2.1 Plugin Ledger Rename Verified

**Purpose:** Ensure no references to deprecated `plugin-billing-ledger` remain.

```bash
grep -r "plugin-billing-ledger" platform-plugins/
grep -r "com\.ghatana\.plugin\.billing" platform-plugins/
grep -r "BillingLedger" platform-plugins/
# All should return NO matches
```

**Acceptance:** Zero references to deprecated names.

---

### 2.2 Risk Plugin Generalization

**Purpose:** Verify risk plugin uses generic model engine, not domain-specific terms.

**Checks:**
```bash
./gradlew :platform-plugins:plugin-risk-management:check
```

**Verifications:**
- No enum `RiskType` with CLINICAL variant — replaced by `RiskModelId` string value object
- Risk scoring operates on generic `RiskFactor` and `RiskScore`, not domain-specific limits
- Normalizers and rules are externally supplied via model/rule packs, not hardcoded

**Acceptance:** Plugin tests pass, no domain terms in production code.

---

### 2.3 Compliance Plugin Generalization

**Purpose:** Verify compliance plugin is pack-driven, not regulation-biased.

**Checks:**
```bash
./gradlew :platform-plugins:plugin-compliance:check
```

**Verifications:**
- No static rule sets for SOX, HIPAA, GDPR, PCI-DSS in production code
- Rule-pack schema and validation live in product layers
- Platform plugin remains generic: parse, validate, evaluate, explain, report

**Acceptance:** Plugin tests pass, no regulation defaults in platform code.

---

### 2.4 Plugin Config Schema Wired

**Purpose:** Canonical `PluginManifest` in `kernel-plugin` carries config schema.

**Verification:**
```bash
grep -r "configSchema" platform-kernel/kernel-plugin/src/main/java/com/ghatana/platform/plugin/
# Should return matches only in PluginManifest.java
```

**Acceptance:** Schema appears only on canonical manifest.

---

### 2.5 Core Observability Included

**Purpose:** Observability module available to all platform plugins for telemetry.

**Checks:**
```bash
# Verify core-observability is listed in settings
grep "include.*core-observability" platform-plugins/settings.gradle.kts

# Build observability module
./gradlew :platform-plugins:core-observability:build
```

**Acceptance:** Module listed and builds successfully.

---

### 2.6 Plugin Fixture Neutralization

**Purpose:** Plugin tests use neutral identifiers, not product terms.

**Verification:**
```bash
grep -r "patient\|clinical\|trade\.record\|HIPAA\|SOX" platform-plugins/src/test/
# Should return NO matches (only neutral terms like "tenant-1", "resource-x")
```

**Acceptance:** Zero product terms in plugin test fixtures.

---

## Phase 3 — Product Pack Validation Gates

### 3.1–3.2 PHR and Finance Packs

**Purpose:** Validate pack structure, rule schemas, and startup registration.

```bash
# PHR packs validation
./gradlew :products:phr:validatePolicyPack
./gradlew :products:phr:validateComplianceRulePack
./gradlew :products:phr:test

# Finance packs validation
./gradlew :products:finance:validatePolicyPack
./gradlew :products:finance:validateComplianceRulePack
./gradlew :products:finance:test
```

**Checks:**
- Boundary policy rule schema validates
- Compliance rule pack IDs are prefixed correctly (PHR-*, FIN-*)
- Last-rule-is-default-deny behavior enforced
- Product startup registers packs with kernel stores

**Acceptance:** All validation tasks and tests pass.

---

### 3.3 Pack Schema Validation Gates

**Purpose:** Fail fast on schema violations.

```bash
./gradlew :products:phr:validateDomainPackManifest
./gradlew :products:phr:validatePolicyPackSchema
./gradlew :products:phr:validateComplianceRulePackSchema
./gradlew :products:phr:validatePluginBindingSchema

./gradlew :products:finance:validateDomainPackManifest
./gradlew :products:finance:validatePolicyPackSchema
./gradlew :products:finance:validateComplianceRulePackSchema
./gradlew :products:finance:validatePluginBindingSchema
```

**Validation behavior:**
- Fail on schema violations
- Fail on unknown plugin IDs
- Fail on incompatible version ranges

**Acceptance:** All schema validation tasks pass.

---

### 3.4 Product-Side Contract Tests

**Purpose:** Product tests verify boundary and compliance pack integration without importing kernel internals.

```bash
# PHR contract tests
./gradlew :products:phr:test --tests PhrPackContractTest

# Finance contract tests
./gradlew :products:finance:test --tests FinancePackContractTest
```

**Verifications:**
- `PhrPackContractTest.BoundaryPolicyStoreTests` verifies policy store contract
- `PhrPackContractTest.ComplianceRulePackTests` verifies rule pack contract
- `PhrPackContractTest.BoundaryIsolationTests` verifies kernel does not import product packs
- Equivalent tests for Finance

**Acceptance:** All product contract tests pass.

---

## Phase 4 — Bridge Hardening Gates

### 4.1 AbstractKernelBridge Implementation

**Purpose:** Bridge base class in use by both adapters.

```bash
./gradlew :platform-kernel:kernel-core:compileJava
./gradlew :platform-kernel:kernel-core:test --tests AbstractKernelBridgeTest
```

**Verifications:**
- `DataCloudKernelAdapterImpl extends AbstractKernelBridge`
- `AepKernelAdapterImpl extends AbstractKernelBridge`
- Both use bridge port constructors (full 4-arg form, not just 1-arg convenience)

**Acceptance:** Compilation succeeds, bridge tests pass.

---

### 4.2 Kernel-Owned Bridge Ports

**Purpose:** All bridge abstractions (auth, audit, health) owned by kernel, not leaked product concerns.

**Port classes verified:**
```bash
# All must exist and be in kernel-core
ls platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/bridge/port/
  BridgeContext.java
  BridgeAuthorizationService.java
  BridgeAuditEmitter.java
  BridgeHealthIndicator.java
```

**Acceptance:** All four port classes exist in canonical location.

---

### 4.3 Context Propagation and Resilience

**Purpose:** Bridge contract tests verify tenant/principal/correlation propagation and retry/circuit-breaker behavior.

```bash
./gradlew :platform-kernel:kernel-core:test --tests AbstractKernelBridgeTest
```

**Test coverage:**
- `LifecycleTests` — bridge starts/stops correctly
- `AuthorizationTests` — allowed/denied decisions propagate to audit
- `RetryTests` — transient failures retry, exhausted retries fail and report unhealthy
- `RedactionTests` — sensitive metadata redacted in logs/events
- `BridgeContextTests` — tenant ID required, principal ID defaults, idempotency key nullable

**Acceptance:** All bridge contract tests pass.

---

## Phase 5 — Documentation and Capability Matrix

### 5.1 Kernel Purity Rules Documentation

**File:** `docs/KERNEL_PURITY_RULES.md`

**Verification:**
```bash
test -f docs/KERNEL_PURITY_RULES.md && echo "OK" || echo "MISSING"
grep "PASS — no product domain terms" docs/KERNEL_PURITY_RULES.md
```

**Acceptance:** File exists and documents waiver process.

---

### 5.2 Plugin Purity Rules Documentation

**File:** `docs/PLUGIN_PURITY_RULES.md`

**Verification:**
```bash
test -f docs/PLUGIN_PURITY_RULES.md && echo "OK" || echo "MISSING"
```

**Acceptance:** File exists.

---

### 5.3 Product Development Guide

**File:** `docs/PRODUCT_DEVELOPMENT_GUIDE.md`

**Verification:**
```bash
test -f docs/PRODUCT_DEVELOPMENT_GUIDE.md && echo "OK" || echo "MISSING"
grep -q "BoundaryPolicyStore SPI" docs/PRODUCT_DEVELOPMENT_GUIDE.md
grep -q "ComplianceRulePack" docs/PRODUCT_DEVELOPMENT_GUIDE.md
```

**Acceptance:** Guide exists and documents pack models and bridge usage.

---

### 5.4 Capability Matrix Regenerated

**File:** `docs/CAPABILITY_MATRIX.md`

**Verification:**
```bash
test -f docs/CAPABILITY_MATRIX.md && echo "OK" || echo "MISSING"
grep "plugin-ledger" docs/CAPABILITY_MATRIX.md
grep -v "plugin-billing-ledger" docs/CAPABILITY_MATRIX.md
```

**Acceptance:** Matrix exists with canonical names (ledger, not billing-ledger).

---

## Composite CI Pipeline

Execute all gates in order:

```bash
#!/bin/bash
set -e

echo "=== Phase 0: Build and Baseline ==="
./gradlew build > build/reports/baseline-build.log 2>&1
./gradlew -p platform-kernel build
./gradlew -p platform-plugins build

echo "=== Phase 1: Kernel Purity ==="
./gradlew :platform-kernel:kernel-core:check

echo "=== Phase 2: Plugin Purity ==="
./gradlew :platform-plugins:check
./gradlew :platform-kernel:kernel-plugin:check

echo "=== Phase 3: Product Packs ==="
./gradlew :products:phr:test :products:finance:test

echo "=== Phase 4: Bridge Hardening ==="
./gradlew :platform-kernel:kernel-core:test

echo "=== Phase 5: Documentation ==="
test -f docs/KERNEL_PURITY_RULES.md
test -f docs/PLUGIN_PURITY_RULES.md
test -f docs/PRODUCT_DEVELOPMENT_GUIDE.md
test -f docs/CAPABILITY_MATRIX.md

echo "=== ✓ All CI gates PASSED ==="
```

---

## Implementation Policy

1. **Every hardening PR** must include test evidence and CI logs
2. **No stub adapters** in production paths
3. **No object-literal test theatre** — tests must exercise real subjects
4. **No silent fallback** for policy evaluation (default-deny only)
5. **No product-domain defaults** in kernel-core or platform-plugins
6. **All TypeScript code** fully typed during implementation (no `any`)
7. **All Java public APIs** include Javadoc `@doc.*` tags

---

## Troubleshooting

### Composite build not found
```bash
# Check platform-kernel/settings.gradle.kts includes expected modules
cat platform-kernel/settings.gradle.kts

# Verify working directory
pwd
# Should be inside platform-kernel/ for included-build-mode
```

### Purity task reports false positive
```bash
# Add to allowlist in kernel-core/build.gradle.kts if term appears in governance only
# Re-run task
./gradlew :platform-kernel:kernel-core:checkKernelPurity
```

### Architecture test fails
```bash
# Print violation details
./gradlew :platform-kernel:kernel-core:architecture-test --info

# Fix import in the reported file
# Re-run architecture tests
./gradlew :platform-kernel:kernel-core:architecture-test
```

---

## Related Documents

- [KERNEL_BOUNDARY_HARDENING_PLAN.md](../KERNEL_BOUNDARY_HARDENING_PLAN.md) — full plan and acceptance criteria
- [docs/BUILD.md](./BUILD.md) — composite build commands
- [docs/adr/ADR-025-plugin-api-ownership.md](./adr/ADR-025-plugin-api-ownership.md) — plugin API decision
- [docs/GOVERNANCE.md](./GOVERNANCE.md) — CI-enforced standards
- [PRODUCT_LIFECYCLE_CONTRACT.md](PRODUCT_LIFECYCLE_CONTRACT.md) — lifecycle phase contracts and gates
- [PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md](PRODUCT_TOOLCHAIN_ADAPTER_SPEC.md) — toolchain adapter specification
