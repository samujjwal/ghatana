# Data Cloud Platform Module Split Plan

**Status:** Phase 7 (P7-3e) — Extraction complete  
**Date:** 2026-03-21  
**Current:** Aggregate `data-cloud/platform` now acts as a compatibility wrapper with 0 Java source files; all Data Cloud platform code has been extracted into the split modules

---

## Target Module Structure

The monolithic `data-cloud/platform` module is split into 5 bounded-context modules:

```
products/data-cloud/
├── platform-entity/       # Entity & metadata management
├── platform-event/        # Event sourcing & streaming
├── platform-config/       # Configuration, policy, governance
├── platform-analytics/    # Analytics, query engine, reporting
├── platform-launcher/     # API layer, deployment, DI wiring
└── spi/                   # (existing) Plugin SPI contracts
```

### Module Details

| Module | Packages | Est. Files | Dependencies |
|--------|----------|-----------|--------------|
| `platform-entity` | `entity/`, `record/`, `schema/`, `spi/storage/` | ~155 | platform:java:core, domain, database |
| `platform-event` | `event/`, `spi/streaming/` | ~30 | platform:java:core, event-cloud |
| `platform-config` | `config/`, `infrastructure/policy/`, `application/policy/`, `pattern/`, `reflex/` | ~65 | platform:java:core, config |
| `platform-analytics` | `analytics/`, `plugins/analytics/`, `application/query/`, `client/` | ~80 | platform-entity, platform-event |
| `platform-launcher` | `api/`, `grpc/`, `di/`, `deployment/`, `distributed/`, `edge/`, `brain/`, remaining | ~188 | all above modules |

### Dependency Flow

```
platform-launcher → platform-analytics → platform-entity
                  → platform-config    → platform-event
                  → platform-event
                  → platform-entity
```

### Migration Strategy

1. **Create new module directories** with `build.gradle.kts` using correct dependencies
2. **Move packages incrementally** — one bounded context per PR
3. **Keep `platform` as thin launcher** that re-exports all sub-modules during transition
4. **Update `settings.gradle.kts`** with new module includes
5. **Verify compilation** after each package move

### Interim Compatibility

During migration, `platform/build.gradle.kts` aggregates all sub-modules:

```kotlin
dependencies {
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":products:data-cloud:platform-config"))
    api(project(":products:data-cloud:platform-analytics"))
}
```

This ensures downstream consumers continue to compile without changes.

Current direct wrapper consumers in Gradle build files:

- none

### Completed Migration Slices

