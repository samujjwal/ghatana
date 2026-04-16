# Data Cloud Test Manual

This manual explains how to validate Data Cloud safely and repeatedly across backend, SDK, UI, durability, and operational checks.

Use it together with:

- `DEVELOPER_MANUAL.md` for everyday development setup
- `RUNBOOK.md` for validated deployment and recovery paths
- `README.md` for the product overview and quick start

## 1. Test Strategy

Data Cloud uses several test layers. Treat them differently:

| Layer | Purpose | Typical Scope |
| --- | --- | --- |
| Unit tests | fast behavior validation | one module or class |
| Integration tests | real provider and transport validation | launcher, plugins, Testcontainers |
| Contract checks | route and schema alignment | OpenAPI, SDKs, UI contracts |
| UI tests | React component and page behavior | Vitest + RTL |
| Browser tests | key user journeys | Playwright |
| Load and durability tests | latency, throughput, tenant isolation | durable suites and reports |

## 2. Prerequisites

For the full test surface:

- Java 21
- `pnpm`
- Docker or compatible runtime for Testcontainers-based tests
- `node`

Before blaming Data Cloud code, verify Docker is actually healthy if a durable provider test fails unexpectedly.

## 3. Default Fast Validation

Run this first when your change touches normal product code:

```bash
./gradlew :products:data-cloud:launcher:test
./gradlew :products:data-cloud:sdk:build
pnpm --dir products/data-cloud/ui type-check
pnpm --dir products/data-cloud/ui lint
pnpm --dir products/data-cloud/ui test
```

This gives you coverage across:

- launcher and transport logic
- SDK generation and compilation
- strict TypeScript verification
- frontend lint and unit tests

## 4. Backend Module Validation

### Full product build

```bash
./gradlew :products:data-cloud:build
```

Use this before merging broad backend changes.

### Focused provider tests

```bash
./gradlew :products:data-cloud:platform-plugins:test --tests "*PostgresEntityStore*"
./gradlew :products:data-cloud:platform-plugins:test --tests "*DurableMultiTenantLoadIntegrationTest"
./gradlew :products:data-cloud:platform-launcher:test --tests "*DataCloudFactoryTest"
```

Use these when you change:

- entity persistence
- event durability
- runtime profile composition
- provider discovery

### JMH-backed benchmark path

```bash
./gradlew :products:data-cloud:platform-launcher:jmhClasses
./gradlew :products:data-cloud:platform-launcher:jmh -Pjmh.include="EntityCrudBenchmark|DataCloudBenchmark"
```

Results are written under:

- `products/data-cloud/platform-launcher/build/reports/jmh/`

## 5. Durable Load And Isolation Validation

The dedicated runner is:

```bash
products/data-cloud/scripts/run-durable-load-suite.sh
```

What it validates:

- PostgreSQL and Kafka durable paths
- latency percentiles for writes and reads
- multi-tenant isolation
- sustained workload behavior
- metrics artifact generation

Primary artifact:

- `products/data-cloud/build/reports/load-tests/durable-multi-tenant-load.json`

Useful environment overrides:

- `DATACLOUD_LOAD_TENANTS`
- `DATACLOUD_LOAD_ENTITY_OPS_PER_TENANT`
- `DATACLOUD_LOAD_EVENT_OPS_PER_TENANT`
- `DATACLOUD_LOAD_ITERATIONS`
- `DATACLOUD_LOAD_TIMEOUT_SECONDS`
- `DATACLOUD_LOAD_MIN_THROUGHPUT_OPS_PER_SECOND`
- `DATACLOUD_LOAD_MAX_HEAP_DELTA_MB`
- `DATACLOUD_LOAD_MAX_P95_ENTITY_SAVE_MS`
- `DATACLOUD_LOAD_MAX_P95_EVENT_APPEND_MS`
- `DATACLOUD_LOAD_MAX_P95_QUERY_MS`

## 6. Frontend Validation

### Install once

```bash
pnpm --dir products/data-cloud/ui install
```

### Type-check and lint

```bash
pnpm --dir products/data-cloud/ui type-check
pnpm --dir products/data-cloud/ui lint
```

### Unit and component tests

```bash
pnpm --dir products/data-cloud/ui test
pnpm --dir products/data-cloud/ui test:ui
```

### Contract tests

