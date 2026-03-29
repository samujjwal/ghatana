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

- `auth-gateway`: retained as a shared runtime service
- `user-profile-service`: retained as a shared runtime service
- `ai-inference-service`: shared runtime candidate that must stay operationally justified
- `feature-store-ingest`: migrated to `products/data-cloud/feature-store-ingest`; the local directory is retained only as historical residue and is not included in Gradle

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
