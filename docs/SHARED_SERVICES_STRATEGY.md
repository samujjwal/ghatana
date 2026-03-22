# Shared-Services Strategy

**Status:** Active guidance  
**Last Updated:** 2026-03-22  
**Related:** ADR-013 Shared-Services Ownership and Fate

## Purpose

This document defines when code belongs in `shared-services/`, when it belongs in platform libraries, and when it must remain product-owned.

The goal is to stop `shared-services/` from becoming a dumping ground for unowned abstractions while preserving it for the small set of capabilities that are genuinely cross-product and runtime-oriented.

## Definitions

### shared-services

`shared-services/` is for deployable, cross-product runtime services.

A shared service is appropriate only when all of the following are true:

1. It runs as a service or gateway, not just as a library.
2. Multiple products consume it in production.
3. No single product can reasonably own the runtime and lifecycle by itself.
4. The capability has an identifiable service owner.

Examples:

- Authentication gateway
- Central user-profile service
- Multi-product inference proxy, if it is a real routed service and not just a client wrapper

### platform libraries

`platform/java`, `libs/java`, and similar library locations are for reusable code that is embedded in another process.

Use platform or libs when the capability is:

1. A library, SDK, adapter, SPI, value object set, or utility
2. Intended to be linked into product runtimes
3. Governed by stable contracts instead of service endpoints
4. Better reused by dependency than by network hop

Examples:

- Auth interfaces and security primitives
- AI integration clients and adapters
- HTTP middleware, observability, retry helpers, contracts

### product-owned code

`products/<product>` is the default home for business logic, product-specific runtime code, and domain workflows.

Code belongs in a product when any of the following are true:

1. The capability exists primarily for one product
2. The data model is product-specific
3. Other products are consumers at most indirectly
4. The owning roadmap and operations are held by a product team

Examples:

- Data-cloud feature store ingestion
- Product-specific lifecycle orchestration
- Product-specific security policies or authorization rules

## Decision Matrix

| Question | Put it in shared-services | Put it in platform/libs | Put it in products |
|:---|:---:|:---:|:---:|
| Is it a deployable runtime service? | Yes | No | Sometimes |
| Is it consumed by multiple products over the network? | Yes | Sometimes | Rarely |
| Is it code reused in-process by many modules? | No | Yes | No |
| Is it product-specific business logic? | No | No | Yes |
| Does one product clearly own the lifecycle? | Usually no | Not applicable | Yes |
| Would a network hop be unnecessary overhead? | No | Yes | Usually yes |

Rule of thumb:

- If it is reused by import, prefer platform or libs.
- If it is reused by HTTP, messaging, or gateway semantics across products, shared-services may be appropriate.
- If it is owned by one product, keep it in that product even if other teams think it is "shared".

## Current Shared-Services Posture

Based on ADR-013, the current directory contains a mix of real shared-service candidates and thin stubs.

### Keep and develop

- `shared-services/auth-gateway` (now also absorbs `auth-service`)
- `shared-services/user-profile-service`

### Consolidated or deleted (completed 2026-03-22)

- `shared-services/auth-service` -> consolidated into `shared-services/auth-gateway`
- `shared-services/ai-registry` -> consolidated into `platform/java/ai-integration`

### Needs explicit near-term decision

- `shared-services/ai-inference-service` -> stabilise as a genuine cross-product service or remove

### Moved to product ownership (completed 2026-03-22)

- `shared-services/feature-store-ingest` -> moved to `products/data-cloud/feature-store-ingest`

## Ownership Model

Every shared service must have an explicit owner.

Required ownership artifacts:

1. `OWNER.md` in the service root
2. Named team, escalation path, and operational contact
3. Stated SLA or at least service criticality
4. Release and rollback procedure

Ownership rules:

1. Shared services are not ownerless platform leftovers.
2. If no team accepts ownership, the service should not remain in `shared-services/`.
3. Thin stubs without runtime commitments should be deleted or moved into platform/product code.

## Versioning Policy

Shared services should version their network contracts, not just their code.

Rules:

1. Public API changes require explicit versioning strategy.
2. Contracts should live in `contracts/` when shared across multiple consumers.
3. Backward-incompatible changes require migration notes and consumer coordination.
4. Internal implementation refactors do not justify a new public version unless the contract changes.

For platform libraries:

1. Compatibility should be managed through stable APIs and SPI contracts.
2. Avoid product imports from platform implementation internals.
3. Prefer additive contract evolution over silent behavior changes.

## Admission Criteria For New Shared Services

Do not create a new directory under `shared-services/` unless all checks pass.

Checklist:

1. At least two products need the runtime service in production.
2. The capability cannot be solved more cleanly as a platform library.
3. A named owner exists before the service is added.
4. The network boundary is justified by scaling, isolation, or operational independence.
5. The service has a clear contract and deployment model.

If any of these fail, do not create the service.

## Exit Criteria

A shared service should be moved or removed when:

1. It becomes product-specific.
2. It is only a thin wrapper over a platform library.
3. It has no active owner.
4. It remains a scaffold or stub without operational reality.
5. Its responsibilities can be consolidated into an existing shared service.

## Recommended Governance

1. Review `shared-services/` quarterly as part of the boundary audit.
2. Require `OWNER.md` for every retained shared service.
3. Reject new one-file or placeholder services.
4. Move library-like logic out of `shared-services/` into platform or libs.
5. Prefer deletion over indefinite placeholder retention.

## Practical Guidance

When deciding where new code goes, choose in this order:

1. Product first
2. Platform or libs second
3. Shared service only if a true runtime boundary is needed

This ordering prevents premature centralization and keeps `shared-services/` small, intentional, and operationally credible.