```bash
pnpm --dir products/data-cloud/ui test:contract
```

### Browser tests

```bash
pnpm --dir products/data-cloud/ui test:e2e
pnpm --dir products/data-cloud/ui test:e2e:headed
pnpm --dir products/data-cloud/ui test:e2e:debug
```

Use headed or debug modes only when diagnosing a failure. Keep the default CI path lean.

## 7. API And Documentation Drift Checks

These checks matter whenever you change routes, payload shapes, or docs that claim feature behavior.

```bash
products/data-cloud/scripts/check-openapi-drift.sh
products/data-cloud/scripts/check-doc-boundaries.sh
products/data-cloud/scripts/verify-requirements.sh
```

Run `check-openapi-drift.sh` whenever you touch:

- `launcher/`
- `REST_API_DOCUMENTATION.md`
- `api/openapi.yaml`
- frontend API clients or contract schemas

## 8. Operational And Recovery Validation

Use these scripts when testing backup, restore, or operational readiness changes:

```bash
products/data-cloud/scripts/backup-postgres.sh
products/data-cloud/scripts/restore-postgres.sh
products/data-cloud/scripts/validate-backup.sh
products/data-cloud/scripts/run-backup-drill.sh
products/data-cloud/scripts/run-smoke-e2e.sh
```

Use the product `RUNBOOK.md` as the source of truth for interpreting recovery success and provider-specific failure signatures.

## 9. CI-Oriented Verification Set

A strong pre-merge verification set for non-trivial changes is:

```bash
./gradlew :products:data-cloud:build
./gradlew :products:data-cloud:sdk:build
pnpm --dir products/data-cloud/ui type-check
pnpm --dir products/data-cloud/ui lint
pnpm --dir products/data-cloud/ui test:contract
products/data-cloud/scripts/check-openapi-drift.sh
```

Add the durable load suite if you changed any of these areas:

- entity or event persistence
- tenant isolation
- provider discovery or profiles
- retention and compaction behavior

## 10. What To Test By Change Type

### HTTP or handler change

Run:

- `:products:data-cloud:launcher:test`
- `products/data-cloud/scripts/check-openapi-drift.sh`
- relevant frontend contract tests if a UI consumer exists

### Entity or event store change

Run:

- relevant `platform-plugins` tests
- durable load suite
- any tenant isolation checks tied to your provider

### SDK or OpenAPI change

Run:

- `:products:data-cloud:sdk:build`
- OpenAPI drift checks
- frontend contract tests if TypeScript clients consume the same schema

### UI route or page change

Run:

- `pnpm --dir products/data-cloud/ui type-check`
- `pnpm --dir products/data-cloud/ui lint`
- `pnpm --dir products/data-cloud/ui test`
- `pnpm --dir products/data-cloud/ui test:e2e` for key flows if navigation changed materially

### Deployment or profile change

Run:

- focused launcher tests
- relevant runbook procedures
- backup or smoke scripts if the change affects startup, readiness, or recovery

## 11. Troubleshooting

### Testcontainers failures on macOS

Check:

- Docker Desktop is running
- ports are not blocked by a stale local process
- the environment matches the Data Cloud convention documented in `RUNBOOK.md`

### UI contract tests fail but unit tests pass

Usually means a drift between:

- backend route registration
- `api/openapi.yaml`
- frontend API or schema assumptions

Treat this as a contract bug, not just a test annoyance.

### Durable suite is slow but not obviously broken

Look at the artifact in:

- `products/data-cloud/build/reports/load-tests/durable-multi-tenant-load.json`

Check p95 metrics, tenant count, and whether your change increased work per operation before changing thresholds.

### Coverage or flakiness regressions

Use:

```bash
products/data-cloud/scripts/verify-coverage.sh
products/data-cloud/scripts/verify-flakiness.sh
```

Do not lower gates casually. Fix the source of non-determinism or missing test depth.

## 12. Definition Of Done For Testing

A Data Cloud change is not ready when only one layer is green. As a rule:

- code changes need the nearest unit or integration coverage green
- API changes need route and contract verification green
- UI changes need strict type-check plus the relevant test layer green
- persistence and tenant-isolation changes need durable-path validation green

If you cannot run one of those locally because a required tool is missing, record that limitation explicitly in your handoff and point to the exact command the next person should run.