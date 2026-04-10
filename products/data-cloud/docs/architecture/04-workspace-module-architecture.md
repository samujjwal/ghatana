# Workspace And Module Architecture

See [`diagrams/workspace-structure.mmd`](diagrams/workspace-structure.mmd), [`diagrams/module-dependencies.mmd`](diagrams/module-dependencies.mmd), and [`diagrams/shared-library-consumers.mmd`](diagrams/shared-library-consumers.mmd).

## Repo Structure

**Implemented**
- Data Cloud is a product-scoped subtree inside the monorepo and is included as multiple Gradle modules from the root `settings.gradle.kts`.
- The product also contains a standalone Node/Vite workspace for the UI.

| Area | Path | Purpose |
|---|---|---|
| Product docs | `products/data-cloud/docs` | Maintained docs, OpenAPI, testing plans |
| Historical/generated docs | `products/data-cloud/docs-generated` | Earlier generated analysis output |
| Shared Java contracts/libs | `products/data-cloud/spi`, `platform-*` | Runtime libraries |
| Deployable Java app | `products/data-cloud/launcher` | Standalone transport/bootstrap |
| Frontend | `products/data-cloud/ui` | React/Vite SPA |
| Infra | `products/data-cloud/helm`, `k8s`, `terraform` | Delivery and deployment |
| Product scripts | `products/data-cloud/scripts` | Verification, drift detection, DR/smoke helpers |

## Included Gradle Modules

| Module | Primary Role | Notes |
|---|---|---|
| `:products:data-cloud:spi` | Public contracts | Most important external consumption seam |
| `:products:data-cloud:platform-entity` | Domain/persistence models | Shared by SPI and runtime |
| `:products:data-cloud:platform-event` | Event abstractions | Supports replay/durability/plugins |
| `:products:data-cloud:platform-config` | Config pipeline | YAML load/compile/reload |
| `:products:data-cloud:platform-analytics` | Analytics/query/report services | Depends on event/entity |
| `:products:data-cloud:platform-api` | Controllers and app services | Partial extraction from launcher |
| `:products:data-cloud:platform-launcher` | Runtime/infra core | Still very wide in scope |
| `:products:data-cloud:platform-client` | Planned lean client | Currently empty extraction target |
| `:products:data-cloud:platform-plugins` | Plugin implementations | Kafka, Redis, Iceberg, vector, etc. |
| `:products:data-cloud:launcher` | Standalone runtime app | HTTP/gRPC bootstrap and handlers |
| `:products:data-cloud:sdk` | OpenAPI-driven SDK generation | Generated outputs go to `build/` |
| `:products:data-cloud:agent-registry` | Agent metadata persistence | Used by AEP-facing integrations |
| `:products:data-cloud:feature-store-ingest` | Event-to-feature worker | Polling service |

## Dependency Direction

**Implemented**
- Root settings and build files enforce Data Cloud as a product boundary and show intended downward dependency flow from deployables to shared modules.
- Actual dependencies still concentrate around `platform-launcher`.

### Implemented Core Flow

`launcher` → `platform-launcher` → shared libs (`spi`, `platform-entity`, `platform-event`, `platform-config`, `platform-analytics`, `platform-plugins`)

### Implemented Consumer Flow

`agent-registry` → `platform-launcher`  
`feature-store-ingest` → `platform-launcher`  
Other products (AEP, YAPPC, Tutorputor) → mostly `spi` and select public modules via root settings/build files

## Build Relationships

| Build Surface | Evidence | Architectural Meaning |
|---|---|---|
| Root Gradle includes all Data Cloud modules | `settings.gradle.kts` | Product lives in one monorepo build graph |
| Product modules use root convention plugins | root `build.gradle.kts` | Shared Java/testing/quality defaults |
| UI uses Vite/Vitest/Playwright/Storybook | `ui/package.json` | Separate frontend toolchain |
| SDK generation depends on OpenAPI | `sdk/build.gradle.kts` | REST contract feeds client codegen |
| Drift checks and verification scripts are product-local | `scripts/check-openapi-drift.sh`, `verify-coverage.sh` | Some architectural governance is script-enforced |

## Shared Abstractions

**Implemented**
- `spi` is the cleanest public abstraction layer.
- `platform-entity`, `platform-event`, `platform-config`, and `platform-analytics` are genuine shared internals.

**Inferred**
- `platform-api`, `platform-client`, and `platform-launcher` represent an unfinished decomposition plan rather than a fully stabilized layering scheme.

## Fragmentation And Coupling

| Finding | Evidence | Impact |
|---|---|---|
| `platform-launcher` remains over-centralized | 237 main-source files; owns client, DI, distributed, embedded, infra, grpc, migration, observability, storage | high coupling, slow extractions |
| `platform-api` extraction is partial | build file says extracted from `platform-launcher`, but live HTTP routing still lives in `launcher/http` | duplicated API surface |
| `platform-client` exists without sources | empty module with extraction comments only | architectural placeholder debt |
| `sdk` and `api` are support modules, not runtime modules | empty `sdk/src`, spec-only `api` dir | inventory noise if not documented clearly |
| UI also has multiple data-access layers | `src/api`, `src/lib/api`, `src/services` | frontend boundary confusion |

## Ease Of Adding/Removing Modules

**Implemented**
- Adding a Gradle module is straightforward at the monorepo level.

**Missing**
- There is no evidence of a completed extraction playbook that ensures modules are either fully real or clearly marked deprecated/placeholder.

**Recommended**
- Finish the current split before creating additional Data Cloud platform submodules.
