# Complete Legacy Platform Cleanup Plan

## Current State Analysis

The legacy `platform/` module still contains **376 Java files** across 113 packages. These need to be systematically migrated or archived to achieve a completely clean codebase.

## Remaining Content Categories

### 1. Core Engine Components (68 files)
**Source**: `com.ghatana.core.*`, `com.ghatana.engine.*`
**Target**: `platform-core` (extend existing)
- `core/operator` (17 files) - Core operator implementations
- `core/pipeline` (11 files) - Pipeline execution logic
- `engine/model` (9 files) - Engine data models
- `core/eventcloud` (6 files) - Event cloud integration
- `core/infra` (5 files) - Infrastructure components
- `core/adapter` (4 files) - Core adapters
- `engine/executor` (4 files) - Execution engine
- `core/monitoring` (3 files) - Monitoring components
- `core/validation` (3 files) - Validation logic
- `core/exception` (2 files) - Exception handling

### 2. Pattern Learning & AI (26 files)
**Source**: `com.ghatana.pattern.learning.*`, `com.ghatana.ai.*`
**Target**: `platform-analytics` (extend existing)
- `pattern/learning` (18 files) - Pattern learning algorithms
- `ai/anomaly` (8 files) - Anomaly detection

### 3. State Store & Storage (20 files)
**Source**: `com.ghatana.statestore.*`
**Target**: `platform-core` (infrastructure)
- `statestore/checkpoint` (13 files) - Checkpoint management
- `statestore/core` (3 files) - Core state store
- `statestore/redis` (2 files) - Redis implementation
- `statestore/hybrid` (1 file) - Hybrid storage
- `statestore/factory` (1 file) - Factory patterns

### 4. Pipeline Registry (58 files)
**Source**: `com.ghatana.pipeline.registry.*`
**Target**: `platform-registry` (extend existing)
- `registry/model` (10 files) - Registry models
- `registry/service` (7 files) - Registry services
- `registry/web` (7 files) - Web interfaces
- `registry/config` (7 files) - Configuration
- `registry/connector` (5 files) - Connector registry
- `registry/validation` (5 files) - Validation
- `registry/audit` (6 files) - Audit logging
- `registry/grpc` (4 files) - gRPC interfaces
- `registry/http` (4 files) - HTTP interfaces
- `registry/publisher` (3 files) - Event publishing

### 5. Event Processing & Query (21 files)
**Source**: `com.ghatana.event.*`, `com.ghatana.eventcore.*`, `com.ghatana.eventprocessing.*`
**Target**: `platform-core` (event handling)
- `event/query` (10 files) - Event query processing
- `eventcloud/partition` (6 files) - Partition management
- `eventprocessing/observability` (7 files) - Event observability

### 6. Data Exploration & API (16 files)
**Source**: `com.ghatana.dataexploration.*`, `com.ghatana.api.*`
**Target**: `platform-api` (extend existing)
- `dataexploration/model` (7 files) - Data models
- `api/model` (7 files) - API models
- `api/codegen` (6 files) - Code generation
- `dataexploration/service` (3 files) - Exploration services

### 7. Operators & Built-ins (9 files)
**Source**: `com.ghatana.operator.builtin.*`
**Target**: `platform-core` (operators)
- `operator/builtin` (9 files) - Built-in operators

### 8. Service Management (8 files)
**Source**: `com.ghatana.servicemanager.*`
**Target**: `platform-core` (services)
- `servicemanager/service` (4 files) - Service management
- `servicemanager/process` (3 files) - Process management
- `servicemanager/config` (1 file) - Configuration

### 9. Validation (17 files)
**Source**: `com.ghatana.validation.*`
**Target**: `platform-security` (extend existing)
- `validation/ai/anomaly` (4 files) - AI validation
- `validation/ai/detectors` (3 files) - Anomaly detectors
- `validation/ai/model` (2 files) - Validation models
- `validation/core` (8 files) - Core validation

### 10. Supporting Components (143 files)
**Source**: Various packages (`acceptance`, `audit`, `alerting`, `catalog`, `config`, `contracts`, `evaluation`, `ingress`, `orchestrator`, `recommendation`, `stream`, `validation`)
**Target**: Archive or migrate based on relevance

## Migration Strategy

### Phase 1: Core Infrastructure Migration
1. **Extend platform-core** with:
   - Core engine components (68 files)
   - State store implementations (20 files)
   - Event processing (21 files)
   - Built-in operators (9 files)
   - Service management (8 files)

