# Finance Reference Slice: Post-Trade Settlement

> **Status**: Day 18 Planning Pass — COMPLETE  
> **Date**: 2026-01-19  
> **Reference Slice**: Trade Settlement (Post-Trade Domain)

## 1. Slice Selection Rationale

The Post-Trade Settlement domain is the ideal finance reference slice because:

1. **Clear kernel/domain separation** — depends on 8 generic kernel services + 6 domain-specific services
2. **End-to-end flow** — order → trade → settlement → reconciliation → custody
3. **All 4 contract families** — API, Schema, Analytics, Packaging
4. **Multi-jurisdiction** — exercises JurisdictionRegistry (T+1 India, T+2 US)
5. **Production complexity** — matching, clearing, netting, custody, exception handling

## 2. Generic Kernel Services Required

| Service | Kernel Module | Capability | Day Classified |
|:--------|:-------------|:-----------|:--------------|
| WorkflowExecutionRuntimeService | workflow-orchestration | `compute.workflow` | Day 12 |
| WorkflowDefinitionService | workflow-orchestration | `compute.workflow` | Day 12 |
| LedgerService (21 VOs + 7 services) | ledger-framework → `libs:ledger-core` | `data.storage` | Day 14 |
| BalanceEnforcer | ledger-framework → `libs:ledger-core` | `data.storage` | Day 14 |
| EventStore + EventConsumerBase | event-store | `data.event-streaming` | Day 11 |
| AuditTrailStore + HashChainService | audit-trail | `audit.immutable-trail` | Day 11 |
| TenantResourceMonitoringService | operator-workflows | `compute.scheduling` | Day 13 |
| CelExpressionEvaluatorService | workflow-orchestration | `compute.workflow` | Day 12 |

## 3. Domain-Specific Services (Pack-Owned)

| Service | Responsibility | Pattern |
|:--------|:--------------|:--------|
| TradeConfirmationMatchingService | Match buy/sell confirmations, detect breaks | State machine + business rules |
| SettlementCycleEnforcerService | Enforce T+N cycles per instrument + jurisdiction | Rules engine + calendar lookup |
| ClearingNetService | Compute net obligations per counterparty | Reconciliation + netting algorithm |
| CustodyManagementService | Safekeeping, corporate actions, lending | Custody state machine |
| FailedTradeRecoveryService | Exception handling for rejected/failed settlements | Manual exception + auto-retry |
| SettlementRiskMonitorService | Monitor counterparty credit exposure | Real-time risk aggregation |

## 4. Finance-Specific Operator Workflows (from Day 13)

| Operator Service | Role in Post-Trade | Consumed By |
|:----------------|:-------------------|:-----------|
| JurisdictionRegistryService | Settlement cycle lookup per jurisdiction | SettlementCycleEnforcerService |
| CrossJurisdictionReportingService | Settlement volume, AML risk reporting | Compliance reporting |
| UsageMeteringService | Settlement throughput billing | Operator billing |

## 5. Contract Surface Declarations

### 5.1 API Contracts (ContractFamily.API)

| Method | Path | Purpose |
|:-------|:-----|:--------|
| POST | `/settlements/{tradeId}/confirm` | Confirm trade settlement |
| GET | `/settlements/{settlementId}` | Query settlement status |
| POST | `/settlements/{settlementId}/exceptions` | Report settlement break |
| GET | `/reconciliation/breaks` | List unmatched trades |

### 5.2 Schema Contracts (ContractFamily.SCHEMA)

| Event | Compatibility | Format |
|:------|:-------------|:-------|
| SettlementInitiatedEvent | BACKWARD | JSON Schema v7 |
| SettlementConfirmedEvent | BACKWARD | JSON Schema v7 |
| SettlementFailedEvent | BACKWARD | JSON Schema v7 |
| SettlementLedgerEntry | FULL | JSON Schema v7 |

### 5.3 Analytics Contracts (ContractFamily.ANALYTICS)

| Metric | Type | Tags |
|:-------|:-----|:-----|
| `finance.settlement.confirmed_count` | COUNTER | `jurisdiction`, `instrument_type` |
| `finance.settlement.latency_ms` | TIMER | `jurisdiction`, `settlement_type` |
| `finance.settlement.pending_volume` | GAUGE | `jurisdiction` |
| `finance.settlement.failure_rate` | GAUGE | `jurisdiction`, `failure_reason` |

### 5.4 Packaging Contract (ContractFamily.PACKAGING)

- **Pack ID**: `finance.post-trade`
- **Tier**: T1_CORE
- **Dependencies**: `kernel.ledger-core`, `kernel.workflow`, `kernel.event-store`, `kernel.audit-trail`
- **Lifecycle hooks**: INSTALL (schema migration), UPGRADE (version gate), HEALTH_CHECK (settlement queue depth)

## 6. Validation Path

```
1. Generic services exist → checked via CANONICAL_CAPABILITY_MAPPING.md Days 11-14
2. Contract families declared → checked via ContractRegistry + ContractValidator
3. Domain pack isolation → checked via product-isolation.gradle
4. Schema compatibility → checked via SchemaGovernanceValidator
5. Autonomy governance → N/A for this slice (no agents)
```

## 7. Key File Paths

- `products/finance/domains/post-trade/` — Post-Trade domain module
- `products/finance/ledger-framework/` — Finance ledger (wraps generic core)
- `products/app-platform/kernel/workflow-orchestration/` — Generic workflow runtime
- `products/app-platform/kernel/operator-workflows/` — Jurisdiction + metering
- `platform/java/kernel/src/main/java/com/ghatana/kernel/contracts/` — Canonical contracts
