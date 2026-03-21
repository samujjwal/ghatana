# Kernel Canonicalization Decisions

**Date**: 2026-03-19  
**Version**: 1.0  
**Status**: Proposed canonical architecture decisions  
**Purpose**: Resolve duplicate kernel abstractions, remove product-aware logic from canonical kernel space, and define the target generic kernel model for AppPlatform convergence

---

## 1. Why This Document Exists

The current repo still contains four architecture gaps that block a stable kernel-first platform strategy:

1. duplicate kernel abstractions
2. product-aware logic still living inside canonical kernel packages
3. missing cross-frontier developer-platform contracts
4. historical docs that claim stronger completion than the code supports

This document solves the first two directly and provides the architectural baseline that the contract model and next-phase program board build on.

---

## 2. Evidence From Current Repo

### 2.1 Duplicate abstractions still exist

The repo currently contains overlapping kernel models:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/KernelCapability.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/capability/KernelCapability.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/extension/KernelExtension.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelExtension.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelPlugin.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/ProductPlugin.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/KernelRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/CapabilityRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/ServiceRegistry.java`

These models overlap in responsibility, vocabulary, and lifecycle semantics. That creates three risks:

- teams can implement against different abstractions and both appear "correct"
- AppPlatform cannot reliably map to one kernel contract set
- purity tests can pass while architectural drift continues elsewhere

### 2.2 Product-aware kernel logic still exists

Canonical kernel packages still contain explicit product terms and rules:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/audit/CrossProductAuditService.java`
  - hardcodes retention periods for `finance` and `phr`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/ProductBoundaryEnforcer.java`
  - hardcodes product ids `phr`, `finance`, `flashit`, `aura`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/communication/KernelInterProductBus.java`
  - uses source/target product as the primary communication model
- `platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/CrossProductWorkflowEngine.java`
  - encodes sourceProduct/targetProduct into workflow context

These classes are valuable, but they are currently framed too narrowly for a generic kernel that must support independent domains, shared deployments, and multi-product composition.

---

## 3. Canonical Decisions

### D1. Canonical capability model

**Decision**

`com.ghatana.kernel.descriptor.KernelCapability` is the canonical capability model.

**Why**

- it is already the type used by `KernelModule`, `KernelRegistry`, core modules, and most tests
- it fits the descriptor-oriented kernel model better than the parallel `capability` package
- it is the least disruptive choice for current code and docs

**Action**

- treat `com.ghatana.kernel.capability.KernelCapability` as legacy/transitional
- do not add new call sites against the `capability` package
- create an explicit migration/removal plan for that package

### D2. Canonical extension model

**Decision**

`com.ghatana.kernel.extension.KernelExtension` is the canonical extension contract.

**Why**

- it carries lifecycle hooks against `KernelContext`
- it understands compatibility against `KernelModule`
- it is richer and more aligned with the generic kernel model

**Action**

- treat `com.ghatana.kernel.plugin.KernelExtension` as a legacy adapter concept
- if a lighter manifest-layer concept is still needed, rename it to something unambiguous such as `DeclaredExtensionContribution`
- avoid two different interfaces with the same name in long-term architecture

### D3. Canonical runtime plugin model

**Decision**

`KernelPlugin` is the canonical runtime plugin model.

`ProductPlugin` is not a peer runtime model. It becomes one of two things:

1. a transitional compatibility adapter for older product declarations, or
2. a manifest-level packaging descriptor that is compiled into `KernelPlugin` registrations

**Why**

- only one runtime lifecycle should exist at the kernel boundary
- `KernelPlugin` already extends `KernelModule` and returns canonical descriptors/capabilities
- two runtime plugin models make certification, loading, isolation, and tenancy much harder to enforce

**Action**

- no new product runtime features should be added to `ProductPlugin`
- if retained short-term, document it as transitional and compile/translate it into canonical plugin/module registrations

### D4. Canonical registry model

**Decision**

`KernelRegistry` is the only public root registry contract.

`PluginRegistry`, `CapabilityRegistry`, and `ServiceRegistry` become internal implementation facets or sub-registries behind `KernelRegistry`.

**Why**

- consumers need one stable root for capability, module, and plugin discovery
- one public registry reduces ambiguity in contracts, docs, and validation
- sub-registry specialization is still fine internally

**Action**

- publish a single registry architecture diagram in future docs and ADR follow-ups
- mark helper registries as internal in documentation unless they are intentionally public SPIs

---

## 4. Target Generic Vocabulary

The canonical kernel must generalize its vocabulary so the same runtime can support:

- one domain deployed alone
- many domains deployed together
- one product over one or more domain packs
- multiple products cohabiting in one deployment

### 4.1 Replace "product-first" framing with "scope-first" framing

Use these canonical concepts:

- `scope`
  - one of `kernel`, `platform`, `domain-pack`, `product`, `tenant`, `operator`, `agent`, `workflow`
- `sourceScope`
- `targetScope`
- `scopeType`
- `scopeId`

Keep `product` as a valid scope type, not as the universal top-level assumption.

### 4.2 Replace product-aware branching with policy resolution

The kernel must not branch on literal product ids for retention, consent, cross-boundary access, or workflow routing.

Instead it must resolve rules from:

- classification metadata
- tenant policy
- domain-pack manifest
- deployment profile
- compliance policy pack
- capability metadata

---

## 5. Replacement Architecture For Product-Aware Kernel Services