### Phase 2: Analytics & Pattern Learning
1. **Extend platform-analytics** with:
   - Pattern learning algorithms (18 files)
   - AI anomaly detection (8 files)
   - Validation AI components (9 files)

### Phase 3: Registry & API Extensions
1. **Extend platform-registry** with:
   - Advanced registry features (58 files)
2. **Extend platform-api** with:
   - Data exploration (7 files)
   - API models and codegen (13 files)

### Phase 4: Security & Validation
1. **Extend platform-security** with:
   - Advanced validation (17 files)

### Phase 5: Archive Non-Essential Code
1. **Archive or delete** supporting components (143 files):
   - Acceptance tests
   - Audit tools
   - Alerting systems
   - Catalog implementations
   - Evaluation tools
   - Ingress controllers
   - Recommendation engines
   - Stream processing

## Implementation Plan

### Step 1: Analyze Dependencies
```bash
# Check inter-package dependencies
./gradlew :products:aep:platform:dependencies --configuration compileClasspath

# Identify circular dependencies
./gradlew :products:aep:platform:compileJava --dry-run
```

### Step 2: Create Migration Scripts
```bash
# Script to move packages with dependency updates
for pkg in core engine statestore event operator servicemanager; do
  mv /Users/samujjwal/Development/ghatana/products/aep/platform/src/main/java/com/ghatana/$pkg \
     /Users/samujjwal/Development/ghatana/products/aep/platform-core/src/main/java/com/ghatana/$pkg
done
```

### Step 3: Update Build Files
- Update `platform-core/build.gradle.kts` with new dependencies
- Update `platform-analytics/build.gradle.kts` with AI/ML libraries
- Update `platform-registry/build.gradle.kts` with web/gRPC dependencies
- Update `platform-api.build.gradle.kts` with codegen dependencies

### Step 4: Fix Import Statements
- Run automated refactoring to update package imports
- Fix circular dependencies
- Update test files

### Step 5: Validate Build
```bash
# Build each module individually
./gradlew :products:aep:platform-core:build
./gradlew :products:aep:platform-analytics:build
./gradlew :products:aep:platform-registry:build
./gradlew :products:aep:platform-api:build
./gradlew :products:aep:platform-security:build

# Build launcher
./gradlew :products:aep:launcher:build
```

### Step 6: Archive Non-Essential Code
```bash
# Create archive directory
mkdir -p /Users/samujjwal/Development/ghatana/products/aep/platform-archived

# Move non-essential packages
for pkg in acceptance audit alerting catalog evaluation ingress orchestrator recommendation stream; do
  mv /Users/samujjwal/Development/ghatana/products/aep/platform/src/main/java/com/ghatana/$pkg \
     /Users/samujjwal/Development/ghatana/products/aep/platform-archived/$pkg
done
```

### Step 7: Final Cleanup
```bash
# Remove empty directories
find /Users/samujjwal/Development/ghatana/products/aep/platform/src/main/java -type d -empty -delete

# Verify clean state
find /Users/samujjwal/Development/ghatana/products/aep/platform/src/main/java -name "*.java" | wc -l
# Expected: 0 files
```

## Success Criteria

1. **Zero Java files** remain in `platform/src/main/java`
2. **All 376 files** either migrated or archived
3. **Build passes** for all platform modules and launcher
4. **No circular dependencies** between modules
5. **Documentation updated** with final module structure
6. **Tests pass** for migrated functionality

## Risk Mitigation

1. **Backup before migration**: Create full backup of platform/ directory
2. **Incremental migration**: Migrate one package at a time
3. **Dependency validation**: Check imports after each migration
4. **Build verification**: Run full build after each phase
5. **Test coverage**: Ensure migrated code has adequate tests

## Timeline

- **Phase 1**: 2-3 hours (Core infrastructure)
- **Phase 2**: 1-2 hours (Analytics & AI)
- **Phase 3**: 2-3 hours (Registry & API)
- **Phase 4**: 1 hour (Security & validation)
- **Phase 5**: 1-2 hours (Archive & cleanup)
- **Total**: 7-11 hours

## Final State

After completion:
- **7 platform modules** with focused responsibilities
- **Zero legacy code** in platform/ directory
- **Clean dependency graph** between modules
- **Maintainable architecture** for future development
- **Complete documentation** of module structure
