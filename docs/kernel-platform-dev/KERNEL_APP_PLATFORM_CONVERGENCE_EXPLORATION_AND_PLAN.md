# Kernel-AppPlatform Convergence Exploration And Plan

**Date**: March 19, 2026  
**Version**: 1.0  
**Status**: Architecture Exploration and Convergence Plan  
**Classification**: Internal - Restricted

---

## Executive Summary

This document explores how to combine:

- `platform/java/kernel`
- `products/app-platform`
- `docs/kernel-platform-dev`

into a single coherent platform strategy.

The core conclusion is:

> The best path is not to treat `platform/java/kernel` and `products/app-platform` as competing architectures.
> Instead, `platform/java/kernel` should become the **canonical kernel runtime and composition model**, while AppPlatform should become the **operational multi-domain platform product built on top of that kernel model**.

In that combined model, the kernel becomes:

- the **driver** of lifecycle, policy, and capability activation
- the **enforcer** of boundaries, security, tenancy, and compliance guardrails
- the **orchestrator** of workflows, agents, plugins, and cross-product operations
- the **logical communication medium** for contracts, events, and cross-domain interaction
- the **aggregator and concentrator** of platform primitives for product teams
- the **development platform surface** for UI/UX, middleware, backend, APIs, data, analytics, and autonomous systems

At the same time, AppPlatform provides the richer productization needed for:

- multi-domain kernel services
- domain packs
- deployment, governance, and operator tooling
- production hardening and multi-tenant activation

The current repo already contains many of the right ingredients, but they are split across two partially overlapping models. The plan below explains how to merge them cleanly.

### What must be preserved from the grand vision

The consolidation must not accidentally narrow the ambition of either side:

- from the original kernel vision, we must preserve:
  - the kernel as the cornerstone of the full application lifecycle
  - AI-native behavior as a pervasive property, not a bolt-on
  - an easy-to-use but powerful development surface
  - strong infrastructure abstraction without hiding critical policy/control points
  - opinionated core patterns with flexible product/domain edges
- from the AppPlatform vision, we must preserve:
  - multi-domain operationalization
  - deployment, upgrade, operator, and governance depth
  - domain-pack-first product assembly
  - runtime activation, certification, and observability discipline
  - a platform that is usable for real products, not only a clean theoretical kernel

The convergence plan is therefore not a simplification exercise.
It is a preservation-and-hardening exercise:

- keep the kernel vision’s compositional purity and full-spectrum development ambition
- keep the AppPlatform vision’s operational realism and domain activation model
- remove only duplication, ambiguity, stale assumptions, and misplaced responsibilities

---

## 1. Review Scope

This exploration is based on:

- `platform/java/kernel/src/main/java/**`
- `platform/java/kernel/modules/**`
- `platform/java/kernel/src/test/java/**`
- `docs/kernel-platform-dev/**`
- `products/app-platform/**`
- prior PHR/AppPlatform integration review work
- `products/finance/**`
- `products/app-platform/finance-ghatana-integration-plan.md`
- `products/aura/**`

Key kernel files reviewed include:

- `KernelModule`
- `KernelContext`
- `DefaultKernelContext`
- `KernelRegistry`
- `KernelRegistryImpl`
- `KernelPlugin`
- `ProductPlugin`
- `KernelInterProductBus`
- `CrossProductWorkflowEngine`
- `CrossProductAuditService`
- `ProductBoundaryEnforcer`
- Data-Cloud and AEP adapters

Key design docs reviewed include:

- `docs/kernel-platform-dev/ARCHITECTURE_DOCUMENTATION.md`
- `docs/kernel-platform-dev/DETAILED_KERNEL_IMPLEMENTATION_PLAN.md`
- `docs/kernel-platform-dev/KERNEL_PLATFORM_BRAINSTORM.md`
- `docs/kernel-platform-dev/PLUGIN_BASED_ARCHITECTURE.md`
- `docs/kernel-platform-dev/API_DOCUMENTATION.md`
- finance integration and migration docs under `docs/` and `products/app-platform/`
- Aura master/spec/integration/event/testing docs under `products/aura/docs/`

---

## 1A. Source-Of-Truth And Authority Model

To make the convergence plan strong, we need a clear authority order.

### 1A.1 Canonical kernel authority

