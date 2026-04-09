# Architecture Decisions

See [04-workspace-module-architecture.md](04-workspace-module-architecture.md), [07-shared-library-architecture.md](07-shared-library-architecture.md), and [20-risks-gaps-recommendations.md](20-risks-gaps-recommendations.md).

## Reconstructed Decisions

| Decision | Evidence | Status |
|---|---|---|
| Keep Data Cloud product boundary separate from AEP runtime | `README.md`, `OWNER.md`, ADR-DC-001 | deliberate |
| Use monorepo multi-module Gradle structure | `settings.gradle.kts` includes | deliberate |
| Build around ActiveJ async runtime | launcher and SPI Promise/eventloop usage | deliberate |
| Support many storage/runtime backends through plugins | `platform-plugins`, build deps, Helm/Terraform | deliberate but broad |
| Extract API and client layers from `platform-launcher` | `FINDING-DC-H2` comments in build files | partial |
| Provide standalone deployable launcher | `launcher` module, Dockerfile, K8s/Helm | deliberate |
| Use React SPA with simplified information architecture | `ui/src/routes.tsx` comments and route tree | deliberate but mid-migration |
| Use Data Cloud as durable persistence backend for agent registry | `agent-registry` docs and code comments | deliberate |

## Decision: Separate Data Cloud from AEP Runtime

**Evidence**
- `products/data-cloud/README.md`
- `products/data-cloud/OWNER.md`
- `products/data-cloud/docs/ADR-DC-001-MODULE-OWNERSHIP.md`

**Likely Intent**
- Prevent product entanglement and keep Data Cloud as the persistence/analytics/data plane while AEP owns agent execution.

**Benefits**
- clearer product ownership
- lower coupling to agent runtime

**Drawbacks**
- some subsystems like memory plane and registry still sit very close to agent concerns

**Adoption Consistency**
- mostly consistent, especially in docs and module boundaries

## Decision: Use `platform-launcher` as the Runtime Kernel

**Evidence**
- `launcher/build.gradle.kts`
- `platform-launcher/build.gradle.kts`
- `DataCloud.java`

**Likely Intent**
- centralize runtime composition and shared infrastructure in one reusable backend module.

**Benefits**
- fast feature development and one place for adapters

**Drawbacks**
- module became too broad and is now the main architectural hotspot

**Adoption Consistency**
- consistent in implementation, but now a liability

## Decision: Split `platform-api` and `platform-client` Out of `platform-launcher`

**Evidence**
- build comments tagged `FINDING-DC-H2`
- empty `platform-client`
- partial `platform-api`

**Likely Intent**
- sharpen boundaries between transport/app services, runtime infra, and external clients.

**Benefits**
- better long-term modularity if completed

**Drawbacks**
- current repo is in a half-migrated state

**Adoption Consistency**
- partial

## Decision: Use Generic Entity and Event Stores as Core Product Abstractions

**Evidence**
- `spi/EntityStore.java`
- `spi/EventLogStore.java`
- entity/event HTTP and gRPC surfaces

**Likely Intent**
- make the product extensible across use cases without hardcoding one domain schema.

**Benefits**
- high reuse potential
- flexible collection model

**Drawbacks**
- many downstream features need their own adapter/mapping layers

**Adoption Consistency**
- high

## Decision: Support Multiple Deployment Modes and Backends

**Evidence**
- Docker, Helm, K8s, Terraform
- H2, SQLite, RocksDB, PostgreSQL, Redis, Kafka, OpenSearch, ClickHouse, S3 deps

**Likely Intent**
- run from local/dev through production-scale cloud deployments and edge/embedded variants.

**Benefits**
- flexibility and broad product fit

**Drawbacks**
- operational and code complexity

**Adoption Consistency**
- high in code and infra assets, uneven in docs

## Decision: Simplify UI Information Architecture

**Evidence**
- comments at top of `ui/src/routes.tsx`
- route aliases preserving old pages

**Likely Intent**
- reduce route sprawl by collapsing collections/datasets/lineage/quality into broader experience areas.

**Benefits**
- cleaner navigation model

**Drawbacks**
- transition period leaves stale tests and duplicate clients

**Adoption Consistency**
- partial but visible

## Decision Findings

| Finding | Assessment |
|---|---|
| Most important decisions look deliberate; most inconsistencies are mid-migration rather than random | supports a cleanup-first roadmap |
| The biggest accidental architecture is not product scope, but half-finished extraction work | warrants focused stabilization effort |
