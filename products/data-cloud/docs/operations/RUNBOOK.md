# Data Cloud Operations Runbook

## Scope

This runbook covers the validated Data Cloud deployment paths and the verification steps that now exist in the repository.

The UI's shell-role mode is a disclosure and diagnostics aid only. It does not change backend authorization or tenant boundaries.

## Deployment Modes

### Local development

- `DATACLOUD_PROFILE=local`
- Uses explicit in-memory entity and event stores even when durable providers are present on the classpath
- Intended for development and manual API validation only

### Sovereign embedded mode

- `DATACLOUD_PROFILE=sovereign`
- `DATACLOUD_SOVEREIGN_DATA_DIR=/absolute/path/to/data`
- `DATACLOUD_COMPACTION_INTERVAL_SECONDS=300`
- `DATACLOUD_COMPACTION_TOMBSTONE_THRESHOLD=100`
- Starts with embedded file-backed H2 stores and disables external-LLM-only features

### PostgreSQL-backed entity storage

- `DATACLOUD_PROFILE=staging` or `DATACLOUD_PROFILE=production`
- `DATACLOUD_DB_URL=jdbc:postgresql://host:5432/datacloud`
- `DATACLOUD_DB_USER=datacloud`
- `DATACLOUD_DB_PASSWORD=...`
- Optional pool tuning:
  - `DATACLOUD_DB_POOL_MAX_SIZE`
  - `DATACLOUD_DB_POOL_MIN_IDLE`
  - `DATACLOUD_DB_CONN_TIMEOUT_MS`
  - `DATACLOUD_DB_IDLE_TIMEOUT_MS`
  - `DATACLOUD_DB_MAX_LIFETIME_MS`

The PostgreSQL provider is discovered via `META-INF/services/com.ghatana.datacloud.spi.EntityStore`. If the plugin is present but the database variables are missing, the provider fails with a clear configuration error on first use.

### HTTP security and settings-store startup guardrails

- Non-embedded profiles fail startup when both API key and JWT authentication are missing.
- Configure one of:
  - `DATACLOUD_API_KEYS`
  - `DATACLOUD_JWT_SECRET` or `DATACLOUD_JWT_JWKS_URL`
- Insecure mode (`DATACLOUD_INSECURE_MODE=true`) is only intended for local/embedded workflows.
- In insecure embedded mode, bind host must be loopback (`127.0.0.1`, `localhost`, or `::1`).
- Strict/production-like profiles require durable settings storage; in-memory settings storage is blocked at startup.

### Kafka-backed durable event storage

- `DATACLOUD_KAFKA_BOOTSTRAP_SERVERS=host:9092`
- Optional tuning:
  - `DATACLOUD_KAFKA_PARTITIONS`
  - `DATACLOUD_KAFKA_REPLICATION_FACTOR`
  - `DATACLOUD_KAFKA_READ_TIMEOUT_MS`

The durable event-store path is now discovered through the legacy SPI registration at `META-INF/services/com.ghatana.datacloud.spi.EventLogStore`, which `DataCloud` adapts to the platform-owned event-store contract at startup.

## Validation Commands

### Focused provider tests

```bash
./gradlew :products:data-cloud:extensions:plugins:test --tests "*PostgresEntityStore*"
./gradlew :products:data-cloud:extensions:plugins:test --tests "*DurableMultiTenantLoadIntegrationTest"
./gradlew :products:data-cloud:delivery:runtime-composition:test --tests "*DataCloudFactoryTest"
```

### Extended durable load run

```bash
./products/data-cloud/scripts/run-durable-load-suite.sh
```

The durable load suite runs against real PostgreSQL and Kafka Testcontainers, records latency percentiles for entity writes, event appends, and tenant-scoped reads, verifies zero cross-tenant leakage in both stores, supports repeated soak iterations, and writes a JSON metrics artifact to `products/data-cloud/build/reports/load-tests/durable-multi-tenant-load.json`.

The dedicated runner disables Testcontainers Ryuk (`TESTCONTAINERS_RYUK_DISABLED=true`) to match the existing Data Cloud CI pattern and forces `TESTCONTAINERS_HOST_OVERRIDE=localhost` so host-run macOS executions connect to exposed container ports correctly instead of resolving `host.docker.internal` from the JVM.