For kernel runtime contracts, use this order:

1. actual code in `platform/java/kernel/src/main/java/**`
2. convergence ADR and convergence planning docs
3. `docs/kernel-platform-dev/*`
4. migration-complete summaries only as historical context

Reason:

- some “final” or “complete” migration docs are stronger on intent than on current code reality

### 1A.2 Product authority

For product-specific rules, use product-local authority:

- **PHR**: `products/phr/docs/**`
- **Finance**: `products/app-platform/finance-ghatana-integration-plan.md` plus `products/finance/**`
- **Aura**: `products/aura/docs/Aura_Master_Platform_Specification.md` first, then specialized Aura docs

### 1A.3 Shared boundary authority

For shared event, data, security, and observability boundaries:

- use the strictest product-local rule as the forcing function for platform design
- then generalize it into reusable canonical kernel contracts

Examples:

- Aura requires AEP as its only cross-process event boundary
- Aura requires Data Cloud as its managed data plane
- PHR requires tenant, consent, and governance rigor
- Finance requires replayability, low latency, and regulatory evidence

---

## 2. What `platform/java/kernel` Already Is

`platform/java/kernel` already expresses a strong kernel idea:

### 2.1 A composition runtime

The kernel defines:

- a canonical module lifecycle through `KernelModule`
- runtime dependency and event access through `KernelContext`
- centralized discovery and dependency ordering through `KernelRegistry`
- capability-driven composition through `KernelCapability`
- tenant-aware execution through `KernelTenantContext`

This is the backbone of a real composition platform, not just a library collection.

### 2.2 A capability and plugin model

The kernel codebase supports two extension styles:

- `KernelPlugin`: module-shaped runtime plugins with install/uninstall/start/stop semantics
- `ProductPlugin`: product-declared capabilities, extensions, operators, and workflows

This signals a powerful intent:

- kernel modules provide shared platform primitives
- products contribute domain logic dynamically
- composition happens through contracts and capability discovery rather than static coupling

### 2.3 A cross-product coordination layer

The kernel also contains high-order coordination concepts:

- `KernelInterProductBus`
- `CrossProductWorkflowEngine`
- `CrossProductAuditService`
- `ProductBoundaryEnforcer`

Taken together, these are the beginnings of a logical control plane for:

- communication
- orchestration
- policy enforcement
- cross-product observability

### 2.4 Adapter-first infrastructure abstraction

The adapters to:

- Data-Cloud
- AEP

show a clear architectural decision:

- kernel should not own raw infrastructure complexity directly
- kernel should present a stable ActiveJ-first runtime surface
- deeper data and processing concerns should be delegated to shared infrastructure

This is a strong foundation for a platform concentrator.

---

## 3. What AppPlatform Already Is

`products/app-platform` already expresses a different but complementary strength:

### 3.1 A multi-domain operating platform

AppPlatform includes:

- kernel services
- domain packs
- infrastructure and operator docs
- upgrade and deployment runbooks
- governance, compliance, plugin, and routing architecture

It is much closer to a full platform product than `platform/java/kernel` is.

### 3.2 A production-facing decomposition model

AppPlatform already thinks in terms of:

- domain packs
- independent services
- tenant activation
- blue-green upgrades
- operator workflows
- observability and governance at platform scale

This makes it the stronger current basis for deployment and operationalization.

### 3.3 A broader front-door for products

AppPlatform is also where the repo currently frames:

- API gateway
- plugin runtime
- data governance
- audit
- resilience
- workflow orchestration
- operator and admin surfaces

So even though `platform/java/kernel` has a cleaner composition-core story, AppPlatform has the more complete operational platform story.

---

## 4. The Core Convergence Insight

The repo currently has two overlapping ideas:

### 4.1 Kernel model

`platform/java/kernel` says:

- kernel is the reusable runtime and composition center
- products register capabilities and integrate through plugins/adapters

### 4.2 AppPlatform model

`products/app-platform` says:

- platform provides multi-domain kernel services and domain-pack packaging
- products/domains are deployed and governed around that platform

### 4.3 Combined interpretation

These should be merged as follows:

