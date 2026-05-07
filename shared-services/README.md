# shared-services

Deployable cross-product runtime services.

This directory is for networked services and gateways that are shared by multiple products at runtime. It is not the default home for reusable library code.

## What belongs here

- Services consumed by multiple products over HTTP, gRPC, or messaging
- Capabilities with an explicit runtime boundary, owner, and operational lifecycle
- Gateways and central services such as authentication or profile management

## What does not belong here

- Reusable in-process libraries and SDKs: keep those in `platform/`
- Product-specific business logic: keep that in `products/<product>`
- Ownerless stubs or placeholder modules

## Current posture

- `auth-gateway`: **ACTIVE** — retained as the canonical cross-product authentication service (ADR-013, ADR-019)
- `user-profile-service`: **ACTIVE** — retained as the cross-product user-profile service
- `incident-service`: **ACTIVE** — kill switch and graceful degradation for incident response
- `ai-inference-service`: **DELETED** (2026-04-21) — superseded by `platform:java:ai-integration`; product teams use platform module directly
- `ai-registry-service`: **DELETED** (2026-04-21) — test-only stub with no implementation
- `auth-service`: **DELETED** (2026-04-21) — test-only stub; OAuth2 functionality in auth-gateway/AuthService.java
- `feature-store-ingest`: **RESIDUE** — canonical location is `products/data-cloud/planes/intelligence/feature-ingest`; this directory is not included in Gradle and will be deleted in the next cleanup sprint

## Build conventions

- Java toolchain is pinned to Java 21 through [shared-services/build.gradle.kts](/Users/samujjwal/Development/ghatana/shared-services/build.gradle.kts)
- JUnit Platform is enabled for all Java test tasks under this directory
- Shared services should prefer existing platform modules before adding local infrastructure code

## Ownership requirements

Every live shared service must include:

- `OWNER.md`
- clear operational ownership
- an explicit contract and rollout path

See [docs/SHARED_SERVICES_STRATEGY.md](/Users/samujjwal/Development/ghatana/docs/SHARED_SERVICES_STRATEGY.md) and [docs/adr/ADR-013-shared-services-ownership.md](/Users/samujjwal/Development/ghatana/docs/adr/ADR-013-shared-services-ownership.md) for the governing policy.

## Current Services

| Service                | Status | Purpose                                                                     | Owner                             |
| ---------------------- | ------ | --------------------------------------------------------------------------- | --------------------------------- |
| `auth-gateway`         | ACTIVE | Centralized authentication gateway with JWT, OAuth2, tenant extraction, MFA | Security Team (Platform Sub-team) |
| `user-profile-service` | ACTIVE | Cross-product user profile and preferences storage                          | Platform Team                     |
| `incident-service`     | ACTIVE | Kill switch and graceful degradation for incident response                  | Security Team (Platform Sub-team) |

## Deployment

Services are deployed via Kubernetes manifests in `shared-services/infrastructure/k8s/`. Note: Currently only product-specific manifests exist (AEP, Data-Cloud, etc.). Shared-service manifests are a work in progress.
