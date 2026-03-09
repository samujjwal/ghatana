# Virtual-Org Compilation Issues

## Status
The `virtual-org` module is currently excluded from the build due to ~30 compilation errors requiring plugin interface implementation updates.

## Progress Summary

### ✅ Phase 1 Complete - Dependencies Fixed
1. **Domain Models** - Created missing domain value objects:
   - `com.ghatana.virtualorg.core.domain.TenantId`
   - `com.ghatana.virtualorg.core.domain.OrganizationId`

2. **AEP Integration** - Added missing dependencies to `build.gradle`:
   - `implementation project(':products:agentic-event-processor:libs:java:domain-models')`
   - `implementation project(':libs:domain-models')`
   - `implementation project(':libs:operator-catalog')`

3. **Error Reduction**: ~60 errors → ~30 errors

### Remaining Issues (~30 errors)

#### 1. Missing AEP Domain Classes
**Package**: `com.ghatana.aep.domain.agent`

Files affected:
- `AgentRegistryAdapter.java` - References `com.ghatana.aep.domain.agent` classes
- Multiple integration files in `integration/aep/`

**Required Classes**:
- `AgentMetadata`
- `AgentCapability`
- `AgentStatus`
- Other AEP agent domain types

**Resolution**: These classes need to be either:
- Imported from AEP if they exist elsewhere
- Created in the virtual-org module
- Replaced with virtual-org-specific types

#### 2. Missing Operator Catalog API
**Package**: `com.ghatana.core.operator.catalog`

Files affected:
- `VirtualOrgOperatorAdapter.java:6` - Cannot find package

**Error**:
```
error: package com.ghatana.core.operator.catalog does not exist
```

**Resolution**: 
- Check if operator catalog exists in `libs/operator-catalog`
- Update import paths if package was moved
- Create adapter interfaces if needed

#### 3. AgentProvider API Mismatches
**Files**: Plugin implementations in `plugins/healthcare/`, `plugins/finance/`

**Errors**:
```
error: <anonymous> is not abstract and does not override abstract method getCapabilities() in AgentProvider
error: method does not override or implement a method from a supertype
```

**Current AgentProvider Interface** (`virtualorg/spi/AgentProvider.java`):
- Has methods like `getId()`, `getDefaultConfiguration()`
- Plugin implementations have outdated method signatures

**Resolution**:
- Review `AgentProvider` interface in `virtualorg/spi/`
- Update all plugin implementations to match current interface
- Add missing abstract methods to implementations

#### 4. Event Type Incompatibilities
**Files**: 
- `EventTrigger.java:186-188`
- `AgentMemoryStateStore.java:386`
- `MemoryEventPublisher.java` (multiple lines)

**Errors**:
```
error: incompatible types: Class<TriggerEvent> cannot be converted to EventFilter
error: incompatible types: MemoryEvent cannot be converted to Event
error: incompatible types: MemoryLifecycleEvent cannot be converted to Event
```

**Issue**: Virtual-org has custom event types (`MemoryEvent`, `MemoryLifecycleEvent`, `TriggerEvent`) that don't match the core `Event` type from the platform.

**Resolution**:
- Create adapter/wrapper classes to convert between event types
- Update event publishers to use correct event types
- Consider if custom events should extend core `Event` class

#### 5. Record Accessor Method Issue
**File**: `AgentKnowledgeConfig.java:247`

**Error**:
```
error: invalid accessor method in record QueryExpansionConfig
```

**Resolution**: Review record definition and ensure accessor methods follow Java record conventions

#### 6. Method Signature Conflicts
**File**: `VirtualOrgOperatorAdapter.java:285`

**Error**:
```
error: name clash: filterByOrgPermissions(String,List<OperatorId>) and 
filterByOrgPermissions(String,List<UnifiedOperator>) have the same erasure
```

**Resolution**: Rename one of the methods or use different parameter types to avoid erasure conflicts

## Workaround
Module is currently excluded via:
```bash
./build-clean.sh 
  -x :products:virtual-org:compileJava \
  -x :products:virtual-org:compileTestJava
```

## Recommended Fix Strategy

### Phase 1: AEP Integration (High Priority)
1. Locate or create missing AEP domain classes
2. Fix operator catalog imports
3. Update integration adapters

### Phase 2: Plugin System (Medium Priority)
1. Review and update `AgentProvider` interface
2. Fix all plugin implementations to match interface
3. Add missing abstract method implementations

### Phase 3: Event System (Medium Priority)
1. Create event type adapters/converters
2. Update event publishers
3. Fix event filter incompatibilities

### Phase 4: Code Quality (Low Priority)
1. Fix record accessor issues
2. Resolve method signature conflicts
3. Remove unused imports

## Estimated Effort
- **Phase 1**: 4-6 hours (requires understanding AEP architecture)
- **Phase 2**: 2-3 hours (straightforward interface implementation)
- **Phase 3**: 3-4 hours (event type system design)
- **Phase 4**: 1 hour (cleanup)

**Total**: ~10-14 hours of focused development work

## Dependencies
- Understanding of AEP (Agentic Event Processor) architecture
- Access to AEP domain model documentation
- Knowledge of the platform's event system design

## Files to Review
- `products/agentic-event-processor/libs/java/domain-models/` - For AEP domain classes
- `libs/operator-catalog/` - For operator catalog API
- `libs/domain-models/` - For core Event types
- `products/virtual-org/src/main/java/com/ghatana/virtualorg/spi/` - For SPI interfaces
