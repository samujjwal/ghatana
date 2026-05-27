# YAPPC Ownership Boundaries

This document defines ownership boundaries between YAPPC, Kernel, Data Cloud, AEP, and shared platform libraries. Use it when adding routes, services, evidence, UI, or integration code.

| Area | Owns | Does Not Own | Primary Evidence/Contracts |
| --- | --- | --- | --- |
| YAPPC | Creator lifecycle UX, intent/shape/generate/run/observe/learn/evolve orchestration, phase packets, ProductUnitIntent generation, YAPPC admin/product-family surfaces, YAPPC-specific evidence and recommendations. | Kernel lifecycle execution, Kernel registry mutation, Data Cloud storage engines, AEP execution runtime internals, cross-product platform package ownership. | `docs/YAPPC_BACKLOG_PROGRESS.md`, `docs/api/route-manifest.yaml`, `core/yappc-services`, `frontend/web`. |
| Kernel | Product lifecycle execution truth, ProductUnit public contracts, gate execution, registry governance, Kernel lifecycle/artifact/deployment truth. | YAPPC creator UX, YAPPC phase cockpit actions, Data Cloud persistence implementation, AEP execution orchestration. | `platform/typescript/kernel-product-contracts`, ProductUnitIntent schema, Kernel public APIs/events. |
| Data Cloud | Durable tenant-scoped records for YAPPC repositories, `kernel_lifecycle_truth`, `yappc_platform_runs`, evidence/audit/read-model storage, tenant isolation behavior. | YAPPC UI decisions, Kernel gate semantics, AEP runtime action semantics. | `infrastructure/datacloud`, Data Cloud repository tests, `DataCloudKernelLifecycleTruthSource`. |
| AEP | Agent/execution pipeline events, execution outcomes, run telemetry, workflow orchestration signals consumed by YAPPC. | YAPPC phase authorization, Kernel ProductUnit lifecycle truth, Data Cloud persistence ownership. | AEP event contracts, platform run writer/reader tests, learning evidence services. |
| Shared Platform Java | Product-agnostic Java foundations such as HTTP, database, observability, security, testing, and workflow support. | YAPPC-specific route handlers, ProductUnit business rules, product-specific adapters. | `platform/java/*`, `.github/copilot-instructions.md` platform boundary rules. |
| Shared Platform TypeScript | Product-agnostic UI/design/canvas/i18n/API/theme/tokens/state/forms packages. | YAPPC-only phase cockpit components, YAPPC product-local adapters, one-product libraries. | `platform/typescript/*`, `@ghatana/*` canonical package registry. |
| YAPPC Frontend Libraries | Product-local reusable frontend code under `frontend/libs/*` with more than one YAPPC consumer. | Platform-level packages, app-only route/page code, backend domain logic. | `frontend/libs/README.md`, `frontend/README.md`. |

## Boundary Rules

- YAPPC must generate ProductUnitIntent and call Kernel public contracts; it must not mutate Kernel registry files directly.
- Production Kernel lifecycle truth must come from Data Cloud/Event Cloud truth sources, not local filesystem fixtures.
- Data Cloud repositories must fail closed on missing/default tenant context.
- AEP/Kernel execution events may update YAPPC run/evidence read models, but phase advancement still goes through YAPPC backend authorization and governance checks.
- Cross-product reusable frontend work starts in `platform/typescript/*`; YAPPC-only reusable work stays in `products/yappc/frontend/libs/*`.
- Cross-product reusable Java work starts in `platform/java/*`; YAPPC-specific orchestration stays under `products/yappc/core/*`.

## Validation Links

| Boundary | Validation |
| --- | --- |
| Kernel public contract import/export | `ProductUnitKernelContractRegistryTest`, `ProductUnitIntentExporterTest` |
| Production truth-source guard | `check-production-truth-sources.mjs`, `YappcEnvironmentConfigTest`, `KernelLifecycleEventIngestServiceTest` |
| Data Cloud tenant enforcement | `YappcDataCloudRepositoryTenantEnforcementTest`, `TenantIsolationTest` |
| Route/auth/OpenAPI parity | `RouteManifestParityTest`, `RouteAuthorizationRegistryParityTest`, `generate-api-reference.py --check` |
| Frontend package boundaries | `frontend/README.md`, `frontend/libs/README.md`, `inventory:components:check` |