| Layer | Combined role |
| --- | --- |
| Shared infra | Data-Cloud, AEP, event cloud, workflow runtime, AI services, object/data stores |
| Canonical kernel runtime | `platform/java/kernel` contracts and lifecycle model |
| AppPlatform implementation layer | concrete kernel services, deployment model, domain-pack activation, operator tooling |
| Domain packs | healthcare, finance, insurance, banking, etc. |
| Products | PHR, finance product, FlashIt, Aura, others |

This gives one coherent answer:

> `platform/java/kernel` becomes the canonical kernel contract model, and `products/app-platform` becomes the first major platform product that implements and operationalizes that model.

---

## 4A. Cross-Product Requirement Synthesis

The convergence plan must satisfy not only kernel and AppPlatform intent, but also the actual shape of Finance, PHR, and Aura.

### 4A.1 What Finance adds

Finance reinforces:

- low-latency Java 21 + ActiveJ runtime expectations
- event-driven and workflow-heavy operations
- strict audit, compliance, and replayability needs
- resilience and failure-path rigor
- clean separation between generic capability and domain logic

### 4A.2 What PHR adds

PHR reinforces:

- tenant isolation
- consent as a first-class control point
- explicit data classification and retention
- healthcare interoperability
- OCR, voice, and HITL workflows
- export/delete sensitivity and policy strictness

### 4A.3 What Aura adds

Aura reinforces:

- AI-native platform behavior from day one
- AEP as the only Aura cross-process event boundary
- Data Cloud as the authoritative Aura managed data plane
- modular-monolith-first delivery as a valid early topology
- explainability, drift, fairness, and trust telemetry as first-class concerns

### 4A.4 Unified requirement set

Therefore the combined kernel/AppPlatform model must be:

- **performant** enough for finance-class event and workflow load
- **governed** enough for PHR-class consent and regulated data handling
- **AI-native** enough for Aura-class recommendation, agents, and learning loops
- **flexible** enough for both modular-monolith-first and microservice-grade deployment shapes
- **interoperable** enough for FHIR, APIs, events, plugins, and domain integrations
- **customizable** enough to support tenant, domain, and product variation without kernel contamination

---

## 5. Target Platform Identity

The combined kernel should be defined as:

> an AI-native, policy-driven, full-spectrum development and operations platform in which the canonical kernel supplies the composition and enforcement model, and AppPlatform supplies the operational multi-domain realization of that model.

That identity has six non-negotiable traits:

1. **Driver**
   - drives lifecycle, activation, dependency ordering, and runtime composition
2. **Enforcer**
   - enforces tenancy, policy, security, consent, compliance, and isolation
3. **Orchestrator**
   - orchestrates workflows, operators, agents, plugins, and pack interactions
4. **Logical communication medium**
   - standardizes contracts, events, schemas, routing metadata, and observability context
5. **Aggregator and concentrator**
   - brings shared platform primitives into one coherent developer/runtime surface
6. **Full-frontier development platform**
   - supports UI/UX, middleware/integrations, backend/API, data, analytics, and autonomous systems under one model

### 5.1 Grand-vision operating principles

The merged architecture should preserve these operating principles as active design constraints:

- **Easy front door, powerful core**
  - product teams should work with stable, high-level contracts and manifests rather than raw infrastructure wiring
- **Opinionated core, flexible edges**
  - the kernel should enforce proven runtime patterns while allowing controlled customization through packs, policies, and manifests
- **AI-native by default**
  - observability, automation, explainability, agent controls, and adaptive behavior must be first-class platform concerns
- **Full lifecycle ownership**
  - the platform must support design-time, build-time, deploy-time, run-time, and change-time workflows coherently
- **Operational truth over architecture theatre**
  - every platform claim must map back to real modules, contracts, policies, and validation
- **Scale without fragmentation**
  - performance, topology flexibility, and domain isolation must improve without spawning parallel kernels

### 5.2 Detailed merge blueprint

The merge should happen by responsibility, not by file relocation alone.

#### A. Preserve and strengthen from `platform/java/kernel`

Keep and strengthen:

- canonical lifecycle model
- canonical module/plugin/extension contracts
- tenant and policy propagation hooks
- scope-aware orchestration and communication contracts
- capability and dependency model
- infrastructure abstraction through AEP/Data Cloud adapters

Change:

- remove duplicate abstractions
- generalize product-aware classes into scope-aware, policy-driven kernel services
- extend the kernel surface so it cleanly supports UI/schema/analytics/autonomous contracts

