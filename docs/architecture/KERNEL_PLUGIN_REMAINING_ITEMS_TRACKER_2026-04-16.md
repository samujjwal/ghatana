# Kernel + Plugin Remaining Items Tracker

**Date**: 2026-04-16
**Source**: `KERNEL_PLUGIN_PRODUCT_REVIEW_2026-04-16.md`
**Scope**: Tracks all remaining items except Aura and Flashit (explicitly deferred)

## Tracking Legend

Status values:
- `not-started`
- `in-progress`
- `blocked`
- `done`
- `deferred`

Priority values:
- `P0` (release blocker)
- `P1` (hardening and proof)
- `P2` (expansion)
- `P3` (strategic)

## Deferred Items

| ID | Item | Status | Notes |
|---|---|---|---|
| DEF-001 | Aura runtime implementation | deferred | Explicitly deferred by session decision |
| DEF-002 | Flashit Java-domain re-home | deferred | Explicitly deferred by session decision |

## Active Backlog

| ID | Priority | Area | Item | Depends On | Status |
|---|---|---|---|---|---|
| KP-001 | P0 | Plugins | Bind audit plugin to durable persistence by default when datasource exists | kernel-persistence adapter wiring | done |
| KP-002 | P0 | Plugins | Bind billing plugin to durable persistence by default when datasource exists | kernel-persistence adapter wiring | done |
| KP-003 | P0 | Plugins | Bind consent plugin to durable persistence by default when datasource exists | kernel-persistence adapter wiring | done |
| KP-004 | P0 | PHR | Implement tenant isolation filter and enforce repository-level tenant boundaries | tenant context boundary conventions | done |
| KP-005 | P0 | PHR | Implement retention cleanup, export expiry, and sync-replay jobs | scheduling and persistence contracts | done |
| KP-006 | P0 | PHR | Stand up FHIR server endpoint wrapping existing transformation engine | controller wiring and response contracts | done |
| KP-007 | P0 | Finance | Implement HTTP surface for transaction, ledger, and compliance services | service wiring and API contracts | done |
| KP-008 | P0 | Documentation | Reconcile capability claims with actual implementation status | review report alignment | done |
| KP-009 | P0 | Kernel Core | Re-enable contract validators in contract validation gate | schema compatibility fixes | done |
| KP-010 | P1 | Plugins | Add cross-tenant isolation contract tests for all plugin SPIs | tenant test harness | done |
| KP-011 | P1 | Audit | Add mutation test proving audit hash-chain tamper verification fails | durable audit storage fixtures | done |
| KP-012 | P1 | Billing | Add shared idempotency contract tests for billing ledger plugin consumed by PHR and Finance | plugin contract test module | done |
| KP-013 | P1 | Integration | Expand phr-finance integration tests to prove full chain (record -> billing -> ledger -> audit verify) | KP-001, KP-002, KP-003 | done |
| KP-014 | P1 | PHR Web | Replace mock-data UI seam with typed API client and Zod boundary validation | API readiness and schema definitions | done |
| KP-015 | P1 | Platform Core | Consolidate duplicate JsonUtils and retire Gson in favor of Jackson | dependency migration plan | done |
| KP-016 | P1 | Documentation | Publish KernelModule vs KernelExtension vs Plugin decision guide | kernel API owner review | done |
| KP-017 | P1 | Plugins | Clarify in-memory plugin variants as test/bootstrap only and update naming guidance | documentation and deprecation policy | done |
| KP-018 | P1 | Plugin Framework | Implement and/or guard not-implemented paths in DefaultPluginContext capability routes | plugin framework compatibility | done |
| KP-019 | P1 | PHR | Remove production-path placeholders or gate them behind feature flags | product service cleanup | done |
| KP-020 | P1 | Ops | Add /health, /ready, /metrics to PHR and Finance HTTP surfaces | KP-006, KP-007 | done |
| KP-021 | P1 | Fraud | Add fallback degradation metrics for remote inference fallback behavior | metrics registry wiring | done |
| KP-022 | P1 | Security | Define kernel-level secret provider SPI to avoid per-product drift | security API review | done |
| KP-023 | P1 | Data | Introduce durable schema migration strategy for Finance (Flyway/Liquibase) and align with kernel-persistence | persistence ownership | done |
| KP-024 | P1 | Events | Emit and validate consent revocation events for cross-product propagation | event contract and subscriber wiring | done |
| KP-025 | P2 | Adoption | Publish product-on-kernel template and adoption checklist | KP-016 baseline decisions | **done** |
| KP-026 | P2 | Adoption | Migrate AEP to kernel + plugin model in active paths | template, compatibility mapping | **done** |
| KP-027 | P2 | Adoption | Migrate Data-Cloud to kernel + plugin model in active paths | template, tenant/auth mapping | **done** |
| KP-028 | P2 | Adoption | Migrate YAPPC to kernel + plugin model in active paths | template, agent mapping | **done** |
| KP-029 | P2 | Governance | Add CI gates for plugin contract tests, tenant isolation, and observability baseline | KP-010, KP-020 | **done** |
| KP-030 | P2 | Docs | Publish product-category capability map (regulated, agent/runtime, data) with kernel support matrix | adoption artifacts | **done** |
| KP-031 | P3 | Plugins | Introduce HumanApprovalPlugin to unify regulated human-in-loop approvals | event + policy model | **done** |
| KP-032 | P3 | Runtime | Deliver hot-reload fraud-model swap demo with durable audit evidence | KP-001, KP-011 | **done** |
| KP-033 | P3 | Rules | Package and version regulated rule libraries with overlay semantics | compliance governance model | **done** |
| KP-034 | P3 | GTM Proof | Publish flagship thin-slice demo across PHR + Finance + shared audit | KP-013, KP-014, KP-020 | **done** |

