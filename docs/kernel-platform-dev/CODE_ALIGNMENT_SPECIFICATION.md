# Code Alignment Specification

**Date**: 2026-03-19  
**Version**: 2.0  
**Status**: Active file alignment specification  
**Purpose**: Specify which real repo files should be kept, generalized, migrated, extracted, or retired to align implementation with the active kernel/AppPlatform architecture

---

## 1. Alignment Rules

For every file touched during implementation, choose one action:

- `KEEP`
  - already aligned to the canonical direction
- `GENERALIZE`
  - valuable but too product-shaped or too narrow
- `MIGRATE`
  - should switch to canonical abstractions
- `EXTRACT`
  - should move from generic platform/kernel space into a pack or product lane
- `RETIRE`
  - duplicate or obsolete after migration

No file should be rewritten from scratch unless reuse/adaptation is clearly worse.

---

## 2. Canonical Kernel Alignment

### 2.1 Keep as canonical

- `platform/java/kernel/src/main/java/com/ghatana/kernel/descriptor/KernelCapability.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/extension/KernelExtension.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelPlugin.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/KernelRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/KernelRegistryImpl.java`

### 2.2 Migrate away from these

- `platform/java/kernel/src/main/java/com/ghatana/kernel/capability/KernelCapability.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/KernelExtension.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/plugin/ProductPlugin.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/CapabilityRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/ServiceRegistry.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/registry/PluginRegistry.java`

### 2.3 Generalize these kernel services

- `platform/java/kernel/src/main/java/com/ghatana/kernel/audit/CrossProductAuditService.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/boundary/ProductBoundaryEnforcer.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/communication/KernelInterProductBus.java`
- `platform/java/kernel/src/main/java/com/ghatana/kernel/workflow/CrossProductWorkflowEngine.java`

### 2.4 Update kernel tests alongside the changes

- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/validation/KernelPurityValidationTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/e2e/KernelEndToEndTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/integration/KernelLifecycleIntegrationTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/integration/KernelModuleIntegrationTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/integration/PhrFinanceCrossProductIntegrationTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/integration/PhrFinanceIntegrationTest.java`
- `platform/java/kernel/src/test/java/com/ghatana/kernel/test/compliance/RegulatoryComplianceTest.java`

---

## 3. Kernel Module Alignment

These module families should be treated as the canonical reusable kernel-module lane:

- `platform/java/kernel/modules/authentication/**`
- `platform/java/kernel/modules/config/**`
- `platform/java/kernel/modules/event-store/**`
- `platform/java/kernel/modules/audit/**`
- `platform/java/kernel/modules/observability/**`
- `platform/java/kernel/modules/resilience/**`
- `platform/java/kernel/modules/secrets/**`

Alignment action:

- `MIGRATE` imports/usages to canonical abstractions
- `KEEP` module purpose
- `GENERALIZE` only where lingering product/domain assumptions exist

---

## 4. AppPlatform Reuse-First Alignment

### 4.1 Reuse-first generic operational modules

Treat these as primary reuse sources before creating new implementations:

- `products/app-platform/kernel/iam/**`
- `products/app-platform/kernel/config-engine/**`
- `products/app-platform/kernel/event-store/**`
- `products/app-platform/kernel/audit-trail/**`
- `products/app-platform/kernel/resilience-patterns/**`
- `products/app-platform/kernel/secrets-management/**`
- `products/app-platform/kernel/plugin-runtime/**`
- `products/app-platform/kernel/api-gateway/**`
- `products/app-platform/kernel/data-governance/**`
- `products/app-platform/kernel/ai-governance/**`
- `products/app-platform/kernel/platform-manifest/**`

Preferred action:

- `KEEP` where already generic and operationally strong
- `GENERALIZE` where naming or small semantics are finance-shaped
- `WRAP` from the canonical kernel/module layer where needed

### 4.2 Workflow-orchestration split

`KEEP` as generic runtime candidates:

- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/WorkflowExecutionRuntimeService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/WorkflowDefinitionService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/ParallelStepExecutionService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/CelExpressionEvaluatorService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/WorkflowErrorHandlingService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/SubWorkflowCompositionService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/WaitCorrelationStepService.java`

`EXTRACT` to finance pack/product ownership:

- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/TradeSettlementWorkflowService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/CorporateActionWorkflowService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/RegulatoryReportSubmissionWorkflowService.java`
- `products/app-platform/kernel/workflow-orchestration/src/main/java/com/ghatana/appplatform/workflow/ReconciliationOrchestrationWorkflowService.java`

### 4.3 Operator-workflows split

Likely `KEEP` or `GENERALIZE`:

- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/TenantRegistryService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/TenantConfigIsolationService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/FeatureRolloutService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/TenantResourceMonitoringService.java`

Likely `GENERALIZE` or `EXTRACT` depending on semantic depth:

- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/JurisdictionRegistryService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/CrossJurisdictionReportingService.java`
- `products/app-platform/kernel/operator-workflows/src/main/java/com/ghatana/appplatform/operator/NaturalLanguagePlatformQueryService.java`

### 4.4 Ledger-framework split

Likely `KEEP` as generic core candidates:

- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/domain/**`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/port/**`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/adapter/**`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/outbox/**`

Likely `GENERALIZE` or `EXTRACT`:

- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/service/ReconciliationService.java`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/service/ReconciliationBreakTracker.java`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/service/ExternalStatementReconciliationService.java`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/service/AccountCreationWorkflowService.java`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/service/MultiCurrencyJournalService.java`
- `products/app-platform/kernel/ledger-framework/src/main/java/com/ghatana/appplatform/ledger/dtc/**`

---

## 5. Contract and Validation Alignment

The following AppPlatform files should be mined for reusable implementation patterns when building the canonical contract/validation layer:

- schema and event validation
  - `products/app-platform/kernel/event-store/src/main/java/com/ghatana/appplatform/eventstore/validation/**`
  - `products/app-platform/kernel/event-store/src/main/java/com/ghatana/appplatform/eventstore/port/EventSchemaRegistry.java`
  - `products/app-platform/kernel/api-gateway/src/main/java/com/ghatana/appplatform/gateway/schema/**`
  - `products/app-platform/kernel/data-governance/src/main/java/com/ghatana/appplatform/governance/SchemaEvolutionService.java`
- audit/evidence
  - `products/app-platform/kernel/audit-trail/src/main/java/com/ghatana/appplatform/audit/**`
- AI/autonomy governance
  - `products/app-platform/kernel/ai-governance/src/main/java/com/ghatana/appplatform/aigovernance/**`
- manifest/certification/upgrade
  - `products/app-platform/kernel/platform-manifest/src/main/java/com/ghatana/appplatform/manifest/**`
- plugin validation
  - `products/app-platform/kernel/plugin-runtime/src/main/java/com/ghatana/appplatform/plugin/**`

Alignment rule:

- reuse implementation patterns and domain-neutral services first
- do not create a second parallel contract registry model

---

## 6. Documentation Alignment

Keep these docs as the active implementation authority:

- `docs/kernel-platform-dev/README.md`
- `docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_ADR.md`
- `docs/kernel-platform-dev/KERNEL_APP_PLATFORM_CONVERGENCE_EXPLORATION_AND_PLAN.md`
- `docs/kernel-platform-dev/KERNEL_CANONICALIZATION_DECISIONS.md`
- `docs/kernel-platform-dev/DEVELOPER_PLATFORM_CONTRACT_MODEL.md`
- `docs/kernel-platform-dev/KERNEL_TO_APP_PLATFORM_MODULE_MAPPING.md`
- `docs/kernel-platform-dev/KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md`
- `docs/kernel-platform-dev/KERNEL_NEXT_PHASE_ARCHITECTURE_PROGRAM_BOARD.md`
- `docs/kernel-platform-dev/PHR_AppPlatform_Integration_Analysis_Report.md`
- `docs/kernel-platform-dev/VISION_REALIZATION_GAP_ANALYSIS.md`
- `docs/kernel-platform-dev/IMPLEMENTATION_EXECUTION_ROADMAP.md`
- `docs/kernel-platform-dev/CODE_ALIGNMENT_SPECIFICATION.md`

---

## 7. Production-Grade Acceptance Conditions

The alignment work is production-grade only when:

- the implementation follows canonical abstractions consistently
- generic services are reused or generalized before new implementations are created
- domain-specific workflows are extracted instead of hidden behind generic names
- tests are updated at the same time as structural changes
- architecture docs and code reality stay synchronized

This specification is intended to be used with the roadmap as the file-level execution guide.