Environment overrides:

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

For manual Gradle invocation, the equivalent JVM properties are:

```bash
./gradlew :products:data-cloud:extensions:plugins:test \
  --tests "*DurableMultiTenantLoadIntegrationTest" \
  TESTCONTAINERS_RYUK_DISABLED=true \
  -Ddatacloud.load.tenants=100 \
  -Ddatacloud.load.entityOpsPerTenant=25 \
  -Ddatacloud.load.eventOpsPerTenant=25 \
  -Ddatacloud.load.iterations=1 \
  -Ddatacloud.load.timeoutSeconds=1800 \
  -Ddatacloud.load.minThroughputOpsPerSecond=0 \
  -Ddatacloud.load.maxHeapDeltaMb=256 \
  -Ddatacloud.load.maxP95EntitySaveMs=2500 \
  -Ddatacloud.load.maxP95EventAppendMs=2500 \
  -Ddatacloud.load.maxP95QueryMs=2500 \
  -Ddatacloud.load.metricsOutput=$PWD/products/data-cloud/build/reports/load-tests/durable-multi-tenant-load.json
```

### Scheduled CI execution and published artifacts

The durable load suite is also wired into the repository workflow at `.github/workflows/data-cloud-durable-load.yml`.

- It runs on a weekly schedule and also supports `workflow_dispatch` for manual baselines.
- Each run uploads:
  - `data-cloud-durable-load-metrics` — JSON metrics artifact from `products/data-cloud/build/reports/load-tests/`
  - `data-cloud-durable-load-test-results` — JUnit XML plus Gradle HTML test report for the durable suite
- The workflow writes a compact throughput and latency summary into the GitHub Actions job summary so the latest baseline is visible without downloading artifacts.

### JMH benchmark compilation and execution

```bash
./gradlew :products:data-cloud:delivery:runtime-composition:jmhClasses
./gradlew :products:data-cloud:delivery:runtime-composition:jmh -Pjmh.include="EntityCrudBenchmark|DataCloudBenchmark"
```

Results are written under `products/data-cloud/delivery/runtime-composition/build/reports/jmh/`.

## Operational Checks

### Tenant isolation

- Verify every durable entity row carries the correct `tenant_id`
- For PostgreSQL-backed tests, use direct SQL inspection against the `entities` table after writes
- Querying tenant A data as tenant B must return zero results through the `EntityStore` SPI
- For Kafka-backed load validation, each tenant topic must return only payloads tagged with that tenant's `tenantId`

### Sovereign compaction

- Monitor compaction metrics and audit events for compaction runs
- If storage grows unexpectedly, inspect tombstone count before lowering the interval or threshold
- Use sovereign mode only with a persistent `DATACLOUD_SOVEREIGN_DATA_DIR`

### Failure symptoms

- `No durable EntityStore provider found`: non-local profile started without a discovered provider
- `No durable EventLogStore provider found`: non-local profile started without a discovered event-store provider on the classpath
- `PostgresEntityStore discovered on the classpath, but no database configuration was provided`: provider present but missing `DATACLOUD_DB_*` or `DC_DB_*`
- H2 sovereign startup failures with `AUTO_SERVER=TRUE`: unsupported by the repo H2 version; use `jdbc:h2:file:<path>;DB_CLOSE_ON_EXIT=FALSE`

## Recovery Notes

- For sovereign mode, restore from the data directory snapshot and restart with the same `DATACLOUD_SOVEREIGN_DATA_DIR`
- For PostgreSQL mode, recover the database first, then restart the service so the provider reconnects through HikariCP
- After any recovery, rerun the focused provider tests or the health-probe flow before reopening traffic

## Production Startup Checklist (P0-01 Durable Storage Gate)

The following sequence must succeed before Data Cloud accepts production traffic. Each step is validated by automated tests in the launcher and platform-launcher suites.

### Step 1 — Verify durable storage configuration

Set all required environment variables before launching:

```bash
# PostgreSQL-backed entity + event storage (production mandatory)
export DATACLOUD_PROFILE=production
export DATACLOUD_DB_URL=jdbc:postgresql://db-host:5432/datacloud
export DATACLOUD_DB_USER=datacloud
export DATACLOUD_DB_PASSWORD=<vault-resolved>
export DATACLOUD_KAFKA_BOOTSTRAP_SERVERS=kafka-host:9092

# Settings store (required in production; in-memory is rejected at startup)
export DATACLOUD_DB_SETTINGS_TABLE=dc_settings
```

Non-durable (`in-memory`) settings storage is blocked at startup when `DATACLOUD_PROFILE` is `production` or `strict`. The launcher throws `DataCloudTransportStartupException` and exits with a non-zero code. Validated by `DataCloudHttpServerProductionDependencyTest`.

### Step 2 — Verify authentication is configured

At least one of the following must be set for non-embedded profiles:

```bash
# Option A: static API key list (comma-separated; supports rotation)
export DATACLOUD_API_KEYS=<current-key>,<previous-key>

# Option B: JWT with shared secret
export DATACLOUD_JWT_SECRET=<secret>

# Option C: JWT with JWKS endpoint
export DATACLOUD_JWT_JWKS_URL=https://idp.example.com/.well-known/jwks.json
```

Missing auth configuration causes a startup failure validated by `DataCloudHttpLauncherBootstrapTest`.

### Step 3 — Verify health probes

After startup, confirm the health endpoints respond correctly:

```bash
curl http://localhost:8080/health/ready     # must return 200 {"status":"UP"}
curl http://localhost:8080/health/live      # must return 200
curl http://localhost:8080/metrics          # must return Prometheus text format
```

The readiness probe returns `503` until all durable stores (PostgreSQL, Kafka) have established their connections.

### Step 4 — Run migration contract checks

Before cutting traffic, verify schema migrations have been applied cleanly:

```bash
./gradlew :products:data-cloud:delivery:runtime-composition:test \
  --tests "com.ghatana.datacloud.migration.DatabaseMigrationContractTest" \
  --no-daemon
```

All 10 migration contract tests must pass. This confirms: version contiguity, tenant_id NOT NULL enforcement across all domain tables, no DEFAULT NULL loopholes, tenant-scoped unique constraints, workload-path lookup indexes, and RLS tenant isolation policy installation.

### Step 5 — Confirm no in-memory storage

In production the service must NOT log:

```
WARN  DataCloudHttpLauncherBootstrap - Settings store missing; defaulting to IN-MEMORY
WARN  DataCloudHttpLauncherBootstrap - Embedded profile detected; authentication is OPTIONAL
```

If these warnings appear with `DATACLOUD_PROFILE=production`, the startup guard has been bypassed and the deployment must not receive traffic.

### Launch-mode summary

| Profile | EntityStore | EventStore | Auth required | Settings store |
|---|---|---|---|---|
| `local` | In-memory | In-memory | Optional | In-memory (allowed) |
| `sovereign` | H2 file-backed | In-memory | Optional | JDBC or in-memory |
| `staging` | PostgreSQL | Kafka | Required | JDBC (required) |
| `production` | PostgreSQL | Kafka | Required | JDBC (required) |

Profiles `staging` and `production` use the strict profile policy. In-memory settings storage is rejected at startup in both.

## Production Tenant Isolation Verification

After deployment, confirm cross-tenant isolation is operational at the HTTP layer:

```bash
# Generate two API keys for different tenants
TENANT_A_KEY="key-tenant-a-..."
TENANT_B_KEY="key-tenant-b-..."

# Write an entity as tenant A
curl -X POST http://localhost:8080/api/v1/entities/my-collection \
  -H "Authorization: Bearer $TENANT_A_KEY" \
  -H "X-Tenant-ID: tenant-a" \
  -H "Content-Type: application/json" \
  -d '{"type":"product","data":{"name":"Widget"}}'

# Attempt to read as tenant B — must return 403
curl -o /dev/null -w "%{http_code}" \
  http://localhost:8080/api/v1/entities/my-collection \
  -H "Authorization: Bearer $TENANT_B_KEY" \
  -H "X-Tenant-ID: tenant-a"
# Expected: 403
```

Cross-tenant access denial is validated by `DataCloudHttpServerCriticalRouteTenantEnforcementTest`, `DataCloudHttpServerGovernanceTest.CrossTenantEnforcementTests`, and `TenantIsolationTest`.