#### B. Preserve and strengthen from `products/app-platform`

Keep and strengthen:

- domain-pack model
- pack deployment/upgrade/certification concepts
- operator and governance tooling
- runtime operationalization
- multi-domain activation and deployment topologies

Change:

- stop treating AppPlatform as a competing kernel contract set
- re-scope generic runtime concerns back to the canonical kernel
- move domain-specific workflows and rules into packs/products

#### C. Preserve product pressure from Finance, PHR, and Aura

Use each product family as a forcing function:

- **Finance**
  - proves performance, replayability, compliance rigor, and deterministic evidence
- **PHR**
  - proves tenant isolation, consent, residency, healthcare interoperability, and HITL sensitivity
- **Aura**
  - proves AI-native event/data contracts, explainability, trust telemetry, and safe autonomy

The architecture is not complete until it can satisfy all three without special-case kernel forks.

### 5.3 Detailed plan shape

The active doc set should be read as one layered plan:

1. ADR
   - what the merged architecture is
2. this exploration and plan
   - why the merge is correct, what must be preserved, and how responsibilities divide
3. canonicalization decisions
   - which abstractions survive and how purity is restored
4. contract model
   - how all development frontiers plug into the same platform
5. module mapping and backlog
   - how the current repo is transformed
6. next-phase program board
   - how execution is sequenced and governed

That layered structure is intentional.
It is meant to keep the active set both high-vision and implementation-usable at the same time.

## 5.1 Driver

It drives:

- module lifecycle
- plugin lifecycle
- pack activation
- workflow registration
- tenant capability enablement
- environment-specific feature activation

## 5.2 Enforcer

It enforces:

- domain boundaries
- cross-product access rules
- tenant isolation
- configuration hierarchy
- security and compliance policy hooks
- operational contracts for plugins/packs/products

## 5.3 Orchestrator

It orchestrates:

- workflows
- operators
- agents
- integrations
- long-running product processes
- cross-domain requests and compensations

## 5.4 Logical communication medium

It is the logical communication medium for:

- events
- commands
- workflow triggers
- audit flow
- configuration propagation
- product-to-product contracts

This does not mean the kernel must be the physical transport for everything.
It means the kernel defines and governs the logical communication contracts and routing model.

## 5.5 Aggregator and concentrator

It aggregates and concentrates:

- infrastructure abstractions
- shared services
- developer contracts
- policy engines
- observability and control metadata

So product teams build against one coherent platform surface instead of stitching together many systems directly.

---

## 6. Support For All Development Frontiers

The combined kernel should explicitly support all of the frontiers you named.

### 6.1 UI/UX development

Kernel role:

- provide design-system and route-contract integration points
- expose feature flags, actor context, tenant context, policy context, and navigation metadata
- provide a schema/contract registry consumed by UI tooling
- support workflow-aware UI surfaces such as human tasks, review queues, consent prompts, and operator consoles

AppPlatform role:

- admin portal shells
- product shell composition
- manifest-driven UI contribution points
- domain-pack and product UI activation

Target outcome:

- frontend teams build against stable contracts, policy-aware context, and feature/tenant activation from the kernel

### 6.2 Middleware and integrations

Kernel role:

- standard integration contracts
- plugin/operator lifecycle
- secrets and policy checks
- retry/circuit/DLQ behaviors
- event and workflow integration abstractions

AppPlatform role:

- runtime adapter deployment
- gateway and routing
- operator workflows
- pack-level integration activation and certification

Target outcome:

- integrations are declared, governed, observable, and replaceable rather than hand-wired per product

### 6.3 Backend and API development

Kernel role:

- module/service lifecycle
- capability discovery
- tenant context
- auth and policy hooks
- event and workflow primitives
- backend composition contracts

AppPlatform role:

- concrete kernel services
- gateway
- deployment topology
- pack activation and scaling

Target outcome:

- backend teams develop bounded contexts on top of kernel contracts instead of rebuilding infra concerns repeatedly

### 6.4 Data management

Kernel role:

- Data-Cloud abstraction
- data classification hooks
- audit hooks
- tenant propagation
- schema and dataset coordination
- data sharing contracts across products

AppPlatform role:

- governance services
- retention/deletion controls
- pack-specific data model activation
- operational data policies

