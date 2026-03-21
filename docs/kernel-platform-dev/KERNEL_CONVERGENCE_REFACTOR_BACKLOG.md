# Kernel Convergence Refactor Backlog

**Date**: 2026-03-19  
**Version**: 1.0  
**Status**: Proposed Refactor Backlog  
**Purpose**: Concrete backlog for converging `platform/java/kernel` and `products/app-platform` into one canonical kernel/platform architecture

---

## 1. Backlog Goals

This backlog is focused on four goals:

1. eliminate duplicate kernel abstractions
2. remove product/domain leakage from canonical kernel code
3. align AppPlatform modules to the canonical kernel model
4. prepare the platform for domain-pack-first, multi-domain product development

---

## 2. Workstream A: Canonical Contract Cleanup

### A1. Choose one `KernelCapability` model

**Problem**

- both `com.ghatana.kernel.descriptor.KernelCapability` and `com.ghatana.kernel.capability.KernelCapability` exist

**Action**

- select one canonical class
- migrate all plugin, registry, and module code to it
- deprecate and remove the other

**Priority**: P0  
**Outcome**: one stable capability model across kernel and AppPlatform

### A2. Choose one `KernelExtension` model

**Problem**

- both `com.ghatana.kernel.extension.KernelExtension` and `com.ghatana.kernel.plugin.KernelExtension` exist

**Action**

- keep one extension contract
- if a simplified product extension model is still needed, rename it clearly instead of reusing the same concept name

**Priority**: P0

### A3. Resolve `KernelPlugin` vs `ProductPlugin`

**Problem**

- current architecture has both a runtime plugin model and a product plugin model

**Action**

- decide whether:
  - `KernelPlugin` is the only runtime extension model and `ProductPlugin` is removed
  - or `ProductPlugin` becomes a manifest-level concept on top of `KernelPlugin`

**Priority**: P0

### A4. Consolidate registry model

**Problem**

- `KernelRegistry` coexists with `PluginRegistry`, `CapabilityRegistry`, and `ServiceRegistry` without one obvious canonical center

**Action**

- define the canonical registry architecture
- decide which registries are internal sub-registries and which are public contracts

**Priority**: P0

---

## 3. Workstream B: Kernel Purity Fixes

### B1. Remove hardcoded product retention logic from `CrossProductAuditService`

**Problem**

- finance and PHR retention are hardcoded in canonical kernel code

**Action**

- replace with policy-driven retention resolution
- use classification/domain/pack metadata rather than product string checks

**Priority**: P0

### B2. Remove hardcoded product allowlists from `ProductBoundaryEnforcer`

**Problem**

- source/target product ids and resource policies are hardcoded

**Action**

- replace with policy registry / boundary policy provider
- support domain pack, product, tenant, and capability based evaluation

**Priority**: P0

### B3. Generalize `KernelInterProductBus`

**Problem**

- it encodes cross-product communication too literally

**Action**

- evolve it to support cross-pack, cross-domain, and cross-product communication contracts
- keep “product” as one possible dimension, not the only dimension

**Priority**: P1

### B4. Generalize `CrossProductWorkflowEngine`

**Problem**

- good concept, but framed around product-to-product orchestration rather than generic orchestration contracts

**Action**

- evolve toward a canonical orchestration contract aware of:
  - source scope
  - target scope
  - tenant
  - pack
  - capability
  - operator/agent/workflow metadata

**Priority**: P1

### B5. Strengthen kernel purity tests

**Problem**

- existing purity tests focus mainly on naming and capability ids

**Action**

- add tests for:
  - forbidden hardcoded product ids in canonical kernel
  - policy-driven rather than product-driven branching
  - duplicate abstraction detection

**Priority**: P1

---

## 4. Workstream C: AppPlatform Alignment

### C1. Publish canonical capability mapping

**Problem**

- AppPlatform modules are not explicitly mapped to canonical kernel capabilities

**Action**

- create and maintain capability-to-module mapping
- use it in docs, manifests, and implementation reviews

**Priority**: P0

### C2. Re-scope `workflow-orchestration`

**Problem**

- finance workflows live in generic kernel space

**Action**

- keep only generic workflow engine pieces in kernel
- move trade settlement, corporate actions, and regulatory report flows to domain packs

**Priority**: P0

### C3. Re-scope `operator-workflows`

**Problem**

- mixes legitimate shared operations with domain-biased semantics

**Action**

- split into:
  - generic tenant/policy/locale registry services
  - domain-specific operational workflows in packs

**Priority**: P1

