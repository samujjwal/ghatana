# YAPPC Backlog Progress

Last updated: 2026-05-26

This file tracks the consolidated YAPPC-only backlog from the `797819d9ee4e5e0270e1e6accf7ba5674d98ba38` audit. Status is evidence-based: an item is marked complete only when code and focused verification are present.

## Completed

| ID | Status | Evidence |
| --- | --- | --- |
| YAPPC-P0-001 | Complete | `ProductUnitKernelContractRegistry` loads `kernel-product-unit-contract.json`; `ProductUnitKernelContractRegistryTest` compares imported values with Kernel TypeScript public constants. |
| YAPPC-P0-002 | Complete | `ProductUnitIntentValidationService` validates canonical intent fields, scope, product unit kind, provider, surface DTOs, and implementation statuses. |
| YAPPC-P0-003 | Complete | `ProductUnitIntentExporter` builds typed ProductUnitIntent DTO records before serializing YAML/JSON; exporter output passes validator. |
| YAPPC-P0-005 | Complete | `KernelLifecycleEventIngestService` has production-profile guard coverage for local filesystem provider construction. |
| YAPPC-P0-006 | Complete | Local filesystem Kernel lifecycle provider constructors reject production profiles and require injected truth source in production. |
| YAPPC-P0-007 | Complete | `DataCloudKernelLifecycleTruthSource` normalizes typed `kernel_lifecycle_truth` records and returns explicit degraded error records for malformed data. |
| YAPPC-P0-008 | Complete | `DataCloudPlatformRunStatusService` returns `DEGRADED_RUNTIME_TRUTH` with trace/evidence IDs on Data Cloud query failure. |
| YAPPC-P0-010 | Complete | `PhasePacketServiceImpl.queryPhaseEvidence` returns degraded system evidence and blocks unsafe advancement when evidence lookup fails. |
| YAPPC-P0-011 | Complete | `PhasePacketServiceImpl.queryGovernanceRecords` fails closed with policy-denial governance records when governance lookup fails. |
| YAPPC-P0-013 | Complete | `PhaseActionAuthorizationService` now emits stable `phaseAction.*` label/description/disabled-reason keys; phase cockpit translates those keys with `@ghatana/i18n`. |
| YAPPC-P0-014 | Complete | `PhaseGateValidator.PhaseGateContext` carries typed artifacts/evidence/governance/health/flags; `PhasePacketServiceImpl` now passes typed context and tests capture it. |

## Verified Commands

| Scope | Command | Result |
| --- | --- | --- |
| Kernel ProductUnit contract/export/validation | `./gradlew :products:yappc:core:scaffold:api:test --tests "com.ghatana.yappc.kernel.*" --tests "com.ghatana.yappc.cli.CreateCommandKernelProductUnitTest" --tests "com.ghatana.yappc.cli.CICommandKernelProductUnitTest"` | Passed |
| Kernel lifecycle truth source/guard | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.kernelvisibility.*"` | Passed |
| Phase failure semantics | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.phase.PhasePacketServiceImplTest" --tests "com.ghatana.yappc.services.phase.DataCloudPlatformRunStatusServiceTest"` | Passed |
| Phase action i18n keys | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.phase.PhaseActionAuthorizationServiceTest"`, `node -e "JSON.parse(...common.json...)"`, and `pnpm -C products/yappc/frontend/web type-check` | Passed |
| Typed phase gate context | `./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.phase.PhasePacketServiceImplTest"` | Passed |

## In Progress

| ID range | Current status |
| --- | --- |
| YAPPC-P0-004, YAPPC-P0-009, YAPPC-P0-012, YAPPC-P0-015 through YAPPC-P0-025 | Not yet complete in this pass. Next focus is server-side phase action execution authorization, artifact source/matching, tenant enforcement, and route/client parity gates. |

## Not Started

| Priority | Scope |
| --- | --- |
| P1 | Lifecycle feature completeness from Intent through Evolve. |
| P2 | UX, observability, i18n, a11y, performance, and hardening matrix. |
| P3 | Documentation, cleanup, ADRs, and maintenance polish after code evidence exists. |
| E2E | Cross-cutting Playwright/API/integration journey matrix E2E-001 through E2E-025. |