Target outcome:

- data is managed through one logical platform contract with domain-aware policy on top

### 6.5 Analytics

Kernel role:

- event and data pipelines as first-class capabilities
- unified observability and telemetry
- analytics contract registration
- metric and feature extraction hooks

AppPlatform role:

- operational dashboards
- domain-pack metrics
- governance and reporting layers

Target outcome:

- analytics is not bolted on later; it is a platform-native consequence of data/event contracts

### 6.6 Autonomous and agentic systems

Kernel role:

- AEP-backed operator and agent orchestration
- workflow/agent coordination
- policy and audit wrapping
- task routing and feedback loops

AppPlatform role:

- domain-specific AI governance
- model approval/deployment integration
- operator workflow tooling
- runtime visibility and rollback

Target outcome:

- autonomous behavior runs inside governed kernel lanes rather than as isolated sidecars

---

## 6A. Hardened Architecture Principles

The convergence plan should adopt these as non-negotiable architecture principles.

### 6A.1 Shared event and data plane principle

- AEP should be treated as the default shared cross-process event plane.
- Data Cloud should be treated as the default shared managed data plane.
- Products may define semantics, schemas, retention classes, and trust rules, but should not bypass these planes without ADR-level exception.

### 6A.2 Modular boundary persistence principle

- service boundaries must remain valid whether modules are:
  - in one deployable
  - in many deployables
- same-process deployment must not justify cross-domain repository writes or hidden code coupling

### 6A.3 Tenant, consent, and policy first principle

- tenant propagation is mandatory at all layers
- policy hooks must exist at gateway, service, workflow, event, and data boundaries
- regulated-domain products such as PHR must be able to inject stricter policy without forking kernel logic

### 6A.4 Explainability and auditability principle

- all autonomous, AI, recommendation, and critical decision flows must support:
  - reason codes
  - trace context
  - audit correlation
  - model/version attribution
  - replayability where required

### 6A.5 Performance tiering principle

The kernel approach must support distinct runtime tiers:

- **hot path**: low-latency synchronous execution
- **async path**: event/workflow/agent execution
- **batch/nearline path**: training, analytics, exports, large reprocessing

Products should declare which tier a path belongs to, and the platform should apply appropriate execution, retry, timeout, and observability policies.

### 6A.6 Customization without contamination principle

Customization is allowed only through:

- domain packs
- product packs
- plugins/extensions/manifests
- policy packs
- config and feature activation

Customization must not require direct kernel core modification.

### 6A.7 AI-native pervasive principle

AI-native means:

- agent/operator lifecycle is first-class
- model governance is first-class
- drift/fairness/quality telemetry is first-class
- AI workflows remain policy-wrapped and observable
- deterministic fallback paths exist for critical flows

It does **not** mean every request must invoke an LLM.

---

## 6B. Required Non-Functional Capability Model

To be solid, performant, scalable, interoperable, flexible, AI-native, and customizable, the combined platform needs explicit non-functional design lanes.

### 6B.1 Performance and scalability lane

Needed capabilities:

- async ActiveJ-native execution discipline
- workload tiering
- hot-path caching and invalidation contracts
- event partitioning and ordering contracts
- workflow backpressure and compensation
- deployment-time scaling and canary hooks

### 6B.2 Interoperability lane

Needed capabilities:

- API/schema contract registry
- event contract registry
- plugin/pack integration contracts
- domain-specific interoperability packs
- shared auth/security/o11y wiring

### 6B.3 Governance and trust lane

Needed capabilities:

- policy engine contract
- data governance contract
- audit contract
- consent and purpose-of-use hook points
- export/delete/retention lifecycle support

### 6B.4 AI-native and autonomous lane

Needed capabilities:

- operator catalog
- agent orchestration contract
- model registry/inference governance integration
- quality and drift feedback loops
- human-task / HITL workflow support

### 6B.5 Developer platform lane

Needed capabilities:

- route/schema registry for frontend and backend
- pack manifests
- scaffolding and SDK support
- integration test harnesses
- topology-aware local/staging/prod workflows

---

## 6C. Product-Informed Architecture Constraints

The hardened plan should respect these product-driven constraints.

### Aura-informed constraints

