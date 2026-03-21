# Data-Cloud Package Restructuring Plan

## Current State Analysis

### Problematic Packages (from audit)
1. **entity/** - 102 files (TOO LARGE)
2. **infrastructure/** - 68 files (TOO LARGE)
3. **application/** - 63 files (TOO LARGE)
4. **client/** - 42 files (NEEDS REVIEW)

## Target Structure

### 1. entity/ Restructuring (102 files → 4 subpackages)

```
entity/
├── domain/                    # Core domain entities
│   ├── Entity.java
│   ├── EntityId.java
│   ├── EntityMetadata.java
│   ├── EntityRelationship.java
│   ├── EntityState.java
│   └── events/               # Domain events
│       ├── EntityCreatedEvent.java
│       ├── EntityUpdatedEvent.java
│       └── EntityDeletedEvent.java
│
├── storage/                  # Storage-related entities
│   ├── StorageBackend.java
│   ├── StorageProfile.java
│   ├── StorageConnector.java
│   ├── StorageLocation.java
│   └── StorageMetadata.java
│
├── lifecycle/                # Entity lifecycle
│   ├── EntityLifecycle.java
│   ├── LifecycleState.java
│   ├── LifecycleTransition.java
│   └── LifecycleEvent.java
│
└── query/                    # Query specifications
    ├── QuerySpec.java
    ├── QueryFilter.java
    ├── QuerySort.java
    ├── QueryPagination.java
    └── QueryResult.java
```

### 2. infrastructure/ Restructuring (68 files → 4 subpackages)

```
infrastructure/
├── persistence/              # Database access
│   ├── jpa/
│   │   ├── JpaEntityRepository.java
│   │   ├── JpaCollectionRepository.java
│   │   └── JpaQueryExecutor.java
│   ├── jdbc/
│   │   ├── JdbcTemplate.java
│   │   └── JdbcQueryBuilder.java
│   └── postgres/
│       ├── PostgresJsonbConnector.java
│       └── PostgresQueryBuilder.java
│
├── cache/                  # Caching layer
│   ├── redis/
│   │   ├── RedisStateAdapter.java
│   │   ├── RedisCollectionCache.java
│   │   └── RedisVectorCache.java
│   ├── caffeine/
│   │   └── CaffeineLocalCache.java
│   └── CacheManager.java
│
├── storage/                # Storage connectors
│   ├── s3/
│   │   ├── BlobStorageConnector.java
│   │   └── S3Presigner.java
│   ├── ceph/
│   │   └── CephObjectStorageConnector.java
│   ├── iceberg/
│   │   └── IcebergTableManager.java
│   └── clickhouse/
│       └── ClickHouseTimeSeriesConnector.java
│
└── search/                 # Search infrastructure
    ├── opensearch/
    │   └── OpenSearchConnector.java
    └── indexing/
        ├── EntityIndexer.java
        └── SearchQueryBuilder.java
```

### 3. application/ Restructuring (63 files → 4 subpackages)

```
application/
├── services/               # Core services
│   ├── EntityService.java
│   ├── CollectionService.java
│   ├── QueryService.java
│   ├── StorageService.java
│   └── MigrationService.java
│
├── workflow/               # Workflow management
│   ├── WorkflowEngine.java
│   ├── WorkflowDefinition.java
│   ├── WorkflowInstance.java
│   ├── WorkflowStep.java
│   └── WorkflowExecutor.java
│
├── security/               # Security services
│   ├── TenantIsolation.java
│   ├── AccessControl.java
│   ├── EncryptionService.java
│   └── AuditService.java
│
└── observability/          # Monitoring & metrics
    ├── MetricsCollector.java
    ├── HealthIndicator.java
    ├── PerformanceMonitor.java
    └── TracingAspect.java
```

### 4. client/ Restructuring (42 files → 3 subpackages)

```
client/
├── http/                   # HTTP client
│   ├── DataCloudHttpClient.java
│   ├── HttpClientConfig.java
│   └── RetryPolicy.java
│
├── grpc/                   # gRPC client (if applicable)
│   └── DataCloudGrpcClient.java
│
└── sdk/                    # High-level SDK
    ├── DataCloudSdk.java
    ├── EntityClient.java
    └── CollectionClient.java
```

## Implementation Steps

### Phase 1: Prepare (Day 1)
1. Create new package structure
2. Update import statements in all files
3. Update build configuration
4. Run tests to ensure nothing breaks

### Phase 2: entity/ Restructuring (Day 2)
1. Move files to new locations
2. Update package declarations
3. Update imports in dependent files
4. Run tests
5. Commit changes

### Phase 3: infrastructure/ Restructuring (Day 3)
1. Move persistence files
2. Move cache files
3. Move storage files
4. Move search files
5. Update all imports
6. Run tests
7. Commit changes

### Phase 4: application/ Restructuring (Day 4)
1. Move service files
2. Move workflow files
3. Move security files
4. Move observability files
5. Update all imports
6. Run tests
7. Commit changes

### Phase 5: client/ Restructuring (Day 5)
1. Move client files
2. Update imports
3. Run tests
4. Commit changes

## Migration Script

```bash
#!/bin/bash

# entity/ migration
mkdir -p entity/domain entity/storage entity/lifecycle entity/query

# Move domain entities
git mv entity/Entity.java entity/domain/
git mv entity/EntityId.java entity/domain/
# ... (repeat for all domain entities)

# Move storage entities
git mv entity/StorageBackend.java entity/storage/
git mv entity/StorageProfile.java entity/storage/
# ... (repeat for all storage entities)

# Update imports in dependent files
find . -name "*.java" -type f -exec sed -i \
  's/import com.ghatana.datacloud.entity.Entity;/import com.ghatana.datacloud.entity.domain.Entity;/g' {} \;

# Run tests
./gradlew test

# Commit
git commit -m "refactor(entity): restructure entity package into subpackages

- Split 102 files into 4 focused subpackages
- domain/: Core domain entities
- storage/: Storage-related entities  
- lifecycle/: Entity lifecycle management
- query/: Query specifications

Improves cohesion and maintainability."
```

## Risk Mitigation

### Risks
1. **Import conflicts** - IDE refactoring tools will help
2. **Circular dependencies** - Check with dependency analysis
3. **Build breaks** - Run CI after each phase
4. **Test failures** - Ensure all tests pass before commit

### Mitigation
1. Use IDE refactoring (IntelliJ "Move" feature)
2. Run dependency analysis before and after
3. Use feature flags for gradual migration
4. Comprehensive test coverage before migration

## Success Criteria

- [ ] All packages have <25 files
- [ ] Cohesion improved (related classes together)
- [ ] No circular dependencies introduced
- [ ] All tests pass
- [ ] Build successful
- [ ] Documentation updated

## Verification

```bash
# Count files per package
find entity -name "*.java" | wc -l
find entity/domain -name "*.java" | wc -l
find entity/storage -name "*.java" | wc -l

# Check for circular dependencies
./gradlew dependencies --configuration compileClasspath | grep -i circular

# Run all tests
./gradlew test

# Build
./gradlew build
```

---

**Created**: March 19, 2026  
**Owner**: Architecture Team
