# Kernel Next-Phase Architecture Program Board

**Date**: 2026-03-19  
**Version**: 2.0  
**Status**: Proposed next-phase architecture program  
**Purpose**: Convert the convergence exploration, canonicalization decisions, and contract model into a sequenced execution program

---

## 1. Program Goal

The next phase should turn the current repo from:

- a promising but partially overlapping kernel/platform architecture

into:

- one canonical kernel model
- one operational AppPlatform built on that model
- domain-pack-first product development
- contract-driven support for UI, middleware, backend, API, data, analytics, and autonomous systems
- honest readiness reporting tied to executable validation

---

## 2. Program Outcomes

At the end of this phase, the architecture should support all of the following without kernel forks:

- independent single-domain deployment
- multi-domain shared-kernel deployment
- Finance, PHR, and Aura as first-class stress cases
- AEP as default cross-process event plane
- Data Cloud as default managed data plane
- policy-driven rather than product-driven enforcement
- AI-native operation with explainability, governance, and safe autonomy

---

## 3. Architectural Epics

### Epic A. Canonicalize the kernel core

**Goal**

Eliminate duplicate abstractions and publish one canonical kernel vocabulary.

**Includes**

- one capability model
- one extension model
- one runtime plugin model
- one public registry root

**Depends on**

- convergence ADR and canonicalization decisions

**Exit criteria**

- legacy duplicates are frozen and migration paths are explicit
- new work cannot target non-canonical abstractions

### Epic B. Purify canonical kernel packages

**Goal**

Remove product-aware logic from generic kernel implementations.

**Includes**

- cross-scope audit policy resolution
- scope boundary enforcement
- scope-based communication contracts
- scope-based workflow contracts

**Depends on**

- Epic A

**Exit criteria**

- no hardcoded product ids drive canonical kernel behavior
- Finance, PHR, and Aura differences come from policy/manifests, not kernel branching

### Epic C. Establish the developer-platform contract system

**Goal**

Create shared contract families for experience, APIs, schemas, analytics, autonomy, and deployment.

**Includes**

- contract grammars
- validators
- compatibility rules
- ownership rules

**Depends on**

- Epic A

**Exit criteria**

- all core contract families are versioned and validated in CI

### Epic D. Align AppPlatform to the canonical kernel

**Goal**

Make AppPlatform the operational platform built on the kernel, not a parallel kernel.

**Includes**

- capability mapping
- workflow/operator re-scoping
- manifest and certification alignment
- deployment profile alignment

**Depends on**

- Epics A, B, C

**Exit criteria**

- AppPlatform docs and module ownership map cleanly to canonical kernel capabilities and contract families

### Epic E. Prove the model against Finance, PHR, and Aura

**Goal**

Use the hardest current products as architecture forcing functions.

**Includes**

- Finance replay/compliance/performance stress cases
- PHR consent/tenant/residency/schema stress cases
- Aura explainability/autonomy/event/data stress cases

**Depends on**

- Epics B, C, D

**Exit criteria**

- one representative slice from each product is modeled and validated end-to-end against the new contracts

### Epic F. Correct readiness and validation governance

**Goal**

Ensure repo claims match executable validation reality.

**Includes**

- downgrade historical “complete/production-ready” docs
- introduce status taxonomy
- define evidence requirements for readiness claims

**Depends on**

- none

**Exit criteria**

- no active planning doc overclaims completion without evidence

---

## 4. Sequencing

### Wave 1. Authority and drift control

Focus:

- Epic A
- Epic F

Why first:

- we need one vocabulary and honest status markers before deeper design work can stick

### Wave 2. Generic kernel hardening

Focus:

- Epic B
- beginning of Epic D

Why second:

- this removes the architectural debt that would otherwise leak into every pack and product

### Wave 3. Contract-system buildout

Focus:

- Epic C
- remaining design work for Epic D

Why third:

- once the kernel is generic, the contract model becomes the main developer surface

### Wave 4. Product stress-case validation

Focus:

- Epic E

Why fourth:

- Finance, PHR, and Aura prove whether the design is actually flexible and scalable

### Wave 5. Readiness scoring and adoption

Focus:

- final operationalization of Epics D, E, F

Why fifth:

- this is where architecture becomes a managed platform program instead of a document set

---

## 5. Detailed Work Packages

### WP-A1. Capability canonicalization

Acceptance criteria:

- `descriptor.KernelCapability` is the published canonical type
- legacy capability package is marked transitional
- mapping guidance exists for all current consumers

### WP-A2. Extension and plugin canonicalization

Acceptance criteria:

- `extension.KernelExtension` is canonical
- `KernelPlugin` is the canonical runtime plugin contract
- `ProductPlugin` is documented as transitional or manifest-level only

### WP-A3. Registry canonicalization

