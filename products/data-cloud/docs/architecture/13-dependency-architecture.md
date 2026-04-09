# Dependency Architecture

See [04-workspace-module-architecture.md](04-workspace-module-architecture.md), [07-shared-library-architecture.md](07-shared-library-architecture.md), and [`diagrams/module-dependencies.mmd`](diagrams/module-dependencies.mmd).

## Dependency Landscape

| Layer | Primary Frameworks / Libraries | Evidence |
|---|---|---|
| Backend runtime | ActiveJ, Jackson, Micrometer, gRPC, HikariCP | `launcher/build.gradle.kts`, `platform-launcher/build.gradle.kts`, `platform-api/build.gradle.kts` |
| Storage / infra | PostgreSQL/JPA/Flyway, Redis, Kafka, S3/Ceph, ClickHouse, OpenSearch, RocksDB, SQLite, H2, Caffeine | `platform-launcher/build.gradle.kts`, Helm values, Terraform docs |
| Frontend | React 19, React Router 7, TanStack Query, Jotai, RHF, Zod, Vite, Tailwind, Monaco, xyflow | `ui/package.json` |
| Testing | JUnit 5, AssertJ, Mockito, ArchUnit, Testcontainers, Vitest, Playwright, Storybook | module build files and `ui/package.json` |
| Security / analysis | SpotBugs, OWASP Dependency-Check, CodeQL, GitLeaks, TruffleHog | `.github/workflows/security-scanning.yml` |

## Implemented

- `platform-launcher` is the dependency hub for backend runtime features and carries the broadest infra surface.
- `launcher` is intentionally thinner and depends on `platform-launcher` plus `platform-api`.
- `feature-store-ingest` and `agent-registry` both depend directly on heavy runtime modules instead of a narrower extracted client/store layer.
- The UI uses a modern React stack plus several workspace-shared UI packages.

## Inferred

- Dependency breadth reflects product ambition and active extraction work, but today it also reflects incomplete modularization rather than only deliberate extensibility.

## Missing

- There is no enforced lightweight client module for downstream product integrations; `platform-client` exists as a placeholder with no sources.

## Duplicate / Overlap Matrix

| Category | Overlap | Evidence | Architectural Impact |
|---|---|---|---|
| Redis clients | `jedis` and `lettuce-core` | `platform-launcher/build.gradle.kts`, `platform-plugins/build.gradle.kts` | larger runtime surface, dual programming models |
| OpenSearch clients | Java client and REST client | `platform-launcher/build.gradle.kts` | extra maintenance and configuration burden |
| Embedded/local DBs | `h2`, `sqlite-jdbc`, RocksDB plus PostgreSQL | `platform-launcher/build.gradle.kts` | multiple runtime/storage modes inside one module |
| UI API layers | `ui/src/api`, `ui/src/lib/api`, `ui/src/services` | source tree | duplicate transport logic |
| API abstraction layers | `platform-api` controllers and `launcher/http` handlers | source tree | split transport ownership |

## Framework Usage Map

| Concern | Dominant Choice | Conflicting / Secondary Choice |
|---|---|---|
| Async backend runtime | ActiveJ Promise/eventloop | virtual-thread executor use for blocking isolation |
| HTTP server | ActiveJ HTTP | no alternate server in product-local runtime |
| Metrics | Micrometer via `MetricsCollector` | product-specific metrics wrappers add another layer |
| Browser async data | TanStack Query | Jotai also carries state, sometimes overlapping responsibility |
| Validation | Jakarta Validation and custom validators | Zod in older frontend client layer |

## Version / Pattern Drift

**Implemented**
- Java 21 is standardized in backend modules.
- Node 20 is referenced by product-local security workflow.
- React 19 and React Router 7 are already adopted in the UI.

**Inferred**
- Some product-local docs and tests still describe older browser routes and API shapes, so dependency modernization has moved faster than contract cleanup.

## Dependency Findings

| Finding | Evidence | Impact |
|---|---|---|
| `platform-launcher` is both runtime and dependency aggregator | broad build file plus 237 main source files | extraction pressure remains high |
| `feature-store-ingest` knowingly depends on the wrong abstraction | comment in `feature-store-ingest/build.gradle.kts` | heavyweight transitive closure |
| Frontend duplicates transport and validation choices | `zod` in old client, newer `lib/api` bypasses some schema validation | inconsistent browser guarantees |
| Placeholder modules hide intended dependency direction | `platform-client`, `sdk`, `platform-governance` | unclear future architecture |