- AEP is the only Aura cross-process event boundary
- Data Cloud is the managed Aura data plane
- modular-monolith-first is allowed for early Aura delivery
- explainability, fairness, drift, and trust telemetry are release-critical

### PHR-informed constraints

- consent and tenant isolation are never optional
- classification, retention, audit, and export/delete paths are mandatory
- healthcare adapters and FHIR semantics must stay outside the generic kernel

### Finance-informed constraints

- performance-sensitive paths need deterministic execution lanes
- domain workflows and market semantics must stay outside generic kernel
- regulatory evidence and replayability must be preserved end to end

---

## 7. Current Gaps And Conflicts We Must Resolve

This convergence is promising, but the current repo is not yet internally unified.

### 7.1 Duplicate and competing abstractions

There are overlapping concepts that must be normalized:

- `com.ghatana.kernel.descriptor.KernelCapability`
- `com.ghatana.kernel.capability.KernelCapability`
- `com.ghatana.kernel.extension.KernelExtension`
- `com.ghatana.kernel.plugin.KernelExtension`
- `KernelPlugin`
- `ProductPlugin`
- `KernelRegistry`
- `PluginRegistry` + `CapabilityRegistry` + `ServiceRegistry`

This is the clearest sign that the architecture is currently split between two design generations.

### 7.2 Kernel purity is only partially true today

Some kernel classes remain product-aware or hardcoded:

- `CrossProductAuditService` hardcodes finance and PHR retention semantics
- `ProductBoundaryEnforcer` hardcodes product ids such as `phr`, `finance`, `flashit`, `aura`
- `KernelInterProductBus` and `CrossProductWorkflowEngine` speak in source/target product terms directly

These are useful experiments, but they are not yet a pure generic kernel.

### 7.3 Documentation overstates current readiness

`docs/kernel-platform-dev/FINAL_VALIDATION_REPORT.md` says the kernel is production ready.
That is too strong relative to the codebase because:

- some integration tests are disabled
- cross-product infrastructure contracts are still shifting
- the abstraction model is duplicated in places
- there is not yet a single canonical runtime model shared by kernel and AppPlatform

### 7.4 AppPlatform and kernel are not yet contract-aligned

AppPlatform currently has its own:

- kernel naming
- plugin and domain-pack framing
- workflow and operator structures
- deployment and activation assumptions

Those are not yet explicitly implemented as the runtime contract of `platform/java/kernel`.

### 7.5 Frontend and developer-experience concerns are still mostly described, not platformized

The vision in `docs/kernel-platform-dev/KERNEL_PLATFORM_BRAINSTORM.md` correctly says the kernel should support UI/UX, API, and lifecycle development, but the current implementation is still much stronger on backend composition than on end-to-end developer platform capabilities.

---

## 8. Target Convergence Architecture

The recommended target architecture is:

```text
Products
  PHR
  Finance
  FlashIt
  Aura
  Future products
    |
    v
Domain Packs / Product Packs
  Healthcare
  Finance
  Insurance
  Banking
  Cross-domain shared packs
    |
    v
AppPlatform Runtime Product
  API gateway
  pack activation
  operator/admin tooling
  deployment management
  governance and observability integration
    |
    v
Canonical Kernel Runtime (`platform/java/kernel`)
  module lifecycle
  capability registry
  service registry
  plugin runtime
  workflow contracts
  event contracts
  boundary enforcement
  tenant context
  policy hooks
    |
    v
Shared Infrastructure
  Data-Cloud
  AEP
  Event Cloud
  Workflow runtime
  AI services
  Secrets / storage / telemetry backends
```

In this model:

- the kernel is the **canonical control-plane runtime**
- AppPlatform is the **first full platform implementation on top of it**
- domain packs are the **business/domain packaging model**
- products are the **final application surfaces**

---

## 9. Canonical Role Split

### 9.1 What belongs in the canonical kernel

- module/plugin/extension lifecycle
- capability model
- dependency resolution
- service and contract registry
- tenant context propagation
- config resolution contract
- event publication/subscription contract
- workflow registration/execution contract
- operator/agent registration contract
- boundary enforcement framework
- audit policy hooks
- logical pack/product activation model

### 9.2 What belongs in AppPlatform

- concrete kernel service implementations
- production runtime wiring
- gateway and ingress control plane
- pack deployment and activation tooling
- operator/admin portals
- certification and rollout processes
- observability and governance composition
- multi-domain deployment topologies