Acceptance criteria:

- `KernelRegistry` is documented as the public root registry
- sub-registries are classified as internal or SPI

### WP-B1. Audit policy extraction

Acceptance criteria:

- retention is policy-resolved
- no canonical kernel class branches on `finance`, `phr`, `aura`, or other product ids

### WP-B2. Boundary policy extraction

Acceptance criteria:

- scope boundary rules are manifest/policy driven
- consent, audit, and residency checks are provider-driven

### WP-B3. Inter-scope event contract

Acceptance criteria:

- stable envelope defined
- AEP alignment defined
- schema versioning and telemetry minimums defined

### WP-B4. Inter-scope workflow contract

Acceptance criteria:

- generic workflow context defined
- compensation and SLA policy hooks defined

### WP-C1. Experience contract grammar

Acceptance criteria:

- route/screen/action manifest schema exists
- tenant/role gating hooks are standardized

### WP-C2. API and integration contract grammar

Acceptance criteria:

- route/schema/auth/idempotency metadata standardized
- compatibility and contract-test expectations documented

### WP-C3. Data and analytics contract grammar

Acceptance criteria:

- schema, classification, dataset, telemetry, and evidence contracts are defined
- Data Cloud binding rules are explicit

### WP-C4. Autonomous contract grammar

Acceptance criteria:

- agent tiering, approval rules, evaluation, and rollback semantics are defined

### WP-D1. AppPlatform module re-scoping

Acceptance criteria:

- generic workflow/operator services stay in kernel/platform
- finance-specific workflows move to domain packs or product modules

### WP-D2. Domain-pack contract integration

Acceptance criteria:

- domain-pack manifests declare capabilities, schemas, workflows, and policies in the shared grammar

### WP-E1. Finance reference slice

Acceptance criteria:

- one finance slice proves performance, compliance, replay, and audit evidence using the new model

### WP-E2. PHR reference slice

Acceptance criteria:

- one PHR slice proves consent, tenancy, route/schema compatibility, and residency controls

### WP-E3. Aura reference slice

Acceptance criteria:

- one Aura slice proves AI-native telemetry, explainability, event-plane usage, and autonomous controls

### WP-F1. Readiness taxonomy

Acceptance criteria:

- approved statuses defined, such as `historical`, `planned`, `design-ready`, `implementation-in-progress`, `validated`, `production-operational`
- evidence required for each status is documented

---

## 6. Decision Gates

The next phase should include explicit architecture gates.

### Gate G1. Canonical vocabulary gate

No new kernel or AppPlatform design work proceeds until canonical abstractions are accepted.

### Gate G2. Kernel purity gate

No canonical kernel service may ship with product-id branching or product-owned workflows.

### Gate G3. Contract completeness gate

No new domain pack should be certified without contract declarations for API/schema/telemetry and required policy metadata.

### Gate G4. Product stress-case gate

No architecture should be declared platform-ready until Finance, PHR, and Aura all pass representative reference-slice validation.

### Gate G5. Evidence gate

No document may claim completion or readiness without executable evidence or an explicit historical note.

---

## 7. Risks And Controls

### Risk 1. Canonicalization stalls because teams keep using legacy abstractions

Control:

- publish canonicalization doc
- freeze drift
- add purity and architecture review checks

### Risk 2. AppPlatform remains a shadow kernel

Control:

- force capability/module mapping reviews
- require re-scoping of workflow/operator services

### Risk 3. AI-native ambitions outpace governance

Control:

- autonomous contract family must ship with approval, telemetry, and kill-switch semantics before wide adoption

### Risk 4. Product-specific exceptions re-enter the kernel

Control:

- route all exceptions through policy packs and manifests
- extend purity validation to forbid known product string branching

### Risk 5. Docs drift ahead of code again

Control:

- introduce status taxonomy and evidence gate
- mark historical docs clearly

---

## 8. Suggested Ownership Pattern

This is a planning suggestion, not a firm org chart.

- Kernel architecture owner
  - owns Epics A and B
- Developer platform/contracts owner
  - owns Epic C
- AppPlatform/platform operations owner
  - owns Epic D
- Product architecture delegates for Finance, PHR, Aura
  - co-own Epic E
- Architecture governance owner
  - owns Epic F and decision gates

---

## 9. What “Next Best Planning Phase” Means

The immediate next-best phase is not another broad exploration.

It is a controlled architecture program with this exact near-term order:

1. accept canonicalization decisions
2. freeze non-canonical drift and overclaiming docs
3. define the contract grammars and validation gates
4. re-scope AppPlatform modules to the canonical model
5. validate the design on Finance, PHR, and Aura reference slices

That is the shortest path to a kernel approach that is solid, performant, scalable, interoperable, flexible, AI-native, and customizable without degenerating into product-specific forks.
