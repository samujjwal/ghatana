# Kernel Architecture

> **Authoritative Source**: This document is the canonical reference for Ghatana Kernel architecture. All other Kernel documentation should defer to this document for architectural truth. If you find conflicting information elsewhere, update this document as the source of truth.
> **Lifecycle Contract**: [`PRODUCT_LIFECYCLE_CONTRACT.md`](PRODUCT_LIFECYCLE_CONTRACT.md)

## Current State

Ghatana lifecycle execution is centered on the shared TypeScript Kernel packages. Product code describes what can be built and operated; Kernel code owns planning, execution, truth, manifests, and provider boundaries.

- Canonical product registry: `config/canonical-product-registry.json`
- Product contract schemas: `platform/typescript/kernel-product-contracts`
- Lifecycle planner, executor, service, API handlers, and manifests: `platform/typescript/kernel-lifecycle`
- Bootstrap file-backed providers: `platform/typescript/kernel-providers`
- Toolchain adapters: `platform/typescript/kernel-toolchains`
- Digital Marketing pilot config: `products/digital-marketing/kernel-product.yaml`

Digital Marketing is the only lifecycle-enabled pilot. Other products may have shape entries, registry metadata, and readiness evidence, but they remain planned, partial, disabled, or shape-only until the product-shape matrix and lifecycle checks prove them executable.

## Lifecycle Boundary

`KernelLifecycleService` is the reusable orchestration boundary. CLI, Studio/API clients, Data Cloud Action gateway routes, and agentic lifecycle services must call the shared service or its public API handlers instead of wiring separate planner/executor runners.

```text
CLI scripts/kernel-product.mjs
Studio kernelLifecycleClient
Data Cloud Action gateway /api/kernel/*
AgentLifecycleActionService
        |
        v
KernelLifecycleService
        |
        +-- ProductLifecyclePlanner
        +-- ProductLifecycleExecutor
        +-- ManifestPointerStore
        +-- Kernel lifecycle providers
```

The service keeps lifecycle artifacts in the existing `.kernel/out/products/<product>/<phase>/<runId>` shape and writes latest pointers for bootstrap/local reads. API responses expose safe plan, result, manifest, approval, and error shapes; raw command lines, secrets, and unsafe stdout/stderr are not UI or agent contracts.

## Provider Modes

Kernel has two explicit provider modes.

- `bootstrap`: file-backed providers under `.kernel/out`. This mode can build, package, deploy, and verify the Data Cloud product itself because it does not depend on Data Cloud.
- `platform`: Data Cloud-backed providers. This mode requires events, artifacts, health, approvals, provenance, memory, and runtime truth providers and fails closed until the Data Cloud provider bridge is configured.

Bootstrap requires events, artifacts, health, approvals, provenance, and runtime truth. Memory is available through the file-backed provider for local summaries, while platform mode treats memory as required.

## Truth And Observability

Lifecycle truth is a feature contract, not best-effort decoration. Required provider writes fail closed. Runtime truth and provenance are recorded for plan creation, phase transitions, failure, approval pending, and agentic actions. Event writes are awaited when required and logged as structured warnings only when optional.

Manifests include run and correlation identifiers so Studio, CLI, API handlers, and future Data Cloud provider bridges can correlate artifacts, deployments, health, and evidence.

### Kernel Ownership

The Kernel platform owns the following cross-cutting concerns for all products:

- **Lifecycle**: Product lifecycle planning, execution, validation, and governance through `kernel-lifecycle` packages
- **Policy**: Policy evaluation, enforcement, and PHI access control through `kernel-security` and `kernel-policy` packages
- **Observability**: Audit trails, structured logging, metrics, tracing, and health monitoring through `kernel-observability` packages

Products must use Kernel-provided abstractions for these concerns and must not implement parallel lifecycle, policy, or observability systems. Product-specific policy rules and observability configurations are expressed through Kernel plugin contracts and provider interfaces.

## Product Boundaries

- Kernel packages stay product-neutral.
- Product-specific lifecycle config belongs under the owning product.
- Data Cloud internals remain under `products/data-cloud`; Kernel uses public provider interfaces or API-backed adapters.
- YAPPC exports `ProductUnitIntent` and evidence through public Kernel/Data Cloud boundaries and must not mutate Kernel registry files directly.
- Agents can request lifecycle work only through `AgentLifecycleActionService` and canonical request/result contracts.

## Validation

Focused checks:

```bash
pnpm check:kernel-lifecycle-service
pnpm check:kernel-api-contracts
pnpm check:kernel-lifecycle-truth
pnpm check:kernel-provider-mode
pnpm check:agentic-lifecycle-action-contracts
pnpm check:digital-marketing-lifecycle-pilot --smoke
pnpm check:product-shape-capability-matrix
```

Broader release checks also include TypeScript package tests, gateway tests, Studio checks, production readiness, and Gradle builds.