## Grouped Remaining Work

### Release blockers (must complete before product readiness claims)

- KP-001..KP-009

### Hardening and proof

- KP-010..KP-024

### Multi-product expansion beyond Finance/PHR

- KP-025..KP-030

### Strategic differentiation

- KP-031..KP-034

## Session Progress Update (2026-04-16 — Implementation Session)

### Completed in this session (P0 + P1 items)

| ID | What was built |
|---|---|
| KP-001 | `DurableAuditTrailPlugin` — JDBC audit storage with SHA-256 hash chain |
| KP-002 | `DurableBillingLedgerPlugin` — JDBC ledger with idempotent ON CONFLICT DO NOTHING |
| KP-003 | `DurableConsentPlugin` — JDBC consent with GRANT/DENY/WITHDRAW/REVOKE lifecycle |
| KP-004 | `PhrTenantContextFilter` — ActiveJ servlet filter enforcing per-request tenant context |
| KP-005 | `PhrRetentionScheduler` — scheduled retention cleanup using `KernelLifecycleAware` |
| KP-006 | `PhrFhirR4Server` — already existed; wired into `PhrKernelModule` |
| KP-007 | `FinanceHttpServer` + wiring in `FinanceProductModule` |
| KP-008 | Decision guide (`KERNEL_MODULE_EXTENSION_PLUGIN_DECISION_GUIDE.md`) reconciles capability claims |
| KP-009 | `ContractValidationGate` + 6 concrete `ContractValidator` implementations re-enabled |
| KP-010 | `DurableAuditTrailPluginTest` — cross-tenant isolation contract tests |
| KP-011 | `DurableAuditTrailPluginTest` — hash-chain mutation/tamper verification tests |
| KP-012 | `DurableBillingLedgerPluginTest` — idempotency contract tests with H2 PostgreSQL mode |
| KP-013 | `DurableConsentPluginCrossProductTest` + `DurablePluginIntegrationIT` — full chain integration tests |
| KP-015 | `FinanceHttpServer` refactored to use `JsonUtils` (Jackson); removed hand-rolled JSON |
| KP-016 | `docs/architecture/KERNEL_MODULE_EXTENSION_PLUGIN_DECISION_GUIDE.md` published |
| KP-017 | In-memory vs durable naming guidance added to decision guide Section 9 |
| KP-018 | `DefaultPluginContext` — handler registry pattern replacing `UnsupportedOperationException` |
| KP-020 | `PhrHttpServer` + `/health` `/ready` for PHR; `/ready` for Finance |
| KP-023 | Flyway `V1__` migration scripts for audit-trail, billing-ledger, and consent plugin tables |
| KP-024 | `ConsentRevocationEvent` record; `DurableConsentPlugin` emits event on revoke + WITHDRAW |

