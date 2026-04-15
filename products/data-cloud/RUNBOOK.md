# Data Cloud Operations Runbook

## Scope

This runbook covers the validated Data Cloud deployment paths and the verification steps that now exist in the repository.

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
./gradlew :products:data-cloud:platform-plugins:test --tests "*PostgresEntityStore*"
./gradlew :products:data-cloud:platform-plugins:test --tests "*DurableMultiTenantLoadIntegrationTest"
./gradlew :products:data-cloud:platform-launcher:test --tests "*DataCloudFactoryTest"
```

### Extended durable load run

```bash
./products/data-cloud/scripts/run-durable-load-suite.sh
```

The durable load suite runs against real PostgreSQL and Kafka Testcontainers, records latency percentiles for entity writes, event appends, and tenant-scoped reads, verifies zero cross-tenant leakage in both stores, supports repeated soak iterations, and writes a JSON metrics artifact to `products/data-cloud/build/reports/load-tests/durable-multi-tenant-load.json`.

Environment overrides:

- `DATACLOUD_LOAD_TENANTS`
- `DATACLOUD_LOAD_ENTITY_OPS_PER_TENANT`
- `DATACLOUD_LOAD_EVENT_OPS_PER_TENANT`
- `DATACLOUD_LOAD_ITERATIONS`
- `DATACLOUD_LOAD_TIMEOUT_SECONDS`
- `DATACLOUD_LOAD_MIN_THROUGHPUT_OPS_PER_SECOND`

For manual Gradle invocation, the equivalent JVM properties are:

```bash
./gradlew :products:data-cloud:platform-plugins:test \
  --tests "*DurableMultiTenantLoadIntegrationTest" \
  -Ddatacloud.load.tenants=100 \
  -Ddatacloud.load.entityOpsPerTenant=25 \
  -Ddatacloud.load.eventOpsPerTenant=25 \
  -Ddatacloud.load.iterations=1 \
  -Ddatacloud.load.timeoutSeconds=1800 \
  -Ddatacloud.load.minThroughputOpsPerSecond=0 \
  -Ddatacloud.load.metricsOutput=$PWD/products/data-cloud/build/reports/load-tests/durable-multi-tenant-load.json
```

### JMH benchmark compilation and execution

```bash
./gradlew :products:data-cloud:platform-launcher:jmhClasses
./gradlew :products:data-cloud:platform-launcher:jmh -Pjmh.include="EntityCrudBenchmark|DataCloudBenchmark"
```

Results are written under `products/data-cloud/platform-launcher/build/reports/jmh/`.

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