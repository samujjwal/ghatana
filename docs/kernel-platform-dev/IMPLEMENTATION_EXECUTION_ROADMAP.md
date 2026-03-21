# Implementation Execution Roadmap

**Date**: 2026-03-19  
**Version**: 2.0  
**Status**: Active execution roadmap  
**Purpose**: Provide a day-by-day, file-by-file implementation plan that is consistent with the active architecture docs, reuse-first, production-grade, and repo-grounded

---

## 1. Execution Rules

This roadmap assumes:

- reuse first
- no duplicate abstractions
- generic kernel purity is non-negotiable
- domain logic moves to packs/products, not deeper into the kernel
- every change must be testable
- every milestone must leave the repo in a cleaner state than before

---

## 2. Day-By-Day Plan

This plan is structured as 20 implementation days.

### Day 1. Freeze architecture drift

Goal:

- stop new non-canonical abstractions from entering the codebase

Primary files:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/capability/KernelCapability.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelExtension.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/ProductPlugin.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/CapabilityRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/ServiceRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/PluginRegistry.java`

Actions:

- mark transitional abstractions clearly in code comments or deprecation annotations
- add doc references to canonical replacements
- do not remove anything yet

Definition of done:

- the codebase clearly signals which abstractions are canonical and which are transitional

### Day 2. Strengthen kernel purity checks

Primary files:

- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/validation/KernelPurityValidationTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/integration/PhrFinanceCrossProductIntegrationTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/integration/PhrFinanceIntegrationTest.java`

Actions:

- extend purity validation to detect duplicate abstraction usage
- add checks for hardcoded product-id branching in canonical kernel packages
- document which disabled tests are architectural placeholders vs broken validations

Definition of done:

- purity tests fail for reintroduced product-aware branching and duplicate abstraction usage

### Day 3. Canonical capability migration pass

Primary files:

- all consumers of `com.ghatana.kernel.capability.KernelCapability`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/KernelCapability.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/KernelRegistry.java`

Actions:

- migrate imports and call sites to `descriptor.KernelCapability`
- keep compatibility adapters only where necessary

Definition of done:

- no active kernel module depends on the legacy capability package

### Day 4. Canonical extension and plugin migration pass

Primary files:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/extension/KernelExtension.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelExtension.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelPlugin.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/ProductPlugin.java`

Actions:

- keep `extension.KernelExtension` as canonical
- decide and implement `ProductPlugin` as transitional adapter or manifest-level compatibility layer
- update consumers to the canonical extension contract

Definition of done:

- only one public extension contract remains active in new code

### Day 5. Registry canonicalization

Primary files:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/KernelRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/KernelRegistryImpl.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/CapabilityRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/ServiceRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/PluginRegistry.java`

Actions:

- establish `KernelRegistry` as the root public contract
- reclassify helper registries as internal helpers or compatibility facades

Definition of done:

- public registry semantics are unambiguous in code and docs

### Day 6. Audit policy extraction

Primary files:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/audit/CrossProductAuditService.java`
- proposed new package `platform/java/kernel/src/main/java/com/ghatana/kernel/policy/`
- proposed new package `platform/java/kernel/src/main/java/com/ghatana/kernel/scope/`

Actions:

- replace hardcoded retention branching with policy/provider design
- introduce generic scope and classification inputs
- preserve behavior via policy mappings, not product string checks

Definition of done:

- kernel audit logic becomes policy-driven and scope-aware

### Day 7. Boundary policy extraction

Primary files:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/ProductBoundaryEnforcer.java`
- proposed new boundary/policy interfaces under `platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/`

Actions:

- generalize to scope-aware evaluation
- separate consent, residency, and access-policy resolution

Definition of done:

- boundary logic no longer depends on hardcoded product allowlists

### Day 8. Communication contract generalization

Primary files:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/communication/KernelInterProductBus.java`
- relevant AEP adapter files under `platform/java/kernel/src/main/java/com/ghatana/kernel/adapter/aep/`

Actions:

- redesign around inter-scope envelope semantics
- keep AEP as the default cross-process event backend

Definition of done:

- logical communication contract supports product, pack, tenant, workflow, and agent scopes

### Day 9. Workflow contract generalization

Primary files:

- `platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/CrossProductWorkflowEngine.java`
- related workflow context types

Actions:

- shift from product-to-product framing to scope graph/workflow context framing
- preserve compensation, orchestration, and operator integration intent

Definition of done:

- generic orchestration contract exists without product-specific assumptions

### Day 10. Remove legacy duplicates

Primary files:

- legacy capability/extension/registry files identified in Days 1-5

Actions:

- remove or isolate dead duplicates once migrations are complete
- update tests and docs together

Definition of done:

- duplicate abstraction surface is materially reduced and no longer used by active code

### Day 11. AppPlatform generic-core alignment pass

Primary files:

- `products/app-platform/kernel/iam/**`
- `products/app-platform/kernel/config-engine/**`
- `products/app-platform/kernel/event-store/**`
- `products/app-platform/kernel/audit-trail/**`
- `products/app-platform/kernel/resilience-patterns/**`
- `products/app-platform/kernel/secrets-management/**`