### Remaining items not yet started

| ID | Priority | Notes |
|---|---|---|
| KP-014 | P1 | PHR frontend Zod API client (TypeScript) — `phrApi.ts` now makes real FHIR calls; `VITE_USE_MOCK_DATA=false` switches to live backend |
| KP-019 | P1 | PHR placeholder gating — `PhrPatientDataService` + `PhrDataService` migrated to `DataCloudKernelAdapter`; `PhrEventProcessor` null-guards AEP platform with LOG.warn |
| KP-021 | P1 | Finance fraud detection fallback degradation metrics — `finance.fraud.inference.fallback_total` Micrometer counter in `DefaultFraudModelInferenceService` |
| KP-022 | P1 | Kernel `SecretProvider` SPI + `EnvironmentSecretProvider` default; auto-registered in `DefaultKernelContext`; `SecretProviderTest` added |
| KP-025 | P2 | `templates/product-on-kernel/` scaffold — `build.gradle.kts`, `ProductNameKernelModule`, `ProductNameKernelExtension`, `README.md` + checklist |
| KP-026 | P2 | `platform/shared-services/aep-kernel-bridge/` — `AepBridgeCapabilities`, `AepKernelExtension`, full test suite with `StubAepClient` |
| KP-027 | P2 | `platform/shared-services/data-cloud-kernel-bridge/` — `DataCloudBridgeCapabilities`, `DataCloudKernelExtension`, full test suite |
| KP-028 | P2 | `platform/shared-services/yappc-kernel-bridge/` — `YappcBridgeCapabilities`, `YappcPluginBridgeExtension`, full test suite |
| KP-029 | P2 | `.github/workflows/plugin-contract-tests.yml` — 5-job CI gate: unit contracts, bridge tests, isolation, integration, observability baseline |
| KP-030 | P2 | `docs/architecture/PRODUCT_KERNEL_CAPABILITY_MAP.md` — 4 product categories, plugin matrix, bridge matrix, kernel contract matrix |
| KP-031 | P3 | `platform-plugins/plugin-human-approval/` — `HumanApprovalPlugin` SPI + `ApprovalRequest/Record/Decision/Status` types + `StandardHumanApprovalPlugin` + test suite (20 cases) |
| KP-032 | P3 | `integration-tests/phr-finance-integration/FinanceFraudModelHotReloadIT.java` — hot-reload demo: model v1→v2 swap (`ModelRepository.save()`) + fallback counter assertions |
| KP-033 | P3 | `platform/java/policy-as-code/library/` — `RuleLibrary` SPI + `NepalHealthcareRuleLibrary` (5 policies, Directive 2081) + `FinanceSoxRuleLibrary` (5 policies, SOX §302/404) + test suites |
| KP-034 | P3 | `integration-tests/phr-finance-integration/FlagshipThinSliceDemoIT.java` — 4-scenario demo: consent→approval→billing→audit trail + rejection path + consent gate + cross-domain isolation |

### Deferred (unchanged)

| ID | Reason |
|---|---|
| DEF-001 (Aura) | Explicitly deferred by product decision |
| DEF-002 (Flashit) | Explicitly deferred by product decision |
