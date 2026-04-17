# Kernel + Plugin Multi-Product Execution Plan

**Date**: 2026-04-16
**Inputs**:
- `KERNEL_PLUGIN_PRODUCT_REVIEW_2026-04-16.md`
- `platform-kernel/MIGRATION_STATUS.md`
- `products/phr/docs/01_governance/phr_qa_delivery_plan.md`

## 1. Scope Decision

This plan intentionally **defers Aura and Flashit** to future work.

This plan focuses on:
- hardening the kernel + shared plugin substrate,
- delivering product-agnostic extension patterns,
- completing cross-product adoption in active products,
- proving one end-to-end regulated thin slice,
- enabling future products to adopt the same kernel contracts without product-specific rewrites.

Out of scope for this plan:
- Aura runtime implementation,
- Flashit Java-domain re-homing.

## 2. Goal

Design and deliver the kernel and related features so they support multiple product categories, not only Finance and PHR.

Target product classes:
- regulated products (PHR, Finance),
- agent/runtime products (AEP, YAPPC),
- data platform products (Data-Cloud),
- operational products with strict tenancy/audit requirements.

## 3. Product-Agnostic Kernel Design Blueprint

### 3.1 Stable extension model (required)

Provide one explicit decision model for extension points:
- `KernelModule`: owns lifecycle, service graph, and product runtime wiring.
- `KernelExtension`: product-local adaptation around existing kernel/plugin capabilities.
- `Plugin`: reusable cross-product capability with a stable SPI.

Required artifact:
- one-page decision doc with examples for each extension path.

### 3.2 Durable-by-default plugin architecture (required)

Current standard plugin implementations are in-memory reference implementations. To support diverse products, default behavior must be durable when persistence is available.

Design rules:
- keep in-memory plugin implementations for tests/bootstrap only,
- provide repository-backed implementations for production paths,
- automatically bind durable plugin implementations when datasource is configured,
- keep SPI unchanged so existing products can migrate without API churn.

### 3.3 Tenant safety baseline (required)

All product classes require tenant isolation.

Design rules:
- enforce `TenantContext` at HTTP boundary,
- enforce tenant partitioning in repositories and plugin data access,
- add reusable tenant contract tests that each product must pass.

### 3.4 Contract integrity baseline (required)

Kernel contract validation must be enabled by default.

Design rules:
- remove silent disables and TODO-based bypasses,
- fail fast on incompatible schema/metadata contracts,
- emit observable signals for contract validation failures.

### 3.5 Evented cross-product integration baseline (required)

Kernel event patterns must support product-to-product workflows.

Design rules:
- publish canonical event envelopes with correlation and tenant context,
- define idempotency requirements for cross-product subscribers,
- ship reference events for consent, billing, and compliance evidence.

### 3.6 Observability baseline (required)

Kernel and plugin operations must be diagnosable in all product classes.

Design rules:
- structured logs with correlation IDs,
- plugin degradation metrics (fallback, timeout, policy bypass),
- product-level `/health`, `/ready`, `/metrics` endpoints on new HTTP surfaces,
- test assertions for critical telemetry emission paths.

### 3.7 API boundary safety baseline (required)

TypeScript surfaces must validate all external boundaries.

Design rules:
- use Zod validation at API boundaries,
- separate DTO/domain/persistence models,
- explicit typed API clients for frontend-to-backend seams.

## 4. Multi-Product Adoption Design

### 4.1 Adoption waves

- **Wave 1 (proof and hardening)**: kernel core + plugins + PHR + Finance
- **Wave 2 (platform expansion)**: AEP + Data-Cloud + YAPPC
- **Wave 3 (new products)**: future products onboard from the template and contract tests

### 4.2 Mandatory adoption artifacts for every product

- kernel module declaration and runtime wiring,
- plugin manifest and capability declaration,
- tenant isolation filter and repository tests,
- contract validation gate enabled,
- health/readiness/metrics endpoints,
- product runbook and migration notes,
- plugin contract test suite execution in CI.

### 4.3 Product-on-kernel template requirements

Template should include:
- minimal `KernelModule` scaffold,
- default plugin wiring with durable bindings,
- event publisher/subscriber examples,
- tenant filter and auth boundary patterns,
- baseline integration tests and contract test setup,
- deployment skeleton with observability endpoints enabled.

## 5. Phase Plan

### Phase 0 - Critical correctness and credibility

- durable default paths for audit, billing, consent plugins,
- PHR tenant isolation,
- PHR retention/export/sync jobs,
- PHR FHIR endpoint,
- Finance HTTP surface,
- reconcile capability claims with implementation reality,
- re-enable kernel contract validators.

### Phase 1 - Hardening and cross-product proof

- plugin contract tests (idempotency, tenant isolation, tamper verification),
- expand cross-product integration tests for full billing/audit chain,
- wire PHR frontend to typed API client,
- JSON stack consolidation and duplicate utility cleanup,
- publish extension model decision doc.

### Phase 2 - Cross-product expansion

- migrate AEP, Data-Cloud, and YAPPC to kernel/plugin adoption paths,
- introduce product-on-kernel template and adoption checklist,
- add durability and event conformance gates to CI.

## 6. Completion Criteria

The kernel+plugin model is considered proven when:
- standard production plugin paths are durable by default,
- at least one end-to-end regulated thin slice is demonstrable,
- at least three additional non-PHR/non-Finance product lines adopt kernel contracts,
- onboarding docs and template reduce new product bootstrap time,
- CI enforces plugin contract, tenant, and observability baselines.

## 7. Session Notes (2026-04-16)

- Aura and Flashit explicitly deferred.
- Multi-product kernel design baseline documented.
- Remaining-item tracker moved to dedicated artifact:
  - `docs/architecture/KERNEL_PLUGIN_REMAINING_ITEMS_TRACKER_2026-04-16.md`
