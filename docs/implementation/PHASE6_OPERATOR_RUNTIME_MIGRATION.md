# Phase 6: EventOperatorCapability Runtime Universal Migration

## Current State

The operator runtime has comprehensive contracts and implementations:

### Existing Components

- ✅ **EventOperator Contract** - Base event operator interface
- ✅ **EventOperatorCapability Contract** - Event processing as an agent capability
- ✅ **Agent Capability Implementations**:
  - `AgentActionOperator` - Action execution
  - `AgentPredicateOperator` - Predicate evaluation
  - `AgentEnrichmentOperator` - Data enrichment
  - `AgentExplanationOperator` - Explanation generation
  - `AgentExtractOperator` - Data extraction
  - `AgentReflectionOperator` - Reflection operations
  - `AbstractAgentInferenceOperator` - Base inference operator
- ✅ **Operator Catalog**:
  - `UnifiedOperatorCatalog` - Unified operator catalog
  - `OperatorCatalogEntry` - Catalog entry
  - `OperatorCatalogQuery` - Catalog query
- ✅ **Operator Registry**:
  - `OperatorRegistry` - Operator registration
  - `AgentCapabilityExecutionFactory` - Agent capability execution factory
- ✅ **Operator Runtime Context**:
  - `OperatorRuntimeContext` - Runtime context
  - `EventOperatorResult` - Operator result
- ✅ **Operator Tests**:
  - `EventOperatorCapabilityArchitectureContractTest`
  - `AgentActionOperatorTest`
  - `AgentPredicateOperatorTest`
  - `AgentInferenceOperatorsTest`
  - `AgentRuntimeGoldenSetEvaluationTest`
  - `RegistryAndFactoryTest`

## Target State

According to the implementation tracker, the next step is:
**Making every event-processing agent use in the Action Plane go through `EventOperatorCapability`, not direct callbacks, detector-specific agents, ad hoc dispatchers, or service-level shortcuts.**

## Implementation Tasks

### 1. Audit Agent Usage Patterns

**Status**: Pending

**Required**:

- Identify all direct agent invocations
- Identify detector-specific agent implementations
- Identify ad hoc dispatchers
- Identify service-level shortcuts
- Create migration inventory

**Implementation**:

- Search for direct agent calls
- Search for detector-specific patterns
- Search for ad hoc dispatchers
- Document findings in migration inventory

### 2. Create EventOperatorCapability Adapter Layer

**Status**: Pending

**Required**:

- Create adapter for direct agent calls
- Create adapter for detector-specific agents
- Create adapter for ad hoc dispatchers
- Ensure backward compatibility during migration

**Implementation**:

- Create `AgentEventOperatorCapabilityAdapter` service
- Add adapter tests
- Add migration path documentation
- Ensure adapter invokes an agent capability through the event-operator execution contract

### 3. Migrate Direct Agent Calls

**Status**: Pending

**Required**:

- Replace direct agent calls with `EventOperatorCapability` invocations
- Update call sites to use operator catalog
- Ensure operator context is passed
- Add migration tests

**Implementation**:

- Identify call sites from audit
- Replace with `EventOperatorCapability` calls
- Add operator context
- Add regression tests

### 4. Migrate Detector-Specific Agents

**Status**: Pending

**Required**:

- Convert detector-specific agents to `EventOperatorCapability` implementations
- Register in operator catalog
- Update detector logic to use operators
- Add migration tests

**Implementation**:

- Identify detector-specific agents
- Create `EventOperatorCapability` implementations
- Register in catalog
- Update detector logic

### 5. Migrate Ad Hoc Dispatchers

**Status**: Pending

**Required**:

- Replace ad hoc dispatchers with operator catalog
- Use UnifiedOperatorCatalog for dispatch
- Ensure operator selection is consistent
- Add migration tests

**Implementation**:

- Identify ad hoc dispatchers
- Replace with catalog-based dispatch
- Ensure operator selection logic
- Add regression tests

### 6. Migrate Service-Level Shortcuts

**Status**: Pending

**Required**:

- Replace service-level shortcuts with operator calls
- Ensure service layer uses operators
- Add operator context to service calls
- Add migration tests

**Implementation**:

- Identify service-level shortcuts
- Replace with operator calls
- Add operator context
- Add regression tests

### 7. Validate Universal Migration

**Status**: Pending

**Required**:

- Verify all event-processing agent uses go through `EventOperatorCapability`
- Verify no direct agent calls remain
- Verify no detector-specific agents remain
- Verify no ad hoc dispatchers remain
- Verify no service-level shortcuts remain

**Implementation**:

- Create validation script
- Add CI check for direct agent calls
- Add CI check for detector-specific patterns
- Add CI check for ad hoc dispatchers
- Add CI check for service-level shortcuts

## Exit Criteria

- [ ] All event-processing agent uses go through `EventOperatorCapability`
- [ ] No direct agent calls remain
- [ ] No detector-specific agents remain
- [ ] No ad hoc dispatchers remain
- [ ] No service-level shortcuts remain
- [ ] CI checks enforce operator usage

## Dependencies

- Phase 5: PatternSpec production path (completed)
- Phase 7: GovernedAgentDispatcher decomposition (pending)

## Related Files

- `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/operator/contract/EventOperator.java`
- `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/agent/capability/EventOperatorCapability.java`
- `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/core/operator/catalog/UnifiedOperatorCatalog.java`
- `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/registry/AgentCapabilityExecutionFactory.java`

## Notes

The operator runtime has excellent contracts and implementations. The main task for Phase 6 is:

1. Audit all agent usage patterns in the Action Plane
2. Create adapter layer for backward compatibility
3. Migrate all event-processing agent uses to go through `EventOperatorCapability`
4. Validate universal migration with CI checks

This is a significant refactoring that should be done incrementally with each migration having corresponding tests and validation.
