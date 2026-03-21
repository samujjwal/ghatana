# ADR: Kernel-AppPlatform Convergence

**Status**: Proposed  
**Date**: 2026-03-19  
**Decision Makers**: Platform Architecture Team  
**Related**: [KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md](./KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md), [PHR_AppPlatform_Integration_Analysis_Report.md](./PHR_AppPlatform_Integration_Analysis_Report.md)

---

## Context

The repository currently contains two overlapping platform architecture models:

1. `platform/java/kernel`
   - a Java 21 + ActiveJ kernel runtime/composition model
   - core abstractions for modules, context, registry, capabilities, plugins, adapters, tenant context, and cross-product coordination

2. `products/app-platform`
   - a broader multi-domain platform product
   - concrete kernel-style services, domain-pack model, deployment/upgrade runbooks, operational docs, gateway/governance concerns, and product-facing platform topology

Both are valuable, but they are not yet fully aligned.

Current issues include:

- duplicate or competing abstractions:
  - two `KernelCapability` types
  - two `KernelExtension` types
  - `KernelPlugin` vs `ProductPlugin`
  - `KernelRegistry` vs `PluginRegistry` / `CapabilityRegistry` / `ServiceRegistry`
- product-aware logic still present inside `platform/java/kernel`
- AppPlatform conceptually acting as a second kernel model rather than an implementation/product built on the canonical kernel
- domain-pack, deployment, and operational concepts stronger in AppPlatform than in `platform/java/kernel`

We need one coherent platform architecture that can support:

- multi-domain operation
- product isolation
- domain-pack composition
- UI/UX, middleware, backend, API, data, analytics, and autonomous development
- both single-domain and multi-domain shared-kernel deployments

---

## Decision

We will adopt the following architecture:

### 1. `platform/java/kernel` becomes the canonical kernel contract/runtime model

It is the authoritative home for:

- `KernelModule`
- `KernelContext`
- `KernelPlugin`
- `KernelExtension`
- `KernelCapability`
- `KernelDependency`
- tenant runtime context
- canonical registries and lifecycle contracts
- logical contracts for workflow, eventing, capability discovery, and policy enforcement

### 2. `products/app-platform` becomes the operational multi-domain platform built on the kernel

AppPlatform will be treated as:

- the first major production implementation of the canonical kernel model
- the runtime/control-plane product that operationalizes kernel capabilities
- the multi-domain platform that activates and manages domain packs

AppPlatform must stop behaving as a competing kernel theory.

This does **not** mean AppPlatform becomes less ambitious.
Its grand vision is preserved as the operational face of the platform:

- multi-domain activation
- governance and observability depth
- pack certification and upgrade discipline
- operator/admin control-plane tooling
- production-grade runtime topology management

### 3. Domain packs become the official domain packaging abstraction

Domain packs are the layer where domain-specific logic lives:

- healthcare
- finance
- insurance
- banking
- future domains

Kernel remains generic.
Domain packs provide domain semantics, workflows, integrations, and optional UI contributions.

### 4. Products sit above domain packs

Products such as PHR, finance product, FlashIt, Aura, and future products:

- compose domain packs and kernel capabilities
- expose user-facing APIs and experiences
- remain independently activatable and deployable

### 5. Kernel must support both independent and co-deployed domain topologies

The combined architecture must support:

- healthcare-only deployment on the kernel
- finance-only deployment on the kernel
- multi-domain shared-kernel deployment

Co-deployment must not permit hidden cross-domain coupling.

### 6. The merged platform preserves both grand visions

The merged architecture must preserve the strongest goals from both sides:

- from the kernel vision:
  - full-lifecycle platform ambition
  - AI-native pervasive design
  - easy but powerful development surface
  - opinionated generic runtime core
- from the AppPlatform vision:
  - multi-domain runtime realism
  - operational governance and upgrade rigor
  - domain-pack-first product assembly
  - deployable platform services and control-plane depth

The decision is therefore a merge of strengths, not a reduction to the smallest common denominator.

---

## Architectural Implications

### Kernel responsibilities

Kernel is responsible for:

- lifecycle driving
- boundary enforcement
- logical orchestration
- tenant/context propagation
- capability discovery
- plugin/pack activation contracts
- communication contracts
- policy hook points
- developer-platform contracts across UI/UX, API, schema, analytics, and autonomy

### AppPlatform responsibilities

AppPlatform is responsible for:

- concrete kernel service implementations
- pack activation/deployment/upgrade
- gateway and ingress control-plane concerns
- operator/admin tooling
- governance and observability composition
- runtime operationalization of canonical kernel capabilities
- preserving the production-grade multi-domain platform vision in deployable form

### Domain-pack responsibilities

Domain packs are responsible for:

- domain workflows
- domain rules
- domain integrations
- domain data models
- domain-specific UI/runtime contributions

### Product responsibilities

Products are responsible for:

- end-user experience
- product APIs and shells
- packaging and rollout profiles
- product-specific composition of packs and platform services

---

## Required Follow-On Decisions

This ADR requires the following immediate follow-up:

1. choose one canonical `KernelCapability` abstraction
2. choose one canonical `KernelExtension` abstraction
3. decide whether `ProductPlugin` survives as a higher-level convenience model or is replaced by a pack/product manifest layer built on `KernelPlugin`
4. map every AppPlatform kernel module to canonical kernel capabilities
5. remove product-aware logic from `platform/java/kernel`
6. create a healthcare domain pack before PHR is deeply integrated with AppPlatform

---

## Consequences

### Positive

- one kernel story across the repository
- clear separation between runtime contracts and operational implementation
- cleaner path for healthcare, finance, insurance, and future domains
- stronger support for both independent and shared-kernel deployments
- better basis for full-spectrum developer platform capabilities

### Negative / Cost

- refactoring duplicate abstractions will be non-trivial
- some current docs will need downgrading or correction
- some kernel classes currently treated as generic will need redesign
- AppPlatform docs and code will need remapping to the canonical kernel vocabulary

---

## Acceptance Criteria

This ADR is considered implemented when:

1. only one canonical kernel contract family remains active
2. AppPlatform docs explicitly reference the canonical kernel model
3. at least one domain pack can run independently on the kernel
4. at least one multi-domain shared-kernel deployment topology is validated
5. no product-specific logic remains inside canonical kernel core classes