### 9.3 What belongs in domain packs

- healthcare semantics
- finance semantics
- insurance semantics
- banking semantics
- domain workflows
- domain-specific rules
- domain integrations
- domain UIs and route contributions

### 9.4 What belongs in products

- product-specific user journeys
- product packaging
- product-facing APIs and shells
- product activation presets
- experience-layer composition

---

## 10. Recommended Unification Decisions

### 10.1 Make `platform/java/kernel` the canonical contract source

All kernel contracts should live here:

- `KernelModule`
- `KernelContext`
- `KernelPlugin`
- `KernelExtension`
- `KernelCapability`
- `KernelDependency`
- runtime registries

AppPlatform should implement against these, not redefine them conceptually in parallel.

### 10.2 Collapse duplicate abstractions

Adopt one canonical abstraction for each of:

- capability
- extension
- plugin
- registry

Recommended simplification:

- keep `KernelPlugin` as the canonical runtime-loadable extension model
- reinterpret `ProductPlugin` as a higher-level convenience or migrate it into a domain-pack/product manifest model
- keep only one `KernelCapability` type
- keep only one `KernelExtension` type

### 10.3 Convert hardcoded cross-product services into policy-driven generic services

Refactor:

- `CrossProductAuditService`
- `ProductBoundaryEnforcer`
- `KernelInterProductBus`
- `CrossProductWorkflowEngine`

so they operate on:

- pack ids
- product ids
- capability ids
- policy descriptors
- retention classes
- data classifications

not hardcoded product strings or domain rules.

### 10.4 Reframe AppPlatform kernel modules as kernel-runtime implementations

AppPlatform modules such as:

- IAM
- config
- plugin runtime
- event store
- audit
- data governance
- AI governance
- resilience
- gateway

should be positioned as:

> concrete implementations of canonical kernel capabilities

not as a parallel kernel theory.

### 10.5 Use domain packs as the official multi-domain packaging abstraction

This should become the bridge between the kernel model and AppPlatform:

- kernel defines runtime contracts
- domain packs implement domain semantics
- AppPlatform activates/deploys/manages them

### 10.6 Elevate developer platform features into first-class kernel capability groups

Create explicit capability groups for:

- UI composition and route contracts
- API/schema contracts
- integration adapters
- data products and analytics
- human-task and workflow UX
- agent/operator/autonomous execution

This is how the kernel becomes a full development platform rather than only a backend runtime.

### 10.7 Standardize product/platform integration contracts

The convergence plan should formalize a single integration contract family for all products:

- event contracts
- data product registration
- API/schema registration
- policy and classification metadata
- UI contribution metadata
- workflow/operator/agent metadata

This is needed so Finance, PHR, Aura, and future products all integrate with the kernel the same way.

### 10.8 Introduce architecture review gates tied to source docs

Every major kernel/platform design change should be checked against:

- canonical kernel contracts
- Finance integration plan
- PHR source-of-truth docs
- Aura master/shared platform specs

This prevents “kernel purity” from drifting into something that is elegant in theory but unusable by the products.

---

## 11. Phased Convergence Plan

### Phase 0: Canonicalization

Goal:

- decide the canonical kernel contract set

Actions:

- choose one capability model
- choose one extension model
- choose one plugin model
- map current AppPlatform kernels to canonical kernel capabilities
- publish a kernel-app-platform convergence ADR

Exit criteria:

- one contract family only
- no ambiguous “kernel of the kernel” duplication

### Phase 1: Purify the kernel

Goal:

- make the canonical kernel truly generic

Actions:

- remove hardcoded product/domain retention logic
- replace product-specific boundary logic with policy-driven evaluation
- convert cross-product services into cross-pack/cross-capability services
- keep kernel vocabulary domain-neutral

Exit criteria:

- kernel passes stricter purity tests than it does now
- no healthcare/finance-specific behavior remains in kernel core classes

### Phase 2: Align AppPlatform to the canonical kernel

Goal:

- make AppPlatform the operational implementation layer of the kernel

Actions:

- map each AppPlatform kernel module to canonical kernel capabilities
- refactor AppPlatform docs to reference the canonical kernel contracts
- isolate any domain-specific logic that still lives in AppPlatform kernel modules
- standardize pack activation/deployment terminology

