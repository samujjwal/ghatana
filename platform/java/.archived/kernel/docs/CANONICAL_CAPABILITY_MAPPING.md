<!--
  Canonical Capability-to-Module Mapping
  Created: Day 11, Kernel/AppPlatform Convergence Program
  Status: Active classification — to be maintained as authoritative source
-->

# Canonical Capability-to-Module Mapping

Per KERNEL_CONVERGENCE_REFACTOR_BACKLOG.md item C1: "AppPlatform modules are not explicitly
mapped to canonical kernel capabilities. Create and maintain capability-to-module mapping."

## Legend

| Classification | Meaning |
|:---------------|:--------|
| **GENERIC-CORE** | Domain-agnostic, reusable across all products. Candidate for lift into `platform/java/kernel/`. |
| **OPERATIONAL-IMPL** | Concrete operational implementation. Stays in AppPlatform but implements canonical kernel capabilities. |
| **DOMAIN-SPECIFIC** | Contains domain/product-specific logic. Must be owned by product or domain pack. |

---

## Kernel Module Mapping

### platform/java/kernel (Canonical Contracts)

| Capability ID | Kernel Type | Notes |
|:-------------|:-----------|:------|
| `data.storage` | Core constant | Generic data storage abstraction |
| `data.query` | Core constant | Generic query abstraction |
| `user.authentication` | Core constant | Authentication contract |
| `user.authorization` | Core constant | Authorization contract |
| `api.gateway` | Core constant | Gateway abstractions |
| `api.rate-limiting` | Core constant | Rate limiting contract |
| `workflow.execution` | Core constant | Workflow execution contract |
| `workflow.scheduling` | Core constant | Scheduling contract |
| `event.processing` | Core constant | Event processing contract |
| `event.streaming` | Core constant | Event streaming contract |
| `ai.inference` | Core constant | AI inference contract |
| `ai.training` | Core constant | AI training contract |
| `observability.metrics` | Core constant | Metrics contract |
| `observability.tracing` | Core constant | Tracing contract |
| `observability.logging` | Core constant | Logging contract |
| `security.encryption` | Core constant | Encryption contract |
| `security.key-management` | Core constant | Key management contract |
| `config.management` | Core constant | Config resolution contract |
| `config.feature-flags` | Core constant | Feature flag contract |
| `tenant.isolation` | Core constant | Tenant isolation contract |
| `tenant.provisioning` | Core constant | Tenant provisioning contract |
| `resilience.circuit-breaker` | Core constant | Circuit breaker contract |
| `resilience.retry` | Core constant | Retry contract |
| `resilience.bulkhead` | Core constant | Bulkhead contract |
| `mfa.authentication` | Core constant | MFA contract |
| `oauth.authorization` | Core constant | OAuth contract |

---

### AppPlatform Kernel Module Classification

#### 1. IAM (`products/app-platform/kernel/iam/`)

| Classification | **GENERIC-CORE** |
|:--------------|:-----------------|
| Capabilities | `user.authentication`, `user.authorization`, `tenant.isolation` |
| Key Services | `JwtTokenService`, `AuthorizationService`, `RedisSessionStore`, `TenantSessionValidator` |
| Reuse Status | **Lift candidate** — JWT issuance, RBAC, and session management are domain-agnostic |
| Action | Extract interfaces to `platform/java/kernel/` as SPI; keep Vault/Redis impls in AppPlatform |

#### 2. Config-Engine (`products/app-platform/kernel/config-engine/`)

| Classification | **GENERIC-CORE** |
|:--------------|:-----------------|
| Capabilities | `config.management` (enhanced with governance/approval) |
| Key Services | `ConfigChangeApprovalService`, `ConfigCanaryRouter`, `ConfigBundleSigner` |
| Reuse Status | **Lift candidate** — maker-checker config approval and canary routing are generic |
| Action | Extract approval/canary contracts; `HierarchicalKernelConfigResolver` already in kernel |

#### 3. Event-Store (`products/app-platform/kernel/event-store/`)

| Classification | **GENERIC-CORE** |
|:--------------|:-----------------|
| Capabilities | `event.processing`, `event.streaming`, `data.storage` |
| Key Services | `AggregateEventStore`, `ValidatingAggregateEventStore`, `SchemaCompatibilityChecker`, `EventConsumerBase` |
| Reuse Status | **Lift candidate** — aggregate event store with schema validation is domain-agnostic |
| Action | Extract port interfaces to kernel; validation/consumer patterns are reusable |

#### 4. Audit-Trail (`products/app-platform/kernel/audit-trail/`)