### C4. Reframe `ledger-framework`

**Problem**

- naming/docs are finance-first

**Action**

- confirm whether core engine is generic
- rename/re-document as immutable ledger/balance engine if retained in platform layer

**Priority**: P1

### C5. Align AppPlatform docs to canonical kernel vocabulary

**Problem**

- AppPlatform docs currently imply a parallel kernel model

**Action**

- update docs to explicitly state that AppPlatform modules implement canonical kernel capabilities

**Priority**: P1

---

## 5. Workstream D: Domain-Pack-First Runtime

### D1. Standardize pack manifest model

**Problem**

- plugin and pack concepts are not yet unified

**Action**

- define one manifest model for:
  - identity/version
  - required kernel capabilities
  - exported contracts
  - workflows
  - integrations
  - UI contributions
  - activation constraints

**Priority**: P0

### D2. Create healthcare domain pack skeleton

**Problem**

- healthcare is a target domain but not yet a real pack

**Action**

- create `products/app-platform/domain-packs/healthcare/`
- align it to PHR needs and canonical pack contracts

**Priority**: P0

### D3. Validate independent and shared-kernel deployment

**Problem**

- architecture requires both deployment modes, but they are not yet proven together

**Action**

- create tests and activation matrices for:
  - healthcare only
  - finance only
  - healthcare + finance shared kernel

**Priority**: P0

---

## 6. Workstream E: Developer Platform Expansion

### E1. Add frontend/UI contribution contract

**Problem**

- kernel vision includes UI/UX support, but current contracts are backend-heavy

**Action**

- define UI contribution contracts for:
  - routes
  - navigation
  - screens/widgets
  - human task surfaces
  - policy-aware UI context

**Priority**: P1

### E2. Add schema and API contract registry

**Problem**

- product teams need a shared contract layer across backend and UI

**Action**

- add canonical registry for:
  - OpenAPI
  - event schemas
  - workflow schemas
  - shared DTO/schema packs

**Priority**: P1

### E3. Add analytics and autonomous capability groups

**Problem**

- analytics and autonomous support are part of the vision, but not yet first-class kernel contracts

**Action**

- define kernel capability groups for:
  - analytics pipelines
  - feature extraction
  - agent/operator governance
  - autonomous workflow routing

**Priority**: P1

---

## 7. Workstream F: Validation And Documentation Corrections

### F1. Reconcile validation docs with actual repo state

**Problem**

- `FINAL_VALIDATION_REPORT.md` says production ready, but multiple integration tests remain disabled

**Action**

- downgrade status to reflect current reality or re-enable tests before claiming readiness

**Priority**: P0

### F2. Re-enable disabled kernel integration/e2e tests

**Problem**

- several kernel tests are disabled pending missing infrastructure or classpath shape

**Action**

- make test topology match intended runtime architecture
- re-enable and stabilize:
  - `KernelEndToEndTest`
  - `PhrFinanceIntegrationTest`
  - `PhrFinanceCrossProductIntegrationTest`
  - other disabled kernel compliance/integration tests

**Priority**: P1

### F3. Publish convergence documentation set

**Action**

- keep these artifacts together:
  - convergence ADR
  - mapping document
  - refactor backlog
  - healthcare integration plan

**Priority**: P1

---

## 8. Suggested Priority Order

### P0

- A1 choose canonical capability model
- A2 choose canonical extension model
- A3 resolve plugin model
- A4 consolidate registries
- B1 remove hardcoded retention logic
- B2 remove hardcoded boundary product ids
- C1 publish capability mapping
- C2 re-scope workflow orchestration
- D1 standardize pack manifest model
- D2 create healthcare pack skeleton
- D3 validate deployment modes
- F1 correct validation status docs

### P1

- B3 generalize inter-product bus
- B4 generalize workflow engine
- B5 strengthen purity tests
- C3 re-scope operator workflows
- C4 reframe ledger framework
- C5 align AppPlatform docs
- E1 UI contribution contract
- E2 schema/API registry
- E3 analytics/autonomous capability groups
- F2 re-enable disabled integration tests
- F3 publish maintained convergence docs

---

## 9. Definition Of Done For Convergence

Convergence is not complete until:

1. one canonical kernel contract family exists
2. no product-specific logic remains in canonical kernel core
3. AppPlatform modules are explicitly mapped as implementations of canonical capabilities
4. healthcare and finance both fit the same pack/runtime model
5. independent and shared-kernel deployments are both validated
6. developer platform capabilities cover UI, APIs, integrations, data, analytics, and autonomous execution