Exit criteria:

- AppPlatform reads as an implementation of the kernel model, not a competing architecture

### Phase 3: Establish domain-pack-first product assembly

Goal:

- make domain packs the official domain construction layer

Actions:

- create healthcare pack
- normalize finance pack structure
- define pack manifests, dependencies, exported contracts, UI contributions, workflows, and integrations
- ensure packs are independently activatable and co-deployable

Exit criteria:

- healthcare-only, finance-only, and multi-pack shared-kernel deployments all work conceptually and operationally

### Phase 4: Expand the kernel into a true developer platform

Goal:

- support all frontiers of product development

Actions:

- add schema/contract registry for APIs and frontend routes
- add workflow/human-task UI contribution model
- add integration adapter SDK and certification path
- add analytics and autonomous execution capability groups
- add product scaffolding from kernel/pack manifests

Exit criteria:

- frontend, backend, integration, data, analytics, and autonomous teams all build against one platform surface

### Phase 4A: Harden architecture against product stress cases

Goal:

- validate that the kernel approach survives Finance, PHR, and Aura stress cases

Validation lenses:

- Finance: throughput, resilience, audit, replay
- PHR: consent, tenancy, policy strictness, export/delete
- Aura: AI orchestration, drift/fairness, AEP/Data Cloud boundaries, modular-monolith-first topology

Exit criteria:

- one architecture can explain and support all three without special-case kernel forks

### Phase 5: Validate with real products

Goal:

- prove the convergence through product delivery

Validation products:

- PHR
- Finance
- one lighter product such as FlashIt or Aura

Success criteria:

- each product can run independently
- products can coexist on the same kernel estate
- no hidden direct coupling between domain packs
- workflow, audit, policy, and observability behave consistently across all products

### Phase 5A: Add architecture scorecards

Each validation product should be scored across:

- kernel purity
- deployment independence
- shared-kernel coexistence
- event/data boundary compliance
- performance tiering correctness
- AI governance and explainability
- customization without core modification

---

## 12. Immediate Deliverables Recommended

1. A convergence ADR defining:
   - canonical kernel contracts
   - AppPlatform’s role as operational runtime product
   - domain-pack-first multi-domain assembly

2. A kernel contract cleanup task list covering:
   - duplicate classes/interfaces
   - hardcoded product-specific logic
   - plugin/extension/capability normalization

3. An AppPlatform capability mapping document:
   - each AppPlatform kernel module
   - canonical kernel capability mapping
   - whether it is generic, operational, or domain-specific

4. A domain-pack manifest standard:
   - dependencies
   - exported APIs/events
   - workflows
   - UI contributions
   - activation rules

5. A developer-platform expansion plan:
   - UI/UX support
   - API/schema support
   - integration SDK support
   - analytics support
   - autonomous support
6. A product-constraint scorecard template for Finance, PHR, Aura, and future products
7. A source-of-truth governance note identifying which docs are authoritative, advisory, outdated, or contradictory

---

## 13. Final Recommendation

The right architectural move is:

> **promote `platform/java/kernel` into the canonical kernel contract/runtime model, and evolve AppPlatform into the production multi-domain platform that implements, operationalizes, and scales that kernel model.**

That gives Ghatana one coherent platform story:

- one kernel
- one operational platform product
- many domain packs
- many products
- one development surface across all frontiers

This is the model most capable of making the kernel what you described:

- **driver**
- **enforcer**
- **orchestrator**
- **logical communication medium**
- **aggregator**
- **concentrator**
- **full-spectrum development platform**

It is also the model most likely to remain robust under:

- Finance-grade performance and compliance stress
- PHR-grade governance and interoperability stress
- Aura-grade AI-native and learning-loop stress

---

## 14. Bottom Line

Do not merge these codebases by flattening them together.

Merge them by **assigning each one its proper level**:

- `platform/java/kernel`: canonical kernel runtime and contracts
- `products/app-platform`: operational multi-domain platform implementation
- domain packs: business/domain packaging
- products: end-user and business application surfaces

That preserves the strengths of both and creates a platform that can genuinely support:

- UI/UX development
- middleware and integrations
- backend and API development
- data management
- analytics
- autonomous and agentic systems

without turning the kernel into a domain-specific monolith.
