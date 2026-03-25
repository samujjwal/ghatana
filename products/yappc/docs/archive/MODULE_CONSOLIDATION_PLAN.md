# YAPPC Module Consolidation Plan - Phase 2

## Current State: 18 Core Modules → Target: 6 Consolidated Modules

### 📊 Current Module Structure (18 modules)

```
products/yappc/core/
├── agents/                    # Agent implementations
│   ├── architecture-specialists/
│   ├── code-specialists/
│   ├── data-specialists/
│   ├── security-specialists/
│   └── testing-specialists/
├── domain/                    # Domain models
├── infrastructure/            # Infrastructure components
│   ├── storage/
│   ├── messaging/
│   └── monitoring/
├── services/                  # Business services
│   ├── orchestration/
│   ├── validation/
│   └── transformation/
├── api/                       # API layer
│   ├── rest/
│   └── graphql/
└── shared/                    # Shared utilities
    ├── common/
    └── utils/
```

### 🎯 Target Structure (6 modules)

```
products/yappc/core/
├── yappc-agents/              # Consolidated all agent modules (5→1)
├── yappc-domain/              # Domain models (1→1)
├── yappc-infrastructure/      # Consolidated infrastructure (3→1)
├── yappc-services/            # Consolidated services (3→1)
├── yappc-api/                 # Consolidated API (2→1)
└── yappc-shared/              # Consolidated shared (2→1)
```

## 🔄 Consolidation Strategy

### 1. yappc-agents (5 → 1)
**Source modules:**
- `agents/architecture-specialists/`
- `agents/code-specialists/`
- `agents/data-specialists/`
- `agents/security-specialists/`
- `agents/testing-specialists/`

**Consolidation approach:**
- Move all agent implementations to `yappc-agents/src/main/java/com/ghatana/yappc/agents/`
- Organize by specialty: `agents/architecture/`, `agents/code/`, `agents/data/`, etc.
- Shared agent utilities in `agents/common/`
- YAML configs in `yappc-agents/src/main/resources/agents/`
- Tests in `yappc-agents/src/test/java/`

**Benefits:**
- Single module for all agent logic
- Easier dependency management
- Unified testing strategy
- Simplified build configuration

### 2. yappc-domain (1 → 1)
**Source module:** `domain/`

**Approach:** Keep as-is, just rename for consistency
- `yappc-domain/src/main/java/com/ghatana/yappc/domain/`

### 3. yappc-infrastructure (3 → 1)
**Source modules:**
- `infrastructure/storage/`
- `infrastructure/messaging/`
- `infrastructure/monitoring/`

**Consolidation approach:**
- `yappc-infrastructure/src/main/java/com/ghatana/yappc/infrastructure/`
- Subpackages: `storage/`, `messaging/`, `monitoring/`
- Shared infrastructure utilities in `common/`

### 4. yappc-services (3 → 1)
**Source modules:**
- `services/orchestration/`
- `services/validation/`
- `services/transformation/`

**Consolidation approach:**
- `yappc-services/src/main/java/com/ghatana/yappc/services/`
- Subpackages: `orchestration/`, `validation/`, `transformation/`

### 5. yappc-api (2 → 1)
**Source modules:**
- `api/rest/`
- `api/graphql/`

**Consolidation approach:**
- `yappc-api/src/main/java/com/ghatana/yappc/api/`
- Subpackages: `rest/`, `graphql/`
- Shared API utilities in `common/`

### 6. yappc-shared (2 → 1)
**Source modules:**
- `shared/common/`
- `shared/utils/`

**Consolidation approach:**
- `yappc-shared/src/main/java/com/ghatana/yappc/shared/`
- Merge common and utils into unified shared utilities

## 📋 Implementation Steps

### Step 1: Create new module structure
1. Create 6 new Gradle modules with proper build files
2. Set up dependencies between consolidated modules
3. Update root `settings.gradle.kts`

### Step 2: Migrate source code
1. Move source files to new locations
2. Update package declarations
3. Fix import statements
4. Update resource locations

### Step 3: Update build configuration
1. Consolidate dependencies in new build files
2. Remove old module build files
3. Update inter-module dependencies
4. Ensure all tests still pass

### Step 4: Update documentation
1. Update `CORE_ARCHITECTURE.md`
2. Update build documentation
3. Update developer guides

## 🎯 Expected Benefits

### Build Performance
- **67% reduction** in module count (18 → 6)
- Faster dependency resolution
- Reduced build overhead
- Simpler CI/CD pipelines

### Developer Experience
- Clearer module boundaries
- Easier navigation
- Reduced cognitive load
- Simplified dependency management

### Maintenance
- Fewer build files to maintain
- Consolidated test suites
- Unified version management
- Easier refactoring

## ⚠️ Migration Considerations

### Dependency Management
- Carefully map existing dependencies
- Resolve circular dependencies
- Update version catalogs
- Ensure platform library usage

### Testing Strategy
- Consolidate test suites
- Update test configurations
- Ensure test coverage is maintained
- Fix any broken tests after migration

### Backward Compatibility
- Maintain public API stability
- Update integration points
- Ensure no breaking changes for consumers
- Update documentation

## 📊 Success Metrics

- ✅ Build time reduction > 30%
- ✅ Module count reduction from 18 to 6
- ✅ All tests passing after consolidation
- ✅ No breaking changes to public APIs
- ✅ Developer satisfaction with new structure
