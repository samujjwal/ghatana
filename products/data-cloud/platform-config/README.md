# data-cloud / platform-config

Configuration loading, validation, and profile resolution for the Data-Cloud product.

---

## Responsibilities

- Load collection, plugin, routing, and policy YAML configurations.
- Resolve per-tenant overrides against shared defaults.
- Validate config schemas at startup via `DataCloudStartupValidator`.
- Expose environment-variable-based runtime knobs through `DataCloudEnvConfig`.
- Support async hot-reload through `ConfigReloadManager`.

---

## Profile Resolution Precedence

Configuration is resolved at three levels. **Higher levels win over lower ones.**

```
1. Environment variables          (highest priority)
2. Filesystem / YAML files        (tenant-specific overrides default)
3. Built-in defaults              (lowest priority — compile-time constants)
```

### 1. Environment Variables (highest priority)

All runtime knobs must be read through `DataCloudEnvConfig`. No class in
`data-cloud` should call `System.getenv()` directly.

Key variables and their defaults:

| Variable | Default | Purpose |
|---|---|---|
| `DC_DEPLOYMENT_MODE` | `EMBEDDED` | `EMBEDDED` / `STANDALONE` / `DISTRIBUTED` |
| `DC_SERVER_URL` | *(none)* | Base URL for STANDALONE/DISTRIBUTED clients |
| `DC_CLUSTER_URLS` | *(none)* | Comma-separated URLs for DISTRIBUTED mode |
| `APP_ENV` | `production` | Runtime environment label |
| `DATACLOUD_PG_URL` | `jdbc:postgresql://localhost:5432/datacloud` | PostgreSQL JDBC URL (warm tier) |
| `DATACLOUD_PG_USER` | `datacloud` | PostgreSQL username |
| `DATACLOUD_PG_PASSWORD` | *(empty)* | PostgreSQL password — **must be set in prod** |
| `DATACLOUD_PG_POOL_SIZE` | `10` | HikariCP maximum pool size |
| `REDIS_HOST` | `localhost` | Redis hostname (hot tier) |
| `REDIS_PORT` | `6379` | Redis port |
| `S3_REGION` | `us-east-1` | AWS region for S3 cold-tier archive |
| `DC_S3_ARCHIVE_BUCKET` | `dc-archive` | S3 bucket name for cold tier |
| `ICEBERG_CATALOG_URI` | *(empty)* | Iceberg REST catalog URI (optional) |
| `ICEBERG_WAREHOUSE` | *(empty)* | Iceberg warehouse path |
| `DATACLOUD_HTTP_AUTH_TOKEN` | *(empty)* | Bearer token for remote client auth |

Environment variable interpolation is also supported inside YAML files using
`${VAR_NAME}` or `${VAR_NAME:default}` syntax. `ConfigLoader` performs the
substitution at YAML load time.

### 2. YAML File Resolution (middle tier)

`ConfigLoader` resolves YAML files in a deterministic two-step lookup for each
config kind (collection, plugin, routing, policy):

```
<config-base-path>/<kind>/<tenantId>/<name>.yaml   ← tenant-specific (checked first)
<config-base-path>/<kind>/default/<name>.yaml       ← shared default (fallback)
classpath:datacloud/<kind>/<tenantId>/<name>.yaml   ← classpath fallback (last resort)
```

**Rule**: if a tenant-specific file exists it is used exclusively; the default
file is only consulted when no tenant override is present. Listing operations
(e.g. `listPluginsAsync`) merge tenant and default entries, deduplicating by
name with tenant entries winning.

`configBasePath` defaults to `config/` relative to the working directory. It
can be overridden via the `ConfigLoader(Path, Executor)` constructor (used by
the CLI and integration tests).

### 3. Built-in Defaults (lowest priority)

Constants defined in source code (e.g. `DataCloudEnvConfig.DEFAULT_*` fields)
serve as last-resort fallbacks when neither environment variables nor YAML
files provide a value. Defaults are compile-time constants — they cannot be
changed without a code change.

---

## Startup Validation

`DataCloudStartupValidator` runs on boot and enforces that the resolved
configuration satisfies all invariants for the selected deployment mode:

- `EMBEDDED` — no external URL required; validates local resource availability.
- `STANDALONE` — `DC_SERVER_URL` must be set.
- `DISTRIBUTED` — `DC_CLUSTER_URLS` must be set with ≥2 URLs.

Validation failures throw `ConfigurationException` and halt startup.

---

## Hot Reload

`ConfigReloadManager` watches the YAML config directory for changes and
asynchronously reloads affected config entries without a service restart.
Polling interval is configurable; reload errors are logged and do not crash
the running service.

---

## Key Classes

| Class | Purpose |
|---|---|
| `ConfigLoader` | Loads YAML (collection / plugin) with env-var interpolation and tenant → default fallback |
| `DataCloudEnvConfig` | Centralised `System.getenv()` wrapper; all env var reads go here |
| `DataCloudStartupValidator` | Validates resolved config against deployment-mode invariants at startup |
| `ConfigReloadManager` | Hot-reload watcher for YAML config files |
| `ConfigRegistry` | Runtime registry for compiled config objects with version tracking |
| `CollectionConfigCompiler` | Compiles `RawCollectionConfig` into validated domain objects |
| `PluginConfigCompiler` | Compiles `RawPluginConfig` into validated domain objects |
| `StorageProfileCompiler` | Resolves and compiles storage backend profiles |

---

## Tests

| Test class | What it covers |
|---|---|
| `ConfigLoaderPrecedenceTest` | Tenant-specific file wins over default; fallback when absent; list deduplication |
| `DataCloudEnvConfigTest` | All variable reads, type coercion, defaults |
| `DataCloudStartupValidatorTest` | Mode-specific invariant enforcement (EMBEDDED / STANDALONE / DISTRIBUTED) |
| `ConfigRegistryTest` | Construction, cache, version tracking, async reload |
| `ConfigValidationTest` | Schema validation for raw collection / index / event spec |
| `StorageProfileCompilerTest` | Backend tier compilation (hot / warm / cold) |
| `CollectionConfigCompilerTest` | Collection domain object compilation |
| `SchemaValidationCoverageTest` | Ensures every schema field is covered by validation |

Run module tests:

```bash
./gradlew :products:data-cloud:platform-config:test
```

---

## Related Modules

- `products/data-cloud/platform-entity` — entity schemas consumed by compiled config.
- `products/data-cloud/platform-api` — API layer that reads config at request time.
- `platform/java/core` — `ConfigurationException` base type.