| Classification | **GENERIC-CORE** |
|:--------------|:-----------------|
| Capabilities | **NEW: `audit.immutable-trail`** (beyond `observability.logging`) |
| Key Services | `AuditTrailStore`, `HashChainService`, `MerkleAnchorService`, `AuditRetentionService` |
| Reuse Status | **Lift candidate** — cryptographic hash chain, Merkle anchoring, and retention are domain-agnostic |
| Action | Define new `audit.immutable-trail` capability in `KernelCapability.Core`; extract store port |

#### 5. Resilience-Patterns (`products/app-platform/kernel/resilience-patterns/`)

| Classification | **GENERIC-CORE** |
|:--------------|:-----------------|
| Capabilities | `resilience.circuit-breaker`, `resilience.retry`, `resilience.bulkhead` |
| Key Services | `KernelResilienceFactory`, `DependencyHealthAggregator`, `CompositeResilienceProfile`, `TimeoutBudgetPropagator` |
| Reuse Status | **Lift candidate** — pre-composed resilience profiles and timeout budget propagation are generic |
| Action | Extract composite profile/factory contracts; health aggregator complements kernel `HealthStatus` |

#### 6. Secrets-Management (`products/app-platform/kernel/secrets-management/`)

| Classification | **GENERIC-CORE** |
|:--------------|:-----------------|
| Capabilities | `security.key-management` |
| Key Services | `SecretProvider`, `VaultSecretProvider`, `LocalFileSecretProvider`, `SecretRotationScheduler`, `CertificateLifecycleService` |
| Reuse Status | **Lift candidate** — secret provider SPI, rotation, and certificate lifecycle are generic |
| Action | Extract `SecretProvider` port to kernel; keep Vault/HSM impls in AppPlatform |

---

### Remaining AppPlatform Modules (Pending Days 12-14 Classification)

| Module | Primary Capability | Preliminary Classification |
|:-------|:------------------|:--------------------------|
| `workflow-orchestration/` | `workflow.execution` | **MIXED** — see Day 12 breakdown below |
| `operator-workflows/` | `tenant.provisioning` | **MIXED** — see Day 13 breakdown below |
| `ledger-framework/` | `data.storage` | **MIXED** — see Day 14 breakdown below |
| `api-gateway/` | `api.gateway`, `api.rate-limiting` | GENERIC-CORE |
| `ai-governance/` | `ai.inference` | GENERIC-CORE + domain policy |
| `data-governance/` | `data.storage` | GENERIC-CORE |
| `observability-sdk/` | `observability.*` | GENERIC-CORE |
| `platform-manifest/` | NEW: `platform.manifest` | GENERIC-CORE |
| `plugin-runtime/` | related to KernelPlugin | GENERIC-CORE |
| `rules-engine/` | related to `workflow.*` | GENERIC-CORE |
| `pack-certification/` | NEW: `platform.certification` | GENERIC-CORE |
| `calendar-service/` | NEW: `scheduling.calendar` | OPERATIONAL-IMPL |
| `client-onboarding/` | `tenant.provisioning` | DOMAIN-SPECIFIC (finance) |
| `incident-management/` | NEW: `operations.incident` | OPERATIONAL-IMPL |
| `dlq-management/` | `event.processing` | OPERATIONAL-IMPL |
| `deployment-abstraction/` | NEW: `platform.deployment` | GENERIC-CORE |
| `regulator-portal/` | compliance | DOMAIN-SPECIFIC (finance/healthcare) |
| `integration-testing/` | test infrastructure | TEST |

---

## New Capability Definitions Required

| Proposed ID | Module Source | Justification |
|:-----------|:-------------|:-------------|
| `audit.immutable-trail` | Audit-Trail | Cryptographic hash chain + Merkle anchoring goes beyond logging |

---

## Summary

- **6/6 Day 11 modules classified as GENERIC-CORE** — all are reuse candidates
- **1 new capability needed**: `audit.immutable-trail`
- **Immediate actions**: Extract port/SPI interfaces for IAM, event-store, audit-trail, secrets-management to kernel contracts
- **Deferred**: Remaining modules (Days 13-14) need operator/ledger classification passes

---

## Day 12: Workflow-Orchestration Extraction Classification

### GENERIC-CORE (Keep in kernel/platform)

| Service | Purpose |
|:--------|:--------|
| `WorkflowExecutionRuntimeService` | Stateful workflow engine (PENDING→RUNNING→COMPLETED lifecycle) |
| `WorkflowDefinitionService` | DSL storage, versioning, and templating for workflow definitions |
| `ParallelStepExecutionService` | Concurrent branch orchestration (ALL/FIRST/N_OF_M join strategies) |
| `CelExpressionEvaluatorService` | CEL expression evaluation for DECISION steps and triggers |

### DOMAIN-SPECIFIC (Move to product/domain packs)