Actions:

- map each module to canonical kernel capabilities
- identify what should stay as operational implementation vs be lifted/reused in `platform/java/kernel/modules/**`

Definition of done:

- generic reuse candidates are explicitly classified

### Day 12. Workflow-orchestration extraction pass

Primary files:

- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/WorkflowExecutionRuntimeService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/WorkflowDefinitionService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/ParallelStepExecutionService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/CelExpressionEvaluatorService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/TradeSettlementWorkflowService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/CorporateActionWorkflowService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/RegulatoryReportSubmissionWorkflowService.java`

Actions:

- keep generic runtime services in the platform/kernel lane
- move finance-shaped workflows to finance pack/product ownership

Definition of done:

- workflow runtime and domain workflows are separated cleanly

### Day 13. Operator-workflows extraction pass

Primary files:

- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/TenantRegistryService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/TenantConfigIsolationService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/JurisdictionRegistryService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/CrossJurisdictionReportingService.java`

Actions:

- keep generic tenant/operator capabilities
- push jurisdiction/domain-specific behavior to packs/products where required

Definition of done:

- operator layer becomes generic enough for multi-domain reuse

### Day 14. Ledger-framework classification pass

Primary files:

- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/domain/**`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/service/**`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/dtc/**`

Actions:

- separate generic immutable-ledger primitives from finance-shaped workflows/reconciliation services
- decide rename or pack extraction boundaries

Definition of done:

- ledger framework has a clear generic-core vs finance-domain split

### Day 15. Contract-system skeleton

Primary files:

- proposed contract packages under `platform/java/kernel/src/main/java/com/ghatana/kernel/contracts/`
- existing gateway/schema/event/governance modules in AppPlatform for reuse patterns

Actions:

- introduce contract family skeletons for experience, API, schema, analytics, autonomy, and packaging
- define minimal metadata and validation hooks

Definition of done:

- contract layer exists as a real implementation target, not just a doc concept

### Day 16. Event/schema validation alignment

Primary files:

- `products/app-platform/kernel/event-store/src/main/java/com/ghatana/appplatform/eventstore/validation/**`
- `products/app-platform/kernel/api-gateway/src/main/java/com/ghatana/appplatform/gateway/schema/**`
- `products/app-platform/kernel/data-governance/src/main/java/com/ghatana/appplatform/governance/SchemaEvolutionService.java`

Actions:

- reuse existing schema validation patterns
- align them to the canonical contract model

Definition of done:

- service, gateway, and event schema validation have one consistent direction

### Day 17. AI/analytics/autonomy contract alignment

Primary files:

- `products/app-platform/kernel/ai-governance/**`
- `products/app-platform/kernel/audit-trail/**`
- `products/app-platform/kernel/platform-manifest/**`

Actions:

- align explainability, audit, model governance, and rollout/certification hooks to the contract model
- define safe-autonomy validation expectations

Definition of done:

- Aura-class requirements have an implementation lane in the active platform model

### Day 18. Finance reference-slice planning pass

Primary files:

- finance pack/product docs and finance domain modules
- finance-shaped AppPlatform extractions identified on Days 12-14

Actions:

- select one finance reference slice for validation
- map required generic services vs pack-owned logic

Definition of done:

- finance validation path is concrete and file-scoped

### Day 19. PHR reference-slice planning pass

Primary files:

- `products/phr/docs/**`
- `PHR_AppPlatform_Integration_Analysis_Report.md`

Actions:

- select one PHR reference slice
- confirm contract, consent, and tenancy dependencies

Definition of done:

- healthcare validation path is concrete and file-scoped

### Day 20. Aura reference-slice planning pass and release gate review

Primary files:

- `products/aura/docs/**`
- contract, audit, event, and AI-governance implementation lanes

Actions:

- select one Aura reference slice
- finalize program gates, evidence expectations, and production-readiness review checkpoints

Definition of done:

- the implementation program is ready to move from planning to execution without ambiguity

---

## 3. Weekly Outcome Summary

### Week 1

- canonical abstractions frozen
- purity guardrails strengthened

### Week 2

- product-aware kernel logic generalized

### Week 3

- AppPlatform generic-vs-domain split clarified

### Week 4

- contract-system and product validation lanes prepared

### Week 5

- Finance, PHR, and Aura reference slices ready for execution governance

---

## 4. Non-Negotiable Engineering Standards

Every execution day should enforce:

- backward-compatible migrations where practical
- explicit deprecation before deletion
- tests updated with each abstraction change
- zero new duplicate abstractions
- no new product-aware branching in canonical kernel packages
- reuse of existing AppPlatform modules before invention of new ones
- code comments and docs kept in sync with behavior

---

## 5. What This Roadmap Is For

This roadmap is not a speculative rewrite plan.

It is the implementation-preparation layer for:

- day-by-day work planning
- file-by-file tasking
- production-grade sequencing
- architecture-safe execution
