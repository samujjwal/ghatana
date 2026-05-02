# Capability Matrix

> **Source**: Derived from canonical plugin module metadata.
> **Last Generated**: 2026-04-20
> **How to regenerate**: Run `./gradlew :platform-plugins:generateCapabilityMatrix` (Phase 6 task — scheduled).

## 1. Platform Plugin Modules

| Module | Canonical Name | SPI Entry Point | Pack-Driven? | Config Schema |
|--------|---------------|-----------------|:---:|:---:|
| `platform-plugins/plugin-audit-trail` | `plugin-audit-trail` | `AuditTrailPlugin` | No | Yes |
| `platform-plugins/plugin-compliance` | `plugin-compliance` | `CompliancePlugin` | Yes | Yes |
| `platform-plugins/plugin-consent` | `plugin-consent` | `ConsentPlugin` | Yes | Yes |
| `platform-plugins/plugin-fraud-detection` | `plugin-fraud-detection` | `FraudDetectionPlugin` | Yes | Yes |
| `platform-plugins/plugin-human-approval` | `plugin-human-approval` | `HumanApprovalPlugin` | Yes | Yes |
| `platform-plugins/plugin-ledger` | `plugin-ledger` | `LedgerPlugin` | No | Yes |
| `platform-plugins/plugin-risk-management` | `plugin-risk-management` | `RiskPlugin` | Yes | Yes |
| `platform-plugins/core-observability` | `core-observability` | `ObservabilityPlugin` | No | Yes |

> **Pack-Driven**: The plugin is a generic engine; products supply rule packs or model providers.

## 2. Kernel Bridge Adapters

| Adapter | Bridge ID | Authorization Port | Audit Port | Health Port |
|---------|-----------|:---:|:---:|:---:|
| `AepKernelAdapterImpl` | `aep-kernel-bridge` | `BridgeAuthorizationService` | `BridgeAuditEmitter` | `BridgeHealthIndicator` |
| `DataCloudKernelAdapterImpl` | `data-cloud-kernel-bridge` | `BridgeAuthorizationService` | `BridgeAuditEmitter` | `BridgeHealthIndicator` |

All bridge adapters extend `AbstractKernelBridge` which provides:
- Lifecycle state management (`markStarted`, `markStopped`, `requireStarted`)
- Authorization via `BridgeAuthorizationService` port
- Audit emission via `BridgeAuditEmitter` port
- Health reporting via `BridgeHealthIndicator` port
- `CompletableFuture` → ActiveJ `Promise` wrapping
- Bounded retry with exponential back-off
- Sensitive metadata redaction for logging

## 3. Product Pack Registry

| Product | BoundaryPolicyStore | ComplianceRulePack | Rule ID Prefix |
|---------|--------------------|--------------------|:---:|
| PHR | `PhrBoundaryPolicyStore` | `PhrComplianceRulePack` | `PHR-` |
| Finance | `FinanceBoundaryPolicyStore` | `FinanceComplianceRulePack` | `FIN-` |

## 4. Compliance Rule Sets per Product

### PHR

| Rule Set ID | Factory Method | Rule Count |
|-------------|---------------|:---:|
| `PHR_SUBJECT_RECORD_ACCESS` | `subjectRecordAccessRules()` | 3 |
| `PHR_CONSENT_LIFECYCLE` | `consentLifecycleRules()` | 3 |
| `PHR_AUDIT_TRACEABILITY` | `auditTraceabilityRules()` | 3 |

### Finance

| Rule Set ID | Factory Method | Rule Count |
|-------------|---------------|:---:|
| `FIN_TRANSACTION_INTEGRITY` | `transactionIntegrityRules()` | 3 |
| `FIN_AUDIT_RECORD_KEEPING` | `auditRecordKeepingRules()` | 3 |
| `FIN_TRADE_SURVEILLANCE` | `tradeSurveillanceRules()` | 3 |

## 5. Boundary Policy Rule Summary

### PHR Boundary Policies

| Rule ID | Resource | Actions | Effect | Notes |
|---------|----------|---------|--------|-------|
| `PHR-BP-001` | `subject-records/**` | `read` | ALLOW | requiresConsent + requiresAudit |
| `PHR-BP-002` | `subject-records/**` | `write`, `delete` | REQUIRE_APPROVAL | Four-eyes |
| `PHR-BP-003` | `interop/**` | `read` | ALLOW | Feature flag: `phr.interop.enabled` |
| `PHR-BP-004` | `clinical-documents/**` | `export` | DENY | Always denied |
| `PHR-BP-005` | `**` / `phr.*` | `*` | DENY | Default-deny catch-all |

### Finance Boundary Policies

| Rule ID | Resource | Actions | Effect | Notes |
|---------|----------|---------|--------|-------|
| `FIN-BP-001` | `transactions/**` | `read` | ALLOW | requiresAudit |
| `FIN-BP-002` | `transactions/**` | `write`, `settle`, `cancel` | REQUIRE_APPROVAL | Four-eyes |
| `FIN-BP-003` | `positions/**` | `export` | DENY | Always denied |
| `FIN-BP-004` | `market-data/**` | `read` | ALLOW | Feature flag: `finance.market-data.interop.enabled` |
| `FIN-BP-005` | `**` / `finance.*` | `*` | DENY | Default-deny catch-all |

## 6. CI Gate Checklist

All the following gates must pass on every PR touching platform or product code:

| Gate | Gradle Task | Scope |
|------|------------|-------|
| Kernel purity | `checkKernelPurity` | `platform-kernel/kernel-core/src/main` |
| Resource purity | `checkKernelResourcePurity` | `platform-kernel/kernel-core/src/main/resources` |
| Docs purity | `checkKernelDocsPurity` | `docs/examples/**` |
| Plugin purity | `checkPluginPurity` | `platform-plugins/*/src/main` |
| Domain pack validation | `validateDomainPackManifest` | Product build |
| Policy pack validation | `validatePolicyPack` | Product build |
| Compliance rule validation | `validateComplianceRulePack` | Product build |
| Architecture tests | `test` (ArchUnit) | `kernel-core` + plugin modules |
| Contract tests | `test` (`*PackContractTest`) | Product modules |

---

*See also: [KERNEL_PURITY_RULES.md](KERNEL_PURITY_RULES.md), [PLUGIN_PURITY_RULES.md](PLUGIN_PURITY_RULES.md), [PRODUCT_DEVELOPMENT_GUIDE.md](PRODUCT_DEVELOPMENT_GUIDE.md)*