- `config/DataCloudEnvConfig` moved from `platform` to `platform-config`
- Full `entity` bounded context moved from `platform` to `platform-entity`
- Root entity-domain types (`DataRecord`, `DataRecordInterface`, `EntityRecord`, `RecordType`, `RetentionPolicy`) now live in `platform-entity`
- Full `event` bounded context moved from `platform` to `platform-event`
- Root event-domain type `EventRecord` now lives in `platform-event`
- `platform-event:test` passes and downstream aggregate/platform consumer compile validation remains green
- Full `analytics` bounded context moved from `platform` to `platform-analytics`
- Shared AI capability contracts (`com.ghatana.datacloud.spi.ai`) moved from `platform` to `spi`
- `platform-analytics:test` passes and downstream aggregate/platform consumer compile validation remains green
- Root query/schema contracts (`RecordQuery`, `Collection`, `FieldDefinition`, `EventConfig`) moved from `platform` to `platform-entity`
- Storage SPI contracts moved from `platform` to `spi`: `StoragePlugin`, `StoragePluginRegistry`, `AggregationCapability`, `StreamingCapability`, `TransactionCapability`, `SimilaritySearchCapability`, `AuditLogger`, `EncryptionService`, and `spi/capability/**`
- `platform-entity` no longer depends on `spi`; `DataCloudClient`-bound services (`CcpaDataSubjectRightsService`, `RetentionEnforcerService`, `Soc2ControlFramework`) moved to `platform-launcher`
- `DataCloudCoreModule` now lives in `platform-launcher` together with `api/dto/**` and root `DataCloud`
- Config infrastructure moved from `platform` to `platform-config`: `config/model/**`, compiler/loader/registry/reload classes, health/policy/routing helpers, `ConfigValidationCli`, `DataCloudStartupValidator`, and `di/DataCloudConfigModule`
- Pattern/reflex and governance policy slices moved from `platform` to `platform-config`: `pattern/**`, `reflex/**`, `application/policy/**`, and `infrastructure/policy/**`
- Streaming runtime moved from `platform` to `platform-launcher`: `di/DataCloudStreamingModule`, `infrastructure/state/redis/RedisStateAdapter`, and the Kafka runtime package (`plugins/kafka/**`, including the former `plugins/streaming/**` sources that already declared `com.ghatana.datacloud.plugins.kafka`)
- Deployment/runtime transport moved from `platform` to `platform-launcher`: `deployment/**`, `distributed/**`, `grpc/**`, and `edge/**`
- Runtime observability and scaling moved from `platform` to `platform-launcher`: `observability/DataCloudMetrics`, `observability/DataCloudDashboard`, `scaling/AutoScaler`, and `scaling/PluginAutoScaler`
- Cognitive runtime moved from `platform` to `platform-launcher`: `brain/**`, `workspace/**`, `di/DataCloudBrainModule`, the `attention/**` and `memory/**` implementations that were still physically parked under `client/`, plus the supporting contracts `client/ContextDocument`, `client/ContextGateway`, `client/LearningSignal`, `client/LearningSignalStore`, and `client/feedback/FeedbackEvent`
- Base record contracts moved from `platform` to `platform-entity`: `record/Record` and `record/DataRecord` now live with the entity-facing shared contracts, which unblocked launcher-owned client/aspect code without moving the full `record/**` package
- Shared CRUD storage contract moved into `spi` as `DataStorageOperations`; `DataStoragePlugin` in aggregate `platform` now extends that contract so launcher-owned embedded runtime can depend on CRUD operations without depending on the aggregate-only plugin type
- Final client runtime extraction completed from `platform` to `platform-launcher`: `AnomalyDetectionAspect`, `ClassificationAspect`, `EmbeddingAspect`, `ValidationAspect`, `EmbeddedDataCloudClient`, and `DataCloudClientFactory` now live in `platform-launcher`; aggregate `platform` retains only `DataStoragePlugin` as the remaining client-path compatibility blocker because moving it to `spi` still creates a confirmed `spi -> platform:java:plugin -> spi` cycle
- Embedded runtime package moved from `platform` to `platform-launcher`: `embedded/EmbeddableDataCloud`, `DefaultEmbeddableDataCloud`, `RecordCodec`, `RocksDBStore`, `SQLiteStore`, `H2Store`, and `package-info` now live in `platform-launcher`, with the storage backend dependencies owned there as well
- Remaining record-domain traits and immutable implementations moved from `platform` to `platform-entity`: `RecordId`, `Versioned`, `Schematized`, `Auditable`, `Timestamped`, `AIEnhanced`, `HasMetadata`, `MutableRecord`, `MetadataRecord`, `ImmutableRecord`, and `record/impl/**` now live in `platform-entity`; aggregate `platform` retains only the JPA bridge adapters under `record/adapter/**`
- Graph operations moved from `platform` to `platform-entity`: `graph/GraphOperations` and `graph/InMemoryGraphOperations` now live with the graph record types they operate on
- Event schema types moved from `platform` to `platform-entity`: `schema/EventSchema`, `EventSchemaRegistry`, `SchemaCompatibilityChecker`, `SchemaFormat`, and `CompatibilityMode` now live in `platform-entity`
- Shared ingest backpressure contract moved from `platform` to `spi` as `BackpressurePort`; aggregate `platform` infrastructure now depends on the SPI-owned port instead of an aggregate-local interface
- Backpressure runtime utility moved from `platform` to `platform-launcher`: `backpressure/BackpressureManager`, `BackpressureException`, and `BackpressureManagerTest` now live in `platform-launcher`, which now owns the backpressure runtime/test slice while aggregate `platform` retains only the watermark-based ingest infrastructure
- Warm-tier runtime moved from `platform` to `platform-launcher`: `storage/WarmTierEventLogStore`, `workflow/WorkflowRunRepository`, and `WarmTierEventLogStoreTest` now live in `platform-launcher`; aggregate `platform` still provides `di/DataCloudStorageModule` temporarily, but it now consumes launcher-owned runtime types through the transitional re-export layer
- Additional launcher-owned adapter/runtime slices moved from `platform` to `platform-launcher`: `api/controller/AutonomyApiController`, `GlobalWorkspaceController`, `MemoryController`, and `PatternController` now live in `platform-launcher`, together with the full `migration/**` package, `migration/DataMigrationServiceTest`, and `infrastructure/query/QueryTelemetryService` plus `QueryAdvisorService`
- Additional launcher-owned audit and webhook slices moved from `platform` to `platform-launcher`: `audit/DataCloudAuditService`, `infrastructure/audit/**`, `application/webhook/**`, and `api/controller/WebhookController` now live in `platform-launcher`; `platform-launcher` now owns the audit service dependency on `platform:java:audit` while aggregate `platform` consumes the moved audit logger through the transitional launcher re-export
- Additional launcher-owned governance slice moved from `platform` to `platform-launcher`: the full `infrastructure/governance/**` subtree now lives in `platform-launcher`, including role-assignment services and DTOs, health contracts, governance config holders, and the in-memory SLO implementation
- Additional launcher-owned storage slice moved from `platform` to `platform-launcher`: the full `infrastructure/storage/**` connector/config/mapper package and the `application/storage/**` routing/admin services now live in `platform-launcher`, together with their storage connector and routing tests; `platform-launcher` now owns the required OpenSearch, ClickHouse, AWS S3, ClickHouse Testcontainers, and Caffeine dependencies for that storage seam
- Additional launcher-owned collection-facing application/API slice moved from `platform` to `platform-launcher`: `CollectionService`, `EntityService`, `EntityValidationService`, `ValidationService`, `SchemaDiffService`, `EntitySuggestionService`, `api/controller/CollectionController`, and `api/graphql/GraphQLMutations` now live in `platform-launcher`, together with `GraphQLMutationsTest`; revalidated the affected compile chain and reduced aggregate `platform` to 143 Java files
- Additional launcher-owned query/search slice moved from `platform` to `platform-launcher`: `QuerySpec`, `DynamicQueryBuilder`, `application/query/**`, `application/nlq/**`, `application/search/**`, `application/ai/QueryRecommender`, and `NLQServiceTest` now live in `platform-launcher`; revalidated the affected compile chain again and reduced aggregate `platform` to 130 Java files with the remaining application slice down to 32 files
- Additional launcher-owned plugin slice moved from `platform` to `platform-launcher`: the full residual `plugins/**` subtree now lives in `platform-launcher`, including the Trino connector classes, enterprise compliance/lineage/documentation/recovery helpers, Iceberg/Redis/S3 archive storage plugins, knowledge graph plugin package, validation processor, and vector plugin package; `platform-launcher` now owns the remaining plugin-specific dependencies for AWS Glacier, Disruptor, Iceberg, Hadoop, Parquet, Trino, and JGraphT, and the affected compile chain remained green with aggregate `platform` reduced to 79 Java files
- Additional launcher-owned residual application slice moved from `platform` to `platform-launcher`: the remaining `application/**` subtree now lives in `platform-launcher`, including `PaginationHelper`, `WorkflowService`, the agent/governance/monitoring/observability/quality/realtime/security/version/workflow packages, and the content-generation DTOs; the affected compile chain remained green and eliminated the aggregate application layer entirely
- Additional launcher-owned residual infrastructure slice moved from `platform` to `platform-launcher`: the remaining portable `infrastructure/**` subtree now lives in `platform-launcher`, including backpressure/cache/config/encryption/event/health/import-export/persistence/quality plus the `state/h2` and `state/memory` adapters; `platform-launcher` now owns the remaining Lettuce dependency for cache adapters, and the affected compile chain remained green with aggregate `platform` reduced to 17 Java files
- Final movable singleton cleanup completed: `DocumentRecord`, `GraphRecord`, and `TimeSeriesRecord` now live in `platform-entity`; `ai/AIModelManager` and `feature/DataCloudFeature` now live in `platform-launcher`; and the duplicate aggregate copy of `client/EmbeddedDataCloudClient` was removed, leaving aggregate `platform` with only 11 Java files: `ConfigAwareCollectionService`, `DataCloudStorageModule`, `spi/DataStoragePlugin`, `record/adapter/**`, and package-info compatibility files
- Final non-cycle compatibility cleanup completed: `ConfigAwareCollectionService` now lives in `platform-config`; `DataCloudStorageModule`, `di/package-info`, the `record/adapter/**` bridge package, and the residual `api`, `brain`, and `workspace` package-info files now live in `platform-launcher`; `RecordAdapterTest` and `DataCloudDiModulesTest` moved with their owned runtime packages; the affected compile/test chain remained green, leaving aggregate `platform` with only `spi/DataStoragePlugin`
- Targeted compile validation remains green for `platform-entity`, `spi`, `platform-launcher`, aggregate `platform`, downstream `launcher`, and `feature-store-ingest`
- Final cycle break completed: `platform:java:plugin` no longer depends on `products:data-cloud:spi`; the `EventCloudPluginAdapter` bridge moved to `platform-launcher`; `products:data-cloud:spi` now owns the dependency on `platform:java:plugin`; and `spi/DataStoragePlugin` now lives in `products:data-cloud:spi`, reducing aggregate `platform` to zero Java source files while the full affected compile chain remains green
- Compatibility-wrapper ownership completed for operational assets: Flyway migrations, the shared Data Cloud test fixture, JMH benchmarks, and the remaining wrapper-owned test suite now live under `products:data-cloud:platform-launcher`, while `products/data-cloud/platform/build.gradle.kts` was reduced to a thin compatibility `java-library` before final retirement
- Post-extraction downstream validation completed for direct wrapper consumers: `:products:data-cloud:launcher:test` remained green after the wrapper dependency reduction (510 passed, 0 failed), while broader direct-consumer compile sweeps were blocked by an unrelated pre-existing `:platform:java:kernel:compileJava` failure (`KernelContract.ContractFamily` symbol missing); the next cleanup target is migrating the remaining direct wrapper consumers off `products:data-cloud:platform`
- Direct wrapper-consumer migration completed for AEP and YAPPC: `products:aep:aep-event-cloud` now depends only on `products:data-cloud:spi`; `products:aep:server` now depends on `products:data-cloud:spi` plus `products:data-cloud:platform-launcher`; `products:yappc:core:knowledge-graph` now depends on `products:data-cloud:platform-launcher`; `products:yappc:core:yappc-services` now depends on `products:data-cloud:spi`; and the dead `products:yappc:services` test-only wrapper edge was removed. Targeted validation confirmed the new classpaths, while YAPPC compile verification remained blocked by the same unrelated pre-existing `:platform:java:kernel:compileJava` failure
- Remaining internal wrapper-consumer migration completed inside Data Cloud: `products:data-cloud:feature-store-ingest`, `products:data-cloud:launcher`, and `products:data-cloud:agent-registry` now depend on `products:data-cloud:platform-launcher` instead of the aggregate wrapper, leaving `products:data-cloud:platform` with zero direct Gradle consumers
- Wrapper retirement completed: removed `:products:data-cloud:platform` from root and YAPPC composite settings, deleted `products/data-cloud/platform/`, rewired live CI/workflow tasks and owner docs away from the deleted wrapper, and kept the post-removal validation target focused on `platform-launcher` plus root-owned SBOM generation because the deleted wrapper no longer participates in the build graph
- Root-owned SBOM validation completed after wrapper retirement: upgraded the root CycloneDX Gradle plugin to `3.2.2`, migrated the root build to the CycloneDX 3.x direct/aggregate task model, and separated root direct-task output (`build/sbom/direct-bom.json`) from aggregate output (`build/sbom/bom.json`) so the aggregate artifact is not overwritten; `./gradlew --no-configuration-cache --no-parallel cyclonedxBom --console=plain` now finishes `BUILD SUCCESSFUL` and produces a populated aggregate SBOM at `build/sbom/bom.json`
- Launcher-owned OWASP validation no longer crashes after wrapper retirement: the `HttpClientBuilder.setProxySelector(...)` failure came from `buildSrc` exporting Saxon-HE's transitive `httpclient5:5.1.3` into the parent plugin classloader ahead of OWASP's own `httpclient5:5.5.1`; `buildSrc/build.gradle.kts` now excludes Saxon's stale Apache HttpClient artifacts and adds compatible `httpclient5:5.5.1` / `httpcore5:5.3.6`, allowing `:products:data-cloud:platform-launcher:dependencyCheckAnalyze` to enter its normal NVD update/analyze flow instead of failing during task initialization

---

## Phase 7 Action Items

- [x] Package analysis complete (518 files, 32 packages)
- [x] Bounded context boundaries identified (5 modules)
- [x] Dependency graph documented
- [x] Migration strategy defined
- [x] Create stub `build.gradle.kts` for each new module
- [x] Move first bounded-context slice as proof of migration (`config/DataCloudEnvConfig` -> `platform-config`)
- [x] Move first full bounded context (`platform-entity`) as proof of migration
- [x] Complete remaining context moves (incremental PRs)

## Next Boundary Targets

- Re-run targeted validation for the new `platform-launcher` ownership of tests, Flyway migrations, and JMH benchmarks after wrapper deletion to keep CI/task ownership green
- Sweep remaining non-archive docs over time to convert historical `products:data-cloud:platform` references into either `platform-launcher` or explicitly historical notes
- Keep using targeted downstream validation because broad aggregate sweeps are currently polluted by the unrelated `:platform:java:kernel:compileJava` failure (`KernelContract.ContractFamily` missing)