| Service | Domain | Target |
|:--------|:-------|:-------|
| `TradeSettlementWorkflowService` | Finance/Securities | `products/finance/` |
| `CorporateActionWorkflowService` | Finance/Securities | `products/finance/` |
| `RegulatoryReportSubmissionWorkflowService` | Regulatory/Compliance | `products/compliance/` or cross-domain compliance pack |

---

## Day 13: Operator-Workflows Extraction Classification

### GENERIC-CORE (Keep in kernel/platform — 8 services)

| Service | Purpose |
|:--------|:--------|
| `TenantRegistryService` | Multi-firm tenant lifecycle (ONBOARDING→ACTIVE→SUSPENDED→OFFBOARDED) |
| `TenantConfigIsolationService` | Per-tenant config namespace isolation with bounds enforcement |
| `TenantResourceMonitoringService` | Per-tenant resource quotas (API RPS, throughput, storage) |
| `MaintenanceWindowManagementService` | Platform maintenance window scheduling and health probes |
| `CapacityPlanningPredictiveAlertService` | ML-powered capacity forecasting (CPU, memory, Kafka lag) |
| `NaturalLanguagePlatformQueryService` | NL→PromQL/ES query translation for operator observability |
| `FeatureRolloutService` | Progressive feature rollout (percentage, tenant-list, A/B) |
| `TenantTrialProvisioningService` | Trial tenant lifecycle (provision→convert/offboard) |

### DOMAIN-SPECIFIC (Move to domain packs — 4 services)

| Service | Domain | Target |
|:--------|:-------|:-------|
| `JurisdictionRegistryService` | Finance (settlement cycles, trade confirms) | `products/finance/` |
| `CrossJurisdictionReportingService` | Finance (settlement volume, AML risk) | `products/finance/` |
| `UsageMeteringService` | Finance (settlement volume, reconciliations billing) | `products/finance/` |
| `LicenseFeatureGateService` | Finance (algo trading, sanctions, risk features) | `products/finance/` |

---

## Day 14: Ledger-Framework Classification

### GENERIC-CORE — Immutable double-entry primitives (21 files → candidate for `libs:ledger-core`)

**Domain Value Objects:**
| Type | Purpose |
|:-----|:--------|
| `Account` | Chart-of-accounts entry in hierarchy tree |
| `AccountType` | ASSET/LIABILITY/EQUITY/REVENUE/EXPENSE classification |
| `AssetBalance` | Net asset position per (account, assetClass, instrumentId) |
| `AssetClass` | Multi-asset classification (CASH, SECURITY, UNIT) |
| `AssetEntry` | Multi-asset journal entry line |
| `BalanceSnapshot` | Immutable point-in-time balance snapshot |
| `Currency` | ISO 4217 currency with decimal precision |
| `Direction` | DEBIT/CREDIT double-entry direction |
| `Journal` | Immutable balanced journal (≥2 entries) |
| `JournalEntry` | Single debit/credit line with hash chain link |
| `MonetaryAmount` | BigDecimal + Currency value object |
| `RoundingAllocator` | Largest-remainder monetary distribution |

**Core Services:**
| Service | Purpose |
|:--------|:--------|
| `LedgerService` | Primary journal posting entry point |
| `BalanceEnforcer` | Double-entry balance validation (no I/O) |
| `BalanceSnapshotService` | Point-in-time snapshot creation/retrieval |
| `EntryHashChain` | SHA-256 hash chain for tamper detection |
| `JournalReversalService` | Contra-entry reversal of posted journals |
| `MultiCurrencyJournalService` | Multi-currency posting with FX support |
| `AssetAccountService` | Multi-asset posting and balance queries |

**Distributed Middleware:**
| Type | Purpose |
|:-----|:--------|
| `VersionVector` | Vector clock for causal ordering (Mattern 1989) |
| `CausalOrderingMiddleware` | Causal event ordering with buffer timeout |
| `ConflictDetector` | Write conflict detection via concurrent version vectors |

### DOMAIN-SPECIFIC — Finance/DTC settlement (14 files → stays in AppPlatform)

**Reconciliation:**
`ReconciliationService`, `ReconciliationBreakTracker`, `ExternalStatementReconciliationService`

**Reporting:**
`BalanceHistoryReport` (dual-calendar BS/Gregorian)

**Workflows:**
`AccountCreationWorkflowService` (maker-checker for chart-of-accounts)

**DTC Settlement:**
`DtcSagaPolicies`, `DtcSagaCoordinator`, `DtcSagaMonitor`, `DtcCompensationRegistry`,
`DtcIdempotencyConfig`, `DtcIdempotencyMiddleware`

**Infrastructure:**
`PostgresAccountStore`, `PostgresBalanceSnapshotStore`, `PostgresCurrencyRegistry`, `PostgresLedgerStore`