### 5.1 `CrossProductAuditService` → `CrossScopeAuditService`

**Current issue**

- retention logic is hardcoded around `finance` and `phr`

**Decision**

Replace the service with a generic `CrossScopeAuditService` using:

- `AuditPolicyResolver`
- `RetentionPolicyResolver`
- `ScopeDescriptor`
- `ClassificationDescriptor`

**Target behavior**

- event metadata includes source scope, target scope, tenant, capability, and classification
- retention is resolved from policy metadata, not from product string checks
- product/domain policies are plugged in by manifests or policy packs

**Benefits**

- supports Finance, PHR, Aura, and future domains without kernel edits
- aligns with Data Cloud governance and regulatory evidence requirements

### 5.2 `ProductBoundaryEnforcer` → `ScopeBoundaryEnforcer`

**Current issue**

- boundary rules are hardcoded around current product ids and example resources

**Decision**

Replace it with a generic boundary engine using:

- `BoundaryPolicyProvider`
- `ConsentPolicyProvider`
- `DataResidencyPolicyProvider`
- `CapabilityAccessPolicyProvider`

**Target behavior**

- evaluates `(source scope, target scope, tenant, resource classification, action, policy set)`
- supports product-to-product, pack-to-pack, operator-to-domain, and agent-to-service interactions
- handles PHR consent rules, Finance audit requirements, and Aura governance rules through policy packs

### 5.3 `KernelInterProductBus` → `KernelInterScopeBus`

**Current issue**

- communication model is constrained to sourceProduct/targetProduct

**Decision**

Generalize it to an inter-scope contract with:

- stable envelope
- schema version
- source scope
- target scope
- tenant id
- capability id
- classification
- correlation/causation ids
- routing hints

**Target behavior**

- AEP remains the default cross-process event plane
- the kernel owns the logical communication contract, not a product-specific route model
- envelopes remain stable across domains and deployment topologies

### 5.4 `CrossProductWorkflowEngine` → `CrossScopeWorkflowEngine`

**Current issue**

- orchestration is modeled as product-to-product even though real workflows span packs, services, operators, and agents

**Decision**

Generalize orchestration around:

- workflow definition
- scope graph
- operator/agent step contracts
- compensation policy
- SLA policy
- observability context

**Target behavior**

- supports domain-local workflows, cross-domain workflows, and multi-product workflows
- works whether domains are independently deployed or co-located in the same kernel deployment

---

## 6. Canonical Layer Model

### 6.1 Canonical kernel responsibilities

The canonical kernel owns:

- lifecycle and composition
- capability registry and dependency graph
- policy enforcement hooks
- generic event and workflow contracts
- plugin isolation and certification hooks
- tenancy, audit, observability, resilience, secrets, config
- developer-platform contract surfaces

The canonical kernel does not own:

- finance-specific workflows
- healthcare-specific record rules
- product-specific data retention tables
- product-specific resource allowlists

### 6.2 AppPlatform responsibilities

AppPlatform operationalizes the kernel by providing:

- deployable services
- operator tooling
- manifests and certification
- upgrade workflows
- multi-domain packaging and activation
- production runbooks and topology management

### 6.3 Domain-pack responsibilities

Domain packs own:

- domain semantics
- domain data contracts
- domain workflow definitions
- domain policies
- domain-specific compliance behavior
- UI/API/schema surfaces that are domain-owned

### 6.4 Product responsibilities

Products own:

- product experience
- product composition of domain packs
- product-specific policy overlays
- rollout choices
- user journeys and commercial packaging

---

## 7. Migration Plan For Canonicalization

### Phase C0. Freeze drift

- declare canonical abstractions in docs and ADRs
- block new uses of the legacy capability and extension packages
- block new product string branching in canonical kernel packages

### Phase C1. Introduce compatibility adapters

- add explicit adapters from legacy capability/extension/plugin declarations to canonical models
- keep old code readable during the transition
- add `@Deprecated` markers where appropriate once implementation work begins

### Phase C2. Move policy out of kernel implementations

- replace hardcoded retention, boundary, and routing rules with providers/resolvers
- move Finance, PHR, and Aura specifics into pack/product policy definitions

### Phase C3. Generalize inter-scope orchestration

- publish generic envelope and workflow context contracts
- align AppPlatform workflow/operator/event modules to them

### Phase C4. Remove legacy duplicates

- remove duplicate classes after all consumers are migrated
- update purity tests to fail on reintroduction of duplicate names and product-aware logic

---

## 8. Acceptance Criteria

Canonicalization is complete only when all of the following are true:

- every kernel module, plugin, and registry uses one canonical capability type
- only one kernel extension contract remains public and authoritative
- only one runtime plugin model remains authoritative
- public docs describe `KernelRegistry` as the root registry contract
- no canonical kernel package contains literal branching on known product ids
- communication and workflow contexts are scope-based, not product-first
- Finance, PHR, and Aura specifics are supplied through manifests, policy packs, or domain packs
- purity validation is updated to test for duplicate abstractions and forbidden product-aware branching

---

## 9. Immediate Follow-Through

The next planning documents that depend on these decisions are:

- `DEVELOPER_PLATFORM_CONTRACT_MODEL.md`
- `KERNEL_NEXT_PHASE_PROGRAM_BOARD.md`

Those documents should be read as downstream of the canonicalization decisions in this file.
